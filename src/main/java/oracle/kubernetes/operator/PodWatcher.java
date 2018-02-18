// Copyright 2017, 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at http://oss.oracle.com/licenses/upl.
package oracle.kubernetes.operator;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import com.google.gson.reflect.TypeToken;

import io.kubernetes.client.ApiException;
import io.kubernetes.client.models.V1ObjectMeta;
import io.kubernetes.client.models.V1Pod;
import io.kubernetes.client.models.V1PodCondition;
import io.kubernetes.client.models.V1PodStatus;
import io.kubernetes.client.util.Watch;
import oracle.kubernetes.operator.helpers.ClientHelper;
import oracle.kubernetes.operator.helpers.ClientHolder;
import oracle.kubernetes.operator.logging.LoggingFacade;
import oracle.kubernetes.operator.logging.LoggingFactory;
import oracle.kubernetes.operator.logging.MessageKeys;
import oracle.kubernetes.operator.watcher.Watcher;
import oracle.kubernetes.operator.watcher.Watching;
import oracle.kubernetes.operator.watcher.WatchingEventDestination;
import oracle.kubernetes.operator.work.NextAction;
import oracle.kubernetes.operator.work.Packet;
import oracle.kubernetes.operator.work.Step;

/**
 * Watches for Pods to become Ready or leave Ready state
 * 
 */
public class PodWatcher implements Runnable {
  private static final LoggingFacade LOGGER = LoggingFactory.getLogger("Operator", "Operator");
  
  private final String ns;
  private final String initialResourceVersion;
  private final WatchingEventDestination<V1Pod> destination;
  private final AtomicBoolean isStopping;
  
  // Map of domainUID to PodStateListener
  private final Map<String, PodStateListener> listeners = new ConcurrentHashMap<>();
  // Map of domainUID to Map of server name to Ready state
  private final Map<String, Map<String, Boolean>> serversKnownReadyState = new ConcurrentHashMap<>();
  // Map of domainUID to Map of pod name to pod status for failed pods
  private final Map<String, Map<String, V1PodStatus>> failedPods = new ConcurrentHashMap<>();
  // Map of Pod name to OnReady
  private final Map<String, OnReady> readyCallbackRegistrations = new ConcurrentHashMap<>();
  
  /**
   * Factory for PodWatcher
   * @param ns Namespace
   * @param initialResourceVersion Initial resource version or empty string
   * @param isStopping Stop signal
   * @return Pod watcher for the namespace
   */
  public static PodWatcher create(String ns, String initialResourceVersion, WatchingEventDestination<V1Pod> destination, AtomicBoolean isStopping) {
    PodWatcher prw = new PodWatcher(ns, initialResourceVersion, destination, isStopping);
    Thread thread = new Thread(prw);
    thread.setName("Thread-PodWatcher-" + ns);
    thread.setDaemon(true);
    thread.start();
    return prw;
  }

  private PodWatcher(String ns, String initialResourceVersion, WatchingEventDestination<V1Pod> destination, AtomicBoolean isStopping) {
    this.ns = ns;
    this.initialResourceVersion = initialResourceVersion;
    this.destination = destination;
    this.isStopping = isStopping;
  }

  /**
   * Polling loop. Get the next Pod object event and process it.
   */
  @Override
  public void run() {
    ClientHelper helper = ClientHelper.getInstance();
    ClientHolder client = helper.take();
    try {
      Watching<V1Pod> w = createWatching(client);
      Watcher<V1Pod> watcher = new Watcher<V1Pod>(w, null, initialResourceVersion);
      
      // invoke watch on current Thread.  Won't return until watch stops
      watcher.doWatch();
      
    } finally {
      helper.recycle(client);
    }
  }
  
  private Watching<V1Pod> createWatching(ClientHolder client) {
    return new Watching<V1Pod>() {

      /**
       * Watcher callback to issue the list Pod changes. It is driven by the
       * Watcher wrapper to issue repeated watch requests.
       * @param context user defined contact object or null
       * @param resourceVersion resource version to omit older events
       * @return Watch object or null if the operation should end
       * @throws ApiException if there is an API error.
       */
      @Override
      public Watch<V1Pod> initiateWatch(Object context, String resourceVersion) throws ApiException {
        return Watch.createWatch(client.getApiClient(),
            client.callBuilder().with($ -> {
              $.resourceVersion = resourceVersion;
              $.labelSelector = LabelConstants.DOMAINUID_LABEL; // Any Pod with a domainUID label
              $.timeoutSeconds = 30;
              $.watch = true;
            }).listPodCall(ns),
            new TypeToken<Watch.Response<V1Pod>>() {
            }.getType());
      }

      @Override
      public void eventCallback(Watch.Response<V1Pod> item) {
        processEventCallback(item);
      }

      @Override
      public boolean isStopping() {
        return isStopping.get();
      }
    };
  }
  
  private void processEventCallback(Watch.Response<V1Pod> item) {
    LOGGER.entering();
    
    V1Pod pod;
    Boolean previous;
    String domainUID;
    String name;
    switch(item.type) {
    case "ADDED":
    case "MODIFIED":
      pod = item.object;
      Boolean isReady = isReady(pod);
      String podName = pod.getMetadata().getName();
      domainUID = getPodDomainUID(pod);
      name = getPodServerName(pod);
      if (domainUID != null && name != null) {
        Map<String, Boolean> created = new ConcurrentHashMap<>();
        Map<String, Boolean> map = serversKnownReadyState.putIfAbsent(domainUID, created);
        if (map == null) {
          map = created;
        }
        previous = map.put(name, isReady);
        Map<String, V1PodStatus> map2;
        boolean previouslyFailed = false;
        boolean failed = isFailed(pod);
        if (failed) {
          Map<String, V1PodStatus> created2 = new ConcurrentHashMap<>();
          map2 = failedPods.putIfAbsent(domainUID, created2);
          if (map2 == null) {
            map2 = created2;
          }
          previouslyFailed = map2.put(podName, pod.getStatus()) != null;
        } else {
          map2 = failedPods.get(domainUID);
          if (map2 != null) {
            previouslyFailed = map2.remove(podName) != null;
          }
        }
        
        if (!isReady.equals(previous) || failed != previouslyFailed) {
          // change in Pod Ready of Failed state
          PodStateListener listener = listeners.get(domainUID);
          if (listener != null) {
            listener.onStateChange(map, map2);
          }
        }
      }
      if (isReady) {
        OnReady ready = readyCallbackRegistrations.remove(podName);
        if (ready != null) {
          ready.onReady();
        }
      }
      break;
    case "DELETED":
      pod = item.object;
      domainUID = getPodDomainUID(pod);
      name = getPodServerName(pod);
      if (domainUID != null && name != null) {
        Map<String, Boolean> map = serversKnownReadyState.get(domainUID);
        previous = map != null ? map.remove(name) : null;
        Map<String, V1PodStatus> map2;
        boolean previouslyFailed = false;
        map2 = failedPods.get(domainUID);
        if (map2 != null) {
          previouslyFailed = map2.remove(pod.getMetadata().getName()) != null;
        }
        
        if (Boolean.TRUE.equals(previous) || previouslyFailed) {
          // change in Pod Ready of Failed state
          PodStateListener listener = listeners.get(domainUID);
          if (listener != null) {
            listener.onStateChange(map, map2);
          }
        }
      }
      break;
    case "ERROR":
    default:
    }
    
    destination.eventCallback(item);

    LOGGER.exiting();
  }
  
  private boolean isReady(V1Pod pod) {
    V1PodStatus status = pod.getStatus();
    if (status != null) {
      if ("Running".equals(status.getPhase())) {
        List<V1PodCondition> conds = status.getConditions();
        if (conds != null) {
          for (V1PodCondition cond : conds) {
            if ("Ready".equals(cond.getType())) {
              if ("True".equals(cond.getStatus())) {
                // Pod is Ready!
                LOGGER.info(MessageKeys.POD_IS_READY, pod.getMetadata().getName());
                return true;
              }
            }
          }
        }
      }
    }
    return false;
  }
  
  private boolean isFailed(V1Pod pod) {
    V1PodStatus status = pod.getStatus();
    if (status != null) {
      if ("Failed".equals(status.getPhase())) {
        LOGGER.severe(MessageKeys.POD_IS_FAILED, pod.getMetadata().getName());
        return true;
      }
    }
    return false;
  }
  
  private String getPodDomainUID(V1Pod pod) {
    V1ObjectMeta meta = pod.getMetadata();
    Map<String, String> labels = meta.getLabels();
    if (labels != null) {
      return labels.get(LabelConstants.DOMAINUID_LABEL);
    }
    return null;
  }
  
  private String getPodServerName(V1Pod pod) {
    V1ObjectMeta meta = pod.getMetadata();
    Map<String, String> labels = meta.getLabels();
    if (labels != null) {
      return labels.get(LabelConstants.SERVERNAME_LABEL);
    }
    return null;
  }
  
  Map<String, PodStateListener> getListeners() {
    return listeners;
  }
  
  /**
   * Waits until the Pod is Ready
   * @param pod Pod to watch
   * @param next Next processing step once Pod is ready
   * @return Asynchronous step
   */
  public Step waitForReady(V1Pod pod, Step next) {
    return new WaitForPodReadyStep(pod, next);
  }
  
  private class WaitForPodReadyStep extends Step {
    private final V1Pod pod;

    private WaitForPodReadyStep(V1Pod pod, Step next) {
      super(next);
      this.pod = pod;
    }

    @Override
    public NextAction apply(Packet packet) {
      if (isReady(pod)) {
        return doNext(packet);
      }
      
      LOGGER.info(MessageKeys.WAITING_FOR_POD_READY, pod.getMetadata().getName());
      
      return doSuspend((fiber) -> {
        registerForOnReady(pod, () -> {
          fiber.resume(packet);
        });
      });
    }
  }
  
  @FunctionalInterface
  private interface OnReady {
    void onReady();
  }
  
  private void registerForOnReady(V1Pod pod, OnReady readyListener) {
    String podName = pod.getMetadata().getName();
    readyCallbackRegistrations.put(podName, readyListener);
    
    // Timing window -- Pod may have become ready in between read and this registration
    String domainUID = getPodDomainUID(pod);
    String name = getPodServerName(pod);
    if (domainUID != null && name != null) {
      Map<String, Boolean> map = serversKnownReadyState.get(domainUID);
      if (Boolean.TRUE.equals(map.get(name))) {
        // Pod is already Ready
        OnReady r = readyCallbackRegistrations.remove(podName);
        if (r != null) {
          r.onReady();
        }
      }
    }
  }
}

@FunctionalInterface
interface PodStateListener {
  public void onStateChange(Map<String, Boolean> knownReadyState, Map<String, V1PodStatus> failedPods);
}


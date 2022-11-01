// Copyright (c) 2017, 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import javax.annotation.Nonnull;

import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1Pod;
import io.kubernetes.client.util.Watch;
import io.kubernetes.client.util.Watchable;
import oracle.kubernetes.common.logging.MessageKeys;
import oracle.kubernetes.operator.builders.WatchBuilder;
import oracle.kubernetes.operator.calls.CallResponse;
import oracle.kubernetes.operator.helpers.CallBuilder;
import oracle.kubernetes.operator.helpers.DomainPresenceInfo;
import oracle.kubernetes.operator.helpers.KubernetesUtils;
import oracle.kubernetes.operator.helpers.PodHelper;
import oracle.kubernetes.operator.helpers.ResponseStep;
import oracle.kubernetes.operator.logging.LoggingFacade;
import oracle.kubernetes.operator.logging.LoggingFactory;
import oracle.kubernetes.operator.steps.DefaultResponseStep;
import oracle.kubernetes.operator.watcher.WatchListener;
import oracle.kubernetes.operator.work.NextAction;
import oracle.kubernetes.operator.work.Packet;
import oracle.kubernetes.operator.work.Step;
import org.jetbrains.annotations.Nullable;

import static oracle.kubernetes.common.logging.MessageKeys.LOG_WAITING_COUNT;
import static oracle.kubernetes.operator.ProcessingConstants.SERVER_NAME;

/**
 * Watches for changes to pods.
 */
public class PodWatcher extends Watcher<V1Pod> implements WatchListener<V1Pod>, PodAwaiterStepFactory {
  private static final LoggingFacade LOGGER = LoggingFactory.getLogger("Operator", "Operator");
  private final String namespace;
  private final WatchListener<V1Pod> listener;

  // Map of Pod name to callback. Note that since each pod name can be mapped to multiple callback registrations,
  // a concurrent map will not suffice; we therefore use an ordinary map and synchronous accesses.
  private final Map<String, Collection<Consumer<V1Pod>>> modifiedCallbackRegistrations = new HashMap<>();
  private final Map<String, Collection<Consumer<V1Pod>>> deletedCallbackRegistrations = new HashMap<>();

  private PodWatcher(
      String namespace,
      String initialResourceVersion,
      WatchTuning tuning,
      WatchListener<V1Pod> listener,
      AtomicBoolean isStopping) {
    super(initialResourceVersion, tuning, isStopping);
    setListener(this);
    this.namespace = namespace;
    this.listener = listener;
  }

  /**
   * Factory for PodWatcher.
   *
   * @param factory thread factory
   * @param ns Namespace
   * @param initialResourceVersion Initial resource version or empty string
   * @param tuning Watch tuning parameters
   * @param listener Callback for watch events
   * @param isStopping Stop signal
   * @return Pod watcher for the namespace
   */
  public static PodWatcher create(
      ThreadFactory factory,
      String ns,
      String initialResourceVersion,
      WatchTuning tuning,
      WatchListener<V1Pod> listener,
      AtomicBoolean isStopping) {
    PodWatcher watcher = new PodWatcher(ns, initialResourceVersion, tuning, listener, isStopping);
    watcher.start(factory);
    return watcher;
  }

  private @Nonnull Collection<Consumer<V1Pod>> getOnModifiedCallbacks(String podName) {
    synchronized (modifiedCallbackRegistrations) {
      return Optional.ofNullable(modifiedCallbackRegistrations.get(podName)).orElse(Collections.emptyList());
    }
  }

  private @Nonnull Collection<Consumer<V1Pod>> getOnDeleteCallbacks(String podName) {
    synchronized (deletedCallbackRegistrations) {
      return Optional.ofNullable(deletedCallbackRegistrations.remove(podName)).orElse(Collections.emptyList());
    }
  }

  @Override
  public Watchable<V1Pod> initiateWatch(WatchBuilder watchBuilder) throws ApiException {
    return watchBuilder
        .withLabelSelectors(LabelConstants.DOMAINUID_LABEL, LabelConstants.CREATEDBYOPERATOR_LABEL)
        .createPodWatch(namespace);
  }

  @Override
  public String getNamespace() {
    return namespace;
  }

  @Override
  public String getDomainUid(Watch.Response<V1Pod> item) {
    return KubernetesUtils.getDomainUidLabel(
        Optional.ofNullable(item.object).map(V1Pod::getMetadata).orElse(null));
  }

  /**
   * Receive response.
   * @param item item
   */
  public void receivedResponse(Watch.Response<V1Pod> item) {
    listener.receivedResponse(item);

    V1Pod pod = item.object;
    switch (item.type) {
      case "ADDED":
      case "MODIFIED":
        copyOf(getOnModifiedCallbacks(PodHelper.getPodName(pod))).forEach(c -> c.accept(pod));
        break;
      case "DELETED":
        getOnDeleteCallbacks(PodHelper.getPodName(pod)).forEach(c -> c.accept(pod));
        break;
      case "ERROR":
      default:
    }
  }

  // make a copy to avoid concurrent modification
  private <T> Collection<T> copyOf(Collection<T> collection) {
    return new ArrayList<>(collection);
  }

  /**
   * Waits until the Pod is Ready.
   *
   * @param pod Pod to watch
   * @param next Next processing step once Pod is ready
   * @return Asynchronous step
   */
  public Step waitForReady(V1Pod pod, Step next) {
    return new WaitForPodReadyStep(pod, next);
  }

  /**
   * Waits until the Pod with given name is Ready.
   *
   * @param podName Name of the Pod to watch
   * @param next Next processing step once Pod is ready
   * @return Asynchronous step
   */
  public Step waitForReady(String podName, Step next) {
    return new WaitForPodReadyStep(podName, next);
  }

  /**
   * Waits until the Pod is deleted.
   *
   * @param pod Pod to watch
   * @param next Next processing step once Pod is deleted
   * @return Asynchronous step
   */
  public Step waitForDelete(V1Pod pod, Step next) {
    return new WaitForPodDeleteStep(pod, next);
  }

  private abstract static class WaitForPodStatusStep extends WaitForReadyStep<V1Pod> {

    public static final int RECHECK_DEBUG_COUNT = 10;

    private WaitForPodStatusStep(V1Pod pod, Step next) {
      super(pod, next);
    }

    private WaitForPodStatusStep(String podName, Step next) {
      super(podName, null, next);
    }

    @Override
    V1ObjectMeta getMetadata(V1Pod pod) {
      return pod.getMetadata();
    }
    
    @Override
    Step createReadAsyncStep(String name, String namespace, String domainUid, ResponseStep<V1Pod> responseStep) {
      return new CallBuilder().readPodAsync(name, namespace, domainUid, responseStep);
    }

    @Nullable
    private String getResource() {
      return this.initialResource == null ? this.resourceName : getMetadata(this.initialResource).getName();
    }

    static class WaitForPodResponseContext {

      private final DomainPresenceInfo info;
      private final String serverName;
      private final CallResponse<V1Pod> callResponse;

      WaitForPodResponseContext(Packet packet, CallResponse<V1Pod> callResponse) {
        info = DomainPresenceInfo.fromPacket(packet).orElseThrow(); // always present
        serverName = packet.getValue(SERVER_NAME);

        this.callResponse = callResponse;
      }

      // Return true if pod is cached but not found in explicit read.
      private boolean cachedPodNotFound() {
        if (callResponse.getResult() != null) {
          return false;  // this indicates that a pod was found.
        }

        return Optional.ofNullable(serverName).map(info::getServerPod).isPresent();
      }
    }

    protected ResponseStep<V1Pod> resumeIfReady(Callback callback) {
      return new DefaultResponseStep<>(getNext()) {
        @Override
        public NextAction onSuccess(Packet packet, CallResponse<V1Pod> callResponse) {
          final WaitForPodResponseContext context = new WaitForPodResponseContext(packet, callResponse);

          if (context.cachedPodNotFound()) {
            removeCallback(getResource(), callback);
            return doForkJoinAbort(DomainStatusUpdater.createServerPodFailureSteps("not found"), packet);
          } else if (isReady(callResponse.getResult()) || callback.didResumeFiber()) {
            callback.proceedFromWait(callResponse.getResult());
            return doEnd(packet);
          } else if (shouldWait()) {
            logWaitingMessage(context);
            // Watch backstop recheck count is less than or equal to the configured recheck count, delay.
            return doDelay(createReadAndIfReadyCheckStep(callback), packet,
                    getWatchBackstopRecheckDelaySeconds(), TimeUnit.SECONDS);
          } else {
            removeCallback(getResource(), callback);
            return doForkJoinAbort(DomainStatusUpdater.createServerPodFailureSteps("recheck count exceeded"), packet);
          }
        }

        private void logWaitingMessage(WaitForPodResponseContext context) {
          if ((callback.getRecheckCount() % RECHECK_DEBUG_COUNT) == 0) {
            LOGGER.fine(LOG_WAITING_COUNT,  context.serverName, callback.getRecheckCount());
          }
        }

        private boolean shouldWait() {
          return callback.incrementAndGetRecheckCount() <= getWatchBackstopRecheckCount();
        }
      };
    }
  }

  private class WaitForPodReadyStep extends WaitForPodStatusStep {

    private WaitForPodReadyStep(V1Pod pod, Step next) {
      super(pod, next);
    }

    private WaitForPodReadyStep(String podName, Step next) {
      super(podName, next);
    }

    // A pod is ready if it is not being deleted and has the ready status.
    @Override
    protected boolean isReady(V1Pod result) {
      return result != null && !PodHelper.isDeleting(result) && PodHelper.isReady(result);
    }

    // Pods should be processed if ready.
    @Override
    boolean shouldProcessCallback(V1Pod resource) {
      return isReady(resource);
    }

    private void addOnModifiedCallback(String podName, Consumer<V1Pod> callback) {
      synchronized (modifiedCallbackRegistrations) {
        modifiedCallbackRegistrations.computeIfAbsent(podName, k -> new ArrayList<>()).add(callback);
      }
    }

    @Override
    protected void addCallback(String podName, Consumer<V1Pod> callback) {
      addOnModifiedCallback(podName, callback);
    }

    private void removeOnModifiedCallback(String podName, Consumer<V1Pod> callback) {
      synchronized (modifiedCallbackRegistrations) {
        Optional.ofNullable(modifiedCallbackRegistrations.get(podName)).ifPresent(c -> c.remove(callback));
      }
    }

    @Override
    protected void removeCallback(String podName, Consumer<V1Pod> callback) {
      removeOnModifiedCallback(podName, callback);
    }

    @Override
    protected void logWaiting(String name) {
      LOGGER.fine(MessageKeys.WAITING_FOR_POD_READY, name);
    }

  }

  private class WaitForPodDeleteStep extends WaitForPodStatusStep {
    private WaitForPodDeleteStep(V1Pod pod, Step next) {
      super(pod, next);
    }

    @Override
    protected ResponseStep<V1Pod> resumeIfReady(Callback callback) {
      return new WaitForDeleteResponseStep(callback);
    }

    // A pod is considered deleted when reading its value from Kubernetes returns null.
    @Override
    protected boolean isReady(V1Pod result) {
      return result == null;
    }

    private void addOnDeleteCallback(String podName, Consumer<V1Pod> callback) {
      synchronized (deletedCallbackRegistrations) {
        deletedCallbackRegistrations.computeIfAbsent(podName, k -> new ArrayList<>()).add(callback);
      }
    }

    @Override
    protected void addCallback(String podName, Consumer<V1Pod> callback) {
      addOnDeleteCallback(podName, callback);
    }

    private void removeOnDeleteCallback(String podName, Consumer<V1Pod> callback) {
      synchronized (deletedCallbackRegistrations) {
        Optional.ofNullable(deletedCallbackRegistrations.get(podName)).ifPresent(c -> c.remove(callback));
      }
    }

    @Override
    protected void removeCallback(String podName, Consumer<V1Pod> callback) {
      removeOnDeleteCallback(podName, callback);
    }

    private class WaitForDeleteResponseStep extends DefaultResponseStep<V1Pod> {

      private final WaitForReadyStep<V1Pod>.Callback callback;

      WaitForDeleteResponseStep(Callback callback) {
        super(WaitForPodDeleteStep.this.getNext());
        this.callback = callback;
      }

      @Override
      public NextAction onSuccess(Packet packet, CallResponse<V1Pod> callResponse) {
        if (callResponse.getResult() == null || callback.didResumeFiber()) {
          callback.proceedFromWait(callResponse.getResult());
          return doEnd(packet);
        } else {
          return doDelay(createReadAndIfReadyCheckStep(callback), packet,
              getWatchBackstopRecheckDelaySeconds(), TimeUnit.SECONDS);
        }
      }
    }
  }
}

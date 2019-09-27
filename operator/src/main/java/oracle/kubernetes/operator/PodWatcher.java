// Copyright (c) 2017, 2019, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicBoolean;

import io.kubernetes.client.ApiException;
import io.kubernetes.client.models.V1ObjectMeta;
import io.kubernetes.client.models.V1Pod;
import io.kubernetes.client.util.Watch;
import oracle.kubernetes.operator.TuningParameters.WatchTuning;
import oracle.kubernetes.operator.builders.WatchBuilder;
import oracle.kubernetes.operator.builders.WatchI;
import oracle.kubernetes.operator.calls.CallResponse;
import oracle.kubernetes.operator.helpers.CallBuilder;
import oracle.kubernetes.operator.helpers.CallBuilderFactory;
import oracle.kubernetes.operator.helpers.PodHelper;
import oracle.kubernetes.operator.helpers.ResponseStep;
import oracle.kubernetes.operator.logging.LoggingFacade;
import oracle.kubernetes.operator.logging.LoggingFactory;
import oracle.kubernetes.operator.logging.MessageKeys;
import oracle.kubernetes.operator.watcher.WatchListener;
import oracle.kubernetes.operator.work.ContainerResolver;
import oracle.kubernetes.operator.work.NextAction;
import oracle.kubernetes.operator.work.Packet;
import oracle.kubernetes.operator.work.Step;

/** Watches for Pods to become Ready or leave Ready state. */
public class PodWatcher extends Watcher<V1Pod>
    implements WatchListener<V1Pod>, PodAwaiterStepFactory {
  private static final LoggingFacade LOGGER = LoggingFactory.getLogger("Operator", "Operator");

  private final String ns;
  private final WatchListener<V1Pod> listener;

  // Map of Pod name to callback
  private final Map<String, Collection<Runnable>> readyCallbackRegistrations = new HashMap<>();
  private final Map<String, Collection<Runnable>> deletedCallbackRegistrations = new HashMap<>();

  private PodWatcher(
      String ns,
      String initialResourceVersion,
      WatchTuning tuning,
      WatchListener<V1Pod> listener,
      AtomicBoolean isStopping) {
    super(initialResourceVersion, tuning, isStopping);
    setListener(this);
    this.ns = ns;
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

  private void registerOnReady(String podName, Runnable onReady) {
    synchronized (readyCallbackRegistrations) {
      Collection<Runnable> col = readyCallbackRegistrations.get(podName);
      if (col == null) {
        col = new ArrayList<>();
        readyCallbackRegistrations.put(podName, col);
      }
      col.add(onReady);
    }
  }

  private Collection<Runnable> retrieveOnReady(String podName) {
    synchronized (readyCallbackRegistrations) {
      return readyCallbackRegistrations.remove(podName);
    }
  }

  private void unregisterOnReady(String podName, Runnable onReady) {
    synchronized (readyCallbackRegistrations) {
      Collection<Runnable> col = readyCallbackRegistrations.get(podName);
      if (col != null) {
        col.remove(onReady);
      }
    }
  }

  private void registerOnDelete(String podName, Runnable onReady) {
    synchronized (deletedCallbackRegistrations) {
      Collection<Runnable> col = deletedCallbackRegistrations.get(podName);
      if (col == null) {
        col = new ArrayList<>();
        deletedCallbackRegistrations.put(podName, col);
      }
      col.add(onReady);
    }
  }

  private Collection<Runnable> retrieveOnDelete(String podName) {
    synchronized (deletedCallbackRegistrations) {
      return deletedCallbackRegistrations.remove(podName);
    }
  }

  private void unregisterOnDelete(String podName, Runnable onReady) {
    synchronized (deletedCallbackRegistrations) {
      Collection<Runnable> col = deletedCallbackRegistrations.get(podName);
      if (col != null) {
        col.remove(onReady);
      }
    }
  }

  @Override
  public WatchI<V1Pod> initiateWatch(WatchBuilder watchBuilder) throws ApiException {
    return watchBuilder
        .withLabelSelectors(LabelConstants.DOMAINUID_LABEL, LabelConstants.CREATEDBYOPERATOR_LABEL)
        .createPodWatch(ns);
  }

  public void receivedResponse(Watch.Response<V1Pod> item) {
    LOGGER.entering();

    listener.receivedResponse(item);

    V1Pod pod;
    Boolean isReady;
    String podName;
    switch (item.type) {
      case "ADDED":
      case "MODIFIED":
        pod = item.object;
        isReady = !PodHelper.isDeleting(pod) && PodHelper.isReady(pod);
        podName = pod.getMetadata().getName();
        if (isReady) {
          Collection<Runnable> col = retrieveOnReady(podName);
          if (col != null) {
            for (Runnable ready : col) {
              ready.run();
            }
          }
        }
        break;
      case "DELETED":
        pod = item.object;
        podName = pod.getMetadata().getName();
        Collection<Runnable> col = retrieveOnDelete(podName);
        if (col != null) {
          for (Runnable delete : col) {
            delete.run();
          }
        }
        break;
      case "ERROR":
      default:
    }

    LOGGER.exiting();
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
   * Waits until the Pod is deleted.
   *
   * @param pod Pod to watch
   * @param next Next processing step once Pod is deleted
   * @return Asynchronous step
   */
  public Step waitForDelete(V1Pod pod, Step next) {
    return new WaitForPodDeleteStep(pod, next);
  }

  private abstract class WaitForPodStatusStep extends Step {
    private final V1Pod pod;

    private WaitForPodStatusStep(V1Pod pod, Step next) {
      super(next);
      this.pod = pod;
    }

    @Override
    public NextAction apply(Packet packet) {
      if (!PodHelper.isDeleting(pod) && PodHelper.getReadyStatus(pod)) {
        return doNext(packet);
      }

      V1ObjectMeta metadata = pod.getMetadata();

      log(metadata);

      AtomicBoolean didResume = new AtomicBoolean(false);
      return doSuspend(
          (fiber) -> {
            Runnable ready =
                () -> {
                  if (didResume.compareAndSet(false, true)) {
                    fiber.resume(packet);
                  }
                };
            register(metadata, ready);

            // Timing window -- pod may have come ready before registration for callback
            CallBuilderFactory factory =
                ContainerResolver.getInstance().getContainer().getSpi(CallBuilderFactory.class);
            fiber
                .createChildFiber()
                .start(
                    factory
                        .create()
                        .readPodAsync(
                            metadata.getName(),
                            metadata.getNamespace(),
                            new ResponseStep<V1Pod>(null) {
                              @Override
                              public NextAction onFailure(
                                  Packet packet,
                                  CallResponse<V1Pod> callResponse) {
                                if (callResponse.getStatusCode() == CallBuilder.NOT_FOUND) {
                                  return onSuccess(packet, callResponse);
                                }
                                return super.onFailure(packet, callResponse);
                              }

                              @Override
                              public NextAction onSuccess(Packet packet, CallResponse<V1Pod> callResponse) {
                                if (testPod(callResponse.getResult())) {
                                  if (didResume.compareAndSet(false, true)) {
                                    unregister(metadata, ready);
                                    fiber.resume(packet);
                                  }
                                }
                                return doNext(packet);
                              }
                            }),
                    packet.clone(),
                    null);
          });
    }

    protected void log(V1ObjectMeta metadata) {
      // no-op
    }

    protected abstract boolean testPod(V1Pod result);

    protected abstract void register(V1ObjectMeta metadata, Runnable callback);

    protected abstract void unregister(V1ObjectMeta metadata, Runnable callback);
  }

  private class WaitForPodReadyStep extends WaitForPodStatusStep {
    private WaitForPodReadyStep(V1Pod pod, Step next) {
      super(pod, next);
    }

    @Override
    protected void log(V1ObjectMeta metadata) {
      LOGGER.info(MessageKeys.WAITING_FOR_POD_READY, metadata.getName());
    }

    @Override
    protected boolean testPod(V1Pod result) {
      return result != null && !PodHelper.isDeleting(result) && PodHelper.getReadyStatus(result);
    }

    @Override
    protected void register(V1ObjectMeta metadata, Runnable callback) {
      registerOnReady(metadata.getName(), callback);
    }

    @Override
    protected void unregister(V1ObjectMeta metadata, Runnable callback) {
      unregisterOnReady(metadata.getName(), callback);
    }
  }

  private class WaitForPodDeleteStep extends WaitForPodStatusStep {
    private WaitForPodDeleteStep(V1Pod pod, Step next) {
      super(pod, next);
    }

    @Override
    protected boolean testPod(V1Pod result) {
      return result == null;
    }

    @Override
    protected void register(V1ObjectMeta metadata, Runnable callback) {
      registerOnDelete(metadata.getName(), callback);
    }

    @Override
    protected void unregister(V1ObjectMeta metadata, Runnable callback) {
      unregisterOnDelete(metadata.getName(), callback);
    }
  }
}

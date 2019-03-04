// Copyright 2017, 2019, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator;

import io.kubernetes.client.ApiException;
import io.kubernetes.client.models.V1ObjectMeta;
import io.kubernetes.client.models.V1Pod;
import io.kubernetes.client.models.V1PodCondition;
import io.kubernetes.client.models.V1PodStatus;
import io.kubernetes.client.util.Watch;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicBoolean;
import oracle.kubernetes.operator.TuningParameters.WatchTuning;
import oracle.kubernetes.operator.builders.WatchBuilder;
import oracle.kubernetes.operator.builders.WatchI;
import oracle.kubernetes.operator.helpers.CallBuilder;
import oracle.kubernetes.operator.helpers.CallBuilderFactory;
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

  // Map of Pod name to OnReady
  private final ConcurrentMap<String, OnReady> readyCallbackRegistrations =
      new ConcurrentHashMap<>();

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

  @Override
  public WatchI<V1Pod> initiateWatch(WatchBuilder watchBuilder) throws ApiException {
    return watchBuilder
        .withLabelSelectors(LabelConstants.DOMAINUID_LABEL, LabelConstants.CREATEDBYOPERATOR_LABEL)
        .createPodWatch(ns);
  }

  public void receivedResponse(Watch.Response<V1Pod> item) {
    LOGGER.entering();

    switch (item.type) {
      case "ADDED":
      case "MODIFIED":
        V1Pod pod = item.object;
        Boolean isReady = isReady(pod);
        String podName = pod.getMetadata().getName();
        if (isReady) {
          OnReady ready = readyCallbackRegistrations.remove(podName);
          if (ready != null) {
            ready.onReady();
          }
        }
        break;
      case "DELETED":
      case "ERROR":
      default:
    }

    listener.receivedResponse(item);

    LOGGER.exiting();
  }

  static boolean isTerminating(V1Pod pod) {
    return pod.getMetadata().getDeletionTimestamp() != null
        || pod.getMetadata().getDeletionGracePeriodSeconds() != null;
  }

  static boolean isReady(V1Pod pod) {
    boolean ready = getReadyStatus(pod);
    if (ready) {
      LOGGER.info(MessageKeys.POD_IS_READY, pod.getMetadata().getName());
    }
    return ready;
  }

  static boolean getReadyStatus(V1Pod pod) {
    V1PodStatus status = pod.getStatus();
    if (status != null) {
      if ("Running".equals(status.getPhase())) {
        List<V1PodCondition> conds = status.getConditions();
        if (conds != null) {
          for (V1PodCondition cond : conds) {
            if ("Ready".equals(cond.getType())) {
              if ("True".equals(cond.getStatus())) {
                return true;
              }
            }
          }
        }
      }
    }
    return false;
  }

  static boolean isFailed(V1Pod pod) {
    V1PodStatus status = pod.getStatus();
    if (status != null) {
      if ("Failed".equals(status.getPhase())) {
        LOGGER.severe(MessageKeys.POD_IS_FAILED, pod.getMetadata().getName());
        return true;
      }
    }
    return false;
  }

  static String getPodDomainUID(V1Pod pod) {
    V1ObjectMeta meta = pod.getMetadata();
    Map<String, String> labels = meta.getLabels();
    if (labels != null) {
      return labels.get(LabelConstants.DOMAINUID_LABEL);
    }
    return null;
  }

  static String getPodServerName(V1Pod pod) {
    V1ObjectMeta meta = pod.getMetadata();
    Map<String, String> labels = meta.getLabels();
    if (labels != null) {
      return labels.get(LabelConstants.SERVERNAME_LABEL);
    }
    return null;
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

      V1ObjectMeta metadata = pod.getMetadata();

      LOGGER.info(MessageKeys.WAITING_FOR_POD_READY, metadata.getName());

      AtomicBoolean didResume = new AtomicBoolean(false);
      return doSuspend(
          (fiber) -> {
            OnReady ready =
                () -> {
                  if (didResume.compareAndSet(false, true)) {
                    fiber.resume(packet);
                  }
                };
            readyCallbackRegistrations.put(metadata.getName(), ready);

            // Timing window -- pod may have come ready before registration for callback
            CallBuilderFactory factory =
                ContainerResolver.getInstance().getContainer().getSPI(CallBuilderFactory.class);
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
                                  ApiException e,
                                  int statusCode,
                                  Map<String, List<String>> responseHeaders) {
                                if (statusCode == CallBuilder.NOT_FOUND) {
                                  return onSuccess(packet, null, statusCode, responseHeaders);
                                }
                                return super.onFailure(packet, e, statusCode, responseHeaders);
                              }

                              @Override
                              public NextAction onSuccess(
                                  Packet packet,
                                  V1Pod result,
                                  int statusCode,
                                  Map<String, List<String>> responseHeaders) {
                                if (result != null && isReady(result)) {
                                  if (didResume.compareAndSet(false, true)) {
                                    readyCallbackRegistrations.remove(metadata.getName(), ready);
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
  }

  @FunctionalInterface
  private interface OnReady {
    void onReady();
  }
}

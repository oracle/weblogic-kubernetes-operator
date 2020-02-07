// Copyright (c) 2017, 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import javax.annotation.Nonnull;

import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1Pod;
import io.kubernetes.client.util.Watch;
import oracle.kubernetes.operator.TuningParameters.WatchTuning;
import oracle.kubernetes.operator.builders.WatchBuilder;
import oracle.kubernetes.operator.builders.WatchI;
import oracle.kubernetes.operator.helpers.CallBuilder;
import oracle.kubernetes.operator.helpers.PodHelper;
import oracle.kubernetes.operator.helpers.ResponseStep;
import oracle.kubernetes.operator.logging.LoggingFacade;
import oracle.kubernetes.operator.logging.LoggingFactory;
import oracle.kubernetes.operator.logging.MessageKeys;
import oracle.kubernetes.operator.watcher.WatchListener;
import oracle.kubernetes.operator.work.Step;

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

  private void addOnModifiedCallback(String podName, Consumer<V1Pod> callback) {
    synchronized (modifiedCallbackRegistrations) {
      modifiedCallbackRegistrations.computeIfAbsent(podName, k -> new ArrayList<>()).add(callback);
    }
  }

  private @Nonnull Collection<Consumer<V1Pod>> getOnModifiedCallbacks(String podName) {
    synchronized (modifiedCallbackRegistrations) {
      return Optional.ofNullable(modifiedCallbackRegistrations.get(podName)).orElse(Collections.emptyList());
    }
  }

  private void removeOnModifiedCallback(String podName, Consumer<V1Pod> callback) {
    synchronized (modifiedCallbackRegistrations) {
      Optional.ofNullable(modifiedCallbackRegistrations.get(podName)).ifPresent(c -> c.remove(callback));
    }
  }

  private void addOnDeleteCallback(String podName, Consumer<V1Pod> callback) {
    synchronized (deletedCallbackRegistrations) {
      deletedCallbackRegistrations.computeIfAbsent(podName, k -> new ArrayList<>()).add(callback);
    }
  }

  private @Nonnull Collection<Consumer<V1Pod>> getOnDeleteCallbacks(String podName) {
    synchronized (deletedCallbackRegistrations) {
      return Optional.ofNullable(deletedCallbackRegistrations.remove(podName)).orElse(Collections.emptyList());
    }
  }

  private void removeOnDeleteCallback(String podName, Consumer<V1Pod> callback) {
    synchronized (deletedCallbackRegistrations) {
      Optional.ofNullable(deletedCallbackRegistrations.get(podName)).ifPresent(c -> c.remove(callback));
    }
  }

  @Override
  public WatchI<V1Pod> initiateWatch(WatchBuilder watchBuilder) throws ApiException {
    return watchBuilder
        .withLabelSelectors(LabelConstants.DOMAINUID_LABEL, LabelConstants.CREATEDBYOPERATOR_LABEL)
        .createPodWatch(namespace);
  }

  /**
   * Receive response.
   * @param item item
   */
  public void receivedResponse(Watch.Response<V1Pod> item) {
    LOGGER.entering();

    listener.receivedResponse(item);

    V1Pod pod = item.object;
    String podName = pod.getMetadata().getName();
    switch (item.type) {
      case "ADDED":
      case "MODIFIED":
        copyOf(getOnModifiedCallbacks(podName)).forEach(c -> c.accept(pod));
        break;
      case "DELETED":
        getOnDeleteCallbacks(podName).forEach(c -> c.accept(pod));
        break;
      case "ERROR":
      default:
    }

    LOGGER.exiting();
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
   * Waits until the Pod is deleted.
   *
   * @param pod Pod to watch
   * @param next Next processing step once Pod is deleted
   * @return Asynchronous step
   */
  public Step waitForDelete(V1Pod pod, Step next) {
    return new WaitForPodDeleteStep(pod, next);
  }

  private abstract class WaitForPodStatusStep extends WaitForReadyStep<V1Pod> {

    private WaitForPodStatusStep(V1Pod pod, Step next) {
      super(pod, next);
    }

    @Override
    V1ObjectMeta getMetadata(V1Pod pod) {
      return pod.getMetadata();
    }
    
    @Override
    Step createReadAsyncStep(String name, String namespace, ResponseStep<V1Pod> responseStep) {
      return new CallBuilder().readPodAsync(name, namespace, responseStep);
    }
  }

  private class WaitForPodReadyStep extends WaitForPodStatusStep {

    private WaitForPodReadyStep(V1Pod pod, Step next) {
      super(pod, next);
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

    @Override
    protected void addCallback(String podName, Consumer<V1Pod> callback) {
      addOnModifiedCallback(podName, callback);
    }

    @Override
    protected void removeCallback(String podName, Consumer<V1Pod> callback) {
      removeOnModifiedCallback(podName, callback);
    }

    @Override
    protected void logWaiting(String name) {
      LOGGER.info(MessageKeys.WAITING_FOR_POD_READY, name);
    }
  }

  private class WaitForPodDeleteStep extends WaitForPodStatusStep {
    private WaitForPodDeleteStep(V1Pod pod, Step next) {
      super(pod, next);
    }

    // A pod is considered deleted when reading its value from Kubernetes returns null.
    @Override
    protected boolean isReady(V1Pod result) {
      return result == null;
    }

    @Override
    protected void addCallback(String podName, Consumer<V1Pod> callback) {
      addOnDeleteCallback(podName, callback);
    }

    @Override
    protected void removeCallback(String podName, Consumer<V1Pod> callback) {
      removeOnDeleteCallback(podName, callback);
    }
  }
}

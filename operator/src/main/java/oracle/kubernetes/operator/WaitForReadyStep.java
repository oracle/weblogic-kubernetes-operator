// Copyright (c) 2019, 2023, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import io.kubernetes.client.openapi.models.V1ObjectMeta;
import oracle.kubernetes.operator.calls.CallResponse;
import oracle.kubernetes.operator.helpers.CallBuilder;
import oracle.kubernetes.operator.helpers.DomainPresenceInfo;
import oracle.kubernetes.operator.helpers.ResponseStep;
import oracle.kubernetes.operator.steps.DefaultResponseStep;
import oracle.kubernetes.operator.tuning.TuningParameters;
import oracle.kubernetes.operator.work.AsyncFiber;
import oracle.kubernetes.operator.work.NextAction;
import oracle.kubernetes.operator.work.Packet;
import oracle.kubernetes.operator.work.Step;
import oracle.kubernetes.weblogic.domain.model.DomainResource;

import static oracle.kubernetes.operator.ProcessingConstants.INTROSPECTOR_JOB_FAILURE_THROWABLE;
import static oracle.kubernetes.operator.ProcessingConstants.MAKE_RIGHT_DOMAIN_OPERATION;
import static oracle.kubernetes.operator.helpers.KubernetesUtils.getDomainUidLabel;

/**
 * This class is the base for steps that must suspend while waiting for a resource to become ready. It is typically
 * implemented as a part of a {@link Watcher} and relies on callbacks from that watcher to proceed.
 * @param <T> the type of resource handled by this step
 */
abstract class WaitForReadyStep<T> extends Step {

  static NextStepFactory nextStepFactory = WaitForReadyStep::createMakeDomainRightStep;

  protected static Step createMakeDomainRightStep(WaitForReadyStep<?>.Callback callback,
                                           DomainPresenceInfo info, Step next) {
    return new CallBuilder().readDomainAsync(info.getDomainName(),
            info.getNamespace(), new MakeRightDomainStep<>(callback, null));
  }

  static int getWatchBackstopRecheckDelaySeconds() {
    return TuningParameters.getInstance().getWatchTuning().getWatchBackstopRecheckDelay();
  }

  static int getWatchBackstopRecheckCount() {
    return TuningParameters.getInstance().getWatchTuning().getWatchBackstopRecheckCount();
  }

  final T initialResource;
  final String resourceName;

  /**
   * Creates a step which will only proceed once the specified resource is ready.
   * @param resource the resource to watch
   * @param next the step to run once the resource is ready
   */
  WaitForReadyStep(T resource, Step next) {
    this(null, resource, next);
  }

  WaitForReadyStep(String resourceName, T resource, Step next) {
    super(next);
    this.initialResource = resource;
    this.resourceName = resourceName;
  }

  @Override
  protected String getDetail() {
    return getResourceName();
  }

  /**
   * Returns true if the specified resource is deemed "ready." Different steps may define readiness in different ways.
   * @param resource the resource to check
   * @return true if processing can proceed
   */
  abstract boolean isReady(T resource);

  /**
   * Returns true if the cached resource is not found during periodic listing.
   * @param cachedResource cached resource to check
   * @param isNotFoundOnRead Boolean indicating if resource is not found in call response.
   *
   * @return true if cached resource not found on read
   */
  boolean onReadNotFoundForCachedResource(T cachedResource, boolean isNotFoundOnRead) {
    return false;
  }

  /**
   * Returns true if the callback for this resource should be processed. This is typically used to exclude
   * resources which have changed but are not yet ready, or else different instances with the same name.
   * This default implementation processes all callbacks.
   * 
   * @param resource the resource to check
   * @return true if the resource is expected
   */
  boolean shouldProcessCallback(T resource) {
    return true;
  }

  /**
   * Returns the metadata associated with the resource.
   * @param resource the resource to check
   * @return a Kubernetes metadata object containing the namespace and name
   */
  abstract V1ObjectMeta getMetadata(T resource);

  /**
   * Registers a callback for changes to the resource.
   * @param name the name of the resource to watch
   * @param callback the callback to invoke when a change is reported
   */
  abstract void addCallback(String name, Consumer<T> callback);

  /**
   * Unregisters a callback for the specified resource name.
   * @param name the name of the resource to stop watching
   * @param callback the previously registered callback
   */
  abstract void removeCallback(String name, Consumer<T> callback);

  /**
   * Creates a {@link Step} that reads the specified resource asynchronously and then invokes the specified response.
   * @param name the name of the resource
   * @param namespace the namespace containing the resource
   * @param domainUid the identifier of the domain that the resource is associated with
   * @param responseStep the step which should be invoked once the resource has been read
   * @return the created step
   */
  abstract Step createReadAsyncStep(String name, String namespace, String domainUid, ResponseStep<T> responseStep);

  /**
   * Updates the packet when the resource is declared ready. The default implementation does nothing.
   * @param packet the packet to update
   * @param resource the now-ready resource
   */
  void updatePacket(Packet packet, T resource) {
  }

  /**
   * Determines whether the state of the resource requires the fiber to be terminated.
   * This default implementation always returns false; if it returns true, {@link #createTerminationException(Object)}
   * must return a non-null result
   * @param resource the resource to check
   * @return true if the fiber should be terminated
   */
  boolean shouldTerminateFiber(T resource) {
    return false;
  }

  /**
   * Creates an exception to report as the fiber completion if the fiber is being terminated.
   * @param resource the resource from which the exception should be created
   * @return an exception. Must not return null if
   */
  Throwable createTerminationException(T resource) {
    return null;
  }

  /**
   * Log a message to indicate that we have started waiting for the resource to become ready.
   * This default implementation does nothing.
   * @param name the name of the resource
   */
  void logWaiting(String name) {
    // no-op
  }

  @Override
  public final NextAction apply(Packet packet) {
    if (shouldTerminateFiber(initialResource)) {
      return doTerminate(createTerminationException(initialResource), packet);
    } else if (isReady(initialResource)) {
      return doNext(packet);
    }

    logWaiting(getResourceName());
    return doSuspend(fiber -> resumeWhenReady(packet, fiber));
  }

  // Registers a callback for updates to the specified resource and
  // verifies that we haven't already missed the update.
  private void resumeWhenReady(Packet packet, AsyncFiber fiber) {
    Callback callback = new Callback(fiber, packet);
    addCallback(getResourceName(), callback);
    checkUpdatedResource(packet, fiber, callback);
  }

  // It is possible that the watch event was received between the time the step was created, and the time the callback
  // was registered. Just in case, we will check the latest resource value in Kubernetes and process the resource
  // if it is now ready
  private void checkUpdatedResource(Packet packet, AsyncFiber fiber, Callback callback) {
    fiber
        .createChildFiber()
        .start(
            createReadAndIfReadyCheckStep(callback),
            packet.copy(),
            null);
  }

  Step createReadAndIfReadyCheckStep(Callback callback) {
    if (initialResource != null) {
      return createReadAsyncStep(getResourceName(), getNamespace(), getDomainUid(), resumeIfReady(callback));
    } else {
      return new ReadAndIfReadyCheckStep(getResourceName(), resumeIfReady(callback), getNext());
    }
  }

  protected abstract ResponseStep<T> resumeIfReady(Callback callback);

  private String getNamespace() {
    return getMetadata(initialResource).getNamespace();
  }

  private String getDomainUid() {
    return getDomainUidLabel(getMetadata(initialResource));
  }

  @Override
  public String getResourceName() {
    return initialResource != null ? getMetadata(initialResource).getName() : resourceName;
  }


  private class ReadAndIfReadyCheckStep extends Step {
    private final String resourceName;
    private final ResponseStep<T> responseStep;

    ReadAndIfReadyCheckStep(String resourceName, ResponseStep<T> responseStep, Step next) {
      super(next);
      this.resourceName = resourceName;
      this.responseStep = responseStep;
    }

    @Override
    public NextAction apply(Packet packet) {
      DomainPresenceInfo info = packet.getSpi(DomainPresenceInfo.class);
      return doNext(createReadAsyncStep(resourceName, info.getNamespace(),
              info.getDomainUid(), responseStep), packet);
    }

  }

  static class MakeRightDomainStep<V> extends DefaultResponseStep<V> {
    public static final String WAIT_TIMEOUT_EXCEEDED = "Wait timeout exceeded";
    private final WaitForReadyStep<?>.Callback callback;

    MakeRightDomainStep(WaitForReadyStep<?>.Callback callback, Step next) {
      super(next);
      this.callback = callback;
    }

    @Override
    public NextAction onSuccess(Packet packet, CallResponse<V> callResponse) {
      clearExistingKubernetesNetworkException(packet);
      MakeRightDomainOperation makeRightDomainOperation =
              (MakeRightDomainOperation)packet.get(MAKE_RIGHT_DOMAIN_OPERATION);
      if (makeRightDomainOperation != null) {
        makeRightDomainOperation.clear();
        makeRightDomainOperation.setLiveInfo(new DomainPresenceInfo((DomainResource) callResponse.getResult()));
        makeRightDomainOperation.withExplicitRecheck().interrupt().execute();
      }
      callback.fiber.terminate(new Exception(WAIT_TIMEOUT_EXCEEDED), packet);
      return super.onSuccess(packet, callResponse);
    }

  }

  class Callback implements Consumer<T> {
    private final AsyncFiber fiber;
    private final Packet packet;
    private final AtomicBoolean didResume = new AtomicBoolean(false);
    private final AtomicInteger recheckCount = new AtomicInteger(0);

    Callback(AsyncFiber fiber, Packet packet) {
      this.fiber = fiber;
      this.packet = packet;
    }

    @Override
    public void accept(T resource) {
      boolean shouldProcessCallback = shouldProcessCallback(resource);
      if (shouldProcessCallback) {
        proceedFromWait(resource);
      }
    }

    private void handleResourceReady(Packet packet, T resource) {
      updatePacket(packet, resource);
      if (shouldTerminateFiber(resource)) {
        packet.put(INTROSPECTOR_JOB_FAILURE_THROWABLE, createTerminationException(resource));
      }
    }

    // The resource has now either completed or failed, so we can continue processing.
    void proceedFromWait(T resource) {
      removeCallback(getResourceName(), this);
      if (mayResumeFiber()) {
        handleResourceReady(packet, resource);
        fiber.resume(packet);
      }
    }

    // Returns true if it is now time to resume the fiber.
    // This method will return true only the first time it is called.
    private boolean mayResumeFiber() {
      return didResume.compareAndSet(false, true);
    }

    boolean didResumeFiber() {
      return didResume.get();
    }

    int incrementAndGetRecheckCount() {
      return recheckCount.incrementAndGet();
    }

    int getAndIncrementRecheckCount() {
      return recheckCount.getAndIncrement();
    }

    int getRecheckCount() {
      return recheckCount.get();
    }
  }

  // an interface to provide a hook for unit testing.
  interface NextStepFactory {
    Step createMakeDomainRightStep(WaitForReadyStep<?>.Callback callback,
                                                   DomainPresenceInfo info, Step next);
  }

}

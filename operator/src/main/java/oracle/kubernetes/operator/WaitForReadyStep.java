// Copyright (c) 2019, 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import io.kubernetes.client.openapi.models.V1ObjectMeta;
import oracle.kubernetes.operator.calls.CallResponse;
import oracle.kubernetes.operator.helpers.ResponseStep;
import oracle.kubernetes.operator.steps.DefaultResponseStep;
import oracle.kubernetes.operator.work.Fiber;
import oracle.kubernetes.operator.work.NextAction;
import oracle.kubernetes.operator.work.Packet;
import oracle.kubernetes.operator.work.Step;

/**
 * This class is the base for steps that must suspend while waiting for a resource to become ready. It is typically
 * implemented as a part of a {@link Watcher} and relies on callbacks from that watcher to proceed.
 * @param <T> the type of resource handled by this step
 */
abstract class WaitForReadyStep<T> extends Step {
  private final T initialResource;

  /**
   * Creates a step which will only proceed once the specified resource is ready.
   * @param resource the resource to watch
   * @param next the step to run once it the resource is ready
   */
  WaitForReadyStep(T resource, Step next) {
    super(next);
    this.initialResource = resource;
  }

  /**
   * Returns true if the specified resource is deemed "ready." Different steps may define readiness in different ways.
   * @param resource the resource to check
   * @return true if processing can proceed
   */
  abstract boolean isReady(T resource);

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
   * @param responseStep the step which should be invoked once the resource has been read
   * @return the created step
   */
  abstract Step createReadAsyncStep(String name, String namespace, ResponseStep<T> responseStep);

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

    logWaiting(getName());
    return doSuspend((fiber) -> resumeWhenReady(packet, fiber));
  }

  // Registers a callback for updates to the specified resource and
  // verifies that we haven't already missed the update.
  private void resumeWhenReady(Packet packet, Fiber fiber) {
    Callback callback = new Callback(fiber, packet);
    addCallback(getName(), callback);
    checkUpdatedResource(packet, fiber, callback);
  }

  // It is possible that the watch event was received between the time the step was created, and the time the callback
  // was registered. Just in case, we will check the latest resource value in Kubernetes and process the resource
  // if it is now ready
  private void checkUpdatedResource(Packet packet, Fiber fiber, Callback callback) {
    fiber
        .createChildFiber()
        .start(
            createReadAsyncStep(getName(), getNamespace(), resumeIfReady(callback)),
            packet.clone(),
            null);
  }

  private String getNamespace() {
    return getMetadata(initialResource).getNamespace();
  }

  private String getName() {
    return getMetadata(initialResource).getName();
  }

  private DefaultResponseStep<T> resumeIfReady(Callback callback) {
    return new DefaultResponseStep<>(null) {
      @Override
      public NextAction onSuccess(Packet packet, CallResponse<T> callResponse) {
        if (isReady(callResponse.getResult())) {
          callback.proceedFromWait(callResponse.getResult());
        }
        return doNext(packet);
      }
    };
  }

  private class Callback implements Consumer<T> {
    private final Fiber fiber;
    private final Packet packet;
    private final AtomicBoolean didResume = new AtomicBoolean(false);

    Callback(Fiber fiber, Packet packet) {
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

    // The resource has now either completed or failed, so we can continue processing.
    private void proceedFromWait(T resource) {
      removeCallback(getName(), this);
      if (mayResumeFiber()) {
        handleResourceReady(fiber, packet, resource);
        fiber.resume(packet);
      }
    }

    // Returns true if it is now time to resume the fiber.
    // This method will return true only the first time it is called.
    private boolean mayResumeFiber() {
      return didResume.compareAndSet(false, true);
    }
  }

  private void handleResourceReady(Fiber fiber, Packet packet, T resource) {
    updatePacket(packet, resource);
    if (shouldTerminateFiber(resource)) {
      fiber.terminate(createTerminationException(resource), packet);
    }
  }
}

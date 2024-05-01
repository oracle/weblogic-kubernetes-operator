// Copyright (c) 2018, 2024, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.work;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import oracle.kubernetes.operator.work.Fiber.CompletionCallback;
import oracle.kubernetes.operator.work.Fiber.FiberExecutor;
import org.jetbrains.annotations.NotNull;

/**
 * Allows at most one running Fiber per key value.
 */
public class FiberGate {
  private final ScheduledExecutorService scheduledExecutorService;

  /** A map of domain UIDs to the fiber charged with running processing on that domain. **/
  private final ConcurrentMap<String, Fiber> gateMap = new ConcurrentHashMap<>();

  /**
   * Constructor taking Engine for running Fibers.
   *
   * @param scheduledExecutorService Executor
   */
  public FiberGate(ScheduledExecutorService scheduledExecutorService) {
    this.scheduledExecutorService = scheduledExecutorService;
  }

  /**
   * Access map of current fibers.
   * @return Map of fibers in this gate
   */
  public Map<String, Fiber> getCurrentFibers() {
    return new HashMap<>(gateMap);
  }

  /**
   * Starts Fiber that cancels any earlier running Fibers with the same domain UID. Fiber map is not
   * updated if no Fiber is started.
   *
   * @param domainUid the UID for which a fiber should be started
   * @param stepSupplier Supplier for Step for Fiber to begin with
   * @param packetSupplier Supplier for Packet
   * @param callback Completion callback
   */
  public void startFiber(String domainUid, Supplier<Step> stepSupplier, Supplier<Packet> packetSupplier,
                     CompletionCallback callback) {
    requestNewFiberStart(domainUid, stepSupplier, packetSupplier, callback);
  }

  /**
   * Starts Fiber only if the last started Fiber matches the given old Fiber.
   *
   * @param domainUid the UID for which a fiber should be started
   * @param stepSupplier Supplier for step for Fiber to begin with
   * @param packetSupplier Supplier for Packet
   * @param callback Completion callback
   */
  private synchronized void requestNewFiberStart(
      String domainUid, Supplier<Step> stepSupplier, Supplier<Packet> packetSupplier, CompletionCallback callback) {
    new FiberRequest(domainUid, stepSupplier, packetSupplier, callback).invoke();
  }

  private class FiberRequest {

    private final String domainUid;
    private final Fiber fiber;
    private final Supplier<Step> stepSupplier;
    private final Supplier<Packet> packetSupplier;

    FiberRequest(String domainUid, Supplier<Step> stepSupplier,
             Supplier<Packet> packetSupplier, CompletionCallback callback) {
      this.domainUid = domainUid;
      this.stepSupplier = stepSupplier;
      this.packetSupplier = packetSupplier;

      fiber = new Fiber(new FiberExecutorImpl(), stepSupplier.get(), packetSupplier.get(),
          new FiberGateCompletionCallback(callback, domainUid));
    }

    void invoke() {
      fiber.start();
    }

    private class FiberExecutorImpl implements FiberExecutor {
      @Override
      public Cancellable schedule(Fiber fiber, Duration duration) {
        ScheduledFuture<?> future = scheduledExecutorService.schedule(
                () -> scheduledExecution(fiber), TimeUnit.MILLISECONDS.convert(duration), TimeUnit.MILLISECONDS);
        return () -> future.cancel(true);
      }

      private void scheduledExecution(Fiber fiber) {
        Fiber scheduledReplacement = Fiber.copyWithNewStepsAndPacket(fiber, stepSupplier.get(), packetSupplier.get());
        if (gateMap.compute(domainUid,
            (k, v) -> (v == null || v == fiber) ? scheduledReplacement : v) == scheduledReplacement) {
          scheduledExecutorService.execute(scheduledReplacement);
        }
      }

      @Override
      public void execute(@NotNull Fiber fiber) {
        Fiber existing = gateMap.put(domainUid, fiber);
        if (existing != null) {
          existing.cancel();
        }
        scheduledExecutorService.execute(fiber);
      }
    }
  }

  private class FiberGateCompletionCallback implements CompletionCallback {

    private final CompletionCallback callback;
    private final String domainUid;

    public FiberGateCompletionCallback(CompletionCallback callback, String domainUid) {
      this.callback = callback;
      this.domainUid = domainUid;
    }

    @Override
    public void onCompletion(Packet packet) {
      Fiber fiber = packet.getFiber();
      try {
        callback.onCompletion(packet);
      } finally {
        gateMap.remove(domainUid, fiber);
      }
    }

    @Override
    public void onThrowable(Packet packet, Throwable throwable) {
      Fiber fiber = packet.getFiber();
      try {
        callback.onThrowable(packet, throwable);
      } finally {
        gateMap.remove(domainUid, fiber);
      }
    }
  }
}

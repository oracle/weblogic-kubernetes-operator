// Copyright (c) 2018, 2026, Oracle and/or its affiliates.
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

import oracle.kubernetes.operator.ProcessingConstants;
import oracle.kubernetes.operator.helpers.ResourcePresenceInfo;
import oracle.kubernetes.operator.logging.LoggingFacade;
import oracle.kubernetes.operator.logging.LoggingFactory;
import oracle.kubernetes.operator.work.Fiber.CompletionCallback;
import oracle.kubernetes.operator.work.Fiber.FiberExecutor;
import org.jetbrains.annotations.NotNull;

import static oracle.kubernetes.operator.work.Cancellable.createCancellable;

/**
 * Allows at most one running Fiber per key value.
 */
public class FiberGate {
  private static final LoggingFacade LOGGER = LoggingFactory.getLogger("Operator", "Operator");

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
    private final String namespace;
    private final Fiber fiber;
    private final Supplier<Step> stepSupplier;
    private final Supplier<Packet> packetSupplier;

    FiberRequest(String domainUid, Supplier<Step> stepSupplier,
             Supplier<Packet> packetSupplier, CompletionCallback callback) {
      this.domainUid = domainUid;
      this.stepSupplier = stepSupplier;
      this.packetSupplier = packetSupplier;

      Step step = stepSupplier.get();
      Packet packet = packetSupplier.get();
      namespace = getNamespace(packet);
      fiber = new Fiber(new FiberExecutorImpl(), step, packet,
          new FiberGateCompletionCallback(callback, domainUid, namespace));
    }

    void invoke() {
      fiber.start();
    }

    private class FiberExecutorImpl implements FiberExecutor {
      @Override
      public Cancellable schedule(Fiber fiber, Duration duration) {
        long delayMillis = TimeUnit.MILLISECONDS.convert(duration);
        LOGGER.fine(
            "WKO-POD-STARTUP-TRACE component=fiber-gate phase=requeue-scheduled "
                + "domainUid={0} namespace={1} fiber={2} delayMs={3} thread={4}",
            domainUid,
            namespace,
            fiber,
            delayMillis,
            Thread.currentThread().getName());
        ScheduledFuture<?> future = scheduledExecutorService.schedule(
                () -> scheduledExecution(fiber), delayMillis, TimeUnit.MILLISECONDS);
        return createCancellable(future);
      }

      private void scheduledExecution(Fiber fiber) {
        Fiber scheduledReplacement = Fiber.copyWithNewStepsAndPacket(fiber, stepSupplier.get(), packetSupplier.get());
        Fiber selected = gateMap.compute(
            domainUid, (k, v) -> (v == null || v == fiber) ? scheduledReplacement : v);
        boolean accepted = selected == scheduledReplacement;
        LOGGER.fine(
            "WKO-POD-STARTUP-TRACE component=fiber-gate phase=requeue-fired "
                + "domainUid={0} namespace={1} sourceFiber={2} replacementFiber={3} selectedFiber={4} "
                + "accepted={5} thread={6}",
            domainUid,
            namespace,
            fiber,
            scheduledReplacement,
            selected,
            accepted,
            Thread.currentThread().getName());
        if (accepted) {
          scheduledExecutorService.execute(scheduledReplacement);
        }
      }

      @Override
      public void execute(@NotNull Fiber fiber) {
        Fiber existing = gateMap.put(domainUid, fiber);
        LOGGER.fine(
            "WKO-POD-STARTUP-TRACE component=fiber-gate phase=start domainUid={0} namespace={1} fiber={2} "
                + "replacedFiber={3} action={4} thread={5}",
            domainUid,
            namespace,
            fiber,
            existing,
            existing == null ? "new" : "replace",
            Thread.currentThread().getName());
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
    private final String namespace;

    public FiberGateCompletionCallback(CompletionCallback callback, String domainUid, String namespace) {
      this.callback = callback;
      this.domainUid = domainUid;
      this.namespace = namespace;
    }

    @Override
    public void onCompletion(Packet packet) {
      Fiber fiber = packet.getFiber();
      try {
        callback.onCompletion(packet);
      } finally {
        boolean removed = gateMap.remove(domainUid, fiber);
        LOGGER.fine(
            "WKO-POD-STARTUP-TRACE component=fiber-gate phase=complete outcome=success "
                + "domainUid={0} namespace={1} fiber={2} removed={3} thread={4}",
            domainUid,
            getNamespace(packet, namespace),
            fiber,
            removed,
            Thread.currentThread().getName());
      }
    }

    @Override
    public void onThrowable(Packet packet, Throwable throwable) {
      Fiber fiber = packet.getFiber();
      try {
        callback.onThrowable(packet, throwable);
      } finally {
        boolean removed = gateMap.remove(domainUid, fiber);
        LOGGER.fine(
            "WKO-POD-STARTUP-TRACE component=fiber-gate phase=complete outcome=throw "
                + "domainUid={0} namespace={1} fiber={2} removed={3} throwable={4} thread={5}",
            domainUid,
            getNamespace(packet, namespace),
            fiber,
            removed,
            throwable.getClass().getName(),
            Thread.currentThread().getName());
      }
    }
  }

  private static String getNamespace(Packet packet) {
    return getNamespace(packet, null);
  }

  private static String getNamespace(Packet packet, String defaultNamespace) {
    Object domainPresenceInfo = packet.get(ProcessingConstants.DOMAIN_PRESENCE_INFO);
    Object clusterPresenceInfo = packet.get(ProcessingConstants.CLUSTER_PRESENCE_INFO);
    if (domainPresenceInfo instanceof ResourcePresenceInfo presenceInfo) {
      return presenceInfo.getNamespace();
    } else if (clusterPresenceInfo instanceof ResourcePresenceInfo presenceInfo) {
      return presenceInfo.getNamespace();
    }
    return defaultNamespace;
  }
}

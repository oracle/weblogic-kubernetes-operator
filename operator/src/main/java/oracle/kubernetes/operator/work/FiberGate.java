// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.work;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicReference;
import oracle.kubernetes.operator.ProcessingConstants;
import oracle.kubernetes.operator.work.Fiber.CompletionCallback;
import oracle.kubernetes.operator.work.Fiber.ExitCallback;

/**
 * Allows at most one running Fiber per key value. However, rather than queue later arriving Fibers
 * this class cancels the earlier arriving Fibers. For the operator, this makes sense as domain
 * presence Fibers that come later will always complete or correct work that may have been
 * in-flight.
 */
public class FiberGate {
  private final Engine engine;
  private final ConcurrentMap<String, Fiber> gateMap = new ConcurrentHashMap<String, Fiber>();

  private final Fiber PLACEHOLDER;

  /**
   * Constructor taking Engine for running Fibers.
   *
   * @param engine Engine
   */
  public FiberGate(Engine engine) {
    this.engine = engine;
    this.PLACEHOLDER = engine.createFiber();
  }

  public ScheduledExecutorService getExecutor() {
    return engine.getExecutor();
  }

  /**
   * Starts Fiber that cancels any earlier running Fibers with the same key. Fiber map is not
   * updated if no Fiber is started.
   *
   * @param key Key
   * @param strategy Step for Fiber to begin with
   * @param packet Packet
   * @param callback Completion callback
   * @return started Fiber
   */
  public Fiber startFiber(String key, Step strategy, Packet packet, CompletionCallback callback) {
    return startFiberIfLastFiberMatches(key, null, strategy, packet, callback);
  }

  /**
   * Starts Fiber only if there is no running Fiber with the same key. Fiber map is not updated if
   * no Fiber is started.
   *
   * @param key Key
   * @param strategy Step for Fiber to begin with
   * @param packet Packet
   * @param callback Completion callback
   * @return started Fiber
   */
  public Fiber startFiberIfNoCurrentFiber(
      String key, Step strategy, Packet packet, CompletionCallback callback) {
    return startFiberIfLastFiberMatches(key, PLACEHOLDER, strategy, packet, callback);
  }

  /**
   * Starts Fiber only if the last started Fiber matches the given old Fiber.
   *
   * @param key Key
   * @param old Expected last Fiber
   * @param strategy Step for Fiber to begin with
   * @param packet Packet
   * @param callback Completion callback
   * @return started Fiber, or null, if no Fiber started
   */
  public synchronized Fiber startFiberIfLastFiberMatches(
      String key, Fiber old, Step strategy, Packet packet, CompletionCallback callback) {
    Fiber f = engine.createFiber();
    WaitForOldFiberStep wfofs;
    if (old != null) {
      if (old == PLACEHOLDER) {
        if (gateMap.putIfAbsent(key, f) != null) {
          return null;
        }
      } else if (!gateMap.replace(key, old, f)) {
        return null;
      }
    } else {
      old = gateMap.put(key, f);
    }
    wfofs = new WaitForOldFiberStep(old, strategy);
    f.getComponents().put(ProcessingConstants.FIBER_COMPONENT_NAME, Component.createFor(wfofs));
    f.start(
        wfofs,
        packet,
        new CompletionCallback() {
          @Override
          public void onCompletion(Packet packet) {
            gateMap.remove(key, f);
            callback.onCompletion(packet);
          }

          @Override
          public void onThrowable(Packet packet, Throwable throwable) {
            gateMap.remove(key, f);
            callback.onThrowable(packet, throwable);
          }
        });
    return f;
  }

  private static class WaitForOldFiberStep extends Step {
    private final AtomicReference<Fiber> old;
    private final AtomicReference<WaitForOldFiberStep> current;

    public WaitForOldFiberStep(Fiber old, Step next) {
      super(next);
      this.old = new AtomicReference<>(old);
      current = new AtomicReference<>(this);
    }

    @Override
    public NextAction apply(Packet packet) {
      WaitForOldFiberStep c = current.get();
      Fiber o = c != null ? c.old.getAndSet(null) : null;
      if (o == null) {
        return doNext(packet);
      }

      return doSuspend(
          this,
          (fiber) -> {
            boolean isWillCall =
                o.cancelAndExitCallback(
                    true,
                    new ExitCallback() {
                      @Override
                      public void onExit() {
                        current.set(o.getSPI(WaitForOldFiberStep.class));
                        fiber.resume(packet);
                      }
                    });

            if (!isWillCall) {
              current.set(o.getSPI(WaitForOldFiberStep.class));
              fiber.resume(packet);
            }
          });
    }
  }
}

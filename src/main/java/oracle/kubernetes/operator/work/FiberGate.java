// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.work;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicReference;

import oracle.kubernetes.operator.ProcessingConstants;
import oracle.kubernetes.operator.work.Fiber.CompletionCallback;
import oracle.kubernetes.operator.work.Fiber.ExitCallback;

public class FiberGate {
  private final Engine engine;
  private final ConcurrentMap<String, Fiber> gateMap = new ConcurrentHashMap<String, Fiber>();

  public FiberGate(Engine engine) {
    this.engine = engine;
  }
  
  public Fiber startFiber(String key, Step strategy, Packet packet, CompletionCallback callback) {
    return replaceAndStartFiber(key, null, strategy, packet, callback);
  }
  
  public Fiber replaceAndStartFiber(String key, Fiber old, Step strategy, Packet packet, CompletionCallback callback) {
    Fiber f = engine.createFiber();
    WaitForOldFiberStep wfofs;
    synchronized (this) {
      if (old != null) {
        if (!gateMap.replace(key, old, f)) {
          return null;
        }
      } else {
        old = gateMap.put(key, f);
      }
      wfofs = new WaitForOldFiberStep(old, strategy);
      f.getComponents().put(ProcessingConstants.FIBER_COMPONENT_NAME, Component.createFor(wfofs));
    }
    f.start(wfofs, packet, new CompletionCallback() {
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
    private WaitForOldFiberStep current;

    public WaitForOldFiberStep(Fiber old, Step next) {
      super(next);
      this.old = new AtomicReference<>(old);
      current = this;
    }

    @Override
    public NextAction apply(Packet packet) {
      Fiber o = current != null ? current.old.getAndSet(null) : null;
      if (o == null) {
        return doNext(packet);
      }

      return doSuspend(this, (fiber) -> {
        boolean isWillCall = o.cancelAndExitCallback(true, new ExitCallback() {
          @Override
          public void onExit() {
            current = o.getSPI(WaitForOldFiberStep.class);
            fiber.resume(packet);
          }
        });

        if (!isWillCall) {
          current = o.getSPI(WaitForOldFiberStep.class);
          fiber.resume(packet);
        }
      });
    }
  }
  
}

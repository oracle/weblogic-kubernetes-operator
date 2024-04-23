// Copyright (c) 2022, 2024, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.work;

import javax.annotation.Nonnull;

import io.kubernetes.client.extended.controller.reconciler.Result;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;

class FiberGateTest {

  private static final String UID1 = "uid1";
  private static final String UID2 = "uid2";

  private final FiberTestSupport testSupport = new FiberTestSupport();
  private final FiberGate fiberGate = new FiberGate(testSupport.getScheduledExecutorService());
  private final TerminalStep terminalStep = new TerminalStep();
  private final Step noopStep = new NoopStep();
  private final Packet packet = new Packet();
  private final TestCompletionCallback completionCallback = new TestCompletionCallback();

  @Test
  void whenFiberStarted_stepsAreRun() {
    fiberGate.startFiber(UID1, () -> terminalStep, () -> packet, completionCallback);

    assertThat(terminalStep.wasRun(), is(true));
  }

  @Test
  void afterFiberStarted_callbackIsInvokedWithPacket() {
    packet.put("name", "value");

    fiberGate.startFiber(UID1, () -> terminalStep, () -> packet, completionCallback);

    assertThat(completionCallback.foundValue, equalTo("value"));
  }

  @Test
  void ifNoFiberAlreadyRunningWithUID_startAnother() {
    fiberGate.startFiber(UID1, () -> new RunFiberStep(UID2), () -> packet, completionCallback);

    assertThat(terminalStep.wasRun(), is(true));
  }

  @Test
  void ifCallbackSettingMatchesCurrentUID_startAnother() {
    fiberGate.startFiber(UID1, () -> noopStep, () -> packet, new DelegatingCompletionCallback(UID1));

    assertThat(terminalStep.wasRun(), is(true));
  }

  private static class NoopStep extends Step {

    @Override
    public @Nonnull Result apply(Packet packet) {
      return doNext(packet);
    }
  }

  private class RunFiberStep extends Step {
    private final String subStepUid;

    RunFiberStep(String subStepUid) {
      this.subStepUid = subStepUid;
    }

    @Override
    public @Nonnull Result apply(Packet packet) {
      fiberGate.startFiber(subStepUid, () -> terminalStep, () -> packet, completionCallback);
      return doNext(packet);
    }
  }

  private static class TestCompletionCallback implements Fiber.CompletionCallback {

    Object foundValue;

    @Override
    public void onCompletion(Packet packet) {
      foundValue = packet.getValue("name");
    }

    @Override
    public void onThrowable(Packet packet, Throwable throwable) {

    }
  }

  private class DelegatingCompletionCallback implements Fiber.CompletionCallback {

    private final String uid;

    DelegatingCompletionCallback(String uid) {
      this.uid = uid;
    }

    @Override
    public void onCompletion(Packet packet) {
      fiberGate.startFiber(uid, () -> terminalStep, () -> packet, completionCallback);
    }

    @Override
    public void onThrowable(Packet packet, Throwable throwable) {

    }
  }
}
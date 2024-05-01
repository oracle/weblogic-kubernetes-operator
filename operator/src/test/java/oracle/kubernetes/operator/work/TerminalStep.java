// Copyright (c) 2019, 2024, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.work;

import java.util.concurrent.atomic.AtomicInteger;
import javax.annotation.Nonnull;

import io.kubernetes.client.extended.controller.reconciler.Result;

/** A do-nothing step that can be used as a base for testing. It has no next step. */
public class TerminalStep extends Step {
  private boolean executed;
  private final AtomicInteger executionCount = new AtomicInteger(0);

  public TerminalStep() {
    super(null);
  }

  public boolean wasRun() {
    return executed;
  }

  public int getExecutionCount() {
    return executionCount.get();
  }

  @Override
  public @Nonnull Result apply(Packet packet) {
    executed = true;
    executionCount.getAndIncrement();
    return doNext(null, packet);
  }
}

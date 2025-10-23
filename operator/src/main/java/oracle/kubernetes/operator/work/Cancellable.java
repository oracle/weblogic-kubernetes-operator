// Copyright (c) 2024, 2025, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.work;

import java.util.concurrent.ScheduledFuture;

public interface Cancellable {
  boolean cancel();

  boolean isDoneOrCancelled();

  /**
   * Create instance wrapping a ScheduledFuture.
   * @param future Scheduled future instance
   * @return Cancellable wrapping scheduled future
   */
  static Cancellable createCancellable(ScheduledFuture<?> future) {
    return new Cancellable() {
      @Override
      public boolean cancel() {
        return future.cancel(true);
      }

      @Override
      public boolean isDoneOrCancelled() {
        return future.isDone();
      }
    };
  }
}

// Copyright (c) 2020, 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.work;

import java.util.concurrent.TimeUnit;

/**
 * Defines operations on a fiber that may be done by asynchronous processing.
 */
public interface AsyncFiber {

  /**
   * Resumes a suspended fiber.
   *
   * @param resumePacket the packet to use for resumed operations
   */
  void resume(Packet resumePacket);

  /**
   * Terminates this fiber with a throwable. Must be called while the fiber is suspended.
   *
   * @param t the reason for terminating the fiber
   * @param packet the new packet
   */
  void terminate(Throwable t, Packet packet);

  /**
   * Schedules an operation for some time in the future.
   *
   * @param timeout the interval before the check should run, in units
   * @param unit the unit of time that defines the interval
   * @param runnable the operation to run
   */
  void scheduleOnce(long timeout, TimeUnit unit, Runnable runnable);

  /**
   * Creates a child Fiber. If this Fiber is cancelled, so will all of the children.
   *
   * @return a new child fiber
   */
  Fiber createChildFiber();

  /**
   * Instructs the fiber to cancel all of its child fibers. It then alters the next step of the fiber
   * to the specified step and resumes the fiber.
   * @param packet the fiber's packet, needed to resume it
   * @param nextStep the next step for the fiber to execute
   */
  void cancelChildFibersAndResume(Packet packet, Step nextStep);

  /**
   * Returns the nearest parent of this fiber which initiated a fork join.
   * @return a fiber waiting on a fork join, or null if no such parent fiber is found.
   */
  ForkJoinEnder getForkJoinEnder();
}

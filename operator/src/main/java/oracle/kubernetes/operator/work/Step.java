// Copyright (c) 2018, 2024, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.work;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import javax.annotation.Nonnull;

import io.kubernetes.client.extended.controller.reconciler.Result;
import oracle.kubernetes.operator.logging.LoggingFacade;
import oracle.kubernetes.operator.logging.LoggingFactory;
import oracle.kubernetes.operator.tuning.TuningParameters;

/** Individual step in a processing flow. */
public abstract class Step {
  private static final LoggingFacade LOGGER = LoggingFactory.getLogger("Operator", "Operator");

  public interface StepAdapter {
    Step adapt(Fiber fiber, Step step, Packet packet);
  }

  private static final StepAdapter DEFAULT_ADAPTER = (fiber, step, packet) -> {
    if (fiber != null) {
      fiber.addBreadcrumb(step);
    }
    return step;
  };

  @SuppressWarnings({"FieldMayBeFinal", "CanBeFinal"})
  private static StepAdapter adapter = DEFAULT_ADAPTER;

  public static final String THROWABLE = "throwable";

  private Step next;

  /** Create a step with no next step. */
  protected Step() {
    this(null);
  }

  /**
   * Create a step with the indicated next step.
   *
   * @param next The next step, use null to indicate a terminal step
   */
  protected Step(Step next) {
    this.next = next;
  }

  /**
   * Chain the specified step groups into a single chain of steps.
   *
   * @param stepGroups multiple groups of steps
   * @return the first step of the resultant chain
   */
  public static Step chain(Step... stepGroups) {
    int start = getFirstNonNullIndex(stepGroups);
    if (start >= stepGroups.length) {
      throw new IllegalArgumentException("No non-Null steps specified");
    }

    for (int i = start + 1; i < stepGroups.length; i++) {
      addLink(stepGroups[start], stepGroups[i]);
    }
    return stepGroups[start];
  }

  /**
   * Chain the specified step groups into a single chain of steps.
   *
   * @param stepGroups multiple groups of steps
   * @return the first step of the resultant chain
   */
  public static Step chain(List<Step> stepGroups) {
    return chain(stepGroups.toArray(new Step[0]));
  }

  private static int getFirstNonNullIndex(Step[] stepGroups) {
    for (int i = 0; i < stepGroups.length; i++) {
      if (stepGroups[i] != null) {
        return i;
      }
    }

    return stepGroups.length;
  }

  private static void addLink(Step stepGroup1, Step stepGroup2) {
    Step lastStep = lastStepIfNoDuplicate(stepGroup1, stepGroup2);
    if (lastStep != null) {
      // add steps in stepGroup2 to the end of stepGroup1 only if no steps
      // appears in both groups to avoid introducing a loop
      lastStep.next = stepGroup2;
    }
  }

  /**
   * Return last step in stepGroup1, or null if any step appears in both step groups.
   *
   * @param stepGroup1 Step that we want to find the last step for
   * @param stepGroup2 Step to check for duplicates
   *
   * @return last step in stepGroup1, or null if any step appears in both step groups.
   */
  private static Step lastStepIfNoDuplicate(Step stepGroup1, Step stepGroup2) {
    Step s = stepGroup1;
    List<Step> stepGroup2Array = stepToArray(stepGroup2);
    while (s.next != null) {
      if (stepGroup2Array.contains(s.next)) {
        return null;
      }
      s = s.next;
    }
    return s;
  }

  private static List<Step> stepToArray(Step stepGroup) {
    ArrayList<Step> stepsArray = new ArrayList<>();
    Step s = stepGroup;
    while (s != null) {
      stepsArray.add(s);
      s = s.next;
    }
    return stepsArray;
  }

  /**
   * The name of the step. This will default to the class name minus "Step".
   * @return The name of the step
   */
  public String getResourceName() {
    return getBaseName() + getNameSuffix();
  }

  @Nonnull
  private String getBaseName() {
    String name = getClass().getName();
    int idx = name.lastIndexOf('.');
    if (idx >= 0) {
      name = name.substring(idx + 1);
    }
    name = name.endsWith("Step") ? name.substring(0, name.length() - 4) : name;
    return name;
  }

  @Nonnull
  private String getNameSuffix() {
    return Optional.ofNullable(getDetail()).map(detail -> " (" + detail + ")").orElse("");
  }

  protected String getDetail() {
    return null;
  }

  // creates a unique ID that allows matching requests to responses
  @Override
  public String toString() {
    if (next == null) {
      return getResourceName();
    }
    return getResourceName() + "[" + next.toString() + "]";
  }

  /**
   * Invokes step using the packet as input/output context.
   *
   * @param packet Packet
   */
  public abstract @Nonnull Result apply(Packet packet);

  static final Step adapt(Step step, Packet packet) {
    return adapt(Fiber.getCurrentIfSet(), step, packet);
  }

  static final Step adapt(Fiber fiber, Step step, Packet packet) {
    if (fiber != null && fiber.isCancelled()) {
      return null;
    }
    return adapter.adapt(fiber, step, packet);
  }

  /**
   * Invokes this step.
   *
   * @param packet Packet to provide when invoking the step
   */
  public final Result doStepNext(Packet packet) {
    return doNext(this, packet);
  }

  /**
   * Invokes the next step, if set.
   *
   * @param packet Packet to provide when invoking the next step
   */
  protected final Result doNext(Packet packet) {
    return doNext(next, packet);
  }

  /**
   * Invokes the indicated next step.
   *
   * @param step The step
   * @param packet Packet to provide when invoking the next step
   */
  protected static final Result doNext(Step step, Packet packet) {
    if (step != null) {
      Step s = adapt(step, packet);
      if (s != null) {
        return s.apply(packet);
      }
    }
    return doEnd(packet);
  }

  /**
   * End the fiber processing.
   *
   * @param packet Packet
   */
  protected static final Result doEnd(Packet packet) {
    return new Result(false);
  }

  /**
   * End the fiber processing and requeue after the standard delay.
   *
   * @param packet Packet
   */
  protected static final Result doRequeue(Packet packet) {
    return new Result(true,
            Duration.ofSeconds(TuningParameters.getInstance().getWatchTuning().getWatchBackstopRecheckDelay()));
  }

  /**
   * Terminate fiber processing with a throwable.
   *
   * @param throwable Throwable
   * @param packet Packet
   * @return Next action that will end processing with a throwable
   */
  protected static final Result doTerminate(Throwable throwable, Packet packet) {
    Fiber fiber = Fiber.getCurrentIfSet();
    if (fiber != null) {
      fiber.addBreadcrumb("[throw= " + throwable.getMessage() + "]");
    }
    packet.put(THROWABLE, throwable);
    return doEnd(packet);
  }

  /**
   * Retries this step after a delay.
   *
   * @param packet Packet to provide when retrying this step
   * @param delay Delay time
   * @param unit Delay time unit
   */
  @SuppressWarnings("SameParameterValue")
  protected final Result doRetry(Packet packet, long delay, TimeUnit unit) {
    return doDelay(this, packet, delay, unit);
  }

  /**
   * Invoke the indicated step after a delay.
   *
   * @param step Step from which to resume
   * @param packet Packet to provide when retrying this step
   * @param delay Delay time
   * @param unit Delay time unit
   */
  protected static final Result doDelay(Step step, Packet packet, long delay, TimeUnit unit) {
    try {
      Fiber fiber = Fiber.getCurrentIfSet();
      if (fiber != null) {
        fiber.addBreadcrumb(("[delay: " + unit.toMillis(delay) + "ms]"));
      }
      unit.sleep(delay);
    } catch (InterruptedException e) {
      return doTerminate(e, packet);
    }
    return step.doStepNext(packet);
  }

  public final Step getNext() {
    return next;
  }

  /**
   * Invokes a set of steps and then conditionally continues to invoke a given step. If any of the steps
   * return requesting a requeue then the conditional step is not invoked and a requeue result with the
   * shortest duration. Otherwise, if none of the steps request a requeue then the result of invoking the
   * conditional step is returned.
   *
   * @param step Step to invoke conditionally after the set of steps are invoked
   * @param packet Resume packet
   * @param startDetails Pairs of step and packet to use when starting
   */
  protected final Result doForkJoin(
      Step step, Packet packet, Collection<Fiber.StepAndPacket> startDetails) {
    boolean requeue = false;
    Duration duration = null;

    Fiber fiber = Fiber.getCurrentIfSet();
    int count = 0;
    if (LOGGER.isFinerEnabled() && fiber != null) {
      fiber.addBreadcrumb("[forkJoin]");
    }
    for (Fiber.StepAndPacket sap : startDetails) {
      if (LOGGER.isFinerEnabled() && fiber != null) {
        fiber.addBreadcrumb("[" + ++count + "of" + startDetails.size() + "]");
      }

      Packet sapPacket = sap.packet();
      Result r = sap.step().doStepNext(sapPacket);
      Throwable t = Optional.ofNullable(sapPacket).map(p -> (Throwable) p.getValue(THROWABLE)).orElse(null);
      if (t != null) {
        return doTerminate(t, packet);
      }
      if (r != null && r.isRequeue()) {
        requeue = true;
        duration = minDuration(duration, r.getRequeueAfter());
      }
    }

    if (requeue) {
      if (LOGGER.isFinerEnabled() && fiber != null) {
        fiber.addBreadcrumb("[forkJoin-requeue: " + duration + "]");
      }
      return new Result(true, duration);
    }

    if (step == null) {
      if (LOGGER.isFinerEnabled() && fiber != null) {
        fiber.addBreadcrumb("[forkJoin-end]");
      }
      return doEnd(packet);
    }
    if (LOGGER.isFinerEnabled() && fiber != null) {
      fiber.addBreadcrumb("[forkJoin-cont]");
    }
    return step.doStepNext(packet);
  }

  private Duration minDuration(Duration one, Duration two) {
    if (one == null) {
      return two;
    }
    if (two == null) {
      return one;
    }
    return one.compareTo(two) <= 0 ? one : two;
  }
}

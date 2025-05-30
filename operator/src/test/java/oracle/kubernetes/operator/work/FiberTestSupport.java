// Copyright (c) 2018, 2024, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.work;

import java.io.File;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayDeque;
import java.util.Optional;
import java.util.PriorityQueue;
import java.util.Queue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import io.kubernetes.client.common.KubernetesListObject;
import io.kubernetes.client.openapi.models.CoreV1Event;
import io.kubernetes.client.openapi.models.CoreV1EventList;
import io.kubernetes.client.openapi.models.V1ConfigMap;
import io.kubernetes.client.openapi.models.V1ConfigMapList;
import io.kubernetes.client.openapi.models.V1CustomResourceDefinition;
import io.kubernetes.client.openapi.models.V1CustomResourceDefinitionList;
import io.kubernetes.client.openapi.models.V1Job;
import io.kubernetes.client.openapi.models.V1JobList;
import io.kubernetes.client.openapi.models.V1Namespace;
import io.kubernetes.client.openapi.models.V1NamespaceList;
import io.kubernetes.client.openapi.models.V1PersistentVolume;
import io.kubernetes.client.openapi.models.V1PersistentVolumeClaim;
import io.kubernetes.client.openapi.models.V1PersistentVolumeClaimList;
import io.kubernetes.client.openapi.models.V1PersistentVolumeList;
import io.kubernetes.client.openapi.models.V1PodDisruptionBudget;
import io.kubernetes.client.openapi.models.V1PodDisruptionBudgetList;
import io.kubernetes.client.openapi.models.V1Secret;
import io.kubernetes.client.openapi.models.V1SecretList;
import io.kubernetes.client.openapi.models.V1SelfSubjectRulesReview;
import io.kubernetes.client.openapi.models.V1Service;
import io.kubernetes.client.openapi.models.V1ServiceList;
import io.kubernetes.client.openapi.models.V1SubjectAccessReview;
import io.kubernetes.client.openapi.models.V1TokenReview;
import io.kubernetes.client.openapi.models.V1ValidatingWebhookConfiguration;
import io.kubernetes.client.openapi.models.V1ValidatingWebhookConfigurationList;
import oracle.kubernetes.operator.CoreDelegate;
import oracle.kubernetes.operator.MainDelegate;
import oracle.kubernetes.operator.ProcessingConstants;
import oracle.kubernetes.operator.calls.RequestBuilder;
import oracle.kubernetes.operator.helpers.DomainPresenceInfo;
import oracle.kubernetes.operator.logging.LoggingContext;
import oracle.kubernetes.utils.SystemClockTestSupport;
import oracle.kubernetes.weblogic.domain.model.ClusterList;
import oracle.kubernetes.weblogic.domain.model.ClusterResource;
import oracle.kubernetes.weblogic.domain.model.DomainList;
import oracle.kubernetes.weblogic.domain.model.DomainResource;

import static com.meterware.simplestub.Stub.createStrictStub;
import static com.meterware.simplestub.Stub.createStub;
import static oracle.kubernetes.operator.logging.LoggingContext.LOGGING_CONTEXT_KEY;

/**
 * Support for writing unit tests that use a fiber to run steps. Such tests can call #runStep to
 * initiate fiber execution, which will happen in simulated time. That time starts at zero when an
 * instance is created, and can be increased by a call to setTime. This is useful to run steps which
 * are scheduled for the future, without using delays. As all steps are run in a single thread,
 * there is no need to add semaphores to coordinate them.
 *
 * <p>The components in the packet used by the embedded fiber may be access via
 * #getPacketComponents.
 */
@SuppressWarnings("UnusedReturnValue")
public class FiberTestSupport {
  private final CompletionCallbackStub completionCallback = new CompletionCallbackStub();
  private final ScheduledExecutorStub schedule = ScheduledExecutorStub.create();

  private final CoreDelegate coreDelegate;
  private Packet packet = new Packet();

  /**
   * Create support for testing of fibers.
   */
  public FiberTestSupport() {
    MainDelegateStub mainDelegate = createStrictStub(MainDelegateStub.class);
    this.coreDelegate = mainDelegate;
    packet.put(ProcessingConstants.DELEGATE_COMPONENT_NAME, mainDelegate);
  }

  /**
   * Gets core delegate.
   * @return Delegate
   */
  public CoreDelegate getCoreDelegate() {
    return coreDelegate;
  }

  public ScheduledExecutorService getScheduledExecutorService() {
    return schedule;
  }

  /** Creates a single-threaded FiberGate instance. */
  public FiberGate createFiberGate() {
    return new FiberGate(schedule);
  }

  /**
   * Schedules a runnable to run immediately. In practice, it will run as soon as all previously
   * queued runnables have complete.
   *
   * @param runnable a runnable to be executed by the scheduler.
   */
  public void schedule(Runnable runnable) {
    schedule.execute(runnable);
  }

  /**
   * Schedules a runnable to run at some time in the future. See {@link #schedule(Runnable)}.
   *
   * @param command a runnable to be executed by the scheduler.
   * @param delay the number of time units in the future to run.
   * @param unit the time unit used for the above parameters
   */
  public ScheduledFuture<?> schedule(@Nonnull Runnable command, long delay, @Nonnull TimeUnit unit) {
    return schedule.schedule(command, delay, unit);
  }

  /**
   * Schedules a runnable to run immediately and at fixed intervals afterwards. See {@link
   * #schedule(Runnable)}.
   *
   * @param command a runnable to be executed by the scheduler.
   * @param initialDelay the number of time units in the future to run for the first time.
   * @param delay the number of time units between scheduled executions
   * @param unit the time unit used for the above parameters
   */
  public ScheduledFuture<?> scheduleWithFixedDelay(
      Runnable command, long initialDelay, long delay, TimeUnit unit) {
    return schedule.scheduleWithFixedDelay(command, initialDelay, delay, unit);
  }

  /**
   * Returns true if an item is scheduled to run at the specified time.
   *
   * @param time the time, in units
   * @param unit the unit associated with the time
   */
  public boolean hasItemScheduledAt(int time, TimeUnit unit) {
    return schedule.containsItemAt(time, unit);
  }

  /**
   * Returns the number of items actually run since this object was created.
   */
  public int getNumItemsRun() {
    return schedule.getNumItemsRun();
  }

  /**
   * Sets the simulated time, thus triggering the execution of any runnable items associated with
   * earlier times.
   *
   * @param time the time, in units
   * @param unit the unit associated with the time
   */
  public void setTime(long time, TimeUnit unit) {
    schedule.setTime(time, unit);
  }

  public Packet getPacket() {
    return packet;
  }

  public FiberTestSupport withClearPacket() {
    packet.clear();
    return this;
  }

  public FiberTestSupport addToPacket(String key, Object value) {
    packet.put(key, value);
    return this;
  }

  public FiberTestSupport addDomainPresenceInfo(DomainPresenceInfo info) {
    packet.put(ProcessingConstants.DOMAIN_PRESENCE_INFO, info);
    return this;
  }

  public FiberTestSupport addLoggingContext(LoggingContext loggingContext) {
    packet.put(LOGGING_CONTEXT_KEY, loggingContext);
    return this;
  }

  /**
   * Specifies a predefined packet to use for the next run.
   * @param packet the new packet
   */
  public FiberTestSupport withPacket(@Nonnull Packet packet) {
    this.packet = packet;
    return this;
  }

  public FiberTestSupport withCompletionAction(Runnable completionAction) {
    completionCallback.setCompletionAction(completionAction);
    return this;
  }

  /**
   * Starts a unit-test fiber with the specified steps.
   *
   * @param step the first step to run
   */
  public Packet runSteps(Step... step) {
    final Step stepList = (step.length == 1 ? step[0] : Step.chain(step));
    return runSteps(packet, stepList);
  }

  /**
   * Starts a unit-test fiber with the specified packet and step.
   *
   * @param packet the packet to use
   * @param step the first step to run
   */
  public Packet runSteps(Packet packet, Step step) {
    Fiber fiber = new Fiber(schedule, step, packet, completionCallback);
    fiber.start();

    return packet;
  }

  /**
   * Starts a unit-test fiber with the specified step.
   *
   * @param nextStep the first step to run
   */
  public Packet runSteps(StepFactory factory, Step nextStep) {
    return runSteps(factory.createStepList(nextStep));
  }

  /**
   * Verifies that the completion callback's 'onThrowable' method was invoked with a throwable of
   * the specified class. Clears the throwable so that #throwOnFailure will not throw the expected
   * exception.
   *
   * @param throwableClass the class of the excepted throwable
   */
  public void verifyCompletionThrowable(Class<? extends Throwable> throwableClass) {
    completionCallback.verifyThrowable(throwableClass);
  }

  /**
   * If the completion callback's 'onThrowable' method was invoked, throws the specified throwable.
   * Note that a call to #verifyCompletionThrowable will consume the throwable, so this method will
   * not throw it.
   *
   * @throws Exception the exception reported as a failure
   */
  public void throwOnCompletionFailure() throws Exception {
    completionCallback.throwOnFailure();
  }

  /**
   * Ensures that the next task scheduled with a fixed delay will be executed immediately.
   */
  public void presetFixedDelay() {
    schedule.presetFixedDelay = true;
  }

  @FunctionalInterface
  public interface StepFactory {
    Step createStepList(Step next);
  }

  abstract static class ScheduledExecutorStub implements ScheduledExecutorService {
    /* current time in milliseconds. */
    private long currentTime = 0;

    private final PriorityQueue<ScheduledItem> scheduledItems = new PriorityQueue<>();
    private final Queue<Runnable> queue = new ArrayDeque<>();
    private Runnable current;
    private int numItemsRun;
    private boolean presetFixedDelay;

    static ScheduledExecutorStub create() {
      return createStrictStub(ScheduledExecutorStub.class);
    }

    int getNumItemsRun() {
      return numItemsRun;
    }

    @Override
    public void shutdown() {
      // no-op
    }

    @Override
    @Nonnull
    public ScheduledFuture<?> schedule(
        @Nonnull Runnable command, long delay, @Nonnull TimeUnit unit) {
      scheduledItems.add(new ScheduledItem(currentTime + unit.toMillis(delay), command));
      if (current == null) {
        runNextRunnable();
      }
      return createStub(ScheduledFuture.class);
    }

    @Override
    @Nonnull
    public ScheduledFuture<?> scheduleWithFixedDelay(
        @Nonnull Runnable command, long initialDelay, long delay, @Nonnull TimeUnit unit) {
      scheduledItems.add(
          new PeriodicScheduledItem(
              currentTime + unit.toMillis(initialDelay), unit.toMillis(delay), command));
      if (presetFixedDelay) {
        presetFixedDelay = false;
        setTime(currentTime + unit.toMillis(initialDelay), TimeUnit.MILLISECONDS);
      }
      if (current == null) {
        runNextRunnable();
      }
      return createStub(ScheduledFuture.class);
    }

    @Override
    public void execute(@Nullable Runnable command) {
      queue.add(command);
      if (current == null) {
        runNextRunnable();
      }
    }

    private void runNextRunnable() {
      while (null != (current = queue.poll())) {
        current.run();
        numItemsRun++;
        current = null;
      }
    }

    /**
     * Sets the simulated time, thus triggering the execution of any runnable items associated with
     * earlier times.
     *
     * @param time the time, in units
     * @param unit the unit associated with the time
     */
    void setTime(long time, TimeUnit unit) {
      long newTime = unit.toMillis(time);
      if (newTime < currentTime) {
        throw new IllegalStateException(
            "Attempt to move clock backwards from " + currentTime + " to " + newTime);
      }

      while (!scheduledItems.isEmpty() && scheduledItems.peek().atTime <= newTime) {
        executeAsScheduled(scheduledItems.poll());
      }

      currentTime = newTime;
    }

    private void executeAsScheduled(ScheduledItem item) {
      currentTime = item.atTime;
      adjustSystemClock(currentTime);
      execute(item.runnable);
      if (item.isReschedulable()) {
        scheduledItems.add(item.rescheduled());
      }
    }

    private void adjustSystemClock(long currentTime) {
      Optional.ofNullable(SystemClockTestSupport.getTestStartTime())
          .ifPresent(startTime -> adjustSystemClock(currentTime, startTime));
    }

    private void adjustSystemClock(long currentTime, OffsetDateTime initialClockTime) {
      SystemClockTestSupport.setCurrentTime(initialClockTime.plus(currentTime, ChronoUnit.MILLIS));
    }

    /**
     * Returns true if a runnable item has been scheduled for the specified time.
     *
     * @param time the time, in units
     * @param unit the unit associated with the time
     * @return true if such an item exists
     */
    boolean containsItemAt(long time, TimeUnit unit) {
      for (ScheduledItem scheduledItem : scheduledItems) {
        if (scheduledItem.atTime == unit.toMillis(time)) {
          return true;
        }
      }
      return false;
    }

    private static class ScheduledItem implements Comparable<ScheduledItem> {
      private final long atTime;
      private final Runnable runnable;

      ScheduledItem(long atTime, Runnable runnable) {
        this.atTime = atTime;
        this.runnable = runnable;
      }

      // Return true if the item should be rescheduled after it is run.
      private boolean isReschedulable() {
        return rescheduled() != null;
      }

      @Override
      public int compareTo(@Nonnull ScheduledItem o) {
        return Long.compare(atTime, o.atTime);
      }

      ScheduledItem rescheduled() {
        return null;
      }
    }

    private static class PeriodicScheduledItem extends ScheduledItem {
      private final long interval;

      PeriodicScheduledItem(long atTime, long interval, Runnable runnable) {
        super(atTime, runnable);
        this.interval = interval;
      }

      @Override
      ScheduledItem rescheduled() {
        return new PeriodicScheduledItem(super.atTime + interval, interval, super.runnable);
      }
    }
  }

  static class CompletionCallbackStub implements Fiber.CompletionCallback {
    private Throwable throwable;
    private Runnable completionAction;

    void setCompletionAction(Runnable completionAction) {
      this.completionAction = completionAction;
    }

    @Override
    public void onCompletion(Packet packet) {
      Optional.ofNullable(completionAction).ifPresent(Runnable::run);
    }

    @Override
    public void onThrowable(Packet packet, Throwable throwable) {
      this.throwable = throwable;
    }

    /**
     * Verifies that 'onThrowable' was invoked with a throwable of the specified class. Clears the
     * throwable so that #throwOnFailure will not throw the expected exception.
     *
     * @param throwableClass the class of the excepted throwable
     */
    void verifyThrowable(Class<?> throwableClass) {
      Throwable actual = throwable;
      throwable = null;

      if (actual == null) {
        throw new AssertionError("Expected exception: " + throwableClass.getName());
      }
      if (!throwableClass.isInstance(actual)) {
        throw new AssertionError(
            "Expected exception: " + throwableClass.getName() + " but was " + actual);
      }
    }

    /**
     * If 'onThrowable' was invoked, throws the specified throwable. Note that a call to
     * #verifyThrowable will consume the throwable, so this method will not throw it.
     *
     * @throws Exception the exception reported as a failure
     */
    void throwOnFailure() throws Exception {
      if (throwable == null) {
        return;
      }
      if (throwable instanceof Error) {
        throw (Error) throwable;
      }
      throw (Exception) throwable;
    }
  }

  abstract static class MainDelegateStub implements MainDelegate {
    public File getDeploymentHome() {
      return new File("/deployment");
    }

    public RequestBuilder.VersionCodeRequestBuilder getVersionBuilder() {
      return new RequestBuilder.VersionCodeRequestBuilder();
    }

    public RequestBuilder<DomainResource, DomainList> getDomainBuilder() {
      return new RequestBuilder<>(DomainResource.class, DomainList.class,
              "weblogic.oracle", "v9", "domains", "domain");
    }

    public RequestBuilder<ClusterResource, ClusterList> getClusterBuilder() {
      return new RequestBuilder<>(ClusterResource.class, ClusterList.class,
              "weblogic.oracle", "v1", "clusters", "cluster");
    }

    public RequestBuilder<V1Namespace, V1NamespaceList> getNamespaceBuilder() {
      return new RequestBuilder<>(V1Namespace.class, V1NamespaceList.class,
              "", "v1", "namespaces", "namespace");
    }

    public RequestBuilder.PodRequestBuilder getPodBuilder() {
      return new RequestBuilder.PodRequestBuilder();
    }

    public RequestBuilder<V1Service, V1ServiceList> getServiceBuilder() {
      return new RequestBuilder<>(V1Service.class, V1ServiceList.class,
              "", "v1", "services", "service");
    }

    public RequestBuilder<V1ConfigMap, V1ConfigMapList> getConfigMapBuilder() {
      return new RequestBuilder<>(V1ConfigMap.class, V1ConfigMapList.class,
              "", "v1", "configmaps", "configmap");
    }

    public RequestBuilder<V1Secret, V1SecretList> getSecretBuilder() {
      return new RequestBuilder<>(V1Secret.class, V1SecretList.class,
              "", "v1", "secrets", "secret");
    }

    public RequestBuilder<CoreV1Event, CoreV1EventList> getEventBuilder() {
      return new RequestBuilder<>(CoreV1Event.class, CoreV1EventList.class,
              "", "v1", "events", "event");
    }

    public RequestBuilder<V1PersistentVolume, V1PersistentVolumeList> getPersistentVolumeBuilder() {
      return new RequestBuilder<>(V1PersistentVolume.class, V1PersistentVolumeList.class,
              "", "v1", "persistentvolumes", "persistentvolume");
    }

    public RequestBuilder<V1PersistentVolumeClaim, V1PersistentVolumeClaimList> getPersistentVolumeClaimBuilder() {
      return new RequestBuilder<>(V1PersistentVolumeClaim.class, V1PersistentVolumeClaimList.class,
              "", "v1", "persistentvolumeclaims", "persistentvolumeclaim");
    }

    public RequestBuilder<V1CustomResourceDefinition, V1CustomResourceDefinitionList>
        getCustomResourceDefinitionBuilder() {
      return new RequestBuilder<>(V1CustomResourceDefinition.class, V1CustomResourceDefinitionList.class,
              "apiextensions.k8s.io", "v1",
              "customresourcedefinitions", "customresourcedefinition");
    }

    public RequestBuilder<V1ValidatingWebhookConfiguration, V1ValidatingWebhookConfigurationList>
        getValidatingWebhookConfigurationBuilder() {
      return new RequestBuilder<>(V1ValidatingWebhookConfiguration.class, V1ValidatingWebhookConfigurationList.class,
              "admissionregistration.k8s.io", "v1",
              "validatingwebhookconfigurations", "validatingwebhookconfiguration");
    }

    public RequestBuilder<V1Job, V1JobList> getJobBuilder() {
      return new RequestBuilder<>(V1Job.class, V1JobList.class,
              "batch", "v1", "jobs", "job");
    }

    public RequestBuilder<V1PodDisruptionBudget, V1PodDisruptionBudgetList> getPodDisruptionBudgetBuilder() {
      return new RequestBuilder<>(V1PodDisruptionBudget.class, V1PodDisruptionBudgetList.class,
              "policy", "v1", "poddisruptionbudgets", "poddisruptionbudget");
    }

    public RequestBuilder<V1TokenReview, KubernetesListObject> getTokenReviewBuilder() {
      return new RequestBuilder<>(V1TokenReview.class, KubernetesListObject.class,
              "authentication.k8s.io", "v1", "tokenreviews", "tokenreview");
    }

    public RequestBuilder<V1SelfSubjectRulesReview, KubernetesListObject> getSelfSubjectRulesReviewBuilder() {
      return new RequestBuilder<>(V1SelfSubjectRulesReview.class, KubernetesListObject.class,
              "authorization.k8s.io", "v1", "selfsubjectrulesreviews", "selfsubjectrulesreview");
    }

    public RequestBuilder<V1SubjectAccessReview, KubernetesListObject> getSubjectAccessReviewBuilder() {
      return new RequestBuilder<>(V1SubjectAccessReview.class, KubernetesListObject.class,
              "authorization.k8s.io", "v1", "selfsubjectaccessreviews", "selfsubjectaccessreview");
    }
  }
}

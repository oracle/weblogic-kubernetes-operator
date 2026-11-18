// Copyright (c) 2022, 2026, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import javax.annotation.Nonnull;

import com.meterware.simplestub.Memento;
import com.meterware.simplestub.Stub;
import io.kubernetes.client.extended.controller.reconciler.Result;
import io.kubernetes.client.openapi.models.V1Job;
import io.kubernetes.client.openapi.models.V1JobSpec;
import io.kubernetes.client.openapi.models.V1JobStatus;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.util.Watch;
import oracle.kubernetes.common.utils.BaseTestUtils;
import oracle.kubernetes.operator.helpers.DomainPresenceInfo;
import oracle.kubernetes.operator.helpers.EventHelper;
import oracle.kubernetes.operator.helpers.KubernetesTestSupport;
import oracle.kubernetes.operator.helpers.UnitTestHash;
import oracle.kubernetes.operator.tuning.TuningParametersStub;
import oracle.kubernetes.operator.watcher.JobWatcher;
import oracle.kubernetes.operator.work.Cancellable;
import oracle.kubernetes.operator.work.FiberTestSupport;
import oracle.kubernetes.operator.work.Packet;
import oracle.kubernetes.operator.work.Step;
import oracle.kubernetes.utils.SystemClock;
import oracle.kubernetes.utils.SystemClockTestSupport;
import oracle.kubernetes.utils.TestUtils;
import oracle.kubernetes.weblogic.domain.model.DomainCommonConfigurator;
import oracle.kubernetes.weblogic.domain.model.DomainCondition;
import oracle.kubernetes.weblogic.domain.model.DomainList;
import oracle.kubernetes.weblogic.domain.model.DomainResource;
import oracle.kubernetes.weblogic.domain.model.DomainSpec;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static oracle.kubernetes.operator.EventConstants.DOMAIN_FAILED_EVENT;
import static oracle.kubernetes.operator.EventMatcher.hasEvent;
import static oracle.kubernetes.operator.ProcessingConstants.FATAL_INTROSPECTOR_ERROR;
import static oracle.kubernetes.weblogic.domain.model.DomainConditionMatcher.hasCondition;
import static oracle.kubernetes.weblogic.domain.model.DomainConditionType.FAILED;
import static oracle.kubernetes.weblogic.domain.model.DomainFailureReason.ABORTED;
import static oracle.kubernetes.weblogic.domain.model.DomainFailureReason.INTERNAL;
import static oracle.kubernetes.weblogic.domain.model.DomainFailureReason.INTROSPECTION;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThan;

class FailureRetryTest {

  private final DomainResource domain = DomainProcessorTestSetup.createTestDomain();
  private final DomainResource cachedDomain = DomainProcessorTestSetup.createTestDomain();
  private final DomainPresenceInfo info = new DomainPresenceInfo(domain);
  private final KubernetesTestSupport testSupport = new KubernetesTestSupport();
  private final List<Memento> mementos = new ArrayList<>();
  private final StepFactory stepFactory = new StepFactory();
  private final MakeRightStub makeRight = MakeRightStub.createFor(info, stepFactory);
  private final LocalDomainProcessorDelegateStub delegate
      = Stub.createStrictStub(LocalDomainProcessorDelegateStub.class, testSupport, makeRight);
  private final DomainProcessorImpl domainProcessor = new DomainProcessorImpl(delegate);
  private final AddDomainInvalidStep domainInvalidStep = new AddDomainInvalidStep();

  private OffsetDateTime testStartTime = SystemClock.now();
  private BaseTestUtils.ConsoleHandlerMemento consoleHandlerMemento;

  @BeforeEach
  void setUp() throws NoSuchFieldException {
    consoleHandlerMemento = TestUtils.silenceOperatorLogger();
    mementos.add(consoleHandlerMemento);
    mementos.add(testSupport.install());
    mementos.add(TuningParametersStub.install());
    mementos.add(UnitTestHash.install());
    mementos.add(SystemClockTestSupport.installClock());
    mementos.add(new DomainProcessorTestSupport().install());

    testSupport.defineResources(domain);
    testStartTime = SystemClock.now();
    cachedDomain.getMetadata().setCreationTimestamp(testStartTime.minusSeconds(1));
    domainProcessor.registerDomainPresenceInfo(new DomainPresenceInfo(cachedDomain));
    stepFactory.setSteps(domainInvalidStep);

    DomainProcessorTestSetup.defineRequiredResources(testSupport);
  }

  @AfterEach
  void tearDown() {
    mementos.forEach(Memento::revert);
  }

  @Test
  void whenInitialFailureOccurs_includeTimeInStatus() {
    domainProcessor.createMakeRightOperation(info).withExplicitRecheck().execute();

    assertThat(domain.getStatus().getInitialFailureTime(), equalTo(testStartTime));
    assertThat(domain.getStatus().getLastFailureTime(), equalTo(testStartTime));
    assertThat(domain.getNextRetryTime(), Matchers.greaterThan(testStartTime));
  }

  @Test
  void whenNotYetNextRetryTime_dontExecuteRetry() {
    domainProcessor.createMakeRightOperation(info).withExplicitRecheck().execute();

    final OffsetDateTime nextRetryTime = domain.getNextRetryTime().minusSeconds(2);
    setCurrentTime(nextRetryTime);

    assertThat(domainInvalidStep.numTimesRun, equalTo(1));
  }

  @Test
  void whenNextRetryTime_executeRetry() {
    domainProcessor.createMakeRightOperation(info).withExplicitRecheck().execute();

    setCurrentTime(getRecordedDomain().getNextRetryTime());

    assertThat(domainInvalidStep.numTimesRun, equalTo(2));
  }

  @Test
  void whenRestartedBeforeRetryTime_waitForRemainingInterval() {
    definePersistedFailure(120);

    scanDomains();
    testSupport.setTime(479, TimeUnit.SECONDS);
    assertThat(domainInvalidStep.numTimesRun, equalTo(0));

    testSupport.setTime(480, TimeUnit.SECONDS);
    assertThat(domainInvalidStep.numTimesRun, equalTo(1));
    assertThat(getRecordedDomain().getStatus().getInitialFailureTime(), equalTo(testStartTime.minusSeconds(120)));
  }

  @Test
  void whenRestartedAfterRetryTime_retryWithoutAnotherInterval() {
    definePersistedFailure(700);

    scanDomains();
    testSupport.setTime(0, TimeUnit.SECONDS);

    assertThat(domainInvalidStep.numTimesRun, equalTo(1));
  }

  @Test
  void whenRepeatedScansRestoreRetry_scheduleOnlyOneAttempt() {
    definePersistedFailure(120);

    scanDomains();
    scanDomains();
    scanDomains();
    testSupport.setTime(480, TimeUnit.SECONDS);

    assertThat(domainInvalidStep.numTimesRun, equalTo(1));
    testSupport.setTime(1080, TimeUnit.SECONDS);
    assertThat(domainInvalidStep.numTimesRun, equalTo(2));
  }

  @Test
  void whenRetryAlreadyScheduled_resourceScanDoesNotDuplicateIt() {
    new DomainCommonConfigurator(domain).withFailureRetryIntervalSeconds(600);
    domainProcessor.createMakeRightOperation(info).withExplicitRecheck().execute();

    scanDomains();
    testSupport.setTime(600, TimeUnit.SECONDS);

    assertThat(domainInvalidStep.numTimesRun, equalTo(2));
  }

  @Test
  void whenUnchangedDomainWatchIsRejected_keepPendingFailureRetry() {
    definePersistedFailure(120);
    scanDomains();
    testSupport.setTime(60, TimeUnit.SECONDS);

    domainProcessor.dispatchDomainWatch(new Watch.Response<>("MODIFIED", domain));

    assertThat(domainInvalidStep.numTimesRun, equalTo(0));
    assertThat(testSupport.hasItemScheduledAt(480, TimeUnit.SECONDS), equalTo(true));
    testSupport.setTime(480, TimeUnit.SECONDS);
    assertThat(domainInvalidStep.numTimesRun, equalTo(1));
  }

  @Test
  void whenOrdinaryRecheckIsRejected_keepPendingFailureRetry() {
    definePersistedFailure(120);
    scanDomains();
    testSupport.setTime(60, TimeUnit.SECONDS);

    domainProcessor.createMakeRightOperation(info).withExplicitRecheck().execute();

    assertThat(domainInvalidStep.numTimesRun, equalTo(0));
    assertThat(testSupport.hasItemScheduledAt(480, TimeUnit.SECONDS), equalTo(true));
    testSupport.setTime(480, TimeUnit.SECONDS);
    assertThat(domainInvalidStep.numTimesRun, equalTo(1));
  }

  @Test
  void whenAcceptedReconciliationSucceeds_cancelPendingFailureRetryWithoutReplacement() {
    definePersistedFailure(120);
    scanDomains();
    assertThat(testSupport.hasItemScheduledAt(480, TimeUnit.SECONDS), equalTo(true));
    stepFactory.setSteps(new Step() {
      @Override
      public @Nonnull Result apply(Packet packet) {
        domain.getStatus().removeConditionsWithType(FAILED);
        return doNext(packet);
      }
    });

    changeDomain(d -> d.getSpec().setIntrospectVersion("corrected"));
    domainProcessor.dispatchDomainWatch(new Watch.Response<>("MODIFIED", domain));

    assertThat(domain.shouldRetry(), equalTo(false));
    assertThat(delegate.cancelledRetryCount, equalTo(1));
    testSupport.setTime(1200, TimeUnit.SECONDS);
    assertThat(domainInvalidStep.numTimesRun, equalTo(0));
  }

  @Test
  void whenDomainUnregistered_cancelPendingFailureRetry() {
    definePersistedFailure(120);
    scanDomains();
    assertThat(testSupport.hasItemScheduledAt(480, TimeUnit.SECONDS), equalTo(true));

    domainProcessor.unregisterDomainPresenceInfo(info);

    assertThat(delegate.cancelledRetryCount, equalTo(1));
    testSupport.setTime(480, TimeUnit.SECONDS);
    assertThat(domainInvalidStep.numTimesRun, equalTo(0));
  }

  @Test
  void whenRestartedWithAbortedFailure_doNotResumeRetry() {
    definePersistedFailure(120);
    domain.getStatus().addCondition(new DomainCondition(FAILED).withReason(ABORTED)
        .withFailureInfo(domain.getSpec()).withMessage("retry limit reached"));

    scanDomains();
    testSupport.setTime(1200, TimeUnit.SECONDS);

    assertThat(domainInvalidStep.numTimesRun, equalTo(0));
  }

  @Test
  void whenRestoredRetryFailsAfterLimit_preserveHistoryAndAbort() {
    definePersistedFailure(700);
    new DomainCommonConfigurator(domain).withFailureRetryLimitMinutes(10);

    scanDomains();
    testSupport.setTime(0, TimeUnit.SECONDS);

    assertThat(getRecordedDomain(), hasCondition(FAILED).withReason(ABORTED));
    testSupport.setTime(1200, TimeUnit.SECONDS);
    assertThat(domainInvalidStep.numTimesRun, equalTo(1));
  }

  @Test
  void whenFailureClearedBeforeRestoredRetry_doNotRunStaleRetry() {
    definePersistedFailure(120);
    scanDomains();
    domain.getStatus().removeConditionsWithType(FAILED);

    testSupport.setTime(480, TimeUnit.SECONDS);

    assertThat(domainInvalidStep.numTimesRun, equalTo(0));
  }

  @Test
  void whenDomainRemovedBeforeRestoredRetry_doNotRecreateIt() {
    definePersistedFailure(120);
    scanDomains();
    domainProcessor.getDomainPresenceInfoMap().clear();

    testSupport.setTime(480, TimeUnit.SECONDS);

    assertThat(domainInvalidStep.numTimesRun, equalTo(0));
  }

  @Test
  void whenDomainReplacedBeforeRestoredRetry_doNotApplyOldRetryToNewResource() {
    definePersistedFailure(120);
    scanDomains();
    domain.getMetadata().setUid("replacement-domain");

    testSupport.setTime(480, TimeUnit.SECONDS);

    assertThat(domainInvalidStep.numTimesRun, equalTo(0));
  }

  @Test
  void whenFailureChangesBeforeRestoredRetry_useLatestRetryTime() {
    definePersistedFailure(120);
    scanDomains();
    testSupport.schedule(() -> domain.getStatus().addCondition(new DomainCondition(FAILED)
        .withReason(INTROSPECTION).withMessage("another failure")), 60, TimeUnit.SECONDS);

    testSupport.setTime(480, TimeUnit.SECONDS);
    assertThat(domainInvalidStep.numTimesRun, equalTo(0));

    testSupport.setTime(660, TimeUnit.SECONDS);
    assertThat(domainInvalidStep.numTimesRun, equalTo(1));
    assertThat(getRecordedDomain().getStatus().getInitialFailureTime(), equalTo(testStartTime.minusSeconds(120)));
  }

  @Test
  void whenDomainAbortedBeforeRestoredRetry_doNotRunStaleRetry() {
    definePersistedFailure(120);
    scanDomains();
    domain.getStatus().addCondition(new DomainCondition(FAILED).withReason(ABORTED)
        .withFailureInfo(domain.getSpec()).withMessage("retry limit reached"));

    testSupport.setTime(480, TimeUnit.SECONDS);

    assertThat(domainInvalidStep.numTimesRun, equalTo(0));
  }

  @Test
  void whenDomainChangesBeforeRestoredRetry_replaceOldTimer() {
    definePersistedFailure(120);
    scanDomains();

    changeDomain(d -> d.getSpec().setIntrospectVersion("updated"));
    domainProcessor.dispatchDomainWatch(new Watch.Response<>("MODIFIED", domain));
    testSupport.setTime(480, TimeUnit.SECONDS);
    assertThat(domainInvalidStep.numTimesRun, equalTo(1));

    testSupport.setTime(600, TimeUnit.SECONDS);
    assertThat(domainInvalidStep.numTimesRun, equalTo(2));
  }

  @Test
  void whenReconciliationIsStillActive_resourceScanDoesNotScheduleAnotherRetry() {
    definePersistedFailure(120);
    stepFactory.setSteps(new Step() {
      @Override
      public @Nonnull Result apply(Packet packet) {
        return doRequeue();
      }
    });
    domainProcessor.createMakeRightOperation(info).withExplicitRecheck().retryOnFailure().execute();

    scanDomains();

    assertThat(testSupport.hasItemScheduledAt(480, TimeUnit.SECONDS), equalTo(false));
  }

  @Test
  void whenNamespaceStopsBeforeRestoredRetry_doNotRunRetry() {
    definePersistedFailure(120);
    scanDomains();
    delegate.setNamespaceRunning(false);

    testSupport.setTime(480, TimeUnit.SECONDS);

    assertThat(domainInvalidStep.numTimesRun, equalTo(0));
  }

  private void definePersistedFailure(long secondsBeforeRestart) {
    new DomainCommonConfigurator(domain).withFailureRetryIntervalSeconds(600);
    SystemClockTestSupport.setCurrentTime(testStartTime.minusSeconds(secondsBeforeRestart));
    domain.getStatus().addCondition(new DomainCondition(FAILED).withReason(INTROSPECTION)
        .withMessage("introspector deadline exceeded before restart"));
    domain.getStatus().setObservedGeneration(domain.getMetadata().getGeneration());
    SystemClockTestSupport.setCurrentTime(testStartTime);
    domainProcessor.getDomainPresenceInfoMap().clear();
  }

  private void scanDomains() {
    Processors processors = new DomainResourcesValidation(domain.getNamespace(), domainProcessor).getProcessors();
    processors.getDomainListProcessing().accept(new DomainList().withItems(List.of(getRecordedDomain())));
    processors.completeProcessing(new Packet());
  }

  @Test
  void whenFailureStillExistsAfterRetry_updateLastFailureTime() {
    domainProcessor.createMakeRightOperation(info).withExplicitRecheck().execute();

    final OffsetDateTime nextRetryTime = domain.getNextRetryTime();
    setCurrentTime(nextRetryTime);

    assertThat(getRecordedDomain().getStatus().getInitialFailureTime(), equalTo(testStartTime));
    assertThat(getRecordedDomain().getStatus().getLastFailureTime(), equalTo(nextRetryTime));
  }

  private DomainResource getRecordedDomain() {
    return testSupport.<DomainResource>getResources(KubernetesTestSupport.DOMAIN).get(0);
  }

  private void setCurrentTime(OffsetDateTime newTime) {
    final Duration offset = Duration.between(testStartTime, newTime);
    testSupport.setTime(offset.toSeconds(), TimeUnit.SECONDS);
  }

  @Test
  void whenRetryAfterRetryLimit_addAbortedFailure() {
    new DomainCommonConfigurator(domain).withFailureRetryLimitMinutes(10);
    domainProcessor.createMakeRightOperation(info).withExplicitRecheck().execute();

    testSupport.setTime(getTimeAfterRetryLimit(), TimeUnit.SECONDS);

    assertThat(getRecordedDomain(), hasCondition(FAILED).withReason(ABORTED));
  }

  // A time by which the retry which exceeds the limit will be executed
  private long getTimeAfterRetryLimit() {
    return TimeUnit.MINUTES.toSeconds(domain.getFailureRetryLimitMinutes()) + domain.getFailureRetryIntervalSeconds();
  }

  @Test
  void whenFatalIntrospectionErrorDetected_addAbortedFailure() {
    stepFactory.setSteps(new AddFatalIntrospectionFailureStep());

    domainProcessor.createMakeRightOperation(info).withExplicitRecheck().execute();

    assertThat(getRecordedDomain(), hasCondition(FAILED).withReason(ABORTED));
  }

  @Test
  void afterAbortedConditionAdded_dontRetryAutomatically() {
    domain.getStatus().addCondition(new DomainCondition(FAILED).withReason(ABORTED).withMessage("in test"));
    final int numTimesRunToAborted = domainInvalidStep.numTimesRun;

    testSupport.setTime(getTimeAfterRetryLimit() + 10 * domain.getFailureRetryIntervalSeconds(), TimeUnit.SECONDS);

    assertThat(domainInvalidStep.numTimesRun, equalTo(numTimesRunToAborted));
  }

  @Test
  void afterAbortedConditionAdded_dontRetryOnDomainChange() {
    domain.getStatus().addCondition(new DomainCondition(FAILED).withReason(ABORTED).withMessage("in test"));
    final int numTimesRunToAborted = domainInvalidStep.numTimesRun;

    changeDomain(d -> d.getSpec().setReplicas(3));
    domainProcessor.dispatchDomainWatch(new Watch.Response<>("MODIFIED", domain));

    assertThat(domainInvalidStep.numTimesRun, equalTo(numTimesRunToAborted));
  }

  private void changeDomain(Consumer<DomainResource> domainUpdate) {
    domainUpdate.accept(domain);
    domain.getMetadata().setGeneration(2L);
  }

  @ParameterizedTest
  @EnumSource(VersionChangeType.class)
  void afterAbortedConditionAdded_retryAfterRestartVersionChanged(VersionChangeType changeType) {
    domain.getStatus().addCondition(new DomainCondition(FAILED).withReason(ABORTED).withMessage("in test"));
    final int numTimesRunToAborted = domainInvalidStep.numTimesRun;

    changeDomain(changeType::updateDomain);
    domainProcessor.dispatchDomainWatch(new Watch.Response<>("MODIFIED", domain));

    assertThat(domainInvalidStep.numTimesRun, greaterThan(numTimesRunToAborted));
  }

  enum VersionChangeType {
    INTROSPECT_VERSION(DomainSpec::setIntrospectVersion),
    RESTART_VERSION(DomainSpec::setRestartVersion),
    INTROSPECT_IMAGE(DomainSpec::setImage);

    private final BiConsumer<DomainSpec,String> mutator;

    VersionChangeType(BiConsumer<DomainSpec, String> mutator) {
      this.mutator = mutator;
    }

    void updateDomain(DomainResource domain) {
      mutator.accept(domain.getSpec(), "test");
    }

  }

  @Test
  void whenExceptionDuringProcessing_reportInDomainStatus() {
    forceExceptionDuringProcessing(new NullPointerException());

    domainProcessor.createMakeRightOperation(info).withExplicitRecheck().execute();

    assertThat(
        getRecordedDomain(),
        hasCondition(FAILED).withStatus("True").withReason(INTERNAL));
  }

  private void forceExceptionDuringProcessing(Exception exception) {
    stepFactory.setSteps(new ThrowExceptionStep(exception));
    consoleHandlerMemento.ignoringLoggedExceptions(exception.getClass());
  }

  @Test
  void whenExceptionDuringProcessing_createFailedEvent() {
    final long deadlineSeconds = 100;
    final long jobRunningTime  = 200;
    final V1Job introspectionJob = createIntrospectionJob(deadlineSeconds, jobRunningTime);
    forceExceptionDuringProcessing(new JobWatcher.DeadlineExceededException(introspectionJob));

    domainProcessor.createMakeRightOperation(info).withExplicitRecheck().execute();

    assertThat(testSupport,
        hasEvent(DOMAIN_FAILED_EVENT)
            .withNoteContaining("DeadlineExceeded", Long.toString(deadlineSeconds), Long.toString(jobRunningTime)));
  }

  @SuppressWarnings("SameParameterValue")
  @Nonnull
  private V1Job createIntrospectionJob(long deadlineSeconds, long jobRunningTime) {
    return new V1Job()
        .metadata(new V1ObjectMeta().name("introspection"))
        .spec(new V1JobSpec().activeDeadlineSeconds(deadlineSeconds))
        .status(new V1JobStatus().startTime(SystemClock.now().minusSeconds(jobRunningTime)));
  }

  static class AddDomainInvalidStep extends Step {

    private int numTimesRun = 0;

    @Override
    public @Nonnull Result apply(Packet packet) {
      numTimesRun++;
      return doNext(DomainStatusUpdater.createDomainInvalidFailureSteps("in unit test"), packet);
    }
  }

  static class AddFatalIntrospectionFailureStep extends Step {

    @Override
    public @Nonnull Result apply(Packet packet) {
      return doNext(DomainStatusUpdater.createIntrospectionFailureSteps(FATAL_INTROSPECTOR_ERROR), packet);
    }
  }

  static class StepFactory {
    private Step steps;

    public void setSteps(Step steps) {
      this.steps = steps;
    }

    public Step getSteps() {
      return steps;
    }
  }

  static class ThrowExceptionStep extends Step {
    private final Exception exception;

    ThrowExceptionStep(Exception exception) {
      this.exception = exception;
    }

    @Override
    public @Nonnull Result apply(Packet packet) {
      if (exception instanceof RuntimeException) {
        throw (RuntimeException) exception;
      } else {
        return doTerminate(exception, packet);
      }
    }
  }

  abstract static class MakeRightStub implements MakeRightDomainOperation {
    private final DomainPresenceInfo info;
    private final StepFactory stepFactory;
    private boolean explicitRecheck = false;
    private boolean retryOnFailure = false;
    private MakeRightExecutor executor;

    static MakeRightStub createFor(DomainPresenceInfo info, StepFactory stepFactory) {
      return Stub.createStrictStub(MakeRightStub.class, info, stepFactory);
    }

    MakeRightStub(DomainPresenceInfo info, StepFactory stepFactory) {
      this.info = info;
      this.stepFactory = stepFactory;
    }

    @Nonnull
    @Override
    public DomainPresenceInfo getPresenceInfo() {
      return info;
    }

    @Override
    public boolean isWillInterrupt() {
      return true;
    }

    @Override
    public MakeRightDomainOperation interrupt() {
      return this;
    }

    @Override
    public MakeRightDomainOperation withEventData(EventHelper.EventData eventItem) {
      return this;
    }

    @Override
    public boolean hasEventData() {
      return false;
    }

    @Override
    public MakeRightDomainOperation createRetry(@Nonnull DomainPresenceInfo info) {
      final MakeRightStub retry = createFor(info, stepFactory);
      retry.setExecutor(executor);
      return retry.withExplicitRecheck().retryOnFailure();
    }

    @Override
    public MakeRightDomainOperation withExplicitRecheck() {
      explicitRecheck = true;
      return this;
    }

    @Override
    public boolean isExplicitRecheck() {
      return explicitRecheck;
    }

    @Override
    public boolean isDeleting() {
      return false;
    }

    @Override
    public MakeRightDomainOperation retryOnFailure() {
      retryOnFailure = true;
      return this;
    }

    @Override
    public EventHelper.EventData getEventData() {
      return null;
    }

    @Override
    public boolean isRetryOnFailure() {
      return retryOnFailure;
    }

    @Override
    public void execute() {
      executor.runMakeRight(this);
    }

    @Nonnull
    @Override
    public Packet createPacket() {
      Packet packet = new Packet();
      packet.put(ProcessingConstants.DOMAIN_PRESENCE_INFO, info);
      packet.put(ProcessingConstants.MAKE_RIGHT_DOMAIN_OPERATION, this);
      return packet;
    }

    @Override
    public Step createSteps() {
      executor.registerDomainPresenceInfo(info);
      return stepFactory.getSteps();
    }

    void setExecutor(MakeRightExecutor executor) {
      this.executor = executor;
    }
  }

  abstract static class LocalDomainProcessorDelegateStub extends DomainProcessorDelegateStub {

    private final MakeRightStub makeRight;
    private int cancelledRetryCount;

    public LocalDomainProcessorDelegateStub(FiberTestSupport testSupport, MakeRightStub makeRight) {
      super(testSupport);
      this.makeRight = makeRight;
    }

    @Override
    public Cancellable schedule(Runnable command, long delay, TimeUnit unit) {
      Cancellable scheduled = super.schedule(command, delay, unit);
      // FiberTestSupport retains canceled callbacks in its queue. Record cancellation requests separately;
      // advancing time still exercises the production callback's guard against a canceled registration.
      return new Cancellable() {
        @Override
        public boolean cancel() {
          cancelledRetryCount++;
          return scheduled.cancel();
        }

        @Override
        public boolean isDoneOrCancelled() {
          return scheduled.isDoneOrCancelled();
        }
      };
    }

    @Nonnull
    @Override
    public MakeRightStub createMakeRightOperation(MakeRightExecutor executor, DomainPresenceInfo info) {
      MakeRightStub operation = MakeRightStub.createFor(info, makeRight.stepFactory);
      operation.setExecutor(executor);
      return operation;
    }
  }

}

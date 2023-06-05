// Copyright (c) 2022, 2023, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import javax.annotation.Nonnull;

import com.meterware.simplestub.Memento;
import com.meterware.simplestub.Stub;
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
import oracle.kubernetes.operator.work.FiberTestSupport;
import oracle.kubernetes.operator.work.NextAction;
import oracle.kubernetes.operator.work.Packet;
import oracle.kubernetes.operator.work.Step;
import oracle.kubernetes.utils.SystemClock;
import oracle.kubernetes.utils.SystemClockTestSupport;
import oracle.kubernetes.utils.TestUtils;
import oracle.kubernetes.weblogic.domain.model.DomainCommonConfigurator;
import oracle.kubernetes.weblogic.domain.model.DomainCondition;
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
    cachedDomain.getMetadata().setCreationTimestamp(testStartTime.minus(1, ChronoUnit.SECONDS));
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

    final OffsetDateTime nextRetryTime = domain.getNextRetryTime().minus(2, ChronoUnit.SECONDS);
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

    changeDomain(domain -> domain.getSpec().setReplicas(3));
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
            .withMessageContaining("DeadlineExceeded", Long.toString(deadlineSeconds), Long.toString(jobRunningTime)));
  }

  @SuppressWarnings("SameParameterValue")
  @Nonnull
  private V1Job createIntrospectionJob(long deadlineSeconds, long jobRunningTime) {
    return new V1Job()
        .metadata(new V1ObjectMeta().name("introspection"))
        .spec(new V1JobSpec().activeDeadlineSeconds(deadlineSeconds))
        .status(new V1JobStatus().startTime(SystemClock.now().minus(jobRunningTime, ChronoUnit.SECONDS)));
  }

  static class AddDomainInvalidStep extends Step {

    private int numTimesRun = 0;

    @Override
    public NextAction apply(Packet packet) {
      numTimesRun++;
      return doNext(DomainStatusUpdater.createDomainInvalidFailureSteps("in unit test"), packet);
    }
  }

  static class AddFatalIntrospectionFailureStep extends Step {

    @Override
    public NextAction apply(Packet packet) {
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
    public NextAction apply(Packet packet) {
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
      retry.explicitRecheck = true;
      return retry;
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
      return this;
    }

    @Override
    public EventHelper.EventData getEventData() {
      return null;
    }

    @Override
    public boolean isRetryOnFailure() {
      return true;
    }

    @Override
    public void execute() {
      executor.runMakeRight(this);
    }

    @Nonnull
    @Override
    public Packet createPacket() {
      return new Packet().with(info).with(this);
    }

    @Override
    public Step createSteps() {
      return stepFactory.getSteps();
    }

    void setExecutor(MakeRightExecutor executor) {
      this.executor = executor;
    }
  }

  abstract static class LocalDomainProcessorDelegateStub extends DomainProcessorDelegateStub {

    private final MakeRightStub makeRight;

    public LocalDomainProcessorDelegateStub(FiberTestSupport testSupport, MakeRightStub makeRight) {
      super(testSupport);
      this.makeRight = makeRight;
    }

    @Nonnull
    @Override
    public MakeRightStub createMakeRightOperation(MakeRightExecutor executor, DomainPresenceInfo info) {
      makeRight.setExecutor(executor);
      return makeRight;
    }
  }

}

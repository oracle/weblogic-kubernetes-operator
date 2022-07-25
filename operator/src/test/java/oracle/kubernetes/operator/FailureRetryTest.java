// Copyright (c) 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import javax.annotation.Nonnull;

import com.meterware.simplestub.Memento;
import com.meterware.simplestub.Stub;
import oracle.kubernetes.operator.helpers.DomainPresenceInfo;
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
import oracle.kubernetes.weblogic.domain.model.DomainCondition;
import oracle.kubernetes.weblogic.domain.model.DomainFailureReason;
import oracle.kubernetes.weblogic.domain.model.DomainResource;
import oracle.kubernetes.weblogic.domain.model.DomainStatus;
import org.hamcrest.Matchers;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static oracle.kubernetes.weblogic.domain.model.DomainConditionType.FAILED;
import static oracle.kubernetes.weblogic.domain.model.DomainFailureReason.DOMAIN_INVALID;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

class FailureRetryTest {

  private final DomainResource domain = DomainProcessorTestSetup.createTestDomain();
  private final DomainPresenceInfo info = new DomainPresenceInfo(domain);
  private final KubernetesTestSupport testSupport = new KubernetesTestSupport();
  private final List<Memento> mementos = new ArrayList<>();
  private final StepFactory stepFactory = new StepFactory();
  private final MakeRightStub makeRight = MakeRightStub.createFor(info, stepFactory);
  private final LocalDomainProcessorDelegateStub delegate
      = Stub.createStrictStub(LocalDomainProcessorDelegateStub.class, testSupport, makeRight);
  private final DomainProcessorImpl domainProcessor = new DomainProcessorImpl(delegate);
  private final AddFailureStep domainInvalidStep = new AddFailureStep(DOMAIN_INVALID);
  private OffsetDateTime testStartTime = SystemClock.now();

  @BeforeEach
  void setUp() throws NoSuchFieldException {
    mementos.add(TestUtils.silenceOperatorLogger());
    mementos.add(testSupport.install());
    mementos.add(TuningParametersStub.install());
    mementos.add(UnitTestHash.install());
    mementos.add(SystemClockTestSupport.installClock());
    mementos.add(new DomainProcessorTestSupport().install());

    testStartTime = SystemClock.now();
    stepFactory.setSteps(domainInvalidStep);

    DomainProcessorTestSetup.defineRequiredResources(testSupport);
  }

  @AfterEach
  void tearDown() {
    mementos.forEach(Memento::revert);
  }

  @Test
  void whenInitialFailureOccurs_includeTimeInStatus() {
    domainProcessor.createMakeRightOperation(info).execute();

    assertThat(domain.getStatus().getInitialFailureTime(), equalTo(testStartTime));
    assertThat(domain.getStatus().getLastFailureTime(), equalTo(testStartTime));
    assertThat(domain.getNextRetryTime(), Matchers.greaterThan(testStartTime));
  }

  @Test
  void whenNotYetNextRetryTime_dontExecuteRetry() {
    domainProcessor.createMakeRightOperation(info).execute();

    final OffsetDateTime nextRetryTime = domain.getNextRetryTime().minus(2, ChronoUnit.SECONDS);
    setCurrentTime(nextRetryTime);

    assertThat(domainInvalidStep.numTimesRun, equalTo(1));
  }

  @Test
  void whenNextRetryTime_executeRetry() {
    domainProcessor.createMakeRightOperation(info).execute();

    setCurrentTime(domain.getNextRetryTime());

    assertThat(domainInvalidStep.numTimesRun, equalTo(2));
  }

  @Test
  void whenFailureStillExistsAfterRetry_updateLastFailureTime() {
    domainProcessor.createMakeRightOperation(info).execute();

    final OffsetDateTime nextRetryTime = domain.getNextRetryTime();
    setCurrentTime(nextRetryTime);

    assertThat(domain.getStatus().getInitialFailureTime(), equalTo(testStartTime));
    assertThat(domain.getStatus().getLastFailureTime(), equalTo(nextRetryTime));
  }

  private void setCurrentTime(OffsetDateTime newTime) {
    final Duration offset = Duration.between(testStartTime, newTime);
    SystemClockTestSupport.setCurrentTime(newTime);
    testSupport.setTime(offset.toSeconds(), TimeUnit.SECONDS);
  }

  // todo at time after max, add aborted failure and don't retry
  // todo when step throws exception, add appropriate failure to status
  // todo after abort, changing retry version causes retry

  // --- these might pertain to the domain status rather than the retry processing.
  // todo when retry with issue fixed, remove failures
  // todo after abort and retry and issue fixed, remove failures
  // todo on retry, if a different failure occurs, update initial failure time. Maybe DomainStatus


  static class AddFailureStep extends Step {
    private final DomainFailureReason reason;
    private int numTimesRun = 0;

    AddFailureStep(DomainFailureReason reason) {
      this.reason = reason;
    }

    @Override
    public NextAction apply(Packet packet) {
      numTimesRun++;
      final DomainStatus status = getStatus(packet);
      Arrays.stream(DomainFailureReason.values()).forEach(status::markFailuresForRemoval);
      status.addCondition(new DomainCondition(FAILED).withReason(reason).withMessage("in unit test"));
      status.removeMarkedFailures();
      return doNext(packet);
    }

    @Nonnull
    private DomainStatus getStatus(Packet packet) {
      return DomainPresenceInfo.fromPacket(packet)
          .map(DomainPresenceInfo::getDomain)
          .map(DomainResource::getStatus)
          .orElseThrow();
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

  abstract static class MakeRightStub implements MakeRightDomainOperation {
    private final DomainPresenceInfo info;
    private final StepFactory stepFactory;
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
    public MakeRightDomainOperation createRetry(@NotNull DomainPresenceInfo info) {
      final MakeRightStub retry = createFor(info, stepFactory);
      retry.setExecutor(executor);
      return retry;
    }

    @Override
    public void execute() {
      executor.runMakeRight(this, i -> true);
    }

    @NotNull
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

    @NotNull
    @Override
    public MakeRightStub createMakeRightOperation(MakeRightExecutor executor, DomainPresenceInfo info) {
      makeRight.setExecutor(executor);
      return makeRight;
    }
  }

}

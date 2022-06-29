// Copyright (c) 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.makeright;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import com.meterware.simplestub.Memento;
import com.meterware.simplestub.StaticStubSupport;
import com.meterware.simplestub.Stub;
import oracle.kubernetes.operator.DomainProcessorDelegateStub;
import oracle.kubernetes.operator.DomainProcessorTestSetup;
import oracle.kubernetes.operator.MakeRightDomainOperation;
import oracle.kubernetes.operator.MakeRightExecutor;
import oracle.kubernetes.operator.Processors;
import oracle.kubernetes.operator.calls.SimulatedStep;
import oracle.kubernetes.operator.helpers.DomainPresenceInfo;
import oracle.kubernetes.operator.helpers.IntrospectionTestUtils;
import oracle.kubernetes.operator.helpers.KubernetesTestSupport;
import oracle.kubernetes.operator.helpers.SecretType;
import oracle.kubernetes.operator.helpers.UnitTestHash;
import oracle.kubernetes.operator.tuning.TuningParametersStub;
import oracle.kubernetes.operator.utils.WlsDomainConfigSupport;
import oracle.kubernetes.operator.work.Fiber;
import oracle.kubernetes.operator.work.NextAction;
import oracle.kubernetes.operator.work.Packet;
import oracle.kubernetes.operator.work.Step;
import oracle.kubernetes.operator.work.TerminalStep;
import oracle.kubernetes.utils.TestUtils;
import oracle.kubernetes.weblogic.domain.model.DomainCommonConfigurator;
import oracle.kubernetes.weblogic.domain.model.DomainCondition;
import oracle.kubernetes.weblogic.domain.model.DomainFailureReason;
import oracle.kubernetes.weblogic.domain.model.DomainResource;
import oracle.kubernetes.weblogic.domain.model.DomainStatus;
import oracle.kubernetes.weblogic.domain.model.DomainValidationMessages;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static oracle.kubernetes.operator.DomainFailureMessages.createReplicaFailureMessage;
import static oracle.kubernetes.operator.DomainProcessorTestSetup.NS;
import static oracle.kubernetes.operator.ProcessingConstants.DOMAIN_TOPOLOGY;
import static oracle.kubernetes.weblogic.domain.model.DomainConditionType.FAILED;
import static oracle.kubernetes.weblogic.domain.model.DomainFailureReason.DOMAIN_INVALID;
import static oracle.kubernetes.weblogic.domain.model.DomainFailureReason.INTROSPECTION;
import static oracle.kubernetes.weblogic.domain.model.DomainFailureReason.REPLICAS_TOO_HIGH;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;

class FailureReportingTest {

  private static final String INFO_MESSAGE = "@[INFO] just letting you know";
  private static final String SEVERE_MESSAGE = "@[SEVERE] really bad";
  public static final String CLUSTER_1 = "cluster1";
  public static final int MAXIMUM_CLUSTER_SIZE = 5;
  public static final int TOO_HIGH_REPLICA_COUNT = MAXIMUM_CLUSTER_SIZE + 1;
  private final DomainResource domain = DomainProcessorTestSetup.createTestDomain();
  private final DomainPresenceInfo info = new DomainPresenceInfo(domain);
  private final KubernetesTestSupport testSupport = new KubernetesTestSupport();
  private final List<Memento> mementos = new ArrayList<>();
  private final MakeRightExecutorStub executor = Stub.createNiceStub(MakeRightExecutorStub.class);
  private final DomainProcessorDelegateStub delegate
      = Stub.createStrictStub(DomainProcessorDelegateStub.class, testSupport);
  private final MakeRightDomainOperation makeRight = new MakeRightDomainOperationImpl(executor, delegate, info);
  private final TerminalStep terminalStep = new TerminalStep();
  private String introspectionString = INFO_MESSAGE;
  private Step steps;

  @BeforeEach
  void setUp() throws NoSuchFieldException {
    mementos.add(TestUtils.silenceOperatorLogger());
    mementos.add(testSupport.install());
    mementos.add(TuningParametersStub.install());
    mementos.add(UnitTestHash.install());

    testSupport.defineResources(domain);
    defineDomainTopology();

    DomainProcessorTestSetup.defineRequiredResources(testSupport);
    steps = makeRight.createSteps();
  }

  private void defineDomainTopology() {
    WlsDomainConfigSupport configSupport = new WlsDomainConfigSupport("domain");
    configSupport.addWlsServer("admin", 8045);
    configSupport.setAdminServerName("admin");
    configSupport.addWlsCluster(
          new WlsDomainConfigSupport.DynamicClusterConfigBuilder(CLUSTER_1)
                .withClusterLimits(0, MAXIMUM_CLUSTER_SIZE)
                .withServerNames("ms1", "ms2", "ms3", "ms4", "ms5"));
    testSupport.addToPacket(DOMAIN_TOPOLOGY, configSupport.createDomainConfig());
  }

  @AfterEach
  void tearDown() {
    mementos.forEach(Memento::revert);
  }

  @Test
  void withNoProblems_reachEndOfFiber() {
    executeMakeRight(null, test -> {});

    assertThat(terminalStep.wasRun(), is(true));
  }

  @ParameterizedTest
  @EnumSource(
      value = TestCase.class, names = "DOMAIN_VALIDATION_FAILURE"
  )
  void abortMakeRightAfterFailureDetected(TestCase testCase) {
    executeMakeRight(testCase.getStepToAvoid(), testCase.getMutator());

    assertThat(terminalStep.wasRun(), is(false));
  }

  private void executeMakeRight(String stepClassName, Consumer<FailureReportingTest> mutator) {
    steps.insertBefore(terminalStep, stepClassName);
    mutator.accept(this);
    executeMakeRight();
  }

  private void executeMakeRight() {
    copyFrom(testSupport.getPacket(), makeRight.createPacket());
    IntrospectionTestUtils.defineIntrospectionPodLog(testSupport, introspectionString);

    testSupport.runSteps(steps);
  }

  private void removeCredentialsSecret() {
    DomainCommonConfigurator commonConfigurator = new DomainCommonConfigurator(domain);
    commonConfigurator.withWebLogicCredentialsSecret("no-such-secret");
  }

  private void copyFrom(Packet target, Packet source) {
    target.getComponents().putAll(source.getComponents());
    target.putAll(source);
  }

  private void defineSevereIntrospectionFailure() {
    introspectionString = SEVERE_MESSAGE;
  }

  @ParameterizedTest
  @EnumSource(
      value = TestCase.class, names = {"DOMAIN_VALIDATION_FAILURE", "REPLICAS_TOO_HIGH_FAILURE"}
  )
  void whenMakeWithExistingFailureFails_failuresDoNotFlicker(TestCase testCase) throws NoSuchFieldException {
    final DomainCondition domainCondition = createDomainConditionFor(testCase);
    final FlickerDetector detector = new FlickerDetector(testCase.getReason(), domainCondition);
    domain.getStatus().addCondition(domainCondition);
    mementos.add(detector.install());
    testCase.getMutator().accept(this);

    executeMakeRight();

    detector.assertNotFlickered(testSupport.getPacket());
  }

  private DomainCondition createDomainConditionFor(TestCase testCase) {
    return new DomainCondition(FAILED).withReason(testCase.getReason()).withMessage(testCase.getExpectedMessage());
  }

  private void setReplicasTooHigh() {
    domain.getSpec().setReplicas(TOO_HIGH_REPLICA_COUNT);
  }

  enum TestCase {
    DOMAIN_VALIDATION_FAILURE {
      @Override
      Consumer<FailureReportingTest> getMutator() {
        return FailureReportingTest::removeCredentialsSecret;
      }

      @Override
      DomainFailureReason getReason() {
        return DOMAIN_INVALID;
      }

      @Override
      String getStepToAvoid() {
        return "StartPlanStep";
      }

      @Override
      String getExpectedMessage() {
        return DomainValidationMessages.noSuchSecret("no-such-secret", NS, SecretType.WEBLOGIC_CREDENTIALS);
      }
    },
    SEVERE_INTROSPECTION_FAILURE {
      @Override
      Consumer<FailureReportingTest> getMutator() {
        return FailureReportingTest::defineSevereIntrospectionFailure;
      }

      @Override
      DomainFailureReason getReason() {
        return INTROSPECTION;
      }

      @Override
      String getStepToAvoid() {
        return "BeforeAdminServiceStep";
      }

      @Override
      String getExpectedMessage() {
        return null;
      }
    },
    REPLICAS_TOO_HIGH_FAILURE {
      @Override
      Consumer<FailureReportingTest> getMutator() {
        return FailureReportingTest::setReplicasTooHigh;
      }

      @Override
      DomainFailureReason getReason() {
        return REPLICAS_TOO_HIGH;
      }

      @Override
      String getStepToAvoid() {
        return null;
      }

      @Override
      String getExpectedMessage() {
        return createReplicaFailureMessage(CLUSTER_1, TOO_HIGH_REPLICA_COUNT, MAXIMUM_CLUSTER_SIZE);
      }
    };

    abstract DomainFailureReason getReason();
    
    abstract Consumer<FailureReportingTest> getMutator();

    abstract String getStepToAvoid();

    abstract String getExpectedMessage();
  }


  static class FlickerDetector {
    private final DomainFailureReason reason;
    private final Set<DomainCondition> expectedConditions;
    private Step currentStep;
    private Step flickeredStep;

    FlickerDetector(DomainFailureReason reason, DomainCondition... expectedConditionList) {
      this.reason = reason;
      this.expectedConditions = Set.of(expectedConditionList);
    }

    Memento install() throws NoSuchFieldException {
      final BiConsumer<NextAction,String> detector = this::detectFlicker;
      return StaticStubSupport.install(Fiber.class, "preApplyReport", detector);
    }

    void detectFlicker(NextAction nextAction, String fiberName) {
      final Set<DomainCondition> conditions = getMatchingConditions(nextAction);
      if (flickeredStep != null) { // already found a problem; no need to keep looking
        return;
      }

      if (!expectedConditions.equals(conditions)) {
        flickeredStep = currentStep;
      } else if (!(nextAction.getNext() instanceof SimulatedStep)) {
        currentStep = nextAction.getNext();
      }
    }

    private Set<DomainCondition> getMatchingConditions(NextAction nextAction) {
      return getMatchingConditions(nextAction.getPacket());
    }

    @NotNull
    private Set<DomainCondition> getMatchingConditions(Packet packet) {
      return DomainPresenceInfo.fromPacket(packet)
          .map(DomainPresenceInfo::getDomain)
          .map(DomainResource::getStatus)
          .map(DomainStatus::getConditions)
          .orElse(Collections.emptyList()).stream()
          .filter(this::hasReason)
          .collect(Collectors.toSet());
    }

    void assertNotFlickered(Packet packet) {
      assertThat("Flicker step", flickeredStep, nullValue());
      assertThat("Final conditions", getMatchingConditions(packet), equalTo(expectedConditions));
    }

    private boolean hasReason(DomainCondition condition) {
      return reason.equals(condition.getReason());
    }
  }

  abstract static class MakeRightExecutorStub implements MakeRightExecutor {

    @Override
    public void registerDomainPresenceInfo(DomainPresenceInfo info) {
      
    }

    @Override
    public Step createNamespacedResourceSteps(Processors processors, DomainPresenceInfo info) {
      return null;
    }
  }

}

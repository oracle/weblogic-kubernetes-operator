// Copyright (c) 2022, 2025, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.makeright;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;

import com.google.gson.Gson;
import com.meterware.simplestub.Memento;
import com.meterware.simplestub.StaticStubSupport;
import com.meterware.simplestub.Stub;
import io.kubernetes.client.openapi.models.V1Pod;
import io.kubernetes.client.openapi.models.V1PodCondition;
import io.kubernetes.client.openapi.models.V1PodStatus;
import oracle.kubernetes.operator.CoreDelegate;
import oracle.kubernetes.operator.DomainNamespaces;
import oracle.kubernetes.operator.DomainProcessorDelegateStub;
import oracle.kubernetes.operator.DomainProcessorTestSetup;
import oracle.kubernetes.operator.MakeRightDomainOperation;
import oracle.kubernetes.operator.MakeRightExecutor;
import oracle.kubernetes.operator.Processors;
import oracle.kubernetes.operator.ServerStartPolicy;
import oracle.kubernetes.operator.calls.SimulatedStep;
import oracle.kubernetes.operator.helpers.DomainPresenceInfo;
import oracle.kubernetes.operator.helpers.EventHelper;
import oracle.kubernetes.operator.helpers.KubernetesTestSupport;
import oracle.kubernetes.operator.helpers.SecretType;
import oracle.kubernetes.operator.helpers.UnitTestHash;
import oracle.kubernetes.operator.introspection.IntrospectionTestUtils;
import oracle.kubernetes.operator.logging.LoggingFacade;
import oracle.kubernetes.operator.logging.LoggingFactory;
import oracle.kubernetes.operator.tuning.TuningParametersStub;
import oracle.kubernetes.operator.utils.WlsDomainConfigSupport;
import oracle.kubernetes.operator.work.Fiber;
import oracle.kubernetes.operator.work.Packet;
import oracle.kubernetes.operator.work.Step;
import oracle.kubernetes.operator.work.TerminalStep;
import oracle.kubernetes.utils.SystemClock;
import oracle.kubernetes.utils.SystemClockTestSupport;
import oracle.kubernetes.utils.TestUtils;
import oracle.kubernetes.weblogic.domain.model.DomainCommonConfigurator;
import oracle.kubernetes.weblogic.domain.model.DomainCondition;
import oracle.kubernetes.weblogic.domain.model.DomainFailureReason;
import oracle.kubernetes.weblogic.domain.model.DomainResource;
import oracle.kubernetes.weblogic.domain.model.DomainStatus;
import oracle.kubernetes.weblogic.domain.model.DomainValidationMessages;
import oracle.kubernetes.weblogic.domain.model.InitializeDomainOnPV;
import oracle.kubernetes.weblogic.domain.model.ManagedServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static oracle.kubernetes.common.logging.MessageKeys.MAKE_RIGHT_WILL_RETRY;
import static oracle.kubernetes.common.logging.MessageKeys.NO_MANAGED_SERVER_IN_DOMAIN;
import static oracle.kubernetes.operator.DomainFailureMessages.createReplicaFailureMessage;
import static oracle.kubernetes.operator.DomainProcessorTestSetup.NS;
import static oracle.kubernetes.operator.EventMatcher.hasEvent;
import static oracle.kubernetes.operator.ProcessingConstants.DOMAIN_INTROSPECTION_COMPLETE;
import static oracle.kubernetes.operator.ProcessingConstants.DOMAIN_TOPOLOGY;
import static oracle.kubernetes.operator.ProcessingConstants.FATAL_INTROSPECTOR_ERROR_MSG;
import static oracle.kubernetes.weblogic.domain.model.DomainConditionMatcher.hasCondition;
import static oracle.kubernetes.weblogic.domain.model.DomainConditionType.COMPLETED;
import static oracle.kubernetes.weblogic.domain.model.DomainConditionType.FAILED;
import static oracle.kubernetes.weblogic.domain.model.DomainFailureReason.ABORTED;
import static oracle.kubernetes.weblogic.domain.model.DomainFailureReason.DOMAIN_INVALID;
import static oracle.kubernetes.weblogic.domain.model.DomainFailureReason.INTROSPECTION;
import static oracle.kubernetes.weblogic.domain.model.DomainFailureReason.REPLICAS_TOO_HIGH;
import static oracle.kubernetes.weblogic.domain.model.DomainFailureReason.TOPOLOGY_MISMATCH;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;

class FailureReportingTest {

  private static final LoggingFacade LOGGER = LoggingFactory.getLogger("Operator", "Operator");

  private static final String INFO_MESSAGE = "@[INFO] just letting you know. " + DOMAIN_INTROSPECTION_COMPLETE;
  private static final String SEVERE_MESSAGE = "really bad";
  private static final String SEVERE_INTROSPECTION_ENTRY = "@[SEVERE] " + SEVERE_MESSAGE;
  private static final String CLUSTER_1 = "cluster1";
  private static final int MAXIMUM_CLUSTER_SIZE = 5;
  private static final int TOO_HIGH_REPLICA_COUNT = MAXIMUM_CLUSTER_SIZE + 1;
  private final DomainResource domain = DomainProcessorTestSetup.createTestDomain();
  private final DomainPresenceInfo info = new DomainPresenceInfo(domain);
  private final KubernetesTestSupport testSupport = new KubernetesTestSupport();
  private final List<Memento> mementos = new ArrayList<>();
  private final MakeRightExecutorStub executor = Stub.createNiceStub(MakeRightExecutorStub.class);
  private final DomainProcessorDelegateStub delegate
      = Stub.createStrictStub(DomainProcessorDelegateStub.class, testSupport);
  private final MakeRightDomainOperation makeRight = new MakeRightDomainOperationImpl(executor, delegate, info)
      .withEventData(new EventHelper.EventData(EventHelper.EventItem.DOMAIN_CHANGED));
  private final TerminalStep terminalStep = new TerminalStep();
  private String introspectionString = INFO_MESSAGE;
  private Step steps;

  @BeforeEach
  void setUp() throws NoSuchFieldException {
    mementos.add(TestUtils.silenceOperatorLogger());
    mementos.add(testSupport.install());
    mementos.add(TuningParametersStub.install());
    mementos.add(UnitTestHash.install());
    mementos.add(SystemClockTestSupport.installClock());

    testSupport.defineResources(domain);
    defineDomainTopology();

    DomainProcessorTestSetup.defineRequiredResources(testSupport);
    steps = makeRight.createSteps();
    testSupport.doOnCreate(KubernetesTestSupport.POD, p -> setPodReady((V1Pod) p));
  }

  private void setPodReady(V1Pod pod) {
    pod.status(updatePodReadyStatus(Optional.of(pod).map(V1Pod::getStatus).orElse(new V1PodStatus())));
  }

  private V1PodStatus updatePodReadyStatus(V1PodStatus status) {
    return status.phase("Running").addConditionsItem(new V1PodCondition().status("True").type("Ready"));
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
    executeMakeRight(test -> {});

    assertThat(terminalStep.wasRun(), is(true));
  }

  @Test
  void whenReplicasTooHighDetected_reachEndOfFiber() {
    executeMakeRight(TestCase.REPLICAS_TOO_HIGH_FAILURE.getMutator());

    assertThat(terminalStep.wasRun(), is(true));
  }

  @ParameterizedTest
  @EnumSource(
      value = TestCase.class, names = {
          "DOMAIN_VALIDATION_FAILURE", "SEVERE_INTROSPECTION_FAILURE", "TOPOLOGY_MISMATCH_FAILURE"
      }
  )
  void abortMakeRightAfterFailureDetected(TestCase testCase) throws NoSuchFieldException {
    final AbortMakeRightDetector abortMakeRightDetector = new AbortMakeRightDetector(testCase.getStepToAvoid());
    mementos.add(abortMakeRightDetector.install());

    executeMakeRight(testCase.getMutator());

    assertThat(abortMakeRightDetector.reachedBannedStep, nullValue());
  }

  private void executeMakeRight(Consumer<FailureReportingTest> mutator) {
    mutator.accept(this);
    executeMakeRight();
  }

  private void executeMakeRight(String testCase) {
    copyFrom(testSupport.getPacket(), makeRight.createPacket());
    if (!TestCase.INITIALIZE_DOMAIN_ON_PV_INTROSPECTION_FAILURE.toString().equals(testCase)) {
      IntrospectionTestUtils.defineIntrospectionPodLog(testSupport, introspectionString);
    }
    testSupport.runSteps(steps, terminalStep);
  }

  private void executeMakeRight() {
    executeMakeRight((String) null);
  }

  private void updateDomain() {
    removeCredentialsSecret();
    domain.getMetadata().setCreationTimestamp(SystemClock.now().minusSeconds(20));
  }

  private void removeCredentialsSecret() {
    DomainCommonConfigurator commonConfigurator = new DomainCommonConfigurator(domain);
    commonConfigurator.withWebLogicCredentialsSecret("no-such-secret");
  }

  private void copyFrom(Packet target, Packet source) {
    target.putAll(source);
  }

  private void defineSevereIntrospectionFailure() {
    introspectionString = SEVERE_INTROSPECTION_ENTRY;
  }

  private void defineSevereIntrospectionFailureForInitDomainOnPV() {
    DomainCommonConfigurator commonConfigurator = new DomainCommonConfigurator(domain);
    commonConfigurator.withConfigurationForInitializeDomainOnPV(
        new InitializeDomainOnPV(), "test-volume", "test-pvc", "/shared");
    introspectionString = SEVERE_INTROSPECTION_ENTRY;
  }

  @ParameterizedTest
  @EnumSource(TestCase.class)
  void whenMakeWithExistingFailureFails_failuresDoNotFlicker(TestCase testCase) throws NoSuchFieldException {
    final DomainCondition domainCondition = createDomainConditionFor(testCase);
    final FlickerDetector detector = new FlickerDetector(testCase.getReason(), domainCondition);
    domain.getStatus().addCondition(domainCondition);
    mementos.add(detector.install());
    testCase.getMutator().accept(this);

    executeMakeRight(testCase.toString());

    detector.assertNotFlickered(testSupport.getPacket());
  }

  private DomainCondition createDomainConditionFor(TestCase testCase) {
    return new DomainCondition(FAILED).withReason(testCase.getReason())
        .withFailureInfo(domain.getSpec()).withMessage(testCase.getExpectedMessage());
  }

  @ParameterizedTest
  @EnumSource(TestCase.class)
  void whenFailureReported_setCompletedFalse(TestCase testCase) {
    domain.getStatus().addCondition(new DomainCondition(COMPLETED).withStatus(true));
    testCase.getMutator().accept(this);

    executeMakeRight();

    assertThat(domain, hasCondition(COMPLETED).withStatus("False"));
  }

  @ParameterizedTest
  @EnumSource(TestCase.class)
  void afterFailureReported_canSerializeAndDeserializeDomain(TestCase testCase) {
    domain.getStatus().addCondition(new DomainCondition(COMPLETED).withStatus(true));
    testCase.getMutator().accept(this);

    executeMakeRight();
    final Gson gson = new Gson();
    final String json = gson.toJson(domain);

    assertThat(gson.fromJson(json, DomainResource.class), equalTo(domain));
  }

  @ParameterizedTest
  @EnumSource(TestCase.class)
  void whenFailureReported_domainSummaryMessageIncludesRetryTime(TestCase testCase) {
    final OffsetDateTime makeRightTime = SystemClock.now();
    testCase.getMutator().accept(this);

    executeMakeRight();

    if (testCase.isRetryable()) {
      assertThat(domain.getStatus().getMessage(), containsString(createRetryMessageAugmentation(makeRightTime)));
    } else {
      assertThat(domain.getStatus().getMessage().toLowerCase(), not(containsString("will retry")));
    }
  }

  private String createRetryMessageAugmentation(OffsetDateTime makeRightTime) {
    return LOGGER.formatMessage(MAKE_RIGHT_WILL_RETRY, "",
        domain.getNextRetryTime(),
        domain.getFailureRetryIntervalSeconds(),
        makeRightTime.plusMinutes(domain.getFailureRetryLimitMinutes()));
  }

  @ParameterizedTest
  @EnumSource(TestCase.class)
  void whenMakeWithExistingFailureFails_createFailedEvent(TestCase testCase) {
    testCase.getMutator().accept(this);

    executeMakeRight();

    assertThat(testSupport, hasEvent("Failed").withMessageContaining(testCase.getExpectedMessage()));
  }

  enum TestCase {
    DOMAIN_VALIDATION_FAILURE {
      @Override
      Consumer<FailureReportingTest> getMutator() {
        return FailureReportingTest::updateDomain;
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
      @Nonnull
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
      @Nonnull
      String getExpectedMessage() {
        return SEVERE_MESSAGE;
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
      @Nonnull
      String getExpectedMessage() {
        return createReplicaFailureMessage(CLUSTER_1, TOO_HIGH_REPLICA_COUNT, MAXIMUM_CLUSTER_SIZE);
      }

      @Override
      boolean isRetryable() {
        return false;
      }
    },
    TOPOLOGY_MISMATCH_FAILURE {
      @Override
      Consumer<FailureReportingTest> getMutator() {
        return FailureReportingTest::configureUnknownServer;
      }

      @Override
      DomainFailureReason getReason() {
        return TOPOLOGY_MISMATCH;
      }

      @Override
      String getStepToAvoid() {
        return "BeforeAdminService";
      }

      @Override
      @Nonnull
      String getExpectedMessage() {
        return LOGGER.formatMessage(NO_MANAGED_SERVER_IN_DOMAIN, "No-such-server");
      }
    },
    INITIALIZE_DOMAIN_ON_PV_INTROSPECTION_FAILURE {
      @Override
      Consumer<FailureReportingTest> getMutator() {
        return FailureReportingTest::defineSevereIntrospectionFailureForInitDomainOnPV;
      }

      @Override
      DomainFailureReason getReason() {
        return ABORTED;
      }

      @Override
      String getStepToAvoid() {
        return "BeforeAdminServiceStep";
      }

      @Override
      @Nonnull
      String getExpectedMessage() {
        return FATAL_INTROSPECTOR_ERROR_MSG + SEVERE_MESSAGE;
      }

      @Override
      boolean isRetryable() {
        return false;
      }
    };

    abstract DomainFailureReason getReason();

    abstract Consumer<FailureReportingTest> getMutator();

    abstract String getStepToAvoid();

    @Nonnull
    abstract String getExpectedMessage();

    boolean isRetryable() {
      return true;
    }
  }

  private void setReplicasTooHigh() {
    domain.getSpec().setReplicas(TOO_HIGH_REPLICA_COUNT);
  }

  private void configureUnknownServer() {
    final ManagedServer managedServer = new ManagedServer().withServerName("No-such-server");
    managedServer.setServerStartPolicy(ServerStartPolicy.IF_NEEDED);
    domain.getSpec().getManagedServers().add(managedServer);
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
      final Step.StepAdapter detector = this::detectFlicker;
      return StaticStubSupport.install(Step.class, "adapter", detector);
    }

    Step detectFlicker(Fiber fiber, Step step, Packet packet) {
      final Set<DomainCondition> conditions = getMatchingConditions(packet);
      if (flickeredStep != null) { // already found a problem; no need to keep looking
        throw new IllegalStateException();
      }

      if (!expectedConditions.equals(conditions)) {
        flickeredStep = currentStep;
      } else if (!(step instanceof SimulatedStep)) {
        currentStep = step;
      }
      return step;
    }

    @Nonnull
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

  static class AbortMakeRightDetector {
    final String stepClassName;
    Step reachedBannedStep;

    AbortMakeRightDetector(String stepClassName) {
      this.stepClassName = stepClassName;
    }

    Memento install() throws NoSuchFieldException {
      final Step.StepAdapter detector = this::detectBannedStep;
      return StaticStubSupport.install(Step.class, "adapter", detector);
    }

    Step detectBannedStep(Fiber fiber, Step step, Packet packet) {
      if (reachedBannedStep == null && isSpecifiedStep(step)) {
        reachedBannedStep = step;
      }
      return step;
    }

    private boolean isSpecifiedStep(Step step) {
      return stepClassName != null && hasSpecifiedClassName(step.getClass().getName());
    }

    private boolean hasSpecifiedClassName(String name) {
      return name.endsWith("." + stepClassName) || name.endsWith("$" + stepClassName);
    }
  }

  abstract static class MakeRightExecutorStub implements MakeRightExecutor {

    @Override
    public void registerDomainPresenceInfo(DomainPresenceInfo info) {

    }

    @Override
    public Step createNamespacedResourceSteps(
        CoreDelegate delegate, Processors processors, DomainPresenceInfo info, DomainNamespaces ns) {
      return null;
    }
  }

}

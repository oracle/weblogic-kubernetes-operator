// Copyright (c) 2023, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helpers;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.LogRecord;

import com.meterware.simplestub.Memento;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1PersistentVolumeClaim;
import oracle.kubernetes.operator.DomainProcessorDelegateStub;
import oracle.kubernetes.operator.DomainSourceType;
import oracle.kubernetes.operator.KubernetesConstants;
import oracle.kubernetes.operator.LabelConstants;
import oracle.kubernetes.operator.PvcAwaiterStepFactory;
import oracle.kubernetes.operator.calls.UnrecoverableCallException;
import oracle.kubernetes.operator.calls.unprocessable.UnrecoverableErrorBuilderImpl;
import oracle.kubernetes.operator.utils.WlsDomainConfigSupport;
import oracle.kubernetes.operator.wlsconfig.WlsDomainConfig;
import oracle.kubernetes.operator.work.Packet;
import oracle.kubernetes.operator.work.Step;
import oracle.kubernetes.operator.work.TerminalStep;
import oracle.kubernetes.utils.TestUtils;
import oracle.kubernetes.weblogic.domain.DomainConfigurator;
import oracle.kubernetes.weblogic.domain.DomainConfiguratorFactory;
import oracle.kubernetes.weblogic.domain.model.DomainResource;
import oracle.kubernetes.weblogic.domain.model.DomainSpec;
import oracle.kubernetes.weblogic.domain.model.InitializeDomainOnPV;
import oracle.kubernetes.weblogic.domain.model.PersistentVolumeClaim;
import oracle.kubernetes.weblogic.domain.model.PersistentVolumeClaimSpec;
import org.hamcrest.Description;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.meterware.simplestub.Stub.createStrictStub;
import static oracle.kubernetes.common.logging.MessageKeys.KUBERNETES_EVENT_ERROR;
import static oracle.kubernetes.common.logging.MessageKeys.PVC_CREATED;
import static oracle.kubernetes.common.logging.MessageKeys.PVC_EXISTS;
import static oracle.kubernetes.common.utils.LogMatcher.containsFine;
import static oracle.kubernetes.common.utils.LogMatcher.containsInfo;
import static oracle.kubernetes.operator.DomainStatusMatcher.hasStatus;
import static oracle.kubernetes.operator.EventTestUtils.getExpectedEventMessage;
import static oracle.kubernetes.operator.EventTestUtils.getLocalizedString;
import static oracle.kubernetes.operator.KubernetesConstants.HTTP_CONFLICT;
import static oracle.kubernetes.operator.KubernetesConstants.HTTP_INTERNAL_ERROR;
import static oracle.kubernetes.operator.ProcessingConstants.DOMAIN_TOPOLOGY;
import static oracle.kubernetes.operator.ProcessingConstants.PVCWATCHER_COMPONENT_NAME;
import static oracle.kubernetes.operator.helpers.EventHelper.EventItem.DOMAIN_FAILED;
import static oracle.kubernetes.operator.helpers.KubernetesTestSupport.PODDISRUPTIONBUDGET;
import static oracle.kubernetes.operator.helpers.KubernetesTestSupport.PVC;
import static oracle.kubernetes.operator.helpers.StepContextConstants.READ_WRITE_MANY;
import static oracle.kubernetes.weblogic.domain.model.DomainFailureReason.KUBERNETES;
import static oracle.kubernetes.weblogic.domain.model.DomainValidationTest.createResources;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.stringContainsInOrder;
import static org.hamcrest.junit.MatcherAssert.assertThat;

@SuppressWarnings("ConstantConditions")
class PersistentVolumeClaimHelperTest {

  static final String DOMAIN_NAME = "domain1";
  static final String NS = "namespace";
  static final String UID = "uid1";
  static final String KUBERNETES_UID = "12345";
  final List<Memento> mementos = new ArrayList<>();
  final DomainPresenceInfo domainPresenceInfo = createPresenceInfo();
  private static final String[] MESSAGE_KEYS = {
      PVC_EXISTS,
      PVC_CREATED,
  };

  private static final TerminalStep terminalStep = new TerminalStep();
  private final KubernetesTestSupport testSupport = new KubernetesTestSupport();
  private final RetryStrategyStub retryStrategy = createStrictStub(RetryStrategyStub.class);
  private final List<LogRecord> logRecords = new ArrayList<>();
  private TestUtils.ConsoleHandlerMemento consoleHandlerMemento;

  @BeforeEach
  public void setUp() throws Exception {
    mementos.add(
        consoleHandlerMemento =
            TestUtils.silenceOperatorLogger()
                .collectLogMessages(logRecords, MESSAGE_KEYS)
                .withLogLevel(Level.FINE)
                .ignoringLoggedExceptions(ApiException.class));
    mementos.add(testSupport.install());

    WlsDomainConfigSupport configSupport = new WlsDomainConfigSupport(DOMAIN_NAME);

    WlsDomainConfig domainConfig = configSupport.createDomainConfig();
    testSupport
        .addToPacket(DOMAIN_TOPOLOGY, domainConfig)
        .addComponent(PVCWATCHER_COMPONENT_NAME, PvcAwaiterStepFactory.class,
            new DomainProcessorDelegateStub.TestPvcAwaiterStepFactory())
        .addDomainPresenceInfo(domainPresenceInfo);
    configureDomain();
  }

  private DomainConfigurator configureDomain() {
    return configureDomain(domainPresenceInfo);
  }

  private DomainConfigurator configureDomain(DomainPresenceInfo domainPresenceInfo) {
    return DomainConfiguratorFactory.forDomain(domainPresenceInfo.getDomain())
        .withDomainHomeSourceType(DomainSourceType.PERSISTENT_VOLUME)
        .withInitializeDomainOnPv(new InitializeDomainOnPV().persistentVolumeClaim(createPvc()));
  }

  private PersistentVolumeClaim createPvc() {
    return new PersistentVolumeClaim().spec(new PersistentVolumeClaimSpec().storageClassName("SC")
        .resources(createResources())).metadata(new V1ObjectMeta().name("Test"));
  }

  @AfterEach
  public void tearDown() throws Exception {
    mementos.forEach(Memento::revert);
    testSupport.throwOnCompletionFailure();
  }

  private DomainPresenceInfo createPresenceInfo() {
    return new DomainPresenceInfo(
            new DomainResource()
                    .withApiVersion(KubernetesConstants.DOMAIN_GROUP + "/" + KubernetesConstants.DOMAIN_VERSION)
                    .withKind(KubernetesConstants.DOMAIN)
                    .withMetadata(new V1ObjectMeta().namespace(NS).name(DOMAIN_NAME).uid(KUBERNETES_UID))
                    .withSpec(createDomainSpec()));
  }

  private DomainSpec createDomainSpec() {
    return new DomainSpec().withDomainUid(UID);
  }

  @Test
  void whenCreated_modelHasCorrectAccessMode() {
    V1PersistentVolumeClaim model = createPvcModel(testSupport.getPacket());

    Map<String, String> labels = new HashMap<>();
    labels.put(LabelConstants.CREATEDBYOPERATOR_LABEL, "true");
    labels.put(LabelConstants.DOMAINUID_LABEL, UID);
    assertThat(
        model.getSpec().getAccessModes(), is(Collections.singletonList(READ_WRITE_MANY)));
  }

  @Test
  void whenCreated_modelMetadataHasExpectedLabels() {
    V1PersistentVolumeClaim model = createPvcModel(testSupport.getPacket());

    assertThat(
            model.getMetadata().getLabels(), allOf(hasEntry(LabelConstants.CREATEDBYOPERATOR_LABEL, "true"),
                    hasEntry(LabelConstants.DOMAINUID_LABEL, UID)));
  }

  @Test
  void whenCreated_modelHasExpectedStorageClass() {
    V1PersistentVolumeClaim model = createPvcModel(testSupport.getPacket());

    assertThat(model.getSpec().getStorageClassName(), equalTo("SC"));
  }

  @Test
  void onRunWithNoPersistentVolumeClaim_logCreatedMessage() {
    runPersistentVolumeClaimHelper();

    assertThat(logRecords, containsInfo(getPvcCreateLogMessage()));
  }

  @Test
  void onRunWithNoPersistentVolumeClaim_createIt() {
    domainPresenceInfo.getDomain().getInitializeDomainOnPV().waitForPvcToBind(false);
    consoleHandlerMemento.ignoreMessage(getPvcCreateLogMessage());

    runPersistentVolumeClaimHelper();

    assertThat(
            getPersistentVolumeClaimResource(domainPresenceInfo),
            is(persistentVolumeClaimWithName("Test")));
  }

  @Test
  void onFailedRun_reportFailure() {
    testSupport.addRetryStrategy(retryStrategy);
    testSupport.failOnCreate(PVC, NS, HTTP_INTERNAL_ERROR);

    runPersistentVolumeClaimHelper();

    testSupport.verifyCompletionThrowable(UnrecoverableCallException.class);
  }

  @Test
  void onFailedRunWithConflictAndNoExistingPVC_createItOnRetry() {
    consoleHandlerMemento.ignoreMessage(getPvcCreateLogMessage());
    consoleHandlerMemento.ignoreMessage(getPvcExistsLogMessage());
    retryStrategy.setNumRetriesLeft(1);
    testSupport.addRetryStrategy(retryStrategy);
    testSupport.failOnCreate(PVC, NS, HTTP_CONFLICT);

    runPersistentVolumeClaimHelper();

    assertThat(
            getPersistentVolumeClaimResource(domainPresenceInfo),
            is(persistentVolumeClaimWithName("Test")));
  }

  @Test
  void onFailedRunWithConflictAndExistingPVC_retryAndLogMessage() {
    consoleHandlerMemento.ignoreMessage(getPvcExistsLogMessage());
    V1PersistentVolumeClaim existingPdb = createPvcModel(testSupport.getPacket());
    existingPdb.getMetadata().setNamespace(NS);
    retryStrategy.setNumRetriesLeft(1);
    testSupport.addRetryStrategy(retryStrategy);
    testSupport.failOnCreate(PODDISRUPTIONBUDGET, NS, HTTP_CONFLICT);
    testSupport.defineResources(existingPdb);

    runPersistentVolumeClaimHelper();

    assertThat(
        getPersistentVolumeClaimResource(domainPresenceInfo),
        is(persistentVolumeClaimWithName("Test")));
  }

  @Test
  void onFailedRunWithConflictAndExistingPvc_retryAndLogMessage() {
    consoleHandlerMemento.ignoreMessage(getPvcExistsLogMessage());
    V1PersistentVolumeClaim existingPvc = createPvcModel(testSupport.getPacket());
    existingPvc.getMetadata().setNamespace(NS);
    retryStrategy.setNumRetriesLeft(1);
    testSupport.addRetryStrategy(retryStrategy);
    testSupport.failOnCreate(PVC, NS, HTTP_CONFLICT);
    testSupport.defineResources(existingPvc);

    runPersistentVolumeClaimHelper();

    assertThat(
            getPersistentVolumeClaimResource(domainPresenceInfo),
            is(persistentVolumeClaimWithName("Test")));
  }

  @Test
  void whenPersistentVolumeClaimCreationFailsDueToUnprocessableEntityFailure_reportInDomainStatus() {
    testSupport.defineResources(domainPresenceInfo.getDomain());
    testSupport.failOnCreate(PVC, NS, new UnrecoverableErrorBuilderImpl()
            .withReason("FieldValueNotFound")
            .withMessage("Test this failure")
            .build());

    runPersistentVolumeClaimHelper();

    assertThat(getDomain(), hasStatus().withReason(KUBERNETES)
            .withMessageContaining("create", PVC.toLowerCase(), NS, "Test this failure"));
  }

  @Test
  void whenPersistentVolumeClaimCreationFailsDueToUnprocessableEntityFailure_generateFailedEvent() {
    testSupport.defineResources(domainPresenceInfo.getDomain());
    testSupport.failOnCreate(PVC, NS, new UnrecoverableErrorBuilderImpl()
        .withReason("FieldValueNotFound")
        .withMessage("Test this failure")
        .build());

    runPersistentVolumeClaimHelper();

    assertThat(
        "Expected Event " + DOMAIN_FAILED + " expected with message not found",
        getExpectedEventMessage(testSupport, DOMAIN_FAILED),
        stringContainsInOrder("Domain", UID, "failed due to",
            getLocalizedString(KUBERNETES_EVENT_ERROR)));
  }

  @Test
  void whenPersistentVolumeClaimCreationFailsDueToUnprocessableEntityFailure_abortFiber() {
    testSupport.defineResources(domainPresenceInfo.getDomain());
    testSupport.failOnCreate(PVC, NS, new UnrecoverableErrorBuilderImpl()
            .withReason("FieldValueNotFound")
            .withMessage("Test this failure")
            .build());

    runPersistentVolumeClaimHelper();

    assertThat(terminalStep.wasRun(), is(false));
  }

  public V1PersistentVolumeClaim createPvcModel(Packet packet) {
    return new PersistentVolumeClaimHelper.PersistentVolumeClaimContext(null, packet)
        .createModel();
  }

  public String getPvcCreateLogMessage() {
    return PVC_CREATED;
  }

  private void runPersistentVolumeClaimHelper() {
    testSupport.runSteps(createSteps(null));
  }

  public Step createSteps(Step next) {
    return PersistentVolumeClaimHelper.createPersistentVolumeClaimStep(next);
  }

  public V1PersistentVolumeClaim getPersistentVolumeClaimResource(DomainPresenceInfo info) {
    return testSupport.getResourceWithName(PVC, "Test");
  }

  static PersistentVolumeClaimNameMatcher persistentVolumeClaimWithName(String expectedName) {
    return new PersistentVolumeClaimNameMatcher(expectedName);
  }

  private DomainResource getDomain() {
    return (DomainResource) testSupport.getResources(KubernetesTestSupport.DOMAIN).get(0);
  }

  @Test
  void whenMatchingPersistentVolumeClaimExists_logPvcExists() {
    V1PersistentVolumeClaim existingPvc = createPvcModel(testSupport.getPacket());
    existingPvc.getMetadata().setNamespace(NS);
    testSupport.defineResources(existingPvc);

    runPersistentVolumeClaimHelper();

    assertThat(logRecords, containsFine(getPvcExistsLogMessage()));
  }

  public String getPvcExistsLogMessage() {
    return PVC_EXISTS;
  }

  static class PersistentVolumeClaimNameMatcher
          extends org.hamcrest.TypeSafeDiagnosingMatcher<V1PersistentVolumeClaim> {
    private final String expectedName;

    private PersistentVolumeClaimNameMatcher(String expectedName) {
      this.expectedName = expectedName;
    }

    private String getName(V1PersistentVolumeClaim item) {
      return item.getMetadata().getName();
    }

    public void describeTo(Description description) {
      description.appendText("Pvc with name ").appendValue(expectedName);
    }

    @Override
    protected boolean matchesSafely(V1PersistentVolumeClaim item, Description mismatchDescription) {
      if (expectedName.equals(getName(item))) {
        return true;
      }

      mismatchDescription.appendText("Pvc with name ").appendValue(getName(item));
      return false;
    }
  }
}

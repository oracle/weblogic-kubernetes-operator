// Copyright (c) 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helpers;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.LogRecord;

import com.meterware.simplestub.Memento;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.models.V1LabelSelector;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1OwnerReference;
import io.kubernetes.client.openapi.models.V1beta1PodDisruptionBudget;
import oracle.kubernetes.operator.KubernetesConstants;
import oracle.kubernetes.operator.LabelConstants;
import oracle.kubernetes.operator.calls.FailureStatusSourceException;
import oracle.kubernetes.operator.calls.unprocessable.UnrecoverableErrorBuilderImpl;
import oracle.kubernetes.operator.utils.WlsDomainConfigSupport;
import oracle.kubernetes.operator.wlsconfig.WlsDomainConfig;
import oracle.kubernetes.operator.work.Packet;
import oracle.kubernetes.operator.work.Step;
import oracle.kubernetes.operator.work.TerminalStep;
import oracle.kubernetes.utils.TestUtils;
import oracle.kubernetes.weblogic.domain.ClusterConfigurator;
import oracle.kubernetes.weblogic.domain.DomainConfigurator;
import oracle.kubernetes.weblogic.domain.DomainConfiguratorFactory;
import oracle.kubernetes.weblogic.domain.model.Domain;
import oracle.kubernetes.weblogic.domain.model.DomainSpec;
import org.hamcrest.Description;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.meterware.simplestub.Stub.createStrictStub;
import static oracle.kubernetes.operator.ProcessingConstants.CLUSTER_NAME;
import static oracle.kubernetes.operator.ProcessingConstants.DOMAIN_TOPOLOGY;
import static oracle.kubernetes.operator.helpers.DomainStatusMatcher.hasStatus;
import static oracle.kubernetes.operator.helpers.KubernetesTestSupport.PODDISRUPTIONBUDGET;
import static oracle.kubernetes.operator.logging.MessageKeys.CLUSTER_PDB_CREATED;
import static oracle.kubernetes.operator.logging.MessageKeys.CLUSTER_PDB_EXISTS;
import static oracle.kubernetes.utils.LogMatcher.containsFine;
import static oracle.kubernetes.utils.LogMatcher.containsInfo;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.junit.MatcherAssert.assertThat;

@SuppressWarnings("ConstantConditions")
public class PodDisruptionBudgetHelperTest {

  static final String DOMAIN_NAME = "domain1";
  static final String NS = "namespace";
  static final String UID = "uid1";
  static final String KUBERNETES_UID = "12345";
  final List<Memento> mementos = new ArrayList<>();
  final DomainPresenceInfo domainPresenceInfo = createPresenceInfo();
  private static final String TEST_CLUSTER = "cluster-1";
  private static final String TEST_SERVER = "server1";
  private static final int MIN_REPLICA_VALUE = 2;
  private static final String[] MESSAGE_KEYS = {
    CLUSTER_PDB_EXISTS,
    CLUSTER_PDB_CREATED,
  };
  private static final TerminalStep terminalStep = new TerminalStep();
  private final KubernetesTestSupport testSupport = new KubernetesTestSupport();
  private final RetryStrategyStub retryStrategy = createStrictStub(RetryStrategyStub.class);
  private final List<LogRecord> logRecords = new ArrayList<>();
  private TestUtils.ConsoleHandlerMemento consoleHandlerMemento;

  static String getTestCluster() {
    return TEST_CLUSTER;
  }

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
    configSupport.addWlsCluster(TEST_CLUSTER, TEST_SERVER);

    WlsDomainConfig domainConfig = configSupport.createDomainConfig();
    testSupport
        .addToPacket(CLUSTER_NAME, TEST_CLUSTER)
        .addToPacket(DOMAIN_TOPOLOGY, domainConfig)
        .addDomainPresenceInfo(domainPresenceInfo);
  }

  private DomainConfigurator configureDomain() {
    return configureDomain(domainPresenceInfo);
  }

  private DomainConfigurator configureDomain(DomainPresenceInfo domainPresenceInfo) {
    return DomainConfiguratorFactory.forDomain(domainPresenceInfo.getDomain());
  }

  @SuppressWarnings("SameParameterValue")
  private ClusterConfigurator configureCluster(String clusterName) {
    return configureDomain().configureCluster(clusterName);
  }

  @AfterEach
  public void tearDown() throws Exception {
    mementos.forEach(Memento::revert);
    testSupport.throwOnCompletionFailure();
  }

  private DomainPresenceInfo createPresenceInfo() {
    return new DomainPresenceInfo(
            new Domain()
                    .withApiVersion(KubernetesConstants.DOMAIN_GROUP + "/" + KubernetesConstants.DOMAIN_VERSION)
                    .withKind(KubernetesConstants.DOMAIN)
                    .withMetadata(new V1ObjectMeta().namespace(NS).name(DOMAIN_NAME).uid(KUBERNETES_UID))
                    .withSpec(createDomainSpec()));
  }

  private DomainSpec createDomainSpec() {
    return new DomainSpec().withDomainUid(UID);
  }

  @Test
  public void whenPdbCreated_createWithOwnerReference() {
    V1OwnerReference expectedReference = new V1OwnerReference()
        .apiVersion(KubernetesConstants.DOMAIN_GROUP + "/" + KubernetesConstants.DOMAIN_VERSION)
        .kind(KubernetesConstants.DOMAIN)
        .name(DOMAIN_NAME)
        .uid(KUBERNETES_UID)
        .controller(true);

    V1beta1PodDisruptionBudget model = createPDBModel(testSupport.getPacket());
    assertThat(model.getMetadata().getOwnerReferences(), contains(expectedReference));
  }

  @Test
  public void whenCreated_modelHasExpectedSelectors() {
    V1beta1PodDisruptionBudget model = createPDBModel(testSupport.getPacket());

    Map<String, String> labels = new HashMap<>();
    labels.put(LabelConstants.CREATEDBYOPERATOR_LABEL, "true");
    labels.put(LabelConstants.DOMAINUID_LABEL, UID);
    labels.put(LabelConstants.CLUSTERNAME_LABEL, getTestCluster());
    assertThat(
        model.getSpec().getSelector(), is(new V1LabelSelector().matchLabels(labels)));
  }

  @Test
  public void whenCreated_modelMetadataHasExpectedLabels() {
    V1beta1PodDisruptionBudget model = createPDBModel(testSupport.getPacket());

    assertThat(
            model.getMetadata().getLabels(), allOf(hasEntry(LabelConstants.CREATEDBYOPERATOR_LABEL, "true"),
                    hasEntry(LabelConstants.DOMAINUID_LABEL, UID),
                    hasEntry(LabelConstants.CLUSTERNAME_LABEL, getTestCluster())));
  }

  @Test
  public void whenCreated_modelHasExpectedMinAvailableSpec() {
    configureCluster(getTestCluster()).withReplicas(3).withMaxUnavailable(1);

    V1beta1PodDisruptionBudget model = createPDBModel(testSupport.getPacket());

    assertThat(model.getSpec().getMinAvailable().getIntValue(), equalTo(MIN_REPLICA_VALUE));
  }

  @Test
  public void onRunWithNoPodDisruptionBudget_logCreatedMessage() {
    runPodDisruptionBudgetHelper();

    assertThat(logRecords, containsInfo(getPdbCreateLogMessage()));
  }

  @Test
  public void onRunWithNoPodDisruptionBudget_createIt() {
    consoleHandlerMemento.ignoreMessage(getPdbCreateLogMessage());

    runPodDisruptionBudgetHelper();

    assertThat(
            getRecordedPodDisruptionBudget(domainPresenceInfo),
            is(podDisruptionBudgetWithName(getPdbName())));
  }

  @Test
  public void onFailedRun_reportFailure() {
    testSupport.addRetryStrategy(retryStrategy);
    testSupport.failOnResource(PODDISRUPTIONBUDGET, getPdbName(), NS, 500);

    runPodDisruptionBudgetHelper();

    testSupport.verifyCompletionThrowable(FailureStatusSourceException.class);
  }

  @Test
  public void whenPodDisruptionBudgetCreationFailsDueToUnprocessableEntityFailure_reportInDomainStatus() {
    testSupport.defineResources(domainPresenceInfo.getDomain());
    testSupport.failOnResource(PODDISRUPTIONBUDGET, getPdbName(), NS, new UnrecoverableErrorBuilderImpl()
            .withReason("FieldValueNotFound")
            .withMessage("Test this failure")
            .build());

    runPodDisruptionBudgetHelper();

    assertThat(getDomain(), hasStatus("FieldValueNotFound",
            "testcall in namespace junit, for testName: Test this failure"));
  }

  @Test
  public void whenPodDisruptionBudgetCreationFailsDueToUnprocessableEntityFailure_abortFiber() {
    testSupport.defineResources(domainPresenceInfo.getDomain());
    testSupport.failOnResource(PODDISRUPTIONBUDGET, getPdbName(), NS, new UnrecoverableErrorBuilderImpl()
            .withReason("FieldValueNotFound")
            .withMessage("Test this failure")
            .build());

    runPodDisruptionBudgetHelper();

    assertThat(terminalStep.wasRun(), is(false));
  }

  public V1beta1PodDisruptionBudget createPDBModel(Packet packet) {
    return new PodDisruptionBudgetHelper.PodDisruptionBudgetContext(null, packet)
            .createModel();
  }

  public String getPdbCreateLogMessage() {
    return CLUSTER_PDB_CREATED;
  }

  private void runPodDisruptionBudgetHelper() {
    testSupport.runSteps(createSteps(null));
  }

  public Step createSteps(Step next) {
    return PodDisruptionBudgetHelper.createPodDisruptionBudgetForClusterStep(next);
  }

  public V1beta1PodDisruptionBudget getRecordedPodDisruptionBudget(DomainPresenceInfo info) {
    return info.getPodDisruptionBudget(getTestCluster());
  }

  static PodDisruptionBudgetHelperTest.PodDisruptionBudgetNameMatcher podDisruptionBudgetWithName(String expectedName) {
    return new PodDisruptionBudgetHelperTest.PodDisruptionBudgetNameMatcher(expectedName);
  }

  public String getPdbName() {
    return UID + "-" + getTestCluster();
  }

  private Domain getDomain() {
    return (Domain) testSupport.getResources(KubernetesTestSupport.DOMAIN).get(0);
  }

  @Test
  public void whenMatchingPodDisruptionBudgetRecordedInDomainPresence_logPdbExists() {
    V1beta1PodDisruptionBudget originalPdb = createPDBModel(testSupport.getPacket());
    recordPodDisruptionBudget(domainPresenceInfo, originalPdb);

    runPodDisruptionBudgetHelper();

    assertThat(logRecords, containsFine(getPdbExistsLogMessage()));
  }

  public String getPdbExistsLogMessage() {
    return CLUSTER_PDB_EXISTS;
  }

  public void recordPodDisruptionBudget(DomainPresenceInfo info, V1beta1PodDisruptionBudget pdb) {
    info.setPodDisruptionBudget(getTestCluster(), pdb);
  }

  static class PodDisruptionBudgetNameMatcher
          extends org.hamcrest.TypeSafeDiagnosingMatcher<V1beta1PodDisruptionBudget> {
    private final String expectedName;

    private PodDisruptionBudgetNameMatcher(String expectedName) {
      this.expectedName = expectedName;
    }

    private String getName(V1beta1PodDisruptionBudget item) {
      return item.getMetadata().getName();
    }

    public void describeTo(Description description) {
      description.appendText("pdb with name ").appendValue(expectedName);
    }

    @Override
    protected boolean matchesSafely(V1beta1PodDisruptionBudget item, Description mismatchDescription) {
      if (expectedName.equals(getName(item))) {
        return true;
      }

      mismatchDescription.appendText("pdb with name ").appendValue(getName(item));
      return false;
    }
  }
}
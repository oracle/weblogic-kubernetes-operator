// Copyright (c) 2018, 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helpers;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.stream.IntStream;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.meterware.simplestub.Memento;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.models.V1ConfigMap;
import io.kubernetes.client.openapi.models.V1Container;
import io.kubernetes.client.openapi.models.V1Job;
import io.kubernetes.client.openapi.models.V1JobStatus;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1SecretReference;
import oracle.kubernetes.operator.JobAwaiterStepFactory;
import oracle.kubernetes.operator.TuningParameters;
import oracle.kubernetes.operator.calls.unprocessable.UnrecoverableErrorBuilderImpl;
import oracle.kubernetes.operator.rest.ScanCacheStub;
import oracle.kubernetes.operator.wlsconfig.WlsClusterConfig;
import oracle.kubernetes.operator.wlsconfig.WlsDomainConfig;
import oracle.kubernetes.operator.wlsconfig.WlsServerConfig;
import oracle.kubernetes.operator.work.FiberTestSupport;
import oracle.kubernetes.operator.work.TerminalStep;
import oracle.kubernetes.utils.TestUtils;
import oracle.kubernetes.weblogic.domain.model.Cluster;
import oracle.kubernetes.weblogic.domain.model.Configuration;
import oracle.kubernetes.weblogic.domain.model.ConfigurationConstants;
import oracle.kubernetes.weblogic.domain.model.Domain;
import oracle.kubernetes.weblogic.domain.model.DomainSpec;
import oracle.kubernetes.weblogic.domain.model.Model;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.meterware.simplestub.Stub.createNiceStub;
import static com.meterware.simplestub.Stub.createStrictStub;
import static oracle.kubernetes.operator.DomainProcessorTestSetup.NS;
import static oracle.kubernetes.operator.DomainProcessorTestSetup.UID;
import static oracle.kubernetes.operator.ProcessingConstants.DOMAIN_INTROSPECTOR_JOB;
import static oracle.kubernetes.operator.ProcessingConstants.DOMAIN_TOPOLOGY;
import static oracle.kubernetes.operator.ProcessingConstants.JOBWATCHER_COMPONENT_NAME;
import static oracle.kubernetes.operator.ProcessingConstants.JOB_POD_NAME;
import static oracle.kubernetes.operator.helpers.DomainStatusMatcher.hasStatus;
import static oracle.kubernetes.operator.helpers.KubernetesTestSupport.DOMAIN;
import static oracle.kubernetes.operator.helpers.KubernetesTestSupport.JOB;
import static oracle.kubernetes.operator.helpers.Matchers.hasEnvVar;
import static oracle.kubernetes.operator.logging.MessageKeys.INTROSPECTOR_JOB_FAILED;
import static oracle.kubernetes.operator.logging.MessageKeys.INTROSPECTOR_JOB_FAILED_DETAIL;
import static oracle.kubernetes.operator.logging.MessageKeys.JOB_CREATED;
import static oracle.kubernetes.operator.logging.MessageKeys.JOB_DELETED;
import static oracle.kubernetes.operator.logging.MessageKeys.NO_CLUSTER_IN_DOMAIN;
import static oracle.kubernetes.utils.LogMatcher.containsFine;
import static oracle.kubernetes.utils.LogMatcher.containsInfo;
import static oracle.kubernetes.utils.LogMatcher.containsWarning;
import static oracle.kubernetes.weblogic.domain.model.ConfigurationConstants.START_NEVER;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.stringContainsInOrder;
import static org.hamcrest.core.AllOf.allOf;
import static org.hamcrest.junit.MatcherAssert.assertThat;

@SuppressWarnings({"SameParameterValue"})
public class DomainIntrospectorJobTest {
  private static final String NODEMGR_HOME = "/u01/nodemanager";
  private static final String OVERRIDES_CM = "overrides-config-map";
  private static final String OVERRIDE_SECRET_1 = "override-secret-1";
  private static final String OVERRIDE_SECRET_2 = "override-secret-2";
  private static final String LOG_HOME = "/shared/logs/" + UID;
  private static final String CREDENTIALS_SECRET_NAME = "webLogicCredentialsSecretName";
  private static final String WDT_MODEL_HOME = "/u01/wdt/my-models";
  private static final String LATEST_IMAGE = "image:latest";
  private static final String ADMIN_NAME = "admin";
  private static final int MAX_SERVERS = 2;
  private static final String MS_PREFIX = "managed-server";
  private static final String[] MANAGED_SERVER_NAMES =
      IntStream.rangeClosed(1, MAX_SERVERS).mapToObj(n -> MS_PREFIX + n).toArray(String[]::new);
  private static final String SEVERE_PROBLEM_1 = "really bad";
  private static final String SEVERE_MESSAGE_1 = "@[SEVERE] " + SEVERE_PROBLEM_1;

  private final TerminalStep terminalStep = new TerminalStep();
  private final Domain domain = createDomain();
  private final DomainPresenceInfo domainPresenceInfo = createDomainPresenceInfo(domain);
  private final KubernetesTestSupport testSupport = new KubernetesTestSupport();
  private final List<Memento> mementos = new ArrayList<>();
  private final List<LogRecord> logRecords = new ArrayList<>();
  private final RetryStrategyStub retryStrategy = createStrictStub(RetryStrategyStub.class);
  private final String jobPodName = LegalNames.toJobIntrospectorName(UID);

  public DomainIntrospectorJobTest() {
  }

  private static String getJobName() {
    return LegalNames.toJobIntrospectorName(UID);
  }

  @BeforeEach
  public void setUp() throws Exception {
    mementos.add(
        TestUtils.silenceOperatorLogger()
            .collectLogMessages(logRecords, getMessageKeys())
            .withLogLevel(Level.FINE)
            .ignoringLoggedExceptions(ApiException.class));
    mementos.add(TuningParametersStub.install());
    mementos.add(testSupport.install());
    mementos.add(ScanCacheStub.install());
    testSupport.addToPacket(JOB_POD_NAME, jobPodName);
    testSupport.addDomainPresenceInfo(domainPresenceInfo);
    testSupport.defineResources(domain);
    testSupport.addComponent(JOBWATCHER_COMPONENT_NAME,
          JobAwaiterStepFactory.class,
          createNiceStub(JobAwaiterStepFactory.class));
  }

  private String[] getMessageKeys() {
    return new String[] {
        getJobCreatedMessageKey(),
        getJobDeletedMessageKey(),
        getNoClusterInDomainMessageKey(),
        getJobFailedMessageKey(),
        getJobFailedDetailMessageKey()
    };
  }

  @AfterEach
  public void tearDown() {
    for (Memento memento : mementos) {
      memento.revert();
    }
  }

  private Domain createDomain() {
    return new Domain()
        .withMetadata(new V1ObjectMeta().name(UID).namespace(NS))
        .withSpec(createDomainSpec());
  }

  private DomainPresenceInfo createDomainPresenceInfo(Domain domain) {
    return new DomainPresenceInfo(domain);
  }

  private DomainSpec createDomainSpec() {
    Cluster cluster = new Cluster();
    cluster.setClusterName("cluster-1");
    cluster.setReplicas(1);
    cluster.setServerStartPolicy(ConfigurationConstants.START_IF_NEEDED);
    DomainSpec spec =
        new DomainSpec()
            .withDomainUid(UID)
            .withWebLogicCredentialsSecret(new V1SecretReference().name(CREDENTIALS_SECRET_NAME))
            .withConfigOverrides(OVERRIDES_CM)
            .withCluster(cluster)
            .withImage(LATEST_IMAGE)
            .withDomainHomeInImage(false);
    spec.setServerStartPolicy(ConfigurationConstants.START_IF_NEEDED);

    List<String> overrideSecrets = new ArrayList<>();
    overrideSecrets.add(OVERRIDE_SECRET_1);
    overrideSecrets.add(OVERRIDE_SECRET_2);
    spec.setConfigOverrideSecrets(overrideSecrets);

    return spec;
  }

  private String getJobCreatedMessageKey() {
    return JOB_CREATED;
  }

  private String getJobDeletedMessageKey() {
    return JOB_DELETED;
  }

  private String getJobFailedMessageKey() {
    return INTROSPECTOR_JOB_FAILED;
  }

  private String getJobFailedDetailMessageKey() {
    return INTROSPECTOR_JOB_FAILED_DETAIL;
  }

  private String getNoClusterInDomainMessageKey() {
    return NO_CLUSTER_IN_DOMAIN;
  }

  @Test
  public void whenNoJob_createIt() throws JsonProcessingException {
    IntrospectionTestUtils.defineResources(testSupport, createDomainConfig("cluster-1"));
    testSupport.defineResources(
        new V1ConfigMap()
            .metadata(
                new V1ObjectMeta()
                    .namespace(NS)
                    .name(ConfigMapHelper.getIntrospectorConfigMapName(UID))));

    testSupport.runSteps(getStepFactory(), terminalStep);

    assertThat(logRecords, containsInfo(getJobCreatedMessageKey()));
    assertThat(logRecords, containsFine(getJobDeletedMessageKey()));
  }

  private static WlsDomainConfig createDomainConfig(String clusterName) {
    WlsClusterConfig clusterConfig = new WlsClusterConfig(clusterName);
    for (String serverName : MANAGED_SERVER_NAMES) {
      clusterConfig.addServerConfig(new WlsServerConfig(serverName, "domain1-" + serverName, 8001));
    }
    return new WlsDomainConfig("base_domain")
        .withAdminServer(ADMIN_NAME, "domain1-admin-server", 7001)
        .withCluster(clusterConfig);
  }

  private FiberTestSupport.StepFactory getStepFactory() {
    return JobHelper::createDomainIntrospectorJobStep;
  }

  @Test
  public void whenNoJob_onFiveHundred() {
    testSupport.addRetryStrategy(retryStrategy);
    testSupport.failOnResource(KubernetesTestSupport.JOB, getJobName(), NS, 500);

    testSupport.runSteps(getStepFactory(), terminalStep);

    assertThat(getDomain(), hasStatus("ServerError",
            "testcall in namespace junit, for testName: failure reported in test"));
  }

  @Test
  public void whenJobCreated_jobNameContainsDefaultSuffix() {
    testSupport.runSteps(getStepFactory(), terminalStep);
    logRecords.clear();

    assertThat(getCreatedJobName(), stringContainsInOrder(UID,"-introspector"));
  }

  @SuppressWarnings("ConstantConditions")
  @Nullable
  private String getCreatedJobName() {
    List<V1Job> jobs = testSupport.getResources(KubernetesTestSupport.JOB);
    return jobs.get(0).getMetadata().getName();
  }

  @Test
  public void whenJobCreatedWithCustomIntrospectorJobnameSuffix_jobNameContainsConfiguredSuffix() {
    TuningParameters.getInstance().put(LegalNames.INTROSPECTOR_JOB_NAME_SUFFIX_PARAM, "-introspector-job");
    testSupport.runSteps(getStepFactory(), terminalStep);
    logRecords.clear();

    assertThat(getCreatedJobName(), stringContainsInOrder(UID,"-introspector-job"));
  }

  @Test
  public void whenJobCreated_specHasOneContainer() {
    testSupport.runSteps(getStepFactory(), terminalStep);
    logRecords.clear();

    List<V1Job> jobs = testSupport.getResources(KubernetesTestSupport.JOB);
    assertThat(getPodTemplateContainers(jobs.get(0)), hasSize(1));
  }

  @SuppressWarnings("ConstantConditions")
  private List<V1Container> getPodTemplateContainers(V1Job v1Job) {
    return v1Job.getSpec().getTemplate().getSpec().getContainers();
  }

  @Test
  public void whenJobCreated_hasPredefinedEnvVariables() {
    testSupport.runSteps(getStepFactory(), terminalStep);
    logRecords.clear();

    List<V1Job> jobs = testSupport.getResources(KubernetesTestSupport.JOB);
    List<V1Container> podTemplateContainers = getPodTemplateContainers(jobs.get(0));
    assertThat(
        podTemplateContainers.get(0).getEnv(),
        allOf(
            hasEnvVar("NAMESPACE", NS),
            hasEnvVar("DOMAIN_UID", UID),
            hasEnvVar("DOMAIN_HOME", getDomainHome()),
            hasEnvVar("NODEMGR_HOME", NODEMGR_HOME),
            hasEnvVar("LOG_HOME", LOG_HOME),
            hasEnvVar("INTROSPECT_HOME", getDomainHome()),
            hasEnvVar("SERVER_OUT_IN_POD_LOG", "true"),
            hasEnvVar("CREDENTIALS_SECRET_NAME", CREDENTIALS_SECRET_NAME)));
  }

  @Test
  public void whenJobCreatedWithModelHomeDefined_hasModelHomeEnvVariable() {
    getDomain().getSpec()
        .setConfiguration(new Configuration().withModel(new Model().withModelHome(WDT_MODEL_HOME)));
    testSupport.runSteps(getStepFactory(), terminalStep);
    logRecords.clear();

    List<V1Job> jobs = testSupport.getResources(KubernetesTestSupport.JOB);
    List<V1Container> podTemplateContainers = getPodTemplateContainers(jobs.get(0));
    assertThat(
        podTemplateContainers.get(0).getEnv(),
        hasEnvVar("WDT_MODEL_HOME", WDT_MODEL_HOME));
  }

  @Test
  public void whenPodCreationFailsDueToUnprocessableEntityFailure_reportInDomainStatus() {
    testSupport.failOnResource(JOB, getJobName(), NS, new UnrecoverableErrorBuilderImpl()
        .withReason("FieldValueNotFound")
        .withMessage("Test this failure")
        .build());

    testSupport.runSteps(getStepFactory(), terminalStep);

    assertThat(getDomain(), hasStatus("FieldValueNotFound",
        "testcall in namespace junit, for testName: Test this failure"));
  }

  Domain getDomain() {
    return testSupport.getResourceWithName(DOMAIN, UID);
  }

  @Test
  public void whenPodCreationFailsDueToUnprocessableEntityFailure_abortFiber() {
    testSupport.failOnResource(JOB, getJobName(), NS, new UnrecoverableErrorBuilderImpl()
        .withReason("FieldValueNotFound")
        .withMessage("Test this failure")
        .build());

    testSupport.runSteps(getStepFactory(), terminalStep);

    assertThat(terminalStep.wasRun(), is(false));
  }

  @Test
  public void whenIntrospectorJobIsRun_validatesDomainTopology() throws JsonProcessingException {
    // create WlsDomainConfig with "cluster-2" whereas domain spec contains cluster-1
    IntrospectionTestUtils.defineResources(testSupport, createDomainConfig("cluster-2"));

    testSupport.runSteps(getStepFactory(), terminalStep);

    assertThat(logRecords, containsInfo(getJobCreatedMessageKey()));
    assertThat(logRecords, containsFine(getJobDeletedMessageKey()));
    assertThat(logRecords, containsWarning(getNoClusterInDomainMessageKey()));
  }

  @Test
  public void whenIntrospectorJobNotNeeded_doesNotValidatesDomainTopology() throws JsonProcessingException {
    // create WlsDomainConfig with "cluster-2" whereas domain spec contains "cluster-1"
    WlsDomainConfig wlsDomainConfig = createDomainConfig("cluster-2");
    IntrospectionTestUtils.defineResources(testSupport, wlsDomainConfig);

    // make JobHelper.runIntrospector() return false
    getCluster("cluster-1").setServerStartPolicy(START_NEVER);
    domain.getSpec().setServerStartPolicy(START_NEVER);
    testSupport.addToPacket(DOMAIN_TOPOLOGY, wlsDomainConfig);

    testSupport.runSteps(getStepFactory(), terminalStep);

    assertThat(logRecords, empty());
  }

  @Test
  public void whenJobLogContainsSevereError_logJobInfosOnDelete() {
    testSupport.defineResources(
        new V1Job().metadata(new V1ObjectMeta().name(getJobName()).namespace(NS)).status(new V1JobStatus()));
    IntrospectionTestUtils.defineResources(testSupport, SEVERE_MESSAGE_1);
    testSupport.addToPacket(DOMAIN_INTROSPECTOR_JOB, testSupport.getResourceWithName(JOB, getJobName()));

    testSupport.runSteps(JobHelper.deleteDomainIntrospectorJobStep(terminalStep));

    assertThat(logRecords, containsInfo(getJobFailedMessageKey()));
    assertThat(logRecords, containsFine(getJobFailedDetailMessageKey()));
    assertThat(logRecords, containsFine(getJobDeletedMessageKey()));
  }

  @Test
  public void whenJobLogContainsSevereError_logJobInfosOnReadPogLog() {
    testSupport.defineResources(
        new V1Job().metadata(new V1ObjectMeta().name(getJobName()).namespace(NS)).status(new V1JobStatus()));
    IntrospectionTestUtils.defineResources(testSupport, SEVERE_MESSAGE_1);
    testSupport.addToPacket(DOMAIN_INTROSPECTOR_JOB, testSupport.getResourceWithName(JOB, getJobName()));

    testSupport.runSteps(JobHelper.readDomainIntrospectorPodLog(terminalStep));

    assertThat(logRecords, containsInfo(getJobFailedMessageKey()));
    assertThat(logRecords, containsFine(getJobFailedDetailMessageKey()));
  }

  private Cluster getCluster(String clusterName) {
    return domain.getSpec().getClusters().stream()
          .filter(c -> clusterName.equals(c.getClusterName()))
          .findFirst().orElse(new Cluster());
  }

  private String getDomainHome() {
    return "/shared/domains/" + UID;
  }
}

// Copyright (c) 2018, 2023, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helpers;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.stream.IntStream;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.meterware.simplestub.Memento;
import io.kubernetes.client.custom.Quantity;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.models.V1ConfigMap;
import io.kubernetes.client.openapi.models.V1ConfigMapVolumeSource;
import io.kubernetes.client.openapi.models.V1Container;
import io.kubernetes.client.openapi.models.V1ContainerState;
import io.kubernetes.client.openapi.models.V1ContainerStateWaiting;
import io.kubernetes.client.openapi.models.V1ContainerStatus;
import io.kubernetes.client.openapi.models.V1EmptyDirVolumeSource;
import io.kubernetes.client.openapi.models.V1Job;
import io.kubernetes.client.openapi.models.V1JobCondition;
import io.kubernetes.client.openapi.models.V1JobSpec;
import io.kubernetes.client.openapi.models.V1JobStatus;
import io.kubernetes.client.openapi.models.V1LocalObjectReference;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1Pod;
import io.kubernetes.client.openapi.models.V1PodSpec;
import io.kubernetes.client.openapi.models.V1PodStatus;
import io.kubernetes.client.openapi.models.V1PodTemplateSpec;
import io.kubernetes.client.openapi.models.V1ResourceRequirements;
import io.kubernetes.client.openapi.models.V1Secret;
import io.kubernetes.client.openapi.models.V1SecretVolumeSource;
import io.kubernetes.client.openapi.models.V1Volume;
import io.kubernetes.client.openapi.models.V1VolumeMount;
import oracle.kubernetes.common.utils.SchemaConversionUtils;
import oracle.kubernetes.operator.DomainSourceType;
import oracle.kubernetes.operator.FluentdUtils;
import oracle.kubernetes.operator.JobAwaiterStepFactory;
import oracle.kubernetes.operator.JobWatcher;
import oracle.kubernetes.operator.LabelConstants;
import oracle.kubernetes.operator.ServerStartPolicy;
import oracle.kubernetes.operator.calls.unprocessable.UnrecoverableErrorBuilderImpl;
import oracle.kubernetes.operator.http.rest.ScanCacheStub;
import oracle.kubernetes.operator.introspection.IntrospectionTestUtils;
import oracle.kubernetes.operator.logging.LoggingFacade;
import oracle.kubernetes.operator.logging.LoggingFactory;
import oracle.kubernetes.operator.tuning.TuningParametersStub;
import oracle.kubernetes.operator.wlsconfig.WlsClusterConfig;
import oracle.kubernetes.operator.wlsconfig.WlsDomainConfig;
import oracle.kubernetes.operator.wlsconfig.WlsServerConfig;
import oracle.kubernetes.operator.work.Packet;
import oracle.kubernetes.operator.work.Step;
import oracle.kubernetes.operator.work.TerminalStep;
import oracle.kubernetes.utils.SystemClock;
import oracle.kubernetes.utils.SystemClockTestSupport;
import oracle.kubernetes.utils.TestUtils;
import oracle.kubernetes.weblogic.domain.DomainConfigurator;
import oracle.kubernetes.weblogic.domain.DomainConfiguratorFactory;
import oracle.kubernetes.weblogic.domain.model.AuxiliaryImage;
import oracle.kubernetes.weblogic.domain.model.ClusterResource;
import oracle.kubernetes.weblogic.domain.model.ClusterSpec;
import oracle.kubernetes.weblogic.domain.model.Configuration;
import oracle.kubernetes.weblogic.domain.model.DomainCondition;
import oracle.kubernetes.weblogic.domain.model.DomainCreationImage;
import oracle.kubernetes.weblogic.domain.model.DomainOnPV;
import oracle.kubernetes.weblogic.domain.model.DomainResource;
import oracle.kubernetes.weblogic.domain.model.DomainSpec;
import oracle.kubernetes.weblogic.domain.model.DomainTestUtils;
import oracle.kubernetes.weblogic.domain.model.InitializeDomainOnPV;
import oracle.kubernetes.weblogic.domain.model.Model;
import oracle.kubernetes.weblogic.domain.model.Opss;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import static com.meterware.simplestub.Stub.createStrictStub;
import static java.net.HttpURLConnection.HTTP_CONFLICT;
import static oracle.kubernetes.common.AuxiliaryImageConstants.AUXILIARY_IMAGE_DEFAULT_INIT_CONTAINER_COMMAND;
import static oracle.kubernetes.common.AuxiliaryImageConstants.AUXILIARY_IMAGE_INIT_CONTAINER_NAME_PREFIX;
import static oracle.kubernetes.common.logging.MessageKeys.INTROSPECTOR_FLUENTD_CONTAINER_TERMINATED;
import static oracle.kubernetes.common.logging.MessageKeys.INTROSPECTOR_JOB_FAILED;
import static oracle.kubernetes.common.logging.MessageKeys.INTROSPECTOR_JOB_FAILED_DETAIL;
import static oracle.kubernetes.common.logging.MessageKeys.JOB_CREATED;
import static oracle.kubernetes.common.logging.MessageKeys.JOB_DELETED;
import static oracle.kubernetes.common.logging.MessageKeys.KUBERNETES_EVENT_ERROR;
import static oracle.kubernetes.common.logging.MessageKeys.NO_CLUSTER_IN_DOMAIN;
import static oracle.kubernetes.common.utils.LogMatcher.containsFine;
import static oracle.kubernetes.common.utils.LogMatcher.containsInfo;
import static oracle.kubernetes.common.utils.LogMatcher.containsWarning;
import static oracle.kubernetes.operator.DomainProcessorTestSetup.NS;
import static oracle.kubernetes.operator.DomainProcessorTestSetup.UID;
import static oracle.kubernetes.operator.DomainStatusMatcher.hasStatus;
import static oracle.kubernetes.operator.EventTestUtils.getExpectedEventMessage;
import static oracle.kubernetes.operator.EventTestUtils.getLocalizedString;
import static oracle.kubernetes.operator.KubernetesConstants.DOMAIN;
import static oracle.kubernetes.operator.KubernetesConstants.HTTP_FORBIDDEN;
import static oracle.kubernetes.operator.KubernetesConstants.HTTP_INTERNAL_ERROR;
import static oracle.kubernetes.operator.ProcessingConstants.DOMAIN_INTROSPECTION_COMPLETE;
import static oracle.kubernetes.operator.ProcessingConstants.DOMAIN_INTROSPECTOR_JOB;
import static oracle.kubernetes.operator.ProcessingConstants.DOMAIN_TOPOLOGY;
import static oracle.kubernetes.operator.ProcessingConstants.JOBWATCHER_COMPONENT_NAME;
import static oracle.kubernetes.operator.ProcessingConstants.JOB_POD;
import static oracle.kubernetes.operator.ProcessingConstants.JOB_POD_INTROSPECT_CONTAINER_TERMINATED;
import static oracle.kubernetes.operator.ProcessingConstants.JOB_POD_INTROSPECT_CONTAINER_TERMINATED_MARKER;
import static oracle.kubernetes.operator.helpers.EventHelper.EventItem.DOMAIN_FAILED;
import static oracle.kubernetes.operator.helpers.KubernetesTestSupport.JOB;
import static oracle.kubernetes.operator.helpers.Matchers.hasEnvVar;
import static oracle.kubernetes.operator.helpers.Matchers.hasLegacyAuxiliaryImageInitContainer;
import static oracle.kubernetes.operator.helpers.PodHelperTestBase.CUSTOM_COMMAND_SCRIPT;
import static oracle.kubernetes.operator.helpers.PodHelperTestBase.CUSTOM_MODEL_SOURCE_HOME;
import static oracle.kubernetes.operator.helpers.PodHelperTestBase.CUSTOM_MOUNT_PATH;
import static oracle.kubernetes.operator.helpers.PodHelperTestBase.CUSTOM_WDT_INSTALL_SOURCE_HOME;
import static oracle.kubernetes.operator.helpers.PodHelperTestBase.createAuxiliaryImage;
import static oracle.kubernetes.operator.helpers.PodHelperTestBase.createAuxiliaryImageVolume;
import static oracle.kubernetes.operator.helpers.PodHelperTestBase.createLegacyDomainMap;
import static oracle.kubernetes.operator.helpers.PodHelperTestBase.createResources;
import static oracle.kubernetes.operator.helpers.PodHelperTestBase.getLegacyAuxiliaryImageVolumeName;
import static oracle.kubernetes.operator.helpers.StepContextConstants.FLUENTD_CONTAINER_NAME;
import static oracle.kubernetes.operator.helpers.StepContextConstants.OPSS_KEYPASSPHRASE_VOLUME;
import static oracle.kubernetes.operator.helpers.StepContextConstants.OPSS_KEY_MOUNT_PATH;
import static oracle.kubernetes.operator.helpers.StepContextConstants.OPSS_WALLETFILE_MOUNT_PATH;
import static oracle.kubernetes.operator.helpers.StepContextConstants.OPSS_WALLETFILE_VOLUME;
import static oracle.kubernetes.operator.helpers.StepContextConstants.SECRETS_VOLUME;
import static oracle.kubernetes.operator.helpers.StepContextConstants.WDTCONFIGMAP_MOUNT_PATH;
import static oracle.kubernetes.operator.helpers.StepContextConstants.WDT_MODEL_ENCRYPTION_PASSPHRASE_MOUNT_PATH;
import static oracle.kubernetes.operator.helpers.StepContextConstants.WDT_MODEL_ENCRYPTION_PASSPHRASE_VOLUME;
import static oracle.kubernetes.operator.tuning.TuningParameters.DOMAIN_PRESENCE_RECHECK_INTERVAL_SECONDS;
import static oracle.kubernetes.weblogic.domain.model.AuxiliaryImage.AUXILIARY_IMAGE_DEFAULT_SOURCE_WDT_INSTALL_HOME;
import static oracle.kubernetes.weblogic.domain.model.AuxiliaryImage.AUXILIARY_IMAGE_INTERNAL_VOLUME_NAME;
import static oracle.kubernetes.weblogic.domain.model.DomainConditionMatcher.hasCondition;
import static oracle.kubernetes.weblogic.domain.model.DomainConditionType.FAILED;
import static oracle.kubernetes.weblogic.domain.model.DomainCreationImage.DOMAIN_CREATION_IMAGE_MOUNT_PATH;
import static oracle.kubernetes.weblogic.domain.model.DomainFailureReason.ABORTED;
import static oracle.kubernetes.weblogic.domain.model.DomainFailureReason.INTROSPECTION;
import static oracle.kubernetes.weblogic.domain.model.DomainFailureReason.KUBERNETES;
import static oracle.kubernetes.weblogic.domain.model.Model.DEFAULT_AUXILIARY_IMAGE_MOUNT_PATH;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.stringContainsInOrder;
import static org.hamcrest.core.AllOf.allOf;
import static org.hamcrest.junit.MatcherAssert.assertThat;

@SuppressWarnings({"SameParameterValue"})
class DomainIntrospectorJobTest extends DomainTestUtils {

  private static final int MAX_RETRY_COUNT = 2;
  private static final LoggingFacade LOGGER = LoggingFactory.getLogger("Operator", "Operator");

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
  private static final String SEVERE_PROBLEM = "really bad";
  private static final String SEVERE_MESSAGE = "@[SEVERE] " + SEVERE_PROBLEM;
  private static final String FATAL_PROBLEM = "FatalIntrospectorError: really bad";
  private static final String FATAL_MESSAGE = "@[SEVERE] " + FATAL_PROBLEM;
  private static final String INFO_MESSAGE = "@[INFO] just letting you know. " + DOMAIN_INTROSPECTION_COMPLETE;
  private static final String JOB_UID = "FAILED_JOB";
  private static final String JOB_NAME = UID + "-introspector";
  public static final String TEST_VOLUME_NAME = "test";
  private static final String DEFAULT_LEGACY_AUXILIARY_IMAGE_MOUNT_PATH = "/auxiliary";

  private final TerminalStep terminalStep = new TerminalStep();
  private final DomainResource domain = createDomain();
  private final ClusterResource cluster = createCluster();
  private final DomainPresenceInfo domainPresenceInfo = createDomainPresenceInfo(domain, cluster);
  private final KubernetesTestSupport testSupport = new KubernetesTestSupport();
  private final List<Memento> mementos = new ArrayList<>();
  private final List<LogRecord> logRecords = new ArrayList<>();
  private final DomainConfigurator configurator = DomainConfiguratorFactory.forDomain(domain);
  private final RetryStrategyStub retryStrategy = createStrictStub(RetryStrategyStub.class);
  private final String jobPodName = LegalNames.toJobIntrospectorName(UID);
  private TestUtils.ConsoleHandlerMemento consoleHandlerMemento;
  private final SchemaConversionUtils schemaConversionUtils = new SchemaConversionUtils();

  private boolean jobDeleted;

  public DomainIntrospectorJobTest() {
  }

  private static String getJobName() {
    return LegalNames.toJobIntrospectorName(UID);
  }

  @BeforeEach
  public void setUp() throws Exception {
    mementos.add(TuningParametersStub.install());
    mementos.add(testSupport.install());
    mementos.add(ScanCacheStub.install());
    mementos.add(SystemClockTestSupport.installClock());
    mementos.add(UnitTestHash.install());
    mementos.add(
        consoleHandlerMemento = TestUtils.silenceOperatorLogger()
            .collectLogMessages(logRecords, getMessageKeys())
            .withLogLevel(Level.FINE)
            .ignoringLoggedExceptions(ApiException.class));

    testSupport.addToPacket(JOB_POD, getIntrospectorJobPod());
    testSupport.addDomainPresenceInfo(domainPresenceInfo);
    testSupport.defineResources(domain);
    testSupport.defineResources(cluster);
    testSupport.addComponent(JOBWATCHER_COMPONENT_NAME, JobAwaiterStepFactory.class, new JobAwaiterStepFactoryStub());

    TuningParametersStub.setParameter(DOMAIN_PRESENCE_RECHECK_INTERVAL_SECONDS, "2");
  }

  private V1Pod getIntrospectorJobPod() {
    return new V1Pod().metadata(new V1ObjectMeta().name(jobPodName));
  }

  private static class JobAwaiterStepFactoryStub implements JobAwaiterStepFactory {
    @Override
    public Step waitForReady(V1Job job, Step next) {
      return next;
    }
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

  private DomainResource createDomain() {
    return new DomainResource()
        .withMetadata(new V1ObjectMeta().name(UID).namespace(NS))
        .withSpec(createDomainSpec());
  }

  private ClusterResource createCluster() {
    return new ClusterResource()
        .withMetadata(new V1ObjectMeta().name("cluster-1").namespace(NS))
        .spec(createClusterSpec());
  }

  private DomainPresenceInfo createDomainPresenceInfo(DomainResource domain, ClusterResource cluster) {
    DomainPresenceInfo dpi = new DomainPresenceInfo(domain);
    dpi.addClusterResource(cluster);
    return dpi;
  }

  private DomainSpec createDomainSpec() {
    DomainSpec spec =
        new DomainSpec()
            .withDomainUid(UID)
            .withWebLogicCredentialsSecret(new V1LocalObjectReference().name(CREDENTIALS_SECRET_NAME))
            .withConfiguration(new Configuration()
                .withOverridesConfigMap(OVERRIDES_CM).withSecrets(List.of(OVERRIDE_SECRET_1, OVERRIDE_SECRET_2)))
            .withCluster(new V1LocalObjectReference().name("cluster-1"))
            .withImage(LATEST_IMAGE)
            .withDomainHomeSourceType(DomainSourceType.PERSISTENT_VOLUME);
    spec.setServerStartPolicy(ServerStartPolicy.IF_NEEDED);

    return spec;
  }

  private ClusterSpec createClusterSpec() {
    return new ClusterSpec()
        .withClusterName("cluster-1")
        .withReplicas(1)
        .withServerStartPolicy(ServerStartPolicy.IF_NEEDED);
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

  private V1PodSpec getJobPodSpec(V1Job job) {
    return Objects.requireNonNull(job.getSpec()).getTemplate().getSpec();
  }

  private List<V1Job> runStepsAndGetJobs() {
    testSupport.runSteps(JobHelper.createIntrospectionStartStep());
    logRecords.clear();

    return testSupport.getResources(KubernetesTestSupport.JOB);
  }

  DomainConfigurator getConfigurator() {
    return configurator;
  }

  @Test
  void whenNoJob_createIt() throws JsonProcessingException {
    establishWlsDomainWithCluster("cluster-1");
    testSupport.defineResources(
        new V1ConfigMap()
            .metadata(
                new V1ObjectMeta()
                    .namespace(NS)
                    .name(ConfigMapHelper.getIntrospectorConfigMapName(UID))));

    testSupport.runSteps(JobHelper.createIntrospectionStartStep());

    assertThat(logRecords, containsInfo(getJobCreatedMessageKey()));
    assertThat(logRecords, containsFine(getJobDeletedMessageKey()));
  }

  private void establishWlsDomainWithCluster(String s) throws JsonProcessingException {
    IntrospectionTestUtils.defineIntrospectionTopology(testSupport, createDomainConfig(s));
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

  @Test
  void whenNoJob_onInternalError() {
    testSupport.addRetryStrategy(retryStrategy);
    testSupport.failOnCreate(KubernetesTestSupport.JOB, NS, HTTP_INTERNAL_ERROR);

    testSupport.runSteps(JobHelper.createIntrospectionStartStep());

    assertThat(getDomain(), hasStatus().withReason(KUBERNETES)
        .withMessageContaining("create", "job", NS, "failure reported in test"));
  }

  @Test
  void whenNoJob_generateFailedEvent() {
    testSupport.addRetryStrategy(retryStrategy);
    testSupport.failOnCreate(KubernetesTestSupport.JOB, NS, HTTP_INTERNAL_ERROR);

    testSupport.runSteps(JobHelper.createIntrospectionStartStep());

    assertThat(
        "Expected Event " + DOMAIN_FAILED + " expected with message not found",
        getExpectedEventMessage(testSupport, DOMAIN_FAILED),
        stringContainsInOrder("Domain", UID, "failed due to",
            getLocalizedString(KUBERNETES_EVENT_ERROR)));
  }

  @Test
  void whenJobCreated_jobNameContainsDefaultSuffix() {
    testSupport.runSteps(JobHelper.createIntrospectionStartStep());
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
  void whenJobCreatedWithCustomIntrospectorJobnameSuffix_jobNameContainsConfiguredSuffix() {
    TuningParametersStub.setParameter(LegalNames.INTROSPECTOR_JOB_NAME_SUFFIX_PARAM, "-introspector-job");
    testSupport.runSteps(JobHelper.createIntrospectionStartStep());
    logRecords.clear();

    assertThat(getCreatedJobName(), stringContainsInOrder(UID,"-introspector-job"));
  }

  @Test
  void whenJobCreated_specHasOneContainer() {
    List<V1Job> jobs = runStepsAndGetJobs();
    assertThat(getPodTemplateContainers(jobs.get(0)), hasSize(1));
  }

  private List<V1Container> getPodTemplateContainers(V1Job v1Job) {
    return getJobPodSpec(v1Job).getContainers();
  }

  private List<V1Container> getPodTemplateInitContainers(V1Job v1Job) {
    return getJobPodSpec(v1Job).getInitContainers();
  }

  @SuppressWarnings("unchecked")
  @Test
  void whenJobCreated_hasPredefinedEnvVariables() {
    List<V1Job> jobs = runStepsAndGetJobs();
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
  void whenJobCreatedWithModelHomeDefined_hasModelHomeEnvVariable() {
    getDomain().getSpec()
        .setConfiguration(new Configuration().withModel(new Model().withModelHome(WDT_MODEL_HOME)));
    List<V1Job> jobs = runStepsAndGetJobs();
    List<V1Container> podTemplateContainers = getPodTemplateContainers(jobs.get(0));
    assertThat(
        podTemplateContainers.get(0).getEnv(),
        hasEnvVar("WDT_MODEL_HOME", WDT_MODEL_HOME));
  }

  @Test
  void whenJobCreatedWithAuxiliaryImageDefined_hasAuxiliaryImageInitContainerVolumeAndMounts() {
    getConfigurator()
        .withDomainHomeSourceType(DomainSourceType.FROM_MODEL)
            .withAuxiliaryImages(Collections.singletonList(getAuxiliaryImage("wdt-image:v1")));

    V1Job job = runStepsAndGetJobs().get(0);
    List<V1Container> podTemplateInitContainers = getPodTemplateInitContainers(job);

    assertThat(
            podTemplateInitContainers,
            Matchers.hasAuxiliaryImageInitContainer(AUXILIARY_IMAGE_INIT_CONTAINER_NAME_PREFIX + 1,
                "wdt-image:v1", "IfNotPresent"));
    assertThat(getJobPodSpec(job).getVolumes(),
            hasItem(new V1Volume().name(AUXILIARY_IMAGE_INTERNAL_VOLUME_NAME).emptyDir(
                    new V1EmptyDirVolumeSource())));
    assertThat(getPodTemplateContainers(job).get(0).getVolumeMounts(),
            hasItem(new V1VolumeMount().name(AUXILIARY_IMAGE_INTERNAL_VOLUME_NAME)
                    .mountPath(DEFAULT_AUXILIARY_IMAGE_MOUNT_PATH)));
  }

  @Test
  void whenJobCreatedWithInitializeDomainOnPVDefined_hasSecretsVolumeAndMounts() {
    getConfigurator().withInitializeDomainOnPVOpssWalletFileSecret("wfSecret")
        .withInitializeDomainOnPVOpssWalletPasswordSecret("wpSecret");

    testSupport.defineResources(createSecret("wpSecret"), createSecret("wfSecret"));

    List<V1Job> jobs = runStepsAndGetJobs();
    V1Job job = jobs.get(0);

    assertThat(getJobPodSpec(job).getVolumes(),
        hasItem(new V1Volume().name(SECRETS_VOLUME).secret(
            new V1SecretVolumeSource().secretName("webLogicCredentialsSecretName").defaultMode(420))));
    assertThat(getJobPodSpec(job).getVolumes(),
        hasItem(new V1Volume().name(OPSS_WALLETFILE_VOLUME).secret(
            new V1SecretVolumeSource().secretName("wfSecret").defaultMode(420).optional(true))));
    assertThat(getJobPodSpec(job).getVolumes(),
        hasItem(new V1Volume().name(OPSS_KEYPASSPHRASE_VOLUME).secret(
            new V1SecretVolumeSource().secretName("wpSecret").defaultMode(420).optional(true))));
    assertThat(getCreatedPodSpecContainers(jobs).get(0).getVolumeMounts(),
        hasItem(new V1VolumeMount().name(OPSS_WALLETFILE_VOLUME)
            .mountPath(OPSS_WALLETFILE_MOUNT_PATH).readOnly(true)));
    assertThat(getCreatedPodSpecContainers(jobs).get(0).getVolumeMounts(),
        hasItem(new V1VolumeMount().name(OPSS_KEYPASSPHRASE_VOLUME)
            .mountPath(OPSS_KEY_MOUNT_PATH).readOnly(true)));
  }

  @Test
  void whenJobCreatedWithInitDomainOnPVWithModelEncryption_hasSecretsVolumeAndMounts() {
    getConfigurator().withInitializeDomainOnPVModelEncryptionSecret("encryptedSecret");
    testSupport.defineResources(createSecret("encryptedSecret"));

    List<V1Job> jobs = runStepsAndGetJobs();
    V1Job job = jobs.get(0);

    assertThat(getJobPodSpec(job).getVolumes(),
            hasItem(new V1Volume().name(WDT_MODEL_ENCRYPTION_PASSPHRASE_VOLUME).secret(
                    new V1SecretVolumeSource().secretName("encryptedSecret").optional(true).defaultMode(420))));
    assertThat(getCreatedPodSpecContainers(jobs).get(0).getVolumeMounts(),
            hasItem(new V1VolumeMount().name(WDT_MODEL_ENCRYPTION_PASSPHRASE_VOLUME)
                    .mountPath(WDT_MODEL_ENCRYPTION_PASSPHRASE_MOUNT_PATH).readOnly(true)));

  }


  private V1Secret createSecret(String name) {
    return new V1Secret().metadata(new V1ObjectMeta().name(name).namespace(NS));
  }

  @Test
  void whenJobCreatedWithoutInitializeDomainOnPVDefined_dontHaveSecretsVolumeAndMounts() {
    List<V1Job> jobs = runStepsAndGetJobs();
    V1Job job = jobs.get(0);

    assertThat(getJobPodSpec(job).getVolumes(),
        hasItem(new V1Volume().name(SECRETS_VOLUME).secret(
            new V1SecretVolumeSource().secretName("webLogicCredentialsSecretName").defaultMode(420))));
    assertThat(getJobPodSpec(job).getVolumes(),
        not(hasItem(new V1Volume().name(OPSS_WALLETFILE_VOLUME).secret(
            new V1SecretVolumeSource().secretName("wfSecret").defaultMode(420).optional(true)))));
    assertThat(getJobPodSpec(job).getVolumes(),
        not(hasItem(new V1Volume().name(OPSS_KEYPASSPHRASE_VOLUME).secret(
            new V1SecretVolumeSource().secretName("wpSecret").defaultMode(420).optional(true)))));
    assertThat(getCreatedPodSpecContainers(jobs).get(0).getVolumeMounts(),
        not(hasItem(new V1VolumeMount().name(OPSS_WALLETFILE_VOLUME)
            .mountPath(OPSS_WALLETFILE_MOUNT_PATH).readOnly(true))));
    assertThat(getCreatedPodSpecContainers(jobs).get(0).getVolumeMounts(),
        not(hasItem(new V1VolumeMount().name(OPSS_KEYPASSPHRASE_VOLUME)
            .mountPath(OPSS_KEY_MOUNT_PATH).readOnly(true))));
  }

  @Test
  void whenJobCreatedWithInitializeDomainOnPVCreateDomainCMDefined_hasConfigMapVolumeAndMounts() {
    V1ConfigMap cm = new V1ConfigMap().metadata(new V1ObjectMeta().name("initpvdomaincm").namespace(NS));
    testSupport.defineResources(cm);
    getConfigurator().withDomainCreationConfigMap("initpvdomaincm");

    List<V1Job> jobs = runStepsAndGetJobs();
    V1Job job = jobs.get(0);

    assertThat(getJobPodSpec(job).getVolumes(),
        hasItem(new V1Volume().name("initpvdomaincm-volume").configMap(
            new V1ConfigMapVolumeSource().name("initpvdomaincm").defaultMode(365))));
    assertThat(getCreatedPodSpecContainers(jobs).get(0).getVolumeMounts(),
        hasItem(new V1VolumeMount().name("initpvdomaincm-volume")
            .mountPath(WDTCONFIGMAP_MOUNT_PATH).readOnly(true)));
  }

  private DomainOnPV getInitDomain() {
    DomainOnPV initDomain = new DomainOnPV();
    initDomain.opss(getOpss());
    return initDomain;
  }

  private Opss getOpss() {
    Opss opss = new Opss();
    opss.withWalletFileSecret("wfSecret");
    opss.withWalletPasswordSecret("wpSecret");
    return opss;
  }

  private List<V1Container> getCreatedPodSpecContainers(List<V1Job> jobs) {
    return getJobPodSpec(jobs.get(0)).getContainers();
  }

  @Nonnull
  private List<AuxiliaryImage> getAuxiliaryImages(String...images) {
    List<AuxiliaryImage> auxiliaryImageList = new ArrayList<>();
    Arrays.stream(images).forEach(image -> auxiliaryImageList.add(getAuxiliaryImage(image)));
    return auxiliaryImageList;
  }

  @Nonnull
  public static AuxiliaryImage getAuxiliaryImage(String image) {
    return new AuxiliaryImage().image(image);
  }

  @Nonnull
  private List<DomainCreationImage> getDomainCreationImages(String...images) {
    List<DomainCreationImage> auxiliaryImageList = new ArrayList<>();
    Arrays.stream(images).forEach(image -> auxiliaryImageList.add(getDomainCreationImage(image)));
    return auxiliaryImageList;
  }

  @Nonnull
  public static DomainCreationImage getDomainCreationImage(String image) {
    return new DomainCreationImage().image(image);
  }

  @Test
  void whenJobCreatedWithAuxiliaryImageAndVolumeHavingAuxiliaryImagePath_hasVolumeMountWithAuxiliaryImagePath() {
    DomainConfiguratorFactory.forDomain(domain)
            .withDomainHomeSourceType(DomainSourceType.FROM_MODEL)
            .withAuxiliaryImageVolumeMountPath(CUSTOM_MOUNT_PATH)
            .withAuxiliaryImages(getAuxiliaryImages("wdt-image:v1"));

    List<V1Job> jobs = runStepsAndGetJobs();
    assertThat(getCreatedPodSpecContainers(jobs).get(0).getVolumeMounts(),
            hasItem(new V1VolumeMount().name(AUXILIARY_IMAGE_INTERNAL_VOLUME_NAME)
                    .mountPath(CUSTOM_MOUNT_PATH)));
  }

  @Test
  void whenJobCreatedWithAuxiliaryImageVolumeWithMedium_createdJobPodsHasVolumeWithSpecifiedMedium() {
    getConfigurator()
        .withDomainHomeSourceType(DomainSourceType.FROM_MODEL)
            .withAuxiliaryImageVolumeMedium("Memory")
            .withAuxiliaryImages(getAuxiliaryImages("wdt-image:v1"));

    V1Job job = runStepsAndGetJobs().get(0);
    assertThat(getJobPodSpec(job).getVolumes(),
            hasItem(new V1Volume().name(AUXILIARY_IMAGE_INTERNAL_VOLUME_NAME).emptyDir(
                    new V1EmptyDirVolumeSource().medium("Memory"))));
  }

  @Test
  void whenJobCreatedWithAuxiliaryImageVolumeWithSizeLimit_createdJobPodsHasVolumeWithSpecifiedSizeLimit() {
    getConfigurator()
        .withDomainHomeSourceType(DomainSourceType.FROM_MODEL)
            .withAuxiliaryImageVolumeSizeLimit("100G")
            .withAuxiliaryImages(getAuxiliaryImages());

    V1Job job = runStepsAndGetJobs().get(0);
    assertThat(getJobPodSpec(job).getVolumes(),
            hasItem(new V1Volume().name(AUXILIARY_IMAGE_INTERNAL_VOLUME_NAME).emptyDir(
                    new V1EmptyDirVolumeSource().sizeLimit(Quantity.fromString("100G")))));
  }

  @Test
  void whenJobCreatedWithAuxiliaryImageWithImagePullPolicy_createJobPodHasImagePullPolicy() {
    getConfigurator()
        .withDomainHomeSourceType(DomainSourceType.FROM_MODEL)
        .withAuxiliaryImages(Collections.singletonList(getAuxiliaryImage("wdt-image:v1")
                .imagePullPolicy("Always")));

    V1Job job = runStepsAndGetJobs().get(0);
    assertThat(getPodTemplateInitContainers(job),
                Matchers.hasAuxiliaryImageInitContainer(AUXILIARY_IMAGE_INIT_CONTAINER_NAME_PREFIX + 1,
                    "wdt-image:v1", "Always"));
  }

  @Test
  void whenJobCreatedWithAuxiliaryImageWithResourceRequirements_createInitContainerHasResourceRequirements() {
    getConfigurator()
        .withDomainHomeSourceType(DomainSourceType.FROM_MODEL)
        .withAuxiliaryImages(Collections.singletonList(getAuxiliaryImage("wdt-image:v1")
            .imagePullPolicy("Always")))
        .withLimitRequirement("cpu", "250m")
        .withRequestRequirement("memory", "1Gi");

    V1Job job = runStepsAndGetJobs().get(0);
    assertThat(getPodTemplateInitContainers(job),
        Matchers.hasAuxiliaryImageInitContainer(AUXILIARY_IMAGE_INIT_CONTAINER_NAME_PREFIX + 1,
            "wdt-image:v1", "Always", new V1ResourceRequirements()
                .limits(Collections.singletonMap("cpu", new Quantity("250m")))
                .requests(Collections.singletonMap("memory", new Quantity("1Gi")))));
  }

  @Test
  void whenJobCreatedWithAuxiliaryImageWithResourceLimits_createInitContainerHasResourceLimits() {
    getConfigurator()
        .withDomainHomeSourceType(DomainSourceType.FROM_MODEL)
        .withAuxiliaryImages(Collections.singletonList(getAuxiliaryImage("wdt-image:v1")
            .imagePullPolicy("Always")))
        .withLimitRequirement("memory", "1Gi");

    V1Job job = runStepsAndGetJobs().get(0);
    assertThat(getPodTemplateInitContainers(job),
        Matchers.hasAuxiliaryImageInitContainer(AUXILIARY_IMAGE_INIT_CONTAINER_NAME_PREFIX + 1,
            "wdt-image:v1", "Always", new V1ResourceRequirements()
                .limits(Collections.singletonMap("memory", new Quantity("1Gi")))));
  }

  @Test
  void whenJobCreatedWithAuxiliaryImageWithResourceRequests_createInitContainerHasResourceRequests() {
    getConfigurator()
        .withDomainHomeSourceType(DomainSourceType.FROM_MODEL)
        .withAuxiliaryImages(Collections.singletonList(getAuxiliaryImage("wdt-image:v1")
            .imagePullPolicy("Always")))
        .withRequestRequirement("memory", "1Gi");

    V1Job job = runStepsAndGetJobs().get(0);
    assertThat(getPodTemplateInitContainers(job),
        Matchers.hasAuxiliaryImageInitContainer(AUXILIARY_IMAGE_INIT_CONTAINER_NAME_PREFIX + 1,
            "wdt-image:v1", "Always", new V1ResourceRequirements()
                .requests(Collections.singletonMap("memory", new Quantity("1Gi")))));
  }

  @Test
  void whenJobCreatedWithAIAndCustomSourceWDTInstallHome_createPodWithInitContainerHavingCustomSourceWDTInstallHome() {
    getConfigurator()
        .withDomainHomeSourceType(DomainSourceType.FROM_MODEL)
            .withAuxiliaryImages(Collections.singletonList(getAuxiliaryImage("wdt-image:v1")
                    .sourceWDTInstallHome(CUSTOM_WDT_INSTALL_SOURCE_HOME)));

    V1Job job = runStepsAndGetJobs().get(0);
    assertThat(getPodTemplateInitContainers(job),
                Matchers.hasAuxiliaryImageInitContainer(AUXILIARY_IMAGE_INIT_CONTAINER_NAME_PREFIX + 1,
                    "wdt-image:v1", "IfNotPresent", CUSTOM_WDT_INSTALL_SOURCE_HOME));
  }

  @Test
  void whenJobCreatedWithAIAndCustomSourceModelHome_createPodWithInitContainerHavingCustomSourceModelHome() {
    getConfigurator()
        .withDomainHomeSourceType(DomainSourceType.FROM_MODEL)
            .withAuxiliaryImages(Collections.singletonList(getAuxiliaryImage("wdt-image:v1")
                    .sourceModelHome(CUSTOM_MODEL_SOURCE_HOME)));

    V1Job job = runStepsAndGetJobs().get(0);
    assertThat(getPodTemplateInitContainers(job),
            Matchers.hasAuxiliaryImageInitContainer(AUXILIARY_IMAGE_INIT_CONTAINER_NAME_PREFIX + 1,
                "wdt-image:v1", "IfNotPresent",
                AUXILIARY_IMAGE_DEFAULT_SOURCE_WDT_INSTALL_HOME, CUSTOM_MODEL_SOURCE_HOME));
  }

  @Test
  void whenJobCreatedWithMultipleAuxiliaryImages_createdJobPodsHasMultipleInitContainers() {
    getConfigurator()
            .withDomainHomeSourceType(DomainSourceType.FROM_MODEL)
            .withAuxiliaryImages(getAuxiliaryImages("wdt-image1:v1", "wdt-image2:v1"));

    V1Job job = runStepsAndGetJobs().get(0);
    assertThat(getPodTemplateInitContainers(job),
            org.hamcrest.Matchers.allOf(
                Matchers.hasAuxiliaryImageInitContainer(AUXILIARY_IMAGE_INIT_CONTAINER_NAME_PREFIX + 1,
                    "wdt-image1:v1", "IfNotPresent"),
                    Matchers.hasAuxiliaryImageInitContainer(AUXILIARY_IMAGE_INIT_CONTAINER_NAME_PREFIX + 2,
                        "wdt-image2:v1", "IfNotPresent")));
    assertThat(getPodTemplateContainers(job).get(0).getVolumeMounts(), hasSize(8));
    assertThat(getPodTemplateContainers(job).get(0).getVolumeMounts(),
            hasItem(new V1VolumeMount().name(AUXILIARY_IMAGE_INTERNAL_VOLUME_NAME)
                    .mountPath(DEFAULT_AUXILIARY_IMAGE_MOUNT_PATH)));
  }

  @Test
  void whenJobCreatedWithMultipleDomainCreationImages_createdJobPodsHasMultipleInitContainers() {
    getConfigurator()
        .withDomainHomeSourceType(DomainSourceType.PERSISTENT_VOLUME)
        .withInitializeDomainOnPv(new InitializeDomainOnPV()
            .domain(new DomainOnPV().domainCreationImages(getDomainCreationImages("wdt-image1:v1", "wdt-image2:v1"))));

    V1Job job = runStepsAndGetJobs().get(0);
    assertThat(getPodTemplateInitContainers(job),
        org.hamcrest.Matchers.allOf(
            Matchers.hasAuxiliaryImageInitContainer(AUXILIARY_IMAGE_INIT_CONTAINER_NAME_PREFIX + 1,
                "wdt-image1:v1", "IfNotPresent"),
            Matchers.hasAuxiliaryImageInitContainer(AUXILIARY_IMAGE_INIT_CONTAINER_NAME_PREFIX + 2,
                "wdt-image2:v1", "IfNotPresent")));
    assertThat(getPodTemplateContainers(job).get(0).getVolumeMounts(), hasSize(7));
    assertThat(getPodTemplateContainers(job).get(0).getVolumeMounts(),
        hasItem(new V1VolumeMount().name(AUXILIARY_IMAGE_INTERNAL_VOLUME_NAME)
            .mountPath(DOMAIN_CREATION_IMAGE_MOUNT_PATH)));
  }

  @Test
  void whenJobCreatedWithFluentd_mustHaveFluentdContainerAndMountPathIsCorrect() {
    DomainConfiguratorFactory.forDomain(domain)
            .withFluentdConfiguration(true, "elastic-cred", null,
                null, null);

    List<V1Job> jobs = runStepsAndGetJobs();

    V1Container fluentdContainer = Optional.ofNullable(getCreatedPodSpecContainers(jobs))
            .orElseGet(Collections::emptyList)
            .stream()
            .filter(c -> c.getName().equals(FLUENTD_CONTAINER_NAME))
            .findFirst()
            .orElse(null);

    assertThat(fluentdContainer, notNullValue());

    assertThat(Optional.of(fluentdContainer)
            .map(V1Container::getVolumeMounts)
            .orElseGet(Collections::emptyList)
            .stream()
            .filter(c -> "/fluentd/etc/fluentd.conf".equals(c.getMountPath())), notNullValue());
  }

  @Test
  void whenJobCreatedWithFluentdTerminatedDuringIntrospection_checkExpectedMessage() {
    String jobName = UID + "-introspector";
    DomainConfiguratorFactory.forDomain(domain).withFluentdConfiguration(true,
        "elastic-cred", null, null, null);
    V1Pod jobPod = new V1Pod().metadata(new V1ObjectMeta().name(jobName).namespace(NS));
    FluentdUtils.defineFluentdJobContainersCompleteStatus(jobPod, jobName, true, true);
    testSupport.defineResources(jobPod);
    defineFailedFluentdContainerInIntrospection();

    testSupport.runSteps(JobHelper.createIntrospectionStartStep());
    logRecords.clear();
    String expectedDetail = LOGGER.formatMessage(INTROSPECTOR_FLUENTD_CONTAINER_TERMINATED,
            jobPod.getMetadata().getName(),
            jobPod.getMetadata().getNamespace(), 1, null, null);

    assertThat(getUpdatedDomain().getStatus().getMessage(), containsString(expectedDetail));
  }

  @Test
  void whenJobCreatedWithFluentdAndSuccessIntrospection_JobIsTerminatedAndJobTerminatedMarkerInserted() {
    String jobName = UID + "-introspector";
    DomainConfiguratorFactory.forDomain(domain)
        .withFluentdConfiguration(true, "elastic-cred", null, null,
            null);
    V1Pod jobPod = new V1Pod().metadata(new V1ObjectMeta().name(jobName).namespace(NS));
    FluentdUtils.defineFluentdJobContainersCompleteStatus(jobPod, jobName, true, false);
    testSupport.defineResources(jobPod);
    defineNormalFluentdContainerInIntrospection();
    testSupport.addToPacket(DOMAIN_TOPOLOGY, createDomainConfig("cluster-1"));

    Packet packet = testSupport.runSteps(JobHelper.createIntrospectionStartStep(), terminalStep);
    logRecords.clear();
    assertThat(packet.get(JOB_POD_INTROSPECT_CONTAINER_TERMINATED),
            equalTo(JOB_POD_INTROSPECT_CONTAINER_TERMINATED_MARKER));
    assertThat(terminalStep.wasRun(), is(true));

  }

  @Test
  void whenJobCreatedWithFluentdAndSuccessIntrospection_containerShouldNotHaveTheseEnvEntriesIfElsCredisNull() {
    DomainConfiguratorFactory.forDomain(domain)
        .withFluentdConfiguration(true, null, null, null, null);

    List<V1Job> jobs = runStepsAndGetJobs();

    V1Container fluentdContainer = Optional.ofNullable(getCreatedPodSpecContainers(jobs))
        .orElseGet(Collections::emptyList)
        .stream()
        .filter(c -> c.getName().equals(FLUENTD_CONTAINER_NAME))
        .findFirst()
        .orElse(null);

    assertThat(fluentdContainer, notNullValue());

    Set<String> elsDefaultEnvNames = new HashSet<>(Arrays.asList("ELASTICSEARCH_HOST", "ELASTICSEARCH_PORT",
        "ELASTICSEARCH_USER", "ELASTICSEARCH_PASSWORD"));

    long counter = Optional.of(fluentdContainer)
        .map(V1Container::getEnv)
        .orElseGet(Collections::emptyList)
        .stream()
        .filter(c -> elsDefaultEnvNames.contains(c.getName())).count();

    assertThat(counter, org.hamcrest.Matchers.equalTo(0L));

  }

  @Test
  void whenJobCreatedWithFluentdAndSuccessIntrospection_containerShouldMatchUserSpecifiedArgsCommand() {
    List<String> args = new ArrayList<>(List.of("line1", "line2"));
    List<String> command = new ArrayList<>(List.of("line1", "line2"));
    DomainConfigurator configurator = DomainConfiguratorFactory.forDomain(domain)
        .withFluentdConfiguration(true, null, null, args, command);

    List<V1Job> jobs = runStepsAndGetJobs();

    V1Container fluentdContainer = Optional.ofNullable(getCreatedPodSpecContainers(jobs))
        .orElseGet(Collections::emptyList)
        .stream()
        .filter(c -> c.getName().equals(FLUENTD_CONTAINER_NAME))
        .findFirst()
        .orElse(null);

    assertThat(fluentdContainer, notNullValue());

    List<String> containerArgs = Optional.of(fluentdContainer)
        .map(V1Container::getArgs)
        .orElseGet(Collections::emptyList);

    List<String> containerCommand = Optional.of(fluentdContainer)
        .map(V1Container::getCommand)
        .orElseGet(Collections::emptyList);

    assertThat(Objects.equals(containerArgs, args), equalTo(true));
    assertThat(Objects.equals(containerCommand, command), equalTo(true));

  }

  @Test
  void whenNewSuccessfulJobExists_readOldPodLogWithoutCreatingNewJob() {
    consoleHandlerMemento.ignoreMessage(getJobDeletedMessageKey());
    testSupport.addToPacket(DOMAIN_TOPOLOGY, createDomainConfig("cluster-1"));
    defineCompletedIntrospection();
    testSupport.failOnCreate(JOB, NS, HTTP_FORBIDDEN);

    testSupport.runSteps(JobHelper.createIntrospectionStartStep());

    assertThat(getUpdatedDomain(), not(hasCondition(FAILED)));
  }

  private void defineCompletedIntrospection() {
    testSupport.defineResources(asCompletedJob(createIntrospectorJob()));
    testSupport.definePodLog(LegalNames.toJobIntrospectorName(UID), NS, INFO_MESSAGE);
  }

  @Test
  void whenNewFailedJobExists_readPodLogAndReportFailure() {
    ignoreIntrospectorFailureLogs();

    testSupport.addToPacket(DOMAIN_TOPOLOGY, createDomainConfig("cluster-1"));
    defineFailedIntrospection();
    testSupport.runSteps(JobHelper.createIntrospectionStartStep());

    assertThat(getUpdatedDomain(), hasCondition(FAILED));
  }

  @Test
  void whenNewFailedJobExistsAndUnableToReadContainerLogs_reportFailure() {
    ignoreIntrospectorFailureLogs();

    testSupport.addToPacket(DOMAIN_TOPOLOGY, createDomainConfig("cluster-1"));
    defineFailedIntrospectionWithUnableToReadContainerLogs();
    testSupport.runSteps(JobHelper.createIntrospectionStartStep());

    assertThat(getUpdatedDomain(), hasCondition(FAILED));
  }

  @Test
  void whenNewJobSucceededOnFailedDomain_clearFailedCondition() {
    consoleHandlerMemento.ignoreMessage(getJobDeletedMessageKey());
    testSupport.addToPacket(DOMAIN_TOPOLOGY, createDomainConfig("cluster-1"));
    getDomain().getOrCreateStatus().addCondition(new DomainCondition(FAILED).withReason(INTROSPECTION));
    defineCompletedIntrospection();

    testSupport.runSteps(JobHelper.createIntrospectionStartStep());

    assertThat(getUpdatedDomain(), not(hasCondition(FAILED)));
  }

  private void ignoreIntrospectorFailureLogs() {
    consoleHandlerMemento.ignoreMessage(getJobFailedMessageKey());
    consoleHandlerMemento.ignoreMessage(getJobFailedDetailMessageKey());
  }

  private void defineFailedIntrospection() {
    testSupport.defineResources(asFailedJob(createIntrospectorJob()));
    testSupport.definePodLog(LegalNames.toJobIntrospectorName(UID), NS, SEVERE_MESSAGE);
  }

  private void defineFailedIntrospectionWithUnableToReadContainerLogs() {
    testSupport.defineResources(asFailedJob(createIntrospectorJob()));
    testSupport.definePodLog(LegalNames.toJobIntrospectorName(UID), NS,
        "unable to retrieve container logs for container containerd://9295e63");
  }

  private void defineFailedFluentdContainerInIntrospection() {
    testSupport.defineResources(asFailedJob(createIntrospectorJob()));
    testSupport.definePodLog(LegalNames.toJobIntrospectorName(UID), NS, INFO_MESSAGE);
  }

  private void defineNormalFluentdContainerInIntrospection() {
    testSupport.defineResources(createIntrospectorJob());
    testSupport.definePodLog(LegalNames.toJobIntrospectorName(UID), NS, INFO_MESSAGE);
  }

  @Test
  void whenPreviousFailedJobExists_readOnlyNewPodLog() throws Exception {
    ignoreJobCreatedAndDeletedLogs();
    ignoreIntrospectorFailureLogs();
    testSupport.addToPacket(DOMAIN_TOPOLOGY, createDomainConfig("cluster-1"));
    definePreviousFailedIntrospectionWithoutPodLog();
    testSupport.doAfterCall(JOB, "deleteJob", this::defineNewIntrospectionResult);

    testSupport.runSteps(JobHelper.createIntrospectionStartStep());

    testSupport.throwOnCompletionFailure();
  }

  private void defineNewIntrospectionResult() {
    testSupport.getPacket().put(JOB_POD, new V1Pod().metadata(new V1ObjectMeta().name(jobPodName))
        .status(createJobPodStatus()));
    testSupport.definePodLog(jobPodName, NS, SEVERE_MESSAGE);
  }

  private V1PodStatus createJobPodStatus() {
    return new V1PodStatus().containerStatuses(
        Collections.singletonList(new V1ContainerStatus().name(UID + "-introspector").ready(true).started(true)));
  }

  private void ignoreJobCreatedAndDeletedLogs() {
    consoleHandlerMemento.ignoreMessage(getJobCreatedMessageKey());
    consoleHandlerMemento.ignoreMessage(getJobDeletedMessageKey());
  }

  private void defineIntrospection() {
    testSupport.defineResources(createIntrospectorJob());
  }

  private void defineIntrospectionWithAuxiliaryImage(String... auxiliaryImages) {
    if (auxiliaryImages != null) {
      V1Job job = createIntrospectorJob();
      int index = 1;
      for (String auxiliaryImage: auxiliaryImages) {
        job.getSpec()
            .getTemplate()
            .getSpec()
            .addInitContainersItem(
                new V1Container().name("operator-aux-container" + index++).image(auxiliaryImage));
      }
      testSupport.defineResources(job);
    }
  }

  private void defineIntrospectionWithInitContainer() {
    V1Job job = createIntrospectorJob();
    job.getSpec()
        .getTemplate()
        .getSpec()
        .addInitContainersItem(
            new V1Container().name("some-init-container").image("i-am-not-an-auxiliary-image"));
    testSupport.defineResources(job);
  }

  private void definePreviousFailedIntrospection() {
    defineFailedIntrospection();
    getDomain().getOrCreateStatus().setFailedIntrospectionUid(JOB_UID);
  }

  private void definePreviousFailedIntrospectionWithoutPodLog() {
    testSupport.defineResources(asFailedJob(createIntrospectorJob()));
    getDomain().getOrCreateStatus().setFailedIntrospectionUid(JOB_UID);
  }

  private void defineIntrospectionWithIntrospectVersionLabel(String introspectVersion) {
    testSupport.defineResources(createIntrospectorJobWithIntrospectVersionLabel(introspectVersion));
  }

  @Test
  void whenPreviousFailedJobExists_deleteIt() {
    ignoreJobCreatedAndDeletedLogs();
    ignoreIntrospectorFailureLogs();
    testSupport.addToPacket(DOMAIN_TOPOLOGY, createDomainConfig("cluster-1"));
    definePreviousFailedIntrospection();
    testSupport.doAfterCall(JOB, "deleteJob", this::recordJobDeleted);

    testSupport.runSteps(JobHelper.createIntrospectionStartStep());

    assertThat(jobDeleted, is(true));
  }

  private void recordJobDeleted() {
    this.jobDeleted = true;
  }

  @Test
  void whenPreviousFailedJobExists_createNewJob() {
    ignoreIntrospectorFailureLogs();
    ignoreJobCreatedAndDeletedLogs();
    testSupport.addToPacket(DOMAIN_TOPOLOGY, createDomainConfig("cluster-1"));
    definePreviousFailedIntrospection();
    testSupport.doOnCreate(JOB, this::recordJob);

    testSupport.runSteps(JobHelper.createIntrospectionStartStep());

    assertThat(affectedJob, notNullValue());
  }

  @Test
  void whenPreviousFailedJobWithImagePullErrorExistsAndMakeRightContinued_createNewJob() {
    ignoreIntrospectorFailureLogs();
    ignoreJobCreatedAndDeletedLogs();
    testSupport.addToPacket(DOMAIN_TOPOLOGY, createDomainConfig("cluster-1"));
    defineFailedIntrospectionWithImagePullError("ErrImagePull");
    testSupport.doOnCreate(JOB, this::recordJob);
    testSupport.doAfterCall(JOB, "deleteJob", this::replaceFailedJobPodWithSuccess);

    testSupport.runSteps(JobHelper.createIntrospectionStartStep());

    assertThat(affectedJob, notNullValue());
  }

  @Test
  void whenJobInProgressAndAuxiliaryImageChanged_createNewJob() {
    ignoreIntrospectorFailureLogs();
    ignoreJobCreatedAndDeletedLogs();

    getConfigurator()
        .withAuxiliaryImages(Collections.singletonList(getAuxiliaryImage("wdt-image:v2")));

    testSupport.addToPacket(DOMAIN_TOPOLOGY, createDomainConfig("cluster-1"));
    defineIntrospectionWithAuxiliaryImage("wdt-image:v1");
    testSupport.doOnCreate(JOB, this::recordJob);

    testSupport.runSteps(JobHelper.createIntrospectionStartStep());

    assertThat(affectedJob, notNullValue());
  }

  @Test
  void whenJobInProgressAndOneOfTwoAuxiliaryImagesChanged_createNewJob() {
    ignoreIntrospectorFailureLogs();
    ignoreJobCreatedAndDeletedLogs();

    getConfigurator()
        .withAuxiliaryImages(List.of(getAuxiliaryImage("wdt-image1:v1"), getAuxiliaryImage("wdt-image2:v2")));

    testSupport.addToPacket(DOMAIN_TOPOLOGY, createDomainConfig("cluster-1"));
    defineIntrospectionWithAuxiliaryImage("wdt-image1:v1", "wdt-image2:v1");
    testSupport.doOnCreate(JOB, this::recordJob);

    testSupport.runSteps(JobHelper.createIntrospectionStartStep());

    assertThat(affectedJob, notNullValue());
  }

  @Test
  void whenJobInProgressAndFirstAuxiliaryImageAdded_createNewJob() {
    ignoreIntrospectorFailureLogs();
    ignoreJobCreatedAndDeletedLogs();

    getConfigurator()
        .withDomainHomeSourceType(DomainSourceType.FROM_MODEL)
        .withAuxiliaryImages(Collections.singletonList(getAuxiliaryImage("wdt-image:v2")));

    testSupport.addToPacket(DOMAIN_TOPOLOGY, createDomainConfig("cluster-1"));
    defineIntrospection();
    testSupport.doOnCreate(JOB, this::recordJob);

    testSupport.runSteps(JobHelper.createIntrospectionStartStep());

    assertThat(affectedJob, notNullValue());
  }

  @Test
  void whenJobInProgressAndAdditaionalAuxiliaryImageAdded_createNewJob() {
    ignoreIntrospectorFailureLogs();
    ignoreJobCreatedAndDeletedLogs();

    getConfigurator()
        .withAuxiliaryImages(List.of(getAuxiliaryImage("wdt-image:v1"), getAuxiliaryImage("wdt-image2:v1")));

    testSupport.addToPacket(DOMAIN_TOPOLOGY, createDomainConfig("cluster-1"));
    defineIntrospectionWithAuxiliaryImage("wdt-image:v1");
    testSupport.doOnCreate(JOB, this::recordJob);

    testSupport.runSteps(JobHelper.createIntrospectionStartStep());

    assertThat(affectedJob, notNullValue());
  }


  @Test
  void whenJobInProgressAndAuxiliaryImageRemoved_createNewJob() {
    ignoreIntrospectorFailureLogs();
    ignoreJobCreatedAndDeletedLogs();

    getConfigurator()
        .withAuxiliaryImages(List.of(getAuxiliaryImage("wdt-image:v1")));

    testSupport.addToPacket(DOMAIN_TOPOLOGY, createDomainConfig("cluster-1"));
    defineIntrospectionWithAuxiliaryImage("wdt-image:v1", "wdt-image2:v1");
    testSupport.doOnCreate(JOB, this::recordJob);

    testSupport.runSteps(JobHelper.createIntrospectionStartStep());

    assertThat(affectedJob, notNullValue());
  }

  @Test
  void whenJobInProgressAndImageChanged_createNewJob() {
    ignoreIntrospectorFailureLogs();
    ignoreJobCreatedAndDeletedLogs();

    getConfigurator().withDefaultImage("weblogic-image:v2");

    testSupport.addToPacket(DOMAIN_TOPOLOGY, createDomainConfig("cluster-1"));
    defineIntrospection();
    testSupport.doOnCreate(JOB, this::recordJob);

    testSupport.runSteps(JobHelper.createIntrospectionStartStep());

    assertThat(affectedJob, notNullValue());
  }

  @Test
  void whenJobInProgressAndNoImageChanged_doNotCreateNewJob() {
    ignoreIntrospectorFailureLogs();
    ignoreJobCreatedAndDeletedLogs();

    getConfigurator()
        .withDomainHomeSourceType(DomainSourceType.FROM_MODEL)
        .withAuxiliaryImages(Collections.singletonList(getAuxiliaryImage("wdt-image:v1")));

    testSupport.addToPacket(DOMAIN_TOPOLOGY, createDomainConfig("cluster-1"));
    defineIntrospectionWithAuxiliaryImage("wdt-image:v1");
    testSupport.doOnCreate(JOB, this::recordJob);

    testSupport.runSteps(JobHelper.createIntrospectionStartStep());

    assertThat(affectedJob, nullValue());
  }

  @Test
  void whenJobInProgressAndNoImageChanged_withoutAuxiliaryImage_doNotCreateNewJob() {
    ignoreIntrospectorFailureLogs();
    ignoreJobCreatedAndDeletedLogs();

    testSupport.addToPacket(DOMAIN_TOPOLOGY, createDomainConfig("cluster-1"));
    defineIntrospection();
    testSupport.doOnCreate(JOB, this::recordJob);

    testSupport.runSteps(JobHelper.createIntrospectionStartStep());

    assertThat(affectedJob, nullValue());
  }

  @Test
  void whenJobInProgressAndNoImageChanged_mulitipleAuxImages_doNotCreateNewJob() {
    ignoreIntrospectorFailureLogs();
    ignoreJobCreatedAndDeletedLogs();

    getConfigurator()
        .withDomainHomeSourceType(DomainSourceType.FROM_MODEL)
        .withAuxiliaryImages(List.of(getAuxiliaryImage("wdt-image1:v1"), getAuxiliaryImage("wdt-image2:v1")));

    testSupport.addToPacket(DOMAIN_TOPOLOGY, createDomainConfig("cluster-1"));
    defineIntrospectionWithAuxiliaryImage("wdt-image2:v1", "wdt-image1:v1");
    testSupport.doOnCreate(JOB, this::recordJob);

    testSupport.runSteps(JobHelper.createIntrospectionStartStep());

    assertThat(affectedJob, nullValue());
  }

  @Test
  void whenJobInProgressWithNonAuxImageInitContainer_doNotCreateNewJob() {
    ignoreIntrospectorFailureLogs();
    ignoreJobCreatedAndDeletedLogs();

    testSupport.addToPacket(DOMAIN_TOPOLOGY, createDomainConfig("cluster-1"));
    defineIntrospectionWithInitContainer();
    testSupport.doOnCreate(JOB, this::recordJob);

    testSupport.runSteps(JobHelper.createIntrospectionStartStep());

    assertThat(affectedJob, nullValue());
  }

  @Test
  void whenJobInProgressAndIntrospectVersionAdded_createNewJob() {
    ignoreIntrospectorFailureLogs();
    ignoreJobCreatedAndDeletedLogs();

    getConfigurator().withIntrospectVersion("v2");

    testSupport.addToPacket(DOMAIN_TOPOLOGY, createDomainConfig("cluster-1"));
    defineIntrospection();
    testSupport.doOnCreate(JOB, this::recordJob);

    testSupport.runSteps(JobHelper.createIntrospectionStartStep());

    assertThat(affectedJob, notNullValue());
  }

  @Test
  void whenJobInProgressAndIntrospectVersionChanged_createNewJob() {
    ignoreIntrospectorFailureLogs();
    ignoreJobCreatedAndDeletedLogs();

    getConfigurator().withIntrospectVersion("v2");

    testSupport.addToPacket(DOMAIN_TOPOLOGY, createDomainConfig("cluster-1"));
    defineIntrospectionWithIntrospectVersionLabel("v1");
    testSupport.doOnCreate(JOB, this::recordJob);

    testSupport.runSteps(JobHelper.createIntrospectionStartStep());

    assertThat(affectedJob, notNullValue());
  }

  @Test
  void whenJobInProgressAndIntrospectVersionUnchanged_doNotCreateNewJob() {
    ignoreIntrospectorFailureLogs();
    ignoreJobCreatedAndDeletedLogs();

    getConfigurator().withIntrospectVersion("v2");

    testSupport.addToPacket(DOMAIN_TOPOLOGY, createDomainConfig("cluster-1"));
    defineIntrospectionWithIntrospectVersionLabel("v2");
    testSupport.doOnCreate(JOB, this::recordJob);

    testSupport.runSteps(JobHelper.createIntrospectionStartStep());

    assertThat(affectedJob, nullValue());
  }

  @Test
  void whenJobInProgressAndNullIntrospectVersionUnchanged_doNotCreateNewJob() {
    ignoreIntrospectorFailureLogs();
    ignoreJobCreatedAndDeletedLogs();

    getConfigurator().withIntrospectVersion(null);

    testSupport.addToPacket(DOMAIN_TOPOLOGY, createDomainConfig("cluster-1"));
    defineIntrospectionWithIntrospectVersionLabel(null);
    testSupport.doOnCreate(JOB, this::recordJob);

    testSupport.runSteps(JobHelper.createIntrospectionStartStep());

    assertThat(affectedJob, nullValue());
  }

  private void replaceFailedJobPodWithSuccess() {
    testSupport.deleteResources(createIntrospectorJobPod());
    testSupport.defineResources(createIntrospectorJobPod());
  }

  private void defineFailedIntrospectionWithImagePullError(String imagePullError) {
    testSupport.defineResources(asFailedJobWithBackoffLimitExceeded(createIntrospectorJob()));
    testSupport.defineResources(asFailedJobPodWithImagePullError(createIntrospectorJobPod(), imagePullError));
  }

  private void defineFailedIntrospectionWithDeadlineExceeded() {
    testSupport.defineResources(createIntrospectorJob());
    testSupport.defineResources(asFailedJobPodWithDeadlineExceeded(createIntrospectorJobPod()));
  }

  private V1Pod asFailedJobPodWithImagePullError(V1Pod introspectorJobPod, String imagePullError) {
    List<V1ContainerStatus> statuses = List.of(createImagePullContainerStatus(imagePullError));
    return introspectorJobPod.status(new V1PodStatus().containerStatuses(statuses));
  }

  private V1ContainerStatus createImagePullContainerStatus(String imagePullError) {
    return new V1ContainerStatus().state(
          new V1ContainerState().waiting(new V1ContainerStateWaiting().reason(imagePullError)));
  }

  private V1Pod asFailedJobPodWithDeadlineExceeded(V1Pod introspectorJobPod) {
    return introspectorJobPod.status(new V1PodStatus().reason("DeadlineExceeded"));
  }

  private V1Pod createIntrospectorJobPod() {
    Map<String, String> labels = new HashMap<>();
    labels.put(LabelConstants.JOBNAME_LABEL, JOB_NAME);
    return new V1Pod().metadata(new V1ObjectMeta().name(JOB_NAME).labels(labels).namespace(NS));
  }

  private V1Job asFailedJobWithBackoffLimitExceeded(V1Job job) {
    job.setStatus(new V1JobStatus().addConditionsItem(
        new V1JobCondition().status("True").type("Failed")
            .reason("BackoffLimitExceeded")));
    return job;
  }

  private V1Job asFailedJobWithDeadlineExceeded(V1Job job) {
    job.setStatus(new V1JobStatus().addConditionsItem(
        new V1JobCondition().status("True").type("Failed")
            .reason("DeadlineExceeded")));
    return job;
  }

  @Test
  void whenPreviousFailedJobWithImagePullBackoffErrorExistsAndMakeRightContinued_createNewJob() {
    ignoreIntrospectorFailureLogs();
    ignoreJobCreatedAndDeletedLogs();
    testSupport.addToPacket(DOMAIN_TOPOLOGY, createDomainConfig("cluster-1"));
    defineFailedIntrospectionWithImagePullError("ImagePullBackOff");
    testSupport.doOnCreate(JOB, this::recordJob);
    testSupport.doAfterCall(JOB, "deleteJob", this::replaceFailedJobPodWithSuccess);

    testSupport.runSteps(JobHelper.createIntrospectionStartStep());

    assertThat(affectedJob, notNullValue());
  }

  @Test
  void whenPreviousFailedJobWithDeadlineExceeded_terminateWithException() {
    ignoreIntrospectorFailureLogs();
    ignoreJobCreatedAndDeletedLogs();
    testSupport.addToPacket(DOMAIN_TOPOLOGY, createDomainConfig("cluster-1"));
    defineFailedIntrospectionWithDeadlineExceeded();
    testSupport.doOnCreate(JOB, this::recordJob);
    testSupport.doAfterCall(JOB, "deleteJob", this::replaceFailedJobPodWithSuccess);

    testSupport.runSteps(JobHelper.createIntrospectionStartStep());

    testSupport.verifyCompletionThrowable(JobWatcher.DeadlineExceededException.class);
  }

  private V1Job affectedJob;

  private void recordJob(Object job) {
    affectedJob = asCompletedJob((V1Job) job);
  }

  private V1Job asCompletedJob(V1Job job) {
    job.setStatus(new V1JobStatus().addConditionsItem(
        new V1JobCondition().status("True").type("Complete")));
    return job;
  }

  private V1Job asFailedJob(V1Job job) {
    job.setStatus(new V1JobStatus().addConditionsItem(
        new V1JobCondition().status("True").type("Failed")));
    return job;
  }

  @Test
  void whenPodCreationFailsDueToUnprocessableEntityFailure_reportInDomainStatus() {
    testSupport.failOnCreate(JOB, NS, new UnrecoverableErrorBuilderImpl()
        .withReason("FieldValueNotFound")
        .withMessage("Test this failure")
        .build());

    testSupport.runSteps(JobHelper.createIntrospectionStartStep());

    assertThat(getDomain(), hasStatus().withReason(KUBERNETES)
          .withMessageContaining("create", "job", NS, "Test this failure"));
  }

  @Test
  void whenPodCreationFailsDueToUnprocessableEntityFailure_generateFailedEvent() {
    testSupport.failOnCreate(JOB, NS, new UnrecoverableErrorBuilderImpl()
        .withReason("FieldValueNotFound")
        .withMessage("Test this failure")
        .build());

    testSupport.runSteps(JobHelper.createIntrospectionStartStep());

    assertThat(
        "Expected Event " + DOMAIN_FAILED + " expected with message not found",
        getExpectedEventMessage(testSupport, DOMAIN_FAILED),
        stringContainsInOrder("Domain", UID, "failed due to",
            getLocalizedString(KUBERNETES_EVENT_ERROR)));
  }

  DomainResource getDomain() {
    return testSupport.getResourceWithName(DOMAIN, UID);
  }

  @Test
  void whenPodCreationFailsDueToUnprocessableEntityFailure_abortFiber() {
    testSupport.failOnCreate(JOB, NS, new UnrecoverableErrorBuilderImpl()
        .withReason("FieldValueNotFound")
        .withMessage("Test this failure")
        .build());

    testSupport.runSteps(JobHelper.createIntrospectionStartStep(), terminalStep);

    assertThat(terminalStep.wasRun(), is(false));
  }

  @Test
  void whenIntrospectorJobIsRun_validatesDomainTopology() throws JsonProcessingException {
    establishWlsDomainWithCluster("cluster-2");

    testSupport.runSteps(JobHelper.createIntrospectionStartStep());

    assertThat(logRecords, containsInfo(getJobCreatedMessageKey()));
    assertThat(logRecords, containsFine(getJobDeletedMessageKey()));
    assertThat(logRecords, containsWarning(getNoClusterInDomainMessageKey()));
  }

  @Test
  void whenIntrospectorJobNotNeeded_validateDomainAgainstPreviousTopology() throws JsonProcessingException {
    // create WlsDomainConfig with "cluster-2" whereas domain spec contains "cluster-1"
    WlsDomainConfig wlsDomainConfig = createDomainConfig("cluster-2");
    IntrospectionTestUtils.defineIntrospectionTopology(testSupport, wlsDomainConfig);

    // make JobHelper.runIntrospector() return false
    cluster.getSpec().setServerStartPolicy(ServerStartPolicy.NEVER);
    domain.getSpec().setServerStartPolicy(ServerStartPolicy.NEVER);
    testSupport.addToPacket(DOMAIN_TOPOLOGY, wlsDomainConfig);

    testSupport.runSteps(JobHelper.createIntrospectionStartStep());

    assertThat(logRecords, containsWarning(NO_CLUSTER_IN_DOMAIN));
  }

  @Test
  void whenJobStatusContainsNoConditions_dontLogJobFailedAndInfosOnDelete() {
    testSupport.defineResources(createIntrospectorJob());
    IntrospectionTestUtils.defineIntrospectionPodLog(testSupport, SEVERE_MESSAGE);
    testSupport.addToPacket(DOMAIN_INTROSPECTOR_JOB, testSupport.getResourceWithName(JOB, getJobName()));

    testSupport.runSteps(JobHelper.deleteDomainIntrospectorJobStep(null));

    assertThat(logRecords, not(containsInfo(getJobFailedMessageKey())));
    assertThat(logRecords, not(containsFine(getJobFailedDetailMessageKey())));
    assertThat(logRecords, containsFine(getJobDeletedMessageKey()));
  }

  @Test
  void whenJobStatusHasFailedCondition_logJobInfosOnDelete() {
    testSupport.defineResources(asFailedJob(createIntrospectorJob()));
    testSupport.addToPacket(DOMAIN_INTROSPECTOR_JOB, testSupport.getResourceWithName(JOB, getJobName()));

    testSupport.runSteps(JobHelper.deleteDomainIntrospectorJobStep(null));

    assertThat(logRecords, containsInfo(getJobFailedMessageKey()));
    assertThat(logRecords, containsFine(getJobFailedDetailMessageKey()));
    assertThat(logRecords, containsFine(getJobDeletedMessageKey()));
  }

  private V1Job createIntrospectorJob() {
    return createIntrospectorJob(JOB_UID);
  }

  private V1Job createIntrospectorJob(String uid) {
    return new V1Job().metadata(createJobMetadata(uid)).status(new V1JobStatus())
        .spec(createJobSpecWithImage(LATEST_IMAGE));
  }

  private V1JobSpec createJobSpecWithImage(String image) {
    V1Container container = new V1Container().name(UID + "-introspector").image(image);
    V1PodSpec podSpec = new V1PodSpec().addContainersItem(container);
    V1PodTemplateSpec podTemplateSpec = new V1PodTemplateSpec().spec(podSpec);
    return new V1JobSpec().template(podTemplateSpec);
  }

  private V1Job createIntrospectorJobWithIntrospectVersionLabel(String introspectVersion) {
    V1Job job = createIntrospectorJob(UID);
    job.getMetadata().putLabelsItem(LabelConstants.INTROSPECTION_STATE_LABEL, introspectVersion);
    return job;
  }

  private V1ObjectMeta createJobMetadata(String uid) {
    return new V1ObjectMeta().name(getJobName()).namespace(NS).creationTimestamp(SystemClock.now()).uid(uid);
  }

  @Test
  void whenJobLogContainsSevereError_logJobInfosOnReadPogLog() {
    testSupport.defineResources(createIntrospectorJob());
    IntrospectionTestUtils.defineIntrospectionPodLog(testSupport, SEVERE_MESSAGE);
    testSupport.addToPacket(DOMAIN_INTROSPECTOR_JOB, testSupport.getResourceWithName(JOB, getJobName()));

    testSupport.runSteps(JobHelper.readDomainIntrospectorPodLog(null));

    assertThat(logRecords, containsInfo(getJobFailedMessageKey()));
    assertThat(logRecords, containsFine(getJobFailedDetailMessageKey()));
  }

  @Test
  void whenJobLogContainsSevereErrorAndRecheckIntervalNotExceeded_dontExecuteTerminalStep() {
    createFailedIntrospectionLog();

    testSupport.runSteps(JobHelper.readDomainIntrospectorPodLog(terminalStep));

    assertThat(terminalStep.wasRun(), is(false));
  }

  private void createFailedIntrospectionLog() {
    ignoreIntrospectorFailureLogs();
    testSupport.defineResources(createIntrospectorJob());
    IntrospectionTestUtils.defineIntrospectionPodLog(testSupport, SEVERE_MESSAGE);
    testSupport.addToPacket(DOMAIN_INTROSPECTOR_JOB, testSupport.getResourceWithName(JOB, getJobName()));
  }

  @Test
  void whenJobLogContainsSevereErrorAndRecheckIntervalExceeded_executeTerminalStep() {
    createFailedIntrospectionLog();

    SystemClockTestSupport.increment(getRecheckIntervalSeconds() + 1);
    testSupport.runSteps(JobHelper.readDomainIntrospectorPodLog(terminalStep));

    assertThat(terminalStep.wasRun(), is(true));
  }

  private int getRecheckIntervalSeconds() {
    return oracle.kubernetes.operator.tuning.TuningParameters.getInstance().getDomainPresenceRecheckIntervalSeconds();
  }

  @Test
  void whenJobCreateFailsWith409Error_JobIsCreated() {
    testSupport.addRetryStrategy(createStrictStub(OnConflictRetryStrategyStub.class));
    testSupport.failOnCreate(KubernetesTestSupport.JOB, NS, HTTP_CONFLICT);

    testSupport.runSteps(JobHelper.createIntrospectionStartStep());

    assertThat(testSupport.getPacket().get(DOMAIN_INTROSPECTOR_JOB), notNullValue());
    logRecords.clear();
  }

  private DomainResource getUpdatedDomain() {
    return testSupport.<DomainResource>getResources(DOMAIN).get(0);
  }

  @Test
  void whenJobLogContainsSevereErrorAndRetriesLeft_domainStatusHasExpectedMessage() {
    createIntrospectionLog(SEVERE_MESSAGE);

    testSupport.runSteps(JobHelper.readDomainIntrospectorPodLog(null));

    assertThat(getUpdatedDomain().getStatus().getMessage(), containsString(SEVERE_PROBLEM));
  }

  @Test
  void whenJobLogContainsFatalError_domainStatusHasExpectedMessage() {
    createIntrospectionLog(FATAL_MESSAGE);

    testSupport.runSteps(JobHelper.readDomainIntrospectorPodLog(null));

    assertThat(getUpdatedDomain().getStatus().getMessage(), containsString(FATAL_PROBLEM));
  }

  @Test
  void whenJobLogContainsSevereErrorAndRetryLimitReached_domainStatusHasAbortedCondition() {
    createIntrospectionLog(SEVERE_MESSAGE);
    getUpdatedDomain().getOrCreateStatus().addCondition(createFailedCondition("first failure"));
    SystemClockTestSupport.increment(getSecondsJustShortOfLimit());
    getUpdatedDomain().getOrCreateStatus().addCondition(createFailedCondition("first failure"));
    SystemClockTestSupport.increment(getUpdatedDomain().getFailureRetryIntervalSeconds());

    testSupport.runSteps(JobHelper.readDomainIntrospectorPodLog(null));

    assertThat(getUpdatedDomain(), hasCondition(FAILED).withReason(ABORTED));  // todo check updated status message
  }

  private DomainCondition createFailedCondition(String message) {
    return new DomainCondition(FAILED).withReason(INTROSPECTION).withMessage(message);
  }

  private long getSecondsJustShortOfLimit() {
    return getUpdatedDomain().getFailureRetryLimitMinutes() * 60L - 1;
  }

  private void createIntrospectionLog(String logMessage) {
    createIntrospectionLog(logMessage, true);
  }

  private void createIntrospectionLog(String logMessage, boolean ignoreLogMessages) {
    if (ignoreLogMessages) {
      consoleHandlerMemento.ignoreMessage(getJobFailedMessageKey());
      consoleHandlerMemento.ignoreMessage(getJobFailedDetailMessageKey());
    }
    testSupport.defineResources(createIntrospectorJob());
    IntrospectionTestUtils.defineIntrospectionPodLog(testSupport, logMessage);
    testSupport.addToPacket(DOMAIN_INTROSPECTOR_JOB, testSupport.getResourceWithName(JOB, getJobName()));
  }

  @Test
  void whenJobCreatedWithLegacyAuxiliaryImageDefined_hasAuxiliaryImageInitContainerVolumeAndMounts() {
    Map<String, Object> auxiliaryImageVolume = createAuxiliaryImageVolume(TEST_VOLUME_NAME,
            DEFAULT_LEGACY_AUXILIARY_IMAGE_MOUNT_PATH);
    Map<String, Object> auxiliaryImage =
        createAuxiliaryImage("wdt-image:v1", "IfNotPresent");

    convertDomainWithLegacyAuxImages(
            createLegacyDomainMap(
                    PodHelperTestBase.createDomainSpecMap(
                            Collections.singletonList(auxiliaryImageVolume),
                            List.of(auxiliaryImage))));

    V1Job job = runStepsAndGetJobs().get(0);
    List<V1Container> podTemplateInitContainers = getPodTemplateInitContainers(job);

    assertThat(
            podTemplateInitContainers,
            hasLegacyAuxiliaryImageInitContainer(AUXILIARY_IMAGE_INIT_CONTAINER_NAME_PREFIX + 1,
                    "wdt-image:v1",
                    "IfNotPresent", AUXILIARY_IMAGE_DEFAULT_INIT_CONTAINER_COMMAND));
    assertThat(getJobPodSpec(job).getVolumes(),
            hasItem(new V1Volume().name(getLegacyAuxiliaryImageVolumeName()).emptyDir(
                    new V1EmptyDirVolumeSource())));
    assertThat(getPodTemplateContainers(job).get(0).getVolumeMounts(),
            hasItem(new V1VolumeMount().name(getLegacyAuxiliaryImageVolumeName())
                    .mountPath(DEFAULT_LEGACY_AUXILIARY_IMAGE_MOUNT_PATH)));
  }

  @Test
  void whenJobCreatedWithLegacyAuxiliaryImageAndVolumeHavingAuxiliaryImagePath_hasVolumeMountWithAuxiliaryImagePath() {
    Map<String, Object> auxiliaryImageVolume = createAuxiliaryImageVolume(TEST_VOLUME_NAME, CUSTOM_MOUNT_PATH);
    Map<String, Object> auxiliaryImage =
        createAuxiliaryImage("wdt-image:v1", "IfNotPresent");

    convertDomainWithLegacyAuxImages(
            createLegacyDomainMap(
                    PodHelperTestBase.createDomainSpecMap(
                            Collections.singletonList(auxiliaryImageVolume),
                            List.of(auxiliaryImage))));

    List<V1Job> jobs = runStepsAndGetJobs();
    assertThat(getCreatedPodSpecContainers(jobs).get(0).getVolumeMounts(),
            hasItem(new V1VolumeMount().name(getLegacyAuxiliaryImageVolumeName())
                    .mountPath(CUSTOM_MOUNT_PATH)));
  }

  @Test
  void whenJobCreatedWithLegacyAuxiliaryImageVolumeWithMedium_createdJobPodsHasVolumeWithSpecifiedMedium() {
    Map<String, Object> auxiliaryImageVolume = createAuxiliaryImageVolume(TEST_VOLUME_NAME,
            DEFAULT_LEGACY_AUXILIARY_IMAGE_MOUNT_PATH, null, "Memory");
    Map<String, Object> auxiliaryImage =
        createAuxiliaryImage("wdt-image:v1", "IfNotPresent");

    convertDomainWithLegacyAuxImages(
            createLegacyDomainMap(
                    PodHelperTestBase.createDomainSpecMap(
                            Collections.singletonList(auxiliaryImageVolume),
                            List.of(auxiliaryImage))));
    V1Job job = runStepsAndGetJobs().get(0);
    assertThat(getJobPodSpec(job).getVolumes(),
            hasItem(new V1Volume().name(getLegacyAuxiliaryImageVolumeName()).emptyDir(
                    new V1EmptyDirVolumeSource().medium("Memory"))));
  }


  @Test
  void whenJobCreatedWithLegacyAuxiliaryImageVolumeWithSizeLimit_createdJobPodsHasVolumeWithSpecifiedSizeLimit() {
    Map<String, Object> auxiliaryImageVolume = createAuxiliaryImageVolume(TEST_VOLUME_NAME, CUSTOM_MOUNT_PATH,
            "100G", null);
    Map<String, Object> auxiliaryImage =
        createAuxiliaryImage("wdt-image:v1", "IfNotPresent");

    convertDomainWithLegacyAuxImages(
            createLegacyDomainMap(
                    PodHelperTestBase.createDomainSpecMap(
                            Collections.singletonList(auxiliaryImageVolume),
                            List.of(auxiliaryImage))));

    V1Job job = runStepsAndGetJobs().get(0);
    assertThat(getJobPodSpec(job).getVolumes(),
            hasItem(new V1Volume().name(getLegacyAuxiliaryImageVolumeName()).emptyDir(
                    new V1EmptyDirVolumeSource().sizeLimit(Quantity.fromString("100G")))));
  }

  @Test
  void whenJobCreatedWithLegacyAuxiliaryImageWithImagePullPolicy_createJobPodHasImagePullPolicy() {
    Map<String, Object> auxiliaryImageVolume = createAuxiliaryImageVolume(TEST_VOLUME_NAME,
            DEFAULT_LEGACY_AUXILIARY_IMAGE_MOUNT_PATH);
    Map<String, Object> auxiliaryImage = createAuxiliaryImage("wdt-image:v1", "Always");

    convertDomainWithLegacyAuxImages(
            createLegacyDomainMap(
                    PodHelperTestBase.createDomainSpecMap(
                            Collections.singletonList(auxiliaryImageVolume),
                            List.of(auxiliaryImage))));
    V1Job job = runStepsAndGetJobs().get(0);
    assertThat(getPodTemplateInitContainers(job),
        hasLegacyAuxiliaryImageInitContainer(AUXILIARY_IMAGE_INIT_CONTAINER_NAME_PREFIX + 1,
           "wdt-image:v1", "Always", AUXILIARY_IMAGE_DEFAULT_INIT_CONTAINER_COMMAND));
  }

  @Test
  void whenJobCreatedWithLegacyAuxiliaryImageWithResourceRequirements_createJobPodHasResourceRequirements() {
    Map<String, Object> auxiliaryImageVolume = createAuxiliaryImageVolume(TEST_VOLUME_NAME,
        DEFAULT_LEGACY_AUXILIARY_IMAGE_MOUNT_PATH);
    Map<String, Object> auxiliaryImage = createAuxiliaryImage("wdt-image:v1", "Always");

    convertDomainWithLegacyAuxImages(
        createLegacyDomainMap(
            PodHelperTestBase.createDomainSpecMapWithResources(
                Collections.singletonList(auxiliaryImageVolume),
                List.of(auxiliaryImage), createResources())));
    V1Job job = runStepsAndGetJobs().get(0);
    assertThat(getPodTemplateInitContainers(job),
        hasLegacyAuxiliaryImageInitContainer(AUXILIARY_IMAGE_INIT_CONTAINER_NAME_PREFIX + 1,
            "wdt-image:v1", "Always", AUXILIARY_IMAGE_DEFAULT_INIT_CONTAINER_COMMAND,
            new V1ResourceRequirements().limits(Collections.singletonMap("cpu", new Quantity("250m")))
                .requests(Collections.singletonMap("memory", new Quantity("1Gi")))));
  }

  void convertDomainWithLegacyAuxImages(Map<String, Object> map) {
    try {
      testSupport.addDomainPresenceInfo(new DomainPresenceInfo(
              readDomain(schemaConversionUtils.convertDomainSchema(new Yaml().dump(map)))));
    } catch (IOException ioe) {
      ioe.printStackTrace();
    }
  }

  @Test
  void whenJobCreatedWithLegacyAuxiliaryImageAndCustomCommand_createJobPodsWithInitContainerHavingCustomCommand() {
    Map<String, Object> auxiliaryImageVolume = createAuxiliaryImageVolume(TEST_VOLUME_NAME,
            DEFAULT_LEGACY_AUXILIARY_IMAGE_MOUNT_PATH);
    Map<String, Object> auxiliaryImage =
        createAuxiliaryImage("wdt-image:v1", "IfNotPresent",
            CUSTOM_COMMAND_SCRIPT);

    convertDomainWithLegacyAuxImages(
            createLegacyDomainMap(
                    PodHelperTestBase.createDomainSpecMap(
                            Collections.singletonList(auxiliaryImageVolume),
                            List.of(auxiliaryImage))));
    V1Job job = runStepsAndGetJobs().get(0);
    assertThat(getPodTemplateInitContainers(job),
        hasLegacyAuxiliaryImageInitContainer(AUXILIARY_IMAGE_INIT_CONTAINER_NAME_PREFIX + 1,
            "wdt-image:v1", "IfNotPresent", CUSTOM_COMMAND_SCRIPT));
  }

  @Test
  void whenJobCreatedWithMultipleLegacyAuxiliaryImages_createdJobPodsHasMultipleInitContainers() {
    Map<String, Object> auxiliaryImageVolume = createAuxiliaryImageVolume(TEST_VOLUME_NAME,
            DEFAULT_LEGACY_AUXILIARY_IMAGE_MOUNT_PATH);
    Map<String, Object> auxiliaryImage =
        createAuxiliaryImage("wdt-image1:v1", "IfNotPresent");
    Map<String, Object> auxiliaryImage2 =
        createAuxiliaryImage("wdt-image2:v1", "IfNotPresent");

    convertDomainWithLegacyAuxImages(
            createLegacyDomainMap(
                    PodHelperTestBase.createDomainSpecMap(
                            Collections.singletonList(auxiliaryImageVolume),
                            Arrays.asList(auxiliaryImage, auxiliaryImage2))));
    V1Job job = runStepsAndGetJobs().get(0);
    assertThat(getPodTemplateInitContainers(job),
        org.hamcrest.Matchers.allOf(
            hasLegacyAuxiliaryImageInitContainer(AUXILIARY_IMAGE_INIT_CONTAINER_NAME_PREFIX + 1,
                "wdt-image1:v1", "IfNotPresent",
                AUXILIARY_IMAGE_DEFAULT_INIT_CONTAINER_COMMAND),
            hasLegacyAuxiliaryImageInitContainer(AUXILIARY_IMAGE_INIT_CONTAINER_NAME_PREFIX + 2,
                "wdt-image2:v1",
                "IfNotPresent", AUXILIARY_IMAGE_DEFAULT_INIT_CONTAINER_COMMAND)));
    assertThat(getPodTemplateContainers(job).get(0).getVolumeMounts(), hasSize(4));
    assertThat(getPodTemplateContainers(job).get(0).getVolumeMounts(),
            hasItem(new V1VolumeMount().name(getLegacyAuxiliaryImageVolumeName())
                    .mountPath(DEFAULT_LEGACY_AUXILIARY_IMAGE_MOUNT_PATH)));
  }

  private String getDomainHome() {
    return "/shared/domains/" + UID;
  }
}

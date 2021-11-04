// Copyright (c) 2018, 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helpers;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.stream.IntStream;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.meterware.simplestub.Memento;
import io.kubernetes.client.custom.Quantity;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.models.V1ConfigMap;
import io.kubernetes.client.openapi.models.V1Container;
import io.kubernetes.client.openapi.models.V1EmptyDirVolumeSource;
import io.kubernetes.client.openapi.models.V1Job;
import io.kubernetes.client.openapi.models.V1JobCondition;
import io.kubernetes.client.openapi.models.V1JobStatus;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1PodSpec;
import io.kubernetes.client.openapi.models.V1SecretReference;
import io.kubernetes.client.openapi.models.V1Volume;
import io.kubernetes.client.openapi.models.V1VolumeMount;
import oracle.kubernetes.operator.JobAwaiterStepFactory;
import oracle.kubernetes.operator.TuningParameters;
import oracle.kubernetes.operator.calls.unprocessable.UnrecoverableErrorBuilderImpl;
import oracle.kubernetes.operator.rest.ScanCacheStub;
import oracle.kubernetes.operator.wlsconfig.WlsClusterConfig;
import oracle.kubernetes.operator.wlsconfig.WlsDomainConfig;
import oracle.kubernetes.operator.wlsconfig.WlsServerConfig;
import oracle.kubernetes.operator.work.FiberTestSupport;
import oracle.kubernetes.operator.work.Step;
import oracle.kubernetes.operator.work.TerminalStep;
import oracle.kubernetes.utils.SystemClock;
import oracle.kubernetes.utils.SystemClockTestSupport;
import oracle.kubernetes.utils.TestUtils;
import oracle.kubernetes.weblogic.domain.DomainConfigurator;
import oracle.kubernetes.weblogic.domain.DomainConfiguratorFactory;
import oracle.kubernetes.weblogic.domain.model.AuxiliaryImage;
import oracle.kubernetes.weblogic.domain.model.AuxiliaryImageVolume;
import oracle.kubernetes.weblogic.domain.model.Cluster;
import oracle.kubernetes.weblogic.domain.model.Configuration;
import oracle.kubernetes.weblogic.domain.model.ConfigurationConstants;
import oracle.kubernetes.weblogic.domain.model.Domain;
import oracle.kubernetes.weblogic.domain.model.DomainSpec;
import oracle.kubernetes.weblogic.domain.model.DomainStatus;
import oracle.kubernetes.weblogic.domain.model.Model;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.meterware.simplestub.Stub.createStrictStub;
import static java.net.HttpURLConnection.HTTP_CONFLICT;
import static oracle.kubernetes.operator.DomainConditionMatcher.hasCondition;
import static oracle.kubernetes.operator.DomainFailureReason.Kubernetes;
import static oracle.kubernetes.operator.DomainProcessorTestSetup.NS;
import static oracle.kubernetes.operator.DomainProcessorTestSetup.UID;
import static oracle.kubernetes.operator.DomainStatusMatcher.hasStatus;
import static oracle.kubernetes.operator.KubernetesConstants.HTTP_FORBIDDEN;
import static oracle.kubernetes.operator.KubernetesConstants.HTTP_INTERNAL_ERROR;
import static oracle.kubernetes.operator.ProcessingConstants.DOMAIN_INTROSPECTOR_JOB;
import static oracle.kubernetes.operator.ProcessingConstants.DOMAIN_TOPOLOGY;
import static oracle.kubernetes.operator.ProcessingConstants.JOBWATCHER_COMPONENT_NAME;
import static oracle.kubernetes.operator.ProcessingConstants.JOB_POD_NAME;
import static oracle.kubernetes.operator.helpers.KubernetesTestSupport.DOMAIN;
import static oracle.kubernetes.operator.helpers.KubernetesTestSupport.JOB;
import static oracle.kubernetes.operator.helpers.Matchers.hasEnvVar;
import static oracle.kubernetes.operator.helpers.PodHelperTestBase.CUSTOM_COMMAND_SCRIPT;
import static oracle.kubernetes.operator.helpers.PodHelperTestBase.CUSTOM_MOUNT_PATH;
import static oracle.kubernetes.operator.logging.MessageKeys.INTROSPECTOR_JOB_FAILED;
import static oracle.kubernetes.operator.logging.MessageKeys.INTROSPECTOR_JOB_FAILED_DETAIL;
import static oracle.kubernetes.operator.logging.MessageKeys.JOB_CREATED;
import static oracle.kubernetes.operator.logging.MessageKeys.JOB_DELETED;
import static oracle.kubernetes.operator.logging.MessageKeys.NO_CLUSTER_IN_DOMAIN;
import static oracle.kubernetes.utils.LogMatcher.containsFine;
import static oracle.kubernetes.utils.LogMatcher.containsInfo;
import static oracle.kubernetes.utils.LogMatcher.containsWarning;
import static oracle.kubernetes.weblogic.domain.model.AuxiliaryImage.AUXILIARY_IMAGE_DEFAULT_INIT_CONTAINER_COMMAND;
import static oracle.kubernetes.weblogic.domain.model.AuxiliaryImage.AUXILIARY_IMAGE_INIT_CONTAINER_NAME_PREFIX;
import static oracle.kubernetes.weblogic.domain.model.AuxiliaryImage.AUXILIARY_IMAGE_VOLUME_NAME_PREFIX;
import static oracle.kubernetes.weblogic.domain.model.AuxiliaryImageVolume.DEFAULT_AUXILIARY_IMAGE_PATH;
import static oracle.kubernetes.weblogic.domain.model.ConfigurationConstants.START_NEVER;
import static oracle.kubernetes.weblogic.domain.model.DomainConditionType.Failed;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.stringContainsInOrder;
import static org.hamcrest.core.AllOf.allOf;
import static org.hamcrest.junit.MatcherAssert.assertThat;

@SuppressWarnings({"SameParameterValue"})
class DomainIntrospectorJobTest {
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
  private static final String INFO_MESSAGE = "@[INFO] just letting you know";
  public static final String TEST_VOLUME_NAME = "test";
  private static final String JOB_UID = "FAILED_JOB";

  private final TerminalStep terminalStep = new TerminalStep();
  private final Domain domain = createDomain();
  private final DomainPresenceInfo domainPresenceInfo = createDomainPresenceInfo(domain);
  private final KubernetesTestSupport testSupport = new KubernetesTestSupport();
  private final List<Memento> mementos = new ArrayList<>();
  private final List<LogRecord> logRecords = new ArrayList<>();
  private final DomainConfigurator configurator = DomainConfiguratorFactory.forDomain(domain);
  private final RetryStrategyStub retryStrategy = createStrictStub(RetryStrategyStub.class);
  private final String jobPodName = LegalNames.toJobIntrospectorName(UID);
  private TestUtils.ConsoleHandlerMemento consoleHandlerMemento;
  private boolean jobDeleted;

  public DomainIntrospectorJobTest() {
  }

  private static String getJobName() {
    return LegalNames.toJobIntrospectorName(UID);
  }

  @BeforeEach
  public void setUp() throws Exception {
    mementos.add(
        consoleHandlerMemento = TestUtils.silenceOperatorLogger()
            .collectLogMessages(logRecords, getMessageKeys())
            .withLogLevel(Level.FINE)
            .ignoringLoggedExceptions(ApiException.class));
    mementos.add(TuningParametersStub.install());
    mementos.add(testSupport.install());
    mementos.add(ScanCacheStub.install());
    mementos.add(SystemClockTestSupport.installClock());
    testSupport.addToPacket(JOB_POD_NAME, jobPodName);
    testSupport.addDomainPresenceInfo(domainPresenceInfo);
    testSupport.defineResources(domain);
    testSupport.addComponent(JOBWATCHER_COMPONENT_NAME, JobAwaiterStepFactory.class, new JobAwaiterStepFactoryStub());
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

  private V1PodSpec getJobPodSpec(V1Job job) {
    return Objects.requireNonNull(job.getSpec()).getTemplate().getSpec();
  }

  private List<V1Job> runStepsAndGetJobs() {
    testSupport.runSteps(getStepFactory(), terminalStep);
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

    testSupport.runSteps(getStepFactory(), terminalStep);

    assertThat(logRecords, containsInfo(getJobCreatedMessageKey()));
    assertThat(logRecords, containsFine(getJobDeletedMessageKey()));
  }

  private void establishWlsDomainWithCluster(String s) throws JsonProcessingException {
    IntrospectionTestUtils.defineResources(testSupport, createDomainConfig(s));
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
    return JobHelper::createIntrospectionStartStep;
  }

  @Test
  void whenNoJob_onInternalError() {
    testSupport.addRetryStrategy(retryStrategy);
    testSupport.failOnCreate(KubernetesTestSupport.JOB, NS, HTTP_INTERNAL_ERROR);

    testSupport.runSteps(getStepFactory(), terminalStep);

    assertThat(getDomain(), hasStatus().withReason(Kubernetes)
          .withMessageContaining("create", "job", NS, "failure reported in test"));
  }

  @Test
  void whenJobCreated_jobNameContainsDefaultSuffix() {
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
  void whenJobCreatedWithCustomIntrospectorJobnameSuffix_jobNameContainsConfiguredSuffix() {
    TuningParameters.getInstance().put(LegalNames.INTROSPECTOR_JOB_NAME_SUFFIX_PARAM, "-introspector-job");
    testSupport.runSteps(getStepFactory(), terminalStep);
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
            .withAuxiliaryImageVolumes(getAuxiliaryImageVolume(DEFAULT_AUXILIARY_IMAGE_PATH))
            .withAuxiliaryImages(Collections.singletonList(getAuxiliaryImage("wdt-image:v1")));

    V1Job job = runStepsAndGetJobs().get(0);
    List<V1Container> podTemplateInitContainers = getPodTemplateInitContainers(job);

    assertThat(
            podTemplateInitContainers,
            Matchers.hasAuxiliaryImageInitContainer(AUXILIARY_IMAGE_INIT_CONTAINER_NAME_PREFIX + 1,
                "wdt-image:v1",
                "IfNotPresent", AUXILIARY_IMAGE_DEFAULT_INIT_CONTAINER_COMMAND));
    assertThat(getJobPodSpec(job).getVolumes(),
            hasItem(new V1Volume().name(AUXILIARY_IMAGE_VOLUME_NAME_PREFIX + TEST_VOLUME_NAME).emptyDir(
                    new V1EmptyDirVolumeSource())));
    assertThat(getPodTemplateContainers(job).get(0).getVolumeMounts(),
            hasItem(new V1VolumeMount().name(AUXILIARY_IMAGE_VOLUME_NAME_PREFIX + TEST_VOLUME_NAME)
                    .mountPath(DEFAULT_AUXILIARY_IMAGE_PATH)));
  }

  @NotNull
  List<AuxiliaryImageVolume> getAuxiliaryImageVolume(String mountPath) {
    return Collections.singletonList(new AuxiliaryImageVolume().mountPath(mountPath).name(TEST_VOLUME_NAME));
  }

  private List<V1Container> getCreatedPodSpecContainers(List<V1Job> jobs) {
    return getJobPodSpec(jobs.get(0)).getContainers();
  }

  @NotNull
  private List<AuxiliaryImage> getAuxiliaryImages(String...images) {
    List<AuxiliaryImage> auxiliaryImageList = new ArrayList<>();
    Arrays.stream(images).forEach(image -> auxiliaryImageList.add(new AuxiliaryImage().image(image)
            .volume(TEST_VOLUME_NAME)));
    return auxiliaryImageList;
  }

  @NotNull
  public static AuxiliaryImage getAuxiliaryImage(String image) {
    return new AuxiliaryImage().image(image).volume(TEST_VOLUME_NAME);
  }

  @Test
  void whenJobCreatedWithAuxiliaryImageAndVolumeHavingAuxiliaryImagePath_hasVolumeMountWithAuxiliaryImagePath() {
    DomainConfiguratorFactory.forDomain(domain)
            .withAuxiliaryImageVolumes(getAuxiliaryImageVolume(CUSTOM_MOUNT_PATH))
            .withAuxiliaryImages(getAuxiliaryImages("wdt-image:v1"));

    List<V1Job> jobs = runStepsAndGetJobs();
    assertThat(getCreatedPodSpecContainers(jobs).get(0).getVolumeMounts(),
            hasItem(new V1VolumeMount().name(AUXILIARY_IMAGE_VOLUME_NAME_PREFIX + TEST_VOLUME_NAME)
                    .mountPath(CUSTOM_MOUNT_PATH)));
  }

  @Test
  void whenJobCreatedWithAuxiliaryImageVolumeWithMedium_createdJobPodsHasVolumeWithSpecifiedMedium() {
    getConfigurator()
            .withAuxiliaryImageVolumes(Collections.singletonList(
                    new AuxiliaryImageVolume().name(TEST_VOLUME_NAME).medium("Memory")))
            .withAuxiliaryImages(getAuxiliaryImages("wdt-image:v1"));

    V1Job job = runStepsAndGetJobs().get(0);
    assertThat(getJobPodSpec(job).getVolumes(),
            hasItem(new V1Volume().name(AUXILIARY_IMAGE_VOLUME_NAME_PREFIX + TEST_VOLUME_NAME).emptyDir(
                    new V1EmptyDirVolumeSource().medium("Memory"))));
  }


  @Test
  void whenJobCreatedWithAuxiliaryImageVolumeWithSizeLimit_createdJobPodsHasVolumeWithSpecifiedSizeLimit() {
    getConfigurator()
            .withAuxiliaryImageVolumes(Collections.singletonList(
                    new AuxiliaryImageVolume().name(TEST_VOLUME_NAME).sizeLimit("100G")))
            .withAuxiliaryImages(getAuxiliaryImages());

    V1Job job = runStepsAndGetJobs().get(0);
    assertThat(getJobPodSpec(job).getVolumes(),
            hasItem(new V1Volume().name(AUXILIARY_IMAGE_VOLUME_NAME_PREFIX + TEST_VOLUME_NAME).emptyDir(
                    new V1EmptyDirVolumeSource().sizeLimit(Quantity.fromString("100G")))));
  }

  @Test
  void whenJobCreatedWithAuxiliaryImageWithImagePullPolicy_createJobPodHasImagePullPolicy() {
    getConfigurator()
            .withAuxiliaryImageVolumes(getAuxiliaryImageVolume(DEFAULT_AUXILIARY_IMAGE_PATH))
            .withAuxiliaryImages(Collections.singletonList(getAuxiliaryImage("wdt-image:v1")
                    .imagePullPolicy("ALWAYS")));

    V1Job job = runStepsAndGetJobs().get(0);
    assertThat(getPodTemplateInitContainers(job),
                Matchers.hasAuxiliaryImageInitContainer(AUXILIARY_IMAGE_INIT_CONTAINER_NAME_PREFIX + 1,
                    "wdt-image:v1", "ALWAYS", AUXILIARY_IMAGE_DEFAULT_INIT_CONTAINER_COMMAND));
  }

  @Test
  void whenJobCreatedWithAuxiliaryImageAndCustomCommand_createJobPodsWithInitContainerHavingCustomCommand() {
    getConfigurator()
            .withAuxiliaryImageVolumes(getAuxiliaryImageVolume(DEFAULT_AUXILIARY_IMAGE_PATH))
            .withAuxiliaryImages(Collections.singletonList(getAuxiliaryImage("wdt-image:v1")
                    .command(CUSTOM_COMMAND_SCRIPT)));

    V1Job job = runStepsAndGetJobs().get(0);
    assertThat(getPodTemplateInitContainers(job),
                Matchers.hasAuxiliaryImageInitContainer(AUXILIARY_IMAGE_INIT_CONTAINER_NAME_PREFIX + 1,
                    "wdt-image:v1", "IfNotPresent", CUSTOM_COMMAND_SCRIPT));
  }

  @Test
  void whenJobCreatedWithMultipleAuxiliaryImages_createdJobPodsHasMultipleInitContainers() {
    getConfigurator()
            .withAuxiliaryImageVolumes(getAuxiliaryImageVolume(DEFAULT_AUXILIARY_IMAGE_PATH))
            .withAuxiliaryImages(getAuxiliaryImages("wdt-image1:v1", "wdt-image2:v1"));

    V1Job job = runStepsAndGetJobs().get(0);
    assertThat(getPodTemplateInitContainers(job),
            org.hamcrest.Matchers.allOf(
                Matchers.hasAuxiliaryImageInitContainer(AUXILIARY_IMAGE_INIT_CONTAINER_NAME_PREFIX + 1,
                    "wdt-image1:v1", "IfNotPresent", AUXILIARY_IMAGE_DEFAULT_INIT_CONTAINER_COMMAND),
                    Matchers.hasAuxiliaryImageInitContainer(AUXILIARY_IMAGE_INIT_CONTAINER_NAME_PREFIX + 2,
                        "wdt-image2:v1",
                        "IfNotPresent", AUXILIARY_IMAGE_DEFAULT_INIT_CONTAINER_COMMAND)));
    assertThat(getPodTemplateContainers(job).get(0).getVolumeMounts(), hasSize(7));
    assertThat(getPodTemplateContainers(job).get(0).getVolumeMounts(),
            hasItem(new V1VolumeMount().name(AUXILIARY_IMAGE_VOLUME_NAME_PREFIX + TEST_VOLUME_NAME)
                    .mountPath(DEFAULT_AUXILIARY_IMAGE_PATH)));
  }

  @Test
  void whenNewSuccessfulJobExists_readOldPodLogWithoutCreatingNewJob() {
    consoleHandlerMemento.ignoreMessage(getJobDeletedMessageKey());
    testSupport.addToPacket(DOMAIN_TOPOLOGY, createDomainConfig("cluster-1"));
    defineCompletedIntrospection();
    testSupport.failOnCreate(JOB, NS, HTTP_FORBIDDEN);

    testSupport.runSteps(JobHelper.createIntrospectionStartStep(null));

    assertThat(getUpdatedDomain(), not(hasCondition(Failed)));
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
    testSupport.runSteps(JobHelper.createIntrospectionStartStep(terminalStep));

    assertThat(getUpdatedDomain(), hasCondition(Failed));
  }

  private void ignoreIntrospectorFailureLogs() {
    consoleHandlerMemento.ignoreMessage(getJobFailedMessageKey());
    consoleHandlerMemento.ignoreMessage(getJobFailedDetailMessageKey());
  }

  private void defineFailedIntrospection() {
    testSupport.defineResources(asFailedJob(createIntrospectorJob()));
    testSupport.definePodLog(LegalNames.toJobIntrospectorName(UID), NS, SEVERE_MESSAGE);
  }

  @Test
  void whenPreviousFailedJobExists_readOnlyNewPodLog() throws Exception {
    ignoreJobCreatedAndDeletedLogs();
    ignoreIntrospectorFailureLogs();
    testSupport.addToPacket(DOMAIN_TOPOLOGY, createDomainConfig("cluster-1"));
    definePreviousFailedIntrospectionWithoutPodLog();
    testSupport.doAfterCall(JOB, "deleteJob", this::defineNewIntrospectionResult);

    testSupport.runSteps(JobHelper.createIntrospectionStartStep(null));

    testSupport.throwOnCompletionFailure();
  }

  private void defineNewIntrospectionResult() {
    testSupport.getPacket().put(JOB_POD_NAME, jobPodName);
    testSupport.definePodLog(jobPodName, NS, SEVERE_MESSAGE);
  }

  private void ignoreJobCreatedAndDeletedLogs() {
    consoleHandlerMemento.ignoreMessage(getJobCreatedMessageKey());
    consoleHandlerMemento.ignoreMessage(getJobDeletedMessageKey());
  }

  private void definePreviousFailedIntrospection() {
    defineFailedIntrospection();
    getDomain().getOrCreateStatus().incrementIntrospectJobFailureCount(JOB_UID);
  }

  private void definePreviousFailedIntrospectionWithoutPodLog() {
    testSupport.defineResources(asFailedJob(createIntrospectorJob()));
    getDomain().getOrCreateStatus().incrementIntrospectJobFailureCount(JOB_UID);
  }

  @Test
  void whenPreviousFailedJobExists_deleteIt() {
    ignoreJobCreatedAndDeletedLogs();
    ignoreIntrospectorFailureLogs();
    testSupport.addToPacket(DOMAIN_TOPOLOGY, createDomainConfig("cluster-1"));
    definePreviousFailedIntrospection();
    testSupport.doAfterCall(JOB, "deleteJob", this::recordJobDeleted);

    testSupport.runSteps(JobHelper.createIntrospectionStartStep(null));

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

    testSupport.runSteps(JobHelper.createIntrospectionStartStep(null));

    assertThat(affectedJob, notNullValue());
  }

  private V1Job affectedJob;

  private void recordJob(Object job) {
    affectedJob = asCompletedJob((V1Job) job);
  }

  private V1Job asCompletedJob(V1Job job) {
    job.setStatus(new V1JobStatus().addConditionsItem(new V1JobCondition().status("True").type("Complete")));
    return job;
  }

  private V1Job asFailedJob(V1Job job) {
    job.setStatus(new V1JobStatus().addConditionsItem(new V1JobCondition().status("True").type("Failed")));
    return job;
  }

  @Test
  void whenPodCreationFailsDueToUnprocessableEntityFailure_reportInDomainStatus() {
    testSupport.failOnCreate(JOB, NS, new UnrecoverableErrorBuilderImpl()
        .withReason("FieldValueNotFound")
        .withMessage("Test this failure")
        .build());

    testSupport.runSteps(getStepFactory(), terminalStep);

    assertThat(getDomain(), hasStatus().withReason(Kubernetes)
          .withMessageContaining("create", "job", NS, "Test this failure"));
  }

  Domain getDomain() {
    return testSupport.getResourceWithName(DOMAIN, UID);
  }

  @Test
  void whenPodCreationFailsDueToUnprocessableEntityFailure_abortFiber() {
    testSupport.failOnCreate(JOB, NS, new UnrecoverableErrorBuilderImpl()
        .withReason("FieldValueNotFound")
        .withMessage("Test this failure")
        .build());

    testSupport.runSteps(getStepFactory(), terminalStep);

    assertThat(terminalStep.wasRun(), is(false));
  }

  @Test
  void whenIntrospectorJobIsRun_validatesDomainTopology() throws JsonProcessingException {
    establishWlsDomainWithCluster("cluster-2");

    testSupport.runSteps(getStepFactory(), terminalStep);

    assertThat(logRecords, containsInfo(getJobCreatedMessageKey()));
    assertThat(logRecords, containsFine(getJobDeletedMessageKey()));
    assertThat(logRecords, containsWarning(getNoClusterInDomainMessageKey()));
  }

  @Test
  void whenIntrospectorJobNotNeeded_doesNotValidateDomainTopology() throws JsonProcessingException {
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
  void whenJobLogContainsSevereError_logJobInfosOnDelete() {
    testSupport.defineResources(createIntrospectorJob());
    IntrospectionTestUtils.defineResources(testSupport, SEVERE_MESSAGE);
    testSupport.addToPacket(DOMAIN_INTROSPECTOR_JOB, testSupport.getResourceWithName(JOB, getJobName()));

    testSupport.runSteps(JobHelper.deleteDomainIntrospectorJobStep(terminalStep));

    assertThat(logRecords, containsInfo(getJobFailedMessageKey()));
    assertThat(logRecords, containsFine(getJobFailedDetailMessageKey()));
    assertThat(logRecords, containsFine(getJobDeletedMessageKey()));
  }

  private V1Job createIntrospectorJob() {
    return new V1Job().metadata(createJobMetadata()).status(new V1JobStatus());
  }

  private V1ObjectMeta createJobMetadata() {
    return new V1ObjectMeta().name(getJobName()).namespace(NS).creationTimestamp(SystemClock.now()).uid(JOB_UID);
  }

  @Test
  void whenJobLogContainsSevereError_logJobInfosOnReadPogLog() {
    testSupport.defineResources(createIntrospectorJob());
    IntrospectionTestUtils.defineResources(testSupport, SEVERE_MESSAGE);
    testSupport.addToPacket(DOMAIN_INTROSPECTOR_JOB, testSupport.getResourceWithName(JOB, getJobName()));

    testSupport.runSteps(JobHelper.readDomainIntrospectorPodLog(terminalStep));

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
    IntrospectionTestUtils.defineResources(testSupport, SEVERE_MESSAGE);
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
    return TuningParameters.getInstance().getMainTuning().domainPresenceRecheckIntervalSeconds;
  }

  @Test
  void whenJobCreateFailsWith409Error_JobIsCreated() {
    testSupport.addRetryStrategy(createStrictStub(OnConflictRetryStrategyStub.class));
    testSupport.failOnCreate(KubernetesTestSupport.JOB, NS, HTTP_CONFLICT);

    testSupport.runSteps(getStepFactory(), terminalStep);

    assertThat(testSupport.getPacket().get(DOMAIN_INTROSPECTOR_JOB), notNullValue());
    logRecords.clear();
  }

  @Test
  void whenJobLogContainsSevereError_incrementFailureCount() {
    createFailedIntrospectionLog();

    testSupport.runSteps(JobHelper.readDomainIntrospectorPodLog(terminalStep));

    assertThat(getUpdatedDomain().getStatus().getIntrospectJobFailureCount(), equalTo(1));
  }

  private Domain getUpdatedDomain() {
    return testSupport.<Domain>getResources(DOMAIN).get(0);
  }

  @Test
  void whenJobLogContainsSevereError_recordJobUid() {
    createFailedIntrospectionLog();

    testSupport.runSteps(JobHelper.readDomainIntrospectorPodLog(terminalStep));

    assertThat(getUpdatedDomain().getStatus().getFailedIntrospectionUid(), equalTo(JOB_UID));
  }

  @Test
  void whenJobLogContainsSevereErrorAndStatusHasCurrentJobUID_dontIncrementFailureCount() {
    createFailedIntrospectionLog();
    getUpdatedDomain().setStatus(createDomainStatusAfterOneFailure(JOB_UID));

    testSupport.runSteps(JobHelper.readDomainIntrospectorPodLog(terminalStep));

    assertThat(getUpdatedDomain().getStatus().getIntrospectJobFailureCount(), equalTo(1));
  }

  private DomainStatus createDomainStatusAfterOneFailure(String jobUid) {
    final DomainStatus status = new DomainStatus();
    status.incrementIntrospectJobFailureCount(jobUid);
    return status;
  }

  @Test
  void whenJobLogContainsSevereErrorAndStatusHasDifferentJobUID_incrementFailureCount() {
    createFailedIntrospectionLog();
    getUpdatedDomain().setStatus(createDomainStatusAfterOneFailure("zzzz"));

    testSupport.runSteps(JobHelper.readDomainIntrospectorPodLog(terminalStep));

    assertThat(getUpdatedDomain().getStatus().getIntrospectJobFailureCount(), equalTo(2));
  }


  // create job
  // add job uid to status
  // do NOT create pod log
  // run successfully

  private Cluster getCluster(String clusterName) {
    return domain.getSpec().getClusters().stream()
          .filter(c -> clusterName.equals(c.getClusterName()))
          .findFirst().orElse(new Cluster());
  }

  private String getDomainHome() {
    return "/shared/domains/" + UID;
  }
}

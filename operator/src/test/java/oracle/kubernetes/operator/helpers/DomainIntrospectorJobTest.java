// Copyright 2018, 2019, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helpers;

import static com.meterware.simplestub.Stub.createStrictStub;
import static oracle.kubernetes.LogMatcher.containsInfo;
import static oracle.kubernetes.operator.logging.MessageKeys.JOB_CREATED;
import static oracle.kubernetes.operator.logging.MessageKeys.JOB_DELETED;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.core.AllOf.allOf;
import static org.hamcrest.junit.MatcherAssert.assertThat;

import com.meterware.simplestub.Memento;
import io.kubernetes.client.ApiException;
import io.kubernetes.client.models.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import oracle.kubernetes.TestUtils;
import oracle.kubernetes.operator.JobWatcher;
import oracle.kubernetes.operator.KubernetesConstants;
import oracle.kubernetes.operator.LabelConstants;
import oracle.kubernetes.operator.TuningParameters;
import oracle.kubernetes.operator.TuningParameters.WatchTuning;
import oracle.kubernetes.operator.VersionConstants;
import oracle.kubernetes.operator.work.FiberTestSupport;
import oracle.kubernetes.operator.work.TerminalStep;
import oracle.kubernetes.weblogic.domain.v2.Cluster;
import oracle.kubernetes.weblogic.domain.v2.ConfigurationConstants;
import oracle.kubernetes.weblogic.domain.v2.Domain;
import oracle.kubernetes.weblogic.domain.v2.DomainSpec;
import org.hamcrest.Matcher;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

@SuppressWarnings({"ConstantConditions, unchecked", "SameParameterValue", "deprecation"})
public class DomainIntrospectorJobTest {
  static final String NS = "namespace";
  private static final String DOMAIN_NAME = "domain1";
  private static final String UID = "uid1";
  static final String ADMIN_SERVER = "ADMIN_SERVER";
  static final Integer ADMIN_PORT = 7001;

  private static final String CREDENTIALS_SECRET_NAME = "webLogicCredentialsSecretName";
  private static final String LATEST_IMAGE = "image:latest";
  static final String VERSIONED_IMAGE = "image:1.2.3";

  static final String SECRETS_VOLUME = "weblogic-credentials-volume";
  static final String SCRIPTS_VOLUME = "weblogic-domain-cm-volume";
  static final String SIT_CONFIG_MAP_VOLUME_SUFFIX = "-weblogic-domain-introspect-cm-volume";
  static final String STORAGE_VOLUME = "weblogic-domain-storage-volume";
  static final String SECRETS_MOUNT_PATH = "/weblogic-operator/secrets";
  static final String SCRIPTS_MOUNTS_PATH = "/weblogic-operator/scripts";
  static final String STORAGE_MOUNT_PATH = "/shared";
  static final String NODEMGR_HOME = "/u01/nodemanager";
  static final String LOG_HOME = "/shared/logs/" + UID;
  static final int FAILURE_THRESHOLD = 1;

  static final String OVERRIDES_CM = "overrides-config-map";
  static final String OVERRIDE_SECRET_1 = "override-secret-1";
  static final String OVERRIDE_SECRET_2 = "override-secret-2";
  static final String OVERRIDE_SECRETS_MOUNT_PATH = "/weblogic-operator/config-overrides-secrets";
  static final String OVERRIDES_CM_MOUNT_PATH = "/weblogic-operator/config-overrides";

  static final String READ_WRITE_MANY_ACCESS = "ReadWriteMany";

  @SuppressWarnings("OctalInteger")
  static final int ALL_READ_AND_EXECUTE = 0555;

  private static final String WEBLOGIC_OPERATOR_SCRIPTS_INTROSPECT_DOMAIN_SH =
      "/weblogic-operator/scripts/introspectDomain.sh";

  private static final String introspectResult =
      ">>>  /u01/introspect/domain1/userConfigNodeManager.secure\n"
          + "#WebLogic User Configuration File; 2\n"
          + "#Thu Oct 04 21:07:06 GMT 2018\n"
          + "weblogic.management.username={AES}fq11xKVoE927O07IUKhQ00d4A8QY598Dvd+KSnHNTEA\\=\n"
          + "weblogic.management.password={AES}LIxVY+aqI8KBkmlBTwkvAnQYQs4PS0FX3Ili4uLBggo\\=\n"
          + "\n"
          + ">>> EOF\n"
          + "\n"
          + "@[2018-10-04T21:07:06.864 UTC][introspectDomain.py:105] Printing file /u01/introspect/domain1/userKeyNodeManager.secure\n"
          + "\n"
          + ">>>  /u01/introspect/domain1/userKeyNodeManager.secure\n"
          + "BPtNabkCIIc2IJp/TzZ9TzbUHG7O3xboteDytDO3XnwNhumdSpaUGKmcbusdmbOUY+4J2kteu6xJPWTzmNRAtg==\n"
          + "\n"
          + ">>> EOF\n"
          + "\n"
          + "@[2018-10-04T21:07:06.867 UTC][introspectDomain.py:105] Printing file /u01/introspect/domain1/topology.yaml\n"
          + "\n"
          + ">>>  /u01/introspect/domain1/topology.yaml\n"
          + "domainValid: true\n"
          + "domain:\n"
          + "  name: \"base_domain\"\n"
          + "  adminServerName: \"admin-server\"\n"
          + "  configuredClusters:\n"
          + "    - name: \"mycluster\"\n"
          + "      servers:\n"
          + "        - name: \"managed-server1\"\n"
          + "          listenPort: 8001\n"
          + "          listenAddress: \"domain1-managed-server1\"\n"
          + "        - name: \"managed-server2\"\n"
          + "          listenPort: 8001\n"
          + "          listenAddress: \"domain1-managed-server2\"\n"
          + "  servers:\n"
          + "    - name: \"admin-server\"\n"
          + "      listenPort: 7001\n"
          + "      listenAddress: \"domain1-admin-server\"\n"
          + "\n"
          + ">>> EOF";

  final TerminalStep terminalStep = new TerminalStep();
  private final Domain domain = createDomain();
  private final DomainPresenceInfo domainPresenceInfo = createDomainPresenceInfo(domain);
  protected AsyncCallTestSupport testSupport = new AsyncCallTestSupport();
  protected List<Memento> mementos = new ArrayList<>();
  protected List<LogRecord> logRecords = new ArrayList<>();
  RetryStrategyStub retryStrategy = createStrictStub(RetryStrategyStub.class);

  public DomainIntrospectorJobTest() {}

  @Before
  public void setUp() throws Exception {
    mementos.add(
        TestUtils.silenceOperatorLogger()
            .collectLogMessages(logRecords, getMessageKeys())
            .withLogLevel(Level.INFO));
    mementos.add(testSupport.installRequestStepFactory());
    // mementos.add(TuningParametersStub.install());

    testSupport.addDomainPresenceInfo(domainPresenceInfo);
  }

  private String[] getMessageKeys() {
    return new String[] {getJobCreatedMessageKey(), getJobDeletedMessageKey()};
  }

  @After
  public void tearDown() throws Exception {
    for (Memento memento : mementos) memento.revert();
  }

  private Domain createDomain() {
    return new Domain().withMetadata(new V1ObjectMeta().namespace(NS)).withSpec(createDomainSpec());
  }

  private DomainPresenceInfo createDomainPresenceInfo(Domain domain) {
    DomainPresenceInfo domainPresenceInfo = new DomainPresenceInfo(domain);
    return domainPresenceInfo;
  }

  @SuppressWarnings("deprecation")
  private DomainSpec createDomainSpec() {
    Cluster cluster = new Cluster();
    cluster.setClusterName("cluster-1");
    cluster.setReplicas(1);
    cluster.setServerStartPolicy(ConfigurationConstants.START_IF_NEEDED);
    DomainSpec spec =
        new DomainSpec()
            .withDomainUID(UID)
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

  String getJobCreatedMessageKey() {
    return JOB_CREATED;
  }

  String getJobDeletedMessageKey() {
    return JOB_DELETED;
  }

  @Test
  public void whenNoJob_createIt() {
    expectCreateJob(jobWithName(getJobName())).returning(createJobModel());
    expectListPods(NS).returning(createListPods());
    expectReadPodLog(getJobName(), NS).returning(introspectResult);
    expectDeleteJob(getJobName(), NS, new V1DeleteOptions().propagationPolicy("Foreground"))
        .returning(new V1Status());
    expectReadConfigMap(ConfigMapHelper.SitConfigMapContext.getConfigMapName(UID), NS)
        .returning(new V1ConfigMap());

    expectStepsAfterCreation();

    testSupport.runSteps(getStepFactory(), terminalStep);
    assertThat(logRecords, containsInfo(getJobCreatedMessageKey()));

    assertThat(logRecords, containsInfo(getJobDeletedMessageKey()));
    testSupport.verifyAllDefinedResponsesInvoked();
  }

  @Test
  public void whenNoJob_retryOnFailure() {
    testSupport.addRetryStrategy(retryStrategy);
    expectCreateJob(jobWithName(getJobName())).failingWithStatus(401);

    expectStepsAfterCreation();

    testSupport.runSteps(getStepFactory(), terminalStep);

    testSupport.verifyCompletionThrowable(ApiException.class);
    testSupport.verifyAllDefinedResponsesInvoked();
  }

  @Test
  public void whenJobCreated_specHasOneContainer() {
    assertThat(getCreatedJob().getSpec().getTemplate().getSpec().getContainers(), hasSize(1));
  }

  @SuppressWarnings("unchecked")
  @Test
  public void whenJobCreated_hasPredefinedEnvVariables() {
    assertThat(
        getCreatedJobSpecContainer().getEnv(),
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

  V1Container getCreatedJobSpecContainer() {
    return getCreatedJob().getSpec().getTemplate().getSpec().getContainers().get(0);
  }

  CallTestSupport.CannedResponse expectCreateJob(BodyMatcher bodyMatcher) {
    return testSupport.createCannedResponse("createJob").withNamespace(NS).withBody(bodyMatcher);
  }

  CallTestSupport.CannedResponse expectListPods(String namespace) {
    return testSupport
        .createCannedResponse("listPod")
        .withNamespace(namespace)
        .withLabelSelectors(LabelConstants.JOBNAME_LABEL);
  }

  CallTestSupport.CannedResponse expectReadPodLog(String jobName, String namespace) {
    return testSupport
        .createCannedResponse("readPodLog")
        .withName(jobName)
        .withNamespace(namespace);
  }

  CallTestSupport.CannedResponse expectDeleteJob(
      String jobName, String namespace, V1DeleteOptions deleteOptions) {
    return testSupport
        .createCannedResponse("deleteJob")
        .withName(jobName)
        .withNamespace(namespace)
        .withBody(deleteOptions);
  }

  CallTestSupport.CannedResponse expectReadConfigMap(String cmName, String namespace) {
    return testSupport
        .createCannedResponse("readConfigMap")
        .withName(cmName)
        .withNamespace(namespace);
  }

  BodyMatcher jobWithName(String jobName) {
    return body -> body instanceof V1Job && getJobName((V1Job) body).equals(jobName);
  }

  private static String getJobName(V1Job actualBody) {
    return actualBody.getMetadata().getName();
  }

  static String getJobName() {
    return LegalNames.toJobIntrospectorName(UID);
  }

  FiberTestSupport.StepFactory getStepFactory() {
    return next ->
        JobHelper.createDomainIntrospectorJobStep(
            new WatchTuning(30, 0),
            next,
            new ConcurrentHashMap<String, JobWatcher>(),
            new AtomicBoolean(false));
  }

  V1PodList createListPods() {
    return new V1PodList()
        .addItemsItem(new V1Pod().metadata(new V1ObjectMeta().name(getJobName())));
  }

  V1Job createJobModel() {
    return new V1Job()
        .metadata(createJobMetadata())
        .spec(createJobSpec(null))
        .status(
            new V1JobStatus()
                .addConditionsItem(new V1JobCondition().type("Complete").status("True")));
  }

  void expectStepsAfterCreation() {}

  private CallTestSupport.CannedResponse expectDeleteJob(String jobName) {
    return testSupport
        .createCannedResponse("deletePod")
        .withNamespace(NS)
        .withBody(new V1DeleteOptions())
        .withName(jobName);
  }

  protected V1ObjectMeta createJobMetadata() {
    V1ObjectMeta metadata =
        new V1ObjectMeta()
            .name(getJobName())
            .namespace(NS)
            .putLabelsItem(LabelConstants.RESOURCE_VERSION_LABEL, VersionConstants.DOMAIN_V1)
            .putLabelsItem(LabelConstants.DOMAINUID_LABEL, UID)
            .putLabelsItem(LabelConstants.DOMAINNAME_LABEL, DOMAIN_NAME)
            .putLabelsItem(LabelConstants.CREATEDBYOPERATOR_LABEL, "true");
    return metadata;
  }

  protected V1JobSpec createJobSpec(TuningParameters tuningParameters) {
    V1JobSpec jobSpec =
        new V1JobSpec().backoffLimit(0).template(createPodTemplateSpec(tuningParameters));

    return jobSpec;
  }

  private V1PodTemplateSpec createPodTemplateSpec(TuningParameters tuningParameters) {
    V1ObjectMeta metadata = new V1ObjectMeta().name(getJobName());
    V1PodTemplateSpec podTemplateSpec =
        new V1PodTemplateSpec().metadata(metadata).spec(createPodSpec(tuningParameters));
    return podTemplateSpec;
  }

  private V1PodSpec createPodSpec(TuningParameters tuningParameters) {
    V1PodSpec podSpec =
        new V1PodSpec()
            .activeDeadlineSeconds(60L)
            .restartPolicy("Never")
            .addContainersItem(createContainer(tuningParameters))
            .addVolumesItem(new V1Volume().name(SECRETS_VOLUME).secret(getSecretsVolume()))
            .addVolumesItem(
                new V1Volume().name(SCRIPTS_VOLUME).configMap(getConfigMapVolumeSource()));
    /**
     * V1LocalObjectReference imagePullSecret =
     * domainPresenceInfo.getDomain().getSpec().getImagePullSecret(); if (imagePullSecret != null) {
     * podSpec.addImagePullSecretsItem(imagePullSecret); }
     */
    List<String> configOverrideSecrets = getConfigOverrideSecrets();
    for (String secretName : configOverrideSecrets) {
      podSpec.addVolumesItem(
          new V1Volume()
              .name(secretName + "-volume")
              .secret(getOverrideSecretVolumeSource(secretName)));
    }
    podSpec.addVolumesItem(
        new V1Volume()
            .name(getConfigOverrides())
            .configMap(getOverridesVolumeSource(getConfigOverrides())));

    return podSpec;
  }

  private V1Container createContainer(TuningParameters tuningParameters) {
    V1Container container =
        new V1Container()
            .name(getJobName())
            .image(getImageName())
            .imagePullPolicy(getImagePullPolicy())
            .command(getContainerCommand())
            .env(getEnvironmentVariables(tuningParameters))
            .addVolumeMountsItem(volumeMount(STORAGE_VOLUME, STORAGE_MOUNT_PATH))
            .addVolumeMountsItem(readOnlyVolumeMount(SECRETS_VOLUME, SECRETS_MOUNT_PATH))
            .addVolumeMountsItem(readOnlyVolumeMount(SCRIPTS_VOLUME, SCRIPTS_MOUNTS_PATH));

    List<String> configOverrideSecrets = getConfigOverrideSecrets();
    for (String secretName : configOverrideSecrets) {
      container.addVolumeMountsItem(
          readOnlyVolumeMount(secretName + "-volume", OVERRIDE_SECRETS_MOUNT_PATH));
    }
    container.addVolumeMountsItem(
        readOnlyVolumeMount(OVERRIDES_CM + "-volume", OVERRIDES_CM_MOUNT_PATH));

    return container;
  }

  String getImageName() {
    return KubernetesConstants.DEFAULT_IMAGE;
  }

  String getImagePullPolicy() {
    return KubernetesConstants.IFNOTPRESENT_IMAGEPULLPOLICY;
  }

  protected List<String> getContainerCommand() {
    return Arrays.asList(WEBLOGIC_OPERATOR_SCRIPTS_INTROSPECT_DOMAIN_SH);
  }

  List<V1EnvVar> getEnvironmentVariables(TuningParameters tuningParameters) {
    List<V1EnvVar> envVarList = new ArrayList<V1EnvVar>();
    addEnvVar(envVarList, "NAMESPACE", NS);
    addEnvVar(envVarList, "DOMAIN_UID", UID);
    addEnvVar(envVarList, "DOMAIN_HOME", getDomainHome());
    addEnvVar(envVarList, "NODEMGR_HOME", NODEMGR_HOME);
    addEnvVar(envVarList, "LOG_HOME", LOG_HOME);
    addEnvVar(envVarList, "INTROSPECT_HOME", getDomainHome());
    addEnvVar(envVarList, "CREDENTIALS_SECRET_NAME", CREDENTIALS_SECRET_NAME);
    addEnvVar(envVarList, "SERVER_OUT_IN_POD_LOG", "true");

    return envVarList;
  }

  static void addEnvVar(List<V1EnvVar> vars, String name, String value) {
    vars.add(new V1EnvVar().name(name).value(value));
  }

  protected String getDomainHome() {
    return "/shared/domains/" + UID;
  }

  protected V1SecretVolumeSource getSecretsVolume() {
    return new V1SecretVolumeSource().secretName(CREDENTIALS_SECRET_NAME).defaultMode(420);
  }

  protected V1ConfigMapVolumeSource getConfigMapVolumeSource() {
    return new V1ConfigMapVolumeSource()
        .name(KubernetesConstants.DOMAIN_CONFIG_MAP_NAME)
        .defaultMode(ALL_READ_AND_EXECUTE);
  }

  private List<String> getConfigOverrideSecrets() {
    return domainPresenceInfo.getDomain().getConfigOverrideSecrets();
  }

  private String getConfigOverrides() {
    return domainPresenceInfo.getDomain().getConfigOverrides();
  }

  private static V1VolumeMount readOnlyVolumeMount(String volumeName, String mountPath) {
    return volumeMount(volumeName, mountPath).readOnly(true);
  }

  private static V1VolumeMount volumeMount(String volumeName, String mountPath) {
    return new V1VolumeMount().name(volumeName).mountPath(mountPath);
  }

  protected V1PersistentVolumeClaimVolumeSource getPersistenVolumeClaimVolumeSource(
      String claimName) {
    return new V1PersistentVolumeClaimVolumeSource().claimName(claimName);
  }

  protected V1SecretVolumeSource getOverrideSecretVolumeSource(String name) {
    return new V1SecretVolumeSource().secretName(name).defaultMode(420);
  }

  protected V1ConfigMapVolumeSource getOverridesVolumeSource(String name) {
    return new V1ConfigMapVolumeSource().name(name).defaultMode(ALL_READ_AND_EXECUTE);
  }

  V1Job getCreatedJob() {
    JobFetcher jobFetcher = new JobFetcher(getJobName());
    expectCreateJob(jobFetcher).returning(createJobModel());
    expectStepsAfterCreation();

    testSupport.runSteps(getStepFactory(), terminalStep);
    logRecords.clear();

    return jobFetcher.getCreatedJob();
  }

  static Matcher<Iterable<? super V1EnvVar>> hasEnvVar(String name, String value) {
    return hasItem(new V1EnvVar().name(name).value(value));
  }

  static class JobFetcher implements BodyMatcher {
    private String jobName;
    V1Job createdJob;

    JobFetcher(String jobName) {
      this.jobName = jobName;
    }

    V1Job getCreatedJob() {
      return createdJob;
    }

    @Override
    public boolean matches(Object actualBody) {
      if (!isExpectedJob(actualBody)) {
        return false;
      } else {
        createdJob = (V1Job) actualBody;
        return true;
      }
    }

    private boolean isExpectedJob(Object body) {
      return body instanceof V1Job && getJobName((V1Job) body).equals(jobName);
    }
  }
}

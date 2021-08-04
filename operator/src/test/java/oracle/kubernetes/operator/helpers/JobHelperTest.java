// Copyright (c) 2019, 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helpers;

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import com.meterware.simplestub.Memento;
import io.kubernetes.client.openapi.models.V1Affinity;
import io.kubernetes.client.openapi.models.V1Container;
import io.kubernetes.client.openapi.models.V1EnvVar;
import io.kubernetes.client.openapi.models.V1Job;
import io.kubernetes.client.openapi.models.V1JobSpec;
import io.kubernetes.client.openapi.models.V1LocalObjectReference;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1PodReadinessGate;
import io.kubernetes.client.openapi.models.V1PodSecurityContext;
import io.kubernetes.client.openapi.models.V1PodSpec;
import io.kubernetes.client.openapi.models.V1PodTemplateSpec;
import io.kubernetes.client.openapi.models.V1SecurityContext;
import io.kubernetes.client.openapi.models.V1Toleration;
import io.kubernetes.client.openapi.models.V1Volume;
import io.kubernetes.client.openapi.models.V1VolumeMount;
import oracle.kubernetes.operator.DomainSourceType;
import oracle.kubernetes.operator.JobAwaiterStepFactory;
import oracle.kubernetes.operator.LabelConstants;
import oracle.kubernetes.operator.ProcessingConstants;
import oracle.kubernetes.operator.TuningParameters;
import oracle.kubernetes.operator.helpers.JobHelper.DomainIntrospectorJobStepContext;
import oracle.kubernetes.operator.utils.WlsDomainConfigSupport;
import oracle.kubernetes.operator.work.Component;
import oracle.kubernetes.operator.work.Packet;
import oracle.kubernetes.utils.TestUtils;
import oracle.kubernetes.weblogic.domain.ClusterConfigurator;
import oracle.kubernetes.weblogic.domain.DomainConfigurator;
import oracle.kubernetes.weblogic.domain.DomainConfiguratorFactory;
import oracle.kubernetes.weblogic.domain.ServerConfigurator;
import oracle.kubernetes.weblogic.domain.model.ConfigurationConstants;
import oracle.kubernetes.weblogic.domain.model.Domain;
import oracle.kubernetes.weblogic.domain.model.DomainSpec;
import oracle.kubernetes.weblogic.domain.model.DomainValidationBaseTest;
import oracle.kubernetes.weblogic.domain.model.ServerEnvVars;
import org.hamcrest.Matcher;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.meterware.simplestub.Stub.createNiceStub;
import static oracle.kubernetes.operator.DomainProcessorTestSetup.NS;
import static oracle.kubernetes.operator.DomainProcessorTestSetup.UID;
import static oracle.kubernetes.operator.DomainProcessorTestSetup.createTestDomain;
import static oracle.kubernetes.operator.ProcessingConstants.DOMAIN_TOPOLOGY;
import static oracle.kubernetes.operator.ProcessingConstants.JOBWATCHER_COMPONENT_NAME;
import static oracle.kubernetes.operator.helpers.Matchers.hasConfigMapVolume;
import static oracle.kubernetes.operator.helpers.Matchers.hasContainer;
import static oracle.kubernetes.operator.helpers.Matchers.hasEnvVar;
import static oracle.kubernetes.operator.helpers.Matchers.hasEnvVarRegEx;
import static oracle.kubernetes.operator.helpers.Matchers.hasSecretVolume;
import static oracle.kubernetes.operator.helpers.Matchers.hasVolumeMount;
import static oracle.kubernetes.operator.helpers.PodHelperTestBase.createAffinity;
import static oracle.kubernetes.operator.helpers.PodHelperTestBase.createConfigMapKeyRefEnvVar;
import static oracle.kubernetes.operator.helpers.PodHelperTestBase.createContainer;
import static oracle.kubernetes.operator.helpers.PodHelperTestBase.createFieldRefEnvVar;
import static oracle.kubernetes.operator.helpers.PodHelperTestBase.createPodSecurityContext;
import static oracle.kubernetes.operator.helpers.PodHelperTestBase.createSecretKeyRefEnvVar;
import static oracle.kubernetes.operator.helpers.PodHelperTestBase.createSecurityContext;
import static oracle.kubernetes.operator.helpers.PodHelperTestBase.createToleration;
import static oracle.kubernetes.operator.utils.ChecksumUtils.getMD5Hash;
import static oracle.kubernetes.weblogic.domain.model.IntrospectorJobEnvVars.MII_USE_ONLINE_UPDATE;
import static oracle.kubernetes.weblogic.domain.model.IntrospectorJobEnvVars.MII_WDT_ACTIVATE_TIMEOUT;
import static oracle.kubernetes.weblogic.domain.model.IntrospectorJobEnvVars.MII_WDT_CONNECT_TIMEOUT;
import static oracle.kubernetes.weblogic.domain.model.IntrospectorJobEnvVars.MII_WDT_DEPLOY_TIMEOUT;
import static oracle.kubernetes.weblogic.domain.model.IntrospectorJobEnvVars.MII_WDT_REDEPLOY_TIMEOUT;
import static oracle.kubernetes.weblogic.domain.model.IntrospectorJobEnvVars.MII_WDT_SET_SERVERGROUPS_TIMEOUT;
import static oracle.kubernetes.weblogic.domain.model.IntrospectorJobEnvVars.MII_WDT_START_APPLICATION_TIMEOUT;
import static oracle.kubernetes.weblogic.domain.model.IntrospectorJobEnvVars.MII_WDT_STOP_APPLICAITON_TIMEOUT;
import static oracle.kubernetes.weblogic.domain.model.IntrospectorJobEnvVars.MII_WDT_UNDEPLOY_TIMEOUT;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.anEmptyMap;
import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.junit.MatcherAssert.assertThat;

class JobHelperTest extends DomainValidationBaseTest {
  private static final String RAW_VALUE_1 = "find uid1 at $(DOMAIN_HOME)";
  private static final String END_VALUE_1 = "find uid1 at /u01/oracle/user_projects/domains";
  protected static final String LONG_RESOURCE_NAME
            = "very-long-resource-name-very-long-resource-name-abcdefghi";
  protected static final String SECOND_LONG_RESOURCE_NAME
            = "very-long-resource-name-very-long-resource-name-abcdefghijklmnopqrstuvwxyz";

  /** 
   * OEVN is the name of an env var that contains a comma-separated list of oper supplied env var names.
   * It's used by the Model in Image introspector job to detect env var differences from the last
   * time the job ran.
   */
  private static final String OEVN = "OPERATOR_ENVVAR_NAMES";
  public static final String SECRET_VOLUME_SUFFIX1 = "-volume-st-" + getMD5Hash(LONG_RESOURCE_NAME);
  public static final String SECRET_VOLUME_SUFFIX2 = "-volume-st-" + getMD5Hash(SECOND_LONG_RESOURCE_NAME);
  public static final String CM_VOLUME_SUFFIX1 = "-volume-cm-" + getMD5Hash(LONG_RESOURCE_NAME);
  public static final int MAX_ALLOWED_VOLUME_NAME_LENGTH = 63;
  public static final String VOLUME_NAME_FOR_LONG_SECRET_NAME = LONG_RESOURCE_NAME
          .substring(0, MAX_ALLOWED_VOLUME_NAME_LENGTH - SECRET_VOLUME_SUFFIX1.length()) + SECRET_VOLUME_SUFFIX1;
  public static final String VOLUME_NAME_FOR_SECOND_LONG_SECRET_NAME = SECOND_LONG_RESOURCE_NAME
          .substring(0, MAX_ALLOWED_VOLUME_NAME_LENGTH - SECRET_VOLUME_SUFFIX2.length()) + SECRET_VOLUME_SUFFIX2;
  public static final String VOLUME_NAME_FOR_LONG_CONFIG_MAP_NAME = LONG_RESOURCE_NAME
          .substring(0, MAX_ALLOWED_VOLUME_NAME_LENGTH - SECRET_VOLUME_SUFFIX1.length()) + CM_VOLUME_SUFFIX1;
  public static final int MODE_420 = 420;
  public static final int MODE_365 = 365;
  private Method getDomainSpec;
  private final Domain domain = createTestDomain();
  private final DomainPresenceInfo domainPresenceInfo = createDomainPresenceInfo(domain);
  private final V1PodSecurityContext podSecurityContext = createPodSecurityContext(123L);
  private final V1SecurityContext containerSecurityContext = createSecurityContext(555L);
  private final V1Affinity podAffinity = createAffinity();
  private final V1Toleration toleration = createToleration("key","Eqauls", "value", "NoSchedule");
  private final V1EnvVar configMapKeyRefEnvVar = createConfigMapKeyRefEnvVar("VARIABLE1", "my-env", "VAR1");
  private final V1EnvVar secretKeyRefEnvVar = createSecretKeyRefEnvVar("VARIABLE2", "my-secret", "VAR2");
  private final V1EnvVar fieldRefEnvVar = createFieldRefEnvVar("MY_NODE_IP", "status.hostIP");
  private final List<Memento> mementos = new ArrayList<>();
  private final KubernetesTestSupport testSupport = new KubernetesTestSupport();

  @BeforeEach
  public void setup() throws Exception {
    mementos.add(TestUtils.silenceOperatorLogger());
    mementos.add(TuningParametersStub.install());
    mementos.add(testSupport.install());

    domain.getSpec().setNodeName(null);
    testSupport.defineResources(domain);
    testSupport.addDomainPresenceInfo(domainPresenceInfo);
    testSupport.addComponent(JOBWATCHER_COMPONENT_NAME,
          JobAwaiterStepFactory.class,
          createNiceStub(JobAwaiterStepFactory.class));
  }

  @AfterEach
  public void tearDown() {
    mementos.forEach(Memento::revert);
  }

  @Test
  void creatingServers_true_whenClusterReplicas_gt_0() {
    configureCluster("cluster1").withReplicas(1);

    assertThat(JobHelper.creatingServers(domainPresenceInfo), equalTo(true));
  }

  @Test
  void creatingServers_false_whenClusterReplicas_is_0() {
    configureCluster("cluster1").withReplicas(0);

    assertThat(JobHelper.creatingServers(domainPresenceInfo), equalTo(false));
  }

  @Test
  void creatingServers_true_whenDomainReplicas_gt_0_and_cluster_has_no_replicas() {
    configureDomain().withDefaultReplicaCount(1);

    configureCluster("cluster1");

    assertThat(JobHelper.creatingServers(domainPresenceInfo), equalTo(true));
  }

  @Test
  void creatingServers_false_whenDomainReplicas_is_0_and_cluster_has_no_replicas() {
    configureDomain().withDefaultReplicaCount(0);

    configureCluster("cluster1");

    assertThat(JobHelper.creatingServers(domainPresenceInfo), equalTo(false));
  }

  @Test
  void creatingServers_false_when_no_domain_nor_cluster_replicas() {
    configureCluster("cluster1");

    assertThat(JobHelper.creatingServers(domainPresenceInfo), equalTo(false));
  }

  @Test
  void creatingServers_false_when_noCluster_and_Start_Never_startPolicy() {
    assertThat(JobHelper.creatingServers(domainPresenceInfo), equalTo(false));
  }

  @Test
  void creatingServers_true_when_noCluster_and_Start_If_Needed_startPolicy() {
    configureDomain()
        .withDefaultServerStartPolicy(ConfigurationConstants.START_IF_NEEDED);

    assertThat(JobHelper.creatingServers(domainPresenceInfo), equalTo(true));
  }

  @Test
  void creatingServers_true_when_noCluster_and_Start_Always_startPolicy() {
    configureDomain()
        .withDefaultServerStartPolicy(ConfigurationConstants.START_ALWAYS);

    assertThat(JobHelper.creatingServers(domainPresenceInfo), equalTo(true));
  }

  @Test
  void creatingServers_false_when_server_with_Start_Never_startPolicy() {
    configureServer("managed-server1")
        .withServerStartPolicy(ConfigurationConstants.START_NEVER);

    assertThat(JobHelper.creatingServers(domainPresenceInfo), equalTo(false));
  }

  @Test
  void creatingServers_true_when_server_with_Start_If_Needed_startPolicy() {
    configureServer("managed-server1")
        .withServerStartPolicy(ConfigurationConstants.START_IF_NEEDED);

    assertThat(JobHelper.creatingServers(domainPresenceInfo), equalTo(true));
  }

  @Test
  void creatingServers_true_when_server_with_Start_Always_startPolicy() {
    configureServer("managed-server1")
        .withServerStartPolicy(ConfigurationConstants.START_ALWAYS);

    assertThat(JobHelper.creatingServers(domainPresenceInfo), equalTo(true));
  }

  @Test
  void whenDomainHasEnvironmentItems_introspectorPodStartupWithThem() {
    configureDomain()
        .withEnvironmentVariable("item1", "value1")
        .withEnvironmentVariable("item2", "value2")
        .withEnvironmentVariable("WL_HOME", "/u01/custom_wl_home/")
        .withEnvironmentVariable("MW_HOME", "/u01/custom_mw_home/");

    V1JobSpec jobSpec = createJobSpec();

    assertThat(
        getMatchingContainerEnv(domainPresenceInfo, jobSpec),
        allOf(
            hasEnvVar("item1", "value1"),
            hasEnvVar("item2", "value2"),
            hasEnvVar("WL_HOME", "/u01/custom_wl_home/"),
            hasEnvVar("MW_HOME", "/u01/custom_mw_home/")));

    assertThat(
        getMatchingContainerEnv(domainPresenceInfo, jobSpec),
        allOf(
            envVarOEVNContains("item1"),
            envVarOEVNContains("item2"),
            envVarOEVNContains("WL_HOME"),
            envVarOEVNContains("MW_HOME")));
  }

  @Test
  void whenDomainIsOnlineUpdate_introspectorPodStartupWithThem() {
    configureDomain()
        .withMIIOnlineUpdate();

    V1JobSpec jobSpec = createJobSpec();

    assertThat(
        getMatchingContainerEnv(domainPresenceInfo, jobSpec),
        allOf(
            hasEnvVar(MII_USE_ONLINE_UPDATE, "true"),
            envVarOEVNContains(MII_WDT_ACTIVATE_TIMEOUT),
            envVarOEVNContains(MII_WDT_CONNECT_TIMEOUT),
            envVarOEVNContains(MII_WDT_DEPLOY_TIMEOUT),
            envVarOEVNContains(MII_WDT_REDEPLOY_TIMEOUT),
            envVarOEVNContains(MII_WDT_UNDEPLOY_TIMEOUT),
            envVarOEVNContains(MII_WDT_START_APPLICATION_TIMEOUT),
            envVarOEVNContains(MII_WDT_STOP_APPLICAITON_TIMEOUT),
            envVarOEVNContains(MII_WDT_SET_SERVERGROUPS_TIMEOUT)
            ));
  }

  @Test
  void whenDomainIsNotOnlineUpdate_introspectorPodStartupWithoutThem() {

    V1JobSpec jobSpec = createJobSpec();

    assertThat(
        getMatchingContainerEnv(domainPresenceInfo, jobSpec),
        not(anyOf(envVarOEVNContains(MII_USE_ONLINE_UPDATE),
            envVarOEVNContains(MII_WDT_ACTIVATE_TIMEOUT),
            envVarOEVNContains(MII_WDT_CONNECT_TIMEOUT),
            envVarOEVNContains(MII_WDT_DEPLOY_TIMEOUT),
            envVarOEVNContains(MII_WDT_REDEPLOY_TIMEOUT),
            envVarOEVNContains(MII_WDT_UNDEPLOY_TIMEOUT),
            envVarOEVNContains(MII_WDT_START_APPLICATION_TIMEOUT),
            envVarOEVNContains(MII_WDT_STOP_APPLICAITON_TIMEOUT),
            envVarOEVNContains(MII_WDT_SET_SERVERGROUPS_TIMEOUT)
            )));

  }

  private V1JobSpec createJobSpec() {
    Packet packet = new Packet();
    packet
        .getComponents()
        .put(ProcessingConstants.DOMAIN_COMPONENT_NAME, Component.createFor(domainPresenceInfo));
    DomainIntrospectorJobStepContext domainIntrospectorJobStepContext =
        new DomainIntrospectorJobStepContext(packet);
    return domainIntrospectorJobStepContext.createJobSpec(TuningParameters.getInstance());
  }

  @Test
  void introspectorPodStartsWithDefaultUser_Mem_Args_environmentVariable() {
    V1JobSpec jobSpec = createJobSpec();

    assertThat(
        getMatchingContainerEnv(domainPresenceInfo, jobSpec),
        hasEnvVar(
            "USER_MEM_ARGS", "-Djava.security.egd=file:/dev/./urandom"));
  }

  @Test
  void whenDomainHasEmptyStringUser_Mem_Args_EnvironmentItem_introspectorPodStartupWithIt() {
    configureDomain().withEnvironmentVariable("USER_MEM_ARGS", "");

    V1JobSpec jobSpec = createJobSpec();

    assertThat(
        getMatchingContainerEnv(domainPresenceInfo, jobSpec), hasEnvVar("USER_MEM_ARGS", ""));

    assertThat(
        getMatchingContainerEnv(domainPresenceInfo, jobSpec), envVarOEVNContains("USER_MEM_ARGS"));
  }

  @Test
  void whenDomainHasEnvironmentItemsWithVariables_introspectorPodStartupWithThem() {
    configureDomain().withEnvironmentVariable("item1", RAW_VALUE_1);

    V1JobSpec jobSpec = createJobSpec();

    assertThat(
        getMatchingContainerEnv(domainPresenceInfo, jobSpec), hasEnvVar("item1", END_VALUE_1));

    assertThat(
        getMatchingContainerEnv(domainPresenceInfo, jobSpec), envVarOEVNContains("item1"));

  }

  private static final String EMPTY_DATA_HOME = "";

  @Test
  void whenDomainHasEnvironmentVars_introspectorPodStartupVerifyDataHomeEnvNotDefined() {
    V1JobSpec jobSpec = createJobSpec();

    assertThat(getMatchingContainerEnv(domainPresenceInfo, jobSpec),
              not(hasEnvVar(ServerEnvVars.DATA_HOME, EMPTY_DATA_HOME)));

    assertThat(
        getMatchingContainerEnv(domainPresenceInfo, jobSpec),
        allOf(
            hasEnvVar(OEVN),
            not(envVarOEVNContains(ServerEnvVars.DATA_HOME))));
  }

  private static final String OVERRIDE_DATA_DIR = "/u01/data";
  private static final String OVERRIDE_DATA_HOME = OVERRIDE_DATA_DIR + File.separator + UID;

  @Test
  void whenDomainHasEnvironmentVars_introspectorPodStartupVerifyDataHomeEnvDefined() {
    configureDomain().withDataHome(OVERRIDE_DATA_DIR);

    V1JobSpec jobSpec = createJobSpec();

    assertThat(getMatchingContainerEnv(domainPresenceInfo, jobSpec),
            hasEnvVar(ServerEnvVars.DATA_HOME, OVERRIDE_DATA_HOME));

    assertThat(getMatchingContainerEnv(domainPresenceInfo, jobSpec),
            envVarOEVNContains(ServerEnvVars.DATA_HOME));
  }

  @Test
  void whenDomainHasEnvironmentVars_introspectorPodStartupVerifyEmptyDataHome() {
    configureDomain().withDataHome(EMPTY_DATA_HOME);

    V1JobSpec jobSpec = createJobSpec();

    assertThat(getMatchingContainerEnv(domainPresenceInfo, jobSpec),
            not(hasEnvVar(ServerEnvVars.DATA_HOME, EMPTY_DATA_HOME)));

    assertThat(getMatchingContainerEnv(domainPresenceInfo, jobSpec),
        allOf(
            hasEnvVar(OEVN),
            not(envVarOEVNContains(ServerEnvVars.DATA_HOME))));
  }

  private static final String NULL_DATA_HOME = null;

  @Test
  void whenDomainHasEnvironmentVars_introspectorPodStartupVerifyNullDataHome() {
    configureDomain().withDataHome(NULL_DATA_HOME);

    V1JobSpec jobSpec = createJobSpec();

    assertThat(getMatchingContainerEnv(domainPresenceInfo, jobSpec),
            not(hasEnvVar(ServerEnvVars.DATA_HOME, NULL_DATA_HOME)));
  }

  @Test
  void whenAdminServerHasEnvironmentItems_introspectorPodStartupWithThem() {
    configureDomain()
        .withEnvironmentVariable("item1", "domain-value1")
        .withEnvironmentVariable("item2", "domain-value2")
        .configureAdminServer()
        .withEnvironmentVariable("item2", "admin-value2")
        .withEnvironmentVariable("item3", "admin-value3");

    V1JobSpec jobSpec = createJobSpec();

    assertThat(
        getMatchingContainerEnv(domainPresenceInfo, jobSpec),
        allOf(
            hasEnvVar("item1", "domain-value1"),
            hasEnvVar("item2", "admin-value2"),
            hasEnvVar("item3", "admin-value3")));

    assertThat(
        getMatchingContainerEnv(domainPresenceInfo, jobSpec),
        allOf(
            envVarOEVNContains("item1"),
            envVarOEVNContains("item2"),
            envVarOEVNContains("item3")));
  }

  @Test
  void whenDomainHasValueFromEnvironmentItems_introspectorPodStartupWithThem() {
    configureDomain()
        .withEnvironmentVariable(configMapKeyRefEnvVar)
        .withEnvironmentVariable(secretKeyRefEnvVar)
        .withEnvironmentVariable(fieldRefEnvVar);

    V1JobSpec jobSpec = createJobSpec();

    assertThat(
        getMatchingContainerEnv(domainPresenceInfo, jobSpec),
        allOf(
            hasItem(configMapKeyRefEnvVar),
            hasItem(secretKeyRefEnvVar),
            hasItem(fieldRefEnvVar)));

    assertThat(
        getMatchingContainerEnv(domainPresenceInfo, jobSpec),
        allOf(
            envVarOEVNContains(configMapKeyRefEnvVar.getName()),
            envVarOEVNContains(secretKeyRefEnvVar.getName()),
            envVarOEVNContains(fieldRefEnvVar.getName())));
  }

  @Test
  void whenAdminServerHasValueFromEnvironmentItems_introspectorPodStartupWithThem() {
    configureDomain()
        .configureAdminServer()
        .withEnvironmentVariable(configMapKeyRefEnvVar)
        .withEnvironmentVariable(secretKeyRefEnvVar)
        .withEnvironmentVariable(fieldRefEnvVar);

    V1JobSpec jobSpec = createJobSpec();

    assertThat(
        getMatchingContainerEnv(domainPresenceInfo, jobSpec),
        allOf(
            hasItem(configMapKeyRefEnvVar),
            hasItem(secretKeyRefEnvVar),
            hasItem(fieldRefEnvVar)));

    assertThat(
        getMatchingContainerEnv(domainPresenceInfo, jobSpec),
        allOf(
            envVarOEVNContains(configMapKeyRefEnvVar.getName()),
            envVarOEVNContains(secretKeyRefEnvVar.getName()),
            envVarOEVNContains(fieldRefEnvVar.getName())));
  }

  @Test
  void introspectorPodStartupWithNullAdminUsernamePasswordEnvVarValues() {
    V1JobSpec jobSpec = createJobSpec();

    assertThat(
        getMatchingContainerEnv(domainPresenceInfo, jobSpec),
        allOf(hasEnvVar("ADMIN_USERNAME", null), hasEnvVar("ADMIN_PASSWORD", null)));

    assertThat(
        getMatchingContainerEnv(domainPresenceInfo, jobSpec),
        allOf(hasEnvVar(OEVN),
              not(envVarOEVNContains("ADMIN_USERNAME")),
              not(envVarOEVNContains("ADMIN_PASSWORD"))));
  }

  @Test
  void
      whenDomainHasEnvironmentItemsWithVariable_createIntrospectorPodShouldNotChangeItsValue()
          throws NoSuchMethodException, IllegalAccessException, InvocationTargetException {
    DomainConfigurator domainConfigurator =
        configureDomain().withEnvironmentVariable("item1", RAW_VALUE_1);

    createJobSpec();

    assertThat(
        getConfiguredDomainSpec(domainConfigurator).getEnv(), hasEnvVar("item1", RAW_VALUE_1));
  }

  @Test
  void whenDomainHasAdditionalVolumesWithReservedVariables_createIntrospectorPodWithSubstitutions() {
    configureDomain()
        .withAdditionalVolumeMount("volume2", "/source-$(DOMAIN_UID)");
    runCreateJob();
    assertThat(getJobVolumeMounts(), hasVolumeMount("volume2", "/source-" + UID));
  }

  private List<V1VolumeMount> getJobVolumeMounts() {
    return Optional.ofNullable(job.getSpec())
          .map(V1JobSpec::getTemplate)
          .map(V1PodTemplateSpec::getSpec)
          .map(V1PodSpec::getContainers)
          .orElseThrow()
          .get(0)
          .getVolumeMounts();
  }

  private List<V1Volume> getJobVolumes() {
    return Optional.ofNullable(job.getSpec())
            .map(V1JobSpec::getTemplate)
            .map(V1PodTemplateSpec::getSpec)
            .map(V1PodSpec::getVolumes)
            .orElseThrow();
  }

  @Test
  void whenDomainHasAdditionalVolumesWithCustomVariables_createIntrospectorPodWithSubstitutions() {
    resourceLookup.defineResource(SECRET_NAME, KubernetesResourceType.Secret, NS);
    resourceLookup.defineResource(OVERRIDES_CM_NAME_MODEL, KubernetesResourceType.ConfigMap, NS);
    resourceLookup.defineResource(OVERRIDES_CM_NAME_IMAGE, KubernetesResourceType.ConfigMap, NS);

    configureDomain()
        .withEnvironmentVariable(ENV_NAME1, GOOD_MY_ENV_VALUE)
        .withWebLogicCredentialsSecret(SECRET_NAME, null)
        .withAdditionalVolume("volume1", VOLUME_PATH_1)
        .withAdditionalVolumeMount("volume1", VOLUME_MOUNT_PATH_1);

    runCreateJob();

    assertThat(getJobVolumeMounts(), hasVolumeMount("volume1", END_VOLUME_MOUNT_PATH_1));
  }

  @Test
  void whenDomainHasAdditionalVolumesWithCustomVariablesInvalidValue_jobNotCreated() {
    resourceLookup.defineResource(SECRET_NAME, KubernetesResourceType.Secret, NS);
    resourceLookup.defineResource(OVERRIDES_CM_NAME_MODEL, KubernetesResourceType.ConfigMap, NS);
    resourceLookup.defineResource(OVERRIDES_CM_NAME_IMAGE, KubernetesResourceType.ConfigMap, NS);

    V1EnvVar envVar = new V1EnvVar().name(ENV_NAME1).value(BAD_MY_ENV_VALUE);
    testSupport.addToPacket(ProcessingConstants.ENVVARS, Collections.singletonList(envVar));

    configureDomain()
        .withEnvironmentVariable(ENV_NAME1, BAD_MY_ENV_VALUE)
        .withWebLogicCredentialsSecret(SECRET_NAME, null)
        .withAdditionalVolume("volume1", VOLUME_PATH_1)
        .withAdditionalVolumeMount("volume1", VOLUME_MOUNT_PATH_1);

    runCreateJob();

    assertThat(testSupport.getResources(KubernetesTestSupport.POD).isEmpty(), org.hamcrest.Matchers.is(true));
    assertThat(job, is(nullValue()));
  }

  @Test
  void whenDomainHasMultipleConfigOverrideSecretsWithLongNames_volumesCreatedWithShorterNames() {
    resourceLookup.defineResource(LONG_RESOURCE_NAME, KubernetesResourceType.Secret, NS);
    resourceLookup.defineResource(SECOND_LONG_RESOURCE_NAME, KubernetesResourceType.Secret, NS);

    configureDomain()
            .withConfigOverrideSecrets(LONG_RESOURCE_NAME, SECOND_LONG_RESOURCE_NAME);

    runCreateJob();

    assertThat(getJobVolumes(), hasSecretVolume(VOLUME_NAME_FOR_LONG_SECRET_NAME, LONG_RESOURCE_NAME, MODE_420));
    assertThat(getJobVolumes(), hasSecretVolume(VOLUME_NAME_FOR_SECOND_LONG_SECRET_NAME,
            SECOND_LONG_RESOURCE_NAME, MODE_420));
    assertThat(getJobVolumeMounts(), hasVolumeMount(VOLUME_NAME_FOR_LONG_SECRET_NAME,
            "/weblogic-operator/config-overrides-secrets/" + LONG_RESOURCE_NAME, true));
    assertThat(getJobVolumeMounts(), hasVolumeMount(VOLUME_NAME_FOR_SECOND_LONG_SECRET_NAME,
            "/weblogic-operator/config-overrides-secrets/" + SECOND_LONG_RESOURCE_NAME, true));
  }

  @Test
  void whenDomainHasConfigMapOverrideWithLongConfigMapName_volumeCreatedWithShorterName() {
    resourceLookup.defineResource(LONG_RESOURCE_NAME, KubernetesResourceType.ConfigMap, NS);

    configureDomain()
            .withConfigOverrides(LONG_RESOURCE_NAME);

    runCreateJob();

    assertThat(getJobVolumes(), hasConfigMapVolume(VOLUME_NAME_FOR_LONG_CONFIG_MAP_NAME, LONG_RESOURCE_NAME, MODE_365));
    assertThat(getJobVolumeMounts(), hasVolumeMount(VOLUME_NAME_FOR_LONG_CONFIG_MAP_NAME,
            "/weblogic-operator/config-overrides", true));
  }

  @Test
  void whenDomainHasModelConfigMapOverrideWithLongModelCMName_volumeCreatedWithShorterName() {
    resourceLookup.defineResource(LONG_RESOURCE_NAME, KubernetesResourceType.ConfigMap, NS);

    configureDomain()
            .withDomainHomeSourceType(DomainSourceType.FromModel)
            .withModelConfigMap(LONG_RESOURCE_NAME);

    runCreateJob();

    assertThat(getJobVolumes(), hasConfigMapVolume(VOLUME_NAME_FOR_LONG_CONFIG_MAP_NAME, LONG_RESOURCE_NAME, MODE_365));
    assertThat(getJobVolumeMounts(), hasVolumeMount(VOLUME_NAME_FOR_LONG_CONFIG_MAP_NAME,
            "/weblogic-operator/wdt-config-map", true));
  }

  @Test
  void whenDomainHasMultipleConfigOverrideSecretsWithLongAndShortNames_volumeCreatedWithCorrectNames() {
    resourceLookup.defineResource(SECRET_NAME, KubernetesResourceType.Secret, NS);
    resourceLookup.defineResource(LONG_RESOURCE_NAME, KubernetesResourceType.Secret, NS);

    configureDomain()
            .withConfigOverrideSecrets(SECRET_NAME, LONG_RESOURCE_NAME);

    runCreateJob();

    assertThat(getJobVolumes(), hasSecretVolume(SECRET_NAME + "-volume", SECRET_NAME, MODE_420));
    assertThat(getJobVolumes(), hasSecretVolume(VOLUME_NAME_FOR_LONG_SECRET_NAME, LONG_RESOURCE_NAME, MODE_420));
    assertThat(getJobVolumeMounts(), hasVolumeMount(SECRET_NAME + "-volume",
            "/weblogic-operator/config-overrides-secrets/" + SECRET_NAME, true));
    assertThat(getJobVolumeMounts(), hasVolumeMount(VOLUME_NAME_FOR_LONG_SECRET_NAME,
            "/weblogic-operator/config-overrides-secrets/" + LONG_RESOURCE_NAME, true));
  }

  @Test
  void verify_introspectorPodSpec_activeDeadlineSeconds_initial_values() {
    V1JobSpec jobSpec = createJobSpec();

    assertThat(
          getActiveDeadlineSeconds(jobSpec),
        is(TuningParametersStub.INTROSPECTOR_JOB_ACTIVE_DEADLINE_SECONDS));
    assertThat(
        jobSpec.getActiveDeadlineSeconds(), is(TuningParametersStub.INTROSPECTOR_JOB_ACTIVE_DEADLINE_SECONDS));
  }

  private static Long getActiveDeadlineSeconds(V1JobSpec jobSpec) {
    return getTemplateSpec(jobSpec).getActiveDeadlineSeconds();
  }

  private static V1PodSpec getTemplateSpec(V1JobSpec jobSpec) {
    return jobSpec.getTemplate().getSpec();
  }

  @Test
  void verify_introspectorPodSpec_activeDeadlineSeconds_retry_values() {
    int failureCount = domainPresenceInfo.incrementAndGetFailureCount();

    V1JobSpec jobSpec = createJobSpec();

    long expectedActiveDeadlineSeconds =
        TuningParametersStub.INTROSPECTOR_JOB_ACTIVE_DEADLINE_SECONDS
            + (failureCount * JobStepContext.DEFAULT_ACTIVE_DEADLINE_INCREMENT_SECONDS);
    assertThat(getActiveDeadlineSeconds(jobSpec), is(expectedActiveDeadlineSeconds));
    assertThat(jobSpec.getActiveDeadlineSeconds(), is(expectedActiveDeadlineSeconds));
  }

  @Test
  void verify_introspectorPodSpec_activeDeadlineSeconds_domain_overrides_values() {
    configureDomain().withIntrospectorJobActiveDeadlineSeconds(600L);
   
    V1JobSpec jobSpec = createJobSpec();

    assertThat(getActiveDeadlineSeconds(jobSpec), is(600L));
    assertThat(jobSpec.getActiveDeadlineSeconds(), is(600L));
  }

  @Test
  void podTemplate_hasCreateByOperatorLabel() {
    V1JobSpec jobSpec = createJobSpec();

    assertThat(getTemplateLabel(jobSpec, LabelConstants.CREATEDBYOPERATOR_LABEL), equalTo("true"));
  }

  @Test
  void podTemplate_hasDomainUidLabel() {
    V1JobSpec jobSpec = createJobSpec();

    assertThat(getTemplateLabel(jobSpec, LabelConstants.DOMAINUID_LABEL), equalTo(UID));
  }

  @Test
  void podTemplate_hasJobNameLabel() {
    V1JobSpec jobSpec = createJobSpec();

    assertThat(
        getTemplateLabel(jobSpec, LabelConstants.JOBNAME_LABEL),
        equalTo(LegalNames.toJobIntrospectorName(UID)));
  }

  private String getTemplateLabel(V1JobSpec jobSpec, String labelKey) {
    return Optional.ofNullable(jobSpec.getTemplate())
        .map(V1PodTemplateSpec::getMetadata)
        .map(V1ObjectMeta::getLabels)
        .map(m -> m.get(labelKey))
        .orElse(null);
  }

  @Test
  void introspectorPodSpec_alwaysCreatedWithNeverRestartPolicy() {
    configureDomain()
        .withRestartPolicy("Always");
    V1JobSpec jobSpec = createJobSpec();

    assertThat(
        getPodSpec(jobSpec).getRestartPolicy(),
        is("Never"));
  }

  @Test
  void introspectorPodSpec_createdWithoutConfiguredReadinessGates() {
    configureDomain()
        .withReadinessGate(new V1PodReadinessGate().conditionType("www.example.com/feature-1"));
    V1JobSpec jobSpec = createJobSpec();

    assertThat(
        getPodSpec(jobSpec).getReadinessGates(),
        nullValue());
  }

  @Test
  void introspectorPodSpec_createdWithoutConfiguredInitContainers() {
    configureDomain()
        .withInitContainer(
            createContainer(
                "container1", "busybox", "sh", "-c", "echo managed server && sleep 120"));

    V1JobSpec jobSpec = createJobSpec();

    assertThat(
        getPodSpec(jobSpec).getInitContainers(),
        nullValue());
  }

  @Test
  void introspectorPodSpec_createdWithoutConfiguredContainers() {
    configureDomain()
        .withContainer(
            createContainer(
                "container1", "busybox", "sh", "-c", "echo managed server && sleep 120"));

    V1JobSpec jobSpec = createJobSpec();

    assertThat(
        getPodSpec(jobSpec).getContainers(),
        not(hasContainer("container1", "busybox", "sh", "-c", "echo admin server && sleep 120"))
    );
  }

  @Test
  void introspectorPodContainerSpec_hasJobNameAsContainerName() {
    V1JobSpec jobSpec = createJobSpec();

    assertThat(
        getMatchingContainer(domainPresenceInfo, jobSpec).map(V1Container::getName).orElse(null),
        is(JobHelper.createJobName(UID)));
  }

  @Test
  void whenDomainHasContainerSecurityContext_introspectorPodContainersStartupWithIt() {
    configureDomain().withContainerSecurityContext(containerSecurityContext);
    V1JobSpec jobSpec = createJobSpec();

    getContainerStream(jobSpec).forEach(c -> assertThat(c.getSecurityContext(), is(containerSecurityContext)));
  }

  @Test
  void whenNotConfigured_introspectorPodContainers_hasEmptySecurityContext() {
    V1JobSpec jobSpec = createJobSpec();

    getContainerStream(jobSpec).forEach(c -> assertThat(c.getSecurityContext(), is(new V1SecurityContext())));
  }

  @Test
  void whenDomainHasPodSecurityContext_introspectorPodSpecStartupWithIt() {
    configureDomain().withPodSecurityContext(podSecurityContext);
    V1JobSpec jobSpec = createJobSpec();

    assertThat(
        getPodSpec(jobSpec).getSecurityContext(),
        is(podSecurityContext));
  }

  @Test
  void whenNotConfigured_introspectorPodSpec_hasEmptySecurityContext() {
    V1JobSpec jobSpec = createJobSpec();

    assertThat(
        getPodSpec(jobSpec).getSecurityContext(),
        is(new V1PodSecurityContext()));
  }

  @Test
  void whenDomainHasAffinityConfigured_introspectorPodSpecStartupWithIt() {
    configureDomain().withAffinity(podAffinity);
    V1JobSpec jobSpec = createJobSpec();

    assertThat(
        getPodSpec(jobSpec).getAffinity(),
        is(podAffinity));
  }

  @Test
  void whenNotConfigured_introspectorPodSpec_hasNullAffinity() {
    V1JobSpec jobSpec = createJobSpec();

    assertThat(
        getPodSpec(jobSpec).getAffinity(),
        nullValue());
  }

  @Test
  void whenDomainHasNodeSelectorConfigured_introspectorPodSpecStartupWithIt() {
    configureDomain().withNodeSelector("os", "linux");
    V1JobSpec jobSpec = createJobSpec();

    assertThat(
        getPodSpec(jobSpec).getNodeSelector(),
        hasEntry("os", "linux"));
  }

  @Test
  void whenNotConfigured_introspectorPodSpec_hasEmptyNodeSelector() {
    V1JobSpec jobSpec = createJobSpec();

    assertThat(
        getPodSpec(jobSpec).getNodeSelector(),
        is(anEmptyMap()));
  }

  @Test
  void whenDomainHasNodeNameConfigured_introspectorPodSpecStartupWithIt() {
    configureDomain().withNodeName("kube-02");
    V1JobSpec jobSpec = createJobSpec();

    assertThat(
        getPodSpec(jobSpec).getNodeName(),
        is("kube-02"));
  }

  @Test
  void whenNotConfigured_introspectorPodSpec_hasNullNodeName() {
    V1JobSpec jobSpec = createJobSpec();

    assertThat(
        getPodSpec(jobSpec).getNodeName(),
        nullValue());
  }

  @Test
  void whenDomainHasSchedulerNameConfigured_introspectorPodSpecStartupWithIt() {
    configureDomain().withSchedulerName("my-scheduler");
    V1JobSpec jobSpec = createJobSpec();

    assertThat(
        getPodSpec(jobSpec).getSchedulerName(),
        is("my-scheduler"));
  }

  @Test
  void whenNotConfigured_introspectorPodSpec_hasNullSchedulerName() {
    V1JobSpec jobSpec = createJobSpec();

    assertThat(
        getPodSpec(jobSpec).getSchedulerName(),
        nullValue());
  }

  @Test
  void whenDomainHasRuntimeClassNameConfigured_introspectorPodSpecStartupWithIt() {
    configureDomain().withRuntimeClassName("MyRuntimeClass");
    V1JobSpec jobSpec = createJobSpec();

    assertThat(
        getPodSpec(jobSpec).getRuntimeClassName(),
        is("MyRuntimeClass"));
  }

  @Test
  void whenNotConfigured_introspectorPodSpec_hasNullRuntimeClassName() {
    V1JobSpec jobSpec = createJobSpec();

    assertThat(
        getPodSpec(jobSpec).getRuntimeClassName(),
        nullValue());
  }

  @Test
  void whenDomainHasImagePullSecretsConfigured_introspectorPodSpecStartupWithIt() {
    V1LocalObjectReference imagePullSecret = new V1LocalObjectReference().name("secret");
    configureDomain().withDefaultImagePullSecrets(imagePullSecret);

    V1JobSpec jobSpec = createJobSpec();

    assertThat(getPodSpec(jobSpec).getImagePullSecrets(), hasItem(imagePullSecret));
  }

  @Test
  void whenNotConfigured_introspectorPodSpec_hasEmptyImagePullSecrets() {
    V1JobSpec jobSpec = createJobSpec();

    assertThat(
        getPodSpec(jobSpec).getImagePullSecrets(),
        empty());
  }

  @Test
  void whenDomainHasPriorityClassNameConfigured_introspectorPodSpecStartupWithIt() {
    configureDomain().withPriorityClassName("MyPriorityClass");
    V1JobSpec jobSpec = createJobSpec();

    assertThat(
        getPodSpec(jobSpec).getPriorityClassName(),
        is("MyPriorityClass"));
  }

  @Test
  void whenNotConfigured_introspectorPodSpec_hasNullPriorityClassName() {
    V1JobSpec jobSpec = createJobSpec();

    assertThat(
        getPodSpec(jobSpec).getPriorityClassName(),
        nullValue());
  }

  @Test
  void whenDomainHasTolerationsConfigured_introspectorPodSpecStartupWithThem() {
    configureDomain().withToleration(toleration);
    V1JobSpec jobSpec = createJobSpec();

    assertThat(
        getPodSpec(jobSpec).getTolerations(),
        contains(toleration));
  }

  @Test
  void whenNotConfigured_introspectorPodSpec_hasNullTolerations() {
    V1JobSpec jobSpec = createJobSpec();

    assertThat(
        getPodSpec(jobSpec).getTolerations(),
        nullValue());
  }

  @Test
  void whenDomainHasHttpAccessLogInLogHomeConfigured_introspectorPodSpecStartupWithIt() {
    configureDomain().withHttpAccessLogInLogHome(false);
    V1JobSpec jobSpec = createJobSpec();

    assertThat(getMatchingContainerEnv(domainPresenceInfo, jobSpec),
        hasEnvVar(ServerEnvVars.ACCESS_LOG_IN_LOG_HOME, "false")
    );
  }

  @Test
  void whenNotConfigured_introspectorPodSpec_hasTrueAccessLogInLogHomeEnvVar() {
    V1JobSpec jobSpec = createJobSpec();

    assertThat(getMatchingContainerEnv(domainPresenceInfo, jobSpec),
        hasEnvVar(ServerEnvVars.ACCESS_LOG_IN_LOG_HOME, "true")
    );
  }

  @Test
  void whenNoExistingTopologyRunIntrospector() {
    runCreateJob();

    assertThat(job, notNullValue());
  }

  private void runCreateJob() {
    testSupport.doOnCreate(KubernetesTestSupport.JOB, j -> recordJob((V1Job) j));
    testSupport.runSteps(JobHelper.createDomainIntrospectorJobStep(null));
  }

  @Test
  void whenTopologyExistsAndNothingChanged_dontRunIntrospector() {
    defineTopology();

    runCreateJob();

    assertThat(job, nullValue());
  }

  @Test
  void whenIntrospectNotRequested_dontRunIntrospector() {
    defineTopology();

    runCreateJob();

    assertThat(job, nullValue());
  }

  @Test
  void whenIntrospectRequestSet_runIntrospector() {
    defineTopology();
    testSupport.addToPacket(ProcessingConstants.DOMAIN_INTROSPECT_REQUESTED, "123");

    runCreateJob();

    assertThat(job, notNullValue());
  }

  private V1Job job;

  private void recordJob(V1Job job) {
    this.job = job;
  }

  private void defineTopology() {
    WlsDomainConfigSupport configSupport = new WlsDomainConfigSupport("domain");
    configSupport.addWlsServer("admin", 8045);
    configSupport.setAdminServerName("admin");
    testSupport.addToPacket(DOMAIN_TOPOLOGY, configSupport.createDomainConfig());
  }

  private DomainPresenceInfo createDomainPresenceInfo(Domain domain) {
    DomainPresenceInfo domainPresenceInfo = new DomainPresenceInfo(domain);
    configureDomain(domainPresenceInfo)
        .withDefaultServerStartPolicy(ConfigurationConstants.START_NEVER);
    return domainPresenceInfo;
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

  @SuppressWarnings("SameParameterValue")
  private ServerConfigurator configureServer(String serverName) {
    return configureDomain().configureServer(serverName);
  }

  private V1PodSpec getPodSpec(V1JobSpec jobSpec) {
    return jobSpec.getTemplate().getSpec();
  }

  private Optional<V1Container> getMatchingContainer(
      DomainPresenceInfo domainPresenceInfo, V1JobSpec jobSpec) {
    return getContainerStream(jobSpec)
        .filter(c -> hasCreateJobName(c, domainPresenceInfo.getDomainUid()))
        .findFirst();
  }

  private List<V1EnvVar> getMatchingContainerEnv(
      DomainPresenceInfo domainPresenceInfo, V1JobSpec jobSpec) {
    return getMatchingContainer(domainPresenceInfo, jobSpec)
        .map(V1Container::getEnv)
        .orElse(Collections.emptyList());
  }

  private boolean hasCreateJobName(V1Container container, String domainUid) {
    return JobHelper.createJobName(domainUid).equals(container.getName());
  }

  private Stream<V1Container> getContainerStream(V1JobSpec jobSpec) {
    return Optional.ofNullable(jobSpec.getTemplate().getSpec())
          .map(V1PodSpec::getContainers)
          .stream()
          .flatMap(Collection::stream);
  }

  private DomainSpec getConfiguredDomainSpec(DomainConfigurator domainConfigurator)
      throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
    if (getDomainSpec == null) {
      getDomainSpec = DomainConfigurator.class.getDeclaredMethod("getDomainSpec");
      getDomainSpec.setAccessible(true);
    }
    return (DomainSpec) getDomainSpec.invoke(domainConfigurator);
  }

  private static Matcher<Iterable<? super V1EnvVar>> envVarOEVNContains(String val) {
    // OEVN env var contains a comma separated list of env var names
    return hasEnvVarRegEx(OEVN, "(^|.*,)" + val + "($|,.*)");
  }

  // todo add domain uid and created by operator labels to pod template so that they can be watched
  // todo have pod processor able to recognize job-created pods to update domain status
}

// Copyright (c) 2019, 2020, Oracle Corporation and/or its affiliates.
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
import io.kubernetes.client.openapi.models.V1JobSpec;
import io.kubernetes.client.openapi.models.V1LocalObjectReference;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1PodReadinessGate;
import io.kubernetes.client.openapi.models.V1PodSecurityContext;
import io.kubernetes.client.openapi.models.V1PodSpec;
import io.kubernetes.client.openapi.models.V1PodTemplateSpec;
import io.kubernetes.client.openapi.models.V1SecretReference;
import io.kubernetes.client.openapi.models.V1SecurityContext;
import io.kubernetes.client.openapi.models.V1Toleration;
import oracle.kubernetes.operator.LabelConstants;
import oracle.kubernetes.operator.ProcessingConstants;
import oracle.kubernetes.operator.TuningParameters;
import oracle.kubernetes.operator.helpers.JobHelper.DomainIntrospectorJobStepContext;
import oracle.kubernetes.operator.work.Component;
import oracle.kubernetes.operator.work.Packet;
import oracle.kubernetes.weblogic.domain.ClusterConfigurator;
import oracle.kubernetes.weblogic.domain.DomainConfigurator;
import oracle.kubernetes.weblogic.domain.DomainConfiguratorFactory;
import oracle.kubernetes.weblogic.domain.ServerConfigurator;
import oracle.kubernetes.weblogic.domain.model.ConfigurationConstants;
import oracle.kubernetes.weblogic.domain.model.Domain;
import oracle.kubernetes.weblogic.domain.model.DomainSpec;
import oracle.kubernetes.weblogic.domain.model.ServerEnvVars;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static oracle.kubernetes.operator.helpers.Matchers.hasContainer;
import static oracle.kubernetes.operator.helpers.Matchers.hasEnvVar;
import static oracle.kubernetes.operator.helpers.PodHelperTestBase.createAffinity;
import static oracle.kubernetes.operator.helpers.PodHelperTestBase.createConfigMapKeyRefEnvVar;
import static oracle.kubernetes.operator.helpers.PodHelperTestBase.createContainer;
import static oracle.kubernetes.operator.helpers.PodHelperTestBase.createFieldRefEnvVar;
import static oracle.kubernetes.operator.helpers.PodHelperTestBase.createPodSecurityContext;
import static oracle.kubernetes.operator.helpers.PodHelperTestBase.createSecretKeyRefEnvVar;
import static oracle.kubernetes.operator.helpers.PodHelperTestBase.createSecurityContext;
import static oracle.kubernetes.operator.helpers.PodHelperTestBase.createToleration;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.anEmptyMap;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.junit.MatcherAssert.assertThat;

public class JobHelperTest {

  private static final String NS = "ns1";
  private static final String DOMAIN_UID = "JobHelperTestDomain";
  private static final String RAW_VALUE_1 = "find uid1 at $(DOMAIN_HOME)";
  private static final String END_VALUE_1 = "find uid1 at /u01/oracle/user_projects/domains";
  private Method getDomainSpec;
  private final DomainPresenceInfo domainPresenceInfo = createDomainPresenceInfo();
  private final V1PodSecurityContext podSecurityContext = createPodSecurityContext(123L);
  private final V1SecurityContext containerSecurityContext = createSecurityContext(555L);
  private final V1Affinity podAffinity = createAffinity();
  private final V1Toleration toleration = createToleration("key","Eqauls", "value", "NoSchedule");
  private final V1EnvVar configMapKeyRefEnvVar = createConfigMapKeyRefEnvVar("VARIABLE1", "my-env", "VAR1");
  private final V1EnvVar secretKeyRefEnvVar = createSecretKeyRefEnvVar("VARIABLE2", "my-secret", "VAR2");
  private final V1EnvVar fieldRefEnvVar = createFieldRefEnvVar("MY_NODE_IP", "status.hostIP");
  protected List<Memento> mementos = new ArrayList<>();

  /**
   * Setup test environment.
   * @throws Exception if TuningParameterStub fails to install
   */
  @Before
  public void setup() throws Exception {
    mementos.add(TuningParametersStub.install());
  }

  /**
   * Cleanup test environment.
   */
  @After
  public void tearDown() {
    for (Memento memento : mementos) {
      memento.revert();
    }
  }

  @Test
  public void creatingServers_true_whenClusterReplicas_gt_0() {
    configureCluster("cluster1").withReplicas(1);

    assertThat(JobHelper.creatingServers(domainPresenceInfo), equalTo(true));
  }

  @Test
  public void creatingServers_false_whenClusterReplicas_is_0() {
    configureCluster("cluster1").withReplicas(0);

    assertThat(JobHelper.creatingServers(domainPresenceInfo), equalTo(false));
  }

  @Test
  public void creatingServers_true_whenDomainReplicas_gt_0_and_cluster_has_no_replicas() {
    configureDomain().withDefaultReplicaCount(1);

    configureCluster("cluster1");

    assertThat(JobHelper.creatingServers(domainPresenceInfo), equalTo(true));
  }

  @Test
  public void creatingServers_false_whenDomainReplicas_is_0_and_cluster_has_no_replicas() {
    configureDomain().withDefaultReplicaCount(0);

    configureCluster("cluster1");

    assertThat(JobHelper.creatingServers(domainPresenceInfo), equalTo(false));
  }

  @Test
  public void creatingServers_false_when_no_domain_nor_cluster_replicas() {
    configureCluster("cluster1");

    assertThat(JobHelper.creatingServers(domainPresenceInfo), equalTo(false));
  }

  @Test
  public void creatingServers_false_when_noCluster_and_Start_Never_startPolicy() {
    assertThat(JobHelper.creatingServers(domainPresenceInfo), equalTo(false));
  }

  @Test
  public void creatingServers_true_when_noCluster_and_Start_If_Needed_startPolicy() {
    configureDomain()
        .withDefaultServerStartPolicy(ConfigurationConstants.START_IF_NEEDED);

    assertThat(JobHelper.creatingServers(domainPresenceInfo), equalTo(true));
  }

  @Test
  public void creatingServers_true_when_noCluster_and_Start_Always_startPolicy() {
    configureDomain()
        .withDefaultServerStartPolicy(ConfigurationConstants.START_ALWAYS);

    assertThat(JobHelper.creatingServers(domainPresenceInfo), equalTo(true));
  }

  @Test
  public void creatingServers_false_when_server_with_Start_Never_startPolicy() {
    configureServer("managed-server1")
        .withServerStartPolicy(ConfigurationConstants.START_NEVER);

    assertThat(JobHelper.creatingServers(domainPresenceInfo), equalTo(false));
  }

  @Test
  public void creatingServers_true_when_server_with_Start_If_Needed_startPolicy() {
    configureServer("managed-server1")
        .withServerStartPolicy(ConfigurationConstants.START_IF_NEEDED);

    assertThat(JobHelper.creatingServers(domainPresenceInfo), equalTo(true));
  }

  @Test
  public void creatingServers_true_when_server_with_Start_Always_startPolicy() {
    configureServer("managed-server1")
        .withServerStartPolicy(ConfigurationConstants.START_ALWAYS);

    assertThat(JobHelper.creatingServers(domainPresenceInfo), equalTo(true));
  }

  @Test
  public void whenDomainHasEnvironmentItems_introspectorPodStartupWithThem() {
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
  public void introspectorPodStartsWithDefaultUser_Mem_Args_environmentVariable() {
    V1JobSpec jobSpec = createJobSpec();

    assertThat(
        getMatchingContainerEnv(domainPresenceInfo, jobSpec),
        hasEnvVar(
            "USER_MEM_ARGS", "-Djava.security.egd=file:/dev/./urandom"));
  }

  @Test
  public void whenDomainHasEmptyStringUser_Mem_Args_EnvironmentItem_introspectorPodStartupWithIt() {
    configureDomain().withEnvironmentVariable("USER_MEM_ARGS", "");

    V1JobSpec jobSpec = createJobSpec();

    assertThat(
        getMatchingContainerEnv(domainPresenceInfo, jobSpec), hasEnvVar("USER_MEM_ARGS", ""));
  }

  @Test
  public void whenDomainHasEnvironmentItemsWithVariables_introspectorPodStartupWithThem() {
    configureDomain().withEnvironmentVariable("item1", RAW_VALUE_1);

    V1JobSpec jobSpec = createJobSpec();

    assertThat(
        getMatchingContainerEnv(domainPresenceInfo, jobSpec), hasEnvVar("item1", END_VALUE_1));
  }

  private static final String EMPTY_DATA_HOME = "";

  @Test
  public void whenDomainHasEnvironmentVars_introspectorPodStartupVerifyDataHomeEnvNotDefined() {
    DomainConfigurator domainConfigurator = configureDomain();

    V1JobSpec jobSpec = createJobSpec();

    assertThat(getMatchingContainerEnv(domainPresenceInfo, jobSpec),
              not(hasEnvVar(ServerEnvVars.DATA_HOME, EMPTY_DATA_HOME)));
  }

  private static final String OVERRIDE_DATA_DIR = "/u01/data";
  private static final String OVERRIDE_DATA_HOME = OVERRIDE_DATA_DIR + File.separator + DOMAIN_UID;

  @Test
  public void whenDomainHasEnvironmentVars_introspectorPodStartupVerifyDataHomeEnvDefined() {
    DomainConfigurator domainConfigurator = configureDomain().withDataHome(OVERRIDE_DATA_DIR);

    V1JobSpec jobSpec = createJobSpec();

    assertThat(getMatchingContainerEnv(domainPresenceInfo, jobSpec),
            hasEnvVar(ServerEnvVars.DATA_HOME, OVERRIDE_DATA_HOME));
  }

  @Test
  public void whenDomainHasEnvironmentVars_introspectorPodStartupVerifyEmptyDataHome() {
    DomainConfigurator domainConfigurator =
            configureDomain().withDataHome(EMPTY_DATA_HOME);

    V1JobSpec jobSpec = createJobSpec();

    assertThat(getMatchingContainerEnv(domainPresenceInfo, jobSpec),
            not(hasEnvVar(ServerEnvVars.DATA_HOME, EMPTY_DATA_HOME)));
  }

  private static final String NULL_DATA_HOME = null;

  @Test
  public void whenDomainHasEnvironmentVars_introspectorPodStartupVerifyNullDataHome() {
    DomainConfigurator domainConfigurator =
            configureDomain().withDataHome(NULL_DATA_HOME);

    V1JobSpec jobSpec = createJobSpec();

    assertThat(getMatchingContainerEnv(domainPresenceInfo, jobSpec),
            not(hasEnvVar(ServerEnvVars.DATA_HOME, NULL_DATA_HOME)));
  }

  @Test
  public void whenAdminServerHasEnvironmentItems_introspectorPodStartupWithThem() {
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
  }

  @Test
  public void whenDomainHasValueFromEnvironmentItems_introspectorPodStartupWithThem() {
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
  }

  @Test
  public void whenAdminServerHasValueFromEnvironmentItems_introspectorPodStartupWithThem() {
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
  }

  @Test
  public void introspectorPodStartupWithNullAdminUsernamePasswordEnvVarValues() {
    V1JobSpec jobSpec = createJobSpec();

    assertThat(
        getMatchingContainerEnv(domainPresenceInfo, jobSpec),
        allOf(hasEnvVar("ADMIN_USERNAME", null), hasEnvVar("ADMIN_PASSWORD", null)));
  }

  @Test
  public void
      whenDomainHasEnvironmentItemsWithVariable_createIntrospectorPodShouldNotChangeItsValue()
          throws NoSuchMethodException, IllegalAccessException, InvocationTargetException {
    DomainConfigurator domainConfigurator =
        configureDomain().withEnvironmentVariable("item1", RAW_VALUE_1);

    createJobSpec();

    assertThat(
        getConfiguredDomainSpec(domainConfigurator).getEnv(), hasEnvVar("item1", RAW_VALUE_1));
  }

  @Test
  public void verify_introspectorPodSpec_activeDeadlineSeconds_initial_values() {
    V1JobSpec jobSpec = createJobSpec();

    assertThat(
        jobSpec.getTemplate().getSpec().getActiveDeadlineSeconds(),
        is(TuningParametersStub.INTROSPECTOR_JOB_ACTIVE_DEADLINE_SECONDS));
    assertThat(
        jobSpec.getActiveDeadlineSeconds(), is(TuningParametersStub.INTROSPECTOR_JOB_ACTIVE_DEADLINE_SECONDS));
  }

  @Test
  public void verify_introspectorPodSpec_activeDeadlineSeconds_retry_values() {
    int failureCount = domainPresenceInfo.incrementAndGetFailureCount();

    V1JobSpec jobSpec = createJobSpec();

    long expectedActiveDeadlineSeconds =
        TuningParametersStub.INTROSPECTOR_JOB_ACTIVE_DEADLINE_SECONDS
            + (failureCount * JobStepContext.DEFAULT_ACTIVE_DEADLINE_INCREMENT_SECONDS);
    assertThat(
        jobSpec.getTemplate().getSpec().getActiveDeadlineSeconds(),
        is(expectedActiveDeadlineSeconds));
    assertThat(jobSpec.getActiveDeadlineSeconds(), is(expectedActiveDeadlineSeconds));
  }

  @Test
  public void podTemplate_hasCreateByOperatorLabel() {
    V1JobSpec jobSpec = createJobSpec();

    assertThat(getTemplateLabel(jobSpec, LabelConstants.CREATEDBYOPERATOR_LABEL), equalTo("true"));
  }

  @Test
  public void podTemplate_hasDomainUidLabel() {
    V1JobSpec jobSpec = createJobSpec();

    assertThat(getTemplateLabel(jobSpec, LabelConstants.DOMAINUID_LABEL), equalTo(DOMAIN_UID));
  }

  @Test
  public void podTemplate_hasJobNameLabel() {
    V1JobSpec jobSpec = createJobSpec();

    assertThat(
        getTemplateLabel(jobSpec, LabelConstants.JOBNAME_LABEL),
        equalTo(LegalNames.toJobIntrospectorName(DOMAIN_UID)));
  }

  private String getTemplateLabel(V1JobSpec jobSpec, String labelKey) {
    return Optional.ofNullable(jobSpec.getTemplate())
        .map(V1PodTemplateSpec::getMetadata)
        .map(V1ObjectMeta::getLabels)
        .map(m -> m.get(labelKey))
        .orElse(null);
  }

  @Test
  public void introspectorPodSpec_alwaysCreatedWithNeverRestartPolicy() {
    configureDomain()
        .withRestartPolicy("Always");
    V1JobSpec jobSpec = createJobSpec();

    assertThat(
        getPodSpec(jobSpec).getRestartPolicy(),
        is("Never"));
  }

  @Test
  public void introspectorPodSpec_createdWithoutConfiguredReadinessGates() {
    configureDomain()
        .withReadinessGate(new V1PodReadinessGate().conditionType("www.example.com/feature-1"));
    V1JobSpec jobSpec = createJobSpec();

    assertThat(
        getPodSpec(jobSpec).getReadinessGates(),
        nullValue());
  }

  @Test
  public void introspectorPodSpec_createdWithoutConfiguredInitContainers() {
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
  public void introspectorPodSpec_createdWithoutConfiguredContainers() {
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
  public void introspectorPodContainerSpec_hasJobNameAsContainerName() {
    V1JobSpec jobSpec = createJobSpec();

    assertThat(
        getMatchingContainer(domainPresenceInfo, jobSpec).get().getName(),
        is(JobHelper.createJobName(DOMAIN_UID)));
  }

  @Test
  public void whenDomainHasContainerSecurityContext_introspectorPodContainersStartupWithIt() {
    configureDomain().withContainerSecurityContext(containerSecurityContext);
    V1JobSpec jobSpec = createJobSpec();

    getContainerStream(jobSpec).forEach(c -> assertThat(c.getSecurityContext(), is(containerSecurityContext)));
  }

  @Test
  public void whenNotConfigured_introspectorPodContainers_hasEmptySecurityContext() {
    V1JobSpec jobSpec = createJobSpec();

    getContainerStream(jobSpec).forEach(c -> assertThat(c.getSecurityContext(), is(new V1SecurityContext())));
  }

  @Test
  public void whenDomainHasPodSecurityContext_introspectorPodSpecStartupWithIt() {
    configureDomain().withPodSecurityContext(podSecurityContext);
    V1JobSpec jobSpec = createJobSpec();

    assertThat(
        getPodSpec(jobSpec).getSecurityContext(),
        is(podSecurityContext));
  }

  @Test
  public void whenNotConfigured_introspectorPodSpec_hasEmptySecurityContext() {
    V1JobSpec jobSpec = createJobSpec();

    assertThat(
        getPodSpec(jobSpec).getSecurityContext(),
        is(new V1PodSecurityContext()));
  }

  @Test
  public void whenDomainHasAffinityConfigured_introspectorPodSpecStartupWithIt() {
    configureDomain().withAffinity(podAffinity);
    V1JobSpec jobSpec = createJobSpec();

    assertThat(
        getPodSpec(jobSpec).getAffinity(),
        is(podAffinity));
  }

  @Test
  public void whenNotConfigured_introspectorPodSpec_hasNullAffinity() {
    V1JobSpec jobSpec = createJobSpec();

    assertThat(
        getPodSpec(jobSpec).getAffinity(),
        nullValue());
  }

  @Test
  public void whenDomainHasNodeSelectorConfigured_introspectorPodSpecStartupWithIt() {
    configureDomain().withNodeSelector("os", "linux");
    V1JobSpec jobSpec = createJobSpec();

    assertThat(
        getPodSpec(jobSpec).getNodeSelector(),
        hasEntry("os", "linux"));
  }

  @Test
  public void whenNotConfigured_introspectorPodSpec_hasEmptyNodeSelector() {
    V1JobSpec jobSpec = createJobSpec();

    assertThat(
        getPodSpec(jobSpec).getNodeSelector(),
        is(anEmptyMap()));
  }

  @Test
  public void whenDomainHasNodeNameConfigured_introspectorPodSpecStartupWithIt() {
    configureDomain().withNodeName("kube-02");
    V1JobSpec jobSpec = createJobSpec();

    assertThat(
        getPodSpec(jobSpec).getNodeName(),
        is("kube-02"));
  }

  @Test
  public void whenNotConfigured_introspectorPodSpec_hasNullNodeName() {
    V1JobSpec jobSpec = createJobSpec();

    assertThat(
        getPodSpec(jobSpec).getNodeName(),
        nullValue());
  }

  @Test
  public void whenDomainHasSchedulerNameConfigured_introspectorPodSpecStartupWithIt() {
    configureDomain().withSchedulerName("my-scheduler");
    V1JobSpec jobSpec = createJobSpec();

    assertThat(
        getPodSpec(jobSpec).getSchedulerName(),
        is("my-scheduler"));
  }

  @Test
  public void whenNotConfigured_introspectorPodSpec_hasNullSchedulerName() {
    V1JobSpec jobSpec = createJobSpec();

    assertThat(
        getPodSpec(jobSpec).getSchedulerName(),
        nullValue());
  }

  @Test
  public void whenDomainHasRuntimeClassNameConfigured_introspectorPodSpecStartupWithIt() {
    configureDomain().withRuntimeClassName("MyRuntimeClass");
    V1JobSpec jobSpec = createJobSpec();

    assertThat(
        getPodSpec(jobSpec).getRuntimeClassName(),
        is("MyRuntimeClass"));
  }

  @Test
  public void whenNotConfigured_introspectorPodSpec_hasNullRuntimeClassName() {
    V1JobSpec jobSpec = createJobSpec();

    assertThat(
        getPodSpec(jobSpec).getRuntimeClassName(),
        nullValue());
  }

  @Test
  public void whenDomainHasImagePullSecretsConfigured_introspectorPodSpecStartupWithIt() {
    V1LocalObjectReference imagePullSecret = new V1LocalObjectReference().name("secret");
    configureDomain().withDefaultImagePullSecrets(imagePullSecret);

    V1JobSpec jobSpec = createJobSpec();

    assertThat(getPodSpec(jobSpec).getImagePullSecrets(), hasItem(imagePullSecret));
  }

  @Test
  public void whenNotConfigured_introspectorPodSpec_hasEmptyImagePullSecrets() {
    V1JobSpec jobSpec = createJobSpec();

    assertThat(
        getPodSpec(jobSpec).getImagePullSecrets(),
        empty());
  }

  @Test
  public void whenDomainHasPriorityClassNameConfigured_introspectorPodSpecStartupWithIt() {
    configureDomain().withPriorityClassName("MyPriorityClass");
    V1JobSpec jobSpec = createJobSpec();

    assertThat(
        getPodSpec(jobSpec).getPriorityClassName(),
        is("MyPriorityClass"));
  }

  @Test
  public void whenNotConfigured_introspectorPodSpec_hasNullPriorityClassName() {
    V1JobSpec jobSpec = createJobSpec();

    assertThat(
        getPodSpec(jobSpec).getPriorityClassName(),
        nullValue());
  }

  @Test
  public void whenDomainHasTolerationsConfigured_introspectorPodSpecStartupWithThem() {
    configureDomain().withToleration(toleration);
    V1JobSpec jobSpec = createJobSpec();

    assertThat(
        getPodSpec(jobSpec).getTolerations(),
        contains(toleration));
  }

  @Test
  public void whenNotConfigured_introspectorPodSpec_hasNullTolerations() {
    V1JobSpec jobSpec = createJobSpec();

    assertThat(
        getPodSpec(jobSpec).getTolerations(),
        nullValue());
  }

  private DomainPresenceInfo createDomainPresenceInfo() {
    DomainPresenceInfo domainPresenceInfo =
        new DomainPresenceInfo(
            new Domain()
                .withMetadata(new V1ObjectMeta().namespace(NS))
                .withSpec(
                    new DomainSpec()
                        .withDomainUid(DOMAIN_UID)
                        .withWebLogicCredentialsSecret(
                            new V1SecretReference().name("webLogicCredentialsSecretName"))));
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
    return Optional.ofNullable(jobSpec.getTemplate().getSpec().getContainers()).stream()
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

  // todo add domain uid and created by operator labels to pod template so that they can be watched
  // todo have pod processor able to recognize job-created pods to update domain status
}

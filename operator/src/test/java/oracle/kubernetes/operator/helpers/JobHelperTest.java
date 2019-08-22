// Copyright 2019, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helpers;

import com.meterware.simplestub.Memento;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import io.kubernetes.client.models.V1Container;
import io.kubernetes.client.models.V1EnvVar;
import io.kubernetes.client.models.V1JobSpec;
import io.kubernetes.client.models.V1ObjectMeta;
import io.kubernetes.client.models.V1PodTemplateSpec;
import io.kubernetes.client.models.V1SecretReference;
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
import org.hamcrest.Matcher;
import org.hamcrest.junit.MatcherAssert;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.hasItem;
import static org.junit.Assert.assertThat;

public class JobHelperTest {

  private static final String NS = "ns1";
  private static final String DOMAIN_UID = "JobHelperTestDomain";
  private static final String RAW_VALUE_1 = "find uid1 at $(DOMAIN_HOME)";
  private static final String END_VALUE_1 = "find uid1 at /u01/oracle/user_projects/domains";
  private Method getDomainSpec;
  private final DomainPresenceInfo domainPresenceInfo = createDomainPresenceInfo();
  protected List<Memento> mementos = new ArrayList<>();

  private static Matcher<Iterable<? super V1EnvVar>> hasEnvVar(String name, String value) {
    return hasItem(new V1EnvVar().name(name).value(value));
  }

  @Before
  public void setup() throws Exception {
    mementos.add(TuningParametersStub.install());
  }

  @After
  public void tearDown() throws Exception {
    for (Memento memento : mementos) memento.revert();
  }

  @Test
  public void creatingServers_true_whenClusterReplicas_gt_0() {
    configureCluster(domainPresenceInfo, "cluster1").withReplicas(1);

    assertThat(JobHelper.creatingServers(domainPresenceInfo), equalTo(true));
  }

  @Test
  public void creatingServers_false_whenClusterReplicas_is_0() {
    configureCluster(domainPresenceInfo, "cluster1").withReplicas(0);

    assertThat(JobHelper.creatingServers(domainPresenceInfo), equalTo(false));
  }

  @Test
  public void creatingServers_true_whenDomainReplicas_gt_0_and_cluster_has_no_replicas() {
    configureDomain(domainPresenceInfo).withDefaultReplicaCount(1);

    configureCluster(domainPresenceInfo, "cluster1");

    assertThat(JobHelper.creatingServers(domainPresenceInfo), equalTo(true));
  }

  @Test
  public void creatingServers_false_whenDomainReplicas_is_0_and_cluster_has_no_replicas() {
    configureDomain(domainPresenceInfo).withDefaultReplicaCount(0);

    configureCluster(domainPresenceInfo, "cluster1");

    assertThat(JobHelper.creatingServers(domainPresenceInfo), equalTo(false));
  }

  @Test
  public void creatingServers_false_when_no_domain_nor_cluster_replicas() {
    configureCluster(domainPresenceInfo, "cluster1");

    assertThat(JobHelper.creatingServers(domainPresenceInfo), equalTo(false));
  }

  @Test
  public void creatingServers_false_when_noCluster_and_Start_Never_startPolicy() {
    assertThat(JobHelper.creatingServers(domainPresenceInfo), equalTo(false));
  }

  @Test
  public void creatingServers_true_when_noCluster_and_Start_If_Needed_startPolicy() {
    configureDomain(domainPresenceInfo)
        .withDefaultServerStartPolicy(ConfigurationConstants.START_IF_NEEDED);

    assertThat(JobHelper.creatingServers(domainPresenceInfo), equalTo(true));
  }

  @Test
  public void creatingServers_true_when_noCluster_and_Start_Always_startPolicy() {
    configureDomain(domainPresenceInfo)
        .withDefaultServerStartPolicy(ConfigurationConstants.START_ALWAYS);

    assertThat(JobHelper.creatingServers(domainPresenceInfo), equalTo(true));
  }

  @Test
  public void creatingServers_false_when_server_with_Start_Never_startPolicy() {
    configureServer(domainPresenceInfo, "managed-server1")
        .withServerStartPolicy(ConfigurationConstants.START_NEVER);

    assertThat(JobHelper.creatingServers(domainPresenceInfo), equalTo(false));
  }

  @Test
  public void creatingServers_true_when_server_with_Start_If_Needed_startPolicy() {
    configureServer(domainPresenceInfo, "managed-server1")
        .withServerStartPolicy(ConfigurationConstants.START_IF_NEEDED);

    assertThat(JobHelper.creatingServers(domainPresenceInfo), equalTo(true));
  }

  @Test
  public void creatingServers_true_when_server_with_Start_Always_startPolicy() {
    configureServer(domainPresenceInfo, "managed-server1")
        .withServerStartPolicy(ConfigurationConstants.START_ALWAYS);

    assertThat(JobHelper.creatingServers(domainPresenceInfo), equalTo(true));
  }

  @Test
  public void whenDomainHasEnvironmentItems_introspectorPodStartupWithThem() {
    configureDomain(domainPresenceInfo)
        .withEnvironmentVariable("item1", "value1")
        .withEnvironmentVariable("item2", "value2")
        .withEnvironmentVariable("WL_HOME", "/u01/custom_wl_home/")
        .withEnvironmentVariable("MW_HOME", "/u01/custom_mw_home/");

    V1JobSpec jobSpec = createJobSpec();

    MatcherAssert.assertThat(
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
        new DomainIntrospectorJobStepContext(domainPresenceInfo, packet);
    return domainIntrospectorJobStepContext.createJobSpec(TuningParameters.getInstance());
  }

  @Test
  public void introspectorPodStartsWithDefaultUser_Mem_Args_environmentVariable() {
    V1JobSpec jobSpec = createJobSpec();

    MatcherAssert.assertThat(
        getMatchingContainerEnv(domainPresenceInfo, jobSpec),
        hasEnvVar(
            "USER_MEM_ARGS", "-XX:+UseContainerSupport -Djava.security.egd=file:/dev/./urandom"));
  }

  @Test
  public void whenDomainHasUser_Mem_Args_EnvironmentItem_introspectorPodStartupWithIt() {
    configureDomain(domainPresenceInfo)
        .withEnvironmentVariable("USER_MEM_ARGS", "-XX:+UseContainerSupport");

    V1JobSpec jobSpec = createJobSpec();

    MatcherAssert.assertThat(
        getMatchingContainerEnv(domainPresenceInfo, jobSpec),
        hasEnvVar("USER_MEM_ARGS", "-XX:+UseContainerSupport"));
  }

  @Test
  public void whenDomainHasEmptyStringUser_Mem_Args_EnvironmentItem_introspectorPodStartupWithIt() {
    configureDomain(domainPresenceInfo).withEnvironmentVariable("USER_MEM_ARGS", "");

    V1JobSpec jobSpec = createJobSpec();

    MatcherAssert.assertThat(
        getMatchingContainerEnv(domainPresenceInfo, jobSpec), hasEnvVar("USER_MEM_ARGS", ""));
  }

  @Test
  public void whenDomainHasEnvironmentItemsWithVariables_introspectorPodStartupWithThem() {
    configureDomain(domainPresenceInfo).withEnvironmentVariable("item1", RAW_VALUE_1);

    V1JobSpec jobSpec = createJobSpec();

    MatcherAssert.assertThat(
        getMatchingContainerEnv(domainPresenceInfo, jobSpec), hasEnvVar("item1", END_VALUE_1));
  }

  @Test
  public void whenAdminServerHasEnvironmentItems_introspectorPodStartupWithThem() {
    configureDomain(domainPresenceInfo)
        .withEnvironmentVariable("item1", "domain-value1")
        .withEnvironmentVariable("item2", "domain-value2")
        .configureAdminServer()
        .withEnvironmentVariable("item2", "admin-value2")
        .withEnvironmentVariable("item3", "admin-value3");

    V1JobSpec jobSpec = createJobSpec();

    MatcherAssert.assertThat(
        getMatchingContainerEnv(domainPresenceInfo, jobSpec),
        allOf(
            hasEnvVar("item1", "domain-value1"),
            hasEnvVar("item2", "admin-value2"),
            hasEnvVar("item3", "admin-value3")));
  }

  @Test
  public void introspectorPodStartupWithNullAdminUsernamePasswordEnvVarValues() {
    V1JobSpec jobSpec = createJobSpec();

    MatcherAssert.assertThat(
        getMatchingContainerEnv(domainPresenceInfo, jobSpec),
        allOf(hasEnvVar("ADMIN_USERNAME", null), hasEnvVar("ADMIN_PASSWORD", null)));
  }

  @Test
  public void
      whenDomainHasEnvironmentItemsWithVariable_createIntrospectorPodShouldNotChangeItsValue()
          throws NoSuchMethodException, IllegalAccessException, InvocationTargetException {
    DomainConfigurator domainConfigurator =
        configureDomain(domainPresenceInfo).withEnvironmentVariable("item1", RAW_VALUE_1);

    createJobSpec();

    MatcherAssert.assertThat(
        getConfiguredDomainSpec(domainConfigurator).getEnv(), hasEnvVar("item1", RAW_VALUE_1));
  }

  @Test
  public void verify_introspectorPodSpec_activeDeadlineSeconds_initial_values() {
    V1JobSpec jobSpec = createJobSpec();

    MatcherAssert.assertThat(
        jobSpec.getTemplate().getSpec().getActiveDeadlineSeconds(),
        is(TuningParametersStub.INTROSPECTOR_JOB_ACTIVE_DEADLINE_SECONDS));
    MatcherAssert.assertThat(
        jobSpec.getActiveDeadlineSeconds(), is(TuningParametersStub.INTROSPECTOR_JOB_ACTIVE_DEADLINE_SECONDS));
  }

  @Test
  public void verify_introspectorPodSpec_activeDeadlineSeconds_retry_values() {
    int failureCount = domainPresenceInfo.incrementAndGetFailureCount();

    V1JobSpec jobSpec = createJobSpec();

    long expectedActiveDeadlineSeconds =
        TuningParametersStub.INTROSPECTOR_JOB_ACTIVE_DEADLINE_SECONDS
            + (failureCount * JobStepContext.DEFAULT_ACTIVE_DEADLINE_INCREMENT_SECONDS);
    MatcherAssert.assertThat(
        jobSpec.getTemplate().getSpec().getActiveDeadlineSeconds(),
        is(expectedActiveDeadlineSeconds));
    MatcherAssert.assertThat(jobSpec.getActiveDeadlineSeconds(), is(expectedActiveDeadlineSeconds));
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

  private DomainConfigurator configureDomain(DomainPresenceInfo domainPresenceInfo) {
    return DomainConfiguratorFactory.forDomain(domainPresenceInfo.getDomain());
  }

  @SuppressWarnings("SameParameterValue")
  private ClusterConfigurator configureCluster(
      DomainPresenceInfo domainPresenceInfo, String clusterName) {
    return configureDomain(domainPresenceInfo).configureCluster(clusterName);
  }

  @SuppressWarnings("SameParameterValue")
  private ServerConfigurator configureServer(
      DomainPresenceInfo domainPresenceInfo, String serverName) {
    return configureDomain(domainPresenceInfo).configureServer(serverName);
  }

  private List<V1EnvVar> getMatchingContainerEnv(
      DomainPresenceInfo domainPresenceInfo, V1JobSpec jobSpec) {
    return getContainerStream(jobSpec)
        .filter(c -> hasCreateJobName(c, domainPresenceInfo.getDomainUid()))
        .findFirst()
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

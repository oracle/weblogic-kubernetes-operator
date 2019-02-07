// Copyright 2019, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helpers;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.hasItem;
import static org.junit.Assert.assertThat;

import io.kubernetes.client.models.V1Container;
import io.kubernetes.client.models.V1EnvVar;
import io.kubernetes.client.models.V1JobSpec;
import io.kubernetes.client.models.V1ObjectMeta;
import io.kubernetes.client.models.V1SecretReference;
import java.util.List;
import oracle.kubernetes.operator.ProcessingConstants;
import oracle.kubernetes.operator.TuningParameters;
import oracle.kubernetes.operator.helpers.JobHelper.DomainIntrospectorJobStepContext;
import oracle.kubernetes.operator.work.Component;
import oracle.kubernetes.operator.work.Packet;
import oracle.kubernetes.weblogic.domain.ClusterConfigurator;
import oracle.kubernetes.weblogic.domain.DomainConfigurator;
import oracle.kubernetes.weblogic.domain.DomainConfiguratorFactory;
import oracle.kubernetes.weblogic.domain.ServerConfigurator;
import oracle.kubernetes.weblogic.domain.v2.ConfigurationConstants;
import oracle.kubernetes.weblogic.domain.v2.Domain;
import oracle.kubernetes.weblogic.domain.v2.DomainSpec;
import org.hamcrest.Matcher;
import org.hamcrest.junit.MatcherAssert;
import org.junit.Test;

public class JobHelperTest {

  private static final String NS = "ns1";
  private static final String DOMAIN_UID = "JobHelperTestDomain";
  private static final String RAW_VALUE_1 = "find uid1 at $(DOMAIN_HOME)";
  private static final String END_VALUE_1 = "find uid1 at /u01/oracle/user_projects/domains";

  @Test
  public void creatingServers_true_whenClusterReplicas_gt_0() {
    DomainPresenceInfo domainPresenceInfo = createDomainPresenceInfo();

    configureCluster(domainPresenceInfo, "cluster1").withReplicas(1);

    assertThat(JobHelper.creatingServers(domainPresenceInfo), equalTo(true));
  }

  @Test
  public void creatingServers_false_whenClusterReplicas_is_0() {
    DomainPresenceInfo domainPresenceInfo = createDomainPresenceInfo();

    configureCluster(domainPresenceInfo, "cluster1").withReplicas(0);

    assertThat(JobHelper.creatingServers(domainPresenceInfo), equalTo(false));
  }

  @Test
  public void creatingServers_true_whenDomainReplicas_gt_0_and_cluster_has_no_replicas() {
    DomainPresenceInfo domainPresenceInfo = createDomainPresenceInfo();

    configureDomain(domainPresenceInfo).withDefaultReplicaCount(1);

    configureCluster(domainPresenceInfo, "cluster1");

    assertThat(JobHelper.creatingServers(domainPresenceInfo), equalTo(true));
  }

  @Test
  public void creatingServers_false_whenDomainReplicas_is_0_and_cluster_has_no_replicas() {
    DomainPresenceInfo domainPresenceInfo = createDomainPresenceInfo();

    configureDomain(domainPresenceInfo).withDefaultReplicaCount(0);

    configureCluster(domainPresenceInfo, "cluster1");

    assertThat(JobHelper.creatingServers(domainPresenceInfo), equalTo(false));
  }

  @Test
  public void creatingServers_false_when_no_domain_nor_cluster_replicas() {
    DomainPresenceInfo domainPresenceInfo = createDomainPresenceInfo();

    configureCluster(domainPresenceInfo, "cluster1");

    assertThat(JobHelper.creatingServers(domainPresenceInfo), equalTo(false));
  }

  @Test
  public void creatingServers_false_when_noCluster_and_START_NEVER_startPolicy() {
    DomainPresenceInfo domainPresenceInfo = createDomainPresenceInfo();

    assertThat(JobHelper.creatingServers(domainPresenceInfo), equalTo(false));
  }

  @Test
  public void creatingServers_true_when_noCluster_and_START_IF_NEEDED_startPolicy() {
    DomainPresenceInfo domainPresenceInfo = createDomainPresenceInfo();

    configureDomain(domainPresenceInfo)
        .withDefaultServerStartPolicy(ConfigurationConstants.START_IF_NEEDED);

    assertThat(JobHelper.creatingServers(domainPresenceInfo), equalTo(true));
  }

  @Test
  public void creatingServers_true_when_noCluster_and_START_ALWAYS_startPolicy() {
    DomainPresenceInfo domainPresenceInfo = createDomainPresenceInfo();

    configureDomain(domainPresenceInfo)
        .withDefaultServerStartPolicy(ConfigurationConstants.START_ALWAYS);

    assertThat(JobHelper.creatingServers(domainPresenceInfo), equalTo(true));
  }

  @Test
  public void creatingServers_false_when_server_with_START_NEVER_startPolicy() {
    DomainPresenceInfo domainPresenceInfo = createDomainPresenceInfo();

    configureServer(domainPresenceInfo, "managed-server1")
        .withServerStartPolicy(ConfigurationConstants.START_NEVER);

    assertThat(JobHelper.creatingServers(domainPresenceInfo), equalTo(false));
  }

  @Test
  public void creatingServers_true_when_server_with_START_IF_NEEDED_startPolicy() {
    DomainPresenceInfo domainPresenceInfo = createDomainPresenceInfo();

    configureServer(domainPresenceInfo, "managed-server1")
        .withServerStartPolicy(ConfigurationConstants.START_IF_NEEDED);

    assertThat(JobHelper.creatingServers(domainPresenceInfo), equalTo(true));
  }

  @Test
  public void creatingServers_true_when_server_with_START_AWLAYS_startPolicy() {
    DomainPresenceInfo domainPresenceInfo = createDomainPresenceInfo();

    configureServer(domainPresenceInfo, "managed-server1")
        .withServerStartPolicy(ConfigurationConstants.START_ALWAYS);

    assertThat(JobHelper.creatingServers(domainPresenceInfo), equalTo(true));
  }

  @Test
  public void whenDomainHasEnvironmentItems_introspectorPodStartupWithThem() {
    DomainPresenceInfo domainPresenceInfo = createDomainPresenceInfo();

    configureDomain(domainPresenceInfo)
        .withEnvironmentVariable("item1", "value1")
        .withEnvironmentVariable("item2", "value2")
        .withEnvironmentVariable("WL_HOME", "/u01/custom_wl_home/")
        .withEnvironmentVariable("MW_HOME", "/u01/custom_mw_home/");

    Packet packet = new Packet();
    packet
        .getComponents()
        .put(ProcessingConstants.DOMAIN_COMPONENT_NAME, Component.createFor(domainPresenceInfo));
    DomainIntrospectorJobStepContext domainIntrospectorJobStepContext =
        new DomainIntrospectorJobStepContext(domainPresenceInfo, packet);
    V1JobSpec jobSpec =
        domainIntrospectorJobStepContext.createJobSpec(TuningParameters.getInstance());

    MatcherAssert.assertThat(
        getContainerFromJobSpec(jobSpec, domainPresenceInfo.getDomainUID()).getEnv(),
        allOf(
            hasEnvVar("item1", "value1"),
            hasEnvVar("item2", "value2"),
            hasEnvVar("WL_HOME", "/u01/custom_wl_home/"),
            hasEnvVar("MW_HOME", "/u01/custom_mw_home/")));
  }

  @Test
  public void introspectorPodStartsWithDefaultUSER_MEM_ARGS_environmentVariable() {
    DomainPresenceInfo domainPresenceInfo = createDomainPresenceInfo();

    Packet packet = new Packet();
    packet
        .getComponents()
        .put(ProcessingConstants.DOMAIN_COMPONENT_NAME, Component.createFor(domainPresenceInfo));
    DomainIntrospectorJobStepContext domainIntrospectorJobStepContext =
        new DomainIntrospectorJobStepContext(domainPresenceInfo, packet);
    V1JobSpec jobSpec =
        domainIntrospectorJobStepContext.createJobSpec(TuningParameters.getInstance());

    MatcherAssert.assertThat(
        getContainerFromJobSpec(jobSpec, domainPresenceInfo.getDomainUID()).getEnv(),
        allOf(hasEnvVar("USER_MEM_ARGS", "-Djava.security.egd=file:/dev/./urandom")));
  }

  @Test
  public void whenDomainHasUSER_MEM_ARGS_EnvironmentItem_introspectorPodStartupWithIt() {
    DomainPresenceInfo domainPresenceInfo = createDomainPresenceInfo();

    configureDomain(domainPresenceInfo)
        .withEnvironmentVariable("USER_MEM_ARGS", "-Xms64m -Xmx256m");

    Packet packet = new Packet();
    packet
        .getComponents()
        .put(ProcessingConstants.DOMAIN_COMPONENT_NAME, Component.createFor(domainPresenceInfo));
    DomainIntrospectorJobStepContext domainIntrospectorJobStepContext =
        new DomainIntrospectorJobStepContext(domainPresenceInfo, packet);
    V1JobSpec jobSpec =
        domainIntrospectorJobStepContext.createJobSpec(TuningParameters.getInstance());

    MatcherAssert.assertThat(
        getContainerFromJobSpec(jobSpec, domainPresenceInfo.getDomainUID()).getEnv(),
        allOf(hasEnvVar("USER_MEM_ARGS", "-Xms64m -Xmx256m")));
  }

  @Test
  public void whenDomainHasEmptyStringUSER_MEM_ARGS_EnvironmentItem_introspectorPodStartupWithIt() {
    DomainPresenceInfo domainPresenceInfo = createDomainPresenceInfo();

    configureDomain(domainPresenceInfo).withEnvironmentVariable("USER_MEM_ARGS", "");

    Packet packet = new Packet();
    packet
        .getComponents()
        .put(ProcessingConstants.DOMAIN_COMPONENT_NAME, Component.createFor(domainPresenceInfo));
    DomainIntrospectorJobStepContext domainIntrospectorJobStepContext =
        new DomainIntrospectorJobStepContext(domainPresenceInfo, packet);
    V1JobSpec jobSpec =
        domainIntrospectorJobStepContext.createJobSpec(TuningParameters.getInstance());

    MatcherAssert.assertThat(
        getContainerFromJobSpec(jobSpec, domainPresenceInfo.getDomainUID()).getEnv(),
        allOf(hasEnvVar("USER_MEM_ARGS", "")));
  }

  @Test
  public void whenDomainHasEnvironmentItemsWithVariables_introspectorPodStartupWithThem() {
    DomainPresenceInfo domainPresenceInfo = createDomainPresenceInfo();

    configureDomain(domainPresenceInfo).withEnvironmentVariable("item1", RAW_VALUE_1);

    Packet packet = new Packet();
    packet
        .getComponents()
        .put(ProcessingConstants.DOMAIN_COMPONENT_NAME, Component.createFor(domainPresenceInfo));
    DomainIntrospectorJobStepContext domainIntrospectorJobStepContext =
        new DomainIntrospectorJobStepContext(domainPresenceInfo, packet);
    V1JobSpec jobSpec =
        domainIntrospectorJobStepContext.createJobSpec(TuningParameters.getInstance());

    MatcherAssert.assertThat(
        getContainerFromJobSpec(jobSpec, domainPresenceInfo.getDomainUID()).getEnv(),
        allOf(hasEnvVar("item1", END_VALUE_1)));
  }

  @Test
  public void whenAdminServerHasEnvironmentItems_introspectorPodStartupWithThem() {
    DomainPresenceInfo domainPresenceInfo = createDomainPresenceInfo();

    configureDomain(domainPresenceInfo)
        .withEnvironmentVariable("item1", "domain-value1")
        .withEnvironmentVariable("item2", "domain-value2")
        .configureAdminServer()
        .withEnvironmentVariable("item2", "admin-value2")
        .withEnvironmentVariable("item3", "admin-value3");

    Packet packet = new Packet();
    packet
        .getComponents()
        .put(ProcessingConstants.DOMAIN_COMPONENT_NAME, Component.createFor(domainPresenceInfo));
    DomainIntrospectorJobStepContext domainIntrospectorJobStepContext =
        new DomainIntrospectorJobStepContext(domainPresenceInfo, packet);
    V1JobSpec jobSpec =
        domainIntrospectorJobStepContext.createJobSpec(TuningParameters.getInstance());

    MatcherAssert.assertThat(
        getContainerFromJobSpec(jobSpec, domainPresenceInfo.getDomainUID()).getEnv(),
        allOf(
            hasEnvVar("item1", "domain-value1"),
            hasEnvVar("item2", "admin-value2"),
            hasEnvVar("item3", "admin-value3")));
  }

  @Test
  public void introspectorPodStartupWithNullAdminUsernamePasswordEnvVarValues() {
    DomainPresenceInfo domainPresenceInfo = createDomainPresenceInfo();

    Packet packet = new Packet();
    packet
        .getComponents()
        .put(ProcessingConstants.DOMAIN_COMPONENT_NAME, Component.createFor(domainPresenceInfo));
    DomainIntrospectorJobStepContext domainIntrospectorJobStepContext =
        new DomainIntrospectorJobStepContext(domainPresenceInfo, packet);
    V1JobSpec jobSpec =
        domainIntrospectorJobStepContext.createJobSpec(TuningParameters.getInstance());

    MatcherAssert.assertThat(
        getContainerFromJobSpec(jobSpec, domainPresenceInfo.getDomainUID()).getEnv(),
        allOf(hasEnvVar("ADMIN_USERNAME", null), hasEnvVar("ADMIN_PASSWORD", null)));
  }

  @Test
  public void verify_introspectorPodSpec_activeDeadlineSeconds_initial_values() {
    DomainPresenceInfo domainPresenceInfo = createDomainPresenceInfo();

    Packet packet = new Packet();
    packet
        .getComponents()
        .put(ProcessingConstants.DOMAIN_COMPONENT_NAME, Component.createFor(domainPresenceInfo));
    DomainIntrospectorJobStepContext domainIntrospectorJobStepContext =
        new DomainIntrospectorJobStepContext(domainPresenceInfo, packet);
    V1JobSpec jobSpec =
        domainIntrospectorJobStepContext.createJobSpec(TuningParameters.getInstance());

    MatcherAssert.assertThat(
        jobSpec.getTemplate().getSpec().getActiveDeadlineSeconds(),
        is(JobStepContext.DEFAULT_ACTIVE_DEADLINE_SECONDS));
    MatcherAssert.assertThat(
        jobSpec.getActiveDeadlineSeconds(), is(JobStepContext.DEFAULT_ACTIVE_DEADLINE_SECONDS));
  }

  @Test
  public void verify_introspectorPodSpec_activeDeadlineSeconds_retry_values() {
    DomainPresenceInfo domainPresenceInfo = createDomainPresenceInfo();
    int failureCount = domainPresenceInfo.incrementAndGetFailureCount();

    Packet packet = new Packet();
    packet
        .getComponents()
        .put(ProcessingConstants.DOMAIN_COMPONENT_NAME, Component.createFor(domainPresenceInfo));
    DomainIntrospectorJobStepContext domainIntrospectorJobStepContext =
        new DomainIntrospectorJobStepContext(domainPresenceInfo, packet);
    V1JobSpec jobSpec =
        domainIntrospectorJobStepContext.createJobSpec(TuningParameters.getInstance());

    long expectedActiveDeadlineSeconds =
        JobStepContext.DEFAULT_ACTIVE_DEADLINE_SECONDS
            + (failureCount * JobStepContext.DEFAULT_ACTIVE_DEADLINE_INCREMENT_SECONDS);
    MatcherAssert.assertThat(
        jobSpec.getTemplate().getSpec().getActiveDeadlineSeconds(),
        is(expectedActiveDeadlineSeconds));
    MatcherAssert.assertThat(jobSpec.getActiveDeadlineSeconds(), is(expectedActiveDeadlineSeconds));
  }

  private DomainPresenceInfo createDomainPresenceInfo() {
    DomainPresenceInfo domainPresenceInfo =
        new DomainPresenceInfo(
            new Domain()
                .withMetadata(new V1ObjectMeta().namespace(NS))
                .withSpec(
                    new DomainSpec()
                        .withDomainUID(DOMAIN_UID)
                        .withWebLogicCredentialsSecret(
                            new V1SecretReference().name("webLogicCredentialsSecretName"))));
    configureDomain(domainPresenceInfo)
        .withDefaultServerStartPolicy(ConfigurationConstants.START_NEVER);
    return domainPresenceInfo;
  }

  private DomainConfigurator configureDomain(DomainPresenceInfo domainPresenceInfo) {
    return DomainConfiguratorFactory.forDomain(domainPresenceInfo.getDomain());
  }

  private ClusterConfigurator configureCluster(
      DomainPresenceInfo domainPresenceInfo, String clusterName) {
    return configureDomain(domainPresenceInfo).configureCluster(clusterName);
  }

  private ServerConfigurator configureServer(
      DomainPresenceInfo domainPresenceInfo, String serverName) {
    return configureDomain(domainPresenceInfo).configureServer(serverName);
  }

  private V1Container getContainerFromJobSpec(V1JobSpec jobSpec, String domainUID) {
    List<V1Container> containersList = jobSpec.getTemplate().getSpec().getContainers();
    if (containersList != null) {
      for (V1Container container : containersList) {
        if (JobHelper.createJobName(domainUID).equals(container.getName())) {
          return container;
        }
      }
    }
    return null;
  }

  static Matcher<Iterable<? super V1EnvVar>> hasEnvVar(String name, String value) {
    return hasItem(new V1EnvVar().name(name).value(value));
  }
}

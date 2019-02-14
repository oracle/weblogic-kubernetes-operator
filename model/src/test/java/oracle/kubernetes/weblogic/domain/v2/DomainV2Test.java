// Copyright 2018, 2019, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.weblogic.domain.v2;

import static oracle.kubernetes.operator.KubernetesConstants.DEFAULT_IMAGE;
import static oracle.kubernetes.operator.KubernetesConstants.IFNOTPRESENT_IMAGEPULLPOLICY;
import static oracle.kubernetes.weblogic.domain.v2.ConfigurationConstants.START_ALWAYS;
import static oracle.kubernetes.weblogic.domain.v2.ConfigurationConstants.START_NEVER;
import static org.hamcrest.Matchers.*;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;

import io.kubernetes.client.custom.Quantity;
import io.kubernetes.client.models.*;
import java.io.IOException;
import java.util.Map;
import oracle.kubernetes.weblogic.domain.AdminServerConfigurator;
import oracle.kubernetes.weblogic.domain.DomainConfigurator;
import oracle.kubernetes.weblogic.domain.DomainTestBase;
import org.hamcrest.Matcher;
import org.junit.Before;
import org.junit.Test;

public class DomainV2Test extends DomainTestBase {

  private static final int DEFAULT_REPLICA_LIMIT = 0;
  private static final String DOMAIN_V2_SAMPLE_YAML = "v2/domain-sample.yaml";
  private static final String DOMAIN_V2_SAMPLE_YAML_2 = "v2/domain-sample-2.yaml";
  private static final String DOMAIN_V2_SAMPLE_YAML_3 = "v2/domain-sample-3.yaml";
  private static final int INITIAL_DELAY = 17;
  private static final int TIMEOUT = 23;
  private static final int PERIOD = 5;
  private static final V1Sysctl DOMAIN_SYSCTL =
      new V1Sysctl().name("net.ipv4.route.min_pmtu").value("552");
  private static final V1Sysctl CLUSTER_SYSCTL =
      new V1Sysctl().name("kernel.shm_rmid_forced").value("0");

  @Before
  public void setUp() {
    configureDomain(domain);
  }

  @Override
  protected DomainConfigurator configureDomain(Domain domain) {
    return new DomainV2Configurator(domain);
  }

  @Test
  public void whenClusterNotConfiguredAndNoDomainReplicaCount_countIsZero() {
    assertThat(domain.getReplicaCount("nosuchcluster"), equalTo(0));
  }

  @Test
  public void whenClusterNotConfiguredAndDomainHasReplicaCount_useIt() {
    configureDomain(domain).withDefaultReplicaCount(3);

    assertThat(domain.getReplicaCount("nosuchcluster"), equalTo(3));
  }

  @Test
  public void whenStartupPolicyUnspecified_adminServerStartsUp() {
    assertThat(domain.getAdminServerSpec().shouldStart(0), is(true));
  }

  @Test
  public void whenStartupPolicyUnspecified_nonClusteredServerStartsUp() {
    assertThat(domain.getServer("server1", null).shouldStart(0), is(true));
  }

  @Test
  public void whenStartupPolicyUnspecified_clusteredServerStartsUpIfLimitNotReached() {
    configureCluster("cluster1").withReplicas(3);

    assertThat(domain.getServer("server1", null).shouldStart(1), is(true));
  }

  @Test
  public void whenStartupPolicyUnspecified_clusteredServerDoesNotStartUpIfLimitReached() {
    configureCluster("cluster1").withReplicas(3);

    assertThat(domain.getServer("server1", "cluster1").shouldStart(4), is(false));
  }

  @Test
  public void whenStartupPolicyNever_nonClusteredServerDoesNotStartUp() {
    configureDomain(domain).withDefaultServerStartPolicy(START_NEVER);

    assertThat(domain.getServer("server1", null).shouldStart(0), is(false));
  }

  @Test
  public void whenStartupPolicyAlways_clusteredServerStartsUpEvenIfLimitReached() {
    configureDomain(domain).withDefaultServerStartPolicy(START_ALWAYS);
    configureCluster("cluster1").withReplicas(3);

    assertThat(domain.getServer("server1", "cluster1").shouldStart(4), is(true));
  }

  @Test
  public void whenAdminServerChannelsNotDefined_exportedNamesIsEmpty() {
    assertThat(domain.getAdminServerChannelNames(), empty());
  }

  @Test
  public void whenAdminServerChannelsDefined_returnChannelNames() {
    AdminServerConfigurator configurator = configureDomain(domain).configureAdminServer();
    AdminService adminService = configurator.configureAdminService();
    adminService.withChannel("channel1", 0);
    adminService.withChannel("channel2", 1);

    assertThat(domain.getAdminServerChannelNames(), containsInAnyOrder("channel1", "channel2"));
  }

  @Test
  public void whenServerStartStateConfiguredOnClusterAndServer_useServerSetting() {
    configureCluster("cluster1").withServerStartState("cluster");
    configureServer("server1").withServerStartState("server");

    assertThat(domain.getServer("server1", "cluster1").getDesiredState(), equalTo("server"));
  }

  @Test
  public void whenServerStartPolicyAlwaysConfiguredOnlyOnDomain_startServer() {
    configureDomain(domain).withDefaultServerStartPolicy(ConfigurationConstants.START_ALWAYS);
    configureServer("server1");

    assertThat(domain.getServer("server1", "cluster1").shouldStart(0), is(true));
  }

  @Test
  public void whenServerStartPolicyNever_dontStartServer() {
    configureServer("server1").withServerStartPolicy(START_NEVER);

    assertThat(domain.getServer("server1", "cluster1").shouldStart(0), is(false));
  }

  @Test
  public void whenServerStartPolicyAlways_startServer() {
    configureServer("server1").withServerStartPolicy(ConfigurationConstants.START_ALWAYS);

    assertThat(domain.getServer("server1", "cluster1").shouldStart(0), is(true));
  }

  @Test
  public void whenNonClusteredServerStartPolicyUndefined_startServer() {
    assertThat(domain.getServer("server1", null).shouldStart(0), is(true));
  }

  @Test
  public void whenUnconfiguredClusterHasDefaultNumberOfReplicas_dontStartServer() {
    assertThat(domain.getServer("server1", "cls1").shouldStart(DEFAULT_REPLICA_LIMIT), is(false));
  }

  @Test
  public void whenClusteredServerStartPolicyInheritedAndNeedMoreServers_startServer() {
    configureCluster("cluster1").withReplicas(5);

    assertThat(domain.getServer("server1", "cluster1").shouldStart(4), is(true));
  }

  @Test
  public void whenClusteredServerStartPolicyIfNeededAndDontNeedMoreServers_dontStartServer() {
    configureServer("server1").withServerStartPolicy(ConfigurationConstants.START_IF_NEEDED);
    configureCluster("cluster1").withReplicas(5);

    assertThat(domain.getServer("server1", "cluster1").shouldStart(5), is(false));
  }

  @Test
  public void whenDomainStartPolicyNever_ignoreServerSettings() {
    configureDomain(domain).withDefaultServerStartPolicy(ConfigurationConstants.START_NEVER);
    configureServer("server1").withServerStartPolicy(ConfigurationConstants.START_ALWAYS);

    assertThat(domain.getServer("server1", "cluster1").shouldStart(0), is(false));
  }

  @Test
  public void whenClusterStartPolicyNever_ignoreServerSettings() {
    configureCluster("cluster1").withServerStartPolicy(ConfigurationConstants.START_NEVER);
    configureServer("server1").withServerStartPolicy(ConfigurationConstants.START_ALWAYS);

    assertThat(domain.getServer("server1", "cluster1").shouldStart(0), is(false));
  }

  @Test
  public void whenDomainStartPolicyAdminOnly_dontStartManagedServer() {
    configureDomain(domain).withDefaultServerStartPolicy(ConfigurationConstants.START_ADMIN_ONLY);
    configureServer("server1").withServerStartPolicy(ConfigurationConstants.START_ALWAYS);

    assertThat(domain.getServer("server1", "cluster1").shouldStart(0), is(false));
  }

  @Test
  public void whenDomainStartPolicyAdminOnlyAndAdminServerNever_dontStartAdminServer() {
    configureDomain(domain).withDefaultServerStartPolicy(ConfigurationConstants.START_ADMIN_ONLY);
    configureAdminServer().withServerStartPolicy(ConfigurationConstants.START_NEVER);

    assertThat(domain.getAdminServerSpec().shouldStart(0), is(false));
  }

  @Test
  public void whenDomainStartPolicyAdminOnlyAndAdminServerIfNeeded_startAdminServer() {
    configureDomain(domain).withDefaultServerStartPolicy(ConfigurationConstants.START_ADMIN_ONLY);
    configureAdminServer().withServerStartPolicy(ConfigurationConstants.START_IF_NEEDED);

    assertThat(domain.getAdminServerSpec().shouldStart(0), is(true));
  }

  @Test
  public void whenEnvironmentConfiguredOnMultipleLevels_useCombination() {
    configureDomain(domain)
        .withEnvironmentVariable("name1", "domain")
        .withEnvironmentVariable("name2", "domain");
    configureCluster("cluster1")
        .withEnvironmentVariable("name2", "cluster")
        .withEnvironmentVariable("name3", "cluster")
        .withEnvironmentVariable("name4", "cluster");
    configureServer("server1").withEnvironmentVariable("name4", "server");

    assertThat(
        domain.getServer("server1", "cluster1").getEnvironmentVariables(),
        containsInAnyOrder(
            envVar("name1", "domain"),
            envVar("name2", "cluster"),
            envVar("name3", "cluster"),
            envVar("name4", "server")));
  }

  private V1EnvVar envVar(String name, String value) {
    return new V1EnvVar().name(name).value(value);
  }

  @Test
  public void livenessProbeSettings_returnsConfiguredValues() {
    configureServer(SERVER1).withLivenessProbeSettings(INITIAL_DELAY, TIMEOUT, PERIOD);
    ServerSpec spec = domain.getServer(SERVER1, CLUSTER_NAME);

    assertThat(spec.getLivenessProbe().getInitialDelaySeconds(), equalTo(INITIAL_DELAY));
    assertThat(spec.getLivenessProbe().getTimeoutSeconds(), equalTo(TIMEOUT));
    assertThat(spec.getLivenessProbe().getPeriodSeconds(), equalTo(PERIOD));
  }

  @Test
  public void whenLivenessProbeConfiguredOnMultipleLevels_useCombination() {
    configureDomain(domain).withDefaultLivenessProbeSettings(INITIAL_DELAY, -2, -3);
    configureCluster(CLUSTER_NAME).withLivenessProbeSettings(null, TIMEOUT, -4);
    configureServer(SERVER1).withLivenessProbeSettings(null, null, PERIOD);

    ServerSpec spec = domain.getServer(SERVER1, CLUSTER_NAME);

    assertThat(spec.getLivenessProbe().getInitialDelaySeconds(), equalTo(INITIAL_DELAY));
    assertThat(spec.getLivenessProbe().getTimeoutSeconds(), equalTo(TIMEOUT));
    assertThat(spec.getLivenessProbe().getPeriodSeconds(), equalTo(PERIOD));
  }

  @Test
  public void readinessProbeSettings_returnsConfiguredValues() {
    configureServer(SERVER1).withReadinessProbeSettings(INITIAL_DELAY, TIMEOUT, PERIOD);
    ServerSpec spec = domain.getServer(SERVER1, CLUSTER_NAME);

    assertThat(spec.getReadinessProbe().getInitialDelaySeconds(), equalTo(INITIAL_DELAY));
    assertThat(spec.getReadinessProbe().getTimeoutSeconds(), equalTo(TIMEOUT));
    assertThat(spec.getReadinessProbe().getPeriodSeconds(), equalTo(PERIOD));
  }

  @Test
  public void whenReadinessProbeConfiguredOnMultipleLevels_useCombination() {
    configureDomain(domain).withDefaultReadinessProbeSettings(INITIAL_DELAY, -2, -3);
    configureCluster(CLUSTER_NAME).withReadinessProbeSettings(null, TIMEOUT, -4);
    configureServer(SERVER1).withReadinessProbeSettings(null, null, PERIOD);

    ServerSpec spec = domain.getServer(SERVER1, CLUSTER_NAME);

    assertThat(spec.getReadinessProbe().getInitialDelaySeconds(), equalTo(INITIAL_DELAY));
    assertThat(spec.getReadinessProbe().getTimeoutSeconds(), equalTo(TIMEOUT));
    assertThat(spec.getReadinessProbe().getPeriodSeconds(), equalTo(PERIOD));
  }

  @Test
  public void whenDomainsAreConfiguredAlike_objectsAreEqual() {
    Domain domain1 = createDomain();

    configureDomain(domain).configureCluster("cls1");
    configureDomain(domain1).configureCluster("cls1");

    assertThat(domain, equalTo(domain1));
  }

  @Test
  public void whenNodeSelectorConfiguredOnMultipleLevels_useCombination() {
    configureDomain(domain)
        .withNodeSelector("key1", "domain")
        .withNodeSelector("key2", "domain")
        .withNodeSelector("key3", "domain");
    configureCluster(CLUSTER_NAME)
        .withNodeSelector("key2", "cluser")
        .withNodeSelector("key3", "cluser");
    configureServer(SERVER1).withNodeSelector("key3", "server");
    configureAdminServer().withNodeSelector("key2", "admin").withNodeSelector("key3", "admin");

    ServerSpec serverSpec = domain.getServer(SERVER1, CLUSTER_NAME);

    assertThat(domain.getAdminServerSpec().getNodeSelectors(), hasEntry("key1", "domain"));
    assertThat(domain.getAdminServerSpec().getNodeSelectors(), hasEntry("key2", "admin"));
    assertThat(domain.getAdminServerSpec().getNodeSelectors(), hasEntry("key3", "admin"));

    assertThat(serverSpec.getNodeSelectors(), hasEntry("key1", "domain"));
    assertThat(serverSpec.getNodeSelectors(), hasEntry("key2", "cluser"));
    assertThat(serverSpec.getNodeSelectors(), hasEntry("key3", "server"));
  }

  @Test
  public void whenRestartVersionConfiguredOnMultipleLevels_useCombination() {
    configureDomain(domain).withRestartVersion("1");
    configureCluster(CLUSTER_NAME).withRestartVersion("2");
    configureServer(SERVER1).withRestartVersion("3");
    configureAdminServer().withRestartVersion("4");

    ServerSpec clusteredServer = domain.getServer(SERVER1, CLUSTER_NAME);
    ServerSpec nonClusteredServerWithRestartVersion = domain.getServer(SERVER1, null);
    ServerSpec nonClusteredServerNoRestartVersion = domain.getServer("anyServer", null);
    ServerSpec adminServer = domain.getAdminServerSpec();

    assertThat(clusteredServer.getDomainRestartVersion(), is("1"));
    assertThat(clusteredServer.getClusterRestartVersion(), is("2"));
    assertThat(clusteredServer.getServerRestartVersion(), is("3"));

    assertThat(nonClusteredServerWithRestartVersion.getDomainRestartVersion(), is("1"));
    assertThat(nonClusteredServerWithRestartVersion.getClusterRestartVersion(), nullValue());
    assertThat(nonClusteredServerWithRestartVersion.getServerRestartVersion(), is("3"));

    assertThat(nonClusteredServerNoRestartVersion.getDomainRestartVersion(), is("1"));
    assertThat(nonClusteredServerNoRestartVersion.getClusterRestartVersion(), nullValue());
    assertThat(nonClusteredServerNoRestartVersion.getServerRestartVersion(), nullValue());

    assertThat(adminServer.getDomainRestartVersion(), is("1"));
    assertThat(adminServer.getClusterRestartVersion(), nullValue());
    assertThat(adminServer.getServerRestartVersion(), is("4"));
  }

  @Test
  public void whenResourceRequirementsConfiguredOnDomain() {
    configureDomainWithResourceRequirements(domain);
    assertThat(
        domain.getSpec().getResources().getRequests(), hasResourceQuantity("memory", "64Mi"));
    assertThat(domain.getSpec().getResources().getRequests(), hasResourceQuantity("cpu", "250m"));
    assertThat(domain.getSpec().getResources().getLimits(), hasResourceQuantity("memory", "128Mi"));
    assertThat(domain.getSpec().getResources().getLimits(), hasResourceQuantity("cpu", "500m"));
  }

  @Test
  public void whenResourceRequirementsConfiguredOnClusterOverrideDomain() {
    configureDomainWithResourceRequirements(domain);
    V1ResourceRequirements msResourceReq = domain.getServer("any", CLUSTER_NAME).getResources();

    // Since the "any" server is not specified in domain, it will take all values from CLUSTER_NAME
    assertThat(msResourceReq.getRequests(), hasResourceQuantity("memory", "128Mi"));
    assertThat(msResourceReq.getRequests(), hasResourceQuantity("cpu", "250m"));
    assertThat(msResourceReq.getLimits(), hasResourceQuantity("memory", "256Mi"));
    assertThat(msResourceReq.getLimits(), hasResourceQuantity("cpu", "500m"));
  }

  @Test
  public void whenResourceRequirementsConfiguredOnManagedServerOverrideClusterAndDomain() {
    configureDomainWithResourceRequirements(domain);
    V1ResourceRequirements ms1ResourceReq = domain.getServer(SERVER1, CLUSTER_NAME).getResources();

    // Since SERVER1 is specified in domain and it overrides only memory requirement of 512Mi
    // Only the following assertion should be different from the cluster defined values
    assertThat(ms1ResourceReq.getLimits(), hasResourceQuantity("memory", "512Mi"));

    // The rest of the resource requirements should be identical to the ones defined in the cluster
    assertThat(ms1ResourceReq.getRequests(), hasResourceQuantity("memory", "128Mi"));
    assertThat(ms1ResourceReq.getRequests(), hasResourceQuantity("cpu", "250m"));
    assertThat(ms1ResourceReq.getLimits(), hasResourceQuantity("cpu", "500m"));
  }

  @Test
  public void whenResourceRequirementsConfiguredOnAdminServerOverrideClusterAndDomain() {
    configureDomainWithResourceRequirements(domain);
    V1ResourceRequirements asResourceReq = domain.getAdminServerSpec().getResources();

    // Admin server specify request of cpu: 500m and Limit cpu: 800m
    assertThat(asResourceReq.getRequests(), hasResourceQuantity("cpu", "500m"));
    assertThat(asResourceReq.getLimits(), hasResourceQuantity("cpu", "0.8"));

    // The rest should be the same as the domain requirements
    assertThat(asResourceReq.getRequests(), hasResourceQuantity("memory", "64Mi"));
    assertThat(asResourceReq.getLimits(), hasResourceQuantity("memory", "128Mi"));
  }

  private void configureDomainWithResourceRequirements(Domain domain) {
    configureDomain(domain)
        .withRequestRequirement("memory", "64Mi")
        .withRequestRequirement("cpu", "250m")
        .withLimitRequirement("memory", "128Mi")
        .withLimitRequirement("cpu", "500m");

    configureCluster(CLUSTER_NAME)
        .withRequestRequirement("memory", "128Mi")
        .withLimitRequirement("memory", "256Mi");

    configureServer(SERVER1).withLimitRequirement("memory", "512Mi");

    configureAdminServer()
        .withRequestRequirement("cpu", "500m")
        .withLimitRequirement("cpu", "0.8"); // Same as 800 millicores
  }

  @Test
  public void whenPodSecurityContextConfiguredOnManagedServerOverrideClusterAndDomain() {
    configureDomainWithPodSecurityContext(domain);
    V1PodSecurityContext ms1PodSecCtx =
        domain.getServer(SERVER1, CLUSTER_NAME).getPodSecurityContext();

    // Server1 only defines runAsGroup = 422L and SELinuxOption level = server, role = slave,
    assertThat(ms1PodSecCtx.getRunAsGroup(), is(422L));
    assertThat(ms1PodSecCtx.getSeLinuxOptions().getLevel(), is("server"));
    assertThat(ms1PodSecCtx.getSeLinuxOptions().getRole(), is("slave"));

    // Since at the server level the type and user are not defined those should be null
    assertThat(ms1PodSecCtx.getSeLinuxOptions().getType(), nullValue());
    assertThat(ms1PodSecCtx.getSeLinuxOptions().getUser(), nullValue());

    // These are inherited from the cluster
    assertThat(ms1PodSecCtx.getSysctls(), contains(CLUSTER_SYSCTL));
    assertThat(ms1PodSecCtx.isRunAsNonRoot(), is(true));

    // The following are not defined either in domain, cluster or server, so they should be null
    assertThat(ms1PodSecCtx.getRunAsUser(), nullValue());
    assertThat(ms1PodSecCtx.getFsGroup(), nullValue());
    assertThat(ms1PodSecCtx.getSupplementalGroups(), nullValue());
  }

  @Test
  public void whenPodSecurityContextConfiguredOnClusterOverrideDomain() {
    configureDomainWithPodSecurityContext(domain);
    V1PodSecurityContext msPodSecCtx =
        domain.getServer("any", CLUSTER_NAME).getPodSecurityContext();

    // Since "any" is not defined in the domain, it will take the values from CLUSTER_NAME
    assertThat(msPodSecCtx.getRunAsGroup(), is(421L));
    assertThat(msPodSecCtx.getSysctls(), contains(CLUSTER_SYSCTL));
    assertThat(msPodSecCtx.isRunAsNonRoot(), is(true));
    assertThat(msPodSecCtx.getSeLinuxOptions().getLevel(), is("cluster"));
    assertThat(msPodSecCtx.getSeLinuxOptions().getRole(), is("admin"));
    assertThat(msPodSecCtx.getSeLinuxOptions().getType(), is("admin"));
    assertThat(msPodSecCtx.getRunAsUser(), nullValue());

    // The following are not defined either in domain, cluster or server, so they should be null
    assertThat(msPodSecCtx.getRunAsUser(), nullValue());
    assertThat(msPodSecCtx.getFsGroup(), nullValue());
    assertThat(msPodSecCtx.getSupplementalGroups(), nullValue());
  }

  @Test
  public void whenPodSecurityContextConfiguredOnAdminServerOverrideClusterAndDomain() {
    configureDomainWithPodSecurityContext(domain);
    V1PodSecurityContext asPodSecCtx = domain.getAdminServerSpec().getPodSecurityContext();

    // Since admin server only defines runAsNonRoot = false
    assertThat(asPodSecCtx.isRunAsNonRoot(), is(false));

    // The rest should be identical to the domain
    assertThat(asPodSecCtx.getRunAsGroup(), is(420L));
    assertThat(asPodSecCtx.getSysctls(), contains(DOMAIN_SYSCTL));
    assertThat(asPodSecCtx.getSeLinuxOptions().getLevel(), is("domain"));
    assertThat(asPodSecCtx.getSeLinuxOptions().getRole(), is("admin"));
    assertThat(asPodSecCtx.getSeLinuxOptions().getUser(), is("weblogic"));
    assertThat(asPodSecCtx.getSeLinuxOptions().getType(), nullValue());
    assertThat(asPodSecCtx.getRunAsUser(), nullValue());

    // The following are not defined either in domain or admin server, should be null
    assertThat(asPodSecCtx.getRunAsUser(), nullValue());
    assertThat(asPodSecCtx.getFsGroup(), nullValue());
    assertThat(asPodSecCtx.getSupplementalGroups(), nullValue());
  }

  private void configureDomainWithPodSecurityContext(Domain domain) {
    configureDomain(domain)
        .withPodSecurityContext(
            new V1PodSecurityContext()
                .runAsGroup(420L)
                .addSysctlsItem(DOMAIN_SYSCTL)
                .seLinuxOptions(
                    new V1SELinuxOptions().level("domain").role("admin").user("weblogic"))
                .runAsNonRoot(true));

    configureCluster(CLUSTER_NAME)
        .withPodSecurityContext(
            new V1PodSecurityContext()
                .runAsGroup(421L)
                .addSysctlsItem(CLUSTER_SYSCTL)
                .seLinuxOptions(
                    new V1SELinuxOptions()
                        .level("cluster")
                        .role("admin")
                        .type("admin")
                        .user("weblogic"))
                .runAsNonRoot(true));

    configureServer(SERVER1)
        .withPodSecurityContext(
            new V1PodSecurityContext()
                .runAsGroup(422L)
                .seLinuxOptions(new V1SELinuxOptions().level("server").role("slave")));

    configureAdminServer().withPodSecurityContext(new V1PodSecurityContext().runAsNonRoot(false));
  }

  @Test
  public void whenContainerSecurityContextConfiguredOnManagedServerOverrideClusterAndDomain() {
    configureDomainWithContainerSecurityContext(domain);
    V1SecurityContext ms1ContainerSecSpec =
        domain.getServer(SERVER1, CLUSTER_NAME).getContainerSecurityContext();

    // Since SERVER1 only overrides runAsGroup = 422, Capabilities to be SYS_TIME
    assertThat(ms1ContainerSecSpec.getRunAsGroup(), is(422L));
    assertThat(ms1ContainerSecSpec.isAllowPrivilegeEscalation(), is(false));
    assertThat(
        ms1ContainerSecSpec.getCapabilities().getAdd(), contains("SYS_TIME", "CHOWN", "SYS_BOOT"));
    assertThat(ms1ContainerSecSpec.getSeLinuxOptions().getLevel(), is("server"));
    assertThat(ms1ContainerSecSpec.getSeLinuxOptions().getRole(), is("slave"));
    assertThat(ms1ContainerSecSpec.getSeLinuxOptions().getType(), nullValue());
    assertThat(ms1ContainerSecSpec.getRunAsUser(), nullValue());
  }

  @Test
  public void whenContainerSecurityContextConfiguredOnClusterOverrideDomain() {
    configureDomainWithContainerSecurityContext(domain);
    V1SecurityContext ms2ContainerSecSpec =
        domain.getServer("any", CLUSTER_NAME).getContainerSecurityContext();

    // Since "any" is not defined in the domain, it will take the values from CLUSTER_NAME
    assertThat(ms2ContainerSecSpec.getRunAsGroup(), is(421L));
    assertThat(ms2ContainerSecSpec.isAllowPrivilegeEscalation(), is(false));
    assertThat(
        ms2ContainerSecSpec.getCapabilities().getAdd(), contains("SYS_TIME", "CHOWN", "SYS_BOOT"));
    assertThat(ms2ContainerSecSpec.getSeLinuxOptions().getLevel(), is("cluster"));
    assertThat(ms2ContainerSecSpec.getSeLinuxOptions().getRole(), is("admin"));
    assertThat(ms2ContainerSecSpec.getSeLinuxOptions().getType(), is("admin"));

    // The following are not defined either in domain, cluster or server, so they should be null
    assertThat(ms2ContainerSecSpec.getRunAsUser(), nullValue());
    assertThat(ms2ContainerSecSpec.isPrivileged(), nullValue());
  }

  @Test
  public void whenContainerSecurityContextConfiguredOnAdminServerOverrideClusterAndDomain() {
    configureDomainWithContainerSecurityContext(domain);
    V1SecurityContext asContainerSecSpec =
        domain.getAdminServerSpec().getContainerSecurityContext();

    // Since admin server only defines runAsNonRoot = false
    assertThat(asContainerSecSpec.isRunAsNonRoot(), is(false));

    // The rest should be identical to the domain
    assertThat(asContainerSecSpec.getRunAsGroup(), is(420L));
    assertThat(asContainerSecSpec.isAllowPrivilegeEscalation(), is(false));
    assertThat(asContainerSecSpec.getCapabilities().getAdd(), contains("CHOWN", "SYS_BOOT"));
    assertThat(asContainerSecSpec.getSeLinuxOptions().getLevel(), is("domain"));
    assertThat(asContainerSecSpec.getSeLinuxOptions().getRole(), is("admin"));
    assertThat(asContainerSecSpec.getSeLinuxOptions().getUser(), is("weblogic"));

    // The following are not defined either in domain or admin server, so they should be null
    assertThat(asContainerSecSpec.getSeLinuxOptions().getType(), nullValue());
    assertThat(asContainerSecSpec.getRunAsUser(), nullValue());
    assertThat(asContainerSecSpec.isPrivileged(), nullValue());
  }

  private void configureDomainWithContainerSecurityContext(Domain domain) {
    configureDomain(domain)
        .withContainerSecurityContext(
            new V1SecurityContext()
                .runAsGroup(420L)
                .allowPrivilegeEscalation(false)
                .capabilities(new V1Capabilities().addAddItem("CHOWN").addAddItem("SYS_BOOT"))
                .seLinuxOptions(
                    new V1SELinuxOptions().level("domain").role("admin").user("weblogic"))
                .runAsNonRoot(true));

    configureCluster(CLUSTER_NAME)
        .withContainerSecurityContext(
            new V1SecurityContext()
                .runAsGroup(421L)
                .capabilities(new V1Capabilities().addAddItem("SYS_TIME"))
                .seLinuxOptions(
                    new V1SELinuxOptions()
                        .level("cluster")
                        .role("admin")
                        .type("admin")
                        .user("weblogic"))
                .runAsNonRoot(true));

    configureServer(SERVER1)
        .withContainerSecurityContext(
            new V1SecurityContext()
                .runAsGroup(422L)
                .seLinuxOptions(new V1SELinuxOptions().level("server").role("slave")));

    configureAdminServer()
        .withContainerSecurityContext(new V1SecurityContext().runAsNonRoot(false));
  }

  @Test
  public void whenDomainsHaveDifferentClusters_objectsAreNotEqual() {
    Domain domain1 = createDomain();

    configureDomain(domain).configureCluster("cls1").withReplicas(2);
    configureDomain(domain1).configureCluster("cls1").withReplicas(3);

    assertThat(domain, not(equalTo(domain1)));
  }

  @Test
  public void whenDomainReadFromYaml_unconfiguredServerHasDomainDefaults() throws IOException {
    Domain domain = readDomain(DOMAIN_V2_SAMPLE_YAML);
    ServerSpec serverSpec = domain.getServer("server0", null);

    assertThat(serverSpec.getImage(), equalTo(DEFAULT_IMAGE));
    assertThat(serverSpec.getImagePullPolicy(), equalTo(IFNOTPRESENT_IMAGEPULLPOLICY));
    assertThat(serverSpec.getImagePullSecrets().get(0).getName(), equalTo("pull-secret1"));
    assertThat(serverSpec.getImagePullSecrets().get(1).getName(), equalTo("pull-secret2"));
    assertThat(serverSpec.getEnvironmentVariables(), contains(envVar("var1", "value0")));
    assertThat(serverSpec.getConfigOverrides(), equalTo("overrides-config-map"));
    assertThat(
        serverSpec.getConfigOverrideSecrets(),
        containsInAnyOrder("overrides-secret-1", "overrides-secret-2"));
    assertThat(serverSpec.getDesiredState(), equalTo("RUNNING"));
    assertThat(serverSpec.shouldStart(1), is(true));
  }

  @Test
  public void whenDomainReadFromYamlWithNoSetting_defaultsToDomainHomeInImage() throws IOException {
    Domain domain = readDomain(DOMAIN_V2_SAMPLE_YAML);

    assertThat(domain.isDomainHomeInImage(), is(true));
  }

  @Test
  public void whenDomainReadFromYaml_domainHomeInImageIsDisabled() throws IOException {
    Domain domain = readDomain(DOMAIN_V2_SAMPLE_YAML_2);

    assertThat(domain.isDomainHomeInImage(), is(false));
  }

  @Test
  public void whenDomainReadFromYamlWithNoSetting_defaultsToServerOutInPodLog() throws IOException {
    Domain domain = readDomain(DOMAIN_V2_SAMPLE_YAML);

    assertThat(domain.isIncludeServerOutInPodLog(), is(true));
  }

  @Test
  public void whenDomainReadFromYaml_serverOutInPodLogIsSet() throws IOException {
    Domain domain = readDomain(DOMAIN_V2_SAMPLE_YAML_2);

    assertThat(domain.isIncludeServerOutInPodLog(), is(false));
  }

  @Test
  public void whenDomainReadFromYaml_unconfiguredClusteredServerHasDomainDefaults()
      throws IOException {
    Domain domain = readDomain(DOMAIN_V2_SAMPLE_YAML);
    ServerSpec serverSpec = domain.getServer("server0", "cluster0");

    assertThat(serverSpec.getImage(), equalTo(DEFAULT_IMAGE));
    assertThat(serverSpec.getImagePullPolicy(), equalTo(IFNOTPRESENT_IMAGEPULLPOLICY));
    assertThat(serverSpec.getImagePullSecrets().get(0).getName(), equalTo("pull-secret1"));
    assertThat(serverSpec.getImagePullSecrets().get(1).getName(), equalTo("pull-secret2"));
    assertThat(serverSpec.getConfigOverrides(), equalTo("overrides-config-map"));
    assertThat(
        serverSpec.getConfigOverrideSecrets(),
        containsInAnyOrder("overrides-secret-1", "overrides-secret-2"));
    assertThat(serverSpec.getEnvironmentVariables(), contains(envVar("var1", "value0")));
    assertThat(serverSpec.getDesiredState(), equalTo("RUNNING"));
    assertThat(serverSpec.shouldStart(1), is(true));
  }

  @Test
  public void whenDomainReadFromYaml_adminServerOverridesDefaults() throws IOException {
    Domain domain = readDomain(DOMAIN_V2_SAMPLE_YAML);
    ServerSpec serverSpec = domain.getAdminServerSpec();

    assertThat(serverSpec.getEnvironmentVariables(), contains(envVar("var1", "value1")));
  }

  @Test
  public void whenDomainReadFromYaml_server1OverridesDefaults() throws IOException {
    Domain domain = readDomain(DOMAIN_V2_SAMPLE_YAML);
    ServerSpec serverSpec = domain.getServer("server1", "cluster1");

    assertThat(serverSpec.getImage(), equalTo(DEFAULT_IMAGE));
    assertThat(
        serverSpec.getEnvironmentVariables(),
        containsInAnyOrder(
            envVar("JAVA_OPTIONS", "-server"),
            envVar("USER_MEM_ARGS", "-Djava.security.egd=file:/dev/./urandom -Xms64m -Xmx256m "),
            envVar("var1", "value0")));
    assertThat(serverSpec.getConfigOverrides(), equalTo("overrides-config-map"));
    assertThat(
        serverSpec.getConfigOverrideSecrets(),
        containsInAnyOrder("overrides-secret-1", "overrides-secret-2"));
  }

  @Test
  public void whenDomainReadFromYaml_cluster2OverridesDefaults() throws IOException {
    Domain domain = readDomain(DOMAIN_V2_SAMPLE_YAML);
    ServerSpec serverSpec = domain.getServer("server2", "cluster2");

    assertThat(serverSpec.getDesiredState(), equalTo("ADMIN"));
    assertThat(
        serverSpec.getEnvironmentVariables(),
        containsInAnyOrder(
            envVar("JAVA_OPTIONS", "-Dweblogic.management.startupMode=ADMIN -verbose"),
            envVar("USER_MEM_ARGS", "-Xms64m -Xmx256m "),
            envVar("var1", "value0")));
    assertThat(serverSpec.getConfigOverrides(), equalTo("overrides-config-map"));
    assertThat(
        serverSpec.getConfigOverrideSecrets(),
        containsInAnyOrder("overrides-secret-1", "overrides-secret-2"));
  }

  @Test
  public void whenDomainReadFromYaml_AdminAndManagedOverrideDomainNodeSelectors()
      throws IOException {
    Domain domain = readDomain(DOMAIN_V2_SAMPLE_YAML);
    ServerSpec server1Spec = domain.getServer("server1", null);
    ServerSpec server2Spec = domain.getServer("server2", null);
    assertThat(domain.getAdminServerSpec().getNodeSelectors(), hasEntry("os_arch", "x86_64"));
    assertThat(domain.getAdminServerSpec().getNodeSelectors(), hasEntry("os", "linux"));
    assertThat(server2Spec.getNodeSelectors(), hasEntry("os_arch", "x86"));
    assertThat(server2Spec.getNodeSelectors(), hasEntry("os", "linux"));
    assertThat(server1Spec.getNodeSelectors(), hasEntry("os_arch", "arm64"));
    assertThat(server1Spec.getNodeSelectors(), hasEntry("os", "linux"));
  }

  @Test
  public void whenDomainReadFromYaml_ManagedServerOverrideDomainResourceRequirements()
      throws IOException {
    Domain domain = readDomain(DOMAIN_V2_SAMPLE_YAML);
    V1ResourceRequirements server1ResReq = domain.getServer("server1", null).getResources();
    V1ResourceRequirements server2ResReq = domain.getServer("server2", null).getResources();

    // Server1 overrides request memory: "32Mi" and limit memory: "256Mi"
    assertThat(server1ResReq.getRequests(), hasResourceQuantity("memory", "32Mi"));
    assertThat(server1ResReq.getLimits(), hasResourceQuantity("memory", "256Mi"));

    // These values come from the domain
    assertThat(server1ResReq.getRequests(), hasResourceQuantity("cpu", "250m"));
    assertThat(server1ResReq.getLimits(), hasResourceQuantity("cpu", "500m"));

    // Server2 inherits everything from the domain
    assertThat(server2ResReq.getRequests(), hasResourceQuantity("memory", "64Mi"));
    assertThat(server2ResReq.getLimits(), hasResourceQuantity("memory", "128Mi"));
    assertThat(server2ResReq.getRequests(), hasResourceQuantity("cpu", "250m"));
    assertThat(server2ResReq.getLimits(), hasResourceQuantity("cpu", "500m"));
  }

  @Test
  public void whenDomainReadFromYaml_AdminAndManagedOverrideResourceRequirements()
      throws IOException {
    Domain domain = readDomain(DOMAIN_V2_SAMPLE_YAML);
    V1ResourceRequirements asResReq = domain.getAdminServerSpec().getResources();

    // Admin server override requests cpu: "150m" and limit cpu: "200m"
    assertThat(asResReq.getRequests(), hasResourceQuantity("cpu", "150m"));
    assertThat(asResReq.getLimits(), hasResourceQuantity("cpu", "200m"));

    // The rest are inherited from the domain definition
    assertThat(asResReq.getRequests(), hasResourceQuantity("memory", "64Mi"));
    assertThat(asResReq.getLimits(), hasResourceQuantity("memory", "128Mi"));
  }

  private Matcher<Map<? extends String, ? extends Quantity>> hasResourceQuantity(
      String resource, String quantity) {
    return hasEntry(resource, Quantity.fromString(quantity));
  }

  @Test
  public void whenDomain2ReadFromYaml_unknownClusterUseDefaultReplicaCount() throws IOException {
    Domain domain = readDomain(DOMAIN_V2_SAMPLE_YAML_2);

    assertThat(domain.getReplicaCount("unknown"), equalTo(3));
  }

  @Test
  public void whenDomain2ReadFromYaml_unconfiguredClusterUseDefaultReplicaCount()
      throws IOException {
    Domain domain = readDomain(DOMAIN_V2_SAMPLE_YAML_2);

    assertThat(domain.getReplicaCount("cluster1"), equalTo(3));
  }

  @Test
  public void whenDomain2ReadFromYaml_serverReadsDomainDefaultOfNever() throws IOException {
    Domain domain = readDomain(DOMAIN_V2_SAMPLE_YAML_2);
    ServerSpec serverSpec = domain.getServer("server2", null);

    assertThat(serverSpec.shouldStart(0), is(false));
  }

  @Test
  public void whenDomain2ReadFromYaml_serverConfiguresReadinessProbe() throws IOException {
    Domain domain = readDomain(DOMAIN_V2_SAMPLE_YAML_2);
    ServerSpec serverSpec = domain.getServer("server2", "cluster1");

    assertThat(serverSpec.getReadinessProbe().getInitialDelaySeconds(), equalTo(10));
    assertThat(serverSpec.getReadinessProbe().getTimeoutSeconds(), equalTo(15));
    assertThat(serverSpec.getReadinessProbe().getPeriodSeconds(), equalTo(20));
  }

  @Test
  public void whenDomain2ReadFromYaml_serverConfiguresLivenessProbe() throws IOException {
    Domain domain = readDomain(DOMAIN_V2_SAMPLE_YAML_2);
    ServerSpec serverSpec = domain.getServer("server2", "cluster1");

    assertThat(serverSpec.getLivenessProbe().getInitialDelaySeconds(), equalTo(20));
    assertThat(serverSpec.getLivenessProbe().getTimeoutSeconds(), equalTo(5));
    assertThat(serverSpec.getLivenessProbe().getPeriodSeconds(), equalTo(18));
  }

  @Test
  public void whenDomain2ReadFromYaml_clusterHasNodeSelector() throws IOException {
    Domain domain = readDomain(DOMAIN_V2_SAMPLE_YAML_2);
    ServerSpec serverSpec = domain.getServer(SERVER2, "cluster1");
    assertThat(serverSpec.getNodeSelectors(), hasEntry("os", "linux"));
  }

  @Test
  public void whenDomain2ReadFromYaml_clusterAndManagedServerHaveDifferentNodeSelectors()
      throws IOException {
    Domain domain = readDomain(DOMAIN_V2_SAMPLE_YAML_2);
    ServerSpec serverSpec = domain.getServer("server2", "cluster1");
    assertThat(serverSpec.getNodeSelectors(), hasEntry("os", "linux"));
    assertThat(serverSpec.getNodeSelectors(), hasEntry("os_type", "oel7"));
  }

  @Test
  public void whenDomain2ReadFromYaml_ManagedServerInheritContainerSecurityContextFromDomain()
      throws IOException {
    Domain domain = readDomain(DOMAIN_V2_SAMPLE_YAML_2);

    V1SecurityContext server2ContainerSecCtx =
        domain.getServer("server2", null).getContainerSecurityContext();
    assertThat(server2ContainerSecCtx.getRunAsGroup(), is(422L));
    assertThat(server2ContainerSecCtx.isAllowPrivilegeEscalation(), is(false));
    assertThat(server2ContainerSecCtx.getCapabilities().getAdd(), contains("CHOWN", "SYS_BOOT"));
    assertThat(server2ContainerSecCtx.getSeLinuxOptions().getLevel(), is("server"));
    assertThat(server2ContainerSecCtx.getSeLinuxOptions().getRole(), is("slave"));
    assertThat(server2ContainerSecCtx.getSeLinuxOptions().getType(), nullValue());
    assertThat(server2ContainerSecCtx.getRunAsUser(), nullValue());
  }

  @Test
  public void whenDomain2ReadFromYaml_ManagedServerInheritContainerSecurityContextFromCluster()
      throws IOException {
    Domain domain = readDomain(DOMAIN_V2_SAMPLE_YAML_2);

    V1SecurityContext server1ContainerSecCtx =
        domain.getServer("server1", "cluster1").getContainerSecurityContext();
    assertThat(server1ContainerSecCtx.getRunAsGroup(), is(421L));
    assertThat(server1ContainerSecCtx.isAllowPrivilegeEscalation(), is(false));
    assertThat(
        server1ContainerSecCtx.getCapabilities().getAdd(),
        contains("SYS_TIME", "CHOWN", "SYS_BOOT"));
    assertThat(server1ContainerSecCtx.getSeLinuxOptions().getLevel(), is("cluster"));
    assertThat(server1ContainerSecCtx.getSeLinuxOptions().getRole(), is("admin"));
    assertThat(server1ContainerSecCtx.getSeLinuxOptions().getType(), is("admin"));
    assertThat(server1ContainerSecCtx.getRunAsUser(), nullValue());
  }

  @Test
  public void whenDomain2ReadFromYaml_AdminServerInheritContainerSecurityContextFromDomain()
      throws IOException {
    Domain domain = readDomain(DOMAIN_V2_SAMPLE_YAML_2);

    V1SecurityContext asContainerSecCtx = domain.getAdminServerSpec().getContainerSecurityContext();
    assertThat(asContainerSecCtx.getRunAsGroup(), is(420L));
    assertThat(asContainerSecCtx.isAllowPrivilegeEscalation(), is(false));
    assertThat(asContainerSecCtx.getCapabilities().getAdd(), contains("CHOWN", "SYS_BOOT"));
    assertThat(asContainerSecCtx.getSeLinuxOptions().getLevel(), is("domain"));
    assertThat(asContainerSecCtx.getSeLinuxOptions().getRole(), is("admin"));
    assertThat(asContainerSecCtx.getSeLinuxOptions().getType(), nullValue());
    assertThat(asContainerSecCtx.getRunAsUser(), nullValue());
    assertThat(asContainerSecCtx.isPrivileged(), nullValue());
  }

  @Test
  public void whenDomain2ReadFromYaml_ManagedServerInheritPodSecurityContextFromDomain()
      throws IOException {
    Domain domain = readDomain(DOMAIN_V2_SAMPLE_YAML_2);
    V1PodSecurityContext server2PodSecCtx =
        domain.getServer("server2", null).getPodSecurityContext();
    assertThat(server2PodSecCtx.getRunAsGroup(), is(422L));
    assertThat(server2PodSecCtx.getSysctls(), contains(DOMAIN_SYSCTL));
    assertThat(server2PodSecCtx.getSeLinuxOptions().getLevel(), is("server"));
    assertThat(server2PodSecCtx.getSeLinuxOptions().getRole(), is("slave"));
    assertThat(server2PodSecCtx.getSeLinuxOptions().getType(), nullValue());
    assertThat(server2PodSecCtx.getRunAsUser(), nullValue());
  }

  @Test
  public void whenDomain2ReadFromYaml_ManagedServerInheritPodSecurityContextFromCluster()
      throws IOException {
    Domain domain = readDomain(DOMAIN_V2_SAMPLE_YAML_2);
    V1PodSecurityContext server1PodSecCtx =
        domain.getServer("server1", "cluster1").getPodSecurityContext();
    assertThat(server1PodSecCtx.getRunAsGroup(), is(421L));
    assertThat(server1PodSecCtx.getSysctls(), contains(CLUSTER_SYSCTL));
    assertThat(server1PodSecCtx.getSeLinuxOptions().getLevel(), is("cluster"));
    assertThat(server1PodSecCtx.getSeLinuxOptions().getRole(), is("admin"));
    assertThat(server1PodSecCtx.getSeLinuxOptions().getType(), is("admin"));
    assertThat(server1PodSecCtx.getRunAsUser(), nullValue());
  }

  @Test
  public void whenDomain2ReadFromYaml_AdminServerInheritPodSecurityContextFromDomain()
      throws IOException {
    Domain domain = readDomain(DOMAIN_V2_SAMPLE_YAML_2);
    V1PodSecurityContext asPodSecCtx = domain.getAdminServerSpec().getPodSecurityContext();
    assertThat(asPodSecCtx.getRunAsGroup(), is(420L));
    assertThat(asPodSecCtx.getSysctls(), contains(DOMAIN_SYSCTL));
    assertThat(asPodSecCtx.getSeLinuxOptions().getLevel(), is("domain"));
    assertThat(asPodSecCtx.getSeLinuxOptions().getRole(), is("admin"));
    assertThat(asPodSecCtx.getSeLinuxOptions().getType(), nullValue());
    assertThat(asPodSecCtx.getRunAsUser(), nullValue());
  }

  @Test
  public void whenDomain2ReadFromYaml_serviceAnnotationsFound() throws IOException {
    Domain domain = readDomain(DOMAIN_V2_SAMPLE_YAML_2);
    ServerSpec serverSpec = domain.getServer("server2", "cluster1");
    assertThat(serverSpec.getServiceAnnotations(), hasEntry("testKey3", "testValue3"));
  }

  @Test
  public void whenDomain3ReadFromYaml_hasExportedNaps() throws IOException {
    Domain domain = readDomain(DOMAIN_V2_SAMPLE_YAML_3);

    assertThat(domain.getAdminServerChannelNames(), containsInAnyOrder("channelA", "channelB"));
  }

  @Test
  public void whenDomain3ReadFromYaml_adminServerHasNodeSelector() throws IOException {
    Domain domain = readDomain(DOMAIN_V2_SAMPLE_YAML_3);
    assertThat(domain.getAdminServerSpec().getNodeSelectors(), hasEntry("os", "linux"));
  }

  @Test
  public void whenDomain3ReadFromYaml_adminServerHasAnnotationsAndLabels() throws IOException {
    Domain domain = readDomain(DOMAIN_V2_SAMPLE_YAML_3);
    assertThat(
        domain.getAdminServerSpec().getServiceAnnotations(), hasEntry("testKey3", "testValue3"));
    assertThat(domain.getAdminServerSpec().getServiceLabels(), hasEntry("testKey1", "testValue1"));
    assertThat(domain.getAdminServerSpec().getServiceLabels(), hasEntry("testKey2", "testValue2"));
  }

  @Test
  public void whenDomain3ReadFromYaml_AdminServerRestartVersion() throws IOException {
    Domain domain = readDomain(DOMAIN_V2_SAMPLE_YAML_3);
    assertThat(domain.getAdminServerSpec().getServerRestartVersion(), is("1"));
  }

  @Test
  public void whenDomain3ReadFromYaml_NoRestartVersion() throws IOException {
    Domain domain = readDomain(DOMAIN_V2_SAMPLE_YAML_3);
    ServerSpec clusteredServer = domain.getServer("anyServer", "anyCluster");
    ServerSpec nonClusteredServer = domain.getServer("anyServer", null);
    assertThat(clusteredServer.getDomainRestartVersion(), nullValue());
    assertThat(clusteredServer.getClusterRestartVersion(), nullValue());
    assertThat(clusteredServer.getServerRestartVersion(), nullValue());
    assertThat(nonClusteredServer.getDomainRestartVersion(), nullValue());
    assertThat(nonClusteredServer.getClusterRestartVersion(), nullValue());
    assertThat(nonClusteredServer.getServerRestartVersion(), nullValue());
  }

  @Test
  public void whenDomainReadFromYaml_DomainRestartVersion() throws IOException {
    Domain domain = readDomain(DOMAIN_V2_SAMPLE_YAML);
    assertThat(domain.getAdminServerSpec().getDomainRestartVersion(), is("1"));
    assertThat(domain.getAdminServerSpec().getClusterRestartVersion(), nullValue());
    assertThat(domain.getAdminServerSpec().getServerRestartVersion(), nullValue());
  }

  @Test
  public void whenDomainReadFromYaml_ClusterRestartVersion() throws IOException {
    Domain domain = readDomain(DOMAIN_V2_SAMPLE_YAML);
    ServerSpec serverSpec = domain.getServer("server1", "cluster2");

    assertThat(serverSpec.getDomainRestartVersion(), is("1"));
    assertThat(serverSpec.getClusterRestartVersion(), is("2"));
    assertThat(serverSpec.getServerRestartVersion(), nullValue());
  }

  @Test
  public void whenDomainReadFromYaml_ServerRestartVersion() throws IOException {
    Domain domain = readDomain(DOMAIN_V2_SAMPLE_YAML);
    ServerSpec serverSpec = domain.getServer("server2", null);

    assertThat(serverSpec.getDomainRestartVersion(), is("1"));
    assertThat(serverSpec.getClusterRestartVersion(), nullValue());
    assertThat(serverSpec.getServerRestartVersion(), is("3"));
  }

  @Test
  public void whenVolumesConfiguredOnMultipleLevels_useCombination() {
    configureDomain(domain)
        .withAdditionalVolume("name1", "/domain-tmp1")
        .withAdditionalVolume("name2", "/domain-tmp2");
    configureCluster("cluster1")
        .withAdditionalVolume("name3", "/cluster-tmp1")
        .withAdditionalVolume("name4", "/cluster-tmp2")
        .withAdditionalVolume("name5", "/cluster-tmp3");
    configureServer("server1").withAdditionalVolume("name6", "/server-tmp1");

    assertThat(
        domain.getServer("server1", "cluster1").getAdditionalVolumes(),
        containsInAnyOrder(
            volume("name1", "/domain-tmp1"),
            volume("name2", "/domain-tmp2"),
            volume("name3", "/cluster-tmp1"),
            volume("name4", "/cluster-tmp2"),
            volume("name5", "/cluster-tmp3"),
            volume("name6", "/server-tmp1")));
  }

  @Test
  public void whenVolumeMountsConfiguredOnMultipleLevels_useCombination() {
    configureDomain(domain)
        .withAdditionalVolumeMount("name1", "/domain-test1")
        .withAdditionalVolumeMount("name2", "/domain-test2");
    configureCluster("cluster1")
        .withAdditionalVolumeMount("name3", "/cluster-test1")
        .withAdditionalVolumeMount("name4", "/cluster-test2")
        .withAdditionalVolumeMount("name5", "/cluster-test3");
    configureServer("server1").withAdditionalVolumeMount("name6", "/server-test1");

    assertThat(
        domain.getServer("server1", "cluster1").getAdditionalVolumeMounts(),
        containsInAnyOrder(
            volumeMount("name1", "/domain-test1"),
            volumeMount("name2", "/domain-test2"),
            volumeMount("name3", "/cluster-test1"),
            volumeMount("name4", "/cluster-test2"),
            volumeMount("name5", "/cluster-test3"),
            volumeMount("name6", "/server-test1")));
  }

  @Test
  public void whenDuplicateVolumesConfiguredOnMultipleLevels_useCombination() {
    configureDomain(domain)
        .withAdditionalVolume("name1", "/domain-tmp1")
        .withAdditionalVolume("name2", "/domain-tmp2")
        .withAdditionalVolume("name3", "/domain-tmp3");
    configureCluster("cluster1")
        .withAdditionalVolume("name2", "/cluster-tmp1")
        .withAdditionalVolume("name3", "/cluster-tmp2");
    configureServer("server1").withAdditionalVolume("name3", "/server-tmp1");

    assertThat(
        domain.getServer("server1", "cluster1").getAdditionalVolumes(),
        containsInAnyOrder(
            volume("name1", "/domain-tmp1"),
            volume("name2", "/cluster-tmp1"),
            volume("name3", "/server-tmp1")));
  }

  @Test
  public void whenDuplicateVolumeMountsConfiguredOnMultipleLevels_useCombination() {
    configureDomain(domain)
        .withAdditionalVolumeMount("name1", "/domain-test1")
        .withAdditionalVolumeMount("name2", "/domain-test2")
        .withAdditionalVolumeMount("name3", "/domain-test3");
    configureCluster("cluster1")
        .withAdditionalVolumeMount("name2", "/cluster-test1")
        .withAdditionalVolumeMount("name3", "/cluster-test2");
    configureServer("server1").withAdditionalVolumeMount("name3", "/server-test1");

    assertThat(
        domain.getServer("server1", "cluster1").getAdditionalVolumeMounts(),
        containsInAnyOrder(
            volumeMount("name1", "/domain-test1"),
            volumeMount("name2", "/cluster-test1"),
            volumeMount("name3", "/server-test1")));
  }

  @Test
  public void whenDefaultConfiguration_domainHomeInImageIsTrue() {
    configureDomain(domain);

    assertThat(domain.getSpec().isDomainHomeInImage(), is(true));
  }

  @Test
  public void whenDomainHomeInImageSpecified_useValue() {
    configureDomain(domain).withDomainHomeInImage(false);

    assertThat(domain.getSpec().isDomainHomeInImage(), is(false));
  }

  @Test
  public void whenLogHomeNotSet_useDefault() {
    configureDomain(domain);

    assertThat(domain.getLogHome(), equalTo("/shared/logs/uid1"));
  }

  @Test
  public void whenLogHomeSet_useValue() {
    configureDomain(domain).withLogHome("/custom/logs");

    assertThat(domain.getLogHome(), equalTo("/custom/logs"));
  }

  @Test
  public void whenDomainHomeInImage_logHomeNotEnabled() {
    configureDomain(domain).withDomainHomeInImage(true);

    assertThat(domain.getSpec().getLogHomeEnabled(), is(false));
  }

  @Test
  public void whenDomainHomeNotInImage_logHomeEnabled() {
    configureDomain(domain).withDomainHomeInImage(false);

    assertThat(domain.getSpec().getLogHomeEnabled(), is(true));
  }

  @Test
  public void whenLogHomeEnabledSet_useValue() {
    configureDomain(domain).withLogHomeEnabled(true);

    assertThat(domain.getSpec().getLogHomeEnabled(), is(true));
  }

  @Test
  public void domainHomeTest_standardHome2() {
    configureDomain(domain).withDomainHomeInImage(false);

    assertThat(domain.getDomainHome(), equalTo("/shared/domains/uid1"));
  }

  @Test
  public void domainHomeTest_standardHome3() {
    configureDomain(domain).withDomainHomeInImage(true);

    assertThat(domain.getDomainHome(), equalTo("/u01/oracle/user_projects/domains"));
  }

  @Test
  public void domainHomeTest_customHome1() {
    configureDomain(domain).withDomainHome("/custom/domain/home");

    assertThat(domain.getDomainHome(), equalTo("/custom/domain/home"));
  }

  @Test
  public void whenPodLabelsAppliedOnMultipleLevels_useCombination() {
    configureDomain(domain)
        .withPodLabel("label1", "domain-label-value1")
        .withPodLabel("label2", "domain-label-value2");
    configureCluster("cluster1")
        .withPodLabel("label3", "cluster-label-value1")
        .withPodLabel("label4", "cluster-label-value2");
    configureServer("server1").withPodLabel("label5", "server-label-value1");

    assertThat(
        domain.getServer("server1", "cluster1").getPodLabels(),
        hasEntry("label1", "domain-label-value1"));
    assertThat(
        domain.getServer("server1", "cluster1").getPodLabels(),
        hasEntry("label2", "domain-label-value2"));
    assertThat(
        domain.getServer("server1", "cluster1").getPodLabels(),
        hasEntry("label3", "cluster-label-value1"));
    assertThat(
        domain.getServer("server1", "cluster1").getPodLabels(),
        hasEntry("label4", "cluster-label-value2"));
    assertThat(
        domain.getServer("server1", "cluster1").getPodLabels(),
        hasEntry("label5", "server-label-value1"));
  }

  @Test
  public void whenPodAnnotationsAppliedOnMultipleLevels_useCombination() {
    configureDomain(domain)
        .withPodAnnotation("annotation1", "domain-annotation-value1")
        .withPodAnnotation("annotation2", "domain-annotation-value2");
    configureCluster("cluster1")
        .withPodAnnotation("annotation3", "cluster-annotation-value1")
        .withPodAnnotation("annotation4", "cluster-annotation-value2");
    configureServer("server1").withPodAnnotation("annotation5", "server-annotation-value1");

    assertThat(
        domain.getServer("server1", "cluster1").getPodAnnotations(),
        hasEntry("annotation1", "domain-annotation-value1"));
    assertThat(
        domain.getServer("server1", "cluster1").getPodAnnotations(),
        hasEntry("annotation2", "domain-annotation-value2"));
    assertThat(
        domain.getServer("server1", "cluster1").getPodAnnotations(),
        hasEntry("annotation3", "cluster-annotation-value1"));
    assertThat(
        domain.getServer("server1", "cluster1").getPodAnnotations(),
        hasEntry("annotation4", "cluster-annotation-value2"));
    assertThat(
        domain.getServer("server1", "cluster1").getPodAnnotations(),
        hasEntry("annotation5", "server-annotation-value1"));
  }

  @Test
  public void whenDuplicatePodLabelsConfiguredOnMultipleLevels_useCombination() {
    configureDomain(domain)
        .withPodLabel("label1", "domain-label-value1")
        .withPodLabel("label2", "domain-label-value2");
    configureCluster("cluster1")
        .withPodLabel("label2", "cluster-label-value1")
        .withPodLabel("label3", "cluster-label-value2");
    ;
    configureServer("server1").withPodLabel("label3", "server-label-value1");

    assertThat(
        domain.getServer("server1", "cluster1").getPodLabels(),
        hasEntry("label1", "domain-label-value1"));
    assertThat(
        domain.getServer("server1", "cluster1").getPodLabels(),
        hasEntry("label2", "cluster-label-value1"));
    assertThat(
        domain.getServer("server1", "cluster1").getPodLabels(),
        hasEntry("label3", "server-label-value1"));
  }

  @Test
  public void whenDuplicatePodAnnotationsConfiguredOnMultipleLevels_useCombination() {
    configureDomain(domain)
        .withPodAnnotation("annotation1", "domain-annotation-value1")
        .withPodAnnotation("annotation2", "domain-annotation-value2");
    configureCluster("cluster1")
        .withPodAnnotation("annotation2", "cluster-annotation-value1")
        .withPodAnnotation("annotation3", "cluster-annotation-value2");
    configureServer("server1").withPodAnnotation("annotation3", "server-annotation-value1");

    assertThat(
        domain.getServer("server1", "cluster1").getPodAnnotations(),
        hasEntry("annotation1", "domain-annotation-value1"));
    assertThat(
        domain.getServer("server1", "cluster1").getPodAnnotations(),
        hasEntry("annotation2", "cluster-annotation-value1"));
    assertThat(
        domain.getServer("server1", "cluster1").getPodAnnotations(),
        hasEntry("annotation3", "server-annotation-value1"));
  }

  private V1Volume volume(String name, String path) {
    return new V1Volume().name(name).hostPath(new V1HostPathVolumeSource().path(path));
  }

  private V1VolumeMount volumeMount(String name, String path) {
    return new V1VolumeMount().name(name).mountPath(path);
  }
}

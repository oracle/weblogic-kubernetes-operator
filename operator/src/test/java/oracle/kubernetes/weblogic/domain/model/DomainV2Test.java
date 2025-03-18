// Copyright (c) 2018, 2025, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.weblogic.domain.model;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import io.kubernetes.client.common.KubernetesObject;
import io.kubernetes.client.custom.Quantity;
import io.kubernetes.client.openapi.models.V1Capabilities;
import io.kubernetes.client.openapi.models.V1ConfigMapEnvSource;
import io.kubernetes.client.openapi.models.V1Container;
import io.kubernetes.client.openapi.models.V1EnvFromSource;
import io.kubernetes.client.openapi.models.V1EnvVar;
import io.kubernetes.client.openapi.models.V1HostPathVolumeSource;
import io.kubernetes.client.openapi.models.V1NFSVolumeSource;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1PodSecurityContext;
import io.kubernetes.client.openapi.models.V1ResourceRequirements;
import io.kubernetes.client.openapi.models.V1SELinuxOptions;
import io.kubernetes.client.openapi.models.V1SecurityContext;
import io.kubernetes.client.openapi.models.V1Sysctl;
import io.kubernetes.client.openapi.models.V1Volume;
import io.kubernetes.client.openapi.models.V1VolumeMount;
import oracle.kubernetes.operator.DomainSourceType;
import oracle.kubernetes.operator.LogHomeLayoutType;
import oracle.kubernetes.operator.OverrideDistributionStrategy;
import oracle.kubernetes.operator.ServerStartPolicy;
import oracle.kubernetes.operator.ShutdownType;
import oracle.kubernetes.operator.helpers.DomainPresenceInfo;
import oracle.kubernetes.operator.processing.EffectiveServerSpec;
import oracle.kubernetes.weblogic.domain.DomainConfigurator;
import org.hamcrest.Matcher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static oracle.kubernetes.operator.DomainSourceType.FROM_MODEL;
import static oracle.kubernetes.operator.KubernetesConstants.DEFAULT_IMAGE;
import static oracle.kubernetes.operator.WebLogicConstants.JRF;
import static oracle.kubernetes.operator.WebLogicConstants.WLS;
import static oracle.kubernetes.operator.helpers.PodHelperTestBase.CONFIGURED_FAILURE_THRESHOLD;
import static oracle.kubernetes.operator.helpers.PodHelperTestBase.CONFIGURED_PATH;
import static oracle.kubernetes.operator.helpers.PodHelperTestBase.CONFIGURED_SUCCESS_THRESHOLD;
import static oracle.kubernetes.weblogic.domain.ChannelMatcher.channelWith;
import static oracle.kubernetes.weblogic.domain.model.CreateIfNotExists.DOMAIN;
import static org.hamcrest.Matchers.both;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.junit.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DomainV2Test extends DomainTestBase {

  private static final String SERVER2 = "ms2";
  private static final int DEFAULT_REPLICA_LIMIT = 1;
  private static final int INITIAL_DELAY = 17;
  private static final int TIMEOUT = 23;
  private static final int PERIOD = 5;
  private static final V1Sysctl DOMAIN_SYSCTL =
      new V1Sysctl().name("net.ipv4.route.min_pmtu").value("552");
  private static final V1Sysctl CLUSTER_SYSCTL =
      new V1Sysctl().name("kernel.shm_rmid_forced").value("0");
  public static final String LIVENESS_PROBE_CUSTOM_SCRIPT = "/u01/customLiveness.sh";

  @BeforeEach
  public void setUp() {
    configureDomain(domain);
  }

  @Test
  void whenDomainOnPV_logHomeDefaultsToEnabled() {
    configureDomain(domain).withDomainHomeSourceType(DomainSourceType.PERSISTENT_VOLUME);

    assertThat(domain.isLogHomeEnabled(), is(true));
  }

  @Test
  void whenDomainOnPvAndLogHomeDisabled_returnOverride() {
    configureDomain(domain).withDomainHomeSourceType(DomainSourceType.PERSISTENT_VOLUME).withLogHomeEnabled(false);

    assertThat(domain.isLogHomeEnabled(), is(false));
  }

  @Test
  void whenDomainInImage_logHomeDefaultsToDisabled() {
    configureDomain(domain).withDomainHomeSourceType(DomainSourceType.IMAGE);

    assertThat(domain.isLogHomeEnabled(), is(false));
  }

  @Test
  void whenDomainInImageAndLogHomeEnabled_returnOverride() {
    configureDomain(domain).withDomainHomeSourceType(DomainSourceType.IMAGE).withLogHomeEnabled(true);

    assertThat(domain.isLogHomeEnabled(), is(true));
  }

  @Test
  void whenLogHomeSet_returnIt() {
    configureDomain(domain).withLogHome("/my/logs/");

    assertThat(domain.getLogHome(), equalTo("/my/logs/"));
  }

  @Test
  void whenLogHomeLayoutSet_returnTheCorrectLayoutType() {
    configureDomain(domain).withLogHomeLayout(LogHomeLayoutType.FLAT);

    assertThat(domain.getLogHomeLayout(), equalTo(LogHomeLayoutType.FLAT));
  }

  @Test
  void whenLogHomeSetWithoutTrailingSlash_appendOne() {
    configureDomain(domain).withLogHome("/my/logs");

    assertThat(domain.getLogHome(), equalTo("/my/logs/"));
  }

  @Test
  void whenLogHomeSetNull_returnDefaultLogHome() {
    configureDomain(domain).withLogHome(null);

    assertThat(domain.getLogHome(), equalTo("/shared/logs/" + DOMAIN_UID));
  }

  @Test
  void whenLogHomeSetToBlanks_returnDefaultLogHome() {
    configureDomain(domain).withLogHome("   ");

    assertThat(domain.getLogHome(), equalTo("/shared/logs/" + DOMAIN_UID));
  }

  @Test
  void whenLogHomeDisabled_effectiveLogHomeIsNull() {
    configureDomain(domain).withLogHomeEnabled(false).withLogHome("/my/logs/");

    assertThat(domain.getEffectiveLogHome(), nullValue());
  }

  @Test
  void whenLogHomeEnabled_effectiveLogHomeEqualsLogHome() {
    configureDomain(domain).withLogHomeEnabled(true).withLogHome("/my/logs/");

    assertThat(domain.getEffectiveLogHome(), equalTo("/my/logs/"));
  }

  @Test
  void whenClusterNotConfiguredAndNoDomainReplicaCount_countIsOne() {
    assertThat(info.getReplicaCount("nosuchcluster"), equalTo(1));
  }

  @Test
  void whenClusterNotConfiguredAndDomainHasReplicaCount_useIt() {
    configureDomain(domain).withDefaultReplicaCount(3);

    assertThat(info.getReplicaCount("nosuchcluster"), equalTo(3));
  }

  @Test
  void whenStartupPolicyUnspecified_adminServerStartsUp() {
    assertThat(domain.getAdminServerSpec().shouldStart(0), is(true));
  }

  @Test
  void whenStartupPolicyUnspecified_nonClusteredServerStartsUp() {
    assertThat(info.getServer("server1", null).shouldStart(0), is(true));
  }

  @Test
  void whenStartupPolicyUnspecified_clusteredServerStartsUpIfLimitNotReached() {
    configureCluster("cluster1").withReplicas(3);

    assertThat(info.getServer("server1", null).shouldStart(1), is(true));
  }

  @Test
  void whenStartupPolicyUnspecified_clusteredServerDoesNotStartUpIfLimitReached() {
    configureCluster("cluster1").withReplicas(3);

    assertThat(info.getServer("server1", "cluster1").shouldStart(4), is(false));
  }

  @Test
  void whenStartupPolicyNever_nonClusteredServerDoesNotStartUp() {
    configureDomain(domain).withDefaultServerStartPolicy(ServerStartPolicy.NEVER);

    assertThat(info.getServer("server1", null).shouldStart(0), is(false));
  }

  @Test
  void whenStartupPolicyAlways_clusteredServerStartsUpEvenIfLimitReached() {
    configureDomain(domain).withDefaultServerStartPolicy(ServerStartPolicy.ALWAYS);
    configureCluster("cluster1").withReplicas(3);

    assertThat(info.getServer("server1", "cluster1").shouldStart(4), is(true));
  }

  @Test
  void whenAdminServerChannelsNotDefined_exportedNamesIsEmpty() {
    assertThat(domain.getAdminServerChannelNames(), empty());
  }

  @Test
  void whenServerStartPolicyAlwaysConfiguredOnlyOnDomain_startServer() {
    configureDomain(domain).withDefaultServerStartPolicy(ServerStartPolicy.ALWAYS);
    configureServer("server1");

    assertThat(info.getServer("server1", "cluster1").shouldStart(0), is(true));
  }

  @Test
  void whenServerStartPolicyNever_dontStartServer() {
    configureServer("server1").withServerStartPolicy(ServerStartPolicy.NEVER);

    assertThat(info.getServer("server1", "cluster1").shouldStart(0), is(false));
  }

  @Test
  void whenServerStartPolicyAlways_startServer() {
    configureServer("server1").withServerStartPolicy(ServerStartPolicy.ALWAYS);

    assertThat(info.getServer("server1", "cluster1").shouldStart(0), is(true));
  }

  @Test
  void whenNonClusteredServerStartPolicyUndefined_startServer() {
    assertThat(info.getServer("server1", null).shouldStart(0), is(true));
  }

  @Test
  void whenUnconfiguredClusterHasDefaultNumberOfReplicas_dontStartServer() {
    assertThat(info.getServer("server1", "cls1").shouldStart(DEFAULT_REPLICA_LIMIT), is(false));
  }

  @Test
  void whenClusteredServerStartPolicyInheritedAndNeedMoreServers_startServer() {
    configureCluster("cluster1").withReplicas(5);

    assertThat(info.getServer("server1", "cluster1").shouldStart(4), is(true));
  }

  @Test
  void whenClusteredServerStartPolicyIfNeededAndDontNeedMoreServers_dontStartServer() {
    configureServer("server1").withServerStartPolicy(ServerStartPolicy.IF_NEEDED);
    configureCluster("cluster1").withReplicas(5);

    assertThat(info.getServer("server1", "cluster1").shouldStart(5), is(false));
  }

  @Test
  void whenDomainStartPolicyNever_ignoreServerSettings() {
    configureDomain(domain).withDefaultServerStartPolicy(ServerStartPolicy.NEVER);
    configureServer("server1").withServerStartPolicy(ServerStartPolicy.ALWAYS);

    assertThat(info.getServer("server1", "cluster1").shouldStart(0), is(false));
  }

  @Test
  void whenClusterStartPolicyNever_ignoreServerSettings() {
    configureCluster("cluster1").withServerStartPolicy(ServerStartPolicy.NEVER);
    configureServer("server1").withServerStartPolicy(ServerStartPolicy.ALWAYS);

    assertThat(info.getServer("server1", "cluster1").shouldStart(0), is(false));
  }

  @Test
  void whenDomainStartPolicyAdminOnly_dontStartManagedServer() {
    configureDomain(domain).withDefaultServerStartPolicy(ServerStartPolicy.ADMIN_ONLY);
    configureServer("server1").withServerStartPolicy(ServerStartPolicy.ALWAYS);

    assertThat(info.getServer("server1", "cluster1").shouldStart(0), is(false));
  }

  @Test
  void whenDomainStartPolicyAdminOnlyAndAdminServerNever_dontStartAdminServer() {
    configureDomain(domain).withDefaultServerStartPolicy(ServerStartPolicy.ADMIN_ONLY);
    configureAdminServer().withServerStartPolicy(ServerStartPolicy.NEVER);

    assertThat(domain.getAdminServerSpec().shouldStart(0), is(false));
  }

  @Test
  void whenDomainStartPolicyAdminOnlyAndAdminServerIfNeeded_startAdminServer() {
    configureDomain(domain).withDefaultServerStartPolicy(ServerStartPolicy.ADMIN_ONLY);
    configureAdminServer().withServerStartPolicy(ServerStartPolicy.IF_NEEDED);

    assertThat(domain.getAdminServerSpec().shouldStart(0), is(true));
  }

  @Test
  void whenEnvironmentConfiguredOnMultipleLevels_useCombination() {
    configureDomain(domain)
        .withEnvironmentVariable("name1", "domain")
        .withEnvironmentVariable("name2", "domain");
    configureCluster("cluster1")
        .withEnvironmentVariable("name2", "cluster")
        .withEnvironmentVariable("name3", "cluster")
        .withEnvironmentVariable("name4", "cluster");
    configureServer("server1").withEnvironmentVariable("name4", "server");

    assertThat(
        info.getServer("server1", "cluster1").getEnvironmentVariables(),
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
  void livenessProbeSettings_returnsConfiguredValues() {
    configureServer(SERVER1)
            .withLivenessProbeSettings(INITIAL_DELAY, TIMEOUT, PERIOD)
            .withLivenessProbeThresholds(CONFIGURED_SUCCESS_THRESHOLD, CONFIGURED_FAILURE_THRESHOLD);
    EffectiveServerSpec spec = info.getServer(SERVER1, CLUSTER_NAME);

    assertThat(spec.getLivenessProbe().getInitialDelaySeconds(), equalTo(INITIAL_DELAY));
    assertThat(spec.getLivenessProbe().getTimeoutSeconds(), equalTo(TIMEOUT));
    assertThat(spec.getLivenessProbe().getPeriodSeconds(), equalTo(PERIOD));
    assertThat(spec.getLivenessProbe().getSuccessThreshold(), equalTo(CONFIGURED_SUCCESS_THRESHOLD));
    assertThat(spec.getLivenessProbe().getFailureThreshold(), equalTo(CONFIGURED_FAILURE_THRESHOLD));
  }

  @Test
  void whenLivenessProbeConfiguredOnMultipleLevels_useCombination() {
    configureDomain(domain).withDefaultLivenessProbeSettings(INITIAL_DELAY, -2, -3);
    configureCluster(CLUSTER_NAME)
            .withLivenessProbeSettings(null, TIMEOUT, -4)
            .withLivenessProbeThresholds(1, 1);
    configureServer(SERVER1)
            .withLivenessProbeSettings(null, null, PERIOD)
            .withLivenessProbeThresholds(CONFIGURED_SUCCESS_THRESHOLD, CONFIGURED_FAILURE_THRESHOLD);

    EffectiveServerSpec spec = info.getServer(SERVER1, CLUSTER_NAME);

    assertThat(spec.getLivenessProbe().getInitialDelaySeconds(), equalTo(INITIAL_DELAY));
    assertThat(spec.getLivenessProbe().getTimeoutSeconds(), equalTo(TIMEOUT));
    assertThat(spec.getLivenessProbe().getPeriodSeconds(), equalTo(PERIOD));
    assertThat(spec.getLivenessProbe().getSuccessThreshold(), equalTo(CONFIGURED_SUCCESS_THRESHOLD));
    assertThat(spec.getLivenessProbe().getFailureThreshold(), equalTo(CONFIGURED_FAILURE_THRESHOLD));
  }

  @Test
  void whenEnvFromConfiguredOnMultipleLevels_useCombination() {
    configureDomain(domain).withEnvFrom(createEnvFrom("domain"));
    configureCluster(CLUSTER_NAME)
        .withEnvFrom(createEnvFrom("cluster"));
    configureServer(SERVER1)
        .withEnvFrom(createEnvFrom("server"));

    EffectiveServerSpec spec = info.getServer(SERVER1, CLUSTER_NAME);

    assertTrue(spec.getEnvFrom().containsAll(createEnvFrom("server")));
    assertTrue(spec.getEnvFrom().containsAll(createEnvFrom("cluster")));
    assertTrue(spec.getEnvFrom().containsAll(createEnvFrom("domain")));
  }

  private List<V1EnvFromSource> createEnvFrom(String scope) {
    return List.of(new V1EnvFromSource().configMapRef(new V1ConfigMapEnvSource().name(scope)));
  }

  @Test
  void readinessProbeSettings_returnsConfiguredValues() {
    configureServer(SERVER1)
            .withReadinessProbeSettings(INITIAL_DELAY, TIMEOUT, PERIOD)
            .withReadinessProbeThresholds(CONFIGURED_SUCCESS_THRESHOLD, CONFIGURED_FAILURE_THRESHOLD)
            .withReadinessProbeHttpGetActionPath(CONFIGURED_PATH);
    EffectiveServerSpec spec = info.getServer(SERVER1, CLUSTER_NAME);

    assertThat(spec.getReadinessProbe().getInitialDelaySeconds(), equalTo(INITIAL_DELAY));
    assertThat(spec.getReadinessProbe().getTimeoutSeconds(), equalTo(TIMEOUT));
    assertThat(spec.getReadinessProbe().getPeriodSeconds(), equalTo(PERIOD));
    assertThat(spec.getReadinessProbe().getSuccessThreshold(), equalTo(CONFIGURED_SUCCESS_THRESHOLD));
    assertThat(spec.getReadinessProbe().getFailureThreshold(), equalTo(CONFIGURED_FAILURE_THRESHOLD));
    assertThat(spec.getReadinessProbe().getHttpGet().getPath(), equalTo(CONFIGURED_PATH));
  }

  @Test
  void whenReadinessProbeConfiguredOnMultipleLevels_useCombination() {
    configureDomain(domain).withDefaultReadinessProbeSettings(INITIAL_DELAY, -2, -3);
    configureCluster(CLUSTER_NAME).withReadinessProbeSettings(null, TIMEOUT, -4);
    configureServer(SERVER1)
            .withReadinessProbeSettings(null, null, PERIOD)
            .withReadinessProbeThresholds(CONFIGURED_SUCCESS_THRESHOLD, CONFIGURED_FAILURE_THRESHOLD);

    EffectiveServerSpec spec = info.getServer(SERVER1, CLUSTER_NAME);

    assertThat(spec.getReadinessProbe().getInitialDelaySeconds(), equalTo(INITIAL_DELAY));
    assertThat(spec.getReadinessProbe().getTimeoutSeconds(), equalTo(TIMEOUT));
    assertThat(spec.getReadinessProbe().getPeriodSeconds(), equalTo(PERIOD));
    assertThat(spec.getReadinessProbe().getSuccessThreshold(), equalTo(CONFIGURED_SUCCESS_THRESHOLD));
    assertThat(spec.getReadinessProbe().getFailureThreshold(), equalTo(CONFIGURED_FAILURE_THRESHOLD));
  }

  @Test
  void whenNodeSelectorConfiguredOnMultipleLevels_useCombination() {
    configureDomain(domain)
        .withNodeSelector("key1", "domain")
        .withNodeSelector("key2", "domain")
        .withNodeSelector("key3", "domain");
    configureCluster(CLUSTER_NAME)
        .withNodeSelector("key2", "cluser")
        .withNodeSelector("key3", "cluser");
    configureServer(SERVER1).withNodeSelector("key3", "server");
    configureAdminServer().withNodeSelector("key2", "admin").withNodeSelector("key3", "admin");

    final EffectiveServerSpec effectiveServerSpec = info.getServer(SERVER1, CLUSTER_NAME);

    assertThat(domain.getAdminServerSpec().getNodeSelectors(), hasEntry("key1", "domain"));
    assertThat(domain.getAdminServerSpec().getNodeSelectors(), hasEntry("key2", "admin"));
    assertThat(domain.getAdminServerSpec().getNodeSelectors(), hasEntry("key3", "admin"));

    assertThat(effectiveServerSpec.getNodeSelectors(), hasEntry("key1", "domain"));
    assertThat(effectiveServerSpec.getNodeSelectors(), hasEntry("key2", "cluser"));
    assertThat(effectiveServerSpec.getNodeSelectors(), hasEntry("key3", "server"));
  }

  @Test
  void whenRestartVersionConfiguredOnMultipleLevels_useCombination() {
    configureDomain(domain).withRestartVersion("1");
    configureCluster(CLUSTER_NAME).withRestartVersion("2");
    configureServer(SERVER1).withRestartVersion("3");
    configureAdminServer().withRestartVersion("4");

    final EffectiveServerSpec clusteredServer = info.getServer(SERVER1, CLUSTER_NAME);
    final EffectiveServerSpec nonClusteredServerWithRestartVersion = info.getServer(SERVER1, null);
    final EffectiveServerSpec nonClusteredServerNoRestartVersion = info.getServer("anyServer", null);
    final EffectiveServerSpec adminServer = domain.getAdminServerSpec();

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
  void whenResourceRequirementsConfiguredOnDomain() {
    configureDomainWithResourceRequirements(domain);
    assertThat(
        domain.getSpec().getResources().getRequests(), hasResourceQuantity("memory", "64Mi"));
    assertThat(domain.getSpec().getResources().getRequests(), hasResourceQuantity("cpu", "250m"));
    assertThat(domain.getSpec().getResources().getLimits(), hasResourceQuantity("memory", "128Mi"));
    assertThat(domain.getSpec().getResources().getLimits(), hasResourceQuantity("cpu", "500m"));
  }

  @Test
  void whenResourceRequirementsConfiguredOnClusterOverrideDomain() {
    configureDomainWithResourceRequirements(domain);
    V1ResourceRequirements msResourceReq = info.getServer("any", CLUSTER_NAME).getResources();

    // Since the "any" server is not specified in domain, it will take all values from CLUSTER_NAME
    assertThat(msResourceReq.getRequests(), hasResourceQuantity("memory", "128Mi"));
    assertThat(msResourceReq.getRequests(), hasResourceQuantity("cpu", "250m"));
    assertThat(msResourceReq.getLimits(), hasResourceQuantity("memory", "256Mi"));
    assertThat(msResourceReq.getLimits(), hasResourceQuantity("cpu", "500m"));
  }

  @Test
  void whenResourceRequirementsConfiguredOnManagedServerOverrideClusterAndDomain() {
    configureDomainWithResourceRequirements(domain);
    V1ResourceRequirements ms1ResourceReq = info.getServer(SERVER1, CLUSTER_NAME).getResources();

    // Since SERVER1 is specified in domain, and it overrides only memory requirement of 512Mi
    // Only the following assertion should be different from the cluster defined values
    assertThat(ms1ResourceReq.getLimits(), hasResourceQuantity("memory", "512Mi"));

    // The rest of the resource requirements should be identical to the ones defined in the cluster
    assertThat(ms1ResourceReq.getRequests(), hasResourceQuantity("memory", "128Mi"));
    assertThat(ms1ResourceReq.getRequests(), hasResourceQuantity("cpu", "250m"));
    assertThat(ms1ResourceReq.getLimits(), hasResourceQuantity("cpu", "500m"));
  }

  @Test
  void whenResourceRequirementsConfiguredOnAdminServerOverrideClusterAndDomain() {
    configureDomainWithResourceRequirements(domain);
    V1ResourceRequirements asResourceReq = domain.getAdminServerSpec().getResources();

    // Admin server specify request of cpu: 500m and Limit cpu: 800m
    assertThat(asResourceReq.getRequests(), hasResourceQuantity("cpu", "500m"));
    assertThat(asResourceReq.getLimits(), hasResourceQuantity("cpu", "0.8"));

    // The rest should be the same as the domain requirements
    assertThat(asResourceReq.getRequests(), hasResourceQuantity("memory", "64Mi"));
    assertThat(asResourceReq.getLimits(), hasResourceQuantity("memory", "128Mi"));
  }

  private void configureDomainWithResourceRequirements(DomainResource domain) {
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
  void whenPodSecurityContextConfiguredOnManagedServerOverrideClusterAndDomain() {
    configureDomainWithPodSecurityContext(domain);
    V1PodSecurityContext ms1PodSecCtx =
        info.getServer(SERVER1, CLUSTER_NAME).getPodSecurityContext();

    // Server1 only defines runAsGroup = 422L and SELinuxOption level = server, role = slave,
    assertThat(ms1PodSecCtx.getRunAsGroup(), is(422L));
    assertThat(ms1PodSecCtx.getSeLinuxOptions().getLevel(), is("server"));
    assertThat(ms1PodSecCtx.getSeLinuxOptions().getRole(), is("slave"));

    // Since at the server level the type and user are not defined those should be null
    assertThat(ms1PodSecCtx.getSeLinuxOptions().getType(), nullValue());
    assertThat(ms1PodSecCtx.getSeLinuxOptions().getUser(), nullValue());

    // These are inherited from the cluster
    assertThat(ms1PodSecCtx.getSysctls(), contains(CLUSTER_SYSCTL));
    assertThat(ms1PodSecCtx.getRunAsNonRoot(), is(true));

    // The following are not defined either in domain, cluster or server, so they should be null
    assertThat(ms1PodSecCtx.getRunAsUser(), nullValue());
    assertThat(ms1PodSecCtx.getFsGroup(), nullValue());
    assertThat(ms1PodSecCtx.getSupplementalGroups(), nullValue());
  }

  @Test
  void whenPodSecurityContextConfiguredOnClusterOverrideDomain() {
    configureDomainWithPodSecurityContext(domain);
    V1PodSecurityContext msPodSecCtx =
        info.getServer("any", CLUSTER_NAME).getPodSecurityContext();

    // Since "any" is not defined in the domain, it will take the values from CLUSTER_NAME
    assertThat(msPodSecCtx.getRunAsGroup(), is(421L));
    assertThat(msPodSecCtx.getSysctls(), contains(CLUSTER_SYSCTL));
    assertThat(msPodSecCtx.getRunAsNonRoot(), is(true));
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
  void whenPodSecurityContextConfiguredOnAdminServerOverrideClusterAndDomain() {
    configureDomainWithPodSecurityContext(domain);
    V1PodSecurityContext asPodSecCtx = domain.getAdminServerSpec().getPodSecurityContext();

    // Since admin server only defines runAsNonRoot = false
    assertThat(asPodSecCtx.getRunAsNonRoot(), is(false));

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

  private void configureDomainWithPodSecurityContext(DomainResource domain) {
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
  void whenDefaultContainerSecurityContextConfiguredOnManagedServer() {
    V1SecurityContext ms1ContainerSecSpec =
        info.getServer(SERVER1, CLUSTER_NAME).getContainerSecurityContext();

    assertThat(ms1ContainerSecSpec.getRunAsNonRoot(), is(true));
    assertThat(ms1ContainerSecSpec.getPrivileged(), is(false));
    assertThat(ms1ContainerSecSpec.getAllowPrivilegeEscalation(), is(false));
    assertThat(ms1ContainerSecSpec.getCapabilities().getDrop(), contains("ALL"));
  }

  @Test
  void whenPodSecurityContextConfiguredNoDefaultContainerSecurityContextOnManagedServer() {
    configureDomainWithPodSecurityContext(domain);
    V1SecurityContext ms1ContainerSecSpec =
        info.getServer(SERVER1, CLUSTER_NAME).getContainerSecurityContext();

    assertThat(ms1ContainerSecSpec, nullValue());
  }

  @Test
  void whenContainerSecurityContextConfiguredOnManagedServerOverrideClusterAndDomain() {
    configureDomainWithContainerSecurityContext(domain);
    V1SecurityContext ms1ContainerSecSpec =
        info.getServer(SERVER1, CLUSTER_NAME).getContainerSecurityContext();

    // Since SERVER1 only overrides runAsGroup = 422, Capabilities to be SYS_TIME
    assertThat(ms1ContainerSecSpec.getRunAsGroup(), is(422L));
    assertThat(ms1ContainerSecSpec.getAllowPrivilegeEscalation(), is(false));
    assertThat(
        ms1ContainerSecSpec.getCapabilities().getAdd(), contains("SYS_TIME", "CHOWN", "SYS_BOOT"));
    assertThat(ms1ContainerSecSpec.getSeLinuxOptions().getLevel(), is("server"));
    assertThat(ms1ContainerSecSpec.getSeLinuxOptions().getRole(), is("slave"));
    assertThat(ms1ContainerSecSpec.getSeLinuxOptions().getType(), nullValue());
    assertThat(ms1ContainerSecSpec.getRunAsUser(), nullValue());
  }

  @Test
  void whenContainerSecurityContextConfiguredOnClusterOverrideDomain() {
    configureDomainWithContainerSecurityContext(domain);
    V1SecurityContext ms2ContainerSecSpec =
        info.getServer("any", CLUSTER_NAME).getContainerSecurityContext();

    // Since "any" is not defined in the domain, it will take the values from CLUSTER_NAME
    assertThat(ms2ContainerSecSpec.getRunAsGroup(), is(421L));
    assertThat(ms2ContainerSecSpec.getAllowPrivilegeEscalation(), is(false));
    assertThat(
        ms2ContainerSecSpec.getCapabilities().getAdd(), contains("SYS_TIME", "CHOWN", "SYS_BOOT"));
    assertThat(ms2ContainerSecSpec.getSeLinuxOptions().getLevel(), is("cluster"));
    assertThat(ms2ContainerSecSpec.getSeLinuxOptions().getRole(), is("admin"));
    assertThat(ms2ContainerSecSpec.getSeLinuxOptions().getType(), is("admin"));

    // The following are not defined either in domain, cluster or server, so they should be null
    assertThat(ms2ContainerSecSpec.getRunAsUser(), nullValue());
    assertThat(ms2ContainerSecSpec.getPrivileged(), nullValue());
  }

  @Test
  void whenContainerSecurityContextConfiguredOnAdminServerOverrideClusterAndDomain() {
    configureDomainWithContainerSecurityContext(domain);
    V1SecurityContext asContainerSecSpec =
        domain.getAdminServerSpec().getContainerSecurityContext();

    // Since admin server only defines runAsNonRoot = false
    assertThat(asContainerSecSpec.getRunAsNonRoot(), is(false));

    // The rest should be identical to the domain
    assertThat(asContainerSecSpec.getRunAsGroup(), is(420L));
    assertThat(asContainerSecSpec.getAllowPrivilegeEscalation(), is(false));
    assertThat(asContainerSecSpec.getCapabilities().getAdd(), contains("CHOWN", "SYS_BOOT"));
    assertThat(asContainerSecSpec.getSeLinuxOptions().getLevel(), is("domain"));
    assertThat(asContainerSecSpec.getSeLinuxOptions().getRole(), is("admin"));
    assertThat(asContainerSecSpec.getSeLinuxOptions().getUser(), is("weblogic"));

    // The following are not defined either in domain or admin server, so they should be null
    assertThat(asContainerSecSpec.getSeLinuxOptions().getType(), nullValue());
    assertThat(asContainerSecSpec.getRunAsUser(), nullValue());
    assertThat(asContainerSecSpec.getPrivileged(), nullValue());
  }

  private void configureDomainWithContainerSecurityContext(DomainResource domain) {
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
  void whenDomainReadFromYaml_unconfiguredServerHasDomainDefaults() throws IOException {
    DomainPresenceInfo info = readDomainPresence(DOMAIN_V2_SAMPLE_YAML);
    EffectiveServerSpec effectiveServerSpec = info.getServer("server0", null);

    assertThat(effectiveServerSpec.getImage(), equalTo(DEFAULT_IMAGE));
    assertThat(effectiveServerSpec.getImagePullPolicy(), equalTo("IfNotPresent"));
    assertThat(effectiveServerSpec.getImagePullSecrets().get(0).getName(), equalTo("pull-secret1"));
    assertThat(effectiveServerSpec.getImagePullSecrets().get(1).getName(), equalTo("pull-secret2"));
    assertThat(effectiveServerSpec.getEnvironmentVariables(), contains(envVar("var1", "value0")));
    assertThat(info.getDomain().getConfigOverrides(), equalTo("overrides-config-map"));
    assertThat(
        info.getDomain().getConfigOverrideSecrets(),
        containsInAnyOrder("overrides-secret-1", "overrides-secret-2"));
    assertThat(effectiveServerSpec.shouldStart(1), is(true));
  }

  @Test
  void whenDomainReadFromYamlWithNoSetting_defaultsToDomainHomeInImage() throws IOException {
    List<KubernetesObject> resources = readFromYaml(DOMAIN_V2_SAMPLE_YAML);
    DomainResource domain = (DomainResource) resources.get(0);

    assertThat(domain.getDomainHomeSourceType(), equalTo(DomainSourceType.IMAGE));
  }

  @Test
  void whenDomainReadFromYaml_domainHomeSourceTypePersistentVolume() throws IOException {
    List<KubernetesObject> resources = readFromYaml(DOMAIN_V2_SAMPLE_YAML_2);
    DomainResource domain = (DomainResource) resources.get(0);

    assertThat(domain.getDomainHomeSourceType(), equalTo(DomainSourceType.PERSISTENT_VOLUME));
  }

  @Test
  void whenDomainReadFromYamlWithNoSetting_defaultsToServerOutInPodLog() throws IOException {
    List<KubernetesObject> resources = readFromYaml(DOMAIN_V2_SAMPLE_YAML);
    DomainResource domain = (DomainResource) resources.get(0);

    assertThat(domain.isIncludeServerOutInPodLog(), is(true));
  }

  @Test
  void whenDomainReadFromYaml_serverOutInPodLogIsSet() throws IOException {
    List<KubernetesObject> resources = readFromYaml(DOMAIN_V2_SAMPLE_YAML_2);
    DomainResource domain = (DomainResource) resources.get(0);

    assertThat(domain.isIncludeServerOutInPodLog(), is(false));
  }

  @Test
  void whenDomainReadFromYaml_unconfiguredClusteredServerHasDomainDefaults()
      throws IOException {
    DomainPresenceInfo info = readDomainPresence(DOMAIN_V2_SAMPLE_YAML);
    EffectiveServerSpec effectiveServerSpec = info.getServer("server0", "cluster0");

    assertThat(effectiveServerSpec.getImage(), equalTo(DEFAULT_IMAGE));
    assertThat(effectiveServerSpec.getImagePullPolicy(), equalTo("IfNotPresent"));
    assertThat(effectiveServerSpec.getImagePullSecrets().get(0).getName(), equalTo("pull-secret1"));
    assertThat(effectiveServerSpec.getImagePullSecrets().get(1).getName(), equalTo("pull-secret2"));
    assertThat(info.getDomain().getConfigOverrides(), equalTo("overrides-config-map"));
    assertThat(
        info.getDomain().getConfigOverrideSecrets(),
        containsInAnyOrder("overrides-secret-1", "overrides-secret-2"));
    assertThat(effectiveServerSpec.getEnvironmentVariables(), contains(envVar("var1", "value0")));
    assertThat(effectiveServerSpec.shouldStart(1), is(true));
  }

  @Test
  void whenDomainReadFromYaml_adminServerOverridesDefaults() throws IOException {
    List<KubernetesObject> resources = readFromYaml(DOMAIN_V2_SAMPLE_YAML);
    DomainResource domain = (DomainResource) resources.get(0);
    EffectiveServerSpec effectiveServerSpec = domain.getAdminServerSpec();

    assertThat(effectiveServerSpec.getEnvironmentVariables(), contains(envVar("var1", "value1")));
  }

  @Test
  void whenDomainReadFromYaml_server1OverridesDefaults() throws IOException {
    DomainPresenceInfo info = readDomainPresence(DOMAIN_V2_SAMPLE_YAML);
    EffectiveServerSpec effectiveServerSpec = info.getServer("server1", "cluster1");

    assertThat(effectiveServerSpec.getImage(), equalTo(DEFAULT_IMAGE));
    assertThat(
        effectiveServerSpec.getEnvironmentVariables(),
        containsInAnyOrder(
            envVar("JAVA_OPTIONS", "-server"),
            envVar(
                "USER_MEM_ARGS",
                "-Djava.security.egd=file:/dev/./urandom "),
            envVar("var1", "value0")));
    assertThat(info.getDomain().getConfigOverrides(), equalTo("overrides-config-map"));
    assertThat(
        info.getDomain().getConfigOverrideSecrets(),
        containsInAnyOrder("overrides-secret-1", "overrides-secret-2"));
  }

  @Test
  void whenDomainReadFromYaml_cluster2OverridesDefaults() throws IOException {
    DomainPresenceInfo info = readDomainPresence(DOMAIN_V2_SAMPLE_YAML);
    EffectiveServerSpec effectiveServerSpec = info.getServer("server2", "cluster2");

    assertThat(
        effectiveServerSpec.getEnvironmentVariables(),
        containsInAnyOrder(
            envVar("JAVA_OPTIONS", "-verbose"),
            envVar("USER_MEM_ARGS", "-Xms64m -Xmx256m "),
            envVar("var1", "value0")));
    assertThat(info.getDomain().getConfigOverrides(), equalTo("overrides-config-map"));
    assertThat(
        info.getDomain().getConfigOverrideSecrets(),
        containsInAnyOrder("overrides-secret-1", "overrides-secret-2"));
  }

  @Test
  void whenDomainReadFromYaml_AdminAndManagedOverrideDomainNodeSelectors()
      throws IOException {
    DomainPresenceInfo info = readDomainPresence(DOMAIN_V2_SAMPLE_YAML);
    final EffectiveServerSpec server1Spec = info.getServer("server1", null);
    final EffectiveServerSpec server2Spec = info.getServer("server2", null);
    assertThat(info.getDomain().getAdminServerSpec().getNodeSelectors(), hasEntry("os_arch", "x86_64"));
    assertThat(info.getDomain().getAdminServerSpec().getNodeSelectors(), hasEntry("os", "linux"));
    assertThat(server2Spec.getNodeSelectors(), hasEntry("os_arch", "x86"));
    assertThat(server2Spec.getNodeSelectors(), hasEntry("os", "linux"));
    assertThat(server1Spec.getNodeSelectors(), hasEntry("os_arch", "arm64"));
    assertThat(server1Spec.getNodeSelectors(), hasEntry("os", "linux"));
  }

  @Test
  void whenDomainReadFromYaml_ManagedServerOverrideDomainResourceRequirements()
      throws IOException {
    DomainPresenceInfo info = readDomainPresence(DOMAIN_V2_SAMPLE_YAML);
    final V1ResourceRequirements server1ResReq = info.getServer("server1", null).getResources();
    final V1ResourceRequirements server2ResReq = info.getServer("server2", null).getResources();

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
  void whenDomainReadFromYaml_AdminAndManagedOverrideResourceRequirements()
      throws IOException {
    List<KubernetesObject> resources = readFromYaml(DOMAIN_V2_SAMPLE_YAML);
    DomainResource domain = (DomainResource) resources.get(0);
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
  void whenDomainReadFromYaml_AdminServiceIsDefined() throws IOException {
    List<KubernetesObject> resources = readFromYaml(DOMAIN_V2_SAMPLE_YAML);
    DomainResource domain = (DomainResource) resources.get(0);
    AdminService adminService = domain.getAdminServerSpec().getAdminService();

    assertThat(adminService.getChannels(), containsInAnyOrder(
          List.of(channelWith("default", 7001), channelWith("extra", 7011))));
    assertThat(
        adminService.getLabels(), both(hasEntry("red", "maroon")).and(hasEntry("blue", "azure")));
    assertThat(
        adminService.getAnnotations(),
        both(hasEntry("sunday", "dimanche")).and(hasEntry("monday", "lundi")));
  }

  @Test
  void whenDomain2ReadFromYaml_unknownClusterUseDefaultReplicaCount() throws IOException {
    DomainPresenceInfo info = readDomainPresence(DOMAIN_V2_SAMPLE_YAML_2);

    assertThat(info.getReplicaCount("unknown"), equalTo(3));
  }

  @Test
  void whenDomain2ReadFromYaml_unconfiguredClusterUseDefaultReplicaCount()
      throws IOException {
    DomainPresenceInfo info = readDomainPresence(DOMAIN_V2_SAMPLE_YAML_2);

    assertThat(info.getReplicaCount("cluster1"), equalTo(3));
  }

  @Test
  void whenDomain2ReadFromYaml_serverReadsDomainDefaultOfNever() throws IOException {
    DomainPresenceInfo info = readDomainPresence(DOMAIN_V2_SAMPLE_YAML_2);
    EffectiveServerSpec effectiveServerSpec = info.getServer("server2", null);

    assertThat(effectiveServerSpec.shouldStart(0), is(false));
  }

  @Test
  void whenDomain2ReadFromYaml_serverConfiguresReadinessProbe() throws IOException {
    DomainPresenceInfo info = readDomainPresence(DOMAIN_V2_SAMPLE_YAML_2);
    EffectiveServerSpec effectiveServerSpec = info.getServer("server2", "cluster1");

    assertThat(effectiveServerSpec.getReadinessProbe().getInitialDelaySeconds(), equalTo(10));
    assertThat(effectiveServerSpec.getReadinessProbe().getTimeoutSeconds(), equalTo(15));
    assertThat(effectiveServerSpec.getReadinessProbe().getPeriodSeconds(), equalTo(20));
  }

  @Test
  void whenDomain2ReadFromYaml_serverConfiguresLivenessProbe() throws IOException {
    DomainPresenceInfo info = readDomainPresence(DOMAIN_V2_SAMPLE_YAML_2);
    EffectiveServerSpec effectiveServerSpec = info.getServer("server2", "cluster1");

    assertThat(effectiveServerSpec.getLivenessProbe().getInitialDelaySeconds(), equalTo(20));
    assertThat(effectiveServerSpec.getLivenessProbe().getTimeoutSeconds(), equalTo(5));
    assertThat(effectiveServerSpec.getLivenessProbe().getPeriodSeconds(), equalTo(18));
  }

  @Test
  void whenDomain2ReadFromYaml_clusterHasNodeSelector() throws IOException {
    DomainPresenceInfo info = readDomainPresence(DOMAIN_V2_SAMPLE_YAML_2);
    EffectiveServerSpec effectiveServerSpec = info.getServer(SERVER2, "cluster1");
    assertThat(effectiveServerSpec.getNodeSelectors(), hasEntry("os", "linux"));
  }

  @Test
  void whenDomain2ReadFromYaml_clusterAndManagedServerHaveDifferentNodeSelectors()
      throws IOException {
    DomainPresenceInfo info = readDomainPresence(DOMAIN_V2_SAMPLE_YAML_2);
    EffectiveServerSpec effectiveServerSpec = info.getServer("server2", "cluster1");
    assertThat(effectiveServerSpec.getNodeSelectors(), hasEntry("os", "linux"));
    assertThat(effectiveServerSpec.getNodeSelectors(), hasEntry("os_type", "oel7"));
  }

  @Test
  void whenDomain2ReadFromYaml_ManagedServerInheritContainerSecurityContextFromDomain()
      throws IOException {
    DomainPresenceInfo info = readDomainPresence(DOMAIN_V2_SAMPLE_YAML_2);

    V1SecurityContext server2ContainerSecCtx =
        info.getServer("server2", null).getContainerSecurityContext();
    assertThat(server2ContainerSecCtx.getRunAsGroup(), is(422L));
    assertThat(server2ContainerSecCtx.getAllowPrivilegeEscalation(), is(false));
    assertThat(server2ContainerSecCtx.getCapabilities().getAdd(), contains("CHOWN", "SYS_BOOT"));
    assertThat(server2ContainerSecCtx.getSeLinuxOptions().getLevel(), is("server"));
    assertThat(server2ContainerSecCtx.getSeLinuxOptions().getRole(), is("slave"));
    assertThat(server2ContainerSecCtx.getSeLinuxOptions().getType(), nullValue());
    assertThat(server2ContainerSecCtx.getRunAsUser(), nullValue());
  }

  @Test
  void whenDomain2ReadFromYamlTwice_objectsEquals()
      throws IOException {
    List<KubernetesObject> resources = readFromYaml(DOMAIN_V2_SAMPLE_YAML);
    DomainResource domain1 = (DomainResource) resources.get(0);
    List<KubernetesObject> resources2 = readFromYaml(DOMAIN_V2_SAMPLE_YAML);
    DomainResource domain2 = (DomainResource) resources2.get(0);

    assertThat(domain1, equalTo(domain2));
  }

  @Test
  void whenDomain2ReadFromYamlTwice_matchingIntrospectionVersionValuesLeaveDomainsEqual()
      throws IOException {
    List<KubernetesObject> resources = readFromYaml(DOMAIN_V2_SAMPLE_YAML);
    DomainResource domain1 = (DomainResource) resources.get(0);
    List<KubernetesObject> resources2 = readFromYaml(DOMAIN_V2_SAMPLE_YAML);
    DomainResource domain2 = (DomainResource) resources2.get(0);

    domain1.getSpec().setIntrospectVersion("123");
    domain2.getSpec().setIntrospectVersion("123");
    assertThat(domain1, equalTo(domain2));
  }

  @Test
  void whenDomain2ReadFromYamlTwice_differentIntrospectionVersionValuesLeaveDomainsUnequal()
      throws IOException {
    List<KubernetesObject> resources = readFromYaml(DOMAIN_V2_SAMPLE_YAML);
    DomainResource domain1 = (DomainResource) resources.get(0);
    List<KubernetesObject> resources2 = readFromYaml(DOMAIN_V2_SAMPLE_YAML);
    DomainResource domain2 = (DomainResource) resources2.get(0);

    domain1.getSpec().setIntrospectVersion("123");
    domain2.getSpec().setIntrospectVersion("124");
    assertThat(domain1, not(equalTo(domain2)));
  }

  @Test
  void whenDomain2ReadFromYaml_ManagedServerInheritContainerSecurityContextFromCluster()
      throws IOException {
    DomainPresenceInfo info = readDomainPresence(DOMAIN_V2_SAMPLE_YAML_2);

    V1SecurityContext server1ContainerSecCtx =
        info.getServer("server1", "cluster1").getContainerSecurityContext();
    assertThat(server1ContainerSecCtx.getRunAsGroup(), is(421L));
    assertThat(server1ContainerSecCtx.getAllowPrivilegeEscalation(), is(false));
    assertThat(
        server1ContainerSecCtx.getCapabilities().getAdd(),
        contains("SYS_TIME", "CHOWN", "SYS_BOOT"));
    assertThat(server1ContainerSecCtx.getSeLinuxOptions().getLevel(), is("cluster"));
    assertThat(server1ContainerSecCtx.getSeLinuxOptions().getRole(), is("admin"));
    assertThat(server1ContainerSecCtx.getSeLinuxOptions().getType(), is("admin"));
    assertThat(server1ContainerSecCtx.getRunAsUser(), nullValue());
  }

  @Test
  void whenDomain2ReadFromYaml_AdminServerInheritContainerSecurityContextFromDomain()
      throws IOException {
    List<KubernetesObject> resources = readFromYaml(DOMAIN_V2_SAMPLE_YAML_2);
    DomainResource domain = (DomainResource) resources.get(0);

    V1SecurityContext asContainerSecCtx = domain.getAdminServerSpec().getContainerSecurityContext();
    assertThat(asContainerSecCtx.getRunAsGroup(), is(420L));
    assertThat(asContainerSecCtx.getAllowPrivilegeEscalation(), is(false));
    assertThat(asContainerSecCtx.getCapabilities().getAdd(), contains("CHOWN", "SYS_BOOT"));
    assertThat(asContainerSecCtx.getSeLinuxOptions().getLevel(), is("domain"));
    assertThat(asContainerSecCtx.getSeLinuxOptions().getRole(), is("admin"));
    assertThat(asContainerSecCtx.getSeLinuxOptions().getType(), nullValue());
    assertThat(asContainerSecCtx.getRunAsUser(), nullValue());
    assertThat(asContainerSecCtx.getPrivileged(), nullValue());
  }

  @Test
  void whenDomain2ReadFromYaml_ManagedServerInheritPodSecurityContextFromDomain()
      throws IOException {
    DomainPresenceInfo info = readDomainPresence(DOMAIN_V2_SAMPLE_YAML_2);
    V1PodSecurityContext server2PodSecCtx =
        info.getServer("server2", null).getPodSecurityContext();
    assertThat(server2PodSecCtx.getRunAsGroup(), is(422L));
    assertThat(server2PodSecCtx.getSysctls(), contains(DOMAIN_SYSCTL));
    assertThat(server2PodSecCtx.getSeLinuxOptions().getLevel(), is("server"));
    assertThat(server2PodSecCtx.getSeLinuxOptions().getRole(), is("slave"));
    assertThat(server2PodSecCtx.getSeLinuxOptions().getType(), nullValue());
    assertThat(server2PodSecCtx.getRunAsUser(), nullValue());
  }

  @Test
  void whenDomain2ReadFromYaml_ManagedServerInheritPodSecurityContextFromCluster()
      throws IOException {
    DomainPresenceInfo info = readDomainPresence(DOMAIN_V2_SAMPLE_YAML_2);
    V1PodSecurityContext server1PodSecCtx =
        info.getServer("server1", "cluster1").getPodSecurityContext();
    assertThat(server1PodSecCtx.getRunAsGroup(), is(421L));
    assertThat(server1PodSecCtx.getSysctls(), contains(CLUSTER_SYSCTL));
    assertThat(server1PodSecCtx.getSeLinuxOptions().getLevel(), is("cluster"));
    assertThat(server1PodSecCtx.getSeLinuxOptions().getRole(), is("admin"));
    assertThat(server1PodSecCtx.getSeLinuxOptions().getType(), is("admin"));
    assertThat(server1PodSecCtx.getRunAsUser(), nullValue());
  }

  @Test
  void whenDomain2ReadFromYaml_AdminServerInheritPodSecurityContextFromDomain()
      throws IOException {
    List<KubernetesObject> resources = readFromYaml(DOMAIN_V2_SAMPLE_YAML_2);
    DomainResource domain = (DomainResource) resources.get(0);
    V1PodSecurityContext asPodSecCtx = domain.getAdminServerSpec().getPodSecurityContext();
    assertThat(asPodSecCtx.getRunAsGroup(), is(420L));
    assertThat(asPodSecCtx.getSysctls(), contains(DOMAIN_SYSCTL));
    assertThat(asPodSecCtx.getSeLinuxOptions().getLevel(), is("domain"));
    assertThat(asPodSecCtx.getSeLinuxOptions().getRole(), is("admin"));
    assertThat(asPodSecCtx.getSeLinuxOptions().getType(), nullValue());
    assertThat(asPodSecCtx.getRunAsUser(), nullValue());
  }

  @Test
  void whenDomain2ReadFromYaml_InitContainersAreReadFromServerSpec() throws IOException {
    List<KubernetesObject> resources = readFromYaml(DOMAIN_V2_SAMPLE_YAML_4);
    DomainResource domain = (DomainResource) resources.get(0);

    List<V1Container> serverSpecInitContainers = domain.getSpec().getInitContainers();
    assertThat(serverSpecInitContainers.isEmpty(), is(false));
    assertThat(serverSpecInitContainers.size(), is(1));
    assertThat(serverSpecInitContainers.get(0).getName(), is("test1"));
    assertThat(serverSpecInitContainers.get(0).getImage(), is("busybox"));
    assertThat(serverSpecInitContainers.get(0).getCommand().get(2), containsString("serverPod"));
  }

  @Test
  void whenDomain2ReadFromYaml_InitContainersAreReadFromAdminServerSpec()
      throws IOException {
    List<KubernetesObject> resources = readFromYaml(DOMAIN_V2_SAMPLE_YAML_4);
    DomainResource domain = (DomainResource) resources.get(0);

    List<V1Container> serverSpecInitContainers = domain.getAdminServerSpec().getInitContainers();
    assertThat(serverSpecInitContainers.isEmpty(), is(false));
    assertThat(serverSpecInitContainers.size(), is(2));
    assertThat(serverSpecInitContainers.get(0).getName(), is("test2"));
    assertThat(serverSpecInitContainers.get(0).getImage(), is("busybox"));
    assertThat(serverSpecInitContainers.get(0).getCommand().get(2), containsString("admin server"));
    assertThat(serverSpecInitContainers.get(1).getName(), is("test1"));
    assertThat(serverSpecInitContainers.get(1).getImage(), is("busybox"));
    assertThat(serverSpecInitContainers.get(1).getCommand().get(2), containsString("serverPod"));
  }

  @Test
  void whenDomain2ReadFromYaml_InitContainersAreReadFromManagedServerSpec()
      throws IOException {
    DomainPresenceInfo info = readDomainPresence(DOMAIN_V2_SAMPLE_YAML_4);

    List<V1Container> serverSpecInitContainers =
        info.getServer("server1", null).getInitContainers();
    assertThat(serverSpecInitContainers.isEmpty(), is(false));
    assertThat(serverSpecInitContainers.size(), is(2));
    assertThat(serverSpecInitContainers.get(0).getName(), is("test3"));
    assertThat(serverSpecInitContainers.get(0).getImage(), is("busybox"));
    assertThat(
        serverSpecInitContainers.get(0).getCommand().get(2), containsString("managed server"));
    assertThat(serverSpecInitContainers.get(1).getName(), is("test1"));
    assertThat(serverSpecInitContainers.get(1).getImage(), is("busybox"));
    assertThat(serverSpecInitContainers.get(1).getCommand().get(2), containsString("serverPod"));
  }

  @Test
  void whenDomain2ReadFromYaml_InitContainersAreInheritedFromServerSpec()
      throws IOException {
    DomainPresenceInfo info = readDomainPresence(DOMAIN_V2_SAMPLE_YAML_4);

    List<V1Container> serverSpecInitContainers =
        info.getServer("server2", null).getInitContainers();
    assertThat(serverSpecInitContainers.isEmpty(), is(false));
    assertThat(serverSpecInitContainers.size(), is(1));
    assertThat(serverSpecInitContainers.get(0).getName(), is("test1"));
    assertThat(serverSpecInitContainers.get(0).getImage(), is("busybox"));
    assertThat(serverSpecInitContainers.get(0).getCommand().get(2), containsString("serverPod"));
  }

  @Test
  void whenDomain2ReadFromYaml_InitContainersAreReadFromClusteredServerSpec()
      throws IOException {
    DomainPresenceInfo info = readDomainPresence(DOMAIN_V2_SAMPLE_YAML_4);

    List<V1Container> serverSpecInitContainers = info.getCluster("cluster2").getInitContainers();
    assertThat(serverSpecInitContainers.isEmpty(), is(false));
    assertThat(serverSpecInitContainers.size(), is(2));
    assertThat(serverSpecInitContainers.get(0).getName(), is("test4"));
    assertThat(serverSpecInitContainers.get(0).getImage(), is("busybox"));
    assertThat(
        serverSpecInitContainers.get(0).getCommand().get(2), containsString("cluster member"));
    assertThat(serverSpecInitContainers.get(1).getName(), is("test1"));
    assertThat(serverSpecInitContainers.get(1).getImage(), is("busybox"));
    assertThat(serverSpecInitContainers.get(1).getCommand().get(2), containsString("serverPod"));
  }

  @Test
  void whenDomain2ReadFromYaml_ContainersAreReadFromServerSpec() throws IOException {
    List<KubernetesObject> resources = readFromYaml(DOMAIN_V2_SAMPLE_YAML_4);
    DomainResource domain = (DomainResource) resources.get(0);

    List<V1Container> serverSpecContainers = domain.getSpec().getContainers();
    assertThat(serverSpecContainers.isEmpty(), is(false));
    assertThat(serverSpecContainers.size(), is(1));
    assertThat(serverSpecContainers.get(0).getName(), is("cont1"));
    assertThat(serverSpecContainers.get(0).getImage(), is("busybox"));
    assertThat(serverSpecContainers.get(0).getCommand().get(2), containsString("cat cont"));
  }

  @Test
  void whenDomain2ReadFromYaml_ContainersAreReadFromAdminServerSpec() throws IOException {
    List<KubernetesObject> resources = readFromYaml(DOMAIN_V2_SAMPLE_YAML_4);
    DomainResource domain = (DomainResource) resources.get(0);

    List<V1Container> serverSpecContainers = domain.getAdminServerSpec().getContainers();
    assertThat(serverSpecContainers.isEmpty(), is(false));
    assertThat(serverSpecContainers.size(), is(2));
    assertThat(serverSpecContainers.get(0).getName(), is("cont2"));
    assertThat(serverSpecContainers.get(0).getImage(), is("busybox"));
    assertThat(serverSpecContainers.get(0).getCommand().get(2), containsString("cat date"));
    assertThat(serverSpecContainers.get(1).getName(), is("cont1"));
    assertThat(serverSpecContainers.get(1).getImage(), is("busybox"));
    assertThat(serverSpecContainers.get(1).getCommand().get(2), containsString("cat cont"));
  }

  @Test
  void whenDomain2ReadFromYaml_ContainersAreReadFromManagedServerSpec() throws IOException {
    DomainPresenceInfo info = readDomainPresence(DOMAIN_V2_SAMPLE_YAML_4);

    List<V1Container> serverSpecContainers = info.getServer("server1", null).getContainers();
    assertThat(serverSpecContainers.isEmpty(), is(false));
    assertThat(serverSpecContainers.size(), is(2));
    assertThat(serverSpecContainers.get(0).getName(), is("cont3"));
    assertThat(serverSpecContainers.get(0).getImage(), is("busybox"));
    assertThat(serverSpecContainers.get(0).getCommand().get(2), containsString("cat ls"));
    assertThat(serverSpecContainers.get(1).getName(), is("cont1"));
    assertThat(serverSpecContainers.get(1).getImage(), is("busybox"));
    assertThat(serverSpecContainers.get(1).getCommand().get(2), containsString("cat cont"));
  }

  @Test
  void whenDomain2ReadFromYaml_ContainersAreInheritedFromServerSpec() throws IOException {
    DomainPresenceInfo info = readDomainPresence(DOMAIN_V2_SAMPLE_YAML_4);

    List<V1Container> serverSpecContainers = info.getServer("server2", null).getContainers();
    assertThat(serverSpecContainers.isEmpty(), is(false));
    assertThat(serverSpecContainers.size(), is(1));
    assertThat(serverSpecContainers.get(0).getName(), is("cont1"));
    assertThat(serverSpecContainers.get(0).getImage(), is("busybox"));
    assertThat(serverSpecContainers.get(0).getCommand().get(2), containsString("cat cont"));
  }

  @Test
  void whenDomain2ReadFromYaml_ContainersAreReadFromClusteredServerSpec()
      throws IOException {
    DomainPresenceInfo info = readDomainPresence(DOMAIN_V2_SAMPLE_YAML_4);

    List<V1Container> serverSpecContainers = info.getCluster("cluster2").getContainers();
    assertThat(serverSpecContainers.isEmpty(), is(false));
    assertThat(serverSpecContainers.size(), is(2));
    assertThat(serverSpecContainers.get(0).getName(), is("cont4"));
    assertThat(serverSpecContainers.get(0).getImage(), is("busybox"));
    assertThat(serverSpecContainers.get(0).getCommand().get(2), containsString("cat tmp"));
    assertThat(serverSpecContainers.get(1).getName(), is("cont1"));
    assertThat(serverSpecContainers.get(1).getImage(), is("busybox"));
    assertThat(serverSpecContainers.get(1).getCommand().get(2), containsString("cat cont"));
  }

  @Test
  void whenDomain2ReadFromYaml_ShutdownIsReadFromSpec() throws IOException {
    List<KubernetesObject> resources = readFromYaml(DOMAIN_V2_SAMPLE_YAML_4);
    DomainResource domain = (DomainResource) resources.get(0);

    Shutdown shutdown = domain.getSpec().getShutdown();
    assertThat(shutdown.getShutdownType(), is(ShutdownType.GRACEFUL));
    assertThat(shutdown.getTimeoutSeconds(), is(45L));
  }

  @Test
  void whenDomain2ReadFromYaml_ShutdownIsReadFromClusterSpec() throws IOException {
    DomainPresenceInfo info = readDomainPresence(DOMAIN_V2_SAMPLE_YAML_4);

    Shutdown shutdown = info.getCluster("cluster2").getShutdown();
    assertThat(shutdown.getShutdownType(), is(ShutdownType.GRACEFUL));
    assertThat(shutdown.getTimeoutSeconds(), is(45L));
    assertThat(shutdown.getIgnoreSessions(), is(true));
  }

  @Test
  void whenDomain2ReadFromYaml_ShutdownIsReadFromServerSpec() throws IOException {
    DomainPresenceInfo info = readDomainPresence(DOMAIN_V2_SAMPLE_YAML_4);

    Shutdown shutdown = info.getServer("server2", "cluster2").getShutdown();
    assertThat(shutdown.getShutdownType(), is(ShutdownType.GRACEFUL));
    assertThat(shutdown.getTimeoutSeconds(), is(60L));
    assertThat(shutdown.getIgnoreSessions(), is(false));
  }

  @Test
  void whenDomain2ReadFromYaml_RestartPolicyIsReadFromSpec() throws IOException {
    List<KubernetesObject> resources = readFromYaml(DOMAIN_V2_SAMPLE_YAML_5);
    DomainResource domain = (DomainResource) resources.get(0);

    String restartPolicy = domain.getSpec().getRestartPolicy();
    assertThat(restartPolicy, is("OnFailure"));
  }

  @Test
  void whenDomain2ReadFromYaml_RestartPolicyIsReadFromClusterSpec() throws IOException {
    DomainPresenceInfo info = readDomainPresence(DOMAIN_V2_SAMPLE_YAML_5);

    String restartPolicy = info.getCluster("cluster2").getRestartPolicy();
    assertThat(restartPolicy, is("OnFailure"));
  }

  @Test
  void whenDomain2ReadFromYaml_RuntimeClassNameIsReadFromSpec() throws IOException {
    List<KubernetesObject> resources = readFromYaml(DOMAIN_V2_SAMPLE_YAML_5);
    DomainResource domain = (DomainResource) resources.get(0);

    String runtimeClassName = domain.getSpec().getRuntimeClassName();
    assertThat(runtimeClassName, is("weblogic-class"));
  }

  @Test
  void whenDomain2ReadFromYaml_RuntimeClassNameIsReadFromClusterSpec() throws IOException {
    DomainPresenceInfo info = readDomainPresence(DOMAIN_V2_SAMPLE_YAML_5);

    String runtimeClassName = info.getCluster("cluster2").getRuntimeClassName();
    assertThat(runtimeClassName, is("weblogic-class"));
  }

  @Test
  void whenDomain2ReadFromYaml_SchedulerNameIsReadFromSpec() throws IOException {
    List<KubernetesObject> resources = readFromYaml(DOMAIN_V2_SAMPLE_YAML_5);
    DomainResource domain = (DomainResource) resources.get(0);

    String schedulerName = domain.getSpec().getSchedulerName();
    assertThat(schedulerName, is("my-scheduler"));
  }

  @Test
  void whenDomain2ReadFromYaml_SchedulerClassNameIsReadFromClusterSpec() throws IOException {
    DomainPresenceInfo info = readDomainPresence(DOMAIN_V2_SAMPLE_YAML_5);

    String schedulerName = info.getCluster("cluster2").getSchedulerName();
    assertThat(schedulerName, is("my-scheduler"));
  }

  @Test
  void whenDomain2ReadFromYaml_sessionAffinityIsReadFromClusteredServerSpec()
      throws IOException {
    DomainPresenceInfo info = readDomainPresence(DOMAIN_V2_SAMPLE_YAML_2);

    String sessionAffinity = info.getCluster("cluster1").getClusterSessionAffinity();
    assertThat(sessionAffinity, is("ClientIP"));
  }

  @Test
  void whenDomain2ReadFromYaml_sessionAffinityIsNotPresent()
      throws IOException {
    DomainPresenceInfo info = readDomainPresence(DOMAIN_V2_SAMPLE_YAML_4);

    String sessionAffinity = info.getCluster("cluster2").getClusterSessionAffinity();
    assertThat(sessionAffinity, nullValue());
  }

  @Test
  void whenDomain2ReadFromYaml_serviceAnnotationsFound() throws IOException {
    DomainPresenceInfo info = readDomainPresence(DOMAIN_V2_SAMPLE_YAML_2);
    EffectiveServerSpec effectiveServerSpec = info.getServer("server2", "cluster1");
    assertThat(effectiveServerSpec.getServiceAnnotations(), hasEntry("testKey3", "testValue3"));
  }

  @Test
  void whenDomain3ReadFromYaml_hasExportedNaps() throws IOException {
    List<KubernetesObject> resources = readFromYaml(DOMAIN_V2_SAMPLE_YAML_3);
    DomainResource domain = (DomainResource) resources.get(0);

    assertThat(domain.getAdminServerChannelNames(), containsInAnyOrder("channelA", "channelB"));
  }

  @Test
  void whenDomain3ReadFromYaml_adminServerHasNodeSelector() throws IOException {
    List<KubernetesObject> resources = readFromYaml(DOMAIN_V2_SAMPLE_YAML_3);
    DomainResource domain = (DomainResource) resources.get(0);
    assertThat(domain.getAdminServerSpec().getNodeSelectors(), hasEntry("os", "linux"));
  }

  @Test
  void whenDomain3ReadFromYaml_adminServerHasAnnotationsAndLabels() throws IOException {
    List<KubernetesObject> resources = readFromYaml(DOMAIN_V2_SAMPLE_YAML_3);
    DomainResource domain = (DomainResource) resources.get(0);
    assertThat(
        domain.getAdminServerSpec().getServiceAnnotations(), hasEntry("testKey3", "testValue3"));
    assertThat(domain.getAdminServerSpec().getServiceLabels(), hasEntry("testKey1", "testValue1"));
    assertThat(domain.getAdminServerSpec().getServiceLabels(), hasEntry("testKey2", "testValue2"));
  }

  @Test
  void whenDomain3ReadFromYaml_AdminServerRestartVersion() throws IOException {
    List<KubernetesObject> resources = readFromYaml(DOMAIN_V2_SAMPLE_YAML_3);
    DomainResource domain = (DomainResource) resources.get(0);
    assertThat(domain.getAdminServerSpec().getServerRestartVersion(), is("1"));
  }

  @Test
  void whenDomain3ReadFromYaml_NoRestartVersion() throws IOException {
    List<KubernetesObject> resources = readFromYaml(DOMAIN_V2_SAMPLE_YAML_3);
    DomainResource domain = (DomainResource) resources.get(0);
    final EffectiveServerSpec clusteredServer = info.getServer("anyServer", "anyCluster");
    final EffectiveServerSpec nonClusteredServer = info.getServer("anyServer", null);
    assertThat(clusteredServer.getDomainRestartVersion(), nullValue());
    assertThat(clusteredServer.getClusterRestartVersion(), nullValue());
    assertThat(clusteredServer.getServerRestartVersion(), nullValue());
    assertThat(nonClusteredServer.getDomainRestartVersion(), nullValue());
    assertThat(nonClusteredServer.getClusterRestartVersion(), nullValue());
    assertThat(nonClusteredServer.getServerRestartVersion(), nullValue());
  }

  @Test
  void whenDomainReadFromYaml_DomainRestartVersion() throws IOException {
    List<KubernetesObject> resources = readFromYaml(DOMAIN_V2_SAMPLE_YAML);
    DomainResource domain = (DomainResource) resources.get(0);
    assertThat(domain.getAdminServerSpec().getDomainRestartVersion(), is("1"));
    assertThat(domain.getAdminServerSpec().getClusterRestartVersion(), nullValue());
    assertThat(domain.getAdminServerSpec().getServerRestartVersion(), nullValue());
  }

  @Test
  void whenDomainReadFromYaml_ClusterRestartVersion() throws IOException {
    DomainPresenceInfo info = readDomainPresence(DOMAIN_V2_SAMPLE_YAML);
    EffectiveServerSpec effectiveServerSpec = info.getServer("server1", "cluster2");

    assertThat(effectiveServerSpec.getDomainRestartVersion(), is("1"));
    assertThat(effectiveServerSpec.getClusterRestartVersion(), is("2"));
    assertThat(effectiveServerSpec.getServerRestartVersion(), nullValue());
  }

  @Test
  void whenDomainReadFromYaml_ServerRestartVersion() throws IOException {
    DomainPresenceInfo info = readDomainPresence(DOMAIN_V2_SAMPLE_YAML);
    EffectiveServerSpec effectiveServerSpec = info.getServer("server2", null);

    assertThat(effectiveServerSpec.getDomainRestartVersion(), is("1"));
    assertThat(effectiveServerSpec.getClusterRestartVersion(), nullValue());
    assertThat(effectiveServerSpec.getServerRestartVersion(), is("3"));
  }

  @Test
  void whenDomainReadFromYaml_livenessCustomScriptMatches() throws IOException {
    List<KubernetesObject> resources = readFromYaml(DOMAIN_V2_SAMPLE_YAML);
    DomainResource domain = (DomainResource) resources.get(0);
    assertThat(domain.getLivenessProbeCustomScript(), is(LIVENESS_PROBE_CUSTOM_SCRIPT));
  }

  @Test
  void whenVolumesConfiguredOnMultipleLevels_useCombination() {
    configureDomain(domain)
        .withAdditionalVolume("name1", "/domain-tmp1")
        .withAdditionalVolume("name2", "/domain-tmp2");
    configureCluster("cluster1")
        .withAdditionalVolume("name3", "/cluster-tmp1")
        .withAdditionalVolume("name4", "/cluster-tmp2")
        .withAdditionalVolume("name5", "/cluster-tmp3");
    configureServer("server1").withAdditionalVolume("name6", "/server-tmp1");

    assertThat(
        info.getServer("server1", "cluster1").getAdditionalVolumes(),
        containsInAnyOrder(
            volume("name1", "/domain-tmp1"),
            volume("name2", "/domain-tmp2"),
            volume("name3", "/cluster-tmp1"),
            volume("name4", "/cluster-tmp2"),
            volume("name5", "/cluster-tmp3"),
            volume("name6", "/server-tmp1")));
  }

  @Test
  void whenVolumeMountsConfiguredOnMultipleLevels_useCombination() {
    configureDomain(domain)
        .withAdditionalVolumeMount("name1", "/domain-test1")
        .withAdditionalVolumeMount("name2", "/domain-test2");
    configureCluster("cluster1")
        .withAdditionalVolumeMount("name3", "/cluster-test1")
        .withAdditionalVolumeMount("name4", "/cluster-test2")
        .withAdditionalVolumeMount("name5", "/cluster-test3");
    configureServer("server1").withAdditionalVolumeMount("name6", "/server-test1");

    assertThat(
        info.getServer("server1", "cluster1").getAdditionalVolumeMounts(),
        containsInAnyOrder(
            volumeMount("name1", "/domain-test1"),
            volumeMount("name2", "/domain-test2"),
            volumeMount("name3", "/cluster-test1"),
            volumeMount("name4", "/cluster-test2"),
            volumeMount("name5", "/cluster-test3"),
            volumeMount("name6", "/server-test1")));
  }

  @Test
  void whenDuplicateVolumesConfiguredOnMultipleLevels_useCombination() {
    configureDomain(domain)
        .withAdditionalVolume("name1", "/domain-tmp1")
        .withAdditionalVolume("name2", "/domain-tmp2")
        .withAdditionalVolume("name3", "/domain-tmp3");
    configureCluster("cluster1")
        .withAdditionalVolume("name2", "/cluster-tmp1")
        .withAdditionalVolume("name3", "/cluster-tmp2");
    configureServer("server1").withAdditionalVolume("name3", "/server-tmp1");

    assertThat(
        info.getServer("server1", "cluster1").getAdditionalVolumes(),
        containsInAnyOrder(
            volume("name1", "/domain-tmp1"),
            volume("name2", "/cluster-tmp1"),
            volume("name3", "/server-tmp1")));
  }

  @Test
  void whenDuplicateVolumeMountsConfiguredOnMultipleLevels_useCombination() {
    configureDomain(domain)
        .withAdditionalVolumeMount("name1", "/domain-test1")
        .withAdditionalVolumeMount("name2", "/domain-test2")
        .withAdditionalVolumeMount("name3", "/domain-test3");
    configureCluster("cluster1")
        .withAdditionalVolumeMount("name2", "/cluster-test1")
        .withAdditionalVolumeMount("name3", "/cluster-test2");
    configureServer("server1").withAdditionalVolumeMount("name3", "/server-test1");

    assertThat(
        info.getServer("server1", "cluster1").getAdditionalVolumeMounts(),
        containsInAnyOrder(
            volumeMount("name1", "/domain-test1"),
            volumeMount("name2", "/cluster-test1"),
            volumeMount("name3", "/server-test1")));
  }

  @Test
  void whenLogHomeNotSet_useDefault() {
    configureDomain(domain);

    assertThat(domain.getLogHome(), equalTo("/shared/logs/uid1"));
  }

  @Test
  void whenLogHomeSet_useValue() {
    configureDomain(domain).withLogHome("/custom/logs/");

    assertThat(domain.getLogHome(), equalTo("/custom/logs/"));
  }

  @Test
  void whenLogHomeEnabledSet_useValue() {
    configureDomain(domain).withLogHomeEnabled(true);

    assertThat(domain.getSpec().isLogHomeEnabled(), is(true));
  }

  @Test
  void whenPortForwardingEnabledSet_useValue() {
    configureDomain(domain).withAdminChannelPortForwardingEnabled(false);

    assertThat(domain.getSpec().getAdminServer().isAdminChannelPortForwardingEnabled(), is(false));
  }

  @Test
  void whenLivenessProbeCustomScriptSet_useValue() {
    configureDomain(domain).withLivenessProbeCustomScript(LIVENESS_PROBE_CUSTOM_SCRIPT);

    assertThat(domain.getLivenessProbeCustomScript(), equalTo(LIVENESS_PROBE_CUSTOM_SCRIPT));
  }

  @Test
  void whenReplaceEnvironmentVariablesInJavaOptions_useValue() {
    configureDomain(domain).withReplaceEnvVariablesInJavaOptions(Boolean.TRUE);

    assertThat(domain.getSpec().getReplaceVariablesInJavaOptions(), equalTo(Boolean.TRUE));
  }

  @Test
  void domainHomeTest_standardHome1() {
    configureDomain(domain).withDomainHomeSourceType(FROM_MODEL);

    assertThat(domain.getDomainHome(), equalTo("/u01/domains/uid1"));
  }

  @Test
  void domainHomeTest_standardHome2() {
    configureDomain(domain).withDomainHomeSourceType(DomainSourceType.PERSISTENT_VOLUME);

    assertThat(domain.getDomainHome(), equalTo("/shared/domains/uid1"));
  }

  @Test
  void domainHomeTest_standardHome3() {
    configureDomain(domain).withDomainHomeSourceType(DomainSourceType.IMAGE);

    assertThat(domain.getDomainHome(), equalTo("/u01/oracle/user_projects/domains"));
  }

  @Test
  void domainHomeTest_customHome1() {
    configureDomain(domain).withDomainHome("/custom/domain/home");

    assertThat(domain.getDomainHome(), equalTo("/custom/domain/home"));
  }

  @Test
  void whenPodLabelsAppliedOnMultipleLevels_useCombination() {
    configureDomain(domain)
        .withPodLabel("label1", "domain-label-value1")
        .withPodLabel("label2", "domain-label-value2");
    configureCluster("cluster1")
        .withPodLabel("label3", "cluster-label-value1")
        .withPodLabel("label4", "cluster-label-value2");
    configureServer("server1").withPodLabel("label5", "server-label-value1");

    assertThat(
        info.getServer("server1", "cluster1").getPodLabels(),
        hasEntry("label1", "domain-label-value1"));
    assertThat(
        info.getServer("server1", "cluster1").getPodLabels(),
        hasEntry("label2", "domain-label-value2"));
    assertThat(
        info.getServer("server1", "cluster1").getPodLabels(),
        hasEntry("label3", "cluster-label-value1"));
    assertThat(
        info.getServer("server1", "cluster1").getPodLabels(),
        hasEntry("label4", "cluster-label-value2"));
    assertThat(
        info.getServer("server1", "cluster1").getPodLabels(),
        hasEntry("label5", "server-label-value1"));
  }

  @Test
  void whenPodAnnotationsAppliedOnMultipleLevels_useCombination() {
    configureDomain(domain)
        .withPodAnnotation("annotation1", "domain-annotation-value1")
        .withPodAnnotation("annotation2", "domain-annotation-value2");
    configureCluster("cluster1")
        .withPodAnnotation("annotation3", "cluster-annotation-value1")
        .withPodAnnotation("annotation4", "cluster-annotation-value2");
    configureServer("server1").withPodAnnotation("annotation5", "server-annotation-value1");

    assertThat(
        info.getServer("server1", "cluster1").getPodAnnotations(),
        hasEntry("annotation1", "domain-annotation-value1"));
    assertThat(
        info.getServer("server1", "cluster1").getPodAnnotations(),
        hasEntry("annotation2", "domain-annotation-value2"));
    assertThat(
        info.getServer("server1", "cluster1").getPodAnnotations(),
        hasEntry("annotation3", "cluster-annotation-value1"));
    assertThat(
        info.getServer("server1", "cluster1").getPodAnnotations(),
        hasEntry("annotation4", "cluster-annotation-value2"));
    assertThat(
        info.getServer("server1", "cluster1").getPodAnnotations(),
        hasEntry("annotation5", "server-annotation-value1"));
  }

  @Test
  void whenDuplicatePodLabelsConfiguredOnMultipleLevels_useCombination() {
    configureDomain(domain)
        .withPodLabel("label1", "domain-label-value1")
        .withPodLabel("label2", "domain-label-value2");
    configureCluster("cluster1")
        .withPodLabel("label2", "cluster-label-value1")
        .withPodLabel("label3", "cluster-label-value2");

    configureServer("server1").withPodLabel("label3", "server-label-value1");

    assertThat(
        info.getServer("server1", "cluster1").getPodLabels(),
        hasEntry("label1", "domain-label-value1"));
    assertThat(
        info.getServer("server1", "cluster1").getPodLabels(),
        hasEntry("label2", "cluster-label-value1"));
    assertThat(
        info.getServer("server1", "cluster1").getPodLabels(),
        hasEntry("label3", "server-label-value1"));
  }

  @Test
  void whenDuplicatePodAnnotationsConfiguredOnMultipleLevels_useCombination() {
    configureDomain(domain)
        .withPodAnnotation("annotation1", "domain-annotation-value1")
        .withPodAnnotation("annotation2", "domain-annotation-value2");
    configureCluster("cluster1")
        .withPodAnnotation("annotation2", "cluster-annotation-value1")
        .withPodAnnotation("annotation3", "cluster-annotation-value2");
    configureServer("server1").withPodAnnotation("annotation3", "server-annotation-value1");

    assertThat(
        info.getServer("server1", "cluster1").getPodAnnotations(),
        hasEntry("annotation1", "domain-annotation-value1"));
    assertThat(
        info.getServer("server1", "cluster1").getPodAnnotations(),
        hasEntry("annotation2", "cluster-annotation-value1"));
    assertThat(
        info.getServer("server1", "cluster1").getPodAnnotations(),
        hasEntry("annotation3", "server-annotation-value1"));
  }

  private V1Volume volume(String name, String path) {
    return new V1Volume().name(name).hostPath(new V1HostPathVolumeSource().path(path));
  }

  private V1VolumeMount volumeMount(String name, String path) {
    return new V1VolumeMount().name(name).mountPath(path);
  }

  @Test
  void whenNoDistributionStrategySpecified_defaultToDynamic() throws IOException {
    List<KubernetesObject> resources = readFromYaml(DOMAIN_V2_SAMPLE_YAML_2);
    DomainResource domain = (DomainResource) resources.get(0);

    assertThat(domain.getOverrideDistributionStrategy(), equalTo(OverrideDistributionStrategy.DYNAMIC));
  }

  @Test
  void whenDistributionStrategySpecified_readIt() throws IOException {
    List<KubernetesObject> resources = readFromYaml(DOMAIN_V2_SAMPLE_YAML_4);
    DomainResource domain = (DomainResource) resources.get(0);

    assertThat(domain.getOverrideDistributionStrategy(), equalTo(OverrideDistributionStrategy.ON_RESTART));
  }

  @Test
  void whenDistributionStrategyConfigured_returnIt() {
    configureDomain(domain).withConfigOverrideDistributionStrategy(OverrideDistributionStrategy.ON_RESTART);

    assertThat(domain.getOverrideDistributionStrategy(), equalTo(OverrideDistributionStrategy.ON_RESTART));
  }

  @Test
  void whenReadFromYaml_MonitoringExporterSpecificationSupportsBasicOperations() throws IOException {
    List<KubernetesObject> resources = readFromYaml(DOMAIN_V2_SAMPLE_YAML_3);
    DomainResource domain = (DomainResource) resources.get(0);

    final MonitoringExporterSpecification specification = domain.getSpec().getMonitoringExporterSpecification();
    assertThat(specification.toString(), containsString("monexp:latest"));
    assertThat(specification, not(equalTo(new MonitoringExporterSpecification())));
    assertThat(specification.hashCode(), not(equalTo(new MonitoringExporterSpecification().hashCode())));
  }

  @Test
  void whenNoMonitoringExporterConfigurationDefined_refuseAttemptToSetResourceRequirements() {
    final DomainConfigurator domainConfigurator = configureDomain(domain);
    final V1ResourceRequirements resourceRequirements = new V1ResourceRequirements();
    
    assertThrows(AssertionError.class,
        () -> domainConfigurator.withMonitoringExporterResources(resourceRequirements));
  }

  @Test
  void whenPersistentVolumeConfigured_useConfiguredValues() {
    configureDomain(domain).withInitializeDomainOnPv(new InitializeDomainOnPV().persistentVolume(createPv()));

    assertThat(getPersistentVolume(domain), equalTo(createPv()));
    assertThat(getPersistentVolume(domain).getSpec().getStorageClassName(), equalTo("oke-pv"));
    assertThat(getPersistentVolume(domain).getSpec().getCapacity(), notNullValue());
    assertThat(getPersistentVolume(domain).getSpec().getHostPath().getPath(), equalTo("/shared"));
  }

  private PersistentVolume createPv() {
    return new PersistentVolume().metadata(new V1ObjectMeta().name("test-pv"))
        .spec(new PersistentVolumeSpec().storageClassName("oke-pv")
            .capacity(Collections.singletonMap("storage", new Quantity("500Gi")))
            .hostPath(new V1HostPathVolumeSource().path("/shared")));
  }

  @Test
  void whenPersistentVolumeConfiguredWithNfs_useConfiguredValues() {
    configureDomain(domain).withInitializeDomainOnPv(new InitializeDomainOnPV().persistentVolume(createNfsPv()));

    assertThat(getPersistentVolume(domain), equalTo(createNfsPv()));
    assertThat(getPersistentVolume(domain).getSpec().getStorageClassName(), equalTo("oke-pv"));
    assertThat(getPersistentVolume(domain).getSpec().getCapacity(), notNullValue());
    assertThat(getPersistentVolume(domain).getSpec().getNfs().getPath(), equalTo("/shared"));
  }

  private PersistentVolume createNfsPv() {
    return new PersistentVolume().metadata(new V1ObjectMeta().name("test-pv"))
        .spec(new PersistentVolumeSpec().storageClassName("oke-pv")
            .capacity(Collections.singletonMap("storage", new Quantity("500Gi")))
            .nfs(new V1NFSVolumeSource().path("/shared")
            .server("10.0.3.9")));
  }

  @Test
  void whenPersistentVolumeClaimConfigured_useConfiguredValues() {
    configureDomain(domain).withInitializeDomainOnPv(new InitializeDomainOnPV().persistentVolumeClaim(createPvc()));

    assertThat(getPersistentVolumeClaim(domain), equalTo(createPvc()));
    assertThat(getPersistentVolumeClaim(domain).getSpec().getVolumeName(), equalTo("test-pv"));
    assertThat(getPersistentVolumeClaim(domain).getSpec().getStorageClassName(), equalTo("oke-pv"));
    assertThat(getPersistentVolumeClaim(domain).getSpec().getResources(), notNullValue());
  }

  private PersistentVolumeClaim createPvc() {
    return new PersistentVolumeClaim().metadata(new V1ObjectMeta().name("test-pvc"))
        .spec(new PersistentVolumeClaimSpec()
            .volumeName("test-pv")
            .storageClassName("oke-pv")
            .resources(new V1ResourceRequirements()
                .requests(Collections.singletonMap("storage", new Quantity("50Gi")))));
  }

  @Test
  void whenInitializeDomainOnPvWithDomainConfigured_useConfiguredValues() {
    InitializeDomainOnPV initializeDomainOnPv = new InitializeDomainOnPV();
    initializeDomainOnPv.domain(createDomainOnPV());
    configureDomain(domain).withInitializeDomainOnPv(initializeDomainOnPv);

    assertThat(getDomain(domain), equalTo(createDomainOnPV()));
    assertThat(getDomain(domain).getCreateIfNotExists(), equalTo(CreateIfNotExists.DOMAIN_AND_RCU));
    assertThat(getDomain(domain).getDomainType(), equalTo(WLS));
    assertThat(getDomain(domain).getDomainCreationConfigMap(), equalTo("wdf-config-map"));
    assertThat(getDomain(domain).getDomainCreationImages(), hasItems(new DomainCreationImage().image("image:v1")));
    assertThat(getDomain(domain).getOpss(),
        is(new Opss().withWalletFileSecret("wallet-file-secret").withWalletPasswordSecret("weblogic")));
  }

  private DomainOnPV createDomainOnPV() {
    return new DomainOnPV().domainType(WLS)
        .createMode(CreateIfNotExists.DOMAIN_AND_RCU).domainCreationConfigMap("wdf-config-map")
        .domainCreationImages(Collections.singletonList(new DomainCreationImage().image("image:v1")))
        .opss(new Opss().withWalletFileSecret("wallet-file-secret").withWalletPasswordSecret("weblogic"));
  }

  private PersistentVolume getPersistentVolume(DomainResource domain) {
    return domain.getSpec().getInitializeDomainOnPV().getPersistentVolume();
  }

  private PersistentVolumeClaim getPersistentVolumeClaim(DomainResource domain) {
    return domain.getSpec().getInitializeDomainOnPV().getPersistentVolumeClaim();
  }

  private DomainOnPV getDomain(DomainResource domain) {
    return domain.getSpec().getInitializeDomainOnPV().getDomain();
  }

  @Test
  void whenDomainWithInitializeDomainOnPvSpecReadFromYaml_pvNameandSpecsAreSet() throws IOException {
    List<KubernetesObject> resources = readFromYaml(DOMAIN_V2_SAMPLE_YAML_6);
    DomainResource domain = (DomainResource) resources.get(0);

    assertThat(getPvName(domain), not(nullValue()));
    assertThat(getStorageClass(domain), not(nullValue()));

  }

  private String getPvName(DomainResource domain) {
    return getPersistentVolume(domain).getMetadata().getName();
  }

  private String getStorageClass(DomainResource domain) {
    return getPersistentVolume(domain).getSpec().getStorageClassName();
  }

  @Test
  void whenInitializeDomainOnPvWithWaitForPvcToBindConfigured_useConfiguredValues() {
    InitializeDomainOnPV initializeDomainOnPv = new InitializeDomainOnPV();
    initializeDomainOnPv.waitForPvcToBind(false);
    configureDomain(domain).withInitializeDomainOnPv(initializeDomainOnPv);

    assertThat(domain.getSpec().getInitializeDomainOnPV().getWaitForPvcToBind(), is(false));
  }

  @Test
  void whenDomainWithInitializeDomainOnPvSpecReadFromYaml_pvcNameAndSpecsAreSet() throws IOException {
    List<KubernetesObject> resources = readFromYaml(DOMAIN_V2_SAMPLE_YAML_6);
    DomainResource domain = (DomainResource) resources.get(0);

    assertThat(getPvcName(domain), not(nullValue()));
    assertThat(getPvcVolumeName(domain), not(nullValue()));
  }

  private String getPvcName(DomainResource domain) {
    return getPersistentVolumeClaim(domain).getMetadata().getName();
  }

  private String getPvcVolumeName(DomainResource domain) {
    return getPersistentVolumeClaim(domain).getSpec().getVolumeName();
  }

  @Test
  void whenDomainWithInitializeDomainOnPvSpecReadFromYaml_domainSpecsHaveCorrectDefaultsAndValues()
      throws IOException {
    List<KubernetesObject> resources = readFromYaml(DOMAIN_V2_SAMPLE_YAML_6);
    DomainResource domain = (DomainResource) resources.get(0);

    assertThat(getDomain(domain).getCreateIfNotExists(), equalTo(DOMAIN));
    assertThat(getDomain(domain).getDomainType(), equalTo(JRF));
    assertThat(getDomain(domain).getDomainCreationConfigMap(), equalTo("domain-on-pv-cm"));
    assertThat(getDomain(domain).getDomainCreationImages(),
        hasItems(new DomainCreationImage().image("domain-on-pv-image:v1")));
    assertThat(getDomain(domain).getOpss(),
        is(new Opss().withWalletFileSecret("domain-opss-wallet").withWalletPasswordSecret("weblogic")));
    assertThat(getPersistentVolume(domain).getSpec().getStorageClassName(), equalTo("domain-on-pv-storage-class"));
    assertThat(getPersistentVolumeClaim(domain).getSpec().getVolumeName(), equalTo("pvDomainVolume"));
    assertThat(domain.getSpec().getInitializeDomainOnPV().getWaitForPvcToBind(), is(true));
  }

  @Test
  void whenDomainWithInitializeDomainOnPvReadFromYamlTwice_matchingIntrospectionVersionValuesLeaveDomainsEqual()
      throws IOException {
    List<KubernetesObject> resources = readFromYaml(DOMAIN_V2_SAMPLE_YAML_6);
    DomainResource domain1 = (DomainResource) resources.get(0);
    List<KubernetesObject> resources2 = readFromYaml(DOMAIN_V2_SAMPLE_YAML_6);
    DomainResource domain2 = (DomainResource) resources2.get(0);

    domain1.getSpec().setIntrospectVersion("123");
    domain2.getSpec().setIntrospectVersion("123");
    assertThat(domain1, equalTo(domain2));
  }
}

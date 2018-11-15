// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.weblogic.domain.v2;

import static com.jayway.jsonpath.matchers.JsonPathMatchers.hasJsonPath;
import static oracle.kubernetes.operator.KubernetesConstants.DEFAULT_IMAGE;
import static oracle.kubernetes.operator.KubernetesConstants.IFNOTPRESENT_IMAGEPULLPOLICY;
import static oracle.kubernetes.weblogic.domain.v2.ConfigurationConstants.START_ALWAYS;
import static oracle.kubernetes.weblogic.domain.v2.ConfigurationConstants.START_NEVER;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;

import com.google.gson.GsonBuilder;
import io.kubernetes.client.custom.Quantity;
import io.kubernetes.client.models.V1Capabilities;
import io.kubernetes.client.models.V1EnvVar;
<<<<<<< HEAD
import io.kubernetes.client.models.V1PodSecurityContext;
import io.kubernetes.client.models.V1SELinuxOptions;
import io.kubernetes.client.models.V1SecurityContext;
import io.kubernetes.client.models.V1Sysctl;
=======
import io.kubernetes.client.models.V1HostPathVolumeSource;
import io.kubernetes.client.models.V1Volume;
import io.kubernetes.client.models.V1VolumeMount;
>>>>>>> 2eee6231654d610cd04c6dec4de65c51d358d62a
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
  private static final String CREATED_BY_OPERATOR_LABEL_PATH =
      "$.metadata.labels.['weblogic.createdByOperator']";

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
  public void whenStorageNotConfigured_persistentVolumeClaimIsNull() {
    assertThat(domain.getPersistentVolumeClaimName(), nullValue());
  }

  @Test
  public void whenPredefinedStorageConfigured_storageElementSpecifiedClaimName() {
    configureDomain(domain).withPredefinedClaim("test-pvc");

    assertThat(toJson(domain), hasJsonPath("$.spec.storage.predefined.claim", equalTo("test-pvc")));
  }

  private String toJson(Object object) {
    return new GsonBuilder().create().toJson(object);
  }

  @Test
  public void whenPredefinedStorageConfigured_returnSpecifiedPersistentVolumeClaim() {
    configureDomain(domain).withPredefinedClaim("test-pvc");

    assertThat(domain.getPersistentVolumeClaimName(), equalTo("test-pvc"));
  }

  @Test
  public void whenPredefinedStorageConfigured_requiredPersistentVolumeAndClaimAreNull() {
    configureDomain(domain).withPredefinedClaim("test-pvc");

    assertThat(domain.getRequiredPersistentVolume(), nullValue());
    assertThat(domain.getRequiredPersistentVolumeClaim(), nullValue());
  }

  private String getDefaultPVCName() {
    return getDomainUid() + "-weblogic-domain-pvc";
  }

  @Test
  public void whenHostPathDefinedStorageConfigured_storageElementIncludesSize() {
    configureDomain(domain).withHostPathStorage("/tmp").withStorageSize("10Gi");

    String domainJson = toJson(domain);
    assertThat(domainJson, hasJsonPath("$.spec.storage.generated.storageSize", equalTo("10Gi")));
    assertThat(domainJson, hasJsonPath("$.spec.storage.generated.hostPath.path", equalTo("/tmp")));
  }

  @Test
  public void whenHostPathDefinedStorageConfigured_useDefaultPersistentClaim() {
    configureDomain(domain).withHostPathStorage("/tmp").withStorageSize("10Gi");

    assertThat(domain.getPersistentVolumeClaimName(), equalTo(getDefaultPVCName()));
  }

  @Test
  public void whenHostPathDefinedStorageConfigured_returnRequiredPersistentVolume() {
    configureDomain(domain).withHostPathStorage("/tmp").withStorageReclaimPolicy("Delete");

    String pv = toJson(domain.getRequiredPersistentVolume());
    assertThat(pv, hasJsonPath("$.metadata.name", equalTo(getPersistentVolumeName())));
    assertThat(pv, hasDomainUidLabel(getDomainUid()));
    assertThat(pv, hasCreatedByOperatorLabel());
    assertThat(pv, hasJsonPath("$.spec.storageClassName", equalTo(getStorageClass())));
    assertThat(pv, hasJsonPath("$.spec.capacity.storage", equalTo("10Gi")));
    assertThat(pv, hasJsonPath("$.spec.accessModes", hasItem("ReadWriteMany")));
    assertThat(pv, hasJsonPath("$.spec.persistentVolumeReclaimPolicy", equalTo("Delete")));
    assertThat(pv, hasJsonPath("$.spec.hostPath.path", equalTo("/tmp")));
  }

  private static Matcher<? super Object> hasDomainUidLabel(String domainUid) {
    return hasJsonPath("$.metadata.labels.['weblogic.domainUID']", equalTo(domainUid));
  }

  private Matcher<? super Object> hasCreatedByOperatorLabel() {
    return hasJsonPath(CREATED_BY_OPERATOR_LABEL_PATH, equalTo("true"));
  }

  private String getPersistentVolumeName() {
    return getPersistentVolumeName(getDomainUid());
  }

  private String getPersistentVolumeName(String domainUid) {
    return domainUid + "-weblogic-domain-pv";
  }

  private String getStorageClass() {
    return getStorageClass(getDomainUid());
  }

  private String getStorageClass(String domainUid) {
    return domainUid + "-weblogic-domain-storage-class";
  }

  @Test
  public void whenHostPathDefinedStorageConfigured_returnRequiredPersistentVolumeClaim() {
    configureDomain(domain).withHostPathStorage("/tmp").withStorageReclaimPolicy("Delete");

    assertPersistentVolumeClaim();
  }

  private void assertPersistentVolumeClaim() {
    String pv = toJson(domain.getRequiredPersistentVolumeClaim());
    assertThat(pv, hasJsonPath("$.metadata.name", equalTo(getDefaultPVCName())));
    assertThat(pv, hasJsonPath("$.metadata.namespace", equalTo(getNamespace())));
    assertThat(pv, hasDomainUidLabel(getDomainUid()));
    assertThat(pv, hasCreatedByOperatorLabel());
    assertThat(pv, hasJsonPath("$.spec.storageClassName", equalTo(getStorageClass())));
    assertThat(pv, hasJsonPath("$.spec.accessModes", hasItem("ReadWriteMany")));
    assertThat(pv, hasJsonPath("$.spec.resources.requests.storage", equalTo("10Gi")));
  }

  @Test
  public void whenNfsDefinedStorageConfigured_useDefaultPersistentClaim() {
    configureDomain(domain).withNfsStorage("myserver", "/tmp").withStorageReclaimPolicy("Retain");

    assertThat(domain.getPersistentVolumeClaimName(), equalTo(getDefaultPVCName()));
  }

  @Test
  public void whenNfsDefinedStorageConfigured_returnRequiredPersistentVolume() {
    configureDomain(domain).withNfsStorage("myserver", "/tmp").withStorageReclaimPolicy("Retain");

    String pv = toJson(domain.getRequiredPersistentVolume());
    assertThat(pv, hasJsonPath("$.metadata.name", equalTo(getPersistentVolumeName())));
    assertThat(pv, hasDomainUidLabel(getDomainUid()));
    assertThat(pv, hasCreatedByOperatorLabel());
    assertThat(pv, hasJsonPath("$.spec.storageClassName", equalTo(getStorageClass())));
    assertThat(pv, hasJsonPath("$.spec.capacity.storage", equalTo("10Gi")));
    assertThat(pv, hasJsonPath("$.spec.accessModes", hasItem("ReadWriteMany")));
    assertThat(pv, hasJsonPath("$.spec.persistentVolumeReclaimPolicy", equalTo("Retain")));
    assertThat(pv, hasJsonPath("$.spec.nfs.server", equalTo("myserver")));
    assertThat(pv, hasJsonPath("$.spec.nfs.path", equalTo("/tmp")));
  }

  @Test
  public void whenNfsDefinedStorageConfigured_returnRequiredPersistentVolumeClaim() {
    configureDomain(domain).withNfsStorage("myserver", "/tmp").withStorageReclaimPolicy("Retain");

    assertPersistentVolumeClaim();
  }

  @Test
  public void whenExportT3ChannelsNotDefined_exportedNamesIsEmpty() {
    assertThat(domain.getExportedNetworkAccessPointNames(), empty());
  }

  @Test
  public void whenExportT3ChannelsDefined_returnChannelNames() {
    AdminServerConfigurator configurator = configureDomain(domain).configureAdminServer("");
    configurator.withExportedNetworkAccessPoints("channel1", "channel2");

    assertThat(
        domain.getExportedNetworkAccessPointNames(), containsInAnyOrder("channel1", "channel2"));
  }

  @Test
  public void whenAdminServerConfiguredWithNodePort_returnNodePort() {
    configureAdminServer().withNodePort(31);

    assertThat(domain.getAdminServerSpec().getNodePort(), equalTo(31));
  }

  @Test
  public void whenExportT3ChannelsDefinedWithLabels_returnChannelNames() {
    AdminServerConfigurator configurator = configureDomain(domain).configureAdminServer("");
    configurator
        .configureExportedNetworkAccessPoint("channel1")
        .addLabel("label1", "value1")
        .addLabel("label2", "value2");
    configurator
        .configureExportedNetworkAccessPoint("channel2")
        .addLabel("label3", "value3")
        .addLabel("label4", "value4");

    assertThat(
        domain.getExportedNetworkAccessPointNames(), containsInAnyOrder("channel1", "channel2"));
  }

  @Test
  public void whenExportT3ChannelsDefinedWithLabels_returnLabels() {
    AdminServerConfigurator configurator = configureDomain(domain).configureAdminServer("");
    configurator
        .configureExportedNetworkAccessPoint("channel1")
        .addLabel("label1", "value1")
        .addLabel("label2", "value2");

    assertThat(domain.getChannelServiceLabels("channel1"), hasEntry("label1", "value1"));
  }

  @Test
  public void whenExportT3ChannelsDefinedWithAnnotations_returnAnnotations() {
    AdminServerConfigurator configurator = configureDomain(domain).configureAdminServer("");
    configurator
        .configureExportedNetworkAccessPoint("channel1")
        .addAnnotation("annotation1", "value1")
        .addAnnotation("annotation2", "value2");

    assertThat(domain.getChannelServiceAnnotations("channel1"), hasEntry("annotation1", "value1"));
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
    configureServer(SERVER1).withEnvironmentVariable("name4", "server");

    assertThat(
        domain.getServer(SERVER1, "cluster1").getEnvironmentVariables(),
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
  public void whenResourceRequirementsConfiguredOnMultipleLevels_useCombination() {
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

    ServerSpec serverSpec = domain.getServer(SERVER1, CLUSTER_NAME);

    assertThat(serverSpec.getResources().getRequests(), hasResourceQuantity("memory", "128Mi"));
    assertThat(serverSpec.getResources().getRequests(), hasResourceQuantity("cpu", "250m"));
    assertThat(serverSpec.getResources().getLimits(), hasResourceQuantity("memory", "512Mi"));
    assertThat(serverSpec.getResources().getLimits(), hasResourceQuantity("cpu", "500m"));

    assertThat(
        domain.getAdminServerSpec().getResources().getRequests(),
        hasResourceQuantity("memory", "64Mi"));
    assertThat(
        domain.getAdminServerSpec().getResources().getRequests(),
        hasResourceQuantity("cpu", "500m"));
    assertThat(
        domain.getAdminServerSpec().getResources().getLimits(),
        hasResourceQuantity("memory", "128Mi"));
    assertThat(
        domain.getAdminServerSpec().getResources().getLimits(), hasResourceQuantity("cpu", "0.8"));
  }

  @Test
  public void whenPodSecurityContextConfiguredOnMultipleLevels_useCombination() {
    V1Sysctl domainSysctl = new V1Sysctl().name("net.ipv4.route.min_pmtu").value("552");
    V1Sysctl clusterSysctl = new V1Sysctl().name("kernel.shm_rmid_forced").value("0");
    configureDomain(domain)
        .withPodSecurityContext(
            new V1PodSecurityContext()
                .runAsGroup(420L)
                .addSysctlsItem(domainSysctl)
                .seLinuxOptions(
                    new V1SELinuxOptions().level("domain").role("admin").user("weblogic"))
                .runAsNonRoot(true));
    configureCluster(CLUSTER_NAME)
        .withPodSecurityContext(
            new V1PodSecurityContext()
                .runAsGroup(421L)
                .addSysctlsItem(clusterSysctl)
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
    configureServer(SERVER2);
    configureAdminServer().withPodSecurityContext(new V1PodSecurityContext().runAsNonRoot(false));

    ServerSpec server1Spec = domain.getServer(SERVER1, CLUSTER_NAME);
    assertThat(server1Spec.getPodSecurityContext().getRunAsGroup(), is(422L));
    assertThat(server1Spec.getPodSecurityContext().getSysctls(), contains(clusterSysctl));
    assertThat(server1Spec.getPodSecurityContext().getSeLinuxOptions().getLevel(), is("server"));
    assertThat(server1Spec.getPodSecurityContext().getSeLinuxOptions().getRole(), is("slave"));
    assertThat(server1Spec.getPodSecurityContext().getSeLinuxOptions().getType(), nullValue());
    assertThat(server1Spec.getPodSecurityContext().getRunAsUser(), nullValue());

    ServerSpec server2Spec = domain.getServer(SERVER2, CLUSTER_NAME);
    assertThat(server2Spec.getPodSecurityContext().getRunAsGroup(), is(421L));
    assertThat(server1Spec.getPodSecurityContext().getSysctls(), contains(clusterSysctl));
    assertThat(server2Spec.getPodSecurityContext().getSeLinuxOptions().getLevel(), is("cluster"));
    assertThat(server2Spec.getPodSecurityContext().getSeLinuxOptions().getRole(), is("admin"));
    assertThat(server2Spec.getPodSecurityContext().getSeLinuxOptions().getType(), is("admin"));
    assertThat(server2Spec.getPodSecurityContext().getRunAsUser(), nullValue());

    ServerSpec adminServerSpec = domain.getAdminServerSpec();
    assertThat(adminServerSpec.getPodSecurityContext().getRunAsGroup(), is(420L));
    assertThat(adminServerSpec.getPodSecurityContext().getSysctls(), contains(domainSysctl));
    assertThat(
        adminServerSpec.getPodSecurityContext().getSeLinuxOptions().getLevel(), is("domain"));
    assertThat(adminServerSpec.getPodSecurityContext().getSeLinuxOptions().getRole(), is("admin"));
    assertThat(adminServerSpec.getPodSecurityContext().getSeLinuxOptions().getType(), nullValue());
    assertThat(adminServerSpec.getPodSecurityContext().getRunAsUser(), nullValue());
  }

  @Test
  public void whenContainerSecurityContextConfiguredOnMultipleLevels_useCombination() {
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
    configureServer(SERVER2);
    configureAdminServer()
        .withContainerSecurityContext(new V1SecurityContext().runAsNonRoot(false));

    ServerSpec server1Spec = domain.getServer(SERVER1, CLUSTER_NAME);
    assertThat(server1Spec.getContainerSecurityContext().getRunAsGroup(), is(422L));
    assertThat(server1Spec.getContainerSecurityContext().isAllowPrivilegeEscalation(), is(false));
    assertThat(
        server1Spec.getContainerSecurityContext().getCapabilities().getAdd(), contains("SYS_TIME"));
    assertThat(
        server1Spec.getContainerSecurityContext().getSeLinuxOptions().getLevel(), is("server"));
    assertThat(
        server1Spec.getContainerSecurityContext().getSeLinuxOptions().getRole(), is("slave"));
    assertThat(
        server1Spec.getContainerSecurityContext().getSeLinuxOptions().getType(), nullValue());
    assertThat(server1Spec.getContainerSecurityContext().getRunAsUser(), nullValue());

    ServerSpec server2Spec = domain.getServer(SERVER2, CLUSTER_NAME);
    assertThat(server2Spec.getContainerSecurityContext().getRunAsGroup(), is(421L));
    assertThat(server2Spec.getContainerSecurityContext().isAllowPrivilegeEscalation(), is(false));
    assertThat(
        server2Spec.getContainerSecurityContext().getCapabilities().getAdd(), contains("SYS_TIME"));
    assertThat(
        server2Spec.getContainerSecurityContext().getSeLinuxOptions().getLevel(), is("cluster"));
    assertThat(
        server2Spec.getContainerSecurityContext().getSeLinuxOptions().getRole(), is("admin"));
    assertThat(
        server2Spec.getContainerSecurityContext().getSeLinuxOptions().getType(), is("admin"));
    assertThat(server2Spec.getContainerSecurityContext().getRunAsUser(), nullValue());

    ServerSpec adminServerSpec = domain.getAdminServerSpec();
    assertThat(adminServerSpec.getContainerSecurityContext().getRunAsGroup(), is(420L));
    assertThat(
        adminServerSpec.getContainerSecurityContext().isAllowPrivilegeEscalation(), is(false));
    assertThat(
        adminServerSpec.getContainerSecurityContext().getCapabilities().getAdd(),
        contains("CHOWN", "SYS_BOOT"));
    assertThat(
        adminServerSpec.getContainerSecurityContext().getSeLinuxOptions().getLevel(), is("domain"));
    assertThat(
        adminServerSpec.getContainerSecurityContext().getSeLinuxOptions().getRole(), is("admin"));
    assertThat(
        adminServerSpec.getContainerSecurityContext().getSeLinuxOptions().getType(), nullValue());
    assertThat(adminServerSpec.getContainerSecurityContext().getRunAsUser(), nullValue());
    assertThat(adminServerSpec.getContainerSecurityContext().isPrivileged(), nullValue());
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
    assertThat(
        serverSpec.getConfigOverrideSecrets(),
        containsInAnyOrder("overrides-secret-1", "overrides-secret-2"));
    assertThat(serverSpec.getDesiredState(), equalTo("RUNNING"));
    assertThat(serverSpec.shouldStart(1), is(true));
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

    assertThat(serverSpec.getNodePort(), equalTo(7001));
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
            envVar("USER_MEM_ARGS", "-Xms64m -Xmx256m "),
            envVar("var1", "value0")));
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
    assertThat(
        serverSpec.getConfigOverrideSecrets(),
        containsInAnyOrder("overrides-secret-1", "overrides-secret-2"));
  }

  @Test
  public void whenDomainReadFromYaml_nfsStorageDefinesRequiredPV() throws IOException {
    Domain domain = readDomain(DOMAIN_V2_SAMPLE_YAML);

    String pv = toJson(domain.getRequiredPersistentVolume());
    assertThat(pv, hasJsonPath("$.metadata.name", equalTo(getPersistentVolumeName("test-domain"))));
    assertThat(pv, hasDomainUidLabel("test-domain"));
    assertThat(pv, hasCreatedByOperatorLabel());
    assertThat(pv, hasJsonPath("$.spec.storageClassName", equalTo(getStorageClass("test-domain"))));
    assertThat(pv, hasJsonPath("$.spec.capacity.storage", equalTo("8Gi")));
    assertThat(pv, hasJsonPath("$.spec.accessModes", hasItem("ReadWriteMany")));
    assertThat(pv, hasJsonPath("$.spec.persistentVolumeReclaimPolicy", equalTo("Retain")));
    assertThat(pv, hasJsonPath("$.spec.nfs.server", equalTo("thatServer")));
    assertThat(pv, hasJsonPath("$.spec.nfs.path", equalTo("/local/path")));
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
  public void whenDomainReadFromYaml_AdminAndManagedOverrideResourceRequirements()
      throws IOException {
    Domain domain = readDomain(DOMAIN_V2_SAMPLE_YAML);
    ServerSpec server1Spec = domain.getServer("server1", null);
    ServerSpec server2Spec = domain.getServer("server2", null);

    assertThat(
        domain.getAdminServerSpec().getResources().getRequests(),
        hasResourceQuantity("memory", "64Mi"));
    assertThat(
        domain.getAdminServerSpec().getResources().getRequests(),
        hasResourceQuantity("cpu", "150m"));
    assertThat(
        domain.getAdminServerSpec().getResources().getLimits(),
        hasResourceQuantity("memory", "128Mi"));
    assertThat(
        domain.getAdminServerSpec().getResources().getLimits(), hasResourceQuantity("cpu", "200m"));

    assertThat(server1Spec.getResources().getRequests(), hasResourceQuantity("memory", "32Mi"));
    assertThat(server1Spec.getResources().getRequests(), hasResourceQuantity("cpu", "250m"));
    assertThat(server1Spec.getResources().getLimits(), hasResourceQuantity("memory", "256Mi"));
    assertThat(server1Spec.getResources().getLimits(), hasResourceQuantity("cpu", "500m"));

    assertThat(server2Spec.getResources().getRequests(), hasResourceQuantity("memory", "64Mi"));
    assertThat(server2Spec.getResources().getRequests(), hasResourceQuantity("cpu", "250m"));
    assertThat(server2Spec.getResources().getLimits(), hasResourceQuantity("memory", "128Mi"));
    assertThat(server2Spec.getResources().getLimits(), hasResourceQuantity("cpu", "500m"));
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
  public void whenDomain2ReadFromYaml_hostPathStorageDefinesRequiredPV() throws IOException {
    Domain domain = readDomain(DOMAIN_V2_SAMPLE_YAML_2);

    String pv = toJson(domain.getRequiredPersistentVolume());
    assertThat(
        pv, hasJsonPath("$.spec.storageClassName", equalTo(getStorageClass("test-domain-2"))));
    assertThat(pv, hasJsonPath("$.spec.capacity.storage", equalTo("10Gi")));
    assertThat(pv, hasJsonPath("$.spec.accessModes", hasItem("ReadWriteMany")));
    assertThat(pv, hasJsonPath("$.spec.persistentVolumeReclaimPolicy", equalTo("Delete")));
    assertThat(pv, hasJsonPath("$.spec.hostPath.path", equalTo("/other/path")));
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
  public void whenDomain3ReadFromYaml_PredefinedStorageDefinesClaimName() throws IOException {
    Domain domain = readDomain(DOMAIN_V2_SAMPLE_YAML_3);

    assertThat(domain.getPersistentVolumeClaimName(), equalTo("magic-drive"));
  }

  @Test
  public void whenDomain3ReadFromYaml_hasExportedNaps() throws IOException {
    Domain domain = readDomain(DOMAIN_V2_SAMPLE_YAML_3);

    assertThat(
        domain.getExportedNetworkAccessPointNames(), containsInAnyOrder("channelA", "channelB"));
  }

  @Test
  public void whenDomain3ReadFromYaml_channelHasLabels() throws IOException {
    Domain domain = readDomain(DOMAIN_V2_SAMPLE_YAML_3);

    assertThat(domain.getChannelServiceLabels("channelB"), hasEntry("color", "red"));
  }

  @Test
  public void whenDomain3ReadFromYaml_channelHasAnnotations() throws IOException {
    Domain domain = readDomain(DOMAIN_V2_SAMPLE_YAML_3);

    assertThat(domain.getChannelServiceAnnotations("channelB"), hasEntry("time", "midnight"));
  }

  @Test
<<<<<<< HEAD
  public void whenDomain3ReadFromYaml_adminServerHasNodeSelector() throws IOException {
    Domain domain = readDomain(DOMAIN_V2_SAMPLE_YAML_3);
    assertThat(domain.getAdminServerSpec().getNodeSelectors(), hasEntry("os", "linux"));
=======
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
  public void domainHomeTest_standardHome2() {
    configureDomain(domain).withDomainHomeInImage(false);

    assertThat(domain.getDomainHome(), equalTo("/shared/domains/uid1"));
  }

  @Test
  public void domainHomeTest_standardHome3() {
    configureDomain(domain).withDomainHomeInImage(true);

    assertThat(domain.getDomainHome(), equalTo("/shared/domain"));
  }

  @Test
  public void domainHomeTest_customHome1() {
    configureDomain(domain).withDomainHome("/custom/domain/home");

    assertThat(domain.getDomainHome(), equalTo("/custom/domain/home"));
  }

  private V1Volume volume(String name, String path) {
    return new V1Volume().name(name).hostPath(new V1HostPathVolumeSource().path(path));
  }

  private V1VolumeMount volumeMount(String name, String path) {
    return new V1VolumeMount().name(name).mountPath(path);
>>>>>>> 2eee6231654d610cd04c6dec4de65c51d358d62a
  }
}

// Copyright (c) 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.weblogic.domain.model;

import java.io.IOException;

import io.kubernetes.client.openapi.models.V1Container;
import io.kubernetes.client.openapi.models.V1EnvVar;
import io.kubernetes.client.openapi.models.V1LocalObjectReference;
import oracle.kubernetes.operator.ServerStartState;
import oracle.kubernetes.operator.helpers.DomainPresenceInfo;
import oracle.kubernetes.operator.processing.EffectiveServerSpec;
import org.hamcrest.MatcherAssert;
import org.junit.jupiter.api.Test;

import static oracle.kubernetes.operator.KubernetesConstants.DEFAULT_ALLOW_REPLICAS_BELOW_MIN_DYN_CLUSTER_SIZE;
import static oracle.kubernetes.operator.KubernetesConstants.DEFAULT_IMAGE;
import static oracle.kubernetes.operator.KubernetesConstants.DEFAULT_MAX_CLUSTER_CONCURRENT_SHUTDOWN;
import static oracle.kubernetes.operator.KubernetesConstants.DEFAULT_MAX_CLUSTER_CONCURRENT_START_UP;
import static oracle.kubernetes.operator.KubernetesConstants.LATEST_IMAGE_SUFFIX;
import static org.hamcrest.Matchers.both;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.junit.MatcherAssert.assertThat;

class DomainResourceBasicTest extends DomainTestBase {

  private static final String NAME1 = "name1";
  private static final String NAME2 = "name2";
  private static final String VALUE1 = "value1";
  private static final String VALUE2 = "value2";
  private static final String IMAGE = "myimage";
  private static final String PULL_SECRET_NAME = "pull-secret";

  @Test
  void canGetAdminServerInfoFromDomain() {
    assertThat(domain.getWebLogicCredentialsSecretName(), equalTo(SECRET_NAME));
  }

  @Test
  void canGetDomainInfoFromDomain() {
    assertThat(domain.getDomainUid(), equalTo(DOMAIN_UID));
  }

  @Test
  void adminServerSpecHasStandardValues() {
    EffectiveServerSpec spec = domain.getAdminServerSpec();

    verifyStandardFields(spec);
  }

  // Confirms the value of fields that are constant across the domain
  private void verifyStandardFields(EffectiveServerSpec spec) {
    assertThat(spec.getImage(), equalTo(DEFAULT_IMAGE));
    assertThat(spec.getImagePullPolicy(), equalTo(V1Container.ImagePullPolicyEnum.IFNOTPRESENT));
    assertThat(spec.getImagePullSecrets(), empty());
  }

  @Test
  void unconfiguredManagedServerSpecHasStandardValues() {
    EffectiveServerSpec spec = info.getServer("aServer", CLUSTER_NAME);

    verifyStandardFields(spec);
  }

  @SuppressWarnings("SameParameterValue")
  private V1EnvVar envVar(String name, String value) {
    return new V1EnvVar().name(name).value(value);
  }

  @Test
  void unconfiguredAdminServer_hasNoEnvironmentVariables() {
    EffectiveServerSpec spec = domain.getAdminServerSpec();

    assertThat(spec.getEnvironmentVariables(), empty());
  }

  @Test
  void whenDefaultImageSpecified_serversHaveSpecifiedImage() {
    configureDomain(domain).withDefaultImage(IMAGE);

    assertThat(domain.getAdminServerSpec().getImage(), equalTo(IMAGE));
    assertThat(info.getServer("aServer", "aCluster").getImage(), equalTo(IMAGE));
  }

  @Test
  void whenLatestImageSpecifiedAsDefault_serversHaveAlwaysPullPolicy() {
    configureDomain(domain).withDefaultImage(IMAGE + LATEST_IMAGE_SUFFIX);

    assertThat(domain.getAdminServerSpec().getImagePullPolicy(), equalTo(V1Container.ImagePullPolicyEnum.ALWAYS));
    assertThat(
        info.getServer("aServer", "aCluster").getImagePullPolicy(),
        equalTo(V1Container.ImagePullPolicyEnum.ALWAYS));
  }

  @Test
  void whenNotSpecified_imageHasDefault() {
    domain.getSpec().setImage(null);

    EffectiveServerSpec spec = domain.getAdminServerSpec();

    assertThat(spec.getImage(), equalTo(DEFAULT_IMAGE));
  }

  @Test
  void whenImageTagIsLatestAndPullPolicyNotSpecified_pullPolicyIsAlways() {
    domain.getSpec().setImage("test:latest");
    domain.getSpec().setImagePullPolicy(null);

    EffectiveServerSpec spec = domain.getAdminServerSpec();

    assertThat(spec.getImagePullPolicy(), equalTo(V1Container.ImagePullPolicyEnum.ALWAYS));
  }

  @Test
  void whenImageTagIsNotLatestAndPullPolicyNotSpecified_pullPolicyIsIfAbsent() {
    domain.getSpec().setImage("test:1.0");
    domain.getSpec().setImagePullPolicy(null);

    EffectiveServerSpec spec = domain.getAdminServerSpec();

    assertThat(spec.getImagePullPolicy(), equalTo(V1Container.ImagePullPolicyEnum.IFNOTPRESENT));
  }

  @Test
  void whenImagePullPolicySpecifiedAsDefault_allServersHaveIt() {
    configureDomain(domain).withDefaultImagePullPolicy(V1Container.ImagePullPolicyEnum.ALWAYS);

    assertThat(domain.getAdminServerSpec().getImagePullPolicy(), equalTo(V1Container.ImagePullPolicyEnum.ALWAYS));
    assertThat(
        info.getServer("aServer", "aCluster").getImagePullPolicy(),
        equalTo(V1Container.ImagePullPolicyEnum.ALWAYS));
  }

  @Test
  void whenDefaultImagePullSecretSpecified_allServersHaveIt() {
    V1LocalObjectReference secretReference = createSecretReference(PULL_SECRET_NAME);
    configureDomain(domain).withDefaultImagePullSecrets(secretReference);

    assertThat(domain.getAdminServerSpec().getImagePullSecrets(), hasItem(secretReference));
    assertThat(
        info.getServer("aServer", "aCluster").getImagePullSecrets(), hasItem(secretReference));
  }

  @SuppressWarnings("SameParameterValue")
  private V1LocalObjectReference createSecretReference(String pullSecretName) {
    return new V1LocalObjectReference().name(pullSecretName);
  }

  @Test
  void whenSpecified_adminServerHasEnvironmentVariables() {
    configureAdminServer()
        .withEnvironmentVariable(NAME1, VALUE1)
        .withEnvironmentVariable(NAME2, VALUE2);

    EffectiveServerSpec spec = domain.getAdminServerSpec();

    assertThat(spec.getEnvironmentVariables(), containsInAnyOrder(createEnvironment()));
  }

  private V1EnvVar[] createEnvironment() {
    return new V1EnvVar[] {envVar(NAME1, VALUE1), envVar(NAME2, VALUE2)};
  }

  @Test
  void whenOtherServersDefined_adminServerHasNoEnvironmentVariables() {
    configureServer(SERVER1)
        .withEnvironmentVariable(NAME1, VALUE1)
        .withEnvironmentVariable(NAME2, VALUE2);

    EffectiveServerSpec spec = domain.getAdminServerSpec();

    assertThat(spec.getEnvironmentVariables(), empty());
  }

  @Test
  void whenSpecified_adminServerDesiredStateIsAsSpecified() {
    configureAdminServer().withDesiredState(ServerStartState.ADMIN);

    EffectiveServerSpec spec = domain.getAdminServerSpec();

    assertThat(spec.getDesiredState(), equalTo("ADMIN"));
  }

  @Test
  void whenNotSpecified_adminServerDesiredStateIsRunning() {
    EffectiveServerSpec spec = domain.getAdminServerSpec();

    assertThat(spec.getDesiredState(), equalTo("RUNNING"));
  }

  @Test
  void whenNotSpecified_managedServerDesiredStateIsRunning() {
    EffectiveServerSpec spec = info.getServer(SERVER1, CLUSTER_NAME);

    assertThat(spec.getDesiredState(), equalTo("RUNNING"));
  }

  @Test
  void whenSpecified_managedServerDesiredStateIsAsSpecified() {
    configureServer(SERVER1).withDesiredState(ServerStartState.ADMIN);

    EffectiveServerSpec spec = info.getServer(SERVER1, CLUSTER_NAME);

    assertThat(spec.getDesiredState(), equalTo("ADMIN"));
  }

  @Test
  void whenOnlyAsStateSpecified_managedServerDesiredStateIsRunning() {
    configureAdminServer().withDesiredState(ServerStartState.ADMIN);

    EffectiveServerSpec spec = info.getServer(SERVER1, CLUSTER_NAME);

    assertThat(spec.getDesiredState(), equalTo("RUNNING"));
  }

  @Test
  void whenClusterStateSpecified_managedServerDesiredStateIsAsSpecified() {
    configureCluster(CLUSTER_NAME).withDesiredState(ServerStartState.ADMIN);

    EffectiveServerSpec spec = info.getServer(SERVER1, CLUSTER_NAME);

    assertThat(spec.getDesiredState(), equalTo("ADMIN"));
  }

  @Test
  void whenNoReplicaCountSpecified_canChangeIt() {
    info.setReplicaCount("cluster1", 7);

    assertThat(info.getReplicaCount("cluster1"), equalTo(7));
  }

  @Test
  void afterReplicaCountSetForCluster_canReadIt() {
    configureCluster("cluster1").withReplicas(5);

    assertThat(info.getReplicaCount("cluster1"), equalTo(5));
  }

  @Test
  void afterReplicaCountSetForCluster_canChangeIt() {
    configureCluster("cluster1").withReplicas(5);

    info.setReplicaCount("cluster1", 4);
    assertThat(info.getReplicaCount("cluster1"), equalTo(4));
  }

  @Test
  void afterReplicaCountMaxUnavailableSetForCluster_canReadMinAvailable() {
    configureCluster("cluster1").withReplicas(5).withMaxUnavailable(2);

    assertThat(info.getMinAvailable("cluster1"), equalTo(3));
  }

  @Test
  void afterReplicaCountSetForCluster_canReadMinAvailable() {
    configureCluster("cluster1").withReplicas(5);

    assertThat(info.getMinAvailable("cluster1"), equalTo(4));
  }

  @Test
  void afterReplicaCountMaxUnavailableSetForCluster_zeroMin() {
    configureCluster("cluster1").withReplicas(3).withMaxUnavailable(10);

    assertThat(info.getMinAvailable("cluster1"), equalTo(0));
  }

  @Test
  void afterMaxUnavailableSetForCluster_canReadIt() {
    configureCluster("cluster1").withMaxUnavailable(5);

    assertThat(info.getMaxUnavailable("cluster1"), equalTo(5));
  }

  @Test
  void afterAllowReplicasBelowMinDynamicClusterSizeSetForCluster_canReadIt() {
    configureCluster("cluster1").withAllowReplicasBelowDynClusterSize(false);

    assertThat(info.isAllowReplicasBelowMinDynClusterSize("cluster1"), equalTo(false));
  }

  @Test
  void whenNotSpecified_allowReplicasBelowMinDynamicClusterSizeHasDefault() {
    configureCluster("cluster1");
    configureDomain(domain).withAllowReplicasBelowMinDynClusterSize(null);

    assertThat(info.isAllowReplicasBelowMinDynClusterSize("cluster1"),
        equalTo(DEFAULT_ALLOW_REPLICAS_BELOW_MIN_DYN_CLUSTER_SIZE));
  }

  @Test
  void whenNotSpecified_allowReplicasBelowMinDynamicClusterSizeFromDomain() {
    configureCluster("cluster1");
    configureDomain(domain).withAllowReplicasBelowMinDynClusterSize(false);

    assertThat(info.isAllowReplicasBelowMinDynClusterSize("cluster1"),
        equalTo(false));
  }

  @Test
  void whenNoClusterSpec_allowReplicasBelowMinDynamicClusterSizeHasDefault() {
    assertThat(info.isAllowReplicasBelowMinDynClusterSize("cluster-with-no-spec"),
        equalTo(DEFAULT_ALLOW_REPLICAS_BELOW_MIN_DYN_CLUSTER_SIZE));
  }

  @Test
  void whenBothClusterAndDomainSpecified_allowReplicasBelowMinDynamicClusterSizeFromCluster() {
    configureCluster("cluster1").withAllowReplicasBelowDynClusterSize(false);
    configureDomain(domain).withAllowReplicasBelowMinDynClusterSize(true);

    assertThat(info.isAllowReplicasBelowMinDynClusterSize("cluster1"),
        equalTo(false));
  }

  @Test
  void afterMaxConcurrentStartupSetForCluster_canReadIt() {
    configureCluster("cluster1").withMaxConcurrentStartup(3);

    assertThat(info.getMaxConcurrentStartup("cluster1"), equalTo(3));
  }

  @Test
  void whenNotSpecified_maxConcurrentStartupHasDefault() {
    configureCluster("cluster1");
    configureDomain(domain).withMaxConcurrentStartup(null);

    assertThat(info.getMaxConcurrentStartup("cluster1"),
        equalTo(DEFAULT_MAX_CLUSTER_CONCURRENT_START_UP));
  }

  @Test
  void whenNotSpecified_maxConcurrentStartupFromDomain() {
    configureCluster("cluster1");
    configureDomain(domain).withMaxConcurrentStartup(2);

    assertThat(info.getMaxConcurrentStartup("cluster1"),
        equalTo(2));
  }

  @Test
  void whenNoClusterSpec_maxConcurrentStartupHasDefault() {
    assertThat(info.getMaxConcurrentStartup("cluster-with-no-spec"),
        equalTo(DEFAULT_MAX_CLUSTER_CONCURRENT_START_UP));
  }

  @Test
  void whenBothClusterAndDomainSpecified_maxConcurrentStartupFromCluster() {
    configureCluster("cluster1").withMaxConcurrentStartup(1);
    configureDomain(domain).withMaxConcurrentStartup(0);

    assertThat(info.getMaxConcurrentStartup("cluster1"),
        equalTo(1));
  }

  @Test
  void afterMaxConcurrentShutdownSetForCluster_canReadIt() {
    configureCluster("cluster1").withMaxConcurrentShutdown(3);

    assertThat(info.getMaxConcurrentShutdown("cluster1"), equalTo(3));
  }

  @Test
  void whenNotSpecified_maxConcurrentShutdownHasDefault() {
    configureCluster("cluster1");
    configureDomain(domain).withMaxConcurrentShutdown(null);

    assertThat(info.getMaxConcurrentShutdown("cluster1"),
            equalTo(DEFAULT_MAX_CLUSTER_CONCURRENT_SHUTDOWN));
  }

  @Test
  void whenNotSpecified_maxConcurrentShutdownFromDomain() {
    configureDomain(domain).withMaxConcurrentShutdown(2);

    assertThat(info.getMaxConcurrentShutdown("cluster1"),
            equalTo(2));
  }

  @Test
  void whenNoClusterSpec_maxConcurrentShutdownHasDefault() {
    assertThat(info.getMaxConcurrentShutdown("cluster-with-no-spec"),
            equalTo(DEFAULT_MAX_CLUSTER_CONCURRENT_SHUTDOWN));
  }

  @Test
  void whenBothClusterAndDomainSpecified_maxConcurrentShutdownFromCluster() {
    configureCluster("cluster1").withMaxConcurrentShutdown(1);
    configureDomain(domain).withMaxConcurrentShutdown(0);

    assertThat(info.getMaxConcurrentShutdown("cluster1"),
            equalTo(1));
  }

  @Test
  void whenBothClusterAndServerStateSpecified_managedServerUsesServerState() {
    configureServer(SERVER1).withDesiredState(ServerStartState.ADMIN);
    configureCluster(CLUSTER_NAME).withDesiredState(ServerStartState.RUNNING);

    EffectiveServerSpec spec = info.getServer(SERVER1, CLUSTER_NAME);

    assertThat(spec.getDesiredState(), equalTo("ADMIN"));
  }

  @Test
  void whenSpecifiedOnServer_managedServerHasEnvironmentVariables() {
    configureServer(SERVER1)
        .withEnvironmentVariable(NAME1, VALUE1)
        .withEnvironmentVariable(NAME2, VALUE2);

    EffectiveServerSpec spec = info.getServer(SERVER1, CLUSTER_NAME);

    assertThat(spec.getEnvironmentVariables(), containsInAnyOrder(createEnvironment()));
  }

  @Test
  void whenSpecifiedOnCluster_managedServerHasEnvironmentVariables() {
    configureCluster(CLUSTER_NAME)
        .withEnvironmentVariable(NAME1, VALUE1)
        .withEnvironmentVariable(NAME2, VALUE2);

    EffectiveServerSpec spec = info.getServer(SERVER1, CLUSTER_NAME);

    assertThat(spec.getEnvironmentVariables(), containsInAnyOrder(createEnvironment()));
  }

  @Test
  void whenDesiredStateAdminAndSpecifiedOnCluster_managedServerHasEnvironmentVariables() {
    configureCluster(CLUSTER_NAME)
        .withDesiredState(ServerStartState.ADMIN)
        .withEnvironmentVariable("JAVA_OPTIONS", "value");

    EffectiveServerSpec spec = info.getServer(SERVER1, CLUSTER_NAME);

    assertThat(spec.getEnvironmentVariables(), hasItem(envVar("JAVA_OPTIONS", "value")));
  }

  @Test
  void whenDomainReadFromYaml_Server1OverridesDefaults() throws IOException {
    DomainPresenceInfo info = readDomainPresence(DOMAIN_V2_SAMPLE_YAML);
    EffectiveServerSpec effectiveServerSpec = info.getServer("server1", null);

    assertThat(
        effectiveServerSpec.getEnvironmentVariables(),
        both(hasItem(envVar("JAVA_OPTIONS", "-server")))
            .and(
                hasItem(
                    envVar(
                        "USER_MEM_ARGS",
                        "-Djava.security.egd=file:/dev/./urandom "))));
    assertThat(effectiveServerSpec.getDesiredState(), equalTo("RUNNING"));
  }

  @Test
  void whenDomainReadFromYaml_Server2OverridesDefaults() throws IOException {
    DomainPresenceInfo info = readDomainPresence(DOMAIN_V2_SAMPLE_YAML);
    EffectiveServerSpec effectiveServerSpec = info.getServer("server2", null);

    assertThat(effectiveServerSpec.getDesiredState(), equalTo("ADMIN"));
  }

  @Test
  void whenDomainReadFromYaml_Cluster2OverridesDefaults() throws IOException {
    DomainPresenceInfo info = readDomainPresence(DOMAIN_V2_SAMPLE_YAML);

    assertThat(info.getReplicaCount("cluster2"), equalTo(5));
    assertThat(
        info.getServer("server3", "cluster2").getEnvironmentVariables(),
        both(hasItem(envVar("JAVA_OPTIONS", "-verbose")))
            .and(hasItem(envVar("USER_MEM_ARGS", "-Xms64m -Xmx256m "))));
  }

  @Test
  void whenDomainResourceInitialized_hasCorrectApiVersionAndKind() {
    MatcherAssert.assertThat(domain.getApiVersion(), equalTo("weblogic.oracle/v9"));
    MatcherAssert.assertThat(domain.getKind(), equalTo("Domain"));
  }
}

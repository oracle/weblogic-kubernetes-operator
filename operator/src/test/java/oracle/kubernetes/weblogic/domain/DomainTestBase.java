// Copyright (c) 2018, 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.weblogic.domain;

import java.io.IOException;
import java.net.URL;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import io.kubernetes.client.openapi.models.V1EnvVar;
import io.kubernetes.client.openapi.models.V1LocalObjectReference;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1SecretReference;
import oracle.kubernetes.weblogic.domain.model.Domain;
import oracle.kubernetes.weblogic.domain.model.DomainSpec;
import oracle.kubernetes.weblogic.domain.model.ServerSpec;
import org.junit.Test;

import static oracle.kubernetes.operator.KubernetesConstants.ALWAYS_IMAGEPULLPOLICY;
import static oracle.kubernetes.operator.KubernetesConstants.DEFAULT_ALLOW_REPLICAS_BELOW_MIN_DYN_CLUSTER_SIZE;
import static oracle.kubernetes.operator.KubernetesConstants.DEFAULT_IMAGE;
import static oracle.kubernetes.operator.KubernetesConstants.DEFAULT_MAX_CLUSTER_CONCURRENT_START_UP;
import static oracle.kubernetes.operator.KubernetesConstants.IFNOTPRESENT_IMAGEPULLPOLICY;
import static oracle.kubernetes.operator.KubernetesConstants.LATEST_IMAGE_SUFFIX;
import static org.hamcrest.Matchers.both;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.junit.MatcherAssert.assertThat;

public abstract class DomainTestBase {
  protected static final String CLUSTER_NAME = "cluster1";
  protected static final String SERVER1 = "ms1";
  protected static final String SERVER2 = "ms2";
  private static final String NAME1 = "name1";
  private static final String NAME2 = "name2";
  private static final String VALUE1 = "value1";
  private static final String VALUE2 = "value2";
  private static final String NS = "test-namespace";
  protected static final String DOMAIN_UID = "uid1";
  private static final String DOMAIN_V2_SAMPLE_YAML = "model/domain-sample.yaml";
  private static final String IMAGE = "myimage";
  private static final String PULL_SECRET_NAME = "pull-secret";
  private static final String SECRET_NAME = "secret";
  protected final Domain domain = createDomain();

  protected static Domain createDomain() {
    return new Domain()
        .withMetadata(new V1ObjectMeta().namespace(NS))
        .withSpec(
              new DomainSpec()
                    .withWebLogicCredentialsSecret(new V1SecretReference().name(SECRET_NAME))
                    .withDomainUid(DOMAIN_UID));
  }

  protected static String getDomainUid() {
    return DOMAIN_UID;
  }

  protected static String getNamespace() {
    return NS;
  }

  protected abstract DomainConfigurator configureDomain(Domain domain);

  @Test
  public void canGetAdminServerInfoFromDomain() {
    assertThat(domain.getWebLogicCredentialsSecretName(), equalTo(SECRET_NAME));
  }

  @Test
  public void canGetDomainInfoFromDomain() {
    assertThat(domain.getDomainUid(), equalTo(DOMAIN_UID));
  }

  @Test
  public void adminServerSpecHasStandardValues() {
    ServerSpec spec = domain.getAdminServerSpec();

    verifyStandardFields(spec);
  }

  // Confirms the value of fields that are constant across the domain
  private void verifyStandardFields(ServerSpec spec) {
    assertThat(spec.getImage(), equalTo(DEFAULT_IMAGE));
    assertThat(spec.getImagePullPolicy(), equalTo(IFNOTPRESENT_IMAGEPULLPOLICY));
    assertThat(spec.getImagePullSecrets(), empty());
  }

  @Test
  public void unconfiguredManagedServerSpecHasStandardValues() {
    ServerSpec spec = domain.getServer("aServer", CLUSTER_NAME);

    verifyStandardFields(spec);
  }

  @SuppressWarnings("SameParameterValue")
  private V1EnvVar envVar(String name, String value) {
    return new V1EnvVar().name(name).value(value);
  }

  @Test
  public void unconfiguredAdminServer_hasNoEnvironmentVariables() {
    ServerSpec spec = domain.getAdminServerSpec();

    assertThat(spec.getEnvironmentVariables(), empty());
  }

  @Test
  public void whenDefaultImageSpecified_serversHaveSpecifiedImage() {
    configureDomain(domain).withDefaultImage(IMAGE);

    assertThat(domain.getAdminServerSpec().getImage(), equalTo(IMAGE));
    assertThat(domain.getServer("aServer", "aCluster").getImage(), equalTo(IMAGE));
  }

  @Test
  public void whenLatestImageSpecifiedAsDefault_serversHaveAlwaysPullPolicy() {
    configureDomain(domain).withDefaultImage(IMAGE + LATEST_IMAGE_SUFFIX);

    assertThat(domain.getAdminServerSpec().getImagePullPolicy(), equalTo(ALWAYS_IMAGEPULLPOLICY));
    assertThat(
        domain.getServer("aServer", "aCluster").getImagePullPolicy(),
        equalTo(ALWAYS_IMAGEPULLPOLICY));
  }

  @Test
  public void whenNotSpecified_imageHasDefault() {
    domain.getSpec().setImage(null);

    ServerSpec spec = domain.getAdminServerSpec();

    assertThat(spec.getImage(), equalTo(DEFAULT_IMAGE));
  }

  @Test
  public void whenImageTagIsLatestAndPullPolicyNotSpecified_pullPolicyIsAlways() {
    domain.getSpec().setImage("test:latest");
    domain.getSpec().setImagePullPolicy(null);

    ServerSpec spec = domain.getAdminServerSpec();

    assertThat(spec.getImagePullPolicy(), equalTo(ALWAYS_IMAGEPULLPOLICY));
  }

  @Test
  public void whenImageTagIsNotLatestAndPullPolicyNotSpecified_pullPolicyIsIfAbsent() {
    domain.getSpec().setImage("test:1.0");
    domain.getSpec().setImagePullPolicy(null);

    ServerSpec spec = domain.getAdminServerSpec();

    assertThat(spec.getImagePullPolicy(), equalTo(IFNOTPRESENT_IMAGEPULLPOLICY));
  }

  @Test
  public void whenImagePullPolicySpecifiedAsDefault_allServersHaveIt() {
    configureDomain(domain).withDefaultImagePullPolicy(ALWAYS_IMAGEPULLPOLICY);

    assertThat(domain.getAdminServerSpec().getImagePullPolicy(), equalTo(ALWAYS_IMAGEPULLPOLICY));
    assertThat(
        domain.getServer("aServer", "aCluster").getImagePullPolicy(),
        equalTo(ALWAYS_IMAGEPULLPOLICY));
  }

  @Test
  public void whenDefaultImagePullSecretSpecified_allServersHaveIt() {
    V1LocalObjectReference secretReference = createSecretReference(PULL_SECRET_NAME);
    configureDomain(domain).withDefaultImagePullSecrets(secretReference);

    assertThat(domain.getAdminServerSpec().getImagePullSecrets(), hasItem(secretReference));
    assertThat(
        domain.getServer("aServer", "aCluster").getImagePullSecrets(), hasItem(secretReference));
  }

  @SuppressWarnings("SameParameterValue")
  private V1LocalObjectReference createSecretReference(String pullSecretName) {
    return new V1LocalObjectReference().name(pullSecretName);
  }

  @Test
  public void whenSpecified_adminServerHasEnvironmentVariables() {
    configureAdminServer()
        .withEnvironmentVariable(NAME1, VALUE1)
        .withEnvironmentVariable(NAME2, VALUE2);

    ServerSpec spec = domain.getAdminServerSpec();

    assertThat(spec.getEnvironmentVariables(), containsInAnyOrder(createEnvironment()));
  }

  protected AdminServerConfigurator configureAdminServer() {
    return configureDomain(domain).configureAdminServer();
  }

  private V1EnvVar[] createEnvironment() {
    return new V1EnvVar[] {envVar(NAME1, VALUE1), envVar(NAME2, VALUE2)};
  }

  @Test
  public void whenOtherServersDefined_adminServerHasNoEnvironmentVariables() {
    configureServer(SERVER1)
        .withEnvironmentVariable(NAME1, VALUE1)
        .withEnvironmentVariable(NAME2, VALUE2);

    ServerSpec spec = domain.getAdminServerSpec();

    assertThat(spec.getEnvironmentVariables(), empty());
  }

  @Test
  public void whenSpecified_adminServerDesiredStateIsAsSpecified() {
    configureAdminServer().withDesiredState("ADMIN");

    ServerSpec spec = domain.getAdminServerSpec();

    assertThat(spec.getDesiredState(), equalTo("ADMIN"));
  }

  protected ServerConfigurator configureServer(String serverName) {
    return configureDomain(domain).configureServer(serverName);
  }

  @Test
  public void whenNotSpecified_adminServerDesiredStateIsRunning() {
    ServerSpec spec = domain.getAdminServerSpec();

    assertThat(spec.getDesiredState(), equalTo("RUNNING"));
  }

  @Test
  public void whenNotSpecified_managedServerDesiredStateIsRunning() {
    ServerSpec spec = domain.getServer(SERVER1, CLUSTER_NAME);

    assertThat(spec.getDesiredState(), equalTo("RUNNING"));
  }

  @Test
  public void whenSpecified_managedServerDesiredStateIsAsSpecified() {
    configureServer(SERVER1).withDesiredState("STAND-BY");

    ServerSpec spec = domain.getServer(SERVER1, CLUSTER_NAME);

    assertThat(spec.getDesiredState(), equalTo("STAND-BY"));
  }

  @Test
  public void whenOnlyAsStateSpecified_managedServerDesiredStateIsRunning() {
    configureAdminServer().withDesiredState("ADMIN");

    ServerSpec spec = domain.getServer(SERVER1, CLUSTER_NAME);

    assertThat(spec.getDesiredState(), equalTo("RUNNING"));
  }

  @Test
  public void whenClusterStateSpecified_managedServerDesiredStateIsAsSpecified() {
    configureCluster(CLUSTER_NAME).withDesiredState("NEVER");

    ServerSpec spec = domain.getServer(SERVER1, CLUSTER_NAME);

    assertThat(spec.getDesiredState(), equalTo("NEVER"));
  }

  protected ClusterConfigurator configureCluster(String clusterName) {
    return configureDomain(domain).configureCluster(clusterName);
  }

  @Test
  public void whenNoReplicaCountSpecified_canChangeIt() {
    domain.setReplicaCount("cluster1", 7);

    assertThat(domain.getReplicaCount("cluster1"), equalTo(7));
  }

  @Test
  public void afterReplicaCountSetForCluster_canReadIt() {
    configureCluster("cluster1").withReplicas(5);

    assertThat(domain.getReplicaCount("cluster1"), equalTo(5));
  }

  @Test
  public void afterReplicaCountSetForCluster_canChangeIt() {
    configureCluster("cluster1").withReplicas(5);

    domain.setReplicaCount("cluster1", 4);
    assertThat(domain.getReplicaCount("cluster1"), equalTo(4));
  }

  @Test
  public void afterReplicaCountMaxUnavailableSetForCluster_canReadMinAvailable() {
    configureCluster("cluster1").withReplicas(5).withMaxUnavailable(2);

    assertThat(domain.getMinAvailable("cluster1"), equalTo(3));
  }

  @Test
  public void afterReplicaCountSetForCluster_canReadMinAvailable() {
    configureCluster("cluster1").withReplicas(5);

    assertThat(domain.getMinAvailable("cluster1"), equalTo(4));
  }

  @Test
  public void afterReplicaCountMaxUnavailableSetForCluster_zeroMin() {
    configureCluster("cluster1").withReplicas(3).withMaxUnavailable(10);

    assertThat(domain.getMinAvailable("cluster1"), equalTo(0));
  }

  @Test
  public void afterMaxUnavailableSetForCluster_canReadIt() {
    configureCluster("cluster1").withMaxUnavailable(5);

    assertThat(domain.getMaxUnavailable("cluster1"), equalTo(5));
  }

  @Test
  public void afterAllowReplicasBelowMinDynamicClusterSizeSetForCluster_canReadIt() {
    configureCluster("cluster1").withAllowReplicasBelowDynClusterSize(false);

    assertThat(domain.isAllowReplicasBelowMinDynClusterSize("cluster1"), equalTo(false));
  }

  @Test
  public void whenNotSpecified_allowReplicasBelowMinDynamicClusterSizeHasDefault() {
    configureCluster("cluster1");
    configureDomain(domain).withAllowReplicasBelowMinDynClusterSize(null);

    assertThat(domain.isAllowReplicasBelowMinDynClusterSize("cluster1"),
        equalTo(DEFAULT_ALLOW_REPLICAS_BELOW_MIN_DYN_CLUSTER_SIZE));
  }

  @Test
  public void whenNotSpecified_allowReplicasBelowMinDynamicClusterSizeFromDomain() {
    configureCluster("cluster1");
    configureDomain(domain).withAllowReplicasBelowMinDynClusterSize(false);

    assertThat(domain.isAllowReplicasBelowMinDynClusterSize("cluster1"),
        equalTo(false));
  }

  @Test
  public void whenNoClusterSpec_allowReplicasBelowMinDynamicClusterSizeHasDefault() {
    assertThat(domain.isAllowReplicasBelowMinDynClusterSize("cluster-with-no-spec"),
        equalTo(DEFAULT_ALLOW_REPLICAS_BELOW_MIN_DYN_CLUSTER_SIZE));
  }

  @Test
  public void whenBothClusterAndDomainSpecified_allowReplicasBelowMinDynamicClusterSizeFromCluster() {
    configureCluster("cluster1").withAllowReplicasBelowDynClusterSize(false);
    configureDomain(domain).withAllowReplicasBelowMinDynClusterSize(true);

    assertThat(domain.isAllowReplicasBelowMinDynClusterSize("cluster1"),
        equalTo(false));
  }

  @Test
  public void afterMaxConcurrentStartupSetForCluster_canReadIt() {
    configureCluster("cluster1").withMaxConcurrentStartup(3);

    assertThat(domain.getMaxConcurrentStartup("cluster1"), equalTo(3));
  }

  @Test
  public void whenNotSpecified_maxConcurrentStartupHasDefault() {
    configureCluster("cluster1");
    configureDomain(domain).withMaxConcurrentStartup(null);

    assertThat(domain.getMaxConcurrentStartup("cluster1"),
        equalTo(DEFAULT_MAX_CLUSTER_CONCURRENT_START_UP));
  }

  @Test
  public void whenNotSpecified_maxConcurrentStartupFromDomain() {
    configureCluster("cluster1");
    configureDomain(domain).withMaxConcurrentStartup(2);

    assertThat(domain.getMaxConcurrentStartup("cluster1"),
        equalTo(2));
  }

  @Test
  public void whenNoClusterSpec_maxConcurrentStartupHasDefault() {
    assertThat(domain.getMaxConcurrentStartup("cluster-with-no-spec"),
        equalTo(DEFAULT_MAX_CLUSTER_CONCURRENT_START_UP));
  }

  @Test
  public void whenBothClusterAndDomainSpecified_maxConcurrentStartupFromCluster() {
    configureCluster("cluster1").withMaxConcurrentStartup(1);
    configureDomain(domain).withMaxConcurrentStartup(0);

    assertThat(domain.getMaxConcurrentStartup("cluster1"),
        equalTo(1));
  }

  @Test
  public void whenBothClusterAndServerStateSpecified_managedServerUsesServerState() {
    configureServer(SERVER1).withDesiredState("STAND-BY");
    configureCluster(CLUSTER_NAME).withDesiredState("NEVER");

    ServerSpec spec = domain.getServer(SERVER1, CLUSTER_NAME);

    assertThat(spec.getDesiredState(), equalTo("STAND-BY"));
  }

  @Test
  public void whenSpecifiedOnServer_managedServerHasEnvironmentVariables() {
    configureServer(SERVER1)
        .withEnvironmentVariable(NAME1, VALUE1)
        .withEnvironmentVariable(NAME2, VALUE2);

    ServerSpec spec = domain.getServer(SERVER1, CLUSTER_NAME);

    assertThat(spec.getEnvironmentVariables(), containsInAnyOrder(createEnvironment()));
  }

  @Test
  public void whenSpecifiedOnCluster_managedServerHasEnvironmentVariables() {
    configureCluster(CLUSTER_NAME)
        .withEnvironmentVariable(NAME1, VALUE1)
        .withEnvironmentVariable(NAME2, VALUE2);

    ServerSpec spec = domain.getServer(SERVER1, CLUSTER_NAME);

    assertThat(spec.getEnvironmentVariables(), containsInAnyOrder(createEnvironment()));
  }

  @Test
  public void whenDesiredStateAdminAndSpecifiedOnCluster_managedServerHasEnvironmentVariables() {
    configureCluster(CLUSTER_NAME)
        .withDesiredState("ADMIN")
        .withEnvironmentVariable("JAVA_OPTIONS", "value");

    ServerSpec spec = domain.getServer(SERVER1, CLUSTER_NAME);

    assertThat(spec.getEnvironmentVariables(), hasItem(envVar("JAVA_OPTIONS", "value")));
  }

  @Test
  public void whenDomainReadFromYaml_unconfiguredServerHasDomainDefaults() throws IOException {
    Domain domain = readDomain(DOMAIN_V2_SAMPLE_YAML);
    ServerSpec serverSpec = domain.getServer("server0", null);

    assertThat(serverSpec.getImage(), equalTo(DEFAULT_IMAGE));
    assertThat(serverSpec.getImagePullPolicy(), equalTo(IFNOTPRESENT_IMAGEPULLPOLICY));
    assertThat(serverSpec.getImagePullSecrets().get(0).getName(), equalTo("pull-secret"));
    assertThat(serverSpec.getEnvironmentVariables(), empty());
    assertThat(serverSpec.getDesiredState(), equalTo("RUNNING"));
  }

  @Test
  public void whenDomainReadFromYaml_Server1OverridesDefaults() throws IOException {
    Domain domain = readDomain(DOMAIN_V2_SAMPLE_YAML);
    ServerSpec serverSpec = domain.getServer("server1", null);

    assertThat(
        serverSpec.getEnvironmentVariables(),
        both(hasItem(envVar("JAVA_OPTIONS", "-server")))
            .and(
                hasItem(
                    envVar(
                        "USER_MEM_ARGS",
                        "-Djava.security.egd=file:/dev/./urandom "))));
    assertThat(serverSpec.getDesiredState(), equalTo("RUNNING"));
  }

  @Test
  public void whenDomainReadFromYaml_Server2OverridesDefaults() throws IOException {
    Domain domain = readDomain(DOMAIN_V2_SAMPLE_YAML);
    ServerSpec serverSpec = domain.getServer("server2", null);

    assertThat(serverSpec.getDesiredState(), equalTo("ADMIN"));
  }

  @Test
  public void whenDomainReadFromYaml_Cluster2OverridesDefaults() throws IOException {
    Domain domain = readDomain(DOMAIN_V2_SAMPLE_YAML);

    assertThat(domain.getReplicaCount("cluster2"), equalTo(5));
    assertThat(
        domain.getServer("server3", "cluster2").getEnvironmentVariables(),
        both(hasItem(envVar("JAVA_OPTIONS", "-verbose")))
            .and(hasItem(envVar("USER_MEM_ARGS", "-Xms64m -Xmx256m "))));
  }

  @SuppressWarnings("SameParameterValue")
  protected Domain readDomain(String resourceName) throws IOException {
    String json = jsonFromYaml(resourceName);
    Gson gson = new GsonBuilder().create();
    return gson.fromJson(json, Domain.class);
  }

  private String jsonFromYaml(String resourceName) throws IOException {
    URL resource = DomainTestBase.class.getResource(resourceName);
    ObjectMapper yamlReader = new ObjectMapper(new YAMLFactory());
    Object obj = yamlReader.readValue(resource, Object.class);

    ObjectMapper jsonWriter = new ObjectMapper();
    return jsonWriter.writeValueAsString(obj);
  }
}

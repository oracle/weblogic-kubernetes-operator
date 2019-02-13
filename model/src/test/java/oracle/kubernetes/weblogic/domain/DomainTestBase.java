// Copyright 2018, 2019, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.weblogic.domain;

import static oracle.kubernetes.operator.KubernetesConstants.ALWAYS_IMAGEPULLPOLICY;
import static oracle.kubernetes.operator.KubernetesConstants.DEFAULT_IMAGE;
import static oracle.kubernetes.operator.KubernetesConstants.IFNOTPRESENT_IMAGEPULLPOLICY;
import static oracle.kubernetes.operator.KubernetesConstants.LATEST_IMAGE_SUFFIX;
import static org.hamcrest.Matchers.both;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.junit.Assert.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import io.kubernetes.client.models.V1EnvVar;
import io.kubernetes.client.models.V1LocalObjectReference;
import io.kubernetes.client.models.V1ObjectMeta;
import io.kubernetes.client.models.V1SecretReference;
import java.io.IOException;
import java.net.URL;
import oracle.kubernetes.operator.KubernetesConstants;
import oracle.kubernetes.weblogic.domain.v2.Domain;
import oracle.kubernetes.weblogic.domain.v2.DomainSpec;
import oracle.kubernetes.weblogic.domain.v2.ServerSpec;
import org.junit.Test;

public abstract class DomainTestBase {
  private static final String NAME1 = "name1";
  private static final String NAME2 = "name2";
  private static final String VALUE1 = "value1";
  private static final String VALUE2 = "value2";
  private static final V1SecretReference SECRET = new V1SecretReference().name("secret");
  private static final String NS = "test-namespace";
  private static final String DOMAIN_UID = "uid1";
  private static final String DOMAIN_V2_SAMPLE_YAML = "v2/domain-sample.yaml";
  private static final String IMAGE = "myimage";
  private static final String PULL_SECRET_NAME = "pull-secret";
  protected static final String CLUSTER_NAME = "cluster1";
  protected static final String SERVER1 = "ms1";
  protected static final String SERVER2 = "ms2";
  protected final Domain domain = createDomain();

  protected static Domain createDomain() {
    return new Domain()
        .withMetadata(new V1ObjectMeta().namespace(NS))
        .withSpec(new DomainSpec().withWebLogicCredentialsSecret(SECRET).withDomainUID(DOMAIN_UID));
  }

  protected abstract DomainConfigurator configureDomain(Domain domain);

  protected static String getDomainUid() {
    return DOMAIN_UID;
  }

  protected static String getNamespace() {
    return NS;
  }

  @Test
  public void canGetAdminServerInfoFromDomain() {
    assertThat(domain.getWebLogicCredentialsSecret(), equalTo(SECRET));
  }

  @Test
  public void canGetDomainInfoFromDomain() {
    assertThat(domain.getDomainUID(), equalTo(DOMAIN_UID));
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

    assertThat(spec.getImage(), equalTo(KubernetesConstants.DEFAULT_IMAGE));
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
  public void whenDesiredStateAdminAndSpecifiedOnServer_managedServerHasJavaOption() {
    configureServer(SERVER1)
        .withDesiredState("ADMIN")
        .withEnvironmentVariable(NAME1, VALUE1)
        .withEnvironmentVariable(NAME2, VALUE2);

    ServerSpec spec = domain.getServer(SERVER1, CLUSTER_NAME);

    assertThat(
        spec.getEnvironmentVariables(),
        hasItem(envVar("JAVA_OPTIONS", "-Dweblogic.management.startupMode=ADMIN")));
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

    assertThat(
        spec.getEnvironmentVariables(),
        hasItem(envVar("JAVA_OPTIONS", "-Dweblogic.management.startupMode=ADMIN value")));
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
                        "-Djava.security.egd=file:/dev/./urandom -Xms64m -Xmx256m "))));
    assertThat(serverSpec.getDesiredState(), equalTo("RUNNING"));
  }

  @Test
  public void whenDomainReadFromYaml_Server2OverridesDefaults() throws IOException {
    Domain domain = readDomain(DOMAIN_V2_SAMPLE_YAML);
    ServerSpec serverSpec = domain.getServer("server2", null);

    assertThat(
        serverSpec.getEnvironmentVariables(),
        hasItem(envVar("JAVA_OPTIONS", "-Dweblogic.management.startupMode=ADMIN")));
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

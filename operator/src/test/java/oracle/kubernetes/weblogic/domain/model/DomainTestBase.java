// Copyright (c) 2018, 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.weblogic.domain.model;

import java.io.IOException;
import java.net.URL;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import io.kubernetes.client.openapi.models.V1Container;
import io.kubernetes.client.openapi.models.V1EnvVar;
import io.kubernetes.client.openapi.models.V1LocalObjectReference;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1SecretReference;
import oracle.kubernetes.operator.ServerStartState;
import oracle.kubernetes.operator.helpers.DomainPresenceInfo;
import oracle.kubernetes.weblogic.domain.AdminServerConfigurator;
import oracle.kubernetes.weblogic.domain.ClusterConfigurator;
import oracle.kubernetes.weblogic.domain.DomainConfigurator;
import oracle.kubernetes.weblogic.domain.ServerConfigurator;
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

public abstract class DomainTestBase {
  protected static final String CLUSTER_NAME = "cluster1";
  protected static final String SERVER1 = "ms1";
  protected static final String SERVER2 = "ms2";
  protected static final String DOMAIN_V2_SAMPLE_YAML = "domain-sample.yaml";
  protected static final String DOMAIN_V2_SAMPLE_YAML_2 = "domain-sample-2.yaml";
  protected static final String DOMAIN_V2_SAMPLE_YAML_3 = "domain-sample-3.yaml";
  protected static final String DOMAIN_V2_SAMPLE_YAML_4 = "domain-sample-4.yaml";
  protected static final String DOMAIN_V2_SAMPLE_YAML_5 = "domain-sample-5.yaml";
  public static final String DOMAIN_V8_AUX_IMAGE30_YAML = "aux-image-30-sample.yaml";
  public static final String DOMAIN_V9_CONVERTED_LEGACY_AUX_IMAGE_YAML = "converted-domain-sample.yaml";
  public static final String DOMAIN_V8_SERVER_SCOPED_AUX_IMAGE30_YAML = "aux-image-30-sample-2.yaml";
  public static final String DOMAIN_V9_CONVERTED_SERVER_SCOPED_LEGACY_AUX_IMAGE_YAML = "converted-domain-sample-2.yaml";
  public static final String CONVERSION_REVIEW_REQUEST = "conversion-review-request.yaml";
  public static final String CONVERSION_REVIEW_RESPONSE = "conversion-review-response.yaml";
  private static final String NAME1 = "name1";
  private static final String NAME2 = "name2";
  private static final String VALUE1 = "value1";
  private static final String VALUE2 = "value2";
  private static final String NS = "test-namespace";
  protected static final String DOMAIN_UID = "uid1";
  private static final String IMAGE = "myimage";
  private static final String PULL_SECRET_NAME = "pull-secret";
  private static final String SECRET_NAME = "secret";
  protected final Domain domain = createDomain();
  protected final DomainPresenceInfo domainPresenceInfo = createDomainPresenceInfo();

  protected static Domain createDomain() {
    return new Domain()
        .withMetadata(new V1ObjectMeta().namespace(NS))
        .withSpec(
              new DomainSpec()
                    .withWebLogicCredentialsSecret(new V1SecretReference().name(SECRET_NAME))
                    .withDomainUid(DOMAIN_UID));
  }

  private DomainPresenceInfo createDomainPresenceInfo() {
    return new DomainPresenceInfo(domain);
  }

  protected DomainPresenceInfo createDomainPresenceInfo(Domain domain) {
    return new DomainPresenceInfo(domain);
  }

  protected abstract DomainConfigurator configureDomain(Domain domain);

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
    ServerSpec spec = domain.getAdminServerSpec();

    verifyStandardFields(spec);
  }

  // Confirms the value of fields that are constant across the domain
  private void verifyStandardFields(ServerSpec spec) {
    assertThat(spec.getImage(), equalTo(DEFAULT_IMAGE));
    assertThat(spec.getImagePullPolicy(), equalTo(V1Container.ImagePullPolicyEnum.IFNOTPRESENT));
    assertThat(spec.getImagePullSecrets(), empty());
  }

  @Test
  void unconfiguredManagedServerSpecHasStandardValues() {
    ServerSpec spec = domainPresenceInfo.getServer("aServer", CLUSTER_NAME);

    verifyStandardFields(spec);
  }

  @SuppressWarnings("SameParameterValue")
  private V1EnvVar envVar(String name, String value) {
    return new V1EnvVar().name(name).value(value);
  }

  @Test
  void unconfiguredAdminServer_hasNoEnvironmentVariables() {
    ServerSpec spec = domain.getAdminServerSpec();

    assertThat(spec.getEnvironmentVariables(), empty());
  }

  @Test
  void whenDefaultImageSpecified_serversHaveSpecifiedImage() {
    configureDomain(domain).withDefaultImage(IMAGE);

    assertThat(domain.getAdminServerSpec().getImage(), equalTo(IMAGE));
    assertThat(domainPresenceInfo.getServer("aServer", "aCluster").getImage(), equalTo(IMAGE));
  }

  @Test
  void whenLatestImageSpecifiedAsDefault_serversHaveAlwaysPullPolicy() {
    configureDomain(domain).withDefaultImage(IMAGE + LATEST_IMAGE_SUFFIX);

    assertThat(domain.getAdminServerSpec().getImagePullPolicy(), equalTo(V1Container.ImagePullPolicyEnum.ALWAYS));
    assertThat(
        domainPresenceInfo.getServer("aServer", "aCluster").getImagePullPolicy(),
        equalTo(V1Container.ImagePullPolicyEnum.ALWAYS));
  }

  @Test
  void whenNotSpecified_imageHasDefault() {
    domain.getSpec().setImage(null);

    ServerSpec spec = domain.getAdminServerSpec();

    assertThat(spec.getImage(), equalTo(DEFAULT_IMAGE));
  }

  @Test
  void whenImageTagIsLatestAndPullPolicyNotSpecified_pullPolicyIsAlways() {
    domain.getSpec().setImage("test:latest");
    domain.getSpec().setImagePullPolicy(null);

    ServerSpec spec = domain.getAdminServerSpec();

    assertThat(spec.getImagePullPolicy(), equalTo(V1Container.ImagePullPolicyEnum.ALWAYS));
  }

  @Test
  void whenImageTagIsNotLatestAndPullPolicyNotSpecified_pullPolicyIsIfAbsent() {
    domain.getSpec().setImage("test:1.0");
    domain.getSpec().setImagePullPolicy(null);

    ServerSpec spec = domain.getAdminServerSpec();

    assertThat(spec.getImagePullPolicy(), equalTo(V1Container.ImagePullPolicyEnum.IFNOTPRESENT));
  }

  @Test
  void whenImagePullPolicySpecifiedAsDefault_allServersHaveIt() {
    configureDomain(domain).withDefaultImagePullPolicy(V1Container.ImagePullPolicyEnum.ALWAYS);

    assertThat(domain.getAdminServerSpec().getImagePullPolicy(), equalTo(V1Container.ImagePullPolicyEnum.ALWAYS));
    assertThat(
        domainPresenceInfo.getServer("aServer", "aCluster").getImagePullPolicy(),
        equalTo(V1Container.ImagePullPolicyEnum.ALWAYS));
  }

  @Test
  void whenDefaultImagePullSecretSpecified_allServersHaveIt() {
    V1LocalObjectReference secretReference = createSecretReference(PULL_SECRET_NAME);
    configureDomain(domain).withDefaultImagePullSecrets(secretReference);

    assertThat(domain.getAdminServerSpec().getImagePullSecrets(), hasItem(secretReference));
    assertThat(
        domainPresenceInfo.getServer("aServer", "aCluster").getImagePullSecrets(), hasItem(secretReference));
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
  void whenOtherServersDefined_adminServerHasNoEnvironmentVariables() {
    configureServer(SERVER1)
        .withEnvironmentVariable(NAME1, VALUE1)
        .withEnvironmentVariable(NAME2, VALUE2);

    ServerSpec spec = domain.getAdminServerSpec();

    assertThat(spec.getEnvironmentVariables(), empty());
  }

  @Test
  void whenSpecified_adminServerDesiredStateIsAsSpecified() {
    configureAdminServer().withDesiredState(ServerStartState.ADMIN);

    ServerSpec spec = domain.getAdminServerSpec();

    assertThat(spec.getDesiredState(), equalTo("ADMIN"));
  }

  protected ServerConfigurator configureServer(String serverName) {
    return configureDomain(domain).configureServer(serverName);
  }

  @Test
  void whenNotSpecified_adminServerDesiredStateIsRunning() {
    ServerSpec spec = domain.getAdminServerSpec();

    assertThat(spec.getDesiredState(), equalTo("RUNNING"));
  }

  @Test
  void whenNotSpecified_managedServerDesiredStateIsRunning() {
    ServerSpec spec = domainPresenceInfo.getServer(SERVER1, CLUSTER_NAME);

    assertThat(spec.getDesiredState(), equalTo("RUNNING"));
  }

  @Test
  void whenSpecified_managedServerDesiredStateIsAsSpecified() {
    configureServer(SERVER1).withDesiredState(ServerStartState.ADMIN);

    ServerSpec spec = domainPresenceInfo.getServer(SERVER1, CLUSTER_NAME);

    assertThat(spec.getDesiredState(), equalTo("ADMIN"));
  }

  @Test
  void whenOnlyAsStateSpecified_managedServerDesiredStateIsRunning() {
    configureAdminServer().withDesiredState(ServerStartState.ADMIN);

    ServerSpec spec = domainPresenceInfo.getServer(SERVER1, CLUSTER_NAME);

    assertThat(spec.getDesiredState(), equalTo("RUNNING"));
  }

  @Test
  void whenClusterStateSpecified_managedServerDesiredStateIsAsSpecified() {
    configureCluster(CLUSTER_NAME).withDesiredState(ServerStartState.ADMIN);

    ServerSpec spec = domainPresenceInfo.getServer(SERVER1, CLUSTER_NAME);

    assertThat(spec.getDesiredState(), equalTo("ADMIN"));
  }

  protected ClusterConfigurator configureCluster(String clusterName) {
    return configureDomain(domain).configureCluster(clusterName);
  }

  @Test
  void whenNoReplicaCountSpecified_canChangeIt() {
    domainPresenceInfo.setReplicaCount("cluster1", 7);

    assertThat(domainPresenceInfo.getReplicaCount("cluster1"), equalTo(7));
  }

  @Test
  void afterReplicaCountSetForCluster_canReadIt() {
    configureCluster("cluster1").withReplicas(5);

    assertThat(domainPresenceInfo.getReplicaCount("cluster1"), equalTo(5));
  }

  @Test
  void afterReplicaCountSetForCluster_canChangeIt() {
    configureCluster("cluster1").withReplicas(5);

    domainPresenceInfo.setReplicaCount("cluster1", 4);
    assertThat(domainPresenceInfo.getReplicaCount("cluster1"), equalTo(4));
  }

  @Test
  void afterReplicaCountMaxUnavailableSetForCluster_canReadMinAvailable() {
    configureCluster("cluster1").withReplicas(5).withMaxUnavailable(2);

    assertThat(domainPresenceInfo.getMinAvailable("cluster1"), equalTo(3));
  }

  @Test
  void afterReplicaCountSetForCluster_canReadMinAvailable() {
    configureCluster("cluster1").withReplicas(5);

    assertThat(domainPresenceInfo.getMinAvailable("cluster1"), equalTo(4));
  }

  @Test
  void afterReplicaCountMaxUnavailableSetForCluster_zeroMin() {
    configureCluster("cluster1").withReplicas(3).withMaxUnavailable(10);

    assertThat(domainPresenceInfo.getMinAvailable("cluster1"), equalTo(0));
  }

  @Test
  void afterMaxUnavailableSetForCluster_canReadIt() {
    configureCluster("cluster1").withMaxUnavailable(5);

    assertThat(domainPresenceInfo.getMaxUnavailable("cluster1"), equalTo(5));
  }

  @Test
  void afterAllowReplicasBelowMinDynamicClusterSizeSetForCluster_canReadIt() {
    configureCluster("cluster1").withAllowReplicasBelowDynClusterSize(false);

    assertThat(domainPresenceInfo.isAllowReplicasBelowMinDynClusterSize("cluster1"), equalTo(false));
  }

  @Test
  void whenNotSpecified_allowReplicasBelowMinDynamicClusterSizeHasDefault() {
    configureCluster("cluster1");
    configureDomain(domain).withAllowReplicasBelowMinDynClusterSize(null);

    assertThat(domainPresenceInfo.isAllowReplicasBelowMinDynClusterSize("cluster1"),
        equalTo(DEFAULT_ALLOW_REPLICAS_BELOW_MIN_DYN_CLUSTER_SIZE));
  }

  @Test
  void whenNotSpecified_allowReplicasBelowMinDynamicClusterSizeFromDomain() {
    configureCluster("cluster1");
    configureDomain(domain).withAllowReplicasBelowMinDynClusterSize(false);

    assertThat(domainPresenceInfo.isAllowReplicasBelowMinDynClusterSize("cluster1"),
        equalTo(false));
  }

  @Test
  void whenNoClusterSpec_allowReplicasBelowMinDynamicClusterSizeHasDefault() {
    assertThat(domainPresenceInfo.isAllowReplicasBelowMinDynClusterSize("cluster-with-no-spec"),
        equalTo(DEFAULT_ALLOW_REPLICAS_BELOW_MIN_DYN_CLUSTER_SIZE));
  }

  @Test
  void whenBothClusterAndDomainSpecified_allowReplicasBelowMinDynamicClusterSizeFromCluster() {
    configureCluster("cluster1").withAllowReplicasBelowDynClusterSize(false);
    configureDomain(domain).withAllowReplicasBelowMinDynClusterSize(true);

    assertThat(domainPresenceInfo.isAllowReplicasBelowMinDynClusterSize("cluster1"),
        equalTo(false));
  }

  @Test
  void afterMaxConcurrentStartupSetForCluster_canReadIt() {
    configureCluster("cluster1").withMaxConcurrentStartup(3);

    assertThat(domainPresenceInfo.getMaxConcurrentStartup("cluster1"), equalTo(3));
  }

  @Test
  void whenNotSpecified_maxConcurrentStartupHasDefault() {
    configureCluster("cluster1");
    configureDomain(domain).withMaxConcurrentStartup(null);

    assertThat(domainPresenceInfo.getMaxConcurrentStartup("cluster1"),
        equalTo(DEFAULT_MAX_CLUSTER_CONCURRENT_START_UP));
  }

  @Test
  void whenNotSpecified_maxConcurrentStartupFromDomain() {
    configureCluster("cluster1");
    configureDomain(domain).withMaxConcurrentStartup(2);

    assertThat(domainPresenceInfo.getMaxConcurrentStartup("cluster1"),
        equalTo(2));
  }

  @Test
  void whenNoClusterSpec_maxConcurrentStartupHasDefault() {
    assertThat(domainPresenceInfo.getMaxConcurrentStartup("cluster-with-no-spec"),
        equalTo(DEFAULT_MAX_CLUSTER_CONCURRENT_START_UP));
  }

  @Test
  void whenBothClusterAndDomainSpecified_maxConcurrentStartupFromCluster() {
    configureCluster("cluster1").withMaxConcurrentStartup(1);
    configureDomain(domain).withMaxConcurrentStartup(0);

    assertThat(domainPresenceInfo.getMaxConcurrentStartup("cluster1"),
        equalTo(1));
  }

  @Test
  void afterMaxConcurrentShutdownSetForCluster_canReadIt() {
    configureCluster("cluster1").withMaxConcurrentShutdown(3);

    assertThat(domainPresenceInfo.getMaxConcurrentShutdown("cluster1"), equalTo(3));
  }

  @Test
  void whenNotSpecified_maxConcurrentShutdownHasDefault() {
    configureCluster("cluster1");
    configureDomain(domain).withMaxConcurrentShutdown(null);

    assertThat(domainPresenceInfo.getMaxConcurrentShutdown("cluster1"),
            equalTo(DEFAULT_MAX_CLUSTER_CONCURRENT_SHUTDOWN));
  }

  @Test
  void whenNotSpecified_maxConcurrentShutdownFromDomain() {
    configureDomain(domain).withMaxConcurrentShutdown(2);

    assertThat(domainPresenceInfo.getMaxConcurrentShutdown("cluster1"),
            equalTo(2));
  }

  @Test
  void whenNoClusterSpec_maxConcurrentShutdownHasDefault() {
    assertThat(domainPresenceInfo.getMaxConcurrentShutdown("cluster-with-no-spec"),
            equalTo(DEFAULT_MAX_CLUSTER_CONCURRENT_SHUTDOWN));
  }

  @Test
  void whenBothClusterAndDomainSpecified_maxConcurrentShutdownFromCluster() {
    configureCluster("cluster1").withMaxConcurrentShutdown(1);
    configureDomain(domain).withMaxConcurrentShutdown(0);

    assertThat(domainPresenceInfo.getMaxConcurrentShutdown("cluster1"),
            equalTo(1));
  }

  @Test
  void whenBothClusterAndServerStateSpecified_managedServerUsesServerState() {
    configureServer(SERVER1).withDesiredState(ServerStartState.ADMIN);
    configureCluster(CLUSTER_NAME).withDesiredState(ServerStartState.RUNNING);

    ServerSpec spec = domainPresenceInfo.getServer(SERVER1, CLUSTER_NAME);

    assertThat(spec.getDesiredState(), equalTo("ADMIN"));
  }

  @Test
  void whenSpecifiedOnServer_managedServerHasEnvironmentVariables() {
    configureServer(SERVER1)
        .withEnvironmentVariable(NAME1, VALUE1)
        .withEnvironmentVariable(NAME2, VALUE2);

    ServerSpec spec = domainPresenceInfo.getServer(SERVER1, CLUSTER_NAME);

    assertThat(spec.getEnvironmentVariables(), containsInAnyOrder(createEnvironment()));
  }

  @Test
  void whenSpecifiedOnCluster_managedServerHasEnvironmentVariables() {
    configureCluster(CLUSTER_NAME)
        .withEnvironmentVariable(NAME1, VALUE1)
        .withEnvironmentVariable(NAME2, VALUE2);

    ServerSpec spec = domainPresenceInfo.getServer(SERVER1, CLUSTER_NAME);

    assertThat(spec.getEnvironmentVariables(), containsInAnyOrder(createEnvironment()));
  }

  @Test
  void whenDesiredStateAdminAndSpecifiedOnCluster_managedServerHasEnvironmentVariables() {
    configureCluster(CLUSTER_NAME)
        .withDesiredState(ServerStartState.ADMIN)
        .withEnvironmentVariable("JAVA_OPTIONS", "value");

    ServerSpec spec = domainPresenceInfo.getServer(SERVER1, CLUSTER_NAME);

    assertThat(spec.getEnvironmentVariables(), hasItem(envVar("JAVA_OPTIONS", "value")));
  }

  @Test
  void whenDomainReadFromYaml_unconfiguredServerHasDomainDefaults() throws IOException {
    Domain domain = readDomain(DOMAIN_V2_SAMPLE_YAML);
    ServerSpec serverSpec = domainPresenceInfo.getServer("server0", null);

    assertThat(serverSpec.getImage(), equalTo(DEFAULT_IMAGE));
    assertThat(serverSpec.getImagePullPolicy(), equalTo(V1Container.ImagePullPolicyEnum.IFNOTPRESENT));
    assertThat(serverSpec.getImagePullSecrets().get(0).getName(), equalTo("pull-secret"));
    assertThat(serverSpec.getEnvironmentVariables(), empty());
    assertThat(serverSpec.getDesiredState(), equalTo("RUNNING"));
  }

  @Test
  void whenDomainReadFromYaml_Server1OverridesDefaults() throws IOException {
    Domain domain = readDomain(DOMAIN_V2_SAMPLE_YAML);
    DomainPresenceInfo domainPresenceInfo = new DomainPresenceInfo((domain));
    ServerSpec serverSpec = domainPresenceInfo.getServer("server1", null);

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
  void whenDomainReadFromYaml_Server2OverridesDefaults() throws IOException {
    Domain domain = readDomain(DOMAIN_V2_SAMPLE_YAML);
    DomainPresenceInfo domainPresenceInfo = new DomainPresenceInfo((domain));
    ServerSpec serverSpec = domainPresenceInfo.getServer("server2", null);

    assertThat(serverSpec.getDesiredState(), equalTo("ADMIN"));
  }

  @Test
  void whenDomainReadFromYaml_Cluster2OverridesDefaults() throws IOException {
    Domain domain = readDomain(DOMAIN_V2_SAMPLE_YAML);
    DomainPresenceInfo domainPresenceInfo = new DomainPresenceInfo((domain));
    assertThat(domainPresenceInfo.getReplicaCount("cluster2"), equalTo(5));
    assertThat(
        domainPresenceInfo.getServer("server3", "cluster2").getEnvironmentVariables(),
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

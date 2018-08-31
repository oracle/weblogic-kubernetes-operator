package oracle.kubernetes.weblogic.domain.v1;

import static oracle.kubernetes.operator.KubernetesConstants.ALWAYS_IMAGEPULLPOLICY;
import static oracle.kubernetes.operator.KubernetesConstants.IFNOTPRESENT_IMAGEPULLPOLICY;
import static oracle.kubernetes.operator.StartupControlConstants.ALL_STARTUPCONTROL;
import static oracle.kubernetes.operator.StartupControlConstants.AUTO_STARTUPCONTROL;
import static oracle.kubernetes.operator.StartupControlConstants.NONE_STARTUPCONTROL;
import static oracle.kubernetes.operator.StartupControlConstants.SPECIFIED_STARTUPCONTROL;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.assertThat;

import io.kubernetes.client.models.V1EnvVar;
import io.kubernetes.client.models.V1SecretReference;
import java.util.Arrays;
import java.util.Collections;
import oracle.kubernetes.operator.KubernetesConstants;
import oracle.kubernetes.weblogic.domain.DomainConfigurator;
import org.junit.Test;

public class DomainV1Test {

  private static final V1SecretReference SECRET = new V1SecretReference().name("secret");
  private static final String AS_NAME = "admin";
  private static final int AS_PORT = 8000;
  private static final String DOMAIN_NAME = "test";
  private static final String DOMAIN_UID = "uid1";
  private static final String IMAGE_NAME = "myimage";
  private static final String IMAGE_PULL_POLICY = "pull";
  private static final String NAME1 = "name1";
  private static final String NAME2 = "name2";
  private static final String VALUE1 = "value1";
  private static final String VALUE2 = "value2";
  private static final String CLUSTER_NAME = "cluster1";
  private static final String SERVER1 = "ms1";

  private final Domain domain =
      new Domain()
          .withSpec(
              new DomainSpec()
                  .withAdminSecret(SECRET)
                  .withAsName(AS_NAME)
                  .withAsPort(AS_PORT)
                  .withDomainName(DOMAIN_NAME)
                  .withDomainUID(DOMAIN_UID)
                  .withImage(IMAGE_NAME)
                  .withImagePullPolicy(IMAGE_PULL_POLICY)
                  .withStartupControl(NONE_STARTUPCONTROL));

  @Test
  public void canGetAdminServerInfoFromDomain() {
    assertThat(domain.getAsName(), equalTo(AS_NAME));
    assertThat(domain.getAsPort(), equalTo(AS_PORT));
    assertThat(domain.getAdminSecret(), equalTo(SECRET));
  }

  @Test
  public void canGetDomainInfoFromDomain() {
    assertThat(domain.getDomainName(), equalTo(DOMAIN_NAME));
    assertThat(domain.getDomainUID(), equalTo(DOMAIN_UID));
  }

  @Test
  public void whenStartupControlSpecified_returnIt() {
    assertThat(domain.getEffectiveStartupControl(), equalTo(NONE_STARTUPCONTROL));
  }

  @Test
  public void whenStartupControlNotSpecified_defaultToAuto() {
    domain.getSpec().setStartupControl(null);

    assertThat(domain.getEffectiveStartupControl(), equalTo(AUTO_STARTUPCONTROL));
  }

  @Test
  public void whenStartupControlIsMixedCase_capitalizeIt() {
    domain.getSpec().setStartupControl("auto");

    assertThat(domain.getEffectiveStartupControl(), equalTo("AUTO"));
  }

  @Test
  public void adminServerSpecHasStandardValues() {
    ServerSpec spec = domain.getAdminServerSpec();

    verifyStandardFields(spec);
  }

  // Confirms the value of fields that are constant across the domain
  private void verifyStandardFields(ServerSpec spec) {
    assertThat(spec.getImage(), equalTo(IMAGE_NAME));
    assertThat(spec.getImagePullPolicy(), equalTo(IMAGE_PULL_POLICY));
  }

  @Test
  public void whenServerStartupIsNull_adminServerHasNoEnvironmentVariables() {
    domain.getSpec().setServerStartup(null);

    ServerSpec spec = domain.getAdminServerSpec();

    assertThat(spec.getEnvironmentVariables(), empty());
  }

  @Test
  public void whenNotSpecified_adminServerHasNoEnvironmentVariables() {
    ServerSpec spec = domain.getAdminServerSpec();

    assertThat(spec.getEnvironmentVariables(), empty());
  }

  @Test
  public void whenSpecified_adminServerHasEnvironmentVariables() {
    addServerStartup(
        new ServerStartup().withServerName(AS_NAME).withEnv(Arrays.asList(createEnvironment())));

    ServerSpec spec = domain.getAdminServerSpec();

    assertThat(spec.getEnvironmentVariables(), containsInAnyOrder(createEnvironment()));
  }

  private void addServerStartup(ServerStartup serverStartup) {
    domain.getSpec().setServerStartup(Collections.singletonList(serverStartup));
  }

  private V1EnvVar[] createEnvironment() {
    return new V1EnvVar[] {
      new V1EnvVar().name(NAME1).value(VALUE1), new V1EnvVar().name(NAME2).value(VALUE2)
    };
  }

  @Test
  public void whenOtherServersDefined_adminServerHasNoEnvironmentVariables() {
    addServerStartup(
        new ServerStartup().withServerName(SERVER1).withEnv(Arrays.asList(createEnvironment())));

    ServerSpec spec = domain.getAdminServerSpec();

    assertThat(spec.getEnvironmentVariables(), empty());
  }

  @Test
  public void whenNotSpecified_adminServerDesiredStateIsRunning() {
    ServerSpec spec = domain.getAdminServerSpec();

    assertThat(spec.getDesiredState(), equalTo("RUNNING"));
  }

  @Test
  public void whenSpecified_adminServerDesiredStateIsAsSpecified() {
    addServerStartup(new ServerStartup().withServerName(AS_NAME).withDesiredState("ADMIN"));

    ServerSpec spec = domain.getAdminServerSpec();

    assertThat(spec.getDesiredState(), equalTo("ADMIN"));
  }

  @Test
  public void managedServerSpecHasStandardValues() {
    ServerSpec spec = domain.getServer(SERVER1, CLUSTER_NAME);

    verifyStandardFields(spec);
  }

  @Test
  public void whenStartAll_shouldStartReturnsTrue() {
    domain.getSpec().setStartupControl(ALL_STARTUPCONTROL);

    assertThat(domain.getServer(CLUSTER_NAME, SERVER1).shouldStart(0), is(true));
  }

  @Test
  public void whenStartAutoAndNamedClusterHasRoomByDefault_shouldStartReturnsTrue() {
    domain.getSpec().setStartupControl(AUTO_STARTUPCONTROL);
    domain.getSpec().setReplicas(3);

    assertThat(domain.getServer(SERVER1, CLUSTER_NAME).shouldStart(2), is(true));
  }

  @Test
  public void whenStartAutoAndNamedClusterHasRoomExplicitly_shouldStartReturnsTrue() {
    domain.getSpec().setStartupControl(AUTO_STARTUPCONTROL);
    domain.getSpec().setReplicas(1);
    addClusterStartup(new ClusterStartup().withClusterName(CLUSTER_NAME).withReplicas(3));

    assertThat(domain.getServer(SERVER1, CLUSTER_NAME).shouldStart(2), is(true));
  }

  @Test
  public void whenStartAutoAndNamedClusterHasNoRoomByDefault_shouldStartReturnsFalse() {
    domain.getSpec().setStartupControl(AUTO_STARTUPCONTROL);
    domain.getSpec().setReplicas(3);

    assertThat(domain.getServer(SERVER1, CLUSTER_NAME).shouldStart(3), is(false));
  }

  @Test
  public void whenStartAutoAndNamedClusterHasNoRoomExplicitly_shouldStartReturnsFalse() {
    domain.getSpec().setStartupControl(AUTO_STARTUPCONTROL);
    domain.getSpec().setReplicas(5);
    addClusterStartup(new ClusterStartup().withClusterName(CLUSTER_NAME).withReplicas(1));

    assertThat(domain.getServer(SERVER1, CLUSTER_NAME).shouldStart(2), is(false));
  }

  @Test
  public void whenStartAutoAndServerSpecifiedWithoutCluster_shouldStartReturnsTrue() {
    domain.getSpec().setStartupControl(AUTO_STARTUPCONTROL);
    domain.getSpec().setReplicas(5);
    addServerStartup(new ServerStartup().withServerName(SERVER1));

    assertThat(domain.getServer(SERVER1, null).shouldStart(0), is(true));
  }

  @Test
  public void whenStartSpecifiedAndSpecifiedClusterHasRoom_shouldStartReturnsTrue() {
    domain.getSpec().setStartupControl(SPECIFIED_STARTUPCONTROL);
    addClusterStartup(new ClusterStartup().withClusterName(CLUSTER_NAME).withReplicas(3));

    assertThat(domain.getServer(SERVER1, CLUSTER_NAME).shouldStart(2), is(true));
  }

  @Test
  public void whenStartSpecifiedAndServerSpecifiedWithoutCluster_shouldStartReturnsTrue() {
    domain.getSpec().setStartupControl(SPECIFIED_STARTUPCONTROL);
    domain.getSpec().setReplicas(5);
    addServerStartup(new ServerStartup().withServerName(SERVER1));

    assertThat(domain.getServer(SERVER1, null).shouldStart(0), is(true));
  }

  @Test
  public void whenStartSpecifiedAndNamedClusterHasRoom_shouldStartReturnsTrue() {
    domain.getSpec().setStartupControl(SPECIFIED_STARTUPCONTROL);
    domain.getSpec().setReplicas(3);
    addClusterStartup(new ClusterStartup().withClusterName(CLUSTER_NAME).withReplicas(3));

    assertThat(domain.getServer(SERVER1, CLUSTER_NAME).shouldStart(2), is(true));
  }

  @Test
  public void whenStartSpecifiedAndNeitherServerNorClusterSpecified_shouldStartReturnsFalse() {
    domain.getSpec().setStartupControl(SPECIFIED_STARTUPCONTROL);
    domain.getSpec().setReplicas(3);

    assertThat(domain.getServer(SERVER1, CLUSTER_NAME).shouldStart(2), is(false));
  }

  @Test
  public void whenClusterStartupIsNull_managedServerDesiredStateIsRunning() {
    domain.getSpec().setClusterStartup(null);

    ServerSpec spec = domain.getServer(SERVER1, CLUSTER_NAME);

    assertThat(spec.getDesiredState(), equalTo("RUNNING"));
  }

  @Test
  public void whenNotSpecified_managedServerDesiredStateIsRunning() {
    ServerSpec spec = domain.getServer(SERVER1, CLUSTER_NAME);

    assertThat(spec.getDesiredState(), equalTo("RUNNING"));
  }

  @Test
  public void whenSpecified_managedServerDesiredStateIsAsSpecified() {
    addServerStartup(new ServerStartup().withServerName(SERVER1).withDesiredState("STAND-BY"));

    ServerSpec spec = domain.getServer(SERVER1, CLUSTER_NAME);

    assertThat(spec.getDesiredState(), equalTo("STAND-BY"));
  }

  @Test
  public void whenOnlyAsStateSpecified_managedServerDesiredStateIsRunning() {
    addServerStartup(new ServerStartup().withServerName(AS_NAME).withDesiredState("ADMIN"));

    ServerSpec spec = domain.getServer(SERVER1, CLUSTER_NAME);

    assertThat(spec.getDesiredState(), equalTo("RUNNING"));
  }

  @Test
  public void whenClusterStateSpecified_managedServerDesiredStateIsAsSpecified() {
    addClusterStartup(new ClusterStartup().withClusterName(CLUSTER_NAME).withDesiredState("NEVER"));

    ServerSpec spec = domain.getServer(SERVER1, CLUSTER_NAME);

    assertThat(spec.getDesiredState(), equalTo("NEVER"));
  }

  private void addClusterStartup(ClusterStartup clusterStartup) {
    domain.getSpec().setClusterStartup(Collections.singletonList(clusterStartup));
  }

  @Test
  public void whenBothClusterAndServerStateSpecified_managedServerUsesServerState() {
    addServerStartup(new ServerStartup().withServerName(SERVER1).withDesiredState("STAND-BY"));
    addClusterStartup(new ClusterStartup().withClusterName(CLUSTER_NAME).withDesiredState("NEVER"));

    ServerSpec spec = domain.getServer(SERVER1, CLUSTER_NAME);

    assertThat(spec.getDesiredState(), equalTo("STAND-BY"));
  }

  @Test
  public void whenSpecifiedOnServer_managedServerHasEnvironmentVariables() {
    addServerStartup(
        new ServerStartup().withServerName(SERVER1).withEnv(Arrays.asList(createEnvironment())));

    ServerSpec spec = domain.getServer(SERVER1, CLUSTER_NAME);

    assertThat(spec.getEnvironmentVariables(), containsInAnyOrder(createEnvironment()));
  }

  @Test
  public void whenDesiredStateAdminAndSpecifiedOnServer_managedServerHasJavaOption() {
    addServerStartup(
        new ServerStartup()
            .withServerName(SERVER1)
            .withDesiredState("ADMIN")
            .withEnv(Arrays.asList(createEnvironment())));

    ServerSpec spec = domain.getServer(SERVER1, CLUSTER_NAME);

    assertThat(
        spec.getEnvironmentVariables(),
        hasItem(envVar("JAVA_OPTIONS", "-Dweblogic.management.startupMode=ADMIN")));
  }

  @SuppressWarnings("SameParameterValue")
  private V1EnvVar envVar(String name, String value) {
    return new V1EnvVar().name(name).value(value);
  }

  @Test
  public void whenSpecifiedOnCluster_managedServerHasEnvironmentVariables() {
    addClusterStartup(
        new ClusterStartup()
            .withClusterName(CLUSTER_NAME)
            .withEnv(Arrays.asList(createEnvironment())));

    ServerSpec spec = domain.getServer(SERVER1, CLUSTER_NAME);

    assertThat(spec.getEnvironmentVariables(), containsInAnyOrder(createEnvironment()));
  }

  @Test
  public void whenDesiredStateAdminAndSpecifiedOnCluster_managedServerHasEnvironmentVariables() {
    addClusterStartup(
        new ClusterStartup()
            .withDesiredState("ADMIN")
            .withClusterName(CLUSTER_NAME)
            .withEnv(Collections.singletonList(envVar("JAVA_OPTIONS", "value"))));

    ServerSpec spec = domain.getServer(SERVER1, CLUSTER_NAME);

    assertThat(
        spec.getEnvironmentVariables(),
        hasItem(envVar("JAVA_OPTIONS", "-Dweblogic.management.startupMode=ADMIN value")));
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
  public void whenNoReplicaCountSpecified_useDefaultValue() {
    assertThat(domain.getReplicaCount("nosuchcluster"), equalTo(Domain.DEFAULT_REPLICA_LIMIT));
  }

  @Test
  public void whenNoStartupForCluster_useDefaultReplicaCount() {
    DomainConfigurator.forDomain(domain).setDefaultReplicas(5);

    assertThat(domain.getReplicaCount("nosuchcluster"), equalTo(5));
  }

  @Test
  public void afterReplicaCountSetForCluster_canReadIt() {
    DomainConfigurator.forDomain(domain).configureCluster("cluster1").withReplicas(5);

    assertThat(domain.getReplicaCount("cluster1"), equalTo(5));
  }

  @Test
  public void afterReplicaCountSetForCluster_defaultIsUnchanged() {
    DomainConfigurator.forDomain(domain).configureCluster("cluster1").withReplicas(5);

    assertThat(domain.getReplicaCount("cluster2"), equalTo(1));
  }

  @Test
  public void whenStartupDefinedForClusterWithReplicas_useClusterReplicaCount() {
    domain.getSpec().setReplicas(5);
    domain
        .getSpec()
        .addClusterStartupItem(new ClusterStartup().withClusterName("cluster1").withReplicas(3));

    assertThat(domain.getReplicaCount("cluster1"), equalTo(3));
  }

  @Test
  public void whenStartupDefinedForClusterWithoutReplicas_useDomainReplicaCount() {
    domain.getSpec().setReplicas(5);
    domain.getSpec().addClusterStartupItem(new ClusterStartup().withClusterName("cluster1"));

    assertThat(domain.getReplicaCount("cluster1"), equalTo(5));
  }

  @Test
  public void whenNoServerStartupDefined_nodePortIsNull() {
    ServerSpec spec = domain.getServer(SERVER1, CLUSTER_NAME);

    assertThat(spec.getNodePort(), nullValue());
  }

  @Test
  public void whenServerStartupDefined_returnNodePort() {
    addServerStartup(new ServerStartup().withServerName(SERVER1).withNodePort(31));
    ServerSpec spec = domain.getServer(SERVER1, CLUSTER_NAME);

    assertThat(spec.getNodePort(), equalTo(31));
  }
}

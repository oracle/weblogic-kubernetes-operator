// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helpers;

import static oracle.kubernetes.operator.KubernetesConstants.*;
import static oracle.kubernetes.operator.StartupControlConstants.*;
import static oracle.kubernetes.operator.helpers.ClusteredServerConfig.*;
import static oracle.kubernetes.operator.helpers.DomainConfigBuilder.*;
import static oracle.kubernetes.operator.helpers.NonClusteredServerConfig.*;
import static oracle.kubernetes.operator.helpers.ServerConfig.*;
import static oracle.kubernetes.operator.utils.KubernetesArtifactUtils.*;
import static org.hamcrest.MatcherAssert.*;
import static org.hamcrest.Matchers.*;

import io.kubernetes.client.models.V1EnvVar;
import java.util.ArrayList;
import java.util.List;
import oracle.kubernetes.weblogic.domain.v1.ClusterStartup;
import oracle.kubernetes.weblogic.domain.v1.DomainSpec;
import oracle.kubernetes.weblogic.domain.v1.ServerStartup;
import org.junit.Test;

/** Tests DomainConfigBuilderV1 */
public class DomainConfigBuilderV1Test {

  private static final String CLUSTER1 = "cluster1";
  private static final String CLUSTER2 = "cluster2";
  private static final String SERVER1 = "server1";
  private static final String SERVER2 = "server2";
  private static final String SERVER3 = "server3";
  private static final String ADMIN_SERVER = "adminServer";

  private static final String IMAGE1 = "image1";
  private static final String IMAGE2 = "image2";
  private static final String IMAGE1_LATEST = "image1:latest";

  private static List<V1EnvVar> ENV1 =
      newEnvVarList().addElement(newEnvVar().name("name1").value("value1"));
  private static List<V1EnvVar> ENV2 =
      newEnvVarList().addElement(newEnvVar().name("name2").value("value2"));
  private static List<V1EnvVar> EMPTY_ENV = newEnvVarList();

  @Test
  public void
      updateDomainSpec_domainSpecPropertiesSet_clusterStartupPropertiesSet_updatesClusterStartup() {
    List<ClusterStartup> clusterStartups = createClusterStartups(CLUSTER1);
    ClusterStartup clusterStartup = clusterStartups.get(0);
    int clusterStartupReplicas = 3;
    clusterStartup.setReplicas(clusterStartupReplicas);

    int domainReplicas = 2;
    DomainSpec domainSpec =
        (new DomainSpec()).withReplicas(domainReplicas).withClusterStartup(clusterStartups);

    int clusterConfigReplicas = 1;
    ClusterConfig clusterConfig =
        (new ClusterConfig()).withClusterName(CLUSTER1).withReplicas(clusterConfigReplicas);

    newBuilder(domainSpec).updateDomainSpec(clusterConfig);

    assertThat(domainSpec.getReplicas(), equalTo(domainReplicas)); // verify it didn't change
    assertThat(clusterStartup.getReplicas(), equalTo(clusterConfigReplicas)); // verify it changed
  }

  @Test
  public void
      updateDomainSpec_domainSpecPropertiesSet_clusterStartupPropertiesNotSet_updatesDomainSpec() {
    List<ClusterStartup> clusterStartups = createClusterStartups(CLUSTER1);
    ClusterStartup clusterStartup = clusterStartups.get(0);

    int domainReplicas = 2;
    DomainSpec domainSpec =
        (new DomainSpec()).withReplicas(domainReplicas).withClusterStartup(clusterStartups);

    int clusterConfigReplicas = 1;
    ClusterConfig clusterConfig =
        (new ClusterConfig()).withClusterName(CLUSTER1).withReplicas(clusterConfigReplicas);

    newBuilder(domainSpec).updateDomainSpec(clusterConfig);

    assertThat(domainSpec.getReplicas(), equalTo(clusterConfigReplicas)); // verify it changed
    assertThat(clusterStartup.getReplicas(), nullValue()); // verify it didn't change
  }

  @Test
  public void updateDomainSpec_domainSpecPropertiesSet_noClusterStartup_updatesDomainSpec() {
    int domainReplicas = 2;
    DomainSpec domainSpec = (new DomainSpec()).withReplicas(domainReplicas);

    int clusterConfigReplicas = 1;
    ClusterConfig clusterConfig =
        (new ClusterConfig()).withClusterName(CLUSTER1).withReplicas(clusterConfigReplicas);

    newBuilder(domainSpec).updateDomainSpec(clusterConfig);

    assertThat(domainSpec.getReplicas(), equalTo(clusterConfigReplicas));
  }

  @Test
  public void
      updateDomainSpec_domainSpecPropertiesNotSet_clusterStartupPropertiesSet_updatesClusterStartup() {
    List<ClusterStartup> clusterStartups = createClusterStartups(CLUSTER1);
    ClusterStartup clusterStartup = clusterStartups.get(0);
    int clusterStartupReplicas = 3;
    clusterStartup.setReplicas(clusterStartupReplicas);

    DomainSpec domainSpec = (new DomainSpec()).withClusterStartup(clusterStartups);

    int clusterConfigReplicas = 1;
    ClusterConfig clusterConfig =
        (new ClusterConfig()).withClusterName(CLUSTER1).withReplicas(clusterConfigReplicas);

    newBuilder(domainSpec).updateDomainSpec(clusterConfig);

    assertThat(domainSpec.getReplicas(), nullValue()); // verify it didn't change
    assertThat(clusterStartup.getReplicas(), equalTo(clusterConfigReplicas)); // verify it changed
  }

  @Test
  public void
      updateDomainSpec_domainSpecPropertiesNotSet_clusterStartupPropertiesNotSet_updatesDomainSpec() {
    List<ClusterStartup> clusterStartups = createClusterStartups(CLUSTER1);
    ClusterStartup clusterStartup = clusterStartups.get(0);

    DomainSpec domainSpec = (new DomainSpec()).withClusterStartup(clusterStartups);

    int clusterConfigReplicas = 1;
    ClusterConfig clusterConfig =
        (new ClusterConfig()).withClusterName(CLUSTER1).withReplicas(clusterConfigReplicas);

    newBuilder(domainSpec).updateDomainSpec(clusterConfig);

    assertThat(domainSpec.getReplicas(), equalTo(clusterConfigReplicas)); // verify it changed
    assertThat(clusterStartup.getReplicas(), nullValue()); // verify it didn't change
  }

  @Test
  public void updateDomainSpec_domainSpecPropertiesNotSet_noClusterStartup_updatesDomainSpec() {
    DomainSpec domainSpec = new DomainSpec();

    int clusterConfigReplicas = 1;
    ClusterConfig clusterConfig =
        (new ClusterConfig()).withClusterName(CLUSTER1).withReplicas(clusterConfigReplicas);

    newBuilder(domainSpec).updateDomainSpec(clusterConfig);

    assertThat(domainSpec.getReplicas(), equalTo(clusterConfigReplicas));
  }

  @Test
  public void
      getEffectiveNonClusteredServerConfig_domainSpecPropertiesSet_serverStartupPropertiesSet_returnsCorrectConfig() {
    List<ServerStartup> serverStartups = createServerStartups(SERVER1);
    ServerStartup serverStartup = serverStartups.get(0);
    serverStartup.withNodePort(1).withDesiredState(STARTED_SERVER_STATE_ADMIN).withEnv(ENV1);

    DomainSpec domainSpec =
        (new DomainSpec())
            .withStartupControl(SPECIFIED_STARTUPCONTROL)
            .withAsName(ADMIN_SERVER)
            .withImage(IMAGE2)
            .withImagePullPolicy(ALWAYS_IMAGEPULLPOLICY)
            .withServerStartup(serverStartups);

    // we want the default properties plus whatever we customized:
    NonClusteredServerConfig want = new NonClusteredServerConfig();
    newBuilder().initServerConfigFromDefaults(want);
    want.withServerName(SERVER1)
        .withNodePort(serverStartup.getNodePort())
        .withStartedServerState(serverStartup.getDesiredState())
        .withEnv(serverStartup.getEnv())
        .withImage(domainSpec.getImage())
        .withImagePullPolicy(domainSpec.getImagePullPolicy())
        .withNonClusteredServerStartPolicy(NON_CLUSTERED_SERVER_START_POLICY_ALWAYS);

    NonClusteredServerConfig actual =
        newBuilder(domainSpec).getEffectiveNonClusteredServerConfig(SERVER1);

    assertThat(actual, equalTo(want));
  }

  @Test
  public void
      getEffectiveNonClusteredServerConfig_domainSpecPropertiesSet_noServerStartup_returnsCorrectConfig() {
    DomainSpec domainSpec =
        (new DomainSpec())
            .withStartupControl(SPECIFIED_STARTUPCONTROL)
            .withAsName(ADMIN_SERVER)
            .withImage(IMAGE2)
            .withImagePullPolicy(ALWAYS_IMAGEPULLPOLICY);

    // we want the default properties plus whatever we customized:
    NonClusteredServerConfig want = new NonClusteredServerConfig();
    newBuilder().initServerConfigFromDefaults(want);
    want.withServerName(SERVER1)
        .withImage(domainSpec.getImage())
        .withImagePullPolicy(domainSpec.getImagePullPolicy())
        .withNonClusteredServerStartPolicy(NON_CLUSTERED_SERVER_START_POLICY_NEVER);

    NonClusteredServerConfig actual =
        newBuilder(domainSpec).getEffectiveNonClusteredServerConfig(SERVER1);

    assertThat(actual, equalTo(want));
  }

  @Test
  public void
      getEffectiveNonClusteredServerConfig_domainSpecPropertiesNotSet_noServerStartup_returnsCorrectConfig() {
    DomainSpec domainSpec =
        (new DomainSpec()).withStartupControl(ADMIN_STARTUPCONTROL).withAsName(SERVER1);

    // we want the default properties plus whatever we customized:
    NonClusteredServerConfig want = new NonClusteredServerConfig();
    newBuilder().initServerConfigFromDefaults(want);
    want.withServerName(SERVER1)
        .withImagePullPolicy(IFNOTPRESENT_IMAGEPULLPOLICY)
        .withNonClusteredServerStartPolicy(NON_CLUSTERED_SERVER_START_POLICY_ALWAYS);

    NonClusteredServerConfig actual =
        newBuilder(domainSpec).getEffectiveNonClusteredServerConfig(SERVER1);

    assertThat(actual, equalTo(want));
  }

  @Test
  public void
      getEffectiveClusteredServerConfig_domainSpecPropertiesSet_clusterStartupPropertiesSet_serverStartupPropertiesSet_returnsCorrectConfig() {
    List<ServerStartup> serverStartups = createServerStartups(SERVER1);
    ServerStartup serverStartup = serverStartups.get(0);
    serverStartup.withNodePort(1).withDesiredState(STARTED_SERVER_STATE_ADMIN).withEnv(ENV1);

    List<ClusterStartup> clusterStartups = createClusterStartups(CLUSTER1);
    ClusterStartup clusterStartup = clusterStartups.get(0);
    clusterStartup.withDesiredState(STARTED_SERVER_STATE_RUNNING).withEnv(ENV2);

    DomainSpec domainSpec =
        (new DomainSpec())
            .withStartupControl(SPECIFIED_STARTUPCONTROL)
            .withAsName(ADMIN_SERVER)
            .withImage(IMAGE2)
            .withImagePullPolicy(ALWAYS_IMAGEPULLPOLICY)
            .withClusterStartup(clusterStartups)
            .withServerStartup(serverStartups);

    // we want the default properties plus whatever we customized:
    ClusteredServerConfig want = new ClusteredServerConfig();
    newBuilder().initServerConfigFromDefaults(want);
    want.withClusterName(CLUSTER1)
        .withServerName(SERVER1)
        .withNodePort(serverStartup.getNodePort())
        .withStartedServerState(serverStartup.getDesiredState())
        .withEnv(serverStartup.getEnv())
        .withImage(domainSpec.getImage())
        .withImagePullPolicy(domainSpec.getImagePullPolicy())
        .withClusteredServerStartPolicy(CLUSTERED_SERVER_START_POLICY_ALWAYS);

    ClusteredServerConfig actual =
        newBuilder(domainSpec).getEffectiveClusteredServerConfig(CLUSTER1, SERVER1);

    assertThat(actual, equalTo(want));
  }

  @Test
  public void
      getEffectiveClusteredServerConfig_domainSpecPropertiesSet_noClusterStartup_serverStartupPropertiesSet_returnsCorrectConfig() {
    List<ServerStartup> serverStartups = createServerStartups(SERVER1);
    ServerStartup serverStartup = serverStartups.get(0);
    serverStartup.withNodePort(1).withDesiredState(STARTED_SERVER_STATE_ADMIN).withEnv(ENV1);

    DomainSpec domainSpec =
        (new DomainSpec())
            .withStartupControl(SPECIFIED_STARTUPCONTROL)
            .withAsName(ADMIN_SERVER)
            .withImage(IMAGE2)
            .withImagePullPolicy(ALWAYS_IMAGEPULLPOLICY)
            .withServerStartup(serverStartups);

    // we want the default properties plus whatever we customized:
    ClusteredServerConfig want = new ClusteredServerConfig();
    newBuilder().initServerConfigFromDefaults(want);
    want.withClusterName(CLUSTER1)
        .withServerName(SERVER1)
        .withNodePort(serverStartup.getNodePort())
        .withStartedServerState(serverStartup.getDesiredState())
        .withEnv(serverStartup.getEnv())
        .withImage(domainSpec.getImage())
        .withImagePullPolicy(domainSpec.getImagePullPolicy())
        .withClusteredServerStartPolicy(CLUSTERED_SERVER_START_POLICY_ALWAYS);

    ClusteredServerConfig actual =
        newBuilder(domainSpec).getEffectiveClusteredServerConfig(CLUSTER1, SERVER1);

    assertThat(actual, equalTo(want));
  }

  @Test
  public void
      getEffectiveClusteredServerConfig_domainSpecPropertiesSet_clusterStartupPropertiesSet_noServerStartup_returnsCorrectConfig() {
    List<ClusterStartup> clusterStartups = createClusterStartups(CLUSTER1);
    ClusterStartup clusterStartup = clusterStartups.get(0);
    clusterStartup.withDesiredState(STARTED_SERVER_STATE_RUNNING).withEnv(ENV2);

    DomainSpec domainSpec =
        (new DomainSpec())
            .withStartupControl(SPECIFIED_STARTUPCONTROL)
            .withAsName(ADMIN_SERVER)
            .withImage(IMAGE2)
            .withImagePullPolicy(ALWAYS_IMAGEPULLPOLICY)
            .withClusterStartup(clusterStartups);

    // we want the default properties plus whatever we customized:
    ClusteredServerConfig want = new ClusteredServerConfig();
    newBuilder().initServerConfigFromDefaults(want);
    want.withClusterName(CLUSTER1)
        .withServerName(SERVER1)
        .withEnv(clusterStartup.getEnv())
        .withImage(domainSpec.getImage())
        .withImagePullPolicy(domainSpec.getImagePullPolicy())
        .withClusteredServerStartPolicy(CLUSTERED_SERVER_START_POLICY_IF_NEEDED)
        .withStartedServerState(clusterStartup.getDesiredState());

    ClusteredServerConfig actual =
        newBuilder(domainSpec).getEffectiveClusteredServerConfig(CLUSTER1, SERVER1);

    assertThat(actual, equalTo(want));
  }

  @Test
  public void
      getEffectiveClusteredServerConfig_domainSpecPropertiesSet_noClusterStartup_noServerStartup_returnsCorrectConfig() {
    DomainSpec domainSpec =
        (new DomainSpec())
            .withStartupControl(SPECIFIED_STARTUPCONTROL)
            .withAsName(ADMIN_SERVER)
            .withImage(IMAGE2)
            .withImagePullPolicy(ALWAYS_IMAGEPULLPOLICY);

    // we want the default properties plus whatever we customized:
    ClusteredServerConfig want = new ClusteredServerConfig();
    newBuilder().initServerConfigFromDefaults(want);
    want.withClusterName(CLUSTER1)
        .withServerName(SERVER1)
        .withImage(domainSpec.getImage())
        .withImagePullPolicy(domainSpec.getImagePullPolicy())
        .withClusteredServerStartPolicy(CLUSTERED_SERVER_START_POLICY_NEVER);

    ClusteredServerConfig actual =
        newBuilder(domainSpec).getEffectiveClusteredServerConfig(CLUSTER1, SERVER1);

    assertThat(actual, equalTo(want));
  }

  @Test
  public void
      getEffectiveClusteredServerConfig_domainSpecPropertiesNotSet_noClusterStartup_noServerStartup_returnsCorrectConfig() {
    DomainSpec domainSpec =
        (new DomainSpec()).withStartupControl(ALL_STARTUPCONTROL).withAsName(ADMIN_SERVER);

    // we want the default properties plus whatever we customized:
    ClusteredServerConfig want = new ClusteredServerConfig();
    newBuilder().initServerConfigFromDefaults(want);
    want.withClusterName(CLUSTER1)
        .withServerName(SERVER1)
        .withClusteredServerStartPolicy(CLUSTERED_SERVER_START_POLICY_ALWAYS)
        .withImagePullPolicy(IFNOTPRESENT_IMAGEPULLPOLICY);

    ClusteredServerConfig actual =
        newBuilder(domainSpec).getEffectiveClusteredServerConfig(CLUSTER1, SERVER1);

    assertThat(actual, equalTo(want));
  }

  @Test
  public void
      getEffectiveClusterConfig_domainSpecPropertiesSet_clusterStartupPropertiesSet_returnsClusterStartupProperties() {
    List<ClusterStartup> clusterStartups = createClusterStartups(CLUSTER1);
    int replicasWant = 1;
    ClusterStartup clusterStartup = clusterStartups.get(0);
    clusterStartup.setReplicas(replicasWant);

    DomainSpec domainSpec = (new DomainSpec()).withReplicas(2).withClusterStartup(clusterStartups);

    ClusterConfig want =
        (new ClusterConfig())
            .withClusterName(CLUSTER1)
            .withReplicas(replicasWant)
            .withMinReplicas(replicasWant)
            .withMaxReplicas(replicasWant);

    ClusterConfig actual = newBuilder(domainSpec).getEffectiveClusterConfig(CLUSTER1);

    assertThat(actual, equalTo(want));
  }

  @Test
  public void
      getEffectiveClusterConfig_domainSpecPropertiesSet_noClusterStartup_returnsDomainSpecProperties() {
    int replicasWant = 1;
    DomainSpec domainSpec = (new DomainSpec()).withReplicas(replicasWant);

    ClusterConfig want =
        (new ClusterConfig())
            .withClusterName(CLUSTER1)
            .withReplicas(replicasWant)
            .withMinReplicas(replicasWant)
            .withMaxReplicas(replicasWant);

    ClusterConfig actual = newBuilder(domainSpec).getEffectiveClusterConfig(CLUSTER1);

    assertThat(actual, equalTo(want));
  }

  @Test
  public void
      getEffectiveClusterConfig_domainSpecPropertiesNotSet_noClusterStartup_returnsDefaults() {
    DomainSpec domainSpec = new DomainSpec();

    ClusterConfig want =
        (new ClusterConfig())
            .withClusterName(CLUSTER1)
            .withReplicas(0)
            .withMinReplicas(0)
            .withMaxReplicas(0);

    ClusterConfig actual = newBuilder(domainSpec).getEffectiveClusterConfig(CLUSTER1);

    assertThat(actual, equalTo(want));
  }

  @Test
  public void initServerConfigFromServerStartup_noServerStartup_doesNotSetProperties() {
    ServerConfig actual =
        (new ServerConfig())
            .withNodePort(1)
            .withStartedServerState(STARTED_SERVER_STATE_ADMIN)
            .withEnv(ENV1);

    ServerStartup serverStartup = null;

    ServerConfig want =
        (new ServerConfig())
            .withNodePort(actual.getNodePort())
            .withStartedServerState(actual.getStartedServerState())
            .withEnv(actual.getEnv());

    newBuilder().initServerConfigFromServerStartup(actual, serverStartup);
    assertThat(actual, equalTo(want));
  }

  @Test
  public void
      initServerConfigFromServerStartup_serverStartupPropertiesNotSet_doesNotSetProperties() {
    ServerConfig actual =
        (new ServerConfig())
            .withNodePort(1)
            .withStartedServerState(STARTED_SERVER_STATE_ADMIN)
            .withEnv(ENV1);

    ServerStartup serverStartup = new ServerStartup(); // defaults env to an empty list

    ServerConfig want =
        (new ServerConfig())
            .withNodePort(actual.getNodePort())
            .withStartedServerState(actual.getStartedServerState())
            .withEnv(EMPTY_ENV); // since ServerStartup defaulted this to an empty list

    newBuilder().initServerConfigFromServerStartup(actual, serverStartup);

    assertThat(actual, equalTo(want));
  }

  @Test
  public void initServerConfigFromServerStartup_serverStartupPropertiesSet_copiesProperties() {
    ServerConfig actual =
        (new ServerConfig())
            .withNodePort(2)
            .withStartedServerState(STARTED_SERVER_STATE_RUNNING)
            .withEnv(ENV2);

    ServerStartup serverStartup =
        (new ServerStartup())
            .withNodePort(1)
            .withDesiredState(STARTED_SERVER_STATE_ADMIN)
            .withEnv(ENV1);

    ServerConfig want =
        (new ServerConfig())
            .withNodePort(serverStartup.getNodePort())
            .withStartedServerState(serverStartup.getDesiredState())
            .withEnv(serverStartup.getEnv());

    newBuilder().initServerConfigFromServerStartup(actual, serverStartup);

    assertThat(actual, equalTo(want));
  }

  @Test
  public void initClusteredServerFromClusterStartup_noClusterStartup_doesNotCopyProperties() {
    ClusteredServerConfig actual =
        (new ClusteredServerConfig())
            .withStartedServerState(STARTED_SERVER_STATE_ADMIN)
            .withEnv(ENV1);

    ClusterStartup clusterStartup = null;

    ClusteredServerConfig want =
        (new ClusteredServerConfig())
            .withStartedServerState(actual.getStartedServerState())
            .withEnv(actual.getEnv());

    newBuilder().initClusteredServerConfigFromClusterStartup(actual, clusterStartup);

    assertThat(actual, equalTo(want));
  }

  @Test
  public void
      initClusteredServerFromClusterStartup_clusterStartupPropertiesNotSet_doesNotSetProperties() {
    ClusteredServerConfig actual =
        (new ClusteredServerConfig())
            .withStartedServerState(STARTED_SERVER_STATE_ADMIN)
            .withEnv(ENV1);

    ClusterStartup clusterStartup = new ClusterStartup(); // defaults env to an empty list

    ClusteredServerConfig want =
        (new ClusteredServerConfig())
            .withStartedServerState(actual.getStartedServerState())
            .withEnv(EMPTY_ENV); // since ClusterStartup defaulted this to an empty list

    newBuilder().initClusteredServerConfigFromClusterStartup(actual, clusterStartup);

    assertThat(actual, equalTo(want));
  }

  @Test
  public void initClusteredServerFromClusterStartup_clusterStartupPropertiesSet_copiesProperties() {
    ClusteredServerConfig actual =
        (new ClusteredServerConfig())
            .withStartedServerState(STARTED_SERVER_STATE_RUNNING)
            .withEnv(ENV2);

    ClusterStartup clusterStartup =
        (new ClusterStartup()).withDesiredState(STARTED_SERVER_STATE_ADMIN).withEnv(ENV1);

    ClusteredServerConfig want =
        (new ClusteredServerConfig())
            .withStartedServerState(clusterStartup.getDesiredState())
            .withEnv(clusterStartup.getEnv());

    newBuilder().initClusteredServerConfigFromClusterStartup(actual, clusterStartup);

    assertThat(actual, equalTo(want));
  }

  @Test
  public void
      initServerConfigFromDomainSpec_domainSpecPropertiesNotSet_doesNotSetPropertiesAndDefaultsImagePullPolicy() {
    ServerConfig actual = (new ServerConfig()).withImage(IMAGE1);

    DomainSpec domainSpec = new DomainSpec();

    // the image pull policy should get set to IfNotPresent because the policy isn't set
    // and the image doesn't end with :latest
    ServerConfig want =
        (new ServerConfig())
            .withImage(actual.getImage())
            .withImagePullPolicy(IFNOTPRESENT_IMAGEPULLPOLICY);

    newBuilder(domainSpec).initServerConfigFromDomainSpec(actual);

    assertThat(actual, equalTo(want));
  }

  @Test
  public void
      initServerConfigFromDomainSpec_domainSpecPropertiesSetExceptForImagePullPolicy_copiesPropertiesAndDefaultsImagePullPolicy() {
    ServerConfig actual = (new ServerConfig()).withImage(IMAGE2); // should get overridden

    DomainSpec domainSpec = (new DomainSpec()).withImage(IMAGE1_LATEST);

    // the image pull policy should get set to Always because the policy isn't set and the image
    // ends with :latest
    ServerConfig want =
        (new ServerConfig())
            .withImage(domainSpec.getImage())
            .withImagePullPolicy(ALWAYS_IMAGEPULLPOLICY);

    newBuilder(domainSpec).initServerConfigFromDomainSpec(actual);

    assertThat(actual, equalTo(want));
  }

  @Test
  public void initServerConfigFromDomainSpec_domainSpecPropertiesSet_copiesProperties() {
    ServerConfig actual =
        (new ServerConfig()).withImage(IMAGE2).withImagePullPolicy(ALWAYS_IMAGEPULLPOLICY);

    DomainSpec domainSpec =
        (new DomainSpec()).withImage(IMAGE1_LATEST).withImagePullPolicy(NEVER_IMAGEPULLPOLICY);

    ServerConfig want =
        (new ServerConfig())
            .withImage(domainSpec.getImage())
            .withImagePullPolicy(domainSpec.getImagePullPolicy());

    newBuilder(domainSpec).initServerConfigFromDomainSpec(actual);

    assertThat(actual, equalTo(want));
  }

  @Test
  public void initServerConfigFromDefaults_setsPropertiesToDefaultValues() {
    ServerConfig actual = new ServerConfig();

    ServerConfig want =
        (new ServerConfig())
            .withNodePort(0)
            .withStartedServerState(STARTED_SERVER_STATE_RUNNING)
            .withEnv(EMPTY_ENV)
            .withImage("store/oracle/weblogic:12.2.1.3")
            .withShutdownPolicy(SHUTDOWN_POLICY_FORCED_SHUTDOWN)
            .withGracefulShutdownTimeout(0)
            .withGracefulShutdownIgnoreSessions(false)
            .withGracefulShutdownWaitForSessions(false);

    newBuilder().initServerConfigFromDefaults(actual);

    assertThat(actual, equalTo(want));
  }

  @Test
  public void initClusterConfigFromClusterStartup_noClusterStartup_doesNotSetProperies() {
    ClusterConfig actual = new ClusterConfig().withReplicas(1);

    ClusterStartup clusterStartup = null;

    ClusterConfig want = (new ClusterConfig()).withReplicas(actual.getReplicas());

    newBuilder().initClusterConfigFromClusterStartup(actual, clusterStartup);

    assertThat(actual, equalTo(want));
  }

  @Test
  public void
      initClusterConfigFromClusterStartup_clusterStartupPropertiesNotSet_doesNotSetProperies() {
    ClusterConfig actual = new ClusterConfig().withReplicas(1);

    ClusterStartup clusterStartup = new ClusterStartup();

    ClusterConfig want = (new ClusterConfig()).withReplicas(actual.getReplicas());

    newBuilder().initClusterConfigFromClusterStartup(actual, clusterStartup);

    assertThat(actual, equalTo(want));
  }

  @Test
  public void initClusterConfigFromClusterStartup_clusterStartupPropertiesSet_copiesProperies() {
    ClusterConfig actual = new ClusterConfig().withReplicas(2);

    ClusterStartup clusterStartup = (new ClusterStartup()).withReplicas(1);

    ClusterConfig want = (new ClusterConfig()).withReplicas(clusterStartup.getReplicas());

    newBuilder().initClusterConfigFromClusterStartup(actual, clusterStartup);

    assertThat(actual, equalTo(want));
  }

  @Test
  public void initClusterConfigFromDomainSpec_domainSpecPropertiesNotSet_doesNotSetProperties() {
    ClusterConfig actual = new ClusterConfig().withReplicas(1);

    DomainSpec domainSpec = new DomainSpec();

    ClusterConfig want = (new ClusterConfig()).withReplicas(actual.getReplicas());

    newBuilder(domainSpec).initClusterConfigFromDomainSpec(actual);

    assertThat(actual, equalTo(want));
  }

  @Test
  public void initClusterConfigFromDomainSpec_domainSpecPropertiesSet_copiesProperties() {
    ClusterConfig actual = new ClusterConfig().withReplicas(2);

    DomainSpec domainSpec = (new DomainSpec()).withReplicas(1);

    ClusterConfig want = (new ClusterConfig()).withReplicas(domainSpec.getReplicas());

    newBuilder(domainSpec).initClusterConfigFromDomainSpec(actual);

    assertThat(actual, equalTo(want));
  }

  @Test
  public void initClusterConfigFromDefaults_setsPropertiesToDefaults() {
    ClusterConfig actual = new ClusterConfig();

    ClusterConfig want = (new ClusterConfig()).withReplicas(DEFAULT_REPLICAS);

    newBuilder().initClusterConfigFromDefaults(actual);

    assertThat(actual, equalTo(want));
  }

  @Test
  public void getNonClusteredServerStartPolicy_startupControlNone_returnsCorrectPolicy() {
    String never = NON_CLUSTERED_SERVER_START_POLICY_NEVER;
    getNonClusteredServerStartPolicy_returnsCorrectPolicy(
        NONE_STARTUPCONTROL,
        never, // false, false
        never, // false, true
        never, // true,  false
        never); // true,  true
  }

  @Test
  public void getNonClusteredServerStartPolicy_startupControlAll_returnsCorrectPolicy() {
    String always = NON_CLUSTERED_SERVER_START_POLICY_ALWAYS;
    getNonClusteredServerStartPolicy_returnsCorrectPolicy(
        ALL_STARTUPCONTROL,
        always, // false, false
        always, // false, true
        always, // true,  false
        always); // true,  true
  }

  @Test
  public void getNonClusteredServerStartPolicy_startupControlAdmin_returnsCorrectPolicy() {
    String never = NON_CLUSTERED_SERVER_START_POLICY_NEVER;
    String always = NON_CLUSTERED_SERVER_START_POLICY_ALWAYS;
    getNonClusteredServerStartPolicy_returnsCorrectPolicy(
        ADMIN_STARTUPCONTROL,
        never, // false, false
        never, // false, true
        always, // true,  false
        always); // true,  true
  }

  @Test
  public void getNonClusteredServerStartPolicy_startupControlSpecified_returnsCorrectPolicy() {
    String never = NON_CLUSTERED_SERVER_START_POLICY_NEVER;
    String always = NON_CLUSTERED_SERVER_START_POLICY_ALWAYS;
    getNonClusteredServerStartPolicy_returnsCorrectPolicy(
        SPECIFIED_STARTUPCONTROL,
        never, // false, false
        always, // false, true
        always, // true,  false
        always); // true,  true
  }

  @Test
  public void getNonClusteredServerStartPolicy_startupControlAuto_returnsCorrectPolicy() {
    String never = NON_CLUSTERED_SERVER_START_POLICY_NEVER;
    String always = NON_CLUSTERED_SERVER_START_POLICY_ALWAYS;
    getNonClusteredServerStartPolicy_returnsCorrectPolicy(
        AUTO_STARTUPCONTROL,
        never, // false, false
        always, // false, true
        always, // true,  false
        always); // true,  true
  }

  @Test(expected = AssertionError.class)
  public void getNonClusteredServerStartPolicy_startupControlUnknown_throwsException() {
    newBuilder().getNonClusteredServerStartPolicy("unknown", true, true);
  }

  @Test
  public void getClusteredServerStartPolicy_startupControlNone_returnsCorrectPolicy() {
    String never = CLUSTERED_SERVER_START_POLICY_NEVER;
    getClusteredServerStartPolicy_returnsCorrectPolicy(
        NONE_STARTUPCONTROL,
        never, // false, false, false
        never, // false, false, true
        never, // false, true, false
        never, // false, true, true
        never, // true, false, false
        never, // true, false, true
        never, // true, true, false
        never); // true, true, true
  }

  @Test
  public void getClusteredServerStartPolicy_startupControlAll_returnsCorrectPolicy() {
    String always = CLUSTERED_SERVER_START_POLICY_ALWAYS;
    getClusteredServerStartPolicy_returnsCorrectPolicy(
        ALL_STARTUPCONTROL,
        always, // false, false, false
        always, // false, false, true
        always, // false, true,  false
        always, // false, true,  true
        always, // true,  false, false
        always, // true,  false, true
        always, // true,  true,  false
        always); // true,  true,  true
  }

  @Test
  public void getClusteredServerStartPolicy_startupControlAdmin_returnsCorrectPolicy() {
    String never = CLUSTERED_SERVER_START_POLICY_NEVER;
    String always = CLUSTERED_SERVER_START_POLICY_ALWAYS;
    getClusteredServerStartPolicy_returnsCorrectPolicy(
        ADMIN_STARTUPCONTROL,
        never, // false, false, false
        never, // false, false, true
        never, // false, true,  false
        never, // false, true,  true
        always, // true,  false, false
        always, // true,  false, true
        always, // true,  true,  false
        always); // true,  true,  true
  }

  @Test
  public void getClusteredServerStartPolicy_startupControlSpecified_returnsCorrectPolicy() {
    String never = CLUSTERED_SERVER_START_POLICY_NEVER;
    String always = CLUSTERED_SERVER_START_POLICY_ALWAYS;
    String ifNeeded = CLUSTERED_SERVER_START_POLICY_IF_NEEDED;
    getClusteredServerStartPolicy_returnsCorrectPolicy(
        SPECIFIED_STARTUPCONTROL,
        never, // false, false, false
        always, // false, false, true
        ifNeeded, // false, true,  false
        always, // false, true,  true
        always, // true,  false, false
        always, // true,  false, true
        always, // true,  true,  false
        always); // true,  true,  true
  }

  @Test
  public void getClusteredServerStartPolicy_startupControlAuto_returnsCorrectPolicy() {
    String always = CLUSTERED_SERVER_START_POLICY_ALWAYS;
    String ifNeeded = CLUSTERED_SERVER_START_POLICY_IF_NEEDED;
    getClusteredServerStartPolicy_returnsCorrectPolicy(
        AUTO_STARTUPCONTROL,
        ifNeeded, // false, false, false
        always, // false, false, true
        ifNeeded, // false, true,  false
        always, // false, true,  true
        always, // true,  false, false
        always, // true,  false, true
        always, // true,  true,  false
        always); // true,  true,  true
  }

  @Test(expected = AssertionError.class)
  public void getClusteredServerStartPolicy_startupControlUnknown_throwsException() {
    newBuilder().getClusteredServerStartPolicy("unknown", false, false, false);
  }

  @Test
  public void isAdminServer_sameName_returnsTrue() {
    assertThat(newBuilder().isAdminServer("SERVER1", "SERVER1"), equalTo(true));
  }

  @Test
  public void isAdminServer_differentNames_returnsFalse() {
    assertThat(newBuilder().isAdminServer("SERVER1", "SERVER2"), equalTo(false));
  }

  @Test
  public void isAdminServer_serverNameNotNull_adminServerNameNull_returnsFalse() {
    assertThat(newBuilder().isAdminServer("SERVER1", null), equalTo(false));
  }

  @Test
  public void isAdminServer_serverNameNull_adminServerNameNotNull_returnsFalse() {
    assertThat(newBuilder().isAdminServer(null, "SERVER1"), equalTo(false));
  }

  @Test
  public void getClusterStartup_nullClusterStartups_returnsNull() {
    String CLUSTER2 = "CLUSTER2";
    DomainSpec domainSpec = (new DomainSpec()).withClusterStartup(null);
    ClusterStartup actual = newBuilder(domainSpec).getClusterStartup(CLUSTER2);
    assertThat(actual, nullValue());
  }

  @Test
  public void getClusterStartup_doesNotHaveClusterName_returnsNull() {
    String CLUSTER1 = "CLUSTER1";
    String CLUSTER2 = "CLUSTER2";

    DomainSpec domainSpec = (new DomainSpec()).withClusterStartup(createClusterStartups(CLUSTER1));

    ClusterStartup actual = newBuilder(domainSpec).getClusterStartup(CLUSTER2);

    assertThat(actual, nullValue());
  }

  @Test
  public void getClusterStartup_hasClusterName_returnsClusterStartup() {
    String CLUSTER1 = "CLUSTER1";
    String CLUSTER2 = "CLUSTER2";

    DomainSpec domainSpec =
        (new DomainSpec()).withClusterStartup(createClusterStartups(CLUSTER1, CLUSTER2));

    ClusterStartup actual = newBuilder(domainSpec).getClusterStartup(CLUSTER2);

    ClusterStartup want = domainSpec.getClusterStartup().get(1);

    assertThat(actual, equalTo(want));
  }

  @Test
  public void getServerStartup_nullServerStartups_returnsNull() {
    String SERVER2 = "SERVER2";

    DomainSpec domainSpec = (new DomainSpec()).withServerStartup(null);

    ServerStartup actual = newBuilder(domainSpec).getServerStartup(SERVER2);

    assertThat(actual, nullValue());
  }

  @Test
  public void getServerStartup_doesNotHaveServerName_returnsNull() {
    String SERVER1 = "SERVER1";
    String SERVER2 = "SERVER2";

    DomainSpec domainSpec = (new DomainSpec()).withServerStartup(createServerStartups(SERVER1));

    ServerStartup actual = newBuilder(domainSpec).getServerStartup(SERVER2);

    assertThat(actual, nullValue());
  }

  @Test
  public void getServerStartup_hasServerName_returnsServerStartup() {
    String SERVER1 = "SERVER1";
    String SERVER2 = "SERVER2";

    DomainSpec domainSpec =
        (new DomainSpec()).withServerStartup(createServerStartups(SERVER1, SERVER2));

    ServerStartup actual = newBuilder(domainSpec).getServerStartup(SERVER2);

    ServerStartup want = domainSpec.getServerStartup().get(1);

    assertThat(actual, equalTo(want));
  }

  private void getNonClusteredServerStartPolicy_returnsCorrectPolicy(
      String startupControl,
      String notAdminServer_noServerStartup_policy,
      String notAdminServer_hasServerStartup_policy,
      String isAdminServer_noServerStartup_policy,
      String isAdminServer_hasServerStartup_policy) {
    assertThat(
        newBuilder().getNonClusteredServerStartPolicy(startupControl, false, false),
        equalTo(notAdminServer_noServerStartup_policy));
    assertThat(
        newBuilder().getNonClusteredServerStartPolicy(startupControl, false, true),
        equalTo(notAdminServer_hasServerStartup_policy));
    assertThat(
        newBuilder().getNonClusteredServerStartPolicy(startupControl, true, false),
        equalTo(isAdminServer_noServerStartup_policy));
    assertThat(
        newBuilder().getNonClusteredServerStartPolicy(startupControl, true, true),
        equalTo(isAdminServer_hasServerStartup_policy));
  }

  private void getClusteredServerStartPolicy_returnsCorrectPolicy(
      String startupControl,
      String notAdminServer_noClusterStartup_noServerStartup_policy,
      String notAdminServer_noClusterStartup_hasServerStartup_policy,
      String notAdminServer_hasClusterStartup_noServerStartup_policy,
      String notAdminServer_hasClusterStartup_hasServerStartup_policy,
      String isAdminServer_noClusterStartup_noServerStartup_policy,
      String isAdminServer_noClusterStartup_hasServerStartup_policy,
      String isAdminServer_hasClusterStartup_noServerStartup_policy,
      String isAdminServer_hasClusterStartup_hasServerStartup_policy) {
    assertThat(
        newBuilder().getClusteredServerStartPolicy(startupControl, false, false, false),
        equalTo(notAdminServer_noClusterStartup_noServerStartup_policy));
    assertThat(
        newBuilder().getClusteredServerStartPolicy(startupControl, false, false, true),
        equalTo(notAdminServer_noClusterStartup_hasServerStartup_policy));
    assertThat(
        newBuilder().getClusteredServerStartPolicy(startupControl, false, true, false),
        equalTo(notAdminServer_hasClusterStartup_noServerStartup_policy));
    assertThat(
        newBuilder().getClusteredServerStartPolicy(startupControl, false, true, true),
        equalTo(notAdminServer_hasClusterStartup_hasServerStartup_policy));
    assertThat(
        newBuilder().getClusteredServerStartPolicy(startupControl, true, false, false),
        equalTo(isAdminServer_noClusterStartup_noServerStartup_policy));
    assertThat(
        newBuilder().getClusteredServerStartPolicy(startupControl, true, false, true),
        equalTo(isAdminServer_noClusterStartup_hasServerStartup_policy));
    assertThat(
        newBuilder().getClusteredServerStartPolicy(startupControl, true, true, false),
        equalTo(isAdminServer_hasClusterStartup_noServerStartup_policy));
    assertThat(
        newBuilder().getClusteredServerStartPolicy(startupControl, true, true, true),
        equalTo(isAdminServer_hasClusterStartup_hasServerStartup_policy));
  }

  private List<ClusterStartup> createClusterStartups(String... clusterNames) {
    List<ClusterStartup> rtn = new ArrayList();
    for (String clusterName : clusterNames) {
      ClusterStartup clusterStartup = new ClusterStartup();
      clusterStartup.setClusterName(clusterName);
      rtn.add(clusterStartup);
    }
    return rtn;
  }

  private List<ServerStartup> createServerStartups(String... serverNames) {
    List<ServerStartup> rtn = new ArrayList();
    for (String serverName : serverNames) {
      ServerStartup serverStartup = new ServerStartup();
      serverStartup.setServerName(serverName);
      rtn.add(serverStartup);
    }
    return rtn;
  }

  private DomainConfigBuilderV1 newBuilder() {
    return newBuilder(new DomainSpec());
  }

  private DomainConfigBuilderV1 newBuilder(DomainSpec domainSpec) {
    return new DomainConfigBuilderV1(domainSpec);
  }
}

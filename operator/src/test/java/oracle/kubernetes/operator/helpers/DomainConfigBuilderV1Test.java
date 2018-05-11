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

import java.util.ArrayList;
import java.util.List;
import oracle.kubernetes.weblogic.domain.v1.ClusterStartup;
import oracle.kubernetes.weblogic.domain.v1.DomainSpec;
import oracle.kubernetes.weblogic.domain.v1.ServerStartup;
import org.junit.Test;

/** Tests DomainConfigBuilderV1 */
public class DomainConfigBuilderV1Test {

  @Test
  public void
      updateDomainSpec_haveDomainSpecProperties_haveClusterStartupProperties_updatesClusterStartup() {
    String cluster1 = "cluster1";
    int clusterConfigReplicas = 1;
    Integer domainReplicas = new Integer(2);
    Integer clusterStartupReplicas = new Integer(3);
    ClusterConfig clusterConfig =
        (new ClusterConfig()).withClusterName(cluster1).withReplicas(clusterConfigReplicas);
    List<ClusterStartup> clusterStartups = createClusterStartups(cluster1);
    ClusterStartup clusterStartup = clusterStartups.get(0);
    clusterStartup.setReplicas(clusterStartupReplicas);
    DomainSpec domainSpec =
        (new DomainSpec()).withReplicas(domainReplicas).withClusterStartup(clusterStartups);
    newBuilder(domainSpec).updateDomainSpec(clusterConfig);
    assertThat(domainSpec.getReplicas(), equalTo(domainReplicas));
    assertThat(clusterStartup.getReplicas(), equalTo(clusterConfigReplicas));
  }

  @Test
  public void
      updateDomainSpec_haveDomainSpecProperties_clusterStartupPropertiesNotSet_updatesDomainSpec() {
    String cluster1 = "cluster1";
    int clusterConfigReplicas = 1;
    Integer domainReplicas = new Integer(2);
    ClusterConfig clusterConfig =
        (new ClusterConfig()).withClusterName(cluster1).withReplicas(clusterConfigReplicas);
    List<ClusterStartup> clusterStartups = createClusterStartups(cluster1);
    ClusterStartup clusterStartup = clusterStartups.get(0);
    DomainSpec domainSpec =
        (new DomainSpec()).withReplicas(domainReplicas).withClusterStartup(clusterStartups);
    newBuilder(domainSpec).updateDomainSpec(clusterConfig);
    assertThat(domainSpec.getReplicas(), equalTo(clusterConfigReplicas));
    assertThat(clusterStartup.getReplicas(), nullValue());
  }

  @Test
  public void updateDomainSpec_haveDomainSpecProperties_noClusterStartup_updatesDomainSpec() {
    String cluster1 = "cluster1";
    int clusterConfigReplicas = 1;
    Integer domainReplicas = new Integer(2);
    ClusterConfig clusterConfig =
        (new ClusterConfig()).withClusterName(cluster1).withReplicas(clusterConfigReplicas);
    DomainSpec domainSpec = (new DomainSpec()).withReplicas(domainReplicas);
    newBuilder(domainSpec).updateDomainSpec(clusterConfig);
    assertThat(domainSpec.getReplicas(), equalTo(clusterConfigReplicas));
  }

  @Test
  public void
      updateDomainSpec_domainSpecPropertiesNotSet_haveClusterStartupProperties_updatesClusterStartup() {
    String cluster1 = "cluster1";
    int clusterConfigReplicas = 1;
    Integer clusterStartupReplicas = new Integer(3);
    ClusterConfig clusterConfig =
        (new ClusterConfig()).withClusterName(cluster1).withReplicas(clusterConfigReplicas);
    List<ClusterStartup> clusterStartups = createClusterStartups(cluster1);
    ClusterStartup clusterStartup = clusterStartups.get(0);
    clusterStartup.setReplicas(clusterStartupReplicas);
    DomainSpec domainSpec = (new DomainSpec()).withClusterStartup(clusterStartups);
    newBuilder(domainSpec).updateDomainSpec(clusterConfig);
    assertThat(domainSpec.getReplicas(), nullValue());
    assertThat(clusterStartup.getReplicas(), equalTo(clusterConfigReplicas));
  }

  @Test
  public void
      updateDomainSpec_domainSpecPropertiesNotSet_clusterStartupPropertiesNotSet_updatesDomainSpec() {
    String cluster1 = "cluster1";
    int clusterConfigReplicas = 1;
    ClusterConfig clusterConfig =
        (new ClusterConfig()).withClusterName(cluster1).withReplicas(clusterConfigReplicas);
    List<ClusterStartup> clusterStartups = createClusterStartups(cluster1);
    ClusterStartup clusterStartup = clusterStartups.get(0);
    DomainSpec domainSpec = (new DomainSpec()).withClusterStartup(clusterStartups);
    newBuilder(domainSpec).updateDomainSpec(clusterConfig);
    assertThat(domainSpec.getReplicas(), equalTo(clusterConfigReplicas));
    assertThat(clusterStartup.getReplicas(), nullValue());
  }

  @Test
  public void updateDomainSpec_domainSpecPropertiesNotSet_noClusterStartup_updatesDomainSpec() {
    String cluster1 = "cluster1";
    int clusterConfigReplicas = 1;
    ClusterConfig clusterConfig =
        (new ClusterConfig()).withClusterName(cluster1).withReplicas(clusterConfigReplicas);
    DomainSpec domainSpec = new DomainSpec();
    newBuilder(domainSpec).updateDomainSpec(clusterConfig);
    assertThat(domainSpec.getReplicas(), equalTo(clusterConfigReplicas));
  }

  @Test
  public void
      getEffectiveNonClusteredServerConfig_haveDomainSpecProperties_haveServerStartupProperties_returnsCorrectConfig() {
    String server1 = "server1";
    List<ServerStartup> serverStartups = createServerStartups(server1);
    ServerStartup serverStartup = serverStartups.get(0);
    serverStartup
        .withNodePort(new Integer(1))
        .withDesiredState(STARTED_SERVER_STATE_ADMIN)
        .withEnv(newEnvVarList().addElement(newEnvVar().name("name1").value("value1")));
    DomainSpec domainSpec =
        (new DomainSpec())
            .withStartupControl(SPECIFIED_STARTUPCONTROL)
            .withAsName("adminServer")
            .withImage("image2")
            .withImagePullPolicy(ALWAYS_IMAGEPULLPOLICY)
            .withServerStartup(serverStartups);
    NonClusteredServerConfig actual =
        newBuilder(domainSpec).getEffectiveNonClusteredServerConfig(server1);
    NonClusteredServerConfig want = (new NonClusteredServerConfig()).withServerName(server1);
    newBuilder()
        .initServerConfigFromDefaults(
            want); // setup up the default values - we have separate tests for this
    want.withNodePort(serverStartup.getNodePort())
        .withStartedServerState(serverStartup.getDesiredState())
        .withEnv(serverStartup.getEnv())
        .withImage(domainSpec.getImage())
        .withImagePullPolicy(domainSpec.getImagePullPolicy())
        .withNonClusteredServerStartPolicy(NON_CLUSTERED_SERVER_START_POLICY_ALWAYS);
    assertThat(actual, equalTo(want));
  }

  @Test
  public void
      getEffectiveNonClusteredServerConfig_haveDomainSpecProperties_noServerStartup_returnsCorrectConfig() {
    String server1 = "server1";
    DomainSpec domainSpec =
        (new DomainSpec())
            .withStartupControl(SPECIFIED_STARTUPCONTROL)
            .withAsName("adminServer")
            .withImage("image2")
            .withImagePullPolicy(ALWAYS_IMAGEPULLPOLICY);
    NonClusteredServerConfig actual =
        newBuilder(domainSpec).getEffectiveNonClusteredServerConfig(server1);
    NonClusteredServerConfig want = (new NonClusteredServerConfig()).withServerName(server1);
    newBuilder()
        .initServerConfigFromDefaults(
            want); // setup up the default values - we have separate tests for this
    want.withImage(domainSpec.getImage())
        .withImagePullPolicy(domainSpec.getImagePullPolicy())
        .withNonClusteredServerStartPolicy(NON_CLUSTERED_SERVER_START_POLICY_NEVER);
    assertThat(actual, equalTo(want));
  }

  @Test
  public void
      getEffectiveNonClusteredServerConfig_noDomainSpecProperties_noServerStartup_returnsCorrectConfig() {
    String server1 = "server1";
    DomainSpec domainSpec =
        (new DomainSpec()).withStartupControl(ADMIN_STARTUPCONTROL).withAsName(server1);
    NonClusteredServerConfig actual =
        newBuilder(domainSpec).getEffectiveNonClusteredServerConfig(server1);
    NonClusteredServerConfig want = (new NonClusteredServerConfig()).withServerName(server1);
    newBuilder()
        .initServerConfigFromDefaults(
            want); // setup up the default values - we have separate tests for this
    want.withImagePullPolicy(IFNOTPRESENT_IMAGEPULLPOLICY)
        .withNonClusteredServerStartPolicy(NON_CLUSTERED_SERVER_START_POLICY_ALWAYS);
    assertThat(actual, equalTo(want));
  }

  @Test
  public void
      getEffectiveClusteredServerConfig_haveDomainSpecProperties_haveClusterStartupProperties_haveServerStartupProperties_returnsCorrectConfig() {
    String cluster1 = "cluster1";
    String server1 = "server1";
    List<ServerStartup> serverStartups = createServerStartups(server1);
    ServerStartup serverStartup = serverStartups.get(0);
    serverStartup
        .withNodePort(new Integer(1))
        .withDesiredState(STARTED_SERVER_STATE_ADMIN)
        .withEnv(newEnvVarList().addElement(newEnvVar().name("name1").value("value1")));
    List<ClusterStartup> clusterStartups = createClusterStartups(cluster1);
    ClusterStartup clusterStartup = clusterStartups.get(0);
    clusterStartup
        .withDesiredState(STARTED_SERVER_STATE_RUNNING)
        .withEnv(newEnvVarList().addElement(newEnvVar().name("name2").value("value2")));
    DomainSpec domainSpec =
        (new DomainSpec())
            .withStartupControl(SPECIFIED_STARTUPCONTROL)
            .withAsName("adminServer")
            .withImage("image2")
            .withImagePullPolicy(ALWAYS_IMAGEPULLPOLICY)
            .withClusterStartup(clusterStartups)
            .withServerStartup(serverStartups);
    ClusteredServerConfig actual =
        newBuilder(domainSpec).getEffectiveClusteredServerConfig(cluster1, server1);
    ClusteredServerConfig want =
        (new ClusteredServerConfig()).withClusterName(cluster1).withServerName(server1);
    newBuilder()
        .initServerConfigFromDefaults(
            want); // setup up the default values - we have separate tests for this
    want.withNodePort(serverStartup.getNodePort())
        .withStartedServerState(serverStartup.getDesiredState())
        .withEnv(serverStartup.getEnv())
        .withImage(domainSpec.getImage())
        .withImagePullPolicy(domainSpec.getImagePullPolicy())
        .withClusteredServerStartPolicy(CLUSTERED_SERVER_START_POLICY_ALWAYS);
    assertThat(actual, equalTo(want));
  }

  @Test
  public void
      getEffectiveClusteredServerConfig_haveDomainSpecProperties_noClusterStartup_haveServerStartupProperties_returnsCorrectConfig() {
    String cluster1 = "cluster1";
    String server1 = "server1";
    List<ServerStartup> serverStartups = createServerStartups(server1);
    ServerStartup serverStartup = serverStartups.get(0);
    serverStartup
        .withNodePort(new Integer(1))
        .withDesiredState(STARTED_SERVER_STATE_ADMIN)
        .withEnv(newEnvVarList().addElement(newEnvVar().name("name1").value("value1")));
    DomainSpec domainSpec =
        (new DomainSpec())
            .withStartupControl(SPECIFIED_STARTUPCONTROL)
            .withAsName("adminServer")
            .withImage("image2")
            .withImagePullPolicy(ALWAYS_IMAGEPULLPOLICY)
            .withServerStartup(serverStartups);
    ClusteredServerConfig actual =
        newBuilder(domainSpec).getEffectiveClusteredServerConfig(cluster1, server1);
    ClusteredServerConfig want =
        (new ClusteredServerConfig()).withClusterName(cluster1).withServerName(server1);
    newBuilder()
        .initServerConfigFromDefaults(
            want); // setup up the default values - we have separate tests for this
    want.withNodePort(serverStartup.getNodePort())
        .withStartedServerState(serverStartup.getDesiredState())
        .withEnv(serverStartup.getEnv())
        .withImage(domainSpec.getImage())
        .withImagePullPolicy(domainSpec.getImagePullPolicy())
        .withClusteredServerStartPolicy(CLUSTERED_SERVER_START_POLICY_ALWAYS);
    assertThat(actual, equalTo(want));
  }

  @Test
  public void
      getEffectiveClusteredServerConfig_haveDomainSpecProperties_haveClusterStartupProperties_noServerStartup_returnsCorrectConfig() {
    String cluster1 = "cluster1";
    String server1 = "server1";
    List<ClusterStartup> clusterStartups = createClusterStartups(cluster1);
    ClusterStartup clusterStartup = clusterStartups.get(0);
    clusterStartup
        .withDesiredState(STARTED_SERVER_STATE_RUNNING)
        .withEnv(newEnvVarList().addElement(newEnvVar().name("name2").value("value2")));
    DomainSpec domainSpec =
        (new DomainSpec())
            .withStartupControl(SPECIFIED_STARTUPCONTROL)
            .withAsName("adminServer")
            .withImage("image2")
            .withImagePullPolicy(ALWAYS_IMAGEPULLPOLICY)
            .withClusterStartup(clusterStartups);
    ClusteredServerConfig actual =
        newBuilder(domainSpec).getEffectiveClusteredServerConfig(cluster1, server1);
    ClusteredServerConfig want =
        (new ClusteredServerConfig()).withClusterName(cluster1).withServerName(server1);
    newBuilder()
        .initServerConfigFromDefaults(
            want); // setup up the default values - we have separate tests for this
    want.withStartedServerState(clusterStartup.getDesiredState())
        .withEnv(clusterStartup.getEnv())
        .withImage(domainSpec.getImage())
        .withImagePullPolicy(domainSpec.getImagePullPolicy())
        .withClusteredServerStartPolicy(CLUSTERED_SERVER_START_POLICY_IF_NEEDED);
    assertThat(actual, equalTo(want));
  }

  @Test
  public void
      getEffectiveClusteredServerConfig_haveDomainSpecProperties_noClusterStartupProperties_noServerStartupProperties_returnsCorrectConfig() {
    String cluster1 = "cluster1";
    String server1 = "server1";
    DomainSpec domainSpec =
        (new DomainSpec())
            .withStartupControl(SPECIFIED_STARTUPCONTROL)
            .withAsName("adminServer")
            .withImage("image2")
            .withImagePullPolicy(ALWAYS_IMAGEPULLPOLICY);
    ClusteredServerConfig actual =
        newBuilder(domainSpec).getEffectiveClusteredServerConfig(cluster1, server1);
    ClusteredServerConfig want =
        (new ClusteredServerConfig()).withClusterName(cluster1).withServerName(server1);
    newBuilder()
        .initServerConfigFromDefaults(
            want); // setup up the default values - we have separate tests for this
    want.withImage(domainSpec.getImage())
        .withImagePullPolicy(domainSpec.getImagePullPolicy())
        .withClusteredServerStartPolicy(CLUSTERED_SERVER_START_POLICY_NEVER);
    assertThat(actual, equalTo(want));
  }

  @Test
  public void
      getEffectiveClusteredServerConfig_noDomainSpecProperties_noClusterStartupProperties_noServerStartupProperties_returnsCorrectConfig() {
    String cluster1 = "cluster1";
    String server1 = "server1";
    DomainSpec domainSpec =
        (new DomainSpec()).withStartupControl(ALL_STARTUPCONTROL).withAsName("adminServer");
    ClusteredServerConfig actual =
        newBuilder(domainSpec).getEffectiveClusteredServerConfig(cluster1, server1);
    ClusteredServerConfig want =
        (new ClusteredServerConfig()).withClusterName(cluster1).withServerName(server1);
    newBuilder()
        .initServerConfigFromDefaults(
            want); // setup up the default values - we have separate tests for this
    want.withClusteredServerStartPolicy(CLUSTERED_SERVER_START_POLICY_ALWAYS)
        .withImagePullPolicy(IFNOTPRESENT_IMAGEPULLPOLICY);
    assertThat(actual, equalTo(want));
  }

  @Test
  public void
      getEffectiveClusterConfig_haveDomainSpecProperties_haveClusterStartupProperties_returnsClusterStartupProperties() {
    String cluster1 = "cluster1";
    List<ClusterStartup> clusterStartups = createClusterStartups(cluster1);
    Integer replicasWant = new Integer(1);
    clusterStartups.get(0).setReplicas(replicasWant);
    DomainSpec domainSpec =
        (new DomainSpec()).withReplicas(new Integer(2)).withClusterStartup(clusterStartups);
    ClusterConfig actual = newBuilder(domainSpec).getEffectiveClusterConfig(cluster1);
    ClusterConfig want =
        (new ClusterConfig())
            .withClusterName(cluster1)
            .withReplicas(replicasWant)
            .withMinReplicas(replicasWant)
            .withMaxReplicas(replicasWant);
    assertThat(actual, equalTo(want));
  }

  @Test
  public void
      getEffectiveClusterConfig_haveDomainSpecProperties_noClusterStartup_returnsDomainSpecProperties() {
    String cluster1 = "cluster1";
    Integer replicasWant = new Integer(1);
    DomainSpec domainSpec = (new DomainSpec()).withReplicas(replicasWant);
    ClusterConfig actual = newBuilder(domainSpec).getEffectiveClusterConfig(cluster1);
    ClusterConfig want =
        (new ClusterConfig())
            .withClusterName(cluster1)
            .withReplicas(replicasWant)
            .withMinReplicas(replicasWant)
            .withMaxReplicas(replicasWant);
    assertThat(actual, equalTo(want));
  }

  @Test
  public void getEffectiveClusterConfig_noDomainSpecProperties_noClusterStartup_returnsDefaults() {
    String cluster1 = "cluster1";
    ClusterConfig want =
        (new ClusterConfig())
            .withClusterName(cluster1)
            .withReplicas(0)
            .withMinReplicas(0)
            .withMaxReplicas(0);
    ClusterConfig actual = newBuilder().getEffectiveClusterConfig(cluster1);
    assertThat(actual, equalTo(want));
  }

  @Test
  public void initServerConfigFromServerStartup_nullServerStartup_doesNotSetProperties() {
    ServerConfig want =
        (new ServerConfig())
            .withNodePort(new Integer(1))
            .withStartedServerState(STARTED_SERVER_STATE_ADMIN)
            .withEnv(newEnvVarList());
    ServerConfig actual =
        (new ServerConfig())
            .withNodePort(want.getNodePort())
            .withStartedServerState(want.getStartedServerState())
            .withEnv(want.getEnv());
    newBuilder().initServerConfigFromServerStartup(actual, null);
    assertThat(actual, equalTo(want));
  }

  @Test
  public void initServerConfigFromServerStartup_unsetProperties_doesNotSetProperties() {
    // env always gets replaced by an empty list if there is a server startup and its env hasn't
    // been initialized:
    ServerConfig want =
        (new ServerConfig())
            .withNodePort(new Integer(1))
            .withStartedServerState(STARTED_SERVER_STATE_ADMIN)
            .withEnv(newEnvVarList());
    ServerConfig actual =
        (new ServerConfig())
            .withNodePort(want.getNodePort())
            .withStartedServerState(want.getStartedServerState())
            .withEnv(newEnvVarList().addElement(newEnvVar().name("name1").value("value1")));
    newBuilder().initServerConfigFromServerStartup(actual, new ServerStartup());
    assertThat(actual, equalTo(want));
  }

  @Test
  public void initServerConfigFromServerStartup_setProperties_copiesProperties() {
    ServerConfig want =
        (new ServerConfig())
            .withNodePort(new Integer(1))
            .withStartedServerState(STARTED_SERVER_STATE_ADMIN)
            .withEnv(newEnvVarList().addElement(newEnvVar().name("name1").value("value1")));
    ServerConfig actual =
        (new ServerConfig())
            .withNodePort(new Integer(2))
            .withStartedServerState(STARTED_SERVER_STATE_RUNNING)
            .withEnv(newEnvVarList().addElement(newEnvVar().name("name2").value("value2")));
    ServerStartup serverStartup =
        (new ServerStartup())
            .withNodePort(want.getNodePort())
            .withDesiredState(want.getStartedServerState())
            .withEnv(want.getEnv());
    newBuilder().initServerConfigFromServerStartup(actual, serverStartup);
    assertThat(actual, equalTo(want));
  }

  @Test
  public void initClusteredServerFromClusterStartup_nullClusterStartup_doesNotCopyProperties() {
    ClusteredServerConfig want =
        (new ClusteredServerConfig())
            .withStartedServerState(STARTED_SERVER_STATE_ADMIN)
            .withEnv(newEnvVarList().addElement(newEnvVar().name("name1").value("value1")));
    ClusteredServerConfig actual =
        (new ClusteredServerConfig())
            .withStartedServerState(want.getStartedServerState())
            .withEnv(want.getEnv());
    newBuilder().initClusteredServerConfigFromClusterStartup(actual, null);
    assertThat(actual, equalTo(want));
  }

  @Test
  public void
      initClusteredServerFromClusterStartup_haveClusterStartup_unsetProperties_doesNotSetProperties() {
    ClusteredServerConfig want =
        (new ClusteredServerConfig())
            .withStartedServerState(STARTED_SERVER_STATE_ADMIN)
            .withEnv(newEnvVarList());
    ClusterStartup clusterStartup = new ClusterStartup(); // defaults env to an empty list
    ClusteredServerConfig actual =
        (new ClusteredServerConfig())
            .withStartedServerState(want.getStartedServerState())
            .withEnv(newEnvVarList().addElement(newEnvVar().name("name1").value("value1")));
    newBuilder().initClusteredServerConfigFromClusterStartup(actual, clusterStartup);
    assertThat(actual, equalTo(want));
  }

  @Test
  public void
      initClusteredServerFromClusterStartup_haveClusterStartup_setProperties_copiesProperties() {
    ClusteredServerConfig want =
        (new ClusteredServerConfig())
            .withStartedServerState(STARTED_SERVER_STATE_ADMIN)
            .withEnv(newEnvVarList().addElement(newEnvVar().name("name1").value("value1")));
    ClusterStartup clusterStartup =
        (new ClusterStartup())
            .withDesiredState(want.getStartedServerState())
            .withEnv(want.getEnv());
    ClusteredServerConfig actual =
        (new ClusteredServerConfig())
            .withStartedServerState(STARTED_SERVER_STATE_RUNNING)
            .withEnv(newEnvVarList().addElement(newEnvVar().name("name2").value("value2")));
    newBuilder().initClusteredServerConfigFromClusterStartup(actual, clusterStartup);
    assertThat(actual, equalTo(want));
  }

  @Test
  public void
      initServerConfigFromDomainSpec_unsetProperties_doesNotSetPropertiesAndDefaultsImagePullPolicy() {
    // the image pull policy should get set to IfNotPresent because the policy isn't set
    // and the image doesn't end with :latest
    ServerConfig want =
        (new ServerConfig()).withImage("image1").withImagePullPolicy(IFNOTPRESENT_IMAGEPULLPOLICY);
    ServerConfig actual = (new ServerConfig()).withImage(want.getImage());
    newBuilder().initServerConfigFromDomainSpec(actual);
    assertThat(actual, equalTo(want));
  }

  @Test
  public void
      initServerConfigFromDomainSpec_setPropertiesExceptImagePullPolicy_copiesPropertiesAndDefaultsImagePullPolicy() {
    // the image pull policy should get set to IfNotPresent because the policy isn't set
    // and the image ends with :latest
    ServerConfig want =
        (new ServerConfig()).withImage("image1:latest").withImagePullPolicy(ALWAYS_IMAGEPULLPOLICY);
    DomainSpec domainSpec = (new DomainSpec()).withImage(want.getImage());
    ServerConfig actual = (new ServerConfig()).withImage("image2");
    newBuilder(domainSpec).initServerConfigFromDomainSpec(actual);
    assertThat(actual, equalTo(want));
  }

  @Test
  public void initServerConfigFromDomainSpec_setProperties_copiesProperties() {
    ServerConfig want =
        (new ServerConfig()).withImage("image1:latest").withImagePullPolicy(NEVER_IMAGEPULLPOLICY);
    DomainSpec domainSpec =
        (new DomainSpec())
            .withImage(want.getImage())
            .withImagePullPolicy(want.getImagePullPolicy());
    ServerConfig actual =
        (new ServerConfig()).withImage("image2").withImagePullPolicy(ALWAYS_IMAGEPULLPOLICY);
    newBuilder(domainSpec).initServerConfigFromDomainSpec(actual);
    assertThat(actual, equalTo(want));
  }

  @Test
  public void initServerConfigFromDefaults_setsPropertiesToDefaultValues() {
    ServerConfig want =
        (new ServerConfig())
            .withNodePort(new Integer(0))
            .withStartedServerState(STARTED_SERVER_STATE_RUNNING)
            .withEnv(newEnvVarList())
            .withImage("store/oracle/weblogic:12.2.1.3")
            .withShutdownPolicy(SHUTDOWN_POLICY_FORCED_SHUTDOWN)
            .withGracefulShutdownTimeout(new Integer(0))
            .withGracefulShutdownIgnoreSessions(Boolean.FALSE)
            .withGracefulShutdownWaitForSessions(Boolean.FALSE);
    ServerConfig actual = new ServerConfig();
    newBuilder().initServerConfigFromDefaults(actual);
    assertThat(actual, equalTo(want));
  }

  @Test
  public void initClusterConfigFromClusterStartup_nullClusterStartup_doesNotSetProperies() {
    ClusterConfig want = (new ClusterConfig()).withReplicas(new Integer(1));
    ClusterConfig actual = new ClusterConfig().withReplicas(want.getReplicas());
    newBuilder().initClusterConfigFromClusterStartup(actual, null);
    assertThat(actual, equalTo(want));
  }

  @Test
  public void initClusterConfigFromClusterStartup_unsetProperties_doesNotSetProperies() {
    ClusterConfig want = (new ClusterConfig()).withReplicas(new Integer(1));
    ClusterConfig actual = new ClusterConfig().withReplicas(want.getReplicas());
    newBuilder().initClusterConfigFromClusterStartup(actual, new ClusterStartup());
    assertThat(actual, equalTo(want));
  }

  @Test
  public void initClusterConfigFromClusterStartup_setProperties_copiesProperies() {
    ClusterConfig want = (new ClusterConfig()).withReplicas(new Integer(1));
    ClusterStartup clusterStartup = (new ClusterStartup()).withReplicas(want.getReplicas());
    ClusterConfig actual = new ClusterConfig().withReplicas(new Integer(2));
    newBuilder().initClusterConfigFromClusterStartup(actual, clusterStartup);
    assertThat(actual, equalTo(want));
  }

  @Test
  public void initClusterConfigFromDomainSpec_unsetProperties_doesNotSetProperties() {
    ClusterConfig want = (new ClusterConfig()).withReplicas(new Integer(1));
    DomainSpec domainSpec = new DomainSpec();
    ClusterConfig actual = new ClusterConfig().withReplicas(new Integer(1));
    newBuilder(domainSpec).initClusterConfigFromDomainSpec(actual);
    assertThat(actual, equalTo(want));
  }

  @Test
  public void initClusterConfigFromDomainSpec_setProperties_copiesProperties() {
    ClusterConfig want = (new ClusterConfig()).withReplicas(new Integer(1));
    DomainSpec domainSpec = (new DomainSpec()).withReplicas(want.getReplicas());
    ClusterConfig actual = new ClusterConfig().withReplicas(new Integer(2));
    newBuilder(domainSpec).initClusterConfigFromDomainSpec(actual);
    assertThat(actual, equalTo(want));
  }

  @Test
  public void initClusterConfigFromDefaults_setProperties_setsPropertiesToDefaults() {
    ClusterConfig want = (new ClusterConfig()).withReplicas(DEFAULT_REPLICAS);
    ClusterConfig actual = new ClusterConfig();
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
    assertThat(newBuilder().isAdminServer("server1", "server1"), equalTo(true));
  }

  @Test
  public void isAdminServer_differentNames_returnsFalse() {
    assertThat(newBuilder().isAdminServer("server1", "server2"), equalTo(false));
  }

  @Test
  public void isAdminServer_serverNameNotNull_adminServerNameNull_returnsFalse() {
    assertThat(newBuilder().isAdminServer("server1", null), equalTo(false));
  }

  @Test
  public void isAdminServer_serverNameNull_adminServerNameNotNull_returnsFalse() {
    assertThat(newBuilder().isAdminServer(null, "server1"), equalTo(false));
  }

  @Test
  public void getClusterStartup_nullClusterStartups_returnsNull() {
    String cluster2 = "cluster2";
    DomainSpec domainSpec = (new DomainSpec()).withClusterStartup(null);
    ClusterStartup actual = newBuilder(domainSpec).getClusterStartup(cluster2);
    assertThat(actual, nullValue());
  }

  @Test
  public void getClusterStartup_doesNotHaveClusterName_returnsNull() {
    String cluster1 = "cluster1";
    String cluster2 = "cluster2";
    DomainSpec domainSpec = (new DomainSpec()).withClusterStartup(createClusterStartups(cluster1));
    ClusterStartup actual = newBuilder(domainSpec).getClusterStartup(cluster2);
    assertThat(actual, nullValue());
  }

  @Test
  public void getClusterStartup_hasClusterName_returnsClusterStartup() {
    String cluster1 = "cluster1";
    String cluster2 = "cluster2";
    DomainSpec domainSpec =
        (new DomainSpec()).withClusterStartup(createClusterStartups(cluster1, cluster2));
    ClusterStartup actual = newBuilder(domainSpec).getClusterStartup(cluster2);
    ClusterStartup want = domainSpec.getClusterStartup().get(1);
    assertThat(actual, equalTo(want));
  }

  @Test
  public void getServerStartup_nullServerStartups_returnsNull() {
    String server2 = "server2";
    DomainSpec domainSpec = (new DomainSpec()).withServerStartup(null);
    ServerStartup actual = newBuilder(domainSpec).getServerStartup(server2);
    assertThat(actual, nullValue());
  }

  @Test
  public void getServerStartup_doesNotHaveServerName_returnsNull() {
    String server1 = "server1";
    String server2 = "server2";
    DomainSpec domainSpec = (new DomainSpec()).withServerStartup(createServerStartups(server1));
    ServerStartup actual = newBuilder(domainSpec).getServerStartup(server2);
    assertThat(actual, nullValue());
  }

  @Test
  public void getServerStartup_hasServerName_returnsServerStartup() {
    String server1 = "server1";
    String server2 = "server2";
    DomainSpec domainSpec =
        (new DomainSpec()).withServerStartup(createServerStartups(server1, server2));
    ServerStartup actual = newBuilder(domainSpec).getServerStartup(server2);
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

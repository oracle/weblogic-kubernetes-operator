// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helpers;

import static oracle.kubernetes.operator.KubernetesConstants.*;
import static oracle.kubernetes.operator.helpers.ClusteredServerConfig.*;
import static oracle.kubernetes.operator.helpers.DomainConfigBuilderV1Dot1.*;
import static oracle.kubernetes.operator.helpers.NonClusteredServerConfig.*;
import static oracle.kubernetes.operator.helpers.ServerConfig.*;
import static oracle.kubernetes.operator.utils.KubernetesArtifactUtils.*;
import static oracle.kubernetes.operator.utils.YamlUtils.*;
import static org.hamcrest.MatcherAssert.*;
import static org.hamcrest.Matchers.*;

import com.meterware.simplestub.Memento;
import io.kubernetes.client.custom.IntOrString;
import io.kubernetes.client.models.V1EnvVar;
import java.beans.BeanInfo;
import java.beans.PropertyDescriptor;
import java.util.ArrayList;
import java.util.List;
import oracle.kubernetes.TestUtils;
import oracle.kubernetes.weblogic.domain.v1.Cluster;
import oracle.kubernetes.weblogic.domain.v1.ClusterParams;
import oracle.kubernetes.weblogic.domain.v1.ClusteredServer;
import oracle.kubernetes.weblogic.domain.v1.DomainSpec;
import oracle.kubernetes.weblogic.domain.v1.NonClusteredServer;
import oracle.kubernetes.weblogic.domain.v1.Server;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.junit.Test;

/** Tests DomainConfigBuilderV1Dot1 */
public class DomainConfigBuilderV1Dot1Test {

  private static final String CLUSTER1 = "cluster1";
  private static final String CLUSTER2 = "cluster2";
  private static final String SERVER1 = "server1";
  private static final String SERVER2 = "server2";
  private static final String IMAGE1 = "image1";
  private static final String LABEL1 = "label1";
  private static List<V1EnvVar> ENV1 =
      newEnvVarList().addElement(newEnvVar().name("name1").value("value1"));

  private static final String PROPERTY1 = "property1";
  private static final String PROPERTY2 = "property2";
  private static final String PROPERTY3 = "property3";
  private static final String PROPERTY4 = "property4";
  private static final String PROPERTY5 = "property5";
  private static final String PROPERTY6 = "property6";

  private static final String PROPERTY_STARTED_SERVER_STATE = "startedServerState";
  private static final String PROPERTY_RESTARTED_LABEL = "restartedLabel";
  private static final String PROPERTY_NODE_PORT = "nodePort";
  private static final String PROPERTY_ENV = "env";
  private static final String PROPERTY_IMAGE = "image";
  private static final String PROPERTY_IMAGE_PULL_POLICY = "imagePullPolicy";
  private static final String PROPERTY_IMAGE_PULL_SECRETS = "imagePullSecrets";
  private static final String PROPERTY_SHUTDOWN_POLICY = "shutdownPolicy";
  private static final String PROPERTY_GRACEFUL_SHUTDOWN_TIMEOUT = "gracefulShutdownTimeout";
  private static final String PROPERTY_GRACEFUL_SHUTDOWN_IGNORE_SESSIONS =
      "gracefulShutdownIgnoreSessions";
  private static final String PROPERTY_GRACEFUL_SHUTDOWN_WAIT_FOR_SESSIONS =
      "gracefulShutdownWaitForSessions";
  private static final String PROPERTY_CLUSTERED_SERVER_START_POLICY = "clusteredServerStartPolicy";
  private static final String PROPERTY_NON_CLUSTERED_SERVER_START_POLICY =
      "nonClusteredServerStartPolicy";
  private static final String PROPERTY_REPLICAS = "replicas";
  private static final String PROPERTY_MAX_SURGE = "maxSurge";
  private static final String PROPERTY_MAX_UNAVAILABLE = "maxUnavailable";

  @Test
  public void updateDomainSpec_clusterReplicasSet_updatesReplicas() {
    Cluster cluster = new Cluster().withReplicas(5);
    DomainSpec domainSpec = new DomainSpec().withCluster(CLUSTER1, cluster);

    int replicas = 3;
    ClusterConfig clusterConfig =
        (new ClusterConfig()).withClusterName(CLUSTER1).withReplicas(replicas);

    newBuilder(domainSpec).updateDomainSpec(clusterConfig);

    assertThat(cluster.getReplicas(), equalTo(replicas));
  }

  @Test
  public void udpateDomainSpec_noCluster_noClusterDefaults_createsClusterDefaultsAndSetsReplicas() {
    DomainSpec domainSpec = new DomainSpec();

    int replicas = 3;
    ClusterConfig clusterConfig =
        (new ClusterConfig()).withClusterName(CLUSTER1).withReplicas(replicas);

    newBuilder(domainSpec).updateDomainSpec(clusterConfig);

    assertThat(domainSpec.getClusterDefaults().getReplicas(), equalTo(replicas));
  }

  @Test
  public void updateClusterReplicas_noClusters_returnsFalse() {
    DomainSpec domainSpec = new DomainSpec().withClusters(null);

    boolean updated = newBuilder(domainSpec).updateClusterReplicas(CLUSTER1, 3);

    assertThat(updated, equalTo(false));
  }

  @Test
  public void updateClusterReplicas_noCluster_returnsFalse() {
    DomainSpec domainSpec = new DomainSpec();

    boolean updated = newBuilder(domainSpec).updateClusterReplicas(CLUSTER1, 3);

    assertThat(updated, equalTo(false));
  }

  @Test
  public void updateClusterReplicas_clusterReplicasNotSet_returnsFalseAndDoesNotSetReplicas() {
    Cluster cluster = new Cluster();
    DomainSpec domainSpec = new DomainSpec().withCluster(CLUSTER1, cluster);

    boolean updated = newBuilder(domainSpec).updateClusterReplicas(CLUSTER1, 3);

    assertThat(updated, equalTo(false));
    assertThat(cluster.getReplicas(), nullValue());
  }

  @Test
  public void updateClusterReplicas_clusterReplicasSet_returnsUpdatesReplicasAndReturnsTrue() {
    Cluster cluster = new Cluster().withReplicas(5);
    DomainSpec domainSpec = new DomainSpec().withCluster(CLUSTER1, cluster);
    int replicas = 3;

    boolean updated = newBuilder(domainSpec).updateClusterReplicas(CLUSTER1, replicas);

    assertThat(updated, equalTo(true));
    assertThat(cluster.getReplicas(), equalTo(replicas));
  }

  @Test
  public void
      updateClusterDefaultsReplicas_noClusterDefaults_createsClusterDefaultsAndSetsReplicas() {
    DomainSpec domainSpec = new DomainSpec();
    int replicas = 3;

    newBuilder(domainSpec).updateClusterDefaultsReplicas(replicas);

    assertThat(domainSpec.getClusterDefaults().getReplicas(), equalTo(replicas));
  }

  @Test
  public void udpateClusterDefaultReplicas_clusterDefaultsReplicasNotSet_setsReplicas() {
    ClusterParams clusterDefaults = new ClusterParams();
    DomainSpec domainSpec = (new DomainSpec()).withClusterDefaults(clusterDefaults);
    int replicas = 3;

    newBuilder(domainSpec).updateClusterDefaultsReplicas(replicas);

    assertThat(clusterDefaults.getReplicas(), equalTo(replicas));
  }

  @Test
  public void updateClusterDefaultReplicas_clusterDefaultsReplicasSet_updatesReplicas() {
    ClusterParams clusterDefaults = (new ClusterParams()).withReplicas(2);
    DomainSpec domainSpec = (new DomainSpec()).withClusterDefaults(clusterDefaults);
    int replicas = 3;

    newBuilder(domainSpec).updateClusterDefaultsReplicas(replicas);

    assertThat(clusterDefaults.getReplicas(), equalTo(replicas));
  }

  @Test
  public void getEffectiveNonClusteredServerConfig_returnsCorrectConfig() {
    NonClusteredServer server = newNonClusteredServer().withImage(IMAGE1);
    DomainSpec domainSpec = newDomainSpec().withServer(SERVER1, server);

    NonClusteredServerConfig ncsc =
        newBuilder(domainSpec).getEffectiveNonClusteredServerConfig(SERVER1);

    // Just spot check a few properties

    NonClusteredServerConfig actual =
        (new NonClusteredServerConfig())
            .withServerName(ncsc.getServerName())
            .withImage(ncsc.getImage())
            .withImagePullPolicy(ncsc.getImagePullPolicy())
            .withNonClusteredServerStartPolicy(ncsc.getNonClusteredServerStartPolicy());

    NonClusteredServerConfig want =
        (new NonClusteredServerConfig())
            .withServerName(SERVER1)
            .withImage(server.getImage())
            .withImagePullPolicy(IFNOTPRESENT_IMAGEPULLPOLICY)
            .withNonClusteredServerStartPolicy(NON_CLUSTERED_SERVER_START_POLICY_ALWAYS);

    assertThat(actual, equalTo(want));
  }

  @Test
  public void getEffectiveClusteredServerConfig_returnsCorrectConfig() {
    ClusteredServer server = newClusteredServer().withImage(IMAGE1);
    Cluster cluster = newCluster().withServer(SERVER1, server);
    DomainSpec domainSpec = newDomainSpec().withCluster(CLUSTER1, cluster);

    ClusteredServerConfig csc =
        newBuilder(domainSpec).getEffectiveClusteredServerConfig(CLUSTER1, SERVER1);

    // Just spot check a few properties

    ClusteredServerConfig actual =
        (new ClusteredServerConfig())
            .withServerName(csc.getServerName())
            .withClusterName(csc.getClusterName())
            .withImage(csc.getImage())
            .withImagePullPolicy(csc.getImagePullPolicy())
            .withClusteredServerStartPolicy(csc.getClusteredServerStartPolicy());

    ClusteredServerConfig want =
        (new ClusteredServerConfig())
            .withServerName(SERVER1)
            .withClusterName(CLUSTER1)
            .withImage(server.getImage())
            .withImagePullPolicy(IFNOTPRESENT_IMAGEPULLPOLICY)
            .withClusteredServerStartPolicy(CLUSTERED_SERVER_START_POLICY_IF_NEEDED);

    assertThat(actual, equalTo(want));
  }

  @Test
  public void getEffectiveClusterConfig_returnsCorrectConfig() {
    Cluster cluster = newCluster().withReplicas(100).withMaxSurge(newIntOrString("30%"));
    DomainSpec domainSpec = newDomainSpec().withCluster(CLUSTER1, cluster);

    ClusterConfig cc = newBuilder(domainSpec).getEffectiveClusterConfig(CLUSTER1);

    // Just spot check a few properties

    ClusterConfig actual =
        (new ClusterConfig())
            .withClusterName(cc.getClusterName())
            .withReplicas(cc.getReplicas())
            .withMinReplicas(cc.getMinReplicas())
            .withMaxReplicas(cc.getMaxReplicas());

    ClusterConfig want =
        (new ClusterConfig())
            .withClusterName(CLUSTER1)
            .withReplicas(cluster.getReplicas())
            .withMinReplicas(80) // 100 - 20%(100) since maxUnavailable defaults to 20%
            .withMaxReplicas(130); // 100 + 30%(100)

    assertThat(actual, equalTo(want));
  }

  @Test
  public void toNonClusteredServerConfig_createsCorrectConfig() {
    NonClusteredServer ncs =
        newNonClusteredServer()
            .withNonClusteredServerStartPolicy(NON_CLUSTERED_SERVER_START_POLICY_NEVER)
            .withRestartedLabel(LABEL1);

    NonClusteredServerConfig ncsc = newBuilder().toNonClusteredServerConfig(SERVER1, ncs);

    // toNonClusteredServer calls copyServerProperties, which fills in the default values.
    // we've already tested this separately.
    // just test that toNonClusteredServer filled in the server name,
    // one property from the server level (restartedLabel) and
    // nonClusteredServerStartPolicy (which copyServerProperties doesn't handle)

    NonClusteredServerConfig actual =
        (new NonClusteredServerConfig())
            .withServerName(ncsc.getServerName())
            .withRestartedLabel(ncsc.getRestartedLabel())
            .withNonClusteredServerStartPolicy(ncsc.getNonClusteredServerStartPolicy());

    NonClusteredServerConfig want =
        (new NonClusteredServerConfig())
            .withServerName(SERVER1)
            .withRestartedLabel(ncs.getRestartedLabel())
            .withNonClusteredServerStartPolicy(ncs.getNonClusteredServerStartPolicy());

    assertThat(actual, equalTo(want));
  }

  @Test
  public void toClusteredServerConfig_createsCorrectConfig() {
    ClusteredServer cs =
        newClusteredServer()
            .withClusteredServerStartPolicy(CLUSTERED_SERVER_START_POLICY_NEVER)
            .withRestartedLabel(LABEL1);

    ClusteredServerConfig csc = newBuilder().toClusteredServerConfig(CLUSTER1, SERVER1, cs);

    // toClusteredServer calls copyServerProperties, which fills in the default values.
    // we've already tested this separately.
    // just test that toClusteredServer filled in the cluster name, server name,
    // one property from the server level (restartedLabel) and
    // clusteredServerStartPolicy (which copyServerProperties doesn't handle)

    ClusteredServerConfig actual =
        (new ClusteredServerConfig())
            .withClusterName(csc.getClusterName())
            .withServerName(csc.getServerName())
            .withRestartedLabel(csc.getRestartedLabel())
            .withClusteredServerStartPolicy(csc.getClusteredServerStartPolicy());

    ClusteredServerConfig want =
        (new ClusteredServerConfig())
            .withClusterName(CLUSTER1)
            .withServerName(SERVER1)
            .withRestartedLabel(cs.getRestartedLabel())
            .withClusteredServerStartPolicy(cs.getClusteredServerStartPolicy());

    assertThat(actual, equalTo(want));
  }

  @Test
  public void toClusterConfig_createsCorrectConfig() {
    Cluster cluster =
        newCluster()
            .withReplicas(10)
            .withMaxSurge(newIntOrString("11%"))
            .withMaxUnavailable(newIntOrString(3));

    ClusterConfig actual = newBuilder().toClusterConfig(CLUSTER1, cluster);

    ClusterConfig want =
        (new ClusterConfig())
            .withClusterName(CLUSTER1)
            .withReplicas(cluster.getReplicas())
            .withMaxReplicas(12)
            .withMinReplicas(7);

    assertThat(actual, equalTo(want));
  }

  @Test
  public void copyServerProperties_allPropertiesSet_copiesProperties() {
    Server s =
        newServer()
            .withStartedServerState(STARTED_SERVER_STATE_ADMIN)
            .withRestartedLabel(LABEL1)
            .withNodePort(30003)
            .withEnv(ENV1)
            .withImage(IMAGE1)
            .withImagePullPolicy(NEVER_IMAGEPULLPOLICY)
            .withImagePullSecrets(
                newLocalObjectReferenceList().addElement(newLocalObjectReference().name("secret1")))
            .withShutdownPolicy(SHUTDOWN_POLICY_GRACEFUL_SHUTDOWN)
            .withGracefulShutdownTimeout(120)
            .withGracefulShutdownIgnoreSessions(true)
            .withGracefulShutdownWaitForSessions(true);
    ServerConfig actual = new ServerConfig();

    newBuilder().copyServerPropertiesToServerConfig(SERVER1, s, actual);

    ServerConfig want =
        (new ServerConfig())
            .withServerName(SERVER1)
            .withStartedServerState(s.getStartedServerState())
            .withRestartedLabel(s.getRestartedLabel())
            .withNodePort(s.getNodePort())
            .withEnv(s.getEnv())
            .withImage(s.getImage())
            .withImagePullPolicy(s.getImagePullPolicy())
            .withImagePullSecrets(s.getImagePullSecrets())
            .withShutdownPolicy(s.getShutdownPolicy())
            .withGracefulShutdownTimeout(s.getGracefulShutdownTimeout())
            .withGracefulShutdownIgnoreSessions(s.getGracefulShutdownIgnoreSessions())
            .withGracefulShutdownWaitForSessions(s.getGracefulShutdownWaitForSessions());

    assertThat(actual, equalTo(want));
  }

  @Test
  public void copyServerProperties_noPropertiesSet_fillsInDefaultValues() {
    ServerConfig actual = new ServerConfig();

    newBuilder().copyServerPropertiesToServerConfig(null, newServer(), actual);

    ServerConfig want =
        (new ServerConfig())
            .withStartedServerState(STARTED_SERVER_STATE_RUNNING)
            .withNodePort(0)
            .withImage(DEFAULT_IMAGE)
            .withImagePullPolicy(IFNOTPRESENT_IMAGEPULLPOLICY)
            .withShutdownPolicy(SHUTDOWN_POLICY_FORCED_SHUTDOWN)
            .withGracefulShutdownTimeout(0)
            .withGracefulShutdownIgnoreSessions(false)
            .withGracefulShutdownWaitForSessions(false);

    assertThat(actual, equalTo(want));
  }

  @Test
  public void getMaxReplicas_intMaxSurge_returns_replicasPlusMaxSurge() {
    int replicas = 10;
    int maxUnavailable = 2;
    assertThat(
        newBuilder().getMaxReplicas(replicas, new IntOrString(maxUnavailable)),
        equalTo(replicas + maxUnavailable));
  }

  @Test
  public void getMaxReplicas_percentMaxSurge_returns_replicasPlusPercentageOfReplica() {
    assertThat(newBuilder().getMaxReplicas(10, new IntOrString("21%")), equalTo(13));
  }

  @Test
  public void getMinReplicas_zeroReplicas_intMaxUnavailable_returns_0() {
    assertThat(newBuilder().getMinReplicas(0, new IntOrString(2)), equalTo(0));
  }

  @Test
  public void getMinReplicas_zeroReplicas_percentMaxUnavailable_returns_0() {
    assertThat(newBuilder().getMinReplicas(0, new IntOrString("100%")), equalTo(0));
  }

  @Test
  public void
      getMinReplicas_intMaxUnavailableLessThanReplicas_returns_replicasMinusMaxUnavailable() {
    int replicas = 10;
    int maxUnavailable = 2;
    assertThat(
        newBuilder().getMinReplicas(replicas, new IntOrString(maxUnavailable)),
        equalTo(replicas - maxUnavailable));
  }

  @Test
  public void getMinReplicas_intMaxUnavailableGreaterThanReplicas_returns_0() {
    int replicas = 10;
    assertThat(newBuilder().getMinReplicas(10, new IntOrString(11)), equalTo(0));
  }

  @Test
  public void getMinReplicas_percentMaxUnavailable_returns_replicasMinusPercentageOfReplicas() {
    assertThat(newBuilder().getMinReplicas(10, new IntOrString("22%")), equalTo(7));
  }

  @Test
  public void
      getEffectiveClusteredServer_haveServer_haveClusterHasServerDefaults_haveClusterDefaultsHasServerDefaults_haveClusteredServerDefaults_haveServerDefaults_returnsCorrectParents() {

    ClusteredServer server1 = newClusteredServer();
    ClusteredServer cluster1ServerDefaults = newClusteredServer();
    ClusteredServer clusterDefaultsServerDefaults = newClusteredServer();
    Server serverDefaults = newServer();
    ClusteredServer want = withClusteredServerDefaults(newClusteredServer());

    Cluster cluster1 =
        newCluster()
            .withServerDefaults(cluster1ServerDefaults)
            .withServer(SERVER1, server1)
            .withServer(SERVER2, newClusteredServer());
    ClusterParams clusterDefaults =
        (newClusterParams()).withServerDefaults(clusterDefaultsServerDefaults);
    DomainSpec domainSpec =
        newDomainSpec()
            .withCluster(CLUSTER1, cluster1)
            .withClusterDefaults(clusterDefaults)
            .withServerDefaults(serverDefaults);

    server1.setImage(IMAGE1);
    cluster1ServerDefaults.setImage("image2"); // ignored because set on server1
    clusterDefaultsServerDefaults.setImage("image3"); // ignored because set on server1
    serverDefaults.setImage("image4"); // ignored because set on server1
    want.setImage(server1.getImage());

    cluster1ServerDefaults.setGracefulShutdownTimeout(
        new Integer(200)); // used because not set on server1
    clusterDefaultsServerDefaults.setGracefulShutdownTimeout(
        new Integer(300)); // ignored because set on cluster1ServerDefaults
    serverDefaults.setGracefulShutdownTimeout(
        new Integer(400)); // ignored because set on cluster1ServerDefaults
    want.setGracefulShutdownTimeout(cluster1ServerDefaults.getGracefulShutdownTimeout());

    clusterDefaultsServerDefaults.setRestartedLabel(
        LABEL1); // used because not set on server1 or cluster1ServerDefaults
    serverDefaults.setRestartedLabel(
        "label2"); // ignored because set on clusterDefaultsServerDefaults
    want.setRestartedLabel(clusterDefaultsServerDefaults.getRestartedLabel());

    serverDefaults.setStartedServerState(
        STARTED_SERVER_STATE_ADMIN); // used because not set on server1, cluster1ServerDefaults or
    // clusterDefaultsServerDefaults
    want.setStartedServerState(serverDefaults.getStartedServerState());

    ClusteredServer actual = newBuilder(domainSpec).getEffectiveClusteredServer(CLUSTER1, SERVER1);
    assertThat(actual, equalTo(want));
  }

  @Test
  public void
      getEffectivClusteredServer_noServer_noCluster_noClusterDefaults_noServerDefaults_returnsBakedInDefaults() {
    ClusteredServer actual = newBuilder().getEffectiveClusteredServer(CLUSTER1, SERVER1);
    ClusteredServer want = withClusteredServerDefaults(newClusteredServer());
    assertThat(actual, equalTo(want));
  }

  @Test
  public void
      getEffectiveNonClusteredServer_haveServer_haveNonClusteredServerDefaults_haveServerDefaults_returnsNearestProperties() {

    NonClusteredServer server1 = newNonClusteredServer();
    NonClusteredServer nonClusteredServerDefaults = newNonClusteredServer();
    Server serverDefaults = newServer();
    NonClusteredServer want = withNonClusteredServerDefaults(newNonClusteredServer());

    DomainSpec domainSpec =
        newDomainSpec()
            .withServer(SERVER1, server1)
            .withNonClusteredServerDefaults(nonClusteredServerDefaults)
            .withServerDefaults(serverDefaults);

    server1.withNodePort(new Integer(20)); // used because set on server1
    nonClusteredServerDefaults.withNodePort(new Integer(25)); // ignored because set on server1
    serverDefaults.withNodePort(new Integer(30)); // ignored because set on server1
    want.setNodePort(server1.getNodePort());

    nonClusteredServerDefaults.withImagePullSecrets(
        newLocalObjectReferenceList() // used because not set on server1
            .addElement(newLocalObjectReference().name("secret1"))
            .addElement(newLocalObjectReference().name("secret2")));
    serverDefaults.withImagePullSecrets(
        newLocalObjectReferenceList() // ignored because set on nonClusteredServerDefaults
            .addElement(newLocalObjectReference().name("secret3"))
            .addElement(newLocalObjectReference().name("secret4"))
            .addElement(newLocalObjectReference().name("secret5")));
    want.setImagePullSecrets(nonClusteredServerDefaults.getImagePullSecrets());

    serverDefaults.withEnv(ENV1); // used because not set on server1 or nonClusteredServerDefaults
    want.setEnv(serverDefaults.getEnv());

    NonClusteredServer actual = newBuilder(domainSpec).getEffectiveNonClusteredServer(SERVER1);
    assertThat(actual, yamlEqualTo(want));
  }

  @Test
  public void
      getEffectiveNonClusteredServer_noServer_noNonClusteredServerDefaults_noServerDefaults_returnsBakedInDefaults() {
    NonClusteredServer actual = newBuilder().getEffectiveNonClusteredServer(SERVER1);
    NonClusteredServer want = withNonClusteredServerDefaults(newNonClusteredServer());
  }

  @Test
  public void getEffectiveCluster_haveCluster_haveClusterDefaults_returnsNearestProperties() {
    Cluster cluster1 = newCluster();
    ClusterParams clusterDefaults = newClusterParams();
    Cluster want = withClusterDefaults(newCluster());

    DomainSpec domainSpec =
        newDomainSpec().withCluster(CLUSTER1, cluster1).withClusterDefaults(clusterDefaults);

    cluster1.withMaxSurge(newIntOrString("30%")); // used because set on server1
    clusterDefaults.withMaxSurge(newIntOrString(40)); // ignored because set on server1
    want.setMaxSurge(cluster1.getMaxSurge());

    clusterDefaults.withReplicas(new Integer(6)); // used because not set on server1
    want.setReplicas(clusterDefaults.getReplicas());

    Cluster actual = newBuilder(domainSpec).getEffectiveCluster(CLUSTER1);
    // IntOrString.equals is broken, so convert to sorted yaml and compare that
    assertThat(actual, yamlEqualTo(want));
  }

  @Test
  public void getEffectiveCluster_noCluster_noClusterDefaults_returnsBakedInDefaults() {
    Cluster actual = newBuilder().getEffectiveCluster(CLUSTER1);
    Cluster want = withClusterDefaults(newCluster());
    // IntOrString.equals is broken, so convert to sorted yaml and compare that
    assertThat(actual, yamlEqualTo(want));
  }

  @Test
  public void
      getClusteredServerParents_haveServer_haveClusterHasServerDefaults_haveClusterDefaultsHasServerDefaults_haveClusteredServerDefaults_haveServerDefaults_returnsCorrectParents() {

    ClusteredServer server1 = newClusteredServer();
    ClusteredServer cluster1ServerDefaults = newClusteredServer();
    ClusteredServer clusterDefaultsServerDefaults = newClusteredServer();
    Server serverDefaults = newServer();

    Cluster cluster1 =
        newCluster()
            .withServerDefaults(cluster1ServerDefaults)
            .withServer(SERVER1, server1)
            .withServer(SERVER2, newClusteredServer());
    ClusterParams clusterDefaults =
        (newClusterParams()).withServerDefaults(clusterDefaultsServerDefaults);
    DomainSpec domainSpec =
        newDomainSpec()
            .withCluster(CLUSTER1, cluster1)
            .withClusterDefaults(clusterDefaults)
            .withServerDefaults(serverDefaults);

    // Disambiguate the parents so that equalTo fails when comparing different parents:
    server1.withGracefulShutdownTimeout(new Integer(50));
    cluster1ServerDefaults.withGracefulShutdownTimeout(new Integer(51));
    serverDefaults.withGracefulShutdownTimeout(new Integer(52));

    List<Object> actual = newBuilder(domainSpec).getClusteredServerParents(CLUSTER1, SERVER1);
    List<Object> want =
        toList(
            server1,
            cluster1ServerDefaults,
            clusterDefaultsServerDefaults,
            serverDefaults,
            CLUSTERED_SERVER_DEFAULTS,
            SERVER_DEFAULTS);
    assertThat(actual, equalTo(want));
  }

  @Test
  public void
      getClusteredServerParents_noServer_noCluster_noClusterDefaults_noClusteredServerDefaults_noServerDefaults_returnsCorrectParents() {
    List<Object> actual = newBuilder().getClusteredServerParents(CLUSTER1, SERVER1);
    List<Object> want =
        toList(
            null, // no server
            CLUSTERED_SERVER_DEFAULTS,
            SERVER_DEFAULTS);
    assertThat(actual, equalTo(want));
  }

  @Test
  public void
      getNonClusteredServerParents_haveServer_haveNonClusteredServerDefaults_haveServerDefaults_returnsCorrectParents() {
    NonClusteredServer server1 = newNonClusteredServer();
    NonClusteredServer nonClusteredServerDefaults = newNonClusteredServer();
    Server serverDefaults = newServer();

    DomainSpec domainSpec =
        newDomainSpec()
            .withServerDefaults(serverDefaults)
            .withNonClusteredServerDefaults(nonClusteredServerDefaults)
            .withServer(SERVER1, server1)
            .withServer(SERVER2, newNonClusteredServer());

    // Disambiguate the parents so that equalTo fails when comparing different parents:
    server1.withRestartedLabel(LABEL1);
    nonClusteredServerDefaults.withRestartedLabel("label2");
    serverDefaults.withRestartedLabel("label3");

    List<Object> actual = newBuilder(domainSpec).getNonClusteredServerParents(SERVER1);
    List<Object> want =
        toList(
            server1,
            nonClusteredServerDefaults,
            serverDefaults,
            NON_CLUSTERED_SERVER_DEFAULTS,
            SERVER_DEFAULTS);
    assertThat(actual, equalTo(want));
  }

  @Test
  public void
      getNonClusteredServerParents_noServer_noNonClusteredServerDefaults_noServerDefaults_returnsCorrectParents() {
    List<Object> actual = newBuilder().getNonClusteredServerParents(SERVER1);
    List<Object> want =
        toList(
            null, // no server
            null, // no domain non-clustered server defaults
            null, // no domain server defaults
            NON_CLUSTERED_SERVER_DEFAULTS,
            SERVER_DEFAULTS);
    assertThat(actual, equalTo(want));
  }

  @Test
  public void getClusterParents_haveCluster_haveClusterDefaults_returnsCorrectParents() {
    Cluster cluster1 = newCluster();
    ClusterParams clusterDefaults = newClusterParams();

    DomainSpec domainSpec =
        newDomainSpec()
            .withClusterDefaults(clusterDefaults)
            .withCluster(CLUSTER1, cluster1)
            .withCluster(CLUSTER2, newCluster());

    // Disambiguate the parents so that equalTo fails when comparing different parents:
    cluster1.withReplicas(new Integer(5));
    clusterDefaults.withReplicas(new Integer(7));

    List<Object> actual = newBuilder(domainSpec).getClusterParents(CLUSTER1);
    List<Object> want = toList(cluster1, clusterDefaults, CLUSTER_DEFAULTS);
    assertThat(actual, equalTo(want));
  }

  @Test
  public void getClusterParents_noCluster_noClusterDefaults_returnsCorrectParents() {
    List<Object> actual = newBuilder().getClusterParents(CLUSTER1);
    List<Object> want =
        toList(
            null, // no cluster
            null, // no domain cluster defaults
            CLUSTER_DEFAULTS);
    assertThat(actual, equalTo(want));
  }

  @Test
  public void getEffectiveProperties_copiesNearestParentProperties() {

    TestBean greatGrandParent = new TestBean();
    TestBean grandParent = null; // test that null parents are allowed
    TestBean parent = new TestBean();
    TestBean actual = new TestBean();
    TestBean want = new TestBean();

    // set on great grand parent, set on parent, set on actual: use actual
    greatGrandParent.setProperty1("property1-greatgrandparent");
    parent.setProperty1("property1-parent");
    actual.setProperty1("property1-actual");
    want.setProperty1(actual.getProperty1());

    // set on great grand parent, not set on parent, set on actual: use actual
    greatGrandParent.setProperty2(new Integer(1));
    actual.setProperty2(new Integer(2));
    want.setProperty2(actual.getProperty2());

    // set on great grand parent, not set on parent, not set on actual : use great grand parent
    greatGrandParent.setProperty3(Boolean.TRUE);
    want.setProperty3(greatGrandParent.getProperty3());

    // not set on great grand parent, set on parent, set on actual : use actual
    parent.setProperty4("property4-parent");
    actual.setProperty4("property4-actual");
    want.setProperty4(actual.getProperty4());

    // not set on great grand parent, set on parent, not set on actual : use parent
    parent.setProperty5("property5-parent");
    want.setProperty5(parent.getProperty5());

    // not set on great grand parent, not set on parent, set on actual : use actual
    actual.setProperty6("property6-actual");
    want.setProperty6(actual.getProperty6());

    List<Object> parents = new ArrayList();
    parents.add(parent);
    parents.add(grandParent);
    parents.add(greatGrandParent);

    newBuilder()
        .getEffectiveProperties(
            actual, parents, PROPERTY1, PROPERTY2, PROPERTY3, PROPERTY4, PROPERTY5, PROPERTY6);

    assertThat(actual, equalTo(want));
  }

  @Test
  public void copyParentProperties_copiesUnsetProperties() {

    TestBean actual = new TestBean();
    TestBean parent = new TestBean();
    TestBean want = new TestBean();

    // set on parent, set on actual : use actual
    parent.setProperty1("property1-parent");
    actual.setProperty1("property1-actual");
    want.setProperty1(actual.getProperty1());

    // set on parent, not set on actual : use parent
    parent.setProperty2(new Integer(1));
    want.setProperty2(parent.getProperty2());

    // not set on parent, set on actual: use actual
    actual.setProperty3(new Boolean(true));
    want.setProperty3(actual.getProperty3());

    newBuilder()
        .copyParentProperties(
            actual, getTestBeanBI(), parent, getTestBeanBI(), PROPERTY1, PROPERTY2, PROPERTY3);

    assertThat(actual, equalTo(want));
  }

  @Test
  public void
      setPropertyIfUnsetAndHaveValue_propertyNotSet_dontHaveValue_doesNotSetPropertyValue() {
    String propertyName = PROPERTY1;
    TestBean to = new TestBean();
    newBuilder().setPropertyIfUnsetAndHaveValue(to, getTestBeanBI(), propertyName, null);
    assertThat(to, hasProperty(propertyName, nullValue()));
  }

  @Test
  public void setPropertyIfUnsetAndHaveValue_propertySet_dontHaveValue_doesNotSetPropertyValue() {
    String propertyName = PROPERTY1;
    String oldPropertyValue = "oldvalue";
    TestBean to = (new TestBean()).withProperty1(oldPropertyValue);
    newBuilder().setPropertyIfUnsetAndHaveValue(to, getTestBeanBI(), propertyName, null);
    assertThat(to, hasProperty(propertyName, equalTo(oldPropertyValue)));
  }

  @Test
  public void setPropertyIfUnsetAndHaveValue_propertySet_haveValue_doesNotSetPropertyValue() {
    String propertyName = PROPERTY1;
    String oldPropertyValue = "property1-old";
    String newPropertyValue = "property1-new";
    TestBean to = (new TestBean()).withProperty1(oldPropertyValue);
    newBuilder()
        .setPropertyIfUnsetAndHaveValue(to, getTestBeanBI(), propertyName, newPropertyValue);
    assertThat(to, hasProperty(propertyName, equalTo(oldPropertyValue)));
  }

  @Test
  public void setPropertyIfUnsetAndHaveValue_propertyNotSet_haveValue_setsPropertyValue() {
    String propertyName = PROPERTY1;
    String newPropertyValue = "property1-value";
    TestBean to = new TestBean();
    newBuilder()
        .setPropertyIfUnsetAndHaveValue(to, getTestBeanBI(), propertyName, newPropertyValue);
    assertThat(to, hasProperty(propertyName, equalTo(newPropertyValue)));
  }

  @Test
  public void getProperty_propertyDoesNotExist_returnsNull() {
    assertThat(newBuilder().getProperty(new TestBean(), null), nullValue());
  }

  @Test
  public void getProperty_propertyExists_propertyNotSet_returnsNull() {
    assertThat(
        newBuilder()
            .getProperty(
                new TestBean(), newBuilder().getPropertyDescriptor(getTestBeanBI(), PROPERTY2)),
        nullValue());
  }

  @Test
  public void getProperty_propertyExists_propertySet_returnsPropertyValue() {
    String propertyValue = "property1-value";
    TestBean testBean = (new TestBean()).withProperty1(propertyValue);
    assertThat(
        newBuilder()
            .getProperty(testBean, newBuilder().getPropertyDescriptor(getTestBeanBI(), PROPERTY1)),
        equalTo(propertyValue));
  }

  @Test
  public void getPropertyDescriptor_existingProperty_returnsPropertyDescriptor() {
    PropertyDescriptor pd = newBuilder().getPropertyDescriptor(getTestBeanBI(), PROPERTY1);
    assertThat(pd, notNullValue());
    assertThat(pd.getName(), equalTo(PROPERTY1));
  }

  @Test
  public void getPropertyDescriptor_nonExistingProperty_returnsPropertyDescriptor() {
    assertThat(newBuilder().getPropertyDescriptor(getTestBeanBI(), "noSuchProperty"), nullValue());
  }

  @Test
  public void getBeanInfo_returnsBeanInfo() {
    BeanInfo bi = getTestBeanBI();
    assertThat(bi, notNullValue());
    assertThat(bi.getBeanDescriptor().getBeanClass(), equalTo(TestBean.class));
  }

  @Test
  public void test_getPercent_nonFraction_doesntRoundUp() {
    assertThat(newBuilder().getPercentage(10, 50), equalTo(5));
  }

  @Test
  public void test_getPercent_fraction_roundsUp() {
    assertThat(newBuilder().getPercentage(10, 11), equalTo(2));
  }

  @Test
  public void test_getPercent_zero_returns_zero() {
    assertThat(newBuilder().getPercentage(0, 100), equalTo(0));
  }

  @Test
  public void test_getPercent_null_returns_0() {
    assertThat(newBuilder().getPercent("reason", null), equalTo(0));
  }

  @Test
  public void test_getPercent_noPercentSign_returns_0() {
    assertThat(newBuilder().getPercent("reason", "123"), equalTo(0));
  }

  @Test
  public void test_getPercent_nonInteger_returns_0() {
    assertThat(newBuilder().getPercent("reason", "12.3%"), equalTo(0));
  }

  @Test
  public void test_getPercent_negative_returns_0() {
    assertThat(newBuilder().getPercent("reason", "-1%"), equalTo(0));
  }

  @Test
  public void test_getPercent_zero_returns_0() {
    int percent = 0;
    assertThat(newBuilder().getPercent("reason", percent + "%"), equalTo(percent));
  }

  @Test
  public void test_getPercent_100_returns_0() {
    int percent = 100;
    assertThat(newBuilder().getPercent("reason", percent + "%"), equalTo(percent));
  }

  @Test
  public void test_getPercent_over100_returns_0() {
    assertThat(newBuilder().getPercent("reason", "101%"), equalTo(0));
  }

  @Test
  public void test_getPercent_between0And100_returns_percent() {
    int percent = 54;
    assertThat(newBuilder().getPercent("reason", percent + "%"), equalTo(percent));
  }

  @Test
  public void test_getNonNegativeInt_positveValue_returns_value() {
    int val = 5;
    assertThat(newBuilder().getNonNegativeInt("reason", val), equalTo(val));
  }

  @Test
  public void test_getNonNegativeInt_zero_returns_0() {
    assertThat(newBuilder().getNonNegativeInt("reason", 0), equalTo(0));
  }

  @Test
  public void test_getNonNegativeInt_negativeValue_returns_0() {
    assertThat(newBuilder().getNonNegativeInt("reason", -1), equalTo(0));
  }

  @Test
  public void toInt_int_returns_int() {
    int val = 7;
    assertThat(newBuilder().toInt(new Integer(val)), equalTo(val));
  }

  @Test
  public void toInt_null_returns_0() {
    assertThat(newBuilder().toInt(null), equalTo(0));
  }

  @Test
  public void toBool_val_returns_val() {
    boolean val = true;
    assertThat(newBuilder().toBool(new Boolean(val)), equalTo(val));
  }

  @Test
  public void toBool_null_returns_false() {
    assertThat(newBuilder().toBool(null), equalTo(false));
  }

  private NonClusteredServer withNonClusteredServerDefaults(NonClusteredServer nonClusteredServer) {
    withServerDefaults(nonClusteredServer);
    return nonClusteredServer.withNonClusteredServerStartPolicy(
        NON_CLUSTERED_SERVER_START_POLICY_ALWAYS);
  }

  private ClusteredServer withClusteredServerDefaults(ClusteredServer clusteredServer) {
    withServerDefaults(clusteredServer);
    return clusteredServer.withClusteredServerStartPolicy(CLUSTERED_SERVER_START_POLICY_IF_NEEDED);
  }

  private Server withServerDefaults(Server server) {
    return server
        .withStartedServerState(STARTED_SERVER_STATE_RUNNING)
        // no restartedLabel value
        // no nodePort value
        // no env value
        .withImage(DEFAULT_IMAGE)
        // no default image pull policy since it gets computed based on the image
        // no imagePullSecrets value
        .withShutdownPolicy(SHUTDOWN_POLICY_FORCED_SHUTDOWN)
        .withGracefulShutdownTimeout(new Integer(0))
        .withGracefulShutdownIgnoreSessions(Boolean.FALSE)
        .withGracefulShutdownWaitForSessions(Boolean.FALSE);
  }

  private Cluster withClusterDefaults(Cluster cluster) {
    withClusterParamsDefaults(cluster);
    return cluster;
  }

  private ClusterParams withClusterParamsDefaults(ClusterParams clusterParams) {
    return clusterParams
        // no default replicas value
        .withMaxSurge(newIntOrString("20%"))
        .withMaxUnavailable(newIntOrString("20%"));
  }

  private BeanInfo getTestBeanBI() {
    return newBuilder().getBeanInfo(new TestBean());
  }

  public static class TestBean {
    private String property1;

    public String getProperty1() {
      return property1;
    }

    public void setProperty1(String val) {
      property1 = val;
    }

    public TestBean withProperty1(String val) {
      setProperty1(val);
      return this;
    }

    private Integer property2;

    public Integer getProperty2() {
      return property2;
    }

    public void setProperty2(Integer val) {
      property2 = val;
    }

    public TestBean withProperty2(Integer val) {
      setProperty2(val);
      return this;
    }

    private Boolean property3;

    public Boolean getProperty3() {
      return property3;
    }

    public void setProperty3(Boolean val) {
      property3 = val;
    }

    public TestBean withProperty3(Boolean val) {
      setProperty3(val);
      return this;
    }

    private String property4;

    public String getProperty4() {
      return property4;
    }

    public void setProperty4(String val) {
      property4 = val;
    }

    public TestBean withProperty4(String val) {
      setProperty4(val);
      return this;
    }

    private String property5;

    public String getProperty5() {
      return property5;
    }

    public void setProperty5(String val) {
      property5 = val;
    }

    public TestBean withProperty5(String val) {
      setProperty5(val);
      return this;
    }

    private String property6;

    public String getProperty6() {
      return property6;
    }

    public void setProperty6(String val) {
      property6 = val;
    }

    public TestBean withProperty6(String val) {
      setProperty6(val);
      return this;
    }

    @Override
    public String toString() {
      return new ToStringBuilder(this)
          .append("property1", property1)
          .append("property2", property2)
          .append("property3", property3)
          .append("property4", property4)
          .append("property5", property5)
          .append("property6", property6)
          .toString();
    }

    @Override
    public int hashCode() {
      return new HashCodeBuilder()
          .append(property1)
          .append(property2)
          .append(property3)
          .append(property4)
          .append(property5)
          .append(property6)
          .toHashCode();
    }

    @Override
    public boolean equals(Object other) {
      if (other == this) {
        return true;
      }
      if ((other instanceof TestBean) == false) {
        return false;
      }
      TestBean rhs = ((TestBean) other);
      return new EqualsBuilder()
          .append(property1, rhs.property1)
          .append(property2, rhs.property2)
          .append(property3, rhs.property3)
          .append(property4, rhs.property4)
          .append(property5, rhs.property5)
          .append(property6, rhs.property6)
          .isEquals();
    }
  }

  private <T> List<T> toList(T... vals) {
    List<T> list = new ArrayList();
    for (T val : vals) {
      list.add(val);
    }
    return list;
  }

  private DomainConfigBuilderV1Dot1 newBuilder() {
    return newBuilder(new DomainSpec());
  }

  private DomainConfigBuilderV1Dot1 newBuilder(DomainSpec domainSpec) {
    return new TestDomainConfigBuilderV1Dot1(domainSpec);
  }

  // Extend the v1.1 builder so that we can disable logging messages in one place:
  private static class TestDomainConfigBuilderV1Dot1 extends DomainConfigBuilderV1Dot1 {

    private TestDomainConfigBuilderV1Dot1(DomainSpec domainSpec) {
      super(domainSpec);
    }

    @Override
    protected void logWarning(String context, String message) {
      Memento memento = TestUtils.silenceOperatorLogger();
      try {
        super.logWarning(context, message);
      } finally {
        memento.revert();
      }
    }
  }
}

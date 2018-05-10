// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helpers;

import static oracle.kubernetes.operator.KubernetesConstants.*;
import static oracle.kubernetes.operator.LabelConstants.*;
import static oracle.kubernetes.operator.StartupControlConstants.*;
import static oracle.kubernetes.operator.VersionConstants.*;
import static oracle.kubernetes.operator.create.KubernetesArtifactUtils.*;
import static org.hamcrest.MatcherAssert.*;
import static org.hamcrest.Matchers.*;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import oracle.kubernetes.weblogic.domain.v1.Domain;
import oracle.kubernetes.weblogic.domain.v1.DomainSpec;
import org.junit.Test;

/** Tests LifeCycleHelper */
public class LifeCycleHelperTest extends LifeCycleHelper {

  @Test
  public void updateDomainSpec_updatesSpec() {
    String cluster1 = "cluster1";
    int clusterConfigReplicas = 1;
    ClusterConfig clusterConfig =
        (new ClusterConfig()).withClusterName(cluster1).withReplicas(clusterConfigReplicas);
    DomainSpec domainSpec = new DomainSpec();
    Domain domain = newDomainV1().withSpec(domainSpec);
    updateDomainSpec(domain, clusterConfig);
    assertThat(domainSpec.getReplicas(), equalTo(clusterConfigReplicas));
  }

  @Test
  public void getEffectiveDomainConfig_returnsCorrectConfig() {
    String cluster1 = "cluster1";
    String cluster2 = "cluster2";
    String server1 = "server1";
    String server2 = "server2";
    String server3 = "server3";
    Map<String, Set<String>> clusters = new HashMap();
    clusters.put(cluster1, getServers(server1));
    clusters.put(cluster2, getServers(server2));
    Set<String> servers = getServers(server3);
    DomainSpec domainSpec =
        (new DomainSpec()).withStartupControl(ADMIN_STARTUPCONTROL).withAsName(server3);
    Domain domain = newDomainV1().withSpec(domainSpec);
    DomainConfig actual = getEffectiveDomainConfig(domain, servers, clusters);
    // Just spot check that the expected servers and clusters got created
    assertThat(actual.getServers().keySet(), contains(server3));
    assertThat(actual.getClusters().keySet(), contains(cluster1, cluster2));
    assertThat(actual.getClusters().get(cluster1).getServers().keySet(), contains(server1));
    assertThat(actual.getClusters().get(cluster2).getServers().keySet(), contains(server2));
  }

  @Test
  public void getEffectiveNonClusteredServerConfig_returnsCorrectConfig() {
    String server1 = "server1";
    DomainSpec domainSpec =
        (new DomainSpec()).withStartupControl(ADMIN_STARTUPCONTROL).withAsName(server1);
    Domain domain = newDomainV1().withSpec(domainSpec);
    NonClusteredServerConfig actual = getEffectiveNonClusteredServerConfig(domain, server1);
    // Just check that we got something back.  The unit tests for the various
    // DomainConfigBuilders test the specific.
    assertThat(actual, notNullValue());
  }

  @Test
  public void getEffectiveClusteredServerConfig_returnsCorrectConfig() {
    String cluster1 = "cluster1";
    String server1 = "server1";
    DomainSpec domainSpec =
        (new DomainSpec()).withStartupControl(ADMIN_STARTUPCONTROL).withAsName(server1);
    Domain domain = newDomainV1().withSpec(domainSpec);
    ClusteredServerConfig actual = getEffectiveClusteredServerConfig(domain, cluster1, server1);
    // Just check that we got something back.  The unit tests for the various
    // DomainConfigBuilders test the specific.
    assertThat(actual, notNullValue());
  }

  @Test
  public void getEffectiveClusterConfig_returnsCorrectConfig() {
    String cluster1 = "cluster1";
    String server1 = "server1";
    DomainSpec domainSpec =
        (new DomainSpec()).withStartupControl(ADMIN_STARTUPCONTROL).withAsName(server1);
    Domain domain = newDomainV1().withSpec(domainSpec);
    ClusterConfig actual = getEffectiveClusterConfig(domain, cluster1);
    // Just check that we got something back.  The unit tests for the various
    // DomainConfigBuilders test the specific.
    assertThat(actual, notNullValue());
  }

  @Test
  public void getEffectiveNonClusteredServerConfigs_returnsExpectedConfig() {
    String server1 = "server1";
    String server2 = "server2";
    DomainConfig actual = new DomainConfig();
    getEffectiveNonClusteredServerConfigs(getBuilder(), actual, null, getServers(server1, server2));
    DomainConfig want =
        (new DomainConfig())
            .withServer(server1, (new NonClusteredServerConfig()).withServerName(server1))
            .withServer(server2, (new NonClusteredServerConfig()).withServerName(server2));
    assertThat(actual, equalTo(want));
  }

  @Test
  public void getEffectiveNonClusteredServerConfig_returnsExpectedConfig() {
    String server1 = "server1";
    DomainConfig actual = new DomainConfig();
    getEffectiveNonClusteredServerConfig(getBuilder(), actual, null, server1);
    DomainConfig want =
        (new DomainConfig())
            .withServer(server1, (new NonClusteredServerConfig()).withServerName(server1));
    assertThat(actual, equalTo(want));
  }

  @Test
  public void getEffectiveClusterConfigs_returnsExpectedConfig() {
    String cluster1 = "cluster1";
    String cluster2 = "cluster2";
    String server1 = "server1";
    String server2 = "server2";
    Map<String, Set<String>> clusters = new HashMap();
    clusters.put(cluster1, getServers(server1));
    clusters.put(cluster2, getServers(server2));
    DomainConfig actual = new DomainConfig();
    getEffectiveClusterConfigs(getBuilder(), actual, null, clusters);
    DomainConfig want =
        (new DomainConfig())
            .withCluster(
                cluster1,
                (new ClusterConfig())
                    .withClusterName(cluster1)
                    .withServer(
                        server1,
                        (new ClusteredServerConfig())
                            .withClusterName(cluster1)
                            .withServerName(server1)))
            .withCluster(
                cluster2,
                (new ClusterConfig())
                    .withClusterName(cluster2)
                    .withServer(
                        server2,
                        (new ClusteredServerConfig())
                            .withClusterName(cluster2)
                            .withServerName(server2)));
    assertThat(actual, equalTo(want));
  }

  @Test
  public void getEffectiveClusterConfig1_returnsExpectedConfig() {
    String cluster1 = "cluster1";
    String server1 = "server1";
    DomainConfig actual = new DomainConfig();
    getEffectiveClusterConfig(getBuilder(), actual, null, cluster1, getServers(server1));
    DomainConfig want =
        (new DomainConfig())
            .withCluster(
                cluster1,
                (new ClusterConfig())
                    .withClusterName(cluster1)
                    .withServer(
                        server1,
                        (new ClusteredServerConfig())
                            .withClusterName(cluster1)
                            .withServerName(server1)));
    assertThat(actual, equalTo(want));
  }

  @Test
  public void getEffectiveClusterConfig2_returnsExpectedConfig() {
    String cluster1 = "cluster1";
    String server1 = "server1";
    ClusterConfig actual =
        getEffectiveClusterConfig(getBuilder(), null, cluster1, getServers(server1));
    ClusterConfig want =
        (new ClusterConfig())
            .withClusterName(cluster1)
            .withServer(
                server1,
                (new ClusteredServerConfig()).withClusterName(cluster1).withServerName(server1));
    assertThat(actual, equalTo(want));
  }

  @Test
  public void getEffectiveClusteredServerConfigs_returnsExpectedConfig() {
    String cluster1 = "cluster1";
    String server1 = "server1";
    String server2 = "server2";
    ClusterConfig actual = (new ClusterConfig()).withClusterName(cluster1);
    getEffectiveClusteredServerConfigs(getBuilder(), actual, null, getServers(server1, server2));
    ClusterConfig want =
        (new ClusterConfig())
            .withClusterName(cluster1)
            .withServer(
                server1,
                (new ClusteredServerConfig()).withClusterName(cluster1).withServerName(server1))
            .withServer(
                server2,
                (new ClusteredServerConfig()).withClusterName(cluster1).withServerName(server2));
    assertThat(actual, equalTo(want));
  }

  @Test
  public void getEffectiveClusteredServerConfig_returnsExpectedConfig() {
    String cluster1 = "cluster1";
    String server1 = "server1";
    ClusterConfig actual = (new ClusterConfig()).withClusterName(cluster1);
    getEffectiveClusteredServerConfig(getBuilder(), actual, null, server1);
    ClusterConfig want =
        (new ClusterConfig())
            .withClusterName(cluster1)
            .withServer(
                server1,
                (new ClusteredServerConfig()).withClusterName(cluster1).withServerName(server1));
    assertThat(actual, equalTo(want));
  }

  @Test
  public void getDomainConfigBuilder_domainV1ResourceVersion_returnsDomainConfigBuilderV1() {
    assertThat(getDomainConfigBuilder(newDomainV1()), instanceOf(DomainConfigBuilderV1.class));
  }

  @Test(expected = AssertionError.class)
  public void getDomainConfigBuilder_missingResourceVersion_throwsException() {
    getDomainConfigBuilder(newDomain());
  }

  @Test(expected = AssertionError.class)
  public void getDomainConfigBuilder_unsupportedResourceVersion_throwsException() {
    getDomainConfigBuilder(
        newDomain()
            .withMetadata(newObjectMeta().putLabelsItem(RESOURCE_VERSION_LABEL, "NoSuchVersion")));
  }

  private Domain newDomainV1() {
    return newDomain()
        .withMetadata(newObjectMeta().putLabelsItem(RESOURCE_VERSION_LABEL, DOMAIN_V1));
  }

  private Set<String> getServers(String... servers) {
    Set<String> set = new HashSet();
    for (String server : servers) {
      set.add(server);
    }
    return set;
  }

  private DomainConfigBuilder getBuilder() {
    return new TestDomainConfigBuilder();
  }

  private static class TestDomainConfigBuilder extends DomainConfigBuilder {
    @Override
    public NonClusteredServerConfig getEffectiveNonClusteredServerConfig(
        DomainSpec domainSpec, String serverName) {
      return (new NonClusteredServerConfig()).withServerName(serverName);
    }

    @Override
    public ClusteredServerConfig getEffectiveClusteredServerConfig(
        DomainSpec domainSpec, String clusterName, String serverName) {
      return (new ClusteredServerConfig()).withClusterName(clusterName).withServerName(serverName);
    }

    @Override
    public ClusterConfig getEffectiveClusterConfig(DomainSpec domainSpec, String clusterName) {
      return (new ClusterConfig()).withClusterName(clusterName);
    }

    @Override
    public void updateDomainSpec(DomainSpec domainSpec, ClusterConfig clusterConfig) {
      // TBD - should we modify domainSpec?
    }
  }
}

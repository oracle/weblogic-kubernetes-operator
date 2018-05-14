// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helpers;

import static oracle.kubernetes.operator.KubernetesConstants.*;
import static oracle.kubernetes.operator.LabelConstants.*;
import static oracle.kubernetes.operator.StartupControlConstants.*;
import static oracle.kubernetes.operator.VersionConstants.*;
import static oracle.kubernetes.operator.utils.KubernetesArtifactUtils.*;
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
public class LifeCycleHelperTest {

  private static final String CLUSTER1 = "cluster1";
  private static final String CLUSTER2 = "cluster2";
  private static final String SERVER1 = "server1";
  private static final String SERVER2 = "server2";
  private static final String SERVER3 = "server3";

  @Test
  public void updateDomainSpec_updatesSpec() {
    int clusterConfigReplicas = 1;
    ClusterConfig clusterConfig =
        (new ClusterConfig()).withClusterName(CLUSTER1).withReplicas(clusterConfigReplicas);

    DomainSpec domainSpec = new DomainSpec();
    Domain domain = newDomainV1().withSpec(domainSpec);

    getHelper().updateDomainSpec(domain, clusterConfig);

    assertThat(domainSpec.getReplicas(), equalTo(clusterConfigReplicas));
  }

  @Test
  public void getEffectiveDomainConfig_returnsCorrectConfig() {
    Map<String, Set<String>> clusters = new HashMap();
    clusters.put(CLUSTER1, getServers(SERVER1));
    clusters.put(CLUSTER2, getServers(SERVER2));
    Set<String> servers = getServers(SERVER3);

    DomainSpec domainSpec =
        (new DomainSpec()).withStartupControl(ADMIN_STARTUPCONTROL).withAsName(SERVER3);
    Domain domain = newDomainV1().withSpec(domainSpec);

    DomainConfig actual = getHelper().getEffectiveDomainConfig(domain, servers, clusters);

    // Just spot check that the expected servers and clusters got created
    assertThat(actual.getServers().keySet(), contains(SERVER3));
    assertThat(actual.getClusters().keySet(), contains(CLUSTER1, CLUSTER2));
    assertThat(actual.getClusters().get(CLUSTER1).getServers().keySet(), contains(SERVER1));
    assertThat(actual.getClusters().get(CLUSTER2).getServers().keySet(), contains(SERVER2));
  }

  @Test
  public void getEffectiveNonClusteredServerConfig_returnsCorrectConfig() {
    DomainSpec domainSpec =
        (new DomainSpec()).withStartupControl(ADMIN_STARTUPCONTROL).withAsName(SERVER1);
    Domain domain = newDomainV1().withSpec(domainSpec);

    NonClusteredServerConfig actual =
        getHelper().getEffectiveNonClusteredServerConfig(domain, SERVER1);

    // Just check that we got something back.  The unit tests for the various
    // DomainConfigBuilders test the specific.
    assertThat(actual, notNullValue());
  }

  @Test
  public void getEffectiveClusteredServerConfig_returnsCorrectConfig() {
    DomainSpec domainSpec =
        (new DomainSpec()).withStartupControl(ADMIN_STARTUPCONTROL).withAsName(SERVER1);
    Domain domain = newDomainV1().withSpec(domainSpec);

    ClusteredServerConfig actual =
        getHelper().getEffectiveClusteredServerConfig(domain, CLUSTER1, SERVER1);

    // Just check that we got something back.  The unit tests for the various
    // DomainConfigBuilders test the specific.
    assertThat(actual, notNullValue());
  }

  @Test
  public void getEffectiveClusterConfig_returnsCorrectConfig() {
    DomainSpec domainSpec =
        (new DomainSpec()).withStartupControl(ADMIN_STARTUPCONTROL).withAsName(SERVER1);
    Domain domain = newDomainV1().withSpec(domainSpec);

    ClusterConfig actual = getHelper().getEffectiveClusterConfig(domain, CLUSTER1);

    // Just check that we got something back.  The unit tests for the various
    // DomainConfigBuilders test the specific.
    assertThat(actual, notNullValue());
  }

  @Test
  public void getEffectiveNonClusteredServerConfigs_returnsExpectedConfig() {
    DomainConfig actual = new DomainConfig();

    getHelper()
        .getEffectiveNonClusteredServerConfigs(getBuilder(), actual, getServers(SERVER1, SERVER2));

    DomainConfig want =
        (new DomainConfig())
            .withServer(SERVER1, (new NonClusteredServerConfig()).withServerName(SERVER1))
            .withServer(SERVER2, (new NonClusteredServerConfig()).withServerName(SERVER2));

    assertThat(actual, equalTo(want));
  }

  @Test
  public void getEffectiveNonClusteredServerConfig_returnsExpectedConfig() {
    DomainConfig actual = new DomainConfig();

    DomainConfig want =
        (new DomainConfig())
            .withServer(SERVER1, (new NonClusteredServerConfig()).withServerName(SERVER1));

    getHelper().getEffectiveNonClusteredServerConfig(getBuilder(), actual, SERVER1);

    assertThat(actual, equalTo(want));
  }

  @Test
  public void getEffectiveClusterConfigs_returnsExpectedConfig() {
    Map<String, Set<String>> clusters = new HashMap();
    clusters.put(CLUSTER1, getServers(SERVER1));
    clusters.put(CLUSTER2, getServers(SERVER2));

    DomainConfig actual = new DomainConfig();

    DomainConfig want =
        (new DomainConfig())
            .withCluster(
                CLUSTER1,
                (new ClusterConfig())
                    .withClusterName(CLUSTER1)
                    .withServer(
                        SERVER1,
                        (new ClusteredServerConfig())
                            .withClusterName(CLUSTER1)
                            .withServerName(SERVER1)))
            .withCluster(
                CLUSTER2,
                (new ClusterConfig())
                    .withClusterName(CLUSTER2)
                    .withServer(
                        SERVER2,
                        (new ClusteredServerConfig())
                            .withClusterName(CLUSTER2)
                            .withServerName(SERVER2)));

    getHelper().getEffectiveClusterConfigs(getBuilder(), actual, clusters);

    assertThat(actual, equalTo(want));
  }

  @Test
  public void getEffectiveClusterConfig1_returnsExpectedConfig() {
    DomainConfig actual = new DomainConfig();

    DomainConfig want =
        (new DomainConfig())
            .withCluster(
                CLUSTER1,
                (new ClusterConfig())
                    .withClusterName(CLUSTER1)
                    .withServer(
                        SERVER1,
                        (new ClusteredServerConfig())
                            .withClusterName(CLUSTER1)
                            .withServerName(SERVER1)));

    getHelper().getEffectiveClusterConfig(getBuilder(), actual, CLUSTER1, getServers(SERVER1));

    assertThat(actual, equalTo(want));
  }

  @Test
  public void getEffectiveClusterConfig2_returnsExpectedConfig() {
    ClusterConfig want =
        (new ClusterConfig())
            .withClusterName(CLUSTER1)
            .withServer(
                SERVER1,
                (new ClusteredServerConfig()).withClusterName(CLUSTER1).withServerName(SERVER1));

    ClusterConfig actual =
        getHelper().getEffectiveClusterConfig(getBuilder(), CLUSTER1, getServers(SERVER1));

    assertThat(actual, equalTo(want));
  }

  @Test
  public void getEffectiveClusteredServerConfigs_returnsExpectedConfig() {
    ClusterConfig actual = (new ClusterConfig()).withClusterName(CLUSTER1);

    ClusterConfig want =
        (new ClusterConfig())
            .withClusterName(CLUSTER1)
            .withServer(
                SERVER1,
                (new ClusteredServerConfig()).withClusterName(CLUSTER1).withServerName(SERVER1))
            .withServer(
                SERVER2,
                (new ClusteredServerConfig()).withClusterName(CLUSTER1).withServerName(SERVER2));

    getHelper()
        .getEffectiveClusteredServerConfigs(getBuilder(), actual, getServers(SERVER1, SERVER2));

    assertThat(actual, equalTo(want));
  }

  @Test
  public void getEffectiveClusteredServerConfig_returnsExpectedConfig() {
    ClusterConfig actual = (new ClusterConfig()).withClusterName(CLUSTER1);

    ClusterConfig want =
        (new ClusterConfig())
            .withClusterName(CLUSTER1)
            .withServer(
                SERVER1,
                (new ClusteredServerConfig()).withClusterName(CLUSTER1).withServerName(SERVER1));

    getHelper().getEffectiveClusteredServerConfig(getBuilder(), actual, SERVER1);

    assertThat(actual, equalTo(want));
  }

  @Test
  public void getDomainConfigBuilder_domainV1ResourceVersion_returnsDomainConfigBuilderV1() {
    assertThat(
        getHelper().getDomainConfigBuilder(newDomainV1()), instanceOf(DomainConfigBuilderV1.class));
  }

  @Test(expected = AssertionError.class)
  public void getDomainConfigBuilder_missingResourceVersion_throwsException() {
    getHelper().getDomainConfigBuilder(newDomain());
  }

  @Test(expected = AssertionError.class)
  public void getDomainConfigBuilder_unsupportedResourceVersion_throwsException() {
    getHelper()
        .getDomainConfigBuilder(
            newDomain()
                .withMetadata(
                    newObjectMeta().putLabelsItem(RESOURCE_VERSION_LABEL, "NoSuchVersion")));
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
    public NonClusteredServerConfig getEffectiveNonClusteredServerConfig(String serverName) {
      return (new NonClusteredServerConfig()).withServerName(serverName);
    }

    @Override
    public ClusteredServerConfig getEffectiveClusteredServerConfig(
        String clusterName, String serverName) {
      return (new ClusteredServerConfig()).withClusterName(clusterName).withServerName(serverName);
    }

    @Override
    public ClusterConfig getEffectiveClusterConfig(String clusterName) {
      return (new ClusterConfig()).withClusterName(clusterName);
    }

    @Override
    public void updateDomainSpec(ClusterConfig clusterConfig) {}
  }

  private LifeCycleHelper getHelper() {
    return LifeCycleHelper.instance();
  }
}

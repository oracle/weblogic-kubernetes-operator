// Copyright (c) 2017, 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.wlsconfig;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.stream.Collectors;

import com.meterware.simplestub.Memento;
import oracle.kubernetes.operator.utils.WlsDomainConfigSupport;
import oracle.kubernetes.utils.TestUtils;
import org.hamcrest.Description;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.jayway.jsonpath.matchers.JsonPathMatchers.hasJsonPath;
import static oracle.kubernetes.operator.logging.MessageKeys.NO_WLS_SERVER_IN_CLUSTER;
import static oracle.kubernetes.operator.logging.MessageKeys.REPLICA_MORE_THAN_WLS_SERVERS;
import static oracle.kubernetes.operator.wlsconfig.WlsDomainConfigTest.WlsServerConfigMatcher.withServerConfig;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.arrayContaining;
import static org.hamcrest.Matchers.arrayContainingInAnyOrder;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.emptyArray;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class WlsDomainConfigTest {

  // The log messages to be checked during this test
  private static final String[] LOG_KEYS = {
    NO_WLS_SERVER_IN_CLUSTER, REPLICA_MORE_THAN_WLS_SERVERS
  };
  private final List<LogRecord> logRecords = new ArrayList<>();
  private final List<Memento> mementos = new ArrayList<>();
  private WlsDomainConfig wlsDomainConfig = new WlsDomainConfig("test-domain");
  private final WlsDomainConfigSupport support = new WlsDomainConfigSupport("test-domain");

  @BeforeEach
  public void setup() {
    mementos.add(
        TestUtils.silenceOperatorLogger()
            .collectLogMessages(logRecords, LOG_KEYS)
            .withLogLevel(Level.WARNING));
    mementos.add(TestUtils.silenceJsonPathLogger());
  }

  @AfterEach
  public void tearDown() {
    for (Memento memento : mementos) {
      memento.revert();
    }
  }

  @Test
  void verifyGetClusterConfigsDoesNotReturnNull() {
    WlsClusterConfig wlsClusterConfig = wlsDomainConfig.getClusterConfig("DockerCluster");
    assertNotNull(wlsClusterConfig);
    assertEquals(0, wlsClusterConfig.getClusterSize());
    assertEquals(0, wlsDomainConfig.getClusterConfigs().size(),
          "newly created empty WlsClusterConfig should not added to the clusterConfigs list");
  }

  @Test
  void verifyGetServerConfigsReturnNullIfNotFound() {
    assertNull(wlsDomainConfig.getServerConfig("noSuchServer"));
  }

  @Test
  void whenNoClustersDefined_returnEmptyArray() {
    support.addWlsServer("server1");
    support.addWlsServer("server2");

    assertThat(support.createDomainConfig().getClusterNames(), emptyArray());
  }

  @Test
  void whenOneClusterDefined_returnItsName() {
    support.addWlsCluster("cluster1", "ms1", "ms2", "ms3");
    support.addWlsServer("server2");

    assertThat(support.createDomainConfig().getClusterNames(), arrayContaining("cluster1"));
  }

  @Test
  void whenTwoClustersDefined_returnBothNames() {
    support.addWlsCluster("cluster1", "ms1", "ms2", "ms3");
    support.addWlsCluster("cluster2", "ms4", "ms5");

    assertThat(
        support.createDomainConfig().getClusterNames(),
        arrayContainingInAnyOrder("cluster1", "cluster2"));
  }

  @Test
  void whenTwoClustersDefined_returnReplicaLimits() {
    support.addWlsCluster("cluster1", "ms1", "ms2", "ms3");
    support.addWlsCluster("cluster2", "ms4", "ms5");

    assertThat(support.createDomainConfig().getReplicaLimit("cluster1"), equalTo(3));
    assertThat(support.createDomainConfig().getReplicaLimit("cluster2"), equalTo(2));
  }

  @Test
  void whenUnknownClusterName_returnZeroReplicaLimit() {
    assertThat(support.createDomainConfig().getReplicaLimit("cluster3"), equalTo(0));
  }

  @Test
  void whenStandaloneAndClusteredServersDefined_returnThemAll() {
    support.addWlsServer("standalone");
    support.addWlsCluster("static", "st1", "st2");
    support.addDynamicWlsCluster("dynamic", "ds1", "ds2", "ds3");

    assertThat(
          support.createDomainConfig().getAllServers().stream()
                .map(WlsServerConfig::getName)
                .collect(Collectors.toList()),
          containsInAnyOrder("standalone", "st1", "st2", "ds1", "ds2", "ds3"));
  }

  private boolean containsServer(WlsClusterConfig wlsClusterConfig, String serverName) {
    List<WlsServerConfig> serverConfigs = wlsClusterConfig.getServerConfigs();
    for (WlsServerConfig serverConfig : serverConfigs) {
      if (serverName.equals(serverConfig.getName())) {
        return true;
      }
    }
    return false;
  }

  private boolean containsNetworkAccessPoint(WlsServerConfig wlsServerConfig, String channelName) {
    List<NetworkAccessPoint> networkAccessPoints = wlsServerConfig.getNetworkAccessPoints();
    for (NetworkAccessPoint networkAccessPoint : networkAccessPoints) {
      if (channelName.equals(networkAccessPoint.getName())) {
        return true;
      }
    }
    return false;
  }

  @Test
  void whenTopologyGenerated_containsDomainValidFlag() {
    assertThat(wlsDomainConfig.toTopology(), hasJsonPath("domainValid", equalTo("true")));
  }

  @Test
  void whenTopologyGenerated_containsDomainName() {
    assertThat(wlsDomainConfig.toTopology(), hasJsonPath("domain.name", equalTo("test-domain")));
  }

  @Test
  void whenTopologyGenerated_containsAdminServerName() {
    wlsDomainConfig.withAdminServer("admin-server", "admin-host", 7001);

    assertThat(
        wlsDomainConfig.toTopology(), hasJsonPath("domain.adminServerName", equalTo("admin-server")));
  }

  @Test
  void whenTopologyGenerated_containsAdminServerSpec() {
    wlsDomainConfig.withAdminServer("admin-server", "admin-host", 7001);

    assertThat(
        wlsDomainConfig.toTopology(),
        hasJsonPath("domain.servers", withServerConfig("admin-server", "admin-host", 7001)));
  }

  @Test
  void whenYamlGenerated_containsClusterConfig() {
    wlsDomainConfig.withCluster(new WlsClusterConfig("cluster1"));

    assertThat(
        wlsDomainConfig.toTopology(),
        hasJsonPath("domain.configuredClusters[*].name", contains("cluster1")));
  }

  @Test
  void whenYamlGenerated_containsClusteredServerConfigs() {
    wlsDomainConfig.withCluster(
                new WlsClusterConfig("cluster1")
                    .addServerConfig(new WlsServerConfig("ms1", "host1", 8001))
                    .addServerConfig(new WlsServerConfig("ms2", "host2", 8001)));

    assertThat(
        wlsDomainConfig.toTopology(),
        hasJsonPath(
            "domain.configuredClusters[0].servers", withServerConfig("ms1", "host1", 8001)));
    assertThat(
        wlsDomainConfig.toTopology(),
        hasJsonPath(
            "domain.configuredClusters[0].servers", withServerConfig("ms2", "host2", 8001)));
  }

  @Test
  void containsServer_returnsTrue_forExistingStandaloneServer() {
    wlsDomainConfig.addWlsServer("ms1","host1", 8001);

    assertThat(wlsDomainConfig.containsServer("ms1"), equalTo(true));
  }

  @Test
  void containsServer_returnsTrue_forExistingConfiguredClusteredServer() {
    support.addWlsCluster("cluster-1", "ms1");

    assertThat(support.createDomainConfig().containsServer("ms1"), equalTo(true));
  }

  @Test
  void containsServer_returnsTrue_forExistingDynamicClusterServer() {
    support.addDynamicWlsCluster("cluster-1", "ms1");

    assertThat(support.createDomainConfig().containsServer("ms1"), equalTo(true));
  }

  @Test
  void containsServer_returnsFalse_forNonExistingServer() {
    support.addWlsCluster("cluster-1", "ms1");
    support.addDynamicWlsCluster("dynamic-cluster", "dyn1");
    support.addWlsServer("standalone");

    assertThat(support.createDomainConfig().containsServer("notthere"), equalTo(false));
  }

  @Test
  void containsServer_returnsFalse_forNullServerName() {
    assertThat(wlsDomainConfig.containsServer(null), equalTo(false));
  }

  @Test
  void containsCluster_returnsFalse_forNonExistingCluster() {
    support.addWlsCluster("cluster-1", "ms1");

    assertThat(support.createDomainConfig().containsCluster("notthere"), equalTo(false));
  }

  @Test
  void containsCluster_returnsTrue_forExistingCluster() {
    support.addWlsCluster("cluster-1", "ms1");

    assertThat(support.createDomainConfig().containsCluster("cluster-1"), equalTo(true));
  }

  @Test
  void containsCluster_returnsFalse_forNullServerName() {
    assertThat(wlsDomainConfig.containsCluster(null), equalTo(false));
  }

  @SuppressWarnings("unused")
  static class WlsServerConfigMatcher
      extends org.hamcrest.TypeSafeDiagnosingMatcher<
          java.util.List<java.util.Map<String, Object>>> {
    private final String expectedName;
    private final String expectedAddress;
    private final int expectedPort;

    private WlsServerConfigMatcher(String expectedName, String expectedAddress, int expectedPort) {
      this.expectedName = expectedName;
      this.expectedAddress = expectedAddress;
      this.expectedPort = expectedPort;
    }

    static WlsServerConfigMatcher withServerConfig(
        String serverName, String listenAddress, int port) {
      return new WlsServerConfigMatcher(serverName, listenAddress, port);
    }

    @Override
    protected boolean matchesSafely(
        List<Map<String, Object>> configs, Description mismatchDescription) {
      for (Map<String, Object> config : configs) {
        if (isExpectedConfig(config)) {
          return true;
        }
      }

      mismatchDescription.appendText(configs.toString());
      return false;
    }

    private boolean isExpectedConfig(Map<String, Object> item) {
      return expectedName.equals(item.get("name"))
          && expectedAddress.equals(item.get("listenAddress"))
          && Objects.equals(expectedPort, item.get("listenPort"));
    }

    @Override
    public void describeTo(Description description) {
      description
          .appendText("name: ")
          .appendValue(expectedName)
          .appendText(", listenAddress: ")
          .appendValue(expectedAddress)
          .appendText(", listenPort: ")
          .appendValue(expectedPort);
    }
  }
}

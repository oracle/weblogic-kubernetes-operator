// Copyright (c) 2017, 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.wlsconfig;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.LogRecord;

import com.meterware.simplestub.Memento;
import oracle.kubernetes.utils.TestUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static oracle.kubernetes.common.logging.MessageKeys.NO_WLS_SERVER_IN_CLUSTER;
import static oracle.kubernetes.common.logging.MessageKeys.REPLICA_MORE_THAN_WLS_SERVERS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;

class WlsClusterConfigTest {

  // The log messages to be checked during this test
  private static final String[] LOG_KEYS = {
    NO_WLS_SERVER_IN_CLUSTER, REPLICA_MORE_THAN_WLS_SERVERS
  };

  private final List<LogRecord> logRecords = new ArrayList<>();
  private Memento consoleControl;

  @SuppressWarnings("SameParameterValue")
  static WlsDynamicServersConfig createDynamicServersConfig(
      int clusterSize, int minClusterSize, String serverNamePrefix, String clusterName) {
    WlsServerConfig serverTemplate =
        new WlsServerConfig("serverTemplate1", "host1", null, 7001, 7002, null, null);
    List<String> serverNames = new ArrayList<>();
    final int startingServerNameIndex = 1;
    for (int i = 0; i < clusterSize; i++) {
      serverNames.add(serverNamePrefix + (i + startingServerNameIndex));
    }
    List<WlsServerConfig> serverConfigs =
        WlsDynamicServersConfig.createServerConfigsFromTemplate(
            serverNames, serverTemplate, clusterName, "base-domain", false);

    return new WlsDynamicServersConfig(
        clusterSize, clusterSize, minClusterSize, serverNamePrefix, false, null, serverTemplate, serverConfigs);
  }

  @BeforeEach
  public void setup() {
    consoleControl =
        TestUtils.silenceOperatorLogger()
            .collectLogMessages(logRecords, LOG_KEYS)
            .withLogLevel(Level.WARNING);
  }

  @AfterEach
  public void tearDown() {
    consoleControl.revert();
  }

  @Test
  void verifyClusterSizeIsSameAsNumberOfServers() {
    WlsClusterConfig wlsClusterConfig = new WlsClusterConfig("cluster1");
    wlsClusterConfig.addServerConfig(createWlsServerConfig("ms-0", null));
    wlsClusterConfig.addServerConfig(createWlsServerConfig("ms-1", 8011));
    assertThat(wlsClusterConfig.getClusterSize(), equalTo(2));
  }

  @Test
  void verifyClusterSizeIs0IfNoServers() {
    WlsClusterConfig wlsClusterConfig = new WlsClusterConfig("cluster1");
    assertThat(wlsClusterConfig.getClusterSize(), equalTo(0));
  }

  @Test
  void verifyHasStaticServersIsFalseIfNoServers() {
    WlsClusterConfig wlsClusterConfig = new WlsClusterConfig("cluster1");
    assertThat(wlsClusterConfig.hasStaticServers(), is(false));
  }

  @Test
  void verifyHasStaticServersIsTrueIfStaticServers() {
    WlsClusterConfig wlsClusterConfig = new WlsClusterConfig("cluster1");
    wlsClusterConfig.addServerConfig(createWlsServerConfig("ms-0", null));
    assertThat(wlsClusterConfig.hasStaticServers(), is(true));
  }

  @Test
  void verifyHasDynamicServersIsFalseIfNoDynamicServers() {
    WlsClusterConfig wlsClusterConfig = new WlsClusterConfig("cluster1");
    assertThat(wlsClusterConfig.hasDynamicServers(), is(false));
  }

  @Test
  void verifyHasDynamicServersIsTrueForDynamicCluster() {
    WlsClusterConfig wlsClusterConfig =
        new WlsClusterConfig("cluster1", createDynamicServersConfig(2, 1, "ms-", "cluster1"));
    assertThat(wlsClusterConfig.hasDynamicServers(), is(true));
  }

  @Test
  void verifyHasStaticServersIsFalseForDynamicCluster() {
    WlsClusterConfig wlsClusterConfig =
        new WlsClusterConfig("cluster1", createDynamicServersConfig(2, 1, "ms-", "cluster1"));
    assertThat(wlsClusterConfig.hasStaticServers(), is(false));
  }

  @Test
  void verifyHasDynamicServersIsTrueForMixedCluster() {
    WlsClusterConfig wlsClusterConfig =
        new WlsClusterConfig("cluster1", createDynamicServersConfig(2, 1, "ms-", "cluster1"));
    wlsClusterConfig.addServerConfig(createWlsServerConfig("mss-0", 8011));
    assertThat(wlsClusterConfig.hasDynamicServers(), is(true));
  }

  @Test
  void verifyHasStaticServersIsTrueForMixedCluster() {
    WlsClusterConfig wlsClusterConfig =
        new WlsClusterConfig("cluster1", createDynamicServersConfig(2, 1, "ms-", "cluster1"));
    wlsClusterConfig.addServerConfig(createWlsServerConfig("mss-0", 8011));
    assertThat(wlsClusterConfig.hasStaticServers(), is(true));
  }

  @Test
  void verifyClusterSizeIsSameAsNumberOfServersPlusDynamicSizeForMixedCluster() {
    WlsClusterConfig wlsClusterConfig =
        new WlsClusterConfig("cluster1", createDynamicServersConfig(2, 1, "ms-", "cluster1"));
    wlsClusterConfig.addServerConfig(createWlsServerConfig("mss-0", 8011));

    assertThat(wlsClusterConfig.getClusterSize(), equalTo(3));
  }

  @Test
  void verifyClusterSizeIsSameAsDynamicSizeForDynamicCluster() {
    WlsClusterConfig wlsClusterConfig =
        new WlsClusterConfig("cluster1", createDynamicServersConfig(2, 1, "ms-", "cluster1"));

    assertThat(wlsClusterConfig.getClusterSize(), equalTo(2));
  }

  @Test
  void verifyDynamicClusterSizeIsSameAsNumberOfDynamicServers() {
    WlsClusterConfig wlsClusterConfig =
        new WlsClusterConfig("cluster1", createDynamicServersConfig(2, 1, "ms-", "cluster1"));
    assertThat(wlsClusterConfig.getClusterSize(), equalTo(2));
    assertThat(wlsClusterConfig.getConfiguredClusterSize(), equalTo(0));
    assertThat(wlsClusterConfig.getDynamicClusterSize(), equalTo(2));
  }

  @Test
  void verifyGetServerConfigsReturnListOfAllServerConfigs() {
    WlsClusterConfig wlsClusterConfig = new WlsClusterConfig("cluster1");
    wlsClusterConfig.addServerConfig(createWlsServerConfig("ms-0", 8011));
    wlsClusterConfig.addServerConfig(createWlsServerConfig("ms-1", 8012));

    List<WlsServerConfig> wlsServerConfigList = wlsClusterConfig.getServerConfigs();
    assertThat(wlsServerConfigList.size(), equalTo(2));
    assertThat(containsServer(wlsClusterConfig, "ms-0"), is(true));
    assertThat(containsServer(wlsClusterConfig, "ms-1"), is(true));
  }

  @Test
  void verifyGetServerConfigsReturnEmptyServerListForDynamicServersWithClusterSizeOf0() {
    WlsClusterConfig wlsClusterConfig =
        new WlsClusterConfig("cluster1", createDynamicServersConfig(0, 0, "ms-", "cluster1"));

    assertThat(wlsClusterConfig.getServerConfigs(), empty());
  }

  @Test
  void verifyGetServerConfigsReturnListOfAllServerConfigsWithDynamicServers() {
    WlsClusterConfig wlsClusterConfig =
        new WlsClusterConfig("cluster1", createDynamicServersConfig(3, 1, "ms-", "cluster1"));
    wlsClusterConfig.addServerConfig(createWlsServerConfig("static-0", 8011));
    wlsClusterConfig.addServerConfig(createWlsServerConfig("static-1", 8012));

    List<WlsServerConfig> wlsServerConfigList = wlsClusterConfig.getServerConfigs();
    assertThat(wlsServerConfigList, hasSize(5));
    // verify dynamic servers
    assertThat(containsServer(wlsClusterConfig, "ms-1"), is(true));
    assertThat(containsServer(wlsClusterConfig, "ms-2"), is(true));
    assertThat(containsServer(wlsClusterConfig, "ms-3"), is(true));
    // verify static servers
    assertThat(containsServer(wlsClusterConfig, "static-0"), is(true));
    assertThat(containsServer(wlsClusterConfig, "static-1"), is(true));
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

  @Test
  void verifyClusterSizeForStaticCluster() {
    WlsClusterConfig wlsClusterConfig = new WlsClusterConfig("cluster1");
    wlsClusterConfig.addServerConfig(createWlsServerConfig("ms-0", 8011));

    assertThat(wlsClusterConfig.getClusterSize(), equalTo(1));
  }

  @Test
  void verifyClusterSizeForDynamicCluster() {
    WlsDynamicServersConfig wlsDynamicServersConfig =
        createDynamicServersConfig(1, 1, "ms-", "cluster1");
    WlsClusterConfig wlsClusterConfig = new WlsClusterConfig("cluster1", wlsDynamicServersConfig);

    assertThat(wlsClusterConfig.getClusterSize(), equalTo(1));
  }

  @Test
  void verifyClusterSizeForMixedCluster() {
    WlsDynamicServersConfig wlsDynamicServersConfig =
        createDynamicServersConfig(1, 1, "ms-", "cluster1");
    WlsClusterConfig wlsClusterConfig = new WlsClusterConfig("cluster1", wlsDynamicServersConfig);
    wlsClusterConfig.addServerConfig(createWlsServerConfig("ms-0", 8011));

    assertThat(wlsClusterConfig.getClusterSize(), equalTo(2));
  }

  private WlsServerConfig createWlsServerConfig(String serverName, Integer listenPort) {
    return new WlsServerConfig(serverName, null, null, listenPort, null, null, null);
  }

}

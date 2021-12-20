// Copyright (c) 2017, 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.wlsconfig;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.LogRecord;

import com.meterware.simplestub.Memento;
import oracle.kubernetes.operator.work.NextAction;
import oracle.kubernetes.operator.work.Packet;
import oracle.kubernetes.operator.work.Step;
import oracle.kubernetes.utils.TestUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static oracle.kubernetes.operator.logging.MessageKeys.NO_WLS_SERVER_IN_CLUSTER;
import static oracle.kubernetes.operator.logging.MessageKeys.REPLICA_MORE_THAN_WLS_SERVERS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

class WlsClusterConfigTest {

  // The log messages to be checked during this test
  private static final String[] LOG_KEYS = {
    NO_WLS_SERVER_IN_CLUSTER, REPLICA_MORE_THAN_WLS_SERVERS
  };

  Field nextField;
  private final List<LogRecord> logRecords = new ArrayList<>();
  private Memento consoleControl;

  @SuppressWarnings("SameParameterValue")
  static WlsDynamicServersConfig createDynamicServersConfig(
      int clusterSize, int maxClusterSize, int minClusterSize, String serverNamePrefix, String clusterName) {
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
        clusterSize, maxClusterSize, minClusterSize, serverNamePrefix, false, null, serverTemplate, serverConfigs);
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
    wlsClusterConfig.addServerConfig(createWlsServerConfig("ms-0", null, null));
    wlsClusterConfig.addServerConfig(createWlsServerConfig("ms-1", 8011, null));
    assertEquals(2, wlsClusterConfig.getClusterSize());
  }

  @Test
  void verifyMaxClusterSizeIsSameAsNumberOfServers() {
    WlsClusterConfig wlsClusterConfig = new WlsClusterConfig("cluster1");
    wlsClusterConfig.addServerConfig(createWlsServerConfig("ms-0", null, null));
    wlsClusterConfig.addServerConfig(createWlsServerConfig("ms-1", 8011, null));
    assertEquals(2, wlsClusterConfig.getMaxClusterSize());
  }

  @Test
  void verifyClusterSizeIs0IfNoServers() {
    WlsClusterConfig wlsClusterConfig = new WlsClusterConfig("cluster1");
    assertEquals(0, wlsClusterConfig.getClusterSize());
  }

  @Test
  void verifyHasStaticServersIsFalseIfNoServers() {
    WlsClusterConfig wlsClusterConfig = new WlsClusterConfig("cluster1");
    assertFalse(wlsClusterConfig.hasStaticServers());
  }

  @Test
  void verifyHasStaticServersIsTrueIfStaticServers() {
    WlsClusterConfig wlsClusterConfig = new WlsClusterConfig("cluster1");
    wlsClusterConfig.addServerConfig(createWlsServerConfig("ms-0", null, null));
    assertTrue(wlsClusterConfig.hasStaticServers());
  }

  @Test
  void verifyHasDynamicServersIsFalseIfNoDynamicServers() {
    WlsClusterConfig wlsClusterConfig = new WlsClusterConfig("cluster1");
    assertFalse(wlsClusterConfig.hasDynamicServers());
  }

  @Test
  void verifyHasDynamicServersIsTrueForDynamicCluster() {
    WlsClusterConfig wlsClusterConfig =
        new WlsClusterConfig("cluster1", createDynamicServersConfig(2, 5, 1, "ms-", "cluster1"));
    assertTrue(wlsClusterConfig.hasDynamicServers());
  }

  @Test
  void verifyHasStaticServersIsFalseForDynamicCluster() {
    WlsClusterConfig wlsClusterConfig =
        new WlsClusterConfig("cluster1", createDynamicServersConfig(2, 5, 1, "ms-", "cluster1"));
    assertFalse(wlsClusterConfig.hasStaticServers());
  }

  @Test
  void verifyHasDynamicServersIsTrueForMixedCluster() {
    WlsClusterConfig wlsClusterConfig =
        new WlsClusterConfig("cluster1", createDynamicServersConfig(2, 5, 1, "ms-", "cluster1"));
    wlsClusterConfig.addServerConfig(createWlsServerConfig("mss-0", 8011, null));
    assertTrue(wlsClusterConfig.hasDynamicServers());
  }

  @Test
  void verifyHasStaticServersIsTrueForMixedCluster() {
    WlsClusterConfig wlsClusterConfig =
        new WlsClusterConfig("cluster1", createDynamicServersConfig(2, 5, 1, "ms-", "cluster1"));
    wlsClusterConfig.addServerConfig(createWlsServerConfig("mss-0", 8011, null));
    assertTrue(wlsClusterConfig.hasStaticServers());
  }

  @Test
  void verifyMaxClusterSizeIsSameAsNumberOfServersPlusDynamicSizeForMixedCluster() {
    WlsClusterConfig wlsClusterConfig =
        new WlsClusterConfig("cluster1", createDynamicServersConfig(2, 5, 1, "ms-", "cluster1"));
    wlsClusterConfig.addServerConfig(createWlsServerConfig("mss-0", 8011, null));

    assertEquals(6, wlsClusterConfig.getMaxClusterSize());
  }

  @Test
  void verifyMaxClusterSizeIsSameAsDynamicSizeForDynamicCluster() {
    WlsClusterConfig wlsClusterConfig =
        new WlsClusterConfig("cluster1", createDynamicServersConfig(2, 5, 1, "ms-", "cluster1"));

    assertEquals(5, wlsClusterConfig.getMaxClusterSize());
  }

  @Test
  void verifyDynamicClusterSizeIsSameAsNumberOfDynamicServers() {
    WlsClusterConfig wlsClusterConfig =
        new WlsClusterConfig("cluster1", createDynamicServersConfig(2, 5, 1, "ms-", "cluster1"));
    assertEquals(0, wlsClusterConfig.getClusterSize());
    assertEquals(2, wlsClusterConfig.getDynamicClusterSize());
    assertEquals(5, wlsClusterConfig.getMaxDynamicClusterSize());
    assertEquals(1, wlsClusterConfig.getMinDynamicClusterSize());
  }

  @Test
  void verifyDynamicClusterSizeIsNeg1IfNoDynamicServers() {
    WlsClusterConfig wlsClusterConfig = new WlsClusterConfig("cluster1");
    assertEquals(-1, wlsClusterConfig.getDynamicClusterSize());
  }

  @Test
  void verifyMinDynamicClusterSizeIsNeg1IfNoDynamicServers() {
    WlsClusterConfig wlsClusterConfig = new WlsClusterConfig("cluster1");
    assertEquals(-1, wlsClusterConfig.getMinDynamicClusterSize());
  }

  @Test
  void verifyGetServerConfigsReturnListOfAllServerConfigs() {
    WlsClusterConfig wlsClusterConfig = new WlsClusterConfig("cluster1");
    wlsClusterConfig.addServerConfig(createWlsServerConfig("ms-0", 8011, null));
    wlsClusterConfig.addServerConfig(createWlsServerConfig("ms-1", 8012, null));

    List<WlsServerConfig> wlsServerConfigList = wlsClusterConfig.getServerConfigs();
    assertEquals(2, wlsServerConfigList.size());
    assertTrue(containsServer(wlsClusterConfig, "ms-0"));
    assertTrue(containsServer(wlsClusterConfig, "ms-1"));
  }

  @Test
  void verifyGetServerConfigsReturnEmptyServerListForDynamicServersWithClusterSizeOf0() {
    WlsClusterConfig wlsClusterConfig =
        new WlsClusterConfig("cluster1", createDynamicServersConfig(0, 0, 0, "ms-", "cluster1"));

    assertTrue(wlsClusterConfig.getServerConfigs().isEmpty());
  }

  @Test
  void verifyGetServerConfigsReturnListOfAllServerConfigsWithDynamicServers() {
    WlsClusterConfig wlsClusterConfig =
        new WlsClusterConfig("cluster1", createDynamicServersConfig(3, 5, 1, "ms-", "cluster1"));
    wlsClusterConfig.addServerConfig(createWlsServerConfig("static-0", 8011, null));
    wlsClusterConfig.addServerConfig(createWlsServerConfig("static-1", 8012, null));

    List<WlsServerConfig> wlsServerConfigList = wlsClusterConfig.getServerConfigs();
    assertEquals(5, wlsServerConfigList.size());
    // verify dynamic servers
    assertTrue(containsServer(wlsClusterConfig, "ms-1"));
    assertTrue(containsServer(wlsClusterConfig, "ms-2"));
    assertTrue(containsServer(wlsClusterConfig, "ms-3"));
    // verify static servers
    assertTrue(containsServer(wlsClusterConfig, "static-0"));
    assertTrue(containsServer(wlsClusterConfig, "static-1"));
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
  void verifyMaxClusterSizeForStaticCluster() {
    WlsClusterConfig wlsClusterConfig = new WlsClusterConfig("cluster1");
    wlsClusterConfig.addServerConfig(createWlsServerConfig("ms-0", 8011, null));

    assertThat(wlsClusterConfig.getMaxClusterSize(), equalTo(1));
  }

  @Test
  void verifyMaxClusterSizeForDynamicCluster() {
    WlsDynamicServersConfig wlsDynamicServersConfig =
        createDynamicServersConfig(1, 1, 1, "ms-", "cluster1");
    WlsClusterConfig wlsClusterConfig = new WlsClusterConfig("cluster1", wlsDynamicServersConfig);

    assertThat(wlsClusterConfig.getMaxClusterSize(), equalTo(1));
  }

  @Test
  void verifyMaxClusterSizeForMixedCluster() {
    WlsDynamicServersConfig wlsDynamicServersConfig =
        createDynamicServersConfig(1, 1, 1, "ms-", "cluster1");
    WlsClusterConfig wlsClusterConfig = new WlsClusterConfig("cluster1", wlsDynamicServersConfig);
    wlsClusterConfig.addServerConfig(createWlsServerConfig("ms-0", 8011, null));

    assertThat(wlsClusterConfig.getMaxClusterSize(), equalTo(2));
  }

  private WlsServerConfig createWlsServerConfig(
      String serverName, Integer listenPort, String listenAddress) {
    Map<String, Object> serverConfigMap = new HashMap<>();
    serverConfigMap.put("name", serverName);
    serverConfigMap.put("listenPort", listenPort);
    serverConfigMap.put("listenAddress", listenAddress);
    return new WlsServerConfig(serverName, listenAddress, null, listenPort, null, null, null);
  }

  Step getNext(Step step) throws IllegalAccessException, NoSuchFieldException {
    if (nextField == null) {
      nextField = Step.class.getDeclaredField("next");
      nextField.setAccessible(true);
    }
    return (Step) nextField.get(step);
  }

  static class MockStep extends Step {
    public MockStep(Step next) {
      super(next);
    }

    @Override
    public NextAction apply(Packet packet) {
      return null;
    }
  }
}

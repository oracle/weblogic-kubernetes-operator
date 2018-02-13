// Copyright 2017, 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.wlsconfig;

import oracle.kubernetes.operator.domain.model.oracle.kubernetes.weblogic.domain.v1.ClusterStartup;
import org.junit.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

/**
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
 */
public class WlsClusterConfigTest {

  @Test
  public void verifyClusterSizeIsSameAsNumberOfServers() throws Exception {
    WlsClusterConfig wlsClusterConfig = new WlsClusterConfig("cluster1");
    wlsClusterConfig.addServerConfig(createWlsServerConfig("ms-0", null, null));
    wlsClusterConfig.addServerConfig(createWlsServerConfig("ms-1", 8011, null));
    assertEquals(2, wlsClusterConfig.getClusterSize());
  }

  @Test
  public void verifyClusterSizeIs0IfNoServers() throws Exception {
    WlsClusterConfig wlsClusterConfig = new WlsClusterConfig("cluster1");
    assertEquals(0, wlsClusterConfig.getClusterSize());
  }

  @Test
  public void verifyHasStaticServersIsFalseIfNoServers() throws Exception {
    WlsClusterConfig wlsClusterConfig = new WlsClusterConfig("cluster1");
    assertFalse(wlsClusterConfig.hasStaticServers());
  }

  @Test
  public void verifyHasStaticServersIsTrueIfStaticServers() throws Exception {
    WlsClusterConfig wlsClusterConfig = new WlsClusterConfig("cluster1");
    wlsClusterConfig.addServerConfig(createWlsServerConfig("ms-0", null, null));
    assertTrue(wlsClusterConfig.hasStaticServers());
  }

  @Test
  public void verifyHasDynamicServersIsFalsefNoDynamicServers() throws Exception {
    WlsClusterConfig wlsClusterConfig = new WlsClusterConfig("cluster1");
    assertFalse(wlsClusterConfig.hasDynamicServers());
  }

  @Test
  public void verifyHasDynamicServersIsTrueForDynamicCluster() throws Exception {
    WlsClusterConfig wlsClusterConfig = new WlsClusterConfig("cluster1",
            createDynamicServersConfig(2, 5, "ms-", "clsuter1"));
    assertTrue(wlsClusterConfig.hasDynamicServers());
  }

  @Test
  public void verifyHasStaticServersIsFalseForDynamicCluster() throws Exception {
    WlsClusterConfig wlsClusterConfig = new WlsClusterConfig("cluster1",
            createDynamicServersConfig(2, 5, "ms-", "clsuter1"));
    assertFalse(wlsClusterConfig.hasStaticServers());
  }

  @Test
  public void verifyHasDynamicServersIsTrueForMixedCluster() throws Exception {
    WlsClusterConfig wlsClusterConfig = new WlsClusterConfig("cluster1",
            createDynamicServersConfig(2, 5, "ms-", "clsuter1"));
    wlsClusterConfig.addServerConfig(createWlsServerConfig("mss-0", 8011, null));
    assertTrue(wlsClusterConfig.hasDynamicServers());
  }

  @Test
  public void verifyHasStaticServersIsTrueForMixedCluster() throws Exception {
    WlsClusterConfig wlsClusterConfig = new WlsClusterConfig("cluster1",
            createDynamicServersConfig(2, 5, "ms-", "clsuter1"));
    wlsClusterConfig.addServerConfig(createWlsServerConfig("mss-0", 8011, null));
    assertTrue(wlsClusterConfig.hasStaticServers());
  }

  @Test
  public void verifyDynamicClusterSizeIsSameAsNumberOfDynamicServers() throws Exception {
    WlsClusterConfig wlsClusterConfig = new WlsClusterConfig("cluster1",
    createDynamicServersConfig(2, 5, "ms-", "clsuter1"));
    assertEquals(0, wlsClusterConfig.getClusterSize());
    assertEquals(2, wlsClusterConfig.getDynamicClusterSize());
    assertEquals(5, wlsClusterConfig.getMaxDynamicClusterSize());
  }

  @Test
  public void verifyDynamicClusterSizeIsNeg1IfNoDynamicServers() throws Exception {
    WlsClusterConfig wlsClusterConfig = new WlsClusterConfig("cluster1");
    assertEquals(-1, wlsClusterConfig.getDynamicClusterSize());
  }

  @Test
  public void verifyGetServerConfigsReturnListOfAllServerConfigs() throws Exception {
    WlsClusterConfig wlsClusterConfig = new WlsClusterConfig("cluster1");
    wlsClusterConfig.addServerConfig(createWlsServerConfig("ms-0", 8011, null));
    wlsClusterConfig.addServerConfig(createWlsServerConfig("ms-1", 8012, null));

    List<WlsServerConfig> wlsServerConfigList = wlsClusterConfig.getServerConfigs();
    assertEquals(2, wlsServerConfigList.size());
    assertTrue(containsServer(wlsClusterConfig, "ms-0"));
    assertTrue(containsServer(wlsClusterConfig, "ms-1"));
  }

  @Test
  public void verifyGetServerConfigsReturnListOfAllServerConfigsWithDynamicServers() throws Exception {
    WlsClusterConfig wlsClusterConfig = new WlsClusterConfig("cluster1",
            createDynamicServersConfig(3, 5, "ms-", "clsuter1"));
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
  public void verifyValidateClusterStartupWarnsIfNoServersInCluster() throws Exception {
    WlsClusterConfig wlsClusterConfig = new WlsClusterConfig("cluster1");
    ClusterStartup cs = new ClusterStartup().clusterName("cluster1").replicas(1);
    TestUtil.LogHandlerImpl handler = null;
    try {
      handler = TestUtil.setupLogHandler(wlsClusterConfig);
      wlsClusterConfig.validateClusterStartup(cs);
      assertTrue("Message logged: " + handler.getAllFormattedMessage(), handler.hasWarningMessageWithSubString("No servers configured in weblogic cluster with name cluster1"));
    } finally {
      TestUtil.removeLogHandler(wlsClusterConfig, handler);
    }
  }

  @Test
  public void verifyValidateClusterStartupWarnsIfReplicasTooHigh() throws Exception {
    WlsClusterConfig wlsClusterConfig = new WlsClusterConfig("cluster1");
    wlsClusterConfig.addServerConfig(createWlsServerConfig("ms-0", 8011, null));
    ClusterStartup cs = new ClusterStartup().clusterName("cluster1").replicas(2);
    TestUtil.LogHandlerImpl handler = null;
    try {
      handler = TestUtil.setupLogHandler(wlsClusterConfig);
      wlsClusterConfig.validateClusterStartup(cs);
      assertTrue("Message logged: " + handler.getAllFormattedMessage(), handler.hasWarningMessageWithSubString("replicas in clusterStartup for cluster cluster1 is specified with a value of 2 which is larger than the number of configured WLS servers in the cluster: 1"));
    } finally {
      TestUtil.removeLogHandler(wlsClusterConfig, handler);
    }
  }

  @Test
  public void verifyValidateClusterStartupDoesNotWarnIfDynamicCluster() throws Exception {
    WlsDynamicServersConfig wlsDynamicServersConfig = createDynamicServersConfig(1, 1, "ms-", "cluster1");
    WlsClusterConfig wlsClusterConfig = new WlsClusterConfig("cluster1", wlsDynamicServersConfig);
    ClusterStartup cs = new ClusterStartup().clusterName("cluster1").replicas(2);
    TestUtil.LogHandlerImpl handler = null;
    try {
      handler = TestUtil.setupLogHandler(wlsClusterConfig);
      wlsClusterConfig.validateClusterStartup(cs);
      assertFalse("No message should be logged, but found: " + handler.getAllFormattedMessage(), handler.hasWarningMessageLogged());
    } finally {
      TestUtil.removeLogHandler(wlsClusterConfig, handler);
    }
  }

  @Test
  public void verifyGetUpdateDynamicClusterSizeUrlIncludesClusterName() {
    WlsDynamicServersConfig wlsDynamicServersConfig = createDynamicServersConfig(1, 1, "ms-", "cluster1");
    WlsClusterConfig wlsClusterConfig = new WlsClusterConfig("cluster1", wlsDynamicServersConfig);
    assertEquals(wlsClusterConfig.getUpdateDynamicClusterSizeUrl(), "/management/weblogic/latest/edit/clusters/cluster1/dynamicServers");
  }

  @Test
  public void verifyGetUpdateDynamicClusterSizePayload1() {
    WlsDynamicServersConfig wlsDynamicServersConfig = createDynamicServersConfig(1, 5, "ms-", "cluster1");
    WlsClusterConfig wlsClusterConfig = new WlsClusterConfig("cluster1", wlsDynamicServersConfig);
    assertEquals(wlsClusterConfig.getUpdateDynamicClusterSizePayload(2), "{ dynamicClusterSize: 2,  maxDynamicClusterSize: 5 }");
  }

  @Test
  public void verifyGetUpdateDynamicClusterSizePayload2() {
    WlsDynamicServersConfig wlsDynamicServersConfig = createDynamicServersConfig(2, 5, "ms-", "cluster1");
    WlsClusterConfig wlsClusterConfig = new WlsClusterConfig("cluster1", wlsDynamicServersConfig);
    assertEquals(wlsClusterConfig.getUpdateDynamicClusterSizePayload(1), "{ dynamicClusterSize: 1,  maxDynamicClusterSize: 5 }");
  }

  @Test
  public void verifyGetUpdateDynamicClusterSizePayload3() {
    WlsDynamicServersConfig wlsDynamicServersConfig = createDynamicServersConfig(2, 5, "ms-", "cluster1");
    WlsClusterConfig wlsClusterConfig = new WlsClusterConfig("cluster1", wlsDynamicServersConfig);
    assertEquals(wlsClusterConfig.getUpdateDynamicClusterSizePayload(8), "{ dynamicClusterSize: 8,  maxDynamicClusterSize: 8 }");
  }

  private WlsServerConfig createWlsServerConfig(String serverName, Integer listenPort, String listenAddress) {
    Map<String, Object> serverConfigMap = new HashMap<>();
    serverConfigMap.put("name", serverName);
    serverConfigMap.put("listenPort", listenPort);
    serverConfigMap.put("listenAddress", listenAddress);
    return WlsServerConfig.create(serverConfigMap);
  }

  private WlsDynamicServersConfig createDynamicServersConfig(int clusterSize, int maxClusterSize,
                                                             String serverNamePrefix, String clusterName) {
    WlsServerConfig serverTemplate = new WlsServerConfig("serverTemplate1", 7001, "host1",
            7002, false, null, null);
    List<String> serverNames = new ArrayList<>();
    final int startingServerNameIndex = 1;
    for (int i=0; i<clusterSize; i++) {
      serverNames.add(serverNamePrefix + (i + startingServerNameIndex));
    }
    List<WlsServerConfig> serverConfigs = WlsDynamicServersConfig.createServerConfigsFromTemplate(
            serverNames, serverTemplate, clusterName, "base-domain", false);

    return new WlsDynamicServersConfig(clusterSize, maxClusterSize, serverNamePrefix,
            false, serverTemplate, serverConfigs);

  }
}

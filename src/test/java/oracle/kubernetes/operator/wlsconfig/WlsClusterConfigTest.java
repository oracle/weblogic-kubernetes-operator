// Copyright 2017, Oracle Corporation and/or its affiliates.  All rights reserved.

package oracle.kubernetes.operator.wlsconfig;

import oracle.kubernetes.operator.domain.model.oracle.kubernetes.weblogic.domain.v1.ClusterStartup;
import org.junit.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

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
  public void verifyGetServerConfigsReturnListOfAllServerConfigs() throws Exception {
    WlsClusterConfig wlsClusterConfig = new WlsClusterConfig("cluster1");
    wlsClusterConfig.addServerConfig(createWlsServerConfig("ms-0", 8011, null));
    wlsClusterConfig.addServerConfig(createWlsServerConfig("ms-1", 8012, null));

    List<WlsServerConfig> wlsServerConfigList = wlsClusterConfig.getServerConfigs();
    assertEquals(2, wlsServerConfigList.size());
    assertTrue(containsServer(wlsClusterConfig, "ms-0"));
    assertTrue(containsServer(wlsClusterConfig, "ms-1"));
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

  private WlsServerConfig createWlsServerConfig(String serverName, Integer listenPort, String listenAddress) {
    Map<String, Object> serverConfigMap = new HashMap<>();
    serverConfigMap.put("name", serverName);
    serverConfigMap.put("listenPort", listenPort);
    serverConfigMap.put("listenAddress", listenAddress);
    return new WlsServerConfig(serverConfigMap);
  }
}

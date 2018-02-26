// Copyright 2017, Oracle Corporation and/or its affiliates.  All rights reserved.

package oracle.kubernetes.operator.wlsconfig;

import oracle.kubernetes.operator.domain.model.oracle.kubernetes.weblogic.domain.v1.ClusterStartup;
import oracle.kubernetes.operator.domain.model.oracle.kubernetes.weblogic.domain.v1.DomainSpec;
import org.junit.Test;

import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
 */
public class WlsDomainConfigTest {

  @Test
  public void verifyServersLoadedFromJsonString() throws Exception {
    WlsDomainConfig wlsDomainConfig = WlsDomainConfig.create().load(JSON_STRING_1_CLUSTER);
    Map<String, WlsClusterConfig> wlsClusterConfigList = wlsDomainConfig.getClusterConfigs();
    assertEquals(1, wlsClusterConfigList.size());

    WlsClusterConfig wlsClusterConfig = wlsDomainConfig.getClusterConfig("DockerCluster");
    assertEquals(5, wlsClusterConfig.getClusterSize());
    for (WlsServerConfig wlsServerConfig : wlsClusterConfig.getServerConfigs()) {
      assertEquals(wlsServerConfig.getName() + ".wls-subdomain.default.svc.cluster.local", wlsServerConfig.getListenAddress());
      assertEquals(new Integer(8011), wlsServerConfig.getListenPort());
    }
    assertEquals(6, wlsDomainConfig.getServerConfigs().size());
    assertEquals("AdminServer", wlsDomainConfig.getServerConfig("AdminServer").getName());
  }

  @Test
  public void verifyServerWithNoChannelLoadedFromJsonString() throws Exception {
    WlsDomainConfig wlsDomainConfig = WlsDomainConfig.create().load(JSON_STRING_1_CLUSTER);

    WlsServerConfig serverConfig = wlsDomainConfig.getServerConfig("ms-1");
    assertEquals(0, serverConfig.getNetworkAccessPoints().size());
  }

  @Test
  public void verifyServerChannelsLoadedFromJsonString() throws Exception {
    WlsDomainConfig wlsDomainConfig = WlsDomainConfig.create().load(JSON_STRING_1_CLUSTER);

    WlsServerConfig serverConfig = wlsDomainConfig.getServerConfig("ms-0");
    assertEquals(3, serverConfig.getNetworkAccessPoints().size());
    assertTrue(containsNetworkAccessPoint(serverConfig, "Channel-0"));
    assertTrue(containsNetworkAccessPoint(serverConfig, "Channel-1"));
    assertTrue(containsNetworkAccessPoint(serverConfig, "Channel-2"));

    for (NetworkAccessPoint networkAccessPoint: serverConfig.getNetworkAccessPoints()) {
      String expectedProtocol = null;
      Integer expectedListenPort = null;
      if ("Channel-0".equals(networkAccessPoint.getName())) {
        expectedProtocol = "t3";
        expectedListenPort = 8012;
      } else if ("Channel-1".equals(networkAccessPoint.getName())) {
        expectedProtocol = "t3";
        expectedListenPort = 8013;
      } else if ("Channel-2".equals(networkAccessPoint.getName())) {
        expectedProtocol = "t3s";
        expectedListenPort = 8014;
      }
      assertEquals("protocol for " + networkAccessPoint.getName() + " not loaded properly", expectedProtocol, networkAccessPoint.getProtocol());
      assertEquals("listen port for " + networkAccessPoint.getName() + " not loaded properly", expectedListenPort, networkAccessPoint.getListenPort());
    }
  }
  @Test
  public void verifyMultipleClustersLoadedFromJsonString() throws Exception {
    WlsDomainConfig wlsDomainConfig = WlsDomainConfig.create().load(JSON_STRING_2_CLUSTERS);
    Map<String, WlsClusterConfig> wlsClusterConfigList = wlsDomainConfig.getClusterConfigs();
    assertEquals(2, wlsClusterConfigList.size());

    WlsClusterConfig wlsClusterConfig = wlsDomainConfig.getClusterConfig("DockerCluster");
    assertEquals(3, wlsClusterConfig.getClusterSize());
    assertTrue(containsServer(wlsClusterConfig, "ms-0"));
    assertTrue(containsServer(wlsClusterConfig, "ms-1"));
    assertTrue(containsServer(wlsClusterConfig, "ms-2"));

    WlsClusterConfig wlsClusterConfig2 = wlsDomainConfig.getClusterConfig("DockerCluster2");
    assertEquals(2, wlsClusterConfig2.getClusterSize());
    assertTrue(containsServer(wlsClusterConfig2, "ms-3"));
    assertTrue(containsServer(wlsClusterConfig2, "ms-4"));
  }

  @Test
  public void verifyGetClusterConfigsDoesNotReturnNull() throws Exception {
    WlsDomainConfig wlsDomainConfig = WlsDomainConfig.create();
    WlsClusterConfig wlsClusterConfig = wlsDomainConfig.getClusterConfig("DockerCluster");
    assertNotNull(wlsClusterConfig);
    assertEquals(0, wlsClusterConfig.getClusterSize());
    assertEquals("newly created empty WlsClusterConfig should not added to the clsuterConfigs list", 0, wlsDomainConfig.getClusterConfigs().size());
  }

  @Test
  public void verifyGetServerConfigsReturnNullIfNotFound() throws Exception {
    WlsDomainConfig wlsDomainConfig = WlsDomainConfig.create();
    assertNull(wlsDomainConfig.getServerConfig("noSuchServer"));
  }

  @Test
  public void verifyUpdateDomainSpecWarnsIfNoServersInClusterStartupCluster() throws Exception {
    WlsDomainConfig wlsDomainConfig = new WlsDomainConfig();
    DomainSpec domainSpec = new DomainSpec().addClusterStartupItem(new ClusterStartup().clusterName("noSuchCluster"));
    TestUtil.LogHandlerImpl handler = null;
    WlsClusterConfig wlsClusterConfig = wlsDomainConfig.getClusterConfig("noSuchCluster");
    try {
      handler = TestUtil.setupLogHandler(wlsClusterConfig);
      wlsDomainConfig.updateDomainSpecAsNeeded(domainSpec);
      assertTrue("Message logged: " + handler.getAllFormattedMessage(), handler.hasWarningMessageWithSubString("No servers configured in WebLogic cluster with name noSuchCluster"));
    } finally {
      TestUtil.removeLogHandler(wlsClusterConfig, handler);
    }
  }

  @Test
  public void verifyUpdateDomainSpecWarnsIfReplicasTooLarge() throws Exception {
    WlsDomainConfig wlsDomainConfig = WlsDomainConfig.create().load(JSON_STRING_1_CLUSTER);
    DomainSpec domainSpec = new DomainSpec().addClusterStartupItem(new ClusterStartup().clusterName("DockerCluster")).replicas(10);
    TestUtil.LogHandlerImpl handler = null;
    WlsClusterConfig wlsClusterConfig = wlsDomainConfig.getClusterConfig("DockerCluster");
    try {
      handler = TestUtil.setupLogHandler(wlsClusterConfig);
      wlsDomainConfig.updateDomainSpecAsNeeded(domainSpec);
      assertTrue("Message logged: " + handler.getAllFormattedMessage(), handler.hasWarningMessageWithSubString("Replicas in domainSpec for cluster DockerCluster is specified with a value of 10 which is larger than the number of configured WLS servers in the cluster: 5"));
    } finally {
      TestUtil.removeLogHandler(wlsClusterConfig, handler);
    }
  }

  @Test
  public void verifyUpdateDomainSpecInfoIfReplicasAndZeroClusters() throws Exception {
    WlsDomainConfig wlsDomainConfig = WlsDomainConfig.create();
    DomainSpec domainSpec = new DomainSpec().addClusterStartupItem(new ClusterStartup().clusterName("DockerCluster")).replicas(10);
    TestUtil.LogHandlerImpl handler = null;
    try {
      handler = TestUtil.setupLogHandler(wlsDomainConfig);
      wlsDomainConfig.updateDomainSpecAsNeeded(domainSpec);
      assertTrue("Message logged: " + handler.getAllFormattedMessage(), handler.hasInfoMessageWithSubString("Replicas specified in Domain spec is ignored because there number of configured WLS cluster is not 1."));
    } finally {
      TestUtil.removeLogHandler(wlsDomainConfig, handler);
    }
  }

  @Test
  public void verifyUpdateDomainSpecInfoIfReplicasAndTwoClusters() throws Exception {
    WlsDomainConfig wlsDomainConfig = WlsDomainConfig.create().load(JSON_STRING_2_CLUSTERS);
    DomainSpec domainSpec = new DomainSpec().addClusterStartupItem(new ClusterStartup().clusterName("DockerCluster")).replicas(10);
    TestUtil.LogHandlerImpl handler = null;
    try {
      handler = TestUtil.setupLogHandler(wlsDomainConfig);
      wlsDomainConfig.updateDomainSpecAsNeeded(domainSpec);
      assertTrue("Message logged: " + handler.getAllFormattedMessage(), handler.hasInfoMessageWithSubString("Replicas specified in Domain spec is ignored because there number of configured WLS cluster is not 1."));
    } finally {
      TestUtil.removeLogHandler(wlsDomainConfig, handler);
    }
  }

  @Test
  public void verifyUpdateDomainSpecReplicasNotValidatedWithMoreThan1Clusters() throws Exception {
    WlsDomainConfig wlsDomainConfig = WlsDomainConfig.create().load(JSON_STRING_2_CLUSTERS);
    DomainSpec domainSpec = new DomainSpec().addClusterStartupItem(new ClusterStartup().clusterName("DockerCluster")).replicas(10);
    TestUtil.LogHandlerImpl handler = null;
    WlsClusterConfig wlsClusterConfig = wlsDomainConfig.getClusterConfig("DockerCluster");
    try {
      handler = TestUtil.setupLogHandler(wlsClusterConfig);
      wlsDomainConfig.updateDomainSpecAsNeeded(domainSpec);
      assertFalse(handler.hasWarningMessageLogged());
    } finally {
      TestUtil.removeLogHandler(wlsClusterConfig, handler);
    }
  }

  @Test
  public void verifyUpdateDomainSpecNoWarningIfReplicasOK() throws Exception {
    WlsDomainConfig wlsDomainConfig = WlsDomainConfig.create().load(JSON_STRING_1_CLUSTER);
    DomainSpec domainSpec = new DomainSpec().addClusterStartupItem(new ClusterStartup().clusterName("DockerCluster")).replicas(5);
    TestUtil.LogHandlerImpl handler = null;
    WlsClusterConfig wlsClusterConfig = wlsDomainConfig.getClusterConfig("DockerCluster");
    try {
      handler = TestUtil.setupLogHandler(wlsClusterConfig);
      wlsDomainConfig.updateDomainSpecAsNeeded(domainSpec);
      assertFalse(handler.hasWarningMessageLogged());
    } finally {
      TestUtil.removeLogHandler(wlsClusterConfig, handler);
    }
  }

  @Test
  public void verifyUpdateDomainSpecWarnsIfClusterStatupReplicasTooLarge() throws Exception {
    WlsDomainConfig wlsDomainConfig = WlsDomainConfig.create().load(JSON_STRING_2_CLUSTERS);
    DomainSpec domainSpec = new DomainSpec().addClusterStartupItem(new ClusterStartup().clusterName("DockerCluster2").replicas(3)).replicas(5);
    TestUtil.LogHandlerImpl handler = null;
    WlsClusterConfig wlsClusterConfig = wlsDomainConfig.getClusterConfig("DockerCluster2");
    try {
      handler = TestUtil.setupLogHandler(wlsClusterConfig);
      wlsDomainConfig.updateDomainSpecAsNeeded(domainSpec);
      assertTrue("Message logged: " + handler.getAllFormattedMessage(), handler.hasWarningMessageWithSubString("Replicas in clusterStartup for cluster DockerCluster2 is specified with a value of 3 which is larger than the number of configured WLS servers in the cluster: 2"));
    } finally {
      TestUtil.removeLogHandler(wlsClusterConfig, handler);
    }
  }

  @Test
  public void verifyUpdateDomainSpecWarnsIfClusterStatupReplicasTooLarge_2clusters() throws Exception {
    WlsDomainConfig wlsDomainConfig = WlsDomainConfig.create().load(JSON_STRING_2_CLUSTERS);
    ClusterStartup dockerCluster = new ClusterStartup().clusterName("DockerCluster").replicas(10);
    ClusterStartup dockerCluster2 = new ClusterStartup().clusterName("DockerCluster2").replicas(10);
    DomainSpec domainSpec = new DomainSpec().addClusterStartupItem(dockerCluster).addClusterStartupItem(dockerCluster2);
    TestUtil.LogHandlerImpl handler = null;
    WlsClusterConfig wlsClusterConfig = wlsDomainConfig.getClusterConfig("DockerCluster2");
    try {
      handler = TestUtil.setupLogHandler(wlsClusterConfig);
      wlsDomainConfig.updateDomainSpecAsNeeded(domainSpec);
      assertTrue("Message logged: " + handler.getAllFormattedMessage(), handler.hasWarningMessageWithSubString("Replicas in clusterStartup for cluster DockerCluster is specified with a value of 10 which is larger than the number of configured WLS servers in the cluster: 3"));
      assertTrue("Message logged: " + handler.getAllFormattedMessage(), handler.hasWarningMessageWithSubString("Replicas in clusterStartup for cluster DockerCluster2 is specified with a value of 10 which is larger than the number of configured WLS servers in the cluster: 2"));
    } finally {
      TestUtil.removeLogHandler(wlsClusterConfig, handler);
    }
  }

  @Test
  public void verifyUpdateDomainSpecNoWarningIfClusterStatupReplicasOK() throws Exception {
    WlsDomainConfig wlsDomainConfig = WlsDomainConfig.create().load(JSON_STRING_2_CLUSTERS);
    DomainSpec domainSpec = new DomainSpec().addClusterStartupItem(new ClusterStartup().clusterName("DockerCluster2").replicas(2)).replicas(5);
    TestUtil.LogHandlerImpl handler = null;
    WlsClusterConfig wlsClusterConfig = wlsDomainConfig.getClusterConfig("DockerCluster2");
    try {
      handler = TestUtil.setupLogHandler(wlsClusterConfig);
      wlsDomainConfig.updateDomainSpecAsNeeded(domainSpec);
      assertFalse(handler.hasWarningMessageLogged());
    } finally {
      TestUtil.removeLogHandler(wlsClusterConfig, handler);
    }
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

  final String JSON_STRING_1_CLUSTER = "{\"servers\": {\"items\": [\n" +
          "    {\n" +
          "        \"listenAddress\": \"\",\n" +
          "        \"name\": \"AdminServer\",\n" +
          "        \"listenPort\": 8001,\n" +
          "        \"cluster\": null,\n" +
          "        \"networkAccessPoints\": {\"items\": []}\n" +
          "    },\n" +
          "    {\n" +
          "        \"listenAddress\": \"ms-0.wls-subdomain.default.svc.cluster.local\",\n" +
          "        \"name\": \"ms-0\",\n" +
          "        \"listenPort\": 8011,\n" +
          "        \"cluster\": [\n" +
          "            \"clusters\",\n" +
          "            \"DockerCluster\"\n" +
          "        ],\n" +
          "        \"networkAccessPoints\": {\"items\": [\n" +
          "            {\n" +
          "                \"protocol\": \"t3\",\n" +
          "                \"name\": \"Channel-0\",\n" +
          "                \"listenPort\": 8012\n" +
          "            },\n" +
          "            {\n" +
          "                \"protocol\": \"t3\",\n" +
          "                \"name\": \"Channel-1\",\n" +
          "                \"listenPort\": 8013\n" +
          "            },\n" +
          "            {\n" +
          "                \"protocol\": \"t3s\",\n" +
          "                \"name\": \"Channel-2\",\n" +
          "                \"listenPort\": 8014\n" +
          "            }\n" +
          "        ]}\n" +
          "    },\n" +
          "    {\n" +
          "        \"listenAddress\": \"ms-1.wls-subdomain.default.svc.cluster.local\",\n" +
          "        \"name\": \"ms-1\",\n" +
          "        \"listenPort\": 8011,\n" +
          "        \"cluster\": [\n" +
          "            \"clusters\",\n" +
          "            \"DockerCluster\"\n" +
          "        ],\n" +
          "        \"networkAccessPoints\": {\"items\": []}\n" +
          "    },\n" +
          "    {\n" +
          "        \"listenAddress\": \"ms-2.wls-subdomain.default.svc.cluster.local\",\n" +
          "        \"name\": \"ms-2\",\n" +
          "        \"listenPort\": 8011,\n" +
          "        \"cluster\": [\n" +
          "            \"clusters\",\n" +
          "            \"DockerCluster\"\n" +
          "        ],\n" +
          "        \"networkAccessPoints\": {\"items\": []}\n" +
          "    },\n" +
          "    {\n" +
          "        \"listenAddress\": \"ms-3.wls-subdomain.default.svc.cluster.local\",\n" +
          "        \"name\": \"ms-3\",\n" +
          "        \"listenPort\": 8011,\n" +
          "        \"cluster\": [\n" +
          "            \"clusters\",\n" +
          "            \"DockerCluster\"\n" +
          "        ],\n" +
          "        \"networkAccessPoints\": {\"items\": []}\n" +
          "    },\n" +
          "    {\n" +
          "        \"listenAddress\": \"ms-4.wls-subdomain.default.svc.cluster.local\",\n" +
          "        \"name\": \"ms-4\",\n" +
          "        \"listenPort\": 8011,\n" +
          "        \"cluster\": [\n" +
          "            \"clusters\",\n" +
          "            \"DockerCluster\"\n" +
          "        ],\n" +
          "        \"networkAccessPoints\": {\"items\": []}\n" +
          "    }\n" +
          "]}}";

  final String JSON_STRING_2_CLUSTERS = "{\"servers\": {\"items\": [\n" +
          "    {\n" +
          "        \"listenAddress\": \"\",\n" +
          "        \"name\": \"AdminServer\",\n" +
          "        \"listenPort\": 8001,\n" +
          "        \"cluster\": null,\n" +
          "        \"networkAccessPoints\": {\"items\": []}\n" +
          "    },\n" +
          "    {\n" +
          "        \"listenAddress\": \"ms-0.wls-subdomain.default.svc.cluster.local\",\n" +
          "        \"name\": \"ms-0\",\n" +
          "        \"listenPort\": 8011,\n" +
          "        \"cluster\": [\n" +
          "            \"clusters\",\n" +
          "            \"DockerCluster\"\n" +
          "        ],\n" +
          "        \"networkAccessPoints\": {\"items\": [\n" +
          "            {\n" +
          "                \"protocol\": \"t3\",\n" +
          "                \"name\": \"Channel-0\",\n" +
          "                \"listenPort\": 8012\n" +
          "            },\n" +
          "            {\n" +
          "                \"protocol\": \"t3s\",\n" +
          "                \"name\": \"Channel-1\",\n" +
          "                \"listenPort\": 8013\n" +
          "            }\n" +
          "        ]}\n" +
          "    },\n" +
          "    {\n" +
          "        \"listenAddress\": \"ms-1.wls-subdomain.default.svc.cluster.local\",\n" +
          "        \"name\": \"ms-1\",\n" +
          "        \"listenPort\": 8011,\n" +
          "        \"cluster\": [\n" +
          "            \"clusters\",\n" +
          "            \"DockerCluster\"\n" +
          "        ],\n" +
          "        \"networkAccessPoints\": {\"items\": []}\n" +
          "    },\n" +
          "    {\n" +
          "        \"listenAddress\": \"ms-2.wls-subdomain.default.svc.cluster.local\",\n" +
          "        \"name\": \"ms-2\",\n" +
          "        \"listenPort\": 8011,\n" +
          "        \"cluster\": [\n" +
          "            \"clusters\",\n" +
          "            \"DockerCluster\"\n" +
          "        ],\n" +
          "        \"networkAccessPoints\": {\"items\": []}\n" +
          "    },\n" +
          "    {\n" +
          "        \"listenAddress\": \"ms-3.wls-subdomain.default.svc.cluster.local\",\n" +
          "        \"name\": \"ms-3\",\n" +
          "        \"listenPort\": 8011,\n" +
          "        \"cluster\": [\n" +
          "            \"clusters\",\n" +
          "            \"DockerCluster2\"\n" +
          "        ],\n" +
          "        \"networkAccessPoints\": {\"items\": []}\n" +
          "    },\n" +
          "    {\n" +
          "        \"listenAddress\": \"ms-4.wls-subdomain.default.svc.cluster.local\",\n" +
          "        \"name\": \"ms-4\",\n" +
          "        \"listenPort\": 8011,\n" +
          "        \"cluster\": [\n" +
          "            \"clusters\",\n" +
          "            \"DockerCluster2\"\n" +
          "        ],\n" +
          "        \"networkAccessPoints\": {\"items\": []}\n" +
          "    }\n" +
          "]}}";
}

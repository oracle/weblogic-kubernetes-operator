// Copyright 2017, 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.wlsconfig;

import static oracle.kubernetes.LogMatcher.containsWarning;
import static oracle.kubernetes.operator.logging.MessageKeys.NO_WLS_SERVER_IN_CLUSTER;
import static oracle.kubernetes.operator.logging.MessageKeys.REPLICA_MORE_THAN_WLS_SERVERS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.arrayContaining;
import static org.hamcrest.Matchers.arrayContainingInAnyOrder;
import static org.hamcrest.Matchers.emptyArray;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import oracle.kubernetes.TestUtils;
import oracle.kubernetes.operator.utils.WlsDomainConfigSupport;
import oracle.kubernetes.weblogic.domain.ClusterConfigurator;
import oracle.kubernetes.weblogic.domain.DomainConfigurator;
import oracle.kubernetes.weblogic.domain.DomainConfiguratorFactory;
import oracle.kubernetes.weblogic.domain.model.Domain;
import oracle.kubernetes.weblogic.domain.model.DomainSpec;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/** Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved. */
public class WlsDomainConfigTest {

  private Domain domain = new Domain().withSpec(new DomainSpec());
  private DomainSpec domainSpec = domain.getSpec();
  private DomainConfigurator configurator = DomainConfiguratorFactory.forDomain(domain);
  private WlsDomainConfig wlsDomainConfig = new WlsDomainConfig(null);

  // The log messages to be checked during this test
  private static final String[] LOG_KEYS = {
    NO_WLS_SERVER_IN_CLUSTER, REPLICA_MORE_THAN_WLS_SERVERS
  };

  private List<LogRecord> logRecords = new ArrayList<>();
  private TestUtils.ConsoleHandlerMemento consoleControl;

  @Before
  public void setup() {
    consoleControl =
        TestUtils.silenceOperatorLogger()
            .collectLogMessages(logRecords, LOG_KEYS)
            .withLogLevel(Level.WARNING);
  }

  @After
  public void tearDown() {
    consoleControl.revert();
  }

  @Test
  public void verifyDomainNameLoadedFromJsonString() {
    createDomainConfig(JSON_STRING_1_CLUSTER);

    assertEquals("base_domain", wlsDomainConfig.getName());
  }

  private void createDomainConfig(String json) {
    wlsDomainConfig = WlsDomainConfig.create(json);
  }

  @Test
  public void verifyServersLoadedFromJsonString() {
    createDomainConfig(JSON_STRING_1_CLUSTER);
    Map<String, WlsClusterConfig> wlsClusterConfigList = wlsDomainConfig.getClusterConfigs();
    assertEquals(1, wlsClusterConfigList.size());

    WlsClusterConfig wlsClusterConfig = wlsDomainConfig.getClusterConfig("DockerCluster");
    assertEquals(5, wlsClusterConfig.getClusterSize());
    for (WlsServerConfig wlsServerConfig : wlsClusterConfig.getServerConfigs()) {
      if (!wlsServerConfig.isDynamicServer()) {
        assertEquals(
            wlsServerConfig.getName() + ".wls-subdomain.default.svc.cluster.local",
            wlsServerConfig.getListenAddress());
        assertEquals(Integer.valueOf(8011), wlsServerConfig.getListenPort());
      }
    }
    assertEquals(6, wlsDomainConfig.getServerConfigs().size());
    assertEquals("AdminServer", wlsDomainConfig.getServerConfig("AdminServer").getName());
  }

  @Test
  public void verifyDynamicServersLoadedFromJsonString() {
    createDomainConfig(JSON_STRING_MIXED_CLUSTER);
    WlsClusterConfig wlsClusterConfig = wlsDomainConfig.getClusterConfig("DockerCluster");
    assertEquals(2, wlsClusterConfig.getDynamicClusterSize());
    assertEquals(8, wlsClusterConfig.getMaxDynamicClusterSize());

    List<WlsServerConfig> serverConfigs = wlsClusterConfig.getServerConfigs();
    assertEquals(7, serverConfigs.size()); // 5 static + 2 dynamic servers

    assertTrue(containsServer(wlsClusterConfig, "dynamic-1"));
    assertTrue(containsServer(wlsClusterConfig, "dynamic-2"));
    assertTrue(containsServer(wlsClusterConfig, "ms-0"));
    assertTrue(containsServer(wlsClusterConfig, "ms-1"));
    assertTrue(containsServer(wlsClusterConfig, "ms-2"));
    assertTrue(containsServer(wlsClusterConfig, "ms-3"));
    assertTrue(containsServer(wlsClusterConfig, "ms-4"));

    for (WlsServerConfig wlsServerConfig : wlsClusterConfig.getServerConfigs()) {
      if (wlsServerConfig.isDynamicServer()) {
        String serverName = wlsServerConfig.getName();
        assertEquals("domain1-" + serverName, wlsServerConfig.getListenAddress());
        assertTrue(wlsServerConfig.isSslPortEnabled());
        if ("dynamic-1".equals(serverName)) {
          assertEquals(new Integer(8051), wlsServerConfig.getListenPort());
          assertEquals(new Integer(8151), wlsServerConfig.getSslListenPort());

        } else {
          assertEquals(new Integer(8052), wlsServerConfig.getListenPort());
          assertEquals(new Integer(8152), wlsServerConfig.getSslListenPort());
        }
      }
    }

    assertEquals(6, wlsDomainConfig.getServerConfigs().size()); // does not include dynamic servers
  }

  @Test
  public void verifyMachinesLoadedFromJsonString() {
    createDomainConfig(JSON_STRING_1_CLUSTER);
    Map<String, WlsMachineConfig> wlsMachineConfigList = wlsDomainConfig.getMachineConfigs();
    assertEquals(2, wlsMachineConfigList.size());

    WlsMachineConfig domain1_machine1 = wlsDomainConfig.getMachineConfig("domain1-machine1");
    assertEquals("domain1-machine1", domain1_machine1.getName());
    assertEquals(new Integer(5556), domain1_machine1.getNodeManagerListenPort());
    assertEquals("domain1-managed-server1", domain1_machine1.getNodeManagerListenAddress());
    assertEquals("Plain", domain1_machine1.getNodeManagerType());

    WlsMachineConfig domain1_machine2 = wlsDomainConfig.getMachineConfig("domain1-machine2");
    assertEquals("domain1-machine2", domain1_machine2.getName());
    assertEquals(new Integer(5556), domain1_machine2.getNodeManagerListenPort());
    assertEquals("domain1-managed-server2", domain1_machine2.getNodeManagerListenAddress());
    assertEquals("SSL", domain1_machine2.getNodeManagerType());
  }

  @Test
  public void verifyGetServerConfigsDoesNotIncludeDynamicServers() {
    createDomainConfig(JSON_STRING_MIXED_CLUSTER);

    assertEquals(6, wlsDomainConfig.getServerConfigs().size());
  }

  @Test
  public void verifySuggestsUpdateToDynamicClusterIfReplicasTooHigh() {
    createDomainConfig(JSON_STRING_MIXED_CLUSTER);
    configureCluster("DockerCluster").withReplicas(3);

    ArrayList<ConfigUpdate> suggestedConfigUpdates = new ArrayList<>();
    wlsDomainConfig.validate(domain, suggestedConfigUpdates);

    assertEquals(1, suggestedConfigUpdates.size());
    WlsClusterConfig.DynamicClusterSizeConfigUpdate configUpdate =
        (WlsClusterConfig.DynamicClusterSizeConfigUpdate) suggestedConfigUpdates.get(0);
    assertEquals(3, configUpdate.targetClusterSize);
  }

  private ClusterConfigurator configureCluster(String clusterName) {
    return configurator.configureCluster(clusterName);
  }

  @Test
  public void verifyNetworkAccessPointsInDynamicServersLoadedFromJsonString() {
    createDomainConfig(JSON_STRING_MIXED_CLUSTER);
    WlsClusterConfig wlsClusterConfig = wlsDomainConfig.getClusterConfig("DockerCluster");
    assertEquals(2, wlsClusterConfig.getDynamicClusterSize());
    assertEquals(8, wlsClusterConfig.getMaxDynamicClusterSize());

    for (WlsServerConfig wlsServerConfig : wlsClusterConfig.getServerConfigs()) {
      if (wlsServerConfig.isDynamicServer()) {
        String serverName = wlsServerConfig.getName();
        assertTrue(containsNetworkAccessPoint(wlsServerConfig, "DChannel-0"));
        assertTrue(containsNetworkAccessPoint(wlsServerConfig, "DChannel-1"));
        List<NetworkAccessPoint> networkAccessPoints = wlsServerConfig.getNetworkAccessPoints();
        for (NetworkAccessPoint networkAccessPoint : networkAccessPoints) {
          String expectedProtocol = null;
          Integer expectedListenPort = null;
          if ("DChannel-0".equals(networkAccessPoint.getName())) {
            expectedProtocol = "t3";
            expectedListenPort = "dynamic-1".equals(serverName) ? 9011 : 9012;
          } else if ("DChannel-1".equals(networkAccessPoint.getName())) {
            expectedProtocol = "t3s";
            expectedListenPort = "dynamic-1".equals(serverName) ? 9021 : 9022;
          }
          assertEquals(
              "protocol for " + networkAccessPoint.getName() + " not loaded properly",
              expectedProtocol,
              networkAccessPoint.getProtocol());
          assertEquals(
              "listen port for " + networkAccessPoint.getName() + " not loaded properly",
              expectedListenPort,
              networkAccessPoint.getListenPort());
        }
      }
    }
  }

  @Test
  public void verifyServerWithNoChannelLoadedFromJsonString() {
    WlsDomainConfig wlsDomainConfig = WlsDomainConfig.create(JSON_STRING_1_CLUSTER);

    WlsServerConfig serverConfig = wlsDomainConfig.getServerConfig("ms-1");
    assertEquals(0, serverConfig.getNetworkAccessPoints().size());
  }

  @Test
  public void verifyNetworkAccessPointsLoadedFromJsonString() {
    createDomainConfig(JSON_STRING_1_CLUSTER);

    WlsServerConfig serverConfig = wlsDomainConfig.getServerConfig("ms-0");
    assertEquals(3, serverConfig.getNetworkAccessPoints().size());
    assertTrue(containsNetworkAccessPoint(serverConfig, "Channel-0"));
    assertTrue(containsNetworkAccessPoint(serverConfig, "Channel-1"));
    assertTrue(containsNetworkAccessPoint(serverConfig, "Channel-2"));

    for (NetworkAccessPoint networkAccessPoint : serverConfig.getNetworkAccessPoints()) {
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
      assertEquals(
          "protocol for " + networkAccessPoint.getName() + " not loaded properly",
          expectedProtocol,
          networkAccessPoint.getProtocol());
      assertEquals(
          "listen port for " + networkAccessPoint.getName() + " not loaded properly",
          expectedListenPort,
          networkAccessPoint.getListenPort());
    }
  }

  @Test
  public void verifySSLConfigsLoadedFromJsonString() {
    createDomainConfig(JSON_STRING_1_CLUSTER);

    WlsServerConfig serverConfig = wlsDomainConfig.getServerConfig("ms-0");
    assertEquals(new Integer(8101), serverConfig.getSslListenPort());
    assertTrue(serverConfig.isSslPortEnabled());

    serverConfig = wlsDomainConfig.getServerConfig("ms-1");
    assertNull(serverConfig.getSslListenPort());
    assertFalse(serverConfig.isSslPortEnabled());
  }

  @Test
  public void verifyMultipleClustersLoadedFromJsonString() {
    createDomainConfig(JSON_STRING_2_CLUSTERS);
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
  public void verifyGetClusterConfigsDoesNotReturnNull() {
    WlsClusterConfig wlsClusterConfig = wlsDomainConfig.getClusterConfig("DockerCluster");
    assertNotNull(wlsClusterConfig);
    assertEquals(0, wlsClusterConfig.getClusterSize());
    assertEquals(
        "newly created empty WlsClusterConfig should not added to the clsuterConfigs list",
        0,
        wlsDomainConfig.getClusterConfigs().size());
  }

  @Test
  public void verifyGetServerConfigsReturnNullIfNotFound() {
    assertNull(wlsDomainConfig.getServerConfig("noSuchServer"));
  }

  @Test
  public void verifyGetMachineConfigsReturnNullIfNotFound() {
    assertNull(wlsDomainConfig.getMachineConfig("noSuchMachine"));
  }

  @Test
  public void verifyUpdateDomainSpecWarnsIfReplicasTooLarge() {
    createDomainConfig(JSON_STRING_1_CLUSTER);
    configureCluster("DockerCluster").withReplicas(10);

    wlsDomainConfig.validate(domain);
    assertThat(logRecords, containsWarning(REPLICA_MORE_THAN_WLS_SERVERS, "DockerCluster"));
  }

  @Test
  public void verifyUpdateDomainSpecNoWarningIfReplicasOK() {
    createDomainConfig(JSON_STRING_1_CLUSTER);
    configureCluster("DockerCluster").withReplicas(5);

    wlsDomainConfig.validate(domain);
    assertTrue(logRecords.isEmpty());
  }

  @Test
  public void verifyUpdateDomainSpecWarnsIfClusterStatupReplicasTooLarge() {
    createDomainConfig(JSON_STRING_2_CLUSTERS);
    configureCluster("DockerCluster2").withReplicas(3);

    wlsDomainConfig.validate(domain);
    assertThat(logRecords, containsWarning(REPLICA_MORE_THAN_WLS_SERVERS, "DockerCluster2"));
  }

  @Test
  public void verifyUpdateDomainSpecWarnsIfClusterStatupReplicasTooLarge_2clusters() {
    createDomainConfig(JSON_STRING_2_CLUSTERS);
    configureCluster("DockerCluster").withReplicas(10);
    configureCluster("DockerCluster2").withReplicas(10);

    wlsDomainConfig.validate(domain);
    assertThat(logRecords, containsWarning(REPLICA_MORE_THAN_WLS_SERVERS, "DockerCluster"));
    assertThat(logRecords, containsWarning(REPLICA_MORE_THAN_WLS_SERVERS, "DockerCluster2"));
  }

  @Test
  public void verifyUpdateDomainSpecNoWarningIfClusterStatupReplicasOK() {
    createDomainConfig(JSON_STRING_2_CLUSTERS);
    configureCluster("DockerCluster2").withReplicas(2);

    wlsDomainConfig.validate(domain);
    assertTrue(logRecords.isEmpty());
  }

  @Test
  public void verifyUpdateDomainSpecNoWarningIfClusterStatupOnDynamicCluster() {
    createDomainConfig(JSON_STRING_MIXED_CLUSTER);
    configureCluster("DockerCluster").withReplicas(10);

    wlsDomainConfig.validate(domain);
    assertTrue(logRecords.isEmpty());
  }

  @Test
  public void whenNoClustersDefined_returnEmptyArray() {
    WlsDomainConfigSupport support = new WlsDomainConfigSupport("test-domain");
    support.addWlsServer("server1");
    support.addWlsServer("server2");

    assertThat(support.createDomainConfig().getClusterNames(), emptyArray());
  }

  @Test
  public void whenOneClusterDefined_returnItsName() {
    WlsDomainConfigSupport support = new WlsDomainConfigSupport("test-domain");
    support.addWlsCluster("cluster1", "ms1", "ms2", "ms3");
    support.addWlsServer("server2");

    assertThat(support.createDomainConfig().getClusterNames(), arrayContaining("cluster1"));
  }

  @Test
  public void whenTwoClustersDefined_returnBothNames() {
    WlsDomainConfigSupport support = new WlsDomainConfigSupport("test-domain");
    support.addWlsCluster("cluster1", "ms1", "ms2", "ms3");
    support.addWlsCluster("cluster2", "ms4", "ms5");

    assertThat(
        support.createDomainConfig().getClusterNames(),
        arrayContainingInAnyOrder("cluster1", "cluster2"));
  }

  @Test
  public void whenTwoClustersDefined_returnReplicaLimits() {
    WlsDomainConfigSupport support = new WlsDomainConfigSupport("test-domain");
    support.addWlsCluster("cluster1", "ms1", "ms2", "ms3");
    support.addWlsCluster("cluster2", "ms4", "ms5");

    assertThat(support.createDomainConfig().getReplicaLimit("cluster1"), equalTo(3));
    assertThat(support.createDomainConfig().getReplicaLimit("cluster2"), equalTo(2));
  }

  @Test
  public void whenUnknownClusterName_returnZeroReplicaLimit() {
    WlsDomainConfigSupport support = new WlsDomainConfigSupport("test-domain");

    assertThat(support.createDomainConfig().getReplicaLimit("cluster3"), equalTo(0));
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

  private final String JSON_STRING_MIXED_CLUSTER =
      "{     \"name\": \"base_domain\",\n "
          + "\"servers\": {\"items\": [\n"
          + "    {\n"
          + "        \"listenAddress\": \"\",\n"
          + "        \"name\": \"AdminServer\",\n"
          + "        \"listenPort\": 8001,\n"
          + "        \"cluster\": null,\n"
          + "        \"networkAccessPoints\": {\"items\": []}\n"
          + "    },\n"
          + "    {\n"
          + "        \"listenAddress\": \"ms-0.wls-subdomain.default.svc.cluster.local\",\n"
          + "        \"name\": \"ms-0\",\n"
          + "        \"listenPort\": 8011,\n"
          + "        \"cluster\": [\n"
          + "            \"clusters\",\n"
          + "            \"DockerCluster\"\n"
          + "        ],\n"
          + "        \"networkAccessPoints\": {\"items\": [\n"
          + "            {\n"
          + "                \"protocol\": \"t3\",\n"
          + "                \"name\": \"Channel-0\",\n"
          + "                \"listenPort\": 8012\n"
          + "            },\n"
          + "            {\n"
          + "                \"protocol\": \"t3\",\n"
          + "                \"name\": \"Channel-1\",\n"
          + "                \"listenPort\": 8013\n"
          + "            },\n"
          + "            {\n"
          + "                \"protocol\": \"t3s\",\n"
          + "                \"name\": \"Channel-2\",\n"
          + "                \"listenPort\": 8014\n"
          + "            }\n"
          + "        ]},\n"
          + "            \"SSL\": {\n"
          + "                \"enabled\": true,\n"
          + "                \"listenPort\": 8101\n"
          + "            }\n"
          + "    },\n"
          + "    {\n"
          + "        \"listenAddress\": \"ms-1.wls-subdomain.default.svc.cluster.local\",\n"
          + "        \"name\": \"ms-1\",\n"
          + "        \"listenPort\": 8011,\n"
          + "        \"cluster\": [\n"
          + "            \"clusters\",\n"
          + "            \"DockerCluster\"\n"
          + "        ],\n"
          + "        \"networkAccessPoints\": {\"items\": []}\n"
          + "    },\n"
          + "    {\n"
          + "        \"listenAddress\": \"ms-2.wls-subdomain.default.svc.cluster.local\",\n"
          + "        \"name\": \"ms-2\",\n"
          + "        \"listenPort\": 8011,\n"
          + "        \"cluster\": [\n"
          + "            \"clusters\",\n"
          + "            \"DockerCluster\"\n"
          + "        ],\n"
          + "        \"networkAccessPoints\": {\"items\": []}\n"
          + "    },\n"
          + "    {\n"
          + "        \"listenAddress\": \"ms-3.wls-subdomain.default.svc.cluster.local\",\n"
          + "        \"name\": \"ms-3\",\n"
          + "        \"listenPort\": 8011,\n"
          + "        \"cluster\": [\n"
          + "            \"clusters\",\n"
          + "            \"DockerCluster\"\n"
          + "        ],\n"
          + "        \"networkAccessPoints\": {\"items\": []}\n"
          + "    },\n"
          + "    {\n"
          + "        \"listenAddress\": \"ms-4.wls-subdomain.default.svc.cluster.local\",\n"
          + "        \"name\": \"ms-4\",\n"
          + "        \"listenPort\": 8011,\n"
          + "        \"cluster\": [\n"
          + "            \"clusters\",\n"
          + "            \"DockerCluster\"\n"
          + "        ],\n"
          + "        \"networkAccessPoints\": {\"items\": []}\n"
          + "    }\n"
          + "    ]},\n"
          + "\"serverTemplates\": {\"items\": [\n"
          + "    {\n"
          + "        \"listenAddress\": \"domain1-${serverName}\",\n"
          + "        \"name\": \"server-template-1\",\n"
          + "        \"listenPort\": 8050,\n"
          + "        \"cluster\": [\n"
          + "            \"clusters\",\n"
          + "            \"DockerCluster\"\n"
          + "        ],\n"
          + "        \"networkAccessPoints\": {\"items\": [\n"
          + "            {\n"
          + "                \"protocol\": \"t3\",\n"
          + "                \"name\": \"DChannel-0\",\n"
          + "                \"listenPort\": 9010\n"
          + "            },\n"
          + "            {\n"
          + "                \"protocol\": \"t3s\",\n"
          + "                \"name\": \"DChannel-1\",\n"
          + "                \"listenPort\": 9020\n"
          + "            }\n"
          + "        ]},\n"
          + "            \"SSL\": {\n"
          + "                \"enabled\": true,\n"
          + "                \"listenPort\": 8150\n"
          + "            }\n"
          + "    }\n"
          + "    ]},\n"
          + "    \"clusters\": {\"items\": [\n"
          + "        {\n"
          + "            \"name\": \"DockerCluster\",\n"
          + "            \"dynamicServers\": {\n"
          + "                \"dynamicClusterSize\": 2,\n"
          + "                \"maxDynamicClusterSize\": 8,\n"
          + "                \"serverNamePrefix\": \"dynamic-\",\n"
          + "                \"dynamicServerNames\": [\n"
          + "                    \"dynamic-1\",\n"
          + "                    \"dynamic-2\"\n"
          + "                ],\n"
          + "                \"calculatedListenPorts\": true,\n"
          + "                \"serverTemplate\": [\n"
          + "                    \"serverTemplates\",\n"
          + "                    \"server-template-1\"\n"
          + "                ]\n"
          + "            }\n"
          + "        }"
          + "    ]}\n"
          + "}";

  private final String JSON_STRING_1_CLUSTER =
      "{     \"name\": \"base_domain\",\n "
          + "\"servers\": {\"items\": [\n"
          + "    {\n"
          + "        \"listenAddress\": \"\",\n"
          + "        \"name\": \"AdminServer\",\n"
          + "        \"listenPort\": 8001,\n"
          + "        \"cluster\": null,\n"
          + "        \"networkAccessPoints\": {\"items\": []}\n"
          + "    },\n"
          + "    {\n"
          + "        \"listenAddress\": \"ms-0.wls-subdomain.default.svc.cluster.local\",\n"
          + "        \"name\": \"ms-0\",\n"
          + "        \"listenPort\": 8011,\n"
          + "        \"cluster\": [\n"
          + "            \"clusters\",\n"
          + "            \"DockerCluster\"\n"
          + "        ],\n"
          + "        \"networkAccessPoints\": {\"items\": [\n"
          + "            {\n"
          + "                \"protocol\": \"t3\",\n"
          + "                \"name\": \"Channel-0\",\n"
          + "                \"listenPort\": 8012\n"
          + "            },\n"
          + "            {\n"
          + "                \"protocol\": \"t3\",\n"
          + "                \"name\": \"Channel-1\",\n"
          + "                \"listenPort\": 8013\n"
          + "            },\n"
          + "            {\n"
          + "                \"protocol\": \"t3s\",\n"
          + "                \"name\": \"Channel-2\",\n"
          + "                \"listenPort\": 8014\n"
          + "            }\n"
          + "        ]},\n"
          + "            \"SSL\": {\n"
          + "                \"enabled\": true,\n"
          + "                \"listenPort\": 8101\n"
          + "            }\n"
          + "    },\n"
          + "    {\n"
          + "        \"listenAddress\": \"ms-1.wls-subdomain.default.svc.cluster.local\",\n"
          + "        \"name\": \"ms-1\",\n"
          + "        \"listenPort\": 8011,\n"
          + "        \"cluster\": [\n"
          + "            \"clusters\",\n"
          + "            \"DockerCluster\"\n"
          + "        ],\n"
          + "        \"networkAccessPoints\": {\"items\": []}\n"
          + "    },\n"
          + "    {\n"
          + "        \"listenAddress\": \"ms-2.wls-subdomain.default.svc.cluster.local\",\n"
          + "        \"name\": \"ms-2\",\n"
          + "        \"listenPort\": 8011,\n"
          + "        \"cluster\": [\n"
          + "            \"clusters\",\n"
          + "            \"DockerCluster\"\n"
          + "        ],\n"
          + "        \"networkAccessPoints\": {\"items\": []}\n"
          + "    },\n"
          + "    {\n"
          + "        \"listenAddress\": \"ms-3.wls-subdomain.default.svc.cluster.local\",\n"
          + "        \"name\": \"ms-3\",\n"
          + "        \"listenPort\": 8011,\n"
          + "        \"cluster\": [\n"
          + "            \"clusters\",\n"
          + "            \"DockerCluster\"\n"
          + "        ],\n"
          + "        \"networkAccessPoints\": {\"items\": []}\n"
          + "    },\n"
          + "    {\n"
          + "        \"listenAddress\": \"ms-4.wls-subdomain.default.svc.cluster.local\",\n"
          + "        \"name\": \"ms-4\",\n"
          + "        \"listenPort\": 8011,\n"
          + "        \"cluster\": [\n"
          + "            \"clusters\",\n"
          + "            \"DockerCluster\"\n"
          + "        ],\n"
          + "        \"networkAccessPoints\": {\"items\": []}\n"
          + "    }\n"
          + "  ]}, "
          + "    \"machines\": {\"items\": [\n"
          + "        {\n"
          + "            \"name\": \"domain1-machine1\",\n"
          + "            \"nodeManager\": {\n"
          + "                \"NMType\": \"Plain\",\n"
          + "                \"listenAddress\": \"domain1-managed-server1\",\n"
          + "                \"name\": \"domain1-machine1\",\n"
          + "                \"listenPort\": 5556\n"
          + "            }\n"
          + "        },\n"
          + "        {\n"
          + "            \"name\": \"domain1-machine2\",\n"
          + "            \"nodeManager\": {\n"
          + "                \"NMType\": \"SSL\",\n"
          + "                \"listenAddress\": \"domain1-managed-server2\",\n"
          + "                \"name\": \"domain1-machine2\",\n"
          + "                \"listenPort\": 5556\n"
          + "            }\n"
          + "        }\n"
          + "    ]}\n"
          + "}";

  private final String JSON_STRING_2_CLUSTERS =
      "{\"servers\": {\"items\": [\n"
          + "    {\n"
          + "        \"listenAddress\": \"\",\n"
          + "        \"name\": \"AdminServer\",\n"
          + "        \"listenPort\": 8001,\n"
          + "        \"cluster\": null,\n"
          + "        \"networkAccessPoints\": {\"items\": []}\n"
          + "    },\n"
          + "    {\n"
          + "        \"listenAddress\": \"ms-0.wls-subdomain.default.svc.cluster.local\",\n"
          + "        \"name\": \"ms-0\",\n"
          + "        \"listenPort\": 8011,\n"
          + "        \"cluster\": [\n"
          + "            \"clusters\",\n"
          + "            \"DockerCluster\"\n"
          + "        ],\n"
          + "        \"networkAccessPoints\": {\"items\": [\n"
          + "            {\n"
          + "                \"protocol\": \"t3\",\n"
          + "                \"name\": \"Channel-0\",\n"
          + "                \"listenPort\": 8012\n"
          + "            },\n"
          + "            {\n"
          + "                \"protocol\": \"t3s\",\n"
          + "                \"name\": \"Channel-1\",\n"
          + "                \"listenPort\": 8013\n"
          + "            }\n"
          + "        ]}\n"
          + "    },\n"
          + "    {\n"
          + "        \"listenAddress\": \"ms-1.wls-subdomain.default.svc.cluster.local\",\n"
          + "        \"name\": \"ms-1\",\n"
          + "        \"listenPort\": 8011,\n"
          + "        \"cluster\": [\n"
          + "            \"clusters\",\n"
          + "            \"DockerCluster\"\n"
          + "        ],\n"
          + "        \"networkAccessPoints\": {\"items\": []}\n"
          + "    },\n"
          + "    {\n"
          + "        \"listenAddress\": \"ms-2.wls-subdomain.default.svc.cluster.local\",\n"
          + "        \"name\": \"ms-2\",\n"
          + "        \"listenPort\": 8011,\n"
          + "        \"cluster\": [\n"
          + "            \"clusters\",\n"
          + "            \"DockerCluster\"\n"
          + "        ],\n"
          + "        \"networkAccessPoints\": {\"items\": []}\n"
          + "    },\n"
          + "    {\n"
          + "        \"listenAddress\": \"ms-3.wls-subdomain.default.svc.cluster.local\",\n"
          + "        \"name\": \"ms-3\",\n"
          + "        \"listenPort\": 8011,\n"
          + "        \"cluster\": [\n"
          + "            \"clusters\",\n"
          + "            \"DockerCluster2\"\n"
          + "        ],\n"
          + "        \"networkAccessPoints\": {\"items\": []}\n"
          + "    },\n"
          + "    {\n"
          + "        \"listenAddress\": \"ms-4.wls-subdomain.default.svc.cluster.local\",\n"
          + "        \"name\": \"ms-4\",\n"
          + "        \"listenPort\": 8011,\n"
          + "        \"cluster\": [\n"
          + "            \"clusters\",\n"
          + "            \"DockerCluster2\"\n"
          + "        ],\n"
          + "        \"networkAccessPoints\": {\"items\": []}\n"
          + "    }\n"
          + "]}}";
}

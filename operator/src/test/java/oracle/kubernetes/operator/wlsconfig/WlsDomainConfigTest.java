// Copyright (c) 2017, 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.wlsconfig;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.LogRecord;

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
import static org.hamcrest.Matchers.emptyArray;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class WlsDomainConfigTest {

  // The log messages to be checked during this test
  private static final String[] LOG_KEYS = {
    NO_WLS_SERVER_IN_CLUSTER, REPLICA_MORE_THAN_WLS_SERVERS
  };
  private static final String JSON_STRING_MIXED_CLUSTER =
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
          + "                \"minDynamicClusterSize\": 1,\n"
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
  private static final String JSON_STRING_1_CLUSTER =
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
  private static final String JSON_STRING_2_CLUSTERS =
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
    assertEquals(1, wlsClusterConfig.getMinDynamicClusterSize());

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
          assertEquals(Integer.valueOf(8051), wlsServerConfig.getListenPort());
          assertEquals(Integer.valueOf(8151), wlsServerConfig.getSslListenPort());

        } else {
          assertEquals(Integer.valueOf(8052), wlsServerConfig.getListenPort());
          assertEquals(Integer.valueOf(8152), wlsServerConfig.getSslListenPort());
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

    WlsMachineConfig domain1machine1 = wlsDomainConfig.getMachineConfig("domain1-machine1");
    assertEquals("domain1-machine1", domain1machine1.getName());
    assertEquals(Integer.valueOf(5556), domain1machine1.getNodeManagerListenPort());
    assertEquals("domain1-managed-server1", domain1machine1.getNodeManagerListenAddress());
    assertEquals("Plain", domain1machine1.getNodeManagerType());

    WlsMachineConfig domain1machine2 = wlsDomainConfig.getMachineConfig("domain1-machine2");
    assertEquals("domain1-machine2", domain1machine2.getName());
    assertEquals(Integer.valueOf(5556), domain1machine2.getNodeManagerListenPort());
    assertEquals("domain1-managed-server2", domain1machine2.getNodeManagerListenAddress());
    assertEquals("SSL", domain1machine2.getNodeManagerType());
  }

  @Test
  public void verifyGetServerConfigsDoesNotIncludeDynamicServers() {
    createDomainConfig(JSON_STRING_MIXED_CLUSTER);

    assertEquals(6, wlsDomainConfig.getServerConfigs().size());
  }

  @Test
  public void verifyNetworkAccessPointsInDynamicServersLoadedFromJsonString() {
    createDomainConfig(JSON_STRING_MIXED_CLUSTER);
    WlsClusterConfig wlsClusterConfig = wlsDomainConfig.getClusterConfig("DockerCluster");
    assertEquals(2, wlsClusterConfig.getDynamicClusterSize());
    assertEquals(8, wlsClusterConfig.getMaxDynamicClusterSize());
    assertEquals(1, wlsClusterConfig.getMinDynamicClusterSize());

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
  public void verifySslConfigsLoadedFromJsonString() {
    createDomainConfig(JSON_STRING_1_CLUSTER);

    WlsServerConfig serverConfig = wlsDomainConfig.getServerConfig("ms-0");
    assertEquals(Integer.valueOf(8101), serverConfig.getSslListenPort());
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
        "newly created empty WlsClusterConfig should not added to the clusterConfigs list",
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
  public void whenNoClustersDefined_returnEmptyArray() {
    support.addWlsServer("server1");
    support.addWlsServer("server2");

    assertThat(support.createDomainConfig().getClusterNames(), emptyArray());
  }

  @Test
  public void whenOneClusterDefined_returnItsName() {
    support.addWlsCluster("cluster1", "ms1", "ms2", "ms3");
    support.addWlsServer("server2");

    assertThat(support.createDomainConfig().getClusterNames(), arrayContaining("cluster1"));
  }

  @Test
  public void whenTwoClustersDefined_returnBothNames() {
    support.addWlsCluster("cluster1", "ms1", "ms2", "ms3");
    support.addWlsCluster("cluster2", "ms4", "ms5");

    assertThat(
        support.createDomainConfig().getClusterNames(),
        arrayContainingInAnyOrder("cluster1", "cluster2"));
  }

  @Test
  public void whenTwoClustersDefined_returnReplicaLimits() {
    support.addWlsCluster("cluster1", "ms1", "ms2", "ms3");
    support.addWlsCluster("cluster2", "ms4", "ms5");

    assertThat(support.createDomainConfig().getReplicaLimit("cluster1"), equalTo(3));
    assertThat(support.createDomainConfig().getReplicaLimit("cluster2"), equalTo(2));
  }

  @Test
  public void whenUnknownClusterName_returnZeroReplicaLimit() {
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

  @Test
  public void whenTopologyGenerated_containsDomainValidFlag() {
    assertThat(wlsDomainConfig.toTopology(), hasJsonPath("domainValid", equalTo("true")));
  }

  @Test
  public void whenTopologyGenerated_containsDomainName() {
    assertThat(wlsDomainConfig.toTopology(), hasJsonPath("domain.name", equalTo("test-domain")));
  }

  @Test
  public void whenTopologyGenerated_containsAdminServerName() {
    wlsDomainConfig.withAdminServer("admin-server", "admin-host", 7001);

    assertThat(
        wlsDomainConfig.toTopology(), hasJsonPath("domain.adminServerName", equalTo("admin-server")));
  }

  @Test
  public void whenTopologyGenerated_containsAdminServerSpec() {
    wlsDomainConfig.withAdminServer("admin-server", "admin-host", 7001);

    assertThat(
        wlsDomainConfig.toTopology(),
        hasJsonPath("domain.servers", withServerConfig("admin-server", "admin-host", 7001)));
  }

  @Test
  public void whenYamlGenerated_containsClusterConfig() {
    wlsDomainConfig.withCluster(new WlsClusterConfig("cluster1"));

    assertThat(
        wlsDomainConfig.toTopology(),
        hasJsonPath("domain.configuredClusters[*].name", contains("cluster1")));
  }

  @Test
  public void whenYamlGenerated_containsClusteredServerConfigs() {
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
  public void containsServer_returnsTrue_forExistingStandaloneServer() {
    wlsDomainConfig.addWlsServer("ms1","host1", 8001);

    assertThat(wlsDomainConfig.containsServer("ms1"), equalTo(true));
  }

  @Test
  public void containsServer_returnsTrue_forExistingConfiguredClusteredServer() {
    support.addWlsCluster("cluster-1", "ms1");

    assertThat(support.createDomainConfig().containsServer("ms1"), equalTo(true));
  }

  @Test
  public void containsServer_returnsTrue_forExistingDynamicClusterServer() {
    support.addDynamicWlsCluster("cluster-1", "ms1");

    assertThat(support.createDomainConfig().containsServer("ms1"), equalTo(true));
  }

  @Test
  public void containsServer_returnsFalse_forNonExistingServer() {
    support.addWlsCluster("cluster-1", "ms1");
    support.addDynamicWlsCluster("dynamic-cluster", "dyn1");
    support.addWlsServer("standalone");

    assertThat(support.createDomainConfig().containsServer("notthere"), equalTo(false));
  }

  @Test
  public void containsServer_returnsFalse_forNullServerName() {
    assertThat(wlsDomainConfig.containsServer(null), equalTo(false));
  }

  @Test
  public void containsCluster_returnsFalse_forNonExistingCluster() {
    support.addWlsCluster("cluster-1", "ms1");

    assertThat(support.createDomainConfig().containsCluster("notthere"), equalTo(false));
  }

  @Test
  public void containsCluster_returnsTrue_forExistingCluster() {
    support.addWlsCluster("cluster-1", "ms1");

    assertThat(support.createDomainConfig().containsCluster("cluster-1"), equalTo(true));
  }

  @Test
  public void containsCluster_returnsFalse_forNullServerName() {
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

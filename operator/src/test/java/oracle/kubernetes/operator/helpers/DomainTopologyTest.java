// Copyright (c) 2019, 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helpers;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import oracle.kubernetes.operator.wlsconfig.NetworkAccessPoint;
import oracle.kubernetes.operator.wlsconfig.WlsClusterConfig;
import oracle.kubernetes.operator.wlsconfig.WlsDomainConfig;
import oracle.kubernetes.operator.wlsconfig.WlsDynamicServersConfig;
import oracle.kubernetes.operator.wlsconfig.WlsServerConfig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class DomainTopologyTest {


  private static final String DOMAIN_TOPOLOGY =
            "domainValid: true\n"
          + "domain:\n"
          + "  name: \"base_domain\"\n"
          + "  adminServerName: \"admin-server\"\n"
          + "  configuredClusters:\n"
          + "  - name: \"cluster-1\"\n"
          + "    servers:\n"
          + "      - name: \"managed-server1\"\n"
          + "        listenPort: 7003\n"
          + "        listenAddress: \"domain1-managed-server1\"\n"
          + "        sslListenPort: 7103\n"
          + "        machineName: \"machine-managed-server1\"\n"
          + "      - name: \"managed-server2\"\n"
          + "        listenPort: 7004\n"
          + "        listenAddress: \"domain1-managed-server2\"\n"
          + "        sslListenPort: 7104\n"
          + "        networkAccessPoints:\n"
          + "          - name: \"nap2\"\n"
          + "            protocol: \"t3\"\n"
          + "            listenPort: 7105\n"
          + "            publicPort: 7105\n"
          + "  servers:\n"
          + "    - name: \"admin-server\"\n"
          + "      listenPort: 7001\n"
          + "      listenAddress: \"domain1-admin-server\"\n"
          + "      adminPort: 7099\n"
          + "    - name: \"server1\"\n"
          + "      listenPort: 9003\n"
          + "      listenAddress: \"domain1-managed-server1\"\n"
          + "      sslListenPort: 8003\n"
          + "      machineName: \"machine-managed-server1\"\n"
          + "    - name: \"server2\"\n"
          + "      listenPort: 9004\n"
          + "      listenAddress: \"domain1-managed-server2\"\n"
          + "      sslListenPort: 8004\n"
          + "      networkAccessPoints:\n"
          + "        - name: \"nap2\"\n"
          + "          protocol: \"t3\"\n"
          + "          listenPort: 8005\n"
          + "          publicPort: 8005\n";
  private static final String DYNAMIC_SERVER_TOPOLOGY =
            "domainValid: true\n"
          + "domain:\n"
          + "  name: \"base_domain\"\n"
          + "  adminServerName: \"admin-server\"\n"
          + "  configuredClusters:\n"
          + "  - name: \"cluster-1\"\n"
          + "    dynamicServersConfig:\n"
          + "        name: \"cluster-1\"\n"
          + "        serverTemplateName: \"cluster-1-template\"\n"
          + "        calculatedListenPorts: false\n"
          + "        serverNamePrefix: \"managed-server\"\n"
          + "        dynamicClusterSize: 4\n"
          + "        maxDynamicClusterSize: 8\n"
          + "        minDynamicClusterSize: 2\n"
          + "  serverTemplates:\n"
          + "    - name: \"cluster-1-template\"\n"
          + "      listenPort: 8001\n"
          + "      clusterName: \"cluster-1\"\n"
          + "      listenAddress: \"domain1-managed-server${id}\"\n"
          + "  servers:\n"
          + "    - name: \"admin-server\"\n"
          + "      listenPort: 7001\n"
          + "      listenAddress: \"domain1-admin-server\"\n";
  private static final String MIXED_CLUSTER_TOPOLOGY =
            "domainValid: true\n"
          + "domain:\n"
          + "  name: \"base_domain\"\n"
          + "  adminServerName: \"admin-server\"\n"
          + "  configuredClusters:\n"
          + "  - name: \"cluster-1\"\n"
          + "    dynamicServersConfig:\n"
          + "        name: \"cluster-1\"\n"
          + "        serverTemplateName: \"cluster-1-template\"\n"
          + "        calculatedListenPorts: false\n"
          + "        serverNamePrefix: \"managed-server\"\n"
          + "        dynamicClusterSize: 3\n"
          + "        maxDynamicClusterSize: 8\n"
          + "    servers:\n"
          + "      - name: \"ms1\"\n"
          + "        listenPort: 7003\n"
          + "        listenAddress: \"domain1-managed-server1\"\n"
          + "        sslListenPort: 7103\n"
          + "        machineName: \"machine-managed-server1\"\n"
          + "      - name: \"ms2\"\n"
          + "        listenPort: 7004\n"
          + "        listenAddress: \"domain1-managed-server2\"\n"
          + "        sslListenPort: 7104\n"
          + "        networkAccessPoints:\n"
          + "          - name: \"nap2\"\n"
          + "            protocol: \"t3\"\n"
          + "            listenPort: 7105\n"
          + "            publicPort: 7105\n"
          + "  serverTemplates:\n"
          + "    - name: \"cluster-1-template\"\n"
          + "      listenPort: 8001\n"
          + "      clusterName: \"cluster-1\"\n"
          + "      listenAddress: \"domain1-managed-server${id}\"\n"
          + "      sslListenPort: 7204\n"
          + "      networkAccessPoints:\n"
          + "        - name: \"nap3\"\n"
          + "          protocol: \"t3\"\n"
          + "          listenPort: 7205\n"
          + "          publicPort: 7205\n"
          + "  servers:\n"
          + "    - name: \"admin-server\"\n"
          + "      listenPort: 7001\n"
          + "      listenAddress: \"domain1-admin-server\"\n";
  private static final String INVALID_TOPOLOGY =
            "domainValid: false\n"
          + "validationErrors:\n"
          + "  - \"The dynamic cluster \\\"mycluster\\\"'s dynamic servers use calculated listen ports.\"";
  private static final String DOMAIN_INVALID_NO_ERRORS =
      "domainValid: false\n" + "validationErrors:\n";

  @Test
  public void parseDomainTopologyYaml() {
    DomainTopology domainTopology =
        DomainTopology.parseDomainTopologyYaml(DOMAIN_TOPOLOGY);

    assertNotNull(domainTopology);
    assertTrue(domainTopology.getDomainValid());

    WlsDomainConfig wlsDomainConfig = domainTopology.getDomain();
    assertNotNull(wlsDomainConfig);

    assertEquals("base_domain", wlsDomainConfig.getName());
    assertEquals("admin-server", wlsDomainConfig.getAdminServerName());

    Map<String, WlsClusterConfig> wlsClusterConfigs = wlsDomainConfig.getClusterConfigs();
    assertEquals(1, wlsClusterConfigs.size());

    WlsClusterConfig wlsClusterConfig = wlsClusterConfigs.get("cluster-1");
    assertNotNull(wlsClusterConfig);

    List<WlsServerConfig> wlsServerConfigs = wlsClusterConfig.getServers();
    assertEquals(2, wlsServerConfigs.size());

    Map<String, WlsServerConfig> serverConfigMap = wlsDomainConfig.getServerConfigs();
    assertEquals(3, serverConfigMap.size());

    assertTrue(serverConfigMap.containsKey("admin-server"));
    assertTrue(serverConfigMap.containsKey("server1"));
    assertTrue(serverConfigMap.containsKey("server2"));
    WlsServerConfig adminServerConfig = serverConfigMap.get("admin-server");
    assertEquals(7099, adminServerConfig.getAdminPort().intValue());
    assertTrue(adminServerConfig.isAdminPortEnabled());

    WlsServerConfig server2Config = serverConfigMap.get("server2");
    assertEquals("domain1-managed-server2", server2Config.getListenAddress());
    assertEquals(9004, server2Config.getListenPort().intValue());
    assertEquals(8004, server2Config.getSslListenPort().intValue());
    assertTrue(server2Config.isSslPortEnabled());
    List<NetworkAccessPoint> server2ConfigNaps = server2Config.getNetworkAccessPoints();
    assertEquals(1, server2ConfigNaps.size());

    NetworkAccessPoint server2ConfigNap = server2ConfigNaps.get(0);
    assertEquals("nap2", server2ConfigNap.getName());
    assertEquals("t3", server2ConfigNap.getProtocol());
    assertEquals(8005, server2ConfigNap.getListenPort().intValue());
    assertEquals(8005, server2ConfigNap.getPublicPort().intValue());
  }

  @Test
  public void parseDynamicServerTopologyYaml() {
    DomainTopology domainTopology =
        DomainTopology.parseDomainTopologyYaml(DYNAMIC_SERVER_TOPOLOGY);

    assertNotNull(domainTopology);
    assertTrue(domainTopology.getDomainValid());

    WlsDomainConfig wlsDomainConfig = domainTopology.getDomain();
    assertNotNull(wlsDomainConfig);

    assertEquals("base_domain", wlsDomainConfig.getName());
    assertEquals("admin-server", wlsDomainConfig.getAdminServerName());

    wlsDomainConfig.processDynamicClusters();

    Map<String, WlsClusterConfig> wlsClusterConfigs = wlsDomainConfig.getClusterConfigs();
    assertEquals(1, wlsClusterConfigs.size());

    WlsClusterConfig wlsClusterConfig = wlsClusterConfigs.get("cluster-1");
    assertNotNull(wlsClusterConfig);

    WlsDynamicServersConfig wlsDynamicServersConfig = wlsClusterConfig.getDynamicServersConfig();
    assertNotNull(wlsDynamicServersConfig);
    assertEquals("cluster-1", wlsDynamicServersConfig.getName());
    assertEquals("cluster-1-template", wlsDynamicServersConfig.getServerTemplateName());
    assertFalse(wlsDynamicServersConfig.getCalculatedListenPorts());
    assertEquals("managed-server", wlsDynamicServersConfig.getServerNamePrefix());
    assertEquals(4, wlsDynamicServersConfig.getDynamicClusterSize().intValue());
    assertEquals(8, wlsDynamicServersConfig.getMaxDynamicClusterSize().intValue());
    assertEquals(2, wlsDynamicServersConfig.getMinDynamicClusterSize().intValue());

    List<WlsServerConfig> serverTemplates = wlsDomainConfig.getServerTemplates();
    assertEquals(1, serverTemplates.size());
    assertEquals("cluster-1-template", serverTemplates.get(0).getName());
    assertEquals("domain1-managed-server${id}", serverTemplates.get(0).getListenAddress());
    assertEquals("cluster-1", serverTemplates.get(0).getClusterName());

    Map<String, WlsServerConfig> serverConfigMap = wlsDomainConfig.getServerConfigs();
    assertEquals(1, serverConfigMap.size());

    assertTrue(serverConfigMap.containsKey("admin-server"));
  }

  @Test
  public void parseMixedClusterTopologyYaml() {
    DomainTopology domainTopology =
        DomainTopology.parseDomainTopologyYaml(MIXED_CLUSTER_TOPOLOGY);

    assertNotNull(domainTopology);
    assertTrue(domainTopology.getDomainValid());

    WlsDomainConfig wlsDomainConfig = domainTopology.getDomain();
    assertNotNull(wlsDomainConfig);

    assertEquals("base_domain", wlsDomainConfig.getName());
    assertEquals("admin-server", wlsDomainConfig.getAdminServerName());

    wlsDomainConfig.processDynamicClusters();

    Map<String, WlsClusterConfig> wlsClusterConfigs = wlsDomainConfig.getClusterConfigs();
    assertEquals(1, wlsClusterConfigs.size());

    WlsClusterConfig wlsClusterConfig = wlsClusterConfigs.get("cluster-1");
    assertNotNull(wlsClusterConfig);

    assertTrue(wlsClusterConfig.hasDynamicServers());
    assertTrue(wlsClusterConfig.hasStaticServers());
    assertEquals(2, wlsClusterConfig.getClusterSize());
    assertEquals(3, wlsClusterConfig.getDynamicClusterSize());
    assertEquals(5, wlsClusterConfig.getServerConfigs().size());
    assertEquals(2, wlsClusterConfig.getServers().size());

    assertNotNull(wlsDomainConfig.getServerTemplates());
    assertEquals(1, wlsDomainConfig.getServerTemplates().size());
    assertNotNull(wlsDomainConfig.getServerTemplates().get(0));
    assertEquals("cluster-1-template", wlsDomainConfig.getServerTemplates().get(0).getName());
    assertEquals(
        "domain1-managed-server${id}",
        wlsDomainConfig.getServerTemplates().get(0).getListenAddress());
    assertEquals(8001, wlsDomainConfig.getServerTemplates().get(0).getListenPort().intValue());

    List<WlsServerConfig> serverTemplates = wlsDomainConfig.getServerTemplates();
    assertEquals(1, serverTemplates.size());
    assertEquals("cluster-1-template", serverTemplates.get(0).getName());
    assertEquals("domain1-managed-server${id}", serverTemplates.get(0).getListenAddress());
    assertEquals("cluster-1", serverTemplates.get(0).getClusterName());

    WlsDynamicServersConfig dynamicServerConfig = wlsClusterConfig.getDynamicServersConfig();
    assertNotNull(dynamicServerConfig.getServerTemplateName());
    assertEquals("cluster-1-template", dynamicServerConfig.getServerTemplateName());

    List<WlsServerConfig> dynamicServerConfigs = dynamicServerConfig.getServerConfigs();
    assertEquals(3, dynamicServerConfigs.size());

    assertTrue(dynamicServerConfigs.get(0).isDynamicServer());
    assertEquals("domain1-managed-server1", dynamicServerConfigs.get(0).getListenAddress());
    assertEquals(8001, dynamicServerConfigs.get(0).getListenPort().intValue());
    assertEquals(7204, dynamicServerConfigs.get(0).getSslListenPort().intValue());
    assertEquals(1, dynamicServerConfigs.get(0).getNetworkAccessPoints().size());
    assertEquals(
        7205,
        dynamicServerConfigs.get(0).getNetworkAccessPoints().get(0).getListenPort().intValue());

    assertTrue(dynamicServerConfigs.get(1).isDynamicServer());
    assertEquals("domain1-managed-server2", dynamicServerConfigs.get(1).getListenAddress());
    assertEquals(8001, dynamicServerConfigs.get(1).getListenPort().intValue());
    assertEquals(7204, dynamicServerConfigs.get(1).getSslListenPort().intValue());
    assertEquals(1, dynamicServerConfigs.get(1).getNetworkAccessPoints().size());
    assertEquals(
        7205,
        dynamicServerConfigs.get(1).getNetworkAccessPoints().get(0).getListenPort().intValue());

    assertTrue(dynamicServerConfigs.get(1).isDynamicServer());
    assertEquals("domain1-managed-server3", dynamicServerConfigs.get(2).getListenAddress());
    assertEquals(8001, dynamicServerConfigs.get(2).getListenPort().intValue());
    assertEquals(7204, dynamicServerConfigs.get(2).getSslListenPort().intValue());
    assertEquals(1, dynamicServerConfigs.get(2).getNetworkAccessPoints().size());
    assertEquals(
        7205,
        dynamicServerConfigs.get(2).getNetworkAccessPoints().get(0).getListenPort().intValue());
  }

  @Test
  public void parseInvalidTopologyYamlWithValidationErrors() {
    DomainTopology domainTopology = Objects.requireNonNull(DomainTopology.parseDomainTopologyYaml(INVALID_TOPOLOGY));

    assertFalse(domainTopology.getValidationErrors().isEmpty());
    assertFalse(domainTopology.getDomainValid());
    assertEquals(
        "The dynamic cluster \"mycluster\"'s dynamic servers use calculated listen ports.",
        domainTopology.getValidationErrors().get(0));
  }

  @Test
  public void parseInvalidTopologyYamlWithNoValidationErrors() {
    DomainTopology domainTopology
          = Objects.requireNonNull(DomainTopology.parseDomainTopologyYaml(DOMAIN_INVALID_NO_ERRORS));

    assertFalse(domainTopology.getValidationErrors().isEmpty());
    assertFalse(domainTopology.getDomainValid());
  }
}
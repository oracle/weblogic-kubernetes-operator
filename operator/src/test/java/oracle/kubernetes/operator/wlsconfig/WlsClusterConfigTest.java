// Copyright 2017, 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.wlsconfig;

import static org.junit.Assert.*;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Handler;
import java.util.logging.Logger;
import oracle.kubernetes.TestUtils;
import oracle.kubernetes.operator.logging.LoggingFactory;
import oracle.kubernetes.operator.work.NextAction;
import oracle.kubernetes.operator.work.Packet;
import oracle.kubernetes.operator.work.Step;
import oracle.kubernetes.weblogic.domain.v1.ClusterStartup;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

public class WlsClusterConfigTest {

  private static final Logger UNDERLYING_LOGGER =
      LoggingFactory.getLogger("Operator", "Operator").getUnderlyingLogger();
  private List<Handler> savedhandlers;

  @Before
  public void disableConsoleLogging() {
    savedhandlers = TestUtils.removeConsoleHandlers(UNDERLYING_LOGGER);
  }

  @After
  public void restoreConsoleLogging() {
    TestUtils.restoreConsoleHandlers(UNDERLYING_LOGGER, savedhandlers);
  }

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
    WlsClusterConfig wlsClusterConfig =
        new WlsClusterConfig("cluster1", createDynamicServersConfig(2, 5, "ms-", "clsuter1"));
    assertTrue(wlsClusterConfig.hasDynamicServers());
  }

  @Test
  public void verifyHasStaticServersIsFalseForDynamicCluster() throws Exception {
    WlsClusterConfig wlsClusterConfig =
        new WlsClusterConfig("cluster1", createDynamicServersConfig(2, 5, "ms-", "clsuter1"));
    assertFalse(wlsClusterConfig.hasStaticServers());
  }

  @Test
  public void verifyHasDynamicServersIsTrueForMixedCluster() throws Exception {
    WlsClusterConfig wlsClusterConfig =
        new WlsClusterConfig("cluster1", createDynamicServersConfig(2, 5, "ms-", "clsuter1"));
    wlsClusterConfig.addServerConfig(createWlsServerConfig("mss-0", 8011, null));
    assertTrue(wlsClusterConfig.hasDynamicServers());
  }

  @Test
  public void verifyHasStaticServersIsTrueForMixedCluster() throws Exception {
    WlsClusterConfig wlsClusterConfig =
        new WlsClusterConfig("cluster1", createDynamicServersConfig(2, 5, "ms-", "clsuter1"));
    wlsClusterConfig.addServerConfig(createWlsServerConfig("mss-0", 8011, null));
    assertTrue(wlsClusterConfig.hasStaticServers());
  }

  @Test
  public void verifyDynamicClusterSizeIsSameAsNumberOfDynamicServers() throws Exception {
    WlsClusterConfig wlsClusterConfig =
        new WlsClusterConfig("cluster1", createDynamicServersConfig(2, 5, "ms-", "clsuter1"));
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
  public void verifyGetServerConfigsReturnListOfAllServerConfigsWithDynamicServers()
      throws Exception {
    WlsClusterConfig wlsClusterConfig =
        new WlsClusterConfig("cluster1", createDynamicServersConfig(3, 5, "ms-", "clsuter1"));
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
    ClusterStartup cs = new ClusterStartup().withClusterName("cluster1").withReplicas(1);
    TestUtil.LogHandlerImpl handler = null;
    try {
      handler = TestUtil.setupLogHandler(wlsClusterConfig);
      wlsClusterConfig.validateClusterStartup(cs, null);
      assertTrue(
          "Message logged: " + handler.getAllFormattedMessage(),
          handler.hasWarningMessageWithSubString(
              "No servers configured in WebLogic cluster with name cluster1"));
    } finally {
      TestUtil.removeLogHandler(wlsClusterConfig, handler);
    }
  }

  @Test
  public void verifyValidateClusterStartupWarnsIfReplicasTooHigh() throws Exception {
    WlsClusterConfig wlsClusterConfig = new WlsClusterConfig("cluster1");
    wlsClusterConfig.addServerConfig(createWlsServerConfig("ms-0", 8011, null));
    ClusterStartup cs = new ClusterStartup().withClusterName("cluster1").withReplicas(2);
    TestUtil.LogHandlerImpl handler = null;
    try {
      handler = TestUtil.setupLogHandler(wlsClusterConfig);
      wlsClusterConfig.validateClusterStartup(cs, null);
      assertTrue(
          "Message logged: " + handler.getAllFormattedMessage(),
          handler.hasWarningMessageWithSubString(
              "Replicas in clusterStartup for cluster cluster1 is specified with a value of 2 which is larger than the number of configured WLS servers in the cluster: 1"));
    } finally {
      TestUtil.removeLogHandler(wlsClusterConfig, handler);
    }
  }

  @Test
  public void verifyValidateClusterStartupDoNotSuggestsUpdateToConfiguredClusterIfReplicasTooHigh()
      throws Exception {
    WlsClusterConfig wlsClusterConfig = new WlsClusterConfig("cluster1");
    wlsClusterConfig.addServerConfig(createWlsServerConfig("ms-0", 8011, null));
    ClusterStartup cs = new ClusterStartup().withClusterName("cluster1").withReplicas(2);
    ArrayList<ConfigUpdate> suggestedConfigUpdates = new ArrayList<>();
    wlsClusterConfig.validateClusterStartup(cs, suggestedConfigUpdates);
    assertEquals(0, suggestedConfigUpdates.size());
  }

  @Test
  public void verifyValidateClusterStartupWarnsIfReplicasTooHigh_DynamicCluster() throws Exception {
    WlsDynamicServersConfig wlsDynamicServersConfig =
        createDynamicServersConfig(1, 1, "ms-", "cluster1");
    WlsClusterConfig wlsClusterConfig = new WlsClusterConfig("cluster1", wlsDynamicServersConfig);
    ClusterStartup cs = new ClusterStartup().withClusterName("cluster1").withReplicas(2);
    TestUtil.LogHandlerImpl handler = null;
    try {
      handler = TestUtil.setupLogHandler(wlsClusterConfig);
      wlsClusterConfig.validateClusterStartup(cs, null);
      assertTrue(
          "Message logged: " + handler.getAllFormattedMessage(),
          handler.hasWarningMessageWithSubString(
              "Replicas in clusterStartup for cluster cluster1 is specified with a value of 2 which is larger than the number of configured WLS servers in the cluster: 1"));
    } finally {
      TestUtil.removeLogHandler(wlsClusterConfig, handler);
    }
  }

  @Test
  public void verifyValidateClusterStartupWarnsIfReplicasTooHigh_mixedCluster() throws Exception {
    WlsDynamicServersConfig wlsDynamicServersConfig =
        createDynamicServersConfig(1, 1, "ms-", "cluster1");
    WlsClusterConfig wlsClusterConfig = new WlsClusterConfig("cluster1", wlsDynamicServersConfig);
    wlsClusterConfig.addServerConfig(createWlsServerConfig("ms-0", 8011, null));
    ClusterStartup cs = new ClusterStartup().withClusterName("cluster1").withReplicas(3);
    TestUtil.LogHandlerImpl handler = null;
    try {
      handler = TestUtil.setupLogHandler(wlsClusterConfig);
      wlsClusterConfig.validateClusterStartup(cs, null);
      assertTrue(
          "Message logged: " + handler.getAllFormattedMessage(),
          handler.hasWarningMessageWithSubString(
              "Replicas in clusterStartup for cluster cluster1 is specified with a value of 3 which is larger than the number of configured WLS servers in the cluster: 2"));
    } finally {
      TestUtil.removeLogHandler(wlsClusterConfig, handler);
    }
  }

  @Test
  public void verifyValidateClusterStartupDoNotWarnIfReplicasNotHigh_mixedCluster()
      throws Exception {
    WlsDynamicServersConfig wlsDynamicServersConfig =
        createDynamicServersConfig(1, 1, "ms-", "cluster1");
    WlsClusterConfig wlsClusterConfig = new WlsClusterConfig("cluster1", wlsDynamicServersConfig);
    wlsClusterConfig.addServerConfig(createWlsServerConfig("ms-0", 8011, null));
    ClusterStartup cs = new ClusterStartup().withClusterName("cluster1").withReplicas(2);
    TestUtil.LogHandlerImpl handler = null;
    try {
      handler = TestUtil.setupLogHandler(wlsClusterConfig);
      wlsClusterConfig.validateClusterStartup(cs, null);
      assertFalse(
          "No message should be logged, but found: " + handler.getAllFormattedMessage(),
          handler.hasWarningMessageLogged());
    } finally {
      TestUtil.removeLogHandler(wlsClusterConfig, handler);
    }
  }

  @Test
  public void verifyValidateClusterStartupSuggestsUpdateToDynamicClusterIfReplicasTooHigh()
      throws Exception {
    WlsDynamicServersConfig wlsDynamicServersConfig =
        createDynamicServersConfig(1, 2, "ms-", "cluster1");
    WlsClusterConfig wlsClusterConfig = new WlsClusterConfig("cluster1", wlsDynamicServersConfig);
    ClusterStartup cs = new ClusterStartup().withClusterName("cluster1").withReplicas(2);
    ArrayList<ConfigUpdate> suggestedConfigUpdates = new ArrayList<>();
    wlsClusterConfig.validateClusterStartup(cs, suggestedConfigUpdates);
    assertEquals(1, suggestedConfigUpdates.size());
    WlsClusterConfig.DynamicClusterSizeConfigUpdate configUpdate =
        (WlsClusterConfig.DynamicClusterSizeConfigUpdate) suggestedConfigUpdates.get(0);
    assertEquals(2, configUpdate.targetClusterSize);
  }

  @Test
  public void
      verifyValidateClusterStartupDoNotSuggestsUpdateToDynamicClusterIfReplicasSameAsClusterSize()
          throws Exception {
    WlsDynamicServersConfig wlsDynamicServersConfig =
        createDynamicServersConfig(1, 2, "ms-", "cluster1");
    WlsClusterConfig wlsClusterConfig = new WlsClusterConfig("cluster1", wlsDynamicServersConfig);
    ClusterStartup cs = new ClusterStartup().withClusterName("cluster1").withReplicas(1);
    TestUtil.LogHandlerImpl handler = null;
    ArrayList<ConfigUpdate> suggestedConfigUpdates = new ArrayList<>();
    wlsClusterConfig.validateClusterStartup(cs, suggestedConfigUpdates);
    assertEquals(0, suggestedConfigUpdates.size());
  }

  @Test
  public void
      verifyValidateClusterStartupDoNotSuggestsUpdateToDynamicClusterIfReplicasLowerThanClusterSize()
          throws Exception {
    WlsDynamicServersConfig wlsDynamicServersConfig =
        createDynamicServersConfig(2, 2, "ms-", "cluster1");
    WlsClusterConfig wlsClusterConfig = new WlsClusterConfig("cluster1", wlsDynamicServersConfig);
    ClusterStartup cs = new ClusterStartup().withClusterName("cluster1").withReplicas(1);
    ArrayList<ConfigUpdate> suggestedConfigUpdates = new ArrayList<>();
    wlsClusterConfig.validateClusterStartup(cs, suggestedConfigUpdates);
    assertEquals(0, suggestedConfigUpdates.size());
  }

  @Test
  public void
      verifyValidateClusterStartupDoNotSuggestsUpdateToDynamicClusterIfCurrentSizeAlreadyMax()
          throws Exception {
    WlsDynamicServersConfig wlsDynamicServersConfig =
        createDynamicServersConfig(2, 2, "ms-", "cluster1");
    WlsClusterConfig wlsClusterConfig = new WlsClusterConfig("cluster1", wlsDynamicServersConfig);
    ClusterStartup cs = new ClusterStartup().withClusterName("cluster1").withReplicas(3);
    ArrayList<ConfigUpdate> suggestedConfigUpdates = new ArrayList<>();
    wlsClusterConfig.validateClusterStartup(cs, suggestedConfigUpdates);
    assertEquals(0, suggestedConfigUpdates.size());
  }

  @Test
  public void
      verifyValidateClusterStartupSuggestsUpdateToDynamicClusterEvenIfReplicasExceedsMaxClusterSize()
          throws Exception {
    WlsDynamicServersConfig wlsDynamicServersConfig =
        createDynamicServersConfig(1, 2, "ms-", "cluster1");
    WlsClusterConfig wlsClusterConfig = new WlsClusterConfig("cluster1", wlsDynamicServersConfig);
    ClusterStartup cs = new ClusterStartup().withClusterName("cluster1").withReplicas(10);
    ArrayList<ConfigUpdate> suggestedConfigUpdates = new ArrayList<>();
    wlsClusterConfig.validateClusterStartup(cs, suggestedConfigUpdates);
    WlsClusterConfig.DynamicClusterSizeConfigUpdate configUpdate =
        (WlsClusterConfig.DynamicClusterSizeConfigUpdate) suggestedConfigUpdates.get(0);
    assertEquals(2, configUpdate.targetClusterSize);
  }

  @Ignore // we are currently not suggesting updates based on number of machines
  @Test
  public void verifyValidateClusterStartupSuggestsUpdateToDynamicClusterIfNotEnoughMachines()
      throws Exception {
    WlsDynamicServersConfig wlsDynamicServersConfig =
        createDynamicServersConfig(2, 1, "ms-", "cluster1");
    WlsClusterConfig wlsClusterConfig = new WlsClusterConfig("cluster1", wlsDynamicServersConfig);

    Map<String, WlsClusterConfig> clusters = new HashMap();
    clusters.put(wlsClusterConfig.getClusterName(), wlsClusterConfig);

    WlsMachineConfig machine1 =
        new WlsMachineConfig("domain1-cluster1-machine1", 5556, "localhost", "SSL");
    Map<String, WlsMachineConfig> machines = new HashMap();
    machines.put(machine1.getName(), machine1);

    WlsDomainConfig wlsDomainConfig =
        new WlsDomainConfig("base_domain", clusters, null, null, machines);
    wlsClusterConfig.setWlsDomainConfig(wlsDomainConfig);

    ClusterStartup cs = new ClusterStartup().withClusterName("cluster1").withReplicas(2);

    ArrayList<ConfigUpdate> suggestedConfigUpdates = new ArrayList<>();
    wlsClusterConfig.validateClusterStartup(cs, suggestedConfigUpdates);
    assertEquals(1, suggestedConfigUpdates.size());
    WlsClusterConfig.DynamicClusterSizeConfigUpdate configUpdate =
        (WlsClusterConfig.DynamicClusterSizeConfigUpdate) suggestedConfigUpdates.get(0);
    assertEquals(2, configUpdate.targetClusterSize);
  }

  @Ignore // we are currently not suggesting updates based on number of machines
  @Test
  public void verifyValidateClusterStartupDoNotLowerClusterSizeIfNotEnoughMachines()
      throws Exception {
    WlsDynamicServersConfig wlsDynamicServersConfig =
        createDynamicServersConfig(2, 1, "ms-", "cluster1");
    WlsClusterConfig wlsClusterConfig = new WlsClusterConfig("cluster1", wlsDynamicServersConfig);

    Map<String, WlsClusterConfig> clusters = new HashMap();
    clusters.put(wlsClusterConfig.getClusterName(), wlsClusterConfig);

    Map<String, WlsMachineConfig> machines = new HashMap();

    WlsDomainConfig wlsDomainConfig =
        new WlsDomainConfig("base_domain", clusters, null, null, machines);
    wlsClusterConfig.setWlsDomainConfig(wlsDomainConfig);

    ClusterStartup cs = new ClusterStartup().withClusterName("cluster1").withReplicas(1);

    ArrayList<ConfigUpdate> suggestedConfigUpdates = new ArrayList<>();

    // replica = 1, dynamicClusterSize = 2, num of machines = 0 ==> need to create one machine
    // but need to ensure that the update will not reduce dynamicClusterSize from 2 to 1
    wlsClusterConfig.validateClusterStartup(cs, suggestedConfigUpdates);
    assertEquals(1, suggestedConfigUpdates.size());
    WlsClusterConfig.DynamicClusterSizeConfigUpdate configUpdate =
        (WlsClusterConfig.DynamicClusterSizeConfigUpdate) suggestedConfigUpdates.get(0);
    assertEquals(2, configUpdate.targetClusterSize);
  }

  @Ignore // we are currently not suggesting updates based on number of machines
  @Test
  public void verifyValidateClusterStartupDoNotSuggestUpdateToDynamicClusterIfEnoughMachines()
      throws Exception {
    WlsDynamicServersConfig wlsDynamicServersConfig =
        createDynamicServersConfig(1, 1, "ms-", "cluster1");
    WlsClusterConfig wlsClusterConfig = new WlsClusterConfig("cluster1", wlsDynamicServersConfig);

    Map<String, WlsClusterConfig> clusters = new HashMap();
    clusters.put(wlsClusterConfig.getClusterName(), wlsClusterConfig);

    WlsMachineConfig machine1 =
        new WlsMachineConfig("domain1-cluster1-machine1", 5556, "localhost", "SSL");
    Map<String, WlsMachineConfig> machines = new HashMap();
    machines.put(machine1.getName(), machine1);

    WlsDomainConfig wlsDomainConfig =
        new WlsDomainConfig("base_domain", clusters, null, null, machines);
    wlsClusterConfig.setWlsDomainConfig(wlsDomainConfig);

    ClusterStartup cs = new ClusterStartup().withClusterName("cluster1").withReplicas(1);

    ArrayList<ConfigUpdate> suggestedConfigUpdates = new ArrayList<>();
    wlsClusterConfig.validateClusterStartup(cs, suggestedConfigUpdates);
    assertEquals(0, suggestedConfigUpdates.size());
  }

  @Test
  public void verifyGetUpdateDynamicClusterSizeUrlIncludesClusterName() {
    WlsDynamicServersConfig wlsDynamicServersConfig =
        createDynamicServersConfig(1, 1, "ms-", "cluster1");
    WlsClusterConfig wlsClusterConfig = new WlsClusterConfig("cluster1", wlsDynamicServersConfig);
    assertEquals(
        wlsClusterConfig.getUpdateDynamicClusterSizeUrl(),
        "/management/weblogic/latest/edit/clusters/cluster1/dynamicServers");
  }

  @Test
  public void verifyGetUpdateDynamicClusterSizePayload() {
    WlsDynamicServersConfig wlsDynamicServersConfig =
        createDynamicServersConfig(1, 5, "ms-", "cluster1");
    WlsClusterConfig wlsClusterConfig = new WlsClusterConfig("cluster1", wlsDynamicServersConfig);
    assertEquals(
        wlsClusterConfig.getUpdateDynamicClusterSizePayload(2), "{ dynamicClusterSize: 2 }");
  }

  @Test
  public void verifyStepCreatedFromDynamicClusterSizeConfigUpdate()
      throws NoSuchFieldException, IllegalAccessException {
    final WlsClusterConfig wlsClusterConfig = new WlsClusterConfig("cluster1");
    final int clusterSize = 8;
    final Step nextStep = new MockStep(null);
    WlsClusterConfig.DynamicClusterSizeConfigUpdate dynamicClusterSizeConfigUpdate =
        new WlsClusterConfig.DynamicClusterSizeConfigUpdate(wlsClusterConfig, clusterSize);

    WlsRetriever.UpdateDynamicClusterStep updateStep =
        (WlsRetriever.UpdateDynamicClusterStep) dynamicClusterSizeConfigUpdate.createStep(nextStep);
    assertSame(wlsClusterConfig, updateStep.wlsClusterConfig);
    assertEquals(clusterSize, updateStep.targetClusterSize);
    assertSame(nextStep, getNext(updateStep));
  }

  @Test
  public void checkDynamicClusterSizeJsonResultReturnsFalseOnNull() {
    WlsClusterConfig wlsClusterConfig = new WlsClusterConfig("someCluster");
    assertFalse(wlsClusterConfig.checkUpdateDynamicClusterSizeJsonResult(null));
  }

  @Test
  public void checkDynamicClusterSizeJsonResultReturnsFalseOnUnexpectedString() {
    WlsClusterConfig wlsClusterConfig = new WlsClusterConfig("someCluster");
    assertFalse(wlsClusterConfig.checkUpdateDynamicClusterSizeJsonResult("{ xyz }"));
  }

  @Test
  public void checkDynamicClusterSizeJsonResultReturnsTrueWithExpectedString() {
    WlsClusterConfig wlsClusterConfig = new WlsClusterConfig("someCluster");
    assertTrue(wlsClusterConfig.checkUpdateDynamicClusterSizeJsonResult("{}"));
  }

  private WlsServerConfig createWlsServerConfig(
      String serverName, Integer listenPort, String listenAddress) {
    Map<String, Object> serverConfigMap = new HashMap<>();
    serverConfigMap.put("name", serverName);
    serverConfigMap.put("listenPort", listenPort);
    serverConfigMap.put("listenAddress", listenAddress);
    return WlsServerConfig.create(serverConfigMap);
  }

  @Test
  public void verifyMachinesConfiguredReturnTrueIfAllMachinesConfigured() {
    WlsMachineConfig machine1 = new WlsMachineConfig("domain1-machine1", 5556, "localhost", "SSL");
    WlsMachineConfig machine2 = new WlsMachineConfig("domain1-machine2", 5556, "localhost", "SSL");
    Map<String, WlsMachineConfig> machines = new HashMap();
    machines.put(machine1.getName(), machine1);
    machines.put(machine2.getName(), machine2);

    WlsClusterConfig wlsClusterConfig =
        new WlsClusterConfig(
            "cluster1", WlsClusterConfigTest.createDynamicServersConfig(2, 5, "ms-", "cluster1"));
    Map<String, WlsClusterConfig> clusters = new HashMap();
    clusters.put(wlsClusterConfig.getClusterName(), wlsClusterConfig);

    WlsDomainConfig wlsDomainConfig =
        new WlsDomainConfig("base_domain", clusters, null, null, machines);
    wlsClusterConfig.setWlsDomainConfig(wlsDomainConfig);

    assertTrue(wlsClusterConfig.verifyMachinesConfigured("domain1-machine", 2));
  }

  @Test
  public void verifyMachinesConfiguredReturnFalseIfNotAllMachinesConfigured() {
    WlsMachineConfig machine1 = new WlsMachineConfig("domain1-machine1", 5556, "localhost", "SSL");
    WlsMachineConfig machine2 = new WlsMachineConfig("domain1-machine2", 5556, "localhost", "SSL");
    Map<String, WlsMachineConfig> machines = new HashMap();
    machines.put(machine1.getName(), machine1);
    machines.put(machine2.getName(), machine2);

    WlsClusterConfig wlsClusterConfig =
        new WlsClusterConfig(
            "cluster1", WlsClusterConfigTest.createDynamicServersConfig(2, 5, "ms-", "cluster1"));
    Map<String, WlsClusterConfig> clusters = new HashMap();
    clusters.put(wlsClusterConfig.getClusterName(), wlsClusterConfig);

    WlsDomainConfig wlsDomainConfig =
        new WlsDomainConfig("base_domain", clusters, null, null, machines);
    wlsClusterConfig.setWlsDomainConfig(wlsDomainConfig);

    assertFalse(wlsClusterConfig.verifyMachinesConfigured("domain1-machine", 3));
  }

  @Test
  public void verifyMachinesConfiguredReturnTrueIfNoWlsDomainConfig() {
    Map<String, WlsMachineConfig> machines = new HashMap();

    WlsClusterConfig wlsClusterConfig =
        new WlsClusterConfig(
            "cluster1", WlsClusterConfigTest.createDynamicServersConfig(2, 5, "ms-", "cluster1"));
    Map<String, WlsClusterConfig> clusters = new HashMap();
    clusters.put(wlsClusterConfig.getClusterName(), wlsClusterConfig);

    assertNull(wlsClusterConfig.getWlsDomainConfig());
    assertTrue(wlsClusterConfig.verifyMachinesConfigured("domain1-machine", 2));
  }

  @Test
  public void verifyMachinesConfiguredReturnTrueIfPrefixIsNull() {
    Map<String, WlsMachineConfig> machines = new HashMap();

    WlsClusterConfig wlsClusterConfig =
        new WlsClusterConfig(
            "cluster1", WlsClusterConfigTest.createDynamicServersConfig(2, 5, "ms-", "cluster1"));
    Map<String, WlsClusterConfig> clusters = new HashMap();
    clusters.put(wlsClusterConfig.getClusterName(), wlsClusterConfig);

    WlsDomainConfig wlsDomainConfig =
        new WlsDomainConfig("base_domain", clusters, null, null, machines);
    wlsClusterConfig.setWlsDomainConfig(wlsDomainConfig);

    assertNotNull(wlsClusterConfig.getWlsDomainConfig());
    assertTrue(wlsClusterConfig.verifyMachinesConfigured(null, 2));
  }

  @Test
  public void verifyGetMachinesNameReturnsExpectedMachineName() {
    WlsMachineConfig machine1 = new WlsMachineConfig("domain1-machine1", 5556, "localhost", "SSL");
    WlsMachineConfig machine2 = new WlsMachineConfig("domain1-machine2", 5556, "localhost", "SSL");
    Map<String, WlsMachineConfig> machines = new HashMap();
    machines.put(machine1.getName(), machine1);
    machines.put(machine2.getName(), machine2);

    WlsClusterConfig wlsClusterConfig =
        new WlsClusterConfig(
            "cluster1", WlsClusterConfigTest.createDynamicServersConfig(2, 5, "ms-", "cluster1"));
    Map<String, WlsClusterConfig> clusters = new HashMap();
    clusters.put(wlsClusterConfig.getClusterName(), wlsClusterConfig);

    WlsDomainConfig wlsDomainConfig =
        new WlsDomainConfig("base_domain", clusters, null, null, machines);
    wlsClusterConfig.setWlsDomainConfig(wlsDomainConfig);

    String names[] = wlsClusterConfig.getMachineNamesForDynamicServers("domain1-machine", 4);
    assertEquals(2, names.length);
    assertEquals("domain1-machine3", names[0]);
    assertEquals("domain1-machine4", names[1]);
  }

  @Test
  public void verifyGetMachinesNameDoesNotReturnExistingMachines() {
    WlsMachineConfig machine1 = new WlsMachineConfig("domain1-machine1", 5556, "localhost", "SSL");
    WlsMachineConfig machine2 = new WlsMachineConfig("domain1-machine2", 5556, "localhost", "SSL");
    WlsMachineConfig machine3 = new WlsMachineConfig("domain1-machine3", 5556, "localhost", "SSL");
    Map<String, WlsMachineConfig> machines = new HashMap();
    machines.put(machine1.getName(), machine1);
    machines.put(machine2.getName(), machine2);
    machines.put(machine3.getName(), machine3);

    WlsClusterConfig wlsClusterConfig =
        new WlsClusterConfig(
            "cluster1", WlsClusterConfigTest.createDynamicServersConfig(1, 5, "ms-", "cluster1"));
    Map<String, WlsClusterConfig> clusters = new HashMap();
    clusters.put(wlsClusterConfig.getClusterName(), wlsClusterConfig);

    WlsDomainConfig wlsDomainConfig =
        new WlsDomainConfig("base_domain", clusters, null, null, machines);
    wlsClusterConfig.setWlsDomainConfig(wlsDomainConfig);

    String names[] = wlsClusterConfig.getMachineNamesForDynamicServers("domain1-machine", 3);
    assertEquals(0, names.length);
  }

  @Test
  public void verifyGetMachinesNameReturnsEmptyArrayIfNoDomainConfig() {
    WlsMachineConfig machine1 = new WlsMachineConfig("domain1-machine1", 5556, "localhost", "SSL");
    WlsMachineConfig machine2 = new WlsMachineConfig("domain1-machine2", 5556, "localhost", "SSL");
    Map<String, WlsMachineConfig> machines = new HashMap();
    machines.put(machine1.getName(), machine1);
    machines.put(machine2.getName(), machine2);

    WlsClusterConfig wlsClusterConfig =
        new WlsClusterConfig(
            "cluster1", WlsClusterConfigTest.createDynamicServersConfig(2, 5, "ms-", "cluster1"));

    assertNull("verify no domain config is setup", wlsClusterConfig.getWlsDomainConfig());

    String names[] = wlsClusterConfig.getMachineNamesForDynamicServers("domain1-machine", 4);
    assertEquals(0, names.length);
  }

  @Test
  public void verifyGetMachineNamesNoExceptionWhenPrefixIsNull() {
    WlsClusterConfig wlsClusterConfig =
        new WlsClusterConfig(
            "cluster1", WlsClusterConfigTest.createDynamicServersConfig(1, 5, "ms-", "cluster1"));
    Map<String, WlsClusterConfig> clusters = new HashMap();
    clusters.put(wlsClusterConfig.getClusterName(), wlsClusterConfig);

    WlsDomainConfig wlsDomainConfig =
        new WlsDomainConfig("base_domain", clusters, null, null, null);
    wlsClusterConfig.setWlsDomainConfig(wlsDomainConfig);

    String[] names = wlsClusterConfig.getMachineNamesForDynamicServers(null, 2);
    assertEquals(2, names.length);
    assertEquals("1", names[0]);
    assertEquals("2", names[1]);
  }

  @Test
  public void verifyGetMachineNamesReturnsEmptyArrayWhenNumIsInvalid() {
    WlsClusterConfig wlsClusterConfig =
        new WlsClusterConfig(
            "cluster1", WlsClusterConfigTest.createDynamicServersConfig(0, 5, "ms-", "clsuter1"));
    Map<String, WlsClusterConfig> clusters = new HashMap();
    clusters.put(wlsClusterConfig.getClusterName(), wlsClusterConfig);

    WlsDomainConfig wlsDomainConfig =
        new WlsDomainConfig("base_domain", clusters, null, null, null);
    wlsClusterConfig.setWlsDomainConfig(wlsDomainConfig);
    String[] names = wlsClusterConfig.getMachineNamesForDynamicServers("domain1-machine", 0);
    assertEquals(0, names.length);

    names = wlsClusterConfig.getMachineNamesForDynamicServers("domain1-machine", -1);
    assertEquals(0, names.length);
  }

  @Test
  public void verifyGetMachineNamesReturnsUndefinedMachineNamesEvenWithSameTargetSize() {
    WlsClusterConfig wlsClusterConfig =
        new WlsClusterConfig(
            "cluster1", WlsClusterConfigTest.createDynamicServersConfig(2, 5, "ms-", "clsuter1"));
    Map<String, WlsClusterConfig> clusters = new HashMap();
    clusters.put(wlsClusterConfig.getClusterName(), wlsClusterConfig);

    WlsDomainConfig wlsDomainConfig =
        new WlsDomainConfig("base_domain", clusters, null, null, null);
    wlsClusterConfig.setWlsDomainConfig(wlsDomainConfig);
    String[] names = wlsClusterConfig.getMachineNamesForDynamicServers("domain1-machine", 2);
    assertEquals(2, names.length);
    assertEquals("domain1-machine1", names[0]);
    assertEquals("domain1-machine2", names[1]);
  }

  static WlsDynamicServersConfig createDynamicServersConfig(
      int clusterSize, int maxClusterSize, String serverNamePrefix, String clusterName) {
    WlsServerConfig serverTemplate =
        new WlsServerConfig("serverTemplate1", 7001, "host1", 7002, false, null, null);
    List<String> serverNames = new ArrayList<>();
    final int startingServerNameIndex = 1;
    for (int i = 0; i < clusterSize; i++) {
      serverNames.add(serverNamePrefix + (i + startingServerNameIndex));
    }
    List<WlsServerConfig> serverConfigs =
        WlsDynamicServersConfig.createServerConfigsFromTemplate(
            serverNames, serverTemplate, clusterName, "base-domain", false);

    return new WlsDynamicServersConfig(
        clusterSize, maxClusterSize, serverNamePrefix, false, null, serverTemplate, serverConfigs);
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

  Field nextField;

  Step getNext(Step step) throws IllegalAccessException, NoSuchFieldException {
    if (nextField == null) {
      nextField = Step.class.getDeclaredField("next");
      nextField.setAccessible(true);
    }
    return (Step) nextField.get(step);
  }
}

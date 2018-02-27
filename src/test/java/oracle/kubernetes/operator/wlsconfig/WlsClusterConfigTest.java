// Copyright 2017, 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.wlsconfig;

import oracle.kubernetes.TestUtils;
import oracle.kubernetes.operator.domain.model.oracle.kubernetes.weblogic.domain.v1.ClusterStartup;
import oracle.kubernetes.operator.helpers.ClientHelper;
import oracle.kubernetes.operator.work.NextAction;
import oracle.kubernetes.operator.work.Packet;
import oracle.kubernetes.operator.work.Step;
import oracle.kubernetes.operator.logging.LoggingFactory;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Handler;
import java.util.logging.Logger;

import static org.junit.Assert.*;

/**
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
 */
public class WlsClusterConfigTest {

    private static final Logger UNDERLYING_LOGGER = LoggingFactory.getLogger("Operator", "Operator").getUnderlyingLogger();
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
      wlsClusterConfig.validateClusterStartup(cs, null);
      assertTrue("Message logged: " + handler.getAllFormattedMessage(), handler.hasWarningMessageWithSubString("No servers configured in WebLogic cluster with name cluster1"));
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
      wlsClusterConfig.validateClusterStartup(cs, null);
      assertTrue("Message logged: " + handler.getAllFormattedMessage(), handler.hasWarningMessageWithSubString("Replicas in clusterStartup for cluster cluster1 is specified with a value of 2 which is larger than the number of configured WLS servers in the cluster: 1"));
    } finally {
      TestUtil.removeLogHandler(wlsClusterConfig, handler);
    }
  }

  @Test
  public void verifyValidateClusterStartupDoNotSuggestsUpdateToConfiguredClusterIfReplicasTooHigh() throws Exception {
    WlsClusterConfig wlsClusterConfig = new WlsClusterConfig("cluster1");
    wlsClusterConfig.addServerConfig(createWlsServerConfig("ms-0", 8011, null));
    ClusterStartup cs = new ClusterStartup().clusterName("cluster1").replicas(2);
    TestUtil.LogHandlerImpl handler = null;
    try {
      handler = TestUtil.setupLogHandler(wlsClusterConfig);
      ArrayList<ConfigUpdate> suggestedConfigUpdates = new ArrayList<>();
      wlsClusterConfig.validateClusterStartup(cs, suggestedConfigUpdates);
      assertEquals(0, suggestedConfigUpdates.size());
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
      wlsClusterConfig.validateClusterStartup(cs, null);
      assertFalse("No message should be logged, but found: " + handler.getAllFormattedMessage(), handler.hasWarningMessageLogged());
    } finally {
      TestUtil.removeLogHandler(wlsClusterConfig, handler);
    }
  }

  @Test
  public void verifyValidateClusterStartupSuggestsUpdateToDynamicClusterIfReplicasTooHigh() throws Exception {
    WlsDynamicServersConfig wlsDynamicServersConfig = createDynamicServersConfig(1, 1, "ms-", "cluster1");
    WlsClusterConfig wlsClusterConfig = new WlsClusterConfig("cluster1", wlsDynamicServersConfig);
    ClusterStartup cs = new ClusterStartup().clusterName("cluster1").replicas(2);
    TestUtil.LogHandlerImpl handler = null;
    try {
      handler = TestUtil.setupLogHandler(wlsClusterConfig);
      ArrayList<ConfigUpdate> suggestedConfigUpdates = new ArrayList<>();
      wlsClusterConfig.validateClusterStartup(cs, suggestedConfigUpdates);
      assertEquals(1, suggestedConfigUpdates.size());
      assertTrue(suggestedConfigUpdates.get(0) instanceof WlsClusterConfig.DynamicClusterSizeConfigUpdate);
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

  @Test
  public void verifyDynamicClusterSizeConfigUpdateCallsUpdateDynamicClusterSize() {
    final WlsClusterConfig wlsClusterConfig = new WlsClusterConfig("cluster1");
    final int clusterSize = 8;
    WlsClusterConfig.DynamicClusterSizeConfigUpdate dynamicClusterSizeConfigUpdate =
      new WlsClusterConfig.DynamicClusterSizeConfigUpdate(wlsClusterConfig, clusterSize);
    MockWlsConfigRetriever mockWlsConfigRetriever = new MockWlsConfigRetriever(null, "namespace",
      "asServiceName", "adminSecretName");
    assertTrue(dynamicClusterSizeConfigUpdate.doUpdate(mockWlsConfigRetriever));
    assertSame(wlsClusterConfig, mockWlsConfigRetriever.wlsClusterConfigParamValue);
    assertEquals(clusterSize, mockWlsConfigRetriever.clusterSizeParamvalue);
  }

  @Test
  public void verifyStepCreatedFromDynamicClusterSizeConfigUpdate() throws NoSuchFieldException, IllegalAccessException {
    final WlsClusterConfig wlsClusterConfig = new WlsClusterConfig("cluster1");
    final int clusterSize = 8;
    final Step nextStep = new MockStep(null);
    WlsClusterConfig.DynamicClusterSizeConfigUpdate dynamicClusterSizeConfigUpdate =
      new WlsClusterConfig.DynamicClusterSizeConfigUpdate(wlsClusterConfig, clusterSize);

    WlsConfigRetriever.UpdateDynamicClusterStep updateStep =
      (WlsConfigRetriever.UpdateDynamicClusterStep) dynamicClusterSizeConfigUpdate.createStep(nextStep);
    assertSame(wlsClusterConfig, updateStep.wlsClusterConfig);
    assertEquals(clusterSize, updateStep.desiredClusterSize);
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

  static class MockWlsConfigRetriever extends WlsConfigRetriever {

    WlsClusterConfig wlsClusterConfigParamValue;
    int clusterSizeParamvalue;

    public MockWlsConfigRetriever(ClientHelper clientHelper, String namespace, String asServiceName, String adminSecretName) {
      super(clientHelper, namespace, asServiceName, adminSecretName);
    }

    @Override
    public boolean updateDynamicClusterSize(WlsClusterConfig wlsClusterConfig, int clusterSize) {
      wlsClusterConfigParamValue = wlsClusterConfig;
      clusterSizeParamvalue = clusterSize;
      return true;
    }
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

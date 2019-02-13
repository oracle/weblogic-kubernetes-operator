// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helpers;

import static com.meterware.simplestub.Stub.createStrictStub;
import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertTrue;
import static oracle.kubernetes.LogMatcher.containsFine;
import static oracle.kubernetes.LogMatcher.containsInfo;
import static oracle.kubernetes.operator.ProcessingConstants.SCRIPT_CONFIG_MAP;
import static oracle.kubernetes.operator.logging.MessageKeys.CM_CREATED;
import static oracle.kubernetes.operator.logging.MessageKeys.CM_EXISTS;
import static oracle.kubernetes.operator.logging.MessageKeys.CM_REPLACED;
import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.Matchers.sameInstance;
import static org.hamcrest.junit.MatcherAssert.assertThat;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;

import com.meterware.simplestub.Memento;
import com.meterware.simplestub.StaticStubSupport;
import io.kubernetes.client.ApiException;
import io.kubernetes.client.models.V1ConfigMap;
import io.kubernetes.client.models.V1ObjectMeta;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.net.HttpURLConnection;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import oracle.kubernetes.TestUtils;
import oracle.kubernetes.operator.KubernetesConstants;
import oracle.kubernetes.operator.LabelConstants;
import oracle.kubernetes.operator.VersionConstants;
import oracle.kubernetes.operator.wlsconfig.NetworkAccessPoint;
import oracle.kubernetes.operator.wlsconfig.WlsClusterConfig;
import oracle.kubernetes.operator.wlsconfig.WlsDomainConfig;
import oracle.kubernetes.operator.wlsconfig.WlsDynamicServersConfig;
import oracle.kubernetes.operator.wlsconfig.WlsServerConfig;
import oracle.kubernetes.operator.work.Packet;
import oracle.kubernetes.operator.work.Step;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

public class ConfigMapHelperTest {
  private static final String DOMAIN_NS = "namespace";
  private static final String OPERATOR_NS = "operator";
  private static final String DOMAIN_UID = "domainUID1";
  static final String[] SCRIPT_NAMES = {
    "livenessProbe.sh",
    "readState.sh",
    "start-server.py",
    "startServer.sh",
    "stop-server.py",
    "stopServer.sh",
    "introspectDomain.sh",
    "introspectDomain.py",
    "startNodeManager.sh",
    "traceUtils.py",
    "traceUtils.sh",
    "wlst.sh"
  };

  private static final String introspectResult =
      ">>>  /u01/introspect/domain1/userConfigNodeManager.secure\n"
          + "#WebLogic User Configuration File; 2\n"
          + "#Thu Oct 04 21:07:06 GMT 2018\n"
          + "weblogic.management.username={AES}fq11xKVoE927O07IUKhQ00d4A8QY598Dvd+KSnHNTEA\\=\n"
          + "weblogic.management.password={AES}LIxVY+aqI8KBkmlBTwkvAnQYQs4PS0FX3Ili4uLBggo\\=\n"
          + "\n"
          + ">>> EOF\n"
          + "\n"
          + "@[2018-10-04T21:07:06.864 UTC][introspectDomain.py:105] Printing file /u01/introspect/domain1/userKeyNodeManager.secure\n"
          + "\n"
          + ">>>  /u01/introspect/domain1/userKeyNodeManager.secure\n"
          + "BPtNabkCIIc2IJp/TzZ9TzbUHG7O3xboteDytDO3XnwNhumdSpaUGKmcbusdmbOUY+4J2kteu6xJPWTzmNRAtg==\n"
          + "\n"
          + ">>> EOF\n"
          + "\n"
          + "@[2018-10-04T21:07:06.867 UTC][introspectDomain.py:105] Printing file /u01/introspect/domain1/topology.yaml\n"
          + "\n"
          + ">>>  /u01/introspect/domain1/topology.yaml\n"
          + "domainValid: true\n"
          + "domain:\n"
          + "  name: \"base_domain\"\n"
          + "  adminServerName: \"admin-server\"\n"
          + "  configuredClusters:\n"
          + "    \"mycluster\":\n"
          + "      port: 8001\n"
          + "      servers:\n"
          + "        \"managed-server1\": {}\n"
          + "        \"managed-server2\": {}\n"
          + "  dynamicClusters: {}\n"
          + "  servers:\n"
          + "    \"admin-server\":\n"
          + "      port: 7001\n"
          + "\n"
          + ">>> EOF";

  private static final String[] PARTIAL_SCRIPT_NAMES = {"livenessProbe.sh", "additional.sh"};
  private static final String[] COMBINED_SCRIPT_NAMES = combine(SCRIPT_NAMES, PARTIAL_SCRIPT_NAMES);

  private final V1ConfigMap defaultConfigMap = defineDefaultConfigMap();
  private RetryStrategyStub retryStrategy = createStrictStub(RetryStrategyStub.class);

  private AsyncCallTestSupport testSupport = new AsyncCallTestSupport();
  private List<Memento> mementos = new ArrayList<>();
  private List<LogRecord> logRecords = new ArrayList<>();

  private V1ConfigMap defineDefaultConfigMap() {
    return defineConfigMap(SCRIPT_NAMES);
  }

  private V1ConfigMap defineConfigMap(String... scriptNames) {
    return new V1ConfigMap()
        .apiVersion("v1")
        .kind("ConfigMap")
        .metadata(createMetadata())
        .data(nameOnlyScriptMap(scriptNames));
  }

  @SuppressWarnings("SameParameterValue")
  private static String[] combine(String[] first, String[] second) {
    return Stream.of(first, second).flatMap(Stream::of).distinct().toArray(String[]::new);
  }

  private static Map<String, String> nameOnlyScriptMap(String... scriptNames) {
    return Stream.of(scriptNames).collect(Collectors.toMap(s -> s, s -> ""));
  }

  private V1ObjectMeta createMetadata() {
    return new V1ObjectMeta()
        .name(KubernetesConstants.DOMAIN_CONFIG_MAP_NAME)
        .namespace(DOMAIN_NS)
        .putLabelsItem(
            LabelConstants.RESOURCE_VERSION_LABEL, VersionConstants.DEFAULT_DOMAIN_VERSION)
        .putLabelsItem(LabelConstants.OPERATORNAME_LABEL, OPERATOR_NS)
        .putLabelsItem(LabelConstants.CREATEDBYOPERATOR_LABEL, "true");
  }

  @Before
  public void setUp() throws Exception {
    mementos.add(
        TestUtils.silenceOperatorLogger()
            .collectLogMessages(logRecords, CM_CREATED, CM_EXISTS, CM_REPLACED)
            .withLogLevel(Level.FINE));
    mementos.add(testSupport.installRequestStepFactory());
    mementos.add(TestComparator.install());
  }

  @After
  public void tearDown() throws Exception {
    for (Memento memento : mementos) memento.revert();

    testSupport.throwOnCompletionFailure();
    testSupport.verifyAllDefinedResponsesInvoked();
  }

  @Test
  @Ignore("TBD Fails on introspector branch, passes intermittently on develop branch.")
  public void whenUnableToReadConfigMap_reportFailure() {
    testSupport.addRetryStrategy(retryStrategy);
    expectReadConfigMap().failingWithStatus(401);

    Step scriptConfigMapStep = ConfigMapHelper.createScriptConfigMapStep(OPERATOR_NS, DOMAIN_NS);
    testSupport.runSteps(scriptConfigMapStep);

    testSupport.verifyCompletionThrowable(ApiException.class);
  }

  @Test
  @Ignore("TBD Fails on introspector branch, passes intermittently on develop branch.")
  public void whenNoConfigMap_createIt() {
    expectReadConfigMap().failingWithStatus(HttpURLConnection.HTTP_NOT_FOUND);
    expectSuccessfulCreateConfigMap(defaultConfigMap);

    testSupport.runSteps(ConfigMapHelper.createScriptConfigMapStep(OPERATOR_NS, DOMAIN_NS));

    assertThat(logRecords, containsInfo(CM_CREATED));
  }

  @Test
  @Ignore("TBD Fails on introspector branch, passes intermittently on develop branch.")
  public void whenNoConfigMap_retryOnFailure() {
    testSupport.addRetryStrategy(retryStrategy);
    expectReadConfigMap().failingWithStatus(HttpURLConnection.HTTP_NOT_FOUND);
    expectCreateConfigMap(defaultConfigMap).failingWithStatus(401);

    Step scriptConfigMapStep = ConfigMapHelper.createScriptConfigMapStep(OPERATOR_NS, DOMAIN_NS);
    testSupport.runSteps(scriptConfigMapStep);

    testSupport.verifyCompletionThrowable(ApiException.class);
    assertThat(retryStrategy.getConflictStep(), sameInstance(scriptConfigMapStep));
  }

  @SuppressWarnings("unchecked")
  @Test
  @Ignore("TBD Fails on introspector branch, passes intermittently on develop branch.")
  public void whenMatchingConfigMapExists_addToPacket() {
    expectReadConfigMap().returning(defaultConfigMap);

    Packet packet =
        testSupport.runSteps(ConfigMapHelper.createScriptConfigMapStep(OPERATOR_NS, DOMAIN_NS));

    assertThat(packet, hasEntry(SCRIPT_CONFIG_MAP, defaultConfigMap));
    assertThat(logRecords, containsFine(CM_EXISTS));
  }

  @SuppressWarnings("unchecked")
  @Test
  @Ignore("TBD Fails on introspector branch, passes intermittently on develop branch.")
  public void whenExistingConfigMapIsMissingData_replaceIt() {
    expectReadConfigMap().returning(defineConfigMap(PARTIAL_SCRIPT_NAMES));
    expectSuccessfulReplaceConfigMap(defineConfigMap(COMBINED_SCRIPT_NAMES));

    testSupport.runSteps(ConfigMapHelper.createScriptConfigMapStep(OPERATOR_NS, DOMAIN_NS));

    assertThat(logRecords, containsInfo(CM_REPLACED));
  }

  @SuppressWarnings("unchecked")
  @Test
  @Ignore("TBD Fails on introspector branch, passes intermittently on develop branch.")
  public void whenReplaceFails_scheduleRetry() {
    testSupport.addRetryStrategy(retryStrategy);
    expectReadConfigMap().returning(defineConfigMap(PARTIAL_SCRIPT_NAMES));
    expectReplaceConfigMap(defineConfigMap(COMBINED_SCRIPT_NAMES)).failingWithStatus(401);

    Step scriptConfigMapStep = ConfigMapHelper.createScriptConfigMapStep(OPERATOR_NS, DOMAIN_NS);
    testSupport.runSteps(scriptConfigMapStep);

    testSupport.verifyCompletionThrowable(ApiException.class);
    assertThat(retryStrategy.getConflictStep(), sameInstance(scriptConfigMapStep));
  }

  // @Test
  @Ignore
  public void parseIntrospectorResult() {
    Map<String, String> result =
        ConfigMapHelper.parseIntrospectorResult(introspectResult, DOMAIN_UID);
    System.out.println("ConfigMapHelperTest.parseIntrospectorResult: " + result);
    assertEquals(3, result.size());
    assertTrue(result.containsKey("userConfigNodeManager.secure"));
    assertTrue(result.containsKey("userKeyNodeManager.secure"));
    assertTrue(result.containsKey("topology.yaml"));
  }

  // @Test
  @Ignore
  public void readSingleFile() throws IOException {
    Map<String, String> map = new HashMap<>();
    String text =
        ">>>  /u01/introspect/domain1/userConfigNodeManager.secure\n"
            + "#WebLogic User Configuration File; 2\n"
            + "#Thu Oct 04 21:07:06 GMT 2018\n"
            + "weblogic.management.username={AES}fq11xKVoE927O07IUKhQ00d4A8QY598Dvd+KSnHNTEA\\=\n"
            + "weblogic.management.password={AES}LIxVY+aqI8KBkmlBTwkvAnQYQs4PS0FX3Ili4uLBggo\\=\n"
            + "\n"
            + ">>> EOF\n"
            + "\n"
            + "@[2018-10-04T21:07:06.864 UTC][introspectDomain.py:105] Printing file /u01/introspect/domain1/userKeyNodeManager.secure\n"
            + "\n";

    BufferedReader reader = new BufferedReader(new StringReader(text));
    String line = reader.readLine();
    System.out.println("ConfigMapHelperTest.readSingleFile line: " + line);
    assertTrue(line.startsWith(">>>"));
    String fileName = ConfigMapHelper.extractFilename(line);
    System.out.println("ConfigMapHelperTest.readSingleFile fileName: " + fileName);
    ConfigMapHelper.readFile(reader, fileName, map, DOMAIN_UID);
    System.out.println("ConfigMapHelperTest.readSingleFile map: " + map);
    assertEquals(1, map.size());
    assertTrue(map.containsKey("userConfigNodeManager.secure"));
  }

  private CallTestSupport.CannedResponse expectReadConfigMap() {
    return testSupport
        .createCannedResponse("readConfigMap")
        .withNamespace(DOMAIN_NS)
        .withName(KubernetesConstants.DOMAIN_CONFIG_MAP_NAME);
  }

  @SuppressWarnings("unchecked")
  private void expectSuccessfulCreateConfigMap(V1ConfigMap expectedConfig) {
    expectCreateConfigMap(expectedConfig).returning(expectedConfig);
  }

  private CallTestSupport.CannedResponse expectCreateConfigMap(V1ConfigMap expectedConfig) {
    return testSupport
        .createCannedResponse("createConfigMap")
        .withNamespace(DOMAIN_NS)
        .withBody(new V1ConfigMapMatcher(expectedConfig));
  }

  @SuppressWarnings("unchecked")
  private void expectSuccessfulReplaceConfigMap(V1ConfigMap expectedConfig) {
    expectReplaceConfigMap(expectedConfig).returning(expectedConfig);
  }

  private CallTestSupport.CannedResponse expectReplaceConfigMap(V1ConfigMap expectedConfig) {
    return testSupport
        .createCannedResponse("replaceConfigMap")
        .withNamespace(DOMAIN_NS)
        .withName(KubernetesConstants.DOMAIN_CONFIG_MAP_NAME)
        .withBody(new V1ConfigMapMatcher(expectedConfig));
  }

  class V1ConfigMapMatcher implements BodyMatcher {
    private V1ConfigMap expected;

    V1ConfigMapMatcher(V1ConfigMap expected) {
      this.expected = expected;
    }

    @Override
    public boolean matches(Object actualBody) {
      return actualBody instanceof V1ConfigMap && matches((V1ConfigMap) actualBody);
    }

    private boolean matches(V1ConfigMap actualBody) {
      return hasExpectedKeys(actualBody) && adjustedBody(actualBody).equals(actualBody);
    }

    private boolean hasExpectedKeys(V1ConfigMap actualBody) {
      return expected.getData().keySet().equals(actualBody.getData().keySet());
    }

    private V1ConfigMap adjustedBody(V1ConfigMap actualBody) {
      return new V1ConfigMap()
          .apiVersion(expected.getApiVersion())
          .kind(expected.getKind())
          .metadata(expected.getMetadata())
          .data(actualBody.getData());
    }
  }

  // An implementation of the comparator that tests only the keys in the maps
  static class TestComparator implements ConfigMapHelper.ConfigMapComparator {
    static Memento install() throws NoSuchFieldException {
      return StaticStubSupport.install(ConfigMapHelper.class, "COMPARATOR", new TestComparator());
    }

    @Override
    public boolean containsAll(V1ConfigMap actual, V1ConfigMap expected) {
      return actual.getData().keySet().containsAll(expected.getData().keySet());
    }
  }

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
    ConfigMapHelper.DomainTopology domainTopology =
        ConfigMapHelper.parseDomainTopologyYaml(DOMAIN_TOPOLOGY);

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
    List<NetworkAccessPoint> server2ConfigNAPs = server2Config.getNetworkAccessPoints();
    assertEquals(1, server2ConfigNAPs.size());

    NetworkAccessPoint server2ConfigNAP = server2ConfigNAPs.get(0);
    assertEquals("nap2", server2ConfigNAP.getName());
    assertEquals("t3", server2ConfigNAP.getProtocol());
    assertEquals(8005, server2ConfigNAP.getListenPort().intValue());
    assertEquals(8005, server2ConfigNAP.getPublicPort().intValue());
  }

  @Test
  public void parseDynamicServerTopologyYaml() {
    ConfigMapHelper.DomainTopology domainTopology =
        ConfigMapHelper.parseDomainTopologyYaml(DYNAMIC_SERVER_TOPOLOGY);

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
    assertFalse(
        "Expected calculatedListenPorts false", wlsDynamicServersConfig.getCalculatedListenPorts());
    assertEquals("managed-server", wlsDynamicServersConfig.getServerNamePrefix());
    assertEquals(4, wlsDynamicServersConfig.getDynamicClusterSize().intValue());
    assertEquals(8, wlsDynamicServersConfig.getMaxDynamicClusterSize().intValue());

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
    ConfigMapHelper.DomainTopology domainTopology =
        ConfigMapHelper.parseDomainTopologyYaml(MIXED_CLUSTER_TOPOLOGY);

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

    assertEquals(true, dynamicServerConfigs.get(0).isDynamicServer());
    assertEquals("domain1-managed-server1", dynamicServerConfigs.get(0).getListenAddress());
    assertEquals(8001, dynamicServerConfigs.get(0).getListenPort().intValue());
    assertEquals(7204, dynamicServerConfigs.get(0).getSslListenPort().intValue());
    assertEquals(1, dynamicServerConfigs.get(0).getNetworkAccessPoints().size());
    assertEquals(
        7205,
        dynamicServerConfigs.get(0).getNetworkAccessPoints().get(0).getListenPort().intValue());

    assertEquals(true, dynamicServerConfigs.get(1).isDynamicServer());
    assertEquals("domain1-managed-server2", dynamicServerConfigs.get(1).getListenAddress());
    assertEquals(8001, dynamicServerConfigs.get(1).getListenPort().intValue());
    assertEquals(7204, dynamicServerConfigs.get(1).getSslListenPort().intValue());
    assertEquals(1, dynamicServerConfigs.get(1).getNetworkAccessPoints().size());
    assertEquals(
        7205,
        dynamicServerConfigs.get(1).getNetworkAccessPoints().get(0).getListenPort().intValue());

    assertEquals(true, dynamicServerConfigs.get(1).isDynamicServer());
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
    ConfigMapHelper.DomainTopology domainTopology =
        ConfigMapHelper.parseDomainTopologyYaml(INVALID_TOPOLOGY);

    assertFalse(domainTopology.getValidationErrors().isEmpty());
    assertFalse(domainTopology.getDomainValid());
    assertEquals(
        "The dynamic cluster \"mycluster\"'s dynamic servers use calculated listen ports.",
        domainTopology.getValidationErrors().get(0));
  }

  @Test
  public void parseInvalidTopologyYamlWithNoValidationErrors() {
    ConfigMapHelper.DomainTopology domainTopology =
        ConfigMapHelper.parseDomainTopologyYaml(DOMAIN_INVALID_NO_ERRORS);

    assertFalse(domainTopology.getValidationErrors().isEmpty());
    assertFalse(domainTopology.getDomainValid());
  }
}

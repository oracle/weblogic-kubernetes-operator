// Copyright (c) 2022, 2023, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.common.utils;

import java.io.IOException;
import java.io.InputStream;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.meterware.simplestub.Memento;
import com.meterware.simplestub.StaticStubSupport;
import oracle.kubernetes.common.CommonConstants;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.yaml.snakeyaml.Yaml;

import static com.jayway.jsonpath.matchers.JsonPathMatchers.hasJsonPath;
import static com.jayway.jsonpath.matchers.JsonPathMatchers.hasNoJsonPath;
import static oracle.kubernetes.common.AuxiliaryImageConstants.AUXILIARY_IMAGE_VOLUME_NAME_PREFIX;
import static oracle.kubernetes.common.CommonConstants.API_VERSION_V8;
import static oracle.kubernetes.common.CommonConstants.API_VERSION_V9;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.junit.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class SchemaConversionUtilsTest {

  private static final String DOMAIN_V8_AUX_IMAGE30_YAML = "aux-image-30-sample.yaml";
  private static final String DOMAIN_V9_CONVERTED_LEGACY_AUX_IMAGE_YAML = "converted-domain-sample.yaml";
  private static final String DOMAIN_V8_SERVER_SCOPED_AUX_IMAGE30_YAML = "aux-image-30-sample-2.yaml";
  private static final String DOMAIN_V9_CONVERTED_SERVER_SCOPED_AUX_IMAGE_YAML = "converted-domain-sample-2.yaml";

  private final List<Memento> mementos = new ArrayList<>();
  private final ConversionAdapter converter = new ConversionAdapter(API_VERSION_V9);
  private final ConversionAdapter converterv8 = new ConversionAdapter(API_VERSION_V8);
  private Map<String, Object> v8Domain;

  private static CommonUtils.CheckedFunction<String, String> getMD5Hash = SchemaConversionUtilsTest::getMD5Hash;

  private static String getMD5Hash(String s) throws NoSuchAlgorithmException {
    throw new NoSuchAlgorithmException();
  }

  @BeforeEach
  public void setUp() throws Exception {
    mementos.add(CommonTestUtils.silenceLogger());
    mementos.add(BaseTestUtils.silenceJsonPathLogger());
    v8Domain = readAsYaml(DOMAIN_V8_AUX_IMAGE30_YAML);
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> readAsYaml(String fileName) throws IOException {
    return (Map<String, Object>) getYamlDocuments(fileName).iterator().next();
  }

  private static Iterable<Object> getYamlDocuments(String fileName) throws IOException {
    InputStream yamlStream = inputStreamFromClasspath(fileName);
    return new Yaml().loadAll(yamlStream);
  }

  private static InputStream inputStreamFromClasspath(String path) {
    return SchemaConversionUtilsTest.class.getResourceAsStream(path);
  }

  @AfterEach
  public void tearDown() throws Exception {
    mementos.forEach(Memento::revert);
  }

  static class ConversionAdapter {

    private final SchemaConversionUtils utils;
    private Map<String, Object> convertedDomain;
    private List<Map<String, Object>> generatedClusters;

    ConversionAdapter(String targetApiVersion) {
      utils = SchemaConversionUtils.create(targetApiVersion);
    }

    void convert(Map<String, Object> yaml) {
      convert(yaml, null);
    }

    void convert(Map<String, Object> yaml, List<Map<String, Object>> clusters) {
      assertDoesNotThrow(() -> {
        SchemaConversionUtils.Resources convertedResources = utils.convertDomainSchema(yaml, toLookup(clusters));
        convertedDomain = convertedResources.domain;
        generatedClusters = convertedResources.clusters;
      });
    }

    private SchemaConversionUtils.ResourceLookup toLookup(List<Map<String, Object>> clusters) {
      return () -> clusters;
    }

    Map<String, Object> getDomain() {
      return convertedDomain;
    }

    List<Map<String, Object>> getClusters() {
      return generatedClusters;
    }
  }

  @SuppressWarnings("SameParameterValue")
  private static Map<String, Object> getMapAtPath(Map<String, Object> parent, String jsonPath) {
    Map<String, Object> result = parent;
    for (String key : jsonPath.split("\\.")) {
      result = getSubMap(result, key);
    }

    return result;
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> getSubMap(Map<String, Object> parentMap, String key) {
    assertThat(parentMap, hasKey(key));
    return (Map<String, Object>) parentMap.get(key);
  }

  @Test
  void testV8DomainUpgradeWithLegacyAuxImagesToV9DomainWithInitContainers() throws IOException {
    Iterator<Object> yamlDocuments = getYamlDocuments(DOMAIN_V9_CONVERTED_LEGACY_AUX_IMAGE_YAML).iterator();
    final Object expectedDomain = yamlDocuments.next();
    List<Object> clusters = new ArrayList<>();
    yamlDocuments.forEachRemaining(clusters::add);

    converter.convert(readAsYaml(DOMAIN_V8_AUX_IMAGE30_YAML));

    assertThat(converter.getDomain(), equalTo(expectedDomain));
    assertThat(clusters, equalTo(converter.getClusters()));
  }

  @Test
  @SuppressWarnings("unchecked")
  void testV9DomainDowngrade() throws IOException {
    Iterator<Object> yamlDocuments = getYamlDocuments(DOMAIN_V9_CONVERTED_LEGACY_AUX_IMAGE_YAML).iterator();
    final Map<String, Object> domain = (Map<String, Object>) yamlDocuments.next();
    List<Map<String, Object>> clusters = new ArrayList<>();
    yamlDocuments.forEachRemaining(doc -> clusters.add((Map<String, Object>) doc));

    converterv8.convert(domain, clusters);

    Map<String, Object> v8Domain = readAsYaml(DOMAIN_V8_AUX_IMAGE30_YAML);
    List<Object> convertedClusters = (List<Object>) getDomainSpec(converterv8.getDomain()).get("clusters");
    List<Object> origClusters = (List<Object>) getDomainSpec(v8Domain).get("clusters");
    assertThat(convertedClusters, notNullValue());
    assertThat(convertedClusters, equalTo(origClusters));
  }

  @Test
  @SuppressWarnings("unchecked")
  void testV8DomainUpgradeWithServerScopedLegacyAuxImagesToV9DomainWithInitContainers() throws IOException {
    Iterator<Object> yamlDocuments = getYamlDocuments(DOMAIN_V9_CONVERTED_SERVER_SCOPED_AUX_IMAGE_YAML).iterator();
    final Map<String, Object> expectedDomain = (Map<String, Object>) yamlDocuments.next();
    List<Map<String, Object>> clusters = new ArrayList<>();
    yamlDocuments.forEachRemaining(doc -> clusters.add((Map<String, Object>) doc));

    Map<String, Object> v8Domain = readAsYaml(DOMAIN_V8_SERVER_SCOPED_AUX_IMAGE30_YAML);
    converter.convert(v8Domain);

    assertThat(converter.getDomain(), equalTo(expectedDomain));

    converterv8.convert(converter.getDomain(), clusters);

    // have to read document again because v8Domain variable contents will be modified
    v8Domain = readAsYaml(DOMAIN_V8_SERVER_SCOPED_AUX_IMAGE30_YAML);
    assertThat(converterv8.getDomain(), equalTo(v8Domain));
  }

  @Test
  void whenOldDomainHasProgressingCondition_removeIt() {
    addStatusCondition("Progressing", "True", null, "in progress");

    converter.convert(v8Domain);

    assertThat(converter.getDomain(), hasJsonPath("$.status.conditions[?(@.type=='Progressing')]", empty()));
  }

  private void addStatusCondition(String type, String status, String reason, String message) {
    addStatusCondition(v8Domain, type, status, reason, message);
  }

  private void addStatusCondition(Map<String, Object> domain, String type, String status,
                                  String reason, String message) {
    final Map<String, String> condition = new HashMap<>();
    condition.put("type", type);
    condition.put("status", status);
    Optional.ofNullable(reason).ifPresent(r -> condition.put("reason", r));
    Optional.ofNullable(message).ifPresent(m -> condition.put("message", m));
    getStatusConditions(domain).add(condition);
  }

  @SuppressWarnings("unchecked")
  private List<Object> getStatusConditions(Map<String, Object> domain) {
    return (List<Object>) getStatus(domain).computeIfAbsent("conditions", k -> new ArrayList<>());
  }

  @SuppressWarnings("unchecked")
  private Map<String,Object> getStatus(Map<String, Object> domain) {
    return (Map<String, Object>) domain.computeIfAbsent("status", k -> new HashMap<>());
  }

  @Test
  void whenOldDomainHasUnsupportedFailedConditionReason_replaceAndPreserve() {
    addStatusCondition("Failed", "True", "Danger", "whoops");

    converter.convert(v8Domain);

    assertThat(converter.getDomain(),
          hasJsonPath("$.status.conditions[?(@.type=='Failed')].reason", contains("Internal")));
    assertThat(converter.getDomain(), hasJsonPath("$.metadata.annotations.['weblogic.v8.failed.reason']",
            equalTo("Danger")));
  }

  @Test
  void whenOldDomainHasUnsupportedAvailableConditionReason_replaceAndPreserve() {
    addStatusCondition("Available", "True", "ServersReady", "Ready");

    converter.convert(v8Domain);

    assertThat(converter.getDomain(),
        hasJsonPath("$.status.conditions[?(@.type=='Available')].reason", contains("Internal")));
    assertThat(converter.getDomain(), hasJsonPath("$.metadata.annotations.['weblogic.v8.available.reason']",
        equalTo("ServersReady")));
  }

  @Test
  void whenOldDomainHasNullAvailableConditionReason_dontPreserve() {
    addStatusCondition("Available", "True", null, "Ready");

    converter.convert(v8Domain);

    assertThat(converter.getDomain(), hasNoJsonPath("$.metadata.annotations.['weblogic.v8.available.reason']"));
  }

  @Test
  void testV9DomainFailedConditionReason_restored() throws IOException {
    Map<String, Object> v9Domain = readAsYaml(DOMAIN_V9_CONVERTED_LEGACY_AUX_IMAGE_YAML);
    getMapAtPath(v9Domain, "metadata.annotations")
        .put("weblogic.v8.failed.reason", "Danger");
    addStatusCondition(v9Domain, "Failed", "True", "Internal", "whoops");

    converterv8.convert(v9Domain);

    assertThat(converterv8.getDomain(), hasNoJsonPath("$.metadata.annotations.['weblogic.v8.failed.reason']"));
    assertThat(converterv8.getDomain(),
            hasJsonPath("$.status.conditions[?(@.type=='Failed')].reason", contains("Danger")));
  }

  @Test
  void testV9DomainAvailableConditionReason_restored() throws IOException {
    Map<String, Object> v9Domain = readAsYaml(DOMAIN_V9_CONVERTED_LEGACY_AUX_IMAGE_YAML);
    getMapAtPath(v9Domain, "metadata.annotations")
        .put("weblogic.v8.available.reason", "ServersReady");
    addStatusCondition(v9Domain, "Available", "True", "Internal", "Ready");

    converterv8.convert(v9Domain);

    assertThat(converterv8.getDomain(), hasNoJsonPath("$.metadata.annotations.['weblogic.v8.available.reason']"));
    assertThat(converterv8.getDomain(),
        hasJsonPath("$.status.conditions[?(@.type=='Available')].reason", contains("ServersReady")));
  }

  @Test
  void testV9DomainRollingCondition_preservedAndRestored() throws IOException {
    Map<String, Object> v9Domain = readAsYaml(DOMAIN_V9_CONVERTED_LEGACY_AUX_IMAGE_YAML);
    addStatusCondition(v9Domain, "Rolling", "True", null, "Rolling cluster-2");

    converterv8.convert(v9Domain);

    assertThat(converterv8.getDomain(),
        hasNoJsonPath("$.status.conditions"));

    converter.convert(converterv8.getDomain());

    assertThat(converter.getDomain(),
        hasJsonPath("$.status.conditions[?(@.type=='Rolling')].message", contains("Rolling cluster-2")));
  }

  @ParameterizedTest
  @CsvSource({"true,, Image", "false,, PersistentVolume", "true, FromModel, FromModel"})
  void whenOldDomainHasDomainHomeInImageBoolean_convertToDomainSourceType(
        boolean isImage, String oldSourceType, String newSourceType) {

    setDomainHomeInImage(v8Domain, isImage);
    setDomainHomeSourceType(v8Domain, oldSourceType);

    converter.convert(v8Domain);

    assertThat(converter.getDomain(), hasJsonPath("$.spec.domainHomeSourceType", equalTo(newSourceType)));
    assertThat(converter.getDomain(), hasNoJsonPath("$.spec.domainHomeInImage"));
  }

  private Map<String, Object> getDomainSpec(Map<String, Object> domainMap) {
    return getSubMap(domainMap, "spec");
  }

  private void setDomainHomeInImage(Map<String, Object> v8Domain, boolean domainHomeInImage) {
    getDomainSpec(v8Domain).put("domainHomeInImage", domainHomeInImage);
  }

  private void setDomainHomeSourceType(Map<String, Object> v8Domain, String domainHomeSourceType) {
    Map<String, Object> spec = getDomainSpec(v8Domain);
    if (domainHomeSourceType == null) {
      spec.remove("domainHomeSourceType");
    } else {
      spec.put("domainHomeSourceType", domainHomeSourceType);
    }
  }

  @Test
  void whenOldDomainHasDesiredStateFields_renameAsStateGoal() {
    addV8ServerStatus("ms1", "RUNNING", "UNKNOWN");
    addV8ServerStatus("ms2", "RUNNING", "RUNNING");
    addV8ServerStatus("ms2", "SHUTDOWN", "SHUTDOWN");

    converter.convert(v8Domain);

    assertThat(converter.getDomain(),
        hasJsonPath("$.status.servers[*].state", contains("UNKNOWN", "RUNNING", "SHUTDOWN")));
    assertThat(converter.getDomain(),
        hasJsonPath("$.status.servers[*].stateGoal", contains("RUNNING", "RUNNING", "SHUTDOWN")));
    assertThat(converter.getDomain(), hasNoJsonPath("$.status.servers[0].desiredState"));
  }

  private void addV8ServerStatus(String serverName, String desiredState, String state) {
    final Map<String, String> serverStatus
        = Map.of("serverName", serverName, "state", state, "desiredState", desiredState);
    getServerStatuses(v8Domain).add(new HashMap<>(serverStatus));
  }

  @SuppressWarnings("unchecked")
  private List<Object> getServerStatuses(Map<String, Object> domain) {
    return (List<Object>) getStatus(domain).computeIfAbsent("servers", k -> new ArrayList<>());
  }

  @Test
  void testV8DomainWithConfigOverrides_moveToOverridesConfigMap() {
    setConfigOverrides(v8Domain, "someMap");

    converter.convert(v8Domain);

    assertThat(converter.getDomain(), hasJsonPath("$.spec.configuration.overridesConfigMap", equalTo("someMap")));
    assertThat(converter.getDomain(), hasNoJsonPath("$.spec.configOverrides"));
  }

  @Test
  void testV8DomainWithConfigOverrides_dontReplaceExistingOverridesConfigMap() {
    setConfigOverrides(v8Domain, "someMap");
    setOverridesConfigMap(v8Domain, "existingMap");

    converter.convert(v8Domain);

    assertThat(converter.getDomain(), hasJsonPath("$.spec.configuration.overridesConfigMap", equalTo("existingMap")));
    assertThat(converter.getDomain(), hasNoJsonPath("$.spec.configOverrides"));
  }

  @SuppressWarnings("SameParameterValue")
  private void setConfigOverrides(Map<String, Object> v8Domain, String configMap) {
    getDomainSpec(v8Domain).put("configOverrides", configMap);
  }

  @SuppressWarnings("SameParameterValue")
  private void setOverridesConfigMap(Map<String, Object> v8Domain, String configMap) {
    getMapAtPath(v8Domain, "spec.configuration").put("overridesConfigMap", configMap);
  }

  @SuppressWarnings({"unchecked", "SameParameterValue"})
  private Map<String, Object> addCluster(Map<String, Object> v8Domain, String name) {
    List<Map<String, Object>> clusters = (List<Map<String, Object>>) getDomainSpec(v8Domain)
            .computeIfAbsent("clusters", k -> new ArrayList<>());
    Map<String, Object> newCluster = new HashMap<>();
    newCluster.put("clusterName", name);
    clusters.add(newCluster);
    return newCluster;
  }

  @SuppressWarnings({"unchecked", "SameParameterValue"})
  private Map<String, Object> addManagedServer(Map<String, Object> v8Domain, String name) {
    List<Map<String, Object>> managedServers = (List<Map<String, Object>>) getDomainSpec(v8Domain)
            .computeIfAbsent("managedServers", k -> new ArrayList<>());
    Map<String, Object> newServer = new HashMap<>();
    newServer.put("serverName", name);
    managedServers.add(newServer);
    return newServer;
  }

  @Test
  void testV8DomainWithConfigOverrideSecrets_moveToConfigurationSecrets() {
    setConfigOverrideSecrets(v8Domain, Collections.singletonList("someSecret"));

    converter.convert(v8Domain);

    assertThat(converter.getDomain(), hasJsonPath("$.spec.configuration.secrets", contains("someSecret")));
    assertThat(converter.getDomain(), hasNoJsonPath("$.spec.configOverrideSecrets"));
  }

  @Test
  void testV8DomainWithConfigOverrideSecrets_dontReplaceExistingSecrets() {
    setConfigOverrideSecrets(v8Domain, Collections.singletonList("someSecret"));
    setConfigurationSecrets(v8Domain, Collections.singletonList("configSecret"));

    converter.convert(v8Domain);

    assertThat(converter.getDomain(), hasJsonPath("$.spec.configuration.secrets", contains("configSecret")));
    assertThat(converter.getDomain(), hasNoJsonPath("$.spec.configOverrideSecrets"));
  }

  @SuppressWarnings("unchecked")
  private void setConfigOverrideSecrets(Map<String, Object> v8Domain, List<String> secrets) {
    ((Map<String, Object>) v8Domain.get("spec")).put("configOverrideSecrets", secrets);
  }

  private void setConfigurationSecrets(Map<String, Object> v8Domain, List<String> secrets) {
    getMapAtPath(v8Domain, "spec.configuration").put("secrets", secrets);
  }

  @Test
  void testV8DomainWithSeverStartPolicy_changeToCamelCase() {
    converter.convert(v8Domain);

    assertThat(converter.getDomain(), hasJsonPath("$.spec.serverStartPolicy", equalTo("IfNeeded")));
  }

  @Test
  void testV8DomainClusterWithSeverStartPolicy_changeToCamelCase() {
    Map<String, Object> cluster = addCluster(v8Domain, "cluster-2");
    cluster.put("serverStartPolicy", "NEVER");

    converter.convert(v8Domain);

    assertThat(cluster, hasEntry("serverStartPolicy", "Never"));
  }

  @Test
  void testV8DomainManagedServerWithSeverStartPolicy_changeToCamelCase() {
    Map<String, Object> managedServer = addManagedServer(v8Domain, "ms-3");
    managedServer.put("serverStartPolicy", "ALWAYS");

    converter.convert(v8Domain);

    assertThat(managedServer, hasEntry("serverStartPolicy", "Always"));
  }

  @Test
  @SuppressWarnings("unchecked")
  void testV8DomainWithAdminSeverStartPolicy_changeToCamelCase() {
    Map<String, Object> adminServer = (Map<String, Object>) getDomainSpec(v8Domain)
            .computeIfAbsent("adminServer", k -> new HashMap<>());
    adminServer.put("serverStartPolicy", "IF_NEEDED");

    converter.convert(v8Domain);

    assertThat(converter.getDomain(),
            hasJsonPath("$.spec.adminServer.serverStartPolicy", equalTo("IfNeeded")));
  }

  @Test
  void testV8DomainWithOverrideDistributionStrategy_changeToCamelCase() {
    getMapAtPath(v8Domain, "spec.configuration").put("overrideDistributionStrategy", "ON_RESTART");

    converter.convert(v8Domain);

    assertThat(converter.getDomain(),
            hasJsonPath("$.spec.configuration.overrideDistributionStrategy", equalTo("OnRestart")));
  }

  @Test
  void testV8Domain_addLogHomeLayout() {
    converter.convert(v8Domain);

    assertThat(converter.getDomain(),
            hasJsonPath("$.spec.logHomeLayout", equalTo("Flat")));
  }

  @Test
  void testV8DomainWithPreservedLogHomeLayout_restoreLogHomeLayout() {
    Map<String, Object> annotations = new HashMap<>();
    getMapAtPath(v8Domain, "metadata").put("annotations", annotations);
    annotations.put("weblogic.v9.preserved", "{\"$.spec\":{\"logHomeLayout\":\"ByServers\"}}");
    converter.convert(v8Domain);

    assertThat(converter.getDomain(),
        hasJsonPath("$.spec.logHomeLayout", equalTo("ByServers")));
  }

  @Test
  void testV9DomainWithLogHomeLayoutFlat_dropIt() throws IOException {
    Map<String, Object> v9Domain = readAsYaml(DOMAIN_V9_CONVERTED_LEGACY_AUX_IMAGE_YAML);
    getMapAtPath(v9Domain, "spec")
        .put("logHomeLayout", "Flat");

    converterv8.convert(v9Domain);

    assertThat(converterv8.getDomain(), hasNoJsonPath("$.metadata.annotations.['weblogic.v9.preserved']"));
  }

  @Test
  void testV9DomainWithLogHomeLayoutByServers_preserveIt() throws IOException {
    Map<String, Object> v9Domain = readAsYaml(DOMAIN_V9_CONVERTED_LEGACY_AUX_IMAGE_YAML);
    getMapAtPath(v9Domain, "spec")
        .put("logHomeLayout", "ByServers");

    converterv8.convert(v9Domain);

    assertThat(converterv8.getDomain(), hasNoJsonPath("$.spec.logHomeLayout"));
    assertThat(converterv8.getDomain(), hasJsonPath("$.metadata.annotations.['weblogic.v9.preserved']",
        equalTo("{\"$.spec\":{\"logHomeLayout\":\"ByServers\"}}")));
  }

  @Test
  void testV9DomainWithNoLogHomeLayout_preserveItAsByServers() throws IOException {
    Map<String, Object> v9Domain = readAsYaml(DOMAIN_V9_CONVERTED_LEGACY_AUX_IMAGE_YAML);
    getMapAtPath(v9Domain, "spec")
        .remove("logHomeLayout");

    converterv8.convert(v9Domain);

    assertThat(converterv8.getDomain(), hasNoJsonPath("$.spec.logHomeLayout"));
    assertThat(converterv8.getDomain(), hasJsonPath("$.metadata.annotations.['weblogic.v9.preserved']",
        equalTo("{\"$.spec\":{\"logHomeLayout\":\"ByServers\"}}")));
  }

  @Test
  void testV8DomainWithoutReplicas_setToZero() {
    getDomainSpec(v8Domain).remove("replicas");

    converter.convert(v8Domain);

    assertThat(converter.getDomain(),
        hasJsonPath("$.spec.replicas", equalTo(0)));
  }

  @Test
  void testV8DomainFields_preserved() {
    converter.convert(v8Domain);

    assertThat(converter.getDomain(), hasNoJsonPath("$.spec.adminServer.serverStartState"));
    assertThat(converter.getDomain(), hasNoJsonPath("$.spec.clusters[0].serverStartState"));
    assertThat(converter.getDomain(), hasNoJsonPath("$.spec.allowReplicasBelowMinDynClusterSize"));
    assertThat(converter.getDomain(), hasNoJsonPath("$.spec.clusters[0].allowReplicasBelowMinDynClusterSize"));
    assertThat(converter.getDomain(), hasJsonPath("$.metadata.annotations.['weblogic.v8.preserved']",
        equalTo("{\"$.spec\":{\"allowReplicasBelowMinDynClusterSize\":false},"
            + "\"$.spec.adminServer\":{\"serverStartState\":\"RUNNING\"},"
            + "\"$.spec.clusters[?(@.clusterName=='cluster-1')]\":{\"allowReplicasBelowMinDynClusterSize\":true,"
            + "\"serverStartState\":\"RUNNING\"}}")));
  }

  @Test
  @SuppressWarnings("unchecked")
  void testV9DomainFields_restored() throws IOException {
    Iterator<Object> yamlDocuments = getYamlDocuments(DOMAIN_V9_CONVERTED_LEGACY_AUX_IMAGE_YAML).iterator();
    final Map<String, Object> domain = (Map<String, Object>) yamlDocuments.next();
    List<Map<String, Object>> clusters = new ArrayList<>();
    yamlDocuments.forEachRemaining(doc -> clusters.add((Map<String, Object>) doc));

    converterv8.convert(domain, clusters);

    assertThat(converterv8.getDomain(), hasNoJsonPath("$.metadata.annotations.['weblogic.v8.preserved']"));
    assertThat(converterv8.getDomain(), hasJsonPath("$.spec.adminServer.serverStartState",
        equalTo("RUNNING")));
    assertThat(converterv8.getDomain(), hasJsonPath("$.spec.clusters[0].serverStartState",
        equalTo("RUNNING")));
    assertThat(converterv8.getDomain(), hasJsonPath("$.spec.allowReplicasBelowMinDynClusterSize",
            equalTo(Boolean.FALSE)));
    assertThat(converterv8.getDomain(), hasJsonPath("$.spec.clusters[0].allowReplicasBelowMinDynClusterSize",
            equalTo(Boolean.TRUE)));
  }

  @Test
  void testV9DomainCompletedIsFalse_toProgressing() throws IOException {
    Map<String, Object> v9Domain = readAsYaml(DOMAIN_V9_CONVERTED_LEGACY_AUX_IMAGE_YAML);
    addStatusCondition(v9Domain, "Completed", "False", "Something", "Hello");
    converterv8.convert(v9Domain);

    assertThat(converterv8.getDomain(), hasJsonPath("$.status.conditions[?(@.type=='Completed')]", empty()));
    assertThat(converterv8.getDomain(),
        hasJsonPath("$.status.conditions[?(@.type=='Progressing')].reason", contains("Something")));
    assertThat(converterv8.getDomain(),
        hasJsonPath("$.status.conditions[?(@.type=='Progressing')].message", contains("Hello")));
    assertThat(converterv8.getDomain(),
        hasJsonPath("$.status.conditions[?(@.type=='Progressing')].status", contains("True")));
  }

  @Test
  void testV9DomainCompletedIsTrue_removeIt() throws IOException {
    Map<String, Object> v9Domain = readAsYaml(DOMAIN_V9_CONVERTED_LEGACY_AUX_IMAGE_YAML);
    addStatusCondition(v9Domain, "Completed", "True", "Something", "Hello");
    converterv8.convert(v9Domain);

    assertThat(converterv8.getDomain(), hasNoJsonPath("$.status.conditions[?(@.type=='Completed')]"));
    assertThat(converterv8.getDomain(), hasNoJsonPath("$.status.conditions[?(@.type=='Progressing')]"));
  }

  @Test
  void whenV9DomainHasServerStatusStateGoal_renameToDesiredState() throws IOException {
    Map<String, Object> v9Domain = readAsYaml(DOMAIN_V9_CONVERTED_LEGACY_AUX_IMAGE_YAML);

    addV9ServerStatus(v9Domain, "ms1", "RUNNING", "UNKNOWN");
    addV9ServerStatus(v9Domain, "ms2", "RUNNING", "RUNNING");
    addV9ServerStatus(v9Domain, "ms2", "SHUTDOWN", "SHUTDOWN");

    converterv8.convert(v9Domain);

    assertThat(converterv8.getDomain(),
        hasJsonPath("$.status.servers[*].state", contains("UNKNOWN", "RUNNING", "SHUTDOWN")));
    assertThat(converterv8.getDomain(),
        hasJsonPath("$.status.servers[*].desiredState", contains("RUNNING", "RUNNING", "SHUTDOWN")));
    assertThat(converterv8.getDomain(), hasNoJsonPath("$.status.servers[0].stateGoal"));
  }

  private void addV9ServerStatus(Map<String, Object> v9Domain, String serverName, String stateGoal, String state) {
    final Map<String, String> serverStatus
        = Map.of("serverName", serverName, "state", state, "stateGoal", stateGoal);
    getServerStatuses(v9Domain).add(new HashMap<>(serverStatus));
  }

  @Test
  void testV8DomainIstio_preserved() {
    // Simplify domain to focus on Istio
    getDomainSpec(v8Domain).remove("adminServer");
    getDomainSpec(v8Domain).remove("clusters");
    getDomainSpec(v8Domain).remove("allowReplicasBelowMinDynClusterSize");

    // Add Istio configuration
    Map<String, Object> istio = new LinkedHashMap<>();
    istio.put("enabled", true);
    istio.put("readinessPort", 9000);
    getMapAtPath(v8Domain, "spec.configuration").put("istio", istio);

    converter.convert(v8Domain);

    assertThat(converter.getDomain(), hasNoJsonPath("$.spec.configuration.istio"));
    assertThat(converter.getDomain(), hasJsonPath("$.metadata.annotations.['weblogic.v8.preserved']",
        equalTo("{\"$.spec.configuration\":{\"istio\":{\"enabled\":true,\"readinessPort\":9000}}}")));
  }

  @Test
  void testV9DomainIstio_restored() throws IOException {
    Map<String, Object> v9Domain = readAsYaml(DOMAIN_V9_CONVERTED_LEGACY_AUX_IMAGE_YAML);
    getMapAtPath(v9Domain, "metadata.annotations")
        .put("weblogic.v8.preserved",
            "{\"$.spec.configuration\":{\"istio\":{\"enabled\":true,\"readinessPort\":9000}}}");

    converterv8.convert(v9Domain);

    assertThat(converterv8.getDomain(), hasNoJsonPath("$.metadata.annotations.['weblogic.v8.preserved']"));
    assertThat(converterv8.getDomain(), hasJsonPath("$.spec.configuration.istio.enabled",
        equalTo(true)));
    assertThat(converterv8.getDomain(), hasJsonPath("$.spec.configuration.istio.readinessPort",
        equalTo(9000)));
  }
  
  @SuppressWarnings("unchecked")
  @Test
  void testV8DomainWebLogicCredentialsSecretWithNamespace_remove() {
    ((Map<String, Object>) getDomainSpec(v8Domain).get("webLogicCredentialsSecret")).put("namespace", "my-ns");

    converter.convert(v8Domain);

    assertThat(converter.getDomain(), hasNoJsonPath("$.spec.webLogicCredentialsSecret.namespace"));
  }

  @Test
  void testV8DomainWithLongAuxiliaryImageVolumeName_convertedVolumeNameIsTruncated() throws NoSuchAlgorithmException {
    Map<String, Object> auxImageVolume = ((Map<String, Object>)
        ((List<Object>) getDomainSpec(v8Domain).get("auxiliaryImageVolumes")).get(0));
    auxImageVolume.put("name", "test-domain-aux-image-volume-test-domain-aux-image-volume");
    getDomainSpec(v8Domain).put("auxiliaryImageVolumes",  Collections.singletonList(auxImageVolume));

    converter.convert(v8Domain);

    assertThat(converter.getDomain(), hasJsonPath("$.spec.serverPod.volumes[0].name",
        equalTo(CommonUtils.getLegalVolumeName(CommonUtils.toDns1123LegalName(CommonConstants.COMPATIBILITY_MODE
            + AUXILIARY_IMAGE_VOLUME_NAME_PREFIX + (String)auxImageVolume.get("name"))))));
  }

  @Test
  void testV8DomainWithLongAuxiliaryImageVolumeNameAndMessageDigestThrowsException_volumeNameIsNotChanged()
      throws NoSuchFieldException {
    mementos.add(StaticStubSupport.install(CommonUtils.class, "getMD5Hash", getMD5Hash));

    Map<String, Object> auxImageVolume = ((Map<String, Object>)
        ((List<Object>) getDomainSpec(v8Domain).get("auxiliaryImageVolumes")).get(0));
    auxImageVolume.put("name", "test-domain-aux-image-volume-test-domain-aux-image-volume");
    getDomainSpec(v8Domain).put("auxiliaryImageVolumes",  Collections.singletonList(auxImageVolume));

    converter.convert(v8Domain);

    assertThat(converter.getDomain(), hasJsonPath("$.spec.serverPod.volumes[0].name",
        equalTo(CommonUtils.toDns1123LegalName(CommonConstants.COMPATIBILITY_MODE
            + AUXILIARY_IMAGE_VOLUME_NAME_PREFIX + "test-domain-aux-image-volume-test-domain-aux-image-volume"))));
  }
}
// Copyright (c) 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.common.utils;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.google.gson.internal.LinkedTreeMap;
import com.meterware.simplestub.Memento;
import io.kubernetes.client.openapi.ApiException;
import oracle.kubernetes.common.ClusterCustomResourceHelper;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static oracle.kubernetes.common.CommonConstants.API_VERSION_V9;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.junit.MatcherAssert.assertThat;

class SchemaConversionUtilsTest {

  public static final String DOMAIN_V8_AUX_IMAGE30_YAML = "aux-image-30-sample.yaml";
  public static final String DOMAIN_V9_CONVERTED_LEGACY_AUX_IMAGE_YAML = "converted-domain-sample.yaml";
  public static final String DOMAIN_V8_SERVER_SCOPED_AUX_IMAGE30_YAML = "aux-image-30-sample-2.yaml";
  public static final String DOMAIN_V9_CONVERTED_SERVER_SCOPED_LEGACY_AUX_IMAGE_YAML = "converted-domain-sample-2.yaml";

  private final List<Memento> mementos = new ArrayList<>();

  @BeforeEach
  public void setUp() throws Exception {
    mementos.add(CommonTestUtils.silenceLogger());
  }

  @AfterEach
  public void tearDown() throws Exception {
    mementos.forEach(Memento::revert);
  }

  @Test
  void testV8DomainUpgradeWithLegacyAuxImagesToV9DomainWithInitContainers() throws IOException {
    SchemaConversionUtils schemaConversionUtils = new SchemaConversionUtils();

    Object convertedDomain = schemaConversionUtils.convertDomainSchema(
            readAsYaml(DOMAIN_V8_AUX_IMAGE30_YAML), API_VERSION_V9);
    Object expectedDomain = readAsYaml(DOMAIN_V9_CONVERTED_LEGACY_AUX_IMAGE_YAML);
    assertThat(convertedDomain, equalTo(expectedDomain));
  }

  @Test
  void testV8DomainUpgradeWithServerScopedLegacyAuxImagesToV9DomainWithInitContainers() throws IOException {
    SchemaConversionUtils schemaConversionUtils = new SchemaConversionUtils();

    Object convertedDomain = schemaConversionUtils.convertDomainSchema(
            readAsYaml(DOMAIN_V8_SERVER_SCOPED_AUX_IMAGE30_YAML), API_VERSION_V9);
    Object expectedDomain = readAsYaml(DOMAIN_V9_CONVERTED_SERVER_SCOPED_LEGACY_AUX_IMAGE_YAML);
    assertThat(convertedDomain, equalTo(expectedDomain));
  }

  @Test
  void testV8toV9ConversionWithClusterCustomResource() throws IOException, ApiException {
    SchemaConversionUtils schemaConversionUtils = new SchemaConversionUtils();

    Map<String, Object> domain = readAsYaml(DOMAIN_V8_SERVER_SCOPED_AUX_IMAGE30_YAML);
    schemaConversionUtils.convertV8toV9(new ClusterCustomResourceHelperStub(true), domain);

    Map<String, Object> spec = SchemaConversionUtils.getSpec(domain);
    List<Object> clusters = (List<Object>) spec.get("clusters");
    assertThat(clusters.isEmpty(), is(true));
    List<Object> clusterResourceReferences = (List<Object>) spec.get("cluster-resource-references");
    assertThat(clusterResourceReferences.size(), is(1));
    assertThat(clusterResourceReferences.get(0), equalTo("cluster-1"));
  }

  @Test
  void testV9toV8ConversionWithClusterCustomResource() throws IOException, ApiException {
    SchemaConversionUtils schemaConversionUtils = new SchemaConversionUtils();

    Map<String, Object> domain = readAsYaml("sample-domain1-v9.yaml");
    schemaConversionUtils.convertV9toV8(new ClusterCustomResourceHelperStub(false), domain);

    Map<String, Object> spec = (Map<String, Object>) domain.get("spec");
    List<Object> clusters = (List<Object>) spec.get("clusters");
    assertThat(clusters.isEmpty(), is(false));
    Map<String, Object> cluster = (Map<String, Object>) clusters.get(0);
    assertThat(cluster.get("clusterName"), equalTo("cluster-1"));
    List<Object> clusterResourceReferences = (List<Object>) spec.get("cluster-resource-references");
    assertThat(clusterResourceReferences, is(nullValue()));
  }

  @Test
  public void getClusterResourceReferences_initializedAsEmpty_whenNotDefined() throws IOException {
    SchemaConversionUtils schemaConversionUtils = new SchemaConversionUtils();

    Map<String, Object> domain = readAsYaml(DOMAIN_V8_SERVER_SCOPED_AUX_IMAGE30_YAML);
    Map<String, Object> domainSpec = (Map<String, Object>) domain.get("spec");
    List<String> clusterResourceReferences = schemaConversionUtils.getClusterResourceReferences(domainSpec);

    assertThat(clusterResourceReferences.isEmpty(), is(true));
    assertThat(domainSpec.get(SchemaConversionUtils.CLUSTER_RESOURCE_REFERENCES), equalTo(clusterResourceReferences));
  }

  @Test
  public void getClusterResourceReferences_exists_whenDefined() throws IOException {
    SchemaConversionUtils schemaConversionUtils = new SchemaConversionUtils();

    Map<String, Object> domain = readAsYaml("sample-domain1-v9.yaml");
    Map<String, Object> domainSpec = (Map<String, Object>) domain.get("spec");
    List<String> clusterResourceReferences = schemaConversionUtils.getClusterResourceReferences(domainSpec);

    assertThat(clusterResourceReferences.size(), is(1));
    assertThat(domainSpec.get(SchemaConversionUtils.CLUSTER_RESOURCE_REFERENCES), equalTo(clusterResourceReferences));
    assertThat(clusterResourceReferences.get(0), is("cluster-1"));
  }

  @Test
  public void getOrCreateClusterSpec_whenNotDefined() throws IOException {
    Map<String, Object> domain = readAsYaml("sample-domain1-v9.yaml");
    Map<String, Object> domainSpec = (Map<String, Object>) domain.get("spec");
    List<Object> clusterSpecs = SchemaConversionUtils.getOrCreateClusterSpecs(domainSpec);

    assertThat(clusterSpecs.isEmpty(), is(true));
    assertThat(domainSpec.get("clusters"), equalTo(clusterSpecs));
  }

  @Test
  public void getOrCreateClusterSpec_whenDefined() throws IOException {
    Map<String, Object> domain = readAsYaml(DOMAIN_V8_SERVER_SCOPED_AUX_IMAGE30_YAML);
    Map<String, Object> domainSpec = (Map<String, Object>) domain.get("spec");
    List<Object> clusterSpecs = SchemaConversionUtils.getOrCreateClusterSpecs(domainSpec);

    assertThat(clusterSpecs.isEmpty(), is(false));
    Map<String, Object> clusterSpec = (Map<String, Object>) clusterSpecs.get(0);
    String clusterName = (String) clusterSpec.get("clusterName");
    assertThat(clusterName, equalTo("cluster-1"));
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> readAsYaml(String fileName) throws IOException {
    InputStream yamlStream = inputStreamFromClasspath(fileName);
    ObjectMapper yamlReader = new ObjectMapper(new YAMLFactory());
    return ((Map<String, Object>) yamlReader.readValue(yamlStream, Map.class));
  }

  public static InputStream inputStreamFromClasspath(String path) {
    return SchemaConversionUtilsTest.class.getResourceAsStream(path);
  }

  private static class ClusterCustomResourceHelperStub implements ClusterCustomResourceHelper {
    private boolean convertV8toV9;

    public ClusterCustomResourceHelperStub(boolean convertV8toV9) {
      this.convertV8toV9 = convertV8toV9;
    }

    @Override
    public void createClusterResource(Map<String, Object> clusterSpec, Map<String, Object> domain)
        throws ApiException {

    }

    @Override
    public Map<String, Object> getDeployedClusterResources(String namespace, String domainUid) {
      Map<String, Object> clusterResources = new LinkedTreeMap<>();
      Map<String, Object> clusterResource = new LinkedTreeMap<>();
      clusterResource.put("apiVersion", "weblogic.oracle/v9");
      clusterResource.put("kind", "Cluster");
      clusterResource.put("metadata", createMetadata());
      clusterResource.put("spec", createClusterSpec());
      clusterResources.put("cluster-1", clusterResource);
      return clusterResources;
    }

    @NotNull
    private Map<String, Object> createClusterSpec() {
      Map<String, Object> spec = new LinkedTreeMap<>();
      spec.put("clusterName", "cluster-1");
      spec.put("serverStartState", "RUNNING");
      Map<String, Object> serverPod = createServerPod();
      spec.put("serverPod", serverPod);
      spec.put("replicas", "2");
      return spec;
    }

    @NotNull
    private Map<String, Object> createServerPod() {
      Map<String, Object> serverPod = new LinkedTreeMap<>();
      Map<String, Object> affinity = createAffinity();
      serverPod.put("affinity", affinity);
      return serverPod;
    }

    @NotNull
    private Map<String, Object> createAffinity() {
      Map<String, Object> affinity = new LinkedTreeMap<>();
      Map<String, Object> podAntiAffinity = createPodAntiAffinity();
      affinity.put("podAntiAffinity", podAntiAffinity);
      return affinity;
    }

    @NotNull
    private Map<String, Object> createPodAntiAffinity() {
      Map<String, Object> podAntiAffinity = new LinkedTreeMap<>();
      List<Object> preferredDuringSchedulingIgnoredDuringExecution =
          createPreferredDuringSchedulingIgnoredDuringExecution();
      podAntiAffinity.put("preferredDuringSchedulingIgnoredDuringExecution",
          preferredDuringSchedulingIgnoredDuringExecution);
      return podAntiAffinity;
    }

    @NotNull
    private List<Object> createPreferredDuringSchedulingIgnoredDuringExecution() {
      List<Object> preferredDuringSchedulingIgnoredDuringExecution = new ArrayList<>();
      Map<String, Object> weightedPodAffinityTerm = createWeightedPodAffinityTerm();
      preferredDuringSchedulingIgnoredDuringExecution.add(weightedPodAffinityTerm);
      return preferredDuringSchedulingIgnoredDuringExecution;
    }

    @NotNull
    private Map<String, Object> createWeightedPodAffinityTerm() {
      Map<String, Object> weightedPodAffinityTerm = new LinkedTreeMap<>();
      weightedPodAffinityTerm.put("weight", "100");
      Map<String, Object> podAffinityTerm = createPodAffinityTerm();
      weightedPodAffinityTerm.put("podAffinityTerm", podAffinityTerm);
      return weightedPodAffinityTerm;
    }

    @NotNull
    private Map<String, Object> createPodAffinityTerm() {
      Map<String, Object> podAffinityTerm = new LinkedTreeMap<>();
      Map<String, Object> labelSelector = createLabelSelector();
      podAffinityTerm.put("labelSelector", labelSelector);
      podAffinityTerm.put("topologyKey", "kubernetes.io/hostname");
      return podAffinityTerm;
    }

    @NotNull
    private Map<String, Object> createLabelSelector() {
      Map<String, Object> labelSelector = new LinkedTreeMap<>();
      List<Object> matchExpressions = createMatchExpressions();
      labelSelector.put("matchExpressions", matchExpressions);
      return labelSelector;
    }

    @NotNull
    private List<Object> createMatchExpressions() {
      List<Object> matchExpressions = new ArrayList<>();
      Map<String, Object> labelSelectorRequirement = createLabelSelectorRequirement();
      matchExpressions.add(labelSelectorRequirement);
      return matchExpressions;
    }

    @NotNull
    private Map<String, Object> createLabelSelectorRequirement() {
      Map<String, Object> labelSelectorRequirement = new LinkedTreeMap<>();
      labelSelectorRequirement.put("key", "weblogic.clusterName");
      labelSelectorRequirement.put("operator", "In");
      List<Object> values = new ArrayList<>();
      values.add("$(CLUSTER_NAME)");
      labelSelectorRequirement.put("values", values);
      return labelSelectorRequirement;
    }

    @NotNull
    private Map<String, Object> createMetadata() {
      Map<String, Object> metadata = new LinkedTreeMap<>();
      metadata.put("name", "cluster-1");
      metadata.put("namespace", "sample-domain1-ns");
      Map<String, Object> labels = createLabels();
      metadata.put("labels", labels);
      return metadata;
    }

    @NotNull
    private Map<String, Object> createLabels() {
      Map<String, Object> labels = new LinkedTreeMap<>();
      labels.put("weblogic.domainUID", "sample-domain1");
      return labels;
    }

    @Override
    public List<String> getNamesOfDeployedClusterResources(String namespace, String domainUid) {
      if (convertV8toV9) {
        return Collections.emptyList();
      }
      return List.of("cluster-1");
    }
  }
}
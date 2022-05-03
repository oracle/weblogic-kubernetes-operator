// Copyright (c) 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.common.utils;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.meterware.simplestub.Memento;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static oracle.kubernetes.common.CommonConstants.API_VERSION_V9;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.not;
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
  void testV8DomainWithDomainHomeInImageTrue_convertedToDomainHomeSourceType() throws IOException {
    SchemaConversionUtils schemaConversionUtils = new SchemaConversionUtils();

    Map<String, Object> v8Domain = readAsYaml(DOMAIN_V8_AUX_IMAGE30_YAML);
    setDomainHomeInImage(v8Domain, true);
    setDomainHomeSourceType(v8Domain, null);

    Map<String, Object> convertedDomain = (Map<String, Object>) schemaConversionUtils.convertDomainSchema(
            v8Domain, API_VERSION_V9);

    assertThat(convertedDomain, hasKey("spec"));
    Map<String, Object> spec = (Map<String, Object>) convertedDomain.get("spec");
    assertThat(spec, hasEntry("domainHomeSourceType", "Image"));
    assertThat(spec, not(hasKey("domainHomeInImage")));
  }

  @Test
  void testV8DomainWithDomainHomeInImageFalse_convertedToDomainHomeSourceType() throws IOException {
    SchemaConversionUtils schemaConversionUtils = new SchemaConversionUtils();

    Map<String, Object> v8Domain = readAsYaml(DOMAIN_V8_AUX_IMAGE30_YAML);
    setDomainHomeInImage(v8Domain, false);
    setDomainHomeSourceType(v8Domain, null);

    Map<String, Object> convertedDomain = (Map<String, Object>) schemaConversionUtils.convertDomainSchema(
            v8Domain, API_VERSION_V9);

    assertThat(convertedDomain, hasKey("spec"));
    Map<String, Object> spec = (Map<String, Object>) convertedDomain.get("spec");
    assertThat(spec, hasEntry("domainHomeSourceType", "PersistentVolume"));
    assertThat(spec, not(hasKey("domainHomeInImage")));
  }

  @Test
  void testV8DomainWithDomainHomeInImage_dontReplaceExistingDomainHomeSourceType() throws IOException {
    SchemaConversionUtils schemaConversionUtils = new SchemaConversionUtils();

    Map<String, Object> v8Domain = readAsYaml(DOMAIN_V8_AUX_IMAGE30_YAML);
    setDomainHomeInImage(v8Domain, true);
    setDomainHomeSourceType(v8Domain, "FromModel");

    Map<String, Object> convertedDomain = (Map<String, Object>) schemaConversionUtils.convertDomainSchema(
            v8Domain, API_VERSION_V9);

    assertThat(convertedDomain, hasKey("spec"));
    Map<String, Object> spec = (Map<String, Object>) convertedDomain.get("spec");
    assertThat(spec, hasEntry("domainHomeSourceType", "FromModel"));
    assertThat(spec, not(hasKey("domainHomeInImage")));
  }

  private void setDomainHomeInImage(Map<String, Object> v8Domain, boolean domainHomeInImage) {
    ((Map<String, Object>) v8Domain.get("spec")).put("domainHomeInImage", String.valueOf(domainHomeInImage));
  }

  private void setDomainHomeSourceType(Map<String, Object> v8Domain, String domainHomeSourceType) {
    Map<String, Object> spec = (Map<String, Object>) v8Domain.get("spec");
    if (domainHomeSourceType == null) {
      spec.remove("domainHomeSourceType");
    } else {
      spec.put("domainHomeSourceType", domainHomeSourceType);
    }
  }

  @Test
  void testV8DomainWithConfigOverrides_moveToOverridesConfigMap() throws IOException {
    SchemaConversionUtils schemaConversionUtils = new SchemaConversionUtils();

    Map<String, Object> v8Domain = readAsYaml(DOMAIN_V8_AUX_IMAGE30_YAML);
    setConfigOverrides(v8Domain, "someMap");

    Map<String, Object> convertedDomain = (Map<String, Object>) schemaConversionUtils.convertDomainSchema(
        v8Domain, API_VERSION_V9);

    assertThat(convertedDomain, hasKey("spec"));
    Map<String, Object> spec = (Map<String, Object>) convertedDomain.get("spec");
    assertThat(spec, hasKey("configuration"));
    Map<String, Object> configuration = (Map<String, Object>) spec.get("configuration");
    assertThat(configuration, hasEntry("overridesConfigMap", "someMap"));
    assertThat(spec, not(hasKey("configOverrides")));
  }

  @Test
  void testV8DomainWithConfigOverrides_dontReplaceExistingOverridesConfigMap() throws IOException {
    SchemaConversionUtils schemaConversionUtils = new SchemaConversionUtils();

    Map<String, Object> v8Domain = readAsYaml(DOMAIN_V8_AUX_IMAGE30_YAML);
    setConfigOverrides(v8Domain, "someMap");
    setOverridesConfigMap(v8Domain, "existingMap");

    Map<String, Object> convertedDomain = (Map<String, Object>) schemaConversionUtils.convertDomainSchema(
        v8Domain, API_VERSION_V9);

    assertThat(convertedDomain, hasKey("spec"));
    Map<String, Object> spec = (Map<String, Object>) convertedDomain.get("spec");
    assertThat(spec, hasKey("configuration"));
    Map<String, Object> configuration = (Map<String, Object>) spec.get("configuration");
    assertThat(configuration, hasEntry("overridesConfigMap", "existingMap"));
    assertThat(spec, not(hasKey("configOverrides")));
  }

  private void setConfigOverrides(Map<String, Object> v8Domain, String configMap) {
    ((Map<String, Object>) v8Domain.get("spec")).put("configOverrides", configMap);
  }

  private void setOverridesConfigMap(Map<String, Object> v8Domain, String configMap) {
    ((Map<String, Object>) ((Map<String, Object>) v8Domain.get("spec"))
        .computeIfAbsent("configuration", k -> new LinkedHashMap<>())).put("overridesConfigMap", configMap);
  }

  @Test
  void testV8DomainWithConfigOverrideSecrets_moveToConfigurationSecrets() throws IOException {
    SchemaConversionUtils schemaConversionUtils = new SchemaConversionUtils();

    Map<String, Object> v8Domain = readAsYaml(DOMAIN_V8_AUX_IMAGE30_YAML);
    setConfigOverrideSecrets(v8Domain, Collections.singletonList("someSecret"));

    Map<String, Object> convertedDomain = (Map<String, Object>) schemaConversionUtils.convertDomainSchema(
        v8Domain, API_VERSION_V9);

    assertThat(convertedDomain, hasKey("spec"));
    Map<String, Object> spec = (Map<String, Object>) convertedDomain.get("spec");
    assertThat(spec, hasKey("configuration"));
    Map<String, Object> configuration = (Map<String, Object>) spec.get("configuration");
    assertThat(configuration, hasEntry("secrets", Collections.singletonList("someSecret")));
    assertThat(spec, not(hasKey("configOverrideSecrets")));
  }

  @Test
  void testV8DomainWithConfigOverrideSecrets_dontReplaceExistingSecrets() throws IOException {
    SchemaConversionUtils schemaConversionUtils = new SchemaConversionUtils();

    Map<String, Object> v8Domain = readAsYaml(DOMAIN_V8_AUX_IMAGE30_YAML);
    setConfigOverrideSecrets(v8Domain, Collections.singletonList("someSecret"));
    setConfigurationSecrets(v8Domain, Collections.singletonList("configSecret"));

    Map<String, Object> convertedDomain = (Map<String, Object>) schemaConversionUtils.convertDomainSchema(
        v8Domain, API_VERSION_V9);

    assertThat(convertedDomain, hasKey("spec"));
    Map<String, Object> spec = (Map<String, Object>) convertedDomain.get("spec");
    assertThat(spec, hasKey("configuration"));
    Map<String, Object> configuration = (Map<String, Object>) spec.get("configuration");
    assertThat(configuration, hasEntry("secrets", Collections.singletonList("configSecret")));
    assertThat(spec, not(hasKey("configOverrideSecrets")));
  }

  @SuppressWarnings("unchecked")
  private void setConfigOverrideSecrets(Map<String, Object> v8Domain, List<String> secrets) {
    ((Map<String, Object>) v8Domain.get("spec")).put("configOverrideSecrets", secrets);
  }

  private void setConfigurationSecrets(Map<String, Object> v8Domain, List<String> secrets) {
    ((Map<String, Object>) ((Map<String, Object>) v8Domain.get("spec"))
        .computeIfAbsent("configuration", k -> new LinkedHashMap<>())).put("secrets", secrets);
  }

  private Map<String, Object> readAsYaml(String fileName) throws IOException {
    InputStream yamlStream = inputStreamFromClasspath(fileName);
    ObjectMapper yamlReader = new ObjectMapper(new YAMLFactory());
    return ((Map<String, Object>) yamlReader.readValue(yamlStream, Map.class));
  }

  public static InputStream inputStreamFromClasspath(String path) {
    return SchemaConversionUtilsTest.class.getResourceAsStream(path);
  }
}
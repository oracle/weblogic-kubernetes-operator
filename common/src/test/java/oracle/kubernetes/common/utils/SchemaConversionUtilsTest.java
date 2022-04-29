// Copyright (c) 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.common.utils;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
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

  @SuppressWarnings("unchecked")
  private Map<String, Object> readAsYaml(String fileName) throws IOException {
    InputStream yamlStream = inputStreamFromClasspath(fileName);
    ObjectMapper yamlReader = new ObjectMapper(new YAMLFactory());
    return ((Map<String, Object>) yamlReader.readValue(yamlStream, Map.class));
  }

  public static InputStream inputStreamFromClasspath(String path) {
    return SchemaConversionUtilsTest.class.getResourceAsStream(path);
  }
}
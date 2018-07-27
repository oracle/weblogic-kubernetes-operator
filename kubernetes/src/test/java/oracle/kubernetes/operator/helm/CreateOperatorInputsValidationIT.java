// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helm;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.both;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.emptyString;

import com.google.common.collect.ImmutableMap;
import java.util.Map;
import org.hamcrest.Matcher;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

public class CreateOperatorInputsValidationIT extends ChartITBase {

  private static final String WRONG_TYPE = "The %s property %s must be a %s instead";

  private static final String[] TOP_LEVEL_BOOLEAN_PROPERTIES = {
    "setupKubernetesCluster", "createOperator" // , "elkIntegrationEnabled"
  };

  private static final String[] OPERATOR_LEVEL_BOOLEAN_PROPERTIES = {"elkIntegrationEnabled"};

  private static final String[] OPERATOR_LEVEL_STRING_PROPERTIES = {
    "operatorNamespace", "operatorServiceAccount", "operatorImage"
  };

  private static final String[] OPERATOR_LEVEL_ENUM_PROPERTIES = {
    "operatorImagePullPolicy", "javaLoggingLevel", "externalRestOption", "internalRestOption"
  };

  private static final String[] LOGGING_LEVELS = {
    "SEVERE", "WARNING", "INFO", "CONFIG", "FINE", "FINER", "FINEST"
  };

  private static final String[] PULL_POLICIES = {"Always", "IfNotPresent", "Never"};

  private static final String[] EXTERNAL_REST_OPTIONS = {"NONE", "SELF_SIGNED_CERT", "CUSTOM_CERT"};

  private static final String[] INTERNAL_REST_OPTIONS = {"SELF_SIGNED_CERT", "CUSTOM_CERT"};

  private HelmOperatorYamlFactory factory = new HelmOperatorYamlFactory();
  private Map<String, Object> overrides;

  @Before
  public void setUp() throws Exception {
    overrides = ((HelmOperatorValues) factory.newOperatorValues()).createMap();
  }

  @Test
  public void whenStringSpecifiedForBooleanTopLevelProperties_reportError() throws Exception {
    whenStringSpecifiedForBooleanProperties_reportError(TOP_LEVEL_BOOLEAN_PROPERTIES);
  }

  @Test
  public void whenStringSpecifiedForOperatorTopLevelProperties_reportError() throws Exception {
    whenStringSpecifiedForBooleanProperties_reportError(OPERATOR_LEVEL_BOOLEAN_PROPERTIES);
  }

  private void whenStringSpecifiedForBooleanProperties_reportError(String[] propertyNames)
      throws Exception {
    for (String propertyName : propertyNames) {
      setProperty(propertyName, "this is not a boolean");
    }

    String processingError = getProcessingError();

    for (String propertyName : propertyNames) {
      assertThat(processingError, containsTypeError(propertyName, "bool", "string"));
    }
  }

  private void setProperty(String propertyName, Object value) {
    overrides.put(propertyName, value);
  }

  private String getProcessingError() throws Exception {
    return getChart("weblogic-operator", overrides).getError();
  }

  private Matcher<String> containsTypeError(String name, String expectedType, String actualType) {
    return containsString(String.format(WRONG_TYPE, actualType, name, expectedType));
  }

  @Test
  public void whenTopLevelBooleanPropertiesMissing_reportError() throws Exception {
    whenBooleanPropertiesMissing_reportError(TOP_LEVEL_BOOLEAN_PROPERTIES);
  }

  @Test
  public void whenOperatorLevelBooleanPropertiesMissing_reportError() throws Exception {
    whenBooleanPropertiesMissing_reportError(OPERATOR_LEVEL_BOOLEAN_PROPERTIES);
  }

  private void whenBooleanPropertiesMissing_reportError(String[] propertyNames) throws Exception {
    for (String propertyName : propertyNames) {
      removeProperty(propertyName);
    }

    String processingError = getProcessingError();

    for (String propertyName : propertyNames) {
      assertThat(processingError, containsMissingBoolParameterError(propertyName));
    }
  }

  private void removeProperty(String propertyName) {
    setProperty(propertyName, null);
  }

  private Matcher<String> containsMissingBoolParameterError(String propertyName) {
    return containsString(String.format("The bool property %s must be specified", propertyName));
  }

  @Test
  public void whenOperatorLevelStringPropertiesMissing_reportError() throws Exception {
    for (String propertyName : OPERATOR_LEVEL_STRING_PROPERTIES) {
      removeProperty(propertyName);
    }

    String processingError = getProcessingError();

    for (String propertyName : OPERATOR_LEVEL_STRING_PROPERTIES) {
      assertThat(processingError, containsMissingStringParameterError(propertyName));
    }
  }

  private Matcher<String> containsMissingStringParameterError(String propertyName) {
    return containsString(String.format("The string property %s must be specified", propertyName));
  }

  @Test
  public void whenOperatorLevelEnumPropertiesMissing_reportError() throws Exception {
    for (String propertyName : OPERATOR_LEVEL_ENUM_PROPERTIES) {
      removeProperty(propertyName);
    }

    String processingError = getProcessingError();

    for (String propertyName : OPERATOR_LEVEL_ENUM_PROPERTIES) {
      assertThat(processingError, containsMissingEnumParameterError(propertyName));
    }
  }

  private Matcher<String> containsMissingEnumParameterError(String propertyName) {
    return containsString(String.format("The string property %s must be specified", propertyName));
  }

  @Test
  public void whenBadValuesSpecifiedForOperatorLevelEnumProperties_reportError() throws Exception {
    for (String propertyName : OPERATOR_LEVEL_ENUM_PROPERTIES) {
      setProperty(propertyName, "bogus");
    }

    assertThat(
        getProcessingError(),
        allOf(
            containsEnumParameterError("operatorImagePullPolicy", PULL_POLICIES),
            containsEnumParameterError("javaLoggingLevel", LOGGING_LEVELS),
            containsEnumParameterError("externalRestOption", EXTERNAL_REST_OPTIONS),
            containsEnumParameterError("internalRestOption", INTERNAL_REST_OPTIONS)));
  }

  private Matcher<String> containsEnumParameterError(String propertyName, String... validValues) {
    return containsString(
        String.format(
            "The property %s must be one of the following values [%s]",
            propertyName, String.join(" ", validValues)));
  }

  @Test
  @Ignore("fails to merge null overrides")
  public void whenExternalRestNotEnabled_ignoreMissingRelatedParameters() throws Exception {
    setProperty("externalRestOption", "NONE");

    removeProperty("externalRestHttpsPort");
    removeProperty("externalOperatorCertSans");
    removeProperty("externalOperatorCert");
    removeProperty("externalOperatorKey");

    assertThat(getProcessingError(), emptyString());
  }

  @Test
  public void whenExternalRestNotEnabled_ignoreRelatedParameterErrors() throws Exception {
    setProperty("externalRestOption", "NONE");

    setProperty("externalRestHttpsPort", "Not a number");
    setProperty("externalOperatorCertSans", false);
    setProperty("externalOperatorCert", 1234);
    setProperty("externalOperatorKey", true);

    assertThat(getProcessingError(), emptyString());
  }

  @Test
  public void whenExternalRestSelfSignedCert_reportRelatedParameterErrors() throws Exception {
    misconfigureExternalRestSelfSignedCert();

    assertThat(
        getProcessingError(),
        allOf(
            containsTypeError("externalRestHttpsPort", "float64", "string"),
            containsTypeError("externalOperatorCertSans", "string", "bool"),
            containsTypeError("externalOperatorCert", "string", "float64"),
            containsTypeError("externalOperatorKey", "string", "bool")));
  }

  @Test
  public void whenExternalRestCustomCert_reportRelatedParameterErrors() throws Exception {
    misconfigureExternalRestCustomCert();

    assertThat(
        getProcessingError(),
        allOf(
            containsTypeError("externalRestHttpsPort", "float64", "string"),
            containsTypeError("externalOperatorCert", "string", "float64"),
            containsTypeError("externalOperatorKey", "string", "bool")));
  }

  private void misconfigureExternalRestSelfSignedCert() {
    setProperty("externalRestOption", "SELF_SIGNED_CERT");

    setProperty("externalRestHttpsPort", "Not a number");
    setProperty("externalOperatorCertSans", false);
    setProperty("externalOperatorCert", 1234);
    setProperty("externalOperatorKey", true);
  }

  private void misconfigureExternalRestCustomCert() {
    setProperty("externalRestOption", "CUSTOM_CERT");

    setProperty("externalRestHttpsPort", "Not a number");
    setProperty("externalOperatorCert", 1234);
    setProperty("externalOperatorKey", true);
  }

  @Test
  public void whenInternalRestSelfSignedCert_reportRelatedParameterErrors() throws Exception {
    misconfigureInternalRestSelfSignedCert();

    assertThat(
        getProcessingError(),
        allOf(
            containsTypeError("internalOperatorCert", "string", "float64"),
            containsTypeError("internalOperatorKey", "string", "bool")));
  }

  @Test
  public void whenInternalRestCustomCert_reportRelatedParameterErrors() throws Exception {
    misconfigureInternalRestCustomCert();

    assertThat(
        getProcessingError(),
        allOf(
            containsTypeError("internalOperatorCert", "string", "float64"),
            containsTypeError("internalOperatorKey", "string", "bool")));
  }

  private void misconfigureInternalRestSelfSignedCert() {
    setProperty("internalRestOption", "SELF_SIGNED_CERT");

    setProperty("internalOperatorCert", 1234);
    setProperty("internalOperatorKey", true);
  }

  private void misconfigureInternalRestCustomCert() {
    setProperty("internalRestOption", "CUSTOM_CERT");

    setProperty("internalOperatorCert", 1234);
    setProperty("internalOperatorKey", true);
  }

  @Test
  @Ignore("fails to merge null overrides")
  public void whenRemoteDebugNodePortDisabled_ignoreMissingPortNumbers() throws Exception {
    setProperty("remoteDebugNodePortEnabled", false);

    removeProperty("internalDebugHttpPort");
    removeProperty("externalDebugHttpPort");

    assertThat(getProcessingError(), emptyString());
  }

  @Test
  public void whenRemoteDebugNodePortDisabled_reportMissingPortNumbers() throws Exception {
    setProperty("remoteDebugNodePortEnabled", true);

    removeProperty("internalDebugHttpPort");
    removeProperty("externalDebugHttpPort");

    assertThat(
        getProcessingError(),
        both(containsMissingIntParameterError("internalDebugHttpPort"))
            .and(containsMissingIntParameterError("externalDebugHttpPort")));
  }

  private Matcher<String> containsMissingIntParameterError(String propertyName) {
    return containsString(String.format("The float64 property %s must be specified", propertyName));
  }

  @Test
  public void whenRemoteDebugNodePortDisabled_reportNonNumericPortNumbers() throws Exception {
    setProperty("remoteDebugNodePortEnabled", true);

    setProperty("internalDebugHttpPort", true);
    setProperty("externalDebugHttpPort", "abcd");

    assertThat(
        getProcessingError(),
        both(containsTypeError("internalDebugHttpPort", "float64", "bool"))
            .and(containsTypeError("externalDebugHttpPort", "float64", "string")));
  }

  @Test
  public void whenDomainsNamespacesPrimitiveType_reportError() throws Exception {
    setProperty("domainsNamespaces", true);

    assertThat(getProcessingError(), containsTypeError("domainsNamespaces", "map", "bool"));
  }

  @Test
  public void whenDomainsNamespacesCreateNotBool_reportError() throws Exception {
    setProperty(
        "domainsNamespaces",
        ImmutableMap.of("aaa", ImmutableMap.of("createDomainsNamespace", 123)));

    assertThat(
        getProcessingError(), containsTypeError("createDomainsNamespace", "bool", "float64"));
  }

  @Test
  public void whenDefaultNamespaceHasCreatedTrue_reportError() throws Exception {
    setProperty(
        "domainsNamespaces",
        ImmutableMap.of("default", ImmutableMap.of("createDomainsNamespace", true)));

    assertThat(
        getProcessingError(),
        containsString(
            "The effective createDomainsNamespace value for the 'default' domainsNamespace must be set to false."));
  }

  @Test
  public void whenDefaultNamespaceHasCreatedFalse_doNotReportError() throws Exception {
    setProperty(
        "domainsNamespaces",
        ImmutableMap.of("default", ImmutableMap.of("createDomainsNamespace", false)));

    assertThat(getProcessingError(), emptyString());
  }
}

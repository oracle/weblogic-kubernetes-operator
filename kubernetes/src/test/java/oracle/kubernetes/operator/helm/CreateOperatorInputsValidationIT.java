// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helm;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.both;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.emptyString;

import java.util.Map;
import org.hamcrest.Matcher;
import org.junit.Before;
import org.junit.Test;

public class CreateOperatorInputsValidationIT extends OperatorChartITBase {

  private static final String MISSING = "%s %s must be specified";

  private static final String WRONG_TYPE = "%s must be a %s : %s";

  private static final String[] TOP_LEVEL_BOOLEAN_PROPERTIES = {
    "createSharedOperatorResources", "createOperator" // , "elkIntegrationEnabled"
  };

  private static final String[] OPERATOR_LEVEL_BOOLEAN_PROPERTIES = {"elkIntegrationEnabled"};

  private static final String[] OPERATOR_LEVEL_STRING_PROPERTIES = {
    "operatorServiceAccount", "image"
  };

  private static final String[] OPERATOR_LEVEL_ENUM_PROPERTIES = {
    "imagePullPolicy", "javaLoggingLevel", "externalRestOption", "internalRestOption"
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
    return getChart(newInstallArgs(overrides)).getError();
  }

  private Matcher<String> containsTypeError(String name, String expectedType, String actualType) {
    return containsString(String.format(WRONG_TYPE, name, expectedType, actualType));
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
    return containsString(String.format(MISSING, "bool", propertyName));
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
    return containsString(String.format(MISSING, "string", propertyName));
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
    return containsString(String.format(MISSING, "string", propertyName));
  }

  @Test
  public void whenBadValuesSpecifiedForOperatorLevelEnumProperties_reportError() throws Exception {
    String badValue = "bogus";
    for (String propertyName : OPERATOR_LEVEL_ENUM_PROPERTIES) {
      setProperty(propertyName, badValue);
    }

    assertThat(
        getProcessingError(),
        allOf(
            containsEnumParameterError("imagePullPolicy", badValue, PULL_POLICIES),
            containsEnumParameterError("javaLoggingLevel", badValue, LOGGING_LEVELS),
            containsEnumParameterError("externalRestOption", badValue, EXTERNAL_REST_OPTIONS),
            containsEnumParameterError("internalRestOption", badValue, INTERNAL_REST_OPTIONS)));
  }

  private Matcher<String> containsEnumParameterError(
      String propertyName, String badValue, String... validValues) {
    return containsString(
        String.format(
            "%s must be one of the following values [%s] : %s",
            propertyName, String.join(" ", validValues), badValue));
  }

  @Test
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
    return containsString(String.format(MISSING, "float64", propertyName));
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
  public void whenDomainNamespacesPrimitiveType_reportError() throws Exception {
    setProperty("domainNamespaces", true);

    assertThat(getProcessingError(), containsTypeError("domainNamespaces", "slice", "bool"));
  }
}

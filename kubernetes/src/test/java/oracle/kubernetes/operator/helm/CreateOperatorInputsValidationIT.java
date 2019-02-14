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

  private static final String MUTEX = "%s can not be present when %s is defined";

  private static final String MISSING = "%s %s must be specified";

  private static final String WRONG_TYPE = "%s must be a %s : %s";

  private static final String[] OPERATOR_LEVEL_BOOLEAN_PROPERTIES = {
    "externalRestEnabled", "remoteDebugNodePortEnabled", "elkIntegrationEnabled"
  };

  private static final String[] OPERATOR_LEVEL_STRING_PROPERTIES = {"serviceAccount", "image"};

  private static final String[] OPERATOR_LEVEL_ENUM_PROPERTIES = {
    "imagePullPolicy", "javaLoggingLevel"
  };

  private static final String[] LOGGING_LEVELS = {
    "SEVERE", "WARNING", "INFO", "CONFIG", "FINE", "FINER", "FINEST"
  };

  private static final String[] PULL_POLICIES = {"Always", "IfNotPresent", "Never"};

  private HelmOperatorYamlFactory factory = new HelmOperatorYamlFactory();
  private Map<String, Object> overrides;

  @Before
  public void setUp() throws Exception {
    overrides = ((HelmOperatorValues) factory.newOperatorValues()).createMap();
  }

  @Test
  public void whenStringSpecifiedForOperatorLevelProperties_reportError() throws Exception {
    for (String propertyName : OPERATOR_LEVEL_BOOLEAN_PROPERTIES) {
      setProperty(propertyName, "this is not a boolean");
    }

    String processingError = getProcessingError();

    for (String propertyName : OPERATOR_LEVEL_BOOLEAN_PROPERTIES) {
      assertThat(processingError, containsTypeError(propertyName, "bool", "string"));
    }
  }

  @Test
  public void whenOperatorLevelBooleanPropertiesMissing_reportError() throws Exception {
    for (String propertyName : OPERATOR_LEVEL_BOOLEAN_PROPERTIES) {
      removeProperty(propertyName);
    }

    String processingError = getProcessingError();

    for (String propertyName : OPERATOR_LEVEL_BOOLEAN_PROPERTIES) {
      assertThat(processingError, containsMissingBoolParameterError(propertyName));
    }
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
            containsEnumParameterError("javaLoggingLevel", badValue, LOGGING_LEVELS)));
  }

  @Test
  public void whenExternalRestEnabled_reportMissingRelatedParameters() throws Exception {
    setProperty("externalRestEnabled", true);

    removeProperty("externalRestHttpsPort");
    removeProperty("externalRestIdentitySecret");
    removeProperty("externalOperatorCert");
    removeProperty("externalOperatorKey");

    assertThat(
        getProcessingError(),
        allOf(
            containsMissingStringParameterError("externalRestIdentitySecret"),
            containsMissingIntParameterError("externalRestHttpsPort")));
  }

  @Test
  public void whenExternalRestNotEnabled_ignoreMissingRelatedParameters() throws Exception {
    setProperty("externalRestEnabled", false);

    removeProperty("externalRestHttpsPort");
    removeProperty("externalOperatorCert");
    removeProperty("externalOperatorKey");

    assertThat(getProcessingError(), emptyString());
  }

  @Test
  public void whenExternalRestEnabled_reportRelatedParameterErrorsLegacy() throws Exception {
    setProperty("externalRestEnabled", true);

    setProperty("externalRestHttpsPort", "Not a number");
    setProperty("externalOperatorCert", 1234);
    setProperty("externalOperatorKey", true);

    assertThat(
        getProcessingError(),
        allOf(
            containsTypeError("externalRestHttpsPort", "float64", "string"),
            containsTypeError("externalOperatorCert", "string", "float64"),
            containsTypeError("externalOperatorKey", "string", "bool")));
  }

  @Test
  public void whenExternalRestEnabled_reportRelatedParameterErrors() throws Exception {
    setProperty("externalRestEnabled", true);
    setProperty("externalRestHttpsPort", "Not a number");
    setProperty("externalRestIdentitySecret", 1234);

    assertThat(
        getProcessingError(),
        allOf(
            containsTypeError("externalRestHttpsPort", "float64", "string"),
            containsTypeError("externalRestIdentitySecret", "string", "float64")));
  }

  @Test
  public void whenExternalRestNotEnabled_ignoreRelatedParameterErrors() throws Exception {
    setProperty("externalRestEnabled", false);

    setProperty("externalRestHttpsPort", "Not a number");
    setProperty("externalRestIdentitySecret", 1234);
    setProperty("externalOperatorCert", 1234);
    setProperty("externalOperatorKey", true);

    assertThat(getProcessingError(), emptyString());
  }

  @Test
  public void whenExternalOperatorSecret_ExcludeCertKeyErrors() throws Exception {
    setProperty("externalRestEnabled", true);

    setProperty("externalRestIdentitySecret", "secretName");
    setProperty("externalOperatorCert", "cert");
    setProperty("externalOperatorKey", "key");

    assertThat(
        getProcessingError(),
        allOf(
            containsMutexParameterError("externalOperatorKey", "externalRestIdentitySecret"),
            containsMutexParameterError("externalOperatorCert", "externalRestIdentitySecret")));
  }

  @Test
  public void whenRemoteDebugNodePortEnabled_reportMissingRelatedParameters() throws Exception {
    setProperty("remoteDebugNodePortEnabled", true);

    removeProperty("internalDebugHttpPort");
    removeProperty("externalDebugHttpPort");

    assertThat(
        getProcessingError(),
        both(containsMissingIntParameterError("internalDebugHttpPort"))
            .and(containsMissingIntParameterError("externalDebugHttpPort")));
  }

  @Test
  public void whenRemoteDebugNodePortNotEnabled_ignoreMissingRelatedParameters() throws Exception {
    setProperty("remoteDebugNodePortEnabled", false);

    removeProperty("internalDebugHttpPort");
    removeProperty("externalDebugHttpPort");

    assertThat(getProcessingError(), emptyString());
  }

  @Test
  public void whenRemoteDebugNodePortEnabled_reportRelatedParameterErrors() throws Exception {
    setProperty("remoteDebugNodePortEnabled", true);

    setProperty("internalDebugHttpPort", true);
    setProperty("externalDebugHttpPort", "abcd");

    assertThat(
        getProcessingError(),
        both(containsTypeError("internalDebugHttpPort", "float64", "bool"))
            .and(containsTypeError("externalDebugHttpPort", "float64", "string")));
  }

  @Test
  public void whenRemoteDebugNodePortNotEnabled_ignoreRelatedParameterErrors() throws Exception {
    setProperty("remoteDebugNodePortEnabled", false);

    setProperty("internalDebugHttpPort", true);
    setProperty("externalDebugHttpPort", "abcd");

    assertThat(getProcessingError(), emptyString());
  }

  @Test
  public void whenElkIntegrationEnabled_reportMissingRelatedParameters() throws Exception {
    setProperty("elkIntegrationEnabled", true);

    removeProperty("logStashImage");
    removeProperty("elasticSearchHost");
    removeProperty("elasticSearchPort");

    assertThat(
        getProcessingError(),
        allOf(
            containsMissingStringParameterError("logStashImage"),
            containsMissingStringParameterError("elasticSearchHost"),
            containsMissingIntParameterError("elasticSearchPort")));
  }

  @Test
  public void whenElkIntegrationNotEnabled_ignoreMissingRelatedParameters() throws Exception {
    setProperty("elkIntegrationEnabled", false);

    setProperty("logStashImage", true);
    setProperty("elasticSearchHost", 7);
    setProperty("elasticSearchPort", false);

    assertThat(getProcessingError(), emptyString());
  }

  @Test
  public void whenElkIntegrationEnabled_reportRelatedParametersErrors() throws Exception {
    setProperty("elkIntegrationEnabled", true);

    setProperty("logStashImage", true);
    setProperty("elasticSearchHost", 7);
    setProperty("elasticSearchPort", false);

    assertThat(
        getProcessingError(),
        allOf(
            containsTypeError("logStashImage", "string", "bool"),
            containsTypeError("elasticSearchHost", "string", "float64"),
            containsTypeError("elasticSearchPort", "float64", "bool")));
  }

  @Test
  public void whenElkIntegrationEnabled_ignoreRelatedParametersErrors() throws Exception {
    setProperty("elkIntegrationEnabled", false);

    setProperty("logStashImage", true);
    setProperty("elasticSearchHost", 7);
    setProperty("elasticSearchPort", false);

    assertThat(getProcessingError(), emptyString());
  }

  @Test
  public void whenDomainNamespacesPrimitiveType_reportError() throws Exception {
    setProperty("domainNamespaces", true);

    assertThat(getProcessingError(), containsTypeError("domainNamespaces", "slice", "bool"));
  }

  @Test
  public void whenImagePullSecretsPrimitiveType_reportError() throws Exception {
    setProperty("imagePullSecrets", true);

    assertThat(getProcessingError(), containsTypeError("imagePullSecrets", "slice", "bool"));
  }

  private Matcher<String> containsTypeError(String name, String expectedType, String actualType) {
    return containsString(String.format(WRONG_TYPE, name, expectedType, actualType));
  }

  private Matcher<String> containsMutexParameterError(String excluded, String value) {
    return containsString(String.format(MUTEX, excluded, value));
  }

  private Matcher<String> containsMissingEnumParameterError(String propertyName) {
    return containsString(String.format(MISSING, "string", propertyName));
  }

  private Matcher<String> containsMissingStringParameterError(String propertyName) {
    return containsString(String.format(MISSING, "string", propertyName));
  }

  private Matcher<String> containsMissingBoolParameterError(String propertyName) {
    return containsString(String.format(MISSING, "bool", propertyName));
  }

  private Matcher<String> containsMissingIntParameterError(String propertyName) {
    return containsString(String.format(MISSING, "float64", propertyName));
  }

  private Matcher<String> containsEnumParameterError(
      String propertyName, String badValue, String... validValues) {
    return containsString(
        String.format(
            "%s must be one of the following values [%s] : %s",
            propertyName, String.join(" ", validValues), badValue));
  }

  private void setProperty(String propertyName, Object value) {
    overrides.put(propertyName, value);
  }

  private void removeProperty(String propertyName) {
    setProperty(propertyName, null);
  }

  private String getProcessingError() throws Exception {
    return getChart(newInstallArgs(overrides)).getError();
  }
}

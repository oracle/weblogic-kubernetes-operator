// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helm;

import static org.hamcrest.MatcherAssert.assertThat;
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

  private static final String[] REQUIRED_BOOLEAN_PROPERTIES = {
    "elkIntegrationEnabled", "createOperatorNamespace", "externalRestEnabled"
  };

  private static final String[] REQUIRED_STRING_PROPERTIES = {
    "operatorNamespace", "operatorServiceAccount", "operatorImage"
  };

  private static final String[] REQUIRED_ENUM_PROPERTIES = {
    "operatorImagePullPolicy", "javaLoggingLevel"
  };

  private static final String[] LOGGING_LEVELS = {
    "SEVERE", "WARNING", "INFO", "CONFIG", "FINE", "FINER", "FINEST"
  };

  private final String[] PULL_POLICIES = {"Always", "IfNotPresent", "Never"};

  private HelmOperatorYamlFactory factory = new HelmOperatorYamlFactory();
  private Map<String, Object> overrides;

  @Before
  public void setUp() throws Exception {
    overrides = ((HelmOperatorValues) factory.newOperatorValues()).createMap();
  }

  @Test
  public void whenStringSpecifiedForBooleanProperties_reportError() throws Exception {
    for (String propertyName : REQUIRED_BOOLEAN_PROPERTIES) {
      setProperty(propertyName, "this is not a boolean");
    }

    String processingError = getProcessingError();

    for (String propertyName : REQUIRED_BOOLEAN_PROPERTIES) {
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
  public void whenRequiredBooleanPropertiesMissing_reportError() throws Exception {
    for (String propertyName : REQUIRED_BOOLEAN_PROPERTIES) {
      removeProperty(propertyName);
    }

    String processingError = getProcessingError();

    for (String propertyName : REQUIRED_BOOLEAN_PROPERTIES) {
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
  public void whenRequiredStringPropertiesMissing_reportError() throws Exception {
    for (String propertyName : REQUIRED_STRING_PROPERTIES) {
      removeProperty(propertyName);
    }

    String processingError = getProcessingError();

    for (String propertyName : REQUIRED_STRING_PROPERTIES) {
      assertThat(processingError, containsMissingStringParameterError(propertyName));
    }
  }

  private Matcher<String> containsMissingStringParameterError(String propertyName) {
    return containsString(String.format("The string property %s must be specified", propertyName));
  }

  @Test
  public void whenEnumPropertiesMissing_reportError() throws Exception {
    for (String propertyName : REQUIRED_ENUM_PROPERTIES) {
      removeProperty(propertyName);
    }

    String processingError = getProcessingError();

    for (String propertyName : REQUIRED_ENUM_PROPERTIES) {
      assertThat(processingError, containsMissingEnumParameterError(propertyName));
    }
  }

  private Matcher<String> containsMissingEnumParameterError(String propertyName) {
    return containsString(String.format("The string property %s must be specified", propertyName));
  }

  @Test
  public void whenBadValuesSpecifiedForEnumProperties_reportError() throws Exception {
    for (String propertyName : REQUIRED_ENUM_PROPERTIES) {
      setProperty(propertyName, "bogus");
    }

    assertThat(
        getProcessingError(),
        both(containsEnumParameterError("operatorImagePullPolicy", PULL_POLICIES))
            .and(containsEnumParameterError("javaLoggingLevel", LOGGING_LEVELS)));
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
    setProperty("externalRestEnabled", false);

    removeProperty("externalRestHttpPort");
    removeProperty("externalOperatorCert");
    removeProperty("externalOperatorKey");

    assertThat(getProcessingError(), emptyString());
  }

  @Test
  public void whenExternalRestNotEnabled_ignoreRelatedParameterErrors() throws Exception {
    setProperty("externalRestEnabled", false);

    setProperty("externalRestHttpPort", "Not a number");
    setProperty("externalOperatorCert", 1234);
    setProperty("externalOperatorKey", true);

    assertThat(getProcessingError(), emptyString());
  }

  @Test
  public void whenExternalRestEnabled_reportRelatedParameterErrors() throws Exception {
    misconfigureExternalRest();

    assertThat(
        getProcessingError(),
        both(containsTypeError("externalOperatorCert", "string", "float64"))
            .and(containsTypeError("externalOperatorKey", "string", "bool")));
  }

  private void misconfigureExternalRest() {
    setProperty("externalRestEnabled", true);

    setProperty("externalRestHttpPort", "Not a number");
    setProperty("externalOperatorCert", 1234);
    setProperty("externalOperatorKey", true);
  }

  @Test
  @Ignore("fails to detect non-numeric port")
  public void whenExternalRestEnabled_reportPortNotANumber() throws Exception {
    misconfigureExternalRest();

    assertThat(getProcessingError(), containsTypeError("externalRestHttpPort", "int", "string"));
  }

  @Test
  public void whenRemoteDebugNotPortDisabled_ignoreMissingPortNumbers() throws Exception {
    setProperty("remoteDebugNotePortEnabled", false);

    removeProperty("internalDebugHttpPort");
    removeProperty("externalDebugHttpPort");

    assertThat(getProcessingError(), emptyString());
  }

  @Test
  @Ignore("ignores missing port numbers")
  public void whenRemoteDebugNotPortDisabled_reportMissingPortNumbers() throws Exception {
    setProperty("remoteDebugNotePortEnabled", true);

    removeProperty("internalDebugHttpPort");
    removeProperty("externalDebugHttpPort");

    assertThat(
        getProcessingError(),
        both(containsMissingIntParameterError("internalDebugHttpPort"))
            .and(containsMissingIntParameterError("externalDebugHttpPort")));
  }

  private Matcher<String> containsMissingIntParameterError(String propertyName) {
    return containsString(String.format("The int property %s must be specified", propertyName));
  }

  @Test
  @Ignore("ignores port number type")
  public void whenRemoteDebugNotPortDisabled_reportNonNumericPortNumbers() throws Exception {
    setProperty("remoteDebugNotePortEnabled", true);

    setProperty("internalDebugHttpPort", true);
    setProperty("externalDebugHttpPort", "abcd");

    assertThat(
        getProcessingError(),
        both(containsTypeError("internalDebugHttpPort", "int", "bool"))
            .and(containsTypeError("externalDebugHttpPort", "int", "string")));
  }

  @Test
  public void whenDomainsNamespacesPrimitiveType_reportError() throws Exception {
    setProperty("domainsNamespaces", true);

    assertThat(getProcessingError(), containsTypeError("domainsNamespaces", "map", "bool"));
  }

  @Test
  @Ignore("Does not validate that createDomainsNamespace nested value is boolean")
  public void whenDomainsNamespacesCreateNotBool_reportError() throws Exception {
    setProperty(
        "domainsNamespaces",
        ImmutableMap.of("aaa", ImmutableMap.of("createDomainsNamespace", 123)));

    assertThat(getProcessingError(), containsTypeError("", "bool", "int"));
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

// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
package oracle.kubernetes.operator.create;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static oracle.kubernetes.operator.create.ExecResultMatcher.errorRegexp;
import static oracle.kubernetes.operator.create.ExecResultMatcher.failsAndPrints;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

import static oracle.kubernetes.operator.create.CreateOperatorInputs.newInputs;

/**
 * Tests that create-weblogic-operator.sh properly validates the parameters
 * that a customer can specify in the inputs yaml file.
 */
public class CreateOperatorInputsValidationTest {

  private UserProjects userProjects;

  @Before
  public void setup() throws Exception {
    userProjects = UserProjects.createUserProjectsDirectory();
  }

  @After
  public void tearDown() throws Exception {
    if (userProjects != null) {
      userProjects.remove();
    }
  }

  private static final String PARAM_SERVICE_ACCOUNT = "serviceAccount";
  private static final String PARAM_NAMESPACE = "namespace";
  private static final String PARAM_TARGET_NAMESPACES = "targetNamespaces";
  private static final String PARAM_IMAGE = "image";
  private static final String PARAM_IMAGE_PULL_POLICY = "imagePullPolicy";
  private static final String PARAM_EXTERNAL_REST_OPTION = "externalRestOption";
  private static final String PARAM_EXTERNAL_REST_HTTPS_PORT = "externalRestHttpsPort";
  private static final String PARAM_EXTERNAL_SANS = "externalSans";
  private static final String PARAM_EXTERNAL_OPERATOR_CERT = "externalOperatorCert";
  private static final String PARAM_EXTERNAL_OPERATOR_KEY = "externalOperatorKey";
  private static final String PARAM_REMOTE_DEBUG_NODE_PORT_ENABLED = "remoteDebugNodePortEnabled";
  private static final String PARAM_INTERNAL_DEBUG_HTTP_PORT = "internalDebugHttpPort";
  private static final String PARAM_EXTERNAL_DEBUG_HTTP_PORT = "externalDebugHttpPort";
  private static final String PARAM_JAVA_LOGGING_LEVEL = "javaLoggingLevel";
  private static final String PARAM_ELK_INTEGRATION_ENABLED = "elkIntegrationEnabled";

  @Test
  public void createOperator_with_missingServiceAccount_FailsAndReturnsError() throws Exception {
    assertThat(
      execCreateOperator(newInputs().serviceAccount("")),
      failsAndPrints(paramMissingError(PARAM_SERVICE_ACCOUNT)));
  }

  @Test
  public void createOperator_with_upperCaseServiceAccount_FailsAndReturnsError() throws Exception {
    String val = "TestServiceAccount";
    assertThat(
      execCreateOperator(newInputs().serviceAccount(val)),
      failsAndPrints(paramNotLowercaseError(PARAM_SERVICE_ACCOUNT, val)));
  }

  @Test
  public void createOperator_with_missingNamespace_FailsAndReturnsError() throws Exception {
    assertThat(
      execCreateOperator(newInputs().namespace("")),
      failsAndPrints(paramMissingError(PARAM_NAMESPACE)));
  }

  @Test
  public void createOperator_with_upperCaseNamespace_FailsAndReturnsError() throws Exception {
    String val = "TestNamespace";
    assertThat(
      execCreateOperator(newInputs().namespace(val)),
      failsAndPrints(paramNotLowercaseError(PARAM_NAMESPACE, val)));
  }

  @Test
  public void createOperator_with_missingTargetNamespaces_FailsAndReturnsError() throws Exception {
    assertThat(
      execCreateOperator(newInputs().targetNamespaces("")),
      failsAndPrints(paramMissingError(PARAM_TARGET_NAMESPACES)));
  }

  @Test
  public void createOperator_with_missingImage_FailsAndReturnsError() throws Exception {
    assertThat(
      execCreateOperator(newInputs().image("")),
      failsAndPrints(paramMissingError(PARAM_IMAGE)));
  }

  @Test
  public void createOperator_with_missingImagePullPolicy_FailsAndReturnsError() throws Exception {
    assertThat(
      execCreateOperator(newInputs().imagePullPolicy("")),
      failsAndPrints(paramMissingError(PARAM_IMAGE_PULL_POLICY)));
  }

  @Test
  public void createOperator_with_missingExternalRestOption_FailsAndReturnsError() throws Exception {
    assertThat(
      execCreateOperator(newInputs().externalRestOption("")),
      failsAndPrints(paramMissingError(PARAM_EXTERNAL_REST_OPTION)));
  }

  @Test
  public void createOperator_with_invalidExternalRestOption_FailsAndReturnsError() throws Exception {
    String val = "invalid-rest-option";
    assertThat(
      execCreateOperator(newInputs().externalRestOption(val)),
      failsAndPrints(errorRegexp("Invalid.*" + PARAM_EXTERNAL_REST_OPTION + ".*" + val)));
  }

  @Test
  public void createOperator_with_externalRestCustomCert_missingExternalRestHttpsPort_FailsAndReturnsError() throws Exception {
    assertThat(
      execCreateOperator(
        newInputs().setupExternalRestCustomCert().externalRestHttpsPort("")),
      failsAndPrints(paramMissingError(PARAM_EXTERNAL_REST_HTTPS_PORT)));
  }

  @Test
  public void createOperator_with_externalRestCustomCert_missingExternalOperatorCert_FailsAndReturnsError() throws Exception {
    assertThat(
      execCreateOperator(
        newInputs().setupExternalRestCustomCert().externalOperatorCert("")),
      failsAndPrints(paramMissingError(PARAM_EXTERNAL_OPERATOR_CERT)));
  }

  @Test
  public void createOperator_with_externalRestCustomCert_missingExternalOperatorKey_FailsAndReturnsError() throws Exception {
    assertThat(
      execCreateOperator(
        newInputs().setupExternalRestCustomCert().externalOperatorKey("")),
      failsAndPrints(paramMissingError(PARAM_EXTERNAL_OPERATOR_KEY)));
  }

  @Test
  public void createOperator_with_externalRestSelfSignedCert_missingExternalRestHttpsPort_FailsAndReturnsError() throws Exception {
    assertThat(
      execCreateOperator(
        newInputs().setupExternalRestSelfSignedCert().externalRestHttpsPort("")),
      failsAndPrints(paramMissingError(PARAM_EXTERNAL_REST_HTTPS_PORT)));
  }

  @Test
  public void createOperator_with_externalRestSelfSignedCert_missingExternalSans_FailsAndReturnsError() throws Exception {
    assertThat(
      execCreateOperator(
        newInputs().setupExternalRestSelfSignedCert().externalSans("")),
      failsAndPrints(paramMissingError(PARAM_EXTERNAL_SANS)));
  }

  @Test
  public void createOperator_with_externalRestSelfSignedCert_invalidExternalSans_FailsAndReturnsError() throws Exception {
    String val = "invalid-sans";
    assertThat(
      execCreateOperator(
        newInputs().setupExternalRestSelfSignedCert().externalSans(val)),
      failsAndPrints("invalid subject alternative names", val));
  }

  @Test
  public void createOperator_with_missingJavaLoggingLevel_FailsAndReturnsError() throws Exception {
    assertThat(
      execCreateOperator(newInputs().javaLoggingLevel("")),
      failsAndPrints(paramMissingError(PARAM_JAVA_LOGGING_LEVEL)));
  }

  private String paramMissingError(String param) {
    return errorRegexp(param + ".*missing");
  }

  private String paramNotLowercaseError(String param, String val) {
    return errorRegexp(param + ".*lowercase.*" + val);
  }

  private ExecResult execCreateOperator(CreateOperatorInputs inputs) throws Exception {
    return ExecCreateOperator.execCreateOperator(userProjects.getPath(), inputs);
  }

/*
TODO
  - test all valid enum values
  - test bogus enum values
  - test upper / lower case
  - test ext rest option dependent sub options (might have already done this)

targetNamespaces
image
imagePullPolicy
imagePullSecretName
externalRestOption
externalRestHttpsPort
externalSans
externalOperatorCert
externalOperatorKey
remoteDebugNodePortEnabled
internalDebugHttpPort
externalDebugHttpPort
javaLoggingLevel
elkIntegrationEnabled
*/
}

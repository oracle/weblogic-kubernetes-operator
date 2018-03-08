// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
package oracle.kubernetes.operator.create;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static oracle.kubernetes.operator.create.ExecResultMatcher.errorRegexp;
import static oracle.kubernetes.operator.create.ExecResultMatcher.failsAndPrints;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

import static oracle.kubernetes.operator.create.CreateOperatorInputs.*;

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
  public void createOperator_with_missingServiceAccount_failsAndReturnsError() throws Exception {
    assertThat(
      execCreateOperator(newInputs().serviceAccount("")),
      failsAndPrints(paramMissingError(PARAM_SERVICE_ACCOUNT)));
  }

  @Test
  public void createOperator_with_upperCaseServiceAccount_failsAndReturnsError() throws Exception {
    String val = "TestServiceAccount";
    assertThat(
      execCreateOperator(newInputs().serviceAccount(val)),
      failsAndPrints(paramNotLowercaseError(PARAM_SERVICE_ACCOUNT, val)));
  }

  @Test
  public void createOperator_with_missingNamespace_failsAndReturnsError() throws Exception {
    assertThat(
      execCreateOperator(newInputs().namespace("")),
      failsAndPrints(paramMissingError(PARAM_NAMESPACE)));
  }

  @Test
  public void createOperator_with_upperCaseNamespace_failsAndReturnsError() throws Exception {
    String val = "TestNamespace";
    assertThat(
      execCreateOperator(newInputs().namespace(val)),
      failsAndPrints(paramNotLowercaseError(PARAM_NAMESPACE, val)));
  }

  @Test
  public void createOperator_with_missingTargetNamespaces_failsAndReturnsError() throws Exception {
    assertThat(
      execCreateOperator(newInputs().targetNamespaces("")),
      failsAndPrints(paramMissingError(PARAM_TARGET_NAMESPACES)));
  }

  @Test
  public void createOperator_with_missingImage_failsAndReturnsError() throws Exception {
    assertThat(
      execCreateOperator(newInputs().image("")),
      failsAndPrints(paramMissingError(PARAM_IMAGE)));
  }

  @Test
  public void createOperator_with_missingImagePullPolicy_failsAndReturnsError() throws Exception {
    assertThat(
      execCreateOperator(newInputs().imagePullPolicy("")),
      failsAndPrints(paramMissingError(PARAM_IMAGE_PULL_POLICY)));
  }

  @Test
  public void createOperator_with_invalidImagePullPolicy_failsAndReturnsError() throws Exception {
    String val = "invalid-image-pull-policy";
    assertThat(
      execCreateOperator(newInputs().imagePullPolicy(val)),
      failsAndPrints(invalidParmValueError(PARAM_IMAGE_PULL_POLICY, val)));
  }

  @Test
  public void createOperator_with_ImagePullPolicyIfNotPresent_succeeds() throws Exception {
    createOperator_with_validImagePullPolicy_succeeds(IMAGE_PULL_POLICY_IF_NOT_PRESENT);
  }

  @Test
  public void createOperator_with_ImagePullPolicyAlways_succeeds() throws Exception {
    createOperator_with_validImagePullPolicy_succeeds(IMAGE_PULL_POLICY_ALWAYS);
  }

  @Test
  public void createOperator_with_ImagePullPolicyNever_succeeds() throws Exception {
    createOperator_with_validImagePullPolicy_succeeds(IMAGE_PULL_POLICY_NEVER);
  }

  @Test
  public void createOperator_with_missingExternalRestOption_failsAndReturnsError() throws Exception {
    assertThat(
      execCreateOperator(newInputs().externalRestOption("")),
      failsAndPrints(paramMissingError(PARAM_EXTERNAL_REST_OPTION)));
  }

  @Test
  public void createOperator_with_invalidExternalRestOption_failsAndReturnsError() throws Exception {
    String val = "invalid-rest-option";
    assertThat(
      execCreateOperator(newInputs().externalRestOption(val)),
      failsAndPrints(invalidParmValueError(PARAM_EXTERNAL_REST_OPTION, val)));
  }

  @Test
  public void createOperator_with_externalRestCustomCert_missingExternalRestHttpsPort_failsAndReturnsError() throws Exception {
    assertThat(
      execCreateOperator(
        newInputs().setupExternalRestCustomCert().externalRestHttpsPort("")),
      failsAndPrints(paramMissingError(PARAM_EXTERNAL_REST_HTTPS_PORT)));
  }

  @Test
  public void createOperator_with_externalRestCustomCert_missingExternalOperatorCert_failsAndReturnsError() throws Exception {
    assertThat(
      execCreateOperator(
        newInputs().setupExternalRestCustomCert().externalOperatorCert("")),
      failsAndPrints(paramMissingError(PARAM_EXTERNAL_OPERATOR_CERT)));
  }

  @Test
  public void createOperator_with_externalRestCustomCert_missingExternalOperatorKey_failsAndReturnsError() throws Exception {
    assertThat(
      execCreateOperator(
        newInputs().setupExternalRestCustomCert().externalOperatorKey("")),
      failsAndPrints(paramMissingError(PARAM_EXTERNAL_OPERATOR_KEY)));
  }

  @Test
  public void createOperator_with_externalRestSelfSignedCert_missingExternalRestHttpsPort_failsAndReturnsError() throws Exception {
    assertThat(
      execCreateOperator(
        newInputs().setupExternalRestSelfSignedCert().externalRestHttpsPort("")),
      failsAndPrints(paramMissingError(PARAM_EXTERNAL_REST_HTTPS_PORT)));
  }

  @Test
  public void createOperator_with_externalRestSelfSignedCert_missingExternalSans_failsAndReturnsError() throws Exception {
    assertThat(
      execCreateOperator(
        newInputs().setupExternalRestSelfSignedCert().externalSans("")),
      failsAndPrints(paramMissingError(PARAM_EXTERNAL_SANS)));
  }

  @Test
  public void createOperator_with_externalRestSelfSignedCert_invalidExternalSans_failsAndReturnsError() throws Exception {
    String val = "invalid-sans";
    assertThat(
      execCreateOperator(
        newInputs().setupExternalRestSelfSignedCert().externalSans(val)),
      failsAndPrints("invalid subject alternative names", val));
  }

  @Test
  public void createOperator_with_missingJavaLoggingLevel_failsAndReturnsError() throws Exception {
    assertThat(
      execCreateOperator(newInputs().javaLoggingLevel("")),
      failsAndPrints(paramMissingError(PARAM_JAVA_LOGGING_LEVEL)));
  }

  @Test
  public void createOperator_with_invalidJavaLoggingLevel_failsAndReturnsError() throws Exception {
    String val = "invalid-java-logging-level";
    assertThat(
      execCreateOperator(newInputs().javaLoggingLevel(val)),
      failsAndPrints(invalidParmValueError(PARAM_JAVA_LOGGING_LEVEL, val)));
  }

  @Test
  public void createOperator_with_javaLoggingLevelSevere_succeeds() throws Exception {
    createOperator_with_validJavaLoggingLevel_succeeds(JAVA_LOGGING_LEVEL_SEVERE);
  }

  @Test
  public void createOperator_with_javaLoggingLevelWarning_succeeds() throws Exception {
    createOperator_with_validJavaLoggingLevel_succeeds(JAVA_LOGGING_LEVEL_WARNING);
  }

  @Test
  public void createOperator_with_javaLoggingLevelInfo_succeeds() throws Exception {
    createOperator_with_validJavaLoggingLevel_succeeds(JAVA_LOGGING_LEVEL_INFO);
  }

  @Test
  public void createOperator_with_javaLoggingLevelConfig_succeeds() throws Exception {
    createOperator_with_validJavaLoggingLevel_succeeds(JAVA_LOGGING_LEVEL_CONFIG);
  }

  @Test
  public void createOperator_with_javaLoggingLevelFine_succeeds() throws Exception {
    createOperator_with_validJavaLoggingLevel_succeeds(JAVA_LOGGING_LEVEL_FINE);
  }

  @Test
  public void createOperator_with_javaLoggingLevelFiner_succeeds() throws Exception {
    createOperator_with_validJavaLoggingLevel_succeeds(JAVA_LOGGING_LEVEL_FINER);
  }

  @Test
  public void createOperator_with_javaLoggingLevelFinest_succeeds() throws Exception {
    createOperator_with_validJavaLoggingLevel_succeeds(JAVA_LOGGING_LEVEL_FINEST);
  }

  private void createOperator_with_validJavaLoggingLevel_succeeds(String level) throws Exception {
    createOperator_with_validInputs_succeeds(newInputs().javaLoggingLevel(level));
  }

  private void createOperator_with_validImagePullPolicy_succeeds(String policy) throws Exception {
    createOperator_with_validInputs_succeeds(newInputs().imagePullPolicy(policy));
  }

  private void createOperator_with_validInputs_succeeds(CreateOperatorInputs inputs) throws Exception {
    // throws an error if the inputs are not valid, succeeds otherwise:
    GeneratedOperatorYamlFiles.generateOperatorYamlFiles(inputs).remove();
  }

  private String invalidParmValueError(String param, String val) {
    return errorRegexp("Invalid.*" + param + ".*" + val);
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

test upper case fails:
  imagePullSecretName
  targetNamespaces

test option missing dependent sub options fails:
  external rest already done
  check debug enabled rules
*/
}

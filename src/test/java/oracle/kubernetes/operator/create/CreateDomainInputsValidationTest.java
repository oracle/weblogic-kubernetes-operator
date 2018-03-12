// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
package oracle.kubernetes.operator.create;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static oracle.kubernetes.operator.create.ExecResultMatcher.errorRegexp;
import static oracle.kubernetes.operator.create.ExecResultMatcher.failsAndPrints;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

import static oracle.kubernetes.operator.create.CreateDomainInputs.*;

/**
 * Tests that create-weblogic-domain.sh properly validates the parameters
 * that a customer can specify in the inputs yaml file.
 */
public class CreateDomainInputsValidationTest {

  private UserProjects userProjects;

  private static final String PARAM_ADMIN_PORT = "adminPort";
  private static final String PARAM_ADMIN_SERVER_NAME = "adminServerName";
  private static final String PARAM_CREATE_DOMAIN_SCRIPT = "createDomainScript";
  private static final String PARAM_DOMAIN_NAME = "domainName";
  private static final String PARAM_DOMAIN_UID = "domainUid";
  private static final String PARAM_STARTUP_CONTROL = "startupControl";
  private static final String PARAM_CLUSTER_NAME = "clusterName";
  private static final String PARAM_MANAGED_SERVER_COUNT = "managedServerCount";
  private static final String PARAM_MANAGED_SERVER_START_COUNT = "managedServerStartCount";
  private static final String PARAM_MANAGED_SERVER_NAME_BASE = "managedServerNameBase";
  private static final String PARAM_MANAGED_SERVER_PORT = "managedServerPort";
  private static final String PARAM_PERSISTENCE_PATH = "persistencePath";
  private static final String PARAM_PERSISTENCE_SIZE = "persistenceSize";
  private static final String PARAM_PERSISTENCE_VOLUME_CLAIM_NAME = "persistenceVolumeClaimName";
  private static final String PARAM_PERSISTENCE_VOLUME_NAME = "persistenceVolumeName";
  private static final String PARAM_PRODUCTION_MODE_ENABLED = "productionModeEnabled";
  private static final String PARAM_SECRET_NAME = "secretName";
  private static final String PARAM_IMAGE_PULL_SECRET_NAME = "imagePullSecretName";
  private static final String PARAM_T3_PUBLIC_ADDRESS = "t3PublicAddress";
  private static final String PARAM_T3_CHANNEL_PORT = "t3ChannelPort";
  private static final String PARAM_EXPOSE_ADMIN_T3_CHANNEL = "exposeAdminT3Channel";
  private static final String PARAM_ADMIN_NODE_PORT = "adminNodePort";
  private static final String PARAM_EXPOSE_ADMIN_NODE_PORT = "exposeAdminNodePort";
  private static final String PARAM_NAMESPACE = "namespace";
  private static final String PARAM_LOAD_BALANCER = "loadBalancer";
  private static final String PARAM_LOAD_BALANCER_WEB_PORT = "loadBalancerWebPort";
  private static final String PARAM_LOAD_BALANCER_ADMIN_PORT = "loadBalancerAdminPort";
  private static final String PARAM_JAVA_OPTIONS = "javaOptions";

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

  @Test
  public void createDomain_with_missingAdminPort_failsAndReturnsError() throws Exception {
    assertThat(
      execCreateDomain(newInputs().adminPort("")),
      failsAndPrints(paramMissingError(PARAM_ADMIN_PORT)));
  }

  @Test
  public void createOperator_with_invalidAdminPort_failsAndReturnsError() throws Exception {
    String val = "invalid-admin-port";
    assertThat(
      execCreateDomain(
        newInputs().adminPort(val)),
      failsAndPrints(invalidIntegerParamValueError(PARAM_ADMIN_PORT, val)));
  }

  @Test
  public void createDomain_with_missingAdminServerName_failsAndReturnsError() throws Exception {
    assertThat(
      execCreateDomain(newInputs().adminServerName("")),
      failsAndPrints(paramMissingError(PARAM_ADMIN_SERVER_NAME)));
  }

  @Test
  public void createDomain_with_missingCreateDomainScript_failsAndReturnsError() throws Exception {
    assertThat(
      execCreateDomain(newInputs().createDomainScript("")),
      failsAndPrints(paramMissingError(PARAM_CREATE_DOMAIN_SCRIPT)));
  }

  @Test
  public void createDomain_with_missingDomainName_failsAndReturnsError() throws Exception {
    assertThat(
      execCreateDomain(newInputs().domainName("")),
      failsAndPrints(paramMissingError(PARAM_DOMAIN_NAME)));
  }

  @Test
  public void createDomain_with_missinDomainUid_failsAndReturnsError() throws Exception {
    assertThat(
      execCreateDomain(newInputs().domainUid("")),
      failsAndPrints(paramMissingError(PARAM_DOMAIN_UID)));
  }

  @Test
  public void createOperator_with_upperCaseDomainUid_failsAndReturnsError() throws Exception {
    String val = "TestDomainUid";
    assertThat(
      execCreateDomain(newInputs().domainUid(val)),
      failsAndPrints(paramNotLowercaseError(PARAM_DOMAIN_UID, val)));
  }

  @Test
  public void createDomain_with_missingStartupControl_failsAndReturnsError() throws Exception {
    assertThat(
      execCreateDomain(newInputs().startupControl("")),
      failsAndPrints(paramMissingError(PARAM_STARTUP_CONTROL)));
  }

  // TBD - startup control enum legal vals: NONE, ALL, ADMIN, SPECIFIED, AUTO

  @Test
  public void createDomain_with_invalidStartupControl_failsAndReturnsError() throws Exception {
    String val = "invalid-startup-control";
    assertThat(
      execCreateDomain(newInputs().startupControl(val)),
      failsAndPrints(invalidEnumParamValueError(PARAM_STARTUP_CONTROL, val)));
  }

  @Test
  public void createDomain_with_missingClusterName_failsAndReturnsError() throws Exception {
    assertThat(
      execCreateDomain(newInputs().clusterName("")),
      failsAndPrints(paramMissingError(PARAM_CLUSTER_NAME)));
  }

  @Test
  public void createDomain_with_missingManagedServerCount_failsAndReturnsError() throws Exception {
    assertThat(
      execCreateDomain(newInputs().managedServerCount("")),
      failsAndPrints(paramMissingError(PARAM_MANAGED_SERVER_COUNT)));
  }

  @Test
  public void createOperator_with_invalidManagedServerCount_failsAndReturnsError() throws Exception {
    String val = "invalid-managed-server-count";
    assertThat(
      execCreateDomain(
        newInputs().managedServerCount(val)),
      failsAndPrints(invalidIntegerParamValueError(PARAM_MANAGED_SERVER_COUNT, val)));
  }

  @Test
  public void createDomain_with_missingManagedServerStartCount_failsAndReturnsError() throws Exception {
    assertThat(
      execCreateDomain(newInputs().managedServerStartCount("")),
      failsAndPrints(paramMissingError(PARAM_MANAGED_SERVER_START_COUNT)));
  }

  @Test
  public void createOperator_with_invalidManagedServerStartCount_failsAndReturnsError() throws Exception {
    String val = "invalid-managed-server-start-count";
    assertThat(
      execCreateDomain(
        newInputs().managedServerStartCount(val)),
      failsAndPrints(invalidIntegerParamValueError(PARAM_MANAGED_SERVER_START_COUNT, val)));
  }

  @Test
  public void createDomain_with_missingManagedServerNameBase_failsAndReturnsError() throws Exception {
    assertThat(
      execCreateDomain(newInputs().managedServerNameBase("")),
      failsAndPrints(paramMissingError(PARAM_MANAGED_SERVER_NAME_BASE)));
  }

  @Test
  public void createDomain_with_missingManagedServerPort_failsAndReturnsError() throws Exception {
    assertThat(
      execCreateDomain(newInputs().managedServerPort("")),
      failsAndPrints(paramMissingError(PARAM_MANAGED_SERVER_PORT)));
  }

  @Test
  public void createOperator_with_invalidManagedServerPort_failsAndReturnsError() throws Exception {
    String val = "invalid-managed-server-port";
    assertThat(
      execCreateDomain(
        newInputs().managedServerPort(val)),
      failsAndPrints(invalidIntegerParamValueError(PARAM_MANAGED_SERVER_PORT, val)));
  }

  @Test
  public void createDomain_with_missingPersistencePath_failsAndReturnsError() throws Exception {
    assertThat(
      execCreateDomain(newInputs().persistencePath("")),
      failsAndPrints(paramMissingError(PARAM_PERSISTENCE_PATH)));
  }

  @Test
  public void createDomain_with_missingPersistenceSize_failsAndReturnsError() throws Exception {
    assertThat(
      execCreateDomain(newInputs().persistenceSize("")),
      failsAndPrints(paramMissingError(PARAM_PERSISTENCE_SIZE)));
  }

  @Test
  public void createDomain_with_missingPersistenceVolumeClaimName_failsAndReturnsError() throws Exception {
    assertThat(
      execCreateDomain(newInputs().persistenceVolumeClaimName("")),
      failsAndPrints(paramMissingError(PARAM_PERSISTENCE_VOLUME_CLAIM_NAME)));
  }

  @Test
  public void createOperator_with_upperCasePersistenceVolumeClaimName_failsAndReturnsError() throws Exception {
    String val = "TestPersistenceVolumeClaimName";
    assertThat(
      execCreateDomain(newInputs().persistenceVolumeClaimName(val)),
      failsAndPrints(paramNotLowercaseError(PARAM_PERSISTENCE_VOLUME_CLAIM_NAME, val)));
  }

  @Test
  public void createDomain_with_missingPersistenceVolumeName_failsAndReturnsError() throws Exception {
    assertThat(
      execCreateDomain(newInputs().persistenceVolumeName("")),
      failsAndPrints(paramMissingError(PARAM_PERSISTENCE_VOLUME_NAME)));
  }

  @Test
  public void createOperator_with_upperCasePersistenceVolumeName_failsAndReturnsError() throws Exception {
    String val = "TestPersistenceVolumeName";
    assertThat(
      execCreateDomain(newInputs().persistenceVolumeName(val)),
      failsAndPrints(paramNotLowercaseError(PARAM_PERSISTENCE_VOLUME_NAME, val)));
  }

  @Test
  public void createDomain_with_missinProductionModeEnabled_failsAndReturnsError() throws Exception {
    assertThat(
      execCreateDomain(newInputs().productionModeEnabled("")),
      failsAndPrints(paramMissingError(PARAM_PRODUCTION_MODE_ENABLED)));
  }

  @Test
  public void createOperator_with_invalidProductionModeEnabled_failsAndReturnsError() throws Exception {
    String val = "invalid-production-mode-enabled";
    assertThat(
      execCreateDomain(
        newInputs().productionModeEnabled(val)),
      failsAndPrints(invalidBooleanParamValueError(PARAM_PRODUCTION_MODE_ENABLED, val)));
  }

  // TBD - test productionModeEnabled true & false succeed?
  // or could handle false in CreateDomainGeneratedFilesOptionalFeaturesDisabledTest
  // and true in CreateDomainGeneratedFilesOptionalFeaturesEnabledTest

  @Test
  public void createDomain_with_missingSecretName_failsAndReturnsError() throws Exception {
    assertThat(
      execCreateDomain(newInputs().secretName("")),
      failsAndPrints(paramMissingError(PARAM_SECRET_NAME)));
  }

  @Test
  public void createOperator_with_upperCaseSecretName_failsAndReturnsError() throws Exception {
    String val = "TestSecretName";
    assertThat(
      execCreateDomain(newInputs().secretName(val)),
      failsAndPrints(paramNotLowercaseError(PARAM_SECRET_NAME, val)));
  }

  // TBD - shouldn't this only be required if exposeAdminT3Channel is true?
  @Test
  public void createOperator_with_upperCaseImagePullSecretName_failsAndReturnsError() throws Exception {
    String val = "TestImagePullSecretName";
    assertThat(
      execCreateDomain(newInputs().imagePullSecretName(val)),
      failsAndPrints(paramNotLowercaseError(PARAM_IMAGE_PULL_SECRET_NAME, val)));
  }

  @Test
  public void createDomain_with_missingT3PublicAddress_failsAndReturnsError() throws Exception {
    assertThat(
      execCreateDomain(newInputs().t3PublicAddress("")),
      failsAndPrints(paramMissingError(PARAM_T3_PUBLIC_ADDRESS)));
  }

  // TBD - shouldn't this only be required if exposeAdminT3Channel is true?
  @Test
  public void createDomain_with_missingT3ChannelPort_failsAndReturnsError() throws Exception {
    assertThat(
      execCreateDomain(newInputs().t3ChannelPort("")),
      failsAndPrints(paramMissingError(PARAM_T3_CHANNEL_PORT)));
  }

  @Test
  public void createOperator_with_invalidT3ChannelPort_failsAndReturnsError() throws Exception {
    String val = "invalid-t3-channel-port";
    assertThat(
      execCreateDomain(
        newInputs().t3ChannelPort(val)),
      failsAndPrints(invalidIntegerParamValueError(PARAM_T3_CHANNEL_PORT, val)));
  }

  @Test
  public void createDomain_with_missingExposeAdminT3Channel_failsAndReturnsError() throws Exception {
    assertThat(
      execCreateDomain(newInputs().exposeAdminT3Channel("")),
      failsAndPrints(paramMissingError(PARAM_EXPOSE_ADMIN_T3_CHANNEL)));
  }

  @Test
  public void createOperator_with_invalidExposeAdminT3Channel_failsAndReturnsError() throws Exception {
    String val = "invalid-t3-admin-channel";
    assertThat(
      execCreateDomain(
        newInputs().exposeAdminT3Channel(val)),
      failsAndPrints(invalidBooleanParamValueError(PARAM_EXPOSE_ADMIN_T3_CHANNEL, val)));
  }

  // TBD - shouldn't this only be required if exposeAdminNodePort is true?
  @Test
  public void createDomain_with_missingAdminNodePort_failsAndReturnsError() throws Exception {
    assertThat(
      execCreateDomain(newInputs().adminNodePort("")),
      failsAndPrints(paramMissingError(PARAM_ADMIN_NODE_PORT)));
  }

  @Test
  public void createOperator_with_invalidAdminNodePort_failsAndReturnsError() throws Exception {
    String val = "invalid-admon-node-port";
    assertThat(
      execCreateDomain(
        newInputs().adminNodePort(val)),
      failsAndPrints(invalidIntegerParamValueError(PARAM_ADMIN_NODE_PORT, val)));
  }

  @Test
  public void createDomain_with_missingExposeAdminNodePort_failsAndReturnsError() throws Exception {
    assertThat(
      execCreateDomain(newInputs().exposeAdminNodePort("")),
      failsAndPrints(paramMissingError(PARAM_EXPOSE_ADMIN_NODE_PORT)));
  }

  @Test
  public void createOperator_with_invalidExposeAdminNodePort_failsAndReturnsError() throws Exception {
    String val = "invalid-admin-node-port";
    assertThat(
      execCreateDomain(
        newInputs().exposeAdminNodePort(val)),
      failsAndPrints(invalidBooleanParamValueError(PARAM_EXPOSE_ADMIN_NODE_PORT, val)));
  }

  @Test
  public void createDomain_with_missingNamespace_failsAndReturnsError() throws Exception {
    assertThat(
      execCreateDomain(newInputs().namespace("")),
      failsAndPrints(paramMissingError(PARAM_NAMESPACE)));
  }

  @Test
  public void createOperator_with_upperCaseNamespace_failsAndReturnsError() throws Exception {
    String val = "TestNamespace";
    assertThat(
      execCreateDomain(newInputs().namespace(val)),
      failsAndPrints(paramNotLowercaseError(PARAM_NAMESPACE, val)));
  }

  @Test
  public void createDomain_with_missingLoadBalancer_failsAndReturnsError() throws Exception {
    assertThat(
      execCreateDomain(newInputs().loadBalancer("")),
      failsAndPrints(paramMissingError(PARAM_LOAD_BALANCER)));
  }

  // TBD - load balancer enum legal vals? none / traefik

  @Test
  public void createDomain_with_invalidLoadBalancer_failsAndReturnsError() throws Exception {
    String val = "invalid-load-balancer";
    assertThat(
      execCreateDomain(newInputs().loadBalancer(val)),
      failsAndPrints(invalidEnumParamValueError(PARAM_LOAD_BALANCER, val)));
  }

  // TBD - should this only be required if loadBalancer is not 'none'?
  @Test
  public void createDomain_with_missingLoadBalancerWebPort_failsAndReturnsError() throws Exception {
    assertThat(
      execCreateDomain(newInputs().loadBalancerWebPort("")),
      failsAndPrints(paramMissingError(PARAM_LOAD_BALANCER_WEB_PORT)));
  }

  @Test
  public void createOperator_with_invalidLoadBalancerWebPort_failsAndReturnsError() throws Exception {
    String val = "invalid-load-balancer-web-port";
    assertThat(
      execCreateDomain(
        newInputs().loadBalancerWebPort(val)),
      failsAndPrints(invalidIntegerParamValueError(PARAM_LOAD_BALANCER_WEB_PORT, val)));
  }

  // TBD - should this only be required if loadBalancer is not 'none'?
  @Test
  public void createDomain_with_missingLoadBalancerAdminPort_failsAndReturnsError() throws Exception {
    assertThat(
      execCreateDomain(newInputs().loadBalancerAdminPort("")),
      failsAndPrints(paramMissingError(PARAM_LOAD_BALANCER_ADMIN_PORT)));
  }

  @Test
  public void createOperator_with_invalidLoadBalancerAdminPort_failsAndReturnsError() throws Exception {
    String val = "invalid-load-balancer-admin-port";
    assertThat(
      execCreateDomain(
        newInputs().loadBalancerAdminPort(val)),
      failsAndPrints(invalidIntegerParamValueError(PARAM_LOAD_BALANCER_ADMIN_PORT, val)));
  }

  // TBD - shouldn't we allow empty java options?
  @Test
  public void createDomain_with_missingJavaOptions_failsAndReturnsError() throws Exception {
    assertThat(
      execCreateDomain(newInputs().javaOptions("")),
      failsAndPrints(paramMissingError(PARAM_JAVA_OPTIONS)));
  }

  private String invalidBooleanParamValueError(String param, String val) {
    return errorRegexp(param + ".*true.*" + val);
  }

  private String invalidIntegerParamValueError(String param, String val) {
    return errorRegexp(param + ".*integer.*" + val);
  }

  private String invalidEnumParamValueError(String param, String val) {
    return errorRegexp("Invalid.*" + param + ".*" + val);
  }

  private String paramMissingError(String param) {
    return errorRegexp(param + ".*missing");
  }

  private String paramNotLowercaseError(String param, String val) {
    return errorRegexp(param + ".*lowercase.*" + val);
  }

  private ExecResult execCreateDomain(CreateDomainInputs inputs) throws Exception {
    return ExecCreateDomain.execCreateDomain(userProjects.getPath(), inputs);
  }
}

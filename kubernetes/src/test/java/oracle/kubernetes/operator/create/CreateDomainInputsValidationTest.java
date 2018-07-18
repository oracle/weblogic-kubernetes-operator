// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.create;

import static oracle.kubernetes.operator.utils.CreateDomainInputs.*;
import static oracle.kubernetes.operator.utils.ExecResultMatcher.*;
import static org.hamcrest.MatcherAssert.assertThat;

import oracle.kubernetes.operator.utils.CreateDomainInputs;
import oracle.kubernetes.operator.utils.ExecCreateDomain;
import oracle.kubernetes.operator.utils.ExecResult;
import oracle.kubernetes.operator.utils.GeneratedDomainYamlFiles;
import oracle.kubernetes.operator.utils.UserProjects;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * Tests that create-weblogic-domain.sh properly validates the parameters that a customer can
 * specify in the inputs yaml file.
 */
public class CreateDomainInputsValidationTest {

  private UserProjects userProjects;

  private static final String PARAM_ADMIN_PORT = "adminPort";
  private static final String PARAM_ADMIN_SERVER_NAME = "adminServerName";
  private static final String PARAM_DOMAIN_NAME = "domainName";
  private static final String PARAM_DOMAIN_UID = "domainUID";
  private static final String PARAM_STARTUP_CONTROL = "startupControl";
  private static final String PARAM_CLUSTER_NAME = "clusterName";
  private static final String PARAM_CLUSTER_TYPE = "clusterType";
  private static final String PARAM_CONFIGURED_MANAGED_SERVER_COUNT =
      "configuredManagedServerCount";
  private static final String PARAM_INITIAL_MANAGED_SERVER_REPLICAS =
      "initialManagedServerReplicas";
  private static final String PARAM_MANAGED_SERVER_NAME_BASE = "managedServerNameBase";
  private static final String PARAM_MANAGED_SERVER_PORT = "managedServerPort";
  private static final String PARAM_WEBLOGIC_DOMAIN_STORAGE_RECLAIM_POLICY =
      "weblogicDomainStorageReclaimPolicy";
  private static final String PARAM_WEBLOGIC_DOMAIN_STORAGE_NFS_SERVER =
      "weblogicDomainStorageNFSServer";
  private static final String PARAM_WEBLOGIC_DOMAIN_STORAGE_PATH = "weblogicDomainStoragePath";
  private static final String PARAM_WEBLOGIC_DOMAIN_STORAGE_SIZE = "weblogicDomainStorageSize";
  private static final String PARAM_WEBLOGIC_DOMAIN_STORAGE_TYPE = "weblogicDomainStorageType";
  private static final String PARAM_PRODUCTION_MODE_ENABLED = "productionModeEnabled";
  private static final String PARAM_WEBLOGIC_CREDENTIALS_SECRET_NAME =
      "weblogicCredentialsSecretName";
  private static final String PARAM_WEBLOGIC_IMAGE_PULL_SECRET_NAME = "weblogicImagePullSecretName";
  private static final String PARAM_T3_PUBLIC_ADDRESS = "t3PublicAddress";
  private static final String PARAM_T3_CHANNEL_PORT = "t3ChannelPort";
  private static final String PARAM_EXPOSE_ADMIN_T3_CHANNEL = "exposeAdminT3Channel";
  private static final String PARAM_ADMIN_NODE_PORT = "adminNodePort";
  private static final String PARAM_EXPOSE_ADMIN_NODE_PORT = "exposeAdminNodePort";
  private static final String PARAM_NAMESPACE = "namespace";
  private static final String PARAM_LOAD_BALANCER = "loadBalancer";
  private static final String PARAM_LOAD_BALANCER_WEB_PORT = "loadBalancerWebPort";
  private static final String PARAM_LOAD_BALANCER_DASHBOARD_PORT = "loadBalancerDashboardPort";
  private static final String PARAM_JAVA_OPTIONS = "javaOptions";
  private static final String PARAM_VERSION = "version";

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
  public void createDomainWithUnmodifiedDefaultInputsFile_failsAndReturnsError() throws Exception {
    assertThat(
        execCreateDomain(readDefaultInputsFile()),
        failsAndPrints(
            paramMissingError(PARAM_DOMAIN_UID),
            paramMissingError(PARAM_WEBLOGIC_DOMAIN_STORAGE_PATH)));
  }

  @Test
  public void createDomain_with_missingAdminPort_failsAndReturnsError() throws Exception {
    assertThat(
        execCreateDomain(newInputs().adminPort("")),
        failsAndPrints(paramMissingError(PARAM_ADMIN_PORT)));
  }

  @Test
  public void createDomain_with_invalidAdminPort_failsAndReturnsError() throws Exception {
    String val = "invalid-admin-port";
    assertThat(
        execCreateDomain(newInputs().adminPort(val)),
        failsAndPrints(invalidIntegerParamValueError(PARAM_ADMIN_PORT, val)));
  }

  @Test
  public void createDomain_with_missingAdminServerName_failsAndReturnsError() throws Exception {
    assertThat(
        execCreateDomain(newInputs().adminServerName("")),
        failsAndPrints(paramMissingError(PARAM_ADMIN_SERVER_NAME)));
  }

  @Test
  public void createDomain_with_missingDomainName_failsAndReturnsError() throws Exception {
    assertThat(
        execCreateDomain(newInputs().domainName("")),
        failsAndPrints(paramMissingError(PARAM_DOMAIN_NAME)));
  }

  @Test
  public void createDomain_with_missinDomainUID_failsAndReturnsError() throws Exception {
    assertThat(
        execCreateDomain(newInputs().domainUID("")),
        failsAndPrints(paramMissingError(PARAM_DOMAIN_UID)));
  }

  @Test
  public void createDomain_with_upperCaseDomainUID_failsAndReturnsError() throws Exception {
    String val = "TestDomainUID";
    assertThat(
        execCreateDomain(newInputs().domainUID(val)),
        failsAndPrints(paramNotLowercaseError(PARAM_DOMAIN_UID, val)));
  }

  @Test
  public void createDomain_with_missingStartupControl_failsAndReturnsError() throws Exception {
    assertThat(
        execCreateDomain(newInputs().startupControl("")),
        failsAndPrints(paramMissingError(PARAM_STARTUP_CONTROL)));
  }

  @Test
  public void createDomain_with_startupControlNone_succeeds() throws Exception {
    createDomain_with_validStartupControl_succeeds(STARTUP_CONTROL_NONE);
  }

  @Test
  public void createDomain_with_startupControlAll_succeeds() throws Exception {
    createDomain_with_validStartupControl_succeeds(STARTUP_CONTROL_ALL);
  }

  @Test
  public void createDomain_with_startupControlAdmin_succeeds() throws Exception {
    createDomain_with_validStartupControl_succeeds(STARTUP_CONTROL_ADMIN);
  }

  @Test
  public void createDomain_with_startupControlSpecified_succeeds() throws Exception {
    createDomain_with_validStartupControl_succeeds(STARTUP_CONTROL_SPECIFIED);
  }

  @Test
  public void createDomain_with_startupControlAuto_succeeds() throws Exception {
    createDomain_with_validStartupControl_succeeds(STARTUP_CONTROL_AUTO);
  }

  @Test
  public void createDomain_with_invalidStartupControl_failsAndReturnsError() throws Exception {
    String val = "invalid-startup-control";
    assertThat(
        execCreateDomain(newInputs().startupControl(val)),
        failsAndPrints(invalidEnumParamValueError(PARAM_STARTUP_CONTROL, val)));
  }

  @Test
  public void createDomain_with_missingClusterType_failsAndReturnsError() throws Exception {
    assertThat(
        execCreateDomain(newInputs().clusterType("")),
        failsAndPrints(paramMissingError(PARAM_CLUSTER_TYPE)));
  }

  @Test
  public void createDomain_with_clusterTypeConfigured_succeeds() throws Exception {
    createDomain_with_validClusterType_succeeds(CLUSTER_TYPE_CONFIGURED);
  }

  @Test
  public void createDomain_with_clusterTypeDynamic_succeeds() throws Exception {
    createDomain_with_validClusterType_succeeds(CLUSTER_TYPE_DYNAMIC);
  }

  @Test
  public void createDomain_with_invalidClusterType_failsAndReturnsError() throws Exception {
    String val = "Invalid-cluster-type";
    assertThat(
        execCreateDomain(newInputs().clusterType(val)),
        failsAndPrints(invalidEnumParamValueError(PARAM_CLUSTER_TYPE, val)));
  }

  @Test
  public void createDomain_with_missingClusterName_failsAndReturnsError() throws Exception {
    assertThat(
        execCreateDomain(newInputs().clusterName("")),
        failsAndPrints(paramMissingError(PARAM_CLUSTER_NAME)));
  }

  @Test
  public void createDomain_with_missingConfiguredManagedServerCount_failsAndReturnsError()
      throws Exception {
    assertThat(
        execCreateDomain(newInputs().configuredManagedServerCount("")),
        failsAndPrints(paramMissingError(PARAM_CONFIGURED_MANAGED_SERVER_COUNT)));
  }

  @Test
  public void createDomain_with_invalidConfiguredManagedServerCount_failsAndReturnsError()
      throws Exception {
    String val = "invalid-managed-server-count";
    assertThat(
        execCreateDomain(newInputs().configuredManagedServerCount(val)),
        failsAndPrints(invalidIntegerParamValueError(PARAM_CONFIGURED_MANAGED_SERVER_COUNT, val)));
  }

  @Test
  public void createDomain_with_missingInitialManagedServerReplicas_failsAndReturnsError()
      throws Exception {
    assertThat(
        execCreateDomain(newInputs().initialManagedServerReplicas("")),
        failsAndPrints(paramMissingError(PARAM_INITIAL_MANAGED_SERVER_REPLICAS)));
  }

  @Test
  public void createDomain_with_invalidInitialManagedServerReplicas_failsAndReturnsError()
      throws Exception {
    String val = "invalid-managed-server-start-count";
    assertThat(
        execCreateDomain(newInputs().initialManagedServerReplicas(val)),
        failsAndPrints(invalidIntegerParamValueError(PARAM_INITIAL_MANAGED_SERVER_REPLICAS, val)));
  }

  @Test
  public void createDomain_with_missingManagedServerNameBase_failsAndReturnsError()
      throws Exception {
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
  public void createDomain_with_invalidManagedServerPort_failsAndReturnsError() throws Exception {
    String val = "invalid-managed-server-port";
    assertThat(
        execCreateDomain(newInputs().managedServerPort(val)),
        failsAndPrints(invalidIntegerParamValueError(PARAM_MANAGED_SERVER_PORT, val)));
  }

  @Test
  public void
      createDomain_with_weblogicDomainStorageTypeNfsAndMissingWeblogicDomainStorageNFSServer_failsAndReturnsError()
          throws Exception {
    assertThat(
        execCreateDomain(
            newInputs()
                .weblogicDomainStorageType(STORAGE_TYPE_NFS)
                .weblogicDomainStorageNFSServer("")),
        failsAndPrints(paramMissingError(PARAM_WEBLOGIC_DOMAIN_STORAGE_NFS_SERVER)));
  }

  @Test
  public void createDomain_with_invalidWeblogicDomainStorageType_failsAndReturnsError()
      throws Exception {
    String val = "invalid-storage-type";
    assertThat(
        execCreateDomain(newInputs().weblogicDomainStorageType(val)),
        failsAndPrints(invalidEnumParamValueError(PARAM_WEBLOGIC_DOMAIN_STORAGE_TYPE, val)));
  }

  @Test
  public void
      createDomain_with_weblogicDomainStorageTypeHostPath_and_missingWeblogicDomainStorageNFSServer_succeeds()
          throws Exception {
    GeneratedDomainYamlFiles.generateDomainYamlFiles(
            newInputs()
                .weblogicDomainStorageType(STORAGE_TYPE_HOST_PATH)
                .weblogicDomainStorageNFSServer(""))
        .remove();
  }

  @Test
  public void createDomain_with_missingWeblogicDomainStoragePath_failsAndReturnsError()
      throws Exception {
    assertThat(
        execCreateDomain(newInputs().weblogicDomainStoragePath("")),
        failsAndPrints(paramMissingError(PARAM_WEBLOGIC_DOMAIN_STORAGE_PATH)));
  }

  @Test
  public void createDomain_with_invalidWeblogicDomainStorageReclaimPolicy_failsAndReturnsError()
      throws Exception {
    String val = "invalid-storage-reclaim-policy";
    assertThat(
        execCreateDomain(newInputs().weblogicDomainStorageReclaimPolicy(val)),
        failsAndPrints(
            invalidEnumParamValueError(PARAM_WEBLOGIC_DOMAIN_STORAGE_RECLAIM_POLICY, val)));
  }

  @Test
  public void
      createDomain_with_weblogicDomainStorageReclaimPolicyDeleteAndNonTmpWeblogicDomainStoragePath_failsAndReturnsError()
          throws Exception {
    assertThat(
        execCreateDomain(
            newInputs()
                .weblogicDomainStorageReclaimPolicy(STORAGE_RECLAIM_POLICY_DELETE)
                .weblogicDomainStoragePath("/scratch")),
        failsAndPrints(
            invalidRelatedParamValueError(
                PARAM_WEBLOGIC_DOMAIN_STORAGE_RECLAIM_POLICY,
                STORAGE_RECLAIM_POLICY_DELETE,
                PARAM_WEBLOGIC_DOMAIN_STORAGE_PATH,
                "/scratch")));
  }

  @Test
  public void createDomain_with_weblogicDomainStorageReclaimPolicyRecycle_succeeds()
      throws Exception {
    GeneratedDomainYamlFiles.generateDomainYamlFiles(
            newInputs().weblogicDomainStorageReclaimPolicy(STORAGE_RECLAIM_POLICY_RECYCLE))
        .remove();
  }

  @Test
  public void
      createDomain_with_weblogicDomainStorageReclaimPolicyDelete_and_tmpWeblogicDomainStoragePath_succeeds()
          throws Exception {
    GeneratedDomainYamlFiles.generateDomainYamlFiles(
            newInputs()
                .weblogicDomainStorageReclaimPolicy(STORAGE_RECLAIM_POLICY_DELETE)
                .weblogicDomainStoragePath("/tmp/"))
        .remove();
  }

  @Test
  public void createDomain_with_missingWeblogicDomainStorageSize_failsAndReturnsError()
      throws Exception {
    assertThat(
        execCreateDomain(newInputs().weblogicDomainStorageSize("")),
        failsAndPrints(paramMissingError(PARAM_WEBLOGIC_DOMAIN_STORAGE_SIZE)));
  }

  @Test
  public void createDomain_with_missingWeblogicDomainStorageType_failsAndReturnsError()
      throws Exception {
    assertThat(
        execCreateDomain(newInputs().weblogicDomainStorageType("")),
        failsAndPrints(paramMissingError(PARAM_WEBLOGIC_DOMAIN_STORAGE_TYPE)));
  }

  @Test
  public void createDomain_with_missinProductionModeEnabled_failsAndReturnsError()
      throws Exception {
    assertThat(
        execCreateDomain(newInputs().productionModeEnabled("")),
        failsAndPrints(paramMissingError(PARAM_PRODUCTION_MODE_ENABLED)));
  }

  @Test
  public void createDomain_with_invalidProductionModeEnabled_failsAndReturnsError()
      throws Exception {
    String val = "invalid-production-mode-enabled";
    assertThat(
        execCreateDomain(newInputs().productionModeEnabled(val)),
        failsAndPrints(invalidBooleanParamValueError(PARAM_PRODUCTION_MODE_ENABLED, val)));
  }

  @Test
  public void createDomain_with_missingSecretName_failsAndReturnsError() throws Exception {
    assertThat(
        execCreateDomain(newInputs().weblogicCredentialsSecretName("")),
        failsAndPrints(paramMissingError(PARAM_WEBLOGIC_CREDENTIALS_SECRET_NAME)));
  }

  @Test
  public void createDomain_with_upperCaseSecretName_failsAndReturnsError() throws Exception {
    String val = "TestWeblogicCredentialsSecretName";
    assertThat(
        execCreateDomain(newInputs().weblogicCredentialsSecretName(val)),
        failsAndPrints(paramNotLowercaseError(PARAM_WEBLOGIC_CREDENTIALS_SECRET_NAME, val)));
  }

  // TBD - shouldn't this only be required if exposeAdminT3Channel is true?
  @Test
  public void createDomain_with_upperCaseImagePullSecretName_failsAndReturnsError()
      throws Exception {
    String val = "TestWeblogicImagePullSecretName";
    assertThat(
        execCreateDomain(newInputs().weblogicImagePullSecretName(val)),
        failsAndPrints(paramNotLowercaseError(PARAM_WEBLOGIC_IMAGE_PULL_SECRET_NAME, val)));
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
  public void createDomain_with_invalidT3ChannelPort_failsAndReturnsError() throws Exception {
    String val = "invalid-t3-channel-port";
    assertThat(
        execCreateDomain(newInputs().t3ChannelPort(val)),
        failsAndPrints(invalidIntegerParamValueError(PARAM_T3_CHANNEL_PORT, val)));
  }

  @Test
  public void createDomain_with_missingExposeAdminT3Channel_failsAndReturnsError()
      throws Exception {
    assertThat(
        execCreateDomain(newInputs().exposeAdminT3Channel("")),
        failsAndPrints(paramMissingError(PARAM_EXPOSE_ADMIN_T3_CHANNEL)));
  }

  @Test
  public void createDomain_with_invalidExposeAdminT3Channel_failsAndReturnsError()
      throws Exception {
    String val = "invalid-t3-admin-channel";
    assertThat(
        execCreateDomain(newInputs().exposeAdminT3Channel(val)),
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
  public void createDomain_with_invalidAdminNodePort_failsAndReturnsError() throws Exception {
    String val = "invalid-admon-node-port";
    assertThat(
        execCreateDomain(newInputs().adminNodePort(val)),
        failsAndPrints(invalidIntegerParamValueError(PARAM_ADMIN_NODE_PORT, val)));
  }

  @Test
  public void createDomain_with_missingExposeAdminNodePort_failsAndReturnsError() throws Exception {
    assertThat(
        execCreateDomain(newInputs().exposeAdminNodePort("")),
        failsAndPrints(paramMissingError(PARAM_EXPOSE_ADMIN_NODE_PORT)));
  }

  @Test
  public void createDomain_with_invalidExposeAdminNodePort_failsAndReturnsError() throws Exception {
    String val = "invalid-admin-node-port";
    assertThat(
        execCreateDomain(newInputs().exposeAdminNodePort(val)),
        failsAndPrints(invalidBooleanParamValueError(PARAM_EXPOSE_ADMIN_NODE_PORT, val)));
  }

  @Test
  public void createDomain_with_missingNamespace_failsAndReturnsError() throws Exception {
    assertThat(
        execCreateDomain(newInputs().namespace("")),
        failsAndPrints(paramMissingError(PARAM_NAMESPACE)));
  }

  @Test
  public void createDomain_with_upperCaseNamespace_failsAndReturnsError() throws Exception {
    String val = "TestNamespace";
    assertThat(
        execCreateDomain(newInputs().namespace(val)),
        failsAndPrints(paramNotLowercaseError(PARAM_NAMESPACE, val)));
  }

  @Test
  public void createDomain_with_loadBalanceTypeTraefik_succeeds() throws Exception {
    createDomain_with_validLoadBalancer_succeeds(LOAD_BALANCER_TRAEFIK);
  }

  @Test
  public void createDomain_with_loadBalanceTypeNone_succeeds() throws Exception {
    createDomain_with_validLoadBalancer_succeeds(LOAD_BALANCER_NONE);
  }

  @Test
  public void createDomain_with_loadBalanceTypeApache_succeeds() throws Exception {
    createDomain_with_validLoadBalancer_succeeds(LOAD_BALANCER_APACHE);
  }

  @Test
  public void createDomain_with_loadBalanceTypeVoyager_succeeds() throws Exception {
    createDomain_with_validLoadBalancer_succeeds(LOAD_BALANCER_VOYAGER);
  }

  @Test
  public void createDomain_with_missingLoadBalancer_failsAndReturnsError() throws Exception {
    assertThat(
        execCreateDomain(newInputs().loadBalancer("")),
        failsAndPrints(paramMissingError(PARAM_LOAD_BALANCER)));
  }

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
  public void createDomain_with_invalidLoadBalancerWebPort_failsAndReturnsError() throws Exception {
    String val = "invalid-load-balancer-web-port";
    assertThat(
        execCreateDomain(newInputs().loadBalancerWebPort(val)),
        failsAndPrints(invalidIntegerParamValueError(PARAM_LOAD_BALANCER_WEB_PORT, val)));
  }

  // TBD - should this only be required if loadBalancer is not 'none'?
  @Test
  public void createDomain_with_missingLoadBalancerDashboardPort_failsAndReturnsError()
      throws Exception {
    assertThat(
        execCreateDomain(newInputs().loadBalancerDashboardPort("")),
        failsAndPrints(paramMissingError(PARAM_LOAD_BALANCER_DASHBOARD_PORT)));
  }

  @Test
  public void createDomain_with_invalidLoadBalancerDashboardPort_failsAndReturnsError()
      throws Exception {
    String val = "invalid-load-balancer-admin-port";
    assertThat(
        execCreateDomain(newInputs().loadBalancerDashboardPort(val)),
        failsAndPrints(invalidIntegerParamValueError(PARAM_LOAD_BALANCER_DASHBOARD_PORT, val)));
  }

  // TBD - shouldn't we allow empty java options?
  @Test
  public void createDomain_with_missingJavaOptions_failsAndReturnsError() throws Exception {
    assertThat(
        execCreateDomain(newInputs().javaOptions("")),
        failsAndPrints(paramMissingError(PARAM_JAVA_OPTIONS)));
  }

  @Test
  public void createDomain_with_missingVersion_failsAndReturnsError() throws Exception {
    assertThat(
        execCreateDomain(newInputs().version("")),
        failsAndPrints(paramMissingError(PARAM_VERSION)));
  }

  @Test
  public void createDomainwith_invalidVersion_failsAndReturnsError() throws Exception {
    String val = "no-such-version";
    assertThat(
        execCreateDomain(newInputs().version(val)),
        failsAndPrints(invalidEnumParamValueError(PARAM_VERSION, val)));
  }

  private void createDomain_with_validStartupControl_succeeds(String startupControl)
      throws Exception {
    createDomain_with_validInputs_succeeds(newInputs().startupControl(startupControl));
  }

  private void createDomain_with_validClusterType_succeeds(String clusterType) throws Exception {
    createDomain_with_validInputs_succeeds(newInputs().clusterType(clusterType));
  }

  private void createDomain_with_validLoadBalancer_succeeds(String loadBalancerType)
      throws Exception {
    createDomain_with_validInputs_succeeds(newInputs().loadBalancer(loadBalancerType));
  }

  private void createDomain_with_validInputs_succeeds(CreateDomainInputs inputs) throws Exception {
    // throws an error if the inputs are not valid, succeeds otherwise:
    GeneratedDomainYamlFiles.generateDomainYamlFiles(inputs).remove();
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

  private String invalidRelatedParamValueError(
      String param, String val, String param2, String val2) {
    return errorRegexp("Invalid.*" + param + ".*" + val + " with " + param2 + ".*" + val2);
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

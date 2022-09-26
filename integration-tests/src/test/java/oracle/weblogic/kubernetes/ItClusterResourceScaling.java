// Copyright (c) 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes;

import java.util.Base64;
import java.util.List;

import io.kubernetes.client.openapi.models.V1EnvVar;
import io.kubernetes.client.openapi.models.V1LocalObjectReference;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1Secret;
import io.kubernetes.client.openapi.models.V1ServiceAccount;
import io.kubernetes.client.openapi.models.V1ServiceAccountList;
import oracle.weblogic.domain.AdminServer;
import oracle.weblogic.domain.ClusterResource;
import oracle.weblogic.domain.ClusterSpec;
import oracle.weblogic.domain.Configuration;
import oracle.weblogic.domain.DomainResource;
import oracle.weblogic.domain.DomainSpec;
import oracle.weblogic.domain.Model;
import oracle.weblogic.domain.ServerPod;
import oracle.weblogic.kubernetes.actions.impl.Secret;
import oracle.weblogic.kubernetes.actions.impl.primitive.Command;
import oracle.weblogic.kubernetes.actions.impl.primitive.CommandParams;
import oracle.weblogic.kubernetes.actions.impl.primitive.HelmParams;
import oracle.weblogic.kubernetes.actions.impl.primitive.Kubernetes;
import oracle.weblogic.kubernetes.annotations.IntegrationTest;
import oracle.weblogic.kubernetes.annotations.Namespaces;
import oracle.weblogic.kubernetes.logging.LoggingFacade;
import oracle.weblogic.kubernetes.utils.ExecResult;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static oracle.weblogic.kubernetes.TestConstants.ADMIN_PASSWORD_DEFAULT;
import static oracle.weblogic.kubernetes.TestConstants.ADMIN_SERVER_NAME_BASE;
import static oracle.weblogic.kubernetes.TestConstants.ADMIN_USERNAME_DEFAULT;
import static oracle.weblogic.kubernetes.TestConstants.DOMAIN_API_VERSION;
import static oracle.weblogic.kubernetes.TestConstants.IMAGE_PULL_POLICY;
import static oracle.weblogic.kubernetes.TestConstants.MANAGED_SERVER_NAME_BASE;
import static oracle.weblogic.kubernetes.TestConstants.MII_BASIC_IMAGE_NAME;
import static oracle.weblogic.kubernetes.TestConstants.MII_BASIC_IMAGE_TAG;
import static oracle.weblogic.kubernetes.TestConstants.OPERATOR_CHART_DIR;
import static oracle.weblogic.kubernetes.TestConstants.OPERATOR_RELEASE_NAME;
import static oracle.weblogic.kubernetes.TestConstants.TEST_IMAGES_REPO_SECRET_NAME;
import static oracle.weblogic.kubernetes.TestConstants.WLS_DOMAIN_TYPE;
import static oracle.weblogic.kubernetes.actions.TestActions.deleteDomainCustomResource;
import static oracle.weblogic.kubernetes.actions.TestActions.deleteSecret;
import static oracle.weblogic.kubernetes.actions.TestActions.deleteServiceAccount;
import static oracle.weblogic.kubernetes.actions.TestActions.getServiceNodePort;
import static oracle.weblogic.kubernetes.actions.TestActions.listSecrets;
import static oracle.weblogic.kubernetes.actions.TestActions.uninstallOperator;
import static oracle.weblogic.kubernetes.utils.ClusterUtils.createClusterAndVerify;
import static oracle.weblogic.kubernetes.utils.ClusterUtils.createClusterResource;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.checkServiceDoesNotExist;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.checkServiceExists;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.getHostAndPort;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.getNextFreePort;
import static oracle.weblogic.kubernetes.utils.DomainUtils.createDomainAndVerify;
import static oracle.weblogic.kubernetes.utils.ExecCommand.exec;
import static oracle.weblogic.kubernetes.utils.ImageUtils.createTestRepoSecret;
import static oracle.weblogic.kubernetes.utils.ImageUtils.dockerLoginAndPushImageToRegistry;
import static oracle.weblogic.kubernetes.utils.OKDUtils.createRouteForOKD;
import static oracle.weblogic.kubernetes.utils.OKDUtils.getRouteHost;
import static oracle.weblogic.kubernetes.utils.OperatorUtils.installAndVerifyOperator;
import static oracle.weblogic.kubernetes.utils.PodUtils.checkPodExists;
import static oracle.weblogic.kubernetes.utils.PodUtils.checkPodReady;
import static oracle.weblogic.kubernetes.utils.PodUtils.getExternalServicePodName;
import static oracle.weblogic.kubernetes.utils.PodUtils.setPodAntiAffinity;
import static oracle.weblogic.kubernetes.utils.SecretUtils.createSecretWithUsernamePassword;
import static oracle.weblogic.kubernetes.utils.ThreadSafeLogger.getLogger;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Simple JUnit test file used for testing operator usability.
 * Use Helm chart to install operator(s)
 */
@DisplayName("Test scaling usability ")
@IntegrationTest
@Tag("olcne")
@Tag("oke-parallel")
@Tag("kind-parallel")
@Tag("okd-wls-mrg")
class ItClusterResourceScaling {

  private static final String LIST_STRATEGY = "List";

  private static String opNamespace = null;
  private static String op2Namespace = null;
  private static String domainNamespace = null;

  // domain constants
  private final String domainUid = "usabdomain";

  private final String clusterName = "cluster-1";
  private final int replicaCount = 2;
  private final String adminServerPrefix = "-" + ADMIN_SERVER_NAME_BASE;
  private final String managedServerPrefix = "-" + MANAGED_SERVER_NAME_BASE;
  private boolean isDomainRunning = false;
  private String adminSvcExtRouteHost = null;

  private static LoggingFacade logger = null;

  /**
   * Get namespaces for operator, domain.
   *
   * @param namespaces list of namespaces created by the IntegrationTestWatcher by the
   *                   JUnit engine parameter resolution mechanism
   */
  @BeforeAll
  public static void initAll(@Namespaces(2) List<String> namespaces) {
    logger = getLogger();
    // get a unique operator namespace
    logger.info("Getting a unique namespace for operator");
    assertNotNull(namespaces.get(0), "Namespace list is null");
    opNamespace = namespaces.get(0);

    // get a unique domain namespace
    logger.info("Getting a unique namespace for WebLogic domain 2");
    assertNotNull(namespaces.get(1), "Namespace list is null");
    domainNamespace = namespaces.get(1);
    createTestRepoSecret(domainNamespace);
  }

  @AfterAll
  public void tearDownAll() {

    logger.info("Delete domain custom resource in namespace {0}", domainNamespace);
    deleteDomainCustomResource(domainUid, domainNamespace);
    logger.info("Deleted Domain Custom Resource {0} from namespace {1}", domainUid, domainNamespace);
  }



  /**
   * Install the Operator successfully.
   * Create domain and verify the domain is started
   * Upgrade the operator helm chart domainNamespaces to include namespace for domain3
   * Verify both domains are managed by the operator by making a REST API call
   * Call helm upgrade to remove the domain3 from operator domainNamespaces
   * Verify it can't be managed by operator anymore.
   * Test fails when an operator fails to manage the domains as expected
   */
  @Test
  @DisplayName("Create domain, managed by operator and domain1, perfom scaling operation via REST  "
      + "verify scaling operation generate expected code in case of negative scenarios")
  void testScaleClusterViaRestApi() {
    HelmParams opHelmParams = null;
    HelmParams op1HelmParams = new HelmParams().releaseName(OPERATOR_RELEASE_NAME)
        .namespace(opNamespace)
        .chartDir(OPERATOR_CHART_DIR);
    String opServiceAccount = opNamespace + "-sa";
    try {
      // install operator
      opHelmParams = installAndVerifyOperator(opNamespace, opServiceAccount, true,
          0, op1HelmParams, domainNamespace).getHelmParams();
      assertNotNull(opHelmParams, "Can't install operator");

      int externalRestHttpsPort = getServiceNodePort(opNamespace, "external-weblogic-operator-svc");
      assertNotEquals(-1, externalRestHttpsPort,
          "Could not get the Operator external service node port");
      logger.info("externalRestHttpsPort {0}", externalRestHttpsPort);
      if (!isDomainRunning) {
        logger.info("Installing and verifying domain");
        assertTrue(createVerifyDomain(domainNamespace, domainUid),
            "can't start or verify domain in namespace " + domainNamespace);
        isDomainRunning = true;
      }

      // scale domain
      int replicaCountdomain = 2;
      String secretToken = getServerToken(opServiceAccount);
      // decode the secret encoded token
      String decodedToken = new String(Base64.getDecoder().decode(secretToken));
      assertTrue(scaleClusterWithRestApi(domainUid, clusterName,replicaCountdomain + 1,
              externalRestHttpsPort,op2Namespace, decodedToken,0, "",
              true, true),
          "domain in namespace " + domainNamespace + " scaling operation failed");

      String managedServerPodName2 = domainUid + managedServerPrefix + (replicaCountdomain + 1);
      logger.info("Checking that the managed server pod {0} exists in namespace {1}",
          managedServerPodName2, domainNamespace);
      assertDoesNotThrow(() -> checkPodExists(managedServerPodName2, domainUid, domainNamespace),
          "operator failed to manage domain, scaling was not succeeded");
      ++replicaCountdomain;
      logger.info("domain scaled to " + replicaCountdomain + " servers");

      // decode the secret encoded token
      String decodedTokenBad = new String(Base64.getDecoder().decode(secretToken)) + "badbad";
      assertTrue(scaleClusterWithRestApi(domainUid, clusterName,replicaCountdomain + 2,
              externalRestHttpsPort,op2Namespace, decodedTokenBad, 0, "401 Unauthorized", true,
              true),
          "domain in namespace " + domainNamespace + " scaling operation succeeded");
      assertTrue(scaleClusterWithRestApi(domainUid + "invalid", clusterName,replicaCountdomain + 2,
              externalRestHttpsPort,op2Namespace, decodedToken, 0, "404 Not Found", true,
              true),
          "domain in namespace " + domainNamespace + " scaling operation succeeded");
      assertTrue(scaleClusterWithRestApi(domainUid, clusterName + "invalid",replicaCountdomain + 2,
              externalRestHttpsPort,op2Namespace, decodedToken, 0, "404 Not Found", true,
              true),
          "domain in namespace " + domainNamespace + " scaling operation succeeded");

      assertTrue(scaleClusterWithRestApi(domainUid, clusterName,replicaCountdomain + 2,
              externalRestHttpsPort,op2Namespace, decodedToken, 0, "400 Bad Request", false, 
              true),
          "domain in namespace " + domainNamespace + " scaling operation succeeded");
      assertTrue(scaleClusterWithRestApi(domainUid, clusterName,replicaCountdomain + 2,
              externalRestHttpsPort,op2Namespace, decodedToken, 0, "401 Unauthorized", true,
              false),
          "domain in namespace " + domainNamespace + " scaling operation succeeded");
    } finally {
      uninstallOperator(opHelmParams);
      deleteSecret(TEST_IMAGES_REPO_SECRET_NAME, opNamespace);
      cleanUpSA(opNamespace);
      if (!isDomainRunning) {
        cleanUpDomainSecrets(domainNamespace);
      }
    }
  }

  /**
   * Scale the cluster of the domain in the specified namespace with REST API.
   *
   * @param domainUid domainUid of the domain to be scaled
   * @param clusterName name of the WebLogic cluster to be scaled in the domain
   * @param numOfServers number of servers to be scaled to
   * @param externalRestHttpsPort node port allocated for the external operator REST HTTPS interface
   * @param opNamespace namespace of WebLogic operator
   * @return true if REST call succeeds, false otherwise
   */
  public static boolean scaleClusterWithRestApi(String domainUid,
                                                String clusterName,
                                                int numOfServers,
                                                int externalRestHttpsPort,
                                                String opNamespace,
                                                String decodedToken,
                                                int expectedExecCode,
                                                String expectedMsg,
                                                boolean hasHeader,
                                                boolean hasAuthHeader) {
    LoggingFacade logger = getLogger();

    String opExternalSvc = getRouteHost(opNamespace, "external-weblogic-operator-svc");


    // build the curl command to scale the cluster
    StringBuffer command = new StringBuffer()
        .append("curl --noproxy '*' -v -k ");
    if (hasAuthHeader) {
      command.append("-H \"Authorization:Bearer ")
          .append(decodedToken)
          .append("\" ");
    }
    command.append("-H Accept:application/json ")
        .append("-H Content-Type:application/json ");
    if (hasHeader) {
      command.append("-H X-Requested-By:MyClient ");
    }
    command.append("-d '{\"spec\": {\"replicas\": ")
    .append(numOfServers)
    .append("}}' ")
    .append("-X POST https://")
    .append(getHostAndPort(opExternalSvc, externalRestHttpsPort))
    .append("/operator/latest/domains/")
    .append(domainUid)
    .append("/clusters/")
    .append(clusterName)
    .append("/scale").toString();

    CommandParams params = Command
        .defaultCommandParams()
        .command(command.toString())
        .saveResults(true)
        .redirect(true);

    logger.info("Calling curl to scale the cluster");
    ExecResult result = Command.withParams(params).executeAndReturnResult();
    logger.info("Return values {0}, errors {1}", result.stdout(), result.stderr());
    assertEquals(expectedExecCode, result.exitValue());
    assertTrue(result.stdout().contains(expectedMsg) || result.stderr().contains(expectedMsg));

    return true;
  }


  private String getServerToken(String opServiceAccount) {
    logger.info("Getting the secret of service account {0} in namespace {1}", opServiceAccount, opNamespace);
    String secretName = Secret.getSecretOfServiceAccount(opNamespace, opServiceAccount);
    if (secretName.isEmpty()) {
      logger.info("Did not find secret of service account {0} in namespace {1}", opServiceAccount, opNamespace);
      return null;
    }
    logger.info("Got secret {0} of service account {1} in namespace {2}",
        secretName, opServiceAccount, opNamespace);

    logger.info("Getting service account token stored in secret {0} to authenticate as service account {1}"
        + " in namespace {2}", secretName, opServiceAccount, opNamespace);
    String secretToken = Secret.getSecretEncodedToken(opNamespace, secretName);
    if (secretToken.isEmpty()) {
      logger.info("Did not get encoded token for secret {0} associated with service account {1} in namespace {2}",
          secretName, opServiceAccount, opNamespace);
      return null;
    }
    logger.info("Got encoded token for secret {0} associated with service account {1} in namespace {2}: {3}",
        secretName, opServiceAccount, opNamespace, secretToken);
    return secretToken;
  }

  private boolean createVerifyDomain(String domainNamespace, String domainUid) {

    // create and verify the domain
    logger.info("Creating and verifying model in image domain");
    createAndVerifyMiiDomain(domainNamespace, domainUid);
    return true;
  }

  /**
   * Create a model in image domain and verify the domain pods are ready.
   */
  private void createAndVerifyMiiDomain(String domainNamespace, String domainUid) {

    // get the pre-built image created by IntegrationTestWatcher
    String miiImage = MII_BASIC_IMAGE_NAME + ":" + MII_BASIC_IMAGE_TAG;

    // docker login and push image to docker registry if necessary
    dockerLoginAndPushImageToRegistry(miiImage);

    // create docker registry secret to pull the image from registry
    // this secret is used only for non-kind cluster
    logger.info("Creating docker registry secret in namespace {0}", domainNamespace);
    createTestRepoSecret(domainNamespace);

    // create secret for admin credentials
    logger.info("Creating secret for admin credentials");
    String adminSecretName = "weblogic-credentials-" + domainUid;
    createSecretWithUsernamePassword(adminSecretName, domainNamespace, ADMIN_USERNAME_DEFAULT, ADMIN_PASSWORD_DEFAULT);

    // create encryption secret
    logger.info("Creating encryption secret");
    String encryptionSecretName = "encryptionsecret" + domainUid;
    createSecretWithUsernamePassword(encryptionSecretName, domainNamespace, "weblogicenc", "weblogicenc");

    // create the domain CR
    DomainResource domain = new DomainResource()
        .apiVersion(DOMAIN_API_VERSION)
        .kind("Domain")
        .metadata(new V1ObjectMeta()
            .name(domainUid)
            .namespace(domainNamespace))
        .spec(new DomainSpec()
            .domainUid(domainUid)
            .domainHomeSourceType("FromModel")
            .image(miiImage)
            .imagePullPolicy(IMAGE_PULL_POLICY)
            .addImagePullSecretsItem(new V1LocalObjectReference()
                .name(TEST_IMAGES_REPO_SECRET_NAME))
            .webLogicCredentialsSecret(new V1LocalObjectReference()
                .name(adminSecretName))
            .includeServerOutInPodLog(true)
            .serverStartPolicy("IfNeeded")
            .serverPod(new ServerPod()
                .addEnvItem(new V1EnvVar()
                    .name("JAVA_OPTIONS")
                    .value("-Dweblogic.StdoutDebugEnabled=false"))
                .addEnvItem(new V1EnvVar()
                    .name("USER_MEM_ARGS")
                    .value("-Djava.security.egd=file:/dev/./urandom ")))
            .adminServer(new AdminServer()
                .adminService(new oracle.weblogic.domain.AdminService()
                    .addChannelsItem(new oracle.weblogic.domain.Channel()
                        .channelName("default")
                        .nodePort(getNextFreePort()))))
            .configuration(new Configuration()
                .introspectorJobActiveDeadlineSeconds(280L)
                .model(new Model()
                    .domainType(WLS_DOMAIN_TYPE)
                    .runtimeEncryptionSecret(encryptionSecretName))));

    // add cluster to the domain
    String clusterResName = domainUid + "-" + clusterName;
    ClusterResource cluster = createClusterResource(clusterResName, domainNamespace,
        new ClusterSpec().withClusterName(clusterName).replicas(replicaCount));
    getLogger().info("Creating cluster {0} in namespace {1}", clusterResName, domainNamespace);
    createClusterAndVerify(cluster);

    // set cluster references
    domain.getSpec().withCluster(new V1LocalObjectReference().name(clusterResName));

    setPodAntiAffinity(domain);

    // create model in image domain
    logger.info("Creating model in image domain {0} in namespace {1} using docker image {2}",
        domainUid, domainNamespace, miiImage);
    createDomainAndVerify(domain, domainNamespace);
    String adminServerPodName = domainUid + adminServerPrefix;
    // check that admin server pod exists in the domain namespace
    logger.info("Checking that admin server pod {0} exists in namespace {1}",
        adminServerPodName, domainNamespace);
    checkPodExists(adminServerPodName, domainUid, domainNamespace);

    // check that admin server pod is ready
    logger.info("Checking that admin server pod {0} is ready in namespace {1}",
        adminServerPodName, domainNamespace);
    checkPodReady(adminServerPodName, domainUid, domainNamespace);

    // check that admin service exists in the domain namespace
    logger.info("Checking that admin service {0} exists in namespace {1}",
        adminServerPodName, domainNamespace);
    checkServiceExists(adminServerPodName, domainNamespace);
    adminSvcExtRouteHost = createRouteForOKD(adminServerPodName + "-ext", domainNamespace);

    // check for managed server pods existence in the domain namespace
    for (int i = 1; i <= replicaCount; i++) {
      String managedServerPodName = domainUid + managedServerPrefix + i;

      // check that the managed server pod exists
      logger.info("Checking that managed server pod {0} exists in namespace {1}",
          managedServerPodName, domainNamespace);
      checkPodExists(managedServerPodName, domainUid, domainNamespace);

      // check that the managed server pod is ready
      logger.info("Checking that managed server pod {0} is ready in namespace {1}",
          managedServerPodName, domainNamespace);
      checkPodReady(managedServerPodName, domainUid, domainNamespace);

      // check that the managed server service exists in the domain namespace
      logger.info("Checking that managed server service {0} exists in namespace {1}",
          managedServerPodName, domainNamespace);
      checkServiceExists(managedServerPodName, domainNamespace);
    }
    //check the access to managed server mbean via rest api
    checkManagedServerConfiguration(domainNamespace, domainUid);
  }

  private void cleanUpSA(String namespace) {
    V1ServiceAccountList sas = Kubernetes.listServiceAccounts(namespace);
    if (sas != null) {
      for (V1ServiceAccount sa : sas.getItems()) {
        String saName = sa.getMetadata().getName();
        deleteServiceAccount(saName, namespace);
        checkServiceDoesNotExist(saName, namespace);
      }
    }
  }

  /*
   * Verify the server MBEAN configuration through rest API.
   * @param managedServer name of the managed server
   * @returns true if MBEAN is found otherwise false
   **/
  private boolean checkManagedServerConfiguration(String domainNamespace, String domainUid) {
    ExecResult result;
    String adminServerPodName = domainUid + adminServerPrefix;
    String managedServer = "managed-server1";
    int adminServiceNodePort
        = getServiceNodePort(domainNamespace, getExternalServicePodName(adminServerPodName), "default");
    String hostAndPort = getHostAndPort(adminSvcExtRouteHost, adminServiceNodePort);
    StringBuilder checkCluster = new StringBuilder("status=$(curl --user ")
        .append(ADMIN_USERNAME_DEFAULT)
        .append(":")
        .append(ADMIN_PASSWORD_DEFAULT)
        .append(" ")
        .append("http://")
        .append(hostAndPort)
        .append("/management/tenant-monitoring/servers/")
        .append(managedServer)
        .append(" --silent --show-error ")
        .append(" -o /dev/null")
        .append(" -w %{http_code});")
        .append("echo ${status}");
    logger.info("checkManagedServerConfiguration: curl command {0}", new String(checkCluster));
    try {
      result = exec(new String(checkCluster), true);
    } catch (Exception ex) {
      logger.info("Exception in checkManagedServerConfiguration() {0}", ex);
      return false;
    }
    logger.info("checkManagedServerConfiguration: curl command returned {0}", result.toString());
    return result.stdout().equals("200");
  }

  private void cleanUpDomainSecrets(String domainNamespace) {
    //cleanup created artifacts for failed domain creation
    for (V1Secret secret : listSecrets(domainNamespace).getItems()) {
      if (secret.getMetadata() != null) {
        String name = secret.getMetadata().getName();
        Kubernetes.deleteSecret(name, domainNamespace);
      }
    }
  }
}

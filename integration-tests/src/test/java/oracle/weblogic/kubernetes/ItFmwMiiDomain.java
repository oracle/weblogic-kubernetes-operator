// Copyright (c) 2020, 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;

import io.kubernetes.client.custom.V1Patch;
import io.kubernetes.client.openapi.models.V1EnvVar;
import io.kubernetes.client.openapi.models.V1LocalObjectReference;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1SecretReference;
import oracle.weblogic.domain.AdminServer;
import oracle.weblogic.domain.AdminService;
import oracle.weblogic.domain.Channel;
import oracle.weblogic.domain.Cluster;
import oracle.weblogic.domain.Configuration;
import oracle.weblogic.domain.Domain;
import oracle.weblogic.domain.DomainSpec;
import oracle.weblogic.domain.Model;
import oracle.weblogic.domain.Opss;
import oracle.weblogic.domain.ServerPod;
import oracle.weblogic.kubernetes.actions.impl.primitive.Command;
import oracle.weblogic.kubernetes.annotations.IntegrationTest;
import oracle.weblogic.kubernetes.annotations.Namespaces;
import oracle.weblogic.kubernetes.logging.LoggingFacade;
import org.awaitility.core.ConditionFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import static java.util.concurrent.TimeUnit.MINUTES;
import static java.util.concurrent.TimeUnit.SECONDS;
import static oracle.weblogic.kubernetes.TestConstants.DB_IMAGE_TO_USE_IN_SPEC;
import static oracle.weblogic.kubernetes.TestConstants.DOMAIN_API_VERSION;
import static oracle.weblogic.kubernetes.TestConstants.FMWINFRA_IMAGE_NAME;
import static oracle.weblogic.kubernetes.TestConstants.FMWINFRA_IMAGE_TAG;
import static oracle.weblogic.kubernetes.TestConstants.FMWINFRA_IMAGE_TO_USE_IN_SPEC;
import static oracle.weblogic.kubernetes.TestConstants.K8S_NODEPORT_HOST;
import static oracle.weblogic.kubernetes.TestConstants.MII_BASIC_APP_NAME;
import static oracle.weblogic.kubernetes.TestConstants.OCIR_SECRET_NAME;
import static oracle.weblogic.kubernetes.actions.ActionConstants.MODEL_DIR;
import static oracle.weblogic.kubernetes.actions.ActionConstants.RESOURCE_DIR;
import static oracle.weblogic.kubernetes.actions.TestActions.getServiceNodePort;
import static oracle.weblogic.kubernetes.actions.TestActions.patchDomainCustomResource;
import static oracle.weblogic.kubernetes.actions.impl.primitive.Command.defaultCommandParams;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.checkPodDeleted;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.checkPodReadyAndServiceExists;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.createDomainAndVerify;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.createMiiImageAndVerify;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.createOcirRepoSecret;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.createOpsswalletpasswordSecret;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.createRcuAccessSecret;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.createSecretWithUsernamePassword;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.dockerLoginAndPushImageToRegistry;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.getExternalServicePodName;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.installAndVerifyOperator;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.patchServerStartPolicy;
import static oracle.weblogic.kubernetes.utils.DbUtils.setupDBandRCUschema;
import static oracle.weblogic.kubernetes.utils.TestUtils.callWebAppAndWaitTillReady;
import static oracle.weblogic.kubernetes.utils.ThreadSafeLogger.getLogger;
import static org.awaitility.Awaitility.with;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("Test to a create JRF model in image domain and start the domain")
@IntegrationTest
public class ItJrfMiiDomain {

  private static String dbNamespace = null;
  private static String opNamespace = null;
  private static String jrfDomainNamespace = null;
  private static String jrfMiiImage = null;

  private static final String RCUSCHEMAPREFIX = "jrfdomainmii";
  private static final String ORACLEDBURLPREFIX = "oracledb.";
  private static final String ORACLEDBSUFFIX = ".svc.cluster.local:1521/devpdb.k8s";
  private static final String RCUSYSUSERNAME = "sys";
  private static final String RCUSYSPASSWORD = "Oradoc_db1";
  private static final String RCUSCHEMAUSERNAME = "myrcuuser";
  private static final String RCUSCHEMAPASSWORD = "Oradoc_db1";
  private static final String modelFile = "model-singleclusterdomain-sampleapp-jrf.yaml";

  private static String dbUrl = null;
  private static LoggingFacade logger = null;

  private String domainUid = "jrfdomain-mii";
  private String adminServerPodName = domainUid + "-admin-server";
  private String managedServerPrefix = domainUid + "-managed-server";
  private int replicaCount = 2;
  private String adminSecretName = domainUid + "-weblogic-credentials";
  private String encryptionSecretName = domainUid + "-encryptionsecret";
  private String rcuaccessSecretName = domainUid + "-rcu-access";
  private String opsswalletpassSecretName = domainUid + "-opss-wallet-password-secret";
  private String opsswalletfileSecretName = domainUid + "opss-wallet-file-secret";

  // create standard, reusable retry/backoff policy
  private static final ConditionFactory withStandardRetryPolicy
      = with().pollDelay(2, SECONDS)
      .and().with().pollInterval(10, SECONDS)
      .atMost(5, MINUTES).await();


  /**
   * Start DB service and create RCU schema.
   * Assigns unique namespaces for operator and domains.
   * Pull FMW image and Oracle DB image if running tests in Kind cluster.
   * Installs operator.
   *
   * @param namespaces injected by JUnit
   */
  @BeforeAll
  public static void initAll(@Namespaces(3) List<String> namespaces) {

    logger = getLogger();
    logger.info("Assign a unique namespace for DB and RCU");
    assertNotNull(namespaces.get(0), "Namespace is null");
    dbNamespace = namespaces.get(0);
    dbUrl = ORACLEDBURLPREFIX + dbNamespace + ORACLEDBSUFFIX;

    logger.info("Assign a unique namespace for operator");
    assertNotNull(namespaces.get(1), "Namespace is null");
    opNamespace = namespaces.get(1);

    logger.info("Assign a unique namespace for JRF domain");
    assertNotNull(namespaces.get(2), "Namespace is null");
    jrfDomainNamespace = namespaces.get(2);

    logger.info("Start DB and create RCU schema for namespace: {0}, RCU prefix: {1}, "
        + "dbUrl: {2}, dbImage: {3},  fmwImage: {4} ", dbNamespace, RCUSCHEMAPREFIX, dbUrl,
        DB_IMAGE_TO_USE_IN_SPEC, FMWINFRA_IMAGE_TO_USE_IN_SPEC);
    assertDoesNotThrow(() -> setupDBandRCUschema(DB_IMAGE_TO_USE_IN_SPEC, FMWINFRA_IMAGE_TO_USE_IN_SPEC,
        RCUSCHEMAPREFIX, dbNamespace, 0, dbUrl),
        String.format("Failed to create RCU schema for prefix %s in the namespace %s with "
        + "dbUrl %s", RCUSCHEMAPREFIX, dbNamespace, dbUrl));

    // install operator and verify its running in ready state
    installAndVerifyOperator(opNamespace, jrfDomainNamespace);

    logger.info("For ItJrfMiiDomain using DB image: {0}, FMW image {1}",
        DB_IMAGE_TO_USE_IN_SPEC, FMWINFRA_IMAGE_TO_USE_IN_SPEC);

  }

  /**
   * Create a basic JRF model in image domain.
   * Verify Pod is ready and service exists for both admin server and managed servers.
   * Verify EM console is accessible.
   */
  @Order(1)
  @Test
  @DisplayName("Create JRF Domain model in image")
  public void testJrfModelInImage() {
    // Create the repo secret to pull the image
    // this secret is used only for non-kind cluster
    createOcirRepoSecret(jrfDomainNamespace);

    // create secret for admin credentials
    logger.info("Create secret for admin credentials");
    assertDoesNotThrow(() -> createSecretWithUsernamePassword(
        adminSecretName,
        jrfDomainNamespace,
        "weblogic",
        "welcome1"),
        String.format("createSecret failed for %s", adminSecretName));

    // create encryption secret
    logger.info("Create encryption secret");
    assertDoesNotThrow(() -> createSecretWithUsernamePassword(
        encryptionSecretName,
        jrfDomainNamespace,
        "weblogicenc",
        "weblogicenc"),
        String.format("createSecret failed for %s", encryptionSecretName));

    // create RCU access secret
    logger.info("Creating RCU access secret: {0}, with prefix: {1}, dbUrl: {2}, schemapassword: {3})",
        rcuaccessSecretName, RCUSCHEMAPREFIX, RCUSCHEMAPASSWORD, dbUrl);
    assertDoesNotThrow(() -> createRcuAccessSecret(
        rcuaccessSecretName,
        jrfDomainNamespace,
        RCUSCHEMAPREFIX,
        RCUSCHEMAPASSWORD,
        dbUrl),
        String.format("createSecret failed for %s", rcuaccessSecretName));

    logger.info("Create OPSS wallet password secret");
    assertDoesNotThrow(() -> createOpsswalletpasswordSecret(
        opsswalletpassSecretName,
        jrfDomainNamespace,
        "welcome1"),
        String.format("createSecret failed for %s", opsswalletpassSecretName));

    logger.info("Create an image with jrf model file");
    final List<String> modelList = Collections.singletonList(MODEL_DIR + "/" + modelFile);
    jrfMiiImage = createMiiImageAndVerify(
        "jrf-mii-image",
        modelList,
        Collections.singletonList(MII_BASIC_APP_NAME),
        FMWINFRA_IMAGE_NAME,
        FMWINFRA_IMAGE_TAG,
        "JRF",
        false);

    // push the image to a registry to make it accessible in multi-node cluster
    dockerLoginAndPushImageToRegistry(jrfMiiImage);

    // create the domain object
    Domain domain = createDomainResource(domainUid,
        jrfDomainNamespace,
        adminSecretName,
        OCIR_SECRET_NAME,
        encryptionSecretName,
        rcuaccessSecretName,
        opsswalletpassSecretName,
        replicaCount,
        jrfMiiImage);

    createDomainAndVerify(domain, jrfDomainNamespace);
    verifyDomainReady();
  }

  /**
   * Save the OPSS key wallet from a running JRF domain's introspector configmap to a file
   * Restore the OPSS key wallet file to a Kubernetes secret.
   * Shutdown the domain.
   * Using the same RCU schema to restart the same JRF domain with restored OPSS key wallet file secret.
   * Verify Pod is ready and service exists for both admin server and managed servers.
   * Verify EM console is accessible.
   */
  @Order(2)
  @Test
  @DisplayName("Reuse the same RCU schema to restart JRF domain")
  public void testReuseRCUschemalToRestartDomain() {
    saveAndRestoreOpssWalletfileSecret(jrfDomainNamespace, domainUid, opsswalletfileSecretName);
    shutdownDomain();
    patchDomainWithWalletFileSecret(opsswalletfileSecretName);
    startupDomain();
    verifyDomainReady();
  }

  /**
   * Save the OPSS key wallet from a running JRF domain's introspector configmap to a file.
   * @param namespace namespace where JRF domain exists
   * @param domainUid unique domain Uid
   * @param walletfileSecretName name of wallet file secret
   */
  private void saveAndRestoreOpssWalletfileSecret(String namespace, String domainUid,
       String walletfileSecretName) {
    Path saveAndRestoreOpssPath =
         Paths.get(RESOURCE_DIR, "bash-scripts", "opss-wallet.sh");
    String script = saveAndRestoreOpssPath.toString();
    logger.info("Script for saveAndRestoreOpss is {0)", script);

    //save opss wallet file
    String command1 = script + " -d " + domainUid + " -n " + namespace + " -s";
    logger.info("Save wallet file command: {0}", command1);
    assertTrue(() -> Command.withParams(
        defaultCommandParams()
            .command(command1)
            .saveResults(true)
            .redirect(true))
        .execute());

    //restore opss wallet password secret
    String command2 = script + " -d " + domainUid + " -n " + namespace + " -r" + " -ws " + walletfileSecretName;
    logger.info("Restore wallet file command: {0}", command2);
    assertTrue(() -> Command.withParams(
          defaultCommandParams()
            .command(command2)
            .saveResults(true)
            .redirect(true))
        .execute());

  }

  /**
   * Shutdown the domain by setting serverStartPolicy as "NEVER".
   */
  private void shutdownDomain() {
    patchServerStartPolicy("/spec/serverStartPolicy", "NEVER", jrfDomainNamespace, domainUid);
    logger.info("Domain is patched to stop entire WebLogic domain");

    // make sure all the server pods are removed after patch
    checkPodDeleted(adminServerPodName, domainUid, jrfDomainNamespace);
    for (int i = 1; i <= replicaCount; i++) {
      checkPodDeleted(managedServerPrefix + i, domainUid, jrfDomainNamespace);
    }

    logger.info("Domain shutdown success");

  }

  /**
   * Startup the domain by setting serverStartPolicy as "IF_NEEDED".
   */
  private void startupDomain() {
    patchServerStartPolicy("/spec/serverStartPolicy", "IF_NEEDED", jrfDomainNamespace, domainUid);
    logger.info("Domain is patched to start all servers in the domain");
  }

  /**
   * Patch the domain with opss wallet file secret.
   * @param opssWalletFileSecretName the name of opps wallet file secret
   * @return true if patching succeeds, false otherwise
   */
  private boolean patchDomainWithWalletFileSecret(String opssWalletFileSecretName) {
    // construct the patch string for adding server pod resources
    StringBuffer patchStr = new StringBuffer("[{")
        .append("\"op\": \"add\", ")
        .append("\"path\": \"/spec/configuration/opss/walletFileSecret\", ")
        .append("\"value\": \"")
        .append(opssWalletFileSecretName)
        .append("\"}]");

    logger.info("Adding opssWalletPasswordSecretName for domain {0} in namespace {1} using patch string: {2}",
        domainUid, jrfDomainNamespace, patchStr.toString());

    V1Patch patch = new V1Patch(new String(patchStr));

    return patchDomainCustomResource(domainUid, jrfDomainNamespace, patch, V1Patch.PATCH_FORMAT_JSON_PATCH);
  }

  /**
   * Construct a domain object with the given parameters that can be used to create a domain resource.
   * @param domainUid unique Uid of the domain
   * @param domNamespace  namespace where the domain exists
   * @param adminSecretName  name of admin secret
   * @param repoSecretName name of repository secret
   * @param encryptionSecretName name of encryption secret
   * @param rcuAccessSecretName name of RCU access secret
   * @param opssWalletPasswordSecretName name of opss wallet password secret
   * @param replicaCount count of replicas
   * @param miiImage name of model in image
   * @return Domain WebLogic domain
   */
  private Domain createDomainResource(
      String domainUid, String domNamespace, String adminSecretName,
      String repoSecretName, String encryptionSecretName, String rcuAccessSecretName,
      String opssWalletPasswordSecretName, int replicaCount, String miiImage) {
    // create the domain CR
    Domain domain = new Domain()
            .apiVersion(DOMAIN_API_VERSION)
            .kind("Domain")
            .metadata(new V1ObjectMeta()
                    .name(domainUid)
                    .namespace(domNamespace))
            .spec(new DomainSpec()
                    .domainUid(domainUid)
                    .domainHomeSourceType("FromModel")
                    .image(miiImage)
                    .imagePullPolicy("IfNotPresent")
                    .addImagePullSecretsItem(new V1LocalObjectReference()
                            .name(repoSecretName))
                    .webLogicCredentialsSecret(new V1SecretReference()
                            .name(adminSecretName)
                            .namespace(domNamespace))
                    .includeServerOutInPodLog(true)
                    .serverStartPolicy("IF_NEEDED")
                    .serverPod(new ServerPod()
                            .addEnvItem(new V1EnvVar()
                                    .name("JAVA_OPTIONS")
                                    .value("-Dweblogic.StdoutDebugEnabled=false"))
                            .addEnvItem(new V1EnvVar()
                                    .name("USER_MEM_ARGS")
                                    .value("-Djava.security.egd=file:/dev/./urandom ")))
                    .adminServer(new AdminServer()
                            .serverStartState("RUNNING")
                            .adminService(new AdminService()
                                    .addChannelsItem(new Channel()
                                            .channelName("default")
                                            .nodePort(0))))
                    .addClustersItem(new Cluster()
                            .clusterName("cluster-1")
                            .replicas(replicaCount)
                            .serverStartState("RUNNING"))
                    .configuration(new Configuration()
                            .opss(new Opss()
                                   .walletPasswordSecret(opssWalletPasswordSecretName))
                            .model(new Model()
                                    .domainType("JRF")
                                    .runtimeEncryptionSecret(encryptionSecretName))
                            .addSecretsItem(rcuAccessSecretName)
                            .introspectorJobActiveDeadlineSeconds(600L)));

    return domain;
  }

  /**
   * Verify Pod is ready and service exists for both admin server and managed servers.
   * Verify EM console is accessible.
   */
  private void verifyDomainReady() {
    checkPodReadyAndServiceExists(adminServerPodName, domainUid, jrfDomainNamespace);
    for (int i = 1; i <= replicaCount; i++) {
      logger.info("Checking managed server service {0} is created in namespace {1}",
          managedServerPrefix + i + "-c1", jrfDomainNamespace);
      checkPodReadyAndServiceExists(managedServerPrefix + i + "-c1", domainUid, jrfDomainNamespace);
    }

    //check access to the em console: http://hostname:port/em
    int nodePort = getServiceNodePort(
           jrfDomainNamespace, getExternalServicePodName(adminServerPodName), "default");
    assertTrue(nodePort != -1,
          "Could not get the default external service node port");
    logger.info("Found the default service nodePort {0}", nodePort);
    String curlCmd1 = "curl -s -L --show-error --noproxy '*' "
        + " http://" + K8S_NODEPORT_HOST + ":" + nodePort
        + "/em --write-out %{http_code} -o /dev/null";
    logger.info("Executing default nodeport curl command {0}", curlCmd1);
    assertTrue(callWebAppAndWaitTillReady(curlCmd1, 5), "Calling web app failed");
    logger.info("EM console is accessible thru default service");
  }

}

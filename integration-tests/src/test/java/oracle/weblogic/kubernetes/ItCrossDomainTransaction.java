// Copyright (c) 2020, 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Properties;

import io.kubernetes.client.openapi.models.V1EnvVar;
import io.kubernetes.client.openapi.models.V1LocalObjectReference;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import oracle.weblogic.domain.AdminServer;
import oracle.weblogic.domain.AdminService;
import oracle.weblogic.domain.Channel;
import oracle.weblogic.domain.Configuration;
import oracle.weblogic.domain.DomainResource;
import oracle.weblogic.domain.DomainSpec;
import oracle.weblogic.domain.Model;
import oracle.weblogic.domain.ServerPod;
import oracle.weblogic.kubernetes.annotations.IntegrationTest;
import oracle.weblogic.kubernetes.annotations.Namespaces;
import oracle.weblogic.kubernetes.assertions.TestAssertions;
import oracle.weblogic.kubernetes.logging.LoggingFacade;
import oracle.weblogic.kubernetes.utils.ExecResult;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static oracle.weblogic.kubernetes.TestConstants.ADMIN_PASSWORD_DEFAULT;
import static oracle.weblogic.kubernetes.TestConstants.ADMIN_USERNAME_DEFAULT;
import static oracle.weblogic.kubernetes.TestConstants.DB_IMAGE_TO_USE_IN_SPEC;
import static oracle.weblogic.kubernetes.TestConstants.DOMAIN_API_VERSION;
import static oracle.weblogic.kubernetes.TestConstants.DOMAIN_VERSION;
import static oracle.weblogic.kubernetes.TestConstants.IMAGE_PULL_POLICY;
import static oracle.weblogic.kubernetes.TestConstants.RESULTS_ROOT;
import static oracle.weblogic.kubernetes.TestConstants.TEST_IMAGES_REPO_SECRET_NAME;
import static oracle.weblogic.kubernetes.TestConstants.WEBLOGIC_SLIM;
import static oracle.weblogic.kubernetes.actions.ActionConstants.APP_DIR;
import static oracle.weblogic.kubernetes.actions.ActionConstants.MODEL_DIR;
import static oracle.weblogic.kubernetes.actions.ActionConstants.WORK_DIR;
import static oracle.weblogic.kubernetes.actions.TestActions.createDomainCustomResource;
import static oracle.weblogic.kubernetes.actions.TestActions.getPodIP;
import static oracle.weblogic.kubernetes.actions.TestActions.getServiceNodePort;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.domainExists;
import static oracle.weblogic.kubernetes.utils.ApplicationUtils.checkAppIsActive;
import static oracle.weblogic.kubernetes.utils.BuildApplication.buildApplication;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.checkPodReadyAndServiceExists;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.getHostAndPort;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.getNextFreePort;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.testUntil;
import static oracle.weblogic.kubernetes.utils.DbUtils.getDBNodePort;
import static oracle.weblogic.kubernetes.utils.DbUtils.startOracleDB;
import static oracle.weblogic.kubernetes.utils.ExecCommand.exec;
import static oracle.weblogic.kubernetes.utils.FileUtils.copyFolder;
import static oracle.weblogic.kubernetes.utils.FileUtils.replaceStringInFile;
import static oracle.weblogic.kubernetes.utils.ImageUtils.createBaseRepoSecret;
import static oracle.weblogic.kubernetes.utils.ImageUtils.createImageAndVerify;
import static oracle.weblogic.kubernetes.utils.ImageUtils.createTestRepoSecret;
import static oracle.weblogic.kubernetes.utils.ImageUtils.imageRepoLoginAndPushImageToRegistry;
import static oracle.weblogic.kubernetes.utils.OKDUtils.createRouteForOKD;
import static oracle.weblogic.kubernetes.utils.OperatorUtils.installAndVerifyOperator;
import static oracle.weblogic.kubernetes.utils.PodUtils.getExternalServicePodName;
import static oracle.weblogic.kubernetes.utils.PodUtils.setPodAntiAffinity;
import static oracle.weblogic.kubernetes.utils.SecretUtils.createSecretWithUsernamePassword;
import static oracle.weblogic.kubernetes.utils.ThreadSafeLogger.getLogger;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Cross domain transaction tests.
 */
@DisplayName("Verify cross domain transaction is successful")
@IntegrationTest
@Tag("olcne")
@Tag("oke-parallel")
@Tag("kind-parallel")
@Tag("okd-wls-srg")
class ItCrossDomainTransaction {

  private static final String WDT_MODEL_FILE_DOMAIN1 = "model-crossdomaintransaction-domain1.yaml";
  private static final String WDT_MODEL_FILE_DOMAIN2 = "model-crossdomaintransaction-domain2.yaml";

  private static final String WDT_MODEL_DOMAIN1_PROPS = "model-crossdomaintransaction-domain1.properties";
  private static final String WDT_MODEL_DOMAIN2_PROPS = "model-crossdomaintransaction-domain2.properties";
  private static final String WDT_IMAGE_NAME1 = "domain1-cdxaction-wdt-image";
  private static final String WDT_IMAGE_NAME2 = "domain2-cdxaction-wdt-image";
  private static final String PROPS_TEMP_DIR = RESULTS_ROOT + "/crossdomaintransactiontemp";
  private static final String WDT_MODEL_FILE_JMS = "model-cdt-jms.yaml";
  private static final String WDT_MODEL_FILE_JDBC = "model-cdt-jdbc.yaml";
  private static final String WDT_MODEL_FILE_JMS2 = "model2-cdt-jms.yaml";

  private static String opNamespace = null;
  private static String domain1Namespace = null;
  private static String domain2Namespace = null;
  private static String domainUid1 = "domain1";
  private static String domainUid2 = "domain2";
  private static int  domain1AdminServiceNodePort = -1;
  private static int  admin2ServiceNodePort = -1;
  private static String domain1AdminServerPodName = domainUid1 + "-admin-server";
  private final String domain1ManagedServerPrefix = domainUid1 + "-managed-server";
  private static String domain2AdminServerPodName = domainUid2 + "-admin-server";
  private final String domain2ManagedServerPrefix = domainUid2 + "-managed-server";
  private static final String ORACLEDBURLPREFIX = "oracledb.";
  private static String ORACLEDBSUFFIX = null;
  private static LoggingFacade logger = null;
  static String dbUrl;
  static int dbNodePort;
  private static String domain1AdminExtSvcRouteHost = null;
  private static String domain2AdminExtSvcRouteHost = null;
  private static String adminExtSvcRouteHost = null;
  private static String hostAndPort = null;
  private static String dbPodIP = null;
  private static int dbPort = 1521;

  /**
   * Install Operator.
   * @param namespaces list of namespaces created by the IntegrationTestWatcher by the
   *     JUnit engine parameter resolution mechanism
   */
  @BeforeAll
  public static void initAll(@Namespaces(3) List<String> namespaces) {
    logger = getLogger();

    // get a new unique opNamespace
    logger.info("Creating unique namespace for Operator");
    assertNotNull(namespaces.get(0), "Namespace list is null");
    opNamespace = namespaces.get(0);

    logger.info("Creating unique namespace for Domain");
    assertNotNull(namespaces.get(1), "Namespace list is null");
    domain1Namespace = namespaces.get(1);

    logger.info("Creating unique namespace for Domain");
    assertNotNull(namespaces.get(2), "Namespace list is null");
    domain2Namespace = namespaces.get(2);

    final int dbListenerPort = getNextFreePort();
    ORACLEDBSUFFIX = ".svc.cluster.local:" + dbListenerPort + "/devpdb.k8s";
    dbUrl = ORACLEDBURLPREFIX + domain2Namespace + ORACLEDBSUFFIX;
    createBaseRepoSecret(domain2Namespace);

    //Start oracleDB
    logger.info("Start Oracle DB with namespace: {0}, dbListenerPort:{1}",
        domain2Namespace, dbListenerPort);
    assertDoesNotThrow(
        () -> startOracleDB(DB_IMAGE_TO_USE_IN_SPEC, getNextFreePort(), domain2Namespace, dbListenerPort),
        "Failed to start Oracle DB");
    dbNodePort = getDBNodePort(domain2Namespace, "oracledb");
    logger.info("DB Node Port = {0}", dbNodePort);

    dbPodIP = assertDoesNotThrow(
        () -> getPodIP(domain2Namespace, "", "oracledb"),
        String.format("Get pod IP address failed with ApiException for oracledb in namespace %s",
            domain2Namespace));
    logger.info("db Pod IP {0} ", dbPodIP);
    // Now that we got the namespaces for both the domains, we need to update the model properties
    // file with the namespaces. For a cross-domain transaction to work, we need to have the externalDNSName
    // set in the config file. Cannot set this after the domain is up since a server restart is
    // required for this to take effect. So, copying the property file to RESULT_ROOT and updating the
    // property file
    updatePropertyFile();

    // install and verify operator
    installAndVerifyOperator(opNamespace, domain1Namespace, domain2Namespace);
    buildApplicationsAndDomains();
  }

  /**
   * Verify all server pods are running.
   * Verify k8s services for all servers are created.
   */
  @BeforeEach
  public void beforeEach() {
    int replicaCount = 2;
    for (int i = 1; i <= replicaCount; i++) {
      checkPodReadyAndServiceExists(domain2ManagedServerPrefix + i,
            domainUid2, domain2Namespace);
    }
    for (int i = 1; i <= replicaCount; i++) {
      checkPodReadyAndServiceExists(domain1ManagedServerPrefix + i,
            domainUid1, domain1Namespace);
    }
  }

  private static void updatePropertyFile() {
    //create a temporary directory to copy and update the properties file
    Path target = Paths.get(PROPS_TEMP_DIR);
    Path source1 = Paths.get(MODEL_DIR, WDT_MODEL_DOMAIN1_PROPS);
    Path source2 = Paths.get(MODEL_DIR, WDT_MODEL_DOMAIN2_PROPS);
    logger.info("Copy the properties file to the above area so that we can add namespace property");
    assertDoesNotThrow(() -> {
      Files.createDirectories(target);
      Files.copy(source1, target.resolve(source1.getFileName()), StandardCopyOption.REPLACE_EXISTING);
      Files.copy(source2, target.resolve(source2.getFileName()), StandardCopyOption.REPLACE_EXISTING);
    });

    assertDoesNotThrow(
        () -> addToPropertyFile(WDT_MODEL_DOMAIN1_PROPS, domain1Namespace),
        String.format("Failed to update %s with namespace %s", WDT_MODEL_DOMAIN1_PROPS, domain1Namespace));
    assertDoesNotThrow(
        () -> addToPropertyFile(WDT_MODEL_DOMAIN2_PROPS, domain2Namespace),
        String.format("Failed to update %s with namespace %s", WDT_MODEL_DOMAIN2_PROPS, domain2Namespace));

  }

  private static void addToPropertyFile(String propFileName, String domainNamespace) throws IOException {
    FileInputStream in = new FileInputStream(PROPS_TEMP_DIR + "/" + propFileName);
    Properties props = new Properties();
    props.load(in);
    in.close();

    FileOutputStream out = new FileOutputStream(PROPS_TEMP_DIR + "/" + propFileName);
    props.setProperty("NAMESPACE", domainNamespace);
    //props.setProperty("K8S_NODEPORT_HOST", K8S_NODEPORT_HOST);
    //props.setProperty("DBPORT", Integer.toString(dbNodePort));
    props.setProperty("K8S_NODEPORT_HOST", dbPodIP);
    props.setProperty("DBPORT", Integer.toString(dbPort));
    props.store(out, null);
    out.close();
  }

  private static void buildApplicationsAndDomains() {

    //build application archive

    Path targetDir = Paths.get(WORK_DIR,
         ItCrossDomainTransaction.class.getName() + "/txforward");
    Path distDir = buildApplication(Paths.get(APP_DIR, "txforward"), null, null,
        "build", domain1Namespace, targetDir);
    logger.info("distDir is {0}", distDir.toString());
    assertTrue(Paths.get(distDir.toString(),
        "txforward.ear").toFile().exists(),
        "Application archive is not available");
    String appSource = distDir.toString() + "/txforward.ear";
    logger.info("Application is in {0}", appSource);

    //build application archive
    targetDir = Paths.get(WORK_DIR,
        ItCrossDomainTransaction.class.getName() + "/cdtservlet");
    distDir = buildApplication(Paths.get(APP_DIR, "cdtservlet"), null, null,
        "build", domain1Namespace, targetDir);
    logger.info("distDir is {0}", distDir.toString());
    assertTrue(Paths.get(distDir.toString(),
        "cdttxservlet.war").toFile().exists(),
        "Application archive is not available");
    String appSource1 = distDir.toString() + "/cdttxservlet.war";
    logger.info("Application is in {0}", appSource1);

    //build application archive for JMS Send/Receive
    targetDir = Paths.get(WORK_DIR,
        ItCrossDomainTransaction.class.getName() + "/jmsservlet");
    distDir = buildApplication(Paths.get(APP_DIR, "jmsservlet"), null, null,
        "build", domain1Namespace, targetDir);
    logger.info("distDir is {0}", distDir.toString());
    assertTrue(Paths.get(distDir.toString(),
        "jmsservlet.war").toFile().exists(),
        "Application archive is not available");
    String appSource2 = distDir.toString() + "/jmsservlet.war";
    logger.info("Application is in {0}", appSource2);

    Path mdbSrcDir  = Paths.get(APP_DIR, "mdbtopic");
    Path mdbDestDir = Paths.get(PROPS_TEMP_DIR, "mdbtopic");

    assertDoesNotThrow(() -> copyFolder(
         mdbSrcDir.toString(), mdbDestDir.toString()),
        "Could not copy mdbtopic application directory");

    Path template = Paths.get(PROPS_TEMP_DIR,
           "mdbtopic/src/application/MdbTopic.java");

    // Add the domain2 namespace decorated URL to the providerURL of MDB
    // so that it can communicate with remote destination on domain2
    assertDoesNotThrow(() -> replaceStringInFile(
        template.toString(), "domain2-namespace", domain2Namespace),
        "Could not modify the domain2Namespace in MDB Template file");

    //build application archive for MDB
    targetDir = Paths.get(WORK_DIR,
         ItCrossDomainTransaction.class.getName() + "/mdbtopic");
    distDir = buildApplication(Paths.get(PROPS_TEMP_DIR, "mdbtopic"), null, null,
        "build", domain1Namespace, targetDir);
    logger.info("distDir is {0}", distDir.toString());
    assertTrue(Paths.get(distDir.toString(),
        "mdbtopic.jar").toFile().exists(),
        "Application archive is not available");
    String appSource3 = distDir.toString() + "/mdbtopic.jar";
    logger.info("Application is in {0}", appSource3);

    // create admin credential secret for domain1
    logger.info("Create admin credential secret for domain1");
    String domain1AdminSecretName = domainUid1 + "-weblogic-credentials";
    assertDoesNotThrow(() -> createSecretWithUsernamePassword(
        domain1AdminSecretName, domain1Namespace, ADMIN_USERNAME_DEFAULT, ADMIN_PASSWORD_DEFAULT),
        String.format("createSecret %s failed for %s", domain1AdminSecretName, domainUid1));

    // create admin credential secret for domain2
    logger.info("Create admin credential secret for domain2");
    String domain2AdminSecretName = domainUid2 + "-weblogic-credentials";
    assertDoesNotThrow(() -> createSecretWithUsernamePassword(
        domain2AdminSecretName, domain2Namespace, ADMIN_USERNAME_DEFAULT, ADMIN_PASSWORD_DEFAULT),
        String.format("createSecret %s failed for %s", domain2AdminSecretName, domainUid2));

    // build the model file list for domain1
    final List<String> modelListDomain1 = Arrays.asList(
        MODEL_DIR + "/" + WDT_MODEL_FILE_DOMAIN1,
        MODEL_DIR + "/" + WDT_MODEL_FILE_JMS);

    final List<String> appSrcDirList1 = Arrays.asList(appSource, appSource1, appSource2, appSource3);

    logger.info("Creating image with model file and verify");
    String domain1Image = createImageAndVerify(
        WDT_IMAGE_NAME1, modelListDomain1, appSrcDirList1, WDT_MODEL_DOMAIN1_PROPS, PROPS_TEMP_DIR, domainUid1);
    logger.info("Created {0} image", domain1Image);

    // repo login and push image to registry if necessary
    imageRepoLoginAndPushImageToRegistry(domain1Image);

    // build the model file list for domain2
    final List<String> modelListDomain2 = Arrays.asList(
        MODEL_DIR + "/" + WDT_MODEL_FILE_DOMAIN2,
        MODEL_DIR + "/" + WDT_MODEL_FILE_JMS2,
        MODEL_DIR + "/" + WDT_MODEL_FILE_JDBC);

    final List<String> appSrcDirList2 = Collections.singletonList(appSource);

    logger.info("Creating image with model file and verify");
    String domain2Image = createImageAndVerify(
        WDT_IMAGE_NAME2, modelListDomain2, appSrcDirList2, WDT_MODEL_DOMAIN2_PROPS, PROPS_TEMP_DIR, domainUid2);
    logger.info("Created {0} image", domain2Image);

    // repo login and push image to registry if necessary
    imageRepoLoginAndPushImageToRegistry(domain2Image);

    //create domain1
    createDomain(domainUid1, domain1Namespace, domain1AdminSecretName, domain1Image);
    domain1AdminExtSvcRouteHost = adminExtSvcRouteHost;
    //create domain2
    createDomain(domainUid2, domain2Namespace, domain2AdminSecretName, domain2Image);
    domain2AdminExtSvcRouteHost = adminExtSvcRouteHost;

    logger.info("Getting admin server external service node port(s)");
    domain1AdminServiceNodePort = assertDoesNotThrow(
        () -> getServiceNodePort(domain1Namespace, getExternalServicePodName(domain1AdminServerPodName), "default"),
        "Getting admin server node port failed");
    assertNotEquals(-1, domain1AdminServiceNodePort, "admin server default node port is not valid");

    admin2ServiceNodePort = assertDoesNotThrow(
      () -> getServiceNodePort(domain2Namespace, getExternalServicePodName(domain2AdminServerPodName), "default"),
        "Getting admin server node port failed");
    assertNotEquals(-1, admin2ServiceNodePort, "admin server default node port is not valid");

    hostAndPort = getHostAndPort(domain1AdminExtSvcRouteHost, domain1AdminServiceNodePort);
  }

  /*
   * This test verifies a cross-domain transaction in non-istio environment.
   * domain-in-image using wdt is used to create 2 domains in different
   * namespaces. An app is deployed to both the domains and the servlet
   * is invoked which starts a transaction that spans both domains.
   * The application consists of
   *  (a) servlet
   *  (b) a remote object that defines a method to register a
   *      simple javax.transaction.Synchronization object.
   * When the servlet is invoked, a global transaction is started, and the
   * specified list of server URLs is used to look up the remote objects and
   * register a Synchronization object on each server.
   * Finally, the transaction is committed.
   * If the server listen-addresses are resolvable between the transaction
   * participants, then the transaction should complete successfully
   */
  @Test
  @DisplayName("Check cross domain transaction works")
  void testCrossDomainTransaction() {

    String curlRequest = String.format("curl -v --show-error --noproxy '*' "
            + "http://%s/TxForward/TxForward?urls=t3://%s.%s:7001,t3://%s1.%s:8001,t3://%s1.%s:8001,t3://%s2.%s:8001",
        hostAndPort, domain1AdminServerPodName, domain1Namespace,
        domain1ManagedServerPrefix, domain1Namespace, domain2ManagedServerPrefix, domain2Namespace,
        domain2ManagedServerPrefix, domain2Namespace);

    ExecResult result = null;
    logger.info("curl command {0}", curlRequest);
    result = assertDoesNotThrow(
        () -> exec(curlRequest, true));
    if (result.exitValue() == 0) {
      logger.info("\n HTTP response is \n " + result.stdout());
      logger.info("curl command returned {0}", result.toString());
      assertTrue(result.stdout().contains("Status=Committed"), "crossDomainTransaction failed");
    }
  }

  /**
   * This test verifies a cross-domain transaction with re-connection.
   * It makes sure the disitibuted transaction is completed successfully
   * when a coordinator server is re-started after writing to transcation log
   * A servlet is deployed to the admin server of domain1.
   * The servlet starts a transaction with TMAfterTLogBeforeCommitExit
   * transaction property set. The servlet inserts data into an Oracle DB
   * table and sends a message to a JMS queue as part of the same transaction.
   * The coordinator (server in domain2) should exit before commit and the
   * domain1 admin server should be able to re-establish the connection with
   * domain2 and the transaction should commit.
   *
   */
  @Test
  @DisplayName("Check cross domain transaction with TMAfterTLogBeforeCommitExit property commits")
  void testCrossDomainTransactionWithFailInjection() {

    String curlRequest = String.format("curl -v --show-error --noproxy '*' "
            + "http://%s/cdttxservlet/cdttxservlet?namespaces=%s,%s",
        hostAndPort, domain1Namespace, domain2Namespace);

    ExecResult result = null;
    logger.info("curl command {0}", curlRequest);
    result = assertDoesNotThrow(
        () -> exec(curlRequest, true));
    if (result.exitValue() == 0) {
      logger.info("\n HTTP response is \n " + result.stdout());
      logger.info("curl command returned {0}", result.toString());
      assertTrue(result.stdout().contains("Status=SUCCESS"),
          "crossDomainTransaction with TMAfterTLogBeforeCommitExit failed");
    }
  }

  /**
   * This test verifies cross-domain MessageDrivenBean communication
   * A transacted MDB on Domain D1 listen on a replicated Distributed Topic
   * on Domain D2.
   * The MDB is deployed to cluster on domain D1 with MessagesDistributionMode
   * set to One-Copy-Per-Server. The OnMessage() routine sends a message to
   * local queue on receiving the message.
   * An application servlet is deployed to Administration Server on D1 which
   * send/receive message from a JMS destination based on a given URL.
   * (a) app servlet send message to Distributed Topic on D2
   * (b) mdb puts a message into local Queue for each received message
   * (c) make sure local Queue gets 2X times messages sent to Distributed Topic
   * Since the MessagesDistributionMode is set to One-Copy-Per-Server and
   * targeted to a cluster of two servers, onMessage() will be triggered
   * for both instance of MDB for a message sent to Distributed Topic
   */
  @Test
  @DisplayName("Check cross domain transcated MDB communication ")
  void testCrossDomainTranscatedMDB() {

    // No extra header info
    assertTrue(checkAppIsActive(hostAndPort,
                 "", "mdbtopic","cluster-1",
                 ADMIN_USERNAME_DEFAULT,ADMIN_PASSWORD_DEFAULT),
             "MDB application can not be activated on domain1/cluster");

    logger.info("MDB application is activated on domain1/cluster");

    String curlRequest = String.format("curl -v --show-error --noproxy '*' "
            + "\"http://%s/jmsservlet/jmstest?"
            + "url=t3://domain2-cluster-cluster-1.%s:8001&"
            + "cf=jms.ClusterConnectionFactory&"
            + "action=send&"
            + "dest=jms/testCdtUniformTopic\"",
           hostAndPort, domain2Namespace);

    ExecResult result = null;
    logger.info("curl command {0}", curlRequest);
    result = assertDoesNotThrow(
        () -> exec(curlRequest, true));
    if (result.exitValue() == 0) {
      logger.info("\n HTTP response is \n " + result.stdout());
      logger.info("curl command returned {0}", result.toString());
      assertTrue(result.stdout().contains("Sent (10) message"),
          "Can not send message to remote Distributed Topic");
    }

    assertTrue(checkLocalQueue(),
         "Expected number of message not found in Accounting Queue");
  }

  private boolean checkLocalQueue() {
    String curlString = String.format("curl -v --show-error --noproxy '*' "
            + "\"http://%s/jmsservlet/jmstest?"
            + "url=t3://localhost:7001&"
            + "action=receive&dest=jms.testAccountingQueue\"",
            hostAndPort);

    logger.info("curl command {0}", curlString);

    testUntil(
        () -> exec(new String(curlString), true).stdout().contains("Messages are distributed"),
        logger,
        "local queue to be updated");
    return true;
  }

  private static void createDomain(String domainUid, String domainNamespace, String adminSecretName,
                            String domainImage) {
    // admin/managed server name here should match with model yaml in WDT_MODEL_FILE
    final String adminServerPodName = domainUid + "-admin-server";
    final String managedServerPrefix = domainUid + "-managed-server";
    final int replicaCount = 2;

    // Create the repo secret to pull the image
    // this secret is used only for non-kind cluster
    createTestRepoSecret(domainNamespace);

    // create the domain CR
    createDomainResource(domainUid, domainNamespace, adminSecretName, TEST_IMAGES_REPO_SECRET_NAME,
        replicaCount, domainImage);

    // wait for the domain to exist
    logger.info("Check for domain custom resource in namespace {0}", domainNamespace);
    testUntil(
        domainExists(domainUid, DOMAIN_VERSION, domainNamespace),
        logger,
        "domain {0} to be created in namespace {1}",
        domainUid,
        domainNamespace
    );

    // check admin server pod exists
    // check admin server services created
    logger.info("Check for admin server pod {0} existence in namespace {1}",
        adminServerPodName, domainNamespace);
    checkPodReadyAndServiceExists(adminServerPodName, domainUid, domainNamespace);
    // check managed server pods exist
    // check managed server services created
    for (int i = 1; i <= replicaCount; i++) {
      logger.info("Check for managed server pod {0} existence in namespace {1}",
          managedServerPrefix + i, domainNamespace);
      checkPodReadyAndServiceExists(managedServerPrefix + i, domainUid, domainNamespace);
    }

    adminExtSvcRouteHost = createRouteForOKD(getExternalServicePodName(adminServerPodName), domainNamespace);
    // The fail inject test case, the response to the curl command takes longer than the default timeout of 30s
    // So, have to increase the proxy timeout for the route
    String command = "oc -n " + domainNamespace + " annotate route "
                      + getExternalServicePodName(adminServerPodName)
                      + " --overwrite haproxy.router.openshift.io/timeout=600s";
    logger.info("command to set timeout = {0}", command);
    assertDoesNotThrow(
        () -> exec(command, true));

    logger.info("Getting node port");
    int serviceNodePort = assertDoesNotThrow(() -> getServiceNodePort(domainNamespace,
        getExternalServicePodName(adminServerPodName), "default"),
        "Getting admin server node port failed");

    if (!WEBLOGIC_SLIM) {
      logger.info("Validating WebLogic admin console");
      testUntil(
          assertDoesNotThrow(() -> {
            return TestAssertions.adminNodePortAccessible(serviceNodePort,
                 ADMIN_USERNAME_DEFAULT, ADMIN_PASSWORD_DEFAULT, adminExtSvcRouteHost);
          }, "Access to admin server node port failed"),
          logger,
          "Console login validation");
    } else {
      logger.info("Skipping WebLogic Console check for Weblogic slim images");
    }
  }

  private static void createDomainResource(String domainUid, String domNamespace, String adminSecretName,
                                    String repoSecretName, int replicaCount, String domainImage) {
    logger.info("Image to be used is {0}", domainImage);

    // create the domain CR
    DomainResource domain = new DomainResource()
        .apiVersion(DOMAIN_API_VERSION)
        .kind("Domain")
        .metadata(new V1ObjectMeta()
            .name(domainUid)
            .namespace(domNamespace))
        .spec(new DomainSpec()
            .domainUid(domainUid)
            .replicas(replicaCount)
            .domainHomeSourceType("Image")
            .image(domainImage)
            .imagePullPolicy(IMAGE_PULL_POLICY)
            .addImagePullSecretsItem(new V1LocalObjectReference()
                .name(repoSecretName))
            .webLogicCredentialsSecret(new V1LocalObjectReference()
                .name(adminSecretName))
            .includeServerOutInPodLog(true)
            .serverStartPolicy("IfNeeded")
            .serverPod(new ServerPod()
                .addEnvItem(new V1EnvVar()
                    .name("JAVA_OPTIONS")
                    .value("-Dweblogic.transaction.EnableInstrumentedTM=true -Dweblogic.StdoutDebugEnabled=false"
                        + "-Dweblogic.debug.DebugJTAXA=true "
                        + "-Dweblogic.debug.DebugJTA2PC=true"))
                .addEnvItem(new V1EnvVar()
                    .name("USER_MEM_ARGS")
                    .value("-Djava.security.egd=file:/dev/./urandom ")))
            .adminServer(new AdminServer()
                .adminService(new AdminService()
                    .addChannelsItem(new Channel()
                        .channelName("default")
                        .nodePort(getNextFreePort()))))
            .configuration(new Configuration()
                .model(new Model()
                    .domainType("WLS"))
                .introspectorJobActiveDeadlineSeconds(300L)));
    setPodAntiAffinity(domain);
    logger.info("Create domain custom resource for domainUid {0} in namespace {1}",
        domainUid, domNamespace);
    boolean domCreated = assertDoesNotThrow(() -> createDomainCustomResource(domain),
        String.format("Create domain custom resource failed with ApiException for %s in namespace %s",
            domainUid, domNamespace));
    assertTrue(domCreated, String.format("Create domain custom resource failed with ApiException "
        + "for %s in namespace %s", domainUid, domNamespace));
  }
}

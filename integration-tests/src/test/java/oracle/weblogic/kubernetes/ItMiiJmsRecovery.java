// Copyright (c) 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;

import oracle.weblogic.kubernetes.actions.impl.primitive.Command;
import oracle.weblogic.kubernetes.actions.impl.primitive.CommandParams;
import oracle.weblogic.kubernetes.annotations.IntegrationTest;
import oracle.weblogic.kubernetes.annotations.Namespaces;
import oracle.weblogic.kubernetes.logging.LoggingFacade;
import oracle.weblogic.kubernetes.utils.ExecResult;
import org.awaitility.core.ConditionFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import static java.util.concurrent.TimeUnit.MINUTES;
import static java.util.concurrent.TimeUnit.SECONDS;
import static oracle.weblogic.kubernetes.TestConstants.ADMIN_PASSWORD_DEFAULT;
import static oracle.weblogic.kubernetes.TestConstants.ADMIN_USERNAME_DEFAULT;
import static oracle.weblogic.kubernetes.TestConstants.DB_IMAGE_TO_USE_IN_SPEC;
import static oracle.weblogic.kubernetes.TestConstants.DOMAIN_VERSION;
import static oracle.weblogic.kubernetes.TestConstants.K8S_NODEPORT_HOST;
import static oracle.weblogic.kubernetes.TestConstants.MII_BASIC_IMAGE_NAME;
import static oracle.weblogic.kubernetes.TestConstants.MII_BASIC_IMAGE_TAG;
import static oracle.weblogic.kubernetes.TestConstants.OCIR_SECRET_NAME;
import static oracle.weblogic.kubernetes.actions.ActionConstants.ITTESTS_DIR;
import static oracle.weblogic.kubernetes.actions.ActionConstants.MODEL_DIR;
import static oracle.weblogic.kubernetes.actions.ActionConstants.RESOURCE_DIR;
import static oracle.weblogic.kubernetes.actions.TestActions.getServiceNodePort;
import static oracle.weblogic.kubernetes.actions.TestActions.scaleCluster;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.domainExists;
import static oracle.weblogic.kubernetes.utils.CommonMiiTestUtils.createDatabaseSecret;
import static oracle.weblogic.kubernetes.utils.CommonMiiTestUtils.createDomainResourceWithLogHome;
import static oracle.weblogic.kubernetes.utils.CommonMiiTestUtils.createDomainSecret;
import static oracle.weblogic.kubernetes.utils.CommonMiiTestUtils.createJobToChangePermissionsOnPvHostPath;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.checkPodDoesNotExist;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.checkPodReadyAndServiceExists;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.createConfigMapAndVerify;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.createOcirRepoSecret;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.createPV;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.createPVC;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.createSecretForBaseImages;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.getExternalServicePodName;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.installAndVerifyOperator;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.runClientInsidePod;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.runJavacInsidePod;
import static oracle.weblogic.kubernetes.utils.DbUtils.createLeasingTable;
import static oracle.weblogic.kubernetes.utils.DbUtils.getDBNodePort;
import static oracle.weblogic.kubernetes.utils.DbUtils.startOracleDB;
import static oracle.weblogic.kubernetes.utils.ExecCommand.exec;
import static oracle.weblogic.kubernetes.utils.FileUtils.copyFileToPod;
import static oracle.weblogic.kubernetes.utils.ThreadSafeLogger.getLogger;
import static org.awaitility.Awaitility.with;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * This test class verifies JMS Service migration with JMS messages stored in 
 * (a) Persistent FileStore (b) Persistent JDBC Store accessible from all pods.
 * The dynamic cluster is scaled down to trigger the Service migration from 
 * a stopped pod/managed server to a live pod/managed server.
 * Configuration : 
 *   MII cluster domain with 2 managed servers
 *   Two set of JMS Resources with FileStore and JDBC Store
 *   All resources are targeted to cluster with enabled JMS service migration
 *   Two Distributed Queue(s) one with FileStore and the other with JDBC Store
 *   Separate ORACLE Datasource for cluster leasing
 * UseCase :  
 * (a) Test client sends 100 messages to member queue@managed-server2
 * (b) Scale down the cluster with replica count 1 to shutdown managed-server2
 * (c) Make sure the JMS Service@managed-server2 is migrated to managed-server1
 * (d) Make sure all 100 messages got recovered once the 
 *     JMS Service@managed-server2 is migrated to managed-server1 
 * Above steps are repeated for both FileStore and JDBCStore based Distributed Queue.
 */

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("Test JMS service migration on cluster scale down")
@IntegrationTest
class ItMiiJmsRecovery {

  private static String opNamespace = null;
  private static String domainNamespace = null;
  private static ConditionFactory withStandardRetryPolicy = null;
  private static int replicaCount = 2;
  private static final String domainUid = "mii-jms-recovery";
  private static String pvName = domainUid + "-pv"; 
  private static String pvcName = domainUid + "-pvc"; 
  private static final String adminServerPodName = domainUid + "-admin-server";
  private static final String managedServerPrefix = domainUid + "-managed-server";
  private static LoggingFacade logger = null;
  private static String cpUrl;
  private static int dbNodePort;

  private final Path samplePath = Paths.get(ITTESTS_DIR, "../kubernetes/samples");
  private final Path domainLifecycleSamplePath = Paths.get(samplePath + "/scripts/domain-lifecycle");

  /**
   * Install Operator.
   * Create domain resource definition.
   * @param namespaces list of namespaces created by the IntegrationTestWatcher by the 
   *     JUnit engine parameter resolution mechanism
   */
  @BeforeAll
  public static void initAll(@Namespaces(2) List<String> namespaces) {
    logger = getLogger();
    // create standard, reusable retry/backoff policy
    withStandardRetryPolicy = with().pollDelay(2, SECONDS)
        .and().with().pollInterval(10, SECONDS)
        .atMost(5, MINUTES).await();

    // get a new unique opNamespace
    logger.info("Creating unique namespace for Operator");
    assertNotNull(namespaces.get(0), "Namespace list is null");
    opNamespace = namespaces.get(0);

    logger.info("Creating unique namespace for Domain");
    assertNotNull(namespaces.get(1), "Namespace list is null");
    domainNamespace = namespaces.get(1);
 
    // Create the repo secret to pull the image
    // this secret is used only for non-kind cluster
    createOcirRepoSecret(domainNamespace);

    //Start oracleDB
    assertDoesNotThrow(() -> {
      startOracleDB(DB_IMAGE_TO_USE_IN_SPEC, 0, domainNamespace);
      String.format("Failed to start Oracle Database Service");
    });
    dbNodePort = getDBNodePort(domainNamespace, "oracledb");
    logger.info("Oracle Database Service Node Port = {0}", dbNodePort);

    // install and verify operator
    installAndVerifyOperator(opNamespace, domainNamespace);

    // create secret for admin credentials
    logger.info("Create secret for admin credentials");
    String adminSecretName = "weblogic-credentials";
    assertDoesNotThrow(() -> createDomainSecret(adminSecretName, 
            ADMIN_USERNAME_DEFAULT, ADMIN_PASSWORD_DEFAULT, domainNamespace),
            String.format("createSecret failed for %s", adminSecretName));

    // create encryption secret
    logger.info("Create encryption secret");
    String encryptionSecretName = "encryptionsecret";
    assertDoesNotThrow(() -> createDomainSecret(encryptionSecretName, "weblogicenc",
            "weblogicenc", domainNamespace),
             String.format("createSecret failed for %s", encryptionSecretName));

    logger.info("Create database secret");
    final String dbSecretName = domainUid  + "-db-secret";
    cpUrl = "jdbc:oracle:thin:@//" + K8S_NODEPORT_HOST + ":"
                         + dbNodePort + "/devpdb.k8s";
    logger.info("ConnectionPool URL = {0}", cpUrl);
    assertDoesNotThrow(() -> createDatabaseSecret(dbSecretName, 
            "sys as sysdba", "Oradoc_db1", cpUrl, domainNamespace),
            String.format("createSecret failed for %s", dbSecretName));
    String configMapName = "jdbc-jms-recovery-configmap";

    createConfigMapAndVerify(
        configMapName, domainUid, domainNamespace,
        Arrays.asList(MODEL_DIR + "/jms.recovery.yaml"));

    // this secret is used only for non-kind cluster
    createSecretForBaseImages(domainNamespace);

    // create PV, PVC for logs/data
    createPV(pvName, domainUid, ItMiiJmsRecovery.class.getSimpleName());
    createPVC(pvName, pvcName, domainUid, domainNamespace);

    // create job to change permissions on PV hostPath
    createJobToChangePermissionsOnPvHostPath(pvName, pvcName, domainNamespace);

    // create the domain CR with a pre-defined configmap

    createDomainResourceWithLogHome(domainUid, domainNamespace,
        MII_BASIC_IMAGE_NAME + ":" + MII_BASIC_IMAGE_TAG,
        adminSecretName, OCIR_SECRET_NAME, encryptionSecretName,
        replicaCount, pvName, pvcName, "cluster-1", configMapName,
        dbSecretName, false, false, true);

    // wait for the domain to exist
    logger.info("Check for domain custom resource in namespace {0}", domainNamespace);
    withStandardRetryPolicy
        .conditionEvaluationListener(
            condition -> logger.info("Waiting for domain {0} to be created in namespace {1} "
                    + "(elapsed time {2}ms, remaining time {3}ms)",
                domainUid,
                domainNamespace,
                condition.getElapsedTimeInMS(),
                condition.getRemainingTimeInMS()))
        .until(domainExists(domainUid, DOMAIN_VERSION, domainNamespace));

    logger.info("Check admin service and pod {0} is created in namespace {1}",
        adminServerPodName, domainNamespace);
    checkPodReadyAndServiceExists(adminServerPodName, domainUid, domainNamespace);
    // create the required leasing table 'ACTIVE' before we start the cluster
    createLeasingTable(adminServerPodName, domainNamespace, dbNodePort); 
    // check managed server services and pods are ready
    for (int i = 1; i <= replicaCount; i++) {
      logger.info("Wait for managed server services and pods are created in namespace {0}",
          domainNamespace);
      checkPodReadyAndServiceExists(managedServerPrefix + i, domainUid, domainNamespace);
    }
  }

  /**
   * Verify JMS Service is migrated to an available active server.
   * Here the JMS messages are stored in Filestore on PV
   */
  @Test
  @Order(1)
  @DisplayName("Verify JMS Service migration with FileStore")
  public void testMiiJmsServiceMigrationWithFileStore() {
   
    // build the standalone JMS Client on Admin pod after rolling restart
    String destLocation = "/u01/JmsSendReceiveClient.java";
    assertDoesNotThrow(() -> copyFileToPod(domainNamespace,
        adminServerPodName, "",
        Paths.get(RESOURCE_DIR, "jms", "JmsSendReceiveClient.java"),
        Paths.get(destLocation)));
    runJavacInsidePod(adminServerPodName, domainNamespace, destLocation);
    
    assertTrue(checkJmsServerRuntime("managed-server2"), 
         "JMSService@managed-server2 is on managed-server2 before migration");

    runJmsClientOnAdminPod("send", 
            "ClusterJmsServer@managed-server2@jms.testUniformQueue");

    boolean psuccess = assertDoesNotThrow(() ->
            scaleCluster(domainUid, domainNamespace, "cluster-1", 1),
        String.format("replica patching to 1 failed for domain %s in namespace %s", domainUid, domainNamespace));
    assertTrue(psuccess,
        String.format("Cluster replica patching failed for domain %s in namespace %s", domainUid, domainNamespace));
    checkPodDoesNotExist(managedServerPrefix + "2", domainUid, domainNamespace);
    // Make sure the JMSService@managed-server2 is migrated to managed-server1
    assertTrue(checkJmsServerRuntime("managed-server1"), 
            "JMSService@managed-server2 is NOT migrated to managed-server1");
    runJmsClientOnAdminPod("receive", 
            "ClusterJmsServer@managed-server2@jms.testUniformQueue");
  }

  /**
   * Verify JMS Service is migrated to an available active server.
   * Here the JMS messages are stored in the JDBC store.
   */
  @Test
  @Order(2)
  @DisplayName("Verify JMS Service migration with JDBCStore")
  public void testMiiJmsServiceMigrationWithJdbcStore() {

    // Restart the managed server(2) if shutdown by previous test method 
    // Make sure that JMS server runtime JMSService@managed-server2 is 
    // hosted on managed server 'managed-server2'
    restartManagedServer("managed-server2");
    assertTrue(checkJmsServerRuntime("managed-server2"), 
         "JMSService@managed-server2 is on managed-server2 before migration");

    // build the standalone JMS Client on Admin pod after rolling restart
    String destLocation = "/u01/JmsSendReceiveClient.java";
    assertDoesNotThrow(() -> copyFileToPod(domainNamespace,
        adminServerPodName, "",
        Paths.get(RESOURCE_DIR, "jms", "JmsSendReceiveClient.java"),
        Paths.get(destLocation)));
    runJavacInsidePod(adminServerPodName, domainNamespace, destLocation);
    
    runJmsClientOnAdminPod("send", 
            "JdbcJmsServer@managed-server2@jms.jdbcUniformQueue");
    boolean psuccess3 = assertDoesNotThrow(() ->
            scaleCluster(domainUid, domainNamespace, "cluster-1", 1),
        String.format("replica patching to 1 failed for domain %s in namespace %s", domainUid, domainNamespace));
    assertTrue(psuccess3,
        String.format("Cluster replica patching failed for domain %s in namespace %s", domainUid, domainNamespace));
    checkPodDoesNotExist(managedServerPrefix + "2", domainUid, domainNamespace);

    assertTrue(checkJmsServerRuntime("managed-server1"), 
           "JMSService@managed-server2 is NOT migrated to managed-server1");
    runJmsClientOnAdminPod("receive", 
            "JdbcJmsServer@managed-server2@jms.jdbcUniformQueue");
  }

  private void restartManagedServer(String serverName) {

    String commonParameters = " -d " + domainUid + " -n " + domainNamespace;
    boolean result;
    CommandParams params = new CommandParams().defaults();
    String script = "startServer.sh";
    params.command("sh "
        + Paths.get(domainLifecycleSamplePath.toString(), "/" + script).toString() 
        + commonParameters + " -s " + serverName);
    result = Command.withParams(params).execute();
    assertTrue(result, "Failed to execute script " + script);
    checkPodReadyAndServiceExists(managedServerPrefix + "2", domainUid, domainNamespace);
  }

  // Run standalone JMS Client to send/receive message from 
  // Distributed Destination Member
  private void runJmsClientOnAdminPod(String action, String queue) {
    withStandardRetryPolicy
        .conditionEvaluationListener(
            condition -> logger.info("Wait for JMS Client to send/recv msg "
                    + "(elapsed time {0}ms, remaining time {1}ms)",
                condition.getElapsedTimeInMS(),
                condition.getRemainingTimeInMS()))
        .until(runClientInsidePod(adminServerPodName, domainNamespace,
            "/u01", "JmsSendReceiveClient", "t3://" + domainUid + "-cluster-cluster-1:8001", action, queue, "100"));
  }

  /*
   * Verify the JMS Server Runtime through rest API.
   * Get the JMSServer Runtime ClusterJmsServer@managed-server2 found on 
   * specified managed server.
   * @param managedServer name of managed server to look for JMSServerRuntime
   * @returns true if MBEAN is found otherwise false
   **/
  private boolean checkJmsServerRuntime(String managedServer) {
    ExecResult result = null;
    int adminServiceNodePort
        = getServiceNodePort(domainNamespace, getExternalServicePodName(adminServerPodName), "default");
    StringBuffer curlString = new StringBuffer("status=$(curl --user weblogic:welcome1 ");
    curlString.append("http://" + K8S_NODEPORT_HOST + ":" + adminServiceNodePort)
          .append("/management/weblogic/latest/domainRuntime/serverRuntimes/")
          .append(managedServer)
          .append("/JMSRuntime/JMSServers/ClusterJmsServer@managed-server2")
          .append(" --silent --show-error ")
          .append(" -o /dev/null")
          .append(" -w %{http_code});")
          .append("echo ${status}");
    logger.info("checkJmsServerRuntime: curl command {0}", new String(curlString));
    withStandardRetryPolicy 
        .conditionEvaluationListener(
            condition -> logger.info("Waiting for JMS Service to migrate "
                + "(elapsed time {0} ms, remaining time {1} ms)",
                condition.getElapsedTimeInMS(),
                condition.getRemainingTimeInMS()))
        .until(assertDoesNotThrow(() -> {
          return () -> {
            return exec(new String(curlString), true).stdout().contains("200");
          };
        }));
    return true;
  }

}

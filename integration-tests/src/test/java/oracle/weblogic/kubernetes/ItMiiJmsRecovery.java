// Copyright (c) 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

import io.kubernetes.client.custom.V1Patch;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.models.V1ConfigMap;
import io.kubernetes.client.openapi.models.V1EnvVar;
import io.kubernetes.client.openapi.models.V1Job;
import io.kubernetes.client.openapi.models.V1JobCondition;
import io.kubernetes.client.openapi.models.V1JobSpec;
import io.kubernetes.client.openapi.models.V1LocalObjectReference;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1PersistentVolumeClaimVolumeSource;
import io.kubernetes.client.openapi.models.V1Pod;
import io.kubernetes.client.openapi.models.V1PodSpec;
import io.kubernetes.client.openapi.models.V1PodTemplateSpec;
import io.kubernetes.client.openapi.models.V1Secret;
import io.kubernetes.client.openapi.models.V1SecretReference;
import io.kubernetes.client.openapi.models.V1Service;
import io.kubernetes.client.openapi.models.V1Volume;
import io.kubernetes.client.openapi.models.V1VolumeMount;
import oracle.weblogic.domain.AdminServer;
import oracle.weblogic.domain.AdminService;
import oracle.weblogic.domain.Channel;
import oracle.weblogic.domain.Cluster;
import oracle.weblogic.domain.Configuration;
import oracle.weblogic.domain.Domain;
import oracle.weblogic.domain.DomainSpec;
import oracle.weblogic.domain.Model;
import oracle.weblogic.domain.ServerPod;
import oracle.weblogic.kubernetes.actions.impl.primitive.Command;
import oracle.weblogic.kubernetes.actions.impl.primitive.CommandParams;
import oracle.weblogic.kubernetes.actions.impl.primitive.Kubernetes;
import oracle.weblogic.kubernetes.annotations.IntegrationTest;
import oracle.weblogic.kubernetes.annotations.Namespaces;
import oracle.weblogic.kubernetes.logging.LoggingFacade;
import oracle.weblogic.kubernetes.utils.ExecResult;
import org.awaitility.core.ConditionFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import static io.kubernetes.client.util.Yaml.dump;
import static java.util.concurrent.TimeUnit.MINUTES;
import static java.util.concurrent.TimeUnit.SECONDS;
import static oracle.weblogic.kubernetes.TestConstants.BASE_IMAGES_REPO_SECRET;
import static oracle.weblogic.kubernetes.TestConstants.DB_IMAGE_TO_USE_IN_SPEC;
import static oracle.weblogic.kubernetes.TestConstants.DOMAIN_API_VERSION;
import static oracle.weblogic.kubernetes.TestConstants.DOMAIN_VERSION;
import static oracle.weblogic.kubernetes.TestConstants.K8S_NODEPORT_HOST;
import static oracle.weblogic.kubernetes.TestConstants.MII_BASIC_IMAGE_NAME;
import static oracle.weblogic.kubernetes.TestConstants.MII_BASIC_IMAGE_TAG;
import static oracle.weblogic.kubernetes.TestConstants.OCIR_SECRET_NAME;
import static oracle.weblogic.kubernetes.actions.ActionConstants.ITTESTS_DIR;
import static oracle.weblogic.kubernetes.actions.ActionConstants.MODEL_DIR;
import static oracle.weblogic.kubernetes.actions.ActionConstants.RESOURCE_DIR;
import static oracle.weblogic.kubernetes.actions.ActionConstants.WORK_DIR;
import static oracle.weblogic.kubernetes.actions.TestActions.createConfigMap;
import static oracle.weblogic.kubernetes.actions.TestActions.createDomainCustomResource;
import static oracle.weblogic.kubernetes.actions.TestActions.createSecret;
import static oracle.weblogic.kubernetes.actions.TestActions.execCommand;
import static oracle.weblogic.kubernetes.actions.TestActions.getJob;
import static oracle.weblogic.kubernetes.actions.TestActions.getPodLog;
import static oracle.weblogic.kubernetes.actions.TestActions.getServiceNodePort;
import static oracle.weblogic.kubernetes.actions.TestActions.listPods;
import static oracle.weblogic.kubernetes.actions.TestActions.listServices;
import static oracle.weblogic.kubernetes.actions.TestActions.scaleCluster;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.domainExists;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.podDoesNotExist;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.checkPodDoesNotExist;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.checkPodReadyAndServiceExists;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.createConfigMapAndVerify;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.createJobAndWaitUntilComplete;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.createOcirRepoSecret;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.createPV;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.createPVC;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.createSecretForBaseImages;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.createfixPVCOwnerContainer;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.getExternalServicePodName;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.installAndVerifyOperator;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.setPodAntiAffinity;
import static oracle.weblogic.kubernetes.utils.DbUtils.startOracleDB;
import static oracle.weblogic.kubernetes.utils.ExecCommand.exec;
import static oracle.weblogic.kubernetes.utils.FileUtils.copyFileToPod;
import static oracle.weblogic.kubernetes.utils.ThreadSafeLogger.getLogger;
import static org.awaitility.Awaitility.with;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * This test class verifies JMS Service migration when a cluster is scaled 
 * down by removing the pod. The usecase verifies the JMS service on shutdown 
 * pod/server is migrated to one of the active server and the JMS messages
 * can be recovered after service migration.
 * The dynamic cluster is configured with an ORACLE leasing datasource.
 * The cluster targeted persistent store is configured with 'Dynamic' 
 * distribution policy and 'Always' migration policy.
 * The associated file store directory is on a shared volume so that it can 
 * be accessed from each managed server pod/server.
 * The associated JDBC store is on remote DB instance so that it can be 
 * accessed from each managed server pod/server.
 * An uniform distributed queue is configured with a cluster targeted JMS 
 * system resource with one member on each managed server.
 * (a) Test client sends 100 messages to member queue@managed-server2
 * (b) Scale down the cluster with replica count 1
 * (c) Make sure all 100 messages got recovered once the 
 *     JMS Service@managed-server2 is migrated to managed-server1 
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
  private StringBuffer curlString = null;
  private StringBuffer checkCluster = null;
  private V1Patch patch = null;
  private static final String adminServerPodName = domainUid + "-admin-server";
  private static final String managedServerPrefix = domainUid + "-managed-server";
  private final String adminServerName = "admin-server";
  private final String clusterName = "cluster-1";

  private static LoggingFacade logger = null;
  private static String cpUrl;
  private static int dbNodePort;

  private final Path samplePath = Paths.get(ITTESTS_DIR, "../kubernetes/samples");
  private final Path domainLifecycleSamplePath = Paths.get(samplePath + "/scripts/domain-lifecycle");

  /**
   * Install Operator.
   * Create domain resource defintion.
   * @param namespaces list of namespaces created by the IntegrationTestWatcher by the
   JUnit engine parameter resolution mechanism
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
    assertDoesNotThrow(() -> createDomainSecret(adminSecretName,"weblogic",
            "welcome1", domainNamespace),
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
    createDomainResource(domainUid, domainNamespace, adminSecretName,
        OCIR_SECRET_NAME, encryptionSecretName,
        replicaCount, configMapName, dbSecretName);

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
    createLeasingTable();
    // check managed server services and pods are ready
    for (int i = 1; i <= replicaCount; i++) {
      logger.info("Wait for managed server services and pods are created in namespace {0}",
          domainNamespace);
      checkPodReadyAndServiceExists(managedServerPrefix + i, domainUid, domainNamespace);
    }
  }

  /**
   * Verify all server pods are running.
   * Verify all k8s services for all servers are created.
   */
  @BeforeEach
  public void beforeEach() {
    logger.info("Running beforeEach() ....");
  }

  /**
   * Verify JMS Service is migrated to an available active server.
   * Here the JMS messages are stored in File store on PV
   */
  @Test
  @Order(1)
  @DisplayName("Verify JMS Service migration with FileStore")
  public void testMiiJmsServiceMigrationWithFileStore() {

    buildClientOnPod();
    runJmsClientOnAdminPod("send", 
            "ClusterJmsServer@managed-server2@jms.testUniformQueue");

    boolean psuccess = assertDoesNotThrow(() ->
            scaleCluster(domainUid, domainNamespace, "cluster-1", 1),
        String.format("replica patching to 1 failed for domain %s in namespace %s", domainUid, domainNamespace));
    assertTrue(psuccess,
        String.format("Cluster replica patching failed for domain %s in namespace %s", domainUid, domainNamespace));
    checkPodDoesNotExist(managedServerPrefix + "2", domainUid, domainNamespace);
    runJmsClientOnAdminPod("receive", 
            "ClusterJmsServer@managed-server2@jms.testUniformQueue");
  }

  /**
   * Verify JMS Service is migrated to an available active server.
   * Here the JMS messages are stored in JDBC store.
   */
  @Test
  @Order(2)
  @DisplayName("Verify JMS Service migration with JDBCStore")
  public void testMiiJmsServiceMigrationWithJdbcStore() {

    // Restart the cluster, so that JMS server runtime are hosted 
    // on the respective managed server
    restartCluster();
    buildClientOnPod();

    runJmsClientOnAdminPod("send", 
            "JdbcJmsServer@managed-server2@jms.jdbcUniformQueue");
    boolean psuccess3 = assertDoesNotThrow(() ->
            scaleCluster(domainUid, domainNamespace, "cluster-1", 1),
        String.format("replica patching to 1 failed for domain %s in namespace %s", domainUid, domainNamespace));
    assertTrue(psuccess3,
        String.format("Cluster replica patching failed for domain %s in namespace %s", domainUid, domainNamespace));
    checkPodDoesNotExist(managedServerPrefix + "2", domainUid, domainNamespace);

    runJmsClientOnAdminPod("receive", 
            "JdbcJmsServer@managed-server2@jms.jdbcUniformQueue");
  }

  // Create leasing Table (ACTIVE)
  private static void createLeasingTable() {
    Path ddlFile = Paths.get(WORK_DIR + "/leasing.ddl");
    String ddlString = "DROP TABLE ACTIVE;\n"
        + "CREATE TABLE ACTIVE (\n" 
        + "  SERVER VARCHAR2(255) NOT NULL,\n" 
        + "  INSTANCE VARCHAR2(255) NOT NULL,\n" 
        + "  DOMAINNAME VARCHAR2(255) NOT NULL,\n" 
        + "  CLUSTERNAME VARCHAR2(255) NOT NULL,\n" 
        + "  TIMEOUT DATE,\n" 
        + "  PRIMARY KEY (SERVER, DOMAINNAME, CLUSTERNAME)\n" 
        + ");\n";

    assertDoesNotThrow(() -> Files.write(ddlFile, ddlString.getBytes()));
    String destLocation = "/u01/oracle/leasing.ddl";
    assertDoesNotThrow(() -> copyFileToPod(domainNamespace,
             adminServerPodName, "",
             Paths.get(WORK_DIR, "leasing.ddl"),
             Paths.get(destLocation)));

    String jarLocation = "/u01/oracle/wlserver/server/lib/weblogic.jar";
    StringBuffer ecmd = new StringBuffer("java -cp ");
    ecmd.append(jarLocation);
    ecmd.append(" utils.Schema ");
    ecmd.append(cpUrl);
    ecmd.append(" oracle.jdbc.OracleDriver");
    ecmd.append(" -verbose ");
    ecmd.append(" -u \"sys as sysdba\"");
    ecmd.append(" -p Oradoc_db1");
    ecmd.append(" /u01/oracle/leasing.ddl");
    ExecResult execResult = assertDoesNotThrow(
        () -> execCommand(domainNamespace, adminServerPodName,
            null, true, "/bin/sh", "-c", ecmd.toString()));
    assertTrue(execResult.exitValue() == 0, "Could not poulate the Leasing Table");
  }

  private void restartCluster() {

    String commonParameters = " -d " + domainUid + " -n " + domainNamespace;
    CommandParams params;
    boolean result;
    params = new CommandParams().defaults();

    String script = "startServer.sh";
    params.command("sh "
        + Paths.get(domainLifecycleSamplePath.toString(), "/" + script).toString() 
        + commonParameters + " -s managed-server2");
    result = Command.withParams(params).execute();
    assertTrue(result, "Failed to execute script " + script);
    checkPodReadyAndServiceExists(managedServerPrefix + "2", domainUid, domainNamespace);

    script = "stopCluster.sh";
    params.command("sh "
         + Paths.get(domainLifecycleSamplePath.toString(), "/" + script).toString() 
         + commonParameters + " -c " + clusterName);
    result = Command.withParams(params).execute();
    assertTrue(result, "Failed to execute script " + script);
    for (int i = 1; i <= replicaCount; i++) {
      checkPodDoesNotExist(managedServerPrefix + i, domainUid, domainNamespace);
    }
    script = "startCluster.sh";
    params.command("sh "
         + Paths.get(domainLifecycleSamplePath.toString(), "/" + script).toString() 
         + commonParameters + " -c " + clusterName);
    result = Command.withParams(params).execute();
    assertTrue(result, "Failed to execute script " + script);
    for (int i = 1; i <= replicaCount; i++) {
      checkPodReadyAndServiceExists(managedServerPrefix + i, domainUid, domainNamespace);
    }

  }

  // Build JMS Client inside the Admin Server Pod
  private void buildClientOnPod() {

    String destLocation = "/u01/oracle/JmsSendReceiveClient.java";
    assertDoesNotThrow(() -> copyFileToPod(domainNamespace,
             adminServerPodName, "",
             Paths.get(RESOURCE_DIR, "jms", "JmsSendReceiveClient.java"),
             Paths.get(destLocation)));

    String jarLocation = "/u01/oracle/wlserver/server/lib/weblogic.jar";

    StringBuffer javacCmd = new StringBuffer("kubectl exec -n ");
    javacCmd.append(domainNamespace);
    javacCmd.append(" -it ");
    javacCmd.append(adminServerPodName);
    javacCmd.append(" -- /bin/bash -c \"");
    javacCmd.append("javac -cp ");
    javacCmd.append(jarLocation);
    javacCmd.append(" JmsSendReceiveClient.java ");
    javacCmd.append(" \"");
    logger.info("javac command {0}", javacCmd.toString());
    ExecResult result = assertDoesNotThrow(
        () -> exec(new String(javacCmd), true));
    logger.info("javac returned {0}", result.toString());
    logger.info("javac returned EXIT value {0}", result.exitValue());
    assertTrue(result.exitValue() == 0, "Client compilation fails");
  }

  private static Callable<Boolean> runClient(String javaCmd) {
    return (()  -> {
      ExecResult result = assertDoesNotThrow(() -> exec(new String(javaCmd), true));
      logger.info("java returned {0}", result.toString());
      logger.info("java returned EXIT value {0}", result.exitValue());
      return ((result.exitValue() == 0));
    });
  }

  // Run standalone JMS Client to send/receive message from 
  // Distributed Destination Member
  private void runJmsClientOnAdminPod(String action, String queue) {

    String jarLocation = "/u01/oracle/wlserver/server/lib/weblogic.jar";
    StringBuffer javapCmd = new StringBuffer("kubectl exec -n ");
    javapCmd.append(domainNamespace);
    javapCmd.append(" -it ");
    javapCmd.append(adminServerPodName);
    javapCmd.append(" -- /bin/bash -c \"");
    javapCmd.append("java -cp ");
    javapCmd.append(jarLocation);
    javapCmd.append(":.");
    javapCmd.append(" JmsSendReceiveClient ");
    javapCmd.append(" t3://");
    javapCmd.append(domainUid);
    javapCmd.append("-cluster-");
    javapCmd.append(clusterName);
    javapCmd.append(":8001 ");
    javapCmd.append(action);
    javapCmd.append(" \'");
    javapCmd.append(queue);
    javapCmd.append("\'");
    javapCmd.append(" 100");
    javapCmd.append(" \"");
    logger.info("java command to be run {0}", javapCmd.toString());
    withStandardRetryPolicy
        .conditionEvaluationListener(
            condition -> logger.info("Wait for JMS Client to send/recv msg "
                    + "(elapsed time {0}ms, remaining time {1}ms)",
                condition.getElapsedTimeInMS(),
                condition.getRemainingTimeInMS()))
        .until(runClient(new String(javapCmd)));
  }

  private static void createDatabaseSecret(
        String secretName, String username, String password, 
        String dburl, String domNamespace) throws ApiException {
    Map<String, String> secretMap = new HashMap();
    secretMap.put("username", username);
    secretMap.put("password", password);
    secretMap.put("url", dburl);
    boolean secretCreated = assertDoesNotThrow(() -> createSecret(new V1Secret()
            .metadata(new V1ObjectMeta()
                    .name(secretName)
                    .namespace(domNamespace))
            .stringData(secretMap)), "Create secret failed with ApiException");
    assertTrue(secretCreated, String.format("create secret failed for %s in namespace %s", secretName, domNamespace));

  }

  private static void createDomainSecret(String secretName, String username, String password, String domNamespace)
          throws ApiException {
    Map<String, String> secretMap = new HashMap();
    secretMap.put("username", username);
    secretMap.put("password", password);
    boolean secretCreated = assertDoesNotThrow(() -> createSecret(new V1Secret()
            .metadata(new V1ObjectMeta()
                    .name(secretName)
                    .namespace(domNamespace))
            .stringData(secretMap)), "Create secret failed with ApiException");
    assertTrue(secretCreated, String.format("create secret failed for %s in namespace %s", secretName, domNamespace));
  }

  private static void createDomainResource(
      String domainUid, String domNamespace, String adminSecretName,
      String repoSecretName, String encryptionSecretName, 
      int replicaCount, String configmapName, String dbSecretName) {
    List<String> securityList = new ArrayList<>();
    securityList.add(dbSecretName);
    // create the domain CR
    Domain domain = new Domain()
            .apiVersion(DOMAIN_API_VERSION)
            .kind("Domain")
            .metadata(new V1ObjectMeta()
                    .name(domainUid)
                    .namespace(domNamespace))
            .spec(new DomainSpec()
                    .allowReplicasBelowMinDynClusterSize(false)
                    .domainUid(domainUid)
                    .domainHomeSourceType("FromModel")
                    .image(MII_BASIC_IMAGE_NAME + ":" + MII_BASIC_IMAGE_TAG)
                    .addImagePullSecretsItem(new V1LocalObjectReference()
                            .name(repoSecretName))
                    .webLogicCredentialsSecret(new V1SecretReference()
                            .name(adminSecretName)
                            .namespace(domNamespace))
                    .includeServerOutInPodLog(true)
                    .logHomeEnabled(Boolean.TRUE)
                    .logHome("/shared/logs")
                    .dataHome("/shared/data")
                    .serverStartPolicy("IF_NEEDED")
                    .serverPod(new ServerPod()
                            .addEnvItem(new V1EnvVar()
                                    .name("JAVA_OPTIONS")
                                    .value("-Dweblogic.StdoutDebugEnabled=false"))
                            .addEnvItem(new V1EnvVar()
                                    .name("USER_MEM_ARGS")
                                    .value("-Djava.security.egd=file:/dev/./urandom "))
                            .addVolumesItem(new V1Volume()
                                    .name(pvName)
                                    .persistentVolumeClaim(new V1PersistentVolumeClaimVolumeSource()
                                        .claimName(pvcName)))
                            .addVolumeMountsItem(new V1VolumeMount()
                                .mountPath("/shared")
                                .name(pvName)))
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
                            .secrets(securityList)
                            .model(new Model()
                                    .domainType("WLS")
                                    .configMap(configmapName)
                                    .runtimeEncryptionSecret(encryptionSecretName))
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

  private void checkPodNotCreated(String podName, String domainUid, String domNamespace) {
    withStandardRetryPolicy
        .conditionEvaluationListener(
            condition -> logger.info("Waiting for pod {0} to be not created in namespace {1} "
                    + "(elapsed time {2}ms, remaining time {3}ms)",
                podName,
                domNamespace,
                condition.getElapsedTimeInMS(),
                condition.getRemainingTimeInMS()))
        .until(assertDoesNotThrow(() -> podDoesNotExist(podName, domainUid, domNamespace),
            String.format("podDoesNotExist failed with ApiException for %s in namespace in %s",
                podName, domNamespace)));
  }

  /*
   * Verify the server MBEAN configuration through rest API.
   * @param managedServer name of the managed server
   * @returns true if MBEAN is found otherwise false
   **/
  private boolean checkManagedServerConfiguration(String managedServer) {
    ExecResult result = null;
    int adminServiceNodePort
        = getServiceNodePort(domainNamespace, getExternalServicePodName(adminServerPodName), "default");
    checkCluster = new StringBuffer("status=$(curl --user weblogic:welcome1 ");
    checkCluster.append("http://" + K8S_NODEPORT_HOST + ":" + adminServiceNodePort)
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
    if (result.stdout().equals("200")) {
      return true;
    } else {
      return false;
    }
  }

  // Crate a ConfigMap with a model file to add a new WebLogic cluster
  private void createClusterConfigMap(String configMapName, String modelFile) {
    Map<String, String> labels = new HashMap<>();
    labels.put("weblogic.domainUid", domainUid);
    String dsModelFile =  String.format("%s/%s", MODEL_DIR,modelFile);
    Map<String, String> data = new HashMap<>();
    String cmData = null;
    cmData = assertDoesNotThrow(() -> Files.readString(Paths.get(dsModelFile)),
        String.format("readString operation failed for %s", dsModelFile));
    assertNotNull(cmData, String.format("readString() operation failed while creating ConfigMap %s", configMapName));
    data.put(modelFile, cmData);

    V1ObjectMeta meta = new V1ObjectMeta()
        .labels(labels)
        .name(configMapName)
        .namespace(domainNamespace);
    V1ConfigMap configMap = new V1ConfigMap()
        .data(data)
        .metadata(meta);

    boolean cmCreated = assertDoesNotThrow(() -> createConfigMap(configMap),
        String.format("Can't create ConfigMap %s", configMapName));
    assertTrue(cmCreated, String.format("createConfigMap failed while creating ConfigMap %s", configMapName));
  }

  private ExecResult checkJdbcRuntime(String resourcesName) {
    int adminServiceNodePort
        = getServiceNodePort(domainNamespace, getExternalServicePodName(adminServerPodName), "default");
    ExecResult result = null;

    curlString = new StringBuffer("curl --user weblogic:welcome1 ");
    curlString.append("http://" + K8S_NODEPORT_HOST + ":" + adminServiceNodePort)
         .append("/management/wls/latest/datasources/id/")
         .append(resourcesName)
         .append("/")
         .append(" --silent --show-error ");
    logger.info("checkJdbcRuntime: curl command {0}", new String(curlString));
    try {
      result = exec(new String(curlString), true);
    } catch (Exception ex) {
      logger.info("checkJdbcRuntime: caught unexpected exception {0}", ex);
      return null;
    }
    return result;
  }

  private static void createJobToChangePermissionsOnPvHostPath(String pvName, String pvcName, String namespace) {
    logger.info("Running Kubernetes job to create domain");
    V1Job jobBody = new V1Job()
        .metadata(
            new V1ObjectMeta()
                .name("change-permissions-onpv-job-" + pvName) // name of the job
                .namespace(namespace))
        .spec(new V1JobSpec()
            .backoffLimit(0) // try only once
            .template(new V1PodTemplateSpec()
                .spec(new V1PodSpec()
                    .restartPolicy("Never")
                    .addContainersItem(
                        createfixPVCOwnerContainer(pvName,
                        "/shared")) // mounted under /shared inside pod
                    .volumes(Arrays.asList(
                        new V1Volume()
                            .name(pvName)
                            .persistentVolumeClaim(
                                new V1PersistentVolumeClaimVolumeSource()
                                    .claimName(pvcName))))
                    .imagePullSecrets(Arrays.asList(
                        new V1LocalObjectReference()
                            .name(BASE_IMAGES_REPO_SECRET)))))); // this secret is used only for non-kind cluster

    String jobName = createJobAndWaitUntilComplete(jobBody, namespace);

    // check job status and fail test if the job failed
    V1Job job = assertDoesNotThrow(() -> getJob(jobName, namespace),
        "Getting the job failed");
    if (job != null) {
      V1JobCondition jobCondition = job.getStatus().getConditions().stream().filter(
          v1JobCondition -> "Failed".equalsIgnoreCase(v1JobCondition.getType()))
          .findAny()
          .orElse(null);
      if (jobCondition != null) {
        logger.severe("Job {0} failed to change permissions on PV hostpath", jobName);
        List<V1Pod> pods = assertDoesNotThrow(() -> listPods(
            namespace, "job-name=" + jobName).getItems(),
            "Listing pods failed");
        if (!pods.isEmpty()) {
          String podLog = assertDoesNotThrow(() -> getPodLog(pods.get(0).getMetadata().getName(), namespace),
              "Failed to get pod log");
          logger.severe(podLog);
          fail("Change permissions on PV hostpath job failed");
        }
      }
    }
  }

  private void checkLogsOnPV(String commandToExecuteInsidePod, String podName) {
    logger.info("Checking logs are written on PV by running the command {0} on pod {1}, namespace {2}",
        commandToExecuteInsidePod, podName, domainNamespace);
    V1Pod serverPod = assertDoesNotThrow(() ->
            Kubernetes.getPod(domainNamespace, null, podName),
        String.format("Could not get the server Pod {0} in namespace {1}",
            podName, domainNamespace));

    ExecResult result = assertDoesNotThrow(() -> Kubernetes.exec(serverPod, null, true,
        "/bin/sh", "-c", commandToExecuteInsidePod),
        String.format("Could not execute the command %s in pod %s, namespace %s",
            commandToExecuteInsidePod, podName, domainNamespace));
    logger.info("Command {0} returned with exit value {1}, stderr {2}, stdout {3}",
        commandToExecuteInsidePod, result.exitValue(), result.stderr(), result.stdout());

    // checking for exitValue 0 for success fails sometimes as k8s exec api returns non-zero exit value even on success,
    // so checking for exitValue non-zero and stderr not empty for failure, otherwise its success
    assertFalse(result.exitValue() != 0 && result.stderr() != null && !result.stderr().isEmpty(),
        String.format("Command %s failed with exit value %s, stderr %s, stdout %s",
            commandToExecuteInsidePod, result.exitValue(), result.stderr(), result.stdout()));

  }

  private static Integer getDBNodePort(String namespace, String dbName) {
    logger.info(dump(Kubernetes.listServices(namespace)));
    List<V1Service> services = listServices(namespace).getItems();
    for (V1Service service : services) {
      if (service.getMetadata().getName().startsWith(dbName)) {
        return service.getSpec().getPorts().get(0).getNodePort();
      }
    }
    return -1;
  }

}

// Copyright (c) 2022, 2024, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.models.V1EnvVar;
import io.kubernetes.client.openapi.models.V1LocalObjectReference;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1PersistentVolumeClaimVolumeSource;
import io.kubernetes.client.openapi.models.V1Volume;
import io.kubernetes.client.openapi.models.V1VolumeMount;
import oracle.weblogic.domain.ClusterResource;
import oracle.weblogic.domain.Configuration;
import oracle.weblogic.domain.DomainResource;
import oracle.weblogic.domain.DomainSpec;
import oracle.weblogic.domain.Model;
import oracle.weblogic.domain.OnlineUpdate;
import oracle.weblogic.domain.ServerPod;
import oracle.weblogic.kubernetes.actions.impl.primitive.Command;
import oracle.weblogic.kubernetes.actions.impl.primitive.CommandParams;
import oracle.weblogic.kubernetes.annotations.IntegrationTest;
import oracle.weblogic.kubernetes.annotations.Namespaces;
import oracle.weblogic.kubernetes.logging.LoggingFacade;
import oracle.weblogic.kubernetes.utils.ExecResult;
import oracle.weblogic.kubernetes.utils.OracleHttpClient;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import static oracle.weblogic.kubernetes.TestConstants.ADMIN_PASSWORD_DEFAULT;
import static oracle.weblogic.kubernetes.TestConstants.ADMIN_USERNAME_DEFAULT;
import static oracle.weblogic.kubernetes.TestConstants.DOMAIN_API_VERSION;
import static oracle.weblogic.kubernetes.TestConstants.DOMAIN_VERSION;
import static oracle.weblogic.kubernetes.TestConstants.ENCRYPION_PASSWORD_DEFAULT;
import static oracle.weblogic.kubernetes.TestConstants.ENCRYPION_USERNAME_DEFAULT;
import static oracle.weblogic.kubernetes.TestConstants.IMAGE_PULL_POLICY;
import static oracle.weblogic.kubernetes.TestConstants.ISTIO_HTTP_HOSTPORT;
import static oracle.weblogic.kubernetes.TestConstants.MII_BASIC_IMAGE_NAME;
import static oracle.weblogic.kubernetes.TestConstants.MII_BASIC_IMAGE_TAG;
import static oracle.weblogic.kubernetes.TestConstants.SKIP_CLEANUP;
import static oracle.weblogic.kubernetes.TestConstants.TEST_IMAGES_REPO_SECRET_NAME;
import static oracle.weblogic.kubernetes.actions.ActionConstants.ITTESTS_DIR;
import static oracle.weblogic.kubernetes.actions.ActionConstants.MODEL_DIR;
import static oracle.weblogic.kubernetes.actions.ActionConstants.RESOURCE_DIR;
import static oracle.weblogic.kubernetes.actions.ActionConstants.WORK_DIR;
import static oracle.weblogic.kubernetes.actions.TestActions.addLabelsToNamespace;
import static oracle.weblogic.kubernetes.actions.TestActions.createDomainCustomResource;
import static oracle.weblogic.kubernetes.actions.TestActions.execCommand;
import static oracle.weblogic.kubernetes.actions.TestActions.scaleCluster;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.domainExists;
import static oracle.weblogic.kubernetes.utils.ClusterUtils.createClusterAndVerify;
import static oracle.weblogic.kubernetes.utils.ClusterUtils.createClusterResource;
import static oracle.weblogic.kubernetes.utils.CommonMiiTestUtils.createDatabaseSecret;
import static oracle.weblogic.kubernetes.utils.CommonMiiTestUtils.createDomainSecret;
import static oracle.weblogic.kubernetes.utils.CommonMiiTestUtils.createJobToChangePermissionsOnPvHostPath;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.checkPodReadyAndServiceExists;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.createTestWebAppWarFile;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.formatIPv6Host;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.getHostAndPort;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.getServiceExtIPAddrtOke;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.getUniqueName;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.runClientInsidePod;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.runJavacInsidePod;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.testUntil;
import static oracle.weblogic.kubernetes.utils.ConfigMapUtils.createConfigMapAndVerify;
import static oracle.weblogic.kubernetes.utils.DbUtils.createOracleDBUsingOperator;
import static oracle.weblogic.kubernetes.utils.DbUtils.deleteOracleDB;
import static oracle.weblogic.kubernetes.utils.FileUtils.copyFileToPod;
import static oracle.weblogic.kubernetes.utils.FileUtils.generateFileFromTemplate;
import static oracle.weblogic.kubernetes.utils.ImageUtils.createBaseRepoSecret;
import static oracle.weblogic.kubernetes.utils.ImageUtils.createTestRepoSecret;
import static oracle.weblogic.kubernetes.utils.IstioUtils.deployHttpIstioGatewayAndVirtualservice;
import static oracle.weblogic.kubernetes.utils.IstioUtils.deployIstioDestinationRule;
import static oracle.weblogic.kubernetes.utils.IstioUtils.getIstioHttpIngressPort;
import static oracle.weblogic.kubernetes.utils.OKDUtils.createRouteForOKD;
import static oracle.weblogic.kubernetes.utils.OperatorUtils.installAndVerifyOperator;
import static oracle.weblogic.kubernetes.utils.PersistentVolumeUtils.createPV;
import static oracle.weblogic.kubernetes.utils.PersistentVolumeUtils.createPVC;
import static oracle.weblogic.kubernetes.utils.PodUtils.checkPodDoesNotExist;
import static oracle.weblogic.kubernetes.utils.PodUtils.getExternalServicePodName;
import static oracle.weblogic.kubernetes.utils.PodUtils.setPodAntiAffinity;
import static oracle.weblogic.kubernetes.utils.ThreadSafeLogger.getLogger;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("Test to a create Istio enabled FMW model in image domain and WebLogic domain using Oracle "
    + "database created using Oracle Database Operator")
@IntegrationTest
@Tag("oke-sequential")
@Tag("kind-sequential")
class ItIstioDBOperator {

  private static String dbNamespace = null;
  private static String opNamespace = null;
  private static String wlsDomainNamespace = null;

  private static final String RCUSYSPASSWORD = "Oradoc_db1";

  private static String dbUrl = null;
  private static String dbName = "istio-oracle-sidb";
  private static LoggingFacade logger = null;

  private String clusterName = "cluster-1";
  private int replicaCount = 2;

  private static final String wlsDomainUid = "mii-jms-istio-db";
  private static final String pvName = getUniqueName(wlsDomainUid + "-pv-");
  private static final String pvcName = getUniqueName(wlsDomainUid + "-pvc-");
  private static final String wlsAdminServerPodName = wlsDomainUid + "-admin-server";
  private static final String wlsManagedServerPrefix = wlsDomainUid + "-managed-server";
  private static int wlDomainIstioIngressPort;
  private static String cpUrl;
  private static String adminSvcExtRouteHost = null;

  private final Path samplePath = Paths.get(ITTESTS_DIR, "../kubernetes/samples");
  private final Path domainLifecycleSamplePath = Paths.get(samplePath + "/scripts/domain-lifecycle");

  private static String testWebAppWarLoc = null;

  private static String hostHeader;
  Map<String, String> httpHeaders;

  private static final String istioNamespace = "istio-system";
  private static final String istioIngressServiceName = "istio-ingressgateway";

  /**
   * Start DB service and create RCU schema.
   * Assigns unique namespaces for operator and domains.
   * Installs operator.
   *
   * @param namespaces injected by JUnit
   */
  @BeforeAll
  public static void initAll(@Namespaces(4) List<String> namespaces) {

    logger = getLogger();
    logger.info("Assign a unique namespace for DB and RCU");
    assertNotNull(namespaces.get(0), "Namespace is null");
    dbNamespace = namespaces.get(0);

    logger.info("Assign a unique namespace for operator");
    assertNotNull(namespaces.get(1), "Namespace is null");
    opNamespace = namespaces.get(1);

    logger.info("Assign a unique namespace for WLS domain");
    assertNotNull(namespaces.get(2), "Namespace is null");
    wlsDomainNamespace = namespaces.get(2);

    // Create the repo secret to pull the image
    // this secret is used only for non-kind cluster
    createBaseRepoSecret(wlsDomainNamespace);
    createTestRepoSecret(wlsDomainNamespace);
    // create PV, PVC for logs/data
    createPV(pvName, wlsDomainUid, ItIstioDBOperator.class.getSimpleName());
    createPVC(pvName, pvcName, wlsDomainUid, wlsDomainNamespace);

    // create job to change permissions on PV hostPath
    createJobToChangePermissionsOnPvHostPath(pvName, pvcName, wlsDomainNamespace);

    // Label the domain/operator namespace with istio-injection=enabled
    Map<String, String> labelMap = new HashMap<>();
    labelMap.put("istio-injection", "enabled");
    assertDoesNotThrow(() -> addLabelsToNamespace(wlsDomainNamespace, labelMap));
    assertDoesNotThrow(() -> addLabelsToNamespace(opNamespace, labelMap));

    logger.info("Create Oracle DB in namespace: {0} ", dbNamespace);
    createBaseRepoSecret(dbNamespace);
    dbUrl = assertDoesNotThrow(() -> createOracleDBUsingOperator(dbName, RCUSYSPASSWORD, dbNamespace));
    
    // create testwebapp.war
    testWebAppWarLoc = createTestWebAppWarFile(wlsDomainNamespace);

    // install operator and verify its running in ready state
    installAndVerifyOperator(opNamespace, wlsDomainNamespace);
  }

  /**
   * Create Istio enabled WebLogic domain using model in image and Oracle database used for JMS and JTA
   * migration and service logs.
   */
  @Test
  void  testIstioWlsModelInImageWithDbOperator() throws UnknownHostException {

    // Create the repo secret to pull the image
    // this secret is used only for non-kind cluster

    // create secret for admin credentials
    logger.info("Create secret for admin credentials");
    String adminSecretName = "weblogic-credentials";
    assertDoesNotThrow(() -> createDomainSecret(adminSecretName,
        ADMIN_USERNAME_DEFAULT, ADMIN_PASSWORD_DEFAULT, wlsDomainNamespace),
        String.format("createSecret failed for %s", adminSecretName));

    // create encryption secret
    logger.info("Create encryption secret");
    String encryptionSecretName = "encryptionsecret";
    assertDoesNotThrow(() -> createDomainSecret(encryptionSecretName, ENCRYPION_USERNAME_DEFAULT,
        ENCRYPION_PASSWORD_DEFAULT, wlsDomainNamespace),
        String.format("createSecret failed for %s", encryptionSecretName));

    logger.info("Create database secret");
    final String dbSecretName = wlsDomainUid + "-db-secret";

    cpUrl = "jdbc:oracle:thin:@//" + dbUrl;
    logger.info("ConnectionPool URL = {0}", cpUrl);
    assertDoesNotThrow(() -> createDatabaseSecret(dbSecretName,
        "sys as sysdba", "Oradoc_db1", cpUrl, wlsDomainNamespace),
        String.format("createSecret failed for %s", dbSecretName));
    String configMapName = "jdbc-jms-recovery-configmap";

    createConfigMapAndVerify(configMapName, wlsDomainUid, wlsDomainNamespace,
        Arrays.asList(MODEL_DIR + "/jms.recovery.yaml"));

    // create the domain CR with a pre-defined configmap
    createDomainResourceWithLogHome(wlsDomainUid, wlsDomainNamespace,
        MII_BASIC_IMAGE_NAME + ":" + MII_BASIC_IMAGE_TAG,
        adminSecretName, TEST_IMAGES_REPO_SECRET_NAME, encryptionSecretName,
        replicaCount, pvName, pvcName, "cluster-1", configMapName,
        dbSecretName, false, true);

    // wait for the domain to exist
    logger.info("Check for domain custom resource in namespace {0}", wlsDomainNamespace);
    testUntil(domainExists(wlsDomainUid, DOMAIN_VERSION, wlsDomainNamespace),
        logger,
        "domain {0} to be created in namespace {1}",
        wlsDomainUid,
        wlsDomainNamespace);

    logger.info("Check admin service and pod {0} is created in namespace {1}",
        wlsAdminServerPodName, wlsDomainNamespace);
    checkPodReadyAndServiceExists(wlsAdminServerPodName, wlsDomainUid, wlsDomainNamespace);

    adminSvcExtRouteHost = createRouteForOKD(getExternalServicePodName(wlsAdminServerPodName), wlsDomainNamespace);
    // create the required leasing table 'ACTIVE' before we start the cluster
    createLeasingTable(wlsAdminServerPodName, wlsDomainNamespace, dbUrl);
    // check managed server services and pods are ready
    for (int i = 1; i <= replicaCount; i++) {
      logger.info("Wait for managed server services and pods are created in namespace {0}",
          wlsDomainNamespace);
      checkPodReadyAndServiceExists(wlsManagedServerPrefix + i, wlsDomainUid, wlsDomainNamespace);
    }

    wlDomainIstioIngressPort = enableIstio("cluster-1", wlsDomainUid, wlsDomainNamespace, wlsAdminServerPodName);
    logger.info("Istio Ingress Port is {0}", wlDomainIstioIngressPort);

    hostHeader = wlsDomainNamespace + ".org";

    //Verify JMS/JTA Service migration with File(JDBC) Store
    testMiiJmsJtaServiceMigration();
  }

  /**
   * Verify JMS/JTA Service is migrated to an available active server.
   */
  private void testMiiJmsJtaServiceMigration() throws UnknownHostException {
    
    httpHeaders = new HashMap<>();
    httpHeaders.put("host", wlsDomainNamespace + ".org");
    httpHeaders.put("Authorization", ADMIN_USERNAME_DEFAULT + ":" + ADMIN_PASSWORD_DEFAULT);
    
    // build the standalone JMS Client on Admin pod
    String destLocation = "/u01/JmsSendReceiveClient.java";
    assertDoesNotThrow(() -> copyFileToPod(wlsDomainNamespace,
        wlsAdminServerPodName, "",
        Paths.get(RESOURCE_DIR, "jms", "JmsSendReceiveClient.java"),
        Paths.get(destLocation)));
    runJavacInsidePod(wlsAdminServerPodName, wlsDomainNamespace, destLocation);

    assertTrue(checkJmsServerRuntime("ClusterJmsServer@managed-server2",
        "managed-server2"),
        "ClusterJmsServer@managed-server2 is not on managed-server2 before migration");

    assertTrue(checkJmsServerRuntime("JdbcJmsServer@managed-server2",
        "managed-server2"),
        "JdbcJmsServer@managed-server2 is not on managed-server2 before migration");

    assertTrue(checkJtaRecoveryServiceRuntime("managed-server2",
        "managed-server2", "true"),
        "JTARecoveryService@managed-server2 is not on managed-server2 before migration");

    // Send persistent messages to both FileStore and JDBCStore based
    // JMS Destination (Queue)
    runJmsClientOnAdminPod("send",
        "ClusterJmsServer@managed-server2@jms.testUniformQueue");
    runJmsClientOnAdminPod("send",
        "JdbcJmsServer@managed-server2@jms.jdbcUniformQueue");

    // Scale down the cluster to repilca count of 1, this will shutdown
    // the managed server managed-server2 in the cluster to trigger
    // JMS/JTA Service Migration.
    boolean psuccess = scaleCluster(wlsDomainUid + "-cluster-1", wlsDomainNamespace, 1);
    assertTrue(psuccess,
        String.format("Cluster replica patching failed for domain %s in namespace %s",
            wlsDomainUid, wlsDomainNamespace));
    checkPodDoesNotExist(wlsManagedServerPrefix + "2", wlsDomainUid, wlsDomainNamespace);

    // Make sure the ClusterJmsServer@managed-server2 and
    // JdbcJmsServer@managed-server2 are migrated to managed-server1
    assertTrue(checkJmsServerRuntime("ClusterJmsServer@managed-server2",
        "managed-server1"),
        "ClusterJmsServer@managed-server2 is NOT migrated to managed-server1");
    logger.info("ClusterJmsServer@managed-server2 is migrated to managed-server1");

    assertTrue(checkJmsServerRuntime("JdbcJmsServer@managed-server2",
        "managed-server1"),
        "JdbcJmsServer@managed-server2 is NOT migrated to managed-server1");
    logger.info("JdbcJmsServer@managed-server2 is migrated to managed-server1");

    assertTrue(checkStoreRuntime("ClusterFileStore@managed-server2",
        "managed-server1"),
        "ClusterFileStore@managed-server2 is NOT migrated to managed-server1");
    logger.info("ClusterFileStore@managed-server2 is migrated to managed-server1");

    assertTrue(checkStoreRuntime("ClusterJdbcStore@managed-server2",
        "managed-server1"),
        "JdbcStore@managed-server2 is NOT migrated to managed-server1");
    logger.info("JdbcStore@managed-server2 is migrated to managed-server1");

    assertTrue(checkJtaRecoveryServiceRuntime("managed-server1",
        "managed-server2", "true"), "JTA RecoveryService@managed-server2 is not migrated to managed-server1");
    logger.info("JTA RecoveryService@managed-server2 is migrated to managed-server1");

    runJmsClientOnAdminPod("receive",
        "ClusterJmsServer@managed-server2@jms.testUniformQueue");
    runJmsClientOnAdminPod("receive",
        "JdbcJmsServer@managed-server2@jms.jdbcUniformQueue");

    // Restart the managed server(2) to make sure the JTA Recovery Service is
    // migrated back to original hosting server
    restartManagedServer("managed-server2");
    assertTrue(checkJtaRecoveryServiceRuntime("managed-server2",
        "managed-server2", "true"),
        "JTARecoveryService@managed-server2 is not on managed-server2 after restart");
    logger.info("JTA RecoveryService@managed-server2 is migrated back to managed-server1");
    assertTrue(checkJtaRecoveryServiceRuntime("managed-server1",
        "managed-server2", "false"),
        "JTARecoveryService@managed-server2 is not deactivated on managed-server1 after restart");
    logger.info("JTA RecoveryService@managed-server2 is deactivated on managed-server1 after restart");

    assertTrue(checkStoreRuntime("ClusterFileStore@managed-server2",
        "managed-server2"),
        "FileStore@managed-server2 is NOT migrated back to managed-server2");
    logger.info("FileStore@managed-server2 is migrated back to managed-server2");
    assertTrue(checkStoreRuntime("ClusterJdbcStore@managed-server2",
        "managed-server2"),
        "JdbcStore@managed-server2 is NOT migrated back to managed-server2");
    logger.info("JdbcStore@managed-server2 is migrated back to managed-server2");

    assertTrue(checkJmsServerRuntime("ClusterJmsServer@managed-server2",
        "managed-server2"),
        "ClusterJmsServer@managed-server2 is NOT migrated back to to managed-server2");
    logger.info("ClusterJmsServer@managed-server2 is migrated back to managed-server2");
    assertTrue(checkJmsServerRuntime("JdbcJmsServer@managed-server2",
        "managed-server2"),
        "JdbcJmsServer@managed-server2 is NOT migrated back to managed-server2");
    logger.info("JdbcJmsServer@managed-server2 is migrated back to managed-server2");
  }

  /**
   * Uninstall DB operator and delete DB instance.
   * The cleanup framework does not uninstall DB operator, delete DB instance and storageclass.
   * Deletes Oracle database instance, operator and storageclass.
   */
  @AfterAll
  public void tearDownAll() throws ApiException {
    if (!SKIP_CLEANUP) {
      deleteOracleDB(dbNamespace, dbName);
    }
  }


  // Restart the managed-server
  private void restartManagedServer(String serverName) {
    String commonParameters = " -d " + wlsDomainUid + " -n " + wlsDomainNamespace;
    boolean result;
    CommandParams params = new CommandParams().defaults();
    String script = "startServer.sh";
    params.command("sh "
        + Paths.get(domainLifecycleSamplePath.toString(), "/" + script).toString()
        + commonParameters + " -s " + serverName);
    result = Command.withParams(params).execute();
    assertTrue(result, "Failed to execute script " + script);
    checkPodReadyAndServiceExists(wlsManagedServerPrefix + "2", wlsDomainUid, wlsDomainNamespace);
  }

  // Run standalone JMS Client to send/receive message from
  // Distributed Destination Member
  private void runJmsClientOnAdminPod(String action, String queue) {
    testUntil(
        runClientInsidePod(wlsAdminServerPodName, wlsDomainNamespace,
            "/u01", "JmsSendReceiveClient",
            "t3://" + wlsDomainUid + "-cluster-cluster-1:8001", action, queue, "100"),
        logger,
        "Wait for JMS Client to send/recv msg");
  }

  /*
   * Verify the JMS Server Runtime through REST API.
   * Get the specific JMSServer Runtime on specified managed server.
   * @param jmsServer name of the JMSServerRuntime to look for
   * @param managedServer name of the managed server to look for JMSServerRuntime
   * @returns true if MBean is found otherwise false
   **/
  private boolean checkJmsServerRuntime(String jmsServer, String managedServer) throws UnknownHostException {
    // In internal OKE env, use Istio EXTERNAL-IP; in non-OKE env, use K8S_NODEPORT_HOST + ":" + istioIngressPort
    String hostAndPort = getServiceExtIPAddrtOke(istioIngressServiceName, istioNamespace) != null
        ? getServiceExtIPAddrtOke(istioIngressServiceName, istioNamespace)
            : getHostAndPort(adminSvcExtRouteHost, wlDomainIstioIngressPort);

    if (!TestConstants.WLSIMG_BUILDER.equals(TestConstants.WLSIMG_BUILDER_DEFAULT)) {
      hostAndPort = formatIPv6Host(InetAddress.getLocalHost().getHostAddress()) + ":" + ISTIO_HTTP_HOSTPORT;
    }
    String url = "http://" + hostAndPort + "/management/weblogic/latest/domainRuntime/serverRuntimes/"
        + managedServer + "/JMSRuntime/JMSServers/" + jmsServer;
    logger.info("Waiting for JMS Server service to migrate");
    checkApp(url, httpHeaders);
    return true;
  }

  /*
   * Verify the Persistent Store Runtimes through REST API.
   * Get the specific Persistent Store Runtime on specified managed server.
   * @param storeName name of the PersistentStore Runtime to look for
   * @param managedServer name of the managed server to look for StoreRuntime
   * @returns true if MBean is found otherwise false
   **/
  private boolean checkStoreRuntime(String storeName, String managedServer) throws UnknownHostException {
    String hostAndPort = getServiceExtIPAddrtOke(istioIngressServiceName, istioNamespace) != null
        ? getServiceExtIPAddrtOke(istioIngressServiceName, istioNamespace)
            : getHostAndPort(adminSvcExtRouteHost, wlDomainIstioIngressPort);

    if (!TestConstants.WLSIMG_BUILDER.equals(TestConstants.WLSIMG_BUILDER_DEFAULT)) {
      hostAndPort = formatIPv6Host(InetAddress.getLocalHost().getHostAddress()) + ":" + ISTIO_HTTP_HOSTPORT;
    }
    logger.info("PersistentStoreRuntimes Service to migrate");
    String url = "http://" + hostAndPort + "/management/weblogic/latest/domainRuntime/serverRuntimes/"
        + managedServer + "/persistentStoreRuntimes/" + storeName;
    checkApp(url, httpHeaders);
    return true;
  }

  /*
   * Verify the JTA Recovery Service Runtime through REST API.
   * Get the JTA Recovery Service Runtime for a server on a
   * specified managed server.
   * @param managedServer name of the server to look for RecoveyServerRuntime
   * @param recoveryService name of RecoveyServerRuntime (managed server)
   * @param active is the recovery active (true or false )
   * @returns true if MBean is found otherwise false
   **/
  private boolean checkJtaRecoveryServiceRuntime(String managedServer,
                                                 String recoveryService,
                                                 String active) throws UnknownHostException {
    
    String hostAndPort = getServiceExtIPAddrtOke(istioIngressServiceName, istioNamespace) != null
        ? getServiceExtIPAddrtOke(istioIngressServiceName, istioNamespace)
        : getHostAndPort(adminSvcExtRouteHost, wlDomainIstioIngressPort);

    if (!TestConstants.WLSIMG_BUILDER.equals(TestConstants.WLSIMG_BUILDER_DEFAULT)) {
      hostAndPort = formatIPv6Host(InetAddress.getLocalHost().getHostAddress()) + ":" + ISTIO_HTTP_HOSTPORT;
    }
    logger.info("JTA Recovery Service to migrate");
    String url = "http://" + hostAndPort + "/management/weblogic/latest/domainRuntime/serverRuntimes/"
        + managedServer + "/JTARuntime/recoveryRuntimeMBeans/" + recoveryService + "?fields=active&links=none";
    checkApp(url, httpHeaders, "\"active\": " + active);
    return true;
  }

  /**
   * Create leasing Table (ACTIVE) on an Oracle DB Instance. Uses the WebLogic utility utils.Schema to add the table So
   * the command MUST be run inside a Weblogic Server pod.
   *
   * @param wlPodName the pod name
   * @param namespace the namespace in which WebLogic pod exists
   * @param dbUrl Oracle database url
   */
  public static void createLeasingTable(String wlPodName, String namespace, String dbUrl) {
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
    String destLocation = "/u01/leasing.ddl";
    assertDoesNotThrow(() -> copyFileToPod(namespace,
        wlPodName, "",
        Paths.get(WORK_DIR, "leasing.ddl"),
        Paths.get(destLocation)));

    //String cpUrl = "jdbc:oracle:thin:@//" + K8S_NODEPORT_HOST + ":"
    String cpUrl = "jdbc:oracle:thin:@//" + dbUrl;
    String jarLocation = "/u01/oracle/wlserver/server/lib/weblogic.jar";
    StringBuffer ecmd = new StringBuffer("java -cp ");
    ecmd.append(jarLocation);
    ecmd.append(" utils.Schema ");
    ecmd.append(cpUrl);
    ecmd.append(" oracle.jdbc.OracleDriver");
    ecmd.append(" -verbose ");
    ecmd.append(" -u \"sys as sysdba\"");
    ecmd.append(" -p Oradoc_db1");
    ecmd.append(" /u01/leasing.ddl");
    ExecResult execResult = assertDoesNotThrow(() -> execCommand(namespace, wlPodName,
        null, true, "/bin/sh", "-c", ecmd.toString()));
    assertEquals(0, execResult.exitValue(), "Could not create the Leasing Table");
  }

  private int enableIstio(String clusterName, String domainUid, String namespace, String adminServerPodName) {

    String clusterService = domainUid + "-cluster-" + clusterName + "." + namespace + ".svc.cluster.local";

    Map<String, String> templateMap = new HashMap<>();
    templateMap.put("NAMESPACE", namespace);
    templateMap.put("DUID", domainUid);
    templateMap.put("ADMIN_SERVICE", adminServerPodName);
    templateMap.put("CLUSTER_SERVICE", clusterService);
    templateMap.put("MANAGED_SERVER_PORT", "7001");

    Path srcHttpFile = Paths.get(RESOURCE_DIR, "istio", "istio-http-template.yaml");
    Path targetHttpFile = assertDoesNotThrow(
        () -> generateFileFromTemplate(srcHttpFile.toString(), "istio-http.yaml", templateMap));
    logger.info("Generated Http VS/Gateway file path is {0}", targetHttpFile);

    boolean deployRes = assertDoesNotThrow(
        () -> deployHttpIstioGatewayAndVirtualservice(targetHttpFile));
    assertTrue(deployRes, "Failed to deploy Http Istio Gateway/VirtualService");

    Path srcDrFile = Paths.get(RESOURCE_DIR, "istio", "istio-dr-template.yaml");
    Path targetDrFile = assertDoesNotThrow(
        () -> generateFileFromTemplate(srcDrFile.toString(), "istio-dr.yaml", templateMap));
    logger.info("Generated DestinationRule file path is {0}", targetDrFile);

    deployRes = assertDoesNotThrow(
        () -> deployIstioDestinationRule(targetDrFile));
    assertTrue(deployRes, "Failed to deploy Istio DestinationRule");

    int istioIngressPort = getIstioHttpIngressPort();
    logger.info("Istio Ingress Port is {0}", istioIngressPort);
    return istioIngressPort;
  }

  private static DomainResource createDomainResourceWithLogHome(
          String domainResourceName,
          String domNamespace,
          String imageName,
          String adminSecretName,
          String repoSecretName,
          String encryptionSecretName,
          int replicaCount,
          String pvName,
          String pvcName,
          String clusterName,
          String configMapName,
          String dbSecretName,
          boolean onlineUpdateEnabled,
          boolean setDataHome) {

    String clusterResName = domainResourceName + "-" + clusterName;
    List<String> securityList = new ArrayList<>();
    if (dbSecretName != null) {
      securityList.add(dbSecretName);
    }

    DomainSpec domainSpec = new DomainSpec()
        .domainUid(domainResourceName)
        .domainHomeSourceType("FromModel")
        .image(imageName)
        .imagePullPolicy(IMAGE_PULL_POLICY)
        .addImagePullSecretsItem(new V1LocalObjectReference()
            .name(repoSecretName))
        .webLogicCredentialsSecret(new V1LocalObjectReference()
            .name(adminSecretName))
        .includeServerOutInPodLog(true)
        .logHomeEnabled(Boolean.TRUE)
        .logHome("/shared/logs")
        .serverStartPolicy("IfNeeded")
        .serverPod(new ServerPod()
            .addEnvItem(new V1EnvVar()
                .name("JAVA_OPTIONS")
                .value("-Dweblogic.security.SSL.ignoreHostnameVerification=true"))
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
        .configuration(new Configuration()
            .secrets(securityList)
            .model(new Model()
                .domainType("WLS")
                .configMap(configMapName)
                .runtimeEncryptionSecret(encryptionSecretName)
                .onlineUpdate(new OnlineUpdate()
                    .enabled(onlineUpdateEnabled)))
            .introspectorJobActiveDeadlineSeconds(3000L));

    if (setDataHome) {
      domainSpec.dataHome("/shared/data");
    }
    // create the domain CR
    DomainResource domain = new DomainResource()
        .apiVersion(DOMAIN_API_VERSION)
        .kind("Domain")
        .metadata(new V1ObjectMeta()
            .name(domainResourceName)
            .namespace(domNamespace))
        .spec(domainSpec);
    
    setPodAntiAffinity(domain);

    // create cluster object
    ClusterResource cluster = createClusterResource(clusterResName, clusterName, domNamespace, replicaCount);
    logger.info("Creating cluster resource {0} in namespace {1}", clusterResName, domNamespace);
    createClusterAndVerify(cluster);
    // set cluster references
    domain.getSpec().withCluster(new V1LocalObjectReference().name(clusterResName));

    logger.info("Create domain custom resource for domainUid {0} in namespace {1}",
        domainResourceName, domNamespace);
    boolean domCreated = assertDoesNotThrow(() -> createDomainCustomResource(domain),
        String.format("Create domain custom resource failed with ApiException for %s in namespace %s",
            domainResourceName, domNamespace));
    assertTrue(domCreated, String.format("Create domain custom resource failed with ApiException "
        + "for %s in namespace %s", domainResourceName, domNamespace));
    
    return domain;
  }

  private void checkApp(String url, Map<String, String> headers, String... expectedResponse) {
    testUntil(
        () -> {
          HttpResponse<String> response = OracleHttpClient.get(url, headers, true);
          
          return response.statusCode() == 200
              && (expectedResponse.length == 0 ? true : response.body().contains(expectedResponse[0]));
        },
        logger,
        "application to be ready {0}",
        url);
  }  
}

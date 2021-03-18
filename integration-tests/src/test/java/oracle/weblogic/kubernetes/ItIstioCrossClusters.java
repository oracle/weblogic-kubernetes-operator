// Copyright (c) 2020, 2021, Oracle and/or its affiliates.
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

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
import oracle.weblogic.domain.ServerPod;
import oracle.weblogic.kubernetes.annotations.IntegrationTest;
import oracle.weblogic.kubernetes.annotations.Namespaces;
import oracle.weblogic.kubernetes.assertions.TestAssertions;
import oracle.weblogic.kubernetes.logging.LoggingFacade;
import oracle.weblogic.kubernetes.utils.BuildApplication;
import oracle.weblogic.kubernetes.utils.ExecResult;
import org.awaitility.core.ConditionFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.condition.DisabledIfEnvironmentVariable;

import static java.util.concurrent.TimeUnit.MINUTES;
import static java.util.concurrent.TimeUnit.SECONDS;
import static oracle.weblogic.kubernetes.TestConstants.ADMIN_PASSWORD_DEFAULT;
import static oracle.weblogic.kubernetes.TestConstants.ADMIN_USERNAME_DEFAULT;
import static oracle.weblogic.kubernetes.TestConstants.DB_IMAGE_TO_USE_IN_SPEC;
import static oracle.weblogic.kubernetes.TestConstants.DOMAIN_API_VERSION;
import static oracle.weblogic.kubernetes.TestConstants.DOMAIN_VERSION;
import static oracle.weblogic.kubernetes.TestConstants.OCIR_SECRET_NAME;
import static oracle.weblogic.kubernetes.TestConstants.RESULTS_ROOT;
import static oracle.weblogic.kubernetes.actions.ActionConstants.APP_DIR;
import static oracle.weblogic.kubernetes.actions.ActionConstants.MODEL_DIR;
import static oracle.weblogic.kubernetes.actions.TestActions.addLabelsToNamespace;
import static oracle.weblogic.kubernetes.actions.TestActions.createDomainCustomResource;
import static oracle.weblogic.kubernetes.actions.TestActions.getServiceNodePort;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.domainExists;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.checkPodExists;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.checkPodReady;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.checkServiceExists;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.createImageAndVerify;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.createOcirRepoSecret;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.createSecretForBaseImages;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.createSecretWithUsernamePassword;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.dockerLoginAndPushImageToRegistry;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.getExternalServicePodName;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.installAndVerifyOperator;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.setPodAntiAffinity;
import static oracle.weblogic.kubernetes.utils.DbUtils.getDBNodePort;
import static oracle.weblogic.kubernetes.utils.DbUtils.startOracleDB;
import static oracle.weblogic.kubernetes.utils.ExecCommand.exec;
import static oracle.weblogic.kubernetes.utils.ThreadSafeLogger.getLogger;
import static org.awaitility.Awaitility.with;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;


@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("Verify cross Kubernetes Cluster domain transaction is successful")
@IntegrationTest
@DisabledIfEnvironmentVariable(named = "TWO_CLUSTERS", matches = "false")
public class ItIstioCrossClusters {


  private static final String WDT_MODEL_FILE_DOMAIN2 = "model-crossdomaintransaction-domain2.yaml";

  private static final String WDT_MODEL_DOMAIN1_PROPS = "model-crossdomaintransaction-domain1.properties";
  private static final String WDT_MODEL_DOMAIN2_PROPS = "model-crossdomaintransaction-domain2.properties";
  private static final String WDT_IMAGE_NAME2 = "crossclustersdomain2-wdt-image";
  private static final String PROPS_TEMP_DIR = RESULTS_ROOT + "/istiocrossclustersdomaintransactiontemp";
  private static final String WDT_MODEL_FILE_JDBC = "model-cdt-jdbc.yaml";

  private static String op2Namespace = null;
  private static String domain1Namespace = "crosscluster-domain1-cluster1";
  private static String domain2Namespace = null;
  private static ConditionFactory withStandardRetryPolicy = null;
  private static String domainUid1 = "domain1";
  private static String domainUid2 = "domain2";
  private static String istioClusterOneIngressPort;
  private static final String domain1AdminServerPodName = domainUid1 + "-admin-server";
  private final String domain1ManagedServerPrefix = domainUid1 + "-managed-server";
  private final String domain2ManagedServerPrefix = domainUid2 + "-managed-server";
  private static final String ORACLEDBURLPREFIX = "oracledb.";
  private static final String ORACLEDBSUFFIX = ".svc.cluster.local:1521/devpdb.k8s";
  
  private static LoggingFacade logger = null;
  static String dbUrl;
  static int dbNodePort;
  private static String K8S_NODEPORT_HOST2 = System.getenv("K8S_NODEPORT_HOST2");

  private static String K8S_NODEPORT_HOST1 = System.getenv("K8S_NODEPORT_HOST1");

  private static boolean TWO_CLUSTERS = Boolean.parseBoolean(java.util.Optional.ofNullable(
      System.getenv("TWO_CLUSTERS"))
      .orElse("false"));

  /**
   * Install Operator, Database in cluster2.
   * @param namespaces list of namespaces created by the IntegrationTestWatcher by the
   *     JUnit engine parameter resolution mechanism
   */
  @BeforeAll
  public static void initAll(@Namespaces(3) List<String> namespaces) {
    logger = getLogger();
    try {
      FileInputStream in = new FileInputStream(PROPS_TEMP_DIR + "/" + WDT_MODEL_DOMAIN1_PROPS);
      Properties props = new Properties();
      props.load(in);
      istioClusterOneIngressPort = props.getProperty("ISTIO_INGRESS_PORT");
      in.close();
    } catch (IOException ex) {
      logger.info("Can't read property file");
    }
    //start to setup in cluster2
    // create standard, reusable retry/backoff policy
    withStandardRetryPolicy = with().pollDelay(2, SECONDS)
        .and().with().pollInterval(10, SECONDS)
        .atMost(5, MINUTES).await();
    // get a new unique opNamespace
    logger.info("Creating unique namespace for Operator");

    assertNotNull(namespaces.get(0), "Namespace list is null");
    op2Namespace = namespaces.get(0);


    logger.info("Creating unique namespace for Domain");
    assertNotNull(namespaces.get(2), "Namespace list is null");
    domain2Namespace = namespaces.get(2);

    dbUrl = ORACLEDBURLPREFIX + domain2Namespace + ORACLEDBSUFFIX;
    createSecretForBaseImages(domain2Namespace);

    //Start oracleDB
    assertDoesNotThrow(() -> {
      startOracleDB(DB_IMAGE_TO_USE_IN_SPEC, 0, domain2Namespace);
      String.format("Failed to start Oracle DB");
    });
    dbNodePort = getDBNodePort(domain2Namespace, "oracledb");
    logger.info("DB Node Port = {0}", dbNodePort);

    // Label the domain/operator namespace with istio-injection=enabled
    Map<String, String> labelMap = new HashMap();
    labelMap.put("istio-injection", "enabled");

    //assertDoesNotThrow(() -> addLabelsToNamespace(domain1Namespace,labelMap));
    assertDoesNotThrow(() -> addLabelsToNamespace(domain2Namespace, labelMap));
    assertDoesNotThrow(() -> addLabelsToNamespace(op2Namespace, labelMap));

    // install and verify operator in cluster2
    installAndVerifyOperator(op2Namespace, domain2Namespace);

    // Now that we got the namespaces for both the domains, we need to update the model properties
    // file with the namespaces. For cross domain transaction to work, we need to have the externalDNSName
    // set in the config file. Cannot set this after the domain is up since a server restart is
    // required for this to take effect. So, copying the property file to RESULT_ROOT and updating the
    // property file
    updatePropertyFile();
  }

  //create domain2 and applications in cluster2
  private static void createDomainAndApps() {
    // build the model file list for domain2
    final List<String> modelListDomain2 = Arrays.asList(
        MODEL_DIR + "/" + WDT_MODEL_FILE_DOMAIN2,
        MODEL_DIR + "/" + WDT_MODEL_FILE_JDBC);
    //build application archive
    Path distDir = BuildApplication.buildApplication(java.nio.file.Paths.get(APP_DIR, "txforward"), null, null,
        "build", domain2Namespace);
    logger.info("distDir is {0}", distDir.toString());
    assertTrue(Paths.get(distDir.toString(),
        "txforward.ear").toFile().exists(),
        "Application archive is not available");
    String appSource = distDir.toString() + "/txforward.ear";
    logger.info("Application is in {0}", appSource);

    final List<String> appSrcDirList2 = Collections.singletonList(appSource);

    // create admin credential secret for domain2
    logger.info("Create admin credential secret for domain2");
    String domain2AdminSecretName = domainUid2 + "-weblogic-credentials";
    assertDoesNotThrow(() -> createSecretWithUsernamePassword(
        domain2AdminSecretName, domain2Namespace, ADMIN_USERNAME_DEFAULT, ADMIN_PASSWORD_DEFAULT),
        String.format("createSecret %s failed for %s", domain2AdminSecretName, domainUid2));

    logger.info("Creating image with model file and verify");
    String domain2Image = createImageAndVerify(
        WDT_IMAGE_NAME2, modelListDomain2, appSrcDirList2, WDT_MODEL_DOMAIN2_PROPS, PROPS_TEMP_DIR, domainUid2);
    logger.info("Created {0} image", domain2Image);

    // docker login and push image to docker registry if necessary
    dockerLoginAndPushImageToRegistry(domain2Image);
    //create domain2
    createDomain(domainUid2, domain2Namespace, domain2AdminSecretName, domain2Image);
  }


  private static void updatePropertyFile() {
    //create a temporary directory to copy and update the properties file
    Path target = Paths.get(PROPS_TEMP_DIR);

    Path source2 = Paths.get(MODEL_DIR, WDT_MODEL_DOMAIN2_PROPS);
    logger.info("Copy the properties file to the above area so that we can add namespace property");
    assertDoesNotThrow(() -> {
      Files.createDirectories(target);

      Files.copy(source2, target.resolve(source2.getFileName()), StandardCopyOption.REPLACE_EXISTING);
    });

    assertDoesNotThrow(() -> {
      addToPropertyFile(WDT_MODEL_DOMAIN2_PROPS, domain2Namespace, K8S_NODEPORT_HOST2);
      String.format("Failed to update %s with namespace %s",
          WDT_MODEL_DOMAIN2_PROPS, domain2Namespace);
    });

  }

  private static void addToPropertyFile(String propFileName, String domainNamespace, String host) throws IOException {
    FileInputStream in = new FileInputStream(PROPS_TEMP_DIR + "/" + propFileName);
    Properties props = new Properties();
    props.load(in);
    in.close();

    FileOutputStream out = new FileOutputStream(PROPS_TEMP_DIR + "/" + propFileName);
    props.setProperty("NAMESPACE", domainNamespace);
    props.setProperty("K8S_NODEPORT_HOST", host);
    props.setProperty("DBPORT", Integer.toString(dbNodePort));
    props.store(out, null);
    out.close();
  }

  /*
   * The test uses Domain-in-image model (with WDT) to start a domain
   * in a unique namespace in each of the kubernetes clusters
   * and verifies cross clusters domain transaction is successful.
   * An app is deployed to both the domains and the servlet
   * is invoked which starts a transaction that spans both domains.
   * The application consists of a servlet front-end and a remote object that defines a method to register
   * a simple javax.transaction.Synchronization object. When the servlet is invoked, a global transaction
   * is started, and the specified list of server URLs is used to look up the remote object and register
   * a Synchronization object on each server.  Finally, the transaction is committed.  If the server
   * listen-addresses are resolvable between the transaction participants, then the transaction should
   * complete successfully
   */
  @Test
  @DisplayName("Check cross clusters domain transaction works")
  public void testCrossDomainTransaction() {
    createDomainAndApps();
    String curlRequest = String.format("curl -v --show-error --noproxy '*' "
            + "-H 'host:domain1-" + domain1Namespace + ".org' "
            + "http://%s:%s/TxForward/TxForward?urls=t3://%s.%s:7001,t3://%s1.%s:8001,t3://%s1.%s:8001,t3://%s2.%s:8001",
        K8S_NODEPORT_HOST1, istioClusterOneIngressPort, domain1AdminServerPodName, domain1Namespace,
        domain1ManagedServerPrefix, domain1Namespace, domain2ManagedServerPrefix, domain2Namespace,
        domain2ManagedServerPrefix, domain2Namespace);

    ExecResult result = null;
    logger.info("curl command {0}", curlRequest);
    result = assertDoesNotThrow(
        () -> exec(curlRequest, true));
    logger.info("curl result {0}", result.exitValue());
    result = assertDoesNotThrow(
        () -> exec(curlRequest, true));
    if (result.exitValue() == 0) {
      logger.info("\n HTTP response is \n " + result.stdout());
      logger.info("curl command returned {0}", result.toString());
      assertTrue(result.stdout().contains("Status=Committed"), "crossDomainTransaction failed");
    }
  }


  /*
   * This test verifies cross clusters domain transaction is successful and able to re-establish connection when
   * one domain is shutdown. Domain in image with wdt is used to create 2 domains in a unique namespace
   * in each of the kubernetes clusters
   * and verifies cross clusters domain transaction is successful.
   * A servlet is deployed to the admin server of domain1. This servlet starts a transaction with
   * TMAfterTLogBeforeCommitExit transaction property set. The servlet inserts data into oracleDB table and
   * sends a message to a JMS queue as part of a same transaction.The coordinator (server in domain2)
   * should exit before commit and the domain1 admin server should be able to re-establish connection
   * with domain2 and the transaction should commit.
   *
   */
  @Test
  @DisplayName("Check cross domain transaction with TMAfterTLogBeforeCommitExit property commits")
  public void testCrossDomainTransactionWithFailInjection() {
    logger.info("Getting admin server external service node port");
    String curlRequest = String.format("curl -v --show-error --noproxy '*' "
            + "-H 'host:domain1-" + domain1Namespace + ".org' "
            + "http://%s:%s/cdttxservlet/cdttxservlet?namespaces=%s,%s",
        K8S_NODEPORT_HOST1, istioClusterOneIngressPort, domain1Namespace, domain2Namespace);

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


  private static void createDomain(String domainUid, String domainNamespace, String adminSecretName,
                                   String domainImage) {
    // admin/managed server name here should match with model yaml in WDT_MODEL_FILE
    final String adminServerPodName = domainUid + "-admin-server";
    final String managedServerPrefix = domainUid + "-managed-server";
    final int replicaCount = 2;

    // Create the repo secret to pull the image
    // this secret is used only for non-kind cluster
    createOcirRepoSecret(domainNamespace);

    // create the domain CR
    createDomainResource(domainUid, domainNamespace, adminSecretName, OCIR_SECRET_NAME,
        replicaCount, domainImage);

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

    // check admin server pod exists
    logger.info("Check for admin server pod {0} existence in namespace {1}",
        adminServerPodName, domainNamespace);
    checkPodExists(adminServerPodName, domainUid, domainNamespace);

    // check managed server pods exist
    for (int i = 1; i <= replicaCount; i++) {
      logger.info("Check for managed server pod {0} existence in namespace {1}",
          managedServerPrefix + i, domainNamespace);
      checkPodExists(managedServerPrefix + i, domainUid, domainNamespace);
    }

    // check admin server pod is ready
    logger.info("Wait for admin server pod {0} to be ready in namespace {1}",
        adminServerPodName, domainNamespace);
    checkPodReady(adminServerPodName, domainUid, domainNamespace);

    // check managed server pods are ready
    for (int i = 1; i <= replicaCount; i++) {
      logger.info("Wait for managed server pod {0} to be ready in namespace {1}",
          managedServerPrefix + i, domainNamespace);
      checkPodReady(managedServerPrefix + i, domainUid, domainNamespace);
    }

    logger.info("Check admin service {0} is created in namespace {1}",
        adminServerPodName, domainNamespace);
    checkServiceExists(adminServerPodName, domainNamespace);

    // check managed server services created
    for (int i = 1; i <= replicaCount; i++) {
      logger.info("Check managed server service {0} is created in namespace {1}",
          managedServerPrefix + i, domainNamespace);
      checkServiceExists(managedServerPrefix + i, domainNamespace);
    }

    logger.info("Getting node port");
    int serviceNodePort = assertDoesNotThrow(() -> getServiceNodePort(domainNamespace,
        getExternalServicePodName(adminServerPodName), "default"),
        "Getting admin server node port failed");

    logger.info("Validating WebLogic admin server access by login to console");
    boolean loginSuccessful = assertDoesNotThrow(() -> {
      return TestAssertions.adminNodePortAccessible(serviceNodePort, ADMIN_USERNAME_DEFAULT,
          ADMIN_PASSWORD_DEFAULT);
    }, "Access to admin server node port failed");
    assertTrue(loginSuccessful, "Console login validation failed");

  }

  private static void createDomainResource(String domainUid, String domNamespace, String adminSecretName,
                                           String repoSecretName, int replicaCount, String domainImage) {
    logger.info("Image to be used is {0}", domainImage);
    // create the domain CR
    Domain domain = new Domain()
        .apiVersion(DOMAIN_API_VERSION)
        .kind("Domain")
        .metadata(new V1ObjectMeta()
            .name(domainUid)
            .namespace(domNamespace))
        .spec(new DomainSpec()
            .domainUid(domainUid)
            .domainHomeSourceType("Image")
            .image(domainImage)
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
                    .value("-Dweblogic.transaction.EnableInstrumentedTM=true -Dweblogic.StdoutDebugEnabled=false"
                        + "-Dweblogic.debug.DebugJTAXA=true "
                        + "-Dweblogic.debug.DebugJTA2PC=true"))
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

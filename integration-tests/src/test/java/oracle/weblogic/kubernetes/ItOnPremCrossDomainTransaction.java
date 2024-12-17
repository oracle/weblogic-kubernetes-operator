// Copyright (c) 2024, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.net.http.HttpResponse;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import io.kubernetes.client.openapi.models.V1EnvVar;
import io.kubernetes.client.openapi.models.V1LocalObjectReference;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import oracle.weblogic.domain.Configuration;
import oracle.weblogic.domain.DomainResource;
import oracle.weblogic.domain.DomainSpec;
import oracle.weblogic.domain.Model;
import oracle.weblogic.domain.ServerPod;
import oracle.weblogic.kubernetes.actions.impl.AppParams;
import oracle.weblogic.kubernetes.actions.impl.primitive.Command;
import oracle.weblogic.kubernetes.actions.impl.primitive.CommandParams;
import oracle.weblogic.kubernetes.annotations.IntegrationTest;
import oracle.weblogic.kubernetes.annotations.Namespaces;
import oracle.weblogic.kubernetes.logging.LoggingFacade;
import oracle.weblogic.kubernetes.utils.ExecResult;
import oracle.weblogic.kubernetes.utils.FileUtils;
import oracle.weblogic.kubernetes.utils.OracleHttpClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static oracle.weblogic.kubernetes.TestConstants.ADMIN_PASSWORD_DEFAULT;
import static oracle.weblogic.kubernetes.TestConstants.ADMIN_USERNAME_DEFAULT;
import static oracle.weblogic.kubernetes.TestConstants.DOMAIN_API_VERSION;
import static oracle.weblogic.kubernetes.TestConstants.DOMAIN_VERSION;
import static oracle.weblogic.kubernetes.TestConstants.IMAGE_PULL_POLICY;
import static oracle.weblogic.kubernetes.TestConstants.IT_ONPREMCRDOMAINTX_CLUSTER_HOSTPORT;
import static oracle.weblogic.kubernetes.TestConstants.IT_ONPREMCRDOMAINTX_CLUSTER_NODEPORT;
import static oracle.weblogic.kubernetes.TestConstants.IT_ONPREMCRDOMAINTX_INGRESS_HTTP_NODEPORT;
import static oracle.weblogic.kubernetes.TestConstants.KUBERNETES_CLI;
import static oracle.weblogic.kubernetes.TestConstants.RESULTS_ROOT;
import static oracle.weblogic.kubernetes.TestConstants.TEST_IMAGES_REPO_SECRET_NAME;
import static oracle.weblogic.kubernetes.TestConstants.TRAEFIK_INGRESS_HTTP_HOSTPORT;
import static oracle.weblogic.kubernetes.TestConstants.WEBLOGIC_IMAGE_NAME;
import static oracle.weblogic.kubernetes.TestConstants.WEBLOGIC_IMAGE_TAG;
import static oracle.weblogic.kubernetes.actions.ActionConstants.APP_DIR;
import static oracle.weblogic.kubernetes.actions.ActionConstants.ARCHIVE_DIR;
import static oracle.weblogic.kubernetes.actions.ActionConstants.DOWNLOAD_DIR;
import static oracle.weblogic.kubernetes.actions.ActionConstants.RESOURCE_DIR;
import static oracle.weblogic.kubernetes.actions.ActionConstants.WDT_DOWNLOAD_URL;
import static oracle.weblogic.kubernetes.actions.ActionConstants.WLS;
import static oracle.weblogic.kubernetes.actions.ActionConstants.WORK_DIR;
import static oracle.weblogic.kubernetes.actions.TestActions.archiveApp;
import static oracle.weblogic.kubernetes.actions.TestActions.createDomainCustomResource;
import static oracle.weblogic.kubernetes.actions.TestActions.defaultAppParams;
import static oracle.weblogic.kubernetes.actions.TestActions.shutdownDomain;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.domainExists;
import static oracle.weblogic.kubernetes.utils.ApplicationUtils.checkAppIsActive;
import static oracle.weblogic.kubernetes.utils.BuildApplication.buildApplication;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.checkPodReadyAndServiceExists;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.createIngressHostRouting;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.formatIPv6Host;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.testUntil;
import static oracle.weblogic.kubernetes.utils.ExecCommand.exec;
import static oracle.weblogic.kubernetes.utils.FileUtils.copyFolder;
import static oracle.weblogic.kubernetes.utils.FileUtils.replaceStringInFile;
import static oracle.weblogic.kubernetes.utils.ImageUtils.createImageAndVerify;
import static oracle.weblogic.kubernetes.utils.ImageUtils.createTestRepoSecret;
import static oracle.weblogic.kubernetes.utils.ImageUtils.imageRepoLoginAndPushImageToRegistry;
import static oracle.weblogic.kubernetes.utils.LoadBalancerUtils.installAndVerifyTraefik;
import static oracle.weblogic.kubernetes.utils.OperatorUtils.installAndVerifyOperator;
import static oracle.weblogic.kubernetes.utils.PodUtils.setPodAntiAffinity;
import static oracle.weblogic.kubernetes.utils.SecretUtils.createSecretWithUsernamePassword;
import static oracle.weblogic.kubernetes.utils.ThreadSafeLogger.getLogger;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Cross domain transaction tests.
 */
@DisplayName("Verify cross domain transaction across K8S and on-premise is successful")
@IntegrationTest
@Tag("kind-sequential")
class ItOnPremCrossDomainTransaction {

  private static final String WDT_MODEL_FILE_DOMAIN1 = "model-crossdomaintransaction-domain1.yaml";
  private static final String WDT_MODEL_FILE_DOMAIN2 = "model-crossdomaintransaction-domain2.yaml";
  private static final String WDT_MODEL_FILE_DOMAIN3 = "model-crossdomaintransaction-domain3.yaml";

  private static final String ONPREM_DOMAIN_ROUTING = "onprem-domain-routing.yaml";
  private static final String K8S_DOMAIN3_NODEPORT_ROUTING = "onprem-domain2-node-portrouting.yaml";
  private static String onpremIngressClass = null;
  private static final String WDT_MODEL_FILE_JMS = "model-cdt-jms.yaml";
  private static final String WDT_MODEL_FILE_JMS2 = "model2-cdt-jms.yaml";
  
  private static List<String> applicationsList;
  private static final String WDT_IMAGE_NAME1 = "domain1-onprem-wdt-image";
  private static final String PROPS_TEMP_DIR = RESULTS_ROOT + "/crossdomainonpremtemp";

  private static String opNamespace = null;
  private static String domain1Namespace = null;
  private static String domain3Namespace = null;
  private static String domainUid1 = "domain1";
  private static String domainUid3 = "domain3";
  private static String adminServerName = "admin-server";
  private static int adminServerPort = 7001;
  
  private static LoggingFacade logger = null;
  private static String hostHeader;
  private static Map<String, String> headers = null;
  private static String hostAndPort = null;
  private static String localAddress;

  private static String javaOptions = "-Dweblogic.Debug.DebugNaming=true "
      + "-Dweblogic.Debug.DebugJTANaming=true "
      + "-Dweblogic.debug.DebugConnection=true "
      + "-Dweblogic.debug.DebugRouting=true "
      + "-Dweblogic.debug.DebugMessaging=true "
      + "-Dweblogic.kernel.debug=true "
      + "-Dweblogic.log.LoggerSeverity=Debug "
      + "-Dweblogic.log.LogSeverity=Debug "
      + "-Dweblogic.StdoutDebugEnabled=true "
      + "-Dweblogic.log.StdoutSeverity=Debug "
      + "-Dweblogic.rjvm.allowUnknownHost=true  "
      + "-Dweblogic.security.remoteAnonymousRMIT3Enabled=true";
  private static Path wlstScript;
  private static Path createDomainScript;
  private static Path domainHome;
  private static Path mwHome;
  private static String domainUT;
  private static String domainNSUT;

  /**
   * Install Operator.
   *
   * @param namespaces list of namespaces
   */
  @BeforeAll
  public static void initAll(@Namespaces(3) List<String> namespaces)
      throws UnknownHostException, IOException, InterruptedException {
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
    domain3Namespace = namespaces.get(2);
    
    //install traefil for on prem domain
    onpremIngressClass = installTraefikForPremDomain();
    
    //add DNS entries in the local machine
    localAddress = formatIPv6Host(InetAddress.getLocalHost().getHostAddress());
    modifyDNS();
    logger.info("installing WebLogic Deploy Tool");
    downloadAndInstallWDT();
    // install and verify operator
    installAndVerifyOperator(opNamespace, domain1Namespace, domain3Namespace);    
  }
  
  /**
   * Stop on premise domain.
   */
  @AfterEach
  public void stopOnPremDomain() throws UnknownHostException, InterruptedException {
    shutdownServers(List.of(wlstScript.toString(),
        Path.of(RESOURCE_DIR, "onpremcrtx").toString() + "/shutdown.py",
        localAddress),
        Path.of(domainHome.toString(), "wlst.log"));
    shutdownDomain(domainUT, domainNSUT);
  }
  
  /**
   * This test verifies cross-domain MessageDrivenBean communication A transacted MDB on Domain D1 listen on a
   * replicated Distributed Topic on Domain D2. The MDB is deployed to cluster on domain D1 with
   * MessagesDistributionMode set to One-Copy-Per-Server. The OnMessage() routine sends a message to local queue on
   * receiving the message. An application servlet is deployed to Administration Server on D1 which send/receive message
   * from a JMS destination based on a given URL. (a) app servlet send message to Distributed Topic on D2 (b) mdb puts a
   * message into local Queue for each received message (c) make sure local Queue gets 2X times messages sent to
   * Distributed Topic Since the MessagesDistributionMode is set to One-Copy-Per-Server and targeted to a cluster of two
   * servers, onMessage() will be triggered for both instance of MDB for a message sent to Distributed Topic
   */
  @Test
  @DisplayName("Check cross domain transcated MDB communication ")
  void testCrossDomainWithExternalJMSProvider() throws IOException, InterruptedException {
    createOnPremDomainJMSProvider();
    //start on prem domain
    startServers(domainHome);
    
    logger.info("creating routing rules for on prem domain to talk to JMS client in K8S cluster");
    Path srcRoutingFile = Path.of(RESOURCE_DIR, "onpremcrtx", ONPREM_DOMAIN_ROUTING);
    String content = new String(Files.readAllBytes(srcRoutingFile), StandardCharsets.UTF_8);
    Path ingressRoutingFile = File.createTempFile("ingressRoutingFile", ".yaml").toPath();
    Files.write(ingressRoutingFile,
        content.replaceAll("NAMESPACE", domain1Namespace)
            .replaceAll("traefik-onprem", onpremIngressClass)
            .getBytes(StandardCharsets.UTF_8), StandardOpenOption.CREATE);
    execK8SCLI(ingressRoutingFile);    
    
    String jmsProvider = "t3://" + localAddress + ":8002," + localAddress + ":8003";
    applicationsList = buildApplications(jmsProvider, null);

    // build the model file list for domain1
    final List<String> modelFilesListDomain1 = Arrays.asList(
        RESOURCE_DIR + "/onpremcrtx/" + WDT_MODEL_FILE_DOMAIN1,
        RESOURCE_DIR + "/onpremcrtx/" + WDT_MODEL_FILE_JMS);
    //create model property file list
    Path propFile = File.createTempFile("k8s-jms-client", ".props").toPath();
    Files.writeString(propFile,
        """
        ADMIN_USERNAME=weblogic
        ADMIN_PASSWORD=welcome1
        DOMAIN_NAME=domain1
        CALCULATED_LISTENPORTS=false
        NAMESPACE=""" + domain1Namespace,
        StandardOpenOption.TRUNCATE_EXISTING);
    List<String> modelPropList = Collections.singletonList(propFile.toString());    
    createK8sDomain(domainUid1, domain1Namespace, modelFilesListDomain1, modelPropList, applicationsList);
    domainUT = domainUid1;
    domainNSUT = domain1Namespace;
    
    // No extra header info
    String curlHostHeader = "";
    if (TestConstants.KIND_CLUSTER
        && !TestConstants.WLSIMG_BUILDER.equals(TestConstants.WLSIMG_BUILDER_DEFAULT)) {
      curlHostHeader = "--header 'Host: " + hostHeader + "'";
    }
    assertTrue(checkAppIsActive(hostAndPort,
        curlHostHeader, "mdbtopic", "cluster-1",
        ADMIN_USERNAME_DEFAULT, ADMIN_PASSWORD_DEFAULT),
        "MDB application can not be activated on domain1/cluster");

    logger.info("MDB application is activated on domain1/cluster");

    //since JMS provider is clustered instances and since they are running on-prem
    //cluster address is not available. Hence sending messages to individual instances
    List<String> ports = List.of("8002", "8003");
    for (String port : ports) {
      String url = String.format("http://%s/jmsservlet/jmstest?"
          + "url=t3://%s:%s&"
          + "cf=jms.ClusterConnectionFactory&"
          + "action=send&"
          + "dest=jms/testCdtUniformTopic",
          hostAndPort, localAddress, port);
      logger.info(url);

      HttpResponse<String> response;
      response = OracleHttpClient.get(url, headers, true);
      assertEquals(200, response.statusCode(), "Didn't get the 200 HTTP status");
      assertTrue(response.body().contains("Sent (10) message"),
          "Can not send message to remote Distributed Topic");
    }

    assertTrue(checkLocalQueue(hostAndPort),
        "Expected number of message not found in Accounting Queue");
  }

  /**
   * This test verifies cross-domain MessageDrivenBean communication A transacted MDB on Domain D1 listen on a
   * replicated Distributed Topic on Domain D2. The MDB is deployed to a on premise domain D1 with
   * MessagesDistributionMode set to One-Copy-Per-Server. The OnMessage() routine sends a message to local queue on
   * receiving the message. An application servlet is deployed to Administration Server on D1 which send/receive message
   * from a JMS destination based on a given URL. (a) app servlet send message to Distributed Topic on D2 (b) mdb puts a
   * message into local Queue for each received message (c) make sure local Queue gets 2X times messages sent to
   * Distributed Topic Since the MessagesDistributionMode is set to One-Copy-Per-Server and targeted to a cluster of two
   * servers, onMessage() will be triggered for both instance of MDB for a message sent to Distributed Topic. Use WLSt
   * script to get the messages count on the local queue populated by the MDB and verify it matches 20.
   */
  @Test
  @DisplayName("Check cross domain transcated MDB communication ")
  void testCrossDomainWithK8SJMSProvider() throws IOException, InterruptedException {
    
    // build the model file list for k8s domain
    final List<String> k8sDomainModelFilesList = Arrays.asList(
        RESOURCE_DIR + "/onpremcrtx/" + WDT_MODEL_FILE_DOMAIN3,
        RESOURCE_DIR + "/onpremcrtx/" + WDT_MODEL_FILE_JMS2);

    //create model property file list
    Path propFile = File.createTempFile("k8s-jms-provider", ".props").toPath();
    Files.writeString(propFile,
        """
        ADMIN_USERNAME=weblogic
        ADMIN_PASSWORD=welcome1
        DOMAIN_NAME=domain3
        CALCULATED_LISTENPORTS=false
        PUBLIC_LB_ADDRESS=""" + localAddress
        + "\nPUBLIC_LB_PORT=" + IT_ONPREMCRDOMAINTX_CLUSTER_HOSTPORT,
        StandardOpenOption.TRUNCATE_EXISTING);
    List<String> modelPropList = Collections.singletonList(propFile.toString());
    
    logger.info("creating k8s domain hosting JMS provider in cluster with mode "
        + "files {0} and property files {1}", k8sDomainModelFilesList, modelPropList);
    createK8sDomain(domainUid3, domain3Namespace, k8sDomainModelFilesList, modelPropList, null);
    domainUT = domainUid3;
    domainNSUT = domain3Namespace;
    
    logger.info("creating node port service for K8S domain cluster-1");
    Path nodePortServiceFile = File.createTempFile(domainUid3, ".yaml").toPath();
    FileUtils.copy(Path.of(RESOURCE_DIR, "/onpremcrtx/", K8S_DOMAIN3_NODEPORT_ROUTING), nodePortServiceFile);
    
    FileUtils.replaceStringInFile(nodePortServiceFile.toString(),
        "NAMESPACE", domain3Namespace);
    FileUtils.replaceStringInFile(nodePortServiceFile.toString(),
        "DOMAIN_NAME", domainUid3);
    FileUtils.replaceStringInFile(nodePortServiceFile.toString(),
        "NODE_PORT", String.valueOf(IT_ONPREMCRDOMAINTX_CLUSTER_NODEPORT));
    //create routing rules for on prem domain to communicate to K8S JMS provider
    execK8SCLI(nodePortServiceFile);
    
    String jmsprovider = "t3://" + localAddress + ":" + IT_ONPREMCRDOMAINTX_CLUSTER_HOSTPORT;    
    logger.info("creating on prem domain, JMS clients for JMS providers running in K8S domain");
    createOnPremDomainJMSClient(jmsprovider);
    
    logger.info("starting on prem domain");
    startServers(domainHome);

    String hostAndPort = localAddress + ":" + adminServerPort;
    assertTrue(checkAppIsActive(hostAndPort, "", "mdbtopic", "cluster-1",
        ADMIN_USERNAME_DEFAULT, ADMIN_PASSWORD_DEFAULT),
        "MDB application can not be activated on domain1/cluster");

    logger.info("MDB application is activated on domain1/cluster");

    String url = String.format("http://%s/jmsservlet/jmstest?"
        + "url=t3://%s:%s&"
        + "cf=jms.ClusterConnectionFactory&"
        + "action=send&"
        + "dest=jms/testCdtUniformTopic",
        hostAndPort, localAddress, IT_ONPREMCRDOMAINTX_CLUSTER_HOSTPORT);
    logger.info(url);
    
    HttpResponse<String> response1;
    response1 = OracleHttpClient.get(url, null, true);
    assertEquals(200, response1.statusCode(), "Didn't get the 200 HTTP status");
    assertTrue(response1.body().contains("Sent (10) message"),
        "Can not send message to remote Distributed Topic");    
    
    testUntil(() -> {
      executeWlst(List.of(wlstScript.toString(),
          Path.of(RESOURCE_DIR, "onpremcrtx").toString() + "/getmessages.py", localAddress),
          Path.of(domainHome.toString(), "accountingQueueMessages.log"), true);
      String content = Files.readString(Path.of(domainHome.toString(), "accountingQueueMessages.log"));
      logger.info(content);
      return content.contains("messagesgot=20");
    }, logger, "checking for messages in testAccountingQueue to be equal to 20"); 
  }

  
  private boolean checkLocalQueue(String hostAndPort) {
    String url = String.format("http://%s/jmsservlet/jmstest?"
        + "url=t3://localhost:" + adminServerPort + "&"
        + "action=receive&dest=jms.testAccountingQueue",
        hostAndPort);

    logger.info("Queue check url {0}", url);
    testUntil(() -> {
      HttpResponse<String> response;
      response = OracleHttpClient.get(url, headers, true);
      return response.statusCode() == 200
          && response.body().contains("Recorded (20) message from [managed-server");
    }, logger, "local queue to be updated");

    testUntil(() -> {
      HttpResponse<String> response;
      response = OracleHttpClient.get(url, headers, true);
      return response.statusCode() == 200
          && (response.body().contains("Recorded (0) message from [managed-server1]")
          || response.body().contains("Recorded (0) message from [managed-server2]"));
    }, logger, "destination topic to be consumed");    
    return true;
  }

  private static void createK8sDomain(String domainUid, String namespace,
      List<String> modelFilesList, List<String> modelPropsList, List<String> applicationsList)
      throws UnknownHostException, IOException {

    // create admin credential secret for domain
    logger.info("Create admin credential secret for domain {0}", domainUid);
    String domain1AdminSecretName = domainUid + "-weblogic-credentials";
    assertDoesNotThrow(() -> createSecretWithUsernamePassword(
        domain1AdminSecretName, namespace, ADMIN_USERNAME_DEFAULT, ADMIN_PASSWORD_DEFAULT),
        String.format("createSecret %s failed for %s", domain1AdminSecretName, domainUid));

    logger.info("Creating image with model files {0} and property files {1}",
        modelFilesList, modelPropsList);
    String domainCreationImage = createImageAndVerify(
        WDT_IMAGE_NAME1, modelFilesList, applicationsList,
        modelPropsList, WEBLOGIC_IMAGE_NAME,
        WEBLOGIC_IMAGE_TAG, WLS, false, domainUid, false);
    logger.info("Created {0} image", domainCreationImage);

    // repo login and push image to registry if necessary
    imageRepoLoginAndPushImageToRegistry(domainCreationImage);

    //create domain1
    createDomain(domainUid, namespace, domain1AdminSecretName, domainCreationImage);

    if (TestConstants.KIND_CLUSTER
        && !TestConstants.WLSIMG_BUILDER.equals(TestConstants.WLSIMG_BUILDER_DEFAULT)) {
      hostHeader = createIngressHostRouting(namespace, domainUid, adminServerName, adminServerPort);
      hostAndPort = localAddress + ":" + TRAEFIK_INGRESS_HTTP_HOSTPORT;
      headers = new HashMap<>();
      headers.put("host", hostHeader);
    }
  }

  private static List<String> buildApplications(String jmsProvider, String localServerAddress) throws IOException {
    //build jmsservlet client application archive
    Path targetDir;
    Path distDir;
    //build application archive for JMS Send/Receive
    targetDir = Paths.get(WORK_DIR,
        ItOnPremCrossDomainTransaction.class.getName() + "/jmsservlet");
    distDir = buildApplication(Paths.get(APP_DIR, "jmsservlet"), null, null,
        "build", domain1Namespace, targetDir);
    logger.info("distDir is {0}", distDir.toString());
    assertTrue(Paths.get(distDir.toString(),
        "jmsservlet.war").toFile().exists(),
        "Application archive is not available");
    String appSource1 = distDir.toString() + "/jmsservlet.war";
    logger.info("Application is in {0}", appSource1);

    //build the MDB application
    Path mdbSrcDir = Paths.get(APP_DIR, "mdbtopic");
    Path mdbDestDir = Paths.get(PROPS_TEMP_DIR, "mdbtopic");
    Files.createDirectories(Path.of(PROPS_TEMP_DIR));
    assertDoesNotThrow(() -> copyFolder(
        mdbSrcDir.toString(), mdbDestDir.toString()),
        "Could not copy mdbtopic application directory");
    Path template = Paths.get(PROPS_TEMP_DIR,
        "mdbtopic/src/application/MdbTopic.java");
    // replace the JMS provider address to K8S or on prem domain depending on the use case
    assertDoesNotThrow(() -> replaceStringInFile(
        template.toString(), "t3://domain2-cluster-cluster-1.domain2-namespace:8001",
        jmsProvider),
        "Could not modify the provider url in MDB Template file");
    // this is needed when JMS provider is in K8S
    if (null != localServerAddress) {
      assertDoesNotThrow(() -> replaceStringInFile(template.toString(), "domain1-admin-server",
          localServerAddress),
          "Could not modify the local server url in MDB Template file");
    }
    //build application archive for MDB
    targetDir = Paths.get(WORK_DIR,
        ItOnPremCrossDomainTransaction.class.getName() + "/mdbtopic");
    distDir = buildApplication(Paths.get(PROPS_TEMP_DIR, "mdbtopic"), null, null,
        "build", domain1Namespace, targetDir);
    logger.info("distDir is {0}", distDir.toString());
    assertTrue(Paths.get(distDir.toString(),
        "mdbtopic.jar").toFile().exists(),
        "Application archive is not available");
    String appSource2 = distDir.toString() + "/mdbtopic.jar";
    logger.info("Application is in {0}", appSource2);
    return List.of(appSource1, appSource2);
  }
  
  private static void createDomain(String domainUid,
      String domainNamespace,
      String adminSecretName,
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
                    .value("-Dweblogic.StdoutDebugEnabled=false "
                        + "-Dweblogic.debug.DebugJTAXA=true "
                        + "-Dweblogic.debug.DebugJTA2PC=true "
                        + "-Dweblogic.security.remoteAnonymousRMIT3Enabled=true "
                        + "-Dweblogic.rjvm.allowUnknownHost=true "))
                .addEnvItem(new V1EnvVar()
                    .name("USER_MEM_ARGS")
                    .value("-Djava.security.egd=file:/dev/./urandom ")))
            .configuration(new Configuration()
                .model(new Model()
                    .domainType("WLS"))
                .introspectorJobActiveDeadlineSeconds(3000L)));
    setPodAntiAffinity(domain);
    logger.info("Create domain custom resource for domainUid {0} in namespace {1}",
        domainUid, domNamespace);
    boolean domCreated = assertDoesNotThrow(() -> createDomainCustomResource(domain),
        String.format("Create domain custom resource failed with ApiException for %s in namespace %s",
            domainUid, domNamespace));
    assertTrue(domCreated, String.format("Create domain custom resource failed with ApiException "
        + "for %s in namespace %s", domainUid, domNamespace));
  }

  private static void createOnPremDomainJMSProvider() throws IOException, InterruptedException {
    String onPremDomainName = "onpremJMSProdomain";
    logger.info("creating on prem domain model list");
    String modelFileList
        = RESOURCE_DIR + "/onpremcrtx/" + WDT_MODEL_FILE_DOMAIN2 + ","
        + RESOURCE_DIR + "/onpremcrtx/" + WDT_MODEL_FILE_JMS2;

    //create model property file list
    Path propFile = File.createTempFile("ext-jms-provider", ".props").toPath();
    Files.writeString(propFile,
        """
        ADMIN_USERNAME=weblogic
        ADMIN_PASSWORD=welcome1
        DOMAIN_NAME=domain2
        DNS_NAME=""" + localAddress,
        StandardOpenOption.TRUNCATE_EXISTING);   
    
    mwHome = Path.of(RESULTS_ROOT, "mwhome");
    domainHome = Path.of(RESULTS_ROOT, "mwhome", "domains", onPremDomainName);
    logger.info("creating on premise domain home {0}", domainHome);
    Files.createDirectories(domainHome);
    
    logger.info("creating on premise {0}", onPremDomainName);
    List<String> command = List.of(
        createDomainScript.toString(),
        "-oracle_home", mwHome.toString(),
        "-domain_type", "WLS",
        "-domain_home", domainHome.toString(),
        "-model_file", modelFileList,
        "-variable_file", propFile.toString()
    );
    runWDTandCreateDomain(command.stream().collect(Collectors.joining(" ")));
    createBootProperties(domainHome.toString());
    wlstScript = Path.of(mwHome.toString(), "oracle_common", "common", "bin", "wlst.sh");
  }
  
  private static void createOnPremDomainJMSClient(String jmsprovider) throws IOException, InterruptedException {
    String onPremDomainName = "onpremJMSClidomain";
    
    logger.info("build the applications to be deployed in onprem domain "
        + "with JMS provider pointing to K8S domain and JMS client as local on prem domain");    
    applicationsList = buildApplications(jmsprovider, InetAddress.getLocalHost().getHostAddress());
    Path buildAppArchiveZip = buildAppArchiveZip(applicationsList);
    
    logger.info("creating model property file for on prem domain {0}", onPremDomainName);
    Path propFile = File.createTempFile(onPremDomainName, ".props").toPath();    
    Files.writeString(propFile,
        "DOMAIN_NAME=" + onPremDomainName + "\n"
        + "ADMIN_USERNAME=weblogic\n"
        + "CALCULATED_LISTENPORTS=true\n"
        + "ADMIN_PASSWORD=welcome1\n", StandardOpenOption.TRUNCATE_EXISTING);
    
    logger.info("creating model file for on prem domain {0}", onPremDomainName);
    Path modelFile = File.createTempFile(onPremDomainName, ".yaml").toPath();
    FileUtils.copy(Path.of(RESOURCE_DIR, "/onpremcrtx/", WDT_MODEL_FILE_DOMAIN1), modelFile);
    //modify the model file to add proper external dns entries
    FileUtils.replaceStringInFile(modelFile.toString(),
        "@@PROP:DOMAIN_NAME@@-admin-server\\.@@PROP:NAMESPACE@@", localAddress);
    FileUtils.replaceStringInFile(modelFile.toString(),
        "@@PROP:DOMAIN_NAME@@-managed-server\\$\\{id\\}\\.@@PROP:NAMESPACE@@", localAddress);
    
    String modelFileList = modelFile.toString() + "," 
        + RESOURCE_DIR + "/onpremcrtx/" + WDT_MODEL_FILE_JMS;
    
    logger.info("creating on premise domain {0} with model files {1} and property file {2}", 
        onPremDomainName, modelFileList, propFile);
    mwHome = Path.of(RESULTS_ROOT, "mwhome");    
    domainHome = Path.of(RESULTS_ROOT, "mwhome", "domains", onPremDomainName);
    logger.info("creating on premise domain home {0}", domainHome);
    Files.createDirectories(domainHome);       
    
    //call WDT create domain to create actual domain
    List<String> command = List.of(
        createDomainScript.toString(),
        "-oracle_home", mwHome.toString(),
        "-domain_type", "WLS",
        "-domain_home", domainHome.toString(),
        "-archive_file", buildAppArchiveZip.toString(),
        "-model_file", modelFileList,
        "-variable_file", propFile.toString()
    );
    runWDTandCreateDomain(command.stream().collect(Collectors.joining(" ")));
    createBootProperties(domainHome.toString());
    wlstScript = Path.of(mwHome.toString(), "oracle_common", "common", "bin", "wlst.sh");
  }  

  private static void downloadAndInstallWDT() throws IOException {
    String wdtUrl = WDT_DOWNLOAD_URL + "/download/weblogic-deploy.zip";
    Path destLocation = Path.of(DOWNLOAD_DIR, "wdt", "weblogic-deploy.zip");
    createDomainScript = Path.of(DOWNLOAD_DIR, "wdt", "weblogic-deploy", "bin", "createDomain.sh");
    if (!Files.exists(destLocation) && !Files.exists(createDomainScript)) {
      logger.info("Downloading WDT to {0}", destLocation);
      Files.createDirectories(destLocation.getParent());
      OracleHttpClient.downloadFile(wdtUrl, destLocation.toString(), null, null, 3);
      String cmd = "cd " + destLocation.getParent() + ";unzip " + destLocation;
      assertTrue(Command.withParams(new CommandParams().command(cmd)).execute(), "unzip command failed");
    }
    assertTrue(Files.exists(createDomainScript), "could not find createDomain.sh script");
  }

  private static void runWDTandCreateDomain(String command) {
    logger.info("running {0}", command);
    assertTrue(Command.withParams(new CommandParams().command(command)).execute(), "create domain failed");
  }
  
  private static void createBootProperties(String domainHome) throws IOException {    
    List<String> servers = List.of("admin-server", "managed-server1", "managed-server2");
    for (String server : servers) {
      Path securityDir = Files.createDirectories(Path.of(domainHome, "servers", server, "security"));
      Files.deleteIfExists(Path.of(securityDir.toString(), "boot.properties"));
      Path bootFile = Files.createFile(Path.of(securityDir.toString(), "boot.properties"));
      logger.info("creating boot.properties {0}", bootFile);
      Files.writeString(bootFile, "username=weblogic\n", StandardOpenOption.TRUNCATE_EXISTING);
      Files.writeString(bootFile, "password=welcome1\n", StandardOpenOption.APPEND);
      assertTrue(Files.exists(bootFile), "failed to create boot.properties file");
    }
  }
  
  private static void startServers(Path domainHome) throws InterruptedException, UnknownHostException {
    startWebLogicServer(List.of(domainHome.toString() + "/bin/startWebLogic.sh"),
        Path.of(domainHome.toString(), "admin-server.log"));
    TimeUnit.SECONDS.sleep(15);
    startWebLogicServer(List.of(domainHome.toString() + "/bin/startManagedWebLogic.sh",
        "managed-server1",
        "t3://" + localAddress + ":" + adminServerPort),
        Path.of(domainHome.toString(), "managed-server1.log"));
    startWebLogicServer(List.of(domainHome.toString() + "/bin/startManagedWebLogic.sh",
        "managed-server2",
        "t3://" + localAddress + ":" + adminServerPort),
        Path.of(domainHome.toString(), "managed-server2.log"));
    TimeUnit.SECONDS.sleep(15);
  }

  private static void startWebLogicServer(List<String> command, Path logFile) {
    Thread serverThread = new Thread(() -> {
      ProcessBuilder processBuilder = new ProcessBuilder(command);
      Map<String, String> combinedEnvMap = new HashMap<>();
      combinedEnvMap.putAll(System.getenv());
      combinedEnvMap.put("JAVA_OPTIONS", javaOptions);
      processBuilder.environment().putAll(combinedEnvMap);
      processBuilder.redirectError(new File(logFile.toString()));
      processBuilder.redirectOutput(new File(logFile.toString()));
      try {
        logger.info("Starting server with command : {0}", String.join(" ", command));
        Process process = processBuilder.start();
        logger.info("Server is starting...");
        process.waitFor(); // This will wait for the process to complete in the thread
        logger.info("Server has shut down.");
      } catch (IOException | InterruptedException e) {
        logger.info(e.getLocalizedMessage());
      }
    });
    serverThread.start();
  }

  private static void shutdownServers(List<String> command, Path logFile) throws InterruptedException {
    logger.info("shutting down servers using wlst " + String.join(" ", command));
    executeWlst(command, logFile, false);
  }

  private static void modifyDNS() throws UnknownHostException, IOException, InterruptedException {
    String managedServerPrefix = domainUid1 + "-managed-server";
    String dnsEntries = localAddress
        + " " + managedServerPrefix + "1." + domain1Namespace
        + " " + managedServerPrefix + "2." + domain1Namespace
        + " " + domainUid1 + "-" + adminServerName + "." + domain1Namespace
        + " " + domainUid3 + "-managed-server1 " + domainUid3 + "-managed-server2";
    String command = "echo \"" + dnsEntries + "\" | sudo tee -a /etc/hosts > /dev/null";
    logger.info("adding DNS entries with command {0}", command);
    ExecResult result;
    result = exec(command, true);
    getLogger().info("The command returned exit value: " + result.exitValue()
        + " command output: " + result.stderr() + "\n" + result.stdout());
    assertEquals(0, result.exitValue(), "adding DNS entries for on prem domain failed");
  }

  private static void execK8SCLI(Path routingFile) throws IOException, InterruptedException {    
    String command = KUBERNETES_CLI + " apply -f " + routingFile;
    logger.info("creating ingress routing rules for onprem domain \n{0}", command);
    ExecResult result;
    result = exec(command, true);
    getLogger().info("The command returned exit value: " + result.exitValue()
        + " command output: " + result.stderr() + "\n" + result.stdout());
    assertEquals(0, result.exitValue(), "creating on prem routing rules failed");
  }
  
  private static String installTraefikForPremDomain() {
    logger.info("installing traefik lb for on prem domain");
    return installAndVerifyTraefik(domain1Namespace,
        IT_ONPREMCRDOMAINTX_INGRESS_HTTP_NODEPORT, 0).getIngressClassName();
  }

  private static Path buildAppArchiveZip(List<String> archiveAppsList) {
    AppParams appParams = defaultAppParams()
        .appArchiveDir(ARCHIVE_DIR + generateRandomString()).srcDirList(archiveAppsList);
    assertTrue(archiveApp(appParams));
    return Path.of(appParams.appArchiveDir(), appParams.appName() + ".zip");
  }

  public static String generateRandomString() {
    int length = 10; // Desired length of the random string
    String characterSet = Charset.forName("US-ASCII").toString();
    Random random = new Random();
    StringBuilder sb = new StringBuilder(length);

    for (int i = 0; i < length; i++) {
      sb.append(characterSet.charAt(random.nextInt(characterSet.length())));
    }
    return sb.toString();
  }
  
  private static void executeWlst(List<String> command, Path logFile, boolean wait) throws InterruptedException {
    Thread serverThread = new Thread(() -> {
      ProcessBuilder processBuilder = new ProcessBuilder(command);
      Map<String, String> combinedEnvMap = new HashMap<>();
      combinedEnvMap.putAll(System.getenv());
      processBuilder.environment().putAll(combinedEnvMap);
      processBuilder.redirectError(new File(logFile.toString()));
      processBuilder.redirectOutput(new File(logFile.toString()));
      try {
        logger.info("running wlst script" + String.join(" ", command));
        Process process = processBuilder.start();
        process.waitFor(); // This will wait for the process to complete in the thread
        logger.info("finished running wlst script.");
      } catch (IOException | InterruptedException e) {
        logger.info(e.getLocalizedMessage());
      }
    });
    serverThread.start();
    if (wait) {
      while (serverThread.isAlive()) {
        logger.info("waiting for the wlst script to finish executing");
        TimeUnit.SECONDS.sleep(5);
      }
    }
  }
  
}

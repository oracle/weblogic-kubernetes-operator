// Copyright (c) 2024, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes;

import java.net.UnknownHostException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.kubernetes.client.openapi.models.V1EnvVar;
import io.kubernetes.client.openapi.models.V1HTTPIngressPath;
import io.kubernetes.client.openapi.models.V1HTTPIngressRuleValue;
import io.kubernetes.client.openapi.models.V1IngressBackend;
import io.kubernetes.client.openapi.models.V1IngressRule;
import io.kubernetes.client.openapi.models.V1IngressServiceBackend;
import io.kubernetes.client.openapi.models.V1LocalObjectReference;
import io.kubernetes.client.openapi.models.V1ServiceBackendPort;
import oracle.weblogic.domain.AuxiliaryImage;
import oracle.weblogic.domain.Channel;
import oracle.weblogic.domain.ClusterList;
import oracle.weblogic.domain.ClusterSpec;
import oracle.weblogic.domain.DomainResource;
import oracle.weblogic.kubernetes.actions.impl.AppParams;
import oracle.weblogic.kubernetes.actions.impl.Cluster;
import oracle.weblogic.kubernetes.actions.impl.NginxParams;
import oracle.weblogic.kubernetes.actions.impl.Service;
import oracle.weblogic.kubernetes.actions.impl.primitive.Command;
import oracle.weblogic.kubernetes.actions.impl.primitive.CommandParams;
import oracle.weblogic.kubernetes.actions.impl.primitive.WitParams;
import oracle.weblogic.kubernetes.annotations.IntegrationTest;
import oracle.weblogic.kubernetes.annotations.Namespaces;
import oracle.weblogic.kubernetes.logging.LoggingFacade;
import oracle.weblogic.kubernetes.utils.ExecCommand;
import oracle.weblogic.kubernetes.utils.ExecResult;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfEnvironmentVariable;

import static java.net.InetAddress.getLocalHost;
import static oracle.weblogic.kubernetes.TestConstants.ADMIN_PASSWORD_DEFAULT;
import static oracle.weblogic.kubernetes.TestConstants.ADMIN_USERNAME_DEFAULT;
import static oracle.weblogic.kubernetes.TestConstants.DOMAIN_API_VERSION;
import static oracle.weblogic.kubernetes.TestConstants.DOMAIN_IMAGES_PREFIX;
import static oracle.weblogic.kubernetes.TestConstants.IMAGE_PULL_POLICY;
import static oracle.weblogic.kubernetes.TestConstants.K8S_NODEPORT_HOST;
import static oracle.weblogic.kubernetes.TestConstants.K8S_NODEPORT_HOSTNAME;
import static oracle.weblogic.kubernetes.TestConstants.KUBERNETES_CLI;
import static oracle.weblogic.kubernetes.TestConstants.OKE_CLUSTER;
import static oracle.weblogic.kubernetes.TestConstants.RESULTS_ROOT;
import static oracle.weblogic.kubernetes.TestConstants.TRAEFIK_INGRESS_HTTP_HOSTPORT;
import static oracle.weblogic.kubernetes.TestConstants.WEBLOGIC_IMAGE_TO_USE_IN_SPEC;
import static oracle.weblogic.kubernetes.actions.ActionConstants.ARCHIVE_DIR;
import static oracle.weblogic.kubernetes.actions.ActionConstants.RESOURCE_DIR;
import static oracle.weblogic.kubernetes.actions.ActionConstants.WORK_DIR;
import static oracle.weblogic.kubernetes.actions.TestActions.buildAppArchive;
import static oracle.weblogic.kubernetes.actions.TestActions.defaultAppParams;
import static oracle.weblogic.kubernetes.actions.TestActions.getServiceNodePort;
import static oracle.weblogic.kubernetes.actions.TestActions.listIngresses;
import static oracle.weblogic.kubernetes.utils.ApplicationUtils.callWebAppAndWaitTillReady;
import static oracle.weblogic.kubernetes.utils.AuxiliaryImageUtils.createAndPushAuxiliaryImage;
import static oracle.weblogic.kubernetes.utils.ClusterUtils.createClusterAndVerify;
import static oracle.weblogic.kubernetes.utils.ClusterUtils.createClusterResource;
import static oracle.weblogic.kubernetes.utils.ClusterUtils.createClusterResourceAndAddReferenceToDomain;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.createIngressHostRouting;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.formatIPv6Host;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.getDateAndTimeStamp;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.getHostAndPort;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.getNextFreePort;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.getServiceExtIPAddrtOke;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.runClientInsidePod;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.runJavacInsidePod;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.testUntil;
import static oracle.weblogic.kubernetes.utils.DomainUtils.createDomainAndVerify;
import static oracle.weblogic.kubernetes.utils.FileUtils.copyFileToPod;
import static oracle.weblogic.kubernetes.utils.FileUtils.generateFileFromTemplate;
import static oracle.weblogic.kubernetes.utils.ImageUtils.createTestRepoSecret;
import static oracle.weblogic.kubernetes.utils.LoadBalancerUtils.createIngressAndRetryIfFail;
import static oracle.weblogic.kubernetes.utils.LoadBalancerUtils.installAndVerifyNginx;
import static oracle.weblogic.kubernetes.utils.OperatorUtils.installAndVerifyOperator;
import static oracle.weblogic.kubernetes.utils.PodUtils.checkPodReady;
import static oracle.weblogic.kubernetes.utils.PodUtils.getExternalServicePodName;
import static oracle.weblogic.kubernetes.utils.PodUtils.setPodAntiAffinity;
import static oracle.weblogic.kubernetes.utils.SecretUtils.createSecretWithUsernamePassword;
import static oracle.weblogic.kubernetes.utils.SecretUtils.createSecretsForImageRepos;
import static oracle.weblogic.kubernetes.utils.ThreadSafeLogger.getLogger;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Cross domain transaction with CrossDomainSecurityEnabled set to true.
 */
@DisplayName("Verify cross domain transaction is successful with CrossDomainSecurityEnabled set to true")
@IntegrationTest
@Tag("kind-parallel")
@Tag("oke-weekly-sequential")
class ItCrossDomainTransactionSecurity {

  private static final String auxImageName1 = DOMAIN_IMAGES_PREFIX + "domain1-cdxaction-aux";
  private static final String auxImageName2 = DOMAIN_IMAGES_PREFIX + "domain2-cdxaction-aux";
  private static final String PROPS_TEMP_DIR = RESULTS_ROOT + "/crossdomsecurity";


  private static String opNamespace = null;
  private static String domainNamespace = null;
  private static String domainUid1 = "domain1";
  private static String domainUid2 = "domain2";
  private static String adminServerName = "admin-server";
  private static String domain1AdminServerPodName = domainUid1 + "-" + adminServerName;
  private static String domain1ManagedServerPrefix = domainUid1 + "-managed-server";
  private static String domain2AdminServerPodName = domainUid2 + "-" + adminServerName;
  private static String domain2ManagedServerPrefix = domainUid2 + "-managed-server";
  private static LoggingFacade logger = null;
  private static int replicaCount = 2;
  private static String clusterName = "cluster-2";
  private static int t3ChannelPort1 = getNextFreePort();
  private static int t3ChannelPort2 = getNextFreePort();
  private static String domain1AdminExtSvcRouteHost = null;
  private static String hostAndPort1 = null;
  private static String hostHeader1;
  private static String nginxNamespace = null;
  private static NginxParams nginxHelmParams = null;
  private static int nginxNodePort;
  private static Path tlsCertFile;
  private static Path tlsKeyFile;
  private static Path jksTrustFile;
  private static String tlsSecretName = domainUid2 + "-test-tls-secret";
  private static String hostAddress = K8S_NODEPORT_HOST;



  /**
   * Install Operator.
   * @param namespaces list of namespaces created by the IntegrationTestWatcher by the
   *     JUnit engine parameter resolution mechanism
   */
  @BeforeAll
  public static void initAll(@Namespaces(3) List<String> namespaces) throws UnknownHostException {
    logger = getLogger();

    // get a new unique opNamespace
    logger.info("Creating unique namespace for Operator");
    assertNotNull(namespaces.get(0), "Namespace list is null");
    opNamespace = namespaces.get(0);

    logger.info("Creating unique namespace for Domain");
    assertNotNull(namespaces.get(1), "Namespace list is null");
    domainNamespace = namespaces.get(1);

    // get a unique Nginx namespace
    logger.info("Assign a unique namespace for Nginx");
    assertNotNull(namespaces.get(2), "Namespace list is null");
    nginxNamespace = namespaces.get(2);


    // Create the repo secret to pull the image
    // this secret is used only for non-kind cluster
    createTestRepoSecret(domainNamespace);

    // install and verify operator
    installAndVerifyOperator(opNamespace, domainNamespace);

    if (OKE_CLUSTER) {
      logger.info("Installing Nginx controller using helm");
      // install and verify Nginx
      nginxHelmParams = installAndVerifyNginx(nginxNamespace, 0, 0);
    }

    buildDomains();

    logger.info("2 domains with crossDomainSecurity enabled start up!");
    int domain1AdminServiceNodePort
          = getServiceNodePort(domainNamespace, getExternalServicePodName(domain1AdminServerPodName), "default");
    assertNotEquals(-1, domain1AdminServiceNodePort, "domain1 admin server default node port is not valid");
    logger.info("domain1AdminServiceNodePort is: " + domain1AdminServiceNodePort);
    int domain2AdminServiceNodePort
          = getServiceNodePort(domainNamespace, getExternalServicePodName(domain2AdminServerPodName), "default");
    assertNotEquals(-1, domain1AdminServiceNodePort, "domain2 admin server default node port is not valid");
    logger.info("domain2AdminServiceNodePort is: " + domain2AdminServiceNodePort);

    if (OKE_CLUSTER) {
      createNginxIngressPathRoutingRules();
      String nginxServiceName = nginxHelmParams.getHelmParams().getReleaseName() + "-ingress-nginx-controller";
      hostAndPort1 = getServiceExtIPAddrtOke(nginxServiceName, nginxNamespace);
    } else {
      hostAndPort1 = getHostAndPort(domain1AdminExtSvcRouteHost, domain1AdminServiceNodePort);
      if (TestConstants.KIND_CLUSTER
          && !TestConstants.WLSIMG_BUILDER.equals(TestConstants.WLSIMG_BUILDER_DEFAULT)) {
        hostHeader1 = createIngressHostRouting(domainNamespace, domainUid1, adminServerName, 7001);
        hostAndPort1 = formatIPv6Host(getLocalHost().getHostAddress())
            + ":" + TRAEFIK_INGRESS_HTTP_HOSTPORT;

      }
    }
    logger.info("hostHeader1 for domain1 is: " + hostHeader1);
    logger.info("hostAndPort1 for domain1 is: " + hostAndPort1);
  }

  /**
   * Configure two domains d1 and d2 with CrossDomainSecurityEnabled set to true
   * On both domains create a user (cross-domain) with group CrossDomainConnectors
   * Add required Credential Mapping
   * Deploy a JSP on d1's admin server that takes 2 parameteers
   * a. The tx action b. the d2's cluster service url
   * Starts a User transcation
   * Using t3 send 10 messgaes to a distributed destination (jms.testUniformQueue) on d2 that has 2 members
   * Using t3 Send a message to local destination (jms.admin.adminQueue) on d1
   * Commit/rollback the transation
   * Using t3 receive the messages from the distributed destination (jms.testUniformQueue) on d2
   * Using t3 receive the message from the local destination (jms.admin.adminQueue) on d1
   */
  @Test
  @DisplayName("Check cross domain transaction works")
  void testCrossDomainTxWithCrossDomainSecurityEnabled() throws UnknownHostException {
    
    // build the standalone JMS Client on Admin pod
    String destLocation = "/u01/JmsSendReceiveClient.java";
    assertDoesNotThrow(() -> copyFileToPod(domainNamespace,
        domain1AdminServerPodName, "",
        Paths.get(RESOURCE_DIR, "jms", "JmsSendReceiveClient.java"),
        Paths.get(destLocation)));
    runJavacInsidePod(domain1AdminServerPodName, domainNamespace, destLocation);

    //In a UserTransaction send 10 msg to remote udq and 1 msg to local queue and commit the tx
    StringBuffer curlCmd1 = new StringBuffer("curl -skg --show-error --noproxy '*' ");
    if (TestConstants.KIND_CLUSTER
        && !TestConstants.WLSIMG_BUILDER.equals(TestConstants.WLSIMG_BUILDER_DEFAULT)) {
      curlCmd1.append(" -H 'host: " + hostHeader1 + "' ");
    }
    String url1 = "\"http://" + hostAndPort1
        + "/sample_war/dtx.jsp?remoteurl=t3://domain2-cluster-cluster-2:8001&action=commit\"";
    curlCmd1.append(url1);
    logger.info("Executing curl command: {0}", curlCmd1);
    assertTrue(getCurlResult(curlCmd1.toString()).contains("Message sent in a commit User Transation"),
          "Didn't send expected msg ");
    // Discard the first result. Sometime It fails with Error
    if (!getCurlResult(curlCmd1.toString()).contains("Message sent in a commit User Transation")) {
      assertTrue(getCurlResult(curlCmd1.toString()).contains("Message sent in a commit User Transation"),
           "Didn't send expected msg ");
    }

    //receive msg from the udq that has 2 memebers
    StringBuffer curlCmd2 = new StringBuffer("curl -j --show-error --noproxy '*' ");
    if (TestConstants.KIND_CLUSTER
        && !TestConstants.WLSIMG_BUILDER.equals(TestConstants.WLSIMG_BUILDER_DEFAULT)) {
      curlCmd2.append(" -H 'host: " + hostHeader1 + "' ");
    }
    String url2 = "\"http://" + hostAndPort1
          + "/sample_war/get.jsp?remoteurl=t3://domain2-cluster-cluster-2:8001&action=recv&dest=jms.testUniformQueue\"";
    curlCmd2.append(url2);
    logger.info("Executing curl command: {0}", curlCmd2);
    for (int i = 0; i < 2; i++) {
      assertTrue(getCurlResult(curlCmd2.toString()).contains("Total Message(s) Received : 5"),
          "Didn't receive expected msg count from remote queue");
    }

    // receive 1 msg from the local queue
    testUntil(
        runClientInsidePod(domain1AdminServerPodName, domainNamespace,
            "/u01", "JmsSendReceiveClient",
            "t3://" + "localhost" + ":" + "7001", "receive", "jms.admin.adminQueue", "1"),
        logger,
        "Wait for JMS Client to send/recv msg");

    //In a UserTransaction send 10 msg to remote udq and 1 msg to local queue and rollback the tx
    StringBuffer curlCmd3 = new StringBuffer("curl -skg --show-error --noproxy '*' ");
    if (TestConstants.KIND_CLUSTER
        && !TestConstants.WLSIMG_BUILDER.equals(TestConstants.WLSIMG_BUILDER_DEFAULT)) {
      curlCmd3.append(" -H 'host: " + hostHeader1 + "' ");
    }
    String url3 = "\"http://" + hostAndPort1
        + "/sample_war/dtx.jsp?remoteurl=t3://domain2-cluster-cluster-2:8001&action=rollback\"";
    curlCmd3.append(url3);
    logger.info("Executing curl command: {0}", curlCmd3);
    assertTrue(getCurlResult(curlCmd3.toString()).contains("Message sent in a rolled-back User Transation"),
          "Didn't send expected msg ");

    //receive 0 msg from the udq that has 2 memebers
    StringBuffer curlCmd4 = new StringBuffer("curl -j --show-error --noproxy '*' ");
    if (TestConstants.KIND_CLUSTER
        && !TestConstants.WLSIMG_BUILDER.equals(TestConstants.WLSIMG_BUILDER_DEFAULT)) {
      curlCmd4.append(" -H 'host: " + hostHeader1 + "' ");
    }
    String url4 = "\"http://" + hostAndPort1
          + "/sample_war/get.jsp?remoteurl=t3://domain2-cluster-cluster-2:8001&action=recv&dest=jms.testUniformQueue\"";
    curlCmd4.append(url4);
    logger.info("Executing curl command: {0}", curlCmd4);
    for (int i = 0; i < 2; i++) {
      assertTrue(getCurlResult(curlCmd4.toString()).contains("Total Message(s) Received : 0"),
          "Didn't receive expected msg count from remote queue");
    }

    // receive 0 msg from the local queue
    testUntil(
        runClientInsidePod(domain1AdminServerPodName, domainNamespace,
            "/u01", "JmsSendReceiveClient",
            "t3://" + "localhost" + ":" + "7001", "receive", "jms.admin.adminQueue", "0"),
        logger,
        "Wait for JMS Client to send/recv msg");
  }

  /**
   * Configure two domains d1 and d2 with CrossDomainSecurityEnabled set to true
   * On both domains create a user (cross-domain) with group CrossDomainConnectors
   * Add required Credential Mapping
   * Deploy a JSP on d1's admin server that takes 2 parameteers
   * a. The tx action b. the d2's cluster service url
   * Starts a User transcation
   * Using t3s send 10 messgaes to a distributed destination (jms.testUniformQueue) on d2 that has 2 members
   * Using t3s Send a message to local destination (jms.admin.adminQueue) on d1
   * Commit/rollback the transation
   * Using t3s receive the messages from the distributed destination (jms.testUniformQueue) on d2
   * Using t3s Receive the message from the local destination (jms.admin.adminQueue) on d1
   */
  @Test
  @DisplayName("Check cross domain transaction works when SSL enabled")
  @DisabledIfEnvironmentVariable(named = "OKE_CLUSTER", matches = "true")
  void testCrossDomainTxWithCrossDomainSecurityAndSSLEnabled() throws UnknownHostException {

    // Create SSL certificate and key using openSSL with SAN extension
    createCertKeyFiles(hostAddress);
    // Create kubernates secret using genereated certificate and key
    createSecretWithTLSCertKey(tlsSecretName);
    // Import the tls certificate into a JKS truststote to be used while
    // running the standalone client.
    importKeytoTrustStore();

    //In a UserTransaction send 10 msg to remote udq and 1 msg to local queue and commit the tx
    StringBuffer curlCmd1 = new StringBuffer("curl -skg --show-error --noproxy '*' ");
    if (TestConstants.KIND_CLUSTER
        && !TestConstants.WLSIMG_BUILDER.equals(TestConstants.WLSIMG_BUILDER_DEFAULT)) {
      curlCmd1.append(" -H 'host: " + hostHeader1 + "' ");
    }
    String url1 = "\"http://" + hostAndPort1
        + "/sample_war/dtx.jsp?remoteurl=t3s://domain2-cluster-cluster-2:8500&action=commit\"";
    curlCmd1.append(url1);
    logger.info("Executing curl command: {0}", curlCmd1);
    assertTrue(getCurlResult(curlCmd1.toString()).contains("Message sent in a commit User Transation"),
          "Didn't send expected msg ");

    //receive msg from the udq that has 2 memebers
    StringBuffer curlCmd2 = new StringBuffer("curl -j --show-error --noproxy '*' ");
    if (TestConstants.KIND_CLUSTER
        && !TestConstants.WLSIMG_BUILDER.equals(TestConstants.WLSIMG_BUILDER_DEFAULT)) {
      curlCmd2.append(" -H 'host: " + hostHeader1 + "' ");
    }
    String url2 = "\"http://" + hostAndPort1
          + "/sample_war/get.jsp?remoteurl="
          + "t3s://domain2-cluster-cluster-2:8500&action=recv&dest=jms.testUniformQueue\"";
    curlCmd2.append(url2);
    logger.info("Executing curl command: {0}", curlCmd2);
    for (int i = 0; i < 2; i++) {
      assertTrue(getCurlResult(curlCmd2.toString()).contains("Total Message(s) Received : 5"),
          "Didn't receive expected msg count from remote queue");
    }

    // receive 1 msg from the local queue
    logger.info("Receiving 1 msg from the local queue");
    StringBuffer curlCmdx = new StringBuffer("curl -j --show-error --noproxy '*' ");
    if (TestConstants.KIND_CLUSTER
        && !TestConstants.WLSIMG_BUILDER.equals(TestConstants.WLSIMG_BUILDER_DEFAULT)) {
      curlCmdx.append(" -H 'host: " + hostHeader1 + "' ");
    }
    String urlx = "\"http://" + hostAndPort1
          + "/sample_war/get.jsp?remoteurl="
          + "t3s://domain1-admin-server:7002&action=recv&dest=jms.admin.adminQueue\"";
    curlCmdx.append(urlx);
    logger.info("Executing curl command for local queue: {0}", curlCmdx);
    assertTrue(getCurlResult(curlCmdx.toString()).contains("Total Message(s) Received : 1"),
          "Didn't receive expected msg count from local queue");

    //In a UserTransaction send 10 msg to remote udq and 1 msg to local queue and rollback the tx
    StringBuffer curlCmd3 = new StringBuffer("curl -skg --show-error --noproxy '*' ");
    if (TestConstants.KIND_CLUSTER
        && !TestConstants.WLSIMG_BUILDER.equals(TestConstants.WLSIMG_BUILDER_DEFAULT)) {
      curlCmd3.append(" -H 'host: " + hostHeader1 + "' ");
    }
    String url3 = "\"http://" + hostAndPort1
        + "/sample_war/dtx.jsp?remoteurl=t3s://domain2-cluster-cluster-2:8500&action=rollback\"";
    curlCmd3.append(url3);
    logger.info("Executing curl command: {0}", curlCmd3);
    assertTrue(getCurlResult(curlCmd3.toString()).contains("Message sent in a rolled-back User Transation"),
          "Didn't send expected msg ");

    //receive 0 msg from the udq that has 2 memebers
    StringBuffer curlCmd4 = new StringBuffer("curl -j --show-error --noproxy '*' ");
    if (TestConstants.KIND_CLUSTER
        && !TestConstants.WLSIMG_BUILDER.equals(TestConstants.WLSIMG_BUILDER_DEFAULT)) {
      curlCmd4.append(" -H 'host: " + hostHeader1 + "' ");
    }
    String url4 = "\"http://" + hostAndPort1
          + "/sample_war/get.jsp?remoteurl="
          + "t3s://domain2-cluster-cluster-2:8500&action=recv&dest=jms.testUniformQueue\"";
    curlCmd4.append(url4);
    logger.info("Executing curl command: {0}", curlCmd4);
    for (int i = 0; i < 2; i++) {
      assertTrue(getCurlResult(curlCmd4.toString()).contains("Total Message(s) Received : 0"),
          "Didn't receive expected msg count from remote queue");
    }

    // receive 0 msg from the local queue
    logger.info("Receiving 0 msg from the local queue");
    StringBuffer curlCmdy = new StringBuffer("curl -j --show-error --noproxy '*' ");
    if (TestConstants.KIND_CLUSTER
        && !TestConstants.WLSIMG_BUILDER.equals(TestConstants.WLSIMG_BUILDER_DEFAULT)) {
      curlCmdy.append(" -H 'host: " + hostHeader1 + "' ");
    }
    String urly = "\"http://" + hostAndPort1
          + "/sample_war/get.jsp?remoteurl="
          + "t3s://domain1-admin-server:7002&action=recv&dest=jms.admin.adminQueue\"";
    curlCmdy.append(urly);
    logger.info("Executing curl command for local queue: {0}", curlCmdy);
    assertTrue(getCurlResult(curlCmdx.toString()).contains("Total Message(s) Received : 0"),
          "Didn't receive expected msg count from local queue");

  }

  private static String createAuxImage(String imageName, String imageTag, List<String> wdtModelFile,
                                       String wdtVariableFile) {

    // build sample-app application
    AppParams appParams = defaultAppParams()
        .srcDirList(Collections.singletonList("crossdomain-security"))
        .appArchiveDir(ARCHIVE_DIR + ItCrossDomainTransactionSecurity.class.getName())
        .appName("crossdomainsec");
    assertTrue(buildAppArchive(appParams),
        String.format("Failed to create app archive for %s", "crossdomainsec"));
    List<String> archiveList = Collections.singletonList(appParams.appArchiveDir() + "/" + "crossdomainsec" + ".zip");

    //create an auxiliary image with model and application
    WitParams witParams
        = new WitParams()
            .modelImageName(imageName)
            .modelImageTag(imageTag)
            .modelFiles(wdtModelFile)
            .modelVariableFiles(Arrays.asList(wdtVariableFile))
            .modelArchiveFiles(archiveList);
    createAndPushAuxiliaryImage(imageName, imageTag, witParams);

    return imageName + ":" + imageTag;
  }

  private static void buildDomains() {

    String auxImageTag = getDateAndTimeStamp();
    String modelDir = RESOURCE_DIR + "/" + "crossdomsecurity";
    List<String> modelList = new ArrayList<>();
    modelList.add(modelDir + "/" + "model.dynamic.wls.yaml");
    modelList.add(modelDir + "/sparse.jdbc.yaml");
    modelList.add(modelDir + "/sparse.jms.yaml");
    modelList.add(modelDir + "/sparse.application.yaml");

    // create WDT properties file for the WDT model domain1
    Path wdtVariableFile1 = Paths.get(WORK_DIR, ItCrossDomainTransactionSecurity.class.getName(),
        "wdtVariable1.properties");
    logger.info("The K8S_NODEPORT_HOSTNAME is: " + K8S_NODEPORT_HOSTNAME);
    logger.info("The K8S_NODEPORT_HOST is: " + K8S_NODEPORT_HOST);
    logger.info("In the domain1 t3ChannelPort1 is: " + t3ChannelPort1);
    logger.info("In the domain2 t3ChannelPort2 is " + t3ChannelPort2);

    assertDoesNotThrow(() -> {
      Files.deleteIfExists(wdtVariableFile1);
      Files.createDirectories(wdtVariableFile1.getParent());
      Files.writeString(wdtVariableFile1, "DOMAIN_UID=domain1\n", StandardOpenOption.CREATE);
      Files.writeString(wdtVariableFile1, "CLUSTER_NAME=cluster-1\n", StandardOpenOption.APPEND);
      Files.writeString(wdtVariableFile1, "ADMIN_SERVER_NAME=admin-server\n", StandardOpenOption.APPEND);
      Files.writeString(wdtVariableFile1, "MANAGED_SERVER_BASE_NAME=managed-server\n", StandardOpenOption.APPEND);
      Files.writeString(wdtVariableFile1, "MANAGED_SERVER_PORT=8001\n", StandardOpenOption.APPEND);
      Files.writeString(wdtVariableFile1, "MANAGED_SERVER_COUNT=4\n", StandardOpenOption.APPEND);
      Files.writeString(wdtVariableFile1, "T3PUBLICADDRESS=" + K8S_NODEPORT_HOSTNAME + "\n", StandardOpenOption.APPEND);
      Files.writeString(wdtVariableFile1, "T3CHANNELPORT=" + t3ChannelPort1 + "\n", StandardOpenOption.APPEND);
      Files.writeString(wdtVariableFile1, "REMOTE_DOMAIN=domain2\n", StandardOpenOption.APPEND);
    });

    // create auxiliary image for domain1
    String auxImage1 = createAuxImage(auxImageName1, auxImageTag, modelList, wdtVariableFile1.toString());

    // create WDT properties file for the WDT model domain2
    Path wdtVariableFile2 = Paths.get(WORK_DIR, ItCrossDomainTransactionSecurity.class.getName(),
        "wdtVariable2.properties");
    assertDoesNotThrow(() -> {
      Files.deleteIfExists(wdtVariableFile2);
      Files.createDirectories(wdtVariableFile2.getParent());
      Files.writeString(wdtVariableFile2, "DOMAIN_UID=domain2\n", StandardOpenOption.CREATE);
      Files.writeString(wdtVariableFile2, "CLUSTER_NAME=cluster-2\n", StandardOpenOption.APPEND);
      Files.writeString(wdtVariableFile2, "ADMIN_SERVER_NAME=admin-server\n", StandardOpenOption.APPEND);
      Files.writeString(wdtVariableFile2, "MANAGED_SERVER_BASE_NAME=managed-server\n", StandardOpenOption.APPEND);
      Files.writeString(wdtVariableFile2, "MANAGED_SERVER_PORT=8001\n", StandardOpenOption.APPEND);
      Files.writeString(wdtVariableFile2, "MANAGED_SERVER_COUNT=4\n", StandardOpenOption.APPEND);
      Files.writeString(wdtVariableFile2, "T3PUBLICADDRESS=" + K8S_NODEPORT_HOSTNAME + "\n", StandardOpenOption.APPEND);
      Files.writeString(wdtVariableFile2, "T3CHANNELPORT=" + t3ChannelPort2 + "\n", StandardOpenOption.APPEND);
      Files.writeString(wdtVariableFile2, "REMOTE_DOMAIN=domain1\n", StandardOpenOption.APPEND);
    });

    // create auxiliary image for domain2
    String auxImage2 = createAuxImage(auxImageName2, auxImageTag, modelList, wdtVariableFile2.toString());

    // create admin credential secret for domain1
    logger.info("Create admin credential secret for domain1");
    String domain1AdminSecretName = domainUid1 + "-weblogic-credentials";
    assertDoesNotThrow(() -> createSecretWithUsernamePassword(
        domain1AdminSecretName, domainNamespace, ADMIN_USERNAME_DEFAULT, ADMIN_PASSWORD_DEFAULT),
        String.format("createSecret %s failed for %s", domain1AdminSecretName, domainUid1));

    // create admin credential secret for domain2
    logger.info("Create admin credential secret for domain2");
    String domain2AdminSecretName = domainUid2 + "-weblogic-credentials";
    assertDoesNotThrow(() -> createSecretWithUsernamePassword(
        domain2AdminSecretName, domainNamespace, ADMIN_USERNAME_DEFAULT, ADMIN_PASSWORD_DEFAULT),
        String.format("createSecret %s failed for %s", domain2AdminSecretName, domainUid2));

    // create encryption secret
    logger.info("Create encryption secret");
    String encryptionSecretName = "encryptionsecret";
    createSecretWithUsernamePassword(encryptionSecretName, domainNamespace,
        "weblogicenc", "weblogicenc");

    //create domain1 and verify its running
    createDomain(domainUid1, auxImage1, domainNamespace, domain1AdminSecretName, encryptionSecretName,
        "cluster-1", domain1AdminServerPodName, domain1ManagedServerPrefix, t3ChannelPort1);

    //create domain2 and verify its running
    createDomain(domainUid2, auxImage2, domainNamespace, domain2AdminSecretName, encryptionSecretName,
        "cluster-2", domain2AdminServerPodName, domain2ManagedServerPrefix, t3ChannelPort2);
  }

  private static void createDomain(String domainUid, String imageName, String domainNamespace, String
      domainAdminSecretName, String encryptionSecretName, String clusterName, String adminServerPodName,
      String managedServerPrefix, int t3ChannelPort) {

    final String auxiliaryImagePath = "/auxiliary";
    //create domain resource with the auxiliary image
    logger.info("Creating domain custom resource with domainUid {0} and auxiliary images {1}",
        domainUid, imageName);
    DomainResource domainCR = createDomainResourceWithAuxiliaryImage(domainUid, domainNamespace,
        WEBLOGIC_IMAGE_TO_USE_IN_SPEC, domainAdminSecretName, createSecretsForImageRepos(domainNamespace),
        encryptionSecretName, t3ChannelPort, auxiliaryImagePath,
        imageName);

    domainCR = createClusterResourceAndAddReferenceToDomain(
        domainUid + "-" + clusterName, clusterName, domainNamespace, domainCR, replicaCount);
    // create domain and verify its running
    logger.info("Creating domain {0} with auxiliary images {1} {2} in namespace {3}",
        domainUid, imageName, domainNamespace);
    createDomainAndVerify(domainUid, domainCR, domainNamespace,
        adminServerPodName, managedServerPrefix, replicaCount);

  }

  private static DomainResource createDomainResourceWithAuxiliaryImage(
      String domainResourceName,
      String domNamespace,
      String baseImageName,
      String adminSecretName,
      String[] repoSecretName,
      String encryptionSecretName,
      int t3ChannelPort,
      String auxiliaryImagePath,
      String... auxiliaryImageName) {

    DomainResource domainCR = createDomainResource(
        domainResourceName,
        domNamespace,
        baseImageName,
        adminSecretName,
        repoSecretName,
        encryptionSecretName,
        replicaCount,
        Collections.<String>emptyList(),
        false,
        0,
        t3ChannelPort);
    int index = 0;
    for (String cmImageName: auxiliaryImageName) {
      AuxiliaryImage auxImage = new AuxiliaryImage()
          .image(cmImageName).imagePullPolicy(IMAGE_PULL_POLICY);
      //Only add the sourceWDTInstallHome and sourceModelHome for the first aux image.
      if (index == 0) {
        auxImage.sourceWDTInstallHome(auxiliaryImagePath + "/weblogic-deploy")
            .sourceModelHome(auxiliaryImagePath + "/models");
      }
      domainCR.spec().configuration().model().withAuxiliaryImage(auxImage);
      index++;
    }
    return domainCR;
  }

  private static DomainResource createDomainResource(
      String domainResourceName,
      String domNamespace,
      String imageName,
      String adminSecretName,
      String[] repoSecretName,
      String encryptionSecretName,
      int replicaCount,
      List<String> clusterNames,
      boolean prefixDomainName,
      int nodePort,
      int t3ChannelPort) {

    // create secrets
    List<V1LocalObjectReference> secrets = new ArrayList<>();
    for (String secret : repoSecretName) {
      secrets.add(new V1LocalObjectReference().name(secret));
    }

    // create the domain CR
    DomainResource domain = new DomainResource()
        .apiVersion(DOMAIN_API_VERSION)
        .kind("Domain")
        .metadata(new io.kubernetes.client.openapi.models.V1ObjectMeta()
            .name(domainResourceName)
            .namespace(domNamespace))
        .spec(new oracle.weblogic.domain.DomainSpec()
            .domainUid(domainResourceName)
            .domainHomeSourceType("FromModel")
            .image(imageName)
            .imagePullPolicy(IMAGE_PULL_POLICY)
            .webLogicCredentialsSecret(new V1LocalObjectReference()
                .name(adminSecretName))
            .includeServerOutInPodLog(true)
            .serverStartPolicy("IfNeeded")
            .serverPod(new oracle.weblogic.domain.ServerPod()
                .addEnvItem(new V1EnvVar()
                    .name("JAVA_OPTIONS")
                    .value("-Dweblogic.security.SSL.ignoreHostnameVerification=true"))
                .addEnvItem(new io.kubernetes.client.openapi.models.V1EnvVar()
                    .name("JAVA_OPTIONS")
                    .value("-Dweblogic.StdoutDebugEnabled=false"))
                .addEnvItem(new io.kubernetes.client.openapi.models.V1EnvVar()
                    .name("USER_MEM_ARGS")
                    .value("-Djava.security.egd=file:/dev/./urandom ")))
            .adminServer(new oracle.weblogic.domain.AdminServer()
                .adminService(new oracle.weblogic.domain.AdminService()
                    .addChannelsItem(new oracle.weblogic.domain.Channel()
                        .channelName("default")
                        .nodePort(nodePort))
                    .addChannelsItem(new Channel()
                        .channelName("T3Channel")
                        .nodePort(t3ChannelPort))))
            .configuration(new oracle.weblogic.domain.Configuration()
                .model(new oracle.weblogic.domain.Model()
                    .domainType("WLS")
                    .runtimeEncryptionSecret(encryptionSecretName))
                .introspectorJobActiveDeadlineSeconds(3000L)));

    domain.spec().setImagePullSecrets(secrets);

    ClusterList clusters = Cluster.listClusterCustomResources(domNamespace);

    if (clusterNames != null) {
      for (String clusterName : clusterNames) {
        String clusterResName = prefixDomainName ? domainResourceName + "-" + clusterName : clusterName;
        if (clusters.getItems().stream().anyMatch(cluster -> cluster.getClusterName().equals(clusterResName))) {
          getLogger().info("!!!Cluster {0} in namespace {1} already exists, skipping...", clusterResName, domNamespace);
        } else {
          getLogger().info("Creating cluster {0} in namespace {1}", clusterResName, domNamespace);
          ClusterSpec spec =
              new ClusterSpec().withClusterName(clusterName).replicas(replicaCount).serverStartPolicy("IfNeeded");
          createClusterAndVerify(createClusterResource(clusterResName, domNamespace, spec));
        }
        // set cluster references
        domain.getSpec().withCluster(new V1LocalObjectReference().name(clusterResName));
      }
    }

    setPodAntiAffinity(domain);
    return domain;
  }

  private String getCurlResult(String curlCmd) {
    ExecResult result = null;
    try {
      result = ExecCommand.exec(curlCmd, true);
    } catch (Exception e) {
      logger.info("Got exception while running command: {0}", curlCmd);
      logger.info(e.toString());
    }
    if (result != null) {
      logger.info("result.stderr: \n{0}", result.stderr());
    }
    return result.stdout();
  }

  private static void createNginxIngressPathRoutingRules() {
    // create an ingress in domain namespace
    final int ADMIN_SERVER_PORT = 7001;
    String ingressName = domainNamespace + "-nginx-path-routing";
    String ingressClassName = nginxHelmParams.getIngressClassName();

    // create ingress rules for two domains
    List<V1IngressRule> ingressRules = new ArrayList<>();
    List<V1HTTPIngressPath> httpIngressPaths = new ArrayList<>();

    V1HTTPIngressPath httpIngressPath = new V1HTTPIngressPath()
        .path("/")
        .pathType("Prefix")
        .backend(new V1IngressBackend()
            .service(new V1IngressServiceBackend()
                .name(domainUid1 + "-admin-server")
                .port(new V1ServiceBackendPort()
                    .number(ADMIN_SERVER_PORT)))
        );
    httpIngressPaths.add(httpIngressPath);

    V1IngressRule ingressRule = new V1IngressRule()
        .host("")
        .http(new V1HTTPIngressRuleValue()
            .paths(httpIngressPaths));

    ingressRules.add(ingressRule);

    createIngressAndRetryIfFail(60, false, ingressName, domainNamespace, null, ingressClassName, ingressRules, null);

    // check the ingress was found in the domain namespace
    assertThat(assertDoesNotThrow(() -> listIngresses(domainNamespace)))
        .as(String.format("Test ingress %s was found in namespace %s", ingressName, domainNamespace))
        .withFailMessage(String.format("Ingress %s was not found in namespace %s", ingressName, domainNamespace))
        .contains(ingressName);

    logger.info("ingress {0} was created in namespace {1}", ingressName, domainNamespace);

    // check the ingress is ready to route the app to the server pod
    String nginxServiceName = nginxHelmParams.getHelmParams().getReleaseName() + "-ingress-nginx-controller";
    nginxNodePort = assertDoesNotThrow(() -> Service.getServiceNodePort(nginxNamespace, nginxServiceName, "http"),
        "Getting Nginx loadbalancer service node port failed");

    String hostAndPort = getServiceExtIPAddrtOke(nginxServiceName, nginxNamespace) != null
        ? getServiceExtIPAddrtOke(nginxServiceName, nginxNamespace) : K8S_NODEPORT_HOST + ":" + nginxNodePort;

    String curlCmd = "curl -g --silent --show-error --noproxy '*' http://" + hostAndPort
        + "/weblogic/ready --write-out %{http_code} -o /dev/null";
    if (OKE_CLUSTER) {
      try {
        if (!callWebAppAndWaitTillReady(curlCmd, 60)) {
          ExecResult result = ExecCommand.exec(KUBERNETES_CLI + " get all -A");
          logger.info(result.stdout());
          //restart core-dns service
          result = ExecCommand.exec(KUBERNETES_CLI + " rollout restart deployment coredns -n kube-system");
          logger.info(result.stdout());
          checkPodReady("core-dns", null, "kube-system");
          result = ExecCommand.exec(curlCmd);
          logger.info(result.stdout());
        }
      } catch (Exception ex) {
        logger.warning(ex.getLocalizedMessage());
      }
    }

    logger.info("Executing curl command {0}", curlCmd);
    assertTrue(callWebAppAndWaitTillReady(curlCmd, 60));
  }

  // Create and display SSL certificate and key using openSSL with SAN extension
  private static void createCertKeyFiles(String cn) {

    Map<String, String> sanConfigTemplateMap  = new HashMap<>();
    sanConfigTemplateMap.put("INGRESS_HOST", hostAddress);

    Path srcFile = Paths.get(RESOURCE_DIR,
        "tunneling", "san.config.template.txt");
    Path targetFile = assertDoesNotThrow(
        () -> generateFileFromTemplate(srcFile.toString(),
        "san.config.txt", sanConfigTemplateMap));
    logger.info("Generated SAN config file {0}", targetFile);

    tlsKeyFile = Paths.get(RESULTS_ROOT, domainNamespace + "-tls.key");
    tlsCertFile = Paths.get(RESULTS_ROOT, domainNamespace + "-tls.cert");
    String opcmd = "openssl req -x509 -nodes -days 365 -newkey rsa:2048 "
          + "-keyout " + tlsKeyFile + " -out " + tlsCertFile
          + " -subj \"/CN=" + cn + "\" -extensions san"
          + " -config " + Paths.get(RESULTS_ROOT, "san.config.txt");
    assertTrue(
          Command.withParams(new CommandParams()
             .command(opcmd)).execute(), "openssl req command fails");

    String opcmd2 = "openssl x509 -in " + tlsCertFile + " -noout -text ";
    assertTrue(
          Command.withParams(new CommandParams()
             .command(opcmd2)).execute(), "openssl list command fails");
  }

  // Import the certificate into a JKS TrustStore to be used while running
  // external JMS client to send message to WebLogic.
  private static void importKeytoTrustStore() {

    jksTrustFile = Paths.get(RESULTS_ROOT, domainNamespace + "-trust.jks");
    String keycmd = "keytool -import -file " + tlsCertFile
        + " --keystore " + jksTrustFile
        + " -storetype jks -storepass password -noprompt ";
    assertTrue(
          Command.withParams(new CommandParams()
             .command(keycmd)).execute(), "keytool import command fails");

    String keycmd2 = "keytool -list -keystore " + jksTrustFile
                   + " -storepass password -noprompt";
    assertTrue(
          Command.withParams(new CommandParams()
             .command(keycmd2)).execute(), "keytool list command fails");
  }

  // Create kubernetes secret from the ssl key and certificate
  private static void createSecretWithTLSCertKey(String tlsSecretName) {
    String kcmd = KUBERNETES_CLI + " create secret tls " + tlsSecretName + " --key "
          + tlsKeyFile + " --cert " + tlsCertFile + " -n " + domainNamespace;
    assertTrue(
          Command.withParams(new CommandParams()
             .command(kcmd)).execute(), KUBERNETES_CLI + " create secret command fails");
  }


}


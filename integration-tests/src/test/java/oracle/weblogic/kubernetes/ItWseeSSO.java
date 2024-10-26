// Copyright (c) 2023, 2024, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;

import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.models.V1Container;
import io.kubernetes.client.openapi.models.V1LocalObjectReference;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1PersistentVolumeClaimVolumeSource;
import io.kubernetes.client.openapi.models.V1Pod;
import io.kubernetes.client.openapi.models.V1PodSpec;
import io.kubernetes.client.openapi.models.V1Volume;
import io.kubernetes.client.openapi.models.V1VolumeMount;
import oracle.weblogic.domain.DomainResource;
import oracle.weblogic.domain.DomainSpec;
import oracle.weblogic.kubernetes.actions.impl.NginxParams;
import oracle.weblogic.kubernetes.actions.impl.primitive.Kubernetes;
import oracle.weblogic.kubernetes.annotations.IntegrationTest;
import oracle.weblogic.kubernetes.annotations.Namespaces;
import oracle.weblogic.kubernetes.logging.LoggingFacade;
import oracle.weblogic.kubernetes.utils.ExecCommand;
import oracle.weblogic.kubernetes.utils.ExecResult;
import oracle.weblogic.kubernetes.utils.OracleHttpClient;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static java.nio.file.Paths.get;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;
import static oracle.weblogic.kubernetes.TestConstants.ADMIN_PASSWORD_DEFAULT;
import static oracle.weblogic.kubernetes.TestConstants.ADMIN_SERVER_NAME_BASE;
import static oracle.weblogic.kubernetes.TestConstants.ADMIN_USERNAME_DEFAULT;
import static oracle.weblogic.kubernetes.TestConstants.BASE_IMAGES_REPO_SECRET_NAME;
import static oracle.weblogic.kubernetes.TestConstants.IMAGE_PULL_POLICY;
import static oracle.weblogic.kubernetes.TestConstants.IT_WSEESSONGINX_INGRESS_HTTPS_NODEPORT;
import static oracle.weblogic.kubernetes.TestConstants.IT_WSEESSONGINX_INGRESS_HTTP_HOSTPORT;
import static oracle.weblogic.kubernetes.TestConstants.IT_WSEESSONGINX_INGRESS_HTTP_NODEPORT;
import static oracle.weblogic.kubernetes.TestConstants.K8S_NODEPORT_HOST;
import static oracle.weblogic.kubernetes.TestConstants.KIND_CLUSTER;
import static oracle.weblogic.kubernetes.TestConstants.KUBERNETES_CLI;
import static oracle.weblogic.kubernetes.TestConstants.MII_BASIC_IMAGE_NAME;
import static oracle.weblogic.kubernetes.TestConstants.MII_BASIC_IMAGE_TAG;
import static oracle.weblogic.kubernetes.TestConstants.NGINX_CHART_VERSION;
import static oracle.weblogic.kubernetes.TestConstants.OKD;
import static oracle.weblogic.kubernetes.TestConstants.OKE_CLUSTER;
import static oracle.weblogic.kubernetes.TestConstants.OKE_CLUSTER_PRIVATEIP;
import static oracle.weblogic.kubernetes.TestConstants.RESULTS_ROOT;
import static oracle.weblogic.kubernetes.TestConstants.TEST_IMAGES_REPO_SECRET_NAME;
import static oracle.weblogic.kubernetes.TestConstants.WEBLOGIC_IMAGE_TO_USE_IN_SPEC;
import static oracle.weblogic.kubernetes.TestConstants.WLSIMG_BUILDER;
import static oracle.weblogic.kubernetes.TestConstants.WLSIMG_BUILDER_DEFAULT;
import static oracle.weblogic.kubernetes.actions.ActionConstants.APP_DIR;
import static oracle.weblogic.kubernetes.actions.ActionConstants.MODEL_DIR;
import static oracle.weblogic.kubernetes.actions.ActionConstants.RESOURCE_DIR;
import static oracle.weblogic.kubernetes.actions.TestActions.getServiceNodePort;
import static oracle.weblogic.kubernetes.actions.TestActions.getServicePort;
import static oracle.weblogic.kubernetes.actions.impl.primitive.Kubernetes.exec;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.podReady;
import static oracle.weblogic.kubernetes.utils.BuildApplication.buildApplication;
import static oracle.weblogic.kubernetes.utils.CommonMiiTestUtils.createDomainResourceWithLogHome;
import static oracle.weblogic.kubernetes.utils.CommonMiiTestUtils.createDomainSecret;
import static oracle.weblogic.kubernetes.utils.CommonMiiTestUtils.createJobToChangePermissionsOnPvHostPath;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.checkPodReadyAndServiceExists;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.createIngressPathRouting;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.formatIPv6Host;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.getHostAndPort;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.getServiceExtIPAddrtOke;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.getUniqueName;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.runClientInsidePodVerifyResult;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.runJavacInsidePod;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.testUntil;
import static oracle.weblogic.kubernetes.utils.ConfigMapUtils.createConfigMapAndVerify;
import static oracle.weblogic.kubernetes.utils.DeployUtil.deployUsingWlst;
import static oracle.weblogic.kubernetes.utils.DomainUtils.createDomainAndVerify;
import static oracle.weblogic.kubernetes.utils.FileUtils.copyFileToPod;
import static oracle.weblogic.kubernetes.utils.FileUtils.replaceStringInFile;
import static oracle.weblogic.kubernetes.utils.ImageUtils.createBaseRepoSecret;
import static oracle.weblogic.kubernetes.utils.ImageUtils.createTestRepoSecret;
import static oracle.weblogic.kubernetes.utils.LoadBalancerUtils.installAndVerifyNginx;
import static oracle.weblogic.kubernetes.utils.OKDUtils.createRouteForOKD;
import static oracle.weblogic.kubernetes.utils.OperatorUtils.installAndVerifyOperator;
import static oracle.weblogic.kubernetes.utils.PersistentVolumeUtils.createPV;
import static oracle.weblogic.kubernetes.utils.PersistentVolumeUtils.createPVC;
import static oracle.weblogic.kubernetes.utils.PodUtils.checkPodReady;
import static oracle.weblogic.kubernetes.utils.PodUtils.execInPod;
import static oracle.weblogic.kubernetes.utils.PodUtils.getExternalServicePodName;
import static oracle.weblogic.kubernetes.utils.SecretUtils.createSecretWithUsernamePassword;
import static oracle.weblogic.kubernetes.utils.SslUtils.generateJksStores;
import static oracle.weblogic.kubernetes.utils.ThreadSafeLogger.getLogger;
import static org.apache.commons.io.FileUtils.deleteDirectory;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Test with webservices and SSO with two domains.
 */
@DisplayName("Verify that client can communicate with webservices with SSO")
@IntegrationTest
@Tag("oke-sequential")
@Tag("kind-parallel")
class ItWseeSSO {

  private static String opNamespace = null;
  private static String domain1Namespace = null;
  private static String domain2Namespace = null;
  private static String nginxNamespace = null;
  private static NginxParams nginxHelmParams = null;
  private static int nodeportshttp = 0;
  private static String WDT_MODEL_FILE_SENDER = "model.wsee1.yaml";
  private static String WDT_MODEL_FILE_RECEIVER = "model.wsee2.yaml";
  private String receiverURI = null;
  private String senderURI = null;
  final String domain1Uid = "mywseedomain1";
  final String domain2Uid = "mywseedomain2";
  final String clusterName = "cluster-1";
  final String adminServerName = "admin-server";
  final String adminServerPodName1 = domain1Uid + "-" + adminServerName;
  final String adminServerPodName2 = domain2Uid + "-" + adminServerName;
  final String managedServerNameBase = "managed-server";
  Path keyStoresPath;

  int replicaCount = 2;
  String adminSvcExtHost1 = null;
  String adminSvcExtHost2 = null;
  static Path wseeServiceAppPath;
  static Path wseeServiceRefAppPath;
  static Path wseeServiceRefStubsPath;
  private String ingressIP;

  private static LoggingFacade logger = null;

  /**
   * Assigns unique namespaces for operator and domains.
   * Pulls WebLogic image if running tests in Kind cluster.
   * Installs operator.
   * Creates and starts a WebLogic domains
   * Builds sender and receiver webservices applications
   *
   * @param namespaces injected by JUnit
   */
  @BeforeAll
  public void initAll(@Namespaces(4) List<String> namespaces) {
    logger = getLogger();

    logger.info("Assign a unique namespace for operator");
    assertNotNull(namespaces.get(0), "Namespace is null");
    opNamespace = namespaces.get(0);
    logger.info("Assign a unique namespace for domain1 namespace");
    assertNotNull(namespaces.get(1), "Namespace is null");
    domain1Namespace = namespaces.get(1);

    logger.info("Assign a unique namespace for domain2 namespace");
    assertNotNull(namespaces.get(2), "Namespace is null");
    domain2Namespace = namespaces.get(2);

    logger.info("Assign a unique namespace for nginx namespace");
    assertNotNull(namespaces.get(3), "Namespace is null");
    nginxNamespace = namespaces.get(3);

    // install operator and verify its running in ready state
    installAndVerifyOperator(opNamespace, domain1Namespace, domain2Namespace);
    if (!OKD) {
      // install and verify NGINX
      nginxHelmParams = installAndVerifyNginx(nginxNamespace, IT_WSEESSONGINX_INGRESS_HTTP_NODEPORT,
          IT_WSEESSONGINX_INGRESS_HTTPS_NODEPORT, NGINX_CHART_VERSION, (OKE_CLUSTER ? null : "NodePort"));

      String nginxServiceName = nginxHelmParams.getHelmParams().getReleaseName() + "-ingress-nginx-controller";
      logger.info("NGINX service name: {0}", nginxServiceName);

      ingressIP = getServiceExtIPAddrtOke(nginxServiceName, nginxNamespace) != null
          ? getServiceExtIPAddrtOke(nginxServiceName, nginxNamespace) : K8S_NODEPORT_HOST;
      nodeportshttp = getServiceNodePort(nginxNamespace, nginxServiceName, "http");
      logger.info("NGINX http node port: {0}", nodeportshttp);
    }
    keyStoresPath = Paths.get(RESULTS_ROOT, "mydomainwsee", "keystores");
    assertDoesNotThrow(() -> deleteDirectory(keyStoresPath.toFile()));
    assertDoesNotThrow(() -> Files.createDirectories(keyStoresPath));
    generateJksStores(keyStoresPath.toString(), "generate-certs.sh");
    //create and start WebLogic domain
    createDomains();

    if (adminSvcExtHost1 == null) {
      adminSvcExtHost1 = createRouteForOKD(getExternalServicePodName(adminServerPodName1), domain1Namespace);
    }
    if (adminSvcExtHost2 == null) {
      adminSvcExtHost2 = createRouteForOKD(getExternalServicePodName(adminServerPodName1), domain2Namespace);
    }

    // build the wsee application
    Path distDir = buildApplication(Paths.get(APP_DIR, "wsee"),
        null, null, "dist", domain1Namespace);
    wseeServiceAppPath = Paths.get(distDir.toString(), "EchoService.war");
    assertTrue(wseeServiceAppPath.toFile().exists(), "Application archive is not available");
    wseeServiceRefAppPath = Paths.get(distDir.toString(), "EchoServiceRef.war");
    assertTrue(wseeServiceRefAppPath.toFile().exists(), "Application archive is not available");
    wseeServiceRefStubsPath = Paths.get(distDir.toString(), "EchoServiceRefStubs.jar");
    assertTrue(wseeServiceRefStubsPath.toFile().exists(), "client stubs  archive is not available");
  }

  /**
   * Deploy webservices apps on domain1(sender), domain2(reciever).
   * A standalone client makes a call to webservice , deployed on domain1 (sender)
   * and provide username/password to invoke via sso webservice on domain2 (receiver)
   * with attached SAML sender-vouches policy
   */
  @Test
  @DisplayName("Test Wsee connect with sso")
  void testInvokeWsee() throws Exception {
    //deploy application to view server configuration
    deployApplication(clusterName + "," + adminServerName, domain2Namespace, domain2Uid, wseeServiceAppPath);
    //deploy application to view server configuration
    deployApplication(clusterName + "," + adminServerName, domain1Namespace, domain1Uid, wseeServiceRefAppPath);
    receiverURI = checkWSDLAccess(domain2Namespace, domain2Uid, adminSvcExtHost2,
        "/samlSenderVouches/EchoService");
    senderURI = checkWSDLAccess(domain1Namespace, domain1Uid, adminSvcExtHost1,
        "/EchoServiceRef/Echo");
    
    testUntil(() -> callPythonScript(domain1Uid, domain1Namespace,
        "addSAMLRelyingPartySenderConfig.py", receiverURI),
        logger,
        "Failed to run python script addSAMLRelyingPartySenderConfig.py");

    int serviceNodePort = assertDoesNotThrow(()
        -> getServiceNodePort(domain2Namespace, getExternalServicePodName(adminServerPodName2),
            "default"),
        "Getting admin server node port failed");
    String hostPort = OKE_CLUSTER_PRIVATEIP ? ingressIP + " 80" : K8S_NODEPORT_HOST + " " + serviceNodePort;
    testUntil(() -> callPythonScript(domain1Uid, domain1Namespace, "setupPKI.py", hostPort),
        logger, "Failed to run python script setupPKI.py");

    buildRunClientOnPod();
  }

  private String checkWSDLAccess(String domainNamespace, String domainUid,
      String adminSvcExtHost,
      String appURI) throws UnknownHostException, IOException, InterruptedException {

    String adminServerPodName = domainUid + "-" + adminServerName;
    String hostAndPort;
    if (!OKE_CLUSTER_PRIVATEIP) {
      int serviceTestNodePort = assertDoesNotThrow(()
          -> getServiceNodePort(domainNamespace, getExternalServicePodName(adminServerPodName), "default"),
          "Getting admin server node port failed");
      logger.info("admin svc host = {0}", adminSvcExtHost);
      hostAndPort = getHostAndPort(adminSvcExtHost, serviceTestNodePort);
      if (KIND_CLUSTER && !WLSIMG_BUILDER.equals(WLSIMG_BUILDER_DEFAULT)) {
        // to access app url in podman we have to use mapped nodeport and localhost
        String url = "http://" + formatIPv6Host(InetAddress.getLocalHost().getHostAddress())
            + ":" + IT_WSEESSONGINX_INGRESS_HTTP_HOSTPORT + appURI;
        assertEquals(200, OracleHttpClient.get(url, true).statusCode());
        // to access app url inside admin pod we have to use nodehost and nodeport
        return "http://" + K8S_NODEPORT_HOST + ":" + serviceTestNodePort + appURI;
      }
    } else {
      hostAndPort = ingressIP + ":80";
    }
    String url = "http://" + hostAndPort + appURI;
    if (OKE_CLUSTER) {
      try {
        if (OracleHttpClient.get(url, true).statusCode() != 200) {
          ExecResult result = ExecCommand.exec(KUBERNETES_CLI + " get all -A");
          logger.info(result.stdout());
          //restart core-dns service
          result = ExecCommand.exec(KUBERNETES_CLI + " rollout restart deployment coredns -n kube-system");
          logger.info(result.stdout());
          checkPodReady("coredns", null, "kube-system");
        }
      } catch (Exception ex) {
        logger.warning(ex.getLocalizedMessage());
      }
    }
    assertEquals(200, OracleHttpClient.get(url, true).statusCode());
    return url;
  }

  private void createDomain(String domainNamespace, String domainUid, String modelFileName) {

    // create pull secrets for WebLogic image when running in non Kind Kubernetes cluster
    // this secret is used only for non-kind cluster
    createBaseRepoSecret(domainNamespace);
    createTestRepoSecret(domainNamespace);

    // create secret for admin credential with special characters
    // the resultant password is ##W%*}!"'"`']\\\\//1$$~x
    // let the user name be something other than weblogic say wlsadmin
    logger.info("Create secret for admin credentials");
    String adminSecretName = "weblogic-credentials";
    assertDoesNotThrow(() -> createSecretWithUsernamePassword(adminSecretName, domainNamespace,
            "weblogic", "welcome1"),
        String.format("create secret for admin credentials failed for %s", adminSecretName));
    // create encryption secret with special characters
    logger.info("Create encryption secret");
    String encryptionSecretName = "encryptionsecret";
    assertDoesNotThrow(() -> createDomainSecret(encryptionSecretName, "weblogicenc",
            "#%*!`${ls}'${DOMAIN_UID}1~3x", domainNamespace),
        String.format("createSecret failed for %s", encryptionSecretName));

    String configMapName = "mii-ssl-configmap";

    String jksMountPath = "/shared/" + domainNamespace + "/" + domainUid + "/keystores";
    assertDoesNotThrow(() ->
        replaceStringInFile(get(modelFileName).toString(),
            "/shared/", jksMountPath + "/"));

    createConfigMapAndVerify(
        configMapName, domainUid, domainNamespace,
        Arrays.asList(modelFileName));

    // this secret is used only for non-kind cluster
    createBaseRepoSecret(domainNamespace);
    String pvName = getUniqueName(domainUid + "-pv-");
    String pvcName = getUniqueName(domainUid + "-pvc-");

    // create PV, PVC for logs/data
    createPV(pvName, domainUid, ItWseeSSO.class.getSimpleName());
    createPVC(pvName, pvcName, domainUid, domainNamespace);

    // create job to change permissions on PV hostPath
    createJobToChangePermissionsOnPvHostPath(pvName, pvcName, domainNamespace);

    copyKeyStores(domainNamespace, keyStoresPath.toString(), jksMountPath, pvName, pvcName);


    // create the domain CR with a pre-defined configmap

    DomainResource domain = createDomainResourceWithLogHome(domainUid, domainNamespace,
        MII_BASIC_IMAGE_NAME + ":" + MII_BASIC_IMAGE_TAG,
        adminSecretName, TEST_IMAGES_REPO_SECRET_NAME, encryptionSecretName,
        pvName, pvcName, configMapName,
        null,
        "-Dweblogic.security.SSL.ignoreHostnameVerification=true -Dweblogic.wsee.verbose=*"
            + " -Dweblogic.xml.crypto.wss.verbose=true -Dweblogic.xml.crypto.wss.verbose=true "
            + " -Dweblogic.xml.crypto.wss.verbose=true "
            + " -Dweblogic.xml.crypto.encrypt.verbose=true "
            + " -Dweblogic.xml.crypto.dsig.verbose=true"
            + " -Dweblogic.xml.crypto.wss.verbose=true"
            + " -Dweblogic.debug.DebugSecuritySAMLService=true"
            + "      -Dweblogic.debug.DebugSecuritySAMLCredMap=true"
            + "      -Dweblogic.debug.DebugSecurityCredMap=true"
            + "      -Dweblogic.debug.DebugSecurityAtn=true"
            + "      -Dweblogic.debug.DebugSecuritySAMLAtn=true"
            + "      -Dweblogic.debug.DebugSecuritySAMLLib=true"
            + "      -Dweblogic.debug.DebugSecuritySAML2Service=true"
            + "      -Dweblogic.debug.DebugSecuritySAML2CredMap=true"
            + "      -Dweblogic.debug.DebugSecuritySAML2Atn=true"
            + "      -Dweblogic.debug.DebugSecuritySAML2Lib=true"
            + "      -Dweblogic.debug.DebugSecuritySAMLService=true"
            + "      -Dweblogic.debug.DebugSecuritySAMLCredMap=true"
            + "      -Dweblogic.debug.DebugSecuritySAMLAtn=true"
            + "      -Dweblogic.debug.DebugSecuritySAMLLib=true"
            + "      -Dweblogic.debug.DebugSecuritySAML2Service=true"
            + "      -Dweblogic.debug.DebugSecuritySAML2CredMap=true"
            + "      -Dweblogic.debug.DebugSecuritySAML2Atn=true"
            + "      -Dweblogic.debug.DebugSecurityCredMap=true"
            + "      -Dweblogic.debug.DebugSecurityAtn=true"
            + "      -Dweblogic.security.SSL.ignoreHostnameVerification=true"
            + "      -Dweblogic.wsee.security.verbose=true"
            + "      -Dweblogic.wsee.security.debug=true"
            + "      -Dweblogic.wsee.jaxws.tubeline.standard.StandardTubelineDeploymentListener.dump=true"
            + "      -Dweblogic.wsee.verbose.timestamp=true"
            + "      -Dweblogic.wsee.verbose.threadstamp=true"
            + "      -Dweblogic.xml.crypto.wss.verbose=true"
            + "      -Dweblogic.xml.crypto.wss.debug=true"
            + "      -Dweblogic.wsee.security.WssHandler=true"
            + "      -Dweblogic.wsee.verbose=*,weblogic.wsee.security.wssp.handlers.*=FINER"
            + "      -Dweblogic.debug.DebugSecuritySAML2Lib=true",
        false, false);
    DomainSpec spec = domain.getSpec().replicas(replicaCount);

    // wait for the domain to exist
    createDomainAndVerify(domain, domainNamespace);
    String adminServerName = "admin-server";
    String adminServerPodName = domainUid + "-" + adminServerName;
    logger.info("Check admin service and pod {0} is created in namespace {1}",
        adminServerPodName, domainNamespace);
    checkPodReadyAndServiceExists(adminServerPodName, domainUid, domainNamespace);

    String managedServerPodNamePrefix = domainUid + "-" + managedServerNameBase;
    for (int i = 1; i <= replicaCount; i++) {
      logger.info("Wait for managed server services and pods are created in namespace {0}",
          domainNamespace);
      checkPodReadyAndServiceExists(managedServerPodNamePrefix + i, domainUid, domainNamespace);
    }
  }

  private static void copyKeyStores(String domainNamespace,
                                    String keyStoresPath,
                                    String jksMountPath,
                                    String pvName,
                                    String pvcName) {

    logger.info("Setting up WebLogic pod to access PV");
    V1Pod pvPod = setupWebLogicPod(domainNamespace, pvName, pvcName, "/shared");
    assertNotNull(pvPod, "pvPod is null");
    assertNotNull(pvPod.getMetadata(), "pvpod metadata is null");

    logger.info("Creating directory {0} in PV", jksMountPath);
    execInPod(pvPod, null, true, "mkdir -p " + jksMountPath);

    //copy the jks files to PV using the temp pod - we don't have access to PVROOT in Jenkins env
    assertDoesNotThrow(() -> copyFileToPod(domainNamespace,
            pvPod.getMetadata().getName(), "",
            Paths.get(keyStoresPath.toString(), "Identity1KeyStore.jks"),
            Paths.get(jksMountPath, "/Identity1KeyStore.jks")),
        "Copying file to pod failed");
    assertDoesNotThrow(() -> copyFileToPod(domainNamespace,
            pvPod.getMetadata().getName(), "",
            Paths.get(keyStoresPath.toString(), "Identity2KeyStore.jks"),
            Paths.get(jksMountPath, "/Identity2KeyStore.jks")),
        "Copying file to pod failed");
    assertDoesNotThrow(() -> copyFileToPod(domainNamespace,
            pvPod.getMetadata().getName(), "",
            Paths.get(keyStoresPath.toString(), "TrustKeyStore.jks"),
            Paths.get(jksMountPath, "/TrustKeyStore.jks")),
        "Copying file to pod failed");
    assertDoesNotThrow(() -> copyFileToPod(domainNamespace,
            pvPod.getMetadata().getName(), "",
            Paths.get(keyStoresPath.toString(), "PkiKeyStore.jks"),
            Paths.get(jksMountPath, "/PkiKeyStore.jks")),
        "Copying file to pod failed");
    assertDoesNotThrow(() -> copyFileToPod(domainNamespace,
            pvPod.getMetadata().getName(), "",
            Paths.get(keyStoresPath.toString(), "certrec.pem"),
            Paths.get(jksMountPath, "/certrec.pem")),
        "Copying file to pod failed");


    logger.info("Changing file ownership {0} to oracle:root in PV", jksMountPath);
    String argCommand = "chown -R 1000:root " + jksMountPath;
    if (OKE_CLUSTER) {
      argCommand = "chown 1000:root " + jksMountPath
          + "/. && find "
          + jksMountPath
          + "/. -maxdepth 1 ! -name '.snapshot' ! -name '.' -print0 | xargs -r -0  chown -R 1000:root";
    }
    //Calls execInPod to change the ownership of files in PV - not valid in OKD
    if (!OKD) {
      execInPod(pvPod, null, true, argCommand);
    }
  }

  //create a standard WebLogic domain.
  private void createDomains() {
    // Generate the model.sessmigr.yaml file at RESULTS_ROOT
    String destYamlFile1 =
        generateNewModelFileWithUpdatedProps(domain1Namespace, domain1Uid, "ItWseeSSO",
            WDT_MODEL_FILE_SENDER);
    String destYamlFile2 =
        generateNewModelFileWithUpdatedProps(domain2Namespace, domain2Uid, "ItWseeSSO",
            WDT_MODEL_FILE_RECEIVER);
    createDomain(domain1Namespace, domain1Uid, destYamlFile1);
    createDomain(domain2Namespace, domain2Uid, destYamlFile2);
    if (!OKD) {
      String ingressClassName = nginxHelmParams.getIngressClassName();
      createIngressPathRouting(domain1Namespace, "/EchoServiceRef",
          domain1Uid + "-admin-server", 7001, ingressClassName);
      createIngressPathRouting(domain2Namespace, "/samlSenderVouches",
          domain2Uid + "-admin-server", 7001, ingressClassName);
    }
    if (adminSvcExtHost1 == null) {
      adminSvcExtHost1 = createRouteForOKD(getExternalServicePodName(adminServerPodName1), domain1Namespace);
    }
    if (adminSvcExtHost2 == null) {
      adminSvcExtHost2 = createRouteForOKD(getExternalServicePodName(adminServerPodName2), domain2Namespace);
    }
    testUntil(() -> callPythonScript(domain1Uid, domain1Namespace,
            "setupAdminSSL.py", "mykeysen changeit 7002 /shared/" + domain1Namespace + "/"
                + domain1Uid + "/keystores Identity1KeyStore.jks"),
        logger,
        "Failed to run python script setupAdminSSL.py");

    testUntil(() -> callPythonScript(domain2Uid, domain2Namespace,
            "setupAdminSSL.py", "mykeyrec changeit 7002 /shared/" + domain2Namespace + "/"
                + domain2Uid + "/keystores Identity2KeyStore.jks"),
        logger,
        "Failed to run python script setupAdminSSL.py");

    testUntil(() -> callPythonScript(domain2Uid, domain2Namespace,
            "addSAMLAssertingPartyReceiverConfig.py", "/samlSenderVouches/EchoService"),
        logger,
        "Failed to run python script addSAMLAssertingPartyReceiverConfig.py");
  }

  //deploy application wsee.war to domain
  private void deployApplication(String targets, String domainNamespace, String domainUid, Path appPath) {
    logger.info("Getting port for default channel");
    String adminServerName = "admin-server";
    String adminServerPodName = domainUid + "-" + adminServerName;
    int defaultChannelPort = assertDoesNotThrow(()
            -> getServicePort(domainNamespace, getExternalServicePodName(adminServerPodName), "default"),
        "Getting admin server default port failed");
    logger.info("default channel port: {0}", defaultChannelPort);
    assertNotEquals(-1, defaultChannelPort, "admin server defaultChannelPort is not valid");
    //deploy application
    logger.info("Deploying webapp {0} to domain", appPath);
    deployUsingWlst(adminServerPodName, Integer.toString(defaultChannelPort),
        ADMIN_USERNAME_DEFAULT, ADMIN_PASSWORD_DEFAULT, targets, appPath,
        domainNamespace);
  }

  // Run standalone client to get initial context using t3s cluster url
  private void buildRunClientOnPod() {
    String destLocation1 = "/u01/WseeClient.java";
    assertDoesNotThrow(() -> copyFileToPod(domain1Namespace,
        "weblogic-pod-" + domain1Namespace, "",
        Paths.get(RESOURCE_DIR, "wsee", "WseeClient.java"),
        Paths.get(destLocation1)));
    String destLocation2 = "/u01/EchoServiceRefStubs.jar";
    assertDoesNotThrow(() -> copyFileToPod(domain1Namespace,
        "weblogic-pod-" + domain1Namespace, "",
        wseeServiceRefStubsPath,
        Paths.get(destLocation2)));
    runJavacInsidePod("weblogic-pod-" + domain1Namespace, domain1Namespace, destLocation1, destLocation2);
    logger.info("Setting up WebLogic pod to access PV");

    assertDoesNotThrow(() -> copyFileToPod(domain1Namespace,
            adminServerPodName1, "",
            Paths.get(keyStoresPath.toString(), "certrec.pem"),
            Paths.get("/u01", "certrec.pem")),
        "Copying file to pod failed");

    StringBuffer extOpts = new StringBuffer("");
    extOpts.append(senderURI + " ");
    extOpts.append(receiverURI);
    extOpts.append(" user_d1 password1");
    extOpts.append(" /u01/certrec.pem");

    testUntil(
        runClientInsidePodVerifyResult("weblogic-pod-" + domain1Namespace, domain1Namespace,
            "/u01:/u01/EchoServiceRefStubs.jar", " WseeClient",
            "echoSignedSamlV20Token11 'Hi, world!'...",
            extOpts.toString()),
        logger,
        "Wait for client to invoke wsee");
  }

  private boolean callPythonScript(String domainUid,
                                   String domainNS,
                                   String scriptName,
                                   String param) throws Exception {
    // copy python script and callpyscript.sh to Admin Server pod
    String adminServerPodName = domainUid + "-" + ADMIN_SERVER_NAME_BASE;
    V1Pod adminPod = Kubernetes.getPod(domainNS, null, adminServerPodName);
    if (adminPod == null) {
      logger.info("The admin pod {0} does not exist in namespace {1}!", adminServerPodName, domainNS);
      return false;
    }

    logger.info("Copying " + scriptName + " and callpyscript.sh to admin server pod");
    try {
      Kubernetes.copyFileToPod(domainNS, adminServerPodName, null,
          Paths.get(RESOURCE_DIR, "python-scripts", scriptName),
          Paths.get("/u01/" + scriptName));

      Kubernetes.copyFileToPod(domainNS, adminServerPodName, null,
          Paths.get(RESOURCE_DIR, "bash-scripts", "callpyscript.sh"),
          Paths.get("/u01/callpyscript.sh"));
    } catch (ApiException apex) {
      logger.severe("Got ApiException while copying file to admin pod {0}", apex.getResponseBody());
      return false;
    } catch (IOException ioex) {
      logger.severe("Got IOException while copying file to admin pod {0}", (Object) ioex.getStackTrace());
      return false;
    }

    logger.info("Adding execute mode for callpyscript.sh");
    ExecResult result = exec(adminPod, null, true,
        "/bin/sh", "-c", "chmod +x /u01/callpyscript.sh");
    if (result.exitValue() != 0) {
      return false;
    }
    logger.info("Running script " + scriptName);
    String command = new StringBuffer("/u01/callpyscript.sh /u01/" + scriptName)
        .append(" ")
        .append(ADMIN_USERNAME_DEFAULT)
        .append(" ")
        .append(ADMIN_PASSWORD_DEFAULT)
        .append(" t3://")
        .append(adminServerPodName)
        .append(":7001 ")
        .append(param)
        .toString();

    testUntil(() -> {
      ExecResult result1 = exec(adminPod, null, true, "/bin/sh", "-c", command);
      if (result1.exitValue() != 0) {
        return false;
      } else {
        return true;
      }
    }, logger, " Command returns unexpected exit value");
    return true;
  }

  private static V1Pod setupWebLogicPod(String namespace, String pvName,
                                        String pvcName, String mountPath) {
    // this secret is used only for non-kind cluster
    createBaseRepoSecret(namespace);

    final String podName = "weblogic-pod-" + namespace;
    V1PodSpec podSpec = new V1PodSpec()
        .containers(Arrays.asList(
            new V1Container()
                .name("weblogic-server")
                .image(WEBLOGIC_IMAGE_TO_USE_IN_SPEC)
                .imagePullPolicy(IMAGE_PULL_POLICY)
                .addCommandItem("sleep")
                .addArgsItem("600")
                .volumeMounts(Arrays.asList(
                    new V1VolumeMount()
                        .name(pvName) // mount the persistent volume to /shared inside the pod
                        .mountPath(mountPath)))))
        .imagePullSecrets(Arrays.asList(new V1LocalObjectReference()
            .name(BASE_IMAGES_REPO_SECRET_NAME)))
        // the persistent volume claim used by the test
        .volumes(Arrays.asList(
            new V1Volume()
                .name(pvName) // the persistent volume that needs to be archived
                .persistentVolumeClaim(
                    new V1PersistentVolumeClaimVolumeSource()
                        .claimName(pvcName))));

    V1Pod podBody = new V1Pod()
        .spec(podSpec)
        .metadata(new V1ObjectMeta().name(podName))
        .apiVersion("v1")
        .kind("Pod");

    V1Pod wlsPod = assertDoesNotThrow(() -> Kubernetes.createPod(namespace, podBody));

    testUntil(
        podReady(podName, null, namespace),
        logger,
        "{0} to be ready in namespace {1}",
        podName,
        namespace);

    return wlsPod;
  }

  public static String generateNewModelFileWithUpdatedProps(String domainUid,
                                                            String namespace,
                                                            String className,
                                                            String origModelFile) {
    final String srcModelYamlFile = MODEL_DIR + "/" + origModelFile;
    final String destModelYamlFile = RESULTS_ROOT + "/" + domainUid + "/" + className + "/" + origModelFile;
    Path srcModelYamlPath = Paths.get(srcModelYamlFile);
    Path destModelYamlPath = Paths.get(destModelYamlFile);

    // create dest dir
    assertDoesNotThrow(() -> Files.createDirectories(
            Paths.get(RESULTS_ROOT + "/" + domainUid, className)),
        String.format("Could not create directory under %s", RESULTS_ROOT + "/"
            + domainUid + className + ""));

    // copy model.yaml to results dir
    assertDoesNotThrow(() -> Files.copy(srcModelYamlPath, destModelYamlPath, REPLACE_EXISTING),
        "Failed to copy " + srcModelYamlFile + " to " + destModelYamlFile);

    // DOMAIN_NAME in model.yaml
    assertDoesNotThrow(() -> replaceStringInFile(
            destModelYamlFile.toString(), "DOMAIN_NAME", domainUid),
        "Could not modify DOMAIN_NAME in " + destModelYamlFile);
    // NAMESPACE in model.yaml
    assertDoesNotThrow(() -> replaceStringInFile(
            destModelYamlFile.toString(), "NAMESPACE", namespace),
        "Could not modify NAMESPACE in " + destModelYamlFile);

    return destModelYamlFile;
  }
}

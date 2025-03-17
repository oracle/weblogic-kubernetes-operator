// Copyright (c) 2020, 2024, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import io.kubernetes.client.openapi.models.V1EnvVar;
import io.kubernetes.client.openapi.models.V1LocalObjectReference;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import oracle.weblogic.domain.Configuration;
import oracle.weblogic.domain.DomainResource;
import oracle.weblogic.domain.DomainSpec;
import oracle.weblogic.domain.Model;
import oracle.weblogic.domain.OnlineUpdate;
import oracle.weblogic.domain.ServerPod;
import oracle.weblogic.kubernetes.annotations.IntegrationTest;
import oracle.weblogic.kubernetes.annotations.Namespaces;
import oracle.weblogic.kubernetes.logging.LoggingFacade;
import oracle.weblogic.kubernetes.utils.ExecCommand;
import oracle.weblogic.kubernetes.utils.ExecResult;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static oracle.weblogic.kubernetes.TestConstants.ADMIN_PASSWORD_DEFAULT;
import static oracle.weblogic.kubernetes.TestConstants.ADMIN_USERNAME_DEFAULT;
import static oracle.weblogic.kubernetes.TestConstants.DOMAIN_API_VERSION;
import static oracle.weblogic.kubernetes.TestConstants.IMAGE_PULL_POLICY;
import static oracle.weblogic.kubernetes.TestConstants.ISTIO_HTTP_HOSTPORT;
import static oracle.weblogic.kubernetes.TestConstants.K8S_NODEPORT_HOST;
import static oracle.weblogic.kubernetes.TestConstants.KUBERNETES_CLI;
import static oracle.weblogic.kubernetes.TestConstants.MII_BASIC_IMAGE_NAME;
import static oracle.weblogic.kubernetes.TestConstants.MII_BASIC_IMAGE_TAG;
import static oracle.weblogic.kubernetes.TestConstants.OCNE;
import static oracle.weblogic.kubernetes.TestConstants.OKE_CLUSTER;
import static oracle.weblogic.kubernetes.TestConstants.TEST_IMAGES_REPO_SECRET_NAME;
import static oracle.weblogic.kubernetes.actions.ActionConstants.MODEL_DIR;
import static oracle.weblogic.kubernetes.actions.ActionConstants.RESOURCE_DIR;
import static oracle.weblogic.kubernetes.actions.ActionConstants.WORK_DIR;
import static oracle.weblogic.kubernetes.actions.TestActions.addLabelsToNamespace;
import static oracle.weblogic.kubernetes.actions.TestActions.patchDomainResourceWithNewIntrospectVersion;
import static oracle.weblogic.kubernetes.utils.ApplicationUtils.checkAppUsingHostHeader;
import static oracle.weblogic.kubernetes.utils.ClusterUtils.createClusterResourceAndAddReferenceToDomain;
import static oracle.weblogic.kubernetes.utils.CommonMiiTestUtils.replaceConfigMapWithModelFiles;
import static oracle.weblogic.kubernetes.utils.CommonMiiTestUtils.verifyIntrospectorRuns;
import static oracle.weblogic.kubernetes.utils.CommonMiiTestUtils.verifyPodIntrospectVersionUpdated;
import static oracle.weblogic.kubernetes.utils.CommonMiiTestUtils.verifyPodsNotRolled;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.checkPodReadyAndServiceExists;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.formatIPv6Host;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.getServiceExtIPAddrtOke;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.testUntil;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.withStandardRetryPolicy;
import static oracle.weblogic.kubernetes.utils.ConfigMapUtils.createConfigMapAndVerify;
import static oracle.weblogic.kubernetes.utils.DomainUtils.createDomainAndVerify;
import static oracle.weblogic.kubernetes.utils.FileUtils.generateFileFromTemplate;
import static oracle.weblogic.kubernetes.utils.FileUtils.replaceStringInFile;
import static oracle.weblogic.kubernetes.utils.ImageUtils.createBaseRepoSecret;
import static oracle.weblogic.kubernetes.utils.ImageUtils.createTestRepoSecret;
import static oracle.weblogic.kubernetes.utils.IstioUtils.createAdminServer;
import static oracle.weblogic.kubernetes.utils.IstioUtils.deployHttpIstioGatewayAndVirtualservice;
import static oracle.weblogic.kubernetes.utils.IstioUtils.deployIstioDestinationRule;
import static oracle.weblogic.kubernetes.utils.IstioUtils.getIstioHttpIngressPort;
import static oracle.weblogic.kubernetes.utils.OperatorUtils.installAndVerifyOperator;
import static oracle.weblogic.kubernetes.utils.PodUtils.getPodCreationTime;
import static oracle.weblogic.kubernetes.utils.PodUtils.setPodAntiAffinity;
import static oracle.weblogic.kubernetes.utils.SecretUtils.createSecretWithUsernamePassword;
import static oracle.weblogic.kubernetes.utils.ThreadSafeLogger.getLogger;
import static org.apache.commons.io.FileUtils.copyFile;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Test istio enabled WebLogic Domain in mii model")
@IntegrationTest
@Tag("kind-parallel")
@Tag("olcne-srg")
@Tag("oke-parallel")
class ItIstioMiiDomain {

  private static String opNamespace = null;
  private static String domainNamespace = null;

  private String domainUid = "istio-mii";
  private String configMapName = "dynamicupdate-istio-configmap";
  private final String clusterName = "cluster-1"; // do not modify
  private final String adminServerPodName = domainUid + "-admin-server";
  private final String managedServerPrefix = domainUid + "-managed-server";
  private final int replicaCount = 2;

  private static final String istioNamespace = "istio-system";
  private static final String istioIngressServiceName = "istio-ingressgateway";

  private static LoggingFacade logger = null;

  /**
   * Install Operator.
   * @param namespaces list of namespaces created by the IntegrationTestWatcher
  */
  @BeforeAll
  public static void initAll(@Namespaces(2) List<String> namespaces) {
    logger = getLogger();

    // get a new unique opNamespace
    logger.info("Assign unique namespace for Operator");
    assertNotNull(namespaces.get(0), "Namespace list is null");
    opNamespace = namespaces.get(0);

    logger.info("Assign unique namespace for Domain");
    assertNotNull(namespaces.get(1), "Namespace list is null");
    domainNamespace = namespaces.get(1);

    // Label the domain/operator namespace with istio-injection=enabled
    Map<String, String> labelMap = new HashMap<>();
    labelMap.put("istio-injection", "enabled");
    assertDoesNotThrow(() -> addLabelsToNamespace(domainNamespace,labelMap));
    assertDoesNotThrow(() -> addLabelsToNamespace(opNamespace,labelMap));
    
    // install and verify operator
    installAndVerifyOperator(opNamespace, domainNamespace);
    
    enableStrictMode(domainNamespace);
  }

  /**
   * Create a domain using model-in-image model.
   * Add istio configuration with default readinessPort.
   * Do not add any AdminService under AdminServer configuration.
   * Deploy istio gateways and virtual service.
   *
   * Verify server pods are in ready state and services are created.
   * Verify WebLogic REST interface is accessible thru istio ingress port.
   *
   * Create a configmap with a sparse model file to add a new workmanager
   * with custom min threads constraint and a max threads constraint
   * Patch the domain resource with the configmap.
   * Update the introspect version of the domain resource.
   * Verify the changes are applied without rolling restart of the domain by comparing PodCreationTimestamp
   * Verify new work manager is configured.
   */
  @Test
  @DisplayName("Create WebLogic Domain with mii model with istio")
  @Tag("gate")
  void testIstioModelInImageDomain() throws UnknownHostException, IOException, InterruptedException {

    // Create the repo secret to pull the image
    // this secret is used only for non-kind cluster
    createTestRepoSecret(domainNamespace);

    // create secret for admin credentials
    logger.info("Create secret for admin credentials");
    String adminSecretName = "weblogic-credentials";
    assertDoesNotThrow(() -> createSecretWithUsernamePassword(
        adminSecretName,
        domainNamespace,
        ADMIN_USERNAME_DEFAULT,
        ADMIN_PASSWORD_DEFAULT),
        String.format("createSecret failed for %s", adminSecretName));

    // create encryption secret
    logger.info("Create encryption secret");
    String encryptionSecretName = "encryptionsecret";
    assertDoesNotThrow(() -> createSecretWithUsernamePassword(
        encryptionSecretName,
        domainNamespace,
        "weblogicenc",
        "weblogicenc"),
        String.format("createSecret failed for %s", encryptionSecretName));

    // create WDT config map without any files
    createConfigMapAndVerify(configMapName, domainUid, domainNamespace, Collections.emptyList());

    // create the domain object
    DomainResource domain = createDomainResource(domainUid, domainNamespace, adminSecretName,
        TEST_IMAGES_REPO_SECRET_NAME, encryptionSecretName,
        MII_BASIC_IMAGE_NAME + ":" + MII_BASIC_IMAGE_TAG, configMapName);
    
    // create model in image domain
    createDomainAndVerify(domain, domainNamespace);

    logger.info("Check admin service {0} is created in namespace {1}",
        adminServerPodName, domainNamespace);
    checkPodReadyAndServiceExists(adminServerPodName, domainUid, domainNamespace);
    // check managed server services created
    for (int i = 1; i <= replicaCount; i++) {
      logger.info("Check managed service {0} is created in namespace {1}",
          managedServerPrefix + i, domainNamespace);
      checkPodReadyAndServiceExists(managedServerPrefix + i, domainUid, domainNamespace);
    }

    // delete the mTLS mode
    ExecResult result = assertDoesNotThrow(() -> ExecCommand.exec(KUBERNETES_CLI + " delete -f "
        + Paths.get(WORK_DIR, "istio-tls-mode.yaml").toString(), true));
    assertEquals(0, result.exitValue(), "Got expected exit value");
    logger.info(result.stdout());
    logger.info(result.stderr());

    String clusterService = domainUid + "-cluster-" + clusterName + "." + domainNamespace + ".svc.cluster.local";

    Map<String, String> templateMap = new HashMap<>();
    templateMap.put("NAMESPACE", domainNamespace);
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

    deployRes = assertDoesNotThrow(() -> deployIstioDestinationRule(targetDrFile));
    assertTrue(deployRes, "Failed to deploy Istio DestinationRule");

    int istioIngressPort = getIstioHttpIngressPort();
    String host = formatIPv6Host(K8S_NODEPORT_HOST);
    logger.info("Istio Ingress Port is {0}", istioIngressPort);
    logger.info("host {0}", host);

    // In internal OKE env, use Istio EXTERNAL-IP; in non-OKE env, use K8S_NODEPORT_HOST + ":" + istioIngressPort
    String hostAndPort = getServiceExtIPAddrtOke(istioIngressServiceName, istioNamespace) != null
        ? getServiceExtIPAddrtOke(istioIngressServiceName, istioNamespace) : host + ":" + istioIngressPort;

    String workManagers = "/management/weblogic/latest/domainConfig/selfTuning/workManagers/";
    String newWM = workManagers + "newWM/";
    if (!TestConstants.WLSIMG_BUILDER.equals(TestConstants.WLSIMG_BUILDER_DEFAULT) && !OCNE) {
      istioIngressPort = ISTIO_HTTP_HOSTPORT;
      hostAndPort = InetAddress.getLocalHost().getHostAddress() + ":" + istioIngressPort;
    }

    String url = "http://" + hostAndPort + "/management/tenant-monitoring/servers/";
    checkApp(url);

    String wmUrl = "http://" + hostAndPort + workManagers;
    checkApp(wmUrl);

    if (OKE_CLUSTER) {
      // create secret for internal OKE cluster
      createBaseRepoSecret(domainNamespace);
    }

    //Verify the dynamic configuration update
    LinkedHashMap<String, OffsetDateTime> pods = new LinkedHashMap<>();
    // get the creation time of the admin server pod before patching
    pods.put(adminServerPodName, getPodCreationTime(domainNamespace, adminServerPodName));
    // get the creation time of the managed server pods before patching
    for (int i = 1; i <= replicaCount; i++) {
      pods.put(managedServerPrefix + i, getPodCreationTime(domainNamespace, managedServerPrefix + i));
    }
    for (int i = 1; i <= replicaCount; i++) {
      pods.put(managedServerPrefix + i, getPodCreationTime(domainNamespace, managedServerPrefix + i));
    }

    replaceConfigMapWithModelFiles(configMapName, domainUid, domainNamespace,
        Arrays.asList(MODEL_DIR + "/model.config.wm.yaml"), withStandardRetryPolicy);

    String introspectVersion = patchDomainResourceWithNewIntrospectVersion(domainUid, domainNamespace);
    verifyIntrospectorRuns(domainUid, domainNamespace);

    wmUrl = "http://" + hostAndPort + workManagers;
    checkApp(wmUrl);

    wmUrl = "http://" + hostAndPort + newWM;
    checkApp(wmUrl);
    logger.info("Found new work manager runtime");

    verifyPodsNotRolled(domainNamespace, pods);
    verifyPodIntrospectVersionUpdated(pods.keySet(), introspectVersion, domainNamespace);
  }
  
  private DomainResource createDomainResource(String domainUid, String domNamespace,
                                              String adminSecretName, String repoSecretName,
                                              String encryptionSecretName,
                                              String miiImage, String configmapName) {

    // create the domain CR
    DomainResource domain = new DomainResource()
        .apiVersion(DOMAIN_API_VERSION)
        .kind("Domain")
        .metadata(new V1ObjectMeta()
            .name(domainUid)
            .namespace(domNamespace))
        .spec(new DomainSpec()
            .domainUid(domainUid)
            .domainHomeSourceType("FromModel")
            .image(miiImage)
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
                    .value("-Dweblogic.StdoutDebugEnabled=false -Dweblogic.rjvm.enableprotocolswitch=true"))
                .addEnvItem(new V1EnvVar()
                    .name("USER_MEM_ARGS")
                    .value("-Djava.security.egd=file:/dev/./urandom ")))
            .adminServer(createAdminServer())
            .configuration(new Configuration()
                .model(new Model()
                    .domainType("WLS")
                    .configMap(configmapName)
                    .onlineUpdate(new OnlineUpdate()
                        .enabled(true))
                    .runtimeEncryptionSecret(encryptionSecretName))
            .introspectorJobActiveDeadlineSeconds(3000L)));
    setPodAntiAffinity(domain);
    return createClusterResourceAndAddReferenceToDomain(
        domainUid + "-" + clusterName, clusterName, domainNamespace, domain, replicaCount);
  }
  
  private static void enableStrictMode(String namespace) {
    Path srcFile = Paths.get(RESOURCE_DIR, "istio", "istio-tls-mode.yaml");
    Path dstFile = Paths.get(WORK_DIR, "istio-tls-mode.yaml");
    logger.info("Enabling STRICT mTLS mode in istio in namesapce {0}", namespace);
    assertDoesNotThrow(() -> {
      copyFile(srcFile.toFile(), dstFile.toFile());
      replaceStringInFile(dstFile.toString(), "NAMESPACE", namespace);
      ExecResult result = ExecCommand.exec(KUBERNETES_CLI + " apply -f "
          + Paths.get(WORK_DIR, "istio-tls-mode.yaml").toString(), true);
      assertEquals(0, result.exitValue(), "Failed to enable mTLS strict mode");
      logger.info(result.stdout());
      logger.info(result.stderr());
    });
  }
  
  private void checkApp(String url) {
    testUntil(
        () -> checkAppUsingHostHeader(url, domainNamespace + ".org"),
        logger,
        "application to be ready {0}",
        url);
  }
}

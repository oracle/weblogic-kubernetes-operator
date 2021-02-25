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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
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
import oracle.weblogic.kubernetes.actions.impl.primitive.Kubernetes;
import oracle.weblogic.kubernetes.annotations.IntegrationTest;
import oracle.weblogic.kubernetes.assertions.TestAssertions;
import oracle.weblogic.kubernetes.logging.LoggingFacade;
import oracle.weblogic.kubernetes.utils.BuildApplication;
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
import static oracle.weblogic.kubernetes.TestConstants.DOMAIN_API_VERSION;
import static oracle.weblogic.kubernetes.TestConstants.DOMAIN_VERSION;
import static oracle.weblogic.kubernetes.TestConstants.OCIR_SECRET_NAME;
import static oracle.weblogic.kubernetes.TestConstants.RESULTS_ROOT;
import static oracle.weblogic.kubernetes.actions.ActionConstants.APP_DIR;
import static oracle.weblogic.kubernetes.actions.ActionConstants.MODEL_DIR;
import static oracle.weblogic.kubernetes.actions.ActionConstants.RESOURCE_DIR;
import static oracle.weblogic.kubernetes.actions.TestActions.addLabelsToNamespace;
import static oracle.weblogic.kubernetes.actions.TestActions.createDomainCustomResource;
import static oracle.weblogic.kubernetes.actions.TestActions.getServiceNodePort;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.domainExists;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.checkAppUsingHostHeader;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.checkPodExists;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.checkPodReady;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.checkServiceExists;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.createImageAndVerify;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.createOcirRepoSecret;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.createSecretWithUsernamePassword;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.dockerLoginAndPushImageToRegistry;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.generateFileFromTemplate;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.getExternalServicePodName;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.installAndVerifyOperator;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.setPodAntiAffinity;
import static oracle.weblogic.kubernetes.utils.IstioUtils.deployHttpIstioGatewayAndVirtualservice;
import static oracle.weblogic.kubernetes.utils.IstioUtils.getIstioHttpIngressPort;
import static oracle.weblogic.kubernetes.utils.ThreadSafeLogger.getLogger;
import static org.awaitility.Awaitility.with;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;


@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("Setup operator, domain, applications for cross cluster domain transaction.")
@IntegrationTest
@DisabledIfEnvironmentVariable(named = "TWO_CLUSTERS", matches = "false")
public class ItIstioCrossClustersSetup {

  private static final String WDT_MODEL_FILE_DOMAIN1 = "model-crossdomaintransaction-domain1.yaml";
  private static final String WDT_MODEL_DOMAIN1_PROPS = "model-crossdomaintransaction-domain1.properties";
  private static final String WDT_IMAGE_NAME1 = "crossclustersdomain1-wdt-image";
  private static final String PROPS_TEMP_DIR = RESULTS_ROOT + "/istiocrossclustersdomaintransactiontemp";
  private static final String WDT_MODEL_FILE_JMS = "model-cdt-jms.yaml";

  private static String op1Namespace = "crosscluster-operator1-cluster1";
  private static String domain1Namespace = "crosscluster-domain1-cluster1";
  private static ConditionFactory withStandardRetryPolicy = null;
  private static String domainUid1 = "domain1";
  private static String clusterName = "cluster-1";
  private static final String domain1AdminServerPodName = domainUid1 + "-admin-server";
  private static String domain1Image;
  private static LoggingFacade logger = null;
  static int istioIngressPort;
  private static String K8S_NODEPORT_HOST1 = System.getenv("K8S_NODEPORT_HOST1");
  private static boolean TWO_CLUSTERS = Boolean.parseBoolean(java.util.Optional.ofNullable(
      System.getenv("TWO_CLUSTERS"))
      .orElse("false"));

  /**
   * Install Operator.
   *     JUnit engine parameter resolution mechanism
   */
  @BeforeAll
  public static void initAll() {
    logger = getLogger();
    // create standard, reusable retry/backoff policy
    withStandardRetryPolicy = with().pollDelay(2, SECONDS)
        .and().with().pollInterval(10, SECONDS)
        .atMost(5, MINUTES).await();
    List<String> clusterOneNamespaces = new ArrayList<>();
    clusterOneNamespaces.add(op1Namespace);
    clusterOneNamespaces.add(domain1Namespace);
    //clean before running the test
    try {
      oracle.weblogic.kubernetes.utils.CleanupUtil.cleanup(clusterOneNamespaces);
    } catch (Exception ex) {
      //ignore
    }
    updatePropertyFile();
    // Label the domain/operator namespace with istio-injection=enabled
    java.util.Map<String, String> labelMap = new java.util.HashMap();
    labelMap.put("istio-injection", "enabled");

    assertDoesNotThrow(() -> addLabelsToNamespace(domain1Namespace, labelMap));
    assertDoesNotThrow(() -> addLabelsToNamespace(op1Namespace, labelMap));
  }

  @Test
  @DisplayName("Build applications and create operator and domain in cluster1")
  public void testInitCluster1() {
    logger.info("Creating namespace for Operator in cluster1");
    assertDoesNotThrow(() -> Kubernetes.createNamespace(op1Namespace),
        "Failed to create namespace for operator in cluster1");

    logger.info("Creating namespace for Domain in cluster1");
    assertDoesNotThrow(() -> Kubernetes.createNamespace(domain1Namespace),
        "Failed to create namespace for domain in cluster1");

    // install and verify operator
    installAndVerifyOperator(op1Namespace, domain1Namespace);

    //build application archive
    Path distDir = BuildApplication.buildApplication(Paths.get(APP_DIR, "txforward"), null, null,
        "build", domain1Namespace);
    logger.info("distDir is {0}", distDir.toString());
    assertTrue(Paths.get(distDir.toString(),
        "txforward.ear").toFile().exists(),
        "Application archive is not available");
    String appSource = distDir.toString() + "/txforward.ear";
    logger.info("Application is in {0}", appSource);

    //build application archive
    distDir = BuildApplication.buildApplication(Paths.get(APP_DIR, "cdtservlet"), null, null,
        "build", domain1Namespace);
    logger.info("distDir is {0}", distDir.toString());
    assertTrue(Paths.get(distDir.toString(),
        "cdttxservlet.war").toFile().exists(),
        "Application archive is not available");
    String appSource1 = distDir.toString() + "/cdttxservlet.war";
    logger.info("Application is in {0}", appSource1);

    // build the model file list for domain1
    final List<String> modelListDomain1 = Arrays.asList(
        MODEL_DIR + "/" + WDT_MODEL_FILE_DOMAIN1,
        MODEL_DIR + "/" + WDT_MODEL_FILE_JMS);

    final List<String> appSrcDirList1 = Arrays.asList(appSource, appSource1);

    logger.info("Creating image with model file and verify");
    domain1Image = createImageAndVerify(
        WDT_IMAGE_NAME1, modelListDomain1, appSrcDirList1, WDT_MODEL_DOMAIN1_PROPS, PROPS_TEMP_DIR, domainUid1);
    logger.info("Created {0} image", domain1Image);

    // docker login and push image to docker registry if necessary
    dockerLoginAndPushImageToRegistry(domain1Image);

    // create admin credential secret for domain1
    logger.info("Create admin credential secret for domain1");
    String domain1AdminSecretName = domainUid1 + "-weblogic-credentials";
    assertDoesNotThrow(() -> createSecretWithUsernamePassword(
        domain1AdminSecretName, domain1Namespace, ADMIN_USERNAME_DEFAULT, ADMIN_PASSWORD_DEFAULT),
        String.format("createSecret %s failed for %s", domain1AdminSecretName, domainUid1));


    //create domain1
    createDomain(domainUid1, domain1Namespace, domain1AdminSecretName, domain1Image);
    int adminServiceNodePort = assertDoesNotThrow(
        () -> getServiceNodePort(domain1Namespace, getExternalServicePodName(domain1AdminServerPodName), "default"),
        "Getting admin server node port failed");

    String clusterService = domainUid1 + "-cluster-" + clusterName + "." + domain1Namespace + ".svc.cluster.local";

    java.util.Map<String, String> templateMap = new java.util.HashMap();
    templateMap.put("NAMESPACE", domain1Namespace);
    templateMap.put("ADMIN_SERVICE", domain1AdminServerPodName);
    templateMap.put("CLUSTER_SERVICE", clusterService);

    Path svcYamlSrc = Paths.get(RESOURCE_DIR, "istio", "istio-cdt-http-template-service.yaml");
    Path svcYmlTarget = assertDoesNotThrow(
        () -> generateFileFromTemplate(svcYamlSrc.toString(),
            "istiocrossdomaintransactiontemp/istio-cdt-http-service.yaml", templateMap));
    logger.info("Generated Http VS/Gateway file path is {0}", svcYmlTarget);

    boolean deployRes = deployHttpIstioGatewayAndVirtualservice(svcYmlTarget);
    assertTrue(deployRes, "Could not deploy Http Istio Gateway/VirtualService");

    istioIngressPort = getIstioHttpIngressPort();
    logger.info("Istio Ingress Port is {0}", istioIngressPort);

    logger.info("Validating WebLogic admin server access by login to console");
    String consoleUrl = "http://" + K8S_NODEPORT_HOST1 + ":" + istioIngressPort + "/console/login/LoginForm.jsp";
    boolean checkConsole =
        checkAppUsingHostHeader(consoleUrl, "domain1-" + domain1Namespace + ".org");
    assertTrue(checkConsole, "Failed to access WebLogic console on domain1");
    logger.info("WebLogic console on domain1 is accessible");
    assertDoesNotThrow(() -> {
      addToPropertyFile(WDT_MODEL_DOMAIN1_PROPS, domain1Namespace, K8S_NODEPORT_HOST1,
          String.valueOf(adminServiceNodePort), String.valueOf(istioIngressPort));
      String.format("Failed to update %s with namespace %s",
          WDT_MODEL_DOMAIN1_PROPS, domain1Namespace);
    });
  }

  private static void updatePropertyFile() {
    //create a temporary directory to copy and update the properties file
    Path target = Paths.get(PROPS_TEMP_DIR);
    Path source1 = Paths.get(MODEL_DIR, WDT_MODEL_DOMAIN1_PROPS);
    logger.info("Copy the properties file to the above area so that we can add namespace property");
    assertDoesNotThrow(() -> {
      Files.createDirectories(target);
      Files.copy(source1, target.resolve(source1.getFileName()), StandardCopyOption.REPLACE_EXISTING);
    });

    assertDoesNotThrow(() -> {
      addToPropertyFile(WDT_MODEL_DOMAIN1_PROPS, domain1Namespace, K8S_NODEPORT_HOST1,"0", "0");
      String.format("Failed to update %s with namespace %s",
          WDT_MODEL_DOMAIN1_PROPS, domain1Namespace);
    });

  }

  private static void addToPropertyFile(String propFileName, String domainNamespace,
                                        String host, String adminServiceNodePort,
                                        String istioIngressPort) throws IOException {
    FileInputStream in = new FileInputStream(PROPS_TEMP_DIR + "/" + propFileName);
    Properties props = new Properties();
    props.load(in);
    in.close();

    FileOutputStream out = new FileOutputStream(PROPS_TEMP_DIR + "/" + propFileName);
    props.setProperty("NAMESPACE", domainNamespace);
    props.setProperty("K8S_NODEPORT_HOST", host);
    props.setProperty("ADMIN_SERVICE_NODE_PORT", adminServiceNodePort);
    props.setProperty("ISTIO_INGRESS_PORT", istioIngressPort);
    props.store(out, null);
    out.close();
  }


  /**
   * Create a basic Kubernetes domain resource and wait until the domain is fully up.
   *
   * @param domainNamespace Kubernetes namespace that the pod is running in
   * @param domainUid identifier of the domain
   * @param domainImage name of the image including its tag
   * @param adminSecretName name of the admin secret
   */
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

  /**
   * Create a basic Kubernetes domain resource and wait until the domain is fully up.
   *
   * @param domNamespace Kubernetes namespace that the pod is running in
   * @param domainUid identifier of the domain
   * @param domainImage name of the image including its tag
   * @param adminSecretName name of the admin secret
   * @param repoSecretName name of the secret for repo
   * @param replicaCount number of managed servers to start
   */
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

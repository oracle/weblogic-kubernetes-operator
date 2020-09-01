// Copyright (c) 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

import com.gargoylesoftware.htmlunit.WebClient;
import com.gargoylesoftware.htmlunit.html.HtmlElement;
import com.gargoylesoftware.htmlunit.html.HtmlForm;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import io.kubernetes.client.custom.Quantity;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.models.V1ConfigMapVolumeSource;
import io.kubernetes.client.openapi.models.V1Container;
import io.kubernetes.client.openapi.models.V1ContainerPort;
import io.kubernetes.client.openapi.models.V1EnvVar;
import io.kubernetes.client.openapi.models.V1HostPathVolumeSource;
import io.kubernetes.client.openapi.models.V1Job;
import io.kubernetes.client.openapi.models.V1JobSpec;
import io.kubernetes.client.openapi.models.V1LocalObjectReference;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1ObjectMetaBuilder;
import io.kubernetes.client.openapi.models.V1PersistentVolume;
import io.kubernetes.client.openapi.models.V1PersistentVolumeClaim;
import io.kubernetes.client.openapi.models.V1PersistentVolumeClaimSpec;
import io.kubernetes.client.openapi.models.V1PersistentVolumeClaimVolumeSource;
import io.kubernetes.client.openapi.models.V1PersistentVolumeSpec;
import io.kubernetes.client.openapi.models.V1PodSpec;
import io.kubernetes.client.openapi.models.V1PodTemplateSpec;
import io.kubernetes.client.openapi.models.V1ResourceRequirements;
import io.kubernetes.client.openapi.models.V1Secret;
import io.kubernetes.client.openapi.models.V1SecretReference;
import io.kubernetes.client.openapi.models.V1SecurityContext;
import io.kubernetes.client.openapi.models.V1Volume;
import io.kubernetes.client.openapi.models.V1VolumeMount;
import oracle.weblogic.domain.AdminServer;
import oracle.weblogic.domain.AdminService;
import oracle.weblogic.domain.Channel;
import oracle.weblogic.domain.Cluster;
import oracle.weblogic.domain.Domain;
import oracle.weblogic.domain.DomainSpec;
import oracle.weblogic.domain.ServerPod;
import oracle.weblogic.kubernetes.actions.ActionConstants;
import oracle.weblogic.kubernetes.actions.impl.primitive.HelmParams;
import oracle.weblogic.kubernetes.annotations.IntegrationTest;
import oracle.weblogic.kubernetes.annotations.Namespaces;
import oracle.weblogic.kubernetes.logging.LoggingFacade;
import oracle.weblogic.kubernetes.utils.BuildApplication;
import oracle.weblogic.kubernetes.utils.DeployUtil;
import oracle.weblogic.kubernetes.utils.ExecCommand;
import oracle.weblogic.kubernetes.utils.ExecResult;
import org.awaitility.core.ConditionFactory;
import org.joda.time.DateTime;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import static java.nio.file.Files.copy;
import static java.nio.file.Files.createDirectories;
import static java.nio.file.Paths.get;
import static java.util.concurrent.TimeUnit.MINUTES;
import static java.util.concurrent.TimeUnit.SECONDS;
import static oracle.weblogic.kubernetes.TestConstants.ADMIN_PASSWORD_DEFAULT;
import static oracle.weblogic.kubernetes.TestConstants.ADMIN_SERVER_NAME_BASE;
import static oracle.weblogic.kubernetes.TestConstants.ADMIN_USERNAME_DEFAULT;
import static oracle.weblogic.kubernetes.TestConstants.APACHE_IMAGE;
import static oracle.weblogic.kubernetes.TestConstants.APACHE_RELEASE_NAME;
import static oracle.weblogic.kubernetes.TestConstants.DOMAIN_API_VERSION;
import static oracle.weblogic.kubernetes.TestConstants.DOMAIN_VERSION;
import static oracle.weblogic.kubernetes.TestConstants.K8S_NODEPORT_HOST;
import static oracle.weblogic.kubernetes.TestConstants.KIND_REPO;
import static oracle.weblogic.kubernetes.TestConstants.MANAGED_SERVER_NAME_BASE;
import static oracle.weblogic.kubernetes.TestConstants.OCR_REGISTRY;
import static oracle.weblogic.kubernetes.TestConstants.OCR_SECRET_NAME;
import static oracle.weblogic.kubernetes.TestConstants.PV_ROOT;
import static oracle.weblogic.kubernetes.TestConstants.REPO_DEFAULT;
import static oracle.weblogic.kubernetes.TestConstants.REPO_PASSWORD;
import static oracle.weblogic.kubernetes.TestConstants.REPO_REGISTRY;
import static oracle.weblogic.kubernetes.TestConstants.REPO_USERNAME;
import static oracle.weblogic.kubernetes.TestConstants.RESULTS_ROOT;
import static oracle.weblogic.kubernetes.TestConstants.VOYAGER_CHART_NAME;
import static oracle.weblogic.kubernetes.actions.ActionConstants.APP_DIR;
import static oracle.weblogic.kubernetes.actions.ActionConstants.RESOURCE_DIR;
import static oracle.weblogic.kubernetes.actions.ActionConstants.WLS_BASE_IMAGE_NAME;
import static oracle.weblogic.kubernetes.actions.ActionConstants.WLS_BASE_IMAGE_TAG;
import static oracle.weblogic.kubernetes.actions.TestActions.deleteConfigMap;
import static oracle.weblogic.kubernetes.actions.TestActions.deleteDomainCustomResource;
import static oracle.weblogic.kubernetes.actions.TestActions.deleteIngress;
import static oracle.weblogic.kubernetes.actions.TestActions.deleteJob;
import static oracle.weblogic.kubernetes.actions.TestActions.deletePersistentVolume;
import static oracle.weblogic.kubernetes.actions.TestActions.deletePersistentVolumeClaim;
import static oracle.weblogic.kubernetes.actions.TestActions.deletePod;
import static oracle.weblogic.kubernetes.actions.TestActions.deleteSecret;
import static oracle.weblogic.kubernetes.actions.TestActions.dockerLogin;
import static oracle.weblogic.kubernetes.actions.TestActions.dockerPull;
import static oracle.weblogic.kubernetes.actions.TestActions.dockerPush;
import static oracle.weblogic.kubernetes.actions.TestActions.dockerTag;
import static oracle.weblogic.kubernetes.actions.TestActions.getServiceNodePort;
import static oracle.weblogic.kubernetes.actions.TestActions.listIngresses;
import static oracle.weblogic.kubernetes.actions.TestActions.listJobs;
import static oracle.weblogic.kubernetes.actions.TestActions.listPods;
import static oracle.weblogic.kubernetes.actions.TestActions.listSecrets;
import static oracle.weblogic.kubernetes.actions.TestActions.shutdownDomain;
import static oracle.weblogic.kubernetes.actions.TestActions.startDomain;
import static oracle.weblogic.kubernetes.actions.TestActions.uninstallApache;
import static oracle.weblogic.kubernetes.actions.TestActions.uninstallOperator;
import static oracle.weblogic.kubernetes.actions.TestActions.uninstallTraefik;
import static oracle.weblogic.kubernetes.actions.TestActions.uninstallVoyager;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.domainDoesNotExist;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.podStateNotChanged;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.checkPodDoesNotExist;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.checkPodReadyAndServiceExists;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.checkPodRestarted;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.createConfigMapFromFiles;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.createDomainAndVerify;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.createJobAndWaitUntilComplete;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.createOCRRepoSecret;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.createPVPVCAndVerify;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.createSecretWithTLSCertKey;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.createSecretWithUsernamePassword;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.getPodCreationTime;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.installAndVerifyApache;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.installAndVerifyOperator;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.installAndVerifyTraefik;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.installAndVerifyVoyager;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.installVoyagerIngressAndVerify;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.scaleAndVerifyCluster;
import static oracle.weblogic.kubernetes.utils.TestUtils.getNextFreePort;
import static oracle.weblogic.kubernetes.utils.TestUtils.verifyServerCommunication;
import static oracle.weblogic.kubernetes.utils.ThreadSafeLogger.getLogger;
import static org.apache.commons.io.FileUtils.deleteDirectory;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.with;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Test operator manages multiple domains.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("Verify operator manages multiple domains")
@IntegrationTest
public class ItTwoDomainsLoadBalancers {

  private static final int numberOfDomains = 2;
  private static final int numberOfOperators = 2;
  private static final String wlSecretName = "weblogic-credentials";

  private static boolean isUseSecret = true;
  private static String defaultNamespace = "default";
  private static String image = WLS_BASE_IMAGE_NAME + ":" + WLS_BASE_IMAGE_TAG;
  private static String domain1Uid = null;
  private static String domain2Uid = null;
  private static String domain1Namespace = null;
  private static String domain2Namespace = null;
  private static String traefikNamespace = null;
  private static String voyagerNamespace = null;
  private static List<String> opNamespaces = new ArrayList<>();
  private static List<String> domainNamespaces = new ArrayList<>();
  private static List<String> domainUids = new ArrayList<>();
  private static HelmParams operatorHelmParams = null;
  private static HelmParams traefikHelmParams = null;
  private static HelmParams voyagerHelmParams = null;
  private static Path tlsCertFile;
  private static Path tlsKeyFile;
  private static Path clusterViewAppPath;
  private static LoggingFacade logger = null;
  private static Path dstFile = null;
  private static HelmParams apacheHelmParams1 = null;
  private static HelmParams apacheHelmParams2 = null;
  private static String kindRepoApacheImage = APACHE_IMAGE;

  // domain constants
  private final String clusterName = "cluster-1";
  private final int replicaCount = 2;
  private final String managedServerPort = "8001";

  private int t3ChannelPort = 0;
  private int replicasAfterScale;
  private List<String> domainAdminServerPodNames = new ArrayList<>();
  private List<DateTime> domainAdminPodOriginalTimestamps = new ArrayList<>();
  private List<DateTime> domain1ManagedServerPodOriginalTimestampList = new ArrayList<>();
  private List<DateTime> domain2ManagedServerPodOriginalTimestampList = new ArrayList<>();

  /**
   * Get namespaces, install operator and initiate domain UID list.
   *
   * @param namespaces injected by JUnit
   */
  @BeforeAll
  public static void initAll(@Namespaces(6) List<String> namespaces) {
    logger = getLogger();
    // get unique operator namespaces
    logger.info("Get unique namespaces for operator1 and operator2");
    for (int i = 0; i < numberOfOperators; i++) {
      assertNotNull(namespaces.get(i), "Namespace list is null");
      opNamespaces.add(namespaces.get(i));
    }

    // get unique domain namespaces
    logger.info("Get unique namespaces for WebLogic domain1 and domain2");
    for (int i = numberOfOperators; i < numberOfOperators + numberOfDomains; i++) {
      assertNotNull(namespaces.get(i), "Namespace list is null");
      domainNamespaces.add(namespaces.get(i));
    }

    logger.info("Assign a unique namespace for Traefik");
    assertNotNull(namespaces.get(4), "Namespace list is null");
    traefikNamespace = namespaces.get(4);

    // get a unique Voyager namespace
    logger.info("Assign a unique namespace for Voyager");
    assertNotNull(namespaces.get(5), "Namespace list is null");
    voyagerNamespace = namespaces.get(5);

    // install and verify operator
    operatorHelmParams = installAndVerifyOperator(opNamespaces.get(0), domainNamespaces.get(0), defaultNamespace);
    installAndVerifyOperator(opNamespaces.get(1), domainNamespaces.get(1));

    // initiate domainUid list for two domains
    for (int i = 1; i <= numberOfDomains; i++) {
      domainUids.add("tdlbs-domain" + i);
    }

    domain1Uid = domainUids.get(0);
    domain2Uid = domainUids.get(1);
    domain1Namespace = domainNamespaces.get(0);
    domain2Namespace = domainNamespaces.get(1);

    //determine if the tests are running in Kind cluster. if true use images from Kind registry
    if (KIND_REPO != null) {
      String kindRepoImage = KIND_REPO + image.substring(OCR_REGISTRY.length() + 1);
      logger.info("Using image {0}", kindRepoImage);
      image = kindRepoImage;
      isUseSecret = false;

      // The kind clusters can't pull Apache webtier image from OCIR using the image pull secret.
      // Try the following instead:
      //   1. docker login
      //   2. docker pull
      //   3. docker tag with the KIND_REPO value
      //   4. docker push to KIND_REPO

      ConditionFactory withStandardRetryPolicy
          = with().pollDelay(0, SECONDS)
          .and().with().pollInterval(10, SECONDS)
          .atMost(30, MINUTES).await();

      withStandardRetryPolicy
          .conditionEvaluationListener(
              condition -> logger.info("Waiting for docker login to be successful"
                      + "(elapsed time {0} ms, remaining time {1} ms)",
                  condition.getElapsedTimeInMS(),
                  condition.getRemainingTimeInMS()))
          .until(() -> dockerLogin(REPO_REGISTRY, REPO_USERNAME, REPO_PASSWORD));

      withStandardRetryPolicy
          .conditionEvaluationListener(
              condition -> logger.info("Waiting for pullImageFromOcirAndPushToKind for image {0} to be successful"
                      + "(elapsed time {1} ms, remaining time {2} ms)", APACHE_IMAGE,
                  condition.getElapsedTimeInMS(),
                  condition.getRemainingTimeInMS()))
          .until(pullImageFromOcirAndPushToKind(APACHE_IMAGE));
    }
  }

  /**
   * Test covers the following use cases.
   * create two domains on PV using WLST
   * domain1 managed by operator1
   * domain2 managed by operator2
   * scale cluster in domain1 from 2 to 3 servers and verify no impact on domain2, domain2 continues to run
   * restart domain1 and verify no impact on domain2, domain2 continues to run
   * shutdown the domains using serverStartPolicy
   */
  @Order(1)
  @Test
  @DisplayName("Create domain on PV using WLST script")
  public void testTwoDomainsManagedByTwoOperators() {

    // create two domains on PV using WLST
    createTwoDomainsOnPVUsingWlstAndVerify();

    // get the domain1 and domain2 pods original creation timestamps
    getBothDomainsPodsOriginalCreationTimestamp(domainUids, domainNamespaces);

    // scale cluster in domain1 from 2 to 3 servers and verify no impact on domain2
    replicasAfterScale = 3;
    scaleDomain1AndVerifyNoImpactOnDomain2();

    // restart domain1 and verify no impact on domain2
    restartDomain1AndVerifyNoImpactOnDomain2(replicasAfterScale, domain1Namespace, domain2Namespace);

    // shutdown domain2 and verify the pods were shutdown
    shutdownDomainAndVerify(domain2Namespace, domain2Uid);
  }

  /**
   * Create one operator if it is not running.
   * Create domain domain1 and domain2 dynamic cluster in default namespace, managed by operator1.
   * Both domains share one PV.
   * Verify scaling for domain2 cluster from 2 to 3 servers and back to 2, plus verify no impact on domain1.
   * Shut down domain1 and back up, plus verify no impact on domain2.
   * shutdown both domains
   */
  @Order(2)
  @Test
  public void testTwoDomainsManagedByOneOperatorSharingPV() {
    // create two domains sharing one PV in default namespace
    createTwoDomainsSharingPVUsingWlstAndVerify();

    // get the domain1 and domain2 pods original creation timestamps
    List<String> domainNamespaces = new ArrayList<>();
    domainNamespaces.add(defaultNamespace);
    domainNamespaces.add(defaultNamespace);
    getBothDomainsPodsOriginalCreationTimestamp(domainUids, domainNamespaces);

    // scale domain2 to 3 servers and back to 2 and verify no impact on domain1
    scaleDomain2AndVerifyNoImpactOnDomain1();

    // restart domain1 and verify no impact on domain2
    restartDomain1AndVerifyNoImpactOnDomain2(replicaCount, defaultNamespace, defaultNamespace);
  }

  /**
   * Test deploy applications and install ingress controllers Traefik and Voyager.
   */
  @Order(3)
  @Test
  public void testDeployAppAndInstallIngressControllers() {

    // install and verify Traefik
    logger.info("Installing Traefik controller using helm");
    traefikHelmParams = installAndVerifyTraefik(traefikNamespace, 0, 0);

    final String cloudProvider = "baremetal";
    final boolean enableValidatingWebhook = false;

    // install and verify Voyager
    voyagerHelmParams =
        installAndVerifyVoyager(voyagerNamespace, cloudProvider, enableValidatingWebhook);

    // build the clusterview application
    logger.info("Building clusterview application");
    Path distDir = BuildApplication.buildApplication(Paths.get(APP_DIR, "clusterview"), null, null,
        "dist", defaultNamespace);
    assertTrue(Paths.get(distDir.toString(),
        "clusterview.war").toFile().exists(),
        "Application archive is not available");
    clusterViewAppPath = Paths.get(distDir.toString(), "clusterview.war");

    // deploy clusterview application in default namespace
    for (String domainUid : domainUids) {
      // admin/managed server name here should match with model yaml in MII_BASIC_WDT_MODEL_FILE
      String adminServerPodName = domainUid + "-admin-server";
      deployApplication(defaultNamespace, domainUid, adminServerPodName);
    }

    // deploy clusterview application in domain1Namespace
    deployApplication(domain1Namespace, domain1Uid, domain1Uid + "-admin-server");

    // create TLS secret for https traffic
    for (String domainUid : domainUids) {
      createCertKeyFiles(domainUid);
      assertDoesNotThrow(() -> createSecretWithTLSCertKey(domainUid + "-tls-secret",
          defaultNamespace, tlsKeyFile, tlsCertFile));
    }
    // create loadbalancing rules for Traefik
    createTraefikIngressRoutingRules();
    createVoyagerIngressRoutingRules();

    // install and verify Apache
    apacheHelmParams1 = assertDoesNotThrow(
        () -> installAndVerifyApache(domain1Namespace, kindRepoApacheImage, 0, 0, domain1Uid));

    LinkedHashMap<String, String> clusterNamePortMap = new LinkedHashMap<>();
    for (int i = 0; i < numberOfDomains; i++) {
      clusterNamePortMap.put(domainUids.get(i) + "-cluster-cluster-1", managedServerPort);
    }
    apacheHelmParams2 = assertDoesNotThrow(
        () -> installAndVerifyApache(defaultNamespace, kindRepoApacheImage, 0, 0, domain1Uid,
            PV_ROOT + "/" + this.getClass().getSimpleName(), "apache-sample-host", clusterNamePortMap));
  }

  /**
   * Test verifies admin server access through Traefik loadbalancer. Accesses the admin server for each domain through
   * Traefik loadbalancer web port and verifies it is a console login page.
   */
  @Order(4)
  @Test
  @DisplayName("Access admin server console through Traefik loadbalancer")
  public void testTraefikHostRoutingAdminServer() {
    logger.info("Verifying admin server access through Traefik");
    for (String domainUid : domainUids) {
      verifyAdminServerAccess(domainUid);
    }
  }

  /**
   * Test verifies multiple WebLogic domains can be loadbalanced by Traefik loadbalancer with host based routing rules.
   * Accesses the clusterview application deployed in the WebLogic cluster through Traefik loadbalancer web
   * channel and verifies it is correctly routed to the specific domain cluster identified by the -H host header.
   *
   */
  @Order(5)
  @Test
  @DisplayName("Create domain1 and domain2 and Ingress resources")
  public void testTraefikHttpHostRoutingAcrossDomains() {

    // verify load balancing works when 2 domains are running in the same namespace
    logger.info("Verifying http traffic");
    for (String domainUid : domainUids) {
      verifyClusterLoadbalancing(domainUid, defaultNamespace, "http", getTraefikLbNodePort(false),
          replicaCount, true, "");
    }
  }

  /**
   * Test verifies multiple WebLogic domains can be loadbalanced by Traefik loadbalancer with host based routing rules.
   * Accesses the clusterview application deployed in the WebLogic cluster through Traefik loadbalancer websecure
   * channel and verifies it is correctly routed to the specific domain cluster identified by the -H host header.
   */
  @Order(6)
  @Test
  @DisplayName("Loadbalance WebLogic cluster traffic through Traefik loadbalancer websecure channel")
  public void testTraefikHostHttpsRoutingAcrossDomains() {

    logger.info("Verifying https traffic");
    for (String domainUid : domainUids) {
      verifyClusterLoadbalancing(domainUid, defaultNamespace, "https", getTraefikLbNodePort(true),
          replicaCount, true, "");
    }
  }

  /**
   * Test verifies multiple WebLogic domains can be loadbalanced by Voyager loadbalancer with host based routing rules.
   * Accesses the clusterview application deployed in the WebLogic cluster through Voyager loadbalancer and verifies it
   * is correctly routed to the specific domain cluster identified by the -H host header.
   */
  @Order(7)
  @Test
  @DisplayName("Loadbalance WebLogic cluster traffic through Voyager loadbalancer tcp channel")
  public void testVoyagerHostHttpRoutingAcrossDomains() {

    // verify load balancing works when 2 domains are running in the same namespace
    logger.info("Verifying http traffic");
    for (String domainUid : domainUids) {
      String ingressName = domainUid + "-ingress-host-routing";
      verifyClusterLoadbalancing(domainUid, defaultNamespace, "http", getVoyagerLbNodePort(ingressName),
          replicaCount, true, "");
    }
  }

  /**
   * Verify Apache load balancer default sample through HTTP channel.
   * Configure the Apache webtier as a load balancer for a WebLogic domain using the default configuration.
   * It only support HTTP protocol.
   * For details, please see
   * https://github.com/oracle/weblogic-kubernetes-operator/tree/master/kubernetes/samples/charts/apache-samples/default-sample
   */
  @Order(8)
  @Test
  @DisplayName("verify Apache load balancer default sample through HTTP channel")
  public void testApacheDefaultSample() {

    // verify Apache default sample
    logger.info("Verifying Apache default sample");
    int httpNodePort = getApacheNodePort(domain1Namespace, "http");
    verifyClusterLoadbalancing(domain1Uid, domain1Namespace, "http", httpNodePort, 3, false, "/weblogic");
  }

  /**
   * Verify Apache load balancer custom sample through HTTP and HTTPS channel.
   * Configure the Apache webtier as a load balancer for multiple WebLogic domains using a custom configuration.
   * Create a custom Apache plugin configuration file named custom_mod_wl_apache.conf in a directory specified
   * in helm chart parameter volumePath.
   * For more details, please check:
   * https://github.com/oracle/weblogic-kubernetes-operator/tree/master/kubernetes/samples/charts/apache-samples/custom-sample
   */
  @Order(9)
  @Test
  @DisplayName("verify Apache load balancer custom sample through HTTP and HTTPS channel")
  public void testApacheCustomSample() {

    // verify Apache custom sample
    logger.info("Verifying Apache custom sample");
    for (int i = 1; i <= numberOfDomains; i++) {
      int httpNodePort = getApacheNodePort(defaultNamespace, "http");
      verifyClusterLoadbalancing(domainUids.get(i - 1), defaultNamespace, "http", httpNodePort, replicaCount,
          false, "/weblogic" + i);

      int httpsNodePort = getApacheNodePort(defaultNamespace, "https");
      verifyClusterLoadbalancing(domainUids.get(i - 1), defaultNamespace, "https", httpsNodePort, replicaCount,
          false, "/weblogic" + i);
    }
  }

  /**
   * Cleanup all the remaining artifacts in default namespace created by the test.
   */
  @AfterAll
  public void tearDownAll() {
    // uninstall Traefik loadbalancer
    if (traefikHelmParams != null) {
      assertThat(uninstallTraefik(traefikHelmParams))
          .as("Test uninstallTraefik returns true")
          .withFailMessage("uninstallTraefik() did not return true")
          .isTrue();
    }

    // uninstall Voyager
    if (voyagerHelmParams != null) {
      assertThat(uninstallVoyager(voyagerHelmParams))
          .as("Test uninstallVoyager returns true")
          .withFailMessage("uninstallVoyager() did not return true")
          .isTrue();
    }

    // uninstall Apache
    if (apacheHelmParams1 != null) {
      assertThat(uninstallApache(apacheHelmParams1))
          .as("Test whether uninstallApache in domain1Namespace returns true")
          .withFailMessage("uninstallApache() in domain1Namespace did not return true")
          .isTrue();
    }

    if (apacheHelmParams2 != null) {
      assertThat(uninstallApache(apacheHelmParams2))
          .as("Test whether uninstallApache in default namespace returns true")
          .withFailMessage("uninstallApache() in default namespace did not return true")
          .isTrue();
    }

    // uninstall operator which manages default namespace
    logger.info("uninstalling operator which manages default namespace");
    if (operatorHelmParams != null) {
      assertThat(uninstallOperator(operatorHelmParams))
          .as("Test uninstallOperator returns true")
          .withFailMessage("uninstallOperator() did not return true")
          .isTrue();
    }

    ConditionFactory withStandardRetryPolicy =
        with().pollDelay(2, SECONDS)
            .and().with().pollInterval(10, SECONDS)
            .atMost(5, MINUTES).await();

    for (int i = 1; i <= numberOfDomains; i++) {
      String domainUid = domainUids.get(i - 1);
      // delete domain
      logger.info("deleting domain custom resource {0}", domainUid);
      assertTrue(deleteDomainCustomResource(domainUid, defaultNamespace));

      // wait until domain was deleted
      withStandardRetryPolicy
          .conditionEvaluationListener(
              condition -> logger.info("Waiting for domain {0} to be created in namespace {1} "
                      + "(elapsed time {2}ms, remaining time {3}ms)",
                  domainUid,
                  defaultNamespace,
                  condition.getElapsedTimeInMS(),
                  condition.getRemainingTimeInMS()))
          .until(domainDoesNotExist(domainUid, DOMAIN_VERSION, defaultNamespace));

      // delete configMap in default namespace
      logger.info("deleting configMap {0}", "create-domain" + i + "-scripts-cm");
      assertTrue(deleteConfigMap("create-domain" + i + "-scripts-cm", defaultNamespace));
    }

    // delete configMap weblogic-scripts-cm in default namespace
    logger.info("deleting configMap weblogic-scripts-cm");
    assertTrue(deleteConfigMap("weblogic-scripts-cm", defaultNamespace));

    // Delete jobs
    try {
      for (var item :listJobs(defaultNamespace).getItems()) {
        if (item.getMetadata() != null) {
          deleteJob(item.getMetadata().getName(), defaultNamespace);
        }
      }

      for (var item : listPods(defaultNamespace, null).getItems()) {
        if (item.getMetadata() != null) {
          deletePod(item.getMetadata().getName(), defaultNamespace);
        }
      }
    } catch (ApiException ex) {
      logger.warning(ex.getMessage());
      logger.warning("Failed to delete jobs");
    }

    // delete pv and pvc in default namespace
    logger.info("deleting pvc default-sharing-pvc");
    logger.info("deleting pv default-sharing-pv");
    assertTrue(deletePersistentVolumeClaim("default-sharing-pvc", defaultNamespace));
    assertTrue(deletePersistentVolume("default-sharing-pv"));

    // delete ingressroute in namespace
    if (dstFile != null) {
      String command = "kubectl delete" + " -f " + dstFile;

      logger.info("Running {0}", command);
      try {
        ExecResult result = ExecCommand.exec(command, true);
        String response = result.stdout().trim();
        logger.info("exitCode: {0}, \nstdout: {1}, \nstderr: {2}",
            result.exitValue(), response, result.stderr());
        assertEquals(0, result.exitValue(), "Command didn't succeed");
      } catch (IOException | InterruptedException ex) {
        logger.severe(ex.getMessage());
      }
    }

    // delete ingress in default namespace
    try {
      for (String ingressname : listIngresses(defaultNamespace)) {
        logger.info("deleting ingress {0}", ingressname);
        deleteIngress(ingressname, defaultNamespace);
      }
    } catch (ApiException apiEx) {
      logger.severe(apiEx.getResponseBody());
    }

    // delete secret in default namespace
    for (V1Secret secret : listSecrets(defaultNamespace).getItems()) {
      if (secret.getMetadata() != null) {
        String secretName = secret.getMetadata().getName();
        if (secretName != null && !secretName.startsWith("default")) {
          logger.info("deleting secret {0}", secretName);
          assertTrue(deleteSecret(secretName, defaultNamespace));
        }
      }
    }
  }

  /**
   * Create two domains on PV using WLST.
   */
  private void createTwoDomainsOnPVUsingWlstAndVerify() {

    for (int i = 0; i < numberOfDomains; i++) {

      if (isUseSecret) {
        // create pull secrets for WebLogic image
        createOCRRepoSecret(domainNamespaces.get(i));
      }

      t3ChannelPort = getNextFreePort(32001, 32700);
      logger.info("t3ChannelPort for domain {0} is {1}", domainUids.get(i), t3ChannelPort);

      String domainUid = domainUids.get(i);
      String domainNamespace = domainNamespaces.get(i);
      String pvName = domainUid + "-pv-" + domainNamespace;
      String pvcName = domainUid + "-pvc";

      // create WebLogic credentials secret
      createSecretWithUsernamePassword(wlSecretName, domainNamespace, ADMIN_USERNAME_DEFAULT, ADMIN_PASSWORD_DEFAULT);

      // create persistent volume and persistent volume claims
      Path pvHostPath = get(PV_ROOT, this.getClass().getSimpleName(), domainUid + "-persistentVolume");

      logger.info("Creating PV directory {0}", pvHostPath);
      assertDoesNotThrow(() -> deleteDirectory(pvHostPath.toFile()), "deleteDirectory failed with IOException");
      assertDoesNotThrow(() -> createDirectories(pvHostPath), "createDirectories failed with IOException");

      V1PersistentVolume v1pv = new V1PersistentVolume()
          .spec(new V1PersistentVolumeSpec()
              .addAccessModesItem("ReadWriteMany")
              .storageClassName(domainUid + "-weblogic-domain-storage-class")
              .volumeMode("Filesystem")
              .putCapacityItem("storage", Quantity.fromString("2Gi"))
              .persistentVolumeReclaimPolicy("Retain")
              .hostPath(new V1HostPathVolumeSource()
                  .path(pvHostPath.toString())))
          .metadata(new V1ObjectMetaBuilder()
              .withName(pvName)
              .build()
              .putLabelsItem("weblogic.domainUid", domainUid));

      V1PersistentVolumeClaim v1pvc = new V1PersistentVolumeClaim()
          .spec(new V1PersistentVolumeClaimSpec()
              .addAccessModesItem("ReadWriteMany")
              .storageClassName(domainUid + "-weblogic-domain-storage-class")
              .volumeName(pvName)
              .resources(new V1ResourceRequirements()
                  .putRequestsItem("storage", Quantity.fromString("2Gi"))))
          .metadata(new V1ObjectMetaBuilder()
              .withName(pvcName)
              .withNamespace(domainNamespace)
              .build()
              .putLabelsItem("weblogic.domainUid", domainUid));

      String labelSelector = String.format("weblogic.domainUid in (%s)", domainUid);
      createPVPVCAndVerify(v1pv, v1pvc, labelSelector, domainNamespace);

      // run create a domain on PV job using WLST
      runCreateDomainOnPVJobUsingWlst(pvName, pvcName, domainUid, domainNamespace,
          "create-domain-scripts-cm", "create-domain-onpv-job");

      // create the domain custom resource configuration object
      logger.info("Creating domain custom resource");
      Domain domain =
          createDomainCustomResource(domainUid, domainNamespace, pvName, pvcName, t3ChannelPort);

      logger.info("Creating domain custom resource {0} in namespace {1}", domainUid, domainNamespace);
      createDomainAndVerify(domain, domainNamespace);

      String adminServerPodName = domainUid + "-" + ADMIN_SERVER_NAME_BASE;
      // check admin server pod is ready and service exists in domain namespace
      checkPodReadyAndServiceExists(adminServerPodName, domainUid, domainNamespace);

      // check for managed server pods are ready and services exist in domain namespace
      for (int j = 1; j <= replicaCount; j++) {
        String managedServerPodName = domainUid + "-" + MANAGED_SERVER_NAME_BASE + j;
        checkPodReadyAndServiceExists(managedServerPodName, domainUid, domainNamespace);
      }

      logger.info("Getting admin service node port");
      int serviceNodePort =
              getServiceNodePort(domainNamespace, adminServerPodName + "-external", "default");

      logger.info("Validating WebLogic admin server access by login to console");
      assertTrue(assertDoesNotThrow(
          () -> adminNodePortAccessible(serviceNodePort, ADMIN_USERNAME_DEFAULT, ADMIN_PASSWORD_DEFAULT),
          "Access to admin server node port failed"), "Console login validation failed");
    }
  }

  /**
   * Run a job to create a WebLogic domain on a persistent volume by doing the following.
   * Copies the WLST domain script to a temp location.
   * Creates a domain properties in the temp location.
   * Creates a configmap containing domain scripts and property files.
   * Runs a job to create domain on persistent volume.
   *
   * @param pvName persistence volume on which the WebLogic domain home will be hosted
   * @param pvcName persistence volume claim for the WebLogic domain
   * @param domainUid the Uid of the domain to create
   * @param domainNamespace the namespace in which the domain will be created
   * @param domainScriptConfigMapName the configMap name for domain script
   * @param createDomainInPVJobName the job name for creating domain in PV
   */
  private void runCreateDomainOnPVJobUsingWlst(String pvName,
                                               String pvcName,
                                               String domainUid,
                                               String domainNamespace,
                                               String domainScriptConfigMapName,
                                               String createDomainInPVJobName) {

    logger.info("Creating a staging location for domain creation scripts");
    Path pvTemp = get(RESULTS_ROOT, this.getClass().getSimpleName(), "domainCreateTempPV");
    assertDoesNotThrow(() -> deleteDirectory(pvTemp.toFile()),"deleteDirectory failed with IOException");
    assertDoesNotThrow(() -> createDirectories(pvTemp), "createDirectories failed with IOException");

    logger.info("Copying the domain creation WLST script to staging location");
    Path srcWlstScript = get(RESOURCE_DIR, "python-scripts", "wlst-create-domain-onpv.py");
    Path targetWlstScript = get(pvTemp.toString(), "create-domain.py");
    assertDoesNotThrow(() -> copy(srcWlstScript, targetWlstScript, StandardCopyOption.REPLACE_EXISTING),
        "copy failed with IOException");

    logger.info("Creating WebLogic domain properties file");
    Path domainPropertiesFile = get(pvTemp.toString(), "domain.properties");
    createDomainProperties(domainPropertiesFile, domainUid);

    logger.info("Adding files to a ConfigMap for domain creation job");
    List<Path> domainScriptFiles = new ArrayList<>();
    domainScriptFiles.add(targetWlstScript);
    domainScriptFiles.add(domainPropertiesFile);

    logger.info("Creating a ConfigMap to hold domain creation scripts");
    createConfigMapFromFiles(domainScriptConfigMapName, domainScriptFiles, domainNamespace);

    logger.info("Running a Kubernetes job to create the domain");
    V1Job jobBody = new V1Job()
        .metadata(
            new V1ObjectMeta()
                .name(createDomainInPVJobName)
                .namespace(domainNamespace))
        .spec(new V1JobSpec()
            .backoffLimit(0) // try only once
            .template(new V1PodTemplateSpec()
                .spec(new V1PodSpec()
                    .restartPolicy("Never")
                    .initContainers(Collections.singletonList(new V1Container()
                        .name("fix-pvc-owner")
                        .image(image)
                        .addCommandItem("/bin/sh")
                        .addArgsItem("-c")
                        .addArgsItem("chown -R 1000:1000 /shared")
                        .volumeMounts(Collections.singletonList(
                            new V1VolumeMount()
                                .name(pvName)
                                .mountPath("/shared")))
                        .securityContext(new V1SecurityContext()
                            .runAsGroup(0L)
                            .runAsUser(0L))))
                    .containers(Collections.singletonList(new V1Container()
                        .name("create-weblogic-domain-onpv-container")
                        .image(image)
                        .ports(Collections.singletonList(new V1ContainerPort()
                            .containerPort(7001)))
                        .volumeMounts(Arrays.asList(
                            new V1VolumeMount()
                                .name("create-weblogic-domain-job-cm-volume") // domain creation scripts volume
                                .mountPath("/u01/weblogic"), // availble under /u01/weblogic inside pod
                            new V1VolumeMount()
                                .name(pvName) // location to write domain
                                .mountPath("/shared"))) // mounted under /shared inside pod
                        .addCommandItem("/bin/sh") //call wlst.sh script with py and properties file
                        .addArgsItem("/u01/oracle/oracle_common/common/bin/wlst.sh")
                        .addArgsItem("/u01/weblogic/create-domain.py")
                        .addArgsItem("-skipWLSModuleScanning")
                        .addArgsItem("-loadProperties")
                        .addArgsItem("/u01/weblogic/domain.properties")))
                    .volumes(Arrays.asList(
                        new V1Volume()
                            .name(pvName)
                            .persistentVolumeClaim(
                                new V1PersistentVolumeClaimVolumeSource()
                                    .claimName(pvcName)),
                        new V1Volume()
                            .name("create-weblogic-domain-job-cm-volume")
                            .configMap(
                                new V1ConfigMapVolumeSource()
                                    .name(domainScriptConfigMapName))))  //ConfigMap containing domain scripts
                    .imagePullSecrets(isUseSecret ? Collections.singletonList(
                        new V1LocalObjectReference()
                            .name(OCR_SECRET_NAME))
                        : null))));

    assertNotNull(jobBody.getMetadata());
    logger.info("Running a job {0} to create a domain on PV for domain {1} in namespace {2}",
        jobBody.getMetadata().getName(), domainUid, domainNamespace);
    createJobAndWaitUntilComplete(jobBody, domainNamespace);
  }

  /**
   * Create a properties file for WebLogic domain configuration.
   * @param wlstPropertiesFile path of the properties file
   * @param domainUid the WebLogic domain for which the properties file is created
   */
  private void createDomainProperties(Path wlstPropertiesFile,
                                      String domainUid) {
    // create a list of properties for the WebLogic domain configuration
    Properties p = new Properties();

    p.setProperty("domain_path", "/shared/domains");
    p.setProperty("domain_name", domainUid);
    p.setProperty("cluster_name", clusterName);
    p.setProperty("admin_server_name", ADMIN_SERVER_NAME_BASE);
    p.setProperty("managed_server_port", "8001");
    p.setProperty("admin_server_port", "7001");
    p.setProperty("admin_username", ADMIN_USERNAME_DEFAULT);
    p.setProperty("admin_password", ADMIN_PASSWORD_DEFAULT);
    p.setProperty("admin_t3_public_address", K8S_NODEPORT_HOST);
    p.setProperty("admin_t3_channel_port", Integer.toString(t3ChannelPort));
    p.setProperty("number_of_ms", "4");
    p.setProperty("managed_server_name_base", MANAGED_SERVER_NAME_BASE);
    p.setProperty("domain_logs", "/shared/logs");
    p.setProperty("production_mode_enabled", "true");

    FileOutputStream fileOutputStream =
        assertDoesNotThrow(() -> new FileOutputStream(wlstPropertiesFile.toFile()),
            "new FileOutputStream failed with FileNotFoundException");
    assertDoesNotThrow(() -> p.store(fileOutputStream, "WLST properties file"),
        "Writing the property list to the specified output stream failed with IOException");
  }

  /**
   * Scale domain1 and verify there is no impact on domain2.
   */
  private void scaleDomain1AndVerifyNoImpactOnDomain2() {

    // scale domain1
    logger.info("Scaling cluster {0} of domain {1} in namespace {2} to {3} servers.",
        clusterName, domain1Uid, domain1Namespace, replicasAfterScale);
    scaleAndVerifyCluster(clusterName, domain1Uid, domain1Namespace,
        domain1Uid + "-" + MANAGED_SERVER_NAME_BASE, replicaCount, replicasAfterScale,
        null, null);

    // add the third managed server pod original creation timestamp to the list
    domain1ManagedServerPodOriginalTimestampList.add(
        getPodCreationTime(domain1Namespace, domain1Uid + "-" + MANAGED_SERVER_NAME_BASE + replicasAfterScale));

    // verify scaling domain1 has no impact on domain2
    logger.info("Checking that domain2 was not changed after domain1 was scaled up");
    verifyDomain2NotChanged(domain2Namespace);
  }

  /**
   * Restart domain1 and verify there was no impact on domain2.
   *
   * @param numServersInDomain1 number of servers in domain1
   * @param domain1Namespace  namespace in which domain1 exists
   * @param domain2Namespace  namespace in which domain2 exists
   */
  private void restartDomain1AndVerifyNoImpactOnDomain2(int numServersInDomain1,
                                                        String domain1Namespace,
                                                        String domain2Namespace) {
    String domain1AdminServerPodName = domainAdminServerPodNames.get(0);

    // shutdown domain1
    logger.info("Shutting down domain1");
    assertTrue(shutdownDomain(domain1Uid, domain1Namespace),
        String.format("shutdown domain %s in namespace %s failed", domain1Uid, domain1Namespace));

    // verify all the server pods in domain1 were shutdown
    logger.info("Checking that admin server pod in domain1 was shutdown");
    checkPodDoesNotExist(domain1AdminServerPodName, domain1Uid, domain1Namespace);

    logger.info("Checking managed server pods in domain1 were shutdown");
    for (int i = 1; i <= numServersInDomain1; i++) {
      String domain1ManagedServerPodName = domain1Uid + "-" + MANAGED_SERVER_NAME_BASE + i;
      checkPodDoesNotExist(domain1ManagedServerPodName, domain1Uid, domain1Namespace);
    }

    // verify domain2 was not changed after domain1 was shut down
    logger.info("Verifying that domain2 was not changed after domain1 was shut down");
    verifyDomain2NotChanged(domain2Namespace);

    // restart domain1
    logger.info("Starting domain1");
    assertTrue(startDomain(domain1Uid, domain1Namespace),
        String.format("start domain %s in namespace %s failed", domain1Uid, domain1Namespace));

    // verify domain1 is restarted
    // check domain1 admin server pod is ready, also check admin service exists in the domain1 namespace
    logger.info("Checking admin server pod in domain1 was started");
    checkPodReadyAndServiceExists(domain1AdminServerPodName, domain1Uid, domain1Namespace);
    checkPodRestarted(domain1Uid, domain1Namespace, domain1AdminServerPodName,
        domainAdminPodOriginalTimestamps.get(0));

    // check managed server pods in domain1
    logger.info("Checking managed server pods in domain1 were started");
    for (int i = 1; i <= numServersInDomain1; i++) {
      String domain1ManagedServerPodName = domain1Uid + "-" + MANAGED_SERVER_NAME_BASE + i;
      checkPodReadyAndServiceExists(domain1ManagedServerPodName, domain1Uid, domain1Namespace);
      checkPodRestarted(domain1Uid, domain1Namespace, domain1ManagedServerPodName,
          domain1ManagedServerPodOriginalTimestampList.get(i - 1));
    }

    // verify domain2 was not changed after domain1 was restarted
    logger.info("Verifying that domain2 was not changed after domain1 was started");
    verifyDomain2NotChanged(domain2Namespace);
  }

  /**
   * Verify domain2 server pods were not changed.
   *
   * @param domain2Namespace namespace in which domain2 exists
   */
  private void verifyDomain2NotChanged(String domain2Namespace) {
    String domain2AdminServerPodName = domainAdminServerPodNames.get(1);

    logger.info("Checking that domain2 admin server pod state was not changed");
    assertThat(podStateNotChanged(domain2AdminServerPodName, domain2Uid, domain2Namespace,
        domainAdminPodOriginalTimestamps.get(1)))
        .as("Test state of pod {0} was not changed in namespace {1}",
            domain2AdminServerPodName, domain2Namespace)
        .withFailMessage("State of pod {0} was changed in namespace {1}",
            domain2AdminServerPodName, domain2Namespace)
        .isTrue();

    logger.info("Checking that domain2 managed server pods states were not changed");
    for (int i = 1; i <= replicaCount; i++) {
      String managedServerPodName = domain2Uid + "-" + MANAGED_SERVER_NAME_BASE + i;
      assertThat(podStateNotChanged(managedServerPodName, domain2Uid, domain2Namespace,
          domain2ManagedServerPodOriginalTimestampList.get(i - 1)))
          .as("Test state of pod {0} was not changed in namespace {1}",
              managedServerPodName, domain2Namespace)
          .withFailMessage("State of pod {0} was changed in namespace {1}",
              managedServerPodName, domain2Namespace)
          .isTrue();
    }
  }

  /**
   * Get domain1 and domain2 server pods original creation timestamps.
   * @param domainUids list of domainUids
   * @param domainNamespaces list of domain namespaces
   */
  private void getBothDomainsPodsOriginalCreationTimestamp(List<String> domainUids,
                                                           List<String> domainNamespaces) {
    domainAdminServerPodNames.clear();
    domainAdminPodOriginalTimestamps.clear();
    domain1ManagedServerPodOriginalTimestampList.clear();
    domain2ManagedServerPodOriginalTimestampList.clear();

    // get the domain1 pods original creation timestamp
    logger.info("Getting admin server pod original creation timestamps for both domains");
    for (int i = 0; i < numberOfDomains; i++) {
      domainAdminServerPodNames.add(domainUids.get(i) + "-" + ADMIN_SERVER_NAME_BASE);
      domainAdminPodOriginalTimestamps.add(
          getPodCreationTime(domainNamespaces.get(i), domainAdminServerPodNames.get(i)));
    }

    // get the managed server pods original creation timestamps
    logger.info("Getting managed server pods original creation timestamps for both domains");
    for (int i = 1; i <= replicaCount; i++) {
      domain1ManagedServerPodOriginalTimestampList.add(
          getPodCreationTime(domainNamespaces.get(0), domainUids.get(0) + "-" + MANAGED_SERVER_NAME_BASE + i));

      domain2ManagedServerPodOriginalTimestampList.add(
          getPodCreationTime(domainNamespaces.get(1), domainUids.get(1) + "-" + MANAGED_SERVER_NAME_BASE + i));
    }
  }

  /**
   * Shutdown domain and verify all the server pods were shutdown.
   *
   * @param domainNamespace the namespace where the domain exists
   * @param domainUid the uid of the domain to shutdown
   */
  private void shutdownDomainAndVerify(String domainNamespace, String domainUid) {

    // shutdown domain
    logger.info("Shutting down domain {0} in namespace {1}", domainUid, domainNamespace);
    shutdownDomain(domainUid, domainNamespace);

    // verify all the pods were shutdown
    logger.info("Verifying all server pods were shutdown for the domain");
    // check admin server pod was shutdown
    checkPodDoesNotExist(domainUid + "-" + ADMIN_SERVER_NAME_BASE,
          domainUid, domainNamespace);

    for (int i = 1; i <= replicaCount; i++) {
      String managedServerPodName = domainUid + "-" + MANAGED_SERVER_NAME_BASE + i;
      checkPodDoesNotExist(managedServerPodName, domainUid, domainNamespace);
    }
  }

  /**
   * Create a domain custom resource object.
   *
   * @param domainUid uid of the domain
   * @param domainNamespace namespace of the domain
   * @param pvName name of persistence volume
   * @param pvcName name of persistence volume claim
   * @param t3ChannelPort t3 channel port for admin server
   * @return oracle.weblogic.domain.Domain object
   */
  private Domain createDomainCustomResource(String domainUid,
                                            String domainNamespace,
                                            String pvName,
                                            String pvcName,
                                            int t3ChannelPort) {
    return new Domain()
        .apiVersion(DOMAIN_API_VERSION)
        .kind("Domain")
        .metadata(new V1ObjectMeta()
            .name(domainUid)
            .namespace(domainNamespace))
        .spec(new DomainSpec()
            .domainUid(domainUid)
            .domainHome("/shared/domains/" + domainUid)
            .domainHomeSourceType("PersistentVolume")
            .image(image)
            .imagePullSecrets(isUseSecret ? Collections.singletonList(
                new V1LocalObjectReference()
                    .name(OCR_SECRET_NAME))
                : null)
            .webLogicCredentialsSecret(new V1SecretReference()
                .name(wlSecretName)
                .namespace(domainNamespace))
            .includeServerOutInPodLog(true)
            .logHomeEnabled(Boolean.TRUE)
            .logHome("/shared/logs/" + domainUid)
            .dataHome("")
            .serverStartPolicy("IF_NEEDED")
            .serverPod(new ServerPod()
                .addEnvItem(new V1EnvVar()
                    .name("JAVA_OPTIONS")
                    .value("-Dweblogic.StdoutDebugEnabled=false "
                        + "-Dweblogic.kernel.debug=true "
                        + "-Dweblogic.debug.DebugMessaging=true "
                        + "-Dweblogic.debug.DebugConnection=true "
                        + "-Dweblogic.ResolveDNSName=true "
                        + "-Dweblogic.MaxMessageSize=20000000"))
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
                        .nodePort(0))
                    .addChannelsItem(new Channel()
                        .channelName("T3Channel")
                        .nodePort(t3ChannelPort))))
            .addClustersItem(new Cluster()
                .clusterName(clusterName)
                .replicas(replicaCount)
                .serverStartState("RUNNING")));
  }

  /**
   * Create two domains on PV using WLST.
   */
  private void createTwoDomainsSharingPVUsingWlstAndVerify() {

    String pvName = "default-sharing-pv";
    String pvcName = "default-sharing-pvc";

    if (isUseSecret) {
      // create pull secrets for WebLogic image
      createOCRRepoSecret(defaultNamespace);
    }

    // create WebLogic credentials secret
    createSecretWithUsernamePassword(wlSecretName, defaultNamespace, ADMIN_USERNAME_DEFAULT, ADMIN_PASSWORD_DEFAULT);

    // create persistent volume
    Path pvHostPath = get(PV_ROOT, this.getClass().getSimpleName(), "default-sharing-persistentVolume");

    logger.info("Creating PV directory {0}", pvHostPath);
    assertDoesNotThrow(() -> deleteDirectory(pvHostPath.toFile()), "deleteDirectory failed with IOException");
    assertDoesNotThrow(() -> createDirectories(pvHostPath), "createDirectories failed with IOException");

    V1PersistentVolume v1pv = new V1PersistentVolume()
        .spec(new V1PersistentVolumeSpec()
            .addAccessModesItem("ReadWriteMany")
            .storageClassName("default-sharing-weblogic-domain-storage-class")
            .volumeMode("Filesystem")
            .putCapacityItem("storage", Quantity.fromString("6Gi"))
            .persistentVolumeReclaimPolicy("Retain")
            .hostPath(new V1HostPathVolumeSource()
                .path(pvHostPath.toString())))
        .metadata(new V1ObjectMetaBuilder()
            .withName(pvName)
            .build()
            .putLabelsItem("sharing-pv", "true"));

    V1PersistentVolumeClaim v1pvc = new V1PersistentVolumeClaim()
        .spec(new V1PersistentVolumeClaimSpec()
            .addAccessModesItem("ReadWriteMany")
            .storageClassName("default-sharing-weblogic-domain-storage-class")
            .volumeName(pvName)
            .resources(new V1ResourceRequirements()
                .putRequestsItem("storage", Quantity.fromString("6Gi"))))
        .metadata(new V1ObjectMetaBuilder()
            .withName(pvcName)
            .withNamespace(defaultNamespace)
            .build()
            .putLabelsItem("sharing-pvc", "true"));

    // create pv and pvc
    String labelSelector = String.format("sharing-pv in (%s)", "true");
    createPVPVCAndVerify(v1pv, v1pvc, labelSelector, defaultNamespace);

    for (int i = 1; i <= numberOfDomains; i++) {
      String domainUid = domainUids.get(i - 1);
      String domainScriptConfigMapName = "create-domain" + i + "-scripts-cm";
      String createDomainInPVJobName = "create-domain" + i + "-onpv-job";

      t3ChannelPort = getNextFreePort(32003 + i, 32700);
      logger.info("t3ChannelPort for domain {0} is {1}", domainUid, t3ChannelPort);

      // run create a domain on PV job using WLST
      runCreateDomainOnPVJobUsingWlst(pvName, pvcName, domainUid, defaultNamespace, domainScriptConfigMapName,
          createDomainInPVJobName);

      // create the domain custom resource configuration object
      logger.info("Creating domain custom resource");
      Domain domain =
          createDomainCustomResource(domainUid, defaultNamespace, pvName, pvcName, t3ChannelPort);

      logger.info("Creating domain custom resource {0} in namespace {1}", domainUid, defaultNamespace);
      createDomainAndVerify(domain, defaultNamespace);

      String adminServerPodName = domainUid + "-" + ADMIN_SERVER_NAME_BASE;
      // check admin server pod is ready and service exists in domain namespace
      checkPodReadyAndServiceExists(adminServerPodName, domainUid, defaultNamespace);

      // check for managed server pods are ready and services exist in domain namespace
      for (int j = 1; j <= replicaCount; j++) {
        String managedServerPodName = domainUid + "-" + MANAGED_SERVER_NAME_BASE + j;
        checkPodReadyAndServiceExists(managedServerPodName, domainUid, defaultNamespace);
      }

      logger.info("Getting admin service node port");
      int serviceNodePort =
          getServiceNodePort(defaultNamespace, adminServerPodName + "-external", "default");

      logger.info("Validating WebLogic admin server access by login to console");
      assertTrue(assertDoesNotThrow(
          () -> adminNodePortAccessible(serviceNodePort, ADMIN_USERNAME_DEFAULT, ADMIN_PASSWORD_DEFAULT),
          "Access to admin server node port failed"), "Console login validation failed");
    }
  }

  /**
   * Scale domain2 and verify there is no impact on domain1.
   */
  private void scaleDomain2AndVerifyNoImpactOnDomain1() {

    // scale domain2 from 2 servers to 3 servers
    replicasAfterScale = 3;
    logger.info("Scaling cluster {0} of domain {1} in namespace {2} to {3} servers.",
        clusterName, domain2Uid, defaultNamespace, replicasAfterScale);
    scaleAndVerifyCluster(clusterName, domain2Uid, defaultNamespace,
        domain2Uid + "-" + MANAGED_SERVER_NAME_BASE, replicaCount, replicasAfterScale,
        null, null);

    // scale domain2 from 3 servers to 2 servers
    logger.info("Scaling cluster {0} of domain {1} in namespace {2} to {3} servers.",
        clusterName, domain2Uid, defaultNamespace, replicaCount);
    scaleAndVerifyCluster(clusterName, domain2Uid, defaultNamespace,
        domain2Uid + "-" + MANAGED_SERVER_NAME_BASE, replicasAfterScale, replicaCount,
        null, null);

    // verify scaling domain2 has no impact on domain1
    logger.info("Checking that domain1 was not changed after domain2 was scaled up");
    verifyDomain1NotChanged();
  }

  /**
   * Verify domain1 server pods were not changed.
   */
  private void verifyDomain1NotChanged() {
    String domain1AdminServerPodName = domainAdminServerPodNames.get(0);

    logger.info("Checking that domain1 admin server pod state was not changed");
    assertThat(podStateNotChanged(domain1AdminServerPodName, domain1Uid, defaultNamespace,
        domainAdminPodOriginalTimestamps.get(0)))
        .as("Test state of pod {0} was not changed in namespace {1}",
            domain1AdminServerPodName, defaultNamespace)
        .withFailMessage("State of pod {0} was changed in namespace {1}",
            domain1AdminServerPodName, defaultNamespace)
        .isTrue();

    logger.info("Checking that domain1 managed server pods states were not changed");
    for (int i = 1; i <= replicaCount; i++) {
      String managedServerPodName = domain1Uid + "-" + MANAGED_SERVER_NAME_BASE + i;
      assertThat(podStateNotChanged(managedServerPodName, domain1Uid, defaultNamespace,
          domain1ManagedServerPodOriginalTimestampList.get(i - 1)))
          .as("Test state of pod {0} was not changed in namespace {1}",
              managedServerPodName, defaultNamespace)
          .withFailMessage("State of pod {0} was changed in namespace {1}",
              managedServerPodName, defaultNamespace)
          .isTrue();
    }
  }

  private static void createCertKeyFiles(String domainUid) {
    String cn = domainUid + "." + defaultNamespace + ".cluster-1.test";
    assertDoesNotThrow(() -> {
      tlsKeyFile = Files.createTempFile("tls", ".key");
      tlsCertFile = Files.createTempFile("tls", ".crt");
      ExecCommand.exec("openssl"
              + " req -x509 "
              + " -nodes "
              + " -days 365 "
              + " -newkey rsa:2048 "
              + " -keyout " + tlsKeyFile
              + " -out " + tlsCertFile
              + " -subj /CN=" + cn,
          true);
    });
  }

  private static void createTraefikIngressRoutingRules() {
    logger.info("Creating ingress rules for domain traffic routing");
    Path srcFile = Paths.get(ActionConstants.RESOURCE_DIR, "traefik/traefik-ingress-rules.yaml");
    dstFile = Paths.get(TestConstants.RESULTS_ROOT, "traefik/traefik-ingress-rules.yaml");
    assertDoesNotThrow(() -> {
      Files.deleteIfExists(dstFile);
      Files.createDirectories(dstFile.getParent());
      Files.write(dstFile, Files.readString(srcFile).replaceAll("@NS@", defaultNamespace)
          .replaceAll("@domain1uid@", domainUids.get(0))
          .replaceAll("@domain2uid@", domainUids.get(1))
          .getBytes(StandardCharsets.UTF_8));
    });
    String command = "kubectl create"
        + " -f " + dstFile;
    logger.info("Running {0}", command);
    ExecResult result;
    try {
      result = ExecCommand.exec(command, true);
      String response = result.stdout().trim();
      logger.info("exitCode: {0}, \nstdout: {1}, \nstderr: {2}",
          result.exitValue(), response, result.stderr());
      assertEquals(0, result.exitValue(), "Command didn't succeed");
    } catch (IOException | InterruptedException ex) {
      logger.severe(ex.getMessage());
    }
  }

  private static void createVoyagerIngressRoutingRules() {
    for (String domainUid : domainUids) {
      String ingressName = domainUid + "-ingress-host-routing";

      // create Voyager ingress resource
      Map<String, Integer> clusterNameMsPortMap = new HashMap<>();
      clusterNameMsPortMap.put("cluster-1", 8001);
      installVoyagerIngressAndVerify(domainUid, defaultNamespace, ingressName, clusterNameMsPortMap);
    }
  }

  private static int getVoyagerLbNodePort(String ingressName) {
    String ingressServiceName = VOYAGER_CHART_NAME + "-" + ingressName;
    String channelName = "tcp-80";

    // get ingress service Nodeport
    int ingressServiceNodePort = assertDoesNotThrow(() ->
            getServiceNodePort(defaultNamespace, ingressServiceName, channelName),
        "Getting voyager loadbalancer service node port failed");
    logger.info("Node port for {0} is: {1} :", ingressServiceName, ingressServiceNodePort);
    return ingressServiceNodePort;
  }

  private static int getApacheNodePort(String namespace, String channelName) {
    String apacheServiceName = APACHE_RELEASE_NAME + "-" + namespace.substring(3) + "-apache-webtier";

    // get Apache service NodePort
    int apacheNodePort = assertDoesNotThrow(() ->
            getServiceNodePort(namespace, apacheServiceName, channelName),
        "Getting Apache service NodePort failed");
    logger.info("NodePort for {0} is: {1} :", apacheServiceName, apacheNodePort);
    return apacheNodePort;
  }

  private static void deployApplication(String namespace, String domainUid, String adminServerPodName) {
    logger.info("Getting node port for admin server default channel");
    int serviceNodePort = assertDoesNotThrow(() ->
            getServiceNodePort(namespace, adminServerPodName + "-external", "default"),
        "Getting admin server node port failed");

    logger.info("Deploying application {0} to domain {1} cluster target cluster-1 in namespace {2}",
        clusterViewAppPath, domainUid, namespace);
    ExecResult result = DeployUtil.deployUsingRest(K8S_NODEPORT_HOST,
        String.valueOf(serviceNodePort),
        ADMIN_USERNAME_DEFAULT, ADMIN_PASSWORD_DEFAULT,
        "cluster-1", clusterViewAppPath, null, domainUid + "clusterview");
    assertNotNull(result, "Application deployment failed");
    logger.info("Application deployment returned {0}", result.toString());
    assertEquals("202", result.stdout(), "Deployment didn't return HTTP status code 202");
  }

  private void verifyAdminServerAccess(String domainUid) {
    String consoleUrl = new StringBuffer()
        .append("http://")
        .append(K8S_NODEPORT_HOST)
        .append(":")
        .append(getTraefikLbNodePort(false))
        .append("/console/login/LoginForm.jsp").toString();

    //access application in managed servers through Traefik load balancer and bind domain in the JNDI tree
    String curlCmd = String.format("curl --silent --show-error --noproxy '*' -H 'host: %s' %s",
        domainUid + "." + defaultNamespace + "." + "admin-server" + ".test", consoleUrl);

    boolean hostRouting = false;
    for (int i = 0; i < 10; i++) {
      assertDoesNotThrow(() -> TimeUnit.SECONDS.sleep(1));
      ExecResult result;
      try {
        logger.info("Accessing console using curl request iteration{0} {1}", i, curlCmd);
        result = ExecCommand.exec(curlCmd, true);
        String response = result.stdout().trim();
        logger.info("exitCode: {0}, \nstdout: {1}, \nstderr: {2}",
            result.exitValue(), response, result.stderr());
        if (response.contains("login")) {
          hostRouting = true;
          break;
        }
        //assertTrue(result.stdout().contains("Login"), "Couldn't access admin server console");
      } catch (IOException | InterruptedException ex) {
        logger.severe(ex.getMessage());
      }
    }
    assertTrue(hostRouting, "Couldn't access admin server console");
  }

  private int getTraefikLbNodePort(boolean isHttps) {
    logger.info("Getting web node port for Traefik loadbalancer {0}", traefikHelmParams.getReleaseName());
    return assertDoesNotThrow(() ->
            getServiceNodePort(traefikNamespace, traefikHelmParams.getReleaseName(), isHttps ? "websecure" : "web"),
        "Getting web node port for Traefik loadbalancer failed");
  }

  /**
   * Verify cluster load balancing with ClusterViewServlet app.
   *
   * @param domainUid uid of the domain in which the cluster exists
   * @param namespace namespace in which the domain exists
   * @param protocol protocol used to test, accepted value: http or https
   * @param lbPort  load balancer service port
   * @param replicaCount replica count of the managed servers in the cluster
   * @param hostRouting whether it is a host base routing
   * @param apacheLocationString location string in apache configuration
   */
  private void verifyClusterLoadbalancing(String domainUid,
                                          String namespace,
                                          String protocol,
                                          int lbPort,
                                          int replicaCount,
                                          boolean hostRouting,
                                          String apacheLocationString) {

    // access application in managed servers through load balancer
    logger.info("Accessing the clusterview app through load balancer to verify all servers in cluster");
    String curlRequest;
    if (hostRouting) {
      curlRequest = String.format("curl --silent --show-error -ks --noproxy '*' "
              + "-H 'host: %s' %s://%s:%s/clusterview/ClusterViewServlet"
              + "\"?user=" + ADMIN_USERNAME_DEFAULT
              + "&password=" + ADMIN_PASSWORD_DEFAULT + "\"",
          domainUid + "." + namespace + "." + "cluster-1.test", protocol, K8S_NODEPORT_HOST, lbPort);
    } else {
      curlRequest = String.format("curl --silent --show-error -ks --noproxy '*' "
              + "%s://%s:%s" + apacheLocationString + "/clusterview/ClusterViewServlet"
              + "\"?user=" + ADMIN_USERNAME_DEFAULT
              + "&password=" + ADMIN_PASSWORD_DEFAULT + "\"",
          protocol, K8S_NODEPORT_HOST, lbPort);
    }

    List<String> managedServers = new ArrayList<>();
    for (int i = 1; i <= replicaCount; i++) {
      managedServers.add(MANAGED_SERVER_NAME_BASE + i);
    }

    // verify each managed server can see other member in the cluster
    verifyServerCommunication(curlRequest, managedServers);

    boolean containsCorrectDomainUid = false;
    logger.info("Verifying the requests are routed to correct domain and cluster");
    for (int i = 0; i < 10; i++) {
      ExecResult result;
      try {
        logger.info("executing curl command: {0}", curlRequest);
        result = ExecCommand.exec(curlRequest, true);
        String response = result.stdout().trim();
        logger.info("Response for iteration {0}: exitValue {1}, stdout {2}, stderr {3}",
            i, result.exitValue(), response, result.stderr());
        if (response.contains(domainUid)) {
          containsCorrectDomainUid = true;
          break;
        }
      } catch (IOException | InterruptedException ex) {
        // ignore
      }
    }
    assertTrue(containsCorrectDomainUid, "The request was not routed to the correct domain");
  }

  /**
   * Verify admin node port(default/t3channel) is accessible by login to WebLogic console
   * using the node port and validate its the Home page.
   *
   * @param nodePort the node port that needs to be tested for access
   * @param userName WebLogic administration server user name
   * @param password WebLogic administration server password
   * @return true if login to WebLogic administration console is successful
   * @throws IOException when connection to console fails
   */
  private static boolean adminNodePortAccessible(int nodePort, String userName, String password)
      throws IOException {

    String consoleUrl = new StringBuffer()
        .append("http://")
        .append(K8S_NODEPORT_HOST)
        .append(":")
        .append(nodePort)
        .append("/console/login/LoginForm.jsp").toString();

    boolean adminAccessible = false;
    for (int i = 1; i <= 10; i++) {
      getLogger().info("Iteration {0} out of 10: Accessing WebLogic console with url {1}", i, consoleUrl);
      final WebClient webClient = new WebClient();
      final HtmlPage loginPage = assertDoesNotThrow(() -> webClient.getPage(consoleUrl),
          "connection to the WebLogic admin console failed");
      HtmlForm form = loginPage.getFormByName("loginData");
      form.getInputByName("j_username").type(userName);
      form.getInputByName("j_password").type(password);
      HtmlElement submit = form.getOneHtmlElementByAttribute("input", "type", "submit");
      getLogger().info("Clicking login button");
      HtmlPage home = submit.click();
      if (home.asText().contains("Persistent Stores")) {
        getLogger().info("Console login passed");
        adminAccessible = true;
        break;
      }
    }

    return adminAccessible;
  }

  private static Callable<Boolean> pullImageFromOcirAndPushToKind(String apacheImage) {
    return (() -> {
      kindRepoApacheImage = KIND_REPO + apacheImage.substring(REPO_DEFAULT.length());
      logger.info("pulling image {0} from OCIR, tag it as image {1} and push to KIND repo",
          apacheImage, kindRepoApacheImage);
      return dockerPull(apacheImage) && dockerTag(apacheImage, kindRepoApacheImage) && dockerPush(kindRepoApacheImage);
    });
  }
}

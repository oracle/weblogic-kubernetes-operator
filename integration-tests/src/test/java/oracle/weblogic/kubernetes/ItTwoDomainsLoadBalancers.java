// Copyright (c) 2020, 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

import com.gargoylesoftware.htmlunit.WebClient;
import com.gargoylesoftware.htmlunit.html.HtmlElement;
import com.gargoylesoftware.htmlunit.html.HtmlForm;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import io.kubernetes.client.custom.IntOrString;
import io.kubernetes.client.custom.Quantity;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.models.NetworkingV1beta1HTTPIngressPath;
import io.kubernetes.client.openapi.models.NetworkingV1beta1HTTPIngressRuleValue;
import io.kubernetes.client.openapi.models.NetworkingV1beta1IngressBackend;
import io.kubernetes.client.openapi.models.NetworkingV1beta1IngressRule;
import io.kubernetes.client.openapi.models.NetworkingV1beta1IngressTLS;
import io.kubernetes.client.openapi.models.V1ConfigMapVolumeSource;
import io.kubernetes.client.openapi.models.V1Container;
import io.kubernetes.client.openapi.models.V1ContainerPort;
import io.kubernetes.client.openapi.models.V1EnvVar;
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
import static oracle.weblogic.kubernetes.TestConstants.BASE_IMAGES_REPO_SECRET;
import static oracle.weblogic.kubernetes.TestConstants.DOMAIN_API_VERSION;
import static oracle.weblogic.kubernetes.TestConstants.DOMAIN_VERSION;
import static oracle.weblogic.kubernetes.TestConstants.K8S_NODEPORT_HOST;
import static oracle.weblogic.kubernetes.TestConstants.KIND_REPO;
import static oracle.weblogic.kubernetes.TestConstants.MANAGED_SERVER_NAME_BASE;
import static oracle.weblogic.kubernetes.TestConstants.OCIR_PASSWORD;
import static oracle.weblogic.kubernetes.TestConstants.OCIR_REGISTRY;
import static oracle.weblogic.kubernetes.TestConstants.OCIR_USERNAME;
import static oracle.weblogic.kubernetes.TestConstants.PV_ROOT;
import static oracle.weblogic.kubernetes.TestConstants.RESULTS_ROOT;
import static oracle.weblogic.kubernetes.TestConstants.VOYAGER_CHART_NAME;
import static oracle.weblogic.kubernetes.TestConstants.WEBLOGIC_IMAGE_TO_USE_IN_SPEC;
import static oracle.weblogic.kubernetes.TestConstants.WEBLOGIC_SLIM;
import static oracle.weblogic.kubernetes.actions.ActionConstants.APP_DIR;
import static oracle.weblogic.kubernetes.actions.ActionConstants.RESOURCE_DIR;
import static oracle.weblogic.kubernetes.actions.TestActions.createIngress;
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
import static oracle.weblogic.kubernetes.actions.TestActions.getPodLog;
import static oracle.weblogic.kubernetes.actions.TestActions.getServiceNodePort;
import static oracle.weblogic.kubernetes.actions.TestActions.listIngresses;
import static oracle.weblogic.kubernetes.actions.TestActions.listJobs;
import static oracle.weblogic.kubernetes.actions.TestActions.listPods;
import static oracle.weblogic.kubernetes.actions.TestActions.listSecrets;
import static oracle.weblogic.kubernetes.actions.TestActions.shutdownDomain;
import static oracle.weblogic.kubernetes.actions.TestActions.startDomain;
import static oracle.weblogic.kubernetes.actions.TestActions.uninstallApache;
import static oracle.weblogic.kubernetes.actions.TestActions.uninstallNginx;
import static oracle.weblogic.kubernetes.actions.TestActions.uninstallOperator;
import static oracle.weblogic.kubernetes.actions.TestActions.uninstallTraefik;
import static oracle.weblogic.kubernetes.actions.TestActions.uninstallVoyager;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.domainDoesNotExist;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.isVoyagerReady;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.podStateNotChanged;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.checkPodDoesNotExist;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.checkPodReadyAndServiceExists;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.checkPodRestarted;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.createConfigMapFromFiles;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.createDomainAndVerify;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.createJobAndWaitUntilComplete;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.createPVPVCAndVerify;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.createSecretForBaseImages;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.createSecretWithTLSCertKey;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.createSecretWithUsernamePassword;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.createfixPVCOwnerContainer;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.getExternalServicePodName;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.getPodCreationTime;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.installAndVerifyApache;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.installAndVerifyNginx;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.installAndVerifyOperator;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.installAndVerifyTraefik;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.installAndVerifyVoyager;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.scaleAndVerifyCluster;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.setPodAntiAffinity;
import static oracle.weblogic.kubernetes.utils.TestUtils.callWebAppAndWaitTillReady;
import static oracle.weblogic.kubernetes.utils.TestUtils.getNextFreePort;
import static oracle.weblogic.kubernetes.utils.TestUtils.verifyServerCommunication;
import static oracle.weblogic.kubernetes.utils.ThreadSafeLogger.getLogger;
import static org.apache.commons.io.FileUtils.deleteDirectory;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.with;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

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
  private static final String defaultSharingPvcName = "default-sharing-pvc";
  private static final String defaultSharingPvName = "default-sharing-pv";
  private static final String apachePvcName = "apache-custom-file-pvc";
  private static final String apachePvName = "apache-custom-file-pv";

  private static String defaultNamespace = "default";
  private static String domain1Uid = null;
  private static String domain2Uid = null;
  private static String domain1Namespace = null;
  private static String domain2Namespace = null;
  private static String traefikNamespace = null;
  private static String voyagerNamespace = null;
  private static String nginxNamespace = null;
  private static List<String> opNamespaces = new ArrayList<>();
  private static List<String> domainNamespaces = new ArrayList<>();
  private static List<String> domainUids = new ArrayList<>();
  private static HelmParams operatorHelmParams = null;
  private static HelmParams traefikHelmParams = null;
  private static HelmParams voyagerHelmParams = null;
  private static HelmParams nginxHelmParams = null;
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
  private static final int MANAGED_SERVER_PORT = 8001;
  private static final int ADMIN_SERVER_PORT = 7001;

  private int t3ChannelPort = 0;
  private int replicasAfterScale;
  private List<String> domainAdminServerPodNames = new ArrayList<>();
  private List<OffsetDateTime> domainAdminPodOriginalTimestamps = new ArrayList<>();
  private List<OffsetDateTime> domain1ManagedServerPodOriginalTimestampList = new ArrayList<>();
  private List<OffsetDateTime> domain2ManagedServerPodOriginalTimestampList = new ArrayList<>();

  private static ConditionFactory withStandardRetryPolicy =
      with().pollDelay(2, SECONDS)
          .and().with().pollInterval(10, SECONDS)
          .atMost(5, MINUTES).await();

  /**
   * Get namespaces, install operator and initiate domain UID list.
   * @param namespaces injected by JUnit
   */
  @BeforeAll
  public static void initAll(@Namespaces(7) List<String> namespaces) {
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

    // get a unique Nginx namespace
    logger.info("Assign a unique namespace for Nginx");
    nginxNamespace = namespaces.get(6);

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

    if (KIND_REPO != null) {
      // The kind clusters can't pull Apache webtier image from OCIR using the image pull secret.
      // Try the following instead:
      //   1. docker login
      //   2. docker pull
      //   3. docker tag with the KIND_REPO value
      //   4. docker push to KIND_REPO
      withStandardRetryPolicy
          .conditionEvaluationListener(
              condition -> logger.info("Waiting for docker login to be successful"
                      + "(elapsed time {0} ms, remaining time {1} ms)",
                  condition.getElapsedTimeInMS(),
                  condition.getRemainingTimeInMS()))
          .until(() -> dockerLogin(OCIR_REGISTRY, OCIR_USERNAME, OCIR_PASSWORD));

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
   * Create domain domain1 and domain2 with a dynamic cluster in each domain in default namespace, managed by operator1.
   * Both domains share one PV.
   * Verify scaling for domain2 cluster from 2 to 3 servers and back to 2, plus verify no impact on domain1.
   * Shut down and restart domain1, and make sure there is no impact on domain2.
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
   * Test deploy applications and install ingress controllers Traefik, NGINX, Apache and Voyager.
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

    // install and verify Nginx
    nginxHelmParams = installAndVerifyNginx(nginxNamespace, 0, 0);

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

    // create TLS secret for Traefik HTTPS traffic
    for (String domainUid : domainUids) {
      createCertKeyFiles(domainUid + "." + defaultNamespace + ".cluster-1.test");
      assertDoesNotThrow(() -> createSecretWithTLSCertKey(domainUid + "-traefik-tls-secret",
          defaultNamespace, tlsKeyFile, tlsCertFile));
    }

    // create ingress rules with non-tls host routing, tls host routing and path routing for Traefik
    createTraefikIngressRoutingRules();

    // create ingress rules with non-tls host routing for Voyager and NGINX
    createVoyagerIngressHostRoutingRules(false);
    createNginxIngressHostRoutingForTwoDomains(false);

    // create ingress rules with tls host routing for Voyager and NGINX
    createVoyagerIngressHostRoutingRules(true);
    createNginxIngressHostRoutingForTwoDomains(true);

    // create ingress rules with path routing for Voyager and NGINX
    createVoyagerIngressPathRoutingRules();
    createNginxIngressPathRoutingForTwoDomains();

    // create ingress rules with TLS path routing for Voyager and NGINX
    createVoyagerIngressTLSPathRoutingRules();
    createNginxTLSPathRoutingForTwoDomains();

    // install and verify Apache for default sample
    apacheHelmParams1 = assertDoesNotThrow(
        () -> installAndVerifyApache(domain1Namespace, kindRepoApacheImage, 0, 0, domain1Uid));

    // install and verify Apache for custom sample
    LinkedHashMap<String, String> clusterNamePortMap = new LinkedHashMap<>();
    for (int i = 0; i < numberOfDomains; i++) {
      clusterNamePortMap.put(domainUids.get(i) + "-cluster-cluster-1", "" + MANAGED_SERVER_PORT);
    }
    createPVPVCForApacheCustomConfiguration(defaultNamespace);
    apacheHelmParams2 = assertDoesNotThrow(
        () -> installAndVerifyApache(defaultNamespace, kindRepoApacheImage, 0, 0, domain1Uid,
            apachePvcName, "apache-sample-host", ADMIN_SERVER_PORT, clusterNamePortMap));
  }

  /**
   * Verify WebLogic admin console is accessible through NGINX path routing with HTTPS protocol.
   */
  @Order(4)
  @Test
  @DisplayName("Verify WebLogic admin console is accessible through NGINX path routing with HTTPS protocol")
  public void testNginxTLSPathRoutingAdminServer() {
    assumeFalse(WEBLOGIC_SLIM, "Skipping the Console Test for slim image");
    logger.info("Verifying WebLogic admin console is accessible through NGINX path routing with HTTPS protocol");
    for (int i = 0; i < numberOfDomains; i++) {
      verifyAdminServerAccess(true, getNginxLbNodePort("https"), false, "",
          "/" + domainUids.get(i).substring(6) + "console");


      // verify the header 'WL-Proxy-Client-IP' is removed in the admin server log
      // verify the header 'WL-Proxy-SSL: false' is removed in the admin server log
      // verify the header 'WL-Proxy-SSL: true' is added in the admin server log
      verifyHeadersInAdminServerLog(domainAdminServerPodNames.get(i), defaultNamespace);
    }
  }

  /**
   * Test verifies multiple WebLogic domains can be loadbalanced by NGINX loadbalancer with TLS path routing rules.
   * Accesses the clusterview application deployed in the WebLogic cluster through NGINX loadbalancer and verifies it
   * is correctly routed to the specific domain cluster.
   */
  @Order(5)
  @Test
  @DisplayName("Verify NGINX path routing with HTTPS protocol across two domains")
  public void testNginxTLSPathRoutingAcrossDomains() {

    // verify NGINX path routing with HTTP protocol across two domains
    logger.info("Verifying NGINX path routing with HTTPS protocol across two domains");
    for (String domainUid : domainUids) {
      verifyClusterLoadbalancing(domainUid, "", "https", getNginxLbNodePort("https"),
          replicaCount, false, "/" + domainUid.substring(6));
    }
  }

  /**
   * Verify WebLogic admin console is accessible through Voyager path routing with HTTPS protocol.
   */
  @Order(6)
  @Test
  @DisplayName("Verify WebLogic admin console is accessible through Voyager path routing with HTTPS protocol")
  public void testVoyagerTLSPathRoutingAdminServer() {
    assumeFalse(WEBLOGIC_SLIM, "Skipping the Console Test for slim image");
    logger.info("Verifying WebLogic admin console is accessible through Voyager path routing with HTTPS protocol");
    String ingressName = "voyager-tls-pathrouting";
    for (int i = 0; i < numberOfDomains; i++) {
      verifyAdminServerAccess(true, getVoyagerLbNodePort(ingressName, "tcp-443"), false, "",
          "/" + domainUids.get(i).substring(6) + "console");

      // verify the header 'WL-Proxy-Client-IP' is removed in the admin server log
      // verify the header 'WL-Proxy-SSL: false' is removed in the admin server log
      // verify the header 'WL-Proxy-SSL: true' is added in the admin server log
      verifyHeadersInAdminServerLog(domainAdminServerPodNames.get(i), defaultNamespace);
    }
  }

  /**
   * Verify WebLogic admin console is accessible through Traefik host routing with HTTP protocol.
   */
  @Order(7)
  @Test
  @DisplayName("Verify WebLogic admin console is accessible through Traefik host routing with HTTP protocol")
  public void testTraefikHostRoutingAdminServer() {
    assumeFalse(WEBLOGIC_SLIM, "Skipping the Console Test for slim image");
    logger.info("Verifying WebLogic admin console is accessible through Traefik host routing with HTTP protocol");
    for (String domainUid : domainUids) {
      verifyAdminServerAccess(false, getTraefikLbNodePort(false), true,
          domainUid + "." + defaultNamespace + "." + "admin-server" + ".test", "");
    }
  }

  /**
   * Verify multiple WebLogic domains can be loadbalanced by Traefik loadbalancer with host based routing rules.
   * Accesses the clusterview application deployed in the WebLogic cluster through Traefik loadbalancer web
   * channel and verifies it is correctly routed to the specific domain cluster identified by the -H host header.
   *
   */
  @Order(8)
  @Test
  @DisplayName("Verify Traefik host routing with HTTP protocol across two domains")
  public void testTraefikHttpHostRoutingAcrossDomains() {

    // verify Traefik host routing with HTTP protocol across two domains
    logger.info("Verifying Traefik host routing with HTTP protocol across two domains");
    for (String domainUid : domainUids) {
      verifyClusterLoadbalancing(domainUid, domainUid + "." + defaultNamespace + ".cluster-1.test",
          "http", getTraefikLbNodePort(false), replicaCount, true, "");
    }
  }

  /**
   * Verify multiple WebLogic domains can be loadbalanced by Traefik loadbalancer with host based routing rules.
   * Accesses the clusterview application deployed in the WebLogic cluster through Traefik loadbalancer websecure
   * channel and verifies it is correctly routed to the specific domain cluster identified by the -H host header.
   */
  @Order(9)
  @Test
  @DisplayName("Verify Traefik host routing with HTTPS protocol across two domains")
  public void testTraefikHostHttpsRoutingAcrossDomains() {

    logger.info("Verifying Traefik host routing with HTTPS protocol across two domains");
    for (String domainUid : domainUids) {
      verifyClusterLoadbalancing(domainUid, domainUid + "." + defaultNamespace + ".cluster-1.test",
          "https", getTraefikLbNodePort(true), replicaCount, true, "");
    }
  }

  /**
   * Verify Traefik path routing with HTTP protocol across two domains.
   */
  @Order(10)
  @Test
  @DisplayName("Verify Traefik path routing with HTTP protocol across two domains")
  public void testTraefikPathRoutingAcrossDomains() {

    logger.info("Verifying Traefik path routing with HTTP protocol across two domains");
    for (String domainUid : domainUids) {
      verifyClusterLoadbalancing(domainUid, "", "http", getTraefikLbNodePort(false),
          replicaCount, false, "/" + domainUid.substring(6));
    }
  }

  /**
   * Verify multiple WebLogic domains can be loadbalanced by Voyager loadbalancer with host based routing rules.
   * Accesses the clusterview application deployed in the WebLogic cluster through Voyager loadbalancer and verifies it
   * is correctly routed to the specific domain cluster identified by the -H host header.
   */
  @Order(11)
  @Test
  @DisplayName("Verify Voyager host routing with HTTP protocol across two domains")
  public void testVoyagerHostHttpRoutingAcrossDomains() {

    // verify Voyager host routing with HTTP protocol across two domains
    logger.info("Verifying Voyager host routing with HTTP protocol across two domains");
    for (String domainUid : domainUids) {
      String ingressName = domainUid + "-voyager-host-routing";
      verifyClusterLoadbalancing(domainUid, domainUid + "." + defaultNamespace + ".voyager.nonssl.test",
          "http", getVoyagerLbNodePort(ingressName, "tcp-80"),
          replicaCount, true, "");
    }
  }

  /**
   * Verify multiple WebLogic domains can be loadbalanced by Voyager loadbalancer with tls based routing rules.
   * Accesses the clusterview application deployed in the WebLogic cluster through Voyager loadbalancer and verifies it
   * is correctly routed to the specific domain cluster identified by the -H host header.
   */
  @Order(12)
  @Test
  @DisplayName("Verify Voyager host routing with HTTPS protocol across two domains")
  public void testVoyagerHostHttpsRoutingAcrossDomains() {

    // verify Voyager host routing with HTTPS protocol across two domains
    logger.info("Verifying Voyager host routing with HTTPS protocol across two domains");
    for (String domainUid : domainUids) {
      String ingressName = domainUid + "-voyager-tls";
      verifyClusterLoadbalancing(domainUid, domainUid + "." + defaultNamespace + ".voyager.ssl.test",
          "https", getVoyagerLbNodePort(ingressName, "tcp-443"),
          replicaCount, true, "");
    }
  }

  /**
   * Verify multiple WebLogic domains can be loadbalanced by Voyager loadbalancer with path routing rules.
   * Accesses the clusterview application deployed in the WebLogic cluster through Voyager loadbalancer and verifies it
   * is correctly routed to the specific domain cluster.
   */
  @Order(13)
  @Test
  @DisplayName("Verify Voyager path routing with HTTP protocol across two domains")
  public void testVoyagerPathRoutingAcrossDomains() {

    // verify Voyager path routing with HTTP protocol across two domains
    logger.info("Verifying Voyager path routing with HTTP protocol across two domains");
    for (String domainUid : domainUids) {
      String ingressName = "voyager-path-routing";
      verifyClusterLoadbalancing(domainUid, "", "http", getVoyagerLbNodePort(ingressName, "tcp-80"),
          replicaCount, false, "/" + domainUid.substring(6));
    }
  }

  /**
   * Verify Apache load balancer default sample through HTTP channel.
   * Configure the Apache webtier as a load balancer for a WebLogic domain using the default configuration.
   * It only support HTTP protocol.
   * For details, please see
   * https://github.com/oracle/weblogic-kubernetes-operator/tree/master/kubernetes/samples/charts/apache-samples/default-sample
   */
  @Order(14)
  @Test
  @DisplayName("verify Apache load balancer default sample through HTTP channel")
  public void testApacheLoadBalancingDefaultSample() {

    // verify Apache default sample
    logger.info("Verifying Apache default sample");
    int httpNodePort = getApacheNodePort(domain1Namespace, "http");
    verifyClusterLoadbalancing(domain1Uid, "", "http", httpNodePort, 3, false, "/weblogic");
  }

  /**
   * Verify Apache load balancer custom sample through HTTP and HTTPS channel.
   * Configure the Apache webtier as a load balancer for multiple WebLogic domains using a custom configuration.
   * Create a custom Apache plugin configuration file named custom_mod_wl_apache.conf in a directory specified
   * in helm chart parameter volumePath.
   * For more details, please check:
   * https://github.com/oracle/weblogic-kubernetes-operator/tree/master/kubernetes/samples/charts/apache-samples/custom-sample
   */
  @Order(15)
  @Test
  @DisplayName("verify Apache load balancer custom sample through HTTP and HTTPS channel")
  public void testApacheLoadBalancingCustomSample() {

    // verify Apache custom sample
    logger.info("Verifying Apache custom sample");
    for (int i = 1; i <= numberOfDomains; i++) {
      int httpNodePort = getApacheNodePort(defaultNamespace, "http");
      verifyClusterLoadbalancing(domainUids.get(i - 1), "", "http", httpNodePort, replicaCount,
          false, "/weblogic" + i);

      int httpsNodePort = getApacheNodePort(defaultNamespace, "https");
      verifyClusterLoadbalancing(domainUids.get(i - 1), "", "https", httpsNodePort, replicaCount,
          false, "/weblogic" + i);
    }
  }

  /**
   * Verify multiple WebLogic domains can be loadbalanced by NGINX loadbalancer with host based routing rules.
   * Accesses the clusterview application deployed in the WebLogic cluster through NGINX host routing with HTTP protocol
   * and verifies it is correctly routed to the specific domain cluster identified by the -H host header.
   *
   */
  @Order(16)
  @Test
  @DisplayName("verify NGINX host routing with HTTP protocol across two domains")
  public void testNginxHttpHostRoutingAcrossDomains() {

    // verify NGINX host routing with HTTP protocol
    logger.info("Verifying NGINX host routing with HTTP protocol");
    for (String domainUid : domainUids) {
      verifyClusterLoadbalancing(domainUid, domainUid + "." + defaultNamespace + ".nginx.nonssl.test",
          "http", getNginxLbNodePort("http"), replicaCount, true, "");
    }
  }

  /**
   * Test verifies multiple WebLogic domains can be loadbalanced by NGINX loadbalancer with host based routing rules.
   * Accesses the clusterview application deployed in the WebLogic cluster through NGINX tls host routing with HTTPS
   * protocol and verifies it is correctly routed to the specific domain cluster identified by the -H host header.
   *
   */
  @Order(17)
  @Test
  @DisplayName("verify NGINX host routing with https protocol across two domains")
  public void testNginxHttpsHostRoutingAcrossDomains() {

    // verify NGINX host routing with HTTPS protocol across two domains
    logger.info("Verifying NGINX host routing with HTTPS protocol across two domains");
    for (String domainUid : domainUids) {
      verifyClusterLoadbalancing(domainUid, domainUid + "." + defaultNamespace + ".nginx.ssl.test",
          "https", getNginxLbNodePort("https"), replicaCount, true, "");
    }
  }

  /**
   * Test verifies multiple WebLogic domains can be loadbalanced by NGINX loadbalancer with path routing rules.
   * Accesses the clusterview application deployed in the WebLogic cluster through NGINX loadbalancer and verifies it
   * is correctly routed to the specific domain cluster.
   */
  @Order(18)
  @Test
  @DisplayName("Verify NGINX path routing with HTTP protocol across two domains")
  public void testNginxPathRoutingAcrossDomains() {

    // verify NGINX path routing with HTTP protocol across two domains
    logger.info("Verifying NGINX path routing with HTTP protocol across two domains");
    for (String domainUid : domainUids) {
      verifyClusterLoadbalancing(domainUid, "", "http", getNginxLbNodePort("http"),
          replicaCount, false, "/" + domainUid.substring(6));
    }
  }

  /**
   * Test verifies multiple WebLogic domains can be loadbalanced by Voyager with TLS path routing rules.
   * Accesses the clusterview application deployed in the WebLogic cluster through Voyager and verifies it
   * is correctly routed to the specific domain cluster.
   */
  @Order(19)
  @Test
  @DisplayName("Verify Voyager path routing with HTTPS protocol across two domains")
  public void testVoyagerTLSPathRoutingAcrossDomains() {

    // verify Voyager path routing with HTTP protocol across two domains
    logger.info("Verifying Voyager path routing with HTTPS protocol across two domains");
    String ingressName = "voyager-tls-pathrouting";
    for (String domainUid : domainUids) {
      verifyClusterLoadbalancing(domainUid, "", "https", getVoyagerLbNodePort(ingressName, "tcp-443"),
          replicaCount, false, "/" + domainUid.substring(6));
    }
  }

  /**
   * Verify WebLogic admin console is accessible through Traefik path routing with HTTPS protocol.
   */
  @Order(20)
  @Test
  @DisplayName("Verify WebLogic admin console is accessible through Traefik path routing with HTTPS protocol")
  public void testTraefikTLSPathRoutingAdminServer() {
    assumeFalse(WEBLOGIC_SLIM, "Skipping the Console Test for slim image");
    logger.info("Verifying WebLogic admin console is accessible through Traefik path routing with HTTPS protocol");

    verifyAdminServerAccess(true, getTraefikLbNodePort(true), false, "", "");

    // verify the header 'WL-Proxy-Client-IP' is removed in the admin server log
    // verify the header 'WL-Proxy-SSL: false' is removed in the admin server log
    // verify the header 'WL-Proxy-SSL: true' is added in the admin server log
    verifyHeadersInAdminServerLog(domainAdminServerPodNames.get(0), defaultNamespace);
  }

  /**
   * Test verifies multiple WebLogic domains can be loadbalanced by Traefik with TLS path routing rules.
   * Accesses the clusterview application deployed in the WebLogic cluster through Traefik and verifies it
   * is correctly routed to the specific domain cluster.
   */
  @Order(21)
  @Test
  @DisplayName("Verify Traefik path routing with HTTPS protocol across two domains")
  public void testTraefikTLSPathRoutingAcrossDomains() {

    // verify Voyager path routing with HTTP protocol across two domains
    logger.info("Verifying Traefik path routing with HTTPS protocol across two domains");
    for (String domainUid : domainUids) {
      verifyClusterLoadbalancing(domainUid, "", "https", getTraefikLbNodePort(true),
          replicaCount, false, "/" + domainUid.substring(6));
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

    // uninstall NGINX
    if (nginxHelmParams != null) {
      assertThat(uninstallNginx(nginxHelmParams))
          .as("Test uninstallNginx returns true")
          .withFailMessage("uninstallNginx() did not return true")
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
    logger.info("deleting pvc {0}", defaultSharingPvcName);
    assertTrue(deletePersistentVolumeClaim(defaultSharingPvcName, defaultNamespace));
    logger.info("deleting pv {0}", defaultSharingPvName);
    assertTrue(deletePersistentVolume(defaultSharingPvName));
    logger.info("deleting pvc {0}", apachePvcName);
    assertTrue(deletePersistentVolumeClaim(apachePvcName, defaultNamespace));
    logger.info("deleting pv {0}", apachePvName);
    assertTrue(deletePersistentVolume(apachePvName));

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

      // this secret is used only for non-kind cluster
      createSecretForBaseImages(domainNamespaces.get(i));

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

      V1PersistentVolume v1pv = new V1PersistentVolume()
          .spec(new V1PersistentVolumeSpec()
              .addAccessModesItem("ReadWriteMany")
              .volumeMode("Filesystem")
              .putCapacityItem("storage", Quantity.fromString("2Gi"))
              .persistentVolumeReclaimPolicy("Retain"))
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
      createPVPVCAndVerify(v1pv, v1pvc, labelSelector, domainNamespace,
          domainUid + "-weblogic-domain-storage-class", pvHostPath);

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
              getServiceNodePort(domainNamespace, getExternalServicePodName(adminServerPodName), "default");

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
                    .initContainers(Collections.singletonList(createfixPVCOwnerContainer(pvName, "/shared")))
                    .containers(Collections.singletonList(new V1Container()
                        .name("create-weblogic-domain-onpv-container")
                        .image(WEBLOGIC_IMAGE_TO_USE_IN_SPEC)
                        .ports(Collections.singletonList(new V1ContainerPort()
                            .containerPort(ADMIN_SERVER_PORT)))
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
                    .imagePullSecrets(Collections.singletonList(
                        new V1LocalObjectReference()
                            .name(BASE_IMAGES_REPO_SECRET))))));  // this secret is used only for non-kind cluster

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
    p.setProperty("managed_server_port", "" + MANAGED_SERVER_PORT);
    p.setProperty("admin_server_port", "" + ADMIN_SERVER_PORT);
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
    Domain domain = new Domain()
        .apiVersion(DOMAIN_API_VERSION)
        .kind("Domain")
        .metadata(new V1ObjectMeta()
            .name(domainUid)
            .namespace(domainNamespace))
        .spec(new DomainSpec()
            .domainUid(domainUid)
            .domainHome("/shared/domains/" + domainUid)
            .domainHomeSourceType("PersistentVolume")
            .image(WEBLOGIC_IMAGE_TO_USE_IN_SPEC)
            .imagePullSecrets(Collections.singletonList(
                new V1LocalObjectReference()
                    .name(BASE_IMAGES_REPO_SECRET)))  // this secret is used only for non-kind cluster
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
                    .value("-Dweblogic.StdoutDebugEnabled=true "
                        + "-Dweblogic.http.isWLProxyHeadersAccessible=true "
                        + "-Dweblogic.debug.DebugHttp=true "
                        + "-Dweblogic.rjvm.allowUnknownHost=true "
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
    setPodAntiAffinity(domain);
    return domain;
  }

  /**
   * Create two domains on PV using WLST.
   */
  private void createTwoDomainsSharingPVUsingWlstAndVerify() {

    // create pull secrets for WebLogic image
    // this secret is used only for non-kind cluster
    createSecretForBaseImages(defaultNamespace);

    // create WebLogic credentials secret
    createSecretWithUsernamePassword(wlSecretName, defaultNamespace, ADMIN_USERNAME_DEFAULT, ADMIN_PASSWORD_DEFAULT);
    Path pvHostPath = get(PV_ROOT, this.getClass().getSimpleName(), "default-sharing-persistentVolume");;

    V1PersistentVolume v1pv = new V1PersistentVolume()
        .spec(new V1PersistentVolumeSpec()
            .addAccessModesItem("ReadWriteMany")
            .volumeMode("Filesystem")
            .putCapacityItem("storage", Quantity.fromString("6Gi"))
            .persistentVolumeReclaimPolicy("Retain"))
        .metadata(new V1ObjectMetaBuilder()
            .withName(defaultSharingPvName)
            .build()
            .putLabelsItem("sharing-pv", "true"));

    V1PersistentVolumeClaim v1pvc = new V1PersistentVolumeClaim()
        .spec(new V1PersistentVolumeClaimSpec()
            .addAccessModesItem("ReadWriteMany")
            .volumeName(defaultSharingPvName)
            .resources(new V1ResourceRequirements()
                .putRequestsItem("storage", Quantity.fromString("6Gi"))))
        .metadata(new V1ObjectMetaBuilder()
            .withName(defaultSharingPvcName)
            .withNamespace(defaultNamespace)
            .build()
            .putLabelsItem("sharing-pvc", "true"));

    // create pv and pvc
    String labelSelector = String.format("sharing-pv in (%s)", "true");
    createPVPVCAndVerify(v1pv, v1pvc, labelSelector,
        defaultNamespace, "default-sharing-weblogic-domain-storage-class", pvHostPath);

    for (int i = 1; i <= numberOfDomains; i++) {
      String domainUid = domainUids.get(i - 1);
      String domainScriptConfigMapName = "create-domain" + i + "-scripts-cm";
      String createDomainInPVJobName = "create-domain" + i + "-onpv-job";

      t3ChannelPort = getNextFreePort(32003 + i, 32700);
      logger.info("t3ChannelPort for domain {0} is {1}", domainUid, t3ChannelPort);

      // run create a domain on PV job using WLST
      runCreateDomainOnPVJobUsingWlst(defaultSharingPvName, defaultSharingPvcName, domainUid, defaultNamespace,
          domainScriptConfigMapName, createDomainInPVJobName);

      // create the domain custom resource configuration object
      logger.info("Creating domain custom resource");
      Domain domain = createDomainCustomResource(domainUid, defaultNamespace, defaultSharingPvName,
          defaultSharingPvcName, t3ChannelPort);

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

      int serviceNodePort =
          getServiceNodePort(defaultNamespace, getExternalServicePodName(adminServerPodName), "default");
      logger.info("Getting admin service node port: {0}", serviceNodePort);

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

  private static void createCertKeyFiles(String cn) {
    assertDoesNotThrow(() -> {
      tlsKeyFile = Files.createTempFile("tls", ".key");
      tlsCertFile = Files.createTempFile("tls", ".crt");
      String command = "openssl req -x509 -nodes -days 365 -newkey rsa:2048 -keyout " + tlsKeyFile
          + " -out " + tlsCertFile + " -subj \"/CN=" + cn + "\"";
      logger.info("Executing command: {0}", command);
      ExecCommand.exec(command, true);
    });
  }

  private static void createSecretWithTLSCertKeyVoyager(String tlsSecretName) {
    String command = "kubectl create secret tls " + tlsSecretName + " --key " + tlsKeyFile + " --cert " + tlsCertFile;
    logger.info("Executing command: {0}", command);
    assertDoesNotThrow(() -> ExecCommand.exec(command, true));
  }

  private void createTraefikIngressRoutingRules() {
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
    String command = "kubectl create -f " + dstFile;
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

  private void createVoyagerIngressHostRoutingRules(boolean isTLS) {
    for (String domainUid : domainUids) {
      String ingressName;
      if (isTLS) {
        ingressName = domainUid + "-voyager-tls";
      } else {
        ingressName = domainUid + "-voyager-host-routing";
      }

      // set the annotations for Voyager
      HashMap<String, String> annotations = new HashMap<>();
      annotations.put("ingress.appscode.com/type", "NodePort");
      annotations.put("ingress.appscode.com/affinity", "cookie");
      annotations.put("kubernetes.io/ingress.class", "voyager");

      List<NetworkingV1beta1IngressRule> ingressRules = new ArrayList<>();
      List<NetworkingV1beta1IngressTLS> tlsList = new ArrayList<>();

      NetworkingV1beta1HTTPIngressPath httpIngressPath = new NetworkingV1beta1HTTPIngressPath()
          .path(null)
          .backend(new NetworkingV1beta1IngressBackend()
              .serviceName(domainUid + "-cluster-cluster-1")
              .servicePort(new IntOrString(MANAGED_SERVER_PORT))
          );

      // set the ingress rule host
      String ingressHost;
      if (isTLS) {
        ingressHost = domainUid + "." + defaultNamespace + ".voyager.ssl.test";
      } else {
        ingressHost = domainUid + "." + defaultNamespace + ".voyager.nonssl.test";
      }
      NetworkingV1beta1IngressRule ingressRule = new NetworkingV1beta1IngressRule()
          .host(ingressHost)
          .http(new NetworkingV1beta1HTTPIngressRuleValue()
              .paths(Collections.singletonList(httpIngressPath)));

      ingressRules.add(ingressRule);

      if (isTLS) {
        String tlsSecretName = domainUid + "-voyager-tls-secret";
        createCertKeyFiles(ingressHost);
        // for Voyager tls secret, it should be created using kubectl command
        createSecretWithTLSCertKeyVoyager(tlsSecretName);
        NetworkingV1beta1IngressTLS tls = new NetworkingV1beta1IngressTLS()
            .addHostsItem(ingressHost)
            .secretName(tlsSecretName);
        tlsList.add(tls);
      }

      if (isTLS) {
        assertDoesNotThrow(() -> createIngress(ingressName, defaultNamespace, annotations, ingressRules, tlsList));
      } else {
        assertDoesNotThrow(() -> createIngress(ingressName, defaultNamespace, annotations, ingressRules, null));
      }

      // wait until voyager ingress pod is ready
      waitUntilVoyagerPodReady(ingressName);

      // check the ingress was found in the domain namespace
      assertThat(assertDoesNotThrow(() -> listIngresses(defaultNamespace)))
          .as(String.format("Test ingress %s was found in namespace %s", ingressName, defaultNamespace))
          .withFailMessage(String.format("Ingress %s was not found in namespace %s", ingressName, defaultNamespace))
          .contains(ingressName);

      logger.info("ingress {0} was created in namespace {1}", ingressName, defaultNamespace);

      // check the ingress is ready to route the app to the server pod
      int httpNodeport = getVoyagerLbNodePort(ingressName, "tcp-80");
      int httpsNodeport = getVoyagerLbNodePort(ingressName, "tcp-443");
      checkIngressReady(true, ingressHost, isTLS, httpNodeport, httpsNodeport, "");
    }
  }

  private void createVoyagerIngressPathRoutingRules() {
    String ingressName = "voyager-path-routing";

    // set the annotations for Voyager
    HashMap<String, String> annotations = new HashMap<>();
    annotations.put("ingress.appscode.com/type", "NodePort");
    annotations.put("kubernetes.io/ingress.class", "voyager");
    annotations.put("ingress.appscode.com/rewrite-target", "/");

    List<NetworkingV1beta1IngressRule> ingressRules = new ArrayList<>();
    List<NetworkingV1beta1HTTPIngressPath> httpIngressPaths = new ArrayList<>();

    for (String domainUid : domainUids) {
      NetworkingV1beta1HTTPIngressPath httpIngressPath = new NetworkingV1beta1HTTPIngressPath()
          .path("/" + domainUid.substring(6))
          .backend(new NetworkingV1beta1IngressBackend()
              .serviceName(domainUid + "-cluster-cluster-1")
              .servicePort(new IntOrString(MANAGED_SERVER_PORT))
          );
      httpIngressPaths.add(httpIngressPath);
    }

    NetworkingV1beta1IngressRule ingressRule = new NetworkingV1beta1IngressRule()
        .host("")
        .http(new NetworkingV1beta1HTTPIngressRuleValue()
            .paths(httpIngressPaths));

    ingressRules.add(ingressRule);

    assertDoesNotThrow(() -> createIngress(ingressName, defaultNamespace, annotations, ingressRules, null));

    // wait until voyager ingress pod is ready
    waitUntilVoyagerPodReady(ingressName);

    // check the ingress was found in the domain namespace
    assertThat(assertDoesNotThrow(() -> listIngresses(defaultNamespace)))
        .as(String.format("Test ingress %s was found in namespace %s", ingressName, defaultNamespace))
        .withFailMessage(String.format("Ingress %s was not found in namespace %s", ingressName, defaultNamespace))
        .contains(ingressName);

    logger.info("ingress {0} was created in namespace {1}", ingressName, defaultNamespace);

    // check the ingress is ready to route the app to the server pod
    int httpNodeport = getVoyagerLbNodePort(ingressName, "tcp-80");
    for (String domainUid : domainUids) {
      checkIngressReady(false, "", false, httpNodeport, -1, domainUid.substring(6));
    }
  }

  private void createVoyagerIngressTLSPathRoutingRules() {
    logger.info("Creating ingress rules for Voyager tls console traffic routing");
    Path srcFile = Paths.get(ActionConstants.RESOURCE_DIR, "voyager/voyager-tls-pathrouting.yaml");
    dstFile = Paths.get(TestConstants.RESULTS_ROOT, "voyager/voyager-tls-pathrouting.yaml");
    assertDoesNotThrow(() -> {
      Files.deleteIfExists(dstFile);
      Files.createDirectories(dstFile.getParent());
      Files.write(dstFile, Files.readString(srcFile).replaceAll("@NS@", defaultNamespace)
          .replaceAll("@domain1uid@", domainUids.get(0))
          .replaceAll("@domain2uid@", domainUids.get(1))
          .replaceAll("@secretName@", domainUids.get(0) + "-voyager-tls-secret")
          .getBytes(StandardCharsets.UTF_8));
    });
    String command = "kubectl create -f " + dstFile;
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

  private void createNginxIngressHostRoutingForTwoDomains(boolean isTLS) {

    // create an ingress in domain namespace
    String ingressName;
    if (isTLS) {
      ingressName = defaultNamespace + "-nginx-tls";
    } else {
      ingressName = defaultNamespace + "-nginx-host-routing";
    }

    HashMap<String, String> annotations = new HashMap<>();
    annotations.put("kubernetes.io/ingress.class", "nginx");

    // create ingress rules for two domains
    List<NetworkingV1beta1IngressRule> ingressRules = new ArrayList<>();
    List<NetworkingV1beta1IngressTLS> tlsList = new ArrayList<>();
    for (String domainUid : domainUids) {

      NetworkingV1beta1HTTPIngressPath httpIngressPath = new NetworkingV1beta1HTTPIngressPath()
          .path(null)
          .backend(new NetworkingV1beta1IngressBackend()
              .serviceName(domainUid + "-cluster-cluster-1")
              .servicePort(new IntOrString(MANAGED_SERVER_PORT))
          );

      // set the ingress rule host
      String ingressHost;
      if (isTLS) {
        ingressHost = domainUid + "." + defaultNamespace + ".nginx.ssl.test";
      } else {
        ingressHost = domainUid + "." + defaultNamespace + ".nginx.nonssl.test";
      }
      NetworkingV1beta1IngressRule ingressRule = new NetworkingV1beta1IngressRule()
          .host(ingressHost)
          .http(new NetworkingV1beta1HTTPIngressRuleValue()
              .paths(Collections.singletonList(httpIngressPath)));

      ingressRules.add(ingressRule);

      if (isTLS) {
        String tlsSecretName = domainUid + "-nginx-tls-secret";
        createCertKeyFiles(ingressHost);
        assertDoesNotThrow(() -> createSecretWithTLSCertKey(tlsSecretName, defaultNamespace, tlsKeyFile, tlsCertFile));
        NetworkingV1beta1IngressTLS tls = new NetworkingV1beta1IngressTLS()
            .addHostsItem(ingressHost)
            .secretName(tlsSecretName);
        tlsList.add(tls);
      }
    }

    if (isTLS) {
      assertDoesNotThrow(() -> createIngress(ingressName, defaultNamespace, annotations, ingressRules, tlsList));
    } else {
      assertDoesNotThrow(() -> createIngress(ingressName, defaultNamespace, annotations, ingressRules, null));
    }

    // check the ingress was found in the domain namespace
    assertThat(assertDoesNotThrow(() -> listIngresses(defaultNamespace)))
        .as(String.format("Test ingress %s was found in namespace %s", ingressName, defaultNamespace))
        .withFailMessage(String.format("Ingress %s was not found in namespace %s", ingressName, defaultNamespace))
        .contains(ingressName);

    logger.info("ingress {0} was created in namespace {1}", ingressName, defaultNamespace);

    // check the ingress is ready to route the app to the server pod
    int httpNodeport = getNginxLbNodePort("http");
    int httpsNodeport = getNginxLbNodePort("https");
    for (String domainUid : domainUids) {
      String ingressHost;
      if (isTLS) {
        ingressHost = domainUid + "." + defaultNamespace + ".nginx.ssl.test";
      } else {
        ingressHost = domainUid + "." + defaultNamespace + ".nginx.nonssl.test";
      }

      checkIngressReady(true, ingressHost, isTLS, httpNodeport, httpsNodeport, "");
    }
  }

  private void createNginxIngressPathRoutingForTwoDomains() {

    // create an ingress in domain namespace
    String ingressName = defaultNamespace + "-nginx-path-routing";

    HashMap<String, String> annotations = new HashMap<>();
    annotations.put("kubernetes.io/ingress.class", "nginx");
    annotations.put("nginx.ingress.kubernetes.io/rewrite-target", "/$1");

    // create ingress rules for two domains
    List<NetworkingV1beta1IngressRule> ingressRules = new ArrayList<>();
    List<NetworkingV1beta1HTTPIngressPath> httpIngressPaths = new ArrayList<>();

    for (String domainUid : domainUids) {
      NetworkingV1beta1HTTPIngressPath httpIngressPath = new NetworkingV1beta1HTTPIngressPath()
          .path("/" + domainUid.substring(6) + "(.+)")
          .backend(new NetworkingV1beta1IngressBackend()
              .serviceName(domainUid + "-cluster-cluster-1")
              .servicePort(new IntOrString(MANAGED_SERVER_PORT))
          );
      httpIngressPaths.add(httpIngressPath);
    }

    NetworkingV1beta1IngressRule ingressRule = new NetworkingV1beta1IngressRule()
        .host("")
        .http(new NetworkingV1beta1HTTPIngressRuleValue()
            .paths(httpIngressPaths));

    ingressRules.add(ingressRule);

    assertDoesNotThrow(() -> createIngress(ingressName, defaultNamespace, annotations, ingressRules, null));

    // check the ingress was found in the domain namespace
    assertThat(assertDoesNotThrow(() -> listIngresses(defaultNamespace)))
        .as(String.format("Test ingress %s was found in namespace %s", ingressName, defaultNamespace))
        .withFailMessage(String.format("Ingress %s was not found in namespace %s", ingressName, defaultNamespace))
        .contains(ingressName);

    logger.info("ingress {0} was created in namespace {1}", ingressName, defaultNamespace);

    // check the ingress is ready to route the app to the server pod
    int httpNodeport = getNginxLbNodePort("http");
    for (String domainUid : domainUids) {
      checkIngressReady(false, "", false, httpNodeport, -1, domainUid.substring(6));
    }
  }

  private void createNginxTLSPathRoutingForTwoDomains() {

    // create an ingress in domain namespace
    String ingressName = defaultNamespace + "-nginx-tls-pathrouting";

    HashMap<String, String> annotations = new HashMap<>();
    annotations.put("kubernetes.io/ingress.class", "nginx");
    annotations.put("nginx.ingress.kubernetes.io/rewrite-target", "/$1");
    String configurationSnippet =
        new StringBuffer()
        .append("more_clear_input_headers \"WL-Proxy-Client-IP\" \"WL-Proxy-SSL\"; ")
        .append("more_set_input_headers \"X-Forwarded-Proto: https\"; ")
        .append("more_set_input_headers \"WL-Proxy-SSL: true\";")
        .toString();
    annotations.put("nginx.ingress.kubernetes.io/configuration-snippet", configurationSnippet);
    annotations.put("nginx.ingress.kubernetes.io/ingress.allow-http", "false");

    // create ingress rules for two domains
    List<NetworkingV1beta1IngressRule> ingressRules = new ArrayList<>();
    List<NetworkingV1beta1HTTPIngressPath> httpIngressPaths = new ArrayList<>();

    for (String domainUid : domainUids) {
      NetworkingV1beta1HTTPIngressPath httpIngressAdminConsolePath = new NetworkingV1beta1HTTPIngressPath()
          .path("/" + domainUid.substring(6) + "console(.+)")
          .backend(new NetworkingV1beta1IngressBackend()
              .serviceName(domainUid + "-" + ADMIN_SERVER_NAME_BASE)
              .servicePort(new IntOrString(ADMIN_SERVER_PORT))
          );
      httpIngressPaths.add(httpIngressAdminConsolePath);
      NetworkingV1beta1HTTPIngressPath httpIngressPath = new NetworkingV1beta1HTTPIngressPath()
          .path("/" + domainUid.substring(6) + "(.+)")
          .backend(new NetworkingV1beta1IngressBackend()
              .serviceName(domainUid + "-cluster-cluster-1")
              .servicePort(new IntOrString(MANAGED_SERVER_PORT))
          );
      httpIngressPaths.add(httpIngressPath);
    }

    NetworkingV1beta1IngressRule ingressRule = new NetworkingV1beta1IngressRule()
        .host("")
        .http(new NetworkingV1beta1HTTPIngressRuleValue()
            .paths(httpIngressPaths));

    ingressRules.add(ingressRule);

    // create TLS list for the ingress
    List<NetworkingV1beta1IngressTLS> tlsList = new ArrayList<>();
    String tlsSecretName = domainUids.get(0) + "-nginx-tlspathrouting-secret";
    createCertKeyFiles(domainUids.get(0) + "." + defaultNamespace + ".nginx.tlspathrouting.test");
    assertDoesNotThrow(() -> createSecretWithTLSCertKey(tlsSecretName, defaultNamespace, tlsKeyFile, tlsCertFile));
    NetworkingV1beta1IngressTLS tls = new NetworkingV1beta1IngressTLS()
        .secretName(tlsSecretName);
    tlsList.add(tls);

    assertDoesNotThrow(() -> createIngress(ingressName, defaultNamespace, annotations, ingressRules, tlsList));

    // check the ingress was found in the domain namespace
    assertThat(assertDoesNotThrow(() -> listIngresses(defaultNamespace)))
        .as(String.format("Test ingress %s was found in namespace %s", ingressName, defaultNamespace))
        .withFailMessage(String.format("Ingress %s was not found in namespace %s", ingressName, defaultNamespace))
        .contains(ingressName);

    logger.info("ingress {0} was created in namespace {1}", ingressName, defaultNamespace);

    // check the ingress is ready to route the app to the server pod
    int httpsNodeport = getNginxLbNodePort("https");
    for (String domainUid : domainUids) {
      checkIngressReady(false, "", true, -1, httpsNodeport, domainUid.substring(6));
    }
  }

  /**
   * Get the Voyager ingress nodeport.
   * @param ingressName name of the Voyager ingress
   * @param channelName channel name of the Voyager ingress service, accept value: tcp-80 or tcp-443
   * @return voyager load balancer node port
   */
  private static int getVoyagerLbNodePort(String ingressName, String channelName) {

    String ingressServiceName = VOYAGER_CHART_NAME + "-" + ingressName;
    // get ingress service Nodeport
    int ingressServiceNodePort = assertDoesNotThrow(() ->
            getServiceNodePort(defaultNamespace, ingressServiceName, channelName),
        "Getting voyager loadbalancer service node port failed");
    logger.info("Node port for {0} is: {1} :", ingressServiceName, ingressServiceNodePort);
    return ingressServiceNodePort;
  }

  private int getTraefikLbNodePort(boolean isHttps) {
    logger.info("Getting web node port for Traefik loadbalancer {0}", traefikHelmParams.getReleaseName());
    return assertDoesNotThrow(() ->
            getServiceNodePort(traefikNamespace, traefikHelmParams.getReleaseName(), isHttps ? "websecure" : "web"),
        "Getting web node port for Traefik loadbalancer failed");
  }

  /**
   * Get NGINX node port with specified channel name.
   *
   * @param channelName channel name of the NGINX node port, either http or https
   * @return NGINX load balancer node port
   */
  private int getNginxLbNodePort(String channelName) {
    String nginxServiceName = nginxHelmParams.getReleaseName() + "-ingress-nginx-controller";

    return getServiceNodePort(nginxNamespace, nginxServiceName, channelName);
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
            getServiceNodePort(namespace, getExternalServicePodName(adminServerPodName), "default"),
        "Getting admin server node port failed");
    assertNotEquals(-1, serviceNodePort, "admin server default node port is not valid");
    logger.info("Deploying application {0} to domain {1} cluster target cluster-1 in namespace {2}",
        clusterViewAppPath, domainUid, namespace);
    String targets = "{ identity: [ clusters, 'cluster-1' ] }";
    ExecResult result = DeployUtil.deployUsingRest(K8S_NODEPORT_HOST,
        String.valueOf(serviceNodePort),
        ADMIN_USERNAME_DEFAULT, ADMIN_PASSWORD_DEFAULT,
        targets, clusterViewAppPath, null, domainUid + "clusterview");
    assertNotNull(result, "Application deployment failed");
    logger.info("Application deployment returned {0}", result.toString());
    assertEquals("202", result.stdout(), "Deployment didn't return HTTP status code 202");
  }

  private void verifyAdminServerAccess(boolean isTLS,
                                       int lbNodePort,
                                       boolean isHostRouting,
                                       String ingressHostName,
                                       String pathLocation) {

    StringBuffer consoleUrl = new StringBuffer();
    if (isTLS) {
      consoleUrl.append("https://");
    } else {
      consoleUrl.append("http://");
    }
    consoleUrl.append(K8S_NODEPORT_HOST)
        .append(":")
        .append(lbNodePort);
    if (!isHostRouting) {
      consoleUrl.append(pathLocation);
    }

    consoleUrl.append("/console/login/LoginForm.jsp");
    String curlCmd;
    if (isHostRouting) {
      curlCmd = String.format("curl -ks --show-error --noproxy '*' -H 'host: %s' %s",
          ingressHostName, consoleUrl.toString());
    } else {
      if (isTLS) {
        curlCmd = String.format("curl -ks --show-error --noproxy '*' -H 'WL-Proxy-Client-IP: 1.2.3.4' "
            + "-H 'WL-Proxy-SSL: false' %s", consoleUrl.toString());
      } else {
        curlCmd = String.format("curl -ks --show-error --noproxy '*' %s", consoleUrl.toString());
      }
    }

    boolean consoleAccessible = false;
    for (int i = 0; i < 10; i++) {
      assertDoesNotThrow(() -> TimeUnit.SECONDS.sleep(1));
      ExecResult result;
      try {
        logger.info("Accessing console using curl request, iteration {0}: {1}", i, curlCmd);
        result = ExecCommand.exec(curlCmd, true);
        String response = result.stdout().trim();
        logger.info("exitCode: {0}, \nstdout: {1}, \nstderr: {2}",
            result.exitValue(), response, result.stderr());
        if (response.contains("login")) {
          consoleAccessible = true;
          break;
        }
      } catch (IOException | InterruptedException ex) {
        logger.severe(ex.getMessage());
      }
    }
    assertTrue(consoleAccessible, "Couldn't access admin server console");
  }

  /**
   * Verify cluster load balancing with ClusterViewServlet app.
   *
   * @param domainUid uid of the domain in which the cluster exists
   * @param ingressHostName ingress host name
   * @param protocol protocol used to test, accepted value: http or https
   * @param lbPort  load balancer service port
   * @param replicaCount replica count of the managed servers in the cluster
   * @param hostRouting whether it is a host base routing
   * @param locationString location string in apache configuration or path prefix in path routing
   */
  private void verifyClusterLoadbalancing(String domainUid,
                                          String ingressHostName,
                                          String protocol,
                                          int lbPort,
                                          int replicaCount,
                                          boolean hostRouting,
                                          String locationString) {

    // access application in managed servers through load balancer
    logger.info("Accessing the clusterview app through load balancer to verify all servers in cluster");
    String curlRequest;
    if (hostRouting) {
      curlRequest = String.format("curl --show-error -ks --noproxy '*' "
              + "-H 'host: %s' %s://%s:%s/clusterview/ClusterViewServlet"
              + "\"?user=" + ADMIN_USERNAME_DEFAULT
              + "&password=" + ADMIN_PASSWORD_DEFAULT + "\"",
          ingressHostName, protocol, K8S_NODEPORT_HOST, lbPort);
    } else {
      curlRequest = String.format("curl --show-error -ks --noproxy '*' "
              + "%s://%s:%s" + locationString + "/clusterview/ClusterViewServlet"
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
    if (WEBLOGIC_SLIM) { 
      getLogger().info("Check REST Console for WebLogic slim image");
      StringBuffer curlCmd  = null;
      curlCmd  = new StringBuffer("status=$(curl --user ");
      curlCmd.append(userName)
          .append(":")
          .append(password)
          .append(" http://" + K8S_NODEPORT_HOST + ":" + nodePort)
          .append("/management/tenant-monitoring/servers/")
          .append(" --silent --show-error ")
          .append(" -o /dev/null")
          .append(" -w %{http_code});")
          .append("echo ${status}");
      logger.info("checkRestConsole : curl command {0}", new String(curlCmd));
      try {
        ExecResult result = ExecCommand.exec(new String(curlCmd), true);
        String response = result.stdout().trim();
        logger.info("exitCode: {0}, \nstdout: {1}, \nstderr: {2}",
            result.exitValue(), response, result.stderr());
        if (response.contains("200")) {
          return true;
        } else {
          return false;
        }
      } catch (IOException | InterruptedException ex) {
        logger.info("Exception in checkRestConsole {0}", ex);
        return false;
      }
    } else {
      // generic/dev Image
      getLogger().info("Check administration Console for generic/dev image");
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
  }

  private static Callable<Boolean> pullImageFromOcirAndPushToKind(String apacheImage) {
    return (() -> {
      kindRepoApacheImage = KIND_REPO + apacheImage.substring(OCIR_REGISTRY.length() + 1);
      logger.info("pulling image {0} from OCIR, tag it as image {1} and push to KIND repo",
          apacheImage, kindRepoApacheImage);
      return dockerPull(apacheImage) && dockerTag(apacheImage, kindRepoApacheImage) && dockerPush(kindRepoApacheImage);
    });
  }

  private void waitUntilVoyagerPodReady(String ingressName) {
    withStandardRetryPolicy
        .conditionEvaluationListener(
            condition -> logger.info(
                "Waiting for Voyager ingress to be ready in namespace {0} (elapsed time {1}ms, remaining time {2}ms)",
                defaultNamespace,
                condition.getElapsedTimeInMS(),
                condition.getRemainingTimeInMS()))
        .until(assertDoesNotThrow(() -> isVoyagerReady(defaultNamespace, ingressName),
            "isVoyagerReady failed with ApiException"));
  }

  private void checkIngressReady(boolean isHostRouting, String ingressHost, boolean isTLS,
                                 int httpNodeport, int httpsNodeport, String pathString) {
    // check the ingress is ready to route the app to the server pod
    if (httpNodeport != 0 && httpsNodeport != 0) {
      String curlCmd;
      if (isHostRouting) {
        if (isTLS) {
          curlCmd = "curl -k --silent --show-error --noproxy '*' -H 'host: " + ingressHost
              + "' https://" + K8S_NODEPORT_HOST + ":" + httpsNodeport
              + "/weblogic/ready --write-out %{http_code} -o /dev/null";
        } else {
          curlCmd = "curl --silent --show-error --noproxy '*' -H 'host: " + ingressHost
              + "' http://" + K8S_NODEPORT_HOST + ":" + httpNodeport
              + "/weblogic/ready --write-out %{http_code} -o /dev/null";
        }
      } else {
        if (isTLS) {
          curlCmd = "curl -k --silent --show-error --noproxy '*' https://" + K8S_NODEPORT_HOST + ":" + httpsNodeport
              + "/" + pathString + "/weblogic/ready --write-out %{http_code} -o /dev/null";
        } else {
          curlCmd = "curl --silent --show-error --noproxy '*' http://" + K8S_NODEPORT_HOST + ":" + httpNodeport
              + "/" + pathString + "/weblogic/ready --write-out %{http_code} -o /dev/null";
        }
      }
      logger.info("Executing curl command {0}", curlCmd);
      assertTrue(callWebAppAndWaitTillReady(curlCmd, 60));
    }
  }

  /**
   * Create PV and PVC for Apache custom configuration file in specified namespace.
   * @param apacheNamespace namespace in which to create PVC
   */
  private void createPVPVCForApacheCustomConfiguration(String apacheNamespace) {

    Path pvHostPath = get(PV_ROOT, this.getClass().getSimpleName(), "apache-persistentVolume");

    V1PersistentVolume v1pv = new V1PersistentVolume()
        .spec(new V1PersistentVolumeSpec()
            .addAccessModesItem("ReadWriteMany")
            .storageClassName("apache-storage-class")
            .volumeMode("Filesystem")
            .putCapacityItem("storage", Quantity.fromString("1Gi"))
            .persistentVolumeReclaimPolicy("Retain"))
        .metadata(new V1ObjectMetaBuilder()
            .withName(apachePvName)
            .build()
            .putLabelsItem("apacheLabel", "apache-custom-config"));

    V1PersistentVolumeClaim v1pvc = new V1PersistentVolumeClaim()
        .spec(new V1PersistentVolumeClaimSpec()
            .addAccessModesItem("ReadWriteMany")
            .storageClassName("apache-storage-class")
            .volumeName(apachePvName)
            .resources(new V1ResourceRequirements()
                .putRequestsItem("storage", Quantity.fromString("1Gi"))))
        .metadata(new V1ObjectMetaBuilder()
            .withName(apachePvcName)
            .withNamespace(apacheNamespace)
            .build()
            .putLabelsItem("apacheLabel", "apache-custom-config"));

    String labelSelector = String.format("apacheLabel in (%s)", "apache-custom-config");
    createPVPVCAndVerify(v1pv, v1pvc, labelSelector, apacheNamespace,
        "apache-storage-class", pvHostPath);
  }

  private void verifyHeadersInAdminServerLog(String podName, String namespace) {

    logger.info("Getting admin server pod log from pod {0} in namespace {1}", podName, namespace);

    withStandardRetryPolicy
        .conditionEvaluationListener(
            condition -> logger.info("Getting admin server pod log {0} in namespace {1}, waiting for success "
                    + "(elapsed time {2}ms, remaining time {3}ms)",
                podName,
                namespace,
                condition.getElapsedTimeInMS(),
                condition.getRemainingTimeInMS()))
        .until(() -> {
          return assertDoesNotThrow(() ->
              getPodLog(podName, namespace, "weblogic-server", null, 120)) != null;
        });

    String adminServerPodLog0 = assertDoesNotThrow(() ->
        getPodLog(podName, namespace, "weblogic-server", null, 120));

    assertNotNull(adminServerPodLog0,
        String.format("failed to get admin server log from pod %s in namespace %s, returned null",
            podName, namespace));

    String adminServerPodLog = adminServerPodLog0.toLowerCase();

    // verify the admin server log does not contain WL-Proxy-Client-IP header
    logger.info("Checking that the admin server log does not contain 'WL-Proxy-Client-IP' header");
    assertFalse(adminServerPodLog.contains("WL-Proxy-Client-IP".toLowerCase()),
        String.format("found WL-Proxy-Client-IP in the admin server pod log, pod: %s; namespace: %s; pod log: %s",
            podName, namespace, adminServerPodLog0));

    // verify the admin server log does not contain header "WL-Proxy-SSL: false"
    logger.info("Checking that the admin server log does not contain header 'WL-Proxy-SSL: false'");
    assertFalse(adminServerPodLog.contains("WL-Proxy-SSL: false".toLowerCase()),
        String.format("found 'WL-Proxy-SSL: false' in the admin server pod log, pod: %s; namespace: %s; pod log: %s",
            podName, namespace, adminServerPodLog0));

    // verify the admin server log contains header "WL-Proxy-SSL: true"
    logger.info("Checking that the admin server log contains header 'WL-Proxy-SSL: true'");
    assertTrue(adminServerPodLog.contains("WL-Proxy-SSL: true".toLowerCase()),
        String.format(
            "Did not find 'WL-Proxy-SSL: true' in the admin server pod log, pod: %s; namespace: %s; pod log: %s",
            podName, namespace, adminServerPodLog0));
  }
}

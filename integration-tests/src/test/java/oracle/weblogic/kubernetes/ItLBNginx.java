// Copyright (c) 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes;

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
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

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

import static java.nio.file.Files.copy;
import static java.nio.file.Files.createDirectories;
import static java.nio.file.Paths.get;
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
import static oracle.weblogic.kubernetes.TestConstants.MII_BASIC_IMAGE_NAME;
import static oracle.weblogic.kubernetes.TestConstants.MII_BASIC_IMAGE_TAG;
import static oracle.weblogic.kubernetes.TestConstants.OCIR_PASSWORD;
import static oracle.weblogic.kubernetes.TestConstants.OCIR_REGISTRY;
import static oracle.weblogic.kubernetes.TestConstants.OCIR_USERNAME;
import static oracle.weblogic.kubernetes.TestConstants.PV_ROOT;
import static oracle.weblogic.kubernetes.TestConstants.RESULTS_ROOT;
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
import static oracle.weblogic.kubernetes.assertions.TestAssertions.domainDoesNotExist;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.podStateNotChanged;
import static oracle.weblogic.kubernetes.utils.ApplicationUtils.callWebAppAndWaitTillReady;
import static oracle.weblogic.kubernetes.utils.CommonMiiTestUtils.createMiiDomainAndVerify;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.checkPodReadyAndServiceExists;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.getNextFreePort;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.scaleAndVerifyCluster;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.testUntil;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.verifyServerCommunication;
import static oracle.weblogic.kubernetes.utils.ConfigMapUtils.createConfigMapFromFiles;
import static oracle.weblogic.kubernetes.utils.DomainUtils.createDomainAndVerify;
import static oracle.weblogic.kubernetes.utils.ImageUtils.createSecretForBaseImages;
import static oracle.weblogic.kubernetes.utils.JobUtils.createJobAndWaitUntilComplete;
import static oracle.weblogic.kubernetes.utils.LoadBalancerUtils.installAndVerifyApache;
import static oracle.weblogic.kubernetes.utils.LoadBalancerUtils.installAndVerifyNginx;
import static oracle.weblogic.kubernetes.utils.LoadBalancerUtils.installAndVerifyTraefik;
import static oracle.weblogic.kubernetes.utils.OKDUtils.createRouteForOKD;
import static oracle.weblogic.kubernetes.utils.OKDUtils.setTlsTerminationForRoute;
import static oracle.weblogic.kubernetes.utils.OperatorUtils.installAndVerifyOperator;
import static oracle.weblogic.kubernetes.utils.PersistentVolumeUtils.createPVPVCAndVerify;
import static oracle.weblogic.kubernetes.utils.PersistentVolumeUtils.createfixPVCOwnerContainer;
import static oracle.weblogic.kubernetes.utils.PodUtils.checkPodDoesNotExist;
import static oracle.weblogic.kubernetes.utils.PodUtils.checkPodRestarted;
import static oracle.weblogic.kubernetes.utils.PodUtils.getExternalServicePodName;
import static oracle.weblogic.kubernetes.utils.PodUtils.getPodCreationTime;
import static oracle.weblogic.kubernetes.utils.PodUtils.setPodAntiAffinity;
import static oracle.weblogic.kubernetes.utils.SecretUtils.createSecretWithTLSCertKey;
import static oracle.weblogic.kubernetes.utils.SecretUtils.createSecretWithUsernamePassword;
import static oracle.weblogic.kubernetes.utils.ThreadSafeLogger.getLogger;
import static org.apache.commons.io.FileUtils.deleteDirectory;
import static org.assertj.core.api.Assertions.assertThat;
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
class ItLBNginx {

  private static final int numberOfDomains = 2;
  private static final String wlSecretName = "weblogic-credentials";
  private static final String defaultSharingPvcName = "default-sharing-pvc";
  private static final String defaultSharingPvName = "default-sharing-pv";
  private static final String apachePvcName = "apache-custom-file-pvc";
  private static final String apachePvName = "apache-custom-file-pv";

  private static List<String> domainUids = new ArrayList<>();
  private static String domain1Uid = null;
  private static String domain2Uid = null;
  private static String domain1Namespace = null;
  private static String domain2Namespace = null;
  private static String nginxNamespace = null;
  private static String opNamespace = null;
  private static HelmParams operatorHelmParams = null;
  private static HelmParams nginxHelmParams = null;
  private static Path tlsCertFile;
  private static Path tlsKeyFile;
  private static Path clusterViewAppPath;
  private static LoggingFacade logger = null;
  private static Path dstFile = null;

  // domain constants
  private final String clusterName = "cluster-1";
  private static final int replicaCount = 2;
  private static final int MANAGED_SERVER_PORT = 7100;
  private static final int ADMIN_SERVER_PORT = 7001;

  private int t3ChannelPort = 0;
  private List<String> domainAdminServerPodNames = new ArrayList<>();
  private static List<String> domainNamespaces = new ArrayList<>();

  /**
   * Assigns unique namespaces for operator and domains.
   * Pull WebLogic image if running tests in Kind cluster.
   * Installs operator and create domain.
   *
   * @param namespaces injected by JUnit
   */
  @BeforeAll
  public static void initAll(@Namespaces(4) List<String> namespaces) {
    logger = getLogger();
    logger.info("Assign a unique namespace for operator");
    assertNotNull(namespaces.get(0), "Namespace is null");
    opNamespace = namespaces.get(0);

    // get unique domain namespaces
    logger.info("Get unique namespaces for WebLogic domain1 and domain2");
    for (int i = 1; i <= numberOfDomains; i++) {
      assertNotNull(namespaces.get(i), "Namespace list is null");
      domainNamespaces.add(namespaces.get(i));
    }

    // get a unique Nginx namespace
    logger.info("Assign a unique namespace for Nginx");
    nginxNamespace = namespaces.get(3);

    // set the service account name for the operator
    String opServiceAccount = opNamespace + "-sa";

    // install and verify operator with REST API
    installAndVerifyOperator(opNamespace, opServiceAccount, true, 0, domainNamespaces.get(0),
        domainNamespaces.get(1));
    int externalRestHttpsPort = getServiceNodePort(opNamespace, "external-weblogic-operator-svc");

    // This test uses the operator restAPI to scale the domain. To do this in OKD cluster,
    // we need to expose the external service as route and set tls termination to  passthrough
    logger.info("Create a route for the operator external service - only for OKD");
    createRouteForOKD("external-weblogic-operator-svc", opNamespace);
    // Patch the route just created to set tls termination to passthrough
    setTlsTerminationForRoute("external-weblogic-operator-svc", opNamespace);

    // initiate domainUid list for two domains
    for (int i = 0; i < numberOfDomains; i++) {
      domainUids.add("lb-domain" + (i + 1));
      // create pull secrets for WebLogic image when running in non Kind Kubernetes cluster
      // this secret is used only for non-kind cluster
      createSecretForBaseImages(domainNamespaces.get(i));

      // create a domain resource
      logger.info("Create model-in-image domain {0} in namespace {1}, and wait until it comes up",
          domainUids.get(i), domainNamespaces.get(i));
      createMiiDomainAndVerify(
          domainNamespaces.get(i),
          domainUids.get(i),
          MII_BASIC_IMAGE_NAME + ":" + MII_BASIC_IMAGE_TAG,
          domainUids.get(i) + "-" + ADMIN_SERVER_NAME_BASE,
          domainUids.get(i) + "-" + MANAGED_SERVER_NAME_BASE,
          replicaCount);
    }

    domain1Uid = domainUids.get(0);
    domain2Uid = domainUids.get(1);
    domain1Namespace = domainNamespaces.get(0);
    domain2Namespace = domainNamespaces.get(1);
  }

  /**
   * Verify WebLogic admin console is accessible through NGINX path routing with HTTPS protocol.
   */
  @Test
  @DisplayName("Verify WebLogic admin console is accessible through NGINX path routing with HTTPS protocol")
  void testNginxTLSPathRoutingAdminServer() {
    // build and deploy app to be used by all test cases
    buildAndDeployApp();

    // install Nginx ingress controller for all test cases using Nginx
    installIngressController("Nginx");

    logger.info("Verifying WebLogic admin console is accessible through NGINX path routing with HTTPS protocol");
    for (int i = 0; i < numberOfDomains; i++) {
      verifyAdminServerAccess(true, getNginxLbNodePort("https"), false, "",
          "/" + domainUids.get(i).substring(6) + "console");

      // verify the header 'WL-Proxy-Client-IP' is removed in the admin server log
      // verify the header 'WL-Proxy-SSL: false' is removed in the admin server log
      // verify the header 'WL-Proxy-SSL: true' is added in the admin server log
      verifyHeadersInAdminServerLog(domainUids.get(i) + "-" + ADMIN_SERVER_NAME_BASE,
          domainNamespaces.get(i));
    }
  }

  /**
   * Test verifies multiple WebLogic domains can be loadbalanced by NGINX loadbalancer with TLS path routing rules.
   * Accesses the clusterview application deployed in the WebLogic cluster through NGINX loadbalancer and verifies it
   * is correctly routed to the specific domain cluster.
   */
  @Test
  @DisplayName("Verify NGINX path routing with HTTPS protocol across two domains")
  void testNginxTLSPathRoutingAcrossDomains() {
    // verify NGINX path routing with HTTP protocol across two domains
    logger.info("Verifying NGINX path routing with HTTPS protocol across two domains");
    for (String domainUid : domainUids) {
      verifyClusterLoadbalancing(domainUid, "", "https", getNginxLbNodePort("https"),
          replicaCount, false, "/" + domainUid.substring(6));
    }
  }

  /**
   * Verify multiple WebLogic domains can be loadbalanced by NGINX loadbalancer with host based routing rules.
   * Accesses the clusterview application deployed in the WebLogic cluster through NGINX host routing with HTTP protocol
   * and verifies it is correctly routed to the specific domain cluster identified by the -H host header.
   *
   */
  @Test
  @DisplayName("verify NGINX host routing with HTTP protocol across two domains")
  void testNginxHttpHostRoutingAcrossDomains() {
    // verify NGINX host routing with HTTP protocol
    logger.info("Verifying NGINX host routing with HTTP protocol");
    for (int i = 0; i < numberOfDomains; i++) {
      verifyClusterLoadbalancing(domainUids.get(i),
          domainUids.get(i) + "." + domainNamespaces.get(i) + ".nginx.nonssl.test",
          "http", getNginxLbNodePort("http"), replicaCount, true, "");
    }
  }

  /**
   * Test verifies multiple WebLogic domains can be loadbalanced by NGINX loadbalancer with host based routing rules.
   * Accesses the clusterview application deployed in the WebLogic cluster through NGINX tls host routing with HTTPS
   * protocol and verifies it is correctly routed to the specific domain cluster identified by the -H host header.
   *
   */
  @Test
  @DisplayName("verify NGINX host routing with https protocol across two domains")
  void testNginxHttpsHostRoutingAcrossDomains() {
    // verify NGINX host routing with HTTPS protocol across two domains
    logger.info("Verifying NGINX host routing with HTTPS protocol across two domains");
    for (int i = 0; i < numberOfDomains; i++) {
      verifyClusterLoadbalancing(domainUids.get(i),
          domainUids.get(i) + "." + domainNamespaces.get(i) + ".nginx.ssl.test",
          "https", getNginxLbNodePort("https"), replicaCount, true, "");
    }
  }

  /**
   * Test verifies multiple WebLogic domains can be loadbalanced by NGINX loadbalancer with path routing rules.
   * Accesses the clusterview application deployed in the WebLogic cluster through NGINX loadbalancer and verifies it
   * is correctly routed to the specific domain cluster.
   */
  @Test
  @DisplayName("Verify NGINX path routing with HTTP protocol across two domains")
  void testNginxPathRoutingAcrossDomains() {
    // verify NGINX path routing with HTTP protocol across two domains
    logger.info("Verifying NGINX path routing with HTTP protocol across two domains");
    for (String domainUid : domainUids) {
      verifyClusterLoadbalancing(domainUid, "", "http", getNginxLbNodePort("http"),
          replicaCount, false, "/" + domainUid.substring(6));
    }
  }

  /**
   * Cleanup all the remaining artifacts in default namespace created by the test.
   */
  @AfterAll
  public void tearDownAll() {
    if (System.getenv("SKIP_CLEANUP") == null
        || (System.getenv("SKIP_CLEANUP") != null
        && System.getenv("SKIP_CLEANUP").equalsIgnoreCase("false"))) {

      // uninstall NGINX
      if (nginxHelmParams != null) {
        logger.info("uninstall NGINX with namespace {0}", nginxHelmParams.getNamespace());
        assertThat(uninstallNginx(nginxHelmParams))
            .as("Test uninstallNginx returns true")
            .withFailMessage("uninstallNginx() did not return true")
            .isTrue();
      }
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
   * Get NGINX node port with specified channel name.
   *
   * @param channelName channel name of the NGINX node port, either http or https
   * @return NGINX load balancer node port
   */
  private int getNginxLbNodePort(String channelName) {
    String nginxServiceName = nginxHelmParams.getReleaseName() + "-ingress-nginx-controller";

    return getServiceNodePort(nginxNamespace, nginxServiceName, channelName);
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

    testUntil(
        () -> assertDoesNotThrow(() ->
            getPodLog(podName, namespace, "weblogic-server", null, 120)) != null,
        logger,
        "Getting admin server pod log {0} in namespace {1}",
        podName,
        namespace);

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

  private void buildAndDeployApp() {
    // build the clusterview application
    logger.info("Building clusterview application");
    Path distDir = BuildApplication.buildApplication(Paths.get(APP_DIR, "clusterview"),
        null, null, "dist", defaultNamespace);
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
  }

  private void installIngressController(String lberName) {
      // install and verify Nginx
      logger.info("Installing Nginx controller using helm");
      nginxHelmParams = installAndVerifyNginx(nginxNamespace, 0, 0);

      // create ingress rules with non-tls host routing for NGINX
      createNginxIngressHostRoutingForTwoDomains(false);

      // create ingress rules with tls host routing for NGINX
      createNginxIngressHostRoutingForTwoDomains(true);

      // create ingress rules with path routing for NGINX
      createNginxIngressPathRoutingForTwoDomains();

      // create ingress rules with TLS path routing for NGINX
      createNginxTLSPathRoutingForTwoDomains();
  }
}

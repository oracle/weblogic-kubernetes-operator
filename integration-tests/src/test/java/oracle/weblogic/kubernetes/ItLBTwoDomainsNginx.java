// Copyright (c) 2022, 2024, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.models.V1HTTPIngressPath;
import io.kubernetes.client.openapi.models.V1HTTPIngressRuleValue;
import io.kubernetes.client.openapi.models.V1IngressBackend;
import io.kubernetes.client.openapi.models.V1IngressRule;
import io.kubernetes.client.openapi.models.V1IngressServiceBackend;
import io.kubernetes.client.openapi.models.V1IngressTLS;
import io.kubernetes.client.openapi.models.V1ServiceBackendPort;
import oracle.weblogic.kubernetes.actions.impl.NginxParams;
import oracle.weblogic.kubernetes.annotations.IntegrationTest;
import oracle.weblogic.kubernetes.annotations.Namespaces;
import oracle.weblogic.kubernetes.logging.LoggingFacade;
import oracle.weblogic.kubernetes.utils.ExecCommand;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import static oracle.weblogic.kubernetes.TestConstants.ADMIN_SERVER_NAME_BASE;
import static oracle.weblogic.kubernetes.TestConstants.IT_LBTWODOMAINSNGINX_INGRESS_HTTPS_HOSTPORT;
import static oracle.weblogic.kubernetes.TestConstants.IT_LBTWODOMAINSNGINX_INGRESS_HTTPS_NODEPORT;
import static oracle.weblogic.kubernetes.TestConstants.IT_LBTWODOMAINSNGINX_INGRESS_HTTP_HOSTPORT;
import static oracle.weblogic.kubernetes.TestConstants.IT_LBTWODOMAINSNGINX_INGRESS_HTTP_NODEPORT;
import static oracle.weblogic.kubernetes.TestConstants.K8S_NODEPORT_HOST;
import static oracle.weblogic.kubernetes.TestConstants.KIND_CLUSTER;
import static oracle.weblogic.kubernetes.TestConstants.NGINX_CHART_VERSION;
import static oracle.weblogic.kubernetes.TestConstants.OKE_CLUSTER;
import static oracle.weblogic.kubernetes.TestConstants.OKE_CLUSTER_PRIVATEIP;
import static oracle.weblogic.kubernetes.TestConstants.RESULTS_TEMPFILE_DIR;
import static oracle.weblogic.kubernetes.TestConstants.SKIP_CLEANUP;
import static oracle.weblogic.kubernetes.TestConstants.WEBLOGIC_IMAGE_TAG;
import static oracle.weblogic.kubernetes.TestConstants.WLSIMG_BUILDER;
import static oracle.weblogic.kubernetes.TestConstants.WLSIMG_BUILDER_DEFAULT;
import static oracle.weblogic.kubernetes.actions.TestActions.createIngress;
import static oracle.weblogic.kubernetes.actions.TestActions.deletePersistentVolume;
import static oracle.weblogic.kubernetes.actions.TestActions.deletePersistentVolumeClaim;
import static oracle.weblogic.kubernetes.actions.TestActions.getServiceNodePort;
import static oracle.weblogic.kubernetes.actions.TestActions.listIngresses;
import static oracle.weblogic.kubernetes.actions.TestActions.uninstallNginx;
import static oracle.weblogic.kubernetes.utils.CommonLBTestUtils.buildAndDeployClusterviewApp;
import static oracle.weblogic.kubernetes.utils.CommonLBTestUtils.checkIngressReady;
import static oracle.weblogic.kubernetes.utils.CommonLBTestUtils.createMultipleDomainsSharingPVUsingWlstAndVerify;
import static oracle.weblogic.kubernetes.utils.CommonLBTestUtils.verifyAdminServerAccess;
import static oracle.weblogic.kubernetes.utils.CommonLBTestUtils.verifyClusterLoadbalancing;
import static oracle.weblogic.kubernetes.utils.CommonLBTestUtils.verifyHeadersInAdminServerLog;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.formatIPv6Host;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.getServiceExtIPAddrtOke;
import static oracle.weblogic.kubernetes.utils.ImageUtils.createBaseRepoSecret;
import static oracle.weblogic.kubernetes.utils.LoadBalancerUtils.installAndVerifyNginx;
import static oracle.weblogic.kubernetes.utils.OperatorUtils.installAndVerifyOperator;
import static oracle.weblogic.kubernetes.utils.SecretUtils.createSecretWithTLSCertKey;
import static oracle.weblogic.kubernetes.utils.ThreadSafeLogger.getLogger;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Test a single operator can manage multiple WebLogic domains with a single NGINX fronted loadbalancer.
 * Create two domains using WLST with domain-on-pv type.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("Verify Nginx load balancer handles traffic to two background WebLogic domains")
@IntegrationTest
@Tag("olcne-mrg")
@Tag("kind-parallel")
@Tag("oke-sequential")
class ItLBTwoDomainsNginx {

  private static final int numberOfDomains = 2;
  private static final String wlSecretName = "weblogic-credentials";

  private static List<String> domainUids = new ArrayList<>();
  private static String domainNamespace = null;
  private static String nginxNamespace = null;
  private static NginxParams nginxHelmParams = null;
  private static Path tlsCertFile;
  private static Path tlsKeyFile;
  private static LoggingFacade logger = null;
  private static List<String> pvPvcNamePair = null;

  // domain constants
  private static final int replicaCount = 2;
  private static int MANAGED_SERVER_PORT;
  
  static {
    if (WEBLOGIC_IMAGE_TAG.contains("12")) {
      MANAGED_SERVER_PORT = 7100;
    } else {
      MANAGED_SERVER_PORT = 7001;
    }
  }
    
  private static final int ADMIN_SERVER_PORT = 7001;
  private static final String clusterName = "cluster-1";

  private static String ingressIP = null;

  /**
   * Assigns unique namespaces for operator and domains.
   * Pull WebLogic image if running tests in Kind cluster.
   * Installs operator and create domain.
   *
   * @param namespaces injected by JUnit
   */
  @BeforeAll
  public static void initAll(@Namespaces(3) List<String> namespaces) {
    logger = getLogger();
    logger.info("Assign a unique namespace for operator");
    assertNotNull(namespaces.get(0), "Namespace is null");
    String opNamespace = namespaces.get(0);

    // get unique domain namespaces
    logger.info("Get unique namespaces for WebLogic domain1 and domain2");
    assertNotNull(namespaces.get(1), "Namespace list is null");
    domainNamespace = namespaces.get(1);

    // get a unique Nginx namespace
    logger.info("Assign a unique namespace for Nginx");
    assertNotNull(namespaces.get(1), "Namespace list is null");
    nginxNamespace = namespaces.get(2);

    // set the service account name for the operator
    String opServiceAccount = opNamespace + "-sa";

    // install and verify operator with REST API
    installAndVerifyOperator(opNamespace, opServiceAccount, true, 0, domainNamespace);

    // create pull secrets for WebLogic image when running in non Kind Kubernetes cluster
    // this secret is used only for non-kind cluster
    createBaseRepoSecret(domainNamespace);

    for (int i = 1; i <= numberOfDomains; i++) {
      domainUids.add("wls-nginx-domain-" + i);
    }

    pvPvcNamePair = createMultipleDomainsSharingPVUsingWlstAndVerify(
        domainNamespace, wlSecretName, ItLBTwoDomainsNginx.class.getSimpleName(), numberOfDomains, domainUids,
        replicaCount, clusterName, ADMIN_SERVER_PORT, MANAGED_SERVER_PORT);

    // build and deploy app to be used by all test cases
    buildAndDeployClusterviewApp(domainNamespace, domainUids);

    // install Nginx ingress controller for all test cases using Nginx
    installNginxIngressController();

    String ingressServiceName = nginxHelmParams.getHelmParams().getReleaseName() + "-ingress-nginx-controller";
    ingressIP = getServiceExtIPAddrtOke(ingressServiceName, nginxNamespace) != null
        ? getServiceExtIPAddrtOke(ingressServiceName, nginxNamespace) : formatIPv6Host(K8S_NODEPORT_HOST);
  }

  /**
   *  Verify the WebLogic Administration Console from both domains is accessible through a path routing
   *  based single NGIX LoadBalancer using HTTP protocol.
   */
  @Test
  @DisplayName("Verify WebLogic admin console is accessible through NGINX path routing with HTTPS protocol")
  void testNginxTLSPathRoutingAdminServer() {

    logger.info("Verifying WebLogic admin console is accessible through NGINX path routing with HTTPS protocol");
    for (int i = 0; i < numberOfDomains; i++) {
      verifyAdminServerAccess(true, getNginxLbNodePort("https"), false, "",
          "/" + domainUids.get(i).substring(4) + "console", ingressIP);
      // verify the header 'WL-Proxy-Client-IP' is removed in the admin server log
      // verify the header 'WL-Proxy-SSL: false' is removed in the admin server log
      // verify the header 'WL-Proxy-SSL: true' is added in the admin server log
      verifyHeadersInAdminServerLog(domainUids.get(i) + "-" + ADMIN_SERVER_NAME_BASE,
          domainNamespace);
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
          replicaCount, false, "/" + domainUid.substring(4), ingressIP);
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
          domainUids.get(i) + "." + domainNamespace + ".nginx.nonssl.test",
          "http", getNginxLbNodePort("http"), replicaCount, true, "", ingressIP);
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
          domainUids.get(i) + "." + domainNamespace + ".nginx.ssl.test",
          "https", getNginxLbNodePort("https"), replicaCount, true, "", ingressIP);
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
          replicaCount, false, "/" + domainUid.substring(4), ingressIP);
    }
  }

  /**
   * Delete PV and PVC.
   * @throws ApiException if Kubernetes API call fails
   */
  @AfterAll
  public void tearDownAll() throws ApiException {
    if (!SKIP_CLEANUP) {
      if (pvPvcNamePair != null) {
        // delete pvc
        deletePersistentVolumeClaim(pvPvcNamePair.get(1), domainNamespace);
        // delete pv
        deletePersistentVolume(pvPvcNamePair.get(0));
      }
    }
    if (nginxHelmParams != null && OKE_CLUSTER) {
      assertThat(uninstallNginx(nginxHelmParams.getHelmParams()))
          .as("Test uninstallNginx returns true")
          .withFailMessage("uninstallNginx() did not return true")
          .isTrue();
    }
  }

  private static void createCertKeyFiles(String cn) {
    assertDoesNotThrow(() -> {
      tlsKeyFile = Files.createTempFile(RESULTS_TEMPFILE_DIR, "tls", ".key");
      tlsCertFile = Files.createTempFile(RESULTS_TEMPFILE_DIR, "tls", ".crt");
      String command = "openssl req -x509 -nodes -days 365 -newkey rsa:2048 -keyout " + tlsKeyFile
          + " -out " + tlsCertFile + " -subj \"/CN=" + cn + "\"";
      logger.info("Executing command: {0}", command);
      ExecCommand.exec(command, true);
    });
  }

  private static void createNginxIngressHostRoutingForTwoDomains(String ingressClassName, boolean isTLS) {
    // create an ingress in domain namespace
    String ingressName;

    if (isTLS) {
      ingressName = domainNamespace + "-nginx-tls";
    } else {
      ingressName = domainNamespace + "-nginx-host-routing";
    }

    // create ingress rules for two domains
    List<V1IngressRule> ingressRules = new ArrayList<>();
    List<V1IngressTLS> tlsList = new ArrayList<>();
    for (String domainUid : domainUids) {

    
      V1HTTPIngressPath httpIngressPath = new V1HTTPIngressPath()
          .path(null)
          .pathType("ImplementationSpecific")
          .backend(new V1IngressBackend()
              .service(new V1IngressServiceBackend()
                  .name(domainUid + "-cluster-cluster-1")
                  .port(new V1ServiceBackendPort().number(MANAGED_SERVER_PORT)))
          );

      // set the ingress rule host
      String ingressHost;
      if (isTLS) {
        ingressHost = domainUid + "." + domainNamespace + ".nginx.ssl.test";
      } else {
        ingressHost = domainUid + "." + domainNamespace + ".nginx.nonssl.test";
      }
      V1IngressRule ingressRule = new V1IngressRule()
          .host(ingressHost)
          .http(new V1HTTPIngressRuleValue()
              .paths(Collections.singletonList(httpIngressPath)));

      ingressRules.add(ingressRule);

      if (isTLS) {
        String tlsSecretName = domainUid + "-nginx-tls-secret";
        createCertKeyFiles(ingressHost);
        assertDoesNotThrow(() -> createSecretWithTLSCertKey(tlsSecretName, domainNamespace, tlsKeyFile, tlsCertFile));
        V1IngressTLS tls = new V1IngressTLS()
            .addHostsItem(ingressHost)
            .secretName(tlsSecretName);
        tlsList.add(tls);
      }
    }

    assertDoesNotThrow(() -> createIngress(ingressName, domainNamespace, null,
        ingressClassName, ingressRules, (isTLS ? tlsList : null)));

    // check the ingress was found in the domain namespace
    assertThat(assertDoesNotThrow(() -> listIngresses(domainNamespace)))
        .as(String.format("Test ingress %s was found in namespace %s", ingressName, domainNamespace))
        .withFailMessage(String.format("Ingress %s was not found in namespace %s", ingressName, domainNamespace))
        .contains(ingressName);

    logger.info("ingress {0} was created in namespace {1}", ingressName, domainNamespace);

    // check the ingress is ready to route the app to the server pod
    int httpNodeport = getNginxLbNodePort("http");
    int httpsNodeport = getNginxLbNodePort("https");
    for (String domainUid : domainUids) {
      String ingressHost;
      if (isTLS) {
        ingressHost = domainUid + "." + domainNamespace + ".nginx.ssl.test";
      } else {
        ingressHost = domainUid + "." + domainNamespace + ".nginx.nonssl.test";
      }

      if (!OKE_CLUSTER) {
        checkIngressReady(true, ingressHost, isTLS, httpNodeport, httpsNodeport, "");
      }
    }
  }

  private static void createNginxIngressPathRoutingForTwoDomains() {
    // create an ingress in domain namespace
    String ingressName = domainNamespace + "-nginx-path-routing";

    HashMap<String, String> annotations = new HashMap<>();
    annotations.put("nginx.ingress.kubernetes.io/rewrite-target", "/$1");

    String ingressClassName = nginxHelmParams.getIngressClassName();

    // create ingress rules for two domains
    List<V1IngressRule> ingressRules = new ArrayList<>();
    List<V1HTTPIngressPath> httpIngressPaths = new ArrayList<>();

    for (String domainUid : domainUids) {
      V1HTTPIngressPath httpIngressPath = new V1HTTPIngressPath()
          .path("/" + domainUid.substring(4) + "(.+)")
          .pathType("ImplementationSpecific")
          .backend(new V1IngressBackend()
              .service(new V1IngressServiceBackend()
                  .name(domainUid + "-cluster-cluster-1")
                  .port(new V1ServiceBackendPort()
                      .number(MANAGED_SERVER_PORT)))
          );
      httpIngressPaths.add(httpIngressPath);
    }

    V1IngressRule ingressRule = new V1IngressRule()
        .host("")
        .http(new V1HTTPIngressRuleValue()
            .paths(httpIngressPaths));

    ingressRules.add(ingressRule);

    assertDoesNotThrow(() -> createIngress(ingressName, domainNamespace, annotations,
        ingressClassName, ingressRules, null));

    // check the ingress was found in the domain namespace
    assertThat(assertDoesNotThrow(() -> listIngresses(domainNamespace)))
        .as(String.format("Test ingress %s was found in namespace %s", ingressName, domainNamespace))
        .withFailMessage(String.format("Ingress %s was not found in namespace %s", ingressName, domainNamespace))
        .contains(ingressName);

    logger.info("ingress {0} was created in namespace {1}", ingressName, domainNamespace);

    // check the ingress is ready to route the app to the server pod
    int httpNodeport = getNginxLbNodePort("http");
    for (String domainUid : domainUids) {
      if (!OKE_CLUSTER) {
        checkIngressReady(false, "", false, httpNodeport, -1, domainUid.substring(4));
      }
    }
  }

  private static void createNginxTLSPathRoutingForTwoDomains() {
    // create an ingress in domain namespace
    String ingressName = domainNamespace + "-nginx-tls-pathrouting";

    HashMap<String, String> annotations = new HashMap<>();
    annotations.put("nginx.ingress.kubernetes.io/rewrite-target", "/$1");
    String configurationSnippet =
        new StringBuffer()
        .append("more_clear_input_headers \"WL-Proxy-Client-IP\" \"WL-Proxy-SSL\"; ")
        .append("more_set_input_headers \"X-Forwarded-Proto: https\"; ")
        .append("more_set_input_headers \"WL-Proxy-SSL: true\";")
        .toString();
    annotations.put("nginx.ingress.kubernetes.io/configuration-snippet", configurationSnippet);
    annotations.put("nginx.ingress.kubernetes.io/ingress.allow-http", "false");

    String ingressClassName = nginxHelmParams.getIngressClassName();

    // create ingress rules for two domains
    List<V1IngressRule> ingressRules = new ArrayList<>();
    List<V1HTTPIngressPath> httpIngressPaths = new ArrayList<>();

    for (String domainUid : domainUids) {
      V1HTTPIngressPath httpIngressAdminConsolePath = new V1HTTPIngressPath()
          .path("/" + domainUid.substring(4) + "console(.+)")
          .pathType("ImplementationSpecific")
          .backend(new V1IngressBackend()
              .service(new V1IngressServiceBackend()
                  .name(domainUid + "-" + ADMIN_SERVER_NAME_BASE)
                  .port(new V1ServiceBackendPort()
                      .number(ADMIN_SERVER_PORT)))
          );
      httpIngressPaths.add(httpIngressAdminConsolePath);
      V1HTTPIngressPath httpIngressPath = new V1HTTPIngressPath()
          .path("/" + domainUid.substring(4) + "(.+)")
          .pathType("ImplementationSpecific")
          .backend(new V1IngressBackend()
              .service(new V1IngressServiceBackend()
                  .name(domainUid + "-cluster-cluster-1")
                  .port(new V1ServiceBackendPort()
                      .number(MANAGED_SERVER_PORT)))
          );
      httpIngressPaths.add(httpIngressPath);
    }

    V1IngressRule ingressRule = new V1IngressRule()
        .host("")
        .http(new V1HTTPIngressRuleValue()
            .paths(httpIngressPaths));

    ingressRules.add(ingressRule);

    // create TLS list for the ingress
    List<V1IngressTLS> tlsList = new ArrayList<>();
    String tlsSecretName = domainUids.get(0) + "-nginx-tlspathrouting-secret";
    createCertKeyFiles(domainUids.get(0) + "." + domainNamespace + ".nginx.tlspathrouting.test");
    assertDoesNotThrow(() -> createSecretWithTLSCertKey(tlsSecretName, domainNamespace, tlsKeyFile, tlsCertFile));
    V1IngressTLS tls = new V1IngressTLS()
        .secretName(tlsSecretName);
    tlsList.add(tls);

    assertDoesNotThrow(() -> createIngress(ingressName, domainNamespace, annotations,
        ingressClassName, ingressRules, tlsList));

    // check the ingress was found in the domain namespace
    assertThat(assertDoesNotThrow(() -> listIngresses(domainNamespace)))
        .as(String.format("Test ingress %s was found in namespace %s", ingressName, domainNamespace))
        .withFailMessage(String.format("Ingress %s was not found in namespace %s", ingressName, domainNamespace))
        .contains(ingressName);

    logger.info("ingress {0} was created in namespace {1}", ingressName, domainNamespace);

    // check the ingress is ready to route the app to the server pod
    int httpsNodeport = getNginxLbNodePort("https");
    for (String domainUid : domainUids) {
      if (!OKE_CLUSTER) {
        checkIngressReady(false, "", true, -1, httpsNodeport, domainUid.substring(4));
      }
    }
  }

  /**
   * Get NGINX node port with specified channel name.
   *
   * @param channelName channel name of the NGINX node port, either http or https
   * @return NGINX load balancer node port
   */
  private static int getNginxLbNodePort(String channelName) {
    if (KIND_CLUSTER && !WLSIMG_BUILDER.equals(WLSIMG_BUILDER_DEFAULT)) {
      return channelName.equals("https")
          ? IT_LBTWODOMAINSNGINX_INGRESS_HTTPS_HOSTPORT : IT_LBTWODOMAINSNGINX_INGRESS_HTTP_HOSTPORT;
    } else {
      String nginxServiceName = nginxHelmParams.getHelmParams().getReleaseName() + "-ingress-nginx-controller";
      return getServiceNodePort(nginxNamespace, nginxServiceName, channelName);
    }
  }

  private static void installNginxIngressController() {
    // install and verify Nginx
    logger.info("Installing Nginx controller using helm");
    nginxHelmParams = installNginxLB();

    // create ingress rules with non-tls host routing for NGINX
    createNginxIngressHostRoutingForTwoDomains(nginxHelmParams.getIngressClassName(), false);

    // create ingress rules with tls host routing for NGINX
    createNginxIngressHostRoutingForTwoDomains(nginxHelmParams.getIngressClassName(), true);

    // create ingress rules with path routing for NGINX
    createNginxIngressPathRoutingForTwoDomains();

    // create ingress rules with TLS path routing for NGINX
    createNginxTLSPathRoutingForTwoDomains();
  }

  private static NginxParams installNginxLB() {

    getLogger().info("Installing NGINX in namespace {0}", nginxNamespace);

    String nodePortValue = null;
    if (!OKE_CLUSTER) {
      nodePortValue = "NodePort";
    }

    NginxParams params = null;
    if (OKE_CLUSTER_PRIVATEIP) {
      params = installAndVerifyNginx(nginxNamespace, 0,
          0, NGINX_CHART_VERSION, nodePortValue);
    } else {
      params = installAndVerifyNginx(nginxNamespace, IT_LBTWODOMAINSNGINX_INGRESS_HTTP_NODEPORT,
          IT_LBTWODOMAINSNGINX_INGRESS_HTTPS_NODEPORT, NGINX_CHART_VERSION, nodePortValue);
    }

    return params;
  }
}

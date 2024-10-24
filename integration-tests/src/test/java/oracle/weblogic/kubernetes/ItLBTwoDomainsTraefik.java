// Copyright (c) 2022, 2024, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import io.kubernetes.client.openapi.ApiException;
import oracle.weblogic.kubernetes.actions.ActionConstants;
import oracle.weblogic.kubernetes.actions.impl.primitive.HelmParams;
import oracle.weblogic.kubernetes.annotations.DisabledOnSlimImage;
import oracle.weblogic.kubernetes.annotations.IntegrationTest;
import oracle.weblogic.kubernetes.annotations.Namespaces;
import oracle.weblogic.kubernetes.logging.LoggingFacade;
import oracle.weblogic.kubernetes.utils.ExecCommand;
import oracle.weblogic.kubernetes.utils.ExecResult;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import static oracle.weblogic.kubernetes.TestConstants.K8S_NODEPORT_HOST;
import static oracle.weblogic.kubernetes.TestConstants.KIND_CLUSTER;
import static oracle.weblogic.kubernetes.TestConstants.KUBERNETES_CLI;
import static oracle.weblogic.kubernetes.TestConstants.OCNE;
import static oracle.weblogic.kubernetes.TestConstants.RESULTS_TEMPFILE_DIR;
import static oracle.weblogic.kubernetes.TestConstants.SKIP_CLEANUP;
import static oracle.weblogic.kubernetes.TestConstants.TRAEFIK_INGRESS_HTTPS_HOSTPORT;
import static oracle.weblogic.kubernetes.TestConstants.TRAEFIK_INGRESS_HTTPS_NODEPORT;
import static oracle.weblogic.kubernetes.TestConstants.TRAEFIK_INGRESS_HTTP_HOSTPORT;
import static oracle.weblogic.kubernetes.TestConstants.TRAEFIK_INGRESS_HTTP_NODEPORT;
import static oracle.weblogic.kubernetes.TestConstants.WLSIMG_BUILDER;
import static oracle.weblogic.kubernetes.TestConstants.WLSIMG_BUILDER_DEFAULT;
import static oracle.weblogic.kubernetes.actions.TestActions.deletePersistentVolume;
import static oracle.weblogic.kubernetes.actions.TestActions.deletePersistentVolumeClaim;
import static oracle.weblogic.kubernetes.actions.TestActions.getServiceNodePort;
import static oracle.weblogic.kubernetes.utils.CommonLBTestUtils.buildAndDeployClusterviewApp;
import static oracle.weblogic.kubernetes.utils.CommonLBTestUtils.createMultipleDomainsSharingPVUsingWlstAndVerify;
import static oracle.weblogic.kubernetes.utils.CommonLBTestUtils.verifyAdminServerAccess;
import static oracle.weblogic.kubernetes.utils.CommonLBTestUtils.verifyClusterLoadbalancing;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.getServiceExtIPAddrtOke;
import static oracle.weblogic.kubernetes.utils.ImageUtils.createBaseRepoSecret;
import static oracle.weblogic.kubernetes.utils.LoadBalancerUtils.installAndVerifyTraefik;
import static oracle.weblogic.kubernetes.utils.OperatorUtils.installAndVerifyOperator;
import static oracle.weblogic.kubernetes.utils.SecretUtils.createSecretWithTLSCertKey;
import static oracle.weblogic.kubernetes.utils.ThreadSafeLogger.getLogger;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Test a single operator can manage multiple WebLogic domains with a single Traefik fronted loadbalancer.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("Verify a single operator manages multiple WebLogic domains with a single Traefik fronted loadbalancer")
@IntegrationTest
@Tag("olcne-mrg")
@Tag("kind-parallel")
@Tag("oke-sequential")
class ItLBTwoDomainsTraefik {

  private static final int numberOfDomains = 2;
  private static final String wlSecretName = "weblogic-credentials";

  private static List<String> domainUids = new ArrayList<>();
  private static String domainNamespace = null;
  private static String traefikNamespace = null;
  private static HelmParams traefikHelmParams = null;
  private static Path tlsCertFile;
  private static Path tlsKeyFile;
  private static LoggingFacade logger = null;
  private static List<String> pvPvcNamePair = null;

  // domain constants
  private static final int replicaCount = 2;
  private static final int MANAGED_SERVER_PORT = 7100;
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

    // get a unique Traefik namespace
    logger.info("Assign a unique namespace for Traefik");
    assertNotNull(namespaces.get(1), "Namespace list is null");
    traefikNamespace = namespaces.get(2);

    // set the service account name for the operator
    String opServiceAccount = opNamespace + "-sa";

    // install and verify operator with REST API
    installAndVerifyOperator(opNamespace, opServiceAccount, true, 0, domainNamespace);

    // create pull secrets for WebLogic image when running in non Kind Kubernetes cluster
    // this secret is used only for non-kind cluster
    createBaseRepoSecret(domainNamespace);

    for (int i = 1; i <= numberOfDomains; i++) {
      domainUids.add("wls-traefik-domain-" + i);
    }

    pvPvcNamePair = createMultipleDomainsSharingPVUsingWlstAndVerify(
        domainNamespace, wlSecretName, ItLBTwoDomainsTraefik.class.getSimpleName(), numberOfDomains, domainUids,
        replicaCount, clusterName, ADMIN_SERVER_PORT, MANAGED_SERVER_PORT);

    // build and deploy app to be used by all test cases
    buildAndDeployClusterviewApp(domainNamespace, domainUids);

    // install Traefik ingress controller for all test cases using Traefik
    installTraefikIngressController();

    if (traefikHelmParams != null) {
      String ingressServiceName = traefikHelmParams.getReleaseName();
      ingressIP = getServiceExtIPAddrtOke(ingressServiceName, traefikNamespace) != null
          ? getServiceExtIPAddrtOke(ingressServiceName, traefikNamespace) : K8S_NODEPORT_HOST;
    } else {
      logger.info("traefikHelmParams is null");
      ingressIP = K8S_NODEPORT_HOST;
    }
  }

  /**
   * Verify WebLogic admin console is accessible through Traefik host routing with HTTP protocol.
   */
  @Test
  @DisabledOnSlimImage
  @DisplayName("Verify WebLogic admin console is accessible through Traefik host routing with HTTP protocol")
  void testTraefikHostRoutingAdminServer() {
    logger.info("Verifying WebLogic admin console is accessible through Traefik host routing with HTTP protocol");
    for (String domainUid : domainUids) {
      verifyAdminServerAccess(false, getTraefikLbNodePort(false), true,
          domainUid + "." + domainNamespace + "." + "admin-server" + ".test", "", ingressIP);
    }
  }

  /**
   * Verify multiple WebLogic domains can be loadbalanced by Traefik loadbalancer with host based routing rules.
   * Accesses the clusterview application deployed in the WebLogic cluster through Traefik loadbalancer web
   * channel and verifies it is correctly routed to the specific domain cluster identified by the -H host header.
   *
   */
  @Test
  @DisplayName("Verify Traefik host routing with HTTP protocol across two domains")
  void testTraefikHttpHostRoutingAcrossDomains() {
    // verify Traefik host routing with HTTP protocol across two domains
    logger.info("Verifying Traefik host routing with HTTP protocol across two domains");
    for (String domainUid : domainUids) {
      verifyClusterLoadbalancing(domainUid, domainUid + "." + domainNamespace + ".cluster-1.test",
          "http", getTraefikLbNodePort(false), replicaCount, true, "", ingressIP);
    }
  }

  /**
   * Verify multiple WebLogic domains can be loadbalanced by Traefik loadbalancer with host based routing rules.
   * Accesses the clusterview application deployed in the WebLogic cluster through Traefik loadbalancer websecure
   * channel and verifies it is correctly routed to the specific domain cluster identified by the -H host header.
   */
  @Test
  @DisplayName("Verify Traefik host routing with HTTPS protocol across two domains")
  void testTraefikHttpsHostRoutingAcrossDomains() {
    logger.info("Verifying Traefik host routing with HTTPS protocol across two domains");
    for (String domainUid : domainUids) {
      verifyClusterLoadbalancing(domainUid, domainUid + "." + domainNamespace + ".cluster-1.test",
          "https", getTraefikLbNodePort(true), replicaCount, true, "", ingressIP);
    }
  }

  /**
   * Verify Traefik path routing with HTTP protocol across two domains.
   */
  @Test
  @DisplayName("Verify Traefik path routing with HTTP protocol across two domains")
  void testTraefikPathRoutingAcrossDomains() {
    logger.info("Verifying Traefik path routing with HTTP protocol across two domains");
    for (String domainUid : domainUids) {
      verifyClusterLoadbalancing(domainUid, "", "http", getTraefikLbNodePort(false),
          replicaCount, false, "/" + domainUid.substring(12).replace("-", ""), ingressIP);
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

  private static void installTraefikIngressController() {
    // install and verify Traefik
    logger.info("Installing Traefik controller using helm");
    if (WLSIMG_BUILDER.equals(WLSIMG_BUILDER_DEFAULT)) {
      traefikHelmParams = installAndVerifyTraefik(traefikNamespace, 0, 0).getHelmParams();
    }

    // create TLS secret for Traefik HTTPS traffic
    for (String domainUid : domainUids) {
      createCertKeyFiles(domainUid + "." + domainNamespace + ".cluster-1.test");
      assertDoesNotThrow(() -> createSecretWithTLSCertKey(domainUid + "-traefik-tls-secret",
          domainNamespace, tlsKeyFile, tlsCertFile));
    }

    // create ingress rules with non-tls host routing, tls host routing and path routing for Traefik
    createTraefikIngressRoutingRules(domainNamespace);
  }

  private static void createTraefikIngressRoutingRules(String domainNamespace) {
    logger.info("Creating ingress rules for domain traffic routing");
    Path srcFile = Paths.get(ActionConstants.RESOURCE_DIR, "traefik/traefik-ingress-rules.yaml");
    Path dstFile = Paths.get(TestConstants.RESULTS_ROOT, "traefik/traefik-ingress-rules.yaml");
    assertDoesNotThrow(() -> {
      Files.deleteIfExists(dstFile);
      Files.createDirectories(dstFile.getParent());
      Files.write(dstFile, Files.readString(srcFile).replaceAll("@NS@", domainNamespace)
          .replaceAll("@domain1uid@", domainUids.get(0))
          .replaceAll("@domain2uid@", domainUids.get(1))
          .getBytes(StandardCharsets.UTF_8));
    });
    String command = KUBERNETES_CLI + " create -f " + dstFile;
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

  private int getTraefikLbNodePort(boolean isHttps) {
    if (KIND_CLUSTER && !WLSIMG_BUILDER.equals(WLSIMG_BUILDER_DEFAULT)) {
      return isHttps ? TRAEFIK_INGRESS_HTTPS_HOSTPORT : TRAEFIK_INGRESS_HTTP_HOSTPORT;
    } else if (OCNE && !WLSIMG_BUILDER.equals(WLSIMG_BUILDER_DEFAULT)) {
      return isHttps ? TRAEFIK_INGRESS_HTTPS_NODEPORT : TRAEFIK_INGRESS_HTTP_NODEPORT;
    } else if (traefikHelmParams != null) {
      logger.info("Getting web node port for Traefik loadbalancer {0}", traefikHelmParams.getReleaseName());
      return assertDoesNotThrow(() ->
              getServiceNodePort(traefikNamespace, traefikHelmParams.getReleaseName(), isHttps ? "websecure" : "web"),
          "Getting web node port for Traefik loadbalancer failed");
    } else {
      logger.info("failed to get Traefik Nodeport");
      return -1;
    }
  }
}

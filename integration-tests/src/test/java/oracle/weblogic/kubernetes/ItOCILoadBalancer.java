// Copyright (c) 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.kubernetes.client.openapi.models.V1LoadBalancerIngress;
import io.kubernetes.client.openapi.models.V1Service;
import oracle.weblogic.kubernetes.actions.impl.primitive.Kubernetes;
import oracle.weblogic.kubernetes.annotations.IntegrationTest;
import oracle.weblogic.kubernetes.annotations.Namespaces;
import oracle.weblogic.kubernetes.logging.LoggingFacade;
import org.awaitility.core.ConditionFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static java.util.concurrent.TimeUnit.MINUTES;
import static java.util.concurrent.TimeUnit.SECONDS;
import static oracle.weblogic.kubernetes.TestConstants.ADMIN_SERVER_NAME_BASE;
import static oracle.weblogic.kubernetes.TestConstants.MANAGED_SERVER_NAME_BASE;
import static oracle.weblogic.kubernetes.TestConstants.MII_BASIC_IMAGE_NAME;
import static oracle.weblogic.kubernetes.TestConstants.MII_BASIC_IMAGE_TAG;
import static oracle.weblogic.kubernetes.assertions.impl.Kubernetes.getService;
import static oracle.weblogic.kubernetes.utils.CommonMiiTestUtils.createMiiDomainAndVerify;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.createOcirRepoSecret;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.installAndVerifyOCILoadBalancer;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.installAndVerifyOperator;
import static oracle.weblogic.kubernetes.utils.TestUtils.callWebAppAndCheckForServerNameInResponse;
import static oracle.weblogic.kubernetes.utils.ThreadSafeLogger.getLogger;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.with;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Verify OCI Load Balancer is installed and running.
 * Verify sample-war web application be accessed via OCI LoadBalancer.
 * Verify Load Balancing between two managed servers in the cluster
 */
@DisplayName("Verify the sample-app app can be accessed from "
    + "all managed servers in the domain through OCI Load Balancer")
@IntegrationTest
class ItOCILoadBalancer {
  // domain constants
  private static final int replicaCount = 2;
  private static int managedServersCount = 2;
  private static String domainNamespace = null;
  private static String domainUid = "lboci-domain";
  private static ConditionFactory withStandardRetryPolicy = null;

  // constants for creating domain image using model in image
  private static final String SAMPLE_APP_NAME = "sample-app";
  private static String clusterName = "cluster-1";
  private static LoggingFacade logger = null;
  private static String loadBalancerIP = null;
  private static final String OCI_LB_NAME = "ocilb";

  /**
   * Install and verify operator.
   *
   * @param namespaces list of namespaces created by the IntegrationTestWatcher by the
   *                   JUnit engine parameter resolution mechanism
   */
  @BeforeAll
  public static void initAll(@Namespaces(2) List<String> namespaces) {

    logger = getLogger();
    // create standard, reusable retry/backoff policy
    withStandardRetryPolicy = with().pollDelay(2, SECONDS)
        .and().with().pollInterval(10, SECONDS)
        .atMost(5, MINUTES).await();

    logger.info("Get a unique namespace for operator");
    assertNotNull(namespaces.get(0), "Namespace list is null");
    final String opNamespace = namespaces.get(0);

    logger.info("Get a unique namespace for WebLogic domain1");
    assertNotNull(namespaces.get(1), "Namespace list is null");
    domainNamespace = namespaces.get(1);

    logger.info("install and verify operator");
    installAndVerifyOperator(opNamespace, domainNamespace);
  }

  @AfterAll
  public void tearDownAll() {
    if (System.getenv("SKIP_CLEANUP") == null
        || (System.getenv("SKIP_CLEANUP") != null
        && System.getenv("SKIP_CLEANUP").equalsIgnoreCase("false"))) {
      Kubernetes.deleteService(OCI_LB_NAME, domainNamespace);
    }
  }

  /**
   * Test covers basic functionality for OCI LoadBalancer .
   * Create domain and  OCI LoadBalancer.
   * Check that application is accessabale via OCI LoadBalancer
   */
  @Test
  @DisplayName("Test the sample-app app can be accessed"
      + " from all managed servers in the domain through OCI Load Balancer.")
  public void testOCILoadBalancer() throws Exception {

    // create and verify one cluster mii domain
    logger.info("Create domain and verify that it's running");

    // create docker registry secret to pull the image from registry
    // this secret is used only for non-kind cluster
    logger.info("Create docker registry secret in namespace {0}", domainNamespace);
    createOcirRepoSecret(domainNamespace);
    String adminServerPodName = domainUid + "-" + ADMIN_SERVER_NAME_BASE;
    String managedServerPrefix = domainUid + "-" + MANAGED_SERVER_NAME_BASE;
    createMiiDomainAndVerify(domainNamespace,domainUid,
        MII_BASIC_IMAGE_NAME + ":" + MII_BASIC_IMAGE_TAG, adminServerPodName,
        managedServerPrefix,replicaCount);
    
    int clusterHttpPort = 8001;

    assertDoesNotThrow(() -> installAndVerifyOCILoadBalancer(domainNamespace,
        clusterHttpPort, clusterName, domainUid, OCI_LB_NAME),
        "Installation of OCI Load Balancer failed");
    loadBalancerIP = getLoadBalancerIP(domainNamespace,OCI_LB_NAME);
    assertNotNull(loadBalancerIP, "External IP for Load Balancer is undefined");
    logger.info("LoadBalancer IP is " + loadBalancerIP);
    verifyWebAppAccessThroughOCILoadBalancer(loadBalancerIP, 2, clusterHttpPort);
  }

  /**
   * Retreive external IP from OCI LoadBalancer.
   */
  private static String getLoadBalancerIP(String namespace, String lbName) throws Exception {
    Map<String, String> labels = new HashMap<>();
    labels.put("loadbalancer", lbName);
    V1Service service = getService(lbName, labels, namespace);
    assertNotNull(service, "Can't find service with name " + lbName);
    logger.info("Found service with name {0} in {1} namespace ", lbName, namespace);
    List<V1LoadBalancerIngress> ingress = service.getStatus().getLoadBalancer().getIngress();
    if (ingress != null) {
      logger.info("LoadBalancer Ingress " + ingress.toString());
      V1LoadBalancerIngress lbIng = ingress.stream().filter(c ->
          !c.getIp().equals("pending")
      ).findAny().orElse(null);
      if (lbIng != null) {
        logger.info("OCI LoadBalancer is created with external ip" + lbIng.getIp());
        return lbIng.getIp();
      }
    }
    return null;
  }

  /**
   * Verify the sample-app app can be accessed from all managed servers in the domain through OCI Load Balancer.
   */
  private void verifyWebAppAccessThroughOCILoadBalancer(String lbIp, int replicaCount, int httpport) {

    List<String> managedServerNames = new ArrayList<>();
    for (int i = 1; i <= replicaCount; i++) {
      managedServerNames.add(MANAGED_SERVER_NAME_BASE + i);
    }

    // check that NGINX can access the sample apps from all managed servers in the domain
    String curlCmd =
        String.format("curl --silent --show-error --noproxy '*'  http://%s:%s/sample-war/index.jsp",
            lbIp,
            httpport);
    assertThat(callWebAppAndCheckForServerNameInResponse(curlCmd, managedServerNames, 50))
        .as("Verify OCI LB can access the sample-war app "
            + "from all managed servers in the domain via http")
        .withFailMessage("OCI LB can not access the the sample-war app "
            + "from one or more of the managed servers via http")
        .isTrue();
  }
}
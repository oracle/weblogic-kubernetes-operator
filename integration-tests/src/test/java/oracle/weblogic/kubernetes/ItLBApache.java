// Copyright (c) 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.concurrent.Callable;

import io.kubernetes.client.custom.Quantity;
import io.kubernetes.client.openapi.models.V1ObjectMetaBuilder;
import io.kubernetes.client.openapi.models.V1PersistentVolume;
import io.kubernetes.client.openapi.models.V1PersistentVolumeClaim;
import io.kubernetes.client.openapi.models.V1PersistentVolumeClaimSpec;
import io.kubernetes.client.openapi.models.V1PersistentVolumeSpec;
import io.kubernetes.client.openapi.models.V1ResourceRequirements;
import oracle.weblogic.kubernetes.annotations.IntegrationTest;
import oracle.weblogic.kubernetes.annotations.Namespaces;
import oracle.weblogic.kubernetes.logging.LoggingFacade;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import static java.nio.file.Paths.get;
import static oracle.weblogic.kubernetes.TestConstants.APACHE_IMAGE;
import static oracle.weblogic.kubernetes.TestConstants.APACHE_RELEASE_NAME;
import static oracle.weblogic.kubernetes.TestConstants.KIND_REPO;
import static oracle.weblogic.kubernetes.TestConstants.MII_BASIC_IMAGE_NAME;
import static oracle.weblogic.kubernetes.TestConstants.MII_BASIC_IMAGE_TAG;
import static oracle.weblogic.kubernetes.TestConstants.OCIR_PASSWORD;
import static oracle.weblogic.kubernetes.TestConstants.OCIR_REGISTRY;
import static oracle.weblogic.kubernetes.TestConstants.OCIR_USERNAME;
import static oracle.weblogic.kubernetes.TestConstants.PV_ROOT;
import static oracle.weblogic.kubernetes.actions.TestActions.dockerLogin;
import static oracle.weblogic.kubernetes.actions.TestActions.dockerPull;
import static oracle.weblogic.kubernetes.actions.TestActions.dockerPush;
import static oracle.weblogic.kubernetes.actions.TestActions.dockerTag;
import static oracle.weblogic.kubernetes.actions.TestActions.getServiceNodePort;
import static oracle.weblogic.kubernetes.utils.CommonLBTestUtils.buildAndDeployClusterviewApp;
import static oracle.weblogic.kubernetes.utils.CommonLBTestUtils.createMultipleDomainsSharingPVUsingWlstAndVerify;
import static oracle.weblogic.kubernetes.utils.CommonLBTestUtils.verifyClusterLoadbalancing;
import static oracle.weblogic.kubernetes.utils.CommonMiiTestUtils.createMiiDomainAndVerify;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.testUntil;
import static oracle.weblogic.kubernetes.utils.ImageUtils.createSecretForBaseImages;
import static oracle.weblogic.kubernetes.utils.LoadBalancerUtils.installAndVerifyApache;
import static oracle.weblogic.kubernetes.utils.OKDUtils.createRouteForOKD;
import static oracle.weblogic.kubernetes.utils.OKDUtils.setTlsTerminationForRoute;
import static oracle.weblogic.kubernetes.utils.OperatorUtils.installAndVerifyOperator;
import static oracle.weblogic.kubernetes.utils.PersistentVolumeUtils.createPVPVCAndVerify;
import static oracle.weblogic.kubernetes.utils.ThreadSafeLogger.getLogger;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Test operator manages multiple domains.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("Verify operator manages multiple domains")
@IntegrationTest
class ItLBApache {

  private static final int numberOfDomains = 2;
  private static final String wlSecretName = "weblogic-credentials";
  private static final String apachePvcName = "apache-custom-file-pvc";
  private static final String apachePvName = "apache-custom-file-pv";

  private static List<String> domainUids = new ArrayList<>();
  private static String domainNamespace = null;
  private static String apacheNamespace = null;
  private static LoggingFacade logger = null;
  private static String kindRepoApacheImage = APACHE_IMAGE;

  // domain constants
  private static final int replicaCount = 2;
  private static final int MANAGED_SERVER_PORT = 7100;
  private static final int ADMIN_SERVER_PORT = 7001;
  private static final String clusterName = "cluster-1";

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

    // get a unique Apache namespace
    logger.info("Assign a unique namespace for Apache");
    assertNotNull(namespaces.get(1), "Namespace list is null");
    apacheNamespace = namespaces.get(2);

    // set the service account name for the operator
    String opServiceAccount = opNamespace + "-sa";

    // install and verify operator with REST API
    installAndVerifyOperator(opNamespace, opServiceAccount, true, 0, domainNamespace, apacheNamespace);

    // This test uses the operator restAPI to scale the domain. To do this in OKD cluster,
    // we need to expose the external service as route and set tls termination to  passthrough
    logger.info("Create a route for the operator external service - only for OKD");
    createRouteForOKD("external-weblogic-operator-svc", opNamespace);
    // Patch the route just created to set tls termination to passthrough
    setTlsTerminationForRoute("external-weblogic-operator-svc", opNamespace);

    // create pull secrets for WebLogic image when running in non Kind Kubernetes cluster
    // this secret is used only for non-kind cluster
    createSecretForBaseImages(domainNamespace);

    for (int i = 1; i <= numberOfDomains; i++) {
      domainUids.add("wls-domain" + i);
    }

    if (KIND_REPO != null) {
      // The kind clusters can't pull Apache webtier image from OCIR using the image pull secret.
      // Try the following instead:
      //   1. docker login
      //   2. docker pull
      //   3. docker tag with the KIND_REPO value
      //   4. docker push to KIND_REPO
      testUntil(
          () -> dockerLogin(OCIR_REGISTRY, OCIR_USERNAME, OCIR_PASSWORD),
          logger,
          "docker login to be successful");

      testUntil(
          pullImageFromOcirAndPushToKind(APACHE_IMAGE),
          logger,
          "pullImageFromOcirAndPushToKind for image {0} to be successful",
          APACHE_IMAGE);
    }

    // create one domain in apache namespace for Apache default sample
    createMiiDomainAndVerify(
        apacheNamespace,
        domainUids.get(0),
        MII_BASIC_IMAGE_NAME + ":" + MII_BASIC_IMAGE_TAG,
        domainUids.get(0) + "-admin-server",
        domainUids.get(0) + "-managed-server",
        replicaCount);

    // create two domains for Apache custom sample
    createMultipleDomainsSharingPVUsingWlstAndVerify(
        domainNamespace, wlSecretName, ItLBApache.class.getSimpleName(), numberOfDomains, domainUids,
        replicaCount, clusterName, ADMIN_SERVER_PORT, MANAGED_SERVER_PORT);

    // build and deploy app to be used by Apache custom sample
    buildAndDeployClusterviewApp(domainNamespace, domainUids);
    // build and deploy app to be used by Apache default sample
    buildAndDeployClusterviewApp(apacheNamespace, Collections.singletonList("wls-domain1"));

    // install Apache ingress controller for all test cases using Apache
    installIngressController();
  }

  /**
   * Verify Apache load balancer default sample through HTTP channel.
   * Configure the Apache webtier as a load balancer for a WebLogic domain using the default configuration.
   * It only support HTTP protocol.
   * For details, please see
   * https://github.com/oracle/weblogic-kubernetes-operator/tree/master/kubernetes/samples/charts/apache-samples/default-sample
   */
  @Test
  @DisplayName("verify Apache load balancer default sample through HTTP channel")
  void testApacheLoadBalancingDefaultSample() {
    // verify Apache default sample
    logger.info("Verifying Apache default sample");
    int httpNodePort = getApacheNodePort(apacheNamespace, "http");
    verifyClusterLoadbalancing(domainUids.get(0), "", "http", httpNodePort, replicaCount, false, "/weblogic");
  }

  /**
   * Verify Apache load balancer custom sample through HTTP and HTTPS channel.
   * Configure the Apache webtier as a load balancer for multiple WebLogic domains using a custom configuration.
   * Create a custom Apache plugin configuration file named custom_mod_wl_apache.conf in a directory specified
   * in helm chart parameter volumePath.
   * For more details, please check:
   * https://github.com/oracle/weblogic-kubernetes-operator/tree/master/kubernetes/samples/charts/apache-samples/custom-sample
   */
  @Test
  @DisplayName("verify Apache load balancer custom sample through HTTP and HTTPS channel")
  void testApacheLoadBalancingCustomSample() {
    // verify Apache custom sample
    logger.info("Verifying Apache custom sample");
    for (int i = 1; i <= numberOfDomains; i++) {
      int httpNodePort = getApacheNodePort(domainNamespace, "http");
      verifyClusterLoadbalancing(domainUids.get(i - 1), "", "http", httpNodePort, replicaCount,
          false, "/weblogic" + i);

      int httpsNodePort = getApacheNodePort(domainNamespace, "https");
      verifyClusterLoadbalancing(domainUids.get(i - 1), "", "https", httpsNodePort, replicaCount,
          false, "/weblogic" + i);
    }
  }

  private static void installIngressController() {
    // install and verify Apache for default sample
    logger.info("Installing Apache controller using helm");
    assertDoesNotThrow(() ->
        installAndVerifyApache(apacheNamespace, kindRepoApacheImage, 0, 0, 8001, domainUids.get(0)));

    // install and verify Apache for custom sample
    LinkedHashMap<String, String> clusterNamePortMap = new LinkedHashMap<>();
    for (int i = 0; i < numberOfDomains; i++) {
      clusterNamePortMap.put(domainUids.get(i) + "-cluster-cluster-1", "" + MANAGED_SERVER_PORT);
    }
    createPVPVCForApacheCustomConfiguration(domainNamespace);
    assertDoesNotThrow(() ->
        installAndVerifyApache(domainNamespace, kindRepoApacheImage, 0, 0, MANAGED_SERVER_PORT, domainUids.get(0),
            apachePvcName, "apache-sample-host", ADMIN_SERVER_PORT, clusterNamePortMap));
  }

  private static Callable<Boolean> pullImageFromOcirAndPushToKind(String apacheImage) {
    return (() -> {
      kindRepoApacheImage = KIND_REPO + apacheImage.substring(OCIR_REGISTRY.length() + 1);
      logger.info("pulling image {0} from OCIR, tag it as image {1} and push to KIND repo",
          apacheImage, kindRepoApacheImage);
      return dockerPull(apacheImage) && dockerTag(apacheImage, kindRepoApacheImage) && dockerPush(kindRepoApacheImage);
    });
  }

  /**
   * Create PV and PVC for Apache custom configuration file in specified namespace.
   * @param apacheNamespace namespace in which to create PVC
   */
  private static void createPVPVCForApacheCustomConfiguration(String apacheNamespace) {
    Path pvHostPath = get(PV_ROOT, ItLBApache.class.getSimpleName(), "apache-persistentVolume");

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

  private static int getApacheNodePort(String namespace, String channelName) {
    String apacheServiceName = APACHE_RELEASE_NAME + "-" + namespace.substring(3) + "-apache-webtier";

    // get Apache service NodePort
    int apacheNodePort = assertDoesNotThrow(() ->
            getServiceNodePort(namespace, apacheServiceName, channelName),
        "Getting Apache service NodePort failed");
    logger.info("NodePort for {0} is: {1} :", apacheServiceName, apacheNodePort);
    return apacheNodePort;
  }

}

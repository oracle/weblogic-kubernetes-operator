// Copyright (c) 2021, 2024, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes.utils;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

import io.kubernetes.client.custom.IntOrString;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.models.V1HTTPIngressPath;
import io.kubernetes.client.openapi.models.V1HTTPIngressRuleValue;
import io.kubernetes.client.openapi.models.V1IngressBackend;
import io.kubernetes.client.openapi.models.V1IngressRule;
import io.kubernetes.client.openapi.models.V1IngressServiceBackend;
import io.kubernetes.client.openapi.models.V1IngressTLS;
import io.kubernetes.client.openapi.models.V1LoadBalancerIngress;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1Service;
import io.kubernetes.client.openapi.models.V1ServiceBackendPort;
import io.kubernetes.client.openapi.models.V1ServicePort;
import io.kubernetes.client.openapi.models.V1ServiceSpec;
import oracle.weblogic.kubernetes.TestConstants;
import oracle.weblogic.kubernetes.actions.impl.NginxParams;
import oracle.weblogic.kubernetes.actions.impl.TraefikParams;
import oracle.weblogic.kubernetes.actions.impl.primitive.Command;
import oracle.weblogic.kubernetes.actions.impl.primitive.HelmParams;
import oracle.weblogic.kubernetes.extensions.InitializationTasks;
import oracle.weblogic.kubernetes.logging.LoggingFacade;
import org.jetbrains.annotations.Nullable;

import static oracle.weblogic.kubernetes.TestConstants.ADMIN_SERVER_NAME_BASE;
import static oracle.weblogic.kubernetes.TestConstants.COMPARTMENT_OCID;
import static oracle.weblogic.kubernetes.TestConstants.K8S_NODEPORT_HOST;
import static oracle.weblogic.kubernetes.TestConstants.KUBERNETES_CLI;
import static oracle.weblogic.kubernetes.TestConstants.NGINX_CHART_NAME;
import static oracle.weblogic.kubernetes.TestConstants.NGINX_CHART_VERSION;
import static oracle.weblogic.kubernetes.TestConstants.NGINX_RELEASE_NAME;
import static oracle.weblogic.kubernetes.TestConstants.NGINX_REPO_NAME;
import static oracle.weblogic.kubernetes.TestConstants.NGINX_REPO_URL;
import static oracle.weblogic.kubernetes.TestConstants.OKE_CLUSTER;
import static oracle.weblogic.kubernetes.TestConstants.RESULTS_ROOT;
import static oracle.weblogic.kubernetes.TestConstants.TEST_IMAGES_REPO_SECRET_NAME;
import static oracle.weblogic.kubernetes.TestConstants.TRAEFIK_CHART_NAME;
import static oracle.weblogic.kubernetes.TestConstants.TRAEFIK_RELEASE_NAME;
import static oracle.weblogic.kubernetes.TestConstants.TRAEFIK_REPO_NAME;
import static oracle.weblogic.kubernetes.TestConstants.TRAEFIK_REPO_URL;
import static oracle.weblogic.kubernetes.actions.ActionConstants.RESOURCE_DIR;
import static oracle.weblogic.kubernetes.actions.TestActions.createIngress;
import static oracle.weblogic.kubernetes.actions.TestActions.createService;
import static oracle.weblogic.kubernetes.actions.TestActions.installNginx;
import static oracle.weblogic.kubernetes.actions.TestActions.installTraefik;
import static oracle.weblogic.kubernetes.actions.TestActions.listIngresses;
import static oracle.weblogic.kubernetes.actions.TestActions.upgradeTraefikImage;
import static oracle.weblogic.kubernetes.actions.impl.primitive.Command.defaultCommandParams;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.isHelmReleaseDeployed;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.isNginxReady;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.isOCILoadBalancerReady;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.isTraefikReady;
import static oracle.weblogic.kubernetes.assertions.impl.Kubernetes.getService;
import static oracle.weblogic.kubernetes.utils.ApplicationUtils.callWebAppAndWaitTillReady;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.checkPodReadyAndServiceExists;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.checkServiceExists;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.getHostAndPort;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.testUntil;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.withLongRetryPolicy;
import static oracle.weblogic.kubernetes.utils.ExecCommand.exec;
import static oracle.weblogic.kubernetes.utils.ImageUtils.createTestRepoSecret;
import static oracle.weblogic.kubernetes.utils.ThreadSafeLogger.getLogger;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class LoadBalancerUtils {
  /**
   * Install OCI Load Balancer and wait up to five minutes until the External IP is ready.
   *
   * @param namespace the namespace in which the Noci Load Balancer will be installed
   * @param portshttp the http port of oci load balancer
   * @param clusterName name of WLS cluster
   * @param domainUID domain UID
   * @param loadBalancerName service name for OCI Load Balancer
   */
  public static void installAndVerifyOCILoadBalancer(
      String namespace,
      int portshttp,
      String clusterName,
      String domainUID,
      String loadBalancerName) throws ApiException {
    Map<String, String> annotations = new HashMap<>();
    annotations.put("service.beta.kubernetes.io/oci-load-balancer-shape", "400Mbps");
    Map<String, String> selectors = new HashMap<>();
    Map<String, String> labels = new HashMap<>();
    labels.put("loadbalancer", loadBalancerName);
    selectors.put("weblogic.clusterName", clusterName);
    selectors.put("weblogic.domainUID",domainUID);
    List<V1ServicePort> ports = new ArrayList<>();
    ports.add(new V1ServicePort()
        .name("http")
        .port(portshttp)
        .targetPort(new IntOrString(portshttp))
        .protocol("TCP"));

    V1Service service = new V1Service()
        .metadata(new V1ObjectMeta()
            .name(loadBalancerName)
            .namespace(namespace)
            .labels(labels)
            .annotations(annotations))
        .spec(new V1ServiceSpec()
            .ports(ports)
            .selector(selectors)
            .sessionAffinity("None")
            .type("LoadBalancer"));
    LoggingFacade logger = getLogger();
    assertNotNull(service, "Can't create ocilb service, returns null");
    assertDoesNotThrow(() -> createService(service), "Can't create OCI LoadBalancer service");
    checkServiceExists(loadBalancerName,namespace);

    // wait until the external IP is generated.
    testUntil(
        assertDoesNotThrow(() -> isOCILoadBalancerReady(
          loadBalancerName,
          labels, namespace), "isOCILoadBalancerReady failed with ApiException"),
        logger,
        "external IP to be generated in {0}",
        namespace);
    if (OKE_CLUSTER) {
      testUntil(
          assertDoesNotThrow(() -> isLoadBalancerHealthy(namespace, loadBalancerName),
              "isLoadBalancerHealthy failed with ApiException"),
          logger,
          "OCI LB to be healthy in namespace {0}",
          namespace);
    }
  }

  /**
   * Install NGINX and wait up to five minutes until the NGINX pod is ready.
   *
   * @param nginxNamespace the namespace in which the NGINX will be installed
   * @param nodeportshttp the http nodeport of NGINX
   * @param nodeportshttps the https nodeport of NGINX
   * @return the NGINX Helm installation parameters
   */
  public static NginxParams installAndVerifyNginx(String nginxNamespace,
                                                 int nodeportshttp,
                                                 int nodeportshttps) {
    return installAndVerifyNginx(nginxNamespace, nodeportshttp, nodeportshttps, NGINX_CHART_VERSION, null);
  }

  /**
   * Install NGINX and wait up to five minutes until the NGINX pod is ready.
   *
   * @param nginxNamespace the namespace in which the NGINX will be installed
   * @param nodeportshttp the http nodeport of NGINX
   * @param nodeportshttps the https nodeport of NGINX
   * @param chartVersion the chart version of NGINX
   * @param type type of service LoadBalancer or NodePort
   * @return the NGINX Helm installation parameters
   */
  public static NginxParams installAndVerifyNginx(String nginxNamespace,
                                                 int nodeportshttp,
                                                 int nodeportshttps,
                                                 String chartVersion,
                                                 String type) {
    LoggingFacade logger = getLogger();
    createTestRepoSecret(nginxNamespace);

    // Helm install parameters
    HelmParams nginxHelmParams = new HelmParams()
        .releaseName(NGINX_RELEASE_NAME + "-" + nginxNamespace.substring(3))
        .namespace(nginxNamespace)
        .repoUrl(NGINX_REPO_URL)
        .repoName(NGINX_REPO_NAME)
        .chartName(NGINX_CHART_NAME);

    if (chartVersion != null) {
      nginxHelmParams.chartVersion(chartVersion);
    }

    // NGINX chart values to override
    NginxParams nginxParams = new NginxParams()
        .helmParams(nginxHelmParams);    
    // set secret to pull images from private registry
    nginxParams.imageRepoSecret(TEST_IMAGES_REPO_SECRET_NAME);
    if (nodeportshttp != 0 && nodeportshttps != 0) {
      nginxParams
          .nodePortsHttp(nodeportshttp)
          .nodePortsHttps(nodeportshttps);
    }
    if (type != null) {
      nginxParams.type(type);
    }
    if (K8S_NODEPORT_HOST.contains(":")) {
      nginxParams.ipFamilies(Arrays.asList("IPv6"));
    } else {
      nginxParams.ipFamilies(Arrays.asList("IPv4"));
    }

    // install NGINX
    assertThat(installNginx(nginxParams))
        .as("Test NGINX installation succeeds")
        .withFailMessage("NGINX installation is failed")
        .isTrue();

    // verify that NGINX is installed
    logger.info("Checking NGINX release {0} status in namespace {1}",
        NGINX_RELEASE_NAME, nginxNamespace);
    assertTrue(isHelmReleaseDeployed(NGINX_RELEASE_NAME, nginxNamespace),
        String.format("NGINX release %s is not in deployed status in namespace %s",
            NGINX_RELEASE_NAME, nginxNamespace));
    logger.info("NGINX release {0} status is deployed in namespace {1}",
        NGINX_RELEASE_NAME, nginxNamespace);

    // wait until the NGINX pod is ready.
    testUntil(
        assertDoesNotThrow(() -> isNginxReady(nginxNamespace), "isNginxReady failed with ApiException"),
        logger,
        "NGINX to be ready in namespace {0}",
        nginxNamespace);
    if (OKE_CLUSTER) {
      testUntil(
          assertDoesNotThrow(() -> isOCILoadBalancerReady(
              nginxHelmParams.getReleaseName() + "-ingress-nginx-controller",
              null, nginxNamespace), "isOCILoadBalancerReady failed with ApiException"),
          logger,
          "external IP to be generated in {0}",
          nginxNamespace);
      testUntil(
          assertDoesNotThrow(() -> isLoadBalancerHealthy(nginxNamespace,
                  nginxHelmParams.getReleaseName() + "-ingress-nginx-controller"),
              "isLoadBalancerHealthy failed with ApiException"),
          logger,
          "NGINX LB to be healthy in namespace {0}",
          nginxNamespace);
    }

    return nginxParams;
  }

  /** Install Traefik and wait for up to five minutes for the Traefik pod to be ready.
   *
   * @param traefikNamespace the namespace in which the Traefik ingress controller is installed
   * @param nodeportshttp the web nodeport of Traefik
   * @param nodeportshttps the websecure nodeport of Traefik
   * @return the Traefik Helm installation parameters
   */
  public static TraefikParams installAndVerifyTraefik(String traefikNamespace,
      int nodeportshttp,
      int nodeportshttps) {
    return installAndVerifyTraefik(traefikNamespace, nodeportshttp, nodeportshttps, null);
  }
  
  /** Install Traefik and wait for up to five minutes for the Traefik pod to be ready.
   *
   * @param traefikNamespace the namespace in which the Traefik ingress controller is installed
   * @param nodeportshttp the web nodeport of Traefik
   * @param nodeportshttps the websecure nodeport of Traefik
   * @param type NodePort or LoadBalancer
   * @return the Traefik Helm installation parameters
   */
  public static TraefikParams installAndVerifyTraefik(String traefikNamespace,
                                                   int nodeportshttp,
                                                   int nodeportshttps,
                                                   String type) {
    LoggingFacade logger = getLogger();
    // Helm install parameters
    HelmParams traefikHelmParams = new HelmParams()
        .releaseName(TRAEFIK_RELEASE_NAME + "-" + traefikNamespace.substring(3))
        .namespace(traefikNamespace)
        .repoUrl(TRAEFIK_REPO_URL)
        .repoName(TRAEFIK_REPO_NAME)
        .chartName(TRAEFIK_CHART_NAME);

    // Traefik chart values to override
    TraefikParams traefikParams = new TraefikParams()
        .helmParams(traefikHelmParams);
    traefikParams
        .nodePortsHttp(nodeportshttp)
        .nodePortsHttps(nodeportshttps);
    if (type != null) {
      traefikParams.type(type);
    }
    logger.info("ingressClass name: {0}", traefikParams.getIngressClassName());

    // install Traefik
    assertThat(installTraefik(traefikParams))
        .as("Test Traefik installation succeeds")
        .withFailMessage("Traefik installation is failed")
        .isTrue();

    // verify that Traefik is installed
    logger.info("Checking Traefik release {0} status in namespace {1}",
        TRAEFIK_RELEASE_NAME, traefikNamespace);
    assertTrue(isHelmReleaseDeployed(TRAEFIK_RELEASE_NAME, traefikNamespace),
        String.format("Traefik release %s is not in deployed status in namespace %s",
            TRAEFIK_RELEASE_NAME, traefikNamespace));
    logger.info("Traefik release {0} status is deployed in namespace {1}",
        TRAEFIK_RELEASE_NAME, traefikNamespace);

    // wait until the Traefik pod is ready.
    testUntil(
        assertDoesNotThrow(() -> isTraefikReady(traefikNamespace), "isTraefikReady failed with ApiException"),
        logger,
        "Traefik to be ready in namespace {0}",
        traefikNamespace);
    if (OKE_CLUSTER) {
      testUntil(
          assertDoesNotThrow(() -> isOCILoadBalancerReady(
              traefikHelmParams.getReleaseName(),
              null, traefikNamespace), "isOCILoadBalancerReady failed with ApiException"),
          logger,
          "external IP to be generated in {0}",
          traefikNamespace);
      testUntil(
          assertDoesNotThrow(() -> isLoadBalancerHealthy(traefikNamespace, traefikHelmParams.getReleaseName()),
              "isLoadBalancerHealthy failed with ApiException"),
          logger,
          "Traefik to be healthy in namespace {0}",
          traefikNamespace);
    }
    return traefikParams;
  }

  /**
   * Check lb has healty status.
   *
   * @param namespace in which to check for lb controller
   * @name service name of lb controller
   * @return true if healthy, false otherwise
   */
  public static Callable<Boolean> isLoadBalancerHealthy(String namespace, String name) {
    return () -> checkLoadBalancerHealthy(namespace, name);
  }

  /**
   * Retreive external IP from OCI LoadBalancer.
   *
   * @param namespace - namespace
   * @param lbName -loadbalancer service name
   */
  public static String getLoadBalancerIP(String namespace, String lbName) throws Exception {
    return getLoadBalancerIP(namespace, lbName, false);
  }

  /**
   * Retreive external IP from OCI LoadBalancer.
   *
   * @param namespace - namespace
   * @param lbName -loadbalancer service name
   * @param addLabel search service with label
   */
  public static String getLoadBalancerIP(String namespace, String lbName, boolean addLabel) throws Exception {
    Map<String, String> labels = null;
    if (addLabel) {
      labels = new HashMap<>();
      labels.put("loadbalancer", lbName);
    }
    V1Service service = getService(lbName, labels, namespace);
    assertNotNull(service, "Can't find service with name " + lbName);
    LoggingFacade logger = getLogger();
    logger.info("Found service with name {0} in {1} namespace ", lbName, namespace);
    assertNotNull(service.getStatus(), "service status is null");
    assertNotNull(service.getStatus().getLoadBalancer(), "service loadbalancer is null");
    List<V1LoadBalancerIngress> ingress = service.getStatus().getLoadBalancer().getIngress();
    if (ingress != null) {
      logger.info("LoadBalancer Ingress " + ingress.toString());
      V1LoadBalancerIngress lbIng =
          ingress.stream().filter(c -> c.getIp() != null && !c.getIp().equals("pending")).findAny().orElse(null);
      if (lbIng != null) {
        logger.info("OCI LoadBalancer is created with external ip" + lbIng.getIp());
        return lbIng.getIp();
      }
    }
    return null;
  }

  private static boolean checkLoadBalancerHealthy(String namespace, String lbServiceName) {

    String lbPublicIP = assertDoesNotThrow(() -> getLoadBalancerIP(namespace, lbServiceName));
    InitializationTasks.registerLoadBalancerExternalIP(lbPublicIP);
    LoggingFacade logger = getLogger();
    String testcompartmentid = System.getProperty("wko.it.oci.compartment.ocid");
    logger.info("wko.it.oci.compartment.ocid property " + testcompartmentid);


    final String command = "oci lb load-balancer list --compartment-id "
        + testcompartmentid + " --query \"data[?contains(\\\"ip-addresses\\\"[0].\\\"ip-address\\\", '"
        + lbPublicIP + "')].id | [0]\" --raw-output --all";

    logger.info("Command to retrieve Load Balancer OCID  is: {0} ", command);

    ExecResult result = assertDoesNotThrow(() -> exec(command, true));
    logger.info("The command returned exit value: " + result.exitValue()
        + " command output: " + result.stderr() + "\n" + result.stdout());

    if (result == null || result.exitValue() != 0 || result.stdout() == null) {
      return false;
    }

    // Clean up the string to extract the Load Balancer ID
    String lbOCID = result.stdout().trim();

    boolean isFlexible = isLoadBalancerShapeFlexible(lbOCID);

    if (!isFlexible) {
      logger.info("Updating load balancer shape to flexible");

      final String command2 = "oci lb load-balancer update-load-balancer-shape --load-balancer-id "
          + lbOCID + "  --shape-name flexible  --shape-details"
          + " '{\"minimumBandwidthInMbps\": 10, \"maximumBandwidthInMbps\": 400}'   --force";

      result = assertDoesNotThrow(() -> exec(command2, true));
      logger.info("Command: {}, Exit value: {}, Stdout: {}, Stderr: {}",
          command2, result.exitValue(), result.stdout(), result.stderr());

      if (result == null || result.stdout() == null) {
        return false;
      } else if (result.exitValue() != 0 && !result.stdout().contains("is currently being modified")) {
        return false;
      }

      testUntil(
          assertDoesNotThrow(() -> checkWorkRequestUpdateShapeSucceeded(
              lbOCID), "isOCILoadBalancer work request to update shape is not ready"),
          logger,
          "load balancer shape is updating ");
      testUntil(
          assertDoesNotThrow(() -> checkLoadBalancerShapeFlexible(
              lbOCID), "checkLoadBalancerShape is not flexible "),
          logger,
          "load balancer shape can't be checked, retrying ");
    }

    //check health status
    final String command1 = "oci lb load-balancer-health get --load-balancer-id " + lbOCID;
    logger.info("Command to retrieve Load Balancer health status  is: {0} ", command1);
    result = assertDoesNotThrow(() -> exec(command1, true));
    logger.info("The command returned exit value: " + result.exitValue()
        + " command output: " + result.stderr() + "\n" + result.stdout());
    logger.info("result.stderr: \n{0}", result.stderr());
    if (result == null || result.exitValue() != 0 || result.stdout() == null) {
      return false;
    }

    return result.stdout().contains("OK");

  }

  @Nullable
  private static boolean isLoadBalancerShapeFlexible(String lbOCID) {
    LoggingFacade logger = getLogger();

    final String checkShapeCommand = "oci lb load-balancer get --load-balancer-id "
        + lbOCID + " | jq '.data[\"shape-name\"], .data[\"shape-details\"]'";
    ExecResult result = assertDoesNotThrow(() -> exec(checkShapeCommand, true));
    logger.info("The command " + checkShapeCommand + " returned exit value: " + result.exitValue()
        + " command output: " + result.stderr() + "\n" + result.stdout());
    logger.info("result.stderr: \n{0}", result.stderr());
    if (result == null || result.exitValue() != 0 || result.stdout() == null || !result.stdout().contains("flexible")) {
      return false;
    }
    return true;
  }

  private static Callable<Boolean> checkLoadBalancerShapeFlexible(String loadBalancerOCID) {
    return () -> isLoadBalancerShapeFlexible(loadBalancerOCID);
  }

  /**
   * Check work request status for load balancer.
   * @param loadBalancerOCID - load balancer OCID
   * @return true if succeeded , false over vise.
   */
  public static boolean isWorkRequestUpdateShapeSucceeded(String loadBalancerOCID) {

    LoggingFacade logger = getLogger();
    final String command = "oci lb work-request list --load-balancer-id "
        +  loadBalancerOCID
        + "  --query 'data[?type == `UpdateShape`].{id:id, lifecycleState:\"lifecycle-state\", "
        + "message:message, timeFinished:\"time-finished\"}' "
        + "| jq '.[] | select(.lifecycleState == \"SUCCEEDED\")'";
    ExecResult result = assertDoesNotThrow(() -> exec(command, true));
    logger.info("The command " + command + " returned exit value: " + result.exitValue()
        + " command output: " + result.stderr() + "\n" + result.stdout());
    logger.info("result.stderr: \n{0}", result.stderr());
    if (result == null || result.exitValue() != 0 || result.stdout() == null || result.stderr().contains("ERROR")) {
      return false;
    }
    return true;
  }

  /**
   * Check if lb work request status is succeeded.
   *
   * @param loadBalancerOCID lb ocid
   * @return true if succeeded, false otherwise
   */
  public static Callable<Boolean> checkWorkRequestUpdateShapeSucceeded(String loadBalancerOCID) {
    return () -> isWorkRequestUpdateShapeSucceeded(loadBalancerOCID);
  }

  /** Upgrade Traefik and wait for up to five minutes for the Traefik pod to be ready.
   *
   * @param traefikNamespace the namespace in which the Traefik ingress controller is installed
   * @return the Traefik Helm upgrade parameters
   */
  public static HelmParams upgradeAndVerifyTraefik(String traefikNamespace) {
    LoggingFacade logger = getLogger();
    // Helm install parameters
    HelmParams traefikHelmParams = new HelmParams()
        .releaseName(TRAEFIK_RELEASE_NAME + "-" + traefikNamespace.substring(3))
        .namespace(traefikNamespace)
        .repoUrl(TRAEFIK_REPO_URL)
        .repoName(TRAEFIK_REPO_NAME)
        .chartName(TRAEFIK_CHART_NAME);

    // Traefik chart values to override
    TraefikParams traefikParams = new TraefikParams()
        .helmParams(traefikHelmParams);

    // upgrade Traefik with new image infor
    assertThat(upgradeTraefikImage(traefikParams))
        .as("Test Traefik upgrade succeeds")
        .withFailMessage("Traefik upgrade is failed")
        .isTrue();

    // wait until the Traefik pod is ready.
    testUntil(
        assertDoesNotThrow(() -> isTraefikReady(traefikNamespace), "isTraefikReady failed with ApiException"),
        logger,
        "Traefik to be ready in namespace {0}",
        traefikNamespace);

    return traefikHelmParams;
  }

  /**
   * Create an ingress for the domain with domainUid in the specified namespace.
   *
   * @param domainUid WebLogic domainUid which is backend to the ingress to be created
   * @param domainNamespace WebLogic domain namespace in which the domain exists
   * @param clusterNameMSPortMap the map with key as cluster name and the value as managed server port of the cluster
   * @return list of ingress hosts
   */
  public static List<String> createIngressForDomainAndVerify(String domainUid,
                                                             String domainNamespace,
                                                             Map<String, Integer> clusterNameMSPortMap) {
    return createIngressForDomainAndVerify(domainUid, domainNamespace, 0, clusterNameMSPortMap, true, false, 0);
  }

  /**
   * Create an ingress for the domain with domainUid in the specified namespace.
   *
   * @param domainUid WebLogic domainUid which is backend to the ingress to be created
   * @param domainNamespace WebLogic domain namespace in which the domain exists
   * @param clusterNameMSPortMap the map with key as cluster name and the value as managed server port of the cluster
   * @param setIngressHost set specific host or set it to all
   * @return list of ingress hosts
   */
  public static List<String> createIngressForDomainAndVerify(String domainUid,
                                                             String domainNamespace,
                                                             Map<String, Integer> clusterNameMSPortMap,
                                                             boolean setIngressHost) {

    return createIngressForDomainAndVerify(domainUid, domainNamespace, 0, clusterNameMSPortMap, setIngressHost,
        false, 0);
  }

  /**
   * Create an ingress for the domain with domainUid in the specified namespace.
   *
   * @param domainUid WebLogic domainUid which is backend to the ingress to be created
   * @param domainNamespace WebLogic domain namespace in which the domain exists
   * @param nodeport node port of the ingress controller
   * @param clusterNameMSPortMap the map with key as cluster name and the value as managed server port of the cluster
   * @return list of ingress hosts
   */
  public static List<String> createIngressForDomainAndVerify(String domainUid,
                                                             String domainNamespace,
                                                             int nodeport,
                                                             Map<String, Integer> clusterNameMSPortMap) {

    return createIngressForDomainAndVerify(domainUid, domainNamespace, nodeport, clusterNameMSPortMap, true,
        false, 0);
  }

  /**
   * Create an ingress for the domain with domainUid in the specified namespace.
   *
   * @param domainUid WebLogic domainUid which is backend to the ingress to be created
   * @param domainNamespace WebLogic domain namespace in which the domain exists
   * @param nodeport node port of the ingress controller
   * @param clusterNameMSPortMap the map with key as cluster name and the value as managed server port of the cluster
   * @param setIngressHost if false does not set ingress host
   * @param enableAdminServerRouting enable the ingress rule to admin server
   * @param adminServerPort the port number of admin server pod of the domain
   * @return list of ingress hosts
   */
  public static List<String> createIngressForDomainAndVerify(String domainUid,
                                                             String domainNamespace,
                                                             int nodeport,
                                                             Map<String, Integer> clusterNameMSPortMap,
                                                             boolean setIngressHost,
                                                             boolean enableAdminServerRouting,
                                                             int adminServerPort) {
    return createIngressForDomainAndVerify(domainUid, domainNamespace, nodeport, clusterNameMSPortMap, setIngressHost,
        null, enableAdminServerRouting, adminServerPort);
  }

  /**
   * Create an ingress for the domain with domainUid in the specified namespace.
   *
   * @param domainUid WebLogic domainUid which is backend to the ingress to be created
   * @param domainNamespace WebLogic domain namespace in which the domain exists
   * @param nodeport node port of the ingress controller
   * @param clusterNameMSPortMap the map with key as cluster name and the value as managed server port of the cluster
   * @param setIngressHost if false does not set ingress host
   * @param ingressNginxClass unique name to add in ingress resource
   * @param enableAdminServerRouting enable the ingress rule to admin server
   * @param adminServerPort the port number of admin server pod of the domain
   * @return list of ingress hosts
   */
  public static List<String> createIngressForDomainAndVerify(String domainUid,
                                                             String domainNamespace,
                                                             int nodeport,
                                                             Map<String, Integer> clusterNameMSPortMap,
                                                             boolean setIngressHost,
                                                             String ingressNginxClass,
                                                             boolean enableAdminServerRouting,
                                                             int adminServerPort) {

    LoggingFacade logger = getLogger();
    // create an ingress in domain namespace
    String ingressName = domainUid + "-" + domainNamespace + "-" + ingressNginxClass;

    List<String> ingressHostList =
        createIngress(ingressName, domainNamespace, domainUid, clusterNameMSPortMap, null,
            ingressNginxClass, setIngressHost,
            null, enableAdminServerRouting, adminServerPort);

    assertNotNull(ingressHostList,
        String.format("Ingress creation failed for domain %s in namespace %s", domainUid, domainNamespace));

    // check the ingress was found in the domain namespace
    assertThat(assertDoesNotThrow(() -> listIngresses(domainNamespace)))
        .as(String.format("Test ingress %s was found in namespace %s", ingressName, domainNamespace))
        .withFailMessage(String.format("Ingress %s was not found in namespace %s", ingressName, domainNamespace))
        .contains(ingressName);

    logger.info("ingress {0} for domain {1} was created in namespace {2}",
        ingressName, domainUid, domainNamespace);

    // check that admin server pod is ready and service exists in domain namespace
    String adminServerPodName = domainUid + "-" + ADMIN_SERVER_NAME_BASE;
    logger.info("Checking that admin server pod {0} is ready and service exists in namespace {1}",
        adminServerPodName, domainNamespace);
    checkPodReadyAndServiceExists(adminServerPodName, domainUid, domainNamespace);

    // check the ingress is ready to route the app to the server pod
    if (nodeport != 0) {
      for (String ingressHost : ingressHostList) {
        String host = K8S_NODEPORT_HOST;
        if (host.contains(":")) {
          host = "[" + host + "]";
        }
        String curlCmd = "curl -g --silent --show-error --noproxy '*' -H 'host: " + ingressHost
            + "' http://" + getHostAndPort(host, nodeport)
            + "/weblogic/ready --write-out %{http_code} -o /dev/null";

        logger.info("Executing curl command {0}", curlCmd);
        testUntil(() -> callWebAppAndWaitTillReady(curlCmd, 5),
            logger,
            "ingress is ready to ping the ready app on server {0}",
            K8S_NODEPORT_HOST);
      }
    }

    return ingressHostList;
  }

  /**
   * Creates Nginx Ingress Path Routing for service.
   * @param domainNamespace - domain namespace
   * @param ingressClassName - class name
   * @param serviceName - service name
   * @param servicePort -service port
   * @param hostAndPort - host and port  for url
   */
  public  static void createNginxIngressPathRoutingRules(String domainNamespace,
                                                         String ingressClassName,
                                                         String serviceName,
                                                         int servicePort,
                                                         String hostAndPort) {
    // create an ingress in domain namespace
    String ingressName = domainNamespace + "-nginx-path-routing";

    // create ingress rules for two domains
    List<V1IngressRule> ingressRules = new ArrayList<>();
    List<V1HTTPIngressPath> httpIngressPaths = new ArrayList<>();

    V1HTTPIngressPath httpIngressPath = new V1HTTPIngressPath()
        .path("/")
        .pathType("Prefix")
        .backend(new V1IngressBackend()
            .service(new V1IngressServiceBackend()
                .name(serviceName)
                .port(new V1ServiceBackendPort()
                .number(servicePort)))
        );
    httpIngressPaths.add(httpIngressPath);

    V1IngressRule ingressRule = new V1IngressRule()
        .host("")
        .http(new V1HTTPIngressRuleValue()
            .paths(httpIngressPaths));

    ingressRules.add(ingressRule);

    createIngressAndRetryIfFail(60, false, ingressName, domainNamespace, null, ingressClassName, ingressRules, null);

    // check the ingress was found in the domain namespace
    assertThat(assertDoesNotThrow(() -> listIngresses(domainNamespace)))
        .as(String.format("Test ingress %s was found in namespace %s", ingressName, domainNamespace))
        .withFailMessage(String.format("Ingress %s was not found in namespace %s", ingressName, domainNamespace))
        .contains(ingressName);
    LoggingFacade logger = getLogger();
    logger.info("ingress {0} was created in namespace {1}", ingressName, domainNamespace);

    // check the ingress is ready to route the app to the server pod
    String curlCmd = "curl -g --silent --show-error --noproxy '*' http://" + hostAndPort
        + "/weblogic/ready --write-out %{http_code} -o /dev/null";

    logger.info("Executing curl command {0}", curlCmd);
    testUntil(
        withLongRetryPolicy,
        callWebAppAndWaitTillReady(curlCmd),
        logger,
        "Waiting until Web App available");
    assertTrue(callWebAppAndWaitTillReady(curlCmd, 60));
  }

  /**
   * Create an ingress for the domain with domainUid in the specified namespace.
   *
   * @param domainUid WebLogic domainUid which is backend to the ingress to be created
   * @param domainNamespace WebLogic domain namespace in which the domain exists
   * @param nodeport node port of the ingress controller
   * @param clusterNameMSPortMap the map with key as cluster name and the value as managed server port of the cluster
   * @param setIngressHost if false does not set ingress host
   * @param tlsSecret name of the TLS secret if any
   * @param ingressTraefikClass the ingressClass for Traefik
   * @return list of ingress hosts
   */
  public static List<String> createTraefikIngressForDomainAndVerify(
      String domainUid,
      String domainNamespace,
      int nodeport,
      Map<String, Integer> clusterNameMSPortMap,
      boolean setIngressHost,
      String tlsSecret,
      String ingressTraefikClass) {

    LoggingFacade logger = getLogger();
    // create an ingress in domain namespace
    String ingressName = domainUid + "-" + ingressTraefikClass;

    List<String> ingressHostList =
        createIngress(ingressName, domainNamespace, domainUid,
            clusterNameMSPortMap, null, ingressTraefikClass, setIngressHost, tlsSecret);

    assertNotNull(ingressHostList,
        String.format("Ingress creation failed for domain %s in namespace %s", domainUid, domainNamespace));

    // check the ingress was found in the domain namespace
    assertThat(assertDoesNotThrow(() -> listIngresses(domainNamespace)))
        .as(String.format("Test ingress %s was found in namespace %s", ingressName, domainNamespace))
        .withFailMessage(String.format("Ingress %s was not found in namespace %s", ingressName, domainNamespace))
        .contains(ingressName);

    logger.info("ingress {0} for domain {1} was created in namespace {2}",
        ingressName, domainUid, domainNamespace);

    // check the ingress is ready to route the app to the server pod
    if (nodeport != 0) {
      for (String ingressHost : ingressHostList) {
        String host = K8S_NODEPORT_HOST;
        if (TestConstants.KIND_CLUSTER
            && !TestConstants.WLSIMG_BUILDER.equals(TestConstants.WLSIMG_BUILDER_DEFAULT)) {
          host = "localhost";
        }
        if (host.contains(":")) {
          host = "[" + host + "]";
        }
        String curlCmd = "curl -g --silent --show-error --noproxy '*' -H 'host: " + ingressHost
            + "' http://" + host + ":" + nodeport
            + "/weblogic/ready --write-out %{http_code} -o /dev/null";

        logger.info("Executing curl command {0}", curlCmd);
        assertTrue(callWebAppAndWaitTillReady(curlCmd, 60));
      }
    }

    return ingressHostList;
  }

  /**
   * Create an ingress in specified namespace and retry up to maxRetries times if fail.
   * @param maxRetries max number of retries
   * @param isTLS whether the ingress uses TLS
   * @param ingressName ingress name
   * @param namespace namespace in which the ingress will be created
   * @param annotations annotations of the ingress
   * @param ingressClassName Ingress class name
   * @param ingressRules a list of ingress rules
   * @param tlsList list of ingress tls
   */
  public static void createIngressAndRetryIfFail(int maxRetries,
                                                 boolean isTLS,
                                                 String ingressName,
                                                 String namespace,
                                                 Map<String, String> annotations,
                                                 String ingressClassName,
                                                 List<V1IngressRule> ingressRules,
                                                 List<V1IngressTLS> tlsList) {
    for (int i = 0; i < maxRetries; i++) {
      try {
        if (isTLS) {
          createIngress(ingressName, namespace, annotations, ingressClassName, ingressRules, tlsList);
        } else {
          createIngress(ingressName, namespace, annotations, ingressClassName, ingressRules, null);
        }
        break;
      } catch (ApiException apiEx) {
        try {
          Thread.sleep(5000);
        } catch (InterruptedException ignore) {
          //ignore
        }
      }
    }
  }

  /**
   * Create an ingress Resource.
   *
   * @param domainNamespace namespace of the domain
   * @param traefikNamespace namespace in which the ingress will be created
   * @param ingressResourceFileName ingress resource file path and name
   * @param domainUids uids of the domains
   */
  public static boolean createTraefikIngressRoutingRules(String domainNamespace,
                                                         String traefikNamespace,
                                                         String ingressResourceFileName,
                                                         String... domainUids) {
    LoggingFacade logger = getLogger();
    logger.info("Creating Traefik ingress resource");

    Path dstFile = Paths.get(RESULTS_ROOT, ingressResourceFileName);
    return createTraefikIngressRoutingRules(domainNamespace,
        traefikNamespace,
        ingressResourceFileName,
        dstFile,
        domainUids);
  }

  /**
   * Create an ingress Resource.
   *
   * @param domainNamespace namespace of the domain
   * @param traefikNamespace namespace in which the ingress will be created
   * @param ingressResourceFileName ingress resource file path and name
   * @param ingressResourceFilePath ingress resource file path and name
   * @param domainUids uids of the domains
   */
  public static boolean createTraefikIngressRoutingRules(String domainNamespace,
                                                         String traefikNamespace,
                                                         String ingressResourceFileName,
                                                         Path ingressResourceFilePath,
                                                         String... domainUids) {
    LoggingFacade logger = getLogger();
    logger.info("Creating Traefik ingress resource");

    // prepare Traefik ingress resource file
    Path srcFile = Paths.get(RESOURCE_DIR, ingressResourceFileName);
    Path dstFile = ingressResourceFilePath;

    assertDoesNotThrow(() -> {
      Files.deleteIfExists(dstFile);
      Files.createDirectories(dstFile.getParent());
      String contentOfFile = Files.readString(srcFile);
      for (int i = 1; i <= domainUids.length; i++) {
        Files.write(dstFile, contentOfFile
            .replaceAll("@NS@", domainNamespace)
            .replaceAll("@domain" + i + "uid@", domainUids[i - 1])
            .getBytes(StandardCharsets.UTF_8));
        contentOfFile = Files.readString(dstFile);
      }
    });

    // create Traefik ingress resource
    String createIngressCmd = KUBERNETES_CLI + " create -f " + dstFile;
    logger.info("Command to create Traefik ingress routing rules " + createIngressCmd);
    ExecResult result = assertDoesNotThrow(() -> ExecCommand.exec(createIngressCmd, true),
        String.format("Failed to create Traefik ingress routing rules %s", createIngressCmd));
    assertEquals(0, result.exitValue(),
        String.format("Failed to create Traefik ingress routing rules. Error is %s ", result.stderr()));

    // get Traefik ingress service name
    String  getServiceName = KUBERNETES_CLI + " get services -n " + traefikNamespace + " -o name";
    logger.info("Command to get Traefik ingress service name " + getServiceName);
    result = assertDoesNotThrow(() -> ExecCommand.exec(getServiceName, true),
        String.format("Failed to get Traefik ingress service name %s", getServiceName));
    assertEquals(0, result.exitValue(),
        String.format("Failed to Traefik ingress service name . Error is %s ", result.stderr()));
    String traefikServiceName = result.stdout().trim().split("/")[1];

    // check that Traefik service exists in the Traefik namespace
    logger.info("Checking that Traefik service {0} exists in namespace {1}",
        traefikServiceName, traefikNamespace);
    checkServiceExists(traefikServiceName, traefikNamespace);

    return true;
  }

  /**
   * Checks that ExternalIP LB is created in OKE.
   *
   * @param lbrelname - LB release name
   * @param lbns - LB namespace
   * @throws Exception fails if not generated after MaxIterations number is reached.
   */
  public static String getLbExternalIp(String lbrelname, String lbns) throws Exception {
    LoggingFacade logger = getLogger();

    String cmdip = KUBERNETES_CLI + " get svc --namespace " + lbns
          + " -o jsonpath='{.items[?(@.metadata.name == \"" + lbrelname + "\")]"
          + ".status.loadBalancer.ingress[0].ip}'";

    logger.info("Command to retrieve external IP is: {0} ", cmdip);

    ExecResult result = exec(cmdip, true);
    logger.info("The command returned exit value: " + result.exitValue()
        + " command output: " + result.stderr() + "\n" + result.stdout());

    if (result == null || result.exitValue() != 0 || result.stdout() == null) {
      return null;
    }

    logger.info("LB_PUBLIC_IP is " + result.stdout().trim());

    return result.stdout().trim();
  }

  /**
   * Delete Load Balancer , created in OCI by using provided public IP.
   *
   * @param lbPublicIP public Load Balancer IP
   */
  public static void deleteLoadBalancer(String lbPublicIP) {
    if (lbPublicIP != null && !lbPublicIP.isEmpty()) {
      if (lbPublicIP.startsWith("[") && lbPublicIP.endsWith("]")) {
        // Remove the brackets and return the content inside
        lbPublicIP = lbPublicIP.substring(1, lbPublicIP.length() - 1);
      }
      LoggingFacade logger = getLogger();
      Path deleteLBPath =
          Paths.get(RESOURCE_DIR, "bash-scripts", "delete_loadbalancer.sh");
      String deleteLBScript = deleteLBPath.toString();
      String command =
          String.format("chmod 777 %s ", deleteLBScript);
      logger.info("Delete Load Balancer command {0}", command);
      assertTrue(() -> Command.withParams(
              defaultCommandParams()
                  .command(command)
                  .redirect(true))
          .execute());

      String command1 =
          String.format("%s %s %s ", deleteLBScript, lbPublicIP, COMPARTMENT_OCID);
      logger.info("Delete Load Balancer command {0}", command);
      assertTrue(() -> Command.withParams(
              defaultCommandParams()
                  .command(command1)
                  .redirect(true))
          .execute());
    }
  }
}

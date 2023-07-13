// Copyright (c) 2021, 2023, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes.utils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.kubernetes.client.custom.IntOrString;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.models.V1IngressRule;
import io.kubernetes.client.openapi.models.V1IngressTLS;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1Service;
import io.kubernetes.client.openapi.models.V1ServicePort;
import io.kubernetes.client.openapi.models.V1ServiceSpec;
import oracle.weblogic.kubernetes.actions.impl.NginxParams;
import oracle.weblogic.kubernetes.actions.impl.TraefikParams;
import oracle.weblogic.kubernetes.actions.impl.primitive.HelmParams;
import oracle.weblogic.kubernetes.logging.LoggingFacade;

import static oracle.weblogic.kubernetes.TestConstants.ADMIN_SERVER_NAME_BASE;
import static oracle.weblogic.kubernetes.TestConstants.K8S_NODEPORT_HOST;
import static oracle.weblogic.kubernetes.TestConstants.KUBERNETES_CLI;
import static oracle.weblogic.kubernetes.TestConstants.NGINX_CHART_NAME;
import static oracle.weblogic.kubernetes.TestConstants.NGINX_CHART_VERSION;
import static oracle.weblogic.kubernetes.TestConstants.NGINX_RELEASE_NAME;
import static oracle.weblogic.kubernetes.TestConstants.NGINX_REPO_NAME;
import static oracle.weblogic.kubernetes.TestConstants.NGINX_REPO_URL;
import static oracle.weblogic.kubernetes.TestConstants.TEST_IMAGES_REPO_SECRET_NAME;
import static oracle.weblogic.kubernetes.TestConstants.TRAEFIK_CHART_NAME;
import static oracle.weblogic.kubernetes.TestConstants.TRAEFIK_RELEASE_NAME;
import static oracle.weblogic.kubernetes.TestConstants.TRAEFIK_REPO_NAME;
import static oracle.weblogic.kubernetes.TestConstants.TRAEFIK_REPO_URL;
import static oracle.weblogic.kubernetes.actions.TestActions.createIngress;
import static oracle.weblogic.kubernetes.actions.TestActions.createService;
import static oracle.weblogic.kubernetes.actions.TestActions.installNginx;
import static oracle.weblogic.kubernetes.actions.TestActions.installTraefik;
import static oracle.weblogic.kubernetes.actions.TestActions.listIngresses;
import static oracle.weblogic.kubernetes.actions.TestActions.upgradeTraefikImage;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.isHelmReleaseDeployed;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.isNginxReady;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.isOCILoadBalancerReady;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.isTraefikReady;
import static oracle.weblogic.kubernetes.utils.ApplicationUtils.callWebAppAndWaitTillReady;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.checkPodReadyAndServiceExists;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.checkServiceExists;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.testUntil;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.verifyCommandResultContainsMsg;
import static oracle.weblogic.kubernetes.utils.ExecCommand.exec;
import static oracle.weblogic.kubernetes.utils.ImageUtils.createTestRepoSecret;
import static oracle.weblogic.kubernetes.utils.ThreadSafeLogger.getLogger;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
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
    return installAndVerifyNginx(nginxNamespace, nodeportshttp, nodeportshttps, NGINX_CHART_VERSION);
  }

  /**
   * Install NGINX and wait up to five minutes until the NGINX pod is ready.
   *
   * @param nginxNamespace the namespace in which the NGINX will be installed
   * @param nodeportshttp the http nodeport of NGINX
   * @param nodeportshttps the https nodeport of NGINX
   * @param chartVersion the chart version of NGINX
   * @return the NGINX Helm installation parameters
   */
  public static NginxParams installAndVerifyNginx(String nginxNamespace,
                                                 int nodeportshttp,
                                                 int nodeportshttps,
                                                 String chartVersion) {
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

    return nginxParams;
  }

  /** Install Traefik and wait for up to five minutes for the Traefik pod to be ready.
   *
   * @param traefikNamespace the namespace in which the Traefik ingress controller is installed
   * @param nodeportshttp the web nodeport of Traefik
   * @param nodeportshttps the websecure nodeport of Traefik
   * @return the Traefik Helm installation parameters
   */
  public static HelmParams installAndVerifyTraefik(String traefikNamespace,
                                                   int nodeportshttp,
                                                   int nodeportshttps) {
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

    return traefikHelmParams;
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
        String curlCmd = "curl --silent --show-error --noproxy '*' -H 'host: " + ingressHost
            + "' http://" + K8S_NODEPORT_HOST + ":" + nodeport
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
        String curlCmd = "curl --silent --show-error --noproxy '*' -H 'host: " + ingressHost
            + "' http://" + K8S_NODEPORT_HOST + ":" + nodeport
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
   * Checks that ExternalIP LB is created in OKE.
   *
   * @param lbrelname - LB release name
   * @param lbns - LB namespace
   * @throws Exception fails if not generated after MaxIterations number is reached.
   */
  public static String getLbExternalIp(String lbrelname, String lbns) throws Exception {
    int i = 0;
    LoggingFacade logger = getLogger();
    StringBuffer cmd = new StringBuffer();
    cmd.append(KUBERNETES_CLI + " get svc ")
        .append(lbrelname)
        .append(" --namespace ")
        .append(lbns)
        .append(" -w ");
    verifyCommandResultContainsMsg(cmd.toString(), "running");


    String cmdip = KUBERNETES_CLI + " get svc --namespace " + lbns
          + " -o jsonpath='{.items[?(@.metadata.name == \"" + lbrelname + "\")]"
          + ".status.loadBalancer.ingress[0].ip}'";
    ExecResult result = exec(cmdip, true);
    logger.info("The command returned exit value: " + result.exitValue()
        + " command output: " + result.stderr() + "\n" + result.stdout());

    if (result == null || result.exitValue() != 0 || result.stdout() == null) {
      return null;
    }
    logger.info(" LB_PUBLIC_IP = " + result.stdout().trim());
    return result.stdout().trim();
  }
}

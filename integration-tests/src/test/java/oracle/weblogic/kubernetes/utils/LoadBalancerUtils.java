// Copyright (c) 2021, 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes.utils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

import io.kubernetes.client.custom.IntOrString;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.models.V1IngressRule;
import io.kubernetes.client.openapi.models.V1IngressTLS;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1PersistentVolume;
import io.kubernetes.client.openapi.models.V1PersistentVolumeClaim;
import io.kubernetes.client.openapi.models.V1Service;
import io.kubernetes.client.openapi.models.V1ServicePort;
import io.kubernetes.client.openapi.models.V1ServiceSpec;
import oracle.weblogic.kubernetes.actions.impl.ApacheParams;
import oracle.weblogic.kubernetes.actions.impl.NginxParams;
import oracle.weblogic.kubernetes.actions.impl.TraefikParams;
import oracle.weblogic.kubernetes.actions.impl.VoyagerParams;
import oracle.weblogic.kubernetes.actions.impl.primitive.Command;
import oracle.weblogic.kubernetes.actions.impl.primitive.CommandParams;
import oracle.weblogic.kubernetes.actions.impl.primitive.HelmParams;
import oracle.weblogic.kubernetes.logging.LoggingFacade;

import static oracle.weblogic.kubernetes.TestConstants.APACHE_RELEASE_NAME;
import static oracle.weblogic.kubernetes.TestConstants.APACHE_SAMPLE_CHART_DIR;
import static oracle.weblogic.kubernetes.TestConstants.APPSCODE_REPO_NAME;
import static oracle.weblogic.kubernetes.TestConstants.APPSCODE_REPO_URL;
import static oracle.weblogic.kubernetes.TestConstants.GCR_NGINX_IMAGE_NAME;
import static oracle.weblogic.kubernetes.TestConstants.IMAGE_PULL_POLICY;
import static oracle.weblogic.kubernetes.TestConstants.K8S_NODEPORT_HOST;
import static oracle.weblogic.kubernetes.TestConstants.NGINX_CHART_NAME;
import static oracle.weblogic.kubernetes.TestConstants.NGINX_CHART_VERSION;
import static oracle.weblogic.kubernetes.TestConstants.NGINX_RELEASE_NAME;
import static oracle.weblogic.kubernetes.TestConstants.NGINX_REPO_NAME;
import static oracle.weblogic.kubernetes.TestConstants.NGINX_REPO_URL;
import static oracle.weblogic.kubernetes.TestConstants.OCIR_EMAIL;
import static oracle.weblogic.kubernetes.TestConstants.OCIR_PASSWORD;
import static oracle.weblogic.kubernetes.TestConstants.OCIR_REGISTRY;
import static oracle.weblogic.kubernetes.TestConstants.OCIR_SECRET_NAME;
import static oracle.weblogic.kubernetes.TestConstants.OCIR_USERNAME;
import static oracle.weblogic.kubernetes.TestConstants.RESULTS_ROOT;
import static oracle.weblogic.kubernetes.TestConstants.TRAEFIK_CHART_NAME;
import static oracle.weblogic.kubernetes.TestConstants.TRAEFIK_RELEASE_NAME;
import static oracle.weblogic.kubernetes.TestConstants.TRAEFIK_REPO_NAME;
import static oracle.weblogic.kubernetes.TestConstants.TRAEFIK_REPO_URL;
import static oracle.weblogic.kubernetes.TestConstants.VOYAGER_CHART_NAME;
import static oracle.weblogic.kubernetes.TestConstants.VOYAGER_CHART_VERSION;
import static oracle.weblogic.kubernetes.TestConstants.VOYAGER_RELEASE_NAME;
import static oracle.weblogic.kubernetes.actions.TestActions.createIngress;
import static oracle.weblogic.kubernetes.actions.TestActions.createService;
import static oracle.weblogic.kubernetes.actions.TestActions.dockerPull;
import static oracle.weblogic.kubernetes.actions.TestActions.dockerTag;
import static oracle.weblogic.kubernetes.actions.TestActions.getPersistentVolume;
import static oracle.weblogic.kubernetes.actions.TestActions.getPersistentVolumeClaim;
import static oracle.weblogic.kubernetes.actions.TestActions.getServiceNodePort;
import static oracle.weblogic.kubernetes.actions.TestActions.installApache;
import static oracle.weblogic.kubernetes.actions.TestActions.installNginx;
import static oracle.weblogic.kubernetes.actions.TestActions.installTraefik;
import static oracle.weblogic.kubernetes.actions.TestActions.installVoyager;
import static oracle.weblogic.kubernetes.actions.TestActions.listIngresses;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.isApacheReady;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.isHelmReleaseDeployed;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.isNginxReady;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.isOCILoadBalancerReady;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.isTraefikReady;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.isVoyagerReady;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.secretExists;
import static oracle.weblogic.kubernetes.utils.ApplicationUtils.callWebAppAndWaitTillReady;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.checkServiceExists;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.testUntil;
import static oracle.weblogic.kubernetes.utils.ImageUtils.createDockerRegistrySecret;
import static oracle.weblogic.kubernetes.utils.ImageUtils.createOcirRepoSecret;
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
        .protocol(V1ServicePort.ProtocolEnum.TCP));

    V1Service service = new V1Service()
        .metadata(new V1ObjectMeta()
            .name(loadBalancerName)
            .namespace(namespace)
            .labels(labels)
            .annotations(annotations))
        .spec(new V1ServiceSpec()
            .ports(ports)
            .selector(selectors)
            .sessionAffinity(V1ServiceSpec.SessionAffinityEnum.NONE)
            .type(V1ServiceSpec.TypeEnum.LOADBALANCER));
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
    createDockerRegistrySecret(OCIR_USERNAME, OCIR_PASSWORD, OCIR_EMAIL,
        OCIR_REGISTRY, OCIR_SECRET_NAME, nginxNamespace);
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
    nginxParams.imageRepoSecret(OCIR_SECRET_NAME);
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

  /**
   * Install Voyager and wait up to five minutes until the Voyager pod is ready.
   *
   * @param voyagerNamespace the namespace in which the Voyager will be installed
   * @param cloudProvider the name of bare metal Kubernetes cluster
   * @param enableValidatingWebhook whehter to enable validating webhook or not
   * @return the Voyager Helm installation parameters
   */
  public static HelmParams installAndVerifyVoyager(String voyagerNamespace,
                                                   String cloudProvider,
                                                   boolean enableValidatingWebhook) {
    LoggingFacade logger = getLogger();
    final String voyagerPodNamePrefix = VOYAGER_CHART_NAME +  "-release";

    // Helm install parameters
    HelmParams voyagerHelmParams = new HelmParams()
        .releaseName(VOYAGER_RELEASE_NAME + "-" + voyagerNamespace.substring(3))
        .namespace(voyagerNamespace)
        .repoUrl(APPSCODE_REPO_URL)
        .repoName(APPSCODE_REPO_NAME)
        .chartName(VOYAGER_CHART_NAME)
        .chartVersion(VOYAGER_CHART_VERSION);

    // Voyager chart values to override
    VoyagerParams voyagerParams = new VoyagerParams()
        .helmParams(voyagerHelmParams)
        .cloudProvider(cloudProvider)
        .enableValidatingWebhook(enableValidatingWebhook);

    // install Voyager
    assertThat(installVoyager(voyagerParams))
        .as("Test Voyager installation succeeds")
        .withFailMessage("Voyager installation is failed")
        .isTrue();

    // verify that Voyager is installed
    logger.info("Checking Voyager release {0} status in namespace {1}",
        VOYAGER_RELEASE_NAME, voyagerNamespace);
    assertTrue(isHelmReleaseDeployed(VOYAGER_RELEASE_NAME, voyagerNamespace),
        String.format("Voyager release %s is not in deployed status in namespace %s",
            VOYAGER_RELEASE_NAME, voyagerNamespace));
    logger.info("Voyager release {0} status is deployed in namespace {1}",
        VOYAGER_RELEASE_NAME, voyagerNamespace);

    // wait until the Voyager pod is ready.
    testUntil(
        assertDoesNotThrow(() -> isVoyagerReady(voyagerNamespace, voyagerPodNamePrefix),
          "isVoyagerReady failed with ApiException"),
        logger,
        "Voyager to be ready in namespace {0}",
        voyagerNamespace);

    return voyagerHelmParams;
  }

  /**
   * Install Apache and wait up to five minutes until the Apache pod is ready.
   *
   * @param apacheNamespace the namespace in which the Apache will be installed
   * @param image the image name of Apache webtier
   * @param httpNodePort the http nodeport of Apache
   * @param httpsNodePort the https nodeport of Apache
   * @param managedServerPort the listenport of each managed server in cluster
   * @param domainUid the uid of the domain to which Apache will route the services
   * @return the Apache Helm installation parameters
   */
  public static HelmParams installAndVerifyApache(String apacheNamespace,
                                                  String image,
                                                  int httpNodePort,
                                                  int httpsNodePort,
                                                  int managedServerPort,
                                                  String domainUid) throws IOException {
    return installAndVerifyApache(apacheNamespace, image, httpNodePort, httpsNodePort, managedServerPort, domainUid,
        null, null, 0, null);
  }

  /**
   * Install Apache and wait up to five minutes until the Apache pod is ready.
   *
   * @param apacheNamespace the namespace in which the Apache will be installed
   * @param image the image name of Apache webtier
   * @param httpNodePort the http nodeport of Apache
   * @param httpsNodePort the https nodeport of Apache
   * @param managedServerPort the listenport of each managed server in cluster
   * @param domainUid the uid of the domain to which Apache will route the services
   * @param pvcName name of the Persistent Volume Claim which contains your own custom_mod_wl_apache.conf file
   * @param virtualHostName the VirtualHostName of the Apache HTTP server which is used to enable custom SSL config
   * @param adminServerPort admin server port
   * @param clusterNamePortMap the map with clusterName as key and cluster port number as value
   * @return the Apache Helm installation parameters
   */
  public static HelmParams installAndVerifyApache(String apacheNamespace,
                                                  String image,
                                                  int httpNodePort,
                                                  int httpsNodePort,
                                                  int managedServerPort,
                                                  String domainUid,
                                                  String pvcName,
                                                  String virtualHostName,
                                                  int adminServerPort,
                                                  LinkedHashMap<String, String> clusterNamePortMap)
      throws IOException {

    LoggingFacade logger = getLogger();

    // Create Docker registry secret in the apache namespace to pull the Apache webtier image from repository
    // this secret is used only for non-kind cluster
    if (!secretExists(OCIR_SECRET_NAME, apacheNamespace)) {
      logger.info("Creating Docker registry secret in namespace {0}", apacheNamespace);
      createOcirRepoSecret(apacheNamespace);
    }

    // map with secret
    Map<String, Object> secretNameMap = new HashMap<>();
    secretNameMap.put("name", OCIR_SECRET_NAME);

    // Helm install parameters
    HelmParams apacheHelmParams = new HelmParams()
        .releaseName(APACHE_RELEASE_NAME + "-" + apacheNamespace.substring(3))
        .namespace(apacheNamespace)
        .chartDir(APACHE_SAMPLE_CHART_DIR);

    // Apache chart values to override
    ApacheParams apacheParams = new ApacheParams()
        .helmParams(apacheHelmParams)
        .imagePullSecrets(secretNameMap)
        .image(image)
        .imagePullPolicy(IMAGE_PULL_POLICY)
        .domainUID(domainUid);

    if (httpNodePort >= 0 && httpsNodePort >= 0) {
      apacheParams
          .httpNodePort(httpNodePort)
          .httpsNodePort(httpsNodePort);
    }
    if (managedServerPort >= 0) {
      apacheParams.managedServerPort(managedServerPort);
    }

    if (pvcName != null && clusterNamePortMap != null) {
      // create a custom Apache plugin configuration file named custom_mod_wl_apache.conf
      // and put it under the directory specified in pv hostPath
      // this file provides a custom Apache plugin configuration to fine tune the behavior of Apache
      V1PersistentVolumeClaim v1pvc = getPersistentVolumeClaim(apacheNamespace, pvcName);
      assertNotNull(v1pvc);
      assertNotNull(v1pvc.getSpec());
      String pvName = v1pvc.getSpec().getVolumeName();
      logger.info("Got PV {0} from PVC {1} in namespace {2}", pvName, pvcName, apacheNamespace);

      V1PersistentVolume v1pv = getPersistentVolume(pvName);
      assertNotNull(v1pv);
      assertNotNull(v1pv.getSpec());
      assertNotNull(v1pv.getSpec().getHostPath());
      String volumePath = v1pv.getSpec().getHostPath().getPath();
      logger.info("hostPath of the PV {0} is {1}", pvName, volumePath);

      Path customConf = Paths.get(volumePath, "custom_mod_wl_apache.conf");
      ArrayList<String> lines = new ArrayList<>();
      lines.add("<IfModule mod_weblogic.c>");
      lines.add("WebLogicHost " + domainUid + "-admin-server");
      lines.add("WebLogicPort " + adminServerPort);
      lines.add("</IfModule>");

      // Directive for weblogic admin Console deployed on Weblogic Admin Server
      lines.add("<Location /console>");
      lines.add("SetHandler weblogic-handler");
      lines.add("WebLogicHost " + domainUid + "-admin-server");
      lines.add("WebLogicPort " + adminServerPort);
      lines.add("</Location>");

      // Directive for all application deployed on weblogic cluster with a prepath defined by LOCATION variable
      // For example, if the LOCAITON is set to '/weblogic1', all applications deployed on the cluster can be accessed
      // via http://myhost:myport/weblogic1/application_end_url
      // where 'myhost' is the IP of the machine that runs the Apache web tier, and
      //       'myport' is the port that the Apache web tier is publicly exposed to.
      // Note that LOCATION cannot be set to '/' unless this is the only Location module configured.
      // create a LOCATION variable for each domain cluster
      int i = 1;
      for (String clusterName : clusterNamePortMap.keySet()) {
        lines.add("<Location /weblogic" + i + ">");
        lines.add("WLSRequest On");
        lines.add("WebLogicCluster " + clusterName + ":" + clusterNamePortMap.get(clusterName));
        lines.add("PathTrim /weblogic" + i);
        lines.add("</Location>");
        i++;
      }

      try {
        Files.write(customConf, lines);
      } catch (IOException ioex) {
        logger.info("Got IOException while write to a file");
        throw ioex;
      }

      apacheParams.pvcName(pvcName);
    }

    if (virtualHostName != null) {
      // create the certificate and private key
      String certFile = RESULTS_ROOT + "/apache-sample.crt";
      String keyFile = RESULTS_ROOT + "/apache-sample.key";

      Map<String, String> envs = new HashMap<>();
      envs.put("VIRTUAL_HOST_NAME", virtualHostName);
      envs.put("SSL_CERT_FILE", certFile);
      envs.put("SSL_CERT_KEY_FILE", keyFile);

      String command = "sh ../kubernetes/samples/charts/apache-samples/custom-sample/certgen.sh";
      CommandParams params = Command
          .defaultCommandParams()
          .command(command)
          .env(envs)
          .saveResults(true)
          .redirect(true);

      Command.withParams(params).execute();

      String customCert = Base64.getEncoder().encodeToString(Files.readAllBytes(Paths.get(certFile)));
      String customKey = Base64.getEncoder().encodeToString(Files.readAllBytes(Paths.get(keyFile)));

      // set the Apache helm install parameters
      apacheParams.virtualHostName(virtualHostName);
      apacheParams.customCert(customCert);
      apacheParams.customKey(customKey);
    }

    // install Apache
    assertThat(installApache(apacheParams))
        .as("Test Apache installation succeeds")
        .withFailMessage("Apache installation is failed")
        .isTrue();

    // verify that Apache is installed
    logger.info("Checking Apache release {0} status in namespace {1}",
        APACHE_RELEASE_NAME + "-" + apacheNamespace.substring(3), apacheNamespace);
    assertTrue(isHelmReleaseDeployed(APACHE_RELEASE_NAME + "-" + apacheNamespace.substring(3), apacheNamespace),
        String.format("Apache release %s is not in deployed status in namespace %s",
            APACHE_RELEASE_NAME + "-" + apacheNamespace.substring(3), apacheNamespace));
    logger.info("Apache release {0} status is deployed in namespace {1}",
        APACHE_RELEASE_NAME + "-" + apacheNamespace.substring(3), apacheNamespace);

    // wait until the Apache pod is ready.
    testUntil(
        assertDoesNotThrow(() -> isApacheReady(apacheNamespace), "isApacheReady failed with ApiException"),
        logger,
        "Apache to be ready in namespace {0}",
        apacheNamespace);

    return apacheHelmParams;
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
   * Create an ingress for the domain with domainUid in the specified namespace.
   *
   * @param domainUid WebLogic domainUid which is backend to the ingress to be created
   * @param domainNamespace WebLogic domain namespace in which the domain exists
   * @param nodeport node port of the ingress controller
   * @param clusterNameMSPortMap the map with key as cluster name and the value as managed server port of the cluster
   * @param setIngressHost if false does not set ingress host
   * @param tlsSecret name of the TLS secret if any
   * @return list of ingress hosts
   */
  public static List<String> createTraefikIngressForDomainAndVerify(
      String domainUid,
      String domainNamespace,
      int nodeport,
      Map<String, Integer> clusterNameMSPortMap,
      boolean setIngressHost,
      String tlsSecret) {

    LoggingFacade logger = getLogger();
    // create an ingress in domain namespace
    final String ingressTraefikClass = null;
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
   * Create an ingress for the domain with domainUid in a given namespace and verify.
   *
   * @param domainUid WebLogic domainUid which is backend to the ingress to be created
   * @param domainNamespace WebLogic domain namespace in which the domain exists
   * @param ingressName name of ingress to be created in a given domain
   * @param clusterNameMSPortMap the map with key as cluster name and the value as managed server port of the cluster
   * @return list of ingress hosts
   */
  public static List<String> installVoyagerIngressAndVerify(String domainUid,
                                                            String domainNamespace,
                                                            String ingressName,
                                                            Map<String, Integer> clusterNameMSPortMap) {
    return installVoyagerIngressAndVerify(domainUid, domainNamespace, ingressName, clusterNameMSPortMap, null);
  }

  /**
   * Create an ingress for the domain with domainUid in a given namespace and verify.
   *
   * @param domainUid WebLogic domainUid which is backend to the ingress to be created
   * @param domainNamespace WebLogic domain namespace in which the domain exists
   * @param ingressName name of ingress to be created in a given domain
   * @param clusterNameMSPortMap the map with key as cluster name and the value as managed server port of the cluster
   * @param tlsSecret the secret name for TLS
   * @return list of ingress hosts
   */
  public static List<String> installVoyagerIngressAndVerify(String domainUid,
                                                            String domainNamespace,
                                                            String ingressName,
                                                            Map<String, Integer> clusterNameMSPortMap,
                                                            String tlsSecret) {
    LoggingFacade logger = getLogger();
    final String voyagerIngressName = VOYAGER_CHART_NAME + "-" + ingressName;
    final String channelName = "tcp-80";
    final String ingressType = "NodePort";
    final String ingressAffinity = "cookie";
    final String ingressClass = "voyager";

    // set the annotations for Voyager
    HashMap<String, String> annotations = new HashMap<>();
    annotations.put("ingress.appscode.com/type", ingressType);
    annotations.put("ingress.appscode.com/affinity", ingressAffinity);

    // create an ingress in domain namespace
    List<String> ingressHostList =
        createIngress(ingressName, domainNamespace, domainUid, clusterNameMSPortMap, annotations,
            ingressClass, true, tlsSecret);

    // wait until the Voyager ingress pod is ready.
    testUntil(
        assertDoesNotThrow(() -> isVoyagerReady(domainNamespace, voyagerIngressName),
          "isVoyagerReady failed with ApiException"),
        logger,
        "Voyager ingress to be ready in namespace {0}",
        domainUid);

    assertNotNull(ingressHostList,
        String.format("Ingress creation failed for domain %s in namespace %s", domainUid, domainNamespace));

    // check the ingress was found in the domain namespace
    assertThat(assertDoesNotThrow(() -> listIngresses(domainNamespace)))
        .as(String.format("Test ingress %s was found in namespace %s", ingressName, domainNamespace))
        .withFailMessage(String.format("Ingress %s was not found in namespace %s", ingressName, domainNamespace))
        .contains(ingressName);

    // get ingress service Nodeport
    int ingressServiceNodePort = assertDoesNotThrow(
        () -> getServiceNodePort(domainNamespace, voyagerIngressName, channelName),
        "Getting admin server node port failed");
    logger.info("Node port for {0} is: {1} :", voyagerIngressName, ingressServiceNodePort);

    // check the ingress is ready to route the app to the server pod
    if (ingressServiceNodePort != 0) {
      for (String ingressHost : ingressHostList) {
        String curlCmd = "curl --silent --show-error --noproxy '*' -H 'host: " + ingressHost
            + "' http://" + K8S_NODEPORT_HOST + ":" + ingressServiceNodePort
            + "/weblogic/ready --write-out %{http_code} -o /dev/null";

        logger.info("Executing curl command {0}", curlCmd);
        assertTrue(callWebAppAndWaitTillReady(curlCmd, 60));
      }
    }

    logger.info("ingress {0} for domain {1} was created in namespace {2}",
        ingressName, domainUid, domainNamespace);

    return ingressHostList;
  }

  private static Callable<Boolean> pullImageFromOcirAndTag(String localImage) {
    return (() -> {
      String nginxImage = GCR_NGINX_IMAGE_NAME + ":" + "v0.35.0";
      LoggingFacade logger = getLogger();
      logger.info("pulling image {0} from OCIR, tag it as image {1} ",
          localImage, nginxImage);
      return dockerPull(localImage) && dockerTag(localImage, nginxImage);
    });
  }

  /**
   * Check Voyager pod is running in the specified namespace.
   *
   * @param podName pod name to check
   * @param namespace the namespace in which the pod is running
   */
  public static Callable<Boolean> isVoyagerPodReady(String namespace, String podName) {
    return isVoyagerReady(namespace, podName);
  }
}

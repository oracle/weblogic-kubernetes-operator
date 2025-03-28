// Copyright (c) 2020, 2024, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes.utils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;

import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.models.V1EnvVar;
import io.kubernetes.client.openapi.models.V1LocalObjectReference;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import oracle.weblogic.domain.AdminServer;
import oracle.weblogic.domain.Configuration;
import oracle.weblogic.domain.DomainResource;
import oracle.weblogic.domain.DomainSpec;
import oracle.weblogic.domain.Model;
import oracle.weblogic.domain.MonitoringExporterSpecification;
import oracle.weblogic.domain.OnlineUpdate;
import oracle.weblogic.domain.ServerPod;
import oracle.weblogic.kubernetes.actions.impl.primitive.Command;
import oracle.weblogic.kubernetes.extensions.InitializationTasks;
import oracle.weblogic.kubernetes.logging.LoggingFacade;
import oracle.weblogic.kubernetes.utils.SemanticVersion.Compatibility;

import static oracle.weblogic.kubernetes.TestConstants.ARM;
import static oracle.weblogic.kubernetes.TestConstants.BASE_IMAGES_REPO;
import static oracle.weblogic.kubernetes.TestConstants.BASE_IMAGES_TENANCY;
import static oracle.weblogic.kubernetes.TestConstants.DOMAIN_API_VERSION;
import static oracle.weblogic.kubernetes.TestConstants.FAILURE_RETRY_INTERVAL_SECONDS;
import static oracle.weblogic.kubernetes.TestConstants.FAILURE_RETRY_LIMIT_MINUTES;
import static oracle.weblogic.kubernetes.TestConstants.IMAGE_PULL_POLICY;
import static oracle.weblogic.kubernetes.TestConstants.ISTIO_VERSION;
import static oracle.weblogic.kubernetes.TestConstants.KUBERNETES_CLI;
import static oracle.weblogic.kubernetes.TestConstants.OCNE;
import static oracle.weblogic.kubernetes.TestConstants.OKE_CLUSTER;
import static oracle.weblogic.kubernetes.TestConstants.OKE_CLUSTER_PRIVATEIP;
import static oracle.weblogic.kubernetes.TestConstants.PROMETHEUS_CONFIG_MAP_RELOAD_IMAGE_NAME;
import static oracle.weblogic.kubernetes.TestConstants.PROMETHEUS_CONFIG_MAP_RELOAD_IMAGE_TAG;
import static oracle.weblogic.kubernetes.TestConstants.PROMETHEUS_IMAGE_NAME;
import static oracle.weblogic.kubernetes.TestConstants.PROMETHEUS_IMAGE_TAG;
import static oracle.weblogic.kubernetes.TestConstants.RESULTS_ROOT;
import static oracle.weblogic.kubernetes.TestConstants.TEST_IMAGES_TENANCY;
import static oracle.weblogic.kubernetes.actions.ActionConstants.RESOURCE_DIR;
import static oracle.weblogic.kubernetes.actions.TestActions.listPods;
import static oracle.weblogic.kubernetes.actions.impl.primitive.Command.defaultCommandParams;
import static oracle.weblogic.kubernetes.utils.ApplicationUtils.checkAppUsingHostHeader;
import static oracle.weblogic.kubernetes.utils.ClusterUtils.createClusterResourceAndAddReferenceToDomain;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.checkServiceExists;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.formatIPv6Host;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.getServiceExtIPAddrtOke;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.testUntil;
import static oracle.weblogic.kubernetes.utils.ExecCommand.exec;
import static oracle.weblogic.kubernetes.utils.FileUtils.generateFileFromTemplate;
import static oracle.weblogic.kubernetes.utils.FileUtils.replaceStringInFile;
import static oracle.weblogic.kubernetes.utils.LoadBalancerUtils.isLoadBalancerHealthy;
import static oracle.weblogic.kubernetes.utils.PodUtils.checkPodReady;
import static oracle.weblogic.kubernetes.utils.PodUtils.setPodAntiAffinity;
import static oracle.weblogic.kubernetes.utils.ThreadSafeLogger.getLogger;
import static org.apache.commons.io.FileUtils.deleteDirectory;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * The istio utility class for tests.
 */
public class IstioUtils {
  private static SemanticVersion installedIstioVersion = new SemanticVersion(ISTIO_VERSION);

  /**
   * Install istio.
   */
  public static void installIstio() {
    LoggingFacade logger = getLogger();

    // Copy the istio (un)intsall scripts to RESULTS_ROOT, so that istio
    // can be (un)installed manually when SKIP_CLEANUP is set to true
    assertDoesNotThrow(() -> Files.copy(
        Paths.get(RESOURCE_DIR, "bash-scripts", "install-istio.sh"),
        Paths.get(RESULTS_ROOT, "install-istio.sh"),
        StandardCopyOption.REPLACE_EXISTING),
        String.format("Copy install-istio.sh to %s failed", RESULTS_ROOT));

    assertDoesNotThrow(() -> Files.copy(
        Paths.get(RESOURCE_DIR, "bash-scripts", "uninstall-istio.sh"),
        Paths.get(RESULTS_ROOT, "uninstall-istio.sh"),
        StandardCopyOption.REPLACE_EXISTING),
        String.format("Copy uninstall-istio.sh to %s failed", RESULTS_ROOT));

    Path istioInstallPath =
        Paths.get(RESULTS_ROOT, "install-istio.sh");
    String installScript = istioInstallPath.toString();

    // When install istio in OCNE environment, 
    // use BASE_IMAGES_REPO/devweblogic/istio-release instead of gcr.io/istio-release
    if (OCNE) {
      String ocneIstioRepo = BASE_IMAGES_REPO + "/" + BASE_IMAGES_TENANCY; 
      logger.info("replace istio installation hub in File {0}", installScript);
      assertDoesNotThrow(() -> replaceStringInFile(installScript, "gcr.io", ocneIstioRepo),
          String.format("Failed to replace string in File %s", installScript));
      assertDoesNotThrow(() -> replaceStringInFile(installScript, "--auth=instance_principal", " "),
          String.format("Failed to replace string in File %s", installScript));
    }
    String arch = "linux-amd64";
    if (ARM) {
      arch = "linux-arm64";
    }

    String command =
        String.format("%s %s %s %s %s", installScript, ISTIO_VERSION, RESULTS_ROOT, TEST_IMAGES_TENANCY, arch);
    logger.info("Istio installation command {0}", command);
    assertTrue(() -> Command.withParams(
        defaultCommandParams()
            .command(command)
            .redirect(false))
        .execute());
    if (OKE_CLUSTER) {
      String loadBalancerIP = getServiceExtIPAddrtOke("istio-ingressgateway", "istio-system");
      testUntil(
          assertDoesNotThrow(() -> isLoadBalancerHealthy("istio-system", "istio-ingressgateway"),
              "isLoadBalancerHealthy failed with ApiException"),
          logger,
          "Istio LoadBalancer to be healthy in namespace {0}",
          "istio-system");
      InitializationTasks.registerLoadBalancerExternalIP(loadBalancerIP);
    }
  }

  /**
   * Uninstall istio.
   */
  public static void uninstallIstio() {
    LoggingFacade logger = getLogger();
    Path istioInstallPath = 
        Paths.get(RESOURCE_DIR, "bash-scripts", "uninstall-istio.sh");
    String installScript = istioInstallPath.toString();
    String command =
        String.format("%s %s %s", installScript, ISTIO_VERSION, RESULTS_ROOT);
    logger.info("Istio uninstallation command {0}", command);
    assertTrue(() -> Command.withParams(
        defaultCommandParams()
            .command(command)
            .redirect(false))
        .execute());
  }

  /**
   * Get the http ingress port of istio installation.
   *
   * @return ingress port for istio-ingressgateway
   */
  public static int getIstioHttpIngressPort() {
    return getIstioHttpIngressPort("http2");
  }
  
  /**
   * Get the http ingress port of istio installation.
   *
   * @param portName name of port to get
   * @return ingress port for istio-ingressgateway
   */
  public static int getIstioHttpIngressPort(String portName) {
    LoggingFacade logger = getLogger();
    ExecResult result;
    StringBuffer getIngressPort;
    getIngressPort = new StringBuffer(KUBERNETES_CLI + " -n istio-system get service istio-ingressgateway ");
    getIngressPort.append("-o jsonpath='{.spec.ports[?(@.name==\"" + portName.trim() + "\")].nodePort}'");
    logger.info("getIngressPort: " + KUBERNETES_CLI + " command {0}", new String(getIngressPort));
    try {
      result = exec(new String(getIngressPort), true);
    } catch (Exception ex) {
      logger.info("Exception in getIngressPort() {0}", ex);
      return 0;
    }
    logger.info("getIngressPort: " + KUBERNETES_CLI + " returned {0}", result.toString());
    if (result.stdout() == null) {
      return 0;
    } else {
      return Integer.valueOf(result.stdout());
    }
  }

  /**
   * Get the secure https ingress port of istio installation.
   *
   * @return secure ingress https port for istio-ingressgateway
   */
  public static int getIstioSecureIngressPort() {
    LoggingFacade logger = getLogger();
    ExecResult result;
    StringBuffer getSecureIngressPort;
    getSecureIngressPort = new StringBuffer(KUBERNETES_CLI + " -n istio-system get service istio-ingressgateway ");
    getSecureIngressPort.append("-o jsonpath='{.spec.ports[?(@.name==\"https\")].nodePort}'");
    logger.info("getSecureIngressPort: " + KUBERNETES_CLI + " command {0}", new String(getSecureIngressPort));
    try {
      result = exec(new String(getSecureIngressPort), true);
    } catch (Exception ex) {
      logger.info("Exception in getSecureIngressPort() {0}", ex);
      return 0;
    }
    logger.info("getSecureIngressPort: " + KUBERNETES_CLI + " returned {0}", result.toString());
    if (result.stdout() == null) {
      return 0;
    } else {
      return Integer.valueOf(result.stdout());
    }
  }

  /**
   * Get the tcp ingress port of istio installation.
   *
   * @return tcp ingress port for istio-ingressgateway
   */
  public static int getIstioTcpIngressPort() {
    LoggingFacade logger = getLogger();
    ExecResult result;
    StringBuffer getTcpIngressPort;
    getTcpIngressPort = new StringBuffer(KUBERNETES_CLI + " -n istio-system get service istio-ingressgateway ");
    getTcpIngressPort.append("-o jsonpath='{.spec.ports[?(@.name==\"tcp\")].nodePort}'");
    logger.info("getTcpIngressPort: " + KUBERNETES_CLI + " command {0}", new String(getTcpIngressPort));
    try {
      result = exec(new String(getTcpIngressPort), true);
    } catch (Exception ex) {
      logger.info("Exception in getTcpIngressPort() {0}", ex);
      return 0;
    }
    logger.info("getTcpIngressPort: " + KUBERNETES_CLI + " returned {0}", result.toString());
    if (result.stdout() == null) {
      return 0;
    } else {
      return Integer.valueOf(result.stdout());
    }
  }

  /**
   * Deploy the Http Istio Gateway and Istio virtual service.
   *
   * @param configPath path to k8s configuration file
   * @return true if deployment is success otherwise false
   */
  public static boolean deployHttpIstioGatewayAndVirtualservice(Path configPath) {
    LoggingFacade logger = getLogger();
    ExecResult result;
    StringBuffer deployIstioGateway;
    deployIstioGateway = new StringBuffer(KUBERNETES_CLI + " apply -f ");
    deployIstioGateway.append(configPath);
    logger.info("deployIstioGateway: " + KUBERNETES_CLI + " command {0}", new String(deployIstioGateway));
    try {
      result = exec(new String(deployIstioGateway), true);
    } catch (Exception ex) {
      logger.info("Exception in deployIstioGateway() {0}", ex);
      return false;
    }
    logger.info("deployIstioHttpGateway: " + KUBERNETES_CLI + " returned {0}", result.toString());
    return !(result.stdout().contains("Error"));
  }

  /**
   * Deploy the tcp Istio Gateway and Istio virtual service.
   *
   * @param configPath path to k8s configuration file
   * @return true if deployment is success otherwise false
   */
  public static boolean deployTcpIstioGatewayAndVirtualservice(
      Path configPath) {
    LoggingFacade logger = getLogger();
    ExecResult result;
    StringBuffer deployIstioGateway;
    deployIstioGateway = new StringBuffer(KUBERNETES_CLI + " apply -f ");
    deployIstioGateway.append(configPath);
    logger.info("deployIstioGateway: " + KUBERNETES_CLI + " command {0}", new String(deployIstioGateway));
    try {
      result = exec(new String(deployIstioGateway), true);
    } catch (Exception ex) {
      logger.info("Exception in deployIstioGateway() {0}", ex);
      return false;
    }
    logger.info("deployIstioTcpGateway: " + KUBERNETES_CLI + " returned {0}", result.toString());
    return result.stdout().contains("istio-tcp-gateway created");
  }

  /**
   * Deploy the Istio DestinationRule.
   *
   * @param configPath path to k8s configuration file
   * @return true if deployment is success otherwise false
   */
  public static boolean deployIstioDestinationRule(
      Path configPath) {
    LoggingFacade logger = getLogger();
    ExecResult result;
    StringBuffer deployIstioGateway;
    deployIstioGateway = new StringBuffer(KUBERNETES_CLI + " apply -f ");
    deployIstioGateway.append(configPath);
    logger.info("deployIstioDestinationRule: " + KUBERNETES_CLI + " command {0}", new String(deployIstioGateway));
    try {
      result = exec(new String(deployIstioGateway), true);
    } catch (Exception ex) {
      logger.info("Exception in deployIstioDestinationRule() {0}", ex);
      return false;
    }
    logger.info("deployIstioDestinationRule: " + KUBERNETES_CLI + " returned {0}", result.toString());
    return result.stdout().contains("destination-rule created");
  }


  /**
   * Deploy the Istio Prometheus.
   *
   * @param domainNamespace namespace of domain to monitor
   * @param domainUid uid of domain to monitor
   * @param prometheusPort nodePort value for prometheus
   * @return true if deployment is successful otherwise false
   */
  public static boolean deployIstioPrometheus(
      String domainNamespace, String domainUid, String prometheusPort) {
    LoggingFacade logger = getLogger();
    final String prometheusRegexValue = String.format("regex: %s;%s", domainNamespace, domainUid);
    Path fileTemp = Paths.get(RESULTS_ROOT, "istioPrometheus");
    assertDoesNotThrow(() -> deleteDirectory(fileTemp.toFile()));
    assertDoesNotThrow(() -> Files.createDirectories(fileTemp));
    logger.info("copy the promvalue.yaml to staging location");
    String fileName = OKE_CLUSTER_PRIVATEIP ? "istioprometheusoke.yaml" : "istioprometheus.yaml";
    Path srcPromFile = Paths.get(RESOURCE_DIR, "exporter", fileName);
    Path targetPromFile = Paths.get(fileTemp.toString(), "istioprometheus.yaml");
    assertDoesNotThrow(() -> Files.copy(srcPromFile, targetPromFile, StandardCopyOption.REPLACE_EXISTING));
    String oldValue = "regex: default;domain1";
    assertDoesNotThrow(() -> replaceStringInFile(targetPromFile.toString(),
        oldValue,
        prometheusRegexValue));
    String oldPortValue = "30510";
    assertDoesNotThrow(() -> replaceStringInFile(targetPromFile.toString(),
        oldPortValue,
        prometheusPort));
    assertDoesNotThrow(() -> replaceStringInFile(targetPromFile.toString(),
        "prometheus_configmap_reload_image",
        PROMETHEUS_CONFIG_MAP_RELOAD_IMAGE_NAME));
    assertDoesNotThrow(() -> replaceStringInFile(targetPromFile.toString(),
        "prometheus_configmap_reload_tag",
        PROMETHEUS_CONFIG_MAP_RELOAD_IMAGE_TAG));
    assertDoesNotThrow(() -> replaceStringInFile(targetPromFile.toString(),
        "prometheus_image",
        PROMETHEUS_IMAGE_NAME));
    assertDoesNotThrow(() -> replaceStringInFile(targetPromFile.toString(),
        "prometheus_tag",
        PROMETHEUS_IMAGE_TAG));
    ExecResult result;
    StringBuffer deployIstioPrometheus;
    deployIstioPrometheus = new StringBuffer(KUBERNETES_CLI + " apply -f ");
    deployIstioPrometheus.append(targetPromFile.toString());
    logger.info("deployIstioPrometheus: " + KUBERNETES_CLI + " command {0}", new String(deployIstioPrometheus));
    try {
      result = exec(new String(deployIstioPrometheus), true);
    } catch (Exception ex) {
      logger.info("Exception in deployIstioPrometheus() {0}", ex);
      return false;
    }
    logger.info("deployIstioPrometheus: " + KUBERNETES_CLI + " returned {0}", result.toString());
    try {
      for (var item : listPods("istio-system", null).getItems()) {
        if (item.getMetadata() != null && item.getMetadata().getName() != null
            && item.getMetadata().getName().contains("prometheus")) {
          logger.info("Waiting for pod {0} to be ready in namespace {1}",
              item.getMetadata().getName(), "istio-system");
          checkPodReady(item.getMetadata().getName(), null, "istio-system");
          checkServiceExists("prometheus", "istio-system");
        }
      }
    } catch (ApiException e) {
      e.printStackTrace();
      return false;
    }
    return true;
  }

  /**
   * Create a domain object for a Kubernetes domain custom resource for istio using the basic model-in-image
   * image.
   *
   * @param domainUid uid of the domain
   * @param domNamespace Kubernetes namespace that the domain is hosted
   * @param adminSecretName name of the new WebLogic admin credentials secret
   * @param repoSecretName name of the secret for pulling the WebLogic image
   * @param encryptionSecretName name of the secret used to encrypt the models
   * @param replicaCount number of managed servers to start
   * @param miiImage used image name
   * @param configmapName used configmap name
   * @param clusterName name of the cluster to add in domain
   * @return domain object of the domain resource
   */
  public static DomainResource createIstioDomainResource(String domainUid, String domNamespace,
                                                         String adminSecretName, String repoSecretName,
                                                         String encryptionSecretName, int replicaCount,
                                                         String miiImage, String configmapName, String clusterName) {
    return createIstioDomainResource(domainUid,
        domNamespace, adminSecretName,repoSecretName,
        encryptionSecretName, replicaCount, miiImage,
        configmapName, clusterName, null, null);
  }

  /**
   * Create a domain object for a Kubernetes domain custom resource for istio using the basic model-in-image
   * image with exporter sidecar.
   *
   * @param domainUid uid of the domain
   * @param domNamespace Kubernetes namespace that the domain is hosted
   * @param adminSecretName name of the new WebLogic admin credentials secret
   * @param repoSecretName name of the secret for pulling the WebLogic image
   * @param encryptionSecretName name of the secret used to encrypt the models
   * @param replicaCount number of managed servers to start
   * @param miiImage used image name
   * @param configmapName used configmap name
   * @param clusterName name of the cluster to add in domain
   * @param monexpConfig path to exporter configuration yaml file
   * @param monexpImage name of monitoring exporter sidecar image
   * @return domain object of the domain resource
   */
  public static DomainResource createIstioDomainResource(String domainUid, String domNamespace,
                                                         String adminSecretName, String repoSecretName,
                                                         String encryptionSecretName, int replicaCount,
                                                         String miiImage, String configmapName, String clusterName,
                                                         String monexpConfig, String monexpImage) {

    // create the domain CR
    DomainResource domain = new DomainResource()
        .apiVersion(DOMAIN_API_VERSION)
        .kind("Domain")
        .metadata(new V1ObjectMeta()
            .name(domainUid)
            .namespace(domNamespace))
        .spec(new DomainSpec()
            .domainUid(domainUid)
            .domainHomeSourceType("FromModel")
            .image(miiImage)
            .imagePullPolicy(IMAGE_PULL_POLICY)
            .addImagePullSecretsItem(new V1LocalObjectReference()
                .name(repoSecretName))
            .webLogicCredentialsSecret(new V1LocalObjectReference()
                .name(adminSecretName))
            .includeServerOutInPodLog(true)
            .serverStartPolicy("IfNeeded")
            .failureRetryIntervalSeconds(FAILURE_RETRY_INTERVAL_SECONDS)
            .failureRetryLimitMinutes(FAILURE_RETRY_LIMIT_MINUTES)
            .serverPod(new ServerPod()
                .addEnvItem(new V1EnvVar()
                    .name("JAVA_OPTIONS")
                    .value("-Dweblogic.StdoutDebugEnabled=false"))
                .addEnvItem(new V1EnvVar()
                    .name("USER_MEM_ARGS")
                    .value("-Djava.security.egd=file:/dev/./urandom ")))
            .configuration(new Configuration()
                .model(new Model()
                    .domainType("WLS")
                    .configMap(configmapName)
                    .onlineUpdate(new OnlineUpdate().enabled(true))
                    .runtimeEncryptionSecret(encryptionSecretName))
                .introspectorJobActiveDeadlineSeconds(3000L)));

    // create cluster resource
    domain = createClusterResourceAndAddReferenceToDomain(domainUid + "-" + clusterName,
        clusterName, domNamespace, domain, replicaCount);

    if (monexpConfig != null) {
      LoggingFacade logger = getLogger();
      logger.info("yaml config file path : " + monexpConfig);
      String contents = null;
      try {
        contents = new String(Files.readAllBytes(Paths.get(monexpConfig)));
      } catch (IOException e) {
        e.printStackTrace();
        fail("Failed to read configuration file");
      }
      String imagePullPolicy = IMAGE_PULL_POLICY;
      domain.getSpec().monitoringExporter(new MonitoringExporterSpecification()
          .image(monexpImage)
          .imagePullPolicy(imagePullPolicy)
          .configuration(contents));

      logger.info("Created domain CR with Monitoring exporter configuration : "
          + domain.getSpec().getMonitoringExporter().toString());
    }

    setPodAntiAffinity(domain);
    return domain;
  }

  /**
   * Check if Istio version used for integration tests require WebLogic NAP's with localhost bindings.
   *
   * @return true if Istio version used for integration tests is prior to version 1.10;
   *        false otherwise
   */
  public static boolean isLocalHostBindingsEnabled() {
    return installedIstioVersion.getCompatibilityWith("1.10") == Compatibility.VERSION_LOWER;
  }

  /**
   *  Create an instance of AdminServer.
   *
   * @return AdminServer instance.
   */
  public static AdminServer createAdminServer() {
    AdminServer adminServer = new AdminServer();

    if (!isLocalHostBindingsEnabled()) {
      adminServer.adminChannelPortForwardingEnabled(true);
    }
    return adminServer;
  }

  
  /**
   * Check WebLogic access through Istio Ingress Port.
   * @param istioHost Host
   * @param istioIngressPort Istio Ingress Port
   * @param domainNamespace Domain namespace that the domain is hosted
   */
  public static void checkIstioService(String istioHost, int istioIngressPort, String domainNamespace) {
    // We can not verify Rest Management console thru Administration NodePort
    // in istio, as we can not enable Administration NodePort
    LoggingFacade logger = getLogger();
    logger.info("Verifying Istio Service @IngressPort [{0}]", istioIngressPort);
    String host = formatIPv6Host(istioHost);
    String readyAppUrl = "http://" + host + ":" + istioIngressPort + "/weblogic/ready";
    boolean checlReadyApp =
        checkAppUsingHostHeader(readyAppUrl, domainNamespace + ".org");
    assertTrue(checlReadyApp, "Failed to access ready app");
    logger.info("ready app is accessible");

  }

  /**
   * Create Istio Virtual Service and Gateway.
   * @param domainUid  Domain resource identifier
   * @param clusterName  Name of the WebLogic cluster
   * @param adminServerPodName Name of the admin server pod
   * @param domainNamespace Domain Namespace
   *
   * @return istioIngressPort
   */
  public static int createIstioService(
       String domainUid, String clusterName, 
       String adminServerPodName, String domainNamespace) {
    LoggingFacade logger = getLogger();
    String clusterService = domainUid + "-cluster-" + clusterName + "." + domainNamespace + ".svc.cluster.local";

    Map<String, String> templateMap  = new HashMap<>();
    templateMap.put("NAMESPACE", domainNamespace);
    templateMap.put("DUID", domainUid);
    templateMap.put("ADMIN_SERVICE",adminServerPodName);
    templateMap.put("CLUSTER_SERVICE", clusterService);
    templateMap.put("MANAGED_SERVER_PORT", "8001");

    Path srcHttpFile = Paths.get(RESOURCE_DIR, "istio", "istio-http-template.yaml");
    Path targetHttpFile = assertDoesNotThrow(
        () -> generateFileFromTemplate(srcHttpFile.toString(), "istio-http.yaml", templateMap));
    logger.info("Generated Http VS/Gateway file path is {0}", targetHttpFile);

    boolean deployRes = assertDoesNotThrow(
        () -> deployHttpIstioGatewayAndVirtualservice(targetHttpFile));
    assertTrue(deployRes, "Failed to deploy Http Istio Gateway/VirtualService");

    Path srcDrFile = Paths.get(RESOURCE_DIR, "istio", "istio-dr-template.yaml");
    Path targetDrFile = assertDoesNotThrow(
        () -> generateFileFromTemplate(srcDrFile.toString(), "istio-dr.yaml", templateMap));
    logger.info("Generated DestinationRule file path is {0}", targetDrFile);

    deployRes = assertDoesNotThrow(
        () -> deployIstioDestinationRule(targetDrFile));
    assertTrue(deployRes, "Failed to deploy Istio DestinationRule");

    int istioIngressPort = getIstioHttpIngressPort();
    logger.info("Istio Ingress Port is {0}", istioIngressPort);
    return istioIngressPort;
    
  }

}

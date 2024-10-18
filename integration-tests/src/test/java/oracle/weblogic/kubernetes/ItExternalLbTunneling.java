// Copyright (c) 2020, 2024, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

import io.kubernetes.client.openapi.models.V1EnvVar;
import io.kubernetes.client.openapi.models.V1LocalObjectReference;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import oracle.weblogic.domain.AdminServer;
import oracle.weblogic.domain.AdminService;
import oracle.weblogic.domain.Channel;
import oracle.weblogic.domain.Configuration;
import oracle.weblogic.domain.DomainResource;
import oracle.weblogic.domain.DomainSpec;
import oracle.weblogic.domain.Model;
import oracle.weblogic.domain.ServerPod;
import oracle.weblogic.kubernetes.actions.impl.primitive.Command;
import oracle.weblogic.kubernetes.actions.impl.primitive.CommandParams;
import oracle.weblogic.kubernetes.actions.impl.primitive.HelmParams;
import oracle.weblogic.kubernetes.annotations.DisabledOnSlimImage;
import oracle.weblogic.kubernetes.annotations.IntegrationTest;
import oracle.weblogic.kubernetes.annotations.Namespaces;
import oracle.weblogic.kubernetes.logging.LoggingFacade;
import oracle.weblogic.kubernetes.utils.ExecResult;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfEnvironmentVariable;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import static oracle.weblogic.kubernetes.TestConstants.ADMIN_PASSWORD_DEFAULT;
import static oracle.weblogic.kubernetes.TestConstants.ADMIN_USERNAME_DEFAULT;
import static oracle.weblogic.kubernetes.TestConstants.DOMAIN_API_VERSION;
import static oracle.weblogic.kubernetes.TestConstants.DOMAIN_VERSION;
import static oracle.weblogic.kubernetes.TestConstants.IMAGE_PULL_POLICY;
import static oracle.weblogic.kubernetes.TestConstants.IT_EXTERNALLBTUNNELING_HTTPS_HOSTPORT;
import static oracle.weblogic.kubernetes.TestConstants.IT_EXTERNALLBTUNNELING_HTTPS_NODEPORT;
import static oracle.weblogic.kubernetes.TestConstants.IT_EXTERNALLBTUNNELING_HTTP_HOSTPORT;
import static oracle.weblogic.kubernetes.TestConstants.IT_EXTERNALLBTUNNELING_HTTP_NODEPORT;
import static oracle.weblogic.kubernetes.TestConstants.K8S_NODEPORT_HOST;
import static oracle.weblogic.kubernetes.TestConstants.K8S_NODEPORT_HOSTNAME;
import static oracle.weblogic.kubernetes.TestConstants.KUBERNETES_CLI;
import static oracle.weblogic.kubernetes.TestConstants.MII_BASIC_IMAGE_NAME;
import static oracle.weblogic.kubernetes.TestConstants.MII_BASIC_IMAGE_TAG;
import static oracle.weblogic.kubernetes.TestConstants.OKD;
import static oracle.weblogic.kubernetes.TestConstants.RESULTS_ROOT;
import static oracle.weblogic.kubernetes.TestConstants.SKIP_CLEANUP;
import static oracle.weblogic.kubernetes.TestConstants.TEST_IMAGES_REPO_SECRET_NAME;
import static oracle.weblogic.kubernetes.TestConstants.TRAEFIK_RELEASE_NAME;
import static oracle.weblogic.kubernetes.actions.ActionConstants.RESOURCE_DIR;
import static oracle.weblogic.kubernetes.actions.TestActions.createDomainCustomResource;
import static oracle.weblogic.kubernetes.actions.TestActions.getServicePort;
import static oracle.weblogic.kubernetes.actions.TestActions.uninstallTraefik;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.domainExists;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.checkPodReadyAndServiceExists;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.formatIPv6Host;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.getHostAndPort;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.getNextFreePort;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.testUntil;
import static oracle.weblogic.kubernetes.utils.ConfigMapUtils.createConfigMapFromFiles;
import static oracle.weblogic.kubernetes.utils.ExecCommand.exec;
import static oracle.weblogic.kubernetes.utils.FileUtils.copyFileFromPod;
import static oracle.weblogic.kubernetes.utils.FileUtils.copyFileToPod;
import static oracle.weblogic.kubernetes.utils.FileUtils.generateFileFromTemplate;
import static oracle.weblogic.kubernetes.utils.ImageUtils.createTestRepoSecret;
import static oracle.weblogic.kubernetes.utils.LoadBalancerUtils.installAndVerifyTraefik;
import static oracle.weblogic.kubernetes.utils.OKDUtils.createRouteForOKD;
import static oracle.weblogic.kubernetes.utils.OKDUtils.setTargetPortForRoute;
import static oracle.weblogic.kubernetes.utils.OKDUtils.setTlsEdgeTerminationForRoute;
import static oracle.weblogic.kubernetes.utils.OperatorUtils.installAndVerifyOperator;
import static oracle.weblogic.kubernetes.utils.PodUtils.setPodAntiAffinity;
import static oracle.weblogic.kubernetes.utils.SecretUtils.createSecretWithUsernamePassword;
import static oracle.weblogic.kubernetes.utils.ThreadSafeLogger.getLogger;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The use case described in this class verifies that an external RMI client
 * can access the WebLogic cluster JNDI tree using the LoadBalancer tunneling
 * approach as described in following  WebLogic Kubernetes operator faq page
 * https://oracle.github.io/weblogic-kubernetes-operator/faq/external-clients/
 * Load balancer tunneling is the preferred approach for giving external
 * clients and servers access to a Kubernetes hosted WebLogic cluster.
 * In a WebLogic domain, configure a custom channel for the T3 protocol that
 * enables HTTP tunneling, and specifies an external address and port that
 * correspond to the address and port remote clients will use to access the
 * load balancer. Set up a load balancer that redirects HTTP(s) traffic to
 * the custom channel. Configure a WebLogic dynamic cluster domain using
 * Model In Image. Add a cluster targeted JMS distributed destination.
 * In OKD cluster, we do not use thrid party loadbalancers, so the tests that
 * specifically test nginx or traefik are diasbled for OKD cluster. A test
 * using routes are added to run only on OKD cluster.
 */

@DisplayName("Test external RMI access through loadbalncer tunneling")
@Tag("kind-parallel")
@Tag("okd-wls-mrg")
@IntegrationTest
@DisabledOnSlimImage
@Tag("olcne-mrg")
@Tag("gate")
class ItExternalLbTunneling {

  private static String opNamespace = null;
  private static String domainNamespace = null;
  private static String traefikNamespace = null;
  private static HelmParams traefikHelmParams = null;
  private static int replicaCount = 2;
  private static String clusterName = "cluster-1";
  private final String adminServerPodName = domainUid + "-admin-server";
  private final String managedServerPrefix = domainUid + "-managed-server";
  private final String clusterServiceName = domainUid + "-cluster-cluster-1";
  private static final String TUNNELING_MODEL_FILE = "tunneling.model.yaml";
  private static final String domainUid = "mii-tunneling";

  private static LoggingFacade logger = null;
  private static Path tlsCertFile;
  private static Path tlsKeyFile;
  private static Path jksTrustFile;
  private static String tlsSecretName = domainUid + "-test-tls-secret";
  private String clusterSvcRouteHost = null;
  private static String hostAddress = K8S_NODEPORT_HOST;

  /**
   * Install Operator.
   * Create domain resource.
   * @param namespaces list of namespaces created by the IntegrationTestWatcher by the
   JUnit engine parameter resolution mechanism
   */
  @BeforeAll
  public static void initAll(@Namespaces(3) List<String> namespaces) throws UnknownHostException {
    logger = getLogger();
    logger.info("K8S_NODEPORT_HOSTNAME {0} K8S_NODEPORT_HOST {1}", K8S_NODEPORT_HOSTNAME, K8S_NODEPORT_HOST);
    if (TestConstants.KIND_CLUSTER
        && !TestConstants.WLSIMG_BUILDER.equals(TestConstants.WLSIMG_BUILDER_DEFAULT)) {
      hostAddress = formatIPv6Host(InetAddress.getLocalHost().getHostAddress());
    }

    // get a new unique opNamespace
    logger.info("Assigning unique namespace for Operator");
    assertNotNull(namespaces.get(0), "Namespace list is null");
    opNamespace = namespaces.get(0);

    logger.info("Assigning unique namespace for Domain");
    assertNotNull(namespaces.get(1), "Namespace list is null");
    domainNamespace = namespaces.get(1);

    logger.info("Assigning unique namespace for Traefik");
    assertNotNull(namespaces.get(2), "Namespace list is null");
    traefikNamespace = namespaces.get(2);

    // install and verify operator
    installAndVerifyOperator(opNamespace, domainNamespace);

    // Create the repo secret to pull the image
    // this secret is used only for non-kind cluster
    createTestRepoSecret(domainNamespace);

    // create secret for admin credentials
    logger.info("Create secret for admin credentials");
    String adminSecretName = "weblogic-credentials";
    createSecretWithUsernamePassword(adminSecretName, domainNamespace,
            ADMIN_USERNAME_DEFAULT, ADMIN_PASSWORD_DEFAULT);

    // create encryption secret
    logger.info("Create encryption secret");
    String encryptionSecretName = "encryptionsecret";
    createSecretWithUsernamePassword(encryptionSecretName, domainNamespace,
            "weblogicenc", "weblogicenc");

    // Prepare the config map sparse model file from the template by replacing
    // Public Address of the custom channel with K8S_NODEPORT_HOST
    Map<String, String> configTemplateMap  = new HashMap<>();
    configTemplateMap.put("INGRESS_HOST", hostAddress);

    Path srcFile = Paths.get(RESOURCE_DIR,
        "wdt-models", "tunneling.model.template.yaml");
    Path targetFile = assertDoesNotThrow(
        () -> generateFileFromTemplate(srcFile.toString(),
        TUNNELING_MODEL_FILE, configTemplateMap));
    logger.info("Generated tunneling ConfigMap model file {0}", targetFile);

    String configMapName = "jms-tunneling-configmap";
    List<Path> configMapFiles = new ArrayList<>();
    configMapFiles.add(Paths.get(RESULTS_ROOT, TUNNELING_MODEL_FILE));

    createConfigMapFromFiles(configMapName, configMapFiles, domainNamespace);

    // create the domain CR with a pre-defined configmap
    createDomainResource(domainUid, domainNamespace, adminSecretName,
        TEST_IMAGES_REPO_SECRET_NAME, encryptionSecretName, replicaCount, configMapName);

    // wait for the domain to exist
    logger.info("Check for domain custom resource in namespace {0}", domainNamespace);
    testUntil(
        domainExists(domainUid, DOMAIN_VERSION, domainNamespace),
        logger,
        "domain {0} to be created in namespace {1}",
        domainUid,
        domainNamespace);

    if (!OKD) {
      logger.info("Installing Traefik controller using helm");
      traefikHelmParams = installAndVerifyTraefik(traefikNamespace,
          IT_EXTERNALLBTUNNELING_HTTP_NODEPORT, IT_EXTERNALLBTUNNELING_HTTPS_NODEPORT).getHelmParams();
    }

    // Create SSL certificate and key using openSSL with SAN extension
    createCertKeyFiles(hostAddress);
    // Create kubernates secret using genereated certificate and key
    createSecretWithTLSCertKey(tlsSecretName);
    // Import the tls certificate into a JKS truststote to be used while
    // running the standalone client.
    importKeytoTrustStore();
  }

  /**
   * Verify all server pods are running.
   * Verify all k8s services for all servers are created.
   */
  @BeforeEach
  public void beforeEach() {
    logger.info("Check admin service and pod {0} is created in namespace {1}",
        adminServerPodName, domainNamespace);
    checkPodReadyAndServiceExists(adminServerPodName, domainUid, domainNamespace);
    // check managed server services and pods are ready
    for (int i = 1; i <= replicaCount; i++) {
      logger.info("Wait for managed server services and pods are created in namespace {0}",
          domainNamespace);
      checkPodReadyAndServiceExists(managedServerPrefix + i, domainUid, domainNamespace);
    }
    if (clusterSvcRouteHost == null) {
      clusterSvcRouteHost = createRouteForOKD(clusterServiceName, domainNamespace);
    }
  }

  /**
   * The external JMS client sends 300 messages to a Uniform Distributed
   * Queue using load balancer HTTP url which maps to custom channel on
   * cluster member server on WebLogic cluster. The test also make sure that
   * each member destination gets an equal number of messages.
   * The test is skipped for slim images, beacuse wlthint3client.jar is not
   * available to download to build the external rmi JMS Client.
   * Verify RMI access to WLS through Traefik LoadBalancer.
   */
  @DisabledIfEnvironmentVariable(named = "OKD", matches = "true")
  @Test
  @DisplayName("Verify RMI access to WLS through Traefik LoadBalancer")
  void testExternalRmiAccessThruTraefik() {
    // Build the standalone JMS Client to send and receive messages
    buildClient();
    buildClientOnPod();

    // Prepare the ingress file from the template file by replacing
    // domain namespace, domain UID, cluster service name and tls secret
    Map<String, String> templateMap  = new HashMap<>();
    templateMap.put("DOMAIN_NS", domainNamespace);
    templateMap.put("DOMAIN_UID", domainUid);
    templateMap.put("CLUSTER", clusterName);
    templateMap.put("INGRESS_HOST", hostAddress);

    Path srcTraefikHttpFile = Paths.get(RESOURCE_DIR,
        "tunneling", "traefik.tunneling.template.yaml");
    Path targetTraefikHttpFile = assertDoesNotThrow(
        () -> generateFileFromTemplate(srcTraefikHttpFile.toString(),
        "traefik.tunneling.yaml", templateMap));
    logger.info("Generated Traefik Http Tunneling file {0}", targetTraefikHttpFile);

    StringBuffer deployIngress = new StringBuffer(KUBERNETES_CLI + " apply -f ");
    deployIngress.append(Paths.get(RESULTS_ROOT, "traefik.tunneling.yaml"));
    // Deploy the traefik ingress controller
    ExecResult result = assertDoesNotThrow(
        () -> exec(new String(deployIngress), true));

    logger.info(KUBERNETES_CLI + " apply returned {0}", result.toString());

    // Get the ingress service nodeport corresponding to non-tls service
    // Get the Traefik Service Name traefik-release-{ns}
    String service
        = TRAEFIK_RELEASE_NAME + "-" + traefikNamespace.substring(3);
    logger.info("TRAEFIK_SERVICE {0} in {1}", service, traefikNamespace);
    int httpTunnelingPort = IT_EXTERNALLBTUNNELING_HTTP_NODEPORT;
    if (TestConstants.KIND_CLUSTER
        && !TestConstants.WLSIMG_BUILDER.equals(TestConstants.WLSIMG_BUILDER_DEFAULT)) {
      httpTunnelingPort = IT_EXTERNALLBTUNNELING_HTTP_HOSTPORT;
    }
    assertNotEquals(-1, httpTunnelingPort,
        "Could not get the Traefik HttpTunnelingPort service node port");
    logger.info("HttpTunnelingPort for Traefik {0}", httpTunnelingPort);

    // Make sure the JMS Connection LoadBalancing and message LoadBalancing
    // works from RMI client outside of k8s cluster
    runExtClient(httpTunnelingPort, 2, false);
    logger.info("External RMI tunneling works for Traefik");
  }

  /**
   * The external JMS client sends 300 messages to a Uniform Distributed
   * Queue using load balancer HTTPS url which maps to custom channel on
   * cluster member server on WebLogic cluster. The test also make sure that
   * each destination member gets an equal number of messages.
   * The test is skipped for slim images, beacuse wlthint3client.jar is not
   * available to download to build the external rmi JMS Client.
   * Verify tls RMI access to WLS through Traefik LoadBalancer.
   */
  @DisabledIfEnvironmentVariable(named = "OKD", matches = "true")
  @Test
  @DisplayName("Verify tls RMI access WLS through Traefik loadBalancer")
  void testExternalRmiAccessThruTraefikHttpsTunneling() {

    // Build the standalone JMS Client to send and receive messages
    buildClient();

    // Prepare the ingress file from the template file by replacing
    // domain namespace, domain UID, cluster service name and tls secret
    Map<String, String> templateMap  = new HashMap<>();
    templateMap.put("DOMAIN_NS", domainNamespace);
    templateMap.put("DOMAIN_UID", domainUid);
    templateMap.put("CLUSTER", clusterName);
    templateMap.put("TLS_CERT", tlsSecretName);
    templateMap.put("INGRESS_HOST", hostAddress);

    Path srcTraefikHttpsFile  = Paths.get(RESOURCE_DIR,
        "tunneling", "traefik.tls.tunneling.template.yaml");
    Path targetTraefikHttpsFile = assertDoesNotThrow(
        () -> generateFileFromTemplate(srcTraefikHttpsFile.toString(),
            "traefik.tls.tunneling.yaml", templateMap));
    logger.info("Generated Traefik Https Tunneling file {0}", targetTraefikHttpsFile);

    // Deploy traefik ingress controller with tls enabled service with SSL
    // terminating at Ingress.
    StringBuffer deployTraefikIngress = new StringBuffer(KUBERNETES_CLI + " apply -f ");
    deployTraefikIngress.append(Paths.get(RESULTS_ROOT, "traefik.tls.tunneling.yaml"));
    ExecResult result = assertDoesNotThrow(
        () -> exec(new String(deployTraefikIngress), true));
    logger.info(KUBERNETES_CLI + " apply returned {0}", result.toString());

    // Get the ingress service nodeport corresponding to tls service
    // Get the Traefik Service Name traefik-release-{ns}
    String service
        = TRAEFIK_RELEASE_NAME + "-" + traefikNamespace.substring(3);
    logger.info("TRAEFIK_SERVICE {0} in {1}", service, traefikNamespace);
    int httpsTunnelingPort = IT_EXTERNALLBTUNNELING_HTTPS_NODEPORT;
    if (TestConstants.KIND_CLUSTER
        && !TestConstants.WLSIMG_BUILDER.equals(TestConstants.WLSIMG_BUILDER_DEFAULT)) {
      httpsTunnelingPort = IT_EXTERNALLBTUNNELING_HTTPS_HOSTPORT;
    }
    assertNotEquals(-1, httpsTunnelingPort,
        "Could not get the Traefik HttpsTunnelingPort service node port");
    logger.info("HttpsTunnelingPort for Traefik {0}", httpsTunnelingPort);
    runExtHttpsClient(httpsTunnelingPort, 2, false);
  }

  /**
   * Verify RMI access to WLS through routes - only for OKD cluster.
   */
  @EnabledIfEnvironmentVariable(named = "OKD", matches = "true")
  @Test
  @DisplayName("Verify RMI access WLS through Route in OKD ")
  void testExternalRmiAccessThruRouteHttpTunneling() {

    // Build the standalone JMS Client to send and receive messages
    buildClient();
    buildClientOnPod();

    // In OKD cluster, we need to set the target port of the route to be the httpTunnelingport
    // By default, when a service is exposed as a route, the endpoint is set to the default port.
    int httpTunnelingPort = getServicePort(
                    domainNamespace, clusterServiceName, "CustomChannel");
    assertNotEquals(-1, httpTunnelingPort,
             "Could not get the cluster custom channel port");
    setTargetPortForRoute(clusterServiceName, domainNamespace, httpTunnelingPort);
    logger.info("Found the administration service nodePort {0}", httpTunnelingPort);
    String routeHost = clusterSvcRouteHost + ":80";

    // Make sure the JMS Connection LoadBalancing and message LoadBalancing
    // works from RMI client outside of k8s cluster
    runExtClient(routeHost, 0, 2, false);
    logger.info("External RMI http tunneling works for Route");
  }

  /**
   * Verify tls RMI access to WLS through routes with edge termination - only for OKD cluster.
   */
  @Disabled("need to add tls key and certs")
  @EnabledIfEnvironmentVariable(named = "OKD", matches = "true")
  @Test
  @DisplayName("Verify tls RMI access WLS through Route in OKD ")
  void testExternalRmiAccessThruRouteHttpsTunneling() {

    // Build the standalone JMS Client to send and receive messages
    buildClient();

    int httpsTunnelingPort = getServicePort(
                    domainNamespace, clusterServiceName, "CustomChannel");
    assertNotEquals(-1, httpsTunnelingPort,
             "Could not get the cluster custom channel port");
    logger.info("Found the administration service nodePort {0}", httpsTunnelingPort);

    assertDoesNotThrow(() ->
                  setTlsEdgeTerminationForRoute(clusterServiceName, domainNamespace, tlsKeyFile, tlsCertFile));
    // In OKD cluster, we need to set the target port of the route to be the httpTunnelingport
    // By default, when a service is exposed as a route, the endpoint is set to the default port.
    setTargetPortForRoute(clusterServiceName, domainNamespace, httpsTunnelingPort);
    String routeHost = clusterSvcRouteHost + ":443";

    // Make sure the JMS Connection LoadBalancing and message LoadBalancing
    // works from RMI client outside of k8s cluster
    runExtHttpsClient(routeHost, 0, 2, false);
    logger.info("External RMI https tunneling works for route");
  }

  // Run the RMI client inside K8s Cluster
  private void runExtHttpsClient(int httpsTunnelingPort, int serverCount, boolean checkConnection) {
    runExtHttpsClient(null, httpsTunnelingPort, serverCount, checkConnection);
  }

  private void runExtHttpsClient(String routeHost, int httpsTunnelingPort, int serverCount, boolean checkConnection) {
    String hostAndPort = getHostAndPort(routeHost, httpsTunnelingPort);
    // Generate java command to execute client with classpath
    StringBuffer httpsUrl = new StringBuffer("https://");
    httpsUrl.append(hostAndPort);

    // StringBuffer javasCmd = new StringBuffer("java -cp ");
    StringBuffer javasCmd = new StringBuffer("");
    javasCmd.append(Paths.get(RESULTS_ROOT, "/jdk/bin/java "));
    javasCmd.append("-cp ");
    javasCmd.append(Paths.get(RESULTS_ROOT, "wlthint3client.jar"));
    javasCmd.append(":");
    javasCmd.append(Paths.get(RESULTS_ROOT));
    // javasCmd.append(" -Djavax.net.debug=all ");
    javasCmd.append(" -Djavax.net.ssl.trustStorePassword=password");
    javasCmd.append(" -Djavax.net.ssl.trustStoreType=jks");
    javasCmd.append(" -Djavax.net.ssl.trustStore=");
    javasCmd.append(jksTrustFile);
    javasCmd.append(" JmsTestClient ");
    javasCmd.append(httpsUrl);
    javasCmd.append(" ");
    javasCmd.append(String.valueOf(serverCount));
    javasCmd.append(" ");
    javasCmd.append(String.valueOf(checkConnection));
    logger.info("java command to be run {0}", javasCmd.toString());

    // Note it takes a couples of iterations before the client success
    testUntil(runJmsClient(new String(javasCmd)),
        logger,
        "Wait for Https JMS Client to access WLS");
  }

  // Run the RMI client inside K8s Cluster
  private void runClientInsidePod(int serverCount, boolean checkConnection) {

    // Make sure the JMS Connection LoadBalancing and message LoadBalancing
    // works inside pod before scaling the cluster
    String jarLocation = "/u01/oracle/wlserver/server/lib/wlthint3client.jar";
    StringBuffer javapCmd = new StringBuffer(KUBERNETES_CLI + " exec -n ");
    javapCmd.append(domainNamespace);
    javapCmd.append(" -it ");
    javapCmd.append(adminServerPodName);
    javapCmd.append(" -- /bin/bash -c \"");
    javapCmd.append("java -cp ");
    javapCmd.append(jarLocation);
    javapCmd.append(":.");
    javapCmd.append(" JmsTestClient ");
    javapCmd.append(" t3://");
    javapCmd.append(domainUid);
    javapCmd.append("-cluster-");
    javapCmd.append(clusterName);
    javapCmd.append(":8001 ");
    javapCmd.append(String.valueOf(serverCount));
    javapCmd.append(" ");
    javapCmd.append(String.valueOf(checkConnection));
    javapCmd.append(" \"");
    logger.info("java command to be run {0}", javapCmd.toString());

    testUntil(runJmsClient(new String(javapCmd)), logger, "Wait for t3 JMS Client to access WLS");
  }

  private void runExtClient(int httpTunnelingPort, int serverCount, boolean checkConnection) {
    runExtClient(null, httpTunnelingPort, serverCount, checkConnection);
  }

  // Run the RMI client outside the K8s Cluster using the JDK binary copied 
  // from the Pod in the method buildClient()
  private void runExtClient(String routeHost, int httpTunnelingPort, int serverCount, boolean checkConnection) {
    String hostAndPort = getHostAndPort(routeHost, httpTunnelingPort);
    // Generate java command to execute client with classpath
    StringBuffer httpUrl = new StringBuffer("http://");
    httpUrl.append(hostAndPort);

    // StringBuffer javaCmd = new StringBuffer("java -cp ");
    StringBuffer javaCmd = new StringBuffer("");
    javaCmd.append(Paths.get(RESULTS_ROOT, "/jdk/bin/java "));
    javaCmd.append("-cp ");
    javaCmd.append(Paths.get(RESULTS_ROOT, "wlthint3client.jar"));
    javaCmd.append(":");
    javaCmd.append(Paths.get(RESULTS_ROOT));
    javaCmd.append(" JmsTestClient ");
    javaCmd.append(httpUrl);
    javaCmd.append(" ");
    javaCmd.append(String.valueOf(serverCount));
    javaCmd.append(" ");
    javaCmd.append(String.valueOf(checkConnection));
    logger.info("java command to be run {0}", javaCmd.toString());

    // Note it takes a couples of iterations before the client success    
    testUntil(runJmsClient(new String(javaCmd)), logger, "Wait for Http JMS Client to access WLS");
  }

  // Download the wlthint3client.jar from Adminserver pod to local filesystem.
  // Use wlthint3client.jar in classpath to build and run the standalone
  // JMS client that sends messages to a Uniform Distributed Queue using
  // load balancer http(s) url which maps to custom channel on cluster member
  // server on WebLogic cluster.
  // Copy the installed JDK from WebLogic server pod to local filesystem 
  // to build and run the JMS client outside of K8s Cluster.
  private void buildClient() {

    assertDoesNotThrow(() -> copyFileFromPod(domainNamespace,
             adminServerPodName, "weblogic-server",
             "/u01/oracle/wlserver/server/lib/wlthint3client.jar",
             Paths.get(RESULTS_ROOT, "wlthint3client.jar")));

    assertDoesNotThrow(() -> copyFileFromPod(domainNamespace,
             adminServerPodName, "weblogic-server",
             "/u01/jdk", Paths.get(RESULTS_ROOT, "jdk")));
    StringBuffer chmodCmd = new StringBuffer("chmod +x ");
    chmodCmd.append(Paths.get(RESULTS_ROOT, "jdk/bin/java "));
    chmodCmd.append(Paths.get(RESULTS_ROOT, "jdk/bin/javac "));
    ExecResult cresult = assertDoesNotThrow(
        () -> exec(new String(chmodCmd), true));
    logger.info("chmod command {0}", chmodCmd.toString());
    logger.info("chmod command returned {0}", cresult.toString());

    // StringBuffer javacCmd = new StringBuffer("javac -cp ");
    StringBuffer javacCmd = new StringBuffer("");
    javacCmd.append(Paths.get(RESULTS_ROOT, "/jdk/bin/javac "));
    javacCmd.append(Paths.get(" -cp "));
    javacCmd.append(Paths.get(RESULTS_ROOT, "wlthint3client.jar "));
    javacCmd.append(Paths.get(RESOURCE_DIR, "tunneling", "JmsTestClient.java"));
    javacCmd.append(Paths.get(" -d "));
    javacCmd.append(Paths.get(RESULTS_ROOT));
    logger.info("javac command {0}", javacCmd.toString());
    ExecResult result = assertDoesNotThrow(
        () -> exec(new String(javacCmd), true));
    logger.info("javac returned {0}", result.toString());
    logger.info("javac returned EXIT value {0}", result.exitValue());
    assertEquals(0, result.exitValue(), "Client compilation fails");
  }

  // Build JMS Client inside the Admin Server Pod
  private void buildClientOnPod() {
    String destLocation = "/u01/JmsTestClient.java";
    assertDoesNotThrow(() -> copyFileToPod(domainNamespace,
             adminServerPodName, "weblogic-server",
             Paths.get(RESOURCE_DIR, "tunneling", "JmsTestClient.java"),
             Paths.get(destLocation)));

    String jarLocation = "/u01/oracle/wlserver/server/lib/wlthint3client.jar";
    StringBuffer javacCmd = new StringBuffer(KUBERNETES_CLI + " exec -n ");
    javacCmd.append(domainNamespace);
    javacCmd.append(" -it ");
    javacCmd.append(adminServerPodName);
    javacCmd.append(" -- /bin/bash -c \"");
    javacCmd.append("cd /u01; javac -cp ");
    javacCmd.append(jarLocation);
    javacCmd.append(" JmsTestClient.java ");
    javacCmd.append(" \"");
    logger.info("javac command {0}", javacCmd.toString());
    ExecResult result = assertDoesNotThrow(
        () -> exec(new String(javacCmd), true));
    logger.info("javac returned {0}", result.toString());
    logger.info("javac returned EXIT value {0}", result.exitValue());
    assertEquals(0, result.exitValue(), "Client compilation fails");
  }

  // Run external standalone JMS Client using wlthint3client.jar in classpath.
  // The client sends 300 messsage to a Uniform Distributed Queue.
  // Make sure that each destination get excatly 150 messages each.
  private static Callable<Boolean> runJmsClient(String javaCmd) {
    return (()  -> {
      ExecResult result = assertDoesNotThrow(() -> exec(new String(javaCmd), true));
      logger.info("java returned {0}", result.toString());
      logger.info("java returned EXIT value {0}", result.exitValue());
      return ((result.exitValue() == 0));
    });
  }

  @AfterAll
  public void tearDownAll() {
    if (!SKIP_CLEANUP) {

      // uninstall Traefik loadbalancer
      if (traefikHelmParams != null) {
        assertThat(uninstallTraefik(traefikHelmParams))
            .as("Test uninstallTraefik returns true")
            .withFailMessage("uninstallTraefik() did not return true")
            .isTrue();
      }     
    }
  }

  // Create and display SSL certificate and key using openSSL with SAN extension
  private static void createCertKeyFiles(String cn) {

    Map<String, String> sanConfigTemplateMap  = new HashMap<>();
    sanConfigTemplateMap.put("INGRESS_HOST", hostAddress);

    Path srcFile = Paths.get(RESOURCE_DIR,
        "tunneling", "san.config.template.txt");
    Path targetFile = assertDoesNotThrow(
        () -> generateFileFromTemplate(srcFile.toString(),
        "san.config.txt", sanConfigTemplateMap));
    logger.info("Generated SAN config file {0}", targetFile);

    tlsKeyFile = Paths.get(RESULTS_ROOT, domainNamespace + "-tls.key");
    tlsCertFile = Paths.get(RESULTS_ROOT, domainNamespace + "-tls.cert");
    String opcmd = "openssl req -x509 -nodes -days 365 -newkey rsa:2048 "
          + "-keyout " + tlsKeyFile + " -out " + tlsCertFile
          + " -subj \"/CN=" + cn + "\" -extensions san"
          + " -config " + Paths.get(RESULTS_ROOT, "san.config.txt");
    assertTrue(
          Command.withParams(new CommandParams()
             .command(opcmd)).execute(), "openssl req command fails");

    String opcmd2 = "openssl x509 -in " + tlsCertFile + " -noout -text ";
    assertTrue(
          Command.withParams(new CommandParams()
             .command(opcmd2)).execute(), "openssl list command fails");
  }

  // Import the certificate into a JKS TrustStore to be used while running
  // external JMS client to send message to WebLogic.
  private static void importKeytoTrustStore() {

    jksTrustFile = Paths.get(RESULTS_ROOT, domainNamespace + "-trust.jks");
    String keycmd = "keytool -import -file " + tlsCertFile
        + " --keystore " + jksTrustFile
        + " -storetype jks -storepass password -noprompt ";
    assertTrue(
          Command.withParams(new CommandParams()
             .command(keycmd)).execute(), "keytool import command fails");

    String keycmd2 = "keytool -list -keystore " + jksTrustFile
                   + " -storepass password -noprompt";
    assertTrue(
          Command.withParams(new CommandParams()
             .command(keycmd2)).execute(), "keytool list command fails");
  }

  // Create kubernetes secret from the ssl key and certificate
  private static void createSecretWithTLSCertKey(String tlsSecretName) {
    String kcmd = KUBERNETES_CLI + " create secret tls " + tlsSecretName + " --key "
          + tlsKeyFile + " --cert " + tlsCertFile + " -n " + domainNamespace;
    assertTrue(
          Command.withParams(new CommandParams()
             .command(kcmd)).execute(), KUBERNETES_CLI + " create secret command fails");
  }

  private static void createDomainResource(
      String domainUid, String domNamespace, String adminSecretName,
      String repoSecretName, String encryptionSecretName,
      int replicaCount, String configmapName) {

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
                    .replicas(replicaCount)
                    .image(MII_BASIC_IMAGE_NAME + ":" + MII_BASIC_IMAGE_TAG)
                    .imagePullPolicy(IMAGE_PULL_POLICY)
                    .addImagePullSecretsItem(new V1LocalObjectReference()
                            .name(repoSecretName))
                    .webLogicCredentialsSecret(new V1LocalObjectReference()
                            .name(adminSecretName))
                    .includeServerOutInPodLog(true)
                    .serverStartPolicy("IfNeeded")
                    .serverPod(new ServerPod()
                            .addEnvItem(new V1EnvVar()
                                    .name("JAVA_OPTIONS")
                                    .value("-Dweblogic.security.SSL.ignoreHostnameVerification=true "
                                        + "-Dweblogic.security.remoteAnonymousRMIT3Enabled=true"))
                            .addEnvItem(new V1EnvVar()
                                    .name("USER_MEM_ARGS")
                                    .value("-Djava.security.egd=file:/dev/./urandom ")))
                    .adminServer(new AdminServer()
                            .adminService(new AdminService()
                                    .addChannelsItem(new Channel()
                                            .channelName("default")
                                            .nodePort(getNextFreePort()))))
                    .configuration(new Configuration()
                            .model(new Model()
                                    .domainType("WLS")
                                    .configMap(configmapName)
                                    .runtimeEncryptionSecret(encryptionSecretName))
                        .introspectorJobActiveDeadlineSeconds(300L)));
    setPodAntiAffinity(domain);
    logger.info("Create domain custom resource for domainUid {0} in namespace {1}",
            domainUid, domNamespace);
    boolean domCreated = assertDoesNotThrow(() -> createDomainCustomResource(domain),
            String.format("Create domain custom resource failed with ApiException for %s in namespace %s",
                    domainUid, domNamespace));
    assertTrue(domCreated, String.format("Create domain custom resource failed with ApiException "
                    + "for %s in namespace %s", domainUid, domNamespace));
  }

}

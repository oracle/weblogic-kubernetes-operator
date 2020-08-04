// Copyright (c) 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import io.kubernetes.client.openapi.models.V1EnvVar;
import io.kubernetes.client.openapi.models.V1LocalObjectReference;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1SecretReference;
import oracle.weblogic.domain.AdminServer;
import oracle.weblogic.domain.AdminService;
import oracle.weblogic.domain.Channel;
import oracle.weblogic.domain.Cluster;
import oracle.weblogic.domain.Configuration;
import oracle.weblogic.domain.Domain;
import oracle.weblogic.domain.DomainSpec;
import oracle.weblogic.domain.Model;
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

import static oracle.weblogic.kubernetes.TestConstants.ADMIN_PASSWORD_DEFAULT;
import static oracle.weblogic.kubernetes.TestConstants.ADMIN_USERNAME_DEFAULT;
import static oracle.weblogic.kubernetes.TestConstants.DOMAIN_API_VERSION;
import static oracle.weblogic.kubernetes.TestConstants.K8S_NODEPORT_HOST;
import static oracle.weblogic.kubernetes.TestConstants.MII_BASIC_IMAGE_NAME;
import static oracle.weblogic.kubernetes.TestConstants.MII_BASIC_IMAGE_TAG;
import static oracle.weblogic.kubernetes.TestConstants.REPO_SECRET_NAME;
import static oracle.weblogic.kubernetes.TestConstants.VOYAGER_CHART_NAME;
import static oracle.weblogic.kubernetes.actions.ActionConstants.APP_DIR;
import static oracle.weblogic.kubernetes.actions.TestActions.getServiceNodePort;
import static oracle.weblogic.kubernetes.actions.TestActions.uninstallTraefik;
import static oracle.weblogic.kubernetes.actions.TestActions.uninstallVoyager;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.checkPodReady;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.checkServiceExists;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.createDockerRegistrySecret;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.createDomainAndVerify;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.createSecretWithTLSCertKey;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.createSecretWithUsernamePassword;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.installAndVerifyOperator;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.installAndVerifyTraefik;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.installAndVerifyVoyager;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.installVoyagerIngressAndVerify;
import static oracle.weblogic.kubernetes.utils.TestUtils.verifyClusterMemberCommunication;
import static oracle.weblogic.kubernetes.utils.ThreadSafeLogger.getLogger;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests related to WebLogic domain traffic routed by Traefik loadbalancer.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("Test Traefik loadbalancing with multiple WebLogic domains")
@IntegrationTest
public class ItLoadBalancer {

  private static String opNamespace = null;
  private static String domainNamespace = null;
  private static String traefikNamespace = null;

  private static HelmParams traefikHelmParams = null;
  private static HelmParams voyagerHelmParams = null;

  private static final String IMAGE = MII_BASIC_IMAGE_NAME + ":" + MII_BASIC_IMAGE_TAG;

  private static final String WL_SECRET_NAME = "weblogic-credentials";
  private static Path tlsCertFile;
  private static Path tlsKeyFile;

  private static Path clusterViewAppPath;
  private static LoggingFacade logger = null;

  private static final String[] domains = {"domain1", "domain2"};
  private static final int replicaCount = 2;
  private static final String managedServerNameBase = "managed-server";

  /**
   * 1. Assigns unique namespaces for operator, Traefik loadbalancer and domains.
   * 2. Installs operator.
   * 3. Creates 2 MII domains.
   * 4. Creates Ingress resource for each domain with Host based routing rules.
   * 5. Deploys clusterview sample application in cluster target in each domain.
   * 6. Creates a TLS Kubernetes for secure access to the clusters.
   * 7. Create Ingress rules for host based routing to various targets.
   *
   * @param namespaces injected by JUnit
   */
  @BeforeAll
  public static void initAll(@Namespaces(4) List<String> namespaces) {
    logger = getLogger();

    logger.info("Assign a unique namespace for operator");
    opNamespace = namespaces.get(0);

    logger.info("Assign a unique namespace for WebLogic domains");
    domainNamespace = namespaces.get(1);

    logger.info("Assign a unique namespace for Traefik");
    traefikNamespace = namespaces.get(2);

    // get a unique Voyager namespace
    logger.info("Assign a unique namespace for Voyager");
    String voyagerNamespace = namespaces.get(3);


    // install operator and verify its running in ready state
    logger.info("Installing operator");
    installAndVerifyOperator(opNamespace, domainNamespace);

    // install and verify Traefik
    logger.info("Installing Traefik controller using helm");
    traefikHelmParams = installAndVerifyTraefik(traefikNamespace, 0, 0);

    final String cloudProvider = "baremetal";
    final boolean enableValidatingWebhook = false;

    // install and verify Voyager
    voyagerHelmParams =
      installAndVerifyVoyager(voyagerNamespace, cloudProvider, enableValidatingWebhook);

    // build the clusterview application
    logger.info("Building clusterview application");
    Path distDir = BuildApplication.buildApplication(Paths.get(APP_DIR, "clusterview"), null, null,
        "dist", domainNamespace);
    assertTrue(Paths.get(distDir.toString(),
        "clusterview.war").toFile().exists(),
        "Application archive is not available");
    clusterViewAppPath = Paths.get(distDir.toString(), "clusterview.war");

    // create 2 WebLogic domains domain1 and domain2
    createDomains();

    // create TLS secret for https traffic
    for (String domainUid : domains) {
      createCertKeyFiles(domainUid);
      assertDoesNotThrow(() -> createSecretWithTLSCertKey(domainUid + "-tls-secret",
          domainNamespace, tlsKeyFile, tlsCertFile));
    }
    // create loadbalancing rules for Traefik
    createTraefikIngressRoutingRules();
    createVoyagerIngressRoutingRules();

  }


  /**
   * Test verifies admin server access through Traefik loadbalancer. Accesses the admin server for each domain through
   * Traefik loadbalancer web port and verifies it is a console login page.
   */
  @Order(1)
  @Test
  @DisplayName("Access admin server console through Traefik loadbalancer")
  public void testTraefikHostRoutingAdminServer() {
    logger.info("Verifying admin server access through Traefik");
    for (String domainUid : domains) {
      verifyAdminServerAccess(domainUid);
    }
  }

  /**
   * Test verifies multiple WebLogic domains can be loadbalanced by Traefik loadbalancer with host based routing rules.
   * Accesses the clusterview application deployed in the WebLogic cluster through Traefik loadbalancer web
   * channel and verifies it is correctly routed to the specific domain cluster identified by the -H host header.
   *
   */
  @Order(2)
  @Test
  @DisplayName("Create model in image domains domain1 and domain2 and Ingress resources")
  public void testTraefikHttpHostRoutingAcrossDomains() {
    // bind domain name in the managed servers
    for (String domain : domains) {
      bindDomainName(domain, getTraefikLbNodePort(false));
    }
    // verify load balancing works when 2 domains are running in the same namespace
    logger.info("Verifying http traffic");
    for (String domainUid : domains) {
      verifyClusterLoadbalancing(domainUid, "http", getTraefikLbNodePort(false));
    }

  }

  /**
   * Test verifies multiple WebLogic domains can be loadbalanced by Traefik loadbalancer with host based routing rules.
   * Accesses the clusterview application deployed in the WebLogic cluster through Traefik loadbalancer websecure
   * channel and verifies it is correctly routed to the specific domain cluster identified by the -H host header.
   */
  @Order(3)
  @Test
  @DisplayName("Loadbalance WebLogic cluster traffic through Traefik loadbalancer websecure channel")
  public void testTraefikHostHttpsRoutingAcrossDomains() {
    // bind domain name in the managed servers
    for (String domain : domains) {
      bindDomainName(domain, getTraefikLbNodePort(false));
    }
    logger.info("Verifying https traffic");
    for (String domainUid : domains) {
      verifyClusterLoadbalancing(domainUid, "https", getTraefikLbNodePort(true));
    }

  }

  /**
   * Test verifies multiple WebLogic domains can be loadbalanced by Voyager loadbalancer with host based routing rules.
   * Accesses the clusterview application deployed in the WebLogic cluster through Voyager loadbalancer and verifies it
   * is correctly routed to the specific domain cluster identified by the -H host header.
   */
  @Order(4)
  @Test
  @DisplayName("Loadbalance WebLogic cluster traffic through Voyager loadbalancer tcp channel")
  public void testVoyagerHostHttpRoutingAcrossDomains() {
    // verify load balancing works when 2 domains are running in the same namespace
    // bind domain name in the managed servers
    for (String domain : domains) {
      String ingressName = domain + "-ingress-host-routing";
      bindDomainName(domain, getVoyagerLbNodePort(ingressName));
    }
    logger.info("Verifying http traffic");
    for (String domainUid : domains) {
      String ingressName = domainUid + "-ingress-host-routing";
      verifyClusterLoadbalancing(domainUid, "http", getVoyagerLbNodePort(ingressName));
    }

  }

  private void verifyClusterLoadbalancing(String domainUid, String protocol, int lbPort) {

    //access application in managed servers through Traefik load balancer
    logger.info("Accessing the clusterview app through load balancer to verify all servers in cluster");
    String curlRequest = String.format("curl --silent --show-error -ks --noproxy '*' "
        + "-H 'host: %s' %s://%s:%s/clusterview/ClusterViewServlet",
        domainUid + "." + domainNamespace + "." + "cluster-1.test", protocol, K8S_NODEPORT_HOST, lbPort);
    List<String> managedServers = new ArrayList<>();
    for (int i = 1; i <= replicaCount; i++) {
      managedServers.add(managedServerNameBase + i);
    }
    assertThat(verifyClusterMemberCommunication(curlRequest, managedServers, 20))
        .as("Verify members can see other in cluster.")
        .withFailMessage("application not accessible through loadbalancer.")
        .isTrue();

    boolean hostRouting = false;
    //access application in managed servers through Traefik load balancer and bind domain in the JNDI tree
    logger.info("Verifying the requests are routed to correct domain and cluster");
    String curlCmd = String.format("curl --silent --show-error -ks --noproxy '*' "
        + "-H 'host: %s' %s://%s:%s/clusterview/ClusterViewServlet?domainTest=%s",
        domainUid + "." + domainNamespace + "." + "cluster-1.test", protocol, K8S_NODEPORT_HOST, lbPort, domainUid);

    // call the webapp and verify the bound domain name to determine
    // the requests are sent to the correct cluster members.
    for (int i = 0; i < 10; i++) {
      ExecResult result;
      try {
        logger.info(curlCmd);
        result = ExecCommand.exec(curlCmd, true);
        String response = result.stdout().trim();
        logger.info("Response for iteration {0}: exitValue {1}, stdout {2}, stderr {3}",
            i, result.exitValue(), response, result.stderr());
        if (response.contains(domainUid)) {
          hostRouting = true;
          break;
        }
      } catch (IOException | InterruptedException ex) {
        //
      }
    }
    assertTrue(hostRouting, "Host routing is not working");

  }

  private void verifyAdminServerAccess(String domainUid) {
    String consoleUrl = new StringBuffer()
        .append("http://")
        .append(K8S_NODEPORT_HOST)
        .append(":")
        .append(getTraefikLbNodePort(false))
        .append("/console/login/LoginForm.jsp").toString();

    //access application in managed servers through Traefik load balancer and bind domain in the JNDI tree
    String curlCmd = String.format("curl --silent --show-error --noproxy '*' -H 'host: %s' %s",
        domainUid + "." + domainNamespace + "." + "admin-server" + ".test", consoleUrl);

    boolean hostRouting = false;
    for (int i = 0; i < 10; i++) {
      assertDoesNotThrow(() -> TimeUnit.SECONDS.sleep(1));
      ExecResult result;
      try {
        logger.info("Accessing console using curl request iteration{0} {1}", i, curlCmd);
        result = ExecCommand.exec(curlCmd, true);
        String response = result.stdout().trim();
        logger.info("exitCode: {0}, \nstdout: {1}, \nstderr: {2}",
            result.exitValue(), response, result.stderr());
        if (response.contains("login")) {
          hostRouting = true;
          break;
        }
        //assertTrue(result.stdout().contains("Login"), "Couldn't access admin server console");
      } catch (IOException | InterruptedException ex) {
        logger.severe(ex.getMessage());
      }
    }
    assertTrue(hostRouting, "Couldn't access admin server console");
  }

  private void bindDomainName(String domainUid, int lbPort) {
    //access application in managed servers through Traefik load balancer and bind domain in the JNDI tree
    String curlCmd = String.format("curl --silent --show-error --noproxy '*' "
        + "-H 'host: %s' http://%s:%s/clusterview/ClusterViewServlet?bindDomain=%s",
        domainUid + "." + domainNamespace + "." + "cluster-1" + ".test", K8S_NODEPORT_HOST,
        lbPort, domainUid);

    // call the webapp and bind the domain name in the JNDI tree of each managed server in the cluster
    for (int i = 0; i < 10; i++) {
      assertDoesNotThrow(() -> TimeUnit.SECONDS.sleep(1));
      ExecResult result;
      try {
        logger.info("Binding domain name in managed server JNDI tree using curl iteration {0}, request {0}",
            i, curlCmd);
        result = ExecCommand.exec(curlCmd, true);
        String response = result.stdout().trim();
        logger.info("Response for iteration {0}: exitValue {1}, stdout {2}, stderr {3}",
            i, result.exitValue(), response, result.stderr());
        if (result.stdout().contains("Bound:" + domainUid)) {
          break;
        }
      } catch (IOException | InterruptedException ex) {
        //
      }
    }

  }

  private static void createTraefikIngressRoutingRules() {
    logger.info("Creating ingress rules for domain traffic routing");
    Path srcFile = Paths.get(ActionConstants.RESOURCE_DIR, "traefik/traefik-ingress-rules.yaml");
    Path dstFile = Paths.get(TestConstants.RESULTS_ROOT, "traefik/traefik-ingress-rules.yaml");
    assertDoesNotThrow(() -> {
      Files.deleteIfExists(dstFile);
      Files.createDirectories(dstFile.getParent());
      Files.write(dstFile, Files.readString(srcFile).replaceAll("@NS@", domainNamespace)
          .getBytes(StandardCharsets.UTF_8));
    });
    String command = "kubectl create"
        + " -f " + dstFile;
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
    logger.info("Getting web node port for Traefik loadbalancer {0}", traefikHelmParams.getReleaseName());
    int webNodePort = assertDoesNotThrow(()
        -> getServiceNodePort(traefikNamespace, traefikHelmParams.getReleaseName(), isHttps ? "websecure" : "web"),
        "Getting web node port for Traefik loadbalancer failed");
    return webNodePort;
  }

  private static void createVoyagerIngressRoutingRules() {
    for (String domainUid : domains) {
      String ingressName = domainUid + "-ingress-host-routing";

      // create Voyager ingress resource
      Map<String, Integer> clusterNameMsPortMap = new HashMap<>();
      clusterNameMsPortMap.put("cluster-1", 8001);
      List<String> hostNames
          = installVoyagerIngressAndVerify(domainUid, domainNamespace, ingressName, clusterNameMsPortMap);
    }
  }

  private static int getVoyagerLbNodePort(String ingressName) {
    String ingressServiceName = VOYAGER_CHART_NAME + "-" + ingressName;
    String channelName = "tcp-80";

    // get ingress service Nodeport
    int ingressServiceNodePort = assertDoesNotThrow(()
        -> getServiceNodePort(domainNamespace, ingressServiceName, channelName),
        "Getting voyager loadbalancer service node port failed");
    logger.info("Node port for {0} is: {1} :", ingressServiceName, ingressServiceNodePort);
    return ingressServiceNodePort;
  }

  private static void createDomains() {
    // Create the repo secret to pull the image
    assertDoesNotThrow(() -> createDockerRegistrySecret(domainNamespace),
        String.format("createSecret failed for %s", REPO_SECRET_NAME));

    // create WebLogic domain credentials secret
    logger.info("Creating WebLogic credentials secrets for domain");
    createSecretWithUsernamePassword(WL_SECRET_NAME, domainNamespace,
        ADMIN_USERNAME_DEFAULT, ADMIN_PASSWORD_DEFAULT);

    // create model encryption secret
    logger.info("Creating encryption secret");
    String encryptionSecretName = "encryptionsecret";
    assertDoesNotThrow(() -> createSecretWithUsernamePassword(
        encryptionSecretName,
        domainNamespace,
        "weblogicenc",
        "weblogicenc"),
        String.format("createSecret failed for %s", encryptionSecretName));

    for (String domainUid : domains) {
      // admin/managed server name here should match with model yaml in MII_BASIC_WDT_MODEL_FILE
      String adminServerPodName = domainUid + "-admin-server";
      String managedServerPrefix = domainUid + "-" + managedServerNameBase;

      // create the domain custom resource object
      Domain domain = createDomainResource(domainUid,
          domainNamespace,
          REPO_SECRET_NAME,
          encryptionSecretName,
          replicaCount,
          IMAGE);

      // create model in image domain
      logger.info("Creating model in image domain {0} in namespace {1} using docker image {2}",
          domainUid, domainNamespace, MII_BASIC_IMAGE_NAME + ":" + MII_BASIC_IMAGE_TAG);
      createDomainAndVerify(domain, domainNamespace);

      //check all servers/services are ready in the domain
      verifyDomainIsReady(domainUid, adminServerPodName, managedServerPrefix, replicaCount);

      // deploy clusterview application
      deployApplication(domainUid, adminServerPodName);
    }

  }

  private static Domain createDomainResource(String domainUid, String domNamespace,
      String repoSecretName, String encryptionSecretName, int replicaCount,
      String miiImage) {
    // create the domain CR
    return new Domain()
        .apiVersion(DOMAIN_API_VERSION)
        .kind("Domain")
        .metadata(new V1ObjectMeta()
            .name(domainUid)
            .namespace(domNamespace))
        .spec(new DomainSpec()
            .domainUid(domainUid)
            .domainHomeSourceType("FromModel")
            .image(miiImage)
            .addImagePullSecretsItem(new V1LocalObjectReference()
                .name(repoSecretName))
            .webLogicCredentialsSecret(new V1SecretReference()
                .name(WL_SECRET_NAME)
                .namespace(domNamespace))
            .includeServerOutInPodLog(true)
            .serverStartPolicy("IF_NEEDED")
            .serverPod(new ServerPod()
                .addEnvItem(new V1EnvVar()
                    .name("JAVA_OPTIONS")
                    .value("-Dweblogic.StdoutDebugEnabled=false"))
                .addEnvItem(new V1EnvVar()
                    .name("USER_MEM_ARGS")
                    .value("-Djava.security.egd=file:/dev/./urandom ")))
            .adminServer(new AdminServer()
                .serverStartState("RUNNING")
                .adminService(new AdminService()
                    .addChannelsItem(new Channel()
                        .channelName("default")
                        .nodePort(0))))
            .addClustersItem(new Cluster()
                .clusterName("cluster-1")
                .replicas(replicaCount)
                .serverStartState("RUNNING"))
            .configuration(new Configuration()
                .model(new Model()
                    .domainType("WLS")
                    .runtimeEncryptionSecret(encryptionSecretName))
                .introspectorJobActiveDeadlineSeconds(300L)));

  }

  private static void verifyDomainIsReady(String domainUid, String adminServerPodName,
      String managedServerPrefix, int replicaCount) {

    logger.info("Check admin service {0} is created in namespace {1}",
        adminServerPodName, domainNamespace);
    checkServiceExists(adminServerPodName, domainNamespace);

    // check admin server pod is ready
    logger.info("Waiting for admin server pod {0} to be ready in namespace {1}",
        adminServerPodName, domainNamespace);
    checkPodReady(adminServerPodName, domainUid, domainNamespace);

    // check managed server services created
    for (int i = 1; i <= replicaCount; i++) {
      logger.info("Check managed server service {0} is created in namespace {1}",
          managedServerPrefix + i, domainNamespace);
      checkServiceExists(managedServerPrefix + i, domainNamespace);
    }

    // check managed server pods are ready
    for (int i = 1; i <= replicaCount; i++) {
      logger.info("Wait for managed server pod {0} to be ready in namespace {1}",
          managedServerPrefix + i, domainNamespace);
      checkPodReady(managedServerPrefix + i, domainUid, domainNamespace);
    }

  }

  private static void deployApplication(String domainUid, String adminServerPodName) {
    logger.info("Getting node port for admin server default channel");
    int serviceNodePort = assertDoesNotThrow(()
        -> getServiceNodePort(domainNamespace, adminServerPodName + "-external", "default"),
        "Getting admin server node port failed");

    logger.info("Deploying application {0} in domain {1} cluster target cluster-1",
        clusterViewAppPath, domainUid);
    ExecResult result = DeployUtil.deployUsingRest(K8S_NODEPORT_HOST,
        String.valueOf(serviceNodePort),
        ADMIN_USERNAME_DEFAULT, ADMIN_PASSWORD_DEFAULT,
        "cluster-1", clusterViewAppPath, null, domainUid + "clusterview");
    assertNotNull(result, "Application deployment failed");
    logger.info("Application deployment returned {0}", result.toString());
    assertEquals("202", result.stdout(), "Deployment didn't return HTTP status code 202");
  }

  private static void createCertKeyFiles(String domainUid) {
    String cn = domainUid + "." + domainNamespace + ".cluster-1.test";
    assertDoesNotThrow(() -> {
      tlsKeyFile = Files.createTempFile("tls", ".key");
      tlsCertFile = Files.createTempFile("tls", ".crt");
      ExecCommand.exec("openssl"
          + " req -x509 "
          + " -nodes "
          + " -days 365 "
          + " -newkey rsa:2048 "
          + " -keyout " + tlsKeyFile
          + " -out " + tlsCertFile
          + " -subj /CN=" + cn,
          true);
    });
  }

  /**
   * Uninstall Traefik. The cleanup framework does not uninstall Traefik release.
   */
  @AfterAll
  public void tearDownAll() {
    // uninstall Traefik loadbalancer
    if (traefikHelmParams != null) {
      assertThat(uninstallTraefik(traefikHelmParams))
          .as("Test uninstallTraefik returns true")
          .withFailMessage("uninstallTraefik() did not return true")
          .isTrue();
    }
    // uninstall Voyager
    if (voyagerHelmParams != null) {
      assertThat(uninstallVoyager(voyagerHelmParams))
          .as("Test uninstallVoyager returns true")
          .withFailMessage("uninstallVoyager() did not return true")
          .isTrue();
    }
  }

}

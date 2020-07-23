// Copyright (c) 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes;

import java.io.IOException;
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
import org.junit.jupiter.api.Test;

import static oracle.weblogic.kubernetes.TestConstants.ADMIN_PASSWORD_DEFAULT;
import static oracle.weblogic.kubernetes.TestConstants.ADMIN_USERNAME_DEFAULT;
import static oracle.weblogic.kubernetes.TestConstants.DOMAIN_API_VERSION;
import static oracle.weblogic.kubernetes.TestConstants.K8S_NODEPORT_HOST;
import static oracle.weblogic.kubernetes.TestConstants.MII_BASIC_IMAGE_NAME;
import static oracle.weblogic.kubernetes.TestConstants.MII_BASIC_IMAGE_TAG;
import static oracle.weblogic.kubernetes.TestConstants.REPO_SECRET_NAME;
import static oracle.weblogic.kubernetes.actions.ActionConstants.APP_DIR;
import static oracle.weblogic.kubernetes.actions.TestActions.getServiceNodePort;
import static oracle.weblogic.kubernetes.actions.TestActions.uninstallTraefik;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.checkPodReady;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.checkServiceExists;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.createDockerRegistrySecret;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.createDomainAndVerify;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.createSecretWithUsernamePassword;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.createTraefikIngressForDomainAndVerify;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.installAndVerifyOperator;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.installAndVerifyTraefik;
import static oracle.weblogic.kubernetes.utils.TestUtils.verifyClusterMemberCommunication;
import static oracle.weblogic.kubernetes.utils.ThreadSafeLogger.getLogger;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests related to WebLogic domain traffic routed by traefik loadbalancer.
 */
@DisplayName("Test traefik loadbalancing with multiple WebLogic domains")
@IntegrationTest
public class ItTraefikLoadBalancer {

  private static String opNamespace = null;
  private static String domainNamespace = null;
  private static String traefikNamespace = null;

  private static HelmParams traefikHelmParams = null;

  private static final String IMAGE = MII_BASIC_IMAGE_NAME + ":" + MII_BASIC_IMAGE_TAG;

  private final String wlSecretName = "weblogic-credentials";

  private static Path clusterViewAppPath;
  private static LoggingFacade logger = null;

  /**
   * Assigns unique namespaces for operator, traefik loadbalancer and domains. Installs operator.
   *
   * @param namespaces injected by JUnit
   */
  @BeforeAll
  public static void initAll(@Namespaces(3) List<String> namespaces) {
    logger = getLogger();

    logger.info("Assign a unique namespace for operator");
    opNamespace = namespaces.get(0);

    logger.info("Assign a unique namespace for WebLogic domains");
    domainNamespace = namespaces.get(1);

    logger.info("Assign a unique namespace for traefik");
    traefikNamespace = namespaces.get(2);

    // install operator and verify its running in ready state
    logger.info("Installing operator");
    installAndVerifyOperator(opNamespace, domainNamespace);

    // install and verify traefik
    logger.info("Installing traefik controller using helm");
    traefikHelmParams = installAndVerifyTraefik(traefikNamespace, 0, 0);

    // build the clusterview application
    logger.info("Building clusterview application");
    Path distDir = BuildApplication.buildApplication(Paths.get(APP_DIR, "clusterview"), null, null,
        "dist", domainNamespace);
    assertTrue(Paths.get(distDir.toString(),
        "clusterview.war").toFile().exists(),
        "Application archive is not available");
    clusterViewAppPath = Paths.get(distDir.toString(), "clusterview.war");

  }

  /**
   * Test verifies multiple WebLogic domains can be loadbalanced by traefik loadbalancer with host based routing rules.
   * 1. Creates 2 MII domains.
   * 2. Creates Ingress resource for each domain with Host based routing rules.
   * 3. Deploys clusterview sample application in cluster target in each domain.
   * 4. Binds domain name JNDI tree of all managed servers in all domain.
   * 5. When clusterview application is accessed through traefik loadbalancer with -H host
   * header, verifies the requests are correctly routed to different clusters in different domains.
   */
  @Test
  @DisplayName("Create model in image domains domain1 and domain2 and Ingress resources")
  public void testTraefikHostRoutingAcrossDomains() {

    // Create the repo secret to pull the image
    assertDoesNotThrow(() -> createDockerRegistrySecret(domainNamespace),
        String.format("createSecret failed for %s", REPO_SECRET_NAME));

    // create WebLogic domain credentials secret
    logger.info("Creating WebLogic credentials secrets for domain");
    createSecretWithUsernamePassword(wlSecretName, domainNamespace,
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

    int replicaCount = 2;
    String managedServerNameBase = "managed-server";
    String[] domains = {"domain1", "domain2"};

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

      //create ingress resource - rules for loadbalancing
      Map<String, Integer> clusterNameMsPortMap = new HashMap<>();
      clusterNameMsPortMap.put("cluster-1", 8001);
      logger.info("Creating ingress resource for domain {0} in namespace {1}", domainUid, domainNamespace);
      createTraefikIngressForDomainAndVerify(domainUid, domainNamespace, 0, clusterNameMsPortMap, true);

      // deploy clusterview application
      deployApplication(domainUid, adminServerPodName);

      //bind domain name in the managed servers
      bindDomainName(domainUid);
    }

    // verify load balancing works when 2 domains are running in the same namespace
    for (String domainUid : domains) {
      verifyLoadbalancing(domainUid, replicaCount, managedServerNameBase);
    }
  }

  private void verifyLoadbalancing(String domainUid, int replicaCount, String managedServerNameBase) {
    //access application in managed servers through traefik load balancer
    logger.info("Accessing the clusterview app through traefik load balancer");
    String curlRequest = String.format("curl --silent --show-error --noproxy '*' "
        + "-H 'host: %s' http://%s:%s/clusterview/ClusterViewServlet",
        domainUid + "." + domainNamespace + "." + "cluster-1.test", K8S_NODEPORT_HOST, getTraefikWebNodePort());
    List<String> managedServers = new ArrayList<>();
    for (int i = 1; i <= replicaCount; i++) {
      managedServers.add(managedServerNameBase + i);
    }
    assertThat(verifyClusterMemberCommunication(curlRequest, managedServers, 20))
        .as("Verify applications from cluster can be acessed through the traefik loadbalancer.")
        .withFailMessage("application not accessible through traefik loadbalancer.")
        .isTrue();

    boolean hostRouting = false;
    //access application in managed servers through traefik load balancer and bind domain in the JNDI tree
    logger.info("Accessing the clusterview app through traefik load balancer");
    String curlCmd = String.format("curl --silent --show-error --noproxy '*' "
        + "-H 'host: %s' http://%s:%s/clusterview/ClusterViewServlet?domainTest=%s",
        domainUid + "." + domainNamespace + "." + "cluster-1.test", K8S_NODEPORT_HOST,
        getTraefikWebNodePort(), domainUid);

    // call the webapp and verify the bound domain name to determine
    // the requests are sent to the correct cluster members.
    for (int i = 0; i < 10; i++) {
      ExecResult result;
      try {
        result = ExecCommand.exec(curlCmd, true);
        String response = result.stdout().trim();
        if (response.contains(domainUid)) {
          hostRouting = true;
        }
        logger.info("Response for iteration {0}: exitValue {1}, stdout {2}, stderr {3}",
            i, result.exitValue(), response, result.stderr());
      } catch (IOException | InterruptedException ex) {
        //
      }
    }
    assertTrue(hostRouting, "Host routing is not working");

  }

  private void bindDomainName(String domainUid) {
    //access application in managed servers through traefik load balancer and bind domain in the JNDI tree
    String curlCmd = String.format("curl --silent --show-error --noproxy '*' "
        + "-H 'host: %s' http://%s:%s/clusterview/ClusterViewServlet?bindDomain=%s",
        domainUid + "." + domainNamespace + "." + "cluster-1" + ".test", K8S_NODEPORT_HOST,
        getTraefikWebNodePort(), domainUid);

    logger.info("Binding domain name in managed server JNDI tree using curl request {0}", curlCmd);

    // call the webapp and bind the domain name in the JNDI tree of each managed server in the cluster
    for (int i = 0; i < 10; i++) {
      assertDoesNotThrow(() -> TimeUnit.MILLISECONDS.sleep(100));
      ExecResult result;
      try {
        result = ExecCommand.exec(curlCmd, true);
        String response = result.stdout().trim();
        logger.info("Response for iteration {0}: exitValue {1}, stdout {2}, stderr {3}",
            i, result.exitValue(), response, result.stderr());
      } catch (IOException | InterruptedException ex) {
        //
      }
    }

  }

  private int getTraefikWebNodePort() {
    logger.info("Getting web node port for traefik loadbalancer {0}", traefikHelmParams.getReleaseName());
    int webNodePort = assertDoesNotThrow(()
        -> getServiceNodePort(domainNamespace, traefikHelmParams.getReleaseName(), "web"),
        "Getting web node port for traefik loadbalancer failed");
    return webNodePort;
  }

  private Domain createDomainResource(String domainUid, String domNamespace,
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
                .name(wlSecretName)
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

  private void verifyDomainIsReady(String domainUid, String adminServerPodName,
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

  private void deployApplication(String domainUid, String adminServerPodName) {
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

  /**
   * Uninstall traefik. The cleanup framework does not uninstall traefik release.
   */
  @AfterAll
  public void tearDownAll() {
    // uninstall traefik loadbalancer
    if (traefikHelmParams != null) {
      assertThat(uninstallTraefik(traefikHelmParams))
          .as("Test uninstallTraefik returns true")
          .withFailMessage("uninstallTraefik() did not return true")
          .isTrue();
    }
  }

}

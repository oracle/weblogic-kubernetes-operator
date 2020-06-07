// Copyright (c) 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes;

//import java.io.File;
//import java.io.FileOutputStream;
//import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
//import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
//import java.util.Properties;

import io.kubernetes.client.openapi.models.V1ClusterRole;
import io.kubernetes.client.openapi.models.V1ClusterRoleBinding;
import io.kubernetes.client.openapi.models.V1EnvVar;
import io.kubernetes.client.openapi.models.V1LocalObjectReference;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1PolicyRule;
import io.kubernetes.client.openapi.models.V1RoleBinding;
import io.kubernetes.client.openapi.models.V1RoleRef;
import io.kubernetes.client.openapi.models.V1SecretReference;
import io.kubernetes.client.openapi.models.V1Subject;
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
import oracle.weblogic.kubernetes.extensions.LoggedTest;
//import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import static oracle.weblogic.kubernetes.TestConstants.ADMIN_PASSWORD_DEFAULT;
import static oracle.weblogic.kubernetes.TestConstants.ADMIN_SERVER_NAME_BASE;
import static oracle.weblogic.kubernetes.TestConstants.ADMIN_USERNAME_DEFAULT;
import static oracle.weblogic.kubernetes.TestConstants.DOMAIN_API_VERSION;
import static oracle.weblogic.kubernetes.TestConstants.K8S_NODEPORT_HOST;
import static oracle.weblogic.kubernetes.TestConstants.MANAGED_SERVER_NAME_BASE;
import static oracle.weblogic.kubernetes.TestConstants.MII_BASIC_APP_NAME;
import static oracle.weblogic.kubernetes.TestConstants.PROJECT_ROOT;
import static oracle.weblogic.kubernetes.TestConstants.RBAC_API_GROUP;
import static oracle.weblogic.kubernetes.TestConstants.RBAC_API_VERSION;
import static oracle.weblogic.kubernetes.TestConstants.RBAC_CLUSTER_ROLE;
import static oracle.weblogic.kubernetes.TestConstants.RBAC_CLUSTER_ROLE_BINDING;
import static oracle.weblogic.kubernetes.TestConstants.RBAC_ROLE_BINDING;
import static oracle.weblogic.kubernetes.TestConstants.REPO_SECRET_NAME;
import static oracle.weblogic.kubernetes.TestConstants.WLS_DOMAIN_TYPE;
//import static oracle.weblogic.kubernetes.actions.ActionConstants.MODEL_DIR;
import static oracle.weblogic.kubernetes.actions.ActionConstants.RESOURCE_DIR;
//import static oracle.weblogic.kubernetes.actions.ActionConstants.WLS_BASE_IMAGE_NAME;
//import static oracle.weblogic.kubernetes.actions.ActionConstants.WLS_BASE_IMAGE_TAG;
import static oracle.weblogic.kubernetes.actions.TestActions.createClusterRole;
import static oracle.weblogic.kubernetes.actions.TestActions.createClusterRoleBinding;
import static oracle.weblogic.kubernetes.actions.TestActions.createRoleBinding;
import static oracle.weblogic.kubernetes.actions.TestActions.deleteClusterRole;
import static oracle.weblogic.kubernetes.actions.TestActions.deleteClusterRoleBinding;
import static oracle.weblogic.kubernetes.actions.TestActions.execCommand;
import static oracle.weblogic.kubernetes.actions.TestActions.uninstallNginx;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.checkPodExists;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.checkPodReady;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.checkServiceExists;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.createDockerRegistrySecret;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.createDomainAndVerify;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.createIngressForDomainAndVerify;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.createMiiImageAndVerify;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.createSecretWithUsernamePassword;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.dockerLoginAndPushImageToRegistry;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.installAndVerifyNginx;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.installAndVerifyOperator;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.scaleAndVerifyCluster;
import static oracle.weblogic.kubernetes.utils.FileUtils.copyFileToPod;
import static oracle.weblogic.kubernetes.utils.TestUtils.getNextFreePort;
//import static oracle.weblogic.kubernetes.utils.WLSTUtils.executeWLSTScript;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Verify the model in image domain with multiple clusters can be scaled up and down.
 * Also verify the sample application can be accessed via NGINX ingress controller.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("Verify scaling multiple clusters domain and the sample application can be accessed via NGINX")
@IntegrationTest
class ItScaleMiiDomainNginx implements LoggedTest {

  // mii constants
  private static final String WDT_MODEL_FILE = "model-multiclusterdomain-sampleapp-wls.yaml";
  private static final String MII_IMAGE_NAME = "mii-image";

  // domain constants
  private static final String domainUid = "domain1";
  private static final int NUMBER_OF_CLUSTERS = 2;
  private static final String CLUSTER_NAME_PREFIX = "cluster-";
  private static final int MANAGED_SERVER_PORT = 8001;
  private static final int replicaCount = 2;

  // rbac constants
  private static final String CLUSTER_ROLE_NAME = "weblogic-domain-cluster-role";
  private static final String CLUSTER_ROLE_BINDING_NAME = "domain-cluster-rolebinding";

  private static String opNamespace = null;
  private static String domainNamespace = null;
  private static String opServiceAccount = null;
  private static String adminServerPodName = null;
  private static HelmParams nginxHelmParams = null;
  private static int nodeportshttp = 0;
  private static int externalRestHttpsPort = 0;
  private static int t3ChannelPort = 0;

  private String curlCmd = null;

  /**
   * Install operator and NGINX. Create model in image domain with multiple clusters.
   * Create ingress for the domain.
   *
   * @param namespaces list of namespaces created by the IntegrationTestWatcher by the
   *                   JUnit engine parameter resolution mechanism
   */
  @BeforeAll
  public static void initAll(@Namespaces(3) List<String> namespaces) {

    // get a unique operator namespace
    logger.info("Get a unique namespace for operator");
    assertNotNull(namespaces.get(0), "Namespace list is null");
    opNamespace = namespaces.get(0);

    // get a unique domain namespace
    logger.info("Get a unique namespace for WebLogic domain");
    assertNotNull(namespaces.get(1), "Namespace list is null");
    domainNamespace = namespaces.get(1);

    // get a unique NGINX namespace
    logger.info("Get a unique namespace for NGINX");
    assertNotNull(namespaces.get(2), "Namespace list is null");
    String nginxNamespace = namespaces.get(2);

    // set the service account name for the operator
    opServiceAccount = opNamespace + "-sa";

    // get a free port for external REST HTTPS port
    externalRestHttpsPort = getNextFreePort(31001, 31201);

    // install and verify operator with REST API
    installAndVerifyOperator(opNamespace, opServiceAccount, true, externalRestHttpsPort, domainNamespace);

    // get a free node port for NGINX
    nodeportshttp = getNextFreePort(30305, 30405);
    int nodeportshttps = getNextFreePort(30443, 30543);

    // install and verify NGINX
    nginxHelmParams = installAndVerifyNginx(nginxNamespace, nodeportshttp, nodeportshttps);

    // get a free port for t3ChannelPort
    t3ChannelPort = getNextFreePort(30012, 30102);

    // create model in image domain with multiple clusters
    createMiiDomainWithMultiClusters();

    // create ingress using host based routing
    Map<String, Integer> clusterNameMsPortMap = new HashMap<>();
    for (int i = 1; i <= NUMBER_OF_CLUSTERS; i++) {
      clusterNameMsPortMap.put(CLUSTER_NAME_PREFIX + i, MANAGED_SERVER_PORT);
    }
    logger.info("Creating ingress for domain {0} in namespace {1}", domainUid, domainNamespace);
    createIngressForDomainAndVerify(domainUid, domainNamespace, clusterNameMsPortMap);
  }

  //@Test
  @DisplayName("Verify scale each cluster of the domain by patching domain resource")
  public void testScaleClustersByPatchingDomainResource() {

    for (int i = 1; i <= NUMBER_OF_CLUSTERS; i++) {

      String clusterName = CLUSTER_NAME_PREFIX + i;
      int numberOfServers;
      // scale cluster-1 to 1 server and cluster-2 to 3 servers
      if (i == 1) {
        numberOfServers = 1;
      } else {
        numberOfServers = 3;
      }

      logger.info("Scaling cluster {0} of domain {1} in namespace {2} to {3} servers.",
          clusterName, domainUid, domainNamespace, numberOfServers);
      curlCmd = generateCurlCmd(clusterName);
      List<String> managedServersBeforeScale = listManagedServersBeforeScale(clusterName, replicaCount);
      scaleClusterAndVerifyByPatchingDomainResource(clusterName, replicaCount, numberOfServers,
          managedServersBeforeScale);

      // then scale cluster-1 and cluster-2 to 2 servers
      logger.info("Scaling cluster {0} of domain {1} in namespace {2} from {3} servers to {4} servers.",
          clusterName, domainUid, domainNamespace, numberOfServers, replicaCount);
      managedServersBeforeScale = listManagedServersBeforeScale(clusterName, numberOfServers);
      scaleClusterAndVerifyByPatchingDomainResource(clusterName, numberOfServers, replicaCount,
          managedServersBeforeScale);
    }
  }

  //@Test
  @DisplayName("Verify scale each cluster of the domain by calling REST API")
  public void testScaleClustersWithRestApi() {

    for (int i = 1; i <= NUMBER_OF_CLUSTERS; i++) {

      String clusterName = CLUSTER_NAME_PREFIX + i;
      int numberOfServers;
      // scale cluster-1 to 1 server and cluster-2 to 3 servers
      if (i == 1) {
        numberOfServers = 1;
      } else {
        numberOfServers = 3;
      }

      logger.info("Scaling cluster {0} of domain {1} in namespace {2} from {3} servers to {4} servers.",
          clusterName, domainUid, domainNamespace, replicaCount, numberOfServers);
      curlCmd = generateCurlCmd(clusterName);
      List<String> managedServersBeforeScale = listManagedServersBeforeScale(clusterName, replicaCount);
      scaleClusterAndVerifyWithRestApi(clusterName, replicaCount, numberOfServers, managedServersBeforeScale);

      // then scale cluster-1 and cluster-2 to 2 servers
      logger.info("Scaling cluster {0} of domain {1} in namespace {2} from {3} servers to {4} servers.",
          clusterName, domainUid, domainNamespace, numberOfServers, replicaCount);
      managedServersBeforeScale = listManagedServersBeforeScale(clusterName, numberOfServers);
      scaleClusterAndVerifyWithRestApi(clusterName, numberOfServers, replicaCount, managedServersBeforeScale);
    }
  }

  @Test
  @DisplayName("Verify scale each cluster of the domain by calling REST API")
  public void testScaleClustersWithWLDF() {
    // create custer role, cluster role binding and role binding used by WLDF action script
    createClusterRoleAndClusterRoleBindings(domainNamespace, opNamespace);

    // copy scalingAction.sh to Admin Server pod
    assertDoesNotThrow(() -> execCommand(domainNamespace, adminServerPodName, null, true,
        "/bin/sh", "-c", "mkdir -p /u01/domains/domain1/bin/scripts"), "execCommand failed");

    assertDoesNotThrow(() -> copyFileToPod(domainNamespace, adminServerPodName, null,
        Paths.get(PROJECT_ROOT + "/../src/scripts/scaling/scalingAction.sh"),
        Paths.get("/u01/domains/domain1/bin/scripts/scalingAction.sh")),
        "copyFileToPod failed with Exception");

    assertDoesNotThrow(() -> execCommand(domainNamespace, adminServerPodName, null,
        true,
        "/bin/sh", "-c", "chmod +x /u01/domains/domain1/bin/scripts/scalingAction.sh"),
        "execCommand failed");

    // copy wldf.py and callscript.sh to Admin Server pod
    assertDoesNotThrow(() -> copyFileToPod(domainNamespace, adminServerPodName, null,
        Paths.get(RESOURCE_DIR, "python-scripts", "wldf.py"),
        Paths.get("/u01/oracle/wldf.py")),
        "copyFileToPod failed with Exception");

    assertDoesNotThrow(() -> copyFileToPod(domainNamespace, adminServerPodName, null,
        Paths.get(RESOURCE_DIR, "python-scripts", "callpyscript.sh"),
        Paths.get("/u01/oracle/callpyscript.sh")),
        "copyFileToPod failed with Exception");

    assertDoesNotThrow(() -> execCommand(domainNamespace, adminServerPodName, null,
        true,
        "/bin/sh", "-c", "chmod +x /u01/oracle/callpyscript.sh"), "execCommand failed");

    String command = new StringBuffer("/u01/oracle/callpyscript.sh /u01/oracle/wldf.py ")
        .append(ADMIN_USERNAME_DEFAULT)
        .append(" ")
        .append(ADMIN_PASSWORD_DEFAULT)
        .append(" t3://")
        .append(adminServerPodName)
        .append(":7001 ")
        .append("scaleUp ")
        .append(domainUid)
        .append(" cluster-1 ")
        .append(domainNamespace)
        .append(" ")
        .append(opNamespace)
        .append(" ")
        .append("myear").toString();

    assertDoesNotThrow(() -> execCommand(domainNamespace, adminServerPodName, null,
        true, "/bin/sh", "-c", command), "execCommand failed");

    /*
    assertDoesNotThrow(() -> execCommand(domainNamespace, "domain1-admin-server", "weblogic-server",
        true, "/bin/sh", "-c", "echo testing-in-domain1-admin-server"), "execCommand failed");

    assertDoesNotThrow(() -> execCommand(domainNamespace, "domain1-admin-server", "weblogic-server",
        true, "/bin/sh", "-c", "echo $KUBERNETES_SERVICE_HOST"), "execCommand failed");

    assertDoesNotThrow(() -> execCommand(domainNamespace, "domain1-admin-server", "weblogic-server",
        true, "/bin/sh", "-c", "echo $KUBERNETES_SERVICE_PORT"), "execCommand failed");

    assertDoesNotThrow(() -> execCommand(domainNamespace, "domain1-admin-server", "weblogic-server",
        true, "/bin/sh", "-c", "echo $INTERNAL_OPERATOR_CERT"), "execCommand failed");

    assertDoesNotThrow(() -> execCommand(domainNamespace, "domain1-admin-server", "weblogic-server",
        true, "/bin/sh", "-c", "echo $DOMAIN_HOME"), "execCommand failed");
    */
    // create a temporary WebLogic WLST property file
    /*
    File wlstPropertiesFile = assertDoesNotThrow(() -> File.createTempFile("wlst", "properties"),
        "Creating WLST properties file failed");

    Properties p = new Properties();
    p.setProperty("username", ADMIN_USERNAME_DEFAULT);
    p.setProperty("password", ADMIN_PASSWORD_DEFAULT);
    //p.setProperty("admin_host", K8S_NODEPORT_HOST);
    //p.setProperty("admin_port", Integer.toString(t3ChannelPort));
    //p.setProperty("aurl", "t3://domain1-admin-server:7001");
    p.setProperty("aurl", "t3://" + K8S_NODEPORT_HOST + ":" + t3ChannelPort);
    p.setProperty("scaleaAction", "scaleUp");
    p.setProperty("domainUid", domainUid);
    p.setProperty("clusterName", "cluster-1");
    p.setProperty("domainNamespace", domainNamespace);
    p.setProperty("opNamespace", opNamespace);
    p.setProperty("domain_home", "/u01/domains/domain1");
    assertDoesNotThrow(() -> p.store(new FileOutputStream(wlstPropertiesFile), "wlst properties file"),
        "Failed to write the WLST properties to file");

    Path wldfScript = Paths.get(RESOURCE_DIR, "python-scripts", "wldf.py");
    executeWLSTScript(wldfScript, wlstPropertiesFile.toPath(), domainNamespace);
    */
  }

  /**
   * TODO: remove this after Sankar's PR is merged
   * The cleanup framework does not uninstall NGINX release. Do it here for now.
   */
  //@AfterAll
  public void tearDownAll() {
    // uninstall NGINX release

    if (nginxHelmParams != null) {
      assertThat(uninstallNginx(nginxHelmParams))
          .as("Test uninstallNginx returns true")
          .withFailMessage("uninstallNginx() did not return true")
          .isTrue();
    }

    deleteClusterRoleBinding(CLUSTER_ROLE_BINDING_NAME);
    assertThat(assertDoesNotThrow(() -> deleteClusterRole(CLUSTER_ROLE_NAME),
        "deleteClusterRole failed with ApiException"))
        .as("Test delete cluster role returns true")
        .withFailMessage("deleteClusterRole() did not return true")
        .isTrue();
  }

  /**
   * Create model in image domain with multiple clusters.
   */
  private static void createMiiDomainWithMultiClusters() {

    // admin/managed server name here should match with model yaml in WDT_MODEL_FILE
    adminServerPodName = domainUid + "-" + ADMIN_SERVER_NAME_BASE;

    // create image with model files
    logger.info("Creating image with model file and verify");
    String miiImage = createMiiImageAndVerify(MII_IMAGE_NAME, WDT_MODEL_FILE, MII_BASIC_APP_NAME);

    /*
    List<String> appSrcDirList = new ArrayList<>();
    appSrcDirList.add(MII_BASIC_APP_NAME);
    appSrcDirList.add("opensessionapp");
    String miiImage =
        createMiiImageAndVerify(MII_IMAGE_NAME, Collections.singletonList(MODEL_DIR + "/" + WDT_MODEL_FILE),
            appSrcDirList, WLS_BASE_IMAGE_NAME, WLS_BASE_IMAGE_TAG, WLS_DOMAIN_TYPE, false);
    */

    // docker login and push image to docker registry if necessary
    dockerLoginAndPushImageToRegistry(miiImage);

    // create docker registry secret to pull the image from registry
    logger.info("Creating docker registry secret in namespace {0}", domainNamespace);
    createDockerRegistrySecret(domainNamespace);

    // create secret for admin credentials
    logger.info("Creating secret for admin credentials");
    String adminSecretName = "weblogic-credentials";
    createSecretWithUsernamePassword(adminSecretName, domainNamespace, "weblogic", "welcome1");

    // create encryption secret
    logger.info("Creating encryption secret");
    String encryptionSecretName = "encryptionsecret";
    createSecretWithUsernamePassword(encryptionSecretName, domainNamespace, "weblogicenc", "weblogicenc");

    // construct the cluster list used for domain custom resource
    List<Cluster> clusterList = new ArrayList<>();
    for (int i = NUMBER_OF_CLUSTERS; i >= 1; i--) {
      clusterList.add(new Cluster()
          .clusterName(CLUSTER_NAME_PREFIX + i)
          .replicas(replicaCount)
          .serverStartState("RUNNING"));
    }

    // create the domain CR
    Domain domain = new Domain()
        .apiVersion(DOMAIN_API_VERSION)
        .kind("Domain")
        .metadata(new V1ObjectMeta()
            .name(domainUid)
            .namespace(domainNamespace))
        .spec(new DomainSpec()
            .domainUid(domainUid)
            .domainHomeSourceType("FromModel")
            .image(miiImage)
            .addImagePullSecretsItem(new V1LocalObjectReference()
                .name(REPO_SECRET_NAME))
            .webLogicCredentialsSecret(new V1SecretReference()
                .name(adminSecretName)
                .namespace(domainNamespace))
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
                        .nodePort(0))
                    .addChannelsItem(new Channel()
                        .channelName("T3Channel")
                        .nodePort(t3ChannelPort))))
            .clusters(clusterList)
            .configuration(new Configuration()
                .model(new Model()
                    .domainType(WLS_DOMAIN_TYPE)
                    .runtimeEncryptionSecret(encryptionSecretName))));

    // create model in image domain
    logger.info("Creating model in image domain {0} in namespace {1} using docker image {2}",
        domainUid, domainNamespace, miiImage);
    createDomainAndVerify(domain, domainNamespace);

    // check admin server pod exists in domain namespace
    logger.info("Checking that admin server pod {0} exists in namespace {1}",
        adminServerPodName, domainNamespace);
    checkPodExists(adminServerPodName, domainUid, domainNamespace);

    // check admin server pod is ready
    logger.info("Checking that admin server pod {0} is ready in namespace {1}",
        adminServerPodName, domainNamespace);
    checkPodReady(adminServerPodName, domainUid, domainNamespace);

    // check admin service exists in the domain namespace
    logger.info("Checking that admin service {0} exists in namespace {1}",
        adminServerPodName, domainNamespace);
    checkServiceExists(adminServerPodName, domainNamespace);

    // check the readiness for the managed servers in each cluster
    for (int i = 1; i <= NUMBER_OF_CLUSTERS; i++) {
      for (int j = 1; j <= replicaCount; j++) {
        String managedServerPodName =
            domainUid + "-" + CLUSTER_NAME_PREFIX + i + "-" + MANAGED_SERVER_NAME_BASE + j;

        // check managed server pod exists in the namespace
        logger.info("Checking that managed server pod {0} exists in namespace {1}",
            managedServerPodName, domainNamespace);
        checkPodExists(managedServerPodName, domainUid, domainNamespace);

        // check managed server pod is ready
        logger.info("Checking that managed server pod {0} is ready in namespace {1}",
            managedServerPodName, domainNamespace);
        checkPodReady(managedServerPodName, domainUid, domainNamespace);

        // check managed server service exists in the domain namespace
        logger.info("Checking that managed server service {0} exists in namespace {1}",
            managedServerPodName, domainNamespace);
        checkServiceExists(managedServerPodName, domainNamespace);
      }
    }
  }

  /**
   * Generate the curl command to access the sample app from the ingress controller.
   *
   * @param clusterName WebLogic cluster name which is the backend of the ingress
   * @return curl command string
   */
  private String generateCurlCmd(String clusterName) {

    return String.format("curl --silent --show-error --noproxy '*' -H 'host: %s' http://%s:%s/sample-war/index.jsp",
        domainUid + "." + clusterName + ".test", K8S_NODEPORT_HOST, nodeportshttp);
  }

  /**
   * Generate a server list which contains all managed servers in the cluster before scale.
   *
   * @param clusterName         the name of the WebLogic cluster
   * @param replicasBeforeScale the replicas of WebLogic cluster before scale
   * @return list of managed servers in the cluster before scale
   */
  private List<String> listManagedServersBeforeScale(String clusterName, int replicasBeforeScale) {
    List<String> managedServerNames = new ArrayList<>();
    for (int i = 1; i <= replicasBeforeScale; i++) {
      managedServerNames.add(clusterName + "-" + MANAGED_SERVER_NAME_BASE + i);
    }

    return managedServerNames;
  }

  /**
   * Scale a cluster using REST API.
   *
   * @param clusterName               cluster name to scale
   * @param replicasBeforeScale       number of servers in cluster before scaling
   * @param replicasAfterScale        number of servers in cluster after scaling
   * @param managedServersBeforeScale list of managed servers in the cluster before scale
   */
  private void scaleClusterAndVerifyWithRestApi(String clusterName,
                                                int replicasBeforeScale,
                                                int replicasAfterScale,
                                                List<String> managedServersBeforeScale) {
    scaleAndVerifyCluster(clusterName, domainUid, domainNamespace,
        domainUid + "-" + clusterName + "-" + MANAGED_SERVER_NAME_BASE,
        replicasBeforeScale, replicasAfterScale, true, externalRestHttpsPort, opNamespace, opServiceAccount,
        curlCmd, managedServersBeforeScale);
  }

  /**
   * Scale a cluster by patching a domain resource.
   *
   * @param clusterName               cluster name to scale
   * @param replicasBeforeScale       number of servers in cluster before scaling
   * @param replicasAfterScale        number of servers in cluster after scaling
   * @param managedServersBeforeScale list of managed servers in the cluster before scale
   */
  private void scaleClusterAndVerifyByPatchingDomainResource(String clusterName,
                                                             int replicasBeforeScale,
                                                             int replicasAfterScale,
                                                             List<String> managedServersBeforeScale) {
    scaleAndVerifyCluster(clusterName, domainUid, domainNamespace,
        domainUid + "-" + clusterName + "-" + MANAGED_SERVER_NAME_BASE,
        replicasBeforeScale, replicasAfterScale, curlCmd, managedServersBeforeScale);
  }

  /**
   * Create cluster role, cluster role binding and role binding used by WLDF script action.
   *
   * @param domainNamespace namespace where WebLogic domain exists
   * @param opNamespace namespace where WebLogic operator exists
   */
  private void createClusterRoleAndClusterRoleBindings(String domainNamespace,
                                                       String opNamespace) {

    // create cluster role
    V1ClusterRole v1ClusterRole = new V1ClusterRole()
        .kind(RBAC_CLUSTER_ROLE)
        .apiVersion(RBAC_API_VERSION)
        .metadata(new V1ObjectMeta()
          .name(CLUSTER_ROLE_NAME))
        .addRulesItem(new V1PolicyRule()
          .addApiGroupsItem("weblogic.oracle")
          .addResourcesItem("domains")
          .addVerbsItem("get")
          .addVerbsItem("list")
          .addVerbsItem("update"))
        .addRulesItem(new V1PolicyRule()
          .addApiGroupsItem("apiextensions.k8s.io")
          .addResourcesItem("customresourcedefinitions")
          .addVerbsItem("get")
          .addVerbsItem("list"));

    assertDoesNotThrow(() -> createClusterRole(v1ClusterRole), "createClusterRole failed with ApiException");

    // create cluster role binding
    V1ClusterRoleBinding v1ClusterRoleBinding = new V1ClusterRoleBinding()
        .kind(RBAC_CLUSTER_ROLE_BINDING)
        .apiVersion(RBAC_API_VERSION)
        .metadata(new V1ObjectMeta()
          .name(CLUSTER_ROLE_BINDING_NAME))
        .addSubjectsItem(new V1Subject()
          .kind("ServiceAccount")
          .name("default")
          .namespace(domainNamespace)
          .apiGroup(""))
        .roleRef(new V1RoleRef()
          .kind(RBAC_CLUSTER_ROLE)
          .name(CLUSTER_ROLE_NAME)
          .apiGroup(RBAC_API_GROUP));

    assertDoesNotThrow(() -> createClusterRoleBinding(v1ClusterRoleBinding),
        "createClusterRoleBinding failed with ApiException");

    // create domain operator role binding
    V1RoleBinding v1RoleBinding = new V1RoleBinding()
        .kind(RBAC_ROLE_BINDING)
        .apiVersion(RBAC_API_VERSION)
        .metadata(new V1ObjectMeta()
            .name("weblogic-domain-operator-rolebinding")
            .namespace(opNamespace))
        .addSubjectsItem(new V1Subject()
            .kind("ServiceAccount")
            .name("default")
            .namespace(domainNamespace)
            .apiGroup(""))
        .roleRef(new V1RoleRef()
            .kind(RBAC_CLUSTER_ROLE)
            .name("cluster-admin")
            .apiGroup(RBAC_API_GROUP));

    assertDoesNotThrow(() -> createRoleBinding(opNamespace, v1RoleBinding),
        "createRoleBinding failed with ApiException");
  }
}
// Copyright (c) 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes;

import java.io.File;
import java.io.FileOutputStream;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.Callable;

import io.kubernetes.client.custom.V1Patch;
import io.kubernetes.client.openapi.models.V1Container;
import io.kubernetes.client.openapi.models.V1EnvVar;
import io.kubernetes.client.openapi.models.V1LocalObjectReference;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1PersistentVolumeClaimVolumeSource;
import io.kubernetes.client.openapi.models.V1SecretReference;
import io.kubernetes.client.openapi.models.V1Volume;
import io.kubernetes.client.openapi.models.V1VolumeMount;
import oracle.weblogic.domain.AdminServer;
import oracle.weblogic.domain.AdminService;
import oracle.weblogic.domain.Channel;
import oracle.weblogic.domain.Cluster;
import oracle.weblogic.domain.Domain;
import oracle.weblogic.domain.DomainSpec;
import oracle.weblogic.domain.ServerPod;
import oracle.weblogic.kubernetes.annotations.IntegrationTest;
import oracle.weblogic.kubernetes.annotations.Namespaces;
import oracle.weblogic.kubernetes.logging.LoggingFacade;
import oracle.weblogic.kubernetes.utils.BuildApplication;
import oracle.weblogic.kubernetes.utils.ExecResult;
import oracle.weblogic.kubernetes.utils.OracleHttpClient;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import static oracle.weblogic.kubernetes.TestConstants.ADMIN_PASSWORD_DEFAULT;
import static oracle.weblogic.kubernetes.TestConstants.ADMIN_USERNAME_DEFAULT;
import static oracle.weblogic.kubernetes.TestConstants.DOMAIN_API_VERSION;
import static oracle.weblogic.kubernetes.TestConstants.K8S_NODEPORT_HOST;
import static oracle.weblogic.kubernetes.TestConstants.OKD;
import static oracle.weblogic.kubernetes.TestConstants.WEBLOGIC_IMAGE_TO_USE_IN_SPEC;
import static oracle.weblogic.kubernetes.actions.ActionConstants.APP_DIR;
import static oracle.weblogic.kubernetes.actions.ActionConstants.RESOURCE_DIR;
import static oracle.weblogic.kubernetes.actions.ActionConstants.WORK_DIR;
import static oracle.weblogic.kubernetes.actions.TestActions.getNextIntrospectVersion;
import static oracle.weblogic.kubernetes.actions.TestActions.getServiceNodePort;
import static oracle.weblogic.kubernetes.actions.TestActions.getServicePort;
import static oracle.weblogic.kubernetes.actions.impl.Domain.patchDomainCustomResource;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.checkPodReadyAndServiceExists;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.getNextFreePort;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.getUniqueName;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.withLongRetryPolicy;
import static oracle.weblogic.kubernetes.utils.ConfigMapUtils.createConfigMapForDomainCreation;
import static oracle.weblogic.kubernetes.utils.DeployUtil.deployUsingRest;
import static oracle.weblogic.kubernetes.utils.DomainUtils.createDomainAndVerify;
import static oracle.weblogic.kubernetes.utils.JobUtils.createDomainJob;
import static oracle.weblogic.kubernetes.utils.JobUtils.getIntrospectJobName;
import static oracle.weblogic.kubernetes.utils.OKDUtils.createRouteForOKD;
import static oracle.weblogic.kubernetes.utils.OperatorUtils.installAndVerifyOperator;
import static oracle.weblogic.kubernetes.utils.PersistentVolumeUtils.createPV;
import static oracle.weblogic.kubernetes.utils.PersistentVolumeUtils.createPVC;
import static oracle.weblogic.kubernetes.utils.PodUtils.checkPodDoesNotExist;
import static oracle.weblogic.kubernetes.utils.PodUtils.checkPodExists;
import static oracle.weblogic.kubernetes.utils.PodUtils.getExternalServicePodName;
import static oracle.weblogic.kubernetes.utils.PodUtils.setPodAntiAffinity;
import static oracle.weblogic.kubernetes.utils.SecretUtils.createSecretWithUsernamePassword;
import static oracle.weblogic.kubernetes.utils.SecretUtils.createSecretsForImageRepos;
import static oracle.weblogic.kubernetes.utils.ThreadSafeLogger.getLogger;
import static oracle.weblogic.kubernetes.utils.WLSTUtils.executeWLSTScript;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests related to large capacity domain and multiple clusters.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("Verify the Operator can handle multiple domains and clusters at the same time.")
@IntegrationTest
@Tag("okdenv")
class ItLargeCapacityDomainsClustersDPV {

  private static String opNamespace = null;

  private static int numOfDomains = 10;
  private static int numOfClusters = 10;
  private static final String baseDomainUid = "domain";
  private static List<String> domainNamespaces;

  private static String domainNamespace;
  private static final String domainUid = "mydomain";
  private static final String cluster1Name = "mycluster";
  private static final String adminServerName = "admin-server";
  private static final String adminServerPodName = domainUid + "-" + adminServerName;
  private static final String cluster1ManagedServerNameBase = cluster1Name + "-managed-server";
  private static final String cluster1ManagedServerPodNamePrefix = domainUid + "-" + cluster1ManagedServerNameBase;

  private static int clusterReplicaCount = 2;
  private static final int t3ChannelPort = getNextFreePort();
  private static final String wlSecretName = "weblogic-credentials";
  private static String wlsUserName = ADMIN_USERNAME_DEFAULT;
  private static String wlsPassword = ADMIN_PASSWORD_DEFAULT;

  private static String adminSvcExtHost = null;
  private static String clusterRouteHost = null;
  private static Path clusterViewAppPath;
  private static LoggingFacade logger;
  private static final int managedServerPort = 7100;

  /**
   * Assigns unique namespaces for operator and domains. Installs operator. Creates a WebLogic domain.
   *
   * @param namespaces injected by JUnit
   */
  @BeforeAll
  public static void initAll(@Namespaces(50) List<String> namespaces) {
    logger = getLogger();
    logger.info("Assign a unique namespace for operator");
    opNamespace = namespaces.get(0);
    logger.info("Assign a unique namespaces for WebLogic domains");
    domainNamespaces = namespaces.subList(1, numOfDomains + 1);
    domainNamespace = namespaces.get(numOfDomains + 1);

    // install operator and verify its running in ready state
    installAndVerifyOperator(opNamespace, namespaces.subList(1, 50).toArray(new String[0]));

    // build the clusterview application
    Path targetDir = Paths.get(WORK_DIR,
        ItLargeCapacityDomainsClustersDPV.class.getName() + "/clusterviewapp");
    Path distDir = BuildApplication.buildApplication(Paths.get(APP_DIR, "clusterview"), null, null,
        "dist", domainNamespace, targetDir);
    assertTrue(Paths.get(distDir.toString(),
        "clusterview.war").toFile().exists(),
        "Application archive is not available");
    clusterViewAppPath = Paths.get(distDir.toString(), "clusterview.war");

    createDomain(domainNamespace, domainUid);
    // verify the admin server service and pod created
    checkPodReadyAndServiceExists(adminServerPodName, domainUid, domainNamespace);
    // verify managed server services and pods are created
    for (int i = 1; i <= clusterReplicaCount; i++) {
      logger.info("Checking managed server service and pod {0} is created in namespace {1}",
          cluster1ManagedServerPodNamePrefix + i, domainNamespace);
      checkPodReadyAndServiceExists(cluster1ManagedServerPodNamePrefix + i, domainUid, domainNamespace);
    }
    List<String> managedServerNames = new ArrayList<>();
    for (int i = 1; i <= clusterReplicaCount; i++) {
      managedServerNames.add(cluster1ManagedServerNameBase + i);
    }
    deployAndVerifyMemberHealth(adminServerPodName, managedServerNames, domainNamespace);

  }

  /**
   * Test brings up new domains and verifies it can successfully start by doing the following.
   * 
   * a. Creates new WebLogic domains using offline WLST in persistent volume. 
   * b. Creates domain resource and deploys in Kubernetes cluster. 
   * c. Verifies the servers in the new WebLogic domain comes up.
   */
  @Order(1)
  @Test
  @DisplayName("Test domains creation")
  void testCreateDomains() {
    String domainUid;
    String adminServerPodName;;
    String clusterName = "cluster1";
    String clusterManagedServerNameBase = clusterName + "-managed-server";
    String clusterManagedServerPodNamePrefix;

    for (int i = 0; i < numOfDomains; i++) {
      domainUid = baseDomainUid + (i + 1);
      adminServerPodName = domainUid + "-" + adminServerName;
      clusterManagedServerPodNamePrefix = domainUid + "-" + clusterManagedServerNameBase;
      createDomain(domainNamespaces.get(i), domainUid, clusterName, clusterManagedServerNameBase);
      // verify the admin server service and pod created
      checkPodReadyAndServiceExists(adminServerPodName, domainUid, domainNamespaces.get(i));
      // verify managed server services created
      for (int j = 1; j <= clusterReplicaCount; j++) {
        logger.info("Checking managed server service and pod {0} is created in namespace {1}",
            clusterManagedServerPodNamePrefix + j, domainNamespaces.get(i));
        checkPodReadyAndServiceExists(clusterManagedServerPodNamePrefix + j, domainUid, domainNamespaces.get(i));
      }

      List<String> managedServerNames = new ArrayList<>();
      for (int j = 1; j <= clusterReplicaCount; j++) {
        managedServerNames.add(clusterManagedServerNameBase + j);
      }
      //verify admin server accessibility and the health of cluster members
      deployAndVerifyMemberHealth(adminServerPodName, managedServerNames, domainNamespaces.get(i));
      //shutdownDomain(domainUid, domainNamespaces.get(i));
    }
  }

  /**
   * Test creates new clusters and verifies it can successfully start by doing the following. 
   * 
   * a. Creates new WebLogic static clusters using online WLST. 
   * b. Patch the Domain Resource with clusters 
   * c. Update the introspectVersion version 
   * d. Verifies the servers in the new WebLogic cluster comes up.
   * e. Repeat the above cycle for a number of clusters.
   * Bug - OWLS-102898
   */
  @Disabled
  @Order(2)
  @Test
  @DisplayName("Test new clusters creation on demand using WLST and introspection")
  void testCreateNewClusters() {

    logger.info("Getting port for default channel");
    int adminServerPort
        = getServicePort(domainNamespace, getExternalServicePodName(adminServerPodName), "default");

    String clusterBaseName = "cluster-";

    for (int j = 1; j <= numOfClusters; j++) {
      String clusterName = clusterBaseName + j;
      String clusterManagedServerNameBase = clusterName + "-ms-";
      String clusterManagedServerPodNamePrefix = domainUid + "-" + clusterManagedServerNameBase;

      // create a temporary WebLogic WLST property file
      File wlstPropertiesFile = assertDoesNotThrow(() -> File.createTempFile("wlst", "properties"),
          "Creating WLST properties file failed");
      Properties p = new Properties();
      p.setProperty("admin_host", adminServerPodName);
      p.setProperty("admin_port", Integer.toString(adminServerPort));
      p.setProperty("admin_username", wlsUserName);
      p.setProperty("admin_password", wlsPassword);
      p.setProperty("test_name", "create_cluster");
      p.setProperty("cluster_name", clusterName);
      p.setProperty("server_prefix", clusterManagedServerNameBase);
      p.setProperty("server_count", String.valueOf(clusterReplicaCount));
      assertDoesNotThrow(() -> p.store(new FileOutputStream(wlstPropertiesFile), "wlst properties file"),
          "Failed to write the WLST properties to file");

      // changet the admin server port to a different value to force pod restart
      Path configScript = Paths.get(RESOURCE_DIR, "python-scripts", "introspect_version_script.py");
      executeWLSTScript(configScript, wlstPropertiesFile.toPath(), domainNamespace);

      String introspectVersion = assertDoesNotThrow(() -> getNextIntrospectVersion(domainUid, domainNamespace));

      logger.info("patch the domain resource with new cluster and introspectVersion");
      String patchStr
          = "["
          + "{\"op\": \"add\",\"path\": \"/spec/clusters/-\", \"value\": "
          + "    {\"clusterName\" : \"" + clusterName + "\", \"replicas\": "
          + clusterReplicaCount + ", \"serverStartState\": \"RUNNING\"}"
          + "},"
          + "{\"op\": \"replace\", \"path\": \"/spec/introspectVersion\", \"value\": \"" + introspectVersion + "\"}"
          + "]";
      logger.info("Updating domain configuration using patch string: {0}\n", patchStr);
      V1Patch patch = new V1Patch(patchStr);
      assertTrue(patchDomainCustomResource(domainUid, domainNamespace, patch, V1Patch.PATCH_FORMAT_JSON_PATCH),
          "Failed to patch domain");

      //verify the introspector pod is created and runs
      String introspectPodNameBase = getIntrospectJobName(domainUid);

      checkPodExists(introspectPodNameBase, domainUid, domainNamespace);
      checkPodDoesNotExist(introspectPodNameBase, domainUid, domainNamespace);

      // verify managed server services and pods are created
      for (int i = 1; i <= clusterReplicaCount; i++) {
        logger.info("Checking managed server service and pod {0} is created in namespace {1}",
            clusterManagedServerPodNamePrefix + i, domainNamespace);
        checkPodReadyAndServiceExists(clusterManagedServerPodNamePrefix + i, domainUid, domainNamespace);
      }

      List<String> managedServerNames = new ArrayList<>();
      for (int i = 1; i <= clusterReplicaCount; i++) {
        managedServerNames.add(clusterManagedServerNameBase + i);
      }

      //verify admin server accessibility and the health of cluster members
      verifyMemberHealth(domainNamespace, adminServerPodName, managedServerNames, wlsUserName, wlsPassword);
    }
  }

  /**
   * Test creates new clusters in shutdown state and verifies it can successfully start after patching the domain with
   * new introspectVersion string 
   * 
   * a. Creates new WebLogic static clusters using online WLST. 
   * b. Patch the Domain Resource with clusters.
   * c. Update the introspectVersion version.
   * d. Verifies the servers in the new WebLogic clusters comes up without affecting any of the running servers on 
   * pre-existing WebLogic cluster.
   */
  @Order(3)
  @Test
  @DisplayName("Test new cluster creations and starting up on introspection on demand using WLST")
  void testCreateNewClustersDontStart() {
    String patchStr;
    V1Patch patch;
    logger.info("Getting port for default channel");
    int adminServerPort
        = getServicePort(domainNamespace, getExternalServicePodName(adminServerPodName), "default");

    String clusterBaseName = "sdcluster-";
    for (int j = 1; j <= numOfClusters; j++) {
      String clusterName = clusterBaseName + j;
      String clusterManagedServerNameBase = clusterName + "-ms-";

      // create a temporary WebLogic WLST property file
      File wlstPropertiesFile = assertDoesNotThrow(() -> File.createTempFile("wlst", "properties"),
          "Creating WLST properties file failed");
      Properties p = new Properties();
      p.setProperty("admin_host", adminServerPodName);
      p.setProperty("admin_port", Integer.toString(adminServerPort));
      p.setProperty("admin_username", wlsUserName);
      p.setProperty("admin_password", wlsPassword);
      p.setProperty("test_name", "create_cluster");
      p.setProperty("cluster_name", clusterName);
      p.setProperty("server_prefix", clusterManagedServerNameBase);
      p.setProperty("server_count", String.valueOf(clusterReplicaCount));
      assertDoesNotThrow(() -> p.store(new FileOutputStream(wlstPropertiesFile), "wlst properties file"),
          "Failed to write the WLST properties to file");

      // changet the admin server port to a different value to force pod restart
      Path configScript = Paths.get(RESOURCE_DIR, "python-scripts", "introspect_version_script.py");
      executeWLSTScript(configScript, wlstPropertiesFile.toPath(), domainNamespace);
      logger.info("patch the domain resource with new cluster and introspectVersion");
      patchStr
          = "["
          + "{\"op\": \"add\",\"path\": \"/spec/clusters/-\", \"value\": "
          + "    {\"clusterName\" : \"" + clusterName + "\", \"replicas\": "
          + clusterReplicaCount + ", \"serverStartPolicy\": \"IF_NEEDED\"}"
          + "}]";
      patch = new V1Patch(patchStr);
      assertTrue(patchDomainCustomResource(domainUid, domainNamespace, patch, V1Patch.PATCH_FORMAT_JSON_PATCH),
          "Failed to patch domain");
    }
    logger.info("Restarting admin server");
    restartAS();

    String introspectVersion = assertDoesNotThrow(() -> getNextIntrospectVersion(domainUid, domainNamespace));
    logger.info("patch the domain resource with new cluster and introspectVersion");
    patchStr
        = "["
        + "{\"op\": \"replace\", \"path\": \"/spec/introspectVersion\", \"value\": \"" + introspectVersion + "\"}"
        + "]";
    logger.info("Updating domain configuration using patch string: {0}\n", patchStr);
    patch = new V1Patch(patchStr);
    assertTrue(patchDomainCustomResource(domainUid, domainNamespace, patch, V1Patch.PATCH_FORMAT_JSON_PATCH),
        "Failed to patch domain");
    //verify the introspector pod is created and runs
    String introspectPodNameBase = getIntrospectJobName(domainUid);
    checkPodExists(introspectPodNameBase, domainUid, domainNamespace);
    checkPodDoesNotExist(introspectPodNameBase, domainUid, domainNamespace);

    for (int j = 1; j <= numOfClusters; j++) {
      String clusterName = clusterBaseName + j;
      String clusterManagedServerNameBase = clusterName + "-ms-";
      String clusterManagedServerPodNamePrefix = domainUid + "-" + clusterManagedServerNameBase;
      // verify managed server services and pods are created
      for (int i = 1; i <= clusterReplicaCount; i++) {
        logger.info("Checking managed server service and pod {0} is created in namespace {1}",
            clusterManagedServerPodNamePrefix + i, domainNamespace);
        checkPodReadyAndServiceExists(clusterManagedServerPodNamePrefix + i, domainUid, domainNamespace);
      }

      List<String> managedServerNames = new ArrayList<>();
      for (int i = 1; i <= clusterReplicaCount; i++) {
        managedServerNames.add(clusterManagedServerNameBase + i);
      }
      //verify admin server accessibility and the health of cluster members
      verifyMemberHealth(domainNamespace, adminServerPodName, managedServerNames, wlsUserName, wlsPassword);
    }
  }

  /**
   * Test shuts down all existing clusters and starts up. 
   * 
   * a. Shutdowns all cluster using serverStartPolicy NEVER. 
   * b. Patch the Domain Resource with cluster serverStartPolicy IF_NEEDED. 
   * c. Verifies the servers in the domain cluster comes up.
   */
  @Order(4)
  @Test
  @DisplayName("Test cluster shutdown and startup")
  void testRestartClusters() {

    String clusterBaseName = "sdcluster-";
    //shutdown all clusters in default domain
    for (int j = 1; j <= numOfClusters; j++) {
      String clusterName = clusterBaseName + j;
      String clusterManagedServerNameBase = clusterName + "-ms-";
      String clusterManagedServerPodNamePrefix = domainUid + "-" + clusterManagedServerNameBase;

      logger.info("patch the domain resource with new cluster and introspectVersion");
      String patchStr
          = "[{\"op\": \"replace\", \"path\": \"/spec/clusters/" + j + "/serverStartPolicy\", "
          + "\"value\": \"NEVER\""
          + "}]";
      logger.info("Updating domain configuration using patch string: {0}\n", patchStr);
      V1Patch patch = new V1Patch(patchStr);
      assertTrue(patchDomainCustomResource(domainUid, domainNamespace, patch, V1Patch.PATCH_FORMAT_JSON_PATCH),
          "Failed to patch domain");
      for (int i = 1; i <= clusterReplicaCount; i++) {
        logger.info("Checking managed server service and pod {0} is deleted in namespace {1}",
            clusterManagedServerPodNamePrefix + i, domainNamespace);
        checkPodDoesNotExist(clusterManagedServerPodNamePrefix + i, domainUid, domainNamespace);
      }
    }
    //startup all clusters in default domain
    for (int j = 1; j <= numOfClusters; j++) {
      String clusterName = clusterBaseName + j;
      String clusterManagedServerNameBase = clusterName + "-ms-";
      String clusterManagedServerPodNamePrefix = domainUid + "-" + clusterManagedServerNameBase;

      logger.info("patch the domain resource with new cluster and introspectVersion");
      String patchStr
          = "[{\"op\": \"replace\",\"path\": \"/spec/clusters/" + j + "/serverStartPolicy\", "
          + "\"value\": \"IF_NEEDED\""
          + "}]";
      logger.info("Updating domain configuration using patch string: {0}\n", patchStr);
      V1Patch patch = new V1Patch(patchStr);
      assertTrue(patchDomainCustomResource(domainUid, domainNamespace, patch, V1Patch.PATCH_FORMAT_JSON_PATCH),
          "Failed to patch domain");
      // verify managed server services and pods are created
      for (int i = 1; i <= clusterReplicaCount; i++) {
        logger.info("Checking managed server service and pod {0} is created in namespace {1}",
            clusterManagedServerPodNamePrefix + i, domainNamespace);
        checkPodReadyAndServiceExists(clusterManagedServerPodNamePrefix + i, domainUid, domainNamespace);
      }
    }

  }

  private void restartAS() {
    //restart admin server
    String patchStr
        = "["
        + "{\"op\": \"replace\", \"path\": \"/spec/adminServer/serverStartPolicy\", \"value\": \"NEVER\"}"
        + "]";
    logger.info("Updating domain configuration using patch string: {0}\n", patchStr);
    V1Patch patch = new V1Patch(patchStr);
    assertTrue(patchDomainCustomResource(domainUid, domainNamespace, patch, V1Patch.PATCH_FORMAT_JSON_PATCH),
        "Failed to patch domain");
    checkPodDoesNotExist(adminServerPodName, domainUid, domainNamespace);

    patchStr
        = "["
        + "{\"op\": \"replace\", \"path\": \"/spec/adminServer/serverStartPolicy\", \"value\": \"IF_NEEDED\"}"
        + "]";
    logger.info("Updating domain configuration using patch string: {0}\n", patchStr);
    patch = new V1Patch(patchStr);
    assertTrue(patchDomainCustomResource(domainUid, domainNamespace, patch, V1Patch.PATCH_FORMAT_JSON_PATCH),
        "Failed to patch domain");
    // verify the admin server service and pod created
    checkPodReadyAndServiceExists(adminServerPodName, domainUid, domainNamespace);
  }

  private static void createDomain(String namespace, String domainUid) {
    createDomain(namespace, domainUid, cluster1Name, cluster1ManagedServerNameBase);
  }

  private static void createDomain(String namespace, String domainUid,
      String clusterName, String clusterManagedServerNameBase) {
    String uniquePath = "/shared/" + namespace + "/domains";

    // create WebLogic domain credential secret
    createSecretWithUsernamePassword(wlSecretName, namespace,
        wlsUserName, wlsPassword);

    //create unique pv pvc for each domain
    final String pvName = getUniqueName(domainUid + "-pv-");
    final String pvcName = getUniqueName(domainUid + "-pvc-");
    createPV(pvName, domainUid, ItLargeCapacityDomainsClustersDPV.class.getSimpleName());
    createPVC(pvName, pvcName, domainUid, namespace);

    // create a temporary WebLogic domain property file
    File domainPropertiesFile = assertDoesNotThrow(()
        -> File.createTempFile("domain", "properties"),
        "Failed to create domain properties file");
    Properties p = new Properties();
    p.setProperty("domain_path", uniquePath);
    p.setProperty("domain_name", domainUid);
    p.setProperty("cluster_name", clusterName);
    p.setProperty("admin_server_name", adminServerName);
    p.setProperty("managed_server_port", Integer.toString(managedServerPort));
    p.setProperty("admin_server_port", "7001");
    p.setProperty("admin_username", wlsUserName);
    p.setProperty("admin_password", wlsPassword);
    p.setProperty("admin_t3_public_address", K8S_NODEPORT_HOST);
    p.setProperty("admin_t3_channel_port", Integer.toString(t3ChannelPort));
    p.setProperty("number_of_ms", "2"); // maximum number of servers in cluster
    p.setProperty("managed_server_name_base", clusterManagedServerNameBase);
    p.setProperty("domain_logs", uniquePath + "/logs");
    p.setProperty("production_mode_enabled", "true");
    assertDoesNotThrow(()
        -> p.store(new FileOutputStream(domainPropertiesFile), "domain properties file"),
        "Failed to write domain properties file");

    // WLST script for creating domain
    Path wlstScript = Paths.get(RESOURCE_DIR, "python-scripts", "wlst-create-domain-onpv.py");

    // create configmap and domain on persistent volume using the WLST script and property file
    createDomainOnPVUsingWlst(wlstScript, domainPropertiesFile.toPath(),
        pvName, pvcName, namespace);

    //createPatchJarConfigMap(domainNamespace);
    // create a domain custom resource configuration object
    logger.info("Creating domain custom resource");
    Domain domain = new Domain()
        .apiVersion(DOMAIN_API_VERSION)
        .kind("Domain")
        .metadata(new V1ObjectMeta()
            .name(domainUid)
            .namespace(namespace))
        .spec(new DomainSpec()
            .domainUid(domainUid)
            .domainHome(uniquePath + "/" + domainUid) // point to domain home in pv
            .domainHomeSourceType("PersistentVolume") // set the domain home source type as pv
            .image(WEBLOGIC_IMAGE_TO_USE_IN_SPEC)
            .imagePullPolicy("IfNotPresent")
            .webLogicCredentialsSecret(new V1SecretReference()
                .name(wlSecretName)
                .namespace(namespace))
            .includeServerOutInPodLog(true)
            .logHomeEnabled(Boolean.TRUE)
            .logHome(uniquePath + "/logs/" + domainUid)
            .dataHome("")
            .serverStartPolicy("IF_NEEDED")
            .serverPod(new ServerPod() //serverpod
                .addEnvItem(new V1EnvVar()
                    .name("JAVA_OPTIONS")
                    .value("-Dweblogic.debug.DebugConfigurationEdit=true "
                        + "-Dweblogic.debug.DebugSituationalConfig=true"))
                .addEnvItem(new V1EnvVar()
                    .name("USER_MEM_ARGS")
                    .value("-Djava.security.egd=file:/dev/./urandom "))
                .addVolumesItem(new V1Volume()
                    .name(pvName)
                    .persistentVolumeClaim(new V1PersistentVolumeClaimVolumeSource()
                        .claimName(pvcName)))
                .addVolumeMountsItem(new V1VolumeMount()
                    .mountPath("/shared")
                    .name(pvName)))
            .adminServer(new AdminServer() //admin server
                .serverStartState("RUNNING")
                .adminService(new AdminService()
                    .addChannelsItem(new Channel()
                        .channelName("default")
                        .nodePort(getNextFreePort()))))
            .addClustersItem(new Cluster() //cluster
                .clusterName(clusterName)
                .replicas(clusterReplicaCount)
                .serverStartState("RUNNING")));

    // create secrets
    List<V1LocalObjectReference> secrets = new ArrayList<>();
    for (String secret : createSecretsForImageRepos(namespace)) {
      secrets.add(new V1LocalObjectReference().name(secret));
    }
    domain.spec().setImagePullSecrets(secrets);

    setPodAntiAffinity(domain);
    // verify the domain custom resource is created
    createDomainAndVerify(domain, namespace);
  }

  /**
   * Create a WebLogic domain on a persistent volume by doing the following. Create a configmap containing WLST script
   * and property file. Create a Kubernetes job to create domain on persistent volume.
   *
   * @param wlstScriptFile python script to create domain
   * @param domainPropertiesFile properties file containing domain configuration
   * @param pvName name of the persistent volume to create domain in
   * @param pvcName name of the persistent volume claim
   * @param namespace name of the domain namespace in which the job is created
   */
  private static void createDomainOnPVUsingWlst(Path wlstScriptFile, Path domainPropertiesFile,
      String pvName, String pvcName, String namespace) {
    logger.info("Preparing to run create domain job using WLST");

    List<Path> domainScriptFiles = new ArrayList<>();
    domainScriptFiles.add(wlstScriptFile);
    domainScriptFiles.add(domainPropertiesFile);

    logger.info("Creating a config map to hold domain creation scripts");
    String domainScriptConfigMapName = "create-domain-scripts-cm";
    assertDoesNotThrow(() -> createConfigMapForDomainCreation(domainScriptConfigMapName,
        domainScriptFiles, namespace, ItLargeCapacityDomainsClustersDPV.class.getSimpleName()),
        "Create configmap for domain creation failed");

    // create a V1Container with specific scripts and properties for creating domain
    V1Container jobCreationContainer = new V1Container()
        .addCommandItem("/bin/sh")
        .addArgsItem("/u01/oracle/oracle_common/common/bin/wlst.sh")
        .addArgsItem("/u01/weblogic/" + wlstScriptFile.getFileName()) //wlst.sh script
        .addArgsItem("-skipWLSModuleScanning")
        .addArgsItem("-loadProperties")
        .addArgsItem("/u01/weblogic/" + domainPropertiesFile.getFileName()); //domain property file

    logger.info("Running a Kubernetes job to create the domain");
    createDomainJob(WEBLOGIC_IMAGE_TO_USE_IN_SPEC, pvName, pvcName, domainScriptConfigMapName,
        namespace, jobCreationContainer);

  }

  private static void deployAndVerifyMemberHealth(String adminServerPodName, List<String> managedServerNames,
      String namespace) {
    if (OKD) {
      adminSvcExtHost = createRouteForOKD(getExternalServicePodName(adminServerPodName), namespace);
      logger.info("admin svc host = {0}", adminSvcExtHost);
    }

    // deploy application and verify all servers functions normally
    logger.info("Getting port for default channel");
    int defaultChannelPort = assertDoesNotThrow(()
        -> getServicePort(namespace, getExternalServicePodName(adminServerPodName), "default"),
        "Getting admin server default port failed");
    logger.info("default channel port: {0}", defaultChannelPort);
    assertNotEquals(-1, defaultChannelPort, "admin server defaultChannelPort is not valid");

    int serviceNodePort = assertDoesNotThrow(()
        -> getServiceNodePort(namespace, getExternalServicePodName(adminServerPodName), "default"),
        "Getting admin server node port failed");
    logger.info("Admin Server default node port : {0}", serviceNodePort);
    assertNotEquals(-1, serviceNodePort, "admin server default node port is not valid");

    //deploy clusterview application
    logger.info("Deploying clusterview app {0} to admin server", clusterViewAppPath);
    String targets = "{identity:[servers,'admin-server']}";

    String hostAndPort = (OKD) ? adminSvcExtHost : K8S_NODEPORT_HOST + ":" + serviceNodePort;
    logger.info("hostAndPort = {0} ", hostAndPort);

    withLongRetryPolicy.conditionEvaluationListener(
        condition -> logger.info("Deploying the application using Rest"
            + "(elapsed time {0} ms, remaining time {1} ms)",
            condition.getElapsedTimeInMS(),
            condition.getRemainingTimeInMS()))
        .until((Callable<Boolean>) () -> {
          ExecResult result = assertDoesNotThrow(() -> deployUsingRest(hostAndPort,
              wlsUserName, wlsPassword,
              targets, clusterViewAppPath, null, "clusterview"));
          return result.stdout().equals("202");
        });

    //verify admin server accessibility and the health of cluster members
    verifyMemberHealth(namespace, adminServerPodName, managedServerNames, wlsUserName, wlsPassword);
  }

  private static void verifyMemberHealth(String namespace, String adminServerPodName, List<String> managedServerNames,
      String user, String password) {

    logger.info("Getting node port for default channel");
    int serviceNodePort = assertDoesNotThrow(()
        -> getServiceNodePort(namespace, getExternalServicePodName(adminServerPodName), "default"),
        "Getting admin server node port failed");

    String hostAndPort = (OKD) ? adminSvcExtHost : K8S_NODEPORT_HOST + ":" + serviceNodePort;
    logger.info("hostAndPort = {0} ", hostAndPort);

    logger.info("Checking the health of servers in cluster");
    String url = "http://" + hostAndPort
        + "/clusterview/ClusterViewServlet?user=" + user + "&password=" + password;

    withLongRetryPolicy.conditionEvaluationListener(
        condition -> logger.info("Verifying the health of all cluster members"
            + "(elapsed time {0} ms, remaining time {1} ms)",
            condition.getElapsedTimeInMS(),
            condition.getRemainingTimeInMS()))
        .until((Callable<Boolean>) () -> {
          HttpResponse<String> response = assertDoesNotThrow(() -> OracleHttpClient.get(url, true));
          if (response.statusCode() != 200) {
            logger.info("Response code is not 200 retrying...");
            return false;
          }
          boolean health = true;
          for (String managedServer : managedServerNames) {
            health = health && response.body().contains(managedServer + ":HEALTH_OK");
            if (health) {
              logger.info(managedServer + " is healthy");
            } else {
              logger.info(managedServer + " health is not OK or server not found");
            }
          }
          return health;
        });
  }
}

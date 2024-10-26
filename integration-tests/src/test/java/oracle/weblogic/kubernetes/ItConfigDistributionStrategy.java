// Copyright (c) 2020, 2024, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

import io.kubernetes.client.custom.Quantity;
import io.kubernetes.client.custom.V1Patch;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.models.V1EnvVar;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1Pod;
import io.kubernetes.client.openapi.models.V1Secret;
import io.kubernetes.client.util.Yaml;
import oracle.weblogic.domain.Configuration;
import oracle.weblogic.domain.CreateIfNotExists;
import oracle.weblogic.domain.DomainCreationImage;
import oracle.weblogic.domain.DomainOnPV;
import oracle.weblogic.domain.DomainOnPVType;
import oracle.weblogic.domain.DomainResource;
import oracle.weblogic.kubernetes.actions.impl.NginxParams;
import oracle.weblogic.kubernetes.actions.impl.Service;
import oracle.weblogic.kubernetes.actions.impl.primitive.WitParams;
import oracle.weblogic.kubernetes.annotations.IntegrationTest;
import oracle.weblogic.kubernetes.annotations.Namespaces;
import oracle.weblogic.kubernetes.logging.LoggingFacade;
import oracle.weblogic.kubernetes.utils.ExecResult;
import oracle.weblogic.kubernetes.utils.OracleHttpClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import static oracle.weblogic.kubernetes.TestConstants.ADMIN_PASSWORD_DEFAULT;
import static oracle.weblogic.kubernetes.TestConstants.ADMIN_USERNAME_DEFAULT;
import static oracle.weblogic.kubernetes.TestConstants.BASE_IMAGES_REPO_SECRET_NAME;
import static oracle.weblogic.kubernetes.TestConstants.DOMAIN_IMAGES_PREFIX;
import static oracle.weblogic.kubernetes.TestConstants.K8S_NODEPORT_HOST;
import static oracle.weblogic.kubernetes.TestConstants.KUBERNETES_CLI;
import static oracle.weblogic.kubernetes.TestConstants.MII_BASIC_IMAGE_TAG;
import static oracle.weblogic.kubernetes.TestConstants.OKE_CLUSTER;
import static oracle.weblogic.kubernetes.TestConstants.OKE_CLUSTER_PRIVATEIP;
import static oracle.weblogic.kubernetes.TestConstants.RESULTS_TEMPFILE;
import static oracle.weblogic.kubernetes.TestConstants.TRAEFIK_INGRESS_HTTP_HOSTPORT;
import static oracle.weblogic.kubernetes.TestConstants.WEBLOGIC_12213;
import static oracle.weblogic.kubernetes.actions.ActionConstants.APP_DIR;
import static oracle.weblogic.kubernetes.actions.ActionConstants.MODEL_DIR;
import static oracle.weblogic.kubernetes.actions.ActionConstants.RESOURCE_DIR;
import static oracle.weblogic.kubernetes.actions.ActionConstants.WORK_DIR;
import static oracle.weblogic.kubernetes.actions.TestActions.createSecret;
import static oracle.weblogic.kubernetes.actions.TestActions.deleteConfigMap;
import static oracle.weblogic.kubernetes.actions.TestActions.deleteSecret;
import static oracle.weblogic.kubernetes.actions.TestActions.getNextIntrospectVersion;
import static oracle.weblogic.kubernetes.actions.TestActions.getPod;
import static oracle.weblogic.kubernetes.actions.TestActions.getServiceNodePort;
import static oracle.weblogic.kubernetes.actions.TestActions.getServicePort;
import static oracle.weblogic.kubernetes.actions.TestActions.shutdownDomain;
import static oracle.weblogic.kubernetes.actions.TestActions.startDomain;
import static oracle.weblogic.kubernetes.actions.impl.Domain.patchDomainCustomResource;
import static oracle.weblogic.kubernetes.actions.impl.primitive.Kubernetes.listConfigMaps;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.podStateNotChanged;
import static oracle.weblogic.kubernetes.utils.AuxiliaryImageUtils.createAndPushAuxiliaryImage;
import static oracle.weblogic.kubernetes.utils.BuildApplication.buildApplication;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.checkServiceExists;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.createIngressHostRouting;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.formatIPv6Host;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.getHostAndPort;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.getNextFreePort;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.getServiceExtIPAddrtOke;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.getUniqueName;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.testUntil;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.withLongRetryPolicy;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.withStandardRetryPolicy;
import static oracle.weblogic.kubernetes.utils.ConfigMapUtils.createConfigMapFromFiles;
import static oracle.weblogic.kubernetes.utils.DeployUtil.deployUsingWlst;
import static oracle.weblogic.kubernetes.utils.DomainUtils.createDomainAndVerify;
import static oracle.weblogic.kubernetes.utils.DomainUtils.createDomainResourceOnPv;
import static oracle.weblogic.kubernetes.utils.ExecCommand.exec;
import static oracle.weblogic.kubernetes.utils.FileUtils.createWdtPropertyFile;
import static oracle.weblogic.kubernetes.utils.FileUtils.replaceStringInFile;
import static oracle.weblogic.kubernetes.utils.FmwUtils.getConfiguration;
import static oracle.weblogic.kubernetes.utils.ImageUtils.createBaseRepoSecret;
import static oracle.weblogic.kubernetes.utils.JobUtils.getIntrospectJobName;
import static oracle.weblogic.kubernetes.utils.LoadBalancerUtils.createNginxIngressPathRoutingRules;
import static oracle.weblogic.kubernetes.utils.LoadBalancerUtils.installAndVerifyNginx;
import static oracle.weblogic.kubernetes.utils.MySQLDBUtils.createMySQLDB;
import static oracle.weblogic.kubernetes.utils.OKDUtils.createRouteForOKD;
import static oracle.weblogic.kubernetes.utils.OperatorUtils.installAndVerifyOperator;
import static oracle.weblogic.kubernetes.utils.PodUtils.checkPodDoesNotExist;
import static oracle.weblogic.kubernetes.utils.PodUtils.checkPodExists;
import static oracle.weblogic.kubernetes.utils.PodUtils.checkPodReady;
import static oracle.weblogic.kubernetes.utils.PodUtils.getExternalServicePodName;
import static oracle.weblogic.kubernetes.utils.PodUtils.getPodCreationTime;
import static oracle.weblogic.kubernetes.utils.SecretUtils.createSecretWithUsernamePassword;
import static oracle.weblogic.kubernetes.utils.ThreadSafeLogger.getLogger;
import static oracle.weblogic.kubernetes.utils.WLSTUtils.executeWLSTScript;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests related to overrideDistributionStrategy attribute.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("Verify the overrideDistributionStrategy applies the overrides accordingly to the value set")
@Tag("kind-parallel")
@Tag("okd-wls-mrg")
@Tag("oke-weekly-sequential")
@IntegrationTest
@Tag("olcne-mrg")
class ItConfigDistributionStrategy {

  private static String opNamespace = null;
  private static String domainNamespace = null;
  private static String nginxNamespace = null;
  private static NginxParams nginxHelmParams = null;

  final String domainUid = "mydomain";
  final String clusterName = "mycluster";
  final String clusterResName = domainUid + "-" + clusterName;
  final String adminServerName = "admin-server";
  final int adminPort = 7001;
  final String adminServerPodName = domainUid + "-" + adminServerName;
  final String managedServerNameBase = "managed-server";
  int t3ChannelPort;
  final String pvName = getUniqueName(domainUid + "-pv-");
  final String pvcName = getUniqueName(domainUid + "-pvc-");
  final String wlSecretName = "weblogic-credentials";
  final String managedServerPodNamePrefix = domainUid + "-" + managedServerNameBase;
  int replicaCount = 2;
  static String hostAndPort;

  static Path clusterViewAppPath;
  String overridecm = "configoverride-cm";
  LinkedHashMap<String, OffsetDateTime> podTimestamps;

  static String dsUrl1;
  static String dsUrl2;
  private static String ingressIP = null;

  String dsName0 = "JdbcTestDataSource-0";
  String dsName1 = "JdbcTestDataSource-1";
  String dsSecret = domainUid.concat("-mysql-secret");
  String adminSvcExtHost = null;
  static String hostHeader;

  private static LoggingFacade logger = null;

  /**
   * Assigns unique namespaces for operator and domains.
   * Pulls WebLogic image if running tests in Kind cluster.
   * Installs operator.
   * Creates 2 MySQL database instances.
   * Creates and starts WebLogic domain containing 2 instances in dynamic cluser.
   * Creates 2 JDBC data sources targeted to cluster.
   * Deploys clusterview application to cluster and admin targets.
   *
   * @param namespaces injected by JUnit
   */
  @BeforeAll
  public void initAll(@Namespaces(3) List<String> namespaces) throws ApiException, IOException {
    logger = getLogger();

    logger.info("Assign a unique namespace for operator");
    assertNotNull(namespaces.get(0), "Namespace is null");
    opNamespace = namespaces.get(0);
    logger.info("Assign a unique namespace for domain namspace");
    assertNotNull(namespaces.get(1), "Namespace is null");
    domainNamespace = namespaces.get(1);
    assertNotNull(namespaces.get(2), "Namespace is null");
    nginxNamespace = namespaces.get(2);

    // install operator and verify its running in ready state
    installAndVerifyOperator(opNamespace, domainNamespace);
    ingressIP = K8S_NODEPORT_HOST;
    if (OKE_CLUSTER_PRIVATEIP) {
      // install and verify NGINX
      nginxHelmParams = installAndVerifyNginx(nginxNamespace, 0, 0);
      String nginxServiceName = nginxHelmParams.getHelmParams().getReleaseName() + "-ingress-nginx-controller";
      ingressIP = getServiceExtIPAddrtOke(nginxServiceName, nginxNamespace) != null
          ? getServiceExtIPAddrtOke(nginxServiceName, nginxNamespace) : K8S_NODEPORT_HOST;

    }
    // create pull secrets for WebLogic image when running in non Kind Kubernetes cluster
    // this secret is used only for non-kind cluster
    createBaseRepoSecret(domainNamespace);

    //start two MySQL database instances
    String dbService1 = createMySQLDB("mysqldb-1", "root", "root123", domainNamespace, null);
    V1Pod pod = getPod(domainNamespace, null, "mysqldb-1");
    assertNotNull(pod, "pod is null");
    assertNotNull(pod.getMetadata(), "pod metadata is null");
    createFileInPod(pod.getMetadata().getName(), domainNamespace, "root123");
    runMysqlInsidePod(pod.getMetadata().getName(), domainNamespace, "root123");

    String dbService2 = createMySQLDB("mysqldb-2", "root", "root456", domainNamespace, null);
    pod = getPod(domainNamespace, null, "mysqldb-2");
    assertNotNull(pod, "pod is null");
    assertNotNull(pod.getMetadata(), "pod metadata is null");
    createFileInPod(pod.getMetadata().getName(), domainNamespace, "root456");
    runMysqlInsidePod(pod.getMetadata().getName(), domainNamespace, "root456");
    
    dsUrl1 = "jdbc:mysql://" + dbService1 + "." + domainNamespace + ".svc:3306";
    dsUrl2 = "jdbc:mysql://" + dbService2 + "." + domainNamespace + ".svc:3306";
    logger.info(dsUrl1);
    logger.info(dsUrl2);

    // build the clusterview application
    Path distDir = buildApplication(Paths.get(APP_DIR, "clusterview"),
        null, null, "dist", domainNamespace);
    clusterViewAppPath = Paths.get(distDir.toString(), "clusterview.war");
    assertTrue(clusterViewAppPath.toFile().exists(), "Application archive is not available");

    //create and start WebLogic domain
    createDomain();
    if (OKE_CLUSTER_PRIVATEIP) {
      String ingressClassName = nginxHelmParams.getIngressClassName();
      String serviceName = domainUid + "-admin-server";
      final int ADMIN_SERVER_PORT = 7001;
      String hostAndPort = getHostAndPortOKE();
      createNginxIngressPathRoutingRules(domainNamespace, ingressClassName,
          serviceName, ADMIN_SERVER_PORT, hostAndPort);
    }

    // Expose the admin service external node port as  a route for OKD
    adminSvcExtHost = createRouteForOKD(getExternalServicePodName(adminServerPodName), domainNamespace);

    //create a jdbc resource targeted to cluster
    createJdbcDataSource(dsName0, "root", "root123", dsUrl1);
    createJdbcDataSource(dsName1, "root", "root123", dsUrl1);
    //deploy application to view server configuration
    deployApplication(clusterName + "," + adminServerName);
  }

  /**
   * Verify the default config before starting any test.
   */
  @BeforeEach
  public void beforeEach() throws UnknownHostException, IOException, InterruptedException {
    getDomainHealth();
    //check configuration values before override
    verifyConfigXMLOverride(false);
    verifyResourceJDBC0Override(false);
  }

  /**
   * Delete the overrides and restart domain to get clean state.
   */
  @AfterEach
  public void afterEach() throws IOException, InterruptedException {
    getDomainHealth();
    deleteConfigMap(overridecm, domainNamespace);
    String patchStr
        = "["
        + "{\"op\": \"remove\", \"path\": \"/spec/configuration/overridesConfigMap\"}"
        + "]";
    logger.info("Updating domain configuration using patch string: {0}", patchStr);
    V1Patch patch = new V1Patch(patchStr);
    patchDomainCustomResource(domainUid, domainNamespace, patch, V1Patch.PATCH_FORMAT_JSON_PATCH);
    restartDomain();

    logger.info("Getting node port for default channel");
    int serviceNodePort = assertDoesNotThrow(()
        -> getServiceNodePort(domainNamespace, getExternalServicePodName(adminServerPodName), "default"),
        "Getting admin server node port failed");

    testUntil(
        withLongRetryPolicy,
        () -> {
          logger.info("Checking if the clusterview app in admin server is accessible after restart");
          String hostAndPort = getHostAndPort(adminSvcExtHost, serviceNodePort);
          Map<String, String> headers = null;
          if (TestConstants.KIND_CLUSTER
              && !TestConstants.WLSIMG_BUILDER.equals(TestConstants.WLSIMG_BUILDER_DEFAULT)) {
            hostAndPort = "localhost:" + TRAEFIK_INGRESS_HTTP_HOSTPORT;
            headers = new HashMap<>();
            headers.put("host", hostHeader);
          }
          boolean ipv6 = K8S_NODEPORT_HOST.contains(":");
          if (OKE_CLUSTER_PRIVATEIP) {
            hostAndPort = ingressIP;
          }
          String baseUri = "http://" + hostAndPort + "/clusterview/";
          String serverListUri = "ClusterViewServlet?user=" + ADMIN_USERNAME_DEFAULT
              + "&password=" + ADMIN_PASSWORD_DEFAULT + "&ipv6=" + ipv6;

          HttpResponse<String> response = OracleHttpClient.get(baseUri + serverListUri, headers, true);
          return response.statusCode() == 200;
        },
        logger,
        "clusterview app in admin server is accessible after restart");
  }

  /**
   * Test server configuration and JDBC datasource configurations are overridden dynamically when
   * /spec/configuration/overrideDistributionStrategy: field is not set. By default, it should be Dynamic.
   *
   * <p>Test sets the /spec/configuration/overridesConfigMap and with new configuration for config.xml and datasources.
   *
   * <p>Verifies after introspector runs the server configuration and JDBC datasource configurations are updated
   * as expected.
   */
  @Order(1)
  @Test
  @DisplayName("Test overrideDistributionStrategy set to DEFAULT")
  void testDefaultOverride() {

    //store the pod creation timestamps
    storePodCreationTimestamps();

    List<Path> overrideFiles = new ArrayList<>();
    overrideFiles.add(
        Paths.get(RESOURCE_DIR, "configfiles/configoverridesset1/jdbc-JdbcTestDataSource-0.xml"));
    overrideFiles.add(Paths.get(RESOURCE_DIR, "configfiles/configoverridesset1/config.xml"));
    overrideFiles.add(Paths.get(RESOURCE_DIR, "configfiles/configoverridesset1/version.txt"));

    //create config override map
    createConfigMapFromFiles(overridecm, overrideFiles, domainNamespace);

    String introspectVersion = assertDoesNotThrow(() -> getNextIntrospectVersion(domainUid, domainNamespace));

    logger.info("patch the domain resource with overridesConfigMap and introspectVersion");
    String patchStr
        = "["
        + "{\"op\": \"add\", \"path\": \"/spec/configuration/overridesConfigMap\", \"value\": \"" + overridecm + "\"},"
        + "{\"op\": \"add\", \"path\": \"/spec/introspectVersion\", \"value\": \"" + introspectVersion + "\"}"
        + "]";
    logger.info("Updating domain configuration using patch string: {0}", patchStr);
    V1Patch patch = new V1Patch(patchStr);
    assertTrue(patchDomainCustomResource(domainUid, domainNamespace, patch, V1Patch.PATCH_FORMAT_JSON_PATCH),
        "Failed to patch domain");

    verifyIntrospectorRuns();
    verifyPodsStateNotChanged();

    //wait until config is updated upto 15 minutes
    testUntil(
        withLongRetryPolicy,
        configUpdated("100000000"),
        logger,
        "server configuration to be updated");

    verifyConfigXMLOverride(true);
    verifyResourceJDBC0Override(true);
  }

  /**
   * Test server configuration and JDBC datasource configurations are updated from previous overrides when underlying
   * override files are changed, configmap is recreated with new files with same name and introspector rerun.
   * a. Test sets the /spec/configuration/overridesConfigMap with configuration for config.xml and datasources.
   * b. Verifies after introspector runs the server configuration and JDBC datasource configurations are updated
   * as expected.
   * c. Recreate the same configmap with modified config.xml and recreates the map.
   * d. Reruns the introspector and verifies that the new configuration is applied as per the new config.xml override
   * file.
   */
  @Order(2)
  @Test
  @DisplayName("Test new overrides are applied as per the files in recreated configmap")
  void testModifiedOverrideContent() {

    //store the pod creation timestamps
    storePodCreationTimestamps();

    //create first set of override
    List<Path> overrideFiles = new ArrayList<>();
    overrideFiles.add(Paths.get(RESOURCE_DIR, "configfiles/configoverridesset1/config.xml"));
    overrideFiles.add(
        Paths.get(RESOURCE_DIR, "configfiles/configoverridesset1/jdbc-JdbcTestDataSource-0.xml"));
    overrideFiles.add(Paths.get(RESOURCE_DIR, "configfiles/configoverridesset1/version.txt"));

    //create config override map
    createConfigMapFromFiles(overridecm, overrideFiles, domainNamespace);

    String introspectVersion = assertDoesNotThrow(() -> getNextIntrospectVersion(domainUid, domainNamespace));

    logger.info("patch the domain resource with overridesConfigMap and introspectVersion");
    String patchStr
        = "["
        + "{\"op\": \"add\", \"path\": \"/spec/configuration/overridesConfigMap\", \"value\": \"" + overridecm + "\"},"
        + "{\"op\": \"replace\", \"path\": \"/spec/introspectVersion\", \"value\": \"" + introspectVersion + "\"}"
        + "]";
    logger.info("Updating domain configuration using patch string: {0}", patchStr);
    V1Patch patch = new V1Patch(patchStr);
    assertTrue(patchDomainCustomResource(domainUid, domainNamespace, patch, V1Patch.PATCH_FORMAT_JSON_PATCH),
        "Failed to patch domain");

    verifyIntrospectorRuns();
    verifyPodsStateNotChanged();

    //wait until config is updated upto 15 minutes
    testUntil(
        withLongRetryPolicy,
        configUpdated("100000000"),
        logger,
        "server configuration to be updated");

    verifyConfigXMLOverride(true);
    verifyResourceJDBC0Override(true);

    logger.info("Deleting the old override configmap {0}", overridecm);
    deleteConfigMap(overridecm, domainNamespace);

    testUntil(
        withLongRetryPolicy,
        () -> listConfigMaps(domainNamespace).getItems().stream().noneMatch(cm
            -> cm.getMetadata() != null && cm.getMetadata().getName() != null
               && cm.getMetadata().getName().equals(overridecm)),
        logger,
        "configmap {0} to be deleted.");

    Path srcOverrideFile = Paths.get(RESOURCE_DIR, "configfiles/configoverridesset1/config1.xml");
    Path dstOverrideFile = Paths.get(WORK_DIR, "config.xml");
    assertDoesNotThrow(() -> Files.copy(srcOverrideFile, dstOverrideFile, StandardCopyOption.REPLACE_EXISTING));

    overrideFiles = new ArrayList<>();
    overrideFiles.add(dstOverrideFile);
    overrideFiles.add(
        Paths.get(RESOURCE_DIR, "configfiles/configoverridesset1/jdbc-JdbcTestDataSource-0.xml"));
    overrideFiles.add(Paths.get(RESOURCE_DIR, "configfiles/configoverridesset1/version.txt"));

    //recreate config override map with new content
    logger.info("Recreating configmap {0} with new override files {1}", overridecm, overrideFiles);
    createConfigMapFromFiles(overridecm, overrideFiles, domainNamespace);

    introspectVersion = assertDoesNotThrow(() -> getNextIntrospectVersion(domainUid, domainNamespace));

    logger.info("patch the domain resource with overridesConfigMap and introspectVersion");
    patchStr
        = "["
        + "{\"op\": \"replace\", \"path\": \"/spec/introspectVersion\", \"value\": \"" + introspectVersion + "\"}"
        + "]";
    logger.info("Updating domain configuration using patch string: {0}", patchStr);
    patch = new V1Patch(patchStr);
    assertTrue(patchDomainCustomResource(domainUid, domainNamespace, patch, V1Patch.PATCH_FORMAT_JSON_PATCH),
        "Failed to patch domain");

    verifyIntrospectorRuns();
    verifyPodsStateNotChanged();

    //wait until config is updated upto 15 minutes
    testUntil(
        withLongRetryPolicy,
        configUpdated("99999999"),
        logger,
        "server configuration to be updated");

    verifyResourceJDBC0Override(true);
  }

  /**
   * Test server configuration and datasource configurations are dynamically overridden when
   * /spec/configuration/overrideDistributionStrategy is set to Dynamic.
   *
   * <p>Test sets the above field to Dynamic and overrides the /spec/configuration/overridesConfigMap
   * with new configuration.
   *
   * <p>Verifies after introspector runs and the server configuration and JDBC datasource configurations are
   * updated as expected.
   */
  @Order(3)
  @Test
  @DisplayName("Test overrideDistributionStrategy value Dyanmic")
  void testDynamicOverride() {

    //patching the domain with /spec/configuration/overrideDistributionStrategy: Dynamic
    String patchStr = "["
        + "{\"op\": \"add\", \"path\": \"/spec/configuration/overrideDistributionStrategy\", "
        + "\"value\": \"Dynamic\"}"
        + "]";
    logger.info("Updating domain configuration using patch string: {0}", patchStr);
    V1Patch patch = new V1Patch(patchStr);
    assertTrue(patchDomainCustomResource(domainUid, domainNamespace, patch, V1Patch.PATCH_FORMAT_JSON_PATCH),
        "Failed to patch domain");

    //TODO: - does changing overrideDistributionStrategy needs restart of server pods?
    restartDomain(); // if above is a bug, remove this after the above bug is fixed

    //store the pod creation timestamps
    storePodCreationTimestamps();

    List<Path> overrideFiles = new ArrayList<>();
    overrideFiles.add(
        Paths.get(RESOURCE_DIR, "configfiles/configoverridesset1/jdbc-JdbcTestDataSource-0.xml"));
    overrideFiles.add(Paths.get(RESOURCE_DIR, "configfiles/configoverridesset1/config.xml"));
    overrideFiles.add(Paths.get(RESOURCE_DIR, "configfiles/configoverridesset1/version.txt"));

    //create config override map
    createConfigMapFromFiles(overridecm, overrideFiles, domainNamespace);

    String introspectVersion = assertDoesNotThrow(() -> getNextIntrospectVersion(domainUid, domainNamespace));

    logger.info("patch the domain resource with overridesConfigMap, secrets , cluster and introspectVersion");
    patchStr
        = "["
        + "{\"op\": \"add\", \"path\": \"/spec/configuration/overridesConfigMap\", \"value\": \"" + overridecm + "\"},"
        + "{\"op\": \"replace\", \"path\": \"/spec/introspectVersion\", \"value\": \"" + introspectVersion + "\"}"
        + "]";
    logger.info("Updating domain configuration using patch string: {0}", patchStr);
    patch = new V1Patch(patchStr);
    assertTrue(patchDomainCustomResource(domainUid, domainNamespace, patch, V1Patch.PATCH_FORMAT_JSON_PATCH),
        "Failed to patch domain");

    verifyIntrospectorRuns();
    verifyPodsStateNotChanged();

    //wait until config is updated upto 5 minutes
    testUntil(configUpdated("100000000"),
        logger,
        "server configuration to be updated");

    verifyConfigXMLOverride(true);
    verifyResourceJDBC0Override(true);

    patchStr
        = "["
        + "{\"op\": \"remove\", \"path\": \"/spec/configuration/overrideDistributionStrategy\"}"
        + "]";
    logger.info("Updating domain configuration using patch string: {0}", patchStr);
    patch = new V1Patch(patchStr);
    assertTrue(patchDomainCustomResource(domainUid, domainNamespace, patch, V1Patch.PATCH_FORMAT_JSON_PATCH),
        "Failed to patch domain");
  }

  /**
   * Test server configuration and JDBC datasource configurations are overridden on restart of pods when
   * /spec/configuration/overrideDistributionStrategy is set to OnRestart.
   *
   * <p>Test sets the above field to OnRestart and overrides the /spec/configuration/overridesConfigMap and
   * /spec/configuration/secrets with new configuration and new secrets.
   *
   * <p>Verifies after introspector runs the server configuration and JDBC datasource configurations are not
   * updated. Verifies the overrides are applied only after a domain restart.
   */
  @Order(4)
  @Test
  @DisplayName("Test overrideDistributionStrategy value OnRestart")
  void testOnRestartOverride() {

    //patching the domain with /spec/configuration/overrideDistributionStrategy: OnRestart
    String patchStr = "["
        + "{\"op\": \"add\", \"path\": \"/spec/configuration/overrideDistributionStrategy\", "
        + "\"value\": \"OnRestart\"}"
        + "]";
    logger.info("Updating domain configuration using patch string: {0}", patchStr);
    V1Patch patch = new V1Patch(patchStr);
    assertTrue(patchDomainCustomResource(domainUid, domainNamespace, patch, V1Patch.PATCH_FORMAT_JSON_PATCH),
        "Failed to patch domain");

    //TODO: - does changing overrideDistributionStrategy needs restart of server pods?
    restartDomain(); // if above is a bug, remove this after the above bug is fixed

    //store the pod creation timestamps
    storePodCreationTimestamps();

    logger.info("Creating secrets for JDBC datasource overrides");
    //create new secrets for jdbc datasource
    Map<String, String> secretMap = new HashMap<>();
    secretMap.put("dbusername", "root");
    secretMap.put("dbpassword", "root456");

    boolean secretCreated = assertDoesNotThrow(() -> createSecret(new V1Secret()
        .metadata(new V1ObjectMeta()
            .name(dsSecret)
            .namespace(domainNamespace))
        .stringData(secretMap)), "Creating secret for datasource failed.");
    assertTrue(secretCreated, String.format("creating secret failed %s", dsSecret));

    //copy the template datasource file for override after replacing JDBC_URL with new datasource url
    Path srcDsOverrideFile = Paths.get(RESOURCE_DIR, "configfiles/configoverridesset1/jdbc-JdbcTestDataSource-1.xml");
    Path dstDsOverrideFile = Paths.get(WORK_DIR, "jdbc-JdbcTestDataSource-1.xml");
    assertDoesNotThrow(() -> {
      Files.copy(srcDsOverrideFile, dstDsOverrideFile, StandardCopyOption.REPLACE_EXISTING);
      replaceStringInFile(dstDsOverrideFile.toString(), "JDBC_URL", dsUrl2);
      if (WEBLOGIC_12213) {
        replaceStringInFile(dstDsOverrideFile.toString(), "com.mysql.cj.jdbc.Driver", "com.mysql.jdbc.Driver");
      }
    });

    List<Path> overrideFiles = new ArrayList<>();
    overrideFiles.add(dstDsOverrideFile);
    overrideFiles.add(Paths.get(RESOURCE_DIR, "configfiles/configoverridesset1/config.xml"));
    overrideFiles.add(Paths.get(RESOURCE_DIR, "configfiles/configoverridesset1/version.txt"));

    //create config override map
    createConfigMapFromFiles(overridecm, overrideFiles, domainNamespace);

    String introspectVersion = assertDoesNotThrow(() -> getNextIntrospectVersion(domainUid, domainNamespace));

    //patch the domain resource with overridesConfigMap, secrets and introspectVersion
    patchStr
        = "["
        + "{\"op\": \"add\", \"path\": \"/spec/configuration/overridesConfigMap\", \"value\": \"" + overridecm + "\"},"
        + "{\"op\": \"add\", \"path\": \"/spec/configuration/secrets\", \"value\": [\"" + dsSecret + "\"]  },"
        + "{\"op\": \"replace\", \"path\": \"/spec/introspectVersion\", \"value\": \"" + introspectVersion + "\"}"
        + "]";
    logger.info("Updating domain configuration using patch string: {0}", patchStr);
    patch = new V1Patch(patchStr);
    assertTrue(patchDomainCustomResource(domainUid, domainNamespace, patch, V1Patch.PATCH_FORMAT_JSON_PATCH),
        "Failed to patch domain");

    verifyIntrospectorRuns();
    verifyPodsStateNotChanged();

    try {
      //wait for a minute to see if the overrides are not applied
      TimeUnit.MINUTES.sleep(1);
    } catch (InterruptedException ex) {
      //ignore
    }

    //verify the overrides are not applied
    verifyConfigXMLOverride(false);
    verifyResourceJDBC0Override(false);

    //restart domain for the distributionstrategy to take effect
    restartDomain();

    //verify on restart the overrides are applied
    verifyConfigXMLOverride(true);
    verifyResourceJDBC1Override(true);

    //cleanup secret
    deleteSecret(dsSecret, domainNamespace);
    patchStr
        = "["
        + "{\"op\": \"remove\", \"path\": \"/spec/configuration/overrideDistributionStrategy\"},"
        + "{\"op\": \"remove\", \"path\": \"/spec/configuration/secrets\"}"
        + "]";
    logger.info("Updating domain configuration using patch string: {0}", patchStr);
    patch = new V1Patch(patchStr);
    assertTrue(patchDomainCustomResource(domainUid, domainNamespace, patch, V1Patch.PATCH_FORMAT_JSON_PATCH),
        "Failed to patch domain");
  }

  /**
   * Test patching the domain with values for /spec/configuration/overrideDistributionStrategy field anything other than
   * Dynamic or OnRestart fails.
   *
   * <p>Test tries to set the above field to OnRestart and asserts the patching fails.
   */
  @Order(5)
  @Test
  @DisplayName("Test invalid overrideDistributionStrategy value RESTART")
  void testOverrideNegative() {

    //patching the domain with /spec/configuration/overrideDistributionStrategy: RESTART
    String patchStr = "["
        + "{\"op\": \"add\", \"path\": \"/spec/configuration/overrideDistributionStrategy\", "
        + "\"value\": \"RESTART\"}"
        + "]";
    logger.info("Updating domain configuration using patch string: {0}", patchStr);
    V1Patch patch = new V1Patch(patchStr);
    assertFalse(patchDomainCustomResource(domainUid, domainNamespace, patch, V1Patch.PATCH_FORMAT_JSON_PATCH),
        "Patch domain with invalid overrideDistributionStrategy succeeded.");

    //verify the overrides are not applied and original configuration is still effective
    verifyConfigXMLOverride(false);
  }

  private Callable<Boolean> configUpdated(String maxMessageSize) {
    logger.info("Getting node port for default channel");
    int serviceNodePort = assertDoesNotThrow(()
        -> getServiceNodePort(domainNamespace, getExternalServicePodName(adminServerPodName),
            "default"),
        "Getting admin server node port failed");

    return (()
        -> {
      String hostAndPort = getHostAndPort(adminSvcExtHost, serviceNodePort);
      logger.info("hostAndPort = {0} ", hostAndPort);

      //verify server attribute MaxMessageSize
      String appURI = "/clusterview/ConfigServlet?"
          + "attributeTest=true&"
          + "serverType=adminserver&"
          + "serverName=" + adminServerName;
      Map<String, String> headers = null;
      if (TestConstants.KIND_CLUSTER
          && !TestConstants.WLSIMG_BUILDER.equals(TestConstants.WLSIMG_BUILDER_DEFAULT)) {
        hostAndPort = "localhost:" + TRAEFIK_INGRESS_HTTP_HOSTPORT;
        headers = new HashMap<>();
        headers.put("host", hostHeader);
      }
      if (OKE_CLUSTER_PRIVATEIP) {
        hostAndPort = ingressIP;
      }
      String url = "http://" + hostAndPort + appURI;
      HttpResponse<String> response = OracleHttpClient.get(url, headers, true);
      assertEquals(200, response.statusCode(), "Status code not equals to 200");
      return response.body().contains("MaxMessageSize=".concat(maxMessageSize));
    });
  }

  private void verifyConfigXMLOverride(boolean configUpdated) {

    int port = getServiceNodePort(domainNamespace, getExternalServicePodName(adminServerPodName), "default");

    testUntil(() -> {
      String hostAndPort = getHostAndPort(adminSvcExtHost, port);
      logger.info("hostAndPort = {0} ", hostAndPort);
      //verify server attribute MaxMessageSize
      String configUri = "ConfigServlet?"
          + "attributeTest=true"
          + "&serverType=adminserver"
          + "&serverName=" + adminServerName;
      Map<String, String> headers = null;
      if (TestConstants.KIND_CLUSTER
          && !TestConstants.WLSIMG_BUILDER.equals(TestConstants.WLSIMG_BUILDER_DEFAULT)) {
        hostAndPort = "localhost:" + TRAEFIK_INGRESS_HTTP_HOSTPORT;
        headers = new HashMap<>();
        headers.put("host", hostHeader);
      }
      if (OKE_CLUSTER_PRIVATEIP) {
        hostAndPort = getHostAndPortOKE();
      }
      String baseUri = "http://" + hostAndPort + "/clusterview/";
      HttpResponse<String> response = OracleHttpClient.get(baseUri + configUri, headers, true);
      if (response.statusCode() != 200) {
        logger.info("Response code is not 200 retrying...");
        return false;
      }
      if (configUpdated) {
        return response.body().contains("MaxMessageSize=100000000");
      } else {
        return response.body().contains("MaxMessageSize=10000000");
      }
    }, logger, "clusterview app in admin server is accessible after restart");

  }

  //use the http client and access the clusterview application to get server configuration
  //and JDBC datasource configuration.
  private void verifyResourceJDBC0Override(boolean configUpdated) {

    // get admin server node port and construct a base url for clusterview app
    int port = getServiceNodePort(domainNamespace, getExternalServicePodName(adminServerPodName), "default");

    testUntil(
        () -> {
          String hostAndPort = getHostAndPort(adminSvcExtHost, port);
          Map<String, String> headers = null;
          if (TestConstants.KIND_CLUSTER
              && !TestConstants.WLSIMG_BUILDER.equals(TestConstants.WLSIMG_BUILDER_DEFAULT)) {
            hostAndPort = "localhost:" + TRAEFIK_INGRESS_HTTP_HOSTPORT;
            headers = new HashMap<>();
            headers.put("host", hostHeader);
          }
          if (OKE_CLUSTER_PRIVATEIP) {
            hostAndPort = getHostAndPortOKE();
          }
          logger.info("hostAndPort = {0} ", hostAndPort);
          String baseUri = "http://" + hostAndPort + "/clusterview/ConfigServlet?";
          //verify datasource attributes of JdbcTestDataSource-0
          String appURI = "resTest=true&resName=" + dsName0;
          String dsOverrideTestUrl = baseUri + appURI;
          HttpResponse<String> response = OracleHttpClient.get(dsOverrideTestUrl, headers, true);
          if (response.statusCode() != 200) {
            logger.info("Response code is not 200 retrying...");
            return false;
          }
          if (configUpdated) {
            if (!(response.body().contains("getMaxCapacity:12"))) {
              logger.info("Did get getMaxCapacity:12");
              return false;
            }
            if (!(response.body().contains("getInitialCapacity:2"))) {
              logger.info("Did get getInitialCapacity:2");
              return false;
            }
          } else {
            if (!(response.body().contains("getMaxCapacity:15"))) {
              logger.info("Did get getMaxCapacity:15");
              return false;
            }
            if (!(response.body().contains("getInitialCapacity:1"))) {
              logger.info("Did get getInitialCapacity:1");
              return false;
            }
          }
          return true;
        },
        logger,
        "clusterview app in admin server is accessible after restart");

    //test connection pool in all managed servers of dynamic cluster
    for (int i = 1; i <= replicaCount; i++) {
      String appURI = "dsTest=true&dsName=" + dsName0 + "&" + "serverName=" + managedServerNameBase + i;
      testDatasource(appURI);
    }
  }

  private void testDatasource(String appURI) {
    int port = getServiceNodePort(domainNamespace, getExternalServicePodName(adminServerPodName), "default");
    testUntil(
        () -> {
          String hostAndPort = getHostAndPort(adminSvcExtHost, port);
          Map<String, String> headers = null;
          if (TestConstants.KIND_CLUSTER
              && !TestConstants.WLSIMG_BUILDER.equals(TestConstants.WLSIMG_BUILDER_DEFAULT)) {
            hostAndPort = "localhost:" + TRAEFIK_INGRESS_HTTP_HOSTPORT;
            headers = new HashMap<>();
            headers.put("host", hostHeader);
          }
          if (OKE_CLUSTER_PRIVATEIP) {
            hostAndPort = getHostAndPortOKE();
          }
          logger.info("hostAndPort = {0} ", hostAndPort);
          String baseUri = "http://" + hostAndPort + "/clusterview/ConfigServlet?";
          
          String dsConnectionPoolTestUrl = baseUri + appURI;
          HttpResponse<String> response = OracleHttpClient.get(dsConnectionPoolTestUrl, headers, true);
          if (response.statusCode() != 200) {
            logger.info("Response code is not 200 retrying...");
            return false;
          }
          if (!(response.body().contains("Connection successful"))) {
            logger.info("Didn't get Connection successful retrying...");
            return false;
          }
          return true;
        },
        logger, "All managed servers get JDBC connection");
  }

  //use the http client and access the clusterview application to get server configuration
  //and JDBC datasource configuration.
  private void verifyResourceJDBC1Override(boolean configUpdated) {

    // get admin server node port and construct a base url for clusterview app
    int port = getServiceNodePort(domainNamespace, getExternalServicePodName(adminServerPodName), "default");


    testUntil(
        () -> {
          String hostAndPort = getHostAndPort(adminSvcExtHost, port);
          logger.info("hostAndPort = {0} ", hostAndPort);

          Map<String, String> headers = null;
          if (TestConstants.KIND_CLUSTER
              && !TestConstants.WLSIMG_BUILDER.equals(TestConstants.WLSIMG_BUILDER_DEFAULT)) {
            hostAndPort = "localhost:" + TRAEFIK_INGRESS_HTTP_HOSTPORT;
            headers = new HashMap<>();
            headers.put("host", hostHeader);
          }
          if (OKE_CLUSTER_PRIVATEIP) {
            hostAndPort = getHostAndPortOKE();
          }
          String baseUri = "http://" + hostAndPort + "/clusterview/ConfigServlet?";

          //verify datasource attributes of JdbcTestDataSource-0
          String appURI = "resTest=true&resName=" + dsName1;
          String dsOverrideTestUrl = baseUri + appURI;
          HttpResponse<String> response = OracleHttpClient.get(dsOverrideTestUrl, headers, true);
          if (response.statusCode() != 200) {
            logger.info("Response code is not 200 retrying...");
            return false;
          }
          if (configUpdated) {
            if (!(response.body().contains("getMaxCapacity:10"))) {
              logger.info("Did get getMaxCapacity:10");
              return false;
            }
            if (!(response.body().contains("getInitialCapacity:4"))) {
              logger.info("Did get getInitialCapacity:4");
              return false;
            }
            if (!(response.body().contains("Url:" + dsUrl2))) {
              logger.info("Didn't get Url:" + dsUrl2);
              return false;
            }
          } else {
            if (!(response.body().contains("getMaxCapacity:15"))) {
              logger.info("Did get getMaxCapacity:15");
              return false;
            }
            if (!(response.body().contains("getInitialCapacity:1"))) {
              logger.info("Did get getInitialCapacity:1");
              return false;
            }
            if (!(response.body().contains("Url:" + dsUrl1))) {
              logger.info("Didn't get Url:" + dsUrl1);
              return false;
            }
          }
          return true;
        },
        logger,
        "clusterview app in admin server is accessible after restart");

    //test connection pool in all managed servers of dynamic cluster
    for (int i = 1; i <= replicaCount; i++) {
      String hostAndPort = getHostAndPort(adminSvcExtHost, port);
      logger.info("hostAndPort = {0} ", hostAndPort);

      Map<String, String> headers = null;
      if (TestConstants.KIND_CLUSTER
          && !TestConstants.WLSIMG_BUILDER.equals(TestConstants.WLSIMG_BUILDER_DEFAULT)) {
        hostAndPort = "localhost:" + TRAEFIK_INGRESS_HTTP_HOSTPORT;
        headers = new HashMap<>();
        headers.put("host", hostHeader);
      }
      if (OKE_CLUSTER_PRIVATEIP) {
        hostAndPort = getHostAndPortOKE();
      }
      String baseUri = "http://" + hostAndPort + "/clusterview/ConfigServlet?";
      String appURI = "dsTest=true&dsName=" + dsName1 + "&" + "serverName=" + managedServerNameBase + i;
      String dsConnectionPoolTestUrl = baseUri + appURI;
      testDatasource(appURI);
    }
  }

  //store pod creation timestamps for podstate check
  private void storePodCreationTimestamps() {
    // get the pod creation time stamps
    podTimestamps = new LinkedHashMap<>();
    // get the creation time of the admin server pod before patching
    OffsetDateTime adminPodCreationTime = getPodCreationTime(domainNamespace, adminServerPodName);
    podTimestamps.put(adminServerPodName, adminPodCreationTime);
    // get the creation time of the managed server pods before patching
    for (int i = 1; i <= replicaCount; i++) {
      podTimestamps.put(managedServerPodNamePrefix + i,
          getPodCreationTime(domainNamespace, managedServerPodNamePrefix + i));
    }
  }

  //check if the pods are restarted by comparing the pod creationtimestamp.
  private void verifyPodsStateNotChanged() {
    logger.info("Verifying the WebLogic server pod states are not changed");
    for (Map.Entry<String, OffsetDateTime> entry : podTimestamps.entrySet()) {
      String podName = entry.getKey();
      OffsetDateTime creationTimestamp = entry.getValue();
      assertTrue(podStateNotChanged(podName, domainUid, domainNamespace,
          creationTimestamp), "Pod is restarted");
    }
  }

  //verify the introspector pod is created and run
  private void verifyIntrospectorRuns() {
    //verify the introspector pod is created and runs
    logger.info("Verifying introspector pod is created, runs and deleted");
    String introspectPodName = getIntrospectJobName(domainUid);
    checkPodExists(introspectPodName, domainUid, domainNamespace);
    checkPodDoesNotExist(introspectPodName, domainUid, domainNamespace);
  }

  //create a standard WebLogic domain.
  private void createDomain() {
    String uniqueDomainHome = "/shared/" + domainNamespace + "/domains";

    // create WebLogic domain credential secret
    createSecretWithUsernamePassword(wlSecretName, domainNamespace,
        ADMIN_USERNAME_DEFAULT, ADMIN_PASSWORD_DEFAULT);
    final String wlsModelFilePrefix = "model-dci-introspect";
    final String wlsModelFile = wlsModelFilePrefix + ".yaml";
    t3ChannelPort = getNextFreePort();
    File wlsModelPropFile = createWdtPropertyFile(wlsModelFilePrefix,
        K8S_NODEPORT_HOST, t3ChannelPort);
    // create domainCreationImage
    String domainCreationImageName = DOMAIN_IMAGES_PREFIX + "configdist-domain-on-pv-image";
    // create image with model and wdt installation files
    WitParams witParams
        = new WitParams()
            .modelImageName(domainCreationImageName)
            .modelImageTag(MII_BASIC_IMAGE_TAG)
            .modelFiles(Collections.singletonList(MODEL_DIR + "/" + wlsModelFile))
            .modelVariableFiles(Collections.singletonList(wlsModelPropFile.getAbsolutePath()));
    createAndPushAuxiliaryImage(domainCreationImageName, MII_BASIC_IMAGE_TAG, witParams);

    DomainCreationImage domainCreationImage
        = new DomainCreationImage().image(domainCreationImageName + ":" + MII_BASIC_IMAGE_TAG);

    // create a domain resource
    logger.info("Creating domain custom resource");
    Map<String, Quantity> pvCapacity = new HashMap<>();
    pvCapacity.put("storage", new Quantity("2Gi"));

    Map<String, Quantity> pvcRequest = new HashMap<>();
    pvcRequest.put("storage", new Quantity("2Gi"));
    Configuration configuration = null;
    final String storageClassName = "weblogic-domain-storage-class";
    if (OKE_CLUSTER) {
      configuration = getConfiguration(pvcName, pvcRequest, "oci-fss");
    } else {
      configuration = getConfiguration(pvName, pvcName, pvCapacity, pvcRequest, storageClassName,
          ItConfigDistributionStrategy.class.getSimpleName());
    }
    configuration.getInitializeDomainOnPV().domain(new DomainOnPV()
        .createMode(CreateIfNotExists.DOMAIN)
        .domainCreationImages(Collections.singletonList(domainCreationImage))
        .domainType(DomainOnPVType.WLS));
    configuration.overrideDistributionStrategy("Dynamic");

    DomainResource domain = createDomainResourceOnPv(domainUid,
        domainNamespace,
        wlSecretName,
        clusterName,
        pvName,
        pvcName,
        new String[]{BASE_IMAGES_REPO_SECRET_NAME},
        uniqueDomainHome,
        2,
        t3ChannelPort,
        configuration);
    domain.spec().serverPod().addEnvItem(new V1EnvVar()
        .name("JAVA_OPTIONS")
        .value("-Dweblogic.debug.DebugSituationalConfig=true "
            + "-Dweblogic.debug.DebugSituationalConfigDumpXml=true "));
    logger.info(Yaml.dump(domain));
    
    // verify the domain custom resource is created
    createDomainAndVerify(domain, domainNamespace);

    // verify the admin server service created
    checkServiceExists(adminServerPodName, domainNamespace);

    // verify admin server pod is ready
    checkPodReady(adminServerPodName, domainUid, domainNamespace);

    // verify managed server services created
    for (int i = 1; i <= replicaCount; i++) {
      logger.info("Checking managed server service {0} is created in namespace {1}",
          managedServerPodNamePrefix + i, domainNamespace);
      checkServiceExists(managedServerPodNamePrefix + i, domainNamespace);
    }

    // verify managed server pods are ready
    for (int i = 1; i <= replicaCount; i++) {
      logger.info("Waiting for managed server pod {0} to be ready in namespace {1}",
          managedServerPodNamePrefix + i, domainNamespace);
      checkPodReady(managedServerPodNamePrefix + i, domainUid, domainNamespace);
    }
    if (TestConstants.KIND_CLUSTER
        && !TestConstants.WLSIMG_BUILDER.equals(TestConstants.WLSIMG_BUILDER_DEFAULT)) {
      hostHeader = createIngressHostRouting(domainNamespace, domainUid, adminServerName, adminPort);
    }
  }

  //deploy application clusterview.war to domain
  private void deployApplication(String targets) {
    logger.info("Getting port for default channel");
    int defaultChannelPort = assertDoesNotThrow(()
        -> getServicePort(domainNamespace, getExternalServicePodName(adminServerPodName), "default"),
        "Getting admin server default port failed");
    logger.info("default channel port: {0}", defaultChannelPort);
    assertNotEquals(-1, defaultChannelPort, "admin server defaultChannelPort is not valid");

    //deploy application
    logger.info("Deploying webapp {0} to domain", clusterViewAppPath);
    deployUsingWlst(adminServerPodName, Integer.toString(defaultChannelPort),
        ADMIN_USERNAME_DEFAULT, ADMIN_PASSWORD_DEFAULT, targets, clusterViewAppPath,
        domainNamespace);
  }

  //restart pods by manipulating the serverStartPolicy to Never and IfNeeded
  private void restartDomain() {
    logger.info("Restarting domain {0}", domainNamespace);
    shutdownDomain(domainUid, domainNamespace);

    logger.info("Checking for admin server pod shutdown");
    checkPodDoesNotExist(adminServerPodName, domainUid, domainNamespace);
    logger.info("Checking managed server pods were shutdown");
    for (int i = 1; i <= replicaCount; i++) {
      checkPodDoesNotExist(managedServerPodNamePrefix + i, domainUid, domainNamespace);
    }

    startDomain(domainUid, domainNamespace);
    //make sure that the introspector runs on a cold start
    verifyIntrospectorRuns();


    // verify the admin server service created
    checkServiceExists(adminServerPodName, domainNamespace);

    logger.info("Checking for admin server pod readiness");
    checkPodReady(adminServerPodName, domainUid, domainNamespace);

    // verify managed server services created
    for (int i = 1; i <= replicaCount; i++) {
      logger.info("Checking managed server service {0} is created in namespace {1}",
          managedServerPodNamePrefix + i, domainNamespace);
      checkServiceExists(managedServerPodNamePrefix + i, domainNamespace);
    }

    logger.info("Checking for managed servers pod readiness");
    for (int i = 1; i <= replicaCount; i++) {
      checkPodReady(managedServerPodNamePrefix + i, domainUid, domainNamespace);
    }
  }

  //create a JDBC datasource targeted to cluster.
  private void createJdbcDataSource(String dsName, String user, String password, String dsUrl) {

    try {
      logger.info("Getting port for default channel");
      int defaultChannelPort = assertDoesNotThrow(()
          -> getServicePort(domainNamespace, getExternalServicePodName(adminServerPodName), "default"),
          "Getting admin server default port failed");
      logger.info("default channel port: {0}", defaultChannelPort);
      assertNotEquals(-1, defaultChannelPort, "admin server defaultChannelPort is not valid");
      String jdbcDsUrl = dsUrl;

      // based on WebLogic image, change the mysql driver to 
      // 12.2.1.3 - com.mysql.jdbc.Driver
      // 12.2.1.4 and above - com.mysql.cj.jdbc.Driver
      // create a temporary WebLogic domain property file
      File domainPropertiesFile = File.createTempFile("domain", ".properties", new File(RESULTS_TEMPFILE));
      Properties p = new Properties();
      p.setProperty("admin_host", adminServerPodName);
      p.setProperty("admin_port", Integer.toString(defaultChannelPort));
      p.setProperty("admin_username", ADMIN_USERNAME_DEFAULT);
      p.setProperty("admin_password", ADMIN_PASSWORD_DEFAULT);
      p.setProperty("dsName", dsName);
      p.setProperty("dsUrl", jdbcDsUrl);
      if (WEBLOGIC_12213) {
        p.setProperty("dsDriver", "com.mysql.jdbc.Driver");
      } else {
        p.setProperty("dsDriver", "com.mysql.cj.jdbc.Driver");
      }
      p.setProperty("dsUser", user);
      p.setProperty("dsPassword", password);
      p.setProperty("dsTarget", clusterName);
      p.store(new FileOutputStream(domainPropertiesFile), "domain properties file");

      // WLST script for creating jdbc datasource
      Path wlstScript = Paths.get(RESOURCE_DIR, "python-scripts", "create-jdbc-resource.py");
      executeWLSTScript(wlstScript, domainPropertiesFile.toPath(), domainNamespace);
    } catch (IOException ex) {
      logger.severe(ex.getMessage());
    }
  }

  private static void runMysqlInsidePod(String podName, String namespace, String password) {
    logger.info("Sleeping for 1 minute before connecting to mysql db");
    assertDoesNotThrow(() -> TimeUnit.MINUTES.sleep(1));
    StringBuffer mysqlCmd = new StringBuffer(KUBERNETES_CLI + " exec -i -n ");
    mysqlCmd.append(namespace);
    mysqlCmd.append(" ");
    mysqlCmd.append(podName);
    mysqlCmd.append(" -- /bin/bash -c \"");
    mysqlCmd.append("mysql --force ");
    mysqlCmd.append("-u root -p" + password);
    mysqlCmd.append(" < /tmp/grant.sql ");
    mysqlCmd.append(" \"");
    logger.info("mysql command {0}", mysqlCmd.toString());
    ExecResult result = assertDoesNotThrow(() -> exec(new String(mysqlCmd), true));
    logger.info("mysql returned {0}", result.toString());
    logger.info("mysql returned EXIT value {0}", result.exitValue());
    assertEquals(0, result.exitValue(), "mysql execution fails");
  }

  private void createFileInPod(String podName, String namespace, String password) throws IOException {
    ExecResult result = assertDoesNotThrow(() -> exec(new String("hostname -i"), true));
    String ip = result.stdout();

    Path sourceFile = Files.writeString(Paths.get(WORK_DIR, "grant.sql"),
        "select user();\n"
            + "SELECT host, user FROM mysql.user;\n"
            + "CREATE USER 'root'@'%' IDENTIFIED BY '" + password + "';\n"
        + "GRANT ALL PRIVILEGES ON *.* TO 'root'@'%' WITH GRANT OPTION;\n"
        + "CREATE USER 'root'@'" + ip + "' IDENTIFIED BY '" + password + "';\n"
        + "GRANT ALL PRIVILEGES ON *.* TO 'root'@'" + ip + "' WITH GRANT OPTION;\n"
        + "SELECT host, user FROM mysql.user;");
    StringBuffer mysqlCmd = new StringBuffer("cat " + sourceFile.toString() + " | ");
    mysqlCmd.append(KUBERNETES_CLI + " exec -i -n ");
    mysqlCmd.append(namespace);
    mysqlCmd.append(" ");
    mysqlCmd.append(podName);
    mysqlCmd.append(" -- /bin/bash -c \"");
    mysqlCmd.append("cat > /tmp/grant.sql\"");
    //logger.info("mysql command {0}", mysqlCmd.toString());
    result = assertDoesNotThrow(() -> exec(new String(mysqlCmd), false));
    //logger.info("mysql returned {0}", result.toString());
    logger.info("mysql returned EXIT value {0}", result.exitValue());
    assertEquals(0, result.exitValue(), "mysql execution fails");
  }

  private void getDomainHealth() throws IOException, InterruptedException {
    testUntil(
        withStandardRetryPolicy,
        () -> {
          String extSvcPodName = getExternalServicePodName(adminServerPodName);
          logger.info("**** adminServerPodName={0}", adminServerPodName);
          logger.info("**** extSvcPodName={0}", extSvcPodName);

          adminSvcExtHost = createRouteForOKD(extSvcPodName, domainNamespace);
          logger.info("**** adminSvcExtHost={0}", adminSvcExtHost);
          logger.info("admin svc host = {0}", adminSvcExtHost);

          logger.info("Getting node port for default channel");
          int serviceNodePort = assertDoesNotThrow(()
              -> getServiceNodePort(domainNamespace, getExternalServicePodName(adminServerPodName), "default"),
              "Getting admin server node port failed");
          String hostAndPort = getHostAndPort(adminSvcExtHost, serviceNodePort);
          boolean ipv6 = K8S_NODEPORT_HOST.contains(":");
          Map<String, String> headers = null;
          if (TestConstants.KIND_CLUSTER
              && !TestConstants.WLSIMG_BUILDER.equals(TestConstants.WLSIMG_BUILDER_DEFAULT)) {
            hostAndPort = formatIPv6Host(InetAddress.getLocalHost().getHostAddress())
                + ":" + TRAEFIK_INGRESS_HTTP_HOSTPORT;
            headers = new HashMap<>();
            headers.put("host", hostHeader);
          }
          if (OKE_CLUSTER_PRIVATEIP) {
            hostAndPort = getHostAndPortOKE();
          }
          String url = "http://" + hostAndPort
              + "/clusterview/ClusterViewServlet?user=" + ADMIN_USERNAME_DEFAULT
              + "&password=" + ADMIN_PASSWORD_DEFAULT + "&ipv6=" + ipv6;

          HttpResponse<String> response;
          response = OracleHttpClient.get(url, headers, true);
          return response.statusCode() == 200;
        },
        logger,
        "clusterview app in admin server is accessible after restart");
  }

  private static String getHostAndPortOKE() {
    String nginxServiceName = nginxHelmParams.getHelmParams().getReleaseName() + "-ingress-nginx-controller";
    int nginxNodePort = assertDoesNotThrow(() -> Service.getServiceNodePort(nginxNamespace, nginxServiceName, "http"),
        "Getting Nginx loadbalancer service node port failed");

    hostAndPort = getServiceExtIPAddrtOke(nginxServiceName, nginxNamespace) != null
      ? getServiceExtIPAddrtOke(nginxServiceName, nginxNamespace) : K8S_NODEPORT_HOST + ":" + nginxNodePort;
    return hostAndPort;
  }
}

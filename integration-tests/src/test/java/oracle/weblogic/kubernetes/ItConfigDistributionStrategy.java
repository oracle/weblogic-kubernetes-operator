// Copyright (c) 2020, 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

import io.kubernetes.client.custom.V1Patch;
import io.kubernetes.client.openapi.models.V1Container;
import io.kubernetes.client.openapi.models.V1EnvVar;
import io.kubernetes.client.openapi.models.V1LocalObjectReference;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1PersistentVolumeClaimVolumeSource;
import io.kubernetes.client.openapi.models.V1Secret;
import io.kubernetes.client.openapi.models.V1Service;
import io.kubernetes.client.openapi.models.V1Volume;
import io.kubernetes.client.openapi.models.V1VolumeMount;
import oracle.weblogic.domain.AdminServer;
import oracle.weblogic.domain.AdminService;
import oracle.weblogic.domain.Channel;
import oracle.weblogic.domain.ClusterResource;
import oracle.weblogic.domain.Configuration;
import oracle.weblogic.domain.DomainResource;
import oracle.weblogic.domain.DomainSpec;
import oracle.weblogic.domain.ServerPod;
import oracle.weblogic.kubernetes.actions.impl.primitive.Kubernetes;
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

import static io.kubernetes.client.util.Yaml.dump;
import static oracle.weblogic.kubernetes.TestConstants.ADMIN_PASSWORD_DEFAULT;
import static oracle.weblogic.kubernetes.TestConstants.ADMIN_USERNAME_DEFAULT;
import static oracle.weblogic.kubernetes.TestConstants.BASE_IMAGES_REPO_SECRET_NAME;
import static oracle.weblogic.kubernetes.TestConstants.DOMAIN_API_VERSION;
import static oracle.weblogic.kubernetes.TestConstants.IMAGE_PULL_POLICY;
import static oracle.weblogic.kubernetes.TestConstants.K8S_NODEPORT_HOST;
import static oracle.weblogic.kubernetes.TestConstants.OKD;
import static oracle.weblogic.kubernetes.TestConstants.WEBLOGIC_12213;
import static oracle.weblogic.kubernetes.TestConstants.WEBLOGIC_IMAGE_TO_USE_IN_SPEC;
import static oracle.weblogic.kubernetes.actions.ActionConstants.APP_DIR;
import static oracle.weblogic.kubernetes.actions.ActionConstants.RESOURCE_DIR;
import static oracle.weblogic.kubernetes.actions.ActionConstants.WORK_DIR;
import static oracle.weblogic.kubernetes.actions.TestActions.createSecret;
import static oracle.weblogic.kubernetes.actions.TestActions.deleteConfigMap;
import static oracle.weblogic.kubernetes.actions.TestActions.deleteSecret;
import static oracle.weblogic.kubernetes.actions.TestActions.getNextIntrospectVersion;
import static oracle.weblogic.kubernetes.actions.TestActions.getServiceNodePort;
import static oracle.weblogic.kubernetes.actions.TestActions.getServicePort;
import static oracle.weblogic.kubernetes.actions.TestActions.listServices;
import static oracle.weblogic.kubernetes.actions.TestActions.shutdownDomain;
import static oracle.weblogic.kubernetes.actions.TestActions.startDomain;
import static oracle.weblogic.kubernetes.actions.impl.Domain.patchDomainCustomResource;
import static oracle.weblogic.kubernetes.actions.impl.primitive.Kubernetes.listConfigMaps;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.podStateNotChanged;
import static oracle.weblogic.kubernetes.utils.BuildApplication.buildApplication;
import static oracle.weblogic.kubernetes.utils.ClusterUtils.createClusterAndVerify;
import static oracle.weblogic.kubernetes.utils.ClusterUtils.createClusterResource;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.checkServiceExists;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.getHostAndPort;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.getNextFreePort;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.getUniqueName;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.testUntil;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.withLongRetryPolicy;
import static oracle.weblogic.kubernetes.utils.ConfigMapUtils.createConfigMapForDomainCreation;
import static oracle.weblogic.kubernetes.utils.ConfigMapUtils.createConfigMapFromFiles;
import static oracle.weblogic.kubernetes.utils.DeployUtil.deployUsingWlst;
import static oracle.weblogic.kubernetes.utils.DomainUtils.createDomainAndVerify;
import static oracle.weblogic.kubernetes.utils.ExecCommand.exec;
import static oracle.weblogic.kubernetes.utils.FileUtils.replaceStringInFile;
import static oracle.weblogic.kubernetes.utils.ImageUtils.createBaseRepoSecret;
import static oracle.weblogic.kubernetes.utils.JobUtils.createDomainJob;
import static oracle.weblogic.kubernetes.utils.JobUtils.getIntrospectJobName;
import static oracle.weblogic.kubernetes.utils.MySQLDBUtils.createMySQLDB;
import static oracle.weblogic.kubernetes.utils.OKDUtils.createRouteForOKD;
import static oracle.weblogic.kubernetes.utils.OperatorUtils.installAndVerifyOperator;
import static oracle.weblogic.kubernetes.utils.PersistentVolumeUtils.createPV;
import static oracle.weblogic.kubernetes.utils.PersistentVolumeUtils.createPVC;
import static oracle.weblogic.kubernetes.utils.PodUtils.checkPodDoesNotExist;
import static oracle.weblogic.kubernetes.utils.PodUtils.checkPodExists;
import static oracle.weblogic.kubernetes.utils.PodUtils.checkPodReady;
import static oracle.weblogic.kubernetes.utils.PodUtils.getExternalServicePodName;
import static oracle.weblogic.kubernetes.utils.PodUtils.getPodCreationTime;
import static oracle.weblogic.kubernetes.utils.PodUtils.setPodAntiAffinity;
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
@Tag("oke-sequential")
@IntegrationTest
@Tag("olcne")
class ItConfigDistributionStrategy {

  private static String opNamespace = null;
  private static String domainNamespace = null;

  final String domainUid = "mydomain";
  final String clusterName = "mycluster";
  final String clusterResName = domainUid + "-" + clusterName;
  final String adminServerName = "admin-server";
  final String adminServerPodName = domainUid + "-" + adminServerName;
  final String managedServerNameBase = "ms-";
  final int managedServerPort = 8001;
  int t3ChannelPort;
  final String pvName = getUniqueName(domainUid + "-pv-");
  final String pvcName = getUniqueName(domainUid + "-pvc-");
  final String wlSecretName = "weblogic-credentials";
  final String managedServerPodNamePrefix = domainUid + "-" + managedServerNameBase;
  int replicaCount = 2;

  static Path clusterViewAppPath;
  String overridecm = "configoverride-cm";
  LinkedHashMap<String, OffsetDateTime> podTimestamps;

  static int mysqlDBPort1;
  static int mysqlDBPort2;
  static String dsUrl1;
  static String dsUrl2;
  static String mysql1SvcEndpoint = null;
  static String mysql2SvcEndpoint = null;

  String dsName0 = "JdbcTestDataSource-0";
  String dsName1 = "JdbcTestDataSource-1";
  String dsSecret = domainUid.concat("-mysql-secret");
  String adminSvcExtHost = null;

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
  public void initAll(@Namespaces(2) List<String> namespaces) {
    logger = getLogger();

    logger.info("Assign a unique namespace for operator");
    assertNotNull(namespaces.get(0), "Namespace is null");
    opNamespace = namespaces.get(0);
    logger.info("Assign a unique namespace for domain namspace");
    assertNotNull(namespaces.get(1), "Namespace is null");
    domainNamespace = namespaces.get(1);

    // install operator and verify its running in ready state
    installAndVerifyOperator(opNamespace, domainNamespace);

    // create pull secrets for WebLogic image when running in non Kind Kubernetes cluster
    // this secret is used only for non-kind cluster
    createBaseRepoSecret(domainNamespace);


    //start two MySQL database instances
    createMySQLDB("mysqldb-1", "root", "root123", getNextFreePort(), domainNamespace, null);
    mysqlDBPort1 = getMySQLNodePort(domainNamespace, "mysqldb-1");
    logger.info("mysqlDBPort1 is: " + mysqlDBPort1);
    createMySQLDB("mysqldb-2", "root", "root456", getNextFreePort(), domainNamespace, null);
    mysqlDBPort2 = getMySQLNodePort(domainNamespace, "mysqldb-2");
    logger.info("mysqlDBPort2 is: " + mysqlDBPort2);

    if (OKD) {
      mysql1SvcEndpoint = getMySQLSvcEndpoint(domainNamespace, "mysqldb-1");
      mysql2SvcEndpoint = getMySQLSvcEndpoint(domainNamespace, "mysqldb-2");
    }

    String mysql1HostAndPort = getHostAndPort(mysql1SvcEndpoint, mysqlDBPort1);
    logger.info("mysql1HostAndPort = {0} ", mysql1HostAndPort);
    String mysql2HostAndPort = getHostAndPort(mysql2SvcEndpoint, mysqlDBPort2);
    logger.info("mysql2HostAndPort = {0} ", mysql2HostAndPort);

    dsUrl1 = "jdbc:mysql://" + mysql1HostAndPort;
    dsUrl2 = "jdbc:mysql://" + mysql2HostAndPort;


    // build the clusterview application
    Path distDir = buildApplication(Paths.get(APP_DIR, "clusterview"),
        null, null, "dist", domainNamespace);
    clusterViewAppPath = Paths.get(distDir.toString(), "clusterview.war");
    assertTrue(clusterViewAppPath.toFile().exists(), "Application archive is not available");

    //create and start WebLogic domain
    createDomain();

    // Expose the admin service external node port as  a route for OKD
    adminSvcExtHost = createRouteForOKD(getExternalServicePodName(adminServerPodName), domainNamespace);

    //create a jdbc resource targeted to cluster
    createJdbcDataSource(dsName0, "root", "root123", mysqlDBPort1, mysql1HostAndPort);
    createJdbcDataSource(dsName1, "root", "root123", mysqlDBPort1, mysql1HostAndPort);
    //deploy application to view server configuration
    deployApplication(clusterName + "," + adminServerName);

  }

  /**
   * Verify the default config before starting any test.
   */
  @BeforeEach
  public void beforeEach() {
    //check configuration values before override
    verifyConfigXMLOverride(false);
    verifyResourceJDBC0Override(false);
  }

  /**
   * Delete the overrides and restart domain to get clean state.
   */
  @AfterEach
  public void afterEach() {
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

    String hostAndPort = getHostAndPort(adminSvcExtHost, serviceNodePort);

    logger.info("Checking if the clusterview app in admin server is accessible after restart");
    String baseUri = "http://" + hostAndPort + "/clusterview/";
    String serverListUri = "ClusterViewServlet?user=" + ADMIN_USERNAME_DEFAULT + "&password=" + ADMIN_PASSWORD_DEFAULT;

    testUntil(
        withLongRetryPolicy,
        () -> {
          HttpResponse<String> response = assertDoesNotThrow(() -> OracleHttpClient.get(baseUri + serverListUri, true));
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
        () -> listConfigMaps(domainNamespace).getItems().stream().noneMatch((cm)
            -> (cm.getMetadata().getName().equals(overridecm))),
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

    String hostAndPort = getHostAndPort(adminSvcExtHost, serviceNodePort);
    logger.info("hostAndPort = {0} ", hostAndPort);

    //verify server attribute MaxMessageSize
    String appURI = "/clusterview/ConfigServlet?"
        + "attributeTest=true&"
        + "serverType=adminserver&"
        + "serverName=" + adminServerName;
    String url = "http://" + hostAndPort + appURI;

    return (()
        -> {
      HttpResponse<String> response = assertDoesNotThrow(() -> OracleHttpClient.get(url, true));
      assertEquals(200, response.statusCode(), "Status code not equals to 200");
      return response.body().contains("MaxMessageSize=".concat(maxMessageSize));
    });
  }

  private void verifyConfigXMLOverride(boolean configUpdated) {

    int port = getServiceNodePort(domainNamespace, getExternalServicePodName(adminServerPodName), "default");

    String hostAndPort = getHostAndPort(adminSvcExtHost, port);
    logger.info("hostAndPort = {0} ", hostAndPort);

    String baseUri = "http://" + hostAndPort + "/clusterview/";

    //verify server attribute MaxMessageSize
    String configUri = "ConfigServlet?"
        + "attributeTest=true"
        + "&serverType=adminserver"
        + "&serverName=" + adminServerName;
    
    testUntil(() -> {
      HttpResponse<String> response = assertDoesNotThrow(() -> OracleHttpClient.get(baseUri + configUri, true));
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

    String hostAndPort = getHostAndPort(adminSvcExtHost, port);
    logger.info("hostAndPort = {0} ", hostAndPort);

    String baseUri = "http://" + hostAndPort + "/clusterview/ConfigServlet?";

    //verify datasource attributes of JdbcTestDataSource-0
    String appURI = "resTest=true&resName=" + dsName0;
    String dsOverrideTestUrl = baseUri + appURI;

    testUntil(
        () -> {
          HttpResponse<String> response = assertDoesNotThrow(() -> OracleHttpClient.get(dsOverrideTestUrl, true));
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
    /*
    HttpResponse<String> response = assertDoesNotThrow(() -> OracleHttpClient.get(dsOverrideTestUrl, true));

    assertEquals(200, response.statusCode(), "Status code not equals to 200");
    if (configUpdated) {
      assertTrue(response.body().contains("getMaxCapacity:12"), "Did get getMaxCapacity:12");
      assertTrue(response.body().contains("getInitialCapacity:2"), "Did get getInitialCapacity:2");
    } else {
      assertTrue(response.body().contains("getMaxCapacity:15"), "Did get getMaxCapacity:15");
      assertTrue(response.body().contains("getInitialCapacity:1"), "Did get getInitialCapacity:1");
    }
    */

    //test connection pool in all managed servers of dynamic cluster
    for (int i = 1; i <= replicaCount; i++) {
      appURI = "dsTest=true&dsName=" + dsName0 + "&" + "serverName=" + managedServerNameBase + i;
      String dsConnectionPoolTestUrl = baseUri + appURI;
      testUntil(
          () -> {
            HttpResponse<String> response = assertDoesNotThrow(() -> OracleHttpClient.get(dsConnectionPoolTestUrl,
                true));
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
  }

  //use the http client and access the clusterview application to get server configuration
  //and JDBC datasource configuration.
  private void verifyResourceJDBC1Override(boolean configUpdated) {

    // get admin server node port and construct a base url for clusterview app
    int port = getServiceNodePort(domainNamespace, getExternalServicePodName(adminServerPodName), "default");

    String hostAndPort = getHostAndPort(adminSvcExtHost, port);
    logger.info("hostAndPort = {0} ", hostAndPort);

    String baseUri = "http://" + hostAndPort + "/clusterview/ConfigServlet?";

    //verify datasource attributes of JdbcTestDataSource-0
    String appURI = "resTest=true&resName=" + dsName1;
    String dsOverrideTestUrl = baseUri + appURI;
    testUntil(
        () -> {
          HttpResponse<String> response = assertDoesNotThrow(() -> OracleHttpClient.get(dsOverrideTestUrl, true));
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
      appURI = "dsTest=true&dsName=" + dsName1 + "&" + "serverName=" + managedServerNameBase + i;
      String dsConnectionPoolTestUrl = baseUri + appURI;
      testUntil(() -> {
        HttpResponse<String> response = assertDoesNotThrow(() -> OracleHttpClient.get(dsConnectionPoolTestUrl, true));
        logger.info("Http response status code {0} \n Http response body {1} ", response.statusCode(), response.body());
        return response.statusCode() == 200 && response.body().contains("Connection successful");
      }, logger, "http response code 200 and message Connection successful");
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
    
    String uniquePath = "/shared/" + domainNamespace + "/domains";

    // create WebLogic domain credential secret
    createSecretWithUsernamePassword(wlSecretName, domainNamespace,
        ADMIN_USERNAME_DEFAULT, ADMIN_PASSWORD_DEFAULT);

    createPV(pvName, domainUid, this.getClass().getSimpleName());
    createPVC(pvName, pvcName, domainUid, domainNamespace);

    t3ChannelPort = getNextFreePort();

    // create a temporary WebLogic domain property file
    File domainPropertiesFile = assertDoesNotThrow(()
        -> File.createTempFile("domain", ".properties"),
        "Failed to create domain properties file");
    Properties p = new Properties();
    p.setProperty("domain_path", uniquePath);
    p.setProperty("domain_name", domainUid);
    p.setProperty("cluster_name", clusterName);
    p.setProperty("admin_server_name", adminServerName);
    p.setProperty("managed_server_port", Integer.toString(managedServerPort));
    p.setProperty("admin_server_port", "7001");
    p.setProperty("admin_username", ADMIN_USERNAME_DEFAULT);
    p.setProperty("admin_password", ADMIN_PASSWORD_DEFAULT);
    p.setProperty("admin_t3_public_address", K8S_NODEPORT_HOST);
    p.setProperty("admin_t3_channel_port", Integer.toString(t3ChannelPort));
    p.setProperty("number_of_ms", "2");
    p.setProperty("managed_server_name_base", managedServerNameBase);
    p.setProperty("domain_logs", uniquePath + "/logs");
    p.setProperty("production_mode_enabled", "true");
    assertDoesNotThrow(()
        -> p.store(new FileOutputStream(domainPropertiesFile), "domain properties file"),
        "Failed to write domain properties file");

    // WLST script for creating domain
    Path wlstScript = Paths.get(RESOURCE_DIR, "python-scripts", "wlst-create-domain-onpv.py");

    // create configmap and domain on persistent volume using the WLST script and property file
    createDomainOnPVUsingWlst(wlstScript, domainPropertiesFile.toPath(),
        pvName, pvcName, domainNamespace);

    // create a domain custom resource configuration object
    logger.info("Creating domain custom resource");
    DomainResource domain = new DomainResource()
        .apiVersion(DOMAIN_API_VERSION)
        .kind("Domain")
        .metadata(new V1ObjectMeta()
            .name(domainUid)
            .namespace(domainNamespace))
        .spec(new DomainSpec()
            .configuration(new Configuration()
                .overrideDistributionStrategy("Dynamic")
                .introspectorJobActiveDeadlineSeconds(300L))
            .domainUid(domainUid)
            .domainHome(uniquePath + "/" + domainUid) // point to domain home in pv
            .domainHomeSourceType("PersistentVolume") // set the domain home source type as pv
            .image(WEBLOGIC_IMAGE_TO_USE_IN_SPEC)
            .imagePullPolicy(IMAGE_PULL_POLICY)
            .imagePullSecrets(Arrays.asList(
                new V1LocalObjectReference()
                    .name(BASE_IMAGES_REPO_SECRET_NAME))) // this secret is used only in non-kind cluster
            .webLogicCredentialsSecret(new V1LocalObjectReference()
                .name(wlSecretName))
            .includeServerOutInPodLog(true)
            .logHomeEnabled(Boolean.TRUE)
            .logHome(uniquePath + "/logs/" + domainUid)
            .dataHome("")
            .serverStartPolicy("IfNeeded")
            .serverPod(new ServerPod() //serverpod
                .addEnvItem(new V1EnvVar()
                    .name("JAVA_OPTIONS")
                    .value("-Dweblogic.debug.DebugSituationalConfig=true "
                        + "-Dweblogic.debug.DebugSituationalConfigDumpXml=true "
                        + "-Dweblogic.kernel.debug=true "
                        + "-Dweblogic.debug.DebugMessaging=true "
                        + "-Dweblogic.debug.DebugConnection=true "
                        + "-Dweblogic.ResolveDNSName=true"))
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
                .adminService(new AdminService()
                    .addChannelsItem(new Channel()
                        .channelName("default")
                        .nodePort(getNextFreePort())))));
    setPodAntiAffinity(domain);
    
    // create cluster object
    ClusterResource cluster = createClusterResource(clusterResName,
        clusterName, domainNamespace, replicaCount);
    logger.info("Creating cluster resource {0} in namespace {1}", clusterResName, domainNamespace);
    createClusterAndVerify(cluster);
    // set cluster references
    domain.getSpec().withCluster(new V1LocalObjectReference().name(clusterResName));
    
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
  private void createJdbcDataSource(String dsName, String user, String password,
                                    int mySQLNodePort, String sqlSvcEndpoint) {

    try {
      logger.info("Getting port for default channel");
      int defaultChannelPort = assertDoesNotThrow(()
          -> getServicePort(domainNamespace, getExternalServicePodName(adminServerPodName), "default"),
          "Getting admin server default port failed");
      logger.info("default channel port: {0}", defaultChannelPort);
      assertNotEquals(-1, defaultChannelPort, "admin server defaultChannelPort is not valid");


      String hostAndPort = getHostAndPort(sqlSvcEndpoint, mySQLNodePort);
      logger.info("hostAndPort = {0} ", hostAndPort);
      String jdbcDsUrl = "jdbc:mysql://" + hostAndPort;

      // based on WebLogic image, change the mysql driver to 
      // 12.2.1.3 - com.mysql.jdbc.Driver
      // 12.2.1.4 and above - com.mysql.cj.jdbc.Driver
      // create a temporary WebLogic domain property file
      File domainPropertiesFile = File.createTempFile("domain", "properties");
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
  private void createDomainOnPVUsingWlst(Path wlstScriptFile, Path domainPropertiesFile,
      String pvName, String pvcName, String namespace) {
    logger.info("Preparing to run create domain job using WLST");

    List<Path> domainScriptFiles = new ArrayList<>();
    domainScriptFiles.add(wlstScriptFile);
    domainScriptFiles.add(domainPropertiesFile);

    logger.info("Creating a config map to hold domain creation scripts");
    String domainScriptConfigMapName = "create-domain-scripts-cm";

    assertDoesNotThrow(
        () -> createConfigMapForDomainCreation(
            domainScriptConfigMapName, domainScriptFiles, namespace, this.getClass().getSimpleName()),
        "Create configmap for domain creation failed");

    // create a V1Container with specific scripts and properties for creating domain
    V1Container jobCreationContainer = new V1Container()
        .addCommandItem("/bin/sh")
        .addArgsItem("/u01/oracle/oracle_common/common/bin/wlst.sh")
        .addArgsItem("/u01/weblogic/" + wlstScriptFile.getFileName()) //wlst.sh
        // script
        .addArgsItem("-skipWLSModuleScanning")
        .addArgsItem("-loadProperties")
        .addArgsItem("/u01/weblogic/" + domainPropertiesFile.getFileName());
    //domain property file

    logger.info("Running a Kubernetes job to create the domain");
    createDomainJob(WEBLOGIC_IMAGE_TO_USE_IN_SPEC, pvName, pvcName, domainScriptConfigMapName,
        namespace, jobCreationContainer);
  }

  private static Integer getMySQLNodePort(String namespace, String dbName) {
    logger.info(dump(Kubernetes.listServices(namespace)));
    List<V1Service> services = listServices(namespace).getItems();
    for (V1Service service : services) {
      if (service.getMetadata().getName().startsWith(dbName)) {
        return service.getSpec().getPorts().get(0).getNodePort();
      }
    }
    return -1;
  }

  private static String getMySQLSvcName(String namespace, String dbName) {
    logger.info(dump(Kubernetes.listServices(namespace)));
    List<V1Service> services = listServices(namespace).getItems();
    for (V1Service service : services) {
      if (service.getMetadata().getName().startsWith(dbName)) {
        return service.getMetadata().getName();
      }
    }
    return null;
  }

  private static String getMySQLSvcEndpoint(String domainNamespace, String dbName) {
    String svcName = getMySQLSvcName(domainNamespace, dbName);
    String command = new String("oc -n " + domainNamespace + " get ep | grep " + svcName + " |  awk '{print $2}'");
    ExecResult result = null;
    try {
      result = exec(new String(command), true);
      getLogger().info("The command returned exit value: "
          + result.exitValue() + " command output: "
          + result.stderr() + "\n" + result.stdout());
      assertTrue((result.exitValue() == 0),
             "curl command returned non zero value");
    } catch (Exception e) {
      getLogger().info("Got exception, command failed with errors " + e.getMessage());
    }
    return  result.stdout();
  }

}

// Copyright (c) 2020, 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
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
import io.kubernetes.client.openapi.models.V1SecretReference;
import io.kubernetes.client.openapi.models.V1Service;
import io.kubernetes.client.openapi.models.V1Volume;
import io.kubernetes.client.openapi.models.V1VolumeMount;
import oracle.weblogic.domain.AdminServer;
import oracle.weblogic.domain.AdminService;
import oracle.weblogic.domain.Channel;
import oracle.weblogic.domain.Cluster;
import oracle.weblogic.domain.Configuration;
import oracle.weblogic.domain.Domain;
import oracle.weblogic.domain.DomainSpec;
import oracle.weblogic.domain.ServerPod;
import oracle.weblogic.kubernetes.actions.impl.primitive.Kubernetes;
import oracle.weblogic.kubernetes.annotations.IntegrationTest;
import oracle.weblogic.kubernetes.annotations.Namespaces;
import oracle.weblogic.kubernetes.logging.LoggingFacade;
import oracle.weblogic.kubernetes.utils.OracleHttpClient;
import org.awaitility.core.ConditionFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import static io.kubernetes.client.util.Yaml.dump;
import static java.util.concurrent.TimeUnit.MINUTES;
import static java.util.concurrent.TimeUnit.SECONDS;
import static oracle.weblogic.kubernetes.TestConstants.ADMIN_PASSWORD_DEFAULT;
import static oracle.weblogic.kubernetes.TestConstants.ADMIN_USERNAME_DEFAULT;
import static oracle.weblogic.kubernetes.TestConstants.BASE_IMAGES_REPO_SECRET;
import static oracle.weblogic.kubernetes.TestConstants.DOMAIN_API_VERSION;
import static oracle.weblogic.kubernetes.TestConstants.K8S_NODEPORT_HOST;
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
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.checkPodDoesNotExist;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.checkPodExists;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.checkPodReady;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.checkServiceExists;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.createConfigMapForDomainCreation;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.createConfigMapFromFiles;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.createDomainAndVerify;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.createDomainJob;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.createPV;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.createPVC;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.createSecretForBaseImages;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.createSecretWithUsernamePassword;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.getExternalServicePodName;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.getIntrospectJobName;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.getPodCreationTime;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.installAndVerifyOperator;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.setPodAntiAffinity;
import static oracle.weblogic.kubernetes.utils.DeployUtil.deployUsingWlst;
import static oracle.weblogic.kubernetes.utils.MySQLDBUtils.createMySQLDB;
import static oracle.weblogic.kubernetes.utils.TestUtils.getNextFreePort;
import static oracle.weblogic.kubernetes.utils.ThreadSafeLogger.getLogger;
import static oracle.weblogic.kubernetes.utils.WLSTUtils.executeWLSTScript;
import static org.awaitility.Awaitility.with;
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
@IntegrationTest
public class ItConfigDistributionStrategy {

  private static String opNamespace = null;
  private static String domainNamespace = null;

  final String domainUid = "mydomain";
  final String clusterName = "mycluster";
  final String adminServerName = "admin-server";
  final String adminServerPodName = domainUid + "-" + adminServerName;
  final String managedServerNameBase = "ms-";
  final int managedServerPort = 8001;
  int t3ChannelPort;
  final String pvName = domainUid + "-pv"; // name of the persistent volume
  final String pvcName = domainUid + "-pvc"; // name of the persistent volume claim
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

  String dsName0 = "JdbcTestDataSource-0";
  String dsName1 = "JdbcTestDataSource-1";
  String dsSecret = domainUid.concat("-mysql-secret");

  // create standard, reusable retry/backoff policy
  private static final ConditionFactory withStandardRetryPolicy
      = with().pollDelay(2, SECONDS)
          .and().with().pollInterval(10, SECONDS)
          .atMost(15, MINUTES).await();
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
    createSecretForBaseImages(domainNamespace);


    //start two MySQL database instances
    createMySQLDB("mysqldb-1", "root", "root123", 0, domainNamespace, null);
    mysqlDBPort1 = getMySQLNodePort(domainNamespace, "mysqldb-1");
    createMySQLDB("mysqldb-2", "root", "root456", 0, domainNamespace, null);
    mysqlDBPort2 = getMySQLNodePort(domainNamespace, "mysqldb-2");

    dsUrl1 = "jdbc:mysql://" + K8S_NODEPORT_HOST + ":" + mysqlDBPort1;
    dsUrl2 = "jdbc:mysql://" + K8S_NODEPORT_HOST + ":" + mysqlDBPort2;

    // build the clusterview application
    Path distDir = buildApplication(Paths.get(APP_DIR, "clusterview"),
        null, null, "dist", domainNamespace);
    clusterViewAppPath = Paths.get(distDir.toString(), "clusterview.war");
    assertTrue(clusterViewAppPath.toFile().exists(), "Application archive is not available");

    //create and start WebLogic domain
    createDomain();
    //create a jdbc resource targeted to cluster
    createJdbcDataSource(dsName0, "root", "root123", mysqlDBPort1);
    createJdbcDataSource(dsName1, "root", "root123", mysqlDBPort1);
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

    logger.info("Checking if the clusterview app in admin server is accessible after restart");
    String baseUri = "http://" + K8S_NODEPORT_HOST + ":" + serviceNodePort + "/clusterview/";
    String serverListUri = "ClusterViewServlet?user=" + ADMIN_USERNAME_DEFAULT + "&password=" + ADMIN_PASSWORD_DEFAULT;

    withStandardRetryPolicy
        .conditionEvaluationListener(
            condition -> logger.info("Waiting for clusterview app in admin server is accessible after restart"
                + "(elapsed time {0} ms, remaining time {1} ms)",
                condition.getElapsedTimeInMS(),
                condition.getRemainingTimeInMS()))
        .until((Callable<Boolean>) () -> {
          HttpResponse<String> response = assertDoesNotThrow(() -> OracleHttpClient.get(baseUri + serverListUri, true));
          return response.statusCode() == 200;
        });
  }

  /**
   * Test server configuration and JDBC datasource configurations are overridden dynamically when
   * /spec/configuration/overrideDistributionStrategy: field is not set. By default it should be DYNAMIC.
   *
   * <p>Test sets the /spec/configuration/overridesConfigMap and with new configuration for config.xml and datasources.
   *
   * <p>Verifies after introspector runs the server configuration and JDBC datasource configurations are updated
   * as expected.
   */
  @Order(1)
  @Test
  @DisplayName("Test overrideDistributionStrategy set to DEFAULT")
  public void testDefaultOverride() {

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

    //wait until config is updated upto 5 minutes
    withStandardRetryPolicy
        .conditionEvaluationListener(
            condition -> logger.info("Waiting for server configuration to be updated"
                + "(elapsed time {0} ms, remaining time {1} ms)",
                condition.getElapsedTimeInMS(),
                condition.getRemainingTimeInMS()))
        .until(configUpdated("100000000"));

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
  public void testModifiedOverrideContent() {

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

    //wait until config is updated upto 5 minutes
    withStandardRetryPolicy
        .conditionEvaluationListener(
            condition -> logger.info("Waiting for server configuration to be updated"
                    + "(elapsed time {0} ms, remaining time {1} ms)",
                condition.getElapsedTimeInMS(),
                condition.getRemainingTimeInMS()))
        .until(configUpdated("100000000"));

    verifyConfigXMLOverride(true);
    verifyResourceJDBC0Override(true);

    logger.info("Deleting the old override configmap {0}", overridecm);
    deleteConfigMap(overridecm, domainNamespace);

    withStandardRetryPolicy.conditionEvaluationListener(
        condition -> logger.info("Waiting for configmap {0} to be deleted. Elapsed time{1}, remaining time {2}",
            overridecm, condition.getElapsedTimeInMS(), condition.getRemainingTimeInMS())).until(() -> {
              return listConfigMaps(domainNamespace).getItems().stream().noneMatch((cm)
                  -> (cm.getMetadata().getName().equals(overridecm)));
            });


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

    //wait until config is updated upto 5 minutes
    withStandardRetryPolicy
        .conditionEvaluationListener(
            condition -> logger.info("Waiting for server configuration to be updated"
                    + "(elapsed time {0} ms, remaining time {1} ms)",
                condition.getElapsedTimeInMS(),
                condition.getRemainingTimeInMS()))
        .until(configUpdated("99999999"));

    verifyResourceJDBC0Override(true);

  }

  /**
   * Test server configuration and datasource configurations are dynamically overridden when
   * /spec/configuration/overrideDistributionStrategy is set to DYNAMIC.
   *
   * <p>Test sets the above field to DYNAMIC and overrides the /spec/configuration/overridesConfigMap
   * with new configuration.
   *
   * <p>Verifies after introspector runs and the server configuration and JDBC datasource configurations are
   * updated as expected.
   */
  @Order(3)
  @Test
  @DisplayName("Test overrideDistributionStrategy value DYNAMIC")
  public void testDynamicOverride() {

    //patching the domain with /spec/configuration/overrideDistributionStrategy: DYNAMIC
    String patchStr = "["
        + "{\"op\": \"add\", \"path\": \"/spec/configuration/overrideDistributionStrategy\", "
        + "\"value\": \"DYNAMIC\"}"
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
    withStandardRetryPolicy
        .conditionEvaluationListener(
            condition -> logger.info("Waiting for server configuration to be updated"
                + "(elapsed time {0} ms, remaining time {1} ms)",
                condition.getElapsedTimeInMS(),
                condition.getRemainingTimeInMS()))
        .until(configUpdated("100000000"));

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
   * /spec/configuration/overrideDistributionStrategy is set to ON_RESTART.
   *
   * <p>Test sets the above field to ON_RESTART and overrides the /spec/configuration/overridesConfigMap and
   * /spec/configuration/secrets with new configuration and new secrets.
   *
   * <p>Verifies after introspector runs the server configuration and JDBC datasource configurations are not
   * updated. Verifies the overrides are applied only after a domain restart.
   */
  @Order(4)
  @Test
  @DisplayName("Test overrideDistributionStrategy value ON_RESTART")
  public void testOnRestartOverride() {

    //patching the domain with /spec/configuration/overrideDistributionStrategy: ON_RESTART
    String patchStr = "["
        + "{\"op\": \"add\", \"path\": \"/spec/configuration/overrideDistributionStrategy\", "
        + "\"value\": \"ON_RESTART\"}"
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
    String tempString = assertDoesNotThrow(()
        -> Files.readString(srcDsOverrideFile).replaceAll("JDBC_URL", dsUrl2));
    assertDoesNotThrow(()
        -> Files.write(dstDsOverrideFile, tempString.getBytes(StandardCharsets.UTF_8)));

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
   * DYNAMIC or ON_RESTART fails.
   *
   * <p>Test tries to set the above field to RESTART and asserts the patching fails.
   */
  @Order(5)
  @Test
  @DisplayName("Test invalid overrideDistributionStrategy value RESTART")
  public void testOverrideNegative() {

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

    //verify server attribute MaxMessageSize
    String appURI = "/clusterview/ConfigServlet?"
        + "attributeTest=true&"
        + "serverType=adminserver&"
        + "serverName=" + adminServerName;
    String url = "http://" + K8S_NODEPORT_HOST + ":" + serviceNodePort + appURI;

    return (()
        -> {
      HttpResponse<String> response = assertDoesNotThrow(() -> OracleHttpClient.get(url, true));
      assertEquals(200, response.statusCode(), "Status code not equals to 200");
      return response.body().contains("MaxMessageSize=".concat(maxMessageSize));
    });
  }

  private void verifyConfigXMLOverride(boolean configUpdated) {

    int port = getServiceNodePort(domainNamespace, getExternalServicePodName(adminServerPodName), "default");
    String baseUri = "http://" + K8S_NODEPORT_HOST + ":" + port + "/clusterview/";

    //verify server attribute MaxMessageSize
    String configUri = "ConfigServlet?"
        + "attributeTest=true"
        + "&serverType=adminserver"
        + "&serverName=" + adminServerName;
    HttpResponse<String> response = assertDoesNotThrow(() -> OracleHttpClient.get(baseUri + configUri, true));

    assertEquals(200, response.statusCode(), "Status code not equals to 200");
    if (configUpdated) {
      assertTrue(response.body().contains("MaxMessageSize=100000000"), "Didn't get MaxMessageSize=100000000");
    } else {
      assertTrue(response.body().contains("MaxMessageSize=10000000"), "Didn't get MaxMessageSize=10000000");
    }

  }

  //use the http client and access the clusterview application to get server configuration
  //and JDBC datasource configuration.
  private void verifyResourceJDBC0Override(boolean configUpdated) {

    // get admin server node port and construct a base url for clusterview app
    int port = getServiceNodePort(domainNamespace, getExternalServicePodName(adminServerPodName), "default");
    String baseUri = "http://" + K8S_NODEPORT_HOST + ":" + port + "/clusterview/ConfigServlet?";

    //verify datasource attributes of JdbcTestDataSource-0
    String appURI = "resTest=true&resName=" + dsName0;
    String dsOverrideTestUrl = baseUri + appURI;
    HttpResponse<String> response = assertDoesNotThrow(() -> OracleHttpClient.get(dsOverrideTestUrl, true));

    assertEquals(200, response.statusCode(), "Status code not equals to 200");
    if (configUpdated) {
      assertTrue(response.body().contains("getMaxCapacity:12"), "Did get getMaxCapacity:12");
      assertTrue(response.body().contains("getInitialCapacity:2"), "Did get getInitialCapacity:2");
    } else {
      assertTrue(response.body().contains("getMaxCapacity:15"), "Did get getMaxCapacity:15");
      assertTrue(response.body().contains("getInitialCapacity:1"), "Did get getInitialCapacity:1");
    }

    //test connection pool in all managed servers of dynamic cluster
    for (int i = 1; i <= replicaCount; i++) {
      appURI = "dsTest=true&dsName=" + dsName0 + "&" + "serverName=" + managedServerNameBase + i;
      String dsConnectionPoolTestUrl = baseUri + appURI;
      response = assertDoesNotThrow(() -> OracleHttpClient.get(dsConnectionPoolTestUrl, true));
      assertEquals(200, response.statusCode(), "Status code not equals to 200");
      assertTrue(response.body().contains("Connection successful"), "Didn't get Connection successful");
    }
  }

  //use the http client and access the clusterview application to get server configuration
  //and JDBC datasource configuration.
  private void verifyResourceJDBC1Override(boolean configUpdated) {

    // get admin server node port and construct a base url for clusterview app
    int port = getServiceNodePort(domainNamespace, getExternalServicePodName(adminServerPodName), "default");
    String baseUri = "http://" + K8S_NODEPORT_HOST + ":" + port + "/clusterview/ConfigServlet?";

    //verify datasource attributes of JdbcTestDataSource-0
    String appURI = "resTest=true&resName=" + dsName1;
    String dsOverrideTestUrl = baseUri + appURI;
    HttpResponse<String> response = assertDoesNotThrow(() -> OracleHttpClient.get(dsOverrideTestUrl, true));

    assertEquals(200, response.statusCode(), "Status code not equals to 200");
    if (configUpdated) {
      assertTrue(response.body().contains("getMaxCapacity:10"), "Did get getMaxCapacity:10");
      assertTrue(response.body().contains("getInitialCapacity:4"), "Did get getInitialCapacity:4");
      assertTrue(response.body().contains("Url:" + dsUrl2), "Didn't get Url:" + dsUrl2);
    } else {
      assertTrue(response.body().contains("getMaxCapacity:15"), "Did get getMaxCapacity:15");
      assertTrue(response.body().contains("getInitialCapacity:1"), "Did get getInitialCapacity:1");
      assertTrue(response.body().contains("Url:" + dsUrl1), "Didn't get Url:" + dsUrl1);
    }

    //test connection pool in all managed servers of dynamic cluster
    for (int i = 1; i <= replicaCount; i++) {
      appURI = "dsTest=true&dsName=" + dsName1 + "&" + "serverName=" + managedServerNameBase + i;
      String dsConnectionPoolTestUrl = baseUri + appURI;
      response = assertDoesNotThrow(() -> OracleHttpClient.get(dsConnectionPoolTestUrl, true));
      assertEquals(200, response.statusCode(), "Status code not equals to 200");
      assertTrue(response.body().contains("Connection successful"), "Didn't get Connection successful");
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
      String podName = (String) entry.getKey();
      OffsetDateTime creationTimestamp = (OffsetDateTime) entry.getValue();
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

    // create WebLogic domain credential secret
    createSecretWithUsernamePassword(wlSecretName, domainNamespace,
        ADMIN_USERNAME_DEFAULT, ADMIN_PASSWORD_DEFAULT);

    createPV(pvName, domainUid, this.getClass().getSimpleName());
    createPVC(pvName, pvcName, domainUid, domainNamespace);

    t3ChannelPort = getNextFreePort(31518, 32767);

    // create a temporary WebLogic domain property file
    File domainPropertiesFile = assertDoesNotThrow(()
        -> File.createTempFile("domain", ".properties"),
        "Failed to create domain properties file");
    Properties p = new Properties();
    p.setProperty("domain_path", "/shared/domains");
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
    p.setProperty("domain_logs", "/shared/logs");
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
    Domain domain = new Domain()
        .apiVersion(DOMAIN_API_VERSION)
        .kind("Domain")
        .metadata(new V1ObjectMeta()
            .name(domainUid)
            .namespace(domainNamespace))
        .spec(new DomainSpec()
            .configuration(new Configuration()
                .overrideDistributionStrategy("DYNAMIC"))
            .domainUid(domainUid)
            .domainHome("/shared/domains/" + domainUid) // point to domain home in pv
            .domainHomeSourceType("PersistentVolume") // set the domain home source type as pv
            .image(WEBLOGIC_IMAGE_TO_USE_IN_SPEC)
            .imagePullPolicy("IfNotPresent")
            .imagePullSecrets(Arrays.asList(
                new V1LocalObjectReference()
                    .name(BASE_IMAGES_REPO_SECRET))) // this secret is used only in non-kind cluster
            .webLogicCredentialsSecret(new V1SecretReference()
                .name(wlSecretName)
                .namespace(domainNamespace))
            .includeServerOutInPodLog(true)
            .logHomeEnabled(Boolean.TRUE)
            .logHome("/shared/logs/" + domainUid)
            .dataHome("")
            .serverStartPolicy("IF_NEEDED")
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
                .serverStartState("RUNNING")
                .adminService(new AdminService()
                    .addChannelsItem(new Channel()
                        .channelName("default")
                        .nodePort(0))))
            .addClustersItem(new Cluster() //cluster
                .clusterName(clusterName)
                .replicas(replicaCount)
                .serverStartState("RUNNING")));
    setPodAntiAffinity(domain);
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

  //restart pods by manipulating the serverStartPolicy to NEVER and IF_NEEDED
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
  private void createJdbcDataSource(String dsName, String user, String password, int mySQLNodePort) {

    try {
      logger.info("Getting port for default channel");
      int defaultChannelPort = assertDoesNotThrow(()
          -> getServicePort(domainNamespace, getExternalServicePodName(adminServerPodName), "default"),
          "Getting admin server default port failed");
      logger.info("default channel port: {0}", defaultChannelPort);
      assertNotEquals(-1, defaultChannelPort, "admin server defaultChannelPort is not valid");

      String jdbcDsUrl = "jdbc:mysql://" + K8S_NODEPORT_HOST + ":" + mySQLNodePort;

      // create a temporary WebLogic domain property file
      File domainPropertiesFile = File.createTempFile("domain", "properties");
      Properties p = new Properties();
      p.setProperty("admin_host", adminServerPodName);
      p.setProperty("admin_port", Integer.toString(defaultChannelPort));
      p.setProperty("admin_username", ADMIN_USERNAME_DEFAULT);
      p.setProperty("admin_password", ADMIN_PASSWORD_DEFAULT);
      p.setProperty("dsName", dsName);
      p.setProperty("dsUrl", jdbcDsUrl);
      p.setProperty("dsDriver", "com.mysql.cj.jdbc.Driver");
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

}

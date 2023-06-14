// Copyright (c) 2020, 2023, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.kubernetes.client.openapi.models.V1EmptyDirVolumeSource;
import io.kubernetes.client.openapi.models.V1EnvVar;
import io.kubernetes.client.openapi.models.V1LocalObjectReference;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1Volume;
import io.kubernetes.client.openapi.models.V1VolumeMount;
import oracle.weblogic.domain.AdminServer;
import oracle.weblogic.domain.AdminService;
import oracle.weblogic.domain.Channel;
import oracle.weblogic.domain.ClusterResource;
import oracle.weblogic.domain.Configuration;
import oracle.weblogic.domain.DomainResource;
import oracle.weblogic.domain.DomainSpec;
import oracle.weblogic.domain.FluentdSpecification;
import oracle.weblogic.domain.Model;
import oracle.weblogic.domain.ServerPod;
import oracle.weblogic.kubernetes.actions.impl.LoggingExporterParams;
import oracle.weblogic.kubernetes.actions.impl.OperatorParams;
import oracle.weblogic.kubernetes.actions.impl.primitive.HelmParams;
import oracle.weblogic.kubernetes.annotations.IntegrationTest;
import oracle.weblogic.kubernetes.annotations.Namespaces;
import oracle.weblogic.kubernetes.assertions.impl.Cluster;
import oracle.weblogic.kubernetes.logging.LoggingFacade;
import oracle.weblogic.kubernetes.utils.ExecResult;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static oracle.weblogic.kubernetes.TestConstants.ADMIN_PASSWORD_DEFAULT;
import static oracle.weblogic.kubernetes.TestConstants.ADMIN_USERNAME_DEFAULT;
import static oracle.weblogic.kubernetes.TestConstants.CLUSTER_VERSION;
import static oracle.weblogic.kubernetes.TestConstants.DOMAIN_API_VERSION;
import static oracle.weblogic.kubernetes.TestConstants.ELASTICSEARCH_HTTPS_PORT;
import static oracle.weblogic.kubernetes.TestConstants.ELASTICSEARCH_HTTP_PORT;
import static oracle.weblogic.kubernetes.TestConstants.ELASTICSEARCH_IMAGE;
import static oracle.weblogic.kubernetes.TestConstants.ELASTICSEARCH_NAME;
import static oracle.weblogic.kubernetes.TestConstants.FLUENTD_IMAGE;
import static oracle.weblogic.kubernetes.TestConstants.FLUENTD_INDEX_KEY;
import static oracle.weblogic.kubernetes.TestConstants.INTROSPECTOR_INDEX_KEY;
import static oracle.weblogic.kubernetes.TestConstants.KIBANA_IMAGE;
import static oracle.weblogic.kubernetes.TestConstants.KIBANA_INDEX_KEY;
import static oracle.weblogic.kubernetes.TestConstants.KIBANA_NAME;
import static oracle.weblogic.kubernetes.TestConstants.KIBANA_PORT;
import static oracle.weblogic.kubernetes.TestConstants.KIBANA_TYPE;
import static oracle.weblogic.kubernetes.TestConstants.MII_BASIC_APP_NAME;
import static oracle.weblogic.kubernetes.TestConstants.OKD;
import static oracle.weblogic.kubernetes.TestConstants.OPERATOR_CHART_DIR;
import static oracle.weblogic.kubernetes.TestConstants.OPERATOR_RELEASE_NAME;
import static oracle.weblogic.kubernetes.TestConstants.SKIP_CLEANUP;
import static oracle.weblogic.kubernetes.TestConstants.TEST_IMAGES_REPO_SECRET_NAME;
import static oracle.weblogic.kubernetes.actions.ActionConstants.MODEL_DIR;
import static oracle.weblogic.kubernetes.actions.ActionConstants.WORK_DIR;
import static oracle.weblogic.kubernetes.actions.TestActions.execCommand;
import static oracle.weblogic.kubernetes.actions.TestActions.getOperatorPodName;
import static oracle.weblogic.kubernetes.utils.ClusterUtils.createClusterAndVerify;
import static oracle.weblogic.kubernetes.utils.ClusterUtils.createClusterResource;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.checkServiceExists;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.getNextFreePort;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.withStandardRetryPolicy;
import static oracle.weblogic.kubernetes.utils.DomainUtils.createDomainAndVerify;
import static oracle.weblogic.kubernetes.utils.FileUtils.copy;
import static oracle.weblogic.kubernetes.utils.ImageUtils.createMiiImageAndVerify;
import static oracle.weblogic.kubernetes.utils.ImageUtils.createTestRepoSecret;
import static oracle.weblogic.kubernetes.utils.ImageUtils.imageRepoLoginAndPushImageToRegistry;
import static oracle.weblogic.kubernetes.utils.LoggingExporterUtils.installAndVerifyElasticsearch;
import static oracle.weblogic.kubernetes.utils.LoggingExporterUtils.installAndVerifyKibana;
import static oracle.weblogic.kubernetes.utils.LoggingExporterUtils.uninstallAndVerifyElasticsearch;
import static oracle.weblogic.kubernetes.utils.LoggingExporterUtils.uninstallAndVerifyKibana;
import static oracle.weblogic.kubernetes.utils.LoggingExporterUtils.verifyLoggingExporterReady;
import static oracle.weblogic.kubernetes.utils.OKDUtils.addSccToNsSvcAccount;
import static oracle.weblogic.kubernetes.utils.OperatorUtils.installAndVerifyOperator;
import static oracle.weblogic.kubernetes.utils.OperatorUtils.upgradeAndVerifyOperator;
import static oracle.weblogic.kubernetes.utils.PodUtils.checkPodReady;
import static oracle.weblogic.kubernetes.utils.PodUtils.setPodAntiAffinity;
import static oracle.weblogic.kubernetes.utils.SecretUtils.createSecretWithUsernamePasswordElk;
import static oracle.weblogic.kubernetes.utils.ThreadSafeLogger.getLogger;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * To test ELK Stack used in Operator env, this Elasticsearch test does
 * 1. Install Kibana/Elasticsearch.
 * 2. Install and start Operator with ELK Stack enabled.
 * 3. Verify that ELK Stack is ready to use by checking the index status of
 *    Kibana and Logstash created in the Operator pod successfully.
 * 4. Create and start the WebLogic domain with fluentd configuration
 *    and fluentd container added.
 * 5. Verify that fluentd is used to send WebLogic server log information to Elasticsearch
 */
@DisplayName("Test to use Elasticsearch API to query WebLogic logs")
@IntegrationTest
@Tag("olcne")
@Tag("oke-parallel")
@Tag("kind-parallel")
@Tag("okd-wls-mrg")
class ItElasticLoggingFluentd {

  // constants for creating domain image using model in image
  private static final String WLS_ELK_LOGGING_MODEL_FILE = WORK_DIR + "/" + "new.model.wlslogging.yaml";
  private static final String WLS_ELK_LOGGING_IMAGE_NAME = "wls-logging-image";
  private static final String FLUENTD_CONFIGMAP_YAML = "fluentd.configmap.elk.yaml";

  // constants for Domain
  private static String domainUid = "elk-domain1";
  private static String clusterName = "cluster-1";
  private static String adminServerName = "admin-server";
  private static String adminServerPodName = domainUid + "-" + adminServerName;
  private static String managedServerPrefix = "managed-server";
  private static String managedServerPodPrefix = domainUid + "-" + managedServerPrefix;
  private static int replicaCount = 2;

  private static String opNamespace = null;
  private static String domainNamespace = null;

  private static LoggingExporterParams elasticsearchParams = null;
  private static LoggingExporterParams kibanaParams = null;
  private static LoggingFacade logger = null;
  static String elasticSearchHost;
  static String elasticSearchNs;

  private static String k8sExecCmdPrefix;
  private static Map<String, String> testVarMap;

  /**
   * Install Elasticsearch, Kibana and Operator.
   * Create domain with fluentd configuration.
   *
   * @param namespaces list of namespaces created by the IntegrationTestWatcher by the
   *                   JUnit engine parameter resolution mechanism.
   */
  @BeforeAll
  public static void init(@Namespaces(3) List<String> namespaces) {
    logger = getLogger();

    // get a new unique opNamespace
    logger.info("Assigning a unique namespace for Operator");
    assertNotNull(namespaces.get(0), "Namespace list is null");
    opNamespace = namespaces.get(0);

    // get a new unique domainNamespace
    logger.info("Assigning a unique namespace for Domain");
    assertNotNull(namespaces.get(1), "Namespace list is null");
    domainNamespace = namespaces.get(1);

    // install and verify Elasticsearch
    elasticSearchNs = namespaces.get(2);
    if (OKD) {
      addSccToNsSvcAccount("default", elasticSearchNs);
    }
    logger.info("install and verify Elasticsearch");
    elasticsearchParams = assertDoesNotThrow(() -> installAndVerifyElasticsearch(elasticSearchNs),
            "Failed to install Elasticsearch");
    assertNotNull(elasticsearchParams, "Failed to install Elasticsearch");

    // install and verify Kibana
    logger.info("install and verify Kibana");
    kibanaParams = assertDoesNotThrow(() -> installAndVerifyKibana(elasticSearchNs), "Failed to install Kibana");
    assertNotNull(kibanaParams, "Failed to install Kibana");

    // install and verify Operator
    installAndVerifyOperator(opNamespace, opNamespace + "-sa",
        false, 0, true, domainNamespace);

    elasticSearchHost = "elasticsearch." + elasticSearchNs + ".svc";

    // upgrade to latest operator
    HelmParams upgradeHelmParams = new HelmParams()
        .releaseName(OPERATOR_RELEASE_NAME)
        .namespace(opNamespace)
        .chartDir(OPERATOR_CHART_DIR);

    // build operator chart values
    OperatorParams opParams = new OperatorParams()
        .helmParams(upgradeHelmParams)
        .elkIntegrationEnabled(true)
        .elasticSearchHost(elasticSearchHost);

    assertTrue(upgradeAndVerifyOperator(opNamespace, opParams),
        String.format("Failed to upgrade operator in namespace %s", opNamespace));

    // modify fluentd configuration
    modifyModelConfigfile();

    // create and verify WebLogic domain image using model in image with model files
    String imageName = createAndVerifyDomainImage();

    // create and verify one cluster domain
    logger.info("Create domain and verify that it's running");
    createAndVerifyDomain(imageName);

    testVarMap = new HashMap<>();

    String elasticsearchUrlBuff =
        "curl http://" + elasticSearchHost + ":" + ELASTICSEARCH_HTTP_PORT;
    k8sExecCmdPrefix = elasticsearchUrlBuff;
    logger.info("Elasticsearch URL {0}", k8sExecCmdPrefix);

    // Verify that ELK Stack is ready to use
    testVarMap = verifyLoggingExporterReady(opNamespace, elasticSearchNs, null, FLUENTD_INDEX_KEY);
    testVarMap.putAll(verifyLoggingExporterReady(opNamespace, elasticSearchNs, null,
        INTROSPECTOR_INDEX_KEY));
    Map<String, String> kibanaMap = verifyLoggingExporterReady(opNamespace, elasticSearchNs, null,
        KIBANA_INDEX_KEY);

    // merge testVarMap and kibanaMap
    testVarMap.putAll(kibanaMap);
  }

  /**
   * Uninstall ELK Stack and delete domain custom resource.
   */
  @AfterAll
  void tearDown() {
    if (!SKIP_CLEANUP) {

      elasticsearchParams = new LoggingExporterParams()
          .elasticsearchName(ELASTICSEARCH_NAME)
          .elasticsearchImage(ELASTICSEARCH_IMAGE)
          .elasticsearchHttpPort(ELASTICSEARCH_HTTP_PORT)
          .elasticsearchHttpsPort(ELASTICSEARCH_HTTPS_PORT)
          .loggingExporterNamespace(elasticSearchNs);

      kibanaParams = new LoggingExporterParams()
          .kibanaName(KIBANA_NAME)
          .kibanaImage(KIBANA_IMAGE)
          .kibanaType(KIBANA_TYPE)
          .loggingExporterNamespace(elasticSearchNs)
          .kibanaContainerPort(KIBANA_PORT);

      // uninstall ELK Stack
      logger.info("Uninstall Elasticsearch pod");
      assertDoesNotThrow(() -> uninstallAndVerifyElasticsearch(elasticsearchParams),
          "uninstallAndVerifyElasticsearch failed with ApiException");

      logger.info("Uninstall Kibana pod");
      assertDoesNotThrow(() -> uninstallAndVerifyKibana(kibanaParams),
          "uninstallAndVerifyKibana failed with ApiException");
    }
  }

  /**
   * WebLogic domain is configured to use Fluentd to send log information to Elasticsearch
   * fluentd runs as a separate container in the Administration Server and Managed Server pods
   * fluentd tails the domain logs files and exports them to Elasticsearch
   * Query Elasticsearch repository for WebLogic log of serverName=adminServerPodName.
   * Verify that total number of logs for serverName=adminServerPodName is not zero
   * and failed if count is zero.
   */
  @Test
  @DisplayName("Use Fluentd to send log information to Elasticsearch and verify")
  void testFluentdQuery() {
    // verify Fluentd query results
    withStandardRetryPolicy.untilAsserted(
        () -> assertTrue(queryAndVerify(),
            String.format("Query logs of serverName=%s failed", adminServerPodName)));

    logger.info("Query logs of serverName={0} succeeded", adminServerPodName);
  }

  private boolean queryAndVerify() {
    //debug
    String queryCriteria0 = "/_search?q=serverName:" + adminServerPodName;
    String results0 = execSearchQuery(queryCriteria0, FLUENTD_INDEX_KEY);
    logger.info("_search?q=serverName:{0} result ===> {1}", adminServerPodName, results0);

    // Verify that number of logs is not zero and failed if count is zero
    String regex = ".*count\":(\\d+),.*failed\":(\\d+)";
    String queryCriteria = "/_count?q=serverName:" + adminServerPodName;
    int count = -1;
    int failedCount = -1;
    String results = execSearchQuery(queryCriteria, FLUENTD_INDEX_KEY);
    Pattern pattern = Pattern.compile(regex, Pattern.DOTALL | Pattern.MULTILINE);
    Matcher matcher = pattern.matcher(results);
    if (matcher.find()) {
      count = Integer.parseInt(matcher.group(1));
      failedCount = Integer.parseInt(matcher.group(2));
    }

    logger.info("Total count of logs: " + count);
    logger.info("Total failed count: " + failedCount);

    // search for introspector indexed entries
    String queryCriteria1 = "/_search?q=filesource:introspectDomain.sh";
    String results1 = execSearchQuery(queryCriteria1, INTROSPECTOR_INDEX_KEY);
    logger.info("/_search?q=filesource:introspectDomain.sh ===> {0}", results1);
    // as long as there is something returned
    boolean jobCompeted = results1.contains("introspectDomain.sh");
    logger.info("found completed job " + jobCompeted);

    return count > 0 && failedCount == 0 && jobCompeted;
  }

  private static String createAndVerifyDomainImage() {
    // create image with model files
    logger.info("Create image with model file and verify");
    String miiImage =
        createMiiImageAndVerify(WLS_ELK_LOGGING_IMAGE_NAME, WLS_ELK_LOGGING_MODEL_FILE, MII_BASIC_APP_NAME);

    // repo login and push image to registry if necessary
    imageRepoLoginAndPushImageToRegistry(miiImage);

    // create registry secret to pull the image from registry
    // this secret is used only for non-kind cluster
    logger.info("Create registry secret in namespace {0}", domainNamespace);
    createTestRepoSecret(domainNamespace);

    return miiImage;
  }

  private static void createAndVerifyDomain(String miiImage) {
    // create secret for admin credentials

    logger.info("Create secret for admin credentials");
    final String adminSecretName = "weblogic-credentials";
    assertDoesNotThrow(() -> createSecretWithUsernamePasswordElk(adminSecretName, domainNamespace,
        ADMIN_USERNAME_DEFAULT, ADMIN_PASSWORD_DEFAULT, elasticSearchHost, String.valueOf(ELASTICSEARCH_HTTP_PORT)),
        String.format("create secret for admin credentials failed for %s", adminSecretName));

    // create encryption secret
    logger.info("Create encryption secret");
    final String encryptionSecretName = "encryptionsecret";
    assertDoesNotThrow(() -> createSecretWithUsernamePasswordElk(encryptionSecretName, domainNamespace,
        "weblogicenc", "weblogicenc", elasticSearchHost, String.valueOf(ELASTICSEARCH_HTTP_PORT)),
        String.format("create encryption secret failed for %s", encryptionSecretName));

    // create domain and verify
    logger.info("Create model in image domain {0} in namespace {1} using image {2}",
        domainUid, domainNamespace, miiImage);
    createDomainCrAndVerify(adminSecretName, TEST_IMAGES_REPO_SECRET_NAME, encryptionSecretName, miiImage);

    // check that admin service exists in the domain namespace
    logger.info("Checking that admin service {0} exists in namespace {1}",
        adminServerPodName, domainNamespace);
    checkServiceExists(adminServerPodName, domainNamespace);

    // check that admin server pod is ready
    logger.info("Checking that admin server pod {0} is ready in namespace {1}",
        adminServerPodName, domainNamespace);
    checkPodReady(adminServerPodName, domainUid, domainNamespace);

    // check for managed server pods existence in the domain namespace
    for (int i = 1; i <= replicaCount; i++) {
      String managedServerPodName = managedServerPodPrefix + i;

      // check that the managed server service exists in the domain namespace
      logger.info("Checking that managed server service {0} exists in namespace {1}",
          managedServerPodName, domainNamespace);
      checkServiceExists(managedServerPodName, domainNamespace);

      // check that the managed server pod is ready
      logger.info("Checking that managed server pod {0} is ready in namespace {1}",
          managedServerPodName, domainNamespace);
      checkPodReady(managedServerPodName, domainUid, domainNamespace);
    }
  }

  private static void createDomainCrAndVerify(String adminSecretName,
                                              String repoSecretName,
                                              String encryptionSecretName,
                                              String miiImage) {
    final String volumeName = "weblogic-domain-storage-volume";
    final String logHomeRootPath = "/scratch";
    // create the domain CR
    logger.info("Choosen FLUENTD_IMAGE {0}", FLUENTD_IMAGE);
    String imagePullPolicy = "IfNotPresent";
    FluentdSpecification fluentdSpecification = new FluentdSpecification();
    fluentdSpecification.setImage(FLUENTD_IMAGE);
    fluentdSpecification.setWatchIntrospectorLogs(true);
    fluentdSpecification.setImagePullPolicy(imagePullPolicy);
    fluentdSpecification.setElasticSearchCredentials("weblogic-credentials");
    fluentdSpecification.setVolumeMounts(Arrays.asList(new V1VolumeMount()
        .name(volumeName)
        .mountPath(logHomeRootPath)));

    assertDoesNotThrow(() -> {
      Path filePath = Path.of(MODEL_DIR + "/" + FLUENTD_CONFIGMAP_YAML);
      fluentdSpecification.setFluentdConfiguration(Files.readString(filePath));
    });

    DomainResource domain = new DomainResource()
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
                .name(repoSecretName))
            .webLogicCredentialsSecret(new V1LocalObjectReference()
                .name(adminSecretName))
            .includeServerOutInPodLog(true)
            .serverStartPolicy("IfNeeded")
            .withFluentdConfiguration(fluentdSpecification)
            .serverPod(new ServerPod()
                .volumes(Arrays.asList(
                    new V1Volume()
                        .name(volumeName)
                        .emptyDir(new V1EmptyDirVolumeSource())))
                .volumeMounts(Arrays.asList(
                    new V1VolumeMount()
                        .name(volumeName)
                        .mountPath(logHomeRootPath)))
                .addEnvItem(new V1EnvVar()
                    .name("JAVA_OPTIONS")
                    .value("-Dweblogic.StdoutDebugEnabled=false"))
                .addEnvItem(new V1EnvVar()
                    .name("USER_MEM_ARGS")
                    .value("-Djava.security.egd=file:/dev/./urandom "))
            )
            .adminServer(new AdminServer()
                .adminService(new AdminService()
                    .addChannelsItem(new Channel()
                        .channelName("default")
                        .nodePort(getNextFreePort()))))
            .logHome("/scratch/logs/" + domainUid)
            .logHomeEnabled(true)
            .configuration(new Configuration()
                .model(new Model()
                    .domainType("WLS")
                    .runtimeEncryptionSecret(encryptionSecretName))
                .introspectorJobActiveDeadlineSeconds(300L)));

    // create cluster resource
    if (!Cluster.doesClusterExist(clusterName, CLUSTER_VERSION, domainNamespace)) {
      ClusterResource cluster =
          createClusterResource(domainUid + "-" + clusterName,
              clusterName, domainNamespace, replicaCount);
      createClusterAndVerify(cluster);
    }
    domain.getSpec().withCluster(new V1LocalObjectReference().name(domainUid + "-" + clusterName));

    setPodAntiAffinity(domain);
    // create domain using model in image
    logger.info("Create model in image domain {0} in namespace {1} using image {2}",
        domainUid, domainNamespace, miiImage);
    createDomainAndVerify(domain, domainNamespace);
  }

  private String execSearchQuery(String queryCriteria, String index) {
    String operatorPodName = assertDoesNotThrow(
        () -> getOperatorPodName(OPERATOR_RELEASE_NAME, opNamespace));
    assertTrue(operatorPodName != null && !operatorPodName.isEmpty(), "Failed to get Operator pad name");
    logger.info("Operator pod name " + operatorPodName);

    int waittime = 5;
    String indexName = testVarMap.get(index);
    StringBuilder curlOptions = new StringBuilder(" --connect-timeout " + waittime)
        .append(" --max-time ").append(waittime)
        .append(" -X GET ");
    StringBuilder k8sExecCmdPrefixBuff = new StringBuilder(k8sExecCmdPrefix);
    int offset = k8sExecCmdPrefixBuff.indexOf("http");
    k8sExecCmdPrefixBuff.insert(offset, curlOptions);
    String cmd = k8sExecCmdPrefixBuff
        .append("/")
        .append(indexName)
        .append(queryCriteria)
        .toString();
    logger.info("Exec command {0} in Operator pod {1}", cmd, operatorPodName);

    ExecResult execResult = assertDoesNotThrow(
        () -> execCommand(opNamespace, operatorPodName, null, true,
            "/bin/sh", "-c", cmd));
    assertNotNull(execResult, "curl command returns null");
    logger.info("Search query returns " + execResult.stdout());

    return execResult.stdout();
  }

  private static void modifyModelConfigfile() {
    final String sourceConfigFile = MODEL_DIR + "/model.wlslogging.yaml";

    assertDoesNotThrow(() -> copy(Paths.get(sourceConfigFile), Paths.get(WLS_ELK_LOGGING_MODEL_FILE)),
        "copy model.wlslogging.yaml failed");

    String[] deleteLineKeys
        = new String[]{"resources", "StartupClass", "LoggingExporterStartupClass", "ClassName", "Target"};
    try (RandomAccessFile file = new RandomAccessFile(WLS_ELK_LOGGING_MODEL_FILE, "rw")) {
      String lineToKeep = "";
      String allLines = "";
      boolean fountit = false;
      while ((lineToKeep = file.readLine()) != null) {
        for (String deleteLineKey : deleteLineKeys) {
          if (lineToKeep.startsWith(deleteLineKey)) {
            fountit = true;
            break;
          }
        }
        if (fountit) {
          continue;
        }
        allLines += lineToKeep + "\n";
      }

      try (BufferedWriter writer = new BufferedWriter(new FileWriter(WLS_ELK_LOGGING_MODEL_FILE))) {
        writer.write(allLines);
      } catch (Exception ex) {
        ex.printStackTrace();
      }
    } catch (Exception ex) {
      ex.printStackTrace();
    }
  }
}

// Copyright (c) 2020, 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.kubernetes.client.openapi.models.V1ConfigMapVolumeSource;
import io.kubernetes.client.openapi.models.V1Container;
import io.kubernetes.client.openapi.models.V1EmptyDirVolumeSource;
import io.kubernetes.client.openapi.models.V1EnvVar;
import io.kubernetes.client.openapi.models.V1EnvVarSource;
import io.kubernetes.client.openapi.models.V1LocalObjectReference;
import io.kubernetes.client.openapi.models.V1ObjectFieldSelector;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1ResourceRequirements;
import io.kubernetes.client.openapi.models.V1SecretKeySelector;
import io.kubernetes.client.openapi.models.V1SecretReference;
import io.kubernetes.client.openapi.models.V1Volume;
import io.kubernetes.client.openapi.models.V1VolumeMount;
import oracle.weblogic.domain.AdminServer;
import oracle.weblogic.domain.AdminService;
import oracle.weblogic.domain.Channel;
import oracle.weblogic.domain.Cluster;
import oracle.weblogic.domain.Configuration;
import oracle.weblogic.domain.Domain;
import oracle.weblogic.domain.DomainSpec;
import oracle.weblogic.domain.Model;
import oracle.weblogic.domain.ServerPod;
import oracle.weblogic.kubernetes.actions.impl.LoggingExporterParams;
import oracle.weblogic.kubernetes.actions.impl.primitive.Command;
import oracle.weblogic.kubernetes.actions.impl.primitive.CommandParams;
import oracle.weblogic.kubernetes.annotations.IntegrationTest;
import oracle.weblogic.kubernetes.annotations.Namespaces;
import oracle.weblogic.kubernetes.logging.LoggingFacade;
import oracle.weblogic.kubernetes.utils.ExecResult;
import org.awaitility.core.ConditionFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;
import static java.util.concurrent.TimeUnit.MINUTES;
import static java.util.concurrent.TimeUnit.SECONDS;
import static oracle.weblogic.kubernetes.TestConstants.DOMAIN_API_VERSION;
import static oracle.weblogic.kubernetes.TestConstants.ELASTICSEARCH_HOST;
import static oracle.weblogic.kubernetes.TestConstants.ELASTICSEARCH_HTTP_PORT;
import static oracle.weblogic.kubernetes.TestConstants.FLUENTD_IMAGE;
import static oracle.weblogic.kubernetes.TestConstants.FLUENTD_INDEX_KEY;
import static oracle.weblogic.kubernetes.TestConstants.KIBANA_INDEX_KEY;
import static oracle.weblogic.kubernetes.TestConstants.MII_BASIC_APP_NAME;
import static oracle.weblogic.kubernetes.TestConstants.OCIR_SECRET_NAME;
import static oracle.weblogic.kubernetes.TestConstants.OPERATOR_RELEASE_NAME;
import static oracle.weblogic.kubernetes.TestConstants.RESULTS_ROOT;
import static oracle.weblogic.kubernetes.actions.ActionConstants.MODEL_DIR;
import static oracle.weblogic.kubernetes.actions.TestActions.execCommand;
import static oracle.weblogic.kubernetes.actions.TestActions.getOperatorPodName;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.checkPodReady;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.checkServiceExists;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.createDomainAndVerify;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.createMiiImageAndVerify;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.createOcirRepoSecret;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.createSecretWithUsernamePasswordElk;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.dockerLoginAndPushImageToRegistry;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.installAndVerifyElasticsearch;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.installAndVerifyKibana;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.installAndVerifyOperator;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.setPodAntiAffinity;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.uninstallAndVerifyElasticsearch;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.uninstallAndVerifyKibana;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.verifyLoggingExporterReady;
import static oracle.weblogic.kubernetes.utils.FileUtils.replaceStringInFile;
import static oracle.weblogic.kubernetes.utils.ThreadSafeLogger.getLogger;
import static org.awaitility.Awaitility.with;
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
class ItElasticLoggingFluentd {

  // constants for creating domain image using model in image
  private static final String WLS_LOGGING_MODEL_FILE = "model.wlslogging.yaml";
  private static final String WLS_LOGGING_IMAGE_NAME = "wls-logging-image";

  private static final String FLUENTD_NAME = "fluentd";
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
  private static ConditionFactory withStandardRetryPolicy = null;

  private static LoggingExporterParams elasticsearchParams = null;
  private static LoggingExporterParams kibanaParams = null;
  private static LoggingFacade logger = null;

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
  public static void init(@Namespaces(2) List<String> namespaces) {
    logger = getLogger();
    // create standard, reusable retry/backoff policy
    withStandardRetryPolicy = with().pollDelay(2, SECONDS)
      .and().with().pollInterval(10, SECONDS)
      .atMost(5, MINUTES).await();

    // get a new unique opNamespace
    logger.info("Assigning a unique namespace for Operator");
    assertNotNull(namespaces.get(0), "Namespace list is null");
    opNamespace = namespaces.get(0);

    // get a new unique domainNamespace
    logger.info("Assigning a unique namespace for Domain");
    assertNotNull(namespaces.get(1), "Namespace list is null");
    domainNamespace = namespaces.get(1);

    // install and verify Elasticsearch
    logger.info("install and verify Elasticsearch");
    elasticsearchParams = assertDoesNotThrow(() -> installAndVerifyElasticsearch(),
            String.format("Failed to install Elasticsearch"));
    assertTrue(elasticsearchParams != null, "Failed to install Elasticsearch");

    // install and verify Kibana
    logger.info("install and verify Kibana");
    kibanaParams = assertDoesNotThrow(() -> installAndVerifyKibana(),
        String.format("Failed to install Kibana"));
    assertTrue(kibanaParams != null, "Failed to install Kibana");

    // install and verify Operator
    installAndVerifyOperator(opNamespace, opNamespace + "-sa",
        false, 0, true, domainNamespace);

    // create fluentd configuration
    configFluentd();

    // create and verify WebLogic domain image using model in image with model files
    String imageName = createAndVerifyDomainImage();

    // create and verify one cluster domain
    logger.info("Create domain and verify that it's running");
    createAndVerifyDomain(imageName);

    testVarMap = new HashMap<String, String>();

    StringBuffer elasticsearchUrlBuff =
        new StringBuffer("curl http://")
            .append(ELASTICSEARCH_HOST)
            .append(":")
            .append(ELASTICSEARCH_HTTP_PORT);
    k8sExecCmdPrefix = elasticsearchUrlBuff.toString();
    logger.info("Elasticsearch URL {0}", k8sExecCmdPrefix);

    // Verify that ELK Stack is ready to use
    testVarMap = verifyLoggingExporterReady(opNamespace, null, FLUENTD_INDEX_KEY);
    Map<String, String> kibanaMap = verifyLoggingExporterReady(opNamespace, null, KIBANA_INDEX_KEY);

    // merge testVarMap and kibanaMap
    testVarMap.putAll(kibanaMap);
  }

  /**
   * Uninstall ELK Stack and delete domain custom resource.
   */
  @AfterAll
  void tearDown() {
    if (System.getenv("SKIP_CLEANUP") == null
        || (System.getenv("SKIP_CLEANUP") != null
        && System.getenv("SKIP_CLEANUP").equalsIgnoreCase("false"))) {
      // uninstall ELK Stack
      if (elasticsearchParams != null) {
        logger.info("Uninstall Elasticsearch pod");
        assertDoesNotThrow(() -> uninstallAndVerifyElasticsearch(elasticsearchParams),
            "uninstallAndVerifyElasticsearch failed with ApiException");
      }

      if (kibanaParams != null) {
        logger.info("Uninstall Elasticsearch pod");
        assertDoesNotThrow(() -> uninstallAndVerifyKibana(kibanaParams),
            "uninstallAndVerifyKibana failed with ApiException");
      }
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
  public void testFluentdQuery() {
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
    assertTrue(count > 0, "Total count of logs should be more than 0!");
    assertTrue(failedCount == 0, "Total failed count should be 0!");
    logger.info("Total failed count: " + failedCount);

    logger.info("Query logs of serverName={0} succeeded", adminServerPodName);
  }

  private static void configFluentd() {
    Class thisClass = new Object(){}.getClass();
    String srcFluentdYamlFile =  MODEL_DIR + "/" + FLUENTD_CONFIGMAP_YAML;
    String destFluentdYamlFile =
        RESULTS_ROOT + "/" + thisClass.getClass().getSimpleName() + "/" + FLUENTD_CONFIGMAP_YAML;
    Path srcFluentdYamlPath = Paths.get(srcFluentdYamlFile);
    Path destFluentdYamlPath = Paths.get(destFluentdYamlFile);

    // create dest dir
    assertDoesNotThrow(() -> Files.createDirectories(
        Paths.get(RESULTS_ROOT + "/" + thisClass.getClass().getSimpleName())),
        String.format("Could not create directory under %s", RESULTS_ROOT
            + "/" + thisClass.getClass().getSimpleName()));

    // copy fluentd.configmap.elk.yaml to results dir
    assertDoesNotThrow(() -> Files.copy(srcFluentdYamlPath, destFluentdYamlPath, REPLACE_EXISTING),
        "Failed to copy fluentd.configmap.elk.yaml");

    // replace weblogic.domainUID, namespace in fluentd.configmap.elk.yaml
    assertDoesNotThrow(() -> replaceStringInFile(
        destFluentdYamlFile.toString(), "fluentd-domain", domainUid),
        "Could not modify weblogic.domainUID in fluentd.configmap.elk.yaml");;
    assertDoesNotThrow(() -> replaceStringInFile(
        destFluentdYamlFile.toString(), "fluentd-namespace", domainNamespace),
        "Could not modify namespace in fluentd.configmap.elk.yaml");

    // create fluentd configuration
    assertTrue(new Command()
        .withParams(new CommandParams()
            .command("kubectl create -f " + destFluentdYamlFile))
        .execute(), "kubectl create failed");
  }

  private static String createAndVerifyDomainImage() {
    // create image with model files
    logger.info("Create image with model file and verify");
    String miiImage =
        createMiiImageAndVerify(WLS_LOGGING_IMAGE_NAME, WLS_LOGGING_MODEL_FILE, MII_BASIC_APP_NAME);

    // docker login and push image to docker registry if necessary
    dockerLoginAndPushImageToRegistry(miiImage);

    // create docker registry secret to pull the image from registry
    // this secret is used only for non-kind cluster
    logger.info("Create docker registry secret in namespace {0}", domainNamespace);
    createOcirRepoSecret(domainNamespace);

    return miiImage;
  }

  private static void createAndVerifyDomain(String miiImage) {
    // create secret for admin credentials
    final String elasticSearchHost = "elasticsearch.default.svc.cluster.local";
    final String elasticSearchPort = "9200";

    logger.info("Create secret for admin credentials");
    final String adminSecretName = "weblogic-credentials";
    assertDoesNotThrow(() -> createSecretWithUsernamePasswordElk(adminSecretName, domainNamespace,
        "weblogic", "welcome1", elasticSearchHost, elasticSearchPort),
        String.format("create secret for admin credentials failed for %s", adminSecretName));

    // create encryption secret
    logger.info("Create encryption secret");
    final String encryptionSecretName = "encryptionsecret";
    assertDoesNotThrow(() -> createSecretWithUsernamePasswordElk(encryptionSecretName, domainNamespace,
        "weblogicenc", "weblogicenc", elasticSearchHost, elasticSearchPort),
        String.format("create encryption secret failed for %s", encryptionSecretName));

    // create domain and verify
    logger.info("Create model in image domain {0} in namespace {1} using docker image {2}",
        domainUid, domainNamespace, miiImage);
    createDomainCrAndVerify(adminSecretName, OCIR_SECRET_NAME, encryptionSecretName, miiImage);

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
    final String fluentdRootPath = "/scratch";
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
                .name(repoSecretName))
            .webLogicCredentialsSecret(new V1SecretReference()
                .name(adminSecretName)
                .namespace(domainNamespace))
            .includeServerOutInPodLog(true)
            .serverStartPolicy("IF_NEEDED")
            .serverPod(new ServerPod()
                .volumes(Arrays.asList(
                    new V1Volume()
                        .name(volumeName)
                        .emptyDir(new V1EmptyDirVolumeSource()),
                    new V1Volume()
                        .name("fluentd-config-volume")
                        .configMap(
                            new V1ConfigMapVolumeSource()
                                .defaultMode(420)
                                .name("fluentd-config"))))
                .volumeMounts(Arrays.asList(
                    new V1VolumeMount()
                        .name(volumeName)
                        .mountPath(fluentdRootPath)))
                .addEnvItem(new V1EnvVar()
                    .name("JAVA_OPTIONS")
                    .value("-Dweblogic.StdoutDebugEnabled=false"))
                .addEnvItem(new V1EnvVar()
                    .name("USER_MEM_ARGS")
                    .value("-Djava.security.egd=file:/dev/./urandom "))
                .containers(Arrays.asList(
                    new V1Container()
                        .addArgsItem("- -c")
                        .addArgsItem("- /etc/fluent.conf")
                        .addEnvItem(new V1EnvVar()
                            .name("DOMAIN_UID")
                            .valueFrom(new V1EnvVarSource()
                                .fieldRef(new V1ObjectFieldSelector()
                                    .fieldPath("metadata.labels['weblogic.domainUID']"))))
                        .addEnvItem(new V1EnvVar()
                            .name("SERVER_NAME")
                            .valueFrom(new V1EnvVarSource()
                                .fieldRef(new V1ObjectFieldSelector()
                                    .fieldPath("metadata.labels['weblogic.serverName']"))))
                        .addEnvItem(new V1EnvVar()
                            .name("LOG_PATH")
                            .value("/scratch/logs/" + domainUid + "/$(SERVER_NAME).log"))
                        .addEnvItem(new V1EnvVar()
                            .name("FLUENTD_CONF")
                            .value("fluentd.conf"))
                        .addEnvItem(new V1EnvVar()
                            .name("FLUENT_ELASTICSEARCH_SED_DISABLE")
                            .value("true"))
                        .addEnvItem(new V1EnvVar()
                            .name("ELASTICSEARCH_HOST")
                            .valueFrom(new V1EnvVarSource()
                                .secretKeyRef(new V1SecretKeySelector()
                                  .key("elasticsearchhost")
                                  .name("weblogic-credentials"))))
                        .addEnvItem(new V1EnvVar()
                            .name("ELASTICSEARCH_PORT")
                            .valueFrom(new V1EnvVarSource()
                                .secretKeyRef(new V1SecretKeySelector()
                                    .key("elasticsearchport")
                                    .name("weblogic-credentials"))))
                        .name(FLUENTD_NAME)
                        .image(FLUENTD_IMAGE)
                        .imagePullPolicy("IfNotPresent")
                        .resources(new V1ResourceRequirements())
                        .volumeMounts(Arrays.asList(
                            new V1VolumeMount()
                                .name("fluentd-config-volume")
                                .mountPath("/fluentd/etc/fluentd.conf")
                                .subPath("fluentd.conf"),
                            new V1VolumeMount()
                                .name("weblogic-domain-storage-volume")
                                .mountPath("/scratch"))))))
            .adminServer(new AdminServer()
                .serverStartState("RUNNING")
                    .adminService(new AdminService()
                        .addChannelsItem(new Channel()
                            .channelName("default")
                            .nodePort(0))))
            .addClustersItem(new Cluster()
                .clusterName(clusterName)
                .replicas(replicaCount)
                .serverStartState("RUNNING"))
            .logHome("/scratch/logs/" + domainUid)
            .logHomeEnabled(true)
            .configuration(new Configuration()
                .model(new Model()
                    .domainType("WLS")
                    .runtimeEncryptionSecret(encryptionSecretName))
                .introspectorJobActiveDeadlineSeconds(300L)));
    setPodAntiAffinity(domain);
    // create domain using model in image
    logger.info("Create model in image domain {0} in namespace {1} using docker image {2}",
        domainUid, domainNamespace, miiImage);
    createDomainAndVerify(domain, domainNamespace);
  }

  private String execSearchQuery(String queryCriteria, String index) {
    String operatorPodName = assertDoesNotThrow(
        () -> getOperatorPodName(OPERATOR_RELEASE_NAME, opNamespace));
    assertTrue(operatorPodName != null && !operatorPodName.isEmpty(), "Failed to get Operator pad name");
    logger.info("Operator pod name " + operatorPodName);

    int waittime = 5;
    String indexName = (String) testVarMap.get(index);
    StringBuffer curlOptions = new StringBuffer(" --connect-timeout " + waittime)
        .append(" --max-time " + waittime)
        .append(" -X GET ");
    StringBuffer k8sExecCmdPrefixBuff = new StringBuffer(k8sExecCmdPrefix);
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
}

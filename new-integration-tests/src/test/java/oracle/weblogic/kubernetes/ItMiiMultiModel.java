// Copyright (c) 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.kubernetes.client.openapi.models.V1ConfigMap;
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
import oracle.weblogic.kubernetes.actions.impl.primitive.Command;
import oracle.weblogic.kubernetes.actions.impl.primitive.CommandParams;
import oracle.weblogic.kubernetes.annotations.IntegrationTest;
import oracle.weblogic.kubernetes.annotations.Namespaces;
import oracle.weblogic.kubernetes.annotations.tags.Slow;
import oracle.weblogic.kubernetes.extensions.LoggedTest;
import org.awaitility.core.ConditionFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static java.util.concurrent.TimeUnit.MINUTES;
import static java.util.concurrent.TimeUnit.SECONDS;
import static oracle.weblogic.kubernetes.TestConstants.ADMIN_PASSWORD_DEFAULT;
import static oracle.weblogic.kubernetes.TestConstants.ADMIN_SERVER_NAME_BASE;
import static oracle.weblogic.kubernetes.TestConstants.ADMIN_USERNAME_DEFAULT;
import static oracle.weblogic.kubernetes.TestConstants.DOMAIN_API_VERSION;
import static oracle.weblogic.kubernetes.TestConstants.K8S_NODEPORT_HOST;
import static oracle.weblogic.kubernetes.TestConstants.MANAGED_SERVER_NAME_BASE;
import static oracle.weblogic.kubernetes.TestConstants.MII_BASIC_IMAGE_NAME;
import static oracle.weblogic.kubernetes.TestConstants.MII_BASIC_IMAGE_TAG;
import static oracle.weblogic.kubernetes.TestConstants.REPO_SECRET_NAME;
import static oracle.weblogic.kubernetes.TestConstants.WLS_DEFAULT_CHANNEL_NAME;
import static oracle.weblogic.kubernetes.actions.ActionConstants.MODEL_DIR;
import static oracle.weblogic.kubernetes.actions.TestActions.createConfigMap;
import static oracle.weblogic.kubernetes.actions.TestActions.getServiceNodePort;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.checkPodReady;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.checkServiceExists;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.createDockerRegistrySecret;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.createDomainAndVerify;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.createSecretWithUsernamePassword;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.installAndVerifyOperator;
import static org.awaitility.Awaitility.with;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Test to create model-in-image domain with a ConfigMap that contains multiple WDT models")
@IntegrationTest
class ItMiiMultiModel implements LoggedTest {

  private static String domainNamespace = null;
  private static ConditionFactory withStandardRetryPolicy = null;
  
  private static String domainUid = "mii-multimodel-cm-domain";

  private static String adminServerPodName = String.format("%s-%s", domainUid, ADMIN_SERVER_NAME_BASE);
  private static String managedServerPrefix = String.format("%s-%s", domainUid, MANAGED_SERVER_NAME_BASE);
  private static int replicaCount = 2;

  /**
   * Perform initialization for all the tests in this class.
   * Set up the necessary namespaces and install the operator in the first namespace.
   * @param namespaces list of namespaces created by the IntegrationTestWatcher by the
   *                   JUnit engine parameter resolution mechanism
   */
  @BeforeAll
  public static void initAll(@Namespaces(2) List<String> namespaces) {
    // create standard, reusable retry/backoff policy
    withStandardRetryPolicy = with().pollDelay(2, SECONDS)
        .and().with().pollInterval(10, SECONDS)
        .atMost(5, MINUTES).await();

    // get a new unique opNamespace
    logger.info("Creating unique namespace for Operator");
    assertNotNull(namespaces.get(0), "Namespace list is null");
    String opNamespace = namespaces.get(0);

    logger.info("Creating unique namespace for Domain");
    assertNotNull(namespaces.get(1), "Namespace list is null");
    domainNamespace = namespaces.get(1);

    // install the operator
    logger.info("Install an operator in namespace {0}, managing namespace {1}",
        opNamespace, domainNamespace);
    installAndVerifyOperator(opNamespace, domainNamespace);
  }

  /**
   * Test that two WDT model files in a domain resource's ConfigMap are applied in the expected order.
   * Create a WebLogic domain with a Kubernetes ConfigMap that contains two WDT model files.
   * Verify that the effective configuration of the domain is as expected.
   * Please refer to docs-source/content/userguide/managing-domains/model-in-image/model-files.md
   * for more details about the ordering rules.
   */
  @Test
  @DisplayName("Create model-in-image domain with a ConfigMap that contains multiple model files")
  @Slow
  public void testCreateMiiDomainWithMultiModeCM() {

    // These two model files define the same DatatSource "TestDataSource". The only difference
    // is the connection pool's MaxCapacity setting, which are 30 and 40 respectively.
    // According to the ordering rules, when both of the files are in the domain's ConfigMap,
    // the model files will be passed to WebLogic Deploy Tooling in the order of 
    // "multi-model-two-ds.10.yaml" and "multi-model-one-ds.20.yaml". As a result, the effective
    // value of MaxCapacity should be 40 for "TestDataSource".
    // In addition, the first model defines a second DataSource "TestDataSource3", which should
    // also be in the resultant configuration as it is in the first model.
    final String modelFileName1 = "multi-model-two-ds.10.yaml";
    final String modelFileName2 = "multi-model-one-ds.20.yaml";

    final String dsName = "TestDataSource";
    final String dsName3 = "TestDataSource3";

    final String configMapName = "ds-multi-model-cm";

    logger.info("Create the repo secret {0} to pull the image", REPO_SECRET_NAME);
    assertDoesNotThrow(() -> createDockerRegistrySecret(domainNamespace),
            String.format("createSecret failed for %s", REPO_SECRET_NAME));

    // create secret for admin credentials
    logger.info("Create secret for admin credentials");
    String adminSecretName = "weblogic-credentials";
    assertDoesNotThrow(() -> createSecretWithUsernamePassword(
        adminSecretName,
        domainNamespace,
        ADMIN_USERNAME_DEFAULT,
        ADMIN_PASSWORD_DEFAULT),
        String.format("createSecret failed for %s", adminSecretName));

    // create encryption secret
    logger.info("Create encryption secret");
    String encryptionSecretName = "encryptionsecret";
    assertDoesNotThrow(() -> createSecretWithUsernamePassword(
        encryptionSecretName,
        domainNamespace,
        "weblogicenc",
        "weblogicenc"),
        String.format("createSecret failed for %s", encryptionSecretName));

    logger.info("Create ConfigMap {0} that contains model files {1} and {2}",
        configMapName, modelFileName1, modelFileName2);

    Map<String, String> data = new HashMap<>();
    addModelFile(data, modelFileName1);
    addModelFile(data, modelFileName2);

    createConfigMapAndVerify(configMapName, domainUid, domainNamespace, data);

    logger.info("Create the domain resource {0} in namespace {1} with ConfigMap {2}",
        domainUid, domainNamespace, configMapName);
    Domain domain = createDomainResource(domainUid, domainNamespace, adminSecretName,
        REPO_SECRET_NAME, encryptionSecretName, replicaCount, configMapName);
    
    createDomainAndVerify(domain, domainNamespace);

    // check admin server pod is ready
    logger.info("Wait for admin server pod {0} to be ready in namespace {1}",
        adminServerPodName, domainNamespace);
    checkPodReady(adminServerPodName, domainUid, domainNamespace);

    logger.info("Check admin service {0} is created in namespace {1}",
        adminServerPodName, domainNamespace);
    checkServiceExists(adminServerPodName, domainNamespace);

    // check managed server pods are ready
    for (int i = 1; i <= replicaCount; i++) {
      logger.info("Wait for managed server pod {0} to be ready in namespace {1}",
          managedServerPrefix + i, domainNamespace);
      checkPodReady(managedServerPrefix + i, domainUid, domainNamespace);
    }

    // check managed server services created
    for (int i = 1; i <= replicaCount; i++) {
      logger.info("Check managed server service {0} is created in namespace {1}",
          managedServerPrefix + i, domainNamespace);
      checkServiceExists(managedServerPrefix + i, domainNamespace);
    }

    logger.info("Check the MaxCapacity setting of DataSource {0}", dsName);
    String maxCapacityValue = getDSMaxCapacity(adminServerPodName, domainNamespace, dsName);
    assertEquals("40", maxCapacityValue, 
        String.format("Domain %s in namespace %s DataSource %s MaxCapacity is %s, instead of %s",
            domainUid, domainNamespace, dsName, maxCapacityValue, "40"));

    logger.info(String.format("Domain %s in namespace %s DataSource %s MaxCapacity is %s, as expected",
            domainUid, domainNamespace, dsName, "40"));

    logger.info("Check the MaxCapacity setting of DataSource {0}", dsName3);
    maxCapacityValue = getDSMaxCapacity(adminServerPodName, domainNamespace, dsName3);
    assertEquals("5", maxCapacityValue, 
        String.format("Domain %s in namespace %s DataSource %s MaxCapacity is %s, instead of %s",
            domainUid, domainNamespace, dsName3, maxCapacityValue, "5"));

    logger.info(String.format("Domain %s in namespace %s DataSource %s MaxCapacity is %s, as expected",
            domainUid, domainNamespace, dsName3, "5"));
  }

  /**
   * Construct a domain object with the given parameters that can be used to create a domain resource.
   */
  private Domain createDomainResource(
      String domainUid, String domNamespace, String adminSecretName,
      String repoSecretName, String encryptionSecretName, 
      int replicaCount, String configMapName) {
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
                    .image(MII_BASIC_IMAGE_NAME + ":" + MII_BASIC_IMAGE_TAG)
                    .addImagePullSecretsItem(new V1LocalObjectReference()
                            .name(repoSecretName))
                    .webLogicCredentialsSecret(new V1SecretReference()
                            .name(adminSecretName)
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
                                    .configMap(configMapName)
                                    .runtimeEncryptionSecret(encryptionSecretName))
                        .introspectorJobActiveDeadlineSeconds(300L)));

  }

  /**
   * Read the content of a model file as a String and add it to a map.
   */
  private void addModelFile(Map data, String modelFileName) {
    logger.info("Add model file {0}", modelFileName);
    String dsModelFile = String.format("%s/%s", MODEL_DIR, modelFileName);

    String cmData = assertDoesNotThrow(() -> Files.readString(Paths.get(dsModelFile)),
        String.format("Failed to read model file %s", dsModelFile));
    assertNotNull(cmData, 
        String.format("Failed to read model file %s", dsModelFile));

    data.put(modelFileName, cmData);
  }

  /**
   * Get DataSource's connection pool MaxCapacity setting.
   */
  private String getDSMaxCapacity(
      String adminServerPodName,
      String namespace,
      String dsName) {
    int adminServiceNodePort = getServiceNodePort(
        namespace, adminServerPodName + "-external", WLS_DEFAULT_CHANNEL_NAME);

    String command = new StringBuffer()
        .append("curl --user weblogic:welcome1 ")
        .append("http://" + K8S_NODEPORT_HOST + ":" + adminServiceNodePort)
        .append("/management/wls/latest/datasources/id/" + dsName)
        .append(" --silent --show-error ")
        .append("| grep maxCapacity | tr -d -c 0-9 ").toString();

    CommandParams params = Command
        .defaultCommandParams()
        .command(command)
        .saveResults(true)
        .redirect(true);

    Command.withParams(params).execute();
    return params.stdout();
  }
  
  /**
   * Create a Kubernetes ConfigMap with the given parameters and verify that the operation succeeds.
   */
  private void createConfigMapAndVerify(
      String configMapName,
      String domainUid,
      String namespace,
      Map<String, String> data) {
    Map<String, String> labels = new HashMap<>();
    labels.put("weblogic.domainUid", domainUid);

    V1ObjectMeta meta = new V1ObjectMeta()
        .labels(labels)
        .name(configMapName)
        .namespace(domainNamespace);
    V1ConfigMap configMap = new V1ConfigMap()
        .data(data)
        .metadata(meta);

    assertTrue(assertDoesNotThrow(() -> createConfigMap(configMap),
        String.format("Create ConfigMap %s failed due to Kubernetes client  ApiException", configMapName)),
        String.format("Failed to create ConfigMap %s", configMapName));
  }
}

// Copyright (c) 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
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
import org.junit.jupiter.api.AfterAll;
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
import static oracle.weblogic.kubernetes.TestConstants.MII_BASIC_APP_NAME;
import static oracle.weblogic.kubernetes.TestConstants.MII_BASIC_IMAGE_NAME;
import static oracle.weblogic.kubernetes.TestConstants.MII_BASIC_IMAGE_TAG;
import static oracle.weblogic.kubernetes.TestConstants.MII_BASIC_WDT_MODEL_FILE;
import static oracle.weblogic.kubernetes.TestConstants.REPO_SECRET_NAME;
import static oracle.weblogic.kubernetes.TestConstants.WLS_DEFAULT_CHANNEL_NAME;
import static oracle.weblogic.kubernetes.actions.ActionConstants.MODEL_DIR;
import static oracle.weblogic.kubernetes.actions.TestActions.createConfigMap;
import static oracle.weblogic.kubernetes.actions.TestActions.deleteImage;
import static oracle.weblogic.kubernetes.actions.TestActions.getServiceNodePort;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.checkPodReady;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.checkServiceExists;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.createDockerRegistrySecret;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.createDomainAndVerify;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.createMiiImageAndVerify;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.createSecretWithUsernamePassword;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.dockerLoginAndPushImageToRegistry;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.installAndVerifyOperator;
import static org.awaitility.Awaitility.with;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Test to create model-in-image domain with a ConfigMap that contains multiple WDT models")
@IntegrationTest
class ItMiiMultiModel implements LoggedTest {

  private static String domainNamespace = null;
  private static ConditionFactory withStandardRetryPolicy = null;

  private static String adminSecretName = null;
  private static String encryptionSecretName = null;
  private static String miiImageMultiModel = null;

  private static int replicaCount = 2;

  // There are four model files in this test case. 
  // "multi-model-two-ds.yaml" and "multi-model-delete-one-ds.20.yaml" are in a MII image.
  // "multi-model-two-ds.10.yaml" and "multi-model-two-ds.10.yaml" are in the domain's ConfigMap.

  // Define two DS: "TestDataSource" and "TestDataSource2". Their MaxCapacity are 10 and 15 respectively
  private static final String modelFileName1 = "multi-model-two-ds.yaml";

  // Delete "TestDataSource2" and define "TestDataSource" with MaxCapacity of 20
  private static final String modelFileName2 = "multi-model-delete-one-ds.20.yaml";

  // Define two DS: "TestDataSource" and "TestDataSource3". Their MaxCapacity are 30 and 5 respectively
  private static final String modelFileName3 = "multi-model-two-ds.10.yaml";

  // Define one DS "TestDataSource" with MaxCapacity of 40
  private static final String modelFileName4 = "multi-model-one-ds.20.yaml";

  private static final String dsName = "TestDataSource";
  private static final String dsName2 = "TestDataSource2";
  private static final String dsName3 = "TestDataSource3";

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

    logger.info("Create the repo secret {0} to pull the image", REPO_SECRET_NAME);
    assertDoesNotThrow(() -> createDockerRegistrySecret(domainNamespace),
            String.format("createSecret failed for %s", REPO_SECRET_NAME));

    // create secret for admin credentials
    logger.info("Create secret for admin credentials");
    adminSecretName = "weblogic-credentials";
    assertDoesNotThrow(() -> createSecretWithUsernamePassword(
        adminSecretName,
        domainNamespace,
        ADMIN_USERNAME_DEFAULT,
        ADMIN_PASSWORD_DEFAULT),
        String.format("createSecret failed for %s", adminSecretName));

    // create encryption secret
    logger.info("Create encryption secret");
    encryptionSecretName = "encryptionsecret";
    assertDoesNotThrow(() -> createSecretWithUsernamePassword(
        encryptionSecretName,
        domainNamespace,
        "weblogicenc",
        "weblogicenc"),
        String.format("createSecret failed for %s", encryptionSecretName));

    logger.info("Create an image with two model files");
    miiImageMultiModel = createMiiImageAndVerify(
        String.format("%s-%s", MII_BASIC_IMAGE_NAME, "test-multi-model-image"),
        Arrays.asList(
            MODEL_DIR + "/" + MII_BASIC_WDT_MODEL_FILE, 
            MODEL_DIR + "/" + modelFileName1, 
            MODEL_DIR + "/" + modelFileName2),
        Collections.singletonList(MII_BASIC_APP_NAME));

    // push the image to a registry to make it accessible in multi node cluster
    dockerLoginAndPushImageToRegistry(miiImageMultiModel);

  }

  /**
   * Test that two WDT model files in a domain resource's ConfigMap are applied in the expected order.
   * Create a WebLogic domain with a Kubernetes ConfigMap that contains two WDT model files.
   * Verify that the effective configuration of the domain is as expected.
   */
  //@Test
  @DisplayName("Create model-in-image domain with a ConfigMap that contains multiple model files")
  @Slow
  public void testMiiWithMultiModeCM() {
    final String domainUid = "mii-mm-cm-domain";
    final String adminServerPodName = String.format("%s-%s", domainUid, ADMIN_SERVER_NAME_BASE);
    final String managedServerPrefix = String.format("%s-%s", domainUid, MANAGED_SERVER_NAME_BASE);
    final String expectedMaxCapacity = "40";
    final String expectedMaxCapacityDS3 = "5";

    // Use two model files modelFileName3 and modelFileName4, which define the same DS "TestDataSource".
    // The only difference is that the connection pool's MaxCapacity setting is 30 and 40 respectively.
    // According to the ordering rules, the effective value of MaxCapacity should be 40 when both
    // model files are in the domain configuration model's ConfigMap.
    // In addition, the first model defines a second DataSource "TestDataSource3", which should
    // also be in the resultant configuration.

    final String configMapName = "ds-multi-model-cm";

    logger.info("Create domain {0} in namespace {1} with CM {2} that contains WDT models {3} and {4}",
        domainUid, domainNamespace, configMapName, modelFileName3, modelFileName4);
    createDomainResourceAndVerify(
        domainUid, domainNamespace, adminServerPodName,
        managedServerPrefix, MII_BASIC_IMAGE_NAME + ":" + MII_BASIC_IMAGE_TAG, configMapName);

    logger.info("Check the MaxCapacity setting of DataSource {0}", dsName);
    String maxCapacityValue = getDSMaxCapacity(adminServerPodName, domainNamespace, dsName);
    assertEquals(expectedMaxCapacity, maxCapacityValue, 
        String.format("Domain %s in namespace %s DataSource %s MaxCapacity is %s, instead of %s",
            domainUid, domainNamespace, dsName, maxCapacityValue, expectedMaxCapacity));

    logger.info(String.format("Domain %s in namespace %s DataSource %s MaxCapacity is %s, as expected",
            domainUid, domainNamespace, dsName, expectedMaxCapacity));

    logger.info("Check the MaxCapacity setting of DataSource {0}", dsName3);
    maxCapacityValue = getDSMaxCapacity(adminServerPodName, domainNamespace, dsName3);
    assertEquals(expectedMaxCapacityDS3, maxCapacityValue, 
        String.format("Domain %s in namespace %s DataSource %s MaxCapacity is %s, instead of %s",
            domainUid, domainNamespace, dsName3, maxCapacityValue, expectedMaxCapacityDS3));

    logger.info(String.format("Domain %s in namespace %s DataSource %s MaxCapacity is %s, as expected",
            domainUid, domainNamespace, dsName3, expectedMaxCapacityDS3));

  }

  @Test
  @DisplayName("Create a domain with two model files in the image")
  @Slow
  public void testMiiWithMultiModelInImage() {
    final String domainUid = "mii-mm-image-domain";
    final String adminServerPodName = String.format("%s-%s", domainUid, ADMIN_SERVER_NAME_BASE);
    final String managedServerPrefix = String.format("%s-%s", domainUid, MANAGED_SERVER_NAME_BASE);
    final String expectedMaxCapacity = "20";

    logger.info("Create domain {0} in namespace {1} with image {2} that contains WDT models {3} and {4}",
        domainUid, domainNamespace, miiImageMultiModel, modelFileName1, modelFileName2);
    createDomainResourceAndVerify(
        domainUid, domainNamespace, adminServerPodName,
        managedServerPrefix, miiImageMultiModel, null);
    // managedServerPrefix, MII_BASIC_IMAGE_NAME + ":" + MII_BASIC_IMAGE_TAG, null);

    logger.info("Check the MaxCapacity setting of DataSource {0}", dsName);
    String maxCapacityValue = getDSMaxCapacity(adminServerPodName, domainNamespace, dsName);
    assertEquals(expectedMaxCapacity, maxCapacityValue, 
        String.format("Domain %s in namespace %s DataSource %s MaxCapacity is %s, instead of %s",
            domainUid, domainNamespace, dsName, maxCapacityValue, expectedMaxCapacity));

    logger.info(String.format("Domain %s in namespace %s DataSource %s MaxCapacity is %s, as expected",
            domainUid, domainNamespace, dsName, expectedMaxCapacity));

    logger.info("Check DataSource {0} does not exist", dsName2);
    assertFalse(doesDSExist(adminServerPodName, domainNamespace, dsName2),
        String.format("Domain %s in namespace %s DataSource %s should not exist",
            domainUid, domainNamespace, dsName2));

    logger.info(String.format("Domain %s in namespace %s DataSource %s does not exist as expected",
            domainUid, domainNamespace, dsName2));

  }

  @Test
  @DisplayName("Create a domain with two model files in the image and two models in CM")
  @Slow
  public void testMiiWithMultiModelInImageAndCM() {
    final String domainUid = "mii-mm-image-cm-domain";
    final String adminServerPodName = String.format("%s-%s", domainUid, ADMIN_SERVER_NAME_BASE);
    final String managedServerPrefix = String.format("%s-%s", domainUid, MANAGED_SERVER_NAME_BASE);
    final String configMapName = "ds-multi-model-image-cm";
    final String expectedMaxCapacity = "40";
    final String expectedMaxCapacityDS3 = "5";

    createDomainResourceAndVerify(
        domainUid, domainNamespace, adminServerPodName,
        managedServerPrefix, miiImageMultiModel, configMapName);

    logger.info("Check the MaxCapacity setting of DataSource {0}", dsName);
    String maxCapacityValue = getDSMaxCapacity(adminServerPodName, domainNamespace, dsName);
    assertEquals(expectedMaxCapacity, maxCapacityValue, 
        String.format("Domain %s in namespace %s DataSource %s MaxCapacity is %s, instead of %s",
            domainUid, domainNamespace, dsName, maxCapacityValue, expectedMaxCapacity));

    logger.info(String.format("Domain %s in namespace %s DataSource %s MaxCapacity is %s, as expected",
            domainUid, domainNamespace, dsName, expectedMaxCapacity));

    logger.info("Check the MaxCapacity setting of DataSource {0}", dsName3);
    maxCapacityValue = getDSMaxCapacity(adminServerPodName, domainNamespace, dsName3);
    assertEquals(expectedMaxCapacityDS3, maxCapacityValue, 
        String.format("Domain %s in namespace %s DataSource %s MaxCapacity is %s, instead of %s",
            domainUid, domainNamespace, dsName3, maxCapacityValue, expectedMaxCapacityDS3));

    logger.info(String.format("Domain %s in namespace %s DataSource %s MaxCapacity is %s, as expected",
            domainUid, domainNamespace, dsName3, expectedMaxCapacityDS3));

    logger.info("Check DataSource {0} does not exist", dsName2);
    assertFalse(doesDSExist(adminServerPodName, domainNamespace, dsName2),
        String.format("Domain %s in namespace %s DataSource %s should not exist",
            domainUid, domainNamespace, dsName2));

    logger.info(String.format("Domain %s in namespace %s DataSource %s does not exist as expected",
            domainUid, domainNamespace, dsName2));

  }

  private void createDomainResourceAndVerify(
      String domainUid,
      String domainNamespace,
      String adminServerPodName,
      String managedServerPrefix,
      String miiImage,
      String configMapName) {

    if (configMapName != null) {
      logger.info("Create ConfigMap {0} that contains model files {1} and {2}",
          configMapName, modelFileName3, modelFileName4);

      Map<String, String> data = new HashMap<>();
      addModelFile(data, modelFileName3);
      addModelFile(data, modelFileName4);

      createConfigMapAndVerify(configMapName, domainUid, domainNamespace, data);
    } 

    logger.info("Create the domain resource {0} in namespace {1} with ConfigMap {2}",
        domainUid, domainNamespace, configMapName);
    Domain domain = createDomainResource(domainUid, domainNamespace, adminSecretName,
        REPO_SECRET_NAME, encryptionSecretName, replicaCount, miiImage, configMapName);
    
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
  }

  @AfterAll
  public void tearDownAll() {
    // delete the domain images created in the test class
    if (miiImageMultiModel != null) {
      deleteImage(miiImageMultiModel);
    }
  }

  /**
   * Construct a domain object with the given parameters that can be used to create a domain resource.
   */
  private Domain createDomainResource(
      String domainUid, String domNamespace, String adminSecretName,
      String repoSecretName, String encryptionSecretName, 
      int replicaCount, String miiImage, String configMapName) {
    // create the domain CR
    Domain domain = new Domain()
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
                                    .runtimeEncryptionSecret(encryptionSecretName))
                        .introspectorJobActiveDeadlineSeconds(300L)));
    if (configMapName != null) {
      domain.spec().configuration().model().configMap(configMapName);
    }
    return domain;
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
   * Check if a DataSource exists.
   */
  private static boolean doesDSExist(
      String adminServerPodName,
      String namespace,
      String dsName) {
    int adminServiceNodePort = getServiceNodePort(
        namespace, adminServerPodName + "-external", WLS_DEFAULT_CHANNEL_NAME);

    String command = new StringBuffer()
        .append("curl --user weblogic:welcome1 ")
        .append("http://" + K8S_NODEPORT_HOST + ":" + adminServiceNodePort)
        .append("/management/wls/latest/datasources")
        .append(" --silent --show-error ")
        .append("| grep " + dsName).toString();

    CommandParams params = Command
        .defaultCommandParams()
        .command(command)
        .saveResults(true)
        .redirect(true);

    assertTrue(Command.withParams(params).execute(),
        String.format("Failed to check DataSource %s's existence", dsName));

    return params.stdout() != null && params.stdout().length() != 0;
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

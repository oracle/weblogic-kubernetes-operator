// Copyright (c) 2020, 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

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
import oracle.weblogic.kubernetes.logging.LoggingFacade;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

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
import static oracle.weblogic.kubernetes.TestConstants.OCIR_SECRET_NAME;
import static oracle.weblogic.kubernetes.TestConstants.WLS_DEFAULT_CHANNEL_NAME;
import static oracle.weblogic.kubernetes.actions.ActionConstants.MODEL_DIR;
import static oracle.weblogic.kubernetes.actions.TestActions.deleteDomainCustomResource;
import static oracle.weblogic.kubernetes.actions.TestActions.deleteImage;
import static oracle.weblogic.kubernetes.actions.TestActions.getServiceNodePort;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.checkPodReady;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.checkServiceExists;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.createConfigMapAndVerify;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.createDomainAndVerify;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.createMiiImageAndVerify;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.createOcirRepoSecret;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.createSecretWithUsernamePassword;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.dockerLoginAndPushImageToRegistry;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.getExternalServicePodName;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.installAndVerifyOperator;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.setPodAntiAffinity;
import static oracle.weblogic.kubernetes.utils.ThreadSafeLogger.getLogger;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Test multiple WDT model files in a model-in-image domain are processed in the expected order.
 * 
 * <p>There are three test methods in this class, covering three basic scenarios. </p>
 *
 * <ul>
 *   <li> testMiiWithMultiModelImage: test multiple WDT model files in the Docker image. </li>
 *   <li> testMiiWithMultiModelCM: test multiple WDT model files in the domain resource's ConfigMap. </li>
 *   <li> testMiiWithMultipleModelImageAndCM: test multiple WDT files in both of the Docker image
 *        and the domain resource's ConfigMap. </li>
 * </ul>
 */
@DisplayName("Test to create model-in-image domain with multiple WDT models")
@IntegrationTest
class ItMiiMultiModel {

  private static String domainNamespace = null;
  private static String adminSecretName = null;
  private static String encryptionSecretName = null;
  private static String miiImageMultiModel = null;

  private static final String domainUid1 = "mii-mm-cm-domain";
  private static final String domainUid2 = "mii-mm-image-domain";
  private static final String domainUid3 = "mii-mm-image-cm-domain";

  private static int replicaCount = 2;

  // There are four model files in this test case. 
  // "multi-model-two-ds.yaml" and "multi-model-delete-one-ds.20.yaml" are in the MII image.
  // "multi-model-two-ds.10.yaml" and "multi-model-two-ds.10.yaml" are in the domain's ConfigMap.

  // Define "TestDataSource" and "TestDataSource2" with MaxCapacity set to 10 and 15 respectively
  private static final String modelFileName1 = "multi-model-two-ds.yaml";

  // Delete "TestDataSource2" and define "TestDataSource" with MaxCapacity set to 20
  private static final String modelFileName2 = "multi-model-delete-one-ds.20.yaml";

  // Define "TestDataSource" and "TestDataSource3" with MaxCapacity set to 30 and 5 respectively
  private static final String modelFileName3 = "multi-model-two-ds.10.yaml";

  // Define "TestDataSource" with MaxCapacity set to 40
  private static final String modelFileName4 = "multi-model-one-ds.20.yaml";

  private static final String dsName = "TestDataSource";
  private static final String dsName2 = "TestDataSource2";
  private static final String dsName3 = "TestDataSource3";

  private static LoggingFacade logger = null;

  /**
   * Perform initialization for all the tests in this class.
   *
   * <p>Set up the necessary namespaces and install the operator in the first namespace, and
   * create a model-in-image image with two WDT model files. </p>
   *
   * @param namespaces list of namespaces created by the IntegrationTestWatcher by the
   *                   JUnit engine parameter resolution mechanism
   */
  @BeforeAll
  public static void initAll(@Namespaces(2) List<String> namespaces) {
    logger = getLogger();
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

    // this secret is used only for non-kind cluster
    logger.info("Create the repo secret {0} to pull the image", OCIR_SECRET_NAME);
    createOcirRepoSecret(domainNamespace);

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
        "mii-test-multi-model-image",
        Arrays.asList(
            MODEL_DIR + "/" + MII_BASIC_WDT_MODEL_FILE, 
            MODEL_DIR + "/" + modelFileName2, 
            MODEL_DIR + "/" + modelFileName1),
        Collections.singletonList(MII_BASIC_APP_NAME));

    // push the image to a registry to make it accessible in multi-node cluster
    dockerLoginAndPushImageToRegistry(miiImageMultiModel);

  }

  /**
   * Test that two WDT model files in a domain resource's ConfigMap are applied in the expected order.
   *
   * <p>Create a WebLogic domain with a Kubernetes ConfigMap that contains two WDT model files.
   * Verify that the effective configuration of the domain is as expected. </p>
   *
   * <p>The two model files specify the same DataSource "TestDataSource" with the connection pool's
   * MaxCapacity set to 30 and 40 respectively. In addition, the first model file also 
   * specifies a second DataSource "TestDataSource3" with the maxCapacity set to 5. </p>
   *
   * <p>According to the ordering rules, the resultant configuration should have two DataSources,
   * "TestDataSource" and "TestDataSource3", with the MaxCapacity set to 40 and 5 respectively. </p>
   */
  @Test
  @DisplayName("Create model-in-image domain with a ConfigMap that contains multiple model files")
  public void testMiiWithMultiModelCM() {
    final String adminServerPodName = String.format("%s-%s", domainUid1, ADMIN_SERVER_NAME_BASE);
    final String managedServerPrefix = String.format("%s-%s", domainUid1, MANAGED_SERVER_NAME_BASE);
    final String expectedMaxCapacity = "40";
    final String expectedMaxCapacityDS3 = "5";

    final String configMapName = "ds-multi-model-cm";

    logger.info("Create ConfigMap {0} in namespace {1} with WDT models {3} and {4}",
        configMapName, domainNamespace, modelFileName3, modelFileName4);

    List<String> modelFiles = Arrays.asList(MODEL_DIR + "/" + modelFileName3, MODEL_DIR + "/" + modelFileName4);
    createConfigMapAndVerify(
        configMapName, domainUid1, domainNamespace, modelFiles);

    logger.info("Create domain {0} in namespace {1} with CM {2} that contains WDT models {3} and {4}",
        domainUid1, domainNamespace, configMapName, modelFileName3, modelFileName4);

    createDomainResourceAndVerify(
        domainUid1,
        domainNamespace,
        adminServerPodName,
        managedServerPrefix, 
        MII_BASIC_IMAGE_NAME + ":" + MII_BASIC_IMAGE_TAG,
        configMapName);

    logger.info("Check the MaxCapacity setting of DataSource {0}", dsName);
    String maxCapacityValue = getDSMaxCapacity(adminServerPodName, domainNamespace, dsName);
    assertEquals(expectedMaxCapacity, maxCapacityValue, 
        String.format("Domain %s in namespace %s DataSource %s MaxCapacity is %s, instead of %s",
            domainUid1, domainNamespace, dsName, maxCapacityValue, expectedMaxCapacity));

    logger.info(String.format("Domain %s in namespace %s DataSource %s MaxCapacity is %s, as expected",
            domainUid1, domainNamespace, dsName, expectedMaxCapacity));

    logger.info("Check the MaxCapacity setting of DataSource {0}", dsName3);
    maxCapacityValue = getDSMaxCapacity(adminServerPodName, domainNamespace, dsName3);
    assertEquals(expectedMaxCapacityDS3, maxCapacityValue, 
        String.format("Domain %s in namespace %s DataSource %s MaxCapacity is %s, instead of %s",
            domainUid1, domainNamespace, dsName3, maxCapacityValue, expectedMaxCapacityDS3));

    logger.info(String.format("Domain %s in namespace %s DataSource %s MaxCapacity is %s, as expected",
            domainUid1, domainNamespace, dsName3, expectedMaxCapacityDS3));

  }

  /**
   * Test that two WDT model files in a model-in-image Docker image are applied in the expected order.
   *
   * <p>Create a WebLogic domain using a model-in-image Docker image that contains two WDT model files.
   * Verify that the effective configuration of the domain is as expected. </p>
   *
   * <p>The two model files specify the same DataSource "TestDataSource" with the connection pool's
   * MaxCapacity set to 15 and 20 respectively. In addition, the first model defines a second 
   * DataSource "TestDataSource2", which is deleted by the second model. </p>
   *
   * <p>According to the ordering rules, When the two model files are applied, the resultant domain should
   * only have "TestDataSource" with MaxCapacity set to 20, and "TestDataSource2" should not exist. </p>
   */
  @Test
  @DisplayName("Create a model-in-image domain with two WDT model files in the image")
  public void testMiiWithMultiModelImage() {
    final String adminServerPodName = String.format("%s-%s", domainUid2, ADMIN_SERVER_NAME_BASE);
    final String managedServerPrefix = String.format("%s-%s", domainUid2, MANAGED_SERVER_NAME_BASE);
    final String expectedMaxCapacity = "20";

    logger.info("Create domain {0} in namespace {1} with image {2} that contains WDT models {3} and {4}",
        domainUid2, domainNamespace, miiImageMultiModel, modelFileName2, modelFileName1);
    createDomainResourceAndVerify(
        domainUid2,
        domainNamespace,
        adminServerPodName,
        managedServerPrefix, 
        miiImageMultiModel,
        null);

    logger.info("Check the MaxCapacity setting of DataSource {0}", dsName);
    String maxCapacityValue = getDSMaxCapacity(adminServerPodName, domainNamespace, dsName);
    assertEquals(expectedMaxCapacity, maxCapacityValue, 
        String.format("Domain %s in namespace %s DataSource %s MaxCapacity is %s, instead of %s",
            domainUid2, domainNamespace, dsName, maxCapacityValue, expectedMaxCapacity));

    logger.info(String.format("Domain %s in namespace %s DataSource %s MaxCapacity is %s, as expected",
            domainUid2, domainNamespace, dsName, expectedMaxCapacity));

    logger.info("Check DataSource {0} does not exist", dsName2);
    assertTrue(dsDoesNotExist(adminServerPodName, domainNamespace, dsName2),
        String.format("Domain %s in namespace %s DataSource %s should not exist",
            domainUid2, domainNamespace, dsName2));

    logger.info(String.format("Domain %s in namespace %s DataSource %s does not exist as expected",
            domainUid2, domainNamespace, dsName2));

  }

  /**
   * Test that two WDT model files in a model-in-image image and two WDT model files in the domain
   * resource's ConfigMap are handled as expected.
   *
   * <p>Create a WebLogic domain using a model-in-image Docker image that contains two WDT model files,
   * and also using a ConfigMap that contains two more model files.
   * Verify that the effective configuration of the domain is as expected. Note that the model files
   * in the image are ordered independently from the model files in the domain's ConfigMap. </p>
   *
   * <p>The two model files in the Docker image define the same DataSource "TestDataSource" with 
   * the connection pool's MaxCapacity set to 15 and 20 respectively.
   * In addition, the first model defines a second DataSource "TestDataSource2", which is deleted by
   * the second model. When the two model files are applied, the resultant domain will only have
   * "TestDataSource" with MaxCapacity set to 20, and "TestDataSource2" should not exist. </p>
   *
   * <p>Then the two model files in the ConfigMap will be applied.
   * They define the same DataSource "TestDataSource" with MaxCapaqcity set to 30 and 40 respectively,
   * and, in addition, the first model defines another DataSource "TestDataSource3" with MaxCapacity
   * set to 5. </p>
   *
   * <p>According to the ordering rules, the effective domain should contain "TestDataSource" with 
   * MaxCapacity set to 40, "TestDataSource3" with MaxCapacity set to "5", and "TestDataSource2"
   * should not exist after all four model files are processed by the WebLogic Deploy Tooling. </p>
   */
  @Test
  @DisplayName("Create a model-in-image domain with two model files in both the image and the ConfigMap")
  public void testMiiWithMultiModelImageAndCM() {
    final String adminServerPodName = String.format("%s-%s", domainUid3, ADMIN_SERVER_NAME_BASE);
    final String managedServerPrefix = String.format("%s-%s", domainUid3, MANAGED_SERVER_NAME_BASE);
    final String configMapName = "ds-multi-model-image-cm";
    final String expectedMaxCapacity = "40";
    final String expectedMaxCapacityDS3 = "5";

    logger.info("Create ConfigMap {0} in namespace {1} with WDT models {2} and {3}",
        configMapName, domainNamespace, modelFileName4, modelFileName3);

    List<String> modelFiles = Arrays.asList(MODEL_DIR + "/" + modelFileName4, MODEL_DIR + "/" + modelFileName3);
    createConfigMapAndVerify(
        configMapName, domainUid3, domainNamespace, modelFiles);

    logger.info("Create domain {0} in namespace {1} with image {2} and CM {3} that contains {4} and {5}",
        domainUid3, domainNamespace, miiImageMultiModel, configMapName, modelFileName4, modelFileName3);

    createDomainResourceAndVerify(
        domainUid3,
        domainNamespace,
        adminServerPodName,
        managedServerPrefix, 
        miiImageMultiModel,
        configMapName);

    logger.info("Check the MaxCapacity setting of DataSource {0}", dsName);
    String maxCapacityValue = getDSMaxCapacity(adminServerPodName, domainNamespace, dsName);
    assertEquals(expectedMaxCapacity, maxCapacityValue, 
        String.format("Domain %s in namespace %s DataSource %s MaxCapacity is %s, instead of %s",
            domainUid3, domainNamespace, dsName, maxCapacityValue, expectedMaxCapacity));

    logger.info(String.format("Domain %s in namespace %s DataSource %s MaxCapacity is %s, as expected",
            domainUid3, domainNamespace, dsName, expectedMaxCapacity));

    logger.info("Check the MaxCapacity setting of DataSource {0}", dsName3);
    maxCapacityValue = getDSMaxCapacity(adminServerPodName, domainNamespace, dsName3);
    assertEquals(expectedMaxCapacityDS3, maxCapacityValue, 
        String.format("Domain %s in namespace %s DataSource %s MaxCapacity is %s, instead of %s",
            domainUid3, domainNamespace, dsName3, maxCapacityValue, expectedMaxCapacityDS3));

    logger.info(String.format("Domain %s in namespace %s DataSource %s MaxCapacity is %s, as expected",
            domainUid3, domainNamespace, dsName3, expectedMaxCapacityDS3));

    logger.info("Check that DataSource {0} does not exist", dsName2);
    assertTrue(dsDoesNotExist(adminServerPodName, domainNamespace, dsName2),
        String.format("Domain %s in namespace %s DataSource %s should not exist",
            domainUid3, domainNamespace, dsName2));

    logger.info(String.format("Domain %s in namespace %s DataSource %s does not exist as expected",
            domainUid3, domainNamespace, dsName2));

  }

  /**
   * Create a domain object with the given image, ConfigMap and other parameters, and verify that
   * the server pods and services are successfully started.
   */
  private void createDomainResourceAndVerify(
      String domainUid,
      String domainNamespace,
      String adminServerPodName,
      String managedServerPrefix,
      String miiImage,
      String configMapName) {

    logger.info("Create the domain resource {0} in namespace {1} with ConfigMap {2}",
        domainUid, domainNamespace, configMapName);
    Domain domain = createDomainResource(domainUid, domainNamespace, adminSecretName,
        OCIR_SECRET_NAME, encryptionSecretName, replicaCount, miiImage, configMapName);
    
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
    // Delete domain custom resources
    logger.info("Delete domain custom resource {0} in namespace {1}", domainUid1, domainNamespace);
    assertDoesNotThrow(() -> deleteDomainCustomResource(domainUid1, domainNamespace),
        "deleteDomainCustomResource failed with ApiException");
    logger.info("Deleted Domain Custom Resource " + domainUid1 + " from " + domainNamespace);

    logger.info("Delete domain custom resource {0} in namespace {1}", domainUid2, domainNamespace);
    assertDoesNotThrow(() -> deleteDomainCustomResource(domainUid2, domainNamespace),
        "deleteDomainCustomResource failed with ApiException");
    logger.info("Deleted Domain Custom Resource " + domainUid2 + " from " + domainNamespace);

    logger.info("Delete domain custom resource {0} in namespace {1}", domainUid3, domainNamespace);
    assertDoesNotThrow(() -> deleteDomainCustomResource(domainUid3, domainNamespace),
        "deleteDomainCustomResource failed with ApiException");
    logger.info("Deleted Domain Custom Resource " + domainUid3 + " from " + domainNamespace);

    // delete the domain image created in the test class
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
    setPodAntiAffinity(domain);

    return domain;
  }

  /**
   * Get a DataSource's connection pool MaxCapacity setting.
   */
  private String getDSMaxCapacity(
      String adminServerPodName,
      String namespace,
      String dsName) {
    int adminServiceNodePort = getServiceNodePort(
        namespace, getExternalServicePodName(adminServerPodName), WLS_DEFAULT_CHANNEL_NAME);

    String command = new StringBuffer()
        .append("curl --user " + ADMIN_USERNAME_DEFAULT + ":" + ADMIN_PASSWORD_DEFAULT)
        .append(" http://" + K8S_NODEPORT_HOST + ":" + adminServiceNodePort)
        .append("/management/wls/latest/datasources/id/" + dsName)
        .append(" --noproxy '*'")
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
   * Check if a DataSource does not exist.
   */
  private static boolean dsDoesNotExist(
      String adminServerPodName,
      String namespace,
      String dsName) {
    int adminServiceNodePort = getServiceNodePort(
        namespace, getExternalServicePodName(adminServerPodName), WLS_DEFAULT_CHANNEL_NAME);

    String command = new StringBuffer()
        .append("curl --user " + ADMIN_USERNAME_DEFAULT + ":" + ADMIN_PASSWORD_DEFAULT)
        .append(" http://" + K8S_NODEPORT_HOST + ":" + adminServiceNodePort)
        .append("/management/wls/latest/datasources")
        .append("/id/" + dsName)
        .append(" --noproxy '*'")
        .append(" --silent --show-error ").toString();

    CommandParams params = Command
        .defaultCommandParams()
        .command(command)
        .saveResults(true)
        .redirect(true);

    String expectedStr = String.format("'%s' was not found", dsName);
    
    return Command.withParams(params).executeAndVerify(expectedStr);
  }
  
}

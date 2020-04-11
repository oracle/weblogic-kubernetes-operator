// Copyright (c) 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.models.V1EnvVar;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1Secret;
import io.kubernetes.client.openapi.models.V1SecretReference;
import io.kubernetes.client.openapi.models.V1ServiceAccount;
import oracle.weblogic.domain.AdminServer;
import oracle.weblogic.domain.AdminService;
import oracle.weblogic.domain.Channel;
import oracle.weblogic.domain.Cluster;
import oracle.weblogic.domain.Configuration;
import oracle.weblogic.domain.Domain;
import oracle.weblogic.domain.DomainSpec;
import oracle.weblogic.domain.Model;
import oracle.weblogic.domain.ServerPod;
import oracle.weblogic.kubernetes.actions.impl.OperatorParams;
import oracle.weblogic.kubernetes.actions.impl.primitive.HelmParams;
import oracle.weblogic.kubernetes.annotations.tags.MustNotRunInParallel;
import oracle.weblogic.kubernetes.annotations.tags.Slow;
import oracle.weblogic.kubernetes.extensions.LoggedTest;
import oracle.weblogic.kubernetes.extensions.Timing;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.extension.ExtendWith;

import static java.util.concurrent.TimeUnit.MINUTES;
import static java.util.concurrent.TimeUnit.SECONDS;
import static oracle.weblogic.kubernetes.actions.ActionConstants.MODEL_DIR;
import static oracle.weblogic.kubernetes.actions.ActionConstants.WIT_BUILD_DIR;
import static oracle.weblogic.kubernetes.actions.TestActions.createDomainCustomResource;
import static oracle.weblogic.kubernetes.actions.TestActions.createMIIImage;
import static oracle.weblogic.kubernetes.actions.TestActions.createSecret;
import static oracle.weblogic.kubernetes.actions.TestActions.createServiceAccount;
import static oracle.weblogic.kubernetes.actions.TestActions.createUniqueNamespace;
//import static oracle.weblogic.kubernetes.actions.TestActions.deleteDomainCustomResource;
import static oracle.weblogic.kubernetes.actions.TestActions.deleteNamespace;
import static oracle.weblogic.kubernetes.actions.TestActions.deleteServiceAccount;
import static oracle.weblogic.kubernetes.actions.TestActions.helmList;
import static oracle.weblogic.kubernetes.actions.TestActions.installOperator;
import static oracle.weblogic.kubernetes.actions.TestActions.uninstallOperator;
import static oracle.weblogic.kubernetes.actions.TestActions.withWITParams;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.dockerImageExists;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.domainExists;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.operatorIsRunning;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.podExists;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.podReady;
import static oracle.weblogic.kubernetes.utils.FileUtils.checkDirectory;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.awaitility.Awaitility.with;
import static org.junit.jupiter.api.Assertions.assertEquals;

// Test to install Operator, create model in image domain and verify the domain
// has started successfully
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("Test to create model in image domain and start the domain")
@ExtendWith(Timing.class)
class ItMiiDomain implements LoggedTest {

  // operator constants
  private static final String OPERATOR_RELEASE_NAME = "weblogic-operator";
  private static final String OPERATOR_CHART_DIR =
      "../kubernetes/charts/weblogic-operator";
  private static final String OPERATOR_IMAGE =
      "weblogic-kubernetes-operator:latest";
  //"phx.ocir.io/weblogick8s/weblogic-kubernetes-operator:develop";

  // mii constants
  private static final String WDT_MODEL_FILE = "model1-wls.yaml";
  private static final String MII_IMAGE_NAME_PREFIX = "mii-image-";
  private static final String MII_IMAGE_TAG = "v1";

  // domain constants
  private static final String DOMAIN_VERSION = "v7";
  private static final String API_VERSION = "weblogic.oracle/" + DOMAIN_VERSION;
  private static final String DOMAIN_HOME_SOURCE_TYPE = "FromModel";

  private static HelmParams opHelmParams = null;
  private static V1ServiceAccount serviceAccount = null;
  private static String opNamespace = null;
  private static String domainNamespace = null;
  private String domainUID = "domain1";

  /**
   * Install Operator.
   */
  @BeforeAll
  public static void initAll() {
    // get a new unique opNamespace
    opNamespace = createNamespace();
    logger.info(String.format("Created a new namespace called %s", opNamespace));

    domainNamespace = createNamespace();
    logger.info(String.format("Created a new namespace called %s", domainNamespace));

    // Create a service account for the unique opNamespace
    String serviceAccountName = createSA(opNamespace);
    logger.info("Created service account: " + serviceAccountName);

    // helm install parameters
    opHelmParams = new HelmParams()
        .releaseName(OPERATOR_RELEASE_NAME)
        .namespace(opNamespace)
        .chartDir(OPERATOR_CHART_DIR);

    // Operator chart values to override
    OperatorParams opParams =
        new OperatorParams()
            .helmParams(opHelmParams)
            .image(OPERATOR_IMAGE)
            .domainNamespaces(Arrays.asList(domainNamespace))
            .serviceAccount(serviceAccountName);

    // install Operator
    assertThat(installOperator(opParams))
        .as("Test installOperator returns true")
        .withFailMessage("installOperator() did not return true")
        .isTrue();
    logger.info(String.format("Operator installed in namespace %s", opNamespace));

    // list helm releases
    assertThat(helmList(opHelmParams))
        .as("Test helmList returns true")
        .withFailMessage("helmList() did not return true")
        .isTrue();

    // check operator is running
    checkOperatorRunning(opNamespace);

  }

  private static String createSA(String namespace) {
    final String serviceAccountName = namespace + "-sa";
    serviceAccount = new V1ServiceAccount()
        .metadata(
            new V1ObjectMeta()
                .namespace(namespace)
                .name(serviceAccountName));

    assertThatCode(
        () -> createServiceAccount(serviceAccount))
        .as("Test that createServiceAccount doesn not throw an exception")
        .withFailMessage("createServiceAccount() threw an exception")
        .doesNotThrowAnyException();
    return serviceAccountName;
  }

  private static String createNamespace() {
    String namespace = null;
    try {
      namespace = createUniqueNamespace();
    } catch (Exception e) {
      logger.log(Level.INFO, "createUniqueNamespace failed with ", e);
      assertThat(e)
          .as("Test that createUniqueNamespace does not throw an exception")
          .withFailMessage("createUniqueNamespace() threw an unexpected exception")
          .isNotInstanceOf(ApiException.class);
    }
    return namespace;
  }

  private static void checkOperatorRunning(String namespace) {
    // check if the operator is running.
    with().pollDelay(30, SECONDS)
        // we check again every 10 seconds.
        .and().with().pollInterval(10, SECONDS)
        // this listener lets us report some status with each poll
        .conditionEvaluationListener(
            condition -> logger.info(()
                -> String.format("Waiting for operator to be running (elapsed time %dms, remaining time %dms)",
                condition.getElapsedTimeInMS(),
                condition.getRemainingTimeInMS())))
        // and here we can set the maximum time we are prepared to wait
        .await().atMost(5, MINUTES)
        // operatorIsRunning() is one of our custom, reusable assertions
        .until(operatorIsRunning(opNamespace));
  }

  @BeforeEach
  public void init() {
    // create unique namespace for domain and do operator upgrade?
  }

  @Test
  @Order(1)
  @DisplayName("Create model in image domain")
  @Slow
  @MustNotRunInParallel
  public void testCreateMiiDomain() {

    // create image with model files
    final String miiImage = createImage();

    // To Do: push the image to OCIR

    // create secret for admin credentials
    Map<String, String> secretMap = new HashMap();
    secretMap.put("username", "weblogic");
    secretMap.put("password", "welcome1");

    assertThatCode(
        () -> createSecret(new V1Secret()
                  .metadata(new V1ObjectMeta()
                    .name("weblogic-credentials")
                    .namespace(domainNamespace))
                  .stringData(secretMap)))
        .as("Test createSecret returns true")
        .withFailMessage("Create secret failed while creating secret for admin credentials")
        .doesNotThrowAnyException();

    // encryption secret
    Map<String, String> encryptionSecretMap = new HashMap();
    encryptionSecretMap.put("username", "weblogicenc");
    encryptionSecretMap.put("password", "welcome1enc");

    assertThatCode(
        () -> createSecret(new V1Secret()
            .metadata(new V1ObjectMeta()
                .name("encryptionsecret")
                .namespace(domainNamespace))
            .stringData(encryptionSecretMap)))
        .as("Test createSecret returns true")
        .withFailMessage("Create secret failed while creating encryption secret")
        .doesNotThrowAnyException();

    // create the domain CR
    Domain domain = new Domain()
        .apiVersion(API_VERSION)
        .kind("Domain")
        .metadata(new V1ObjectMeta()
            .name(domainUID)
            .namespace(domainNamespace))
        .spec(new DomainSpec()
            .domainUid(domainUID)
            .domainHomeSourceType(DOMAIN_HOME_SOURCE_TYPE)
            .image(miiImage)
            .webLogicCredentialsSecret(new V1SecretReference()
                .name("weblogic-credentials")
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
                        .nodePort(30711))))
            .addClustersItem(new Cluster()
                .clusterName("cluster-1")
                .replicas(2)
                .serverStartState("RUNNING"))
            .configuration(new Configuration()
                .model(new Model()
                .domainType("WLS")
                .runtimeEncryptionSecret("encryptionsecret"))));

    assertThatCode(
        () -> createDomainCustomResource(domain))
        .as("Test createDomainCustomResource returns true")
        .withFailMessage("Create domain custom resource failed")
        .doesNotThrowAnyException();

    // wait for the domain to exist
    with().pollDelay(30, SECONDS)
        .and().with().pollInterval(10, SECONDS)
        .conditionEvaluationListener(
            condition -> logger.info(() ->
                String.format(
                    "Waiting for domain to be running (elapsed time %dms, remaining time %dms)",
                    condition.getElapsedTimeInMS(),
                    condition.getRemainingTimeInMS())))
        // and here we can set the maximum time we are prepared to wait
        .await().atMost(5, MINUTES)
        .until(domainExists(domainUID, DOMAIN_VERSION, domainNamespace));

    // check admin server pod exist, admin server name copied from model.yaml
    checkPodCreated(domainUID + "-admin-server");

    // check managed server pods exists, managed server prefix copied from model.yaml
    for (int i = 1; i <= 2; i++) {
      checkPodCreated(domainUID + "-managed-server" + i);
    }

    // check admin server pod is running
    checkPodRunning(domainUID + "-admin-server");

    // check managed server pods are running
    for (int i = 1; i <= 2; i++) {
      checkPodRunning(domainUID + "-managed-server" + i);
    }


  }

  @AfterEach
  public void tearDown() {

    // Delete domain custom resource
    /* assertThatCode(
        () -> deleteDomainCustomResource(domainUID, domainNamespace))
        .as("Test that deleteDomainCustomResource doesn not throw an exception")
        .withFailMessage("delete domain custom resource failed")
        .doesNotThrowAnyException();

    logger.info("Deleted Domain Custom Resource " + domainUID + " from " + domainNamespace);
    */
    // delete domain resources and namespace - just delete domain namespace?
  }

  /**
   * Uninstall Operator, delete service account, domain namespace and
   * operator namespace.
   */
  @AfterAll
  public void tearDownAll() {
    // uninstall operator release
    if (opHelmParams != null) {
      assertThat(uninstallOperator(opHelmParams))
          .as("Test uninstallOperator returns true")
          .withFailMessage("uninstallOperator() did not return true")
          .isTrue();
    }

    // Delete service account from unique opNamespace
    if (serviceAccount != null) {
      assertThatCode(
          () -> deleteServiceAccount(serviceAccount.getMetadata().getName(),
              serviceAccount.getMetadata().getNamespace()))
          .as("Test that deleteServiceAccount doesn not throw an exception")
          .withFailMessage("deleteServiceAccount() threw an exception")
          .doesNotThrowAnyException();
    }
    // Delete domain namespaces
    /* if (domainNamespace != null) {
      assertThatCode(
          () -> deleteNamespace(domainNamespace))
          .as("Test that deleteNamespace doesn not throw an exception")
          .withFailMessage("deleteNamespace() threw an exception")
          .doesNotThrowAnyException();
      logger.info("Deleted namespace: " + domainNamespace);
    } */

    // Delete opNamespace
    if (opNamespace != null) {
      assertThatCode(
          () -> deleteNamespace(opNamespace))
          .as("Test that deleteNamespace doesn not throw an exception")
          .withFailMessage("deleteNamespace() threw an exception")
          .doesNotThrowAnyException();
      logger.info("Deleted namespace: " + opNamespace);
    }

  }

  private String createImage() {
    // create unique image name with date
    DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
    Date date = new Date();
    String currentDateTime = dateFormat.format(date) + "-" + System.currentTimeMillis();
    final String imageName = MII_IMAGE_NAME_PREFIX + currentDateTime;

    logger.info("WDT model directory is " + MODEL_DIR);

    // build the model file list
    List<String> modelList = Collections.singletonList(MODEL_DIR + "/" + WDT_MODEL_FILE);

    // Set additional environment variables for WIT
    checkDirectory(WIT_BUILD_DIR);
    Map<String, String> env = new HashMap();
    env.put("WLSIMG_BLDDIR", WIT_BUILD_DIR);

    // build an image using WebLogic Image Tool
    boolean success = createMIIImage(
        withWITParams()
            .modelImageName(imageName)
            .modelImageTag(MII_IMAGE_TAG)
            .modelFiles(modelList)
            .wdtVersion("latest")
            .env(env)
            .redirect(true));


    assertEquals(true, success, "Failed to create the image using WebLogic Deploy Tool");

    // check image exists
    assertThat(dockerImageExists(imageName, MII_IMAGE_TAG))
        .as("Check dockerImageExists() returns true")
        .withFailMessage(String.format("Image %s doesn't exist", imageName + ":" + MII_IMAGE_TAG))
        .isTrue();


    return imageName + ":" + MII_IMAGE_TAG;
  }

  private void checkPodCreated(String podName) {

    try {
      with().pollDelay(30, SECONDS)
            .and().with().pollInterval(10, SECONDS)
            .conditionEvaluationListener(
                condition -> logger.info(() ->
                    String.format(
                        "Waiting for pod %s to be created in namespace %s (elapsed time %dms, remaining time %dms)",
                        podName,
                        domainNamespace,
                        condition.getElapsedTimeInMS(),
                        condition.getRemainingTimeInMS())))
            // and here we can set the maximum time we are prepared to wait
            .await().atMost(5, MINUTES)
            .until(podExists(podName, domainUID, domainNamespace));
    } catch (Exception e) {
      logger.log(Level.INFO, "podExists failed with ", e);
      assertThat(e)
          .as("Test that podExists does not throw an exception")
          .withFailMessage(String.format(
              "pod %s doesn't exist in namespace %s", podName, domainNamespace))
          .isNotInstanceOf(ApiException.class);
    }

  }

  private void checkPodRunning(String podName) {

    assertThatCode(
        () -> with().pollDelay(30, SECONDS)
            .and().with().pollInterval(10, SECONDS)
            .conditionEvaluationListener(
                condition -> logger.info(() ->
                    String.format(
                        "Waiting for pod %s to be running in namespace %s (elapsed time %dms, remaining time %dms)",
                        podName,
                        domainNamespace,
                        condition.getElapsedTimeInMS(),
                        condition.getRemainingTimeInMS())))
            // and here we can set the maximum time we are prepared to wait
            .await().atMost(5, MINUTES)
            .until(podReady(podName, domainUID, domainNamespace)))
        .as("Test podReady returns true")
        .withFailMessage(String.format("Pod %s is not ready in namespace %s", podName, domainNamespace))
        .doesNotThrowAnyException();
  }


}
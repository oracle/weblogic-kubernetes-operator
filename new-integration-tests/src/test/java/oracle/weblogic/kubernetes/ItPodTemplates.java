// Copyright (c) 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import io.kubernetes.client.openapi.models.V1EnvVar;
import io.kubernetes.client.openapi.models.V1LocalObjectReference;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1Pod;
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
import oracle.weblogic.kubernetes.actions.impl.primitive.Kubernetes;
import oracle.weblogic.kubernetes.annotations.IntegrationTest;
import oracle.weblogic.kubernetes.annotations.Namespaces;
import oracle.weblogic.kubernetes.logging.LoggingFacade;
import org.awaitility.core.ConditionFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static java.util.concurrent.TimeUnit.MINUTES;
import static java.util.concurrent.TimeUnit.SECONDS;
import static oracle.weblogic.kubernetes.TestConstants.ADMIN_PASSWORD_DEFAULT;
import static oracle.weblogic.kubernetes.TestConstants.ADMIN_USERNAME_DEFAULT;
import static oracle.weblogic.kubernetes.TestConstants.DOMAIN_API_VERSION;
import static oracle.weblogic.kubernetes.TestConstants.K8S_NODEPORT_HOST;
import static oracle.weblogic.kubernetes.TestConstants.MANAGED_SERVER_NAME_BASE;
import static oracle.weblogic.kubernetes.TestConstants.MII_BASIC_IMAGE_NAME;
import static oracle.weblogic.kubernetes.TestConstants.MII_BASIC_IMAGE_TAG;
import static oracle.weblogic.kubernetes.TestConstants.REPO_SECRET_NAME;
import static oracle.weblogic.kubernetes.TestConstants.WDT_IMAGE_DOMAINHOME_BASE_DIR;
import static oracle.weblogic.kubernetes.actions.ActionConstants.ITTESTS_DIR;
import static oracle.weblogic.kubernetes.actions.ActionConstants.RESOURCE_DIR;
import static oracle.weblogic.kubernetes.actions.ActionConstants.WLS;
import static oracle.weblogic.kubernetes.actions.ActionConstants.WLS_BASE_IMAGE_NAME;
import static oracle.weblogic.kubernetes.actions.ActionConstants.WLS_BASE_IMAGE_TAG;
import static oracle.weblogic.kubernetes.actions.TestActions.deleteImage;
import static oracle.weblogic.kubernetes.actions.TestActions.shutdownDomain;
import static oracle.weblogic.kubernetes.actions.impl.primitive.Kubernetes.deleteDomainCustomResource;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.checkPodExists;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.checkPodReady;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.checkServiceExists;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.createDockerRegistrySecret;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.createDomainAndVerify;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.createImageAndVerify;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.createSecretWithUsernamePassword;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.dockerLoginAndPushImageToRegistry;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.installAndVerifyOperator;
import static oracle.weblogic.kubernetes.utils.TestUtils.getNextFreePort;
import static oracle.weblogic.kubernetes.utils.ThreadSafeLogger.getLogger;
import static org.awaitility.Awaitility.with;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;



/**
 * This test is used for creating Operator(s) and domain which uses pod templates.
 */
@DisplayName("Test to verify domain pod templates.")
@IntegrationTest
class ItPodTemplates {


  // domain constants
  private static final int replicaCount = 1;
  private static String domain1Namespace = null;
  private static String domain1Uid = "itpodtemplates-domain-1";
  private static String domain2Namespace = null;
  private static String domain2Uid = "itpodtemplates-domain-2";
  private static ConditionFactory withStandardRetryPolicy = null;
  // constants for creating domain image using model in image
  private static final String PODTEMPLATES_IMAGE_NAME = "podtemplates-image";

  private static String clusterName = "cluster-1";
  private static String miiImage = null;
  private static String wdtImage = null;
  private static LoggingFacade logger = null;

  /**
   * Install operator and NGINX. Create model in image domain with multiple clusters.
   * Create ingress for the domain.
   *
   * @param namespaces list of namespaces created by the IntegrationTestWatcher by the
   *                   JUnit engine parameter resolution mechanism
   */
  @BeforeAll

  public static void initAll(@Namespaces(3) List<String> namespaces) {

    logger = getLogger();
    // create standard, reusable retry/backoff policy
    withStandardRetryPolicy = with().pollDelay(2, SECONDS)
        .and().with().pollInterval(10, SECONDS)
        .atMost(5, MINUTES).await();

    logger.info("Get a unique namespace for operator");
    assertNotNull(namespaces.get(0), "Namespace list is null");
    final String opNamespace = namespaces.get(0);

    logger.info("Get a unique namespace for WebLogic domain1");
    assertNotNull(namespaces.get(1), "Namespace list is null");
    domain1Namespace = namespaces.get(1);

    logger.info("Get a unique namespace for WebLogic domain2");
    assertNotNull(namespaces.get(2), "Namespace list is null");
    domain2Namespace = namespaces.get(2);

    logger.info("install and verify operator");
    installAndVerifyOperator(opNamespace, domain1Namespace,domain2Namespace);

    logger.info("create and verify WebLogic domain image using model in image with model files");
    miiImage = MII_BASIC_IMAGE_NAME + ":" + MII_BASIC_IMAGE_TAG;
  }

  /**
   * Test pod templates using all the variables $(SERVER_NAME), $(DOMAIN_NAME), $(DOMAIN_UID),
   * $(DOMAIN_HOME), $(LOG_HOME) and $(CLUSTER_NAME) in serverPod for Domain In Image. Make sure the domain comes up
   * successfully.
   *
   * @throws Exception when the domain crd creation fails or when updating the serverPod with
   *                   variables
   */
  @Test
  @DisplayName("Test pod templates using all the variables.")
  public void testPodTemplateUsingVariablesDomainInImage() throws Exception {
    wdtImage = createAndVerifyDomainInImage();
    try {
      logger.info("Create wdt domain and verify that it's running");
      createAndVerifyDomain(wdtImage, domain2Uid, domain2Namespace, "Image", replicaCount);
      String managedServerPodName = domain2Uid + "-" + MANAGED_SERVER_NAME_BASE + "1";
      V1Pod managedServerPod = Kubernetes.getPod(domain2Namespace, null, managedServerPodName);
      assertNotNull(managedServerPod,"The admin pod does not exist in namespace " + domain2Namespace);
      String serverName = managedServerPod
          .getMetadata().getLabels()
          .get("servername");
      assertNotNull(serverName, "Can't find label servername");
      assertTrue(serverName.equalsIgnoreCase("managed-server1"),
          "Can't find or match label servername");
      String domainName = managedServerPod
          .getMetadata().getLabels()
          .get("domainname");
      assertNotNull(domainName, "Can't find label domainname");
      assertTrue(domainName.equalsIgnoreCase(domain2Uid),
          "Can't find expected value for  label domainname");
      
      String myclusterName = managedServerPod
          .getMetadata().getLabels()
          .get("clustername");
      assertNotNull(myclusterName, "Can't find label clustername");
      assertTrue(myclusterName.equalsIgnoreCase(clusterName),
          "Can't find expected value for label clustername");
      
      String domainuid = managedServerPod
          .getMetadata().getLabels()
          .get("domainuid");
      assertNotNull(domainuid, "Can't find label domainuid");
      assertTrue(domainuid.equalsIgnoreCase(domain2Uid),
          "Can't find expected value for label domainuid");

      String loghome = managedServerPod
          .getMetadata().getAnnotations()
          .get("loghome");
      assertNotNull(loghome, "Can't find label loghome");
      //value is not initialized since logHomeEnable = false
      assertTrue(loghome.equalsIgnoreCase("$(LOG_HOME)"),
          "Can't find expected value for label loghome, real value is " + loghome);

      String domainhome = managedServerPod
          .getMetadata().getAnnotations()
          .get("domainhome");
      assertNotNull(domainhome, "Can't find label domainhome");
      assertTrue(domainhome.equalsIgnoreCase(WDT_IMAGE_DOMAINHOME_BASE_DIR),
          "Can't find expected value for label domainhome");

    } finally {
      logger.info("Shutting down domain2");
      shutdownDomain(domain2Uid, domain2Namespace);
    }
  }

  /**
   * Test pod templates using all the variables $(SERVER_NAME), $(DOMAIN_NAME), $(DOMAIN_UID),
   * $(DOMAIN_HOME), $(LOG_HOME) and $(CLUSTER_NAME)
   * in serverPod for Model In Image domain. Make sure the domain comes up
   * successfully.
   *
   * @throws Exception when the domain crd creation fails or when updating the serverPod with
   *                   variables
   */
  @Test
  @DisplayName("Test pod templates using all the variables for model in image domain.")
  public void testPodTemplateUsingVariablesModelInImage() throws Exception {
    wdtImage = createAndVerifyDomainInImage();
    try {
      logger.info("Create domain and verify that it's running");
      createAndVerifyDomain(miiImage, domain1Uid, domain1Namespace, "FromModel", replicaCount);
      String managedServerPodName = domain1Uid + "-" + MANAGED_SERVER_NAME_BASE + "1";
      //checking that values from template variables are assigned.
      V1Pod managedServerPod = Kubernetes.getPod(domain1Namespace, null, managedServerPodName);
      assertNotNull(managedServerPod, "The managed-server1 pod does not exist in namespace " + domain1Namespace);
      String serverName = managedServerPod
          .getMetadata().getLabels()
          .get("servername");
      assertNotNull(serverName, "Can't find label servername");
      assertTrue(serverName.equalsIgnoreCase("managed-server1"),
          "Can't find or match label servername");
      String domainName = managedServerPod
          .getMetadata().getLabels()
          .get("domainname");
      assertNotNull(domainName, "Can't find label domainname");
      assertTrue(domainName.equalsIgnoreCase("wls-domain1"),
          "Can't find expected value for  label domainname");

      String myclusterName = managedServerPod
          .getMetadata().getLabels()
          .get("clustername");
      assertNotNull(myclusterName, "Can't find label clustername");
      assertTrue(myclusterName.equalsIgnoreCase(clusterName),
          "Can't find expected value for label clustername");

      String domainuid = managedServerPod
          .getMetadata().getLabels()
          .get("domainuid");
      assertNotNull(domainuid, "Can't find label domainuid");
      assertTrue(domainuid.equalsIgnoreCase(domain1Uid),
          "Can't find expected value for label domainuid");

      String loghome = managedServerPod
          .getMetadata().getAnnotations()
          .get("loghome");
      assertNotNull(loghome, "Can't find label loghome");
      //value is not initialized since logHomeEnable = false
      assertTrue(loghome.equalsIgnoreCase("$(LOG_HOME)"),
          "Can't find expected value for label loghome, real value is " + loghome);

      String domainhome = managedServerPod
          .getMetadata().getAnnotations()
          .get("domainhome");
      assertNotNull(domainhome, "Can't find label domainhome");
      assertTrue(domainhome.equalsIgnoreCase("/u01/domains/" + domain1Uid),
          "Can't find expected value for label domainhome");

    } finally {
      logger.info("Shutting down domain1");
      shutdownDomain(domain1Uid, domain1Namespace);
    }
  }

  @AfterAll
  public void tearDownAll() {

    // shutdown domain1
    logger.info("Shutting down domain1");
    assertTrue(shutdownDomain(domain1Uid, domain1Namespace),
        String.format("shutdown domain %s in namespace %s failed", domain1Uid, domain1Namespace));
    if (wdtImage != null) {
      deleteImage(miiImage);
    }

    // Delete domain custom resource
    logger.info("Delete domain custom resource in namespace {0}", domain1Namespace);
    assertDoesNotThrow(() -> deleteDomainCustomResource(domain1Uid, domain1Namespace),
        "deleteDomainCustomResource failed with ApiException");
    logger.info("Deleted Domain Custom Resource " + domain1Uid + " from " + domain1Namespace);

    // Delete wdt domain custom resource
    logger.info("Delete domain custom resource in namespace {0}", domain2Namespace);
    assertDoesNotThrow(() -> deleteDomainCustomResource(domain2Uid, domain2Namespace),
        "deleteDomainCustomResource failed with ApiException");
    logger.info("Deleted Domain Custom Resource " + domain2Uid + " from " + domain2Namespace);
  }

  /**
   * Create and verify domain in image.
   * @return image name
   */
  private static String createAndVerifyDomainInImage() {
    // create image with model files
    logger.info("Create image with model file and verify");
    String appPath = String.format("%s/../src/integration-tests/apps/testwebapp.war", ITTESTS_DIR);

    List<String> appList = new ArrayList();
    appList.add(appPath);

    int t3ChannelPort = getNextFreePort(31600, 32767);  // the port range has to be between 31,000 to 32,767

    Properties p = new Properties();
    p.setProperty("ADMIN_USER", ADMIN_USERNAME_DEFAULT);
    p.setProperty("ADMIN_PWD", ADMIN_PASSWORD_DEFAULT);
    p.setProperty("DOMAIN_NAME", domain2Uid);
    p.setProperty("DOMAIN_UID", domain2Uid);
    p.setProperty("ADMIN_NAME", "admin-server");
    p.setProperty("PRODUCTION_MODE_ENABLED", "true");
    p.setProperty("CLUSTER_NAME", clusterName);
    p.setProperty("CLUSTER_TYPE", "DYNAMIC");
    p.setProperty("CONFIGURED_MANAGED_SERVER_COUNT", "2");
    p.setProperty("MANAGED_SERVER_NAME_BASE", "managed-server");
    p.setProperty("T3_CHANNEL_PORT", Integer.toString(t3ChannelPort));
    p.setProperty("T3_PUBLIC_ADDRESS", K8S_NODEPORT_HOST);
    p.setProperty("MANAGED_SERVER_PORT", "8001");
    p.setProperty("SERVER_START_MODE", "prod");
    p.setProperty("ADMIN_PORT", "7001");
    p.setProperty("MYSQL_USER", "wluser1");
    p.setProperty("MYSQL_PWD", "wlpwd123");
    // create a temporary WebLogic domain property file as a input for WDT model file
    File domainPropertiesFile = assertDoesNotThrow(() ->
            File.createTempFile("domain", "properties"),
        "Failed to create domain properties file");
    assertDoesNotThrow(() ->
            p.store(new FileOutputStream(domainPropertiesFile), "WDT properties file"),
        "Failed to write domain properties file");

    final List<String> propertyList = Collections.singletonList(domainPropertiesFile.getPath());

    // build the model file list
    final List<String> modelList = Collections.singletonList(RESOURCE_DIR
        + "/wdt-models/wdt-model.yaml");

    wdtImage =
        createImageAndVerify(PODTEMPLATES_IMAGE_NAME,
            modelList,
            appList,
            propertyList,
            WLS_BASE_IMAGE_NAME,
            WLS_BASE_IMAGE_TAG,
            WLS,
            false,
            domain2Uid, true);



    // docker login and push image to docker registry if necessary
    dockerLoginAndPushImageToRegistry(wdtImage);

    return wdtImage;
  }

  //create domain from provided image and verify it's start
  private static void createAndVerifyDomain(String miiImage,
                                            String domainUid,
                                            String namespace,
                                            String domainHomeSource,
                                            int replicaCount) {
    // create docker registry secret to pull the image from registry
    logger.info("Create docker registry secret in namespace {0}", namespace);
    assertDoesNotThrow(() -> createDockerRegistrySecret(namespace),
        String.format("create Docker Registry Secret failed for %s", REPO_SECRET_NAME));
    // create secret for admin credentials
    logger.info("Create secret for admin credentials");
    String adminSecretName = "weblogic-credentials";
    assertDoesNotThrow(() -> createSecretWithUsernamePassword(adminSecretName, namespace,
        "weblogic", "welcome1"),
        String.format("create secret for admin credentials failed for %s", adminSecretName));

    // create encryption secret
    logger.info("Create encryption secret");
    String encryptionSecretName = "encryptionsecret";
    assertDoesNotThrow(() -> createSecretWithUsernamePassword(encryptionSecretName, namespace,
        "weblogicenc", "weblogicenc"),
        String.format("create encryption secret failed for %s", encryptionSecretName));

    // create domain and verify
    logger.info("Create model in image domain {0} in namespace {1} using docker image {2}",
        domainUid, namespace, miiImage);
    createDomainCrAndVerify(adminSecretName, REPO_SECRET_NAME, encryptionSecretName, miiImage,domainUid,
        namespace, domainHomeSource, replicaCount);
    String adminServerPodName = domainUid + "-admin-server";

    // check that admin service exists in the domain namespace
    logger.info("Checking that admin service {0} exists in namespace {1}",
        adminServerPodName, namespace);
    checkServiceExists(adminServerPodName, namespace);

    // check that admin server pod is ready
    logger.info("Checking that admin server pod {0} is ready in namespace {1}",
        adminServerPodName, namespace);
    checkPodReady(adminServerPodName, domainUid, namespace);

    String managedServerPrefix = domainUid + "-managed-server";
    // check for managed server pods existence in the domain namespace
    for (int i = 1; i <= replicaCount; i++) {
      String managedServerPodName = managedServerPrefix + i;

      // check that the managed server pod exists
      logger.info("Checking that managed server pod {0} exists in namespace {1}",
          managedServerPodName, namespace);
      checkPodExists(managedServerPodName, domainUid, namespace);

      // check that the managed server pod is ready
      logger.info("Checking that managed server pod {0} is ready in namespace {1}",
          managedServerPodName, namespace);
      checkPodReady(managedServerPodName, domainUid, namespace);

      // check that the managed server service exists in the domain namespace
      logger.info("Checking that managed server service {0} exists in namespace {1}",
          managedServerPodName, namespace);
      checkServiceExists(managedServerPodName, namespace);
    }
  }

  private static void createDomainCrAndVerify(String adminSecretName,
                                              String repoSecretName,
                                              String encryptionSecretName,
                                              String miiImage,
                                              String domainUid,
                                              String namespace,
                                              String domainHomeSource,
                                              int replicaCount) {
    // add labels to serverPod
    Map<String, String> labelKeyValue = new HashMap();
    labelKeyValue.put("servername", "$(SERVER_NAME)");
    labelKeyValue.put("domainname", "$(DOMAIN_NAME)");
    labelKeyValue.put("domainuid", "$(DOMAIN_UID)");

    // add annotations to serverPod as DOMAIN_HOME and LOG_HOME contains "/" which is not allowed
    // in labels
    Map<String, String> envKeyValue = new HashMap();
    envKeyValue.put("domainhome", "$(DOMAIN_HOME)");
    envKeyValue.put("loghome", "$(LOG_HOME)");

    // add label to cluster serverPod for CLUSTER_NAME
    Map<String, String> clusterLabelKeyValue = new HashMap();
    clusterLabelKeyValue.put("clustername", "$(CLUSTER_NAME)");
    int t3ChannelPort = getNextFreePort(31570, 32767);
    // create the domain CR
    Domain domain = new Domain()
        .apiVersion(DOMAIN_API_VERSION)
        .kind("Domain")
        .metadata(new V1ObjectMeta()
            .name(domainUid)
            .namespace(namespace))
        .spec(new DomainSpec()
            .domainUid(domainUid)
            .domainHomeSourceType(domainHomeSource)
            .image(miiImage)
            .addImagePullSecretsItem(new V1LocalObjectReference()
                .name(repoSecretName))
            .webLogicCredentialsSecret(new V1SecretReference()
                .name(adminSecretName)
                .namespace(namespace))
            .includeServerOutInPodLog(true)
            .serverStartPolicy("IF_NEEDED")
            .serverPod(new ServerPod()
                .annotations(envKeyValue)
                .labels(labelKeyValue)
                .addEnvItem(new V1EnvVar()
                    .name("JAVA_OPTIONS")
                    .value("-Dweblogic.StdoutDebugEnabled=false "
                        + "-Dweblogic.security.SSL.ignoreHostnameVerification=true "))
                .addEnvItem(new V1EnvVar()
                    .name("USER_MEM_ARGS")
                    .value("-Djava.security.egd=file:/dev/./urandom ")))
            .adminServer(new AdminServer()
                .serverStartState("RUNNING")
                .adminService(new AdminService()
                    .addChannelsItem(new Channel()
                        .channelName("default")
                        .nodePort(0))
                    .addChannelsItem(new Channel()
                        .channelName("T3Channel")
                        .nodePort(t3ChannelPort))))
            .addClustersItem(new Cluster()
                .clusterName(clusterName)
                .replicas(replicaCount)
                .serverStartState("RUNNING")
                .serverPod(new ServerPod().labels(clusterLabelKeyValue)))
            .configuration(new Configuration()
                .model(new Model()
                    .domainType("WLS")
                    .runtimeEncryptionSecret(encryptionSecretName))
                .introspectorJobActiveDeadlineSeconds(300L)));

    // create domain using model in image
    logger.info("Create model in image domain {0} in namespace {1} using docker image {2}",
        domainUid, namespace, miiImage);
    createDomainAndVerify(domain, namespace);
  }

}
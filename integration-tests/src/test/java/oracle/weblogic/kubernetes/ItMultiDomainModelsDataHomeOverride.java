// Copyright (c) 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;

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
import oracle.weblogic.kubernetes.annotations.IntegrationTest;
import oracle.weblogic.kubernetes.annotations.Namespaces;
import oracle.weblogic.kubernetes.logging.LoggingFacade;
import oracle.weblogic.kubernetes.utils.DomainUtils;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static java.nio.file.Paths.get;
import static oracle.weblogic.kubernetes.TestConstants.ADMIN_PASSWORD_DEFAULT;
import static oracle.weblogic.kubernetes.TestConstants.ADMIN_SERVER_NAME_BASE;
import static oracle.weblogic.kubernetes.TestConstants.ADMIN_USERNAME_DEFAULT;
import static oracle.weblogic.kubernetes.TestConstants.DOMAIN_API_VERSION;
import static oracle.weblogic.kubernetes.TestConstants.MANAGED_SERVER_NAME_BASE;
import static oracle.weblogic.kubernetes.TestConstants.MII_BASIC_APP_NAME;
import static oracle.weblogic.kubernetes.TestConstants.TEST_IMAGES_REPO_SECRET_NAME;
import static oracle.weblogic.kubernetes.TestConstants.WEBLOGIC_IMAGE_NAME;
import static oracle.weblogic.kubernetes.TestConstants.WEBLOGIC_IMAGE_TAG;
import static oracle.weblogic.kubernetes.TestConstants.WLS_DOMAIN_TYPE;
import static oracle.weblogic.kubernetes.actions.ActionConstants.ARCHIVE_DIR;
import static oracle.weblogic.kubernetes.actions.ActionConstants.MODEL_DIR;
import static oracle.weblogic.kubernetes.actions.TestActions.buildAppArchive;
import static oracle.weblogic.kubernetes.actions.TestActions.defaultAppParams;
import static oracle.weblogic.kubernetes.actions.TestActions.getServicePort;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.checkPodReadyAndServiceExists;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.getNextFreePort;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.testUntil;
import static oracle.weblogic.kubernetes.utils.DeployUtil.deployUsingWlst;
import static oracle.weblogic.kubernetes.utils.DomainUtils.createAndVerifyDomainInImageUsingWdt;
import static oracle.weblogic.kubernetes.utils.DomainUtils.createDomainAndVerify;
import static oracle.weblogic.kubernetes.utils.DomainUtils.shutdownDomainAndVerify;
import static oracle.weblogic.kubernetes.utils.FileUtils.doesFileExistInPod;
import static oracle.weblogic.kubernetes.utils.ImageUtils.createMiiImageAndVerify;
import static oracle.weblogic.kubernetes.utils.ImageUtils.createTestRepoSecret;
import static oracle.weblogic.kubernetes.utils.ImageUtils.dockerLoginAndPushImageToRegistry;
import static oracle.weblogic.kubernetes.utils.OKDUtils.createRouteForOKD;
import static oracle.weblogic.kubernetes.utils.OKDUtils.setTlsTerminationForRoute;
import static oracle.weblogic.kubernetes.utils.OperatorUtils.installAndVerifyOperator;
import static oracle.weblogic.kubernetes.utils.PodUtils.getExternalServicePodName;
import static oracle.weblogic.kubernetes.utils.PodUtils.setPodAntiAffinity;
import static oracle.weblogic.kubernetes.utils.SecretUtils.createSecretWithUsernamePassword;
import static oracle.weblogic.kubernetes.utils.ThreadSafeLogger.getLogger;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The test class creates WebLogic domains with three models.
 *  domain-on-pv ( using WDT )
 *  domain-in-image ( using WDT )
 *  model-in-image
 * Verify dataHome override in a domain with three models.
 */
@DisplayName("Verify dataHome override in the domain with different domain types")
@IntegrationTest
class ItMultiDomainModelsDataHomeOverride {

  // domain constants
  private static final int NUMBER_OF_CLUSTERS_MIIDOMAIN = 2;
  private static final String CLUSTER_NAME_PREFIX = "cluster-";
  private static final String clusterName = "cluster-1";
  private static final int replicaCount = 2;
  private static final String WLDF_OPENSESSION_APP = "opensessionapp";
  private static final String wlSecretName = "weblogic-credentials";
  private static final String DATA_HOME_OVERRIDE = "/u01/mydata";
  private static final String miiImageName = "mdlb-mii-image";
  private static final String wdtModelFileForMiiDomain = "model-multiclusterdomain-sampleapp-wls.yaml";
  private static final String miiDomainUid = "mdlb-miidomain";
  private static final String dimDomainUid = "mdlb-domaininimage";
  private static final String domainOnPVUid = "mdlb-domainonpv";
  private static final String wdtModelFileForDomainInImage = "wdt-singlecluster-multiapps-usingprop-wls.yaml";

  private static LoggingFacade logger = null;
  private static String miiDomainNamespace = null;
  private static String domainInImageNamespace = null;
  private static String domainOnPVNamespace = null;
  private static String miiImage = null;
  private static String encryptionSecretName = "encryptionsecret";

  /**
   * Install operator.
   *
   * @param namespaces list of namespaces created by the IntegrationTestWatcher by the
   *                   JUnit engine parameter resolution mechanism
   */
  @BeforeAll
  public static void initAll(@Namespaces(4) List<String> namespaces) {
    logger = getLogger();

    // get a unique operator namespace
    logger.info("Get a unique namespace for operator");
    assertNotNull(namespaces.get(0), "Namespace list is null");
    String opNamespace = namespaces.get(0);

    // get unique namespaces for three different type of domains
    logger.info("Getting unique namespaces for three different type of domains");
    assertNotNull(namespaces.get(1));
    miiDomainNamespace = namespaces.get(1);
    assertNotNull(namespaces.get(2));
    domainOnPVNamespace = namespaces.get(2);
    assertNotNull(namespaces.get(3));
    domainInImageNamespace = namespaces.get(3);

    // set the service account name for the operator
    String opServiceAccount = opNamespace + "-sa";

    // create mii image
    miiImage = createAndPushMiiImage();

    // install and verify operator with REST API
    installAndVerifyOperator(opNamespace, opServiceAccount, true, 0,
        miiDomainNamespace, domainOnPVNamespace, domainInImageNamespace);

    // This test uses the operator restAPI to scale the domain. To do this in OKD cluster,
    // we need to expose the external service as route and set tls termination to  passthrough
    logger.info("Create a route for the operator external service - only for OKD");
    createRouteForOKD("external-weblogic-operator-svc", opNamespace);

    // Patch the route just created to set tls termination to passthrough
    setTlsTerminationForRoute("external-weblogic-operator-svc", opNamespace);
  }

  /**
   * Verify dataHome override in a domain with domain in image type.
   * In this domain, set dataHome to /u01/mydata in domain custom resource
   * The domain contains JMS and File Store configuration
   * File store directory is set to /u01/oracle/customFileStore in the model file which should be overridden by dataHome
   * File store and JMS server are targeted to the WebLogic cluster cluster-1
   * see resource/wdt-models/wdt-singlecluster-multiapps-usingprop-wls.yaml
   */
  @Test
  @DisplayName("Test dataHome override in a domain with domain in image type")
  void testDataHomeOverrideDomainInImage() {

    // create domain in image domain
    List<String> appSrcDirList = new ArrayList<>();
    appSrcDirList.add(MII_BASIC_APP_NAME);
    appSrcDirList.add(WLDF_OPENSESSION_APP);
    createAndVerifyDomainInImageUsingWdt(dimDomainUid, domainInImageNamespace,
        wdtModelFileForDomainInImage, appSrcDirList, wlSecretName, clusterName, replicaCount);

    // check in admin server pod, there is no data file for JMS server created
    String dataFileToCheck = DATA_HOME_OVERRIDE + "/" + dimDomainUid + "/FILESTORE-0000000.DAT";
    String adminServerPodName = dimDomainUid + "-" + ADMIN_SERVER_NAME_BASE;
    assertFalse(assertDoesNotThrow(
        () -> doesFileExistInPod(domainInImageNamespace, adminServerPodName, dataFileToCheck),
            String.format("exception thrown when checking file %s exists in pod %s in namespace %s",
                dataFileToCheck, adminServerPodName, domainInImageNamespace)),
        String.format("%s exists in pod %s in namespace %s, expects not exist",
            dataFileToCheck, adminServerPodName, domainInImageNamespace));

    // check in admin server pod, the default admin server data file moved to DATA_HOME_OVERRIDE
    String defaultAdminDataFile = DATA_HOME_OVERRIDE + "/" + dimDomainUid + "/_WLS_ADMIN-SERVER000000.DAT";
    waitForFileExistsInPod(domainInImageNamespace, adminServerPodName, defaultAdminDataFile);

    // check in managed server pod, the custom data file for JMS and default managed server datafile are created
    // in DATA_HOME_OVERRIDE
    for (int i = 1; i <= replicaCount; i++) {
      String managedServerPodName = dimDomainUid + "-" + MANAGED_SERVER_NAME_BASE + i;
      String customDataFile =
          DATA_HOME_OVERRIDE + "/" + dimDomainUid + "/FILESTORE-0@MANAGED-SERVER" + i + "000000.DAT";
      waitForFileExistsInPod(domainInImageNamespace, managedServerPodName, customDataFile);

      String defaultMSDataFile = DATA_HOME_OVERRIDE + "/" + dimDomainUid + "/_WLS_MANAGED-SERVER" + i + "000000.DAT";
      waitForFileExistsInPod(domainInImageNamespace, managedServerPodName, defaultMSDataFile);
    }

    // shutdown domain and verify the domain is shutdown
    shutdownDomainAndVerify(domainInImageNamespace, dimDomainUid, replicaCount);
  }

  /**
   * Verify dataHome override in a domain with model in image type.
   * In this domain, dataHome is not specified in the domain custom resource
   * The domain contains JMS and File Store configuration
   * File store directory is set to /u01/oracle/customFileStore in the model file which should not be overridden
   * by dataHome
   * File store and JMS server are targeted to the WebLogic admin server
   * see resource/wdt-models/model-multiclusterdomain-sampleapp-wls.yaml
   */
  @Test
  @DisplayName("Test dataHome override in a domain with model in image type")
  void testDataHomeOverrideMiiDomain() {
    createMiiDomainWithMultiClusters(miiDomainNamespace);

    // check in admin server pod, there is a data file for JMS server created in /u01/oracle/customFileStore
    String dataFileToCheck = "/u01/oracle/customFileStore/FILESTORE-0000000.DAT";
    String adminServerPodName = miiDomainUid + "-" + ADMIN_SERVER_NAME_BASE;
    waitForFileExistsInPod(miiDomainNamespace, adminServerPodName, dataFileToCheck);

    // check in admin server pod, the default admin server data file is in default data store
    String defaultAdminDataFile =
        "/u01/domains/" + miiDomainUid + "/servers/admin-server/data/store/default/_WLS_ADMIN-SERVER000000.DAT";
    waitForFileExistsInPod(miiDomainNamespace, adminServerPodName, defaultAdminDataFile);

    // check in managed server pod, there is no custom data file for JMS is created
    for (int i = 1; i <= replicaCount; i++) {
      for (int j = 1; j <= NUMBER_OF_CLUSTERS_MIIDOMAIN; j++) {
        String managedServerPodName = miiDomainUid + "-cluster-" + j + "-" + MANAGED_SERVER_NAME_BASE + i;
        String customDataFile = "/u01/oracle/customFileStore/FILESTORE-0@MANAGED-SERVER" + i + "000000.DAT";
        assertFalse(assertDoesNotThrow(() ->
                    doesFileExistInPod(miiDomainNamespace, managedServerPodName, customDataFile),
                String.format("exception thrown when checking file %s exists in pod %s in namespace %s",
                    customDataFile, managedServerPodName, miiDomainNamespace)),
            String.format("found file %s in pod %s in namespace %s, expect not exist",
                customDataFile, managedServerPodName, miiDomainNamespace));

        String defaultMSDataFile = "/u01/domains/" + miiDomainUid + "/servers/cluster-" + j + "-managed-server" + i
            + "/data/store/default/_WLS_CLUSTER-" + j + "-MANAGED-SERVER" + i + "000000.DAT";
        waitForFileExistsInPod(miiDomainNamespace, managedServerPodName, defaultMSDataFile);
      }
    }

    // shutdown domain and verify the domain is shutdown
    shutdownDomainAndVerify(miiDomainNamespace, miiDomainUid, replicaCount);
  }

  /**
   * Verify dataHome override in a domain with domain on PV type.
   * In this domain, dataHome is set to empty string in the domain custom resource
   * The domain contains JMS and File Store configuration
   * File store directory is set to /u01/oracle/customFileStore in the model file which should not be overridden
   * by dataHome
   * File store and JMS server are targeted to the WebLogic admin server
   * see resource/wdt-models/domain-onpv-wdt-model.yaml
   */
  @Test
  @DisplayName("Test dataHome override in a domain with domain on PV type")
  void testDataHomeOverrideDomainOnPV() {
    createDomainOnPvUsingWdt(domainOnPVUid, domainOnPVNamespace);
    
    String uniquePath = "/u01/shared/" + domainOnPVNamespace + "/domains/" + domainOnPVUid;

    // check in admin server pod, there is a data file for JMS server created in /u01/oracle/customFileStore
    String dataFileToCheck = "/u01/oracle/customFileStore/FILESTORE-0000000.DAT";
    String adminServerPodName = domainOnPVUid + "-" + ADMIN_SERVER_NAME_BASE;
    waitForFileExistsInPod(domainOnPVNamespace, adminServerPodName, dataFileToCheck);

    // check in admin server pod, the default admin server data file is in default data store
    String defaultAdminDataFile =
        uniquePath + "/servers/admin-server/data/store/default/_WLS_ADMIN-SERVER000000.DAT";
    waitForFileExistsInPod(domainOnPVNamespace, adminServerPodName, defaultAdminDataFile);

    // check in managed server pod, there is no custom data file for JMS is created
    for (int i = 1; i <= replicaCount; i++) {
      String managedServerPodName = domainOnPVUid + "-" + MANAGED_SERVER_NAME_BASE + i;
      String customDataFile = "/u01/oracle/customFileStore/FILESTORE-0@MANAGED-SERVER" + i + "000000.DAT";
      assertFalse(assertDoesNotThrow(() ->
                  doesFileExistInPod(domainOnPVNamespace, managedServerPodName, customDataFile),
              String.format("exception thrown when checking file %s exists in pod %s in namespace %s",
                  customDataFile, managedServerPodName, domainOnPVNamespace)),
          String.format("found file %s in pod %s in namespace %s, expect not exist",
              customDataFile, managedServerPodName, domainOnPVNamespace));

      String defaultMSDataFile = uniquePath + "/servers/managed-server" + i
          + "/data/store/default/_WLS_MANAGED-SERVER" + i + "000000.DAT";
      waitForFileExistsInPod(domainOnPVNamespace, managedServerPodName, defaultMSDataFile);
    }

    // shutdown domain and verify the domain is shutdown
    shutdownDomainAndVerify(domainOnPVNamespace, domainOnPVUid, replicaCount);
  }

  /**
   * Create model in image domain with multiple clusters.
   *
   * @param domainNamespace namespace in which the domain will be created
   */
  private static void createMiiDomainWithMultiClusters(String domainNamespace) {

    // admin/managed server name here should match with WDT model yaml file
    String adminServerPodName = miiDomainUid + "-" + ADMIN_SERVER_NAME_BASE;

    // create docker registry secret to pull the image from registry
    // this secret is used only for non-kind cluster
    logger.info("Creating docker registry secret in namespace {0}", domainNamespace);
    createTestRepoSecret(domainNamespace);

    // create secret for admin credentials
    logger.info("Creating secret for admin credentials");
    String adminSecretName = "weblogic-credentials";
    createSecretWithUsernamePassword(adminSecretName, domainNamespace, ADMIN_USERNAME_DEFAULT, ADMIN_PASSWORD_DEFAULT);

    // create encryption secret
    logger.info("Creating encryption secret");
    createSecretWithUsernamePassword(encryptionSecretName, domainNamespace, "weblogicenc", "weblogicenc");

    // construct the cluster list used for domain custom resource
    List<Cluster> clusterList = new ArrayList<>();
    for (int i = NUMBER_OF_CLUSTERS_MIIDOMAIN; i >= 1; i--) {
      clusterList.add(new Cluster()
          .clusterName(CLUSTER_NAME_PREFIX + i)
          .replicas(replicaCount)
          .serverStartState("RUNNING"));
    }

    // create the domain CR
    Domain domain = new Domain()
        .apiVersion(DOMAIN_API_VERSION)
        .kind("Domain")
        .metadata(new V1ObjectMeta()
            .name(miiDomainUid)
            .namespace(domainNamespace))
        .spec(new DomainSpec()
            .domainUid(miiDomainUid)
            .domainHome("/u01/domains/" + miiDomainUid)
            .domainHomeSourceType("FromModel")
            .image(miiImage)
            .addImagePullSecretsItem(new V1LocalObjectReference()
                .name(TEST_IMAGES_REPO_SECRET_NAME))
            .webLogicCredentialsSecret(new V1SecretReference()
                .name(adminSecretName)
                .namespace(domainNamespace))
            .includeServerOutInPodLog(true)
            .serverStartPolicy("IF_NEEDED")
            .serverPod(new ServerPod()
                .addEnvItem(new V1EnvVar()
                    .name("JAVA_OPTIONS")
                    .value("-Dweblogic.security.SSL.ignoreHostnameVerification=true "
                        + "-Dweblogic.StdoutDebugEnabled=false"))
                .addEnvItem(new V1EnvVar()
                    .name("USER_MEM_ARGS")
                    .value("-Djava.security.egd=file:/dev/./urandom ")))
            .adminServer(new AdminServer()
                .serverStartState("RUNNING")
                .adminChannelPortForwardingEnabled(true)
                .adminService(new AdminService()
                    .addChannelsItem(new Channel()
                        .channelName("default-secure")
                        .nodePort(getNextFreePort()))
                    .addChannelsItem(new Channel()
                        .channelName("default")
                        .nodePort(getNextFreePort()))))
            .clusters(clusterList)
            .configuration(new Configuration()
                .introspectorJobActiveDeadlineSeconds(300L)
                .model(new Model()
                    .domainType(WLS_DOMAIN_TYPE)
                    .runtimeEncryptionSecret(encryptionSecretName))));
    setPodAntiAffinity(domain);

    // create model in image domain
    logger.info("Creating model in image domain {0} in namespace {1} using docker image {2}",
        miiDomainUid, domainNamespace, miiImage);
    createDomainAndVerify(domain, domainNamespace);

    // check that admin server pod is ready and service exists in domain namespace
    logger.info("Checking that admin server pod {0} is ready and service exists in namespace {1}",
        adminServerPodName, domainNamespace);
    checkPodReadyAndServiceExists(adminServerPodName, miiDomainUid, domainNamespace);

    // check the readiness for the managed servers in each cluster
    for (int i = 1; i <= NUMBER_OF_CLUSTERS_MIIDOMAIN; i++) {
      for (int j = 1; j <= replicaCount; j++) {
        String managedServerPodName =
            miiDomainUid + "-" + CLUSTER_NAME_PREFIX + i + "-" + MANAGED_SERVER_NAME_BASE + j;

        // check managed server pod is ready and service exists in the namespace
        logger.info("Checking that managed server pod {0} is ready and service exists in namespace {1}",
            managedServerPodName, domainNamespace);
        checkPodReadyAndServiceExists(managedServerPodName, miiDomainUid, domainNamespace);
      }
    }
  }

  /**
   * Create a domain in PV using WDT.
   * @param domainUid uid of the domain
   * @param domainNamespace namespace in which the domain will be created
   * @return oracle.weblogic.domain.Domain objects
   */
  private static Domain createDomainOnPvUsingWdt(String domainUid, String domainNamespace) {

    final String adminServerPodName = domainUid + "-" + ADMIN_SERVER_NAME_BASE;

    Domain domain = DomainUtils.createDomainOnPvUsingWdt(domainUid, domainNamespace, wlSecretName, clusterName,
        replicaCount, ItMultiDomainModelsDataHomeOverride.class.getSimpleName());

    // build application sample-app and opensessionapp
    List<String> appSrcDirList = new ArrayList<>();
    appSrcDirList.add(MII_BASIC_APP_NAME);
    appSrcDirList.add(WLDF_OPENSESSION_APP);

    for (String appName : appSrcDirList) {
      assertTrue(buildAppArchive(defaultAppParams()
              .srcDirList(Collections.singletonList(appName))
              .appName(appName)),
          String.format("Failed to create app archive for %s", appName));

      logger.info("Getting port for default channel");
      int defaultChannelPort = assertDoesNotThrow(()
              -> getServicePort(domainNamespace, getExternalServicePodName(adminServerPodName), "default"),
          "Getting admin server default port failed");
      logger.info("default channel port: {0}", defaultChannelPort);
      assertNotEquals(-1, defaultChannelPort, "admin server defaultChannelPort is not valid");

      //deploy application
      Path archivePath = get(ARCHIVE_DIR, "wlsdeploy", "applications", appName + ".ear");
      logger.info("Deploying webapp {0} to domain {1}", archivePath, domainUid);
      deployUsingWlst(adminServerPodName, Integer.toString(defaultChannelPort),
          ADMIN_USERNAME_DEFAULT, ADMIN_PASSWORD_DEFAULT, clusterName + "," + ADMIN_SERVER_NAME_BASE, archivePath,
          domainNamespace);
    }

    // check that admin server pod is ready and service exists in domain namespace
    logger.info("Checking that admin server pod {0} is ready and service exists in namespace {1}",
        adminServerPodName, domainNamespace);
    checkPodReadyAndServiceExists(adminServerPodName, domainUid, domainNamespace);

    // check the readiness for the managed servers in each cluster
    for (int j = 1; j <= replicaCount; j++) {
      String managedServerPodName = domainUid + "-" + MANAGED_SERVER_NAME_BASE + j;

      // check managed server pod is ready and service exists in the namespace
      logger.info("Checking that managed server pod {0} is ready and service exists in namespace {1}",
          managedServerPodName, domainNamespace);
      checkPodReadyAndServiceExists(managedServerPodName, domainUid, domainNamespace);
    }

    return domain;
  }

  /**
   * Check whether a file exists in a pod in the given namespace.
   *
   * @param namespace the Kubernetes namespace that the pod is in
   * @param podName the name of the Kubernetes pod in which the command is expected to run
   * @param fileName the filename to check
   * @return true if the file exists, otherwise return false
   */
  private Callable<Boolean> fileExistsInPod(String namespace, String podName, String fileName) {
    return () -> doesFileExistInPod(namespace, podName, fileName);
  }

  /**
   * Wait for file existing in the pod in the given namespace up to 1 minute.
   * @param namespace the Kubernetes namespace that the pod is in
   * @param podName the name of the Kubernetes pod in which the command is expected to run
   * @param fileName the filename to check
   */
  private void waitForFileExistsInPod(String namespace, String podName, String fileName) {

    logger.info("Wait for file {0} existing in pod {1} in namespace {2}", fileName, podName, namespace);
    testUntil(
        assertDoesNotThrow(() -> fileExistsInPod(namespace, podName, fileName)),
        logger,
        "file {0} exists in pod {1} in namespace {2}",
        fileName,
        podName,
        namespace);
  }

  /**
   * Create mii image and push it to the registry.
   *
   * @return mii image created
   */
  private static String createAndPushMiiImage() {
    // create image with model files
    logger.info("Creating image with model file {0} and verify", wdtModelFileForMiiDomain);
    List<String> appSrcDirList = new ArrayList<>();
    appSrcDirList.add(MII_BASIC_APP_NAME);
    appSrcDirList.add(WLDF_OPENSESSION_APP);
    miiImage =
        createMiiImageAndVerify(miiImageName, Collections.singletonList(MODEL_DIR + "/" + wdtModelFileForMiiDomain),
            appSrcDirList, WEBLOGIC_IMAGE_NAME, WEBLOGIC_IMAGE_TAG, WLS_DOMAIN_TYPE, false);

    // docker login and push image to docker registry if necessary
    dockerLoginAndPushImageToRegistry(miiImage);

    return miiImage;
  }

}

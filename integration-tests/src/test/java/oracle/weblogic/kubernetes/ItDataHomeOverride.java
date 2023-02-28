// Copyright (c) 2022, 2023, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;

import oracle.weblogic.kubernetes.annotations.IntegrationTest;
import oracle.weblogic.kubernetes.annotations.Namespaces;
import oracle.weblogic.kubernetes.logging.LoggingFacade;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static oracle.weblogic.kubernetes.TestConstants.ADMIN_SERVER_NAME_BASE;
import static oracle.weblogic.kubernetes.TestConstants.MANAGED_SERVER_NAME_BASE;
import static oracle.weblogic.kubernetes.TestConstants.MII_BASIC_APP_NAME;
import static oracle.weblogic.kubernetes.TestConstants.WDT_BASIC_MODEL_PROPERTIES_FILE;
import static oracle.weblogic.kubernetes.TestConstants.WEBLOGIC_IMAGE_NAME;
import static oracle.weblogic.kubernetes.TestConstants.WEBLOGIC_IMAGE_TAG;
import static oracle.weblogic.kubernetes.TestConstants.WLS_DOMAIN_TYPE;
import static oracle.weblogic.kubernetes.actions.ActionConstants.MODEL_DIR;
import static oracle.weblogic.kubernetes.utils.ClusterUtils.deleteClusterCustomResourceAndVerify;
import static oracle.weblogic.kubernetes.utils.CommonMiiTestUtils.createMiiDomainAndVerify;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.testUntil;
import static oracle.weblogic.kubernetes.utils.DomainUtils.shutdownDomainAndVerify;
import static oracle.weblogic.kubernetes.utils.FileUtils.doesFileExistInPod;
import static oracle.weblogic.kubernetes.utils.ImageUtils.createImageAndVerify;
import static oracle.weblogic.kubernetes.utils.ImageUtils.imageRepoLoginAndPushImageToRegistry;
import static oracle.weblogic.kubernetes.utils.OperatorUtils.installAndVerifyOperator;
import static oracle.weblogic.kubernetes.utils.ThreadSafeLogger.getLogger;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Verify dataHome override with different dataHome setting in the domain spec.
 */
@DisplayName("Verify dataHome override with different dataHome setting in the domain spec")
@IntegrationTest
@Tag("oke-parallel")
@Tag("kind-parallel")
@Tag("olcne")
class ItDataHomeOverride {

  // domain constants
  private static final int replicaCount = 2;
  private static final String WLDF_OPENSESSION_APP = "opensessionapp";
  private static final String DATA_HOME_OVERRIDE = "/u01/mydata";
  private static final String miiImageName = "datahome-mii-image";
  private static final String wdtModelFileForMiiDomain = "wdt-singlecluster-multiapps-usingprop-wls.yaml";
  private static final String clusterName = "dimcluster-1";
  private static LoggingFacade logger = null;
  private static String miiDomainNamespace = null;
  private static String miiImage = null;

  /**
   * Install operator.
   *
   * @param namespaces list of namespaces created by the IntegrationTestWatcher by the
   *                   JUnit engine parameter resolution mechanism
   */
  @BeforeAll
  public static void initAll(@Namespaces(2) List<String> namespaces) {
    logger = getLogger();

    // get a unique operator namespace
    logger.info("Get a unique namespace for operator");
    assertNotNull(namespaces.get(0), "Namespace list is null");
    String opNamespace = namespaces.get(0);

    // get unique namespaces for three different type of domains
    logger.info("Getting unique namespaces for three different type of domains");
    assertNotNull(namespaces.get(1));
    miiDomainNamespace = namespaces.get(1);

    // create mii image
    miiImage = createAndPushMiiImage();

    // set the service account name for the operator
    String opServiceAccount = opNamespace + "-sa";

    // install and verify operator with REST API
    installAndVerifyOperator(opNamespace, opServiceAccount, false, 0, miiDomainNamespace);
  }

  /**
   * Verify dataHome override in a domain when dataHome set in the domain spec.
   * In this domain, set dataHome to /u01/mydata in domain custom resource
   * The domain contains JMS and File Store configuration
   * File store directory is set to /u01/oracle/customFileStore in the model file which should be overridden by dataHome
   * File store and JMS server are targeted to the WebLogic cluster dimcluster-1
   * see resource/wdt-models/wdt-singlecluster-multiapps-usingprop-wls.yaml
   */
  @Test
  @DisplayName("Test dataHome override in a domain with dataHome is set in the domain spec")
  void testDataHomeOverrideWithDataHomeInSpec() {

    // create domain in image domain
    String domainUid = "overridedatahome-domain";
    String adminServerPodName = domainUid + "-" + ADMIN_SERVER_NAME_BASE;
    String managedServerPrefix = domainUid + "-" + MANAGED_SERVER_NAME_BASE;
    // create mii domain and override data home
    createMiiDomainAndVerify(miiDomainNamespace, domainUid, miiImage,
        adminServerPodName, managedServerPrefix, replicaCount, Arrays.asList(clusterName),
        true, DATA_HOME_OVERRIDE);

    // check in admin server pod, there is no data file for JMS server created
    String dataFileToCheck = DATA_HOME_OVERRIDE + "/" + domainUid + "/FILESTORE-0000000.DAT";
    assertFalse(assertDoesNotThrow(
        () -> doesFileExistInPod(miiDomainNamespace, adminServerPodName, dataFileToCheck),
            String.format("exception thrown when checking file %s exists in pod %s in namespace %s",
                dataFileToCheck, adminServerPodName, miiDomainNamespace)),
        String.format("%s exists in pod %s in namespace %s, expects not exist",
            dataFileToCheck, adminServerPodName, miiDomainNamespace));

    // check in admin server pod, the default admin server data file moved to DATA_HOME_OVERRIDE
    String defaultAdminDataFile = DATA_HOME_OVERRIDE + "/" + domainUid + "/_WLS_ADMIN-SERVER000000.DAT";
    waitForFileExistsInPod(miiDomainNamespace, adminServerPodName, defaultAdminDataFile);

    // check in managed server pod, the custom data file for JMS and default managed server datafile are created
    // in DATA_HOME_OVERRIDE
    for (int i = 1; i <= replicaCount; i++) {
      String managedServerPodName = domainUid + "-" + MANAGED_SERVER_NAME_BASE + i;
      String customDataFile =
          DATA_HOME_OVERRIDE + "/" + domainUid + "/FILESTORE-0@MANAGED-SERVER" + i + "000000.DAT";
      waitForFileExistsInPod(miiDomainNamespace, managedServerPodName, customDataFile);

      String defaultMSDataFile = DATA_HOME_OVERRIDE + "/" + domainUid + "/_WLS_MANAGED-SERVER" + i + "000000.DAT";
      waitForFileExistsInPod(miiDomainNamespace, managedServerPodName, defaultMSDataFile);
    }

    // shutdown domain and verify the domain is shutdown
    shutdownDomainAndVerify(miiDomainNamespace, domainUid, replicaCount);
  }

  /**
   * Verify dataHome override in a domain with model in image type.
   * In this domain, dataHome is not specified in the domain custom resource
   * The domain contains JMS and File Store configuration
   * File store directory is set to /u01/oracle/customFileStore in the model file which should not be overridden
   * by dataHome
   * File store and JMS server are targeted to the WebLogic cluster dimcluster-1
   * see resource/wdt-models/wdt-singlecluster-multiapps-usingprop-wls.yaml
   */
  @Test
  @DisplayName("Test dataHome override in a domain with model in image type")
  void testDataHomeOverrideNoDataHomeInSpec() {

    String domainUid = "nodatahome-domain";
    String adminServerPodName = domainUid + "-" + ADMIN_SERVER_NAME_BASE;
    String managedServerPrefix = domainUid + "-" + MANAGED_SERVER_NAME_BASE;
    // create mii domain and don't override data home
    createMiiDomainAndVerify(miiDomainNamespace, domainUid, miiImage,
        adminServerPodName, managedServerPrefix, replicaCount, Arrays.asList(clusterName), false, null);

    // check in admin server pod, there is no data file for JMS server created in /u01/oracle/customFileStore
    String dataFileToCheck = "/u01/oracle/customFileStore/FILESTORE-0000000.DAT";
    assertFalse(assertDoesNotThrow(
        () -> doesFileExistInPod(miiDomainNamespace, adminServerPodName, dataFileToCheck),
        String.format("exception thrown when checking file %s exists in pod %s in namespace %s",
            dataFileToCheck, adminServerPodName, miiDomainNamespace)),
        String.format("%s exists in pod %s in namespace %s, expects not exist",
            dataFileToCheck, adminServerPodName, miiDomainNamespace));

    // check in admin server pod, the default admin server data file is in default data store
    String defaultAdminDataFile =
        "/u01/domains/" + domainUid + "/servers/admin-server/data/store/default/_WLS_ADMIN-SERVER000000.DAT";
    waitForFileExistsInPod(miiDomainNamespace, adminServerPodName, defaultAdminDataFile);

    // check in managed server pod, there is custom data file for JMS is created
    for (int i = 1; i <= replicaCount; i++) {
      String managedServerPodName = managedServerPrefix + i;
      String customDataFile = "/u01/oracle/customFileStore/FILESTORE-0@MANAGED-SERVER" + i + "000000.DAT";
      waitForFileExistsInPod(miiDomainNamespace, managedServerPodName, customDataFile);

      String defaultMSDataFile = "/u01/domains/" + domainUid + "/servers/managed-server" + i
          + "/data/store/default/_WLS_MANAGED-SERVER" + i + "000000.DAT";
      waitForFileExistsInPod(miiDomainNamespace, managedServerPodName, defaultMSDataFile);
    }

    // shutdown domain and verify the domain is shutdown
    shutdownDomainAndVerify(miiDomainNamespace, domainUid, replicaCount);
  }

  /**
   * Verify dataHome override in a domain with dataHome is set to empty string.
   * In this domain, dataHome is set to empty string in the domain custom resource
   * The domain contains JMS and File Store configuration
   * File store directory is set to /u01/oracle/customFileStore in the model file which should not be overridden
   * by dataHome
   * File store and JMS server are targeted to the WebLogic cluster dimcluster-1
   */
  @Test
  @DisplayName("Test dataHome override in a domain with dataHome is a empty string in the domain spec")
  void testDataHomeOverrideDataHomeEmpty() {

    String domainUid = "emptydatahome-domain";
    String adminServerPodName = domainUid + "-" + ADMIN_SERVER_NAME_BASE;
    String managedServerPrefix = domainUid + "-" + MANAGED_SERVER_NAME_BASE;
    // create mii domain and set datahome to empty string
    createMiiDomainAndVerify(miiDomainNamespace, domainUid, miiImage,
        adminServerPodName, managedServerPrefix, replicaCount, Arrays.asList(clusterName), true, "");

    // check in admin server pod, there is no data file for JMS server created in /u01/oracle/customFileStore
    String dataFileToCheck = "/u01/oracle/customFileStore/FILESTORE-0000000.DAT";
    assertFalse(assertDoesNotThrow(
        () -> doesFileExistInPod(miiDomainNamespace, adminServerPodName, dataFileToCheck),
        String.format("exception thrown when checking file %s exists in pod %s in namespace %s",
            dataFileToCheck, adminServerPodName, miiDomainNamespace)),
        String.format("%s exists in pod %s in namespace %s, expects not exist",
            dataFileToCheck, adminServerPodName, miiDomainNamespace));

    // check in admin server pod, the default admin server data file is in default data store
    String defaultAdminDataFile =
        "/u01/domains/" + domainUid + "/servers/admin-server/data/store/default/_WLS_ADMIN-SERVER000000.DAT";
    waitForFileExistsInPod(miiDomainNamespace, adminServerPodName, defaultAdminDataFile);

    // check in managed server pod, there is custom data file for JMS is created
    for (int i = 1; i <= replicaCount; i++) {
      String managedServerPodName = domainUid + "-" + MANAGED_SERVER_NAME_BASE + i;
      String customDataFile = "/u01/oracle/customFileStore/FILESTORE-0@MANAGED-SERVER" + i + "000000.DAT";
      waitForFileExistsInPod(miiDomainNamespace, managedServerPodName, customDataFile);

      String defaultMSDataFile = "/u01/domains/" + domainUid + "/servers/managed-server" + i
          + "/data/store/default/_WLS_MANAGED-SERVER" + i + "000000.DAT";
      waitForFileExistsInPod(miiDomainNamespace, managedServerPodName, defaultMSDataFile);
    }

    // shutdown domain and verify the domain is shutdown
    shutdownDomainAndVerify(miiDomainNamespace, domainUid, replicaCount);
  }

  @AfterEach
  public void deleteClusterResource() {
    // delete cluster resource
    deleteClusterCustomResourceAndVerify(clusterName, miiDomainNamespace);
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

    miiImage = createImageAndVerify(
        miiImageName, Collections.singletonList(MODEL_DIR + "/" + wdtModelFileForMiiDomain), appSrcDirList,
        Collections.singletonList(MODEL_DIR + "/" + WDT_BASIC_MODEL_PROPERTIES_FILE), WEBLOGIC_IMAGE_NAME,
        WEBLOGIC_IMAGE_TAG, WLS_DOMAIN_TYPE, true, null, false);

    // docker login and push image to docker registry if necessary
    imageRepoLoginAndPushImageToRegistry(miiImage);

    return miiImage;
  }

}

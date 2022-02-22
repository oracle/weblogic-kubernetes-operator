// Copyright (c) 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes.utils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

import oracle.weblogic.kubernetes.actions.impl.primitive.WitParams;
import oracle.weblogic.kubernetes.logging.LoggingFacade;

import static java.nio.file.Files.readAllLines;
import static java.nio.file.Paths.get;
import static oracle.weblogic.kubernetes.TestConstants.MII_BASIC_IMAGE_TAG;
import static oracle.weblogic.kubernetes.TestConstants.OKD;
import static oracle.weblogic.kubernetes.TestConstants.RESULTS_ROOT;
import static oracle.weblogic.kubernetes.actions.ActionConstants.WIT_BUILD_DIR;
import static oracle.weblogic.kubernetes.actions.TestActions.createAuxImage;
import static oracle.weblogic.kubernetes.actions.TestActions.createAuxImageAndReturnResult;
import static oracle.weblogic.kubernetes.actions.TestActions.getServiceNodePort;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.checkSystemResourceConfig;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.checkSystemResourceConfiguration;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.testUntil;
import static oracle.weblogic.kubernetes.utils.FileUtils.checkDirectory;
import static oracle.weblogic.kubernetes.utils.FileUtils.copyFileFromPod;
import static oracle.weblogic.kubernetes.utils.PodUtils.getExternalServicePodName;
import static oracle.weblogic.kubernetes.utils.ThreadSafeLogger.getLogger;
import static org.apache.commons.io.FileUtils.deleteQuietly;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotEquals;


public class AuxiliaryImageUtils {

  /**
   * Create a AuxImage using WIT.
   *
   * @param witParams wit params
   *
   */
  public static boolean createAuxImageUsingWIT(WitParams witParams) {

    WitParams newWitParams = setupCommonWitParameters(witParams);
    LoggingFacade logger = getLogger();
    // build an image using WebLogic Image Tool
    logger.info("Create image {0}:{1} using imagetool.sh", witParams.modelImageName(), witParams.modelImageTag());
    return createAuxImage(newWitParams);
  }

  /**
   * Create a AuxImage using WIT and return result output.
   *
   * @param witParams wit params
   *
   */
  public static ExecResult createAuxImageUsingWITAndReturnResult(WitParams witParams) {

    WitParams newWitParams = setupCommonWitParameters(witParams);
    LoggingFacade logger = getLogger();

    // build an image using WebLogic Image Tool
    logger.info("Create image {0}:{1} using imagetool.sh", witParams.modelImageName(), witParams.modelImageTag());
    ExecResult result = createAuxImageAndReturnResult(newWitParams);
    logger.info("result stdout={0}", result.stdout());
    logger.info("result stderr={0}", result.stderr());
    return result;
  }

  /**
   * Setup common env with wit params.
   *
   * @param witParams wit params
   *
   */
  public static WitParams setupCommonWitParameters(WitParams witParams) {
    // Set additional environment variables for WIT
    checkDirectory(WIT_BUILD_DIR);
    Map<String, String> env = new HashMap<>();
    env.put("WLSIMG_BLDDIR", WIT_BUILD_DIR);

    // For k8s 1.16 support and as of May 6, 2020, we presently need a different JDK for these
    // tests and for image tool. This is expected to no longer be necessary once JDK 11.0.8 or
    // the next JDK 14 versions are released.
    String witJavaHome = System.getenv("WIT_JAVA_HOME");
    if (witJavaHome != null) {
      env.put("JAVA_HOME", witJavaHome);
    }

    String witTarget = ((OKD) ? "OpenShift" : "Default");

    return witParams.target(witTarget).env(env).redirect(true);
  }

  /**
   * Create a AuxImage.
   *
   * @param auxImage auxImage name
   * @param modelList model list
   * @param archiveList archive list
   *
   */
  public static Callable<Boolean> createAuxiliaryImage(String auxImage, List<String> modelList,
                                                       List<String> archiveList) {

    return (() -> {
      WitParams witParams =
          new WitParams()
              .modelImageName(auxImage)
              .modelImageTag(MII_BASIC_IMAGE_TAG)
              .modelFiles(modelList)
              .modelArchiveFiles(archiveList);

      return createAuxImageUsingWIT(witParams);
    });
  }

  /**
   * Create a AuxImage using WIT.
   *
   * @param witParams wit params
   *
   */
  public static Callable<Boolean> createAuxiliaryImage(WitParams witParams) {

    return (() -> createAuxImageUsingWIT(witParams));
  }

  /**
   * Check Configured JMS Resource.
   *
   * @param domainNamespace domain namespace
   * @param adminServerPodName  admin server pod name
   * @param adminSvcExtHost admin server external host
   */
  public static void checkConfiguredJMSresouce(String domainNamespace, String adminServerPodName,
                                               String adminSvcExtHost) {

    LoggingFacade logger = getLogger();
    int adminServiceNodePort
        = getServiceNodePort(domainNamespace, getExternalServicePodName(adminServerPodName), "default");
    assertNotEquals(-1, adminServiceNodePort, "admin server default node port is not valid");

    testUntil(
        () -> checkSystemResourceConfiguration(adminSvcExtHost, adminServiceNodePort, "JMSSystemResources",
            "TestClusterJmsModule2", "200"),
        logger,
        "Checking for adminSvcExtHost: {0} or adminServiceNodePort: {1} if resourceName: {2} exists",
        adminSvcExtHost,
        adminServiceNodePort,
        "TestClusterJmsModule2");
    logger.info("Found the JMSSystemResource configuration");
  }

  /**
   * Check Configured JDBC Resource.
   *
   * @param domainNamespace domain namespace
   * @param adminServerPodName  admin server pod name
   * @param adminSvcExtHost admin server external host
   */
  public static void checkConfiguredJDBCresouce(String domainNamespace, String adminServerPodName,
                                                String adminSvcExtHost) {
    LoggingFacade logger = getLogger();
    int adminServiceNodePort
        = getServiceNodePort(domainNamespace, getExternalServicePodName(adminServerPodName), "default");
    assertNotEquals(-1, adminServiceNodePort, "admin server default node port is not valid");

    testUntil(
        () -> checkSystemResourceConfig(adminSvcExtHost, adminServiceNodePort,
            "JDBCSystemResources/TestDataSource/JDBCResource/JDBCDriverParams",
            "jdbc:oracle:thin:@\\/\\/xxx.xxx.x.xxx:1521\\/ORCLCDB"),
        logger,
        "Checking for adminSvcExtHost: {0} or adminServiceNodePort: {1} if resourceName: {2} has the right value",
        adminSvcExtHost,
        adminServiceNodePort,
        "JDBCSystemResources/TestDataSource/JDBCResource/JDBCDriverParams");
    logger.info("Found the DataResource configuration");
  }

  /**
   * Check WDT installed version.
   *
   * @param domainNamespace domain namespace
   * @param adminServerPodName  admin server pod name
   * @param auxiliaryImagePath  auxiliary image path
   * @param className test class name
   */
  public static String checkWDTVersion(String domainNamespace, String adminServerPodName,
                                       String auxiliaryImagePath, String className)
      throws Exception {
    assertDoesNotThrow(() ->
        deleteQuietly(get(RESULTS_ROOT, className, "/WDTversion.txt").toFile()));
    assertDoesNotThrow(() -> copyFileFromPod(domainNamespace,
        adminServerPodName, "weblogic-server",
        auxiliaryImagePath + "/weblogic-deploy/VERSION.txt",
        get(RESULTS_ROOT, className, "/WDTversion.txt")),
        " Can't find file in the pod, or failed to copy");


    return readAllLines(get(RESULTS_ROOT, className, "/WDTversion.txt")).get(0);
  }
}

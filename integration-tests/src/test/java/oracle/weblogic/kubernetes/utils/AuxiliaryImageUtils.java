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
import static oracle.weblogic.kubernetes.TestConstants.DOMAIN_IMAGES_REPO;
import static oracle.weblogic.kubernetes.TestConstants.OKD;
import static oracle.weblogic.kubernetes.TestConstants.RESULTS_ROOT;
import static oracle.weblogic.kubernetes.actions.ActionConstants.WIT_BUILD_DIR;
import static oracle.weblogic.kubernetes.actions.ActionConstants.WIT_JAVA_HOME;
import static oracle.weblogic.kubernetes.actions.TestActions.createAuxImage;
import static oracle.weblogic.kubernetes.actions.TestActions.createAuxImageAndReturnResult;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.imageExists;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.testUntil;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.withStandardRetryPolicy;
import static oracle.weblogic.kubernetes.utils.FileUtils.checkDirectory;
import static oracle.weblogic.kubernetes.utils.FileUtils.copyFileFromPod;
import static oracle.weblogic.kubernetes.utils.ImageUtils.imageRepoLoginAndPushImageToRegistry;
import static oracle.weblogic.kubernetes.utils.ThreadSafeLogger.getLogger;
import static org.apache.commons.io.FileUtils.deleteQuietly;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

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
   * Create a AuxImage and push it to repository.
   *
   * @param imageName image name
   * @param archiveList app archive
   * @param modelList model list
   *
   */
  public static void createPushAuxiliaryImageWithDomainConfig(String imageName, String imageTag,
                                                              List<String> archiveList,
                                                              List<String> modelList) {

    // admin/managed server name here should match with model yaml
    WitParams witParams =
        new WitParams()
            .modelImageName(imageName)
            .modelImageTag(imageTag)
            .modelFiles(modelList)
            .modelArchiveFiles(archiveList);
    createAndPushAuxiliaryImage(imageName, imageTag, witParams);
  }

  /**
   * Create a AuxImage and push it to repository.
   *
   * @param imageName image name
   * @param wdtVersion wdt version
   *
   */
  public static void createPushAuxiliaryImageWithWDTInstallOnly(String imageName, String imageTag,
                                                                String wdtVersion) {

    WitParams witParams =
        new WitParams()
            .modelImageName(imageName)
            .modelImageTag(imageTag)
            .wdtModelOnly(true)
            .wdtVersion(wdtVersion);
    createAndPushAuxiliaryImage(imageName, imageTag, witParams);
  }


  /**
   * Create a AuxImage and push it to repository.
   *
   * @param imageName image name
   * @param witParams wit params
   *
   */
  public static void createAndPushAuxiliaryImage(String imageName, String imageTag, WitParams witParams) {
    // create auxiliary image using imagetool command if does not exists
    LoggingFacade logger = getLogger();
    if (! imageExists(imageName, imageTag)) {
      logger.info("creating auxiliary image {0}:{1} using imagetool.sh ", imageName, imageTag);
      testUntil(
          withStandardRetryPolicy,
          createAuxiliaryImage(witParams),
          logger,
          "createAuxImage to be successful");
    } else {
      logger.info("!!!! auxiliary image {0}:{1} exists !!!!", imageName, imageTag);
    }

    // push image to repo for multi node cluster
    if (!DOMAIN_IMAGES_REPO.isEmpty()) {
      logger.info("push image {0} to registry {1}", imageName, DOMAIN_IMAGES_REPO);
      imageRepoLoginAndPushImageToRegistry(imageName + ":" + imageTag);
    }
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
    if (WIT_JAVA_HOME != null) {
      env.put("JAVA_HOME", WIT_JAVA_HOME);
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
  public static Callable<Boolean> createAuxiliaryImage(String auxImage, String imageTag, List<String> modelList,
                                                       List<String> archiveList) {

    return (() -> {
      WitParams witParams =
          new WitParams()
              .modelImageName(auxImage)
              .modelImageTag(imageTag)
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

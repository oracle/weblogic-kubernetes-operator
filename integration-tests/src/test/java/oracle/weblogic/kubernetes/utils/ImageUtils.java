// Copyright (c) 2021, 2024, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes.utils;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import com.google.gson.JsonObject;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1Secret;
import io.kubernetes.client.openapi.models.V1SecretList;
import oracle.weblogic.kubernetes.TestConstants;
import oracle.weblogic.kubernetes.actions.impl.AppParams;
import oracle.weblogic.kubernetes.actions.impl.Namespace;
import oracle.weblogic.kubernetes.actions.impl.primitive.Image;
import oracle.weblogic.kubernetes.actions.impl.primitive.WitParams;
import oracle.weblogic.kubernetes.logging.LoggingFacade;

import static oracle.weblogic.kubernetes.TestConstants.BASE_IMAGES_REPO_EMAIL;
import static oracle.weblogic.kubernetes.TestConstants.BASE_IMAGES_REPO_PASSWORD;
import static oracle.weblogic.kubernetes.TestConstants.BASE_IMAGES_REPO_SECRET_NAME;
import static oracle.weblogic.kubernetes.TestConstants.BASE_IMAGES_REPO_USERNAME;
import static oracle.weblogic.kubernetes.TestConstants.DOMAIN_IMAGES_PREFIX;
import static oracle.weblogic.kubernetes.TestConstants.DOMAIN_IMAGES_REPO;
import static oracle.weblogic.kubernetes.TestConstants.OKD;
import static oracle.weblogic.kubernetes.TestConstants.TEST_IMAGES_REPO;
import static oracle.weblogic.kubernetes.TestConstants.TEST_IMAGES_REPO_EMAIL;
import static oracle.weblogic.kubernetes.TestConstants.TEST_IMAGES_REPO_PASSWORD;
import static oracle.weblogic.kubernetes.TestConstants.TEST_IMAGES_REPO_SECRET_NAME;
import static oracle.weblogic.kubernetes.TestConstants.TEST_IMAGES_REPO_USERNAME;
import static oracle.weblogic.kubernetes.TestConstants.WDT_IMAGE_DOMAINHOME_BASE_DIR;
import static oracle.weblogic.kubernetes.TestConstants.WEBLOGIC_IMAGE_NAME;
import static oracle.weblogic.kubernetes.TestConstants.WEBLOGIC_IMAGE_TAG;
import static oracle.weblogic.kubernetes.TestConstants.WLSIMG_BUILDER;
import static oracle.weblogic.kubernetes.actions.ActionConstants.ARCHIVE_DIR;
import static oracle.weblogic.kubernetes.actions.ActionConstants.MODEL_DIR;
import static oracle.weblogic.kubernetes.actions.ActionConstants.WDT_VERSION;
import static oracle.weblogic.kubernetes.actions.ActionConstants.WIT_BUILD_DIR;
import static oracle.weblogic.kubernetes.actions.ActionConstants.WIT_JAVA_HOME;
import static oracle.weblogic.kubernetes.actions.ActionConstants.WLS;
import static oracle.weblogic.kubernetes.actions.ActionConstants.WORK_DIR;
import static oracle.weblogic.kubernetes.actions.TestActions.archiveApp;
import static oracle.weblogic.kubernetes.actions.TestActions.buildAppArchive;
import static oracle.weblogic.kubernetes.actions.TestActions.buildCoherenceArchive;
import static oracle.weblogic.kubernetes.actions.TestActions.createImage;
import static oracle.weblogic.kubernetes.actions.TestActions.createImageBuilderConfigJson;
import static oracle.weblogic.kubernetes.actions.TestActions.createNamespace;
import static oracle.weblogic.kubernetes.actions.TestActions.createSecret;
import static oracle.weblogic.kubernetes.actions.TestActions.defaultAppParams;
import static oracle.weblogic.kubernetes.actions.TestActions.imagePush;
import static oracle.weblogic.kubernetes.actions.TestActions.imageRepoLogin;
import static oracle.weblogic.kubernetes.actions.impl.primitive.Kubernetes.listSecrets;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.doesImageExist;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.getDateAndTimeStamp;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.testUntil;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.withStandardRetryPolicy;
import static oracle.weblogic.kubernetes.utils.FileUtils.checkDirectory;
import static oracle.weblogic.kubernetes.utils.ThreadSafeLogger.getLogger;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ImageUtils {

  /**
   * Create an image for a model in image domain.
   *
   * @param miiImageNameBase the base mii image name used in local or to construct the image name in repository
   * @param wdtModelFile the WDT model file used to build the image
   * @param appName the sample application name used to build sample app ear file in WDT model file
   * @return image name with tag
   */
  public static  String createMiiImageAndVerify(String miiImageNameBase,
                                                String wdtModelFile,
                                                String appName) {
    return createMiiImageAndVerify(miiImageNameBase, wdtModelFile, appName,
        WEBLOGIC_IMAGE_NAME, WEBLOGIC_IMAGE_TAG, WLS);
  }

  /**
   * Create an image for a model in image domain.
   *
   * @param miiImageNameBase the base mii image name used in local or to construct the image name in repository
   * @param wdtModelFile the WDT model file used to build the image
   * @param appName the sample application name used to build sample app ear file in WDT model file
   * @param additionalBuildCommands - Path to a file with additional build commands
   * @param additionalBuildFilesVarargs - Additional files that are required by your additionalBuildCommands
   * @return image name with tag
   */
  public static  String createMiiImageAndVerify(String miiImageNameBase,
                                                String wdtModelFile,
                                                String appName,
                                                String additionalBuildCommands,
                                                String... additionalBuildFilesVarargs) {
    // build the model file list
    final List<String> modelList = Collections.singletonList(MODEL_DIR + "/" + wdtModelFile);
    final List<String> appSrcDirList = Collections.singletonList(appName);

    return createImageAndVerify(
        miiImageNameBase, modelList, appSrcDirList, null, WEBLOGIC_IMAGE_NAME,
        WEBLOGIC_IMAGE_TAG, WLS, true, null, false,
        additionalBuildCommands, additionalBuildFilesVarargs);
  }

  /**
   * Create an image for a model in image domain.
   *
   * @param miiImageNameBase the base mii image name used in local or to construct the image name in repository
   * @param wdtModelFile the WDT model file used to build the image
   * @param appName the sample application name used to build sample app ear file in WDT model file
   * @param baseImageName the WebLogic base image name to be used while creating mii image
   * @param baseImageTag the WebLogic base image tag to be used while creating mii image
   * @param domainType the type of the WebLogic domain, valid values are "WLS, "JRF", and "Restricted JRF"
   * @return image name with tag
   */
  public static  String createMiiImageAndVerify(String miiImageNameBase,
                                                String wdtModelFile,
                                                String appName,
                                                String baseImageName,
                                                String baseImageTag,
                                                String domainType) {
    // build the model file list
    final String modelFile =
        wdtModelFile.contains(WORK_DIR) ? wdtModelFile : MODEL_DIR + "/" + wdtModelFile;
    final List<String> modelList = Collections.singletonList(modelFile);
    final List<String> appSrcDirList = Collections.singletonList(appName);

    return createMiiImageAndVerify(
        miiImageNameBase, modelList, appSrcDirList, baseImageName, baseImageTag, domainType, true);
  }

  /**
   * Create an image for a model in image domain using multiple WDT model files and application ear files.
   *
   * @param miiImageNameBase the base mii image name used in local or to construct the image name in repository
   * @param wdtModelList list of WDT model files used to build the image
   * @param appSrcDirList list of the sample application source directories used to build sample app ear files
   * @return image name with tag
   */
  public static  String createMiiImageAndVerify(String miiImageNameBase,
                                                List<String> wdtModelList,
                                                List<String> appSrcDirList) {
    return createMiiImageAndVerify(
        miiImageNameBase, wdtModelList, appSrcDirList, WEBLOGIC_IMAGE_NAME, WEBLOGIC_IMAGE_TAG, WLS, true);

  }

  /**
   * Create an image for a model in image domain using multiple WDT model files and application ear files.
   *
   * @param miiImageNameBase the base mii image name used in local or to construct the image name in repository
   * @param wdtModelList list of WDT model files used to build the image
   * @param appSrcDirList list of the sample application source directories used to build sample app ear files
   * @param baseImageName the WebLogic base image name to be used while creating mii image
   * @param baseImageTag the WebLogic base image tag to be used while creating mii image
   * @param domainType the type of the WebLogic domain, valid values are "WLS, "JRF", and "Restricted JRF"
   * @param oneArchiveContainsMultiApps whether one archive contains multiple apps
   * @return image name with tag
   */
  public static String createMiiImageAndVerify(String miiImageNameBase,
                                               List<String> wdtModelList,
                                               List<String> appSrcDirList,
                                               String baseImageName,
                                               String baseImageTag,
                                               String domainType,
                                               boolean oneArchiveContainsMultiApps) {

    return createImageAndVerify(
        miiImageNameBase, wdtModelList, appSrcDirList, null, baseImageName,
        baseImageTag, domainType, true, null, oneArchiveContainsMultiApps);
  }

  /**
   * Create an image for a domain-in-image domain.
   *
   * @param domainUid domain uid
   * @param domainNamespace domain namespace
   * @param imageNameBase the base image name used in local or to construct the image name in repository
   * @param modelFile the model file used to build the image
   * @param modelPropFile property file to be used with the model file above
   * @param appName - application source directories used to build app ear files
   * @return image name with tag
   */
  public static String createDiiImageAndVerify(String domainUid,
                                               String domainNamespace,
                                               String imageNameBase,
                                               String modelFile,
                                               String modelPropFile,
                                               String appName) {
    // create image with model files
    String domImage = createImageAndVerify(imageNameBase, modelFile, appName, modelPropFile, domainUid);

    // repo login and push image to repo registry if necessary
    imageRepoLoginAndPushImageToRegistry(domImage);

    // create repo registry secret to pull the image from registry
    // this secret is used only for non-kind cluster
    createTestRepoSecret(domainNamespace);

    return domImage;
  }

  /**
   * Create an image with modelfile, application archive and property file.If the property file
 is needed to be updated with a property that has been created by the framework, it is copied
 onto RESULT_ROOT and updated. Hence the altModelDir. Call this method to create a domain home in image.
   * @param imageNameBase - base image name used in local or to construct image name in repository
   * @param wdtModelList - model file used to build the image
   * @param appSrcDirList - application to be added to the image
   * @param modelPropFile - property file to be used with the model file above
   * @param altModelDir - directory where the property file is found if not in the default MODEL_DIR
   * @param domainUid domain uid
   * @return image name with tag
   */
  public static String createImageAndVerify(String imageNameBase,
                                            List<String> wdtModelList,
                                            List<String> appSrcDirList,
                                            String modelPropFile,
                                            String altModelDir,
                                            String domainUid) {

    final List<String> modelPropList = Collections.singletonList(altModelDir + "/" + modelPropFile);

    return createImageAndVerify(
        imageNameBase, wdtModelList, appSrcDirList, modelPropList, WEBLOGIC_IMAGE_NAME,
        WEBLOGIC_IMAGE_TAG, WLS, false, domainUid, false);
  }

  /**
   * Create an image from the wdt model, application archives and property file. Call this method
   * to create a domain home in image.
   * @param imageNameBase - base image name used in local or to construct image name in repository
   * @param wdtModelFile - model file used to build the image
   * @param appName - application to be added to the image
   * @param modelPropFile - property file to be used with the model file above
   * @return image name with tag
   */
  public static String createImageAndVerify(String imageNameBase,
                                            String wdtModelFile,
                                            String appName,
                                            String modelPropFile,
                                            String domainHome) {

    final List<String> wdtModelList = Collections.singletonList(MODEL_DIR + "/" + wdtModelFile);
    final List<String> appSrcDirList = Collections.singletonList(appName);
    final List<String> modelPropList = Collections.singletonList(MODEL_DIR + "/" + modelPropFile);

    return createImageAndVerify(
        imageNameBase, wdtModelList, appSrcDirList, modelPropList, WEBLOGIC_IMAGE_NAME,
        WEBLOGIC_IMAGE_TAG, WLS, false, domainHome, false);
  }

  /**
   * Create an image for a model in image domain or domain home in image using multiple WDT model
   * files and application ear files.
   * @param imageNameBase - the base mii image name used in local or to construct the image name in repository
   * @param wdtModelList - list of WDT model files used to build the image
   * @param appSrcDirList - list of the sample application source directories used to build sample app ear files
   * @param modelPropList - the WebLogic base image name to be used while creating mii image
   * @param baseImageName - the WebLogic base image name to be used while creating mii image
   * @param baseImageTag - the WebLogic base image tag to be used while creating mii image
   * @param domainType - the type of the WebLogic domain, valid values are "WLS, "JRF", and "Restricted JRF"
   * @param modelType - create a model image only or domain in image. set to true for MII
   * @param domainHome - the domain home in the image
   * @param oneArchiveContainsMultiApps - whether one archive contains multiple apps
   * @return image name with tag
   */
  public static String createImageAndVerify(String imageNameBase,
                                            List<String> wdtModelList,
                                            List<String> appSrcDirList,
                                            List<String> modelPropList,
                                            String baseImageName,
                                            String baseImageTag,
                                            String domainType,
                                            boolean modelType,
                                            String domainHome,
                                            boolean oneArchiveContainsMultiApps) {
    return createImageAndVerify(
        imageNameBase, wdtModelList, appSrcDirList, modelPropList, baseImageName, baseImageTag, domainType,
        modelType, domainHome, oneArchiveContainsMultiApps, null);
  }

  /**
   * Create an image for a model in image domain or domain home in image using multiple WDT model
   * files and application ear files.
   * @param imageNameBase - the base mii image name used in local or to construct the image name in repository
   * @param wdtModelList - list of WDT model files used to build the image
   * @param appSrcDirList - list of the sample application source directories used to build sample app ear files
   * @param modelPropList - the WebLogic base image name to be used while creating mii image
   * @param baseImageName - the WebLogic base image name to be used while creating mii image
   * @param baseImageTag - the WebLogic base image tag to be used while creating mii image
   * @param domainType - the type of the WebLogic domain, valid values are "WLS, "JRF", and "Restricted JRF"
   * @param modelType - create a model image only or domain in image. set to true for MII
   * @param additionalBuildCommands - Path to a file with additional build commands
   * @param additionalBuildFilesVarargs -Additional files that are required by your additionalBuildCommands
   * @return image name with tag
   */
  public static String createImageAndVerify(String imageNameBase,
                                            List<String> wdtModelList,
                                            List<String> appSrcDirList,
                                            List<String> modelPropList,
                                            String baseImageName,
                                            String baseImageTag,
                                            String domainType,
                                            boolean modelType,
                                            String domainHome,
                                            boolean oneArchiveContainsMultiApps,
                                            String additionalBuildCommands,
                                            String... additionalBuildFilesVarargs) {

    LoggingFacade logger = getLogger();

    // create unique image name with date
    DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
    Date date = new Date();
    final String imageTag = baseImageTag + "-" + dateFormat.format(date) + "-" + System.currentTimeMillis();
    // Add repository name in image name for Jenkins runs
    final String imageName = DOMAIN_IMAGES_PREFIX + imageNameBase;
    final String image = imageName + ":" + imageTag;

    // Generates a "unique" name by choosing a random name from
    // 26^4 possible combinations.
    Random random = new Random(System.currentTimeMillis());
    char[] cacheSfx = new char[4];
    for (int i = 0; i < cacheSfx.length; i++) {
      cacheSfx[i] = (char) (random.nextInt(25) + (int) 'a');
    }

    List<String> archiveList = new ArrayList<>();
    if (appSrcDirList != null && appSrcDirList.size() != 0 && appSrcDirList.get(0) != null) {
      List<String> archiveAppsList = new ArrayList<>();
      List<String> buildAppDirList = new ArrayList<>(appSrcDirList);
      boolean buildCoherence = false;

      for (String appSrcDir : appSrcDirList) {
        if (appSrcDir.contains(".war") || appSrcDir.contains(".ear") || appSrcDir.contains(".jar")) {
          //remove from build
          buildAppDirList.remove(appSrcDir);
          archiveAppsList.add(appSrcDir);
        }

        if (appSrcDir.contains("coherence-proxy") || appSrcDir.contains("CoherenceApp")) {
          buildCoherence = true;
        }
      }

      AppParams appParams = defaultAppParams().appArchiveDir(ARCHIVE_DIR + cacheSfx);

      if (archiveAppsList.size() != 0 && archiveAppsList.get(0) != null) {
        assertTrue(archiveApp(appParams.srcDirList(archiveAppsList)));
        String appPath = archiveAppsList.get(0);

        //archive provided ear or war file
        String appName = appPath.substring(appPath.lastIndexOf("/") + 1,
            appPath.lastIndexOf("."));

        // build the archive list
        String zipAppFile = String.format("%s/%s.zip", appParams.appArchiveDir(), appName);
        archiveList.add(zipAppFile);
      }

      if (buildAppDirList.size() != 0 && buildAppDirList.get(0) != null) {
        // build an application archive using what is in resources/apps/APP_NAME
        String zipFile;
        if (oneArchiveContainsMultiApps) {
          for (String buildAppDirs : buildAppDirList) {
            assertTrue(buildAppArchive(appParams
                    .srcDirList(Collections.singletonList(buildAppDirs))
                    .appName(buildAppDirs)),
                String.format("Failed to create app archive for %s", buildAppDirs));
            zipFile = String.format("%s/%s.zip", appParams.appArchiveDir(), buildAppDirs);
            // build the archive list
            archiveList.add(zipFile);
          }
        } else if (buildCoherence) {
          // build the Coherence GAR file
          assertTrue(buildCoherenceArchive(appParams.srcDirList(buildAppDirList)),
              String.format("Failed to create app archive for %s", buildAppDirList.get(0)));
          zipFile = String.format("%s/%s.zip", appParams.appArchiveDir(), buildAppDirList.get(0));
          // build the archive list
          archiveList.add(zipFile);
        } else {
          for (String appName : buildAppDirList) {
            assertTrue(buildAppArchive(appParams
                    .srcDirList(Collections.singletonList(appName))
                    .appName(appName)),
                String.format("Failed to create app archive for %s", appName));
            zipFile = String.format("%s/%s.zip", appParams.appArchiveDir(), appName);
            // build the archive list
            archiveList.add(zipFile);
          }
        }
      }
    }

    // Set additional environment variables for WIT
    String cacheDir = WIT_BUILD_DIR + "/cache-" + new String(cacheSfx);
    logger.info("WLSIMG_CACHEDIR is set to {0}", cacheDir);
    logger.info("WLSIMG_BLDDIR is set to {0}", WIT_BUILD_DIR);

    checkDirectory(WIT_BUILD_DIR);
    checkDirectory(cacheDir);
    Map<String, String> env = new HashMap<>();
    env.put("WLSIMG_BLDDIR", WIT_BUILD_DIR);
    env.put("WLSIMG_CACHEDIR", cacheDir);

    // For k8s 1.16 support and as of May 6, 2020, we presently need a different JDK for these
    // tests and for image tool. This is expected to no longer be necessary once JDK 11.0.8 or
    // the next JDK 14 versions are released.
    if (WIT_JAVA_HOME != null) {
      env.put("JAVA_HOME", WIT_JAVA_HOME);
    }

    String witTarget = ((OKD) ? "OpenShift" : "Default");
    // build an image using WebLogic Image Tool
    logger.info("Creating image {0} using model directory {1}", image, MODEL_DIR);
    boolean result = false;

    if (!modelType) {  //create a domain home in image image
      WitParams witParams = new WitParams()
          .baseImageName(baseImageName)
          .baseImageTag(baseImageTag)
          .domainType(domainType)
          .modelImageName(imageName)
          .modelImageTag(imageTag)
          .modelFiles(wdtModelList)
          .modelVariableFiles(modelPropList)
          .modelArchiveFiles(archiveList)
          .domainHome(WDT_IMAGE_DOMAINHOME_BASE_DIR + "/" + domainHome)
          .wdtModelOnly(modelType)
          .wdtOperation("CREATE")
          .wdtVersion(WDT_VERSION)
          .target(witTarget)
          .env(env)
          .redirect(true);

      testUntil(
          withStandardRetryPolicy,
          () -> createImage(witParams),
          getLogger(),
          "creating image {0}:{1} succeeds",
          imageName,
          imageTag);

      result = true;
    } else {
      WitParams witParams = new WitParams()
          .baseImageName(baseImageName)
          .baseImageTag(baseImageTag)
          .domainType(domainType)
          .modelImageName(imageName)
          .modelImageTag(imageTag)
          .modelFiles(wdtModelList)
          .modelVariableFiles(modelPropList)
          .modelArchiveFiles(archiveList)
          .wdtModelOnly(modelType)
          .wdtVersion(WDT_VERSION)
          .target(witTarget)
          .env(env)
          .redirect(true);

      if (additionalBuildCommands != null) {
        logger.info("additionalBuildCommands {0}", additionalBuildCommands);
        witParams.additionalBuildCommands(additionalBuildCommands);
        StringBuffer additionalBuildFilesBuff = new StringBuffer();
        for (String buildFile:additionalBuildFilesVarargs) {
          additionalBuildFilesBuff.append(buildFile).append(" ");
        }

        witParams.additionalBuildFiles(additionalBuildFilesBuff.toString().trim());
      }

      if (OKD) {
        witParams.target("OpenShift");
      }

      testUntil(
          withStandardRetryPolicy,
          () -> createImage(witParams),
          getLogger(),
          "creating image {0}:{1} succeeds",
          imageName,
          imageTag);

      result = true;
    }

    assertTrue(result, String.format("Failed to create the image %s using WebLogic Image Tool", image));

    // Check image exists using 'WLSIMG_BUILDER images | grep image tag'.
    assertTrue(doesImageExist(imageTag),
        String.format("Image %s does not exist", image));

    logger.info("Image {0} are created successfully", image);
    return image;
  }

  /**
   * Create a registry secret in the specified namespace.
   * for BASE_IMAGES_REPO to download base images.
   *
   * @param namespace the namespace in which the secret will be created
   */
  public static void createBaseRepoSecret(String namespace) {
    LoggingFacade logger = getLogger();
    logger.info("Creating base image pull secret {0} in namespace {1}", BASE_IMAGES_REPO_SECRET_NAME, namespace);
    createImageRegistrySecret(BASE_IMAGES_REPO_USERNAME, BASE_IMAGES_REPO_PASSWORD, BASE_IMAGES_REPO_EMAIL,
            TestConstants.BASE_IMAGES_REPO, BASE_IMAGES_REPO_SECRET_NAME, namespace);
  }

  /**
   * Create a registry secret in the specified namespace.
   * for TEST_IMAGES_REPO to upload/download test images.
   * @param namespace the namespace in which the secret will be created
   */
  public static void createTestRepoSecret(String namespace) {
    LoggingFacade logger = getLogger();
    logger.info("Creating test image pull secret {0} in namespace {1}", TEST_IMAGES_REPO_SECRET_NAME, namespace);
    createImageRegistrySecret(TEST_IMAGES_REPO_USERNAME,
            TEST_IMAGES_REPO_PASSWORD, TEST_IMAGES_REPO_EMAIL,
            TEST_IMAGES_REPO, TEST_IMAGES_REPO_SECRET_NAME, namespace);
  }

  /**
   * Create repo registry secret with given parameters.
   * @param userName repository user name
   * @param password repository password
   * @param email repository email
   * @param registry registry name
   * @param secretName name of the secret to create
   * @param namespace namespace in which to create the secret
   */
  public static void createImageRegistrySecret(String userName, String password,
                                                String email, String registry, String secretName, String namespace) {
    LoggingFacade logger = getLogger();
    // Create registry secret in the namespace to pull the image from repository
    JsonObject configJsonObject = createImageBuilderConfigJson(
        userName, password, email, registry);
    String configJsonString = configJsonObject.toString();

    // skip if the secret already exists
    V1SecretList listSecrets = listSecrets(namespace);
    if (listSecrets != null) {
      for (V1Secret item : listSecrets.getItems()) {
        if (item.getMetadata() != null && item.getMetadata().getName() != null
            && item.getMetadata().getName().equals(secretName)) {
          logger.info("Secret {0} already exists in namespace {1}, skipping secret creation", secretName, namespace);
          return;
        }
      }
    }

    // Create the V1Secret configuration
    V1Secret repoSecret = new V1Secret()
        .metadata(new V1ObjectMeta()
            .name(secretName)
            .namespace(namespace))
        .type("kubernetes.io/dockerconfigjson")
        .putDataItem(".dockerconfigjson", configJsonString.getBytes());

    boolean secretCreated = assertDoesNotThrow(() -> createSecret(repoSecret),
        String.format("createSecret failed for %s", secretName));
    assertTrue(secretCreated, String.format("createSecret failed while creating secret %s in namespace %s",
        secretName, namespace));
  }

  /**
   * Repo login and push the image to registry.
   *
   * @param image the image to push to registry
   */
  public static void imageRepoLoginAndPushImageToRegistry(String image) {
    LoggingFacade logger = getLogger();
    // push image, if necessary
    if (!DOMAIN_IMAGES_REPO.isEmpty() && image.contains(DOMAIN_IMAGES_REPO)) {
      // repo login, if necessary
      logger.info(WLSIMG_BUILDER + " login");
      assertTrue(imageRepoLogin(TestConstants.BASE_IMAGES_REPO,
           BASE_IMAGES_REPO_USERNAME, BASE_IMAGES_REPO_PASSWORD),  WLSIMG_BUILDER + " login failed");
      logger.info(WLSIMG_BUILDER + " push image {0} to {1}", image, DOMAIN_IMAGES_REPO);
      testUntil(() -> imagePush(image),
          logger,
          WLSIMG_BUILDER + " push succeeds for image {0} to repo {1}",
          image,
          DOMAIN_IMAGES_REPO);
    }
  }

  /**
   * Build image with unique name, create corresponding repo secret and push to registry.
   *
   * @param dockerFileDir directory where dockerfile is located
   * @param baseImageName base image name
   * @param namespace image namespace
   * @param secretName repo secretname for image
   * @param extraImageBuilderArgs user specified extra args
   * @return image name
   */
  public static String createImageAndPushToRepo(String dockerFileDir, String baseImageName,
                                                String namespace, String secretName,
                                                String extraImageBuilderArgs) throws ApiException {
    // create unique image name with date
    final String imageTag = getDateAndTimeStamp();
    // Add repository name in image name for Jenkins runs
    final String imageName = DOMAIN_IMAGES_PREFIX + baseImageName;

    final String image = imageName + ":" + imageTag;
    LoggingFacade logger = getLogger();
    //build image
    assertTrue(
        Image.createImage(dockerFileDir, image, extraImageBuilderArgs),
        "Failed to create image " + baseImageName);
    logger.info("image is created with name {0}", image);
    if (!Namespace.exists(namespace)) {
      createNamespace(namespace);
    }

    //create registry secret
    createImageRegistrySecret(TEST_IMAGES_REPO_USERNAME, TEST_IMAGES_REPO_PASSWORD, TEST_IMAGES_REPO_EMAIL,
            TestConstants.TEST_IMAGES_REPO, secretName, namespace);
    // docker login and push image to docker registry if necessary
    imageRepoLoginAndPushImageToRegistry(image);

    return image;
  }
}

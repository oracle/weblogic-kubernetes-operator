// Copyright (c) 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes.utils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Map;

import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.models.V1Container;
import io.kubernetes.client.openapi.models.V1EnvVar;
import io.kubernetes.client.openapi.models.V1LocalObjectReference;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1Pod;
import io.kubernetes.client.openapi.models.V1PodSpec;
import io.kubernetes.client.openapi.models.V1Secret;
import io.kubernetes.client.openapi.models.V1SecretList;
import io.kubernetes.client.util.exception.CopyNotSupportedException;
import oracle.weblogic.kubernetes.TestConstants;
import oracle.weblogic.kubernetes.actions.impl.Exec;
import oracle.weblogic.kubernetes.actions.impl.primitive.Kubernetes;
import oracle.weblogic.kubernetes.logging.LoggingFacade;
import org.awaitility.core.ConditionFactory;

import static java.util.concurrent.TimeUnit.MINUTES;
import static java.util.concurrent.TimeUnit.SECONDS;
import static oracle.weblogic.kubernetes.TestConstants.KIND_REPO;
import static oracle.weblogic.kubernetes.TestConstants.OCR_EMAIL;
import static oracle.weblogic.kubernetes.TestConstants.OCR_PASSWORD;
import static oracle.weblogic.kubernetes.TestConstants.OCR_REGISTRY;
import static oracle.weblogic.kubernetes.TestConstants.OCR_SECRET_NAME;
import static oracle.weblogic.kubernetes.TestConstants.OCR_USERNAME;
import static oracle.weblogic.kubernetes.actions.ActionConstants.RESOURCE_DIR;
import static oracle.weblogic.kubernetes.actions.ActionConstants.WLS_BASE_IMAGE_NAME;
import static oracle.weblogic.kubernetes.actions.ActionConstants.WLS_BASE_IMAGE_TAG;
import static oracle.weblogic.kubernetes.actions.ActionConstants.WORK_DIR;
import static oracle.weblogic.kubernetes.actions.impl.primitive.Kubernetes.listSecrets;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.podReady;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.verifyDefaultTokenExists;
import static oracle.weblogic.kubernetes.utils.ThreadSafeLogger.getLogger;
import static org.apache.commons.io.FileUtils.copyDirectory;
import static org.apache.commons.io.FileUtils.deleteDirectory;
import static org.awaitility.Awaitility.with;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;


/**
 * Utility class to build application.
 */
public class BuildApplication {

  private static String image;
  private static boolean isUseSecret;
  private static final String APPLICATIONS_PATH = "/u01/application";
  private static final String BUILD_SCRIPT = "build_application.sh";
  private static final Path BUILD_SCRIPT_SOURCE_PATH = Paths.get(RESOURCE_DIR, "bash-scripts", BUILD_SCRIPT);

  /**
   * Build application.
   *
   * <p>The appSrcPath, your application source directory is zipped up and copied to a WebLogic server pod for building.
   * If your archives are placed under &lt application_source &gt /build after building, use <b>build</b> as the
   * archiveDistDir param value. This method copies the folder <b>archiveDistDir</b> to local file system and absolute
   * path of the <b>archiveDistDir</b> directory is returned by this method. In your It test, assert the
   * application exists in the local <b>archiveDistDir</b> before proceeding with the test.
   * <p> Example Usage: </p>

   * <pre>{@literal
   *     HashMap <String,String> antParams = new HashMap<>();
   *     antParams.put("key","value");
   *
   *     Path archivesDir = BuildApplication.buildApplication(
   *         "/scratch/speriyat/weblogic-kubernetes-operator/new-integration-tests/src/test/resources/apps/clusterview",
   *         antParams,
   *         "clean build all" // these targets must be supported by your build file
   *         "build" // the directory your build system creates
   *         "ns-abcd" // your domain or operator namespace, so that the pods are cleaned up after test is done
   *     );
   *     }
   * The returned archivesDir location will be -
   * WORK_DIR/<b>your_application_name</b>/u01/application/<b>archiveDistDir</b>
   * All your built archives will be under the above directory
   * Example: /tmp/it-results/clusterview/u01/application/dist/clusterview.war
   * </pre>
   * </p>
   *
   * @param appSrcPath path of the application source folder
   * @param antParams ant parameters
   * @param antTargets ant targets to call
   * @param archiveDistDir location of the archive built inside source directory
   * @param namespace name of the namespace to create the pod in
   * @return Path path of the archive built
   */
  public static Path buildApplication(Path appSrcPath, Map<String, String> antParams,
                                      String antTargets, String archiveDistDir, String namespace) {
    final LoggingFacade logger = getLogger();
    setImage(namespace);

    // Path of temp location for application source directory
    Path tempAppPath = Paths.get(WORK_DIR, "j2eeapplications", appSrcPath.getFileName().toString());
    // directory to copy archives built
    Path destArchiveBaseDir = Paths.get(WORK_DIR, appSrcPath.getFileName().toString());
    Path destDir = null;

    assertDoesNotThrow(() -> {
      // recreate WORK_DIR/j2eeapplications/<application_directory_name>
      logger.info("Deleting and recreating {0}", tempAppPath);
      Files.createDirectories(tempAppPath);
      deleteDirectory(tempAppPath.toFile());
      Files.createDirectories(tempAppPath);

      Files.createDirectories(destArchiveBaseDir);
      deleteDirectory(destArchiveBaseDir.toFile());
      Files.createDirectories(destArchiveBaseDir);

      // copy the application source to WORK_DIR/j2eeapplications/<application_directory_name> for zipping
      logger.info("Copying {0} to {1}", appSrcPath, tempAppPath);
      copyDirectory(appSrcPath.toFile(), tempAppPath.toFile());
    });

    // zip up the application source to be copied to pod for building
    Path zipFile = Paths.get(FileUtils.createZipFile(tempAppPath));


    // add ant properties as env variable in pod
    V1Container buildContainer = new V1Container();

    // set ZIP_FILE location as env variable in pod
    buildContainer.addEnvItem(new V1EnvVar()
        .name("ZIP_FILE")
        .value(zipFile.getFileName().toString()));

    // set ant parameteres as env variable in pod
    if (antParams != null) {
      StringBuilder params = new StringBuilder();
      antParams.entrySet().forEach((parameter) -> {
        params.append("-D").append(parameter.getKey()).append("=").append(parameter.getValue()).append(" ");
      });
      buildContainer = buildContainer
          .addEnvItem(new V1EnvVar().name("sysprops").value(params.toString()));
    }

    // set add targets in env variable "targets"
    if (antTargets != null) {
      buildContainer = buildContainer
          .addEnvItem(new V1EnvVar().name("targets").value(antTargets));
    }

    //setup temporary WebLogic pod to build application
    V1Pod webLogicPod = setupWebLogicPod(namespace, buildContainer);

    try {
      //copy the zip file to /u01 location inside pod
      Kubernetes.copyFileToPod(namespace, webLogicPod.getMetadata().getName(),
          null, zipFile, Paths.get("/u01", zipFile.getFileName().toString()));
    } catch (ApiException | IOException  ioex) {
      logger.info("Exception while copying file " + zipFile + " to pod", ioex);
    }
    try {
      //copy the build script to /u01 location inside pod
      Kubernetes.copyFileToPod(namespace, webLogicPod.getMetadata().getName(),
          null, BUILD_SCRIPT_SOURCE_PATH, Paths.get("/u01", BUILD_SCRIPT));
    } catch (ApiException | IOException  ioex) {
      logger.info("Exception while copying file " + zipFile + " to pod", ioex);
    }
    try {
      //Kubernetes.exec(webLogicPod, new String[]{"/bin/sh", "/u01/" + BUILD_SCRIPT});
      ExecResult exec = Exec.exec(webLogicPod, null, false, "/bin/sh", "/u01/" + BUILD_SCRIPT);
      if (exec.stdout() != null) {
        logger.info("Exec stdout {0}", exec.stdout());
      }
      if (exec.stderr() != null) {
        logger.info("Exec stderr {0}", exec.stderr());
      }

      // Exec returns a non-zero return code intermittently even when the application builds
      // successfully. This seems to be an issue with io.kubernetes.client.Exec.java. So, Commenting
      // this assertion for now. it is now the responsibility of the It test class
      // to assert the application exists before continuing with the test.

      //assertEquals(0, exec.exitValue(), "Exec into " + webLogicPod.getMetadata().getName()
      //    + " to build an application failed with exit value " + exec.exitValue());

      Kubernetes.copyDirectoryFromPod(webLogicPod,
          Paths.get(APPLICATIONS_PATH, archiveDistDir).toString(), destArchiveBaseDir);
    } catch (ApiException | IOException | InterruptedException | CopyNotSupportedException ioex) {
      logger.info("Exception while copying file " + Paths.get(APPLICATIONS_PATH, archiveDistDir) + " from pod", ioex);
    }

    return destDir = Paths.get(destArchiveBaseDir.toString(), "u01/application", archiveDistDir);
  }



  /**
   * Create a temporary WebLogic pod to build j2ee applications.
   *
   * @param namespace name of the namespace in which to create the temporary pod
   * @return V1Pod created pod object
   * @throws ApiException when create pod fails
   */
  private static V1Pod setupWebLogicPod(String namespace, V1Container container) {
    final LoggingFacade logger = getLogger();
    ConditionFactory withStandardRetryPolicy = with().pollDelay(10, SECONDS)
        .and().with().pollInterval(2, SECONDS)
        .atMost(3, MINUTES).await();
    verifyDefaultTokenExists();

    final String podName = "weblogic-build-pod-" + namespace;
    V1Pod podBody = new V1Pod()
        .spec(new V1PodSpec()
            .containers(Arrays.asList(container
                .name("weblogic-container")
                .image(image)
                .imagePullPolicy("IfNotPresent")
                .addCommandItem("sleep")
                .addArgsItem("600")))
            .imagePullSecrets(isUseSecret
                ? Arrays.asList(new V1LocalObjectReference()
                .name(OCR_SECRET_NAME))
                : null)) // the persistent volume claim used by the test
        .metadata(new V1ObjectMeta().name(podName))
        .apiVersion("v1")
        .kind("Pod");
    V1Pod wlsPod = assertDoesNotThrow(() -> Kubernetes.createPod(namespace, podBody));

    withStandardRetryPolicy
        .conditionEvaluationListener(
            condition -> logger.info("Waiting for {0} to be ready in namespace {1}, "
                    + "(elapsed time {2} , remaining time {3}",
                podName,
                namespace,
                condition.getElapsedTimeInMS(),
                condition.getRemainingTimeInMS()))
        .until(podReady(podName, null, namespace));

    return wlsPod;
  }

  /**
   * Set the image to use and create secrets if needed.
   *
   * @param namespace namespace in which secrets needs to be created
   */
  private static void setImage(String namespace) {
    final LoggingFacade logger = getLogger();
    //determine if the tests are running in Kind cluster.
    //if true use images from Kind registry
    String ocrImage = WLS_BASE_IMAGE_NAME + ":" + WLS_BASE_IMAGE_TAG;
    if (KIND_REPO != null) {
      image = KIND_REPO + ocrImage.substring(TestConstants.OCR_REGISTRY.length() + 1);
      isUseSecret = false;
    } else {
      // create pull secrets for WebLogic image when running in non Kind Kubernetes cluster
      image = ocrImage;
      boolean secretExists = false;
      V1SecretList listSecrets = listSecrets(namespace);
      if (null != listSecrets) {
        for (V1Secret item : listSecrets.getItems()) {
          if (item.getMetadata().getName().equals(OCR_SECRET_NAME)) {
            secretExists = true;
            break;
          }
        }
      }
      if (!secretExists) {
        CommonTestUtils.createDockerRegistrySecret(OCR_USERNAME, OCR_PASSWORD,
            OCR_EMAIL, OCR_REGISTRY, OCR_SECRET_NAME, namespace);
      }
      isUseSecret = true;
    }
    logger.info("Using image {0}", image);
  }

}

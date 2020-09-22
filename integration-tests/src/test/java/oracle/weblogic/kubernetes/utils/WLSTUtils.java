// Copyright (c) 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes.utils;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.models.V1ConfigMapVolumeSource;
import io.kubernetes.client.openapi.models.V1Container;
import io.kubernetes.client.openapi.models.V1Job;
import io.kubernetes.client.openapi.models.V1JobCondition;
import io.kubernetes.client.openapi.models.V1JobSpec;
import io.kubernetes.client.openapi.models.V1LocalObjectReference;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1Pod;
import io.kubernetes.client.openapi.models.V1PodSpec;
import io.kubernetes.client.openapi.models.V1PodTemplateSpec;
import io.kubernetes.client.openapi.models.V1Secret;
import io.kubernetes.client.openapi.models.V1SecretList;
import io.kubernetes.client.openapi.models.V1Volume;
import io.kubernetes.client.openapi.models.V1VolumeMount;
import oracle.weblogic.kubernetes.TestConstants;
import oracle.weblogic.kubernetes.actions.impl.Namespace;
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
import static oracle.weblogic.kubernetes.actions.ActionConstants.WLS_BASE_IMAGE_NAME;
import static oracle.weblogic.kubernetes.actions.ActionConstants.WLS_BASE_IMAGE_TAG;
import static oracle.weblogic.kubernetes.actions.TestActions.createNamespacedJob;
import static oracle.weblogic.kubernetes.actions.TestActions.getJob;
import static oracle.weblogic.kubernetes.actions.TestActions.getPodLog;
import static oracle.weblogic.kubernetes.actions.TestActions.listPods;
import static oracle.weblogic.kubernetes.actions.TestActions.listSecrets;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.jobCompleted;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.createConfigMapFromFiles;
import static oracle.weblogic.kubernetes.utils.ThreadSafeLogger.getLogger;
import static org.awaitility.Awaitility.with;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * A utility class for WLST.
 */
public class WLSTUtils {

  private static String image;
  private static boolean isUseSecret;
  private static final String MOUNT_POINT = "/scripts";

  private static final ConditionFactory withStandardRetryPolicy
      = with().pollDelay(2, SECONDS)
      .and().with().pollInterval(10, SECONDS)
      .atMost(5, MINUTES).await();

  /**
   * Execute WLST script.
   *
   * @param wlstScript WLST script file path
   * @param domainProperties domain property file path
   * @param namespace namespace in which to run the job
   */
  public static void executeWLSTScript(Path wlstScript, Path domainProperties, String namespace) {
    final LoggingFacade logger = getLogger();
    setImage(namespace);

    String wlstScriptFileName = wlstScript.getFileName().toString();
    String wlstPropertiesFile = domainProperties.getFileName().toString();

    logger.info("Creating a config map to hold WLST script files");
    String uniqueName = Namespace.uniqueName();
    String wlstScriptConfigMapName = "wlst-scripts-cm-" + uniqueName;
    String wlstJobName = "wlst-job-" + uniqueName;

    createConfigMapFromFiles(wlstScriptConfigMapName,
        Arrays.asList(wlstScript, domainProperties), namespace);

    logger.info("Preparing to run WLST job");
    // create a V1Container with specific scripts and properties for running WLST script
    V1Container jobCreationContainer = new V1Container()
        .addCommandItem("/bin/sh")
        .addArgsItem("/u01/oracle/oracle_common/common/bin/wlst.sh")
        .addArgsItem(MOUNT_POINT + "/" + wlstScriptFileName) //WLST py script
        .addArgsItem("-skipWLSModuleScanning")
        .addArgsItem("-loadProperties")
        .addArgsItem(MOUNT_POINT + "/" + wlstPropertiesFile); //WLST property file

    assertDoesNotThrow(()
        -> createWLSTJob(wlstJobName, wlstScriptConfigMapName, namespace, jobCreationContainer),
        "Online WLST execution failed");
  }

  /**
   * Create a job to execute WLST script.
   *
   * @param wlstJobName a unique job name
   * @param wlstScriptConfigMapName configmap holding WLST script file
   * @param namespace name of the namespace in which the job is created
   * @param jobContainer V1Container with job commands to execute WLST script
   * @throws ApiException when Kubernetes cluster query fails
   */
  public static void createWLSTJob(String wlstJobName, String wlstScriptConfigMapName, String namespace,
                                   V1Container jobContainer) throws ApiException {
    LoggingFacade logger = getLogger();
    logger.info("Running Kubernetes job to execute WLST script");

    V1Job jobBody = new V1Job()
        .metadata(
            new V1ObjectMeta()
                .name(wlstJobName)
                .namespace(namespace))
        .spec(new V1JobSpec()
            .backoffLimit(0) // try only once
            .template(new V1PodTemplateSpec()
                .spec(new V1PodSpec()
                    .restartPolicy("Never")
                    .containers(Arrays.asList(jobContainer
                        .name("execute-wlst-container")
                        .image(image)
                        .imagePullPolicy("IfNotPresent")
                        .volumeMounts(Arrays.asList(
                            new V1VolumeMount()
                                .name("wlst-job-cm-volume") // WLST script volume
                                .mountPath(MOUNT_POINT))))) // mounted under /sctipts inside pod
                    .volumes(Arrays.asList(new V1Volume()
                        .name("wlst-job-cm-volume") // WLST scripts volume
                        .configMap(new V1ConfigMapVolumeSource()
                            .name(wlstScriptConfigMapName)))) //config map containing WLST script
                    .imagePullSecrets(isUseSecret ? Arrays.asList(
                        new V1LocalObjectReference()
                            .name(OCR_SECRET_NAME))
                        : null))));
    String jobName = assertDoesNotThrow(()
        -> createNamespacedJob(jobBody), "Failed to create WLST execution Job");

    logger.info("Checking if the WLST job {0} completed in namespace {1}",
        jobName, namespace);
    withStandardRetryPolicy
        .conditionEvaluationListener(
            condition -> logger.info("Waiting for job {0} to be completed in namespace {1} "
                    + "(elapsed time {2} ms, remaining time {3} ms)",
                jobName,
                namespace,
                condition.getElapsedTimeInMS(),
                condition.getRemainingTimeInMS()))
        .until(jobCompleted(jobName, null, namespace));

    // check job status and fail test if the job failed to execute WLST
    V1Job job = getJob(jobName, namespace);
    if (job != null) {
      V1JobCondition jobCondition = job.getStatus().getConditions().stream().filter(
          v1JobCondition -> "Failed".equalsIgnoreCase(v1JobCondition.getType()))
          .findAny()
          .orElse(null);
      if (jobCondition != null) {
        logger.severe("Job {0} failed to execute WLST script", jobName);
        List<V1Pod> pods = listPods(namespace, "job-name=" + jobName).getItems();
        if (!pods.isEmpty()) {
          logger.severe(getPodLog(pods.get(0).getMetadata().getName(), namespace));
          fail("WLST execute job failed");
        }
      }
      List<V1Pod> pods = listPods(namespace, "job-name=" + jobName).getItems();
      if (!pods.isEmpty()) {
        logger.info(getPodLog(pods.get(0).getMetadata().getName(), namespace));
      }
    }
  }

  /**
   * Set the image to use and create secrets if needed.
   *
   * @param namespace namespace in which secrets needs to be created
   */
  private static void setImage(String namespace) {
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
    getLogger().info("Using image {0}", image);
  }

}

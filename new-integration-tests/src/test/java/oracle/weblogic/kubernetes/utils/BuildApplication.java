// Copyright (c) 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes.utils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.kubernetes.client.custom.Quantity;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.models.V1ConfigMap;
import io.kubernetes.client.openapi.models.V1ConfigMapVolumeSource;
import io.kubernetes.client.openapi.models.V1Container;
import io.kubernetes.client.openapi.models.V1EnvVar;
import io.kubernetes.client.openapi.models.V1HostPathVolumeSource;
import io.kubernetes.client.openapi.models.V1Job;
import io.kubernetes.client.openapi.models.V1JobCondition;
import io.kubernetes.client.openapi.models.V1JobSpec;
import io.kubernetes.client.openapi.models.V1LocalObjectReference;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1PersistentVolume;
import io.kubernetes.client.openapi.models.V1PersistentVolumeClaim;
import io.kubernetes.client.openapi.models.V1PersistentVolumeClaimSpec;
import io.kubernetes.client.openapi.models.V1PersistentVolumeClaimVolumeSource;
import io.kubernetes.client.openapi.models.V1PersistentVolumeSpec;
import io.kubernetes.client.openapi.models.V1Pod;
import io.kubernetes.client.openapi.models.V1PodSpec;
import io.kubernetes.client.openapi.models.V1PodTemplateSpec;
import io.kubernetes.client.openapi.models.V1ResourceRequirements;
import io.kubernetes.client.openapi.models.V1Secret;
import io.kubernetes.client.openapi.models.V1SecretList;
import io.kubernetes.client.openapi.models.V1Volume;
import io.kubernetes.client.openapi.models.V1VolumeMount;
import oracle.weblogic.kubernetes.TestConstants;
import oracle.weblogic.kubernetes.actions.TestActions;
import org.awaitility.core.ConditionFactory;

import static java.util.concurrent.TimeUnit.MINUTES;
import static java.util.concurrent.TimeUnit.SECONDS;
import static oracle.weblogic.kubernetes.TestConstants.KIND_REPO;
import static oracle.weblogic.kubernetes.TestConstants.OCR_EMAIL;
import static oracle.weblogic.kubernetes.TestConstants.OCR_PASSWORD;
import static oracle.weblogic.kubernetes.TestConstants.OCR_REGISTRY;
import static oracle.weblogic.kubernetes.TestConstants.OCR_SECRET_NAME;
import static oracle.weblogic.kubernetes.TestConstants.OCR_USERNAME;
import static oracle.weblogic.kubernetes.TestConstants.PV_ROOT;
import static oracle.weblogic.kubernetes.actions.ActionConstants.RESOURCE_DIR;
import static oracle.weblogic.kubernetes.actions.ActionConstants.WLS_BASE_IMAGE_NAME;
import static oracle.weblogic.kubernetes.actions.ActionConstants.WLS_BASE_IMAGE_TAG;
import static oracle.weblogic.kubernetes.actions.TestActions.createConfigMap;
import static oracle.weblogic.kubernetes.actions.TestActions.createNamespacedJob;
import static oracle.weblogic.kubernetes.actions.TestActions.createPersistentVolume;
import static oracle.weblogic.kubernetes.actions.TestActions.createPersistentVolumeClaim;
import static oracle.weblogic.kubernetes.actions.TestActions.getJob;
import static oracle.weblogic.kubernetes.actions.TestActions.getPodLog;
import static oracle.weblogic.kubernetes.actions.TestActions.listPods;
import static oracle.weblogic.kubernetes.actions.impl.primitive.Kubernetes.listSecrets;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.jobCompleted;
import static oracle.weblogic.kubernetes.extensions.LoggedTest.logger;
import static org.awaitility.Awaitility.with;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Utility class to build application.
 */
public class BuildApplication {

  private static String image;
  private static boolean isUseSecret;
  private static final String APPLICATIONS_MOUNT_PATH = "/application";
  private static final String SCRIPTS_MOUNT_PATH = "/buildScripts";
  private static final String BUILD_SCRIPT = "build_application.sh";

  private static final ConditionFactory withStandardRetryPolicy
      = with().pollDelay(2, SECONDS)
          .and().with().pollInterval(10, SECONDS)
          .atMost(5, MINUTES).await();

  /**
   * Build application.
   *
   * @param application path of the application archive
   * @param parameters system properties for ant
   * @param targets ant targets to call
   * @param namespace name of the namespace in which server pods running
   */
  public static void buildApplication(Path application, Map<String,String> parameters,
      String targets, String namespace) {

    setImage(namespace);

    // Copy the application source directory to PV_ROOT/applications/<application_directory_name>
    // This location is mounted in the build pod under /application
    Path targetPath = Paths.get(PV_ROOT, "applications", application.getFileName().toString());
    logger.info("Copy the application to staging area");
    assertDoesNotThrow(() -> {
      Files.createDirectories(targetPath);
      Files.copy(application, targetPath, StandardCopyOption.REPLACE_EXISTING);
    });

    // bash script to build application
    Path buildScript = Paths.get(RESOURCE_DIR, "bash-scripts", BUILD_SCRIPT);

    logger.info("Creating a config map to hold build scripts");
    List<Path> buildScriptFiles = new ArrayList<>();
    buildScriptFiles.add(buildScript);
    String buildScriptConfigMapName = "build-scripts-cm";
    assertDoesNotThrow(
        () -> createConfigMapFromFiles(buildScriptConfigMapName, buildScriptFiles, namespace),
        "Create configmap for build applications failed");

    // create the persistent volume to make the application archive accessible to pod
    String pvName = namespace + "-build-pv";
    String pvcName = namespace + "-build-pvc";

    assertDoesNotThrow(() -> createPV(targetPath, pvName), "Failed to create PV");
    createPVC(pvName, pvcName, namespace);

    try {
      // build application
      build(parameters, targets, pvName, pvcName, namespace, buildScriptConfigMapName);
    } finally {
      // delete the persistent volume claim and persistent volume
      TestActions.deletePersistentVolumeClaim(pvcName, namespace);
      TestActions.deletePersistentVolume(pvName);
    }
  }

  /**
   * Build application using a WebLogic image pod.
   *
   * @param parameters system properties for ant
   * @param targets ant targets to call
   * @param pvName name of the persistent volume to create domain in
   * @param pvcName name of the persistent volume claim
   * @param namespace name of the domain namespace in which the job is created
   * @param buildScriptConfigMapName configmap containing build scripts
   */
  public static void build(Map<String, String> parameters,
      String targets, String pvName, String pvcName,
      String namespace, String buildScriptConfigMapName) {
    logger.info("Preparing to run build job");
    V1Container jobCreationContainer = new V1Container()
        .addCommandItem("/bin/sh")
        .addArgsItem(SCRIPTS_MOUNT_PATH + "/" + BUILD_SCRIPT);
    if (parameters != null) {
      StringBuilder params = new StringBuilder();
      parameters.entrySet().forEach((parameter) -> {
        params.append("-D").append(parameter.getKey()).append("=").append(parameter.getValue()).append(" ");
      });
      jobCreationContainer = jobCreationContainer
          .addEnvItem(new V1EnvVar().name("sysprops").value(params.toString()));
    }
    if (targets != null) {
      jobCreationContainer = jobCreationContainer
          .addEnvItem(new V1EnvVar().name("targets").value(targets));
    }

    logger.info("Running a Kubernetes job to build application");
    try {
      createBuildJob(pvName, pvcName, buildScriptConfigMapName, namespace, jobCreationContainer);
    } catch (ApiException ex) {
      logger.severe("Building application failed");
      fail("Halting test since build failed");
    }

  }

  /**
   * Create a job to build application inside a WebLogic pod.
   *
   * @param pvName name of the persistent volume containing application source
   * @param pvcName name of the persistent volume claim
   * @param buildScriptConfigMapName configmap holding build script files
   * @param namespace name of the domain namespace in which the job is created
   * @param jobContainer V1Container with job commands to build application
   * @throws ApiException when Kubernetes cluster query fails
   */
  private static void createBuildJob(String pvName,
      String pvcName, String buildScriptConfigMapName, String namespace, V1Container jobContainer)
      throws ApiException {
    logger.info("Running Kubernetes job to build application");

    V1Job jobBody = new V1Job()
        .metadata(
            new V1ObjectMeta()
                .name(namespace + "-build-job")
                .namespace(namespace))
        .spec(new V1JobSpec()
            .backoffLimit(0) // try only once
            .template(new V1PodTemplateSpec()
                .spec(new V1PodSpec()
                    .restartPolicy("Never")
                    .containers(Arrays.asList(jobContainer
                        .name("build-application-container")
                        .image(image)
                        .imagePullPolicy("IfNotPresent")
                        .volumeMounts(Arrays.asList(
                            new V1VolumeMount()
                                .name("build-job-cm-volume")
                                .mountPath(SCRIPTS_MOUNT_PATH), // build scripts
                            new V1VolumeMount()
                                .name(pvName)
                                .mountPath(APPLICATIONS_MOUNT_PATH))))) // application source directory
                    .volumes(Arrays.asList(new V1Volume()
                        .name(pvName)
                        .persistentVolumeClaim(
                            new V1PersistentVolumeClaimVolumeSource()
                                .claimName(pvcName)),
                        new V1Volume()
                            .name("build-job-cm-volume")
                            .configMap(new V1ConfigMapVolumeSource()
                                .name(buildScriptConfigMapName))))
                    .imagePullSecrets(isUseSecret ? Arrays.asList(
                        new V1LocalObjectReference()
                            .name(OCR_SECRET_NAME))
                        : null))));
    String jobName = assertDoesNotThrow(()
        -> createNamespacedJob(jobBody), "Failed to create Job");

    logger.info("Checking if the build job {0} completed in namespace {1}",
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

    // check job status and fail test if the job failed to finish building
    V1Job job = getJob(jobName, namespace);
    if (job != null) {
      V1JobCondition jobCondition = job.getStatus().getConditions().stream().filter(
          v1JobCondition -> "Failed".equalsIgnoreCase(v1JobCondition.getType()))
          .findAny()
          .orElse(null);
      if (jobCondition != null) {
        logger.severe("Job {0} failed to finish build", jobName);
        List<V1Pod> pods = listPods(namespace, "job-name=" + jobName).getItems();
        if (!pods.isEmpty()) {
          logger.severe(getPodLog(pods.get(0).getMetadata().getName(), namespace));
          fail("Build job failed");
        }
      }
    }

  }

  private static void createPV(Path hostPath, String pvName) throws IOException {
    logger.info("creating persistent volume");

    V1PersistentVolume v1pv = new V1PersistentVolume()
        .spec(new V1PersistentVolumeSpec()
            .addAccessModesItem("ReadWriteMany")
            .storageClassName("weblogic-build-storage-class")
            .volumeMode("Filesystem")
            .putCapacityItem("storage", Quantity.fromString("2Gi"))
            .persistentVolumeReclaimPolicy("Recycle")
            .accessModes(Arrays.asList("ReadWriteMany"))
            .hostPath(new V1HostPathVolumeSource()
                .path(hostPath.toString())))
        .metadata(new V1ObjectMeta()
            .name(pvName));
    boolean success = assertDoesNotThrow(() -> createPersistentVolume(v1pv),
        "Failed to create persistent volume");
    assertTrue(success, "PersistentVolume creation failed");
  }

  private static void createPVC(String pvName, String pvcName, String namespace) {
    logger.info("creating persistent volume claim");

    V1PersistentVolumeClaim v1pvc = new V1PersistentVolumeClaim()
        .spec(new V1PersistentVolumeClaimSpec()
            .addAccessModesItem("ReadWriteMany")
            .storageClassName("weblogic-build-storage-class")
            .volumeName(pvName)
            .resources(new V1ResourceRequirements()
                .putRequestsItem("storage", Quantity.fromString("2Gi"))))
        .metadata(new V1ObjectMeta()
            .name(pvcName)
            .namespace(namespace));

    boolean success = assertDoesNotThrow(() -> createPersistentVolumeClaim(v1pvc),
        "Failed to create persistent volume claim");
    assertTrue(success, "PersistentVolumeClaim creation failed");
  }

  private static void createConfigMapFromFiles(String configMapName, List<Path> files, String namespace)
      throws ApiException, IOException {
    logger.info("Creating configmap {0}", configMapName);

    // add domain creation scripts and properties files to the configmap
    Map<String, String> data = new HashMap<>();
    for (Path file : files) {
      data.put(file.getFileName().toString(), Files.readString(file));
    }

    V1ObjectMeta meta = new V1ObjectMeta()
        .name(configMapName)
        .namespace(namespace);
    V1ConfigMap configMap = new V1ConfigMap()
        .data(data)
        .metadata(meta);

    boolean cmCreated = assertDoesNotThrow(() -> createConfigMap(configMap),
        String.format("Failed to create configmap %s with files", configMapName));
    assertTrue(cmCreated, String.format("Failed while creating ConfigMap %s", configMapName));
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
    logger.info("Using image {0}", image);
  }

}

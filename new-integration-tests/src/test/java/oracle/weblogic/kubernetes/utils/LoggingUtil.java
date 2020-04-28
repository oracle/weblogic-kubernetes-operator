// Copyright (c) 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes.utils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeoutException;

import io.kubernetes.client.custom.Quantity;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.models.V1Container;
import io.kubernetes.client.openapi.models.V1HostPathVolumeSource;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1ObjectMetaBuilder;
import io.kubernetes.client.openapi.models.V1PersistentVolume;
import io.kubernetes.client.openapi.models.V1PersistentVolumeClaim;
import io.kubernetes.client.openapi.models.V1PersistentVolumeClaimSpec;
import io.kubernetes.client.openapi.models.V1PersistentVolumeClaimVolumeSource;
import io.kubernetes.client.openapi.models.V1PersistentVolumeList;
import io.kubernetes.client.openapi.models.V1PersistentVolumeSpec;
import io.kubernetes.client.openapi.models.V1Pod;
import io.kubernetes.client.openapi.models.V1PodSpec;
import io.kubernetes.client.openapi.models.V1ResourceRequirements;
import io.kubernetes.client.openapi.models.V1Volume;
import io.kubernetes.client.openapi.models.V1VolumeMount;
import oracle.weblogic.kubernetes.TestConstants;
import oracle.weblogic.kubernetes.actions.TestActions;
import oracle.weblogic.kubernetes.actions.impl.primitive.Kubernetes;
import org.awaitility.core.ConditionFactory;

import static io.kubernetes.client.util.Yaml.dump;
import static java.util.concurrent.TimeUnit.MINUTES;
import static java.util.concurrent.TimeUnit.SECONDS;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.isPersistentVolumeInState;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.podReady;
import static oracle.weblogic.kubernetes.extensions.LoggedTest.logger;
import static org.awaitility.Awaitility.with;

/**
 * A utility class to collect logs for artifacts in Kubernetes cluster.
 */
public class LoggingUtil {

  /**
   * Collect logs for artifacts in Kubernetes cluster for current running test object. This method can be called
   * anywhere in the test by passing the test instance object and namespaces list.
   *
   * <p>The collected logs are written in the LOGS_DIR/IT_TEST_CLASSNAME/CURRENT_TIMESTAMP directory.
   *
   * @param itInstance the integration test instance
   * @param namespaces list of namespaces used by the test instance
   */
  public static void generateLog(Object itInstance, List namespaces) {
    logger.info("Generating logs...");
    String resultDirExt = new SimpleDateFormat("yyyyMMddHHmmss").format(new Date());
    try {
      Path resultDir = Files.createDirectories(
          Paths.get(TestConstants.LOGS_DIR, itInstance.getClass().getSimpleName(),
              resultDirExt));
      for (var namespace : namespaces) {
        LoggingUtil.collectLogs((String) namespace, resultDir.toString());
      }
    } catch (IOException ex) {
      logger.severe(ex.getMessage());
    }
  }

  /**
   * Queries the Kubernetes cluster to get the logs for various artifacts and writes it to the resultDir.
   *
   * @param namespace in which to query cluster for artifacts
   * @param resultDir existing directory to write log files
   */
  public static void collectLogs(String namespace, String resultDir) {
    logger.info("Collecting logs in namespace : {0}", namespace);

    // get service accounts
    try {
      writeToFile(Kubernetes.listServiceAccounts(namespace), resultDir, namespace + "_sa.log");
    } catch (Exception ex) {
      logger.warning(ex.getMessage());
    }

    // get namespaces
    try {
      for (var ns : Kubernetes.listNamespacesAsObjects().getItems()) {
        if (namespace.equals(ns.getMetadata().getName())) {
          writeToFile(ns, resultDir, namespace + "_ns.log");
        }
      }
    } catch (Exception ex) {
      logger.warning(ex.getMessage());
    }

    // get pvc
    try {
      writeToFile(Kubernetes.listPersistentVolumeClaims(namespace), resultDir, namespace + "_pvc.log");
    } catch (Exception ex) {
      logger.warning(ex.getMessage());
    }

    // get pv configuration and pv files based on the weblogic.domainUid label in pvc
    try {
      for (var pvc : Kubernetes.listPersistentVolumeClaims(namespace).getItems()) {
        String label = Optional.ofNullable(pvc)
            .map(metadata -> metadata.getMetadata())
            .map(labels -> labels.getLabels())
            .map(labels -> labels.get("weblogic.domainUid")).get();

        // get the persistent volumes based on label weblogic.domainUid
        V1PersistentVolumeList pvList = Kubernetes
            .listPersistentVolumes(String.format("weblogic.domainUid = %s", label));
        // write the persistent volume configurations to log
        writeToFile(pvList, resultDir, label + "_pv.log");

        // dump files stored in persistent volumes to
        // RESULT_DIR/PVC_NAME/PV_NAME location
        for (var pv : pvList.getItems()) {
          String claimName = pvc.getMetadata().getName();
          String pvName = pv.getMetadata().getName();
          String hostPath = pv.getSpec().getHostPath().getPath();
          try {
            copyFromPV(namespace, hostPath,
                Files.createDirectories(
                    Paths.get(resultDir, claimName, pvName)));
          } catch (ApiException apex) {
            logger.warning(apex.getResponseBody());
          } catch (Exception ex) {
            ex.printStackTrace();
            logger.warning(ex.getMessage());
          }
        }
        logger.info("Done archiving the persistent volumes");
      }
    } catch (ApiException apex) {
      logger.warning(apex.getResponseBody());
    } catch (Exception ex) {
      logger.warning(ex.getMessage());
    }

    // get secrets
    try {
      writeToFile(Kubernetes.listSecrets(namespace), resultDir, namespace + "_secrets.log");
    } catch (Exception ex) {
      logger.warning(ex.getMessage());
    }

    // get configmaps
    try {
      writeToFile(Kubernetes.listConfigMaps(namespace), resultDir, namespace + "_cm.log");
    } catch (Exception ex) {
      logger.warning(ex.getMessage());
    }

    // get jobs
    try {
      writeToFile(Kubernetes.listJobs(namespace), resultDir, namespace + "_jobs.log");
    } catch (Exception ex) {
      logger.warning(ex.getMessage());
    }

    // get deployments
    try {
      writeToFile(Kubernetes.listDeployments(namespace), resultDir, namespace + "_deploy.log");
    } catch (Exception ex) {
      logger.warning(ex.getMessage());
    }

    // get replicasets
    try {
      writeToFile(Kubernetes.listReplicaSets(namespace), resultDir, namespace + "_rs.log");
    } catch (Exception ex) {
      logger.warning(ex.getMessage());
    }

    // get domain objects in the given namespace
    try {
      writeToFile(Kubernetes.listDomains(namespace), resultDir, namespace + "_domains.log");
    } catch (Exception ex) {
      logger.warning("Listing domain failed, not collecting any data for domain");
    }

    // get pods
    try {
      writeToFile(Kubernetes.listPods(namespace, null), resultDir, namespace + "_pods.log");
    } catch (Exception ex) {
      logger.warning("Listing pods failed, not collecting any data for pod configuration");
    }

    // get domain/operator pods
    try {
      for (var pod : Kubernetes.listPods(namespace, null).getItems()) {
        if (pod.getMetadata() != null) {
          String podName = pod.getMetadata().getName();
          writeToFile(Kubernetes.getPodLog(podName, namespace), resultDir, namespace + "-pod_" + podName + ".log");
        }
      }
    } catch (Exception ex) {
      logger.warning(ex.getMessage());
    }
  }

  /**
   * Write the YAML representation of object to a file in the resultDir.
   *
   * @param obj to write to the file as YAML
   * @param resultDir directory in which to write the log file
   * @param fileName name of the log file
   * @throws IOException when write fails
   */
  private static void writeToFile(Object obj, String resultDir, String fileName) throws IOException {
    logger.info("Generating {0}", Paths.get(resultDir, fileName));
    if (obj != null) {
      Files.write(Paths.get(resultDir, fileName),
          dump(obj).getBytes(StandardCharsets.UTF_8)
      );
    } else {
      logger.info("Nothing to write in {0} list is empty", Paths.get(resultDir, fileName));
    }
  }

  /**
   * Copy files from persistent volume to local folder.
   * @param namespace name of the namespace
   * @param hostPath the persistent volume host path
   * @param destinationPath destination folder to copy the files to
   * @throws ApiException when pod interaction fails
   */
  private static void copyFromPV(String namespace, String hostPath, Path destinationPath) throws ApiException {
    V1Pod pvPod = null;
    try {
      // create a temporary pod to get access to the interested persistent volume
      pvPod = setupPVPod(namespace, hostPath);
      copyDirectoryFromPod(pvPod, hostPath, destinationPath);
    } finally {
      // remove the temporary pod
      if (pvPod != null) {
        cleanupPVPod(namespace);
      }
    }
  }

  /**
   * Creates temporary pod with persistent volume claim and persistent volume using host path.
   *
   * @param namespace name of the namespace
   * @param hostPath host path from ineterested persistent volume
   * @return V1Pod pod object
   * @throws ApiException when create pod fails
   */
  private static V1Pod setupPVPod(String namespace, String hostPath) throws ApiException {

    ConditionFactory withStandardRetryPolicy = with().pollDelay(2, SECONDS)
        .and().with().pollInterval(5, SECONDS)
        .atMost(1, MINUTES).await();

    // create a pvc and pv to get access to the host path of the target pv
    final String pvcName = "pv-pod-pvc-" + namespace;
    final String pvName = "pv-pod-pv-" + namespace;

    V1PersistentVolume v1pv = new V1PersistentVolume()
        .spec(new V1PersistentVolumeSpec()
            .addAccessModesItem("ReadWriteMany")
            .storageClassName(namespace + "-weblogic-domain-storage-class")
            .putCapacityItem("storage", Quantity.fromString("2Gi"))
            .persistentVolumeReclaimPolicy("Recycle")
            .hostPath(new V1HostPathVolumeSource().path(hostPath)))
        .metadata(new V1ObjectMetaBuilder()
            .withName(pvName)
            .build());
    TestActions.createPersistentVolume(v1pv);

    V1PersistentVolumeClaim v1pvc = new V1PersistentVolumeClaim()
        .spec(new V1PersistentVolumeClaimSpec()
            .volumeName(pvName)
            .addAccessModesItem("ReadWriteMany")
            .storageClassName(namespace + "-weblogic-domain-storage-class")
            .resources(new V1ResourceRequirements()
                .putRequestsItem("storage", Quantity.fromString("2Gi"))))
        .metadata(new V1ObjectMetaBuilder()
            .withName(pvcName)
            .withNamespace(namespace)
            .build());
    TestActions.createPersistentVolumeClaim(v1pvc);

    withStandardRetryPolicy
        .conditionEvaluationListener(
            condition -> logger.info("Waiting for pv to be bound, "
                + "(elapsed time {0} , remaining time {1}",
                condition.getElapsedTimeInMS(),
                condition.getRemainingTimeInMS()))
        .until(isPersistentVolumeInState(pvName, "Bound"));

    final String podName = "pv-pod-" + namespace;
    V1Pod podBody = new V1Pod()
        .spec(new V1PodSpec()
            .containers(Arrays.asList(
                new V1Container()
                    .name("pv-container")
                    .image("nginx")
                    .imagePullPolicy("IfNotPresent")
                    .volumeMounts(Arrays.asList(
                        new V1VolumeMount()
                            .name(pvName)
                            .mountPath("/shared")))))
            .volumes(Arrays.asList(
                new V1Volume()
                    .name(pvName)
                    .persistentVolumeClaim(
                        new V1PersistentVolumeClaimVolumeSource()
                            .claimName(pvcName)))))
        .metadata(new V1ObjectMeta().name(podName))
        .apiVersion("v1")
        .kind("Pod");
    V1Pod pvPod = Kubernetes.createPod(namespace, podBody);

    withStandardRetryPolicy
        .conditionEvaluationListener(
            condition -> logger.info("Waiting for {0} to be ready in namespace {1}, "
                + "(elapsed time {2} , remaining time {3}",
                podName,
                namespace,
                condition.getElapsedTimeInMS(),
                condition.getRemainingTimeInMS()))
        .until(podReady(podName, null, namespace));
    return pvPod;
  }

  /**
   * Delete the temporary pv pod.
   * @param namespace name
   * @throws ApiException when pod deletion fails
   */
  private static void cleanupPVPod(String namespace) throws ApiException {
    Kubernetes.deletePod("pv-pod-" + namespace, namespace);
    Kubernetes.deletePvc("pv-pod-pvc-" + namespace, namespace);
    Kubernetes.deletePv("pv-pod-pv-" + namespace);
  }

  // there is currently a bug in the copy API which leaves i/o stream left open
  // and copy not to exit. As a temporary fix using a Thread to do the copy
  // and discard it after a minute.
  // This won't be necessary once the bug is fixed in the api.
  // https://github.com/kubernetes-client/java/issues/861
  /**
   * Copy the persistent volume mount directory to local file system.
   * @param pvPod V1Pod object to copy from
   * @param srcPath location of the source path
   * @param destinationPath location for the destination path
   * @throws ApiException when copy fails
   */
  private static void copyDirectoryFromPod(V1Pod pvPod, String srcPath, Path destinationPath) throws ApiException {
    Future<String> copyJob = null;
    try {
      Runnable copy = () -> {
        try {
          logger.info("Copying from PV path {0} to {1}", srcPath, destinationPath.toString());
          Kubernetes.copyDirectoryFromPod(pvPod, "/shared", destinationPath);
        } catch (IOException | ApiException ex) {
          logger.warning(ex.getMessage());
        }
      };
      ExecutorService executorService = Executors.newSingleThreadExecutor();
      copyJob = executorService.submit(copy, "Done copying");
      copyJob.get(1, MINUTES);
    } catch (ExecutionException ex) {
      logger.warning("Exception in copy");
    } catch (TimeoutException ex) {
      logger.warning("Copy timed out");
    } catch (InterruptedException ex) {
      logger.warning("Copy interrupted");
    } finally {
      if (copyJob != null && !copyJob.isDone()) {
        logger.info("Cancelling the copy job");
        copyJob.cancel(true);
      }
    }
  }

}
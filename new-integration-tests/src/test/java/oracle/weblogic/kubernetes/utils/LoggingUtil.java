// Copyright (c) 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes.utils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeoutException;

import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.models.V1Container;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1PersistentVolume;
import io.kubernetes.client.openapi.models.V1PersistentVolumeClaimVolumeSource;
import io.kubernetes.client.openapi.models.V1Pod;
import io.kubernetes.client.openapi.models.V1PodSpec;
import io.kubernetes.client.openapi.models.V1Volume;
import io.kubernetes.client.openapi.models.V1VolumeMount;
import oracle.weblogic.kubernetes.TestConstants;
import oracle.weblogic.kubernetes.actions.impl.primitive.Kubernetes;
import oracle.weblogic.kubernetes.logging.LoggingFacade;
import org.awaitility.core.ConditionFactory;

import static io.kubernetes.client.util.Yaml.dump;
import static java.util.concurrent.TimeUnit.MINUTES;
import static java.util.concurrent.TimeUnit.SECONDS;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.podDoesNotExist;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.podReady;
import static oracle.weblogic.kubernetes.utils.ThreadSafeLogger.getLogger;
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
    LoggingFacade logger = getLogger();
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
    LoggingFacade logger = getLogger();
    logger.info("Collecting logs in namespace : {0}", namespace);

    // get events
    try {
      writeToFile(Kubernetes.listNamespacedEvents(namespace), resultDir, namespace + ".list.events.log");
    } catch (Exception ex) {
      logger.warning("Listing events failed, not collecting any data for events");
    }

    // get service accounts
    try {
      writeToFile(Kubernetes.listServiceAccounts(namespace), resultDir,
          namespace + ".list.service-accounts.log");
    } catch (Exception ex) {
      logger.warning(ex.getMessage());
    }

    // get services
    try {
      writeToFile(Kubernetes.listServices(namespace), resultDir,
          namespace + ".list.services.log");
    } catch (Exception ex) {
      logger.warning(ex.getMessage());
    }

    // get namespaces
    try {
      for (var ns : Kubernetes.listNamespacesAsObjects().getItems()) {
        if (namespace.equals(ns.getMetadata().getName())) {
          writeToFile(ns, resultDir, namespace + ".list.namespace.log");
        }
      }
    } catch (Exception ex) {
      logger.warning(ex.getMessage());
    }

    // get pvc
    try {
      writeToFile(Kubernetes.listPersistentVolumeClaims(namespace), resultDir,
          namespace + ".list.persistent-volume-claims.log");
    } catch (Exception ex) {
      logger.warning(ex.getMessage());
    }

    // archive persistent volume contents
    List<V1PersistentVolume> pvList = new ArrayList<>();
    for (var pv : Kubernetes.listPersistentVolumes().getItems()) {
      for (var pvc : Kubernetes.listPersistentVolumeClaims(namespace).getItems()) {
        if (pv.getSpec().getStorageClassName()
            .equals(pvc.getSpec().getStorageClassName())
            && pv.getMetadata().getName()
                .equals(pvc.getSpec().getVolumeName())) {
          pvList.add(pv);
          String pvName = pv.getMetadata().getName();
          String pvcName = pvc.getMetadata().getName();
          try {
            if (pv.getMetadata().getDeletionTimestamp() == null) {
              copyFromPV(namespace, pvcName, pvName,
                  Files.createDirectories(
                      Paths.get(resultDir, pvcName, pvName)));
            }
          } catch (ApiException ex) {
            logger.warning(ex.getResponseBody());
          } catch (IOException ex) {
            logger.warning(ex.getMessage());
          }
        }
      }
    }
    // write pv list
    try {
      writeToFile(pvList, resultDir, "list.persistent-volumes.log");
    } catch (IOException ex) {
      logger.warning(ex.getMessage());
    }

    // get secrets
    try {
      writeToFile(Kubernetes.listSecrets(namespace), resultDir,
          namespace + ".list.secrets.log");
    } catch (Exception ex) {
      logger.warning(ex.getMessage());
    }

    // get configmaps
    try {
      writeToFile(Kubernetes.listConfigMaps(namespace), resultDir,
          namespace + ".list.configmaps.log");
    } catch (Exception ex) {
      logger.warning(ex.getMessage());
    }

    // get jobs
    try {
      writeToFile(Kubernetes.listJobs(namespace), resultDir, namespace + ".list.jobs.log");
    } catch (Exception ex) {
      logger.warning(ex.getMessage());
    }

    // get deployments
    try {
      writeToFile(Kubernetes.listDeployments(namespace), resultDir, namespace + ".list.deploy.log");
    } catch (Exception ex) {
      logger.warning(ex.getMessage());
    }

    // get replicasets
    try {
      writeToFile(Kubernetes.listReplicaSets(namespace), resultDir,
          namespace + ".list.replica-sets.log");
    } catch (Exception ex) {
      logger.warning(ex.getMessage());
    }

    // get cluster roles
    try {
      writeToFile(Kubernetes.listClusterRoles(null), resultDir,
          "list.cluster-roles.log");
    } catch (Exception ex) {
      logger.warning(ex.getMessage());
    }

    // get cluster role bindings
    try {
      writeToFile(Kubernetes.listClusterRoleBindings(null), resultDir,
          "list.cluster-rolebindings.log");
    } catch (Exception ex) {
      logger.warning(ex.getMessage());
    }

    // get namespaced roles
    try {
      writeToFile(Kubernetes.listNamespacedRoles(namespace), resultDir,
          namespace + ".list.roles.log");
    } catch (Exception ex) {
      logger.warning(ex.getMessage());
    }

    // get namespaced rolebindings
    try {
      writeToFile(Kubernetes.listNamespacedRoleBinding(namespace), resultDir,
          namespace + ".list.rolebindings.log");
    } catch (Exception ex) {
      logger.warning(ex.getMessage());
    }

    // get domain objects in the given namespace
    try {
      writeToFile(Kubernetes.listDomains(namespace), resultDir, namespace + ".list.domains.log");
    } catch (Exception ex) {
      logger.warning("Listing domain failed, not collecting any data for domain");
    }
    // get pods
    try {
      writeToFile(Kubernetes.listPods(namespace, null), resultDir, namespace + ".list.pods.log");
    } catch (Exception ex) {
      logger.warning("Listing pods failed, not collecting any data for pod configuration");
    }

    // get domain/operator pods
    try {
      for (var pod : Kubernetes.listPods(namespace, null).getItems()) {
        if (pod.getMetadata() != null) {
          String podName = pod.getMetadata().getName();
          List<V1Container> containers = pod.getSpec().getContainers();
          logger.info("Found {0} container(s) in the pod {1}", containers.size(), podName);
          for (var container : containers) {
            String containerName = container.getName();
            writeToFile(Kubernetes.getPodLog(podName, namespace, containerName), resultDir,
                namespace + ".pod." + podName + ".container." + containerName + ".log");
          }
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
  private static void writeToFile(Object obj, String resultDir, String fileName)
      throws IOException {
    LoggingFacade logger = getLogger();
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
   * @param namespace name of the namespace, used for creating temporary pod in it.
   * @param pvcName name of the persistent volume claim
   * @param pvName name of the persistent volume from which the contents needs to be archived
   * @param destinationPath destination folder to copy the files to
   * @throws ApiException when pod interaction fails
   */
  private static void copyFromPV(
      String namespace,
      String pvcName,
      String pvName,
      Path destinationPath) throws ApiException {

    V1Pod pvPod = null;
    try {
      // create a temporary pod
      pvPod = setupPVPod(namespace, pvcName, pvName);
      // copy from the temporary pod to local folder
      copyDirectoryFromPod(pvPod, destinationPath);
    } finally {
      // remove the temporary pod
      if (pvPod != null) {
        cleanupPVPod(namespace);
      }
    }
  }

  /**
   * Create a temporary pod to get access to the persistent volume.
   *
   * @param namespace name of the namespace in which to create the temporary pod
   * @param pvcName name of the persistent volume claim
   * @param pvName name of the persistent volume from which the contents needs to be copied
   * @return V1Pod temporary copy pod object
   * @throws ApiException when create pod fails
   */
  private static V1Pod setupPVPod(String namespace, String pvcName, String pvName)
      throws ApiException {
    final LoggingFacade logger = getLogger();
    ConditionFactory withStandardRetryPolicy = with().pollDelay(10, SECONDS)
        .and().with().pollInterval(2, SECONDS)
        .atMost(1, MINUTES).await();

    // Create the temporary pod with oraclelinux image
    // oraclelinux:7-slim is not useful, missing tar utility
    final String podName = "pv-pod-" + namespace;
    V1Pod podBody = new V1Pod()
        .spec(new V1PodSpec()
            .containers(Arrays.asList(
                new V1Container()
                    .name("pv-container")
                    .image("oraclelinux:7")
                    .imagePullPolicy("IfNotPresent")
                    .volumeMounts(Arrays.asList(
                        new V1VolumeMount()
                            .name(pvName) // mount the persistent volume to /shared inside the pod
                            .mountPath("/shared")))
                    .addCommandItem("tailf")
                    .addArgsItem("/dev/null")))
            .volumes(Arrays.asList(
                new V1Volume()
                    .name(pvName) // the persistent volume that needs to be archived
                    .persistentVolumeClaim(
                        new V1PersistentVolumeClaimVolumeSource()
                            .claimName(pvcName))))) // the persistent volume claim used by the test
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
   * @param namespace name of the namespace in which the copy pod running
   * @throws ApiException when pod deletion fails
   */
  private static void cleanupPVPod(String namespace) throws ApiException {
    final String podName = "pv-pod-" + namespace;
    LoggingFacade logger = getLogger();
    ConditionFactory withStandardRetryPolicy = with().pollDelay(5, SECONDS)
        .and().with().pollInterval(5, SECONDS)
        .atMost(1, MINUTES).await();

    // Delete the temporary pod
    Kubernetes.deletePod(podName, namespace);

    // Wait for the pod to be deleted
    withStandardRetryPolicy
        .conditionEvaluationListener(
            condition -> logger.info("Waiting for {0} to be deleted in namespace {1}, "
                + "(elapsed time {2} , remaining time {3}",
                podName,
                namespace,
                condition.getElapsedTimeInMS(),
                condition.getRemainingTimeInMS()))
        .until(podDoesNotExist(podName, null, namespace));
  }

  // The io.kubernetes.client.Copy.copyDirectoryFromPod(V1Pod pod, String srcPath, Path destination)
  // API has a bug which keeps the i/o stream open and copyDirectoryFromPod not to exit. As  a
  // temporary fix using a Thread to do the copy and discard it after a minute. The assumption here
  // is that the copying of persistsent volume will be done in a minute. If we feel that we need more
  // time for the copy to complete then we can increase the wait time.
  // This won't be necessary once the bug is fixed in the api. Here is the issue # for the API bug.
  // https://github.com/kubernetes-client/java/issues/861
  /**
   * Copy the mounted persistent volume directory to local file system.
   * @param pvPod V1Pod object to copy from
   * @param destinationPath location for the destination path
   * @throws ApiException when copy fails
   */
  private static void copyDirectoryFromPod(V1Pod pvPod, Path destinationPath)
      throws ApiException {
    LoggingFacade logger = getLogger();
    Future<String> copyJob = null;
    try {
      Runnable copy = () -> {
        try {
          logger.info("Copying the contents of PV to {0}", destinationPath.toString());
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

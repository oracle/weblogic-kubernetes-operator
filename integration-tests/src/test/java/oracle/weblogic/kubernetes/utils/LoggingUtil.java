// Copyright (c) 2020, 2025, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes.utils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.StringJoiner;
import java.util.concurrent.Callable;

import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.models.V1Container;
import io.kubernetes.client.openapi.models.V1PersistentVolume;
import io.kubernetes.client.openapi.models.V1PersistentVolumeClaimList;
import io.kubernetes.client.openapi.models.V1PersistentVolumeList;
import oracle.weblogic.kubernetes.TestConstants;
import oracle.weblogic.kubernetes.actions.TestActions;
import oracle.weblogic.kubernetes.actions.impl.primitive.Kubernetes;
import oracle.weblogic.kubernetes.logging.LoggingFacade;

import static io.kubernetes.client.util.Yaml.dump;
import static oracle.weblogic.kubernetes.actions.TestActions.getPodLog;
import static oracle.weblogic.kubernetes.actions.impl.primitive.Kubernetes.listPersistentVolumeClaims;
import static oracle.weblogic.kubernetes.actions.impl.primitive.Kubernetes.listPersistentVolumes;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.testUntil;
import static oracle.weblogic.kubernetes.utils.ThreadSafeLogger.getLogger;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

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
  public static void generateLog(Object itInstance, List<String> namespaces) {
    LoggingFacade logger = getLogger();
    logger.info("Generating logs...");
    String resultDirExt = new SimpleDateFormat("yyyyMMddHHmmss").format(new Date());
    try {
      Path resultDir = Files.createDirectories(
          Paths.get(TestConstants.LOGS_DIR, itInstance.getClass().getSimpleName(),
              resultDirExt));
      for (String namespace : namespaces) {
        LoggingUtil.collectLogs(namespace, resultDir.toString());
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
    
    try {
      if (!TestActions.listNamespaces().contains(namespace)) {
        logger.warning("Namespace {0} doesn't exist");
        return;
      }
    } catch (ApiException ex) {
      logger.warning(ex.getLocalizedMessage());
      return;
    }

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
        if (ns.getMetadata() != null && namespace.equals(ns.getMetadata().getName())) {
          writeToFile(ns, resultDir, namespace + ".list.namespace.log");
        }
      }
    } catch (Exception ex) {
      logger.warning(ex.getMessage());
    }

    // get pvc
    try {
      writeToFile(listPersistentVolumeClaims(namespace), resultDir,
          namespace + ".list.persistent-volume-claims.log");
    } catch (Exception ex) {
      logger.warning(ex.getMessage());
    }

    // archive persistent volume contents
    List<V1PersistentVolume> pvList = new ArrayList<>();
    V1PersistentVolumeList persistentVolumeList = listPersistentVolumes();
    V1PersistentVolumeClaimList persistentVolumeClaimList = listPersistentVolumeClaims(namespace);
    if (persistentVolumeList != null && persistentVolumeClaimList != null) {
      for (var pv : persistentVolumeList.getItems()) {
        for (var pvc : persistentVolumeClaimList.getItems()) {
          if (pv.getSpec() != null && pvc.getSpec() != null && pv.getSpec().getStorageClassName() != null
              && pv.getSpec().getStorageClassName().equals(pvc.getSpec().getStorageClassName())
              && pv.getMetadata() != null && pv.getMetadata().getName() != null
              && pv.getMetadata().getName().equals(pvc.getSpec().getVolumeName())) {
            pvList.add(pv);
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

    // get validatingwebhookconfigurations
    try {
      writeToFile(Kubernetes.listValidatingWebhookConfiguration(), resultDir,
          "list.validating-webhook-configurations.log");
    } catch (Exception ex) {
      logger.warning(ex.getMessage());
    }

    // get domain objects in the given namespace
    try {
      writeToFile(Kubernetes.listDomains(namespace), resultDir, namespace + ".list.domains.log");
    } catch (Exception ex) {
      logger.warning("Listing domain failed, not collecting any data for domain");
    }
    
    // get cluster objects in the given namespace
    try {
      writeToFile(Kubernetes.listClusters(namespace), resultDir, namespace + ".list.clusters.log");
    } catch (Exception ex) {
      logger.warning("Listing clusters failed, not collecting any data for clusters");
    }    

    // get pods
    try {
      writeToFile(Kubernetes.listPods(namespace, null), resultDir, namespace + ".list.pods.log");
    } catch (Exception ex) {
      logger.warning("Listing pods failed, not collecting any data for pod configuration");
    }

    // get pdbs
    try {
      writeToFile(Kubernetes.listPodDisruptionBudgets(namespace, null), resultDir, namespace + ".list.pdbs.log");
    } catch (Exception ex) {
      logger.warning("Listing pods disruption budgets failed, not collecting any data for pdbs");
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
                namespace + ".pod." + podName + ".container." + containerName + ".log", false);

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
    writeToFile(obj, resultDir, fileName, true);
  }

  /**
   * Write the YAML representation of object or String to a file in the resultDir.
   *
   * @param obj to write to the file as YAML or String
   * @param resultDir directory in which to write the log file
   * @param fileName name of the log file
   * @param asYaml write as yaml or string
   * @throws IOException when write fails
   */
  private static void writeToFile(Object obj, String resultDir, String fileName, boolean asYaml)
      throws IOException {
    LoggingFacade logger = getLogger();
    logger.info("Generating {0}", Paths.get(resultDir, fileName));
    if (obj != null) {
      Files.write(Paths.get(resultDir, fileName),
          (asYaml ? dump(obj).getBytes(StandardCharsets.UTF_8) : ((String)obj).getBytes(StandardCharsets.UTF_8))
      );
    } else {
      logger.info("Nothing to write in {0} list is empty", Paths.get(resultDir, fileName));
    }
  }

  private static Callable<Boolean> podLogContainsString(String namespace, String podName, String expectedString) {
    return () -> doesPodLogContainString(namespace, podName, expectedString);
  }

  /**
   * Check whether pod log contains expected string.
   * @param namespace - namespace where pod exists
   * @param podName - pod name of the log
   * @param expectedString - expected string in the pod log
   * @return true if pod log contains expected string, false otherwise
   */
  public static boolean doesPodLogContainString(String namespace, String podName, String expectedString) {
    String podLog;
    try {
      podLog = getPodLog(podName, namespace);
      getLogger().info("Lines of pod log {0} containing expected message {1} in namespace {2} : {3}",
          podName, expectedString, namespace, linesContaining(podLog, expectedString));
    } catch (ApiException apiEx) {
      getLogger().severe("got ApiException while getting pod log: ", apiEx);
      return false;
    }

    return podLog.contains(expectedString);
  }
  
  /**
   * get last N lines from a String.
   *
   * @param input entire string
   * @param n number of lines to get
   * @return the last n lines
   */
  public static String lastNLines(String input, int n) {
    if (input == null || n <= 0) {
      return "";
    }

    input = input.replace("\r\n", "\n");
    int count = 0;
    int i = input.length() - 1;

    while (i >= 0) {
      if (input.charAt(i) == '\n') {
        count++;
        if (count == n) {
          return input.substring(i + 1);
        }
      }
      i--;
    }

    // fewer than n lines → return whole string
    return input;
  }

  /**
   * Get all lines in the log containg the expected substring.
   *
   * @param input input entire string
   * @param expectedMessage expected substring
   * @return lines containing sibstring
   */
  public static String linesContaining(String input, String expectedMessage) {

    if (input == null || expectedMessage == null || expectedMessage.isEmpty()) {
      return "";
    }

    StringJoiner result = new StringJoiner(System.lineSeparator());

    input = input.replace("\r\n", "\n");
    int start = 0;
    int len = input.length();

    for (int i = 0; i <= len; i++) {
      if (i == len || input.charAt(i) == '\n') {
        String line = input.substring(start, i);

        if (line.contains(expectedMessage)) {
          result.add(line);
        }

        start = i + 1;
      }
    }

    return result.toString();
  }
  

  /**
   * Check whether pod log contains expected string in time range from provided start time to the current moment.
   *
   * @param namespace      - namespace where pod exists
   * @param podName        - pod name of the log
   * @param expectedString - expected string in the pod log
   * @param timestamp      - starting time to check the log
   * @return true if pod log contains expected string, false otherwise
   */
  public static boolean doesPodLogContainStringInTimeRange(String namespace, String podName, String expectedString,
                                                           OffsetDateTime timestamp) {
    String rangePodLog;
    try {
      String podLog = getPodLog(podName, namespace);
      String startTimestamp = timestamp.toString().replace("Z", "");
      int begin = -1;
      int count = 0;

      //search log for timestamp plus up to 5 seconds if pod log does not have it matching timestamp
      while (begin == (-1) && count < 5) {
        startTimestamp = timestamp.toString().replace("Z", "");
        begin = podLog.indexOf(startTimestamp);
        getLogger().info("Index of  timestamp {0} in the pod log is : {1}, count {2}",
                startTimestamp,
                begin,
                count);
        timestamp = timestamp.plusSeconds(1);
        count++;
      }
      if (begin == (-1)) {
        getLogger().info("Could not find any messages after timestamp {0}", startTimestamp);
        return false;
      }
      rangePodLog = podLog.substring(begin);
      getLogger().info("pod log for pod {0} in namespace {1} starting from timestamp {2} : {3}", podName,
              namespace,
              startTimestamp,
              rangePodLog);
    } catch (ApiException apiEx) {
      getLogger().severe("got ApiException while getting pod log: ", apiEx);
      return false;
    }
    return rangePodLog.contains(expectedString);
  }

  /**
   * Wait and check the pod log contains the expected string.
   * @param namespace the namespace in which the pod exists
   * @param podName the pod to get the log
   * @param expectedString the expected string to check in the pod log
   */
  public static  void checkPodLogContainsString(String namespace, String podName, String expectedString) {

    getLogger().info("Wait for string {0} existing in pod {1} in namespace {2}", expectedString, podName, namespace);
    testUntil(
        assertDoesNotThrow(() -> podLogContainsString(namespace, podName, expectedString),
          "podLogContainsString failed with IOException, ApiException or InterruptedException"),
        getLogger(),
        "string {0} existing in pod {1} in namespace {2}",
        expectedString,
        podName,
        namespace);
  }
}

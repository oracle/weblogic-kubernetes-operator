// Copyright (c) 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes.utils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

import io.kubernetes.client.openapi.ApiException;
import oracle.weblogic.kubernetes.actions.impl.primitive.Kubernetes;

import static io.kubernetes.client.util.Yaml.dump;
import static oracle.weblogic.kubernetes.extensions.LoggedTest.logger;

/**
 * A utility class to collect logs for artifacts in Kubernetes cluster.
 */
public class LoggingUtil {

  /**
   * Directory to store logs. Needs to be changed to the parameter set in env or Jenkins.
   */
  private static final String LOGS_DIR = System.getProperty("java.io.tmpdir");

  /**
   * Collect logs for artifacts in Kubernetes cluster for current running test object. This method can be called
   * anywhere in the test by passing the test instance object and list namespaces.
   * <p>
   * The collected logs are written in the LOGS_DIR/IT_TEST_CLASSNAME/CURRENT_TIMESTAMP directory.
   *
   * @param itInstance the integration test instance
   * @param namespaces list of namespaces used by the test instance
   */
  public static void collectLogs(Object itInstance, List namespaces) {
    logger.info("Collecting logs...");
    String resultDirExt = new SimpleDateFormat("yyyyMMddHHmmss").format(new Date());
    try {
      Path resultDir = Files.createDirectories(
          Paths.get(LOGS_DIR, itInstance.getClass().getSimpleName(),
              resultDirExt));
      for (var namespace : namespaces) {
        LoggingUtil.generateLog((String) namespace, resultDir);
      }
    } catch (IOException ex) {
      logger.warning(ex.getMessage());
    } catch (ApiException ex) {
      logger.warning(ex.getResponseBody());
    }
  }

  /**
   * Queries the Kubernetes cluster to get the logs for various artifacts and writes it to the resultDir.
   *
   * @param namespace in which to query cluster for artifacts
   * @param resultDir existing directory to write log files
   * @throws IOException when writing to log files fail
   * @throws ApiException when Kubernetes cluster query fails
   */
  public static void generateLog(String namespace, Path resultDir) throws IOException, ApiException {
    logger.info("Collecting logs in namespace : {0}", namespace);
    // get service accounts
    writeToFile(Kubernetes.listServiceAccounts(namespace), resultDir.toString(), namespace + "_sa.log");
    // get namespaces
    writeToFile(Kubernetes.listNamespacesAsObjects(), resultDir.toString(), namespace + "_ns.log");
    // get pv
    writeToFile(Kubernetes.listPersistenVolumes(), resultDir.toString(), namespace + "_pv.log");
    // get pvc
    writeToFile(Kubernetes.listPersistenVolumeClaims(), resultDir.toString(), namespace + "_pvc.log");
    // get secrets
    writeToFile(Kubernetes.listSecrets(namespace), resultDir.toString(), namespace + "_secrets.log");
    // get configmaps
    writeToFile(Kubernetes.listConfigMaps(namespace), resultDir.toString(), namespace + "_cm.log");
    // get jobs
    writeToFile(Kubernetes.listJobs(namespace), resultDir.toString(), namespace + "_jobs.log");
    // get deployments
    writeToFile(Kubernetes.listDeployments(namespace), resultDir.toString(), namespace + "_deploy.log");
    // get replicasets
    writeToFile(Kubernetes.listReplicaSets(namespace), resultDir.toString(), namespace + "_rs.log");
    // get all Domain objects in all namespaces
    writeToFile(Kubernetes.getDomainObjects(), resultDir.toString(), "all_domains.log");
    // get all Domain objects in given namespace
    writeToFile(Kubernetes.listDomains(namespace), resultDir.toString(), namespace + "_list_domains.log");
    // get domain/operator pods
    for (var pod : Kubernetes.listPods(namespace, null).getItems()) {
      if (pod.getMetadata() != null) {
        writeToFile(Kubernetes.getPodLog(pod.getMetadata().getName(), namespace),
            resultDir.toString(),
            namespace + "_" + pod.getMetadata().getName() + ".log");
      }
    }
  }

  /**
   * Write the YAML representation of object to a file in the resultDir.
   *
   * @param obj to write to the file as YAML
   * @param resultDir directory in which to write the file
   * @param fileName name of the log file to write
   * @throws IOException when write fails
   */
  private static void writeToFile(Object obj, String resultDir, String fileName) throws IOException {
    logger.info("Generating {0}", Paths.get(resultDir, fileName));
    if (obj != null) {
      logger.info("Generating {0}", Paths.get(resultDir, fileName));
      Files.write(Paths.get(resultDir, fileName),
          dump(obj).getBytes(StandardCharsets.UTF_8)
      );
    } else {
      logger.info("Nothing to write in {0} list is empty", Paths.get(resultDir, fileName));
    }
  }

}

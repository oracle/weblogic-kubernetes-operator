// Copyright (c) 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes.utils;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.models.V1Pod;
import io.kubernetes.client.openapi.models.V1PodList;
import oracle.weblogic.kubernetes.actions.impl.primitive.Kubernetes;
import oracle.weblogic.kubernetes.annotations.NamespaceList;

import static io.kubernetes.client.util.Yaml.dump;
import static oracle.weblogic.kubernetes.extensions.LoggedTest.logger;

/**
 * A utility class to collect log messages from Kubernetes cluster.
 */
public class LoggingUtil {

  private static final String LOGS_DIR = System.getProperty("java.io.tmpdir");

  /**
   * A utility method to collect logs for current running test object.
   * @param itInstance the integration test instance
   */
  public static void collectLogs(Object itInstance) {
    logger.info("Collecting logs...");
    String resultDirExt = new SimpleDateFormat("yyyyMMddHHmmss").format(new Date());
    try {
      Path resultDir = Files.createDirectories(
          Paths.get(LOGS_DIR, itInstance.getClass().getSimpleName(),
              resultDirExt));
      for (var namespace : LoggingUtil.getNamespaceList(itInstance)) {
        LoggingUtil.generateLog((String) namespace, resultDir);
      }
    } catch (IllegalArgumentException | IllegalAccessException | IOException ex) {
      logger.warning(ex.getMessage());
    } catch (ApiException ex) {
      logger.warning(ex.getResponseBody());
    }
  }

  /**
   * A utility method to introspect the test instance and collect all namespace fields used by the test.
   * @param itInstance the integration test instance
   * @return Set of namespaces used by the tests
   * @throws IllegalArgumentException when test instance access to the fields fails
   * @throws IllegalAccessException when test instance access to the fields fails
   * @throws ApiException when accessing the Kubernetes cluster for artifacts fails
   */
  public static Set getNamespaceList(Object itInstance)
      throws IllegalArgumentException, IllegalAccessException, ApiException {
    Set<String> namespaceFields;
    namespaceFields = getNSListIntersecting(itInstance);
    namespaceFields = getNSListTagged(itInstance);
    return namespaceFields;
  }

  /**
   * This utility method gets all the declared String field values in the test class,
   * gets another list of namespaces existing in the Kubernetes cluster and gets
   * the intersectioin of these 2 Sets.
   * <p>
   * This method doesnot need the test classes to decorate their fields with any annotations.
   * @param itInstance the integration test instance
   * @return Set of namespaces used by the tests and that exists in the Kubernetes cluster
   * @throws IllegalArgumentException when test instance access to the fields fails
   * @throws IllegalAccessException when test instance access to the fields fails
   * @throws ApiException when accessing the Kubernetes cluster for artifacts fails
   */
  public static Set getNSListIntersecting(Object itInstance)
      throws IllegalArgumentException, IllegalAccessException, ApiException {
    Set<String> stringFiledList = new HashSet<>();
    for (Field field : itInstance.getClass().getDeclaredFields()) {
      field.setAccessible(true);
      if (String.class.isAssignableFrom(field.getType())) {
        if (field.get(itInstance) != null) {
          stringFiledList.add((String) field.get(itInstance));
        }
      }
    }
    stringFiledList.retainAll(getNSFromK8s());
    return stringFiledList;
  }

  /**
   * This utility method gets all the declared String field values with annotation @NamespaceList
   * in the test class.
   * <p>
   * This method does need the test classes to decorate their fields with @NamespaceList.
   * @param itInstance the integration test instance
   * @return Set of namespaces used by the tests.
   * @throws IllegalArgumentException when test instance access to the fields fails
   * @throws IllegalAccessException when test instance access to the fields fails
   * @throws ApiException when accessing the Kubernetes cluster for artifacts fails
   */
  public static Set getNSListTagged(Object itInstance)
      throws IllegalArgumentException, IllegalAccessException, ApiException {
    Set<String> namespaceFields = new HashSet<>();
    for (Field field : itInstance.getClass().getDeclaredFields()) {
      field.setAccessible(true);
      if (field.isAnnotationPresent(NamespaceList.class)) {
        if (field.get(itInstance) != null) {
          namespaceFields.add((String) field.get(itInstance));
        }
      }
    }
    logger.info("Tagged namespace set : \n {0}", namespaceFields.toString());
    return namespaceFields;
  }

  /**
   * Utility method to query the Kubernetes cluster with given namespace to get the various artifacts.
   * @param namespace in which to query cluster for artifacts
   * @param resultDir existing directory to place the written log files
   * @throws IOException when writing log files fails
   * @throws ApiException when Kubernetes cluster query fails
   */
  public static void generateLog(String namespace, Path resultDir) throws IOException, ApiException {
    logger.info("Collecting logs for namespace : {0}", namespace);
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
    // get Domain
    writeToFile(Kubernetes.listDomains(namespace), resultDir.toString(), namespace + "_domain.log");
    // get domain/operator pods
    V1PodList listPods = Kubernetes.listPods(namespace, null);
    List<V1Pod> domainPods = listPods.getItems();
    for (V1Pod pod : domainPods) {
      String podName = pod.getMetadata().getName();
      String podLog = Kubernetes.getPodLog(podName, namespace);
      writeToFile(podLog, resultDir.toString(), namespace + podName + ".log");
    }
  }

  /**
   * Method to write files.
   * @param obj to write to the file as YAML
   * @param resultDir directory for the log location
   * @param fileName name of the log file to write
   * @throws IOException when write fails
   */
  private static void writeToFile(Object obj, String resultDir, String fileName) throws IOException {
    if (obj != null) {
      logger.info("Generating {0}", Paths.get(resultDir, fileName));
      Files.write(Paths.get(resultDir, fileName),
          dump(obj).getBytes(StandardCharsets.UTF_8)
      );
    }
  }

  /**
   * A util method to query the Kubernetes cluster to all all namespaces existing.
   * @return Set of namespaces from the Kubernetes cluster
   * @throws ApiException when Kubernetes cluster query fails
   */
  public static Set<String> getNSFromK8s() throws ApiException {
    Set<String> namespaceFields = new HashSet<>();
    for (var iterator
        = Kubernetes.listNamespacesAsObjects().getItems().iterator();
        iterator.hasNext();) {
      String name = iterator.next().getMetadata().getName();
      if (name != null) {
        namespaceFields.add(name);
      }
    }
    return namespaceFields;
  }

}

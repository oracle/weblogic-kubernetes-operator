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

public class LoggingUtil {

  private static final String DIAG_LOGS_DIR = System.getProperty("java.io.tmpdir");

  public static void collectLogs(Object itInstance) throws
      IllegalArgumentException,
      IllegalAccessException,
      ApiException {
    logger.info("Collecting logs...");
    String resultDirExt = new SimpleDateFormat("yyyyMMddHHmmss").format(new Date());
    try {
      Path resultDir = Files.createDirectories(
          Paths.get(DIAG_LOGS_DIR, itInstance.getClass().getSimpleName(), resultDirExt));
      for (var namespace : LoggingUtil.getNamespaceList(itInstance)) {
        LoggingUtil.generateLog((String) namespace, resultDir);
      }
    } catch (IOException ex) {
      logger.warning(ex.getMessage());
    }
  }

  public static Set getNamespaceList(Object itInstance)
      throws IllegalArgumentException, IllegalAccessException, ApiException {
    Set<String> namespaceFields;
    namespaceFields = getNSListIntersecting(itInstance);
    namespaceFields = getNSListTagged(itInstance);
    return namespaceFields;
  }

  public static Set getNSListIntersecting(Object itInstance)
      throws IllegalArgumentException, IllegalAccessException, ApiException {
    Set<String> stringList = new HashSet<>();
    for (Field field : itInstance.getClass().getDeclaredFields()) {
      field.setAccessible(true);
      if (String.class.isAssignableFrom(field.getType())) {
        if (field.get(itInstance) != null) {
          stringList.add((String) field.get(itInstance));
        }
      }
    }
    logger.info("String set before: \n {0}", stringList.toString());
    stringList.retainAll(getNSFromK8s());
    logger.info("String set after: \n {0}", stringList.toString());
    return stringList;
  }

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
    // writeToFile(Kubernetes.listDomains(namespace), resultDir.toString(), namespace + "_domain.log");
    // get domain/operator pods
    V1PodList listPods = Kubernetes.listPods(namespace, null);
    List<V1Pod> domainPods = listPods.getItems();
    for (V1Pod pod : domainPods) {
      String podName = pod.getMetadata().getName();
      String podLog = Kubernetes.getPodLog(podName, namespace);
      writeToFile(podLog, resultDir.toString(), namespace + podName + ".log");
    }
  }

  private static void writeToFile(Object obj, String resultDir, String fileName) throws IOException {
    if (obj != null) {
      logger.info("Generating {0}", Paths.get(resultDir, fileName));
      Files.write(Paths.get(resultDir, fileName),
          dump(obj).getBytes(StandardCharsets.UTF_8)
      );
    }
  }

  private static Set<String> getNSFromK8s() throws ApiException {
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

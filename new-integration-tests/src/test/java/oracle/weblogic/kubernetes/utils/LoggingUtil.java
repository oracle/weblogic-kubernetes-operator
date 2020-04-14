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
import java.util.logging.Level;
import java.util.logging.Logger;

import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.models.V1ConfigMapList;
import io.kubernetes.client.openapi.models.V1DeploymentList;
import io.kubernetes.client.openapi.models.V1JobList;
import io.kubernetes.client.openapi.models.V1NamespaceList;
import io.kubernetes.client.openapi.models.V1PersistentVolumeClaimList;
import io.kubernetes.client.openapi.models.V1PersistentVolumeList;
import io.kubernetes.client.openapi.models.V1Pod;
import io.kubernetes.client.openapi.models.V1PodList;
import io.kubernetes.client.openapi.models.V1ReplicaSetList;
import io.kubernetes.client.openapi.models.V1SecretList;
import io.kubernetes.client.openapi.models.V1ServiceAccountList;
import oracle.weblogic.domain.DomainList;
import oracle.weblogic.kubernetes.actions.impl.primitive.Kubernetes;
import oracle.weblogic.kubernetes.annotations.NamespaceList;
import oracle.weblogic.kubernetes.extensions.IntegrationTestWatcher;

import static io.kubernetes.client.util.Yaml.dump;
import static oracle.weblogic.kubernetes.extensions.LoggedTest.logger;

public class LoggingUtil {

  private static final String DIAG_LOGS_DIR = System.getProperty("java.io.tmpdir");

  public static void collectLogs(Object itInstance) throws
      IllegalArgumentException,
      IllegalAccessException,
      ApiException {
    String resultDirExt = new SimpleDateFormat("yyyyMMddHHmmss").format(new Date());
    try {
      Path resultDir = Files.createDirectories(
          Paths.get(DIAG_LOGS_DIR, itInstance.getClass().getSimpleName(), resultDirExt));
      for (var namespace : LoggingUtil.getNamespaceList(itInstance)) {
        LoggingUtil.generateLog((String) namespace, resultDir);
      }
    } catch (IOException ex) {
      Logger.getLogger(IntegrationTestWatcher.class.getName()).log(Level.SEVERE, null, ex);
    }
  }

  public static Set getNamespaceList(Object itInstance) throws IllegalArgumentException, IllegalAccessException {
    Set<String> namespaceFields = new HashSet<>();
    for (Field field : itInstance.getClass().getFields()) {
      if (field.isAnnotationPresent(NamespaceList.class)) {
        namespaceFields.add((String) field.get(itInstance));
      }
    }
    return namespaceFields;
  }

  public static void generateLog(String namespace, Path resultDir) throws IOException, ApiException {
    logger.info("Collecting logs for namespace :" + namespace);
    logger.info("Writing the logs in :" + resultDir.toString());

    // get service accounts
    V1ServiceAccountList listServiceAccounts = Kubernetes.listServiceAccounts(namespace);
    Files.write(Paths.get(resultDir.toString(), namespace + "_sa.log"),
        dump(listServiceAccounts).getBytes(StandardCharsets.UTF_8)
    );
    // get namespaces
    V1NamespaceList listNamespaces = Kubernetes.listNamespacesAsObjects();
    Files.write(Paths.get(resultDir.toString(), namespace + "_ns.log"),
        dump(listNamespaces).getBytes(StandardCharsets.UTF_8)
    );
    // get pv
    V1PersistentVolumeList listPersistenVolumes = Kubernetes.listPersistenVolumes();
    Files.write(Paths.get(resultDir.toString(), namespace + "_pv.log"),
        dump(listPersistenVolumes).getBytes(StandardCharsets.UTF_8)
    );
    // get pvc
    V1PersistentVolumeClaimList listPersistenVolumeClaims = Kubernetes.listPersistenVolumeClaims();
    Files.write(Paths.get(resultDir.toString(), namespace + "_pvc.log"),
        dump(listPersistenVolumeClaims).getBytes(StandardCharsets.UTF_8)
    );
    // get secrets
    V1SecretList listSecrets = Kubernetes.listSecrets(namespace);
    Files.write(Paths.get(resultDir.toString(), namespace + "_secrets.log"),
        dump(listSecrets).getBytes(StandardCharsets.UTF_8)
    );
    // get configmaps
    V1ConfigMapList listConfigMaps = Kubernetes.listConfigMaps(namespace);
    Files.write(Paths.get(resultDir.toString(), namespace + "_cm.log"),
        dump(listConfigMaps).getBytes(StandardCharsets.UTF_8)
    );
    // get jobs
    V1JobList listJobs = Kubernetes.listJobs(namespace);
    Files.write(Paths.get(resultDir.toString(), namespace + "_jobs.log"),
        dump(listJobs).getBytes(StandardCharsets.UTF_8)
    );
    // get deployments
    V1DeploymentList listDeployments = Kubernetes.listDeployments(namespace);
    Files.write(Paths.get(resultDir.toString(), namespace + "_deploy.log"),
        dump(listDeployments).getBytes(StandardCharsets.UTF_8)
    );
    // get replicasets
    V1ReplicaSetList listReplicaSets = Kubernetes.listReplicaSets(namespace);
    Files.write(Paths.get(resultDir.toString(), namespace + "_rs.log"),
        dump(listReplicaSets).getBytes(StandardCharsets.UTF_8)
    );
    // get Domain
    DomainList listDomains = Kubernetes.listDomains(namespace);
    Files.write(Paths.get(resultDir.toString(), namespace + "_domain.log"),
        dump(listDomains).getBytes(StandardCharsets.UTF_8)
    );
    // get domain pods
    V1PodList listPods = Kubernetes.listPods(namespace, null);
    List<V1Pod> domainPods = listPods.getItems();
    for (V1Pod pod : domainPods) {
      String podName = pod.getMetadata().getName();
      String podLog = Kubernetes.getPodLog(podName, namespace);
      Files.write(Paths.get(resultDir.toString(), namespace + podName + ".log"),
          dump(podLog).getBytes(StandardCharsets.UTF_8)
      );
    }

  }

}

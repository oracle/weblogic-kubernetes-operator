// Copyright (c) 2021, 2023, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.


package oracle.weblogic.kubernetes.utils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Random;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;

import io.kubernetes.client.custom.Quantity;
import io.kubernetes.client.openapi.models.V1Container;
import io.kubernetes.client.openapi.models.V1HostPathVolumeSource;
import io.kubernetes.client.openapi.models.V1NFSVolumeSource;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1PersistentVolume;
import io.kubernetes.client.openapi.models.V1PersistentVolumeClaim;
import io.kubernetes.client.openapi.models.V1PersistentVolumeClaimSpec;
import io.kubernetes.client.openapi.models.V1PersistentVolumeSpec;
import io.kubernetes.client.openapi.models.V1ResourceRequirements;
import io.kubernetes.client.openapi.models.V1SecurityContext;
import io.kubernetes.client.openapi.models.V1VolumeMount;
import oracle.weblogic.kubernetes.logging.LoggingFacade;

import static java.nio.file.Files.createDirectories;
import static oracle.weblogic.kubernetes.TestConstants.FSS_DIR;
import static oracle.weblogic.kubernetes.TestConstants.IMAGE_PULL_POLICY;
import static oracle.weblogic.kubernetes.TestConstants.NFS_SERVER;
import static oracle.weblogic.kubernetes.TestConstants.OKD;
import static oracle.weblogic.kubernetes.TestConstants.OKE_CLUSTER;
import static oracle.weblogic.kubernetes.TestConstants.PV_ROOT;
import static oracle.weblogic.kubernetes.TestConstants.WEBLOGIC_IMAGE_TO_USE_IN_SPEC;
import static oracle.weblogic.kubernetes.actions.TestActions.createPersistentVolume;
import static oracle.weblogic.kubernetes.actions.TestActions.createPersistentVolumeClaim;
import static oracle.weblogic.kubernetes.actions.TestActions.deletePersistentVolume;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.pvExists;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.pvNotExists;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.pvcExists;
import static oracle.weblogic.kubernetes.utils.CommonMiiTestUtils.createJobToChangePermissionsOnPvHostPath;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.testUntil;
import static oracle.weblogic.kubernetes.utils.ImageUtils.createTestRepoSecret;
import static oracle.weblogic.kubernetes.utils.ThreadSafeLogger.getLogger;
import static org.apache.commons.io.FileUtils.deleteDirectory;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

public class PersistentVolumeUtils {
  /**
   * Create a persistent volume and persistent volume claim.
   *
   * @param v1pv V1PersistentVolume object to create the persistent volume
   * @param v1pvc V1PersistentVolumeClaim object to create the persistent volume claim
   * @param labelSelector String containing the labels the PV is decorated with
   * @param namespace the namespace in which the persistence volume claim to be created
   *
   **/
  public static void createPVPVCAndVerify(V1PersistentVolume v1pv,
                                          V1PersistentVolumeClaim v1pvc,
                                          String labelSelector,
                                          String namespace) {
    LoggingFacade logger = getLogger();
    assertNotNull(v1pv, "v1pv is null");
    assertNotNull(v1pvc, "v1pvc is null");

    String pvName = v1pv.getMetadata().getName();
    String pvcName = v1pvc.getMetadata().getName();

    logger.info("Creating persistent volume {0}", pvName);
    assertTrue(assertDoesNotThrow(() -> createPersistentVolume(v1pv),
        "Persistent volume creation failed with ApiException "),
        "PersistentVolume creation failed");

    logger.info("Creating persistent volume claim {0}", pvcName);
    assertTrue(assertDoesNotThrow(() -> createPersistentVolumeClaim(v1pvc),
        "Persistent volume claim creation failed with ApiException"),
        "PersistentVolumeClaim creation failed");

    // check the persistent volume and persistent volume claim exist
    testUntil(
        assertDoesNotThrow(() -> pvExists(pvName, labelSelector),
          String.format("pvExists failed with ApiException when checking pv %s", pvName)),
        logger,
        "persistent volume {0} exists",
        pvName);

    testUntil(
        assertDoesNotThrow(() -> pvcExists(pvcName, namespace),
          String.format("pvcExists failed with ApiException when checking pvc %s in namespace %s",
            pvcName, namespace)),
        logger,
        "persistent volume claim {0} exists in namespace {1}",
        pvcName,
        namespace);
  }

  /**
   * Create a persistent volume and persistent volume claim.
   *
   * @param v1pv V1PersistentVolume object to create the persistent volume
   * @param v1pvc V1PersistentVolumeClaim object to create the persistent volume claim
   * @param labelSelector String containing the labels the PV is decorated with
   * @param namespace the namespace in which the persistence volume claim to be created
   * @param storageClassName the name for storage class
   * @param pvHostPath path to pv dir if hostpath is used, ignored if nfs
   *
   **/
  public static void createPVPVCAndVerify(V1PersistentVolume v1pv,
                                          V1PersistentVolumeClaim v1pvc,
                                          String labelSelector,
                                          String namespace, String storageClassName, Path pvHostPath) {
    LoggingFacade logger = getLogger();
    if (!OKE_CLUSTER && !OKD) {
      logger.info("Creating PV directory {0}", pvHostPath);
      assertDoesNotThrow(() -> deleteDirectory(pvHostPath.toFile()), "deleteDirectory failed with IOException");
      assertDoesNotThrow(() -> createDirectories(pvHostPath), "createDirectories failed with IOException");
    }
    if (OKE_CLUSTER) {
      String fssDir = FSS_DIR[new Random().nextInt(FSS_DIR.length)];
      logger.info("Using FSS PV directory {0}", fssDir);
      v1pv.getSpec()
          .storageClassName("oci-fss")
          .nfs(new V1NFSVolumeSource()
              .path(fssDir)
              .server(NFS_SERVER)
              .readOnly(false));
    } else if (OKD) {
      v1pv.getSpec()
          .storageClassName("okd-nfsmnt")
          .nfs(new V1NFSVolumeSource()
              .path(PV_ROOT)
              .server(NFS_SERVER)
              .readOnly(false));
    } else {
      v1pv.getSpec()
          .storageClassName(storageClassName)
          .hostPath(new V1HostPathVolumeSource()
              .path(pvHostPath.toString()));
    }
    if (OKE_CLUSTER) {
      v1pvc.getSpec()
          .storageClassName("oci-fss");
    } else if (OKD) {
      v1pvc.getSpec()
          .storageClassName("okd-nfsmnt");
    } else {
      v1pvc.getSpec()
          .storageClassName(storageClassName);
    }
    createPVPVCAndVerify(v1pv, v1pvc, labelSelector, namespace);
  }

  /** Create a persistent volume.
   * @param pvName name of the persistent volume to create
   * @param domainUid domain UID
   * @param className name of the class to call this method
   */
  public static void createPV(String pvName, String domainUid, String className) {

    LoggingFacade logger = getLogger();

    logger.info("deleting persistent volume pvName {0} if it exists", pvName);
    deletePersistentVolume(pvName);
    testUntil(
        assertDoesNotThrow(() -> pvNotExists(pvName, null),
            String.format("pvNotExists failedfor pv %s", pvName)), logger, "pv {0} to be deleted", pvName);

    logger.info("creating persistent volume for pvName {0}, domainUid: {1}, className: {2}",
        pvName, domainUid, className);
    Path pvHostPath = null;
    // when tests are running in local box the PV directories need to exist
    if (!OKE_CLUSTER && !OKD) {
      pvHostPath = createPVHostPathDir(pvName, className);
    }

    V1PersistentVolume v1pv = new V1PersistentVolume()
        .spec(new V1PersistentVolumeSpec()
            .addAccessModesItem("ReadWriteMany")
            .volumeMode("Filesystem")
            .putCapacityItem("storage", Quantity.fromString("5Gi"))
            .persistentVolumeReclaimPolicy("Recycle")
            .accessModes(Arrays.asList("ReadWriteMany")))
        .metadata(new V1ObjectMeta()
            .name(pvName)
            .putLabelsItem("weblogic.resourceVersion", "domain-v2")
            .putLabelsItem("weblogic.domainUid", domainUid));
    setVolumeSource(pvHostPath, v1pv);
    boolean success = assertDoesNotThrow(() -> createPersistentVolume(v1pv),
        "Failed to create persistent volume");
    assertTrue(success, "PersistentVolume creation failed");

    testUntil(
        assertDoesNotThrow(() -> pvExists(pvName, null),
          String.format("pvExists failed with ApiException when checking pv %s", pvName)),
        logger,
        "persistent volume {0} exists",
        pvName);
  }

  public static void setVolumeSource(Path pvHostPath, V1PersistentVolume v1pv) {
    setVolumeSource(pvHostPath,v1pv, "weblogic-domain-storage-class");
  }

  private static void setVolumeSource(Path pvHostPath, V1PersistentVolume v1pv, String storageClassName) {
    if (OKE_CLUSTER) {
      String fssDir = FSS_DIR[new Random().nextInt(FSS_DIR.length)];
      LoggingFacade logger = getLogger();
      logger.info("Using FSS PV directory {0}", fssDir);
      logger.info("Using NFS_SERVER  {0}", NFS_SERVER);
      v1pv.getSpec()
              .storageClassName("oci-fss")
              .nfs(new V1NFSVolumeSource()
                      .path(fssDir)
                      .server(NFS_SERVER)
                      .readOnly(false));
    } else if (OKD) {
      v1pv.getSpec()
              .storageClassName("okd-nfsmnt")
              .nfs(new V1NFSVolumeSource()
                      .path(PV_ROOT)
                      .server(NFS_SERVER)
                      .readOnly(false));
    } else {
      v1pv.getSpec()
              .storageClassName(storageClassName)
              .hostPath(new V1HostPathVolumeSource()
                      .path(pvHostPath.toString()));
    }
  }

  /**
   * Create PV hostPath directory.
   * @param pvName Persistent Volume Name
   * @param className Test class name to create the PV
   * @return Path object representing PV host path
   */
  @Nonnull
  public static Path createPVHostPathDir(String pvName, String className) {
    Path pvHostPath = null;
    LoggingFacade logger = getLogger();
    try {
      pvHostPath = Files.createDirectories(Paths.get(
          PV_ROOT, className, pvName));
      logger.info("Creating PV directory host path {0}", pvHostPath);
      deleteDirectory(pvHostPath.toFile());
      createDirectories(pvHostPath);
    } catch (IOException ioex) {
      logger.severe(ioex.getMessage());
      fail("Create persistent volume host path failed");
    }
    return pvHostPath;
  }

  /**
   * Create a persistent volume claim.
   *
   * @param pvName name of the persistent volume
   * @param pvcName name of the persistent volume claim to create
   * @param domainUid UID of the WebLogic domain
   * @param namespace name of the namespace in which to create the persistent volume claim
   */
  public static void createPVC(String pvName, String pvcName, String domainUid, String namespace) {

    LoggingFacade logger = getLogger();
    logger.info("creating persistent volume claim for pvName {0}, pvcName {1}, "
        + "domainUid: {2}, namespace: {3}", pvName, pvcName, domainUid, namespace);
    V1PersistentVolumeClaim v1pvc = new V1PersistentVolumeClaim()
        .spec(new V1PersistentVolumeClaimSpec()
            .addAccessModesItem("ReadWriteMany")
            .volumeName(pvName)
            .resources(new V1ResourceRequirements()
                .putRequestsItem("storage", Quantity.fromString("5Gi"))))
        .metadata(new V1ObjectMeta()
            .name(pvcName)
            .namespace(namespace)
            .putLabelsItem("weblogic.resourceVersion", "domain-v2")
            .putLabelsItem("weblogic.domainUid", domainUid));

    if (OKE_CLUSTER) {
      v1pvc.getSpec()
          .storageClassName("oci-fss");
    } else if (OKD) {
      v1pvc.getSpec()
          .storageClassName("okd-nfsmnt");
    } else {
      v1pvc.getSpec()
          .storageClassName("weblogic-domain-storage-class");
    }
    boolean success = assertDoesNotThrow(() -> createPersistentVolumeClaim(v1pvc),
        "Failed to create persistent volume claim");
    assertTrue(success, "PersistentVolumeClaim creation failed");

    // wait for PVC exists
    testUntil(
        assertDoesNotThrow(() -> pvcExists(pvcName, namespace),
          String.format("pvcExists failed with ApiException when checking pvc %s in namespace %s",
            pvcName, namespace)),
        logger,
        "persistent volume claim {0} exists in namespace {1}",
        pvcName,
        namespace);
  }

  /**
   * Create container to fix pvc owner for pod.
   *
   * @param pvName name of pv
   * @param mountPath mounting path for pv
   * @return container object with required ownership based on OKE_CLUSTER variable value.
   */
  public static synchronized V1Container createfixPVCOwnerContainer(String pvName, String mountPath) {
    String argCommand = "chown -R 1000:0 " + mountPath;
    if (OKE_CLUSTER) {
      argCommand = "chown 1000:0 " + mountPath
          + "/. && find "
          + mountPath
          + "/. -maxdepth 1 ! -name '.snapshot' ! -name '.' -print0 | xargs -r -0  chown -R 1000:0";
    }
    return createfixPVCOwnerContainer(pvName, mountPath, argCommand);
  }

  /**
   * Create container to fix pvc owner for pod.
   *
   * @param pvName name of pv
   * @param mountPath mounting path for pv
   * @param command to run for ownership
   * @return container object with required ownership based on OKE_CLUSTER variable value.
   */
  public static synchronized V1Container createfixPVCOwnerContainer(String pvName, String mountPath, String command) {

    V1Container container = new V1Container()
        .name("fix-pvc-owner") // change the ownership of the pv to opc:opc
        .image(WEBLOGIC_IMAGE_TO_USE_IN_SPEC)
        .imagePullPolicy(IMAGE_PULL_POLICY)
        .addCommandItem("/bin/sh")
        .addArgsItem("-c")
        .addArgsItem(command)
        .volumeMounts(Arrays.asList(
            new V1VolumeMount()
                .name(pvName)
                .mountPath(mountPath)))
        .securityContext(new V1SecurityContext()
            .runAsGroup(0L)
            .runAsUser(0L));
    return container;
  }

  /**
   * Create a persistent volume and persistent volume claim.
   * @param nameSuffix unique nameSuffix for pv and pvc to create
   * @param labels pv and pvc labels
   * @param namespace pv and pvc namespace
   * @param className - class name
   * @throws IOException when creating pv path fails
   */
  public static void createPvAndPvc(String nameSuffix, String namespace,
                                    HashMap<String,String> labels, String className)
      throws IOException {
    LoggingFacade logger = getLogger();
    logger.info("creating persistent volume and persistent volume claim");
    // create persistent volume and persistent volume claims
    // when tests are running in local box the PV directories need to exist
    Path pvHostPath = null;
    if (!OKE_CLUSTER && !OKD) {
      pvHostPath = createPVHostPathDir("pv-test" + nameSuffix, className);
    }

    V1PersistentVolume v1pv = new V1PersistentVolume()
        .spec(new V1PersistentVolumeSpec()
            .addAccessModesItem("ReadWriteMany")
            .volumeMode("Filesystem")
            .putCapacityItem("storage", Quantity.fromString("10Gi"))
            .persistentVolumeReclaimPolicy("Retain")
            .accessModes(Arrays.asList("ReadWriteMany")))
        .metadata(new V1ObjectMeta()
            .name("pv-test" + nameSuffix)
            .namespace(namespace));

    setVolumeSource(pvHostPath, v1pv, nameSuffix);
    boolean hasLabels = false;
    String labelSelector = null;
    if (labels != null || !labels.isEmpty()) {
      hasLabels = true;
      v1pv.getMetadata().setLabels(labels);
      labelSelector = labels.entrySet()
          .stream()
          .map(e -> e.getKey() + "="
              + e.getValue())
          .collect(Collectors.joining(","));
    }

    V1PersistentVolumeClaim v1pvc = new V1PersistentVolumeClaim()
        .spec(new V1PersistentVolumeClaimSpec()
            .addAccessModesItem("ReadWriteMany")
            .volumeName("pv-test" + nameSuffix)
            .resources(new V1ResourceRequirements()
                .putRequestsItem("storage", Quantity.fromString("10Gi"))))
        .metadata(new V1ObjectMeta()
            .name("pvc-" + nameSuffix)
            .namespace(namespace));
    if (hasLabels) {
      v1pvc.getMetadata().setLabels(labels);
    }
    if (OKE_CLUSTER) {
      v1pvc.getSpec()
          .storageClassName("oci-fss");
    } else if (OKD) {
      v1pvc.getSpec()
          .storageClassName("okd-nfsmnt");
    } else {
      v1pvc.getSpec()
          .storageClassName(nameSuffix);
    }

    createPVPVCAndVerify(v1pv, v1pvc, labelSelector, namespace);
    if (nameSuffix.contains("grafana") || nameSuffix.contains("prometheus")) {
      String mountPath = "/data";
      if (nameSuffix.contains("grafana")) {
        mountPath = "/var/lib/grafana";
      }
      String argCommand = "chown -R 1000:1000 " + mountPath;
      if (OKE_CLUSTER) {
        argCommand = "chown 1000:1000 " + mountPath
            + "/. && find "
            + mountPath
            + "/. -maxdepth 1 ! -name '.snapshot' ! -name '.' -print0 | xargs -r -0  chown -R 1000:1000";
      }
      createTestRepoSecret(namespace);
      createJobToChangePermissionsOnPvHostPath("pv-test" + nameSuffix,
          "pvc-" + nameSuffix, namespace,
          mountPath, argCommand);
    }
  }
}

// Copyright (c) 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

import io.kubernetes.client.custom.Quantity;
import io.kubernetes.client.openapi.models.*;
import oracle.weblogic.domain.AdminServer;
import oracle.weblogic.domain.Cluster;
import oracle.weblogic.domain.Configuration;
import oracle.weblogic.domain.Domain;
import oracle.weblogic.domain.DomainSpec;
import oracle.weblogic.domain.Model;
import oracle.weblogic.domain.ServerPod;
import oracle.weblogic.kubernetes.actions.ActionConstants;
import oracle.weblogic.kubernetes.actions.impl.OperatorParams;
import oracle.weblogic.kubernetes.actions.impl.Prometheus;
import oracle.weblogic.kubernetes.actions.impl.PrometheusParams;
import oracle.weblogic.kubernetes.actions.impl.primitive.HelmParams;
import oracle.weblogic.kubernetes.actions.impl.primitive.Kubernetes;
import oracle.weblogic.kubernetes.annotations.IntegrationTest;
import oracle.weblogic.kubernetes.annotations.Namespaces;
import oracle.weblogic.kubernetes.extensions.LoggedTest;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import static oracle.weblogic.kubernetes.TestConstants.*;
import static oracle.weblogic.kubernetes.TestConstants.OPERATOR_CHART_DIR;

import static oracle.weblogic.kubernetes.actions.TestActions.createPersistentVolume;
import static oracle.weblogic.kubernetes.actions.TestActions.createPersistentVolumeClaim;
import static oracle.weblogic.kubernetes.actions.TestActions.uninstallNginx;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.checkPodExists;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.checkPodReady;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.checkServiceExists;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.createDockerRegistrySecret;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.createDomainAndVerify;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.createIngressForDomainAndVerify;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.createMiiImageAndVerify;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.createSecretWithUsernamePassword;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.dockerLoginAndPushImageToRegistry;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.installAndVerifyNginx;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.installAndVerifyOperator;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.installAndVerifyPrometheus;

import static oracle.weblogic.kubernetes.utils.CommonTestUtils.scaleAndVerifyCluster;
import static oracle.weblogic.kubernetes.utils.TestUtils.callWebAppAndCheckForServerNameInResponse;
import static oracle.weblogic.kubernetes.utils.TestUtils.getNextFreePort;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Verify the model in image domain with multiple clusters can be scaled up and down.
 * Also verify the sample application can be accessed via NGINX ingress controller.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("Verify WebLogic Metric is processed as expected by MonitoringExporter via Prometheus and Grafana")
@IntegrationTest
class ItMonitoringExporter implements LoggedTest {


  // domain constants
  private static final String domainUid = "domain1";
  private static final int NUMBER_OF_CLUSTERS = 2;
  private static final String CLUSTER_NAME_PREFIX = "cluster-";
  private static final int MANAGED_SERVER_PORT = 8001;
  private static final int replicaCount = 2;

  private static String domainNamespace = null;
  private static HelmParams nginxHelmParams = null;
  private static int nodeportshttp = 0;
  private static List<String> ingressHostList = null;

  private String curlCmd = null;
  private String monitoringNS = "monitortestns";
  HelmParams promeHelmParams = null;
  private final String pvName = "pv-testprometheus"; // name of the persistent volume for prometheus server
  private final String pvcName = "pvc-prometheus"; // name of the persistent volume claim for prometheus server
  private final String pvNameAlert = "pv-testalertmanager"; // name of the persistent volume for alertmanager
  private final String pvcNameAlert = "pvc-alertmanager"; // name of the persistent volume claim for alertmanager

  /**
   * Install operator and NGINX. Create model in image domain with multiple clusters.
   * Create ingress for the domain.
   *
   * @param namespaces list of namespaces created by the IntegrationTestWatcher by the
   *                   JUnit engine parameter resolution mechanism
   */
  @BeforeAll
  public static void initAll(@Namespaces(3) List<String> namespaces) {

    // get a unique operator namespace
    logger.info("Get a unique namespace for operator");
    assertNotNull(namespaces.get(0), "Namespace list is null");
    String opNamespace = namespaces.get(0);

    // get a unique domain namespace
    logger.info("Get a unique namespace for WebLogic domain");
    assertNotNull(namespaces.get(1), "Namespace list is null");
    domainNamespace = namespaces.get(1);

    // get a unique NGINX namespace
    logger.info("Get a unique namespace for NGINX");
    assertNotNull(namespaces.get(2), "Namespace list is null");
    String nginxNamespace = namespaces.get(2);

    // install and verify operator
    installAndVerifyOperator(opNamespace, domainNamespace);

    // get a free node port for NGINX
    nodeportshttp = getNextFreePort(30305, 30405);
    int nodeportshttps = getNextFreePort(30443, 30543);

    // install and verify NGINX
    nginxHelmParams = installAndVerifyNginx(nginxNamespace, nodeportshttp, nodeportshttps);

  }

  @Test
  @DisplayName("Install Prometheus and verify it running")
  public void testCreatePrometheus() throws Exception {
    Kubernetes.createNamespace(monitoringNS);
    createPVandPVCPrometheus();
    int nodeportalertmanserver = getNextFreePort(30400, 30600);
    int nodeportserver = getNextFreePort(32400, 32600);

    HelmParams promHelmParams = installAndVerifyPrometheus("prometheus",
         monitoringNS,
         ActionConstants.RESOURCE_DIR + "/exporter/promvalues.yaml",
         null,
         nodeportserver,
         nodeportalertmanserver);
    logger.info("Prometheus is running");
    Prometheus.uninstall(promHelmParams);
    logger.info("Prometheus is uninstalled");
  }


  /**
   * TODO: remove this after Sankar's PR is merged
   * The cleanup framework does not uninstall NGINX release. Do it here for now.
   */
  @AfterAll
  public void tearDownAll() {
    // uninstall NGINX release

    if (nginxHelmParams != null) {
      assertThat(uninstallNginx(nginxHelmParams))
          .as("Test uninstallNginx returns true")
          .withFailMessage("uninstallNginx() did not return true")
          .isTrue();
    }
  }

  /**
   * Create a persistent volume and persistent volume claim.
   * @throws IOException when creating pv path fails
   */
  private void createPVandPVCPrometheus() throws IOException {
    logger.info("creating persistent volume and persistent volume claim");

    Path pvHostPath = Files.createDirectories(Paths.get(
        PV_ROOT, this.getClass().getSimpleName(), "testprometheus"));
    logger.info("Creating PV directory {0}", pvHostPath);
    FileUtils.deleteDirectory(pvHostPath.toFile());
    Files.createDirectories(pvHostPath);
    V1PersistentVolume v1pv = new V1PersistentVolume()
        .spec(new V1PersistentVolumeSpec()
            .addAccessModesItem("ReadWriteMany")
            .storageClassName("prometheus")
            .volumeMode("Filesystem")
            .putCapacityItem("storage", Quantity.fromString("10Gi"))
            .persistentVolumeReclaimPolicy("Retain")
            .accessModes(Arrays.asList("ReadWriteMany"))
            .hostPath(new V1HostPathVolumeSource()
                .path(pvHostPath.toString())))
        .metadata(new V1ObjectMetaBuilder()
            .withName(pvName)
            .withNamespace(monitoringNS)
            .build());

    V1PersistentVolume finalV1pv = v1pv;
    boolean success = assertDoesNotThrow(
        () -> createPersistentVolume(finalV1pv),
        "Persistent volume creation failed, "
            + "look at the above console log messages for failure reason in ApiException responsebody"
    );
    assertTrue(success, "PersistentVolume creation failed");


    V1PersistentVolumeClaim v1pvc = new V1PersistentVolumeClaim()
        .spec(new V1PersistentVolumeClaimSpec()
            .addAccessModesItem("ReadWriteMany")
            .storageClassName("prometheus")
            .volumeName("pv-testprometheus")
            .resources(new V1ResourceRequirements()
                .putRequestsItem("storage", Quantity.fromString("10Gi"))))
        .metadata(new V1ObjectMetaBuilder()
            .withName("pvc-prometheus")
            .withNamespace(monitoringNS)
            .build());


    V1PersistentVolumeClaim finalV1pvc = v1pvc;
    success = assertDoesNotThrow(
        () -> createPersistentVolumeClaim(finalV1pvc),
        "Persistent volume claim creation failed for prometheus server, "
            + "look at the above console log messages for failure reason in ApiException response body"
    );
    assertTrue(success, "PersistentVolumeClaim creation failed for prometheus server");

    v1pv = new V1PersistentVolume()
        .spec(new V1PersistentVolumeSpec()
            .addAccessModesItem("ReadWriteMany")
            .storageClassName("alertmanager")
            .volumeMode("Filesystem")
            .putCapacityItem("storage", Quantity.fromString("10Gi"))
            .persistentVolumeReclaimPolicy("Retain")
            .accessModes(Arrays.asList("ReadWriteMany"))
            .hostPath(new V1HostPathVolumeSource()
                .path(pvHostPath.toString())))
        .metadata(new V1ObjectMetaBuilder()
            .withName("pv-testalertmanager")
            .withNamespace(monitoringNS)
            .build());

    V1PersistentVolume finalV1pv1 = v1pv;
    success = assertDoesNotThrow(
        () -> createPersistentVolume(finalV1pv1),
        "Persistent volume creation failed for alertmanager, "
            + "look at the above console log messages for failure reason in ApiException responsebody"
    );
    assertTrue(success, "PersistentVolume creation failed for alertmanager");

    v1pvc = new V1PersistentVolumeClaim()
        .spec(new V1PersistentVolumeClaimSpec()
            .addAccessModesItem("ReadWriteMany")
            .storageClassName("alertmanager")
            .volumeName("pv-testalertmanager")
            .resources(new V1ResourceRequirements()
                .putRequestsItem("storage", Quantity.fromString("10Gi"))))
        .metadata(new V1ObjectMetaBuilder()
            .withName("pvc-alertmanager")
            .withNamespace(monitoringNS)
            .build());


    V1PersistentVolumeClaim finalV1pvc1 = v1pvc;
    success = assertDoesNotThrow(
        () -> createPersistentVolumeClaim(finalV1pvc1),
        "Persistent volume claim creation failed for alertmanager, "
            + "look at the above console log messages for failure reason in ApiException responsebody"
    );
    assertTrue(success, "PersistentVolumeClaim creation failed for alertmanager");
  }

}
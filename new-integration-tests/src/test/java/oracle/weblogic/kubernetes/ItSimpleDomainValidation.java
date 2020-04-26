// Copyright (c) 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes;

import java.util.List;

import io.kubernetes.client.custom.Quantity;
import io.kubernetes.client.openapi.models.V1HostPathVolumeSource;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1ObjectMetaBuilder;
import io.kubernetes.client.openapi.models.V1PersistentVolume;
import io.kubernetes.client.openapi.models.V1PersistentVolumeClaim;
import io.kubernetes.client.openapi.models.V1PersistentVolumeClaimSpec;
import io.kubernetes.client.openapi.models.V1PersistentVolumeSpec;
import io.kubernetes.client.openapi.models.V1ResourceRequirements;
import io.kubernetes.client.openapi.models.V1ServiceAccount;
import oracle.weblogic.domain.Domain;
import oracle.weblogic.domain.DomainSpec;
import oracle.weblogic.kubernetes.actions.TestActions;
import oracle.weblogic.kubernetes.actions.impl.primitive.Kubernetes;
import oracle.weblogic.kubernetes.annotations.IntegrationTest;
import oracle.weblogic.kubernetes.annotations.Namespaces;
import oracle.weblogic.kubernetes.annotations.tags.Slow;
import oracle.weblogic.kubernetes.extensions.LoggedTest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static java.util.concurrent.TimeUnit.MINUTES;
import static java.util.concurrent.TimeUnit.SECONDS;
import static oracle.weblogic.kubernetes.actions.TestActions.createDomainCustomResource;
import static oracle.weblogic.kubernetes.actions.TestActions.deleteDomainCustomResource;
import static oracle.weblogic.kubernetes.actions.TestActions.deletePersistentVolume;
import static oracle.weblogic.kubernetes.actions.TestActions.deletePersistentVolumeClaim;
import static oracle.weblogic.kubernetes.actions.TestActions.deleteServiceAccount;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.domainExists;
import static org.awaitility.Awaitility.with;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Simple validation of basic domain functions")
// Every test class needs to tagged with this annotation for log collection, diagnostic messages logging
// and namespace creation.
@IntegrationTest
class ItSimpleDomainValidation implements LoggedTest {

  final String domainUid = "domain1";
  String namespace;
  String serviceAccountName;
  V1ServiceAccount serviceAccount;
  String pvcName;
  String pvName;

  /**
   * Setup for test suite. Creates service account, namespace, and persistent volumes.
   * @param namespaces injected by Junit extension
   */
  @BeforeAll
  public void setup(@Namespaces(1) List<String> namespaces) {

    // get a new unique namespace
    logger.info("Creating unique namespace for Operator");
    assertNotNull(namespaces.get(0), "Namespace list is null");
    namespace = namespaces.get(0);

    // Create a service account for the unique namespace
    serviceAccountName = namespace + "-sa";
    serviceAccount = assertDoesNotThrow(
        () -> Kubernetes.createServiceAccount(new V1ServiceAccount()
            .metadata(new V1ObjectMeta().namespace(namespace).name(serviceAccountName))));
    logger.info("Created service account: {0}", serviceAccount.getMetadata().getName());

    // create persistent volume and persistent volume claim
    pvcName = domainUid + "-pvc"; // name of the persistent volume claim
    pvName = domainUid + "-pv"; // name of the persistent volume

    V1PersistentVolumeClaim v1pvc = new V1PersistentVolumeClaim()
        .spec(new V1PersistentVolumeClaimSpec()
            .addAccessModesItem("ReadWriteMany")
            .storageClassName(domainUid + "-weblogic-domain-storage-class")
            .volumeName(domainUid + "-weblogic-pv")
            .resources(new V1ResourceRequirements()
                .putRequestsItem("storage", Quantity.fromString("10Gi"))))
        .metadata(new V1ObjectMetaBuilder()
            .withName(pvcName)
            .withNamespace(namespace)
            .build()
            .putLabelsItem("weblogic.resourceVersion", "domain-v2")
            .putLabelsItem("weblogic.domainUid", domainUid));

    boolean success = assertDoesNotThrow(
        () -> TestActions.createPersistentVolumeClaim(v1pvc),
        "Persistent volume claim creation failed, "
        + "look at the above console log messages for failure reason in ApiException responsebody"
    );
    assertTrue(success, "PersistentVolumeClaim creation failed");

    V1PersistentVolume v1pv = new V1PersistentVolume()
        .spec(new V1PersistentVolumeSpec()
            .addAccessModesItem("ReadWriteMany")
            .storageClassName(domainUid + "-weblogic-domain-storage-class")
            .volumeMode("Filesystem")
            .putCapacityItem("storage", Quantity.fromString("10Gi"))
            .persistentVolumeReclaimPolicy("Recycle")
            .hostPath(new V1HostPathVolumeSource()
                .path(System.getProperty("java.io.tmpdir") + "/" + domainUid + "-persistentVolume")))
        .metadata(new V1ObjectMetaBuilder()
            .withName(pvName)
            .withNamespace(namespace)
            .build()
            .putLabelsItem("weblogic.resourceVersion", "domain-v2")
            .putLabelsItem("weblogic.domainUid", domainUid));
    success = assertDoesNotThrow(
        () -> TestActions.createPersistentVolume(v1pv),
        "Persistent volume creation failed, "
        + "look at the above console log messages for failure reason in ApiException responsebody"
    );
    assertTrue(success, "PersistentVolume creation failed");
  }

  /**
   * Create a simple domain and checks if pods are coming up.
   */
  @Test
  @DisplayName("Create a domain")
  @Slow
  public void testCreatingDomain() {

    // create the domain CR
    V1ObjectMeta metadata = new V1ObjectMetaBuilder()
        .withName(domainUid)
        .withNamespace(namespace)
        .build();
    DomainSpec domainSpec = new DomainSpec()
        .domainHome("/shared/domains/sample-domain1")
        .domainHomeInImage(false)
        .image("store/oracle/weblogic:12.2.1.3")
        .imagePullPolicy("IfNotPresent");
    Domain domain = new Domain()
        .apiVersion("weblogic.oracle/v7")
        .kind("Domain")
        .metadata(metadata)
        .spec(domainSpec);
    boolean success = assertDoesNotThrow(
        () -> createDomainCustomResource(domain),
        "Domain failed to be created, "
        + "look at the above console log messages for failure reason in ApiException responsebody"
    );
    assertTrue(success);

    // wait for the domain to exist
    with().pollDelay(2, SECONDS)
        .and().with().pollInterval(10, SECONDS)
        .conditionEvaluationListener(
            condition -> logger.info(
                "Waiting for domain to be running (elapsed time {0} ms, remaining time {1} ms)",
                condition.getElapsedTimeInMS(),
                condition.getRemainingTimeInMS()))
        // and here we can set the maximum time we are prepared to wait
        .await().atMost(5, MINUTES)
        // operatorIsRunning() is one of our custom, reusable assertions
        .until(domainExists(domainUid, "v7", namespace));
  }

  /**
   * Delete artifacts.
   */
  @AfterAll
  public void cleanup() {

    // Delete domain custom resource
    assertTrue(deleteDomainCustomResource(domainUid, namespace), "Domain failed to be deleted, "
        + "look at the above console log messages for failure reason in ApiException responsebody");
    logger.info("Deleted Domain Custom Resource {0} from {1}", domainUid, namespace);

    // Delete service account from unique namespace
    assertTrue(deleteServiceAccount(serviceAccount.getMetadata().getName(),
        serviceAccount.getMetadata().getNamespace()), "Service account failed to be deleted, "
        + "look at the above console log messages for failure reason in ApiException responsebody");
    logger.info("Deleted service account \'" + serviceAccount.getMetadata().getName()
        + "\' in namespace: " + serviceAccount.getMetadata().getNamespace());

    // Delete the persistent volume claim and persistent volume
    assertTrue(deletePersistentVolumeClaim(pvcName, namespace),
        "Persistent volume claim deletion failed, "
        + "look at the above console log messages for failure reason in ApiException responsebody");

    assertTrue(deletePersistentVolume(pvName), "Persistent volume deletion failed, "
        + "look at the above console log messages for failure reason in ApiException responsebody");

    // Delete namespace
    assertTrue(TestActions.deleteNamespace(namespace), "Namespace failed to be deleted, "
        + "look at the above console log messages for failure reason in ApiException responsebody");
    logger.info("Deleted namespace: {0}", namespace);
  }
}

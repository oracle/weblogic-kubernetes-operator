// Copyright (c) 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes;

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
import oracle.weblogic.kubernetes.annotations.tags.Slow;
import oracle.weblogic.kubernetes.extensions.LoggedTest;
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
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Simple validation of basic domain functions")
@IntegrationTest
class ItSimpleDomainValidation implements LoggedTest {

  @Test
  @DisplayName("Create a domain")
  @Slow
  public void testCreatingDomain() {

    final String domainUID = "domain1";

    // get a new unique namespace
    final String namespace = assertDoesNotThrow(TestActions::createUniqueNamespace,
        "Failed to create unique namespace due to ApiException");
    logger.info("Got a new namespace called {0}", namespace);

    // Create a service account for the unique namespace
    final String serviceAccountName = namespace + "-sa";
    final V1ServiceAccount serviceAccount = assertDoesNotThrow(
        () -> Kubernetes.createServiceAccount(new V1ServiceAccount()
            .metadata(new V1ObjectMeta().namespace(namespace).name(serviceAccountName))));
    logger.info("Created service account: {0}", serviceAccount.getMetadata().getName());

    // create persistent volume and persistent volume claim
    final String pvcName = domainUID + "-pvc"; // name of the persistent volume claim
    final String pvName = domainUID + "-pv"; // name of the persistent volume

    V1PersistentVolumeClaim v1pvc = new V1PersistentVolumeClaim()
        .spec(new V1PersistentVolumeClaimSpec()
            .addAccessModesItem("ReadWriteMany")
            .storageClassName(domainUID + "-weblogic-domain-storage-class")
            .volumeName(domainUID + "-weblogic-pv")
            .resources(new V1ResourceRequirements()
                .putRequestsItem("storage", Quantity.fromString("10Gi"))))
        .metadata(new V1ObjectMetaBuilder()
            .withName(pvcName)
            .withNamespace(namespace)
            .build()
            .putLabelsItem("weblogic.resourceVersion", "domain-v2")
            .putLabelsItem("weblogic.domainUID", domainUID));

    boolean success = assertDoesNotThrow(
        () -> TestActions.createPersistentVolumeClaim(v1pvc),
        "Persistent volume claim creation failed, "
            + "look at the above console log messages for failure reason in ApiException responsebody"
    );
    assertTrue(success, "PersistentVolumeClaim creation failed");

    V1PersistentVolume v1pv = new V1PersistentVolume()
        .spec(new V1PersistentVolumeSpec()
            .addAccessModesItem("ReadWriteMany")
            .storageClassName(domainUID + "-weblogic-domain-storage-class")
            .volumeMode("Filesystem")
            .putCapacityItem("storage", Quantity.fromString("10Gi"))
            .persistentVolumeReclaimPolicy("Recycle")
            .hostPath(new V1HostPathVolumeSource()
                .path(System.getProperty("java.io.tmpdir") + domainUID + "-persistentVolume")))
                .metadata(new V1ObjectMetaBuilder()
            .withName(pvName)
            .withNamespace(namespace)
            .build()
            .putLabelsItem("weblogic.resourceVersion", "domain-v2")
            .putLabelsItem("weblogic.domainUID", domainUID));
    success = assertDoesNotThrow(
        () -> TestActions.createPersistentVolume(v1pv),
        "Persistent volume creation failed, "
            + "look at the above console log messages for failure reason in ApiException responsebody"
    );
    assertTrue(success, "PersistentVolume creation failed");

    // create the domain CR
    V1ObjectMeta metadata = new V1ObjectMetaBuilder()
        .withName(domainUID)
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
    success = assertDoesNotThrow(
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
                "Waiting for domain to be running (elapsed time {0}ms, remaining time {1}ms)",
                condition.getElapsedTimeInMS(),
                condition.getRemainingTimeInMS()))
        // and here we can set the maximum time we are prepared to wait
        .await().atMost(5, MINUTES)
        // operatorIsRunning() is one of our custom, reusable assertions
        .until(domainExists(domainUID, "v7", namespace));

    // wait for the admin server pod to exist

    // wait for the managed servers to exist

    // Delete domain custom resource
    assertTrue(deleteDomainCustomResource(domainUID, namespace), "Domain failed to be deleted, "
        + "look at the above console log messages for failure reason in ApiException responsebody");
    logger.info("Deleted Domain Custom Resource {0} from {1}", domainUID, namespace);

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

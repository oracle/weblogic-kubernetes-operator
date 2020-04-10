// Copyright (c) 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
import static oracle.weblogic.kubernetes.assertions.TestAssertions.domainExists;
import static oracle.weblogic.kubernetes.extensions.LoggedTest.logger;
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
    logger.info(String.format("Got a new namespace called %s", namespace));

    // create a persistent volume claim
    final String storageClassName = domainUID + "-weblogic-domain-storage-class";
    final String pvcName = domainUID + "-pvc"; // name of the persistent volume claim
    final String pvName = domainUID + "-pv"; // name of the persistent volume
    HashMap<String, String> labels = new HashMap();
    labels.put("weblogic.resourceVersion", "domain-v2");
    labels.put("weblogic.domainUID", domainUID); // label it with domain uid
    final List<String> accessMode = new ArrayList();
    accessMode.add("ReadWriteMany"); // access mode of the persistent volume claim
    final String storage = "10Gi";
    final String volumeMode = "Filesystem";
    final String volumeName = domainUID + "-weblogic-pv"; // volume name
    final String pvPath = System.getProperty("java.io.tmpdir") + domainUID + "-persistentVolume";
    final String persistentVolumeReclaimPolicy = "Recycle"; // one of Recycle, Retain, Delete
    Quantity maxClaims = Quantity.fromString(storage);
    Map<String, Quantity> capacity = new HashMap<>();
    capacity.put("storage", maxClaims); // capacity requirements

    logger.info("creating a persistent volume claim");
    V1PersistentVolumeClaim v1pvc = new V1PersistentVolumeClaim();

    // build metadata object
    V1ObjectMeta pvcmetadata = new V1ObjectMetaBuilder()
        .withName(pvcName) // set PVC name
        .withNamespace(namespace) // set PVC namespace
        .withLabels(labels) // set PVC labels
        .build();

    // build spec object
    V1PersistentVolumeClaimSpec pvcspec = new V1PersistentVolumeClaimSpec();
    // set spec storageclassname, accessModes and volumeName
    pvcspec.setAccessModes(accessMode);
    pvcspec.setStorageClassName(storageClassName);
    pvcspec.setVolumeName(volumeName);
    // build resource requirements object
    V1ResourceRequirements resources = new V1ResourceRequirements();
    resources.setRequests(capacity);
    pvcspec.setResources(resources);

    // set the matadata and spec objects
    v1pvc.setMetadata(pvcmetadata);
    v1pvc.setSpec(pvcspec);

    boolean success = assertDoesNotThrow(
        () -> TestActions.createPersistentVolumeClaim(v1pvc)
    );
    assertTrue(success, "PersistentVolumeClaim creation failed");

    logger.info("creating a persistent volume");
    V1PersistentVolume v1pv = new V1PersistentVolume();

    // build spec object
    V1PersistentVolumeSpec pvspec = new V1PersistentVolumeSpec();
    // set spec accessModes, storageclassname, persistentVolumeReclaimPolicy, volumeMode, capacity
    pvspec.setAccessModes(accessMode);
    pvspec.setStorageClassName(storageClassName);
    pvspec.setPersistentVolumeReclaimPolicy(persistentVolumeReclaimPolicy);
    pvspec.setVolumeMode(volumeMode);
    pvspec.setCapacity(capacity);
    // set pv path
    V1HostPathVolumeSource hostPath = new V1HostPathVolumeSource();
    hostPath.setPath(pvPath);
    pvspec.setHostPath(hostPath);
    //set metadata, spec
    pvcmetadata.setName(pvName);
    v1pv.setMetadata(pvcmetadata);
    v1pv.setSpec(pvspec);

    success  = assertDoesNotThrow(
        () -> TestActions.createPersistentVolume(v1pv)
      );
    assertTrue(success, "PersistentVolume creation failed");

    // Create a service account for the unique namespace
    final String serviceAccountName = namespace + "-sa";
    final V1ServiceAccount serviceAccount = assertDoesNotThrow(
        () -> Kubernetes.createServiceAccount(new V1ServiceAccount()
        .metadata(new V1ObjectMeta().namespace(namespace).name(serviceAccountName))));
    logger.info("Created service account: " + serviceAccount.getMetadata().getName());

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
        () -> createDomainCustomResource(domain)
    );
    assertTrue(success);

    // wait for the domain to exist
    with().pollDelay(30, SECONDS)
        .and().with().pollInterval(10, SECONDS)
        .conditionEvaluationListener(
            condition -> logger.info(() ->
                String.format(
                    "Waiting for domain to be running (elapsed time %dms, remaining time %dms)",
                    condition.getElapsedTimeInMS(),
                    condition.getRemainingTimeInMS())))
        // and here we can set the maximum time we are prepared to wait
        .await().atMost(5, MINUTES)
        // operatorIsRunning() is one of our custom, reusable assertions
        .until(domainExists(domainUID, "v7", namespace));

    // wait for the admin server pod to exist

    // wait for the managed servers to exist

    // Delete domain custom resource
    assertDoesNotThrow(
        () -> deleteDomainCustomResource(domainUID, namespace)
    );
    logger.info("Deleted Domain Custom Resource " + domainUID + " from " + namespace);

    // Delete service account from unique namespace
    assertDoesNotThrow(
        () -> Kubernetes.deleteServiceAccount(serviceAccount.getMetadata().getName(),
            serviceAccount.getMetadata().getNamespace()));
    logger.info("Deleted service account \'" + serviceAccount.getMetadata().getName()
        + "' in namespace: " + serviceAccount.getMetadata().getNamespace());

    // Delete namespace
    assertDoesNotThrow(
        () -> TestActions.deleteNamespace(namespace));
    logger.info("Deleted namespace: " + namespace);
  }

}

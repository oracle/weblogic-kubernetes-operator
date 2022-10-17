// Copyright (c) 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.webhooks;

import java.util.Collections;

import io.kubernetes.client.openapi.models.V1VolumeMount;
import oracle.kubernetes.operator.DomainSourceType;
import oracle.kubernetes.operator.helpers.KubernetesTestSupport;
import oracle.kubernetes.operator.webhooks.resource.ClusterUpdateAdmissionChecker;
import oracle.kubernetes.operator.webhooks.resource.DomainUpdateAdmissionChecker;
import oracle.kubernetes.weblogic.domain.model.ClusterResource;
import oracle.kubernetes.weblogic.domain.model.DomainResource;
import org.junit.jupiter.api.Test;

import static java.net.HttpURLConnection.HTTP_FORBIDDEN;
import static java.util.Arrays.asList;
import static oracle.kubernetes.operator.DomainProcessorTestSetup.NS;
import static oracle.kubernetes.operator.DomainProcessorTestSetup.UID;
import static oracle.kubernetes.operator.webhooks.AdmissionWebhookTestSetUp.AUX_IMAGE_1;
import static oracle.kubernetes.operator.webhooks.AdmissionWebhookTestSetUp.AUX_IMAGE_2;
import static oracle.kubernetes.operator.webhooks.AdmissionWebhookTestSetUp.BAD_REPLICAS;
import static oracle.kubernetes.operator.webhooks.AdmissionWebhookTestSetUp.GOOD_REPLICAS;
import static oracle.kubernetes.operator.webhooks.AdmissionWebhookTestSetUp.NEW_IMAGE_NAME;
import static oracle.kubernetes.operator.webhooks.AdmissionWebhookTestSetUp.NEW_INTROSPECT_VERSION;
import static oracle.kubernetes.operator.webhooks.AdmissionWebhookTestSetUp.ORIGINAL_REPLICAS;
import static oracle.kubernetes.operator.webhooks.AdmissionWebhookTestSetUp.createAuxiliaryImage;
import static oracle.kubernetes.operator.webhooks.AdmissionWebhookTestSetUp.setAuxiliaryImages;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.junit.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DomainUpdateAdmissionCheckerTest extends DomainAdmissionCheckerTestBase {
  private static final String WARN_MESSAGE_PATTERN_DOMAIN =
      "Change request to domain resource '%s' causes the replica count of each cluster in '%s' to exceed its cluster "
          + "size '%s' respectively";

  @Override
  void setupCheckers() {
    domainChecker = new DomainUpdateAdmissionChecker(existingDomain, proposedDomain);
    clusterChecker = new ClusterUpdateAdmissionChecker(existingCluster, proposedCluster);
  }

  @Test
  void whenSameObject_returnTrue() {
    assertThat(domainChecker.isProposedChangeAllowed(), equalTo(true));
  }

  @Test
  void whenNothingChanged_returnTrue() {
    assertThat(domainChecker.isProposedChangeAllowed(), equalTo(true));
  }

  @Test
  void whenSpecAdded_returnTrue() {
    existingDomain.withSpec(null);
    assertThat(domainChecker.isProposedChangeAllowed(), equalTo(true));
  }

  // DomainResource replica count validation
  @Test
  void whenDomainReplicasChangedAloneValid_returnTrue() {
    proposedDomain.getSpec().withReplicas(GOOD_REPLICAS);

    assertThat(domainChecker.isProposedChangeAllowed(), equalTo(true));
  }

  @Test
  void whenDomainReplicasChangedAloneAndInvalid_returnFalse() {
    proposedDomain.getSpec().withReplicas(BAD_REPLICAS);

    assertThat(domainChecker.isProposedChangeAllowed(), equalTo(false));
  }

  @Test
  void whenIntrospectionVersionChangedAndDomainReplicasInvalid_returnTrue() {
    proposedDomain.getSpec().setIntrospectVersion(NEW_INTROSPECT_VERSION);
    proposedDomain.getSpec().withReplicas(BAD_REPLICAS);

    assertThat(domainChecker.isProposedChangeAllowed(), equalTo(true));
  }

  @Test
  void whenDomainImageChangedAndReplicasInvalid_returnTrue() {
    proposedDomain.getSpec().withImage(NEW_IMAGE_NAME);
    proposedDomain.getSpec().withReplicas(BAD_REPLICAS);

    assertThat(domainChecker.isProposedChangeAllowed(), equalTo(true));
  }

  @Test
  void whenLogHomeChangedAndDomainReplicasInvalid_returnFalse() {
    proposedDomain.getSpec().withReplicas(BAD_REPLICAS);
    proposedDomain.getSpec().setLogHome("/home/dir");

    assertThat(domainChecker.isProposedChangeAllowed(), equalTo(false));
  }

  @Test
  void whenDomainSourceTypeChanged_returnTrue() {
    proposedDomain.getSpec().withDomainHomeSourceType(DomainSourceType.IMAGE);

    assertThat(domainChecker.isProposedChangeAllowed(), equalTo(true));
    assertFalse(((DomainUpdateAdmissionChecker)domainChecker).hasWarnings());
  }

  @Test
  void whenDomainSourceTypeUnchanged_returnTrue() {
    existingDomain.getSpec().withDomainHomeSourceType(DomainSourceType.IMAGE);
    proposedDomain.getSpec().withDomainHomeSourceType(DomainSourceType.IMAGE);

    assertThat(domainChecker.isProposedChangeAllowed(), equalTo(true));
  }

  @Test
  void whenDomainSourceTypeUnchangedAndDomainReplicasValid_returnTrue() {
    proposedDomain.getSpec().withReplicas(GOOD_REPLICAS);
    existingDomain.getSpec().withDomainHomeSourceType(DomainSourceType.IMAGE);
    proposedDomain.getSpec().withDomainHomeSourceType(DomainSourceType.IMAGE);

    assertThat(domainChecker.isProposedChangeAllowed(), equalTo(true));
  }

  @Test
  void whenDomainSourceTypeUnchangedAndDomainReplicasInvalid_returnFalse() {
    proposedDomain.getSpec().withReplicas(BAD_REPLICAS);
    existingDomain.getSpec().withDomainHomeSourceType(DomainSourceType.IMAGE);
    proposedDomain.getSpec().withDomainHomeSourceType(DomainSourceType.IMAGE);

    assertThat(domainChecker.isProposedChangeAllowed(), equalTo(false));
  }

  @Test
  void whenDomainSourceTypePVAndDomainImageChangedReplicasInvalid_returnFalse() {
    proposedDomain.getSpec().withReplicas(BAD_REPLICAS).withImage(NEW_IMAGE_NAME).setLogHome(MOUNT_PATH);
    setPVDomainSourceTypeWithAdditionalVolume(existingDomain);
    setPVDomainSourceTypeWithAdditionalVolume(proposedDomain);

    assertThat(domainChecker.isProposedChangeAllowed(), equalTo(false));
  }

  private void setPVDomainSourceTypeWithAdditionalVolume(DomainResource domain) {
    domain.getSpec().withDomainHomeSourceType(DomainSourceType.PERSISTENT_VOLUME)
        .getAdditionalVolumeMounts().add(new V1VolumeMount().mountPath(MOUNT_PATH));
  }

  @Test
  void whenDomainSourceTypeBothMIINoAuxImgImageChangedAndDomainImageChangedReplicasInvalid_returnTrue() {
    proposedDomain.getSpec().withReplicas(BAD_REPLICAS);
    proposedDomain.getSpec().withImage(NEW_IMAGE_NAME);
    existingDomain.getSpec().withDomainHomeSourceType(DomainSourceType.FROM_MODEL);
    proposedDomain.getSpec().withDomainHomeSourceType(DomainSourceType.FROM_MODEL);

    assertThat(domainChecker.isProposedChangeAllowed(), equalTo(true));
    assertTrue(((DomainUpdateAdmissionChecker)domainChecker).hasWarnings());
  }

  @Test
  void whenDomainSourceTypeBothMIINoAuxImgAndDomainImageChangedReplicasInvalid_returnFalse() {
    proposedDomain.getSpec().withReplicas(BAD_REPLICAS);
    existingDomain.getSpec().withDomainHomeSourceType(DomainSourceType.FROM_MODEL);
    proposedDomain.getSpec().withDomainHomeSourceType(DomainSourceType.FROM_MODEL);

    assertThat(domainChecker.isProposedChangeAllowed(), equalTo(false));
  }

  @Test
  void whenDomainSourceTypeBothMIIAddAuxImgAndDomainImageChangedReplicasInvalid_returnTrue() {
    proposedDomain.getSpec().withReplicas(BAD_REPLICAS);
    proposedDomain.getSpec().withImage(NEW_IMAGE_NAME);
    setAuxiliaryImages(proposedDomain, asList(createAuxiliaryImage(AUX_IMAGE_1), createAuxiliaryImage(AUX_IMAGE_2)));
    existingDomain.getSpec().withDomainHomeSourceType(DomainSourceType.FROM_MODEL);
    proposedDomain.getSpec().withDomainHomeSourceType(DomainSourceType.FROM_MODEL);

    assertThat(domainChecker.isProposedChangeAllowed(), equalTo(true));
  }

  @Test
  void whenDomainSourceTypeBothMIIRemoveAuxImgAndDomainImageChangedReplicasInvalid_returnTrue() {
    proposedDomain.getSpec().withReplicas(BAD_REPLICAS);
    proposedDomain.getSpec().withImage(NEW_IMAGE_NAME);
    setAuxiliaryImages(existingDomain, asList(createAuxiliaryImage(AUX_IMAGE_1), createAuxiliaryImage(AUX_IMAGE_2)));
    existingDomain.getSpec().withDomainHomeSourceType(DomainSourceType.FROM_MODEL);
    proposedDomain.getSpec().withDomainHomeSourceType(DomainSourceType.FROM_MODEL);

    assertThat(domainChecker.isProposedChangeAllowed(), equalTo(true));
  }

  @Test
  void whenDomainSourceTypeBothMIIAuxImgSameAndDomainImageChangedReplicasInvalid_returnFalse() {
    proposedDomain.getSpec().withReplicas(BAD_REPLICAS);
    proposedDomain.getSpec().withImage(NEW_IMAGE_NAME);
    setAuxiliaryImages(existingDomain, asList(createAuxiliaryImage(AUX_IMAGE_1), createAuxiliaryImage(AUX_IMAGE_2)));
    setAuxiliaryImages(proposedDomain, asList(createAuxiliaryImage(AUX_IMAGE_1), createAuxiliaryImage(AUX_IMAGE_2)));
    existingDomain.getSpec().withDomainHomeSourceType(DomainSourceType.FROM_MODEL);
    proposedDomain.getSpec().withDomainHomeSourceType(DomainSourceType.FROM_MODEL);

    assertThat(domainChecker.isProposedChangeAllowed(), equalTo(false));
  }

  @Test
  void whenDomainSourceTypeBothMIIAuxImgSameInDifferentOrderAndDomainImageChangedReplicasInvalid_returnFalse() {
    proposedDomain.getSpec().withReplicas(BAD_REPLICAS);
    proposedDomain.getSpec().withImage(NEW_IMAGE_NAME);
    setAuxiliaryImages(existingDomain, asList(createAuxiliaryImage(AUX_IMAGE_2), createAuxiliaryImage(AUX_IMAGE_1)));
    setAuxiliaryImages(proposedDomain, asList(createAuxiliaryImage(AUX_IMAGE_1), createAuxiliaryImage(AUX_IMAGE_2)));
    existingDomain.getSpec().withDomainHomeSourceType(DomainSourceType.FROM_MODEL);
    proposedDomain.getSpec().withDomainHomeSourceType(DomainSourceType.FROM_MODEL);

    assertThat(domainChecker.isProposedChangeAllowed(), equalTo(false));
  }

  @Test
  void whenDomainSourceTypeBothMIIAuxImgDifferentAndDomainImageChangedReplicasInvalid_returnTrueWithWarnings() {
    proposedDomain.getSpec().withReplicas(BAD_REPLICAS);
    proposedDomain.getSpec().withImage(NEW_IMAGE_NAME);
    setAuxiliaryImages(existingDomain, Collections.singletonList(createAuxiliaryImage(AUX_IMAGE_1)));
    setAuxiliaryImages(proposedDomain, asList(createAuxiliaryImage(AUX_IMAGE_1), createAuxiliaryImage(AUX_IMAGE_2)));
    existingDomain.getSpec().withDomainHomeSourceType(DomainSourceType.FROM_MODEL);
    proposedDomain.getSpec().withDomainHomeSourceType(DomainSourceType.FROM_MODEL);

    assertThat(domainChecker.isProposedChangeAllowed(), equalTo(true));
    assertThat(((DomainUpdateAdmissionChecker)domainChecker).hasWarnings(), equalTo(true));
    assertThat(((DomainUpdateAdmissionChecker)domainChecker).getWarnings().get(0),
        equalTo(getWarningMessageForDomainResource(proposedDomain, proposedCluster, proposedCluster2)));

  }

  private Object getWarningMessageForDomainResource(DomainResource domain, ClusterResource c1, ClusterResource c2) {
    return String.format(WARN_MESSAGE_PATTERN_DOMAIN, domain.getDomainUid(),
        c1.getMetadata().getName() + ", " + c2.getMetadata().getName(), ORIGINAL_REPLICAS + ", " + ORIGINAL_REPLICAS);
  }

  @Test
  void whenDomainCheckerClusterReplicasChangedToUnsetAndReadClusterSucceed_returnFalseWithoutException() {
    testSupport.defineResources(proposedDomain);
    proposedDomain.getSpec().withReplicas(BAD_REPLICAS);
    existingCluster.getSpec().withReplicas(2);
    proposedCluster.getSpec().withReplicas(null);

    assertThat(domainChecker.isProposedChangeAllowed(), equalTo(false));
    assertThat(((DomainUpdateAdmissionChecker)domainChecker).hasException(), equalTo(false));
  }

  @Test
  void whenDomainCheckerReadClusterFailed404_returnFalseWithException() {
    testSupport.defineResources(proposedDomain);
    proposedDomain.getSpec().withReplicas(BAD_REPLICAS);

    testSupport.failOnList(KubernetesTestSupport.CLUSTER, NS, HTTP_FORBIDDEN);

    assertThat(domainChecker.isProposedChangeAllowed(), equalTo(false));
    assertThat(((DomainUpdateAdmissionChecker)domainChecker).hasException(), equalTo(true));
  }

  @Test
  void whenDomainWithInvalidReplicasReferencesNoClusters_returnTrueWithoutWarnings() {
    testSupport.defineResources(proposedDomain2);
    proposedDomain2.getSpec().withReplicas(BAD_REPLICAS);

    testSupport.failOnList(KubernetesTestSupport.CLUSTER, NS, HTTP_FORBIDDEN);

    assertThat(domainChecker.isProposedChangeAllowed(), equalTo(true));
    assertThat(((DomainUpdateAdmissionChecker)domainChecker).hasWarnings(), equalTo(false));
  }

  // ClusterResource validation

  @Test
  void whenClusterReplicasChangedAndValid_returnTrue() {
    testSupport.defineResources(existingDomain);
    proposedCluster.getSpec().withReplicas(GOOD_REPLICAS);

    assertThat(clusterChecker.isProposedChangeAllowed(), equalTo(true));
  }

  @Test
  void whenClusterReplicasChangedAndInvalid_returnFalse() {
    testSupport.defineResources(proposedDomain);
    proposedCluster.getSpec().withReplicas(BAD_REPLICAS);

    assertThat(clusterChecker.isProposedChangeAllowed(), equalTo(false));
    assertThat(((ClusterUpdateAdmissionChecker)clusterChecker).hasException(), equalTo(false));
  }

  @Test
  void whenClusterReplicasChanged_proposedClusterHasNoStatus_returnTrue() {
    testSupport.defineResources(proposedDomain);
    proposedCluster.getSpec().withReplicas(BAD_REPLICAS);
    proposedCluster.setStatus(null);

    assertThat(clusterChecker.isProposedChangeAllowed(), equalTo(true));
    assertThat(((ClusterUpdateAdmissionChecker)clusterChecker).hasException(), equalTo(false));
  }

  @Test
  void whenClusterReplicasChangedToUnsetAndDomainReplicasValid_returnTrue() {
    testSupport.defineResources(proposedDomain);
    proposedDomain.getSpec().withReplicas(GOOD_REPLICAS);
    existingCluster.getSpec().withReplicas(2);
    proposedCluster.getSpec().withReplicas(null);

    assertThat(clusterChecker.isProposedChangeAllowed(), equalTo(true));
    assertThat(((ClusterUpdateAdmissionChecker)clusterChecker).hasException(), equalTo(false));
  }

  @Test
  void whenClusterReplicasChangedToUnsetAndDomainReplicasInvalid_returnFalse() {
    testSupport.defineResources(proposedDomain);
    proposedDomain.getSpec().withReplicas(BAD_REPLICAS);
    existingCluster.getSpec().withReplicas(2);
    proposedCluster.getSpec().withReplicas(null);

    assertThat(clusterChecker.isProposedChangeAllowed(), equalTo(false));
    assertThat(((ClusterUpdateAdmissionChecker)clusterChecker).hasException(), equalTo(false));
  }

  @Test
  void whenClusterReplicasChangedToUnsetAndReadDomainFailed404_returnFalseWithException() {
    testSupport.defineResources(proposedDomain);
    proposedDomain.getSpec().withReplicas(BAD_REPLICAS);
    existingCluster.getSpec().withReplicas(2);
    proposedCluster.getSpec().withReplicas(null);

    testSupport.failOnList(KubernetesTestSupport.DOMAIN, NS, HTTP_FORBIDDEN);

    assertThat(clusterChecker.isProposedChangeAllowed(), equalTo(false));
    assertThat(((ClusterUpdateAdmissionChecker)clusterChecker).hasException(), equalTo(true));
  }

  @Test
  void whenClusterReplicasChangedValidAndReadDomainFailed404_returnTrueNoException() {
    testSupport.defineResources(proposedDomain);
    proposedCluster.getSpec().withReplicas(GOOD_REPLICAS);

    testSupport.failOnRead(KubernetesTestSupport.DOMAIN, UID, NS, HTTP_FORBIDDEN);

    assertThat(clusterChecker.isProposedChangeAllowed(), equalTo(true));
    assertThat(((ClusterUpdateAdmissionChecker)clusterChecker).hasException(), equalTo(false));
  }
}

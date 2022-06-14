// Copyright (c) 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.webhooks;

import java.util.Collections;

import oracle.kubernetes.operator.DomainSourceType;
import oracle.kubernetes.operator.webhooks.resource.AdmissionChecker;
import oracle.kubernetes.weblogic.domain.model.DomainResource;
import org.junit.jupiter.api.Test;

import static java.util.Arrays.asList;
import static oracle.kubernetes.operator.webhooks.AdmissionWebhookTestSetUp.AUX_IMAGE_1;
import static oracle.kubernetes.operator.webhooks.AdmissionWebhookTestSetUp.AUX_IMAGE_2;
import static oracle.kubernetes.operator.webhooks.AdmissionWebhookTestSetUp.BAD_REPLICAS;
import static oracle.kubernetes.operator.webhooks.AdmissionWebhookTestSetUp.GOOD_REPLICAS;
import static oracle.kubernetes.operator.webhooks.AdmissionWebhookTestSetUp.NEW_IMAGE_NAME;
import static oracle.kubernetes.operator.webhooks.AdmissionWebhookTestSetUp.NEW_INTROSPECT_VERSION;
import static oracle.kubernetes.operator.webhooks.AdmissionWebhookTestSetUp.NEW_LOG_HOME;
import static oracle.kubernetes.operator.webhooks.AdmissionWebhookTestSetUp.createAuxiliaryImage;
import static oracle.kubernetes.operator.webhooks.AdmissionWebhookTestSetUp.createDomain;
import static oracle.kubernetes.operator.webhooks.AdmissionWebhookTestSetUp.setAuxiliaryImages;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.junit.MatcherAssert.assertThat;

class AdmissionCheckerTest {
  private final DomainResource existingDomain = createDomain();
  private final DomainResource proposedDomain = createDomain();
  private final AdmissionChecker admissionChecker = new AdmissionChecker(existingDomain, proposedDomain);

  @Test
  void whenSameObject_returnTrue() {
    assertThat(admissionChecker.isProposedChangeAllowed(), equalTo(true));
  }

  @Test
  void whenNothingChanged_returnTrue() {
    assertThat(admissionChecker.isProposedChangeAllowed(), equalTo(true));
  }

  @Test
  void whenNoSpec_returnTrue() {
    existingDomain.withSpec(null);
    proposedDomain.withSpec(null);
    assertThat(admissionChecker.isProposedChangeAllowed(), equalTo(true));
  }

  @Test
  void whenSpecRemoved_returnTrue() {
    proposedDomain.withSpec(null);
    assertThat(admissionChecker.isProposedChangeAllowed(), equalTo(true));
  }

  @Test
  void whenSpecAdded_returnTrue() {
    existingDomain.withSpec(null);
    assertThat(admissionChecker.isProposedChangeAllowed(), equalTo(true));
  }

  @Test
  void whenDomainReplicasChangedAloneValid_returnTrue() {
    proposedDomain.getSpec().withReplicas(GOOD_REPLICAS);

    assertThat(admissionChecker.isProposedChangeAllowed(), equalTo(true));
  }

  @Test
  void whenDomainReplicasChangedAloneAndInvalid_returnFalse() {
    proposedDomain.getSpec().withReplicas(BAD_REPLICAS);

    assertThat(admissionChecker.isProposedChangeAllowed(), equalTo(false));
  }

  @Test
  void whenIntrospectionVersionChangedAndDomainReplicasInvalid_returnTrue() {
    proposedDomain.getSpec().setIntrospectVersion(NEW_INTROSPECT_VERSION);
    proposedDomain.getSpec().withReplicas(BAD_REPLICAS);

    assertThat(admissionChecker.isProposedChangeAllowed(), equalTo(true));
  }

  @Test
  void whenDomainImageChangedAndReplicasInvalid_returnTrue() {
    proposedDomain.getSpec().withImage(NEW_IMAGE_NAME);
    proposedDomain.getSpec().withReplicas(BAD_REPLICAS);

    assertThat(admissionChecker.isProposedChangeAllowed(), equalTo(true));
  }

  @Test
  void whenLogHomeChangedAndDomainReplicasInvalid_returnFalse() {
    proposedDomain.getSpec().withReplicas(BAD_REPLICAS);
    proposedDomain.getSpec().setLogHome("/home/dir");

    assertThat(admissionChecker.isProposedChangeAllowed(), equalTo(false));
  }

  @Test
  void whenOneClusterReplicasChangedAloneAndValid_returnTrue() {
    proposedDomain.getSpec().getClusters().get(0).withReplicas(GOOD_REPLICAS);

    assertThat(admissionChecker.isProposedChangeAllowed(), equalTo(true));
  }

  @Test
  void whenOneClusterReplicasChangedAloneAndInvalid_returnFalse() {
    proposedDomain.getSpec().getClusters().get(0).withReplicas(BAD_REPLICAS);

    assertThat(admissionChecker.isProposedChangeAllowed(), equalTo(false));
  }

  @Test
  void whenIntrospectionVersionChangedAndOneClusterReplicasInvalid_returnTrue() {
    proposedDomain.getSpec().setIntrospectVersion(NEW_INTROSPECT_VERSION);
    proposedDomain.getSpec().getClusters().get(0).withReplicas(BAD_REPLICAS);

    assertThat(admissionChecker.isProposedChangeAllowed(), equalTo(true));
  }

  @Test
  void whenImageChangedAndOneClusterReplicasInvalid_returnTrue() {
    proposedDomain.getSpec().withImage(NEW_IMAGE_NAME);
    proposedDomain.getSpec().getClusters().get(0).withReplicas(BAD_REPLICAS);
    assertThat(admissionChecker.isProposedChangeAllowed(), equalTo(true));
  }

  @Test
  void whenLogHomeChangedAndOneClusterReplicasChangedInvalid_returnFalse() {
    proposedDomain.getSpec().getClusters().get(0).withReplicas(BAD_REPLICAS);
    proposedDomain.getSpec().setLogHome(NEW_LOG_HOME);

    assertThat(admissionChecker.isProposedChangeAllowed(), equalTo(false));
  }

  @Test
  void whenDomainReplicasChangedInvalidAndOneClusterReplicasChangedValid_returnFalse() {
    proposedDomain.getSpec().withReplicas(BAD_REPLICAS);
    proposedDomain.getSpec().getClusters().get(0).withReplicas(1);

    assertThat(admissionChecker.isProposedChangeAllowed(), equalTo(false));
  }

  @Test
  void whenDomainReplicasChangedInvalidAndBothClusterReplicasChangedValid_returnTrue() {
    proposedDomain.getSpec().withReplicas(BAD_REPLICAS);
    proposedDomain.getSpec().getClusters().get(0).withReplicas(GOOD_REPLICAS);
    proposedDomain.getSpec().getClusters().get(1).withReplicas(GOOD_REPLICAS);

    assertThat(admissionChecker.isProposedChangeAllowed(), equalTo(true));
  }

  @Test
  void whenDomainReplicasChangedValidAndOneClusterReplicasChangedInvalid_returnFalse() {
    proposedDomain.getSpec().withReplicas(GOOD_REPLICAS);
    proposedDomain.getSpec().getClusters().get(0).withReplicas(GOOD_REPLICAS);
    proposedDomain.getSpec().getClusters().get(1).withReplicas(BAD_REPLICAS);

    assertThat(admissionChecker.isProposedChangeAllowed(), equalTo(false));
  }

  @Test
  void whenDomainSourceTypeChanged_returnTrue() {
    proposedDomain.getSpec().withDomainHomeSourceType(DomainSourceType.IMAGE);

    assertThat(admissionChecker.isProposedChangeAllowed(), equalTo(true));
  }

  @Test
  void whenDomainSourceTypeUnchanged_returnTrue() {
    existingDomain.getSpec().withDomainHomeSourceType(DomainSourceType.IMAGE);
    proposedDomain.getSpec().withDomainHomeSourceType(DomainSourceType.IMAGE);

    assertThat(admissionChecker.isProposedChangeAllowed(), equalTo(true));
  }

  @Test
  void whenDomainSourceTypeUnchangedAndDomainReplicasValid_returnTrue() {
    proposedDomain.getSpec().withReplicas(GOOD_REPLICAS);
    existingDomain.getSpec().withDomainHomeSourceType(DomainSourceType.IMAGE);
    proposedDomain.getSpec().withDomainHomeSourceType(DomainSourceType.IMAGE);

    assertThat(admissionChecker.isProposedChangeAllowed(), equalTo(true));
  }

  @Test
  void whenDomainSourceTypeUnchangedAndDomainReplicasInvalid_returnFalse() {
    proposedDomain.getSpec().withReplicas(BAD_REPLICAS);
    existingDomain.getSpec().withDomainHomeSourceType(DomainSourceType.IMAGE);
    proposedDomain.getSpec().withDomainHomeSourceType(DomainSourceType.IMAGE);

    assertThat(admissionChecker.isProposedChangeAllowed(), equalTo(false));
  }

  @Test
  void whenDomainSourceTypePVAndDomainImageChangedReplicasInvalid_returnFalse() {
    proposedDomain.getSpec().withReplicas(BAD_REPLICAS);
    proposedDomain.getSpec().withImage(NEW_IMAGE_NAME);
    existingDomain.getSpec().withDomainHomeSourceType(DomainSourceType.PERSISTENT_VOLUME);
    proposedDomain.getSpec().withDomainHomeSourceType(DomainSourceType.PERSISTENT_VOLUME);

    assertThat(admissionChecker.isProposedChangeAllowed(), equalTo(false));
  }

  @Test
  void whenDomainSourceTypeBothMIINoAuxImgImageChangedAndDomainImageChangedReplicasInvalid_returnTrue() {
    proposedDomain.getSpec().withReplicas(BAD_REPLICAS);
    proposedDomain.getSpec().withImage(NEW_IMAGE_NAME);
    existingDomain.getSpec().withDomainHomeSourceType(DomainSourceType.FROM_MODEL);
    proposedDomain.getSpec().withDomainHomeSourceType(DomainSourceType.FROM_MODEL);

    assertThat(admissionChecker.isProposedChangeAllowed(), equalTo(true));
  }

  @Test
  void whenDomainSourceTypeBothMIINoAuxImgAndDomainImageChangedReplicasInvalid_returnFalse() {
    proposedDomain.getSpec().withReplicas(BAD_REPLICAS);
    existingDomain.getSpec().withDomainHomeSourceType(DomainSourceType.FROM_MODEL);
    proposedDomain.getSpec().withDomainHomeSourceType(DomainSourceType.FROM_MODEL);

    assertThat(admissionChecker.isProposedChangeAllowed(), equalTo(false));
  }

  @Test
  void whenDomainSourceTypeBothMIIAddAuxImgAndDomainImageChangedReplicasInvalid_returnTrue() {
    proposedDomain.getSpec().withReplicas(BAD_REPLICAS);
    proposedDomain.getSpec().withImage(NEW_IMAGE_NAME);
    setAuxiliaryImages(proposedDomain, asList(createAuxiliaryImage(AUX_IMAGE_1), createAuxiliaryImage(AUX_IMAGE_2)));
    existingDomain.getSpec().withDomainHomeSourceType(DomainSourceType.FROM_MODEL);
    proposedDomain.getSpec().withDomainHomeSourceType(DomainSourceType.FROM_MODEL);

    assertThat(admissionChecker.isProposedChangeAllowed(), equalTo(true));
  }

  @Test
  void whenDomainSourceTypeBothMIIRemoveAuxImgAndDomainImageChangedReplicasInvalid_returnTrue() {
    proposedDomain.getSpec().withReplicas(BAD_REPLICAS);
    proposedDomain.getSpec().withImage(NEW_IMAGE_NAME);
    setAuxiliaryImages(existingDomain, asList(createAuxiliaryImage(AUX_IMAGE_1), createAuxiliaryImage(AUX_IMAGE_2)));
    existingDomain.getSpec().withDomainHomeSourceType(DomainSourceType.FROM_MODEL);
    proposedDomain.getSpec().withDomainHomeSourceType(DomainSourceType.FROM_MODEL);

    assertThat(admissionChecker.isProposedChangeAllowed(), equalTo(true));
  }

  @Test
  void whenDomainSourceTypeBothMIIAuxImgSameAndDomainImageChangedReplicasInvalid_returnFalse() {
    proposedDomain.getSpec().withReplicas(BAD_REPLICAS);
    proposedDomain.getSpec().withImage(NEW_IMAGE_NAME);
    setAuxiliaryImages(existingDomain, asList(createAuxiliaryImage(AUX_IMAGE_1), createAuxiliaryImage(AUX_IMAGE_2)));
    setAuxiliaryImages(proposedDomain, asList(createAuxiliaryImage(AUX_IMAGE_1), createAuxiliaryImage(AUX_IMAGE_2)));
    existingDomain.getSpec().withDomainHomeSourceType(DomainSourceType.FROM_MODEL);
    proposedDomain.getSpec().withDomainHomeSourceType(DomainSourceType.FROM_MODEL);

    assertThat(admissionChecker.isProposedChangeAllowed(), equalTo(false));
  }

  @Test
  void whenDomainSourceTypeBothMIIAuxImgSameInDifferentOrderAndDomainImageChangedReplicasInvalid_returnFalse() {
    proposedDomain.getSpec().withReplicas(BAD_REPLICAS);
    proposedDomain.getSpec().withImage(NEW_IMAGE_NAME);
    setAuxiliaryImages(existingDomain, asList(createAuxiliaryImage(AUX_IMAGE_2), createAuxiliaryImage(AUX_IMAGE_1)));
    setAuxiliaryImages(proposedDomain, asList(createAuxiliaryImage(AUX_IMAGE_1), createAuxiliaryImage(AUX_IMAGE_2)));
    existingDomain.getSpec().withDomainHomeSourceType(DomainSourceType.FROM_MODEL);
    proposedDomain.getSpec().withDomainHomeSourceType(DomainSourceType.FROM_MODEL);

    assertThat(admissionChecker.isProposedChangeAllowed(), equalTo(false));
  }

  @Test
  void whenDomainSourceTypeBothMIIAuxImgDifferentAndDomainImageChangedReplicasInvalid_returnTrue() {
    proposedDomain.getSpec().withReplicas(BAD_REPLICAS);
    proposedDomain.getSpec().withImage(NEW_IMAGE_NAME);
    setAuxiliaryImages(existingDomain, Collections.singletonList(createAuxiliaryImage(AUX_IMAGE_1)));
    setAuxiliaryImages(proposedDomain, asList(createAuxiliaryImage(AUX_IMAGE_1), createAuxiliaryImage(AUX_IMAGE_2)));
    existingDomain.getSpec().withDomainHomeSourceType(DomainSourceType.FROM_MODEL);
    proposedDomain.getSpec().withDomainHomeSourceType(DomainSourceType.FROM_MODEL);

    assertThat(admissionChecker.isProposedChangeAllowed(), equalTo(true));
  }

  @Test
  void whenDomainSourceTypeNullWithModelAndDomainImageChangedReplicasInvalid_returnTrue() {
    proposedDomain.getSpec().withReplicas(BAD_REPLICAS);
    proposedDomain.getSpec().withImage(NEW_IMAGE_NAME);
    setAuxiliaryImages(proposedDomain, asList(createAuxiliaryImage(AUX_IMAGE_1), createAuxiliaryImage(AUX_IMAGE_2)));

    assertThat(admissionChecker.isProposedChangeAllowed(), equalTo(true));
  }
}

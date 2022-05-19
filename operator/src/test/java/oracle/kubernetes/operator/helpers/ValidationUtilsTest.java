// Copyright (c) 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helpers;

import java.util.Collections;
import java.util.List;

import oracle.kubernetes.operator.DomainSourceType;
import oracle.kubernetes.operator.utils.ValidationUtils;
import oracle.kubernetes.weblogic.domain.model.AuxiliaryImage;
import oracle.kubernetes.weblogic.domain.model.ClusterSpec;
import oracle.kubernetes.weblogic.domain.model.ClusterStatus;
import oracle.kubernetes.weblogic.domain.model.Configuration;
import oracle.kubernetes.weblogic.domain.model.Domain;
import oracle.kubernetes.weblogic.domain.model.DomainStatus;
import oracle.kubernetes.weblogic.domain.model.Model;
import org.junit.jupiter.api.Test;

import static java.util.Arrays.asList;
import static oracle.kubernetes.operator.DomainProcessorTestSetup.createTestDomain;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.junit.MatcherAssert.assertThat;

class ValidationUtilsTest {
  private static final String CLUSTER_NAME_1 = "C1";
  private static final String CLUSTER_NAME_2 = "C2";
  public static final String ORIGINAL_IMAGE_NAME = "abcd";
  public static final int ORIGINAL_REPLICAS = 2;
  public static final String ORIGINAL_INTROSPECT_VERSION = "1234";
  public static final String NEW_IMAGE_NAME = "NewImage";
  public static final String NEW_INTROSPECT_VERSION = "5678";
  public static final int BAD_REPLICAS = 4;
  public static final int GOOD_REPLICAS = 1;
  public static final String NEW_LOG_HOME = "/home/dir";
  public static final String AUX_IMAGE_1 = "image1";
  public static final String AUX_IMAGE_2 = "Image2";

  private final Domain existingDomain = createDomain();
  private final Domain proposedDomain = createDomain();

  private Domain createDomain() {
    Domain domain = createTestDomain().withStatus(createDomainStatus());
    domain.getSpec()
        .withReplicas(ORIGINAL_REPLICAS)
        .withImage(ORIGINAL_IMAGE_NAME)
        .setIntrospectVersion(ORIGINAL_INTROSPECT_VERSION);
    domain.getSpec()
        .withCluster(createCluster(CLUSTER_NAME_1))
        .withCluster(createCluster(CLUSTER_NAME_2));
    return domain;
  }

  private ClusterSpec createCluster(String clusterName) {
    return new ClusterSpec().withClusterName(clusterName);
  }

  private DomainStatus createDomainStatus() {
    return new DomainStatus()
        .addCluster(createClusterStatus(CLUSTER_NAME_1))
        .addCluster(createClusterStatus(CLUSTER_NAME_2));
  }

  private ClusterStatus createClusterStatus(String clusterName) {
    return new ClusterStatus().withClusterName(clusterName).withMaximumReplicas(ORIGINAL_REPLICAS);
  }

  @Test
  void whenSameObject_returnTrue() {
    assertThat(ValidationUtils.isProposedChangeAllowed(existingDomain, existingDomain), equalTo(true));
  }

  @Test
  void whenNothingChanged_returnTrue() {
    assertThat(ValidationUtils.isProposedChangeAllowed(existingDomain, proposedDomain), equalTo(true));
  }

  @Test
  void whenNoSpec_returnTrue() {
    existingDomain.withSpec(null);
    proposedDomain.withSpec(null);
    assertThat(ValidationUtils.isProposedChangeAllowed(existingDomain, proposedDomain), equalTo(true));
  }

  @Test
  void whenSpecRemoved_returnTrue() {
    proposedDomain.withSpec(null);
    assertThat(ValidationUtils.isProposedChangeAllowed(existingDomain, proposedDomain), equalTo(true));
  }

  @Test
  void whenSpecAdded_returnTrue() {
    existingDomain.withSpec(null);
    assertThat(ValidationUtils.isProposedChangeAllowed(existingDomain, proposedDomain), equalTo(true));
  }

  @Test
  void whenDomainReplicasChangedAloneValid_returnTrue() {
    proposedDomain.getSpec().withReplicas(GOOD_REPLICAS);

    assertThat(ValidationUtils.isProposedChangeAllowed(existingDomain, proposedDomain), equalTo(true));
  }

  @Test
  void whenDomainReplicasChangedAloneAndInvalid_returnFalse() {
    proposedDomain.getSpec().withReplicas(BAD_REPLICAS);

    assertThat(ValidationUtils.isProposedChangeAllowed(existingDomain, proposedDomain), equalTo(false));
  }

  @Test
  void whenIntrospectionVersionChangedAndDomainReplicasInvalid_returnTrue() {
    proposedDomain.getSpec().setIntrospectVersion(NEW_INTROSPECT_VERSION);
    proposedDomain.getSpec().withReplicas(BAD_REPLICAS);

    assertThat(ValidationUtils.isProposedChangeAllowed(existingDomain, proposedDomain), equalTo(true));
  }

  @Test
  void whenDomainImageChangedAndReplicasInvalid_returnTrue() {
    proposedDomain.getSpec().withImage(NEW_IMAGE_NAME);
    proposedDomain.getSpec().withReplicas(BAD_REPLICAS);

    assertThat(ValidationUtils.isProposedChangeAllowed(existingDomain, proposedDomain), equalTo(true));
  }

  @Test
  void whenLogHomeChangedAndDomainReplicasInvalid_returnFalse() {
    proposedDomain.getSpec().withReplicas(BAD_REPLICAS);
    proposedDomain.getSpec().setLogHome("/home/dir");

    assertThat(ValidationUtils.isProposedChangeAllowed(existingDomain, proposedDomain), equalTo(false));
  }

  @Test
  void whenOneClusterReplicasChangedAloneAndValid_returnTrue() {
    proposedDomain.getSpec().getClusters().get(0).withReplicas(GOOD_REPLICAS);

    assertThat(ValidationUtils.isProposedChangeAllowed(existingDomain, proposedDomain), equalTo(true));
  }

  @Test
  void whenOneClusterReplicasChangedAloneAndInvalid_returnFalse() {
    proposedDomain.getSpec().getClusters().get(0).withReplicas(BAD_REPLICAS);

    assertThat(ValidationUtils.isProposedChangeAllowed(existingDomain, proposedDomain), equalTo(false));
  }

  @Test
  void whenIntrospectionVersionChangedAndOneClusterReplicasInvalid_returnTrue() {
    proposedDomain.getSpec().setIntrospectVersion(NEW_INTROSPECT_VERSION);
    proposedDomain.getSpec().getClusters().get(0).withReplicas(BAD_REPLICAS);

    assertThat(ValidationUtils.isProposedChangeAllowed(existingDomain, proposedDomain), equalTo(true));
  }

  @Test
  void whenImageChangedAndOneClusterReplicasInvalid_returnTrue() {
    proposedDomain.getSpec().withImage(NEW_IMAGE_NAME);
    proposedDomain.getSpec().getClusters().get(0).withReplicas(BAD_REPLICAS);
    assertThat(ValidationUtils.isProposedChangeAllowed(existingDomain, proposedDomain), equalTo(true));
  }

  @Test
  void whenLogHomeChangedAndOneClusterReplicasChangedInvalid_returnFalse() {
    proposedDomain.getSpec().getClusters().get(0).withReplicas(BAD_REPLICAS);
    proposedDomain.getSpec().setLogHome(NEW_LOG_HOME);

    assertThat(ValidationUtils.isProposedChangeAllowed(existingDomain, proposedDomain), equalTo(false));
  }

  @Test
  void whenDomainReplicasChangedInvalidAndOneClusterReplicasChangedValid_returnFalse() {
    proposedDomain.getSpec().withReplicas(BAD_REPLICAS);
    proposedDomain.getSpec().getClusters().get(0).withReplicas(1);

    assertThat(ValidationUtils.isProposedChangeAllowed(existingDomain, proposedDomain), equalTo(false));
  }

  @Test
  void whenDomainReplicasChangedInvalidAndBothClusterReplicasChangedValid_returnTrue() {
    proposedDomain.getSpec().withReplicas(BAD_REPLICAS);
    proposedDomain.getSpec().getClusters().get(0).withReplicas(GOOD_REPLICAS);
    proposedDomain.getSpec().getClusters().get(1).withReplicas(GOOD_REPLICAS);

    assertThat(ValidationUtils.isProposedChangeAllowed(existingDomain, proposedDomain), equalTo(true));
  }

  @Test
  void whenDomainReplicasChangedValidAndOneClusterReplicasChangedInvalid_returnFalse() {
    proposedDomain.getSpec().withReplicas(GOOD_REPLICAS);
    proposedDomain.getSpec().getClusters().get(0).withReplicas(GOOD_REPLICAS);
    proposedDomain.getSpec().getClusters().get(1).withReplicas(BAD_REPLICAS);

    assertThat(ValidationUtils.isProposedChangeAllowed(existingDomain, proposedDomain), equalTo(false));
  }

  @Test
  void whenDomainSourceTypeChanged_returnTrue() {
    proposedDomain.getSpec().withDomainHomeSourceType(DomainSourceType.IMAGE);

    assertThat(ValidationUtils.isProposedChangeAllowed(existingDomain, proposedDomain), equalTo(true));
  }

  @Test
  void whenDomainSourceTypeUnchanged_returnTrue() {
    existingDomain.getSpec().withDomainHomeSourceType(DomainSourceType.IMAGE);
    proposedDomain.getSpec().withDomainHomeSourceType(DomainSourceType.IMAGE);

    assertThat(ValidationUtils.isProposedChangeAllowed(existingDomain, proposedDomain), equalTo(true));
  }

  @Test
  void whenDomainSourceTypeUnchangedAndDomainReplicasValid_returnTrue() {
    proposedDomain.getSpec().withReplicas(GOOD_REPLICAS);
    existingDomain.getSpec().withDomainHomeSourceType(DomainSourceType.IMAGE);
    proposedDomain.getSpec().withDomainHomeSourceType(DomainSourceType.IMAGE);

    assertThat(ValidationUtils.isProposedChangeAllowed(existingDomain, proposedDomain), equalTo(true));
  }

  @Test
  void whenDomainSourceTypeUnchangedAndDomainReplicasInvalid_returnFalse() {
    proposedDomain.getSpec().withReplicas(BAD_REPLICAS);
    existingDomain.getSpec().withDomainHomeSourceType(DomainSourceType.IMAGE);
    proposedDomain.getSpec().withDomainHomeSourceType(DomainSourceType.IMAGE);

    assertThat(ValidationUtils.isProposedChangeAllowed(existingDomain, proposedDomain), equalTo(false));
  }

  @Test
  void whenDomainSourceTypePVAndDomainImageChangedReplicasInvalid_returnFalse() {
    proposedDomain.getSpec().withReplicas(BAD_REPLICAS);
    proposedDomain.getSpec().withImage(NEW_IMAGE_NAME);
    existingDomain.getSpec().withDomainHomeSourceType(DomainSourceType.PERSISTENT_VOLUME);
    proposedDomain.getSpec().withDomainHomeSourceType(DomainSourceType.PERSISTENT_VOLUME);

    assertThat(ValidationUtils.isProposedChangeAllowed(existingDomain, proposedDomain), equalTo(false));
  }

  @Test
  void whenDomainSourceTypeBothMIINoAuxImgImageChangedAndDomainImageChangedReplicasInvalid_returnTrue() {
    proposedDomain.getSpec().withReplicas(BAD_REPLICAS);
    proposedDomain.getSpec().withImage(NEW_IMAGE_NAME);
    existingDomain.getSpec().withDomainHomeSourceType(DomainSourceType.FROM_MODEL);
    proposedDomain.getSpec().withDomainHomeSourceType(DomainSourceType.FROM_MODEL);

    assertThat(ValidationUtils.isProposedChangeAllowed(existingDomain, proposedDomain), equalTo(true));
  }

  @Test
  void whenDomainSourceTypeBothMIINoAuxImgAndDomainImageChangedReplicasInvalid_returnFalse() {
    proposedDomain.getSpec().withReplicas(BAD_REPLICAS);
    existingDomain.getSpec().withDomainHomeSourceType(DomainSourceType.FROM_MODEL);
    proposedDomain.getSpec().withDomainHomeSourceType(DomainSourceType.FROM_MODEL);

    assertThat(ValidationUtils.isProposedChangeAllowed(existingDomain, proposedDomain), equalTo(false));
  }

  @Test
  void whenDomainSourceTypeBothMIIAddAuxImgAndDomainImageChangedReplicasInvalid_returnTrue() {
    proposedDomain.getSpec().withReplicas(BAD_REPLICAS);
    proposedDomain.getSpec().withImage(NEW_IMAGE_NAME);
    setAuxiliaryImages(proposedDomain, asList(createAuxiliaryImage(AUX_IMAGE_1), createAuxiliaryImage(AUX_IMAGE_2)));
    existingDomain.getSpec().withDomainHomeSourceType(DomainSourceType.FROM_MODEL);
    proposedDomain.getSpec().withDomainHomeSourceType(DomainSourceType.FROM_MODEL);

    assertThat(ValidationUtils.isProposedChangeAllowed(existingDomain, proposedDomain), equalTo(true));
  }

  @Test
  void whenDomainSourceTypeBothMIIRemoveAuxImgAndDomainImageChangedReplicasInvalid_returnTrue() {
    proposedDomain.getSpec().withReplicas(BAD_REPLICAS);
    proposedDomain.getSpec().withImage(NEW_IMAGE_NAME);
    setAuxiliaryImages(existingDomain, asList(createAuxiliaryImage(AUX_IMAGE_1), createAuxiliaryImage(AUX_IMAGE_2)));
    existingDomain.getSpec().withDomainHomeSourceType(DomainSourceType.FROM_MODEL);
    proposedDomain.getSpec().withDomainHomeSourceType(DomainSourceType.FROM_MODEL);

    assertThat(ValidationUtils.isProposedChangeAllowed(existingDomain, proposedDomain), equalTo(true));
  }

  @Test
  void whenDomainSourceTypeBothMIIAuxImgSameAndDomainImageChangedReplicasInvalid_returnFalse() {
    proposedDomain.getSpec().withReplicas(BAD_REPLICAS);
    proposedDomain.getSpec().withImage(NEW_IMAGE_NAME);
    setAuxiliaryImages(existingDomain, asList(createAuxiliaryImage(AUX_IMAGE_1), createAuxiliaryImage(AUX_IMAGE_2)));
    setAuxiliaryImages(proposedDomain, asList(createAuxiliaryImage(AUX_IMAGE_1), createAuxiliaryImage(AUX_IMAGE_2)));
    existingDomain.getSpec().withDomainHomeSourceType(DomainSourceType.FROM_MODEL);
    proposedDomain.getSpec().withDomainHomeSourceType(DomainSourceType.FROM_MODEL);

    assertThat(ValidationUtils.isProposedChangeAllowed(existingDomain, proposedDomain), equalTo(false));
  }

  @Test
  void whenDomainSourceTypeBothMIIAuxImgSameInDifferentOrderAndDomainImageChangedReplicasInvalid_returnFalse() {
    proposedDomain.getSpec().withReplicas(BAD_REPLICAS);
    proposedDomain.getSpec().withImage(NEW_IMAGE_NAME);
    setAuxiliaryImages(existingDomain, asList(createAuxiliaryImage(AUX_IMAGE_2), createAuxiliaryImage(AUX_IMAGE_1)));
    setAuxiliaryImages(proposedDomain, asList(createAuxiliaryImage(AUX_IMAGE_1), createAuxiliaryImage(AUX_IMAGE_2)));
    existingDomain.getSpec().withDomainHomeSourceType(DomainSourceType.FROM_MODEL);
    proposedDomain.getSpec().withDomainHomeSourceType(DomainSourceType.FROM_MODEL);

    assertThat(ValidationUtils.isProposedChangeAllowed(existingDomain, proposedDomain), equalTo(false));
  }

  @Test
  void whenDomainSourceTypeBothMIIAuxImgDifferentAndDomainImageChangedReplicasInvalid_returnTrue() {
    proposedDomain.getSpec().withReplicas(BAD_REPLICAS);
    proposedDomain.getSpec().withImage(NEW_IMAGE_NAME);
    setAuxiliaryImages(existingDomain, Collections.singletonList(createAuxiliaryImage(AUX_IMAGE_1)));
    setAuxiliaryImages(proposedDomain, asList(createAuxiliaryImage(AUX_IMAGE_1), createAuxiliaryImage(AUX_IMAGE_2)));
    existingDomain.getSpec().withDomainHomeSourceType(DomainSourceType.FROM_MODEL);
    proposedDomain.getSpec().withDomainHomeSourceType(DomainSourceType.FROM_MODEL);

    assertThat(ValidationUtils.isProposedChangeAllowed(existingDomain, proposedDomain), equalTo(true));
  }

  @Test
  void whenDomainSourceTypeNullWithModelAndDomainImageChangedReplicasInvalid_returnTrue() {
    proposedDomain.getSpec().withReplicas(BAD_REPLICAS);
    proposedDomain.getSpec().withImage(NEW_IMAGE_NAME);
    setAuxiliaryImages(proposedDomain, asList(createAuxiliaryImage(AUX_IMAGE_1), createAuxiliaryImage(AUX_IMAGE_2)));

    assertThat(ValidationUtils.isProposedChangeAllowed(existingDomain, proposedDomain), equalTo(true));
  }

  private AuxiliaryImage createAuxiliaryImage(String imageName) {
    return new AuxiliaryImage().image(imageName);
  }

  private void setAuxiliaryImages(Domain domain, List<AuxiliaryImage> images) {
    domain.getSpec().withConfiguration(new Configuration().withModel(new Model().withAuxiliaryImages(images)));
  }

}

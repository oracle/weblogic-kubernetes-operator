// Copyright (c) 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helpers;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import oracle.kubernetes.operator.DomainSourceType;
import oracle.kubernetes.operator.utils.ValidationUtils;
import oracle.kubernetes.weblogic.domain.model.AuxiliaryImage;
import oracle.kubernetes.weblogic.domain.model.Cluster;
import oracle.kubernetes.weblogic.domain.model.ClusterStatus;
import oracle.kubernetes.weblogic.domain.model.Configuration;
import oracle.kubernetes.weblogic.domain.model.Domain;
import oracle.kubernetes.weblogic.domain.model.DomainStatus;
import oracle.kubernetes.weblogic.domain.model.Model;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

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

  private final Domain domain1 = createDomain();
  private final Domain domain2 = createDomain();

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

  private Cluster createCluster(String clusterName) {
    return new Cluster().withClusterName(clusterName);
  }

  private DomainStatus createDomainStatus() {
    return new DomainStatus()
        .addCluster(createClusterStatus(CLUSTER_NAME_1))
        .addCluster(createClusterStatus(CLUSTER_NAME_2));
  }

  private ClusterStatus createClusterStatus(String clusterName) {
    return new ClusterStatus().withClusterName(clusterName).withMaximumReplicas(ORIGINAL_REPLICAS);
  }

  @BeforeEach
  public void setUp() throws Exception {
    restoreDomain(domain1);
    restoreDomain(domain2);
  }

  private void restoreDomain(Domain domain) {
    domain.getSpec()
        .withDomainHomeSourceType(null)
        .withReplicas(ORIGINAL_REPLICAS)
        .withImage(ORIGINAL_IMAGE_NAME)
        .withConfiguration(null);
    domain.getSpec().setIntrospectVersion(ORIGINAL_INTROSPECT_VERSION);
    domain.getSpec().getClusters().forEach(c -> c.withReplicas(null));
  }

  @Test
  void whenSameObject_returnTrue() {
    assertThat(ValidationUtils.validateDomain(domain1, domain1), equalTo(true));
  }

  @Test
  void whenNothingChanged_returnTrue() {
    assertThat(ValidationUtils.validateDomain(domain1, domain2), equalTo(true));
  }

  @Test
  void whenDomainReplicasChangedAloneValid_returnTrue() {
    domain2.getSpec().withReplicas(GOOD_REPLICAS);

    assertThat(ValidationUtils.validateDomain(domain1, domain2), equalTo(true));
  }

  @Test
  void whenDomainReplicasChangedAloneAndInvalid_returnFalse() {
    domain2.getSpec().withReplicas(BAD_REPLICAS);

    assertThat(ValidationUtils.validateDomain(domain1, domain2), equalTo(false));
  }

  @Test
  void whenIntrospectionVersionChangedAndDomainReplicasInvalid_returnTrue() {
    domain2.getSpec().setIntrospectVersion(NEW_INTROSPECT_VERSION);
    domain2.getSpec().withReplicas(BAD_REPLICAS);

    assertThat(ValidationUtils.validateDomain(domain1, domain2), equalTo(true));
  }

  @Test
  void whenDomainImageChangedAndReplicasInvalid_returnTrue() {
    domain2.getSpec().withImage(NEW_IMAGE_NAME);
    domain2.getSpec().withReplicas(BAD_REPLICAS);

    assertThat(ValidationUtils.validateDomain(domain1, domain2), equalTo(true));
  }

  @Test
  void whenLogHomeChangedAndDomainReplicasInvalid_returnFalse() {
    domain2.getSpec().withReplicas(BAD_REPLICAS);
    domain2.getSpec().setLogHome("/home/dir");

    assertThat(ValidationUtils.validateDomain(domain1, domain2), equalTo(false));
  }

  @Test
  void whenOneClusterReplicasChangedAloneAndValid_returnTrue() {
    domain2.getSpec().getClusters().get(0).withReplicas(GOOD_REPLICAS);

    assertThat(ValidationUtils.validateDomain(domain1, domain2), equalTo(true));
  }

  @Test
  void whenOneClusterReplicasChangedAloneAndInvalid_returnFalse() {
    domain2.getSpec().getClusters().get(0).withReplicas(BAD_REPLICAS);

    assertThat(ValidationUtils.validateDomain(domain1, domain2), equalTo(false));
  }

  @Test
  void whenIntrospectionVersionChangedAndOneClusterReplicasInvalid_returnTrue() {
    domain2.getSpec().setIntrospectVersion(NEW_INTROSPECT_VERSION);
    domain2.getSpec().getClusters().get(0).withReplicas(BAD_REPLICAS);

    assertThat(ValidationUtils.validateDomain(domain1, domain2), equalTo(true));
  }

  @Test
  void whenImageChangedAndOneClusterReplicasInvalid_returnTrue() {
    domain2.getSpec().withImage(NEW_IMAGE_NAME);
    domain2.getSpec().getClusters().get(0).withReplicas(BAD_REPLICAS);
    assertThat(ValidationUtils.validateDomain(domain1, domain2), equalTo(true));
  }

  @Test
  void whenLogHomeChangedAndOneClusterReplicasChangedInvalid_returnFalse() {
    domain2.getSpec().getClusters().get(0).withReplicas(BAD_REPLICAS);
    domain2.getSpec().setLogHome(NEW_LOG_HOME);

    assertThat(ValidationUtils.validateDomain(domain1, domain2), equalTo(false));
  }

  @Test
  void whenDomainReplicasChangedInvalidAndOneClusterReplicasChangedValid_returnFalse() {
    domain2.getSpec().withReplicas(BAD_REPLICAS);
    domain2.getSpec().getClusters().get(0).withReplicas(1);

    assertThat(ValidationUtils.validateDomain(domain1, domain2), equalTo(false));
  }

  @Test
  void whenDomainReplicasChangedInvalidAndBothClusterReplicasChangedValid_returnTrue() {
    domain2.getSpec().withReplicas(BAD_REPLICAS);
    domain2.getSpec().getClusters().get(0).withReplicas(GOOD_REPLICAS);
    domain2.getSpec().getClusters().get(1).withReplicas(GOOD_REPLICAS);

    assertThat(ValidationUtils.validateDomain(domain1, domain2), equalTo(true));
  }

  @Test
  void whenDomainReplicasChangedValidAndOneClusterReplicasChangedInvalid_returnFalse() {
    domain2.getSpec().withReplicas(GOOD_REPLICAS);
    domain2.getSpec().getClusters().get(0).withReplicas(GOOD_REPLICAS);
    domain2.getSpec().getClusters().get(1).withReplicas(BAD_REPLICAS);

    assertThat(ValidationUtils.validateDomain(domain1, domain2), equalTo(false));
  }

  @Test
  void whenDomainSourceTypeChanged_returnTrue() {
    domain2.getSpec().withDomainHomeSourceType(DomainSourceType.IMAGE);

    assertThat(ValidationUtils.validateDomain(domain1, domain2), equalTo(true));
  }

  @Test
  void whenDomainSourceTypeUnchanged_returnTrue() {
    domain1.getSpec().withDomainHomeSourceType(DomainSourceType.IMAGE);
    domain2.getSpec().withDomainHomeSourceType(DomainSourceType.IMAGE);

    assertThat(ValidationUtils.validateDomain(domain1, domain2), equalTo(true));
  }

  @Test
  void whenDomainSourceTypeUnchangedAndDomainReplicasValid_returnTrue() {
    domain2.getSpec().withReplicas(GOOD_REPLICAS);
    domain1.getSpec().withDomainHomeSourceType(DomainSourceType.IMAGE);
    domain2.getSpec().withDomainHomeSourceType(DomainSourceType.IMAGE);

    assertThat(ValidationUtils.validateDomain(domain1, domain2), equalTo(true));
  }

  @Test
  void whenDomainSourceTypeUnchangedAndDomainReplicasInvalid_returnFalse() {
    domain2.getSpec().withReplicas(BAD_REPLICAS);
    domain1.getSpec().withDomainHomeSourceType(DomainSourceType.IMAGE);
    domain2.getSpec().withDomainHomeSourceType(DomainSourceType.IMAGE);

    assertThat(ValidationUtils.validateDomain(domain1, domain2), equalTo(false));
  }

  @Test
  void whenDomainSourceTypePVAndDomainImageChangedReplicasInvalid_returnFalse() {
    domain2.getSpec().withReplicas(BAD_REPLICAS);
    domain2.getSpec().withImage(NEW_IMAGE_NAME);
    domain1.getSpec().withDomainHomeSourceType(DomainSourceType.PERSISTENT_VOLUME);
    domain2.getSpec().withDomainHomeSourceType(DomainSourceType.PERSISTENT_VOLUME);

    assertThat(ValidationUtils.validateDomain(domain1, domain2), equalTo(false));
  }

  @Test
  void whenDomainSourceTypeBothMIINoAuxImgImageChangedAndDomainImageChangedReplicasInvalid_returnTrue() {
    domain2.getSpec().withReplicas(BAD_REPLICAS);
    domain2.getSpec().withImage(NEW_IMAGE_NAME);
    domain1.getSpec().withDomainHomeSourceType(DomainSourceType.FROM_MODEL);
    domain2.getSpec().withDomainHomeSourceType(DomainSourceType.FROM_MODEL);

    assertThat(ValidationUtils.validateDomain(domain1, domain2), equalTo(true));
  }

  @Test
  void whenDomainSourceTypeBothMIINoAuxImgAndDomainImageChangedReplicasInvalid_returnFalse() {
    domain2.getSpec().withReplicas(BAD_REPLICAS);
    domain1.getSpec().withDomainHomeSourceType(DomainSourceType.FROM_MODEL);
    domain2.getSpec().withDomainHomeSourceType(DomainSourceType.FROM_MODEL);

    assertThat(ValidationUtils.validateDomain(domain1, domain2), equalTo(false));
  }

  @Test
  void whenDomainSourceTypeBothMIIAddAuxImgAndDomainImageChangedReplicasInvalid_returnTrue() {
    domain2.getSpec().withReplicas(BAD_REPLICAS);
    domain2.getSpec().withImage(NEW_IMAGE_NAME);
    setAuxiliaryImages(domain2, Arrays.asList(createAuxiliaryImage(AUX_IMAGE_1), createAuxiliaryImage(AUX_IMAGE_2)));
    domain1.getSpec().withDomainHomeSourceType(DomainSourceType.FROM_MODEL);
    domain2.getSpec().withDomainHomeSourceType(DomainSourceType.FROM_MODEL);

    assertThat(ValidationUtils.validateDomain(domain1, domain2), equalTo(true));
  }

  @Test
  void whenDomainSourceTypeBothMIIRemoveAuxImgAndDomainImageChangedReplicasInvalid_returnTrue() {
    domain2.getSpec().withReplicas(BAD_REPLICAS);
    domain2.getSpec().withImage(NEW_IMAGE_NAME);
    setAuxiliaryImages(domain1, Arrays.asList(createAuxiliaryImage(AUX_IMAGE_1), createAuxiliaryImage(AUX_IMAGE_2)));
    domain1.getSpec().withDomainHomeSourceType(DomainSourceType.FROM_MODEL);
    domain2.getSpec().withDomainHomeSourceType(DomainSourceType.FROM_MODEL);

    assertThat(ValidationUtils.validateDomain(domain1, domain2), equalTo(true));
  }

  @Test
  void whenDomainSourceTypeBothMIIAuxImgSameAndDomainImageChangedReplicasInvalid_returnFalse() {
    domain2.getSpec().withReplicas(BAD_REPLICAS);
    domain2.getSpec().withImage(NEW_IMAGE_NAME);
    setAuxiliaryImages(domain1, Arrays.asList(createAuxiliaryImage(AUX_IMAGE_1), createAuxiliaryImage(AUX_IMAGE_2)));
    setAuxiliaryImages(domain2, Arrays.asList(createAuxiliaryImage(AUX_IMAGE_1), createAuxiliaryImage(AUX_IMAGE_2)));
    domain1.getSpec().withDomainHomeSourceType(DomainSourceType.FROM_MODEL);
    domain2.getSpec().withDomainHomeSourceType(DomainSourceType.FROM_MODEL);

    assertThat(ValidationUtils.validateDomain(domain1, domain2), equalTo(false));
  }

  @Test
  void whenDomainSourceTypeBothMIIAuxImgSameInDifferentOrderAndDomainImageChangedReplicasInvalid_returnFalse() {
    domain2.getSpec().withReplicas(BAD_REPLICAS);
    domain2.getSpec().withImage(NEW_IMAGE_NAME);
    setAuxiliaryImages(domain1, Arrays.asList(createAuxiliaryImage(AUX_IMAGE_2), createAuxiliaryImage(AUX_IMAGE_1)));
    setAuxiliaryImages(domain2, Arrays.asList(createAuxiliaryImage(AUX_IMAGE_1), createAuxiliaryImage(AUX_IMAGE_2)));
    domain1.getSpec().withDomainHomeSourceType(DomainSourceType.FROM_MODEL);
    domain2.getSpec().withDomainHomeSourceType(DomainSourceType.FROM_MODEL);

    assertThat(ValidationUtils.validateDomain(domain1, domain2), equalTo(false));
  }

  @Test
  void whenDomainSourceTypeBothMIIAuxImgDifferentAndDomainImageChangedReplicasInvalid_returnTrue() {
    domain2.getSpec().withReplicas(BAD_REPLICAS);
    domain2.getSpec().withImage(NEW_IMAGE_NAME);
    setAuxiliaryImages(domain1, Collections.singletonList(createAuxiliaryImage(AUX_IMAGE_1)));
    setAuxiliaryImages(domain2, Arrays.asList(createAuxiliaryImage(AUX_IMAGE_1), createAuxiliaryImage(AUX_IMAGE_2)));
    domain1.getSpec().withDomainHomeSourceType(DomainSourceType.FROM_MODEL);
    domain2.getSpec().withDomainHomeSourceType(DomainSourceType.FROM_MODEL);

    assertThat(ValidationUtils.validateDomain(domain1, domain2), equalTo(true));
  }

  @Test
  void whenDomainSourceTypeNullWithModelAndDomainImageChangedReplicasInvalid_returnTrue() {
    domain2.getSpec().withReplicas(BAD_REPLICAS);
    domain2.getSpec().withImage(NEW_IMAGE_NAME);
    setAuxiliaryImages(domain2, Arrays.asList(createAuxiliaryImage(AUX_IMAGE_1), createAuxiliaryImage(AUX_IMAGE_2)));

    assertThat(ValidationUtils.validateDomain(domain1, domain2), equalTo(true));
  }

  private AuxiliaryImage createAuxiliaryImage(String imageName) {
    return new AuxiliaryImage().image(imageName);
  }

  private void setAuxiliaryImages(Domain domain, List<AuxiliaryImage> images) {
    domain.getSpec().withConfiguration(new Configuration().withModel(new Model().withAuxiliaryImages(images)));
  }

}

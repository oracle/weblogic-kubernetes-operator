// Copyright (c) 2020, 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.weblogic.domain.model;

import oracle.kubernetes.operator.KubernetesConstants;
import org.junit.jupiter.api.Test;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.junit.MatcherAssert.assertThat;

public class DomainSpecTest {

  @Test
  public void whenEffectiveDomainSpecsWithDefaultImage_domainSpecEquals() {
    DomainSpec spec1 = new DomainSpec().withImage(KubernetesConstants.DEFAULT_IMAGE);
    DomainSpec spec2 = new DomainSpec();
    assertThat("Expected effective image values to be equal", spec1, equalTo(spec2));
  }

  @Test
  public void whenEffectiveDomainSpecsWithLatestImage_domainSpecNotEquals() {
    DomainSpec spec1 = new DomainSpec().withImage(getLatestDefaultImage());
    DomainSpec spec2 = new DomainSpec();
    assertThat("Expected effective image values to NOT be equal", spec1, not(equalTo(spec2)));
  }

  @Test
  public void whenEffectiveImagePullPolicyWithDefaultImage_domainSpecImagePullPolicyEquals() {
    DomainSpec spec1 = new DomainSpec().withImage(KubernetesConstants.DEFAULT_IMAGE);
    DomainSpec spec2 = new DomainSpec();
    assertThat("Expected effective image pull policy values to be equal",
        spec1.getImagePullPolicy(), equalTo(spec2.getImagePullPolicy()));
  }

  @Test
  public void whenEffectiveImagePullPolicyWithLatestImage_domainSpecImagePullPolicyNotEquals() {
    DomainSpec spec1 = new DomainSpec().withImage(getLatestDefaultImage());
    DomainSpec spec2 = new DomainSpec();
    assertThat("Expected effective image pull policy values to NOT be equal",
        spec1.getImagePullPolicy(), not(equalTo(spec2.getImagePullPolicy())));
  }

  @Test
  public void whenEffectiveImageWithDefaultImage_imageEquals() {
    DomainSpec spec1 = new DomainSpec().withImage(KubernetesConstants.DEFAULT_IMAGE);
    DomainSpec spec2 = new DomainSpec();
    assertThat("Expected effective image values to be equal",
        spec1.getImage(), equalTo(spec2.getImage()));
  }

  @Test
  public void whenEffectiveImageWithLatestImage_imageNotEquals() {
    DomainSpec spec1 = new DomainSpec().withImage(getLatestDefaultImage());
    DomainSpec spec2 = new DomainSpec();
    assertThat("Expected effective image pull policy values to NOT be equal",
        spec1.getImage(), not(equalTo(spec2.getImage())));
  }

  @Test
  public void verifyThatDomainSpecWithoutAllowMinDynClusterSize_equalsToDomainSpecWithDefaultValue() {
    DomainSpec spec1 = new DomainSpec();
    DomainSpec spec2 = new DomainSpec();
    spec2.setAllowReplicasBelowMinDynClusterSize(KubernetesConstants.DEFAULT_ALLOW_REPLICAS_BELOW_MIN_DYN_CLUSTER_SIZE);
    assertThat("Expected null allowReplicasBelowMinDynClusterSize equal to explicit set to "
        + KubernetesConstants.DEFAULT_ALLOW_REPLICAS_BELOW_MIN_DYN_CLUSTER_SIZE,
        spec1.equals(spec2), equalTo(true));
  }

  @Test
  public void verifyThatDomainSpecWithoutAllowMinDynClusterSize_notEqualsToDomainSpecWithFalse() {
    DomainSpec spec1 = new DomainSpec();
    DomainSpec spec2 = new DomainSpec();
    spec2.setAllowReplicasBelowMinDynClusterSize(
        !KubernetesConstants.DEFAULT_ALLOW_REPLICAS_BELOW_MIN_DYN_CLUSTER_SIZE);
    assertThat("Expected null allowReplicasBelowMinDynClusterSize not equal to explicit set to "
        + !KubernetesConstants.DEFAULT_ALLOW_REPLICAS_BELOW_MIN_DYN_CLUSTER_SIZE,
        spec1.equals(spec2), equalTo(false));
  }

  @Test
  public void verifyThatDomainSpecWithoutMaxClusterConcurrentStartup_equalsToDomainSpecWithDefaultValue() {
    DomainSpec spec1 = new DomainSpec();
    DomainSpec spec2 = new DomainSpec();
    spec2.setMaxClusterConcurrentStartup(KubernetesConstants.DEFAULT_MAX_CLUSTER_CONCURRENT_START_UP);
    assertThat("Expected null maxClusterConcurrentStartup equal to explicit set to "
        + KubernetesConstants.DEFAULT_MAX_CLUSTER_CONCURRENT_START_UP,
        spec1.equals(spec2), equalTo(true));
  }

  @Test
  public void verifyThatDomainSpecWithoutMaxClusterConcurrentStartup_notEqualsToDomainSpecWithFalse() {
    DomainSpec spec1 = new DomainSpec();
    DomainSpec spec2 = new DomainSpec();
    spec2.setMaxClusterConcurrentStartup(KubernetesConstants.DEFAULT_MAX_CLUSTER_CONCURRENT_START_UP
        + 1);
    assertThat("Expected null maxClusterConcurrentStartup not equal to explicit set to "
            + (KubernetesConstants.DEFAULT_MAX_CLUSTER_CONCURRENT_START_UP + 1),
        spec1.equals(spec2), equalTo(false));
  }

  private String getLatestDefaultImage() {
    String defaultImageName = KubernetesConstants.DEFAULT_IMAGE
        .substring(0, KubernetesConstants.DEFAULT_IMAGE.indexOf(':'));
    String latestImage = defaultImageName + KubernetesConstants.LATEST_IMAGE_SUFFIX;
    return latestImage;
  }
}

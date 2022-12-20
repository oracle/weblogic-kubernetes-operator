// Copyright (c) 2020, 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.weblogic.domain.model;

import oracle.kubernetes.operator.KubernetesConstants;
import org.junit.jupiter.api.Test;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.junit.MatcherAssert.assertThat;

class DomainResourceSpecTest {

  @Test
  void whenEffectiveDomainSpecsWithDefaultImage_domainSpecEquals() {
    DomainSpec spec1 = new DomainSpec().withImage(KubernetesConstants.DEFAULT_IMAGE);
    DomainSpec spec2 = new DomainSpec();
    assertThat("Expected effective image values to be equal", spec1, equalTo(spec2));
  }

  @Test
  void whenEffectiveDomainSpecsWithLatestImage_domainSpecNotEquals() {
    DomainSpec spec1 = new DomainSpec().withImage(getLatestDefaultImage());
    DomainSpec spec2 = new DomainSpec();
    assertThat("Expected effective image values to NOT be equal", spec1, not(equalTo(spec2)));
  }

  @Test
  void whenEffectiveImagePullPolicyWithDefaultImage_domainSpecImagePullPolicyEquals() {
    DomainSpec spec1 = new DomainSpec().withImage(KubernetesConstants.DEFAULT_IMAGE);
    DomainSpec spec2 = new DomainSpec();
    assertThat("Expected effective image pull policy values to be equal",
        spec1.getImagePullPolicy(), equalTo(spec2.getImagePullPolicy()));
  }

  @Test
  void whenEffectiveImagePullPolicyWithLatestImage_domainSpecImagePullPolicyNotEquals() {
    DomainSpec spec1 = new DomainSpec().withImage(getLatestDefaultImage());
    DomainSpec spec2 = new DomainSpec();
    assertThat("Expected effective image pull policy values to NOT be equal",
        spec1.getImagePullPolicy(), not(equalTo(spec2.getImagePullPolicy())));
  }

  @Test
  void whenEffectiveImageWithDefaultImage_imageEquals() {
    DomainSpec spec1 = new DomainSpec().withImage(KubernetesConstants.DEFAULT_IMAGE);
    DomainSpec spec2 = new DomainSpec();
    assertThat("Expected effective image values to be equal",
        spec1.getImage(), equalTo(spec2.getImage()));
  }

  @Test
  void whenEffectiveImageWithLatestImage_imageNotEquals() {
    DomainSpec spec1 = new DomainSpec().withImage(getLatestDefaultImage());
    DomainSpec spec2 = new DomainSpec();
    assertThat("Expected effective image pull policy values to NOT be equal",
        spec1.getImage(), not(equalTo(spec2.getImage())));
  }

  @Test
  void verifyThatDomainSpecWithoutMaxClusterConcurrentStartup_equalsToDomainSpecWithDefaultValue() {
    DomainSpec spec1 = new DomainSpec();
    DomainSpec spec2 = new DomainSpec();
    spec2.setMaxClusterConcurrentStartup(KubernetesConstants.DEFAULT_MAX_CLUSTER_CONCURRENT_START_UP);
    assertThat("Expected null maxClusterConcurrentStartup equal to explicit set to "
        + KubernetesConstants.DEFAULT_MAX_CLUSTER_CONCURRENT_START_UP,
        spec1.equals(spec2), equalTo(true));
  }

  @Test
  void verifyThatDomainSpecWithoutMaxClusterConcurrentStartup_notEqualsToDomainSpecWithFalse() {
    DomainSpec spec1 = new DomainSpec();
    DomainSpec spec2 = new DomainSpec();
    spec2.setMaxClusterConcurrentStartup(KubernetesConstants.DEFAULT_MAX_CLUSTER_CONCURRENT_START_UP
        + 1);
    assertThat("Expected null maxClusterConcurrentStartup not equal to explicit set to "
            + (KubernetesConstants.DEFAULT_MAX_CLUSTER_CONCURRENT_START_UP + 1),
        spec1.equals(spec2), equalTo(false));
  }

  @Test
  void verifySettingFluentdSpecification() {
    DomainSpec spec1 = new DomainSpec().withFluentdConfiguration(true,
        "mycred", "<matcher/>", null, null);
    spec1.getFluentdSpecification().setImage("myimage:v1");
    spec1.getFluentdSpecification().setImagePullPolicy("Always");

    assertThat(spec1.getFluentdSpecification().getWatchIntrospectorLogs(), equalTo(true));
    assertThat(spec1.getFluentdSpecification().getElasticSearchCredentials(), equalTo("mycred"));
    assertThat(spec1.getFluentdSpecification().getFluentdConfiguration(), notNullValue());
    assertThat(spec1.getFluentdSpecification().getFluentdConfiguration(), equalTo("<matcher/>"));
    assertThat(spec1.getFluentdSpecification().getImagePullPolicy(), equalTo("Always"));
    assertThat(spec1.getFluentdSpecification().getImage(), equalTo("myimage:v1"));


  }

  private String getLatestDefaultImage() {
    String defaultImageName = KubernetesConstants.DEFAULT_IMAGE
        .substring(0, KubernetesConstants.DEFAULT_IMAGE.indexOf(':'));
    return defaultImageName + KubernetesConstants.LATEST_IMAGE_SUFFIX;
  }
}

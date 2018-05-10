// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helpers;

import static oracle.kubernetes.operator.KubernetesConstants.*;
import static org.hamcrest.MatcherAssert.*;
import static org.hamcrest.Matchers.*;

import oracle.kubernetes.weblogic.domain.v1.DomainSpec;
import org.junit.Test;

/** Tests DomainConfigBuilder's concrete methods */
public class DomainConfigBuilderTest extends DomainConfigBuilder {

  @Override
  public void updateDomainSpec(DomainSpec domainSpec, ClusterConfig clusterConfig) {}

  @Override
  public NonClusteredServerConfig getEffectiveNonClusteredServerConfig(
      DomainSpec domainSpec, String serverName) {
    return null;
  }

  @Override
  public ClusteredServerConfig getEffectiveClusteredServerConfig(
      DomainSpec domainSpec, String clusterName, String serverName) {
    return null;
  }

  @Override
  public ClusterConfig getEffectiveClusterConfig(DomainSpec domainSpec, String clusterName) {
    return null;
  }

  @Test
  public void getDefaultImagePullPolicy_nullImage_returns_ifNotPresent() {
    assertThat(getDefaultImagePullPolicy(null), equalTo(IFNOTPRESENT_IMAGEPULLPOLICY));
  }

  @Test
  public void getDefaultImagePullPolicy_imageDoesNotEndWithLatest_returns_ifNotPresent() {
    assertThat(getDefaultImagePullPolicy("image1"), equalTo(IFNOTPRESENT_IMAGEPULLPOLICY));
  }

  @Test
  public void getImagePullPolicy_imageEndsWithLatest_returns_always() {
    assertThat(
        getDefaultImagePullPolicy("image1" + LATEST_IMAGE_SUFFIX), equalTo(ALWAYS_IMAGEPULLPOLICY));
  }
}

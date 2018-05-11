// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helpers;

import static oracle.kubernetes.operator.KubernetesConstants.*;
import static org.hamcrest.MatcherAssert.*;
import static org.hamcrest.Matchers.*;

import org.junit.Test;

/** Tests DomainConfigBuilder's concrete methods */
public class DomainConfigBuilderTest {

  @Test
  public void getDefaultImagePullPolicy_nullImage_returns_ifNotPresent() {
    assertThat(newBuilder().getDefaultImagePullPolicy(null), equalTo(IFNOTPRESENT_IMAGEPULLPOLICY));
  }

  @Test
  public void getDefaultImagePullPolicy_imageDoesNotEndWithLatest_returns_ifNotPresent() {
    assertThat(
        newBuilder().getDefaultImagePullPolicy("image1"), equalTo(IFNOTPRESENT_IMAGEPULLPOLICY));
  }

  @Test
  public void getImagePullPolicy_imageEndsWithLatest_returns_always() {
    assertThat(
        newBuilder().getDefaultImagePullPolicy("image1" + LATEST_IMAGE_SUFFIX),
        equalTo(ALWAYS_IMAGEPULLPOLICY));
  }

  private DomainConfigBuilder newBuilder() {
    return new TestDomainConfigBuilder();
  }

  private static class TestDomainConfigBuilder extends DomainConfigBuilder {

    @Override
    public void updateDomainSpec(ClusterConfig clusterConfig) {}

    @Override
    public NonClusteredServerConfig getEffectiveNonClusteredServerConfig(String serverName) {
      return null;
    }

    @Override
    public ClusteredServerConfig getEffectiveClusteredServerConfig(
        String clusterName, String serverName) {
      return null;
    }

    @Override
    public ClusterConfig getEffectiveClusterConfig(String clusterName) {
      return null;
    }
  }
}

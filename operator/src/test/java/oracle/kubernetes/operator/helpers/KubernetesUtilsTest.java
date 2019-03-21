// Copyright 2019, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helpers;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.*;

import io.kubernetes.client.models.V1ObjectMeta;
import org.joda.time.DateTime;
import org.junit.Test;

public class KubernetesUtilsTest {

  @Test
  public void whenCreationTimesDiffer_metadataWithLaterTimeIsNewer() {
    V1ObjectMeta meta1 = new V1ObjectMeta().creationTimestamp(new DateTime(2)).resourceVersion("2");
    V1ObjectMeta meta2 = new V1ObjectMeta().creationTimestamp(new DateTime(1)).resourceVersion("1");

    assertThat(KubernetesUtils.isFirstNewer(meta1, meta2), is(true));
    assertThat(KubernetesUtils.isFirstNewer(meta2, meta1), is(false));
  }

  @Test
  public void whenCreationTimesMatch_metadataWithHigherResourceVersionIsNewer() {
    V1ObjectMeta meta1 = new V1ObjectMeta().creationTimestamp(new DateTime(1)).resourceVersion("2");
    V1ObjectMeta meta2 = new V1ObjectMeta().creationTimestamp(new DateTime(1)).resourceVersion("1");

    assertThat(KubernetesUtils.isFirstNewer(meta1, meta2), is(true));
    assertThat(KubernetesUtils.isFirstNewer(meta2, meta1), is(false));
  }

  @Test
  public void whenCreationTimesAndResourceVersionsMatch_neitherIsNewer() {
    V1ObjectMeta meta1 = new V1ObjectMeta().creationTimestamp(new DateTime(1)).resourceVersion("2");
    V1ObjectMeta meta2 = new V1ObjectMeta().creationTimestamp(new DateTime(1)).resourceVersion("2");

    assertThat(KubernetesUtils.isFirstNewer(meta2, meta1), is(false));
    assertThat(KubernetesUtils.isFirstNewer(meta1, meta2), is(false));
  }
}

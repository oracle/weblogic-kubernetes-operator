// Copyright (c) 2018, 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helpers;

import io.kubernetes.client.openapi.models.V1ObjectMeta;
import org.junit.Test;

import static oracle.kubernetes.operator.LabelConstants.RESOURCE_VERSION_LABEL;
import static oracle.kubernetes.operator.helpers.VersionHelper.matchesResourceVersion;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

public class VersionHelperTest {

  private static final String V1 = "v1";

  private static V1ObjectMeta newObjectMeta() {
    return new V1ObjectMeta();
  }

  @Test
  public void null_metadata_returns_false() throws Exception {
    assertThat(matchesResourceVersion(null, V1), equalTo(false));
  }

  @Test
  public void null_labels_returns_false() throws Exception {
    assertThat(matchesResourceVersion(newObjectMeta().labels(null), V1), equalTo(false));
  }

  @Test
  public void null_version_returns_false() throws Exception {
    assertThat(
        matchesResourceVersion(newObjectMeta().putLabelsItem(RESOURCE_VERSION_LABEL, null), V1),
        equalTo(false));
  }

  @Test
  public void different_version_returns_false() throws Exception {
    assertThat(
        matchesResourceVersion(newObjectMeta().putLabelsItem(RESOURCE_VERSION_LABEL, "v2"), V1),
        equalTo(false));
  }

  @Test
  public void same_version_returns_true() throws Exception {
    assertThat(
        matchesResourceVersion(newObjectMeta().putLabelsItem(RESOURCE_VERSION_LABEL, V1), V1),
        equalTo(true));
  }
}

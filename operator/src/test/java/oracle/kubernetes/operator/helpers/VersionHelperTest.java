// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helpers;

import static oracle.kubernetes.operator.LabelConstants.*;
import static oracle.kubernetes.operator.helpers.VersionHelper.*;
import static org.hamcrest.MatcherAssert.*;
import static org.hamcrest.Matchers.*;

import io.kubernetes.client.models.V1ObjectMeta;
import org.junit.Test;

public class VersionHelperTest {

  private static final String V1 = "v1";

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

  private static V1ObjectMeta newObjectMeta() {
    return new V1ObjectMeta();
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

// Copyright (c) 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator;

import io.kubernetes.client.openapi.models.V1ObjectMeta;
import oracle.kubernetes.operator.helpers.KubernetesUtils;
import org.joda.time.DateTime;
import org.junit.Test;

import static org.junit.Assert.assertFalse;

public class KubernetesUtilsTest {

  @Test
  public void whenHaveLargeResourceVersionsAndSameTime_succeedIsFirstNewer() {
    DateTime now = DateTime.now();

    // This needs to be a value bigger than 2147483647
    String resVersion = "2733280673";
    String evenBiggerResVersion = "2733280673000";

    V1ObjectMeta first = new V1ObjectMeta().creationTimestamp(now).resourceVersion(resVersion);
    V1ObjectMeta second = new V1ObjectMeta().creationTimestamp(now).resourceVersion(evenBiggerResVersion);

    assertFalse(KubernetesUtils.isFirstNewer(first, second));
  }

}

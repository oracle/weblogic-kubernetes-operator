// Copyright 2018, 2019, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helpers;

import static oracle.kubernetes.operator.helpers.LegalNames.toClusterServiceName;
import static oracle.kubernetes.operator.helpers.LegalNames.toServerServiceName;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

import org.junit.Test;

public class LegalNamesTest {

  @Test
  public void createValidServerServiceNames() throws Exception {
    assertThat(toServerServiceName("abc", "cls1"), equalTo("abc-cls1"));
    assertThat(toServerServiceName("Abc", "cLs1"), equalTo("abc-cls1"));
    assertThat(toServerServiceName("Abc", "cls_1"), equalTo("abc-cls-1"));
  }

  @Test
  public void createValidClusterServiceNames() throws Exception {
    assertThat(toClusterServiceName("abc", "cls1"), equalTo("abc-cluster-cls1"));
    assertThat(toClusterServiceName("Abc", "cLs1"), equalTo("abc-cluster-cls1"));
    assertThat(toClusterServiceName("Abc", "cls_1"), equalTo("abc-cluster-cls-1"));
  }
}

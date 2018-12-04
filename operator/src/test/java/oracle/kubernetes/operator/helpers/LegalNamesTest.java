// Copyright 2018 Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helpers;

import static oracle.kubernetes.operator.helpers.LegalNames.toClusterServiceName;
import static oracle.kubernetes.operator.helpers.LegalNames.toNAPName;
import static oracle.kubernetes.operator.helpers.LegalNames.toServerServiceName;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

import oracle.kubernetes.operator.wlsconfig.NetworkAccessPoint;
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

  @Test
  public void createValidNAPNames() throws Exception {
    NetworkAccessPoint nap1 = new NetworkAccessPoint("nap1", "http", 17000, 7001);
    assertThat(toNAPName("abc", "cls1", nap1), equalTo("abc-cls1-extchannel-nap1"));
    assertThat(toNAPName("Abc", "cLs1", nap1), equalTo("abc-cls1-extchannel-nap1"));

    NetworkAccessPoint nap2 = new NetworkAccessPoint("nap-2", "http", 17000, 7001);
    assertThat(toNAPName("Abc", "cls_1", nap2), equalTo("abc-cls-1-extchannel-nap-2"));
  }
}

// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.weblogic.domain.v2;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.junit.MatcherAssert.assertThat;

import org.junit.Test;

public class AdminServerTest extends BaseConfigurationTestBase {
  private AdminServer server1;
  private AdminServer server2;

  public AdminServerTest() {
    super(new AdminServer(), new AdminServer());
    server1 = getInstance1();
    server2 = getInstance2();
  }

  @Test
  public void whenExportedAccessPointsAreTheSame_objectsAreEqual() {
    server1
        .addExportedNetworkAccessPoint("nap1")
        .addLabel("label1", "value1")
        .addAnnotation("annotation1", "value2");
    server1.addExportedNetworkAccessPoint("nap2");
    server2.addExportedNetworkAccessPoint("nap2");
    server2
        .addExportedNetworkAccessPoint("nap1")
        .addAnnotation("annotation1", "value2")
        .addLabel("label1", "value1");

    assertThat(server1, equalTo(server1));
  }

  @Test
  public void whenExportedAccessPointsDifferByName_objectsAreNotEqual() {
    server1.addExportedNetworkAccessPoint("nap1");
    server2.addExportedNetworkAccessPoint("nap2");

    assertThat(server1, not(equalTo(server2)));
  }

  @Test
  public void whenExportedAccessPointsDifferByLabel_objectsAreNotEqual() {
    server1.addExportedNetworkAccessPoint("nap1").addLabel("a", "b");
    server2.addExportedNetworkAccessPoint("nap1").addLabel("a", "c");

    assertThat(server1, not(equalTo(server2)));
  }

  @Test
  public void whenExportedAccessPointsDifferByAnnotation_objectsAreNotEqual() {
    server1.addExportedNetworkAccessPoint("nap1").addAnnotation("a", "b");
    server2.addExportedNetworkAccessPoint("nap1").addAnnotation("a", "c");

    assertThat(server1, not(equalTo(server2)));
  }
}

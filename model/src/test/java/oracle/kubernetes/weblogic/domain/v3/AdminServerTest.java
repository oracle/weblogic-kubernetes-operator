// Copyright 2018, 2019, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.weblogic.domain.v3;

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
  public void whenChannelsLabelsAnnotationsAreTheSame_objectsAreEqual() {
    server1.withChannel("nap1", 0);
    server1.addPodLabel("label1", "value1");
    server1.addPodAnnotation("annotation1", "value2");
    server1.withChannel("nap2", 1);
    server2.withChannel("nap2", 1);
    server2.withChannel("nap1", 0);
    server2.addPodAnnotation("annotation1", "value2");
    server2.addPodLabel("label1", "value1");

    assertThat(server1, equalTo(server2));
  }

  @Test
  public void whenChannelsDifferByName_objectsAreNotEqual() {
    server1.withChannel("nap1", 0);
    server2.withChannel("nap2", 0);

    assertThat(server1, not(equalTo(server2)));
  }

  @Test
  public void whenDifferByPodLabel_objectsAreNotEqual() {
    server1.addPodLabel("a", "b");
    server2.addPodLabel("a", "c");

    assertThat(server1, not(equalTo(server2)));
  }

  @Test
  public void whenDifferByPodAnnotation_objectsAreNotEqual() {
    server1.addPodAnnotation("a", "b");
    server2.addPodAnnotation("a", "c");

    assertThat(server1, not(equalTo(server2)));
  }

  @Test
  public void whenDifferByServiceLabel_objectsAreNotEqual() {
    server1.addServiceLabel("a", "b");
    server2.addServiceLabel("a", "c");

    assertThat(server1, not(equalTo(server2)));
  }

  @Test
  public void whenDifferByServiceAnnotation_objectsAreNotEqual() {
    server1.addServiceAnnotation("a", "b");
    server2.addServiceAnnotation("a", "c");

    assertThat(server1, not(equalTo(server2)));
  }
}

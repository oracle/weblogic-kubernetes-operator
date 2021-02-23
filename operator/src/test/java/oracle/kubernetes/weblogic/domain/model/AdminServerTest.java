// Copyright (c) 2018, 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.weblogic.domain.model;

import oracle.kubernetes.weblogic.domain.AdminServerConfigurator;
import oracle.kubernetes.weblogic.domain.DomainConfiguratorFactory;
import org.junit.jupiter.api.Test;

import static oracle.kubernetes.weblogic.domain.ChannelMatcher.channelWith;
import static org.hamcrest.Matchers.both;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.junit.MatcherAssert.assertThat;

public class AdminServerTest extends BaseConfigurationTestBase {
  private static final String CHANNEL1 = "default";
  private static final int PORT1 = 12345;
  private static final String CHANNEL2 = "nap1";
  private static final int PORT2 = 56780;
  private static final String NAME1 = "name1";
  private static final String VALUE1 = "value1";
  private static final String NAME2 = "name2";
  private static final String VALUE2 = "value2";
  private final AdminServer server1;
  private final AdminServer server2;
  private final Domain domain = new Domain();

  /**
   * Administration server tests.
   */
  public AdminServerTest() {
    super(new AdminServer(), new AdminServer());
    server1 = getInstance1();
    server2 = getInstance2();
  }

  private AdminServerConfigurator configureAdminServer() {
    return DomainConfiguratorFactory.forDomain(domain).configureAdminServer();
  }

  @Test
  public void whenChannelsLabelsAnnotationsAreTheSame_objectsAreEqual() {
    server1.withChannel("nap1", 0);
    server1.addPodLabel("label1", VALUE1);
    server1.addPodAnnotation("annotation1", VALUE2);
    server1.withChannel("nap2", 1);
    server2.withChannel("nap2", 1);
    server2.withChannel("nap1", 0);
    server2.addPodAnnotation("annotation1", VALUE2);
    server2.addPodLabel("label1", VALUE1);

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

  @Test
  public void whenConstructedWithAdminService_mayCreateChannels() {
    configureAdminServer()
        .configureAdminService()
        .withChannel(CHANNEL1, PORT1)
        .withChannel(CHANNEL2, PORT2);

    assertThat(
        domain.getAdminServerSpec().getAdminService().getChannels(),
        containsInAnyOrder(channelWith(CHANNEL1, PORT1), channelWith(CHANNEL2, PORT2)));
  }

  @Test
  public void whenHaveSameAdminServiceChannels_objectsAreEqual() {
    server1.createAdminService().withChannel(CHANNEL1, PORT1).withChannel(CHANNEL2, PORT2);
    server2.createAdminService().withChannel(CHANNEL2, PORT2).withChannel(CHANNEL1, PORT1);

    assertThat(server1, equalTo(server2));
  }

  @Test
  public void whenHaveDifferentAdminServiceChannels_objectsAreNotEqual() {
    server1.createAdminService().withChannel(CHANNEL1, PORT1).withChannel(CHANNEL2, PORT2);
    server2.createAdminService().withChannel(CHANNEL1, PORT2).withChannel(CHANNEL2, PORT1);

    assertThat(server1, not(equalTo(server2)));
  }

  @Test
  public void whenConstructedWithAdminService_mayAddLabels() {
    configureAdminServer()
        .configureAdminService()
        .withServiceLabel(NAME1, VALUE1)
        .withServiceLabel(NAME2, VALUE2);

    assertThat(
        domain.getAdminServerSpec().getAdminService().getLabels(),
        both(hasEntry(NAME1, VALUE1)).and(hasEntry(NAME2, VALUE2)));
  }

  @Test
  public void whenHaveSameAdminServiceLabels_objectsAreEqual() {
    server1.createAdminService().withServiceLabel(NAME1, VALUE1).withServiceLabel(NAME2, VALUE2);
    server2.createAdminService().withServiceLabel(NAME2, VALUE2).withServiceLabel(NAME1, VALUE1);

    assertThat(server1, equalTo(server2));
  }

  @Test
  public void whenHaveDifferentAdminServiceLabels_objectsAreNotEqual() {
    server1.createAdminService().withServiceLabel(NAME1, VALUE1).withServiceLabel(NAME2, VALUE2);
    server2.createAdminService().withServiceLabel(NAME1, VALUE2).withServiceLabel(NAME2, VALUE1);

    assertThat(server1, not(equalTo(server2)));
  }

  @Test
  public void whenConstructedWithAdminService_mayAddAnnotations() {
    configureAdminServer()
        .configureAdminService()
        .withServiceAnnotation(NAME1, VALUE1)
        .withServiceAnnotation(NAME2, VALUE2);

    assertThat(
        domain.getAdminServerSpec().getAdminService().getAnnotations(),
        both(hasEntry(NAME1, VALUE1)).and(hasEntry(NAME2, VALUE2)));
  }

  @Test
  public void whenHaveSameAdminServiceAnnotations_objectsAreEqual() {
    server1
        .createAdminService()
        .withServiceAnnotation(NAME1, VALUE1)
        .withServiceAnnotation(NAME2, VALUE2);
    server2
        .createAdminService()
        .withServiceAnnotation(NAME2, VALUE2)
        .withServiceAnnotation(NAME1, VALUE1);

    assertThat(server1, equalTo(server2));
  }

  @Test
  public void whenHaveDifferentAdminServiceAnnotations_objectsAreNotEqual() {
    server1
        .createAdminService()
        .withServiceAnnotation(NAME1, VALUE1)
        .withServiceAnnotation(NAME2, VALUE2);
    server2
        .createAdminService()
        .withServiceAnnotation(NAME1, VALUE2)
        .withServiceAnnotation(NAME2, VALUE1);

    assertThat(server1, not(equalTo(server2)));
  }

  @Test
  public void callingGetAdminService_doesNotChangeTheObject() {
    server1.getAdminService();

    assertThat(server1, equalTo(server2));
  }
}

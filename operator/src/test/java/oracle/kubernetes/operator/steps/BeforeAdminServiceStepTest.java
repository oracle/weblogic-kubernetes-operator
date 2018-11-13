// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.steps;

import static oracle.kubernetes.operator.ProcessingConstants.NODE_PORT;
import static oracle.kubernetes.operator.ProcessingConstants.PORT;
import static oracle.kubernetes.operator.ProcessingConstants.SERVER_NAME;
import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.junit.MatcherAssert.assertThat;

import com.meterware.simplestub.Memento;
import io.kubernetes.client.models.V1ObjectMeta;
import java.util.ArrayList;
import java.util.List;
import oracle.kubernetes.TestUtils;
import oracle.kubernetes.operator.helpers.AsyncCallTestSupport;
import oracle.kubernetes.operator.helpers.DomainPresenceInfo;
import oracle.kubernetes.operator.work.Packet;
import oracle.kubernetes.operator.work.Step;
import oracle.kubernetes.operator.work.TerminalStep;
import oracle.kubernetes.weblogic.domain.DomainConfigurator;
import oracle.kubernetes.weblogic.domain.DomainConfiguratorFactory;
import oracle.kubernetes.weblogic.domain.v2.Domain;
import oracle.kubernetes.weblogic.domain.v2.DomainSpec;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class BeforeAdminServiceStepTest {

  private static final String ADMIN_NAME = "admin";
  private static final int ADMIN_PORT_NUM = 3456;
  private static final int NODE_PORT_NUM = 5678;
  private static final String NS = "namespace";
  private static final String UID = "uid1";
  private final Domain domain = createDomain();
  private final DomainConfigurator configurator = DomainConfiguratorFactory.forDomain(domain);
  private Step nextStep = new TerminalStep();
  private List<Memento> mementos = new ArrayList<>();
  private DomainPresenceInfo domainPresenceInfo = createDomainPresenceInfo();
  private AsyncCallTestSupport testSupport = new AsyncCallTestSupport();
  private BeforeAdminServiceStep step = new BeforeAdminServiceStep(nextStep);

  private DomainPresenceInfo createDomainPresenceInfo() {
    return new DomainPresenceInfo(domain);
  }

  private Domain createDomain() {
    return new Domain().withMetadata(createMetaData()).withSpec(createDomainSpec());
  }

  private V1ObjectMeta createMetaData() {
    return new V1ObjectMeta().namespace(NS);
  }

  private DomainSpec createDomainSpec() {
    return new DomainSpec().withDomainUID(UID).withReplicas(1);
  }

  @Before
  public void setUp() throws Exception {
    mementos.add(TestUtils.silenceOperatorLogger());
    mementos.add(testSupport.installRequestStepFactory());
    testSupport.addDomainPresenceInfo(domainPresenceInfo);
    configurator.configureAdminServer(ADMIN_NAME).withPort(ADMIN_PORT_NUM);
  }

  @After
  public void tearDown() throws Exception {
    for (Memento memento : mementos) memento.revert();

    testSupport.throwOnCompletionFailure();
  }

  @Test
  public void afterProcessing_packetContainsAdminServerNameAndPort() {
    Packet packet = invokeStep();

    assertThat(packet, hasEntry(SERVER_NAME, ADMIN_NAME));
    assertThat(packet, hasEntry(PORT, ADMIN_PORT_NUM));
  }

  @Test
  public void whenAdminServerNodePortDefined_packetContainsItAfterProcessing() {
    configurator
        .configureAdminServer(ADMIN_NAME)
        .withPort(ADMIN_PORT_NUM)
        .withNodePort(NODE_PORT_NUM);
    Packet packet = invokeStep();

    assertThat(packet, hasEntry(NODE_PORT, NODE_PORT_NUM));
  }

  @Test
  public void whenAdminServerNodePortNotDefined_packetDoesNotContainItAfterProcessing() {
    Packet packet = invokeStep();

    assertThat(packet, not(hasKey(NODE_PORT)));
  }

  private Packet invokeStep() {
    return testSupport.runSteps(step);
  }
}

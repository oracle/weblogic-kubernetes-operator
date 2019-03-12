// Copyright 2018, 2019, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.steps;

import static oracle.kubernetes.operator.ProcessingConstants.SERVER_NAME;
import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.junit.MatcherAssert.assertThat;

import com.meterware.simplestub.Memento;
import io.kubernetes.client.models.V1ObjectMeta;
import java.util.ArrayList;
import java.util.List;
import oracle.kubernetes.TestUtils;
import oracle.kubernetes.operator.ProcessingConstants;
import oracle.kubernetes.operator.helpers.AsyncCallTestSupport;
import oracle.kubernetes.operator.helpers.DomainPresenceInfo;
import oracle.kubernetes.operator.utils.WlsDomainConfigSupport;
import oracle.kubernetes.operator.work.Packet;
import oracle.kubernetes.operator.work.Step;
import oracle.kubernetes.operator.work.TerminalStep;
import oracle.kubernetes.weblogic.domain.DomainConfigurator;
import oracle.kubernetes.weblogic.domain.DomainConfiguratorFactory;
import oracle.kubernetes.weblogic.domain.model.Domain;
import oracle.kubernetes.weblogic.domain.model.DomainSpec;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class BeforeAdminServiceStepTest {

  private static final String DOMAIN_NAME = "domain";
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
    WlsDomainConfigSupport configSupport = new WlsDomainConfigSupport(DOMAIN_NAME);
    configSupport.addWlsServer(ADMIN_NAME, ADMIN_PORT_NUM);
    configSupport.setAdminServerName(ADMIN_NAME);

    testSupport
        .addToPacket(ProcessingConstants.DOMAIN_TOPOLOGY, configSupport.createDomainConfig())
        .addDomainPresenceInfo(domainPresenceInfo);
    configurator
        .configureAdminServer()
        .configureAdminService()
        .withChannel("default", NODE_PORT_NUM);
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
  }

  private Packet invokeStep() {
    return testSupport.runSteps(step);
  }
}

// Copyright (c) 2018, 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.steps;

import java.util.ArrayList;
import java.util.List;

import com.meterware.simplestub.Memento;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import oracle.kubernetes.operator.ProcessingConstants;
import oracle.kubernetes.operator.helpers.DomainPresenceInfo;
import oracle.kubernetes.operator.utils.WlsDomainConfigSupport;
import oracle.kubernetes.operator.work.FiberTestSupport;
import oracle.kubernetes.operator.work.Packet;
import oracle.kubernetes.operator.work.Step;
import oracle.kubernetes.operator.work.TerminalStep;
import oracle.kubernetes.utils.TestUtils;
import oracle.kubernetes.weblogic.domain.DomainConfigurator;
import oracle.kubernetes.weblogic.domain.DomainConfiguratorFactory;
import oracle.kubernetes.weblogic.domain.model.Domain;
import oracle.kubernetes.weblogic.domain.model.DomainSpec;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static oracle.kubernetes.operator.ProcessingConstants.SERVER_NAME;
import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.junit.MatcherAssert.assertThat;

public class BeforeAdminServiceStepTest {

  private static final String DOMAIN_NAME = "domain";
  private static final String ADMIN_NAME = "admin";
  private static final int ADMIN_PORT_NUM = 3456;
  private static final int NODE_PORT_NUM = 5678;
  private static final String NS = "namespace";
  private static final String UID = "uid1";
  private final Domain domain = createDomain();
  private final DomainConfigurator configurator = DomainConfiguratorFactory.forDomain(domain);
  private final Step nextStep = new TerminalStep();
  private final List<Memento> mementos = new ArrayList<>();
  private final DomainPresenceInfo domainPresenceInfo = createDomainPresenceInfo();
  private final FiberTestSupport testSupport = new FiberTestSupport();
  private final BeforeAdminServiceStep step = new BeforeAdminServiceStep(nextStep);

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
    return new DomainSpec().withDomainUid(UID).withReplicas(1);
  }

  @BeforeEach
  public void setUp()  {
    mementos.add(TestUtils.silenceOperatorLogger());
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

  @AfterEach
  public void tearDown() throws Exception {
    for (Memento memento : mementos) {
      memento.revert();
    }

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

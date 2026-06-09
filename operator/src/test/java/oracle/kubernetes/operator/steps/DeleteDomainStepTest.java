// Copyright (c) 2026, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.steps;

import java.util.ArrayList;
import java.util.List;

import com.meterware.simplestub.Memento;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1Pod;
import oracle.kubernetes.operator.DomainProcessorTestSetup;
import oracle.kubernetes.operator.LabelConstants;
import oracle.kubernetes.operator.helpers.DomainPresenceInfo;
import oracle.kubernetes.operator.helpers.KubernetesTestSupport;
import oracle.kubernetes.operator.work.TerminalStep;
import oracle.kubernetes.weblogic.domain.model.DomainResource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static oracle.kubernetes.operator.DomainProcessorTestSetup.NS;
import static oracle.kubernetes.operator.DomainProcessorTestSetup.UID;
import static oracle.kubernetes.operator.helpers.KubernetesTestSupport.POD;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;

class DeleteDomainStepTest {
  private static final String MANAGED_SERVER = "managed-server1";
  private static final String MANAGED_SERVER_POD = UID + "-" + MANAGED_SERVER;

  private final DomainResource domain = DomainProcessorTestSetup.createTestDomain();
  private final DomainPresenceInfo info = new DomainPresenceInfo(domain);
  private final KubernetesTestSupport testSupport = new KubernetesTestSupport();
  private final List<Memento> mementos = new ArrayList<>();
  private final TerminalStep terminalStep = new TerminalStep();

  @BeforeEach
  void setUp() throws NoSuchFieldException {
    mementos.add(testSupport.install());
    testSupport.addDomainPresenceInfo(info);
  }

  @AfterEach
  void tearDown() {
    mementos.forEach(Memento::revert);
  }

  @Test
  void whenDomainResourceIsNotAvailable_skipGracefulShutdownAndDeletePods() {
    V1Pod managedServerPod = createManagedServerPod();
    info.setDomain(null);
    info.setServerPod(MANAGED_SERVER, managedServerPod);
    testSupport.defineResources(managedServerPod);

    testSupport.runSteps(new DeleteDomainStep(), terminalStep);

    assertThat(terminalStep.wasRun(), is(true));
    assertThat(testSupport.getResourceWithName(POD, MANAGED_SERVER_POD), nullValue());
  }

  private V1Pod createManagedServerPod() {
    return new V1Pod().metadata(new V1ObjectMeta()
        .name(MANAGED_SERVER_POD)
        .namespace(NS)
        .putLabelsItem(LabelConstants.DOMAINUID_LABEL, UID)
        .putLabelsItem(LabelConstants.CREATEDBYOPERATOR_LABEL, "true")
        .putLabelsItem(LabelConstants.SERVERNAME_LABEL, MANAGED_SERVER));
  }
}

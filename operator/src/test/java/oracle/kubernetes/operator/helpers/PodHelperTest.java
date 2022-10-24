// Copyright (c) 2018, 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helpers;

import java.util.ArrayList;
import java.util.List;

import com.meterware.simplestub.Memento;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1Pod;
import io.kubernetes.client.openapi.models.V1PodCondition;
import io.kubernetes.client.openapi.models.V1PodStatus;
import oracle.kubernetes.operator.PodAwaiterStepFactory;
import oracle.kubernetes.operator.calls.UnrecoverableCallException;
import oracle.kubernetes.operator.work.Packet;
import oracle.kubernetes.operator.work.TerminalStep;
import oracle.kubernetes.utils.TestUtils;
import oracle.kubernetes.weblogic.domain.model.DomainResource;
import org.hamcrest.junit.MatcherAssert;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.meterware.simplestub.Stub.createStub;
import static java.net.HttpURLConnection.HTTP_BAD_REQUEST;
import static oracle.kubernetes.operator.helpers.KubernetesTestSupport.POD;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.sameInstance;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class PodHelperTest {
  private static final String UID = "uid1";
  private static final String SERVER_NAME = "server1";
  private static final String POD_NAME = LegalNames.toPodName(UID, SERVER_NAME);
  private static final String NS = "ns1";
  private final TerminalStep terminalStep = new TerminalStep();
  private final KubernetesTestSupport testSupport = new KubernetesTestSupport();
  private final List<Memento> mementos = new ArrayList<>();
  private final DomainPresenceInfo domainPresenceInfo = createDomainPresenceInfo();
  private final V1Pod pod =
      new V1Pod()
          .metadata(
              KubernetesUtils.withOperatorLabels(
                  "uid", new V1ObjectMeta().name(POD_NAME).namespace(NS)));


  private DomainPresenceInfo createDomainPresenceInfo() {
    return new DomainPresenceInfo(new DomainResource().withMetadata(new V1ObjectMeta().namespace(NS)));
  }

  @BeforeEach
  public void setUp() throws NoSuchFieldException {
    mementos.add(TestUtils.silenceOperatorLogger());
    mementos.add(testSupport.install());
    testSupport.addDomainPresenceInfo(domainPresenceInfo);
  }

  @AfterEach
  public void tearDown() throws Exception {
    mementos.forEach(Memento::revert);

    testSupport.throwOnCompletionFailure();
  }

  @Test
  void afterAddingFactoryToPacket_canRetrieveIt() {
    Packet packet = new Packet();
    PodAwaiterStepFactory factory = createStub(PodAwaiterStepFactory.class);
    PodHelper.addToPacket(packet, factory);

    assertThat(PodHelper.getPodAwaiterStepFactory(packet), sameInstance(factory));
  }

  // --- delete pod ---
  // REG: very curious. Deletion uses the namespace from the domain presence info, but the name
  // from the pod (if any) in the SKO.

  @Test
  void afterDeletePodStepRun_markedForDeleteInSko() {
    testSupport.defineResources(pod);
    domainPresenceInfo.setServerPod(SERVER_NAME, pod);

    testSupport.runSteps(PodHelper.deletePodStep(SERVER_NAME, terminalStep));

    MatcherAssert.assertThat(
        domainPresenceInfo.isServerPodBeingDeleted(SERVER_NAME), is(Boolean.TRUE));
  }

  @Test
  void whenDeleteFails_reportCompletionFailure() {
    testSupport.failOnResource(POD, POD_NAME, NS, HTTP_BAD_REQUEST);
    domainPresenceInfo.setServerPod(SERVER_NAME, pod);

    testSupport.runSteps(PodHelper.deletePodStep(SERVER_NAME, terminalStep));

    testSupport.verifyCompletionThrowable(UnrecoverableCallException.class);
  }

  @Test
  void whenDeletePodStepRunWithNoPod_doNotSendDeleteCall() {
    testSupport.runSteps(PodHelper.deletePodStep(SERVER_NAME, terminalStep));

    MatcherAssert.assertThat(domainPresenceInfo.getServerPod(SERVER_NAME), nullValue());
  }

  @Test
  void afterDeletePodStepRun_runSpecifiedNextStep() {
    testSupport.runSteps(PodHelper.deletePodStep(SERVER_NAME, terminalStep));

    MatcherAssert.assertThat(terminalStep.wasRun(), is(true));
  }

  @Test
  void whenInfoHasPodNoDomain_deletePodStepDoesNotThrowException() {
    final DomainPresenceInfo info = new DomainPresenceInfo(NS, UID);
    info.setServerPod(SERVER_NAME, pod);
    testSupport.addDomainPresenceInfo(info);

    assertDoesNotThrow(
        () -> testSupport.runSteps(PodHelper.deletePodStep(SERVER_NAME, terminalStep)));

  }

  @Test
  void whenPodHasNoStatus_isNotReady() {
    assertThat(PodHelper.hasReadyStatus(new V1Pod()), is(false));
  }

  @Test
  void whenPodPhaseNotRunning_isNotReady() {
    V1Pod pod = new V1Pod()
          .status(new V1PodStatus()
                .phase("Pending")
                .addConditionsItem(new V1PodCondition()));

    assertThat(PodHelper.hasReadyStatus(pod), is(false));
  }

  @Test
  void whenPodRunningWithOnlyNonReadyConditions_isNotReady() {
    V1Pod pod = new V1Pod()
          .status(new V1PodStatus()
                .phase("Running")
                .addConditionsItem(new V1PodCondition().type("Initialized")));

    assertThat(PodHelper.hasReadyStatus(pod), is(false));
  }

  @Test
  void whenPodRunningWithOnlyReadyConditionNotTrue_isNotReady() {
    V1Pod pod = new V1Pod()
          .status(new V1PodStatus()
                .phase("Running")
                .addConditionsItem(new V1PodCondition().type("Ready")));

    assertThat(PodHelper.hasReadyStatus(pod), is(false));
  }

  @Test
  void whenPodRunningWithOnlyReadyConditionTrue_isReady() {
    V1Pod pod = new V1Pod()
          .status(new V1PodStatus()
                .phase("Running")
                .addConditionsItem(new V1PodCondition().type("Ready").status("True")));

    assertThat(PodHelper.hasReadyStatus(pod), is(true));
  }

}

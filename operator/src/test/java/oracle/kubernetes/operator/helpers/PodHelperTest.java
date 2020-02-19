// Copyright (c) 2018, 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helpers;

import java.util.ArrayList;
import java.util.List;

import com.meterware.simplestub.Memento;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1Pod;
import oracle.kubernetes.operator.PodAwaiterStepFactory;
import oracle.kubernetes.operator.work.Packet;
import oracle.kubernetes.operator.work.TerminalStep;
import oracle.kubernetes.utils.TestUtils;
import oracle.kubernetes.weblogic.domain.model.Domain;
import org.hamcrest.junit.MatcherAssert;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static com.meterware.simplestub.Stub.createStub;
import static java.net.HttpURLConnection.HTTP_BAD_REQUEST;
import static oracle.kubernetes.operator.helpers.KubernetesTestSupport.POD;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.sameInstance;

public class PodHelperTest {
  private static final String UID = "uid1";
  private static final String SERVER_NAME = "server1";
  private static final String POD_NAME = LegalNames.toPodName(UID, SERVER_NAME);
  private static final String NS = "ns1";
  private final TerminalStep terminalStep = new TerminalStep();
  private KubernetesTestSupport testSupport = new KubernetesTestSupport();
  private List<Memento> mementos = new ArrayList<>();
  private DomainPresenceInfo domainPresenceInfo = createDomainPresenceInfo();
  private V1Pod pod =
      new V1Pod()
          .metadata(
              KubernetesUtils.withOperatorLabels(
                  "uid", new V1ObjectMeta().name(POD_NAME).namespace(NS)));

  private DomainPresenceInfo createDomainPresenceInfo() {
    return new DomainPresenceInfo(new Domain().withMetadata(new V1ObjectMeta().namespace(NS)));
  }

  /**
   * Setup test.
   * @throws NoSuchFieldException on no such field
   */
  @Before
  public void setUp() throws NoSuchFieldException {
    mementos.add(TestUtils.silenceOperatorLogger());
    mementos.add(testSupport.install());
    testSupport.addDomainPresenceInfo(domainPresenceInfo);
  }

  /**
   * Tear down test.
   * @throws Exception on failure
   */
  @After
  public void tearDown() throws Exception {
    for (Memento memento : mementos) {
      memento.revert();
    }

    testSupport.throwOnCompletionFailure();
  }

  @Test
  public void afterAddingFactoryToPacket_canRetrieveIt() {
    Packet packet = new Packet();
    PodAwaiterStepFactory factory = createStub(PodAwaiterStepFactory.class);
    PodHelper.addToPacket(packet, factory);

    assertThat(PodHelper.getPodAwaiterStepFactory(packet), sameInstance(factory));
  }

  // --- delete pod ---
  // REG: very curious. Deletion uses the namespace from the domain presence info, but the name
  // from the pod (if any) in the SKO.

  @Test
  public void afterDeletePodStepRun_markedForDeleteInSko() {
    testSupport.defineResources(pod);
    domainPresenceInfo.setServerPod(SERVER_NAME, pod);

    testSupport.runSteps(PodHelper.deletePodStep(SERVER_NAME, terminalStep));

    MatcherAssert.assertThat(
        domainPresenceInfo.isServerPodBeingDeleted(SERVER_NAME), is(Boolean.TRUE));
  }

  @Test
  public void whenDeleteFails_reportCompletionFailure() {
    testSupport.failOnResource(POD, POD_NAME, NS, HTTP_BAD_REQUEST);
    domainPresenceInfo.setServerPod(SERVER_NAME, pod);

    testSupport.runSteps(PodHelper.deletePodStep(SERVER_NAME, terminalStep));

    testSupport.verifyCompletionThrowable(ApiException.class);
  }

  @Test
  public void whenDeletePodStepRunWithNoPod_doNotSendDeleteCall() {
    testSupport.runSteps(PodHelper.deletePodStep(SERVER_NAME, terminalStep));

    MatcherAssert.assertThat(domainPresenceInfo.getServerPod(SERVER_NAME), nullValue());
  }

  @Test
  public void afterDeletePodStepRun_runSpecifiedNextStep() {
    testSupport.runSteps(PodHelper.deletePodStep(SERVER_NAME, terminalStep));

    MatcherAssert.assertThat(terminalStep.wasRun(), is(true));
  }
}

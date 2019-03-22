// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helpers;

import static com.meterware.simplestub.Stub.createStub;
import static java.net.HttpURLConnection.HTTP_BAD_REQUEST;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.sameInstance;

import com.meterware.simplestub.Memento;
import io.kubernetes.client.ApiException;
import io.kubernetes.client.models.V1DeleteOptions;
import io.kubernetes.client.models.V1ObjectMeta;
import io.kubernetes.client.models.V1Pod;
import io.kubernetes.client.models.V1Status;
import java.net.HttpURLConnection;
import java.util.ArrayList;
import java.util.List;
import oracle.kubernetes.TestUtils;
import oracle.kubernetes.operator.PodAwaiterStepFactory;
import oracle.kubernetes.operator.work.Packet;
import oracle.kubernetes.operator.work.TerminalStep;
import oracle.kubernetes.weblogic.domain.model.Domain;
import org.hamcrest.junit.MatcherAssert;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

@SuppressWarnings("unchecked")
public class PodHelperTest {
  private static final String POD_NAME = "pod1";
  private static final String NS = "ns1";

  private AsyncCallTestSupport testSupport = new AsyncCallTestSupport();
  private final TerminalStep terminalStep = new TerminalStep();
  private List<Memento> mementos = new ArrayList<>();
  private DomainPresenceInfo domainPresenceInfo = createDomainPresenceInfo();

  private DomainPresenceInfo createDomainPresenceInfo() {
    return new DomainPresenceInfo(new Domain().withMetadata(new V1ObjectMeta().namespace(NS)));
  }

  @Before
  public void setUp() throws NoSuchFieldException {
    mementos.add(TestUtils.silenceOperatorLogger());
    mementos.add(testSupport.installRequestStepFactory());
    testSupport.addDomainPresenceInfo(domainPresenceInfo);
  }

  @After
  public void tearDown() throws Exception {
    for (Memento memento : mementos) memento.revert();

    testSupport.throwOnCompletionFailure();
    testSupport.verifyAllDefinedResponsesInvoked();
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
  public void afterDeletePodStepRun_removePodFromSko() {
    expectDeletePodCall().returning(new V1Status());
    ServerKubernetesObjects sko = createSko(createMinimalPod());

    testSupport.runSteps(PodHelper.deletePodStep(sko, terminalStep));

    MatcherAssert.assertThat(sko.getPod().get(), nullValue());
  }

  private CallTestSupport.CannedResponse expectDeletePodCall() {
    return testSupport
        .createCannedResponse("deletePod")
        .withName(POD_NAME)
        .withNamespace(NS)
        .withBody(new V1DeleteOptions());
  }

  private ServerKubernetesObjects createSko(V1Pod pod) {
    ServerKubernetesObjects sko = new ServerKubernetesObjects();
    sko.getPod().set(pod);
    return sko;
  }

  private V1Pod createMinimalPod() {
    return new V1Pod().metadata(new V1ObjectMeta().name(POD_NAME));
  }

  @Test
  public void whenPodNotFound_removePodFromSko() {
    expectDeletePodCall().failingWithStatus(HttpURLConnection.HTTP_NOT_FOUND);
    ServerKubernetesObjects sko = createSko(createMinimalPod());

    testSupport.runSteps(PodHelper.deletePodStep(sko, terminalStep));

    MatcherAssert.assertThat(sko.getPod().get(), nullValue());
  }

  @Test
  public void whenDeleteFails_reportCompletionFailure() {
    expectDeletePodCall().failingWithStatus(HTTP_BAD_REQUEST);
    ServerKubernetesObjects sko = createSko(createMinimalPod());

    testSupport.runSteps(PodHelper.deletePodStep(sko, terminalStep));

    testSupport.verifyCompletionThrowable(ApiException.class);
  }

  @Test
  public void whenDeletePodStepRunWithNoPod_doNotSendDeleteCall() {
    ServerKubernetesObjects sko = createSko(null);

    testSupport.runSteps(PodHelper.deletePodStep(sko, terminalStep));

    MatcherAssert.assertThat(sko.getPod().get(), nullValue());
  }

  @Test
  public void afterDeletePodStepRun_runSpecifiedNextStep() {
    ServerKubernetesObjects sko = createSko(null);

    testSupport.runSteps(PodHelper.deletePodStep(sko, terminalStep));

    MatcherAssert.assertThat(terminalStep.wasRun(), is(true));
  }
}

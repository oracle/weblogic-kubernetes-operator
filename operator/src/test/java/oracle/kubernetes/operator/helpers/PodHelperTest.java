// Copyright (c) 2018, 2026, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helpers;

import java.util.ArrayList;
import java.util.List;

import com.meterware.simplestub.Memento;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.models.V1Container;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1Pod;
import io.kubernetes.client.openapi.models.V1PodCondition;
import io.kubernetes.client.openapi.models.V1PodSpec;
import io.kubernetes.client.openapi.models.V1PodStatus;
import oracle.kubernetes.operator.tuning.TuningParametersStub;
import oracle.kubernetes.operator.work.TerminalStep;
import oracle.kubernetes.utils.TestUtils;
import oracle.kubernetes.weblogic.domain.DomainConfiguratorFactory;
import oracle.kubernetes.weblogic.domain.model.DomainResource;
import org.hamcrest.junit.MatcherAssert;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static java.net.HttpURLConnection.HTTP_BAD_REQUEST;
import static oracle.kubernetes.operator.helpers.KubernetesTestSupport.POD;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
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
  void setUp() throws NoSuchFieldException {
    mementos.add(TestUtils.silenceOperatorLogger());
    mementos.add(TuningParametersStub.install());
    mementos.add(testSupport.install());
    testSupport.addDomainPresenceInfo(domainPresenceInfo);
  }

  @AfterEach
  void tearDown() throws Exception {
    mementos.forEach(Memento::revert);

    testSupport.throwOnCompletionFailure();
  }

  // --- delete pod ---

  @ParameterizedTest
  @ValueSource(strings = {"RUNNING", "SHUTDOWN", "UNKNOWN"})
  void whenNativeSidecarExists_deleteWithLivePodGraceEvenAfterWebLogicShutdown(String serverState) {
    pod.spec(new V1PodSpec().terminationGracePeriodSeconds(90L)
        .addInitContainersItem(new V1Container().name("uploader").restartPolicy("Always")));
    domainPresenceInfo.setServerPod(SERVER_NAME, pod);
    domainPresenceInfo.updateLastKnownServerStatus(SERVER_NAME, serverState);
    testSupport.defineResources(pod);
    testSupport.doOnDelete(POD, context -> assertThat(context.gracePeriodSeconds(), is(90L)));

    testSupport.runSteps(PodHelper.deletePodStep(SERVER_NAME, false, terminalStep));

    assertThat(terminalStep.wasRun(), is(true));
  }

  @Test
  void whenExplicitPodGraceConfigured_preserveGraceForTraditionalSidecarsAfterShutdown() {
    DomainConfiguratorFactory.forDomain(domainPresenceInfo.getDomain()).withTerminationGracePeriodSeconds(120L);
    pod.spec(new V1PodSpec().terminationGracePeriodSeconds(120L));
    domainPresenceInfo.setServerPod(SERVER_NAME, pod);
    domainPresenceInfo.updateLastKnownServerStatus(SERVER_NAME, "SHUTDOWN");
    testSupport.defineResources(pod);
    testSupport.doOnDelete(POD, context -> assertThat(context.gracePeriodSeconds(), is(120L)));

    testSupport.runSteps(PodHelper.deletePodStep(SERVER_NAME, false, terminalStep));
  }

  @Test
  void whenNoNativeSidecarOrExplicitGrace_preserveImmediateDeletionAfterShutdown() {
    domainPresenceInfo.setServerPod(SERVER_NAME, pod);
    domainPresenceInfo.updateLastKnownServerStatus(SERVER_NAME, "SHUTDOWN");
    testSupport.defineResources(pod);
    testSupport.doOnDelete(POD, context -> assertThat(context.gracePeriodSeconds(), is(0L)));

    testSupport.runSteps(PodHelper.deletePodStep(SERVER_NAME, false, terminalStep));
  }

  @Test
  void whenCustomGraceRemovedFromDomain_existingPodStillGetsItsOriginalBudget() {
    pod.spec(new V1PodSpec().terminationGracePeriodSeconds(120L));
    pod.getMetadata().putAnnotationsItem(PodHelper.CUSTOM_TERMINATION_GRACE_PERIOD, "true");
    domainPresenceInfo.setServerPod(SERVER_NAME, pod);
    domainPresenceInfo.updateLastKnownServerStatus(SERVER_NAME, "SHUTDOWN");
    testSupport.defineResources(pod);
    testSupport.doOnDelete(POD, context -> assertThat(context.gracePeriodSeconds(), is(120L)));

    testSupport.runSteps(PodHelper.deletePodStep(SERVER_NAME, false, terminalStep));
  }
  // REG: very curious. Deletion uses the namespace from the domain presence info, but the name
  // from the pod (if any) in the SKO.

  @Test
  void whenDeleteFails_reportCompletionFailure() {
    testSupport.failOnResource(POD, POD_NAME, NS, HTTP_BAD_REQUEST);
    domainPresenceInfo.setServerPod(SERVER_NAME, pod);

    testSupport.runSteps(PodHelper.deletePodStep(SERVER_NAME, false, terminalStep));

    testSupport.verifyCompletionThrowable(ApiException.class);
  }

  @Test
  void whenDeletePodStepRunWithNoPod_doNotSendDeleteCall() {
    testSupport.runSteps(PodHelper.deletePodStep(SERVER_NAME, false, terminalStep));

    MatcherAssert.assertThat(domainPresenceInfo.getServerPod(SERVER_NAME), nullValue());
  }

  @Test
  void afterDeletePodStepRun_runSpecifiedNextStep() {
    testSupport.runSteps(PodHelper.deletePodStep(SERVER_NAME, false, terminalStep));

    MatcherAssert.assertThat(terminalStep.wasRun(), is(true));
  }

  @Test
  void whenInfoHasPodNoDomain_deletePodStepDoesNotThrowException() {
    final DomainPresenceInfo info = new DomainPresenceInfo(NS, UID);
    info.setServerPod(SERVER_NAME, pod);
    testSupport.addDomainPresenceInfo(info);

    assertDoesNotThrow(
        () -> testSupport.runSteps(PodHelper.deletePodStep(SERVER_NAME, false, terminalStep)));

  }

  @Test
  void whenPodHasNoStatus_isNotReady() {
    assertThat(PodHelper.hasReadyStatus(new V1Pod()), is(false));
  }

  @Test
  void whenPodPhaseNotRunning_isNotReady() {
    V1Pod serverPod = new V1Pod()
          .status(new V1PodStatus()
                .phase("Pending")
                .addConditionsItem(new V1PodCondition()));

    assertThat(PodHelper.hasReadyStatus(serverPod), is(false));
  }

  @Test
  void whenPodRunningWithOnlyNonReadyConditions_isNotReady() {
    V1Pod serverPod = new V1Pod()
          .status(new V1PodStatus()
                .phase("Running")
                .addConditionsItem(new V1PodCondition().type("Initialized")));

    assertThat(PodHelper.hasReadyStatus(serverPod), is(false));
  }

  @Test
  void whenPodRunningWithOnlyReadyConditionNotTrue_isNotReady() {
    V1Pod serverPod = new V1Pod()
          .status(new V1PodStatus()
                .phase("Running")
                .addConditionsItem(new V1PodCondition().type("Ready")));

    assertThat(PodHelper.hasReadyStatus(serverPod), is(false));
  }

  @Test
  void whenPodRunningWithOnlyReadyConditionTrue_isReady() {
    V1Pod serverPod = new V1Pod()
          .status(new V1PodStatus()
                .phase("Running")
                .addConditionsItem(new V1PodCondition().type("Ready").status("True")));

    assertThat(PodHelper.hasReadyStatus(serverPod), is(true));
  }

}

// Copyright (c) 2023, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import com.meterware.simplestub.Memento;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1PersistentVolumeClaim;
import io.kubernetes.client.openapi.models.V1PersistentVolumeClaimStatus;
import oracle.kubernetes.operator.helpers.DomainPresenceInfo;
import oracle.kubernetes.operator.helpers.KubernetesTestSupport;
import oracle.kubernetes.operator.work.Step;
import oracle.kubernetes.operator.work.TerminalStep;
import oracle.kubernetes.utils.SystemClock;
import oracle.kubernetes.weblogic.domain.model.DomainCondition;
import oracle.kubernetes.weblogic.domain.model.DomainConditionType;
import oracle.kubernetes.weblogic.domain.model.DomainResource;
import oracle.kubernetes.weblogic.domain.model.DomainSpec;
import oracle.kubernetes.weblogic.domain.model.DomainStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static oracle.kubernetes.operator.DomainProcessorTestSetup.NS;
import static oracle.kubernetes.operator.DomainProcessorTestSetup.UID;
import static oracle.kubernetes.operator.ProcessingConstants.BOUND;
import static oracle.kubernetes.operator.ProcessingConstants.PENDING;
import static oracle.kubernetes.weblogic.domain.model.DomainFailureReason.PERSISTENT_VOLUME_CLAIM;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.junit.MatcherAssert.assertThat;

/** This test class verifies the behavior of the PvcWatcher. */
class PvcWatcherTest {

  private final V1PersistentVolumeClaim cachedPvc = createPvc();
  private OffsetDateTime clock = SystemClock.now();
  private static final String LATEST_IMAGE = "image:latest";

  private final KubernetesTestSupport testSupport = new KubernetesTestSupport();
  private final TerminalStep terminalStep = new TerminalStep();
  private final DomainResource domain = createDomain();
  private final DomainPresenceInfo domainPresenceInfo = createDomainPresenceInfo(domain);
  final List<Memento> mementos = new ArrayList<>();

  @BeforeEach
  public void setUp() throws Exception {
    mementos.add(testSupport.install());
    testSupport.addDomainPresenceInfo(domainPresenceInfo);
  }

  @AfterEach
  public void tearDown() throws Exception {
    mementos.forEach(Memento::revert);
    testSupport.throwOnCompletionFailure();
  }

  private DomainPresenceInfo createDomainPresenceInfo(
      DomainResource domain) {
    DomainPresenceInfo dpi = new DomainPresenceInfo(domain);
    return dpi;
  }

  private DomainResource createDomain() {
    return new DomainResource()
        .withMetadata(new V1ObjectMeta().name(UID).namespace(NS))
        .withSpec(createDomainSpec());
  }

  private DomainSpec createDomainSpec() {
    DomainSpec spec =
        new DomainSpec()
            .withDomainUid(UID)
            .withImage(LATEST_IMAGE)
            .withDomainHomeSourceType(DomainSourceType.PERSISTENT_VOLUME);
    spec.setServerStartPolicy(ServerStartPolicy.IF_NEEDED);

    return spec;
  }

  private V1PersistentVolumeClaim createPvc() {
    return new V1PersistentVolumeClaim().metadata(new V1ObjectMeta().name("test").creationTimestamp(getCurrentTime()));
  }

  private OffsetDateTime getCurrentTime() {
    return clock;
  }

  protected PvcWatcher createWatcher() {
    return new PvcWatcher(new DomainProcessorImpl(DomainProcessorDelegateStub.createDelegate(testSupport)));
  }

  @Test
  void whenPvcHasNoStatus_reportNotBound() {
    assertThat(PvcWatcher.isBound(cachedPvc), is(false));
  }

  @Test
  void whenPvcHasNoPhase_reportNotBound() {
    cachedPvc.status(new V1PersistentVolumeClaimStatus());

    assertThat(PvcWatcher.isBound(cachedPvc), is(false));
  }

  @Test
  void whenPvcPhaseIsPending_reportNotBound() {
    cachedPvc.status(new V1PersistentVolumeClaimStatus().phase(PENDING));

    assertThat(PvcWatcher.isBound(cachedPvc), is(false));
  }

  @Test
  void waitForReady_returnsAStep() {
    PvcWatcher watcher = createWatcher();

    assertThat(watcher.waitForReady(cachedPvc, null), instanceOf(Step.class));
  }

  @Test
  void whenWaitForReadyAppliedToBoundPvc_performNextStep() {
    startWaitForReady(this::markPvcBound);

    assertThat(terminalStep.wasRun(), is(true));
  }

  private V1PersistentVolumeClaim markPvcBound(V1PersistentVolumeClaim pvc) {
    return pvc.status(new V1PersistentVolumeClaimStatus().phase(BOUND));
  }

  private V1PersistentVolumeClaim dontChangePvc(V1PersistentVolumeClaim pvc) {
    return pvc;
  }

  @Test
  void whenWaitForReadyAppliedToUnboundPvc_dontPerformNextStep() {
    startWaitForReady(this::dontChangePvc);

    assertThat(terminalStep.wasRun(), is(false));
  }

  @Test
  void whenWaitForReadyAppliedToPendingPvc_performNextStep() {
    startWaitForReady(this::markPvcPending);

    assertThat(terminalStep.wasRun(), is(false));
  }

  private V1PersistentVolumeClaim markPvcPending(V1PersistentVolumeClaim pvc) {
    return pvc.status(new V1PersistentVolumeClaimStatus().phase(PENDING));
  }

  // Starts the waitForReady step with pvc modified as needed
  private void startWaitForReady(Function<V1PersistentVolumeClaim,V1PersistentVolumeClaim> pvcFunction) {
    PvcWatcher watcher = createWatcher();
    V1PersistentVolumeClaim cachedPvc = pvcFunction.apply(createPvc());

    testSupport.runSteps(watcher.waitForReady(cachedPvc, terminalStep));
  }

  @Test
  void whenPvcBoundOnFirstRead_performNextStep() {
    testSupport.defineResources(createDomain());
    startWaitForReadyThenReadPvc(this::markPvcBound);

    assertThat(terminalStep.wasRun(), is(true));
  }

  @Test
  void whenPvcBoundErrorResolved_performNextStep() {
    DomainResource failedDomain = createFailedDomain();
    testSupport.defineResources(failedDomain);
    domainPresenceInfo.setDomain(failedDomain);
    startWaitForReadyThenReadPvc(this::markPvcBound);

    assertThat(terminalStep.wasRun(), is(true));
  }

  private DomainResource createFailedDomain() {
    return createDomain().withStatus(new DomainStatus().addCondition(new DomainCondition(
        DomainConditionType.FAILED).withReason(PERSISTENT_VOLUME_CLAIM)));
  }

  @Test
  void whenPvcUnboundOnFirstRead_dontPerformNextStep() {
    testSupport.defineResources(createDomain());
    startWaitForReadyThenReadPvc(this::dontChangePvc);

    assertThat(terminalStep.wasRun(), is(false));
  }

  @Test
  void whenPvcPendingOnFirstRead_dontPerformNextStep() {
    testSupport.defineResources(createDomain());
    startWaitForReadyThenReadPvc(this::markPvcPending);

    assertThat(terminalStep.wasRun(), is(false));
  }

  // Starts the waitForReady step with an incomplete pvc cached, but a modified one in kubernetes
  private void startWaitForReadyThenReadPvc(Function<V1PersistentVolumeClaim,V1PersistentVolumeClaim> pvcFunction) {
    startWaitForReadyThenReadPvc(pvcFunction, terminalStep);
  }

  private void startWaitForReadyThenReadPvc(Function<V1PersistentVolumeClaim,V1PersistentVolumeClaim> pvcFunction,
                                            Step nextStep) {
    PvcWatcher watcher = createWatcher();

    V1PersistentVolumeClaim persistedPvc = pvcFunction.apply(createPvc());
    testSupport.defineResources(persistedPvc);

    testSupport.runSteps(watcher.waitForReady(cachedPvc, nextStep));
  }
}

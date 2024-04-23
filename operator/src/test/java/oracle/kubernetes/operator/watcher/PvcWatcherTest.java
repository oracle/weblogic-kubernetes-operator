// Copyright (c) 2023, 2024, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.watcher;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

import com.meterware.simplestub.Memento;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1PersistentVolumeClaim;
import io.kubernetes.client.openapi.models.V1PersistentVolumeClaimStatus;
import oracle.kubernetes.operator.DomainProcessorDelegateStub;
import oracle.kubernetes.operator.DomainProcessorImpl;
import oracle.kubernetes.operator.DomainSourceType;
import oracle.kubernetes.operator.ServerStartPolicy;
import oracle.kubernetes.operator.helpers.DomainPresenceInfo;
import oracle.kubernetes.operator.helpers.KubernetesTestSupport;
import oracle.kubernetes.operator.tuning.TuningParametersStub;
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
import static org.hamcrest.Matchers.is;
import static org.hamcrest.junit.MatcherAssert.assertThat;

/** This test class verifies the behavior of the PvcWatcher. */
class PvcWatcherTest {

  private final V1PersistentVolumeClaim cachedPvc = createPvc();
  private final OffsetDateTime clock = SystemClock.now();
  private static final String LATEST_IMAGE = "image:latest";

  private final KubernetesTestSupport testSupport = new KubernetesTestSupport();
  private final TerminalStep terminalStep = new TerminalStep();
  private final DomainResource domain = createDomain();
  private final DomainPresenceInfo domainPresenceInfo = createDomainPresenceInfo(domain);
  final List<Memento> mementos = new ArrayList<>();

  @BeforeEach
  public void setUp() throws Exception {
    mementos.add(testSupport.install());
    mementos.add(TuningParametersStub.install());
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
    return new V1PersistentVolumeClaim().metadata(new V1ObjectMeta().name("test")
        .namespace(NS).creationTimestamp(getCurrentTime()));
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

  private V1PersistentVolumeClaim markPvcBound(V1PersistentVolumeClaim pvc) {
    return pvc.status(new V1PersistentVolumeClaimStatus().phase(BOUND));
  }

  private V1PersistentVolumeClaim dontChangePvc(V1PersistentVolumeClaim pvc) {
    return pvc;
  }

  private V1PersistentVolumeClaim markPvcPending(V1PersistentVolumeClaim pvc) {
    return pvc.status(new V1PersistentVolumeClaimStatus().phase(PENDING));
  }

  private DomainResource createFailedDomain() {
    return createDomain().withStatus(new DomainStatus().addCondition(new DomainCondition(
        DomainConditionType.FAILED).withReason(PERSISTENT_VOLUME_CLAIM)));
  }
}

// Copyright (c) 2019, 2023, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helpers;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

import com.google.common.collect.ImmutableMap;
import com.meterware.simplestub.Memento;
import com.meterware.simplestub.StaticStubSupport;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1Pod;
import io.kubernetes.client.openapi.models.V1PodCondition;
import io.kubernetes.client.openapi.models.V1PodStatus;
import io.kubernetes.client.util.Watch;
import oracle.kubernetes.operator.DomainProcessorDelegateStub;
import oracle.kubernetes.operator.DomainProcessorImpl;
import oracle.kubernetes.operator.DomainProcessorTestSetup;
import oracle.kubernetes.operator.builders.WatchEvent;
import oracle.kubernetes.operator.http.rest.ScanCacheStub;
import oracle.kubernetes.operator.introspection.IntrospectionTestUtils;
import oracle.kubernetes.operator.tuning.TuningParametersStub;
import oracle.kubernetes.operator.utils.InMemoryCertificates;
import oracle.kubernetes.operator.utils.WlsDomainConfigSupport;
import oracle.kubernetes.operator.work.Packet;
import oracle.kubernetes.utils.SystemClock;
import oracle.kubernetes.utils.SystemClockTestSupport;
import oracle.kubernetes.utils.TestUtils;
import oracle.kubernetes.weblogic.domain.model.DomainResource;
import org.hamcrest.junit.MatcherAssert;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static oracle.kubernetes.operator.DomainProcessorTestSetup.NS;
import static oracle.kubernetes.operator.DomainProcessorTestSetup.UID;
import static oracle.kubernetes.operator.KubernetesConstants.EVICTED_REASON;
import static oracle.kubernetes.operator.LabelConstants.DOMAINUID_LABEL;
import static oracle.kubernetes.operator.LabelConstants.SERVERNAME_LABEL;
import static oracle.kubernetes.operator.ProcessingConstants.CLUSTER_NAME;
import static oracle.kubernetes.operator.ProcessingConstants.DOMAIN_TOPOLOGY;
import static oracle.kubernetes.operator.ProcessingConstants.SERVER_NAME;
import static oracle.kubernetes.operator.ProcessingConstants.SERVER_SCAN;
import static oracle.kubernetes.operator.WebLogicConstants.RUNNING_STATE;
import static oracle.kubernetes.operator.WebLogicConstants.SHUTDOWN_STATE;
import static oracle.kubernetes.operator.WebLogicConstants.SUSPENDING_STATE;
import static oracle.kubernetes.operator.helpers.KubernetesTestSupport.POD;
import static org.hamcrest.CoreMatchers.hasItem;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.sameInstance;
import static org.hamcrest.junit.MatcherAssert.assertThat;

class PodPresenceTest {

  private static final String CLUSTER = "cluster1";
  private static final String SERVER = "server1";
  private static final String POD_NAME = SERVER;
  private static final String RESOURCE_VERSION = "1233489";
  private static final String ADMIN_SERVER_NAME = "admin";
  private final DomainPresenceInfo info = new DomainPresenceInfo(NS, UID);
  private final List<Memento> mementos = new ArrayList<>();
  private final Map<String, Map<String, DomainPresenceInfo>> domains = new HashMap<>();
  private final KubernetesTestSupport testSupport = new KubernetesTestSupport();
  private final DomainProcessorDelegateStub processorDelegate = DomainProcessorDelegateStub.createDelegate(testSupport);
  private final DomainProcessorImpl processor = new DomainProcessorImpl(processorDelegate);
  private final Packet packet = new Packet();
  private final V1Pod pod =
      new V1Pod()
          .metadata(
              KubernetesUtils.withOperatorLabels(
                  "uid", new V1ObjectMeta().name(POD_NAME).namespace(NS)));
  private int numPodsDeleted;

  @BeforeEach
  public void setUp() throws Exception {
    mementos.add(TestUtils.silenceOperatorLogger());
    mementos.add(testSupport.install());
    mementos.add(StaticStubSupport.install(DomainProcessorImpl.class, "domains", domains));
    mementos.add(TuningParametersStub.install());
    mementos.add(InMemoryCertificates.install());
    mementos.add(ConstantTestHash.install());
    mementos.add(ScanCacheStub.install());
    mementos.add(SystemClockTestSupport.installClock());

    HashMap<String, DomainPresenceInfo> infoMap = new HashMap<>();
    infoMap.put(UID, info);
    domains.put(NS, infoMap);
    disableDomainProcessing();
    DomainResource domain = DomainProcessorTestSetup.createTestDomain();
    DomainProcessorTestSetup.defineRequiredResources(testSupport);
    testSupport.defineResources(domain);
    info.setDomain(domain);

    WlsDomainConfigSupport configSupport =
        new WlsDomainConfigSupport(UID).withAdminServerName(ADMIN_SERVER_NAME);
    configSupport.addWlsServer("admin", 8001);
    configSupport.addWlsServer(SERVER, 7001);
    IntrospectionTestUtils.defineIntrospectionTopology(testSupport, configSupport.createDomainConfig());

    packet.put(DOMAIN_TOPOLOGY, configSupport.createDomainConfig());
    packet.put(CLUSTER_NAME, CLUSTER);
    packet.put(SERVER_NAME, SERVER);
    packet.put(SERVER_SCAN, configSupport.createDomainConfig().getServerConfig(SERVER));
    packet.with(processorDelegate).with(info);

    numPodsDeleted = 0;
  }

  private void disableDomainProcessing() {
    info.setDeleting(true);
  }

  private void enableDomainProcessing() {
    info.setDeleting(false);
  }

  @AfterEach
  public void tearDown() {
    mementos.forEach(Memento::revert);
  }

  @Test
  void whenPodHasNoStatus_reportNotReady() {
    MatcherAssert.assertThat(PodHelper.isReady(pod), is(false));
  }

  @Test
  void whenPodPhaseNotRunning_reportNotReady() {
    pod.status(new V1PodStatus());

    MatcherAssert.assertThat(PodHelper.isReady(pod), is(false));
  }

  @Test
  void whenPodRunningButNoConditionsDefined_reportNotReady() {
    pod.status(new V1PodStatus().phase("Running"));

    MatcherAssert.assertThat(PodHelper.isReady(pod), is(false));
  }

  @Test
  void whenPodRunningButNoReadyConditionsDefined_reportNotReady() {
    List<V1PodCondition> conditions = Collections.singletonList(
        new V1PodCondition().type("Initialized"));
    pod.status(new V1PodStatus().phase("Running").conditions(conditions));

    MatcherAssert.assertThat(PodHelper.isReady(pod), is(false));
  }

  @Test
  void whenPodRunningButReadyConditionIsNotTrue_reportNotReady() {
    List<V1PodCondition> conditions =
        Collections.singletonList(new V1PodCondition().type("Ready").status("False"));
    pod.status(new V1PodStatus().phase("Running").conditions(conditions));

    MatcherAssert.assertThat(PodHelper.isReady(pod), is(false));
  }

  @Test
  void whenPodRunningAndReadyConditionIsTrue_reportReady() {
    makePodReady(pod);

    MatcherAssert.assertThat(PodHelper.isReady(pod), is(true));
  }

  private void makePodReady(V1Pod pod) {
    List<V1PodCondition> conditions =
        Collections.singletonList(new V1PodCondition().type("Ready").status("True"));
    pod.status(new V1PodStatus().phase("Running").conditions(conditions));
  }

  @Test
  void whenPodHasNoStatus_reportNotFailed() {
    MatcherAssert.assertThat(PodHelper.isFailed(pod), is(false));
  }

  @Test
  void whenPodPhaseRunning_reportNotFailed() {
    pod.status(new V1PodStatus().phase("Running"));

    MatcherAssert.assertThat(PodHelper.isFailed(pod), is(false));
  }

  @Test
  void whenPodPhasePending_reportNotFailed() {
    pod.status(new V1PodStatus().phase("Pending"));

    MatcherAssert.assertThat(PodHelper.isFailed(pod), is(false));
  }

  @Test
  void whenPodPhasePending_reportPending() {
    pod.status(new V1PodStatus().phase("Pending"));

    MatcherAssert.assertThat(PodHelper.isPending(pod), is(true));
  }

  @Test
  void whenPodPhaseIsFailed_reportFailed() {
    pod.status(new V1PodStatus().phase("Failed"));

    MatcherAssert.assertThat(PodHelper.isFailed(pod), is(true));
  }

  @Test
  void whenPodPhaseIsFailedAndReasonIsEvicted_reportEvicted() {
    pod.status(new V1PodStatus().phase("Failed").reason(EVICTED_REASON));

    MatcherAssert.assertThat(PodHelper.isEvicted(pod), is(true));
  }

  @Test
  void whenPodHasNoStatus_reportNotEvicted() {
    MatcherAssert.assertThat(PodHelper.isEvicted(pod), is(false));
  }

  @Test
  void whenPodPhaseIsFailedAndReasonIsNotEvicted_reportNotEvicted() {
    pod.status(new V1PodStatus().phase("Failed"));

    MatcherAssert.assertThat(PodHelper.isEvicted(pod), is(false));
  }

  @SuppressWarnings("ConstantConditions")
  @Test
  void whenPodHasNoDomainUid_returnNull() {
    pod.getMetadata().getLabels().remove(DOMAINUID_LABEL);
    MatcherAssert.assertThat(PodHelper.getPodDomainUid(pod), nullValue());
  }

  @SuppressWarnings("ConstantConditions")
  @Test
  void whenPodHasDomainUid_returnIt() {
    pod.getMetadata().labels(ImmutableMap.of(DOMAINUID_LABEL, "domain1"));

    MatcherAssert.assertThat(PodHelper.getPodDomainUid(pod), equalTo("domain1"));
  }

  @Test
  void whenPodHasNoServerName_returnNull() {
    MatcherAssert.assertThat(PodHelper.getPodServerName(pod), nullValue());
  }

  @SuppressWarnings("ConstantConditions")
  @Test
  void whenPodHasServerName_returnIt() {
    pod.getMetadata().labels(ImmutableMap.of(SERVERNAME_LABEL, "myserver"));

    MatcherAssert.assertThat(PodHelper.getPodServerName(pod), equalTo("myserver"));
  }

  // tests for updating DomainPresenceInfo's service fields.
  // all assume that a watcher event identifies a service in an existing domain.
  // the timestamps for services increment with each creation

  @Test
  void onAddEventWithNoRecordedServerPod_addIt() {
    V1Pod newPod = createServerPod();
    Watch.Response<V1Pod> event = WatchEvent.createAddedEvent(newPod).toWatchResponse();

    processor.dispatchPodWatch(event);

    assertThat(info.getServerPod(SERVER), sameInstance(newPod));
  }

  @Test
  void onAddEventWithNoRecordedServerPodButDomainUidLabelMissing_dontAddIt() {
    V1Pod newPod = createServerPod();
    newPod.getMetadata().getLabels().remove(DOMAINUID_LABEL);
    Watch.Response<V1Pod> event = WatchEvent.createAddedEvent(newPod).toWatchResponse();

    processor.dispatchPodWatch(event);

    assertThat(info.getServerPod(SERVER), equalTo(null));
  }

  @Test
  void onAddEventWithNoRecordedServerPodButInfoMissing_dontAddIt() {
    domains.get(NS).remove(UID);
    V1Pod newPod = createServerPod();
    Watch.Response<V1Pod> event = WatchEvent.createAddedEvent(newPod).toWatchResponse();

    processor.dispatchPodWatch(event);

    assertThat(info.getServerPod(SERVER), equalTo(null));
  }

  @Test
  void onAddEventWithNewerServerPod_replaceCurrentValue() {
    V1Pod currentPod = createServerPod();
    V1Pod newerPod = createServerPod();
    info.setServerPod(SERVER, currentPod);
    Watch.Response<V1Pod> event = WatchEvent.createAddedEvent(newerPod).toWatchResponse();

    processor.dispatchPodWatch(event);

    assertThat(info.getServerPod(SERVER), sameInstance(newerPod));
  }

  @Test
  void onAddEventWithOlderServerPod_keepCurrentValue() {
    V1Pod olderPod = createServerPod();
    V1Pod currentPod = createServerPod();
    info.setServerPod(SERVER, currentPod);
    Watch.Response<V1Pod> event = WatchEvent.createAddedEvent(olderPod).toWatchResponse();

    processor.dispatchPodWatch(event);

    assertThat(info.getServerPod(SERVER), sameInstance(currentPod));
  }

  @Test
  void onModifyEventWithNoRecordedServerPod_addIt() {
    V1Pod pod = createServerPod();
    Watch.Response<V1Pod> event = WatchEvent.createModifiedEvent(pod).toWatchResponse();

    processor.dispatchPodWatch(event);

    assertThat(info.getServerPod(SERVER), sameInstance(pod));
  }

  @Test
  void onModifyEventWithNewerServerPod_replaceCurrentValue() {
    V1Pod currentPod = createServerPod();
    V1Pod newPod = createServerPod();
    info.setServerPod(SERVER, currentPod);
    Watch.Response<V1Pod> event = WatchEvent.createModifiedEvent(newPod).toWatchResponse();

    processor.dispatchPodWatch(event);

    assertThat(info.getServerPod(SERVER), sameInstance(newPod));
  }

  @Test
  void onModifyEventWithOlderServerPod_keepCurrentValue() {
    V1Pod olderPod = createServerPod();
    V1Pod currentPod = createServerPod();
    info.setServerPod(SERVER, currentPod);
    Watch.Response<V1Pod> event = WatchEvent.createModifiedEvent(olderPod).toWatchResponse();

    processor.dispatchPodWatch(event);

    assertThat(info.getServerPod(SERVER), sameInstance(currentPod));
  }

  @Test
  void onModifyEventWithPodNotReadyAndOldStatusRunning_setLastKnownStatusNull() {
    V1Pod eventPod = createServerPod();
    V1Pod currentPod = createServerPod();
    info.updateLastKnownServerStatus(SERVER, RUNNING_STATE);
    info.setServerPod(SERVER, currentPod);
    Watch.Response<V1Pod> event = WatchEvent.createModifiedEvent(eventPod).toWatchResponse();

    processor.dispatchPodWatch(event);

    assertThat(info.getLastKnownServerStatus(SERVER), nullValue());
  }

  @Test
  void onModifyEventWithPodNotReadyAndOldStatusNotRunning_dontChangeIt() {
    V1Pod eventPod = createServerPod();
    V1Pod currentPod = createServerPod();
    info.updateLastKnownServerStatus(SERVER, SUSPENDING_STATE);
    info.setServerPod(SERVER, currentPod);
    Watch.Response<V1Pod> event = WatchEvent.createModifiedEvent(eventPod).toWatchResponse();

    processor.dispatchPodWatch(event);

    assertThat(info.getLastKnownServerStatus(SERVER).getStatus(), equalTo(SUSPENDING_STATE));
  }

  @Test
  void onModifyEventWithPodReady_setLastKnownStatusRunning() {
    V1Pod eventPod = createServerPod();
    V1Pod currentPod = createServerPod();
    makePodReady(eventPod);
    info.setServerPod(SERVER, currentPod);
    Watch.Response<V1Pod> event = WatchEvent.createModifiedEvent(eventPod).toWatchResponse();

    processor.dispatchPodWatch(event);

    assertThat(info.getLastKnownServerStatus(SERVER).getStatus(), equalTo(RUNNING_STATE));
  }

  @Test
  void onDeleteEventWithNoRecordedServerPod_ignoreIt() {
    V1Pod service = createServerPod();
    Watch.Response<V1Pod> event = WatchEvent.createDeletedEvent(service).toWatchResponse();

    processor.dispatchPodWatch(event);

    assertThat(info.getServerPod(SERVER), nullValue());
  }

  @Test
  void onDeleteEventWithInfoNotDeletingInfoHasMissingDomain_ignoreIt() {
    V1Pod service = createServerPod();
    info.setServerPod(SERVER, service);
    info.setDeleting(false);
    info.setDomain(null);
    Watch.Response<V1Pod> event = WatchEvent.createDeletedEvent(service).toWatchResponse();

    processor.dispatchPodWatch(event);

    assertThat(info.getServerPod(SERVER), nullValue());
  }

  @Test
  void onDeleteEventWithInfoDeleting_ignoreIt() {
    V1Pod service = createServerPod();
    info.setServerPod(SERVER, service);
    info.setDeleting(true);
    Watch.Response<V1Pod> event = WatchEvent.createDeletedEvent(service).toWatchResponse();

    processor.dispatchPodWatch(event);

    assertThat(info.getServerPod(SERVER), nullValue());
  }

  @Test
  void onDeleteEventWithInfoDeletingInfoHasMissingDomain_ignoreIt() {
    V1Pod service = createServerPod();
    info.setServerPod(SERVER, service);
    info.setDeleting(true);
    info.setDomain(null);
    Watch.Response<V1Pod> event = WatchEvent.createDeletedEvent(service).toWatchResponse();

    processor.dispatchPodWatch(event);

    assertThat(info.getServerPod(SERVER), nullValue());
  }

  @Test
  void onDeleteEventWithOlderServerPod_keepCurrentValue() {
    V1Pod oldPod = createServerPod();
    V1Pod currentPod = createServerPod();
    info.setServerPod(SERVER, currentPod);
    Watch.Response<V1Pod> event = WatchEvent.createDeletedEvent(oldPod).toWatchResponse();

    processor.dispatchPodWatch(event);

    assertThat(info.getServerPod(SERVER), sameInstance(currentPod));
  }

  @Test
  void onDeleteEventWithSameServerPod_removeIt() {
    V1Pod currentPod = createServerPod();
    info.setServerPod(SERVER, currentPod);
    Watch.Response<V1Pod> event = WatchEvent.createDeletedEvent(currentPod).toWatchResponse();

    processor.dispatchPodWatch(event);

    assertThat(info.getServerPod(SERVER), nullValue());
  }

  @Test
  void onDeleteEventWithNewerServerPod_removeIt() {
    V1Pod currentPod = createServerPod();
    V1Pod newerPod = createServerPod();
    info.setServerPod(SERVER, currentPod);
    Watch.Response<V1Pod> event = WatchEvent.createDeletedEvent(newerPod).toWatchResponse();

    processor.dispatchPodWatch(event);

    assertThat(info.getServerPod(SERVER), nullValue());
  }

  @Test
  void afterDeleteEvent_setLastKnownStatus_Shutdown() {
    V1Pod currentPod = createServerPod();
    V1Pod newerPod = createServerPod();
    info.setServerPod(SERVER, currentPod);
    Watch.Response<V1Pod> event = WatchEvent.createDeletedEvent(newerPod).toWatchResponse();

    processor.dispatchPodWatch(event);

    assertThat(info.getLastKnownServerStatus(SERVER).getStatus(), equalTo(SHUTDOWN_STATE));
  }

  @Test
  void afterDeleteEvent_restoreRequiredPod() {
    enableDomainProcessing();
    V1Pod currentPod = createServerPod();
    V1Pod newerPod = createServerPod();
    info.setServerPod(SERVER, currentPod);
    Watch.Response<V1Pod> event = WatchEvent.createDeletedEvent(newerPod).toWatchResponse();

    processor.dispatchPodWatch(event);

    assertThat(info.getServerPod(SERVER), notNullValue());
  }

  @Test
  void onModifyEventWithEvictedServerPod_cycleServerPod() {
    V1Pod currentPod = createServerPod();
    V1Pod modifiedPod = withEvictedStatus(createServerPod());
    List<String> createdPodNames = new ArrayList<>();
    testSupport.doOnCreate(POD, p -> recordPodCreation((V1Pod) p, createdPodNames));
    testSupport.doOnDelete(POD, this::recordPodDeletion);

    info.setServerPod(SERVER, currentPod);
    Watch.Response<V1Pod> event = WatchEvent.createModifiedEvent(modifiedPod).toWatchResponse();

    processor.dispatchPodWatch(event);

    assertThat(numPodsDeleted, is(1));
    assertThat(createdPodNames, hasItem(SERVER));
  }
  
  @Test
  void onModifyEventWithEvictedServerPod_notCycleServerPod_ifConfiguredNotTo() {
    TuningParametersStub.setParameter("restartEvictedPods", "false");
    V1Pod currentPod = createServerPod();
    V1Pod modifiedPod = withEvictedStatus(createServerPod());
    List<String> createdPodNames = new ArrayList<>();
    testSupport.doOnCreate(POD, p -> recordPodCreation((V1Pod) p, createdPodNames));
    testSupport.doOnDelete(POD, this::recordPodDeletion);

    info.setServerPod(SERVER, currentPod);
    Watch.Response<V1Pod> event = WatchEvent.createModifiedEvent(modifiedPod).toWatchResponse();

    processor.dispatchPodWatch(event);

    assertThat(numPodsDeleted, is(0));
    assertThat(createdPodNames, not(hasItem(SERVER)));
  }

  @Test
  void onModifyEventWithEvictedAdminServerPod_cycleServerPod() {
    V1Pod currentPod = createAdminServerPod();
    V1Pod modifiedPod = withEvictedStatus(createAdminServerPod());
    List<String> createdPodNames = new ArrayList<>();
    testSupport.doOnCreate(POD, p -> recordPodCreation((V1Pod) p, createdPodNames));
    testSupport.doOnDelete(POD, this::recordPodDeletion);

    packet.put(SERVER_NAME, ADMIN_SERVER_NAME);
    info.setServerPod(ADMIN_SERVER_NAME, currentPod);
    Watch.Response<V1Pod> event = WatchEvent.createModifiedEvent(modifiedPod).toWatchResponse();

    processor.dispatchPodWatch(event);

    assertThat(numPodsDeleted, is(1));
    assertThat(createdPodNames, hasItem(ADMIN_SERVER_NAME));
  }

  private void recordPodCreation(V1Pod pod, List<String> createdPodNames) {
    Optional.of(pod)
        .map(V1Pod::getMetadata)
        .map(V1ObjectMeta::getLabels)
        .map(this::getServerName)
        .map(createdPodNames::add);
  }

  private void recordPodDeletion(Integer value) {
    numPodsDeleted++;
  }

  private String getServerName(Map<String, String> labels) {
    return labels.get(SERVERNAME_LABEL);
  }

  private V1Pod createServerPod() {
    return withTimeAndVersion(PodHelper.createManagedServerPodModel(packet));
  }

  private V1Pod createAdminServerPod() {
    return withTimeAndVersion(PodHelper.createAdminServerPodModel(packet));
  }

  @SuppressWarnings("ConstantConditions")
  private V1Pod withTimeAndVersion(V1Pod pod) {
    pod.getMetadata().creationTimestamp(advanceAndGetTime()).resourceVersion(RESOURCE_VERSION);
    return pod;
  }

  private V1Pod withEvictedStatus(V1Pod pod) {
    return pod.status(new V1PodStatus().phase("Failed").reason(EVICTED_REASON));
  }

  private OffsetDateTime advanceAndGetTime() {
    SystemClockTestSupport.increment(1);
    return SystemClock.now();
  }

  // Returns a constant hash value to make canUseCurrentPod() in VerifyPodStep.apply to return true
  static class ConstantTestHash implements Function<Object, String> {
    public static Memento install() throws NoSuchFieldException {
      return StaticStubSupport.install(AnnotationHelper.class, "hashFunction", new ConstantTestHash());
    }

    @Override
    public String apply(Object object) {
      return "1234567";
    }
  }
}

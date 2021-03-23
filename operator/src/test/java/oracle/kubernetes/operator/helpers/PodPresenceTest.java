// Copyright (c) 2019, 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helpers;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
import oracle.kubernetes.operator.rest.ScanCacheStub;
import oracle.kubernetes.operator.utils.InMemoryCertificates;
import oracle.kubernetes.operator.utils.WlsDomainConfigSupport;
import oracle.kubernetes.operator.work.Component;
import oracle.kubernetes.operator.work.Packet;
import oracle.kubernetes.utils.TestUtils;
import oracle.kubernetes.weblogic.domain.model.Domain;
import org.hamcrest.junit.MatcherAssert;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static oracle.kubernetes.operator.DomainProcessorTestSetup.NS;
import static oracle.kubernetes.operator.DomainProcessorTestSetup.UID;
import static oracle.kubernetes.operator.LabelConstants.DOMAINUID_LABEL;
import static oracle.kubernetes.operator.LabelConstants.SERVERNAME_LABEL;
import static oracle.kubernetes.operator.ProcessingConstants.CLUSTER_NAME;
import static oracle.kubernetes.operator.ProcessingConstants.DOMAIN_COMPONENT_NAME;
import static oracle.kubernetes.operator.ProcessingConstants.DOMAIN_TOPOLOGY;
import static oracle.kubernetes.operator.ProcessingConstants.SERVER_NAME;
import static oracle.kubernetes.operator.ProcessingConstants.SERVER_SCAN;
import static oracle.kubernetes.operator.WebLogicConstants.RUNNING_STATE;
import static oracle.kubernetes.operator.WebLogicConstants.SHUTDOWN_STATE;
import static oracle.kubernetes.operator.WebLogicConstants.SUSPENDING_STATE;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.sameInstance;
import static org.hamcrest.junit.MatcherAssert.assertThat;

public class PodPresenceTest {

  private static final String CLUSTER = "cluster1";
  private static final String SERVER = "server1";
  private static final String POD_NAME = SERVER;
  private static final String RESOURCE_VERSION = "1233489";
  private static final String ADMIN_SERVER_NAME = "admin";
  private final DomainPresenceInfo info = new DomainPresenceInfo(NS, UID);
  private final List<Memento> mementos = new ArrayList<>();
  private final Map<String, Map<String, DomainPresenceInfo>> domains = new HashMap<>();
  private final KubernetesTestSupport testSupport = new KubernetesTestSupport();
  private final DomainProcessorImpl processor =
      new DomainProcessorImpl(DomainProcessorDelegateStub.createDelegate(testSupport));
  private OffsetDateTime clock = OffsetDateTime.now();
  private final Packet packet = new Packet();
  private final V1Pod pod =
      new V1Pod()
          .metadata(
              KubernetesUtils.withOperatorLabels(
                  "uid", new V1ObjectMeta().name(POD_NAME).namespace(NS)));

  @BeforeEach
  public void setUp() throws Exception {
    mementos.add(TestUtils.silenceOperatorLogger());
    mementos.add(testSupport.install());
    mementos.add(StaticStubSupport.install(DomainProcessorImpl.class, "DOMAINS", domains));
    mementos.add(TuningParametersStub.install());
    mementos.add(InMemoryCertificates.install());
    mementos.add(UnitTestHash.install());
    mementos.add(ScanCacheStub.install());

    domains.put(NS, new HashMap<>(ImmutableMap.of(UID, info)));
    disableDomainProcessing();
    Domain domain = DomainProcessorTestSetup.createTestDomain();
    DomainProcessorTestSetup.defineRequiredResources(testSupport);
    testSupport.defineResources(domain);
    info.setDomain(domain);

    WlsDomainConfigSupport configSupport =
        new WlsDomainConfigSupport(UID).withAdminServerName(ADMIN_SERVER_NAME);
    configSupport.addWlsServer("admin", 8001);
    configSupport.addWlsServer(SERVER, 7001);
    IntrospectionTestUtils.defineResources(testSupport, configSupport.createDomainConfig());

    packet.put(DOMAIN_TOPOLOGY, configSupport.createDomainConfig());
    packet.put(CLUSTER_NAME, CLUSTER);
    packet.put(SERVER_NAME, SERVER);
    packet.put(SERVER_SCAN, configSupport.createDomainConfig().getServerConfig(SERVER));
    packet.getComponents().put(DOMAIN_COMPONENT_NAME, Component.createFor(info));
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
  public void whenPodHasNoStatus_reportNotReady() {
    MatcherAssert.assertThat(PodHelper.isReady(pod), is(false));
  }

  @Test
  public void whenPodPhaseNotRunning_reportNotReady() {
    pod.status(new V1PodStatus());

    MatcherAssert.assertThat(PodHelper.isReady(pod), is(false));
  }

  @Test
  public void whenPodRunningButNoConditionsDefined_reportNotReady() {
    pod.status(new V1PodStatus().phase("Running"));

    MatcherAssert.assertThat(PodHelper.isReady(pod), is(false));
  }

  @Test
  public void whenPodRunningButNoReadyConditionsDefined_reportNotReady() {
    List<V1PodCondition> conditions = Collections.singletonList(new V1PodCondition().type("Huge"));
    pod.status(new V1PodStatus().phase("Running").conditions(conditions));

    MatcherAssert.assertThat(PodHelper.isReady(pod), is(false));
  }

  @Test
  public void whenPodRunningButReadyConditionIsNotTrue_reportNotReady() {
    List<V1PodCondition> conditions =
        Collections.singletonList(new V1PodCondition().type("Ready").status("False"));
    pod.status(new V1PodStatus().phase("Running").conditions(conditions));

    MatcherAssert.assertThat(PodHelper.isReady(pod), is(false));
  }

  @Test
  public void whenPodRunningAndReadyConditionIsTrue_reportReady() {
    makePodReady(pod);

    MatcherAssert.assertThat(PodHelper.isReady(pod), is(true));
  }

  private void makePodReady(V1Pod pod) {
    List<V1PodCondition> conditions =
        Collections.singletonList(new V1PodCondition().type("Ready").status("True"));
    pod.status(new V1PodStatus().phase("Running").conditions(conditions));
  }

  @Test
  public void whenPodHasNoStatus_reportNotFailed() {
    MatcherAssert.assertThat(PodHelper.isFailed(pod), is(false));
  }

  @Test
  public void whenPodPhaseNotFailed_reportNotFailed() {
    pod.status(new V1PodStatus().phase("Running"));

    MatcherAssert.assertThat(PodHelper.isFailed(pod), is(false));
  }

  @Test
  public void whenPodPhaseIsFailed_reportFailed() {
    pod.status(new V1PodStatus().phase("Failed"));

    MatcherAssert.assertThat(PodHelper.isFailed(pod), is(true));
  }

  @SuppressWarnings("ConstantConditions")
  @Test
  public void whenPodHasNoDomainUid_returnNull() {
    pod.getMetadata().getLabels().remove(DOMAINUID_LABEL);
    MatcherAssert.assertThat(PodHelper.getPodDomainUid(pod), nullValue());
  }

  @SuppressWarnings("ConstantConditions")
  @Test
  public void whenPodHasDomainUid_returnIt() {
    pod.getMetadata().labels(ImmutableMap.of(DOMAINUID_LABEL, "domain1"));

    MatcherAssert.assertThat(PodHelper.getPodDomainUid(pod), equalTo("domain1"));
  }

  @Test
  public void whenPodHasNoServerName_returnNull() {
    MatcherAssert.assertThat(PodHelper.getPodServerName(pod), nullValue());
  }

  @SuppressWarnings("ConstantConditions")
  @Test
  public void whenPodHasServerName_returnIt() {
    pod.getMetadata().labels(ImmutableMap.of(SERVERNAME_LABEL, "myserver"));

    MatcherAssert.assertThat(PodHelper.getPodServerName(pod), equalTo("myserver"));
  }

  // tests for updating DomainPresenceInfo's service fields.
  // all assume that a watcher event identifies a service in an existing domain.
  // the timestamps for services increment with each creation

  @Test
  public void onAddEventWithNoRecordedServerPod_addIt() {
    V1Pod newPod = createServerPod();
    Watch.Response<V1Pod> event = WatchEvent.createAddedEvent(newPod).toWatchResponse();

    processor.dispatchPodWatch(event);

    assertThat(info.getServerPod(SERVER), sameInstance(newPod));
  }

  @Test
  public void onAddEventWithNewerServerPod_replaceCurrentValue() {
    V1Pod currentPod = createServerPod();
    V1Pod newerPod = createServerPod();
    info.setServerPod(SERVER, currentPod);
    Watch.Response<V1Pod> event = WatchEvent.createAddedEvent(newerPod).toWatchResponse();

    processor.dispatchPodWatch(event);

    assertThat(info.getServerPod(SERVER), sameInstance(newerPod));
  }

  @Test
  public void onAddEventWithOlderServerPod_keepCurrentValue() {
    V1Pod olderPod = createServerPod();
    V1Pod currentPod = createServerPod();
    info.setServerPod(SERVER, currentPod);
    Watch.Response<V1Pod> event = WatchEvent.createAddedEvent(olderPod).toWatchResponse();

    processor.dispatchPodWatch(event);

    assertThat(info.getServerPod(SERVER), sameInstance(currentPod));
  }

  @Test
  public void onModifyEventWithNoRecordedServerPod_addIt() {
    V1Pod pod = createServerPod();
    Watch.Response<V1Pod> event = WatchEvent.createModifiedEvent(pod).toWatchResponse();

    processor.dispatchPodWatch(event);

    assertThat(info.getServerPod(SERVER), sameInstance(pod));
  }

  @Test
  public void onModifyEventWithNewerServerPod_replaceCurrentValue() {
    V1Pod currentPod = createServerPod();
    V1Pod newPod = createServerPod();
    info.setServerPod(SERVER, currentPod);
    Watch.Response<V1Pod> event = WatchEvent.createModifiedEvent(newPod).toWatchResponse();

    processor.dispatchPodWatch(event);

    assertThat(info.getServerPod(SERVER), sameInstance(newPod));
  }

  @Test
  public void onModifyEventWithOlderServerPod_keepCurrentValue() {
    V1Pod olderPod = createServerPod();
    V1Pod currentPod = createServerPod();
    info.setServerPod(SERVER, currentPod);
    Watch.Response<V1Pod> event = WatchEvent.createModifiedEvent(olderPod).toWatchResponse();

    processor.dispatchPodWatch(event);

    assertThat(info.getServerPod(SERVER), sameInstance(currentPod));
  }

  @Test
  public void onModifyEventWithPodNotReadyAndOldStatusRunning_setLastKnownStatusNull() {
    V1Pod eventPod = createServerPod();
    V1Pod currentPod = createServerPod();
    info.updateLastKnownServerStatus(SERVER, RUNNING_STATE);
    info.setServerPod(SERVER, currentPod);
    Watch.Response<V1Pod> event = WatchEvent.createModifiedEvent(eventPod).toWatchResponse();

    processor.dispatchPodWatch(event);

    assertThat(info.getLastKnownServerStatus(SERVER), nullValue());
  }

  @Test
  public void onModifyEventWithPodNotReadyAndOldStatusNotRunning_dontChangeIt() {
    V1Pod eventPod = createServerPod();
    V1Pod currentPod = createServerPod();
    info.updateLastKnownServerStatus(SERVER, SUSPENDING_STATE);
    info.setServerPod(SERVER, currentPod);
    Watch.Response<V1Pod> event = WatchEvent.createModifiedEvent(eventPod).toWatchResponse();

    processor.dispatchPodWatch(event);

    assertThat(info.getLastKnownServerStatus(SERVER).getStatus(), equalTo(SUSPENDING_STATE));
  }

  @Test
  public void onModifyEventWithPodReady_setLastKnownStatusRunning() {
    V1Pod eventPod = createServerPod();
    V1Pod currentPod = createServerPod();
    makePodReady(eventPod);
    info.setServerPod(SERVER, currentPod);
    Watch.Response<V1Pod> event = WatchEvent.createModifiedEvent(eventPod).toWatchResponse();

    processor.dispatchPodWatch(event);

    assertThat(info.getLastKnownServerStatus(SERVER).getStatus(), equalTo(RUNNING_STATE));
  }

  @Test
  public void onDeleteEventWithNoRecordedServerPod_ignoreIt() {
    V1Pod service = createServerPod();
    Watch.Response<V1Pod> event = WatchEvent.createDeletedEvent(service).toWatchResponse();

    processor.dispatchPodWatch(event);

    assertThat(info.getServerPod(SERVER), nullValue());
  }

  @Test
  public void onDeleteEventWithOlderServerPod_keepCurrentValue() {
    V1Pod oldPod = createServerPod();
    V1Pod currentPod = createServerPod();
    info.setServerPod(SERVER, currentPod);
    Watch.Response<V1Pod> event = WatchEvent.createDeletedEvent(oldPod).toWatchResponse();

    processor.dispatchPodWatch(event);

    assertThat(info.getServerPod(SERVER), sameInstance(currentPod));
  }

  @Test
  public void onDeleteEventWithSameServerPod_removeIt() {
    V1Pod currentPod = createServerPod();
    info.setServerPod(SERVER, currentPod);
    Watch.Response<V1Pod> event = WatchEvent.createDeletedEvent(currentPod).toWatchResponse();

    processor.dispatchPodWatch(event);

    assertThat(info.getServerPod(SERVER), nullValue());
  }

  @Test
  public void onDeleteEventWithNewerServerPod_removeIt() {
    V1Pod currentPod = createServerPod();
    V1Pod newerPod = createServerPod();
    info.setServerPod(SERVER, currentPod);
    Watch.Response<V1Pod> event = WatchEvent.createDeletedEvent(newerPod).toWatchResponse();

    processor.dispatchPodWatch(event);

    assertThat(info.getServerPod(SERVER), nullValue());
  }

  @Test
  public void afterDeleteEvent_setLastKnownStatus_Shutdown() {
    V1Pod currentPod = createServerPod();
    V1Pod newerPod = createServerPod();
    info.setServerPod(SERVER, currentPod);
    Watch.Response<V1Pod> event = WatchEvent.createDeletedEvent(newerPod).toWatchResponse();

    processor.dispatchPodWatch(event);

    assertThat(info.getLastKnownServerStatus(SERVER).getStatus(), equalTo(SHUTDOWN_STATE));
  }

  @Test
  public void afterDeleteEvent_restoreRequiredPod() {
    enableDomainProcessing();
    V1Pod currentPod = createServerPod();
    V1Pod newerPod = createServerPod();
    info.setServerPod(SERVER, currentPod);
    Watch.Response<V1Pod> event = WatchEvent.createDeletedEvent(newerPod).toWatchResponse();

    processor.dispatchPodWatch(event);

    assertThat(info.getServerPod(SERVER), notNullValue());
  }

  private V1Pod createServerPod() {
    return withTimeAndVersion(PodHelper.createManagedServerPodModel(packet));
  }

  @SuppressWarnings("ConstantConditions")
  private V1Pod withTimeAndVersion(V1Pod pod) {
    pod.getMetadata().creationTimestamp(getDateTime()).resourceVersion(RESOURCE_VERSION);
    return pod;
  }

  private OffsetDateTime getDateTime() {
    clock = clock.plusSeconds(1);
    return clock;
  }
}

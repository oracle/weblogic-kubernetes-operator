// Copyright (c) 2019, 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helpers;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.google.common.collect.ImmutableMap;
import com.meterware.simplestub.Memento;
import com.meterware.simplestub.StaticStubSupport;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1Service;
import io.kubernetes.client.openapi.models.V1ServiceSpec;
import io.kubernetes.client.util.Watch;
import oracle.kubernetes.operator.DomainProcessorDelegate;
import oracle.kubernetes.operator.DomainProcessorImpl;
import oracle.kubernetes.operator.LabelConstants;
import oracle.kubernetes.operator.builders.WatchEvent;
import oracle.kubernetes.operator.utils.WlsDomainConfigSupport;
import oracle.kubernetes.operator.work.Packet;
import oracle.kubernetes.utils.SystemClock;
import oracle.kubernetes.utils.TestUtils;
import oracle.kubernetes.weblogic.domain.DomainConfiguratorFactory;
import oracle.kubernetes.weblogic.domain.model.DomainResource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.meterware.simplestub.Stub.createStrictStub;
import static oracle.kubernetes.operator.DomainProcessorTestSetup.UID;
import static oracle.kubernetes.operator.LabelConstants.CLUSTERNAME_LABEL;
import static oracle.kubernetes.operator.LabelConstants.CREATEDBYOPERATOR_LABEL;
import static oracle.kubernetes.operator.LabelConstants.DOMAINUID_LABEL;
import static oracle.kubernetes.operator.LabelConstants.SERVERNAME_LABEL;
import static oracle.kubernetes.operator.ProcessingConstants.CLUSTER_NAME;
import static oracle.kubernetes.operator.ProcessingConstants.DOMAIN_TOPOLOGY;
import static oracle.kubernetes.operator.ProcessingConstants.SERVER_NAME;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.sameInstance;
import static org.hamcrest.junit.MatcherAssert.assertThat;

@SuppressWarnings("SameParameterValue")
class ServicePresenceTest {

  private static final String NS = "namespace";
  private static final String UID = "domain1";
  private static final String CLUSTER = "cluster1";
  private static final String SERVER = "server1";
  private static final String RESOURCE_VERSION = "1233489";
  private final DomainPresenceInfo info = new DomainPresenceInfo(NS, UID);
  private final List<Memento> mementos = new ArrayList<>();
  private final Map<String, Map<String, DomainPresenceInfo>> domains = new HashMap<>();
  private final DomainProcessorImpl processor =
      new DomainProcessorImpl(createStrictStub(DomainProcessorDelegate.class));
  private final Packet packet = new Packet();
  private OffsetDateTime clock = SystemClock.now();

  @BeforeEach
  public void setUp() throws Exception {
    mementos.add(TestUtils.silenceOperatorLogger());
    mementos.add(StaticStubSupport.install(DomainProcessorImpl.class, "domains", domains));
    mementos.add(UnitTestHash.install());

    HashMap<String, DomainPresenceInfo> infoMap = new HashMap<>();
    infoMap.put(UID, info);
    domains.put(NS, infoMap);
    disableMakeRightDomainProcessing();
    DomainResource domain = new DomainResource().withMetadata(new V1ObjectMeta().name(UID));
    DomainConfiguratorFactory.forDomain(domain)
        .configureAdminServer()
        .configureAdminService()
        .withChannel("sample");
    info.setDomain(domain);

    WlsDomainConfigSupport configSupport =
        new WlsDomainConfigSupport(UID).withAdminServerName("admin");

    packet.put(DOMAIN_TOPOLOGY, configSupport.createDomainConfig());
    packet.put(CLUSTER_NAME, CLUSTER);
    packet.put(SERVER_NAME, SERVER);
    packet.with(info);
  }

  private void disableMakeRightDomainProcessing() {
    info.setDeleting(true);
  }

  @AfterEach
  public void tearDown() {
    mementos.forEach(Memento::revert);
  }

  @Test
  void whenServiceHasNoDomainUid_returnNull() {
    V1Service service = new V1Service().metadata(new V1ObjectMeta());

    assertThat(ServiceHelper.getServiceDomainUid(service), nullValue());
  }

  @Test
  void whenServiceHasDomainUid_returnIt() {
    V1Service service =
        new V1Service()
            .metadata(new V1ObjectMeta().labels(ImmutableMap.of(DOMAINUID_LABEL, "domain1")));

    assertThat(ServiceHelper.getServiceDomainUid(service), equalTo("domain1"));
  }

  @Test
  void whenServiceHasNoServerName_returnNull() {
    V1Service service = new V1Service().metadata(new V1ObjectMeta());

    assertThat(ServiceHelper.getServerName(service), nullValue());
  }

  @Test
  void whenServiceHasServerName_returnIt() {
    V1Service service =
        new V1Service()
            .metadata(new V1ObjectMeta().labels(ImmutableMap.of(SERVERNAME_LABEL, "myserver")));

    assertThat(ServiceHelper.getServerName(service), equalTo("myserver"));
  }

  // tests for updating DomainPresenceInfo's service fields.
  // all assume that a watcher event identifies a service in an existing domain.
  // the timestamps for services increment with each creation

  @Test
  void onAddEventWithNoRecordedClusterService_addIt() {
    V1Service newService = createClusterService();
    Watch.Response<V1Service> event = WatchEvent.createAddedEvent(newService).toWatchResponse();

    processor.dispatchServiceWatch(event);

    assertThat(info.getClusterService(CLUSTER), sameInstance(newService));
  }

  @Test
  void onAddEventWithNoRecordedClusterServiceMissingInfo_dontAddIt() {
    V1Service newService = createClusterService();
    Watch.Response<V1Service> event = WatchEvent.createAddedEvent(newService).toWatchResponse();
    domains.get(NS).remove(UID);

    processor.dispatchServiceWatch(event);

    assertThat(info.getClusterService(CLUSTER), equalTo(null));
  }

  @Test
  void onAddEventWithNoRecordedClusterServiceMissingDomainUidLabel_dontAddIt() {
    V1Service newService = createClusterService();
    newService.getMetadata().getLabels().remove(DOMAINUID_LABEL);
    Watch.Response<V1Service> event = WatchEvent.createAddedEvent(newService).toWatchResponse();

    processor.dispatchServiceWatch(event);

    assertThat(info.getClusterService(CLUSTER), equalTo(null));
  }

  @Test
  void onAddEventWithNoRecordedClusterServiceMissingNamespace_dontAddIt() {
    V1Service newService = createClusterService();
    newService.getMetadata().setNamespace(null);
    Watch.Response<V1Service> event = WatchEvent.createAddedEvent(newService).toWatchResponse();

    processor.dispatchServiceWatch(event);

    assertThat(info.getClusterService(CLUSTER), equalTo(null));
  }

  @Test
  void onAddEventWithNewerClusterService_replaceCurrentValue() {
    V1Service currentService = createClusterService();
    V1Service newerService = createClusterService();
    info.setClusterService(CLUSTER, currentService);
    Watch.Response<V1Service> event = WatchEvent.createAddedEvent(newerService).toWatchResponse();

    processor.dispatchServiceWatch(event);

    assertThat(info.getClusterService(CLUSTER), sameInstance(newerService));
  }

  @Test
  void onAddEventWithOlderClusterService_keepCurrentValue() {
    V1Service olderService = createClusterService();
    V1Service currentService = createClusterService();
    info.setClusterService(CLUSTER, currentService);
    Watch.Response<V1Service> event = WatchEvent.createAddedEvent(olderService).toWatchResponse();

    processor.dispatchServiceWatch(event);

    assertThat(info.getClusterService(CLUSTER), sameInstance(currentService));
  }

  @Test
  void onModifyEventWithNoRecordedClusterService_addIt() {
    V1Service service1 = createClusterService();
    Watch.Response<V1Service> event = WatchEvent.createModifiedEvent(service1).toWatchResponse();

    processor.dispatchServiceWatch(event);

    assertThat(info.getClusterService(CLUSTER), sameInstance(service1));
  }

  @Test
  void onModifyEventWithNewerClusterService_replaceCurrentValue() {
    V1Service currentService = createClusterService();
    V1Service newService = createClusterService();
    info.setClusterService(CLUSTER, currentService);
    Watch.Response<V1Service> event = WatchEvent.createModifiedEvent(newService).toWatchResponse();

    processor.dispatchServiceWatch(event);

    assertThat(info.getClusterService(CLUSTER), sameInstance(newService));
  }

  @Test
  void onModifyEventWithOlderClusterService_keepCurrentValue() {
    V1Service oldService = createClusterService();
    V1Service currentService = createClusterService();
    info.setClusterService(CLUSTER, currentService);
    Watch.Response<V1Service> event = WatchEvent.createModifiedEvent(oldService).toWatchResponse();

    processor.dispatchServiceWatch(event);

    assertThat(info.getClusterService(CLUSTER), sameInstance(currentService));
  }

  @Test
  void onDeleteEventWithNoRecordedClusterService_ignoreIt() {
    V1Service service = createClusterService();
    Watch.Response<V1Service> event = WatchEvent.createDeletedEvent(service).toWatchResponse();

    processor.dispatchServiceWatch(event);

    assertThat(info.getClusterService(CLUSTER), nullValue());
  }

  @Test
  void onDeleteEventWithOlderClusterService_keepCurrentValue() {
    V1Service oldService = createClusterService();
    V1Service currentService = createClusterService();
    info.setClusterService(CLUSTER, currentService);
    Watch.Response<V1Service> event = WatchEvent.createDeletedEvent(oldService).toWatchResponse();

    processor.dispatchServiceWatch(event);

    assertThat(info.getClusterService(CLUSTER), sameInstance(currentService));
  }

  @Test
  void onDeleteEventWithSameClusterService_removeIt() {
    V1Service currentService = createClusterService();
    info.setClusterService(CLUSTER, currentService);
    Watch.Response<V1Service> event =
        WatchEvent.createDeletedEvent(currentService).toWatchResponse();

    processor.dispatchServiceWatch(event);

    assertThat(info.getClusterService(CLUSTER), nullValue());
  }

  @Test
  void onDeleteEventWithNewerClusterService_removeIt() {
    V1Service currentService = createClusterService();
    V1Service newerService = createClusterService();
    info.setClusterService(CLUSTER, currentService);
    Watch.Response<V1Service> event = WatchEvent.createDeletedEvent(newerService).toWatchResponse();

    processor.dispatchServiceWatch(event);

    assertThat(info.getClusterService(CLUSTER), nullValue());
  }

  @Test
  void onDeleteEventWithNoRecordedClusterServiceInfoMissingDomainObject_dontAddIt() {
    V1Service newService = createClusterService();
    Watch.Response<V1Service> event = WatchEvent.createDeletedEvent(newService).toWatchResponse();
    domains.get(NS).get(UID).setDomain(null);

    processor.dispatchServiceWatch(event);

    assertThat(info.getClusterService(CLUSTER), equalTo(null));
  }

  @Test
  void whenEventContainsServiceWithClusterNameAndNoTypeLabel_addAsClusterService() {
    V1Service service =
        new V1Service()
            .metadata(
                createMetadata()
                    .putLabelsItem(CREATEDBYOPERATOR_LABEL, "true")
                    .putLabelsItem(CLUSTERNAME_LABEL, CLUSTER));
    Watch.Response<V1Service> event = WatchEvent.createAddedEvent(service).toWatchResponse();

    processor.dispatchServiceWatch(event);

    assertThat(info.getClusterService(CLUSTER), sameInstance(service));
  }

  @Test
  void onAddEventWithNoRecordedServerService_addIt() {
    V1Service newService = createServerService();
    Watch.Response<V1Service> event = WatchEvent.createAddedEvent(newService).toWatchResponse();

    processor.dispatchServiceWatch(event);

    assertThat(info.getServerService(SERVER), sameInstance(newService));
  }

  @Test
  void onAddEventWithNewerServerService_replaceCurrentValue() {
    V1Service currentService = createServerService();
    V1Service newerService = createServerService();
    info.setServerService(SERVER, currentService);
    Watch.Response<V1Service> event = WatchEvent.createAddedEvent(newerService).toWatchResponse();

    processor.dispatchServiceWatch(event);

    assertThat(info.getServerService(SERVER), sameInstance(newerService));
  }

  @Test
  void onAddEventWithOlderServerService_keepCurrentValue() {
    V1Service olderService = createServerService();
    V1Service currentService = createServerService();
    info.setServerService(SERVER, currentService);
    Watch.Response<V1Service> event = WatchEvent.createAddedEvent(olderService).toWatchResponse();

    processor.dispatchServiceWatch(event);

    assertThat(info.getServerService(SERVER), sameInstance(currentService));
  }

  @Test
  void onModifyEventWithNoRecordedServerService_addIt() {
    V1Service service1 = createServerService();
    Watch.Response<V1Service> event = WatchEvent.createModifiedEvent(service1).toWatchResponse();

    processor.dispatchServiceWatch(event);

    assertThat(info.getServerService(SERVER), sameInstance(service1));
  }

  @Test
  void onModifyEventWithNewerServerService_replaceCurrentValue() {
    V1Service currentService = createServerService();
    V1Service newService = createServerService();
    info.setServerService(SERVER, currentService);
    Watch.Response<V1Service> event = WatchEvent.createModifiedEvent(newService).toWatchResponse();

    processor.dispatchServiceWatch(event);

    assertThat(info.getServerService(SERVER), sameInstance(newService));
  }

  @Test
  void onModifyEventWithOlderServerService_keepCurrentValue() {
    V1Service oldService = createServerService();
    V1Service currentService = createServerService();
    info.setServerService(SERVER, currentService);
    Watch.Response<V1Service> event = WatchEvent.createModifiedEvent(oldService).toWatchResponse();

    processor.dispatchServiceWatch(event);

    assertThat(info.getServerService(SERVER), sameInstance(currentService));
  }

  @Test
  void onDeleteEventWithNoRecordedServerService_ignoreIt() {
    V1Service service = createServerService();
    Watch.Response<V1Service> event = WatchEvent.createDeletedEvent(service).toWatchResponse();

    processor.dispatchServiceWatch(event);

    assertThat(info.getServerService(SERVER), nullValue());
  }

  @Test
  void onDeleteEventWithOlderServerService_keepCurrentValue() {
    V1Service oldService = createServerService();
    V1Service currentService = createServerService();
    info.setServerService(SERVER, currentService);
    Watch.Response<V1Service> event = WatchEvent.createDeletedEvent(oldService).toWatchResponse();

    processor.dispatchServiceWatch(event);

    assertThat(info.getServerService(SERVER), sameInstance(currentService));
  }

  @Test
  void onDeleteEventWithSameServerService_removeIt() {
    V1Service currentService = createServerService();
    info.setServerService(SERVER, currentService);
    Watch.Response<V1Service> event =
        WatchEvent.createDeletedEvent(currentService).toWatchResponse();

    processor.dispatchServiceWatch(event);

    assertThat(info.getServerService(SERVER), nullValue());
  }

  @Test
  void onDeleteEventWithNewerServerService_removeIt() {
    V1Service currentService = createServerService();
    V1Service newerService = createServerService();
    info.setServerService(SERVER, currentService);
    Watch.Response<V1Service> event = WatchEvent.createDeletedEvent(newerService).toWatchResponse();

    processor.dispatchServiceWatch(event);

    assertThat(info.getServerService(SERVER), nullValue());
  }

  @Test
  void whenEventContainsServiceWithServerNameAndNoTypeLabel_addAsServerService() {
    V1Service service =
        new V1Service()
            .metadata(
                createMetadata()
                    .putLabelsItem(CREATEDBYOPERATOR_LABEL, "true")
                    .putLabelsItem(CLUSTERNAME_LABEL, CLUSTER)
                    .putLabelsItem(SERVERNAME_LABEL, SERVER));
    Watch.Response<V1Service> event = WatchEvent.createAddedEvent(service).toWatchResponse();

    processor.dispatchServiceWatch(event);

    assertThat(info.getServerService(SERVER), sameInstance(service));
  }

  @Test
  void onAddEventWithNoRecordedExternalService_addIt() {
    V1Service newService = createExternalService();
    Watch.Response<V1Service> event = WatchEvent.createAddedEvent(newService).toWatchResponse();

    processor.dispatchServiceWatch(event);

    assertThat(info.getExternalService(SERVER), sameInstance(newService));
  }

  @Test
  void onAddEventWithNewerExternalService_replaceCurrentValue() {
    V1Service currentService = createExternalService();
    V1Service newerService = createExternalService();
    info.setExternalService(SERVER, currentService);
    Watch.Response<V1Service> event = WatchEvent.createAddedEvent(newerService).toWatchResponse();

    processor.dispatchServiceWatch(event);

    assertThat(info.getExternalService(SERVER), sameInstance(newerService));
  }

  @Test
  void onAddEventWithOlderExternalService_keepCurrentValue() {
    V1Service olderService = createExternalService();
    V1Service currentService = createExternalService();
    info.setExternalService(SERVER, currentService);
    Watch.Response<V1Service> event = WatchEvent.createAddedEvent(olderService).toWatchResponse();

    processor.dispatchServiceWatch(event);

    assertThat(info.getExternalService(SERVER), sameInstance(currentService));
  }

  @Test
  void onModifyEventWithNoRecordedExternalService_addIt() {
    V1Service service1 = createExternalService();
    Watch.Response<V1Service> event = WatchEvent.createModifiedEvent(service1).toWatchResponse();

    processor.dispatchServiceWatch(event);

    assertThat(info.getExternalService(SERVER), sameInstance(service1));
  }

  @Test
  void onModifyEventWithNewerExternalService_replaceCurrentValue() {
    V1Service currentService = createExternalService();
    V1Service newService = createExternalService();
    info.setExternalService(SERVER, currentService);
    Watch.Response<V1Service> event = WatchEvent.createModifiedEvent(newService).toWatchResponse();

    processor.dispatchServiceWatch(event);

    assertThat(info.getExternalService(SERVER), sameInstance(newService));
  }

  @Test
  void onModifyEventWithOlderExternalService_keepCurrentValue() {
    V1Service oldService = createExternalService();
    V1Service currentService = createExternalService();
    info.setExternalService(SERVER, currentService);
    Watch.Response<V1Service> event = WatchEvent.createModifiedEvent(oldService).toWatchResponse();

    processor.dispatchServiceWatch(event);

    assertThat(info.getExternalService(SERVER), sameInstance(currentService));
  }

  @Test
  void onDeleteEventWithNoRecordedExternalService_ignoreIt() {
    V1Service service = createExternalService();
    Watch.Response<V1Service> event = WatchEvent.createDeletedEvent(service).toWatchResponse();

    processor.dispatchServiceWatch(event);

    assertThat(info.getExternalService(SERVER), nullValue());
  }

  @Test
  void onDeleteEventWithOlderExternalService_keepCurrentValue() {
    V1Service oldService = createExternalService();
    V1Service currentService = createExternalService();
    info.setExternalService(SERVER, currentService);
    Watch.Response<V1Service> event = WatchEvent.createDeletedEvent(oldService).toWatchResponse();

    processor.dispatchServiceWatch(event);

    assertThat(info.getExternalService(SERVER), sameInstance(currentService));
  }

  @Test
  void onDeleteEventWithSameExternalService_removeIt() {
    V1Service currentService = createExternalService();
    info.setExternalService(SERVER, currentService);
    Watch.Response<V1Service> event =
        WatchEvent.createDeletedEvent(currentService).toWatchResponse();

    processor.dispatchServiceWatch(event);

    assertThat(info.getExternalService(SERVER), nullValue());
  }

  @Test
  void onDeleteEventWithNewerExternalService_removeIt() {
    V1Service currentService = createExternalService();
    V1Service newerService = createExternalService();
    info.setExternalService(SERVER, currentService);
    Watch.Response<V1Service> event = WatchEvent.createDeletedEvent(newerService).toWatchResponse();

    processor.dispatchServiceWatch(event);

    assertThat(info.getExternalService(SERVER), nullValue());
  }

  @Test
  void whenEventContainsServiceWithNodePortAndNoTypeLabel_addAsExternalService() {
    V1Service service =
        new V1Service()
            .metadata(
                createMetadata()
                    .putLabelsItem(CREATEDBYOPERATOR_LABEL, "true")
                    .putLabelsItem(SERVERNAME_LABEL, SERVER))
            .spec(new V1ServiceSpec().type("NodePort"));
    Watch.Response<V1Service> event = WatchEvent.createAddedEvent(service).toWatchResponse();

    processor.dispatchServiceWatch(event);

    assertThat(info.getExternalService(SERVER), sameInstance(service));
  }

  private V1Service createClusterService() {
    return withTimeAndVersion(ServiceHelper.createClusterServiceModel(packet));
  }

  private V1Service createServerService() {
    return withTimeAndVersion(ServiceHelper.createServerServiceModel(packet));
  }

  private V1Service createExternalService() {
    return withTimeAndVersion(ServiceHelper.createExternalServiceModel(packet));
  }

  private V1Service withTimeAndVersion(V1Service service) {
    Objects.requireNonNull(service.getMetadata()).creationTimestamp(getDateTime()).resourceVersion(RESOURCE_VERSION);
    return service;
  }

  private OffsetDateTime getDateTime() {
    clock = clock.plusSeconds(1);
    return clock;
  }

  private V1ObjectMeta createMetadata() {
    return new V1ObjectMeta()
        .namespace(NS)
        .putLabelsItem(LabelConstants.DOMAINUID_LABEL, UID)
        .creationTimestamp(getDateTime())
        .resourceVersion(RESOURCE_VERSION);
  }

  @Test
  void whenClusterServiceAdded_recordforCluster() {
    V1Service clusterService = createClusterService();

    ServiceHelper.addToPresence(info, clusterService);

    assertThat(info.getClusterService(CLUSTER), sameInstance(clusterService));
  }

  @Test
  void whenServerServiceAdded_recordforServer() {
    V1Service serverService = createServerService();

    ServiceHelper.addToPresence(info, serverService);

    assertThat(info.getServerService(SERVER), sameInstance(serverService));
  }

  @Test
  void whenExternalServiceAdded_recordforServer() {
    V1Service externalService = createExternalService();

    ServiceHelper.addToPresence(info, externalService);

    assertThat(info.getExternalService(SERVER), sameInstance(externalService));
  }
}

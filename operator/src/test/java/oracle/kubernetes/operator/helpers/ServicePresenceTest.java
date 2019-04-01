// Copyright 2019, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helpers;

import static com.meterware.simplestub.Stub.createStrictStub;
import static oracle.kubernetes.operator.LabelConstants.CLUSTERNAME_LABEL;
import static oracle.kubernetes.operator.LabelConstants.CREATEDBYOPERATOR_LABEL;
import static oracle.kubernetes.operator.LabelConstants.DOMAINUID_LABEL;
import static oracle.kubernetes.operator.LabelConstants.SERVERNAME_LABEL;
import static oracle.kubernetes.operator.ProcessingConstants.CLUSTER_NAME;
import static oracle.kubernetes.operator.ProcessingConstants.DOMAIN_COMPONENT_NAME;
import static oracle.kubernetes.operator.ProcessingConstants.DOMAIN_TOPOLOGY;
import static oracle.kubernetes.operator.ProcessingConstants.SERVER_NAME;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.sameInstance;
import static org.hamcrest.junit.MatcherAssert.assertThat;

import com.google.common.collect.ImmutableMap;
import com.meterware.simplestub.Memento;
import com.meterware.simplestub.StaticStubSupport;
import io.kubernetes.client.models.V1ObjectMeta;
import io.kubernetes.client.models.V1Service;
import io.kubernetes.client.util.Watch;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import oracle.kubernetes.TestUtils;
import oracle.kubernetes.operator.DomainProcessorDelegate;
import oracle.kubernetes.operator.DomainProcessorImpl;
import oracle.kubernetes.operator.LabelConstants;
import oracle.kubernetes.operator.builders.WatchEvent;
import oracle.kubernetes.operator.utils.WlsDomainConfigSupport;
import oracle.kubernetes.operator.work.Component;
import oracle.kubernetes.operator.work.Packet;
import oracle.kubernetes.weblogic.domain.DomainConfiguratorFactory;
import oracle.kubernetes.weblogic.domain.model.Domain;
import org.joda.time.DateTime;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

@SuppressWarnings("SameParameterValue")
public class ServicePresenceTest {

  private static final String NS = "namespace";
  private static final String UID = "domain1";
  private static final String CLUSTER = "cluster1";
  private static final String SERVER = "server1";
  private static final String RESOURCE_VERSION = "1233489";
  private DomainPresenceInfo info = new DomainPresenceInfo(NS, UID);
  private List<Memento> mementos = new ArrayList<>();
  private Map<String, Map<String, DomainPresenceInfo>> domains = new HashMap<>();
  private DomainProcessorImpl processor =
      new DomainProcessorImpl(createStrictStub(DomainProcessorDelegate.class));
  private long clock;
  private Packet packet = new Packet();

  @Before
  public void setUp() throws Exception {
    mementos.add(TestUtils.silenceOperatorLogger());
    mementos.add(StaticStubSupport.install(DomainProcessorImpl.class, "DOMAINS", domains));
    mementos.add(UnitTestHash.install());

    domains.put(NS, ImmutableMap.of(UID, info));
    disableMakeRightDomainProcessing();
    Domain domain = new Domain().withMetadata(new V1ObjectMeta().name(UID));
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
    packet.getComponents().put(DOMAIN_COMPONENT_NAME, Component.createFor(info));
  }

  private void disableMakeRightDomainProcessing() {
    info.setDeleting(true);
  }

  @After
  public void tearDown() {
    for (Memento memento : mementos) memento.revert();
  }

  @Test
  public void whenServiceHasNoDomainUid_returnNull() {
    V1Service service = new V1Service().metadata(new V1ObjectMeta());

    assertThat(ServiceHelper.getServiceDomainUID(service), nullValue());
  }

  @Test
  public void whenServiceHasDomainUid_returnIt() {
    V1Service service =
        new V1Service()
            .metadata(new V1ObjectMeta().labels(ImmutableMap.of(DOMAINUID_LABEL, "domain1")));

    assertThat(ServiceHelper.getServiceDomainUID(service), equalTo("domain1"));
  }

  @Test
  public void whenServiceHasNoServerName_returnNull() {
    V1Service service = new V1Service().metadata(new V1ObjectMeta());

    assertThat(ServiceHelper.getServerName(service), nullValue());
  }

  @Test
  public void whenServiceHasServerName_returnIt() {
    V1Service service =
        new V1Service()
            .metadata(new V1ObjectMeta().labels(ImmutableMap.of(SERVERNAME_LABEL, "myserver")));

    assertThat(ServiceHelper.getServerName(service), equalTo("myserver"));
  }

  // tests for updating DomainPresenceInfo's service fields.
  // all assume that a watcher event identifies a service in an existing domain.
  // the timestamps for services increment with each creation

  @Test
  public void onAddEventWithNoRecordedClusterService_addIt() {
    V1Service newService = createClusterService();
    Watch.Response<V1Service> event = WatchEvent.createAddedEvent(newService).toWatchResponse();

    processor.dispatchServiceWatch(event);

    assertThat(info.getClusterService(CLUSTER), sameInstance(newService));
  }

  @Test
  public void onAddEventWithNewerClusterService_replaceCurrentValue() {
    V1Service currentService = createClusterService();
    V1Service newerService = createClusterService();
    info.setClusterService(CLUSTER, currentService);
    Watch.Response<V1Service> event = WatchEvent.createAddedEvent(newerService).toWatchResponse();

    processor.dispatchServiceWatch(event);

    assertThat(info.getClusterService(CLUSTER), sameInstance(newerService));
  }

  @Test
  public void onAddEventWithOlderClusterService_keepCurrentValue() {
    V1Service olderService = createClusterService();
    V1Service currentService = createClusterService();
    info.setClusterService(CLUSTER, currentService);
    Watch.Response<V1Service> event = WatchEvent.createAddedEvent(olderService).toWatchResponse();

    processor.dispatchServiceWatch(event);

    assertThat(info.getClusterService(CLUSTER), sameInstance(currentService));
  }

  @Test
  public void onModifyEventWithNoRecordedClusterService_addIt() {
    V1Service service1 = createClusterService();
    Watch.Response<V1Service> event = WatchEvent.createModifiedEvent(service1).toWatchResponse();

    processor.dispatchServiceWatch(event);

    assertThat(info.getClusterService(CLUSTER), sameInstance(service1));
  }

  @Test
  public void onModifyEventWithNewerClusterService_replaceCurrentValue() {
    V1Service currentService = createClusterService();
    V1Service newService = createClusterService();
    info.setClusterService(CLUSTER, currentService);
    Watch.Response<V1Service> event = WatchEvent.createModifiedEvent(newService).toWatchResponse();

    processor.dispatchServiceWatch(event);

    assertThat(info.getClusterService(CLUSTER), sameInstance(newService));
  }

  @Test
  public void onModifyEventWithOlderClusterService_keepCurrentValue() {
    V1Service oldService = createClusterService();
    V1Service currentService = createClusterService();
    info.setClusterService(CLUSTER, currentService);
    Watch.Response<V1Service> event = WatchEvent.createModifiedEvent(oldService).toWatchResponse();

    processor.dispatchServiceWatch(event);

    assertThat(info.getClusterService(CLUSTER), sameInstance(currentService));
  }

  @Test
  public void onDeleteEventWithNoRecordedClusterService_ignoreIt() {
    V1Service service = createClusterService();
    Watch.Response<V1Service> event = WatchEvent.createDeleteEvent(service).toWatchResponse();

    processor.dispatchServiceWatch(event);

    assertThat(info.getClusterService(CLUSTER), nullValue());
  }

  @Test
  public void onDeleteEventWithOlderClusterService_keepCurrentValue() {
    V1Service oldService = createClusterService();
    V1Service currentService = createClusterService();
    info.setClusterService(CLUSTER, currentService);
    Watch.Response<V1Service> event = WatchEvent.createDeleteEvent(oldService).toWatchResponse();

    processor.dispatchServiceWatch(event);

    assertThat(info.getClusterService(CLUSTER), sameInstance(currentService));
  }

  @Test
  public void onDeleteEventWithSameClusterService_removeIt() {
    V1Service currentService = createClusterService();
    info.setClusterService(CLUSTER, currentService);
    Watch.Response<V1Service> event =
        WatchEvent.createDeleteEvent(currentService).toWatchResponse();

    processor.dispatchServiceWatch(event);

    assertThat(info.getClusterService(CLUSTER), nullValue());
  }

  @Test
  public void onDeleteEventWithNewerClusterService_removeIt() {
    V1Service currentService = createClusterService();
    V1Service newerService = createClusterService();
    info.setClusterService(CLUSTER, currentService);
    Watch.Response<V1Service> event = WatchEvent.createDeleteEvent(newerService).toWatchResponse();

    processor.dispatchServiceWatch(event);

    assertThat(info.getClusterService(CLUSTER), nullValue());
  }

  @Test
  public void whenEventContainsServiceWithClusterNameAndNoTypeLabel_addAsClusterService() {
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
  public void onAddEventWithNoRecordedServerService_addIt() {
    V1Service newService = createServerService();
    Watch.Response<V1Service> event = WatchEvent.createAddedEvent(newService).toWatchResponse();

    processor.dispatchServiceWatch(event);

    assertThat(info.getServerService(SERVER), sameInstance(newService));
  }

  @Test
  public void onAddEventWithNewerServerService_replaceCurrentValue() {
    V1Service currentService = createServerService();
    V1Service newerService = createServerService();
    info.setServerService(SERVER, currentService);
    Watch.Response<V1Service> event = WatchEvent.createAddedEvent(newerService).toWatchResponse();

    processor.dispatchServiceWatch(event);

    assertThat(info.getServerService(SERVER), sameInstance(newerService));
  }

  @Test
  public void onAddEventWithOlderServerService_keepCurrentValue() {
    V1Service olderService = createServerService();
    V1Service currentService = createServerService();
    info.setServerService(SERVER, currentService);
    Watch.Response<V1Service> event = WatchEvent.createAddedEvent(olderService).toWatchResponse();

    processor.dispatchServiceWatch(event);

    assertThat(info.getServerService(SERVER), sameInstance(currentService));
  }

  @Test
  public void onModifyEventWithNoRecordedServerService_addIt() {
    V1Service service1 = createServerService();
    Watch.Response<V1Service> event = WatchEvent.createModifiedEvent(service1).toWatchResponse();

    processor.dispatchServiceWatch(event);

    assertThat(info.getServerService(SERVER), sameInstance(service1));
  }

  @Test
  public void onModifyEventWithNewerServerService_replaceCurrentValue() {
    V1Service currentService = createServerService();
    V1Service newService = createServerService();
    info.setServerService(SERVER, currentService);
    Watch.Response<V1Service> event = WatchEvent.createModifiedEvent(newService).toWatchResponse();

    processor.dispatchServiceWatch(event);

    assertThat(info.getServerService(SERVER), sameInstance(newService));
  }

  @Test
  public void onModifyEventWithOlderServerService_keepCurrentValue() {
    V1Service oldService = createServerService();
    V1Service currentService = createServerService();
    info.setServerService(SERVER, currentService);
    Watch.Response<V1Service> event = WatchEvent.createModifiedEvent(oldService).toWatchResponse();

    processor.dispatchServiceWatch(event);

    assertThat(info.getServerService(SERVER), sameInstance(currentService));
  }

  @Test
  public void onDeleteEventWithNoRecordedServerService_ignoreIt() {
    V1Service service = createServerService();
    Watch.Response<V1Service> event = WatchEvent.createDeleteEvent(service).toWatchResponse();

    processor.dispatchServiceWatch(event);

    assertThat(info.getServerService(SERVER), nullValue());
  }

  @Test
  public void onDeleteEventWithOlderServerService_keepCurrentValue() {
    V1Service oldService = createServerService();
    V1Service currentService = createServerService();
    info.setServerService(SERVER, currentService);
    Watch.Response<V1Service> event = WatchEvent.createDeleteEvent(oldService).toWatchResponse();

    processor.dispatchServiceWatch(event);

    assertThat(info.getServerService(SERVER), sameInstance(currentService));
  }

  @Test
  public void onDeleteEventWithSameServerService_removeIt() {
    V1Service currentService = createServerService();
    info.setServerService(SERVER, currentService);
    Watch.Response<V1Service> event =
        WatchEvent.createDeleteEvent(currentService).toWatchResponse();

    processor.dispatchServiceWatch(event);

    assertThat(info.getServerService(SERVER), nullValue());
  }

  @Test
  public void onDeleteEventWithNewerServerService_removeIt() {
    V1Service currentService = createServerService();
    V1Service newerService = createServerService();
    info.setServerService(SERVER, currentService);
    Watch.Response<V1Service> event = WatchEvent.createDeleteEvent(newerService).toWatchResponse();

    processor.dispatchServiceWatch(event);

    assertThat(info.getServerService(SERVER), nullValue());
  }

  @Test
  public void whenEventContainsServiceWithServerNameAndNoTypeLabel_addAsServerService() {
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
  public void onAddEventWithNoRecordedExternalService_addIt() {
    V1Service newService = createExternalService();
    Watch.Response<V1Service> event = WatchEvent.createAddedEvent(newService).toWatchResponse();

    processor.dispatchServiceWatch(event);

    assertThat(info.getExternalService(SERVER), sameInstance(newService));
  }

  @Test
  public void onAddEventWithNewerExternalService_replaceCurrentValue() {
    V1Service currentService = createExternalService();
    V1Service newerService = createExternalService();
    info.setExternalService(SERVER, currentService);
    Watch.Response<V1Service> event = WatchEvent.createAddedEvent(newerService).toWatchResponse();

    processor.dispatchServiceWatch(event);

    assertThat(info.getExternalService(SERVER), sameInstance(newerService));
  }

  @Test
  public void onAddEventWithOlderExternalService_keepCurrentValue() {
    V1Service olderService = createExternalService();
    V1Service currentService = createExternalService();
    info.setExternalService(SERVER, currentService);
    Watch.Response<V1Service> event = WatchEvent.createAddedEvent(olderService).toWatchResponse();

    processor.dispatchServiceWatch(event);

    assertThat(info.getExternalService(SERVER), sameInstance(currentService));
  }

  @Test
  public void onModifyEventWithNoRecordedExternalService_addIt() {
    V1Service service1 = createExternalService();
    Watch.Response<V1Service> event = WatchEvent.createModifiedEvent(service1).toWatchResponse();

    processor.dispatchServiceWatch(event);

    assertThat(info.getExternalService(SERVER), sameInstance(service1));
  }

  @Test
  public void onModifyEventWithNewerExternalService_replaceCurrentValue() {
    V1Service currentService = createExternalService();
    V1Service newService = createExternalService();
    info.setExternalService(SERVER, currentService);
    Watch.Response<V1Service> event = WatchEvent.createModifiedEvent(newService).toWatchResponse();

    processor.dispatchServiceWatch(event);

    assertThat(info.getExternalService(SERVER), sameInstance(newService));
  }

  @Test
  public void onModifyEventWithOlderExternalService_keepCurrentValue() {
    V1Service oldService = createExternalService();
    V1Service currentService = createExternalService();
    info.setExternalService(SERVER, currentService);
    Watch.Response<V1Service> event = WatchEvent.createModifiedEvent(oldService).toWatchResponse();

    processor.dispatchServiceWatch(event);

    assertThat(info.getExternalService(SERVER), sameInstance(currentService));
  }

  @Test
  public void onDeleteEventWithNoRecordedExternalService_ignoreIt() {
    V1Service service = createExternalService();
    Watch.Response<V1Service> event = WatchEvent.createDeleteEvent(service).toWatchResponse();

    processor.dispatchServiceWatch(event);

    assertThat(info.getExternalService(SERVER), nullValue());
  }

  @Test
  public void onDeleteEventWithOlderExternalService_keepCurrentValue() {
    V1Service oldService = createExternalService();
    V1Service currentService = createExternalService();
    info.setExternalService(SERVER, currentService);
    Watch.Response<V1Service> event = WatchEvent.createDeleteEvent(oldService).toWatchResponse();

    processor.dispatchServiceWatch(event);

    assertThat(info.getExternalService(SERVER), sameInstance(currentService));
  }

  @Test
  public void onDeleteEventWithSameExternalService_removeIt() {
    V1Service currentService = createExternalService();
    info.setExternalService(SERVER, currentService);
    Watch.Response<V1Service> event =
        WatchEvent.createDeleteEvent(currentService).toWatchResponse();

    processor.dispatchServiceWatch(event);

    assertThat(info.getExternalService(SERVER), nullValue());
  }

  @Test
  public void onDeleteEventWithNewerExternalService_removeIt() {
    V1Service currentService = createExternalService();
    V1Service newerService = createExternalService();
    info.setExternalService(SERVER, currentService);
    Watch.Response<V1Service> event = WatchEvent.createDeleteEvent(newerService).toWatchResponse();

    processor.dispatchServiceWatch(event);

    assertThat(info.getExternalService(SERVER), nullValue());
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
    service.getMetadata().creationTimestamp(getDateTime()).resourceVersion(RESOURCE_VERSION);
    return service;
  }

  private DateTime getDateTime() {
    return new DateTime(++clock);
  }

  private V1ObjectMeta createMetadata() {
    return new V1ObjectMeta()
        .namespace(NS)
        .putLabelsItem(LabelConstants.DOMAINUID_LABEL, UID)
        .creationTimestamp(getDateTime())
        .resourceVersion(RESOURCE_VERSION);
  }

  @Test
  public void whenClusterServiceAdded_recordforCluster() {
    V1Service clusterService = createClusterService();

    ServiceHelper.addToPresence(info, clusterService);

    assertThat(info.getClusterService(CLUSTER), sameInstance(clusterService));
  }

  @Test
  public void whenServerServiceAdded_recordforServer() {
    V1Service serverService = createServerService();

    ServiceHelper.addToPresence(info, serverService);

    assertThat(info.getServerService(SERVER), sameInstance(serverService));
  }

  @Test
  public void whenExternalServiceAdded_recordforServer() {
    V1Service externalService = createExternalService();

    ServiceHelper.addToPresence(info, externalService);

    assertThat(info.getExternalService(SERVER), sameInstance(externalService));
  }
}

// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator;

import static oracle.kubernetes.operator.DomainPresenceInfoMatcher.domain;
import static oracle.kubernetes.operator.KubernetesConstants.DOMAIN_CONFIG_MAP_NAME;
import static oracle.kubernetes.operator.LabelConstants.CHANNELNAME_LABEL;
import static oracle.kubernetes.operator.LabelConstants.CLUSTERNAME_LABEL;
import static oracle.kubernetes.operator.LabelConstants.CREATEDBYOPERATOR_LABEL;
import static oracle.kubernetes.operator.LabelConstants.DOMAINUID_LABEL;
import static oracle.kubernetes.operator.LabelConstants.SERVERNAME_LABEL;
import static oracle.kubernetes.operator.LabelConstants.forDomainUid;
import static oracle.kubernetes.operator.WebLogicConstants.READINESS_PROBE_NOT_READY_STATE;
import static org.hamcrest.Matchers.anEmptyMap;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.Matchers.hasValue;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.sameInstance;
import static org.hamcrest.junit.MatcherAssert.assertThat;

import com.meterware.simplestub.Memento;
import com.meterware.simplestub.StaticStubSupport;
import com.meterware.simplestub.Stub;
import io.kubernetes.client.models.V1ConfigMap;
import io.kubernetes.client.models.V1Event;
import io.kubernetes.client.models.V1EventList;
import io.kubernetes.client.models.V1ListMeta;
import io.kubernetes.client.models.V1ObjectMeta;
import io.kubernetes.client.models.V1ObjectReference;
import io.kubernetes.client.models.V1PersistentVolume;
import io.kubernetes.client.models.V1PersistentVolumeClaim;
import io.kubernetes.client.models.V1PersistentVolumeClaimList;
import io.kubernetes.client.models.V1PersistentVolumeList;
import io.kubernetes.client.models.V1Pod;
import io.kubernetes.client.models.V1PodList;
import io.kubernetes.client.models.V1Service;
import io.kubernetes.client.models.V1ServiceList;
import io.kubernetes.client.models.V1Status;
import io.kubernetes.client.models.V1beta1Ingress;
import io.kubernetes.client.models.V1beta1IngressList;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicBoolean;
import oracle.kubernetes.TestUtils;
import oracle.kubernetes.operator.builders.StubWatchFactory;
import oracle.kubernetes.operator.helpers.AsyncCallTestSupport;
import oracle.kubernetes.operator.helpers.DomainPresenceInfo;
import oracle.kubernetes.operator.helpers.LegalNames;
import oracle.kubernetes.operator.helpers.ServerKubernetesObjects;
import oracle.kubernetes.operator.work.ThreadFactorySingleton;
import oracle.kubernetes.weblogic.domain.v2.Domain;
import oracle.kubernetes.weblogic.domain.v2.DomainList;
import oracle.kubernetes.weblogic.domain.v2.DomainSpec;
import org.joda.time.DateTime;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

@SuppressWarnings("SameParameterValue")
public class DomainPresenceTest extends ThreadFactoryTestBase {

  private static final String NS = "default";
  private static final String UID = "UID1";
  private final DomainList domains = createEmptyDomainList();
  private final V1beta1IngressList ingresses = createEmptyIngressList();
  private final V1ServiceList services = createEmptyServiceList();
  private final V1EventList events = createEmptyEventList();
  private final V1PodList pods = createEmptyPodList();
  private final V1ConfigMap domainConfigMap = createEmptyConfigMap();
  private final V1PersistentVolumeList persistentVolumes = createEmptyPersistentVolumeList();
  private final V1PersistentVolumeClaimList claims = createEmptyPersistentVolumeClaimList();

  private List<Memento> mementos = new ArrayList<>();
  private AsyncCallTestSupport testSupport = new AsyncCallTestSupport();
  private Map<String, AtomicBoolean> isNamespaceStopping;

  @Before
  public void setUp() throws Exception {
    mementos.add(TestUtils.silenceOperatorLogger());
    mementos.add(testSupport.installRequestStepFactory());
    mementos.add(ClientFactoryStub.install());
    mementos.add(StubWatchFactory.install());
    mementos.add(installStub(ThreadFactorySingleton.class, "INSTANCE", this));
    mementos.add(
        installStub(DomainProcessorImpl.class, "FIBER_GATE", testSupport.createFiberGateStub()));
    testSupport.addContainerComponent("TF", ThreadFactory.class, this);

    getStartedVariable();
    isNamespaceStopping = getStoppingVariable();
    isNamespaceStopping.computeIfAbsent(NS, k -> new AtomicBoolean(true)).set(true);
  }

  private static Memento installStub(Class<?> containingClass, String fieldName, Object newValue)
      throws NoSuchFieldException {
    return StaticStubSupport.install(containingClass, fieldName, newValue);
  }

  private Map<String, AtomicBoolean> getStartedVariable() throws NoSuchFieldException {
    Memento startedMemento = StaticStubSupport.preserve(Main.class, "isNamespaceStarted");
    return startedMemento.getOriginalValue();
  }

  private Map<String, AtomicBoolean> getStoppingVariable() throws NoSuchFieldException {
    Memento stoppingMemento = StaticStubSupport.preserve(Main.class, "isNamespaceStopping");
    return stoppingMemento.getOriginalValue();
  }

  @After
  public void tearDown() throws Exception {
    isNamespaceStopping.computeIfAbsent(NS, k -> new AtomicBoolean(true)).set(true);
    shutDownThreads();

    for (Memento memento : mementos) memento.revert();

    testSupport.throwOnCompletionFailure();
  }

  public abstract static class DomainProcessorStub implements DomainProcessor {
    private final Map<String, DomainPresenceInfo> dpis = new HashMap<>();

    public Map<String, DomainPresenceInfo> getDomainPresenceInfos() {
      return dpis;
    }

    @Override
    public void makeRightDomainPresence(
        DomainPresenceInfo info,
        boolean explicitRecheck,
        boolean isDeleting,
        boolean isWillInterrupt) {
      dpis.put(info.getDomainUID(), info);
    }
  }

  @Test
  public void whenNoPreexistingDomains_createEmptyDomainPresenceInfoMap() {
    DomainProcessorStub dp = Stub.createStub(DomainProcessorStub.class);
    testSupport.addComponent("DP", DomainProcessor.class, dp);

    readExistingResources();

    assertThat(dp.getDomainPresenceInfos(), is(anEmptyMap()));
  }

  private void readExistingResources() {
    createCannedListDomainResponses();
    testSupport.runStepsToCompletion(Main.readExistingResources("operator", NS));
  }

  @Test
  public void whenK8sHasOneDomainWithAssociatedIngress_readIt() {
    addDomainResource(UID, NS);
    addIngressResource(UID, NS, "cluster1");

    DomainProcessorStub dp = Stub.createStub(DomainProcessorStub.class);
    testSupport.addComponent("DP", DomainProcessor.class, dp);

    readExistingResources();

    assertThat(
        dp.getDomainPresenceInfos(),
        hasValue(domain(UID).withNamespace(NS).withIngressForCluster("cluster1")));
  }

  private void addDomainResource(String uid, String namespace) {
    domains.getItems().add(createDomain(uid, namespace));
  }

  private Domain createDomain(String uid, String namespace) {
    return new Domain()
        .withSpec(new DomainSpec().withDomainUID(uid))
        .withMetadata(
            new V1ObjectMeta()
                .namespace(namespace)
                .resourceVersion("1")
                .creationTimestamp(DateTime.now()));
  }

  private void addIngressResource(String uid, String namespace, String clusterName) {
    ingresses.getItems().add(createIngress(uid, namespace, clusterName));
  }

  private V1beta1Ingress createIngress(String uid, String namespace, String clusterName) {
    return new V1beta1Ingress().metadata(createIngressMetaData(uid, namespace, clusterName));
  }

  private V1ObjectMeta createIngressMetaData(String uid, String namespace, String clusterName) {
    return new V1ObjectMeta()
        .name(LegalNames.toIngressName(uid, clusterName))
        .namespace(namespace)
        .labels(createMap(DOMAINUID_LABEL, uid, CLUSTERNAME_LABEL, clusterName));
  }

  private Map<String, String> createMap(String key1, String value1) {
    Map<String, String> map = new HashMap<>();
    map.put(key1, value1);
    return map;
  }

  private Map<String, String> createMap(String key1, String value1, String key2, String value2) {
    Map<String, String> map = new HashMap<>();
    map.put(key1, value1);
    map.put(key2, value2);
    return map;
  }

  @Test
  public void whenK8sHasOneDomainWithChannelService_createSkoEntry() {
    addDomainResource(UID, NS);
    V1Service serviceResource = addServiceResource(UID, NS, "admin", "channel1");

    DomainProcessorStub dp = Stub.createStub(DomainProcessorStub.class);
    testSupport.addComponent("DP", DomainProcessor.class, dp);

    readExistingResources();

    String serverName = "admin";
    assertThat(
        getServerKubernetesObjects(dp, UID, serverName).getChannels(),
        hasEntry(equalTo("channel1"), sameInstance(serviceResource)));
  }

  private ServerKubernetesObjects getServerKubernetesObjects(
      DomainProcessorStub dp, String uid, String serverName) {
    return dp.getDomainPresenceInfos().get(uid).getServers().get(serverName);
  }

  private V1Service addServiceResource(
      String uid, String namespace, String serverName, String channelName) {
    V1Service service = createService(uid, namespace, serverName, channelName);
    services.getItems().add(service);
    return service;
  }

  private V1Service createService(
      String uid, String namespace, String serverName, String channelName) {
    V1ObjectMeta metadata = createServerMetadata(uid, namespace, serverName);
    metadata.putLabelsItem(CHANNELNAME_LABEL, channelName);
    return new V1Service().metadata(metadata);
  }

  private V1ObjectMeta createMetadata(String uid, String namespace, String name) {
    return createMetadata(uid, name).namespace(namespace);
  }

  private V1ObjectMeta createMetadata(String uid, String name) {
    return new V1ObjectMeta().name(name).labels(createMap(DOMAINUID_LABEL, uid));
  }

  private V1ObjectMeta createServerMetadata(String uid, String namespace, String serverName) {
    return createServerMetadata(uid, serverName).namespace(namespace);
  }

  private V1ObjectMeta createServerMetadata(String uid, String name) {
    return new V1ObjectMeta()
        .name(LegalNames.toServerName(uid, name))
        .labels(createMap(DOMAINUID_LABEL, uid, SERVERNAME_LABEL, name));
  }

  private void addPersistentVolumeResource(String uid, String name) {
    V1PersistentVolume volume = new V1PersistentVolume().metadata(createMetadata(uid, name));
    persistentVolumes.getItems().add(volume);
  }

  private void addPersistentVolumeClaimResource(String uid, String namespace, String name) {
    V1PersistentVolumeClaim claim =
        new V1PersistentVolumeClaim().metadata(createMetadata(uid, namespace, name));
    claims.getItems().add(claim);
  }

  @Test
  public void whenK8sHasOneDomainWithoutChannelService_createSkoEntry() {
    addDomainResource(UID, NS);
    V1Service serviceResource = addServiceResource(UID, NS, "admin");

    DomainProcessorStub dp = Stub.createStub(DomainProcessorStub.class);
    testSupport.addComponent("DP", DomainProcessor.class, dp);

    readExistingResources();

    assertThat(
        getServerKubernetesObjects(dp, UID, "admin").getService().get(), equalTo(serviceResource));
  }

  private V1Service addServiceResource(String uid, String namespace, String serverName) {
    V1Service service = createService(uid, namespace, serverName);
    services.getItems().add(service);
    return service;
  }

  private V1Service createService(String uid, String namespace, String serverName) {
    return new V1Service().metadata(createServerMetadata(uid, namespace, serverName));
  }

  @Test
  public void whenK8sHasOneDomainWithPod_createSkoEntry() {
    addDomainResource(UID, NS);
    V1Pod podResource = addPodResource(UID, NS, "admin");

    DomainProcessorStub dp = Stub.createStub(DomainProcessorStub.class);
    testSupport.addComponent("DP", DomainProcessor.class, dp);

    readExistingResources();

    assertThat(getServerKubernetesObjects(dp, UID, "admin").getPod().get(), equalTo(podResource));
  }

  private V1Pod addPodResource(String uid, String namespace, String serverName) {
    V1Pod pod = createPodResource(uid, namespace, serverName);
    pods.getItems().add(pod);
    return pod;
  }

  private V1Pod createPodResource(String uid, String namespace, String serverName) {
    return new V1Pod().metadata(createServerMetadata(uid, namespace, serverName));
  }

  @Test
  @Ignore("Don't process events during read of existing resources")
  public void whenK8sHasOneDomainWithNotReadyEvent_updateLastKnownStatus() {
    addDomainResource(UID, NS);
    addPodResource(UID, NS, "admin");
    addEventResource(UID, "admin", READINESS_PROBE_NOT_READY_STATE + "do something!");

    DomainProcessorStub dp = Stub.createStub(DomainProcessorStub.class);
    testSupport.addComponent("DP", DomainProcessor.class, dp);

    readExistingResources();

    assertThat(
        getServerKubernetesObjects(dp, UID, "admin").getLastKnownStatus().get(),
        equalTo("do something!"));
  }

  @Test
  public void whenK8sHasOneDomainWithOtherEvent_ignoreIt() {
    addDomainResource(UID, NS);
    addPodResource(UID, NS, "admin");
    addEventResource(UID, "admin", "ignore this event");

    DomainProcessorStub dp = Stub.createStub(DomainProcessorStub.class);
    testSupport.addComponent("DP", DomainProcessor.class, dp);

    readExistingResources();

    assertThat(
        getServerKubernetesObjects(dp, UID, "admin").getLastKnownStatus().get(), nullValue());
  }

  private void addEventResource(String uid, String serverName, String message) {
    events.getItems().add(createEventResource(uid, serverName, message));
  }

  private V1Event createEventResource(String uid, String serverName, String message) {
    return new V1Event()
        .involvedObject(new V1ObjectReference().name(LegalNames.toServerName(uid, serverName)))
        .message(message);
  }

  @SuppressWarnings("unchecked")
  private void createCannedListDomainResponses() {
    testSupport.createCannedResponse("listDomain").withNamespace(NS).returning(domains);
    testSupport
        .createCannedResponse("listIngress")
        .withNamespace(NS)
        .withLabelSelectors(LabelConstants.DOMAINUID_LABEL, CREATEDBYOPERATOR_LABEL)
        .returning(ingresses);
    testSupport
        .createCannedResponse("listService")
        .withNamespace(NS)
        .withLabelSelectors(LabelConstants.DOMAINUID_LABEL, CREATEDBYOPERATOR_LABEL)
        .returning(services);
    testSupport
        .createCannedResponse("listEvent")
        .withNamespace(NS)
        .withFieldSelector(Main.READINESS_PROBE_FAILURE_EVENT_FILTER)
        .returning(events);
    testSupport
        .createCannedResponse("listPod")
        .withNamespace(NS)
        .withLabelSelectors(LabelConstants.DOMAINUID_LABEL, CREATEDBYOPERATOR_LABEL)
        .returning(pods);
    testSupport
        .createCannedResponse("readConfigMap")
        .withNamespace(NS)
        .withName(DOMAIN_CONFIG_MAP_NAME)
        .returning(domainConfigMap);
    testSupport
        .createCannedResponse("replaceConfigMap")
        .withNamespace(NS)
        .withName(DOMAIN_CONFIG_MAP_NAME)
        .ignoringBody()
        .returning(domainConfigMap);
  }

  private DomainList createEmptyDomainList() {
    return new DomainList().withMetadata(createListMetadata());
  }

  private V1ListMeta createListMetadata() {
    return new V1ListMeta().resourceVersion("1");
  }

  private V1beta1IngressList createEmptyIngressList() {
    return new V1beta1IngressList().metadata(createListMetadata());
  }

  private V1ServiceList createEmptyServiceList() {
    return new V1ServiceList().metadata(createListMetadata());
  }

  private V1EventList createEmptyEventList() {
    return new V1EventList().metadata(createListMetadata());
  }

  private V1PodList createEmptyPodList() {
    return new V1PodList().metadata(createListMetadata());
  }

  private V1ConfigMap createEmptyConfigMap() {
    return new V1ConfigMap().metadata(createObjectMetaData()).data(new HashMap<>());
  }

  private V1ObjectMeta createObjectMetaData() {
    return new V1ObjectMeta().resourceVersion("1");
  }

  private V1PersistentVolumeList createEmptyPersistentVolumeList() {
    return new V1PersistentVolumeList().metadata(createListMetadata());
  }

  private V1PersistentVolumeClaimList createEmptyPersistentVolumeClaimList() {
    return new V1PersistentVolumeClaimList().metadata(createListMetadata());
  }

  @SuppressWarnings("unchecked")
  @Test
  public void whenStrandedResourcesExist_removeThem() {
    addIngressResource(UID, NS, "cluster1");
    addIngressResource(UID, NS, "cluster2");
    addServiceResource(UID, NS, "admin");
    addServiceResource(UID, NS, "ms1", "channel1");
    addPersistentVolumeResource(UID, "volume1");
    addPersistentVolumeClaimResource(UID, NS, "claim1");

    testSupport
        .createCannedResponse("deleteService")
        .withNamespace(NS)
        .withName(LegalNames.toServerName(UID, "admin"))
        .ignoringBody()
        .returning(new V1Status());
    testSupport
        .createCannedResponse("deleteService")
        .withNamespace(NS)
        .withName(LegalNames.toServerName(UID, "ms1"))
        .ignoringBody()
        .returning(new V1Status());

    testSupport
        .createCannedResponse("listService")
        .withNamespace(NS)
        .withLabelSelectors(forDomainUid(UID), CREATEDBYOPERATOR_LABEL)
        .returning(services);

    testSupport
        .createCannedResponse("deleteCollection")
        .withNamespace(NS)
        .withLabelSelectors(forDomainUid(UID), CREATEDBYOPERATOR_LABEL)
        .returning(new V1Status());

    testSupport
        .createCannedResponse("listIngress")
        .withNamespace(NS)
        .withLabelSelectors(forDomainUid(UID), CREATEDBYOPERATOR_LABEL)
        .returning(ingresses);
    testSupport
        .createCannedResponse("deleteIngress")
        .withNamespace(NS)
        .withName(LegalNames.toIngressName(UID, "cluster1"))
        .ignoringBody()
        .returning(new V1Status());
    testSupport
        .createCannedResponse("deleteIngress")
        .withNamespace(NS)
        .withName(LegalNames.toIngressName(UID, "cluster2"))
        .ignoringBody()
        .returning(new V1Status());

    testSupport
        .createCannedResponse("listPersistentVolume")
        .withLabelSelectors(forDomainUid(UID), CREATEDBYOPERATOR_LABEL)
        .returning(persistentVolumes);
    testSupport
        .createCannedResponse("deletePersistentVolume")
        .withName("volume1")
        .ignoringBody()
        .returning(new V1Status());

    testSupport
        .createCannedResponse("listPersistentVolumeClaim")
        .withLabelSelectors(forDomainUid(UID), CREATEDBYOPERATOR_LABEL)
        .withNamespace(NS)
        .returning(claims);
    testSupport
        .createCannedResponse("deletePersistentVolumeClaim")
        .withNamespace(NS)
        .withName("claim1")
        .ignoringBody()
        .returning(new V1Status());

    isNamespaceStopping.get(NS).set(false);

    readExistingResources();

    testSupport.verifyAllDefinedResponsesInvoked();
  }
}

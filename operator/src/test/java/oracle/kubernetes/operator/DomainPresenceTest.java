// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator;

import static com.meterware.simplestub.Stub.createStub;
import static oracle.kubernetes.operator.DomainPresenceInfoMatcher.domain;
import static oracle.kubernetes.operator.KubernetesConstants.DOMAIN_CONFIG_MAP_NAME;
import static oracle.kubernetes.operator.LabelConstants.CHANNELNAME_LABEL;
import static oracle.kubernetes.operator.LabelConstants.CLUSTERNAME_LABEL;
import static oracle.kubernetes.operator.LabelConstants.DOMAINUID_LABEL;
import static oracle.kubernetes.operator.LabelConstants.SERVERNAME_LABEL;
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
import io.kubernetes.client.ApiClient;
import io.kubernetes.client.models.V1ConfigMap;
import io.kubernetes.client.models.V1EventList;
import io.kubernetes.client.models.V1ListMeta;
import io.kubernetes.client.models.V1ObjectMeta;
import io.kubernetes.client.models.V1PodList;
import io.kubernetes.client.models.V1Service;
import io.kubernetes.client.models.V1ServiceList;
import io.kubernetes.client.models.V1beta1Ingress;
import io.kubernetes.client.models.V1beta1IngressList;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import oracle.kubernetes.TestUtils;
import oracle.kubernetes.operator.builders.StubWatchFactory;
import oracle.kubernetes.operator.helpers.ClientFactory;
import oracle.kubernetes.operator.helpers.ClientPool;
import oracle.kubernetes.operator.helpers.DomainPresenceInfo;
import oracle.kubernetes.operator.helpers.DomainPresenceInfoManager;
import oracle.kubernetes.operator.helpers.LegalNames;
import oracle.kubernetes.operator.helpers.ServerKubernetesObjectsManager;
import oracle.kubernetes.operator.work.AsyncCallTestSupport;
import oracle.kubernetes.weblogic.domain.v1.Domain;
import oracle.kubernetes.weblogic.domain.v1.DomainList;
import oracle.kubernetes.weblogic.domain.v1.DomainSpec;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

@SuppressWarnings("SameParameterValue")
public class DomainPresenceTest {

  private static final String NS = "default";
  private final DomainList domains = createEmptyDomainList();
  private final V1beta1IngressList ingresses = createEmptyIngressList();
  private final V1ServiceList services = createEmptyServiceList();
  private final V1EventList events = createEmptyEventList();
  private final V1PodList pods = createEmptyPodList();
  private final V1ConfigMap domainConfigMap = createEmptyConfigMap();

  private AtomicBoolean stopping;

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

  private List<Memento> mementos = new ArrayList<>();
  private AsyncCallTestSupport testSupport = new AsyncCallTestSupport();

  @Before
  public void setUp() throws Exception {
    mementos.add(TestUtils.silenceOperatorLogger());
    mementos.add(
        StaticStubSupport.install(
            DomainPresenceInfoManager.class, "domains", new ConcurrentHashMap<>()));
    mementos.add(
        StaticStubSupport.install(
            ServerKubernetesObjectsManager.class, "serverMap", new ConcurrentHashMap<>()));
    mementos.add(testSupport.installRequestStepFactory());
    mementos.add(ClientFactoryStub.install());
    mementos.add(StubWatchFactory.install());

    stopping = getStoppingVariable();
  }

  private AtomicBoolean getStoppingVariable() throws NoSuchFieldException {
    Memento stoppingMemento = StaticStubSupport.preserve(Main.class, "stopping");
    return stoppingMemento.getOriginalValue();
  }

  @After
  public void tearDown() throws Exception {
    stopping.set(true);

    for (Memento memento : mementos) memento.revert();

    testSupport.throwOnCompletionFailure();
  }

  @Test
  public void whenNoPreexistingDomains_createEmptyDomainPresenceInfoMap() throws Exception {
    readExistingResources();

    assertThat(Main.getDomainPresenceInfos(), is(anEmptyMap()));
  }

  private void readExistingResources() {
    createCannedListDomainResponses();
    testSupport.runSteps(Main.readExistingResources("operator", NS));
  }

  @Test
  public void whenK8sHasOneDomainWithAssociatedIngress_readIt() throws Exception {
    addDomainResource("UID1", "ns1");
    addIngressResource("UID1", "cluster1");

    readExistingResources();

    assertThat(
        Main.getDomainPresenceInfos(),
        hasValue(domain("UID1").withNamespace("ns1").withIngressForCluster("cluster1")));
  }

  private void addDomainResource(String uid, String namespace) {
    domains.getItems().add(createDomain(uid, namespace));
  }

  private Domain createDomain(String uid, String namespace) {
    return new Domain()
        .withSpec(new DomainSpec().withDomainUID(uid))
        .withMetadata(new V1ObjectMeta().namespace(namespace));
  }

  private void addIngressResource(String uid, String clusterName) {
    ingresses.getItems().add(createIngress(uid, clusterName));
  }

  private V1beta1Ingress createIngress(String uid, String clusterName) {
    return new V1beta1Ingress().metadata(createIngressMetaData(uid, clusterName));
  }

  private V1ObjectMeta createIngressMetaData(String uid, String clusterName) {
    return new V1ObjectMeta()
        .labels(createMap(DOMAINUID_LABEL, uid, CLUSTERNAME_LABEL, clusterName));
  }

  private Map<String, String> createMap(String key1, String value1, String key2, String value2) {
    Map<String, String> map = new HashMap<>();
    map.put(key1, value1);
    map.put(key2, value2);
    return map;
  }

  @Test
  public void whenK8sHasOneDomainWithChannelService_createSkoEntry() throws Exception {
    addDomainResource("UID1", "ns1");
    V1Service serviceResource = addServiceResource("UID1", "admin", "channel1");

    readExistingResources();

    assertThat(
        Main.getKubernetesObjects(LegalNames.toServerName("UID1", "admin")).getChannels(),
        hasEntry(equalTo("channel1"), sameInstance(serviceResource)));
  }

  private V1Service addServiceResource(String uid, String serverName, String channelName) {
    V1Service service = createService(uid, serverName, channelName);
    services.getItems().add(service);
    return service;
  }

  private V1Service createService(String uid, String serverName, String channelName) {
    V1ObjectMeta metadata = createServiceMetadata(uid, serverName);
    metadata.putLabelsItem(CHANNELNAME_LABEL, channelName);
    return new V1Service().metadata(metadata);
  }

  private V1ObjectMeta createServiceMetadata(String uid, String serverName) {
    return new V1ObjectMeta().labels(createMap(DOMAINUID_LABEL, uid, SERVERNAME_LABEL, serverName));
  }

  @Test
  @Ignore("running into a problem with the map")
  public void whenK8sHasOneDomainWithoutChannelService_createSkoEntry() throws Exception {
    addDomainResource("UID1", "ns1");
    V1Service serviceResource = addServiceResource("UID1", "admin");

    readExistingResources();

    assertThat(
        Main.getKubernetesObjects(LegalNames.toServerName("UID1", "admin")).getService(),
        equalTo(serviceResource));
  }

  private V1Service addServiceResource(String uid, String serverName) {
    V1Service service = createService(uid, serverName);
    services.getItems().add(service);
    return service;
  }

  private V1Service createService(String uid, String serverName) {
    return new V1Service().metadata(createServiceMetadata(uid, serverName));
  }

  @SuppressWarnings("unchecked")
  private void createCannedListDomainResponses() {
    testSupport.createCannedResponse("listDomain").withNamespace(NS).returning(domains);
    testSupport.createCannedResponse("listIngress").withNamespace(NS).returning(ingresses);
    testSupport.createCannedResponse("listService").withNamespace(NS).returning(services);
    testSupport.createCannedResponse("listEvent").withNamespace(NS).returning(events);
    testSupport.createCannedResponse("listPod").withNamespace(NS).returning(pods);
    testSupport
        .createCannedResponse("readConfigMap")
        .withNamespace(NS)
        .withName(DOMAIN_CONFIG_MAP_NAME)
        .returning(domainConfigMap);
    testSupport
        .createCannedResponse("replaceConfigMap")
        .withNamespace(NS)
        .withName(DOMAIN_CONFIG_MAP_NAME)
        .returning(domainConfigMap);
  }

  @Test
  public void afterCancelDomainStatusUpdating_statusUpdaterIsNull() throws Exception {
    DomainPresenceInfo info = DomainPresenceInfoManager.getOrCreate("namespace", "domainUID");
    info.getStatusUpdater().getAndSet(createStub(ScheduledFuture.class));

    DomainPresenceControl.cancelDomainStatusUpdating(info);

    assertThat(info.getStatusUpdater().get(), nullValue());
  }

  static class ClientFactoryStub implements ClientFactory {

    static Memento install() throws NoSuchFieldException {
      return StaticStubSupport.install(ClientPool.class, "FACTORY", new ClientFactoryStub());
    }

    @Override
    public ApiClient get() {
      return new ApiClient();
    }
  }
}

// Copyright (c) 2018, 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import com.meterware.simplestub.Memento;
import com.meterware.simplestub.StaticStubSupport;
import io.kubernetes.client.openapi.models.CoreV1Event;
import io.kubernetes.client.openapi.models.V1LocalObjectReference;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1ObjectReference;
import io.kubernetes.client.openapi.models.V1PersistentVolume;
import io.kubernetes.client.openapi.models.V1PersistentVolumeClaim;
import io.kubernetes.client.openapi.models.V1Pod;
import io.kubernetes.client.openapi.models.V1Service;
import oracle.kubernetes.operator.builders.StubWatchFactory;
import oracle.kubernetes.operator.helpers.DomainPresenceInfo;
import oracle.kubernetes.operator.helpers.KubernetesTestSupport;
import oracle.kubernetes.operator.helpers.LegalNames;
import oracle.kubernetes.operator.helpers.OperatorServiceType;
import oracle.kubernetes.operator.tuning.TuningParametersStub;
import oracle.kubernetes.operator.work.ThreadFactorySingleton;
import oracle.kubernetes.utils.SystemClock;
import oracle.kubernetes.utils.TestUtils;
import oracle.kubernetes.weblogic.domain.DomainConfigurator;
import oracle.kubernetes.weblogic.domain.DomainConfiguratorFactory;
import oracle.kubernetes.weblogic.domain.model.ClusterResource;
import oracle.kubernetes.weblogic.domain.model.ClusterSpec;
import oracle.kubernetes.weblogic.domain.model.DomainResource;
import oracle.kubernetes.weblogic.domain.model.DomainSpec;
import oracle.kubernetes.weblogic.domain.model.DomainStatus;
import org.hamcrest.MatcherAssert;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.meterware.simplestub.Stub.createStrictStub;
import static com.meterware.simplestub.Stub.createStub;
import static oracle.kubernetes.operator.LabelConstants.SERVERNAME_LABEL;
import static oracle.kubernetes.operator.helpers.KubernetesTestSupport.CLUSTER;
import static org.hamcrest.Matchers.anEmptyMap;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.junit.MatcherAssert.assertThat;

@SuppressWarnings("SameParameterValue")
class DomainPresenceTest extends ThreadFactoryTestBase {

  private static final String NS = "default";
  private static final String UID1 = "UID1";
  private static final String UID2 = "UID2";
  private static final String UID3 = "UID3";
  // Call builder tuning
  public static final int CALL_REQUEST_LIMIT = 10;
  private static final int LAST_DOMAIN_NUM = 2 * CALL_REQUEST_LIMIT - 1;
  public static final String CLUSTER_1 = "cluster1";
  public static final String CLUSTER_2 = "cluster2";
  public static final String CLUSTER_3 = "cluster3";

  private final List<Memento> mementos = new ArrayList<>();
  private final KubernetesTestSupport testSupport = new KubernetesTestSupport();
  private final DomainProcessorStub dp = createStub(DomainProcessorStub.class);
  private final DomainNamespaces domainNamespaces = new DomainNamespaces(null);
  final DomainResource domain = createDomain(UID1, NS);

  @BeforeEach
  public void setUp() throws Exception {
    mementos.add(TestUtils.silenceOperatorLogger().withLogLevel(Level.OFF));
    mementos.add(testSupport.install());
    mementos.add(ClientFactoryStub.install());
    mementos.add(StubWatchFactory.install());
    mementos.add(StaticStubSupport.install(ThreadFactorySingleton.class, "instance", this));
    mementos.add(NoopWatcherStarter.install());
    mementos.add(TuningParametersStub.install());
  }

  @AfterEach
  public void tearDown() throws Exception {
    shutDownThreads();
    mementos.forEach(Memento::revert);
    testSupport.throwOnCompletionFailure();
  }

  @Test
  void whenNoPreexistingDomains_createEmptyDomainPresenceInfoMap() {
    testSupport.addComponent("DP", DomainProcessor.class, dp);
    testSupport.runSteps(domainNamespaces.readExistingResources(NS, dp));

    assertThat(dp.getDomainPresenceInfos(), is(anEmptyMap()));
  }

  @Test
  void whenPreexistingDomainExistsWithoutPodsOrServices_addToPresenceMap() {
    DomainResource domain = createDomain(UID1, NS);
    testSupport.defineResources(domain);

    testSupport.addComponent("DP", DomainProcessor.class, dp);
    testSupport.runSteps(domainNamespaces.readExistingResources(NS, dp));

    assertThat(getDomainPresenceInfo(dp, UID1).getDomain(), equalTo(domain));
  }

  @Test
  void whenDomainsDeletedButAlreadyInPresence_deleteFromPresenceMap() {
    DomainResource domain1 = createDomain(UID1, NS);
    DomainResource domain2 = createDomain(UID2, NS);
    DomainResource domain3 = createDomain(UID3, NS);
    testSupport.defineResources(domain1, domain2, domain3);

    testSupport.addComponent("DP", DomainProcessor.class, dp);
    testSupport.runSteps(domainNamespaces.readExistingResources(NS, dp));

    assertThat(getDomainPresenceInfoMap(dp).keySet(), hasSize(3));

    testSupport.deleteResources(domain2);

    testSupport.runSteps(domainNamespaces.readExistingResources(NS, dp));

    assertThat(getDomainPresenceInfoMap(dp).keySet(), hasSize(2));
    assertThat(getDomainPresenceInfoMap(dp), not(hasKey(UID2)));
  }

  private void addDomainResource(String uid, String namespace) {
    testSupport.defineResources(createDomain(uid, namespace));
  }

  private DomainResource createDomain(String uid, String namespace) {
    return new DomainResource()
        .withSpec(new DomainSpec().withDomainUid(uid))
        .withMetadata(
            new V1ObjectMeta()
                .namespace(namespace)
                .name(uid)
                .resourceVersion("1")
                .creationTimestamp(SystemClock.now()))
        .withStatus(new DomainStatus());
  }

  @Test
  void whenClustersMatchDomain_addToDomainPresenceInfo() {
    DomainResource domain = createDomain(UID1, NS);
    ClusterResource cluster1 = createClusterResource(NS, CLUSTER_1);
    ClusterResource cluster2 = createClusterResource(NS, CLUSTER_2);
    ClusterResource cluster3 = createClusterResource("ns2", CLUSTER_3);
    testSupport.defineResources(domain, cluster1, cluster2, cluster3);

    testSupport.addComponent("DP", DomainProcessor.class, dp);
    testSupport.runSteps(domainNamespaces.readExistingResources(NS, dp));

    DomainPresenceInfo info = getDomainPresenceInfo(dp, UID1);
    DomainConfigurator configurator = DomainConfiguratorFactory.forDomain(domain);
    configurator.configureCluster(info, CLUSTER_1);
    configurator.configureCluster(info, CLUSTER_2);

    MatcherAssert.assertThat(info.getClusterResource(CLUSTER_1), notNullValue());
    MatcherAssert.assertThat(info.getClusterResource(CLUSTER_2), notNullValue());
    MatcherAssert.assertThat(info.getClusterResource(CLUSTER_3), nullValue());
  }

  @Test
  void whenClusterResourceDeletedButAlreadyInPresence_deleteFromPresenceMap() {
    testSupport.addComponent("DP", DomainProcessor.class, dp);
    for (String clusterName : List.of(CLUSTER_1, CLUSTER_2, CLUSTER_3)) {
      testSupport.defineResources(createClusterResource(NS, clusterName));
      domain.getSpec().getClusters().add(new V1LocalObjectReference().name(clusterName));
    }
    testSupport.defineResources(domain);

    testSupport.runSteps(domainNamespaces.readExistingResources(NS, dp));
    testSupport.deleteResources(
        testSupport.<ClusterResource>getResourceWithName(CLUSTER, CLUSTER_2));
    testSupport.runSteps(domainNamespaces.readExistingResources(NS, dp));

    DomainPresenceInfo info = getDomainPresenceInfo(dp, UID1);
    MatcherAssert.assertThat(info.getClusterResource(CLUSTER_1), notNullValue());
    MatcherAssert.assertThat(info.getClusterResource(CLUSTER_2), nullValue());
    MatcherAssert.assertThat(info.getClusterResource(CLUSTER_3), notNullValue());
  }

  @Test
  void whenMultipleClustersMatchTwoDomains_addToDomainPresenceInfo() {
    for (String clusterName : List.of(CLUSTER_1, CLUSTER_2, CLUSTER_3)) {
      testSupport.defineResources(createClusterResource(NS, clusterName));
      domain.getSpec().getClusters().add(new V1LocalObjectReference().name(clusterName));
    }

    DomainResource domain2 = createDomain(UID2, NS);
    domain2.getSpec().getClusters().add(new V1LocalObjectReference().name(CLUSTER_2));
    testSupport.defineResources(domain, domain2);
    testSupport.addComponent("DP", DomainProcessor.class, dp);

    testSupport.runSteps(domainNamespaces.readExistingResources(NS, dp));

    DomainPresenceInfo info = getDomainPresenceInfo(dp, UID1);
    MatcherAssert.assertThat(info.getClusterResource(CLUSTER_1), notNullValue());
    MatcherAssert.assertThat(info.getClusterResource(CLUSTER_2), notNullValue());
    MatcherAssert.assertThat(info.getClusterResource(CLUSTER_3), notNullValue());
    MatcherAssert.assertThat(getDomainPresenceInfo(dp, UID2).getClusterResource(CLUSTER_2), notNullValue());
  }

  // todo examine the recheck logic to see if we need to handle events
  // todo when cluster added, add to info
  // todo when cluster removed, remove from info
  // todo when cluster modified, update info

  // todo domain validation should use cluster resources, not cluster from domain spec
  // todo unit tests should create cluster resources, not cluster in domain spec
  // todo when upgrading from v8 domain, create cluster resource (WebHook) and tag as operator-created
  // todo when deleting domain, also delete any operator-created clusters

  // tell dp - here are the actual clusters that exist.
  // dp goes through its active domains, remove clusters no longer present?

  private ClusterResource createClusterResource(String namespace,
      String clusterName) {
    return new ClusterResource()
        .withMetadata(new V1ObjectMeta().namespace(namespace).name(clusterName))
        .spec(new ClusterSpec().withClusterName(clusterName));
  }

  private Map<String, DomainPresenceInfo> getDomainPresenceInfoMap(DomainProcessorStub dp) {
    return dp.getDomainPresenceInfos();
  }

  private DomainPresenceInfo getDomainPresenceInfo(DomainProcessorStub dp, String uid) {
    return dp.getDomainPresenceInfos().get(uid);
  }

  private V1Service createServerService(String uid, String namespace, String serverName) {
    V1ObjectMeta metadata =
        createNamespacedMetadata(uid, namespace)
            .name(LegalNames.toServerServiceName(uid, serverName))
            .putLabelsItem(SERVERNAME_LABEL, serverName);
    return OperatorServiceType.SERVER.withTypeLabel(new V1Service().metadata(metadata));
  }

  private V1ObjectMeta createServerMetadata(String uid, String namespace, String serverName) {
    return createNamespacedMetadata(uid, namespace)
        .name(LegalNames.toServerServiceName(uid, serverName))
        .putLabelsItem(SERVERNAME_LABEL, serverName);
  }

  private V1ObjectMeta createNamespacedMetadata(String uid, String namespace) {
    return createMetadata(uid).namespace(namespace);
  }

  private V1ObjectMeta createMetadata(String uid) {
    return new V1ObjectMeta()
        .putLabelsItem(LabelConstants.DOMAINUID_LABEL, uid)
        .putLabelsItem(LabelConstants.CREATEDBYOPERATOR_LABEL, "true");
  }

  private V1ObjectMeta createMetadata(String uid, String name) {
    return createMetadata(uid).name(name);
  }

  private V1ObjectMeta createMetadata(String uid, String namespace, String name) {
    return createNamespacedMetadata(uid, namespace).name(name);
  }

  @Test
  void whenK8sHasOneDomain_recordAdminServerService() {
    addDomainResource(UID1, NS);
    V1Service service = createServerService(UID1, NS, "admin");
    testSupport.defineResources(service);

    testSupport.addComponent("DP", DomainProcessor.class, dp);
    testSupport.runSteps(domainNamespaces.readExistingResources(NS, dp));

    assertThat(getDomainPresenceInfo(dp, UID1).getServerService("admin"), equalTo(service));
  }

  @Test
  void whenK8sHasOneDomainWithPod_recordPodPresence() {
    addDomainResource(UID1, NS);
    V1Pod pod = createPodResource(UID1, NS, "admin");
    testSupport.defineResources(pod);

    testSupport.addComponent("DP", DomainProcessor.class, dp);
    testSupport.runSteps(domainNamespaces.readExistingResources(NS, dp));

    assertThat(getDomainPresenceInfo(dp, UID1).getServerPod("admin"), equalTo(pod));
  }

  private V1Pod createPodResource(String uid, String namespace, String serverName) {
    return new V1Pod().metadata(createServerMetadata(uid, namespace, serverName));
  }

  private void addPodResource(String uid, String namespace, String serverName) {
    testSupport.defineResources(createPodResource(uid, namespace, serverName));
  }

  @Test
  void whenK8sHasOneDomainWithOtherEvent_ignoreIt() {
    addDomainResource(UID1, NS);
    addPodResource(UID1, NS, "admin");
    addEventResource(UID1, "admin", "ignore this event");

    testSupport.addComponent("DP", DomainProcessor.class, dp);
    testSupport.runSteps(domainNamespaces.readExistingResources(NS, dp));

    assertThat(getDomainPresenceInfo(dp, UID1).getLastKnownServerStatus("admin"), nullValue());
  }

  private void addEventResource(String uid, String serverName, String message) {
    testSupport.defineResources(createEventResource(uid, serverName, message));
  }

  private CoreV1Event createEventResource(String uid, String serverName, String message) {
    return new CoreV1Event()
        .metadata(createNamespacedMetadata(uid, NS))
        .involvedObject(new V1ObjectReference().name(LegalNames.toEventName(uid, serverName)))
        .message(message);
  }

  @Test
  void whenStrandedResourcesExist_removeThem() {
    V1Service service1 = createServerService(UID1, NS, "admin");
    V1Service service2 = createServerService(UID1, NS, "ms1");
    V1PersistentVolume volume = new V1PersistentVolume().metadata(createMetadata(UID1, "volume1"));
    V1PersistentVolumeClaim claim =
        new V1PersistentVolumeClaim().metadata(createMetadata(UID1, NS, "claim1"));
    testSupport.defineResources(service1, service2, volume, claim);

    testSupport.runSteps(domainNamespaces.readExistingResources(NS, dp));

    assertThat(dp.isDeletingStrandedResources(UID1), is(true));
  }

  @Test
  void dontRemoveNonStrandedResources() {
    createDomains(LAST_DOMAIN_NUM);
    V1Service service1 = createServerService("UID1", NS, "admin");
    V1Service service2 = createServerService("UID" + LAST_DOMAIN_NUM, NS, "admin");
    testSupport.defineResources(service1, service2);

    testSupport.runSteps(domainNamespaces.readExistingResources(NS, dp));

    assertThat(dp.isEstablishingDomain("UID1"), is(true));
    assertThat(dp.isEstablishingDomain("UID" + LAST_DOMAIN_NUM), is(true));
  }

  private void createDomains(int lastDomainNum) {
    IntStream.rangeClosed(1, lastDomainNum)
          .boxed()
          .map(i -> "UID" + i)
          .map(uid -> createDomain(uid, NS))
          .forEach(testSupport::defineResources);
  }

  public abstract static class DomainProcessorStub implements DomainProcessor {
    private final Map<String, DomainPresenceInfo> dpis = new ConcurrentHashMap<>();
    private final List<MakeRightDomainOperationStub> operationStubs = new ArrayList<>();

    Map<String, DomainPresenceInfo> getDomainPresenceInfos() {
      return dpis;
    }

    boolean isDeletingStrandedResources(String uid) {
      return Optional.ofNullable(getMakeRightOperations(uid))
            .map(MakeRightDomainOperationStub::isDeletingStrandedResources)
            .orElse(false);
    }

    @Override
    public Stream<DomainPresenceInfo> findStrandedDomainPresenceInfos(String namespace, Set<String> domainUids) {
      return dpis.entrySet().stream().filter(e -> !domainUids.contains(e.getKey())).map(Map.Entry::getValue);
    }

    private MakeRightDomainOperationStub getMakeRightOperations(String uid) {
      return operationStubs.stream().filter(s -> uid.equals(s.getUid())).findFirst().orElse(null);
    }

    boolean isEstablishingDomain(String uid) {
      return Optional.ofNullable(getMakeRightOperations(uid))
            .map(MakeRightDomainOperationStub::isEstablishingDomain)
            .orElse(false);
    }


    @Override
    public MakeRightDomainOperation createMakeRightOperation(DomainPresenceInfo liveInfo) {
      final MakeRightDomainOperationStub stub = createStrictStub(MakeRightDomainOperationStub.class, liveInfo, dpis);
      operationStubs.add(stub);
      return stub;
    }

    abstract static class MakeRightDomainOperationStub implements MakeRightDomainOperation {
      private final DomainPresenceInfo info;
      private final Map<String, DomainPresenceInfo> dpis;
      private boolean explicitRecheck;
      private boolean deleting;

      MakeRightDomainOperationStub(DomainPresenceInfo info, Map<String, DomainPresenceInfo> dpis) {
        this.info = info;
        this.dpis = dpis;
      }

      boolean isDeletingStrandedResources() {
        return explicitRecheck && deleting;
      }

      boolean isEstablishingDomain() {
        return explicitRecheck && !deleting;
      }

      String getUid() {
        return info.getDomainUid();
      }

      @Override
      public MakeRightDomainOperation withExplicitRecheck() {
        explicitRecheck = true;
        return this;
      }

      @Override
      public MakeRightDomainOperation forDeletion() {
        deleting = true;
        return this;
      }

      @Override
      public void execute() {
        if (deleting) {
          dpis.remove(info.getDomainUid());
        } else {
          dpis.put(info.getDomainUid(), info);
        }
      }
    }
  }
}

// Copyright (c) 2018, 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;

import com.meterware.simplestub.Memento;
import com.meterware.simplestub.StaticStubSupport;
import io.kubernetes.client.openapi.models.V1Event;
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
import oracle.kubernetes.operator.work.ThreadFactorySingleton;
import oracle.kubernetes.utils.TestUtils;
import oracle.kubernetes.weblogic.domain.model.Domain;
import oracle.kubernetes.weblogic.domain.model.DomainSpec;
import org.joda.time.DateTime;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static com.meterware.simplestub.Stub.createStrictStub;
import static com.meterware.simplestub.Stub.createStub;
import static oracle.kubernetes.operator.LabelConstants.SERVERNAME_LABEL;
import static org.hamcrest.Matchers.anEmptyMap;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.junit.MatcherAssert.assertThat;

@SuppressWarnings("SameParameterValue")
public class DomainPresenceTest extends ThreadFactoryTestBase {

  private static final String NS = "default";
  private static final String UID = "UID1";

  private final List<Memento> mementos = new ArrayList<>();
  private final KubernetesTestSupport testSupport = new KubernetesTestSupport();
  private Map<String, AtomicBoolean> namespaceStoppingMap;

  private static Memento installStub(Class<?> containingClass, String fieldName, Object newValue)
      throws NoSuchFieldException {
    return StaticStubSupport.install(containingClass, fieldName, newValue);
  }

  @Before
  public void setUp() throws Exception {
    mementos.add(TestUtils.silenceOperatorLogger().withLogLevel(Level.OFF));
    mementos.add(testSupport.install());
    mementos.add(ClientFactoryStub.install());
    mementos.add(StubWatchFactory.install());
    mementos.add(installStub(ThreadFactorySingleton.class, "INSTANCE", this));
    mementos.add(StaticStubSupport.install(Main.class, "engine", testSupport.getEngine()));
    mementos.add(NoopWatcherStarter.install());

    namespaceStoppingMap = getStoppingVariable();
    namespaceStoppingMap.computeIfAbsent(NS, k -> new AtomicBoolean(true)).set(true);
  }

  private Map<String, AtomicBoolean> getStoppingVariable() throws NoSuchFieldException {
    Memento stoppingMemento = StaticStubSupport.preserve(Main.class, "namespaceStoppingMap");
    return stoppingMemento.getOriginalValue();
  }

  @After
  public void tearDown() throws Exception {
    namespaceStoppingMap.computeIfAbsent(NS, k -> new AtomicBoolean(true)).set(true);
    shutDownThreads();
    mementos.forEach(Memento::revert);
    testSupport.throwOnCompletionFailure();
  }

  @Test
  public void whenNoPreexistingDomains_createEmptyDomainPresenceInfoMap() {
    DomainProcessorStub dp = createStub(DomainProcessorStub.class);
    testSupport.addComponent("DP", DomainProcessor.class, dp);

    readExistingResources();

    assertThat(dp.getDomainPresenceInfos(), is(anEmptyMap()));
  }

  private void readExistingResources() {
    testSupport.runStepsToCompletion(Main.readExistingResources("operator", NS));
  }

  private void addDomainResource(String uid, String namespace) {
    testSupport.defineResources(createDomain(uid, namespace));
  }

  private Domain createDomain(String uid, String namespace) {
    return new Domain()
        .withSpec(new DomainSpec().withDomainUid(uid))
        .withMetadata(
            new V1ObjectMeta()
                .namespace(namespace)
                .resourceVersion("1")
                .creationTimestamp(DateTime.now()));
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
  public void whenK8sHasOneDomain_recordAdminServerService() {
    addDomainResource(UID, NS);
    V1Service service = createServerService(UID, NS, "admin");
    testSupport.defineResources(service);

    DomainProcessorStub dp = createStub(DomainProcessorStub.class);
    testSupport.addComponent("DP", DomainProcessor.class, dp);

    readExistingResources();

    assertThat(getDomainPresenceInfo(dp, UID).getServerService("admin"), equalTo(service));
  }

  @Test
  public void whenK8sHasOneDomainWithPod_recordPodPresence() {
    addDomainResource(UID, NS);
    V1Pod pod = createPodResource(UID, NS, "admin");
    testSupport.defineResources(pod);

    DomainProcessorStub dp = createStub(DomainProcessorStub.class);
    testSupport.addComponent("DP", DomainProcessor.class, dp);

    readExistingResources();

    assertThat(getDomainPresenceInfo(dp, UID).getServerPod("admin"), equalTo(pod));
  }

  private V1Pod createPodResource(String uid, String namespace, String serverName) {
    return new V1Pod().metadata(createServerMetadata(uid, namespace, serverName));
  }

  private void addPodResource(String uid, String namespace, String serverName) {
    testSupport.defineResources(createPodResource(uid, namespace, serverName));
  }

  @Test
  public void whenK8sHasOneDomainWithOtherEvent_ignoreIt() {
    addDomainResource(UID, NS);
    addPodResource(UID, NS, "admin");
    addEventResource(UID, "admin", "ignore this event");

    DomainProcessorStub dp = createStub(DomainProcessorStub.class);
    testSupport.addComponent("DP", DomainProcessor.class, dp);

    readExistingResources();

    assertThat(getDomainPresenceInfo(dp, UID).getLastKnownServerStatus("admin"), nullValue());
  }

  private void addEventResource(String uid, String serverName, String message) {
    testSupport.defineResources(createEventResource(uid, serverName, message));
  }

  private V1Event createEventResource(String uid, String serverName, String message) {
    return new V1Event()
        .metadata(createNamespacedMetadata(uid, NS))
        .involvedObject(new V1ObjectReference().name(LegalNames.toEventName(uid, serverName)))
        .message(message);
  }

  @Test
  public void whenStrandedResourcesExist_removeThem() {
    V1Service service1 = createServerService(UID, NS, "admin");
    V1Service service2 = createServerService(UID, NS, "ms1");
    V1PersistentVolume volume = new V1PersistentVolume().metadata(createMetadata(UID, "volume1"));
    V1PersistentVolumeClaim claim =
        new V1PersistentVolumeClaim().metadata(createMetadata(UID, NS, "claim1"));
    testSupport.defineResources(service1, service2, volume, claim);

    namespaceStoppingMap.get(NS).set(false);

    readExistingResources();

    assertThat(testSupport.getResources(KubernetesTestSupport.SERVICE), empty());
  }

  public abstract static class DomainProcessorStub implements DomainProcessor {
    private final Map<String, DomainPresenceInfo> dpis = new HashMap<>();

    Map<String, DomainPresenceInfo> getDomainPresenceInfos() {
      return dpis;
    }


    @Override
    public MakeRightDomainOperation createMakeRightOperation(DomainPresenceInfo liveInfo) {
      return createStrictStub(MakeRightDomainOperationImpl.class, liveInfo, dpis);
    }

    abstract static class MakeRightDomainOperationImpl implements MakeRightDomainOperation {
      private final DomainPresenceInfo info;
      private final Map<String, DomainPresenceInfo> dpis;

      MakeRightDomainOperationImpl(DomainPresenceInfo info, Map<String, DomainPresenceInfo> dpis) {
        this.info = info;
        this.dpis = dpis;
      }

      @Override
      public MakeRightDomainOperation withExplicitRecheck() {
        return this;
      }

      @Override
      public MakeRightDomainOperation forDeletion() {
        return this;
      }

      @Override
      public void execute() {
        dpis.put(info.getDomainUid(), info);
      }
    }
  }
}

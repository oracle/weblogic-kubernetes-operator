// Copyright (c) 2018, 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Level;
import java.util.stream.IntStream;

import com.meterware.simplestub.Memento;
import com.meterware.simplestub.StaticStubSupport;
import io.kubernetes.client.openapi.models.CoreV1Event;
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
import oracle.kubernetes.operator.helpers.TuningParametersStub;
import oracle.kubernetes.operator.work.ThreadFactorySingleton;
import oracle.kubernetes.utils.TestUtils;
import oracle.kubernetes.weblogic.domain.model.Domain;
import oracle.kubernetes.weblogic.domain.model.DomainSpec;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.meterware.simplestub.Stub.createStrictStub;
import static com.meterware.simplestub.Stub.createStub;
import static oracle.kubernetes.operator.LabelConstants.SERVERNAME_LABEL;
import static oracle.kubernetes.operator.helpers.TuningParametersStub.CALL_REQUEST_LIMIT;
import static org.hamcrest.Matchers.anEmptyMap;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.junit.MatcherAssert.assertThat;

@SuppressWarnings("SameParameterValue")
public class DomainPresenceTest extends ThreadFactoryTestBase {

  private static final String NS = "default";
  private static final String UID = "UID1";
  private static final int LAST_DOMAIN_NUM = 2 * CALL_REQUEST_LIMIT - 1;

  private final List<Memento> mementos = new ArrayList<>();
  private final KubernetesTestSupport testSupport = new KubernetesTestSupport();
  private final DomainProcessorStub dp = createStub(DomainProcessorStub.class);
  private final DomainNamespaces domainNamespaces = new DomainNamespaces();

  @BeforeEach
  public void setUp() throws Exception {
    mementos.add(TestUtils.silenceOperatorLogger().withLogLevel(Level.OFF));
    mementos.add(testSupport.install());
    mementos.add(ClientFactoryStub.install());
    mementos.add(StubWatchFactory.install());
    mementos.add(StaticStubSupport.install(ThreadFactorySingleton.class, "INSTANCE", this));
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
  public void whenNoPreexistingDomains_createEmptyDomainPresenceInfoMap() {
    testSupport.addComponent("DP", DomainProcessor.class, dp);
    testSupport.runSteps(domainNamespaces.readExistingResources(NS, dp));

    assertThat(dp.getDomainPresenceInfos(), is(anEmptyMap()));
  }

  @Test
  public void whenPreexistingDomainExistsWithoutPodsOrServices_addToPresenceMap() {
    Domain domain = createDomain(UID, NS);
    testSupport.defineResources(domain);

    testSupport.addComponent("DP", DomainProcessor.class, dp);
    testSupport.runSteps(domainNamespaces.readExistingResources(NS, dp));

    assertThat(getDomainPresenceInfo(dp, UID).getDomain(), equalTo(domain));
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
                .name(uid)
                .resourceVersion("1")
                .creationTimestamp(OffsetDateTime.now()));
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

    testSupport.addComponent("DP", DomainProcessor.class, dp);
    testSupport.runSteps(domainNamespaces.readExistingResources(NS, dp));

    assertThat(getDomainPresenceInfo(dp, UID).getServerService("admin"), equalTo(service));
  }

  @Test
  public void whenK8sHasOneDomainWithPod_recordPodPresence() {
    addDomainResource(UID, NS);
    V1Pod pod = createPodResource(UID, NS, "admin");
    testSupport.defineResources(pod);

    testSupport.addComponent("DP", DomainProcessor.class, dp);
    testSupport.runSteps(domainNamespaces.readExistingResources(NS, dp));

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

    testSupport.addComponent("DP", DomainProcessor.class, dp);
    testSupport.runSteps(domainNamespaces.readExistingResources(NS, dp));

    assertThat(getDomainPresenceInfo(dp, UID).getLastKnownServerStatus("admin"), nullValue());
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
  public void whenStrandedResourcesExist_removeThem() {
    V1Service service1 = createServerService(UID, NS, "admin");
    V1Service service2 = createServerService(UID, NS, "ms1");
    V1PersistentVolume volume = new V1PersistentVolume().metadata(createMetadata(UID, "volume1"));
    V1PersistentVolumeClaim claim =
        new V1PersistentVolumeClaim().metadata(createMetadata(UID, NS, "claim1"));
    testSupport.defineResources(service1, service2, volume, claim);

    testSupport.runSteps(domainNamespaces.readExistingResources(NS, dp));

    assertThat(dp.isDeletingStrandedResources(UID), is(true));
  }

  @Test
  public void dontRemoveNonStrandedResources() {
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
    private final Map<String, DomainPresenceInfo> dpis = new HashMap<>();
    private final List<MakeRightDomainOperationStub> operationStubs = new ArrayList<>();

    Map<String, DomainPresenceInfo> getDomainPresenceInfos() {
      return dpis;
    }

    boolean isDeletingStrandedResources(String uid) {
      return Optional.ofNullable(getMakeRightOperations(uid))
            .map(MakeRightDomainOperationStub::isDeletingStrandedResources)
            .orElse(false);
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
        dpis.put(info.getDomainUid(), info);
      }
    }
  }
}

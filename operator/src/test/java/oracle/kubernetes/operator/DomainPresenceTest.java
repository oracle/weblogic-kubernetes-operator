// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator;

import static com.meterware.simplestub.Stub.createStub;
import static org.hamcrest.Matchers.anEmptyMap;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.junit.MatcherAssert.assertThat;

import com.meterware.simplestub.Memento;
import com.meterware.simplestub.StaticStubSupport;
import io.kubernetes.client.ApiClient;
import io.kubernetes.client.ApiException;
import io.kubernetes.client.models.V1ConfigMap;
import io.kubernetes.client.models.V1EventList;
import io.kubernetes.client.models.V1ListMeta;
import io.kubernetes.client.models.V1ObjectMeta;
import io.kubernetes.client.models.V1PersistentVolumeList;
import io.kubernetes.client.models.V1PodList;
import io.kubernetes.client.models.V1SelfSubjectRulesReview;
import io.kubernetes.client.models.V1ServiceList;
import io.kubernetes.client.models.V1SubjectRulesReviewStatus;
import io.kubernetes.client.models.V1beta1IngressList;
import io.kubernetes.client.models.VersionInfo;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import oracle.kubernetes.TestUtils;
import oracle.kubernetes.operator.builders.StubWatchFactory;
import oracle.kubernetes.operator.helpers.CallBuilder;
import oracle.kubernetes.operator.helpers.ClientFactory;
import oracle.kubernetes.operator.helpers.ClientPool;
import oracle.kubernetes.operator.helpers.DomainPresenceInfo;
import oracle.kubernetes.operator.helpers.SynchronousCallFactory;
import oracle.kubernetes.operator.work.AsyncCallTestSupport;
import oracle.kubernetes.weblogic.domain.v1.DomainList;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class DomainPresenceTest {

  private static final String NS = "default";
  private final DomainList expectedDomains = createEmptyDomainList();
  private final V1beta1IngressList expectedIngresses = createEmptyIngressList();
  private final V1ServiceList expectedServices = createEmptyServiceList();
  private final V1EventList expectedEvents = createEmptyEventList();
  private final V1PodList expectedPods = createEmptyPodList();
  private final V1ConfigMap expectedDomainConfigMap = createEmptyConfigMap();

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
    stopping = getStoppingVariable();

    mementos.add(TestUtils.silenceOperatorLogger());
    mementos.add(testSupport.installRequestStepFactory());
    mementos.add(SynchronousCallFactoryStub.install());
    mementos.add(ClientFactoryStub.install());
    mementos.add(StubWatchFactory.install());
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
    createCannedListDomainResponses();

    testSupport.runStep(Main.readExistingResources("operator", NS));

    assertThat(Main.getDomainPresenceInfos(), is(anEmptyMap()));
  }

  @SuppressWarnings("unchecked")
  private void createCannedListDomainResponses() {
    testSupport.createCannedResponse("listDomain").withNamespace(NS).returning(expectedDomains);
    testSupport.createCannedResponse("listIngress").withNamespace(NS).returning(expectedIngresses);
    testSupport.createCannedResponse("listService").withNamespace(NS).returning(expectedServices);
    testSupport.createCannedResponse("listEvent").withNamespace(NS).returning(expectedEvents);
    testSupport.createCannedResponse("listPod").withNamespace(NS).returning(expectedPods);
    testSupport
        .createCannedResponse("readConfigMap")
        .withNamespace(NS)
        .withName(KubernetesConstants.DOMAIN_CONFIG_MAP_NAME)
        .returning(expectedDomainConfigMap);
    testSupport
        .createCannedResponse("replaceConfigMap")
        .withNamespace(NS)
        .withName(KubernetesConstants.DOMAIN_CONFIG_MAP_NAME)
        .returning(expectedDomainConfigMap);
  }

  @Test
  public void afterCancelDomainStatusUpdating_statusUpdaterIsNull() throws Exception {
    DomainPresenceInfo info = new DomainPresenceInfo("namespace");
    info.getStatusUpdater().getAndSet(createStub(ScheduledFuture.class));

    DomainPresenceControl.cancelDomainStatusUpdating(info);

    assertThat(info.getStatusUpdater().get(), nullValue());
  }

  abstract static class SynchronousCallFactoryStub implements SynchronousCallFactory {

    static Memento install() throws NoSuchFieldException {
      return StaticStubSupport.install(CallBuilder.class, "CALL_FACTORY", create());
    }

    private static SynchronousCallFactoryStub create() {
      return createStub(SynchronousCallFactoryStub.class);
    }

    @Override
    public VersionInfo getVersionCode(ApiClient client) throws ApiException {
      return new VersionInfo().major("1").minor("8");
    }

    @Override
    public DomainList getDomainList(
        ApiClient client,
        String namespace,
        String pretty,
        String _continue,
        String fieldSelector,
        Boolean includeUninitialized,
        String labelSelector,
        Integer limit,
        String resourceVersion,
        Integer timeoutSeconds,
        Boolean watch)
        throws ApiException {
      return new DomainList();
    }

    @Override
    public V1PersistentVolumeList listPersistentVolumes(
        ApiClient client,
        String pretty,
        String _continue,
        String fieldSelector,
        Boolean includeUninitialized,
        String labelSelector,
        Integer limit,
        String resourceVersion,
        Integer timeoutSeconds,
        Boolean watch)
        throws ApiException {
      return new V1PersistentVolumeList();
    }

    @Override
    public V1SelfSubjectRulesReview createSelfSubjectRulesReview(
        ApiClient client, V1SelfSubjectRulesReview body, String pretty) throws ApiException {
      return new V1SelfSubjectRulesReview().status(new V1SubjectRulesReviewStatus());
    }
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

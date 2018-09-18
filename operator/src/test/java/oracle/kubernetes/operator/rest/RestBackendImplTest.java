// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.rest;

import static com.meterware.simplestub.Stub.createStrictStub;
import static oracle.kubernetes.operator.rest.RestBackendImplTest.AuthorizationCallFactoryStub.getUpdatedDomain;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.junit.MatcherAssert.assertThat;

import com.meterware.simplestub.Memento;
import com.meterware.simplestub.StaticStubSupport;
import io.kubernetes.client.ApiClient;
import io.kubernetes.client.models.V1ObjectMeta;
import io.kubernetes.client.models.V1SubjectAccessReview;
import io.kubernetes.client.models.V1SubjectAccessReviewStatus;
import io.kubernetes.client.models.V1TokenReview;
import io.kubernetes.client.models.V1TokenReviewStatus;
import io.kubernetes.client.models.V1UserInfo;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import javax.validation.constraints.NotNull;
import javax.ws.rs.WebApplicationException;
import oracle.kubernetes.TestUtils;
import oracle.kubernetes.operator.helpers.CallBuilder;
import oracle.kubernetes.operator.rest.backend.RestBackend;
import oracle.kubernetes.operator.utils.WlsDomainConfigSupport;
import oracle.kubernetes.operator.wlsconfig.WlsDomainConfig;
import oracle.kubernetes.operator.work.Component;
import oracle.kubernetes.operator.work.ContainerResolver;
import oracle.kubernetes.weblogic.domain.ClusterConfigurator;
import oracle.kubernetes.weblogic.domain.DomainConfigurator;
import oracle.kubernetes.weblogic.domain.DomainConfiguratorFactory;
import oracle.kubernetes.weblogic.domain.v1.Domain;
import oracle.kubernetes.weblogic.domain.v1.DomainList;
import oracle.kubernetes.weblogic.domain.v1.DomainSpec;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

@SuppressWarnings("SameParameterValue")
public class RestBackendImplTest {

  private static final String DOMAIN = "domain";
  private static final String NS = "namespace1";
  private static final String UID = "uid1";
  private static List<Domain> domains = new ArrayList<>();
  private WlsDomainConfigSupport configSupport = new WlsDomainConfigSupport(DOMAIN);

  private List<Memento> mementos = new ArrayList<>();
  private RestBackend restBackend;
  private Domain domain = createDomain(NS, UID);
  private DomainConfigurator configurator = DomainConfiguratorFactory.forDomain(domain);

  private static Domain createDomain(String namespace, String uid) {
    return new Domain()
        .withMetadata(new V1ObjectMeta().namespace(namespace))
        .withSpec(new DomainSpec().withDomainUID(uid));
  }

  @Before
  public void setUp() throws Exception {
    mementos.add(TestUtils.silenceOperatorLogger());
    mementos.add(AuthorizationCallFactoryStub.install());
    mementos.add(WlsRetrievalExecutor.install(configSupport));

    domains.clear();
    domains.add(domain);
    configSupport.addWlsCluster("cluster1", "ms1", "ms2", "ms3", "ms4", "ms5", "ms6");
    restBackend = new RestBackendImpl("", "", Collections.singletonList(NS));
  }

  @After
  public void tearDown() {
    for (Memento memento : mementos) memento.revert();
  }

  @Test(expected = WebApplicationException.class)
  public void whenNegativeScaleSpecified_throwException() {
    restBackend.scaleCluster(UID, "cluster1", -1);
  }

  @Test
  public void whenPerClusterReplicaSettingMatchesScaleRequest_doNothing() {
    configureCluster("cluster1").withReplicas(5);

    restBackend.scaleCluster(UID, "cluster1", 5);

    assertThat(getUpdatedDomain(), nullValue());
  }

  private ClusterConfigurator configureCluster(String clusterName) {
    return configurator.configureCluster(clusterName);
  }

  @Test
  public void whenPerClusterReplicaSetting_scaleClusterUpdatesSetting() {
    configureCluster("cluster1").withReplicas(1);

    restBackend.scaleCluster(UID, "cluster1", 5);

    assertThat(getUpdatedDomain().getReplicaCount("cluster1"), equalTo(5));
  }

  @Test
  public void whenNoPerClusterReplicaSetting_scaleClusterCreatesOne() {
    restBackend.scaleCluster(UID, "cluster1", 5);

    assertThat(getUpdatedDomain().getReplicaCount("cluster1"), equalTo(5));
  }

  @Test
  public void whenNoPerClusterReplicaSettingAndDefaultMatchesRequest_doNothing() {
    domain.getSpec().setReplicas(5);

    restBackend.scaleCluster(UID, "cluster1", 5);

    assertThat(getUpdatedDomain(), nullValue());
  }

  abstract static class AuthorizationCallFactoryStub
      implements oracle.kubernetes.operator.helpers.SynchronousCallFactory {

    private static AuthorizationCallFactoryStub callFactory;
    private final boolean allowed = true;
    private final boolean authenticated = true;
    private Domain updatedDomain;

    static Memento install() throws NoSuchFieldException {
      callFactory = createStrictStub(AuthorizationCallFactoryStub.class);
      return StaticStubSupport.install(CallBuilder.class, "CALL_FACTORY", callFactory);
    }

    static Domain getUpdatedDomain() {
      return callFactory.updatedDomain;
    }

    @Override
    public Domain replaceWebLogicOracleV1NamespacedDomain(
        ApiClient client, String name, String namespace, Domain body, String pretty) {
      return updatedDomain = body;
    }

    @Override
    public V1SubjectAccessReview createSubjectAccessReview(
        ApiClient client, V1SubjectAccessReview body, String pretty) {
      return new V1SubjectAccessReview().status(new V1SubjectAccessReviewStatus().allowed(allowed));
    }

    @Override
    public V1TokenReview createTokenReview(ApiClient client, V1TokenReview body, String pretty) {
      return new V1TokenReview()
          .status(new V1TokenReviewStatus().authenticated(authenticated).user(new V1UserInfo()));
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
        Boolean watch) {
      return new DomainList().withItems(domains);
    }
  }

  abstract static class WlsRetrievalExecutor implements ScheduledExecutorService {
    private WlsDomainConfigSupport configSupport;

    static Memento install(WlsDomainConfigSupport configSupport) {
      return new MapMemento<>(
          ContainerResolver.getInstance().getContainer().getComponents(),
          "test",
          Component.createFor(ScheduledExecutorService.class, newExecutor(configSupport)));
    }

    private static WlsRetrievalExecutor newExecutor(WlsDomainConfigSupport response) {
      return createStrictStub(WlsRetrievalExecutor.class, response);
    }

    WlsRetrievalExecutor(WlsDomainConfigSupport configSupport) {
      this.configSupport = configSupport;
    }

    @SuppressWarnings("unchecked")
    @Override
    public @NotNull <T> Future<T> submit(@NotNull Callable<T> task) {
      return (Future<T>) createStrictStub(WlsDomainConfigFuture.class, configSupport);
    }
  }

  abstract static class WlsDomainConfigFuture implements Future<WlsDomainConfig> {
    private WlsDomainConfigSupport configSupport;

    WlsDomainConfigFuture(WlsDomainConfigSupport configSupport) {
      this.configSupport = configSupport;
    }

    @Override
    public WlsDomainConfig get(long timeout, @NotNull TimeUnit unit) {
      return configSupport.createDomainConfig();
    }
  }

  static class MapMemento<K, V> implements Memento {
    private final Map<K, V> map;
    private final K key;
    private final V originalValue;

    MapMemento(Map<K, V> map, K key, V value) {
      this.map = map;
      this.key = key;
      this.originalValue = map.get(key);
      map.put(key, value);
    }

    @Override
    public void revert() {
      if (originalValue == null) map.remove(key);
      else map.put(key, originalValue);
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> T getOriginalValue() {
      return (T) originalValue;
    }
  }
}

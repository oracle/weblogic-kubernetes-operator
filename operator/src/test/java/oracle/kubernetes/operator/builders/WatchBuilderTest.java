// Copyright (c) 2018, 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.builders;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Queue;

import com.meterware.simplestub.Memento;
import com.meterware.simplestub.StaticStubSupport;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1Pod;
import io.kubernetes.client.openapi.models.V1Service;
import io.kubernetes.client.util.Watch;
import io.kubernetes.client.util.Watchable;
import oracle.kubernetes.operator.ClientFactoryStub;
import oracle.kubernetes.operator.KubernetesConstants;
import oracle.kubernetes.operator.NoopWatcherStarter;
import oracle.kubernetes.operator.helpers.ClientPool;
import oracle.kubernetes.utils.TestUtils;
import oracle.kubernetes.weblogic.domain.model.Domain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static java.net.HttpURLConnection.HTTP_ENTITY_TOO_LARGE;
import static oracle.kubernetes.operator.LabelConstants.CREATEDBYOPERATOR_LABEL;
import static oracle.kubernetes.operator.LabelConstants.DOMAINUID_LABEL;
import static oracle.kubernetes.operator.builders.EventMatcher.addEvent;
import static oracle.kubernetes.operator.builders.EventMatcher.bookmarkEvent;
import static oracle.kubernetes.operator.builders.EventMatcher.deleteEvent;
import static oracle.kubernetes.operator.builders.EventMatcher.errorEvent;
import static oracle.kubernetes.operator.builders.EventMatcher.modifyEvent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.fail;

/**
 * Tests watches created by the WatchBuilder, verifying that they are created with the correct query
 * URLs and handle responses correctly.
 */
public class WatchBuilderTest {

  private static final String API_VERSION = "weblogic.oracle/" + KubernetesConstants.DOMAIN_VERSION;
  private static final String NAMESPACE = "testspace";
  private static final int INITIAL_RESOURCE_VERSION = 123;
  private static final String BOOKMARK_RESOURCE_VERSION = "456";
  private int resourceVersion = INITIAL_RESOURCE_VERSION;
  private final List<Memento> mementos = new ArrayList<>();

  @BeforeEach
  public void setUp() throws Exception {
    mementos.add(TestUtils.silenceOperatorLogger());
    mementos.add(ClientPoolStub.install());
    mementos.add(ClientFactoryStub.install());
    mementos.add(StubWatchFactory.install());
    mementos.add(NoopWatcherStarter.install());
  }

  @AfterEach
  public void tearDown() {
    mementos.forEach(Memento::revert);
  }

  @Test
  public void whenDomainWatchReceivesAddResponse_returnItFromIterator() throws Exception {
    Domain domain =
        new Domain()
            .withApiVersion(API_VERSION)
            .withKind("Domain")
            .withMetadata(createMetaData("domain1", NAMESPACE));
    StubWatchFactory.addCallResponses(createAddResponse(domain));

    Watchable<Domain> domainWatch = new WatchBuilder().createDomainWatch(NAMESPACE);

    assertThat(domainWatch, contains(addEvent(domain)));
  }

  @Test
  public void whenDomainWatchReceivesBookmarkResponse_updateResourceVersion() throws Exception {
    Domain domain =
            new Domain()
                    .withApiVersion(API_VERSION)
                    .withKind("Domain")
                    .withMetadata(createMetaData("domain1", NAMESPACE, BOOKMARK_RESOURCE_VERSION));
    StubWatchFactory.addCallResponses(createBookmarkResponse(domain));

    Watchable<Domain> domainWatch = new WatchBuilder().createDomainWatch(NAMESPACE);

    assertThat(domainWatch, contains(bookmarkEvent(domain)));
  }

  private <T> Watch.Response<T> createAddResponse(T object) {
    return WatchEvent.createAddedEvent(object).toWatchResponse();
  }

  private <T> Watch.Response<T> createModifyResponse(T object) {
    return WatchEvent.createModifiedEvent(object).toWatchResponse();
  }

  private <T> Watch.Response<T> createDeleteResponse(T object) {
    return WatchEvent.createDeletedEvent(object).toWatchResponse();
  }

  private <T> Watch.Response<T> createBookmarkResponse(T object) {
    return WatchEvent.createBookmarkEvent(object).toWatchResponse();
  }

  @SuppressWarnings("SameParameterValue")
  private Watch.Response<Object> createErrorResponse(int statusCode) {
    return WatchEvent.createErrorEvent(statusCode).toWatchResponse();
  }

  @Test
  public void afterWatchClosed_returnClientToPool() throws Exception {
    Domain domain =
        new Domain()
            .withApiVersion(API_VERSION)
            .withKind("Domain")
            .withMetadata(createMetaData("domain1", NAMESPACE));
    StubWatchFactory.addCallResponses(createAddResponse(domain));

    try (Watchable<Domain> domainWatch = new WatchBuilder().createDomainWatch(NAMESPACE)) {
      domainWatch.next();
    }

    assertThat(ClientPoolStub.getPooledClients(), not(empty()));
  }

  @Test
  public void afterWatchError_closeDoesNotReturnClientToPool() {

    try (Watchable<Domain> domainWatch = new WatchBuilder().createDomainWatch(NAMESPACE)) {
      domainWatch.next();
      fail("Should have thrown an exception");
    } catch (Throwable ignore) {
      // no-op
    }

    assertThat(ClientPoolStub.getPooledClients(), is(empty()));
  }

  @Test
  public void whenDomainWatchReceivesModifyAndDeleteResponses_returnBothFromIterator()
      throws Exception {
    Domain domain1 =
        new Domain()
            .withApiVersion(API_VERSION)
            .withKind("Domain")
            .withMetadata(createMetaData("domain1", NAMESPACE));
    Domain domain2 =
        new Domain()
            .withApiVersion(API_VERSION)
            .withKind("Domain")
            .withMetadata(createMetaData("domain2", NAMESPACE));
    StubWatchFactory.addCallResponses(createModifyResponse(domain1), createDeleteResponse(domain2));

    Watchable<Domain> domainWatch = new WatchBuilder().createDomainWatch(NAMESPACE);

    assertThat(domainWatch, contains(modifyEvent(domain1), deleteEvent(domain2)));
  }

  @Test
  public void whenDomainWatchReceivesErrorResponse_returnItFromIterator() throws Exception {
    StubWatchFactory.addCallResponses(createErrorResponse(HTTP_ENTITY_TOO_LARGE));

    Watchable<Domain> domainWatch = new WatchBuilder().createDomainWatch(NAMESPACE);

    assertThat(domainWatch, contains(errorEvent(HTTP_ENTITY_TOO_LARGE)));
  }

  @Test
  public void whenServiceWatchSpecifiesParameters_verifyAndReturnResponse() throws Exception {
    String startResourceVersion = getNextResourceVersion();
    V1Service service =
        new V1Service()
            .apiVersion(API_VERSION)
            .kind("Service")
            .metadata(createMetaData("service3", NAMESPACE));

    StubWatchFactory.addCallResponses(createModifyResponse(service));

    Watchable<V1Service> serviceWatch =
        new WatchBuilder()
            .withResourceVersion(startResourceVersion)
            .withLabelSelector(DOMAINUID_LABEL + "," + CREATEDBYOPERATOR_LABEL)
            .createServiceWatch(NAMESPACE);

    assertThat(serviceWatch, contains(modifyEvent(service)));
    assertThat(StubWatchFactory.getRequestParameters().get(0),
          allOf(hasEntry("resourceVersion", startResourceVersion),
                hasEntry("labelSelector", DOMAINUID_LABEL + "," + CREATEDBYOPERATOR_LABEL),
                hasEntry("watch", "true")));
  }

  @Test
  public void whenPodWatchSpecifiesParameters_verifyAndReturnResponse() throws Exception {
    V1Pod pod =
        new V1Pod().apiVersion(API_VERSION).kind("Pod").metadata(createMetaData("pod4", NAMESPACE));
    StubWatchFactory.addCallResponses(createAddResponse(pod));

    Watchable<V1Pod> podWatch =
        new WatchBuilder()
            .withFieldSelector("thisValue")
            .withLimit(25)
            .createPodWatch(NAMESPACE);

    assertThat(podWatch, contains(addEvent(pod)));
    assertThat(StubWatchFactory.getRequestParameters().get(0),
          allOf(hasEntry("fieldSelector", "thisValue"), hasEntry("limit", "25")));
  }

  @Test
  public void whenPodWatchFindsNoData_hasNextReturnsFalse() throws Exception {

    Watchable<V1Pod> podWatch = new WatchBuilder().createPodWatch(NAMESPACE);

    assertThat(podWatch.hasNext(), is(false));
  }

  @SuppressWarnings("SameParameterValue")
  private V1ObjectMeta createMetaData(String name, String namespace) {
    return createMetaData(name, namespace, getNextResourceVersion());
  }

  private V1ObjectMeta createMetaData(String name, String namespace, String resourceVersion) {
    return new V1ObjectMeta()
        .name(name)
        .namespace(namespace)
        .resourceVersion(resourceVersion);
  }

  private String getNextResourceVersion() {
    return Integer.toString(resourceVersion++);
  }

  static class ClientPoolStub extends ClientPool {
    private static Queue<ApiClient> queue;

    static Memento install() throws NoSuchFieldException {
      queue = new ArrayDeque<>();
      return StaticStubSupport.install(ClientPool.class, "SINGLETON", new ClientPoolStub());
    }

    static Collection<ApiClient> getPooledClients() {
      return Collections.unmodifiableCollection(queue);
    }

    @Override
    protected Queue<ApiClient> getQueue() {
      return queue;
    }
  }
}

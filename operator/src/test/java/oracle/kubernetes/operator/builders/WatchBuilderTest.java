// Copyright (c) 2018, 2024, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.builders;

import java.util.ArrayList;
import java.util.List;

import com.meterware.simplestub.Memento;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1Pod;
import io.kubernetes.client.openapi.models.V1Service;
import io.kubernetes.client.util.Watch;
import io.kubernetes.client.util.Watchable;
import io.kubernetes.client.util.generic.options.ListOptions;
import oracle.kubernetes.operator.KubernetesConstants;
import oracle.kubernetes.operator.calls.RequestBuilder;
import oracle.kubernetes.operator.watcher.NoopWatcherStarter;
import oracle.kubernetes.utils.TestUtils;
import oracle.kubernetes.weblogic.domain.model.ClusterResource;
import oracle.kubernetes.weblogic.domain.model.DomainResource;
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
import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.Matchers.is;

/**
 * Tests watches created by the WatchBuilder, verifying that they are created with the correct query
 * URLs and handle responses correctly.
 */
class WatchBuilderTest {

  private static final String API_VERSION = "weblogic.oracle/" + KubernetesConstants.DOMAIN_VERSION;
  private static final String NAMESPACE = "testspace";
  private static final int INITIAL_RESOURCE_VERSION = 123;
  private static final String BOOKMARK_RESOURCE_VERSION = "456";
  private int resourceVersion = INITIAL_RESOURCE_VERSION;
  private final List<Memento> mementos = new ArrayList<>();

  @BeforeEach
  public void setUp() throws Exception {
    mementos.add(TestUtils.silenceOperatorLogger());
    mementos.add(StubWatchFactory.install());
    mementos.add(NoopWatcherStarter.install());
  }

  @AfterEach
  public void tearDown() {
    mementos.forEach(Memento::revert);
  }

  @Test
  void whenClusterWatchReceivesAddResponse_returnItFromIterator() throws Exception {
    ClusterResource cluster =
        new ClusterResource()
            .withApiVersion(API_VERSION)
            .withKind("Cluster")
            .withMetadata(createMetaData("cluster1", NAMESPACE));
    StubWatchFactory.addCallResponses(createAddResponse(cluster));

    Watchable<ClusterResource> clusterWatch = RequestBuilder.CLUSTER.watch(NAMESPACE, new ListOptions());

    assertThat(clusterWatch, contains(addEvent(cluster)));
  }

  @Test
  void whenClusterWatchReceivesBookmarkResponse_updateResourceVersion() throws Exception {
    ClusterResource cluster =
        new ClusterResource()
            .withApiVersion(API_VERSION)
            .withKind("Cluster")
            .withMetadata(createMetaData("cluster1", NAMESPACE, BOOKMARK_RESOURCE_VERSION));
    StubWatchFactory.addCallResponses(createBookmarkResponse(cluster));

    Watchable<ClusterResource> clusterWatch = RequestBuilder.CLUSTER.watch(NAMESPACE, new ListOptions());

    assertThat(clusterWatch, contains(bookmarkEvent(cluster)));
  }

  @Test
  void whenDomainWatchReceivesAddResponse_returnItFromIterator() throws Exception {
    DomainResource domain =
        new DomainResource()
            .withApiVersion(API_VERSION)
            .withKind("Domain")
            .withMetadata(createMetaData("domain1", NAMESPACE));
    StubWatchFactory.addCallResponses(createAddResponse(domain));

    Watchable<DomainResource> domainWatch = RequestBuilder.DOMAIN.watch(NAMESPACE, new ListOptions());

    assertThat(domainWatch, contains(addEvent(domain)));
  }

  @Test
  void whenDomainWatchReceivesBookmarkResponse_updateResourceVersion() throws Exception {
    DomainResource domain =
            new DomainResource()
                    .withApiVersion(API_VERSION)
                    .withKind("Domain")
                    .withMetadata(createMetaData("domain1", NAMESPACE, BOOKMARK_RESOURCE_VERSION));
    StubWatchFactory.addCallResponses(createBookmarkResponse(domain));

    Watchable<DomainResource> domainWatch = RequestBuilder.DOMAIN.watch(NAMESPACE, new ListOptions());

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
  void whenDomainWatchReceivesModifyAndDeleteResponses_returnBothFromIterator()
      throws Exception {
    DomainResource domain1 =
        new DomainResource()
            .withApiVersion(API_VERSION)
            .withKind("Domain")
            .withMetadata(createMetaData("domain1", NAMESPACE));
    DomainResource domain2 =
        new DomainResource()
            .withApiVersion(API_VERSION)
            .withKind("Domain")
            .withMetadata(createMetaData("domain2", NAMESPACE));
    StubWatchFactory.addCallResponses(createModifyResponse(domain1), createDeleteResponse(domain2));

    Watchable<DomainResource> domainWatch = RequestBuilder.DOMAIN.watch(NAMESPACE, new ListOptions());

    assertThat(domainWatch, contains(List.of(modifyEvent(domain1), deleteEvent(domain2))));
  }

  @Test
  void whenDomainWatchReceivesErrorResponse_returnItFromIterator() throws Exception {
    StubWatchFactory.addCallResponses(createErrorResponse(HTTP_ENTITY_TOO_LARGE));

    Watchable<DomainResource> domainWatch = RequestBuilder.DOMAIN.watch(NAMESPACE, new ListOptions());

    assertThat(domainWatch, contains(errorEvent(HTTP_ENTITY_TOO_LARGE)));
  }

  @Test
  void whenServiceWatchSpecifiesParameters_verifyAndReturnResponse() throws Exception {
    String startResourceVersion = getNextResourceVersion();
    V1Service service =
        new V1Service()
            .apiVersion(API_VERSION)
            .kind("Service")
            .metadata(createMetaData("service3", NAMESPACE));

    StubWatchFactory.addCallResponses(createModifyResponse(service));

    Watchable<V1Service> serviceWatch = RequestBuilder.SERVICE.watch(NAMESPACE,
            new ListOptions()
                    .resourceVersion(startResourceVersion)
                    .labelSelector(DOMAINUID_LABEL + "," + CREATEDBYOPERATOR_LABEL));

    assertThat(serviceWatch, contains(modifyEvent(service)));
    assertThat(StubWatchFactory.getRequestParameters().get(0),
          allOf(hasEntry("resourceVersion", startResourceVersion),
                hasEntry("labelSelector", DOMAINUID_LABEL + "," + CREATEDBYOPERATOR_LABEL)));
  }

  @Test
  void whenPodWatchSpecifiesParameters_verifyAndReturnResponse() throws Exception {
    V1Pod pod =
        new V1Pod().apiVersion(API_VERSION).kind("Pod").metadata(createMetaData("pod4", NAMESPACE));
    StubWatchFactory.addCallResponses(createAddResponse(pod));

    Watchable<V1Pod> podWatch = RequestBuilder.POD.watch(NAMESPACE,
            new ListOptions().fieldSelector("thisValue").limit(25));

    assertThat(podWatch, contains(addEvent(pod)));
    assertThat(StubWatchFactory.getRequestParameters().get(0),
          allOf(hasEntry("fieldSelector", "thisValue"), hasEntry("limit", "25")));
  }

  @Test
  void whenPodWatchFindsNoData_hasNextReturnsFalse() throws Exception {

    Watchable<V1Pod> podWatch = RequestBuilder.POD.watch(NAMESPACE, new ListOptions());

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
}

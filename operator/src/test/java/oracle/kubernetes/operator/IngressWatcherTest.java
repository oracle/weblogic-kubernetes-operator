// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator;

import static oracle.kubernetes.operator.LabelConstants.CLUSTERNAME_LABEL;
import static oracle.kubernetes.operator.LabelConstants.DOMAINUID_LABEL;
import static org.hamcrest.Matchers.both;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.junit.MatcherAssert.assertThat;

import com.google.common.collect.ImmutableMap;
import io.kubernetes.client.models.V1ObjectMeta;
import io.kubernetes.client.models.V1beta1Ingress;
import io.kubernetes.client.util.Watch;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import oracle.kubernetes.operator.builders.StubWatchFactory;
import oracle.kubernetes.operator.watcher.WatchListener;
import org.junit.Test;

/** This test class verifies the behavior of the IngressWatcher. */
public class IngressWatcherTest extends WatcherTestBase implements WatchListener<V1beta1Ingress> {

  private static final int INITIAL_RESOURCE_VERSION = 456;

  @Override
  public void receivedResponse(Watch.Response<V1beta1Ingress> response) {
    recordCallBack(response);
  }

  @Test
  public void initialRequest_specifiesStartingResourceVersionAndStandardLabelSelector()
      throws Exception {
    sendInitialRequest(INITIAL_RESOURCE_VERSION);

    assertThat(
        StubWatchFactory.getRecordedParameters().get(0),
        both(hasEntry("resourceVersion", Integer.toString(INITIAL_RESOURCE_VERSION)))
            .and(
                hasEntry(
                    "labelSelector",
                    asList(DOMAINUID_LABEL, LabelConstants.CREATEDBYOPERATOR_LABEL))));
  }

  private String asList(String... selectors) {
    return String.join(",", selectors);
  }

  @Test
  public void whenIngressHasNoDomainUid_returnNull() throws Exception {
    V1beta1Ingress ingress = new V1beta1Ingress().metadata(new V1ObjectMeta());

    assertThat(IngressWatcher.getIngressDomainUID(ingress), nullValue());
  }

  @Test
  public void whenIngressHasDomainUid_returnIt() throws Exception {
    V1beta1Ingress ingress =
        new V1beta1Ingress()
            .metadata(new V1ObjectMeta().labels(ImmutableMap.of(DOMAINUID_LABEL, "domain1")));

    assertThat(IngressWatcher.getIngressDomainUID(ingress), equalTo("domain1"));
  }

  @Test
  public void whenIngressHasNoClusterName_returnNull() throws Exception {
    V1beta1Ingress ingress = new V1beta1Ingress().metadata(new V1ObjectMeta());

    assertThat(IngressWatcher.getIngressClusterName(ingress), nullValue());
  }

  @Test
  public void whenIngressHasClusterName_returnIt() throws Exception {
    V1beta1Ingress ingress =
        new V1beta1Ingress()
            .metadata(new V1ObjectMeta().labels(ImmutableMap.of(CLUSTERNAME_LABEL, "mycluster")));

    assertThat(IngressWatcher.getIngressClusterName(ingress), equalTo("mycluster"));
  }

  @SuppressWarnings("unchecked")
  @Override
  protected <T> T createObjectWithMetaData(V1ObjectMeta metaData) {
    return (T) new V1beta1Ingress().metadata(metaData);
  }

  @Override
  protected IngressWatcher createWatcher(
      String nameSpace, AtomicBoolean stopping, int initialResourceVersion) {
    return IngressWatcher.create(
        Executors.defaultThreadFactory(),
        nameSpace,
        Integer.toString(initialResourceVersion),
        this,
        stopping);
  }
}

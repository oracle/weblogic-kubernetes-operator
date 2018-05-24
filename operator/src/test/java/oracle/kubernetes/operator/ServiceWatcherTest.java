// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator;

import static oracle.kubernetes.operator.LabelConstants.CHANNELNAME_LABEL;
import static oracle.kubernetes.operator.LabelConstants.CREATEDBYOPERATOR_LABEL;
import static oracle.kubernetes.operator.LabelConstants.DOMAINUID_LABEL;
import static oracle.kubernetes.operator.LabelConstants.SERVERNAME_LABEL;
import static org.hamcrest.Matchers.both;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.junit.MatcherAssert.assertThat;

import com.google.common.collect.ImmutableMap;
import io.kubernetes.client.models.V1ObjectMeta;
import io.kubernetes.client.models.V1Service;
import io.kubernetes.client.util.Watch;
import java.util.concurrent.atomic.AtomicBoolean;
import oracle.kubernetes.operator.builders.StubWatchFactory;
import oracle.kubernetes.operator.watcher.WatchListener;
import org.junit.Test;

/** This test class verifies the behavior of the ServiceWatcher. */
public class ServiceWatcherTest extends WatcherTestBase implements WatchListener<V1Service> {

  private static final int INITIAL_RESOURCE_VERSION = 987;

  @Override
  public void receivedResponse(Watch.Response<V1Service> response) {
    recordCallBack(response);
  }

  @Test
  public void initialRequest_specifiesStartingResourceVersionAndLabelSelector() throws Exception {
    sendInitialRequest(INITIAL_RESOURCE_VERSION);

    assertThat(
        StubWatchFactory.getRequestParameters().get(0),
        both(hasEntry("resourceVersion", Integer.toString(INITIAL_RESOURCE_VERSION)))
            .and(hasEntry("labelSelector", asList(DOMAINUID_LABEL, CREATEDBYOPERATOR_LABEL))));
  }

  private String asList(String... selectors) {
    return String.join(",", selectors);
  }

  @SuppressWarnings("unchecked")
  @Override
  protected <T> T createObjectWithMetaData(V1ObjectMeta metaData) {
    return (T) new V1Service().metadata(metaData);
  }

  @Override
  protected ServiceWatcher createWatcher(String ns, AtomicBoolean stopping, int rv) {
    return ServiceWatcher.create(this, ns, Integer.toString(rv), this, stopping);
  }

  @Test
  public void whenServiceHasNoDomainUid_returnNull() throws Exception {
    V1Service service = new V1Service().metadata(new V1ObjectMeta());

    assertThat(ServiceWatcher.getServiceDomainUID(service), nullValue());
  }

  @Test
  public void whenServiceHasDomainUid_returnIt() throws Exception {
    V1Service service =
        new V1Service()
            .metadata(new V1ObjectMeta().labels(ImmutableMap.of(DOMAINUID_LABEL, "domain1")));

    assertThat(ServiceWatcher.getServiceDomainUID(service), equalTo("domain1"));
  }

  @Test
  public void whenServiceHasNoServerName_returnNull() throws Exception {
    V1Service service = new V1Service().metadata(new V1ObjectMeta());

    assertThat(ServiceWatcher.getServiceServerName(service), nullValue());
  }

  @Test
  public void whenServiceHasServerName_returnIt() throws Exception {
    V1Service service =
        new V1Service()
            .metadata(new V1ObjectMeta().labels(ImmutableMap.of(SERVERNAME_LABEL, "myserver")));

    assertThat(ServiceWatcher.getServiceServerName(service), equalTo("myserver"));
  }

  @Test
  public void whenServiceHasNoChannelName_returnNull() throws Exception {
    V1Service service = new V1Service().metadata(new V1ObjectMeta());

    assertThat(ServiceWatcher.getServiceChannelName(service), nullValue());
  }

  @Test
  public void whenServiceHasChannelName_returnIt() throws Exception {
    V1Service service =
        new V1Service()
            .metadata(new V1ObjectMeta().labels(ImmutableMap.of(CHANNELNAME_LABEL, "channel1")));

    assertThat(ServiceWatcher.getServiceChannelName(service), equalTo("channel1"));
  }
}

// Copyright (c) 2018, 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator;

import java.math.BigInteger;
import java.util.concurrent.atomic.AtomicBoolean;

import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.util.Watch;
import oracle.kubernetes.operator.builders.StubWatchFactory;
import oracle.kubernetes.operator.watcher.WatchListener;
import oracle.kubernetes.weblogic.domain.model.Domain;
import oracle.kubernetes.weblogic.domain.model.DomainSpec;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.Matchers.is;

/** This test class verifies the behavior of the DomainWatcher. */
public class DomainWatcherTest extends WatcherTestBase implements WatchListener<Domain> {

  private static final BigInteger INITIAL_RESOURCE_VERSION = new BigInteger("456");
  private static final String BOOKMARK_RESOURCE_VERSION = "987";
  private static final String UID = "uid";

  private final Domain domain = createDomain();

  private static Domain createDomain() {
    return new Domain().withSpec(new DomainSpec().withDomainUid(UID));
  }

  @Override
  public void receivedResponse(Watch.Response<Domain> response) {
    recordCallBack(response);
  }

  @Test
  public void initialRequest_specifiesStartingResourceVersion() {
    sendInitialRequest(INITIAL_RESOURCE_VERSION);

    assertThat(
        StubWatchFactory.getRequestParameters().get(0),
        hasEntry("resourceVersion", INITIAL_RESOURCE_VERSION.toString()));
  }

  @Test
  public void whenWatcherReceivesBookmarkEvent_updateResourceVersion() {
    Watcher<?> watcher = sendBookmarkRequest(INITIAL_RESOURCE_VERSION, BOOKMARK_RESOURCE_VERSION);

    assertThat(watcher.getResourceVersion(), is(BOOKMARK_RESOURCE_VERSION));
  }

  @Test
  public void whenDomainAdded_createPersistentVolumeClaim() {
    scheduleAddResponse(domain);
  }

  @SuppressWarnings("unchecked")
  @Override
  protected <T> T createObjectWithMetaData(V1ObjectMeta metaData) {
    return (T) new Domain().withMetadata(metaData);
  }

  @Override
  protected DomainWatcher createWatcher(String ns, AtomicBoolean stopping, BigInteger rv) {
    return DomainWatcher.create(this, ns, rv.toString(), tuning, this, stopping);
  }
}

// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasEntry;

import io.kubernetes.client.models.V1ObjectMeta;
import io.kubernetes.client.util.Watch;
import java.util.concurrent.atomic.AtomicBoolean;
import oracle.kubernetes.operator.builders.StubWatchFactory;
import oracle.kubernetes.operator.watcher.WatchListener;
import oracle.kubernetes.weblogic.domain.v1.Domain;
import org.junit.Test;

/** This test class verifies the behavior of the DomainWatcher. */
public class DomainWatcherTest extends WatcherTestBase implements WatchListener<Domain> {

  private static final int INITIAL_RESOURCE_VERSION = 456;

  @Override
  public void receivedResponse(Watch.Response<Domain> response) {
    recordCallBack(response);
  }

  @Test
  public void initialRequest_specifiesStartingResourceVersion() throws Exception {
    sendInitialRequest(INITIAL_RESOURCE_VERSION);

    assertThat(
        StubWatchFactory.getRecordedParameters().get(0),
        hasEntry("resourceVersion", Integer.toString(INITIAL_RESOURCE_VERSION)));
  }

  @SuppressWarnings("unchecked")
  @Override
  protected <T> T createObjectWithMetaData(V1ObjectMeta metaData) {
    return (T) new Domain().withMetadata(metaData);
  }

  @Override
  protected DomainWatcher createWatcher(String ns, AtomicBoolean stopping, int rv) {
    return DomainWatcher.create(this, ns, Integer.toString(rv), this, stopping);
  }
}

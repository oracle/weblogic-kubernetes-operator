// Copyright (c) 2019, 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator;

import java.math.BigInteger;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicBoolean;

import io.kubernetes.client.openapi.models.V1Namespace;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.util.Watch;
import oracle.kubernetes.operator.builders.StubWatchFactory;
import oracle.kubernetes.operator.watcher.WatchListener;
import org.junit.Test;

import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.junit.MatcherAssert.assertThat;

/** This test class verifies the behavior of the NamespaceWatcher. */
public class NamespaceWatcherTest extends WatcherTestBase 
    implements WatchListener<V1Namespace> {

  private static final BigInteger INITIAL_RESOURCE_VERSION = new BigInteger("456");

  @Override
  public void receivedResponse(Watch.Response<V1Namespace> response) {
    recordCallBack(response);
  }

  @Test
  public void initialRequest_specifiesStartingResourceVersionAndStandardLabelSelector() {
    sendInitialRequest(INITIAL_RESOURCE_VERSION);

    assertThat(
        StubWatchFactory.getRequestParameters().get(0),
        hasEntry("resourceVersion", INITIAL_RESOURCE_VERSION.toString()));
  }

  @SuppressWarnings("unchecked")
  @Override
  protected <T> T createObjectWithMetaData(V1ObjectMeta metaData) {
    return (T) new V1Namespace().metadata(metaData);
  }

  @Override
  protected NamespaceWatcher createWatcher(String ns, AtomicBoolean stopping, BigInteger rv) {
    return NamespaceWatcher.create((ThreadFactory)this, rv.toString(),
        tuning, (WatchListener<V1Namespace>)this, stopping);
  }
}

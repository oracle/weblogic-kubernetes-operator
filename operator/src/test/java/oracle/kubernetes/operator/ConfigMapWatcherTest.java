// Copyright (c) 2018, 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator;

import java.math.BigInteger;
import java.util.concurrent.atomic.AtomicBoolean;

import io.kubernetes.client.openapi.models.V1ConfigMap;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.util.Watch;
import oracle.kubernetes.operator.builders.StubWatchFactory;
import oracle.kubernetes.operator.watcher.WatchListener;
import org.junit.Test;

import static org.hamcrest.Matchers.both;
import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.junit.MatcherAssert.assertThat;

/** This test class verifies the behavior of the ConfigMapWatcher. */
public class ConfigMapWatcherTest extends WatcherTestBase implements WatchListener<V1ConfigMap> {

  private static final BigInteger INITIAL_RESOURCE_VERSION = new BigInteger("456");

  @Override
  public void receivedResponse(Watch.Response<V1ConfigMap> response) {
    recordCallBack(response);
  }

  @Test
  public void initialRequest_specifiesStartingResourceVersionAndStandardLabelSelector() {
    sendInitialRequest(INITIAL_RESOURCE_VERSION);

    assertThat(
        StubWatchFactory.getRequestParameters().get(0),
        both(hasEntry("resourceVersion", INITIAL_RESOURCE_VERSION.toString()))
            .and(hasEntry("labelSelector", LabelConstants.CREATEDBYOPERATOR_LABEL)));
  }

  @SuppressWarnings("unchecked")
  @Override
  protected <T> T createObjectWithMetaData(V1ObjectMeta metaData) {
    return (T) new V1ConfigMap().metadata(metaData);
  }

  @Override
  protected ConfigMapWatcher createWatcher(String ns, AtomicBoolean stopping, BigInteger rv) {
    return ConfigMapWatcher.create(this, ns, rv.toString(), tuning, this, stopping);
  }
}

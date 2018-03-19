/* Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved. */
package oracle.kubernetes.operator;

import io.kubernetes.client.models.V1ConfigMap;
import io.kubernetes.client.models.V1ObjectMeta;
import io.kubernetes.client.util.Watch;
import oracle.kubernetes.operator.builders.StubWatchFactory;
import oracle.kubernetes.operator.watcher.ThreadedWatcher;
import oracle.kubernetes.operator.watcher.WatchingEventDestination;
import org.junit.Test;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.hamcrest.Matchers.both;
import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.junit.MatcherAssert.assertThat;

/**
 * This test class verifies the behavior of the ConfigMapWatcher.
 */
public class ConfigMapWatcherTest extends WatcherTestBase implements WatchingEventDestination<V1ConfigMap> {

  private static final int INITIAL_RESOURCE_VERSION = 456;

  @Override
  public void eventCallback(Watch.Response<V1ConfigMap> response) {
    recordCallBack(response);
  }

  @Test
  public void initialRequest_specifiesStartingResourceVersionAndStandardLabelSelector() throws Exception {
    sendInitialRequest(INITIAL_RESOURCE_VERSION);

    assertThat(StubWatchFactory.getRecordedParameters().get(0),
                    both(hasEntry("resourceVersion", Integer.toString(INITIAL_RESOURCE_VERSION)))
                    .and(hasEntry("labelSelector", LabelConstants.CREATEDBYOPERATOR_LABEL)));
  }

  private String asList(String... selectors) {
    return String.join(",", selectors);
  }

  @SuppressWarnings("unchecked")
  @Override
  protected <T> T createObjectWithMetaData(V1ObjectMeta metaData) {
      return (T) new V1ConfigMap().metadata(metaData);
  }

  @Override
  protected ThreadedWatcher createWatcher(String nameSpace, AtomicBoolean stopping, int initialResourceVersion) {
      return ConfigMapWatcher.create(nameSpace, Integer.toString(initialResourceVersion), this, stopping);
  }
}

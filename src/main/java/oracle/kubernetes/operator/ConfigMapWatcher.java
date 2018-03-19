// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator;

import io.kubernetes.client.ApiException;
import io.kubernetes.client.models.V1ConfigMap;
import io.kubernetes.client.util.Watch;
import oracle.kubernetes.operator.builders.WatchBuilder;
import oracle.kubernetes.operator.builders.WatchI;
import oracle.kubernetes.operator.helpers.ClientHelper;
import oracle.kubernetes.operator.helpers.ClientHolder;
import oracle.kubernetes.operator.watcher.ThreadedWatcher;
import oracle.kubernetes.operator.watcher.Watching;
import oracle.kubernetes.operator.watcher.WatchingEventDestination;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * This class handles ConfigMap watching. It receives config map change events and sends
 * them into the operator for processing.
 */
public class ConfigMapWatcher implements Runnable, ThreadedWatcher {
  private final String ns;
  private final String initialResourceVersion;
  private final WatchingEventDestination<V1ConfigMap> destination;
  private final AtomicBoolean isStopping;
  private Thread thread;

  public static ConfigMapWatcher create(String ns, String initialResourceVersion, WatchingEventDestination<V1ConfigMap> destination, AtomicBoolean isStopping) {
    ConfigMapWatcher dlw = new ConfigMapWatcher(ns, initialResourceVersion, destination, isStopping);
    Thread thread = new Thread(dlw);
    thread.setName("Thread-ConfigMapWatcher-" + ns);
    thread.setDaemon(true);
    thread.start();
    dlw.thread = thread;
    return dlw;
  }

  private ConfigMapWatcher(String ns, String initialResourceVersion, WatchingEventDestination<V1ConfigMap> destination, AtomicBoolean isStopping) {
    this.ns = ns;
    this.initialResourceVersion = initialResourceVersion;
    this.destination = destination;
    this.isStopping = isStopping;
  }

  @Override
  public Thread getThread() {
    return thread;
  }

  /**
   * Polling loop. Get the next ConfigMap object event and process it.
   */
  @Override
  public void run() {
    ClientHelper helper = ClientHelper.getInstance();
    ClientHolder client = helper.take();
    try {
      Watching<V1ConfigMap> w = createWatching(client);
      Watcher<V1ConfigMap> watcher = new Watcher<>(w, initialResourceVersion);
      
      // invoke watch on current Thread.  Won't return until watch stops
      watcher.doWatch();
      
    } finally {
      helper.recycle(client);
    }
  }
  
  protected Watching<V1ConfigMap> createWatching(ClientHolder client) {
    return new Watching<V1ConfigMap>() {

      /**
       * Watcher callback to issue the list ConfigMap changes. It is driven by the
       * Watcher wrapper to issue repeated watch requests.
       * @param resourceVersion resource version to omit older events
       * @return Watch object or null if the operation should end
       * @throws ApiException if there is an API error.
       */
      @Override
      public WatchI<V1ConfigMap> initiateWatch(String resourceVersion) throws ApiException {
        return initiateWatch(new WatchBuilder(client).withResourceVersion(resourceVersion));
      }

      @Override
      public WatchI<V1ConfigMap> initiateWatch(WatchBuilder watchBuilder) throws ApiException {
        return watchBuilder.withLabelSelector(LabelConstants.CREATEDBYOPERATOR_LABEL)
                        .createConfigMapWatch(ns);
      }

      @Override
      public void eventCallback(Watch.Response<V1ConfigMap> item) {
        processEventCallback(item);
      }

      @Override
      public boolean isStopping() {
        return isStopping.get();
      }
    };
  }
  
  public void processEventCallback(Watch.Response<V1ConfigMap> item) {
    destination.eventCallback(item);
  }
}

// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator;

import io.kubernetes.client.ApiException;
import io.kubernetes.client.models.V1ObjectMeta;
import io.kubernetes.client.models.V1Service;
import io.kubernetes.client.util.Watch;
import oracle.kubernetes.operator.builders.WatchBuilder;
import oracle.kubernetes.operator.builders.WatchI;
import oracle.kubernetes.operator.helpers.ClientHelper;
import oracle.kubernetes.operator.helpers.ClientHolder;
import oracle.kubernetes.operator.watcher.ThreadedWatcher;
import oracle.kubernetes.operator.watcher.Watching;
import oracle.kubernetes.operator.watcher.WatchingEventDestination;

import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * This class handles Service watching. It receives service change events and sends
 * them into the operator for processing.
 */
public class ServiceWatcher implements Runnable, ThreadedWatcher {
  private final String ns;
  private final String initialResourceVersion;
  private final WatchingEventDestination<V1Service> destination;
  private final AtomicBoolean isStopping;
  private Thread thread;

  public static ServiceWatcher create(String ns, String initialResourceVersion, WatchingEventDestination<V1Service> destination, AtomicBoolean isStopping) {
    ServiceWatcher dlw = new ServiceWatcher(ns, initialResourceVersion, destination, isStopping);
    Thread thread = new Thread(dlw);
    thread.setName("Thread-ServiceWatcher-" + ns);
    thread.setDaemon(true);
    thread.start();
    dlw.thread = thread;
    return dlw;
  }

  private ServiceWatcher(String ns, String initialResourceVersion, WatchingEventDestination<V1Service> destination, AtomicBoolean isStopping) {
    this.ns = ns;
    this.initialResourceVersion = initialResourceVersion;
    this.destination = destination;
    this.isStopping = isStopping;
  }

  public Thread getThread() {
    return thread;
  }

  /**
   * Polling loop. Get the next Service object event and process it.
   */
  @Override
  public void run() {
    ClientHelper helper = ClientHelper.getInstance();
    ClientHolder client = helper.take();
    try {
      Watching<V1Service> w = createWatching(client);
      Watcher<V1Service> watcher = new Watcher<>(w, initialResourceVersion);
      
      // invoke watch on current Thread.  Won't return until watch stops
      watcher.doWatch();
      
    } finally {
      helper.recycle(client);
    }
  }
  
  private Watching<V1Service> createWatching(ClientHolder client) {
    return new Watching<V1Service>() {

      /**
       * Watcher callback to issue the list Service changes. It is driven by the
       * Watcher wrapper to issue repeated watch requests.
       * @param resourceVersion resource version to omit older events
       * @return Watch object or null if the operation should end
       * @throws ApiException if there is an API error.
       */
      @Override
      public WatchI<V1Service> initiateWatch(String resourceVersion) throws ApiException {
        return initiateWatch(new WatchBuilder(client).withResourceVersion(resourceVersion));
      }

      @Override
      public WatchI<V1Service> initiateWatch(WatchBuilder watchBuilder) throws ApiException {
        return watchBuilder
                 .withLabelSelectors(LabelConstants.DOMAINUID_LABEL, LabelConstants.CREATEDBYOPERATOR_LABEL)
                 .createServiceWatch(ns);
      }

      @Override
      public void eventCallback(Watch.Response<V1Service> item) {
        processEventCallback(item);
      }

      @Override
      public boolean isStopping() {
        return isStopping.get();
      }
    };
  }
  
  static String getServiceDomainUID(V1Service service) {
    V1ObjectMeta meta = service.getMetadata();
    Map<String, String> labels = meta.getLabels();
    if (labels != null) {
      return labels.get(LabelConstants.DOMAINUID_LABEL);
    }
    return null;
  }
  
  static String getServiceServerName(V1Service service) {
    V1ObjectMeta meta = service.getMetadata();
    Map<String, String> labels = meta.getLabels();
    if (labels != null) {
      return labels.get(LabelConstants.SERVERNAME_LABEL);
    }
    return null;
  }

  static String getServiceChannelName(V1Service service) {
    V1ObjectMeta meta = service.getMetadata();
    Map<String, String> labels = meta.getLabels();
    if (labels != null) {
      return labels.get(LabelConstants.CHANNELNAME_LABEL);
    }
    return null;
  }

  public void processEventCallback(Watch.Response<V1Service> item) {
    destination.eventCallback(item);
  }
}

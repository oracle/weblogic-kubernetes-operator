// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator;

import java.util.concurrent.atomic.AtomicBoolean;

import com.google.gson.reflect.TypeToken;
import io.kubernetes.client.ApiException;
import io.kubernetes.client.models.V1Service;
import io.kubernetes.client.util.Watch;
import oracle.kubernetes.operator.helpers.ClientHelper;
import oracle.kubernetes.operator.helpers.ClientHolder;
import oracle.kubernetes.operator.watcher.Watcher;
import oracle.kubernetes.operator.watcher.Watching;
import oracle.kubernetes.operator.watcher.WatchingEventDestination;

/**
 * This class handles Service watching. It service change events and sends
 * them into the operator for processing.
 */
public class ServiceWatcher implements Runnable {
  private final String ns;
  private final String initialResourceVersion;
  private final WatchingEventDestination<V1Service> destination;
  private final AtomicBoolean isStopping;
  
  public static ServiceWatcher create(String ns, String initialResourceVersion, WatchingEventDestination<V1Service> destination, AtomicBoolean isStopping) {
    ServiceWatcher dlw = new ServiceWatcher(ns, initialResourceVersion, destination, isStopping);
    Thread thread = new Thread(dlw);
    thread.setName("Thread-ServiceWatcher-" + ns);
    thread.setDaemon(true);
    thread.start();
    return dlw;
  }

  private ServiceWatcher(String ns, String initialResourceVersion, WatchingEventDestination<V1Service> destination, AtomicBoolean isStopping) {
    this.ns = ns;
    this.initialResourceVersion = initialResourceVersion;
    this.destination = destination;
    this.isStopping = isStopping;
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
      Watcher<V1Service> watcher = new Watcher<V1Service>(w, null, initialResourceVersion);
      
      // invoke watch on current Thread.  Won't return until watch stops
      watcher.doWatch();
      
    } finally {
      helper.recycle(client);
    }
  }
  
  protected Watching<V1Service> createWatching(ClientHolder client) {
    return new Watching<V1Service>() {

      /**
       * Watcher callback to issue the list Service changes. It is driven by the
       * Watcher wrapper to issue repeated watch requests.
       * @param context user defined contact object or null
       * @param resourceVersion resource version to omit older events
       * @return Watch object or null if the operation should end
       * @throws ApiException if there is an API error.
       */
      @Override
      public Watch<V1Service> initiateWatch(Object context, String resourceVersion) throws ApiException {
        return Watch.createWatch(client.getApiClient(),
            client.callBuilder().with($ -> {
              $.resourceVersion = resourceVersion;
              $.labelSelector = LabelConstants.DOMAINUID_LABEL; // Any Service with a domainUID label
              $.timeoutSeconds = 30;
              $.watch = true;
            }).listServiceCall(ns),
            new TypeToken<Watch.Response<V1Service>>() {
            }.getType());
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
  
  public void processEventCallback(Watch.Response<V1Service> item) {
    destination.eventCallback(item);
  }
}

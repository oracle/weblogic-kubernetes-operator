// Copyright 2017, 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator;

import java.util.concurrent.atomic.AtomicBoolean;

import com.google.gson.reflect.TypeToken;
import io.kubernetes.client.ApiException;
import io.kubernetes.client.util.Watch;
import oracle.kubernetes.operator.domain.model.oracle.kubernetes.weblogic.domain.v1.Domain;
import oracle.kubernetes.operator.helpers.ClientHelper;
import oracle.kubernetes.operator.helpers.ClientHolder;
import oracle.kubernetes.operator.watcher.Watcher;
import oracle.kubernetes.operator.watcher.Watching;
import oracle.kubernetes.operator.watcher.WatchingEventDestination;

/**
 * This class handles Domain watching. It receives domain events and sends
 * them into the operator for processing.
 */
public class DomainWatcher implements Runnable {
  private final String ns;
  private final String initialResourceVersion;
  private final WatchingEventDestination<Domain> destination;
  private final AtomicBoolean isStopping;
  
  public static DomainWatcher create(String ns, String initialResourceVersion, WatchingEventDestination<Domain> destination, AtomicBoolean isStopping) {
    DomainWatcher dlw = new DomainWatcher(ns, initialResourceVersion, destination, isStopping);
    Thread thread = new Thread(dlw);
    thread.setName("Thread-DomainWatcher-" + ns);
    thread.setDaemon(true);
    thread.start();
    return dlw;
  }

  private DomainWatcher(String ns, String initialResourceVersion, WatchingEventDestination<Domain> destination, AtomicBoolean isStopping) {
    this.ns = ns;
    this.initialResourceVersion = initialResourceVersion;
    this.destination = destination;
    this.isStopping = isStopping;
  }

  /**
   * Polling loop. Get the next Domain object event and process it.
   */
  @Override
  public void run() {
    ClientHelper helper = ClientHelper.getInstance();
    ClientHolder client = helper.take();
    try {
      Watching<Domain> w = createWatching(client);
      Watcher<Domain> watcher = new Watcher<Domain>(w, null, initialResourceVersion);
      
      // invoke watch on current Thread.  Won't return until watch stops
      watcher.doWatch();
      
    } finally {
      helper.recycle(client);
    }
  }
  
  protected Watching<Domain> createWatching(ClientHolder client) {
    return new Watching<Domain>() {

      /**
       * Watcher callback to issue the list Domain changes. It is driven by the
       * Watcher wrapper to issue repeated watch requests.
       * @param context user defined contact object or null
       * @param resourceVersion resource version to omit older events
       * @return Watch object or null if the operation should end
       * @throws ApiException if there is an API error.
       */
      @Override
      public Watch<Domain> initiateWatch(Object context, String resourceVersion) throws ApiException {
        return Watch.createWatch(client.getApiClient(),
            client.callBuilder().with($ -> {
              $.resourceVersion = resourceVersion;
              $.timeoutSeconds = 30;
              $.watch = true;
            }).listDomainCall(ns),
            new TypeToken<Watch.Response<Domain>>() {
            }.getType());
      }

      @Override
      public void eventCallback(Watch.Response<Domain> item) {
        processEventCallback(item);
      }

      @Override
      public boolean isStopping() {
        return isStopping.get();
      }
    };
  }
  
  public void processEventCallback(Watch.Response<Domain> item) {
    destination.eventCallback(item);
  }
}

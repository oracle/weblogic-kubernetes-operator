// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator;

import java.util.concurrent.atomic.AtomicBoolean;

import com.google.gson.reflect.TypeToken;
import io.kubernetes.client.ApiException;
import io.kubernetes.client.models.V1beta1Ingress;
import io.kubernetes.client.util.Watch;
import oracle.kubernetes.operator.helpers.ClientHelper;
import oracle.kubernetes.operator.helpers.ClientHolder;
import oracle.kubernetes.operator.watcher.Watcher;
import oracle.kubernetes.operator.watcher.Watching;
import oracle.kubernetes.operator.watcher.WatchingEventDestination;

/**
 * This class handles Ingress watching. It receives Ingress change events and sends
 * them into the operator for processing.
 */
public class IngressWatcher implements Runnable {
  private final String ns;
  private final String initialResourceVersion;
  private final WatchingEventDestination<V1beta1Ingress> destination;
  private final AtomicBoolean isStopping;
  
  public static IngressWatcher create(String ns, String initialResourceVersion, WatchingEventDestination<V1beta1Ingress> destination, AtomicBoolean isStopping) {
    IngressWatcher dlw = new IngressWatcher(ns, initialResourceVersion, destination, isStopping);
    Thread thread = new Thread(dlw);
    thread.setName("Thread-IngressWatcher-" + ns);
    thread.setDaemon(true);
    thread.start();
    return dlw;
  }

  private IngressWatcher(String ns, String initialResourceVersion, WatchingEventDestination<V1beta1Ingress> destination, AtomicBoolean isStopping) {
    this.ns = ns;
    this.initialResourceVersion = initialResourceVersion;
    this.destination = destination;
    this.isStopping = isStopping;
  }

  /**
   * Polling loop. Get the next Ingress object event and process it.
   */
  @Override
  public void run() {
    ClientHelper helper = ClientHelper.getInstance();
    ClientHolder client = helper.take();
    try {
      Watching<V1beta1Ingress> w = createWatching(client);
      Watcher<V1beta1Ingress> watcher = new Watcher<V1beta1Ingress>(w, null, initialResourceVersion);
      
      // invoke watch on current Thread.  Won't return until watch stops
      watcher.doWatch();
      
    } finally {
      helper.recycle(client);
    }
  }
  
  protected Watching<V1beta1Ingress> createWatching(ClientHolder client) {
    return new Watching<V1beta1Ingress>() {

      /**
       * Watcher callback to issue the list Ingress changes. It is driven by the
       * Watcher wrapper to issue repeated watch requests.
       * @param context user defined contact object or null
       * @param resourceVersion resource version to omit older events
       * @return Watch object or null if the operation should end
       * @throws ApiException if there is an API error.
       */
      @Override
      public Watch<V1beta1Ingress> initiateWatch(Object context, String resourceVersion) throws ApiException {
        return Watch.createWatch(client.getApiClient(),
            client.callBuilder().with($ -> {
              $.resourceVersion = resourceVersion;
              $.labelSelector = LabelConstants.DOMAINUID_LABEL; // Any Ingress with a domainUID label
              $.timeoutSeconds = 30;
              $.watch = true;
            }).listIngressCall(ns),
            new TypeToken<Watch.Response<V1beta1Ingress>>() {
            }.getType());
      }

      @Override
      public void eventCallback(Watch.Response<V1beta1Ingress> item) {
        processEventCallback(item);
      }

      @Override
      public boolean isStopping() {
        return isStopping.get();
      }
    };
  }
  
  public void processEventCallback(Watch.Response<V1beta1Ingress> item) {
    destination.eventCallback(item);
  }
}

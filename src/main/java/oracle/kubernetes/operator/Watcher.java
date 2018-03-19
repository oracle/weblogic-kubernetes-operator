// Copyright 2017, 2018 Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator;

import io.kubernetes.client.ApiException;
import io.kubernetes.client.models.V1ObjectMeta;
import io.kubernetes.client.models.V1Status;
import io.kubernetes.client.util.Watch;
import oracle.kubernetes.operator.builders.WatchI;
import oracle.kubernetes.operator.logging.LoggingFacade;
import oracle.kubernetes.operator.logging.LoggingFactory;
import oracle.kubernetes.operator.logging.MessageKeys;
import oracle.kubernetes.operator.watcher.Watching;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.concurrent.atomic.AtomicBoolean;

import static java.net.HttpURLConnection.HTTP_GONE;

/**
 * This class handles the Watching interface and drives the watch support
 * for a specific type of object. It runs in a separate thread to drive
 * watching asynchronously to the main thread.
 *
 * @param <T> The type of the object to be watched.
 */
public class Watcher<T> {
  static final String HAS_NEXT_EXCEPTION_MESSAGE = "IO Exception during hasNext method.";
  private static final LoggingFacade LOGGER = LoggingFactory.getLogger("Operator", "Operator");
  
  private final Watching<T> watching;
  private final AtomicBoolean isDraining = new AtomicBoolean(false);
  private String resourceVersion;

  public Watcher(Watching<T> watching, String resourceVersion) {
    this.watching = watching;
    this.resourceVersion = resourceVersion;
  }
  
  /**
   * Kick off the watcher processing that runs in a separate thread.
   * @return Started thread
   */
  public Thread start(String threadName) {
    Thread thread = new Thread(this::doWatch);
    thread.setName(threadName);
    thread.setDaemon(true);
    thread.start();
    return thread;
  }

  // Are we draining?
  private boolean isDraining() {
    return isDraining.get();
  }

  // Set the draining state.
  private void setIsDraining(boolean isDraining) {
    this.isDraining.set(isDraining);
  }

  /**
   * Start the watching streaming operation in the current thread
   */
  public void doWatch() {
    setIsDraining(false);

    while (!isDraining()) {
      if (watching.isStopping())
        setIsDraining(true);
      else
        watchForEvents();
    }
  }

  private void watchForEvents() {
    try (WatchI<T> watch = watching.initiateWatch(resourceVersion)) {
      while (watch.hasNext()) {
        Watch.Response<T> item = watch.next();

        if (watching.isStopping())
          setIsDraining(true);
        if (isDraining())
          continue;

        if (isError(item))
          handleErrorResponse(item);
        else
          handleRegularUpdate(item);
      }
    } catch (RuntimeException | ApiException | IOException ignored) {
    }
  }

  private boolean isError(Watch.Response<T> item) {
    return item.type.equalsIgnoreCase("ERROR");
  }

  private void handleRegularUpdate(Watch.Response<T> item) {
    LOGGER.fine(MessageKeys.WATCH_EVENT, item.type, item.object);
    trackResourceVersion(item.type, item.object);
    watching.eventCallback(item);
  }

  private void handleErrorResponse(Watch.Response<T> item) {
    V1Status status = item.status;
    if (status.getCode() == HTTP_GONE) {
        String message = status.getMessage();
        int index1 = message.indexOf('(');
        if (index1 > 0) {
            int index2 = message.indexOf(')', index1+1);
            if (index2 > 0) {
                resourceVersion = message.substring(index1+1, index2);
            }
        }
    }
  }

  /**
   * Track resourceVersion and keep highest one for next watch iteration. The
   * resourceVersion is extracted from the metadata in the class by a
   * getter written to return that information. If the getter is not defined
   * then the user will get all watches repeatedly.
   *
   * @param type   the type of operation
   * @param object the object that is returned
   */
  private void trackResourceVersion(String type, Object object) {

    Class<?> cls = object.getClass();
    // This gets tricky because the class might not have a direct getter
    // for resourceVersion. So it is necessary to dig into the metadata
    // object to pull out the resourceVersion.
    V1ObjectMeta metadata;
    try {
      Field metadataField = cls.getDeclaredField("metadata");
      metadataField.setAccessible(true);
      metadata = (V1ObjectMeta) metadataField.get(object);
    } catch (NoSuchFieldException | IllegalAccessException ex) {
      LOGGER.warning(MessageKeys.EXCEPTION, ex);
      return;
    }
    String rv = metadata.getResourceVersion();
    if (type.equalsIgnoreCase("DELETED")) {
      int rv1 = Integer.parseInt(resourceVersion);
      rv = "" + (rv1 + 1);
    }
    if (resourceVersion == null || resourceVersion.length() < 1) {
      resourceVersion = rv;
    } else {
      if ( rv.compareTo(resourceVersion) > 0 ) {  
        resourceVersion = rv;
      }
    }
  }
}

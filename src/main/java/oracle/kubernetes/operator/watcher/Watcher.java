// Copyright 2017, 2018 Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.watcher;

import com.google.gson.annotations.SerializedName;
import com.squareup.okhttp.ResponseBody;
import io.kubernetes.client.ApiException;
import io.kubernetes.client.JSON;
import io.kubernetes.client.models.V1ObjectMeta;
import io.kubernetes.client.models.V1Status;
import io.kubernetes.client.util.Watch;
import oracle.kubernetes.operator.logging.LoggingFacade;
import oracle.kubernetes.operator.logging.LoggingFactory;
import oracle.kubernetes.operator.logging.MessageKeys;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Type;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * This class handles the Watching interface and drives the watch support
 * for a specific type of object. It runs in a separate thread to drive
 * watching asynchronously to the main thread.
 *
 * @param <T> The type of the object to be watched.
 */
public class Watcher<T> {
  private static final LoggingFacade LOGGER = LoggingFactory.getLogger("Operator", "Operator");
  
  private final Watching<T> watching;
  private final Object userContext;
  private final AtomicBoolean isAlive = new AtomicBoolean(true);
  private final AtomicBoolean isDraining = new AtomicBoolean(false);
  private String resourceVersion = "";
  private Watch<T> watch = null;

  /*
   * ErrorResponse is used when the response is not returning data but an error
   * from the server.
   */
  private class ErrorResponse {
    @SerializedName("type")
    public String type;

    @SerializedName("object")
    public V1Status object;
  }

  // These are used to simulate Watch.next
  private Type watchType;
  private ResponseBody response;
  private JSON json;

  public Watcher(Watching<T> watching) {
    this(watching, null);
  }

  public Watcher(Watching<T> watching, Object context) {
    this(watching, context, "");
  }

  public Watcher(Watching<T> watching, Object context, String resourceVersion) {
    this.watching = watching;
    this.userContext = context;
    this.resourceVersion = resourceVersion; 
  }
  
  /**
   * Kick off the watcher processing that runs in a separate thread.
   */
  public void start() {
    Thread thread = new Thread(() -> { doWatch(); });
    thread.setName("Watcher");
    thread.setDaemon(true);
    thread.start();
  }

  // Are we draining?
  private boolean isDraining() {
    return isDraining.get();
  }

  // Set the draining state.
  private void setIsDraining(boolean isDraining) {
    this.isDraining.set(isDraining);
  }

  // Thread still running?
  private boolean isAlive() {
    return isAlive.get();
  }

  // Set thread status
  private void setIsAlive(boolean isAlive) {
    this.isAlive.set(isAlive);
  }

  // Necessary to fool OKhttp that there are no response leaks.
  private void watchClose() {

    if (watch != null) {

      // This fixes API bug where response close method is missing from
      // default Watch object.
      Class<?> cls = watch.getClass();
      try {
        Field responseField = cls.getDeclaredField("response");
        responseField.setAccessible(true);
        ResponseBody responseBody = (ResponseBody) responseField.get(watch);

        responseBody.close();
      } catch (NoSuchFieldException | IllegalAccessException | IOException ex) {
        LOGGER.warning(MessageKeys.EXCEPTION, ex);
      }
    }
  }

  /**
   * Tell the watcher to gracefully terminate.
   */
  public void closeAndDrain() {

    setIsDraining(true);

    // Wait for thread to die gracefully.
    while (isAlive()) {
      try {  
         Thread.sleep(500);
      }
      catch ( InterruptedException ir ) {
          // Ignore this exception
      }
    }
  }

  /**
   * Start the watching streaming operation in the current thread
   */
  public void doWatch() {

    setIsAlive(true);
    setIsDraining(false);

    // Loop around doing the watch dance until draining.
    while (!isDraining()) {
      try {
        if (watching.isStopping()) {
          setIsDraining(true);
          break;
        }
        
        watch = (Watch<T>) watching.initiateWatch(userContext, resourceVersion);
        if (watch == null) {
          // Method override wants to terminate the watch cycle
          setIsDraining(true);
          break;
        }

        // Pickup essential fields in Watch object so the Watch.next
        // can be simulated in this class. 
        Class<?> cls = watch.getClass();
        try {
           Field responseField = cls.getDeclaredField("response");
           responseField.setAccessible(true);
           response = (ResponseBody) responseField.get(watch);
           Field watchTypeField = cls.getDeclaredField("watchType");
           watchTypeField.setAccessible(true);
           watchType = (Type) watchTypeField.get(watch);
           Field jsonField = cls.getDeclaredField("json");
           jsonField.setAccessible(true);
           json = (JSON) jsonField.get(watch);
        } catch (NoSuchFieldException | IllegalAccessException ex) {
           LOGGER.warning(MessageKeys.EXCEPTION, ex);
        }
              
        while ( watch.hasNext() ) {
            
          Watch.Response<T> item = simulateWatchNext(watch);     

          if (watching.isStopping()) {
            setIsDraining(true);
          }
          if (isDraining()) {
            // When draining just throw away anything new.
            continue;
          }

          LOGGER.fine(MessageKeys.WATCH_EVENT, item.type, item.object);

          if (item.type.equalsIgnoreCase("ERROR")) {
            // Check the type of error. If code is 410 meaning
            // resource is gone then extract current resourceVersion 
            // from message and use it to resync with server. 
            V1Status status = (V1Status)item.object;
            if ( status.getCode() == 410 ) {
                String message = status.getMessage();
                int index1 = message.indexOf('(');
                if ( index1 > 0 ) {
                    int index2 = message.indexOf(')', index1+1);
                    if ( index2 > 0 ) {
                        resourceVersion = message.substring(index1+1, index2);
                        continue; 
                    }
                }
            }
            // Allow error to be reflected to watcher
          }
          else {
            // Track the resourceVersion assuming the user has setup
            // the watch target class correctly.
            trackResourceVersion(item.type, item.object);
          }
          // invoke callback
          watching.eventCallback(item);
        }
        
        // So OKhttp doesn't think responses are leaking.
        watchClose();
      } catch (RuntimeException | ApiException apiException ) {
        String message = apiException.getMessage();
        // Treat hasNext as a soft error because no watch events have
        // arrived in the latest cycle. Just quietly reissue the watch request.
        if (message != null && message.equals("IO Exception during hasNext method.")) {
          // Close the timed out request OKhttp doesn't think responses are leaking.
          watchClose();
          continue;
        }
        // Something bad has happened.
        LOGGER.warning(MessageKeys.EXCEPTION, apiException);
        // This is a horrible hack but it is necessary until deserialization of
        // type=ERROR, Kind=status is fixed. The error provides an updated
        // resourceVersion but since we can't get the value from the response the
        // only recovery possible is rolling the resourceVersion by 1 until
        // synchronized with the server.
        if ( resourceVersion.length() > 0 ) {
          int rv = Integer.parseInt(resourceVersion);
          rv++;
          resourceVersion = "" + rv;
        }
      }
    }
    // Say goodnight, Gracie.
    setIsAlive(false);
  }
  
  /**
   * Simulate the Watch.next method so the typed class and ERROR responses
   * can be properly de-serialized. 
   * @param watch Watch object 
   * @return Watch.Response<T> for this item
   */
  private Watch.Response<T> simulateWatchNext(Watch watch) {
      
      // If reflection failed then just use original method
      if ( response == null || watchType == null || json == null ) {
          return watch.next();
      }
    try {
        String line = response.source().readUtf8Line();
        if (line == null) {
            throw new RuntimeException("Null response from the server.");
        }

        // Check if an error is being returned. 
        if ( line.startsWith("{\"type\":\"ERROR\"") ) {
            // WE have a winner. De-serialize using Error object
            ErrorResponse er = json.deserialize(line, ErrorResponse.class);
            try { 
               // We need to generate a fake Watch.Response to avoid a class
               // cast issue when returning. Reflection is used to generate 
               // an instance of the response that is populated from the
               // error response that was just de-serialized. 
               Class<?> cls = Watch.Response.class; 
               Class[] cArgs = new Class[2];
               cArgs[0] = String.class; 
               cArgs[1] = Object.class; 
               
               Constructor<?> constructor = cls.getDeclaredConstructor(cArgs);
               constructor.setAccessible(true);
 
               Watch.Response resp = (Watch.Response) 
                       constructor.newInstance(er.type, er.object);
               return resp;
            } catch ( InstantiationException | IllegalAccessException | NoSuchMethodException |
                      InvocationTargetException ex ) {  
               // Shrug. Reflection failed so throw a RuntimeException in 
               // desparation. This should not happen unless K8S changed their
               // Watch class. 
               throw new RuntimeException("Watch.Response reflection failed - " + ex); 
            }
        }
        return json.deserialize(line, watchType);
    } catch (IOException e) {
        throw new RuntimeException("IO Exception during next method.", e);
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

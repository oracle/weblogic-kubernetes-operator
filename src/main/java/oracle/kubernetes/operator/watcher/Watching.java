// Copyright 2017, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.watcher;

import io.kubernetes.client.ApiException;
import io.kubernetes.client.util.Watch;

/**
 * This interface is used to drive the user's watch request by call backs within
 * an anonymous class or implemented by a named class.
 *
 * @param <T> The type of the object that is being watched.
 */
public interface Watching<T> extends WatchingEventDestination<T> {
  /**
   * Initiate a watch operation, repeated when timed out by framework.
   *
   * @param api Optional context object or null.
   * @param resourceVersion Provided resourceVersion from last event
   * @return Watch object returned from API.
   * @throws ApiException in the event of an API error.
   */
  public Watch<T> initiateWatch(Object api, String resourceVersion) throws ApiException;

  /**
   * Return true when the watch process should stop
   * @return true, if its time to stop
   */
  public boolean isStopping();
}

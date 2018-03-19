// Copyright 2017, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.watcher;

import io.kubernetes.client.ApiException;
import oracle.kubernetes.operator.builders.WatchBuilder;
import oracle.kubernetes.operator.builders.WatchI;

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
   * @param resourceVersion Provided resourceVersion from last event
   * @return Watch object returned from API.
   * @throws ApiException in the event of an API error.
   */
  public WatchI<T> initiateWatch(String resourceVersion) throws ApiException;

  WatchI<T> initiateWatch(WatchBuilder watchBuilder) throws ApiException;

  /**
   * Return true when the watch process should stop
   * @return true, if its time to stop
   */
  public boolean isStopping();
}

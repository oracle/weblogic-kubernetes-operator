// Copyright 2017, 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.watcher;

import io.kubernetes.client.util.Watch;

/**
 * This interface is used for the final destination to deliver watch events.
 *
 * @param <T> The type of the object that is being watched.
 */
@FunctionalInterface
public interface WatchListener<T> {
  /**
   * Call back for any watch type. This can be used instead of the specific call backs to handle any
   * type of watch response.
   *
   * @param response Watch response consisting of type and object
   */
  public void receivedResponse(Watch.Response<T> response);
}

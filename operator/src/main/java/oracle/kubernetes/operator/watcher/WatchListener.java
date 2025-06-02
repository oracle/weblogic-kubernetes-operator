// Copyright (c) 2017, 2025, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.watcher;

import io.kubernetes.client.common.KubernetesObject;
import io.kubernetes.client.util.Watch;

/**
 * This interface is used for the final destination to deliver watch events.
 *
 * @param <T> The type of the object that is being watched.
 */
@FunctionalInterface
public interface  WatchListener<T extends KubernetesObject> {
  /**
   * Call back for any watch type. This can be used instead of the specific call backs to handle any
   * type of watch response.
   *
   * @param response Watch response consisting of type and object
   */
  void receivedResponse(Watch.Response<T> response);
}

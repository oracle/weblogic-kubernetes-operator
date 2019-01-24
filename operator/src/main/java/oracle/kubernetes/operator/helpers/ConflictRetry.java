// Copyright 2019, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helpers;

/**
 * Interface used by CallBuilder to obtain the latest version of the Kubernetes object for retrying
 * synchronous API calls that previously failed with Conflict response code (409). This indicates an
 * optimistic locking failure and the kubernetes object has since been modified. The synchoronus API
 * can be retried with the latest version of the kubernetes object.
 *
 * @param <T> Type of kubernetes object to be passed to the API
 */
public interface ConflictRetry<T> {

  /**
   * @return The latest version of the kubernetes object for passing to the kubernetes API, or null
   *     if the API should not be retried
   */
  T getUpdatedObject();
}

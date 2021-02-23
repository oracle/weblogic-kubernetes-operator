// Copyright (c) 2019, 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helpers;

/**
 * Interface used by CallBuilder to obtain the latest version of the Kubernetes object for retrying
 * synchronous API calls that previously failed with Conflict response code (409). This indicates an
 * optimistic locking failure and the kubernetes object has since been modified. The synchronous API
 * can be retried with the latest version of the kubernetes object.
 *
 * @param <T> Type of kubernetes object to be passed to the API
 */
public interface ConflictRetry<T> {

}

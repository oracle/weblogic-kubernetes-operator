// Copyright (c) 2018, 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.builders;

import io.kubernetes.client.openapi.ApiException;

/**
 * An exception used to bypass functional programming incompatibility with checked exceptions. This
 * is thrown by a function object and the underlying ApiException is then rethrown by the caller of
 * the function object.
 */
@SuppressWarnings("serial")
class UncheckedApiException extends RuntimeException {
  UncheckedApiException(ApiException e) {
    super(e);
  }

  @Override
  public synchronized ApiException getCause() {
    return (ApiException) super.getCause();
  }
}

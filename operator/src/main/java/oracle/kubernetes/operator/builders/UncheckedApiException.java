// Copyright (c) 2018, 2023, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.builders;

import java.io.Serial;

import io.kubernetes.client.openapi.ApiException;

/**
 * An exception used to bypass functional programming incompatibility with checked exceptions. This
 * is thrown by a function object and the underlying ApiException is then rethrown by the caller of
 * the function object.
 */
class UncheckedApiException extends RuntimeException {
  @Serial
  private static final long serialVersionUID  = 1L;

  UncheckedApiException(ApiException e) {
    super(e);
  }

  @Override
  public synchronized ApiException getCause() {
    return (ApiException) super.getCause();
  }
}

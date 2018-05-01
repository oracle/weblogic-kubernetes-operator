// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.calls;

import java.util.List;
import java.util.Map;

import io.kubernetes.client.ApiException;

public final class CallResponse<T> {
  public final T result;
  public final ApiException e;
  public final int statusCode;
  public final Map<String, List<String>> responseHeaders;

  public CallResponse(T result, ApiException e, int statusCode, Map<String, List<String>> responseHeaders) {
    this.result = result;
    this.e = e;
    this.statusCode = statusCode;
    this.responseHeaders = responseHeaders;
  }
}

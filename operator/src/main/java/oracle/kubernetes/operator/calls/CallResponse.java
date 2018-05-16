// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.calls;

import io.kubernetes.client.ApiException;
import java.util.List;
import java.util.Map;

public final class CallResponse<T> {
  private final T result;
  private final ApiException e;
  private final int statusCode;
  private final Map<String, List<String>> responseHeaders;

  public CallResponse(
      T result, ApiException e, int statusCode, Map<String, List<String>> responseHeaders) {
    this.result = result;
    this.e = e;
    this.statusCode = statusCode;
    this.responseHeaders = responseHeaders;
  }

  public boolean isFailure() {
    return e != null;
  }

  public T getResult() {
    return result;
  }

  public ApiException getE() {
    return e;
  }

  public int getStatusCode() {
    return statusCode;
  }

  public Map<String, List<String>> getResponseHeaders() {
    return responseHeaders;
  }
}

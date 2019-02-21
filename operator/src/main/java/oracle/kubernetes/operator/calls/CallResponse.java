// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.calls;

import io.kubernetes.client.ApiException;
import java.util.List;
import java.util.Map;

public final class CallResponse<T> {
  private final T result;
  private final ApiException ex;
  private final int statusCode;
  private final Map<String, List<String>> responseHeaders;

  /**
   * Constructor for CallResponse.
   *
   * @param result Result
   * @param ex API exception
   * @param statusCode Status code
   * @param responseHeaders Response headers
   */
  public CallResponse(
      T result, ApiException ex, int statusCode, Map<String, List<String>> responseHeaders) {
    this.result = result;
    this.ex = ex;
    this.statusCode = statusCode;
    this.responseHeaders = responseHeaders;
  }

  public boolean isFailure() {
    return ex != null;
  }

  public T getResult() {
    return result;
  }

  public ApiException getE() {
    return ex;
  }

  public int getStatusCode() {
    return statusCode;
  }

  public Map<String, List<String>> getResponseHeaders() {
    return responseHeaders;
  }
}

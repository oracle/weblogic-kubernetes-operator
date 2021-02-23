// Copyright (c) 2018, 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.calls;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import io.kubernetes.client.openapi.ApiException;

public final class CallResponse<T> {
  private final RequestParams requestParams;
  private final T result;
  private final ApiException ex;
  private final int statusCode;
  private Map<String, List<String>> responseHeaders;

  public static <R> CallResponse<R> createSuccess(RequestParams requestParams, R result, int statusCode) {
    return new CallResponse<>(requestParams,  result, null, statusCode);
  }

  public static CallResponse<Void> createFailure(RequestParams requestParams, ApiException ex, int statusCode) {
    return new CallResponse<>(requestParams, null, ex, statusCode);
  }

  public static <R> CallResponse<R> createNull() {
    return new CallResponse<>(null, null, null, 0);
  }

  CallResponse<T> withResponseHeaders(Map<String, List<String>> responseHeaders) {
    this.responseHeaders = responseHeaders;
    return this;
  }

  /**
   * Constructor for CallResponse.
   *
   * @param requestParams Request parameters
   * @param result Result
   * @param ex API exception
   * @param statusCode Status code
   */
  private CallResponse(RequestParams requestParams, T result, ApiException ex, int statusCode) {
    this.requestParams = requestParams;
    this.result = result;
    this.ex = ex;
    this.statusCode = statusCode;
  }

  public boolean isFailure() {
    return ex != null;
  }

  public RequestParams getRequestParams() {
    return requestParams;
  }

  public T getResult() {
    return result;
  }

  public ApiException getE() {
    return ex;
  }

  public String getExceptionString() {
    return Optional.ofNullable(ex).map(Throwable::toString).orElse("");
  }

  public int getStatusCode() {
    return statusCode;
  }

  public String getHeadersString() {
    return Optional.ofNullable(responseHeaders).map(Object::toString).orElse("");
  }
  
}

// Copyright (c) 2019, 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.calls;

import io.kubernetes.client.openapi.ApiException;
import oracle.kubernetes.operator.calls.unprocessable.UnrecoverableErrorBuilderImpl;

public class UnrecoverableErrorBuilder {

  private UnrecoverableErrorBuilder() {
    // no-op
  }

  /**
   * Returns true if the specified call response indicates an unrecoverable response from Kubernetes.
   * @param <T> call response type
   * @param callResponse the response from a Kubernetes call
   * @return true if an unprocessable entity failure has been reported
   */
  public static <T> boolean isAsyncCallUnrecoverableFailure(CallResponse<T> callResponse) {
    return callResponse.isFailure() && isUnrecoverable(callResponse.getE());
  }

  /**
   * Returns true if the specified call response indicates a NotFound/Gone response from Kubernetes.
   * @param callResponse the response from a Kubernetes call
   * @param <T> call response type
   * @return true if a NotFound/Gone entity failure has been reported
   */
  public static <T> boolean isAsyncCallNotFoundFailure(CallResponse<T> callResponse) {
    return callResponse.isFailure() && isNotFound(callResponse.getE());
  }

  public static <T> boolean isAsyncCallConflictFailure(CallResponse<T> callResponse) {
    return callResponse.isFailure() && hasConflict(callResponse.getE());
  }

  private static boolean isUnrecoverable(ApiException e) {
    return UnrecoverableErrorBuilderImpl.isUnrecoverable(e);
  }

  private static boolean isNotFound(ApiException e) {
    return UnrecoverableErrorBuilderImpl.isNotFound(e);
  }

  private static boolean hasConflict(ApiException e) {
    return UnrecoverableErrorBuilderImpl.hasConflict(e);
  }

  /**
   * Populate FailureStatusSource from a failed call response.
   * @param callResponse the failed call response
   * @return status source object
   */
  public static FailureStatusSource fromFailedCall(CallResponse<?> callResponse) {
    return UnrecoverableErrorBuilderImpl.fromFailedCall(callResponse);
  }

  /**
   * Throws FailureStatusSourceException generated from a failed call response.
   * @param callResponse the failed call response
   * @return exception bearing status source object
   */
  public static Exception createExceptionFromFailedCall(CallResponse<?> callResponse) {
    return new UnrecoverableCallException(fromFailedCall(callResponse), callResponse.getE());
  }
}

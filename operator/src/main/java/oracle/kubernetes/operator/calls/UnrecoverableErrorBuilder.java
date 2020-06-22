// Copyright (c) 2019, 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.calls;

import io.kubernetes.client.openapi.ApiException;
import oracle.kubernetes.operator.calls.unprocessable.UnprocessableEntityBuilder;

public class UnrecoverableErrorBuilder {

  /**
   * Returns true if the specified call response indicates an unprocessable entity response from Kubernetes.
   * @param callResponse the response from a Kubernetes call
   * @param <T> call response type
   * @return true if an unprocessable entity failure has been reported
   */
  public static <T> boolean isAsyncCallFailure(CallResponse<T> callResponse) {
    return callResponse.isFailure() && isUnrecoverable(callResponse.getE());
  }

  private static boolean isUnrecoverable(ApiException e) {
    return OtherUnrecoverableErrorBuilder.isUnrecoverable(e) || UnprocessableEntityBuilder.isUnprocessableEntity(e);
  }

  /**
   * Populate FailureStatusSource from a failed call response.
   * @param callResponse the failed call response
   * @return status source object
   */
  public static FailureStatusSource fromFailedCall(CallResponse callResponse) {
    ApiException apiException = callResponse.getE();
    if (UnprocessableEntityBuilder.isUnprocessableEntity(apiException)) {
      return UnprocessableEntityBuilder.fromFailedCall(callResponse);
    } else {
      return OtherUnrecoverableErrorBuilder.fromFailedCall(callResponse);
    }
  }

  /**
   * Throws FailureStatusSourceException generated from a failed call response.
   * @param callResponse the failed call response
   * @return exception bearing status source object
   */
  public static FailureStatusSourceException createExceptionFromFailedCall(CallResponse callResponse) {
    ApiException apiException = callResponse.getE();
    return new FailureStatusSourceException(fromFailedCall(callResponse), apiException);
  }
}

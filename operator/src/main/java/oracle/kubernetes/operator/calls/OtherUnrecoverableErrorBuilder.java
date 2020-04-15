// Copyright (c) 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.calls;

import io.kubernetes.client.openapi.ApiException;

/**
 * A builder for 'forbidden' and other unrecoverable async results.
 */
public class OtherUnrecoverableErrorBuilder implements FailureStatusSource {
  private final String message;
  private final int code;

  private OtherUnrecoverableErrorBuilder(CallResponse callResponse) {
    this.message = callResponse.getRequestParams().toString(false)
        + ": " + callResponse.getE().getMessage();
    this.code = callResponse.getStatusCode();
  }

  /**
   * Create a ForbiddenErrorBuilder from the provided failed call.
   * @param callResponse the failed call
   * @return the ForbiddenErrorBuilder
   */
  public static OtherUnrecoverableErrorBuilder fromFailedCall(CallResponse callResponse) {
    return new OtherUnrecoverableErrorBuilder(callResponse);
  }

  public static boolean isUnrecoverable(ApiException e) {
    int code = e.getCode();
    return code == 400 || code == 401 || code == 403 || code == 404 || code == 405 || code == 410;
  }

  @Override
  public String getMessage() {
    return message;
  }

  @Override
  public String getReason() {
    switch (code) {
      case 400:
        return "Bad Request";
      case 401:
        return "Unauthorized";
      case 403:
        return "Forbidden";
      case 404:
        return "Not Found";
      case 405:
        return "Method Not Allowed";
      case 410:
        return "Gone";
      default:
        return String.valueOf(code);
    }
  }
}

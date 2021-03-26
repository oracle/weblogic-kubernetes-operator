// Copyright (c) 2019, 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.calls.unprocessable;

import java.util.Collections;
import java.util.Optional;

import com.google.gson.Gson;
import io.kubernetes.client.openapi.ApiException;
import oracle.kubernetes.operator.calls.CallResponse;
import oracle.kubernetes.operator.calls.FailureStatusSource;

public class UnrecoverableErrorBuilderImpl implements FailureStatusSource {
  private final String message;
  private final int code;
  private final ErrorBody errorBody;

  /**
   * Create an UnrecoverableErrorBuilder from the provided failed call.
   * @param callResponse the failed call
   * @return the FailureStatusSource
   */
  public static FailureStatusSource fromFailedCall(CallResponse callResponse) {
    return new UnrecoverableErrorBuilderImpl(callResponse);
  }

  private UnrecoverableErrorBuilderImpl(CallResponse callResponse) {
    message = callResponse.getRequestParams().toString(false);
    code = callResponse.getStatusCode();
    ErrorBody eb = new Gson().fromJson(callResponse.getE().getResponseBody(), ErrorBody.class);
    if (eb == null) {
      eb = new ErrorBody();
      eb.setMessage(callResponse.getE().getMessage());
      eb.addDetails();
      eb.getDetails().addCause(new Cause().withReason(getReasonCode()));
    }
    errorBody = eb;
  }

  private String getReasonCode() {
    switch (code) {
      case 400:
        return "BadRequest";
      case 401:
        return "Unauthorized";
      case 403:
        return "Forbidden";
      case 404:
        return "NotFound";
      case 405:
        return "MethodNotAllowed";
      case 410:
        return "Gone";
      case 500:
        return "ServerError";
      default:
        return String.valueOf(code);
    }
  }

  public static boolean isUnrecoverable(ApiException e) {
    int code = e.getCode();
    return code == 400 || code == 401 || code == 403 || code == 404 || code == 405 || code == 410 || code == 500;
  }

  public static boolean isNotFound(ApiException e) {
    int code = e.getCode();
    return code == 404 || code == 410;
  }

  /**
   * Create unrecoverable error builder.
   */
  public UnrecoverableErrorBuilderImpl() {
    message = "";
    code = 500;
    errorBody = new ErrorBody();
  }

  @Override
  public String getMessage() {
    return message + ": " + errorBody.getMessage();
  }

  @Override
  public String getReason() {
    return Optional.ofNullable(errorBody.getDetails())
        .map(ErrorDetails::getCauses)
        .filter(list -> list.length != 0)
        .map(n -> n[0])
        .map(Cause::getReason)
        .orElse("");
  }

  /**
   * Build with reason.
   * @param reason reason
   * @return builder
   */
  public UnrecoverableErrorBuilderImpl withReason(String reason) {
    if (errorBody.getDetails() == null) {
      errorBody.addDetails();
    }
    errorBody.getDetails().addCause(new Cause().withReason(reason));
    return this;
  }

  public UnrecoverableErrorBuilderImpl withMessage(String message) {
    errorBody.setMessage(message);
    return this;
  }

  public ApiException build() {
    return new ApiException(code, Collections.emptyMap(), createMessageBody());
  }

  private String createMessageBody() {
    return new Gson().toJson(errorBody);
  }
}

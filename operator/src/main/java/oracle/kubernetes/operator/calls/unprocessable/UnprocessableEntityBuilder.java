// Copyright (c) 2019, 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.calls.unprocessable;

import java.util.Collections;

import com.google.gson.Gson;
import io.kubernetes.client.openapi.ApiException;
import oracle.kubernetes.operator.calls.CallResponse;
import oracle.kubernetes.operator.calls.FailureStatusSource;

public class UnprocessableEntityBuilder implements FailureStatusSource {
  static final int HTTP_UNPROCESSABLE_ENTITY = 422;

  private final String message;
  private final ErrorBody errorBody;

  /**
   * Create an UnprocessableEntityBuilder from the provided failed call.
   * @param callResponse the failed call
   * @return the UnprocessableEntityBuilder
   */
  public static UnprocessableEntityBuilder fromFailedCall(CallResponse callResponse) {
    ApiException apiException = callResponse.getE();
    if (apiException.getCode() != HTTP_UNPROCESSABLE_ENTITY) {
      throw new IllegalArgumentException("Is not unprocessable entity exception");
    }

    return new UnprocessableEntityBuilder(callResponse);
  }

  private UnprocessableEntityBuilder(CallResponse callResponse) {
    message = callResponse.getRequestParams().toString(false);
    errorBody = new Gson().fromJson(callResponse.getE().getResponseBody(), ErrorBody.class);
  }

  public static boolean isUnprocessableEntity(ApiException exception) {
    return exception.getCode() == HTTP_UNPROCESSABLE_ENTITY;
  }

  public UnprocessableEntityBuilder() {
    message = "";
    errorBody = new ErrorBody();
  }

  @Override
  public String getMessage() {
    return message + ": " + errorBody.getMessage();
  }

  @Override
  public String getReason() {
    return errorBody.getDetails().getCauses()[0].getReason();
  }

  /**
   * Build with reason.
   * @param reason reason
   * @return builder
   */
  public UnprocessableEntityBuilder withReason(String reason) {
    if (errorBody.getDetails() == null) {
      errorBody.addDetails();
    }
    errorBody.getDetails().addCause(new Cause().withReason(reason));
    return this;
  }

  public UnprocessableEntityBuilder withMessage(String message) {
    errorBody.setMessage(message);
    return this;
  }

  public ApiException build() {
    return new ApiException(HTTP_UNPROCESSABLE_ENTITY, Collections.emptyMap(), createMessageBody());
  }

  private String createMessageBody() {
    return new Gson().toJson(errorBody);
  }
}

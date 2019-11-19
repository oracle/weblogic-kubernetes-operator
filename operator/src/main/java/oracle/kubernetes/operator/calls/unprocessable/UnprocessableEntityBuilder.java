// Copyright (c) 2019, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.calls.unprocessable;

import java.util.Collections;

import com.google.gson.Gson;
import io.kubernetes.client.ApiException;
import oracle.kubernetes.operator.calls.FailureStatusSource;

public class UnprocessableEntityBuilder implements FailureStatusSource {
  static final int HTTP_UNPROCESSABLE_ENTITY = 422;
  private ErrorBody errorBody;

  public static UnprocessableEntityBuilder fromException(ApiException exception) {
    if (exception.getCode() != HTTP_UNPROCESSABLE_ENTITY)
      throw new IllegalArgumentException("Is not unprocessable entity exception");

    return new UnprocessableEntityBuilder(exception);
  }

  private UnprocessableEntityBuilder(ApiException exception) {
    errorBody = new Gson().fromJson(exception.getResponseBody(), ErrorBody.class);
  }

  public static boolean isUnprocessableEntity(ApiException exception) {
    return exception.getCode() == HTTP_UNPROCESSABLE_ENTITY;
  }

  public UnprocessableEntityBuilder() {
    errorBody = new ErrorBody();
  }

  @Override
  public String getMessage() {
    return errorBody.getMessage();
  }

  @Override
  public String getReason() {
    return errorBody.getDetails().getCauses()[0].getReason();
  }

  public UnprocessableEntityBuilder withReason(String reason) {
    if (errorBody.getDetails() == null) errorBody.addDetails();
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

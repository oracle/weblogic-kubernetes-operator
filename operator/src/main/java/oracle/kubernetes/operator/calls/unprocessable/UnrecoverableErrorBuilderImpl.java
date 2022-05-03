// Copyright (c) 2019, 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.calls.unprocessable;

import java.util.Collections;
import java.util.Set;

import com.google.gson.Gson;
import io.kubernetes.client.openapi.ApiException;
import oracle.kubernetes.operator.calls.CallResponse;
import oracle.kubernetes.operator.calls.FailureStatusSource;

import static oracle.kubernetes.operator.KubernetesConstants.HTTP_BAD_METHOD;
import static oracle.kubernetes.operator.KubernetesConstants.HTTP_BAD_REQUEST;
import static oracle.kubernetes.operator.KubernetesConstants.HTTP_CONFLICT;
import static oracle.kubernetes.operator.KubernetesConstants.HTTP_FORBIDDEN;
import static oracle.kubernetes.operator.KubernetesConstants.HTTP_GONE;
import static oracle.kubernetes.operator.KubernetesConstants.HTTP_INTERNAL_ERROR;
import static oracle.kubernetes.operator.KubernetesConstants.HTTP_NOT_FOUND;
import static oracle.kubernetes.operator.KubernetesConstants.HTTP_UNAUTHORIZED;
import static oracle.kubernetes.operator.KubernetesConstants.HTTP_UNPROCESSABLE_ENTITY;
import static oracle.kubernetes.weblogic.domain.model.DomainFailureReason.KUBERNETES;

/**
 * An object which encapsulates a human-readable description of a failure, along with information needed by the
 * operator to handle it.
 */
public class UnrecoverableErrorBuilderImpl implements FailureStatusSource {
  private final String message;
  private final int code;
  private final ErrorBody errorBody;

  /**
   * Create a description of the failure from the provided failed call.
   * @param callResponse the failed call
   * @return the FailureStatusSource
   */
  public static FailureStatusSource fromFailedCall(CallResponse<?> callResponse) {
    return new UnrecoverableErrorBuilderImpl(callResponse);
  }

  private UnrecoverableErrorBuilderImpl(CallResponse<?> callResponse) {
    message = callResponse.createFailureMessage();
    code = callResponse.getStatusCode();
    ErrorBody eb = new Gson().fromJson(callResponse.getE().getResponseBody(), ErrorBody.class);
    if (eb == null) {
      eb = new ErrorBody();
      eb.setMessage(callResponse.getE().getMessage());
      eb.addDetails();
    }
    errorBody = eb;
  }

  private static final Set<Integer> UNRECOVERABLE_ERROR_CODES = Set.of(
        HTTP_BAD_REQUEST, HTTP_UNAUTHORIZED, HTTP_FORBIDDEN, HTTP_NOT_FOUND,
        HTTP_BAD_METHOD, HTTP_GONE, HTTP_UNPROCESSABLE_ENTITY, HTTP_INTERNAL_ERROR);

  public static boolean isUnrecoverable(ApiException e) {
    return UNRECOVERABLE_ERROR_CODES.contains(e.getCode());
  }

  public static boolean isNotFound(ApiException e) {
    int code = e.getCode();
    return code == HTTP_NOT_FOUND || code == HTTP_GONE;
  }

  public static boolean hasConflict(ApiException e) {
    return e.getCode() == HTTP_CONFLICT;
  }

  /**
   * Create unrecoverable error builder.
   */
  public UnrecoverableErrorBuilderImpl() {
    message = "";
    code = HTTP_INTERNAL_ERROR;
    errorBody = new ErrorBody();
  }

  @Override
  public String getMessage() {
    return message + ": " + errorBody.getMessage();
  }

  @Override
  public String getReason() {
    return KUBERNETES.toString();
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

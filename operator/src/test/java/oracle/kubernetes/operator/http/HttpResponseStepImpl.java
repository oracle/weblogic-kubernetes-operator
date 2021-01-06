// Copyright (c) 2020, 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.http;

import java.net.http.HttpResponse;

import oracle.kubernetes.operator.work.NextAction;
import oracle.kubernetes.operator.work.Packet;
import oracle.kubernetes.operator.work.Step;

public class HttpResponseStepImpl extends HttpResponseStep {
  private HttpResponse<String> successResponse;
  private HttpResponse<String> failureResponse;

  public HttpResponseStepImpl(Step step) {
    super(step);
  }

  HttpResponse<String> getSuccessResponse() {
    return successResponse;
  }

  HttpResponse<String> getFailureResponse() {
    return failureResponse;
  }

  @Override
  public NextAction onSuccess(Packet packet, HttpResponse<String> response) {
    successResponse = response;
    return doNext(packet);
  }

  @Override
  public NextAction onFailure(Packet packet, HttpResponse<String> response) {
    failureResponse = response;
    return doNext(packet);
  }
}

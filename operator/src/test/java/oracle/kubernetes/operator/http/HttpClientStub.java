// Copyright (c) 2019, 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.http;

public abstract class HttpClientStub extends HttpClient {

  String response = "{}";
  int status = 200;
  boolean successful = true;

  public HttpClientStub() {
    super(null, null);
  }

  @Override
  public Result executePostUrlOnServiceClusterIP(
      String requestUrl, String serviceUrl, String payload, boolean throwOnFailure)
      throws HttpException {
    return new Result(response, status, successful);
  }

  public HttpClientStub withResponse(String response) {
    this.response = response;
    return this;
  }

  public HttpClientStub withStatus(int status) {
    this.status = status;
    return this;
  }

  public HttpClientStub withSuccessful(boolean successful) {
    this.successful = successful;
    return this;
  }
}

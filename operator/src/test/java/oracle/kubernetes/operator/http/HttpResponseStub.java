// Copyright (c) 2020, 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.http;

import java.net.http.HttpResponse;

public abstract class HttpResponseStub implements HttpResponse<String> {

  private final int statusCode;
  private String body;

  HttpResponseStub(int statusCode) {
    this.statusCode = statusCode;
  }

  public HttpResponseStub(int statusCode, String body) {
    this(statusCode);
    this.body = body;
  }

  @Override
  public int statusCode() {
    return statusCode;
  }

  @Override
  public String body() {
    return body;
  }
}

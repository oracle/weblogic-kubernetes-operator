// Copyright (c) 2020, 2023, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.http.client;

import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public abstract class HttpResponseStub implements HttpResponse<String> {

  private final int statusCode;
  private String body;
  private HttpHeaders headers = HttpHeaders.of(Collections.emptyMap(), (a,b) -> true);
  private HttpRequest request;

  HttpResponseStub(int statusCode) {
    this.statusCode = statusCode;
  }

  public HttpResponseStub(int statusCode, String body) {
    this(statusCode);
    this.body = body;
  }

  @Override
  public HttpRequest request() {
    return request;
  }

  @Override
  public int statusCode() {
    return statusCode;
  }

  @Override
  public String body() {
    return body;
  }

  @Override
  public HttpHeaders headers() {
    return headers;
  }

  public HttpResponseStub withRequest(HttpRequest request) {
    this.request = request;
    return this;
  }

  /**
   * Adds the specified header to the response and returns the modified response.
   * @param name the name of the header
   * @param value the value of the header
   */
  @SuppressWarnings("SameParameterValue")
  public HttpResponseStub withHeader(String name, String value) {
    final Map<String, List<String>> headerMap = new HashMap<>(headers.map());
    headerMap.computeIfAbsent(name, n -> new ArrayList<>()).add(value);
    headers = HttpHeaders.of(headerMap, (a,b) -> true);
    return this;
  }
}

// Copyright (c) 2017, 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.http;

/** Exception when a HTTP status code is received that indicates the request was not successful. */
public class HttpException extends Exception {

  final int statusCode;

  public HttpException(int statusCode) {
    super("status code: " + statusCode);
    this.statusCode = statusCode;
  }

  public int getStatusCode() {
    return statusCode;
  }
}

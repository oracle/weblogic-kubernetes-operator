// Copyright 2017, 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.http;

/** Exception when a HTTP status code is received that indicates the request was not successful. */
public class HTTPException extends Exception {

  final int statusCode;

  public HTTPException(int statusCode) {
    super("status code: " + statusCode);
    this.statusCode = statusCode;
  }

  public int getStatusCode() {
    return statusCode;
  }
}

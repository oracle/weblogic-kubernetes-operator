// Copyright 2019 Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.http;

public abstract class HttpClientStub extends HttpClient {

  final String RESPONSE = "{}";

  public HttpClientStub() {
    super(null, null);
  }

  @Override
  public Result executePostUrlOnServiceClusterIP(
      String requestUrl, String serviceURL, String payload, boolean throwOnFailure)
      throws HTTPException {
    return new Result(RESPONSE, 200, true);
  }
}

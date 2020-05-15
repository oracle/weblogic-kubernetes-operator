// Copyright (c) 2018, 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.http;

public abstract class ResultStub extends Result {

  String response = "{}";
  int status = 200;
  boolean successful = true;

  public ResultStub() {
    super("{}", 200);
  }

  public ResultStub(String response, int status, boolean successful) {
    super(response, 200);
  }

  public ResultStub withResponse(String response) {
    this.response = response;
    return this;
  }

  public ResultStub withStatus(int status) {
    this.status = status;
    return this;
  }

  public ResultStub withSuccessful(boolean successful) {
    this.successful = successful;
    return this;
  }
}

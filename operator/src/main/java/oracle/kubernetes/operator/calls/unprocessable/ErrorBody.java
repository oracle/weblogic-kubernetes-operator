// Copyright (c) 2019, 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.calls.unprocessable;

public class ErrorBody {
  private String message;
  private ErrorDetails details;

  public String getMessage() {
    return message;
  }

  public ErrorDetails getDetails() {
    return details;
  }

  public void addDetails() {
    details = new ErrorDetails();
  }

  public void setMessage(String message) {
    this.message = message;
  }
}

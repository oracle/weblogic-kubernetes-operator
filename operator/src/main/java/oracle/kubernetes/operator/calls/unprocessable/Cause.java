// Copyright (c) 2019, 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.calls.unprocessable;

public class Cause {
  private String reason;
  private String message;
  private String field;

  public Cause withReason(String reason) {
    this.reason = reason;
    return this;
  }

  public String getReason() {
    return reason;
  }

  public String getMessage() {
    return message;
  }

  public String getField() {
    return field;
  }
}

// Copyright (c) 2019, 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.calls.unprocessable;

public class Cause {
  private String reason;

  public Cause withReason(String reason) {
    this.reason = reason;
    return this;
  }

  public String getReason() {
    return reason;
  }

}

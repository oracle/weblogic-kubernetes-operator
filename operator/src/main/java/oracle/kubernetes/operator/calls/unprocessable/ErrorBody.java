// Copyright (c) 2019, 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.calls.unprocessable;

import java.util.Map;

public class ErrorBody {
  private String kind;
  private String apiVersion;
  private Map<String,String> metaData;
  private String status;
  private String message;
  private ErrorDetails details;
  private int code;

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

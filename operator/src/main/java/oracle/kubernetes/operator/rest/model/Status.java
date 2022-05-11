// Copyright (c) 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.rest.model;

import java.util.Objects;

import com.google.gson.annotations.Expose;

public class Status {
  @Expose
  private Integer code;
  @Expose
  private String message;

  public Integer getCode() {
    return code;
  }

  public void setCode(Integer code) {
    this.code = code;
  }

  public Status code(Integer code) {
    this.code = code;
    return this;
  }

  public String getMessage() {
    return message;
  }

  public void setMessage(String message) {
    this.message = message;
  }

  public Status message(String message) {
    this.message = message;
    return this;
  }

  @Override
  public String toString() {
    return "Status{"
            + "code='" + code + '\''
            + ", message='" + message + '\''
            + '}';
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    Status status = (Status) o;
    return Objects.equals(code, status.code)
            && Objects.equals(message, status.message);
  }

  @Override
  public int hashCode() {
    return Objects.hash(code, message);
  }
}
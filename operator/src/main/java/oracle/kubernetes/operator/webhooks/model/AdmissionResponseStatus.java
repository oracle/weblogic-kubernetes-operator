// Copyright (c) 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.webhooks.model;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

/**
 * AdmissionResponseStatus represents customized HTT code and message when rejecting an admission request. The
 * specified status object is returned to the user.
 */
public class AdmissionResponseStatus {
  /**
   * HTTP return code for this status.
   */
  @SerializedName("code")
  @Expose
  private Integer code;

  /**
   * A human-readable description of the status of this admission call.
   */
  @SerializedName("message")
  @Expose
  private String message;

  public Integer getCode() {
    return code;
  }

  public AdmissionResponseStatus code(Integer code) {
    this.code = code;
    return this;
  }

  public String getMessage() {
    return message;
  }

  public AdmissionResponseStatus message(String message) {
    this.message = message;
    return this;
  }

  @Override
  public String toString() {
    return "AdmissionResponseStatus{"
            + "code='" + code + '\''
            + ", message='" + message + '\''
            + '}';
  }
}
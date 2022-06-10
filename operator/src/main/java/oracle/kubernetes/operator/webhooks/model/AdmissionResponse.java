// Copyright (c) 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.webhooks.model;

import java.util.List;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

/**
 * AdmissionResponse represents a Kubernetes admission response that an admission webhook sends back to the Kubernetes
 * ApiServer. It contains the result of an admission call, including whether the proposed change is accepted or not
 * as well as the reason for a rejection.
 * <p>More info: <a href="https://kubernetes.io/docs/reference/access-authn-authz/extensible-admission-controllers/
 #webhook-request-and-response">Admission webhook request and response</a>.
 * </p>
 */
public class AdmissionResponse {
  /**
   * An uid uniquely identifying this admission call (copied from the request.uid sent to the webhook).
   */
  @SerializedName("uid")
  @Expose
  private String uid;

  /**
   * Indicate if the corresponding admission request is allowed.
   */
  @SerializedName("allowed")
  @Expose
  private boolean allowed;

  /**
   * Optional warning messages.
   */
  @SerializedName("warnings")
  @Expose
  private List<String> warnings;

  /**
   * Optionally provide more information about what happened to the admission call. Mostly used when a webhook rejects
   * a call.
   * @see AdmissionResponseStatus
   */
  @SerializedName("status")
  @Expose
  private AdmissionResponseStatus status;

  public AdmissionResponse uid(String uid) {
    this.uid = uid;
    return this;
  }

  public String getUid() {
    return uid;
  }

  public boolean isAllowed() {
    return allowed;
  }

  public AdmissionResponse allowed(boolean allowed) {
    this.allowed = allowed;
    return this;
  }

  public List<String> getWarnings() {
    return warnings;
  }

  public AdmissionResponse warnings(List<String> warnings) {
    this.warnings = warnings;
    return this;
  }

  public AdmissionResponseStatus getStatus() {
    return status;
  }

  public AdmissionResponse status(AdmissionResponseStatus status) {
    this.status = status;
    return this;
  }

  @Override
  public String toString() {
    return "AdmissionResponse{"
        + "uid='" + uid + '\''
        + ", allowed='" + allowed + '\''
        + ", warnings='" + warnings + '\''
        + ", status='" + status + '\''
        + '}';
  }
}

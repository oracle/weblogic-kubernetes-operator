// Copyright (c) 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.rest.model;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

/**
 * AdmissionResponse represents a Kubernetes admission response inside an AdmissionReview that an admission webhook
 * sends back o the Kubernetes ApiServer as a response to an admission call. It contains the result of an admission
 * call, including whether the required change is accepted or not as well as the reason of a rejection.
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
  protected String uid;

  /**
   * Indicate if the corresponding admission request is allowed.
   */
  @SerializedName("allowed")
  @Expose
  protected boolean allowed;

  /**
   * Optionally provide more information about what happened to the admission call. Mostly used when a webhook rejects
   * a call.
   * @see oracle.kubernetes.operator.rest.model.AdmissionResponseStatus
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

  public void setUid(String uid) {
    this.uid = uid;
  }

  public boolean getAllowed() {
    return allowed;
  }

  public void setAllowed(boolean allowed) {
    this.allowed = allowed;
  }

  public AdmissionResponse allowed(boolean allowed) {
    this.allowed = allowed;
    return this;
  }

  public AdmissionResponseStatus getStatus() {
    return status;
  }

  public void setStatus(AdmissionResponseStatus status) {
    this.status = status;
  }

  public AdmissionResponse status(AdmissionResponseStatus status) {
    this.status = status;
    return this;
  }

  @Override
  public String toString() {
    return "AdmissionResponse{"
        + "uid='" + uid + '\''
        + ", status='" + status + '\''
        + ", allowed='" + allowed + '\''
        + '}';
  }
}

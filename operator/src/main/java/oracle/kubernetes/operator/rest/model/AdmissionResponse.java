// Copyright (c) 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.rest.model;

import com.google.gson.annotations.Expose;

/**
 * AdmissionResponse represents a Kubernetes admission response inside an AdmissionReview that is sent by
 * an admission webhook to the Kubernetes ApiServer as a response to an admission request.
 */
public class AdmissionResponse {
  @Expose
  protected String uid;
  @Expose
  protected boolean allowed;
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

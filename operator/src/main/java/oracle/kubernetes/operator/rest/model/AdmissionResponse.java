// Copyright (c) 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.rest.model;

import java.util.Objects;

import com.google.gson.annotations.Expose;

public class AdmissionResponse {
  @Expose
  protected String uid;
  @Expose
  protected boolean allowed;
  @Expose
  private Status status;

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

  public Status getStatus() {
    return status;
  }

  public void setStatus(Status status) {
    this.status = status;
  }

  public AdmissionResponse status(Status status) {
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

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    AdmissionResponse that = (AdmissionResponse) o;
    return Objects.equals(uid, that.uid)
        && Objects.equals(status, that.status)
        && allowed == that.allowed;
  }

  @Override
  public int hashCode() {
    return Objects.hash(uid, status, allowed);
  }
}

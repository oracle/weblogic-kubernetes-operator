// Copyright (c) 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.rest.model;

import java.util.Objects;

import com.google.gson.annotations.Expose;

public class AdmissionReview {
  @Expose
  private String kind;
  @Expose
  private String apiVersion;
  @Expose
  private AdmissionRequest request;
  @Expose
  private AdmissionResponse response;

  public String getKind() {
    return kind;
  }

  public void setKind(String kind) {
    this.kind = kind;
  }

  public AdmissionReview kind(String kind) {
    setKind(kind);
    return this;
  }

  public String getApiVersion() {
    return apiVersion;
  }

  public void setApiVersion(String apiVersion) {
    this.apiVersion = apiVersion;
  }

  public AdmissionReview apiVersion(String apiVersion) {
    setApiVersion(apiVersion);
    return this;
  }

  public AdmissionRequest getRequest() {
    return request;
  }

  public void setRequest(AdmissionRequest request) {
    this.request = request;
  }

  public AdmissionReview request(AdmissionRequest request) {
    setRequest(request);
    return this;
  }

  public AdmissionResponse getResponse() {
    return response;
  }

  public void setResponse(AdmissionResponse response) {
    this.response = response;
  }

  public AdmissionReview response(AdmissionResponse response) {
    setResponse(response);
    return this;
  }

  @Override
  public String toString() {
    return "AdmissionReview{"
        + "kind='" + kind + '\''
        + ", apiVersion='" + apiVersion + '\''
        + ", request='" + request + '\''
        + ", response='" + response + '\''
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
    AdmissionReview that = (AdmissionReview) o;
    return Objects.equals(kind, that.kind)
        && Objects.equals(apiVersion, that.apiVersion)
        && Objects.equals(request, that.request)
        && Objects.equals(response, that.response);
  }

  @Override
  public int hashCode() {
    return Objects.hash(kind, apiVersion, request, response);
  }
}

// Copyright (c) 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.webhooks.model;

import java.util.Objects;

import com.google.gson.annotations.Expose;

public class ConversionReviewModel {

  @Expose
  private String kind;
  @Expose
  private String apiVersion;
  @Expose
  private ConversionRequest request;
  @Expose
  private ConversionResponse response;

  public String getKind() {
    return kind;
  }

  public void setKind(String kind) {
    this.kind = kind;
  }

  public ConversionReviewModel kind(String kind) {
    this.kind = kind;
    return this;
  }

  public String getApiVersion() {
    return apiVersion;
  }

  public void setApiVersion(String apiVersion) {
    this.apiVersion = apiVersion;
  }

  public ConversionReviewModel apiVersion(String apiVersion) {
    this.apiVersion = apiVersion;
    return this;
  }

  public ConversionRequest getRequest() {
    return request;
  }

  public void setRequest(ConversionRequest request) {
    this.request = request;
  }

  public ConversionResponse getResponse() {
    return response;
  }

  public void setResponse(ConversionResponse response) {
    this.response = response;
  }

  public ConversionReviewModel response(ConversionResponse response) {
    this.response = response;
    return this;
  }

  @Override
  public String toString() {
    return "ConversionReviewParamsModel{" 
            + "kind='" + kind + '\'' 
            + ", apiVersion='" + apiVersion + '\'' 
            + ", request=" + request 
            + ", response=" + response 
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
    ConversionReviewModel that = (ConversionReviewModel) o;
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

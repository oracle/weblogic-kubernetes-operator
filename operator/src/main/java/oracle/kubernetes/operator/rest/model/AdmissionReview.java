// Copyright (c) 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.rest.model;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;
import oracle.kubernetes.json.Description;

/**
 * AdmissionReview represents a request or a response object for an admission webhook call.
 *
 * <p></p>When a user performs an operation to a Kubernetes resource, Kubernetes ApiServer invokes all admission
 * webhooks that have registered their interests to the resource. Upon invocation, the Kubernetes ApiServer passes an
 * AdmissionReview object to the webhook, and expects the webhook to return an AdmissionReview as the response. An
 * admission webhook uses the information in the AdmissionReview request to perform the necessary operation
 * the resource, and sends back an AdmissionReview object to the ApiServer about the result of the operation.
 * </p>
 * <p>It implements a subset of the AdmissionReview interfaces described in <a href="https://kubernetes.io/docs/
 reference/access-authn-authz/extensible-admission-controllers/#webhook-request-and-response">
 Admission webhook request and response</a>.
 * </p>
 */
public class AdmissionReview {
  /**
   * APIVersion defines the versioned schema of this representation of an Admission object. Servers should
   * convert recognized schemas to the latest internal value, and may reject unrecognized values.
   * More info: https://git.k8s.io/community/contributors/devel/api-conventions.md#resources
   */
  @SerializedName("apiVersion")
  @Expose
  @Description("The API version defines the versioned schema of this AdminssionReview. Required.")
  private String apiVersion;

  /**
   * Kind is a string value representing the REST resource this object represents. Servers may infer
   * this from the endpoint the client submits requests to. Cannot be updated. In CamelCase. More
   * info: https://git.k8s.io/community/contributors/devel/api-conventions.md#types-kinds
   */
  @SerializedName("kind")
  @Expose
  @Description("The type of the REST resource. Must be \"AdmissionReview\". Required.")
  private String kind;

  /**
   * An {@link  AdmissionRequest}  that contains the details of the admission call.
   */
  @SerializedName("request")
  @Expose
  private AdmissionRequest request;

  /**
   * An {@link  AdmissionResponse} that contains the details of the result of the admission call.
   */
  @SerializedName("response")
  @Expose
  private AdmissionResponse response;


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
        + "apiVersion='" + apiVersion + '\''
        + ", kind='" + kind + '\''
        + ", request='" + request + '\''
        + ", response='" + response + '\''
        + '}';
  }
}

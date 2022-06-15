// Copyright (c) 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.webhooks.model;

import java.util.Map;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;
import oracle.kubernetes.weblogic.domain.model.DomainResource;

import static oracle.kubernetes.operator.webhooks.utils.GsonBuilderUtils.readDomain;
import static oracle.kubernetes.operator.webhooks.utils.GsonBuilderUtils.writeMap;

/**
 * AdmissionRequest represents a Kubernetes admission request sent by the Kubernetes ApiServer upon invoking an
 * admission webhook. It describes the details of the request, including the information about the resource object
 * that the required admission operation should be performed upon, as well as the existing version and the proposed
 * version of the object.
 *
 * <p>More info:
 * <a href="https://kubernetes.io/docs/reference/access-authn-authz/extensible-admission-controllers/
 #webhook-request-and-response">Admission webhook request and response</a>.
 * </p>
 */
public class AdmissionRequest {
  /**
   * An uid uniquely identifying this admission call.
   */
  @SerializedName("uid")
  @Expose
  private String uid;

  /**
   * Fully-qualified group/version/kind of the incoming object.
   */
  @SerializedName("kind")
  @Expose
  private Map<String, String> kind;

  /**
   * Fully-qualified group/version/kind of the resource being modified.
   */
  @SerializedName("resource")
  @Expose
  private Map<String, String> resource;

  /**
   * The subresource, if the request is to a subresource.
   */
  @SerializedName("subResource")
  @Expose
  private Map<String, String> subResource;

  /**
   * The new object being admitted.
   */
  @SerializedName("object")
  @Expose
  private Map<String,Object> object;

  /**
   * The existing object.
   */
  @SerializedName("oldObject")
  @Expose
  private Map<String,Object> oldObject;

  public String getUid() {
    return uid;
  }

  public void setUid(String uid) {
    this.uid = uid;
  }

  public Map<String, String> getKind() {
    return kind;
  }

  public void setKind(Map<String, String> kind) {
    this.kind = kind;
  }

  public Map<String, String> getResource() {
    return resource;
  }

  public void setResource(Map<String, String> resource) {
    this.resource = resource;
  }

  public Map<String, String> getSubResource() {
    return subResource;
  }

  public void setSubResource(Map<String, String> subResource) {
    this.subResource = subResource;
  }

  public Map<String,Object> getObject() {
    return object;
  }

  public void setObject(Map<String,Object> object) {
    this.object = object;
  }

  public AdmissionRequest object(Map<String,Object> object) {
    setObject(object);
    return this;
  }

  public Map<String,Object> getOldObject() {
    return oldObject;
  }

  public void setOldObject(Map<String,Object> oldObject) {
    this.oldObject = oldObject;
  }

  public AdmissionRequest oldObject(Map<String,Object> oldObject) {
    setOldObject(oldObject);
    return this;
  }

  public DomainResource getExistingDomain() {
    return readDomain(writeMap(getOldObject()));
  }

  public DomainResource getProposedDomain() {
    return readDomain(writeMap(getObject()));
  }

  @Override
  public String toString() {
    return "AdmissionRequest{"
        + "uid='" + uid + '\''
        + ", kind='" + kind + '\''
        + ", resource='" + resource + '\''
        + ", subResource='" + subResource + '\''
        + ", object='" + object + '\''
        + ", oldObject='" + oldObject + '\''
        + '}';
  }
}

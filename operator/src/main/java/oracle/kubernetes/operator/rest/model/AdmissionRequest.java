// Copyright (c) 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.rest.model;

import java.util.Map;
import java.util.Objects;

import com.google.gson.annotations.Expose;

public class AdmissionRequest {
  @Expose
  protected String uid;
  @Expose
  protected Map<String, String> kind;
  @Expose
  protected Map<String, String> resource;
  @Expose
  protected Map<String, String> subResource;
  @Expose
  protected Object object;
  @Expose
  protected Object oldObject;

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

  public Object getObject() {
    return object;
  }

  public void setObject(Object object) {
    this.object = object;
  }

  public Object getOldObject() {
    return oldObject;
  }

  public void setOldObject(Object oldObject) {
    this.oldObject = oldObject;
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

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    AdmissionRequest that = (AdmissionRequest) o;
    return Objects.equals(uid, that.uid)
        && Objects.equals(kind, that.kind)
        && Objects.equals(resource, that.resource)
        && Objects.equals(subResource, that.subResource)
        && Objects.equals(object, that.object)
        && Objects.equals(oldObject, that.oldObject);
  }

  @Override
  public int hashCode() {
    return Objects.hash(uid, kind, resource, subResource, object, oldObject);
  }

}

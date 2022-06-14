// Copyright (c) 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.webhooks.model;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;
import oracle.kubernetes.weblogic.domain.model.DomainResource;

/**
 * DomainAdmissionRequest represents a Kubernetes admission request sent by the Kubernetes ApiServer upon invoking an
 * admission webhook. It describes the details of the request, including the information about the domain resource
 * that the required admission operation should be performed upon, as well as the existing version and the proposed
 * version of the domain.
 *
 * <p>More info:
 * <a href="https://kubernetes.io/docs/reference/access-authn-authz/extensible-admission-controllers/
 #webhook-request-and-response">Admission webhook request and response</a>.
 * </p>
 */
public class DomainAdmissionRequest extends AdmissionRequest {
  /**
   * The new object being admitted.
   */
  @SerializedName("object")
  @Expose
  private DomainResource object;

  /**
   * The existing object.
   */
  @SerializedName("oldObject")
  @Expose
  private DomainResource oldObject;

  public DomainResource getObject() {
    return object;
  }

  public void setObject(DomainResource object) {
    this.object = object;
  }

  public DomainResource getOldObject() {
    return oldObject;
  }

  public void setOldObject(DomainResource oldObject) {
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
}

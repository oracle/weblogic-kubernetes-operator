// Copyright (c) 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.webhooks.model;

import java.util.List;
import java.util.Map;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;
import io.kubernetes.client.openapi.ApiException;
import oracle.kubernetes.operator.webhooks.resource.AdmissionChecker;
import oracle.kubernetes.operator.webhooks.resource.ClusterCreateAdmissionChecker;
import oracle.kubernetes.operator.webhooks.resource.ClusterScaleAdmissionChecker;
import oracle.kubernetes.operator.webhooks.resource.ClusterUpdateAdmissionChecker;
import oracle.kubernetes.operator.webhooks.resource.DomainCreateAdmissionChecker;
import oracle.kubernetes.operator.webhooks.resource.DomainUpdateAdmissionChecker;
import oracle.kubernetes.weblogic.domain.model.ClusterResource;
import oracle.kubernetes.weblogic.domain.model.DomainResource;

import static oracle.kubernetes.operator.KubernetesConstants.CLUSTER;
import static oracle.kubernetes.operator.KubernetesConstants.DOMAIN;
import static oracle.kubernetes.operator.KubernetesConstants.SCALE;
import static oracle.kubernetes.operator.webhooks.utils.GsonBuilderUtils.readCluster;
import static oracle.kubernetes.operator.webhooks.utils.GsonBuilderUtils.readDomain;
import static oracle.kubernetes.operator.webhooks.utils.GsonBuilderUtils.readScale;
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

  public static final String NOT_SUPPORTED_MSG = "Not Supported";

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
  private String subResource;

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

  public String getSubResource() {
    return subResource;
  }

  public void setSubResource(String subResource) {
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

  public Object getExistingResource() {
    return getOldObject() == null ? null : getOldResource();
  }

  private Object getOldResource() {
    return getRequestKind().readOldObject(this);
  }

  public Object getProposedResource() {
    return getRequestKind().readObject(this);
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

  /**
   * Get the kind type of the admission request.
   *
   * @return enum element.
   */
  public RequestKind getRequestKind() {
    switch (getKind().get("kind")) {
      case DOMAIN:
        return RequestKind.DOMAIN;
      case CLUSTER:
        return RequestKind.CLUSTER;
      case SCALE:
        return RequestKind.SCALE;
      default:
        return RequestKind.NOT_SUPPORTED;
    }
  }

  public enum RequestKind {
    DOMAIN {
      @Override
      public Object readOldObject(AdmissionRequest request) {
        return readDomain(writeMap(request.getOldObject()));
      }

      @Override
      public Object readObject(AdmissionRequest request) {
        return readDomain(writeMap(request.getObject()));
      }

      @Override
      public AdmissionChecker getAdmissionChecker(AdmissionRequest request) {
        DomainResource existing = (DomainResource) request.getExistingResource();
        DomainResource proposed = (DomainResource) request.getProposedResource();
        return request.isNewResource()
            ? new DomainCreateAdmissionChecker(proposed)
            : new DomainUpdateAdmissionChecker(existing, proposed);
      }
    },
    CLUSTER {
      @Override
      public Object readOldObject(AdmissionRequest request) {
        return readCluster(writeMap(request.getOldObject()));
      }

      @Override
      public Object readObject(AdmissionRequest request) {
        return readCluster(writeMap(request.getObject()));
      }

      @Override
      public AdmissionChecker getAdmissionChecker(AdmissionRequest request) {
        ClusterResource existing = (ClusterResource) request.getExistingResource();
        ClusterResource proposed = (ClusterResource) request.getProposedResource();
        return request.isNewResource()
            ? new ClusterCreateAdmissionChecker(proposed)
            : new ClusterUpdateAdmissionChecker(existing, proposed);
      }
    },
    SCALE {
      @Override
      public Object readOldObject(AdmissionRequest request) {
        return readScale(writeMap(request.getOldObject()));
      }

      @Override
      public Object readObject(AdmissionRequest request) {
        return readScale(writeMap(request.getObject()));
      }

      @Override
      public AdmissionChecker getAdmissionChecker(AdmissionRequest request) throws ApiException {
        Scale proposed = (Scale) request.getProposedResource();
        ClusterResource cluster = getCluster(proposed.getMetadata().getName(),
              proposed.getMetadata().getNamespace());
        if (cluster != null) {
          cluster.getSpec().withReplicas(Integer.valueOf(proposed.getSpec().get("replicas")));
          return new ClusterScaleAdmissionChecker(cluster);
        } else {
          throw new ApiException("Cluster " + proposed.getMetadata().getName() + " not found");
        }
      }

      private ClusterResource getCluster(String clusterName, String namespace) throws ApiException {
        List<ClusterResource> clusters = AdmissionChecker.getClusters(namespace);
        return clusters.stream().filter(cluster -> clusterName.equals(cluster.getMetadata().getName()))
            .findFirst().orElse(null);
      }
    },
    NOT_SUPPORTED {
      @Override
      public boolean isSupported() {
        return false;
      }

      @Override
      public Object readOldObject(AdmissionRequest request) {
        throw new AssertionError(NOT_SUPPORTED_MSG);
      }

      @Override
      public Object readObject(AdmissionRequest request) {
        throw new AssertionError(NOT_SUPPORTED_MSG);
      }

      @Override
      public AdmissionChecker getAdmissionChecker(AdmissionRequest request) {
        throw new AssertionError(NOT_SUPPORTED_MSG);
      }
    };

    public boolean isSupported() {
      return true;
    }

    public abstract Object readOldObject(AdmissionRequest request);

    public abstract Object readObject(AdmissionRequest request);

    public abstract AdmissionChecker getAdmissionChecker(AdmissionRequest request) throws ApiException;

  }

  private boolean isNewResource() {
    return getOldObject() == null;
  }
}

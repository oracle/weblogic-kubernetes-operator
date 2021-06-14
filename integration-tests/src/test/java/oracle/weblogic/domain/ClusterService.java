// Copyright (c) 2020, 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.domain;

import java.util.HashMap;
import java.util.Map;

import io.swagger.annotations.ApiModelProperty;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;

public class ClusterService {

  @ApiModelProperty(
      "The labels to be attached to generated resources. The label names must "
          + "not start with 'weblogic.'.")
  private Map<String, String> labels = new HashMap<>();

  @ApiModelProperty("The annotations to be attached to generated resources.")
  private Map<String, String> annotations = new HashMap<>();

  @ApiModelProperty(
      "Supports \"ClientIP\" and \"None\". Used to maintain session affinity. Enable client IP based session affinity. "
          + "Must be ClientIP or None. Defaults to None. More info: "
          + "https://kubernetes.io/docs/concepts/services-networking/service/#virtual-ips-and-service-proxies")
  private String sessionAffinity;

  public ClusterService labels(Map<String, String> labels) {
    this.labels = labels;
    return this;
  }

  public Map<String, String> labels() {
    return labels;
  }

  /**
   * Puts item in labels map.
   * @param key Label name
   * @param labelsItem Label value
   * @return this
   */
  public ClusterService putLabelsItem(String key, String labelsItem) {
    if (labels == null) {
      labels = new HashMap<>();
    }
    labels.put(key, labelsItem);
    return this;
  }

  public Map<String, String> getLabels() {
    return labels;
  }

  public void setLabels(Map<String, String> labels) {
    this.labels = labels;
  }

  public ClusterService annotations(Map<String, String> annotations) {
    this.annotations = annotations;
    return this;
  }

  public Map<String, String> annotations() {
    return annotations;
  }

  /**
   * Puts item in annotations map.
   * @param key Annotation name
   * @param annotationsItem Annotation value
   * @return this
   */
  public ClusterService putAnnotationsItem(String key, String annotationsItem) {
    if (annotations == null) {
      annotations = new HashMap<>();
    }
    annotations.put(key, annotationsItem);
    return this;
  }

  public Map<String, String> getAnnotations() {
    return annotations;
  }

  public void setAnnotations(Map<String, String> annotations) {
    this.annotations = annotations;
  }

  public ClusterService sessionAffinity(String sessionAffinity) {
    this.sessionAffinity = sessionAffinity;
    return this;
  }

  public String getSessionAffinity() {
    return sessionAffinity;
  }

  public void setSessionAffinity(String sessionAffinity) {
    this.sessionAffinity = sessionAffinity;
  }

  @Override
  public String toString() {
    return new ToStringBuilder(this)
        .append("labels", labels)
        .append("annotations", annotations)
        .append("sessionAffinity", sessionAffinity)
        .toString();
  }

  @Override
  public boolean equals(Object other) {
    if (this == other) {
      return true;
    }

    if (other == null || getClass() != other.getClass()) {
      return false;
    }
    ClusterService rhs = (ClusterService) other;
    return new EqualsBuilder()
        .append(labels, rhs.labels)
        .append(annotations, rhs.annotations)
        .append(sessionAffinity, rhs.sessionAffinity)
        .isEquals();
  }

  @Override
  public int hashCode() {
    return new HashCodeBuilder(17, 37)
        .append(labels).append(annotations).append(sessionAffinity).toHashCode();
  }
}

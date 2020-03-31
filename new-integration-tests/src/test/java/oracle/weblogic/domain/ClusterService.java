// Copyright (c) 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.weblogic.domain.model;

import java.util.HashMap;
import java.util.Map;

import oracle.kubernetes.json.Description;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;

public class ClusterService {

  @Description(
      "The labels to be attached to generated resources. The label names must "
          + "not start with 'weblogic.'.")
  private final Map<String, String> labels = new HashMap<>();

  @Description("The annotations to be attached to generated resources.")
  private final Map<String, String> annotations = new HashMap<>();

  public ClusterService labels(Map<String, String> labels) {
    this.labels = labels;
    return this;
  }

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

  @Override
  public String toString() {
    return new ToStringBuilder(this)
        .append("labels", labels)
        .append("annotations", annotations)
        .toString();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }

    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    KubernetesResource that = (KubernetesResource) o;

    return new EqualsBuilder()
        .append(labels, that.labels)
        .append(annotations, that.annotations)
        .isEquals();
  }

  @Override
  public int hashCode() {
    return new HashCodeBuilder(17, 37).append(labels).append(annotations).toHashCode();
  }
}

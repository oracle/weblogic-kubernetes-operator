// Copyright 2019, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.weblogic.domain.v2;

import java.util.HashMap;
import java.util.Map;
import oracle.kubernetes.json.Description;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;

class KubernetesResource {

  /**
   * The labels to be attached to generated resources. The label names must not start with
   * 'weblogic.'.
   *
   * @since 2.0
   */
  @Description(
      "The labels to be attached to generated resources. The label names must "
          + "not start with 'weblogic.'.")
  private Map<String, String> labels = new HashMap<>();

  /**
   * The annotations to be attached to generated resources.
   *
   * @since 2.0
   */
  @Description("The annotations to be attached to generated resources.")
  private Map<String, String> annotations = new HashMap<>();

  void fillInFrom(KubernetesResource kubernetesResource1) {
    kubernetesResource1.getLabels().forEach(this::addLabelIfMissing);
    kubernetesResource1.getAnnotations().forEach(this::addAnnotationIfMissing);
  }

  private void addLabelIfMissing(String name, String value) {
    if (!labels.containsKey(name)) {
      labels.put(name, value);
    }
  }

  private void addAnnotationIfMissing(String name, String value) {
    if (!annotations.containsKey(name)) {
      annotations.put(name, value);
    }
  }

  void addLabel(String name, String value) {
    labels.put(name, value);
  }

  void addAnnotations(String name, String value) {
    annotations.put(name, value);
  }

  Map<String, String> getLabels() {
    return labels;
  }

  Map<String, String> getAnnotations() {
    return annotations;
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

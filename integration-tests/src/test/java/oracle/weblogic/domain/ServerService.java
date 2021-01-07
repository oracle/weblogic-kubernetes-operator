// Copyright (c) 2020, 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.domain;

import java.util.HashMap;
import java.util.Map;

import io.swagger.annotations.ApiModelProperty;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;

public class ServerService {

  @ApiModelProperty(
      "If true, operator will create server services even for server instances without running pods.")
  private Boolean precreateService;

  @ApiModelProperty(
      "The labels to be attached to generated resources. The label names must "
          + "not start with 'weblogic.'.")
  private Map<String, String> labels = new HashMap<>();

  @ApiModelProperty("The annotations to be attached to generated resources.")
  private Map<String, String> annotations = new HashMap<>();

  public ServerService precreateService(Boolean precreateService) {
    this.precreateService = precreateService;
    return this;
  }

  public Boolean precreateService() {
    return precreateService;
  }

  public Boolean getPrecreateService() {
    return precreateService;
  }

  public void setPrecreateService(Boolean precreateService) {
    this.precreateService = precreateService;
  }

  public ServerService labels(Map<String, String> labels) {
    this.labels = labels;
    return this;
  }

  public Map<String, String> labels() {
    return labels;
  }

  /**
   * Puts labels map item.
   * @param key Label name
   * @param labelsItem Label value
   * @return this
   */
  public ServerService putLabelsItem(String key, String labelsItem) {
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

  public ServerService annotations(Map<String, String> annotations) {
    this.annotations = annotations;
    return this;
  }

  public Map<String, String> annotations() {
    return annotations;
  }

  /**
   * Puts annotations map item.
   * @param key Annotation name
   * @param annotationsItem Annotation value
   * @return this
   */
  public ServerService putAnnotationsItem(String key, String annotationsItem) {
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
        .append("precreateService", precreateService)
        .append("labels", labels)
        .append("annotations", annotations)
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
    ServerService rhs = (ServerService) other;
    return new EqualsBuilder()
        .append(precreateService, rhs.precreateService)
        .append(labels, rhs.labels)
        .append(annotations, rhs.annotations)
        .isEquals();
  }

  @Override
  public int hashCode() {
    return new HashCodeBuilder(17, 37)
        .append(precreateService)
        .append(labels)
        .append(annotations)
        .toHashCode();
  }
}

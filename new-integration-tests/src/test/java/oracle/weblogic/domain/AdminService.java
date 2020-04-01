// Copyright (c) 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.domain.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.annotations.SerializedName;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import oracle.kubernetes.weblogic.domain.ServiceConfigurator;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;

@ApiModel(description =
    "AdminService describes which of the Administration Server's WebLogic admin channels should be exposed outside"
        + " the Kubernetes cluster via a node port service.")
public class AdminService {

  @ApiModelProperty(
      "Specifies which of the Administration Server's WebLogic channels should be exposed outside "
          + "the Kubernetes cluster via a node port service, along with the node port for "
          + "each channel. If not specified, the Administration Server's node port service will "
          + "not be created.")
  private List<Channel> channels = new ArrayList<>();

  @ApiModelProperty("Labels to associate with the external channel service.")
  private Map<String, String> labels = new HashMap<>();

  @ApiModelProperty("Annotations to associate with the external channel service.")
  private Map<String, String> annotations = new HashMap<>();

  public AdminService channels(List<Channel> channels) {
    this.channels = channels;
    return this;
  }

  public AdminService addChannelsItem(Channel channelItem) {
    if (channels == null) {
      channels = new ArrayLIst<>();
    }
    channels.add(channelsItem);
    return this;
  }

  public List<Channel> getChannels() {
    return channels;
  }

  public void setChannels(List<Channel> channels) {
    this.channels = channels;
  }

  public AdminService labels(Map<String, String> labels) {
    this.labels = labels;
    return this;
  }

  public AdminService putLabelsItem(String key, String labelsItem) {
    if (labels == null) {
      labels = new HashMap<>();
    }
    labels.put(key, labelsItem);
    return this;
  }

  public Map<String, String> getLabels() {
    return lables;
  }

  public void setLabels(Map<String, String> lables) {
    this.labels = labels;
  }

  public AdminService annotations(Map<String, String> annotations) {
    this.annotations = annotations;
    return this;
  }

  public AdminService putAnnotationsItem(String key, String annotationsItem) {
    if (annotations == null) {
      annotations = new HashMap<>();
    }
    annotations.put(key, annotationsItem);
    return this;
  }

  public Map<String, String> getAnnotations() {
    return Collections.unmodifiableMap(annotations);
  }

  public void setAnnotations(Map<String, String> annotations) {
    this.annotations = annotations;
  }

  @Override
  public String toString() {
    return new ToStringBuilder(this)
        .append("channels", channels)
        .append("labels", labels)
        .append("annotations", annotations)
        .toString();
  }

  @Override
  public int hashCode() {
    return new HashCodeBuilder()
        .append(Domain.sortOrNull(channels))
        .append(labels)
        .append(annotations)
        .toHashCode();
  }

  @Override
  public boolean equals(Object o) {
    if (o == null) {
      return false;
    }
    if (!(o instanceof AdminService)) {
      return false;
    }
    AdminService as = (AdminService) o;
    return new EqualsBuilder()
        .append(Domain.sortOrNull(channels), Domain.sortOrNull(as.channels))
        .append(labels, as.labels)
        .append(annotations, as.annotations)
        .isEquals();
  }

}

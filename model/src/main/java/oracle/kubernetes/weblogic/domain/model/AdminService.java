// Copyright 2018, 2019, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.weblogic.domain.model;

import com.google.gson.annotations.SerializedName;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import oracle.kubernetes.json.Description;
import oracle.kubernetes.weblogic.domain.ServiceConfigurator;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;

public class AdminService implements ServiceConfigurator {
  @SerializedName("channels")
  @Description(
      "Specifies which of the admin server's WebLogic channels should be exposed outside "
          + "the Kubernetes cluster via a node port service, along with the node port for "
          + "each channel. If not specified, the admin server's node port service will "
          + "not be created.")
  private List<Channel> channels = new ArrayList<>();

  @Description("Labels to associate with the external channel service")
  private Map<String, String> labels = new HashMap<>();

  @Description("Annotations to associate with the external channel service")
  private Map<String, String> annotations = new HashMap<>();

  /**
   * Adds a channel to expose an admin server port outside the cluster via a specified port.
   *
   * @param channelName name of the channel to expose
   * @param nodePort the external port on which the channel will be exposed
   * @return this object
   */
  public AdminService withChannel(String channelName, int nodePort) {
    channels.add(new Channel().withChannelName(channelName).withNodePort(nodePort));
    return this;
  }

  /**
   * Adds a channel to expose an admin server port outside the cluster. Will use the matching NAP
   * port number.
   *
   * @param channelName name of the channel to expose
   * @return this object
   */
  public AdminService withChannel(String channelName) {
    channels.add(new Channel().withChannelName(channelName));
    return this;
  }

  public List<Channel> getChannels() {
    return channels;
  }

  public Channel getChannel(String name) {
    return channels.stream().filter(c -> c.getChannelName().equals(name)).findFirst().orElse(null);
  }

  /**
   * Adds a label to associate with the external channel service.
   *
   * @param name the label name
   * @param value the label value
   * @return this object
   */
  public AdminService withServiceLabel(String name, String value) {
    labels.put(name, value);
    return this;
  }

  /**
   * Returns the labels to associate with the external channel service.
   *
   * @return a map of label names to value
   */
  public Map<String, String> getLabels() {
    return Collections.unmodifiableMap(labels);
  }

  /**
   * Adds an annotation to associate with the external channel service.
   *
   * @param name the label name
   * @param value the label value
   * @return this object
   */
  public AdminService withServiceAnnotation(String name, String value) {
    annotations.put(name, value);
    return this;
  }

  /**
   * Returns the annotations to associate with the external channel service.
   *
   * @return a map of annotation names to value
   */
  public Map<String, String> getAnnotations() {
    return Collections.unmodifiableMap(annotations);
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

// Copyright (c) 2018, 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.weblogic.domain.model;

import javax.annotation.Nonnull;

import com.google.gson.annotations.SerializedName;
import oracle.kubernetes.json.Description;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;

public class Channel implements Comparable<Channel> {
  @SerializedName("channelName")
  @Description(
      "Name of the channel. The \"default\" value refers to the Administration Server's default channel, "
          + "which is configured using the ServerMBean's ListenPort. "
          + "The \"default-secure\" value refers to the Administration Server's default secure channel, "
          + "which is configured using the ServerMBean's SSLMBean's ListenPort. "
          + "The \"default-admin\" value refers to the Administration Server's default administrative channel, "
          + "which is configured using the DomainMBean's AdministrationPort. "
          + "Otherwise, provide the name of one of the Administration Server's network access points, "
          + "which is configured using the ServerMBean's NetworkAccessMBeans. The \"default\", \"default-secure\", "
          + "and \"default-admin\" channels may not be specified here when using Istio.")
  @Nonnull
  private String channelName;

  @SerializedName("nodePort")
  @Description(
      "Specifies the port number used to access the WebLogic channel outside of the Kubernetes cluster. "
          + "If not specified, defaults to the port defined by the WebLogic channel.")
  private Integer nodePort;

  public String getChannelName() {
    return channelName;
  }

  public void setChannelName(String channelName) {
    this.channelName = channelName;
  }

  public Channel withChannelName(String channelName) {
    setChannelName(channelName);
    return this;
  }

  public Integer getNodePort() {
    return nodePort;
  }

  public void setNodePort(Integer nodePort) {
    this.nodePort = nodePort;
  }

  public Channel withNodePort(Integer nodePort) {
    setNodePort(nodePort);
    return this;
  }

  @Override
  public String toString() {
    return new ToStringBuilder(this)
        .append("channelName", channelName)
        .append("nodePort", nodePort)
        .toString();
  }

  @Override
  public int hashCode() {
    return new HashCodeBuilder().append(channelName).append(nodePort).toHashCode();
  }

  @Override
  public boolean equals(Object o) {
    if (o == null) {
      return false;
    }
    if (!(o instanceof Channel)) {
      return false;
    }
    Channel ch = (Channel) o;
    return new EqualsBuilder()
        .append(channelName, ch.channelName)
        .append(nodePort, ch.nodePort)
        .isEquals();
  }

  @Override
  public int compareTo(Channel o) {
    return channelName.compareTo(o.channelName);
  }
}

// Copyright (c) 2020, 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.domain;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;

@ApiModel(description = "Describes a single channel used by the Administration Server.")
public class Channel {
  @ApiModelProperty(
      "Name of channel.\n'default' refers to the Administration Server's default channel (configured "
          + "via the ServerMBean's ListenPort) "
          + "\n'default-secure' refers to the Administration Server's default secure channel "
          + "(configured via the ServerMBean's SSLMBean's ListenPort) "
          + "\n'default-admin' refers to the Administration Server's default administrative channel "
          + "(configured via the DomainMBean's AdministrationPort) "
          + "\nOtherwise, the name is the name of one of the Administration Server's network access points "
          + "(configured via the ServerMBean's NetworkAccessMBeans).")
  private String channelName;

  @ApiModelProperty(
      "Specifies the port number used to access the WebLogic channel "
          + "outside of the Kubernetes cluster. "
          + "If not specified, defaults to the port defined by the WebLogic channel.")
  private Integer nodePort;

  public Channel channelName(String channelName) {
    this.channelName = channelName;
    return this;
  }

  public String channelName() {
    return channelName;
  }

  public String getChannelName() {
    return channelName;
  }

  public void setChannelName(String channelName) {
    this.channelName = channelName;
  }

  public Channel nodePort(Integer nodePort) {
    this.nodePort = nodePort;
    return this;
  }

  public Integer nodePort() {
    return nodePort;
  }

  public Integer getNodePort() {
    return nodePort;
  }

  public void setNodePort(Integer nodePort) {
    this.nodePort = nodePort;
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
  public boolean equals(Object other) {
    if (this == other) {
      return true;
    }

    if (other == null || getClass() != other.getClass()) {
      return false;
    }
    Channel rhs = (Channel) other;
    return new EqualsBuilder()
        .append(channelName, rhs.channelName)
        .append(nodePort, rhs.nodePort)
        .isEquals();
  }
}

// Copyright 2018, 2019, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.weblogic.domain.v2;

import com.google.gson.annotations.SerializedName;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;

public class Channel implements Comparable<Channel> {
  @SerializedName("channelName")
  private String channelName;

  @SerializedName("nodePort")
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

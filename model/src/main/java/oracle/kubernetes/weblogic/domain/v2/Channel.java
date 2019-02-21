// Copyright 2018, 2019, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.weblogic.domain.v2;

import com.google.gson.annotations.SerializedName;
import javax.annotation.Nonnull;
import oracle.kubernetes.json.Description;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;

@Description("Describes a single channel used by the admin server.")
public class Channel implements Comparable<Channel> {
  @SerializedName("channelName")
  @Description(
      "Name of channel.\n'default' refers to the admin server's default channel (configured "
          + "via the ServerMBean's ListenPort) "
          + "\n'default-secure' refers to the admin server's default secure channel "
          + "(configured via the ServerMBean's SSLMBean's ListenPort) "
          + "\n'default-admin' refers to the admin server's default administrative channel "
          + "(configured via the DomainMBean's AdministrationPort) "
          + "\nOtherwise, the name is the name of one of the admin server's network access points "
          + "(configured via the ServerMBean's NetworkAccessMBeans).")
  @Nonnull
  private String channelName;

  @SerializedName("nodePort")
  @Description(
      "Specifies the port number used to access the WebLogic channel "
          + "outside of the Kubernetes cluster. "
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

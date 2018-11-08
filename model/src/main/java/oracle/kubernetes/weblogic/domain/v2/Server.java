// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.weblogic.domain.v2;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;

public class Server extends BaseConfiguration {
  /** The node port associated with this server. The introspector will override this value. */
  @SerializedName("nodePort")
  @Expose
  private Integer nodePort;

  protected Server getConfiguration() {
    Server configuration = new Server();
    configuration.fillInFrom(this);
    configuration.setNodePort(nodePort);
    return configuration;
  }

  public void setNodePort(Integer nodePort) {
    this.nodePort = nodePort;
  }

  public Integer getNodePort() {
    return nodePort;
  }

  @Override
  public String toString() {
    return new ToStringBuilder(this)
        .appendSuper(super.toString())
        .append("nodePort", nodePort)
        .toString();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;

    if (o == null || getClass() != o.getClass()) return false;

    if (!(o instanceof Server)) return false;

    Server that = (Server) o;

    return new EqualsBuilder()
        .appendSuper(super.equals(o))
        .append(nodePort, that.nodePort)
        .isEquals();
  }

  @Override
  public int hashCode() {
    return new HashCodeBuilder(17, 37).appendSuper(super.hashCode()).append(nodePort).toHashCode();
  }
}

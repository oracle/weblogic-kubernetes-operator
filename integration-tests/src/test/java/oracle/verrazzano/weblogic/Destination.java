// Copyright (c) 2023, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.verrazzano.weblogic;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;

@ApiModel(
    description
    = "Destination represents a Verrazzano Ingress rule Destination and how it "
    + "will be realized in the Kubernetes cluster.")
public class Destination {

  @ApiModelProperty("host name.")
  public String host;

  @ApiModelProperty("port number.")
  public Integer port;

  public Destination host(String host) {
    this.host = host;
    return this;
  }

  public String host() {
    return host;
  }

  public String getHost() {
    return host;
  }

  public void setHost(String host) {
    this.host = host;
  }

  public Destination port(int port) {
    this.port = port;
    return this;
  }

  public int port() {
    return port;
  }

  public int getport() {
    return port;
  }

  public void setport(int port) {
    this.port = port;
  }

  @Override
  public String toString() {
    return new ToStringBuilder(this)
        .append("host", host)
        .append("port", port)
        .toString();
  }

  @Override
  public int hashCode() {
    return new HashCodeBuilder()
        .append(host)
        .append(port)
        .toHashCode();
  }

  @Override
  public boolean equals(Object other) {
    if (this == other) {
      return true;
    }
    if (other == null || getClass() != other.getClass()) {
      return false;
    }
    Destination rhs = (Destination) other;
    return new EqualsBuilder()
        .append(host, rhs.host)
        .append(port, rhs.port)
        .isEquals();
  }

}

// Copyright (c) 2020, 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.domain;

import io.swagger.annotations.ApiModelProperty;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;

public class Istio {

  @ApiModelProperty(
      "True, if this domain is deployed under an Istio service mesh. "
          + "Defaults to true when the 'istio' element is included. Not required.")
  private Boolean enabled;

  @ApiModelProperty("The WebLogic readiness port for Istio. Defaults to 8888. Not required.")
  private Integer readinessPort;

  public static Integer DEFAULT_REPLICATION_PORT = 4358;

  @ApiModelProperty("The operator will create a WebLogic network access point with this port that will then be exposed "
      + "from the container running the WebLogic Server instance. The WebLogic Replication Service will use this "
      + "network access point for all replication traffic. Defaults to 4358.")
  private Integer replicationChannelPort = DEFAULT_REPLICATION_PORT;

  @ApiModelProperty("Starting with Istio 1.10, the networking behavior was changed in that the proxy no "
      + "longer redirects the traffic to the localhost interface, but instead forwards (or passes it "
      + "through) it to the network interface associated with the pod's IP. True, if Istio v1.10 or "
      + "higher is installed. Defaults to false")
  private String version;

  public Istio enabled(Boolean enabled) {
    this.enabled = enabled;
    return this;
  }

  public Boolean enabled() {
    return this.enabled;
  }

  public Boolean getEnabled() {
    return enabled;
  }

  public void setEnabled(Boolean enabled) {
    this.enabled = enabled;
  }

  public Istio readinessPort(Integer readinessPort) {
    this.readinessPort = readinessPort;
    return this;
  }

  public Integer readinessPort() {
    return this.readinessPort;
  }

  public Integer getReadinessPort() {
    return readinessPort;
  }

  public void setReadinessPort(Integer readinessPort) {
    this.readinessPort = readinessPort;
  }

  /**
   * Get the replication channel port.
   *
   * @return the replication channel port.
   */
  public Integer getReplicationChannelPort() {
    return this.replicationChannelPort;
  }

  /**
   * Sets the replication channel port.
   *
   * @param replicationChannelPort the port for replication channel.
   */
  public void setReplicationChannelPort(Integer replicationChannelPort) {
    this.replicationChannelPort = replicationChannelPort;
  }

  /**
   * Get the Istio version.
   *
   * @return the version of Istio.
   */
  public String getVersion() {
    return this.version;
  }

  /**
   * Sets the Istio version.
   *
   * @param version the version of Istio.
   */
  public void setVersion(String version) {
    this.version = version;
  }

  public Istio version(String version) {
    this.version = version;
    return this;
  }

  /**
   * Sets the Istio enabled status.
   *
   * @param istioEnabled True, if this domain is deployed under an Istio service mesh.
   * @return this
   */
  public Istio istioEnabled(Boolean istioEnabled) {
    this.enabled = istioEnabled;
    return this;
  }

  @Override
  public String toString() {
    ToStringBuilder builder =
        new ToStringBuilder(this)
            .append("enabled", enabled)
            .append("readinessPort", readinessPort)
            .append("replicationChannelPort", replicationChannelPort)
            .append("version", version);

    return builder.toString();
  }

  @Override
  public int hashCode() {
    HashCodeBuilder builder = new HashCodeBuilder().append(enabled).append(readinessPort)
        .append(replicationChannelPort).append(version);

    return builder.toHashCode();
  }

  @Override
  public boolean equals(Object other) {
    if (other == this) {
      return true;
    }
    if (!(other instanceof Istio)) {
      return false;
    }

    Istio rhs = ((Istio) other);
    EqualsBuilder builder =
        new EqualsBuilder()
            .append(enabled, rhs.enabled)
            .append(readinessPort, rhs.readinessPort)
            .append(replicationChannelPort, rhs.replicationChannelPort)
            .append(version, rhs.version);

    return builder.isEquals();
  }
}

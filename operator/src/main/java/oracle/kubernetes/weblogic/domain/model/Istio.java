// Copyright (c) 2019, 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.weblogic.domain.model;

import oracle.kubernetes.json.Description;
import oracle.kubernetes.operator.helpers.SemanticVersion;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;

public class Istio {

  @Description(
      "True, if this domain is deployed under an Istio service mesh. "
          + "Defaults to true when the `istio` field is specified.")
  private Boolean enabled = true;

  @Description("The operator will create a WebLogic network access point with this port that will then be exposed "
          + "from the container running the WebLogic Server instance. The readiness probe will use this network "
          + "access point to verify that the server instance is ready for application traffic. Defaults to 8888.")
  private Integer readinessPort = 8888;

  public static Integer DEFAULT_REPLICATION_PORT = 4358;

  @Description("The operator will create a WebLogic network access point with this port that will then be exposed "
      + "from the container running the WebLogic Server instance. The WebLogic Replication Service will use this "
      + "network access point for all replication traffic. Defaults to 4358.")
  private Integer replicationChannelPort = DEFAULT_REPLICATION_PORT;

  @Description("Starting with Istio 1.10, the networking behavior was changed in that the proxy no "
      + "longer redirects the traffic to the localhost interface, but instead forwards (or passes it "
      + "through) it to the network interface associated with the pod's IP. True, if Istio v1.10 or "
      + "higher is installed. Defaults to false")
  private String version;

  private SemanticVersion semanticVersion;

  /**
   * True, if this domain is deployed under an Istio service mesh.
   *
   * @return True, if this domain is deployed under an Istio service mesh.
   */
  public Boolean getEnabled() {
    return this.enabled;
  }

  /**
   * Sets the Istio enabled status.
   *
   * @param enabled True, if this domain is deployed under an Istio service mesh.
   */
  public void setEnabled(Boolean enabled) {
    this.enabled = enabled;
  }

  /**
   * Get the readiness port.
   *
   * @return the readiness port.
   */
  public Integer getReadinessPort() {
    return this.readinessPort;
  }

  /**
   * Sets the Istio readiness port.
   *
   * @param readinessPort the Istio readiness port.
   */
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

  /**
   * Get the semantic version.
   *
   * @return the semantic version
   */
  public SemanticVersion getSemanticVersion() {
    if (semanticVersion == null) {
      semanticVersion = new SemanticVersion(version);
    }

    return semanticVersion;
  }

  /**
   * Sets the Istio enabled status.
   *
   * @param istioEnabled True, if this domain is deployed under an Istio service mesh.
   * @return this
   */
  public Istio withIstioEnabled(Boolean istioEnabled) {
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

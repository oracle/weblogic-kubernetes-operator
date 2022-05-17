// Copyright (c) 2019, 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.weblogic.domain.model;

import oracle.kubernetes.json.Description;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;

public class Istio {

  @Description(
      "True, if this domain is deployed under an Istio service mesh. "
          + "Defaults to true when the `istio` field is specified.")
  private Boolean enabled = true;

  @Description(
      "The operator will create WebLogic network access points with this port on each WebLogic Server. "
          + "The readiness probe on each pod will use these network access points to verify that the "
          + "pod is ready for application traffic. Defaults to 8888.")
  private Integer readinessPort = 8888;

  public static final Integer DEFAULT_REPLICATION_PORT = 4564;

  @Description(
      "The operator will create a `T3` protocol WebLogic network access point on each WebLogic Server "
          + "that is part of a cluster with this port to handle EJB and servlet session state replication traffic "
          + "between servers. This setting is ignored for clusters where the WebLogic cluster configuration already "
          + "defines a `replication-channel` attribute. Defaults to 4564.")
  private Integer replicationChannelPort = DEFAULT_REPLICATION_PORT;

  @Description(
      "This setting was added in operator version 3.3.3, "
          + "defaults to the `istioLocalhostBindingsEnabled` Helm install value "
          + "which in turn defaults to `true`, "
          + "and is ignored in version 4.0 and later. In version 3.x, when `true`, the operator "
          + "creates a WebLogic network access point with a `localhost` binding for each existing "
          + "channel and protocol.  In version 3.x, use `true` for Istio versions prior to 1.10 "
          + "and set to `false` for version 1.10 and later.  Version 4.0 and later requires Istio "
          + "1.10 and later, will not create localhost bindings, and ignores this attribute. "
          + "More info: https://oracle.github.io/weblogic-kubernetes-operator/userguide/"
          + "managing-operators/using-helm/#istiolocalhostbindingsenabled")
  private Boolean localhostBindingsEnabled;

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
   * True, if the proxy redirects traffic to localhost.
   *
   * @return True, if the proxy redirects traffic to localhost.
   */
  public Boolean getLocalhostBindingsEnabled() {
    return this.localhostBindingsEnabled;
  }

  /**
   * Sets the 'localhostBindingsEnabled' Istio configuration property to indicate the proxy
   * redirects traffic to localhost.
   *
   * @param localhostBindingsEnabled True, if proxy redirects traffic to localhost.
   */
  public void setLocalhostBindingsEnabled(Boolean localhostBindingsEnabled) {
    this.localhostBindingsEnabled = localhostBindingsEnabled;
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
            .append("localhostBindingsEnabled", localhostBindingsEnabled);

    return builder.toString();
  }

  @Override
  public int hashCode() {
    HashCodeBuilder builder = new HashCodeBuilder().append(enabled).append(readinessPort)
        .append(replicationChannelPort);
    if (localhostBindingsEnabled != null) {
      builder.append(localhostBindingsEnabled);
    }

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
            .append(replicationChannelPort, rhs.replicationChannelPort);

    if (localhostBindingsEnabled != null) {
      builder.append(localhostBindingsEnabled, rhs.localhostBindingsEnabled);
    }

    return builder.isEquals();
  }
}

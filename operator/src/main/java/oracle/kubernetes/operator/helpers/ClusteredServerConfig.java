// Copyright (c) 2018, 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helpers;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;

/** ClusteredServerConfig describes the desired state of a clustered server. */
public class ClusteredServerConfig extends ServerConfig {

  private String clusteredServerStartPolicy;
  private String clusterName;

  @Override
  public String toString() {
    return new ToStringBuilder(this)
        .appendSuper(super.toString())
        .append("clusterName", clusterName)
        .append("clusteredServerStartPolicy", clusteredServerStartPolicy)
        .toString();
  }

  @Override
  public int hashCode() {
    return new HashCodeBuilder()
        .appendSuper(super.hashCode())
        .append(clusterName)
        .append(clusteredServerStartPolicy)
        .toHashCode();
  }

  @Override
  public boolean equals(Object other) {
    if (other == this) {
      return true;
    }
    if (!(other instanceof ClusteredServerConfig)) {
      return false;
    }
    ClusteredServerConfig rhs = ((ClusteredServerConfig) other);
    return new EqualsBuilder()
        .appendSuper(super.equals(other))
        .append(clusterName, rhs.clusterName)
        .append(clusteredServerStartPolicy, rhs.clusteredServerStartPolicy)
        .isEquals();
  }
}

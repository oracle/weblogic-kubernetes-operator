// Copyright (c) 2018, 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helpers;

import java.util.HashMap;
import java.util.Map;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;

/** DomainConfig describes the desired state of a domain. */
public class DomainConfig {

  private final Map<String, NonClusteredServerConfig> servers = new HashMap<>();
  private final Map<String, ClusterConfig> clusters = new HashMap<>();

  @Override
  public String toString() {
    return new ToStringBuilder(this)
        .append("servers", servers)
        .append("clusters", clusters)
        .toString();
  }

  @Override
  public int hashCode() {
    return new HashCodeBuilder().append(servers).append(clusters).toHashCode();
  }

  @Override
  public boolean equals(Object other) {
    if (other == this) {
      return true;
    }
    if (!(other instanceof DomainConfig)) {
      return false;
    }
    DomainConfig rhs = ((DomainConfig) other);
    return new EqualsBuilder()
        .append(servers, rhs.servers)
        .append(clusters, rhs.clusters)
        .isEquals();
  }
}

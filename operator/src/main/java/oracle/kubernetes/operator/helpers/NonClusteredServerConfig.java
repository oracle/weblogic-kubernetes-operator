// Copyright (c) 2018, 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helpers;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;

/** NonClusteredServerConfig describes the desired state of a non-clustered server. */
public class NonClusteredServerConfig extends ServerConfig {

  public static final String NON_CLUSTERED_SERVER_START_POLICY_ALWAYS = SERVER_START_POLICY_ALWAYS;
  public static final String NON_CLUSTERED_SERVER_START_POLICY_NEVER = SERVER_START_POLICY_NEVER;

  private String nonClusteredServerStartPolicy;

  @Override
  public String toString() {
    return new ToStringBuilder(this)
        .appendSuper(super.toString())
        .append("nonClusteredServerStartPolicy", nonClusteredServerStartPolicy)
        .toString();
  }

  @Override
  public int hashCode() {
    return new HashCodeBuilder()
        .appendSuper(super.hashCode())
        .append(nonClusteredServerStartPolicy)
        .toHashCode();
  }

  @Override
  public boolean equals(Object other) {
    if (other == this) {
      return true;
    }
    if (!(other instanceof NonClusteredServerConfig)) {
      return false;
    }
    NonClusteredServerConfig rhs = ((NonClusteredServerConfig) other);
    return new EqualsBuilder()
        .appendSuper(super.equals(other))
        .append(nonClusteredServerStartPolicy, rhs.nonClusteredServerStartPolicy)
        .isEquals();
  }
}

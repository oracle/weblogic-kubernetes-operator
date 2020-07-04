// Copyright (c) 2018, 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.weblogic.domain.model;

import javax.annotation.Nonnull;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;
import oracle.kubernetes.json.Description;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;

public class ManagedServer extends Server implements Comparable<ManagedServer> {
  /** The name of the Managed Server. Required. */
  @SerializedName("serverName")
  @Expose
  @Description("The name of the Managed Server. This name must match the name of a Managed Server instance or of a "
      + "dynamic cluster member name from a server template already defined in the WebLogic domain configuration. "
      + "Required.")
  @Nonnull
  private String serverName;

  public String getServerName() {
    return serverName;
  }

  public void setServerName(@Nonnull String serverName) {
    this.serverName = serverName;
  }

  public ManagedServer withServerName(@Nonnull String serverName) {
    setServerName(serverName);
    return this;
  }

  @Override
  public String toString() {
    return new ToStringBuilder(this)
        .appendSuper(super.toString())
        .append("serverName", serverName)
        .toString();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }

    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    if (!(o instanceof ManagedServer)) {
      return false;
    }

    ManagedServer that = (ManagedServer) o;

    return new EqualsBuilder()
        .appendSuper(super.equals(o))
        .append(serverName, that.serverName)
        .isEquals();
  }

  @Override
  public int hashCode() {
    return new HashCodeBuilder(17, 37)
        .appendSuper(super.hashCode())
        .append(serverName)
        .toHashCode();
  }

  @Override
  public int compareTo(ManagedServer o) {
    return serverName.compareTo(o.serverName);
  }
}

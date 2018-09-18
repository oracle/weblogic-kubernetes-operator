// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.weblogic.domain.v2;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;
import javax.annotation.Nonnull;

public class ManagedServer extends Server {
  /** The name of the managed server. Required. */
  @SerializedName("serverName")
  @Expose
  private String serverName;

  public String getServerName() {
    return serverName;
  }

  public void setServerName(@Nonnull String serverName) {
    this.serverName = serverName;
  }

  ManagedServer withServerName(@Nonnull String serverName) {
    setServerName(serverName);
    return this;
  }
}

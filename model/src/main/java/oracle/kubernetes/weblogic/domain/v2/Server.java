// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.weblogic.domain.v2;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

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
}

// Copyright (c) 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.wlsconfig;

public class PortDetails {

  private final int portNum;
  private final boolean portSecure;

  public PortDetails(int portNum, boolean portSecure) {
    this.portNum = portNum;
    this.portSecure = portSecure;
  }

  public String toHttpUrl(String host) {
    return String.format("http%s://%s:%d", (portSecure ? "s" : ""), host, portNum);
  }
}

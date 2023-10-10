// Copyright (c) 2021, 2023, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.wlsconfig;

public class PortDetails {

  private final int portNum;
  private final boolean portSecure;

  public PortDetails(int portNum, boolean portSecure) {
    this.portNum = portNum;
    this.portSecure = portSecure;
  }

  /**
   * Generates a URL containing the host name.
   * @param host Host name
   * @return URL containing the indicated host name
   */
  public String toHttpUrl(String host) {
    if (host.contains(":")) {
      host = "[" + host + "]";
    }
    return String.format("http%s://%s:%d", (portSecure ? "s" : ""), host, portNum);
  }
}

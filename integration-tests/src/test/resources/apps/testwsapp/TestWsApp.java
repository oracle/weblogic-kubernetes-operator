// Copyright (c) 2019, 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package apps.testwsapp;

import java.net.InetAddress;
import javax.jws.WebMethod;
import javax.jws.WebService;
import javax.jws.soap.SOAPBinding;

@WebService(name = "TestWsApp", targetNamespace = "http://example.org", serviceName = "TestWsApp")
// Standard JWS annotation that specifies the mapping of the service onto the
// SOAP message protocol.
@SOAPBinding(
    style = SOAPBinding.Style.DOCUMENT,
    use = SOAPBinding.Use.LITERAL,
    parameterStyle = SOAPBinding.ParameterStyle.WRAPPED)
public class TestWsApp {

  /**
   * Check addresses and hosts.
   * @return document content
   */
  @WebMethod
  public String checkInetAddressAndHost() {

    InetAddress inetAddress;
    try {
      inetAddress = getInetAddressInfo();
      return "<li>InetAddress: "
          + inetAddress.toString()
          + "</li><li>"
          + "InetAddress.hostname: "
          + inetAddress.getHostName()
          + "</li>";
    } catch (Exception ex) {
      return ex.getMessage();
    }
  }

  private InetAddress getInetAddressInfo() throws Exception {
    return InetAddress.getLocalHost();
  }
}

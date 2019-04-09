// Copyright 2019, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package apps.testwsapp;

import javax.jws.WebMethod;
import javax.jws.WebService;
import javax.xml.ws.Endpoint;
import java.net.InetAddress;
import javax.jws.soap.SOAPBinding;

@WebService(name = "TestWSApp", targetNamespace = "http://example.org", serviceName = "TestWSApp")
// Standard JWS annotation that specifies the mapping of the service onto the
// SOAP message protocol.
@SOAPBinding(style = SOAPBinding.Style.DOCUMENT,
        use = SOAPBinding.Use.LITERAL,
        parameterStyle = SOAPBinding.ParameterStyle.WRAPPED)
public class TestWSApp {
    @WebMethod
    public String checkInetAddressAndHost() {

        InetAddress inetAddress;
        try {
            inetAddress = getInetAddressInfo();
            return "<li>InetAddress: " + inetAddress.toString() + "</li><li>" + "InetAddress.hostname: " + inetAddress.getHostName() + "</li>";
        } catch (Exception ex) {
            return ex.getMessage();
        }
    }

    private InetAddress getInetAddressInfo() throws Exception {
        return InetAddress.getLocalHost();
    }

}

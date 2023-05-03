// Copyright (c) 2023, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package saml.sendervouches.service;

import javax.jws.WebMethod;
import javax.jws.WebService;
import javax.jws.soap.SOAPBinding;

import weblogic.jws.Policies;
import weblogic.jws.Policy;
import weblogic.jws.WLHttpTransport;
import weblogic.jws.security.WssConfiguration;


@WebService(name = "Echo", serviceName = "EchoJavaComponent", targetNamespace = "http://example.org")
@WssConfiguration(value = "default_wss")
@SOAPBinding(style = SOAPBinding.Style.RPC, use = SOAPBinding.Use.ENCODED)
@WLHttpTransport(contextPath = "samlSenderVouches", serviceUri = "EchoService")
public class EchoService {

  @WebMethod()
  @Policies({
      @Policy(uri = "policy:Wssp1.2-2007-Saml2.0-SenderVouches-Wss1.1-Basic256Sha256.xml",
          direction = Policy.Direction.inbound)})

  public String echoSignedSamlV20Token11(String input) {
    return "echoSignedSamlV20Token11 '" + input + "'...";
  }
}

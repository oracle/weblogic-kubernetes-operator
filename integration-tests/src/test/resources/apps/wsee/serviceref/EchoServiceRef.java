// Copyright (c) 2023, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package saml.sendervouches.serviceref;

import java.security.cert.X509Certificate;

import javax.jws.WebService;
import javax.jws.WebMethod;
import javax.xml.rpc.ServiceException;
import javax.xml.rpc.Stub;

import saml.sendervouches.client.EchoService.Echo;
import saml.sendervouches.client.EchoService.EchoJavaComponent;
import saml.sendervouches.client.EchoService.EchoJavaComponent_Impl;

import java.rmi.RemoteException;
import java.security.cert.X509Certificate;
import weblogic.jws.security.WssConfiguration;
import weblogic.jws.WLHttpTransport;
import weblogic.wsee.security.util.CertUtils;
import weblogic.wsee.security.bst.StubPropertyBSTCredProv;
import weblogic.wsee.security.util.CertUtils;
import weblogic.wsee.security.bst.StubPropertyBSTCredProv;

@WebService(name = "EchoServiceRef", serviceName = "EchoServiceRef", targetNamespace = "http://www.bea.com")
@WssConfiguration(value = "default_wss")
@WLHttpTransport(contextPath = "EchoServiceRef", serviceUri = "Echo")
public class EchoServiceRef {
  private static Echo getPort(String endpointAddress) throws ServiceException {
    EchoJavaComponent service = new EchoJavaComponent_Impl(endpointAddress + "?WSDL");
    Echo port = service.getEchoSoapPort();
    return port;
  }

  @WebMethod()
  public String echoSignedSamlV20Token11(String endpointAddress,
                                         String input, String serverCert) throws RemoteException, Exception {
    Echo port = getPort(endpointAddress);
    X509Certificate cert = CertUtils.getCertificate(serverCert);
    ((Stub) port)._setProperty(StubPropertyBSTCredProv.SERVER_ENCRYPT_CERT, cert);
    return port.echoSignedSamlV20Token11(input);
  }
}
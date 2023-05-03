// Copyright (c) 2023, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

import javax.xml.rpc.Stub;
import javax.xml.ws.WebServiceRef;

import saml.sendervouches.client.EchoServiceRef.EchoServiceRef_Service;
import saml.sendervouches.client.EchoServiceRef.EchoServiceRef_PortType;
import saml.sendervouches.client.EchoServiceRef.EchoServiceRef_Service_Impl;

public class WseeClient {
  private EchoServiceRef_Service service;
  public static void main(String[] args) {
    try {
      WseeClient client = new WseeClient();
      client.doTest(args);
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  public void doTest(String[] args) {
    String serversenderURI = args[0];
    String serverrecieverURI = args[1];
    String username = args[2];
    String password = args[3];
    String certPath = args[4];
    try {
        service = new EchoServiceRef_Service_Impl(serversenderURI + "?WSDL");
      EchoServiceRef_PortType port = service.getEchoServiceRefSoapPort();
        Stub stub = (Stub) port;
        ((Stub)port)._setProperty(Stub.USERNAME_PROPERTY, username);
        ((Stub)port)._setProperty(Stub.PASSWORD_PROPERTY, password);
        System.setProperty("weblogic.xml.crypto.wss.provider.SecurityTokenHandler","weblogic.wsee.security.saml.SAMLTokenHandler");
        System.out.println("Invoking the echoSignedSamlV20Token11 operation on the port.");
        String response = port.echoSignedSamlV20Token11(serverrecieverURI, "Hi, world!", certPath);
        System.out.println(response);
      } catch(Exception e){
        e.printStackTrace();
      }
    }
  }
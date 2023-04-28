// Copyright (c) 2023, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package saml.sendervouches.filter;

import java.io.ByteArrayInputStream;
import javax.servlet.http.HttpServletRequest;

import weblogic.xml.saaj.SOAPConstants;

public class RawXMLInputStream extends RawXMLInputStreamBase {
  private static int count = 0;
  protected String inputStr = null;
  protected String samlAssertionReplace = null;

  /**
   * Constructor for RaqXMLInputStream.
   * @param req http servlet request
   */
  public RawXMLInputStream(HttpServletRequest req) {
    super(req);
    this.request = req;
    try {
      this.input = req.getInputStream();

      StringBuffer sbuf = new StringBuffer();
      int i = input.read();
      while (i != -1) {
        sbuf.append((char) i);
        i = input.read();
      }
      inputStr = sbuf.toString().trim();

      String soapEnvelope = SOAPConstants.ENV_PREFIX + ":Envelope";
      if (inputStr.indexOf(soapEnvelope) == -1) {
        isRawXML = true;
      }

      this.bais = new ByteArrayInputStream(inputStr.getBytes());
      count++;
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  public String getInputStr() {
    return this.inputStr;
  }

}



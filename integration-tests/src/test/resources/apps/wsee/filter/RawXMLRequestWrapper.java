// Copyright (c) 2023, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package saml.sendervouches.filter;

import javax.servlet.ServletInputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;

public class RawXMLRequestWrapper extends HttpServletRequestWrapper {
  private RawXMLInputStream rxis = null;

  public RawXMLRequestWrapper(HttpServletRequest req) {
    super(req);
    rxis = new RawXMLInputStream(req);
  }

  public ServletInputStream getInputStream() {
    return rxis;
  }


  /**
   * Get Header.
   *
   * @param name name ofheader
   * @return name
   */
  public String getHeader(String name) {
    if (name.equalsIgnoreCase("Content-Type")) {
      return "text/xml";
    } else {
      return super.getHeader(name);
    }
  }

  public boolean isRawXML() {
    return rxis.isRawXML();
  }

  public String toString() {
    return rxis.getInputStr();
  }
}

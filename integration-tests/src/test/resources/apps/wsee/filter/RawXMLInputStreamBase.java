// Copyright (c) 2023, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package saml.sendervouches.filter;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ResourceBundle;
import javax.servlet.ReadListener;
import javax.servlet.ServletInputStream;
import javax.servlet.http.HttpServletRequest;

public class RawXMLInputStreamBase extends ServletInputStream {
  // The response with which this servlet output stream is associated.
  protected HttpServletRequest request = null;

  // The underlying servket input stream to which we should write data.
  protected ServletInputStream input = null;

  // The underlying servket output stream to which we should write data.
  protected ByteArrayInputStream bais = null;

  public boolean isRawXML = false;

  public static String top = null;
  public static String bottom = null;
  public static int count = 0;
  public static String testResultDir = null;

  static {
    try {
      ResourceBundle resource = ResourceBundle.getBundle("RawXML");
      if (resource != null) {
        top = (String)resource.getObject("rawXMLTop");
        bottom = (String)resource.getObject("rawXMLBottom");
        testResultDir = (String)resource.getObject("test.result.dir");
      }
    } catch (Exception ne) {
      ne.printStackTrace();
    }
  }

  public RawXMLInputStreamBase(HttpServletRequest req) {
    super();
  }

  public boolean isRawXML() {
    return isRawXML;
  }

  public int read() throws IOException {
    return bais.read();
  }

  public int read(byte[] b) throws IOException {
    return bais.read(b, 0, b.length);
  }

  public int read(byte[] b, int off, int len) throws IOException {
    return bais.read(b, off, len);
  }

  public void trace(String data) {
    System.out.println("RawXMLInputStreamBase: " + data);
  }

  public boolean isFinished() {
    return false;
  }

  public boolean isReady() {
    return false;
  }

  public void setReadListener(ReadListener readListener) {
    throw new IllegalStateException("Not Supported");
  }
}



// Copyright (c) 2023, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package saml.sendervouches.filter;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import javax.servlet.http.HttpServletResponse;

public class RawXMLOutputStream extends RawXMLOutputStreamBase {

  /**
   * Constructor for RawXMLOutputStream.
   * @param response servlet response
   * @throws IOException exception
   */
  public RawXMLOutputStream(HttpServletResponse response) throws IOException {
    super(response);
    closed = false;
    commit = false;
    count = 0;
    this.response = response;
    this.output = response.getOutputStream();
    this.baos = new ByteArrayOutputStream();
  }
}

// Copyright (c) 2023, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package saml.sendervouches.filter;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import javax.servlet.ServletOutputStream;
import javax.servlet.WriteListener;
import javax.servlet.http.HttpServletResponse;


public class RawXMLOutputStreamBase extends ServletOutputStream {

  // The response with which this servlet output stream is associated.
  protected HttpServletResponse response = null;

  // The underlying servket output stream to which we should write data.
  protected ServletOutputStream output = null;

  // The underlying servket output stream to which we should write data.
  protected ByteArrayOutputStream baos = null;

  // Has this stream been closed?
  protected boolean closed = false;

  // Should we commit the response when we are flushed?
  protected boolean commit = true;

  // The number of bytes which have already been written to this stream.
  protected int count = 0;


  /**
   * Constructor for RawXMLOutputStreamBase.
   * @param response servlet response
   * @throws IOException excption
   */
  public RawXMLOutputStreamBase(HttpServletResponse response)
                            throws IOException {
    super();
    closed = false;
    commit = false;
    count = 0;
    this.response = response;
    this.output = response.getOutputStream();
    this.baos = new ByteArrayOutputStream();
  }

  /**
   * Convert to String.
   * @return string
   */
  public String toString() {
    return this.baos.toString();
  }

  /**
   * Implement write.
   * @param b bytes
   * @throws IOException exception
   */
  public void write(int b) throws IOException {
    if (closed) {
      throw new IOException("Cannot write to a closed output stream");
    }
    baos.write(b);
  }

  /**
   * Close this output stream, causing any buffered data to be flushed and.
   * any further output data to throw an IOException.
   */
  public void close() throws IOException {

    if (closed) {
      throw new IOException("This output stream has already been closed");
    }
    if (baos != null) {
      baos.close();
    }
    closed = true;
  }

  /**
   * Copy servlet stream.
   * @return servlet output stream
   * @throws IOException exception
   */
  public ServletOutputStream getCopiedStream()throws IOException {

    if (baos != null) {
      baos.writeTo(output);
    }
    return output;
  }


  private void trace(String data) {
    System.out.println("RawXMLOutputStreamBase: " + data);
  }

  @Override
  public boolean isReady() {
    return false;
  } 
    
  @Override
  public void setWriteListener(WriteListener writeListener) {
    throw new IllegalStateException("Not Supported");
  }

}



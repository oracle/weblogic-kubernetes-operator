// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.utils;

import com.google.common.io.ByteStreams;
import java.io.*;
import java.util.stream.Collectors;

/** Class for executing shell commands from java */
public class ExecCommand {

  public static ExecResult exec(String command) throws Exception {
    return exec(command, false);
  }

  public static ExecResult exec(String command, boolean isRedirectToOut) throws Exception {
    Process p = Runtime.getRuntime().exec(new String[] {"/bin/sh", "-c", command});

    InputStreamWrapper in = new SimpleInputStreamWrapper(p.getInputStream());
    Thread out = null;

    try {
      if (isRedirectToOut) {
        InputStream i = in.getInputStream();
        @SuppressWarnings("resource")
        CopyingOutputStream copyOut = new CopyingOutputStream(System.out);
        // this makes sense because CopyingOutputStream is an InputStreamWrapper
        in = copyOut;
        out =
            new Thread(
                new Runnable() {
                  public void run() {
                    try {
                      ByteStreams.copy(i, copyOut);
                    } catch (IOException ex) {
                      ex.printStackTrace();
                    }
                  }
                });
        out.start();
      }

      p.waitFor();
      return new ExecResult(p.exitValue(), read(in.getInputStream()), read(p.getErrorStream()));
    } finally {
      if (out != null) {
        out.join();
      }
      p.destroy();
    }
  }

  private static String read(InputStream is) throws Exception {
    return new BufferedReader(new InputStreamReader(is)).lines().collect(Collectors.joining("\n"));
  }

  private interface InputStreamWrapper {
    InputStream getInputStream();
  }

  private static class SimpleInputStreamWrapper implements InputStreamWrapper {
    final InputStream in;

    SimpleInputStreamWrapper(InputStream in) {
      this.in = in;
    }

    @Override
    public InputStream getInputStream() {
      return in;
    }
  }

  private static class CopyingOutputStream extends OutputStream implements InputStreamWrapper {
    final OutputStream out;
    final ByteArrayOutputStream copy = new ByteArrayOutputStream();

    CopyingOutputStream(OutputStream out) {
      this.out = out;
    }

    @Override
    public void write(int b) throws IOException {
      out.write(b);
      copy.write(b);
    }

    @Override
    public InputStream getInputStream() {
      return new ByteArrayInputStream(copy.toByteArray());
    }
  }
}

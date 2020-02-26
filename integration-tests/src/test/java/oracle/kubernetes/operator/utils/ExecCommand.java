// Copyright (c) 2018, 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.utils;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

import com.google.common.io.ByteStreams;

/** Class for executing shell commands from java. */
public class ExecCommand {

  public static ExecResult exec(String command) throws Exception {
    return exec(command, false, null);
  }

  public static ExecResult exec(String command, boolean isRedirectToOut) throws Exception {
    return exec(command, isRedirectToOut, null);
  }

  /**
   * execute command.
   * @param command command
   * @param isRedirectToOut redirect to out flag
   * @param additionalEnvMap additional environment map
   * @return result
   * @throws Exception on failure
   */
  public static ExecResult exec(
      String command, boolean isRedirectToOut, Map<String, String> additionalEnvMap)
      throws Exception {

    Process p = null;
    if (additionalEnvMap == null) {
      p = Runtime.getRuntime().exec(new String[] {"/bin/sh", "-c", command});
    } else {
      // Combine new env vars with existing ones and generate a string array with those values
      // If the 2 maps have a dup key then the additional env map entry will replace the existing.
      Map<String, String> combinedEnvMap = new HashMap();
      combinedEnvMap.putAll(System.getenv());
      combinedEnvMap.putAll(additionalEnvMap);
      String[] envParams = generateNameValueArrayFromMap(combinedEnvMap);
      p = Runtime.getRuntime().exec(new String[] {"/bin/sh", "-c", command}, envParams);
    }

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
                () -> {
                  try {
                    ByteStreams.copy(i, copyOut);
                  } catch (IOException ex) {
                    ex.printStackTrace();
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

  /**
   * Generate a string array of name=value items, one for each env map entry.
   *
   * @return
   */
  private static String[] generateNameValueArrayFromMap(Map<String, String> map) {
    int mapSize = map.size();
    String[] strArray = new String[mapSize];
    int i = 0;
    for (Map.Entry<String, String> entry : map.entrySet()) {
      strArray[i++] = entry.getKey() + "=" + entry.getValue();
    }
    return strArray;
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

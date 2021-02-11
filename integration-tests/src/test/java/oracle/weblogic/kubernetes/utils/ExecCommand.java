// Copyright (c) 2018, 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes.utils;

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

import io.kubernetes.client.util.Streams;

/** 
 * Class for executing shell commands from java. 
 */
public class ExecCommand {

  public static ExecResult exec(String command) throws IOException, InterruptedException {
    return exec(command, false, null);
  }

  public static ExecResult exec(
      String command, 
      boolean isRedirectToOut
  ) throws IOException, InterruptedException {
    return exec(command, isRedirectToOut, null);
  }

  /**
   * execute command.
   * @param command command
   * @param isRedirectToOut redirect to out flag
   * @param additionalEnvMap additional environment map
   * @return result
   * @throws IOException if the command failed to execute
   * @throws InterruptedException if the process was interrupted
   */
  public static ExecResult exec(
      String command, 
      boolean isRedirectToOut,
      Map<String, String> additionalEnvMap
  ) throws IOException, InterruptedException {

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
                    Streams.copy(i, copyOut);
                  } catch (IOException ex) {
                    ex.printStackTrace();
                  }
                });
        out.start();
      }

      p.waitFor();

      // we need to join the thread before we read the stdout so that the saved stdout is complete
      if (out != null) {
        out.join();
        out = null;
      }

      return new ExecResult(p.exitValue(), read(in.getInputStream()), read(p.getErrorStream()));
    
    } finally {
      // we try to join again if for any reason the code failed before the previous attempt
      if (out != null) {
        out.join();
      }
      p.destroy();
    }
  }

  /**
   * Generate a string array of name=value items, one for each env map entry.
   *
   * @return list of envs
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

  private static String read(InputStream is) throws IOException {
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

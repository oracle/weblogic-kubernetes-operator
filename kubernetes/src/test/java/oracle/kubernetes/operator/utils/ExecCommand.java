// Copyright (c) 2018, 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.utils;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.stream.Collectors;

/**
 * Class for executing shell commands from java.
 */
public class ExecCommand {

  /**
   * execute command.
   * @param command command
   * @return executor
   * @throws Exception on failure
   */
  public static ExecResult exec(String command) throws Exception {
    Process p = Runtime.getRuntime().exec(command);
    try {
      p.waitFor();
      return new ExecResult(p.exitValue(), read(p.getInputStream()), read(p.getErrorStream()));
    } finally {
      p.destroy();
    }
  }

  private static String read(InputStream is) throws Exception {
    return new BufferedReader(new InputStreamReader(is)).lines().collect(Collectors.joining("\n"));
  }
}

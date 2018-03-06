package oracle.kubernetes.operator.create;

import java.io.*;
import java.util.stream.Collectors;

/**
 * Class for executing shell commands from java
 */
public class ExecCommand {

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

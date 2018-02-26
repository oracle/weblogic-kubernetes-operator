package oracle.kubernetes;

import org.apache.commons.exec.CommandLine;
import org.apache.commons.exec.DefaultExecutor;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.ConsoleHandler;
import java.util.logging.Handler;
import java.util.logging.Logger;

public class TestUtils {
  private static Boolean kubernetesStatus;

  /**
   * Returns true if Kubernetes-dependent tests should run
   */
  public static boolean isKubernetesAvailable() { // assume it is available when running on Linux
    if (kubernetesStatus == null)
      kubernetesStatus = checkKubernetes();
    return kubernetesStatus;
  }

    private static Boolean checkKubernetes() {
      PrintStream savedOut = System.out;
      System.setOut(new PrintStream(new ByteArrayOutputStream()));
      try {
        CommandLine cmdLine = CommandLine.parse("kubectl cluster-info");
        DefaultExecutor executor = new DefaultExecutor();
        executor.execute(cmdLine);
        return true;
      } catch (IOException e) {
        return false;
      } finally {
        System.setOut(savedOut);
      }
    }

  public static List<Handler> removeConsoleHandlers(Logger logger) {
    List<Handler> savedHandlers = new ArrayList<>();
    for (Handler handler : logger.getHandlers()) {
      if (handler instanceof ConsoleHandler) {
        savedHandlers.add(handler);
      }
    }
    for (Handler handler : savedHandlers)
      logger.removeHandler(handler);
    return savedHandlers;
  }

  public static void restoreConsoleHandlers(Logger logger, List<Handler> savedHandlers) {
    for (Handler handler : savedHandlers) {
      logger.addHandler(handler);
    }
  }
}

// Copyright (c) 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes.actions.impl;

import java.nio.file.Path;
import java.nio.file.Paths;

import oracle.weblogic.kubernetes.actions.impl.primitive.Command;
import oracle.weblogic.kubernetes.actions.impl.primitive.Installer;
import oracle.weblogic.kubernetes.logging.LoggingFacade;
import oracle.weblogic.kubernetes.utils.ExecResult;

import static oracle.weblogic.kubernetes.actions.ActionConstants.REMOTECONSOLE_FILE;
import static oracle.weblogic.kubernetes.actions.ActionConstants.RESOURCE_DIR;
import static oracle.weblogic.kubernetes.actions.ActionConstants.WORK_DIR;
import static oracle.weblogic.kubernetes.actions.impl.primitive.Command.defaultCommandParams;
import static oracle.weblogic.kubernetes.actions.impl.primitive.Installer.defaultInstallRemoteconsoleParams;
import static oracle.weblogic.kubernetes.utils.ExecCommand.exec;
import static oracle.weblogic.kubernetes.utils.TestUtils.callWebAppAndWaitTillReady;
import static oracle.weblogic.kubernetes.utils.ThreadSafeLogger.getLogger;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

/**
 * Utility class for WebLogic Remote Console.
 */
public class WebLogicRemoteConsole {

  private static LoggingFacade logger = getLogger();

  /**
   * Install WebLogic Remote Console.
   *
   * @return true if WebLogic Remote Console is successfully installed, false otherwise.
   */
  public static boolean installWlsRemoteConsole() {
    if (!downloadRemoteConsole()) {
      return false;
    }

    if (!runRemoteconsole()) {
      return false;
    }

    return true;
  }

  /**
   * Shutdown WebLogic Remote Console.
   *
   * @return true if WebLogic Remote Console is successfully shutdown, false otherwise.
   */
  public static boolean shutdownWlsRemoteConsole() {

    Path shutdownRemoteconsolePath =
        Paths.get(RESOURCE_DIR, "bash-scripts", "shutdown-remoteconsole.sh");
    String shutdownScript = shutdownRemoteconsolePath.toString();
    String command = "sh " + shutdownScript;
    logger.info("Remote console shutdown command {0}", command);
    return  Command.withParams(
        defaultCommandParams()
            .command(command)
            .redirect(false))
        .execute();

  }

  private static boolean downloadRemoteConsole() {

    return Installer.withParams(
        defaultInstallRemoteconsoleParams())
        .download();
  }

  private static boolean runRemoteconsole() {

    String jarLocation = REMOTECONSOLE_FILE;
    StringBuffer javaCmd = new StringBuffer("java -jar ");
    javaCmd.append(jarLocation);
    javaCmd.append(" > ");
    javaCmd.append(WORK_DIR + "/console");
    javaCmd.append("/remoteconsole.out 2>&1 ");
    javaCmd.append(WORK_DIR + "/console");
    javaCmd.append(" &");
    logger.info("java command to be run {0}", javaCmd.toString());

    ExecResult result = assertDoesNotThrow(() -> exec(new String(javaCmd), true));
    logger.info("java returned {0}", result.toString());
    logger.info("java returned EXIT value {0}", result.exitValue());

    return ((result.exitValue() == 0) && accessRemoteconsole());

  }

  private static boolean accessRemoteconsole() {

    String curlCmd = "curl -s -L --show-error --noproxy '*' "
        + " http://localhost:8012"
        + " --write-out %{http_code} -o /dev/null";
    logger.info("Executing curl command {0}", curlCmd);

    return callWebAppAndWaitTillReady(curlCmd, 5);

  }

}

// Copyright (c) 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes.actions.impl;

import java.nio.file.Paths;

import oracle.weblogic.kubernetes.actions.impl.primitive.Installer;
import oracle.weblogic.kubernetes.logging.LoggingFacade;
import oracle.weblogic.kubernetes.utils.ExecResult;

import static oracle.weblogic.kubernetes.actions.ActionConstants.REMOTECONSOLE_FILE;
import static oracle.weblogic.kubernetes.actions.ActionConstants.WORK_DIR;
import static oracle.weblogic.kubernetes.actions.impl.primitive.Installer.defaultInstallRemoteconsoleParams;
import static oracle.weblogic.kubernetes.utils.ApplicationUtils.callWebAppAndWaitTillReady;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.testUntil;
import static oracle.weblogic.kubernetes.utils.ExecCommand.exec;
import static oracle.weblogic.kubernetes.utils.FileUtils.copyFileFromPod;
import static oracle.weblogic.kubernetes.utils.ThreadSafeLogger.getLogger;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

/**
 * Utility class for WebLogic Remote Console.
 */
public class WebLogicRemoteConsole {

  private static LoggingFacade logger = getLogger();

  /**
   * Install WebLogic Remote Console.
   * @param domainNamespace namespace in which the domain will be created
   * @param adminServerPodName the name of the admin server pod
   * @return true if WebLogic Remote Console is successfully installed, false otherwise.
   */
  public static boolean installWlsRemoteConsole(String domainNamespace, String adminServerPodName) {
    if (!downloadRemoteConsole()) {
      return false;
    }

    if (!runRemoteconsole(domainNamespace, adminServerPodName)) {
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

    String command = "kill -9 `jps | grep console.jar | awk '{print $1}'`";
    logger.info("Command to shutdown the remote console: {0}", command);
    ExecResult result = assertDoesNotThrow(() -> exec(command, true));
    logger.info("Shutdown command returned {0}", result.toString());
    logger.info(" Shutdown command returned EXIT value {0}", result.exitValue());

    return (result.exitValue() == 0);

  }

  private static boolean downloadRemoteConsole() {

    return Installer.withParams(
        defaultInstallRemoteconsoleParams())
        .download();
  }

  private static boolean runRemoteconsole(String domainNamespace, String adminServerPodName) {

    String jarLocation = REMOTECONSOLE_FILE;

    assertDoesNotThrow(() -> copyFileFromPod(domainNamespace,
             adminServerPodName, "weblogic-server",
             "/u01/oracle/wlserver/server/lib/DemoTrust.jks",
             Paths.get(WORK_DIR, "DemoTrust.jks")));
    StringBuffer javaCmd = new StringBuffer("java");
    javaCmd.append(" -Dconsole.disableHostnameVerification=true");
    javaCmd.append(" -Djavax.net.ssl.trustStore=" + "\"" + WORK_DIR + "/DemoTrust.jks" + "\"");
    javaCmd.append(" -Djavax.net.ssl.trustStoreType=\"JKS\"");
    javaCmd.append(" -jar ");
    javaCmd.append(jarLocation);
    javaCmd.append(" > ");
    javaCmd.append(WORK_DIR + "/console");
    javaCmd.append("/remoteconsole.out 2>&1 ");
    javaCmd.append(WORK_DIR + "/console");
    javaCmd.append(" &");
    logger.info("java command to start remote console {0}", javaCmd.toString());

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

    testUntil((() -> {
      return callWebAppAndWaitTillReady(curlCmd, 1);
    }), logger, "Waiting for remote console access to return 200 status code");

    return true;

  }

}

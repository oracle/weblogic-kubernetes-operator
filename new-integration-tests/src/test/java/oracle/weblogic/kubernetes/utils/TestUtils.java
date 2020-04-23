// Copyright (c) 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes.utils;


import java.util.HashMap;
import java.util.List;

import static oracle.weblogic.kubernetes.extensions.LoggedTest.logger;

/**
 * The utility class for tests.
 */
public class TestUtils {

  /**
   * Run the specified command and check that the output of the command contains the expected value.
   *
   * @param command the command to run
   * @param expectedValue the expected value that the command output should contain
   * @return true if the command output contains the expected value, false otherwise
   */
  public static boolean runCmdAndCheckResultContainsString(String command, String expectedValue) {
    logger.info("Running command - \n" + command);

    ExecResult result;
    try {
      result = ExecCommand.exec(command);
      if (result.exitValue() != 0) {
        logger.info("Command failed with errors {0} \n {1}",result.stderr(), result.stdout());
        return false;
      }
    } catch (Exception e) {
      logger.info("Got exception, command failed with errors {0}", e.getMessage());
      return false;
    }

    if (!result.stdout().contains(expectedValue)) {
      logger.info("Did not get the expected result, expected: {0}, got {1}", expectedValue, result.stdout());
      return false;
    }

    return true;
  }

  /**
   * Call the curl command and check the app can be reached from all managed servers.
   *
   * @param curlCmd curl command to call the sample app
   * @param managedServerNames managed server names that the sample app response should return
   * @param maxIterations max interations to call the curl command
   * @throws Exception if the web app can not hit one or more managed servers
   */
  public static void callWebAppAndCheckForServerNameInResponse(String curlCmd,
                                                               List<String> managedServerNames,
                                                               int maxIterations) throws Exception {

    // map with server names and boolean values
    HashMap<String, Boolean> managedServers = new HashMap<String, Boolean>();
    managedServerNames.forEach(managedServerName -> {
      managedServers.put(managedServerName, false);
    });

    logger.info("Calling webapp at most {0} times using command: {1}", maxIterations, curlCmd);

    // check the response contains managed server name
    for (int i = 1; i <= maxIterations; i++) {
      if (!managedServers.containsValue(false)) {
        break;
      } else {
        ExecResult result = ExecCommand.exec(curlCmd, true);

        String response = result.stdout().trim();
        managedServers.keySet().forEach(key -> {
          if (response.contains(key)) {
            managedServers.put(key, true);
          }
        });
      }
    }

    // after the max iterations, check if any managedserver value is false
    managedServers.entrySet().forEach(entry -> {
      if (entry.getValue()) {
        logger.info("The sample app can be accessed from the server " + entry.getKey());
      } else {
        throw new RuntimeException(
            "FAILURE: The sample app can not be accessed from the server " + entry.getKey());
      }
    });
  }
}

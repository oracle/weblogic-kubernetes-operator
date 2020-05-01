// Copyright (c) 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes.utils;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.HashMap;
import java.util.List;

import static oracle.weblogic.kubernetes.extensions.LoggedTest.logger;

/**
 * The utility class for tests.
 */
public class TestUtils {

  /**
   * Call the curl command and check the app can be reached from all managed servers.
   *
   * @param curlCmd curl command to call the sample app
   * @param managedServerNames managed server names that the sample app response should return
   * @param maxIterations max iterations to call the curl command
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
        Thread.sleep(100);
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

  /**
   * Get the next free port between range from to to.
   *
   * @param from range starting point
   * @param to range ending port
   * @return the port number which is free
   */
  public static int getNextFreePort(int from, int to) {
    int port;
    for (port = from; port < to; port++) {
      if (isLocalPortFree(port)) {
        logger.info("next free port is: {0}", port);
        return port;
      }
    }
    logger.info("Can not find free port between {0} and {1}", from, to);
    return port;
  }

  /**
   * Check if the given port number is free.
   *
   * @param port port number to check
   * @return true if the port is free, false otherwise
   */
  public static boolean isLocalPortFree(int port) {
    try {
      new ServerSocket(port).close();
      return true;
    } catch (IOException e) {
      return false;
    }
  }
}

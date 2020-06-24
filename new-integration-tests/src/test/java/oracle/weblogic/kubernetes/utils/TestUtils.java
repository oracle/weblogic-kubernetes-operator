// Copyright (c) 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes.utils;

import java.io.IOException;
import java.net.Socket;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.TimeUnit;

import oracle.weblogic.kubernetes.logging.LoggingFacade;

import static oracle.weblogic.kubernetes.TestConstants.K8S_NODEPORT_HOST;
import static oracle.weblogic.kubernetes.utils.ThreadSafeLogger.getLogger;

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
   * @return true if the web app can hit all managed servers, false otherwise
   */
  public static boolean callWebAppAndCheckForServerNameInResponse(
      String curlCmd,
      List<String> managedServerNames,
      int maxIterations) {
    LoggingFacade logger = getLogger();
    // first map all server names with false
    HashMap<String, Boolean> managedServers = new HashMap<>();
    managedServerNames.forEach(managedServerName ->
        managedServers.put(managedServerName, false)
    );

    logger.info("Calling webapp at most {0} times using command: {1}", maxIterations, curlCmd);

    // check the response contains managed server name
    ExecResult result = null;
    for (int i = 0; i < maxIterations; i++) {

      if (managedServers.containsValue(false)) {
        try {
          // sometimes the pod is not ready even the condition check is ready, sleep a little bit
          Thread.sleep(1000);
        } catch (InterruptedException ignore) {
          // ignore
        }

        try {
          result = ExecCommand.exec(curlCmd, true);

          String response = result.stdout().trim();
          logger.info("Response for iteration {0}: exitValue {1}, stdout {2}, stderr {3}",
              i, result.exitValue(), response, result.stderr());
          managedServers.keySet().forEach(key -> {
            if (response.contains(key)) {
              managedServers.put(key, true);
            }
          });
        } catch (Exception e) {
          logger.info("Got exception while running command: {0}", curlCmd);
          logger.info(e.toString());
          if (result != null) {
            logger.info("result.stdout: \n{0}", result.stdout());
            logger.info("result.stderr: \n{0}", result.stderr());
          }
          return false;
        }
      } else {
        return true;
      }
    }

    // after the max iterations, if hit here, then the sample app can not be accessed from at least one server
    // log the sample app accessibility information and return false
    managedServers.forEach((key, value) -> {
      if (value) {
        logger.info("The sample app can be accessed from the server {0}", key);
      } else {
        logger.info("FAILURE: The sample app can not be accessed from the server {0}", key);
      }
    });

    return false;
  }

  /**
   * Call the curl command and check the managed servers can see each other.
   *
   * @param curlCmd curl command to call the clusterview app
   * @param managedServerNames managed server names part of the cluster
   * @param maxIterations max iterations to call the curl command
   * @return true if the managed servers can see each other, false otherwise
   */
  public static boolean verifyClusterMemberCommunication(
      String curlCmd,
      List<String> managedServerNames,
      int maxIterations) {
    LoggingFacade logger = getLogger();
    // first map all server names with false
    HashMap<String, Boolean> managedServers = new HashMap<>();
    managedServerNames.forEach(managedServerName
        -> managedServers.put(managedServerName, false)
    );

    logger.info("Calling clusterview at most {0} times using command: {1}", maxIterations, curlCmd);

    // check the response contains managed server name
    ExecResult result = null;
    for (int i = 0; i < maxIterations; i++) {
      if (managedServers.containsValue(false)) {
        try {
          TimeUnit.MILLISECONDS.sleep(100);
          result = ExecCommand.exec(curlCmd, true);
          String response = result.stdout().trim();
          for (var entry : managedServers.entrySet()) {
            if (response.contains("ServerName:" + entry.getKey())) {
              boolean bound = true;
              for (String managedServerName : managedServerNames) {
                bound = bound && response.contains("Bound:" + managedServerName);
              }
              if (bound) {
                managedServers.put(entry.getKey(), true);
              }
            }
          }
        } catch (IOException | InterruptedException e) {
          logger.info(e.toString());
          return false;
        }
      } else {
        return true;
      }
    }
    // after the max iterations, if hit here, one or more servers cannot see other
    managedServers.forEach((key, value) -> {
      if (value) {
        logger.info("The server {0} can see other cluster members", key);
      } else {
        logger.info("The server {0} is not bound in JNDI server "
            + "or is generating an unexpected curl response", key);
      }
    });

    return false;
  }

  /**
   * Get the next free port between from and to.
   *
   * @param from range starting point
   * @param to range ending point
   * @return the next free port number, if there is no free port between the range, return the ending point
   */
  public static int getNextFreePort(int from, int to) {
    LoggingFacade logger = getLogger();
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
   * Get current date and timestamp in format yyyy-MM-dd-currentimemillis.
   * @return string with date and timestamp
   */
  public static String getDateAndTimeStamp() {
    DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
    Date date = new Date();
    return dateFormat.format(date) + "-" + System.currentTimeMillis();
  }

  /**
   * Call a web app and wait for the response code 200.
   * @param curlCmd curl command to call the web app
   * @param maxIterations max iterations to call the curl command
   * @return true if 200 response code is returned, false otherwise
   */
  public static boolean callWebAppAndWaitTillReady(String curlCmd, int maxIterations)  {
    LoggingFacade logger = getLogger();
    ExecResult result = null;
    String responseCode = "";

    for (int i = 0; i < maxIterations; i++) {
      try {
        result = ExecCommand.exec(curlCmd);
        responseCode = result.stdout().trim();

        if (result.exitValue() != 0 || !responseCode.equals("200")) {
          logger.info("callWebApp did not return 200 response code, got {0}, iteration {1} of {2}",
              responseCode, i, maxIterations);

          try {
            Thread.sleep(1000);
          } catch (InterruptedException ignore) {
            // ignore
          }
        } else if (responseCode.equals("200")) {
          logger.info("callWebApp returned 200 response code, iteration {0}", i);
          return true;
        }
      } catch (Exception e) {
        logger.info("Got exception while running command: {0}", curlCmd);
        logger.info(e.toString());
        if (result != null) {
          logger.info("result.stdout: \n{0}", result.stdout());
          logger.info("result.stderr: \n{0}", result.stderr());
          logger.info("result.exitValue: \n{0}", result.exitValue());
        }
        return false;
      }
    }

    logger.info("FAILURE: callWebApp did not return 200 response code, got {0}", responseCode);
    if (result != null) {
      logger.info("result.stdout: \n{0}", result.stdout());
      logger.info("result.stderr: \n{0}", result.stderr());
      logger.info("result.exitValue: \n{0}", result.exitValue());
    }

    return false;
  }

  /**
   * Check if the given port number is free.
   *
   * @param port port number to check
   * @return true if the port is free, false otherwise
   */
  private static boolean isLocalPortFree(int port) {
    LoggingFacade logger = getLogger();
    Socket socket = null;
    try {
      socket = new Socket(K8S_NODEPORT_HOST, port);
      return false;
    } catch (IOException ignored) {
      return true;
    } finally {
      if (socket != null) {
        try {
          socket.close();
        } catch (IOException ex) {
          logger.severe("can not close Socket {0}", ex.getMessage());
        }
      }
    }
  }
}

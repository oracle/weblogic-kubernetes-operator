// Copyright (c) 2021, 2024, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes.utils;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.Callable;

import oracle.weblogic.kubernetes.logging.LoggingFacade;
import org.awaitility.core.ConditionFactory;

import static oracle.weblogic.kubernetes.TestConstants.ADMIN_PASSWORD_DEFAULT;
import static oracle.weblogic.kubernetes.TestConstants.ADMIN_USERNAME_DEFAULT;
import static oracle.weblogic.kubernetes.TestConstants.KUBERNETES_CLI;
import static oracle.weblogic.kubernetes.TestConstants.OPERATOR_RELEASE_NAME;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.appAccessibleInPod;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.checkHelmReleaseRevision;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.createTestWebAppWarFile;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.testUntil;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.withQuickRetryPolicy;
import static oracle.weblogic.kubernetes.utils.ExecCommand.exec;
import static oracle.weblogic.kubernetes.utils.ThreadSafeLogger.getLogger;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ApplicationUtils {
  /**
   * Check the application running in WebLogic server using host information in the header.
   * @param url url to access the application
   * @param hostHeader host information to be passed as http header
   * @param args arguments to determine whether to check the console accessible or not
   * @return true if curl command returns HTTP code 200 otherwise false
   */
  public static boolean checkAppUsingHostHeader(String url, String hostHeader, Boolean... args) {
    boolean checlReadyAppAccessible = args.length == 0;
    LoggingFacade logger = getLogger();
    StringBuffer curlString = new StringBuffer("status=$(curl --user weblogic:welcome1 ");
    StringBuffer headerString;
    if (hostHeader != null) {
      headerString = new StringBuffer("-H 'host: ");
      headerString.append(hostHeader)
          .append("' ");
    } else {
      headerString = new StringBuffer();
    }
    curlString.append(" -g -sk --noproxy '*' ")
        .append(" --silent --show-error ")
        .append(headerString.toString())
        .append(url)
        .append(" -o /dev/null")
        .append(" -w %{http_code});")
        .append("echo ${status}");
    logger.info("checkAppUsingHostInfo: curl command {0}", new String(curlString));

    if (checlReadyAppAccessible) {
      testUntil(
          assertDoesNotThrow(() -> () -> exec(new String(curlString), true).stdout().contains("200")),
          logger,
          "application to be ready {0}",
          url);
    } else {
      try {
        return exec(new String(curlString), true).stdout().contains("200");
      } catch (Exception ex) {
        logger.info("Failed to exec command {0}. Caught exception {1}", curlString.toString(), ex.getMessage());
        return false;
      }
    }
    return true;
  }

  /**
   * Check if the the application is accessible inside the WebLogic server pod.
   * @param conditionFactory condition factory
   * @param namespace namespace of the domain
   * @param podName name of the pod
   * @param internalPort internal port of the managed server running in the pod
   * @param appPath path to access the application
   * @param expectedStr expected response from the app
   */
  public static void checkAppIsRunning(
      ConditionFactory conditionFactory,
      String namespace,
      String podName,
      String internalPort,
      String appPath,
      String expectedStr
  ) {

    // check if the application is accessible inside of a server pod
    testUntil(
        conditionFactory,
        () -> appAccessibleInPod(
          namespace,
          podName,
          internalPort,
          appPath,
          expectedStr),
        getLogger(),
        "application {0} is running on pod {1} in namespace {2}",
        appPath,
        podName,
        namespace);
  }

  /**
   * Check if the the application is active for a given weblogic target.
   * @param host hostname to construct the REST url
   * @param port the port construct the REST url
   * @param headers extra header info to pass to the REST url
   * @param application name of the application
   * @param target the weblogic target for the application
   * @param username username to log into the system
   * @param password password for the username
   */
  public static boolean checkAppIsActive(
      String host,
      int    port,
      String headers,
      String application,
      String target,
      String username,
      String password
  ) {
    return checkAppIsActive(host + ":" + port, headers, application, target, username, password);
  }

  /**
   * Check if the the application is active for a given weblogic target.
   * @param hostAndPort String containing host:port or routename for OKD cluster
   * @param headers extra header info to pass to the REST url
   * @param application name of the application
   * @param target the weblogic target for the application
   * @param username username to log into the system
   * @param password password for the username
   */
  public static boolean checkAppIsActive(
      String hostAndPort,
      String headers,
      String application,
      String target,
      String username,
      String password
  ) {

    LoggingFacade logger = getLogger();
    String curlString = String.format("curl -g -v --show-error --noproxy '*' "
        + "--user " + username + ":" + password + " " + headers
        + " -H X-Requested-By:MyClient -H Accept:application/json "
        + "-H Content-Type:application/json "
        + " -d \"{ target: '" + target + "' }\" "
        + " -X POST "
        + "http://%s/management/weblogic/latest/domainRuntime/deploymentManager/appDeploymentRuntimes/"
        + application + "/getState", hostAndPort);

    logger.info("curl command {0}", curlString);
    testUntil(
        assertDoesNotThrow(() -> () -> exec(curlString, true).stdout().contains("STATE_ACTIVE")),
        logger,
        "Application {0} to be active",
        application);
    return true;
  }

  /**
   * Deploy application and access the application once to make sure the app is accessible.
   * @param domainNamespace namespace where domain exists
   * @param domainUid the domain to which the cluster belongs
   * @param clusterName the WebLogic cluster name that the app deploys to
   * @param adminServerName the WebLogic admin server name that the app deploys to
   * @param adminServerPodName WebLogic admin pod prefix
   * @param managedServerPodNamePrefix WebLogic managed server pod prefix
   * @param replicaCount replica count of the cluster
   * @param adminInternalPort admin server's internal port
   * @param msInternalPort managed server's internal port
   */
  public static void deployAndAccessApplication(String domainNamespace,
                                                String domainUid,
                                                String clusterName,
                                                String adminServerName,
                                                String adminServerPodName,
                                                String managedServerPodNamePrefix,
                                                int replicaCount,
                                                String adminInternalPort,
                                                String msInternalPort) {
    final LoggingFacade logger = getLogger();

    String testWebAppWarLoc = createTestWebAppWarFile(domainNamespace);
    Path archivePath = Paths.get(testWebAppWarLoc);
    logger.info("Deploying application {0} to domain {1} cluster target cluster-1 in namespace {2}",
        archivePath, domainUid, domainNamespace);
    logger.info("Deploying webapp {0} to admin server and cluster", archivePath);
    DeployUtil.deployUsingWlst(adminServerPodName,
        adminInternalPort,
        ADMIN_USERNAME_DEFAULT,
        ADMIN_PASSWORD_DEFAULT,
        clusterName + "," + adminServerName,
        archivePath,
        domainNamespace);

    // check if the application is accessible inside of a server pod using quick retry policy
    logger.info("Check and wait for the application to become ready");
    for (int i = 1; i <= replicaCount; i++) {
      checkAppIsRunning(withQuickRetryPolicy, domainNamespace, managedServerPodNamePrefix + i,
          msInternalPort, "testwebapp/index.jsp", managedServerPodNamePrefix + i);
    }
    checkAppIsRunning(withQuickRetryPolicy, domainNamespace, adminServerPodName,
        adminInternalPort, "testwebapp/index.jsp", adminServerPodName);
  }

  /**
   * Check application availability while the operator upgrade is happening and once the ugprade is complete
   * by accessing the application inside the managed server pods.
   * @param domainNamespace namespace where domain exists
   * @param operatorNamespace namespace where operator exists
   * @param appAvailability application's availability
   * @param adminPodName WebLogic admin server pod name
   * @param managedServerPodNamePrefix WebLogic managed server pod prefix
   * @param replicaCount replica count of the cluster
   * @param adminInternalPort admin server's internal port
   * @param msInternalPort managed server's internal port
   * @param appPath application path
   */
  public static void collectAppAvailability(String domainNamespace,
                                            String operatorNamespace,
                                            List<Integer> appAvailability,
                                            String adminPodName,
                                            String managedServerPodNamePrefix,
                                            int replicaCount,
                                            String adminInternalPort,
                                            String msInternalPort,
                                            String appPath) {
    final LoggingFacade logger = getLogger();

    // Access the pod periodically to check application's availability during
    // upgrade and after upgrade is complete.
    // appAccessedAfterUpgrade is used to access the app once after upgrade is complete
    boolean appAccessedAfterUpgrade = false;
    while (!appAccessedAfterUpgrade) {
      boolean isUpgradeComplete = checkHelmReleaseRevision(OPERATOR_RELEASE_NAME, operatorNamespace, "2");
      // upgrade is not complete or app is not accessed after upgrade
      if (!isUpgradeComplete || !appAccessedAfterUpgrade) {
        // Check application accessibility on admin server
        if (appAccessibleInPod(domainNamespace,
            adminPodName,
            adminInternalPort,
            appPath,
            adminPodName)) {
          appAvailability.add(1);
          logger.info("application accessible in admin pod " + adminPodName);
        } else {
          appAvailability.add(0);
          logger.info("application not accessible in admin pod " + adminPodName);
        }

        // Check application accessibility on managed servers
        for (int i = 1; i <= replicaCount; i++) {
          if (appAccessibleInPod(domainNamespace,
              managedServerPodNamePrefix + i,
              msInternalPort,
              appPath,
              managedServerPodNamePrefix + i)) {
            appAvailability.add(1);
            logger.info("application is accessible in pod " + managedServerPodNamePrefix + i);
          } else {
            appAvailability.add(0);
            logger.info("application is not accessible in pod " + managedServerPodNamePrefix + i);
          }
        }
      }
      if (isUpgradeComplete) {
        logger.info("Upgrade is complete and app is accessed after upgrade");
        appAccessedAfterUpgrade = true;
      }
    }
  }

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
   * Call a web app and wait for the response code 200.
   * @param curlCmd curl command to call the web app
   * @return true if 200 response code is returned, false otherwise
   */
  public static Callable<Boolean> callWebAppAndWaitTillReady(String curlCmd)  {
    LoggingFacade logger = getLogger();
    String httpStatusCode = "200";

    return () -> {
      final ExecResult result = ExecCommand.exec(curlCmd);
      final String responseCode = result.stdout().trim();

      if (result != null) {
        logger.info("result.stdout: \n{0}", result.stdout());
        logger.info("result.stderr: \n{0}", result.stderr());
        logger.info("result.exitValue: \n{0}", result.exitValue());
      }

      if (result.exitValue() != 0 || !responseCode.equals(httpStatusCode)) {
        logger.info("callWebApp did not return {0} response code, got {1}", httpStatusCode, responseCode);
        return false;
      }

      return true;
    };
  }

  /**
   * Call a web app and wait for the response code 200.
   * @param curlCmd curl command to call the web app
   * @param maxIterations max iterations to call the curl command
   * @return true if 200 response code is returned, false otherwise
   */
  public static boolean callWebAppAndWaitTillReady(String curlCmd, int maxIterations)  {
    return callWebAppAndWaitTillReturnedCode(curlCmd, "200", maxIterations);
  }

  /**
   * Call a web app and wait for the specified HTTP status code.
   * @param curlCmd curl command to call the web app
   * @param maxIterations max iterations to call the curl command
   * @param httpStatusCode HTTP status code
   * @return true if specified HTTP status code is returned, false otherwise
   */
  public static boolean callWebAppAndWaitTillReturnedCode(String curlCmd, String httpStatusCode, int maxIterations)  {
    LoggingFacade logger = getLogger();
    ExecResult result = null;
    String responseCode = "";

    for (int i = 0; i < maxIterations; i++) {
      try {
        result = ExecCommand.exec(curlCmd);
        responseCode = result.stdout().trim();

        if (result.exitValue() != 0 || !responseCode.equals(httpStatusCode)) {
          logger.info("callWebApp did not return {0} response code, got {1}, iteration {2} of {3}",
              httpStatusCode, responseCode, i, maxIterations);

          try {
            Thread.sleep(1000);
          } catch (InterruptedException ignore) {
            // ignore
          }
        } else if (responseCode.equals(httpStatusCode)) {
          logger.info("callWebApp returned {0} response code, iteration {1}", httpStatusCode, i);
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

    logger.info("FAILURE: callWebApp did not return {0} response code, got {1}", httpStatusCode, responseCode);
    if (result != null) {
      logger.info("result.stdout: \n{0}", result.stdout());
      logger.info("result.stderr: \n{0}", result.stderr());
      logger.info("result.exitValue: \n{0}", result.exitValue());
    }

    return false;
  }

  /**
   * verify admin console accessible.
   *
   * @param domainNamespace namespace of the domain
   * @param hostName host name to access WLS console
   * @param port port number to access WLS console
   * @param secureMode true to access WLS console via ssh channel, false otherwise
   * @param args arguments to determine whether to check the console accessible or not
   */
  public static void verifyAdminConsoleAccessible(String domainNamespace,
                                                  String hostName,
                                                  String port,
                                                  boolean secureMode,
                                                  Boolean... args) {
    boolean checlReadyAppAccessible = args.length == 0;
    LoggingFacade logger = getLogger();
    String httpKey = "http://";
    if (secureMode) {
      // Since WLS servers use self-signed certificates, it's ok to use --insecure option
      // to ignore SSL/TLS certificate errors:
      // curl: (60) SSL certificate problem: Invalid certificate chain
      // and explicitly allows curl to perform “insecure” SSL connections and transfers
      httpKey = " --insecure https://";
    }
    if (hostName.contains(":")) {
      hostName = "[" + hostName + "]";
    }
    String readyAppUrl = httpKey + hostName + ":" + port + "/weblogic/ready";

    boolean checlReadyApp = assertDoesNotThrow(() ->
        checkAppUsingHostHeader(readyAppUrl, domainNamespace + ".org", args));
    if (checlReadyAppAccessible) {
      assertTrue(checlReadyApp, "Failed to access ready app");
      logger.info("ready app is accessible");
    } else {
      assertFalse(checlReadyApp, "Shouldn't be able to access WebLogic console");
      logger.info("WebLogic console is not accessible");
    }
  }

  /**
   * Verify admin server is accessible through REST interface.
   *
   * @param host host name to connect to
   * @param port the port configured to access admin server
   * @param secure is https
   * @param hostHeader header to pass in curl
   * @return true if REST interface is accessible
   * @throws IOException when connection to admin server fails
   */
  public static boolean verifyAdminServerRESTAccess(String host, int port, boolean secure, String hostHeader)
      throws IOException {
    getLogger().info("Check REST interface availability");
    StringBuffer curlCmd = new StringBuffer("status=$(curl -vkg --noproxy '*'");
    if (host.contains(":")) {
      host = "[" + host + "]";
    }
    curlCmd.append(" -H 'host: ")
        .append(hostHeader)
        .append("' ")
        .append("--user ")
        .append(ADMIN_USERNAME_DEFAULT)
        .append(":")
        .append(ADMIN_PASSWORD_DEFAULT)
        .append(" ")
        .append(secure ? " https://" : "http://")
        .append(host)
        .append(":")
        .append(port)
        .append("/management/tenant-monitoring/servers/ --show-error -w %{http_code}); ")
        .append("echo ${status}");
    getLogger().info("checkRestConsole : curl command {0}", new String(curlCmd));
    try {
      ExecResult result = ExecCommand.exec(new String(curlCmd), true);
      String response = result.stdout().trim();
      getLogger().info("exitCode: {0}, \nstdout: {1}, \nstderr: {2}",
          result.exitValue(), response, result.stderr());
      return response.contains("RUNNING");
    } catch (IOException | InterruptedException ex) {
      getLogger().info("Exception in checkRestConsole {0}", ex);
      return false;
    }
  }
  
  /**
   * Verify admin server is accessible through REST interface inside admin server pod.
   *
   * @param adminServerPodName admin server pod name
   * @param adminPort the port configured to access admin server
   * @param namespace namespace in which admin server pod is running
   * @param userName admin server user name
   * @param password admin server admin password
   * @return true if REST interface is accessible
   * @throws IOException when connection to admin server fails
   */
  public static boolean verifyAdminServerRESTAccessInAdminPod(String adminServerPodName, String adminPort,
      String namespace, String userName, String password)
      throws IOException {
    LoggingFacade logger = getLogger();
    logger.info("Checking REST Console");
    StringBuffer curlCmd = new StringBuffer(KUBERNETES_CLI + " exec -n "
        + namespace + " " + adminServerPodName)
        .append(" -- /bin/bash -c \"")
        .append("curl -g --user ")
        .append(userName)
        .append(":")
        .append(password)
        .append(" http://" + adminServerPodName + ":" + adminPort)
        .append("/management/tenant-monitoring/servers/ --silent --show-error -o /dev/null -w %{http_code}\"");
    logger.info("checkRestConsole : curl command {0}", new String(curlCmd));
    try {
      ExecResult result = ExecCommand.exec(new String(curlCmd), true);
      String response = result.stdout().trim();
      logger.info("exitCode: {0}, \nstdout: {1}, \nstderr: {2}",
          result.exitValue(), response, result.stderr());
      return response.contains("200");
    } catch (IOException | InterruptedException ex) {
      logger.info("Exception in checkRestConsole {0}", ex);
      return false;
    }
  }
  
}

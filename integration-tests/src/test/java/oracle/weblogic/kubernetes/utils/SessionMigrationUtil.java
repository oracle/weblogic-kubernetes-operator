// Copyright (c) 2022, 2025, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes.utils;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import oracle.weblogic.kubernetes.logging.LoggingFacade;

import static oracle.weblogic.kubernetes.actions.TestActions.execCommand;
import static oracle.weblogic.kubernetes.actions.TestActions.shutdownManagedServerUsingServerStartPolicy;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.testUntil;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.withStandardRetryPolicy;
import static oracle.weblogic.kubernetes.utils.PodUtils.checkPodDoesNotExist;
import static oracle.weblogic.kubernetes.utils.ThreadSafeLogger.getLogger;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Utility class for session migration tests.
 */
public class SessionMigrationUtil {

  private static final String SESSMIGR_MODEL_FILE = "model.sessmigr.yaml";

  /**
   * Get original model file for session migration tests.
   *
   * @return - the model file name
   */
  public static String getOrigModelFile() {
    return SESSMIGR_MODEL_FILE;
  }

  /**
   * Patch domain to shutdown a WebLogic server by changing the value of
   * server's serverStartPolicy property to Never.
   *
   * @param domainUid unique domain identifier
   * @param domainNamespace namespace in which the domain will be created
   * @param serverName name of the WebLogic server to shutdown
   */
  public static void shutdownServerAndVerify(String domainUid,
                                             String domainNamespace,
                                             String serverName) {
    final String podName = domainUid + "-" + serverName;
    LoggingFacade logger = getLogger();

    // shutdown a server by changing the it's serverStartPolicy property.
    logger.info("Shutdown the server {0}", serverName);
    boolean serverStopped = assertDoesNotThrow(() ->
        shutdownManagedServerUsingServerStartPolicy(domainUid, domainNamespace, serverName));
    assertTrue(serverStopped, String.format("Failed to shutdown server %s ", serverName));

    // check that the managed server pod shutdown successfully
    logger.info("Check that managed server pod {0} stopped in namespace {1}", podName, domainNamespace);
    checkPodDoesNotExist(podName, domainUid, domainNamespace);
  }

  /**
   * An util method referred by the test method testSessionMigration. It sends a HTTP request
   * to set or get http session state (count number) and return the primary server,
   * the secondary server, session create time and session state(count number).
   *
   * @param domainNamespace namespace in which the domain will be created
   * @param adminServerPodName admin server pod name
   * @param hostName host name to access the web app
   * @param port port number to access the web app
   * @param webServiceUrl fully qualified URL to the server on which the web app is running
   * @param headerOption option to save or use HTTP session info
   *
   * @return map that contains primary and secondary server names, session create time and session state
   */
  public static Map<String, String> getServerAndSessionInfoAndVerify(String domainNamespace,
                                                                     String adminServerPodName,
                                                                     String hostName,
                                                                     int port,
                                                                     String webServiceUrl,
                                                                     String headerOption) {
    final String primaryServerAttr = "primary";
    final String secondaryServerAttr = "secondary";
    final String sessionCreateTimeAttr = "sessioncreatetime";
    final String countAttr = "count";
    LoggingFacade logger = getLogger();

    // send a HTTP request to set http session state(count number) and save HTTP session cookie info
    // or get http session state(count number using saved HTTP session cookie info
    logger.info("Process HTTP request with web service URL {0}", webServiceUrl);
    Map<String, String> httpAttrInfo =
        processHttpRequest(domainNamespace, adminServerPodName, hostName, port, webServiceUrl, headerOption);

    // get HTTP response data
    String primaryServerName = httpAttrInfo.get(primaryServerAttr);
    String secondaryServerName = httpAttrInfo.get(secondaryServerAttr);
    String sessionCreateTime = httpAttrInfo.get(sessionCreateTimeAttr);
    String countStr = httpAttrInfo.get(countAttr);

    // verify that the HTTP response data are not null
    assertAll("Check that WebLogic server and session vars is not null or empty",
        () -> assertNotNull(primaryServerName,"Primary server name shouldn’t be null"),
        () -> assertNotNull(secondaryServerName,"Second server name shouldn’t be null"),
        () -> assertNotNull(sessionCreateTime,"Session create time shouldn’t be null"),
        () -> assertNotNull(countStr,"Session state shouldn’t be null")
    );

    // map to save server and session info
    Map<String, String> httpDataInfo = new HashMap<>();
    httpDataInfo.put(primaryServerAttr, primaryServerName);
    httpDataInfo.put(secondaryServerAttr, secondaryServerName);
    httpDataInfo.put(sessionCreateTimeAttr, sessionCreateTime);
    httpDataInfo.put(countAttr, countStr);

    return httpDataInfo;
  }

  private static Map<String, String> processHttpRequest(String domainNamespace,
                                                        String adminServerPodName,
                                                        String hostName,
                                                        int port,
                                                        String curlUrlPath,
                                                        String headerOption) {
    String[] httpAttrArray = {"sessioncreatetime", "sessionid", "primary", "secondary", "count"};
    Map<String, String> httpAttrInfo = new HashMap<String, String>();
    LoggingFacade logger = getLogger();

    // build curl command
    String curlCmd = buildCurlCommand(curlUrlPath, headerOption, hostName, port);
    logger.info("Command to set HTTP request and get HTTP response {0} ", curlCmd);

    // check if primary server is ready
    testUntil(withStandardRetryPolicy,
        () -> checkSessionReplicatorServerReady(domainNamespace, adminServerPodName, "primary", curlCmd),
        logger, "check if primary server is ready in namespace {0}", domainNamespace);

    logger.info("Sending request from inside admin server pod to cluster : {0}", curlCmd);
    // set HTTP request and get HTTP response
    testUntil(withStandardRetryPolicy, () -> {
      ExecResult execResult = execCommand(domainNamespace, adminServerPodName, 
          "weblogic-server", true, "/bin/sh", "-c", curlCmd);
      if (execResult.exitValue() == 0 && execResult.stderr() != null) {
        logger.info("\n HTTP response is \n" + execResult.stdout());
        if (execResult.stdout() == null || execResult.stdout().isEmpty()) {
          logger.info("Null or empty output");
          return false;
        }
        for (String httpAttrKey : httpAttrArray) {
          String httpAttrValue = getHttpResponseAttribute(execResult.stdout(), httpAttrKey);
          httpAttrInfo.put(httpAttrKey, httpAttrValue);
        }
        return true;
      } else {
        logger.info("Didn't get correct exit code or there is an error");
        logger.info("Exit code \n" + execResult.exitValue());
        logger.info("Stdout \n" + execResult.stdout());
        logger.info("Stderr \n" + execResult.stderr());
        return false;
      }
    }, logger, "curl command to return exit code 0, no error, and cookie output");
    return httpAttrInfo;
  }

  private static boolean checkSessionReplicatorServerReady(String domainNamespace,
                                                 String adminServerPodName,
                                                 String replicator,
                                                 String curlCmd) {
    boolean primaryServerReady = false;
    LoggingFacade logger = getLogger();

    logger.info("Sending request from inside admin server pod to cluster : {0}", curlCmd);
    // set HTTP request and get HTTP response
    ExecResult execResult = assertDoesNotThrow(
        () -> execCommand(domainNamespace, adminServerPodName,
        "weblogic-server", true, "/bin/sh", "-c", curlCmd));

    if (execResult.exitValue() == 0 && execResult.stdout() != null && !execResult.stdout().isEmpty()) {
      String primaryServerName = getHttpResponseAttribute(execResult.stdout(), replicator);

      if (primaryServerName != null && !primaryServerName.isEmpty()) {
        logger.info("\n {0} server is ready: \n {1}", replicator, execResult.stdout());
        primaryServerReady = true;
      }
    }

    return primaryServerReady;
  }

  private static String buildCurlCommand(String curlUrlPath,
                                         String headerOption,
                                         String hostName,
                                         int port) {
    final String httpHeaderFile = "/tmp/header";
    LoggingFacade logger = getLogger();

    // In OKE_CLUSTER env, the test uses LBer ext IP addr only.
    String hostAndPort = (port == 0) ? hostName : hostName + ":" + port;

    // --connect-timeout - Maximum time in seconds that you allow curl's connection to take
    // --max-time - Maximum time in seconds that you allow the whole operation to take
    int waittime = 10;
    String curlCommand =  new StringBuilder()
        .append("curl -g --show-error ")
        .append(" --noproxy '*'")
        .append(" --connect-timeout ").append(waittime).append(" --max-time ").append(waittime)
        .append(" http://")
        .append(hostAndPort)
        .append("/")
        .append(curlUrlPath)
        .append(headerOption)
        .append(httpHeaderFile).toString();

    logger.info("Build a curl command: {0}", curlCommand);

    return curlCommand;
  }

  private static String getHttpResponseAttribute(String httpResponseString, String attribute) {
    // map to save HTTP response data
    Map<String, String> httpAttrMap = new HashMap<String, String>();
    httpAttrMap.put("sessioncreatetime", "(.*)sessioncreatetime>(.*)</sessioncreatetime(.*)");
    httpAttrMap.put("sessionid", "(.*)sessionid>(.*)</sessionid(.*)");
    httpAttrMap.put("primary", "(.*)primary>(.*)</primary(.*)");
    httpAttrMap.put("secondary", "(.*)secondary>(.*)</secondary(.*)");
    httpAttrMap.put("count", "(.*)countattribute>(.*)</countattribute(.*)");

    // retrieve the search pattern that matches the given HTTP data attribute
    String attrPatn = httpAttrMap.get(attribute);
    assertNotNull(attrPatn,"HTTP Attribute key shouldn’t be null");

    // search the value of given HTTP data attribute
    Pattern pattern = Pattern.compile(attrPatn);
    Matcher matcher = pattern.matcher(httpResponseString);
    String httpAttribute = null;

    if (matcher.find()) {
      httpAttribute = matcher.group(2);
    }

    return httpAttribute;
  }
}

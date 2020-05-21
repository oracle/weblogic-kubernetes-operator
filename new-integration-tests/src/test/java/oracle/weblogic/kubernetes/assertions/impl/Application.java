// Copyright (c) 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes.assertions.impl;

import java.io.IOException;

import io.kubernetes.client.openapi.ApiException;
import oracle.weblogic.kubernetes.actions.impl.primitive.Command;
import oracle.weblogic.kubernetes.actions.impl.primitive.CommandParams;
import oracle.weblogic.kubernetes.logging.LoggingFacade;
import oracle.weblogic.kubernetes.logging.LoggingFactory;
import oracle.weblogic.kubernetes.utils.ExecResult;

import static oracle.weblogic.kubernetes.TestConstants.ADMIN_PASSWORD_DEFAULT;
import static oracle.weblogic.kubernetes.TestConstants.ADMIN_USERNAME_DEFAULT;
import static oracle.weblogic.kubernetes.TestConstants.WLS_DEFAULT_CHANNEL_NAME;
import static oracle.weblogic.kubernetes.actions.TestActions.execCommand;
import static oracle.weblogic.kubernetes.actions.TestActions.getServiceNodePort;

/**
 * Assertions for applications that are deployed in a domain custom resource.
 *
 */

public class Application {
  private static final LoggingFacade logger = LoggingFactory.getLogger(Application.class);

  /**
   * Check if an application is accessible inside a WebLogic server pod.
   * 
   * @param namespace Kubernetes namespace where the WebLogic server pod is running
   * @param podName name of the WebLogic server pod
   * @param port internal port of the managed server running in the pod
   * @param appPath path to access the application
   * @param expectedResponse expected response from the app
   * @return true if the command succeeds 
   */
  public static boolean appAccessibleInPodKubectl(
      String namespace, 
      String podName,
      String port,
      String appPath, 
      String expectedResponse
  ) {

    // calling "kubectl exec" command to access the app inside a pod
    String cmd = String.format(
         "kubectl -n %s exec -it %s -- /bin/bash -c 'curl http://%s:%s/%s'",
         namespace,
         podName,
         podName,
         port,
         appPath);

    CommandParams params = Command
        .defaultCommandParams()
        .command(cmd)
        .saveResults(true)
        .redirect(false)
        .verbose(false);
    return Command.withParams(params).executeAndVerify(expectedResponse);
  }

  /**
   * Check if an application is accessible inside a WebLogic server pod using
   * Kubernetes Java client.
   * 
   * @param namespace Kubernetes namespace where the WebLogic server pod is running
   * @param podName name of the WebLogic server pod
   * @param port internal port of the managed server running in the pod
   * @param appPath path to access the application
   * @param expectedResponse expected response from the app
   * @return true if the command succeeds 
   */
  public static boolean appAccessibleInPod(
      String namespace, 
      String podName,
      String port,
      String appPath, 
      String expectedResponse
  ) {

    // access the application in the given pod
    String[] cmd = new String[] {
        "/usr/bin/curl",
        String.format("http://%s:%s/%s",
            podName,
            port,
            appPath)};

    try {
      ExecResult execResult = execCommand(
          namespace,
          podName, 
          "weblogic-server", // container name
          false, // redirectOutput
          cmd);
      if (execResult.exitValue() == 0
          && execResult.stdout() != null 
          && execResult.stdout().contains(expectedResponse)) {
        logger.info(
            String.format("App is accessible inside pod %s in namespace %s",
                podName,
                namespace));
        return true;
      } else {
        logger.warning(
            String.format("Failed to access the app inside pod %s in namespace %s",
                podName,
                namespace));
        return false;
      }
    } catch (ApiException | IOException | InterruptedException e) {
      logger.warning(
          String.format("Failed to access the app inside pod %s in namespace %s",
              podName,
              namespace),
          e);
      return false;
    } catch (IllegalArgumentException iae) {
      logger.warning(String.format("Failed to find pod %s to check the app", podName));
      return false;
    }
  } 
  
  /**
   * Check if the given WebLogic credentials are valid by using the credentials to 
   * invoke a RESTful Management Services command.
   *
   * @param host hostname of the admin server pod
   * @param podName name of the admin server pod
   * @param namespace name of the namespace that the pod is running in
   * @param username WebLogic admin username
   * @param password WebLogic admin password
   * @return true if the RESTful Management Services command succeeded
   **/
  public static boolean credentialsValid(
      String host,
      String podName,
      String namespace,
      String username,
      String password) {
    CommandParams params = createCommandParams(host, podName, namespace, username, password);
    return Command.withParams(params).executeAndVerify("200");
  }
  
  /**
   * Check if the given WebLogic credentials are not valid by using the credentials to 
   * invoke a RESTful Management Services command.
   *
   * @param host hostname of the admin server pod
   * @param podName name of the admin server pod
   * @param namespace name of the namespace that the pod is running in
   * @param username WebLogic admin username
   * @param password WebLogic admin password
   * @return true if the RESTful Management Services command failed with exitCode 401
   **/
  public static boolean credentialsNotValid(
      String host,
      String podName,
      String namespace,
      String username,
      String password) {
    CommandParams params = createCommandParams(host, podName, namespace, username, password);
    return Command.withParams(params).executeAndVerify("401");
  }
 
  private static CommandParams createCommandParams(
      String host,
      String podName,
      String namespace,
      String username,
      String password) {
    int adminServiceNodePort = getServiceNodePort(namespace, podName + "-external", WLS_DEFAULT_CHANNEL_NAME);

    if (username == null) {
      username = ADMIN_USERNAME_DEFAULT;
    }
    if (password == null) {
      password = ADMIN_PASSWORD_DEFAULT;
    }

    // create a RESTful management services command that connects to admin server using given credentials to get
    // information about a managed server
    StringBuffer cmdString = new StringBuffer()
        .append("status=$(curl --user " + username + ":" + password)
        .append(" http://" + host + ":" + adminServiceNodePort)
        .append("/management/tenant-monitoring/servers/managed-server1")
        .append(" --silent --show-error ")
        .append(" -o /dev/null")
        .append(" -w %{http_code});")
        .append("echo ${status}");

    return Command
            .defaultCommandParams()
            .command(cmdString.toString())
            .saveResults(true)
            .redirect(true)
            .verbose(true);
  }
}

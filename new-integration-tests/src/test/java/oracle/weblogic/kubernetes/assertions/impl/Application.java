// Copyright (c) 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes.assertions.impl;

import java.io.IOException;

import io.kubernetes.client.openapi.ApiException;
import oracle.weblogic.kubernetes.utils.ExecResult;

import static oracle.weblogic.kubernetes.actions.TestActions.execCommand;
import static oracle.weblogic.kubernetes.utils.ThreadSafeLogger.getLogger;

/**
 * Assertions for applications that are deployed in a domain custom resource.
 *
 */

public class Application {

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
        getLogger().info(
            String.format("App is accessible inside pod %s in namespace %s",
                podName,
                namespace));
        return true;
      } else {
        getLogger().warning(
            String.format("Failed to access the app inside pod %s in namespace %s",
                podName,
                namespace));
        return false;
      }
    } catch (ApiException | IOException | InterruptedException e) {
      getLogger().warning(
          String.format("Failed to access the app inside pod %s in namespace %s",
              podName,
              namespace),
          e);
      return false;
    } catch (IllegalArgumentException iae) {
      getLogger().warning(String.format("Failed to find pod %s to check the app", podName));
      return false;
    }
  } 

}

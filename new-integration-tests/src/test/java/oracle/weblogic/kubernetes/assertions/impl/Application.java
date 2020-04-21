// Copyright (c) 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes.assertions.impl;

import oracle.weblogic.kubernetes.actions.impl.primitive.Command;
import oracle.weblogic.kubernetes.actions.impl.primitive.CommandParams;
import oracle.weblogic.kubernetes.logging.LoggingFacade;
import oracle.weblogic.kubernetes.logging.LoggingFactory;

/**
 * Assertions for applications in a domain.
 *
 */

public class Application {
  private static final LoggingFacade logger = LoggingFactory.getLogger(Application.class);

  /**
   * Check if an application is accessible inside a WebLogic server pod.
   * @param ns Kubernetes namespace where the WebLogic servers are running
   * @param port internal port of the managed servers
   * @param appPath the path to access the application
   * @return true if the command succeeds 
   */
  public static boolean appAccessibleInPod(
      String ns, 
      String port, 
      String appPath,
      String expectedStr) {

    return verifyAppInPod(ns, port, appPath).contains(expectedStr);
  }
  
  /**
   * Check if an application is accessible externally.
   * @param ns Kubernetes namespace where the WebLogic servers are running
   * @param port external port of the managed servers
   * @param appPath the path to access the application
   * @return true if the command succeeds 
   */
  public static boolean appAccessibleExternally(
      String ns, 
      String port, 
      String appPath,
      String expectedStr) {

    return expectedStr.contentEquals(verifyAppExternally(ns, port, appPath));
  }
  
  private static String verifyAppInPod(String domainNS, String port, String appPath) {
    String appStr = "";
    // get managed server pod name
    String cmd = String.format(
        "kubectl get pod -n %s -o=jsonpath='{.items[1].metadata.name}' | grep managed-server1",
        domainNS);

    String msPodName = exec(cmd, true);
    logger.info("msPodName = " + msPodName);
    

    // access the application deployed in managed-server1
    cmd = String.format(
         "kubectl -n %s exec -it %s -- /bin/bash -c 'curl http://%s:%s/%s'",
         domainNS,
         msPodName,
         msPodName,
         port,
         appPath);

    appStr = exec(cmd, true);
    logger.info("appStr =" + appStr);
    return appStr;
  }
  
  private static String verifyAppExternally(String domainNS, String port, String appPath) {

    // access the application deployed in managed-server1
    String cmd = String.format(
         "curl -H 'host: domain1.org' http://localhost:%s/%s",
         port,
         appPath);
 
    String appStr = exec(cmd, true);
    logger.info("appStr =" + appStr);
    return appStr;
  }
  
  private static String exec(String command, boolean redirectOutput) {
    CommandParams params = Command
        .defaultCommandParams()
        .command(command)
        .saveStdOut(true)
        .redirect(true);
    Command.withParams(params).execute();
    logger.info("Stdout = " + params.stdOut());
    return params.stdOut();
  }
  
}

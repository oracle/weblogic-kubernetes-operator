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
   * @param domainUID unique identifier of the Kubernetes domain custom resource instance
   * @param domainNS Kubernetes namespace where the WebLogic servers are running
   * @param port internal port of the managed servers
   * @param appPath the path to access the application
   * @return true if the command succeeds 
   */
  public static boolean appAccessibleInPod(
      String domainUID, 
      String domainNS,
      String port, 
      String appPath,
      String expectedStr
  ) {
    return verifyAppInPod(domainUID, domainNS, port, appPath).contains(expectedStr);
  }

  private static String verifyAppInPod(
      String domainUID,
      String domainNS,
      String port,
      String appPath
  ) { 
    String msPodName = domainUID + "-managed-server1";

    // access the application deployed on managed-server1
    String cmd = String.format(
         "kubectl -n %s exec -it %s -- /bin/bash -c 'curl http://%s:%s/%s'",
         domainNS,
         msPodName,
         msPodName,
         port,
         appPath);

    return exec(cmd, true);
  }

  private static String exec(String command, boolean redirectOutput) {
    CommandParams params = Command
        .defaultCommandParams()
        .command(command)
        .saveResults(true)
        .redirect(false)
        .debug(false);
    Command.withParams(params).execute();
    return params.stdout();
  }
  
}

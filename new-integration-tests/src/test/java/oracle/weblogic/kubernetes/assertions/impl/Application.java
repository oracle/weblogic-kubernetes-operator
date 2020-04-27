// Copyright (c) 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes.assertions.impl;

import oracle.weblogic.kubernetes.actions.impl.primitive.Command;
import oracle.weblogic.kubernetes.actions.impl.primitive.CommandParams;

/**
 * Assertions for applications that are deployed in a domain custom resource.
 *
 */

public class Application {

  /**
   * Check if an application is accessible inside a WebLogic server pod.
   * @param domainUID identifier of the Kubernetes domain custom resource instance
   * @param domainNS Kubernetes namespace where the WebLogic servers are running
   * @param podName name of the WebLogic server pod
   * @param port internal port of the managed servers
   * @param appPath the path to access the application
   * @return true if the command succeeds 
   */
  public static boolean appAccessibleInPod(
      String domainUID, 
      String domainNS,
      String podName,
      String port, 
      String appPath,
      String expectedStr
  ) {

    // TODO currently calling "kubectl exec" command; will change it to use a Kubernetes
    // action once that action for "exec" command is available.
    // access the application deployed on managed-server1
    String cmd = String.format(
         "kubectl -n %s exec -it %s -- /bin/bash -c 'curl http://%s:%s/%s'",
         domainNS,
         podName,
         podName,
         port,
         appPath);

    CommandParams params = Command
        .defaultCommandParams()
        .command(cmd)
        .saveResults(true)
        .redirect(true)
        .verbose(true);
    return Command.withParams(params).executeAndVerify(expectedStr);
  } 
}

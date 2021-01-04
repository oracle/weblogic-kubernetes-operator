// Copyright (c) 2020, 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes.assertions.impl;

import java.util.Map;
import java.util.concurrent.Callable;

public class Deployment {

  /**
  * Check if deployment is running in a given namespace.
  * @param deploymentName deployment name to check
  * @param label map of labels for deployment
  * @param namespace in which to check for the deployment
  * @return true if found and running otherwise false
  */
  public static Callable<Boolean> isReady(String deploymentName, Map<String, String> label, String namespace) {
    return () -> Kubernetes.isDeploymentReady(deploymentName, label,namespace);
  }
}

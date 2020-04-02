// Copyright (c) 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes.assertions.impl;

import java.util.HashMap;
import java.util.concurrent.Callable;

import io.kubernetes.client.openapi.ApiException;
import oracle.weblogic.kubernetes.extensions.LoggedTest;

public class Operator implements LoggedTest {

  /**
   * Check if the operator pod is running in a given namespace.
   * @param namespace in which to check for the operator pod
   * @return true if found and running otherwise false
   */
  public static Callable<Boolean> isRunning(String namespace) {
    return () -> {
      return Kubernetes.isOperatorPodRunning(namespace);
    };
  }

  /**
   * Checks if the operator external service is created.
   * @param namespace in which to check for the operator external service
   * @return true if service is found otherwise false
   * @throws ApiException when there is error in querying the cluster
   */
  public static boolean isExternalRestServiceCreated(String namespace) throws ApiException {
    HashMap label = new HashMap();
    label.put("weblogic.operatorName", namespace);
    String serviceName = "external-weblogic-operator-svc";
    return Kubernetes.isServiceCreated(serviceName, label, namespace);
  }

}

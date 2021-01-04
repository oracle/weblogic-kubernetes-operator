// Copyright (c) 2020, 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes.assertions.impl;

import java.util.HashMap;
import java.util.concurrent.Callable;

import io.kubernetes.client.openapi.ApiException;


public class Operator {

  /**
   * Check if the operator pod is running in a given namespace.
   * @param namespace in which to check for the operator pod
   * @return true if found and running otherwise false
   */
  public static Callable<Boolean> isReady(String namespace) {
    return () -> {
      return Kubernetes.isOperatorPodReady(namespace);
    };
  }

  /**
   * Checks if the operator external service exists.
   * @param namespace in which to check for the operator external service
   * @return true if service is found otherwise false
   * @throws ApiException when there is error in querying the cluster
   */
  public static boolean doesExternalRestServiceExists(String namespace) throws ApiException {
    HashMap label = new HashMap();
    label.put("weblogic.operatorName", namespace);
    String serviceName = "external-weblogic-operator-svc";
    return Kubernetes.doesServiceExist(serviceName, label, namespace);
  }

}

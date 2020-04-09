// Copyright (c) 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes.actions.impl;

import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.models.V1Service;
import oracle.weblogic.kubernetes.actions.impl.primitive.Kubernetes;

public class Service {

  /**
   * Create a Kubernetes Service.
   *
   * @param service V1Service object containing Kubernetes secret configuration data
   * @return true if successful
   * @throws ApiException if Kubernetes client API call fails
   */
  public static boolean create(V1Service service) throws ApiException {
    return Kubernetes.createService(service);
  }

  /**
   * Delete a Kubernetes Service.
   *
   * @param name name of the Service
   * @param namespace name of namespace
   * @return true if successful
   */
  public static boolean delete(String name, String namespace) {
    return Kubernetes.deleteService(name, namespace);
  }
}

// Copyright (c) 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes.actions.impl;

import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.models.V1Service;
import oracle.weblogic.kubernetes.actions.impl.primitive.Kubernetes;

public class Service {

  /**
   * Create Kubernetes Service
   *
   * @param service - V1Service object containing Kubernetes secret configuration data
   * @return true if successful
   * @throws ApiException - missing required configuration data, if Kubernetes request fails or
   *     unsuccessful
   */
  public static boolean create(V1Service service) throws ApiException {
    return Kubernetes.createService(service);
  }

  /**
   * Delete Kubernetes Service
   *
   * @param service - V1Service object containing service configuration data
   * @return true if successful
   * @throws ApiException - missing required configuration data, if Kubernetes request fails or
   *     unsuccessful
   */
  public static boolean delete(V1Service service) throws ApiException {
    return Kubernetes.deleteService(service);
  }
}

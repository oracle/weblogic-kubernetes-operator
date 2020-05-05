// Copyright (c) 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes.actions.impl;

import java.util.Map;

import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.models.V1Service;
import oracle.weblogic.kubernetes.actions.impl.primitive.Kubernetes;

import static oracle.weblogic.kubernetes.extensions.LoggedTest.logger;

public class Service {

  /**
   * Create a Kubernetes Service.
   *
   * @param service V1Service object containing Kubernetes service configuration data
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

  /**
   * Get a Kubernetes Service.
   *
   * @param serviceName name of the Service
   * @param label Map of key value pair with which the service is decorated with
   * @param namespace name of namespace
   * @return V1Service object if found otherwise null
   */
  public static V1Service getService(
      String serviceName,
      Map<String, String> label,
      String namespace) {
    V1Service v1Service;
    try {
      v1Service = Kubernetes.getService(serviceName, label, namespace);
    } catch (ApiException apex) {
      logger.severe(apex.getResponseBody());
      return null;
    }
    return v1Service;
  }

  /**
   * Returns NodePort of admin service.
   *
   * @param serviceName name of admin server service
   * @param label key value pair with which the service is decorated with
   * @param namespace the namespace in which to check for the service
   * @return AdminNodePort of the Kubernetes service if exits else -1
   */
  public static int getAdminServiceNodePortString(
      String serviceName,
      Map<String, String> label,
      String namespace) {

    int adminNodePort = -1;
    try {
      adminNodePort = Kubernetes.getAdminServiceNodePort(serviceName, label, namespace);
    } catch (ApiException apex) {
      logger.severe(apex.getResponseBody());
      return -1;
    }
    return adminNodePort;
  }

}

// Copyright (c) 2020, 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes.actions.impl;


import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.models.V1Service;
import io.kubernetes.client.openapi.models.V1ServiceList;
import oracle.weblogic.kubernetes.actions.impl.primitive.Kubernetes;


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
   * Get namespaced service object.
   *
   * @param namespace name of the namespace in which to get the service
   * @param serviceName name of the service object to get
   * @return V1Service object if found, otherwise null
   */
  public static V1Service getNamespacedService(String namespace, String serviceName) {
    return Kubernetes.getNamespacedService(namespace, serviceName);
  }

  /**
   * Get node port of a namespaced service given the channel name.
   *
   * @param namespace name of the namespace in which to get the service
   * @param serviceName name of the service
   * @param channelName name of the channel for which to get the nodeport
   * @return node port if service and channel is found, otherwise -1
   */
  public static int getServiceNodePort(String namespace, String serviceName, String channelName) {
    return Kubernetes.getServiceNodePort(namespace, serviceName, channelName);
  }

  /**
   * Get node port of a namespaced service.
   *
   * @param namespace name of the namespace in which to get the service
   * @param serviceName name of the service
   * @return node port if service and channel is found, otherwise -1
   */
  public static int getServiceNodePort(String namespace, String serviceName) {
    return Kubernetes.getServiceNodePort(namespace, serviceName);
  }

  /**
   * Get port of a namespaced service given the channel name.
   *
   * @param namespace name of the namespace in which to get the service
   * @param serviceName name of the service
   * @param channelName name of the channel for which to get the port
   * @return node port if service and channel is found, otherwise -1
   */
  public static int getServicePort(String namespace, String serviceName, String channelName) {
    return Kubernetes.getServicePort(namespace, serviceName, channelName);
  }

  /**
   * List services in a namespace.
   *
   * @param namespace namespace in which to list services
   * @return V1ServiceList
   */
  public static V1ServiceList listServices(String namespace) {
    return Kubernetes.listServices(namespace);
  }

}


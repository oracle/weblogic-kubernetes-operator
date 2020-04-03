// Copyright (c) 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes.actions.impl;

import java.util.List;

import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.models.V1Namespace;
import oracle.weblogic.kubernetes.actions.impl.primitive.Kubernetes;

public class Namespace {

  /**
   * Creates a new unique namespace
   * @return name of the unique namespace
   * @throws ApiException - if Kubernetes request fails to create a namespace
   */
  public static String createUniqueNamespace() throws ApiException {
    return Kubernetes.createUniqueNamespace();
  }

  /**
   * Create Kubernetes namespace
   *
   * @param name - V1Namespace object containing namespace configuration data
   * @return true on success, false otherwise
   * @throws ApiException - missing required configuration data, if Kubernetes request fails or
   *     unsuccessful
   */
  public static boolean create(V1Namespace name) throws ApiException {
    return Kubernetes.createNamespace(name);
  }

  /**
   * List of namespaces in Kubernetes cluster
   *
   * @return - List of names of all namespaces in Kubernetes cluster
   * @throws ApiException - if Kubernetes client API call fails
   */
  public static List<String> listNamespaces() throws ApiException {
    return Kubernetes.listNamespaces();
  }

  /**
   * Delete a Kubernetes namespace
   *
   * @param namespace - V1Namespace object containing name space configuration data
   * @return true if successful
   * @throws ApiException - missing required configuration data, if Kubernetes request fails or
   *     unsuccessful
   */
  public static boolean delete(V1Namespace namespace) throws ApiException {
    return Kubernetes.deleteNamespace(namespace);
  }
}

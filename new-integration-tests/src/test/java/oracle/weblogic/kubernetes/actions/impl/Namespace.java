// Copyright (c) 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes.actions.impl;

import java.util.List;
import java.util.Random;

import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.models.V1Namespace;
import oracle.weblogic.kubernetes.actions.impl.primitive.Kubernetes;

public class Namespace {
  public static Random random = new Random(System.currentTimeMillis());
  private String name;

  public Namespace name(String name) {
    this.name = name;
    return this;
  }

  /**
   * Generates a "unique" name by choosing a random name from
   * 26^4 possible combinations.
   *
   * @return name
   */
  public static String uniqueName() {
    char[] nsName = new char[4];
    for (int i = 0; i < nsName.length; i++) {
      nsName[i] = (char) (random.nextInt(25) + (int) 'a');
    }
    String uniqueName = "ns-" + new String(nsName);
    return uniqueName;
  }

  public boolean create() throws ApiException {
    return Kubernetes.createNamespace(name);
  }

  /**
   * Create a Kubernetes namespace.
   *
   * @param name V1Namespace object containing namespace configuration data
   * @return true on success, false otherwise
   * @throws ApiException if Kubernetes Client API request fails
   */
  public static boolean create(V1Namespace name) throws ApiException {
    return Kubernetes.createNamespace(name);
  }

  /**
   * List of namespaces in Kubernetes cluster.
   *
   * @return List of names of all namespaces in Kubernetes cluster
   * @throws ApiException if Kubernetes client API call fails
   */
  public static List<String> listNamespaces() throws ApiException {
    return Kubernetes.listNamespaces();
  }

  /**
   * Delete a Kubernetes namespace.
   *
   * @param namespace name of namespace
   * @return true if successful, false otherwise
   */
  public static boolean delete(String namespace) {
    return Kubernetes.deleteNamespace(namespace);
  }

  public static boolean exists(String name) throws ApiException {
    return Kubernetes.listNamespaces().contains(name);
  }
}

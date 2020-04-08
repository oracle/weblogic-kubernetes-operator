// Copyright (c) 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes.actions.impl;

import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.models.V1PersistentVolumeClaim;
import oracle.weblogic.kubernetes.actions.impl.primitive.Kubernetes;

/**
 * TestAction class PersistentVolumeClaim to create and delete a Kubernetes Persistent Volume
 * It takes a oracle.weblogic.domain.PersistentVolumeClaim persistentVolumeClaim POJO object
 * to pass the PersistentVolumeClaim details to the create method
 */
public class PersistentVolumeClaim {

  /**
   * Create a Kubernetes Persistent Volume Claim.
   *
   * @param persistentVolumeClaim object containing persistent volume claim configuration data
   * @return true if successful false otherwise
   * @throws ApiException if Kubernetes client API call fails
   */
  public static boolean create(V1PersistentVolumeClaim persistentVolumeClaim) throws ApiException {
    return Kubernetes.createPvc(persistentVolumeClaim);
  }

  /**
   * Delete the Kubernetes Persistent Volume Claim.
   *
   * @param name name of the Persistent Volume Claim
   * @param namespace name of the namespace
   * @return true if successful, false otherwise
   * @throws ApiException if Kubernetes client API call fails
   */
  public static boolean delete(String name, String namespace) throws ApiException {
    return Kubernetes.deletePvc(name, namespace);
  }
}

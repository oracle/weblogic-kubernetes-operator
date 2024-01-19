// Copyright (c) 2020, 2024, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes.actions.impl;

import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.models.V1ServiceAccount;
import io.kubernetes.client.openapi.models.V1ServiceAccountList;
import oracle.weblogic.kubernetes.actions.impl.primitive.Kubernetes;

public class ServiceAccount {

  /**
   * Create a Kubernetes Service Account.
   *
   * @param serviceAccount V1ServiceAccount object containing service account configuration data
   * @return created service account
   * @throws ApiException if Kubernetes client API call fails
   */
  public static boolean create(V1ServiceAccount serviceAccount) throws ApiException {
    Kubernetes.createServiceAccount(serviceAccount);
    return true;
  }

  /**
   * Delete a Kubernetes Service Account.
   *
   * @param name name of the Service Account
   * @param namespace name of namespace
   * @return true if successful, false otherwise
   */
  public static boolean delete(String name, String namespace) {
    return Kubernetes.deleteServiceAccount(name, namespace);
  }

  /**
   * Verify whether the service account exists in the namespace.
   * @param name name of the service account
   * @param namespace namespace where the service account exits
   * @return true if the service account exists, false otherwise
   */
  public static boolean serviceAccountExists(String name, String namespace) {
    V1ServiceAccountList sas = Kubernetes.listServiceAccounts(namespace);
    if (sas != null) {
      for (V1ServiceAccount sa : sas.getItems()) {
        if (sa.getMetadata().getName().equals(name)) {
          return true;
        }
      }
    }
    return false;
  }
}

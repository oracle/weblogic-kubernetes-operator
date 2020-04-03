// Copyright (c) 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes.actions.impl;

import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.models.V1Secret;
import oracle.weblogic.kubernetes.actions.impl.primitive.Kubernetes;

public class Secret {

  /**
   * Create Kubernetes Secret
   *
   * @param secret - V1Secret object containing Kubernetes secret configuration data
   * @return true if successful
   * @throws ApiException - missing required configuration data, if Kubernetes request fails or
   *     unsuccessful
   */
  public static boolean create(V1Secret secret) throws ApiException {
    return Kubernetes.createSecret(secret);
  }

  /**
   * Delete Kubernetes Secret
   *
   * @param secret - V1Secret object containing Kubernetes secret configuration data
   * @return true if successful
   * @throws ApiException - missing required configuration data, if Kubernetes request fails or
   *     unsuccessful
   */
  public static boolean delete(V1Secret secret) throws ApiException {
    return Kubernetes.deleteSecret(secret);
  }
}

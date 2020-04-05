// Copyright (c) 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes.actions.impl;

import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.models.V1ServiceAccount;
import oracle.weblogic.kubernetes.actions.impl.primitive.Kubernetes;

public class ServiceAccount {

  public static boolean create(V1ServiceAccount serviceAccount) throws ApiException {
    Kubernetes.createServiceAccount(serviceAccount);
    return true;
  }

  public static boolean delete(V1ServiceAccount serviceAccount) throws ApiException {
    Kubernetes.deleteServiceAccount(serviceAccount);
    return true;
  }
}

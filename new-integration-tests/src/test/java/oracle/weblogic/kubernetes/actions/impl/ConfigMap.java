// Copyright (c) 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes.actions.impl;

import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.models.V1ConfigMap;
import io.kubernetes.client.openapi.models.V1ConfigMapList;
import oracle.weblogic.kubernetes.actions.impl.primitive.Kubernetes;

public class ConfigMap {

  /**
   * Create Kubernetes Config Map.
   *
   * @param configMap V1ConfigMap object containing config map configuration data
   * @return true on success
   * @throws ApiException if Kubernetes client API call fails
  */
  public static boolean create(V1ConfigMap configMap) throws ApiException {
    return Kubernetes.createConfigMap(configMap);
  }

  /**
   * Delete Kubernetes Config Map.
   *
   * @param name name of the Config Map
   * @param namespace name of namespace
   * @return true if successful, false otherwise
  */
  public static boolean delete(String name, String namespace) {
    return Kubernetes.deleteConfigMap(name, namespace);
  }

  /**
   * List Kubernetes Config Map in a namesapce.
   *
   * @param namespace name of namespace
   * @return List of Config Maps in a namespace
  */
  public static V1ConfigMapList list(String namespace) {
    V1ConfigMapList retConfigMapList = null;
    try {
      retConfigMapList = Kubernetes.listConfigMaps(namespace);
    } catch (ApiException api) {
      return null;
    }
    return retConfigMapList;
  }
}

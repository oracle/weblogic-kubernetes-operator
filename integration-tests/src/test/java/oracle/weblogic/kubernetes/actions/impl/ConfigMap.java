// Copyright (c) 2020, 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes.actions.impl;

import java.util.ArrayList;
import java.util.List;

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
   * Check whether the configmap exists.
   * @param name name of the config map
   * @param namespace namespace where config map exists
   * @return true if the config map exists, false otherwise
   * @throws ApiException if Kubernetes client API call fails
   */
  public static boolean doesCMExist(String name, String namespace) throws ApiException {
    List<V1ConfigMap> configMaps = new ArrayList<>();
    V1ConfigMapList configMapList = Kubernetes.listConfigMaps(namespace);

    if (configMapList != null) {
      configMaps = configMapList.getItems();
    }

    for (V1ConfigMap configMap : configMaps) {
      if (configMap.getMetadata() != null && configMap.getMetadata().getName() != null
          && configMap.getMetadata().getName().equals(name)) {
        return true;
      }
    }

    return false;
  }
}

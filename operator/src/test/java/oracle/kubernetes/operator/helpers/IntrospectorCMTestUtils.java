// Copyright (c) 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helpers;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import javax.annotation.Nonnull;

import io.kubernetes.client.openapi.models.V1ConfigMap;
import io.kubernetes.client.openapi.models.V1ObjectMeta;

import static oracle.kubernetes.operator.DomainProcessorTestSetup.UID;

public class IntrospectorCMTestUtils {

  /**
   * Returns the data portion of the introspector config map for the test domain.
   * @param testSupport the instance of KubernetesTestSupport holding the data
   */
  @Nonnull
  public static Map<String, String> getIntrospectorConfigMapData(KubernetesTestSupport testSupport) {
    return testSupport.getResources(KubernetesTestSupport.CONFIG_MAP).stream()
          .map(V1ConfigMap.class::cast)
          .filter(IntrospectorCMTestUtils::isIntrospectorConfigMap)
          .map(V1ConfigMap::getData)
          .findFirst()
          .orElseGet(Collections::emptyMap);
  }

  /**
   * Returns the metadata of the introspector config map for the test domain.
   * @param testSupport the instance of KubernetesTestSupport holding the data
   */
  public static V1ObjectMeta getIntrospectorConfigMapMetadata(KubernetesTestSupport testSupport) {
    return testSupport.getResources(KubernetesTestSupport.CONFIG_MAP).stream()
        .map(V1ConfigMap.class::cast)
        .filter(IntrospectorCMTestUtils::isIntrospectorConfigMap)
        .map(V1ConfigMap::getMetadata)
        .findFirst()
        .orElse(null);
  }

  private static boolean isIntrospectorConfigMap(V1ConfigMap configMap) {
    return getIntrospectorConfigMapName().equals(getConfigMapName(configMap));
  }

  private static String getConfigMapName(V1ConfigMap configMap) {
    return Optional.ofNullable(configMap.getMetadata()).map(V1ObjectMeta::getName).orElse("");
  }

  static String getIntrospectorConfigMapName() {
    return ConfigMapHelper.getIntrospectorConfigMapName(UID);
  }
}

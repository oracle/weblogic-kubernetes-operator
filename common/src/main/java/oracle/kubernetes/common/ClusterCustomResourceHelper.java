// Copyright (c) 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.common;

import java.util.List;
import java.util.Map;

import io.kubernetes.client.openapi.ApiException;

public interface ClusterCustomResourceHelper {
  void createClusterResource(Map<String, Object> clusterSpec, Map<String, Object> domain) throws ApiException;

  Map<String, Object> getDeployedClusterResources(String namespace, String domainUid);

  List<String> getNamesOfDeployedClusterResources(String namespace, String domainUid);
}

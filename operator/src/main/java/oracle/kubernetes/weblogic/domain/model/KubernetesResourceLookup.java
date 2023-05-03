// Copyright (c) 2019, 2023, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.weblogic.domain.model;

import java.util.List;

import io.kubernetes.client.openapi.models.V1LocalObjectReference;
import io.kubernetes.client.openapi.models.V1Secret;

/**
 * An object to check the presence of required Kubernetes resources.
 */
public interface KubernetesResourceLookup {

  /**
   * Returns all the secrets.

   * @return list of secrets
   */
  List<V1Secret> getSecrets();

  /**
   * Returns true if the Kubernetes cluster to which this domain is deployed has a config map with the specified
   * name and namespace.
   * @param name the name of the configmap
   * @param namespace the containing namespace
   * @return true if such a configmap exists
   */
  boolean isConfigMapExists(String name, String namespace);

  /**
   * Finds a Cluster resource given a reference.
   * @param reference Local reference
   * @return Cluster resource or null, if cluster resource is not found
   */
  ClusterResource findCluster(V1LocalObjectReference reference);

  /**
   * Finds a cluster resource in a namespace given a reference.
   * @param reference Local reference
   * @param namespace the namespace in which the cluster resource is looked for
   * @return Cluster resource or null, if cluster resource is not found
   */
  ClusterResource findClusterInNamespace(V1LocalObjectReference reference, String namespace);

  /**
   * Returns all domain resources in the given namespace.
   *
   * @param namespace the containing namespace
   * @return list of DomainResources
   */
  List<DomainResource> getDomains(String namespace);
}

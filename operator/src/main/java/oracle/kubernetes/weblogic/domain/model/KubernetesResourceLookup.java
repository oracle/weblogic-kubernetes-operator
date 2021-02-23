// Copyright (c) 2019, 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.weblogic.domain.model;

/**
 * An object to check the presence of required Kubernetes resources.
 */
public interface KubernetesResourceLookup {

  /**
   * Returns true if the Kubernetes cluster to which this domain is deployed has a secret with the specified
   * name and namespace.
   * @param name the name of the secret
   * @param namespace the containing namespace
   * @return true if such a secret exists
   */
  boolean isSecretExists(String name, String namespace);

  /**
   * Returns true if the Kubernetes cluster to which this domain is deployed has a config map with the specified
   * name and namespace.
   * @param name the name of the configmap
   * @param namespace the containing namespace
   * @return true if such a configmap exists
   */
  boolean isConfigMapExists(String name, String namespace);
}

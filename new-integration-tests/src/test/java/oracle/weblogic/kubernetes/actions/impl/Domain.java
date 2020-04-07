// Copyright (c) 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes.actions.impl;

import io.kubernetes.client.openapi.ApiException;
import oracle.weblogic.domain.DomainList;
import oracle.weblogic.kubernetes.actions.impl.primitive.Kubernetes;

public class Domain {

  /**
   * Create a domain custom resource.
   *
   * @param domain Domain custom resource model object
   * @return true on success, false otherwise
   */
  public static boolean createDomainCustomResource(oracle.weblogic.domain.Domain domain)
      throws ApiException {
    return Kubernetes.createDomainCustomResource(domain);
  }

  /**
   * List all Custom Resource Domains in a namespace.
   *
   * @param namespace name of namespace
   * @return list of Custom Resource Domains for a given namespace
   */
  public static DomainList listDomainCustomResources(String namespace) {
    return Kubernetes.listDomains(namespace);
  }

  public static boolean shutdown(String domainUID, String namespace) {
    return true;
  }

  public static boolean restart(String domainUID, String namespace) {
    return true;
  }

  /**
   * Delete a Domain Custom Resource.
   *
   * @param domainUID unique domain identifier
   * @param namespace name of namespace
   * @return true if successful, false otherwise
   * @throws ApiException if Kubernetes client API call fails
   */
  public static boolean deleteDomainCustomResource(String domainUID, String namespace)
      throws ApiException {
    return Kubernetes.deleteDomainCustomResource(domainUID, namespace);
  }

  /**
   * Get a Domain Custom Resource.
   *
   * @param domainUID unique domain identifier
   * @param namespace name of namespace
   * @return domain custom resource or null if Domain does not exist
   * @throws ApiException if Kubernetes request fails
   */
  public static oracle.weblogic.domain.Domain getDomainCustomResource(String domainUID,
      String namespace) throws ApiException {
    return Kubernetes.getDomainCustomResource(domainUID, namespace);
  }
}

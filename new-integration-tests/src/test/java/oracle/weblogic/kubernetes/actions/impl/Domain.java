// Copyright (c) 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes.actions.impl;

import io.kubernetes.client.custom.V1Patch;
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

  public static boolean shutdown(String domainUid, String namespace) {
    return true;
  }

  public static boolean restart(String domainUid, String namespace) {
    return true;
  }

  /**
   * Delete a Domain Custom Resource.
   *
   * @param domainUid unique domain identifier
   * @param namespace name of namespace
   * @return true if successful, false otherwise
   */
  public static boolean deleteDomainCustomResource(String domainUid, String namespace) {
    return Kubernetes.deleteDomainCustomResource(domainUid, namespace);
  }

  /**
   * Get a Domain Custom Resource.
   *
   * @param domainUid unique domain identifier
   * @param namespace name of namespace
   * @return domain custom resource or null if Domain does not exist
   * @throws ApiException if Kubernetes request fails
   */
  public static oracle.weblogic.domain.Domain getDomainCustomResource(String domainUid,
      String namespace) throws ApiException {
    return Kubernetes.getDomainCustomResource(domainUid, namespace);
  }

  /**
   * Patch the Domain Custom Resource.
   *
   * @param domainUid unique domain identifier
   * @param namespace name of namespace
   * @param patch patch data in format matching the specified media type
   * @param patchFormat one of the following types used to identify patch document:
   *     "application/json-patch+json", "application/merge-patch+json",
   * @return true if successful, false otherwise
   */
  public static boolean patchDomainCustomResource(String domainUid, String namespace, V1Patch patch,
      String patchFormat) {
    return Kubernetes.patchDomainCustomResource(domainUid, namespace, patch, patchFormat);
  }
}

// Copyright (c) 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes.actions.impl;

import java.util.List;

import io.kubernetes.client.openapi.ApiException;
import oracle.weblogic.kubernetes.actions.impl.primitive.Kubernetes;

public class Domain {

  /**
   * Create Custom Resource Domain
   * @param namespace - name of namespace
   * @param domainYAML - path to a file containing domain custom resource spec in yaml format
   * @return true if successful
   */
  public static boolean createDomainCustomResource(String namespace, String domainYAML) {
    return true;
  }

  /**
   * List all Custom Resource Domains
   *
   * @param namespace - name of namespace
   * @return list of Custom Resource Domains for a given namespace
   * @throws ApiException - if Kubernetes client API call fails
   */
  public static List<String> listDomainCustomResources(String namespace) throws ApiException {
    return Kubernetes.listDomains(namespace);
  }

  public static boolean shutdown(String domainUID, String namespace) {
    return true;
  }

  public static boolean restart(String domainUID, String namespace) {
    return true;
  }

  /**
   * Delete the domain custom resource
   *
   * @param domainUID - unique domain identifier
   * @param namespace - name of namespace
   * @return true if successful, false otherwise
   * @throws ApiException - if Kubernetes client API call fails
   */
  public static boolean deleteDomainCustomResource(String domainUID, String namespace)
      throws ApiException {
    return Kubernetes.deleteDomainCustomResource(domainUID, namespace);
  }

  /**
   * Get the domain custom resource
   *
   * @param domainUID - unique domain identifier
   * @param namespace - name of namespace
   * @return domain custom resource
   * @throws ApiException - if Kubernetes request fails or domain does not exist
   */
  public static oracle.weblogic.domain.Domain getDomainCustomResource(String domainUID,
      String namespace) throws ApiException {
    return Kubernetes.getDomainCustomResource(domainUID, namespace);
  }
}

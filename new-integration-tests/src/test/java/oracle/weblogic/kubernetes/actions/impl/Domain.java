// Copyright (c) 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes.actions.impl;

import java.util.ArrayList;
import java.util.List;

import io.kubernetes.client.custom.V1Patch;
import io.kubernetes.client.openapi.ApiException;
import oracle.weblogic.domain.Cluster;
import oracle.weblogic.domain.DomainList;
import oracle.weblogic.kubernetes.actions.impl.primitive.Kubernetes;

import static oracle.weblogic.kubernetes.extensions.LoggedTest.logger;

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

  /**
   * Shut down a domain in the specified namespace.
   * @param domainUid the domain to shut down
   * @param namespace the namespace in which the domain exists
   * @return true if patching domain custom resource succeeded, false otherwise
   */
  public static boolean shutdown(String domainUid, String namespace) {
    // change the /spec/serverStartPolicy to NEVER to shut down all servers in the domain
    // create patch string to shut down the domain
    StringBuffer patchStr = new StringBuffer("[{")
        .append("\"op\": \"add\", ")
        .append("\"path\": \"/spec/serverStartPolicy\", ")
        .append("\"value\": \"NEVER\"")
        .append("}]");

    logger.info("Shutting down domain {0} in namespace {1} using patch string: {2}",
        domainUid, namespace, patchStr.toString());

    V1Patch patch = new V1Patch(new String(patchStr));

    return patchDomainCustomResource(domainUid, namespace, patch, V1Patch.PATCH_FORMAT_JSON_PATCH);
  }

  /**
   * Restart a domain in the specified namespace.
   *
   * @param domainUid the domain to restart
   * @param namespace the namespace in which the domain exists
   * @return true if patching domain resource succeeded, false otherwise
   */
  public static boolean restart(String domainUid, String namespace) {

    // change the /spec/serverStartPolicy to IF_NEEDED to start all servers in the domain
    // create patch string to start the domain
    StringBuffer patchStr = new StringBuffer("[{")
        .append("\"op\": \"replace\", ")
        .append("\"path\": \"/spec/serverStartPolicy\", ")
        .append("\"value\": \"IF_NEEDED\"")
        .append("}]");

    logger.info("Restarting domain {0} in namespace {1} using patch string: {2}",
        domainUid, namespace, patchStr.toString());

    V1Patch patch = new V1Patch(new String(patchStr));

    return patchDomainCustomResource(domainUid, namespace, patch, V1Patch.PATCH_FORMAT_JSON_PATCH);
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

  /**
   * Scale the cluster of the domain in the specified namespace.
   *
   * @param domainUid domainUid of the domain to be scaled
   * @param namespace namespace in which the domain exists
   * @param clusterName name of the WebLogic cluster to be scaled in the domain
   * @param numOfServers number of servers to be scaled to
   * @return true if patch domain custom resource succeeds, false otherwise
   * @throws ApiException if Kubernetes client API call fails
   */
  public static boolean scaleCluster(String domainUid, String namespace, String clusterName, int numOfServers)
      throws ApiException {

    // get the domain cluster list
    oracle.weblogic.domain.Domain domain = getDomainCustomResource(domainUid, namespace);

    List<Cluster> clusters = new ArrayList<>();
    if (domain.getSpec() != null) {
      clusters = domain.getSpec().getClusters();
    }

    // get the index of the cluster with clusterName in the cluster list
    int index = 0;
    for (int i = 0; i < clusters.size(); i++) {
      if (clusters.get(i).getClusterName().equals(clusterName)) {
        index = i;
        break;
      }
    }

    // construct the patch string for scaling the cluster in the domain
    StringBuffer patchStr = new StringBuffer("[{")
        .append("\"op\": \"replace\", ")
        .append("\"path\": \"/spec/clusters/")
        .append(index)
        .append("/replicas\", ")
        .append("\"value\": ")
        .append(numOfServers)
        .append("}]");

    logger.info("Scaling cluster {0} in domain {1} using patch string: {2}",
        clusterName, domainUid, patchStr.toString());

    V1Patch patch = new V1Patch(new String(patchStr));

    return Kubernetes.patchDomainCustomResource(domainUid, namespace, patch, V1Patch.PATCH_FORMAT_JSON_PATCH);
  }

}

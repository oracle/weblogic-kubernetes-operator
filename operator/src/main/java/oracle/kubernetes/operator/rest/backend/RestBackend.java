// Copyright (c) 2017, 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.rest.backend;

import java.util.Set;

import oracle.kubernetes.operator.rest.model.DomainAction;

/**
 * The RestBackend interface is to implement all of the WebLogic Operator REST resources that need
 * to talk to Kubernetes and WebLogic to get their work done. It separates the jaxrs part of the
 * WebLogic Operator REST api from its implementation.
 */
public interface RestBackend {

  /**
   * Get the unique identifiers of all the WebLogic domains that have been registered with the
   * WebLogic operator.
   *
   * @return a Set of domain UIDs.
   */
  Set<String> getDomainUids();

  /**
   * Determines whether or not a WebLogic domain has been registered with the WebLogic operator.
   *
   * @param domainUid - the unique identifier assigned to a WebLogic domain.
   * @return whether or not this domainUID has been registered with the WebLogic operator.
   */
  boolean isDomainUid(String domainUid);

  /**
   * Applies the specified command to the specified domain.
   * @param domainUid the unique ID of a domain
   * @param params an update command with optional parameters
   */
  void performDomainAction(String domainUid, DomainAction params);

  /**
   * Get the names of the clusters in a WebLogic domain.
   *
   * @param domainUid - the unique identifier assigned to the Weblogic domain when it was registered
   *     with the WebLogic operator. The caller is responsible for calling isDomainUid first and not
   *     calling this method if the domain has not been registered.
   * @return a Set of Weblogic cluster names.
   */
  Set<String> getClusters(String domainUid);

  /**
   * Determines whether or not a cluster exists in a WebLogic domain.
   *
   * @param domainUid - the unique identifier assigned to the Weblogic domain when it was registered
   *     with the WebLogic operator. The caller is responsible for calling isDomainUid first and not
   *     calling this method if the domain has not been registered.
   * @param cluster - the name of the cluster in the WebLogic domain.
   * @return whether or not a cluster with this name exists in the WebLogic domain.
   */
  boolean isCluster(String domainUid, String cluster);

  /**
   * Scales the number of managed servers in a WebLogic cluster. This method configures the desired
   * number of managed servers, both at the Kubernetes and WebLogic cluster levels, then returns. It
   * does not wait for the number of running managed servers to match the configured number of
   * servers.
   *
   * @param domainUid - the unique identifier assigned to the Weblogic domain when it was registered
   *     with the WebLogic operator. The caller is responsible for calling isDomainUid first and not
   *     calling this method if the domain has not been registered.
   * @param cluster - the name of the cluster in the WebLogic domain. The caller is responsible for
   *     calling isCluster first and not calling this method if the cluster does not exist.
   * @param managedServerCount - the desired number of WebLogic managed servers.
   */
  void scaleCluster(String domainUid, String cluster, int managedServerCount);
}

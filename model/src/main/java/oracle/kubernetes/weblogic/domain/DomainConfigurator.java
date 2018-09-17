// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.weblogic.domain;

import io.kubernetes.client.models.V1LocalObjectReference;
import javax.annotation.Nonnull;
import oracle.kubernetes.weblogic.domain.v1.Domain;
import oracle.kubernetes.weblogic.domain.v1.DomainV1Configurator;

/**
 * Configures a domain, adding settings independently of the version of the domain representation.
 * Note that the configurator uses a predefined domain schema, and should only be used for testing.
 * Using it in the runtime runs the risk of corrupting the domain.
 */
public interface DomainConfigurator {

  static DomainConfigurator forDomain(Domain domain) {
    return new DomainV1Configurator(domain);
  }

  /**
   * Defines a name for the domain's admin server.
   *
   * @param adminServerName the name of the admin server
   */
  void defineAdminServer(String adminServerName);

  /**
   * Defines a name and port for the domain's admin server.
   *
   * @param adminServerName the name of the admin server
   * @param port the admin server port
   */
  void defineAdminServer(String adminServerName, int port);

  /**
   * Sets the default number of replicas to be run in a cluster.
   *
   * @param replicas a non-negative number
   */
  void setDefaultReplicas(int replicas);

  /**
   * Sets the default image for the domain.
   *
   * @param image the name of the image
   */
  void setDefaultImage(String image);

  /**
   * Sets the default image pull policy for the domain.
   *
   * @param imagepullpolicy the new policy
   */
  void setDefaultImagePullPolicy(String imagepullpolicy);

  /**
   * Sets the default image pull secret for the domain
   *
   * @param secretReference the object referring to the secret
   */
  void setDefaultImagePullSecret(V1LocalObjectReference secretReference);

  DomainConfigurator setStartupControl(String startupControl);

  /**
   * Add an environment variable to the domain
   *
   * @param name variable name
   * @param value value
   * @return this object
   */
  DomainConfigurator withEnvironmentVariable(String name, String value);

  /**
   * Adds an admin server configuration to the domain, if not already present.
   *
   * @return an object to add additional configurations
   */
  ServerConfigurator configureAdminServer();

  /**
   * Adds a default server configuration to the domain, if not already present.
   *
   * @param serverName the name of the server to add
   * @return an object to add additional configurations
   */
  ServerConfigurator configureServer(@Nonnull String serverName);

  /**
   * Adds a default cluster configuration to the domain, if not already present.
   *
   * @param clusterName the name of the server to add
   * @return an object to add additional configurations
   */
  ClusterConfigurator configureCluster(@Nonnull String clusterName);
}

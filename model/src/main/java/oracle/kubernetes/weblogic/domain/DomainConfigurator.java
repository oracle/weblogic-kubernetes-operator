// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.weblogic.domain;

import io.kubernetes.client.models.V1LocalObjectReference;
import io.kubernetes.client.models.V1ObjectMeta;
import io.kubernetes.client.models.V1PodSecurityContext;
import io.kubernetes.client.models.V1SecurityContext;
import java.util.Arrays;
import javax.annotation.Nonnull;
import oracle.kubernetes.weblogic.domain.v2.Domain;
import oracle.kubernetes.weblogic.domain.v2.DomainSpec;
import oracle.kubernetes.weblogic.domain.v2.DomainStorage;

/**
 * Configures a domain, adding settings independently of the version of the domain representation.
 * Note that the configurator uses a predefined domain schema, and should only be used for testing.
 * Using it in the runtime runs the risk of corrupting the domain.
 */
@SuppressWarnings("UnusedReturnValue")
public abstract class DomainConfigurator {

  private Domain domain;

  public DomainConfigurator() {}

  protected DomainConfigurator(Domain domain) {
    this.domain = domain;
    if (domain.getSpec() == null) domain.setSpec(new DomainSpec());
    if (domain.getMetadata() == null) domain.setMetadata(new V1ObjectMeta());
  }

  public abstract DomainConfigurator createFor(Domain domain);

  /**
   * Sets the home for the domain.
   *
   * @param home the home of the domain
   * @return this object
   */
  public DomainConfigurator withDomainHome(String domainHome) {
    getDomainSpec().setDomainHome(domainHome);
    return this;
  }

  /**
   * @param homeInImage
   * @return
   */
  public DomainConfigurator withDomainHomeInImage(boolean domainHomeInImage) {
    getDomainSpec().setDomainHomeInImage(domainHomeInImage);
    return this;
  }

  /**
   * Defines a name for the domain's admin server.
   *
   * @param adminServerName the name of the admin server
   */
  public abstract AdminServerConfigurator configureAdminServer(String adminServerName);

  public void withDefaultReplicaCount(int replicas) {
    getDomainSpec().setReplicas(replicas);
  }

  /**
   * Sets the default image for the domain.
   *
   * @param image the name of the image
   * @return this object
   */
  public DomainConfigurator withDefaultImage(String image) {
    getDomainSpec().setImage(image);
    return this;
  }

  /**
   * Sets the default image pull policy for the domain.
   *
   * @param imagepullpolicy the new policy
   * @return this object
   */
  public DomainConfigurator withDefaultImagePullPolicy(String imagepullpolicy) {
    getDomainSpec().setImagePullPolicy(imagepullpolicy);
    return this;
  }

  /**
   * Sets the default image pull secret for the domain
   *
   * @param secretReference the object referring to the secret
   * @return this object
   */
  public DomainConfigurator withDefaultImagePullSecret(V1LocalObjectReference secretReference) {
    getDomainSpec().setImagePullSecret(secretReference);
    return this;
  }

  /**
   * Sets the image pull secrets for the domain
   *
   * @param secretReferences a list of objects referring to secrets
   * @return this object
   */
  public DomainConfigurator withDefaultImagePullSecrets(
      V1LocalObjectReference... secretReferences) {
    getDomainSpec().setImagePullSecrets(Arrays.asList(secretReferences));
    return this;
  }

  /**
   * Configures the domain to use a persistent volume claim defined before the domain is created.
   *
   * @param claimName the name of the persistent volume claim
   * @return this object
   */
  public DomainConfigurator withPredefinedClaim(String claimName) {
    getDomainSpec().setStorage(DomainStorage.createPredefinedClaim(claimName));
    return this;
  }

  /**
   * Configures the domain to use storage in the local node.
   *
   * @param path the path to the storage
   * @return this object
   */
  public DomainConfigurator withHostPathStorage(String path) {
    getDomainSpec().setStorage(DomainStorage.createHostPathStorage(path));
    return this;
  }

  /**
   * Configures the domain to use storage on a remote server.
   *
   * @param server the server hosting the storage
   * @param path the path to the storage
   * @return this object
   */
  public DomainConfigurator withNfsStorage(String server, String path) {
    getDomainSpec().setStorage(DomainStorage.createNfsStorage(server, path));
    return this;
  }

  /**
   * Defines the amount of storage to allocate for the domain.
   *
   * @param size the size to allocate
   * @return this object
   */
  public DomainConfigurator withStorageSize(String size) {
    getDomainSpec().getStorage().setStorageSize(size);
    return this;
  }

  /**
   * Defines the amount of storage to allocate for the domain.
   *
   * @param policy the size to allocate
   * @return this object
   */
  public DomainConfigurator withStorageReclaimPolicy(String policy) {
    getDomainSpec().getStorage().setStorageReclaimPolicy(policy);
    return this;
  }

  /**
   * Sets the WebLogic configuration overrides configmap name for the domain
   *
   * @param configMapName Name of the Kubernetes configmap that contains the config overrides
   * @return this object
   */
  public abstract DomainConfigurator withConfigOverrides(String configMapName);

  /**
   * Sets the WebLogic configuration overrides secret names for the domain
   *
   * @param secretNames a list of secret names
   * @return this object
   */
  public abstract DomainConfigurator withConfigOverrideSecrets(String... secretNames);

  /**
   * Sets the default settings for the readiness probe. Any settings left null will default to the
   * tuning parameters.
   *
   * @param initialDelay the default initial delay, in seconds.
   * @param timeout the default timeout, in seconds.
   * @param period the default probe period, in seconds.
   */
  public abstract void withDefaultReadinessProbeSettings(
      Integer initialDelay, Integer timeout, Integer period);

  /**
   * Sets the default settings for the liveness probe. Any settings left null will default to the
   * tuning parameters.
   *
   * @param initialDelay the default initial delay, in seconds.
   * @param timeout the default timeout, in seconds.
   * @param period the default probe period, in seconds.
   */
  public abstract void withDefaultLivenessProbeSettings(
      Integer initialDelay, Integer timeout, Integer period);

  /**
   * Sets the default server start policy ("ALWAYS", "NEVER" or "IF_NEEDED") for the domain.
   *
   * @param startPolicy the new default policy
   */
  public abstract DomainConfigurator withDefaultServerStartPolicy(String startPolicy);

  /**
   * Add an environment variable to the domain
   *
   * @param name variable name
   * @param value value
   * @return this object
   */
  public abstract DomainConfigurator withEnvironmentVariable(String name, String value);

  protected DomainSpec getDomainSpec() {
    return domain.getSpec();
  }

  protected String getAsName() {
    return domain.getAsName();
  }

  public abstract DomainConfigurator withAdditionalVolume(String name, String path);

  public abstract DomainConfigurator withAdditionalVolumeMount(String name, String path);

  /**
   * Adds a default server configuration to the domain, if not already present.
   *
   * @param serverName the name of the server to add
   * @return an object to add additional configurations
   */
  public abstract ServerConfigurator configureServer(@Nonnull String serverName);

  /**
   * Adds a default cluster configuration to the domain, if not already present.
   *
   * @param clusterName the name of the server to add
   * @return an object to add additional configurations
   */
  public abstract ClusterConfigurator configureCluster(@Nonnull String clusterName);

  public abstract void setShuttingDown(boolean start);

  /**
   * Add a node label to the Domain's node selector
   *
   * @param labelKey the pod label key
   * @param labelValue the pod label value
   * @return this object
   */
  public abstract DomainConfigurator withNodeSelector(String labelKey, String labelValue);

  /**
   * Add a resource requirement at domain level. The requests for memory are measured in bytes. You
   * can express memory as a plain integer or as a fixed-point integer using one of these suffixes:
   * E, P, T, G, M, K. You can also use the power-of-two equivalents: Ei, Pi, Ti, Gi, Mi, Ki. The
   * requests for cpu are mesured in cpu units and can be expressed in millicores i.e. 100m is the
   * same as 0.1
   *
   * @param resource the resource to be added as requirement cpu or memory
   * @param quantity the quantity required for the resource
   * @return this object
   */
  public abstract DomainConfigurator withRequestRequirement(String resource, String quantity);

  /**
   * Add a resource limit at domain level, the requests for memory are measured in bytes. You can
   * express memory as a plain integer or as a fixed-point integer using one of these suffixes: E,
   * P, T, G, M, K. You can also use the power-of-two equivalents: Ei, Pi, Ti, Gi, Mi, Ki. The
   * requests for cpu are mesured in cpu units and can be expressed in millicores i.e. 100m is the
   * same as 0.1
   *
   * @param resource the resource to be added as requirement cpu or memory
   * @param quantity the quantity required for the resource
   * @return this object
   */
  public abstract DomainConfigurator withLimitRequirement(String resource, String quantity);

  /**
   * Add security constraints at container level, if the same constraint is also defined at pod
   * level then container constraint take precedence
   *
   * @param containerSecurityContext the security context object
   * @return this object
   */
  public abstract DomainConfigurator withContainerSecurityContext(
      V1SecurityContext containerSecurityContext);

  /**
   * Add security constraints at container level, if the same constraint is also defined at pod
   * level then container constraint take precedence
   *
   * @param podSecurityContext
   * @return this object
   */
  public abstract DomainConfigurator withPodSecurityContext(
      V1PodSecurityContext podSecurityContext);
}

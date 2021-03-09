// Copyright (c) 2018, 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.weblogic.domain;

import java.util.Arrays;
import javax.annotation.Nonnull;

import io.kubernetes.client.openapi.models.V1Affinity;
import io.kubernetes.client.openapi.models.V1Container;
import io.kubernetes.client.openapi.models.V1EnvVar;
import io.kubernetes.client.openapi.models.V1LocalObjectReference;
import io.kubernetes.client.openapi.models.V1PodReadinessGate;
import io.kubernetes.client.openapi.models.V1PodSecurityContext;
import io.kubernetes.client.openapi.models.V1SecurityContext;
import io.kubernetes.client.openapi.models.V1Toleration;
import oracle.kubernetes.operator.DomainSourceType;
import oracle.kubernetes.operator.OverrideDistributionStrategy;
import oracle.kubernetes.weblogic.domain.model.Domain;
import oracle.kubernetes.weblogic.domain.model.DomainSpec;

/**
 * Configures a domain, adding settings independently of the version of the domain representation.
 * Note that the configurator uses a predefined domain schema, and should only be used for testing.
 * Using it in the runtime runs the risk of corrupting the domain.
 */
@SuppressWarnings("UnusedReturnValue")
public abstract class DomainConfigurator {

  private Domain domain;

  public DomainConfigurator() {
  }

  protected DomainConfigurator(Domain domain) {
    this.domain = domain;
  }

  public abstract DomainConfigurator createFor(Domain domain);

  /**
   * Sets the home for the domain.
   *
   * @param domainHome the home of the domain
   * @return this object
   */
  public DomainConfigurator withDomainHome(String domainHome) {
    getDomainSpec().setDomainHome(domainHome);
    return this;
  }

  /**
   * Sets the full path of liveness probe custom script for domain.
   *
   * @param livenessProbeCustomScript full path of the liveness probe custom script
   * @return this object
   */
  public DomainConfigurator withLivenessProbeCustomScript(String livenessProbeCustomScript) {
    getDomainSpec().setLivenessProbeCustomScript(livenessProbeCustomScript);
    return this;
  }

  /**
   * Specifies whether the domain home is stored in the image.
   *
   * @param domainHomeInImage boolean indicating if the domain home is stored in the image
   * @return this object
   */
  public DomainConfigurator withDomainHomeInImage(boolean domainHomeInImage) {
    getDomainSpec().setDomainHomeInImage(domainHomeInImage);
    return this;
  }

  public DomainConfigurator withDomainHomeSourceType(DomainSourceType domainHomeSourceType) {
    getDomainSpec().setDomainHomeSourceType(domainHomeSourceType);
    return this;
  }

  /**
   * Configure admin server.
   *
   * @return An AdminServerConfigurator object for configuring an admin server
   */
  public abstract AdminServerConfigurator configureAdminServer();

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
   * Sets the default image pull secret for the domain.
   *
   * @param secretReference the object referring to the secret
   * @return this object
   */
  public DomainConfigurator withDefaultImagePullSecret(V1LocalObjectReference secretReference) {
    getDomainSpec().setImagePullSecret(secretReference);
    return this;
  }

  /**
   * Sets the image pull secrets for the domain.
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
   * Sets the log home value.
   *
   * @param logHome the log home value
   * @return this object
   */
  public DomainConfigurator withLogHome(String logHome) {
    getDomainSpec().setLogHome(logHome);
    return this;
  }

  /**
   * Sets the log home enabled flag.
   *
   * @param logHomeEnabled true if log home is enabled, false otherwise
   * @return this object
   */
  public DomainConfigurator withLogHomeEnabled(boolean logHomeEnabled) {
    getDomainSpec().setLogHomeEnabled(logHomeEnabled);
    return this;
  }

  /**
   * Sets the data home value.
   *
   * @param dataHome the data home value
   * @return this object
   */
  public DomainConfigurator withDataHome(String dataHome) {
    getDomainSpec().setDataHome(dataHome);
    return this;
  }

  /**
   * Sets whether to write server HTTP access log files to the directory specified in
   * logHome.
   *
   * @param httpAccessLogInLogHome boolean specifying whether to write server HTTP
   *                               access log files to the logHome directory
   * @return this object
   */
  public DomainConfigurator withHttpAccessLogInLogHome(boolean httpAccessLogInLogHome) {
    getDomainSpec().setHttpAccessLogInLogHome(httpAccessLogInLogHome);
    return this;
  }

  public DomainConfigurator withAllowReplicasBelowMinDynClusterSize(Boolean allowReplicasBelowMinDynClusterSize) {
    getDomainSpec().setAllowReplicasBelowMinDynClusterSize(allowReplicasBelowMinDynClusterSize);
    return this;
  }

  public DomainConfigurator withMaxConcurrentStartup(Integer maxConcurrentStartup) {
    getDomainSpec().setMaxClusterConcurrentStartup(maxConcurrentStartup);
    return this;
  }

  public DomainConfigurator withMaxConcurrentShutdown(Integer maxConcurrentShutdown) {
    getDomainSpec().setMaxClusterConcurrentShutdown(maxConcurrentShutdown);
    return this;
  }

  /**
   * Sets the WebLogic configuration overrides configmap name for the domain.
   *
   * @param configMapName Name of the Kubernetes configmap that contains the config overrides
   * @return this object
   */
  public abstract DomainConfigurator withConfigOverrides(String configMapName);

  /**
   * Sets the WebLogic configuration overrides secret names for the domain.
   *
   * @param secretNames a list of secret names
   * @return this object
   */
  public abstract DomainConfigurator withConfigOverrideSecrets(String... secretNames);

  public abstract DomainConfigurator withConfigOverrideDistributionStrategy(
        OverrideDistributionStrategy strategy);

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
   * @return this object
   */
  public abstract DomainConfigurator withDefaultServerStartPolicy(String startPolicy);

  /**
   * Sets the server start state ("RUNNING" or "ADMIN") for the domain.
   *
   * @param startState the server start state
   * @return this object
   */
  public abstract DomainConfigurator withServerStartState(String startState);

  /**
   * Add an environment variable with the given name and value to the domain.
   *
   * @param name variable name
   * @param value value
   * @return this object
   */
  public abstract DomainConfigurator withEnvironmentVariable(String name, String value);

  /**
   * Add an environment variable to the domain.
   * @param envVar V1EnvVar to be added to the domain
   * @return this object
   */
  public abstract DomainConfigurator withEnvironmentVariable(V1EnvVar envVar);

  protected DomainSpec getDomainSpec() {
    return domain.getSpec();
  }

  public abstract DomainConfigurator withAdditionalVolume(String name, String path);

  public abstract DomainConfigurator withAdditionalPvClaimVolume(String name, String claimName);

  public abstract DomainConfigurator withAdditionalVolumeMount(String name, String path);

  public abstract DomainConfigurator withInitContainer(V1Container initContainer);

  public abstract DomainConfigurator withContainer(V1Container container);

  public abstract DomainConfigurator withPodLabel(String name, String value);

  public abstract DomainConfigurator withPodAnnotation(String name, String value);

  public abstract DomainConfigurator withMonitoringExporterConfiguration(String configuration);

  public abstract DomainConfigurator withMonitoringExporterImage(String imageName);

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
   * Add a node label to the Domain's node selector.
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
   * requests for cpu are measured in cpu units and can be expressed in millicores i.e. 100m is the
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
   * requests for cpu are measured in cpu units and can be expressed in millicores i.e. 100m is the
   * same as 0.1
   *
   * @param resource the resource to be added as requirement cpu or memory
   * @param quantity the quantity required for the resource
   * @return this object
   */
  public abstract DomainConfigurator withLimitRequirement(String resource, String quantity);

  /**
   * Add security constraints at container level, if the same constraint is also defined at pod
   * level then container constraint take precedence.
   *
   * @param containerSecurityContext the security context object
   * @return this object
   */
  public abstract DomainConfigurator withContainerSecurityContext(
      V1SecurityContext containerSecurityContext);

  /**
   * Add security constraints at pod level, if the same constraint is also defined at container
   * level then container constraint take precedence.
   *
   * @param podSecurityContext pod-level security attributes to be added to this DomainConfigurator
   * @return this object
   */
  public abstract DomainConfigurator withPodSecurityContext(
      V1PodSecurityContext podSecurityContext);

  /**
   * Tells the operator whether the customer wants to restart the server pods. The value can be any
   * String and it can be defined on domain, cluster or server to restart the different pods. After
   * the value is added, the corresponding pods will be terminated and created again. If customer
   * modifies the value again after the pods were recreated, then the pods will again be terminated
   * and recreated.
   *
   * @since 2.0
   * @param restartVersion If present, every time this value is updated the operator will restart
   *     the required servers
   * @return this object
   */
  public abstract DomainConfigurator withRestartVersion(String restartVersion);


  /**
   * Tells the operator to start the introspect domain job.
   *
   * @since 2.0
   * @param introspectVersion If present, every time this value is updated the operator will start
   *     the introspect domain job.
   * @return this object
   */
  public abstract DomainConfigurator withIntrospectVersion(String introspectVersion);


  /**
   * Defines a secret reference for the domain.
   * @param secretName the name of the secret
   * @param namespace the namespace containing the secret
   * @return this object
   */
  public abstract DomainConfigurator withWebLogicCredentialsSecret(String secretName, String namespace);

  /**
   * Set affinity for the pod configuration.
   *
   * @param affinity affinity to be set to this DomainConfigurator
   * @return this object
   */
  public abstract DomainConfigurator withAffinity(V1Affinity affinity);

  /**
   * Set restart policy for the pod configuration.
   *
   * @param restartPolicy restart policy to be set to this DomainConfigurator
   * @return this object
   */
  public abstract DomainConfigurator withRestartPolicy(String restartPolicy);

  /**
   * Add readiness gate to the pod configuration.
   *
   * @param readinessGate readiness gate to be added to this DomainConfigurator
   * @return this object
   */
  public abstract DomainConfigurator withReadinessGate(V1PodReadinessGate readinessGate);

  /**
   * Set node name for the pod configuration.
   *
   * @param nodeName node name to be set to this DomainConfigurator
   * @return this object
   */
  public abstract DomainConfigurator withNodeName(String nodeName);

  /**
   * Set scheduler name for the pod configuration.
   *
   * @param schedulerName scheduler name to be set to this DomainConfigurator
   * @return this object
   */
  public abstract DomainConfigurator withSchedulerName(String schedulerName);

  /**
   * Set runtime class name for the pod configuration.
   *
   * @param runtimeClassName runtime class name to be set to this DomainConfigurator
   * @return this object
   */
  public abstract DomainConfigurator withRuntimeClassName(String runtimeClassName);

  /**
   * Set priority class name for the pod configuration.
   *
   * @param priorityClassName priority class name to be set to this DomainConfigurator
   * @return this object
   */
  public abstract DomainConfigurator withPriorityClassName(String priorityClassName);

  /**
   * Add a toleration to the pod configuration.
   *
   * @param toleration toleration to be added to this DomainConfigurator
   * @return this object
   */
  public abstract DomainConfigurator withToleration(V1Toleration toleration);

  /**
   * Add the introspector job active deadline.
   *
   * @param deadline the deadline value to be set to this DomainConfigurator
   * @return this object
   */
  public abstract DomainConfigurator withIntrospectorJobActiveDeadlineSeconds(long deadline);

  /**
   * Add WDT model config map for the domain resource.
   *
   * @param configmap the configmap for WDT model
   * @return this object
   */
  public abstract DomainConfigurator withModelConfigMap(String configmap);

  /**
   * Add model runtime encryption secret for the domain resource.
   *
   * @param secret the runtime encryption secret for WDT model
   * @return this object
   */
  public abstract DomainConfigurator withRuntimeEncryptionSecret(String secret);

  /**
   * Enable MII Online Update.
   *
   * @return this object
   */
  public abstract DomainConfigurator withMIIOnlineUpdate();

  /**
   * Set onNonDynamicChanges to CommitUpdateAndRoll for  MII Online Update.
   *
   * @return this object
   */
  public abstract DomainConfigurator withMIIOnlineUpdateOnDynamicChangesUpdateAndRoll();


  /**
   * Add OPSS wallet password secret for the domain resource.
   *
   * @param secret the OPSS wallet password secret
   * @return this object
   */
  public abstract DomainConfigurator withOpssWalletPasswordSecret(String secret);

  /**
   * Add OPSS wallet file secret for the domain resource.
   *
   * @param secret the OPSS wallet file secret
   * @return this object
   */
  public abstract DomainConfigurator withOpssWalletFileSecret(String secret);

  /**
   * Add Istio for the domain resource.
   *
   * @return this object
   */
  public abstract DomainConfigurator withIstio();

  /**
   * Add domain type for the domain resource.
   *
   * @param type the domain type
   * @return this object
   */
  public abstract DomainConfigurator withDomainType(String type);
}

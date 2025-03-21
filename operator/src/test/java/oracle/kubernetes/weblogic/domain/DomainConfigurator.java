// Copyright (c) 2018, 2024, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.weblogic.domain;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import javax.annotation.Nonnull;

import io.kubernetes.client.openapi.models.V1Affinity;
import io.kubernetes.client.openapi.models.V1Container;
import io.kubernetes.client.openapi.models.V1EnvFromSource;
import io.kubernetes.client.openapi.models.V1EnvVar;
import io.kubernetes.client.openapi.models.V1HostAlias;
import io.kubernetes.client.openapi.models.V1LocalObjectReference;
import io.kubernetes.client.openapi.models.V1PodReadinessGate;
import io.kubernetes.client.openapi.models.V1PodSecurityContext;
import io.kubernetes.client.openapi.models.V1ResourceRequirements;
import io.kubernetes.client.openapi.models.V1SecurityContext;
import io.kubernetes.client.openapi.models.V1Toleration;
import io.kubernetes.client.openapi.models.V1Volume;
import oracle.kubernetes.operator.DomainSourceType;
import oracle.kubernetes.operator.LogHomeLayoutType;
import oracle.kubernetes.operator.ModelInImageDomainType;
import oracle.kubernetes.operator.OverrideDistributionStrategy;
import oracle.kubernetes.operator.ServerStartPolicy;
import oracle.kubernetes.operator.helpers.DomainPresenceInfo;
import oracle.kubernetes.weblogic.domain.model.AuxiliaryImage;
import oracle.kubernetes.weblogic.domain.model.Configuration;
import oracle.kubernetes.weblogic.domain.model.DomainResource;
import oracle.kubernetes.weblogic.domain.model.DomainSpec;
import oracle.kubernetes.weblogic.domain.model.InitializeDomainOnPV;
import oracle.kubernetes.weblogic.domain.model.Model;
import oracle.kubernetes.weblogic.domain.model.Shutdown;

/**
 * Configures a domain, adding settings independently of the version of the domain representation.
 * Note that the configurator uses a predefined domain schema, and should only be used for testing.
 * Using it in the runtime runs the risk of corrupting the domain.
 */
@SuppressWarnings("UnusedReturnValue")
public abstract class DomainConfigurator {

  private DomainResource domain;

  protected DomainConfigurator() {
    // no-op
  }

  protected DomainConfigurator(DomainResource domain) {
    this.domain = domain;
  }

  public abstract DomainConfigurator createFor(DomainResource domain);

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
   * Sets the value of replace environment variables in Java options for domain.
   *
   * @param replaceEnvironmentVariablesInJavaOptions value of replace Env variables in Java options.
   * @return this object
   */
  public DomainConfigurator withReplaceEnvVariablesInJavaOptions(Boolean replaceEnvironmentVariablesInJavaOptions) {
    getDomainSpec().setReplaceVariablesInJavaOptions(replaceEnvironmentVariablesInJavaOptions);
    return this;
  }

  public DomainConfigurator withDomainHomeSourceType(DomainSourceType domainHomeSourceType) {
    getDomainSpec().setDomainHomeSourceType(domainHomeSourceType);
    return this;
  }

  public DomainConfigurator withWDTInstallationHome(String wdtInstallationHome) {
    getDomainSpec().setWdtInstallHome(wdtInstallationHome);
    return this;
  }

  public DomainConfigurator withModelHome(String modelHome) {
    getDomainSpec().setModelHome(modelHome);
    return this;
  }

  /**
   * Sets the configuration for initialization for the domain on PV.
   *
   * @param initializeDomainOnPv configuration for initialization for the domain on PV
   * @return this object
   */
  public DomainConfigurator withInitializeDomainOnPv(InitializeDomainOnPV initializeDomainOnPv) {
    Configuration configuration = getDomainSpec().getConfiguration();
    if (configuration == null) {
      getDomainSpec().setConfiguration(new Configuration());
    }
    Optional.ofNullable(getDomainSpec().getConfiguration())
        .ifPresent(c -> c.withInitializeDomainOnPv(initializeDomainOnPv));
    return this;
  }

  /**
   * Configure introspector.
   *
   * @return An IntrospectorConfigurator object for configuring an admin server
   */
  public abstract IntrospectorConfigurator configureIntrospector();

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
   * Sets the host aliases in the server pod for the domain.
   *
   * @param hostAliases a list of host aliases
   * @return this object
   */
  public DomainConfigurator withHostAliases(
      V1HostAlias... hostAliases) {
    getDomainSpec().setHostAliases(Arrays.asList(hostAliases));
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
   * Sets the log home layout value.
   *
   * @param logHomeLayout the log home layout value
   * @return this object
   */
  public DomainConfigurator withLogHomeLayout(LogHomeLayoutType logHomeLayout) {
    getDomainSpec().setLogHomeLayout(logHomeLayout);
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
   * Sets the admin channel port forwarding enabled flag.
   *
   * @param adminChannelPortForwardingEnabled true if admin channel port forwarding is enabled, false otherwise
   * @return this object
   */
  public DomainConfigurator withAdminChannelPortForwardingEnabled(boolean adminChannelPortForwardingEnabled) {
    Optional.ofNullable(getDomainSpec().getAdminServer())
            .ifPresent(admin -> admin.setAdminChannelPortForwardingEnabled(adminChannelPortForwardingEnabled));
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

  public DomainConfigurator withMaxConcurrentStartup(Integer maxConcurrentStartup) {
    getDomainSpec().setMaxClusterConcurrentStartup(maxConcurrentStartup);
    return this;
  }

  public DomainConfigurator withMaximumReadyWaitTimeSeconds(Long readyWaitTimeSeconds) {
    getDomainSpec().setMaxReadyWaitTimeSeconds(readyWaitTimeSeconds);
    return this;
  }

  public DomainConfigurator withMaximumPendingWaitTimeSeconds(Long pendingWaitTimeSeconds) {
    getDomainSpec().setMaxPendingWaitTimeSeconds(pendingWaitTimeSeconds);
    return this;
  }

  public DomainConfigurator withMaxConcurrentShutdown(Integer maxConcurrentShutdown) {
    getDomainSpec().setMaxClusterConcurrentShutdown(maxConcurrentShutdown);
    return this;
  }

  public DomainConfigurator withMaxUnavailable(Integer maxUnavailable) {
    getDomainSpec().setMaxClusterUnavailable(maxUnavailable);
    return this;
  }

  public DomainConfigurator withClusterReference(String clusterResourceName) {
    getDomainSpec().getClusters().add(new V1LocalObjectReference().name(clusterResourceName));
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
  public abstract void withDefaultReadinessProbeSettings(Integer initialDelay, Integer timeout, Integer period);

  /**
   * Add auxiliary images for the domain resource.
   */
  public abstract DomainConfigurator withAuxiliaryImages(List<AuxiliaryImage> ai);

  /**
   * Configure auxiliary image volume mount path.
   */
  public abstract DomainConfigurator withAuxiliaryImageVolumeMountPath(String auxiliaryImageVolumeMountPath);

  /**
   * Configure auxiliary image volume withAuxiliaryImageVolumeMedium.
   */
  public abstract DomainConfigurator withAuxiliaryImageVolumeMedium(String auxiliaryImageVolumeMedium);

  /**
   * Configure auxiliary image volume size limit.
   */
  public abstract DomainConfigurator withAuxiliaryImageVolumeSizeLimit(String auxiliaryImageSizeLimit);

  /**
   * Sets the default settings for the liveness probe. Any settings left null will default to the
   * tuning parameters.
   *
   * @param initialDelay the default initial delay, in seconds.
   * @param timeout the default timeout, in seconds.
   * @param period the default probe period, in seconds.
   *
   */
  public abstract void withDefaultLivenessProbeSettings(Integer initialDelay, Integer timeout, Integer period);

  /**
   * Sets the default thresholds for the liveness probe. Any thresholds left null will default to the
   * tuning parameters.
   *
   * @param successThreshold the default success threshold.
   * @param failureThreshold the default failure threshold.
   */
  public abstract void withDefaultLivenessProbeThresholds(Integer successThreshold, Integer failureThreshold);

  /**
   * Sets the default server start policy ("Always", "Never" or "IfNeeded") for the domain.
   *
   * @param startPolicy the new default policy
   * @return this object
   */
  public abstract DomainConfigurator withDefaultServerStartPolicy(ServerStartPolicy startPolicy);

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

  /**
   * Add env from a source such as a config map or a secret.
   *
   * @param envFromSources list of source of the env variables.
   * @return this object
   */
  public abstract DomainConfigurator withEnvFrom(List<V1EnvFromSource> envFromSources);

  protected DomainSpec getDomainSpec() {
    return domain.getSpec();
  }

  public abstract DomainConfigurator withAdditionalVolume(String name, String path);

  public abstract DomainConfigurator withAdditionalVolume(V1Volume volume);

  public abstract DomainConfigurator withAdditionalPvClaimVolume(String name, String claimName);

  public abstract DomainConfigurator withAdditionalVolumeMount(String name, String path);

  public abstract DomainConfigurator withInitContainer(V1Container initContainer);

  public abstract DomainConfigurator withContainer(V1Container container);

  public abstract DomainConfigurator withPodLabel(String name, String value);

  public abstract DomainConfigurator withPodAnnotation(String name, String value);

  public abstract DomainConfigurator withMonitoringExporterConfiguration(String configuration);

  public abstract DomainConfigurator withMonitoringExporterResources(V1ResourceRequirements resourceRequirements);

  public abstract DomainConfigurator withMonitoringExporterImage(String imageName);

  public abstract DomainConfigurator withMonitoringExporterPort(Integer port);

  public abstract DomainConfigurator withFluentdConfiguration(boolean watchIntrospectorLog,
                                                              String credentialName, String fluentdConfig,
                                                              List<String> args, List<String> command);

  public abstract DomainConfigurator withFluentbitConfiguration(boolean watchIntrospectorLog,
                                                                String credentialName, String fluentbitConfig,
                                                                String parserConfig,
                                                                List<String> args, List<String> command);

  public abstract DomainConfigurator withServerPodShutdownSpec(Shutdown shutdown);

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
   * @param info Domain processor info
   * @param clusterName the name of the server to add
   * @return an object to add additional configurations
   */
  public abstract ClusterConfigurator configureCluster(DomainPresenceInfo info, @Nonnull String clusterName);

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
   * String, and it can be defined on domain, cluster or server to restart the different pods. After
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
   * @return this object
   */
  public abstract DomainConfigurator withWebLogicCredentialsSecret(String secretName);

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
   * Add the maximum server pod wait time.
   *
   * @param waitTime the wait time value to be set to this DomainConfigurator
   * @return this object
   */
  public abstract DomainConfigurator withMaxReadyWaitTimeSeconds(long waitTime);

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
   * Add domain type for the domain resource.
   *
   * @param type the domain type
   * @return this object
   */
  public abstract DomainConfigurator withDomainType(ModelInImageDomainType type);

  /**
   * Specify the Severe error retry interval in seconds.
   * @param retrySeconds the new value
   * @return this object
   */
  public abstract DomainConfigurator withFailureRetryIntervalSeconds(long retrySeconds);

  /**
   * Specify the Severe error retry limit in minutes.
   * @param limitMinutes the new value
   * @return this object
   */
  public abstract DomainConfigurator withFailureRetryLimitMinutes(long limitMinutes);

  public abstract DomainConfigurator withInitializeDomainOnPV(InitializeDomainOnPV initPvDomain);

  public abstract DomainConfigurator withConfigurationForInitializeDomainOnPV(
      InitializeDomainOnPV initializeDomainOnPV, String volumeName, String pvcName, String mountPath);

  /**
   * Add OPSS wallet password secret for the domain resource's initializeDomainOnPV.
   *
   * @param secret the OPSS wallet password secret
   * @return this object
   */
  public abstract DomainConfigurator withInitializeDomainOnPVOpssWalletPasswordSecret(String secret);

  /**
   * Add OPSS wallet file secret for the domain resource's initializeDomainOnPV.
   *
   * @param secret the OPSS wallet file secret
   * @return this object
   */
  public abstract DomainConfigurator withInitializeDomainOnPVOpssWalletFileSecret(String secret);

  /**
   * Add domain type for the domain resource's initializeDomainOnPV.
   *
   * @param type the domain type
   * @return this object
   */
  public abstract DomainConfigurator withInitializeDomainOnPVType(String type);

  /**
   * Add domain type for the domain resource's initializeDomainOnPV.
   *
   * @param cm the configmap
   * @return this object
   */
  public abstract DomainConfigurator withDomainCreationConfigMap(String cm);

  /**
   * Add model encryption secret for the domain resource's initializeDomainOnPV.
   *
   * @param secret the model encryption file secret
   * @return this object
   */
  public abstract DomainConfigurator withInitializeDomainOnPVModelEncryptionSecret(String secret);


  public abstract DomainConfigurator withModel(Model model);
}

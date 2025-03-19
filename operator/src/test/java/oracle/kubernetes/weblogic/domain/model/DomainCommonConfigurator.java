// Copyright (c) 2018, 2024, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.weblogic.domain.model;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import javax.annotation.Nonnull;

import io.kubernetes.client.openapi.models.V1Affinity;
import io.kubernetes.client.openapi.models.V1Container;
import io.kubernetes.client.openapi.models.V1EnvFromSource;
import io.kubernetes.client.openapi.models.V1EnvVar;
import io.kubernetes.client.openapi.models.V1LocalObjectReference;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1PodReadinessGate;
import io.kubernetes.client.openapi.models.V1PodSecurityContext;
import io.kubernetes.client.openapi.models.V1ResourceRequirements;
import io.kubernetes.client.openapi.models.V1SecurityContext;
import io.kubernetes.client.openapi.models.V1Toleration;
import io.kubernetes.client.openapi.models.V1TopologySpreadConstraint;
import io.kubernetes.client.openapi.models.V1Volume;
import oracle.kubernetes.operator.DomainSourceType;
import oracle.kubernetes.operator.KubernetesConstants;
import oracle.kubernetes.operator.MIINonDynamicChangesMethod;
import oracle.kubernetes.operator.ModelInImageDomainType;
import oracle.kubernetes.operator.OverrideDistributionStrategy;
import oracle.kubernetes.operator.ServerStartPolicy;
import oracle.kubernetes.operator.helpers.DomainPresenceInfo;
import oracle.kubernetes.weblogic.domain.AdminServerConfigurator;
import oracle.kubernetes.weblogic.domain.ClusterConfigurator;
import oracle.kubernetes.weblogic.domain.DomainConfigurator;
import oracle.kubernetes.weblogic.domain.IntrospectorConfigurator;
import oracle.kubernetes.weblogic.domain.IntrospectorJobPodConfigurator;
import oracle.kubernetes.weblogic.domain.ServerConfigurator;

public class DomainCommonConfigurator extends DomainConfigurator {

  public DomainCommonConfigurator() {
  }

  public DomainCommonConfigurator(@Nonnull DomainResource domain) {
    super(domain);
    setApiVersion(domain);
  }

  @Override
  public DomainConfigurator createFor(DomainResource domain) {
    return new DomainCommonConfigurator(domain);
  }

  @Override
  public IntrospectorConfigurator configureIntrospector() {
    return new IntrospectorConfiguratorImpl(getOrCreateIntrospector());
  }

  private Introspector getOrCreateIntrospector() {
    return getDomainSpec().getOrCreateIntrospector();
  }

  private void setApiVersion(DomainResource domain) {
    domain.setApiVersion(
        KubernetesConstants.DOMAIN_GROUP + "/" + KubernetesConstants.DOMAIN_VERSION);
  }

  @Override
  public AdminServerConfigurator configureAdminServer() {
    return new AdminServerConfiguratorImpl(getOrCreateAdminServer());
  }

  @Override
  public void withDefaultReadinessProbeSettings(Integer initialDelay, Integer timeout, Integer period) {
    getDomainSpec().setReadinessProbe(initialDelay, timeout, period);
  }

  @Override
  public DomainConfigurator withAuxiliaryImages(List<AuxiliaryImage> auxiliaryImages) {
    getOrCreateModel().withAuxiliaryImages(auxiliaryImages);
    return this;
  }

  @Override
  public DomainConfigurator withAuxiliaryImageVolumeMountPath(String auxiliaryImageVolumeMountPath) {
    getOrCreateModel().withAuxiliaryImageVolumeMountPath(auxiliaryImageVolumeMountPath);
    return this;
  }

  @Override
  public DomainConfigurator withAuxiliaryImageVolumeMedium(String auxiliaryImageVolumeMedium) {
    getOrCreateModel().withAuxiliaryImageVolumeMedium(auxiliaryImageVolumeMedium);
    return this;
  }

  @Override
  public DomainConfigurator withAuxiliaryImageVolumeSizeLimit(String auxiliaryImageVolumeSizeLimit) {
    getOrCreateModel().withAuxiliaryImageVolumeSizeLimit(auxiliaryImageVolumeSizeLimit);
    return this;
  }

  @Override
  public void withDefaultLivenessProbeSettings(Integer initialDelay, Integer timeout, Integer period) {
    getDomainSpec().setLivenessProbe(initialDelay, timeout, period);
  }


  @Override
  public void withDefaultLivenessProbeThresholds(Integer successThreshold, Integer failureThreshold) {
    getDomainSpec().setLivenessProbeThresholds(successThreshold, failureThreshold);
  }

  @Override
  public DomainConfigurator withDefaultServerStartPolicy(ServerStartPolicy startPolicy) {
    getDomainSpec().setServerStartPolicy(startPolicy);
    return this;
  }

  @Override
  public DomainConfigurator withEnvironmentVariable(String name, String value) {
    getDomainSpec().addEnvironmentVariable(name, value);
    return this;
  }

  @Override
  public DomainConfigurator withEnvironmentVariable(V1EnvVar envVar) {
    getDomainSpec().addEnvironmentVariable(envVar);
    return this;
  }

  @Override
  public DomainConfigurator withEnvFrom(List<V1EnvFromSource> envFromSources) {
    getDomainSpec().setEnvFrom(envFromSources);
    return this;
  }

  @Override
  public DomainConfigurator withAdditionalVolume(String name, String path) {
    getDomainSpec().addAdditionalVolume(name, path);
    return this;
  }

  @Override
  public DomainConfigurator withAdditionalVolume(V1Volume volume) {
    getDomainSpec().addAdditionalVolume(volume);
    return this;
  }

  @Override
  public DomainConfigurator withAdditionalPvClaimVolume(String name, String claimName) {
    getDomainSpec().addAdditionalPvClaimVolume(name, claimName);
    return this;
  }

  @Override
  public DomainConfigurator withAdditionalVolumeMount(String name, String path) {
    getDomainSpec().addAdditionalVolumeMount(name, path);
    return this;
  }

  @Override
  public DomainConfigurator withInitContainer(V1Container initContainer) {
    getDomainSpec().addInitContainer(initContainer);
    return this;
  }

  @Override
  public DomainConfigurator withContainer(V1Container container) {
    getDomainSpec().addContainer(container);
    return this;
  }

  /**
   * Sets the WebLogic configuration overrides config map name for the domain.
   *
   * @param configMapName Name of the Kubernetes config map that contains the configuration
   *     overrides
   * @return this object
   */
  @Override
  public DomainConfigurator withConfigOverrides(String configMapName) {
    getOrCreateConfiguration().setOverridesConfigMap(configMapName);
    return this;
  }

  @Override
  public DomainConfigurator withConfigOverrideSecrets(String... secretNames) {
    getOrCreateConfiguration().setSecrets(Arrays.asList(secretNames));
    return this;
  }

  @Override
  public DomainConfigurator withConfigOverrideDistributionStrategy(OverrideDistributionStrategy strategy) {
    getOrCreateConfiguration().setOverrideDistributionStrategy(strategy);
    return this;
  }

  @Override
  public DomainConfigurator withPodLabel(String name, String value) {
    getDomainSpec().addPodLabel(name, value);
    return this;
  }

  @Override
  public DomainConfigurator withPodAnnotation(String name, String value) {
    getDomainSpec().addPodAnnotation(name, value);
    return this;
  }

  @Override
  public DomainConfigurator withMonitoringExporterConfiguration(String configuration) {
    getDomainSpec().createMonitoringExporterConfiguration(configuration);
    return this;
  }

  @Override
  public DomainConfigurator withMonitoringExporterResources(V1ResourceRequirements resourceRequirements) {
    getDomainSpec().setMonitoringExporterResources(resourceRequirements);
    return this;
  }

  @Override
  public DomainConfigurator withMonitoringExporterImage(String imageName) {
    getDomainSpec().setMonitoringExporterImage(imageName);
    return this;
  }

  @Override
  public DomainConfigurator withMonitoringExporterPort(Integer port) {
    getDomainSpec().setMonitoringExporterPort(port);
    return this;
  }

  @Override
  public DomainConfigurator withFluentdConfiguration(boolean watchIntrospectorLog,
                                                     String credentialName, String fluendConfig, List<String> args,
                                                     List<String> command) {
    getDomainSpec().withFluentdConfiguration(watchIntrospectorLog, credentialName,
            fluendConfig, args, command);
    return this;
  }

  @Override
  public DomainConfigurator withFluentbitConfiguration(boolean watchIntrospectorLog,
                                                       String credentialName, String fluentbitConfig,
                                                       String parserConfig,
                                                       List<String> args, List<String> command) {
    getDomainSpec().withFluentbitConfiguration(watchIntrospectorLog, credentialName,
            fluentbitConfig, parserConfig, args, command);
    return this;
  }

  @Override
  public DomainConfigurator withServerPodShutdownSpec(Shutdown shutdown) {
    getDomainSpec().setShutdown(shutdown);
    return this;
  }

  private AdminServer getOrCreateAdminServer() {
    return getDomainSpec().getOrCreateAdminServer();
  }

  @Override
  public ServerConfigurator configureServer(@Nonnull String serverName) {
    return new ServerConfiguratorImpl(getOrCreateManagedServer(serverName));
  }

  private Server getOrCreateManagedServer(@Nonnull String serverName) {
    ManagedServer server = getDomainSpec().getManagedServer(serverName);
    if (server != null) {
      return server;
    }

    return createManagedServer(serverName);
  }

  private Server createManagedServer(String serverName) {
    ManagedServer server = new ManagedServer().withServerName(serverName);
    getDomainSpec().getManagedServers().add(server);
    return server;
  }

  @Override
  public DomainConfigurator withNodeSelector(String labelKey, String labelValue) {
    getDomainSpec().addNodeSelector(labelKey, labelValue);
    return this;
  }

  @Override
  public DomainConfigurator withRequestRequirement(String resource, String quantity) {
    getDomainSpec().addRequestRequirement(resource, quantity);
    return this;
  }

  @Override
  public DomainConfigurator withLimitRequirement(String resource, String quantity) {
    getDomainSpec().addLimitRequirement(resource, quantity);
    return this;
  }

  @Override
  public DomainConfigurator withContainerSecurityContext(
      V1SecurityContext containerSecurityContext) {
    getDomainSpec().setContainerSecurityContext(containerSecurityContext);
    return this;
  }

  @Override
  public DomainConfigurator withPodSecurityContext(V1PodSecurityContext podSecurityContext) {
    getDomainSpec().setPodSecurityContext(podSecurityContext);
    return this;
  }

  @Override
  public DomainConfigurator withRestartVersion(String restartVersion) {
    getDomainSpec().setRestartVersion(restartVersion);
    return this;
  }

  @Override
  public DomainConfigurator withIntrospectVersion(String introspectVersion) {
    getDomainSpec().setIntrospectVersion(introspectVersion);
    return this;
  }

  @Override
  public DomainConfigurator withWebLogicCredentialsSecret(String secretName) {
    getDomainSpec().setWebLogicCredentialsSecret(new V1LocalObjectReference().name(secretName));
    return this;
  }

  @Override
  public DomainConfigurator withMaxReadyWaitTimeSeconds(long waitTime) {
    getDomainSpec().setMaxReadyWaitTimeSeconds(waitTime);
    return this;
  }

  @Override
  public ClusterConfigurator configureCluster(DomainPresenceInfo info, @Nonnull String clusterName) {
    return new ClusterConfiguratorImpl(getOrCreateCluster(info, clusterName));
  }

  private ClusterSpec getOrCreateCluster(DomainPresenceInfo info, @Nonnull String clusterName) {
    ClusterResource resource = info.getClusterResource(clusterName);
    if (resource == null) {
      resource = createCluster(info, clusterName);
    }
    return resource.getSpec();
  }

  private ClusterResource createCluster(DomainPresenceInfo info, @Nonnull String clusterName) {
    ClusterResource cluster = new ClusterResource()
        .withMetadata(new V1ObjectMeta().name(clusterName).namespace(getNamespace(info)))
        .spec(new ClusterSpec().withClusterName(clusterName));
    getDomainSpec().getClusters().add(new V1LocalObjectReference().name(clusterName));
    info.addClusterResource(cluster);
    return cluster;
  }

  private String getNamespace(DomainPresenceInfo info) {
    return Optional.ofNullable(info.getDomain())
        .map(DomainResource::getMetadata).map(V1ObjectMeta::getNamespace).orElse(null);
  }


  @Override
  public DomainConfigurator withAffinity(V1Affinity affinity) {
    getDomainSpec().setAffinity(affinity);
    return this;
  }

  @Override
  public DomainConfigurator withRestartPolicy(String restartPolicy) {
    getDomainSpec().setRestartPolicy(restartPolicy);
    return this;
  }

  @Override
  public DomainConfigurator withReadinessGate(V1PodReadinessGate readinessGate) {
    getDomainSpec().addReadinessGate(readinessGate);
    return this;
  }

  @Override
  public DomainConfigurator withNodeName(String nodeName) {
    getDomainSpec().setNodeName(nodeName);
    return this;
  }

  @Override
  public DomainConfigurator withSchedulerName(String schedulerName) {
    getDomainSpec().setSchedulerName(schedulerName);
    return this;
  }

  @Override
  public DomainConfigurator withRuntimeClassName(String runtimeClassName) {
    getDomainSpec().setRuntimeClassName(runtimeClassName);
    return this;
  }

  @Override
  public DomainConfigurator withPriorityClassName(String priorityClassName) {
    getDomainSpec().setPriorityClassName(priorityClassName);
    return this;
  }

  @Override
  public DomainConfigurator withToleration(V1Toleration toleration) {
    getDomainSpec().addToleration(toleration);
    return this;
  }

  @Override
  public DomainConfigurator withIntrospectorJobActiveDeadlineSeconds(long deadline) {
    getOrCreateConfiguration().setIntrospectorJobActiveDeadlineSeconds(deadline);
    return this;
  }

  @Override
  public DomainConfigurator withModelConfigMap(String configmap) {
    getOrCreateModel().withConfigMap(configmap);
    return this;
  }

  @Override
  public DomainConfigurator withRuntimeEncryptionSecret(String secret) {
    getOrCreateModel().withRuntimeEncryptionSecret(secret);
    return this;
  }

  @Override
  public DomainConfigurator withMIIOnlineUpdate() {
    OnlineUpdate onlineUpdate = new OnlineUpdate();
    onlineUpdate.setEnabled(true);
    getOrCreateModel().withOnlineUpdate(onlineUpdate).getOnlineUpdate()
        .setOnNonDynamicChanges(MIINonDynamicChangesMethod.COMMIT_UPDATE_ONLY);
    return this;
  }

  @Override
  public DomainConfigurator withMIIOnlineUpdateOnDynamicChangesUpdateAndRoll() {
    OnlineUpdate onlineUpdate = new OnlineUpdate();
    onlineUpdate.setEnabled(true);
    getOrCreateModel().withOnlineUpdate(onlineUpdate).getOnlineUpdate()
        .setOnNonDynamicChanges(MIINonDynamicChangesMethod.COMMIT_UPDATE_AND_ROLL);
    return this;
  }

  @Override
  public DomainConfigurator withModel(Model model) {
    getOrCreateConfiguration().withModel(model);
    return this;
  }

  @Override
  public DomainConfigurator withOpssWalletPasswordSecret(String secret) {
    getOrCreateOpss().withWalletPasswordSecret(secret);
    return this;
  }

  @Override
  public DomainConfigurator withOpssWalletFileSecret(String secret) {
    getOrCreateOpss().withWalletFileSecret(secret);
    return this;
  }

  @Override
  public DomainConfigurator withInitializeDomainOnPVOpssWalletPasswordSecret(String secret) {
    getOrCreateInitializeDomainOnPVOpss().withWalletPasswordSecret(secret);
    return this;
  }

  @Override
  public DomainConfigurator withInitializeDomainOnPVOpssWalletFileSecret(String secret) {
    getOrCreateInitializeDomainOnPVOpss().withWalletFileSecret(secret);
    return this;
  }

  @Override
  public DomainConfigurator withInitializeDomainOnPVModelEncryptionSecret(String secret) {
    getOrCreateInitializeDomainOnPVModelSecret(secret);
    return this;
  }

  @Override
  public DomainConfigurator withInitializeDomainOnPVType(String type) {
    getOrCreateInitializeDomainOnPVDomain().domainType(type);
    return this;
  }

  @Override
  public DomainConfigurator withDomainCreationConfigMap(String cm) {
    getOrCreateInitializeDomainOnPVDomain().domainCreationConfigMap(cm);
    return this;
  }

  @Override
  public DomainConfigurator withDomainType(ModelInImageDomainType type) {
    getOrCreateModel().withDomainType(type);
    return this;
  }

  @Override
  public DomainConfigurator withFailureRetryIntervalSeconds(long retrySeconds) {
    getDomainSpec().setFailureRetryIntervalSeconds(retrySeconds);
    return this;
  }

  @Override
  public DomainConfigurator withFailureRetryLimitMinutes(long limitMinutes) {
    getDomainSpec().setFailureRetryLimitMinutes(limitMinutes);
    return this;
  }

  @Override
  public DomainConfigurator withInitializeDomainOnPV(InitializeDomainOnPV initializeDomainOnPV) {
    getOrCreateConfiguration().setInitializeDomainOnPV(initializeDomainOnPV);
    return this;
  }

  @Override
  public DomainConfigurator withConfigurationForInitializeDomainOnPV(
      InitializeDomainOnPV initializeDomainOnPV, String volumeName, String pvcName, String mountPath) {
    getOrCreateConfiguration().setInitializeDomainOnPV(initializeDomainOnPV);
    this.withDomainHomeSourceType(DomainSourceType.PERSISTENT_VOLUME);
    this.withAdditionalVolumeMount(volumeName, mountPath);
    this.withAdditionalPvClaimVolume(volumeName, pvcName);
    return this;
  }

  private Configuration getOrCreateConfiguration() {
    DomainSpec spec = getDomainSpec();
    if (spec.getConfiguration() == null) {
      spec.setConfiguration(new Configuration());
    } 
    return spec.getConfiguration();
  }

  private Model getOrCreateModel() {
    Configuration configuration = getOrCreateConfiguration();
    if (configuration.getModel() == null) {
      configuration.setModel(new Model());
    }
    return configuration.getModel();   
  }

  private Opss getOrCreateOpss() {
    Configuration configuration = getOrCreateConfiguration();
    if (configuration.getOpss() == null) {
      configuration.setOpss(new Opss());
    }
    return configuration.getOpss();   
  }

  private InitializeDomainOnPV getOrCreateInitializeDomainOnPV() {
    Configuration configuration = getOrCreateConfiguration();
    if (configuration.getInitializeDomainOnPV() == null) {
      configuration.setInitializeDomainOnPV(new InitializeDomainOnPV());
    }
    return configuration.getInitializeDomainOnPV();
  }

  private DomainOnPV getOrCreateInitializeDomainOnPVDomain() {
    InitializeDomainOnPV initializeDomainOnPV = getOrCreateInitializeDomainOnPV();
    if (initializeDomainOnPV.getDomain() == null) {
      initializeDomainOnPV.domain(new DomainOnPV());
    }
    return initializeDomainOnPV.getDomain();
  }

  private Opss getOrCreateInitializeDomainOnPVOpss() {
    DomainOnPV domain = getOrCreateInitializeDomainOnPVDomain();
    if (domain.getOpss() == null) {
      domain.opss(new Opss());
    }
    return domain.getOpss();
  }

  private void getOrCreateInitializeDomainOnPVModelSecret(String secretName) {
    InitializeDomainOnPV initializeDomainOnPV = getOrCreateInitializeDomainOnPV();
    if (initializeDomainOnPV.getWdtModelEncryptionPassphraseSecret() == null) {
      initializeDomainOnPV.wdtModelEncryptionPassphraseSecret(secretName);
    }
  }

  @Override
  public void setShuttingDown(boolean shuttingDown) {
    configureAdminServer().withServerStartPolicy(shuttingDown ? ServerStartPolicy.NEVER : ServerStartPolicy.ALWAYS);
  }

  static class IntrospectorConfiguratorImpl implements IntrospectorConfigurator {
    private final Introspector introspector;

    IntrospectorConfiguratorImpl(Introspector introspector) {
      this.introspector = introspector;
    }

    @Override
    public IntrospectorJobPodConfigurator withEnvironmentVariable(String name, String value) {
      introspector.addEnvironmentVariable(name, value);
      return this;
    }

    @Override
    public IntrospectorJobPodConfigurator withEnvironmentVariable(V1EnvVar envVar) {
      introspector.addEnvironmentVariable(envVar);
      return this;
    }

    @Override
    public IntrospectorJobPodConfigurator withEnvFrom(List<V1EnvFromSource> envFromSources) {
      introspector.setEnvFrom(envFromSources);
      return this;
    }

    @Override
    public IntrospectorJobPodConfigurator withRequestRequirement(String resource, String quantity) {
      introspector.addRequestRequirement(resource, quantity);
      return this;
    }

    @Override
    public IntrospectorJobPodConfigurator withLimitRequirement(String resource, String quantity) {
      introspector.addLimitRequirement(resource, quantity);
      return this;
    }

    @Override
    public IntrospectorJobPodConfigurator withPodSecurityContext(V1PodSecurityContext podSecurityContext) {
      introspector.setPodSecurityContext(podSecurityContext);
      return this;
    }

    @Override
    public Introspector getIntrospector() {
      return introspector;
    }
  }

  class AdminServerConfiguratorImpl extends ServerConfiguratorImpl
      implements AdminServerConfigurator {
    private final AdminServer adminServer;

    AdminServerConfiguratorImpl(AdminServer adminServer) {
      super(adminServer);
      this.adminServer = adminServer;
    }

    @Override
    public AdminService configureAdminService() {
      return adminServer.createAdminService();
    }

    public AdminServer getAdminServer() { 
      return adminServer; 
    }
  }

  class ServerConfiguratorImpl implements ServerConfigurator {
    private final Server server;

    ServerConfiguratorImpl(Server server) {
      this.server = server;
    }

    @Override
    public ServerConfigurator withEnvironmentVariable(String name, String value) {
      server.addEnvironmentVariable(name, value);
      return this;
    }

    @Override
    public ServerConfigurator withEnvironmentVariable(V1EnvVar envVar) {
      server.addEnvironmentVariable(envVar);
      return this;
    }

    @Override
    public ServerConfigurator withEnvFrom(List<V1EnvFromSource> envFromSource) {
      server.setEnvFrom(envFromSource);
      return this;
    }

    @Override
    public ServerConfigurator withServerStartPolicy(ServerStartPolicy policy) {
      server.setServerStartPolicy(policy);
      return this;
    }

    @Override
    public ServerConfigurator withLivenessProbeSettings(Integer initialDelay, Integer timeout, Integer period) {
      server.setLivenessProbe(initialDelay, timeout, period);
      return this;
    }

    @Override
    public ServerConfigurator withLivenessProbeThresholds(Integer successThreshold, Integer failureThreshold) {
      server.setLivenessProbeThresholds(successThreshold, failureThreshold);
      return this;
    }

    @Override
    public ServerConfigurator withReadinessProbeSettings(Integer initialDelay, Integer timeout, Integer period) {
      server.setReadinessProbe(initialDelay, timeout, period);
      return this;
    }

    @Override
    public ServerConfigurator withReadinessProbeThresholds(Integer successThreshold, Integer failureThreshold) {
      server.setReadinessProbeThresholds(successThreshold, failureThreshold);
      return this;
    }

    @Override
    public ServerConfigurator withReadinessProbeHttpGetActionPath(String httpGetActionPath) {
      server.setReadinessProbeHttpGetActionPath(httpGetActionPath);
      return this;
    }

    @Override
    public ServerConfigurator withRequestRequirement(String resource, String quantity) {
      server.addRequestRequirement(resource, quantity);
      return this;
    }

    @Override
    public ServerConfigurator withNodeSelector(String labelKey, String labelValue) {
      server.addNodeSelector(labelKey, labelValue);
      return this;
    }

    @Override
    public ServerConfigurator withLimitRequirement(String resource, String quantity) {
      server.addLimitRequirement(resource, quantity);
      return this;
    }

    @Override
    public ServerConfigurator withContainerSecurityContext(
        V1SecurityContext containerSecurityContext) {
      server.setContainerSecurityContext(containerSecurityContext);
      return this;
    }

    @Override
    public ServerConfigurator withPodSecurityContext(V1PodSecurityContext podSecurityContext) {
      server.setPodSecurityContext(podSecurityContext);
      return this;
    }

    @Override
    public ServerConfigurator withAdditionalVolume(String name, String path) {
      server.addAdditionalVolume(name, path);
      return this;
    }

    @Override
    public ServerConfigurator withAdditionalVolumeMount(String name, String path) {
      server.addAdditionalVolumeMount(name, path);
      return this;
    }

    @Override
    public ServerConfigurator withInitContainer(V1Container initContainer) {
      server.addInitContainer(initContainer);
      return this;
    }

    @Override
    public ServerConfigurator withContainer(V1Container container) {
      server.addContainer(container);
      return this;
    }

    @Override
    public ServerConfigurator withPodLabel(String name, String value) {
      server.addPodLabel(name, value);
      return this;
    }

    @Override
    public ServerConfigurator withPodAnnotation(String name, String value) {
      server.addPodAnnotation(name, value);
      return this;
    }

    @Override
    public ServerConfigurator withServiceLabel(String name, String value) {
      server.addServiceLabel(name, value);
      return this;
    }

    @Override
    public ServerConfigurator withServiceAnnotation(String name, String value) {
      server.addServiceAnnotation(name, value);
      return this;
    }

    @Override
    public ServerConfigurator withRestartVersion(String restartVersion) {
      server.setRestartVersion(restartVersion);
      return this;
    }

    @Override
    public ServerConfigurator withRestartPolicy(String restartPolicy) {
      server.setRestartPolicy(restartPolicy);
      return this;
    }

    @Override
    public ServerConfigurator withAffinity(V1Affinity affinity) {
      server.setAffinity(affinity);
      return this;
    }

    @Override
    public ServerConfigurator withTopologySpreadConstraints(
        List<V1TopologySpreadConstraint> topologySpreadConstraints) {
      server.setTopologySpreadConstraints(topologySpreadConstraints);
      return this;
    }

    @Override
    public ServerConfigurator withNodeName(String nodeName) {
      server.setNodeName(nodeName);
      return this;
    }

    @Override
    public ServerConfigurator withMaximumReadyWaitTimeSeconds(long waitTime) {
      server.setMaxReadyWaitTimeSeconds(waitTime);
      return this;
    }

    @Override
    public ServerConfigurator withMaximumPendingWaitTimeSeconds(long waitTime) {
      server.setMaxPendingWaitTimeSeconds(waitTime);
      return this;
    }

    @Override
    public ServerConfigurator withSchedulerName(String schedulerName) {
      getDomainSpec().setSchedulerName(schedulerName);
      return this;
    }

    @Override
    public ServerConfigurator withRuntimeClassName(String runtimeClassName) {
      getDomainSpec().setRuntimeClassName(runtimeClassName);
      return this;
    }

    @Override
    public ServerConfigurator withPriorityClassName(String priorityClassName) {
      getDomainSpec().setPriorityClassName(priorityClassName);
      return this;
    }

  }

  class ClusterConfiguratorImpl implements ClusterConfigurator {
    private final ClusterSpec clusterSpec;

    ClusterConfiguratorImpl(ClusterSpec clusterSpec) {
      this.clusterSpec = clusterSpec;
    }

    @Override
    public ClusterConfigurator withReplicas(int replicas) {
      clusterSpec.setReplicas(replicas);
      return this;
    }

    @Override
    public ClusterConfigurator withMaxUnavailable(int maxUnavailable) {
      clusterSpec.setMaxUnavailable(maxUnavailable);
      return this;
    }

    @Override
    public ClusterConfigurator withEnvironmentVariable(String name, String value) {
      clusterSpec.addEnvironmentVariable(name, value);
      return this;
    }

    @Override
    public ClusterConfigurator withEnvFrom(List<V1EnvFromSource> envFromSources) {
      clusterSpec.setEnvFrom(envFromSources);
      return this;
    }

    @Override
    public ClusterConfigurator withServerStartPolicy(ServerStartPolicy policy) {
      clusterSpec.setServerStartPolicy(policy);
      return this;
    }

    @Override
    public ClusterConfigurator withReadinessProbeSettings(Integer initialDelay, Integer timeout, Integer period) {
      clusterSpec.setReadinessProbe(initialDelay, timeout, period);
      return this;
    }

    @Override
    public ClusterConfigurator withReadinessProbeThresholds(Integer successThreshold, Integer failureThreshold) {
      clusterSpec.setReadinessProbeThresholds(successThreshold, failureThreshold);
      return this;
    }

    @Override
    public ClusterConfigurator withLivenessProbeSettings(Integer initialDelay, Integer timeout, Integer period) {
      clusterSpec.setLivenessProbe(initialDelay, timeout, period);
      return this;
    }

    @Override
    public ClusterConfigurator withLivenessProbeThresholds(Integer successThreshold, Integer failureThreshold) {
      clusterSpec.setLivenessProbeThresholds(successThreshold, failureThreshold);
      return this;
    }

    @Override
    public ClusterConfigurator withNodeSelector(String labelKey, String labelValue) {
      clusterSpec.addNodeSelector(labelKey, labelValue);
      return this;
    }

    @Override
    public ClusterConfigurator withRequestRequirement(String resource, String quantity) {
      clusterSpec.addRequestRequirement(resource, quantity);
      return this;
    }

    @Override
    public ClusterConfigurator withLimitRequirement(String resource, String quantity) {
      clusterSpec.addLimitRequirement(resource, quantity);
      return this;
    }

    @Override
    public ClusterConfigurator withContainerSecurityContext(
        V1SecurityContext containerSecurityContext) {
      clusterSpec.setContainerSecurityContext(containerSecurityContext);
      return this;
    }

    @Override
    public ClusterConfigurator withPodSecurityContext(V1PodSecurityContext podSecurityContext) {
      clusterSpec.setPodSecurityContext(podSecurityContext);
      return this;
    }

    @Override
    public ClusterConfigurator withAdditionalVolume(String name, String path) {
      clusterSpec.addAdditionalVolume(name, path);
      return this;
    }

    @Override
    public ClusterConfigurator withAdditionalVolumeMount(String name, String path) {
      clusterSpec.addAdditionalVolumeMount(name, path);
      return this;
    }

    @Override
    public ClusterConfigurator withInitContainer(V1Container initContainer) {
      clusterSpec.addInitContainer(initContainer);
      return this;
    }

    @Override
    public ClusterConfigurator withContainer(V1Container container) {
      clusterSpec.addContainer(container);
      return this;
    }

    @Override
    public ClusterConfigurator withPodLabel(String name, String value) {
      clusterSpec.addPodLabel(name, value);
      return this;
    }

    @Override
    public ClusterConfigurator withPodAnnotation(String name, String value) {
      clusterSpec.addPodAnnotation(name, value);
      return this;
    }

    @Override
    public ClusterConfigurator withServiceLabel(String name, String value) {
      clusterSpec.addClusterLabel(name, value);
      return this;
    }

    @Override
    public ClusterConfigurator withServiceAnnotation(String name, String value) {
      clusterSpec.addClusterAnnotation(name, value);
      return this;
    }

    @Override
    public ClusterConfigurator withRestartVersion(String restartVersion) {
      clusterSpec.setRestartVersion(restartVersion);
      return this;
    }

    @Override
    public ClusterConfigurator withRestartPolicy(String restartPolicy) {
      clusterSpec.setRestartPolicy(restartPolicy);
      return this;
    }

    @Override
    public ClusterConfigurator withAffinity(V1Affinity affinity) {
      clusterSpec.setAffinity(affinity);
      return this;
    }

    @Override
    public ClusterConfigurator withNodeName(String nodeName) {
      clusterSpec.setNodeName(nodeName);
      return this;
    }

    @Override
    public ClusterConfigurator withMaxConcurrentStartup(Integer maxConcurrentStartup) {
      clusterSpec.setMaxConcurrentStartup(maxConcurrentStartup);
      return this;
    }

    @Override
    public ClusterConfigurator withMaxConcurrentShutdown(Integer maxConcurrentShutdown) {
      clusterSpec.setMaxConcurrentShutdown(maxConcurrentShutdown);
      return this;
    }

    @Override
    public ClusterConfigurator withMaximumReadyWaitTimeSeconds(long maximumReadyWaitTimeSeconds) {
      clusterSpec.setMaxReadyWaitTimeSeconds(maximumReadyWaitTimeSeconds);
      return this;
    }

    @Override
    public ClusterConfigurator withMaximumPendingWaitTimeSeconds(long maximumReadyWaitTimeSeconds) {
      clusterSpec.setMaxPendingWaitTimeSeconds(maximumReadyWaitTimeSeconds);
      return this;
    }

    @Override
    public ClusterConfigurator withSchedulerName(String schedulerName) {
      getDomainSpec().setSchedulerName(schedulerName);
      return this;
    }

    @Override
    public ClusterConfigurator withRuntimeClassName(String runtimeClassName) {
      getDomainSpec().setRuntimeClassName(runtimeClassName);
      return this;
    }

    @Override
    public ClusterConfigurator withPriorityClassName(String priorityClassName) {
      getDomainSpec().setPriorityClassName(priorityClassName);
      return this;
    }

    @Override
    public ClusterConfigurator withPrecreateServerService(boolean precreateServerService) {
      getDomainSpec().setPrecreateServerService(precreateServerService);
      return this;
    }
  }
}

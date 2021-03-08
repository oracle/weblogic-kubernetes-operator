// Copyright (c) 2018, 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.weblogic.domain.model;

import java.util.Arrays;
import javax.annotation.Nonnull;

import io.kubernetes.client.openapi.models.V1Affinity;
import io.kubernetes.client.openapi.models.V1Container;
import io.kubernetes.client.openapi.models.V1EnvVar;
import io.kubernetes.client.openapi.models.V1PodReadinessGate;
import io.kubernetes.client.openapi.models.V1PodSecurityContext;
import io.kubernetes.client.openapi.models.V1SecretReference;
import io.kubernetes.client.openapi.models.V1SecurityContext;
import io.kubernetes.client.openapi.models.V1Toleration;
import oracle.kubernetes.operator.KubernetesConstants;
import oracle.kubernetes.operator.MIINonDynamicChangesMethod;
import oracle.kubernetes.operator.OverrideDistributionStrategy;
import oracle.kubernetes.weblogic.domain.AdminServerConfigurator;
import oracle.kubernetes.weblogic.domain.ClusterConfigurator;
import oracle.kubernetes.weblogic.domain.DomainConfigurator;
import oracle.kubernetes.weblogic.domain.ServerConfigurator;

import static oracle.kubernetes.weblogic.domain.model.ConfigurationConstants.START_ALWAYS;
import static oracle.kubernetes.weblogic.domain.model.ConfigurationConstants.START_NEVER;

public class DomainCommonConfigurator extends DomainConfigurator {

  public DomainCommonConfigurator() {
  }

  public DomainCommonConfigurator(@Nonnull Domain domain) {
    super(domain);
    setApiVersion(domain);
  }

  @Override
  public DomainConfigurator createFor(Domain domain) {
    return new DomainCommonConfigurator(domain);
  }

  private void setApiVersion(Domain domain) {
    domain.setApiVersion(
        KubernetesConstants.DOMAIN_GROUP + "/" + KubernetesConstants.DOMAIN_VERSION);
  }

  @Override
  public AdminServerConfigurator configureAdminServer() {
    return new AdminServerConfiguratorImpl(getOrCreateAdminServer());
  }

  @Override
  public void withDefaultReadinessProbeSettings(
      Integer initialDelay, Integer timeout, Integer period) {
    getDomainSpec().setReadinessProbe(initialDelay, timeout, period);
  }

  @Override
  public void withDefaultLivenessProbeSettings(
      Integer initialDelay, Integer timeout, Integer period) {
    getDomainSpec().setLivenessProbe(initialDelay, timeout, period);
  }

  @Override
  public DomainConfigurator withDefaultServerStartPolicy(String startPolicy) {
    getDomainSpec().setServerStartPolicy(startPolicy);
    return this;
  }

  @Override
  public DomainConfigurator withServerStartState(String startState) {
    getDomainSpec().setServerStartState(startState);
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
  public DomainConfigurator withAdditionalVolume(String name, String path) {
    getDomainSpec().addAdditionalVolume(name, path);
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
  public DomainConfigurator withMonitoringExporterImage(String imageName) {
    getDomainSpec().setMonitoringExporterImage(imageName);
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
  public DomainConfigurator withWebLogicCredentialsSecret(String secretName, String namespace) {
    getDomainSpec().setWebLogicCredentialsSecret(new V1SecretReference().name(secretName).namespace(namespace));
    return this;
  }

  @Override
  public ClusterConfigurator configureCluster(@Nonnull String clusterName) {
    return new ClusterConfiguratorImpl(getOrCreateCluster(clusterName));
  }

  private Cluster getOrCreateCluster(@Nonnull String clusterName) {
    Cluster cluster = getDomainSpec().getCluster(clusterName);
    if (cluster != null) {
      return cluster;
    }

    return createCluster(clusterName);
  }

  private Cluster createCluster(@Nonnull String clusterName) {
    Cluster cluster = new Cluster().withClusterName(clusterName);
    getDomainSpec().getClusters().add(cluster);
    return cluster;
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
  public DomainConfigurator withMIIOnlineUpate() {
    OnlineUpdate onlineUpdate = new OnlineUpdate();
    onlineUpdate.setEnabled(true);
    getOrCreateModel().withOnlineUpdate(onlineUpdate).getOnlineUpdate()
        .setOnNonDynamicChanges(MIINonDynamicChangesMethod.CommitUpdateOnly);
    return this;
  }

  @Override
  public DomainConfigurator withMIIOnlineUpdateOnDynamicChangesUpdateAndRoll() {
    OnlineUpdate onlineUpdate = new OnlineUpdate();
    onlineUpdate.setEnabled(true);
    getOrCreateModel().withOnlineUpdate(onlineUpdate).getOnlineUpdate()
        .setOnNonDynamicChanges(MIINonDynamicChangesMethod.CommitUpdateAndRoll);
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
  public DomainConfigurator withIstio() {
    getOrCreateIstio();
    return this;
  }

  @Override
  public DomainConfigurator withDomainType(String type) {
    getOrCreateModel().withDomainType(type);
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

  private Istio getOrCreateIstio() {
    Configuration configuration = getOrCreateConfiguration();
    if (configuration.getIstio() == null) {
      configuration.withIstio(new Istio());
    }
    return configuration.getIstio();
  }

  @Override
  public void setShuttingDown(boolean shuttingDown) {
    configureAdminServer().withServerStartPolicy(shuttingDown ? START_NEVER : START_ALWAYS);
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
    public ServerConfigurator withDesiredState(String desiredState) {
      server.setServerStartState(desiredState);
      return this;
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
    public ServerConfigurator withServerStartState(String state) {
      return withDesiredState(state);
    }

    @Override
    public ServerConfigurator withServerStartPolicy(String policy) {
      server.setServerStartPolicy(policy);
      return this;
    }

    @Override
    public ServerConfigurator withLivenessProbeSettings(
        Integer initialDelay, Integer timeout, Integer period) {
      server.setLivenessProbe(initialDelay, timeout, period);
      return this;
    }

    @Override
    public ServerConfigurator withReadinessProbeSettings(
        Integer initialDelay, Integer timeout, Integer period) {
      server.setReadinessProbe(initialDelay, timeout, period);
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
    public ServerConfigurator withNodeName(String nodeName) {
      server.setNodeName(nodeName);
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
    private final Cluster cluster;

    ClusterConfiguratorImpl(Cluster cluster) {
      this.cluster = cluster;
    }

    @Override
    public ClusterConfigurator withReplicas(int replicas) {
      cluster.setReplicas(replicas);
      return this;
    }

    @Override
    public ClusterConfigurator withMaxUnavailable(int maxUnavailable) {
      cluster.setMaxUnavailable(maxUnavailable);
      return this;
    }

    @Override
    public ClusterConfigurator withDesiredState(String state) {
      cluster.setServerStartState(state);
      return this;
    }

    @Override
    public ClusterConfigurator withEnvironmentVariable(String name, String value) {
      cluster.addEnvironmentVariable(name, value);
      return this;
    }

    @Override
    public ClusterConfigurator withServerStartState(String state) {
      return withDesiredState(state);
    }

    @Override
    public ClusterConfigurator withServerStartPolicy(String policy) {
      cluster.setServerStartPolicy(policy);
      return this;
    }

    @Override
    public ClusterConfigurator withReadinessProbeSettings(
        Integer initialDelay, Integer timeout, Integer period) {
      cluster.setReadinessProbe(initialDelay, timeout, period);
      return this;
    }

    @Override
    public ClusterConfigurator withLivenessProbeSettings(
        Integer initialDelay, Integer timeout, Integer period) {
      cluster.setLivenessProbe(initialDelay, timeout, period);
      return this;
    }

    @Override
    public ClusterConfigurator withNodeSelector(String labelKey, String labelValue) {
      cluster.addNodeSelector(labelKey, labelValue);
      return this;
    }

    @Override
    public ClusterConfigurator withRequestRequirement(String resource, String quantity) {
      cluster.addRequestRequirement(resource, quantity);
      return this;
    }

    @Override
    public ClusterConfigurator withLimitRequirement(String resource, String quantity) {
      cluster.addLimitRequirement(resource, quantity);
      return this;
    }

    @Override
    public ClusterConfigurator withContainerSecurityContext(
        V1SecurityContext containerSecurityContext) {
      cluster.setContainerSecurityContext(containerSecurityContext);
      return this;
    }

    @Override
    public ClusterConfigurator withPodSecurityContext(V1PodSecurityContext podSecurityContext) {
      cluster.setPodSecurityContext(podSecurityContext);
      return this;
    }

    @Override
    public ClusterConfigurator withAdditionalVolume(String name, String path) {
      cluster.addAdditionalVolume(name, path);
      return this;
    }

    @Override
    public ClusterConfigurator withAdditionalVolumeMount(String name, String path) {
      cluster.addAdditionalVolumeMount(name, path);
      return this;
    }

    @Override
    public ClusterConfigurator withInitContainer(V1Container initContainer) {
      cluster.addInitContainer(initContainer);
      return this;
    }

    @Override
    public ClusterConfigurator withContainer(V1Container container) {
      cluster.addContainer(container);
      return this;
    }

    @Override
    public ClusterConfigurator withPodLabel(String name, String value) {
      cluster.addPodLabel(name, value);
      return this;
    }

    @Override
    public ClusterConfigurator withPodAnnotation(String name, String value) {
      cluster.addPodAnnotation(name, value);
      return this;
    }

    @Override
    public ClusterConfigurator withServiceLabel(String name, String value) {
      cluster.addClusterLabel(name, value);
      return this;
    }

    @Override
    public ClusterConfigurator withServiceAnnotation(String name, String value) {
      cluster.addClusterAnnotation(name, value);
      return this;
    }

    @Override
    public ClusterConfigurator withRestartVersion(String restartVersion) {
      cluster.setRestartVersion(restartVersion);
      return this;
    }

    @Override
    public ClusterConfigurator withRestartPolicy(String restartPolicy) {
      cluster.setRestartPolicy(restartPolicy);
      return this;
    }

    @Override
    public ClusterConfigurator withAffinity(V1Affinity affinity) {
      cluster.setAffinity(affinity);
      return this;
    }

    @Override
    public ClusterConfigurator withNodeName(String nodeName) {
      cluster.setNodeName(nodeName);
      return this;
    }

    @Override
    public ClusterConfigurator withAllowReplicasBelowDynClusterSize(boolean allowReplicasBelowDynClusterSize) {
      cluster.setAllowReplicasBelowMinDynClusterSize(allowReplicasBelowDynClusterSize);
      return this;
    }

    @Override
    public ClusterConfigurator withMaxConcurrentStartup(Integer maxConcurrentStartup) {
      cluster.setMaxConcurrentStartup(maxConcurrentStartup);
      return this;
    }

    @Override
    public ClusterConfigurator withMaxConcurrentShutdown(Integer maxConcurrentShutdown) {
      cluster.setMaxConcurrentShutdown(maxConcurrentShutdown);
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

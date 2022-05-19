// Copyright (c) 2018, 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helpers;

import java.io.File;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import io.kubernetes.client.common.KubernetesObject;
import io.kubernetes.client.openapi.models.V1Container;
import io.kubernetes.client.openapi.models.V1ContainerPort;
import io.kubernetes.client.openapi.models.V1EnvVar;
import io.kubernetes.client.openapi.models.V1LocalObjectReference;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1Pod;
import io.kubernetes.client.openapi.models.V1PodDisruptionBudget;
import io.kubernetes.client.openapi.models.V1PodSpec;
import io.kubernetes.client.openapi.models.V1Secret;
import io.kubernetes.client.openapi.models.V1SecretReference;
import io.kubernetes.client.openapi.models.V1Service;
import io.kubernetes.client.openapi.models.V1VolumeMount;
import oracle.kubernetes.operator.DomainSourceType;
import oracle.kubernetes.operator.ModelInImageDomainType;
import oracle.kubernetes.operator.ProcessingConstants;
import oracle.kubernetes.operator.WebLogicConstants;
import oracle.kubernetes.operator.tuning.TuningParameters;
import oracle.kubernetes.operator.wlsconfig.WlsServerConfig;
import oracle.kubernetes.operator.work.Component;
import oracle.kubernetes.operator.work.Packet;
import oracle.kubernetes.operator.work.PacketComponent;
import oracle.kubernetes.operator.work.Step;
import oracle.kubernetes.utils.SystemClock;
import oracle.kubernetes.weblogic.domain.EffectiveConfigurationFactory;
import oracle.kubernetes.weblogic.domain.model.AdminServer;
import oracle.kubernetes.weblogic.domain.model.AdminServerSpec;
import oracle.kubernetes.weblogic.domain.model.AdminService;
import oracle.kubernetes.weblogic.domain.model.AuxiliaryImage;
import oracle.kubernetes.weblogic.domain.model.BaseConfiguration;
import oracle.kubernetes.weblogic.domain.model.Channel;
import oracle.kubernetes.weblogic.domain.model.Cluster;
import oracle.kubernetes.weblogic.domain.model.ClusterSpec;
import oracle.kubernetes.weblogic.domain.model.ClusterSpecCommon;
import oracle.kubernetes.weblogic.domain.model.ClusterSpecCommonImpl;
import oracle.kubernetes.weblogic.domain.model.Domain;
import oracle.kubernetes.weblogic.domain.model.DomainValidationMessages;
import oracle.kubernetes.weblogic.domain.model.FluentdSpecification;
import oracle.kubernetes.weblogic.domain.model.IntrospectorJobEnvVars;
import oracle.kubernetes.weblogic.domain.model.KubernetesResourceLookup;
import oracle.kubernetes.weblogic.domain.model.ManagedServer;
import oracle.kubernetes.weblogic.domain.model.ManagedServerSpecCommonImpl;
import oracle.kubernetes.weblogic.domain.model.Model;
import oracle.kubernetes.weblogic.domain.model.ProbeTuning;
import oracle.kubernetes.weblogic.domain.model.ServerEnvVars;
import oracle.kubernetes.weblogic.domain.model.ServerSpec;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;

import static java.lang.System.lineSeparator;
import static java.util.stream.Collectors.toSet;
import static oracle.kubernetes.operator.KubernetesConstants.WLS_CONTAINER_NAME;
import static oracle.kubernetes.operator.ProcessingConstants.DOMAIN_COMPONENT_NAME;
import static oracle.kubernetes.operator.helpers.LegalNames.LEGAL_DNS_LABEL_NAME_MAX_LENGTH;
import static oracle.kubernetes.operator.helpers.PodHelper.hasClusterNameOrNull;
import static oracle.kubernetes.operator.helpers.PodHelper.isNotAdminServer;
import static oracle.kubernetes.operator.helpers.StepContextConstants.DEFAULT_SUCCESS_THRESHOLD;
import static oracle.kubernetes.weblogic.domain.model.Domain.TOKEN_END_MARKER;
import static oracle.kubernetes.weblogic.domain.model.Domain.TOKEN_START_MARKER;
import static oracle.kubernetes.weblogic.domain.model.Model.DEFAULT_AUXILIARY_IMAGE_MOUNT_PATH;

/**
 * Operator's mapping between custom resource Domain and runtime details about that domain,
 * including the scan and the Pods and Services for servers.
 */
public class DomainPresenceInfo implements PacketComponent {
  private final String namespace;
  private final String domainUid;
  private final AtomicReference<Domain> domain;
  private final AtomicBoolean isDeleting = new AtomicBoolean(false);
  private final AtomicBoolean isPopulated = new AtomicBoolean(false);
  private final AtomicReference<Collection<ServerStartupInfo>> serverStartupInfo;
  private final AtomicReference<Collection<ServerShutdownInfo>> serverShutdownInfo;

  private final ConcurrentMap<String, ServerKubernetesObjects> servers = new ConcurrentHashMap<>();
  private final ConcurrentMap<String, Cluster> clusters = new ConcurrentHashMap<>();
  private final ConcurrentMap<String, V1Service> clusterServices = new ConcurrentHashMap<>();
  private final ConcurrentMap<String, V1PodDisruptionBudget> podDisruptionBudgets = new ConcurrentHashMap<>();
  private final ReadWriteLock webLogicCredentialsSecretLock = new ReentrantReadWriteLock();
  private V1Secret webLogicCredentialsSecret;
  private OffsetDateTime webLogicCredentialsSecretLastSet;
  private String adminServerName;

  private final List<String> validationWarnings = Collections.synchronizedList(new ArrayList<>());
  private Map<String, Step.StepAndPacket> serversToRoll = Collections.emptyMap();

  /**
   * Create presence for a domain.
   *
   * @param domain Domain
   */
  public DomainPresenceInfo(Domain domain) {
    this.domain = new AtomicReference<>(domain);
    this.namespace = domain.getMetadata().getNamespace();
    this.domainUid = domain.getDomainUid();
    this.serverStartupInfo = new AtomicReference<>(null);
    this.serverShutdownInfo = new AtomicReference<>(null);
  }

  /**
   * Create presence for a domain.
   *
   * @param namespace Namespace
   * @param domainUid The unique identifier assigned to the WebLogic domain when it was registered
   */
  public DomainPresenceInfo(String namespace, String domainUid) {
    this.domain = new AtomicReference<>(null);
    this.namespace = namespace;
    this.domainUid = domainUid;
    this.serverStartupInfo = new AtomicReference<>(null);
    this.serverShutdownInfo = new AtomicReference<>(null);
  }

  private static <K, V> boolean removeIfPresentAnd(
      ConcurrentMap<K, V> map, K key, Predicate<? super V> predicateFunction) {
    Objects.requireNonNull(predicateFunction);
    for (V oldValue; (oldValue = map.get(key)) != null; ) {
      if (!predicateFunction.test(oldValue)) {
        return false;
      } else if (map.remove(key, oldValue)) {
        return true;
      }
    }
    return false;
  }

  /**
   * Counts the number of non-clustered servers and servers in the specified cluster that are scheduled.
   * @param clusterName cluster name of the pod server
   * @return Number of scheduled servers
   */
  long getNumScheduledServers(String clusterName) {
    return getServersInNoOtherCluster(clusterName)
            .filter(PodHelper::isScheduled)
            .count();
  }

  /**
   * Counts the number of non-clustered managed servers and managed servers in the specified cluster that are scheduled.
   * @param clusterName cluster name of the pod server
   * @param adminServerName Name of the admin server
   * @return Number of scheduled managed servers
   */
  public long getNumScheduledManagedServers(String clusterName, String adminServerName) {
    return getManagedServersInNoOtherCluster(clusterName, adminServerName)
          .filter(PodHelper::isScheduled)
          .count();
  }

  /**
   * Counts the number of non-clustered servers (including admin) and servers in the specified cluster that are ready.
   * @param clusterName cluster name of the pod server
   * @return Number of ready servers
   */
  long getNumReadyServers(String clusterName) {
    return getServersInNoOtherCluster(clusterName)
            .filter(PodHelper::hasReadyServer)
            .count();
  }

  /**
   * Counts the number of non-clustered managed servers and managed servers in the specified cluster that are ready.
   * @param clusterName cluster name of the pod server
   * @return Number of ready servers
   */
  public long getNumReadyManagedServers(String clusterName, String adminServerName) {
    return getManagedServersInNoOtherCluster(clusterName, adminServerName)
          .filter(PodHelper::hasReadyServer)
          .count();
  }

  @Nonnull
  private Stream<V1Pod> getServersInNoOtherCluster(String clusterName) {
    return getActiveServers().values().stream()
            .map(ServerKubernetesObjects::getPod)
            .map(AtomicReference::get)
            .filter(this::isNotDeletingPod)
            .filter(p -> hasClusterNameOrNull(p, clusterName));
  }

  @Nonnull
  private Stream<V1Pod> getManagedServersInNoOtherCluster(String clusterName, String adminServerName) {
    return getActiveServers().values().stream()
          .map(ServerKubernetesObjects::getPod)
          .map(AtomicReference::get)
          .filter(this::isNotDeletingPod)
          .filter(p -> isNotAdminServer(p, adminServerName))
          .filter(p -> hasClusterNameOrNull(p, clusterName));
  }

  private boolean isNotDeletingPod(@Nullable V1Pod pod) {
    return Optional.ofNullable(pod).map(V1Pod::getMetadata).map(V1ObjectMeta::getDeletionTimestamp).isEmpty();
  }

  public void setServerService(String serverName, V1Service service) {
    getSko(serverName).getService().set(service);
  }

  private ServerKubernetesObjects getSko(String serverName) {
    return servers.computeIfAbsent(serverName, (n -> new ServerKubernetesObjects()));
  }

  public V1Service getServerService(String serverName) {
    return getSko(serverName).getService().get();
  }

  V1Service removeServerService(String serverName) {
    return getSko(serverName).getService().getAndSet(null);
  }

  public static Optional<DomainPresenceInfo> fromPacket(Packet packet) {
    return Optional.ofNullable(packet.getSpi(DomainPresenceInfo.class));
  }

  public void addToPacket(Packet packet) {
    packet.getComponents().put(DOMAIN_COMPONENT_NAME, Component.createFor(this));
  }

  public String getAdminServerName() {
    return adminServerName;
  }

  public void setAdminServerName(String adminServerName) {
    this.adminServerName = adminServerName;
  }

  /**
   * Specifies the pod associated with an operator-managed server.
   *
   * @param serverName the name of the server
   * @param pod the pod
   */
  public void setServerPod(String serverName, V1Pod pod) {
    getSko(serverName).getPod().set(pod);
  }

  /**
   * Returns the pod associated with an operator-managed server.
   *
   * @param serverName the name of the server
   * @return the corresponding pod, or null if none exists
   */
  public V1Pod getServerPod(String serverName) {
    return getPod(getSko(serverName));
  }

  /**
   * Returns a stream of all server pods present.
   *
   * @return a pod stream
   */
  public Stream<V1Pod> getServerPods() {
    return getActiveServers().values().stream().map(this::getPod).filter(Objects::nonNull);
  }

  private V1Pod getPod(ServerKubernetesObjects sko) {
    return sko.getPod().get();
  }

  private Boolean getPodIsBeingDeleted(ServerKubernetesObjects sko) {
    return sko.isPodBeingDeleted().get();
  }

  public Boolean isServerPodBeingDeleted(String serverName) {
    return getPodIsBeingDeleted(getSko(serverName));
  }

  public void setServerPodBeingDeleted(String serverName, Boolean isBeingDeleted) {
    getSko(serverName).isPodBeingDeleted().set(isBeingDeleted);
  }

  /**
   * Returns a collection of the names of the active servers.
   */
  public Collection<String> getServerNames() {
    return getActiveServers().keySet();
  }

  private boolean hasDefinedServer(Map.Entry<String, ServerKubernetesObjects> e) {
    return e.getValue().getPod().get() != null;
  }

  /**
   * Applies an add or modify event for a server pod. If the current pod is newer than the one
   * associated with the event, ignores the event.
   *
   * @param serverName the name of the server associated with the event
   * @param event the pod associated with the event
   */
  public void setServerPodFromEvent(String serverName, V1Pod event) {
    updateStatus(serverName, event);
    getSko(serverName).getPod().accumulateAndGet(event, this::getNewerPod);
  }

  /**
   * Applies an add or modify event for a server pod. If the current pod is newer than the one
   * associated with the event, ignores the event.
   *
   * @param serverName the name of the server associated with the event
   * @param event the pod associated with the event
   * @param podPredicate predicate to be applied to the original pod
   * @return boolean result from applying the original pod to the podFunction provided
   */
  public boolean setServerPodFromEvent(String serverName, V1Pod event, @Nonnull Predicate<V1Pod> podPredicate) {
    updateStatus(serverName, event);
    return podPredicate.test(getSko(serverName).getPod().getAndAccumulate(event, this::getNewerPod));
  }

  private void updateStatus(String serverName, V1Pod event) {
    getSko(serverName)
        .getLastKnownStatus()
        .getAndUpdate(
            lastKnownStatus -> {
              LastKnownStatus updatedStatus = lastKnownStatus;
              if (PodHelper.isReady(event)) {
                if (lastKnownStatus == null
                    || !WebLogicConstants.RUNNING_STATE.equals(lastKnownStatus.getStatus())) {
                  updatedStatus = new LastKnownStatus(WebLogicConstants.RUNNING_STATE);
                }
              } else {
                if (lastKnownStatus != null
                    && WebLogicConstants.RUNNING_STATE.equals(lastKnownStatus.getStatus())) {
                  updatedStatus = null;
                }
              }
              return updatedStatus;
            });
  }

  private V1Pod getNewerPod(V1Pod first, V1Pod second) {
    return KubernetesUtils.isFirstNewer(getMetadata(first), getMetadata(second)) ? first : second;
  }

  private V1ObjectMeta getMetadata(V1Pod pod) {
    return pod == null ? null : pod.getMetadata();
  }

  private V1ObjectMeta getMetadata(V1Service service) {
    return service == null ? null : service.getMetadata();
  }

  private V1ObjectMeta getMetadata(V1PodDisruptionBudget pdb) {
    return pdb == null ? null : pdb.getMetadata();
  }

  /**
   * Handles a delete event. If the cached pod is newer than the one associated with the event, ignores the attempt
   * as out-of-date and returns false; otherwise deletes the pod and returns true.
   *
   * @param serverName the server name associated with the pod
   * @param event the pod associated with the delete event
   * @return true if the pod was deleted from the cache.
   */
  public boolean deleteServerPodFromEvent(String serverName, V1Pod event) {
    if (serverName == null) {
      return false;
    }
    ServerKubernetesObjects sko = getSko(serverName);
    V1Pod deletedPod = sko.getPod().getAndAccumulate(event, this::getNewerCurrentOrNull);
    if (deletedPod != null) {
      sko.getLastKnownStatus().set(new LastKnownStatus(WebLogicConstants.SHUTDOWN_STATE));
    }
    return deletedPod != null;
  }

  private V1Pod getNewerCurrentOrNull(V1Pod pod, V1Pod event) {
    return KubernetesUtils.isFirstNewer(getMetadata(pod), getMetadata(event)) ? pod : null;
  }

  /**
   * Computes the result of a delete attempt. If the current service is newer than the one
   * associated with the delete event, returns it; otherwise returns null, thus deleting the value.
   *
   * @param service the current service
   * @param event the service associated with the delete event
   * @return the new value for the service.
   */
  private V1Service getNewerCurrentOrNull(V1Service service, V1Service event) {
    return KubernetesUtils.isFirstNewer(getMetadata(service), getMetadata(event)) ? service : null;
  }

  /**
   * Returns the last status reported for the specified server.
   *
   * @param serverName the name of the server
   * @return the corresponding reported status
   */
  public LastKnownStatus getLastKnownServerStatus(String serverName) {
    return getSko(serverName).getLastKnownStatus().get();
  }

  /**
   * Updates the last status reported for the specified server.
   *
   * @param serverName the name of the server
   * @param status the new status
   */
  public void updateLastKnownServerStatus(String serverName, String status) {
    getSko(serverName)
        .getLastKnownStatus()
        .getAndUpdate(
            lastKnownStatus -> {
              LastKnownStatus updatedStatus = null;
              if (status != null) {
                updatedStatus =
                    (lastKnownStatus != null && status.equals(lastKnownStatus.getStatus()))
                        ? new LastKnownStatus(status, lastKnownStatus.getUnchangedCount() + 1)
                        : new LastKnownStatus(status);
              }
              return updatedStatus;
            });
  }

  /**
   * Applies an add or modify event for a server service. If the current service is newer than the
   * one associated with the event, ignores the event.
   *
   * @param serverName the name of the server associated with the event
   * @param event the service associated with the event
   */
  void setServerServiceFromEvent(String serverName, V1Service event) {
    getSko(serverName).getService().accumulateAndGet(event, this::getNewerService);
  }

  /**
   * Given the service associated with a server service-deleted event, removes the service if it is
   * not older than the one recorded.
   *
   * @param serverName the name of the associated server
   * @param event the service associated with the event
   * @return true if the service was actually removed
   */
  boolean deleteServerServiceFromEvent(String serverName, V1Service event) {
    if (serverName == null) {
      return false;
    }
    V1Service deletedService =
        getSko(serverName).getService().getAndAccumulate(event, this::getNewerCurrentOrNull);
    return deletedService != null;
  }

  void removeClusterService(String clusterName) {
    clusterServices.remove(clusterName);
  }

  public V1Service getClusterService(String clusterName) {
    return clusterServices.get(clusterName);
  }

  void setClusterService(String clusterName, V1Service service) {
    clusterServices.put(clusterName, service);
  }

  void setPodDisruptionBudget(String clusterName, V1PodDisruptionBudget pdb) {
    podDisruptionBudgets.put(clusterName, pdb);
  }

  public V1PodDisruptionBudget getPodDisruptionBudget(String clusterName) {
    return podDisruptionBudgets.get(clusterName);
  }

  void removePodDisruptionBudget(String clusterName) {
    podDisruptionBudgets.remove(clusterName);
  }

  /**
   * Applies an add or modify event for a pod disruption budget. If the current pod disruption budget is newer than the
   * one associated with the event, ignores the event.
   *
   * @param clusterName the name of the cluster associated with the event
   * @param event the pod disruption budget associated with the event
   */
  void setPodDisruptionBudgetFromEvent(String clusterName, V1PodDisruptionBudget event) {
    if (clusterName == null) {
      return;
    }
    podDisruptionBudgets.compute(clusterName, (k, s) -> getNewerPDB(s, event));
  }

  /**
   * Given the pod disruption budget associated with a cluster pdb-deleted event, removes the pod disruption budget if
   * it is not older than the one recorded.
   *
   * @param clusterName the name of the associated cluster
   * @param event the pod disruption budget associated with the event
   * @return true if the pod disruption budget was actually removed
   */
  boolean deletePodDisruptionBudgetFromEvent(String clusterName, V1PodDisruptionBudget event) {
    return removeIfPresentAnd(
            podDisruptionBudgets,
            clusterName,
            s -> !KubernetesUtils.isFirstNewer(getMetadata(s), getMetadata(event)));
  }

  void setClusterServiceFromEvent(String clusterName, V1Service event) {
    if (clusterName == null) {
      return;
    }

    clusterServices.compute(clusterName, (k, s) -> getNewerService(s, event));
  }

  boolean deleteClusterServiceFromEvent(String clusterName, V1Service event) {
    return removeIfPresentAnd(
        clusterServices,
        clusterName,
        s -> !KubernetesUtils.isFirstNewer(getMetadata(s), getMetadata(event)));
  }

  /**
   * Retrieve the WebLogic credentials secret, if cached. This cached value will be automatically cleared
   * after a configured time period.
   * @return Cached secret value
   */
  public V1Secret getWebLogicCredentialsSecret() {
    webLogicCredentialsSecretLock.readLock().lock();
    try {
      if (webLogicCredentialsSecretLastSet == null
          || webLogicCredentialsSecretLastSet.isAfter(
              SystemClock.now().minusSeconds(getWeblogicCredentialsSecretRereadIntervalSeconds()))) {
        return webLogicCredentialsSecret;
      }
    } finally {
      webLogicCredentialsSecretLock.readLock().unlock();
    }

    // time to clear
    setWebLogicCredentialsSecret(null);
    return null;
  }

  private int getWeblogicCredentialsSecretRereadIntervalSeconds() {
    return TuningParameters.getInstance().getCredentialsSecretRereadIntervalSeconds();
  }

  /**
   * Cache the WebLogic credentials secret.
   * @param webLogicCredentialsSecret Secret value
   */
  public void setWebLogicCredentialsSecret(V1Secret webLogicCredentialsSecret) {
    webLogicCredentialsSecretLock.writeLock().lock();
    try {
      webLogicCredentialsSecretLastSet = (webLogicCredentialsSecret != null) ? SystemClock.now() : null;
      this.webLogicCredentialsSecret = webLogicCredentialsSecret;
    } finally {
      webLogicCredentialsSecretLock.writeLock().unlock();
    }
  }

  private V1Service getNewerService(V1Service first, V1Service second) {
    return KubernetesUtils.isFirstNewer(getMetadata(first), getMetadata(second)) ? first : second;
  }

  private V1PodDisruptionBudget getNewerPDB(V1PodDisruptionBudget first, V1PodDisruptionBudget second) {
    return KubernetesUtils.isFirstNewer(getMetadata(first), getMetadata(second)) ? first : second;
  }

  public V1Service getExternalService(String serverName) {
    return getSko(serverName).getExternalService().get();
  }

  void setExternalService(String serverName, V1Service service) {
    getSko(serverName).getExternalService().set(service);
  }

  void setExternalServiceFromEvent(String serverName, V1Service event) {
    getSko(serverName).getExternalService().accumulateAndGet(event, this::getNewerService);
  }

  boolean deleteExternalServiceFromEvent(String serverName, V1Service event) {
    if (serverName == null) {
      return false;
    }
    V1Service deletedService =
        getSko(serverName)
            .getExternalService()
            .getAndAccumulate(event, this::getNewerCurrentOrNull);
    return deletedService != null;
  }

  public boolean isNotDeleting() {
    return !isDeleting.get();
  }

  public void setDeleting(boolean deleting) {
    isDeleting.set(deleting);
  }

  public boolean isPopulated() {
    return isPopulated.get();
  }

  public void setPopulated(boolean populated) {
    isPopulated.set(populated);
  }

  /**
   * Gets the domain. Except the instance to change frequently based on status updates.
   *
   * @return Domain
   */
  public Domain getDomain() {
    return domain.get();
  }

  /**
   * Sets the domain.
   *
   * @param domain Domain
   */
  public void setDomain(Domain domain) {
    this.domain.set(domain);
  }

  /**
   * Add a cluster.
   *
   * @param cluster cluster
   */
  public void addClusterResource(Cluster cluster) {
    Cluster existing = clusters.get(cluster.getClusterName());
    if (existing == null || isAfter(getCreationTimestamp(cluster), getCreationTimestamp(existing))) {
      clusters.put(cluster.getClusterName(), cluster);
    }

  }

  protected Cluster getClusterResource(String clusterName) {
    return clusters.get(clusterName);
  }

  /**
   * Get ClusterSpec (Cluster configuration) from all clusters defined in the domain.
   * @return list of ClusterSpec's.
   */
  public List<ClusterSpec> getClusterSpecs() {
    List<ClusterSpec> clusterSpecs = clusters.values().stream().map(Cluster::getSpec)
        .collect(Collectors.toList());
    clusterSpecs.addAll(getDomain().getSpec().getClusters());
    return clusterSpecs;
  }

  /**
   * Returns the specification applicable to a particular server/cluster combination.
   *
   * @param serverName  the name of the server
   * @param clusterName the name of the cluster; may be null or empty if no applicable cluster.
   * @return the effective configuration for the server
   */
  public ServerSpec getServer(String serverName, String clusterName) {
    return getServerSpec(serverName, clusterName);
  }

  private static OffsetDateTime getCreationTimestamp(KubernetesObject ko) {
    return Optional.ofNullable(ko)
        .map(KubernetesObject::getMetadata).map(V1ObjectMeta::getCreationTimestamp).orElse(null);
  }

  private static boolean isAfter(OffsetDateTime one, OffsetDateTime two) {
    if (two == null) {
      return true;
    }
    if (one == null) {
      return false;
    }
    return one.isAfter(two);
  }

  /**
   * Gets the Domain UID.
   *
   * @return Domain UID
   */
  public String getDomainUid() {
    return domainUid;
  }

  /**
   * Gets the namespace.
   *
   * @return Namespace
   */
  public String getNamespace() {
    return namespace;
  }

  // Returns a map of the active servers (those with a known running pod).
  private Map<String, ServerKubernetesObjects> getActiveServers() {
    return servers.entrySet().stream()
          .filter(this::hasDefinedServer)
          .collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, Map.Entry::getValue));

  }

  /**
   * Returns a list of server names whose pods are defined and match the specified criteria.
   * @param criteria a function that returns true for the desired pods.
   */
  public List<String> getSelectedActiveServerNames(Predicate<V1Pod> criteria) {
    return servers.entrySet().stream()
          .filter(e -> hasMatchingServer(e, criteria))
          .map(Map.Entry::getKey)
          .collect(Collectors.toList());
  }

  private boolean hasMatchingServer(Map.Entry<String, ServerKubernetesObjects> e, Predicate<V1Pod> criteria) {
    final V1Pod pod = e.getValue().getPod().get();
    return pod != null && criteria.test(pod);
  }

  /**
   * Server startup info.
   *
   * @return Server startup info
   */
  public Collection<ServerStartupInfo> getServerStartupInfo() {
    return Optional.ofNullable(serverStartupInfo.get()).orElse(Collections.emptyList());
  }

  /**
   * Sets server startup info.
   *
   * @param serverStartupInfo Server startup info
   */
  public void setServerStartupInfo(Collection<ServerStartupInfo> serverStartupInfo) {
    this.serverStartupInfo.set(serverStartupInfo);
  }

  /**
   * Sets server shutdown info.
   *
   * @param serverShutdownInfo Server shutdown info
   */
  public void setServerShutdownInfo(Collection<ServerShutdownInfo> serverShutdownInfo) {
    this.serverShutdownInfo.set(serverShutdownInfo);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder("DomainPresenceInfo{");
    Domain d = getDomain();
    if (d != null) {
      sb.append(
          String.format(
              "uid=%s, namespace=%s",
              getDomain().getDomainUid(), getDomain().getMetadata().getNamespace()));
    } else {
      sb.append(", namespace=").append(namespace);
    }
    sb.append("}");

    return sb.toString();
  }

  /**
   * Add validation warnings.
   * @param validationWarning validation warning to be added
   */
  public void addValidationWarning(String validationWarning) {
    validationWarnings.add(validationWarning);
  }

  /**
   * Clear all validation warnings.
   */
  void clearValidationWarnings() {
    validationWarnings.clear();
  }

  /**
   * Return all validation warnings as a String.
   * @return validation warnings as a String, or null if there is no validation warnings
   */
  public String getValidationWarningsAsString() {
    if (validationWarnings.isEmpty()) {
      return null;
    }
    return String.join(lineSeparator(), validationWarnings);
  }

  /**
   * Returns the names of the servers which are supposed to be running.
   */
  public Set<String> getExpectedRunningServers() {
    final Set<String> result = new HashSet<>(getExpectedRunningManagedServers());
    Optional.ofNullable(adminServerName).ifPresent(result::add);
    return result;
  }

  long getNumDeadlineIncreases() {
    return getDomain().getOrCreateStatus().getNumDeadlineIncreases(getDomain().getFailureRetryIntervalSeconds());
  }

  @Nonnull
  private Set<String> getExpectedRunningManagedServers() {
    return getServerStartupInfo().stream().map(ServerStartupInfo::getServerName).collect(Collectors.toSet());
  }

  public Map<String, Step.StepAndPacket> getServersToRoll() {
    return serversToRoll;
  }

  public void setServersToRoll(Map<String, Step.StepAndPacket> serversToRoll) {
    this.serversToRoll = serversToRoll;
  }

  public AdminServerSpec getAdminServerSpec() {
    return getEffectiveConfigurationFactory().getAdminServerSpec();
  }

  public String getRestartVersion() {
    return getDomain().getSpec().getRestartVersion();
  }

  public String getIntrospectVersion() {
    return getDomain().getSpec().getIntrospectVersion();
  }

  public FluentdSpecification getFluentdSpecification() {
    return getDomain().getSpec().getFluentdSpecification();
  }

  /**
   * Returns the specification applicable to a particular server/cluster combination.
   *
   * @param serverName  the name of the server
   * @param clusterName the name of the cluster; may be null or empty if no applicable cluster.
   * @return the effective configuration for the server
   */
  private ServerSpec getServerSpec(String serverName, String clusterName) {
    return getEffectiveConfigurationFactory().getServerSpec(serverName, clusterName);
  }

  /**
   * Returns the specification applicable to a particular cluster.
   *
   * @param clusterName the name of the cluster; may be null or empty if no applicable cluster.
   * @return the effective configuration for the cluster
   */
  public ClusterSpecCommon getClusterSpecCommon(String clusterName) {
    return getEffectiveConfigurationFactory().getClusterSpec(clusterName);
  }

  /**
   * Returns the number of replicas to start for the specified cluster.
   *
   * @param clusterName the name of the cluster
   * @return the result of applying any configurations for this value
   */
  public int getReplicaCount(String clusterName) {
    return getEffectiveConfigurationFactory().getReplicaCount(clusterName);
  }

  public void setReplicaCount(String clusterName, int replicaLimit) {
    getEffectiveConfigurationFactory().setReplicaCount(clusterName, replicaLimit);
  }

  /**
   * Returns the maximum number of unavailable replicas for the specified cluster.
   *
   * @param clusterName the name of the cluster
   * @return the result of applying any configurations for this value
   */
  public int getMaxUnavailable(String clusterName) {
    return getEffectiveConfigurationFactory().getMaxUnavailable(clusterName);
  }

  /**
   * Returns whether the specified cluster is allowed to have replica count below the minimum
   * dynamic cluster size configured in WebLogic domain configuration.
   *
   * @param clusterName the name of the cluster
   * @return whether the specified cluster is allowed to have replica count below the minimum
   *     dynamic cluster size configured in WebLogic domain configuration
   */
  public boolean isAllowReplicasBelowMinDynClusterSize(String clusterName) {
    return getEffectiveConfigurationFactory().isAllowReplicasBelowMinDynClusterSize(clusterName);
  }

  public int getMaxConcurrentStartup(String clusterName) {
    return getEffectiveConfigurationFactory().getMaxConcurrentStartup(clusterName);
  }

  private EffectiveConfigurationFactory getEffectiveConfigurationFactory() {
    return new CommonEffectiveConfigurationFactory();
  }

  public int getMaxConcurrentShutdown(String clusterName) {
    return getEffectiveConfigurationFactory().getMaxConcurrentShutdown(clusterName);
  }

  public boolean isShuttingDown() {
    return getEffectiveConfigurationFactory().isShuttingDown();
  }

  /**
   * Return the names of the exported admin NAPs.
   *
   * @return a list of names; may be empty
   */
  public List<String> getAdminServerChannelNames() {
    return getEffectiveConfigurationFactory().getAdminServerChannelNames();
  }

  /**
   * Get replica count for a specific cluster.
   * @param clusterSpec Cluster specification.
   * @return replica count.
   */
  public int getReplicaCountFor(ClusterSpec clusterSpec) {
    return getEffectiveConfigurationFactory().getReplicaCountFor(clusterSpec);
  }

  /**
   * Returns the value of the maximum server pod ready wait time.
   *
   * @param serverName server name
   * @param clusterName cluster name
   * @return value of the wait time in seconds.
   */
  public Long getMaxReadyWaitTimeSeconds(String serverName, String clusterName) {
    return Optional.ofNullable(getServer(serverName, clusterName))
        .map(ServerSpec::getMaximumReadyWaitTimeSeconds).orElse(1800L);
  }

  /**
   * Returns the minimum number of replicas for the specified cluster.
   *
   * @param clusterName the name of the cluster
   * @return the result of applying any configurations for this value
   */
  public int getMinAvailable(String clusterName) {
    return Math.max(getReplicaCount(clusterName) - getMaxUnavailable(clusterName), 0);
  }


  /** Details about a specific managed server. */
  public static class ServerInfo {
    public final WlsServerConfig serverConfig;
    protected final String clusterName;
    protected final ServerSpec serverSpec;
    protected final boolean isServiceOnly;

    /**
     * Create server info.
     *
     * @param serverConfig Server config scan
     * @param clusterName the name of the cluster
     * @param serverSpec the server startup configuration
     * @param isServiceOnly true, if only the server service should be created
     */
    public ServerInfo(
        @Nonnull WlsServerConfig serverConfig,
        @Nullable String clusterName,
        ServerSpec serverSpec,
        boolean isServiceOnly) {
      this.serverConfig = serverConfig;
      this.clusterName = clusterName;
      this.serverSpec = serverSpec;
      this.isServiceOnly = isServiceOnly;
    }

    public String getName() {
      return this.serverConfig.getName();
    }

    public String getServerName() {
      return serverConfig.getName();
    }

    public String getClusterName() {
      return clusterName;
    }

    public List<V1EnvVar> getEnvironment() {
      return serverSpec == null ? Collections.emptyList() : serverSpec.getEnvironmentVariables();
    }

    /**
     * Create a packet using this server info.
     *
     * @param packet the packet to copy from
     * @return a new packet that is populated with the server info
     */
    public Packet createPacket(Packet packet) {
      Packet p = packet.copy();
      p.put(ProcessingConstants.CLUSTER_NAME, getClusterName());
      p.put(ProcessingConstants.SERVER_NAME, getName());
      p.put(ProcessingConstants.SERVER_SCAN, serverConfig);
      p.put(ProcessingConstants.ENVVARS, getEnvironment());
      return p;
    }

    @Override
    public String toString() {
      return new ToStringBuilder(this)
          .append("serverConfig", serverConfig)
          .append("clusterName", clusterName)
          .append("serverSpec", serverSpec)
          .append("isServiceOnly", isServiceOnly)
          .toString();
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }

      if (o == null || getClass() != o.getClass()) {
        return false;
      }

      ServerInfo that = (ServerInfo) o;

      return new EqualsBuilder()
          .append(serverConfig, that.serverConfig)
          .append(clusterName, that.clusterName)
          .append(serverSpec, that.serverSpec)
          .append(isServiceOnly, that.isServiceOnly)
          .isEquals();
    }

    @Override
    public int hashCode() {
      return new HashCodeBuilder(17, 37)
          .append(serverConfig)
          .append(clusterName)
          .append(serverSpec)
          .append(isServiceOnly)
          .toHashCode();
    }

  }

  /** Details about a specific managed server that will be started up. */
  public static class ServerStartupInfo extends ServerInfo {

    /**
     * Create server startup info.
     *
     * @param serverConfig Server config scan
     * @param clusterName the name of the cluster
     * @param serverSpec the server startup configuration
     */
    public ServerStartupInfo(
        WlsServerConfig serverConfig, String clusterName, ServerSpec serverSpec) {
      super(serverConfig, clusterName, serverSpec, false);
    }
  }

  /** Details about a specific managed server that will be shutdown. */
  public static class ServerShutdownInfo extends ServerInfo {
    /**
     * Create server shutdown info.
     *
     * @param serverName the name of the server to shutdown
     * @param clusterName the name of the cluster
     */
    public ServerShutdownInfo(String serverName, String clusterName) {
      super(new WlsServerConfig(serverName, null, 0), clusterName, null, false);
    }

    /**
     * Create server shutdown info.
     *
     * @param serverConfig Server config scan
     * @param clusterName the name of the cluster
     * @param serverSpec Server specifications
     * @param isServiceOnly If service needs to be preserved
     */
    public ServerShutdownInfo(
            WlsServerConfig serverConfig, String clusterName,
            ServerSpec serverSpec, boolean isServiceOnly) {
      super(serverConfig, clusterName, serverSpec, isServiceOnly);
    }

    public boolean isServiceOnly() {
      return  isServiceOnly;
    }
  }

  class CommonEffectiveConfigurationFactory implements EffectiveConfigurationFactory {
    @Override
    public AdminServerSpec getAdminServerSpec() {
      return getDomain().getAdminServerSpec();
    }

    @Override
    public ServerSpec getServerSpec(String serverName, String clusterName) {
      return Optional.ofNullable(clusterName).map(DomainPresenceInfo.this::getClusterResource)
          .map(c -> getServerSpec(getDomain().getManagedServer(serverName),
              c.getSpec(), getClusterLimit(clusterName)))
          .orElse(getServerSpec(getDomain().getManagedServer(serverName),
              getDomain().getSpec().getCluster(clusterName), getClusterLimit(clusterName)));
    }

    public ServerSpec getServerSpec(ManagedServer server, ClusterSpec clusterSpec, Integer clusterLimit) {
      return new ManagedServerSpecCommonImpl(
          getDomain().getSpec(),
          server,
          clusterSpec,
          clusterLimit);
    }


    private boolean hasReplicaCount(ClusterSpec clusterspec) {
      return clusterspec != null && clusterspec.getReplicas() != null;
    }

    private boolean hasMaxUnavailable(ClusterSpec clusterSpec) {
      return clusterSpec != null && clusterSpec.getMaxUnavailable() != null;
    }

    private boolean hasAllowReplicasBelowMinDynClusterSize(ClusterSpec clusterSpec) {
      return clusterSpec != null && clusterSpec.isAllowReplicasBelowMinDynClusterSize() != null;
    }

    private boolean isAllowReplicasBelowDynClusterSizeFor(ClusterSpec clusterSpec) {
      return hasAllowReplicasBelowMinDynClusterSize(clusterSpec)
          ? clusterSpec.isAllowReplicasBelowMinDynClusterSize()
          : getDomain().getSpec().isAllowReplicasBelowMinDynClusterSize();
    }

    private int getMaxConcurrentStartupFor(ClusterSpec cluster) {
      return hasMaxConcurrentStartup(cluster)
          ? cluster.getMaxConcurrentStartup()
          : getDomain().getSpec().getMaxClusterConcurrentStartup();
    }

    private boolean hasMaxConcurrentStartup(ClusterSpec clusterSpec) {
      return clusterSpec != null && clusterSpec.getMaxConcurrentStartup() != null;
    }

    private int getMaxConcurrentShutdownFor(ClusterSpec cluster) {
      return Optional.ofNullable(cluster).map(ClusterSpec::getMaxConcurrentShutdown)
          .orElse(getDomain().getSpec().getMaxClusterConcurrentShutdown());
    }


    /**
     * Get replica count for a specific cluster.
     * @param cluster Cluster specification.
     * @return replica count.
     */
    public int getReplicaCountFor(ClusterSpec cluster) {
      return hasReplicaCount(cluster)
          ? cluster.getReplicas()
          : Optional.ofNullable(getDomain().getSpec().getReplicas()).orElse(0);
    }

    @Override
    public ClusterSpecCommon getClusterSpec(String clusterName) {
      ClusterSpec clusterSpec = Optional.ofNullable(getClusterResource(clusterName)).map(Cluster::getSpec)
          .orElse(getDomain().getSpec().getCluster(clusterName));
      return new ClusterSpecCommonImpl(getDomain().getSpec(), clusterSpec);
    }

    private Integer getClusterLimit(String clusterName) {
      return clusterName == null ? null : getReplicaCount(clusterName);
    }

    @Override
    public boolean isShuttingDown() {
      return getAdminServerSpec().isShuttingDown();
    }

    @Override
    public int getReplicaCount(String clusterName) {
      return Optional.ofNullable(clusterName).map(DomainPresenceInfo.this::getClusterResource)
          .map(c -> getReplicaCountFor(c.getSpec()))
          .orElse(getReplicaCountFor(getDomain().getSpec().getCluster(clusterName)));
    }

    @Override
    public void setReplicaCount(String clusterName, int replicaCount) {
      Optional.ofNullable(clusterName).map(DomainPresenceInfo.this::getClusterResource)
          .ifPresentOrElse(c -> c.setReplicas(replicaCount),
              () -> getOrCreateCluster(clusterName).setReplicas(replicaCount));
    }

    @Override
    public int getMaxUnavailable(String clusterName) {
      return Optional.ofNullable(clusterName).map(DomainPresenceInfo.this::getClusterResource)
          .map(c -> getMaxUnavailableFor(c.getSpec()))
          .orElse(getMaxUnavailableFor(getDomain().getSpec().getCluster(clusterName)));
    }

    private int getMaxUnavailableFor(ClusterSpec cluster) {
      return hasMaxUnavailable(cluster) ? cluster.getMaxUnavailable() : 1;
    }

    @Override
    public List<String> getAdminServerChannelNames() {
      return getDomain().getAdminServerChannelNames();
    }

    @Override
    public boolean isAllowReplicasBelowMinDynClusterSize(String clusterName) {
      return Optional.ofNullable(clusterName).map(DomainPresenceInfo.this::getClusterResource)
          .map(c -> isAllowReplicasBelowDynClusterSizeFor(c.getSpec()))
          .orElse(isAllowReplicasBelowDynClusterSizeFor(getDomain().getSpec().getCluster(clusterName)));
    }

    @Override
    public int getMaxConcurrentStartup(String clusterName) {
      return Optional.ofNullable(clusterName).map(DomainPresenceInfo.this::getClusterResource)
          .map(c -> getMaxConcurrentStartupFor(c.getSpec()))
          .orElse(getMaxConcurrentStartupFor(getDomain().getSpec().getCluster(clusterName)));
    }

    @Override
    public int getMaxConcurrentShutdown(String clusterName) {
      return Optional.ofNullable(clusterName).map(DomainPresenceInfo.this::getClusterResource)
          .map(c -> getMaxConcurrentShutdownFor(c.getSpec()))
          .orElse(getMaxConcurrentShutdownFor(getDomain().getSpec().getCluster(clusterName)));
    }

    private ClusterSpec getOrCreateCluster(String clusterName) {
      ClusterSpec cluster = getDomain().getSpec().getCluster(clusterName);
      if (cluster != null) {
        return cluster;
      }

      return createClusterWithName(clusterName);
    }

    private ClusterSpec createClusterWithName(String clusterName) {
      ClusterSpec cluster = new ClusterSpec().withClusterName(clusterName);
      getDomain().getSpec().withCluster(cluster);
      return cluster;
    }
  }

  public List<String> getValidationFailures(KubernetesResourceLookup kubernetesResources) {
    return new Validator().getValidationFailures(kubernetesResources);
  }

  public List<String> getAdditionalValidationFailures(V1PodSpec podSpec) {
    return new Validator().getAdditionalValidationFailures(podSpec);
  }

  class Validator {
    public static final String ADMIN_SERVER_POD_SPEC_PREFIX = "spec.adminServer.serverPod";
    public static final String CLUSTER_SPEC_PREFIX = "spec.clusters";
    public static final String MS_SPEC_PREFIX = "spec.managedServers";
    private final List<String> failures = new ArrayList<>();
    private final Set<String> clusterNames = new HashSet<>();
    private final Set<String> serverNames = new HashSet<>();

    List<String> getValidationFailures(KubernetesResourceLookup kubernetesResources) {
      addDuplicateNames();
      addInvalidMountPaths();
      addUnmappedLogHome();
      addReservedEnvironmentVariables();
      addMissingSecrets(kubernetesResources);
      addIllegalSitConfigForMii();
      verifyNoAlternateSecretNamespaceSpecified();
      addMissingModelConfigMap(kubernetesResources);
      verifyIstioExposingDefaultChannel();
      verifyIntrospectorJobName();
      verifyLivenessProbeSuccessThreshold();
      verifyContainerNameValidInPodSpec();
      verifyContainerPortNameValidInPodSpec();
      verifyModelHomeNotInWDTInstallHome();
      verifyWDTInstallHomeNotInModelHome();
      whenAuxiliaryImagesDefinedVerifyMountPathNotInUse();
      whenAuxiliaryImagesDefinedVerifyOnlyOneImageSetsSourceWDTInstallHome();
      return failures;
    }

    private void verifyModelHomeNotInWDTInstallHome() {
      if (getDomain().getSpec().getWdtInstallHome().contains(getDomain().getSpec().getModelHome())) {
        failures.add(DomainValidationMessages
            .invalidWdtInstallHome(getDomain().getSpec().getWdtInstallHome(), getDomain().getSpec().getModelHome()));
      }
    }

    private void verifyWDTInstallHomeNotInModelHome() {
      if (getDomain().getSpec().getModelHome().contains(getDomain().getSpec().getWdtInstallHome())) {
        failures.add(DomainValidationMessages
            .invalidModelHome(getDomain().getSpec().getWdtInstallHome(), getDomain().getSpec().getModelHome()));
      }
    }

    private void verifyIntrospectorJobName() {
      // K8S adds a 5 character suffix to an introspector job name
      if (LegalNames.toJobIntrospectorName(getDomainUid()).length()
          > LEGAL_DNS_LABEL_NAME_MAX_LENGTH - 5) {
        failures.add(DomainValidationMessages.exceedMaxIntrospectorJobName(
            getDomainUid(),
            LegalNames.toJobIntrospectorName(getDomainUid()),
            LEGAL_DNS_LABEL_NAME_MAX_LENGTH - 5));
      }
    }

    List<String> getAdditionalValidationFailures(V1PodSpec podSpec) {
      addInvalidMountPathsForPodSpec(podSpec);
      return failures;
    }

    private void addDuplicateNames() {
      getDomain().getSpec().getManagedServers()
          .stream()
          .map(ManagedServer::getServerName)
          .map(LegalNames::toDns1123LegalName)
          .forEach(this::checkDuplicateServerName);
      getClusterSpecs()
          .stream()
          .map(ClusterSpec::getClusterName)
          .map(LegalNames::toDns1123LegalName)
          .forEach(this::checkDuplicateClusterName);
    }

    private void checkDuplicateServerName(String serverName) {
      if (serverNames.contains(serverName)) {
        failures.add(DomainValidationMessages.duplicateServerName(serverName));
      } else {
        serverNames.add(serverName);
      }
    }

    private void checkDuplicateClusterName(String clusterName) {
      if (clusterNames.contains(clusterName)) {
        failures.add(DomainValidationMessages.duplicateClusterName(clusterName));
      } else {
        clusterNames.add(clusterName);
      }
    }

    private void addInvalidMountPaths() {
      getDomain().getSpec().getAdditionalVolumeMounts().forEach(this::checkValidMountPath);
      if (getDomain().getSpec().getAdminServer() != null) {
        getDomain().getSpec().getAdminServer().getAdditionalVolumeMounts().forEach(this::checkValidMountPath);
      }
      if (getClusterSpecs() != null) {
        getClusterSpecs().forEach(
            cluster -> cluster.getAdditionalVolumeMounts().forEach(this::checkValidMountPath));
      }
    }

    private void addInvalidMountPathsForPodSpec(V1PodSpec podSpec) {
      podSpec.getContainers()
          .forEach(container ->
              Optional.ofNullable(container.getVolumeMounts())
                  .ifPresent(volumes -> volumes.forEach(this::checkValidMountPath)));
    }

    private void checkValidMountPath(V1VolumeMount mount) {
      if (skipValidation(mount.getMountPath())) {
        return;
      }

      if (!new File(mount.getMountPath()).isAbsolute()) {
        failures.add(DomainValidationMessages.badVolumeMountPath(mount));
      }
    }

    private boolean skipValidation(String mountPath) {
      StringTokenizer nameList = new StringTokenizer(mountPath, TOKEN_START_MARKER);
      if (!nameList.hasMoreElements()) {
        return false;
      }
      while (nameList.hasMoreElements()) {
        String token = nameList.nextToken();
        if (noMatchingEnvVarName(getEnvNames(), token)) {
          return false;
        }
      }
      return true;
    }

    private void whenAuxiliaryImagesDefinedVerifyMountPathNotInUse() {
      getAdminServerSpec().getAdditionalVolumeMounts().forEach(this::verifyMountPathForAuxiliaryImagesNotUsed);
      getClusterSpecs().forEach(cluster ->
          cluster.getAdditionalVolumeMounts().forEach(this::verifyMountPathForAuxiliaryImagesNotUsed));
      getDomain().getSpec().getManagedServers().forEach(managedServer ->
          managedServer.getAdditionalVolumeMounts().forEach(this::verifyMountPathForAuxiliaryImagesNotUsed));
    }

    private void verifyMountPathForAuxiliaryImagesNotUsed(V1VolumeMount volumeMount) {
      Optional.ofNullable(getDomain().getSpec().getModel()).map(Model::getAuxiliaryImages)
          .ifPresent(ai -> {
            if (volumeMount.getMountPath().equals(DEFAULT_AUXILIARY_IMAGE_MOUNT_PATH)) {
              failures.add(DomainValidationMessages.mountPathForAuxiliaryImageAlreadyInUse());
            }
          });
    }

    private void whenAuxiliaryImagesDefinedVerifyOnlyOneImageSetsSourceWDTInstallHome() {
      Optional.ofNullable(getDomain().getSpec().getModel()).map(Model::getAuxiliaryImages).ifPresent(
          this::verifyWDTInstallHome);
    }

    private void verifyWDTInstallHome(List<AuxiliaryImage> auxiliaryImages) {
      if (auxiliaryImages.stream().filter(this::isWDTInstallHomeSetAndNotNone).count() > 1) {
        failures.add(DomainValidationMessages.moreThanOneAuxiliaryImageConfiguredWDTInstallHome());
      }
    }

    private boolean isWDTInstallHomeSetAndNotNone(AuxiliaryImage ai) {
      return ai.getSourceWDTInstallHome() != null && !"None".equalsIgnoreCase(ai.getSourceWDTInstallHomeOrDefault());
    }

    private void verifyLivenessProbeSuccessThreshold() {
      Optional.of(getAdminServerSpec().getLivenessProbe())
          .ifPresent(probe -> verifySuccessThresholdValue(probe, ADMIN_SERVER_POD_SPEC_PREFIX
              + ".livenessProbe.successThreshold"));
      getClusterSpecs().forEach(cluster ->
          Optional.ofNullable(cluster.getLivenessProbe())
              .ifPresent(probe -> verifySuccessThresholdValue(probe, CLUSTER_SPEC_PREFIX + "["
                  + cluster.getClusterName() + "].serverPod.livenessProbe.successThreshold")));
      getDomain().getSpec().getManagedServers().forEach(managedServer ->
          Optional.ofNullable(managedServer.getLivenessProbe())
              .ifPresent(probe -> verifySuccessThresholdValue(probe, MS_SPEC_PREFIX + "["
                  + managedServer.getServerName() + "].serverPod.livenessProbe.successThreshold")));
    }

    private void verifySuccessThresholdValue(ProbeTuning probe, String prefix) {
      if (probe.getSuccessThreshold() != null && probe.getSuccessThreshold() != DEFAULT_SUCCESS_THRESHOLD) {
        failures.add(DomainValidationMessages.invalidLivenessProbeSuccessThresholdValue(
            probe.getSuccessThreshold(), prefix));
      }
    }

    private void verifyContainerNameValidInPodSpec() {
      getAdminServerSpec().getContainers().forEach(container ->
          isContainerNameReserved(container, ADMIN_SERVER_POD_SPEC_PREFIX + ".containers"));
      getClusterSpecs().forEach(cluster ->
          cluster.getContainers().forEach(container ->
              isContainerNameReserved(container, CLUSTER_SPEC_PREFIX + "[" + cluster.getClusterName()
                  + "].serverPod.containers")));
      getDomain().getSpec().getManagedServers().forEach(managedServer ->
          managedServer.getContainers().forEach(container ->
              isContainerNameReserved(container, MS_SPEC_PREFIX + "[" + managedServer.getServerName()
                  + "].serverPod.containers")));
    }

    private void isContainerNameReserved(V1Container container, String prefix) {
      if (container.getName().equals(WLS_CONTAINER_NAME)) {
        failures.add(DomainValidationMessages.reservedContainerName(container.getName(), prefix));
      }
    }

    private void verifyContainerPortNameValidInPodSpec() {
      getAdminServerSpec().getContainers().forEach(container ->
          areContainerPortNamesValid(container, ADMIN_SERVER_POD_SPEC_PREFIX + ".containers"));
      getClusterSpecs().forEach(cluster ->
          cluster.getContainers().forEach(container ->
              areContainerPortNamesValid(container, CLUSTER_SPEC_PREFIX + "[" + cluster.getClusterName()
                  + "].serverPod.containers")));
      getDomain().getSpec().getManagedServers().forEach(managedServer ->
          managedServer.getContainers().forEach(container ->
              areContainerPortNamesValid(container, MS_SPEC_PREFIX + "[" + managedServer.getServerName()
                  + "].serverPod.containers")));
    }

    private void areContainerPortNamesValid(V1Container container, String prefix) {
      Optional.ofNullable(container.getPorts()).ifPresent(portList ->
          portList.forEach(port -> checkPortNameLength(port, container.getName(), prefix)));
    }

    private void checkPortNameLength(V1ContainerPort port, String name, String prefix) {
      if (port.getName().length() > LegalNames.LEGAL_CONTAINER_PORT_NAME_MAX_LENGTH) {
        failures.add(DomainValidationMessages.exceedMaxContainerPortName(
            getDomainUid(),
            prefix + "." + name,
            port.getName()));
      }
    }

    @Nonnull
    private Set<String> getEnvNames() {
      return Optional.ofNullable(getDomain().getSpec().getEnv()).stream()
          .flatMap(Collection::stream)
          .map(V1EnvVar::getName)
          .collect(toSet());
    }

    private boolean noMatchingEnvVarName(Set<String> varNames, String token) {
      int index = token.indexOf(TOKEN_END_MARKER);
      if (index != -1) {
        String str = token.substring(0, index);
        // IntrospectorJobEnvVars.isReserved() checks env vars in ServerEnvVars too
        return !varNames.contains(str) && !IntrospectorJobEnvVars.isReserved(str);
      }
      return true;
    }

    private void addUnmappedLogHome() {
      if (!getDomain().isLogHomeEnabled()) {
        return;
      }

      if (getDomain().getSpec().getAdditionalVolumeMounts().stream()
          .map(V1VolumeMount::getMountPath)
          .noneMatch(this::mapsLogHome)) {
        failures.add(DomainValidationMessages.logHomeNotMounted(getDomain().getLogHome()));
      }
    }

    private boolean mapsLogHome(String mountPath) {
      return getDomain().getLogHome().startsWith(separatorTerminated(mountPath));
    }

    private String separatorTerminated(String path) {
      if (path.endsWith(File.separator)) {
        return path;
      } else {
        return path + File.separator;
      }
    }

    private void addIllegalSitConfigForMii() {
      if (getDomain().getDomainHomeSourceType() == DomainSourceType.FROM_MODEL
          && getDomain().getConfigOverrides() != null) {
        failures.add(DomainValidationMessages.illegalSitConfigForMii(getDomain().getConfigOverrides()));
      }
    }

    private void verifyIstioExposingDefaultChannel() {
      if (getDomain().getSpec().isIstioEnabled()) {
        Optional.ofNullable(getDomain().getSpec().getAdminServer())
            .map(AdminServer::getAdminService)
            .map(AdminService::getChannels)
            .ifPresent(cs -> cs.forEach(this::checkForDefaultNameExposed));
      }
    }

    private void checkForDefaultNameExposed(Channel channel) {
      if ("default".equals(channel.getChannelName()) || "default-admin".equals(channel.getChannelName())
          || "default-secure".equals(channel.getChannelName())) {
        failures.add(DomainValidationMessages.cannotExposeDefaultChannelIstio(channel.getChannelName()));
      }
    }

    private void addReservedEnvironmentVariables() {
      checkReservedIntrospectorVariables(getDomain().getSpec(), "spec");
      Optional.ofNullable(getDomain().getSpec().getAdminServer())
          .ifPresent(a -> checkReservedIntrospectorVariables(a, "spec.adminServer"));

      getDomain().getSpec().getManagedServers()
          .forEach(s -> checkReservedEnvironmentVariables(s, "spec.managedServers[" + s.getServerName() + "]"));
      getClusterSpecs()
          .forEach(s -> checkReservedEnvironmentVariables(s, "spec.clusters[" + s.getClusterName() + "]"));
    }

    class EnvironmentVariableCheck {
      private final Predicate<String> isReserved;

      EnvironmentVariableCheck(Predicate<String> isReserved) {
        this.isReserved = isReserved;
      }

      void checkEnvironmentVariables(@Nonnull BaseConfiguration configuration, String prefix) {
        if (configuration.getEnv() == null) {
          return;
        }

        List<String> reservedNames = configuration.getEnv()
            .stream()
            .map(V1EnvVar::getName)
            .filter(isReserved)
            .collect(Collectors.toList());

        if (!reservedNames.isEmpty()) {
          failures.add(DomainValidationMessages.reservedVariableNames(prefix, reservedNames));
        }
      }
    }

    private void checkReservedEnvironmentVariables(BaseConfiguration configuration, String prefix) {
      new EnvironmentVariableCheck(ServerEnvVars::isReserved).checkEnvironmentVariables(configuration, prefix);
    }

    @SuppressWarnings("SameParameterValue")
    private void checkReservedIntrospectorVariables(BaseConfiguration configuration, String prefix) {
      new EnvironmentVariableCheck(IntrospectorJobEnvVars::isReserved).checkEnvironmentVariables(configuration, prefix);
    }

    private void addMissingSecrets(KubernetesResourceLookup resourceLookup) {
      verifySecretExists(resourceLookup, getDomain().getWebLogicCredentialsSecretName(),
          SecretType.WEBLOGIC_CREDENTIALS);
      for (V1LocalObjectReference reference : getImagePullSecrets()) {
        verifySecretExists(resourceLookup, reference.getName(), SecretType.IMAGE_PULL);
      }
      for (String secretName : getDomain().getConfigOverrideSecrets()) {
        verifySecretExists(resourceLookup, secretName, SecretType.CONFIG_OVERRIDE);
      }

      verifySecretExists(resourceLookup, getDomain().getOpssWalletPasswordSecret(), SecretType.OPSS_WALLET_PASSWORD);
      verifySecretExists(resourceLookup, getDomain().getOpssWalletFileSecret(), SecretType.OPSS_WALLET_FILE);

      if (getDomain().getDomainHomeSourceType() == DomainSourceType.FROM_MODEL) {
        if (getDomain().getRuntimeEncryptionSecret() == null) {
          failures.add(DomainValidationMessages.missingRequiredSecret(
              "spec.configuration.model.runtimeEncryptionSecret"));
        } else {
          verifySecretExists(resourceLookup, getDomain().getRuntimeEncryptionSecret(), SecretType.RUNTIME_ENCRYPTION);
        }
        if (ModelInImageDomainType.JRF.equals(getDomain().getWdtDomainType())
            && getDomain().getOpssWalletPasswordSecret() == null) {
          failures.add(DomainValidationMessages.missingRequiredOpssSecret(
              "spec.configuration.opss.walletPasswordSecret"));
        }
      }

      if (getFluentdSpecification() != null && getFluentdSpecification().getElasticSearchCredentials() == null) {
        failures.add(DomainValidationMessages.missingRequiredFluentdSecret(
            "spec.fluentdSpecification.elasticSearchCredentials"));
      }
    }

    private List<V1LocalObjectReference> getImagePullSecrets() {
      return getDomain().getSpec().getImagePullSecrets();
    }

    @SuppressWarnings("SameParameterValue")
    private void verifySecretExists(KubernetesResourceLookup resources, String secretName, SecretType type) {
      if (secretName != null && !resources.isSecretExists(secretName, getNamespace())) {
        failures.add(DomainValidationMessages.noSuchSecret(secretName, getNamespace(), type));
      }
    }

    private void verifyNoAlternateSecretNamespaceSpecified() {
      if (!getSpecifiedWebLogicCredentialsNamespace().equals(getNamespace())) {
        failures.add(DomainValidationMessages.illegalSecretNamespace(getSpecifiedWebLogicCredentialsNamespace()));
      }
    }

    private String getSpecifiedWebLogicCredentialsNamespace() {
      return Optional.ofNullable(getDomain().getSpec().getWebLogicCredentialsSecret())
          .map(V1SecretReference::getNamespace)
          .orElse(getNamespace());
    }

    private void addMissingModelConfigMap(KubernetesResourceLookup resourceLookup) {
      verifyModelConfigMapExists(resourceLookup, getDomain().getWdtConfigMap());
    }

    @SuppressWarnings("SameParameterValue")
    private void verifyModelConfigMapExists(KubernetesResourceLookup resources, String modelConfigMapName) {
      if (getDomain().getDomainHomeSourceType() == DomainSourceType.FROM_MODEL
          && modelConfigMapName != null && !resources.isConfigMapExists(modelConfigMapName, getNamespace())) {
        failures.add(DomainValidationMessages.noSuchModelConfigMap(modelConfigMapName, getNamespace()));
      }
    }

  }
}

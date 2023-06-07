// Copyright (c) 2018, 2023, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helpers;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
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

import io.kubernetes.client.openapi.models.V1EnvVar;
import io.kubernetes.client.openapi.models.V1LocalObjectReference;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1PersistentVolumeClaim;
import io.kubernetes.client.openapi.models.V1Pod;
import io.kubernetes.client.openapi.models.V1PodDisruptionBudget;
import io.kubernetes.client.openapi.models.V1Secret;
import io.kubernetes.client.openapi.models.V1Service;
import oracle.kubernetes.operator.MakeRightDomainOperation;
import oracle.kubernetes.operator.ProcessingConstants;
import oracle.kubernetes.operator.WebLogicConstants;
import oracle.kubernetes.operator.logging.ThreadLoggingContext;
import oracle.kubernetes.operator.processing.EffectiveClusterSpec;
import oracle.kubernetes.operator.processing.EffectiveServerSpec;
import oracle.kubernetes.operator.tuning.TuningParameters;
import oracle.kubernetes.operator.wlsconfig.WlsServerConfig;
import oracle.kubernetes.operator.work.Component;
import oracle.kubernetes.operator.work.Packet;
import oracle.kubernetes.operator.work.Step;
import oracle.kubernetes.utils.SystemClock;
import oracle.kubernetes.weblogic.domain.model.ClusterResource;
import oracle.kubernetes.weblogic.domain.model.ClusterSpec;
import oracle.kubernetes.weblogic.domain.model.ClusterStatus;
import oracle.kubernetes.weblogic.domain.model.DomainResource;
import oracle.kubernetes.weblogic.domain.model.DomainSpec;
import oracle.kubernetes.weblogic.domain.model.DomainStatus;
import oracle.kubernetes.weblogic.domain.model.PrivateDomainApi;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.jetbrains.annotations.NotNull;

import static oracle.kubernetes.operator.helpers.PodHelper.hasClusterNameOrNull;
import static oracle.kubernetes.operator.helpers.PodHelper.isNotAdminServer;

/**
 * Operator's mapping between custom resource Domain and runtime details about that domain,
 * including the scan and the Pods and Services for servers.
 */
public class DomainPresenceInfo extends ResourcePresenceInfo {

  private static final String COMPONENT_KEY = "dpi";
  private final String domainUid;
  private final AtomicReference<DomainResource> domain;
  private final AtomicBoolean isDeleting = new AtomicBoolean(false);
  private final AtomicBoolean isPopulated = new AtomicBoolean(false);
  private final AtomicReference<Collection<ServerStartupInfo>> serverStartupInfo;
  private final AtomicReference<Collection<ServerShutdownInfo>> serverShutdownInfo;

  private final ConcurrentMap<String, ServerKubernetesObjects> servers = new ConcurrentHashMap<>();
  private final ConcurrentMap<String, ClusterResource> clusters = new ConcurrentHashMap<>();
  private final ConcurrentMap<String, V1Service> clusterServices = new ConcurrentHashMap<>();
  private final ConcurrentMap<String, V1PodDisruptionBudget> podDisruptionBudgets = new ConcurrentHashMap<>();
  private final ConcurrentMap<String, V1PersistentVolumeClaim> persistentVolumeClaims = new ConcurrentHashMap<>();
  private final ReadWriteLock webLogicCredentialsSecretLock = new ReentrantReadWriteLock();
  private V1Secret webLogicCredentialsSecret;
  private OffsetDateTime webLogicCredentialsSecretLastSet;
  private String adminServerName;

  private final List<String> validationWarnings = Collections.synchronizedList(new ArrayList<>());
  private final List<String> serverNamesFromPodList = Collections.synchronizedList(new ArrayList<>());
  private Map<String, Step.StepAndPacket> serversToRoll = Collections.emptyMap();

  /**
   * Create presence for a domain.
   *
   * @param domain Domain
   */
  public DomainPresenceInfo(DomainResource domain) {
    super(domain.getMetadata().getNamespace());
    this.domain = new AtomicReference<>(domain);
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
    super(namespace);
    this.domain = new AtomicReference<>(null);
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
   * Returns true if the domain in this presence info has a later generation
   * than the passed-in cached info.
   * @param cachedInfo another presence info against which to compare this one.
   */
  public boolean isDomainGenerationChanged(DomainPresenceInfo cachedInfo) {
    return getGeneration(getDomain()).compareTo(getGeneration(cachedInfo.getDomain())) > 0;
  }

  /**
   * Returns true if the state of the current domain presence info, when compared with the cached info for the same
   * domain, indicates that the make-right should not be run. The user has a number of options to resume processing
   * the domain.
   * @param cachedInfo the version of the domain presence info previously processed.
   */
  public boolean isDomainProcessingHalted(DomainPresenceInfo cachedInfo) {
    return isDomainProcessingAborted() && versionsUnchanged(cachedInfo);
  }

  private boolean isDomainProcessingAborted() {
    return Optional.ofNullable(getDomain())
            .map(DomainResource::getStatus)
            .map(DomainStatus::isAborted)
            .orElse(false);
  }

  private boolean versionsUnchanged(DomainPresenceInfo cachedInfo) {
    return hasSameIntrospectVersion(cachedInfo)
        && hasSameRestartVersion(cachedInfo)
        && hasSameIntrospectImage(cachedInfo);
  }

  /**
   * Returns true if the make-right operation was triggered by a domain event and the reported domain
   * is older than the value already cached. That indicates that the event is old and should be ignored.
   * @param operation the make-right operation.
   * @param cachedInfo the cached domain presence info.
   */
  public boolean isFromOutOfDateEvent(MakeRightDomainOperation operation, DomainPresenceInfo cachedInfo) {
    return operation.hasEventData() && !isNewerThan(cachedInfo);
  }

  private boolean isNewerThan(DomainPresenceInfo cachedInfo) {
    return getDomain() == null
        || !KubernetesUtils.isFirstNewer(cachedInfo.getDomain().getMetadata(), getDomain().getMetadata());
  }

  private boolean hasSameIntrospectVersion(DomainPresenceInfo cachedInfo) {
    return Objects.equals(getIntrospectVersion(), cachedInfo.getIntrospectVersion());
  }

  private String getIntrospectVersion() {
    return Optional.ofNullable(getDomain())
        .map(DomainResource::getSpec)
        .map(DomainSpec::getIntrospectVersion)
        .orElse(null);
  }

  private boolean hasSameRestartVersion(DomainPresenceInfo cachedInfo) {
    return Objects.equals(getRestartVersion(), cachedInfo.getRestartVersion());
  }

  private String getRestartVersion() {
    return Optional.ofNullable(getDomain())
        .map(DomainResource::getRestartVersion)
        .orElse(null);
  }

  private boolean hasSameIntrospectImage(DomainPresenceInfo cachedInfo) {
    return Objects.equals(getIntrospectImage(), cachedInfo.getIntrospectImage());
  }

  private String getIntrospectImage() {
    return Optional.ofNullable(getDomain())
        .map(DomainResource::getSpec)
        .map(DomainSpec::getImage)
        .orElse(null);
  }

  public ThreadLoggingContext setThreadContext() {
    return ThreadLoggingContext.setThreadContext().namespace(namespace).domainUid(domainUid);
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
    packet.getComponents().put(COMPONENT_KEY, Component.createFor(this));
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
   * Returns a count of HTTP request failures for an operator-managed server.
   * The failures during a REST HTTP call could be timing related such as when
   * the server is shutting down. We shouldn't log warning message for such
   * failures until there have been a sufficient number of failures.
   *
   * @param serverName the name of the server
   * @return HTTP request failure count for the given server.
   */
  public int getHttpRequestFailureCount(String serverName) {
    return getSko(serverName).getHttpRequestFailureCount().get();
  }

  /**
   * Sets the HTTP request failure count for an operator-managed server.
   *
   * @param serverName the name of the server
   * @param failureCount the failure count
   */
  public void setHttpRequestFailureCount(String serverName, int failureCount) {
    getSko(serverName).getHttpRequestFailureCount().set(failureCount);
  }

  /**
   * Increments the HTTP request failure count for an operator-managed server.
   *
   * @param serverName the name of the server
   */
  public void incrementHttpRequestFailureCount(String serverName) {
    getSko(serverName).getHttpRequestFailureCount().getAndIncrement();
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

  public void setExternalService(String serverName, V1Service service) {
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
  public DomainResource getDomain() {
    return domain.get();
  }

  /**
   * Sets the domain.
   *
   * @param domain Domain
   */
  public void setDomain(DomainResource domain) {
    this.domain.set(domain);
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
   * Gets the Domain name.
   *
   * @return Domain name
   */
  public String getDomainName() {
    return getDomain().getMetadata().getName();
  }

  @Override
  public String getResourceName() {
    return getDomainUid();
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
   * @return Server startup info or null if the current value is not set.
   */
  public Collection<ServerStartupInfo> getServerStartupInfo() {
    return serverStartupInfo.get();
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


  /**
   * Check if all cluster status have been initially populated.
   *
   * @return true if the domain has cluster(s) and the cluster statuses have all been initially populated.
   */
  public boolean clusterStatusInitialized() {
    return !getDomain().getSpec().getClusters().isEmpty() && allClusterStatusInitialized();
  }

  private boolean allClusterStatusInitialized() {
    return getClusterStatuses().size() == getDomain().getSpec().getClusters().size();
  }

  @NotNull
  private List<ClusterStatus> getClusterStatuses() {
    return Optional.ofNullable(getDomain().getStatus()).map(DomainStatus::getClusters)
        .orElse(Collections.emptyList());
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder("DomainPresenceInfo{");
    DomainResource d = getDomain();
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
   * Return server Pod names from List operation.
   */
  public List<String> getServerNamesFromPodList() {
    return serverNamesFromPodList;
  }

  /**
   * Add server Pod names from List operation.
   * @param podNames pod names to be added
   */
  public void addServerNamesFromPodList(Collection<String> podNames) {
    serverNamesFromPodList.addAll(podNames);
  }

  /**
   * Add server Pod name from List operation.
   * @param podName pod name to be added
   */
  public void addServerNameFromPodList(String podName) {
    serverNamesFromPodList.add(podName);
  }

  /**
   * Clear server Pod names from List operation.
   */
  public void clearServerPodNamesFromList() {
    serverNamesFromPodList.clear();
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
    return Optional.ofNullable(getServerStartupInfo()).orElse(Collections.emptySet()).stream()
            .map(ServerStartupInfo::getServerName)
            .collect(Collectors.toSet());
  }

  public Map<String, Step.StepAndPacket> getServersToRoll() {
    return serversToRoll;
  }

  public void setServersToRoll(Map<String, Step.StepAndPacket> serversToRoll) {
    this.serversToRoll = serversToRoll;
  }

  /**
   * Looks up cluster resource for the given cluster name.
   * @param clusterName Cluster name
   * @return Cluster resource, if found
   */
  public ClusterResource getClusterResource(String clusterName) {
    if (clusterName != null) {
      return clusters.get(clusterName);
    }
    return null;
  }

  public boolean doesReferenceCluster(String cluster) {
    return Optional.ofNullable(getDomain()).map(DomainResource::getSpec).map(DomainSpec::getClusters)
        .map(list -> doesReferenceCluster(list, cluster)).orElse(false);
  }

  private boolean doesReferenceCluster(List<V1LocalObjectReference> refs, String cluster) {
    return refs.stream().anyMatch(ref -> cluster.equals(ref.getName()));
  }


  public List<ClusterResource> getReferencedClusters() {
    return Optional.ofNullable(getDomain().getSpec().getClusters()).orElse(Collections.emptyList())
        .stream().map(this::findCluster).filter(Objects::nonNull).collect(Collectors.toList());
  }

  private ClusterResource findCluster(V1LocalObjectReference reference) {
    return Optional.ofNullable(reference.getName())
        .flatMap(name -> clusters.values().stream().filter(cluster -> name.equals(cluster.getMetadata().getName()))
            .findFirst()).orElse(null);
  }

  /**
   * Add a ClusterResource resource.
   * @param clusterResource ClusterResource object.
   */
  public void addClusterResource(ClusterResource clusterResource) {
    Optional.ofNullable(clusterResource)
        .map(ClusterResource::getClusterName)
        .ifPresent(name -> clusters.put(name, clusterResource));
  }

  /**
   * Remove a named cluster resource.
   * @param clusterName the name of the resource to remove.
   */
  public ClusterResource removeClusterResource(String clusterName) {
    return Optional.ofNullable(clusterName).map(clusters::remove).orElse(null);
  }

  /**
   * Updates cluster references.
   * @param resources list of cluster resources.
   */
  public void adjustClusterResources(Collection<ClusterResource> resources) {
    Map<String, ClusterResource> updated = new HashMap<>();
    resources.forEach(cr -> updated.put(cr.getClusterName(), cr));
    clusters.keySet().retainAll(updated.keySet());
    clusters.putAll(updated);
  }

  /**
   * Returns all clusters associated with this domain presence.
   */
  public List<V1LocalObjectReference> getClusters() {
    return Optional.ofNullable(getDomain().getSpec().getClusters()).orElse(Collections.emptyList());
  }

  /**
   * Add a PersistentVolumeClaim resource.
   * @param pvc PersistentVolumeClaim object.
   */
  public void addPersistentVolumeClaim(V1PersistentVolumeClaim pvc) {
    Optional.ofNullable(pvc)
        .map(V1PersistentVolumeClaim::getMetadata)
        .map(V1ObjectMeta::getName)
        .ifPresent(name -> persistentVolumeClaims.put(name, pvc));
  }

  /**
   * Remove a named PersistentVolumeClaim resource.
   * @param pvcName the name of the resource to remove.
   */
  public V1PersistentVolumeClaim removePersistentVolumeClaim(String pvcName) {
    return Optional.ofNullable(pvcName).map(persistentVolumeClaims::remove).orElse(null);
  }

  public V1PersistentVolumeClaim getPersistentVolumeClaim(String pvcName) {
    return Optional.ofNullable(pvcName).map(persistentVolumeClaims::get).orElse(null);
  }

  /**
   * Get the effective configuration of the server, based on Kubernetes resources.
   * @param serverName name of the WLS server.
   * @param clusterName name of the WLS cluster, or null for a non-clustered server.
   * @return the effective server spec.
   */
  public EffectiveServerSpec getServer(@Nonnull String serverName, @Nullable String clusterName) {
    final ClusterSpec clusterSpec = getClusterSpecFromClusterResource(clusterName);
    return getDomainApi().getServer(serverName, clusterName, clusterSpec);
  }

  private PrivateDomainApi getDomainApi() {
    return getDomain().getPrivateApi();
  }

  /**
   * Get the effective configuration of the cluster, based on Kubernetes resources.
   * @param clusterName name of WLS cluster.
   * @return the effective cluster spec.
   */
  public EffectiveClusterSpec getCluster(@Nonnull String clusterName) {
    final ClusterSpec clusterSpec = getClusterSpecFromClusterResource(clusterName);
    return getDomainApi().getCluster(clusterSpec);
  }

  @Nullable
  private ClusterSpec getClusterSpecFromClusterResource(@Nullable String clusterName) {
    return Optional.ofNullable(getClusterResource(clusterName))
        .map(ClusterResource::getSpec)
        .orElse(null);
  }

  /**
   * The desired number of running managed servers in each WebLogic cluster that is not explicitly
   * configured in clusters.
   *
   * @param clusterName the same of the cluster
   * @return replicas
   */
  public int getReplicaCount(@Nonnull String clusterName) {
    final ClusterSpec clusterSpec = getClusterSpecFromClusterResource(clusterName);
    return getDomainApi().getReplicaCount(clusterSpec);
  }

  public void setReplicaCount(@Nonnull String clusterName, int replicaLimit) {
    final ClusterSpec clusterSpec = getClusterSpecFromClusterResource(clusterName);
    getDomainApi().setReplicaCount(clusterName, clusterSpec, replicaLimit);
  }

  /**
   * Returns the minimum number of replicas for the specified cluster.
   *
   * @param clusterName the name of the cluster
   * @return the result of applying any configurations for this value
   */
  public int getMinAvailable(@Nonnull String clusterName) {
    final ClusterSpec clusterSpec = getClusterSpecFromClusterResource(clusterName);
    return getDomainApi().getMinAvailable(clusterSpec);
  }

  public int getMaxUnavailable(@Nonnull String clusterName) {
    final ClusterSpec clusterSpec = getClusterSpecFromClusterResource(clusterName);
    return getDomainApi().getMaxUnavailable(clusterSpec);
  }

  public int getMaxConcurrentStartup(@Nonnull String clusterName) {
    ClusterSpec clusterSpec = getClusterSpecFromClusterResource(clusterName);
    return getDomainApi().getMaxConcurrentStartup(clusterSpec);
  }

  public int getMaxConcurrentShutdown(@Nonnull String clusterName) {
    ClusterSpec clusterSpec = getClusterSpecFromClusterResource(clusterName);
    return getDomainApi().getMaxConcurrentShutdown(clusterSpec);
  }

  public Collection<ClusterResource> getClusterResources() {
    return clusters.values();
  }

  public boolean hasRetriableFailure() {
    return Optional.ofNullable(getDomain()).map(DomainResource::hasRetriableFailure).orElse(false);
  }

  /** Details about a specific managed server. */
  public static class ServerInfo {
    public final WlsServerConfig serverConfig;
    protected final String clusterName;
    protected final EffectiveServerSpec effectiveServerSpec;
    protected final boolean isServiceOnly;

    /**
     * Create server info.
     *
     * @param serverConfig Server config scan
     * @param clusterName the name of the cluster
     * @param effectiveServerSpec the server startup configuration
     * @param isServiceOnly true, if only the server service should be created
     */
    public ServerInfo(
        @Nonnull WlsServerConfig serverConfig,
        @Nullable String clusterName,
        EffectiveServerSpec effectiveServerSpec,
        boolean isServiceOnly) {
      this.serverConfig = serverConfig;
      this.clusterName = clusterName;
      this.effectiveServerSpec = effectiveServerSpec;
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
      return effectiveServerSpec == null ? Collections.emptyList() : effectiveServerSpec.getEnvironmentVariables();
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
          .append("serverSpec", effectiveServerSpec)
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
          .append(effectiveServerSpec, that.effectiveServerSpec)
          .append(isServiceOnly, that.isServiceOnly)
          .isEquals();
    }

    @Override
    public int hashCode() {
      return new HashCodeBuilder(17, 37)
          .append(serverConfig)
          .append(clusterName)
          .append(effectiveServerSpec)
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
     * @param effectiveServerSpec the server startup configuration
     */
    public ServerStartupInfo(
        WlsServerConfig serverConfig, String clusterName, EffectiveServerSpec effectiveServerSpec) {
      super(serverConfig, clusterName, effectiveServerSpec, false);
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
     * @param effectiveServerSpec Server specifications
     * @param isServiceOnly If service needs to be preserved
     */
    public ServerShutdownInfo(
            WlsServerConfig serverConfig, String clusterName,
            EffectiveServerSpec effectiveServerSpec, boolean isServiceOnly) {
      super(serverConfig, clusterName, effectiveServerSpec, isServiceOnly);
    }

    public boolean isServiceOnly() {
      return  isServiceOnly;
    }
  }
}

// Copyright (c) 2018, 2024, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helpers;

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
import oracle.kubernetes.operator.NamespacedResourceCache;
import oracle.kubernetes.operator.ProcessingConstants;
import oracle.kubernetes.operator.logging.ThreadLoggingContext;
import oracle.kubernetes.operator.processing.EffectiveClusterSpec;
import oracle.kubernetes.operator.processing.EffectiveServerSpec;
import oracle.kubernetes.operator.tuning.TuningParameters;
import oracle.kubernetes.operator.wlsconfig.WlsServerConfig;
import oracle.kubernetes.operator.work.Fiber;
import oracle.kubernetes.operator.work.Packet;
import oracle.kubernetes.utils.SystemClock;
import oracle.kubernetes.weblogic.domain.model.ClusterResource;
import oracle.kubernetes.weblogic.domain.model.ClusterSpec;
import oracle.kubernetes.weblogic.domain.model.ClusterStatus;
import oracle.kubernetes.weblogic.domain.model.DomainConditionFailureInfo;
import oracle.kubernetes.weblogic.domain.model.DomainResource;
import oracle.kubernetes.weblogic.domain.model.DomainSpec;
import oracle.kubernetes.weblogic.domain.model.DomainStatus;
import oracle.kubernetes.weblogic.domain.model.PrivateDomainApi;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.jetbrains.annotations.NotNull;

import static oracle.kubernetes.operator.helpers.LegalNames.*;
import static oracle.kubernetes.operator.helpers.PodHelper.*;

/**
 * Operator's mapping between custom resource Domain and runtime details about that domain,
 * including the scan and the Pods and Services for servers.
 */
public class DomainPresenceInfo extends ResourcePresenceInfo {
  private final String domainUid;
  private final AtomicBoolean isDeleting = new AtomicBoolean(false);
  private final AtomicBoolean isPopulated = new AtomicBoolean(false);
  private final AtomicReference<Collection<ServerStartupInfo>> serverStartupInfo;
  private final AtomicReference<Collection<ServerShutdownInfo>> serverShutdownInfo;
  private final ConcurrentMap<String, Integer> httpRequestFailureCountMap = new ConcurrentHashMap<>(); // FIXME

  private final ReadWriteLock webLogicCredentialsSecretLock = new ReentrantReadWriteLock();
  private V1Secret webLogicCredentialsSecret;
  private OffsetDateTime webLogicCredentialsSecretLastSet;
  private String adminServerName;

  private final List<String> validationWarnings = Collections.synchronizedList(new ArrayList<>());
  private final List<String> serverNamesFromPodList = Collections.synchronizedList(new ArrayList<>());
  private Map<String, Fiber.StepAndPacket> serversToRoll = Collections.emptyMap();

  /**
   * Create presence for a domain.
   *
   * @param resourceCache Resource cache
   * @param domain Domain
   */
  public DomainPresenceInfo(NamespacedResourceCache resourceCache, DomainResource domain) {
    super(resourceCache);
    this.domainUid = domain.getDomainUid();
    this.serverStartupInfo = new AtomicReference<>(null);
    this.serverShutdownInfo = new AtomicReference<>(null);
  }

  /**
   * Create presence for a domain.
   *
   * @param resourceCache Resource cache
   * @param domainUid The unique identifier assigned to the WebLogic domain when it was registered
   */
  public DomainPresenceInfo(NamespacedResourceCache resourceCache, String domainUid) {
    super(resourceCache);
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

  public boolean isMustDomainProcessingRestart() {
    return !failureInfoVersionsUnchanged();
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

  private DomainConditionFailureInfo findFailureInfo() {
    return Optional.ofNullable(getDomain()).map(DomainResource::getStatus)
        .map(DomainStatus::getFailureInfo).orElse(null);
  }

  private boolean failureInfoVersionsUnchanged() {
    return Optional.ofNullable(findFailureInfo()).map(this::versionsUnchanged).orElse(true);
  }

  private boolean versionsUnchanged(DomainConditionFailureInfo failureInfo) {
    return hasSameIntrospectVersion(failureInfo)
        && hasSameRestartVersion(failureInfo)
        && hasSameIntrospectImage(failureInfo);
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

  private boolean  hasSameIntrospectVersion(DomainPresenceInfo cachedInfo) {
    return Objects.equals(getIntrospectVersion(), cachedInfo.getIntrospectVersion());
  }

  private boolean  hasSameIntrospectVersion(DomainConditionFailureInfo failureInfo) {
    return Objects.equals(getIntrospectVersion(), failureInfo.getIntrospectVersion());
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

  private boolean hasSameRestartVersion(DomainConditionFailureInfo failureInfo) {
    return Objects.equals(getRestartVersion(), failureInfo.getRestartVersion());
  }

  private String getRestartVersion() {
    return Optional.ofNullable(getDomain())
        .map(DomainResource::getRestartVersion)
        .orElse(null);
  }

  private boolean hasSameIntrospectImage(DomainPresenceInfo cachedInfo) {
    return Objects.equals(getIntrospectImage(), cachedInfo.getIntrospectImage());
  }

  private boolean hasSameIntrospectImage(DomainConditionFailureInfo failureInfo) {
    return Objects.equals(getIntrospectImage(), failureInfo.getIntrospectImage());
  }

  private String getIntrospectImage() {
    return Optional.ofNullable(getDomain())
        .map(DomainResource::getSpec)
        .map(DomainSpec::getImage)
        .orElse(null);
  }

  public ThreadLoggingContext setThreadContext() {
    return ThreadLoggingContext.setThreadContext().namespace(getNamespace()).domainUid(domainUid);
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
    return getServerPods().filter(this::isNotDeletingPod).filter(p -> hasClusterNameOrNull(p, clusterName));
  }

  @Nonnull
  private Stream<V1Pod> getManagedServersInNoOtherCluster(String clusterName, String adminServerName) {
    return getServerPods()
          .filter(this::isNotDeletingPod)
          .filter(p -> isNotAdminServer(p, adminServerName))
          .filter(p -> hasClusterNameOrNull(p, clusterName));
  }

  private boolean isNotDeletingPod(@Nullable V1Pod pod) {
    return Optional.ofNullable(pod).map(V1Pod::getMetadata).map(V1ObjectMeta::getDeletionTimestamp).isEmpty();
  }

  public V1Service getServerService(String serverName) {
    return resourceCache.getServiceResources().get(toServerServiceName(getDomainUid(), serverName));
  }

  public static Optional<DomainPresenceInfo> fromPacket(Packet packet) {
    return Optional.ofNullable((DomainPresenceInfo) packet.get(ProcessingConstants.DOMAIN_PRESENCE_INFO));
  }

  public String getAdminServerName() {
    return adminServerName;
  }

  public void setAdminServerName(String adminServerName) {
    this.adminServerName = adminServerName;
  }

  /**
   * Returns the pod associated with an operator-managed server.
   *
   * @param serverName the name of the server
   * @return the corresponding pod, or null if none exists
   */
  public V1Pod getServerPod(String serverName) {
    return resourceCache.getPodResources().get(toPodName(getDomainUid(), serverName));
  }

  /**
   * Returns a stream of all server pods present.
   *
   * @return a pod stream
   */
  public Stream<V1Pod> getServerPods() {
    return resourceCache.getPodResources().values().stream().filter(this::isDomainPod);
  }

  private boolean isDomainPod(V1Pod pod) {
    return getDomainUid().equals(getPodDomainUid(pod));
  }

  /**
   * Returns a stream of all server pods present, if the server is not marked for deletion.
   *
   * @return a pod stream
   */
  public Stream<V1Pod> getServerPodsNotBeingDeleted() {
    return getServerPods().filter(p -> !isPodAlreadyAnnotatedForShutdown(p));
  }

  public boolean isServerPodBeingDeleted(String serverName) {
    return Optional.ofNullable(getServerPod(serverName)).map(PodHelper::isPodAlreadyAnnotatedForShutdown).orElse(false);
  }

  public boolean isServerPodDeleted(String serverName) {
    return Optional.ofNullable(getServerPod(serverName)).map(PodHelper::isDeleting).orElse(false);
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
    return httpRequestFailureCountMap.getOrDefault(serverName, 0);
  }

  /**
   * Sets the HTTP request failure count for an operator-managed server.
   *
   * @param serverName the name of the server
   * @param failureCount the failure count
   */
  public void setHttpRequestFailureCount(String serverName, int failureCount) {
    httpRequestFailureCountMap.put(serverName, failureCount);
  }

  /**
   * Increments the HTTP request failure count for an operator-managed server.
   *
   * @param serverName the name of the server
   */
  public void incrementHttpRequestFailureCount(String serverName) {
    httpRequestFailureCountMap.compute(serverName, (k, v) -> v == null ? 1 : v + 1);
  }

  /**
   * Returns a collection of the names of the active servers.
   */
  public Collection<String> getServerNames() {
    return getServerPods().map(PodHelper::getServerName).toList();
  }

  public V1Service getClusterService(String clusterName) {
    return resourceCache.getServiceResources().get(toClusterServiceName(getDomainUid(), clusterName));
  }

  public V1PodDisruptionBudget getPodDisruptionBudget(String clusterName) {
    return resourceCache.getPodDistributionBudgetResources().get(
        toDns1123LegalName(getDomainUid() + "-" + clusterName));
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
              SystemClock.now().minusSeconds(getWebLogicCredentialsSecretRereadIntervalSeconds()))) {
        return webLogicCredentialsSecret;
      }
    } finally {
      webLogicCredentialsSecretLock.readLock().unlock();
    }

    // time to clear
    setWebLogicCredentialsSecret(null);
    return null;
  }

  private int getWebLogicCredentialsSecretRereadIntervalSeconds() {
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

  public V1Service getExternalService(String serverName) {
    return resourceCache.getServiceResources().get(toExternalServiceName(getDomainUid(), serverName));
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
    return resourceCache.getDomainResources().get(domainUid);
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
   * Gets server shutdown info.
   * @return Shutdown info, or null if not set
   */
  public Collection<ServerShutdownInfo> getServerShutdownInfo() {
    return serverShutdownInfo.get();
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
      sb.append(", namespace=").append(getNamespace());
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

  public Map<String, Fiber.StepAndPacket> getServersToRoll() {
    return serversToRoll;
  }

  public void setServersToRoll(Map<String, Fiber.StepAndPacket> serversToRoll) {
    this.serversToRoll = serversToRoll;
  }

  /**
   * Looks up cluster resource for the given cluster name.
   * @param clusterName Cluster name
   * @return Cluster resource, if found
   */
  public ClusterResource getClusterResource(String clusterName) {
    ConcurrentMap<String, ClusterResource> clusters = resourceCache.getClusterResources();
    return clusters.values().stream()
        .filter(cluster -> clusterName.equals(cluster.getMetadata().getName())).findFirst().orElse(null);
  }

  public boolean doesReferenceCluster(String cluster) {
    return Optional.ofNullable(getDomain()).map(DomainResource::getSpec).map(DomainSpec::getClusters)
        .map(list -> doesReferenceCluster(list, cluster)).orElse(false);
  }

  private boolean doesReferenceCluster(List<V1LocalObjectReference> refs, String cluster) {
    return refs.stream().anyMatch(ref -> cluster.equals(ref.getName()));
  }


  public List<ClusterResource> getReferencedClusters() {
    return Optional.ofNullable(getDomain()).map(DomainResource::getSpec).map(DomainSpec::getClusters)
            .orElse(Collections.emptyList()).stream().map(this::findCluster).filter(Objects::nonNull).toList();
  }

  private ClusterResource findCluster(V1LocalObjectReference reference) {
    ConcurrentMap<String, ClusterResource> clusters = resourceCache.getClusterResources();
    return Optional.ofNullable(reference.getName()).flatMap(name -> clusters.values().stream()
        .filter(cluster -> name.equals(cluster.getMetadata().getName())).findFirst()).orElse(null);
  }

  /**
   * Returns all clusters associated with this domain presence.
   */
  public List<V1LocalObjectReference> getClusters() {
    return Optional.ofNullable(getDomain().getSpec().getClusters()).orElse(Collections.emptyList());
  }

  public V1PersistentVolumeClaim getPersistentVolumeClaim(String pvcName) {
    return resourceCache.getPersistentVolumeClaimResources().get(pvcName);
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

  public boolean hasRetryableFailure() {
    return Optional.ofNullable(getDomain()).map(DomainResource::hasRetryableFailure).orElse(false);
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
     * @param serverName the name of the server to shut down
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

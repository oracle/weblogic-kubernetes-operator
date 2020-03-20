// Copyright (c) 2018, 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helpers;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;
import java.util.stream.Stream;

import io.kubernetes.client.openapi.models.V1EnvVar;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1Pod;
import io.kubernetes.client.openapi.models.V1Service;
import oracle.kubernetes.operator.WebLogicConstants;
import oracle.kubernetes.operator.wlsconfig.WlsServerConfig;
import oracle.kubernetes.weblogic.domain.model.Domain;
import oracle.kubernetes.weblogic.domain.model.ServerSpec;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;

/**
 * Operator's mapping between custom resource Domain and runtime details about that domain,
 * including the scan and the Pods and Services for servers.
 */
public class DomainPresenceInfo {
  private final String namespace;
  private final String domainUid;
  private final AtomicReference<Domain> domain;
  private final AtomicBoolean isDeleting = new AtomicBoolean(false);
  private final AtomicBoolean isPopulated = new AtomicBoolean(false);
  private final AtomicInteger retryCount = new AtomicInteger(0);
  private final AtomicReference<Collection<ServerStartupInfo>> serverStartupInfo;

  private final ConcurrentMap<String, ServerKubernetesObjects> servers = new ConcurrentHashMap<>();
  private final ConcurrentMap<String, V1Service> clusters = new ConcurrentHashMap<>();

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
  }

  /**
   * Create presence for a domain.
   *
   * @param namespace Namespace
   * @param domainUid The unique identifier assigned to the Weblogic domain when it was registered
   */
  public DomainPresenceInfo(String namespace, String domainUid) {
    this.domain = new AtomicReference<>(null);
    this.namespace = namespace;
    this.domainUid = domainUid;
    this.serverStartupInfo = new AtomicReference<>(null);
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

  void setServerService(String serverName, V1Service service) {
    getSko(serverName).getService().set(service);
  }

  private ServerKubernetesObjects getSko(String serverName) {
    return getServers().computeIfAbsent(serverName, (n -> new ServerKubernetesObjects()));
  }

  public V1Service getServerService(String serverName) {
    return getSko(serverName).getService().get();
  }

  public V1Service removeServerService(String serverName) {
    return getSko(serverName).getService().getAndSet(null);
  }

  V1Service[] getServiceServices() {
    return servers.values().stream()
        .map(ServerKubernetesObjects::getService)
        .map(AtomicReference::get)
        .toArray(V1Service[]::new);
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
    return getServers().values().stream().map(this::getPod).filter(Objects::nonNull);
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
   * Returns a collection of all servers defined.
   *
   * @return the servers
   */
  public Collection<String> getServerNames() {
    return getServers().keySet();
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

  /**
   * Computes the result of a delete attempt. If the current pod is newer than the one associated
   * with the delete event, returns it; otherwise returns null, thus deleting the value.
   *
   * @param serverName the server name associated with the pod
   * @param event the pod associated with the delete event
   * @return the new value for the pod.
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
    clusters.remove(clusterName);
  }

  public V1Service getClusterService(String clusterName) {
    return clusters.get(clusterName);
  }

  void setClusterService(String clusterName, V1Service service) {
    clusters.put(clusterName, service);
  }

  void setClusterServiceFromEvent(String clusterName, V1Service event) {
    if (clusterName == null) {
      return;
    }

    clusters.compute(clusterName, (k, s) -> getNewerService(s, event));
  }

  boolean deleteClusterServiceFromEvent(String clusterName, V1Service event) {
    return removeIfPresentAnd(
        clusters,
        clusterName,
        s -> !KubernetesUtils.isFirstNewer(getMetadata(s), getMetadata(event)));
  }

  private V1Service getNewerService(V1Service first, V1Service second) {
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

  private void resetFailureCount() {
    retryCount.set(0);
  }

  public int incrementAndGetFailureCount() {
    return retryCount.incrementAndGet();
  }

  int getRetryCount() {
    return retryCount.get();
  }

  /** Sets the last completion time to now. */
  public void complete() {
    resetFailureCount();
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

  /**
   * Map from server name to server objects (Pods and Services).
   *
   * @return Server object map
   */
  public ConcurrentMap<String, ServerKubernetesObjects> getServers() {
    return servers;
  }

  /**
   * Server startup info.
   *
   * @return Server startup info
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

  /** Details about a specific managed server that will be started up. */
  public static class ServerStartupInfo {
    public final WlsServerConfig serverConfig;
    private final String clusterName;
    private final ServerSpec serverSpec;
    private final boolean isServiceOnly;

    /**
     * Create server startup info.
     *
     * @param serverConfig Server config scan
     * @param clusterName the name of the cluster
     * @param serverSpec the server startup configuration
     */
    public ServerStartupInfo(
        WlsServerConfig serverConfig, String clusterName, ServerSpec serverSpec) {
      this(serverConfig, clusterName, serverSpec, false);
    }

    /**
     * Create server startup info.
     *
     * @param serverConfig Server config scan
     * @param clusterName the name of the cluster
     * @param serverSpec the server startup configuration
     * @param isServiceOnly true, if only the server service should be created
     */
    public ServerStartupInfo(
        WlsServerConfig serverConfig,
        String clusterName,
        ServerSpec serverSpec,
        boolean isServiceOnly) {
      this.serverConfig = serverConfig;
      this.clusterName = clusterName;
      this.serverSpec = serverSpec;
      this.isServiceOnly = isServiceOnly;
    }

    public String getServerName() {
      return Optional.ofNullable(serverConfig).map(WlsServerConfig::getName).orElse(null);
    }

    public String getClusterName() {
      return clusterName;
    }

    /**
     * Returns the desired state for the started server.
     *
     * @return return a string, which may be null.
     */
    public String getDesiredState() {
      return serverSpec == null ? null : serverSpec.getDesiredState();
    }

    public List<V1EnvVar> getEnvironment() {
      return serverSpec == null ? Collections.emptyList() : serverSpec.getEnvironmentVariables();
    }

    public boolean isServiceOnly() {
      return isServiceOnly;
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

      ServerStartupInfo that = (ServerStartupInfo) o;

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
}

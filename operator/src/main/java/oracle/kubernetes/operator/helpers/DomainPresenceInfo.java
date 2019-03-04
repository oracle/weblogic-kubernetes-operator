// Copyright 2018, 2019, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helpers;

import io.kubernetes.client.models.V1EnvVar;
import io.kubernetes.client.models.V1Service;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import oracle.kubernetes.operator.wlsconfig.WlsServerConfig;
import oracle.kubernetes.weblogic.domain.v2.Domain;
import oracle.kubernetes.weblogic.domain.v2.ServerSpec;
import org.joda.time.DateTime;

/**
 * Operator's mapping between custom resource Domain and runtime details about that domain,
 * including the scan and the Pods and Services for servers.
 */
public class DomainPresenceInfo {
  private final String namespace;
  private final String domainUID;
  private final AtomicReference<Domain> domain;
  private final AtomicBoolean isDeleting = new AtomicBoolean(false);
  private final AtomicBoolean isPopulated = new AtomicBoolean(false);
  private final AtomicInteger retryCount = new AtomicInteger(0);
  private final AtomicReference<Collection<ServerStartupInfo>> serverStartupInfo;

  private final ConcurrentMap<String, ServerKubernetesObjects> servers = new ConcurrentHashMap<>();
  private final ConcurrentMap<String, V1Service> clusters = new ConcurrentHashMap<>();

  private DateTime lastCompletionTime;

  /**
   * Create presence for a domain.
   *
   * @param domain Domain
   */
  public DomainPresenceInfo(Domain domain) {
    this.domain = new AtomicReference<>(domain);
    this.namespace = domain.getMetadata().getNamespace();
    this.domainUID = domain.getDomainUID();
    this.serverStartupInfo = new AtomicReference<>(null);
  }

  /**
   * Create presence for a domain.
   *
   * @param namespace Namespace
   * @param domainUID The unique identifier assigned to the Weblogic domain when it was registered
   */
  public DomainPresenceInfo(String namespace, String domainUID) {
    this.domain = new AtomicReference<>(null);
    this.namespace = namespace;
    this.domainUID = domainUID;
    this.serverStartupInfo = new AtomicReference<>(null);
  }

  public boolean isDeleting() {
    return isDeleting.get();
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

  public void resetFailureCount() {
    retryCount.set(0);
  }

  public int incrementAndGetFailureCount() {
    return retryCount.incrementAndGet();
  }

  public int getRetryCount() {
    return retryCount.get();
  }

  /**
   * Last completion time.
   *
   * @return Last completion time
   */
  public DateTime getLastCompletionTime() {
    return lastCompletionTime;
  }

  /** Sets the last completion time to now. */
  public void complete() {
    this.lastCompletionTime = new DateTime();
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
  public String getDomainUID() {
    return domainUID;
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
   * Map from cluster name to Service objects.
   *
   * @return Cluster object map
   */
  public ConcurrentMap<String, V1Service> getClusters() {
    return clusters;
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
              getDomain().getDomainUID(), getDomain().getMetadata().getNamespace()));
    } else {
      sb.append(", namespace=").append(namespace);
    }
    sb.append("}");

    return sb.toString();
  }

  /** Details about a specific managed server that will be started up. */
  public static class ServerStartupInfo {
    public final WlsServerConfig serverConfig;
    private String clusterName;
    private ServerSpec serverSpec;

    /**
     * Create server startup info.
     *
     * @param serverConfig Server config scan
     * @param clusterName the name of the cluster
     * @param serverSpec the server startup configuration
     */
    public ServerStartupInfo(
        WlsServerConfig serverConfig, String clusterName, ServerSpec serverSpec) {
      this.serverConfig = serverConfig;
      this.clusterName = clusterName;
      this.serverSpec = serverSpec;
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
  }
}

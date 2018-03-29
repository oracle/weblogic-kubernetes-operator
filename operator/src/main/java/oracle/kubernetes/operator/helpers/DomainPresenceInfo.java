// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helpers;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicReference;

import org.joda.time.DateTime;

import io.kubernetes.client.models.V1EnvVar;
import io.kubernetes.client.models.V1PersistentVolumeClaimList;
import io.kubernetes.client.models.V1Service;
import io.kubernetes.client.models.V1beta1Ingress;
import oracle.kubernetes.weblogic.domain.v1.Domain;
import oracle.kubernetes.weblogic.domain.v1.DomainSpec;
import oracle.kubernetes.weblogic.domain.v1.ServerStartup;
import oracle.kubernetes.operator.wlsconfig.WlsClusterConfig;
import oracle.kubernetes.operator.wlsconfig.WlsDomainConfig;
import oracle.kubernetes.operator.wlsconfig.WlsServerConfig;

/**
 * Operator's mapping between custom resource Domain and runtime details about that domain, including the 
 * scan and the Pods and Services for servers.
 * 
 */
public class DomainPresenceInfo {
  private final String namespace;
  private final AtomicReference<Domain> domain;
  private final AtomicReference<ScheduledFuture<?>> statusUpdater;
  private final AtomicReference<Collection<ServerStartupInfo>> serverStartupInfo;

  private final ConcurrentMap<String, ServerKubernetesObjects> servers = new ConcurrentHashMap<>();
  private final ConcurrentMap<String, V1Service> clusters = new ConcurrentHashMap<>();
  private final ConcurrentMap<String, V1beta1Ingress> ingresses = new ConcurrentHashMap<>();

  private V1PersistentVolumeClaimList claims = null;

  private WlsDomainConfig domainConfig;
  private DateTime lastScanTime;

  /**
   * Create presence for a domain
   * @param domain Domain
   */
  public DomainPresenceInfo(Domain domain) {
    this.domain = new AtomicReference<>(domain);
    this.namespace = domain.getMetadata().getNamespace();
    this.serverStartupInfo = new AtomicReference<>(null);
    this.statusUpdater = new AtomicReference<>(null);
  }

  /**
   * Create presence for a domain
   * @param namespace Namespace
   */
  public DomainPresenceInfo(String namespace) {
    this.domain = new AtomicReference<>(null);
    this.namespace = namespace;
    this.serverStartupInfo = new AtomicReference<>(null);
    this.statusUpdater = new AtomicReference<>(null);
  }

  /**
   * Claims associated with the domain
   * @return Claims
   */
  public V1PersistentVolumeClaimList getClaims() {
    return claims;
  }

  /**
   * Sets claims
   * @param claims Claims
   */
  public void setClaims(V1PersistentVolumeClaimList claims) {
    this.claims = claims;
  }

  /**
   * Domain scan
   * @return Domain scan
   */
  public WlsDomainConfig getScan() {
    return domainConfig;
  }

  /**
   * Sets scan
   * @param domainConfig Scan
   */
  public void setScan(WlsDomainConfig domainConfig) {
    this.domainConfig = domainConfig;
  }

  /**
   * Last scan time
   * @return Last scan time
   */
  public DateTime getLastScanTime() {
    return lastScanTime;
  }

  /**
   * Sets last scan time
   * @param lastScanTime Last scan time
   */
  public void setLastScanTime(DateTime lastScanTime) {
    this.lastScanTime = lastScanTime;
  }

  /**
   * Gets the domain.  Except the instance to change frequently based on status updates
   * @return Domain
   */
  public Domain getDomain() {
    return domain.get();
  }
  
  /**
   * Sets the domain.
   * @param domain Domain
   */
  public void setDomain(Domain domain) {
    this.domain.set(domain);
  }

  /**
   * Gets the namespace
   * @return Namespace
   */
  public String getNamespace() {
    return namespace;
  }
  
  /**
   * Map from server name to server objects (Pods and Services)
   * @return Server object map
   */
  public ConcurrentMap<String, ServerKubernetesObjects> getServers() {
    return servers;
  }

  /**
   * Map from cluster name to Service objects
   * @return Cluster object map
   */
  public ConcurrentMap<String, V1Service> getClusters() {
    return clusters;
  }

  /**
   * Map from cluster name to Ingress
   * @return Cluster object map
   */
  public ConcurrentMap<String, V1beta1Ingress> getIngresses() {
    return ingresses;
  }
  
  /**
   * Server objects (Pods and Services) for admin server
   * @return Server objects for admin server
   */
  public ServerKubernetesObjects getAdmin() {
    Domain dom = domain.get();
    DomainSpec spec = dom.getSpec();
    return servers.get(spec.getAsName());
  }
  
  /**
   * Server startup info
   * @return Server startup info
   */
  public Collection<ServerStartupInfo> getServerStartupInfo() {
    return serverStartupInfo.get();
  }
  
  /**
   * Sets server startup info
   * @param serverStartupInfo Server startup info
   */
  public void setServerStartupInfo(Collection<ServerStartupInfo> serverStartupInfo) {
    this.serverStartupInfo.set(serverStartupInfo);
  }

  /**
   * Details about a specific managed server that will be started up
   */
  public static class ServerStartupInfo {
    final public WlsServerConfig serverConfig;
    final public WlsClusterConfig clusterConfig;
    final public List<V1EnvVar> envVars;
    final public ServerStartup serverStartup;

    /**
     * Create server startup info
     * @param serverConfig Server config scan
     * @param clusterConfig Cluster config scan
     * @param envVars Environment variables
     * @param serverStartup Server startup configuration
     */
    public ServerStartupInfo(WlsServerConfig serverConfig, WlsClusterConfig clusterConfig, List<V1EnvVar> envVars, ServerStartup serverStartup) {
      this.serverConfig = serverConfig;
      this.clusterConfig = clusterConfig;
      this.envVars = envVars;
      this.serverStartup = serverStartup;
    }
  }
  
  /**
   * Domain status updater
   * @return Domain status updater
   */
  public AtomicReference<ScheduledFuture<?>> getStatusUpdater() {
    return statusUpdater;
  }
}

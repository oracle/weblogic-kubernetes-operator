// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helpers;

import io.kubernetes.client.models.V1EnvVar;
import io.kubernetes.client.models.V1PersistentVolumeClaimList;
import io.kubernetes.client.models.V1Service;
import io.kubernetes.client.models.V1beta1Ingress;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import oracle.kubernetes.operator.wlsconfig.WlsDomainConfig;
import oracle.kubernetes.operator.wlsconfig.WlsServerConfig;
import oracle.kubernetes.weblogic.domain.v2.Domain;
import oracle.kubernetes.weblogic.domain.v2.DomainSpec;
import oracle.kubernetes.weblogic.domain.v2.ServerSpec;
import org.joda.time.DateTime;

/**
 * Operator's mapping between custom resource Domain and runtime details about that domain,
 * including the scan and the Pods and Services for servers.
 */
public class DomainPresenceInfo {
  private final String namespace;
  private final AtomicReference<Domain> domain;
  private final AtomicReference<ScheduledFuture<?>> statusUpdater;
  private final AtomicReference<Collection<ServerStartupInfo>> serverStartupInfo;

  private final ConcurrentMap<String, ServerKubernetesObjects> servers = new ServerMap();
  private final ConcurrentMap<String, V1Service> clusters = new ConcurrentHashMap<>();
  private final ConcurrentMap<String, V1beta1Ingress> ingresses = new ConcurrentHashMap<>();

  private final AtomicBoolean explicitRestartAdmin = new AtomicBoolean(false);
  private final Set<String> explicitRestartServers = new CopyOnWriteArraySet<>();
  private final Set<String> explicitRestartClusters = new CopyOnWriteArraySet<>();

  private V1PersistentVolumeClaimList claims = null;

  private WlsDomainConfig domainConfig;
  private DateTime lastScanTime;
  private DateTime lastCompletionTime;

  /**
   * Create presence for a domain
   *
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
   *
   * @param namespace Namespace
   */
  DomainPresenceInfo(String namespace) {
    this.domain = new AtomicReference<>(null);
    this.namespace = namespace;
    this.serverStartupInfo = new AtomicReference<>(null);
    this.statusUpdater = new AtomicReference<>(null);
  }

  /**
   * Claims associated with the domain
   *
   * @return Claims
   */
  public V1PersistentVolumeClaimList getClaims() {
    return claims;
  }

  /**
   * Sets claims
   *
   * @param claims Claims
   */
  public void setClaims(V1PersistentVolumeClaimList claims) {
    this.claims = claims;
  }

  /**
   * Domain scan
   *
   * @return Domain scan
   */
  public WlsDomainConfig getScan() {
    return domainConfig;
  }

  /**
   * Sets scan
   *
   * @param domainConfig Scan
   */
  public void setScan(WlsDomainConfig domainConfig) {
    this.domainConfig = domainConfig;
  }

  /**
   * Last scan time
   *
   * @return Last scan time
   */
  public DateTime getLastScanTime() {
    return lastScanTime;
  }

  /**
   * Sets last scan time
   *
   * @param lastScanTime Last scan time
   */
  public void setLastScanTime(DateTime lastScanTime) {
    this.lastScanTime = lastScanTime;
  }

  /**
   * Last completion time
   *
   * @return Last completion time
   */
  public DateTime getLastCompletionTime() {
    return lastCompletionTime;
  }

  /** Sets the last completion time to now */
  public void complete() {
    this.lastCompletionTime = new DateTime();
  }

  /**
   * Gets the domain. Except the instance to change frequently based on status updates
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
    Domain old = this.domain.getAndSet(domain);
    if (old == null) {
      for (Map.Entry<String, ServerKubernetesObjects> entry : servers.entrySet()) {
        ServerKubernetesObjectsManager.register(
            domain.getSpec().getDomainUID(), entry.getKey(), entry.getValue());
      }
    }
  }

  /**
   * Gets the namespace
   *
   * @return Namespace
   */
  public String getNamespace() {
    return namespace;
  }

  /**
   * Map from server name to server objects (Pods and Services)
   *
   * @return Server object map
   */
  public ConcurrentMap<String, ServerKubernetesObjects> getServers() {
    return servers;
  }

  /**
   * Map from cluster name to Service objects
   *
   * @return Cluster object map
   */
  public ConcurrentMap<String, V1Service> getClusters() {
    return clusters;
  }

  /**
   * Map from cluster name to Ingress
   *
   * @return Cluster object map
   */
  public ConcurrentMap<String, V1beta1Ingress> getIngresses() {
    return ingresses;
  }

  /**
   * Control for if domain has outstanding restart admin server pending
   *
   * @return Control for pending admin server restart
   */
  public AtomicBoolean getExplicitRestartAdmin() {
    return explicitRestartAdmin;
  }

  /**
   * Control list for outstanding server restarts
   *
   * @return Control list for outstanding server restarts
   */
  public Set<String> getExplicitRestartServers() {
    return explicitRestartServers;
  }

  /**
   * Control list for outstanding cluster restarts
   *
   * @return Control list for outstanding cluster restarts
   */
  public Set<String> getExplicitRestartClusters() {
    return explicitRestartClusters;
  }

  /**
   * Server objects (Pods and Services) for admin server
   *
   * @return Server objects for admin server
   */
  public ServerKubernetesObjects getAdmin() {
    Domain dom = domain.get();
    DomainSpec spec = dom.getSpec();
    return servers.get(spec.getAsName());
  }

  /**
   * Server startup info
   *
   * @return Server startup info
   */
  public Collection<ServerStartupInfo> getServerStartupInfo() {
    return serverStartupInfo.get();
  }

  /**
   * Sets server startup info
   *
   * @param serverStartupInfo Server startup info
   */
  public void setServerStartupInfo(Collection<ServerStartupInfo> serverStartupInfo) {
    this.serverStartupInfo.set(serverStartupInfo);
  }

  @Override
  public String toString() {
    StringBuilder sb =
        new StringBuilder(
            String.format(
                "DomainPresenceInfo{uid=%s, namespace=%s",
                getDomain().getSpec().getDomainUID(), getDomain().getMetadata().getNamespace()));
    if (!ingresses.isEmpty()) {
      sb.append(", ingresses ").append(String.join(",", ingresses.keySet()));
    }
    sb.append("}");

    return sb.toString();
  }

  /** Details about a specific managed server that will be started up */
  public static class ServerStartupInfo {
    public final WlsServerConfig serverConfig;
    private String clusterName;
    private ServerSpec serverSpec;

    /**
     * Create server startup info
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

    public String getClusterName() {
      return clusterName;
    }

    /**
     * Returns the node port to use when starting up the configured server.
     *
     * @return a port number, or null.
     */
    public Integer getNodePort() {
      return serverSpec == null ? null : serverSpec.getNodePort();
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

  /**
   * Domain status updater
   *
   * @return Domain status updater
   */
  public AtomicReference<ScheduledFuture<?>> getStatusUpdater() {
    return statusUpdater;
  }

  private class ServerMap implements ConcurrentMap<String, ServerKubernetesObjects> {
    private final ConcurrentMap<String, ServerKubernetesObjects> delegate =
        new ConcurrentHashMap<>();

    @Override
    public int size() {
      return delegate.size();
    }

    @Override
    public boolean isEmpty() {
      return delegate.isEmpty();
    }

    @Override
    public boolean containsKey(Object key) {
      return delegate.containsKey(key);
    }

    @Override
    public boolean containsValue(Object value) {
      return delegate.containsValue(value);
    }

    @Override
    public ServerKubernetesObjects get(Object key) {
      return delegate.get(key);
    }

    @Override
    public ServerKubernetesObjects put(String key, ServerKubernetesObjects value) {
      Domain d = domain.get();
      if (d != null) {
        ServerKubernetesObjectsManager.register(d.getSpec().getDomainUID(), key, value);
      }
      return delegate.put(key, value);
    }

    @Override
    public ServerKubernetesObjects remove(Object key) {
      Domain d = domain.get();
      if (d != null) {
        ServerKubernetesObjectsManager.unregister(d.getSpec().getDomainUID(), (String) key);
      }
      return delegate.remove(key);
    }

    @Override
    public void putAll(Map<? extends String, ? extends ServerKubernetesObjects> m) {
      Domain d = domain.get();
      if (d != null) {
        for (Map.Entry<? extends String, ? extends ServerKubernetesObjects> entry : m.entrySet()) {
          ServerKubernetesObjectsManager.register(
              d.getSpec().getDomainUID(), entry.getKey(), entry.getValue());
        }
      }
      delegate.putAll(m);
    }

    @Override
    public void clear() {
      Domain d = domain.get();
      if (d != null) {
        for (Map.Entry<? extends String, ? extends ServerKubernetesObjects> entry : entrySet()) {
          ServerKubernetesObjectsManager.unregister(d.getSpec().getDomainUID(), entry.getKey());
        }
      }
      delegate.clear();
    }

    @Override
    public Set<String> keySet() {
      return Collections.unmodifiableSet(delegate.keySet());
    }

    @Override
    public Collection<ServerKubernetesObjects> values() {
      return Collections.unmodifiableCollection(delegate.values());
    }

    @Override
    public Set<Entry<String, ServerKubernetesObjects>> entrySet() {
      return Collections.unmodifiableSet(delegate.entrySet());
    }

    @Override
    public ServerKubernetesObjects putIfAbsent(String key, ServerKubernetesObjects value) {
      ServerKubernetesObjects result = delegate.putIfAbsent(key, value);
      DomainPresenceMonitor.putIfAbsent(key, result);
      if (result == null) {
        Domain d = domain.get();
        DomainPresenceMonitor.putIfAbsentDomain(d);
        if (d != null) {
          ServerKubernetesObjectsManager.register(d.getSpec().getDomainUID(), key, value);
        }
      }
      return result;
    }

    @Override
    public boolean remove(Object key, Object value) {
      boolean result = delegate.remove(key, value);
      if (result) {
        Domain d = domain.get();
        if (d != null) {
          ServerKubernetesObjectsManager.unregister(d.getSpec().getDomainUID(), (String) key);
        }
      }
      return result;
    }

    @Override
    public boolean replace(
        String key, ServerKubernetesObjects oldValue, ServerKubernetesObjects newValue) {
      boolean result = delegate.replace(key, oldValue, newValue);
      if (result) {
        Domain d = domain.get();
        if (d != null) {
          ServerKubernetesObjectsManager.unregister(d.getSpec().getDomainUID(), (String) key);
        }
      }
      return result;
    }

    @Override
    public ServerKubernetesObjects replace(String key, ServerKubernetesObjects value) {
      ServerKubernetesObjects result = delegate.replace(key, value);
      if (result == null) {
        Domain d = domain.get();
        if (d != null) {
          ServerKubernetesObjectsManager.unregister(d.getSpec().getDomainUID(), (String) key);
        }
      }
      return result;
    }
  }
}

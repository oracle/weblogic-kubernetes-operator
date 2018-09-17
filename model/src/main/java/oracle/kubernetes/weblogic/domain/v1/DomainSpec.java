// Copyright 2017, 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.weblogic.domain.v1;

import static oracle.kubernetes.operator.StartupControlConstants.AUTO_STARTUPCONTROL;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;
import io.kubernetes.client.models.V1LocalObjectReference;
import io.kubernetes.client.models.V1SecretReference;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import oracle.kubernetes.operator.KubernetesConstants;
import oracle.kubernetes.weblogic.domain.EffectiveConfigurationFactory;
import oracle.kubernetes.weblogic.domain.v2.AdminServer;
import oracle.kubernetes.weblogic.domain.v2.BaseConfiguration;
import oracle.kubernetes.weblogic.domain.v2.Cluster;
import oracle.kubernetes.weblogic.domain.v2.ManagedServer;
import oracle.kubernetes.weblogic.domain.v2.Server;
import oracle.kubernetes.weblogic.domain.v2.ServerSpecV2Impl;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;

/** DomainSpec is a description of a domain. */
@SuppressWarnings("NullableProblems")
public class DomainSpec extends BaseConfiguration {

  /** Domain unique identifier. Must be unique across the Kubernetes cluster. (Required) */
  @SerializedName("domainUID")
  @Expose
  @NotNull
  private String domainUID;

  /** Domain name (Required) */
  @SerializedName("domainName")
  @Expose
  @NotNull
  private String domainName;

  /**
   * Reference to secret containing domain administrator username and password. Secret must contain
   * keys names 'username' and 'password' (Required)
   */
  @SerializedName("adminSecret")
  @Expose
  @Valid
  @NotNull
  private V1SecretReference adminSecret;

  /**
   * Admin server name. Note: Possibly temporary as we could find this value through domain home
   * inspection. (Required)
   */
  @SerializedName("asName")
  @Expose
  @NotNull
  private String asName;

  /**
   * Administration server port. Note: Possibly temporary as we could find this value through domain
   * home inspection. (Required)
   */
  @SerializedName("asPort")
  @Expose
  @NotNull
  private Integer asPort;

  /**
   * List of specific T3 channels to export. Named T3 Channels will be exposed using NodePort
   * Services. The internal and external ports must match; therefore, it is required that the
   * channel's port in the WebLogic configuration be a legal and unique value in the Kubernetes
   * cluster's legal NodePort port range.
   */
  @SerializedName("exportT3Channels")
  @Expose
  @Valid
  private List<String> exportT3Channels = new ArrayList<>();

  /**
   * Controls which managed servers will be started. Legal values are NONE, ADMIN, ALL, SPECIFIED
   * and AUTO.
   *
   * <ul>
   *   <li>NONE indicates that no servers, including the administration server, will be started.
   *   <li>ADMIN indicates that only the administration server will be started.
   *   <li>ALL indicates that all servers in the domain will be started.
   *   <li>SPECIFIED indicates that the administration server will be started and then additionally
   *       only those servers listed under serverStartup or managed servers belonging to cluster
   *       listed under clusterStartup up to the cluster's replicas field will be started.
   *   <li>AUTO indicates that servers will be started exactly as with SPECIFIED, but then managed
   *       servers belonging to clusters not listed under clusterStartup will be started up to the
   *       replicas field.
   * </ul>
   *
   * <p>Defaults to AUTO.
   */
  @SerializedName("startupControl")
  @Expose
  private String startupControl;

  /** List of server startup details for selected servers. */
  @SuppressWarnings("deprecation")
  @SerializedName("serverStartup")
  @Expose
  @Valid
  private List<ServerStartup> serverStartup = new ArrayList<>();

  /** List of server startup details for selected clusters. */
  @SuppressWarnings("deprecation")
  @SerializedName("clusterStartup")
  @Expose
  @Valid
  private List<ClusterStartup> clusterStartup = new ArrayList<>();

  /**
   * The desired number of running managed servers in each WebLogic cluster that is not explicitly
   * configured in clusterStartup.
   */
  @SerializedName("replicas")
  @Expose
  private Integer replicas;

  /**
   * The configuration for the admin server.
   *
   * @since 2.0
   */
  @SerializedName("adminServer")
  @Expose
  private AdminServer adminServer;

  /**
   * The configured managed servers.
   *
   * @since 2.0
   */
  @SerializedName("managedServers")
  @Expose
  private List<ManagedServer> managedServers = new ArrayList<>();

  /**
   * The configured clusters.
   *
   * @since 2.0
   */
  @SerializedName("clusters")
  @Expose
  protected List<Cluster> clusters = new ArrayList<>();

  String getEffectiveStartupControl() {
    return Optional.ofNullable(getStartupControl()).orElse(AUTO_STARTUPCONTROL).toUpperCase();
  }

  EffectiveConfigurationFactory getEffectiveConfigurationFactory(String apiVersion) {
    return useVersion2(apiVersion)
        ? new V2EffectiveConfigurationFactory()
        : new V1EffectiveConfigurationFactory();
  }

  private boolean useVersion2(String apiVersion) {
    return isVersion2Specified(apiVersion) || hasV2Configuration() || hasV2Fields();
  }

  private boolean isVersion2Specified(String apiVersion) {
    return KubernetesConstants.API_VERSION_ORACLE_V2.equals(apiVersion);
  }

  private boolean hasV2Configuration() {
    return adminServer != null || !managedServers.isEmpty() || !clusters.isEmpty();
  }

  @SuppressWarnings("deprecation")
  int getReplicaCount(ClusterStartup clusterStartup) {
    return hasReplicaCount(clusterStartup) ? clusterStartup.getReplicas() : getReplicas();
  }

  @SuppressWarnings("deprecation")
  private boolean hasReplicaCount(ClusterStartup clusterStartup) {
    return clusterStartup != null && clusterStartup.getReplicas() != null;
  }

  @SuppressWarnings("deprecation")
  private ClusterStartup getClusterStartup(String clusterName) {
    if (getClusterStartup() == null || clusterName == null) return null;

    for (ClusterStartup cs : getClusterStartup()) {
      if (clusterName.equals(cs.getClusterName())) {
        return cs;
      }
    }

    return null;
  }

  @SuppressWarnings("deprecation")
  private ClusterStartup getOrCreateClusterStartup(String clusterName) {
    ClusterStartup clusterStartup = getClusterStartup(clusterName);
    if (clusterStartup != null) {
      return clusterStartup;
    }

    ClusterStartup newClusterStartup = new ClusterStartup().withClusterName(clusterName);
    getClusterStartup().add(newClusterStartup);
    return newClusterStartup;
  }

  /**
   * Domain unique identifier. Must be unique across the Kubernetes cluster. (Required)
   *
   * @return domain UID
   */
  public String getDomainUID() {
    return domainUID;
  }

  /**
   * Domain unique identifier. Must be unique across the Kubernetes cluster. (Required)
   *
   * @param domainUID domain UID
   */
  public void setDomainUID(String domainUID) {
    this.domainUID = domainUID;
  }

  /**
   * Domain unique identifier. Must be unique across the Kubernetes cluster. (Required)
   *
   * @param domainUID domain UID
   * @return this
   */
  public DomainSpec withDomainUID(String domainUID) {
    this.domainUID = domainUID;
    return this;
  }

  /**
   * Domain name (Required)
   *
   * @return domain name
   */
  public String getDomainName() {
    return domainName;
  }

  /**
   * Domain name (Required)
   *
   * @param domainName domain name
   */
  public void setDomainName(String domainName) {
    this.domainName = domainName;
  }

  /**
   * Domain name (Required)
   *
   * @param domainName domain name
   * @return this
   */
  public DomainSpec withDomainName(String domainName) {
    this.domainName = domainName;
    return this;
  }

  /*
   * Fluent api for setting the image.
   *
   * @param image image
   * @return this
   */
  public DomainSpec withImage(String image) {
    setImage(image);
    return this;
  }

  /**
   * The image pull policy for the WebLogic Docker image. Legal values are Always, Never and
   * IfNotPresent.
   *
   * <p>Defaults to Always if image ends in :latest, IfNotPresent otherwise.
   *
   * <p>More info: https://kubernetes.io/docs/concepts/containers/images#updating-images
   *
   * @param imagePullPolicy image pull policy
   * @return this
   */
  public DomainSpec withImagePullPolicy(String imagePullPolicy) {
    setImagePullPolicy(imagePullPolicy);
    return this;
  }

  /**
   * The name of the secret used to authenticate a request for an image pull.
   *
   * <p>More info:
   * https://kubernetes.io/docs/concepts/containers/images/#referring-to-an-imagepullsecrets-on-a-pod
   */
  public DomainSpec withImagePullSecretName(String imagePullSecretName) {
    setImagePullSecret(new V1LocalObjectReference().name(imagePullSecretName));
    return this;
  }

  public V1SecretReference getAdminSecret() {
    return adminSecret;
  }

  @SuppressWarnings("unused")
  public void setAdminSecret(V1SecretReference adminSecret) {
    this.adminSecret = adminSecret;
  }

  /**
   * Reference to secret containing domain administrator username and password. Secret must contain
   * keys names 'username' and 'password' (Required)
   *
   * @param adminSecret admin secret
   * @return this
   */
  public DomainSpec withAdminSecret(V1SecretReference adminSecret) {
    this.adminSecret = adminSecret;
    return this;
  }

  public String getAsName() {
    return asName;
  }

  public void setAsName(String asName) {
    this.asName = asName;
  }

  /**
   * Admin server name. Note: Possibly temporary as we could find this value through domain home
   * inspection. (Required)
   *
   * @param asName admin server name
   * @return this
   */
  public DomainSpec withAsName(String asName) {
    this.asName = asName;
    return this;
  }

  public Integer getAsPort() {
    return asPort;
  }

  @SuppressWarnings("WeakerAccess")
  // public access is needed for yaml processing
  public void setAsPort(Integer asPort) {
    this.asPort = asPort;
  }

  /**
   * Administration server port. Note: Possibly temporary as we could find this value through domain
   * home inspection. (Required)
   *
   * @param asPort admin server port
   * @return this
   */
  public DomainSpec withAsPort(Integer asPort) {
    this.asPort = asPort;
    return this;
  }

  /**
   * List of specific T3 channels to export. Named T3 Channels will be exposed using NodePort
   * Services. The internal and external ports must match; therefore, it is required that the
   * channel's port in the WebLogic configuration be a legal and unique value in the Kubernetes
   * cluster's legal NodePort port range.
   *
   * @return exported channels
   */
  public List<String> getExportT3Channels() {
    return exportT3Channels;
  }

  /**
   * List of specific T3 channels to export. Named T3 Channels will be exposed using NodePort
   * Services. The internal and external ports must match; therefore, it is required that the
   * channel's port in the WebLogic configuration be a legal and unique value in the Kubernetes
   * cluster's legal NodePort port range.
   *
   * @param exportT3Channels exported channels
   */
  public void setExportT3Channels(List<String> exportT3Channels) {
    this.exportT3Channels = exportT3Channels;
  }

  /**
   * List of specific T3 channels to export. Named T3 Channels will be exposed using NodePort
   * Services. The internal and external ports must match; therefore, it is required that the
   * channel's port in the WebLogic configuration be a legal and unique value in the Kubernetes
   * cluster's legal NodePort port range.
   *
   * @param exportT3Channels exported channels
   * @return this
   */
  @SuppressWarnings("UnusedReturnValue")
  public DomainSpec withExportT3Channels(List<String> exportT3Channels) {
    this.exportT3Channels = exportT3Channels;
    return this;
  }

  /**
   * Controls which managed servers will be started. Legal values are NONE, ADMIN, ALL, SPECIFIED
   * and AUTO.
   *
   * <ul>
   *   <li>NONE indicates that no servers, including the administration server, will be started.
   *   <li>ADMIN indicates that only the administration server will be started.
   *   <li>ALL indicates that all servers in the domain will be started.
   *   <li>SPECIFIED indicates that the administration server will be started and then additionally
   *       only those servers listed under serverStartup or managed servers belonging to cluster
   *       listed under clusterStartup up to the cluster's replicas field will be started.
   *   <li>AUTO indicates that servers will be started exactly as with SPECIFIED, but then managed
   *       servers belonging to clusters not listed under clusterStartup will be started up to the
   *       replicas field.
   * </ul>
   *
   * <p>Defaults to AUTO.
   *
   * @return startup control
   */
  public String getStartupControl() {
    return startupControl;
  }

  /**
   * Controls which managed servers will be started. Legal values are NONE, ADMIN, ALL, SPECIFIED
   * and AUTO.
   *
   * <ul>
   *   <li>NONE indicates that no servers, including the administration server, will be started.
   *   <li>ADMIN indicates that only the administration server will be started.
   *   <li>ALL indicates that all servers in the domain will be started.
   *   <li>SPECIFIED indicates that the administration server will be started and then additionally
   *       only those servers listed under serverStartup or managed servers belonging to cluster
   *       listed under clusterStartup up to the cluster's replicas field will be started.
   *   <li>AUTO indicates that servers will be started exactly as with SPECIFIED, but then managed
   *       servers belonging to clusters not listed under clusterStartup will be started up to the
   *       replicas field.
   * </ul>
   *
   * <p>Defaults to AUTO.
   *
   * @param startupControl startup control
   */
  public void setStartupControl(String startupControl) {
    this.startupControl = startupControl;
  }

  /**
   * Controls which managed servers will be started. Legal values are NONE, ADMIN, ALL, SPECIFIED
   * and AUTO.
   *
   * <ul>
   *   <li>NONE indicates that no servers, including the administration server, will be started.
   *   <li>ADMIN indicates that only the administration server will be started.
   *   <li>ALL indicates that all servers in the domain will be started.
   *   <li>SPECIFIED indicates that the administration server will be started and then additionally
   *       only those servers listed under serverStartup or managed servers belonging to cluster
   *       listed under clusterStartup up to the cluster's replicas field will be started.
   *   <li>AUTO indicates that servers will be started exactly as with SPECIFIED, but then managed
   *       servers belonging to clusters not listed under clusterStartup will be started up to the
   *       replicas field.
   * </ul>
   *
   * <p>Defaults to AUTO.
   *
   * @param startupControl startup control
   * @return this
   */
  public DomainSpec withStartupControl(String startupControl) {
    this.startupControl = startupControl;
    return this;
  }

  /**
   * List of server startup details for selected servers.
   *
   * @deprecated use {@link Domain#getServer(String, String)} to get the effective settings for a
   *     specific server.
   * @return server startup
   */
  @SuppressWarnings({"WeakerAccess", "DeprecatedIsStillUsed"})
  @Deprecated
  // public access is needed for yaml processing
  public List<ServerStartup> getServerStartup() {
    return serverStartup;
  }

  /**
   * List of server startup details for selected servers.
   *
   * @deprecated use {@link
   *     oracle.kubernetes.weblogic.domain.DomainConfigurator#configureServer(String)} to configure
   *     a server.
   * @param serverStartup server startup
   */
  @Deprecated
  public void setServerStartup(List<ServerStartup> serverStartup) {
    this.serverStartup = serverStartup;
  }

  /**
   * Add server startup details for one servers.
   *
   * @param serverStartupItem a single item to add
   */
  @SuppressWarnings("deprecation")
  void addServerStartupItem(ServerStartup serverStartupItem) {
    if (serverStartup == null) serverStartup = new ArrayList<>();
    serverStartup.add(serverStartupItem);
  }

  /**
   * List of startup details for selected clusters
   *
   * @deprecated use {@link Domain#getReplicaCount(String)} to obtain the effective replica count
   *     for a cluster, or {@link Domain#getServer(String, String)} to get any other settings
   *     controlled by a cluster configuration.
   * @return cluster startup
   */
  @SuppressWarnings({"WeakerAccess", "DeprecatedIsStillUsed"})
  @Deprecated
  // public access is needed for yaml processing
  public List<ClusterStartup> getClusterStartup() {
    return clusterStartup;
  }

  /**
   * List of server startup details for selected clusters.
   *
   * @deprecated use {@link
   *     oracle.kubernetes.weblogic.domain.DomainConfigurator#configureCluster(String)} to configure
   *     a cluster.
   * @param clusterStartup cluster startup
   */
  @SuppressWarnings("WeakerAccess")
  @Deprecated
  // public access is needed for yaml processing
  public void setClusterStartup(List<ClusterStartup> clusterStartup) {
    this.clusterStartup = clusterStartup;
  }

  /**
   * Add startup details for a cluster
   *
   * @param clusterStartupItem cluster startup
   */
  @SuppressWarnings("deprecation")
  void addClusterStartupItem(ClusterStartup clusterStartupItem) {
    if (clusterStartup == null) clusterStartup = new ArrayList<>();
    clusterStartup.add(clusterStartupItem);
  }

  /**
   * Replicas is the desired number of managed servers running in each WebLogic cluster that is not
   * configured under clusterStartup. Provided so that administrators can scale the Domain resource.
   * Ignored if startupControl is not AUTO.
   *
   * @deprecated use {@link Domain#getReplicaCount(String)} to obtain the effective setting.
   * @return replicas
   */
  @SuppressWarnings("DeprecatedIsStillUsed")
  @Deprecated
  public Integer getReplicas() {
    return replicas != null ? replicas : Domain.DEFAULT_REPLICA_LIMIT;
  }

  /**
   * The desired number of running managed servers in each WebLogic cluster that is not explicitly
   * configured in clusterStartup.
   *
   * @param replicas replicas
   */
  public void setReplicas(Integer replicas) {
    this.replicas = replicas;
  }

  /**
   * The desired number of running managed servers in each WebLogic cluster that is not explicitly
   * configured in clusterStartup.
   *
   * @param replicas replicas
   * @return this
   */
  public DomainSpec withReplicas(Integer replicas) {
    this.replicas = replicas;
    return this;
  }

  @Override
  public String toString() {
    return new ToStringBuilder(this)
        .appendSuper(super.toString())
        .append("domainUID", domainUID)
        .append("domainName", domainName)
        .append("adminSecret", adminSecret)
        .append("asName", asName)
        .append("asPort", asPort)
        .append("exportT3Channels", exportT3Channels)
        .append("startupControl", startupControl)
        .append("serverStartup", serverStartup)
        .append("clusterStartup", clusterStartup)
        .append("replicas", replicas)
        .toString();
  }

  @Override
  public int hashCode() {
    return new HashCodeBuilder()
        .appendSuper(super.hashCode())
        .append(asName)
        .append(replicas)
        .append(startupControl)
        .append(domainUID)
        .append(clusterStartup)
        .append(asPort)
        .append(domainName)
        .append(exportT3Channels)
        .append(serverStartup)
        .append(adminSecret)
        .toHashCode();
  }

  @Override
  public boolean equals(Object other) {
    if (other == this) return true;
    if (!(other instanceof DomainSpec)) return false;

    DomainSpec rhs = ((DomainSpec) other);
    return new EqualsBuilder()
        .appendSuper(super.equals(other))
        .append(asName, rhs.asName)
        .append(replicas, rhs.replicas)
        .append(startupControl, rhs.startupControl)
        .append(domainUID, rhs.domainUID)
        .append(clusterStartup, rhs.clusterStartup)
        .append(asPort, rhs.asPort)
        .append(domainName, rhs.domainName)
        .append(exportT3Channels, rhs.exportT3Channels)
        .append(serverStartup, rhs.serverStartup)
        .append(adminSecret, rhs.adminSecret)
        .isEquals();
  }

  private Server getServer(String serverName) {
    for (ManagedServer managedServer : managedServers) {
      if (Objects.equals(serverName, managedServer.getServerName())) return managedServer;
    }

    return null;
  }

  private Cluster getCluster(String clusterName) {
    for (Cluster cluster : clusters) {
      if (Objects.equals(clusterName, cluster.getClusterName())) return cluster;
    }

    return null;
  }

  private int getReplicaCountFor(Cluster cluster) {
    return hasReplicaCount(cluster) ? cluster.getReplicas() : Domain.DEFAULT_REPLICA_LIMIT;
  }

  private boolean hasReplicaCount(Cluster cluster) {
    return cluster != null && cluster.getReplicas() != null;
  }

  public AdminServer getAdminServer() {
    return adminServer;
  }

  public void setAdminServer(AdminServer adminServer) {
    this.adminServer = adminServer;
  }

  public List<ManagedServer> getManagedServers() {
    return managedServers;
  }

  public List<Cluster> getClusters() {
    return clusters;
  }

  private class V1EffectiveConfigurationFactory implements EffectiveConfigurationFactory {
    @Override
    public ServerSpec getAdminServerSpec() {
      return new ServerSpecV1Impl(DomainSpec.this, null, getServerStartup(getAsName()), null);
    }

    @Override
    public ServerSpec getServerSpec(String serverName, String clusterName) {
      return new ServerSpecV1Impl(
          DomainSpec.this,
          clusterName,
          getServerStartup(serverName),
          getClusterStartup(clusterName));
    }

    @Override
    public int getReplicaCount(String clusterName) {
      return DomainSpec.this.getReplicaCount(getClusterStartup(clusterName));
    }

    @Override
    public void setReplicaCount(String clusterName, int replicaCount) {
      getOrCreateClusterStartup(clusterName).setReplicas(replicaCount);
    }

    @SuppressWarnings("deprecation")
    private ServerStartup getServerStartup(String name) {
      if (DomainSpec.this.getServerStartup() == null) return null;

      for (ServerStartup ss : DomainSpec.this.getServerStartup()) {
        if (ss.getServerName().equals(name)) {
          return ss;
        }
      }

      return null;
    }
  }

  class V2EffectiveConfigurationFactory implements EffectiveConfigurationFactory {
    @Override
    public ServerSpec getAdminServerSpec() {
      return new ServerSpecV2Impl(adminServer, DomainSpec.this);
    }

    @Override
    public ServerSpec getServerSpec(String serverName, String clusterName) {
      return new ServerSpecV2Impl(getServer(serverName), getCluster(clusterName), DomainSpec.this);
    }

    @Override
    public int getReplicaCount(String clusterName) {
      return getReplicaCountFor(getCluster(clusterName));
    }

    @Override
    public void setReplicaCount(String clusterName, int replicaCount) {
      getOrCreateCluster(clusterName).setReplicas(replicaCount);
    }

    private Cluster getOrCreateCluster(String clusterName) {
      Cluster cluster = getCluster(clusterName);
      if (cluster != null) return cluster;

      return createClusterWithName(clusterName);
    }

    private Cluster createClusterWithName(String clusterName) {
      Cluster cluster = new Cluster().withClusterName(clusterName);
      clusters.add(cluster);
      return cluster;
    }
  }
}

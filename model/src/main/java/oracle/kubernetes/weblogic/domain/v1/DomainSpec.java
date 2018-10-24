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
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import javax.annotation.Nonnull;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import oracle.kubernetes.operator.StartupControlConstants;
import oracle.kubernetes.operator.VersionConstants;
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

  /** The pattern for computing the default persistent volume claim name. */
  private static final String PVC_NAME_PATTERN = "%s-weblogic-domain-pvc";

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
   * List of T3 network access points to export, along with label and annotations to apply to
   * corresponding channel services.
   *
   * @since 2.0
   */
  private Map<String, ExportedNetworkAccessPoint> exportedNetworkAccessPoints = new HashMap<>();

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
   * @deprecated as of 2.0, use BaseConfiguration#serverStartPolicy
   */
  @SuppressWarnings("DeprecatedIsStillUsed")
  @Deprecated
  @SerializedName("startupControl")
  @Expose
  private String startupControl;

  /**
   * List of server startup details for selected servers.
   *
   * @deprecated as of 2.0 use #managedServers field instead
   */
  @SuppressWarnings({"deprecation", "DeprecatedIsStillUsed"})
  @Deprecated
  @SerializedName("serverStartup")
  @Expose
  @Valid
  private List<ServerStartup> serverStartup = new ArrayList<>();

  /**
   * List of server startup details for selected clusters.
   *
   * @deprecated as of 2.0 use #clusters field instead
   */
  @SuppressWarnings({"deprecation", "DeprecatedIsStillUsed"})
  @Deprecated
  @SerializedName("clusterStartup")
  @Expose
  @Valid
  private List<ClusterStartup> clusterStartup = new ArrayList<>();

  /**
   * The desired number of running managed servers in each WebLogic cluster that is not explicitly
   * configured in clusterStartup.
   *
   * @deprecated as of 2.0 defaults to Domain.DEFAULT_REPLICA_LIMIT
   */
  @Deprecated
  @SerializedName("replicas")
  @Expose
  private Integer replicas;

  /**
   * Whether the domain home is part of the image.
   *
   * @since 2.0
   */
  @SerializedName("domainHomeInImage")
  @Expose
  private boolean domainHomeInImage;

  /** The definition of the storage used for this domain. */
  @SerializedName("storage")
  @Expose
  private DomainStorage storage;

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

  @SuppressWarnings("deprecation")
  String getEffectiveStartupControl() {
    return Optional.ofNullable(getStartupControl()).orElse(AUTO_STARTUPCONTROL).toUpperCase();
  }

  EffectiveConfigurationFactory getEffectiveConfigurationFactory(String resourceVersionLabel) {
    return useVersion2(resourceVersionLabel)
        ? new V2EffectiveConfigurationFactory()
        : new V1EffectiveConfigurationFactory();
  }

  private boolean useVersion2(String resourceVersionLabel) {
    return isVersion2Specified(resourceVersionLabel) || hasV2Configuration() || hasV2Fields();
  }

  private boolean isVersion2Specified(String resourceVersionLabel) {
    return VersionConstants.DOMAIN_V2.equals(resourceVersionLabel);
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
   * Map of T3 network access points to export, with associated labels/annotations.
   *
   * @return exported channels
   */
  public Map<String, ExportedNetworkAccessPoint> getExportedNetworkAccessPoints() {
    return exportedNetworkAccessPoints;
  }

  /**
   * Configures an exported T3 network access point.
   *
   * @param name the name of the NAP
   */
  public ExportedNetworkAccessPoint addExportedNetworkAccessPoint(String name) {
    if (exportedNetworkAccessPoints == null) exportedNetworkAccessPoints = new HashMap<>();

    ExportedNetworkAccessPoint exportedNetworkAccessPoint = new ExportedNetworkAccessPoint();
    exportedNetworkAccessPoints.put(name, exportedNetworkAccessPoint);
    return exportedNetworkAccessPoint;
  }

  /**
   * Returns true if this domain's home is defined in the default docker image for the domain.
   *
   * @return true or false
   * @since 2.0
   */
  public boolean isDomainHomeInImage() {
    return domainHomeInImage;
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
   * @deprecated as of 2.0, use BaseConfiguration#setServerStartPolicy
   */
  @SuppressWarnings({"deprecation", "DeprecatedIsStillUsed"})
  @Deprecated
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
   * @deprecated as of 2.0, using BaseConfiguration#setServerStartPolicy
   * @param startupControl startup control
   */
  @SuppressWarnings("DeprecatedIsStillUsed")
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
   * @deprecated as of 2.0, using BaseConfiguration#withServerStartPolicy
   * @param startupControl startup control
   * @return this
   */
  @SuppressWarnings("deprecation")
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
  @SuppressWarnings("deprecation")
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
  @SuppressWarnings("deprecation")
  public DomainSpec withReplicas(Integer replicas) {
    this.replicas = replicas;
    return this;
  }

  /**
   * Specifies the storage for the domain.
   *
   * @param storage the definition of the domain storage.
   */
  public void setStorage(DomainStorage storage) {
    this.storage = storage;
  }

  /**
   * Returns the storage for the domain.
   *
   * @return the definition of the domain storage.
   */
  public DomainStorage getStorage() {
    return storage;
  }

  /**
   * Returns the name of the persistent volume claim for the logs and PV-based domain.
   *
   * @return volume claim
   */
  String getPersistentVolumeClaimName() {
    return storage == null ? null : getConfiguredClaimName(storage);
  }

  private String getConfiguredClaimName(@Nonnull DomainStorage storage) {
    return Optional.ofNullable(storage.getPersistentVolumeClaimName())
        .orElse(String.format(PVC_NAME_PATTERN, domainUID));
  }

  @SuppressWarnings("deprecation")
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
        .append("storage", storage)
        .toString();
  }

  @SuppressWarnings("deprecation")
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
        .append(storage)
        .toHashCode();
  }

  @SuppressWarnings("deprecation")
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
        .append(storage, rhs.storage)
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

    @Override
    public boolean isShuttingDown() {
      return StartupControlConstants.NONE_STARTUPCONTROL.equals(getEffectiveStartupControl());
    }

    @Override
    public List<String> getExportedNetworkAccessPointNames() {
      return Optional.ofNullable(exportT3Channels).orElse(Collections.emptyList());
    }

    @Override
    public Map<String, String> getChannelServiceLabels(String channel) {
      return Collections.emptyMap();
    }

    @Override
    public Map<String, String> getChannelServiceAnnotations(String channel) {
      return Collections.emptyMap();
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
      return new ServerSpecV2Impl(adminServer, null, DomainSpec.this);
    }

    @Override
    public ServerSpec getServerSpec(String serverName, String clusterName) {
      return new ServerSpecV2Impl(
          getServer(serverName),
          getClusterLimit(clusterName),
          getCluster(clusterName),
          DomainSpec.this);
    }

    private Integer getClusterLimit(String clusterName) {
      return clusterName == null ? null : getReplicaCount(clusterName);
    }

    @Override
    public boolean isShuttingDown() {
      return !getAdminServerSpec().shouldStart(0);
    }

    @Override
    public int getReplicaCount(String clusterName) {
      return getReplicaCountFor(getCluster(clusterName));
    }

    @Override
    public void setReplicaCount(String clusterName, int replicaCount) {
      getOrCreateCluster(clusterName).setReplicas(replicaCount);
    }

    @Override
    public List<String> getExportedNetworkAccessPointNames() {
      return new ArrayList<>(exportedNetworkAccessPoints.keySet());
    }

    @Override
    public Map<String, String> getChannelServiceLabels(String channelName) {
      ExportedNetworkAccessPoint accessPoint = exportedNetworkAccessPoints.get(channelName);

      return accessPoint == null ? Collections.emptyMap() : accessPoint.getLabels();
    }

    @Override
    public Map<String, String> getChannelServiceAnnotations(String channel) {
      ExportedNetworkAccessPoint accessPoint = exportedNetworkAccessPoints.get(channel);

      return accessPoint == null ? Collections.emptyMap() : accessPoint.getAnnotations();
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

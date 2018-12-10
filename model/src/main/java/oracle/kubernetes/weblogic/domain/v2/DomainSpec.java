// Copyright 2017, 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.weblogic.domain.v2;

import static oracle.kubernetes.weblogic.domain.v2.ConfigurationConstants.START_IF_NEEDED;

import io.kubernetes.client.models.V1LocalObjectReference;
import io.kubernetes.client.models.V1SecretReference;
import java.util.*;
import javax.annotation.Nullable;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import oracle.kubernetes.json.Description;
import oracle.kubernetes.json.EnumClass;
import oracle.kubernetes.json.Pattern;
import oracle.kubernetes.json.Range;
import oracle.kubernetes.operator.ImagePullPolicy;
import oracle.kubernetes.operator.KubernetesConstants;
import oracle.kubernetes.weblogic.domain.EffectiveConfigurationFactory;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;

/** DomainSpec is a description of a domain. */
public class DomainSpec extends BaseConfiguration {

  /** Domain unique identifier. Must be unique across the Kubernetes cluster. */
  @Description(
      "Domain unique identifier. Must be unique across the Kubernetes cluster. (Not required)"
          + "Defaults to the value of metadata.name")
  @Pattern("^[a-z0-9_.]{1,253}$")
  private String domainUID;

  /**
   * Domain home
   *
   * @since 2.0
   */
  @Description(
      "The folder for the Weblogic Domain. (Not required)"
          + "Defaults to /shared/domains/domains/domainUID if domainHomeInImage is false"
          + "Defaults to /shared/domains/domain if domainHomeInImage is true")
  private String domainHome;

  /**
   * Reference to secret containing domain administrator username and password. Secret must contain
   * keys names 'username' and 'password' (Required)
   */
  @Valid @NotNull private V1SecretReference adminSecret;

  /**
   * The in-pod name of the directory to store the domain, node manager, server logs, and server
   * .out files in.
   */
  @Description(
      "The in-pod name of the directory in which to store the domain, node manager, server logs, and server  *.out files")
  private String logHome;

  /**
   * Whether the log home is enabled.
   *
   * @since 2.0
   */
  @Description(
      "Specified whether the log home folder is enabled (Not required). "
          + "Defaults to true if domainHomeInImage is false. "
          + "Defaults to false if domainHomeInImage is true. ")
  private Boolean logHomeEnabled; // Boolean object, null if unspecified

  /** Whether to include the server .out file to the pod's stdout. Default is true. */
  @Description("If true (the default), the server .out file will be included in the pod's stdout")
  private Boolean includeServerOutInPodLog;

  /**
   * The WebLogic Docker image.
   *
   * <p>Defaults to store/oracle/weblogic:12.2.1.3
   */
  @Description(
      "The Weblogic Docker image; required when domainHomeInImage is true; "
          + "otherwise, defaults to store/oracle/weblogic:12.2.1.3")
  private String image;

  /**
   * The image pull policy for the WebLogic Docker image. Legal values are Always, Never and
   * IfNotPresent.
   *
   * <p>Defaults to Always if image ends in :latest, IfNotPresent otherwise.
   *
   * <p>More info: https://kubernetes.io/docs/concepts/containers/images#updating-images
   */
  @Description(
      "The image pull policy for the WebLogic Docker image. "
          + "Legal values are Always, Never and IfNotPresent. "
          + "Defaults to Always if image ends in :latest, IfNotPresent otherwise")
  @EnumClass(ImagePullPolicy.class)
  private String imagePullPolicy;

  /**
   * The image pull secrets for the WebLogic Docker image.
   *
   * <p>More info:
   * https://kubernetes.io/docs/reference/generated/kubernetes-api/v1.10/#localobjectreference-v1-core
   *
   * @since 2.0
   */
  @Description("A list of image pull secrets for the WebLogic Docker image.")
  private List<V1LocalObjectReference> imagePullSecrets;

  /**
   * The desired number of running managed servers in each WebLogic cluster that is not explicitly
   * configured in a cluster specification.
   */
  @Description(
      "The number of managed servers to run in any cluster that does not specify a replica count")
  @Range(minimum = 0)
  private Integer replicas;

  /**
   * Whether the domain home is part of the image.
   *
   * @since 2.0
   */
  @Description(
      "True if this domain's home is defined in the docker image for the domain. Defaults to true")
  private Boolean domainHomeInImage;

  /** The definition of the storage used for this domain. */
  @Description(
      "The storage used for this domain. "
          + "Defaults to a predefined claim for a PVC whose name is "
          + "the domain UID followed by '-weblogic-domain-pvc'")
  private DomainStorage storage;

  /**
   * The name of the Kubernetes configmap used for optional WebLogic configuration overrides.
   *
   * @since 2.0
   */
  @Description("The name of the configmap for optional WebLogic configuration overrides.")
  private String configOverrides;

  /**
   * A list of names of the Kubernetes secrets used in the WebLogic Configuration overrides.
   *
   * @since 2.0
   */
  @Description("A list of names of the secrets for optional WebLogic configuration overrides.")
  private List<String> configOverrideSecrets;

  /**
   * The configuration for the admin server.
   *
   * @since 2.0
   */
  @Description("Configuration for the admin server")
  private AdminServer adminServer;

  /**
   * The configured managed servers.
   *
   * @since 2.0
   */
  @Description("Configuration for the managed servers")
  private Map<String, ManagedServer> managedServers = new HashMap<>();

  /**
   * The configured clusters.
   *
   * @since 2.0
   */
  @Description("Configuration for the clusters")
  protected Map<String, Cluster> clusters = new HashMap<>();

  /**
   * Adds a Cluster to the DomainSpec
   *
   * @param cluster
   * @return
   */
  public DomainSpec withCluster(Cluster cluster) {
    clusters.put(cluster.getClusterName(), cluster);
    return this;
  }

  AdminServer getOrCreateAdminServer() {
    if (adminServer != null) return adminServer;

    return createAdminServer();
  }

  private AdminServer createAdminServer() {
    AdminServer adminServer = new AdminServer();
    setAdminServer(adminServer);
    return adminServer;
  }

  @SuppressWarnings("unused")
  EffectiveConfigurationFactory getEffectiveConfigurationFactory(String resourceVersionLabel) {
    return new V2EffectiveConfigurationFactory();
  }

  /**
   * Domain unique identifier. Must be unique across the Kubernetes cluster. (Not required) Defaults
   * to the value of metadata.name
   *
   * @return domain UID
   */
  public String getDomainUID() {
    return domainUID;
  }

  /**
   * Domain unique identifier. Must be unique across the Kubernetes cluster. (Not required) Defaults
   * to the value of metadata.name
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
   * Domain home
   *
   * @since 2.0
   * @return domain home
   */
  String getDomainHome() {
    return domainHome;
  }

  /**
   * Domain home
   *
   * @since 2.0
   * @param domainHome domain home
   */
  public void setDomainHome(String domainHome) {
    this.domainHome = domainHome;
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

  @Nullable
  public String getImage() {
    return image;
  }

  public void setImage(@Nullable String image) {
    this.image = image;
  }

  @Nullable
  public String getImagePullPolicy() {
    return imagePullPolicy;
  }

  public void setImagePullPolicy(@Nullable String imagePullPolicy) {
    this.imagePullPolicy = imagePullPolicy;
  }

  private boolean hasImagePullSecrets() {
    return imagePullSecrets != null && imagePullSecrets.size() != 0;
  }

  @Nullable
  public List<V1LocalObjectReference> getImagePullSecrets() {
    if (hasImagePullSecrets()) return imagePullSecrets;
    else return Collections.emptyList();
  }

  public void setImagePullSecret(@Nullable V1LocalObjectReference imagePullSecret) {
    imagePullSecrets = Collections.singletonList(imagePullSecret);
  }

  public void setImagePullSecrets(@Nullable List<V1LocalObjectReference> imagePullSecrets) {
    this.imagePullSecrets = imagePullSecrets;
  }

  /**
   * Log Home
   *
   * @return The in-pod name of the directory to store the domain, node manager, server logs, and
   *     server .out files in.
   */
  String getLogHome() {
    return logHome;
  }

  public void setLogHome(String logHome) {
    this.logHome = logHome;
  }

  /**
   * Log home enabled
   *
   * @since 2.0
   * @return log home enabled
   */
  boolean getLogHomeEnabled() {
    return Optional.ofNullable(logHomeEnabled).orElse(!isDomainHomeInImage());
  }

  /**
   * Log home enabled
   *
   * @since 2.0
   * @param logHomeEnabled log home enabled
   */
  public void setLogHomeEnabled(boolean logHomeEnabled) {
    this.logHomeEnabled = logHomeEnabled;
  }

  /**
   * Whether to include server .out to the pod's stdout
   *
   * @return whether server .out should be included in pod's stdout.
   * @since 2.0
   */
  boolean getIncludeServerOutInPodLog() {
    return Optional.ofNullable(includeServerOutInPodLog)
        .orElse(KubernetesConstants.DEFAULT_INCLUDE_SERVER_OUT_IN_POD_LOG);
  }

  public DomainSpec withIncludeServerOutInPodLog(boolean includeServerOutInPodLog) {
    this.includeServerOutInPodLog = includeServerOutInPodLog;
    return this;
  }

  /**
   * Returns true if this domain's home is defined in the default docker image for the domain.
   *
   * @return true or false
   * @since 2.0
   */
  boolean isDomainHomeInImage() {
    return Optional.ofNullable(domainHomeInImage).orElse(true);
  }

  /**
   * Specifies whether the domain home is stored in the image
   *
   * @param domainHomeInImage true if the domain home is in the image
   */
  public void setDomainHomeInImage(boolean domainHomeInImage) {
    this.domainHomeInImage = domainHomeInImage;
  }

  public DomainSpec withDomainHomeInImage(boolean domainHomeInImage) {
    setDomainHomeInImage(domainHomeInImage);
    return this;
  }

  /**
   * The desired number of running managed servers in each WebLogic cluster that is not explicitly
   * configured in clusters.
   *
   * @param replicas replicas
   */
  public void setReplicas(Integer replicas) {
    this.replicas = replicas;
  }

  /**
   * The desired number of running managed servers in each WebLogic cluster that is not explicitly
   * configured in clusters.
   *
   * @param replicas replicas
   * @return this
   */
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

  @Nullable
  String getConfigOverrides() {
    return configOverrides;
  }

  void setConfigOverrides(@Nullable String overridess) {
    this.configOverrides = overridess;
  }

  public DomainSpec withConfigOverrides(@Nullable String overridess) {
    this.configOverrides = overridess;
    return this;
  }

  private boolean hasConfigOverrideSecrets() {
    return configOverrideSecrets != null && configOverrideSecrets.size() != 0;
  }

  @Nullable
  List<String> getConfigOverrideSecrets() {
    if (hasConfigOverrideSecrets()) return configOverrideSecrets;
    else return Collections.emptyList();
  }

  public void setConfigOverrideSecrets(@Nullable List<String> overridesSecretNames) {
    this.configOverrideSecrets = overridesSecretNames;
  }

  @Nullable
  @Override
  protected String getServerStartPolicy() {
    return Optional.ofNullable(super.getServerStartPolicy()).orElse(START_IF_NEEDED);
  }

  @Override
  public String toString() {
    ToStringBuilder builder =
        new ToStringBuilder(this)
            .appendSuper(super.toString())
            .append("domainUID", domainUID)
            .append("domainHome", domainHome)
            .append("domainHomeInImage", domainHomeInImage)
            .append("adminSecret", adminSecret)
            .append("image", image)
            .append("imagePullPolicy", imagePullPolicy)
            .append("storage", storage)
            .append("imagePullSecrets", imagePullSecrets)
            .append("adminServer", adminServer)
            .append("managedServers", managedServers)
            .append("clusters", clusters)
            .append("replicas", replicas)
            .append("logHome", logHome)
            .append("logHomeEnabled", logHomeEnabled)
            .append("includeServerOutInPodLog", includeServerOutInPodLog)
            .append("configOverrides", configOverrides)
            .append("configOverrideSecrets", configOverrideSecrets);

    return builder.toString();
  }

  @Override
  public int hashCode() {
    HashCodeBuilder builder =
        new HashCodeBuilder()
            .appendSuper(super.hashCode())
            .append(domainUID)
            .append(domainHome)
            .append(domainHomeInImage)
            .append(adminSecret)
            .append(image)
            .append(imagePullPolicy)
            .append(storage)
            .append(imagePullSecrets)
            .append(adminServer)
            .append(managedServers)
            .append(clusters)
            .append(replicas)
            .append(logHome)
            .append(logHomeEnabled)
            .append(includeServerOutInPodLog)
            .append(configOverrides)
            .append(configOverrideSecrets);

    return builder.toHashCode();
  }

  @Override
  public boolean equals(Object other) {
    if (other == this) return true;
    if (!(other instanceof DomainSpec)) return false;

    DomainSpec rhs = ((DomainSpec) other);
    EqualsBuilder builder =
        new EqualsBuilder()
            .appendSuper(super.equals(other))
            .append(domainUID, rhs.domainUID)
            .append(domainHome, rhs.domainHome)
            .append(domainHomeInImage, rhs.domainHomeInImage)
            .append(adminSecret, rhs.adminSecret)
            .append(image, rhs.image)
            .append(storage, rhs.storage)
            .append(imagePullPolicy, rhs.imagePullPolicy)
            .append(imagePullSecrets, rhs.imagePullSecrets)
            .append(adminServer, rhs.adminServer)
            .append(managedServers, rhs.managedServers)
            .append(clusters, rhs.clusters)
            .append(replicas, rhs.replicas)
            .append(logHome, rhs.logHome)
            .append(logHomeEnabled, rhs.logHomeEnabled)
            .append(includeServerOutInPodLog, rhs.includeServerOutInPodLog)
            .append(configOverrides, rhs.configOverrides)
            .append(configOverrideSecrets, rhs.configOverrideSecrets);

    return builder.isEquals();
  }

  private Server getServer(String serverName) {
    return managedServers.get(serverName);
  }

  private Cluster getCluster(String clusterName) {
    return clusters.get(clusterName);
  }

  private int getReplicaCountFor(Cluster cluster) {
    return hasReplicaCount(cluster)
        ? cluster.getReplicas()
        : Optional.ofNullable(replicas).orElse(0);
  }

  private boolean hasReplicaCount(Cluster cluster) {
    return cluster != null && cluster.getReplicas() != null;
  }

  private int getMaxUnavailableFor(Cluster cluster) {
    return hasMaxUnavailable(cluster) ? cluster.getMaxUnavailable() : 1;
  }

  private boolean hasMaxUnavailable(Cluster cluster) {
    return cluster != null && cluster.getMaxUnavailable() != null;
  }

  private AdminServer getAdminServer() {
    return Optional.ofNullable(adminServer).orElse(AdminServer.NULL_ADMIN_SERVER);
  }

  private void setAdminServer(AdminServer adminServer) {
    this.adminServer = adminServer;
  }

  public Map<String, ManagedServer> getManagedServers() {
    return managedServers;
  }

  public Map<String, Cluster> getClusters() {
    return clusters;
  }

  class V2EffectiveConfigurationFactory implements EffectiveConfigurationFactory {
    @Override
    public ServerSpec getAdminServerSpec() {
      return new AdminServerSpecV2Impl(DomainSpec.this, adminServer);
    }

    @Override
    public ServerSpec getServerSpec(String serverName, String clusterName) {
      return new ManagedServerSpecV2Impl(
          DomainSpec.this,
          getServer(serverName),
          getCluster(clusterName),
          getClusterLimit(clusterName));
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
    public int getMaxUnavailable(String clusterName) {
      return getMaxUnavailableFor(getCluster(clusterName));
    }

    @Override
    public List<String> getExportedNetworkAccessPointNames() {
      return getAdminServer().getExportedNetworkAccessPointNames();
    }

    @Override
    public Map<String, String> getChannelServiceLabels(String napName) {
      ExportedNetworkAccessPoint accessPoint = getExportedNetworkAccessPoint(napName);

      return accessPoint == null ? Collections.emptyMap() : accessPoint.getLabels();
    }

    private ExportedNetworkAccessPoint getExportedNetworkAccessPoint(String napName) {
      return getAdminServer().getExportedNetworkAccessPoint(napName);
    }

    @Override
    public Map<String, String> getChannelServiceAnnotations(String napName) {
      ExportedNetworkAccessPoint accessPoint = getExportedNetworkAccessPoint(napName);

      return accessPoint == null ? Collections.emptyMap() : accessPoint.getAnnotations();
    }

    @Override
    public Integer getDefaultReplicaLimit() {
      return 0;
    }

    private Cluster getOrCreateCluster(String clusterName) {
      Cluster cluster = getCluster(clusterName);
      if (cluster != null) return cluster;

      return createClusterWithName(clusterName);
    }

    private Cluster createClusterWithName(String clusterName) {
      Cluster cluster = new Cluster().withClusterName(clusterName);
      clusters.put(clusterName, cluster);
      return cluster;
    }
  }
}

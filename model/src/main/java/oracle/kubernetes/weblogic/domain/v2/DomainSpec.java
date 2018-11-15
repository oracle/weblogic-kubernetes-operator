// Copyright 2017, 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.weblogic.domain.v2;

import static oracle.kubernetes.weblogic.domain.v2.ConfigurationConstants.START_IF_NEEDED;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;
import io.kubernetes.client.models.V1LocalObjectReference;
import io.kubernetes.client.models.V1SecretReference;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import oracle.kubernetes.json.Description;
import oracle.kubernetes.operator.KubernetesConstants;
import oracle.kubernetes.operator.VersionConstants;
import oracle.kubernetes.weblogic.domain.EffectiveConfigurationFactory;
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
   * Domain home
   *
   * @since 2.0
   */
  @Description(
      "The folder for the Weblogic Domain. (Not required)"
          + "Defaults to /shared/domains/domains/domainUID if domainHomeInImage is false"
          + "Defaults to /shared/domains/domain if domainHomeInImage is true")
  @SerializedName("domainHome")
  @Expose
  private String domainHome;

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
   * The in-pod name of the directory to store the domain, node manager, server logs, and server
   * .out files in.
   */
  @SerializedName("logHome")
  @Expose
  private String logHome;

  /** Whether to include the server .out file to the pod's stdout. Default is true. */
  @SerializedName("includeServerOutInPodLog")
  @Expose
  private String includeServerOutInPodLog;

  /**
   * The WebLogic Docker image.
   *
   * <p>Defaults to store/oracle/weblogic:12.2.1.3
   */
  @Description(
      "The Weblogic Docker image; required when domainHomeInImage is true; "
          + "otherwise, defaults to store/oracle/weblogic:12.2.1.3")
  @SerializedName("image")
  @Expose
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
          + ""
          + "Legal values are Always, Never and IfNotPresent. "
          + "Defaults to Always if image ends in :latest, IfNotPresent otherwise")
  @SerializedName("imagePullPolicy")
  @Expose
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
  @SerializedName("imagePullSecrets")
  @Expose
  private List<V1LocalObjectReference> imagePullSecrets;

  /**
   * The desired number of running managed servers in each WebLogic cluster that is not explicitly
   * configured in a cluster specification.
   */
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
  @Description(
      "The storage used for this domain. "
          + "Defaults to a predefined claim for a PVC whose name is "
          + "the domain UID followed by '-weblogic-domain-pvc'")
  private DomainStorage storage;

  /**
   * The name of the Kubernetes configmap used in the WebLogic Configuration overrides.
   *
   * @since 2.0
   */
  @Description("The name of the configmap for optional WebLogic configuration overrides.")
  @SerializedName("configOverrides")
  @Expose
  private String configOverrides;

  /**
   * The list of names of the Kubernetes secrets used in the WebLogic Configuration overrides.
   *
   * @since 2.0
   */
  @Description("A list of names of the secrets for optional WebLogic configuration overrides.")
  @SerializedName("configOverrideSecrets")
  @Expose
  private List<String> configOverrideSecrets;

  /**
   * The configuration for the admin server.
   *
   * @since 2.0
   */
  @SerializedName("adminServer")
  @Expose
  @Description("Configuration for the admin server")
  private AdminServer adminServer;

  /**
   * The configured managed servers.
   *
   * @since 2.0
   */
  @SerializedName("managedServers")
  @Expose
  @Description("Configuration for the managed servers")
  private Map<String, ManagedServer> managedServers = new HashMap<>();

  /**
   * The configured clusters.
   *
   * @since 2.0
   */
  @SerializedName("clusters")
  @Expose
  @Description("Configuration for the clusters")
  protected Map<String, Cluster> clusters = new HashMap<>();

  public AdminServer getOrCreateAdminServer(String adminServerName) {
    if (adminServer != null) return adminServer;

    return createAdminServer(adminServerName);
  }

  private AdminServer createAdminServer(String adminServerName) {
    setAsName(adminServerName);
    AdminServer adminServer = new AdminServer();
    setAdminServer(adminServer);
    return adminServer;
  }

  EffectiveConfigurationFactory getEffectiveConfigurationFactory(String resourceVersionLabel) {
    return new V2EffectiveConfigurationFactory();
  }

  private boolean isVersion2Specified(String resourceVersionLabel) {
    return VersionConstants.DOMAIN_V2.equals(resourceVersionLabel);
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

  /**
   * Domain home
   *
   * @since 2.0
   * @return domain home
   */
  public String getDomainHome() {
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
  public String getLogHome() {
    return logHome;
  }

  public void setLogHome(String logHome) {
    this.logHome = logHome;
  }

  public DomainSpec withLogHome(String logHome) {
    this.logHome = logHome;
    return this;
  }

  /**
   * Whether to include server .out to the pod's stdout
   *
   * @return whether server .out should be included in pod's stdout.
   * @since 2.0
   */
  public String getIncludeServerOutInPodLog() {
    return Optional.ofNullable(getConfiguredIncludeServerOutInPodLog())
        .orElse(KubernetesConstants.DEFAULT_INCLUDE_SERVER_OUT_IN_POD_LOG);
  }

  String getConfiguredIncludeServerOutInPodLog() {
    return includeServerOutInPodLog;
  }

  public void setIncludeServerOutInPodLog(String includeServerOutInPodLog) {
    this.includeServerOutInPodLog = includeServerOutInPodLog;
  }

  public DomainSpec withIncludeServerOutInPodLog(String includeServerOutInPodLog) {
    this.includeServerOutInPodLog = includeServerOutInPodLog;
    return this;
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

  /** @param domainHomeInImage */
  public void setDomainHomeInImage(boolean domainHomeInImage) {
    this.domainHomeInImage = domainHomeInImage;
  }

  /**
   * Replicas is the desired number of managed servers running in each WebLogic cluster that is not
   * configured in clusters. Provided so that administrators can scale the Domain resource.
   *
   * @deprecated use {@link Domain#getReplicaCount(String)} to obtain the effective setting.
   * @return replicas
   */
  @SuppressWarnings("DeprecatedIsStillUsed")
  @Deprecated
  public Integer getReplicas() {
    return replicas != null ? replicas : 0;
  }

  /**
   * The desired number of running managed servers in each WebLogic cluster that is not explicitly
   * configured in clusters.
   *
   * @param replicas replicas
   */
  @SuppressWarnings("deprecation")
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

  @Nullable
  public String getConfigOverrides() {
    return configOverrides;
  }

  public void setConfigOverrides(@Nullable String overridess) {
    this.configOverrides = overridess;
  }

  private boolean hasConfigOverrideSecrets() {
    return configOverrideSecrets != null && configOverrideSecrets.size() != 0;
  }

  @Nullable
  public List<String> getConfigOverrideSecrets() {
    if (hasConfigOverrideSecrets()) return configOverrideSecrets;
    else return Collections.emptyList();
  }

  public void setConfigOverrideSecrets(@Nullable List<String> overridesSecretNames) {
    this.configOverrideSecrets = overridesSecretNames;
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
            .append("domainName", domainName)
            .append("adminSecret", adminSecret)
            .append("asName", asName)
            .append("asPort", asPort)
            .append("image", image)
            .append("imagePullPolicy", imagePullPolicy)
            .append("storage", storage)
            .append("imagePullSecrets", imagePullSecrets)
            .append("adminServer", adminServer)
            .append("managedServers", managedServers)
            .append("clusters", clusters)
            .append("replicas", replicas)
            .append("logHome", logHome)
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
            .append(domainName)
            .append(adminSecret)
            .append(asName)
            .append(asPort)
            .append(image)
            .append(imagePullPolicy)
            .append(storage)
            .append(imagePullSecrets)
            .append(adminServer)
            .append(managedServers)
            .append(clusters)
            .append(replicas)
            .append(logHome)
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
            .append(domainName, rhs.domainName)
            .append(adminSecret, rhs.adminSecret)
            .append(asName, rhs.asName)
            .append(asPort, rhs.asPort)
            .append(image, rhs.image)
            .append(storage, rhs.storage)
            .append(imagePullPolicy, rhs.imagePullPolicy)
            .append(imagePullSecrets, rhs.imagePullSecrets)
            .append(adminServer, rhs.adminServer)
            .append(managedServers, rhs.managedServers)
            .append(clusters, rhs.clusters)
            .append(replicas, rhs.replicas)
            .append(logHome, rhs.logHome)
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

  public AdminServer getAdminServer() {
    return Optional.ofNullable(adminServer).orElse(AdminServer.NULL_ADMIN_SERVER);
  }

  public void setAdminServer(AdminServer adminServer) {
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

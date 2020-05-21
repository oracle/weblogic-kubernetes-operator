// Copyright (c) 2017, 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.weblogic.domain.model;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import javax.annotation.Nullable;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;

import com.google.gson.annotations.SerializedName;
import io.kubernetes.client.openapi.models.V1LocalObjectReference;
import io.kubernetes.client.openapi.models.V1SecretReference;
import oracle.kubernetes.json.Description;
import oracle.kubernetes.json.EnumClass;
import oracle.kubernetes.json.Pattern;
import oracle.kubernetes.json.Range;
import oracle.kubernetes.operator.DomainSourceType;
import oracle.kubernetes.operator.ImagePullPolicy;
import oracle.kubernetes.operator.KubernetesConstants;
import oracle.kubernetes.operator.ModelInImageDomainType;
import oracle.kubernetes.operator.ServerStartPolicy;
import oracle.kubernetes.weblogic.domain.EffectiveConfigurationFactory;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;

import static oracle.kubernetes.operator.KubernetesConstants.ALWAYS_IMAGEPULLPOLICY;
import static oracle.kubernetes.operator.KubernetesConstants.DEFAULT_ALLOW_REPLICAS_BELOW_MIN_DYN_CLUSTER_SIZE;
import static oracle.kubernetes.operator.KubernetesConstants.DEFAULT_IMAGE;
import static oracle.kubernetes.operator.KubernetesConstants.IFNOTPRESENT_IMAGEPULLPOLICY;

/** DomainSpec is a description of a domain. */
@Description("DomainSpec is a description of a domain.")
public class DomainSpec extends BaseConfiguration {

  /** Domain unique identifier. Must be unique across the Kubernetes cluster. */
  @Description(
      "Domain unique identifier. Must be unique across the Kubernetes cluster. Not required."
          + " Defaults to the value of metadata.name.")
  @Pattern("^[a-z0-9-.]{1,253}$")
  @SerializedName("domainUID")
  private String domainUid;

  /**
   * Domain home.
   *
   * @since 2.0
   */
  @Description(
      "The folder for the WebLogic Domain. Not required."
          + " Defaults to /shared/domains/domains/<domainUID> if domainHomeSourceType is PersistentVolume."
          + " Defaults to /u01/oracle/user_projects/domains/ if domainHomeSourceType is Image."
          + " Defaults to /u01/domains/<domainUID> if domainHomeSourceType is FromModel.")
  private String domainHome;

  /**
   * Tells the operator whether the customer wants the server to be running. For non-clustered
   * servers - the operator will start it if the policy isn't NEVER. For clustered servers - the
   * operator will start it if the policy is ALWAYS or the policy is IF_NEEDED and the server needs
   * to be started to get to the cluster's replica count.
   *
   * @since 2.0
   */
  @EnumClass(value = ServerStartPolicy.class, qualifier = "forDomain")
  @Description(
      "The strategy for deciding whether to start a server. "
          + "Legal values are ADMIN_ONLY, NEVER, or IF_NEEDED.")
  private String serverStartPolicy;

  /**
   * Reference to secret containing WebLogic startup credentials username and password. Secret must
   * contain keys names 'username' and 'password'. Required.
   */
  @Description(
      "The name of a pre-created Kubernetes secret, in the domain's namespace, that holds"
          + " the username and password needed to boot WebLogic Server under the 'username' and "
          + "'password' fields.")
  @Valid
  @NotNull
  private V1SecretReference webLogicCredentialsSecret;

  /**
   * The in-pod name of the directory to store the domain, Node Manager, server logs, server
   * .out, and HTTP access log files in.
   */
  @Description(
      "The in-pod name of the directory in which to store the domain, Node Manager, server logs, "
          + "server  *.out, and optionally HTTP access log files if `httpAccessLogInLogHome` is true. "
          + "Ignored if logHomeEnabled is false.")
  private String logHome;

  /**
   * Whether the log home is enabled.
   *
   * @since 2.0
   */
  @Description(
      "Specified whether the log home folder is enabled. Not required. "
          + "Defaults to true if domainHomeSourceType is PersistentVolume; false, otherwise.")
  private Boolean logHomeEnabled; // Boolean object, null if unspecified

  /**
   * An optional, in-pod location for data storage of default and custom file stores. If dataHome is
   * not specified or its value is either not set or empty (e.g. dataHome: "") then the data storage
   * directories are determined from the WebLogic domain home configuration.
   */
  @Description(
      "An optional, in-pod location for data storage of default and custom file stores. "
          + "If dataHome is not specified or its value is either not set or empty (e.g. dataHome: \"\") "
          + "then the data storage directories are determined from the WebLogic domain home configuration.")
  private String dataHome;

  /** Whether to include the server .out file to the pod's stdout. Default is true. */
  @Description("If true (the default), then the server .out file will be included in the pod's stdout.")
  private Boolean includeServerOutInPodLog;

  /** Whether to include the server HTTP access log file to the  directory specified in {@link #logHome}
   *  if {@link #logHomeEnabled} is true. Default is true. */
  @Description("If true (the default), then server HTTP access log files will be written to the same "
      + "directory specified in `logHome`. Otherwise, server HTTP access log files will be written to "
      + "the directory configured in the WebLogic domain home configuration.")
  private Boolean httpAccessLogInLogHome;

  /**
   * The WebLogic Docker image.
   *
   * <p>Defaults to container-registry.oracle.com/middleware/weblogic:12.2.1.4
   */
  @Description(
      "The WebLogic Docker image; required when domainHomeSourceType is Image or FromModel; "
          + "otherwise, defaults to container-registry.oracle.com/middleware/weblogic:12.2.1.4.")
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
          + "Defaults to Always if image ends in :latest, IfNotPresent otherwise.")
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
      "The number of managed servers to run in any cluster that does not specify a replica count.")
  @Range(minimum = 0)
  private Integer replicas;

  /**
   * Whether the domain home is part of the image.
   *
   * @since 2.0
   */
  @Deprecated
  @Description(
      "Deprecated. Use domainHomeSourceType instead. Ignored if domainHomeSourceType is specified."
          + " True indicates that the domain home file system is contained in the Docker image"
          + " specified by the image field. False indicates that the domain home file system is located"
          + " on a persistent volume.")
  private Boolean domainHomeInImage;

  @EnumClass(value = DomainSourceType.class)
  @Description(
      "Domain home file system source type: Legal values: Image, PersistentVolume, FromModel."
          + " Image indicates that the domain home file system is contained in the Docker image"
          + " specified by the image field. PersistentVolume indicates that the domain home file system is located"
          + " on a persistent volume.  FromModel indicates that the domain home file system will be created"
          + " and managed by the operator based on a WDT domain model."
          + " If this field is specified it overrides the value of domainHomeInImage. If both fields are"
          + " unspecified then domainHomeSourceType defaults to Image.")
  private String domainHomeSourceType;

  /**
   * Tells the operator to start the introspect domain job.
   *
   * @since 3.0.0
   */
  @Description(
      "If present, every time this value is updated, the operator will start introspect domain job")
  private String introspectVersion;

  @Description("Models and overrides affecting the WebLogic domain configuration.")
  private Configuration configuration;

  /**
   * The name of the Kubernetes config map used for optional WebLogic configuration overrides.
   *
   * @since 2.0
   */
  @Deprecated
  @Description("Deprecated. Use configuration.overridesConfigMap instead."
      + " Ignored if configuration.overridesConfigMap is specified."
      + " The name of the config map for optional WebLogic configuration overrides.")
  private String configOverrides;

  /**
   * A list of names of the Kubernetes secrets used in the WebLogic Configuration overrides.
   *
   * @since 2.0
   */
  @Deprecated
  @Description("Deprecated. Use configuration.secrets instead. Ignored if configuration.secrets is specified."
      + " A list of names of the secrets for optional WebLogic configuration overrides.")
  private List<String> configOverrideSecrets;

  /**
   * The configuration for the admin server.
   *
   * @since 2.0
   */
  @Description("Configuration for the Administration Server.")
  private AdminServer adminServer;

  /**
   * The configured managed servers.
   *
   * @since 2.0
   */
  @Description("Configuration for individual Managed Servers.")
  private final List<ManagedServer> managedServers = new ArrayList<>();

  /**
   * The configured clusters.
   *
   * @since 2.0
   */
  @Description("Configuration for the clusters.")
  protected final List<Cluster> clusters = new ArrayList<>();

  @Description("Experimental feature configurations.")
  private Experimental experimental;

  /**
   * Adds a Cluster to the DomainSpec.
   *
   * @param cluster The cluster to be added to this DomainSpec
   * @return this object
   */
  public DomainSpec withCluster(Cluster cluster) {
    clusters.add(cluster);
    return this;
  }

  AdminServer getOrCreateAdminServer() {
    if (adminServer != null) {
      return adminServer;
    }

    return createAdminServer();
  }

  private AdminServer createAdminServer() {
    AdminServer adminServer = new AdminServer();
    setAdminServer(adminServer);
    return adminServer;
  }

  @SuppressWarnings("unused")
  EffectiveConfigurationFactory getEffectiveConfigurationFactory(
      String apiVersion, String resourceVersionLabel) {
    return new CommonEffectiveConfigurationFactory();
  }

  /**
   * Domain unique identifier. Must be unique across the Kubernetes cluster. Not required. Defaults
   * to the value of metadata.name.
   *
   * @return domain UID
   */
  public String getDomainUid() {
    return domainUid;
  }

  /**
   * Domain unique identifier. Must be unique across the Kubernetes cluster. Not required. Defaults
   * to the value of metadata.name.
   *
   * @param domainUid domain UID
   */
  public void setDomainUid(String domainUid) {
    this.domainUid = domainUid;
  }

  /**
   * Domain unique identifier. Must be unique across the Kubernetes cluster. Required.
   *
   * @param domainUid domain UID
   * @return this
   */
  public DomainSpec withDomainUid(String domainUid) {
    this.domainUid = domainUid;
    return this;
  }

  /**
   * Domain home.
   *
   * @since 2.0
   * @return domain home
   */
  String getDomainHome() {
    return domainHome;
  }

  /**
   * Domain home.
   *
   * @since 2.0
   * @param domainHome domain home
   */
  public void setDomainHome(String domainHome) {
    this.domainHome = domainHome;
  }

  @Nullable
  @Override
  public String getServerStartPolicy() {
    return Optional.ofNullable(serverStartPolicy).orElse(ConfigurationConstants.START_IF_NEEDED);
  }

  @Override
  public void setServerStartPolicy(String serverStartPolicy) {
    this.serverStartPolicy = serverStartPolicy;
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

  // NOTE: we ignore the namespace, which could be confusing. We should change it with the next schema update.
  V1SecretReference getWebLogicCredentialsSecret() {
    return webLogicCredentialsSecret;
  }

  @SuppressWarnings("unused")
  void setWebLogicCredentialsSecret(V1SecretReference webLogicCredentialsSecret) {
    this.webLogicCredentialsSecret = webLogicCredentialsSecret;
  }

  /**
   * Reference to secret containing WebLogic startup credentials username and password. Secret must
   * contain keys names 'username' and 'password'. Required.
   *
   * @param webLogicCredentialsSecret WebLogic startup credentials secret
   * @return this
   */
  public DomainSpec withWebLogicCredentialsSecret(V1SecretReference webLogicCredentialsSecret) {
    this.webLogicCredentialsSecret = webLogicCredentialsSecret;
    return this;
  }

  /**
   * Reference to secret containing WebLogic startup credentials username and password. Secret must
   * contain keys names 'username' and 'password'. Required.
   *
   * @param opssKeyPassPhrase WebLogic startup credentials secret
   * @return this
   */
  public DomainSpec withOpssKeyPassPhrase(V1SecretReference opssKeyPassPhrase) {
    this.webLogicCredentialsSecret = opssKeyPassPhrase;
    return this;
  }

  public String getImage() {
    return Optional.ofNullable(image).orElse(DEFAULT_IMAGE);
  }

  public void setImage(@Nullable String image) {
    this.image = image;
  }

  public String getImagePullPolicy() {
    return Optional.ofNullable(imagePullPolicy).orElse(getInferredPullPolicy());
  }

  private String getInferredPullPolicy() {
    return useLatestImage() ? ALWAYS_IMAGEPULLPOLICY : IFNOTPRESENT_IMAGEPULLPOLICY;
  }

  private boolean useLatestImage() {
    return getImage().endsWith(KubernetesConstants.LATEST_IMAGE_SUFFIX);
  }

  public void setImagePullPolicy(@Nullable String imagePullPolicy) {
    this.imagePullPolicy = imagePullPolicy;
  }

  /**
   * Gets image pull secrets.
   *
   * @return image pull secrets
   */
  @Nullable
  public List<V1LocalObjectReference> getImagePullSecrets() {
    return Optional.ofNullable(imagePullSecrets).orElse(Collections.emptyList());
  }

  public void setImagePullSecrets(@Nullable List<V1LocalObjectReference> imagePullSecrets) {
    this.imagePullSecrets = imagePullSecrets;
  }

  public void setImagePullSecret(@Nullable V1LocalObjectReference imagePullSecret) {
    imagePullSecrets = Collections.singletonList(imagePullSecret);
  }

  /**
   * Log Home.
   *
   * @return The in-pod name of the directory to store the domain, node manager, server logs, and
   *     server .out files in.
   */
  String getLogHome() {
    return logHome;
  }

  public void setLogHome(String logHome) {
    this.logHome = Optional.ofNullable(logHome).map(this::validatePath).orElse(null);
  }

  private String validatePath(String s) {
    if (s.isBlank()) {
      return null;
    }
    if (s.endsWith(File.separator)) {
      return s;
    }
    return s + File.separator;
  }

  /**
   * Log home enabled.
   *
   * @since 2.0
   * @return log home enabled
   */
  Boolean isLogHomeEnabled() {
    return logHomeEnabled;
  }

  /**
   * Log home enabled.
   *
   * @since 2.0
   * @param logHomeEnabled log home enabled
   */
  public void setLogHomeEnabled(Boolean logHomeEnabled) {
    this.logHomeEnabled = logHomeEnabled;
  }

  /**
   * Data Home.
   *
   * <p>An optional, in-pod location for data storage of default and custom file stores. If dataHome
   * is not specified or its value is either not set or empty (e.g. dataHome: "") then the data
   * storage directories are determined from the WebLogic domain home configuration.
   *
   * @return The in-pod location for data storage of default and custom file stores. Null if
   *     dataHome is not specified or its value is either not set or empty.
   */
  String getDataHome() {
    return dataHome;
  }

  public void setDataHome(String dataHome) {
    this.dataHome = dataHome;
  }

  /**
   * Whether to include server .out to the pod's stdout.
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
   * Whether to write server HTTP access log files to the directory specified in
   * {@link #logHome} if {@link #logHomeEnabled} is true.
   *
   * @return true if server HTTP access log files should be included in the directory
   *     specified in {@link #logHome}, false if server HTTP access log files should be written
   *     to the directory as configured in the WebLogic domain home configuration
   */
  boolean getHttpAccessLogInLogHome() {
    return Optional.ofNullable(httpAccessLogInLogHome)
        .orElse(KubernetesConstants.DEFAULT_HTTP_ACCESS_LOG_IN_LOG_HOME);
  }

  public DomainSpec withHttpAccessLogInLogHome(boolean httpAccessLogInLogHome) {
    this.httpAccessLogInLogHome = httpAccessLogInLogHome;
    return this;
  }

  /**
   * Returns true if this domain's home is defined in the default docker image for the domain.
   *
   * @return true or false
   * @since 2.0
   */
  Boolean isDomainHomeInImage() {
    return domainHomeInImage;
  }

  /**
   * Specifies whether the domain home is stored in the image.
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

  public String getDomainHomeSourceType() {
    return domainHomeSourceType;
  }

  public void setDomainHomeSourceType(String domainHomeSourceType) {
    this.domainHomeSourceType = domainHomeSourceType;
  }

  public DomainSpec withDomainHomeSourceType(String domainHomeSourceType) {
    this.domainHomeSourceType = domainHomeSourceType;
    return this;
  }

  public String getIntrospectVersion() {
    return introspectVersion;
  }

  public void setIntrospectVersionn(String introspectVersion) {
    this.introspectVersion = introspectVersion;
  }

  public DomainSpec withIntrospectVersion(String introspectVersion) {
    this.introspectVersion = introspectVersion;
    return this;
  }

  public Configuration getConfiguration() {
    return configuration;
  }

  public void setConfiguration(Configuration configuration) {
    this.configuration = configuration;
  }

  public DomainSpec withConfiguration(Configuration configuration) {
    this.configuration = configuration;
    return this;
  }

  /**
   * The desired number of running managed servers in each WebLogic cluster that is not explicitly
   * configured in clusters.
   *
   * @return replicas
   */
  public Integer getReplicas() {
    return this.replicas;
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

  @Nullable
  String getConfigOverrides() {
    return configOverrides;
  }

  void setConfigOverrides(@Nullable String overrides) {
    this.configOverrides = overrides;
  }

  public DomainSpec withConfigOverrides(@Nullable String overrides) {
    this.configOverrides = overrides;
    return this;
  }

  @Nullable
  List<String> getConfigOverrideSecrets() {
    return Optional.ofNullable(configOverrideSecrets).orElse(Collections.emptyList());
  }

  public void setConfigOverrideSecrets(@Nullable List<String> overridesSecretNames) {
    this.configOverrideSecrets = overridesSecretNames;
  }

  /**
   * Test if the domain is deployed under Istio environment.
   *
   * @return istioEnabled
   */
  boolean isIstioEnabled() {
    return Optional.ofNullable(experimental)
        .map(Experimental::getIstio)
        .map(Istio::getEnabled)
        .orElse(false);
  }

  /**
   * The WebLogic readiness port used under Istio environment.
   *
   * @return readinessPort
   */
  int getIstioReadinessPort() {
    return Optional.ofNullable(experimental)
        .map(Experimental::getIstio)
        .map(Istio::getReadinessPort)
        .orElse(8888);
  }

  String getWdtDomainType() {
    return Optional.ofNullable(configuration)
        .map(Configuration::getModel)
        .map(Model::getDomainType)
        .orElse(ModelInImageDomainType.WLS.toString());
  }

  String getOpssWalletPasswordSecret() {
    return Optional.ofNullable(configuration)
        .map(Configuration::getOpss)
        .map(Opss::getWalletPasswordSecret)
        .orElse(null);
  }

  /**
   * Get OPSS wallet file secret.
   * @return wallet file secret
   */
  public String getOpssWalletFileSecret() {
    return Optional.ofNullable(configuration)
        .map(Configuration::getOpss)
        .map(Opss::getWalletFileSecret)
        .orElse(null);
  }

  String getRuntimeEncryptionSecret() {
    return Optional.ofNullable(configuration)
        .map(Configuration::getModel)
        .map(Model::getRuntimeEncryptionSecret)
        .orElse(null);
  }

  /**
   * Get WDT config map.
   * @return config map name
   */
  public String getWdtConfigMap() {
    return Optional.ofNullable(configuration)
        .map(Configuration::getModel)
        .map(Model::getConfigMap)
        .orElse(null);
  }

  @Override
  public String toString() {
    ToStringBuilder builder =
        new ToStringBuilder(this)
            .appendSuper(super.toString())
            .append("domainUID", domainUid)
            .append("domainHome", domainHome)
            .append("domainHomeInImage", domainHomeInImage)
            .append("domainHomeSourceType", domainHomeSourceType)
            .append("introspectVersion", introspectVersion)
            .append("configuration", configuration)
            .append("serverStartPolicy", serverStartPolicy)
            .append("webLogicCredentialsSecret", webLogicCredentialsSecret)
            .append("image", image)
            .append("imagePullPolicy", imagePullPolicy)
            .append("imagePullSecrets", imagePullSecrets)
            .append("adminServer", adminServer)
            .append("managedServers", managedServers)
            .append("clusters", clusters)
            .append("replicas", replicas)
            .append("logHome", logHome)
            .append("logHomeEnabled", logHomeEnabled)
            .append("includeServerOutInPodLog", includeServerOutInPodLog)
            .append("configOverrides", configOverrides)
            .append("configOverrideSecrets", configOverrideSecrets)
            .append("experimental", experimental);

    return builder.toString();
  }

  @Override
  public int hashCode() {
    HashCodeBuilder builder =
        new HashCodeBuilder()
            .appendSuper(super.hashCode())
            .append(domainUid)
            .append(domainHome)
            .append(domainHomeInImage)
            .append(domainHomeSourceType)
            .append(introspectVersion)
            .append(configuration)
            .append(serverStartPolicy)
            .append(webLogicCredentialsSecret)
            .append(image)
            .append(imagePullPolicy)
            .append(imagePullSecrets)
            .append(adminServer)
            .append(managedServers)
            .append(clusters)
            .append(replicas)
            .append(logHome)
            .append(logHomeEnabled)
            .append(includeServerOutInPodLog)
            .append(configOverrides)
            .append(configOverrideSecrets)
            .append(experimental);

    return builder.toHashCode();
  }

  @Override
  public boolean equals(Object other) {
    if (other == this) {
      return true;
    }
    if (!(other instanceof DomainSpec)) {
      return false;
    }

    DomainSpec rhs = ((DomainSpec) other);
    EqualsBuilder builder =
        new EqualsBuilder()
            .appendSuper(super.equals(other))
            .append(domainUid, rhs.domainUid)
            .append(domainHome, rhs.domainHome)
            .append(domainHomeInImage, rhs.domainHomeInImage)
            .append(domainHomeSourceType, rhs.domainHomeSourceType)
            .append(introspectVersion, rhs.introspectVersion)
            .append(configuration, rhs.configuration)
            .append(serverStartPolicy, rhs.serverStartPolicy)
            .append(webLogicCredentialsSecret, rhs.webLogicCredentialsSecret)
            .append(getImage(), rhs.getImage())
            .append(getImagePullPolicy(), rhs.getImagePullPolicy())
            .append(imagePullSecrets, rhs.imagePullSecrets)
            .append(adminServer, rhs.adminServer)
            .append(managedServers, rhs.managedServers)
            .append(clusters, rhs.clusters)
            .append(replicas, rhs.replicas)
            .append(logHome, rhs.logHome)
            .append(logHomeEnabled, rhs.logHomeEnabled)
            .append(includeServerOutInPodLog, rhs.includeServerOutInPodLog)
            .append(configOverrides, rhs.configOverrides)
            .append(configOverrideSecrets, rhs.configOverrideSecrets)
            .append(experimental, rhs.experimental);
    return builder.isEquals();
  }

  ManagedServer getManagedServer(String serverName) {
    if (serverName != null) {
      for (ManagedServer s : managedServers) {
        if (serverName.equals(s.getServerName())) {
          return s;
        }
      }
    }
    return null;
  }

  Cluster getCluster(String clusterName) {
    if (clusterName != null) {
      for (Cluster c : clusters) {
        if (clusterName.equals(c.getClusterName())) {
          return c;
        }
      }
    }
    return null;
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

  private boolean isAllowReplicasBelowDynClusterSizeFor(Cluster cluster) {
    return cluster == null ? DEFAULT_ALLOW_REPLICAS_BELOW_MIN_DYN_CLUSTER_SIZE :
        cluster.isAllowReplicasBelowMinDynClusterSize();
  }

  public AdminServer getAdminServer() {
    return adminServer;
  }

  private void setAdminServer(AdminServer adminServer) {
    this.adminServer = adminServer;
  }

  public List<ManagedServer> getManagedServers() {
    return managedServers;
  }

  public List<Cluster> getClusters() {
    return clusters;
  }

  class CommonEffectiveConfigurationFactory implements EffectiveConfigurationFactory {
    @Override
    public AdminServerSpec getAdminServerSpec() {
      return new AdminServerSpecCommonImpl(DomainSpec.this, adminServer);
    }

    @Override
    public ServerSpec getServerSpec(String serverName, String clusterName) {
      return new ManagedServerSpecCommonImpl(
          DomainSpec.this,
          getManagedServer(serverName),
          getCluster(clusterName),
          getClusterLimit(clusterName));
    }

    @Override
    public ClusterSpec getClusterSpec(String clusterName) {
      return new ClusterSpecCommonImpl(DomainSpec.this, getCluster(clusterName));
    }

    private Integer getClusterLimit(String clusterName) {
      return clusterName == null ? null : getReplicaCount(clusterName);
    }

    @Override
    public boolean isShuttingDown() {
      return getAdminServerSpec().isShuttingDown();
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
    public List<String> getAdminServerChannelNames() {
      return adminServer != null ? adminServer.getChannelNames() : Collections.emptyList();
    }

    @Override
    public boolean isAllowReplicasBelowMinDynClusterSize(String clusterName) {
      return isAllowReplicasBelowDynClusterSizeFor(getCluster(clusterName));
    }

    private Cluster getOrCreateCluster(String clusterName) {
      Cluster cluster = getCluster(clusterName);
      if (cluster != null) {
        return cluster;
      }

      return createClusterWithName(clusterName);
    }

    private Cluster createClusterWithName(String clusterName) {
      Cluster cluster = new Cluster().withClusterName(clusterName);
      clusters.add(cluster);
      return cluster;
    }
  }
}

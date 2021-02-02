// Copyright (c) 2020, 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.domain;

import java.util.ArrayList;
import java.util.List;

import com.google.gson.annotations.SerializedName;
import io.kubernetes.client.openapi.models.V1LocalObjectReference;
import io.kubernetes.client.openapi.models.V1SecretReference;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;

@ApiModel(description = "DomainSpec is a description of a domain.")
public class DomainSpec {

  @ApiModelProperty(
      value =
          "Domain unique identifier. Must be unique across the Kubernetes cluster. Not required."
              + " Defaults to the value of metadata.name.",
      allowableValues = "pattern[^[a-z0-9-.]{1,253}$]")
  @SerializedName("domainUID")
  private String domainUid;

  @ApiModelProperty(
      "The folder for the WebLogic Domain. Not required."
          + " Defaults to /shared/domains/domains/<domainUID> if domainHomeSourceType is PersistentVolume."
          + " Defaults to /u01/oracle/user_projects/domains/ if domainHomeSourceType is Image."
          + " Defaults to /u01/domains/<domainUID> if domainHomeSourceType is FromModel.")
  private String domainHome;

  @ApiModelProperty(
      "The strategy for deciding whether to start a server. "
          + "Legal values are ADMIN_ONLY, NEVER, or IF_NEEDED.")
  private String serverStartPolicy;

  @ApiModelProperty(
      "The name of a pre-created Kubernetes secret, in the domain's namespace, that holds"
          + " the username and password needed to boot WebLogic Server under the 'username' and "
          + "'password' fields.")
  private V1SecretReference webLogicCredentialsSecret;

  @ApiModelProperty(
      "The in-pod name of the directory in which to store the domain, node manager, server logs, "
          + "and server  *.out files. Defaults to /shared/logs/<domainUID>. Ignored if logHomeEnabled is false.")
  private String logHome;

  @ApiModelProperty(
      "Specified whether the log home folder is enabled. Not required. "
          + "Defaults to true if domainHomeSourceType is PersistentVolume; false, otherwise.")
  private Boolean logHomeEnabled;

  @ApiModelProperty("Whether to allow the number of running cluster member Managed Server instances to drop "
      + "below the minimum dynamic cluster size configured in the WebLogic domain configuration, "
      + "if this is not specified for a specific cluster under the `clusters` field. Defaults to true."
  )
  private Boolean allowReplicasBelowMinDynClusterSize;

  @ApiModelProperty(
      "An optional, in-pod location for data storage of default and custom file stores. "
          + "If dataHome is not specified or its value is either not set or empty (e.g. dataHome: \"\") "
          + "then the data storage directories are determined from the WebLogic domain home configuration.")
  private String dataHome;

  @ApiModelProperty(
      "If true (the default), the server .out file will be included in the pod's stdout.")
  private Boolean includeServerOutInPodLog;

  @ApiModelProperty(
      "The WebLogic Server image; required when domainHomeSourceType is Image or FromModel; "
          + "otherwise, defaults to container-registry.oracle.com/middleware/weblogic:12.2.1.4.")
  private String image;

  @ApiModelProperty(
      "The image pull policy for the WebLogic Server image. "
          + "Legal values are Always, Never and IfNotPresent. "
          + "Defaults to Always if image ends in :latest, IfNotPresent otherwise.")
  private String imagePullPolicy;

  @ApiModelProperty("A list of image pull secrets for the WebLogic Server image.")
  private List<V1LocalObjectReference> imagePullSecrets = new ArrayList<>();

  @ApiModelProperty(
      value =
          "The number of managed servers to run in any cluster that does not specify a replica count.",
      allowableValues = "range[0,infinity]")
  private Integer replicas;

  @Deprecated
  @ApiModelProperty(
      "Deprecated. Use domainHomeSourceType instead. Ignored if domainHomeSourceType is specified."
          + " True indicates that the domain home file system is contained in the image"
          + " specified by the image field. False indicates that the domain home file system is located"
          + " on a persistent volume.")
  private Boolean domainHomeInImage;

  @ApiModelProperty(
      "Domain home file system source type: Legal values: Image, PersistentVolume, FromModel."
          + " Image indicates that the domain home file system is contained in the image"
          + " specified by the image field. PersistentVolume indicates that the domain home file system is located"
          + " on a persistent volume.  FromModel indicates that the domain home file system will be created"
          + " and managed by the operator based on a WDT domain model."
          + " If this field is specified it overrides the value of domainHomeInImage. If both fields are"
          + " unspecified then domainHomeSourceType defaults to Image.")
  private String domainHomeSourceType;

  @ApiModelProperty(
      "If present, every time this value is updated, the operator will start introspect domain job")
  private String introspectVersion;

  @ApiModelProperty("Models and overrides affecting the WebLogic domain configuration.")
  private Configuration configuration;

  @Deprecated
  @ApiModelProperty(
      "Deprecated. Use configuration.overridesConfigMap instead."
          + " Ignored if configuration.overridesConfigMap is specified."
          + " The name of the config map for optional WebLogic configuration overrides.")
  private String configOverrides;

  @Deprecated
  @ApiModelProperty(
      "Deprecated. Use configuration.secrets instead. Ignored if configuration.secrets is specified."
          + " A list of names of the secrets for optional WebLogic configuration overrides.")
  private List<String> configOverrideSecrets = new ArrayList<>();

  @ApiModelProperty("Configuration for the Administration Server.")
  private AdminServer adminServer;

  @ApiModelProperty("Configuration for individual Managed Servers.")
  private List<ManagedServer> managedServers = new ArrayList<>();

  @ApiModelProperty("Configuration for the clusters.")
  private List<Cluster> clusters = new ArrayList<>();

  @ApiModelProperty("Experimental feature configurations.")
  private Experimental experimental;

  @ApiModelProperty("Configuration affecting server pods.")
  private ServerPod serverPod;

  @ApiModelProperty(
      "Customization affecting ClusterIP Kubernetes services for WebLogic Server instances.")
  private ServerService serverService;

  @ApiModelProperty(
      "The state in which the server is to be started. Use ADMIN if server should start "
          + "in the admin state. Defaults to RUNNING.")
  private String serverStartState;

  @ApiModelProperty(
      "If present, every time this value is updated the operator will restart"
          + " the required servers.")
  private String restartVersion;

  public DomainSpec domainUid(String domainUid) {
    this.domainUid = domainUid;
    return this;
  }

  public String domainUid() {
    return domainUid;
  }

  public String getDomainUid() {
    return domainUid;
  }

  public void setDomainUid(String domainUid) {
    this.domainUid = domainUid;
  }

  public DomainSpec domainHome(String domainHome) {
    this.domainHome = domainHome;
    return this;
  }

  public String domainHome() {
    return domainHome;
  }

  public String getDomainHome() {
    return domainHome;
  }

  public void setDomainHome(String domainHome) {
    this.domainHome = domainHome;
  }

  public DomainSpec serverStartPolicy(String serverStartPolicy) {
    this.serverStartPolicy = serverStartPolicy;
    return this;
  }

  public String serverStartPolicy() {
    return serverStartPolicy;
  }

  public String getServerStartPolicy() {
    return serverStartPolicy;
  }

  public void setServerStartPolicy(String serverStartPolicy) {
    this.serverStartPolicy = serverStartPolicy;
  }

  public DomainSpec webLogicCredentialsSecret(V1SecretReference webLogicCredentialsSecret) {
    this.webLogicCredentialsSecret = webLogicCredentialsSecret;
    return this;
  }

  public V1SecretReference webLogicCredentialsSecret() {
    return webLogicCredentialsSecret;
  }

  public V1SecretReference getWebLogicCredentialsSecret() {
    return webLogicCredentialsSecret;
  }

  public void setWebLogicCredentialsSecret(V1SecretReference webLogicCredentialsSecret) {
    this.webLogicCredentialsSecret = webLogicCredentialsSecret;
  }

  public DomainSpec logHome(String logHome) {
    this.logHome = logHome;
    return this;
  }

  public String logHome() {
    return logHome;
  }

  public String getLogHome() {
    return logHome;
  }

  public void setLogHome(String logHome) {
    this.logHome = logHome;
  }

  public DomainSpec logHomeEnabled(Boolean logHomeEnabled) {
    this.logHomeEnabled = logHomeEnabled;
    return this;
  }

  public Boolean logHomeEnabled() {
    return logHomeEnabled;
  }

  public Boolean getLogHomeEnabled() {
    return logHomeEnabled;
  }

  public void setLogHomeEnabled(Boolean logHomeEnabled) {
    this.logHomeEnabled = logHomeEnabled;
  }

  public DomainSpec allowReplicasBelowMinDynClusterSize(Boolean allowReplicasBelowMinDynClusterSize) {
    this.allowReplicasBelowMinDynClusterSize = allowReplicasBelowMinDynClusterSize;
    return this;
  }

  public Boolean allowReplicasBelowMinDynClusterSize() {
    return allowReplicasBelowMinDynClusterSize;
  }

  public Boolean getAllowReplicasBelowMinDynClusterSize() {
    return allowReplicasBelowMinDynClusterSize;
  }

  public void setAllowReplicasBelowMinDynClusterSize(Boolean allowReplicasBelowMinDynClusterSize) {
    this.allowReplicasBelowMinDynClusterSize = allowReplicasBelowMinDynClusterSize;
  }

  public DomainSpec dataHome(String dataHome) {
    this.dataHome = dataHome;
    return this;
  }

  public String dataHome() {
    return dataHome;
  }

  public String getDataHome() {
    return dataHome;
  }

  public void setDataHome(String dataHome) {
    this.dataHome = dataHome;
  }

  public DomainSpec includeServerOutInPodLog(Boolean includeServerOutInPodLog) {
    this.includeServerOutInPodLog = includeServerOutInPodLog;
    return this;
  }

  public Boolean includeServerOutInPodLog() {
    return includeServerOutInPodLog;
  }

  public Boolean getIncludeServerOutInPodLog() {
    return includeServerOutInPodLog;
  }

  public void setIncludeServerOutInPodLog(Boolean includeServerOutInPodLog) {
    this.includeServerOutInPodLog = includeServerOutInPodLog;
  }

  public DomainSpec image(String image) {
    this.image = image;
    return this;
  }

  public String image() {
    return image;
  }

  public String getImage() {
    return image;
  }

  public void setImage(String image) {
    this.image = image;
  }

  public DomainSpec imagePullPolicy(String imagePullPolicy) {
    this.imagePullPolicy = imagePullPolicy;
    return this;
  }

  public String imagePullPolicy() {
    return imagePullPolicy;
  }

  public String getImagePullPolicy() {
    return imagePullPolicy;
  }

  public void setImagePullPolicy(String imagePullPolicy) {
    this.imagePullPolicy = imagePullPolicy;
  }

  public DomainSpec imagePullSecrets(List<V1LocalObjectReference> imagePullSecrets) {
    this.imagePullSecrets = imagePullSecrets;
    return this;
  }

  public List<V1LocalObjectReference> imagePullSecrets() {
    return imagePullSecrets;
  }

  /**
   * Add image pull secrets item.
   * @param imagePullSecretsItem Image pull secret
   * @return this
   */
  public DomainSpec addImagePullSecretsItem(V1LocalObjectReference imagePullSecretsItem) {
    if (imagePullSecrets == null) {
      imagePullSecrets = new ArrayList<>();
    }
    imagePullSecrets.add(imagePullSecretsItem);
    return this;
  }

  public List<V1LocalObjectReference> getImagePullSecrets() {
    return imagePullSecrets;
  }

  public void setImagePullSecrets(List<V1LocalObjectReference> imagePullSecrets) {
    this.imagePullSecrets = imagePullSecrets;
  }

  public DomainSpec replicas(Integer replicas) {
    this.replicas = replicas;
    return this;
  }

  public Integer replicas() {
    return this.replicas;
  }

  public Integer getReplicas() {
    return replicas;
  }

  public void setReplicas(Integer replicas) {
    this.replicas = replicas;
  }

  public DomainSpec domainHomeInImage(Boolean domainHomeInImage) {
    this.domainHomeInImage = domainHomeInImage;
    return this;
  }

  public Boolean domainHomeInImage() {
    return domainHomeInImage;
  }

  public Boolean getDomainHomeInImage() {
    return domainHomeInImage;
  }

  public void setDomainHomeInImage(Boolean domainHomeInImage) {
    this.domainHomeInImage = domainHomeInImage;
  }

  public DomainSpec domainHomeSourceType(String domainHomeSourceType) {
    this.domainHomeSourceType = domainHomeSourceType;
    return this;
  }

  public String domainHomeSourceType() {
    return domainHomeSourceType;
  }

  public String getDomainHomeSourceType() {
    return domainHomeSourceType;
  }

  public void setDomainHomeSourceType(String domainHomeSourceType) {
    this.domainHomeSourceType = domainHomeSourceType;
  }

  public DomainSpec introspectVersion(String introspectVersion) {
    this.introspectVersion = introspectVersion;
    return this;
  }

  public String introspectVersion() {
    return introspectVersion;
  }

  public String getIntrospectVersion() {
    return introspectVersion;
  }

  public void setIntrospectVersion(String introspectVersion) {
    this.introspectVersion = introspectVersion;
  }

  public DomainSpec configuration(Configuration configuration) {
    this.configuration = configuration;
    return this;
  }

  public Configuration configuration() {
    return configuration;
  }

  public Configuration getConfiguration() {
    return configuration;
  }

  public void setConfiguration(Configuration configuration) {
    this.configuration = configuration;
  }

  public DomainSpec configOverrides(String configOverrides) {
    this.configOverrides = configOverrides;
    return this;
  }

  public String configOverrides() {
    return configOverrides;
  }

  public String getConfigOverrides() {
    return configOverrides;
  }

  public void setConfigOverrides(String configOverrides) {
    this.configOverrides = configOverrides;
  }

  public DomainSpec configOverrideSecrets(List<String> configOverrideSecrets) {
    this.configOverrideSecrets = configOverrideSecrets;
    return this;
  }

  public List<String> configOverrideSecrets() {
    return configOverrideSecrets;
  }

  /**
   * Adds config override secrets.
   * @param configOverrideSecretsItem Config override secret
   * @return this
   */
  public DomainSpec addConfigOverrideSecretsItem(String configOverrideSecretsItem) {
    if (configOverrideSecrets == null) {
      configOverrideSecrets = new ArrayList<>();
    }
    configOverrideSecrets.add(configOverrideSecretsItem);
    return this;
  }

  public List<String> getConfigOverrideSecrets() {
    return configOverrideSecrets;
  }

  public void setConfigOverrideSecrets(List<String> configOverrideSecrets) {
    this.configOverrideSecrets = configOverrideSecrets;
  }

  public DomainSpec adminServer(AdminServer adminServer) {
    this.adminServer = adminServer;
    return this;
  }

  public AdminServer adminServer() {
    return adminServer;
  }

  public AdminServer getAdminServer() {
    return adminServer;
  }

  public void setAdminServer(AdminServer adminServer) {
    this.adminServer = adminServer;
  }

  public DomainSpec managedServers(List<ManagedServer> managedServers) {
    this.managedServers = managedServers;
    return this;
  }

  public List<ManagedServer> managedServers() {
    return managedServers;
  }

  /**
   * Adds managed server item.
   * @param managedServersItem Managed server
   * @return this
   */
  public DomainSpec addManagedServersItem(ManagedServer managedServersItem) {
    if (managedServers == null) {
      managedServers = new ArrayList<>();
    }
    managedServers.add(managedServersItem);
    return this;
  }

  public List<ManagedServer> getManagedServers() {
    return managedServers;
  }

  public void setManagedServers(List<ManagedServer> managedServers) {
    this.managedServers = managedServers;
  }

  public DomainSpec clusters(List<Cluster> clusters) {
    this.clusters = clusters;
    return this;
  }

  public List<Cluster> clusters() {
    return clusters;
  }

  /**
   * Adds cluster item.
   * @param clustersItem Cluster
   * @return this
   */
  public DomainSpec addClustersItem(Cluster clustersItem) {
    if (clusters == null) {
      clusters = new ArrayList<>();
    }
    clusters.add(clustersItem);
    return this;
  }

  public List<Cluster> getClusters() {
    return clusters;
  }

  public void setClusters(List<Cluster> clusters) {
    this.clusters = clusters;
  }

  public DomainSpec experimental(Experimental experimental) {
    this.experimental = experimental;
    return this;
  }

  public Experimental experimental() {
    return experimental;
  }

  public Experimental getExperimental() {
    return experimental;
  }

  public void setExperimental(Experimental experimental) {
    this.experimental = experimental;
  }

  public DomainSpec serverPod(ServerPod serverPod) {
    this.serverPod = serverPod;
    return this;
  }

  public ServerPod serverPod() {
    return serverPod;
  }

  public ServerPod getServerPod() {
    return serverPod;
  }

  public void setServerPod(ServerPod serverPod) {
    this.serverPod = serverPod;
  }

  public DomainSpec serverService(ServerService serverService) {
    this.serverService = serverService;
    return this;
  }

  public ServerService serverService() {
    return serverService;
  }

  public ServerService getServerService() {
    return serverService;
  }

  public void setServerService(ServerService serverService) {
    this.serverService = serverService;
  }

  public DomainSpec serverStartState(String serverStartState) {
    this.serverStartState = serverStartState;
    return this;
  }

  public String serverStartState() {
    return serverStartState;
  }

  public String getServerStartState() {
    return serverStartState;
  }

  public void setServerStartState(String serverStartState) {
    this.serverStartState = serverStartState;
  }

  public DomainSpec restartVersion(String restartVersion) {
    this.restartVersion = restartVersion;
    return this;
  }

  public String restartVersion() {
    return restartVersion;
  }

  public String getRestartVersion() {
    return restartVersion;
  }

  public void setRestartVersion(String restartVersion) {
    this.restartVersion = restartVersion;
  }

  @Override
  public String toString() {
    ToStringBuilder builder =
        new ToStringBuilder(this)
            .append("domainUID", domainUid)
            .append("domainHome", domainHome)
            .append("serverStartPolicy", serverStartPolicy)
            .append("webLogicCredentialsSecret", webLogicCredentialsSecret)
            .append("logHome", logHome)
            .append("logHomeEnabled", logHomeEnabled)
            .append("allowReplicasBelowMinDynClusterSize", allowReplicasBelowMinDynClusterSize)
            .append("dataHome", dataHome)
            .append("includeServerOutInPodLog", includeServerOutInPodLog)
            .append("image", image)
            .append("imagePullPolicy", imagePullPolicy)
            .append("imagePullSecrets", imagePullSecrets)
            .append("replicas", replicas)
            .append("domainHomeInImage", domainHomeInImage)
            .append("domainHomeSourceType", domainHomeSourceType)
            .append("introspectVersion", introspectVersion)
            .append("configuration", configuration)
            .append("configOverrides", configOverrides)
            .append("configOverrideSecrets", configOverrideSecrets)
            .append("adminServer", adminServer)
            .append("managedServers", managedServers)
            .append("clusters", clusters)
            .append("experimental", experimental)
            .append("serverStartState", serverStartState)
            .append("serverPod", serverPod)
            .append("serverService", serverService)
            .append("restartVersion", restartVersion);

    return builder.toString();
  }

  @Override
  public int hashCode() {
    HashCodeBuilder builder =
        new HashCodeBuilder()
            .append(domainUid)
            .append(domainHome)
            .append(serverStartPolicy)
            .append(webLogicCredentialsSecret)
            .append(logHome)
            .append(logHomeEnabled)
            .append(allowReplicasBelowMinDynClusterSize)
            .append(dataHome)
            .append(includeServerOutInPodLog)
            .append(image)
            .append(imagePullPolicy)
            .append(imagePullSecrets)
            .append(replicas)
            .append(domainHomeInImage)
            .append(domainHomeSourceType)
            .append(introspectVersion)
            .append(configuration)
            .append(configOverrides)
            .append(configOverrideSecrets)
            .append(adminServer)
            .append(managedServers)
            .append(clusters)
            .append(experimental)
            .append(serverPod)
            .append(serverService)
            .append(serverStartState)
            .append(restartVersion);

    return builder.toHashCode();
  }

  @Override
  public boolean equals(Object other) {
    if (this == other) {
      return true;
    }

    if (other == null || getClass() != other.getClass()) {
      return false;
    }
    DomainSpec rhs = (DomainSpec) other;
    EqualsBuilder builder =
        new EqualsBuilder()
            .append(domainUid, rhs.domainUid)
            .append(domainHome, rhs.domainHome)
            .append(serverStartPolicy, rhs.serverStartPolicy)
            .append(webLogicCredentialsSecret, rhs.webLogicCredentialsSecret)
            .append(logHome, rhs.logHome)
            .append(logHomeEnabled, rhs.logHomeEnabled)
            .append(allowReplicasBelowMinDynClusterSize, rhs.allowReplicasBelowMinDynClusterSize)
            .append(dataHome, rhs.dataHome)
            .append(includeServerOutInPodLog, rhs.includeServerOutInPodLog)
            .append(image, rhs.image)
            .append(imagePullPolicy, rhs.imagePullPolicy)
            .append(imagePullSecrets, rhs.imagePullSecrets)
            .append(replicas, rhs.replicas)
            .append(domainHomeInImage, rhs.domainHomeInImage)
            .append(domainHomeSourceType, rhs.domainHomeSourceType)
            .append(introspectVersion, rhs.introspectVersion)
            .append(configuration, rhs.configuration)
            .append(configOverrides, rhs.configOverrides)
            .append(configOverrideSecrets, rhs.configOverrideSecrets)
            .append(adminServer, rhs.adminServer)
            .append(managedServers, rhs.managedServers)
            .append(clusters, rhs.clusters)
            .append(experimental, rhs.experimental)
            .append(serverPod, rhs.serverPod)
            .append(serverService, rhs.serverService)
            .append(serverStartState, rhs.serverStartState)
            .append(restartVersion, rhs.restartVersion);
    return builder.isEquals();
  }
}

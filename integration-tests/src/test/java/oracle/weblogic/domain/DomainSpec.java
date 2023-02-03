// Copyright (c) 2020, 2023, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.domain;

import java.util.ArrayList;
import java.util.List;

import com.google.gson.annotations.SerializedName;
import io.kubernetes.client.openapi.models.V1LocalObjectReference;
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
          + "Legal values are AdminOnly, Never, or IfNeeded.")
  private String serverStartPolicy;

  @ApiModelProperty(
      "The name of a pre-created Kubernetes secret, in the domain's namespace, that holds"
          + " the username and password needed to boot WebLogic Server under the 'username' and "
          + "'password' fields.")
  private V1LocalObjectReference webLogicCredentialsSecret;

  @ApiModelProperty(
      "The in-pod name of the directory in which to store the domain, node manager, server logs, "
          + "and server  *.out files. Defaults to /shared/logs/<domainUID>. Ignored if logHomeEnabled is false.")
  private String logHome;

  @ApiModelProperty(
      "Control how the log files under logHome is organized. "
          + "Flat - all files are under the logHome root directory. "
          + "ByServers (default) - domain log file and introspector.out are at the logHome root level, all other files"
          + "are organized under the respective server name logs directory.  logHome/servers/<server name>/logs.")
  private String logHomeLayout;

  @ApiModelProperty(
      "Specified whether the log home folder is enabled. Not required. "
          + "Defaults to true if domainHomeSourceType is PersistentVolume; false, otherwise.")
  private Boolean logHomeEnabled;

  @ApiModelProperty(
      value = "The wait time in seconds before the start of the next retry after a Severe failure. Defaults to 120.",
      allowableValues = "range[0,infinity]")
  private Long failureRetryIntervalSeconds;

  @ApiModelProperty(
      value = "The time in minutes before the operator will stop retrying Severe failures. Defaults to 1440.",
      allowableValues = "range[0,infinity]")
  private Long failureRetryLimitMinutes;

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

  @ApiModelProperty(
      "Domain home file system source type: Legal values: Image, PersistentVolume, FromModel."
          + " Image indicates that the domain home file system is contained in the image"
          + " specified by the image field. PersistentVolume indicates that the domain home file system is located"
          + " on a persistent volume.  FromModel indicates that the domain home file system will be created"
          + " and managed by the operator based on a WDT domain model."
          + " Defaults to Image.")
  private String domainHomeSourceType;

  @ApiModelProperty(
      "If present, every time this value is updated, the operator will start introspect domain job")
  private String introspectVersion;

  @ApiModelProperty("Models and overrides affecting the WebLogic domain configuration.")
  private Configuration configuration;

  @ApiModelProperty("The maximum number of cluster members that can be temporarily unavailable."
      + " You can override this default on a per cluster basis by setting the"
      + " cluster's `maxUnavailable` field. Defaults to 1.")
  private Integer maxClusterUnavailable;

  @ApiModelProperty("The maximum number of cluster member Managed Server instances that the"
      + " operator will start in parallel for a given cluster, if `maxConcurrentStartup` "
      + " is not specified for a specific cluster under the `clusters` field. "
      + " A value of 0 means there is no configured limit. Defaults to 0.")
  private Integer maxClusterConcurrentStartup;

  @ApiModelProperty("The default maximum number of WebLogic Server instances that a cluster will"
      + " shut down in parallel when it is being partially shut down by lowering its replica count."
      + " You can override this default on a per cluster basis by setting the cluster's `maxConcurrentShutdown` field."
      + " A value of 0 means there is no limit. Defaults to 1.")
  private Integer maxClusterConcurrentShutdown;

  /**
   * The Fluentd configuration.
   *
   */
  @ApiModelProperty("Automatic fluentd sidecar injection. If "
      + "specified, the operator "
      + "will deploy a sidecar container alongside each WebLogic Server instance that runs the fluentd, "
      + "Optionally, the introspector job pod can be enabled to deploy with the fluentd sidecar container. "
      + "WebLogic Server instances that are already running when the `fluentdSpecification` field is created "
      + "or deleted, will not be affected until they are restarted. When any given server "
      + "is restarted for another reason, such as a change to the `restartVersion`, then the newly created pod "
      + " will have the fluentd sidecar or not, as appropriate")
  private FluentdSpecification fluentdSpecification;

  public FluentdSpecification getFluentdSpecification() {
    return fluentdSpecification;
  }

  /**
   * Add fluentd specification to the domain spec.
   * @param fluentdSpecification fluentd specification.
   * @return domain spec.
   */

  public DomainSpec withFluentdConfiguration(FluentdSpecification fluentdSpecification) {
    this.fluentdSpecification = fluentdSpecification;
    return this;
  }

  /**
   * Add fluentd specification to the domain spec.
   * @param watchIntrospectorLog watch the introspector job pod log also.
   * @param credentialName k8s secrets containing elastic search credentials.
   * @param fluentdConfig optional fluentd configuration.
   * @return domain spec.
   */

  public DomainSpec withFluentdConfiguration(boolean watchIntrospectorLog, String credentialName,
                                String image, String imagePullPolicy,
                                String fluentdConfig) {
    if (fluentdSpecification == null) {
      fluentdSpecification = new FluentdSpecification();
    }
    fluentdSpecification.setWatchIntrospectorLogs(watchIntrospectorLog);
    fluentdSpecification.setElasticSearchCredentials(credentialName);
    fluentdSpecification.setFluentdConfiguration(fluentdConfig);
    fluentdSpecification.setImage(image);
    fluentdSpecification.setImagePullPolicy(imagePullPolicy);
    return this;
  }

  /**
   * Specifies the image for the monitoring exporter sidecar.
   * @param imageName the name of the image
   */
  public void setFluentdImage(String imageName) {
    assert fluentdSpecification != null : "May not set image without configuration";

    fluentdSpecification.setImage(imageName);
  }

  /**
   * Specifies the pull policy for the fluentd image.
   * @param pullPolicy a Kubernetes pull policy
   */
  public void setFluentdImagePullPolicy(String pullPolicy) {
    assert fluentdSpecification != null : "May not set image pull policy without configuration";

    fluentdSpecification.setImagePullPolicy(pullPolicy);
  }

  @ApiModelProperty("Configuration for the Administration Server.")
  private AdminServer adminServer;

  @ApiModelProperty("Configuration for individual Managed Servers.")
  private List<ManagedServer> managedServers = new ArrayList<>();

  @ApiModelProperty("References to Cluster resources that describe the lifecycle options for all of the Managed Server "
      + "members of a WebLogic cluster, including Java options, environment variables, additional Pod content, and "
      + "the ability to explicitly start, stop, or restart cluster members. The Cluster resource must describe a "
      + "cluster that already exists in the WebLogic domain configuration.")
  public List<V1LocalObjectReference> clusters = new ArrayList<>();

  @ApiModelProperty("Experimental feature configurations.")
  private Experimental experimental;

  @ApiModelProperty("Configuration affecting server pods.")
  private ServerPod serverPod;

  @ApiModelProperty(
      "Customization affecting ClusterIP Kubernetes services for WebLogic Server instances.")
  private ServerService serverService;

  @ApiModelProperty(
      "If present, every time this value is updated the operator will restart"
          + " the required servers.")
  private String restartVersion;

  @ApiModelProperty(
      "Configuration for monitoring exporter.")
  private MonitoringExporterSpecification monitoringExporter;

  @ApiModelProperty(
      "Configure auxiliary image volumes including their respective mount paths. Auxiliary image volumes are in "
      + "turn referenced by one or more serverPod.auxiliaryImages mounts, and are internally implemented using a "
      + "Kubernetes 'emptyDir' volume.")
  private List<AuxiliaryImageVolume> auxiliaryImageVolumes;


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

  public DomainSpec webLogicCredentialsSecret(V1LocalObjectReference webLogicCredentialsSecret) {
    this.webLogicCredentialsSecret = webLogicCredentialsSecret;
    return this;
  }

  public V1LocalObjectReference webLogicCredentialsSecret() {
    return webLogicCredentialsSecret;
  }

  public V1LocalObjectReference getWebLogicCredentialsSecret() {
    return webLogicCredentialsSecret;
  }

  public void setWebLogicCredentialsSecret(V1LocalObjectReference webLogicCredentialsSecret) {
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

  public DomainSpec logHomeLayout(String logHomeLayout) {
    this.logHomeLayout = logHomeLayout;
    return this;
  }

  public String getLogHomeLayout() {
    return logHomeLayout;
  }

  public void setLogHomeLayout(String logHomeLayout) {
    this.logHomeLayout = logHomeLayout;
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

  public DomainSpec failureRetryIntervalSeconds(Long failureRetryIntervalSeconds) {
    this.failureRetryIntervalSeconds = failureRetryIntervalSeconds;
    return this;
  }

  public Long failureRetryIntervalSeconds() {
    return failureRetryIntervalSeconds;
  }

  public Long getFailureRetryIntervalSeconds() {
    return failureRetryIntervalSeconds;
  }

  public void setFailureRetryIntervalSeconds(Long failureRetryIntervalSeconds) {
    this.failureRetryIntervalSeconds = failureRetryIntervalSeconds;
  }

  public DomainSpec failureRetryLimitMinutes(Long failureRetryLimitMinutes) {
    this.failureRetryLimitMinutes = failureRetryLimitMinutes;
    return this;
  }

  public Long failureRetryLimitMinutes() {
    return failureRetryLimitMinutes;
  }

  public Long getFailureRetryLimitMinutes() {
    return failureRetryLimitMinutes;
  }

  public void setFailureRetryLimitMinutes(Long failureRetryLimitMinutes) {
    this.failureRetryLimitMinutes = failureRetryLimitMinutes;
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

  public DomainSpec maxClusterUnavailable(Integer maxClusterUnavailable) {
    this.maxClusterUnavailable = maxClusterUnavailable;
    return this;
  }

  public Integer maxClusterUnavailable() {
    return this.maxClusterUnavailable;
  }

  public Integer getMaxClusterUnavailable() {
    return maxClusterUnavailable;
  }

  public void setMaxClusterUnavailable(Integer maxClusterUnavailable) {
    this.maxClusterUnavailable = maxClusterUnavailable;
  }

  public DomainSpec maxClusterConcurrentStartup(Integer maxClusterConcurrentStartup) {
    this.maxClusterConcurrentStartup = maxClusterConcurrentStartup;
    return this;
  }

  public Integer maxClusterConcurrentStartup() {
    return this.maxClusterConcurrentStartup;
  }

  public Integer getMaxClusterConcurrentStartup() {
    return maxClusterConcurrentStartup;
  }

  public void setMaxClusterConcurrentStartup(Integer maxClusterConcurrentStartup) {
    this.maxClusterConcurrentStartup = maxClusterConcurrentStartup;
  }

  public DomainSpec maxClusterConcurrentShutdown(Integer maxClusterConcurrentShutdown) {
    this.maxClusterConcurrentShutdown = maxClusterConcurrentShutdown;
    return this;
  }

  public Integer maxClusterConcurrentShutdown() {
    return this.maxClusterConcurrentShutdown;
  }

  public Integer getMaxClusterConcurrentShutdown() {
    return maxClusterConcurrentShutdown;
  }

  public void setMaxClusterConcurrentShutdown(Integer maxClusterConcurrentShutdown) {
    this.maxClusterConcurrentShutdown = maxClusterConcurrentShutdown;
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

  public DomainSpec clusters(List<V1LocalObjectReference> clusters) {
    this.clusters = clusters;
    return this;
  }

  public List<V1LocalObjectReference> clusters() {
    return clusters;
  }


  public List<V1LocalObjectReference> getClusters() {
    return clusters;
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

  public MonitoringExporterSpecification getMonitoringExporter() {
    return monitoringExporter;
  }

  public void monitoringExporter(MonitoringExporterSpecification monitoringExporter) {
    this.monitoringExporter = monitoringExporter;
  }

  /**
   * Adds auxiliary image volume item.
   * @param auxiliaryImageVolumesItem AuxiliaryImageVolumeItem
   * @return this
   */
  public DomainSpec addAuxiliaryImageVolumesItem(AuxiliaryImageVolume auxiliaryImageVolumesItem) {
    if (auxiliaryImageVolumes == null) {
      auxiliaryImageVolumes = new ArrayList<>();
    }
    auxiliaryImageVolumes.add(auxiliaryImageVolumesItem);
    return this;
  }

  public List<AuxiliaryImageVolume> getAuxiliaryImageVolumes() {
    return auxiliaryImageVolumes;
  }

  public void setAuxiliaryImageVolumes(List<AuxiliaryImageVolume> auxiliaryImageVolumes) {
    this.auxiliaryImageVolumes = auxiliaryImageVolumes;
  }

  /**
   * Adds a Cluster resource reference to the DomainSpec.
   *
   * @param reference The cluster reference to be added to this DomainSpec
   * @return this object
   */
  public DomainSpec withCluster(V1LocalObjectReference reference) {
    clusters.add(reference);
    return this;
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
            .append("failureRetryIntervalSeconds", failureRetryIntervalSeconds)
            .append("failureRetryLimitMinutes", failureRetryLimitMinutes)
            .append("dataHome", dataHome)
            .append("includeServerOutInPodLog", includeServerOutInPodLog)
            .append("image", image)
            .append("imagePullPolicy", imagePullPolicy)
            .append("imagePullSecrets", imagePullSecrets)
            .append("auxiliaryImageVolumes", auxiliaryImageVolumes)
            .append("replicas", replicas)
            .append("domainHomeSourceType", domainHomeSourceType)
            .append("introspectVersion", introspectVersion)
            .append("configuration", configuration)
            .append("adminServer", adminServer)
            .append("managedServers", managedServers)
            .append("clusters", clusters)
            .append("experimental", experimental)
            .append("serverPod", serverPod)
            .append("serverService", serverService)
            .append("restartVersion", restartVersion)
            .append("monitoringExporter", monitoringExporter)
            .append("fluentdSpecification", fluentdSpecification)
            .append("maxClusterUnavailable", maxClusterUnavailable)
            .append("maxClusterConcurrentStartup", maxClusterConcurrentStartup)
            .append("maxClusterConcurrentShutdown", maxClusterConcurrentShutdown);

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
            .append(failureRetryIntervalSeconds)
            .append(failureRetryLimitMinutes)
            .append(dataHome)
            .append(includeServerOutInPodLog)
            .append(image)
            .append(imagePullPolicy)
            .append(imagePullSecrets)
            .append(auxiliaryImageVolumes)
            .append(replicas)
            .append(domainHomeSourceType)
            .append(introspectVersion)
            .append(configuration)
            .append(adminServer)
            .append(managedServers)
            .append(clusters)
            .append(experimental)
            .append(serverPod)
            .append(serverService)
            .append(restartVersion)
            .append(monitoringExporter)
            .append(fluentdSpecification)
            .append(maxClusterUnavailable)
            .append(maxClusterConcurrentStartup)
            .append(maxClusterConcurrentShutdown);

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
            .append(failureRetryIntervalSeconds, rhs.failureRetryIntervalSeconds)
            .append(failureRetryLimitMinutes, rhs.failureRetryLimitMinutes)
            .append(dataHome, rhs.dataHome)
            .append(includeServerOutInPodLog, rhs.includeServerOutInPodLog)
            .append(image, rhs.image)
            .append(imagePullPolicy, rhs.imagePullPolicy)
            .append(imagePullSecrets, rhs.imagePullSecrets)
            .append(auxiliaryImageVolumes, rhs.auxiliaryImageVolumes)
            .append(replicas, rhs.replicas)
            .append(domainHomeSourceType, rhs.domainHomeSourceType)
            .append(introspectVersion, rhs.introspectVersion)
            .append(configuration, rhs.configuration)
            .append(adminServer, rhs.adminServer)
            .append(managedServers, rhs.managedServers)
            .append(clusters, rhs.clusters)
            .append(experimental, rhs.experimental)
            .append(serverPod, rhs.serverPod)
            .append(serverService, rhs.serverService)
            .append(restartVersion, rhs.restartVersion)
            .append(monitoringExporter, rhs.monitoringExporter)
            .append(fluentdSpecification, rhs.fluentdSpecification)
            .append(maxClusterUnavailable, rhs.maxClusterUnavailable)
            .append(maxClusterConcurrentStartup, rhs.maxClusterConcurrentStartup)
            .append(maxClusterConcurrentShutdown, rhs.maxClusterConcurrentShutdown);
    return builder.isEquals();
  }
}

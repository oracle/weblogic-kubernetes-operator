// Copyright (c) 2017, 2025, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.weblogic.domain.model;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.google.gson.annotations.SerializedName;
import io.kubernetes.client.openapi.models.V1LocalObjectReference;
import io.kubernetes.client.openapi.models.V1ResourceRequirements;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import oracle.kubernetes.common.utils.CommonUtils;
import oracle.kubernetes.json.Default;
import oracle.kubernetes.json.Description;
import oracle.kubernetes.json.EnumClass;
import oracle.kubernetes.json.Pattern;
import oracle.kubernetes.json.Range;
import oracle.kubernetes.operator.DomainSourceType;
import oracle.kubernetes.operator.KubernetesConstants;
import oracle.kubernetes.operator.LogHomeLayoutType;
import oracle.kubernetes.operator.ModelInImageDomainType;
import oracle.kubernetes.operator.OverrideDistributionStrategy;
import oracle.kubernetes.operator.ServerStartPolicy;
import oracle.kubernetes.operator.processing.EffectiveAdminServerSpec;
import oracle.kubernetes.operator.processing.EffectiveClusterSpec;
import oracle.kubernetes.operator.processing.EffectiveIntrospectorJobPodSpec;
import oracle.kubernetes.operator.processing.EffectiveServerSpec;
import oracle.kubernetes.weblogic.domain.EffectiveConfigurationFactory;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;

import static oracle.kubernetes.operator.KubernetesConstants.DEFAULT_IMAGE;
import static oracle.kubernetes.operator.KubernetesConstants.DEFAULT_MAX_CLUSTER_CONCURRENT_SHUTDOWN;
import static oracle.kubernetes.operator.KubernetesConstants.DEFAULT_MAX_CLUSTER_CONCURRENT_START_UP;
import static oracle.kubernetes.operator.KubernetesConstants.DEFAULT_MAX_CLUSTER_UNAVAILABLE;
import static oracle.kubernetes.operator.KubernetesConstants.DEFAULT_REPLACE_VARIABLES_IN_JAVA_OPTIONS;
import static oracle.kubernetes.weblogic.domain.model.Model.DEFAULT_AUXILIARY_IMAGE_MOUNT_PATH;
import static oracle.kubernetes.weblogic.domain.model.Model.DEFAULT_WDT_INSTALL_HOME;
import static oracle.kubernetes.weblogic.domain.model.Model.DEFAULT_WDT_MODEL_HOME;

/** DomainSpec is a description of a domain. */
@Description("The specification of the operation of the WebLogic domain. Required.")
public class DomainSpec extends BaseConfiguration {

  private static final long DEFAULT_RETRY_INTERVAL_SECONDS = TimeUnit.MINUTES.toSeconds(2);
  private static final long DEFAULT_RETRY_LIMIT_MINUTES = TimeUnit.HOURS.toMinutes(24);

  /** Domain unique identifier. Must be unique across the Kubernetes cluster. */
  @Description(
      "Domain unique identifier. It is recommended that this value be unique to assist in future work to "
      + "identify related domains in active-passive scenarios across data centers; however, it is only required "
      + "that this value be unique within the namespace, similarly to the names of Kubernetes resources. "
      + "This value is distinct and need not match the domain name from the WebLogic domain configuration. "
      + "Defaults to the value of `metadata.name`.")
  @Pattern("^[a-z0-9-.]{1,45}$")
  @SerializedName("domainUID")
  private String domainUid;

  /**
   * Domain home.
   *
   * @since 2.0
   */
  @Description(
      "The directory containing the WebLogic domain configuration inside the container."
          + " Defaults to /shared/domains/<domainUID> if `domainHomeSourceType` is PersistentVolume."
          + " Defaults to /u01/oracle/user_projects/domains/ if `domainHomeSourceType` is Image."
          + " Defaults to /u01/domains/<domainUID> if `domainHomeSourceType` is FromModel.")
  private String domainHome;

  /**
   * Tells the operator whether the customer wants the server to be running. For non-clustered
   * servers - the operator will start it if the policy isn't Never. For clustered servers - the
   * operator will start it if the policy is Always or the policy is IfNeeded and the server needs
   * to be started to get to the cluster's replica count.
   *
   * @since 2.0
   */
  @EnumClass(value = ServerStartPolicy.class, qualifier = "forDomain")
  @Description("The strategy for deciding whether to start a WebLogic Server instance. "
      + "Legal values are AdminOnly, Never, or IfNeeded. Defaults to IfNeeded. "
      + "More info: https://oracle.github.io/weblogic-kubernetes-operator/userguide/managing-domains/"
      + "domain-lifecycle/startup/#starting-and-stopping-servers.")
  @Default(strDefault = "IfNeeded")
  private ServerStartPolicy serverStartPolicy;

  /**
   * Reference to secret containing WebLogic startup credentials username and password. Secret must
   * contain keys names 'username' and 'password'. Required.
   */
  @Description(
      "Reference to a Kubernetes Secret that contains"
          + " the user name and password needed to boot a WebLogic Server under the `username` and "
          + "`password` fields.")
  @Valid
  @NotNull
  private V1LocalObjectReference webLogicCredentialsSecret;

  /**
   * The in-pod name of the directory to store the domain, Node Manager, server logs, server
   * .out, introspector.out, and HTTP access log files in.
   */
  @Description(
      "The directory in a server's container in which to store the domain, Node Manager, server logs, "
          + "server *.out, introspector .out, and optionally HTTP access log files "
          + "if `httpAccessLogInLogHome` is true. "
          + "Default is `/shared/logs/DOMAIN-UID`. "
          + "Ignored if `logHomeEnabled` is false."
          + "See also `domains.spec.logHomeLayout`.")
  private String logHome;

  /**
   * The log files layout under `logHome`.
   *   Flat - all files is in one directory
   *   ByServers - log files are organized under loghome/servers/server name/logs.
   * */
  @Description(
      "Control how log files under `logHome` are organized when logHome is set and `logHomeEnabled` is true. "
        + "`Flat` specifies that all files are kept directly in the `logHome` root directory. "
        + "`ByServers` specifies that domain log files and `introspector.out` are at the `logHome` root level, "
        + "all other files are organized under the respective server name logs directory  "
        + "`logHome/servers/<server name>/logs`. Defaults to `ByServers`.")
  private LogHomeLayoutType logHomeLayout = LogHomeLayoutType.BY_SERVERS;


  /**
   * Whether the log home is enabled.
   *
   * @since 2.0
   */
  @Description(
      "Specifies whether the log home folder is enabled. "
          + "Defaults to true if `domainHomeSourceType` is PersistentVolume; false, otherwise.")
  private Boolean logHomeEnabled;

  /**
   * An optional, in-pod location for data storage of default and custom file stores. If dataHome is
   * not specified or its value is either not set or empty (e.g. dataHome: "") then the data storage
   * directories are determined from the WebLogic domain home configuration.
   */
  @Description(
      "An optional directory in a server's container for data storage of default and custom file stores. "
          + "If `dataHome` is not specified or its value is either not set or empty, "
          + "then the data storage directories are determined from the WebLogic domain configuration.")
  private String dataHome;

  /** Whether to include the server .out file to the pod's stdout. Default is true. */
  @Description("Specifies whether the server .out file will be included in the Pod's log. "
      + "Defaults to true.")
  @Default(boolDefault = true)
  private Boolean includeServerOutInPodLog;

  /** Whether to replace the environment variables in the Java options when JAVA_OPTIONS specified using config-map.
   * Default is false.
   */
  @Description("Specifies whether the operator will replace the environment variables in the Java options "
      + "in certain situations, such as when the JAVA_OPTIONS are specified using a config map. "
      + "Defaults to false.")
  @Default(boolDefault = false)
  private Boolean replaceVariablesInJavaOptions;

  /** Whether to include the server HTTP access log file to the  directory specified in {@link #logHome}
   *  if {@link #logHomeEnabled} is true. Default is true. */
  @Description("Specifies whether the server HTTP access log files will be written to the same "
      + "directory specified in `logHome`. Otherwise, server HTTP access log files will be written to "
      + "the directory configured in the WebLogic domain configuration. Defaults to true.")
  @Default(boolDefault = true)
  private Boolean httpAccessLogInLogHome;

  /**
   * Full path of an optional liveness probe custom script for WebLogic Server instance pods.
   * The existing liveness probe script `livenessProbe.sh` will invoke this custom script after the
   * existing script performs its own checks. This element is optional and is for advanced usage only.
   * Its value is not set by default. If the custom script fails with non-zero exit status,
   * then pod will fail the liveness probe and Kubernetes will restart the container.
   * If the script specified by this element value is not found, then it is ignored.
   */
  @Description("Full path of an optional liveness probe custom script for WebLogic Server instance pods. "
      + "The existing liveness probe script `livenessProbe.sh` will invoke this custom script after the "
      + "existing script performs its own checks. This element is optional and is for advanced usage only. "
      + "Its value is not set by default. If the custom script fails with non-zero exit status, "
      + "then pod will fail the liveness probe and Kubernetes will restart the container. "
      + "If the script specified by this element value is not found, then it is ignored."
  )
  private String livenessProbeCustomScript;

  /**
   * The WebLogic Server image.
   *
   * <p>Defaults to container-registry.oracle.com/middleware/weblogic:12.2.1.4
   */
  @Description(
      "The WebLogic Server image; required when `domainHomeSourceType` is Image or FromModel; "
          + "otherwise, defaults to container-registry.oracle.com/middleware/weblogic:12.2.1.4.")
  private String image;

  /**
   * The image pull policy for the WebLogic Server image. Legal values are Always, Never and,
   * IfNotPresent.
   *
   * <p>Defaults to Always if image ends in :latest; IfNotPresent, otherwise.
   *
   * <p>More info: https://kubernetes.io/docs/concepts/containers/images#updating-images
   */
  @Description(
      "The image pull policy for the WebLogic Server image. "
          + "Legal values are Always, Never, and IfNotPresent. "
          + "Defaults to Always if image ends in :latest; IfNotPresent, otherwise.")
  private String imagePullPolicy;

  /**
   * The image pull secrets for the WebLogic Server image.
   *
   * <p>More info:
   * https://kubernetes.io/docs/reference/generated/kubernetes-api/v1.10/#localobjectreference-v1-core
   *
   * @since 2.0
   */
  @Description("A list of image pull Secrets for the WebLogic Server image.")
  private List<V1LocalObjectReference> imagePullSecrets;

  /**
   * The desired number of running managed servers in each WebLogic cluster that is not explicitly
   * configured in a cluster specification.
   */

  @Description(
      "The default number of cluster member Managed Server instances to start for each WebLogic cluster in the "
      + "domain configuration, unless `replicas` is specified for that cluster under the `clusters` field. "
      + "For each cluster, the operator will sort cluster member Managed Server names from the WebLogic domain "
      + "configuration by normalizing any numbers in the Managed Server name and then sorting alphabetically. "
      + "This is done so that server names such as \"managed-server10\" come after \"managed-server9\". "
      + "The operator will then start Managed Servers from the sorted list, "
      + "up to the `replicas` count, unless specific Managed Servers are specified as "
      + "starting in their entry under the `managedServers` field. In that case, the specified Managed Servers "
      + "will be started and then additional cluster members "
      + "will be started, up to the `replicas` count, by finding further cluster members in the sorted list that are "
      + "not already started. If cluster members are started "
      + "because of their entries under `managedServers`, then a cluster may have more cluster members "
      + "running than its `replicas` count. Defaults to 1.")
  @Range(minimum = 0)
  @Default(intDefault = 1)
  private Integer replicas;

  @Description(
      "The maximum number of cluster member Managed Server instances that the operator will start in parallel "
      + "for a given cluster, if `maxConcurrentStartup` is not specified for a specific cluster under the "
      + "`clusters` field. A value of 0 means there is no configured limit. Defaults to 0."
  )
  @Range(minimum = 0)
  @Default(intDefault = 0)
  private Integer maxClusterConcurrentStartup;

  @Description(
      "The default maximum number of WebLogic Server instances that a cluster will shut down in parallel when it "
      + "is being partially shut down by lowering its replica count. You can override this default on a "
      + "per cluster basis by setting the cluster's `maxConcurrentShutdown` field. A value of 0 means "
      + "there is no limit. Defaults to 1."
  )
  @Range(minimum = 0)
  @Default(intDefault = 1)
  private Integer maxClusterConcurrentShutdown;

  @Description(
      "The maximum number of cluster members that can be temporarily unavailable. You can override this default "
      + "on a per cluster basis by setting the cluster's `maxUnavailable` field. Defaults to 1."
  )
  @Range(minimum = 1)
  @Default(intDefault = 1)
  private Integer maxClusterUnavailable;

  @Description("The wait time in seconds before the start of the next retry after a Severe failure. Defaults to 120.")
  @Range(minimum = 0)
  @Default(intDefault = 120)
  private Long failureRetryIntervalSeconds;

  @Description("The time in minutes before the operator will stop retrying Severe failures. Defaults to 1440.")
  @Range(minimum = 0)
  @Default(intDefault = 1440)
  private Long failureRetryLimitMinutes;

  /**
   * Whether the domain home is part of the image.
   *
   * @since 2.0
   */
  @Description(
      "Domain home file system source type: Legal values: `Image`, `PersistentVolume`, `FromModel`."
      + " `Image` indicates that the domain home file system is present in the container image"
      + " specified by the `image` field. `PersistentVolume` indicates that the domain home file system is located"
      + " on a persistent volume. `FromModel` indicates that the domain home file system will be created"
      + " and managed by the operator based on a WDT domain model."
      + " Defaults to `Image`, unless `configuration.model` is set, in which case the default is `FromModel`.")
  private DomainSourceType domainHomeSourceType;

  /**
   * Tells the operator to start the introspect domain job.
   *
   * @since 3.0.0
   */
  @Description(
      "Changes to this field cause the operator to repeat its introspection of the WebLogic domain configuration. "
      + "Repeating introspection is required for the operator to recognize changes to the domain configuration, "
      + "such as adding a new WebLogic cluster or Managed Server instance, to regenerate configuration overrides, or "
      + "to regenerate the WebLogic domain home when the `domainHomeSourceType` is `FromModel`. Introspection occurs "
      + "automatically, without requiring change to this field, when servers are first started or restarted after a "
      + "full domain shut down. For the `FromModel` `domainHomeSourceType`, introspection also occurs when a running "
      + "server must be restarted because of changes to any of the fields listed here: "
      + "https://oracle.github.io/weblogic-kubernetes-operator/userguide/managing-domains/"
      + "domain-lifecycle/startup/#properties-that-cause-servers-to-be-restarted. "
      + "The introspectVersion value must be a valid label value in Kubernetes. "
      + "See also `domains.spec.configuration.overrideDistributionStrategy`.")
  private String introspectVersion;

  @Description("Models and overrides affecting the WebLogic domain configuration.")
  private Configuration configuration;

  /**
   * The WebLogic Monitoring Exporter configuration.
   *
   * @since 3.2
   */
  @Description("Automatic deployment and configuration of the WebLogic Monitoring Exporter. If specified, the operator "
      + "will deploy a sidecar container alongside each WebLogic Server instance that runs the exporter. "
      + "WebLogic Server instances that are already running when the `monitoringExporter` field is created or deleted, "
      + "will not be affected until they are restarted. When any given server "
      + "is restarted for another reason, such as a change to the `restartVersion`, then the newly created pod will "
      + "have the exporter sidecar or not, as appropriate. See https://github.com/oracle/weblogic-monitoring-exporter.")
  private MonitoringExporterSpecification monitoringExporter;

  public MonitoringExporterSpecification getMonitoringExporterSpecification() {
    return monitoringExporter;
  }

  MonitoringExporterConfiguration getMonitoringExporterConfiguration() {
    return Optional.ofNullable(monitoringExporter).map(MonitoringExporterSpecification::getConfiguration).orElse(null);
  }

  void createMonitoringExporterConfiguration(String yaml) {
    if (monitoringExporter == null) {
      monitoringExporter = new MonitoringExporterSpecification();
    }

    monitoringExporter.createConfiguration(yaml);
  }

  String getMonitoringExporterImage() {
    return monitoringExporter == null ? null : monitoringExporter.getImage();
  }

  String getMonitoringExporterImagePullPolicy() {
    return monitoringExporter == null ? null : monitoringExporter.getImagePullPolicy();
  }

  V1ResourceRequirements getMonitoringExporterResourceRequirements() {
    return Optional.ofNullable(monitoringExporter).map(MonitoringExporterSpecification::getResources).orElse(null);
  }

  /**
   * Specifies the image for the monitoring exporter sidecar.
   * @param resourceRequirements the name of the image
   */
  public void setMonitoringExporterResources(V1ResourceRequirements resourceRequirements) {
    assert monitoringExporter != null : "May not set resources without configuration";

    monitoringExporter.setResources(resourceRequirements);
  }

  /**
   * Specifies the image for the monitoring exporter sidecar.
   * @param imageName the name of the image
   */
  public void setMonitoringExporterImage(String imageName) {
    assert monitoringExporter != null : "May not set image without configuration";

    monitoringExporter.setImage(imageName);
  }

  /**
   * Specifies the port for the exporter sidecar.
   * @param port port number
   */
  public void setMonitoringExporterPort(Integer port) {
    assert monitoringExporter != null : "May not set exporter port without configuration";

    monitoringExporter.setPort(port);
  }

  /**
   * The Fluentd configuration.
   *
   */
  @Description("Automatic fluentd sidecar injection. If "
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

  DomainSpec withFluentdConfiguration(boolean watchIntrospectorLog, String credentialName,
                                      String fluentdConfig, List<String> args, List<String> command) {
    if (fluentdSpecification == null) {
      fluentdSpecification = new FluentdSpecification();
    }
    fluentdSpecification.setWatchIntrospectorLogs(watchIntrospectorLog);
    fluentdSpecification.setElasticSearchCredentials(credentialName);
    fluentdSpecification.setFluentdConfiguration(fluentdConfig);
    fluentdSpecification.setContainerArgs(args);
    fluentdSpecification.setContainerCommand(command);
    return this;
  }

  /**
   *  FluentBit configuration.
   */
  @Description("Automatic fluent-bit sidecar injection. If "
          + "specified, the operator "
          + "will deploy a sidecar container alongside each WebLogic Server instance that runs the fluent-bit, "
          + "Optionally, the introspector job pod can be enabled to deploy with the fluent-bit sidecar container. "
          + "WebLogic Server instances that are already running when the `fluentbitSpecification` field is created "
          + "or deleted, will not be affected until they are restarted. When any given server "
          + "is restarted for another reason, such as a change to the `restartVersion`, then the newly created pod "
          + " will have the fluent-bit sidecar or not, as appropriate")
  private FluentbitSpecification fluentbitSpecification;

  public FluentbitSpecification getFluentbitSpecification() {
    return fluentbitSpecification;
  }

  DomainSpec withFluentbitConfiguration(boolean watchIntrospectorLog,
                                        String credentialName, String fluentbitConfig,
                                        String parserConfig,
                                        List<String> args, List<String> command) {
    if (fluentbitSpecification == null) {
      fluentbitSpecification = new FluentbitSpecification();
    }
    fluentbitSpecification.setWatchIntrospectorLogs(watchIntrospectorLog);
    fluentbitSpecification.setElasticSearchCredentials(credentialName);
    fluentbitSpecification.setFluentbitConfiguration(fluentbitConfig);
    fluentbitSpecification.setParserConfiguration(parserConfig);
    fluentbitSpecification.setContainerArgs(args);
    fluentbitSpecification.setContainerCommand(command);
    return this;
  }

  /**
   * The configuration for the introspector job pod.
   *
   */
  @Description("Lifecycle options for the Introspector Job Pod, including Java options, environment variables, "
      + "and resources.")
  private Introspector introspector;

  /**
   * The configuration for the admin server.
   *
   * @since 2.0
   */
  @Description("Lifecycle options for the Administration Server, including Java options, environment variables, "
      + "additional Pod content, and which channels or network access points should be exposed using "
      + "a NodePort Service.")
  private AdminServer adminServer;

  /**
   * The configured managed servers.
   *
   * @since 2.0
   */
  @Description("Lifecycle options for individual Managed Servers, including Java options, environment variables, "
      + "additional Pod content, and the ability to explicitly start, stop, or restart a named server instance. "
      + "The `serverName` field of each entry must match a Managed Server that already exists in the WebLogic "
      + "domain configuration or that matches a dynamic cluster member based on the server template.")
  private final List<ManagedServer> managedServers = new ArrayList<>();

  /**
   * The configured clusters.
   *
   * @since 2.0
   */
  @Description("References to Cluster resources that describe the lifecycle options for all of the Managed Server "
      + "members of a WebLogic cluster, including Java options, environment variables, additional Pod content, and "
      + "the ability to explicitly start, stop, or restart cluster members. The Cluster resource must describe a "
      + "cluster that already exists in the WebLogic domain configuration.")
  protected final List<V1LocalObjectReference> clusters = new ArrayList<>();

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

  /**
   * Get Admin Server configuration or else create default, if doesn't exist.
   * @return Admin Server configuration.
   */
  public Introspector getOrCreateIntrospector() {
    if (introspector != null) {
      return introspector;
    }

    return createIntrospector();
  }

  private Introspector createIntrospector() {
    Introspector newIntrospector = new Introspector();
    setIntrospector(newIntrospector);
    return newIntrospector;
  }

  /**
   * Get Admin Server configuration or else create default, if doesn't exist.
   * @return Admin Server configuration.
   */
  public AdminServer getOrCreateAdminServer() {
    if (adminServer != null) {
      return adminServer;
    }

    return createAdminServer();
  }

  private AdminServer createAdminServer() {
    AdminServer newAdminServer = new AdminServer();
    setAdminServer(newAdminServer);
    return newAdminServer;
  }

  @SuppressWarnings("unused")
  EffectiveConfigurationFactory getEffectiveConfigurationFactory(
      String apiVersion) {
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

  public String getLivenessProbeCustomScript() {
    return Optional.ofNullable(livenessProbeCustomScript).orElse("");
  }

  /**
   * Whether to replace environment variables in Java options such as when JAVA_OPTIONS specified using config map.
   *
   * @return true if environment variables should be replaced, false otherwise.
   */
  public boolean getReplaceVariablesInJavaOptions() {
    return Optional.ofNullable(replaceVariablesInJavaOptions).orElse(DEFAULT_REPLACE_VARIABLES_IN_JAVA_OPTIONS);
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

  public void setLivenessProbeCustomScript(String livenessProbeCustomScript) {
    this.livenessProbeCustomScript = livenessProbeCustomScript;
  }

  public void setReplaceVariablesInJavaOptions(boolean replaceVariablesInJavaOptions) {
    this.replaceVariablesInJavaOptions = replaceVariablesInJavaOptions;
  }

  @Nullable
  @Override
  public ServerStartPolicy getServerStartPolicy() {
    return Optional.ofNullable(serverStartPolicy).orElse(ServerStartPolicy.IF_NEEDED);
  }

  @Override
  public void setServerStartPolicy(ServerStartPolicy serverStartPolicy) {
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

  public V1LocalObjectReference getWebLogicCredentialsSecret() {
    return webLogicCredentialsSecret;
  }

  @SuppressWarnings("unused")
  public void setWebLogicCredentialsSecret(V1LocalObjectReference webLogicCredentialsSecret) {
    this.webLogicCredentialsSecret = webLogicCredentialsSecret;
  }

  /**
   * Reference to secret containing WebLogic startup credentials username and password. Secret must
   * contain keys names 'username' and 'password'. Required.
   *
   * @param webLogicCredentialsSecret WebLogic startup credentials secret
   * @return this
   */
  public DomainSpec withWebLogicCredentialsSecret(V1LocalObjectReference webLogicCredentialsSecret) {
    this.webLogicCredentialsSecret = webLogicCredentialsSecret;
    return this;
  }

  public String getImage() {
    return Optional.ofNullable(image).orElse(DEFAULT_IMAGE);
  }

  public void setImage(@Nullable String image) {
    this.image = image;
  }

  public String getImagePullPolicy() {
    return Optional.ofNullable(imagePullPolicy).orElse(CommonUtils.getInferredImagePullPolicy(getImage()));
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

  /**
   * Log Home Layout.
   *
   * @return The logHomeLayout value.
   */
  LogHomeLayoutType getLogHomeLayout() {
    return logHomeLayout;
  }

  public void setLogHomeLayout(LogHomeLayoutType logHomeLayout) {
    this.logHomeLayout = logHomeLayout;
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

  public void setHttpAccessLogInLogHome(boolean httpAccessLogInLogHome) {
    this.httpAccessLogInLogHome = httpAccessLogInLogHome;
  }

  @Nonnull DomainSourceType getDomainHomeSourceType() {
    return Optional.ofNullable(domainHomeSourceType).orElse(inferDomainSourceType());
  }

  private DomainSourceType inferDomainSourceType() {
    if (getModel() != null) {
      return DomainSourceType.FROM_MODEL;
    } else {
      return DomainSourceType.IMAGE;
    }
  }

  public void setDomainHomeSourceType(DomainSourceType domainHomeSourceType) {
    this.domainHomeSourceType = domainHomeSourceType;
  }

  public DomainSpec withDomainHomeSourceType(DomainSourceType domainHomeSourceType) {
    setDomainHomeSourceType(domainHomeSourceType);
    return this;
  }

  public String getIntrospectVersion() {
    return introspectVersion;
  }

  public void setIntrospectVersion(String introspectVersion) {
    this.introspectVersion = introspectVersion;
  }

  public Configuration getConfiguration() {
    return configuration;
  }

  public void setConfiguration(Configuration configuration) {
    this.configuration = configuration;
  }

  public DomainSpec withConfiguration(Configuration configuration) {
    setConfiguration(configuration);
    return this;
  }

  /**
   * The desired number of running managed servers in each WebLogic cluster that is not explicitly
   * configured in clusters.
   *
   * @return replicas
   */
  public int getReplicas() {
    return Optional.ofNullable(replicas).orElse(1);
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

  public Integer getMaxClusterConcurrentStartup() {
    return Optional.ofNullable(maxClusterConcurrentStartup)
        .orElse(DEFAULT_MAX_CLUSTER_CONCURRENT_START_UP);
  }

  public Integer getMaxClusterConcurrentShutdown() {
    return Optional.ofNullable(maxClusterConcurrentShutdown)
        .orElse(DEFAULT_MAX_CLUSTER_CONCURRENT_SHUTDOWN);
  }

  public Integer getMaxClusterUnavailable() {
    return Optional.ofNullable(maxClusterUnavailable)
        .orElse(DEFAULT_MAX_CLUSTER_UNAVAILABLE);
  }

  @Nullable
  String getConfigOverrides() {
    return Optional.ofNullable(configuration).map(Configuration::getOverridesConfigMap).orElse(null);
  }

  @Nullable
  List<String> getConfigOverrideSecrets() {
    return Optional.ofNullable(configuration).map(Configuration::getSecrets).orElse(Collections.emptyList());
  }

  /**
   * Returns the strategy used for distributing changed config overrides.
   * @return the set or computed strategy
   */
  public OverrideDistributionStrategy getOverrideDistributionStrategy() {
    return Optional.ofNullable(configuration)
        .map(Configuration::getOverrideDistributionStrategy)
        .orElse(OverrideDistributionStrategy.DEFAULT);
  }

  Model getModel() {
    return Optional.ofNullable(configuration).map(Configuration::getModel).orElse(null);
  }

  /**
   * Test if the MII domain wants to use online update.
   *
   * @return true if using online update
   */
  boolean isUseOnlineUpdate() {
    return Optional.ofNullable(configuration)
        .map(Configuration::getModel)
        .map(Model::getOnlineUpdate)
        .map(OnlineUpdate::getEnabled)
        .orElse(false);
  }

  ModelInImageDomainType getWdtDomainType() {
    return Optional.ofNullable(configuration)
        .map(Configuration::getModel)
        .map(Model::getDomainType)
        .orElse(ModelInImageDomainType.WLS);
  }

  String getOpssWalletPasswordSecret() {
    return isInitializeDomainOnPV()
        ? getInitializeDomainOnPVOpssWalletPasswordSecret()
        : getModelOpssWalletPasswordSecret();
  }

  String getModelOpssWalletPasswordSecret() {
    return Optional.ofNullable(configuration)
        .map(Configuration::getOpss)
        .map(Opss::getWalletPasswordSecret)
        .orElse(null);
  }

  boolean isInitializeDomainOnPV() {
    return DomainSourceType.PERSISTENT_VOLUME == (getDomainHomeSourceType()) && getInitializeDomainOnPV() != null;
  }

  String getInitializeDomainOnPVOpssWalletPasswordSecret() {
    return Optional.ofNullable(getInitializeDomainOnPV())
        .map(InitializeDomainOnPV::getDomain)
        .map(DomainOnPV::getOpss)
        .map(Opss::getWalletPasswordSecret)
        .orElse(null);
  }

  /**
   * Get OPSS wallet file secret.
   * @return wallet file secret
   */
  public String getOpssWalletFileSecret() {
    return isInitializeDomainOnPV() ? getInitializeDomainOnPVOpssWalletFileSecret() : getModelOpssWalletFileSecret();
  }

  private String getModelOpssWalletFileSecret() {
    return Optional.ofNullable(configuration)
        .map(Configuration::getOpss)
        .map(Opss::getWalletFileSecret)
        .orElse(null);
  }

  String getModelEncryptionSecret() {
    return Optional.ofNullable(getInitializeDomainOnPV())
            .map(InitializeDomainOnPV::getModelEncryptionPassphraseSecret)
            .orElse(null);
  }

  private String getInitializeDomainOnPVOpssWalletFileSecret() {
    return Optional.ofNullable(getInitializeDomainOnPV())
        .map(InitializeDomainOnPV::getDomain)
        .map(DomainOnPV::getOpss)
        .map(Opss::getWalletFileSecret)
        .orElse(null);
  }

  String getInitializeDomainOnPVDomainType() {
    return Optional.ofNullable(getInitializeDomainOnPV())
        .map(InitializeDomainOnPV::getDomain)
        .map(DomainOnPV::getDomainType)
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
    if (isInitializeDomainOnPV()) {
      return Optional.ofNullable(configuration)
          .map(Configuration::getInitializeDomainOnPV)
          .map(InitializeDomainOnPV::getDomain)
          .map(DomainOnPV::getDomainCreationConfigMap)
          .orElse(null);
    } else {
      return Optional.ofNullable(configuration)
          .map(Configuration::getModel)
          .map(Model::getConfigMap)
          .orElse(null);
    }
  }

  /**
   * Returns the model home directory of the domain.
   *
   * @return model home directory
   */
  public String getModelHome() {
    return Optional.ofNullable(configuration)
        .map(Configuration::getModel).map(Model::getModelHome).orElse(getDefaultModelHome());
  }

  public void setModelHome(String modelHome) {
    Optional.ofNullable(configuration)
        .map(Configuration::getModel).ifPresent(m -> m.setModelHome(modelHome));
  }

  private String getDefaultModelHome() {
    return Optional.ofNullable(getAuxiliaryImages())
        .map(this::getDefaultModelHome)
        .orElse(DEFAULT_WDT_MODEL_HOME);
  }

  private String getDefaultModelHome(List<AuxiliaryImage> auxiliaryImages) {
    return !auxiliaryImages.isEmpty() ? getAuxiliaryImageVolumeMountPath() + "/models" : DEFAULT_WDT_MODEL_HOME;
  }

  /**
   * Returns the WDT install home directory of the domain.
   *
   * @return WDT install home directory
   */
  public String getWdtInstallHome() {
    return Optional.ofNullable(configuration)
        .map(Configuration::getModel).map(Model::getWdtInstallHome).orElse(getDefaultWDTInstallHome());
  }

  public void setWdtInstallHome(String wdtInstallHome) {
    Optional.ofNullable(configuration)
        .map(Configuration::getModel).ifPresent(m -> m.setWdtInstallHome(wdtInstallHome));
  }

  private String getDefaultWDTInstallHome() {
    return Optional.ofNullable(getAuxiliaryImages())
        .map(this::getDefaultWDTInstallHome)
        .orElse(DEFAULT_WDT_INSTALL_HOME);
  }

  private String getDefaultWDTInstallHome(List<AuxiliaryImage> auxiliaryImages) {
    return !auxiliaryImages.isEmpty() ? getAuxiliaryImageVolumeMountPath() + "/weblogic-deploy"
        : DEFAULT_WDT_INSTALL_HOME;
  }

  List<AuxiliaryImage> getAuxiliaryImages() {
    return Optional.ofNullable(configuration)
        .map(Configuration::getModel).map(Model::getAuxiliaryImages).orElse(null);
  }

  InitializeDomainOnPV getInitializeDomainOnPV() {
    return Optional.ofNullable(configuration)
        .map(Configuration::getInitializeDomainOnPV).orElse(null);
  }

  List<DomainCreationImage> getPVDomainCreationImages() {
    return Optional.ofNullable(getInitializeDomainOnPV()).map(InitializeDomainOnPV::getDomain)
        .map(DomainOnPV::getDomainCreationImages).orElse(null);
  }

  String getDomainCreationConfigMap() {
    return Optional.ofNullable(getInitializeDomainOnPV())
        .map(InitializeDomainOnPV::getDomain)
        .map(DomainOnPV::getDomainCreationConfigMap)
        .orElse(null);
  }

  String getAuxiliaryImageVolumeMountPath() {
    return Optional.ofNullable(configuration)
        .map(Configuration::getModel).map(Model::getAuxiliaryImageVolumeMountPath)
        .orElse(DEFAULT_AUXILIARY_IMAGE_MOUNT_PATH);
  }

  String getAuxiliaryImageVolumeMedium() {
    return Optional.ofNullable(configuration)
        .map(Configuration::getModel).map(Model::getAuxiliaryImageVolumeMedium).orElse(null);
  }

  String getAuxiliaryImageVolumeSizeLimit() {
    return Optional.ofNullable(configuration)
        .map(Configuration::getModel).map(Model::getAuxiliaryImageVolumeSizeLimit).orElse(null);
  }

  @Override
  public String toString() {
    ToStringBuilder builder =
        new ToStringBuilder(this)
            .appendSuper(super.toString())
            .append("adminServer", adminServer)
            .append("clusters", clusters)
            .append("configuration", configuration)
            .append("domainHome", domainHome)
            .append("domainHomeSourceType", domainHomeSourceType)
            .append("domainUID", domainUid)
            .append("image", image)
            .append("imagePullPolicy", imagePullPolicy)
            .append("imagePullSecrets", imagePullSecrets)
            .append("includeServerOutInPodLog", includeServerOutInPodLog)
            .append("introspectVersion", introspectVersion)
            .append("logHome", logHome)
            .append("logHomeLayout", logHomeLayout)
            .append("logHomeEnabled", logHomeEnabled)
            .append("managedServers", managedServers)
            .append("maxClusterConcurrentShutdown", maxClusterConcurrentShutdown)
            .append("maxClusterConcurrentStartup", maxClusterConcurrentStartup)
            .append("maxClusterUnavailable", maxClusterUnavailable)
            .append("monitoringExporter", monitoringExporter)
            .append("replicas", replicas)
            .append("serverStartPolicy", serverStartPolicy)
            .append("webLogicCredentialsSecret", webLogicCredentialsSecret)
            .append("fluentdSpecification", fluentdSpecification)
            .append("replaceVariablesInJavaOptions", replaceVariablesInJavaOptions)
                .append("fluentbitSpecification", fluentbitSpecification);

    return builder.toString();
  }

  @Override
  public int hashCode() {
    HashCodeBuilder builder =
        new HashCodeBuilder()
            .appendSuper(super.hashCode())
            .append(adminServer)
            .append(clusters)
            .append(configuration)
            .append(domainHome)
            .append(domainHomeSourceType)
            .append(domainUid)
            .append(image)
            .append(imagePullPolicy)
            .append(imagePullSecrets)
            .append(includeServerOutInPodLog)
            .append(introspectVersion)
            .append(logHome)
            .append(logHomeEnabled)
            .append(logHomeLayout)
            .append(managedServers)
            .append(maxClusterConcurrentShutdown)
            .append(maxClusterConcurrentStartup)
            .append(maxClusterUnavailable)
            .append(monitoringExporter)
            .append(replicas)
            .append(serverStartPolicy)
            .append(webLogicCredentialsSecret)
            .append(fluentdSpecification)
            .append(replaceVariablesInJavaOptions)
                .append(fluentbitSpecification);

    return builder.toHashCode();
  }

  @Override
  public boolean equals(Object other) {
    if (other == this) {
      return true;
    }
    if (!(other instanceof DomainSpec rhs)) {
      return false;
    }

    EqualsBuilder builder =
        new EqualsBuilder()
            .appendSuper(super.equals(other))
            .append(domainUid, rhs.domainUid)
            .append(domainHome, rhs.domainHome)
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
            .append(logHomeLayout, rhs.logHomeLayout)
            .append(logHomeEnabled, rhs.logHomeEnabled)
            .append(monitoringExporter, rhs.monitoringExporter)
            .append(includeServerOutInPodLog, rhs.includeServerOutInPodLog)
            .append(getMaxClusterConcurrentStartup(), rhs.getMaxClusterConcurrentStartup())
            .append(getMaxClusterConcurrentShutdown(), rhs.getMaxClusterConcurrentShutdown())
            .append(getMaxClusterUnavailable(), rhs.getMaxClusterUnavailable())
            .append(fluentdSpecification, rhs.getFluentdSpecification())
                .append(fluentbitSpecification, rhs.getFluentbitSpecification())
            .append(replaceVariablesInJavaOptions, rhs.replaceVariablesInJavaOptions);
    return builder.isEquals();
  }

  /**
   * Find Managed Server.
   * @param serverName name of managed server.
   * @return ManagedServer object or null, if not defined.
   */
  public ManagedServer getManagedServer(String serverName) {
    if (serverName != null) {
      for (ManagedServer s : managedServers) {
        if (serverName.equals(s.getServerName())) {
          return s;
        }
      }
    }
    return null;
  }

  public void setMaxClusterConcurrentStartup(Integer maxClusterConcurrentStartup) {
    this.maxClusterConcurrentStartup = maxClusterConcurrentStartup;
  }

  public void setMaxClusterConcurrentShutdown(Integer maxClusterConcurrentShutdown) {
    this.maxClusterConcurrentShutdown = maxClusterConcurrentShutdown;
  }

  public void setMaxClusterUnavailable(Integer maxClusterUnavailable) {
    this.maxClusterUnavailable = maxClusterUnavailable;
  }


  public Introspector getIntrospector() {
    return introspector;
  }

  private void setIntrospector(Introspector introspector) {
    this.introspector = introspector;
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

  public List<V1LocalObjectReference> getClusters() {
    return clusters;
  }

  void setFailureRetryIntervalSeconds(Long retryIntervalSeconds) {
    this.failureRetryIntervalSeconds = retryIntervalSeconds;
  }

  long getFailureRetryIntervalSeconds() {
    return Optional.ofNullable(failureRetryIntervalSeconds).orElse(DEFAULT_RETRY_INTERVAL_SECONDS);
  }

  void setFailureRetryLimitMinutes(Long retryLimitMinutes) {
    this.failureRetryLimitMinutes = retryLimitMinutes;
  }

  long getFailureRetryLimitMinutes() {
    return Optional.ofNullable(failureRetryLimitMinutes).orElse(DEFAULT_RETRY_LIMIT_MINUTES);
  }

  public boolean hasMiiOpssConfigured() {
    return getModelOpssWalletPasswordSecret() != null || getModelOpssWalletFileSecret() != null;
  }
  
  public boolean isModelConfigured() {
    return Optional.ofNullable(configuration).map(Configuration::getModel).orElse(null) != null;
  }

  class CommonEffectiveConfigurationFactory implements EffectiveConfigurationFactory {
    @Override
    public EffectiveIntrospectorJobPodSpec getIntrospectorJobPodSpec() {
      return Optional.ofNullable(introspector)
          .map(i -> new EffectiveIntrospectorJobPodSpecCommonImpl(DomainSpec.this, i))
          .orElse(null);
    }

    @Override
    public EffectiveAdminServerSpec getAdminServerSpec() {
      return new EffectiveAdminServerSpecCommonImpl(DomainSpec.this, adminServer);
    }

    @Override
    public EffectiveServerSpec getServerSpec(
        String serverName, String clusterName, ClusterSpec clusterSpec) {
      return new EffectiveManagedServerSpecCommonImpl(
          DomainSpec.this,
          getManagedServer(serverName),
          clusterSpec,
          getClusterLimit(clusterName, clusterSpec));
    }

    private boolean hasMaxUnavailable(ClusterSpec clusterSpec) {
      return clusterSpec != null && clusterSpec.getMaxUnavailable() != null;
    }

    private boolean hasMaxConcurrentStartup(ClusterSpec clusterSpec) {
      return clusterSpec != null && clusterSpec.getMaxConcurrentStartup() != null;
    }

    private int getMaxConcurrentShutdownFor(ClusterSpec clusterSpec) {
      return Optional.ofNullable(clusterSpec).map(ClusterSpec::getMaxConcurrentShutdown)
          .orElse(getMaxClusterConcurrentShutdown());
    }

    private int getMaxConcurrentStartupFor(ClusterSpec clusterSpec) {
      return hasMaxConcurrentStartup(clusterSpec)
          ? clusterSpec.getMaxConcurrentStartup()
          : getMaxClusterConcurrentStartup();
    }

    private int getMaxUnavailableFor(ClusterSpec clusterSpec) {
      return hasMaxUnavailable(clusterSpec) ? clusterSpec.getMaxUnavailable() : getMaxClusterUnavailable();
    }

    private int getReplicaCountFor(ClusterSpec clusterSpec) {
      return Optional.ofNullable(clusterSpec).map(ClusterSpec::getReplicas).orElse(getReplicas());
    }

    @Override
    public EffectiveClusterSpec getClusterSpec(ClusterSpec clusterSpec) {
      return new EffectiveClusterSpecCommonImpl(DomainSpec.this, clusterSpec);
    }

    private Integer getClusterLimit(String clusterName, ClusterSpec clusterSpec) {
      return clusterName == null ? null : getReplicaCount(clusterSpec);
    }

    @Override
    public boolean isShuttingDown() {
      return getAdminServerSpec().isShuttingDown();
    }

    @Override
    public int getReplicaCount(ClusterSpec clusterSpec) {
      return getReplicaCountFor(clusterSpec);
    }

    @Override
    public void setReplicaCount(String clusterName, ClusterSpec clusterSpec, int replicaCount) {
      Optional.ofNullable(clusterSpec)
              .ifPresentOrElse(cs -> cs.setReplicas(replicaCount), () -> setReplicas(replicaCount));
    }

    @Override
    public int getMaxUnavailable(ClusterSpec clusterSpec) {
      return getMaxUnavailableFor(clusterSpec);
    }

    @Override
    public List<String> getAdminServerChannelNames() {
      return adminServer != null ? adminServer.getChannelNames() : Collections.emptyList();
    }

    @Override
    public int getMaxConcurrentStartup(ClusterSpec clusterSpec) {
      return getMaxConcurrentStartupFor(clusterSpec);
    }

    @Override
    public int getMaxConcurrentShutdown(ClusterSpec clusterSpec) {
      return getMaxConcurrentShutdownFor(clusterSpec);
    }
  }
}

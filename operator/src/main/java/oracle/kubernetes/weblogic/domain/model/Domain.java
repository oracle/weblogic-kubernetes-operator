// Copyright (c) 2017, 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.weblogic.domain.model;

import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import javax.annotation.Nonnull;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;
import io.kubernetes.client.common.KubernetesObject;
import io.kubernetes.client.openapi.models.V1Container;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1SecretReference;
import jakarta.validation.Valid;
import oracle.kubernetes.json.Description;
import oracle.kubernetes.operator.DomainSourceType;
import oracle.kubernetes.operator.LogHomeLayoutType;
import oracle.kubernetes.operator.MIINonDynamicChangesMethod;
import oracle.kubernetes.operator.ModelInImageDomainType;
import oracle.kubernetes.operator.OverrideDistributionStrategy;
import oracle.kubernetes.operator.tuning.TuningParameters;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;

import static oracle.kubernetes.utils.OperatorUtils.emptyToNull;

/**
 * Domain represents a WebLogic domain and how it will be realized in the Kubernetes cluster.
 */
public class Domain implements KubernetesObject {
  /**
   * The starting marker of a token that needs to be substituted with a matching env var.
   */
  public static final String TOKEN_START_MARKER = "$(";

  /**
   * The ending marker of a token that needs to be substituted with a matching env var.
   */
  public static final String TOKEN_END_MARKER = ")";

  /**
   * The pattern for computing the default shared logs directory.
   */
  private static final String LOG_HOME_DEFAULT_PATTERN = "/shared/logs/%s";

  /**
   * APIVersion defines the versioned schema of this representation of an object. Servers should
   * convert recognized schemas to the latest internal value, and may reject unrecognized values.
   * More info: https://git.k8s.io/community/contributors/devel/api-conventions.md#resources
   */
  @SerializedName("apiVersion")
  @Expose
  @Description("The API version defines the versioned schema of this Domain. Required.")
  private String apiVersion;

  /**
   * Kind is a string value representing the REST resource this object represents. Servers may infer
   * this from the endpoint the client submits requests to. Cannot be updated. In CamelCase. More
   * info: https://git.k8s.io/community/contributors/devel/api-conventions.md#types-kinds
   */
  @SerializedName("kind")
  @Expose
  @Description("The type of the REST resource. Must be \"Domain\". Required.")
  private String kind;

  /**
   * Standard object's metadata. More info:
   * https://git.k8s.io/community/contributors/devel/api-conventions.md#metadata
   */
  @SerializedName("metadata")
  @Expose
  @Valid
  @Description("The resource metadata. Must include the `name` and `namespace`. Required.")
  @Nonnull
  private V1ObjectMeta metadata = new V1ObjectMeta();

  /**
   * DomainSpec is a description of a domain.
   */
  @SerializedName("spec")
  @Expose
  @Valid
  @Description("The specification of the operation of the WebLogic domain. Required.")
  @Nonnull
  private DomainSpec spec = new DomainSpec();

  /**
   * DomainStatus represents information about the status of a domain. Status may trail the actual
   * state of a system.
   */
  @SerializedName("status")
  @Expose
  @Valid
  @Description("The current status of the operation of the WebLogic domain. Updated automatically by the operator.")
  private DomainStatus status;

  @SuppressWarnings({"rawtypes"})
  static List sortOrNull(List list) {
    return sortOrNull(list, null);
  }

  @SuppressWarnings({"rawtypes", "unchecked"})
  static List sortOrNull(List list, Comparator c) {
    if (list != null) {
      Object[] a = list.toArray(new Object[0]);
      Arrays.sort(a, c);
      return Arrays.asList(a);
    }
    return null;
  }

  /**
   * check if the external service is configured for the admin server.
   *
   *
   * @return true if the external service is configured
   */
  public boolean isExternalServiceConfigured() {
    return !Optional.ofNullable(getSpec().getAdminServer())
          .map(AdminServer::getAdminService)
          .map(AdminService::getChannels)
          .orElse(Collections.emptyList()).isEmpty();
  }

  /**
   * APIVersion defines the versioned schema of this representation of an object. Servers should
   * convert recognized schemas to the latest internal value, and may reject unrecognized values.
   * More info: https://git.k8s.io/community/contributors/devel/api-conventions.md#resources
   *
   * @return API version
   */
  public String getApiVersion() {
    return apiVersion;
  }

  /**
   * APIVersion defines the versioned schema of this representation of an object. Servers should
   * convert recognized schemas to the latest internal value, and may reject unrecognized values.
   * More info: https://git.k8s.io/community/contributors/devel/api-conventions.md#resources
   *
   * @param apiVersion API version
   */
  public void setApiVersion(String apiVersion) {
    this.apiVersion = apiVersion;
  }

  /**
   * APIVersion defines the versioned schema of this representation of an object. Servers should
   * convert recognized schemas to the latest internal value, and may reject unrecognized values.
   * More info: https://git.k8s.io/community/contributors/devel/api-conventions.md#resources
   *
   * @param apiVersion API version
   * @return this
   */
  public Domain withApiVersion(String apiVersion) {
    this.apiVersion = apiVersion;
    return this;
  }

  /**
   * Kind is a string value representing the REST resource this object represents. Servers may infer
   * this from the endpoint the client submits requests to. Cannot be updated. In CamelCase. More
   * info: https://git.k8s.io/community/contributors/devel/api-conventions.md#types-kinds
   *
   * @return kind
   */
  public String getKind() {
    return kind;
  }

  /**
   * Kind is a string value representing the REST resource this object represents. Servers may infer
   * this from the endpoint the client submits requests to. Cannot be updated. In CamelCase. More
   * info: https://git.k8s.io/community/contributors/devel/api-conventions.md#types-kinds
   *
   * @param kind Kind
   */
  public void setKind(String kind) {
    this.kind = kind;
  }

  /**
   * Kind is a string value representing the REST resource this object represents. Servers may infer
   * this from the endpoint the client submits requests to. Cannot be updated. In CamelCase. More
   * info: https://git.k8s.io/community/contributors/devel/api-conventions.md#types-kinds
   *
   * @param kind Kind
   * @return this
   */
  public Domain withKind(String kind) {
    this.kind = kind;
    return this;
  }

  /**
   * Standard object's metadata. More info:
   * https://git.k8s.io/community/contributors/devel/api-conventions.md#metadata
   *
   * @return Metadata
   */
  public @Nonnull V1ObjectMeta getMetadata() {
    return metadata;
  }

  /**
   * Standard object's metadata. More info:
   * https://git.k8s.io/community/contributors/devel/api-conventions.md#metadata
   *
   * @param metadata Metadata
   */
  public void setMetadata(@Nonnull V1ObjectMeta metadata) {
    this.metadata = metadata;
  }

  /**
   * Standard object's metadata. More info:
   * https://git.k8s.io/community/contributors/devel/api-conventions.md#metadata
   *
   * @param metadata Metadata
   * @return this
   */
  public Domain withMetadata(V1ObjectMeta metadata) {
    this.metadata = metadata;
    return this;
  }

  public String getNamespace() {
    return metadata.getNamespace();
  }

  public AdminServerSpec getAdminServerSpec() {
    return new AdminServerSpecCommonImpl(getSpec(), getAdminServer());
  }

  public AdminServer getAdminServer() {
    return spec.getAdminServer();
  }

  public List<String> getAdminServerChannelNames() {
    return spec.getAdminServer() != null
        ? spec.getAdminServer().getChannelNames() : Collections.emptyList();
  }

  public String getRestartVersion() {
    return spec.getRestartVersion();
  }

  public String getIntrospectVersion() {
    return spec.getIntrospectVersion();
  }

  public MonitoringExporterConfiguration getMonitoringExporterConfiguration() {
    return spec.getMonitoringExporterConfiguration();
  }

  public MonitoringExporterSpecification getMonitoringExporterSpecification() {
    return spec.getMonitoringExporterSpecification();
  }

  public String getMonitoringExporterImage() {
    return spec.getMonitoringExporterImage();
  }

  public V1Container.ImagePullPolicyEnum getMonitoringExporterImagePullPolicy() {
    return spec.getMonitoringExporterImagePullPolicy();
  }

  public ManagedServer getManagedServer(String serverName) {
    return getSpec().getManagedServer(serverName);
  }

  public int getReplicaCount(String clusterName) {
    return getReplicaCountFor(getSpec().getCluster(clusterName));
  }

  /**
   * Get replica count for a specific cluster.
   * @param cluster Cluster specification.
   * @return replica count.
   */
  public int getReplicaCountFor(ClusterSpec cluster) {
    return hasReplicaCount(cluster)
        ? cluster.getReplicas()
        : Optional.ofNullable(getSpec().getReplicas()).orElse(0);
  }

  private boolean hasReplicaCount(ClusterSpec cluster) {
    return cluster != null && cluster.getReplicas() != null;
  }


  public FluentdSpecification getFluentdSpecification() {
    return spec.getFluentdSpecification();
  }

  /**
   * Return the MII domain.spec.configuration.model.onlineUpdate.nonDynamicChangesMethod
   * @return {@link MIINonDynamicChangesMethod}
   */
  public MIINonDynamicChangesMethod getMiiNonDynamicChangesMethod() {
    return Optional.of(getSpec())
        .map(DomainSpec::getConfiguration)
        .map(Configuration::getModel)
        .map(Model::getOnlineUpdate)
        .map(OnlineUpdate::getOnNonDynamicChanges)
        .orElse(MIINonDynamicChangesMethod.COMMIT_UPDATE_ONLY);
  }

  /**
   * DomainSpec is a description of a domain.
   *
   * @return Specification
   */
  public @Nonnull DomainSpec getSpec() {
    return spec;
  }

  /**
   * DomainSpec is a description of a domain.
   *
   * @param spec Specification
   */
  public void setSpec(@Nonnull DomainSpec spec) {
    this.spec = spec;
  }

  /**
   * DomainSpec is a description of a domain.
   *
   * @param spec Specification
   * @return this
   */
  public Domain withSpec(DomainSpec spec) {
    this.spec = spec;
    return this;
  }

  /**
   * DomainStatus represents information about the status of a domain. Status may trail the actual
   * state of a system.
   *
   * @return Status
   */
  public DomainStatus getStatus() {
    return status;
  }

  /**
   * DomainStatus represents information about the status of a domain. Status may trail the actual
   * state of a system.
   *
   * @return Status
   */
  public DomainStatus getOrCreateStatus() {
    if (status == null) {
      setStatus(new DomainStatus());
    }
    return status;
  }

  /**
   * DomainStatus represents information about the status of a domain. Status may trail the actual
   * state of a system.
   *
   * @param status Status
   */
  public void setStatus(DomainStatus status) {
    this.status = status;
  }

  /**
   * DomainStatus represents information about the status of a domain. Status may trail the actual
   * state of a system.
   *
   * @param status Status
   * @return this instance
   */
  public Domain withStatus(DomainStatus status) {
    setStatus(status);
    return this;
  }

  /**
   * Name of the secret containing WebLogic startup credentials user name and password.
   *
   * @return the secret name
   */
  public String getWebLogicCredentialsSecretName() {
    return Optional.ofNullable(spec.getWebLogicCredentialsSecret()).map(V1SecretReference::getName).orElse(null);
  }

  /**
   * Reference to secret opss key passphrase.
   *
   * @return opss key passphrase
   */
  public String getOpssWalletPasswordSecret() {
    return spec.getOpssWalletPasswordSecret();
  }

  /**
   * Returns the opss wallet file secret.
   *
   * @return opss wallet file secret.
   */
  public String getOpssWalletFileSecret() {
    return spec.getOpssWalletFileSecret();
  }

  /**
   * Reference to runtime encryption secret.
   *
   * @return runtime encryption secret
   */
  public String getRuntimeEncryptionSecret() {
    return spec.getRuntimeEncryptionSecret();
  }

  /**
   * Returns the domain unique identifier.
   *
   * @return domain UID
   */
  public String getDomainUid() {
    return Optional.ofNullable(spec.getDomainUid()).orElse(getMetadata().getName());
  }

  /**
   * Returns the path to the log home to be used by this domain. Null if the log home is disabled.
   *
   * @return a path on a persistent volume, or null
   */
  public String getEffectiveLogHome() {
    return isLogHomeEnabled() ? getLogHome() : null;
  }

  public String getLogHome() {
    return Optional.ofNullable(spec.getLogHome())
        .orElse(String.format(LOG_HOME_DEFAULT_PATTERN, getDomainUid()));
  }

  public LogHomeLayoutType getLogHomeLayout() {
    return spec.getLogHomeLayout();
  }

  public boolean isLogHomeEnabled() {
    return Optional.ofNullable(spec.isLogHomeEnabled()).orElse(getDomainHomeSourceType().hasLogHomeByDefault());
  }

  public String getDataHome() {
    return spec.getDataHome();
  }

  public ModelInImageDomainType getWdtDomainType() {
    return spec.getWdtDomainType();
  }

  public boolean isIncludeServerOutInPodLog() {
    return spec.getIncludeServerOutInPodLog();
  }

  /**
   * Returns a description of how the domain is defined.
   * @return source type
   */
  public DomainSourceType getDomainHomeSourceType() {
    return spec.getDomainHomeSourceType();
  }

  public boolean isNewIntrospectionRequiredForNewServers() {
    return isDomainSourceTypeFromModel();
  }

  private boolean isDomainSourceTypeFromModel() {
    return getDomainHomeSourceType() == DomainSourceType.FROM_MODEL;
  }

  public boolean isHttpAccessLogInLogHome() {
    return spec.getHttpAccessLogInLogHome();
  }

  /**
   * Returns if the domain is using online update.
   * return true if using online update
   */

  public boolean isUseOnlineUpdate() {
    return spec.isUseOnlineUpdate();
  }

  /**
   * Returns WDT activate changes timeout.
   * @return WDT activate timeout
   */
  public Long getWDTActivateTimeoutMillis() {
    return getWDTOnlineUpdateTimeouts()
        .map(WDTTimeouts::getActivateTimeoutMillis)
        .orElse(180000L);
  }

  /**
   * Returns WDT connect timeout.
   * @return WDT connect timeout
   */
  public Long getWDTConnectTimeoutMillis() {
    return getWDTOnlineUpdateTimeouts()
        .map(WDTTimeouts::getConnectTimeoutMillis)
        .orElse(120000L);
  }

  /**
   * Returns WDT deploy application timeout.
   * @return WDT deploy timeout
   */
  public Long getWDTDeployTimeoutMillis() {
    return getWDTOnlineUpdateTimeouts()
        .map(WDTTimeouts::getDeployTimeoutMillis)
        .orElse(180000L);
  }

  /**
   * Returns WDT undeploy application timeout.
   * @return WDT undeploy timeout
   */
  public Long getWDTUnDeployTimeoutMillis() {
    return getWDTOnlineUpdateTimeouts()
        .map(WDTTimeouts::getUndeployTimeoutMillis)
        .orElse(180000L);
  }

  /**
   * Returns WDT redeploy application timeout.
   * @return WDT redeploy timeout
   */
  public Long getWDTReDeployTimeoutMillis() {
    return getWDTOnlineUpdateTimeouts()
        .map(WDTTimeouts::getRedeployTimeoutMillis)
        .orElse(180000L);
  }

  /**
   * Returns WDT start application timeout.
   * @return WDT start application timeout
   */
  public Long getWDTStartApplicationTimeoutMillis() {
    return getWDTOnlineUpdateTimeouts()
        .map(WDTTimeouts::getStartApplicationTimeoutMillis)
        .orElse(180000L);
  }

  /**
   * Returns WDT stop application timeout.
   * @return WDT stop application timeout
   */
  public Long getWDTStopApplicationTimeoutMillis() {
    return getWDTOnlineUpdateTimeouts()
        .map(WDTTimeouts::getStopApplicationTimeoutMillis)
        .orElse(180000L);
  }

  /**
   * Returns WDT set server groups timeout when setting JRF domain server group targeting.
   * @return WDT set server groups timeout
   */
  public Long getWDTSetServerGroupsTimeoutMillis() {
    return getWDTOnlineUpdateTimeouts()
        .map(WDTTimeouts::getSetServerGroupsTimeoutMillis)
        .orElse(180000L);
  }

  private Optional<WDTTimeouts> getWDTOnlineUpdateTimeouts() {
    return Optional.of(spec)
        .map(DomainSpec::getConfiguration)
        .map(Configuration::getModel)
        .map(Model::getOnlineUpdate)
        .map(OnlineUpdate::getWdtTimeouts);
  }

  public boolean isIstioEnabled() {
    return spec.isIstioEnabled();
  }

  /**
   * check if the admin channel port forwarding is enabled for the admin server.
   *
   * @param domainSpec Domain spec
   * @return true if the admin channel port forwarding is enabled
   */
  public static boolean isAdminChannelPortForwardingEnabled(DomainSpec domainSpec) {
    return Optional.ofNullable(domainSpec.getAdminServer())
            .map(AdminServer::isAdminChannelPortForwardingEnabled).orElse(true);
  }

  public int getIstioReadinessPort() {
    return spec.getIstioReadinessPort();
  }

  public int getIstioReplicationPort() {
    return spec.getIstioReplicationPort();
  }

  /**
   * For Istio version prior to 1.10, proxy redirects traffic to localhost and thus requires
   * localhostBindingsEnabled configuration to be true.  Istio 1.10 and later redirects traffic
   * to server pods' IP interface and thus localhostBindingsEnabled configuration should be false.
   * @return true if if the proxy redirects traffic to localhost, false otherwise.
   */
  public boolean isLocalhostBindingsEnabled() {
    Boolean isLocalHostBindingsEnabled = spec.isLocalhostBindingsEnabled();
    if (isLocalHostBindingsEnabled != null) {
      return isLocalHostBindingsEnabled;
    }

    String istioLocalhostBindingsEnabled = TuningParameters.getInstance().get("istioLocalhostBindingsEnabled");
    if (istioLocalhostBindingsEnabled != null) {
      return Boolean.parseBoolean(istioLocalhostBindingsEnabled);
    }

    return true;
  }

  /**
   * Returns the domain home. May be null, but will not be an empty string.
   *
   * @return domain home
   */
  public String getDomainHome() {
    return emptyToNull(spec.getDomainHome());
  }

  /**
   * Returns full path of the liveness probe custom script for the domain. May be null, but will not be an empty string.
   *
   * @return Full path of the liveness probe custom script
   */
  public String getLivenessProbeCustomScript() {
    return emptyToNull(spec.getLivenessProbeCustomScript());
  }

  //public boolean isShuttingDown() {
  //  return getEffectiveConfigurationFactory().isShuttingDown();
  //}

  ///**
  // * Return the names of the exported admin NAPs.
  // *
  // * @return a list of names; may be empty
  // */
  //List<String> getAdminServerChannelNames() {
  //  return getEffectiveConfigurationFactory().getAdminServerChannelNames();
  //}

  /**
   * Returns the name of the Kubernetes config map that contains optional configuration overrides.
   *
   * @return name of the config map
   */
  public String getConfigOverrides() {
    return spec.getConfigOverrides();
  }

  /**
   * Returns the strategy for applying changes to configuration overrides.
   * @return the selected strategy
   */
  public OverrideDistributionStrategy getOverrideDistributionStrategy() {
    return spec.getOverrideDistributionStrategy();
  }

  /**
   * Returns the strategy for applying changes to configuration overrides.
   * @return the selected strategy
   */
  public boolean distributeOverridesDynamically() {
    return spec.getOverrideDistributionStrategy() == OverrideDistributionStrategy.DYNAMIC;
  }

  /**
   * Returns the value of the introspector job active deadline.
   *
   * @return value of the deadline in seconds.
   */
  public Long getIntrospectorJobActiveDeadlineSeconds() {
    return Optional.ofNullable(spec.getConfiguration())
        .map(Configuration::getIntrospectorJobActiveDeadlineSeconds).orElse(null);
  }

  public String getWdtConfigMap() {
    return spec.getWdtConfigMap();
  }

  /**
   * Returns a list of Kubernetes secret names used in optional configuration overrides.
   *
   * @return list of Kubernetes secret names
   */
  public List<String> getConfigOverrideSecrets() {
    return spec.getConfigOverrideSecrets();
  }

  /**
   * Returns the model home directory of the domain.
   *
   * @return model home directory
   */
  public String getModelHome() {
    return spec.getModelHome();
  }

  /**
   * Returns the WDT install home directory of the domain.
   *
   * @return WDT install home directory
   */
  public String getWdtInstallHome() {
    return spec.getWdtInstallHome();
  }

  /**
   * Returns the auxiliary images configured for the domain.
   */
  public List<AuxiliaryImage> getAuxiliaryImages() {
    return spec.getAuxiliaryImages();
  }

  /**
   * Returns the auxiliary image volume mount path.
   */
  public String getAuxiliaryImageVolumeMountPath() {
    return spec.getAuxiliaryImageVolumeMountPath();
  }

  /**
   * Returns the auxiliary image volume withAuxiliaryImageVolumeMedium.
   */
  public String getAuxiliaryImageVolumeMedium() {
    return spec.getAuxiliaryImageVolumeMedium();
  }

  /**
   * Returns the auxiliary image volume size limit.
   */
  public String getAuxiliaryImageVolumeSizeLimit() {
    return spec.getAuxiliaryImageVolumeSizeLimit();
  }

  /**
   * Returns the interval in seconds at which Severe failures will be retried.
   */
  public long getFailureRetryIntervalSeconds() {
    return spec.getFailureRetryIntervalSeconds();
  }

  /**
   * Returns the time in minutes after the first severe failure when the operator will stop retrying Severe failures.
   */
  public long getFailureRetryLimitMinutes() {
    return spec.getFailureRetryLimitMinutes();
  }

  /**
   * Returns true if the operator should retry a failed make-right on this domain.
   */
  public boolean shouldRetry() {
    return getNextRetryTime() != null;
  }

  /**
   * Return the next time a retry should be done.
   */
  public OffsetDateTime getNextRetryTime() {
    return Optional.ofNullable(getStatus())
          .map(DomainStatus::getLastFailureTime)
          .map(this::addRetryInterval)
          .orElse(null);
  }

  // Adds the domain retry interval to the specified time.
  private OffsetDateTime addRetryInterval(@Nonnull OffsetDateTime startTime) {
    return startTime.plus(getFailureRetryIntervalSeconds(), ChronoUnit.SECONDS);
  }

  @Override
  public String toString() {
    return new ToStringBuilder(this)
        .append("apiVersion", apiVersion)
        .append("kind", kind)
        .append("metadata", metadata)
        .append("spec", spec)
        .append("status", status)
        .toString();
  }

  @Override
  public int hashCode() {
    return new HashCodeBuilder()
        .append(metadata)
        .append(apiVersion)
        .append(kind)
        .append(spec)
        .append(status)
        .toHashCode();
  }

  @Override
  public boolean equals(Object other) {
    if (other == this) {
      return true;
    }
    if (!(other instanceof Domain)) {
      return false;
    }
    Domain rhs = ((Domain) other);
    return new EqualsBuilder()
        .append(metadata, rhs.metadata)
        .append(apiVersion, rhs.apiVersion)
        .append(kind, rhs.kind)
        .append(spec, rhs.spec)
        .append(status, rhs.status)
        .isEquals();
  }
}

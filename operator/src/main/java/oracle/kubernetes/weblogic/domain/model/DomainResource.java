// Copyright (c) 2017, 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.weblogic.domain.model;

import java.io.File;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;
import io.kubernetes.client.common.KubernetesObject;
import io.kubernetes.client.openapi.models.V1ContainerPort;
import io.kubernetes.client.openapi.models.V1EnvVar;
import io.kubernetes.client.openapi.models.V1LocalObjectReference;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1PodSpec;
import io.kubernetes.client.openapi.models.V1ResourceRequirements;
import io.kubernetes.client.openapi.models.V1VolumeMount;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import oracle.kubernetes.json.Description;
import oracle.kubernetes.operator.DomainSourceType;
import oracle.kubernetes.operator.KubernetesConstants;
import oracle.kubernetes.operator.LogHomeLayoutType;
import oracle.kubernetes.operator.MIINonDynamicChangesMethod;
import oracle.kubernetes.operator.ModelInImageDomainType;
import oracle.kubernetes.operator.OverrideDistributionStrategy;
import oracle.kubernetes.operator.helpers.LegalNames;
import oracle.kubernetes.operator.helpers.SecretType;
import oracle.kubernetes.operator.processing.EffectiveAdminServerSpec;
import oracle.kubernetes.operator.processing.EffectiveClusterSpec;
import oracle.kubernetes.operator.processing.EffectiveServerSpec;
import oracle.kubernetes.weblogic.domain.EffectiveConfigurationFactory;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;

import static java.util.stream.Collectors.toSet;
import static oracle.kubernetes.common.logging.MessageKeys.MAKE_RIGHT_WILL_RETRY;
import static oracle.kubernetes.operator.helpers.LegalNames.LEGAL_DNS_LABEL_NAME_MAX_LENGTH;
import static oracle.kubernetes.utils.OperatorUtils.emptyToNull;
import static oracle.kubernetes.weblogic.domain.model.DomainValidationMessages.clusterInUse;
import static oracle.kubernetes.weblogic.domain.model.DomainValidationMessages.missingClusterResource;
import static oracle.kubernetes.weblogic.domain.model.Model.DEFAULT_AUXILIARY_IMAGE_MOUNT_PATH;

/**
 * Domain represents a WebLogic domain and how it will be realized in the Kubernetes cluster.
 */
@Description(
    "A Domain resource describes the configuration, logging, images, and lifecycle "
    + "of a WebLogic domain, including Java "
    + "options, environment variables, additional Pod content, and the ability to "
    + "explicitly start, stop, or restart its members. The Domain resource "
    + "references its Cluster resources using `.spec.clusters`."
)
public class DomainResource implements KubernetesObject, RetryMessageFactory {

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
  private String apiVersion = KubernetesConstants.API_VERSION_WEBLOGIC_ORACLE;

  /**
   * Kind is a string value representing the REST resource this object represents. Servers may infer
   * this from the endpoint the client submits requests to. Cannot be updated. In CamelCase. More
   * info: https://git.k8s.io/community/contributors/devel/api-conventions.md#types-kinds
   */
  @SerializedName("kind")
  @Expose
  @Description("The type of the REST resource. Must be \"Domain\". Required.")
  private String kind = KubernetesConstants.DOMAIN;

  /**
   * Standard object's metadata. More info:
   * https://git.k8s.io/community/contributors/devel/api-conventions.md#metadata
   */
  @SuppressWarnings("common-java:DuplicatedBlocks")
  @SerializedName("metadata")
  @Expose
  @Valid
  @Description("The resource metadata. Must include the `name` and `namespace`. Required.")
  @NotNull
  private V1ObjectMeta metadata = new V1ObjectMeta();

  /**
   * DomainSpec is a description of a domain.
   */
  @SerializedName("spec")
  @Expose
  @Valid
  @Description("The specification of the operation of the WebLogic domain. Required.")
  @NotNull
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
  static List sortList(List list) {
    return sortList(list, null);
  }

  @SuppressWarnings({"rawtypes", "unchecked"})
  static List sortList(List list, Comparator c) {
    if (list != null) {
      Object[] a = list.toArray(new Object[0]);
      Arrays.sort(a, c);
      return Arrays.asList(a);
    }
    return List.of();
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
  public DomainResource withApiVersion(String apiVersion) {
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
  public DomainResource withKind(String kind) {
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
  public DomainResource withMetadata(V1ObjectMeta metadata) {
    this.metadata = metadata;
    return this;
  }

  public String getNamespace() {
    return metadata.getNamespace();
  }

  public EffectiveAdminServerSpec getAdminServerSpec() {
    return getEffectiveConfigurationFactory().getAdminServerSpec();
  }

  public String getRestartVersion() {
    return spec.getRestartVersion();
  }

  public String getIntrospectVersion() {
    return spec.getIntrospectVersion();
  }

  public EffectiveConfigurationFactory getEffectiveConfigurationFactory() {
    return spec.getEffectiveConfigurationFactory(apiVersion);
  }

  public MonitoringExporterConfiguration getMonitoringExporterConfiguration() {
    return spec.getMonitoringExporterConfiguration();
  }

  public MonitoringExporterSpecification getMonitoringExporterSpecification() {
    return spec.getMonitoringExporterSpecification();
  }

  public V1ResourceRequirements getMonitoringExporterResources() {
    return spec.getMonitoringExporterResourceRequirements();
  }

  public String getMonitoringExporterImage() {
    return spec.getMonitoringExporterImage();
  }

  public String getMonitoringExporterImagePullPolicy() {
    return spec.getMonitoringExporterImagePullPolicy();
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
  public DomainResource withSpec(DomainSpec spec) {
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
  public DomainResource withStatus(DomainStatus status) {
    setStatus(status);
    return this;
  }

  /**
   * Name of the secret containing WebLogic startup credentials user name and password.
   *
   * @return the secret name
   */
  public String getWebLogicCredentialsSecretName() {
    return Optional.ofNullable(spec.getWebLogicCredentialsSecret()).map(V1LocalObjectReference::getName).orElse(null);
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

  String getLogHome() {
    return Optional.ofNullable(spec.getLogHome())
        .orElse(String.format(LOG_HOME_DEFAULT_PATTERN, getDomainUid()));
  }

  public LogHomeLayoutType getLogHomeLayout() {
    return spec.getLogHomeLayout();
  }

  boolean isLogHomeEnabled() {
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


  /**
   * Returns the domain home. May be null, but will not be an empty string.
   *
   * @return domain home
   */
  public String getDomainHome() {
    return emptyToNull(Optional.ofNullable(spec.getDomainHome())
            .orElse(getDomainHomeSourceType().getDefaultDomainHome(getDomainUid())));
  }

  /**
   * Returns full path of the liveness probe custom script for the domain. May be null, but will not be an empty string.
   *
   * @return Full path of the liveness probe custom script
   */
  public String getLivenessProbeCustomScript() {
    return emptyToNull(spec.getLivenessProbeCustomScript());
  }

  public boolean isShuttingDown() {
    return getEffectiveConfigurationFactory().isShuttingDown();
  }

  /**
   * Return the names of the exported admin NAPs.
   *
   * @return a list of names; may be empty
   */
  public List<String> getAdminServerChannelNames() {
    return getEffectiveConfigurationFactory().getAdminServerChannelNames();
  }

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
   * Returns the cluster names that the domain references to.
   * @return the list of cluster names
   */
  public List<V1LocalObjectReference> getClusters() {
    return spec.getClusters();
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
   * Returns the next time a retry should be done. If the domain resource has no status, or there is no
   * retry needed, returns null.
   */
  @Nullable
  public OffsetDateTime getNextRetryTime() {
    return Optional.ofNullable(getStatus())
          .map(this::getNextRetryTime)
          .orElse(null);
  }

  @Nullable
  private OffsetDateTime getNextRetryTime(@Nonnull DomainStatus domainStatus) {
    return Optional.of(domainStatus)
          .map(DomainStatus::getLastFailureTime)
          .map(this::addRetryInterval)
          .orElse(null);
  }

  // Adds the domain retry interval to the specified time.
  private OffsetDateTime addRetryInterval(@Nonnull OffsetDateTime startTime) {
    return startTime.plus(getFailureRetryIntervalSeconds(), ChronoUnit.SECONDS);
  }

  @Nullable
  private OffsetDateTime getLastRetryTime(@Nonnull DomainStatus domainStatus) {
    return Optional.of(domainStatus)
        .map(DomainStatus::getInitialFailureTime)
        .map(this::addRetryLimit)
        .orElse(null);
  }

  private OffsetDateTime addRetryLimit(@Nonnull OffsetDateTime startTime) {
    return startTime.plus(getFailureRetryLimitMinutes(), ChronoUnit.MINUTES);
  }

  private boolean doesReferenceCluster(@Nonnull String clusterName) {
    return Optional.of(getSpec())
        .map(DomainSpec::getClusters).orElse(new ArrayList<>())
        .stream().map(V1LocalObjectReference::getName).anyMatch(clusterName::equals);
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
    if (!(other instanceof DomainResource)) {
      return false;
    }
    DomainResource rhs = ((DomainResource) other);
    return new EqualsBuilder()
        .append(metadata, rhs.metadata)
        .append(apiVersion, rhs.apiVersion)
        .append(kind, rhs.kind)
        .append(spec, rhs.spec)
        .append(status, rhs.status)
        .isEquals();
  }

  // used by the validating webhook
  public List<String> getFatalValidationFailures() {
    return new DomainValidator().getFatalValidationFailures();
  }

  // used by the operator
  public List<String> getValidationFailures(KubernetesResourceLookup kubernetesResources) {
    return new DomainValidator().getValidationFailures(kubernetesResources);
  }

  public List<String> getAdditionalValidationFailures(V1PodSpec podSpec) {
    return new DomainValidator().getAdditionalValidationFailures(podSpec);
  }

  public PrivateDomainApi getPrivateApi() {
    return new PrivateDomainApiImpl();
  }

  @Override
  public String createRetryMessage(DomainStatus domainStatus, DomainCondition selected) {
    return DomainStatus.LOGGER.formatMessage(MAKE_RIGHT_WILL_RETRY,
        selected.getMessage(),
        getNextRetryTime(domainStatus),
        getFailureRetryIntervalSeconds(),
        getLastRetryTime(domainStatus));
  }

  class PrivateDomainApiImpl implements PrivateDomainApi {

    @Override
    public EffectiveServerSpec getServer(
        String serverName, String clusterName, ClusterSpec clusterSpec) {
      return getEffectiveConfigurationFactory().getServerSpec(serverName, clusterName, clusterSpec);
    }

    @Override
    public EffectiveClusterSpec getCluster(ClusterSpec clusterSpec) {
      return getEffectiveConfigurationFactory().getClusterSpec(clusterSpec);
    }

    @Override
    public int getReplicaCount(ClusterSpec clusterSpec) {
      return getEffectiveConfigurationFactory().getReplicaCount(clusterSpec);
    }

    @Override
    public void setReplicaCount(String clusterName, ClusterSpec clusterSpec, int replicaLimit) {
      getEffectiveConfigurationFactory().setReplicaCount(clusterName, clusterSpec, replicaLimit);
    }

    @Override
    public int getMaxUnavailable(ClusterSpec clusterSpec) {
      return getEffectiveConfigurationFactory().getMaxUnavailable(clusterSpec);
    }

    @Override
    public int getMinAvailable(ClusterSpec clusterSpec) {
      return Math.max(getReplicaCount(clusterSpec) - getMaxUnavailable(clusterSpec), 0);
    }

    @Override
    public int getMaxConcurrentStartup(ClusterSpec clusterSpec) {
      return getEffectiveConfigurationFactory().getMaxConcurrentStartup(clusterSpec);
    }

    @Override
    public int getMaxConcurrentShutdown(ClusterSpec clusterSpec) {
      return getEffectiveConfigurationFactory().getMaxConcurrentShutdown(clusterSpec);
    }
  }

  class DomainValidator extends Validator {
    private final Set<String> clusterNames = new HashSet<>();
    private final Set<String> serverNames = new HashSet<>();

    private List<String> getValidationFailures(KubernetesResourceLookup kubernetesResources) {
      addFatalValidationFailures();
      addCrossReferenceValidationFailures(kubernetesResources);
      return failures;
    }

    private void addCrossReferenceValidationFailures(KubernetesResourceLookup kubernetesResources) {
      addMissingSecrets(kubernetesResources);
      addMissingModelConfigMap(kubernetesResources);
      addDuplicateNamesClusters(kubernetesResources);
      addInvalidMountPathsClusters(kubernetesResources);
      addReservedEnvironmentVariablesClusters(kubernetesResources);
      verifyLivenessProbeSuccessThresholdClusters(kubernetesResources);
      verifyContainerNameValidInPodSpecClusters(kubernetesResources);
      verifyContainerPortNameValidInPodSpecClusters(kubernetesResources);
      whenAuxiliaryImagesDefinedVerifyMountPathNotInUseClusters(kubernetesResources);
      verifyClusterResourcesNotInUse(kubernetesResources);
      addMissingClusterResource(kubernetesResources);
    }

    private void addFatalValidationFailures() {
      addDuplicateNamesManagedServers();
      addDuplicateNamesClusterReferences();
      addInvalidMountPathsManagedServers();
      addReservedEnvironmentVariablesManagedServers();
      verifyLivenessProbeSuccessThresholdManagedServers();
      verifyContainerNameValidInPodSpecManagedServers();
      verifyContainerPortNameValidInPodSpecManagedServers();
      whenAuxiliaryImagesDefinedVerifyMountPathNotInUseManagedServers();
      addUnmappedLogHome();
      addIllegalSitConfigForMii();
      verifyIntrospectorJobName();
      verifyModelHomeNotInWDTInstallHome();
      verifyWDTInstallHomeNotInModelHome();
      whenAuxiliaryImagesDefinedVerifyOnlyOneImageSetsSourceWDTInstallHome();
    }

    private List<String> getFatalValidationFailures() {
      addFatalValidationFailures();
      return failures;
    }

    private void verifyModelHomeNotInWDTInstallHome() {
      if (getSpec().getWdtInstallHome().contains(getSpec().getModelHome())) {
        failures.add(DomainValidationMessages
            .invalidWdtInstallHome(getSpec().getWdtInstallHome(), getSpec().getModelHome()));
      }
    }

    private void verifyWDTInstallHomeNotInModelHome() {
      if (getSpec().getModelHome().contains(getSpec().getWdtInstallHome())) {
        failures.add(DomainValidationMessages
            .invalidModelHome(getSpec().getWdtInstallHome(), getSpec().getModelHome()));
      }
    }

    private void verifyIntrospectorJobName() {
      // K8S adds a 5 character suffix to an introspector job name
      if (LegalNames.toJobIntrospectorName(getDomainUid()).length()
          > LEGAL_DNS_LABEL_NAME_MAX_LENGTH - 5) {
        failures.add(DomainValidationMessages.exceedMaxIntrospectorJobName(
            getDomainUid(),
            LegalNames.toJobIntrospectorName(getDomainUid()),
            LEGAL_DNS_LABEL_NAME_MAX_LENGTH - 5));
      }
    }

    List<String> getAdditionalValidationFailures(V1PodSpec podSpec) {
      addInvalidMountPathsForPodSpec(podSpec);
      return failures;
    }

    private void addDuplicateNamesManagedServers() {
      getSpec().getManagedServers().forEach(this::checkDuplicateServerName);
    }

    private void addDuplicateNamesClusterReferences() {
      Set<String> references = new HashSet<>();
      Optional.ofNullable(getSpec().getClusters()).orElse(new ArrayList<>())
          .stream().map(V1LocalObjectReference::getName).filter(Objects::nonNull)
          .forEach(ref -> checkDuplicateClusterReferences(references, ref));
    }

    private void checkDuplicateClusterReferences(Set<String> references, String ref) {
      if (references.contains(ref)) {
        failures.add(DomainValidationMessages.duplicateClusterName(ref));
      }
      references.add(ref);
    }

    private void addDuplicateNamesClusters(KubernetesResourceLookup kubernetesResources) {
      getSpec().getClusters()
          .stream()
          .map(kubernetesResources::findCluster)
          .filter(Objects::nonNull)
          .forEach(this::checkDuplicateClusterName);
    }

    private void checkDuplicateServerName(ManagedServer ms) {
      String serverName = getServerLegalName(ms);
      if (serverNames.contains(serverName)) {
        failures.add(DomainValidationMessages.duplicateServerName(serverName));
      } else {
        serverNames.add(serverName);
      }
    }

    private String getServerLegalName(ManagedServer ms) {
      return LegalNames.toDns1123LegalName(ms.getServerName());
    }

    private void checkDuplicateClusterName(ClusterResource cluster) {
      String clusterName = getClusterLegalName(cluster);
      if (clusterNames.contains(clusterName)) {
        failures.add(DomainValidationMessages.duplicateClusterName(clusterName));
      } else {
        clusterNames.add(clusterName);
      }
    }

    private String getClusterLegalName(ClusterResource cluster) {
      return LegalNames.toDns1123LegalName(cluster.getClusterName());
    }

    private void addInvalidMountPathsManagedServers() {
      getSpec().getAdditionalVolumeMounts().forEach(mount -> checkValidMountPath(mount, getEnvNames()));
      if (getSpec().getAdminServer() != null) {
        getSpec().getAdminServer().getAdditionalVolumeMounts()
            .forEach(mount -> checkValidMountPath(mount, getEnvNames()));
      }
    }

    private void addInvalidMountPathsClusters(KubernetesResourceLookup kubernetesResources) {
      if (getSpec().getClusters() != null) {
        getSpec().getClusters().forEach(
            cluster -> Optional.ofNullable(kubernetesResources.findCluster(cluster))
                .ifPresent(this::addClusterInvalidMountPaths));
      }
    }

    private void addInvalidMountPathsForPodSpec(V1PodSpec podSpec) {
      podSpec.getContainers()
          .forEach(container ->
              Optional.ofNullable(container.getVolumeMounts())
                  .ifPresent(volumes -> volumes.forEach(mount -> checkValidMountPath(mount, getEnvNames()))));
    }

    private void whenAuxiliaryImagesDefinedVerifyMountPathNotInUseManagedServers() {
      getAdminServerSpec().getAdditionalVolumeMounts().forEach(this::verifyMountPathForAuxiliaryImagesNotUsed);
      getSpec().getManagedServers().forEach(managedServer ->
          managedServer.getAdditionalVolumeMounts().forEach(this::verifyMountPathForAuxiliaryImagesNotUsed));
    }

    private void whenAuxiliaryImagesDefinedVerifyMountPathNotInUseClusters(
        KubernetesResourceLookup kubernetesResources) {
      getSpec().getClusters().forEach(cluster ->
          Optional.ofNullable(kubernetesResources.findCluster(cluster))
              .map(ClusterResource::getSpec).map(ClusterSpec::getAdditionalVolumeMounts)
              .ifPresent(mounts -> mounts.forEach(this::verifyMountPathForAuxiliaryImagesNotUsed)));
    }

    private void verifyMountPathForAuxiliaryImagesNotUsed(V1VolumeMount volumeMount) {
      Optional.ofNullable(getSpec().getModel()).map(Model::getAuxiliaryImages)
          .ifPresent(ai -> {
            if (volumeMount.getMountPath().equals(DEFAULT_AUXILIARY_IMAGE_MOUNT_PATH)) {
              failures.add(DomainValidationMessages.mountPathForAuxiliaryImageAlreadyInUse());
            }
          });
    }

    private void whenAuxiliaryImagesDefinedVerifyOnlyOneImageSetsSourceWDTInstallHome() {
      Optional.ofNullable(getSpec().getModel()).map(Model::getAuxiliaryImages).ifPresent(
          this::verifyWDTInstallHome);
    }

    private void verifyWDTInstallHome(List<AuxiliaryImage> auxiliaryImages) {
      if (auxiliaryImages.stream().filter(this::isWDTInstallHomeSetAndNotNone).count() > 1) {
        failures.add(DomainValidationMessages.moreThanOneAuxiliaryImageConfiguredWDTInstallHome());
      }
    }

    private boolean isWDTInstallHomeSetAndNotNone(AuxiliaryImage ai) {
      return ai.getSourceWDTInstallHome() != null && !"None".equalsIgnoreCase(ai.getSourceWDTInstallHomeOrDefault());
    }

    private void verifyLivenessProbeSuccessThresholdManagedServers() {
      Optional.of(getAdminServerSpec().getLivenessProbe())
          .ifPresent(probe -> verifySuccessThresholdValue(probe, ADMIN_SERVER_POD_SPEC_PREFIX
              + ".livenessProbe.successThreshold"));
      getSpec().getManagedServers().forEach(managedServer ->
          Optional.ofNullable(managedServer.getLivenessProbe())
              .ifPresent(probe -> verifySuccessThresholdValue(probe, MS_SPEC_PREFIX + "["
                  + managedServer.getServerName() + "].serverPod.livenessProbe.successThreshold")));
    }

    private void verifyLivenessProbeSuccessThresholdClusters(KubernetesResourceLookup kubernetesResources) {
      getSpec().getClusters()
          .forEach(cluster -> Optional.ofNullable(kubernetesResources.findCluster(cluster))
              .ifPresent(c -> {
                String s = CLUSTER_SPEC_PREFIX + "["
                    + c.getMetadata().getName() + "].serverPod.livenessProbe.successThreshold";
                verifyClusterLivenessProbeSuccessThreshold(c, s);
              }));
    }

    private void verifyContainerNameValidInPodSpecManagedServers() {
      getAdminServerSpec().getContainers().forEach(container ->
          isContainerNameReserved(container, ADMIN_SERVER_POD_SPEC_PREFIX + ".containers"));
      getSpec().getManagedServers().forEach(managedServer ->
          managedServer.getContainers().forEach(container ->
              isContainerNameReserved(container, MS_SPEC_PREFIX + "[" + managedServer.getServerName()
                  + SERVER_POD_CONTAINERS)));
    }

    private void verifyContainerNameValidInPodSpecClusters(KubernetesResourceLookup kubernetesResources) {
      getSpec().getClusters().forEach(cluster ->
          Optional.ofNullable(kubernetesResources.findCluster(cluster))
              .ifPresent(c -> verifyClusterContainerNameValid(c, CLUSTER_SPEC_PREFIX + "[" + c.getClusterName()
                  + SERVER_POD_CONTAINERS)));
    }

    private void verifyContainerPortNameValidInPodSpecManagedServers() {
      getAdminServerSpec().getContainers().forEach(container ->
          areContainerPortNamesValid(container, ADMIN_SERVER_POD_SPEC_PREFIX + ".containers"));
      getSpec().getManagedServers().forEach(managedServer ->
          managedServer.getContainers().forEach(container ->
              areContainerPortNamesValid(container, MS_SPEC_PREFIX + "[" + managedServer.getServerName()
                  + SERVER_POD_CONTAINERS)));
    }

    private void verifyContainerPortNameValidInPodSpecClusters(KubernetesResourceLookup kubernetesResources) {
      getSpec().getClusters().forEach(cluster ->
          Optional.ofNullable(kubernetesResources.findCluster(cluster))
              .ifPresent(c -> verifyClusterContainerPortNameValidInPodSpec(c, CLUSTER_SPEC_PREFIX
                  + "[" + c.getClusterName() + SERVER_POD_CONTAINERS)));
    }

    @Override
    void checkPortNameLength(V1ContainerPort port, String name, String prefix) {
      if (isPortNameTooLong(port)) {
        failures.add(DomainValidationMessages.exceedMaxContainerPortName(
            getDomainUid(),
            prefix + "." + name,
            port.getName()));
      }
    }

    private void verifyClusterResourcesNotInUse(
        KubernetesResourceLookup kubernetesResources) {
      Optional.ofNullable(getSpec().getClusters()).orElse(new ArrayList<>())
          .forEach(reference ->
              Optional.ofNullable(kubernetesResources.findClusterInNamespace(reference, getNamespace()))
                  .ifPresent(cluster -> verifyClusterNotInUseByAnotherDomain(kubernetesResources, cluster)));
    }

    private void verifyClusterNotInUseByAnotherDomain(KubernetesResourceLookup kubernetesResources,
                                                      ClusterResource cluster) {
      String domainAlreadyReferenceCluster =
          getReferencingDomains(kubernetesResources, cluster.getNamespace(), cluster.getMetadata().getName());
      if (domainAlreadyReferenceCluster != null) {
        failures.add(clusterInUse(cluster.getMetadata().getName(), domainAlreadyReferenceCluster));
      }
    }

    private String getReferencingDomains(KubernetesResourceLookup kubernetesResources,
                                         String namespace, String clusterName) {
      return Optional.ofNullable(kubernetesResources.getDomains(namespace)).orElse(new ArrayList<>()).stream()
          .filter(domain -> !domain.isShuttingDown())
          .filter(domain -> domain.doesReferenceCluster(clusterName))
          .map(DomainResource::getDomainUid)
          .filter(domainUid -> !domainUid.equals(getDomainUid())).findFirst().orElse(null);
    }

    @Nonnull
    private Set<String> getEnvNames() {
      return Optional.ofNullable(spec.getEnv()).stream()
          .flatMap(Collection::stream)
          .map(V1EnvVar::getName)
          .collect(toSet());
    }

    private void addUnmappedLogHome() {
      if (!isLogHomeEnabled()) {
        return;
      }

      if (getSpec().getAdditionalVolumeMounts().stream()
          .map(V1VolumeMount::getMountPath)
          .noneMatch(this::mapsLogHome)) {
        failures.add(DomainValidationMessages.logHomeNotMounted(getLogHome()));
      }
    }

    private boolean mapsLogHome(String mountPath) {
      return getLogHome().startsWith(separatorTerminated(mountPath));
    }

    private String separatorTerminated(String path) {
      if (path.endsWith(File.separator)) {
        return path;
      } else {
        return path + File.separator;
      }
    }

    private void addIllegalSitConfigForMii() {
      if (getDomainHomeSourceType() == DomainSourceType.FROM_MODEL
          && getConfigOverrides() != null) {
        failures.add(DomainValidationMessages.illegalSitConfigForMii(getConfigOverrides()));
      }
    }

    private void addReservedEnvironmentVariablesManagedServers() {
      checkReservedIntrospectorVariables(spec, "spec");
      Optional.ofNullable(spec.getAdminServer())
          .ifPresent(a -> checkReservedIntrospectorVariables(a, "spec.adminServer"));

      spec.getManagedServers()
          .forEach(s -> checkReservedEnvironmentVariables(s, "spec.managedServers[" + s.getServerName() + "]"));
    }

    private void addReservedEnvironmentVariablesClusters(KubernetesResourceLookup kubernetesResources) {
      spec.getClusters()
          .forEach(reference -> Optional.ofNullable(kubernetesResources.findCluster(reference))
              .ifPresent(cluster -> {
                String s = "spec.clusters[" + cluster.getClusterName() + "]";
                this.addClusterReservedEnvironmentVariables(cluster, s);
              }));
    }

    @SuppressWarnings("SameParameterValue")
    private void checkReservedIntrospectorVariables(BaseConfiguration configuration, String prefix) {
      new EnvironmentVariableCheck(IntrospectorJobEnvVars::isReserved).checkEnvironmentVariables(configuration, prefix);
    }

    private void addMissingSecrets(KubernetesResourceLookup resourceLookup) {
      verifySecretExists(resourceLookup, getWebLogicCredentialsSecretName(), SecretType.WEBLOGIC_CREDENTIALS);
      for (String secretName : getConfigOverrideSecrets()) {
        verifySecretExists(resourceLookup, secretName, SecretType.CONFIG_OVERRIDE);
      }

      verifySecretExists(resourceLookup, getOpssWalletPasswordSecret(), SecretType.OPSS_WALLET_PASSWORD);
      verifySecretExists(resourceLookup, getOpssWalletFileSecret(), SecretType.OPSS_WALLET_FILE);

      if (getDomainHomeSourceType() == DomainSourceType.FROM_MODEL) {
        if (getRuntimeEncryptionSecret() == null) {
          failures.add(DomainValidationMessages.missingRequiredSecret(
              "spec.configuration.model.runtimeEncryptionSecret"));
        } else {
          verifySecretExists(resourceLookup, getRuntimeEncryptionSecret(), SecretType.RUNTIME_ENCRYPTION);
        }
        if (ModelInImageDomainType.JRF.equals(getWdtDomainType())
            && getOpssWalletPasswordSecret() == null) {
          failures.add(DomainValidationMessages.missingRequiredOpssSecret(
              "spec.configuration.opss.walletPasswordSecret"));
        }
      }

      if (getFluentdSpecification() != null && getFluentdSpecification().getElasticSearchCredentials() == null) {
        failures.add(DomainValidationMessages.missingRequiredFluentdSecret(
            "spec.fluentdSpecification.elasticSearchCredentials"));
      }

    }

    private void addMissingClusterResource(KubernetesResourceLookup resourceLookup) {
      Optional.ofNullable(getSpec().getClusters()).orElse(new ArrayList<>())
          .forEach(cluster -> verifyClusterExists(resourceLookup, cluster));
    }

    private void verifyClusterExists(KubernetesResourceLookup resourceLookup, V1LocalObjectReference cluster) {
      if (resourceLookup.findClusterInNamespace(cluster, getNamespace()) == null) {
        failures.add(missingClusterResource(cluster.getName(), getNamespace()));
      }
    }

    @SuppressWarnings("SameParameterValue")
    private void verifySecretExists(KubernetesResourceLookup resources, String secretName, SecretType type) {
      if (secretName != null && !resources.isSecretExists(secretName, getNamespace())) {
        failures.add(DomainValidationMessages.noSuchSecret(secretName, getNamespace(), type));
      }
    }

    private void addMissingModelConfigMap(KubernetesResourceLookup resourceLookup) {
      verifyModelConfigMapExists(resourceLookup, getWdtConfigMap());
    }

    @SuppressWarnings("SameParameterValue")
    private void verifyModelConfigMapExists(KubernetesResourceLookup resources, String modelConfigMapName) {
      if (getDomainHomeSourceType() == DomainSourceType.FROM_MODEL
          && modelConfigMapName != null && !resources.isConfigMapExists(modelConfigMapName, getNamespace())) {
        failures.add(DomainValidationMessages.noSuchModelConfigMap(modelConfigMapName, getNamespace()));
      }
    }
  }
}

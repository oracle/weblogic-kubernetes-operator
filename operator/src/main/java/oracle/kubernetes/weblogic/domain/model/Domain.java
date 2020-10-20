// Copyright (c) 2017, 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.weblogic.domain.model;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;
import javax.validation.Valid;

import com.google.common.base.Strings;
import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;
import io.kubernetes.client.common.KubernetesObject;
import io.kubernetes.client.openapi.models.V1EnvVar;
import io.kubernetes.client.openapi.models.V1LocalObjectReference;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1PodSpec;
import io.kubernetes.client.openapi.models.V1SecretReference;
import io.kubernetes.client.openapi.models.V1VolumeMount;
import oracle.kubernetes.json.Description;
import oracle.kubernetes.operator.DomainSourceType;
import oracle.kubernetes.operator.ModelInImageDomainType;
import oracle.kubernetes.operator.OverrideDistributionStrategy;
import oracle.kubernetes.operator.ProcessingConstants;
import oracle.kubernetes.operator.TuningParameters;
import oracle.kubernetes.operator.helpers.LegalNames;
import oracle.kubernetes.operator.helpers.SecretType;
import oracle.kubernetes.operator.wlsconfig.WlsDomainConfig;
import oracle.kubernetes.operator.wlsconfig.WlsServerConfig;
import oracle.kubernetes.operator.work.Packet;
import oracle.kubernetes.weblogic.domain.EffectiveConfigurationFactory;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;

import static java.util.stream.Collectors.toSet;

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

  public static final String CLUSTER_SIZE_PADDING_VALIDATION_ENABLED_PARAM = "clusterSizePaddingValidationEnabled";

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
   * @return true if the external service is configured
   */

  public static boolean isExternalServiceConfigured(DomainSpec domainSpec) {
    AdminServer adminServer = domainSpec.getAdminServer();
    AdminService adminService = adminServer != null ? adminServer.getAdminService() : null;
    List<Channel> channels = adminService != null ? adminService.getChannels() : null;
    if (channels != null && !channels.isEmpty()) {
      return true;
    }
    return false;
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
    return getEffectiveConfigurationFactory().getAdminServerSpec();
  }

  public String getRestartVersion() {
    return spec.getRestartVersion();
  }

  public String getIntrospectVersion() {
    return spec.getIntrospectVersion();
  }

  private EffectiveConfigurationFactory getEffectiveConfigurationFactory() {
    return spec.getEffectiveConfigurationFactory(apiVersion);
  }

  /**
   * Returns the specification applicable to a particular server/cluster combination.
   *
   * @param serverName  the name of the server
   * @param clusterName the name of the cluster; may be null or empty if no applicable cluster.
   * @return the effective configuration for the server
   */
  public ServerSpec getServer(String serverName, String clusterName) {
    return getEffectiveConfigurationFactory().getServerSpec(serverName, clusterName);
  }

  /**
   * Returns the specification applicable to a particular cluster.
   *
   * @param clusterName the name of the cluster; may be null or empty if no applicable cluster.
   * @return the effective configuration for the cluster
   */
  public ClusterSpec getCluster(String clusterName) {
    return getEffectiveConfigurationFactory().getClusterSpec(clusterName);
  }

  /**
   * Returns the number of replicas to start for the specified cluster.
   *
   * @param clusterName the name of the cluster
   * @return the result of applying any configurations for this value
   */
  public int getReplicaCount(String clusterName) {
    return getEffectiveConfigurationFactory().getReplicaCount(clusterName);
  }

  public void setReplicaCount(String clusterName, int replicaLimit) {
    getEffectiveConfigurationFactory().setReplicaCount(clusterName, replicaLimit);
  }

  /**
   * Returns the maximum number of unavailable replicas for the specified cluster.
   *
   * @param clusterName the name of the cluster
   * @return the result of applying any configurations for this value
   */
  public int getMaxUnavailable(String clusterName) {
    return getEffectiveConfigurationFactory().getMaxUnavailable(clusterName);
  }

  /**
   * Returns the minimum number of replicas for the specified cluster.
   *
   * @param clusterName the name of the cluster
   * @return the result of applying any configurations for this value
   */
  public int getMinAvailable(String clusterName) {
    return Math.max(getReplicaCount(clusterName) - getMaxUnavailable(clusterName), 0);
  }

  /**
   * Returns whether the specified cluster is allowed to have replica count below the minimum
   * dynamic cluster size configured in WebLogic domain configuration.
   *
   * @param clusterName the name of the cluster
   * @return whether the specified cluster is allowed to have replica count below the minimum
   *     dynamic cluster size configured in WebLogic domain configuration
   */
  public boolean isAllowReplicasBelowMinDynClusterSize(String clusterName) {
    return getEffectiveConfigurationFactory().isAllowReplicasBelowMinDynClusterSize(clusterName);
  }

  public int getMaxConcurrentStartup(String clusterName) {
    return getEffectiveConfigurationFactory().getMaxConcurrentStartup(clusterName);
  }

  public int getMaxConcurrentShutdown(String clusterName) {
    return getEffectiveConfigurationFactory().getMaxConcurrentShutdown(clusterName);
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
    this.status = status;
    return this;
  }

  /**
   * Name of the secret containing WebLogic startup credentials user name and password.
   *
   * @return the secret name
   */
  public String getWebLogicCredentialsSecretName() {
    return spec.getWebLogicCredentialsSecret().getName();
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

  boolean isLogHomeEnabled() {
    return Optional.ofNullable(spec.isLogHomeEnabled()).orElse(getDomainHomeSourceType().hasLogHomeByDefault());
  }

  public String getDataHome() {
    return spec.getDataHome();
  }

  public String getWdtDomainType() {
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
    return getDomainHomeSourceType() == DomainSourceType.FromModel;
  }

  public Model getModel() {
    return spec.getModel();
  }

  public boolean isHttpAccessLogInLogHome() {
    return spec.getHttpAccessLogInLogHome();
  }

  public boolean isIstioEnabled() {
    return spec.isIstioEnabled();
  }

  public int getIstioReadinessPort() {
    return spec.getIstioReadinessPort();
  }

  /**
   * Returns the domain home. May be null, but will not be an empty string.
   *
   * @return domain home
   */
  public String getDomainHome() {
    return Strings.emptyToNull(spec.getDomainHome());
  }

  public boolean isShuttingDown() {
    return getEffectiveConfigurationFactory().isShuttingDown();
  }

  /**
   * Return the names of the exported admin NAPs.
   *
   * @return a list of names; may be empty
   */
  List<String> getAdminServerChannelNames() {
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
    return Optional.ofNullable(spec.getConfiguration())
        .map(Configuration::getSecrets).orElse(spec.getConfigOverrideSecrets());
  }

  /**
   * Returns the model home directory of the domain.
   *
   * @return model home directory
   */
  public String getModelHome() {
    return spec.getModelHome();
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

  public List<String> getValidationFailures(KubernetesResourceLookup kubernetesResources) {
    return new Validator().getValidationFailures(kubernetesResources);
  }

  public List<String> getAdditionalValidationFailures(V1PodSpec podSpec) {
    return new Validator().getAdditionalValidationFailures(podSpec);
  }

  public List<String> getAfterIntrospectValidationFailures(Packet packet) {
    return new Validator().getAfterIntrospectValidationFailures(packet);
  }

  class Validator {
    private final List<String> failures = new ArrayList<>();
    private final Set<String> clusterNames = new HashSet<>();
    private final Set<String> serverNames = new HashSet<>();

    List<String> getValidationFailures(KubernetesResourceLookup kubernetesResources) {
      addDuplicateNames();
      addInvalidMountPaths();
      addUnmappedLogHome();
      addReservedEnvironmentVariables();
      addMissingSecrets(kubernetesResources);
      addIllegalSitConfigForMii();
      verifyNoAlternateSecretNamespaceSpecified();
      addMissingModelConfigMap(kubernetesResources);
      verifyIstioExposingDefaultChannel();
      verifyIntrospectorJobName();

      return failures;
    }

    private void verifyIntrospectorJobName() {
      // K8S adds a 5 character suffix to an introspector job name
      if (LegalNames.toJobIntrospectorName(getDomainUid()).length()
          > LegalNames.LEGAL_DNS_LABEL_NAME_MAX_LENGTH - 5) {
        failures.add(DomainValidationMessages.exceedMaxIntrospectorJobName(
            getDomainUid(),
            LegalNames.toJobIntrospectorName(getDomainUid()),
            LegalNames.LEGAL_DNS_LABEL_NAME_MAX_LENGTH - 5));
      }
    }

    private void verifyGeneratedResourceNames(WlsDomainConfig wlsDomainConfig) {
      checkGeneratedServerServiceName(wlsDomainConfig.getAdminServerName(), -1);
      if (isExternalServiceConfigured(getSpec())) {
        checkGeneratedExternalServiceName(wlsDomainConfig.getAdminServerName());
      }

      // domain level serverConfigs do not contain servers in dynamic clusters
      wlsDomainConfig.getServerConfigs()
          .values()
          .stream()
          .map(WlsServerConfig::getName)
          .forEach(serverName -> checkGeneratedServerServiceName(serverName, -1));
      wlsDomainConfig.getClusterConfigs()
          .values()
          .iterator()
          .forEachRemaining(wlsClusterConfig
              // serverConfigs contains configured and dynamic servers in the cluster
              -> wlsClusterConfig.getServerConfigs().forEach(wlsServerConfig
                  -> this.checkGeneratedServerServiceName(
                      wlsServerConfig.getName(), wlsClusterConfig.getServerConfigs().size())));
      wlsDomainConfig.getClusterConfigs()
          .values()
          .iterator()
          .forEachRemaining(wlsClusterConfig -> this.checkGeneratedClusterServiceName(wlsClusterConfig.getName()));
    }

    private void checkGeneratedExternalServiceName(String adminServerName) {
      if (LegalNames.toExternalServiceName(getDomainUid(), adminServerName).length()
          > LegalNames.LEGAL_DNS_LABEL_NAME_MAX_LENGTH) {
        failures.add(DomainValidationMessages.exceedMaxExternalServiceName(
            getDomainUid(),
            adminServerName,
            LegalNames.toExternalServiceName(getDomainUid(), adminServerName),
            LegalNames.LEGAL_DNS_LABEL_NAME_MAX_LENGTH));
      }
    }

    private void checkGeneratedServerServiceName(String serverName, int clusterSize) {
      int limit = LegalNames.LEGAL_DNS_LABEL_NAME_MAX_LENGTH;
      if (isClusterSizePaddingValidationEnabled() && clusterSize > 0 && clusterSize < 100) {
        limit = clusterSize >= 10 ? limit - 1 : limit - 2;
      }

      if (LegalNames.toServerServiceName(getDomainUid(), serverName).length() > limit) {
        failures.add(DomainValidationMessages.exceedMaxServerServiceName(
            getDomainUid(),
            serverName,
            LegalNames.toServerServiceName(getDomainUid(), serverName),
            limit));
      }
    }

    /**
     * Gets the configured boolean for enabling cluster size padding validation.
     * @return boolean enabled
     */
    public boolean isClusterSizePaddingValidationEnabled() {
      return "true".equalsIgnoreCase(getClusterSizePaddingValidationEnabledParameter());
    }

    private String getClusterSizePaddingValidationEnabledParameter() {
      return Optional.ofNullable(TuningParameters.getInstance())
            .map(t -> t.get(CLUSTER_SIZE_PADDING_VALIDATION_ENABLED_PARAM))
            .orElse("true");
    }

    private void checkGeneratedClusterServiceName(String clusterName) {
      if (LegalNames.toClusterServiceName(getDomainUid(), clusterName).length()
          > LegalNames.LEGAL_DNS_LABEL_NAME_MAX_LENGTH) {
        failures.add(DomainValidationMessages.exceedMaxClusterServiceName(
            getDomainUid(),
            clusterName,
            LegalNames.toClusterServiceName(getDomainUid(), clusterName),
            LegalNames.LEGAL_DNS_LABEL_NAME_MAX_LENGTH));
      }
    }

    public List<String> getAdditionalValidationFailures(V1PodSpec podSpec) {
      addInvalidMountPathsForPodSpec(podSpec);
      return failures;
    }

    private void addDuplicateNames() {
      getSpec().getManagedServers()
          .stream()
          .map(ManagedServer::getServerName)
          .map(LegalNames::toDns1123LegalName)
          .forEach(this::checkDuplicateServerName);
      getSpec().getClusters()
          .stream()
          .map(Cluster::getClusterName)
          .map(LegalNames::toDns1123LegalName)
          .forEach(this::checkDuplicateClusterName);
    }

    private void checkDuplicateServerName(String serverName) {
      if (serverNames.contains(serverName)) {
        failures.add(DomainValidationMessages.duplicateServerName(serverName));
      } else {
        serverNames.add(serverName);
      }
    }

    private void checkDuplicateClusterName(String clusterName) {
      if (clusterNames.contains(clusterName)) {
        failures.add(DomainValidationMessages.duplicateClusterName(clusterName));
      } else {
        clusterNames.add(clusterName);
      }
    }

    private void addInvalidMountPaths() {
      getSpec().getAdditionalVolumeMounts().forEach(this::checkValidMountPath);
      if (getSpec().getAdminServer() != null) {
        getSpec().getAdminServer().getAdditionalVolumeMounts().forEach(this::checkValidMountPath);
      }
      if (getSpec().getClusters() != null) {
        getSpec().getClusters().forEach(
            cluster -> cluster.getAdditionalVolumeMounts().forEach(this::checkValidMountPath));
      }
    }

    private void addInvalidMountPathsForPodSpec(V1PodSpec podSpec) {
      podSpec.getContainers()
          .forEach(container ->
              Optional.ofNullable(container.getVolumeMounts())
                  .ifPresent(volumes -> volumes.forEach(this::checkValidMountPath)));
    }

    private void checkValidMountPath(V1VolumeMount mount) {
      if (skipValidation(mount.getMountPath())) {
        return;
      }

      if (!new File(mount.getMountPath()).isAbsolute()) {
        failures.add(DomainValidationMessages.badVolumeMountPath(mount));
      }
    }

    private boolean skipValidation(String mountPath) {
      List<V1EnvVar> envVars = spec.getEnv();
      Set<String> varNames = envVars.stream().map(V1EnvVar::getName).collect(toSet());
      StringTokenizer nameList = new StringTokenizer(mountPath, TOKEN_START_MARKER);
      if (!nameList.hasMoreElements()) {
        return false;
      }
      while (nameList.hasMoreElements()) {
        String token = nameList.nextToken();
        if (noMatchingEnvVarName(varNames, token)) {
          return false;
        }
      }
      return true;
    }

    private boolean noMatchingEnvVarName(Set<String> varNames, String token) {
      int index = token.indexOf(TOKEN_END_MARKER);
      if (index != -1) {
        String str = token.substring(0, index);
        // IntrospectorJobEnvVars.isReserved() checks env vars in ServerEnvVars too
        if (varNames.contains(str) || IntrospectorJobEnvVars.isReserved(str)) {
          return false;
        }
      }
      return true;
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
      if (getDomainHomeSourceType() == DomainSourceType.FromModel
          && getConfigOverrides() != null) {
        failures.add(DomainValidationMessages.illegalSitConfigForMii(getConfigOverrides()));
      }
    }

    private void verifyIstioExposingDefaultChannel() {
      if (spec.isIstioEnabled()) {
        Optional.ofNullable(spec.getAdminServer())
            .map(AdminServer::getAdminService)
            .map(AdminService::getChannels)
            .ifPresent(cs -> cs.forEach(this::checkForDefaultNameExposed));
      }
    }

    private void checkForDefaultNameExposed(Channel channel) {
      if ("default".equals(channel.getChannelName()) || "default-admin".equals(channel.getChannelName())
            || "default-secure".equals(channel.getChannelName())) {
        failures.add(DomainValidationMessages.cannotExposeDefaultChannelIstio(channel.getChannelName()));
      }
    }

    private void addReservedEnvironmentVariables() {
      checkReservedIntrospectorVariables(spec, "spec");
      Optional.ofNullable(spec.getAdminServer())
          .ifPresent(a -> checkReservedIntrospectorVariables(a, "spec.adminServer"));

      spec.getManagedServers()
          .forEach(s -> checkReservedEnvironmentVariables(s, "spec.managedServers[" + s.getServerName() + "]"));
      spec.getClusters()
          .forEach(s -> checkReservedEnvironmentVariables(s, "spec.clusters[" + s.getClusterName() + "]"));
    }

    public List<String> getAfterIntrospectValidationFailures(Packet packet) {
      verifyGeneratedResourceNames((WlsDomainConfig) packet.get(ProcessingConstants.DOMAIN_TOPOLOGY));
      return failures;
    }

    class EnvironmentVariableCheck {
      private final Predicate<String> isReserved;

      EnvironmentVariableCheck(Predicate<String> isReserved) {
        this.isReserved = isReserved;
      }

      void checkEnvironmentVariables(@Nonnull BaseConfiguration configuration, String prefix) {
        if (configuration.getEnv() == null) {
          return;
        }

        List<String> reservedNames = configuration.getEnv()
            .stream()
            .map(V1EnvVar::getName)
            .filter(isReserved)
            .collect(Collectors.toList());

        if (!reservedNames.isEmpty()) {
          failures.add(DomainValidationMessages.reservedVariableNames(prefix, reservedNames));
        }
      }
    }

    private void checkReservedEnvironmentVariables(BaseConfiguration configuration, String prefix) {
      new EnvironmentVariableCheck(ServerEnvVars::isReserved).checkEnvironmentVariables(configuration, prefix);
    }

    @SuppressWarnings("SameParameterValue")
    private void checkReservedIntrospectorVariables(BaseConfiguration configuration, String prefix) {
      new EnvironmentVariableCheck(IntrospectorJobEnvVars::isReserved).checkEnvironmentVariables(configuration, prefix);
    }

    private void addMissingSecrets(KubernetesResourceLookup resourceLookup) {
      verifySecretExists(resourceLookup, getWebLogicCredentialsSecretName(), SecretType.WebLogicCredentials);
      for (V1LocalObjectReference reference : getImagePullSecrets()) {
        verifySecretExists(resourceLookup, reference.getName(), SecretType.ImagePull);
      }
      for (String secretName : getConfigOverrideSecrets()) {
        verifySecretExists(resourceLookup, secretName, SecretType.ConfigOverride);
      }

      verifySecretExists(resourceLookup, getOpssWalletPasswordSecret(), SecretType.OpssWalletPassword);
      verifySecretExists(resourceLookup, getOpssWalletFileSecret(), SecretType.OpssWalletFile);

      if (getDomainHomeSourceType() == DomainSourceType.FromModel) {
        if (getRuntimeEncryptionSecret() == null) {
          failures.add(DomainValidationMessages.missingRequiredSecret(
              "spec.configuration.model.runtimeEncryptionSecret"));
        } else {
          verifySecretExists(resourceLookup, getRuntimeEncryptionSecret(), SecretType.RuntimeEncryption);
        }
        if (ModelInImageDomainType.JRF.toString().equals(getWdtDomainType()) 
            && getOpssWalletPasswordSecret() == null) {
          failures.add(DomainValidationMessages.missingRequiredOpssSecret(
              "spec.configuration.opss.walletPasswordSecret"));
        }
      }
    }

    private List<V1LocalObjectReference> getImagePullSecrets() {
      return spec.getImagePullSecrets();
    }

    @SuppressWarnings("SameParameterValue")
    private void verifySecretExists(KubernetesResourceLookup resources, String secretName, SecretType type) {
      if (secretName != null && !resources.isSecretExists(secretName, getNamespace())) {
        failures.add(DomainValidationMessages.noSuchSecret(secretName, getNamespace(), type));
      }
    }

    private void verifyNoAlternateSecretNamespaceSpecified() {
      if (!getSpecifiedWebLogicCredentialsNamespace().equals(getNamespace())) {
        failures.add(DomainValidationMessages.illegalSecretNamespace(getSpecifiedWebLogicCredentialsNamespace()));
      }
    }

    private String getSpecifiedWebLogicCredentialsNamespace() {
      return Optional.ofNullable(spec.getWebLogicCredentialsSecret())
          .map(V1SecretReference::getNamespace)
          .orElse(getNamespace());
    }

    private void addMissingModelConfigMap(KubernetesResourceLookup resourceLookup) {
      verifyModelConfigMapExists(resourceLookup, getWdtConfigMap());
    }

    @SuppressWarnings("SameParameterValue")
    private void verifyModelConfigMapExists(KubernetesResourceLookup resources, String modelConfigMapName) {
      if (getDomainHomeSourceType() == DomainSourceType.FromModel
          && modelConfigMapName != null && !resources.isConfigMapExists(modelConfigMapName, getNamespace())) {
        failures.add(DomainValidationMessages.noSuchModelConfigMap(modelConfigMapName, getNamespace()));
      }
    }

  }

}

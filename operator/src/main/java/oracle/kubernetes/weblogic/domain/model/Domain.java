// Copyright (c) 2017, 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.weblogic.domain.model;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;
import javax.validation.Valid;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;
import io.kubernetes.client.openapi.models.V1EnvVar;
import io.kubernetes.client.openapi.models.V1LocalObjectReference;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1SecretReference;
import io.kubernetes.client.openapi.models.V1VolumeMount;
import oracle.kubernetes.json.Description;
import oracle.kubernetes.operator.LabelConstants;
import oracle.kubernetes.operator.VersionConstants;
import oracle.kubernetes.operator.helpers.SecretType;
import oracle.kubernetes.weblogic.domain.EffectiveConfigurationFactory;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;

/**
 * Domain represents a WebLogic domain and how it will be realized in the Kubernetes cluster.
 */
@Description(
    "Domain represents a WebLogic domain and how it will be realized in the Kubernetes cluster.")
public class Domain {
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
  @Description("The API version for the Domain.")
  private String apiVersion;

  /**
   * Kind is a string value representing the REST resource this object represents. Servers may infer
   * this from the endpoint the client submits requests to. Cannot be updated. In CamelCase. More
   * info: https://git.k8s.io/community/contributors/devel/api-conventions.md#types-kinds
   */
  @SerializedName("kind")
  @Expose
  @Description("The type of resource. Must be 'Domain'.")
  private String kind;

  /**
   * Standard object's metadata. More info:
   * https://git.k8s.io/community/contributors/devel/api-conventions.md#metadata
   */
  @SerializedName("metadata")
  @Expose
  @Valid
  @Description("The domain meta-data. Must include the name and namespace.")
  @Nonnull
  private V1ObjectMeta metadata = new V1ObjectMeta();

  /**
   * DomainSpec is a description of a domain.
   */
  @SerializedName("spec")
  @Expose
  @Valid
  @Description("The specification of the domain. Required.")
  @Nonnull
  private DomainSpec spec = new DomainSpec();

  /**
   * DomainStatus represents information about the status of a domain. Status may trail the actual
   * state of a system.
   */
  @SerializedName("status")
  @Expose
  @Valid
  @Description("The current status of the domain. Updated by the operator.")
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

  private EffectiveConfigurationFactory getEffectiveConfigurationFactory() {
    return spec.getEffectiveConfigurationFactory(apiVersion, getResourceVersion());
  }

  private String getResourceVersion() {
    Map<String, String> labels = metadata.getLabels();
    if (labels == null) {
      return VersionConstants.DEFAULT_DOMAIN_VERSION;
    }
    return labels.get(LabelConstants.RESOURCE_VERSION_LABEL);
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
   * Name of the secret containing WebLogic startup credentials username and password.
   *
   * @return the secret name
   */
  public String getWebLogicCredentialsSecretName() {
    return spec.getWebLogicCredentialsSecret().getName();
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
    return spec.isLogHomeEnabled();
  }

  public String getDataHome() {
    return spec.getDataHome();
  }

  public boolean isIncludeServerOutInPodLog() {
    return spec.getIncludeServerOutInPodLog();
  }

  boolean isDomainHomeInImage() {
    return spec.isDomainHomeInImage();
  }

  public boolean isIstioEnabled() {
    return spec.isIstioEnabled();
  }

  public int getIstioReadinessPort() {
    return spec.getIstioReadinessPort();
  }

  /**
   * Returns the domain home.
   *
   * <p>Defaults to either /u01/oracle/user_projects/domains or /shared/domains/domainUID
   *
   * @return domain home
   */
  public String getDomainHome() {
    if (spec.getDomainHome() != null) {
      return spec.getDomainHome();
    }
    if (spec.isDomainHomeInImage()) {
      return "/u01/oracle/user_projects/domains";
    }
    return "/shared/domains/" + getDomainUid();
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
   * Returns a list of Kubernetes secret names used in optional configuration overrides.
   *
   * @return list of Kubernetes secret names
   */
  public List<String> getConfigOverrideSecrets() {
    return spec.getConfigOverrideSecrets();
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
      verifyNoAlternateSecretNamespaceSpecified();

      return failures;
    }

    private void addDuplicateNames() {
      getSpec().getManagedServers()
          .stream()
          .map(ManagedServer::getServerName)
          .map(this::toDns1123LegalName)
          .forEach(this::checkDuplicateServerName);
      getSpec().getClusters()
          .stream()
          .map(Cluster::getClusterName)
          .map(this::toDns1123LegalName)
          .forEach(this::checkDuplicateClusterName);
    }

    /**
     * Converts value to nearest DNS-1123 legal name, which can be used as a Kubernetes identifier.
     *
     * @param value Input value
     * @return nearest DNS-1123 legal name
     */
    String toDns1123LegalName(String value) {
      return value.toLowerCase().replace('_', '-');
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
    }

    private void checkValidMountPath(V1VolumeMount mount) {
      if (!new File(mount.getMountPath()).isAbsolute()) {
        failures.add(DomainValidationMessages.badVolumeMountPath(mount));
      }
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

    private void addReservedEnvironmentVariables() {
      checkReservedIntrospectorVariables(spec, "spec");
      Optional.ofNullable(spec.getAdminServer())
          .ifPresent(a -> checkReservedIntrospectorVariables(a, "spec.adminServer"));

      spec.getManagedServers()
          .forEach(s -> checkReservedEnvironmentVariables(s, "spec.managedServers[" + s.getServerName() + "]"));
      spec.getClusters()
          .forEach(s -> checkReservedEnvironmentVariables(s, "spec.clusters[" + s.getClusterName() + "]"));
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
    }

    private List<V1LocalObjectReference> getImagePullSecrets() {
      return spec.getImagePullSecrets();
    }

    private List<String> getConfigOverrideSecrets() {
      return spec.getConfigOverrideSecrets();
    }

    @SuppressWarnings("SameParameterValue")
    private void verifySecretExists(KubernetesResourceLookup resources, String secretName, SecretType type) {
      if (!resources.isSecretExists(secretName, getNamespace())) {
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

  }

}

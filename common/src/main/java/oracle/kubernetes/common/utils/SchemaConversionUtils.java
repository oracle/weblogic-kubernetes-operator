// Copyright (c) 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.common.utils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.StringJoiner;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.JsonPathException;
import com.jayway.jsonpath.ReadContext;
import oracle.kubernetes.common.AuxiliaryImageConstants;
import oracle.kubernetes.common.CommonConstants;
import oracle.kubernetes.common.helpers.AuxiliaryImageEnvVars;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import static oracle.kubernetes.common.CommonConstants.API_VERSION_V8;
import static oracle.kubernetes.common.CommonConstants.API_VERSION_V9;

@SuppressWarnings({"unchecked", "rawtypes"})
public class SchemaConversionUtils {
  private static final String API_VERSION = "apiVersion";
  private static final String METADATA = "metadata";
  private static final String NAMESPACE = "namespace";
  private static final String NAME = "name";
  private static final String SPEC = "spec";
  private static final String STATUS = "status";
  private static final String TYPE = "type";
  private static final String CLUSTERS = "clusters";
  private static final String CLUSTER_NAME = "clusterName";

  /**
   * The list of failure reason strings. Hard-coded here to match the values in DomainFailureReason.
   * Validated in tests in the operator.
   */
  public static final List<String> SUPPORTED_FAILURE_REASONS = List.of(
        "Aborted", "Internal", "TopologyMismatch", "ReplicasTooHigh",
        "ServerPod", "Kubernetes", "Introspection", "DomainInvalid");

  /**
   * The list of condition types no longer supported. Should match the values in DomainConditionType with
   * a true value for isObsolete(). Validated in tests in the operator.
   */
  public static final List<String> OBSOLETE_CONDITION_TYPES = List.of("Progressing");

  private static final String VOLUME_MOUNTS = "volumeMounts";
  private static final String VOLUME = "volume";
  private static final String MOUNT_PATH = "mountPath";
  private static final String IMAGE = "image";
  private static final String V8_STATE_GOAL_KEY = "desiredState";
  private static final String V9_STATE_GOAL_KEY = "stateGoal";

  private final AtomicInteger containerIndex = new AtomicInteger(0);
  private final String targetAPIVersion;

  public SchemaConversionUtils(String targetAPIVersion) {
    this.targetAPIVersion = targetAPIVersion;
  }

  public SchemaConversionUtils() {
    this(API_VERSION_V9);
  }

  public static SchemaConversionUtils create() {
    return create(API_VERSION_V9);
  }

  public static SchemaConversionUtils create(String targetAPIVersion) {
    return new SchemaConversionUtils(targetAPIVersion);
  }

  public interface ResourceLookup {
    List<Map<String, Object>> listClusters();
  }

  public static class Resources {
    public final Map<String, Object> domain;
    public final List<Map<String, Object>> clusters;

    public Resources(Map<String, Object> domain, List<Map<String, Object>> clusters) {
      this.domain = domain;
      this.clusters = clusters;
    }
  }

  /**
   * Convert the domain schema to desired API version.
   * @param domain Domain to be converted.
   * @param resourceLookup Related resource lookup
   * @return The converted Domain and any associated Cluster resources
   */
  @SuppressWarnings("java:S112")
  public Resources convertDomainSchema(Map<String, Object> domain, ResourceLookup resourceLookup) {
    Map<String, Object> spec = getSpec(domain);
    if (spec == null) {
      return new Resources(domain, Collections.emptyList());
    }

    String apiVersion = (String) domain.get(API_VERSION);

    integrateClusters(spec, resourceLookup);

    adjustAdminPortForwardingDefault(spec, apiVersion);
    convertLegacyAuxiliaryImages(spec);
    convertDomainStatus(domain);
    convertDomainHomeInImageToDomainHomeSourceType(domain);
    moveConfigOverrides(domain);
    moveConfigOverrideSecrets(domain);
    constantsToCamelCase(spec);
    adjustReplicasDefault(spec, apiVersion);
    removeWebLogicCredentialsSecretNamespace(spec, apiVersion);

    Map<String, Object> toBePreserved = new TreeMap<>();
    removeAndPreserveAllowReplicasBelowMinDynClusterSize(spec, toBePreserved);
    removeAndPreserveServerStartState(spec, toBePreserved);
    removeAndPreserveIstio(spec, toBePreserved);

    try {
      preserve(domain, toBePreserved, apiVersion);
      restore(domain);
    } catch (IOException io) {
      throw new RuntimeException(io); // need to use unchecked exception because of stream processing
    }

    List<Map<String, Object>> clusters = new ArrayList<>();
    generateClusters(domain, clusters, apiVersion);

    domain.put(API_VERSION, targetAPIVersion);
    return new Resources(domain, clusters);
  }

  /**
   * Convert the domain schema to desired API version.
   * @param domainYaml Domain yaml to be converted.
   * @return Domain String containing the converted domain yaml.
   */
  public String convertDomainSchema(String domainYaml) {
    DumperOptions options = new DumperOptions();
    options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
    options.setPrettyFlow(true);
    Yaml yaml = new Yaml(options);
    Object domain = yaml.load(domainYaml);
    Resources convertedResources = convertDomainSchema((Map<String, Object>) domain, null);
    StringBuilder result = new StringBuilder();
    result.append(yaml.dump(convertedResources.domain));
    for (Map<String, Object> cluster : convertedResources.clusters) {
      result.append("\n\n---\n\n");
      result.append(yaml.dump(cluster));
    }
    return result.toString();
  }

  private void adjustAdminPortForwardingDefault(Map<String, Object> spec, String apiVersion) {
    Map<String, Object> adminServerSpec = (Map<String, Object>) spec.get("adminServer");
    Boolean adminChannelPortForwardingEnabled = (Boolean) Optional.ofNullable(adminServerSpec)
            .map(as -> as.get("adminChannelPortForwardingEnabled")).orElse(null);
    if ((adminChannelPortForwardingEnabled == null) && (CommonConstants.API_VERSION_V8.equals(apiVersion))) {
      Optional.ofNullable(adminServerSpec).ifPresent(as -> as.put("adminChannelPortForwardingEnabled", false));
    }
  }

  private void convertLegacyAuxiliaryImages(Map<String, Object> spec) {
    List<Object> auxiliaryImageVolumes = Optional.ofNullable(getAuxiliaryImageVolumes(spec)).orElse(new ArrayList<>());
    convertAuxiliaryImages(spec, auxiliaryImageVolumes);
    Optional.ofNullable(getAdminServer(spec)).ifPresent(as -> convertAuxiliaryImages(as, auxiliaryImageVolumes));
    Optional.ofNullable(getClusters(spec)).ifPresent(cl -> cl.forEach(cluster ->
            convertAuxiliaryImages((Map<String, Object>) cluster, auxiliaryImageVolumes)));
    Optional.ofNullable(getManagedServers(spec)).ifPresent(ms -> ms.forEach(managedServer ->
            convertAuxiliaryImages((Map<String, Object>) managedServer, auxiliaryImageVolumes)));
    spec.remove("auxiliaryImageVolumes");
  }

  private void convertDomainStatus(Map<String, Object> domain) {
    if (API_VERSION_V8.equals(targetAPIVersion)) {
      convertCompletedToProgressing(domain);
      Optional.ofNullable(getStatus(domain)).ifPresent(status -> status.remove("observedGeneration"));
      renameServerStatusFieldsV9ToV8(domain);
    } else { // 9 or above
      removeObsoleteConditionsFromDomainStatus(domain);
      removeUnsupportedDomainStatusConditionReasons(domain);
      renameServerStatusFieldsV8ToV9(domain);
    }
  }

  private void convertCompletedToProgressing(Map<String, Object> domain) {
    Iterator<Map<String, String>> conditions = getStatusConditions(domain).iterator();
    while (conditions.hasNext()) {
      Map<String, String> condition = conditions.next();
      if ("Completed".equals(condition.get(TYPE))) {
        if ("False".equals(condition.get(STATUS))) {
          condition.put(TYPE, "Progressing");
          condition.put(STATUS, "True");
        } else {
          conditions.remove();
        }
      }
    }
  }

  private void removeObsoleteConditionsFromDomainStatus(Map<String, Object> domain) {
    getStatusConditions(domain).removeIf(this::isObsoleteCondition);
  }

  private boolean isObsoleteCondition(Map<String, String> condition) {
    return Optional.ofNullable(condition.get("type")).map(this::isObsoleteConditionType).orElse(true);
  }

  private boolean isObsoleteConditionType(String type) {
    return OBSOLETE_CONDITION_TYPES.contains(type);
  }

  @Nonnull
  private List<Map<String,String>> getStatusConditions(Map<String, Object> domain) {
    return (List<Map<String,String>>) Optional.ofNullable(getStatus(domain))
          .map(status -> status.get("conditions"))
          .orElse(Collections.emptyList());
  }

  private void removeUnsupportedDomainStatusConditionReasons(Map<String, Object> domain) {
    getStatusConditions(domain).forEach(this::removeReasonIfUnsupported);
  }

  private void removeReasonIfUnsupported(Map<String, String> condition) {
    removeIf(condition, "reason", this::isUnsupportedReason);
  }

  @SuppressWarnings("SameParameterValue")
  private void removeIf(Map<String, String> map, String key, Predicate<String> predicate) {
    if (Optional.ofNullable(map.get(key)).map(predicate::test).orElse(false)) {
      map.remove(key);
    }
  }

  private boolean isUnsupportedReason(@Nonnull String reason) {
    return !SUPPORTED_FAILURE_REASONS.contains(reason);
  }

  private void renameServerStatusFieldsV8ToV9(Map<String, Object> domain) {
    getServerStatuses(domain).forEach(this::adjustServerStatusV8ToV9);
  }

  private void adjustServerStatusV8ToV9(Map<String, Object> serverStatus) {
    serverStatus.computeIfAbsent(V9_STATE_GOAL_KEY, key -> serverStatus.get(V8_STATE_GOAL_KEY));
    serverStatus.remove(V8_STATE_GOAL_KEY);
  }

  private void renameServerStatusFieldsV9ToV8(Map<String, Object> domain) {
    getServerStatuses(domain).forEach(this::adjustServerStatusV9ToV8);
  }

  private void adjustServerStatusV9ToV8(Map<String, Object> serverStatus) {
    serverStatus.computeIfAbsent(V8_STATE_GOAL_KEY, key -> serverStatus.get(V9_STATE_GOAL_KEY));
    serverStatus.remove(V9_STATE_GOAL_KEY);
  }

  @Nonnull
  private List<Map<String,Object>> getServerStatuses(Map<String, Object> domain) {
    return (List<Map<String,Object>>) Optional.ofNullable(getStatus(domain))
          .map(status -> status.get("servers"))
          .orElse(Collections.emptyList());
  }

  private void convertDomainHomeInImageToDomainHomeSourceType(Map<String, Object> domain) {
    Map<String, Object> domainSpec = (Map<String, Object>) domain.get("spec");
    if (domainSpec != null) {
      Object existing = domainSpec.remove("domainHomeInImage");
      if (existing != null && !domainSpec.containsKey("domainHomeSourceType")) {
        domainSpec.put("domainHomeSourceType",
                Boolean.parseBoolean((String) existing) ? "Image" : "PersistentVolume");
      }
    }
  }

  private Map<String, Object> getOrCreateConfiguration(Map<String, Object> domainSpec) {
    return (Map<String, Object>) domainSpec.computeIfAbsent("configuration", k -> new LinkedHashMap<>());
  }

  private void moveConfigOverrides(Map<String, Object> domain) {
    Map<String, Object> domainSpec = (Map<String, Object>) domain.get("spec");
    if (domainSpec != null) {
      Object existing = domainSpec.remove("configOverrides");
      if (existing != null) {
        Map<String, Object> configuration = getOrCreateConfiguration(domainSpec);
        configuration.putIfAbsent("overridesConfigMap", existing);
      }
    }
  }

  private void moveConfigOverrideSecrets(Map<String, Object> domain) {
    Map<String, Object> domainSpec = (Map<String, Object>) domain.get("spec");
    if (domainSpec != null) {
      Object existing = domainSpec.remove("configOverrideSecrets");
      if (existing != null) {
        Map<String, Object> configuration = getOrCreateConfiguration(domainSpec);
        configuration.putIfAbsent("secrets", existing);
      }
    }
  }

  private void constantsToCamelCase(Map<String, Object> spec) {
    convertServerStartPolicy(spec);
    Optional.ofNullable(getAdminServer(spec)).ifPresent(this::convertServerStartPolicy);
    Optional.ofNullable(getClusters(spec)).ifPresent(cl -> cl.forEach(cluster ->
            convertServerStartPolicy((Map<String, Object>) cluster)));
    Optional.ofNullable(getManagedServers(spec)).ifPresent(ms -> ms.forEach(managedServer ->
            convertServerStartPolicy((Map<String, Object>) managedServer)));

    Optional.ofNullable(getConfiguration(spec)).ifPresent(this::convertOverrideDistributionStrategy);

    convertLogHomeLayout(spec);
  }

  private void convertServerStartPolicy(Map<String, Object> spec) {
    spec.computeIfPresent("serverStartPolicy", this::serverStartPolicyCamelCase);
  }

  public static <K, V> Map<V, K> invertMapUsingMapper(Map<K, V> sourceMap) {
    return sourceMap.entrySet().stream().collect(
            Collectors.toMap(Map.Entry::getValue, Map.Entry::getKey, (oldValue, newValue) -> oldValue));
  }

  private static Object convertWithMap(Map<String, String> map, Object value) {
    if (value instanceof String) {
      return map.get(value);
    }
    return value;
  }

  private Map<String, String> select(Map<String, String> apiVersion9, Map<String, String> apiVersion8) {
    switch (targetAPIVersion) {
      case API_VERSION_V8:
        return apiVersion8;
      case API_VERSION_V9:
      default:
        return apiVersion9;
    }
  }

  private static final Map<String, String> serverStartPolicyMap = Map.of(
      "ALWAYS", "Always", "NEVER", "Never", "IF_NEEDED", "IfNeeded", "ADMIN_ONLY", "AdminOnly");

  private static final Map<String, String> invertServerStartPolicyMap = invertMapUsingMapper(serverStartPolicyMap);

  private Object serverStartPolicyCamelCase(String key, Object value) {
    return convertWithMap(select(serverStartPolicyMap, invertServerStartPolicyMap), value);
  }

  private void convertOverrideDistributionStrategy(Map<String, Object> configuration) {
    configuration.computeIfPresent("overrideDistributionStrategy", this::overrideDistributionStrategyCamelCase);
  }

  private static final Map<String, String> overrideDistributionStrategyMap = Map.of(
          "DYNAMIC", "Dynamic", "ON_RESTART", "OnRestart");

  private static final Map<String, String> invertOverrideDistributionStrategyMap =
          invertMapUsingMapper(overrideDistributionStrategyMap);

  private Object overrideDistributionStrategyCamelCase(String key, Object value) {
    return convertWithMap(select(overrideDistributionStrategyMap, invertOverrideDistributionStrategyMap), value);
  }

  private void convertLogHomeLayout(Map<String, Object> configuration) {
    configuration.computeIfPresent("logHomeLayout", this::logHomeLayoutCamelCase);
  }

  private static final Map<String, String> logHomeLayoutMap = Map.of(
          "FLAT", "Flat", "BY_SERVERS", "ByServers");

  private static final Map<String, String> invertLogHomeLayoutMap = invertMapUsingMapper(logHomeLayoutMap);

  private Object logHomeLayoutCamelCase(String key, Object value) {
    return convertWithMap(select(logHomeLayoutMap, invertLogHomeLayoutMap), value);
  }

  private List<Object> getAuxiliaryImageVolumes(Map<String, Object> spec) {
    return (List<Object>) spec.get("auxiliaryImageVolumes");
  }

  private Map<String, Object> getConfiguration(Map<String, Object> spec) {
    return (Map<String, Object>) spec.get("configuration");
  }

  private Map<String, Object> getAdminServer(Map<String, Object> spec) {
    return (Map<String, Object>) spec.get("adminServer");
  }

  private List<Object> getClusters(Map<String, Object> spec) {
    return (List<Object>) spec.get(CLUSTERS);
  }

  private List<Object> getManagedServers(Map<String, Object> spec) {
    return (List<Object>) spec.get("managedServers");
  }

  private void convertAuxiliaryImages(Map<String, Object> spec, List<Object> auxiliaryImageVolumes) {
    Map<String, Object> serverPod = getServerPod(spec);
    Optional.ofNullable(serverPod).map(this::getAuxiliaryImages).ifPresent(auxiliaryImages ->
            addInitContainersVolumeAndMountsToServerPod(serverPod, auxiliaryImages, auxiliaryImageVolumes));
    Optional.ofNullable(serverPod).ifPresent(cs -> cs.remove("auxiliaryImages"));
  }

  private List<Object> getAuxiliaryImages(Map<String, Object> serverPod) {
    return (List<Object>) Optional.ofNullable(serverPod).map(sp -> (sp.get("auxiliaryImages"))).orElse(null);
  }

  private Map<String, Object> getServerPod(Map<String, Object> spec) {
    return (Map<String, Object>) Optional.ofNullable(spec).map(s -> s.get("serverPod")).orElse(null);
  }

  Map<String, Object> getSpec(Map<String, Object> domain) {
    return (Map<String, Object>) domain.get(SPEC);
  }

  Map<String, Object> getStatus(Map<String, Object> domain) {
    return (Map<String, Object>) domain.get(STATUS);
  }

  Map<String, Object> getMetadata(Map<String, Object> domain) {
    return (Map<String, Object>) domain.get(METADATA);
  }

  private void addInitContainersVolumeAndMountsToServerPod(Map<String, Object> serverPod, List<Object> auxiliaryImages,
                                                                         List<Object> auxiliaryImageVolumes) {
    addEmptyDirVolume(serverPod, auxiliaryImageVolumes);
    List<Object> initContainers = Optional.ofNullable((List<Object>) serverPod.get("initContainers"))
        .orElse(new ArrayList<>());
    for (Object auxiliaryImage : auxiliaryImages) {
      initContainers.add(
          createInitContainerForAuxiliaryImage((Map<String, Object>) auxiliaryImage, containerIndex.get(),
              auxiliaryImageVolumes));
      containerIndex.addAndGet(1);
    }
    serverPod.put("initContainers", initContainers);
    auxiliaryImages.forEach(ai -> addVolumeMount(serverPod, (Map<String, Object>)ai, auxiliaryImageVolumes));
    addAuxiliaryImageEnv(auxiliaryImages, serverPod, auxiliaryImageVolumes);
  }

  private void addAuxiliaryImageEnv(List<Object> auxiliaryImages, Map<String, Object> serverPod,
                                    List<Object> auxiliaryImageVolumes) {
    List<Object> vars = Optional.ofNullable((List<Object>)serverPod.get("env")).orElse(new ArrayList<>());
    Optional.ofNullable(auxiliaryImages).flatMap(ais ->
            Optional.ofNullable(getAuxiliaryImagePaths(ais, auxiliaryImageVolumes)))
            .ifPresent(c -> addEnvVar(vars, AuxiliaryImageEnvVars.AUXILIARY_IMAGE_PATHS, c));
    serverPod.put("env", vars);
  }

  private String getAuxiliaryImagePaths(List<Object> auxiliaryImages,
                                 List<Object> auxiliaryImageVolumes) {
    return Optional.ofNullable(auxiliaryImages).map(
          aiList -> createauxiliaryImagePathsEnv(aiList, auxiliaryImageVolumes)).orElse(null);
  }

  private String createauxiliaryImagePathsEnv(List<Object> auxiliaryImages, List<Object> auxiliaryImageVolumes) {
    StringJoiner auxiliaryImagePaths = new StringJoiner(",","","");
    auxiliaryImages.forEach(auxiliaryImage -> auxiliaryImagePaths.add(
          getMountPath((Map<String,Object>)auxiliaryImage, auxiliaryImageVolumes)));
    return Arrays.stream(auxiliaryImagePaths.toString().split(Pattern.quote(","))).distinct()
          .filter(st -> !st.isEmpty()).collect(Collectors.joining(","));
  }

  private void addEmptyDirVolume(Map<String,Object> serverPod, List<Object> auxiliaryImageVolumes) {
    Optional.ofNullable(auxiliaryImageVolumes).ifPresent(volumes -> volumes.forEach(auxiliaryImageVolume ->
          addVolumeIfMissing(serverPod, (Map<String, Object>)auxiliaryImageVolume)));
  }

  private void addVolumeIfMissing(Map<String, Object> serverPod, Map<String, Object> auxiliaryImageVolume) {
    List<Object> existingVolumes = Optional.ofNullable((List<Object>)serverPod.get("volumes"))
            .orElse(new ArrayList<>());
    if (Optional.of(existingVolumes).map(volumes -> (volumes).stream().noneMatch(
          volume -> podHasMatchingVolumeName((Map<String, Object>)volume, auxiliaryImageVolume))).orElse(true)) {
      existingVolumes.add(createEmptyDirVolume(auxiliaryImageVolume));
    }
    serverPod.put("volumes", existingVolumes);
  }

  private boolean podHasMatchingVolumeName(Map<String, Object> volume, Map<String, Object> auxiliaryImageVolume) {
    return (volume.get("name")).equals(auxiliaryImageVolume.get("name"));
  }

  private Map<String,Object> createEmptyDirVolume(Map<String, Object> auxiliaryImageVolume) {
    Map<String, Object> emptyDirVolumeSource = new LinkedHashMap<>();
    Optional.ofNullable(auxiliaryImageVolume.get("medium")).ifPresent(m -> emptyDirVolumeSource.put("medium", m));
    Optional.ofNullable(auxiliaryImageVolume.get("sizeLimit"))
          .ifPresent(sl -> emptyDirVolumeSource.put("sizeLimit", sl));
    Map<String, Object> emptyDirVolume = new LinkedHashMap<>();
    emptyDirVolume.put("name", getDNS1123auxiliaryImageVolumeName(auxiliaryImageVolume.get("name")));
    emptyDirVolume.put("emptyDir", emptyDirVolumeSource);
    return emptyDirVolume;
  }

  private void addVolumeMountIfMissing(Map<String, Object> serverPod, Map<String, Object> auxiliaryImage,
                                       String mountPath) {
    if (Optional.ofNullable(serverPod.get(VOLUME_MOUNTS)).map(volumeMounts -> ((List)volumeMounts).stream().noneMatch(
          volumeMount -> hasMatchingVolumeMountName(volumeMount, auxiliaryImage))).orElse(true)) {
      serverPod.put(VOLUME_MOUNTS, Collections.singletonList(getVolumeMount(auxiliaryImage, mountPath)));
    }
  }

  private Object getVolumeMount(Map auxiliaryImage, String mountPath) {
    Map<String, String> volumeMount = new LinkedHashMap<>();
    volumeMount.put("name", getDNS1123auxiliaryImageVolumeName(auxiliaryImage.get(VOLUME)));
    volumeMount.put(MOUNT_PATH, mountPath);
    return volumeMount;
  }

  private Map<String, String> getScriptsVolumeMount() {
    Map<String, String> volumeMount = new LinkedHashMap<>();
    volumeMount.put("name", CommonConstants.SCRIPTS_VOLUME);
    volumeMount.put(MOUNT_PATH, CommonConstants.SCRIPTS_MOUNTS_PATH);
    return volumeMount;
  }

  private boolean hasMatchingVolumeMountName(Object volumeMount, Map<String, Object> auxiliaryImage) {
    return getDNS1123auxiliaryImageVolumeName(auxiliaryImage.get(VOLUME)).equals(((Map)volumeMount).get("name"));
  }

  public static String getDNS1123auxiliaryImageVolumeName(Object name) {
    return CommonUtils.toDns1123LegalName(CommonConstants.COMPATIBILITY_MODE
        + AuxiliaryImageConstants.AUXILIARY_IMAGE_VOLUME_NAME_PREFIX + name);
  }

  private void addVolumeMount(Map<String, Object> serverPod, Map<String, Object> auxiliaryImage,
                           List<Object> auxiliaryImageVolumes) {
    Optional.ofNullable(getMountPath(auxiliaryImage,
          auxiliaryImageVolumes)).ifPresent(mountPath ->
          addVolumeMountIfMissing(serverPod, auxiliaryImage, mountPath));
  }


  private String getMountPath(Map<String, Object> auxiliaryImage, List<Object> auxiliaryImageVolumes) {
    return auxiliaryImageVolumes.stream().filter(
          auxiliaryImageVolume -> hasMatchingVolumeName((Map)auxiliaryImageVolume, auxiliaryImage)).findFirst()
          .map(aiv -> (String)((Map<String, Object>) aiv).get(MOUNT_PATH)).orElse(null);
  }

  private boolean hasMatchingVolumeName(Map<String, Object> auxiliaryImageVolume, Map<String, Object> auxiliaryImage) {
    return Optional.ofNullable(auxiliaryImage.get(VOLUME))
            .map(v -> v.equals(auxiliaryImageVolume.get("name"))).orElse(false);
  }

  private Map<String, Object> createInitContainerForAuxiliaryImage(Map<String, Object> auxiliaryImage, int index,
                                                   List<Object> auxiliaryImageVolumes) {
    Map<String, Object> container = new LinkedHashMap<>();
    container.put("name", CommonConstants.COMPATIBILITY_MODE + getName(index));
    container.put(IMAGE, auxiliaryImage.get(IMAGE));
    container.put("command", List.of(AuxiliaryImageConstants.AUXILIARY_IMAGE_INIT_CONTAINER_WRAPPER_SCRIPT));
    container.put("imagePullPolicy", getImagePullPolicy(auxiliaryImage));
    container.put("env", createEnv(auxiliaryImage, auxiliaryImageVolumes, getName(index)));
    container.put(VOLUME_MOUNTS, Arrays.asList(getVolumeMount(auxiliaryImage,
        AuxiliaryImageConstants.AUXILIARY_IMAGE_TARGET_PATH), getScriptsVolumeMount()));
    return container;
  }

  private Object getImagePullPolicy(Map<String, Object> auxiliaryImage) {
    return Optional.ofNullable(auxiliaryImage.get("imagePullPolicy")).orElse(
            CommonUtils.getInferredImagePullPolicy((String) auxiliaryImage.get(IMAGE)));
  }

  private List<Object> createEnv(Map<String, Object> auxiliaryImage, List<Object> auxiliaryImageVolumes, String name) {
    List<Object> vars = new ArrayList<>();
    addEnvVar(vars, AuxiliaryImageEnvVars.AUXILIARY_IMAGE_PATH, getMountPath(auxiliaryImage, auxiliaryImageVolumes));
    addEnvVar(vars, AuxiliaryImageEnvVars.AUXILIARY_IMAGE_TARGET_PATH,
        AuxiliaryImageConstants.AUXILIARY_IMAGE_TARGET_PATH);
    addEnvVar(vars, AuxiliaryImageEnvVars.AUXILIARY_IMAGE_COMMAND, getCommand(auxiliaryImage));
    addEnvVar(vars, AuxiliaryImageEnvVars.AUXILIARY_IMAGE_CONTAINER_IMAGE, (String)auxiliaryImage.get(IMAGE));
    addEnvVar(vars, AuxiliaryImageEnvVars.AUXILIARY_IMAGE_CONTAINER_NAME, CommonConstants.COMPATIBILITY_MODE + name);
    return vars;
  }

  private String getCommand(Map<String, Object> auxiliaryImage) {
    return Optional.ofNullable((String) auxiliaryImage.get("command"))
            .orElse(AuxiliaryImageConstants.AUXILIARY_IMAGE_DEFAULT_INIT_CONTAINER_COMMAND);
  }

  private String getName(int index) {
    return AuxiliaryImageConstants.AUXILIARY_IMAGE_INIT_CONTAINER_NAME_PREFIX + (index + 1);
  }

  private void addEnvVar(List<Object> vars, String name, String value) {
    Map<String, String> envVar = new LinkedHashMap<>();
    envVar.put("name", name);
    envVar.put("value", value);
    vars.add(envVar);
  }

  private void adjustReplicasDefault(Map<String, Object> spec, String apiVersion) {
    if (CommonConstants.API_VERSION_V8.equals(apiVersion)) {
      spec.putIfAbsent("replicas", 0);
    }
  }

  private void removeAndPreserveIstio(Map<String, Object> spec, Map<String, Object> toBePreserved) {
    Optional.ofNullable(getConfiguration(spec)).ifPresent(configuration -> {
      Object existing = configuration.remove("istio");
      if (existing != null) {
        preserve(toBePreserved, "$.spec.configuration", Map.of("istio", existing));
      }
    });
  }
  
  private void removeWebLogicCredentialsSecretNamespace(Map<String, Object> spec, String apiVersion) {
    if (CommonConstants.API_VERSION_V8.equals(apiVersion)) {
      Optional.ofNullable((Map<String, Object>) spec.get("webLogicCredentialsSecret"))
          .ifPresent(wcs -> wcs.remove(NAMESPACE));
    }
  }

  private void removeAndPreserveAllowReplicasBelowMinDynClusterSize(Map<String, Object> spec,
                                                                    Map<String, Object> toBePreserved) {
    removeAndPreserveAllowReplicasBelowMinDynClusterSize(spec, toBePreserved, "$.spec");
    Optional.ofNullable(getClusters(spec)).ifPresent(cl -> cl.forEach(cluster ->
        removeAndPreserveAllowReplicasBelowMinDynClusterSizeForCluster((Map<String, Object>) cluster, toBePreserved)));
  }

  private void removeAndPreserveAllowReplicasBelowMinDynClusterSize(Map<String, Object> spec,
                                                                    Map<String, Object> toBePreserved, String scope) {
    Object existing = spec.remove("allowReplicasBelowMinDynClusterSize");
    if (existing != null) {
      preserve(toBePreserved, scope, Map.of("allowReplicasBelowMinDynClusterSize", existing));
    }
  }

  private void removeAndPreserveAllowReplicasBelowMinDynClusterSizeForCluster(Map<String, Object> cluster,
                                                                              Map<String, Object> toBePreserved) {
    Object name = cluster.get(CLUSTER_NAME);
    if (name != null) {
      removeAndPreserveAllowReplicasBelowMinDynClusterSize(
          cluster, toBePreserved, "$.spec.clusters[?(@.clusterName=='" + name + "')]");
    }
  }

  private void removeAndPreserveServerStartState(Map<String, Object> spec, Map<String, Object> toBePreserved) {
    removeAndPreserveServerStartState(spec, toBePreserved, "$.spec");
    Optional.ofNullable(getAdminServer(spec)).ifPresent(
        as -> removeAndPreserveServerStartState(as, toBePreserved, "$.spec.adminServer"));
    Optional.ofNullable(getClusters(spec)).ifPresent(cl -> cl.forEach(cluster ->
        removeAndPreserveServerStartStateForCluster((Map<String, Object>) cluster, toBePreserved)));
    Optional.ofNullable(getManagedServers(spec)).ifPresent(ms -> ms.forEach(managedServer ->
        removeAndPreserveServerStartStateForManagedServer((Map<String, Object>) managedServer, toBePreserved)));
  }

  private void removeAndPreserveServerStartState(Map<String, Object> spec,
                                                 Map<String, Object> toBePreserved, String scope) {
    Object existing = spec.remove("serverStartState");
    if (existing != null) {
      preserve(toBePreserved, scope, Map.of("serverStartState", existing));
    }
  }

  private void removeAndPreserveServerStartStateForCluster(Map<String, Object> cluster,
                                                 Map<String, Object> toBePreserved) {
    Object name = cluster.get(CLUSTER_NAME);
    if (name != null) {
      removeAndPreserveServerStartState(cluster, toBePreserved, "$.spec.clusters[?(@.clusterName=='" + name + "')]");
    }
  }

  private void removeAndPreserveServerStartStateForManagedServer(Map<String, Object> managedServer,
                                                           Map<String, Object> toBePreserved) {
    Object name = managedServer.get("serverName");
    if (name != null) {
      removeAndPreserveServerStartState(
          managedServer, toBePreserved, "$.spec.managedServer[?(@.serverName=='" + name + "')]");
    }
  }

  private void preserve(Map<String, Object> toBePreserved, String key, Map<String, Object> value) {
    toBePreserved.compute(key, (k, v) -> {
      if (v == null) {
        v = new TreeMap<String, Object>();
      }
      ((Map<String, Object>) v).putAll(value);
      return v;
    });
  }

  private void preserve(Map<String, Object> domain, Map<String, Object> toBePreserved, String apiVersion)
      throws IOException {
    if (!toBePreserved.isEmpty() && API_VERSION_V8.equals(apiVersion) && !API_VERSION_V8.equals(targetAPIVersion)) {
      Map<String, Object> meta = getMetadata(domain);
      Map<String, Object> annotations = (Map<String, Object>) meta.computeIfAbsent(
          "annotations", k -> new LinkedHashMap<>());
      annotations.put("weblogic.v8.preserved", new ObjectMapper().writeValueAsString(toBePreserved));
    }
  }

  @SuppressWarnings("java:S112")
  private void restore(Map<String, Object> domain) {
    if (API_VERSION_V8.equals(targetAPIVersion)) {
      Optional.ofNullable(getMetadata(domain))
          .map(meta -> (Map<String, Object>) meta.get("annotations"))
          .map(meta -> (String) meta.remove("weblogic.v8.preserved"))
          .ifPresent(labelValue -> {
            try {
              restore(domain, new ObjectMapper().readValue(labelValue, new TypeReference<>(){}));
            } catch (JsonProcessingException e) {
              throw new RuntimeException(e);
            }
          });
    }
  }

  private void restore(Map<String, Object> domain, Map<String, Object> toBeRestored) {
    if (toBeRestored != null && !toBeRestored.isEmpty()) {
      ReadContext context = JsonPath.parse(domain);
      toBeRestored.forEach((key, value) -> {
        JsonPath path = JsonPath.compile(key);
        Optional.of(read(context, path)).map(List::stream)
                .ifPresent(stream -> stream.forEach(item -> item.putAll((Map<String, Object>) value)));
      });
    }
  }

  private List<Map<String, Object>> read(ReadContext context, JsonPath path) {
    try {
      Object value = context.read(path);
      if (value instanceof List) {
        return (List<Map<String, Object>>) value;
      }

      return List.of((Map<String, Object>) value);
    } catch (JsonPathException pathException) {
      return Collections.emptyList();
    }
  }

  private void integrateClusters(Map<String, Object> spec, ResourceLookup resourceLookup) {
    if (API_VERSION_V8.equals(targetAPIVersion)) {
      List<Map<String, Object>> existing = (List<Map<String, Object>>) spec.remove(CLUSTERS);
      if (existing != null) {
        List<Map<String, Object>> clusterResources = Optional.ofNullable(resourceLookup)
            .map(ResourceLookup::listClusters).orElse(Collections.emptyList());
        List<Map<String, Object>> clusters = new ArrayList<>();
        for (Map<String, Object> reference : existing) {
          String name = (String) reference.get(NAME);
          if (name != null) {
            clusterResources.stream().filter(c -> nameMatches(c, name)).findFirst().ifPresent(c -> {
              Map<String, Object> clusterSpec = getSpec(c);
              Map<String, Object> cluster = new LinkedHashMap<>();
              if (!clusterSpec.containsKey(CLUSTER_NAME)) {
                cluster.put(CLUSTER_NAME, name);
              }
              cluster.putAll(clusterSpec);
              clusters.add(cluster);
            });
          }
        }
        if (!clusters.isEmpty()) {
          spec.put(CLUSTERS, clusters);
        }
      }
    }
  }

  private boolean nameMatches(Map<String, Object> cluster, String name) {
    Map<String, Object> clusterMeta = getMetadata(cluster);
    return clusterMeta != null && name.equals(clusterMeta.get("name"));
  }

  private void generateClusters(Map<String, Object> domain, List<Map<String, Object>> clusters, String apiVersion) {
    if (API_VERSION_V8.equals(apiVersion) && !API_VERSION_V8.equals(targetAPIVersion)) {
      Map<String, Object> spec = getSpec(domain);
      List<Map<String, Object>> existing = (List<Map<String, Object>>) spec.remove(CLUSTERS);
      if (existing != null) {
        Map<String, Object> domainMeta = getMetadata(domain);
        List<Map<String, Object>> clusterReferences = new ArrayList<>();
        for (Map<String, Object> c : existing) {
          Map<String, Object> genCluster = generateCluster(domainMeta, c);
          clusters.add(genCluster);
          clusterReferences.add(generateClusterReference(genCluster));
        }
        if (!clusterReferences.isEmpty()) {
          spec.put(CLUSTERS, clusterReferences);
        }
      }
    }
  }

  public static String toDns1123LegalName(String value) {
    return value.toLowerCase().replace('_', '-');
  }

  private Map<String, Object> generateCluster(Map<String, Object> domainMeta,
                                              Map<String, Object> existingCluster) {
    Map<String, Object> cluster = new LinkedHashMap<>();
    cluster.put(API_VERSION, "weblogic.oracle/v1");
    cluster.put("kind", "Cluster");
    Map<String, Object> clusterMeta = new LinkedHashMap<>();
    clusterMeta.put("name", domainMeta.get("name") + "-"
        + toDns1123LegalName((String) existingCluster.get(CLUSTER_NAME)));
    clusterMeta.put(NAMESPACE, domainMeta.get(NAMESPACE));
    Map<String, Object> labels = new LinkedHashMap<>();
    labels.put("weblogic.createdByOperator", "true");
    clusterMeta.put("labels", labels);
    cluster.put(METADATA, clusterMeta);
    cluster.put(SPEC, existingCluster);
    return cluster;
  }

  private Map<String, Object> generateClusterReference(Map<String, Object> clusterResource) {
    Map<String, Object> reference = new LinkedHashMap<>();
    reference.put("name", getMetadata(clusterResource).get("name"));
    return reference;
  }
}

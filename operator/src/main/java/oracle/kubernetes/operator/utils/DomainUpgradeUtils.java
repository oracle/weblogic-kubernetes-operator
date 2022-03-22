// Copyright (c) 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.utils;

import java.io.File;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.StringJoiner;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import oracle.kubernetes.operator.helpers.KubernetesUtils;
import oracle.kubernetes.operator.logging.LoggingFacade;
import oracle.kubernetes.operator.logging.LoggingFactory;
import oracle.kubernetes.operator.rest.model.ConversionRequest;
import oracle.kubernetes.operator.rest.model.ConversionResponse;
import oracle.kubernetes.operator.rest.model.ConversionReviewModel;
import oracle.kubernetes.operator.rest.model.GsonOffsetDateTime;
import oracle.kubernetes.operator.rest.model.Result;
import oracle.kubernetes.weblogic.domain.model.AuxiliaryImageEnvVars;
import oracle.kubernetes.weblogic.domain.model.Domain;
import org.jetbrains.annotations.NotNull;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import static oracle.kubernetes.operator.ProcessingConstants.COMPATIBILITY_MODE;
import static oracle.kubernetes.operator.helpers.LegalNames.toDns1123LegalName;
import static oracle.kubernetes.weblogic.domain.model.AuxiliaryImage.AUXILIARY_IMAGE_DEFAULT_INIT_CONTAINER_COMMAND;
import static oracle.kubernetes.weblogic.domain.model.AuxiliaryImage.AUXILIARY_IMAGE_INIT_CONTAINER_NAME_PREFIX;
import static oracle.kubernetes.weblogic.domain.model.AuxiliaryImage.AUXILIARY_IMAGE_INIT_CONTAINER_WRAPPER_SCRIPT;
import static oracle.kubernetes.weblogic.domain.model.AuxiliaryImage.AUXILIARY_IMAGE_TARGET_PATH;
import static oracle.kubernetes.weblogic.domain.model.AuxiliaryImage.AUXILIARY_IMAGE_VOLUME_NAME_PREFIX;

@SuppressWarnings({"Convert2MethodRef", "unchecked", "rawtypes"})
public class DomainUpgradeUtils {
  private static final LoggingFacade LOGGER = LoggingFactory.getLogger("Webhook", "Operator");

  public static final String API_VERSION_V9 = "weblogic.oracle/v9";
  public static final String API_VERSION_V8 = "weblogic.oracle/v8";
  private AtomicInteger containerIndex = new AtomicInteger(0);

  public ConversionReviewModel readConversionReview(String resourceName) {
    return getGsonBuilder().fromJson(resourceName, ConversionReviewModel.class);
  }

  /**
   * Create the conversion review response.
   * @param conversionRequest The request to be converted.
   * @return ConversionResponse The response to the conversion request.
   */
  public ConversionResponse createConversionResponse(ConversionRequest conversionRequest) {
    List<Object> convertedDomains = new ArrayList<>();

    conversionRequest.getObjects()
            .forEach(domain -> convertedDomains.add(
                    convertDomain((Map<String,Object>) domain, conversionRequest.getDesiredAPIVersion())));

    return new ConversionResponse()
            .uid(conversionRequest.getUid())
            .result(new Result().status("Success"))
            .convertedObjects(convertedDomains);
  }

  public String writeConversionReview(ConversionReviewModel conversionReviewModel) {
    return getGsonBuilder().toJson(conversionReviewModel, ConversionReviewModel.class);
  }

  /**
   * Convert the domain to desired API version.
   * @param domain Domain to be converted.
   * @return Domain The converted domain.
   */
  public Object convertDomain(Map<String, Object> domain, String desiredAPIVersion) {
    Map<String, Object> spec = getSpec(domain);
    if (spec == null) {
      return domain;
    }
    LOGGER.fine("Converting domain " + domain + " to " + desiredAPIVersion + " apiVersion.");

    String apiVersion = (String)domain.get("apiVersion");
    adjustAdminPortForwardingDefault(spec, apiVersion);
    convertLegacyAuxiliaryImages(spec);
    removeProgressingConditionFromDomainStatus(domain);
    domain.put("apiVersion", desiredAPIVersion);
    LOGGER.fine("Converted domain with " + desiredAPIVersion + " apiVersion is " + domain);
    return domain;
  }

  /**
   * Convert the domain to desired API version.
   * @param domainYaml Domain yaml to be converted.
   * @return Domain String containing the converted domain yaml.
   */
  public String convertDomain(String domainYaml) {
    return convertDomain(domainYaml, API_VERSION_V9);
  }

  /**
   * Convert the domain to desired API version.
   * @param domainYaml Domain yaml to be converted.
   * @param desiredApiVersion Desired API version.
   * @return Domain String containing the domain yaml.
   */
  public String convertDomain(String domainYaml, String desiredApiVersion) {
    DumperOptions options = new DumperOptions();
    options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
    options.setPrettyFlow(true);
    Yaml yaml = new Yaml(options);
    Object domain = yaml.load(domainYaml);
    return yaml.dump(convertDomain((Map<String, Object>) domain, desiredApiVersion));
  }

  private void adjustAdminPortForwardingDefault(Map<String, Object> spec, String apiVersion) {
    Map<String, Object> adminServerSpec = (Map<String, Object>) spec.get("adminServer");
    Boolean adminChannelPortForwardingEnabled = (Boolean) Optional.ofNullable(adminServerSpec)
            .map(as -> as.get("adminChannelPortForwardingEnabled")).orElse(null);
    if ((adminChannelPortForwardingEnabled == null) && (API_VERSION_V8.equals(apiVersion))) {
      Optional.ofNullable(adminServerSpec).map(as -> as.put("adminChannelPortForwardingEnabled", false));
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

  private void removeProgressingConditionFromDomainStatus(Map<String, Object> domain) {
    Map<String, Object> domainStatus = (Map<String, Object>) domain.get("status");
    List<Object> conditions = (List) Optional.ofNullable(domainStatus).map(status -> status.get("conditions"))
            .orElse(null);
    Optional.ofNullable(conditions).ifPresent(x -> x.removeIf(cond -> hasType((Map<String, Object>)cond)));
  }

  private boolean hasType(Map<String, Object> condition) {
    return Optional.ofNullable(condition.get("type")).map(type -> "Progressing".equals(type)).orElse(false);
  }

  private List<Object> getAuxiliaryImageVolumes(Map<String, Object> spec) {
    return (List<Object>) spec.get("auxiliaryImageVolumes");
  }

  private Map<String, Object> getAdminServer(Map<String, Object> spec) {
    return (Map<String, Object>) spec.get("adminServer");
  }

  private List<Object> getClusters(Map<String, Object> spec) {
    return (List<Object>) spec.get("clusters");
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
    return (Map<String, Object>)domain.get("spec");
  }

  private void addInitContainersVolumeAndMountsToServerPod(Map<String, Object> serverPod, List<Object> auxiliaryImages,
                                                                         List<Object> auxiliaryImageVolumes) {
    addEmptyDirVolume(serverPod, auxiliaryImageVolumes);
    List<Object> initContainers = new ArrayList<>();
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
      existingVolumes.addAll(Collections.singletonList(createEmptyDirVolume(auxiliaryImageVolume)));
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
    if (Optional.ofNullable(serverPod.get("volumeMounts")).map(volumeMounts -> ((List)volumeMounts).stream().noneMatch(
          volumeMount -> hasMatchingVolumeMountName(volumeMount, auxiliaryImage))).orElse(true)) {
      serverPod.put("volumeMounts", Collections.singletonList(getVolumeMount(auxiliaryImage, mountPath)));
    }
  }

  private Object getVolumeMount(Map auxiliaryImage, String mountPath) {
    Map<String, String> volumeMount = new LinkedHashMap<>();
    volumeMount.put("name", getDNS1123auxiliaryImageVolumeName(auxiliaryImage.get("volume")));
    volumeMount.put("mountPath", mountPath);
    return volumeMount;
  }

  private Map<String, String> getScriptsVolumeMount() {
    Map<String, String> volumeMount = new LinkedHashMap<>();
    volumeMount.put("name", oracle.kubernetes.operator.helpers.StepContextConstants.SCRIPTS_VOLUME);
    volumeMount.put("mountPath", oracle.kubernetes.operator.helpers.StepContextConstants.SCRIPTS_MOUNTS_PATH);
    return volumeMount;
  }

  private boolean hasMatchingVolumeMountName(Object volumeMount, Map<String, Object> auxiliaryImage) {
    return getDNS1123auxiliaryImageVolumeName(auxiliaryImage.get("volume")).equals(((Map)volumeMount).get("name"));
  }

  public static String getDNS1123auxiliaryImageVolumeName(Object name) {
    return toDns1123LegalName(COMPATIBILITY_MODE + AUXILIARY_IMAGE_VOLUME_NAME_PREFIX + name);
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
          .map(aiv -> (String)((Map<String, Object>) aiv).get("mountPath")).orElse(null);
  }

  private boolean hasMatchingVolumeName(Map<String, Object> auxiliaryImageVolume, Map<String, Object> auxiliaryImage) {
    return Optional.ofNullable(auxiliaryImage.get("volume"))
            .map(v -> v.equals(auxiliaryImageVolume.get("name"))).orElse(false);
  }

  private Map<String, Object> createInitContainerForAuxiliaryImage(Map<String, Object> auxiliaryImage, int index,
                                                   List<Object> auxiliaryImageVolumes) {
    Map<String, Object> container = new LinkedHashMap<>();
    container.put("name", COMPATIBILITY_MODE + getName(index));
    container.put("image", auxiliaryImage.get("image"));
    container.put("command", Arrays.asList(AUXILIARY_IMAGE_INIT_CONTAINER_WRAPPER_SCRIPT));
    container.put("imagePullPolicy", getImagePullPolicy(auxiliaryImage));
    container.put("env", createEnv(auxiliaryImage, auxiliaryImageVolumes, getName(index)));
    container.put("volumeMounts", Arrays.asList(getVolumeMount(auxiliaryImage, AUXILIARY_IMAGE_TARGET_PATH),
            getScriptsVolumeMount()));
    return container;
  }

  private Object getImagePullPolicy(Map<String, Object> auxiliaryImage) {
    return Optional.ofNullable(auxiliaryImage.get("imagePullPolicy")).orElse(
            KubernetesUtils.getInferredImagePullPolicy((String) auxiliaryImage.get("image")));
  }

  private List<Object> createEnv(Map<String, Object> auxiliaryImage, List<Object> auxiliaryImageVolumes, String name) {
    List<Object> vars = new ArrayList<>();
    addEnvVar(vars, AuxiliaryImageEnvVars.AUXILIARY_IMAGE_PATH, getMountPath(auxiliaryImage, auxiliaryImageVolumes));
    addEnvVar(vars, AuxiliaryImageEnvVars.AUXILIARY_IMAGE_TARGET_PATH, AUXILIARY_IMAGE_TARGET_PATH);
    addEnvVar(vars, AuxiliaryImageEnvVars.AUXILIARY_IMAGE_COMMAND, getCommand(auxiliaryImage));
    addEnvVar(vars, AuxiliaryImageEnvVars.AUXILIARY_IMAGE_CONTAINER_IMAGE, (String)auxiliaryImage.get("image"));
    addEnvVar(vars, AuxiliaryImageEnvVars.AUXILIARY_IMAGE_CONTAINER_NAME, COMPATIBILITY_MODE + name);
    return vars;
  }

  private String getCommand(Map<String, Object> auxiliaryImage) {
    return Optional.ofNullable((String) auxiliaryImage.get("command"))
            .orElse(AUXILIARY_IMAGE_DEFAULT_INIT_CONTAINER_COMMAND);
  }

  private String getName(int index) {
    return AUXILIARY_IMAGE_INIT_CONTAINER_NAME_PREFIX + (index + 1);
  }

  private void addEnvVar(List<Object> vars, String name, String value) {
    Map<String, String> envVar = new LinkedHashMap<>();
    envVar.put("name", name);
    envVar.put("value", value);
    vars.add(envVar);
  }

  @NotNull
  Gson getGsonBuilder() {
    return new GsonBuilder()
            .registerTypeAdapter(OffsetDateTime.class, new GsonOffsetDateTime())
            .create();
  }

  public Domain readDomain(String resourceName) throws IOException {
    return readDomain(resourceName, false);
  }

  public Domain readDomain(String resourceName, boolean isFile) throws IOException {
    String json = jsonFromYaml(resourceName, isFile);
    return getGsonBuilder().fromJson(json, Domain.class);
  }

  private String jsonFromYaml(String resourceName, boolean isFile) throws IOException {
    ObjectMapper yamlReader = new ObjectMapper(new YAMLFactory());
    Object obj;
    if (isFile) {
      obj = yamlReader.readValue(new File(resourceName), Object.class);
    } else {
      obj = yamlReader.readValue(resourceName, Object.class);
    }

    ObjectMapper jsonWriter = new ObjectMapper();
    return jsonWriter.writeValueAsString(obj);
  }

  public String writeDomain(Domain domain) throws IOException {
    return writeDomain(domain, null, false);
  }

  public String writeDomain(Domain domain, String resourceName) throws IOException {
    return writeDomain(domain, resourceName, true);
  }

  public String writeDomain(Domain domain, String resourceName, boolean toFile) throws IOException {
    String jsonInString = getGsonBuilder().toJson(domain);
    return jsonToYaml(resourceName, jsonInString, toFile);
  }

  private String jsonToYaml(String resourceName, String jsonString, boolean toFile) throws IOException {
    String yamlStr;
    JsonNode jsonNodeTree = new ObjectMapper().readTree(jsonString);
    if (toFile) {
      new YAMLMapper().writeValue(new File(resourceName),jsonNodeTree);
      yamlStr = jsonNodeTree.asText();
    } else {
      yamlStr = new YAMLMapper().writeValueAsString(jsonNodeTree);
    }
    return yamlStr;
  }
}

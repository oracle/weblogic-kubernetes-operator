// Copyright (c) 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.utils;

import java.io.File;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.StringJoiner;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import io.kubernetes.client.custom.Quantity;
import io.kubernetes.client.openapi.models.V1Container;
import io.kubernetes.client.openapi.models.V1EmptyDirVolumeSource;
import io.kubernetes.client.openapi.models.V1EnvVar;
import io.kubernetes.client.openapi.models.V1Volume;
import io.kubernetes.client.openapi.models.V1VolumeMount;
import oracle.kubernetes.operator.rest.model.ConversionRequest;
import oracle.kubernetes.operator.rest.model.ConversionResponse;
import oracle.kubernetes.operator.rest.model.ConversionReviewModel;
import oracle.kubernetes.operator.rest.model.GsonOffsetDateTime;
import oracle.kubernetes.operator.rest.model.Result;
import oracle.kubernetes.weblogic.domain.model.AdminServer;
import oracle.kubernetes.weblogic.domain.model.AuxiliaryImage;
import oracle.kubernetes.weblogic.domain.model.AuxiliaryImageEnvVars;
import oracle.kubernetes.weblogic.domain.model.AuxiliaryImageVolume;
import oracle.kubernetes.weblogic.domain.model.BaseConfiguration;
import oracle.kubernetes.weblogic.domain.model.Cluster;
import oracle.kubernetes.weblogic.domain.model.Domain;
import oracle.kubernetes.weblogic.domain.model.DomainSpec;
import oracle.kubernetes.weblogic.domain.model.ManagedServer;
import oracle.kubernetes.weblogic.domain.model.ServerPod;
import org.jetbrains.annotations.NotNull;

import static oracle.kubernetes.operator.helpers.LegalNames.toDns1123LegalName;
import static oracle.kubernetes.operator.helpers.StepContextConstants.SCRIPTS_MOUNTS_PATH;
import static oracle.kubernetes.operator.helpers.StepContextConstants.SCRIPTS_VOLUME;
import static oracle.kubernetes.weblogic.domain.model.AuxiliaryImage.AUXILIARY_IMAGE_INIT_CONTAINER_NAME_PREFIX;
import static oracle.kubernetes.weblogic.domain.model.AuxiliaryImage.AUXILIARY_IMAGE_INIT_CONTAINER_WRAPPER_SCRIPT;
import static oracle.kubernetes.weblogic.domain.model.AuxiliaryImage.AUXILIARY_IMAGE_TARGET_PATH;
import static oracle.kubernetes.weblogic.domain.model.AuxiliaryImage.AUXILIARY_IMAGE_VOLUME_NAME_PREFIX;

public class MigrationUtils {

  private Domain domain;
  private int containerIndex = 0;

  public ConversionReviewModel readConversionReview(String resourceName) {
    return getGsonBuilder().fromJson(resourceName, ConversionReviewModel.class);
  }

  /**
   * Create the conversion review response.
   * @param conversionRequest The request to be converted.
   * @return ConversionResponse The response to the conversion request.
   */
  public ConversionResponse createConversionResponse(ConversionRequest conversionRequest) {
    List<Domain> convertedDomains = new ArrayList<>();
    conversionRequest.getObjects()
            .forEach(domain -> convertedDomains.add(
                    convertDomain(domain)
                            .withApiVersion(conversionRequest.getDesiredAPIVersion())));

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
  public Domain convertDomain(Domain domain) {
    this.domain = domain;
    System.out.println("api version is " + domain.getApiVersion());
    addAuxiliaryImages(getSpec());
    addAuxiliaryImages(getAdminServer());
    getClusters().forEach(this::addAuxiliaryImages);
    getManagedServers().forEach(this::addAuxiliaryImages);

    return domain;
  }

  private void addAuxiliaryImages(BaseConfiguration spec) {
    Optional.ofNullable(spec).map(BaseConfiguration::getServerPod)
            .map(ServerPod::getAuxiliaryImages)
            .ifPresent(ais -> addInitContainersVolumeAndMounts(ais, spec.getServerPod()));
  }

  @NotNull
  DomainSpec getSpec() {
    return domain.getSpec();
  }

  private AdminServer getAdminServer() {
    return getSpec().getAdminServer();
  }

  private List<Cluster> getClusters() {
    return getSpec().getClusters();
  }

  private List<ManagedServer> getManagedServers() {
    return getSpec().getManagedServers();
  }

  private void addInitContainersVolumeAndMounts(List<AuxiliaryImage> auxiliaryImages, ServerPod serverPod) {
    System.out.println("DEBUG: aux image is " + auxiliaryImages);
    addEmptyDirVolume(serverPod, getAuxiliaryImageVolumes());
    for (int idx = 0; idx < auxiliaryImages.size(); idx++) {
      serverPod.addInitContainer(createInitContainerForAuxiliaryImage(auxiliaryImages.get(idx), containerIndex,
              getAuxiliaryImageVolumes()));
      containerIndex++;
    }

    auxiliaryImages.forEach(ai -> addVolumeMount(serverPod, ai, getAuxiliaryImageVolumes()));
    addAuxiliaryImageEnv(auxiliaryImages, serverPod);
  }

  private List<AuxiliaryImageVolume> getAuxiliaryImageVolumes() {
    return getSpec().getAuxiliaryImageVolumes();
  }

  private void addAuxiliaryImageEnv(List<AuxiliaryImage> auxiliaryImages, ServerPod serverPod) {
    Optional.ofNullable(auxiliaryImages).flatMap(ais ->
            Optional.ofNullable(getAuxiliaryImagePaths(ais, getAuxiliaryImageVolumes())))
            .ifPresent(c -> addEnvVar(serverPod.getEnv(), AuxiliaryImageEnvVars.AUXILIARY_IMAGE_PATHS, c));
  }

  private String getAuxiliaryImagePaths(List<AuxiliaryImage> auxiliaryImages,
                                 List<AuxiliaryImageVolume> auxiliaryImageVolumes) {
    return Optional.ofNullable(auxiliaryImages).map(
          aiList -> createauxiliaryImagePathsEnv(aiList, auxiliaryImageVolumes)).orElse(null);
  }

  private String createauxiliaryImagePathsEnv(List<AuxiliaryImage> auxiliaryImages,
                                     List<AuxiliaryImageVolume> auxiliaryImageVolumes) {
    StringJoiner auxiliaryImagePaths = new StringJoiner(",","","");
    auxiliaryImages.forEach(auxiliaryImage -> auxiliaryImagePaths.add(
          getMountPath(auxiliaryImage, auxiliaryImageVolumes)));
    return Arrays.stream(auxiliaryImagePaths.toString().split(Pattern.quote(","))).distinct()
          .filter(st -> !st.isEmpty()).collect(Collectors.joining(","));
  }

  private void addEmptyDirVolume(ServerPod serverPod, List<AuxiliaryImageVolume> auxiliaryImageVolumes) {
    Optional.ofNullable(auxiliaryImageVolumes).ifPresent(volumes -> volumes.forEach(auxiliaryImageVolume ->
          addVolumeIfMissing(serverPod, auxiliaryImageVolume)));
  }

  private void addVolumeIfMissing(ServerPod serverPod, AuxiliaryImageVolume auxiliaryImageVolume) {
    if (Optional.ofNullable(serverPod.getVolumes()).map(volumes -> volumes.stream().noneMatch(
          volume -> podHasMatchingVolumeName(volume, auxiliaryImageVolume))).orElse(true)) {
      serverPod.setVolumes(Arrays.asList(createEmptyDirVolume(auxiliaryImageVolume)));
    }
  }

  private boolean podHasMatchingVolumeName(V1Volume volume, AuxiliaryImageVolume auxiliaryImageVolume) {
    return volume.getName().equals(auxiliaryImageVolume.getName());
  }

  private V1Volume createEmptyDirVolume(AuxiliaryImageVolume auxiliaryImageVolume) {
    V1EmptyDirVolumeSource emptyDirVolumeSource = new V1EmptyDirVolumeSource();
    Optional.ofNullable(auxiliaryImageVolume.getMedium()).ifPresent(emptyDirVolumeSource::medium);
    Optional.ofNullable(auxiliaryImageVolume.getSizeLimit())
          .ifPresent(sl -> emptyDirVolumeSource.sizeLimit(Quantity.fromString((String) sl)));
    return new V1Volume()
          .name(getDNS1123auxiliaryImageVolumeName(auxiliaryImageVolume.getName())).emptyDir(emptyDirVolumeSource);
  }

  private void addVolumeMountIfMissing(ServerPod serverPod, AuxiliaryImage auxiliaryImage, String mountPath) {
    if (Optional.ofNullable(serverPod.getVolumeMounts()).map(volumeMounts -> volumeMounts.stream().noneMatch(
          volumeMount -> hasMatchingVolumeMountName(volumeMount, auxiliaryImage))).orElse(true)) {
      serverPod.setVolumeMounts(Arrays.asList(new V1VolumeMount().name(
              getDNS1123auxiliaryImageVolumeName(auxiliaryImage.getVolume())).mountPath(mountPath)));
    }
  }

  private boolean hasMatchingVolumeMountName(V1VolumeMount volumeMount, AuxiliaryImage auxiliaryImage) {
    return getDNS1123auxiliaryImageVolumeName(auxiliaryImage.getVolume()).equals(volumeMount.getName());
  }

  public static String getDNS1123auxiliaryImageVolumeName(String name) {
    return toDns1123LegalName(AUXILIARY_IMAGE_VOLUME_NAME_PREFIX + name);
  }

  private void addVolumeMount(ServerPod serverPod, AuxiliaryImage auxiliaryImage,
                           List<AuxiliaryImageVolume> auxiliaryImageVolumes) {
    Optional.ofNullable(getMountPath(auxiliaryImage,
          auxiliaryImageVolumes)).ifPresent(mountPath ->
          addVolumeMountIfMissing(serverPod, auxiliaryImage, mountPath));
  }


  private String getMountPath(AuxiliaryImage auxiliaryImage, List<AuxiliaryImageVolume> auxiliaryImageVolumes) {
    return auxiliaryImageVolumes.stream().filter(
          auxiliaryImageVolume -> hasMatchingVolumeName(auxiliaryImageVolume, auxiliaryImage)).findFirst()
          .map(AuxiliaryImageVolume::getMountPath).orElse(null);
  }

  private boolean hasMatchingVolumeName(AuxiliaryImageVolume auxiliaryImageVolume,
                                 AuxiliaryImage auxiliaryImage) {
    return Optional.ofNullable(auxiliaryImage.getVolume())
            .map(v -> v.equals(auxiliaryImageVolume.getName())).orElse(false);
  }

  private V1Container createInitContainerForAuxiliaryImage(AuxiliaryImage auxiliaryImage, int index,
                                              List<AuxiliaryImageVolume> auxiliaryImageVolumes) {
    return new V1Container().name(getName(index))
          .image(auxiliaryImage.getImage())
          .imagePullPolicy(auxiliaryImage.getImagePullPolicy())
          .command(Arrays.asList(AUXILIARY_IMAGE_INIT_CONTAINER_WRAPPER_SCRIPT, "webhook_generated"))
          .env(createEnv(auxiliaryImage, auxiliaryImageVolumes, getName(index)))
          .volumeMounts(Arrays.asList(
                new V1VolumeMount().name(getDNS1123auxiliaryImageVolumeName(auxiliaryImage.getVolume()))
                    .mountPath(AUXILIARY_IMAGE_TARGET_PATH),
                new V1VolumeMount().name(SCRIPTS_VOLUME).mountPath(SCRIPTS_MOUNTS_PATH)));
  }

  private List<V1EnvVar> createEnv(AuxiliaryImage auxiliaryImage,
                              List<AuxiliaryImageVolume> auxiliaryImageVolumes, String name) {
    List<V1EnvVar> vars = new ArrayList<>();
    addEnvVar(vars, AuxiliaryImageEnvVars.AUXILIARY_IMAGE_PATH, getMountPath(auxiliaryImage, auxiliaryImageVolumes));
    addEnvVar(vars, AuxiliaryImageEnvVars.AUXILIARY_IMAGE_TARGET_PATH, AUXILIARY_IMAGE_TARGET_PATH);
    addEnvVar(vars, AuxiliaryImageEnvVars.AUXILIARY_IMAGE_COMMAND, auxiliaryImage.getCommand());
    addEnvVar(vars, AuxiliaryImageEnvVars.AUXILIARY_IMAGE_CONTAINER_IMAGE, auxiliaryImage.getImage());
    addEnvVar(vars, AuxiliaryImageEnvVars.AUXILIARY_IMAGE_CONTAINER_NAME, name);
    return vars;
  }

  private String getName(int index) {
    return AUXILIARY_IMAGE_INIT_CONTAINER_NAME_PREFIX + (index + 1);
  }

  private void addEnvVar(List<V1EnvVar> vars, String name, String value) {
    vars.add(new V1EnvVar().name(name).value(value));
  }

  @NotNull
  Gson getGsonBuilder() {
    return new GsonBuilder()
            .registerTypeAdapter(OffsetDateTime.class, new GsonOffsetDateTime())
            //.registerTypeHierarchyAdapter(byte[].class, new GsonByteArrayToBase64())
            .create();
  }

  public Domain readDomain(String resourceName) throws IOException {
    return readDomain(resourceName, true);
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

  public void writeDomain(Domain domain, String resourceName) throws IOException {
    writeDomain(domain, resourceName, true);
  }

  public void writeDomain(Domain domain, String resourceName, boolean toFile) throws IOException {
    String jsonInString = getGsonBuilder().toJson(domain);
    jsonToYaml(resourceName, jsonInString, toFile);
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
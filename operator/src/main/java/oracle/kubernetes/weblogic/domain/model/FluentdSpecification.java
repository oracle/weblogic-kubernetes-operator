// Copyright (c) 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.weblogic.domain.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import javax.annotation.Nullable;

import io.kubernetes.client.openapi.models.V1EnvVar;
import io.kubernetes.client.openapi.models.V1ResourceRequirements;
import io.kubernetes.client.openapi.models.V1VolumeMount;
import jakarta.validation.Valid;
import oracle.kubernetes.common.ImagePullPolicy;
import oracle.kubernetes.common.utils.CommonUtils;
import oracle.kubernetes.json.Description;
import oracle.kubernetes.json.EnumClass;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;

import static oracle.kubernetes.operator.KubernetesConstants.DEFAULT_FLUENTD_IMAGE;

public class FluentdSpecification {

  @Description("The configuration configmap name for the Fluentd. If WebLogic Server instances "
      + "are already running and have the fluentd sidecar container, then changes to this field "
      + "will be propagated to the exporter without requiring the restart of the WebLogic Server instances.")
  private String configurationConfigMap;

  /**
   * The Fluentd sidecar image.
   */
  @Description(
        "The Fluentd container image name. Defaults to "
           + DEFAULT_FLUENTD_IMAGE)
  private String image;
  
  @Description(
      "The image pull policy for the Fluentd sidecar container image. "
          + "Legal values are Always, Never, and IfNotPresent. "
          + "Defaults to Always if image ends in :latest; IfNotPresent, otherwise.")
  @EnumClass(ImagePullPolicy.class)
  private String imagePullPolicy;

  @Valid
  @Description("A list of environment variables to set in the fluentd container. "
      + "See `kubectl explain pods.spec.containers.env`.")
  private List<V1EnvVar> env = new ArrayList<>();

  /**
   * Defines the requirements and limits for the fluentd container.
   */
  @Description("Memory and CPU minimum requirements and limits for the fluentd container. "
      + "See `kubectl explain pods.spec.containers.resources`.")
  private final V1ResourceRequirements resources =
      new V1ResourceRequirements().limits(new HashMap<>()).requests(new HashMap<>());

  @Description("Volume mounts for fluentd container")
  private List<V1VolumeMount> volumeMounts = new ArrayList<>();

  public List<V1EnvVar> getEnv() {
    return env;
  }

  public void setEnv(List<V1EnvVar> env) {
    this.env = env;
  }

  public V1ResourceRequirements getResources() {
    return resources;
  }

  public List<V1VolumeMount> getVolumeMounts() {
    return volumeMounts;
  }

  public void setVolumeMounts(List<V1VolumeMount> volumeMounts) {
    this.volumeMounts = volumeMounts;
  }

  public String getConfigurationConfigMap() {
    return configurationConfigMap;
  }

  void setConfigurationConfigMap(String configurationConfigMapMapName) {
    this.configurationConfigMap = configurationConfigMapMapName;
  }

  public String getImage() {
    return Optional.ofNullable(image).orElse(DEFAULT_FLUENTD_IMAGE);
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

  @Override
  public String toString() {
    return new ToStringBuilder(this)
          .append("configurationMap", configurationConfigMap)
          .append("image", image)
          .append("imagePullPolicy", imagePullPolicy)
          .append("env", env)
          .append("resources", resources)
          .append("volumeMounts", volumeMounts)
          .toString();
  }

  @Override
  public boolean equals(Object o) {
    return (this == o)
          || ((o instanceof FluentdSpecification) && equals((FluentdSpecification) o));
  }

  private boolean equals(FluentdSpecification that) {
    return new EqualsBuilder()
          .append(configurationConfigMap, that.configurationConfigMap)
          .append(image, that.image)
          .append(imagePullPolicy, that.imagePullPolicy)
          .append(env, that.env)
          .append(resources, that.resources)
          .append(volumeMounts, this.volumeMounts)
          .isEquals();
  }

  @Override
  public int hashCode() {
    return new HashCodeBuilder(17, 37)
          .append(configurationConfigMap)
          .append(image)
          .append(imagePullPolicy)
          .append(env)
          .append(resources)
          .append(volumeMounts)
          .toHashCode();
  }
}

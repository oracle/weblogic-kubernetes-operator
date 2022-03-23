// Copyright (c) 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.weblogic.domain.model;

import com.google.gson.Gson;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import javax.annotation.Nullable;

import oracle.kubernetes.json.Description;
import oracle.kubernetes.json.EnumClass;
import oracle.kubernetes.json.PreserveUnknown;
import oracle.kubernetes.operator.ImagePullPolicy;
import oracle.kubernetes.operator.helpers.KubernetesUtils;

import io.kubernetes.client.openapi.models.V1EnvVar;
import io.kubernetes.client.openapi.models.V1ResourceRequirements;
import jakarta.validation.Valid;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.yaml.snakeyaml.Yaml;

import static oracle.kubernetes.operator.KubernetesConstants.DEFAULT_EXPORTER_IMAGE;
import static oracle.kubernetes.operator.KubernetesConstants.DEFAULT_FLUENTD_IMAGE;

public class FluentdSpecification {

  @Description("The configuration for the Fluentd. If WebLogic Server instances "
      + "are already running and have the fluentd sidecar container, then changes to this field "
      + "will be propagated to the exporter without requiring the restart of the WebLogic Server instances.")
  @PreserveUnknown
  private Map<String,Object> configuration;

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

  public List<V1EnvVar> getEnv() {
    return env;
  }

  public void setEnv(List<V1EnvVar> env) {
    this.env = env;
  }

  public V1ResourceRequirements getResources() {
    return resources;
  }

  public String getFluentdMountPath() {
    return fluentdMountPath;
  }

  public void setFluentdMountPath(String fluentdMountPath) {
    this.fluentdMountPath = fluentdMountPath;
  }

  @Description("Volume mount path for fluentd storage. Default: /scratch")
  private String fluentdMountPath;

  public FluentdConfiguration getConfiguration() {
    return Optional.ofNullable(configuration).map(this::toJson).map(this::toConfiguration).orElse(null);
  }

  private String toJson(Object object) {
    return new Gson().toJson(object);
  }

  private FluentdConfiguration toConfiguration(String string) {
    return new Gson().fromJson(string, FluentdConfiguration.class);
  }

  void createConfiguration(String yaml) {
    configuration = Optional.ofNullable(yaml).map(this::parse).orElse(null);
  }

  private Map<String, Object> parse(String yaml) {
    return new Yaml().load(yaml);
  }

  String getImage() {
    return Optional.ofNullable(image).orElse(DEFAULT_FLUENTD_IMAGE);
  }

  void setImage(@Nullable String image) {
    this.image = image;
  }

  String getImagePullPolicy() {
    return Optional.ofNullable(imagePullPolicy).orElse(KubernetesUtils.getInferredImagePullPolicy(getImage()));
  }

  void setImagePullPolicy(@Nullable String imagePullPolicy) {
    this.imagePullPolicy = imagePullPolicy;
  }

  @Override
  public String toString() {
    return new ToStringBuilder(this)
          .append("configuration", configuration)
          .append("image", image)
          .append("imagePullPolicy", imagePullPolicy)
          .append("env", env)
          .append("resources", resources)
          .append("fluentdMountPath", fluentdMountPath)
          .toString();
  }

  @Override
  public boolean equals(Object o) {
    return (this == o)
          || ((o instanceof FluentdSpecification) && equals((FluentdSpecification) o));
  }

  private boolean equals(FluentdSpecification that) {
    return new EqualsBuilder()
          .append(configuration, that.configuration)
          .append(image, that.image)
          .append(imagePullPolicy, that.imagePullPolicy)
          .append(env, that.env)
          .append(resources, that.resources)
          .append(fluentdMountPath, this.fluentdMountPath)
          .isEquals();
  }

  @Override
  public int hashCode() {
    return new HashCodeBuilder(17, 37)
          .append(configuration)
          .append(image)
          .append(imagePullPolicy)
          .append(env)
          .append(resources)
          .append(fluentdMountPath)
          .toHashCode();
  }
}

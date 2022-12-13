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
import oracle.kubernetes.common.utils.CommonUtils;
import oracle.kubernetes.json.Default;
import oracle.kubernetes.json.Description;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;

import static oracle.kubernetes.operator.KubernetesConstants.DEFAULT_FLUENTD_IMAGE;

public class FluentdSpecification {

  @Description("The fluentd configuration text, specify your own custom fluentd configuration.")
  private String fluentdConfiguration;

  /**
   * The Fluentd sidecar image.
   */
  @Description(
        "The Fluentd container image name. Defaults to "
           + DEFAULT_FLUENTD_IMAGE)
  @Default(strDefault = DEFAULT_FLUENTD_IMAGE)
  private String image;
  
  @Description(
      "The image pull policy for the Fluentd sidecar container image. "
          + "Legal values are Always, Never, and IfNotPresent. "
          + "Defaults to Always if image ends in :latest; IfNotPresent, otherwise.")
  private String imagePullPolicy;

  @Description(
      "(Optional) The Fluentd sidecar container spec's args. "
          + "Default is: [ -c, /etc/fluentd.conf ] if not specified")
  private List<String> containerArgs;

  @Description(
      "(Optional) The Fluentd sidecar container spec's command. Default is not set if not specified")
  private List<String> containerCommand;

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
  private final List<V1VolumeMount> volumeMounts = new ArrayList<>();

  @Description("Fluentd elastic search credentials. A Kubernetes secret in the same namespace of the domain."
      + " It must contains 4 keys: elasticsearchhost - ElasticSearch Host Service Address,"
      + " elasticsearchport - Elastic Search Service Port,"
      + " elasticsearchuser - Elastic Search Service User Name,"
      + " elasticsearchpassword - Elastic Search User Password")
  private String elasticSearchCredentials;

  @Description("Fluentd will watch introspector logs")
  private Boolean watchIntrospectorLogs = true;

  public List<V1EnvVar> getEnv() {
    return env;
  }

  public void setEnv(List<V1EnvVar> env) {
    this.env = env;
  }

  public List<V1VolumeMount> getVolumeMounts() {
    return volumeMounts;
  }

  public void setVolumeMounts(List<V1VolumeMount> volumeMounts) {
    this.volumeMounts.addAll(volumeMounts);
  }

  public String getFluentdConfiguration() {
    return fluentdConfiguration;
  }

  public void setFluentdConfiguration(String configurationConfigMapMapName) {
    this.fluentdConfiguration = configurationConfigMapMapName;
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

  @Nullable
  public List<String> getContainerArgs() {
    return containerArgs;
  }

  public void setContainerArgs(@Nullable List<String> containerArgs) {
    this.containerArgs = containerArgs;
  }

  @Nullable
  public List<String> getContainerCommand() {
    return containerCommand;
  }

  public void setContainerCommand(@Nullable  List<String> containerCommand) {
    this.containerCommand = containerCommand;
  }

  public Boolean getWatchIntrospectorLogs() {
    return watchIntrospectorLogs;
  }

  public void setWatchIntrospectorLogs(Boolean watchIntrospectorLogs) {
    this.watchIntrospectorLogs = watchIntrospectorLogs;
  }

  @Nullable
  public String getElasticSearchCredentials() {
    return elasticSearchCredentials;
  }

  public void setElasticSearchCredentials(@Nullable String elasticSearchCredentials) {
    this.elasticSearchCredentials = elasticSearchCredentials;
  }

  public V1ResourceRequirements getResources() {
    return resources;
  }

  @Override
  public String toString() {
    return new ToStringBuilder(this)
          .append("fluentdConfiguration", fluentdConfiguration)
          .append("image", image)
          .append("imagePullPolicy", imagePullPolicy)
          .append("env", env)
          .append("resources", resources)
          .append("volumeMounts", volumeMounts)
          .append("watchIntrospectorLogs", watchIntrospectorLogs)
          .append("elasticSearchCredentials", elasticSearchCredentials)
          .append("containerArgs", containerArgs)
          .append("containerCommand", containerCommand)
          .toString();
  }

  @Override
  public boolean equals(Object o) {
    return (this == o)
          || ((o instanceof FluentdSpecification) && equals((FluentdSpecification) o));
  }

  private boolean equals(FluentdSpecification that) {
    return new EqualsBuilder()
          .append(fluentdConfiguration, that.fluentdConfiguration)
          .append(image, that.image)
          .append(imagePullPolicy, that.imagePullPolicy)
          .append(env, that.env)
          .append(resources, that.resources)
          .append(volumeMounts, that.volumeMounts)
          .append(watchIntrospectorLogs, that.watchIntrospectorLogs)
          .append(elasticSearchCredentials, that.elasticSearchCredentials)
          .append(containerArgs, that.containerArgs)
          .append(containerCommand, that.containerCommand)
          .isEquals();
  }

  @Override
  public int hashCode() {
    return new HashCodeBuilder(17, 37)
          .append(fluentdConfiguration)
          .append(image)
          .append(imagePullPolicy)
          .append(env)
          .append(resources)
          .append(volumeMounts)
          .append(watchIntrospectorLogs)
          .append(elasticSearchCredentials)
          .append(containerArgs)
          .append(containerCommand)
        .toHashCode();
  }
}

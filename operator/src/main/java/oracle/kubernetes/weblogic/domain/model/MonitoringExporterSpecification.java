// Copyright (c) 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.weblogic.domain.model;

import java.util.Map;
import java.util.Optional;
import javax.annotation.Nullable;

import com.google.gson.Gson;
import oracle.kubernetes.json.Description;
import oracle.kubernetes.json.EnumClass;
import oracle.kubernetes.json.PreserveUnknown;
import oracle.kubernetes.operator.ImagePullPolicy;
import oracle.kubernetes.operator.helpers.KubernetesUtils;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.yaml.snakeyaml.Yaml;

import static oracle.kubernetes.operator.KubernetesConstants.DEFAULT_EXPORTER_IMAGE;
import static oracle.kubernetes.operator.KubernetesConstants.DEFAULT_EXPORTER_SIDECAR_PORT;

public class MonitoringExporterSpecification {

  @Description("The configuration for the WebLogic Monitoring Exporter. If WebLogic Server instances "
      + "are already running and have the monitoring exporter sidecar container, then changes to this field will "
      + "be propagated to the exporter without requiring the restart of the WebLogic Server instances.")
  @PreserveUnknown
  private Map<String,Object> configuration;

  /**
   * The Monitoring Exporter sidecar image.
   */
  @Description(
        "The WebLogic Monitoring Exporter sidecar container image name. Defaults to "
           + DEFAULT_EXPORTER_IMAGE)
  private String image;
  
  @Description(
      "The image pull policy for the WebLogic Monitoring Exporter sidecar container image. "
          + "Legal values are Always, Never, and IfNotPresent. "
          + "Defaults to Always if image ends in :latest; IfNotPresent, otherwise.")
  @EnumClass(ImagePullPolicy.class)
  private String imagePullPolicy;

  @Description(
      "The port exposed by the WebLogic Monitoring Exporter running in the sidecar container. "
          + "Defaults to 8080. The port value must not conflict with a port used by any WebLogic Server "
          + "instance, including the ports of built-in channels or network access points (NAPs).")
  private Integer port;

  /**
   * Computes the REST port. This port will be used by the
   * metrics exporter to query runtime data.
   */
  public int getRestPort() {
    return Optional.ofNullable(port).orElse(DEFAULT_EXPORTER_SIDECAR_PORT);
  }

  public MonitoringExporterConfiguration getConfiguration() {
    return Optional.ofNullable(configuration).map(this::toJson).map(this::toConfiguration).orElse(null);
  }

  private String toJson(Object object) {
    return new Gson().toJson(object);
  }

  private MonitoringExporterConfiguration toConfiguration(String string) {
    return new Gson().fromJson(string, MonitoringExporterConfiguration.class);
  }

  void createConfiguration(String yaml) {
    configuration = Optional.ofNullable(yaml).map(this::parse).orElse(null);
  }

  private Map<String, Object> parse(String yaml) {
    return new Yaml().load(yaml);
  }

  String getImage() {
    return Optional.ofNullable(image).orElse(DEFAULT_EXPORTER_IMAGE);
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

  Integer getPort() {
    return port;
  }

  void setPort(Integer port) {
    this.port = port;
  }

  @Override
  public String toString() {
    return new ToStringBuilder(this)
          .append("configuration", configuration)
          .append("image", image)
          .append("imagePullPolicy", imagePullPolicy)
          .append("port", port)
          .toString();
  }

  @Override
  public boolean equals(Object o) {
    return (this == o)
          || ((o instanceof MonitoringExporterSpecification) && equals((MonitoringExporterSpecification) o));
  }

  private boolean equals(MonitoringExporterSpecification that) {
    return new EqualsBuilder()
          .append(configuration, that.configuration)
          .append(image, that.image)
          .append(imagePullPolicy, that.imagePullPolicy)
          .append(port, that.port)
          .isEquals();
  }

  @Override
  public int hashCode() {
    return new HashCodeBuilder(17, 37)
          .append(configuration)
          .append(image)
          .append(imagePullPolicy)
          .append(port)
          .toHashCode();
  }
}

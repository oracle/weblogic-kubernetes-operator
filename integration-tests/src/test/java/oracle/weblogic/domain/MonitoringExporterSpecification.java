// Copyright (c) 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.domain;

import java.util.Map;
import java.util.Optional;

import com.google.gson.Gson;
import io.swagger.annotations.ApiModelProperty;
import oracle.weblogic.domain.MonitoringExporterConfiguration;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.yaml.snakeyaml.Yaml;


public class MonitoringExporterSpecification {

  @ApiModelProperty("The configuration for the WebLogic Monitoring Exporter sidecar. If specified, the operator will "
      + "deploy a sidecar alongside each server instance. See https://github.com/oracle/weblogic-monitoring-exporter")
  private Map<String,Object> configuration;

  /**
   * The Monitoring Exporter sidecar image.
   */
  @ApiModelProperty(
      "The WebLogic Monitoring Exporter sidecar image name.")
  private String image;

  @ApiModelProperty(
      "The image pull policy for the WebLogic Monitoring Exporter sidecar image. "
          + "Legal values are Always, Never, and IfNotPresent. ")
  private String imagePullPolicy;


  public MonitoringExporterConfiguration configuration() {
    return Optional.ofNullable(this.configuration).map(this::toJson).map(this::toConfiguration).orElse(null);
  }

  public MonitoringExporterSpecification configuration(String yaml) {
    this.configuration = Optional.ofNullable(yaml).map(this::parse).orElse(null);
    return this;
  }

  public String image() {
    return image;
  }

  public MonitoringExporterSpecification image(String image) {
    this.image = image;
    return this;
  }

  public String imagePullPolicy() {
    return imagePullPolicy;
  }

  public MonitoringExporterSpecification imagePullPolicy(String imagePullPolicy) {
    this.imagePullPolicy = imagePullPolicy;
    return this;
  }

  private String toJson(Object object) {
    return new Gson().toJson(object);
  }

  private MonitoringExporterConfiguration toConfiguration(String string) {
    return new Gson().fromJson(string, MonitoringExporterConfiguration.class);
  }

  private Map<String, Object> parse(String yaml) {
    return new Yaml().load(yaml);
  }



  @Override
  public String toString() {
    return new ToStringBuilder(this)
        .append("configuration", configuration)
        .append("image", image)
        .append("imagePullPolicy", imagePullPolicy)
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
        .isEquals();
  }

  @Override
  public int hashCode() {
    return new HashCodeBuilder(17, 37)
        .append(configuration)
        .append(image)
        .append(imagePullPolicy)
        .toHashCode();
  }
}

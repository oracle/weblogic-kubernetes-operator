// Copyright (c) 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.domain;

import java.util.ArrayList;
import java.util.List;

import io.swagger.annotations.ApiModelProperty;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;

public class Configuration {

  @ApiModelProperty("Model in image model files and properties.")
  private Model model;

  @ApiModelProperty("Configuration for OPSS security.")
  private Opss opss;

  @ApiModelProperty(
      "A list of names of the secrets for WebLogic configuration overrides or model. If this field is specified"
          + " it overrides the value of spec.configOverrideSecrets.")
  private List<String> secrets;

  @ApiModelProperty(
      "The name of the config map for WebLogic configuration overrides. If this field is specified"
          + " it overrides the value of spec.configOverrides.")
  private String overridesConfigMap;

  @ApiModelProperty(
      "The introspector job timeout value in seconds. If this field is specified"
          + " it overrides the Operator's config map data.introspectorJobActiveDeadlineSeconds value.")
  private Long introspectorJobActiveDeadlineSeconds;

  public Configuration model(Model model) {
    this.model = model;
    return this;
  }

  public Model model() {
    return model;
  }

  public Model getModel() {
    return model;
  }

  public void setModel(Model model) {
    this.model = model;
  }

  public Configuration opss(Opss opss) {
    this.opss = opss;
    return this;
  }

  public Opss opss() {
    return this.opss;
  }

  public Opss getOpss() {
    return opss;
  }

  public void setOpss(Opss opss) {
    this.opss = opss;
  }

  public Configuration secrets(List<String> secrets) {
    this.secrets = secrets;
    return this;
  }

  public List<String> secrets() {
    return secrets;
  }

  public Configuration addSecretsItem(String secretsItem) {
    if (secrets == null) {
      secrets = new ArrayList<>();
    }
    secrets.add(secretsItem);
    return this;
  }

  public List<String> getSecrets() {
    return secrets;
  }

  public void setSecrets(List<String> secrets) {
    this.secrets = secrets;
  }

  public Configuration overridesConfigMap(String overridesConfigMap) {
    this.overridesConfigMap = overridesConfigMap;
    return this;
  }

  public String overridesConfigMap() {
    return this.overridesConfigMap;
  }

  public String getOverridesConfigMap() {
    return overridesConfigMap;
  }

  public void setOverridesConfigMap(String overridesConfigMap) {
    this.overridesConfigMap = overridesConfigMap;
  }

  public Configuration introspectorJobActiveDeadlineSeconds(
      Long introspectorJobActiveDeadlineSeconds) {
    this.introspectorJobActiveDeadlineSeconds = introspectorJobActiveDeadlineSeconds;
    return this;
  }

  public Long introspectorJobActiveDeadlineSeconds() {
    return this.introspectorJobActiveDeadlineSeconds;
  }

  public Long getIntrospectorJobActiveDeadlineSeconds() {
    return introspectorJobActiveDeadlineSeconds;
  }

  public void setIntrospectorJobActiveDeadlineSeconds(Long introspectorJobActiveDeadlineSeconds) {
    this.introspectorJobActiveDeadlineSeconds = introspectorJobActiveDeadlineSeconds;
  }

  @Override
  public String toString() {
    ToStringBuilder builder =
        new ToStringBuilder(this)
            .append("model", model)
            .append("opss", opss)
            .append("secrets", secrets)
            .append("overridesConfigMap", overridesConfigMap)
            .append("introspectorJobActiveDeadlineSeconds", introspectorJobActiveDeadlineSeconds);

    return builder.toString();
  }

  @Override
  public int hashCode() {
    HashCodeBuilder builder =
        new HashCodeBuilder()
            .append(model)
            .append(opss)
            .append(secrets)
            .append(overridesConfigMap)
            .append(introspectorJobActiveDeadlineSeconds);

    return builder.toHashCode();
  }

  @Override
  public boolean equals(Object other) {
    if (this == other) {
      return true;
    }

    if (other == null || getClass() != other.getClass()) {
      return false;
    }
    Configuration rhs = (Configuration) other;
    EqualsBuilder builder =
        new EqualsBuilder()
            .append(model, rhs.model)
            .append(opss, rhs.opss)
            .append(secrets, rhs.secrets)
            .append(overridesConfigMap, rhs.overridesConfigMap)
            .append(introspectorJobActiveDeadlineSeconds, rhs.introspectorJobActiveDeadlineSeconds);

    return builder.isEquals();
  }
}

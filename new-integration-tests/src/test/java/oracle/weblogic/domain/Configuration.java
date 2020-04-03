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

  @ApiModelProperty("The name of the config map for WebLogic configuration overrides. If this field is specified"
          + " it overrides the value of spec.configOverrides.")
  private String overridesConfigMap;

  @ApiModelProperty("The introspector job timeout value in seconds. If this field is specified"
          + " it overrides the Operator's config map data.introspectorJobActiveDeadlineSeconds value.")
  private Long introspectorJobActiveDeadlineSeconds;

  public Configuration model(Model model) {
    this.model = model;
    return this;
  }

  public Model getModel() {
    return model;
  }

  public Configuration opss(Opss opss) {
    this.opss = opss;
    return this;
  }

  public Opss getOpss() {
    return this.opss;
  }

  public Configuration secrets(List<String> secrets) {
    this.secrets = secrets;
    return this;
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

  public Configuration overridesConfigMap(String overridesConfigMap) {
    this.overridesConfigMap = overridesConfigMap;
    return this;
  }

  public String getOverridesConfigMap() {
    return this.overridesConfigMap;
  }

  public Configuration introspectorJobActiveDeadlineSeconds(Long introspectorJobActiveDeadlineSeconds) {
    this.introspectorJobActiveDeadlineSeconds = introspectorJobActiveDeadlineSeconds;
    return this;
  }

  public Long getIntrospectorJobActiveDeadlineSeconds() {
    return this.introspectorJobActiveDeadlineSeconds;
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
    HashCodeBuilder builder = new HashCodeBuilder()
        .append(model).append(opss).append(secrets).append(overridesConfigMap)
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


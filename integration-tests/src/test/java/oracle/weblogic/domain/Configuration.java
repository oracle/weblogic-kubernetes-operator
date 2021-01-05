// Copyright (c) 2020, 2021, Oracle and/or its affiliates.
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

  @ApiModelProperty(
      "Determines how updated configuration overrides are distributed to already running WebLogic servers "
      + "following introspection when the domainHomeSourceType is PersistentVolume or Image.  Configuration overrides "
      + "are generated during introspection from secrets, the overrideConfigMap field, and WebLogic domain topology. "
      + "Legal values are DYNAMIC (the default) and ON_RESTART. See also introspectVersion.")
  private String overrideDistributionStrategy;

  @ApiModelProperty("Istio service mesh integration")
  private Istio istio;

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

  public Configuration overrideDistributionStrategy(String overrideDistributionStrategy) {
    this.overrideDistributionStrategy = overrideDistributionStrategy;
    return this;
  }

  public String getOverrideDistributionStrategy() {
    return overrideDistributionStrategy;
  }

  public void setOverrideDistributionStrategy(String overrideDistributionStrategy) {
    this.overrideDistributionStrategy = overrideDistributionStrategy;
  }

  /**
   * Adds item to secrets list.
   * @param secretsItem Secret
   * @return this
   */
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

  public Configuration istio(Istio istio) {
    this.istio = istio;
    return this;
  }

  public Istio istio() {
    return istio;
  }

  public Istio getIstio() {
    return istio;
  }

  public void setIstio(Istio istio) {
    this.istio = istio;
  }

  @Override
  public String toString() {
    ToStringBuilder builder =
        new ToStringBuilder(this)
            .append("model", model)
            .append("opss", opss)
            .append("secrets", secrets)
            .append("overridesConfigMap", overridesConfigMap)
            .append("introspectorJobActiveDeadlineSeconds", introspectorJobActiveDeadlineSeconds)
            .append("istio", istio);

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
            .append(introspectorJobActiveDeadlineSeconds)
            .append(istio);

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
            .append(introspectorJobActiveDeadlineSeconds, rhs.introspectorJobActiveDeadlineSeconds)
            .append(istio, rhs.istio);

    return builder.isEquals();
  }
}

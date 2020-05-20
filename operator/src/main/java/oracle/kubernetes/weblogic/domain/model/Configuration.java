// Copyright (c) 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.weblogic.domain.model;

import java.util.List;

import oracle.kubernetes.json.Description;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;


public class Configuration {

  @Description("Model in image model files and properties.")
  private Model model;

  @Description("Configuration for OPSS security.")
  private Opss opss;

  @Description(
      "A list of names of the secrets for WebLogic configuration overrides or model. If this field is specified"
          + " it overrides the value of spec.configOverrideSecrets.")
  private List<String> secrets;

  @Description("The name of the config map for WebLogic configuration overrides. If this field is specified"
          + " it overrides the value of spec.configOverrides.")
  private String overridesConfigMap;

  @Description("The introspector job timeout value in seconds. If this field is specified"
          + " it overrides the Operator's config map data.introspectorJobActiveDeadlineSeconds value.")
  private Long introspectorJobActiveDeadlineSeconds;

  @Description("Configuration for istio property.")
  private Istio istio;

  public Model getModel() {
    return model;
  }

  public void setModel(Model model) {
    this.model = model;
  }

  public Configuration withModel(Model model) {
    this.model = model;
    return this;
  }

  public Opss getOpss() {
    return this.opss;
  }

  public void setOpss(Opss opss) {
    this.opss = opss;
  }

  public Configuration withOpss(Opss opss) {
    this.opss = opss;
    return this;
  }

  public List<String> getSecrets() {
    return secrets;
  }

  public void setSecrets(List<String> secrets) {
    this.secrets = secrets;
  }

  public Configuration withSecrets(List<String> secrets) {
    this.secrets = secrets;
    return this;
  }

  public String getOverridesConfigMap() {
    return this.overridesConfigMap;
  }

  public void setOverridesConfigMap(String overridesConfigMap) {
    this.overridesConfigMap = overridesConfigMap;
  }

  public Configuration withOverridesConfigMap(String overridesConfigMap) {
    this.overridesConfigMap = overridesConfigMap;
    return this;
  }

  public Long getIntrospectorJobActiveDeadlineSeconds() {
    return this.introspectorJobActiveDeadlineSeconds;
  }

  public void setIntrospectorJobActiveDeadlineSeconds(Long introspectorJobActiveDeadlineSeconds) {
    this.introspectorJobActiveDeadlineSeconds = introspectorJobActiveDeadlineSeconds;
  }

  public Configuration withIntrospectorJobActiveDeadlineSeconds(Long introspectorJobActiveDeadlineSeconds) {
    this.introspectorJobActiveDeadlineSeconds = introspectorJobActiveDeadlineSeconds;
    return this;
  }

  public Istio getIstio() {
    return istio;
  }

  public void setIstio(Istio istio) {
    this.istio = istio;
  }

  public Configuration withIstio(Istio istio) {
    this.istio = istio;
    return this;
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
    HashCodeBuilder builder = new HashCodeBuilder()
        .append(model).append(opss).append(secrets).append(overridesConfigMap)
        .append(introspectorJobActiveDeadlineSeconds).append(istio);

    return builder.toHashCode();
  }

  @Override
  public boolean equals(Object other) {
    if (other == this) {
      return true;
    }
    if (!(other instanceof Configuration)) {
      return false;
    }

    Configuration rhs = ((Configuration) other);
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


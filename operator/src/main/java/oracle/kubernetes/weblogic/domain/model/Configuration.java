// Copyright (c) 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.weblogic.domain.model;

import java.util.List;

import oracle.kubernetes.json.Description;
import oracle.kubernetes.operator.OverrideDistributionStrategy;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;

public class Configuration {

  @Description("Model in image model files and properties.")
  private Model model;

  @Description("Settings for OPSS security.")
  private Opss opss;

  @Description(
      "A list of names of the Secrets for WebLogic configuration overrides or model. If this field is specified,"
          + " then the value of spec.configOverrideSecrets is ignored.")
  private List<String> secrets;

  @Description("The name of the ConfigMap for WebLogic configuration overrides. If this field is specified,"
          + " then the value of spec.configOverrides is ignored.")
  private String overridesConfigMap;

  @Description("The introspector job timeout value in seconds. If this field is specified, "
          + "then the operator's ConfigMap `data.introspectorJobActiveDeadlineSeconds` value is ignored. "
          + "Defaults to 120 seconds.")
  private Long introspectorJobActiveDeadlineSeconds;

  @Description(
      "Determines how updated configuration overrides are distributed to already running WebLogic Servers "
      + "following introspection when the domainHomeSourceType is PersistentVolume or Image.  Configuration overrides "
      + "are generated during introspection from Secrets, the `overrideConfigMap` field, and WebLogic domain topology. "
      + "Legal values are DYNAMIC, which means that the operator will distribute updated configuration overrides "
      + "dynamically to running servers, and ON_RESTART, which means that servers will use updated configuration "
      + "overrides only after the server's next restart. The selection of ON_RESTART will not cause servers to "
      + "restart when there are updated configuration overrides available. See also `introspectVersion`. "
      + "Defaults to DYNAMIC.")
  private OverrideDistributionStrategy overrideDistributionStrategy;

  @Description("The Istio service mesh integration settings.")
  private Istio istio;

  public Model getModel() {
    return model;
  }

  public void setModel(Model model) {
    this.model = model;
  }

  public Opss getOpss() {
    return this.opss;
  }

  public void setOpss(Opss opss) {
    this.opss = opss;
  }

  public List<String> getSecrets() {
    return secrets;
  }

  public void setSecrets(List<String> secrets) {
    this.secrets = secrets;
  }

  public String getOverridesConfigMap() {
    return this.overridesConfigMap;
  }

  public void setOverridesConfigMap(String overridesConfigMap) {
    this.overridesConfigMap = overridesConfigMap;
  }

  public Long getIntrospectorJobActiveDeadlineSeconds() {
    return this.introspectorJobActiveDeadlineSeconds;
  }

  public void setIntrospectorJobActiveDeadlineSeconds(Long introspectorJobActiveDeadlineSeconds) {
    this.introspectorJobActiveDeadlineSeconds = introspectorJobActiveDeadlineSeconds;
  }

  public void setOverrideDistributionStrategy(OverrideDistributionStrategy overrideDistributionStrategy) {
    this.overrideDistributionStrategy = overrideDistributionStrategy;
  }

  public OverrideDistributionStrategy getOverrideDistributionStrategy() {
    return overrideDistributionStrategy;
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
            .append("distributionStrategy", overrideDistributionStrategy)
            .append("overridesConfigMap", overridesConfigMap)
            .append("introspectorJobActiveDeadlineSeconds", introspectorJobActiveDeadlineSeconds)
            .append("istio", istio);

    return builder.toString();
  }

  @Override
  public int hashCode() {
    HashCodeBuilder builder = new HashCodeBuilder()
          .append(model)
          .append(opss)
          .append(secrets)
          .append(overrideDistributionStrategy)
          .append(overridesConfigMap)
          .append(introspectorJobActiveDeadlineSeconds)
          .append(istio);

    return builder.toHashCode();
  }

  @Override
  public boolean equals(Object other) {
    if (other == this) {
      return true;
    } else if (!(other instanceof Configuration)) {
      return false;
    }

    Configuration rhs = ((Configuration) other);
    EqualsBuilder builder =
        new EqualsBuilder()
            .append(model, rhs.model)
            .append(opss, rhs.opss)
            .append(secrets, rhs.secrets)
            .append(overrideDistributionStrategy, rhs.overrideDistributionStrategy)
            .append(overridesConfigMap, rhs.overridesConfigMap)
            .append(introspectorJobActiveDeadlineSeconds, rhs.introspectorJobActiveDeadlineSeconds)
            .append(istio, rhs.istio);

    return builder.isEquals();
  }
}


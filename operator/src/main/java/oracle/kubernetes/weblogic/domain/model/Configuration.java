// Copyright (c) 2020, 2023, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.weblogic.domain.model;

import java.util.List;

import oracle.kubernetes.json.Default;
import oracle.kubernetes.json.Description;
import oracle.kubernetes.operator.OverrideDistributionStrategy;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;


public class Configuration {

  @Description("Model in image model files and properties.")
  private Model model;

  /**
   * Settings for OPSS security for the Model in Image JRF Domain.
   *
   * @deprecated JRF domain support is deprecated in Model in Image.
   **/
  @Description("Settings for OPSS security for the Model in Image JRF Domain. This field is deprecated,"
      + " and will be removed in a future release. For JRF domain on PV initialization, "
      + "use `configuration.initializeDomainOnPV.domain.opss` section for configuring OPSS security settings.")
  @Deprecated(since = "4.1")
  private Opss opss;

  @Description(
      "A list of names of the Secrets for WebLogic configuration overrides or model.")
  private List<String> secrets;

  @Description("The name of the ConfigMap for WebLogic configuration overrides.")
  private String overridesConfigMap;

  @Description("The introspector job timeout value in seconds. If this field is specified, "
          + "then the operator's ConfigMap `data.introspectorJobActiveDeadlineSeconds` value is ignored. "
          + "Defaults to 120 seconds.")
  private Long introspectorJobActiveDeadlineSeconds;

  @Description(
      "Determines how updated configuration overrides are distributed to already running WebLogic Server instances "
      + "following introspection when the `domainHomeSourceType` is PersistentVolume or Image. Configuration overrides "
      + "are generated during introspection from Secrets, the `overridesConfigMap` field, and WebLogic domain "
      + "topology. Legal values are `Dynamic`, which means that the operator will distribute updated configuration "
      + "overrides dynamically to running servers, and `OnRestart`, which means that servers will use updated "
      + "configuration overrides only after the server's next restart. The selection of `OnRestart` will not cause "
      + "servers to restart when there are updated configuration overrides available. See also "
      + "`domains.spec.introspectVersion`. Defaults to `Dynamic`.")
  @Default(strDefault = "Dynamic")
  private OverrideDistributionStrategy overrideDistributionStrategy;

  @Description("Configuration to initialize a WebLogic Domain on persistent volume (`Domain on PV`) and initialize"
      + " related resources such as a persistent volume and a persistent volume claim. If specified, the operator will"
      + " perform these one-time initialization steps only if the domain and resources do not already exist."
      + " The operator will not recreate or update the domain and resources when they already exist. "
      + " For more information, see"
      + " https://oracle.github.io/weblogic-kubernetes-operator/managing-domains/choosing-a-model/ and"
      + " https://oracle.github.io/weblogic-kubernetes-operator/managing-domains/domain-on-pv ")
  private InitializeDomainOnPV initializeDomainOnPV;

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

  public List<String> getSecrets() {
    return secrets;
  }

  public void setSecrets(List<String> secrets) {
    this.secrets = secrets;
  }

  public Configuration withSecrets(List<String> secrets) {
    setSecrets(secrets);
    return this;
  }

  public String getOverridesConfigMap() {
    return this.overridesConfigMap;
  }

  public void setOverridesConfigMap(String overridesConfigMap) {
    this.overridesConfigMap = overridesConfigMap;
  }

  public Configuration withOverridesConfigMap(String overridesConfigMap) {
    setOverridesConfigMap(overridesConfigMap);
    return this;
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

  public InitializeDomainOnPV getInitializeDomainOnPV() {
    return initializeDomainOnPV;
  }

  public void setInitializeDomainOnPV(InitializeDomainOnPV initializeDomainOnPV) {
    this.initializeDomainOnPV = initializeDomainOnPV;
  }

  /**
   * Adds configuration for initializing domain on PV configuration to the DomainSpec.
   *
   * @param initializeDomainOnPV The configuration for initializing domain on PV to be added to this DomainSpec
   * @return this object
   */
  public Configuration withInitializeDomainOnPv(InitializeDomainOnPV initializeDomainOnPV) {
    this.initializeDomainOnPV = initializeDomainOnPV;
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
            .append("initializeDomainOnPV", initializeDomainOnPV);

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
          .append(initializeDomainOnPV);

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
            .append(initializeDomainOnPV, rhs.initializeDomainOnPV);
    return builder.isEquals();
  }

}


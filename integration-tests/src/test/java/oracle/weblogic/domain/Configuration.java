// Copyright (c) 2020, 2023, Oracle and/or its affiliates.
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

  @ApiModelProperty("Istio service mesh integration")
  private Istio istio;

  @ApiModelProperty(
      "A list of names of the secrets for WebLogic configuration overrides or model.")
  private List<String> secrets;

  @ApiModelProperty(
      "The name of the config map for WebLogic configuration overrides.")
  private String overridesConfigMap;

  @ApiModelProperty(
      "The introspector job timeout value in seconds. If this field is specified"
          + " it overrides the Operator's config map data.introspectorJobActiveDeadlineSeconds value.")
  private Long introspectorJobActiveDeadlineSeconds;

  @ApiModelProperty(
      "Determines how updated configuration overrides are distributed to already running WebLogic servers "
      + "following introspection when the domainHomeSourceType is PersistentVolume or Image.  Configuration overrides "
      + "are generated during introspection from secrets, the overrideConfigMap field, and WebLogic domain topology. "
      + "Legal values are Dynamic (the default) and OnRestart. See also introspectVersion.")
  private String overrideDistributionStrategy;

  @ApiModelProperty("Use online update.")
  private Boolean useOnlineUpdate = false;

  @ApiModelProperty("Rollback the changes if the update require domain restart.")
  private Boolean rollBackIfRestartRequired = false;

  @ApiModelProperty("Configuration to initialize a WebLogic Domain on persistent volume (`Domain on PV`) and initialize"
      + " related resources such as a persistent volume and a persistent volume claim. If specified, the operator will"
      + " perform these one-time initialization steps only if the domain and resources do not already exist."
      + " The operator will not recreate or update the domain and resources when they already exist. "
      + " For more information, see"
      + " https://oracle.github.io/weblogic-kubernetes-operator/managing-domains/choosing-a-model/ and"
      + " https://oracle.github.io/weblogic-kubernetes-operator/managing-domains/domain-on-pv-initialization ")
  private InitializeDomainOnPV initializeDomainOnPV;

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

  public Configuration rollBackIfRestartRequired(boolean rollBackIfRestartRequired) {
    this.rollBackIfRestartRequired = rollBackIfRestartRequired;
    return this;
  }

  public Boolean getRollBackIfRestartRequired() {
    return rollBackIfRestartRequired;
  }

  public void setRollBackIfRestartRequired(boolean rollBackIfRestartRequired) {
    this.rollBackIfRestartRequired = rollBackIfRestartRequired;
  }

  public Configuration useOnlineUpdate(boolean useOnlineUpdate) {
    this.useOnlineUpdate = useOnlineUpdate;
    return this;
  }

  public Boolean getUseOnlineUpdate() {
    return useOnlineUpdate;
  }

  public void setUseOnlineUpdate(boolean useOnlineUpdate) {
    this.useOnlineUpdate = useOnlineUpdate;
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

  public Configuration initializeDomainOnPV(InitializeDomainOnPV initializeDomainOnPV) {
    this.initializeDomainOnPV = initializeDomainOnPV;
    return this;
  }

  public InitializeDomainOnPV initializeDomainOnPV() {
    return this.initializeDomainOnPV;
  }

  public InitializeDomainOnPV getInitializeDomainOnPV() {
    return this.initializeDomainOnPV;
  }

  public void setInitializeDomainOnPV(InitializeDomainOnPV initializeDomainOnPV) {
    this.initializeDomainOnPV = initializeDomainOnPV;
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
            .append("useOnlineUpdate", useOnlineUpdate)
            .append("rollBackIfRestartRequired", rollBackIfRestartRequired)
            .append("initializeDomainOnPV", initializeDomainOnPV);

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
            .append(useOnlineUpdate)
            .append(rollBackIfRestartRequired)
            .append(initializeDomainOnPV);

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
            .append(useOnlineUpdate, rhs.useOnlineUpdate)
            .append(rollBackIfRestartRequired, rhs.rollBackIfRestartRequired)
            .append(initializeDomainOnPV, rhs.initializeDomainOnPV);

    return builder.isEquals();
  }
}

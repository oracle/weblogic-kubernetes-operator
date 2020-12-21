// Copyright (c) 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.weblogic.domain.model;

import javax.annotation.Nullable;
import javax.validation.Valid;

import oracle.kubernetes.json.Description;
import oracle.kubernetes.json.EnumClass;
import oracle.kubernetes.operator.ModelInImageDomainType;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;

public class Model {
  static final String DEFAULT_WDT_MODEL_HOME = "/u01/wdt/models";

  @EnumClass(value = ModelInImageDomainType.class)
  @Description("WebLogic Deploy Tooling domain type. Legal values: WLS, RestrictedJRF, JRF. Defaults to WLS.")
  private String domainType;

  @Description("Name of a ConfigMap containing the WebLogic Deploy Tooling model.")
  private String configMap;

  @Description("Location of the WebLogic Deploy Tooling model home. Defaults to /u01/wdt/models.")
  private String modelHome;

  @Description("Online update option for Model In Image dynamic update.")
  private OnlineUpdate onlineUpdate;

  @Nullable
  public OnlineUpdate getOnlineUpdate() {
    return onlineUpdate;
  }

  public void setOnlineUpdate(OnlineUpdate onlineUpdate) {
    this.onlineUpdate = onlineUpdate;
  }

  public Model withOnlineUpdate(@Nullable OnlineUpdate onlineUpdate) {
    this.onlineUpdate = onlineUpdate;
    return this;
  }

  @Valid
  @Nullable
  @Description("Runtime encryption secret. Required when `domainHomeSourceType` is set to FromModel.")
  private String runtimeEncryptionSecret;

  @Nullable
  public String getDomainType() {
    return domainType;
  }

  public void setDomainType(@Nullable String domainType) {
    this.domainType = domainType;
  }

  public Model withDomainType(@Nullable String domainType) {
    this.domainType = domainType;
    return this;
  }

  @Nullable
  String getConfigMap() {
    return configMap;
  }

  void setConfigMap(@Nullable String configMap) {
    this.configMap = configMap;
  }

  public Model withConfigMap(@Nullable String configMap) {
    this.configMap = configMap;
    return this;
  }

  String getRuntimeEncryptionSecret() {
    return runtimeEncryptionSecret;
  }

  void setRuntimeEncryptionSecret(String runtimeEncryptionSecret) {
    this.runtimeEncryptionSecret = runtimeEncryptionSecret;
  }

  public Model withRuntimeEncryptionSecret(String runtimeEncryptionSecret) {
    this.runtimeEncryptionSecret = runtimeEncryptionSecret;
    return this;
  }

  String getModelHome() {
    return modelHome;
  }

  void setModelHome(String modelHome) {
    this.modelHome = modelHome;
  }

  public Model withModelHome(String modelHome) {
    this.modelHome = modelHome;
    return this;
  }

  @Override
  public String toString() {
    ToStringBuilder builder =
        new ToStringBuilder(this)
            .append("domainType", domainType)
            .append("configMap", configMap)
            .append("modelHome", modelHome)
            .append("runtimeEncryptionSecret", runtimeEncryptionSecret);

    return builder.toString();
  }

  @Override
  public int hashCode() {
    HashCodeBuilder builder = new HashCodeBuilder()
        .append(domainType)
        .append(configMap)
        .append(modelHome)
        .append(onlineUpdate)
        .append(runtimeEncryptionSecret);

    return builder.toHashCode();
  }

  @Override
  public boolean equals(Object other) {
    if (other == this) {
      return true;
    }
    if (!(other instanceof Model)) {
      return false;
    }

    Model rhs = ((Model) other);
    EqualsBuilder builder =
        new EqualsBuilder()
            .append(domainType, rhs.domainType)
            .append(configMap,rhs.configMap)
            .append(modelHome,rhs.modelHome)
            .append(onlineUpdate,rhs.onlineUpdate)
            .append(runtimeEncryptionSecret, rhs.runtimeEncryptionSecret);

    return builder.isEquals();
  }

  /**
   * Check to see if the only change in the spec is introspectVersion.
   * @param other - another DomainSpec object for comparison
   * @return true if the change is only introspectVersion and/or onlineUpdate.enabled=true
   */

  public boolean isSpecChangeForOnlineUpdateOnly(Object other) {
    if (other == this) {
      return true;
    } else if (!(other instanceof Model)) {
      return false;
    }

    Model rhs = ((Model) other);
    EqualsBuilder builder =
        new EqualsBuilder()
            .append(domainType, rhs.domainType)
            .append(modelHome,rhs.modelHome)
            .append(runtimeEncryptionSecret, rhs.runtimeEncryptionSecret);

    boolean isEqual = builder.isEquals();
    if (!isEqual) {
      return isEqual;
    } else {
      return onlineUpdate.getEnabled();
    }
  }

}

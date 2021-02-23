// Copyright (c) 2020, 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.domain;

import io.swagger.annotations.ApiModelProperty;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;

public class Model {

  @ApiModelProperty(
      value = "WDT domain type: Legal values: WLS, RestrictedJRF, JRF. Defaults to WLS.",
      allowableValues = "WLS, RestrictedJRF, JRF")
  private String domainType;

  @ApiModelProperty("WDT config map name.")
  private String configMap;

  @ApiModelProperty("Location of the WebLogic Deploy Tooling model home. Defaults to /u01/wdt/models.")
  private String modelHome;

  @ApiModelProperty("Online update option for Model In Image dynamic update.")
  private OnlineUpdate onlineUpdate;

  @ApiModelProperty(
      "Runtime encryption secret. Required when domainHomeSourceType is set to FromModel.")
  private String runtimeEncryptionSecret;

  public Model domainType(String domainType) {
    this.domainType = domainType;
    return this;
  }

  public String domainType() {
    return domainType;
  }

  public String getDomainType() {
    return domainType;
  }

  public void setDomainType(String domainType) {
    this.domainType = domainType;
  }

  public Model configMap(String configMap) {
    this.configMap = configMap;
    return this;
  }

  public String configMap() {
    return configMap;
  }

  public String getConfigMap() {
    return configMap;
  }

  public void setConfigMap(String configMap) {
    this.configMap = configMap;
  }

  public Model runtimeEncryptionSecret(String runtimeEncryptionSecret) {
    this.runtimeEncryptionSecret = runtimeEncryptionSecret;
    return this;
  }

  public String runtimeEncryptionSecret() {
    return runtimeEncryptionSecret;
  }

  public String getRuntimeEncryptionSecret() {
    return runtimeEncryptionSecret;
  }

  public void setRuntimeEncryptionSecret(String runtimeEncryptionSecret) {
    this.runtimeEncryptionSecret = runtimeEncryptionSecret;
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

  public OnlineUpdate getOnlineUpdate() {
    return onlineUpdate;
  }

  public OnlineUpdate onlineUpdate() {
    return onlineUpdate;
  }

  public Model onlineUpdate(OnlineUpdate onlineUpdate) {
    this.onlineUpdate = onlineUpdate;
    return this;
  }

  public void setOnlineUpdate(OnlineUpdate onlineUpdate) {
    this.onlineUpdate = onlineUpdate;
  }

  @Override
  public String toString() {
    ToStringBuilder builder =
        new ToStringBuilder(this)
            .append("domainType", domainType)
            .append("configMap", configMap)
            .append("modelHome", modelHome)
            .append("runtimeEncryptionSecret", runtimeEncryptionSecret)
            .append("onlineUpdate", onlineUpdate);

    return builder.toString();
  }

  @Override
  public int hashCode() {
    HashCodeBuilder builder =
        new HashCodeBuilder().append(domainType).append(configMap).append(modelHome).append(runtimeEncryptionSecret);

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
    Model rhs = (Model) other;
    EqualsBuilder builder =
        new EqualsBuilder()
            .append(domainType, rhs.domainType)
            .append(configMap, rhs.configMap)
            .append(modelHome,rhs.modelHome)
            .append(runtimeEncryptionSecret, rhs.runtimeEncryptionSecret)
            .append(onlineUpdate, rhs.onlineUpdate);

    return builder.isEquals();
  }
}

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

  @EnumClass(value = ModelInImageDomainType.class)
  @Description("WebLogic Deploy Tooling domain type. Legal values: WLS, RestrictedJRF, JRF. Defaults to WLS.")
  private String domainType;

  @Description("Name of a ConfigMap containing the WebLogic Deploy Tooling model.")
  private String configMap;

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

  @Override
  public String toString() {
    ToStringBuilder builder =
        new ToStringBuilder(this)
            .append("domainType", domainType)
            .append("configMap", configMap)
            .append("runtimeEncryptionSecret", runtimeEncryptionSecret);

    return builder.toString();
  }

  @Override
  public int hashCode() {
    HashCodeBuilder builder = new HashCodeBuilder()
        .append(domainType)
        .append(configMap)
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
            .append(runtimeEncryptionSecret, rhs.runtimeEncryptionSecret);

    return builder.isEquals();
  }
}

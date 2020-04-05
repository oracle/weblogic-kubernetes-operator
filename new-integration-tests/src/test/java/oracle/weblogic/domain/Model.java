// Copyright (c) 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.domain;

import io.swagger.annotations.ApiModelProperty;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;

public class Model {

  @ApiModelProperty(value = "WDT domain type: Legal values: WLS, RestrictedJRF, JRF. Defaults to WLS.",
      allowableValues = "WLS, RestrictedJRF, JRF")
  private String domainType;

  @ApiModelProperty("WDT config map name.")
  private String configMap;

  @ApiModelProperty("Runtime encryption secret. Required when domainHomeSourceType is set to FromModel.")
  private String runtimeEncryptionSecret;

  public Model domainType(String domainType) {
    this.domainType = domainType;
    return this;
  }

  public String domainType() {
    return domainType;
  }

  public Model configMap(String configMap) {
    this.configMap = configMap;
    return this;
  }

  public String configMap() {
    return configMap;
  }

  public Model runtimeEncryptionSecret(String runtimeEncryptionSecret) {
    this.runtimeEncryptionSecret = runtimeEncryptionSecret;
    return this;
  }

  public String runtimeEncryptionSecret() {
    return runtimeEncryptionSecret;
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
            .append(configMap,rhs.configMap)
            .append(runtimeEncryptionSecret, rhs.runtimeEncryptionSecret);

    return builder.isEquals();
  }

}

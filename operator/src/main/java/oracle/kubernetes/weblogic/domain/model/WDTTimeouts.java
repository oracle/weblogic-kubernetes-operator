// Copyright (c) 2020, 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.weblogic.domain.model;

import oracle.kubernetes.json.Description;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;

public class WDTTimeouts {
  @Description("WDT application or library deployment timeout in milliseconds. Default: 180000.")
  private Long deployTimeoutMillis = 180000L;

  @Description("WDT application or library redeployment timeout in milliseconds. Default: 180000.")
  private Long redeployTimeoutMillis = 180000L;

  @Description("WDT application or library undeployment timeout in milliseconds. Default: 180000.")
  private Long undeployTimeoutMillis = 180000L;

  @Description("WDT application start timeout in milliseconds. Default: 180000.")
  private Long startApplicationTimeoutMillis = 180000L;

  @Description("WDT application stop timeout in milliseconds. Default: 180000.")
  private Long stopApplicationTimeoutMillis = 180000L;

  @Description("WDT connect to WebLogic admin server timeout in milliseconds. Default: 120000.")
  private Long connectTimeoutMillis = 120000L;

  @Description("WDT activate WebLogic configuration changes timeout in milliseconds. Default: 180000.")
  private Long activateTimeoutMillis = 180000L;

  @Description("WDT set server groups timeout for extending a JRF domain configured cluster in milliseconds. "
      + "Default: 180000.")
  private Long setServerGroupsTimeoutMillis = 180000L;

  public Long getDeployTimeoutMillis() {
    return deployTimeoutMillis;
  }

  public void setDeployTimeoutMillis(Long deployTimeoutMillis) {
    this.deployTimeoutMillis = deployTimeoutMillis;
  }

  public Long getRedeployTimeoutMillis() {
    return redeployTimeoutMillis;
  }

  public void setRedeployTimeoutMillis(Long redeployTimeoutMillis) {
    this.redeployTimeoutMillis = redeployTimeoutMillis;
  }

  public Long getUndeployTimeoutMillis() {
    return undeployTimeoutMillis;
  }

  public void setUndeployTimeoutMillis(Long undeployTimeoutMillis) {
    this.undeployTimeoutMillis = undeployTimeoutMillis;
  }

  public Long getStartApplicationTimeoutMillis() {
    return startApplicationTimeoutMillis;
  }

  public void setStartApplicationTimeoutMillis(Long startApplicationTimeoutMillis) {
    this.startApplicationTimeoutMillis = startApplicationTimeoutMillis;
  }

  public Long getStopApplicationTimeoutMillis() {
    return stopApplicationTimeoutMillis;
  }

  public void setStopApplicationTimeoutMillis(Long stopApplicationTimeoutMillis) {
    this.stopApplicationTimeoutMillis = stopApplicationTimeoutMillis;
  }

  public Long getConnectTimeoutMillis() {
    return connectTimeoutMillis;
  }

  public void setConnectTimeoutMillis(Long connectTimeoutMillis) {
    this.connectTimeoutMillis = connectTimeoutMillis;
  }

  public Long getActivateTimeoutMillis() {
    return activateTimeoutMillis;
  }

  public void setActivateTimeoutMillis(Long activateTimeoutMillis) {
    this.activateTimeoutMillis = activateTimeoutMillis;
  }

  public Long getSetServerGroupsTimeoutMillis() {
    return setServerGroupsTimeoutMillis;
  }

  public void setSetServerGroupsTimeoutMillis(Long setServerGroupsTimeoutMillis) {
    this.setServerGroupsTimeoutMillis = setServerGroupsTimeoutMillis;
  }

  @Override
  public String toString() {
    ToStringBuilder builder = new ToStringBuilder(this)
        .append("undeployTimeoutMillis", undeployTimeoutMillis)
        .append("deployTimeoutMillis", deployTimeoutMillis)
        .append("startApplicationTimeoutMillis", startApplicationTimeoutMillis)
        .append("stopApplicationTimeoutMillis", stopApplicationTimeoutMillis)
        .append("connectTimeoutMillis", connectTimeoutMillis)
        .append("setServerGroupsTimeoutMillis", setServerGroupsTimeoutMillis)
        .append("activateTimeoutMillis", activateTimeoutMillis)
        .append("redeployTimeoutMillis", redeployTimeoutMillis);

    return builder.toString();
  }

  @Override
  public int hashCode() {
    HashCodeBuilder builder = new HashCodeBuilder()
        .append(undeployTimeoutMillis)
        .append(deployTimeoutMillis)
        .append(startApplicationTimeoutMillis)
        .append(stopApplicationTimeoutMillis)
        .append(connectTimeoutMillis)
        .append(setServerGroupsTimeoutMillis)
        .append(activateTimeoutMillis)
        .append(redeployTimeoutMillis);

    return builder.toHashCode();
  }

  @Override
  public boolean equals(Object other) {
    if (other == this) {
      return true;
    } else if (!(other instanceof WDTTimeouts)) {
      return false;
    }

    WDTTimeouts rhs = ((WDTTimeouts) other);
    EqualsBuilder builder =
        new EqualsBuilder()
            .append(deployTimeoutMillis, rhs.deployTimeoutMillis)
            .append(redeployTimeoutMillis, rhs.redeployTimeoutMillis)
            .append(connectTimeoutMillis, rhs.connectTimeoutMillis)
            .append(startApplicationTimeoutMillis, rhs.startApplicationTimeoutMillis)
            .append(stopApplicationTimeoutMillis, rhs.stopApplicationTimeoutMillis)
            .append(setServerGroupsTimeoutMillis, rhs.setServerGroupsTimeoutMillis)
            .append(undeployTimeoutMillis, rhs.undeployTimeoutMillis);

    return builder.isEquals();
  }

}

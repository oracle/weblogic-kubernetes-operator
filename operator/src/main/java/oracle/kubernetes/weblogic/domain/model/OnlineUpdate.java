// Copyright (c) 2020, 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.weblogic.domain.model;

import oracle.kubernetes.json.Description;
import oracle.kubernetes.operator.MIINonDynamicChangesMethod;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;

public class OnlineUpdate {

  @Description("Enable online update.")
  private Boolean enabled = false;

  @Description("Controls behavior when non-dynamic WebLogic configuration changes are detected"
       + " during an online update."
       + " Non-dynamic changes are changes that require a domain restart to take effect."
       + " Valid values are 'CommitUpdateOnly' (default), 'CommitUpdateAndRoll', and 'CancelUpdate'."
       + " \n\n"
       + " If set to 'CommitUpdateOnly' and any non-dynamic changes are detected, then"
       + " all changes will be committed,"
       + " dynamic changes will take effect immediately,"
       + " the domain will not automatically restart (roll),"
       + " and any non-dynamic changes will become effective only when the domain is restarted."
       + " \n\n"
       + " If set to 'CommitUpdateAndRoll' and any non-dynamic changes are detected, then"
       + " all changes will be committed,"
       + " dynamic changes will take effect immediately,"
       + " the domain will automatically restart (roll),"
       + " and non-dynamic changes will take effect on each pod once the pod restarts."
       + " \n\n"
       + " If set to 'CancelUpdate' and any non-dynamic changes are detected, then "
       + " all changes are ignored,"
       + " the domain continues to run without interruption, "
       + " and you must revert non-dynamic changes if you want dynamic changes to take effect."
       + " \n\n"
       + " For more information, see the runtime update section of the Model in Image user guide.")
  private MIINonDynamicChangesMethod onNonDynamicChanges = MIINonDynamicChangesMethod.CommitUpdateOnly;

  @Description("WDT application or library deployment timeout in milliseconds. Default: 180000.")
  private Long deployTimeoutMilliSeconds = 180000L;

  @Description("WDT application or library redeployment timeout in milliseconds. Default: 180000.")
  private Long redeployTimeoutMilliSeconds = 180000L;

  @Description("WDT application or library undeployment timeout in milliseconds. Default: 180000.")
  private Long undeployTimeoutMilliSeconds = 180000L;

  @Description("WDT application start timeout in milliseconds. Default: 180000.")
  private Long startApplicationTimeoutMilliSeconds = 180000L;

  @Description("WDT application stop timeout in milliseconds. Default: 180000.")
  private Long stopApplicationTimeoutMilliSeconds = 180000L;

  @Description("WDT connect to WebLogic admin server timeout in milliseconds. Default: 120000.")
  private Long connectTimeoutMilliSeconds = 120000L;

  @Description("WDT activate WebLogic configuration changes timeout in milliseconds. Default: 180000.")
  private Long activateTimeoutMilliSeconds = 180000L;

  @Description("WDT set server groups timeout for extending a JRF domain configured cluster in milliseconds. "
        + "Default: 180000.")
  private Long setServerGroupsTimeoutMilliSeconds = 180000L;

  public Boolean getEnabled() {
    return enabled;
  }

  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }

  public OnlineUpdate withEnabled(boolean enabled) {
    this.enabled = enabled;
    return this;
  }

  public MIINonDynamicChangesMethod getOnNonDynamicChanges() {
    return onNonDynamicChanges;
  }

  public void setOnNonDynamicChanges(MIINonDynamicChangesMethod onNonDynamicChanges) {
    this.onNonDynamicChanges = onNonDynamicChanges;
  }

  public OnlineUpdate withOnNonDynamicChanges(MIINonDynamicChangesMethod onNonDynamicChanges) {
    this.onNonDynamicChanges = onNonDynamicChanges;
    return this;
  }

  public Long getDeployTimeoutMilliSeconds() {
    return deployTimeoutMilliSeconds;
  }

  public void setDeployTimeoutMilliSeconds(Long deployTimeoutMilliSeconds) {
    this.deployTimeoutMilliSeconds = deployTimeoutMilliSeconds;
  }

  public OnlineUpdate withDeployTimeoutSeconds(Long deployTimeoutSeconds) {
    this.deployTimeoutMilliSeconds = deployTimeoutSeconds;
    return this;
  }

  public Long getRedeployTimeoutMilliSeconds() {
    return redeployTimeoutMilliSeconds;
  }

  public void setRedeployTimeoutMilliSeconds(Long redeployTimeoutMilliSeconds) {
    this.redeployTimeoutMilliSeconds = redeployTimeoutMilliSeconds;
  }

  public OnlineUpdate withRedeployTimeoutSeconds(Long redeployTimeoutSeconds) {
    this.redeployTimeoutMilliSeconds = redeployTimeoutSeconds;
    return this;
  }

  public Long getUndeployTimeoutMilliSeconds() {
    return undeployTimeoutMilliSeconds;
  }

  public void setUndeployTimeoutMilliSeconds(Long undeployTimeoutMilliSeconds) {
    this.undeployTimeoutMilliSeconds = undeployTimeoutMilliSeconds;
  }

  public OnlineUpdate withUndeployTimeoutSeconds(Long undeployTimeoutSeconds) {
    this.undeployTimeoutMilliSeconds = undeployTimeoutSeconds;
    return this;
  }

  public Long getStartApplicationTimeoutMilliSeconds() {
    return startApplicationTimeoutMilliSeconds;
  }

  public void setStartApplicationTimeoutMilliSeconds(Long startApplicationTimeoutMilliSeconds) {
    this.startApplicationTimeoutMilliSeconds = startApplicationTimeoutMilliSeconds;
  }

  public OnlineUpdate withStartApplicationTimeoutSeconds(Long startApplicationTimeoutSeconds) {
    this.startApplicationTimeoutMilliSeconds = startApplicationTimeoutSeconds;
    return this;
  }

  public Long getStopApplicationTimeoutMilliSeconds() {
    return stopApplicationTimeoutMilliSeconds;
  }

  public void setStopApplicationTimeoutMilliSeconds(Long stopApplicationTimeoutMilliSeconds) {
    this.stopApplicationTimeoutMilliSeconds = stopApplicationTimeoutMilliSeconds;
  }

  public OnlineUpdate withStopApplicationTimeoutSeconds(Long stopApplicationTimeoutSeconds) {
    this.stopApplicationTimeoutMilliSeconds = stopApplicationTimeoutSeconds;
    return this;
  }

  public Long getConnectTimeoutMilliSeconds() {
    return connectTimeoutMilliSeconds;
  }

  public void setConnectTimeoutMilliSeconds(Long connectTimeoutMilliSeconds) {
    this.connectTimeoutMilliSeconds = connectTimeoutMilliSeconds;
  }

  public OnlineUpdate withConnectTimeoutSeconds(Long connectTimeoutSeconds) {
    this.connectTimeoutMilliSeconds = connectTimeoutSeconds;
    return this;
  }

  public Long getActivateTimeoutMilliSeconds() {
    return activateTimeoutMilliSeconds;
  }

  public void setActivateTimeoutMilliSeconds(Long activateTimeoutMilliSeconds) {
    this.activateTimeoutMilliSeconds = activateTimeoutMilliSeconds;
  }

  public OnlineUpdate withActivateTimeoutSeconds(Long activateTimeoutSeconds) {
    this.activateTimeoutMilliSeconds = activateTimeoutSeconds;
    return this;
  }

  public Long getSetServerGroupsTimeoutMilliSeconds() {
    return setServerGroupsTimeoutMilliSeconds;
  }

  public void setSetServerGroupsTimeoutMilliSeconds(Long setServerGroupsTimeoutMilliSeconds) {
    this.setServerGroupsTimeoutMilliSeconds = setServerGroupsTimeoutMilliSeconds;
  }

  public OnlineUpdate withSetServerGroupsTimeoutSeconds(Long setServerGroupsTimeoutSeconds) {
    this.setServerGroupsTimeoutMilliSeconds = setServerGroupsTimeoutSeconds;
    return this;
  }

  @Override
  public int hashCode() {
    HashCodeBuilder builder = new HashCodeBuilder()
        .append(enabled)
        .append(onNonDynamicChanges)
        .append(deployTimeoutMilliSeconds)
        .append(redeployTimeoutMilliSeconds)
        .append(undeployTimeoutMilliSeconds)
        .append(startApplicationTimeoutMilliSeconds)
        .append(stopApplicationTimeoutMilliSeconds)
        .append(connectTimeoutMilliSeconds)
        .append(setServerGroupsTimeoutMilliSeconds)
        .append(activateTimeoutMilliSeconds);

    return builder.toHashCode();
  }

  @Override
  public boolean equals(Object other) {
    if (other == this) {
      return true;
    } else if (!(other instanceof OnlineUpdate)) {
      return false;
    }

    OnlineUpdate rhs = ((OnlineUpdate) other);
    EqualsBuilder builder =
        new EqualsBuilder()
            .append(enabled, rhs.enabled)
            .append(onNonDynamicChanges, rhs.onNonDynamicChanges)
            .append(deployTimeoutMilliSeconds, rhs.deployTimeoutMilliSeconds)
            .append(redeployTimeoutMilliSeconds, rhs.redeployTimeoutMilliSeconds)
            .append(undeployTimeoutMilliSeconds, rhs.undeployTimeoutMilliSeconds)
            .append(startApplicationTimeoutMilliSeconds, rhs.startApplicationTimeoutMilliSeconds)
            .append(stopApplicationTimeoutMilliSeconds, rhs.stopApplicationTimeoutMilliSeconds)
            .append(connectTimeoutMilliSeconds, rhs.connectTimeoutMilliSeconds)
            .append(setServerGroupsTimeoutMilliSeconds, rhs.setServerGroupsTimeoutMilliSeconds)
            .append(activateTimeoutMilliSeconds, rhs.activateTimeoutMilliSeconds);

    return builder.isEquals();
  }

}

// Copyright (c) 2020, 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.domain;

import io.swagger.annotations.ApiModelProperty;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;

public class OnlineUpdate {

  @ApiModelProperty("Enable online update.")
  private Boolean enabled = false;

  @ApiModelProperty("If set to true, it will rollback the changes if the update require domain restart. "
      + "All changes are rolled back, the domain continues to run without interruption. "
      + "It is the user responsibility to revert the content changes in the configmap specified in "
      + "`domain.spec.configuration.model.configmap` or secrets. User can detect the changes have been "
      + "rolled back when describing the domain `kubectl -n <ns> describe domain <domain name>"
      + " under the condition `OnlineUpdateRolledback`")
  private Boolean rollBackIfRestartRequired = false;

  @ApiModelProperty("WLST deploy application or libraries timout in milliseconds. Default: 180000.")
  private Long deployTimeoutMilliSeconds = 180000L;

  @ApiModelProperty("WLST redeploy application or libraries timout in milliseconds. Default: 180000.")
  private Long redeployTimeoutMilliSeconds = 180000L;

  @ApiModelProperty("WLST undeploy application or libraries timout in milliseconds. Default: 180000.")
  private Long undeployTimeoutMilliSeconds = 180000L;

  @ApiModelProperty("WLST startApplication timout in milliseconds. Default: 180000.")
  private Long startApplicationTimeoutMilliSeconds = 180000L;

  @ApiModelProperty("WLST stopApplication timout in milliseconds. Default: 180000.")
  private Long stopApplicationTimeoutMilliSeconds = 180000L;

  @ApiModelProperty("WLST connect to running domain timout in milliseconds. Default: 120000.")
  private Long connectTimeoutMilliSeconds = 120000L;

  @ApiModelProperty("WLST activate changes timout in milliseconds. Default: 180000.")
  private Long activateTimeoutMilliSeconds = 180000L;

  @ApiModelProperty("WLST set server groups timout in milliseconds. Default: 180000.")
  private Long setServerGroupsTimeoutMilliSeconds = 180000L;

  @ApiModelProperty("It has three possible values: "
      + "CommitUpdateOnly - Default or if not set.  All changes are committed, but if there are "
      + "                   non-dynamic mbean changes. The domain needs to be restart manually. "
      + "CommitUpdateAndRoll - All changes are committed, but if there are non-dynamic mbean changes, "
      + "                      the domain will rolling restart automatically; if not, no restart is necessary. "
      + "CancelUpdate - If there are non-dynamic mbean changes, all changes are canceled before "
      + "               they are committed. The domain will continue to run, but changes to the configmap "
      + "               and resources in the domain resource YAML should be reverted manually, "
      + "               otherwise in the next introspection will still use the same content")
  private String onNonDynamicChanges = null;

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

  public OnlineUpdate enabled(boolean enabled) {
    this.enabled = enabled;
    return this;
  }

  public Boolean getRollBackIfRestartRequired() {
    return rollBackIfRestartRequired;
  }

  public void setRollBackIfRestartRequired(boolean rollBackIfRestartRequired) {
    this.rollBackIfRestartRequired = rollBackIfRestartRequired;
  }

  public OnlineUpdate withRollBackIfRestartRequired(boolean rollBackIfRestartRequired) {
    this.rollBackIfRestartRequired = rollBackIfRestartRequired;
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

  public String getOnNonDynamicChanges() {
    return onNonDynamicChanges;
  }

  public void setOnNonDynamicChanges(String onNonDynamicChanges) {
    this.onNonDynamicChanges = onNonDynamicChanges;
  }

  public OnlineUpdate withOnNonDynamicChanges(String onNonDynamicChanges) {
    this.onNonDynamicChanges = onNonDynamicChanges;
    return this;
  }

  @Override
  public String toString() {
    ToStringBuilder builder =
        new ToStringBuilder(this)
            .append("enabled", enabled)
            .append("rollBackIfRestartRequired", rollBackIfRestartRequired)
            .append("deployTimeoutMilliSeconds", deployTimeoutMilliSeconds)
            .append("redeployTimeoutMilliSeconds", redeployTimeoutMilliSeconds)
            .append("undeployTimeoutMilliSeconds", undeployTimeoutMilliSeconds)
            .append("startApplicationTimeoutMilliSeconds", startApplicationTimeoutMilliSeconds)
            .append("stopApplicationTimeoutMilliSeconds", stopApplicationTimeoutMilliSeconds)
            .append("activateTimeoutMilliSeconds", activateTimeoutMilliSeconds)
            .append("connectTimeoutMilliSeconds", connectTimeoutMilliSeconds)
            .append("setServerGroupsTimeoutMilliSeconds", setServerGroupsTimeoutMilliSeconds);

    return builder.toString();
  }

  @Override
  public int hashCode() {
    HashCodeBuilder builder = new HashCodeBuilder()
        .append(enabled)
        .append(rollBackIfRestartRequired)
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
            .append(rollBackIfRestartRequired, rhs.rollBackIfRestartRequired)
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

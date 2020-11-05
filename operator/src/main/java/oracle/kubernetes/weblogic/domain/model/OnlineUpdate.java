// Copyright (c) 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.weblogic.domain.model;

import oracle.kubernetes.json.Description;
import oracle.kubernetes.operator.logging.LoggingFacade;
import oracle.kubernetes.operator.logging.LoggingFactory;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;

public class OnlineUpdate {

  private static final LoggingFacade LOGGER = LoggingFactory.getLogger("Operator", "Operator");

  @Description("Enable online update.")
  private Boolean enabled = false;

  @Description("Rollback the changes if the update require domain restart.")
  private Boolean rollBackIfRestartRequired = false;

  @Description("WLST deploy timout seconds (in milliseconds) Default: 180000L.")
  private Long deployTimeoutSeconds = 180000L;

  @Description("WLST redeploy timout seconds (in milliseconds) Default: 180000L.")
  private Long redeployTimeoutSeconds = 180000L;

  @Description("WLST undeploy timout seconds (in milliseconds) Default: 180000L.")
  private Long undeployTimeoutSeconds = 180000L;

  @Description("WLST startApplication timout seconds (in milliseconds) Default: 180000L.")
  private Long startApplicationTimeoutSeconds = 180000L;

  @Description("WLST stopApplication timout seconds (in milliseconds) Default: 180000L.")
  private Long stopApplicationTimeoutSeconds = 180000L;

  @Description("WLST connect timout seconds (in milliseconds) Default: 180000L.")
  private Long connectTimeoutSeconds = 120000L;

  @Description("WLST activate timout seconds (in milliseconds) Default: 180000L.")
  private Long activateTimeoutSeconds = 180000L;

  @Description("WLST set server groups timout seconds (in milliseconds) Default: 180000L.")
  private Long setServerGroupsTimeoutSeconds = 180000L;

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

  public Long getDeployTimeoutSeconds() {
    return deployTimeoutSeconds;
  }

  public void setDeployTimeoutSeconds(Long deployTimeoutSeconds) {
    this.deployTimeoutSeconds = deployTimeoutSeconds;
  }

  public OnlineUpdate withDeployTimeoutSeconds(Long deployTimeoutSeconds) {
    this.deployTimeoutSeconds = deployTimeoutSeconds;
    return this;
  }

  public Long getRedeployTimeoutSeconds() {
    return redeployTimeoutSeconds;
  }

  public void setRedeployTimeoutSeconds(Long redeployTimeoutSeconds) {
    this.redeployTimeoutSeconds = redeployTimeoutSeconds;
  }

  public OnlineUpdate withRedeployTimeoutSeconds(Long redeployTimeoutSeconds) {
    this.redeployTimeoutSeconds = redeployTimeoutSeconds;
    return this;
  }

  public Long getUndeployTimeoutSeconds() {
    return undeployTimeoutSeconds;
  }

  public void setUndeployTimeoutSeconds(Long undeployTimeoutSeconds) {
    this.undeployTimeoutSeconds = undeployTimeoutSeconds;
  }

  public OnlineUpdate withUndeployTimeoutSeconds(Long undeployTimeoutSeconds) {
    this.undeployTimeoutSeconds = undeployTimeoutSeconds;
    return this;
  }

  public Long getStartApplicationTimeoutSeconds() {
    return startApplicationTimeoutSeconds;
  }

  public void setStartApplicationTimeoutSeconds(Long startApplicationTimeoutSeconds) {
    this.startApplicationTimeoutSeconds = startApplicationTimeoutSeconds;
  }

  public OnlineUpdate withStartApplicationTimeoutSeconds(Long startApplicationTimeoutSeconds) {
    this.startApplicationTimeoutSeconds = startApplicationTimeoutSeconds;
    return this;
  }

  public Long getStopApplicationTimeoutSeconds() {
    return stopApplicationTimeoutSeconds;
  }

  public void setStopApplicationTimeoutSeconds(Long stopApplicationTimeoutSeconds) {
    this.stopApplicationTimeoutSeconds = stopApplicationTimeoutSeconds;
  }

  public OnlineUpdate withStopApplicationTimeoutSeconds(Long stopApplicationTimeoutSeconds) {
    this.stopApplicationTimeoutSeconds = stopApplicationTimeoutSeconds;
    return this;
  }

  public Long getConnectTimeoutSeconds() {
    return connectTimeoutSeconds;
  }

  public void setConnectTimeoutSeconds(Long connectTimeoutSeconds) {
    this.connectTimeoutSeconds = connectTimeoutSeconds;
  }

  public OnlineUpdate withConnectTimeoutSeconds(Long connectTimeoutSeconds) {
    this.connectTimeoutSeconds = connectTimeoutSeconds;
    return this;
  }

  public Long getActivateTimeoutSeconds() {
    return activateTimeoutSeconds;
  }

  public void setActivateTimeoutSeconds(Long activateTimeoutSeconds) {
    this.activateTimeoutSeconds = activateTimeoutSeconds;
  }

  public OnlineUpdate withActivateTimeoutSeconds(Long activateTimeoutSeconds) {
    this.activateTimeoutSeconds = activateTimeoutSeconds;
    return this;
  }

  public Long getSetServerGroupsTimeoutSeconds() {
    return setServerGroupsTimeoutSeconds;
  }

  public void setSetServerGroupsTimeoutSeconds(Long setServerGroupsTimeoutSeconds) {
    this.setServerGroupsTimeoutSeconds = setServerGroupsTimeoutSeconds;
  }

  public OnlineUpdate withSetServerGroupsTimeoutSeconds(Long setServerGroupsTimeoutSeconds) {
    this.setServerGroupsTimeoutSeconds = setServerGroupsTimeoutSeconds;
    return this;
  }

  @Override
  public int hashCode() {
    HashCodeBuilder builder = new HashCodeBuilder()
        .append(enabled)
        .append(rollBackIfRestartRequired)
        .append(deployTimeoutSeconds)
        .append(redeployTimeoutSeconds)
        .append(undeployTimeoutSeconds)
        .append(startApplicationTimeoutSeconds)
        .append(stopApplicationTimeoutSeconds)
        .append(connectTimeoutSeconds)
        .append(setServerGroupsTimeoutSeconds)
        .append(activateTimeoutSeconds);

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
            .append(deployTimeoutSeconds, rhs.deployTimeoutSeconds)
            .append(redeployTimeoutSeconds, rhs.redeployTimeoutSeconds)
            .append(undeployTimeoutSeconds, rhs.undeployTimeoutSeconds)
            .append(startApplicationTimeoutSeconds, rhs.startApplicationTimeoutSeconds)
            .append(stopApplicationTimeoutSeconds, rhs.stopApplicationTimeoutSeconds)
            .append(connectTimeoutSeconds, rhs.connectTimeoutSeconds)
            .append(setServerGroupsTimeoutSeconds, rhs.setServerGroupsTimeoutSeconds)
            .append(activateTimeoutSeconds, rhs.activateTimeoutSeconds);

    return builder.isEquals();
  }

}

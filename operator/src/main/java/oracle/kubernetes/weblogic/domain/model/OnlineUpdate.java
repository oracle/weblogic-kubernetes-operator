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
  private Long deployTimeoutMilliSeconds = 180000L;

  @Description("WLST redeploy timout seconds (in milliseconds) Default: 180000L.")
  private Long redeployTimeoutMilliSeconds = 180000L;

  @Description("WLST undeploy timout seconds (in milliseconds) Default: 180000L.")
  private Long undeployTimeoutMilliSeconds = 180000L;

  @Description("WLST startApplication timout seconds (in milliseconds) Default: 180000L.")
  private Long startApplicationTimeoutMilliSeconds = 180000L;

  @Description("WLST stopApplication timout seconds (in milliseconds) Default: 180000L.")
  private Long stopApplicationTimeoutMilliSeconds = 180000L;

  @Description("WLST connect timout seconds (in milliseconds) Default: 180000L.")
  private Long connectTimeoutMilliSeconds = 120000L;

  @Description("WLST activate timout seconds (in milliseconds) Default: 180000L.")
  private Long activateTimeoutMilliSeconds = 180000L;

  @Description("WLST set server groups timout seconds (in milliseconds) Default: 180000L.")
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

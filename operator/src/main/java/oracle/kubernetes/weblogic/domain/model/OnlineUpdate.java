// Copyright (c) 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.weblogic.domain.model;

import oracle.kubernetes.json.Description;
import oracle.kubernetes.operator.MIINonDynamicChangesMethod;
import oracle.kubernetes.operator.logging.LoggingFacade;
import oracle.kubernetes.operator.logging.LoggingFactory;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;

public class OnlineUpdate {

  private static final LoggingFacade LOGGER = LoggingFactory.getLogger("Operator", "Operator");

  @Description("Enable online update.")
  private Boolean enabled = false;

  @Description("Controlling non-dynamic changes behavior in online update.If set to CancelUpdate, it will cancel "
      + " all the changes if the update include non-dynamic changes that "
      + " require domain restart. All changes are canceled, the domain continues to run without interruption. "
      + " Note: It is the user responsibility to revert the content changes in the configmap specified in "
      +     "`domain.spec.configuration.model.configmap` or secrets. User can detect the changes have been "
      +     "rolled back when describing the domain `kubectl -n <ns> describe domain <domain name>"
      +     " under the condition `OnlineUpdateRolledback`"
      +     " If set to CommitUpdateAndRoll, it will commit all changes, if there are non-dynamic changes involved, "
      + " the domain will rolling restart and the effect of the changes will be effective once the restart in complete "
      + " If set to CommitUpdateOnly, it will commit all changes, but the domain "
      + " will not restart even if there are non-dynamic changes involved, the changes will become effective only if "
      + " the domain is restarted"
    )
  private MIINonDynamicChangesMethod onNonDynamicChanges = MIINonDynamicChangesMethod.CommitUpdateAndRoll;

  @Description("WLST deploy application or libraries timout in milliseconds. Default: 180000.")
  private Long deployTimeoutMilliSeconds = 180000L;

  @Description("WLST redeploy application or libraries timout in milliseconds. Default: 180000.")
  private Long redeployTimeoutMilliSeconds = 180000L;

  @Description("WLST undeploy application or libraries timout in milliseconds. Default: 180000.")
  private Long undeployTimeoutMilliSeconds = 180000L;

  @Description("WLST startApplication timout in milliseconds. Default: 180000.")
  private Long startApplicationTimeoutMilliSeconds = 180000L;

  @Description("WLST stopApplication timout in milliseconds. Default: 180000.")
  private Long stopApplicationTimeoutMilliSeconds = 180000L;

  @Description("WLST connect to running domain timout in milliseconds. Default: 120000.")
  private Long connectTimeoutMilliSeconds = 120000L;

  @Description("WLST activate changes timout in milliseconds. Default: 180000.")
  private Long activateTimeoutMilliSeconds = 180000L;

  @Description("WLST set server groups timout in milliseconds. Default: 180000.")
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

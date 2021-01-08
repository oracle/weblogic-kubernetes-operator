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

  private WDTTimeouts wdtTimeouts;

  public WDTTimeouts getWdtTimeouts() {
    return wdtTimeouts;
  }

  public void setWdtTimeouts(WDTTimeouts wdtTimeouts) {
    this.wdtTimeouts = wdtTimeouts;
  }

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

  @Override
  public int hashCode() {
    HashCodeBuilder builder = new HashCodeBuilder()
        .append(enabled)
        .append(onNonDynamicChanges)
        .append(wdtTimeouts);

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
            .append(wdtTimeouts, rhs.wdtTimeouts);

    return builder.isEquals();
  }

  class WDTTimeouts {
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

}

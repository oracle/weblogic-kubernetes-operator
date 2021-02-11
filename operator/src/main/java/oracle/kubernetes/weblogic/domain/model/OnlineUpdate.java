// Copyright (c) 2020, 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.weblogic.domain.model;

import oracle.kubernetes.json.Description;
import oracle.kubernetes.operator.MIINonDynamicChangesMethod;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;

public class OnlineUpdate {

  @Description("Enable online update. Default is 'false'.")
  private Boolean enabled = false;

  @Description(""
       + "Controls behavior when non-dynamic WebLogic configuration changes are detected"
       + " during an online update."
       + " Non-dynamic changes are changes that require a domain restart to take effect."
       + " Valid values are 'CommitUpdateOnly' (default), and 'CommitUpdateAndRoll'."
       + " \n\n"
       + " If set to 'CommitUpdateOnly' and any non-dynamic changes are detected, then"
       + " all changes will be committed,"
       + " dynamic changes will take effect immediately,"
       + " the domain will not automatically restart (roll),"
       + " and any non-dynamic changes will become effective on a pod only if"
       + " the pod is later restarted."
       + " \n\n"
       + " If set to 'CommitUpdateAndRoll' and any non-dynamic changes are detected, then"
       + " all changes will be committed,"
       + " dynamic changes will take effect immediately,"
       + " the domain will automatically restart (roll),"
       + " and non-dynamic changes will take effect on each pod once the pod restarts."
       + " \n\n"
       + " For more information, see the runtime update section of the Model in Image user guide."
  )
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
  public String toString() {
    ToStringBuilder builder =
        new ToStringBuilder(this)
            .append("enabled", enabled)
            .append("wdtTimeouts", wdtTimeouts)
            .append("onNonDynamicChanges", onNonDynamicChanges);

    return builder.toString();
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

}

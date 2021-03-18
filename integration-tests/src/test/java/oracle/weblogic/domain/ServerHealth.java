// Copyright (c) 2020, 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.domain;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;

@ApiModel(
    description =
        "ServerHealth describes the current status and health of a specific WebLogic Server.")
public class ServerHealth {

  @ApiModelProperty("RFC 3339 date and time at which the server started.")
  private OffsetDateTime activationTime;

  @ApiModelProperty(
      "Server health of this WebLogic Server. If the value is \"Not available\", the operator has "
          + "failed to read the health. If the value is \"Not available (possibly overloaded)\", the "
          + "operator has failed to read the health of the server possibly due to the server is "
          + "in overloaded state.")
  private String overallHealth;

  @ApiModelProperty("Status of unhealthy subsystems, if any.")
  private List<SubsystemHealth> subsystems = new ArrayList<>();

  public ServerHealth activationTime(OffsetDateTime activationTime) {
    this.activationTime = activationTime;
    return this;
  }

  public OffsetDateTime activationTime() {
    return activationTime;
  }

  public OffsetDateTime getActivationTime() {
    return activationTime;
  }

  public void setActivationTime(OffsetDateTime activationTime) {
    this.activationTime = activationTime;
  }

  public ServerHealth overallHealth(String overallHealth) {
    this.overallHealth = overallHealth;
    return this;
  }

  public String overallHealth() {
    return overallHealth;
  }

  public String getOverallHealth() {
    return overallHealth;
  }

  public void setOverallHealth(String overallHealth) {
    this.overallHealth = overallHealth;
  }

  public ServerHealth subsystems(List<SubsystemHealth> subsystems) {
    this.subsystems = subsystems;
    return this;
  }

  public List<SubsystemHealth> subsystems() {
    return subsystems;
  }

  /**
   * Adds subsystems health item.
   * @param subsystemsItem Subsystem health
   * @return this
   */
  public ServerHealth addSubsystemsItem(SubsystemHealth subsystemsItem) {
    if (subsystems == null) {
      subsystems = new ArrayList<>();
    }
    subsystems.add(subsystemsItem);
    return this;
  }

  public List<SubsystemHealth> getSubsystems() {
    return subsystems;
  }

  public void setSubsystems(List<SubsystemHealth> subsystems) {
    this.subsystems = subsystems;
  }

  @Override
  public String toString() {
    return new ToStringBuilder(this)
        .append("activationTime", activationTime)
        .append("overallHealth", overallHealth)
        .append("subsystems", subsystems)
        .toString();
  }

  @Override
  public int hashCode() {
    return new HashCodeBuilder()
        .append(overallHealth)
        .append(activationTime)
        .append(subsystems)
        .toHashCode();
  }

  @Override
  public boolean equals(Object other) {
    if (this == other) {
      return true;
    }

    if (other == null || getClass() != other.getClass()) {
      return false;
    }
    ServerHealth rhs = (ServerHealth) other;
    return new EqualsBuilder()
        .append(overallHealth, rhs.overallHealth)
        .append(activationTime, rhs.activationTime)
        .append(subsystems, rhs.subsystems)
        .isEquals();
  }
}

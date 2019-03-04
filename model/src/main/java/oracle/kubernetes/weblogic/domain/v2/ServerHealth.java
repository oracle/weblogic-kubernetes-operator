// Copyright 2017, 2019, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.weblogic.domain.v2;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;
import java.util.ArrayList;
import java.util.List;
import javax.validation.Valid;
import oracle.kubernetes.json.Description;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.joda.time.DateTime;

/** ServerHealth describes the current status and health of a specific WebLogic server. */
public class ServerHealth {

  @Description("RFC 3339 date and time at which the server started.")
  @SerializedName("activationTime")
  @Expose
  private DateTime activationTime;

  @Description("Server health of this WebLogic server.")
  @SerializedName("overallHealth")
  @Expose
  private String overallHealth;

  @Description("Status of unhealthy subsystems, if any.")
  @SerializedName("subsystems")
  @Expose
  @Valid
  private List<SubsystemHealth> subsystems = new ArrayList<SubsystemHealth>();

  /**
   * RFC 3339 date and time at which the server started.
   *
   * @return activation time
   */
  public DateTime getActivationTime() {
    return activationTime;
  }

  /**
   * RFC 3339 date and time at which the server started.
   *
   * @param activationTime activation time
   */
  public void setActivationTime(DateTime activationTime) {
    this.activationTime = activationTime;
  }

  /**
   * RFC 3339 date and time at which the server started.
   *
   * @param activationTime activation time
   * @return this
   */
  public ServerHealth withActivationTime(DateTime activationTime) {
    this.activationTime = activationTime;
    return this;
  }

  /**
   * Server health of this WebLogic server.
   *
   * @return overall health
   */
  public String getOverallHealth() {
    return overallHealth;
  }

  /**
   * Server health of this WebLogic server.
   *
   * @param overallHealth overall health
   */
  public void setOverallHealth(String overallHealth) {
    this.overallHealth = overallHealth;
  }

  /**
   * Server health of this WebLogic server.
   *
   * @param overallHealth overall health
   * @return this
   */
  public ServerHealth withOverallHealth(String overallHealth) {
    this.overallHealth = overallHealth;
    return this;
  }

  /**
   * Status of unhealthy subsystems, if any.
   *
   * @return subsystems
   */
  public List<SubsystemHealth> getSubsystems() {
    return subsystems;
  }

  /**
   * Status of unhealthy subsystems, if any.
   *
   * @param subsystems subsystems
   */
  public void setSubsystems(List<SubsystemHealth> subsystems) {
    this.subsystems = subsystems;
  }

  /**
   * Status of unhealthy subsystems, if any.
   *
   * @param subsystems subsystems
   * @return this
   */
  public ServerHealth withSubsystems(List<SubsystemHealth> subsystems) {
    this.subsystems = subsystems;
    return this;
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
        .append(Domain.sortOrNull(subsystems))
        .toHashCode();
  }

  @Override
  public boolean equals(Object other) {
    if (other == this) {
      return true;
    }
    if (!(other instanceof ServerHealth)) {
      return false;
    }
    ServerHealth rhs = ((ServerHealth) other);
    return new EqualsBuilder()
        .append(overallHealth, rhs.overallHealth)
        .append(activationTime, rhs.activationTime)
        .append(Domain.sortOrNull(subsystems), Domain.sortOrNull(rhs.subsystems))
        .isEquals();
  }
}

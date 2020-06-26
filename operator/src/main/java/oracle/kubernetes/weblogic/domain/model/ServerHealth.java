// Copyright (c) 2017, 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.weblogic.domain.model;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import javax.validation.Valid;

import com.google.gson.annotations.Expose;
import oracle.kubernetes.json.Description;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.joda.time.DateTime;

import static oracle.kubernetes.weblogic.domain.model.ObjectPatch.createObjectPatch;

/** ServerHealth describes the current status and health of a specific WebLogic Server. */
public class ServerHealth {

  @Description("RFC 3339 date and time at which the server started.")
  @Expose
  private DateTime activationTime;

  @Description(
      "Server health of this WebLogic Server instance. If the value is \"Not available\", the operator has "
          + "failed to read the health. If the value is \"Not available (possibly overloaded)\", the "
          + "operator has failed to read the health of the server possibly due to the server is "
          + "in the overloaded state.")
  @Expose
  private String overallHealth;

  @Description("Status of unhealthy subsystems, if any.")
  @Expose
  @Valid
  private List<SubsystemHealth> subsystems = new ArrayList<>();

  public ServerHealth() {
  }

  /**
   * Copy constructor.
   * @param other the object to deep-copy
   */
  ServerHealth(ServerHealth other) {
    this.activationTime = other.activationTime;
    this.overallHealth = other.overallHealth;
    this.subsystems = other.subsystems.stream().map(SubsystemHealth::new).collect(Collectors.toList());
  }

  /**
   * RFC 3339 date and time at which the server started.
   *
   * @return activation time
   */
  private DateTime getActivationTime() {
    return activationTime;
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
   * Add the status of an unhealthy subsystem.
   *
   * @param subsystem the unhealthy subsystem
   * @return this
   */
  public ServerHealth addSubsystem(SubsystemHealth subsystem) {
    subsystems.add(subsystem);
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

  private static final ObjectPatch<ServerHealth> healthPatch = createObjectPatch(ServerHealth.class)
        .withDateTimeField("activationTime", ServerHealth::getActivationTime)
        .withStringField("overallHealth", ServerHealth::getOverallHealth)
        .withListField("subsystems", SubsystemHealth.getObjectPatch(), ServerHealth::getSubsystems);

  static ObjectPatch<ServerHealth> getObjectPatch() {
    return healthPatch;
  }
}

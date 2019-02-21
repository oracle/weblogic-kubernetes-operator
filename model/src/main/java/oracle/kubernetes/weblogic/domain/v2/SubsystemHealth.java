// Copyright 2017, 2019, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.weblogic.domain.v2;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;
import java.util.ArrayList;
import java.util.List;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import oracle.kubernetes.json.Description;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;

/** SubsystemHealth describes the current health of a specific subsystem. */
public class SubsystemHealth implements Comparable<SubsystemHealth> {

  @Description("Server health of this WebLogic server. Required")
  @SerializedName("health")
  @Expose
  @NotNull
  private String health;

  @Description("Name of subsystem providing symptom information. Required")
  @SerializedName("subsystemName")
  @Expose
  @NotNull
  private String subsystemName;

  @Description("Symptoms provided by the reporting subsystem.")
  @SerializedName("symptoms")
  @Expose
  @Valid
  private List<String> symptoms = new ArrayList<String>();

  /**
   * Server health of this WebLogic server. (Required)
   *
   * @return health
   */
  public String getHealth() {
    return health;
  }

  /**
   * Server health of this WebLogic server. (Required)
   *
   * @param health health
   */
  public void setHealth(String health) {
    this.health = health;
  }

  /**
   * Server health of this WebLogic server. (Required)
   *
   * @param health health
   * @return this
   */
  public SubsystemHealth withHealth(String health) {
    this.health = health;
    return this;
  }

  /**
   * Name of subsystem providing symptom information. (Required)
   *
   * @return subsystem name
   */
  public String getSubsystemName() {
    return subsystemName;
  }

  /**
   * Name of subsystem providing symptom information. (Required)
   *
   * @param subsystemName subsystem name
   */
  public void setSubsystemName(String subsystemName) {
    this.subsystemName = subsystemName;
  }

  /**
   * Name of subsystem providing symptom information. (Required)
   *
   * @param subsystemName subsystem name
   * @return this
   */
  public SubsystemHealth withSubsystemName(String subsystemName) {
    this.subsystemName = subsystemName;
    return this;
  }

  /**
   * Symptoms provided by the reporting subsystem.
   *
   * @return symptoms
   */
  public List<String> getSymptoms() {
    return symptoms;
  }

  /**
   * Symptoms provided by the reporting subsystem.
   *
   * @param symptoms symptoms
   */
  public void setSymptoms(List<String> symptoms) {
    this.symptoms = symptoms;
  }

  /**
   * Symptoms provided by the reporting subsystem.
   *
   * @param symptoms symptoms
   * @return this
   */
  public SubsystemHealth withSymptoms(List<String> symptoms) {
    this.symptoms = symptoms;
    return this;
  }

  @Override
  public String toString() {
    return new ToStringBuilder(this)
        .append("health", health)
        .append("subsystemName", subsystemName)
        .append("symptoms", symptoms)
        .toString();
  }

  @Override
  public int hashCode() {
    return new HashCodeBuilder()
        .append(Domain.sortOrNull(symptoms))
        .append(health)
        .append(subsystemName)
        .toHashCode();
  }

  @Override
  public boolean equals(Object other) {
    if (other == this) {
      return true;
    }
    if (!(other instanceof SubsystemHealth)) {
      return false;
    }
    SubsystemHealth rhs = ((SubsystemHealth) other);
    return new EqualsBuilder()
        .append(Domain.sortOrNull(symptoms), Domain.sortOrNull(rhs.symptoms))
        .append(health, rhs.health)
        .append(subsystemName, rhs.subsystemName)
        .isEquals();
  }

  @Override
  public int compareTo(SubsystemHealth o) {
    return subsystemName.compareTo(o.subsystemName);
  }
}

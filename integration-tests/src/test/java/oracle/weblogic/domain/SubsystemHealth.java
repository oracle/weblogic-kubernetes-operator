// Copyright (c) 2020, 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.domain;

import java.util.ArrayList;
import java.util.List;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;

@ApiModel(description = "SubsystemHealth describes the current health of a specific subsystem.")
public class SubsystemHealth {

  @ApiModelProperty("Server health of this WebLogic Server. Required.")
  private String health;

  @ApiModelProperty("Name of subsystem providing symptom information. Required.")
  private String subsystemName;

  @ApiModelProperty("Symptoms provided by the reporting subsystem.")
  private List<String> symptoms = new ArrayList<>();

  public SubsystemHealth health(String health) {
    this.health = health;
    return this;
  }

  public String health() {
    return health;
  }

  public String getHealth() {
    return health;
  }

  public void setHealth(String health) {
    this.health = health;
  }

  public SubsystemHealth subsystemName(String subsystemName) {
    this.subsystemName = subsystemName;
    return this;
  }

  public String subsystemName() {
    return subsystemName;
  }

  public String getSubsystemName() {
    return subsystemName;
  }

  public void setSubsystemName(String subsystemName) {
    this.subsystemName = subsystemName;
  }

  public SubsystemHealth symptoms(List<String> symptoms) {
    this.symptoms = symptoms;
    return this;
  }

  public List<String> symptoms() {
    return symptoms;
  }

  /**
   * Adds symptoms item.
   * @param symptomsItem Symptom
   * @return this
   */
  public SubsystemHealth addSymptomsItem(String symptomsItem) {
    if (symptoms == null) {
      symptoms = new ArrayList<>();
    }
    symptoms.add(symptomsItem);
    return this;
  }

  public List<String> getSymptoms() {
    return symptoms;
  }

  public void setSymptoms(List<String> symptoms) {
    this.symptoms = symptoms;
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
    return new HashCodeBuilder().append(symptoms).append(health).append(subsystemName).toHashCode();
  }

  @Override
  public boolean equals(Object other) {
    if (this == other) {
      return true;
    }

    if (other == null || getClass() != other.getClass()) {
      return false;
    }
    SubsystemHealth rhs = (SubsystemHealth) other;
    return new EqualsBuilder()
        .append(symptoms, rhs.symptoms)
        .append(health, rhs.health)
        .append(subsystemName, rhs.subsystemName)
        .isEquals();
  }
}

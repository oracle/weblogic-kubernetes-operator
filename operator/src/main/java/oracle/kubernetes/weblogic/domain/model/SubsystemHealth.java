// Copyright (c) 2017, 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.weblogic.domain.model;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.annotation.Nonnull;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;

import com.google.gson.annotations.Expose;
import oracle.kubernetes.json.Description;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;

import static oracle.kubernetes.weblogic.domain.model.ObjectPatch.createObjectPatch;

/** SubsystemHealth describes the current health of a specific subsystem. */
public class SubsystemHealth implements Comparable<SubsystemHealth>, PatchableComponent<SubsystemHealth> {

  @Description("Server health of this WebLogic Server instance.")
  @Expose
  @NotNull
  private String health;

  @Description("Name of subsystem providing symptom information.")
  @Expose
  @NotNull
  private String subsystemName;

  @Description("Symptoms provided by the reporting subsystem.")
  @Expose
  @Valid
  private List<String> symptoms = new ArrayList<>();

  public SubsystemHealth() {
  }

  /**
   * Copy constructor.
   * @param other the object to copy
   */
  SubsystemHealth(SubsystemHealth other) {
    this.health = other.health;
    this.subsystemName = other.subsystemName;
    this.symptoms = new ArrayList<>(other.symptoms);
  }

  /**
   * Server health of this WebLogic Server. Required.
   *
   * @return health
   */
  private String getHealth() {
    return health;
  }

  /**
   * Server health of this WebLogic Server. Required.
   *
   * @param health health
   * @return this
   */
  public SubsystemHealth withHealth(String health) {
    this.health = health;
    return this;
  }

  /**
   * Name of subsystem providing symptom information. Required.
   *
   * @return subsystem name
   */
  private String getSubsystemName() {
    return subsystemName;
  }

  /**
   * Name of subsystem providing symptom information. Required.
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
  private List<String> getSymptoms() {
    return symptoms;
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

  /**
   * Symptoms provided by the reporting subsystem.
   *
   * @param symptoms symptoms
   * @return this
   */
  public SubsystemHealth withSymptoms(String... symptoms) {
    this.symptoms = Arrays.asList(symptoms);
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
  public int compareTo(@Nonnull SubsystemHealth o) {
    return subsystemName.compareTo(o.subsystemName);
  }

  @Override
  public boolean isPatchableFrom(SubsystemHealth other) {
    return other.subsystemName != null && other.subsystemName.equals(subsystemName);
  }

  private static final ObjectPatch<SubsystemHealth> healthPatch = createObjectPatch(SubsystemHealth.class)
        .withStringField("health", SubsystemHealth::getHealth)
        .withStringField("subsystemName", SubsystemHealth::getSubsystemName)
        .withListField("symptoms", SubsystemHealth::getSymptoms);

  static ObjectPatch<SubsystemHealth> getObjectPatch() {
    return healthPatch;
  }
}

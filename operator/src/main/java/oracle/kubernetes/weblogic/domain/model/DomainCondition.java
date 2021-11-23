// Copyright (c) 2017, 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.weblogic.domain.model;

import java.time.OffsetDateTime;
import java.util.Optional;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;
import jakarta.validation.constraints.NotNull;
import oracle.kubernetes.json.Description;
import oracle.kubernetes.operator.DomainFailureReason;
import oracle.kubernetes.utils.SystemClock;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;

import static oracle.kubernetes.weblogic.domain.model.ObjectPatch.createObjectPatch;

/** DomainCondition contains details for the current condition of this domain. */
public class DomainCondition implements Comparable<DomainCondition>, PatchableComponent<DomainCondition> {

  public static final String TRUE = "True";
  public static final String FALSE = "False";

  @Description(
      "The type of the condition. Valid types are Completed, "
          + "Available, Failed, and ConfigChangesPendingRestart.")
  @NotNull
  private final DomainConditionType type;

  @Description("Last time we probed the condition.")
  @SerializedName("lastProbeTime")
  @Expose
  private OffsetDateTime lastProbeTime;

  @Description("Last time the condition transitioned from one status to another.")
  @SerializedName("lastTransitionTime")
  @Expose
  private OffsetDateTime lastTransitionTime;

  @Description("Human-readable message indicating details about last transition.")
  @SerializedName("message")
  @Expose
  private String message;

  @Description("Unique, one-word, CamelCase reason for the condition's last transition.")
  @SerializedName("reason")
  @Expose
  private String reason;

  @Description("The status of the condition. Can be True, False, Unknown.")
  @SerializedName("status")
  @Expose
  @NotNull
  private String status = "True";

  /**
   * Creates a new domain condition, initialized with its type.
   * @param conditionType the enum that designates the condition type
   */
  public DomainCondition(DomainConditionType conditionType) {
    lastTransitionTime = SystemClock.now();
    type = conditionType;
  }

  DomainCondition(DomainCondition other) {
    this.type = other.type;
    this.lastProbeTime = other.lastProbeTime;
    this.lastTransitionTime = other.lastTransitionTime;
    this.message = other.message;
    this.reason = other.reason;
    this.status = other.status;
  }

  /**
   * Last time we probed the condition.
   *
   * @return time
   */
  public OffsetDateTime getLastProbeTime() {
    return lastProbeTime;
  }

  /**
   * Last time we probed the condition.
   *
   * @param lastProbeTime time
   */
  public void setLastProbeTime(OffsetDateTime lastProbeTime) {
    this.lastProbeTime = lastProbeTime;
  }

  /**
   * Last time we probed the condition.
   *
   * @param lastProbeTime time
   * @return this
   */
  public DomainCondition withLastProbeTime(OffsetDateTime lastProbeTime) {
    this.lastProbeTime = lastProbeTime;
    return this;
  }

  /**
   * Last time the condition transitioned from one status to another.
   *
   * @return time
   */
  public OffsetDateTime getLastTransitionTime() {
    return lastTransitionTime;
  }

  /**
   * Human-readable message indicating details about last transition.
   *
   * @return message
   */
  public String getMessage() {
    return message;
  }

  /**
   * Human-readable message indicating details about last transition.
   *
   * @param message message
   * @return this
   */
  public DomainCondition withMessage(String message) {
    lastTransitionTime = SystemClock.now();
    this.message = message;
    return this;
  }

  /**
   * Unique, one-word, CamelCase reason for the condition's last transition.
   *
   * @return reason
   */
  public String getReason() {
    return reason;
  }

  /**
   * Unique, one-word, CamelCase reason for the condition's last transition.
   *
   * @param reason reason
   * @return this
   */
  public DomainCondition withReason(DomainFailureReason reason) {
    lastTransitionTime = SystemClock.now();
    this.reason = Optional.ofNullable(reason).map(Enum::toString).orElse(null);
    return this;
  }

  /**
   * The status of the condition. Can be True, False, Unknown. Required.
   *
   * @return status
   */
  public String getStatus() {
    return status;
  }

  /**
   * The status of the condition. Can be True, False, Unknown. Required.
   *
   * @param status the new status value
   * @return this object
   */
  public DomainCondition withStatus(String status) {
    assert status.equals(TRUE) || ! type.statusMustBeTrue() : "Attempt to set illegal status value";
    lastTransitionTime = SystemClock.now();
    this.status = status;
    return this;
  }

  /**
   * Sets the condition status to a boolean value, which will be converted to a standard string.
   * @param status the new status value
   * @return this object
   */
  public DomainCondition withStatus(boolean status) {
    assert status || ! type.statusMustBeTrue() : "Attempt to set illegal status value";
    lastTransitionTime = SystemClock.now();
    this.status = status ? TRUE : FALSE;
    return this;
  }

  /**
   * The type of the condition. Required.
   *
   * @return type
   */
  public DomainConditionType getType() {
    return type;
  }

  public boolean hasType(DomainConditionType type) {
    return type == this.type;
  }

  @Override
  public boolean isPatchableFrom(DomainCondition other) {
    return false; // domain conditions are never patched
  }

  @Override
  public String toString() {
    return new ToStringBuilder(this)
        .append("lastProbeTime", lastProbeTime)
        .append("lastTransitionTime", lastTransitionTime)
        .append("message", message)
        .append("reason", reason)
        .append("status", status)
        .append("type", type)
        .toString();
  }

  @Override
  public int hashCode() {
    return new HashCodeBuilder()
        .append(reason)
        .append(message)
        .append(type)
        .append(status)
        .toHashCode();
  }

  @Override
  public boolean equals(Object other) {
    if (other == this) {
      return true;
    }
    if (!(other instanceof DomainCondition)) {
      return false;
    }
    DomainCondition rhs = ((DomainCondition) other);
    return new EqualsBuilder()
        .append(reason, rhs.reason)
        .append(message, rhs.message)
        .append(type, rhs.type)
        .append(status, rhs.status)
        .isEquals();
  }

  /**
   * Conditions are sorted, first in ascending order of type, and next in descending order of transition time.
   * @param o the condition against which to compare this one.
   */
  @Override
  public int compareTo(DomainCondition o) {
    return type == o.type
          ? -(lastTransitionTime.compareTo(o.lastTransitionTime))
          : type.compareTo(o.type);
  }

  private static final ObjectPatch<DomainCondition> conditionPatch = createObjectPatch(DomainCondition.class)
        .withStringField("message", DomainCondition::getMessage)
        .withStringField("reason", DomainCondition::getReason)
        .withStringField("status", DomainCondition::getStatus)
        .withEnumField("type", DomainCondition::getType);

  static ObjectPatch<DomainCondition> getObjectPatch() {
    return conditionPatch;
  }

  // Returns true if adding the specified condition should not remove this condition.
  boolean isCompatibleWith(DomainCondition newCondition) {
    return (newCondition.getType() != getType()) || getType().allowMultipleConditionsWithThisType();
  }

}

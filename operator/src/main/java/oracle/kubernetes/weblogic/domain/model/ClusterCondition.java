// Copyright (c) 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.weblogic.domain.model;

import java.time.OffsetDateTime;
import java.util.Optional;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;
import jakarta.validation.constraints.NotNull;
import oracle.kubernetes.json.Description;
import oracle.kubernetes.utils.SystemClock;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;

/** ClusterCondition contains details for the current condition of this cluster. */
public class ClusterCondition implements Comparable<ClusterCondition>, PatchableComponent<ClusterCondition> {

  public static final String TRUE = "True";
  public static final String FALSE = "False";

  @Description(
      "The type of the condition. Valid types are Completed, "
          + "Available, Failed, and Rolling.")
  @NotNull
  private final ClusterConditionType type;

  @Description("Last time the condition transitioned from one status to another.")
  @Expose
  @SerializedName("lastTransitionTime")
  private OffsetDateTime lastTransitionTime;

  @Description("Human-readable message indicating details about last transition.")
  @Expose
  @SerializedName("message")
  private String message;

  @Description("The status of the condition. Can be True, False.")
  @Expose
  @SerializedName("status")
  @NotNull
  private String status = "True";

  /**
   * Creates a new domain condition, initialized with its type.
   * @param conditionType the enum that designates the condition type
   */
  public ClusterCondition(ClusterConditionType conditionType) {
    lastTransitionTime = SystemClock.now();
    type = conditionType;
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
   * Last time we transitioned the condition.
   *
   * @param lastTransitionTime time
   */
  public void setLastTransitionTime(OffsetDateTime lastTransitionTime) {
    this.lastTransitionTime = lastTransitionTime;
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
   * The status of the condition. Can be True, False. Required.
   *
   * @return status
   */
  public String getStatus() {
    return status;
  }

  /**
   * The status of the condition. Can be True, False. Required.
   *
   * @param status the new status value
   * @return this object
   */
  public ClusterCondition withStatus(String status) {
    setLastTransitionTime(SystemClock.now());
    this.status = status;
    return this;
  }

  /**
   * Sets the condition status to a boolean value, which will be converted to a standard string.
   * @param status the new status value
   * @return this object
   */
  public ClusterCondition withStatus(boolean status) {
    setLastTransitionTime(SystemClock.now());
    this.status = status ? TRUE : FALSE;
    return this;
  }

  /**
   * The type of the condition. Required.
   *
   * @return type
   */
  public ClusterConditionType getType() {
    return type;
  }

  @Override
  public boolean isPatchableFrom(ClusterCondition other) {
    return false; // cluster conditions are never patched
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder("at ").append(lastTransitionTime).append(" ");
    Optional.ofNullable(type).ifPresent(sb::append);
    Optional.ofNullable(status).ifPresent(s -> sb.append("/").append(s));
    Optional.ofNullable(message).ifPresent(m -> sb.append(" message: ").append(m));
    return sb.toString();
  }

  @Override
  public int hashCode() {
    return new HashCodeBuilder()
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
    if (!(other instanceof ClusterCondition)) {
      return false;
    }
    ClusterCondition rhs = ((ClusterCondition) other);
    return new EqualsBuilder()
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
  public int compareTo(ClusterCondition o) {
    return type != o.type ? type.compareTo(o.type) : type.compare(this, o);
  }

  int compareTransitionTime(ClusterCondition thatCondition) {
    return thatCondition.lastTransitionTime.compareTo(lastTransitionTime);
  }

  // Returns true if adding the specified condition should not remove this condition.
  boolean isCompatibleWith(ClusterCondition newCondition) {
    return newCondition.getType() != getType();
  }
}

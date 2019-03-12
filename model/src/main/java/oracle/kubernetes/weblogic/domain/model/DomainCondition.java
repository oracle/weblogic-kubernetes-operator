// Copyright 2017, 2019, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.weblogic.domain.model;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;
import javax.validation.constraints.NotNull;
import oracle.kubernetes.json.Description;
import oracle.kubernetes.utils.SystemClock;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.joda.time.DateTime;

/** DomainCondition contains details for the current condition of this domain. */
public class DomainCondition implements Comparable<DomainCondition> {

  @Description("Last time we probed the condition.")
  @SerializedName("lastProbeTime")
  @Expose
  private DateTime lastProbeTime;

  @Description("Last time the condition transitioned from one status to another.")
  @SerializedName("lastTransitionTime")
  @Expose
  private DateTime lastTransitionTime;

  @Description("Human-readable message indicating details about last transition.")
  @SerializedName("message")
  @Expose
  private String message;

  @Description("Unique, one-word, CamelCase reason for the condition's last transition.")
  @SerializedName("reason")
  @Expose
  private String reason;

  @Description("Status is the status of the condition. Can be True, False, Unknown. Required")
  @SerializedName("status")
  @Expose
  @NotNull
  private String status;

  @Description(
      "The type of the condition. Valid types are Progressing, "
          + "Available, and Failed. Required")
  @NotNull
  private final DomainConditionType type;

  public DomainCondition(DomainConditionType conditionType) {
    lastTransitionTime = SystemClock.now();
    type = conditionType;
  }

  /**
   * Last time we probed the condition.
   *
   * @return time
   */
  public DateTime getLastProbeTime() {
    return lastProbeTime;
  }

  /**
   * Last time we probed the condition.
   *
   * @param lastProbeTime time
   */
  public void setLastProbeTime(DateTime lastProbeTime) {
    this.lastProbeTime = lastProbeTime;
  }

  /**
   * Last time we probed the condition.
   *
   * @param lastProbeTime time
   * @return this
   */
  public DomainCondition withLastProbeTime(DateTime lastProbeTime) {
    this.lastProbeTime = lastProbeTime;
    return this;
  }

  /**
   * Last time the condition transitioned from one status to another.
   *
   * @return time
   */
  public DateTime getLastTransitionTime() {
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
  public DomainCondition withReason(String reason) {
    lastTransitionTime = SystemClock.now();
    this.reason = reason;
    return this;
  }

  /**
   * Status is the status of the condition. Can be True, False, Unknown. (Required)
   *
   * @return status
   */
  public String getStatus() {
    return status;
  }

  /**
   * Status is the status of the condition. Can be True, False, Unknown. (Required)
   *
   * @param status status
   * @return this
   */
  public DomainCondition withStatus(String status) {
    lastTransitionTime = SystemClock.now();
    this.status = status;
    return this;
  }

  /**
   * Type is the type of the condition. Currently, valid types are Progressing, Available, and
   * Failure. (Required)
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

  @Override
  public int compareTo(DomainCondition o) {
    return type.compareTo(o.type);
  }
}

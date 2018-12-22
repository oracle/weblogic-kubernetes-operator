// Copyright 2017, 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.weblogic.domain.v2;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;
import javax.validation.constraints.NotNull;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.joda.time.DateTime;

/** DomainCondition contains details for the current condition of this domain. */
public class DomainCondition {

  /** Last time we probed the condition. */
  @SerializedName("lastProbeTime")
  @Expose
  private DateTime lastProbeTime;
  /** Last time the condition transitioned from one status to another. */
  @SerializedName("lastTransitionTime")
  @Expose
  private DateTime lastTransitionTime;
  /** Human-readable message indicating details about last transition. */
  @SerializedName("message")
  @Expose
  private String message;
  /** Unique, one-word, CamelCase reason for the condition's last transition. */
  @SerializedName("reason")
  @Expose
  private String reason;
  /** Status is the status of the condition. Can be True, False, Unknown. (Required) */
  @SerializedName("status")
  @Expose
  @NotNull
  private String status;
  /**
   * Type is the type of the condition. Currently, valid types are Progressing, Available, and
   * Failure. (Required)
   */
  @SerializedName("type")
  @Expose
  @NotNull
  private String type;

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
   * Last time the condition transitioned from one status to another.
   *
   * @param lastTransitionTime time
   */
  public void setLastTransitionTime(DateTime lastTransitionTime) {
    this.lastTransitionTime = lastTransitionTime;
  }

  /**
   * Last time the condition transitioned from one status to another.
   *
   * @param lastTransitionTime time
   * @return this
   */
  public DomainCondition withLastTransitionTime(DateTime lastTransitionTime) {
    this.lastTransitionTime = lastTransitionTime;
    return this;
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
   */
  public void setMessage(String message) {
    this.message = message;
  }

  /**
   * Human-readable message indicating details about last transition.
   *
   * @param message message
   * @return this
   */
  public DomainCondition withMessage(String message) {
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
   */
  public void setReason(String reason) {
    this.reason = reason;
  }

  /**
   * Unique, one-word, CamelCase reason for the condition's last transition.
   *
   * @param reason reason
   * @return this
   */
  public DomainCondition withReason(String reason) {
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
   */
  public void setStatus(String status) {
    this.status = status;
  }

  /**
   * Status is the status of the condition. Can be True, False, Unknown. (Required)
   *
   * @param status status
   * @return this
   */
  public DomainCondition withStatus(String status) {
    this.status = status;
    return this;
  }

  /**
   * Type is the type of the condition. Currently, valid types are Progressing, Available, and
   * Failure. (Required)
   *
   * @return type
   */
  public String getType() {
    return type;
  }

  /**
   * Type is the type of the condition. Currently, valid types are Progressing, Available, and
   * Failure. (Required)
   *
   * @param type type
   */
  public void setType(String type) {
    this.type = type;
  }

  /**
   * Type is the type of the condition. Currently, valid types are Progressing, Available, and
   * Failure. (Required)
   *
   * @param type type
   * @return this
   */
  public DomainCondition withType(String type) {
    this.type = type;
    return this;
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
        .append(lastTransitionTime)
        .append(message)
        .append(type)
        .append(lastProbeTime)
        .append(status)
        .toHashCode();
  }

  @Override
  public boolean equals(Object other) {
    if (other == this) {
      return true;
    }
    if ((other instanceof DomainCondition) == false) {
      return false;
    }
    DomainCondition rhs = ((DomainCondition) other);
    return new EqualsBuilder()
        .append(reason, rhs.reason)
        .append(lastTransitionTime, rhs.lastTransitionTime)
        .append(message, rhs.message)
        .append(type, rhs.type)
        .append(lastProbeTime, rhs.lastProbeTime)
        .append(status, rhs.status)
        .isEquals();
  }
}

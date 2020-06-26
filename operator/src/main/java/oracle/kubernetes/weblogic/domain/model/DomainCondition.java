// Copyright (c) 2017, 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.weblogic.domain.model;

import javax.validation.constraints.NotNull;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;
import oracle.kubernetes.json.Description;
import oracle.kubernetes.utils.SystemClock;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.joda.time.DateTime;

import static oracle.kubernetes.weblogic.domain.model.ObjectPatch.createObjectPatch;

/** DomainCondition contains details for the current condition of this domain. */
public class DomainCondition implements Comparable<DomainCondition>, PatchableComponent<DomainCondition> {

  @Description(
      "The type of the condition. Valid types are Progressing, "
          + "Available, and Failed.")
  @NotNull
  private final DomainConditionType type;

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

  @Description("The status of the condition. Can be True, False, Unknown.")
  @SerializedName("status")
  @Expose
  @NotNull
  private String status;

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
   * Returns the reason to set on the domain status when this condition is added.
   * @return a reason or null
   */
  String getStatusReason() {
    return getType().getStatusReason(this);
  }

  /**
   * Returns the message to set on the domain status when this condition is added.
   * @return a message or null
   */
  String getStatusMessage() {
    return getType().getStatusMessage(this);
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
   * Failure. Required.
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

  @Override
  public int compareTo(DomainCondition o) {
    return type.compareTo(o.type);
  }

  private static final ObjectPatch<DomainCondition> conditionPatch = createObjectPatch(DomainCondition.class)
        .withStringField("message", DomainCondition::getMessage)
        .withStringField("reason", DomainCondition::getReason)
        .withStringField("status", DomainCondition::getStatus)
        .withEnumField("type", DomainCondition::getType);

  static ObjectPatch<DomainCondition> getObjectPatch() {
    return conditionPatch;
  }

}

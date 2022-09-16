// Copyright (c) 2017, 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.weblogic.domain.model;

import java.time.OffsetDateTime;
import java.util.Optional;
import javax.annotation.Nonnull;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;
import oracle.kubernetes.json.Description;
import oracle.kubernetes.utils.SystemClock;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;

import static oracle.kubernetes.weblogic.domain.model.DomainConditionType.FAILED;
import static oracle.kubernetes.weblogic.domain.model.DomainFailureSeverity.FATAL;
import static oracle.kubernetes.weblogic.domain.model.DomainFailureSeverity.SEVERE;
import static oracle.kubernetes.weblogic.domain.model.ObjectPatch.createObjectPatch;

/** DomainCondition contains details for the current condition of this domain. */
public class DomainCondition implements Comparable<DomainCondition>, PatchableComponent<DomainCondition> {

  public static final String TRUE = "True";
  public static final String FALSE = "False";

  @Description(
      "The type of the condition. Valid types are Completed, "
          + "Available, Failed, Rolling, and ConfigChangesPendingRestart.")
  @Nonnull
  private final DomainConditionType type;

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
  private DomainFailureReason reason;

  @Description("The status of the condition. Can be True, False, Unknown.")
  @SerializedName("status")
  @Expose
  @Nonnull
  private String status = "True";

  // internal: used to select failure conditions for deletion
  private volatile boolean markedForDeletion;

  @Description("The severity of the failure. Can be Fatal, Severe or Warning.")
  @Expose
  private DomainFailureSeverity severity;

  /**
   * Creates a new domain condition, initialized with its type.
   * @param conditionType the enum that designates the condition type
   */
  public DomainCondition(@Nonnull DomainConditionType conditionType) {
    lastTransitionTime = SystemClock.now();
    type = conditionType;
  }

  DomainCondition(DomainCondition other) {
    this.type = other.type;
    this.lastTransitionTime = other.lastTransitionTime;
    this.message = other.message;
    this.reason = other.reason;
    this.status = other.status;
    this.markedForDeletion = other.markedForDeletion;
    this.severity = other.severity;
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
   * Last time we lastTransitionTime the condition.
   *
   * @param lastTransitionTime time
   * @return this
   */
  public DomainCondition withLastTransitionTime(OffsetDateTime lastTransitionTime) {
    setLastTransitionTime(lastTransitionTime);
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
   * @return this
   */
  public DomainCondition withMessage(@Nonnull String message) {
    setLastTransitionTime(SystemClock.now());
    this.message = message;
    if (reason != null && DomainFailureReason.isFatalError(reason, message)) {
      severity = FATAL;
    }
    return this;
  }

  /**
   * Unique, one-word, CamelCase reason for the condition's last transition.
   *
   * @return reason
   */
  public DomainFailureReason getReason() {
    return reason;
  }

  /**
   * Unique, one-word, CamelCase reason for the condition's last transition.
   *
   * @param reason reason
   * @return this object
   */
  public DomainCondition withReason(DomainFailureReason reason) {
    if (message != null) {
      throw new IllegalStateException("May not set reason after message");
    }
    
    setLastTransitionTime(SystemClock.now());
    this.reason = reason;
    this.severity = Optional.ofNullable(reason).map(DomainFailureReason::getDefaultSeverity).orElseThrow();
    return this;
  }

  /**
   * The status of the condition. Can be True, False, Unknown. Required.
   *
   * @return status
   */
  public @Nonnull String getStatus() {
    return status;
  }

  /**
   * The status of the condition. Can be True, False, Unknown. Required.
   *
   * @param status the new status value
   * @return this object
   */
  public DomainCondition withStatus(String status) {
    setLastTransitionTime(SystemClock.now());
    this.status = status;
    return this;
  }

  /**
   * Sets the condition status to a boolean value, which will be converted to a standard string.
   * @param status the new status value
   * @return this object
   */
  public DomainCondition withStatus(boolean status) {
    setLastTransitionTime(SystemClock.now());
    this.status = status ? TRUE : FALSE;
    return this;
  }

  /**
   * The type of the condition. Required.
   *
   * @return type
   */
  public @Nonnull DomainConditionType getType() {
    return type;
  }

  /**
   * Set the severity for the current FAILED condition. This is not allowed for other types.
   * @param severity the new severity value
   */
  public void setSeverity(DomainFailureSeverity severity) {
    if (!type.equals(FAILED)) {
      throw new IllegalStateException("May not set severity on a condition of type " + type);
    }

    this.severity = severity;
  }

  public DomainFailureSeverity getSeverity() {
    return severity;
  }

  public boolean hasType(DomainConditionType type) {
    return type == this.type;
  }

  public boolean isRetriableFailure() {
    return getType() == FAILED && getSeverity() == SEVERE;
  }

  boolean isMarkedForDeletion() {
    return markedForDeletion;
  }

  void markForDeletion() {
    this.markedForDeletion = true;
  }

  void unMarkForDeletion() {
    this.markedForDeletion = false;
  }

  @Override
  public boolean isPatchableFrom(DomainCondition other) {
    return false; // domain conditions are never patched
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder("at ").append(lastTransitionTime).append(" ");
    sb.append(type).append('/').append(status);
    Optional.ofNullable(reason).ifPresent(r -> sb.append(" reason: ").append(r));
    Optional.ofNullable(severity).ifPresent(m -> sb.append(" severity: ").append(m));
    Optional.ofNullable(message).ifPresent(m -> sb.append(" message: ").append(m));
    return sb.toString();
  }

  @Override
  public int hashCode() {
    return new HashCodeBuilder()
        .append(reason)
        .append(message)
        .append(type)
        .append(status)
        .append(severity)
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
        .append(severity, rhs.severity)
        .isEquals();
  }

  /**
   * Conditions are sorted, first in ascending order of type, and next in descending order of transition time.
   * @param o the condition against which to compare this one.
   */
  @Override
  public int compareTo(DomainCondition o) {
    return type != o.type ? type.compareTo(o.type) : type.compare(this, o);
  }

  int compareTransitionTime(DomainCondition thatCondition) {
    return thatCondition.lastTransitionTime.compareTo(lastTransitionTime);
  }

  private static final ObjectPatch<DomainCondition> conditionPatch = createObjectPatch(DomainCondition.class)
        .withStringField("message", DomainCondition::getMessage)
        .withStringField("status", DomainCondition::getStatus)
        .withEnumField("reason", DomainCondition::getReason)
        .withEnumField("type", DomainCondition::getType)
        .withEnumField("severity", DomainCondition::getSeverity);

  static ObjectPatch<DomainCondition> getObjectPatch() {
    return conditionPatch;
  }

  // Returns true if adding the specified condition should not remove this condition.
  boolean isCompatibleWith(DomainCondition newCondition) {
    return (newCondition.getType() != getType()) || getType().allowMultipleConditionsWithThisType();
  }

  boolean isSpecifiedFailure(DomainFailureReason reason) {
    return hasType(FAILED) && reason == this.reason;
  }

  public boolean isNotValid() {
    return hasType(FAILED) && reason == null;
  }
}

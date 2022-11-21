// Copyright (c) 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.domain;

import java.time.OffsetDateTime;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;

@ApiModel(
    description = "ClusterCondition contains details for the current condition of this cluster.")
public class ClusterCondition {

  @ApiModelProperty(
      "The type of the condition. Valid types are  "
          + "Available, Failed, and Rolling.")
  private String type;

  @ApiModelProperty("Last time the condition transitioned from one status to another.")
  private OffsetDateTime lastTransitionTime;

  @ApiModelProperty("Human-readable message indicating details about last transition.")
  private String message;

  @ApiModelProperty("Status is the status of the condition. Can be True, False, Unknown. Required.")
  private String status;

  public ClusterCondition type(String type) {
    this.type = type;
    return this;
  }

  public String type() {
    return type;
  }

  public String getType() {
    return type;
  }

  public void setType(String type) {
    this.type = type;
  }

  public ClusterCondition lastTransitionTime(OffsetDateTime lastTransitionTime) {
    this.lastTransitionTime = lastTransitionTime;
    return this;
  }

  public OffsetDateTime lastTransitionTime() {
    return lastTransitionTime;
  }

  public OffsetDateTime getLastTransitionTime() {
    return lastTransitionTime;
  }

  public void setLastTransitionTime(OffsetDateTime lastTransitionTime) {
    this.lastTransitionTime = lastTransitionTime;
  }

  public ClusterCondition message(String message) {
    this.message = message;
    return this;
  }

  public String message() {
    return message;
  }

  public String getMessage() {
    return message;
  }

  public void setMessage(String message) {
    this.message = message;
  }

  public ClusterCondition status(String status) {
    this.status = status;
    return this;
  }

  public String status() {
    return status;
  }

  public String getStatus() {
    return status;
  }

  public void setStatus(String status) {
    this.status = status;
  }

  @Override
  public String toString() {
    return new ToStringBuilder(this)
        .append("type", type)
        .append("lastTransitionTime", lastTransitionTime)
        .append("message", message)
        .append("status", status)
        .toString();
  }

  @Override
  public int hashCode() {
    return new HashCodeBuilder()
        .append(type)
        .append(lastTransitionTime)
        .append(message)
        .append(status)
        .toHashCode();
  }

  @Override
  public boolean equals(Object other) {
    if (this == other) {
      return true;
    }

    if (other == null || getClass() != other.getClass()) {
      return false;
    }
    ClusterCondition rhs = (ClusterCondition) other;
    return new EqualsBuilder()
        .append(type, rhs.type)
        .append(lastTransitionTime, rhs.lastTransitionTime)
        .append(message, rhs.message)
        .append(status, rhs.status)
        .isEquals();
  }
}

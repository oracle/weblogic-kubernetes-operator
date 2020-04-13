// Copyright (c) 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.domain;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.joda.time.DateTime;

@ApiModel(description = "DomainCondition contains details for the current condition of this domain.")
public class DomainCondition {

  @ApiModelProperty(
      "The type of the condition. Valid types are Progressing, "
          + "Available, and Failed. Required.")
  private String type;

  @ApiModelProperty("Last time we probed the condition.")
  private DateTime lastProbeTime;

  @ApiModelProperty("Last time the condition transitioned from one status to another.")
  private DateTime lastTransitionTime;

  @ApiModelProperty("Human-readable message indicating details about last transition.")
  private String message;

  @ApiModelProperty("Unique, one-word, CamelCase reason for the condition's last transition.")
  private String reason;

  @ApiModelProperty("Status is the status of the condition. Can be True, False, Unknown. Required.")
  private String status;

  public DomainCondition type(String type) {
    this.type = type;
    return this;
  }

  public String type() {
    return type;
  }

  public DomainCondition lastProbeTime(DateTime lastProbeTime) {
    this.lastProbeTime = lastProbeTime;
    return this;
  }

  public DateTime lastProbeTime() {
    return lastProbeTime;
  }

  public DomainCondition lastTransitionTime(DateTime lastTransitionTime) {
    this.lastTransitionTime = lastTransitionTime;
    return this;
  }

  public DateTime lastTransitionTime() {
    return lastTransitionTime;
  }

  public DomainCondition message(String message) {
    this.message = message;
    return this;
  }

  public String message() {
    return message;
  }

  public DomainCondition reason(String reason) {
    this.reason = reason;
    return this;
  }

  public String reason() {
    return reason;
  }

  public DomainCondition status(String status) {
    this.status = status;
    return this;
  }

  public String status() {
    return status;
  }

  @Override
  public String toString() {
    return new ToStringBuilder(this)
        .append("type", type)
        .append("lastProbeTime", lastProbeTime)
        .append("lastTransitionTime", lastTransitionTime)
        .append("message", message)
        .append("reason", reason)
        .append("status", status)
        .toString();
  }

  @Override
  public int hashCode() {
    return new HashCodeBuilder()
        .append(type)
        .append(lastProbeTime)
        .append(lastTransitionTime)
        .append(reason)
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
    DomainCondition rhs = (DomainCondition) other;
    return new EqualsBuilder()
        .append(type, rhs.type)
        .append(lastProbeTime, rhs.lastProbeTime)
        .append(lastTransitionTime, rhs.lastTransitionTime)
        .append(reason, rhs.reason)
        .append(message, rhs.message)
        .append(status, rhs.status)
        .isEquals();
  }

}

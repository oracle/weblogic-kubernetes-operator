// Copyright (c) 2020, 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.domain;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;

@ApiModel(description = "Shutdown describes the configuration for shutting down a server instance.")
public class Shutdown {

  @ApiModelProperty(
      value =
          "Tells the operator how to shutdown server instances. Not required."
              + " Defaults to graceful shutdown.",
      allowableValues = "Graceful, Forced")
  private String shutdownType;

  @ApiModelProperty(
      "For graceful shutdown only, number of seconds to wait before aborting in-flight work and shutting down"
          + " the server. Not required. Defaults to 30 seconds.")
  private Long timeoutSeconds;

  @ApiModelProperty(
      "For graceful shutdown only, indicates to ignore pending HTTP sessions during in-flight work handling."
          + " Not required. Defaults to false.")
  private Boolean ignoreSessions;

  @ApiModelProperty(
      "For graceful shutdown only, set to true to wait for all HTTP sessions"
          + " during in-flight work handling; false to wait for non-persisted HTTP sessions only."
          + " Defaults to false.")
  private Boolean waitForAllSessions;

  public Shutdown shutdownType(String shutdownType) {
    this.shutdownType = shutdownType;
    return this;
  }

  public String shutdownType() {
    return shutdownType;
  }

  public String getShutdownType() {
    return shutdownType;
  }

  public void setShutdownType(String shutdownType) {
    this.shutdownType = shutdownType;
  }

  public Shutdown timeoutSeconds(Long timeoutSeconds) {
    this.timeoutSeconds = timeoutSeconds;
    return this;
  }

  public Long timeoutSeconds() {
    return timeoutSeconds;
  }

  public Long getTimeoutSeconds() {
    return timeoutSeconds;
  }

  public void setTimeoutSeconds(Long timeoutSeconds) {
    this.timeoutSeconds = timeoutSeconds;
  }

  public Shutdown ignoreSessions(Boolean ignoreSessions) {
    this.ignoreSessions = ignoreSessions;
    return this;
  }

  public Boolean ignoreSessions() {
    return ignoreSessions;
  }

  public Boolean getIgnoreSessions() {
    return ignoreSessions;
  }

  public void setIgnoreSessions(Boolean ignoreSessions) {
    this.ignoreSessions = ignoreSessions;
  }

  public Shutdown waitForAllSessions(Boolean waitForAllSessions) {
    this.waitForAllSessions = waitForAllSessions;
    return this;
  }

  public Boolean waitForAllSessions() {
    return waitForAllSessions;
  }

  public Boolean getWaitForAllSessions() {
    return waitForAllSessions;
  }

  public void setWaitForAllSessions(Boolean waitForAllSessions) {
    this.waitForAllSessions = waitForAllSessions;
  }

  @Override
  public String toString() {
    return new ToStringBuilder(this)
        .append("shutdownType", shutdownType)
        .append("timeoutSeconds", timeoutSeconds)
        .append("ignoreSessions", ignoreSessions)
        .append("waitForAllSessions", waitForAllSessions)
        .toString();
  }

  @Override
  public boolean equals(Object other) {
    if (this == other) {
      return true;
    }

    if (other == null || getClass() != other.getClass()) {
      return false;
    }
    Shutdown rhs = (Shutdown) other;
    return new EqualsBuilder()
        .append(shutdownType, rhs.shutdownType)
        .append(timeoutSeconds, rhs.timeoutSeconds)
        .append(ignoreSessions, rhs.ignoreSessions)
        .append(waitForAllSessions, rhs.waitForAllSessions)
        .isEquals();
  }

  @Override
  public int hashCode() {
    return new HashCodeBuilder(17, 37)
        .append(shutdownType)
        .append(timeoutSeconds)
        .append(ignoreSessions)
        .append(waitForAllSessions)
        .toHashCode();
  }
}

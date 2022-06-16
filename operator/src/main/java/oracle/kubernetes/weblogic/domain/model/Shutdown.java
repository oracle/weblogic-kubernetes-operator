// Copyright (c) 2019, 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.weblogic.domain.model;

import java.util.Optional;

import oracle.kubernetes.json.Default;
import oracle.kubernetes.json.Description;
import oracle.kubernetes.operator.ShutdownType;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;

public class Shutdown {
  // Default timeout must stay 30 seconds to match Kubernetes default
  public static final Long DEFAULT_TIMEOUT = 30L;
  public static final Boolean DEFAULT_IGNORESESSIONS = Boolean.FALSE;
  public static final Boolean DEFAULT_WAIT_FOR_ALL_SESSIONS = Boolean.FALSE;

  @Description(
      "Specifies how the operator will shut down server instances."
          + " Legal values are `Graceful` and `Forced`. Defaults to `Graceful`.")
  @Default(strDefault = "Graceful")
  private ShutdownType shutdownType;

  @Description(
      "For graceful shutdown only, number of seconds to wait before aborting in-flight work and shutting down"
          + " the server. Defaults to 30 seconds.")
  @Default(intDefault = 30)
  private Long timeoutSeconds;

  @Description(
      "For graceful shutdown only, indicates to ignore pending HTTP sessions during in-flight work handling."
          + " Defaults to false.")
  @Default(boolDefault = false)
  private Boolean ignoreSessions;

  @Description(
      "For graceful shutdown only, set to true to wait for all HTTP sessions"
          + " during in-flight work handling; false to wait for non-persisted"
          + " HTTP sessions only."
          + " Defaults to false.")
  @Default(boolDefault = false)
  private Boolean waitForAllSessions;

  void copyValues(Shutdown fromShutdown) {
    if (shutdownType == null) {
      shutdownType(fromShutdown.shutdownType);
    }
    if (timeoutSeconds == null) {
      timeoutSeconds(fromShutdown.timeoutSeconds);
    }
    if (ignoreSessions == null) {
      ignoreSessions(fromShutdown.ignoreSessions);
    }

    if (waitForAllSessions == null) {
      waitForAllSessions(fromShutdown.waitForAllSessions);
    }
  }

  public ShutdownType getShutdownType() {
    return Optional.ofNullable(shutdownType).orElse(ShutdownType.GRACEFUL);
  }

  public Shutdown shutdownType(ShutdownType shutdownType) {
    this.shutdownType = shutdownType;
    return this;
  }

  public Long getTimeoutSeconds() {
    return Optional.ofNullable(timeoutSeconds).orElse(DEFAULT_TIMEOUT);
  }

  public Shutdown timeoutSeconds(Long timeoutSeconds) {
    this.timeoutSeconds = timeoutSeconds;
    return this;
  }

  public Boolean getIgnoreSessions() {
    return Optional.ofNullable(ignoreSessions).orElse(DEFAULT_IGNORESESSIONS);
  }

  public Shutdown ignoreSessions(Boolean ignoreSessions) {
    this.ignoreSessions = ignoreSessions;
    return this;
  }

  public Boolean getWaitForAllSessions() {
    return Optional.ofNullable(waitForAllSessions).orElse(DEFAULT_WAIT_FOR_ALL_SESSIONS);
  }

  public Shutdown waitForAllSessions(Boolean waitForAllSessions) {
    this.waitForAllSessions = waitForAllSessions;
    return this;
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
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }

    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    Shutdown that = (Shutdown) o;

    return new EqualsBuilder()
        .append(shutdownType, that.shutdownType)
        .append(timeoutSeconds, that.timeoutSeconds)
        .append(ignoreSessions, that.ignoreSessions)
        .append(waitForAllSessions, that.waitForAllSessions)
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

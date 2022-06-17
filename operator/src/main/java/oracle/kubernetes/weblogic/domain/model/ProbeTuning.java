// Copyright (c) 2018, 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.weblogic.domain.model;

import com.google.gson.annotations.SerializedName;
import oracle.kubernetes.json.Default;
import oracle.kubernetes.json.Description;
import oracle.kubernetes.json.Range;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;

public class ProbeTuning {
  @Description("The number of seconds before the first check is performed.")
  private Integer initialDelaySeconds = null;

  @Description("The number of seconds between checks.")
  @SerializedName("periodSeconds")
  private Integer periodSeconds = null;

  @Description("The number of seconds with no response that indicates a failure.")
  @SerializedName("timeoutSeconds")
  private Integer timeoutSeconds = null;

  @Description("Number of times the check is performed before giving up. Giving up in "
          + "case of liveness probe means restarting the container. In case of readiness probe, the Pod will be "
          + "marked Unready. Defaults to 1.")
  @SerializedName("failureThreshold")
  @Range(minimum = 1)
  @Default(intDefault = 1)
  Integer failureThreshold = null;

  @Description("Minimum number of times the check needs to pass for the probe to be considered successful"
          + " after having failed. Defaults to 1. Must be 1 for liveness Probe.")
  @SerializedName("successThreshold")
  @Range(minimum = 1)
  @Default(intDefault = 1)
  private Integer successThreshold = null;

  void copyValues(ProbeTuning fromProbe) {
    if (initialDelaySeconds == null) {
      initialDelaySeconds(fromProbe.initialDelaySeconds);
    }
    if (timeoutSeconds == null) {
      timeoutSeconds(fromProbe.timeoutSeconds);
    }
    if (periodSeconds == null) {
      periodSeconds(fromProbe.periodSeconds);
    }
    if (successThreshold == null) {
      successThreshold(fromProbe.successThreshold);
    }
    if (failureThreshold == null) {
      failureThreshold(fromProbe.failureThreshold);
    }
  }

  public Integer getInitialDelaySeconds() {
    return initialDelaySeconds;
  }

  public ProbeTuning initialDelaySeconds(Integer initialDelaySeconds) {
    this.initialDelaySeconds = initialDelaySeconds;
    return this;
  }

  public Integer getPeriodSeconds() {
    return periodSeconds;
  }

  public ProbeTuning periodSeconds(Integer periodSeconds) {
    this.periodSeconds = periodSeconds;
    return this;
  }

  public Integer getTimeoutSeconds() {
    return timeoutSeconds;
  }

  public ProbeTuning timeoutSeconds(Integer timeoutSeconds) {
    this.timeoutSeconds = timeoutSeconds;
    return this;
  }

  public Integer getSuccessThreshold() {
    return successThreshold;
  }

  public ProbeTuning successThreshold(Integer successThreshold) {
    this.successThreshold = successThreshold;
    return this;
  }

  public Integer getFailureThreshold() {
    return failureThreshold;
  }

  public ProbeTuning failureThreshold(Integer failureThreshold) {
    this.failureThreshold = failureThreshold;
    return this;
  }

  @Override
  public String toString() {
    return new ToStringBuilder(this)
        .append("initialDelaySeconds", initialDelaySeconds)
        .append("periodSeconds", periodSeconds)
        .append("timeoutSeconds", timeoutSeconds)
        .append("successThreshold", successThreshold)
        .append("failureThreshold", failureThreshold)
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

    ProbeTuning that = (ProbeTuning) o;

    return new EqualsBuilder()
        .append(initialDelaySeconds, that.initialDelaySeconds)
        .append(periodSeconds, that.periodSeconds)
        .append(timeoutSeconds, that.timeoutSeconds)
        .append(successThreshold, that.successThreshold)
        .append(failureThreshold, that.failureThreshold)
        .isEquals();
  }

  @Override
  public int hashCode() {
    return new HashCodeBuilder(17, 37)
        .append(initialDelaySeconds)
        .append(periodSeconds)
        .append(timeoutSeconds)
        .append(successThreshold)
        .append(failureThreshold)
        .toHashCode();
  }
}

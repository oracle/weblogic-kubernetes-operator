// Copyright 2017, 2019, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;

public interface TuningParameters extends Map<String, String> {

  static TuningParameters initializeInstance(
      ScheduledExecutorService executorService, String mountPoint) throws IOException {
    return TuningParametersImpl.initializeInstance(executorService, mountPoint);
  }

  public static TuningParameters getInstance() {
    return TuningParametersImpl.getInstance();
  }

  public static class MainTuning {
    public final int domainPresenceFailureRetrySeconds;
    public final int domainPresenceFailureRetryMaxCount;
    public final int domainPresenceRecheckIntervalSeconds;
    public final int targetNamespaceRecheckIntervalSeconds;
    public final int statusUpdateTimeoutSeconds;
    public final int unchangedCountToDelayStatusRecheck;
    public final long initialShortDelay;
    public final long eventualLongDelay;

    public MainTuning(
        int domainPresenceFailureRetrySeconds,
        int domainPresenceFailureRetryMaxCount,
        int domainPresenceRecheckIntervalSeconds,
        int targetNamespaceRecheckIntervalSeconds,
        int statusUpdateTimeoutSeconds,
        int unchangedCountToDelayStatusRecheck,
        long initialShortDelay,
        long eventualLongDelay) {
      this.domainPresenceFailureRetrySeconds = domainPresenceFailureRetrySeconds;
      this.domainPresenceFailureRetryMaxCount = domainPresenceFailureRetryMaxCount;
      this.domainPresenceRecheckIntervalSeconds = domainPresenceRecheckIntervalSeconds;
      this.targetNamespaceRecheckIntervalSeconds = targetNamespaceRecheckIntervalSeconds;
      this.statusUpdateTimeoutSeconds = statusUpdateTimeoutSeconds;
      this.unchangedCountToDelayStatusRecheck = unchangedCountToDelayStatusRecheck;
      this.initialShortDelay = initialShortDelay;
      this.eventualLongDelay = eventualLongDelay;
    }

    @Override
    public String toString() {
      return new ToStringBuilder(this)
          .append("domainPresenceFailureRetrySeconds", domainPresenceFailureRetrySeconds)
          .append("domainPresenceFailureRetryMaxCount", domainPresenceFailureRetryMaxCount)
          .append("domainPresenceRecheckIntervalSeconds", domainPresenceRecheckIntervalSeconds)
          .append("targetNamespaceRecheckIntervalSeconds", targetNamespaceRecheckIntervalSeconds)
          .append("statusUpdateTimeoutSeconds", statusUpdateTimeoutSeconds)
          .append("unchangedCountToDelayStatusRecheck", unchangedCountToDelayStatusRecheck)
          .append("initialShortDelay", initialShortDelay)
          .append("eventualLongDelay", eventualLongDelay)
          .toString();
    }

    @Override
    public int hashCode() {
      return new HashCodeBuilder()
          .append(domainPresenceFailureRetrySeconds)
          .append(domainPresenceFailureRetryMaxCount)
          .append(domainPresenceRecheckIntervalSeconds)
          .append(targetNamespaceRecheckIntervalSeconds)
          .append(statusUpdateTimeoutSeconds)
          .append(unchangedCountToDelayStatusRecheck)
          .append(initialShortDelay)
          .append(eventualLongDelay)
          .toHashCode();
    }

    @Override
    public boolean equals(Object o) {
      if (o == null) {
        return false;
      }
      if (!(o instanceof MainTuning)) {
        return false;
      }
      MainTuning mt = (MainTuning) o;
      return new EqualsBuilder()
          .append(domainPresenceFailureRetrySeconds, mt.domainPresenceFailureRetrySeconds)
          .append(domainPresenceFailureRetryMaxCount, mt.domainPresenceFailureRetryMaxCount)
          .append(domainPresenceRecheckIntervalSeconds, mt.domainPresenceRecheckIntervalSeconds)
          .append(targetNamespaceRecheckIntervalSeconds, mt.targetNamespaceRecheckIntervalSeconds)
          .append(statusUpdateTimeoutSeconds, mt.statusUpdateTimeoutSeconds)
          .append(unchangedCountToDelayStatusRecheck, mt.unchangedCountToDelayStatusRecheck)
          .append(initialShortDelay, mt.initialShortDelay)
          .append(eventualLongDelay, mt.eventualLongDelay)
          .isEquals();
    }
  }

  public static class CallBuilderTuning {
    public final int callRequestLimit;
    public final int callMaxRetryCount;
    public final int callTimeoutSeconds;

    public CallBuilderTuning(int callRequestLimit, int callMaxRetryCount, int callTimeoutSeconds) {
      this.callRequestLimit = callRequestLimit;
      this.callMaxRetryCount = callMaxRetryCount;
      this.callTimeoutSeconds = callTimeoutSeconds;
    }

    @Override
    public String toString() {
      return new ToStringBuilder(this)
          .append("callRequestLimit", callRequestLimit)
          .append("callMaxRetryCount", callMaxRetryCount)
          .append("callTimeoutSeconds", callTimeoutSeconds)
          .toString();
    }

    @Override
    public int hashCode() {
      return new HashCodeBuilder()
          .append(callRequestLimit)
          .append(callMaxRetryCount)
          .append(callTimeoutSeconds)
          .toHashCode();
    }

    @Override
    public boolean equals(Object o) {
      if (o == null) {
        return false;
      }
      if (!(o instanceof CallBuilderTuning)) {
        return false;
      }
      CallBuilderTuning cbt = (CallBuilderTuning) o;
      return new EqualsBuilder()
          .append(callRequestLimit, cbt.callRequestLimit)
          .append(callMaxRetryCount, cbt.callMaxRetryCount)
          .append(callTimeoutSeconds, cbt.callTimeoutSeconds)
          .isEquals();
    }
  }

  public static class WatchTuning {
    public final int watchLifetime;
    public final int watchMinimumDelay;

    public WatchTuning(int watchLifetime, int watchMinimumDelay) {
      this.watchLifetime = watchLifetime;
      this.watchMinimumDelay = watchMinimumDelay;
    }

    @Override
    public String toString() {
      return new ToStringBuilder(this)
          .append("watchLifetime", watchLifetime)
          .append("watchMinimumDelay", watchMinimumDelay)
          .toString();
    }

    @Override
    public int hashCode() {
      return new HashCodeBuilder().append(watchLifetime).append(watchMinimumDelay).toHashCode();
    }

    @Override
    public boolean equals(Object o) {
      if (o == null) {
        return false;
      }
      if (!(o instanceof WatchTuning)) {
        return false;
      }
      WatchTuning wt = (WatchTuning) o;
      return new EqualsBuilder()
          .append(watchLifetime, wt.watchLifetime)
          .append(watchMinimumDelay, wt.watchMinimumDelay)
          .isEquals();
    }
  }

  public static class PodTuning {
    public final int readinessProbeInitialDelaySeconds;
    public final int readinessProbeTimeoutSeconds;
    public final int readinessProbePeriodSeconds;
    public final int livenessProbeInitialDelaySeconds;
    public final int livenessProbeTimeoutSeconds;
    public final int livenessProbePeriodSeconds;

    public PodTuning(
        int readinessProbeInitialDelaySeconds,
        int readinessProbeTimeoutSeconds,
        int readinessProbePeriodSeconds,
        int livenessProbeInitialDelaySeconds,
        int livenessProbeTimeoutSeconds,
        int livenessProbePeriodSeconds) {
      this.readinessProbeInitialDelaySeconds = readinessProbeInitialDelaySeconds;
      this.readinessProbeTimeoutSeconds = readinessProbeTimeoutSeconds;
      this.readinessProbePeriodSeconds = readinessProbePeriodSeconds;
      this.livenessProbeInitialDelaySeconds = livenessProbeInitialDelaySeconds;
      this.livenessProbeTimeoutSeconds = livenessProbeTimeoutSeconds;
      this.livenessProbePeriodSeconds = livenessProbePeriodSeconds;
    }

    @Override
    public String toString() {
      return new ToStringBuilder(this)
          .append("readinessProbeInitialDelaySeconds", readinessProbeInitialDelaySeconds)
          .append("readinessProbeTimeoutSeconds", readinessProbeTimeoutSeconds)
          .append("readinessProbePeriodSeconds", readinessProbePeriodSeconds)
          .append("livenessProbeInitialDelaySeconds", livenessProbeInitialDelaySeconds)
          .append("livenessProbeTimeoutSeconds", livenessProbeTimeoutSeconds)
          .append("livenessProbePeriodSeconds", livenessProbePeriodSeconds)
          .toString();
    }

    @Override
    public int hashCode() {
      return new HashCodeBuilder()
          .append(readinessProbeInitialDelaySeconds)
          .append(readinessProbeTimeoutSeconds)
          .append(readinessProbePeriodSeconds)
          .append(livenessProbeInitialDelaySeconds)
          .append(livenessProbeTimeoutSeconds)
          .append(livenessProbePeriodSeconds)
          .toHashCode();
    }

    @Override
    public boolean equals(Object o) {
      if (o == null) {
        return false;
      }
      if (!(o instanceof PodTuning)) {
        return false;
      }
      PodTuning pt = (PodTuning) o;
      return new EqualsBuilder()
          .append(readinessProbeInitialDelaySeconds, pt.readinessProbeInitialDelaySeconds)
          .append(readinessProbeTimeoutSeconds, pt.readinessProbeTimeoutSeconds)
          .append(readinessProbePeriodSeconds, pt.readinessProbePeriodSeconds)
          .append(livenessProbeInitialDelaySeconds, pt.livenessProbeInitialDelaySeconds)
          .append(livenessProbeTimeoutSeconds, pt.livenessProbeTimeoutSeconds)
          .append(livenessProbePeriodSeconds, pt.livenessProbePeriodSeconds)
          .isEquals();
    }
  }

  public MainTuning getMainTuning();

  public CallBuilderTuning getCallBuilderTuning();

  public WatchTuning getWatchTuning();

  public PodTuning getPodTuning();
}

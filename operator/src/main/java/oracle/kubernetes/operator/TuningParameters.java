// Copyright (c) 2017, 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator;

import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;

public interface TuningParameters extends Map<String, String> {

  static TuningParameters initializeInstance(
      ScheduledExecutorService executorService, File mountPoint) {
    return TuningParametersImpl.initializeInstance(executorService, mountPoint);
  }

  static TuningParameters getInstance() {
    return TuningParametersImpl.getInstance();
  }

  MainTuning getMainTuning();

  CallBuilderTuning getCallBuilderTuning();

  WatchTuning getWatchTuning();

  PodTuning getPodTuning();

  FeatureGates getFeatureGates();

  String getKubernetesPlatform();

  class MainTuning {
    public final int initializationRetryDelaySeconds;
    public final int domainPresenceFailureRetrySeconds;
    public final int domainPresenceFailureRetryMaxCount;
    public final int domainPresenceRecheckIntervalSeconds;
    public final int domainNamespaceRecheckIntervalSeconds;
    public final int statusUpdateTimeoutSeconds;
    public final int unchangedCountToDelayStatusRecheck;
    public final int stuckPodRecheckSeconds;
    public final long initialShortDelay;
    public final long eventualLongDelay;
    public final int weblogicCredentialsSecretRereadIntervalSeconds;

    /**
     * create main tuning.
     * @param initializationRetryDelaySeconds initialization retry delay
     * @param domainPresenceFailureRetrySeconds domain presence failure retry
     * @param domainPresenceFailureRetryMaxCount domain presence failure retry max count
     * @param domainPresenceRecheckIntervalSeconds domain presence recheck interval
     * @param domainNamespaceRecheckIntervalSeconds domain namespace recheck interval
     * @param statusUpdateTimeoutSeconds status update timeout
     * @param unchangedCountToDelayStatusRecheck unchanged count to delay status recheck
     * @param stuckPodRecheckSeconds time between checks for stuck pods
     * @param initialShortDelay initial short delay
     * @param eventualLongDelay eventual long delay
     * @param weblogicCredentialsSecretRereadIntervalSeconds credentials secret reread interval
     */
    public MainTuning(
        int initializationRetryDelaySeconds,
        int domainPresenceFailureRetrySeconds,
        int domainPresenceFailureRetryMaxCount,
        int domainPresenceRecheckIntervalSeconds,
        int domainNamespaceRecheckIntervalSeconds,
        int statusUpdateTimeoutSeconds,
        int unchangedCountToDelayStatusRecheck,
        int stuckPodRecheckSeconds,
        long initialShortDelay,
        long eventualLongDelay,
        int weblogicCredentialsSecretRereadIntervalSeconds) {
      this.initializationRetryDelaySeconds = initializationRetryDelaySeconds;
      this.domainPresenceFailureRetrySeconds = domainPresenceFailureRetrySeconds;
      this.domainPresenceFailureRetryMaxCount = domainPresenceFailureRetryMaxCount;
      this.domainPresenceRecheckIntervalSeconds = domainPresenceRecheckIntervalSeconds;
      this.domainNamespaceRecheckIntervalSeconds = domainNamespaceRecheckIntervalSeconds;
      this.statusUpdateTimeoutSeconds = statusUpdateTimeoutSeconds;
      this.unchangedCountToDelayStatusRecheck = unchangedCountToDelayStatusRecheck;
      this.stuckPodRecheckSeconds = stuckPodRecheckSeconds;
      this.initialShortDelay = initialShortDelay;
      this.eventualLongDelay = eventualLongDelay;
      this.weblogicCredentialsSecretRereadIntervalSeconds = weblogicCredentialsSecretRereadIntervalSeconds;
    }

    @Override
    public String toString() {
      return new ToStringBuilder(this)
          .append("domainPresenceFailureRetrySeconds", domainPresenceFailureRetrySeconds)
          .append("domainPresenceFailureRetryMaxCount", domainPresenceFailureRetryMaxCount)
          .append("domainPresenceRecheckIntervalSeconds", domainPresenceRecheckIntervalSeconds)
          .append("domainNamespaceRecheckIntervalSeconds", domainNamespaceRecheckIntervalSeconds)
          .append("statusUpdateTimeoutSeconds", statusUpdateTimeoutSeconds)
          .append("unchangedCountToDelayStatusRecheck", unchangedCountToDelayStatusRecheck)
          .append("initialShortDelay", initialShortDelay)
          .append("eventualLongDelay", eventualLongDelay)
          .append("weblogicCredentialsSecretRereadIntervalSeconds", weblogicCredentialsSecretRereadIntervalSeconds)
          .toString();
    }

    @Override
    public int hashCode() {
      return new HashCodeBuilder()
          .append(domainPresenceFailureRetrySeconds)
          .append(domainPresenceFailureRetryMaxCount)
          .append(domainPresenceRecheckIntervalSeconds)
          .append(domainNamespaceRecheckIntervalSeconds)
          .append(statusUpdateTimeoutSeconds)
          .append(unchangedCountToDelayStatusRecheck)
          .append(initialShortDelay)
          .append(eventualLongDelay)
          .append(weblogicCredentialsSecretRereadIntervalSeconds)
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
          .append(domainNamespaceRecheckIntervalSeconds, mt.domainNamespaceRecheckIntervalSeconds)
          .append(statusUpdateTimeoutSeconds, mt.statusUpdateTimeoutSeconds)
          .append(unchangedCountToDelayStatusRecheck, mt.unchangedCountToDelayStatusRecheck)
          .append(initialShortDelay, mt.initialShortDelay)
          .append(eventualLongDelay, mt.eventualLongDelay)
          .append(weblogicCredentialsSecretRereadIntervalSeconds, mt.weblogicCredentialsSecretRereadIntervalSeconds)
          .isEquals();
    }
  }

  class CallBuilderTuning {
    public final int callRequestLimit;
    public final int callMaxRetryCount;
    public final int callTimeoutSeconds;

    /**
     * Create call builder tuning.
     * @param callRequestLimit call request limit
     * @param callMaxRetryCount call max retry count
     * @param callTimeoutSeconds call timeout
     */
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

  class WatchTuning {
    public final int watchLifetime;
    public final int watchMinimumDelay;
    public final int watchBackstopRecheckDelay;
    public final int watchBackstopRecheckCount;

    /**
     * Create watch tuning.
     * @param watchLifetime Watch lifetime
     * @param watchMinimumDelay Minimum delay before accepting new events to prevent hot loops
     * @param watchBackstopRecheckDelay Recheck delay for get while waiting for a status to backstop missed watch events
     */
    public WatchTuning(int watchLifetime, int watchMinimumDelay, int watchBackstopRecheckDelay,
                       int watchBackstopRecheckCount) {
      this.watchLifetime = watchLifetime;
      this.watchMinimumDelay = watchMinimumDelay;
      this.watchBackstopRecheckDelay = watchBackstopRecheckDelay;
      this.watchBackstopRecheckCount = watchBackstopRecheckCount;
    }

    @Override
    public String toString() {
      return new ToStringBuilder(this)
          .append("watchLifetime", watchLifetime)
          .append("watchMinimumDelay", watchMinimumDelay)
          .append("watchBackstopRecheckDelay", watchBackstopRecheckDelay)
          .toString();
    }

    @Override
    public int hashCode() {
      return new HashCodeBuilder()
              .append(watchLifetime).append(watchMinimumDelay).append(watchBackstopRecheckDelay).toHashCode();
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
          .append(watchBackstopRecheckDelay, wt.watchBackstopRecheckDelay)
          .isEquals();
    }
  }

  class PodTuning {
    public final int readinessProbeInitialDelaySeconds;
    public final int readinessProbeTimeoutSeconds;
    public final int readinessProbePeriodSeconds;
    public final int readinessProbeSuccessThreshold;
    public final int readinessProbeFailureThreshold;
    public final int livenessProbeInitialDelaySeconds;
    public final int livenessProbeTimeoutSeconds;
    public final int livenessProbePeriodSeconds;
    public final int livenessProbeSuccessThreshold;
    public final int livenessProbeFailureThreshold;
    public final long introspectorJobActiveDeadlineSeconds;
    public final long maxReadyWaitTimeSeconds;

    /**
     * create pod tuning.
     * @param readinessProbeInitialDelaySeconds readiness probe initial delay
     * @param readinessProbeTimeoutSeconds readiness probe timeout
     * @param readinessProbePeriodSeconds rediness probe period
     * @param readinessProbeSuccessThreshold readiness probe success threshold
     * @param readinessProbeFailureThreshold readiness probe failure threshold
     * @param livenessProbeInitialDelaySeconds liveness probe initial delay
     * @param livenessProbeTimeoutSeconds liveness probe timeout
     * @param livenessProbePeriodSeconds liveness probe period
     * @param livenessProbeSuccessThreshold liveness probe success threshold
     * @param livenessProbeFailureThreshold liveness probe failure threshold
     * @param introspectorJobActiveDeadlineSeconds introspector job active deadline
     * @param maxReadyWaitTimeSeconds maximum wait time for server pod to reach ready state
     */
    public PodTuning(
        int readinessProbeInitialDelaySeconds,
        int readinessProbeTimeoutSeconds,
        int readinessProbePeriodSeconds,
        int readinessProbeSuccessThreshold,
        int readinessProbeFailureThreshold,
        int livenessProbeInitialDelaySeconds,
        int livenessProbeTimeoutSeconds,
        int livenessProbePeriodSeconds,
        int livenessProbeSuccessThreshold,
        int livenessProbeFailureThreshold,
        long introspectorJobActiveDeadlineSeconds,
        long maxReadyWaitTimeSeconds) {
      this.readinessProbeInitialDelaySeconds = readinessProbeInitialDelaySeconds;
      this.readinessProbeTimeoutSeconds = readinessProbeTimeoutSeconds;
      this.readinessProbePeriodSeconds = readinessProbePeriodSeconds;
      this.readinessProbeSuccessThreshold = readinessProbeSuccessThreshold;
      this.readinessProbeFailureThreshold = readinessProbeFailureThreshold;
      this.livenessProbeInitialDelaySeconds = livenessProbeInitialDelaySeconds;
      this.livenessProbeTimeoutSeconds = livenessProbeTimeoutSeconds;
      this.livenessProbePeriodSeconds = livenessProbePeriodSeconds;
      this.livenessProbeSuccessThreshold = livenessProbeSuccessThreshold;
      this.livenessProbeFailureThreshold = livenessProbeFailureThreshold;
      this.introspectorJobActiveDeadlineSeconds = introspectorJobActiveDeadlineSeconds;
      this.maxReadyWaitTimeSeconds = maxReadyWaitTimeSeconds;
    }

    @Override
    public String toString() {
      return new ToStringBuilder(this)
          .append("readinessProbeInitialDelaySeconds", readinessProbeInitialDelaySeconds)
          .append("readinessProbeTimeoutSeconds", readinessProbeTimeoutSeconds)
          .append("readinessProbePeriodSeconds", readinessProbePeriodSeconds)
          .append("readinessProbeSuccessThreshold", readinessProbeSuccessThreshold)
          .append("readinessProbeFailureThreshold", readinessProbeFailureThreshold)
          .append("livenessProbeInitialDelaySeconds", livenessProbeInitialDelaySeconds)
          .append("livenessProbeTimeoutSeconds", livenessProbeTimeoutSeconds)
          .append("livenessProbePeriodSeconds", livenessProbePeriodSeconds)
          .append("livenessProbeSuccessThreshold", livenessProbeSuccessThreshold)
          .append("livenessProbeFailureThreshold", livenessProbeFailureThreshold)
          .append("maxReadyWaitTimeSeconds", maxReadyWaitTimeSeconds)
          .toString();
    }

    @Override
    public int hashCode() {
      return new HashCodeBuilder()
          .append(readinessProbeInitialDelaySeconds)
          .append(readinessProbeTimeoutSeconds)
          .append(readinessProbePeriodSeconds)
          .append(readinessProbeSuccessThreshold)
          .append(readinessProbeFailureThreshold)
          .append(livenessProbeInitialDelaySeconds)
          .append(livenessProbeTimeoutSeconds)
          .append(livenessProbePeriodSeconds)
          .append(livenessProbeSuccessThreshold)
          .append(livenessProbeFailureThreshold)
          .append(maxReadyWaitTimeSeconds)
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
          .append(readinessProbeSuccessThreshold, pt.readinessProbeSuccessThreshold)
          .append(readinessProbeFailureThreshold, pt.readinessProbeFailureThreshold)
          .append(livenessProbeInitialDelaySeconds, pt.livenessProbeInitialDelaySeconds)
          .append(livenessProbeTimeoutSeconds, pt.livenessProbeTimeoutSeconds)
          .append(livenessProbePeriodSeconds, pt.livenessProbePeriodSeconds)
          .append(livenessProbeSuccessThreshold, pt.livenessProbeSuccessThreshold)
          .append(livenessProbeFailureThreshold, pt.livenessProbeFailureThreshold)
          .append(maxReadyWaitTimeSeconds, pt.maxReadyWaitTimeSeconds)
          .isEquals();
    }
  }

  class FeatureGates {
    public final Collection<String> enabledFeatures;

    public FeatureGates(Collection<String> enabledFeatures) {
      this.enabledFeatures = Collections.unmodifiableCollection(enabledFeatures);
    }

    public Collection<String> getEnabledFeatures() {
      return enabledFeatures;
    }

    public boolean isFeatureEnabled(String featureName) {
      return enabledFeatures.contains(featureName);
    }

    @Override
    public String toString() {
      return new ToStringBuilder(this)
          .append("enabledFeatures", enabledFeatures)
          .toString();
    }

    @Override
    public int hashCode() {
      return new HashCodeBuilder()
          .append(enabledFeatures)
          .toHashCode();
    }

    @Override
    public boolean equals(Object o) {
      if (o == null) {
        return false;
      }
      if (!(o instanceof FeatureGates)) {
        return false;
      }
      FeatureGates fg = (FeatureGates) o;
      return new EqualsBuilder()
          .append(enabledFeatures, fg.enabledFeatures)
          .isEquals();
    }
  }
}

// Copyright 2017, 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ThreadFactory;

public interface TuningParameters extends Map<String, String> {
  
  static TuningParameters initializeInstance(
      ThreadFactory factory, String mountPoint) throws IOException {
    return TuningParametersImpl.initializeInstance(factory, mountPoint);
  }

  public static TuningParameters getInstance() {
    return TuningParametersImpl.getInstance();
  }
  
  public static class MainTuning {
    public final int domainPresenceFailureRetrySeconds;
    public final int domainPresenceRecheckIntervalSeconds;
    public final int statusUpdateTimeoutSeconds;
    public final int unchangedCountToDelayStatusRecheck; 
    public final long initialShortDelay; 
    public final long eventualLongDelay;
    
    public MainTuning(int domainPresenceFailureRetrySeconds, int domainPresenceRecheckIntervalSeconds,
        int statusUpdateTimeoutSeconds, int unchangedCountToDelayStatusRecheck, 
        long initialShortDelay, long eventualLongDelay) {
      this.domainPresenceFailureRetrySeconds = domainPresenceFailureRetrySeconds;
      this.domainPresenceRecheckIntervalSeconds = domainPresenceRecheckIntervalSeconds;
      this.statusUpdateTimeoutSeconds = statusUpdateTimeoutSeconds;
      this.unchangedCountToDelayStatusRecheck = unchangedCountToDelayStatusRecheck;
      this.initialShortDelay = initialShortDelay;
      this.eventualLongDelay = eventualLongDelay;
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
  }
  
  public static class WatchTuning {
    public final int watchLifetime;
    
    public WatchTuning(int watchLifetime) {
      this.watchLifetime = watchLifetime;
    }
  }
  
  public static class PodTuning {
    public final int readinessProbeInitialDelaySeconds;
    public final int readinessProbeTimeoutSeconds;
    public final int readinessProbePeriodSeconds;
    public final int livenessProbeInitialDelaySeconds;
    public final int livenessProbeTimeoutSeconds;
    public final int livenessProbePeriodSeconds;

    public PodTuning(int readinessProbeInitialDelaySeconds, int readinessProbeTimeoutSeconds, 
        int readinessProbePeriodSeconds, int livenessProbeInitialDelaySeconds, 
        int livenessProbeTimeoutSeconds, int livenessProbePeriodSeconds) {
      this.readinessProbeInitialDelaySeconds = readinessProbeInitialDelaySeconds;
      this.readinessProbeTimeoutSeconds = readinessProbeTimeoutSeconds;
      this.readinessProbePeriodSeconds = readinessProbePeriodSeconds;
      this.livenessProbeInitialDelaySeconds = livenessProbeInitialDelaySeconds;
      this.livenessProbeTimeoutSeconds = livenessProbeTimeoutSeconds;
      this.livenessProbePeriodSeconds = livenessProbePeriodSeconds;
    }
  }

  public MainTuning getMainTuning();
  public CallBuilderTuning getCallBuilderTuning();
  public WatchTuning getWatchTuning();
  public PodTuning getPodTuning();
}

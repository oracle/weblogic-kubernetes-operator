// Copyright 2017, 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator;

import java.io.IOException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import oracle.kubernetes.operator.helpers.ConfigMapConsumer;
import oracle.kubernetes.operator.logging.LoggingFacade;
import oracle.kubernetes.operator.logging.LoggingFactory;
import oracle.kubernetes.operator.logging.MessageKeys;

public class TuningParameters extends ConfigMapConsumer {
  private static final LoggingFacade LOGGER = LoggingFactory.getLogger("Operator", "Operator");
  private static TuningParameters INSTANCE = null;
  
  private final ReadWriteLock lock = new ReentrantReadWriteLock();
  private MainTuning main = null;
  private CallBuilderTuning callBuilder = null;
  private WatchTuning watch = null;
  private PodTuning pod = null;
  
  public static class MainTuning {
    public final int statusUpdateTimeoutSeconds;
    public final int unchangedCountToDelayStatusRecheck; 
    public final long initialShortDelay; 
    public final long eventualLongDelay;
    
    public MainTuning(int statusUpdateTimeoutSeconds, int unchangedCountToDelayStatusRecheck, 
        long initialShortDelay, long eventualLongDelay) {
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

  public synchronized static TuningParameters initializeInstance(
      ThreadFactory factory, String mountPoint) throws IOException {
    if (INSTANCE == null) {
      INSTANCE = new TuningParameters(factory, mountPoint);
      return INSTANCE;
    }
    throw new IllegalStateException();
  }
  
  private TuningParameters(ThreadFactory factory, String mountPoint) throws IOException {
    super(factory, mountPoint, () -> {
      updateTuningParameters();
    });
    update();
  }

  public static void updateTuningParameters() {
    INSTANCE.update();
  }
  
  private void update() {
    LOGGER.info(MessageKeys.TUNING_PARAMETERS);
    
    MainTuning main = new MainTuning(
        (int) readTuningParameter("statusUpdateTimeoutSeconds", 10),
        (int) readTuningParameter("statueUpdateUnchangedCountToDelayStatusRecheck", 10),
        readTuningParameter("statusUpdateInitialShortDelay", 3),
        readTuningParameter("statusUpdateEventualLongDelay", 30));

    CallBuilderTuning callBuilder = new CallBuilderTuning(
        (int) readTuningParameter("callRequestLimit", 500),
        (int) readTuningParameter("callMaxRetryCount", 5),
        (int) readTuningParameter("callTimeoutSeconds", 10));
    
    WatchTuning watch = new WatchTuning(
        (int) readTuningParameter("watchLifetime", 45));
    
    PodTuning pod = new PodTuning(
        (int) readTuningParameter("readinessProbeInitialDelaySeconds", 2),
        (int) readTuningParameter("readinessProbeTimeoutSeconds", 5),
        (int) readTuningParameter("readinessProbeTimeoutSeconds", 5),
        (int) readTuningParameter("livenessProbeInitialDelaySeconds", 10),
        (int) readTuningParameter("livenessProbeTimeoutSeconds", 5),
        (int) readTuningParameter("livenessProbePeriodSeconds", 10));

    lock.writeLock().lock();
    try {
      this.main = main;
      this.callBuilder = callBuilder;
      this.watch = watch;
      this.pod = pod;
    } finally {
      lock.writeLock().unlock();
    }
  }
  
  public MainTuning getMainTuning() {
    lock.readLock().lock();
    try {
      return main;
    } finally {
      lock.readLock().unlock();
    }
  }
  
  public CallBuilderTuning getCallBuilderTuning() {
    lock.readLock().lock();
    try {
      return callBuilder;
    } finally {
      lock.readLock().unlock();
    }
  }
  
  public WatchTuning getWatchTuning() {
    lock.readLock().lock();
    try {
      return watch;
    } finally {
      lock.readLock().unlock();
    }
  }
  
  public PodTuning getPodTuning() {
    lock.readLock().lock();
    try {
      return pod;
    } finally {
      lock.readLock().unlock();
    }
  }
}

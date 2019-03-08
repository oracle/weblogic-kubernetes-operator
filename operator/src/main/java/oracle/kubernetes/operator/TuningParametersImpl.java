// Copyright 2017, 2019, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator;

import java.io.IOException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import oracle.kubernetes.operator.helpers.ConfigMapConsumer;
import oracle.kubernetes.operator.logging.LoggingFacade;
import oracle.kubernetes.operator.logging.LoggingFactory;
import oracle.kubernetes.operator.logging.MessageKeys;

public class TuningParametersImpl extends ConfigMapConsumer implements TuningParameters {
  private static final LoggingFacade LOGGER = LoggingFactory.getLogger("Operator", "Operator");
  private static TuningParameters INSTANCE = null;

  private final ReadWriteLock lock = new ReentrantReadWriteLock();
  private MainTuning main = null;
  private CallBuilderTuning callBuilder = null;
  private WatchTuning watch = null;
  private PodTuning pod = null;

  static synchronized TuningParameters initializeInstance(
      ScheduledExecutorService executorService, String mountPoint) throws IOException {
    if (INSTANCE == null) {
      INSTANCE = new TuningParametersImpl(executorService, mountPoint);
      return INSTANCE;
    }
    throw new IllegalStateException();
  }

  public static synchronized TuningParameters getInstance() {
    return INSTANCE;
  }

  private TuningParametersImpl(ScheduledExecutorService executorService, String mountPoint)
      throws IOException {
    super(executorService, mountPoint, TuningParametersImpl::updateTuningParameters);
    update();
  }

  private static void updateTuningParameters() {
    ((TuningParametersImpl) INSTANCE).update();
  }

  private void update() {
    MainTuning main =
        new MainTuning(
            (int) readTuningParameter("domainPresenceFailureRetrySeconds", 10),
            (int) readTuningParameter("domainPresenceFailureRetryMaxCount", 5),
            (int) readTuningParameter("domainPresenceRecheckIntervalSeconds", 120),
            (int) readTuningParameter("targetNamespaceRecheckIntervalSeconds", 3),
            (int) readTuningParameter("statusUpdateTimeoutSeconds", 10),
            (int) readTuningParameter("statusUpdateUnchangedCountToDelayStatusRecheck", 10),
            readTuningParameter("statusUpdateInitialShortDelay", 3),
            readTuningParameter("statusUpdateEventualLongDelay", 30));

    CallBuilderTuning callBuilder =
        new CallBuilderTuning(
            (int) readTuningParameter("callRequestLimit", 500),
            (int) readTuningParameter("callMaxRetryCount", 5),
            (int) readTuningParameter("callTimeoutSeconds", 10));

    WatchTuning watch =
        new WatchTuning(
            (int) readTuningParameter("watchLifetime", 300),
            (int) readTuningParameter("watchMinimumDelay", 5));

    PodTuning pod =
        new PodTuning(
            (int) readTuningParameter("readinessProbeInitialDelaySeconds", 30),
            (int) readTuningParameter("readinessProbeTimeoutSeconds", 5),
            (int) readTuningParameter("readinessProbePeriodSeconds", 5),
            (int) readTuningParameter("livenessProbeInitialDelaySeconds", 30),
            (int) readTuningParameter("livenessProbeTimeoutSeconds", 5),
            (int) readTuningParameter("livenessProbePeriodSeconds", 45));

    lock.writeLock().lock();
    try {
      if (!main.equals(this.main)
          || !callBuilder.equals(this.callBuilder)
          || !watch.equals(this.watch)
          || !pod.equals(this.pod)) {
        LOGGER.info(MessageKeys.TUNING_PARAMETERS);
      }
      this.main = main;
      this.callBuilder = callBuilder;
      this.watch = watch;
      this.pod = pod;
    } finally {
      lock.writeLock().unlock();
    }
  }

  @Override
  public MainTuning getMainTuning() {
    lock.readLock().lock();
    try {
      return main;
    } finally {
      lock.readLock().unlock();
    }
  }

  @Override
  public CallBuilderTuning getCallBuilderTuning() {
    lock.readLock().lock();
    try {
      return callBuilder;
    } finally {
      lock.readLock().unlock();
    }
  }

  @Override
  public WatchTuning getWatchTuning() {
    lock.readLock().lock();
    try {
      return watch;
    } finally {
      lock.readLock().unlock();
    }
  }

  @Override
  public PodTuning getPodTuning() {
    lock.readLock().lock();
    try {
      return pod;
    } finally {
      lock.readLock().unlock();
    }
  }
}

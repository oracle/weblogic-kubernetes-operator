// Copyright (c) 2017, 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;

import oracle.kubernetes.common.logging.MessageKeys;
import oracle.kubernetes.operator.helpers.ConfigMapConsumer;
import oracle.kubernetes.operator.logging.LoggingFacade;
import oracle.kubernetes.operator.logging.LoggingFactory;

import static oracle.kubernetes.operator.helpers.BasePodStepContext.KUBERNETES_PLATFORM_HELM_VARIABLE;

public class TuningParametersImpl extends ConfigMapConsumer implements TuningParameters {
  public static final int DEFAULT_CALL_LIMIT = 50;

  private static final LoggingFacade LOGGER = LoggingFactory.getLogger("Operator", "Operator");

  private static TuningParameters instance = null;

  private final ReadWriteLock lock = new ReentrantReadWriteLock();
  private MainTuning main = null;
  private CallBuilderTuning callBuilder = null;
  private WatchTuning watch = null;
  private PodTuning pod = null;
  private FeatureGates featureGates = null;
  private String kubernetesPlatform = null;

  private TuningParametersImpl(ScheduledExecutorService executorService) {
    super(executorService);
  }

  static synchronized TuningParameters initializeInstance(ScheduledExecutorService executorService, File mountPoint) {
    if (instance == null) {
      final TuningParametersImpl impl = new TuningParametersImpl(executorService);
      instance = impl;
      impl.scheduleUpdates(mountPoint, TuningParametersImpl::updateTuningParameters);
    }
    return instance;
  }

  public static synchronized TuningParameters getInstance() {
    return instance;
  }

  private static void updateTuningParameters() {
    ((TuningParametersImpl) instance).update();
  }

  private void update() {
    MainTuning main =
        new MainTuning(
            (int) readTuningParameter("initializationRetryDelaySeconds", 5),
            (int) readTuningParameter("domainPresenceFailureRetrySeconds", 10),
            (int) readTuningParameter("domainPresenceFailureRetryMaxCount", 5),
            (int) readTuningParameter("domainPresenceRecheckIntervalSeconds", 120),
            (int) readTuningParameter("domainNamespaceRecheckIntervalSeconds", 3),
            (int) readTuningParameter("statusUpdateTimeoutSeconds", 10),
            (int) readTuningParameter("statusUpdateUnchangedCountToDelayStatusRecheck", 10),
            (int) readTuningParameter("stuckPodRecheckSeconds", 30),
            readTuningParameter("statusUpdateInitialShortDelay", 5),
            readTuningParameter("statusUpdateEventualLongDelay", 30),
            (int) readTuningParameter("weblogicCredentialsSecretRereadIntervalSeconds", 120));

    CallBuilderTuning callBuilder =
        new CallBuilderTuning(
            (int) readTuningParameter("callRequestLimit", DEFAULT_CALL_LIMIT),
            (int) readTuningParameter("callMaxRetryCount", 5),
            (int) readTuningParameter("callTimeoutSeconds", 10));

    WatchTuning watch =
        new WatchTuning(
            (int) readTuningParameter("watchLifetime", 300),
            (int) readTuningParameter("watchMinimumDelay", 5),
            (int) readTuningParameter("watchBackstopRecheckDelaySeconds", 5),
            (int) readTuningParameter("watchBackstopRecheckCount", 60));

    PodTuning pod =
        new PodTuning(
            (int) readTuningParameter("readinessProbeInitialDelaySeconds", 30),
            (int) readTuningParameter("readinessProbeTimeoutSeconds", 5),
            (int) readTuningParameter("readinessProbePeriodSeconds", 5),
            (int) readTuningParameter("readinessProbeSuccessThreshold", 1),
            (int) readTuningParameter("readinessProbeFailureThreshold", 1),
            (int) readTuningParameter("livenessProbeInitialDelaySeconds", 30),
            (int) readTuningParameter("livenessProbeTimeoutSeconds", 5),
            (int) readTuningParameter("livenessProbePeriodSeconds", 45),
            (int) readTuningParameter("livenessProbeSuccessThreshold", 1),
            (int) readTuningParameter("livenessProbeFailureThreshold", 1),
            readTuningParameter("introspectorJobActiveDeadlineSeconds", 120),
            readTuningParameter("maxReadyWaitTimeSeconds", 1800),
            readBooleanTuningParameter("restartEvictedPods", true)
            );

    FeatureGates featureGates =
        new FeatureGates(generateFeatureGates(get("featureGates")));

    String kubernetesPlatform = get(KUBERNETES_PLATFORM_HELM_VARIABLE);

    lock.writeLock().lock();
    try {
      if (!main.equals(this.main)
          || !callBuilder.equals(this.callBuilder)
          || !watch.equals(this.watch)
          || !pod.equals(this.pod)
          || !featureGates.equals(this.featureGates)) {
        LOGGER.config(MessageKeys.TUNING_PARAMETERS);
      }
      this.main = main;
      this.callBuilder = callBuilder;
      this.watch = watch;
      this.pod = pod;
      this.featureGates = featureGates;
      this.kubernetesPlatform = kubernetesPlatform;
    } finally {
      lock.writeLock().unlock();
    }
  }

  private Collection<String> generateFeatureGates(String featureGatesProperty) {
    Collection<String> enabledGates = new ArrayList<>();
    if (featureGatesProperty != null) {
      Arrays.stream(
          featureGatesProperty.split(","))
          .filter(s -> s.endsWith("=true"))
          .map(s -> s.substring(0, s.indexOf('=')))
          .collect(Collectors.toCollection(() -> enabledGates));
    }
    return enabledGates;
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

  @Override
  public FeatureGates getFeatureGates() {
    lock.readLock().lock();
    try {
      return featureGates;
    } finally {
      lock.readLock().unlock();
    }
  }

  @Override
  public String getKubernetesPlatform() {
    lock.readLock().lock();
    try {
      return kubernetesPlatform;
    } finally {
      lock.readLock().unlock();
    }
  }
}

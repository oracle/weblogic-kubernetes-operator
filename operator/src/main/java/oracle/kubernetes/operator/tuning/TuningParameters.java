// Copyright (c) 2022, 2023, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.tuning;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import oracle.kubernetes.operator.ProcessingConstants;
import oracle.kubernetes.operator.WatchTuning;
import oracle.kubernetes.operator.WebLogicConstants;
import oracle.kubernetes.operator.logging.LoggingFacade;
import oracle.kubernetes.operator.logging.LoggingFactory;

/**
 * A class which provides access to the Helm tuning parameters, defined in a config map that maps to a directory.
 */
public class TuningParameters {
  public static final int DEFAULT_CALL_LIMIT = 50;

  //----------- supported tuning parameters. ------------

  public static final String WATCH_LIFETIME = "watchLifetime";
  public static final String WATCH_MINIMUM_DELAY = "watchMinimumDelay";
  public static final String WATCH_BACKSTOP_RECHECK_COUNT = "watchBackstopRecheckCount";
  public static final String WATCH_BACKSTOP_RECHECK_DELAY_SECONDS = "watchBackstopRecheckDelaySeconds";

  public static final String CALL_REQUEST_LIMIT = "callRequestLimit";
  public static final String CALL_MAX_RETRY_COUNT = "callMaxRetryCount";
  public static final String CALL_TIMEOUT_SECONDS = "callTimeoutSeconds";

  public static final String READINESS_INITIAL_DELAY_SECONDS = "readinessProbeInitialDelaySeconds";
  public static final String READINESS_TIMEOUT_SECONDS = "readinessProbeTimeoutSeconds";
  public static final String READINESS_PERIOD_SECONDS = "readinessProbePeriodSeconds";
  public static final String READINESS_SUCCESS_COUNT_THRESHOLD = "readinessProbeSuccessThreshold";
  public static final String READINESS_FAILURE_COUNT_THRESHOLD = "readinessProbeFailureThreshold";
  public static final String LIVENESS_INITIAL_DELAY_SECONDS = "livenessProbeInitialDelaySeconds";
  public static final String LIVENESS_TIMEOUT_SECONDS = "livenessProbeTimeoutSeconds";
  public static final String LIVENESS_PERIOD_SECONDS = "livenessProbePeriodSeconds";
  public static final String LIVENESS_SUCCESS_COUNT_THRESHOLD = "livenessProbeSuccessThreshold";
  public static final String LIVENESS_FAILURE_COUNT_THRESHOLD = "livenessProbeFailureThreshold";

  public static final String INITIALIZATION_RETRY_DELAY_SECONDS = "initializationRetryDelaySeconds";
  public static final String UNCHANGED_COUNT_TO_DELAY_STATUS_RECHECK = "statusUpdateUnchangedCountToDelayStatusRecheck";
  public static final String DOMAIN_PRESENCE_RECHECK_INTERVAL_SECONDS = "domainPresenceRecheckIntervalSeconds";
  public static final String DOMAIN_NAMESPACE_RECHECK_INTERVAL_SECONDS = "domainNamespaceRecheckIntervalSeconds";
  public static final String STUCK_POD_RECHECK_SECONDS = "stuckPodRecheckSeconds";
  public static final String STATUS_UPDATE_TIMEOUT_SECONDS = "statusUpdateTimeoutSeconds";
  public static final String STATUS_UPDATE_INITIAL_SHORT_DELAY = "statusUpdateInitialShortDelay";
  public static final String STATUS_UPDATE_EVENTUAL_LONG_DELAY = "statusUpdateEventualLongDelay";
  public static final String SECRET_REREAD_INTERVAL_SECONDS = "weblogicCredentialsSecretRereadIntervalSeconds";
  public static final String MAX_READY_WAIT_TIME_SECONDS = "maxReadyWaitTimeSeconds";
  public static final String MAX_PENDING_WAIT_TIME_SECONDS = "maxPendingWaitTimeSeconds";
  public static final String RESTART_EVICTED_PODS = "restartEvictedPods";
  public static final String INTROSPECTOR_JOB_ACTIVE_DEADLINE_SECONDS = "introspectorJobActiveDeadlineSeconds";
  public static final String INTROSPECTOR_JOB_DEADLINE_INCREMENT_SECONDS = "introspectorJobDeadlineIncrementSeconds";
  public static final String INTROSPECTOR_JOB_MAX_NUM_INCREMENTS = "introspectorJobMaxNumIncrements";
  public static final String KUBERNETES_PLATFORM_NAME = "kubernetesPlatform";
  public static final String FEATURE_GATES = "featureGates";
  public static final String SERVICE_ACCOUNT_NAME = "serviceaccount";
  public static final String CRD_PRESENCE_FAILURE_RETRY_MAX_COUNT = "crdPresenceFailureRetryMaxCount";
  public static final String HTTP_REQUEST_FAILURE_COUNT_THRESHOLD = "httpRequestFailureCountThreshold";
  public static final String SHUTDOWN_WITH_HTTP_POLLING_INTERVAL = "shutdownWithHttpPollingInterval";
  public static final int DEFAULT_HTTP_REQUEST_FAILURE_COUNT_THRESHOLD = 10;
  public static final int DEFAULT_SHUTDOWN_WITH_HTTP_POLLING_INTERVAL = 3;

  public static final long DEFAULT_ACTIVE_DEADLINE_INCREMENT_SECONDS = 60L;

  private static final LoggingFacade LOGGER = LoggingFactory.getLogger("Operator", "Operator");

  static final int DEFAULT_NAMESPACE_RECHECK_SECONDS = 3;

  @SuppressWarnings("FieldMayBeFinal") // allow unit tests to set this
  private static Function<String, Path> getPath = Paths::get;
  private static TuningParameters instance;
  private final Map<String, String> configuredValues;
  private final ReadWriteLock lock = new ReentrantReadWriteLock();

  private final WatchTuning watchTuning = new WatchTuningImpl();
  private final CallBuilderTuning callBuilderTuning = new CallBuilderTuningImpl();
  private final PodTuning podTuning = new PodTuningImpl();

  private String mountPointDir;

  /**
   * Initializes the tuning parameter instance and begins a schedule of updates to load values from
   * the contents of the helm configuration map.
   * @param executor a service which can schedule activities
   * @param mountPointDir the root of a directory containing the parameter values. Each named file defines
   *                      a single parameter value as a string
   */
  public static void initializeInstance(ScheduledExecutorService executor, File mountPointDir) {
    if (instance == null) {
      instance = new TuningParameters(new HashMap<>());
      instance.scheduleUpdates(executor, mountPointDir.getPath());
    }
  }

  public static TuningParameters getInstance() {
    return TuningParameters.instance;
  }

  /**
   * Returns a set of tuning parameters used as a group by Watchers.
   */
  public WatchTuning getWatchTuning() {
    return watchTuning;
  }

  /**
   * Returns a set of tuning parameters used as a group by CallBuilder.
   */
  public CallBuilderTuning getCallBuilderTuning() {
    return callBuilderTuning;
  }

  /**
   * Returns a set of tuning parameters used as a group for creating pods.
   */
  public PodTuning getPodTuning() {
    return podTuning;
  }

  public FeatureGates getFeatureGates() {
    return new FeatureGatesImpl();
  }

  //--------- individual tuning parameters

  /**
   * Returns the specified tuning parameter as a string. Will return null if it is not defined.
   * @param parameterName the name of the parameter
   */
  public String get(String parameterName) {
    return getParameter(parameterName, null);
  }

  /**
   * Returns the name of the service account in the operator's namespace that the operator will use to make requests
   * to the Kubernetes server.
   */
  public String getServiceAccountName() {
    return getParameter(SERVICE_ACCOUNT_NAME, "default");
  }

  public int getInitializationRetryDelaySeconds() {
    return getParameter(INITIALIZATION_RETRY_DELAY_SECONDS, 5);
  }


  public int getDomainPresenceRecheckIntervalSeconds() {
    return getParameter(DOMAIN_PRESENCE_RECHECK_INTERVAL_SECONDS, 120);
  }

  public int getDomainNamespaceRecheckIntervalSeconds() {
    return getParameter(DOMAIN_NAMESPACE_RECHECK_INTERVAL_SECONDS, 3);
  }

  public int getStuckPodRecheckSeconds() {
    return getParameter(STUCK_POD_RECHECK_SECONDS, 30);
  }

  public int getStatusUpdateTimeoutSeconds() {
    return getParameter(STATUS_UPDATE_TIMEOUT_SECONDS, 10);
  }

  public int getInitialShortDelay() {
    return getParameter(STATUS_UPDATE_INITIAL_SHORT_DELAY, 5);
  }

  public int getUnchangedCountToDelayStatusRecheck() {
    return getParameter(UNCHANGED_COUNT_TO_DELAY_STATUS_RECHECK, 10);
  }

  public int getEventualLongDelay() {
    return getParameter(STATUS_UPDATE_EVENTUAL_LONG_DELAY, 30);
  }
  
  public int getCredentialsSecretRereadIntervalSeconds() {
    return getParameter(SECRET_REREAD_INTERVAL_SECONDS, 120);
  }

  public long getMaxReadyWaitTimeSeconds() {
    return getParameter(MAX_READY_WAIT_TIME_SECONDS, 1800);
  }

  public long getMaxPendingWaitTimeSeconds() {
    return getParameter(MAX_PENDING_WAIT_TIME_SECONDS, 300);
  }

  public boolean isRestartEvictedPods() {
    return getParameter(RESTART_EVICTED_PODS, true);
  }

  /**
   * Returns the value of introspector job active deadline seconds with default value depending on the context.
   */
  public long getActiveJobInitialDeadlineSeconds(boolean isInitializeDomainOnPV, String type) {
    long defaultValue = 120L;
    if (isInitializeDomainOnPV && isWlsOrRestrictedJRFDomain(type)) {
      defaultValue = ProcessingConstants.DEFAULT_WLS_OR_RESTRICTED_JRF_INTROSPECTOR_JOB_ACTIVE_DEADLINE_SECONDS;
    } else if (isInitializeDomainOnPV) {
      defaultValue = ProcessingConstants.DEFAULT_JRF_INTROSPECTOR_JOB_ACTIVE_DEADLINE_SECONDS;
    }
    return getParameter(INTROSPECTOR_JOB_ACTIVE_DEADLINE_SECONDS, defaultValue);
  }

  private boolean isWlsOrRestrictedJRFDomain(String type) {
    return WebLogicConstants.WLS.equals(type) || WebLogicConstants.RESTRICTED_JRF.equals(type);
  }

  public long getActiveDeadlineIncrementSeconds() {
    return getParameter(INTROSPECTOR_JOB_DEADLINE_INCREMENT_SECONDS, DEFAULT_ACTIVE_DEADLINE_INCREMENT_SECONDS);
  }

  public long getActiveDeadlineMaxNumIncrements() {
    return getParameter(INTROSPECTOR_JOB_MAX_NUM_INCREMENTS, 5);
  }

  public int getCrdPresenceFailureRetryMaxCount() {
    return getParameter(CRD_PRESENCE_FAILURE_RETRY_MAX_COUNT, 3);
  }

  public int getHttpRequestFailureCountThreshold() {
    return getParameter(HTTP_REQUEST_FAILURE_COUNT_THRESHOLD, DEFAULT_HTTP_REQUEST_FAILURE_COUNT_THRESHOLD);
  }

  public int getShutdownWithHttpPollingInterval() {
    return getParameter(SHUTDOWN_WITH_HTTP_POLLING_INTERVAL, DEFAULT_SHUTDOWN_WITH_HTTP_POLLING_INTERVAL);
  }

  /**
   * Returns the name of the kubernetes platform on which the operator is running. May be null (the default).
   */
  @Nullable
  public String getKubernetesPlatform() {
    return getParameter(KUBERNETES_PLATFORM_NAME, null);
  }

  //---------------------------
  
  TuningParameters(Map<String, String> configuredValues) {
    this.configuredValues = configuredValues;
  }

  private void scheduleUpdates(ScheduledExecutorService executor, String mountPointDir) {
    if (Files.exists(getPath.apply(mountPointDir))) {
      this.mountPointDir = mountPointDir;
      readParameters();
      long delay = getParameter("configMapUpdateDelay", 10L);
      executor.scheduleWithFixedDelay(this::readParameters, delay, delay, TimeUnit.SECONDS);
    }
  }

  private void readParameters() {
    lock.writeLock().lock();
    try (Stream<Path> parameterFiles = Files.list(getPath.apply(mountPointDir))) {
      parameterFiles.forEach(this::readParameterFrom);
    } catch (IOException e) {
      LOGGER.fine("unable to read tuning parameters", e);
    } finally {
      lock.writeLock().unlock();
    }
  }

  private void readParameterFrom(Path parameterPath) {
    try {
      final String parameterName = parameterPath.getFileName().toString();
      final String stringValue = new String(Files.readAllBytes(parameterPath));
      setParameterValue(parameterName, stringValue);
    } catch (IOException ignored) {
      // ignore this
    }
  }

  private void setParameterValue(String parameterName, String stringValue) {
    configuredValues.put(parameterName, stringValue);
  }

  @SuppressWarnings("SameParameterValue")
  boolean getParameter(String name, boolean defaultValue) {
    return Optional.ofNullable(getConfiguredValue(name)).map(Boolean::valueOf).orElse(defaultValue);
  }

  int getParameter(String name, int defaultValue) {
    return Optional.ofNullable(getConfiguredValue(name)).map(Integer::valueOf).orElse(defaultValue);
  }

  long getParameter(String name, long defaultValue) {
    return Optional.ofNullable(getConfiguredValue(name)).map(Long::valueOf).orElse(defaultValue);
  }

  String getParameter(String name, String defaultValue) {
    return Optional.ofNullable(getConfiguredValue(name)).orElse(defaultValue);
  }

  private String getConfiguredValue(String name) {
    lock.readLock().lock();
    try {
      return configuredValues.get(name);
    } finally {
      lock.readLock().unlock();
    }
  }

  /**
   * Returns the interval at which the operator will check all supported namespaces, looking new or updated domains.
   * @return a value in seconds
   */
  int getNamespaceRecheckIntervalSeconds() {
    return getParameter(DOMAIN_NAMESPACE_RECHECK_INTERVAL_SECONDS, DEFAULT_NAMESPACE_RECHECK_SECONDS);
  }

  private class WatchTuningImpl implements WatchTuning {
    private static final int DEFAULT_WATCH_LIFETIME_SECONDS = 300;
    private static final int DEFAULT_MINIMUM_DELAY = 5;
    private static final int DEFAULT_RECHECK_SECONDS = 5;
    private static final int DEFAULT_RECHECK_COUNT = 60;

    @Override
    public int getWatchLifetime() {
      return getParameter(WATCH_LIFETIME, DEFAULT_WATCH_LIFETIME_SECONDS);
    }

    @Override
    public int getWatchMinimumDelay() {
      return getParameter(WATCH_MINIMUM_DELAY, DEFAULT_MINIMUM_DELAY);
    }

    @Override
    public int getWatchBackstopRecheckDelay() {
      return getParameter(WATCH_BACKSTOP_RECHECK_DELAY_SECONDS, DEFAULT_RECHECK_SECONDS);
    }

    @Override
    public int getWatchBackstopRecheckCount() {
      return getParameter(WATCH_BACKSTOP_RECHECK_COUNT, DEFAULT_RECHECK_COUNT);
    }
  }

  private class CallBuilderTuningImpl implements CallBuilderTuning {

    @Override
    public int getCallRequestLimit() {
      return getParameter(CALL_REQUEST_LIMIT, DEFAULT_CALL_LIMIT);
    }

    @Override
    public int getCallMaxRetryCount() {
      return getParameter(CALL_MAX_RETRY_COUNT, 5);
    }

    @Override
    public int getCallTimeoutSeconds() {
      return getParameter(CALL_TIMEOUT_SECONDS, 10);
    }
  }

  private class PodTuningImpl implements PodTuning {

    @Override
    public int getReadinessProbeInitialDelaySeconds() {
      return getParameter(READINESS_INITIAL_DELAY_SECONDS, 30);
    }

    @Override
    public int getReadinessProbeTimeoutSeconds() {
      return getParameter(READINESS_TIMEOUT_SECONDS, 5);
    }

    @Override
    public int getReadinessProbePeriodSeconds() {
      return getParameter(READINESS_PERIOD_SECONDS, 5);
    }

    @Override
    public int getReadinessProbeSuccessThreshold() {
      return getParameter(READINESS_SUCCESS_COUNT_THRESHOLD, 1);
    }

    @Override
    public int getReadinessProbeFailureThreshold() {
      return getParameter(READINESS_FAILURE_COUNT_THRESHOLD, 1);
    }

    @Override
    public int getLivenessProbeInitialDelaySeconds() {
      return getParameter(LIVENESS_INITIAL_DELAY_SECONDS, 30);
    }

    @Override
    public int getLivenessProbeTimeoutSeconds() {
      return getParameter(LIVENESS_TIMEOUT_SECONDS, 5);
    }

    @Override
    public int getLivenessProbePeriodSeconds() {
      return getParameter(LIVENESS_PERIOD_SECONDS, 45);
    }

    @Override
    public int getLivenessProbeSuccessThreshold() {
      return getParameter(LIVENESS_SUCCESS_COUNT_THRESHOLD, 1);
    }

    @Override
    public int getLivenessProbeFailureThreshold() {
      return getParameter(LIVENESS_FAILURE_COUNT_THRESHOLD, 1);
    }
  }

  private class FeatureGatesImpl implements FeatureGates {

    private final List<String> enabledFeatures;

    FeatureGatesImpl() {
      enabledFeatures = parseEnabledFeatures(getParameter(FEATURE_GATES, ""));
    }

    @Nonnull
    private List<String> parseEnabledFeatures(String list) {
      return Arrays.stream(list.split(","))
          .filter(this::isEnabledFeatureName)
          .map(this::getFeatureName)
          .collect(Collectors.toList());
    }

    private boolean isEnabledFeatureName(String v) {
      return v.endsWith("=true");
    }

    private String getFeatureName(String v) {
      return v.split("=")[0];
    }

    @Override
    public Collection<String> getEnabledFeatures() {
      return enabledFeatures;
    }

    @Override
    public boolean isFeatureEnabled(String featureName) {
      return enabledFeatures.contains(featureName);
    }
  }
}

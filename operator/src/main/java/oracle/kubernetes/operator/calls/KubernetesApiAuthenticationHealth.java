// Copyright (c) 2026, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.calls;

import java.time.Duration;
import java.time.OffsetDateTime;

import oracle.kubernetes.common.logging.MessageKeys;
import oracle.kubernetes.operator.logging.LoggingFacade;
import oracle.kubernetes.operator.logging.LoggingFactory;
import oracle.kubernetes.utils.SystemClock;

/**
 * Tracks continuous authentication failures made with the operator's Kubernetes API client.
 *
 * <p>A single successful request proves that the operator service-account client can authenticate
 * and clears the failure. Authorization failures (HTTP 403) and transport failures are deliberately
 * excluded from this signal.
 */
public class KubernetesApiAuthenticationHealth {
  static final int RESTART_AFTER_SECONDS = 15 * 60;
  static final int PROGRESS_INTERVAL_SECONDS = 2 * 60;
  public static final String DIAGNOSTIC_MARKER = "WKO-API-AUTH-HEALTH";
  private static final int MAX_ERROR_DETAILS_LENGTH = 2048;
  private static final String UNKNOWN_ERROR_DETAILS = "No Kubernetes API error details were provided";

  private static final LoggingFacade LOGGER = LoggingFactory.getLogger("Operator", "Operator");
  private static OffsetDateTime unauthorizedSince;
  private static boolean livenessFailureReported;
  private static int lastProgressInterval;
  private static long unauthorizedResponseCount;
  private static String mostRecentErrorDetails = UNKNOWN_ERROR_DETAILS;

  private KubernetesApiAuthenticationHealth() {
  }

  /**
   * Records an HTTP 401 response from the operator's Kubernetes API client.
   * @param errorDetails operation and Kubernetes API error details, without credentials or headers
   */
  public static synchronized void reportUnauthorizedResponse(String errorDetails) {
    OffsetDateTime now = SystemClock.now();
    unauthorizedResponseCount++;
    mostRecentErrorDetails = normalizeErrorDetails(errorDetails);
    if (unauthorizedSince == null) {
      unauthorizedSince = now;
      livenessFailureReported = false;
      lastProgressInterval = 0;
      LOGGER.warning(MessageKeys.K8S_API_AUTHENTICATION_FAILURE,
          now.toString(), now.plusSeconds(RESTART_AFTER_SECONDS).toString(), mostRecentErrorDetails);
    }
  }

  /** Records a successful response from the operator's Kubernetes API client. */
  public static synchronized void reportSuccessfulResponse() {
    if (unauthorizedSince != null) {
      LOGGER.info(MessageKeys.K8S_API_AUTHENTICATION_RECOVERED,
          getElapsedSeconds(), unauthorizedResponseCount);
      clearFailure();
    }
  }

  /**
   * Returns true when authentication has failed continuously long enough to restart the operator container.
   */
  public static synchronized boolean shouldFailLiveness() {
    if (unauthorizedSince == null) {
      return false;
    }

    long elapsedSeconds = getElapsedSeconds();
    boolean failedLongEnough = elapsedSeconds >= RESTART_AFTER_SECONDS;
    if (failedLongEnough && !livenessFailureReported) {
      livenessFailureReported = true;
      LOGGER.severe(MessageKeys.K8S_API_AUTHENTICATION_LIVENESS_FAILURE,
          RESTART_AFTER_SECONDS, unauthorizedResponseCount, mostRecentErrorDetails);
    } else if (!failedLongEnough) {
      reportOngoingFailure(elapsedSeconds);
    }
    return failedLongEnough;
  }

  private static void reportOngoingFailure(long elapsedSeconds) {
    int progressInterval = (int) (elapsedSeconds / PROGRESS_INTERVAL_SECONDS);
    if (progressInterval > 0 && progressInterval > lastProgressInterval) {
      lastProgressInterval = progressInterval;
      LOGGER.info(MessageKeys.K8S_API_AUTHENTICATION_FAILURE_ONGOING,
          elapsedSeconds, RESTART_AFTER_SECONDS - elapsedSeconds,
          unauthorizedResponseCount, mostRecentErrorDetails);
    }
  }

  private static long getElapsedSeconds() {
    return Math.max(0, Duration.between(unauthorizedSince, SystemClock.now()).getSeconds());
  }

  private static String normalizeErrorDetails(String errorDetails) {
    String normalized = errorDetails == null ? UNKNOWN_ERROR_DETAILS : errorDetails.replaceAll("\\s+", " ").trim();
    if (normalized.isEmpty()) {
      return UNKNOWN_ERROR_DETAILS;
    }
    return normalized.substring(0, Math.min(normalized.length(), MAX_ERROR_DETAILS_LENGTH));
  }

  static synchronized boolean hasActiveFailure() {
    return unauthorizedSince != null;
  }

  static synchronized void reset() {
    clearFailure();
  }

  private static void clearFailure() {
    unauthorizedSince = null;
    livenessFailureReported = false;
    lastProgressInterval = 0;
    unauthorizedResponseCount = 0;
    mostRecentErrorDetails = UNKNOWN_ERROR_DETAILS;
  }
}

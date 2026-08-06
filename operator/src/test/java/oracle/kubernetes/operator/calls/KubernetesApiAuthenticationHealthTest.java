// Copyright (c) 2026, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.calls;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.LogRecord;

import com.meterware.simplestub.Memento;
import oracle.kubernetes.common.logging.MessageKeys;
import oracle.kubernetes.utils.SystemClockTestSupport;
import oracle.kubernetes.utils.TestUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static oracle.kubernetes.common.utils.LogMatcher.containsInfo;
import static oracle.kubernetes.common.utils.LogMatcher.containsSevere;
import static oracle.kubernetes.common.utils.LogMatcher.containsWarning;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KubernetesApiAuthenticationHealthTest {
  private static final String ERROR_DETAILS = "operation=get, resource=pod, namespace=test: Unauthorized";
  private static final String LATEST_ERROR_DETAILS = "DomainWatcher in namespace test: Unauthorized";
  private final List<Memento> mementos = new ArrayList<>();
  private final List<LogRecord> logRecords = new ArrayList<>();

  @BeforeEach
  void setUp() throws Exception {
    KubernetesApiAuthenticationHealth.reset();
    mementos.add(SystemClockTestSupport.installClock());
    mementos.add(TestUtils.silenceOperatorLogger()
        .collectAllLogMessages(logRecords)
        .withLogLevel(Level.FINE));
  }

  @AfterEach
  void tearDown() {
    KubernetesApiAuthenticationHealth.reset();
    mementos.forEach(Memento::revert);
  }

  @Test
  void unauthorizedResponseStartsAuthenticationFailure() {
    KubernetesApiAuthenticationHealth.reportUnauthorizedResponse(ERROR_DETAILS);

    assertTrue(KubernetesApiAuthenticationHealth.hasActiveFailure());
    assertThat(logRecords, containsWarning(MessageKeys.K8S_API_AUTHENTICATION_FAILURE));
  }

  @Test
  void repeatedUnauthorizedResponsesDoNotRestartFailureWindow() {
    KubernetesApiAuthenticationHealth.reportUnauthorizedResponse(ERROR_DETAILS);
    assertThat(logRecords, containsWarning(MessageKeys.K8S_API_AUTHENTICATION_FAILURE));
    SystemClockTestSupport.increment(KubernetesApiAuthenticationHealth.RESTART_AFTER_SECONDS - 1);
    KubernetesApiAuthenticationHealth.reportUnauthorizedResponse(LATEST_ERROR_DETAILS);

    assertFalse(KubernetesApiAuthenticationHealth.shouldFailLiveness());
    assertThat(logRecords, containsInfo(MessageKeys.K8S_API_AUTHENTICATION_FAILURE_ONGOING)
        .withParams(899L, 1L, 2L, LATEST_ERROR_DETAILS));
    SystemClockTestSupport.increment(1);
    assertTrue(KubernetesApiAuthenticationHealth.shouldFailLiveness());
    assertThat(logRecords, containsSevere(MessageKeys.K8S_API_AUTHENTICATION_LIVENESS_FAILURE)
        .withParams(KubernetesApiAuthenticationHealth.RESTART_AFTER_SECONDS, 2L, LATEST_ERROR_DETAILS));
  }

  @Test
  void successfulResponseClearsAuthenticationFailure() {
    KubernetesApiAuthenticationHealth.reportUnauthorizedResponse(ERROR_DETAILS);
    assertThat(logRecords, containsWarning(MessageKeys.K8S_API_AUTHENTICATION_FAILURE));
    SystemClockTestSupport.increment(12);

    KubernetesApiAuthenticationHealth.reportSuccessfulResponse();

    assertFalse(KubernetesApiAuthenticationHealth.hasActiveFailure());
    assertFalse(KubernetesApiAuthenticationHealth.shouldFailLiveness());
    assertThat(logRecords,
        containsInfo(MessageKeys.K8S_API_AUTHENTICATION_RECOVERED).withParams(12L, 1L));
  }

  @Test
  void continuousFailureTriggersLivenessOnlyAfterRestartInterval() {
    KubernetesApiAuthenticationHealth.reportUnauthorizedResponse(ERROR_DETAILS);
    assertThat(logRecords, containsWarning(MessageKeys.K8S_API_AUTHENTICATION_FAILURE));
    SystemClockTestSupport.increment(KubernetesApiAuthenticationHealth.RESTART_AFTER_SECONDS - 1);

    assertFalse(KubernetesApiAuthenticationHealth.shouldFailLiveness());
    assertThat(logRecords, containsInfo(MessageKeys.K8S_API_AUTHENTICATION_FAILURE_ONGOING)
        .withParams(899L, 1L, 1L, ERROR_DETAILS));
    assertThat(logRecords, not(containsSevere(MessageKeys.K8S_API_AUTHENTICATION_LIVENESS_FAILURE)));

    SystemClockTestSupport.increment(1);

    assertTrue(KubernetesApiAuthenticationHealth.shouldFailLiveness());
    assertThat(logRecords, containsSevere(MessageKeys.K8S_API_AUTHENTICATION_LIVENESS_FAILURE)
        .withParams(KubernetesApiAuthenticationHealth.RESTART_AFTER_SECONDS, 1L, ERROR_DETAILS));
  }

  @Test
  void ongoingFailureIsReportedEveryTwoMinutesWithLatestErrorDetails() {
    KubernetesApiAuthenticationHealth.reportUnauthorizedResponse(ERROR_DETAILS);
    assertThat(logRecords, containsWarning(MessageKeys.K8S_API_AUTHENTICATION_FAILURE));

    SystemClockTestSupport.increment(KubernetesApiAuthenticationHealth.PROGRESS_INTERVAL_SECONDS - 1);
    assertFalse(KubernetesApiAuthenticationHealth.shouldFailLiveness());
    assertThat(logRecords, not(containsInfo(MessageKeys.K8S_API_AUTHENTICATION_FAILURE_ONGOING)));

    SystemClockTestSupport.increment(1);
    assertFalse(KubernetesApiAuthenticationHealth.shouldFailLiveness());
    assertThat(logRecords, containsInfo(MessageKeys.K8S_API_AUTHENTICATION_FAILURE_ONGOING)
        .withParams(120L, 780L, 1L, ERROR_DETAILS));

    KubernetesApiAuthenticationHealth.reportUnauthorizedResponse(LATEST_ERROR_DETAILS);
    SystemClockTestSupport.increment(KubernetesApiAuthenticationHealth.PROGRESS_INTERVAL_SECONDS);
    assertFalse(KubernetesApiAuthenticationHealth.shouldFailLiveness());
    assertThat(logRecords, containsInfo(MessageKeys.K8S_API_AUTHENTICATION_FAILURE_ONGOING)
        .withParams(240L, 660L, 2L, LATEST_ERROR_DETAILS));

    assertFalse(KubernetesApiAuthenticationHealth.shouldFailLiveness());
    assertThat(logRecords, not(containsInfo(MessageKeys.K8S_API_AUTHENTICATION_FAILURE_ONGOING)));
  }
}

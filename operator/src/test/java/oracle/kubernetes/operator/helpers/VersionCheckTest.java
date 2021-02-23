// Copyright (c) 2018, 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helpers;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.LogRecord;
import java.util.stream.Stream;

import com.meterware.simplestub.Memento;
import io.kubernetes.client.openapi.models.VersionInfo;
import oracle.kubernetes.operator.ClientFactoryStub;
import oracle.kubernetes.utils.TestUtils;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static oracle.kubernetes.operator.helpers.VersionCheckTest.TestType.LOG_MSG_TEST;
import static oracle.kubernetes.operator.helpers.VersionCheckTest.TestType.VERSION_TEST;
import static oracle.kubernetes.operator.helpers.VersionCheckTest.VersionMatcher.returnsVersion;
import static oracle.kubernetes.operator.logging.MessageKeys.K8S_VERSION_CHECK;
import static oracle.kubernetes.operator.logging.MessageKeys.K8S_VERSION_CHECK_FAILURE;
import static oracle.kubernetes.operator.logging.MessageKeys.K8S_VERSION_TOO_LOW;
import static oracle.kubernetes.utils.LogMatcher.containsInfo;
import static oracle.kubernetes.utils.LogMatcher.containsWarning;
import static org.hamcrest.junit.MatcherAssert.assertThat;

public class VersionCheckTest {

  // The log messages to be checked during this test
  private static final String[] LOG_KEYS = {
    K8S_VERSION_TOO_LOW, K8S_VERSION_CHECK, K8S_VERSION_CHECK_FAILURE,
  };
  private final List<Memento> mementos = new ArrayList<>();
  private final List<LogRecord> logRecords = new ArrayList<>();
  private TestUtils.ConsoleHandlerMemento consoleControl;
  private final CallTestSupport testSupport = new CallTestSupport();


  private static Stream<Arguments> getTestParams() {
    return Stream.of(
          Arguments.of(VERSION_TEST, "0", "", "", returnsVersion(0, 0), ignoring(K8S_VERSION_TOO_LOW)),
          Arguments.of(LOG_MSG_TEST, "0", "", "", containsWarning(K8S_VERSION_TOO_LOW), noIgnores()),
          Arguments.of(LOG_MSG_TEST, "1", "6+", "", containsWarning(K8S_VERSION_TOO_LOW), noIgnores()),
          Arguments.of(LOG_MSG_TEST, "1", "10", "1+cor.0", containsWarning(K8S_VERSION_TOO_LOW), noIgnores()),
          Arguments.of(LOG_MSG_TEST, "1", "10", "11", containsWarning(K8S_VERSION_TOO_LOW), noIgnores()),
          Arguments.of(LOG_MSG_TEST, "1", "11", "4", containsWarning(K8S_VERSION_TOO_LOW), noIgnores()),
          Arguments.of(LOG_MSG_TEST, "1", "13", "5", containsWarning(K8S_VERSION_TOO_LOW), noIgnores()),
          Arguments.of(LOG_MSG_TEST, "1", "12", "2", containsWarning(K8S_VERSION_TOO_LOW), noIgnores()),
          Arguments.of(LOG_MSG_TEST, "1", "14", "8", containsInfo(K8S_VERSION_CHECK), noIgnores()),
          Arguments.of(VERSION_TEST, "1", "15", "7", returnsVersion(1, 15), ignoring(K8S_VERSION_CHECK)),
          Arguments.of(VERSION_TEST, "1", "16", "1", returnsVersion(1, 16), ignoring(K8S_VERSION_CHECK)),
          Arguments.of(VERSION_TEST, "1", "17", "2", returnsVersion(1, 17), ignoring(K8S_VERSION_CHECK)),
          Arguments.of(VERSION_TEST, "1", "18", "0", returnsVersion(1, 18), ignoring(K8S_VERSION_CHECK)),
          Arguments.of(VERSION_TEST, "2", "7", "", returnsVersion(2, 7), ignoring(K8S_VERSION_CHECK)),
          Arguments.of(LOG_MSG_TEST, "2", "", "", containsInfo(K8S_VERSION_CHECK), noIgnores())
        );
  }

  private static String[] ignoring(String... logMessages) {
    return logMessages;
  }

  private static String[] noIgnores() {
    return new String[0];
  }

  private static VersionInfo createVersionInfo(
      String majorVersion, String minorVersion, String revision) {
    VersionInfo versionInfo;
    versionInfo = new VersionInfo().major(majorVersion).minor(minorVersion);
    versionInfo.setGitVersion(majorVersion + "." + minorVersion + "." + revision);
    return versionInfo;
  }

  @BeforeEach
  public void setUp() throws Exception {
    consoleControl = TestUtils.silenceOperatorLogger().collectLogMessages(logRecords, LOG_KEYS);
    mementos.add(consoleControl);
    mementos.add(ClientFactoryStub.install());
    mementos.add(TuningParametersStub.install());
    mementos.add(testSupport.installSynchronousCallDispatcher());
  }

  @AfterEach
  public void tearDown() {
    mementos.forEach(Memento::revert);
  }

  @ParameterizedTest
  @MethodSource("getTestParams")
  public void test(TestType testType,
        String majorVersion,
        String minorVersion,
        String revision,
        Matcher<KubernetesVersion> matcher,
        String[] ignoredLogMessages) {
    specifyK8sVersion(majorVersion, minorVersion, revision);
    for (String ignoredLogMessage : ignoredLogMessages) {
      consoleControl.ignoreMessage(ignoredLogMessage);
    }

    testType.runTest(logRecords, matcher);
  }

  private void specifyK8sVersion(String majorVersion, String minorVersion, String revision) {
    testSupport
        .createCannedResponse("getVersion")
        .returning(createVersionInfo(majorVersion, minorVersion, revision));
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  enum TestType {
    LOG_MSG_TEST {
      @Override
      void runTest(List<LogRecord> logRecords, Matcher matcher) {
        HealthCheckHelper.performK8sVersionCheck();

        assertThat(logRecords, matcher);
      }
    },
    VERSION_TEST {
      @Override
      void runTest(List<LogRecord> logRecords, Matcher matcher) {
        assertThat(HealthCheckHelper.performK8sVersionCheck(), matcher);
      }
    };

    abstract void runTest(List<LogRecord> logRecords, Matcher matcher);
  }

  @SuppressWarnings("unused")
  static class VersionMatcher extends org.hamcrest.TypeSafeDiagnosingMatcher<KubernetesVersion> {
    private final int expectedMajor;
    private final int expectedMinor;

    private VersionMatcher(int expectedMajor, int expectedMinor) {
      this.expectedMajor = expectedMajor;
      this.expectedMinor = expectedMinor;
    }

    static VersionMatcher returnsVersion(int major, int minor) {
      return new VersionMatcher(major, minor);
    }

    @Override
    protected boolean matchesSafely(KubernetesVersion item, Description mismatchDescription) {
      if (item.getMajor() == expectedMajor && item.getMinor() == expectedMinor) {
        return true;
      }

      describe(mismatchDescription, item.getMajor(), item.getMinor());
      return false;
    }

    private void describe(Description description, int major, int minor) {
      description.appendText(major + "." + minor);
    }

    @Override
    public void describeTo(Description description) {
      describe(description, expectedMajor, expectedMinor);
    }
  }
}

// Copyright (c) 2018, 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helpers;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.logging.LogRecord;

import com.meterware.simplestub.Memento;
import io.kubernetes.client.openapi.models.VersionInfo;
import oracle.kubernetes.operator.ClientFactoryStub;
import oracle.kubernetes.utils.TestUtils;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import static oracle.kubernetes.operator.helpers.VersionCheckTest.TestType.LOG_MSG_TEST;
import static oracle.kubernetes.operator.helpers.VersionCheckTest.TestType.VERSION_TEST;
import static oracle.kubernetes.operator.helpers.VersionCheckTest.VersionMatcher.returnsVersion;
import static oracle.kubernetes.operator.logging.MessageKeys.K8S_VERSION_CHECK;
import static oracle.kubernetes.operator.logging.MessageKeys.K8S_VERSION_CHECK_FAILURE;
import static oracle.kubernetes.operator.logging.MessageKeys.K8S_VERSION_TOO_LOW;
import static oracle.kubernetes.utils.LogMatcher.containsInfo;
import static oracle.kubernetes.utils.LogMatcher.containsWarning;
import static org.hamcrest.junit.MatcherAssert.assertThat;

@RunWith(Parameterized.class)
public class VersionCheckTest {

  // The log messages to be checked during this test
  private static final String[] LOG_KEYS = {
    K8S_VERSION_TOO_LOW, K8S_VERSION_CHECK, K8S_VERSION_CHECK_FAILURE,
  };
  private List<Memento> mementos = new ArrayList<>();
  private List<LogRecord> logRecords = new ArrayList<>();
  private TestUtils.ConsoleHandlerMemento consoleControl;
  private CallTestSupport testSupport = new CallTestSupport();

  private TestType testType;
  private String majorVersion;
  private String minorVersion;
  private String revision;
  private Matcher<KubernetesVersion> matcher;
  private String[] ignoredLogMessages;

  /**
   * Version check test constructor.
   * @param testType test type
   * @param majorVersion major
   * @param minorVersion minor
   * @param revision rev
   * @param matcher matcher
   * @param ignoredLogMessages ignored log messages
   */
  public VersionCheckTest(
      TestType testType,
      String majorVersion,
      String minorVersion,
      String revision,
      Matcher<KubernetesVersion> matcher,
      String[] ignoredLogMessages) {
    this.testType = testType;
    this.majorVersion = majorVersion;
    this.minorVersion = minorVersion;
    this.revision = revision;
    this.matcher = matcher;
    this.ignoredLogMessages = ignoredLogMessages;
  }

  /**
   * Initialize data.
   * @return data
   */
  @Parameters(name = "{0}: {1}.{2}.{3}")
  public static Collection<Object[]> data() {
    return Arrays.asList(
        new Object[][] {
          {VERSION_TEST, "0", "", "", returnsVersion(0, 0), ignoring(K8S_VERSION_TOO_LOW)},
          {LOG_MSG_TEST, "0", "", "", containsWarning(K8S_VERSION_TOO_LOW), noIgnores()},
          {LOG_MSG_TEST, "1", "6+", "", containsWarning(K8S_VERSION_TOO_LOW), noIgnores()},
          {LOG_MSG_TEST, "1", "10", "1+cor.0", containsWarning(K8S_VERSION_TOO_LOW), noIgnores()},
          {LOG_MSG_TEST, "1", "10", "11", containsWarning(K8S_VERSION_TOO_LOW), noIgnores()},
          {LOG_MSG_TEST, "1", "11", "4", containsWarning(K8S_VERSION_TOO_LOW), noIgnores()},
          {LOG_MSG_TEST, "1", "13", "5", containsInfo(K8S_VERSION_CHECK), noIgnores()},
          {LOG_MSG_TEST, "1", "12", "2", containsWarning(K8S_VERSION_TOO_LOW), noIgnores()},
          {LOG_MSG_TEST, "1", "14", "8", containsInfo(K8S_VERSION_CHECK), noIgnores()},
          {VERSION_TEST, "1", "15", "7", returnsVersion(1, 15), ignoring(K8S_VERSION_CHECK)},
          {LOG_MSG_TEST, "1", "13", "6", containsInfo(K8S_VERSION_CHECK), noIgnores()},
          {VERSION_TEST, "2", "7", "", returnsVersion(2, 7), ignoring(K8S_VERSION_CHECK)},
          {LOG_MSG_TEST, "2", "", "", containsInfo(K8S_VERSION_CHECK), noIgnores()},
        });
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

  /**
   * Setup test.
   * @throws Exception on failure
   */
  @Before
  public void setUp() throws Exception {
    consoleControl = TestUtils.silenceOperatorLogger().collectLogMessages(logRecords, LOG_KEYS);
    mementos.add(consoleControl);
    mementos.add(ClientFactoryStub.install());
    mementos.add(testSupport.installSynchronousCallDispatcher());
  }

  /**
   * Tear down test.
   */
  @After
  public void tearDown() {
    for (Memento memento : mementos) {
      memento.revert();
    }
  }

  @Test
  public void test() {
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

  @SuppressWarnings("unchecked")
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
    private int expectedMajor;
    private int expectedMinor;

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

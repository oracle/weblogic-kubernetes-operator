// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helpers;

import static oracle.kubernetes.LogMatcher.containsInfo;
import static oracle.kubernetes.LogMatcher.containsWarning;
import static oracle.kubernetes.operator.helpers.VersionCheckTest.TestType.LOG_MSG_TEST;
import static oracle.kubernetes.operator.helpers.VersionCheckTest.TestType.VERSION_TEST;
import static oracle.kubernetes.operator.helpers.VersionCheckTest.VersionMatcher.returnsVersion;
import static oracle.kubernetes.operator.logging.MessageKeys.*;
import static org.hamcrest.junit.MatcherAssert.assertThat;

import com.meterware.simplestub.Memento;
import io.kubernetes.client.models.VersionInfo;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.logging.LogRecord;
import oracle.kubernetes.TestUtils;
import oracle.kubernetes.operator.ClientFactoryStub;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

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

  @Before
  public void setUp() throws Exception {
    consoleControl = TestUtils.silenceOperatorLogger().collectLogMessages(logRecords, LOG_KEYS);
    mementos.add(consoleControl);
    mementos.add(ClientFactoryStub.install());
    mementos.add(testSupport.installSynchronousCallDispatcher());
  }

  @After
  public void tearDown() {
    for (Memento memento : mementos) memento.revert();
  }

  @Parameters(name = "{0}: {1}.{2}.{3}")
  public static Collection<Object[]> data() {
    return Arrays.asList(
        new Object[][] {
          {VERSION_TEST, "0", "", "", returnsVersion(0, 0), ignoring(K8S_VERSION_TOO_LOW)},
          {LOG_MSG_TEST, "0", "", "", containsWarning(K8S_VERSION_TOO_LOW), noIgnores()},
          {LOG_MSG_TEST, "1", "6+", "", containsWarning(K8S_VERSION_TOO_LOW), noIgnores()},
          {LOG_MSG_TEST, "1", "10", "1+cor.0", containsWarning(K8S_VERSION_TOO_LOW), noIgnores()},
          {LOG_MSG_TEST, "1", "10", "11", containsInfo(K8S_VERSION_CHECK), noIgnores()},
          {LOG_MSG_TEST, "1", "11", "4", containsWarning(K8S_VERSION_TOO_LOW), noIgnores()},
          {LOG_MSG_TEST, "1", "11", "6", containsInfo(K8S_VERSION_CHECK), noIgnores()},
          {LOG_MSG_TEST, "1", "12", "2", containsWarning(K8S_VERSION_TOO_LOW), noIgnores()},
          {LOG_MSG_TEST, "1", "12", "3", containsInfo(K8S_VERSION_CHECK), noIgnores()},
          {VERSION_TEST, "1", "13", "3", returnsVersion(1, 13), ignoring(K8S_VERSION_CHECK)},
          {LOG_MSG_TEST, "1", "13", "3", containsInfo(K8S_VERSION_CHECK), noIgnores()},
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

  @Test
  public void test() {
    specifyK8sVersion(majorVersion, minorVersion, revision);
    for (String ignoredLogMessage : ignoredLogMessages)
      consoleControl.ignoreMessage(ignoredLogMessage);

    testType.runTest(logRecords, matcher);
  }

  private void specifyK8sVersion(String majorVersion, String minorVersion, String revision) {
    testSupport
        .createCannedResponse("getVersion")
        .returning(createVersionInfo(majorVersion, minorVersion, revision));
  }

  private static VersionInfo createVersionInfo(
      String majorVersion, String minorVersion, String revision) {
    VersionInfo versionInfo;
    versionInfo = new VersionInfo().major(majorVersion).minor(minorVersion);
    versionInfo.setGitVersion(majorVersion + "." + minorVersion + "." + revision);
    return versionInfo;
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
      if (item.getMajor() == expectedMajor && item.getMinor() == expectedMinor) return true;

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

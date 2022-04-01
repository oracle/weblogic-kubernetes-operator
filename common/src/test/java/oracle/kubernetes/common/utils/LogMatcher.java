// Copyright (c) 2018, 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.common.utils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.LogRecord;

import org.hamcrest.Description;
import org.hamcrest.TypeSafeDiagnosingMatcher;

public class LogMatcher extends TypeSafeDiagnosingMatcher<Collection<LogRecord>> {

  private String expectedMessage;
  private Level expectedLevel;
  private Object[] expectedParameters;
  private LogMatcher[] logMatchers;

  private LogMatcher(LogMatcher[] logMatchers) {
    this.logMatchers = logMatchers;
  }

  private LogMatcher(Level expectedLevel, String expectedMessage) {
    this(expectedLevel, expectedMessage, null);
  }

  private LogMatcher(Level expectedLevel, String expectedMessage, Object[] expectedParameters) {
    this.expectedMessage = expectedMessage;
    this.expectedLevel = expectedLevel;
    this.expectedParameters = expectedParameters;
  }

  public static LogMatcher containsInfo(String expectedMessage) {
    return new LogMatcher(Level.INFO, expectedMessage);
  }

  public static LogMatcher containsWarning(String expectedMessage) {
    return new LogMatcher(Level.WARNING, expectedMessage);
  }

  public static LogMatcher containsSevere(String expectedMessage) {
    return new LogMatcher(Level.SEVERE, expectedMessage);
  }

  public static LogMatcher containsConfig(String expectedMessage) {
    return new LogMatcher(Level.CONFIG, expectedMessage);
  }

  public static LogMatcher containsFine(String expectedMessage) {
    return new LogMatcher(Level.FINE, expectedMessage);
  }

  public LogMatcher withParams(Object... expectedParameters) {
    this.expectedParameters = expectedParameters;
    return this;
  }

  public static LogMatcher containsInOrder(LogMatcher... logMatchers) {
    return new LogMatcher(logMatchers);
  }

  @Override
  protected boolean matchesSafely(
      Collection<LogRecord> logRecords, Description mismatchDescription) {
    if (logMatchers == null) {
      return logRecords.removeIf(this::matches) || noLogMessageFound(mismatchDescription);
    } else {
      return matchesAll(logRecords, mismatchDescription);
    }
  }

  private boolean matchesAll(Collection<LogRecord> logRecords, Description mismatchDescription) {
    if (logRecords.size() < logMatchers.length) {
      mismatchDescription.appendText("Expecting " + logMatchers.length
          + " log messages but only found " + logRecords.size());
      return false;
    }
    ArrayList<LogRecord> matchingRecords = new ArrayList<>();

    Iterator<LogRecord> logRecordIterator = logRecords.iterator();
    mismatchDescription.appendText("[ ");
    for (LogMatcher logMatcher: logMatchers) {
      boolean matched = false;
      while (logRecordIterator.hasNext() && !matched) {
        LogRecord logRecord = logRecordIterator.next();
        appendToDescription(logRecord, mismatchDescription);
        if (logMatcher.matches(logRecord)) {
          matchingRecords.add(logRecord);
          matched = true;
        }
      }
      if (!matched) {
        mismatchDescription.appendText(" ]");
        return false;
      }
    }
    logRecords.removeAll(matchingRecords);
    return true;
  }

  private boolean matches(LogRecord item) {
    return item.getLevel() == expectedLevel
        && item.getMessage().equals(expectedMessage)
        && (expectedParameters == null || containsAll(getParametersAsList(item), expectedParameters));
  }

  private List<Object> getParametersAsList(LogRecord item) {
    return Optional.ofNullable(item.getParameters()).map(Arrays::asList).orElse(Collections.emptyList());
  }

  private boolean containsAll(List<Object> actualParameters, Object[] expectedParameters) {
    return Arrays.stream(expectedParameters).allMatch(actualParameters::contains);
  }

  private boolean noLogMessageFound(Description mismatchDescription) {
    mismatchDescription.appendText("no matching log message was found");
    return false;
  }

  @Override
  public void describeTo(Description description) {
    if (logMatchers != null) {
      description.appendText("[");
      for (LogMatcher logMatcher: logMatchers) {
        logMatcher.describeTo(description);
        description.appendText(" ");
      }
      description.appendText("]");
      return;
    }
    description
        .appendValue(expectedLevel)
        .appendText(" log message with value ")
        .appendValue(expectedMessage);
    if (expectedParameters != null) {
      description.appendText(" with parameter(s) ").appendValue(expectedParameters);
    }
    description.appendText(" ");
  }

  private void appendToDescription(LogRecord logRecord, Description description) {
    description
        .appendValue(logRecord.getLevel())
        .appendText(" log message with value ")
        .appendValue(logRecord.getMessage());
    if (logRecord.getParameters() != null) {
      description.appendText(" and includes parameter ").appendValue(logRecord.getParameters());
    }
  }
}

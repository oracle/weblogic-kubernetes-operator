// Copyright (c) 2018, 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.utils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.logging.Level;
import java.util.logging.LogRecord;

import org.hamcrest.Description;

public class LogMatcher
    extends org.hamcrest.TypeSafeDiagnosingMatcher<
        java.util.Collection<java.util.logging.LogRecord>> {

  private String expectedMessage;
  private Level expectedLevel;
  private Object expectedParameter;
  private LogMatcher[] logMatchers;

  private LogMatcher(LogMatcher[] logMatchers) {
    this.logMatchers = logMatchers;
  }

  private LogMatcher(Level expectedLevel, String expectedMessage) {
    this(expectedLevel, expectedMessage, null);
  }

  private LogMatcher(Level expectedLevel, String expectedMessage, Object expectedParameter) {
    this.expectedMessage = expectedMessage;
    this.expectedLevel = expectedLevel;
    this.expectedParameter = expectedParameter;
  }

  public static LogMatcher containsInfo(String expectedMessage) {
    return new LogMatcher(Level.INFO, expectedMessage);
  }

  public static LogMatcher containsInfo(String expectedMessage, Object expectedParameter) {
    return new LogMatcher(Level.INFO, expectedMessage, expectedParameter);
  }

  public static LogMatcher containsWarning(String expectedMessage) {
    return new LogMatcher(Level.WARNING, expectedMessage);
  }

  public static LogMatcher containsWarning(String expectedMessage, Object expectedParameter) {
    return new LogMatcher(Level.WARNING, expectedMessage, expectedParameter);
  }

  public static LogMatcher containsSevere(String expectedMessage) {
    return new LogMatcher(Level.SEVERE, expectedMessage);
  }

  public static LogMatcher containsFine(String expectedMessage) {
    return new LogMatcher(Level.FINE, expectedMessage);
  }

  public static LogMatcher containsFine(String expectedMessage, Object expectedParameter) {
    return new LogMatcher(Level.FINE, expectedMessage, expectedParameter);
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
        && (expectedParameter == null
            || Arrays.stream(item.getParameters()).anyMatch(expectedParameter::equals));
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
    if (expectedParameter != null) {
      description.appendText(" with parameter(s) ").appendValue(expectedParameter);
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

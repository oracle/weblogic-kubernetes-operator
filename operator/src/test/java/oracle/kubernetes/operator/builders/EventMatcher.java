// Copyright (c) 2018, 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.builders;

import java.util.Objects;

import io.kubernetes.client.util.Watch;
import org.hamcrest.Description;
import org.hamcrest.TypeSafeDiagnosingMatcher;

/** A matcher for events returned by a Watch object or sent to an event listener. */
public class EventMatcher extends TypeSafeDiagnosingMatcher<Watch.Response<?>> {
  private final String expectedType;
  private Object expectedObject;
  private int expectedStatusCode;

  private EventMatcher(String expectedType, Object expectedObject) {
    this.expectedType = expectedType;
    this.expectedObject = expectedObject;
  }

  private EventMatcher(String expectedType, int expectedStatusCode) {
    this.expectedType = expectedType;
    this.expectedStatusCode = expectedStatusCode;
  }

  public static EventMatcher addEvent(Object object) {
    return new EventMatcher("ADDED", object);
  }

  public static EventMatcher deleteEvent(Object object) {
    return new EventMatcher("DELETED", object);
  }

  public static EventMatcher modifyEvent(Object object) {
    return new EventMatcher("MODIFIED", object);
  }

  public static EventMatcher bookmarkEvent(Object object) {
    return new EventMatcher("BOOKMARK", object);
  }

  public static EventMatcher errorEvent(int expectedStatusCode) {
    return new EventMatcher("ERROR", expectedStatusCode);
  }

  @Override
  protected boolean matchesSafely(Watch.Response<?> item, Description mismatchDescription) {
    if (isExpectedUpdateResponse(item) || isExpectedErrorResponse(item)) {
      return true;
    }

    if (isError(item.type) && item.status != null) {
      mismatchDescription.appendText("Error with status code ").appendValue(item.status.getCode());
    } else if (isError(item.type)) {
      mismatchDescription.appendValue("Error with no status code");
    } else {
      mismatchDescription.appendValue(item.type).appendText(" event for ").appendValue(item.object);
    }
    return false;
  }

  private boolean isError(String expectedType) {
    return expectedType.equals("ERROR");
  }

  private boolean isExpectedUpdateResponse(Watch.Response<?> item) {
    return item.type.equals(expectedType) && Objects.equals(item.object, expectedObject);
  }

  private boolean isExpectedErrorResponse(Watch.Response<?> item) {
    return isError(item.type)
        && item.status != null
        && Objects.equals(item.status.getCode(), expectedStatusCode);
  }

  @Override
  public void describeTo(Description description) {
    String expectedType = this.expectedType;
    if (isError(expectedType)) {
      description.appendText("error event with code ").appendValue(expectedStatusCode);
    } else {
      description
          .appendValue(this.expectedType)
          .appendText(" event for ")
          .appendValue(expectedObject);
    }
  }
}

// Copyright (c) 2019, 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.utils;

import java.time.OffsetDateTime;

import com.meterware.simplestub.Memento;
import com.meterware.simplestub.StaticStubSupport;
import org.hamcrest.Description;
import org.hamcrest.Matcher;

public class SystemClockTestSupport {
  private static TestSystemClock clock;

  public static Memento installClock() throws NoSuchFieldException {
    clock = new TestSystemClock();
    return StaticStubSupport.install(SystemClock.class, "DELEGATE", clock);
  }

  public static Matcher<OffsetDateTime> isDuringTest() {
    return new DuringTestTimeMatcher();
  }

  /**
   * Increments the system clock by the specified number of seconds.
   * @param numSeconds the number of seconds by which to advance the system clock
   */
  public static void increment(long numSeconds) {
    clock.increment(numSeconds);
  }

  /**
   * Increments the system clock by one second.
   */
  public static void increment() {
    clock.increment(1L);
  }

  static class TestSystemClock extends SystemClock {
    private final OffsetDateTime testStartTime = OffsetDateTime.now();
    private OffsetDateTime currentTime = testStartTime;

    @Override
    public OffsetDateTime getCurrentTime() {
      return currentTime;
    }

    void increment(long numSeconds) {
      currentTime = currentTime.plusSeconds(numSeconds);
    }
  }

  static class DuringTestTimeMatcher extends org.hamcrest.TypeSafeDiagnosingMatcher<OffsetDateTime> {

    @Override
    protected boolean matchesSafely(OffsetDateTime item, Description mismatchDescription) {
      if (item == null) {
        return foundNullTime(mismatchDescription);
      }
      if (!clock.testStartTime.isAfter(item) && !item.isAfter(clock.currentTime)) {
        return true;
      }

      mismatchDescription.appendValue(item);
      return false;
    }

    private boolean foundNullTime(Description mismatchDescription) {
      mismatchDescription.appendText("null");
      return false;
    }

    @Override
    public void describeTo(Description description) {
      description
          .appendText("time between ")
          .appendValue(clock.testStartTime)
          .appendText(" and ")
          .appendValue(clock.currentTime);
    }
  }
}

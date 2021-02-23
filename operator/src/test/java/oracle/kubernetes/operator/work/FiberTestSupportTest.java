// Copyright (c) 2020, 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.work;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.junit.MatcherAssert.assertThat;

public class FiberTestSupportTest {
  private final FiberTestSupport testSupport = new FiberTestSupport();
  private final List<String> itemsRun = new ArrayList<>();
  private final Runnable reportItemRun = () -> itemsRun.add("item");


  @Test
  public void whenItemScheduledImmediately_runIt() {
    testSupport.schedule(reportItemRun);

    assertThat(itemsRun, contains("item"));
  }

  @Test
  public void whenItemScheduledForFuture_dontExecuteItImmediately() {
    testSupport.schedule(reportItemRun, 1, SECONDS);

    assertThat(itemsRun, empty());
  }

  @Test
  public void afterItemScheduledForFuture_executeEachTimeAdvanced() {
    testSupport.scheduleWithFixedDelay(reportItemRun, 1, 1, SECONDS);
    testSupport.setTime(1, SECONDS);
    testSupport.setTime(2, SECONDS);
    testSupport.setTime(3, SECONDS);

    assertThat(itemsRun, contains("item", "item", "item"));
  }

  @Test
  public void afterItemScheduledWithFixedDelay_executeMultipleTimesAfterTimeAdvanced() {
    testSupport.scheduleWithFixedDelay(reportItemRun, 200, 500, MILLISECONDS);
    testSupport.setTime(1, SECONDS);

    assertThat(itemsRun, contains("item", "item"));
  }


}
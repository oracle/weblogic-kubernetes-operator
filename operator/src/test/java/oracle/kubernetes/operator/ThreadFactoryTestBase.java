// Copyright (c) 2018, 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadFactory;
import javax.annotation.Nonnull;

import org.junit.Rule;
import org.junit.rules.TestRule;
import org.junit.rules.TestWatcher;
import org.junit.runner.Description;

public class ThreadFactoryTestBase implements ThreadFactory {
  private final List<Thread> threads = new ArrayList<>();
  private String testName;
  @Rule
  public TestRule watcher =
      new TestWatcher() {
        @Override
        protected void starting(Description description) {
          testName = description.getMethodName();
        }
      };

  @Override
  public Thread newThread(@Nonnull Runnable r) {
    Thread thread = new Thread(r);
    threads.add(thread);
    thread.setName(String.format("Test thread %d for %s", threads.size(), testName));
    return thread;
  }

  void shutDownThreads() {
    for (Thread thread : threads) {
      shutDown(thread);
    }
  }

  private void shutDown(Thread thread) {
    try {
      thread.interrupt();
      thread.join();
    } catch (InterruptedException ignored) {
      // no-op
    }
  }
}

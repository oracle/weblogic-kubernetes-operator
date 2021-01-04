// Copyright (c) 2018, 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.work;

import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

public class ThreadFactorySingleton {

  private static final ThreadFactory DEFAULT_FACTORY = Executors.defaultThreadFactory();
  private static final ThreadFactory INSTANCE =
      (r) -> {
        Thread t = DEFAULT_FACTORY.newThread(r);
        if (!t.isDaemon()) {
          t.setDaemon(true);
        }
        return t;
      };

  public static ThreadFactory getInstance() {
    return INSTANCE;
  }
}

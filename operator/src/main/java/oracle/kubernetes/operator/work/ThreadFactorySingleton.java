// Copyright (c) 2018, 2024, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.work;

import java.util.concurrent.ThreadFactory;

public class ThreadFactorySingleton {

  private ThreadFactorySingleton() {
    // no-op
  }

  @SuppressWarnings({"FieldMayBeFinal", "CanBeFinal"})
  private static ThreadFactory instance = Thread.ofVirtual().factory();

  public static ThreadFactory getInstance() {
    return instance;
  }
}

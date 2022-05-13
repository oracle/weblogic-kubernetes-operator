// Copyright (c) 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.tuning;

import oracle.kubernetes.operator.WatchTuning;

public class FakeWatchTuning implements WatchTuning {

  @Override
  public int getWatchLifetime() {
    return 30;
  }

  @Override
  public int getWatchMinimumDelay() {
    return 0;
  }

  @Override
  public int getWatchBackstopRecheckDelay() {
    return 5;
  }

  @Override
  public int getWatchBackstopRecheckCount() {
    return 24;
  }
}

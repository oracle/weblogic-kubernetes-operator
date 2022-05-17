// Copyright (c) 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator;

/**
 * A collection of tuning parameters to control watchers.
 */
public interface WatchTuning {

  int getWatchLifetime();

  int getWatchMinimumDelay();

  int getWatchBackstopRecheckDelay();

  int getWatchBackstopRecheckCount();
}

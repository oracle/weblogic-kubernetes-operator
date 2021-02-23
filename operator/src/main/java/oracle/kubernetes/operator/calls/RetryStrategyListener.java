// Copyright (c) 2019, 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.calls;

public interface RetryStrategyListener {

  /**
   * Retry strategy should call this method to indicate that the listen time should be doubled.
   */
  void listenTimeoutDoubled();
}

// Copyright 2019, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.calls;

public interface RetryStrategyListener {

  /**
   * Retry strategy should call this method to indicate that the listen time should be doubled.
   */
  void listenTimeoutDoubled();
}

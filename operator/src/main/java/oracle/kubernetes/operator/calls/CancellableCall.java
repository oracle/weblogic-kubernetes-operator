// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.calls;

/** An interface for an asynchronous API invocation that can be canceled. */
public interface CancellableCall {

  /** Cancels the active call. */
  void cancel();
}

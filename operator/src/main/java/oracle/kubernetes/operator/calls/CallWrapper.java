// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.calls;

import com.squareup.okhttp.Call;

/** A wrapper for an OKHttp call to isolate its own callers. */
public class CallWrapper implements CancellableCall {

  private Call underlyingCall;

  public CallWrapper(Call underlyingCall) {
    this.underlyingCall = underlyingCall;
  }

  @Override
  public void cancel() {
    underlyingCall.cancel();
  }
}

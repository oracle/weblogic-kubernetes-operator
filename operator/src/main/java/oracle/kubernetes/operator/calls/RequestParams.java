// Copyright 2017, 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.calls;

import oracle.kubernetes.operator.builders.CallParams;

public final class RequestParams {
  public final String call;
  public final String namespace;
  public final String name;
  public final Object body;
  private CallParams callParams;

  public RequestParams(String call, String namespace, String name, Object body) {
    this.call = call;
    this.namespace = namespace;
    this.name = name;
    this.body = body;
  }

  public RequestParams(
      String call, String namespace, String name, Object body, CallParams callParams) {
    this.call = call;
    this.namespace = namespace;
    this.name = name;
    this.body = body;
    this.callParams = callParams;
  }

  public String getLabelSelector() {
    return callParams.getLabelSelector();
  }
}

// Copyright (c) 2017, 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.calls;

import oracle.kubernetes.operator.builders.CallParams;

public final class RequestParams {
  public final String call;
  public final String namespace;
  public final String name;
  public final Object body;
  private CallParams callParams;

  /**
   * Construct request params.
   * @param call call
   * @param namespace namespace
   * @param name name
   * @param body body
   */
  public RequestParams(String call, String namespace, String name, Object body) {
    this.call = call;
    this.namespace = namespace;
    this.name = name;
    this.body = body;
  }

  /**
   * Construct request params.
   * @param call call
   * @param namespace namespace
   * @param name name
   * @param body body
   * @param callParams call params
   */
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

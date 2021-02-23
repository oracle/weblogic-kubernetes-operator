// Copyright (c) 2017, 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.calls;

import oracle.kubernetes.operator.builders.CallParams;
import oracle.kubernetes.operator.logging.LoggingFacade;
import oracle.kubernetes.operator.logging.LoggingFactory;
import oracle.kubernetes.operator.logging.MessageKeys;

public final class RequestParams {
  private static final LoggingFacade LOGGER = LoggingFactory.getLogger("Operator", "Operator");

  public final String call;
  public final String namespace;
  public final String name;
  public final Object body;
  public String domainUid;
  public CallParams callParams;

  /**
   * Construct request params.
   * @param call call
   * @param namespace namespace
   * @param name name
   * @param body body
   */
  private RequestParams(String call, String namespace, String name, Object body) {
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
   * @param domainUid domain unique identifier
   */
  public RequestParams(String call, String namespace, String name, Object body, String domainUid) {
    this(call, namespace, name, body);
    this.domainUid = domainUid;
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
    this(call, namespace, name, body);
    this.callParams = callParams;
  }

  public CallParams getCallParams() {
    return callParams;
  }

  public String toString() {
    return toString(true);
  }

  /**
   * Create readable details of request, optionally including the request body.
   * @param includeBody true if request body should be included
   * @return readable details of the request
   */
  public String toString(boolean includeBody) {
    StringBuilder sb = new StringBuilder();
    sb.append(call);
    if (namespace != null) {
      sb.append(' ');
      sb.append(LOGGER.formatMessage(MessageKeys.REQUEST_PARAMS_IN_NS, namespace));
    }
    if (name != null) {
      sb.append((namespace != null) ? ", " : " ");
      sb.append(LOGGER.formatMessage(MessageKeys.REQUEST_PARAMS_FOR_NAME, name));
    }
    if (includeBody && body != null) {
      sb.append((namespace != null || name != null) ? ", " : " ");
      sb.append(LOGGER.formatMessage(MessageKeys.REQUEST_PARAMS_WITH, LoggingFactory.getJson().serialize(body)));
    }
    return sb.toString();
  }
}

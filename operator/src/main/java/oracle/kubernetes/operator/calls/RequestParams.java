// Copyright (c) 2017, 2023, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.calls;

import java.util.Arrays;
import javax.annotation.Nonnull;

import io.kubernetes.client.openapi.ApiException;
import oracle.kubernetes.common.logging.MessageKeys;
import oracle.kubernetes.operator.builders.CallParams;
import oracle.kubernetes.operator.logging.LoggingFacade;
import oracle.kubernetes.operator.logging.LoggingFactory;
import org.apache.commons.lang3.StringUtils;

public final class RequestParams {
  public static final String[] SUB_RESOURCE_SUFFIXES = {"Status", "Metadata"};
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

  /**
   * Creates a message to describe a Kubernetes-reported failure.
   * @param apiException the exception from Kubernetes
   */
  public String createFailureMessage(ApiException apiException) {
    return LOGGER.formatMessage(
          MessageKeys.K8S_REQUEST_FAILURE,
          getOperationName(),
          getResourceType().toLowerCase(),
          StringUtils.trimToEmpty(this.name),
          StringUtils.trimToEmpty(this.namespace),
          StringUtils.trimToEmpty(apiException.getMessage()));
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

  public String getResourceType() {
    return call.substring(indexOfFirstCapitalInCallName());
  }

  private int indexOfFirstCapitalInCallName() {
    for (int i = 0; i < call.length(); i++) {
      if (Character.isUpperCase(call.charAt(i))) {
        return i;
      }
    }

    throw new RuntimeException(call + " is not a valid call name");
  }

  @Nonnull
  public String getOperationName() {
    return call.substring(0, indexOfFirstCapitalInCallName()) + getCallSuffix();
  }

  @Nonnull
  private String getCallSuffix() {
    return Arrays.stream(SUB_RESOURCE_SUFFIXES).filter(this::isCallSuffix).findFirst().orElse("");
  }

  private boolean isCallSuffix(String suffix) {
    return call.endsWith(suffix);
  }
}

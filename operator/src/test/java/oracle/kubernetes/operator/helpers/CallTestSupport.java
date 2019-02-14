// Copyright 2018, 2019, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helpers;

import com.meterware.simplestub.Memento;
import com.meterware.simplestub.StaticStubSupport;
import io.kubernetes.client.ApiClient;
import io.kubernetes.client.ApiException;
import java.net.HttpURLConnection;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import javax.annotation.Nonnull;
import oracle.kubernetes.operator.calls.CallResponse;
import oracle.kubernetes.operator.calls.RequestParams;
import oracle.kubernetes.operator.calls.SynchronousCallDispatcher;
import oracle.kubernetes.operator.calls.SynchronousCallFactory;

/**
 * Support for writing unit tests that use CallBuilder to send requests that expect responses.
 *
 * <p>The setUp should invoke #installSynchronousCallDispatcher to modify CallBuilder for unit
 * testing, while capturing the memento to clean up during tearDown.
 *
 * <p>The test must define the simulated responses to the calls it will test, by invoking
 * #createCannedResponse, any qualifiers, and the result of the call. For example:
 *
 * <p>testSupport.createCannedResponse("deletePVC") .withNamespace(namespace) .withName(name)
 * .failingWith(HttpURLConnection.HTTP_CONFLICT);
 *
 * <p>will report a conflict failure on an attempt to delete a persistent volume claim with the
 * specified name and namespace.
 *
 * <p>testSupport.createCannedResponse("readCRD") .withName(name) .returning(new
 * V1beta1CustomResourceDefinition());
 *
 * <p>will return the specified custom resource definition.
 */
public class CallTestSupport {

  private Map<CallTestSupport.CannedResponse, Boolean> cannedResponses = new HashMap<>();

  public Memento installSynchronousCallDispatcher() throws NoSuchFieldException {
    return StaticStubSupport.install(CallBuilder.class, "DISPATCHER", new CallDispatcherStub());
  }

  /**
   * Primes CallBuilder to expect a request for the specified method.
   *
   * @param forMethod the name of the method
   * @return a canned response which may be qualified by parameters and defines how CallBuilder
   *     should react.
   */
  public CannedResponse createCannedResponse(String forMethod) {
    CannedResponse cannedResponse = new CannedResponse(forMethod);
    this.cannedResponses.put(cannedResponse, false);
    return cannedResponse;
  }

  /**
   * Primes CallBuilder to expect a possible request for the specified method.
   *
   * @param forMethod the name of the method
   * @return a canned response which may be qualified by parameters and defines how CallBuilder
   *     should react.
   */
  public CannedResponse createOptionalCannedResponse(String forMethod) {
    CannedResponse cannedResponse = new CannedResponse(forMethod).optional();
    this.cannedResponses.put(cannedResponse, false);
    return cannedResponse;
  }

  /** Throws an exception if any of the canned responses were not used. */
  public void verifyAllDefinedResponsesInvoked() {
    List<CannedResponse> unusedResponses = new ArrayList<>();
    for (CannedResponse cannedResponse : this.cannedResponses.keySet())
      if (isUnused(cannedResponse)) unusedResponses.add(cannedResponse);

    if (unusedResponses.isEmpty()) return;

    StringBuilder sb = new StringBuilder("The following expected calls were not made:\n");
    for (CannedResponse cannedResponse : unusedResponses)
      sb.append("  ").append(cannedResponse).append('\n');

    throw new AssertionError(sb.toString());
  }

  private boolean isUnused(CannedResponse cannedResponse) {
    return !cannedResponse.optional && !cannedResponses.get(cannedResponse);
  }

  CannedResponse getMatchingResponse(
      RequestParams requestParams, String fieldSelector, String labelSelector) {
    AdditionalParams params = new AdditionalParams(fieldSelector, labelSelector);
    for (CannedResponse cannedResponse : this.cannedResponses.keySet())
      if (cannedResponse.matches(requestParams, params)) return afterMarking(cannedResponse);

    throw new AssertionError("Unexpected request for " + toString(requestParams, params));
  }

  private CannedResponse afterMarking(CannedResponse cannedResponse) {
    cannedResponse.validate();
    cannedResponses.put(cannedResponse, true);
    return cannedResponse;
  }

  /**
   * A canned response which may be qualified by parameters and defines how CallBuilder should
   * react.
   */
  public static class CannedResponse {
    private static final String NAMESPACE = "namespace";
    private static final String NAME = "name";
    private static final String BODY = "body";
    private static final String LABEL_SELECTOR = "labelSelector";
    private static final String FIELD_SELECTOR = "fieldSelector";
    private static final String MISFORMED_RESPONSE =
        "%s not defined with returning(), computingResult() or failingWithStatus()";
    private static final BodyMatcher WILD_CARD = actualBody -> true;
    private String methodName;
    private Map<String, Object> requestParamExpectations = new HashMap<>();
    private Object result;
    private int status;
    private boolean optional;
    private Function<RequestParams, Object> function;

    CannedResponse(String methodName) {
      this.methodName = methodName;
    }

    private Object getResult(RequestParams requestParams) throws ApiException {
      if (function != null) {
        return function.apply(requestParams);
      }
      if (status > 0) {
        throw new ApiException(status, "");
      }
      return result;
    }

    CallResponse getCallResponse() {
      if (result == null)
        return new CallResponse<>(null, new ApiException(), status, Collections.emptyMap());
      else
        return new CallResponse<>(result, null, HttpURLConnection.HTTP_OK, Collections.emptyMap());
    }

    boolean matches(@Nonnull RequestParams requestParams, AdditionalParams params) {
      return matches(requestParams) && matches(params);
    }

    private boolean matches(RequestParams requestParams) {
      return Objects.equals(requestParams.call, methodName)
          && Objects.equals(requestParams.name, requestParamExpectations.get(NAME))
          && Objects.equals(requestParams.namespace, requestParamExpectations.get(NAMESPACE))
          && matchesBody(requestParams.body, requestParamExpectations.get(BODY));
    }

    private boolean matchesBody(Object actualBody, Object expectedBody) {
      return expectedBody instanceof BodyMatcher && ((BodyMatcher) expectedBody).matches(actualBody)
          || Objects.equals(actualBody, expectedBody)
          || function != null;
    }

    private boolean matches(AdditionalParams params) {
      return Objects.equals(params.fieldSelector, requestParamExpectations.get(FIELD_SELECTOR))
          && Objects.equals(params.labelSelector, requestParamExpectations.get(LABEL_SELECTOR));
    }

    /**
     * Qualifies the canned response to be used only if the namespace matches the value specified
     *
     * @param namespace the expected namespace
     * @return the updated response
     */
    public CannedResponse withNamespace(String namespace) {
      requestParamExpectations.put(NAMESPACE, namespace);
      return this;
    }

    /**
     * Qualifies the canned response to be used only if the name matches the value specified
     *
     * @param name the expected name
     * @return the updated response
     */
    public CannedResponse withName(String name) {
      requestParamExpectations.put(NAME, name);
      return this;
    }

    /**
     * Qualifies the canned response to be used only if the UID matches the value specified
     *
     * @param uid the expected domain uid
     * @return the updated response
     */
    public CannedResponse withUid(String uid) {
      requestParamExpectations.put(NAME, uid);
      return this;
    }

    private CannedResponse optional() {
      optional = true;
      return this;
    }

    /**
     * Qualifies the canned response to be used for any body value
     *
     * @return the updated response
     */
    public CannedResponse ignoringBody() {
      requestParamExpectations.put(BODY, WILD_CARD);
      return this;
    }

    /**
     * Qualifies the canned response to be used only if the body matches the value specified
     *
     * @param body the expected body
     * @return the updated response
     */
    public CannedResponse withBody(Object body) {
      requestParamExpectations.put(BODY, body);
      return this;
    }

    public CannedResponse withLabelSelectors(String... selectors) {
      requestParamExpectations.put(LABEL_SELECTOR, String.join(",", selectors));
      return this;
    }

    public CannedResponse withFieldSelector(String fieldSelector) {
      requestParamExpectations.put(FIELD_SELECTOR, fieldSelector);
      return this;
    }

    /**
     * Specifies a function to compute the result from the request parameters.
     *
     * @param function the specified function
     */
    void computingResult(Function<RequestParams, Object> function) {
      this.function = function;
    }

    /**
     * Specifies the result to be returned by the canned response.
     *
     * @param result the response to return
     */
    public <T> void returning(T result) {
      this.result = result;
    }

    /**
     * Indicates that the canned response should fail and specifies the HTML status to report.
     *
     * @param status the failure status
     */
    public void failingWithStatus(int status) {
      this.status = status;
    }

    @Override
    public String toString() {
      ErrorFormatter formatter = new ErrorFormatter(methodName);
      for (Map.Entry<String, Object> entry : requestParamExpectations.entrySet())
        formatter.addDescriptor(entry.getKey(), entry.getValue());

      return formatter.toString();
    }

    void validate() {
      if (status == 0 && result == null && function == null) {
        throw new IllegalStateException(String.format(MISFORMED_RESPONSE, this));
      }
    }
  }

  private class AdditionalParams {
    private String fieldSelector;
    private String labelSelector;

    AdditionalParams(String fieldSelector, String labelSelector) {
      this.fieldSelector = fieldSelector;
      this.labelSelector = labelSelector;
    }

    String getFieldSelector() {
      return fieldSelector;
    }

    String getLabelSelector() {
      return labelSelector;
    }
  }

  private static String toString(RequestParams requestParams, AdditionalParams additionalParams) {
    return new ErrorFormatter(requestParams.call)
        .addDescriptor("namespace", requestParams.namespace)
        .addDescriptor("name", requestParams.name)
        .addDescriptor("fieldSelector", additionalParams.getFieldSelector())
        .addDescriptor("labelSelector", additionalParams.getLabelSelector())
        .addDescriptor("body", requestParams.body)
        .toString();
  }

  private static class ErrorFormatter {
    private String call;
    private List<String> descriptors = new ArrayList<>();

    ErrorFormatter(String call) {
      this.call = call;
    }

    ErrorFormatter addDescriptor(String type, Object value) {
      if (isDefined(value)) descriptors.add(String.format("%s '%s'", type, value));
      return this;
    }

    private boolean isDefined(Object value) {
      return !isEmptyString(value);
    }

    private boolean isEmptyString(Object value) {
      return value instanceof String && isEmpty((String) value);
    }

    private boolean isEmpty(String value) {
      return value.trim().length() == 0;
    }

    public String toString() {
      StringBuilder sb = new StringBuilder(call);
      if (!descriptors.isEmpty()) {
        sb.append(" with ").append(descriptors.get(0));
        for (int i = 1; i < descriptors.size() - 1; i++) sb.append(", ").append(descriptors.get(i));
        if (descriptors.size() > 1)
          sb.append(" and ").append(descriptors.get(descriptors.size() - 1));
      }
      return sb.toString();
    }
  }

  private class CallDispatcherStub implements SynchronousCallDispatcher {
    @SuppressWarnings("unchecked")
    @Override
    public <T> T execute(
        SynchronousCallFactory<T> factory, RequestParams requestParams, Pool<ApiClient> helper)
        throws ApiException {
      return (T) getMatchingResponse(requestParams, null, null).getResult(requestParams);
    }
  }
}

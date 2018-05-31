// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.work;

import static oracle.kubernetes.operator.Workarounds.INTORSTRING_BAD_EQUALS;
import static oracle.kubernetes.operator.calls.AsyncRequestStep.RESPONSE_COMPONENT_NAME;

import com.meterware.simplestub.Memento;
import com.meterware.simplestub.StaticStubSupport;
import io.kubernetes.client.ApiException;
import io.kubernetes.client.models.V1beta1Ingress;
import java.net.HttpURLConnection;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import javax.annotation.Nonnull;
import oracle.kubernetes.operator.calls.CallFactory;
import oracle.kubernetes.operator.calls.CallResponse;
import oracle.kubernetes.operator.calls.RequestParams;
import oracle.kubernetes.operator.helpers.AsyncRequestStepFactory;
import oracle.kubernetes.operator.helpers.CallBuilder;
import oracle.kubernetes.operator.helpers.ClientPool;
import oracle.kubernetes.operator.helpers.ResponseStep;
import oracle.kubernetes.operator.utils.YamlUtils;

/**
 * Support for writing unit tests that use CallBuilder to send requests that expect asynchronous
 * responses.
 *
 * <p>The setUp should invoke #installRequestStepFactory to modify CallBuilder for unit testing,
 * while capturing the memento to clean up during tearDown.
 *
 * <p>The test must define the simulated responses to the calls it will test, by invoking
 * #createCannedResponse, any qualifiers, and the result of the call. For example:
 *
 * <p>testSupport.createCannedResponse("deleteIngress") .withNamespace(namespace).withName(name)
 * .failingWith(HttpURLConnection.HTTP_CONFLICT);
 *
 * <p>will report a conflict failure on an attempt to delete an Ingress with the specified name and
 * namespace.
 *
 * <p>testSupport.createCannedResponse("listPod") .withNamespace(namespace) .returning(new
 * V1PodList().items(Arrays.asList(pod1, pod2, pod3);
 *
 * <p>will return a list of pods after a query with the specified namespace.
 */
@SuppressWarnings("unused")
public class AsyncCallTestSupport extends FiberTestSupport {

  /**
   * Installs a factory into CallBuilder to use canned responses.
   *
   * @return a memento which can be used to restore the production factory
   */
  public Memento installRequestStepFactory() throws NoSuchFieldException {
    return StaticStubSupport.install(CallBuilder.class, "STEP_FACTORY", new RequestStepFactory());
  }

  private class RequestStepFactory implements AsyncRequestStepFactory {

    @Override
    public <T> Step createRequestAsync(
        ResponseStep<T> next,
        RequestParams requestParams,
        CallFactory<T> factory,
        ClientPool helper,
        int timeoutSeconds,
        int maxRetryCount,
        String fieldSelector,
        String labelSelector,
        String resourceVersion) {
      return new CannedResponseStep<>(
          next, getMatchingResponse(requestParams, additionalParams(fieldSelector, labelSelector)));
    }

    private AdditionalParams additionalParams(String fieldSelector, String labelSelector) {
      return new AdditionalParams(fieldSelector, labelSelector);
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

  private Map<CannedResponse, Boolean> cannedResponses = new HashMap<>();

  /**
   * Primes CallBuilder to expect a request for the specified method.
   *
   * @param forMethod the name of the method
   * @return a canned response which may be qualified by parameters and defines how CallBuilder
   *     should react.
   */
  public CannedResponse createCannedResponse(String forMethod) {
    CannedResponse cannedResponse = new CannedResponse(forMethod);
    cannedResponses.put(cannedResponse, false);
    return cannedResponse;
  }

  @SuppressWarnings({"unchecked", "SameParameterValue"})
  private <T> CannedResponse<T> getMatchingResponse(
      RequestParams requestParams, AdditionalParams params) {
    for (CannedResponse cannedResponse : cannedResponses.keySet())
      if (cannedResponse.matches(requestParams, params)) return afterMarking(cannedResponse);

    throw new AssertionError("Unexpected request for " + toString(requestParams, params));
  }

  private CannedResponse afterMarking(CannedResponse cannedResponse) {
    cannedResponse.validate();
    cannedResponses.put(cannedResponse, true);
    return cannedResponse;
  }

  private String toString(RequestParams requestParams, AdditionalParams additionalParams) {
    ErrorFormatter formatter = new ErrorFormatter(requestParams.call);
    formatter.addDescriptor("namespace", requestParams.namespace);
    formatter.addDescriptor("name", requestParams.name);
    formatter.addDescriptor("fieldSelector", additionalParams.fieldSelector);
    formatter.addDescriptor("labelSelector", additionalParams.labelSelector);
    formatter.addDescriptor("body", requestParams.body);
    return formatter.toString();
  }

  /** Throws an exception if any of the canned responses were not used. */
  public void verifyAllDefinedResponsesInvoked() {
    List<CannedResponse> unusedResponses = new ArrayList<>();
    for (CannedResponse cannedResponse : cannedResponses.keySet())
      if (!cannedResponses.get(cannedResponse)) unusedResponses.add(cannedResponse);

    if (unusedResponses.isEmpty()) return;

    StringBuilder sb =
        new StringBuilder("The following expected calls were not made:").append('\n');
    for (CannedResponse cannedResponse : unusedResponses)
      sb.append("  ").append(cannedResponse).append('\n');
    throw new AssertionError(sb.toString());
  }

  private static class ErrorFormatter {
    private String call;
    private List<String> descriptors = new ArrayList<>();

    ErrorFormatter(String call) {
      this.call = call;
    }

    void addDescriptor(String type, Object value) {
      if (isDefined(value)) descriptors.add(String.format("%s '%s'", type, value));
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

  /**
   * A canned response which may be qualified by parameters and defines how CallBuilder should
   * react.
   *
   * @param <T> the type of value to be returned in the step, if it succeeds
   */
  public static class CannedResponse<T> {
    private static final String NAMESPACE = "namespace";
    private static final String NAME = "name";
    private static final String BODY = "body";
    private static final String LABEL_SELECTOR = "labelSelector";
    private static final String FIELD_SELECTOR = "fieldSelector";
    private static final String MISFORMED_RESPONSE =
        "%s not defined with returning() or failingWithStatus()";
    protected static final Object WILD_CARD = new Object();
    private String methodName;
    private Map<String, Object> requestParamExpectations = new HashMap<>();
    private T result;
    private int status;

    private CannedResponse(String methodName) {
      this.methodName = methodName;
    }

    private CallResponse<T> getCallResponse() {
      if (result == null)
        return new CallResponse<>(null, new ApiException(), status, Collections.emptyMap());
      else
        return new CallResponse<>(result, null, HttpURLConnection.HTTP_OK, Collections.emptyMap());
    }

    private boolean matches(@Nonnull RequestParams requestParams, AdditionalParams params) {
      return matches(requestParams) && matches(params);
    }

    private boolean matches(RequestParams requestParams) {
      return Objects.equals(requestParams.call, methodName)
          && Objects.equals(requestParams.name, requestParamExpectations.get(NAME))
          && Objects.equals(requestParams.namespace, requestParamExpectations.get(NAMESPACE))
          && matchesBody(requestParams);
    }

    private boolean matchesBody(RequestParams requestParams) {
      return requestParamExpectations.get(BODY) == WILD_CARD
          || equalBodies(requestParams.body, requestParamExpectations.get(BODY));
    }

    // This is a hack to get around a bug in the 1.0 K8s client code:
    //    the IntOrString class does not define equals(), meaning that any classes which depend on
    // it
    //    require special handling.
    private static boolean equalBodies(Object actual, Object expected) {
      return useYamlComparison(actual)
          ? yamlEquals(actual, expected)
          : Objects.equals(actual, expected);
    }

    private static boolean useYamlComparison(Object actual) {
      return INTORSTRING_BAD_EQUALS && actual instanceof V1beta1Ingress;
    }

    private static boolean yamlEquals(Object actual, Object expected) {
      return Objects.equals(objectToYaml(actual), objectToYaml(expected));
    }

    private static String objectToYaml(Object object) {
      return YamlUtils.newYaml().dump(object);
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

    public CannedResponse<T> withLabelSelectors(String... selectors) {
      requestParamExpectations.put(LABEL_SELECTOR, String.join(",", selectors));
      return this;
    }

    public CannedResponse<T> withFieldSelector(String fieldSelector) {
      requestParamExpectations.put(FIELD_SELECTOR, fieldSelector);
      return this;
    }

    /**
     * Specifies the result to be returned by the canned response.
     *
     * @param result the response to return
     */
    public void returning(T result) {
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
      if (status == 0 && result == null) {
        throw new IllegalStateException(String.format(MISFORMED_RESPONSE, this));
      }
    }
  }

  private static class CannedResponseStep<T> extends Step {
    private CannedResponse<T> cannedResponse;

    CannedResponseStep(Step next, CannedResponse<T> cannedResponse) {
      super(next);
      this.cannedResponse = cannedResponse;
    }

    @Override
    public NextAction apply(Packet packet) {
      CannedResponse<T> cannedResponse = this.cannedResponse;
      CallResponse<T> callResponse = cannedResponse.getCallResponse();
      packet.getComponents().put(RESPONSE_COMPONENT_NAME, Component.createFor(callResponse));

      return doNext(packet);
    }
  }

  private static class SuccessStep<T> extends Step {
    private final T result;

    SuccessStep(T result, Step next) {
      super(next);
      this.result = result;
    }

    @Override
    public NextAction apply(Packet packet) {
      packet
          .getComponents()
          .put(
              RESPONSE_COMPONENT_NAME,
              Component.createFor(
                  new CallResponse<>(
                      result, null, HttpURLConnection.HTTP_OK, Collections.emptyMap())));

      return doNext(packet);
    }
  }

  private static class FailureStep extends Step {
    private final int status;

    FailureStep(int status, Step next) {
      super(next);
      this.status = status;
    }

    @SuppressWarnings("unchecked")
    @Override
    public NextAction apply(Packet packet) {
      packet
          .getComponents()
          .put(
              RESPONSE_COMPONENT_NAME,
              Component.createFor(
                  new CallResponse(null, new ApiException(), status, Collections.emptyMap())));

      return doNext(packet);
    }
  }
}

// Copyright (c) 2024, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.calls;

import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import javax.annotation.Nonnull;

import io.kubernetes.client.common.KubernetesType;
import io.kubernetes.client.extended.controller.reconciler.Result;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.models.V1Status;
import io.kubernetes.client.util.generic.KubernetesApiResponse;
import oracle.kubernetes.common.logging.MessageKeys;
import oracle.kubernetes.operator.helpers.DomainPresenceInfo;
import oracle.kubernetes.operator.logging.LoggingFacade;
import oracle.kubernetes.operator.logging.LoggingFactory;
import oracle.kubernetes.operator.tuning.TuningParameters;
import oracle.kubernetes.operator.work.Packet;
import oracle.kubernetes.operator.work.Step;
import oracle.kubernetes.weblogic.domain.model.DomainCondition;
import oracle.kubernetes.weblogic.domain.model.DomainFailureReason;
import oracle.kubernetes.weblogic.domain.model.DomainResource;
import org.apache.commons.lang3.StringUtils;

import static oracle.kubernetes.operator.KubernetesConstants.HTTP_BAD_METHOD;
import static oracle.kubernetes.operator.KubernetesConstants.HTTP_BAD_REQUEST;
import static oracle.kubernetes.operator.KubernetesConstants.HTTP_CONFLICT;
import static oracle.kubernetes.operator.KubernetesConstants.HTTP_FORBIDDEN;
import static oracle.kubernetes.operator.KubernetesConstants.HTTP_GATEWAY_TIMEOUT;
import static oracle.kubernetes.operator.KubernetesConstants.HTTP_GONE;
import static oracle.kubernetes.operator.KubernetesConstants.HTTP_INTERNAL_ERROR;
import static oracle.kubernetes.operator.KubernetesConstants.HTTP_NOT_FOUND;
import static oracle.kubernetes.operator.KubernetesConstants.HTTP_TOO_MANY_REQUESTS;
import static oracle.kubernetes.operator.KubernetesConstants.HTTP_UNAUTHORIZED;
import static oracle.kubernetes.operator.KubernetesConstants.HTTP_UNAVAILABLE;
import static oracle.kubernetes.operator.KubernetesConstants.HTTP_UNPROCESSABLE_ENTITY;
import static oracle.kubernetes.operator.calls.RequestStep.CONTINUE;
import static oracle.kubernetes.operator.calls.RequestStep.FIBER_TIMEOUT;
import static oracle.kubernetes.operator.calls.RequestStep.RESPONSE_COMPONENT_NAME;
import static oracle.kubernetes.operator.calls.RequestStep.accessContinue;
import static oracle.kubernetes.weblogic.domain.model.DomainConditionType.FAILED;
import static oracle.kubernetes.weblogic.domain.model.DomainFailureReason.KUBERNETES;
import static oracle.kubernetes.weblogic.domain.model.DomainFailureReason.KUBERNETES_NETWORK_EXCEPTION;

/**
 * Step to receive response of Kubernetes API server call.
 *
 * <p>Most implementations will only need to implement {@link #onSuccess(Packet, KubernetesApiResponse)}.
 *
 * @param <T> Response type
 */
public abstract class ResponseStep<T extends KubernetesType> extends Step {
  private static final LoggingFacade LOGGER = LoggingFactory.getLogger("Operator", "Operator");

  private static final RetryStrategyFactory DEFAULT_RETRY_STRATEGY_FACTORY =
          (maxRetryCount, retryStep) -> new DefaultRetryStrategy(maxRetryCount, retryStep);

  @SuppressWarnings({"FieldMayBeFinal", "CanBeFinal"})
  private static RetryStrategyFactory retryStrategyFactory = DEFAULT_RETRY_STRATEGY_FACTORY;

  public static final String RETRY = "retry";
  private static final Random R = new Random();
  private static final int HIGH = 200;
  private static final int LOW = 10;
  private static final int SCALE = 100;
  private static final int MAX = 10000;

  private static final Set<Integer> UNRECOVERABLE_ERROR_CODES = Set.of(
      HTTP_BAD_REQUEST, HTTP_UNAUTHORIZED, HTTP_FORBIDDEN, HTTP_NOT_FOUND,
      HTTP_BAD_METHOD, HTTP_GONE, HTTP_UNPROCESSABLE_ENTITY, HTTP_INTERNAL_ERROR);

  public static boolean isUnrecoverable(KubernetesApiResponse<?> r) {
    return UNRECOVERABLE_ERROR_CODES.contains(r.getHttpStatusCode());
  }

  public static boolean isNotFound(KubernetesApiResponse<?> r) {
    int code = r.getHttpStatusCode();
    return code == HTTP_NOT_FOUND || code == HTTP_GONE;
  }

  public static boolean hasConflict(KubernetesApiResponse<?> r) {
    return r.getHttpStatusCode() == HTTP_CONFLICT;
  }

  public static boolean isForbidden(KubernetesApiResponse<?> r) {
    return r.getHttpStatusCode() == HTTP_FORBIDDEN;
  }

  public static boolean isNotAuthorizedOrForbidden(KubernetesApiResponse<?> r) {
    return r.getHttpStatusCode() == HTTP_UNAUTHORIZED || r.getHttpStatusCode() == HTTP_FORBIDDEN;
  }

  private final Step conflictStep;
  private RequestStep previousStep = null;

  /** Constructor specifying no next step. */
  protected ResponseStep() {
    this(null);
  }

  /**
   * Constructor specifying next step.
   *
   * @param nextStep Next step
   */
  protected ResponseStep(Step nextStep) {
    this(null, nextStep);
  }

  /**
   * Constructor specifying conflict and next step.
   *
   * @param conflictStep Conflict step
   * @param nextStep Next step
   */
  protected ResponseStep(Step conflictStep, Step nextStep) {
    super(new AfterStep(nextStep));
    this.conflictStep = conflictStep;
  }

  private static class AfterStep extends Step {

    public AfterStep(Step next) {
      super(next);
    }

    @Override
    public @Nonnull Result apply(Packet packet) {
      packet.remove(CONTINUE);
      packet.remove(RequestStep.RESPONSE_COMPONENT_NAME);
      packet.remove(RETRY);
      return doNext(packet);
    }
  }

  public final void setPrevious(RequestStep previousStep) {
    this.previousStep = previousStep;
  }

  @Override
  public final @Nonnull Result apply(Packet packet) {
    @SuppressWarnings("unchecked")
    KubernetesApiResponse<T> response = (KubernetesApiResponse<T>) packet.get(RESPONSE_COMPONENT_NAME);
    if (response == null || !response.isSuccess()) {
      return onFailure(packet, response);
    } else {
      return onSuccess(packet, response);
    }
  }

  /**
   * Returns next action that can be used to get the next batch of results from a list search that
   * specified a "continue" value, if any; otherwise, returns next.
   *
   * @param callResponse Call response
   * @param packet Packet
   * @return Next action for list continue
   */
  protected final Result doContinueListOrNext(KubernetesApiResponse<T> callResponse, Packet packet) {
    return doContinueListOrNext(callResponse, packet, getNext());
  }

  /**
   * Returns next action that can be used to get the next batch of results from a list search that
   * specified a "continue" value, if any; otherwise, returns next.
   *
   * @param callResponse Call response
   * @param packet Packet
   * @param next Next step, if no continuation
   * @return Next action for list continue
   */
  protected final Result doContinueListOrNext(KubernetesApiResponse<T> callResponse, Packet packet, Step next) {
    String cont = accessContinue(callResponse.getObject());
    if (cont != null) {
      packet.put(CONTINUE, cont);
      // Since the continue value is present, invoking the original request will return
      // the next window of data.
      return resetRetryStrategyAndReinvokeRequest(packet);
    }
    return doNext(next, packet);
  }

  private void addDomainFailureStatus(Packet packet, V1Status status) {
    DomainPresenceInfo.fromPacket(packet)
        .map(DomainPresenceInfo::getDomain)
        .ifPresent(domain -> updateFailureStatus(domain, status));
  }

  private void updateFailureStatus(
      @Nonnull DomainResource domain, V1Status status) {
    DomainFailureReason reason = KUBERNETES;
    if (status != null) {
      LOGGER.fine("updateFailureStatus: " + status);
      if (Integer.valueOf(HTTP_UNAVAILABLE).equals(status.getCode())) {
        reason = KUBERNETES_NETWORK_EXCEPTION;
      }
    }
    DomainCondition condition = new DomainCondition(FAILED).withReason(reason)
        .withMessage(status.toString());
    addFailureStatus(domain, condition);
  }

  private void addFailureStatus(@Nonnull DomainResource domain, DomainCondition condition) {
    domain.getOrCreateStatus().addCondition(condition);
  }

  /**
   * Resets any retry strategy, such as a failed retry count and invokes the request again. This
   * will be useful for patterns such as list requests that include a "continue" value.
   * @param packet Packet
   * @return Next action for the original request
   */
  private Result resetRetryStrategyAndReinvokeRequest(Packet packet) {
    @SuppressWarnings("unchecked")
    RetryStrategy retryStrategy = (RetryStrategy) packet.get(RETRY);
    if (retryStrategy != null) {
      retryStrategy.reset();
    }
    return doNext(previousStep, packet);
  }

  /**
   * Callback for API server call failure. The ApiException, HTTP status code and response headers
   * are provided in callResponse; however, these will be null or 0 when the client timed-out.
   *
   * @param packet Packet
   * @param callResponse the result of the call
   * @return Next action for fiber processing, which may be a retry
   */
  public Result onFailure(Packet packet, KubernetesApiResponse<T> callResponse) {
    return onFailure(conflictStep, packet, callResponse);
  }

  /**
   * Callback for API server call failure. The ApiException and HTTP status code and response
   * headers are provided; however, these will be null or 0 when the client simply timed-out.
   *
   * <p>The default implementation tests if the request could be retried and, if not, ends fiber
   * processing.
   *
   * @param conflict Conflict step
   * @param packet Packet
   * @param callResponse the result of the call
   * @return Next action for fiber processing, which may be a retry
   */
  public Result onFailure(Step conflict, Packet packet, KubernetesApiResponse<T> callResponse) {
    RetryStrategy retryStrategy = getOrCreateRetryStrategy(packet);
    if (retryStrategy != null) {
      Result result = retryStrategy.doPotentialRetry(conflict, packet, callResponse);
      if (result != null) {
        return result;
      }
    }
    return onFailureNoRetry(packet, callResponse);
  }

  private RetryStrategy getOrCreateRetryStrategy(Packet packet) {
    return (RetryStrategy) packet.computeIfAbsent(
            RETRY, s -> create(retryStrategyFactory,
                    TuningParameters.getInstance().getCallBuilderTuning().getCallMaxRetryCount(), previousStep));
  }

  private RetryStrategy create(RetryStrategyFactory factory, int maxRetryCount, Step retry) {
    RetryStrategy strategy = factory.create(maxRetryCount, retry);
    if (strategy == null) {
      strategy = new DefaultRetryStrategy(maxRetryCount, retry);
    }
    return strategy;
  }

  /**
   * Creates a message to describe a Kubernetes-reported failure.
   * @param callResponse the call response
   */
  protected String createFailureMessage(KubernetesApiResponse<T> callResponse) {
    String message = StringUtils.trimToEmpty(
            Optional.ofNullable(callResponse.getStatus()).map(V1Status::getMessage).orElse(null));
    if (previousStep == null) {
      return message;
    }
    return LOGGER.formatMessage(
            MessageKeys.K8S_REQUEST_FAILURE,
            previousStep.getOperationName(),
            previousStep.getResourceSingular(),
            StringUtils.trimToEmpty(previousStep.getName()),
            StringUtils.trimToEmpty(previousStep.getNamespace()),
            message);
  }

  protected Result onFailureNoRetry(Packet packet, KubernetesApiResponse<T> callResponse) {
    addDomainFailureStatus(packet, callResponse.getStatus());
    try {
      callResponse.throwsApiException();
    } catch (ApiException ae) {
      return doTerminate(ae, packet);
    }
    return doEnd(packet);
  }

  /**
   * Callback for API server call success.
   *
   * @param packet Packet
   * @param callResponse the result of the call
   * @return Next action for fiber processing
   */
  public Result onSuccess(Packet packet, KubernetesApiResponse<T> callResponse) {
    throw new IllegalStateException("Must be overridden, if called");
  }

  private static final class DefaultRetryStrategy implements RetryStrategy {
    private long retryCount = 0;
    private final int maxRetryCount;
    private final Step retryStep;

    DefaultRetryStrategy(int maxRetryCount, Step retryStep) {
      this.maxRetryCount = maxRetryCount;
      this.retryStep = retryStep;
    }

    public Result doPotentialRetry(Step conflictStep, Packet packet, KubernetesApiResponse<?> callResponse) {
      int statusCode = Optional.ofNullable(callResponse)
          .map(KubernetesApiResponse::getHttpStatusCode).orElse(FIBER_TIMEOUT);
      if (mayRetryOnStatusValue(statusCode)) {
        return retriesLeft() ? backOffAndRetry(packet, retryStep) : null;
      } else if (isRestartableConflict(conflictStep, statusCode)) {
        return backOffAndRetry(packet, conflictStep);
      }
      return null;
    }

    public void reset() {
      this.retryCount = 0;
    }

    // Check statusCode, many statuses should not be retried
    // https://github.com/kubernetes/community/blob/master/contributors/devel/sig-architecture/api-conventions.md#http-status-codes
    private boolean mayRetryOnStatusValue(int statusCode) {
      return statusCode == FIBER_TIMEOUT
          || statusCode == HTTP_TOO_MANY_REQUESTS
          || statusCode == HTTP_INTERNAL_ERROR
          || statusCode == HTTP_UNAVAILABLE
          || statusCode == HTTP_GATEWAY_TIMEOUT;
    }

    @Nonnull
    private Result backOffAndRetry(Packet packet, Step nextStep) {
      final long waitTime = getNextWaitTime();

      return doDelay(nextStep, packet, waitTime, TimeUnit.MILLISECONDS);
    }

    // Compute wait time, increasing exponentially
    private int getNextWaitTime() {
      return Math.min((2 << ++retryCount) * SCALE, MAX) + (R.nextInt(HIGH - LOW) + LOW);
    }

    // Conflict is an optimistic locking failure.  Therefore, we can't
    // simply retry the request.  Instead, application code needs to rebuild
    // the request based on latest contents.  If provided, a conflict step will do that.
    private boolean isRestartableConflict(Step conflictStep, int statusCode) {
      return statusCode == HTTP_CONFLICT && conflictStep != null;
    }

    private boolean retriesLeft() {
      return (retryCount + 1) <= maxRetryCount;
    }
  }
}

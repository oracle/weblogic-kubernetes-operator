// Copyright (c) 2017, 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helpers;

import java.io.IOException;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Callable;
import javax.annotation.Nonnull;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.reflect.TypeToken;
import io.kubernetes.client.custom.V1Patch;
import io.kubernetes.client.openapi.ApiCallback;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.Pair;
import io.kubernetes.client.openapi.apis.AdmissionregistrationV1Api;
import io.kubernetes.client.openapi.apis.ApiextensionsV1Api;
import io.kubernetes.client.openapi.apis.AuthenticationV1Api;
import io.kubernetes.client.openapi.apis.AuthorizationV1Api;
import io.kubernetes.client.openapi.apis.BatchV1Api;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.apis.CustomObjectsApi;
import io.kubernetes.client.openapi.apis.PolicyV1Api;
import io.kubernetes.client.openapi.apis.VersionApi;
import io.kubernetes.client.openapi.models.CoreV1Event;
import io.kubernetes.client.openapi.models.CoreV1EventList;
import io.kubernetes.client.openapi.models.V1ConfigMap;
import io.kubernetes.client.openapi.models.V1ConfigMapList;
import io.kubernetes.client.openapi.models.V1CustomResourceDefinition;
import io.kubernetes.client.openapi.models.V1DeleteOptions;
import io.kubernetes.client.openapi.models.V1Job;
import io.kubernetes.client.openapi.models.V1JobList;
import io.kubernetes.client.openapi.models.V1NamespaceList;
import io.kubernetes.client.openapi.models.V1Pod;
import io.kubernetes.client.openapi.models.V1PodDisruptionBudget;
import io.kubernetes.client.openapi.models.V1PodDisruptionBudgetList;
import io.kubernetes.client.openapi.models.V1PodList;
import io.kubernetes.client.openapi.models.V1Secret;
import io.kubernetes.client.openapi.models.V1SecretList;
import io.kubernetes.client.openapi.models.V1SelfSubjectAccessReview;
import io.kubernetes.client.openapi.models.V1SelfSubjectRulesReview;
import io.kubernetes.client.openapi.models.V1Service;
import io.kubernetes.client.openapi.models.V1ServiceList;
import io.kubernetes.client.openapi.models.V1Status;
import io.kubernetes.client.openapi.models.V1SubjectAccessReview;
import io.kubernetes.client.openapi.models.V1TokenReview;
import io.kubernetes.client.openapi.models.V1ValidatingWebhookConfiguration;
import io.kubernetes.client.openapi.models.V1ValidatingWebhookConfigurationList;
import io.kubernetes.client.openapi.models.VersionInfo;
import io.kubernetes.client.util.ClientBuilder;
import io.kubernetes.client.util.credentials.AccessTokenAuthentication;
import okhttp3.Call;
import oracle.kubernetes.common.logging.MessageKeys;
import oracle.kubernetes.operator.KubernetesConstants;
import oracle.kubernetes.operator.builders.CallParamsImpl;
import oracle.kubernetes.operator.calls.AsyncRequestStep;
import oracle.kubernetes.operator.calls.CallFactory;
import oracle.kubernetes.operator.calls.CallWrapper;
import oracle.kubernetes.operator.calls.CancellableCall;
import oracle.kubernetes.operator.calls.RequestParams;
import oracle.kubernetes.operator.calls.RetryStrategy;
import oracle.kubernetes.operator.calls.SynchronousCallDispatcher;
import oracle.kubernetes.operator.calls.SynchronousCallFactory;
import oracle.kubernetes.operator.logging.LoggingFacade;
import oracle.kubernetes.operator.logging.LoggingFactory;
import oracle.kubernetes.operator.tuning.CallBuilderTuning;
import oracle.kubernetes.operator.tuning.TuningParameters;
import oracle.kubernetes.operator.work.Step;
import oracle.kubernetes.weblogic.domain.api.WeblogicApi;
import oracle.kubernetes.weblogic.domain.model.Cluster;
import oracle.kubernetes.weblogic.domain.model.ClusterList;
import oracle.kubernetes.weblogic.domain.model.Domain;
import oracle.kubernetes.weblogic.domain.model.DomainList;

import static oracle.kubernetes.operator.KubernetesConstants.HTTP_NOT_FOUND;
import static oracle.kubernetes.operator.helpers.KubernetesUtils.getDomainUidLabel;
import static oracle.kubernetes.utils.OperatorUtils.isNullOrEmpty;

/** Simplifies synchronous and asynchronous call patterns to the Kubernetes API Server. */
@SuppressWarnings({"WeakerAccess", "UnusedReturnValue"})
public class CallBuilder {
  private static final LoggingFacade LOGGER = LoggingFactory.getLogger("Operator", "Operator");

  private static final String RESOURCE_VERSION_MATCH_UNSET = null;
  private String container;

  private static final SynchronousCallDispatcher DEFAULT_DISPATCHER =
      new SynchronousCallDispatcher() {
        @Override
        public <T> T execute(
            SynchronousCallFactory<T> factory, RequestParams params, Pool<ApiClient> pool)
            throws ApiException {
          ApiClient client = pool.take();
          try {
            return factory.execute(client, params);
          } finally {
            pool.recycle(client);
          }
        }
      };

  private static SynchronousCallDispatcher dispatcher = DEFAULT_DISPATCHER;
  private static final AsyncRequestStepFactory DEFAULT_STEP_FACTORY = AsyncRequestStep::new;
  private static AsyncRequestStepFactory stepFactory = DEFAULT_STEP_FACTORY;
  private ClientPool helper;
  private final Boolean allowWatchBookmarks = false;
  private final String dryRun = null;
  private final String pretty = null;
  private final CallFactory<Domain> replaceDomain =
      (requestParams, usage, cont, callback) ->
          wrap(
              replaceDomainAsync(
                  usage,
                  requestParams.name,
                  requestParams.namespace,
                  (Domain) requestParams.body,
                  callback));
  private final CallFactory<Domain> patchDomain =
      (requestParams, usage, cont, callback) ->
          wrap(
              patchDomainAsync(
                  usage,
                  requestParams.name,
                  requestParams.namespace,
                  (V1Patch) requestParams.body,
                  callback));
  private final CallFactory<Domain> replaceDomainStatus =
      (requestParams, usage, cont, callback) ->
          wrap(
              replaceDomainStatusAsync(
                  usage,
                  requestParams.name,
                  requestParams.namespace,
                  (Domain) requestParams.body,
                  callback));
  private final CallFactory<V1CustomResourceDefinition> createCrd =
      (requestParams, usage, cont, callback) ->
          wrap(
              createCustomResourceDefinitionAsync(
                  usage, (V1CustomResourceDefinition) requestParams.body, callback));
  private final CallFactory<V1CustomResourceDefinition> replaceCrd =
      (requestParams, usage, cont, callback) ->
          wrap(
              replaceCustomResourceDefinitionAsync(
                  usage,
                  requestParams.name,
                  (V1CustomResourceDefinition) requestParams.body,
                  callback));
  private final CallFactory<V1ConfigMap> createConfigmap =
      (requestParams, usage, cont, callback) ->
          wrap(
              createConfigMapAsync(
                  usage, requestParams.namespace, (V1ConfigMap) requestParams.body, callback));
  private final CallFactory<V1Secret> createSecret =
          (requestParams, usage, cont, callback) ->
                  wrap(
                          createSecretAsync(
                                  usage, requestParams.namespace, (V1Secret) requestParams.body, callback));
  private final CallFactory<V1ConfigMap> replaceConfigmap =
      (requestParams, usage, cont, callback) ->
          wrap(
              replaceConfigMapAsync(
                  usage,
                  requestParams.name,
                  requestParams.namespace,
                  (V1ConfigMap) requestParams.body,
                  callback));
  private final CallFactory<V1ConfigMap> patchConfigMap =
      (requestParams, usage, cont, callback) ->
          wrap(
              patchConfigMapAsync(
                  usage,
                  requestParams.name,
                  requestParams.namespace,
                  (V1Patch) requestParams.body,
                  callback));
  private final CallFactory<V1Secret> replaceSecret =
          (requestParams, usage, cont, callback) ->
                  wrap(
                          replaceSecretAsync(
                                  usage,
                                  requestParams.name,
                                  requestParams.namespace,
                                  (V1Secret) requestParams.body,
                                  callback));
  private final CallFactory<V1Pod> createPod =
      (requestParams, usage, cont, callback) ->
          wrap(
              createPodAsync(usage, requestParams.namespace, (V1Pod) requestParams.body, callback));
  private final CallFactory<V1Pod> patchPod =
      (requestParams, usage, cont, callback) ->
          wrap(
              patchPodAsync(
                  usage,
                  requestParams.name,
                  requestParams.namespace,
                  (V1Patch) requestParams.body,
                  callback));
  private final CallFactory<V1Job> createJob =
      (requestParams, usage, cont, callback) ->
          wrap(
              createJobAsync(usage, requestParams.namespace, (V1Job) requestParams.body, callback));
  private final CallFactory<V1Service> createService =
      (requestParams, usage, cont, callback) ->
          wrap(
              createServiceAsync(
                  usage, requestParams.namespace, (V1Service) requestParams.body, callback));
  private final CallFactory<CoreV1Event> readEvent =
      (requestParams, usage, cont, callback) ->
          wrap(readEventAsync(usage, requestParams.name, requestParams.namespace, callback));
  private final CallFactory<CoreV1Event> createEvent =
      (requestParams, usage, cont, callback) ->
          wrap(
              createEventAsync(
                  usage, requestParams.namespace, (CoreV1Event) requestParams.body, callback));
  private final CallFactory<CoreV1Event> replaceEvent =
      (requestParams, usage, cont, callback) ->
          wrap(
              replaceEventAsync(
                  usage,
                  requestParams.name,
                  requestParams.namespace,
                  (CoreV1Event) requestParams.body,
                  callback));
  private final CallFactory<String> readPodLog =
      (requestParams, usage, cont, callback) ->
          wrap(
              readPodLogAsync(
                  usage,
                  requestParams.name,
                  requestParams.namespace,
                  container,
                  null,
                  null,
                  null,
                  pretty,
                  null,
                  null,
                  null,
                  null,
                  callback));
  private final CallFactory<V1PodDisruptionBudgetList> listPodDisruptionBudget =
      (requestParams, usage, cont, callback) ->
          wrap(listPodDisruptionBudgetAsync(usage, requestParams.namespace, cont, callback));
  private final CallFactory<V1PodDisruptionBudget> readPodDisruptionBudget =
      (requestParams, usage, cont, callback) ->
          wrap(readPodDisruptionBudgetAsync(usage, requestParams.name, requestParams.namespace, callback));
  private final CallFactory<V1PodDisruptionBudget> createPodDisruptionBudget =
      (requestParams, usage, cont, callback) ->
          wrap(
              createPodDisruptionBudgetAsync(
                  usage, requestParams.namespace, (V1PodDisruptionBudget)
                      requestParams.body, callback));
  private final CallFactory<V1PodDisruptionBudget> patchPodDisruptionBudget =
      (requestParams, usage, cont, callback) ->
          wrap(
              patchPodDisruptionBudgetAsync(
                  usage,
                  requestParams.name,
                  requestParams.namespace,
                  (V1Patch) requestParams.body,
                  callback));
  private final CallFactory<V1Status> deletePodDisruptionBudget =
      (requestParams, usage, cont, callback) ->
          wrap(
              deletePodDisruptionBudgetAsync(
                  usage,
                  requestParams.name,
                  requestParams.namespace,
                  (V1DeleteOptions) requestParams.body,
                  callback));

  private final CallFactory<V1ValidatingWebhookConfigurationList> listValidatingWebhookConfiguration =
      (requestParams, usage, cont, callback) ->
          wrap(listValidatingWebhookConfigurationAsync(usage, cont, callback));
  private final CallFactory<V1ValidatingWebhookConfiguration> readValidatingWebhookConfiguration =
      (requestParams, usage, cont, callback) ->
          wrap(readValidatingWebhookConfigurationAsync(usage, requestParams.name, callback));
  private final CallFactory<V1ValidatingWebhookConfiguration> createValidatingWebhookConfiguration =
      (requestParams, usage, cont, callback) ->
          wrap(
              createValidatingWebhookConfigurationAsync(
                  usage, (V1ValidatingWebhookConfiguration)
                      requestParams.body, callback));
  private final CallFactory<V1ValidatingWebhookConfiguration> patchValidatingWebhookConfiguration =
      (requestParams, usage, cont, callback) ->
          wrap(
              patchValidatingWebhookConfigurationAsync(
                  usage,
                  requestParams.name,
                  (V1Patch) requestParams.body,
                  callback));
  private final CallFactory<V1ValidatingWebhookConfiguration> replaceValidatingWebhookConfiguration =
      (requestParams, usage, cont, callback) ->
          wrap(
              replaceValidatingWebhookConfigurationAsync(
                  usage,
                  requestParams.name,
                  (V1ValidatingWebhookConfiguration) requestParams.body,
                  callback));
  private final CallFactory<V1Status> deleteValidatingWebhookConfiguration =
      (requestParams, usage, cont, callback) ->
          wrap(
              deleteValidatingWebhookConfigurationAsync(
                  usage,
                  requestParams.name,
                  (V1DeleteOptions) requestParams.body,
                  callback));

  private RetryStrategy retryStrategy;

  private String fieldSelector;
  private String labelSelector;

  private Integer limit = 50;
  private Integer timeoutSeconds = 5;
  private final CallParamsImpl callParams = new CallParamsImpl();

  private final String resourceVersion = "";

  private Integer maxRetryCount = 10;
  private final Boolean watch = null;
  private final CallFactory<DomainList> listDomain =
      (requestParams, usage, cont, callback) ->
          wrap(listDomainAsync(usage, requestParams.namespace, cont, callback));
  private final CallFactory<ClusterList> listCluster =
      (requestParams, usage, cont, callback) ->
          wrap(listClusterAsync(usage, requestParams.namespace, cont, callback));
  private final CallFactory<V1PodList> listPod =
      (requestParams, usage, cont, callback) ->
          wrap(listPodAsync(usage, requestParams.namespace, cont, callback));
  private final CallFactory<V1Status> deletecollectionPod =
      (requestParams, usage, cont, callback) ->
          wrap(deleteCollectionPodAsync(usage, requestParams.namespace, cont,
              (V1DeleteOptions) requestParams.body, callback));
  private final CallFactory<V1SecretList> listSecrets =
      (requestParams, usage, cont, callback) ->
          wrap(listSecretsAsync(usage, requestParams.namespace, cont, callback));
  private final CallFactory<V1ServiceList> listService =
      (requestParams, usage, cont, callback) ->
          wrap(listServiceAsync(usage, requestParams.namespace, cont, callback));
  private final CallFactory<CoreV1EventList> listEvent =
      (requestParams, usage, cont, callback) ->
          wrap(listEventAsync(usage, requestParams.namespace, cont, callback));
  private final CallFactory<V1NamespaceList> listNamespace =
      (requestParams, usage, cont, callback) ->
          wrap(listNamespaceAsync(usage, cont, callback));
  private final CallFactory<V1ConfigMapList> listConfigMaps =
      (requestParams, usage, cont, callback) ->
          wrap(listConfigMapsAsync(usage, requestParams.namespace, cont, callback));
  private final CallFactory<Domain> readDomain =
      (requestParams, usage, cont, callback) ->
          wrap(readDomainAsync(usage, requestParams.name, requestParams.namespace, callback));
  private final CallFactory<V1CustomResourceDefinition> readCrd =
      (requestParams, usage, cont, callback) ->
          wrap(readCustomResourceDefinitionAsync(usage, requestParams.name, callback));
  private final CallFactory<V1ConfigMap> readConfigmap =
      (requestParams, usage, cont, callback) ->
          wrap(readConfigMapAsync(usage, requestParams.name, requestParams.namespace, callback));
  private final CallFactory<V1Pod> readPod =
      (requestParams, usage, cont, callback) ->
          wrap(readPodAsync(usage, requestParams.name, requestParams.namespace, callback));
  private final CallFactory<V1Job> readJob =
      (requestParams, usage, cont, callback) ->
          wrap(readJobAsync(usage, requestParams.name, requestParams.namespace, callback));
  private final CallFactory<V1Service> readService =
      (requestParams, usage, cont, callback) ->
          wrap(readServiceAsync(usage, requestParams.name, requestParams.namespace, callback));
  private final CallFactory<V1Secret> readSecret =
      (requestParams, usage, cont, callback) ->
          wrap(readSecretAsync(usage, requestParams.name, requestParams.namespace, callback));

  private Integer gracePeriodSeconds = null;
  private final Boolean orphanDependents = null;
  private final String propagationPolicy = null;

  /* Custom Resource Definitions */
  private final CallFactory<V1Status> deleteConfigMap =
      (requestParams, usage, cont, callback) ->
          wrap(
              deleteConfigMapAsync(
                  usage,
                  requestParams.name,
                  requestParams.namespace,
                  (V1DeleteOptions) requestParams.body,
                  callback));
  private final CallFactory<Object> deletePod =
      (requestParams, usage, cont, callback) ->
          wrap(
              deletePodAsync(
                  usage,
                  requestParams.name,
                  requestParams.namespace,
                  (V1DeleteOptions) requestParams.body,
                  callback));
  private final CallFactory<V1Status> deleteJob =
      (requestParams, usage, cont, callback) ->
          wrap(
              deleteJobAsync(
                  usage,
                  requestParams.name,
                  requestParams.namespace,
                  requestParams.domainUid,
                  (V1DeleteOptions) requestParams.body,
                  callback));
  private final CallFactory<V1Service> deleteService =
      (requestParams, usage, cont, callback) ->
          wrap(
              deleteServiceAsync(
                  usage,
                  requestParams.name,
                  requestParams.namespace,
                  (V1DeleteOptions) requestParams.body,
                  callback));

  private final SynchronousCallFactory<V1Pod> patchPodCall =
      (client, requestParams) ->
          new CoreV1Api(client)
              .patchNamespacedPod(
                  requestParams.name,
                  requestParams.namespace,
                  (V1Patch) requestParams.body,
                  pretty,
                  dryRun,
                  null,
                  null,
                  null);
  private final SynchronousCallFactory<DomainList> listDomainCall =
      (client, requestParams) ->
          new WeblogicApi(client)
              .listNamespacedDomain(
                  requestParams.namespace,
                  pretty,
                  null,
                  fieldSelector,
                  labelSelector,
                  limit,
                  resourceVersion,
                  timeoutSeconds,
                  watch);
  private final SynchronousCallFactory<ClusterList> listClusterCall =
      (client, requestParams) ->
          new WeblogicApi(client)
              .listNamespacedCluster(
                  requestParams.namespace,
                  pretty,
                  null,
                  fieldSelector,
                  labelSelector,
                  limit,
                  resourceVersion,
                  timeoutSeconds,
                  watch);
  private final SynchronousCallFactory<Cluster> createClusterCall =
      (client, requestParams) ->
          new WeblogicApi(client)
              .createNamespacedCluster(
                  requestParams.namespace,
                  requestParams.body);
  private final SynchronousCallFactory<Domain> replaceDomainCall =
      (client, requestParams) ->
          new WeblogicApi(client)
              .replaceNamespacedDomain(
                  requestParams.name,
                  requestParams.namespace,
                  (Domain) requestParams.body);
  private final SynchronousCallFactory<Domain> replaceDomainStatusCall =
      (client, requestParams) ->
          new WeblogicApi(client)
              .replaceNamespacedDomainStatus(
                  requestParams.name,
                  requestParams.namespace,
                  (Domain) requestParams.body);
  private final SynchronousCallFactory<Domain> patchDomainCall =
      (client, requestParams) ->
          new WeblogicApi(client)
              .patchNamespacedDomain(
                  requestParams.name, requestParams.namespace, (V1Patch) requestParams.body);
  private final SynchronousCallFactory<Cluster> patchClusterCall =
      (client, requestParams) ->
          new WeblogicApi(client)
              .patchNamespacedCluster(
                  requestParams.name, requestParams.namespace, (V1Patch) requestParams.body);

  private final SynchronousCallFactory<V1SubjectAccessReview> createSubjectaccessreviewCall =
      ((client, requestParams) ->
          new AuthorizationV1Api(client)
              .createSubjectAccessReview(
                  (V1SubjectAccessReview) requestParams.body, null, null, null, pretty));
  private final SynchronousCallFactory<V1SelfSubjectAccessReview> createSelfsubjectacessreviewCall =
      (client, requestParams) ->
          new AuthorizationV1Api(client)
              .createSelfSubjectAccessReview(
                  (V1SelfSubjectAccessReview) requestParams.body, null, null, null, pretty);
  private final SynchronousCallFactory<V1SelfSubjectRulesReview> createSelfsubjectrulesreviewCall =
      (client, requestParams) ->
          new AuthorizationV1Api(client)
              .createSelfSubjectRulesReview(
                  (V1SelfSubjectRulesReview) requestParams.body, null, null, null, pretty);
  private final SynchronousCallFactory<V1TokenReview> createTokenReviewCall =
      (client, requestParams) ->
          new AuthenticationV1Api(client)
              .createTokenReview((V1TokenReview) requestParams.body, null, null, null, pretty);

  private final SynchronousCallFactory<CoreV1Event> createEventCall =
      (client, requestParams) ->
          new CoreV1Api(client)
              .createNamespacedEvent(requestParams.namespace, (CoreV1Event) requestParams.body, pretty, dryRun,
                  null, null);


  public CallBuilder() {
    this(ClientPool.getInstance());
  }

  private CallBuilder(CallBuilderTuning tuning, ClientPool helper) {
    if (tuning != null) {
      configureTuning(tuning.getCallRequestLimit(), tuning.getCallTimeoutSeconds(), tuning.getCallMaxRetryCount());
    }
    this.helper = helper;
  }

  public CallBuilder(ClientPool pool) {
    this(getCallBuilderTuning(), pool);
  }

  private static CallBuilderTuning getCallBuilderTuning() {
    return Optional.ofNullable(TuningParameters.getInstance())
        .map(TuningParameters::getCallBuilderTuning)
        .orElse(null);
  }

  /* Pods */

  static SynchronousCallDispatcher setCallDispatcher(SynchronousCallDispatcher newDispatcher) {
    SynchronousCallDispatcher oldDispatcher = dispatcher;
    dispatcher = newDispatcher;
    return oldDispatcher;
  }

  static void resetCallDispatcher() {
    dispatcher = DEFAULT_DISPATCHER;
  }

  static AsyncRequestStepFactory setStepFactory(AsyncRequestStepFactory newFactory) {
    AsyncRequestStepFactory oldFactory = stepFactory;
    stepFactory = newFactory;
    return oldFactory;
  }

  static void resetStepFactory() {
    stepFactory = DEFAULT_STEP_FACTORY;
  }

  /**
   * Consumer for label selectors.
   * @param selectors Label selectors
   * @return this CallBuilder
   */
  public CallBuilder withLabelSelectors(String... selectors) {
    this.labelSelector = !isNullOrEmpty(selectors) ? String.join(",", selectors) : null;
    return this;
  }

  /**
   * Set container name for the CallBuilder.
   * @param containerName container name
   * @return this CallBuilder
   */
  public CallBuilder withContainerName(String containerName) {
    this.container = containerName;
    return this;
  }

  public CallBuilder withFieldSelector(String fieldSelector) {
    this.fieldSelector = fieldSelector;
    return this;
  }

  public CallBuilder withRetryStrategy(RetryStrategy retryStrategy) {
    this.retryStrategy = retryStrategy;
    return this;
  }

  public CallBuilder withTimeoutSeconds(int timeoutSeconds) {
    this.timeoutSeconds = timeoutSeconds;
    return this;
  }

  public CallBuilder withGracePeriodSeconds(int gracePeriodSeconds) {
    this.gracePeriodSeconds = gracePeriodSeconds;
    return this;
  }

  private void configureTuning(int limit, int timeoutSeconds, int maxRetryCount) {
    this.limit = limit;
    this.timeoutSeconds = timeoutSeconds;
    this.maxRetryCount = maxRetryCount;

    this.callParams.setLimit(limit);
    this.callParams.setTimeoutSeconds(timeoutSeconds);
  }

  /**
   * Read Kubernetes version code.
   *
   * @return Version code
   * @throws ApiException API Exception
   */
  public VersionInfo readVersionCode() throws ApiException {
    RequestParams requestParams = new RequestParams("getVersion", null, null, null, callParams);
    return executeSynchronousCall(
        requestParams, ((client, params) -> new VersionApi(client).getCode()));
  }

  private <T> T executeSynchronousCall(
      RequestParams requestParams, SynchronousCallFactory<T> factory) throws ApiException {
    return dispatcher.execute(factory, requestParams, helper);
  }

  /**
   * Execute a synchronous call with a retry on failure.
   * @param call The call
   * @param retryDelaySeconds Retry delay in seconds
   * @param <T> Call return type
   * @return Results of operation, if successful
   * @throws Exception Exception types other than ApiException, which will cause failure
   */
  public <T> T executeSynchronousCallWithRetry(Callable<T> call, int retryDelaySeconds) throws Exception {
    /*
     * Implementation Note: synchronous calls are only allowed during operator initialization.
     * All make-right work must be done with the asynchronous calling pattern. Therefore, since
     * we know that this method will only be invoked during operator initialization, we've chosen
     * not to put a limit on the number of retries. This is acceptable because the liveness probe will
     * eventually kill the operator if the initialization sequence does not complete.
     *
     * This call was specifically added to address the Istio-related use case where the operator attempts
     * to initialize prior to the Istio Envoy sidecar completing its initialization as described in this
     * Istio bug: https://github.com/istio/istio/issues/11130. However, the pattern will also work for
     * use cases where the Kubernetes master happens to temporarily unavailable just as the operator is
     * starting.
     */
    T result = null;
    boolean complete = false;
    do {
      try {
        result = call.call();
        complete = true;
      } catch (RuntimeException re) {
        Throwable cause = re.getCause();
        if (cause instanceof ApiException) {
          LOGGER.warning(MessageKeys.EXCEPTION, cause);
        }
      } catch (Throwable t) {
        LOGGER.warning(MessageKeys.EXCEPTION, t);
      }

      if (complete) {
        break;
      }

      Thread.sleep(retryDelaySeconds * 1000L);

      // We are intentionally not limiting the number of retries as described in the implementation note above.
    } while (true);
    return result;
  }

  /**
   * List domains.
   *
   * @param namespace Namespace
   * @return Domain list
   * @throws ApiException API exception
   */
  public @Nonnull DomainList listDomain(String namespace) throws ApiException {
    RequestParams requestParams = new RequestParams("listDomain", namespace, null, null, callParams);
    return executeSynchronousCall(requestParams, listDomainCall);
  }

  private Call listDomainAsync(
      ApiClient client, String namespace, String cont, ApiCallback<DomainList> callback)
      throws ApiException {
    return new WeblogicApi(client)
        .listNamespacedDomainAsync(
            namespace,
            pretty,
            cont,
            fieldSelector,
            labelSelector,
            limit,
            resourceVersion,
            timeoutSeconds,
            watch,
            callback);
  }

  /**
   * Asynchronous step for listing domains.
   *
   * @param namespace Namespace
   * @param responseStep Response step for when call completes
   * @return Asynchronous step
   */
  public Step listDomainAsync(String namespace, ResponseStep<DomainList> responseStep) {
    return createRequestAsync(
        responseStep, new RequestParams("listDomain", namespace, null, null, callParams), listDomain);
  }

  private Call readDomainAsync(
      ApiClient client, String name, String namespace, ApiCallback<Domain> callback)
      throws ApiException {
    return new WeblogicApi(client)
        .getNamespacedDomainAsync(name, namespace, callback);
  }

  /**
   * Asynchronous step for reading domain.
   *
   * @param name Name
   * @param namespace Namespace
   * @param responseStep Response step for when call completes
   * @return Asynchronous step
   */
  public Step readDomainAsync(String name, String namespace, ResponseStep<Domain> responseStep) {
    return createRequestAsync(
        responseStep, new RequestParams("readDomain", namespace, name, null, name), readDomain);
  }

  /**
   * Replace domain.
   *
   * @param uid the domain uid (unique within the k8s cluster)
   * @param namespace Namespace
   * @param body Body
   * @return Replaced domain
   * @throws ApiException APIException
   */
  public Domain replaceDomain(String uid, String namespace, Domain body) throws ApiException {
    RequestParams requestParams = new RequestParams("replaceDomain", namespace, uid, body, uid);
    return executeSynchronousCall(requestParams, replaceDomainCall);
  }

  private Call listClusterAsync(
      ApiClient client, String namespace, String cont, ApiCallback<ClusterList> callback)
      throws ApiException {
    return new WeblogicApi(client)
        .listNamespacedClusterAsync(
            namespace,
            pretty,
            cont,
            fieldSelector,
            labelSelector,
            limit,
            resourceVersion,
            timeoutSeconds,
            watch,
            callback);
  }

  /**
   * Asynchronous step for listing clusters.
   *
   * @param namespace Namespace
   * @param responseStep Response step for when call completes
   * @return Asynchronous step
   */
  public Step listClusterAsync(String namespace, ResponseStep<ClusterList> responseStep) {
    return createRequestAsync(
        responseStep, new RequestParams("listCluster", namespace, null, null, callParams), listCluster);
  }

  /**
   * Patch cluster.
   *
   * @param name the domain uid (unique within the k8s cluster)
   * @param namespace the namespace containing the domain
   * @param patchBody the patch to apply
   * @return Updated cluster
   * @throws ApiException APIException
   */
  public Cluster patchCluster(String name, String namespace, V1Patch patchBody) throws ApiException {
    RequestParams requestParams =
        new RequestParams("patchCluster", namespace, name, patchBody, name);
    return executeSynchronousCall(requestParams, patchClusterCall);
  }


  /**
   * Replace domain status.
   *
   * @param uid the domain uid (unique within the k8s cluster)
   * @param namespace Namespace
   * @param body Body
   * @return Replaced domain
   * @throws ApiException APIException
   */
  public Domain replaceDomainStatus(String uid, String namespace, Domain body) throws ApiException {
    RequestParams requestParams = new RequestParams("replaceDomainStatus", namespace, uid, body, uid);
    return executeSynchronousCall(requestParams, replaceDomainStatusCall);
  }

  private Call replaceDomainAsync(
      ApiClient client, String name, String namespace, Domain body, ApiCallback<Domain> callback)
      throws ApiException {
    return new WeblogicApi(client)
        .replaceNamespacedDomainAsync(name, namespace, body, callback);
  }

  /**
   * Asynchronous step for replacing domain.
   *
   * @param name Name
   * @param namespace Namespace
   * @param body Body
   * @param responseStep Response step for when call completes
   * @return Asynchronous step
   */
  public Step replaceDomainAsync(
      String name, String namespace, Domain body, ResponseStep<Domain> responseStep) {
    return createRequestAsync(
        responseStep, new RequestParams("replaceDomain", namespace, name, body, name), replaceDomain);
  }

  /**
   * Patch domain.
   *
   * @param uid the domain uid (unique within the k8s cluster)
   * @param namespace the namespace containing the domain
   * @param patchBody the patch to apply
   * @return Updated domain
   * @throws ApiException APIException
   */
  public Domain patchDomain(String uid, String namespace, V1Patch patchBody) throws ApiException {
    RequestParams requestParams =
        new RequestParams("patchDomain", namespace, uid, patchBody, uid);
    return executeSynchronousCall(requestParams, patchDomainCall);
  }

  private Call patchDomainAsync(
      ApiClient client, String name, String namespace, V1Patch patch, ApiCallback<Domain> callback)
      throws ApiException {
    return new WeblogicApi(client)
        .patchNamespacedDomainAsync(name, namespace, patch, callback);
  }

  /**
   * Asynchronous step for patching a domain.
   *
   * @param name Name
   * @param namespace Namespace
   * @param patchBody instructions on what to patch
   * @param responseStep Response step for when call completes
   * @return Asynchronous step
   */
  public Step patchDomainAsync(
      String name, String namespace, V1Patch patchBody, ResponseStep<Domain> responseStep) {
    return createRequestAsync(
        responseStep,
        new RequestParams("patchDomain", namespace, name, patchBody, name),
        patchDomain);
  }

  private Call replaceDomainStatusAsync(
      ApiClient client, String name, String namespace, Domain body, ApiCallback<Domain> callback)
      throws ApiException {
    return new WeblogicApi(client)
        .replaceNamespacedDomainStatusAsync(name, namespace, body, callback);
  }

  /**
   * Asynchronous step for replacing domain status.
   *
   * @param name Name
   * @param namespace Namespace
   * @param body Body
   * @param responseStep Response step for when call completes
   * @return Asynchronous step
   */
  public Step replaceDomainStatusAsync(
      String name, String namespace, Domain body, ResponseStep<Domain> responseStep) {
    return createRequestAsync(
        responseStep,
        new RequestParams("replaceDomainStatus", namespace, name, body, name),
        replaceDomainStatus);
  }

  /* CRD's */

  private Call readCustomResourceDefinitionAsync(
      ApiClient client, String name, ApiCallback<V1CustomResourceDefinition> callback)
      throws ApiException {
    return new ApiextensionsV1Api(client)
        .readCustomResourceDefinitionAsync(name, pretty, callback);
  }

  /**
   * Asynchronous step for reading CRD.
   *
   * @param name Name
   * @param responseStep Response step for when call completes
   * @return Asynchronous step
   */
  public Step readCustomResourceDefinitionAsync(
      String name, ResponseStep<V1CustomResourceDefinition> responseStep) {
    return createRequestAsync(
        responseStep, new RequestParams("readCRD", null, name, null, callParams), readCrd);
  }

  private Call createCustomResourceDefinitionAsync(
      ApiClient client,
      V1CustomResourceDefinition body,
      ApiCallback<V1CustomResourceDefinition> callback)
      throws ApiException {
    return new ApiextensionsV1Api(client)
        .createCustomResourceDefinitionAsync(body, pretty, null, null, null, callback);
  }

  /**
   * Asynchronous step for creating CRD.
   *
   * @param body Body
   * @param responseStep Response step for when call completes
   * @return Asynchronous step
   */
  public Step createCustomResourceDefinitionAsync(
      V1CustomResourceDefinition body,
      ResponseStep<V1CustomResourceDefinition> responseStep) {
    return createRequestAsync(
        responseStep, new RequestParams("createCRD", null, null, body, callParams), createCrd);
  }

  private Call replaceCustomResourceDefinitionAsync(
      ApiClient client,
      String name,
      V1CustomResourceDefinition body,
      ApiCallback<V1CustomResourceDefinition> callback)
      throws ApiException {
    return new ApiextensionsV1Api(client)
        .replaceCustomResourceDefinitionAsync(name, body, pretty, null, null, null, callback);
  }

  /**
   * Asynchronous step for replacing CRD.
   *
   * @param name Name
   * @param body Body
   * @param responseStep Response step for when call completes
   * @return Asynchronous step
   */
  public Step replaceCustomResourceDefinitionAsync(
      String name,
      V1CustomResourceDefinition body,
      ResponseStep<V1CustomResourceDefinition> responseStep) {
    return createRequestAsync(
        responseStep, new RequestParams("replaceCRD", null, name, body, callParams), replaceCrd);
  }

  private Call listConfigMapsAsync(
      ApiClient client, String namespace, String cont, ApiCallback<V1ConfigMapList> callback)
      throws ApiException {
    return new CoreV1Api(client)
        .listNamespacedConfigMapAsync(
            namespace,
            pretty,
            allowWatchBookmarks,
            cont,
            fieldSelector,
            labelSelector,
            limit,
            resourceVersion,
            RESOURCE_VERSION_MATCH_UNSET,
            timeoutSeconds,
            watch,
            callback);
  }

  /**
   * Asynchronous step for listing configmaps in a namespace.
   *
   * @param namespace the namespace from which to list configmaps
   * @param responseStep Response step for when call completes
   * @return Asynchronous step
   */
  public Step listConfigMapsAsync(String namespace, ResponseStep<V1ConfigMapList> responseStep) {
    return createRequestAsync(
        responseStep,
        new RequestParams("listConfigMap", namespace, null, null, callParams),
        listConfigMaps);
  }

  private Call readConfigMapAsync(
      ApiClient client, String name, String namespace, ApiCallback<V1ConfigMap> callback)
      throws ApiException {
    return new CoreV1Api(client)
        .readNamespacedConfigMapAsync(name, namespace, pretty, callback);
  }

  /**
   * Asynchronous step for reading config map.
   *
   * @param name Name
   * @param namespace Namespace
   * @param domainUid Identifier of the domain that the ConfigMap is associated with
   * @param responseStep Response step for when call completes
   * @return Asynchronous step
   */
  public Step readConfigMapAsync(
      String name, String namespace, String domainUid, ResponseStep<V1ConfigMap> responseStep) {
    return createRequestAsync(
        responseStep, new RequestParams("readConfigMap", namespace, name, null, domainUid), readConfigmap);
  }

  private Call createConfigMapAsync(
      ApiClient client, String namespace, V1ConfigMap body, ApiCallback<V1ConfigMap> callback)
      throws ApiException {
    return new CoreV1Api(client)
        .createNamespacedConfigMapAsync(namespace, body, pretty, null, null, null, callback);
  }

  /**
   * Asynchronous step for creating config map.
   *
   * @param namespace Namespace
   * @param body Body
   * @param responseStep Response step for when call completes
   * @return Asynchronous step
   */
  public Step createConfigMapAsync(
      String namespace, V1ConfigMap body, ResponseStep<V1ConfigMap> responseStep) {
    return createRequestAsync(
        responseStep, new RequestParams("createConfigMap", namespace, null, body, callParams),
        createConfigmap);
  }

  private Call createSecretAsync(
          ApiClient client, String namespace, V1Secret body, ApiCallback<V1Secret> callback)
          throws ApiException {
    return new CoreV1Api(client)
            .createNamespacedSecretAsync(namespace, body, pretty, null, null, null, callback);
  }

  /**
   * Asynchronous step for creating secret.
   *
   * @param namespace Namespace
   * @param body Body
   * @param responseStep Response step for when call completes
   * @return Asynchronous step
   */
  public Step createSecretAsync(
          String namespace, V1Secret body, ResponseStep<V1Secret> responseStep) {
    return createRequestAsync(
            responseStep, new RequestParams("createSecret", namespace, null, body, callParams),
            createSecret);
  }

  private Call deleteConfigMapAsync(
      ApiClient client,
      String name,
      String namespace,
      V1DeleteOptions body,
      ApiCallback<V1Status> callback)
      throws ApiException {
    return new CoreV1Api(client)
        .deleteNamespacedConfigMapAsync(
            name,
            namespace,
            pretty,
            dryRun,
            gracePeriodSeconds,
            orphanDependents,
            propagationPolicy,
            body,
            callback);
  }

  /**
   * Asynchronous step for deleting config map.
   *
   * @param name Name
   * @param namespace Namespace
   * @param domainUid Identifier of the domain that the ConfigMap is associated with
   * @param deleteOptions Delete options
   * @param responseStep Response step for when call completes
   * @return Asynchronous step
   */
  public Step deleteConfigMapAsync(
      String name,
      String namespace,
      String domainUid,
      V1DeleteOptions deleteOptions,
      ResponseStep<V1Status> responseStep) {
    return createRequestAsync(
        responseStep,
        new RequestParams("deleteConfigMap", namespace, name, deleteOptions, domainUid),
        deleteConfigMap);
  }

  private Call replaceConfigMapAsync(
      ApiClient client,
      String name,
      String namespace,
      V1ConfigMap body,
      ApiCallback<V1ConfigMap> callback)
      throws ApiException {
    return new CoreV1Api(client)
        .replaceNamespacedConfigMapAsync(name, namespace, body, pretty, dryRun, null, null, callback);
  }

  /**
   * Asynchronous step for replacing config map.
   *
   * @param name Name
   * @param namespace Namespace
   * @param body Body
   * @param responseStep Response step for when call completes
   * @return Asynchronous step
   */
  public Step replaceConfigMapAsync(
      String name, String namespace, V1ConfigMap body, ResponseStep<V1ConfigMap> responseStep) {
    return createRequestAsync(
        responseStep,
        new RequestParams("replaceConfigMap", namespace, name, body,
            getDomainUidLabel(Optional.ofNullable(body).map(V1ConfigMap::getMetadata).orElse(null))),
        replaceConfigmap);
  }

  private Call patchConfigMapAsync(
      ApiClient client, String name, String namespace, V1Patch patch, ApiCallback<V1ConfigMap> callback)
      throws ApiException {
    return new CoreV1Api(client)
        .patchNamespacedConfigMapAsync(name, namespace, patch, pretty, null, null, null, null, callback);
  }

  /**
   * Asynchronous step for patching a config map.
   *
   * @param name Name
   * @param namespace Namespace
   * @param domainUid Identifier of the domain that the ConfigMap is associated with
   * @param patchBody instructions on what to patch
   * @param responseStep Response step for when call completes
   * @return Asynchronous step
   */
  public Step patchConfigMapAsync(
      String name, String namespace, String domainUid, V1Patch patchBody, ResponseStep<V1ConfigMap> responseStep) {
    return createRequestAsync(
        responseStep,
        new RequestParams("patchConfigMap", namespace, name, patchBody, domainUid),
        patchConfigMap);
  }

  /**
   * Asynchronous step for replacing secret.
   *
   * @param name Name
   * @param namespace Namespace
   * @param body Body
   * @param responseStep Response step for when call completes
   * @return Asynchronous step
   */
  public Step replaceSecretAsync(
          String name, String namespace, V1Secret body, ResponseStep<V1Secret> responseStep) {
    return createRequestAsync(
            responseStep,
            new RequestParams("replaceSecret", namespace, name, body, ""),
            replaceSecret);
  }

  private Call replaceSecretAsync(
          ApiClient client,
          String name,
          String namespace,
          V1Secret body,
          ApiCallback<V1Secret> callback)
          throws ApiException {
    return new CoreV1Api(client)
            .replaceNamespacedSecretAsync(name, namespace, body, pretty, dryRun, null, null, callback);
  }

  private Call listPodAsync(
      ApiClient client, String namespace, String cont, ApiCallback<V1PodList> callback)
      throws ApiException {
    return new CoreV1Api(client)
        .listNamespacedPodAsync(
            namespace,
            pretty,
            allowWatchBookmarks,
            cont,
            fieldSelector,
            labelSelector,
            limit,
            resourceVersion,
            RESOURCE_VERSION_MATCH_UNSET,
            timeoutSeconds,
            watch,
            callback);
  }

  /**
   * Asynchronous step for listing pods.
   *
   * @param namespace Namespace
   * @param responseStep Response step for when call completes
   * @return Asynchronous step
   */
  public Step listPodAsync(String namespace, ResponseStep<V1PodList> responseStep) {
    return createRequestAsync(
        responseStep, new RequestParams("listPod", namespace, null, null, callParams), listPod);
  }

  private Call readPodAsync(
      ApiClient client, String name, String namespace, ApiCallback<V1Pod> callback)
      throws ApiException {
    return new CoreV1Api(client)
        .readNamespacedPodAsync(name, namespace, pretty, callback);
  }

  /* Events */

  /**
   * Asynchronous step for reading pod.
   *
   * @param name Name
   * @param namespace Namespace
   * @param domainUid Identifier of the domain that the pod is associated with
   * @param responseStep Response step for when call completes
   * @return Asynchronous step
   */
  public Step readPodAsync(String name, String namespace, String domainUid, ResponseStep<V1Pod> responseStep) {
    return createRequestAsync(
        responseStep, new RequestParams("readPod", namespace, name, null, domainUid), readPod);
  }

  private Call createPodAsync(
      ApiClient client, String namespace, V1Pod body, ApiCallback<V1Pod> callback)
      throws ApiException {
    return new CoreV1Api(client)
        .createNamespacedPodAsync(namespace, body, pretty, null, null, null, callback);
  }

  /**
   * Asynchronous step for creating pod.
   *
   * @param namespace Namespace
   * @param body Body
   * @param responseStep Response step for when call completes
   * @return Asynchronous step
   */
  public Step createPodAsync(String namespace, V1Pod body, ResponseStep<V1Pod> responseStep) {
    return createRequestAsync(
        responseStep,
        new RequestParams("createPod", namespace, null, body, PodHelper.getPodDomainUid(body)),
        createPod);
  }

  /* Persistent Volumes */

  private Call deletePodAsync(
      ApiClient client,
      String name,
      String namespace,
      V1DeleteOptions deleteOptions,
      ApiCallback<Object> callback)
      throws ApiException {
    return deleteNamespacedPodAsync(
        client,
        name,
        namespace,
        pretty,
        dryRun,
        gracePeriodSeconds,
        orphanDependents,
        propagationPolicy,
        deleteOptions,
        callback);
  }

  /**
   * Asynchronous step for deleting pod.
   *
   * @param name Name
   * @param namespace Namespace
   * @param domainUid Identifier of the domain that the pod is associated with
   * @param deleteOptions Delete options
   * @param responseStep Response step for when call completes
   * @return Asynchronous step
   */
  public Step deletePodAsync(
      String name,
      String namespace,
      String domainUid,
      V1DeleteOptions deleteOptions,
      ResponseStep<Object> responseStep) {
    return createRequestAsync(
        responseStep, new RequestParams("deletePod", namespace, name, deleteOptions, domainUid),
        deletePod, retryStrategy);
  }

  private Call deleteNamespacedPodAsync(ApiClient client, String name, String namespace, String pretty, String dryRun,
                                        Integer gracePeriodSeconds, Boolean orphanDependents, String propagationPolicy,
                                        V1DeleteOptions body, ApiCallback<Object> callback) throws ApiException {
    Call localVarCall = this.deleteNamespacedPodValidateBeforeCall(client, name, namespace, pretty, dryRun,
        gracePeriodSeconds, orphanDependents, propagationPolicy, body, callback);
    Type localVarReturnType = (new TypeToken<>() {
    }).getType();
    client.executeAsync(localVarCall, localVarReturnType, callback);
    return localVarCall;
  }

  private Call deleteNamespacedPodValidateBeforeCall(ApiClient client, String name, String namespace, String pretty,
                                                     String dryRun, Integer gracePeriodSeconds,
                                                     Boolean orphanDependents, String propagationPolicy,
                                                     V1DeleteOptions body, ApiCallback<Object> callback)
      throws ApiException {
    if (name == null) {
      throw new ApiException("Missing the required parameter 'name' when calling deleteNamespacedPod(Async)");
    } else if (namespace == null) {
      throw new ApiException("Missing the required parameter 'namespace' when calling deleteNamespacedPod(Async)");
    } else {
      Call localVarCall = this.deleteNamespacedPodCall(client, name, namespace, pretty, dryRun, gracePeriodSeconds,
          orphanDependents, propagationPolicy, body, callback);
      return localVarCall;
    }
  }

  private Call deleteNamespacedPodCall(ApiClient client, String name, String namespace, String pretty, String dryRun,
                                       Integer gracePeriodSeconds, Boolean orphanDependents, String propagationPolicy,
                                       V1DeleteOptions body, ApiCallback<Object> callback) throws ApiException {
    String localVarPath = "/api/v1/namespaces/{namespace}/pods/{name}".replaceAll("\\{name\\}",
        client.escapeString(name)).replaceAll("\\{namespace\\}",
        client.escapeString(namespace));
    List<Pair> localVarQueryParams = new ArrayList<>();
    List<Pair> localVarCollectionQueryParams = new ArrayList<>();
    if (pretty != null) {
      localVarQueryParams.addAll(client.parameterToPair("pretty", pretty));
    }

    if (dryRun != null) {
      localVarQueryParams.addAll(client.parameterToPair("dryRun", dryRun));
    }

    if (gracePeriodSeconds != null) {
      localVarQueryParams.addAll(client.parameterToPair("gracePeriodSeconds", gracePeriodSeconds));
    }

    if (orphanDependents != null) {
      localVarQueryParams.addAll(client.parameterToPair("orphanDependents", orphanDependents));
    }

    if (propagationPolicy != null) {
      localVarQueryParams.addAll(client.parameterToPair("propagationPolicy", propagationPolicy));
    }

    Map<String, String> localVarHeaderParams = new HashMap<>();
    Map<String, String> localVarCookieParams = new HashMap<>();
    Map<String, Object> localVarFormParams = new HashMap<>();
    String[] localVarAccepts = new String[]{
        "application/json", "application/yaml", "application/vnd.kubernetes.protobuf"
    };
    String localVarAccept = client.selectHeaderAccept(localVarAccepts);
    if (localVarAccept != null) {
      localVarHeaderParams.put("Accept", localVarAccept);
    }

    String[] localVarContentTypes = new String[0];
    String localVarContentType = client.selectHeaderContentType(localVarContentTypes);
    localVarHeaderParams.put("Content-Type", localVarContentType);
    String[] localVarAuthNames = new String[]{"BearerToken"};
    return client.buildCall(localVarPath, "DELETE", localVarQueryParams, localVarCollectionQueryParams, body,
        localVarHeaderParams, localVarCookieParams, localVarFormParams, localVarAuthNames, callback);
  }

  /**
   * Synchronous step for patching a pod.
   *
   * @param name Name
   * @param namespace Namespace
   * @param domainUid Identifier of the domain that the pod is associated with
   * @param patchBody instructions on what to patch
   * @return the patched pod
   */
  public V1Pod patchPod(String name, String namespace, String domainUid, V1Patch patchBody) throws ApiException {
    RequestParams requestParams =
        new RequestParams("patchPod", namespace, name, patchBody, domainUid);
    return executeSynchronousCall(requestParams, patchPodCall);
  }

  private Call patchPodAsync(
      ApiClient client, String name, String namespace, V1Patch patch, ApiCallback<V1Pod> callback)
      throws ApiException {
    return new CoreV1Api(client)
        .patchNamespacedPodAsync(name, namespace, patch, pretty, null, null, null, null, callback);
  }

  /**
   * Asynchronous step for patching a pod.
   *
   * @param name Name
   * @param namespace Namespace
   * @param domainUid Identifier of the domain that the pod is associated with
   * @param patchBody instructions on what to patch
   * @param responseStep Response step for when call completes
   * @return Asynchronous step
   */
  public Step patchPodAsync(
      String name, String namespace, String domainUid, V1Patch patchBody, ResponseStep<V1Pod> responseStep) {
    return createRequestAsync(
        responseStep,
        new RequestParams("patchPod", namespace, name,  patchBody, domainUid),
        patchPod);
  }

  private Call deleteCollectionPodAsync(
      ApiClient client, String namespace, String cont, V1DeleteOptions deleteOptions, ApiCallback<V1Status> callback)
      throws ApiException {
    return new CoreV1Api(client)
        .deleteCollectionNamespacedPodAsync(
            namespace,
            pretty,
            cont,
            dryRun,
            fieldSelector,
            gracePeriodSeconds,
            labelSelector,
            limit,
            orphanDependents,
            propagationPolicy,
            resourceVersion,
            RESOURCE_VERSION_MATCH_UNSET,
            timeoutSeconds,
            deleteOptions,
            callback);
  }

  /**
   * Asynchronous step for deleting collection of pods.
   *
   * @param namespace Namespace
   * @param responseStep Response step for when call completes
   * @return Asynchronous step
   */
  public Step deleteCollectionPodAsync(String namespace, ResponseStep<V1Status> responseStep) {
    return createRequestAsync(
        responseStep,
        new RequestParams("deletePodCollection", namespace, null, null, callParams),
        deletecollectionPod);
  }

  private Call listJobAsync(
      ApiClient client, String namespace, String cont, ApiCallback<V1JobList> callback)
      throws ApiException {
    return new BatchV1Api(client)
        .listNamespacedJobAsync(
            namespace,
            pretty,
            allowWatchBookmarks,
            cont,
            fieldSelector,
            labelSelector,
            limit,
            resourceVersion,
            RESOURCE_VERSION_MATCH_UNSET,
            timeoutSeconds,
            watch,
            callback);
  }

  private final CallFactory<V1JobList> listJob =
      (requestParams, usage, cont, callback) ->
          wrap(listJobAsync(usage, requestParams.namespace, cont, callback));

  /**
   * Asynchronous step for listing jobs.
   *
   * @param namespace Namespace
   * @param responseStep Response step for when call completes
   * @return Asynchronous step
   */
  public Step listJobAsync(String namespace, ResponseStep<V1JobList> responseStep) {
    return createRequestAsync(
        responseStep, new RequestParams("listJob", namespace, null, null, callParams), listJob);
  }

  private Call createJobAsync(
      ApiClient client, String namespace, V1Job body, ApiCallback<V1Job> callback)
      throws ApiException {
    return new BatchV1Api(client)
        .createNamespacedJobAsync(namespace, body, pretty, null, null, null, callback);
  }

  /**
   * Asynchronous step for creating job.
   *
   * @param namespace Namespace
   * @param domainUid Identifier of the domain that the job is associated with
   * @param body Body
   * @param response Response step for when call completes
   * @return Asynchronous step
   */
  public Step createJobAsync(String namespace, String domainUid, V1Job body, @Nonnull ResponseStep<V1Job> response) {
    return createRequestAsync(
        response, new RequestParams("createJob", namespace, null, body, domainUid), createJob);
  }

  private Call readJobAsync(
      ApiClient client, String name, String namespace, ApiCallback<V1Job> callback)
      throws ApiException {
    return new BatchV1Api(client)
        .readNamespacedJobAsync(name, namespace, pretty, callback);
  }

  /**
   * Asynchronous step for reading job.
   *
   * @param name Name
   * @param namespace Namespace
   * @param domainUid Domain UID
   * @param responseStep Response step for when call completes
   * @return Asynchronous step
   */
  public Step readJobAsync(String name, String namespace, String domainUid, ResponseStep<V1Job> responseStep) {
    return createRequestAsync(
        responseStep, new RequestParams("readJob", namespace, name, null, domainUid), readJob);
  }

  private Call deleteJobAsync(
      ApiClient client,
      String name,
      String namespace,
      String domainUid,
      V1DeleteOptions body,
      ApiCallback<V1Status> callback)
      throws ApiException {
    return new BatchV1Api(client)
        .deleteNamespacedJobAsync(
            name,
            namespace,
            pretty,
            dryRun,
            gracePeriodSeconds,
            orphanDependents,
            propagationPolicy,
            body,
            callback);
  }

  /* Persistent Volume Claims */

  /**
   * Asynchronous step for deleting job.
   *
   * @param name Name
   * @param namespace Namespace
   * @param domainUid Identifier of the domain that the job is associated with
   * @param deleteOptions Delete options
   * @param responseStep Response step for when call completes
   * @return Asynchronous step
   */
  public Step deleteJobAsync(
      String name,
      String namespace,
      String domainUid,
      V1DeleteOptions deleteOptions,
      ResponseStep<V1Status> responseStep) {
    return createRequestAsync(
        responseStep, new RequestParams("deleteJob", namespace, name, deleteOptions, domainUid),
        deleteJob, timeoutSeconds);
  }

  private Call listServiceAsync(
      ApiClient client, String namespace, String cont, ApiCallback<V1ServiceList> callback)
      throws ApiException {
    return new CoreV1Api(client)
        .listNamespacedServiceAsync(
            namespace,
            pretty,
            allowWatchBookmarks,
            cont,
            fieldSelector,
            labelSelector,
            limit,
            resourceVersion,
            RESOURCE_VERSION_MATCH_UNSET,
            timeoutSeconds,
            watch,
            callback);
  }

  /**
   * Asynchronous step for listing services.
   *
   * @param namespace Namespace
   * @param responseStep Response step for when call completes
   * @return Asynchronous step
   */
  public Step listServiceAsync(String namespace, ResponseStep<V1ServiceList> responseStep) {
    return createRequestAsync(
        responseStep, new RequestParams("listService", namespace, null, null, callParams), listService);
  }

  private Call readServiceAsync(
      ApiClient client, String name, String namespace, ApiCallback<V1Service> callback)
      throws ApiException {
    return new CoreV1Api(client)
        .readNamespacedServiceAsync(name, namespace, pretty, callback);
  }

  /**
   * Asynchronous step for reading service.
   *
   * @param name Name
   * @param namespace Namespace
   * @param domainUid Identifier of the domain that the service is associated with
   * @param responseStep Response step for when call completes
   * @return Asynchronous step
   */
  public Step readServiceAsync(
      String name, String namespace, String domainUid, ResponseStep<V1Service> responseStep) {
    return createRequestAsync(
        responseStep, new RequestParams("readService", namespace, name, null, domainUid), readService);
  }

  private Call createServiceAsync(
      ApiClient client, String namespace, V1Service body, ApiCallback<V1Service> callback)
      throws ApiException {
    return new CoreV1Api(client)
        .createNamespacedServiceAsync(namespace, body, pretty, null, null, null, callback);
  }

  /**
   * Asynchronous step for creating service.
   *
   * @param namespace Namespace
   * @param body Body
   * @param responseStep Response step for when call completes
   * @return Asynchronous step
   */
  public Step createServiceAsync(
      String namespace, V1Service body, ResponseStep<V1Service> responseStep) {
    return createRequestAsync(
        responseStep,
        new RequestParams("createService", namespace, null, body,
            getDomainUidLabel(Optional.ofNullable(body).map(V1Service::getMetadata).orElse(null))),
        createService);
  }

  private Call deleteServiceAsync(
      ApiClient client,
      String name,
      String namespace,
      V1DeleteOptions deleteOptions,
      ApiCallback<V1Service> callback)
      throws ApiException {
    return new CoreV1Api(client)
        .deleteNamespacedServiceAsync(
            name,
            namespace,
            pretty,
            dryRun,
            gracePeriodSeconds,
            orphanDependents,
            propagationPolicy,
            deleteOptions,
            callback);
  }

  /**
   * Asynchronous step for deleting service.
   *
   * @param name Name
   * @param namespace Namespace
   * @param domainUid Identifier of the domain that the service is associated with
   * @param deleteOptions Delete options
   * @param responseStep Response step for when call completes
   * @return Asynchronous step
   */
  public Step deleteServiceAsync(
      String name,
      String namespace,
      String domainUid,
      V1DeleteOptions deleteOptions,
      ResponseStep<V1Service> responseStep) {
    return createRequestAsync(
        responseStep,
        new RequestParams("deleteService", namespace, name, deleteOptions, domainUid),
        deleteService);
  }

  private Call listPodDisruptionBudgetAsync(
      ApiClient client, String namespace, String cont, ApiCallback<V1PodDisruptionBudgetList> callback)
      throws ApiException {
    return new PolicyV1Api(client)
        .listNamespacedPodDisruptionBudgetAsync(
            namespace,
            pretty,
            allowWatchBookmarks,
            cont,
            fieldSelector,
            labelSelector,
            limit,
            resourceVersion,
            RESOURCE_VERSION_MATCH_UNSET,
            timeoutSeconds,
            watch,
            callback);
  }

  /**
   * Asynchronous step for listing PodDisruptionBudget.
   *
   * @param ns Namespace
   * @param responseStep Response step for when call completes
   * @return Asynchronous step
   */
  public Step listPodDisruptionBudgetAsync(String ns, ResponseStep<V1PodDisruptionBudgetList> responseStep) {
    return createRequestAsync(
        responseStep, new RequestParams("listPodDisruptionBudget", ns, null, null, callParams),
        listPodDisruptionBudget);
  }

  private Call readPodDisruptionBudgetAsync(
      ApiClient client, String name, String namespace, ApiCallback<V1PodDisruptionBudget> callback)
      throws ApiException {
    return new PolicyV1Api(client)
        .readNamespacedPodDisruptionBudgetAsync(name, namespace, pretty, callback);
  }

  /**
   * Asynchronous step for reading PodDisruptionBudget.
   *
   * @param name Name
   * @param namespace Namespace
   * @param responseStep Response step for when call completes
   * @return Asynchronous step
   */
  public Step readPodDisruptionBudgetAsync(
      String name, String namespace, ResponseStep<V1PodDisruptionBudget> responseStep) {
    return createRequestAsync(
        responseStep, new RequestParams("readPodDisruptionBudget", namespace, name, null, callParams),
        readPodDisruptionBudget);
  }

  private Call createPodDisruptionBudgetAsync(
      ApiClient client, String namespace, V1PodDisruptionBudget body,
      ApiCallback<V1PodDisruptionBudget> callback)
      throws ApiException {
    return new PolicyV1Api(client)
        .createNamespacedPodDisruptionBudgetAsync(namespace, body, pretty, null, null, null, callback);
  }

  /**
   * Asynchronous step for creating PodDisruptionBudget.
   *
   * @param namespace Namespace
   * @param body Body
   * @param responseStep Response step for when call completes
   * @return Asynchronous step
   */
  public Step createPodDisruptionBudgetAsync(
      String namespace, V1PodDisruptionBudget body, ResponseStep<V1PodDisruptionBudget> responseStep) {
    return createRequestAsync(
        responseStep,
        new RequestParams("createPodDisruptionBudget", namespace, null, body,
            getDomainUidLabel(Optional.ofNullable(body)
                .map(V1PodDisruptionBudget::getMetadata).orElse(null))),
        createPodDisruptionBudget);
  }

  private Call patchPodDisruptionBudgetAsync(
      ApiClient client, String name, String namespace, V1Patch patch,
      ApiCallback<V1PodDisruptionBudget> callback)
      throws ApiException {
    return new PolicyV1Api(client)
        .patchNamespacedPodDisruptionBudgetAsync(name, namespace, patch, pretty, null,
            null, null, null, callback);
  }

  /**
   * Asynchronous step for patching PodDisruptionBudget.
   *
   * @param name Name
   * @param namespace Namespace
   * @param patchBody instructions on what to patch
   * @param responseStep Response step for when call completes
   * @return Asynchronous step
   */
  public Step patchPodDisruptionBudgetAsync(
      String name, String namespace, V1Patch patchBody,
      ResponseStep<V1PodDisruptionBudget> responseStep) {
    return createRequestAsync(
        responseStep,
        new RequestParams("patchPodDisruptionBudget", namespace, name, patchBody, callParams),
        patchPodDisruptionBudget);
  }

  private Call deletePodDisruptionBudgetAsync(
      ApiClient client,
      String name,
      String namespace,
      V1DeleteOptions deleteOptions,
      ApiCallback<V1Status> callback)
      throws ApiException {
    return new PolicyV1Api(client)
        .deleteNamespacedPodDisruptionBudgetAsync(
            name,
            namespace,
            pretty,
            dryRun,
            gracePeriodSeconds,
            orphanDependents,
            propagationPolicy,
            deleteOptions,
            callback);
  }

  /**
   * Asynchronous step for deleting PodDisruptionBudget.
   *
   * @param name Name
   * @param namespace Namespace
   * @param domainUid Identifier of the domain that the service is associated with
   * @param deleteOptions Delete options
   * @param responseStep Response step for when call completes
   * @return Asynchronous step
   */
  public Step deletePodDisruptionBudgetAsync(
      String name,
      String namespace,
      String domainUid,
      V1DeleteOptions deleteOptions,
      ResponseStep<V1Status> responseStep) {
    return createRequestAsync(
        responseStep,
        new RequestParams("deletePodDisruptionBudget", namespace, name, deleteOptions, domainUid),
        deletePodDisruptionBudget);
  }

  /* Events */

  private Call listEventAsync(
      ApiClient client, String namespace, String cont, ApiCallback<CoreV1EventList> callback)
      throws ApiException {
    return new CoreV1Api(client)
        .listNamespacedEventAsync(
            namespace,
            pretty,
            allowWatchBookmarks,
            cont,
            fieldSelector,
            labelSelector,
            limit,
            resourceVersion,
            RESOURCE_VERSION_MATCH_UNSET,
            timeoutSeconds,
            watch,
            callback);
  }

  /**
   * Asynchronous step for listing events.
   *
   * @param namespace Namespace
   * @param responseStep Response step for when call completes
   * @return Asynchronous step
   */
  public Step listEventAsync(String namespace, ResponseStep<CoreV1EventList> responseStep) {
    return createRequestAsync(
        responseStep, new RequestParams("listEvent", namespace, null, null, callParams), listEvent);
  }

  private Call readEventAsync(
      ApiClient client, String name, String namespace, ApiCallback<CoreV1Event> callback)
      throws ApiException {
    return new CoreV1Api(client)
        .readNamespacedEventAsync(name, namespace, pretty, callback);
  }

  /**
   * Asynchronous step for reading event.
   *
   * @param name Name
   * @param namespace Namespace
   * @param responseStep Response step for when call completes
   * @return Asynchronous step
   */
  public Step readEventAsync(
      String name, String namespace, ResponseStep<CoreV1Event> responseStep) {
    return createRequestAsync(
        responseStep, new RequestParams("readEvent", namespace, name, null, callParams),
        readEvent);
  }

  /**
   * Create Event.
   *
   * @param namespace Namespace
   * @param body Request body
   * @return Created event
   * @throws ApiException API exception
   */
  public CoreV1Event createEvent(String namespace, CoreV1Event body) throws ApiException {
    RequestParams requestParams = new RequestParams("createEvent", namespace, null, body, callParams);
    return executeSynchronousCall(requestParams, createEventCall);
  }

  /**
   * Asynchronous step for creating event.
   *
   * @param namespace Namespace
   * @param body Body
   * @param responseStep Response step for when call completes
   * @return Asynchronous step
   */
  public Step createEventAsync(
      String namespace, CoreV1Event body, ResponseStep<CoreV1Event> responseStep) {
    return createRequestAsync(
        responseStep,
        new RequestParams("createEvent", namespace, null, body,
            getDomainUidLabel(Optional.ofNullable(body).map(CoreV1Event::getMetadata).orElse(null))),
        createEvent);
  }

  private Call createEventAsync(
      ApiClient client, String namespace, CoreV1Event body, ApiCallback<CoreV1Event> callback)
      throws ApiException {
    return new CoreV1Api(client)
        .createNamespacedEventAsync(namespace, body, pretty, null, null, null, callback);
  }

  /**
   * Asynchronous step for replacing event.
   *
   * @param namespace Namespace
   * @param body Body
   * @param responseStep Response step for when call completes
   * @return Asynchronous step
   */
  public Step replaceEventAsync(
      String name, String namespace, CoreV1Event body, ResponseStep<CoreV1Event> responseStep) {
    return createRequestAsync(
        responseStep,
        new RequestParams("replaceEvent", namespace, name, body, (String) null),
        replaceEvent);
  }

  private Call replaceEventAsync(
      ApiClient client,
      String name,
      String namespace,
      CoreV1Event body,
      ApiCallback<CoreV1Event> callback)
      throws ApiException {
    return new CoreV1Api(client)
        .replaceNamespacedEventAsync(name, namespace, body, pretty, dryRun, null, null, callback);
  }

  private Call listNamespaceAsync(
      ApiClient client, String cont, ApiCallback<V1NamespaceList> callback)
      throws ApiException {
    return new CoreV1Api(client)
        .listNamespaceAsync(
            pretty,
            allowWatchBookmarks,
            cont,
            fieldSelector,
            labelSelector,
            limit,
            resourceVersion,
            RESOURCE_VERSION_MATCH_UNSET,
            timeoutSeconds,
            watch,
            callback);
  }

  /**
   * Asynchronous step for listing namespaces.
   *
   * @param responseStep Response step for when call completes
   * @return Asynchronous step
   */
  public Step listNamespaceAsync(ResponseStep<V1NamespaceList> responseStep) {
    return createRequestAsync(
        responseStep, new RequestParams("listNamespace", null, null, null, callParams),
        listNamespace);
  }

  /* Self Subject Rules Review */

  private Call readSecretAsync(
      ApiClient client, String name, String namespace, ApiCallback<V1Secret> callback)
      throws ApiException {
    return new CoreV1Api(client)
        .readNamespacedSecretAsync(name, namespace, pretty, callback);
  }

  /**
   * Asynchronous step for reading secret.
   *
   * @param name Name
   * @param namespace Namespace
   * @param responseStep Response step for when call completes
   * @return Asynchronous step
   */
  public Step readSecretAsync(String name, String namespace, ResponseStep<V1Secret> responseStep) {
    return createRequestAsync(
        responseStep, new RequestParams("readSecret", namespace, name, null, callParams), readSecret);
  }

  private Call listSecretsAsync(
      ApiClient client, String namespace, String cont, ApiCallback<V1SecretList> callback)
      throws ApiException {
    return new CoreV1Api(client)
        .listNamespacedSecretAsync(
            namespace,
            pretty,
            allowWatchBookmarks,
            cont,
            fieldSelector,
            labelSelector,
            limit,
            resourceVersion,
            RESOURCE_VERSION_MATCH_UNSET,
            timeoutSeconds,
            watch,
            callback);
  }

  /**
   * Asynchronous step for listing secrets in a namespace.
   *
   * @param namespace the namespace from which to list secrets
   * @param responseStep Response step for when call completes
   * @return Asynchronous step
   */
  public Step listSecretsAsync(String namespace, ResponseStep<V1SecretList> responseStep) {
    return createRequestAsync(
        responseStep,
        new RequestParams("listSecret", namespace, null, null, callParams),
        listSecrets);
  }

  /**
   * Create subject access review.
   *
   * @param body Body
   * @return Created subject access review
   * @throws ApiException API Exception
   */
  public V1SubjectAccessReview createSubjectAccessReview(V1SubjectAccessReview body)
      throws ApiException {
    RequestParams params =
        new RequestParams("createSubjectAccessReview", null, null, body, callParams);
    return executeSynchronousCall(params, createSubjectaccessreviewCall);
  }

  /* Token Review */

  /**
   * Create self subject access review.
   *
   * @param body Body
   * @return Created self subject access review
   * @throws ApiException API Exception
   */
  public V1SelfSubjectAccessReview createSelfSubjectAccessReview(V1SelfSubjectAccessReview body)
      throws ApiException {
    RequestParams requestParams
        = new RequestParams("createSelfSubjectAccessReview", null, null, body, callParams);
    return executeSynchronousCall(requestParams, createSelfsubjectacessreviewCall);
  }

  /**
   * Create self subject rules review.
   *
   * @param body Body
   * @return Created self subject rules review
   * @throws ApiException API Exception
   */
  public V1SelfSubjectRulesReview createSelfSubjectRulesReview(V1SelfSubjectRulesReview body)
      throws ApiException {
    RequestParams params
        = new RequestParams("createSelfSubjectRulesReview", null, null, body, callParams);
    return executeSynchronousCall(params, createSelfsubjectrulesreviewCall);
  }

  /**
   * Create token review.
   *
   * @param body Body
   * @return Created token review
   * @throws ApiException API Exception
   */
  public V1TokenReview createTokenReview(V1TokenReview body) throws ApiException {
    RequestParams requestParams
        = new RequestParams("createTokenReview", null, null, body, callParams);
    return executeSynchronousCall(requestParams, createTokenReviewCall);
  }

  /* ValidatingWebhookConfiguration */

  private Call listValidatingWebhookConfigurationAsync(
      ApiClient client, String cont, ApiCallback<V1ValidatingWebhookConfigurationList> callback)
      throws ApiException {
    return new AdmissionregistrationV1Api(client)
        .listValidatingWebhookConfigurationAsync(
            pretty,
            allowWatchBookmarks,
            cont,
            fieldSelector,
            labelSelector,
            limit,
            resourceVersion,
            RESOURCE_VERSION_MATCH_UNSET,
            timeoutSeconds,
            watch,
            callback);
  }

  /**
   * Asynchronous step for listing validating webhook configuration.
   *
   * @param responseStep Response step for when call completes
   * @return Asynchronous step
   */
  public Step listValidatingWebhookConfigurationAsync(ResponseStep<V1ValidatingWebhookConfigurationList> responseStep) {
    return createRequestAsync(
        responseStep,
        new RequestParams("listValidatingWebhookConfiguration", null, null, null, callParams),
        listValidatingWebhookConfiguration);
  }

  private Call readValidatingWebhookConfigurationAsync(
      ApiClient client, String name, ApiCallback<V1ValidatingWebhookConfiguration> callback)
      throws ApiException {
    return new AdmissionregistrationV1Api(client).readValidatingWebhookConfigurationAsync(name, pretty, callback);
  }

  /**
   * Asynchronous step for reading validating webhook configuration.
   *
   * @param name Name
   * @param responseStep Response step for when call completes
   * @return Asynchronous step
   */
  public Step readValidatingWebhookConfigurationAsync(
      String name, ResponseStep<V1ValidatingWebhookConfiguration> responseStep) {
    return createRequestAsync(
        responseStep,
        new RequestParams("readValidatingWebhookConfiguration", null, name, null, ""),
        readValidatingWebhookConfiguration);
  }

  private Call createValidatingWebhookConfigurationAsync(
      ApiClient client, V1ValidatingWebhookConfiguration body,
      ApiCallback<V1ValidatingWebhookConfiguration> callback)
      throws ApiException {
    return new AdmissionregistrationV1Api(client)
        .createValidatingWebhookConfigurationAsync(body, pretty, null, null, null, callback);
  }

  /**
   * Asynchronous step for creating validating webhook configuration.
   *
   * @param body Body
   * @param responseStep Response step for when call completes
   * @return Asynchronous step
   */
  public Step createValidatingWebhookConfigurationAsync(
      V1ValidatingWebhookConfiguration body, ResponseStep<V1ValidatingWebhookConfiguration> responseStep) {
    return createRequestAsync(
        responseStep,
        new RequestParams("createValidatingWebhookConfiguration", null, null, body, callParams),
        createValidatingWebhookConfiguration);
  }

  private Call patchValidatingWebhookConfigurationAsync(
      ApiClient client, String name, V1Patch patch,
      ApiCallback<V1ValidatingWebhookConfiguration> callback)
      throws ApiException {
    return new AdmissionregistrationV1Api(client)
        .patchValidatingWebhookConfigurationAsync(name, patch, pretty, null, null, null, null, callback);
  }

  /**
   * Asynchronous step for patching validating webhook configuration.
   *
   * @param name Name
   * @param patchBody instructions on what to patch
   * @param responseStep Response step for when call completes
   * @return Asynchronous step
   */
  public Step patchValidatingWebhookConfigurationAsync(
      String name, V1Patch patchBody,
      ResponseStep<V1ValidatingWebhookConfiguration> responseStep) {
    return createRequestAsync(
        responseStep,
        new RequestParams("patchValidatingWebhookConfiguration", null, name, patchBody, callParams),
        patchValidatingWebhookConfiguration);
  }

  /**
   * Asynchronous step for replacing validating webhook configuration.
   *
   * @param body Body
   * @param responseStep Response step for when call completes
   * @return Asynchronous step
   */
  public Step replaceValidatingWebhookConfigurationAsync(
      String name, V1ValidatingWebhookConfiguration body, ResponseStep<V1ValidatingWebhookConfiguration> responseStep) {
    return createRequestAsync(
        responseStep,
        new RequestParams("replaceValidatingWebhookConfiguration", null, name, body, (String) null),
        replaceValidatingWebhookConfiguration);
  }

  private Call replaceValidatingWebhookConfigurationAsync(
      ApiClient client,
      String name,
      V1ValidatingWebhookConfiguration body,
      ApiCallback<V1ValidatingWebhookConfiguration> callback)
      throws ApiException {
    return new AdmissionregistrationV1Api(client)
        .replaceValidatingWebhookConfigurationAsync(name, body, pretty, dryRun, null, null, callback);
  }

  private Call deleteValidatingWebhookConfigurationAsync(
      ApiClient client,
      String name,
      V1DeleteOptions deleteOptions,
      ApiCallback<V1Status> callback)
      throws ApiException {
    return new AdmissionregistrationV1Api(client)
        .deleteValidatingWebhookConfigurationAsync(
            name,
            pretty,
            dryRun,
            gracePeriodSeconds,
            orphanDependents,
            propagationPolicy,
            deleteOptions,
            callback);
  }

  /**
   * Asynchronous step for deleting validating webhook configuration.
   *
   * @param name Name
   * @param deleteOptions Delete options
   * @param responseStep Response step for when call completes
   * @return Asynchronous step
   */
  public Step deleteValidatingWebhookConfigurationAsync(
      String name,
      V1DeleteOptions deleteOptions,
      ResponseStep<V1Status> responseStep) {
    return createRequestAsync(
        responseStep,
        new RequestParams("deleteValidatingWebhookConfiguration", null, name, deleteOptions, (String) null),
        deleteValidatingWebhookConfiguration);
  }

  public Step readPodLogAsync(String name, String namespace, String domainUid, ResponseStep<String> responseStep) {
    return createRequestAsync(
        responseStep, new RequestParams("readPodLog", namespace, name, null, domainUid), readPodLog);
  }

  private Call readPodLogAsync(
      ApiClient client,
      String name,
      String namespace,
      String container,
      Boolean follow,
      Boolean insecureSkipTLSVerifyBackend,
      Integer limitBytes,
      String pretty,
      Boolean previous,
      Integer sinceSeconds,
      Integer tailLines,
      Boolean timestamps,
      ApiCallback<String> callback)
      throws ApiException {
    return new CoreV1Api(client)
        .readNamespacedPodLogAsync(
            name,
            namespace,
            container,
            follow,
            insecureSkipTLSVerifyBackend,
            limitBytes,
            pretty,
            previous,
            sinceSeconds,
            tailLines,
            timestamps,
            callback);
  }

  private <T> Step createRequestAsync(
      ResponseStep<T> next, RequestParams requestParams, CallFactory<T> factory) {
    return stepFactory.createRequestAsync(
        next,
        requestParams,
        factory,
        null,
        helper,
        timeoutSeconds,
        maxRetryCount,
        gracePeriodSeconds,
        fieldSelector,
        labelSelector,
        resourceVersion);
  }

  private <T> Step createRequestAsync(
      ResponseStep<T> next, RequestParams requestParams, CallFactory<T> factory, RetryStrategy retryStrategy) {
    return stepFactory.createRequestAsync(
        next,
        requestParams,
        factory,
        retryStrategy,
        helper,
        timeoutSeconds,
        maxRetryCount,
        gracePeriodSeconds,
        fieldSelector,
        labelSelector,
        resourceVersion);
  }

  private <T> Step createRequestAsync(
      ResponseStep<T> next, RequestParams requestParams, CallFactory<T> factory, int timeoutSeconds) {
    return stepFactory.createRequestAsync(
        next,
        requestParams,
        factory,
        retryStrategy,
        helper,
        timeoutSeconds,
        maxRetryCount,
        gracePeriodSeconds,
        fieldSelector,
        labelSelector,
        resourceVersion);
  }

  private CancellableCall wrap(Call call) {
    return new CallWrapper(call);
  }

  public ClientPool getClientPool() {
    return this.helper;
  }

  /**
   * Create AccessTokenAuthentication component for authenticating user represented by
   * the given token.
   * @param accessToken - User's Bearer token
   * @return - this CallBuilder instance
   */
  public CallBuilder withAuthentication(String accessToken) {
    if (!isNullOrEmpty(accessToken)) {
      this.helper = new ClientPool().withApiClient(createApiClient(accessToken));
    }
    return this;
  }

  private ApiClient createApiClient(String accessToken) {
    try {
      ClientBuilder builder = ClientBuilder.standard();
      return builder.setAuthentication(
          new AccessTokenAuthentication(accessToken)).build();
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  // --------------------------- Custom Resource Cluster -----------------------------------
  /**
   * Create a Cluster Custom Resource.
   *
   * @param cluster Cluster custom resource model object
   * @throws ApiException if Kubernetes client API call fails
   */
  public void createClusterCustomResource(Cluster cluster) throws ApiException {
    if (cluster == null) {
      throw new IllegalArgumentException(
          "Parameter 'cluster' cannot be null when calling createClusterCustomResource()");
    }

    if (cluster.getMetadata() == null) {
      throw new IllegalArgumentException(
          "'metadata' field of the parameter 'cluster' cannot be null when calling createClusterCustomResource()");
    }

    if (cluster.getMetadata().getNamespace() == null) {
      throw new IllegalArgumentException(
          "'namespace' field in the metadata cannot be null when calling createClusterCustomResource()");
    }

    String namespace = cluster.getMetadata().getNamespace();

    JsonElement json = convertToJson(cluster);

    RequestParams requestParams = new RequestParams("createCluster", namespace, null, json, callParams);
    executeSynchronousCall(requestParams, createClusterCall);
  }

  /**
   * Converts a Java Object to a JSON element.
   *
   * @param obj java object to be converted
   * @return object representing Json element
   */
  private JsonElement convertToJson(Object obj) {
    ClientPool pool = getClientPool();
    ApiClient apiClient = pool.take();
    try {
      Gson gson = apiClient.getJSON().getGson();
      return gson.toJsonTree(obj);
    } finally {
      pool.recycle(apiClient);
    }
  }

  /**
   * Get the Cluster Custom Resource.
   *
   * @param clusterName unique domain identifier
   * @param namespace name of namespace
   * @return cluster custom resource or null if Cluster does not exist
   * @throws ApiException if Kubernetes request fails
   */
  public Cluster getClusterCustomResource(String clusterName, String namespace)
      throws ApiException {
    return getClusterCustomResource(clusterName, namespace, KubernetesConstants.CLUSTER_VERSION);
  }

  /**
   * Get the Cluster Custom Resource.
   *
   * @param clusterName unique domain identifier
   * @param namespace name of namespace
   * @param clusterVersion version of doclustermain
   * @return cluster custom resource or null if Cluster does not exist
   * @throws ApiException if Kubernetes request fails
   */
  public Cluster getClusterCustomResource(String clusterName, String namespace, String clusterVersion)
      throws ApiException {
    Object cluster = null;
    ClientPool pool = getClientPool();
    ApiClient apiClient = pool.take();
    try {
      CustomObjectsApi customObjectsApi = new CustomObjectsApi(apiClient);
      cluster = customObjectsApi.getNamespacedCustomObject(
          KubernetesConstants.DOMAIN_GROUP, // custom resource's group name
          clusterVersion, // //custom resource's version
          namespace, // custom resource's namespace
          KubernetesConstants.CLUSTER_PLURAL, // custom resource's plural name
          clusterName // custom object's name
      );
    } catch (ApiException apex) {
      if (apex.getCode() != HTTP_NOT_FOUND) {
        throw apex;
      }
    } finally {
      pool.recycle(apiClient);
    }

    if (cluster != null) {
      return handleResponse(cluster, Cluster.class);
    }

    return null;
  }

  /**
   * Converts the response to appropriate type.
   *
   * @param response response object to convert
   * @param type the type to convert into
   * @return the Java object of the type the response object is converted to
   */
  @SuppressWarnings("unchecked")
  private <T> T handleResponse(Object response, Class<T> type) {
    JsonElement jsonElement = convertToJson(response);
    ClientPool pool = getClientPool();
    ApiClient apiClient = pool.take();
    try {
      return apiClient.getJSON().getGson().fromJson(jsonElement, type);
    } finally {
      pool.recycle(apiClient);
    }
  }

  /**
   * List clusters.
   *
   * @param namespace Namespace
   * @return Cluster list
   * @throws ApiException API exception
   */
  public @Nonnull ClusterList listCluster(String namespace) throws ApiException {
    RequestParams requestParams = new RequestParams("listCluster", namespace, null, null, callParams);
    return executeSynchronousCall(requestParams, listClusterCall);
  }
}

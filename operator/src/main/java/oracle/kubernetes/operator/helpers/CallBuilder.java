// Copyright 2017, 2019, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helpers;

import static java.net.HttpURLConnection.HTTP_CONFLICT;

import com.squareup.okhttp.Call;
import io.kubernetes.client.ApiCallback;
import io.kubernetes.client.ApiClient;
import io.kubernetes.client.ApiException;
import io.kubernetes.client.apis.ApiextensionsV1beta1Api;
import io.kubernetes.client.apis.AuthenticationV1Api;
import io.kubernetes.client.apis.AuthorizationV1Api;
import io.kubernetes.client.apis.BatchV1Api;
import io.kubernetes.client.apis.CoreV1Api;
import io.kubernetes.client.apis.VersionApi;
import io.kubernetes.client.models.V1ConfigMap;
import io.kubernetes.client.models.V1DeleteOptions;
import io.kubernetes.client.models.V1EventList;
import io.kubernetes.client.models.V1Job;
import io.kubernetes.client.models.V1Namespace;
import io.kubernetes.client.models.V1PersistentVolume;
import io.kubernetes.client.models.V1PersistentVolumeClaim;
import io.kubernetes.client.models.V1PersistentVolumeClaimList;
import io.kubernetes.client.models.V1PersistentVolumeList;
import io.kubernetes.client.models.V1Pod;
import io.kubernetes.client.models.V1PodList;
import io.kubernetes.client.models.V1Secret;
import io.kubernetes.client.models.V1SelfSubjectAccessReview;
import io.kubernetes.client.models.V1SelfSubjectRulesReview;
import io.kubernetes.client.models.V1Service;
import io.kubernetes.client.models.V1ServiceList;
import io.kubernetes.client.models.V1Status;
import io.kubernetes.client.models.V1SubjectAccessReview;
import io.kubernetes.client.models.V1TokenReview;
import io.kubernetes.client.models.V1beta1CustomResourceDefinition;
import io.kubernetes.client.models.VersionInfo;
import java.util.Optional;
import java.util.function.Consumer;
import javax.json.JsonPatch;
import oracle.kubernetes.operator.TuningParameters;
import oracle.kubernetes.operator.TuningParameters.CallBuilderTuning;
import oracle.kubernetes.operator.calls.AsyncRequestStep;
import oracle.kubernetes.operator.calls.CallFactory;
import oracle.kubernetes.operator.calls.CallWrapper;
import oracle.kubernetes.operator.calls.CancellableCall;
import oracle.kubernetes.operator.calls.RequestParams;
import oracle.kubernetes.operator.calls.SynchronousCallDispatcher;
import oracle.kubernetes.operator.calls.SynchronousCallFactory;
import oracle.kubernetes.operator.logging.LoggingFacade;
import oracle.kubernetes.operator.logging.LoggingFactory;
import oracle.kubernetes.operator.logging.MessageKeys;
import oracle.kubernetes.operator.utils.PatchUtils;
import oracle.kubernetes.operator.work.Step;
import oracle.kubernetes.weblogic.domain.v2.Domain;
import oracle.kubernetes.weblogic.domain.v2.DomainList;
import oracle.kubernetes.weblogic.domain.v2.api.WeblogicApi;

/** Simplifies synchronous and asynchronous call patterns to the Kubernetes API Server. */
@SuppressWarnings({"WeakerAccess", "UnusedReturnValue"})
public class CallBuilder {

  /** HTTP status code for "Not Found". */
  public static final int NOT_FOUND = 404;

  private static final LoggingFacade LOGGER = LoggingFactory.getLogger("Operator", "Operator");

  private static SynchronousCallDispatcher DISPATCHER =
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

  private String pretty = "false";
  private String fieldSelector;
  private Boolean includeUninitialized = Boolean.FALSE;
  private String labelSelector;
  private Integer limit = 500;
  private String resourceVersion = "";
  private Integer timeoutSeconds = 5;
  private Integer maxRetryCount = 10;
  private Boolean watch = Boolean.FALSE;
  private Boolean exact = Boolean.FALSE;
  private Boolean export = Boolean.FALSE;

  private Integer gracePeriodSeconds = null;
  private Boolean orphanDependents = null;
  private String propagationPolicy = null;

  private final ClientPool helper;

  public CallBuilder() {
    this(getCallBuilderTuning(), ClientPool.getInstance());
  }

  private static CallBuilderTuning getCallBuilderTuning() {
    return Optional.ofNullable(TuningParameters.getInstance())
        .map(TuningParameters::getCallBuilderTuning)
        .orElse(null);
  }

  private CallBuilder(CallBuilderTuning tuning, ClientPool helper) {
    if (tuning != null) {
      tuning(tuning.callRequestLimit, tuning.callTimeoutSeconds, tuning.callMaxRetryCount);
    }
    this.helper = helper;
  }

  public CallBuilder withLabelSelectors(String... selectors) {
    this.labelSelector = String.join(",", selectors);
    return this;
  }

  public CallBuilder withFieldSelector(String fieldSelector) {
    this.fieldSelector = fieldSelector;
    return this;
  }

  private void tuning(int limit, int timeoutSeconds, int maxRetryCount) {
    this.limit = limit;
    this.timeoutSeconds = timeoutSeconds;
    this.maxRetryCount = maxRetryCount;
  }

  /**
   * Creates instance that will acquire clients as needed from the {@link ClientPool} instance.
   *
   * @param tuning Tuning parameters
   * @return Call builder
   */
  static CallBuilder create(CallBuilderTuning tuning) {
    return new CallBuilder(tuning, ClientPool.getInstance());
  }

  /**
   * Consumer for lambda-based builder pattern.
   *
   * @param builderFunction Builder lambda function
   * @return this CallBuilder
   */
  public CallBuilder with(Consumer<CallBuilder> builderFunction) {
    builderFunction.accept(this);
    return this;
  }

  /* Version */

  /**
   * Read Kubernetes version code.
   *
   * @return Version code
   * @throws ApiException API Exception
   */
  public VersionInfo readVersionCode() throws ApiException {
    RequestParams requestParams = new RequestParams("getVersion", null, null, null);
    return executeSynchronousCall(
        requestParams, ((client, params) -> new VersionApi(client).getCode()));
  }

  /**
   * Class extended by callers to {@link
   * #executeSynchronousCallWithConflictRetry(RequestParamsBuilder, SynchronousCallFactory,
   * ConflictRetry)} for building the RequestParams to be passed to {@link
   * #executeSynchronousCall(RequestParams, SynchronousCallFactory)}.
   *
   * @param <T> Type of kubernetes object to be passed to the API
   */
  abstract static class RequestParamsBuilder<T> {
    T body;

    public RequestParamsBuilder(T body) {
      this.body = body;
    }

    abstract RequestParams buildRequestParams();

    void setBody(T body) {
      this.body = body;
    }
  }

  private <T> T executeSynchronousCallWithConflictRetry(
      RequestParamsBuilder requestParamsBuilder,
      SynchronousCallFactory<T> factory,
      ConflictRetry<T> conflictRetry)
      throws ApiException {
    int retryCount = 0;
    while (retryCount == 0 || retryCount < maxRetryCount) {
      retryCount++;
      RequestParams requestParams = requestParamsBuilder.buildRequestParams();
      try {
        return executeSynchronousCall(requestParams, factory);
      } catch (ApiException apiException) {
        boolean retry = false;
        if (apiException.getCode() == HTTP_CONFLICT
            && conflictRetry != null
            && retryCount < maxRetryCount) {
          T body = conflictRetry.getUpdatedObject();
          if (body != null) {
            requestParamsBuilder.setBody(body);
            retry = true;
            LOGGER.fine(
                MessageKeys.SYNC_RETRY,
                requestParams.call,
                apiException.getCode(),
                apiException.getMessage(),
                retryCount,
                maxRetryCount);
          }
        }
        if (!retry) {
          throw apiException;
        }
      }
    }
    return null;
  }

  private <T> T executeSynchronousCall(
      RequestParams requestParams, SynchronousCallFactory<T> factory) throws ApiException {
    return DISPATCHER.execute(factory, requestParams, helper);
  }

  /* Namespaces */

  /**
   * Read namespace.
   *
   * @param name Name
   * @return Read service
   * @throws ApiException API Exception
   */
  public V1Namespace readNamespace(String name) throws ApiException {
    ApiClient client = helper.take();
    try {
      return new CoreV1Api(client).readNamespace(name, pretty, exact, export);
    } finally {
      helper.recycle(client);
    }
  }

  /**
   * Create namespace.
   *
   * @param body Body
   * @return Created service
   * @throws ApiException API Exception
   */
  public V1Namespace createNamespace(V1Namespace body) throws ApiException {
    ApiClient client = helper.take();
    try {
      return new CoreV1Api(client).createNamespace(body, pretty);
    } finally {
      helper.recycle(client);
    }
  }

  /* Domains */

  private SynchronousCallFactory<DomainList> LIST_DOMAIN_CALL =
      (client, requestParams) ->
          new WeblogicApi(client)
              .listWebLogicOracleV2NamespacedDomain(
                  requestParams.namespace,
                  pretty,
                  "",
                  fieldSelector,
                  includeUninitialized,
                  labelSelector,
                  limit,
                  resourceVersion,
                  timeoutSeconds,
                  watch);

  /**
   * List domains.
   *
   * @param namespace Namespace
   * @return Domain list
   * @throws ApiException API exception
   */
  public DomainList listDomain(String namespace) throws ApiException {
    RequestParams requestParams = new RequestParams("listDomain", namespace, null, null);
    return executeSynchronousCall(requestParams, LIST_DOMAIN_CALL);
  }

  private com.squareup.okhttp.Call listDomainAsync(
      ApiClient client, String namespace, String cont, ApiCallback<DomainList> callback)
      throws ApiException {
    return new WeblogicApi(client)
        .listWebLogicOracleV2NamespacedDomainAsync(
            namespace,
            pretty,
            cont,
            fieldSelector,
            includeUninitialized,
            labelSelector,
            limit,
            resourceVersion,
            timeoutSeconds,
            watch,
            callback);
  }

  private final CallFactory<DomainList> LIST_DOMAIN =
      (requestParams, usage, cont, callback) ->
          wrap(listDomainAsync(usage, requestParams.namespace, cont, callback));

  /**
   * Asynchronous step for listing domains.
   *
   * @param namespace Namespace
   * @param responseStep Response step for when call completes
   * @return Asynchronous step
   */
  public Step listDomainAsync(String namespace, ResponseStep<DomainList> responseStep) {
    return createRequestAsync(
        responseStep, new RequestParams("listDomain", namespace, null, null), LIST_DOMAIN);
  }

  private com.squareup.okhttp.Call readDomainAsync(
      ApiClient client, String name, String namespace, ApiCallback<Domain> callback)
      throws ApiException {
    return new WeblogicApi(client)
        .readWebLogicOracleV2NamespacedDomainAsync(
            name, namespace, pretty, exact, export, callback);
  }

  private final CallFactory<Domain> READ_DOMAIN =
      (requestParams, usage, cont, callback) ->
          wrap(readDomainAsync(usage, requestParams.name, requestParams.namespace, callback));

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
        responseStep, new RequestParams("readDomain", namespace, name, null), READ_DOMAIN);
  }

  private SynchronousCallFactory<Domain> REPLACE_DOMAIN_CALL =
      (client, requestParams) ->
          new WeblogicApi(client)
              .replaceWebLogicOracleV2NamespacedDomain(
                  requestParams.name, requestParams.namespace, (Domain) requestParams.body, pretty);

  /**
   * Replace domain.
   *
   * @param uid the domain uid (unique within the k8s cluster)
   * @param namespace Namespace
   * @param body Body
   * @param conflictRetry ConflictRetry implementation to be called to obtain the latest version of
   *     the Domain for retrying the replaceDomain synchronous call if previous call failed with
   *     Conflict response code (409)
   * @return Replaced domain
   * @throws ApiException APIException
   */
  public Domain replaceDomainWithConflictRetry(
      String uid, String namespace, Domain body, ConflictRetry<Domain> conflictRetry)
      throws ApiException {
    return executeSynchronousCallWithConflictRetry(
        new RequestParamsBuilder<Domain>(body) {

          @Override
          RequestParams buildRequestParams() {
            return new RequestParams("replaceDomain", namespace, uid, body);
          }
        },
        REPLACE_DOMAIN_CALL,
        conflictRetry);
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
    RequestParams requestParams = new RequestParams("replaceDomain", namespace, uid, body);
    return executeSynchronousCall(requestParams, REPLACE_DOMAIN_CALL);
  }

  private com.squareup.okhttp.Call replaceDomainAsync(
      ApiClient client, String name, String namespace, Domain body, ApiCallback<Domain> callback)
      throws ApiException {
    return new WeblogicApi(client)
        .replaceWebLogicOracleV2NamespacedDomainAsync(name, namespace, body, pretty, callback);
  }

  private final CallFactory<Domain> REPLACE_DOMAIN =
      (requestParams, usage, cont, callback) ->
          wrap(
              replaceDomainAsync(
                  usage,
                  requestParams.name,
                  requestParams.namespace,
                  (Domain) requestParams.body,
                  callback));

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
        responseStep, new RequestParams("replaceDomain", namespace, name, body), REPLACE_DOMAIN);
  }

  private com.squareup.okhttp.Call replaceDomainStatusAsync(
      ApiClient client, String name, String namespace, Domain body, ApiCallback<Domain> callback)
      throws ApiException {
    return new WeblogicApi(client)
        .replaceWebLogicOracleV2NamespacedDomainStatusAsync(
            name, namespace, body, pretty, callback);
  }

  private final CallFactory<Domain> REPLACE_DOMAIN_STATUS =
      (requestParams, usage, cont, callback) ->
          wrap(
              replaceDomainStatusAsync(
                  usage,
                  requestParams.name,
                  requestParams.namespace,
                  (Domain) requestParams.body,
                  callback));

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
        new RequestParams("replaceDomainStatus", namespace, name, body),
        REPLACE_DOMAIN_STATUS);
  }

  /* Custom Resource Definitions */

  private com.squareup.okhttp.Call readCustomResourceDefinitionAsync(
      ApiClient client, String name, ApiCallback<V1beta1CustomResourceDefinition> callback)
      throws ApiException {
    return new ApiextensionsV1beta1Api(client)
        .readCustomResourceDefinitionAsync(name, pretty, exact, export, callback);
  }

  private final CallFactory<V1beta1CustomResourceDefinition> READ_CRD =
      (requestParams, usage, cont, callback) ->
          wrap(readCustomResourceDefinitionAsync(usage, requestParams.name, callback));

  /**
   * Asynchronous step for reading CRD.
   *
   * @param name Name
   * @param responseStep Response step for when call completes
   * @return Asynchronous step
   */
  public Step readCustomResourceDefinitionAsync(
      String name, ResponseStep<V1beta1CustomResourceDefinition> responseStep) {
    return createRequestAsync(
        responseStep, new RequestParams("readCRD", null, name, null), READ_CRD);
  }

  private com.squareup.okhttp.Call createCustomResourceDefinitionAsync(
      ApiClient client,
      V1beta1CustomResourceDefinition body,
      ApiCallback<V1beta1CustomResourceDefinition> callback)
      throws ApiException {
    return new ApiextensionsV1beta1Api(client)
        .createCustomResourceDefinitionAsync(body, pretty, callback);
  }

  private final CallFactory<V1beta1CustomResourceDefinition> CREATE_CRD =
      (requestParams, usage, cont, callback) ->
          wrap(
              createCustomResourceDefinitionAsync(
                  usage, (V1beta1CustomResourceDefinition) requestParams.body, callback));

  /**
   * Asynchronous step for creating CRD.
   *
   * @param body Body
   * @param responseStep Response step for when call completes
   * @return Asynchronous step
   */
  public Step createCustomResourceDefinitionAsync(
      V1beta1CustomResourceDefinition body,
      ResponseStep<V1beta1CustomResourceDefinition> responseStep) {
    return createRequestAsync(
        responseStep, new RequestParams("createCRD", null, null, body), CREATE_CRD);
  }

  private com.squareup.okhttp.Call replaceCustomResourceDefinitionAsync(
      ApiClient client,
      String name,
      V1beta1CustomResourceDefinition body,
      ApiCallback<V1beta1CustomResourceDefinition> callback)
      throws ApiException {
    return new ApiextensionsV1beta1Api(client)
        .replaceCustomResourceDefinitionAsync(name, body, pretty, callback);
  }

  private final CallFactory<V1beta1CustomResourceDefinition> REPLACE_CRD =
      (requestParams, usage, cont, callback) ->
          wrap(
              replaceCustomResourceDefinitionAsync(
                  usage,
                  requestParams.name,
                  (V1beta1CustomResourceDefinition) requestParams.body,
                  callback));

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
      V1beta1CustomResourceDefinition body,
      ResponseStep<V1beta1CustomResourceDefinition> responseStep) {
    return createRequestAsync(
        responseStep, new RequestParams("replaceCRD", null, name, body), REPLACE_CRD);
  }

  /* Config Maps */

  private com.squareup.okhttp.Call readConfigMapAsync(
      ApiClient client, String name, String namespace, ApiCallback<V1ConfigMap> callback)
      throws ApiException {
    return new CoreV1Api(client)
        .readNamespacedConfigMapAsync(name, namespace, pretty, exact, export, callback);
  }

  private final CallFactory<V1ConfigMap> READ_CONFIGMAP =
      (requestParams, usage, cont, callback) ->
          wrap(readConfigMapAsync(usage, requestParams.name, requestParams.namespace, callback));

  /**
   * Asynchronous step for reading config map.
   *
   * @param name Name
   * @param namespace Namespace
   * @param responseStep Response step for when call completes
   * @return Asynchronous step
   */
  public Step readConfigMapAsync(
      String name, String namespace, ResponseStep<V1ConfigMap> responseStep) {
    return createRequestAsync(
        responseStep, new RequestParams("readConfigMap", namespace, name, null), READ_CONFIGMAP);
  }

  private com.squareup.okhttp.Call createConfigMapAsync(
      ApiClient client, String namespace, V1ConfigMap body, ApiCallback<V1ConfigMap> callback)
      throws ApiException {
    return new CoreV1Api(client).createNamespacedConfigMapAsync(namespace, body, pretty, callback);
  }

  private final CallFactory<V1ConfigMap> CREATE_CONFIGMAP =
      (requestParams, usage, cont, callback) ->
          wrap(
              createConfigMapAsync(
                  usage, requestParams.namespace, (V1ConfigMap) requestParams.body, callback));

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
        responseStep,
        new RequestParams("createConfigMap", namespace, null, body),
        CREATE_CONFIGMAP);
  }

  private com.squareup.okhttp.Call deleteConfigMapAsync(
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
            body,
            pretty,
            gracePeriodSeconds,
            orphanDependents,
            propagationPolicy,
            callback);
  }

  private final CallFactory<V1Status> DELETE_CONFIG_MAP =
      (requestParams, usage, cont, callback) ->
          wrap(
              deleteConfigMapAsync(
                  usage,
                  requestParams.name,
                  requestParams.namespace,
                  (V1DeleteOptions) requestParams.body,
                  callback));

  /**
   * Asynchronous step for deleting config map.
   *
   * @param name Name
   * @param namespace Namespace
   * @param deleteOptions Delete options
   * @param responseStep Response step for when call completes
   * @return Asynchronous step
   */
  public Step deleteConfigMapAsync(
      String name,
      String namespace,
      V1DeleteOptions deleteOptions,
      ResponseStep<V1Status> responseStep) {
    return createRequestAsync(
        responseStep,
        new RequestParams("deleteConfigMap", namespace, name, deleteOptions),
        DELETE_CONFIG_MAP);
  }

  private com.squareup.okhttp.Call replaceConfigMapAsync(
      ApiClient client,
      String name,
      String namespace,
      V1ConfigMap body,
      ApiCallback<V1ConfigMap> callback)
      throws ApiException {
    return new CoreV1Api(client)
        .replaceNamespacedConfigMapAsync(name, namespace, body, pretty, callback);
  }

  private final CallFactory<V1ConfigMap> REPLACE_CONFIGMAP =
      (requestParams, usage, cont, callback) ->
          wrap(
              replaceConfigMapAsync(
                  usage,
                  requestParams.name,
                  requestParams.namespace,
                  (V1ConfigMap) requestParams.body,
                  callback));

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
        new RequestParams("replaceConfigMap", namespace, name, body),
        REPLACE_CONFIGMAP);
  }

  /* Pods */

  private com.squareup.okhttp.Call listPodAsync(
      ApiClient client, String namespace, String cont, ApiCallback<V1PodList> callback)
      throws ApiException {
    return new CoreV1Api(client)
        .listNamespacedPodAsync(
            namespace,
            pretty,
            cont,
            fieldSelector,
            includeUninitialized,
            labelSelector,
            limit,
            resourceVersion,
            timeoutSeconds,
            watch,
            callback);
  }

  private final CallFactory<V1PodList> LIST_POD =
      (requestParams, usage, cont, callback) ->
          wrap(listPodAsync(usage, requestParams.namespace, cont, callback));

  /**
   * Asynchronous step for listing pods.
   *
   * @param namespace Namespace
   * @param responseStep Response step for when call completes
   * @return Asynchronous step
   */
  public Step listPodAsync(String namespace, ResponseStep<V1PodList> responseStep) {
    return createRequestAsync(
        responseStep, new RequestParams("listPod", namespace, null, null), LIST_POD);
  }

  private com.squareup.okhttp.Call readPodAsync(
      ApiClient client, String name, String namespace, ApiCallback<V1Pod> callback)
      throws ApiException {
    return new CoreV1Api(client)
        .readNamespacedPodAsync(name, namespace, pretty, exact, export, callback);
  }

  private final CallFactory<V1Pod> READ_POD =
      (requestParams, usage, cont, callback) ->
          wrap(readPodAsync(usage, requestParams.name, requestParams.namespace, callback));

  /**
   * Asynchronous step for reading pod.
   *
   * @param name Name
   * @param namespace Namespace
   * @param responseStep Response step for when call completes
   * @return Asynchronous step
   */
  public Step readPodAsync(String name, String namespace, ResponseStep<V1Pod> responseStep) {
    return createRequestAsync(
        responseStep, new RequestParams("readPod", namespace, name, null), READ_POD);
  }

  private com.squareup.okhttp.Call createPodAsync(
      ApiClient client, String namespace, V1Pod body, ApiCallback<V1Pod> callback)
      throws ApiException {
    return new CoreV1Api(client).createNamespacedPodAsync(namespace, body, pretty, callback);
  }

  private final CallFactory<V1Pod> CREATE_POD =
      (requestParams, usage, cont, callback) ->
          wrap(
              createPodAsync(usage, requestParams.namespace, (V1Pod) requestParams.body, callback));

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
        responseStep, new RequestParams("createPod", namespace, null, body), CREATE_POD);
  }

  private com.squareup.okhttp.Call deletePodAsync(
      ApiClient client,
      String name,
      String namespace,
      V1DeleteOptions deleteOptions,
      ApiCallback<V1Status> callback)
      throws ApiException {
    return new CoreV1Api(client)
        .deleteNamespacedPodAsync(
            name,
            namespace,
            deleteOptions,
            pretty,
            gracePeriodSeconds,
            orphanDependents,
            propagationPolicy,
            callback);
  }

  private final CallFactory<V1Status> DELETE_POD =
      (requestParams, usage, cont, callback) ->
          wrap(
              deletePodAsync(
                  usage,
                  requestParams.name,
                  requestParams.namespace,
                  (V1DeleteOptions) requestParams.body,
                  callback));

  /**
   * Asynchronous step for deleting pod.
   *
   * @param name Name
   * @param namespace Namespace
   * @param deleteOptions Delete options
   * @param responseStep Response step for when call completes
   * @return Asynchronous step
   */
  public Step deletePodAsync(
      String name,
      String namespace,
      V1DeleteOptions deleteOptions,
      ResponseStep<V1Status> responseStep) {
    return createRequestAsync(
        responseStep, new RequestParams("deletePod", namespace, name, deleteOptions), DELETE_POD);
  }

  private com.squareup.okhttp.Call patchPodAsync(
      ApiClient client, String name, String namespace, Object patch, ApiCallback<V1Pod> callback)
      throws ApiException {
    return new CoreV1Api(client).patchNamespacedPodAsync(name, namespace, patch, pretty, callback);
  }

  private final CallFactory<V1Pod> PATCH_POD =
      (requestParams, usage, cont, callback) ->
          wrap(
              patchPodAsync(
                  usage,
                  requestParams.name,
                  requestParams.namespace,
                  requestParams.body,
                  callback));

  /**
   * Asynchronous step for patching a pod.
   *
   * @param name Name
   * @param namespace Namespace
   * @param patchBody instructions on what to patch
   * @param responseStep Response step for when call completes
   * @return Asynchronous step
   */
  public Step patchPodAsync(
      String name, String namespace, JsonPatch patchBody, ResponseStep<V1Pod> responseStep) {
    return createRequestAsync(
        responseStep,
        new RequestParams("patchPod", namespace, name, PatchUtils.toKubernetesPatch(patchBody)),
        PATCH_POD);
  }

  private com.squareup.okhttp.Call deleteCollectionPodAsync(
      ApiClient client, String namespace, String cont, ApiCallback<V1Status> callback)
      throws ApiException {
    return new CoreV1Api(client)
        .deleteCollectionNamespacedPodAsync(
            namespace,
            pretty,
            cont,
            fieldSelector,
            includeUninitialized,
            labelSelector,
            limit,
            resourceVersion,
            timeoutSeconds,
            watch,
            callback);
  }

  private final CallFactory<V1Status> DELETECOLLECTION_POD =
      (requestParams, usage, cont, callback) ->
          wrap(deleteCollectionPodAsync(usage, requestParams.namespace, cont, callback));

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
        new RequestParams("deleteCollection", namespace, null, null),
        DELETECOLLECTION_POD);
  }

  /* Jobs */

  private com.squareup.okhttp.Call createJobAsync(
      ApiClient client, String namespace, V1Job body, ApiCallback<V1Job> callback)
      throws ApiException {
    return new BatchV1Api(client).createNamespacedJobAsync(namespace, body, pretty, callback);
  }

  private final CallFactory<V1Job> CREATE_JOB =
      (requestParams, usage, cont, callback) ->
          wrap(
              createJobAsync(usage, requestParams.namespace, (V1Job) requestParams.body, callback));

  /**
   * Asynchronous step for creating job.
   *
   * @param namespace Namespace
   * @param body Body
   * @param responseStep Response step for when call completes
   * @return Asynchronous step
   */
  public Step createJobAsync(String namespace, V1Job body, ResponseStep<V1Job> responseStep) {
    return createRequestAsync(
        responseStep, new RequestParams("createJob", namespace, null, body), CREATE_JOB);
  }

  private final CallFactory<V1Job> READ_JOB =
      (requestParams, usage, cont, callback) ->
          wrap(readJobAsync(usage, requestParams.name, requestParams.namespace, callback));

  private com.squareup.okhttp.Call readJobAsync(
      ApiClient client, String name, String namespace, ApiCallback<V1Job> callback)
      throws ApiException {
    return new BatchV1Api(client)
        .readNamespacedJobAsync(name, namespace, pretty, exact, export, callback);
  }

  /**
   * Asynchronous step for reading job.
   *
   * @param name Name
   * @param namespace Namespace
   * @param responseStep Response step for when call completes
   * @return Asynchronous step
   */
  public Step readJobAsync(String name, String namespace, ResponseStep<V1Job> responseStep) {
    return createRequestAsync(
        responseStep, new RequestParams("readJob", namespace, name, null), READ_JOB);
  }

  private com.squareup.okhttp.Call deleteJobAsync(
      ApiClient client,
      String name,
      String namespace,
      V1DeleteOptions body,
      ApiCallback<V1Status> callback)
      throws ApiException {
    return new BatchV1Api(client)
        .deleteNamespacedJobAsync(
            name,
            namespace,
            body,
            pretty,
            gracePeriodSeconds,
            orphanDependents,
            propagationPolicy,
            callback);
  }

  private final CallFactory<V1Status> DELETE_JOB =
      (requestParams, usage, cont, callback) ->
          wrap(
              deleteJobAsync(
                  usage,
                  requestParams.name,
                  requestParams.namespace,
                  (V1DeleteOptions) requestParams.body,
                  callback));

  /**
   * Asynchronous step for deleting job.
   *
   * @param name Name
   * @param namespace Namespace
   * @param deleteOptions Delete options
   * @param responseStep Response step for when call completes
   * @return Asynchronous step
   */
  public Step deleteJobAsync(
      String name,
      String namespace,
      V1DeleteOptions deleteOptions,
      ResponseStep<V1Status> responseStep) {
    return createRequestAsync(
        responseStep, new RequestParams("deleteJob", namespace, name, deleteOptions), DELETE_JOB);
  }

  /* Services */

  /**
   * List services.
   *
   * @param namespace Namespace
   * @return List of services
   * @throws ApiException API Exception
   */
  public V1ServiceList listService(String namespace) throws ApiException {
    String cont = "";
    ApiClient client = helper.take();
    try {
      return new CoreV1Api(client)
          .listNamespacedService(
              namespace,
              pretty,
              cont,
              fieldSelector,
              includeUninitialized,
              labelSelector,
              limit,
              resourceVersion,
              timeoutSeconds,
              watch);
    } finally {
      helper.recycle(client);
    }
  }

  private com.squareup.okhttp.Call listServiceAsync(
      ApiClient client, String namespace, String cont, ApiCallback<V1ServiceList> callback)
      throws ApiException {
    return new CoreV1Api(client)
        .listNamespacedServiceAsync(
            namespace,
            pretty,
            cont,
            fieldSelector,
            includeUninitialized,
            labelSelector,
            limit,
            resourceVersion,
            timeoutSeconds,
            watch,
            callback);
  }

  private final CallFactory<V1ServiceList> LIST_SERVICE =
      (requestParams, usage, cont, callback) ->
          wrap(listServiceAsync(usage, requestParams.namespace, cont, callback));

  /**
   * Asynchronous step for listing services.
   *
   * @param namespace Namespace
   * @param responseStep Response step for when call completes
   * @return Asynchronous step
   */
  public Step listServiceAsync(String namespace, ResponseStep<V1ServiceList> responseStep) {
    return createRequestAsync(
        responseStep, new RequestParams("listService", namespace, null, null), LIST_SERVICE);
  }

  /**
   * Read service.
   *
   * @param name Name
   * @param namespace Namespace
   * @return Read service
   * @throws ApiException API Exception
   */
  public V1Service readService(String name, String namespace) throws ApiException {
    ApiClient client = helper.take();
    try {
      return new CoreV1Api(client).readNamespacedService(name, namespace, pretty, exact, export);
    } finally {
      helper.recycle(client);
    }
  }

  private com.squareup.okhttp.Call readServiceAsync(
      ApiClient client, String name, String namespace, ApiCallback<V1Service> callback)
      throws ApiException {
    return new CoreV1Api(client)
        .readNamespacedServiceAsync(name, namespace, pretty, exact, export, callback);
  }

  private final CallFactory<V1Service> READ_SERVICE =
      (requestParams, usage, cont, callback) ->
          wrap(readServiceAsync(usage, requestParams.name, requestParams.namespace, callback));

  /**
   * Asynchronous step for reading service.
   *
   * @param name Name
   * @param namespace Namespace
   * @param responseStep Response step for when call completes
   * @return Asynchronous step
   */
  public Step readServiceAsync(
      String name, String namespace, ResponseStep<V1Service> responseStep) {
    return createRequestAsync(
        responseStep, new RequestParams("readService", namespace, name, null), READ_SERVICE);
  }

  private com.squareup.okhttp.Call createServiceAsync(
      ApiClient client, String namespace, V1Service body, ApiCallback<V1Service> callback)
      throws ApiException {
    return new CoreV1Api(client).createNamespacedServiceAsync(namespace, body, pretty, callback);
  }

  private final CallFactory<V1Service> CREATE_SERVICE =
      (requestParams, usage, cont, callback) ->
          wrap(
              createServiceAsync(
                  usage, requestParams.namespace, (V1Service) requestParams.body, callback));

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
        responseStep, new RequestParams("createService", namespace, null, body), CREATE_SERVICE);
  }

  /**
   * Delete service.
   *
   * @param name Name
   * @param namespace Namespace
   * @param deleteOptions Delete options
   * @return Status of deletion
   * @throws ApiException API Exception
   */
  public V1Status deleteService(String name, String namespace, V1DeleteOptions deleteOptions)
      throws ApiException {
    ApiClient client = helper.take();
    try {
      return new CoreV1Api(client)
          .deleteNamespacedService(
              name,
              namespace,
              deleteOptions,
              pretty,
              gracePeriodSeconds,
              orphanDependents,
              propagationPolicy);
    } finally {
      helper.recycle(client);
    }
  }

  private com.squareup.okhttp.Call deleteServiceAsync(
      ApiClient client,
      String name,
      String namespace,
      V1DeleteOptions deleteOptions,
      ApiCallback<V1Status> callback)
      throws ApiException {
    return new CoreV1Api(client)
        .deleteNamespacedServiceAsync(
            name,
            namespace,
            deleteOptions,
            pretty,
            gracePeriodSeconds,
            orphanDependents,
            propagationPolicy,
            callback);
  }

  private final CallFactory<V1Status> DELETE_SERVICE =
      (requestParams, usage, cont, callback) ->
          wrap(
              deleteServiceAsync(
                  usage,
                  requestParams.name,
                  requestParams.namespace,
                  (V1DeleteOptions) requestParams.body,
                  callback));

  /**
   * Asynchronous step for deleting service.
   *
   * @param name Name
   * @param namespace Namespace
   * @param deleteOptions Delete options
   * @param responseStep Response step for when call completes
   * @return Asynchronous step
   */
  public Step deleteServiceAsync(
      String name,
      String namespace,
      V1DeleteOptions deleteOptions,
      ResponseStep<V1Status> responseStep) {
    return createRequestAsync(
        responseStep,
        new RequestParams("deleteService", namespace, name, deleteOptions),
        DELETE_SERVICE);
  }

  /* Events */

  private com.squareup.okhttp.Call listEventAsync(
      ApiClient client, String namespace, String cont, ApiCallback<V1EventList> callback)
      throws ApiException {
    return new CoreV1Api(client)
        .listNamespacedEventAsync(
            namespace,
            pretty,
            cont,
            fieldSelector,
            includeUninitialized,
            labelSelector,
            limit,
            resourceVersion,
            timeoutSeconds,
            watch,
            callback);
  }

  private final CallFactory<V1EventList> LIST_EVENT =
      (requestParams, usage, cont, callback) ->
          wrap(listEventAsync(usage, requestParams.namespace, cont, callback));

  /**
   * Asynchronous step for listing events.
   *
   * @param namespace Namespace
   * @param responseStep Response step for when call completes
   * @return Asynchronous step
   */
  public Step listEventAsync(String namespace, ResponseStep<V1EventList> responseStep) {
    return createRequestAsync(
        responseStep, new RequestParams("listEvent", namespace, null, null), LIST_EVENT);
  }

  /* Persistent Volumes */

  private com.squareup.okhttp.Call listPersistentVolumeAsync(
      ApiClient client, String cont, ApiCallback<V1PersistentVolumeList> callback)
      throws ApiException {
    return new CoreV1Api(client)
        .listPersistentVolumeAsync(
            pretty,
            cont,
            fieldSelector,
            includeUninitialized,
            labelSelector,
            limit,
            resourceVersion,
            timeoutSeconds,
            watch,
            callback);
  }

  private final CallFactory<V1PersistentVolumeList> LIST_PERSISTENTVOLUME =
      (requestParams, usage, cont, callback) ->
          wrap(listPersistentVolumeAsync(usage, cont, callback));

  /**
   * Asynchronous step for listing persistent volumes.
   *
   * @param responseStep Response step for when call completes
   * @return Asynchronous step
   */
  public Step listPersistentVolumeAsync(ResponseStep<V1PersistentVolumeList> responseStep) {
    return createRequestAsync(
        responseStep,
        new RequestParams("listPersistentVolume", null, null, null),
        LIST_PERSISTENTVOLUME);
  }

  private SynchronousCallFactory<V1PersistentVolume> CREATE_PV_CALL =
      (client, requestParams) ->
          new CoreV1Api(client)
              .createPersistentVolume((V1PersistentVolume) requestParams.body, pretty);

  public V1PersistentVolume createPersistentVolume(V1PersistentVolume volume) throws ApiException {
    RequestParams requestParams = new RequestParams("createPV", null, null, volume);
    return executeSynchronousCall(requestParams, CREATE_PV_CALL);
  }

  private final CallFactory<V1PersistentVolume> CREATE_PERSISTENTVOLUME =
      ((requestParams, client, cont, callback) ->
          wrap(
              new CoreV1Api(client)
                  .createPersistentVolumeAsync(
                      (V1PersistentVolume) requestParams.body, pretty, callback)));

  /**
   * Asynchronous step for creating persistent volumes.
   *
   * @param persistentVolume a resource describing the volume to create
   * @param responseStep the step to invoke when the call completes
   * @return a new asynchronous step
   */
  public Step createPersistentVolumeAsync(
      V1PersistentVolume persistentVolume, ResponseStep<V1PersistentVolume> responseStep) {
    return createRequestAsync(
        responseStep,
        new RequestParams("createPersistentVolume", null, null, persistentVolume),
        CREATE_PERSISTENTVOLUME);
  }

  private SynchronousCallFactory<V1Status> DELETE_PV_CALL =
      (client, requestParams) ->
          new CoreV1Api(client)
              .deletePersistentVolume(
                  requestParams.name,
                  (V1DeleteOptions) requestParams.body,
                  pretty,
                  gracePeriodSeconds,
                  orphanDependents,
                  propagationPolicy);

  public V1Status deletePersistentVolume(String name, V1DeleteOptions deleteOptions)
      throws ApiException {
    RequestParams requestParams =
        new RequestParams("deletePersistentVolume", null, name, deleteOptions);
    return executeSynchronousCall(requestParams, DELETE_PV_CALL);
  }

  private final CallFactory<V1Status> DELETE_PERSISTENTVOLUME =
      (requestParams, client, cont, callback) ->
          wrap(
              new CoreV1Api(client)
                  .deletePersistentVolumeAsync(
                      requestParams.name,
                      (V1DeleteOptions) requestParams.body,
                      pretty,
                      gracePeriodSeconds,
                      orphanDependents,
                      propagationPolicy,
                      callback));

  /**
   * Asynchronous step for deleting persistent volumes.
   *
   * @param name the name of the volume to delete
   * @param deleteOptions options to control deletion
   * @param responseStep the step to invoke when the call completes
   * @return a new asynchronous step
   */
  public Step deletePersistentVolumeAsync(
      String name, V1DeleteOptions deleteOptions, ResponseStep<V1Status> responseStep) {
    return createRequestAsync(
        responseStep,
        new RequestParams("deletePersistentVolume", null, name, deleteOptions),
        DELETE_PERSISTENTVOLUME);
  }

  /* Persistent Volume Claims */

  private com.squareup.okhttp.Call listPersistentVolumeClaimAsync(
      ApiClient client,
      String namespace,
      String cont,
      ApiCallback<V1PersistentVolumeClaimList> callback)
      throws ApiException {
    return new CoreV1Api(client)
        .listNamespacedPersistentVolumeClaimAsync(
            namespace,
            pretty,
            cont,
            fieldSelector,
            includeUninitialized,
            labelSelector,
            limit,
            resourceVersion,
            timeoutSeconds,
            watch,
            callback);
  }

  private final CallFactory<V1PersistentVolumeClaimList> LIST_PERSISTENTVOLUMECLAIM =
      (requestParams, usage, cont, callback) ->
          wrap(listPersistentVolumeClaimAsync(usage, requestParams.namespace, cont, callback));

  /**
   * Asynchronous step for listing persistent volume claims.
   *
   * @param namespace Namespace
   * @param responseStep Response step for when call completes
   * @return Asynchronous step
   */
  public Step listPersistentVolumeClaimAsync(
      String namespace, ResponseStep<V1PersistentVolumeClaimList> responseStep) {
    return createRequestAsync(
        responseStep,
        new RequestParams("listPersistentVolumeClaim", namespace, null, null),
        LIST_PERSISTENTVOLUMECLAIM);
  }

  private SynchronousCallFactory<V1PersistentVolumeClaim> CREATE_PVC_CALL =
      (client, requestParams) ->
          new CoreV1Api(client)
              .createNamespacedPersistentVolumeClaim(
                  requestParams.namespace, (V1PersistentVolumeClaim) requestParams.body, pretty);

  public V1PersistentVolumeClaim createPersistentVolumeClaim(V1PersistentVolumeClaim claim)
      throws ApiException {
    RequestParams requestParams = new RequestParams("createPVC", getNamespace(claim), null, claim);
    return executeSynchronousCall(requestParams, CREATE_PVC_CALL);
  }

  protected String getNamespace(V1PersistentVolumeClaim claim) {
    return claim.getMetadata().getNamespace();
  }

  private final CallFactory<V1PersistentVolumeClaim> CREATE_PERSISTENTVOLUMECLAIM =
      (requestParams, client, cont, callback) ->
          wrap(
              new CoreV1Api(client)
                  .createNamespacedPersistentVolumeClaimAsync(
                      requestParams.namespace,
                      (V1PersistentVolumeClaim) requestParams.body,
                      pretty,
                      callback));

  public Step createPersistentVolumeClaimAsync(
      V1PersistentVolumeClaim claim, ResponseStep<V1PersistentVolumeClaim> responseStep) {
    return createRequestAsync(
        responseStep,
        new RequestParams("createPersistentVolumeClaim", getNamespace(claim), null, claim),
        CREATE_PERSISTENTVOLUMECLAIM);
  }

  private SynchronousCallFactory<V1Status> DELETE_PVC_CALL =
      (client, requestParams) ->
          new CoreV1Api(client)
              .deleteNamespacedPersistentVolumeClaim(
                  requestParams.name,
                  requestParams.namespace,
                  (V1DeleteOptions) requestParams.body,
                  pretty,
                  gracePeriodSeconds,
                  orphanDependents,
                  propagationPolicy);

  public V1Status deletePersistentVolumeClaim(
      String name, String namespace, V1DeleteOptions deleteOptions) throws ApiException {
    return executeSynchronousCall(
        new RequestParams("deletePVC", namespace, name, deleteOptions), DELETE_PVC_CALL);
  }

  private final CallFactory<V1Status> DELETE_PERSISTENTVOLUMECLAIM =
      (requestParams, client, cont, callback) ->
          wrap(
              new CoreV1Api(client)
                  .deleteNamespacedPersistentVolumeClaimAsync(
                      requestParams.name,
                      requestParams.namespace,
                      (V1DeleteOptions) requestParams.body,
                      pretty,
                      gracePeriodSeconds,
                      orphanDependents,
                      propagationPolicy,
                      callback));

  public Step deletePersistentVolumeClaimAsync(
      String name,
      String namespace,
      V1DeleteOptions deleteOptions,
      ResponseStep<V1Status> responseStep) {
    return createRequestAsync(
        responseStep,
        new RequestParams("deletePersistentVolumeClaim", namespace, name, deleteOptions),
        DELETE_PERSISTENTVOLUMECLAIM);
  }

  /* Secrets */

  /**
   * Read secret.
   *
   * @param name Name
   * @param namespace Namespace
   * @return Read secret
   * @throws ApiException API Exception
   */
  public V1Secret readSecret(String name, String namespace) throws ApiException {
    ApiClient client = helper.take();
    try {
      return new CoreV1Api(client).readNamespacedSecret(name, namespace, pretty, exact, export);
    } finally {
      helper.recycle(client);
    }
  }

  private com.squareup.okhttp.Call readSecretAsync(
      ApiClient client, String name, String namespace, ApiCallback<V1Secret> callback)
      throws ApiException {
    return new CoreV1Api(client)
        .readNamespacedSecretAsync(name, namespace, pretty, exact, export, callback);
  }

  private final CallFactory<V1Secret> READ_SECRET =
      (requestParams, usage, cont, callback) ->
          wrap(readSecretAsync(usage, requestParams.name, requestParams.namespace, callback));

  /**
   * Create secret.
   *
   * @param namespace Namespace
   * @param body Body
   * @return Created secret
   * @throws ApiException API Exception
   */
  public V1Secret createSecret(String namespace, V1Secret body) throws ApiException {
    ApiClient client = helper.take();
    try {
      return new CoreV1Api(client).createNamespacedSecret(namespace, body, pretty);
    } finally {
      helper.recycle(client);
    }
  }

  /**
   * Delete secret.
   *
   * @param name Name
   * @param namespace Namespace
   * @param deleteOptions Delete options
   * @return Status of deletion
   * @throws ApiException API Exception
   */
  public V1Status deleteSecret(String name, String namespace, V1DeleteOptions deleteOptions)
      throws ApiException {
    ApiClient client = helper.take();
    try {
      return new CoreV1Api(client)
          .deleteNamespacedSecret(
              name,
              namespace,
              deleteOptions,
              pretty,
              gracePeriodSeconds,
              orphanDependents,
              propagationPolicy);
    } finally {
      helper.recycle(client);
    }
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
        responseStep, new RequestParams("readSecret", namespace, name, null), READ_SECRET);
  }

  /* Subject Access Review */

  private SynchronousCallFactory<V1SubjectAccessReview> CREATE_SUBJECTACCESSREVIEW_CALL =
      ((client, requestParams) ->
          new AuthorizationV1Api(client)
              .createSubjectAccessReview((V1SubjectAccessReview) requestParams.body, pretty));

  /**
   * Create subject access review.
   *
   * @param body Body
   * @return Created subject access review
   * @throws ApiException API Exception
   */
  public V1SubjectAccessReview createSubjectAccessReview(V1SubjectAccessReview body)
      throws ApiException {
    RequestParams params = new RequestParams("createSubjectAccessReview", null, null, body);
    return executeSynchronousCall(params, CREATE_SUBJECTACCESSREVIEW_CALL);
  }

  private com.squareup.okhttp.Call createSubjectAccessReviewAsync(
      ApiClient client, V1SubjectAccessReview body, ApiCallback<V1SubjectAccessReview> callback)
      throws ApiException {
    return new AuthorizationV1Api(client).createSubjectAccessReviewAsync(body, pretty, callback);
  }

  private final CallFactory<V1SubjectAccessReview> CREATE_SUBJECTACCESSREVIEW =
      (requestParams, usage, cont, callback) ->
          wrap(
              createSubjectAccessReviewAsync(
                  usage, (V1SubjectAccessReview) requestParams.body, callback));

  /**
   * Asynchronous step for creating subject access review.
   *
   * @param body Body
   * @param responseStep Response step for when call completes
   * @return Asynchronous step
   */
  public Step createSubjectAccessReviewAsync(
      V1SubjectAccessReview body, ResponseStep<V1SubjectAccessReview> responseStep) {
    return createRequestAsync(
        responseStep,
        new RequestParams("createSubjectAccessReview", null, null, body),
        CREATE_SUBJECTACCESSREVIEW);
  }

  /* Self Subject Access Review */

  private SynchronousCallFactory<V1SelfSubjectAccessReview> CREATE_SELFSUBJECTACESSREVIEW_CALL =
      (client, requestParams) ->
          new AuthorizationV1Api(client)
              .createSelfSubjectAccessReview(
                  (V1SelfSubjectAccessReview) requestParams.body, pretty);

  /**
   * Create self subject access review.
   *
   * @param body Body
   * @return Created self subject access review
   * @throws ApiException API Exception
   */
  public V1SelfSubjectAccessReview createSelfSubjectAccessReview(V1SelfSubjectAccessReview body)
      throws ApiException {
    RequestParams requestParams = new RequestParams("selfSubjectAccessReview", null, null, body);
    return executeSynchronousCall(requestParams, CREATE_SELFSUBJECTACESSREVIEW_CALL);
  }

  private com.squareup.okhttp.Call createSelfSubjectAccessReviewAsync(
      ApiClient client,
      V1SelfSubjectAccessReview body,
      ApiCallback<V1SelfSubjectAccessReview> callback)
      throws ApiException {
    return new AuthorizationV1Api(client)
        .createSelfSubjectAccessReviewAsync(body, pretty, callback);
  }

  private final CallFactory<V1SelfSubjectAccessReview> CREATE_SELFSUBJECTACCESSREVIEW =
      (requestParams, usage, cont, callback) ->
          wrap(
              createSelfSubjectAccessReviewAsync(
                  usage, (V1SelfSubjectAccessReview) requestParams.body, callback));

  /**
   * Asynchronous step for creating self subject access review.
   *
   * @param body Body
   * @param responseStep Response step for when call completes
   * @return Asynchronous step
   */
  public Step createSelfSubjectAccessReviewAsync(
      V1SelfSubjectAccessReview body, ResponseStep<V1SelfSubjectAccessReview> responseStep) {
    return createRequestAsync(
        responseStep,
        new RequestParams("createSelfSubjectAccessReview", null, null, body),
        CREATE_SELFSUBJECTACCESSREVIEW);
  }

  /* Self Subject Rules Review */

  private SynchronousCallFactory<V1SelfSubjectRulesReview> CREATE_SELFSUBJECTRULESREVIEW_CALL =
      (client, requestParams) ->
          new AuthorizationV1Api(client)
              .createSelfSubjectRulesReview((V1SelfSubjectRulesReview) requestParams.body, pretty);

  /**
   * Create self subject rules review.
   *
   * @param body Body
   * @return Created self subject rules review
   * @throws ApiException API Exception
   */
  public V1SelfSubjectRulesReview createSelfSubjectRulesReview(V1SelfSubjectRulesReview body)
      throws ApiException {
    RequestParams params = new RequestParams("selfSubjectRulesReview", null, null, body);
    return executeSynchronousCall(params, CREATE_SELFSUBJECTRULESREVIEW_CALL);
  }

  private com.squareup.okhttp.Call createSelfSubjectRulesReviewAsync(
      ApiClient client,
      V1SelfSubjectRulesReview body,
      ApiCallback<V1SelfSubjectRulesReview> callback)
      throws ApiException {
    return new AuthorizationV1Api(client).createSelfSubjectRulesReviewAsync(body, pretty, callback);
  }

  private final CallFactory<V1SelfSubjectRulesReview> CREATE_SELFSUBJECTRULESREVIEW =
      (requestParams, usage, cont, callback) ->
          wrap(
              createSelfSubjectRulesReviewAsync(
                  usage, (V1SelfSubjectRulesReview) requestParams.body, callback));

  /**
   * Asynchronous step for creating self subject rules review.
   *
   * @param body Body
   * @param responseStep Response step for when call completes
   * @return Asynchronous step
   */
  public Step createSelfSubjectRulesReviewAsync(
      V1SelfSubjectRulesReview body, ResponseStep<V1SelfSubjectRulesReview> responseStep) {
    return createRequestAsync(
        responseStep,
        new RequestParams("createSelfSubjectRulesReview", null, null, body),
        CREATE_SELFSUBJECTRULESREVIEW);
  }

  /* Token Review */

  private SynchronousCallFactory<V1TokenReview> CREATE_TOKEN_REVIEW_CALL =
      (client, requestParams) ->
          new AuthenticationV1Api(client)
              .createTokenReview((V1TokenReview) requestParams.body, pretty);

  /**
   * Create token review.
   *
   * @param body Body
   * @return Created token review
   * @throws ApiException API Exception
   */
  public V1TokenReview createTokenReview(V1TokenReview body) throws ApiException {
    RequestParams requestParams = new RequestParams("createTokenReview", null, null, body);
    return executeSynchronousCall(requestParams, CREATE_TOKEN_REVIEW_CALL);
  }

  private final CallFactory<String> READ_POD_LOG =
      (requestParams, usage, cont, callback) ->
          wrap(
              readPodLogAsync(
                  usage,
                  requestParams.name,
                  requestParams.namespace,
                  null,
                  null,
                  null,
                  pretty,
                  null,
                  null,
                  null,
                  null,
                  callback));

  public Step readPodLogAsync(String name, String namespace, ResponseStep<String> responseStep) {
    return createRequestAsync(
        responseStep, new RequestParams("readPodLog", namespace, name, null), READ_POD_LOG);
  }

  private com.squareup.okhttp.Call readPodLogAsync(
      ApiClient client,
      String name,
      String namespace,
      String container,
      Boolean follow,
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
            limitBytes,
            pretty,
            previous,
            sinceSeconds,
            tailLines,
            timestamps,
            callback);
  }

  static AsyncRequestStepFactory setStepFactory(AsyncRequestStepFactory newFactory) {
    AsyncRequestStepFactory oldFactory = STEP_FACTORY;
    STEP_FACTORY = newFactory;
    return oldFactory;
  }

  static void resetStepFactory() {
    STEP_FACTORY = DEFAULT_STEP_FACTORY;
  }

  private static AsyncRequestStepFactory DEFAULT_STEP_FACTORY = AsyncRequestStep::new;

  private static AsyncRequestStepFactory STEP_FACTORY = DEFAULT_STEP_FACTORY;

  private <T> Step createRequestAsync(
      ResponseStep<T> next, RequestParams requestParams, CallFactory<T> factory) {
    return STEP_FACTORY.createRequestAsync(
        next,
        requestParams,
        factory,
        helper,
        timeoutSeconds,
        maxRetryCount,
        fieldSelector,
        labelSelector,
        resourceVersion);
  }

  private CancellableCall wrap(Call call) {
    return new CallWrapper(call);
  }
}

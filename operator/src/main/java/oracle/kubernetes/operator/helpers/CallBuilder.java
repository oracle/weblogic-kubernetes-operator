// Copyright (c) 2017, 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helpers;

import java.util.Optional;
import java.util.function.Consumer;

import io.kubernetes.client.custom.V1Patch;
import io.kubernetes.client.openapi.ApiCallback;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.apis.ApiextensionsV1beta1Api;
import io.kubernetes.client.openapi.apis.AuthenticationV1Api;
import io.kubernetes.client.openapi.apis.AuthorizationV1Api;
import io.kubernetes.client.openapi.apis.BatchV1Api;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.apis.VersionApi;
import io.kubernetes.client.openapi.models.V1ConfigMap;
import io.kubernetes.client.openapi.models.V1DeleteOptions;
import io.kubernetes.client.openapi.models.V1EventList;
import io.kubernetes.client.openapi.models.V1Job;
import io.kubernetes.client.openapi.models.V1Namespace;
import io.kubernetes.client.openapi.models.V1NamespaceList;
import io.kubernetes.client.openapi.models.V1PersistentVolume;
import io.kubernetes.client.openapi.models.V1PersistentVolumeClaim;
import io.kubernetes.client.openapi.models.V1Pod;
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
import io.kubernetes.client.openapi.models.V1beta1CustomResourceDefinition;
import io.kubernetes.client.openapi.models.VersionInfo;
import okhttp3.Call;
import oracle.kubernetes.operator.TuningParameters;
import oracle.kubernetes.operator.TuningParameters.CallBuilderTuning;
import oracle.kubernetes.operator.calls.AsyncRequestStep;
import oracle.kubernetes.operator.calls.CallFactory;
import oracle.kubernetes.operator.calls.CallWrapper;
import oracle.kubernetes.operator.calls.CancellableCall;
import oracle.kubernetes.operator.calls.RequestParams;
import oracle.kubernetes.operator.calls.SynchronousCallDispatcher;
import oracle.kubernetes.operator.calls.SynchronousCallFactory;
import oracle.kubernetes.operator.work.Step;
import oracle.kubernetes.weblogic.domain.api.WeblogicApi;
import oracle.kubernetes.weblogic.domain.model.Domain;
import oracle.kubernetes.weblogic.domain.model.DomainList;

/** Simplifies synchronous and asynchronous call patterns to the Kubernetes API Server. */
@SuppressWarnings({"WeakerAccess", "UnusedReturnValue"})
public class CallBuilder {

  /** HTTP status code for "Not Found". */
  public static final int NOT_FOUND = 404;

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

  private static SynchronousCallDispatcher DISPATCHER = DEFAULT_DISPATCHER;
  private static final AsyncRequestStepFactory DEFAULT_STEP_FACTORY = AsyncRequestStep::new;
  private static AsyncRequestStepFactory STEP_FACTORY = DEFAULT_STEP_FACTORY;
  private final ClientPool helper;
  private final Boolean allowWatchBookmarks = false;
  private final String dryRun = null;
  private final String pretty = "false";
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
  private final CallFactory<V1beta1CustomResourceDefinition> createCrd =
      (requestParams, usage, cont, callback) ->
          wrap(
              createCustomResourceDefinitionAsync(
                  usage, (V1beta1CustomResourceDefinition) requestParams.body, callback));
  private final CallFactory<V1beta1CustomResourceDefinition> replaceCrd =
      (requestParams, usage, cont, callback) ->
          wrap(
              replaceCustomResourceDefinitionAsync(
                  usage,
                  requestParams.name,
                  (V1beta1CustomResourceDefinition) requestParams.body,
                  callback));
  private final CallFactory<V1ConfigMap> createConfigmap =
      (requestParams, usage, cont, callback) ->
          wrap(
              createConfigMapAsync(
                  usage, requestParams.namespace, (V1ConfigMap) requestParams.body, callback));
  private final CallFactory<V1ConfigMap> replaceConfigmap =
      (requestParams, usage, cont, callback) ->
          wrap(
              replaceConfigMapAsync(
                  usage,
                  requestParams.name,
                  requestParams.namespace,
                  (V1ConfigMap) requestParams.body,
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
  private final CallFactory<V1PersistentVolume> createPersistentvolume =
      ((requestParams, client, cont, callback) ->
          wrap(
              new CoreV1Api(client)
                  .createPersistentVolumeAsync(
                      (V1PersistentVolume) requestParams.body, pretty, null, null, callback)));
  private final CallFactory<V1PersistentVolumeClaim> createPersistentvolumeclaim =
      (requestParams, client, cont, callback) ->
          wrap(
              new CoreV1Api(client)
                  .createNamespacedPersistentVolumeClaimAsync(
                      requestParams.namespace,
                      (V1PersistentVolumeClaim) requestParams.body,
                      pretty,
                      null,
                      null,
                      callback));
  private final CallFactory<V1SubjectAccessReview> createSubjectaccessreview =
      (requestParams, usage, cont, callback) ->
          wrap(
              createSubjectAccessReviewAsync(
                  usage, (V1SubjectAccessReview) requestParams.body, callback));
  private final CallFactory<V1SelfSubjectRulesReview> createSelfsubjectrulesreview =
      (requestParams, usage, cont, callback) ->
          wrap(
              createSelfSubjectRulesReviewAsync(
                  usage, (V1SelfSubjectRulesReview) requestParams.body, callback));
  private final CallFactory<String> readPodLog =
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
  private String fieldSelector;

  /* Version */
  private String labelSelector;
  private Integer limit = 500;

  /* Namespaces */
  private final String resourceVersion = "";
  private Integer timeoutSeconds = 5;

  /* Domains */
  private Integer maxRetryCount = 10;
  private final Boolean watch = Boolean.FALSE;
  private final CallFactory<DomainList> listDomain =
      (requestParams, usage, cont, callback) ->
          wrap(listDomainAsync(usage, requestParams.namespace, cont, callback));
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
  private final CallFactory<V1EventList> listEvent =
      (requestParams, usage, cont, callback) ->
          wrap(listEventAsync(usage, requestParams.namespace, cont, callback));
  private final CallFactory<V1NamespaceList> listNamespace =
      (requestParams, usage, cont, callback) ->
          wrap(listNamespaceAsync(usage, cont, callback));
  private final Boolean exact = Boolean.FALSE;
  private final Boolean export = Boolean.FALSE;
  private final CallFactory<Domain> readDomain =
      (requestParams, usage, cont, callback) ->
          wrap(readDomainAsync(usage, requestParams.name, requestParams.namespace, callback));
  private final CallFactory<V1beta1CustomResourceDefinition> readCrd =
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
  private final Integer gracePeriodSeconds = null;
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
  private final CallFactory<V1Status> deletePod =
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
                  (V1DeleteOptions) requestParams.body,
                  callback));
  private final CallFactory<V1Status> deleteService =
      (requestParams, usage, cont, callback) ->
          wrap(
              deleteServiceAsync(
                  usage,
                  requestParams.name,
                  requestParams.namespace,
                  (V1DeleteOptions) requestParams.body,
                  callback));
  private final CallFactory<V1Status> deletePersistentvolume =
      (requestParams, client, cont, callback) ->
          wrap(
              new CoreV1Api(client)
                  .deletePersistentVolumeAsync(
                      requestParams.name,
                      pretty,
                      dryRun,
                      gracePeriodSeconds,
                      orphanDependents,
                      propagationPolicy,
                      (V1DeleteOptions) requestParams.body,
                      callback));
  private final CallFactory<V1Status> deletePersistentvolumeclaim =
      (requestParams, client, cont, callback) ->
          wrap(
              new CoreV1Api(client)
                  .deleteNamespacedPersistentVolumeClaimAsync(
                      requestParams.name,
                      requestParams.namespace,
                      pretty,
                      dryRun,
                      gracePeriodSeconds,
                      orphanDependents,
                      propagationPolicy,
                      (V1DeleteOptions) requestParams.body,
                      callback));
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
  private final SynchronousCallFactory<Domain> replaceDomainCall =
      (client, requestParams) ->
          new WeblogicApi(client)
              .replaceNamespacedDomain(
                  requestParams.name,
                  requestParams.namespace,
                  (Domain) requestParams.body);
  private final SynchronousCallFactory<Domain> patchDomainCall =
      (client, requestParams) ->
          new WeblogicApi(client)
              .patchNamespacedDomain(
                  requestParams.name, requestParams.namespace, (V1Patch) requestParams.body);

  /* Config Maps */
  private SynchronousCallFactory<V1PersistentVolume> createPvCall =
      (client, requestParams) ->
          new CoreV1Api(client)
              .createPersistentVolume((V1PersistentVolume) requestParams.body, pretty, null, null);
  private SynchronousCallFactory<V1Status> deletePvCall =
      (client, requestParams) ->
          new CoreV1Api(client)
              .deletePersistentVolume(
                  requestParams.name,
                  pretty,
                  dryRun,
                  gracePeriodSeconds,
                  orphanDependents,
                  propagationPolicy,
                  (V1DeleteOptions) requestParams.body);
  private SynchronousCallFactory<V1PersistentVolumeClaim> createPvcCall =
      (client, requestParams) ->
          new CoreV1Api(client)
              .createNamespacedPersistentVolumeClaim(
                  requestParams.namespace,
                  (V1PersistentVolumeClaim) requestParams.body,
                  pretty,
                  null,
                  null);
  private SynchronousCallFactory<V1Status> deletePvcCall =
      (client, requestParams) ->
          new CoreV1Api(client)
              .deleteNamespacedPersistentVolumeClaim(
                  requestParams.name,
                  requestParams.namespace,
                  pretty,
                  dryRun,
                  gracePeriodSeconds,
                  orphanDependents,
                  propagationPolicy,
                  (V1DeleteOptions) requestParams.body);
  private final SynchronousCallFactory<V1SubjectAccessReview> createSubjectaccessreviewCall =
      ((client, requestParams) ->
          new AuthorizationV1Api(client)
              .createSubjectAccessReview(
                  (V1SubjectAccessReview) requestParams.body, null, null, pretty));
  private final SynchronousCallFactory<V1SelfSubjectAccessReview> createSelfsubjectacessreviewCall =
      (client, requestParams) ->
          new AuthorizationV1Api(client)
              .createSelfSubjectAccessReview(
                  (V1SelfSubjectAccessReview) requestParams.body, null, null, pretty);
  private final SynchronousCallFactory<V1SelfSubjectRulesReview> createSelfsubjectrulesreviewCall =
      (client, requestParams) ->
          new AuthorizationV1Api(client)
              .createSelfSubjectRulesReview(
                  (V1SelfSubjectRulesReview) requestParams.body, null, null, pretty);
  private final SynchronousCallFactory<V1TokenReview> createTokenReviewCall =
      (client, requestParams) ->
          new AuthenticationV1Api(client)
              .createTokenReview((V1TokenReview) requestParams.body, null, null, pretty);

  public CallBuilder() {
    this(getCallBuilderTuning(), ClientPool.getInstance());
  }

  private CallBuilder(CallBuilderTuning tuning, ClientPool helper) {
    if (tuning != null) {
      tuning(tuning.callRequestLimit, tuning.callTimeoutSeconds, tuning.callMaxRetryCount);
    }
    this.helper = helper;
  }

  private static CallBuilderTuning getCallBuilderTuning() {
    return Optional.ofNullable(TuningParameters.getInstance())
        .map(TuningParameters::getCallBuilderTuning)
        .orElse(null);
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

  /* Pods */

  static SynchronousCallDispatcher setCallDispatcher(SynchronousCallDispatcher newDispatcher) {
    SynchronousCallDispatcher oldDispatcher = DISPATCHER;
    DISPATCHER = newDispatcher;
    return oldDispatcher;
  }

  static void resetCallDispatcher() {
    DISPATCHER = DEFAULT_DISPATCHER;
  }

  static AsyncRequestStepFactory setStepFactory(AsyncRequestStepFactory newFactory) {
    AsyncRequestStepFactory oldFactory = STEP_FACTORY;
    STEP_FACTORY = newFactory;
    return oldFactory;
  }

  static void resetStepFactory() {
    STEP_FACTORY = DEFAULT_STEP_FACTORY;
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
   * Consumer for lambda-based builder pattern.
   *
   * @param builderFunction Builder lambda function
   * @return this CallBuilder
   */
  public CallBuilder with(Consumer<CallBuilder> builderFunction) {
    builderFunction.accept(this);
    return this;
  }

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

  private <T> T executeSynchronousCall(
      RequestParams requestParams, SynchronousCallFactory<T> factory) throws ApiException {
    return DISPATCHER.execute(factory, requestParams, helper);
  }

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
      return new CoreV1Api(client).createNamespace(body, pretty, null, null);
    } finally {
      helper.recycle(client);
    }
  }

  /**
   * List domains.
   *
   * @param namespace Namespace
   * @return Domain list
   * @throws ApiException API exception
   */
  public DomainList listDomain(String namespace) throws ApiException {
    RequestParams requestParams = new RequestParams("listDomain", namespace, null, null);
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
        responseStep, new RequestParams("listDomain", namespace, null, null), listDomain);
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
        responseStep, new RequestParams("readDomain", namespace, name, null), readDomain);
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
    return executeSynchronousCall(requestParams, replaceDomainCall);
  }

  /* Jobs */

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
        responseStep, new RequestParams("replaceDomain", namespace, name, body), replaceDomain);
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
        new RequestParams("patchDomain", namespace, uid, patchBody);
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
        new RequestParams("patchDomain", namespace, name, patchBody),
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
        new RequestParams("replaceDomainStatus", namespace, name, body),
        replaceDomainStatus);
  }

  private Call readCustomResourceDefinitionAsync(
      ApiClient client, String name, ApiCallback<V1beta1CustomResourceDefinition> callback)
      throws ApiException {
    return new ApiextensionsV1beta1Api(client)
        .readCustomResourceDefinitionAsync(name, pretty, exact, export, callback);
  }

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
        responseStep, new RequestParams("readCRD", null, name, null), readCrd);
  }

  /* Services */

  private Call createCustomResourceDefinitionAsync(
      ApiClient client,
      V1beta1CustomResourceDefinition body,
      ApiCallback<V1beta1CustomResourceDefinition> callback)
      throws ApiException {
    return new ApiextensionsV1beta1Api(client)
        .createCustomResourceDefinitionAsync(body, pretty, null, null, callback);
  }

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
        responseStep, new RequestParams("createCRD", null, null, body), createCrd);
  }

  private Call replaceCustomResourceDefinitionAsync(
      ApiClient client,
      String name,
      V1beta1CustomResourceDefinition body,
      ApiCallback<V1beta1CustomResourceDefinition> callback)
      throws ApiException {
    return new ApiextensionsV1beta1Api(client)
        .replaceCustomResourceDefinitionAsync(name, body, pretty, null, null, callback);
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
      V1beta1CustomResourceDefinition body,
      ResponseStep<V1beta1CustomResourceDefinition> responseStep) {
    return createRequestAsync(
        responseStep, new RequestParams("replaceCRD", null, name, body), replaceCrd);
  }

  private Call readConfigMapAsync(
      ApiClient client, String name, String namespace, ApiCallback<V1ConfigMap> callback)
      throws ApiException {
    return new CoreV1Api(client)
        .readNamespacedConfigMapAsync(name, namespace, pretty, exact, export, callback);
  }

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
        responseStep, new RequestParams("readConfigMap", namespace, name, null), readConfigmap);
  }

  private Call createConfigMapAsync(
      ApiClient client, String namespace, V1ConfigMap body, ApiCallback<V1ConfigMap> callback)
      throws ApiException {
    return new CoreV1Api(client)
        .createNamespacedConfigMapAsync(namespace, body, pretty, null, null, callback);
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
        responseStep, new RequestParams("createConfigMap", namespace, null, body), createConfigmap);
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
        .replaceNamespacedConfigMapAsync(name, namespace, body, pretty, dryRun, null, callback);
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
        new RequestParams("replaceConfigMap", namespace, name, body),
        replaceConfigmap);
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
        responseStep, new RequestParams("listPod", namespace, null, null), listPod);
  }

  private Call readPodAsync(
      ApiClient client, String name, String namespace, ApiCallback<V1Pod> callback)
      throws ApiException {
    return new CoreV1Api(client)
        .readNamespacedPodAsync(name, namespace, pretty, exact, export, callback);
  }

  /* Events */

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
        responseStep, new RequestParams("readPod", namespace, name, null), readPod);
  }

  private Call createPodAsync(
      ApiClient client, String namespace, V1Pod body, ApiCallback<V1Pod> callback)
      throws ApiException {
    return new CoreV1Api(client)
        .createNamespacedPodAsync(namespace, body, pretty, null, null, callback);
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
        responseStep, new RequestParams("createPod", namespace, null, body), createPod);
  }

  /* Persistent Volumes */

  private Call deletePodAsync(
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
        responseStep, new RequestParams("deletePod", namespace, name, deleteOptions), deletePod);
  }

  private Call patchPodAsync(
      ApiClient client, String name, String namespace, V1Patch patch, ApiCallback<V1Pod> callback)
      throws ApiException {
    return new CoreV1Api(client)
        .patchNamespacedPodAsync(name, namespace, patch, pretty, null, null, false, callback);
  }

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
      String name, String namespace, V1Patch patchBody, ResponseStep<V1Pod> responseStep) {
    return createRequestAsync(
        responseStep,
        new RequestParams("patchPod", namespace, name, patchBody),
        patchPod);
  }

  private Call deleteCollectionPodAsync(
      ApiClient client, String namespace, String cont, V1DeleteOptions deleteOptions, ApiCallback<V1Status> callback)
      throws ApiException {
    return new CoreV1Api(client)
        .deleteCollectionNamespacedPodAsync(
            namespace,
            pretty,
            allowWatchBookmarks,
            cont,
            dryRun,
            fieldSelector,
            gracePeriodSeconds,
            labelSelector,
            limit,
            orphanDependents,
            propagationPolicy,
            resourceVersion,
            timeoutSeconds,
            watch,
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
        new RequestParams("deletePodCollection", namespace, null, null),
        deletecollectionPod);
  }

  private Call createJobAsync(
      ApiClient client, String namespace, V1Job body, ApiCallback<V1Job> callback)
      throws ApiException {
    return new BatchV1Api(client)
        .createNamespacedJobAsync(namespace, body, pretty, null, null, callback);
  }

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
        responseStep, new RequestParams("createJob", namespace, null, body), createJob);
  }

  private Call readJobAsync(
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
        responseStep, new RequestParams("readJob", namespace, name, null), readJob);
  }

  private Call deleteJobAsync(
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
        responseStep, new RequestParams("deleteJob", namespace, name, deleteOptions), deleteJob);
  }

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
              allowWatchBookmarks,
              cont,
              fieldSelector,
              labelSelector,
              limit,
              resourceVersion,
              timeoutSeconds,
              watch);
    } finally {
      helper.recycle(client);
    }
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
        responseStep, new RequestParams("listService", namespace, null, null), listService);
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

  private Call readServiceAsync(
      ApiClient client, String name, String namespace, ApiCallback<V1Service> callback)
      throws ApiException {
    return new CoreV1Api(client)
        .readNamespacedServiceAsync(name, namespace, pretty, exact, export, callback);
  }

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
        responseStep, new RequestParams("readService", namespace, name, null), readService);
  }

  private Call createServiceAsync(
      ApiClient client, String namespace, V1Service body, ApiCallback<V1Service> callback)
      throws ApiException {
    return new CoreV1Api(client)
        .createNamespacedServiceAsync(namespace, body, pretty, null, null, callback);
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
        responseStep, new RequestParams("createService", namespace, null, body), createService);
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
              pretty,
              dryRun,
              gracePeriodSeconds,
              orphanDependents,
              propagationPolicy,
              deleteOptions);
    } finally {
      helper.recycle(client);
    }
  }

  private Call deleteServiceAsync(
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
        deleteService);
  }

  /* Secrets */

  private Call listEventAsync(
      ApiClient client, String namespace, String cont, ApiCallback<V1EventList> callback)
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
  public Step listEventAsync(String namespace, ResponseStep<V1EventList> responseStep) {
    return createRequestAsync(
        responseStep, new RequestParams("listEvent", namespace, null, null), listEvent);
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
        responseStep, new RequestParams("listNamespace", null, null, null), listNamespace);
  }

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

  /* Self Subject Rules Review */

  private Call readSecretAsync(
      ApiClient client, String name, String namespace, ApiCallback<V1Secret> callback)
      throws ApiException {
    return new CoreV1Api(client)
        .readNamespacedSecretAsync(name, namespace, pretty, exact, export, callback);
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
        responseStep, new RequestParams("readSecret", namespace, name, null), readSecret);
  }

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
      return new CoreV1Api(client).createNamespacedSecret(namespace, body, pretty, null, null);
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
              pretty,
              dryRun,
              gracePeriodSeconds,
              orphanDependents,
              propagationPolicy,
              deleteOptions);
    } finally {
      helper.recycle(client);
    }
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
        new RequestParams("listSecret", namespace, null, null),
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
    RequestParams params = new RequestParams("createSubjectAccessReview", null, null, body);
    return executeSynchronousCall(params, createSubjectaccessreviewCall);
  }

  /* Token Review */

  private Call createSubjectAccessReviewAsync(
      ApiClient client, V1SubjectAccessReview body, ApiCallback<V1SubjectAccessReview> callback)
      throws ApiException {
    return new AuthorizationV1Api(client)
        .createSubjectAccessReviewAsync(body, null, null, pretty, callback);
  }

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
        createSubjectaccessreview);
  }

  /**
   * Create self subject access review.
   *
   * @param body Body
   * @return Created self subject access review
   * @throws ApiException API Exception
   */
  public V1SelfSubjectAccessReview createSelfSubjectAccessReview(V1SelfSubjectAccessReview body)
      throws ApiException {
    RequestParams requestParams = new RequestParams("createSelfSubjectAccessReview", null, null, body);
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
    RequestParams params = new RequestParams("createSelfSubjectRulesReview", null, null, body);
    return executeSynchronousCall(params, createSelfsubjectrulesreviewCall);
  }

  private Call createSelfSubjectRulesReviewAsync(
      ApiClient client,
      V1SelfSubjectRulesReview body,
      ApiCallback<V1SelfSubjectRulesReview> callback)
      throws ApiException {
    return new AuthorizationV1Api(client)
        .createSelfSubjectRulesReviewAsync(body, null, null, pretty, callback);
  }

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
        createSelfsubjectrulesreview);
  }

  /**
   * Create token review.
   *
   * @param body Body
   * @return Created token review
   * @throws ApiException API Exception
   */
  public V1TokenReview createTokenReview(V1TokenReview body) throws ApiException {
    RequestParams requestParams = new RequestParams("createTokenReview", null, null, body);
    return executeSynchronousCall(requestParams, createTokenReviewCall);
  }

  public Step readPodLogAsync(String name, String namespace, ResponseStep<String> responseStep) {
    return createRequestAsync(
        responseStep, new RequestParams("readPodLog", namespace, name, null), readPodLog);
  }

  private Call readPodLogAsync(
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

// Copyright 2017, 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helpers;

import java.util.function.Consumer;

import com.squareup.okhttp.Call;

import io.kubernetes.client.ApiCallback;
import io.kubernetes.client.ApiClient;
import io.kubernetes.client.ApiException;
import io.kubernetes.client.apis.ApiextensionsV1beta1Api;
import io.kubernetes.client.apis.AuthenticationV1Api;
import io.kubernetes.client.apis.AuthorizationV1Api;
import io.kubernetes.client.apis.BatchV1Api;
import io.kubernetes.client.apis.CoreV1Api;
import io.kubernetes.client.apis.ExtensionsV1beta1Api;
import io.kubernetes.client.apis.VersionApi;
import io.kubernetes.client.models.*;
import oracle.kubernetes.operator.TuningParameters.CallBuilderTuning;
import oracle.kubernetes.operator.calls.AsyncRequestStep;
import oracle.kubernetes.operator.calls.CallFactory;
import oracle.kubernetes.operator.calls.CallWrapper;
import oracle.kubernetes.operator.calls.CancellableCall;
import oracle.kubernetes.operator.calls.RequestParams;
import oracle.kubernetes.operator.work.Step;
import oracle.kubernetes.weblogic.domain.v1.Domain;
import oracle.kubernetes.weblogic.domain.v1.DomainList;
import oracle.kubernetes.weblogic.domain.v1.api.WeblogicApi;

/**
 * Simplifies synchronous and asynchronous call patterns to the Kubernetes API Server.
 * 
 */
@SuppressWarnings({"WeakerAccess", "UnusedReturnValue"})
public class CallBuilder {

  /**
   * HTTP status code for "Not Found"
   */
  public static final int NOT_FOUND = 404;
  /**
   * HTTP status code for "Conflict"
   */
  public static final int CONFLICT = 409;

  public String pretty = "false";
  public String fieldSelector = "";
  public Boolean includeUninitialized = Boolean.FALSE;
  public String labelSelector = "";
  public Integer limit = 500;
  public String resourceVersion = "";
  public Integer timeoutSeconds = 5;
  public Integer maxRetryCount = 10;
  public Boolean watch = Boolean.FALSE;
  public Boolean exact = Boolean.FALSE;
  public Boolean export = Boolean.FALSE;

  // less common
  public Integer gracePeriodSeconds = null;
  public Boolean orphanDependents = null;
  public String propagationPolicy = null;

  private final ClientPool helper;

  CallBuilder(CallBuilderTuning tuning, ClientPool helper) {
    if (tuning != null) {
      tuning(tuning.callRequestLimit, tuning.callTimeoutSeconds, tuning.callMaxRetryCount);
    }
    this.helper = helper;
  }
  
  private void tuning(int limit, int timeoutSeconds, int maxRetryCount) {
    this.limit = limit;
    this.timeoutSeconds = timeoutSeconds;
    this.maxRetryCount = maxRetryCount;
  }
  
  /**
   * Creates instance that will acquire clients as needed from the {@link ClientPool} instance.
   * @param tuning Tuning parameters
   * @return Call builder
   */
  static CallBuilder create(CallBuilderTuning tuning) {
    return new CallBuilder(tuning, ClientPool.getInstance());
  }
  
  /**
   * Consumer for lambda-based builder pattern
   * @param builderFunction Builder lambda function
   * @return this CallBuilder
   */
  public CallBuilder with(Consumer<CallBuilder> builderFunction) {
    builderFunction.accept(this);
    return this;
  }
  
  /**
   * Converts value to nearest DNS-1123 legal name, which can be used as a Kubernetes identifier
   * @param value Input value
   * @return nearest DNS-1123 legal name
   */
  public static String toDNS1123LegalName(String value) {
    if (value != null) {
      value = value.toLowerCase();

      // replace '_'
      value = value.replace('_', '-');
    }

    return value;
  }
  
  /* Version */
  
  /**
   * Read Kubernetes version code
   * @return Version code
   * @throws ApiException API Exception
   */
  public VersionInfo readVersionCode() throws ApiException {
    ApiClient client = helper.take();
    try {
      return CALL_FACTORY.getVersionCode(client);
    } finally {
      helper.recycle(client);
    }
  }

  private static SynchronousCallFactory CALL_FACTORY = new SynchronousCallFactoryImpl();

  /* Namespaces */

  /**
   * Read namespace
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
   * Create namespace
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
  
  /**
   * List domains
   * @param namespace Namespace
   * @return Domain list
   * @throws ApiException API exception
   */
  public DomainList listDomain(String namespace) throws ApiException {
    String _continue = "";
    ApiClient client = helper.take();
    try {
      return CALL_FACTORY.getDomainList(client, namespace, _continue, pretty, fieldSelector, includeUninitialized, labelSelector,
            limit, resourceVersion, timeoutSeconds, watch);
    } finally {
      helper.recycle(client);
    }
  }

  private com.squareup.okhttp.Call listDomainAsync(ApiClient client, String namespace, String _continue, ApiCallback<DomainList> callback) throws ApiException {
    return new WeblogicApi(client).listWebLogicOracleV1NamespacedDomainAsync(namespace, pretty, _continue,
      fieldSelector, includeUninitialized, labelSelector, limit, resourceVersion, timeoutSeconds, watch, callback);
  }

  private final CallFactory<DomainList> LIST_DOMAIN = (requestParams, usage, cont, callback)
        -> wrap(listDomainAsync(usage, requestParams.namespace, cont, callback));

  private CancellableCall wrap(Call call) {
    return new CallWrapper(call);
  }

  /**
   * Asynchronous step for listing domains
   * @param namespace Namespace
   * @param responseStep Response step for when call completes
   * @return Asynchronous step
   */
  public Step listDomainAsync(String namespace, ResponseStep<DomainList> responseStep) {
    return createRequestAsync(responseStep, new RequestParams("listDomain", namespace, null, null), LIST_DOMAIN);
  }
  
  /**
   * Replace domain
   * @param name Name
   * @param namespace Namespace
   * @param body Body
   * @return Replaced domain
   * @throws ApiException APIException
   */
  public Domain replaceDomain(String name, String namespace, Domain body) throws ApiException {
    ApiClient client = helper.take();
    try {
      return new WeblogicApi(client).replaceWebLogicOracleV1NamespacedDomain(name, namespace, body, pretty);
    } finally {
      helper.recycle(client);
    }
  }
  
  private com.squareup.okhttp.Call replaceDomainAsync(ApiClient client, String name, String namespace, Domain body, ApiCallback<Domain> callback) throws ApiException {
    return new WeblogicApi(client).replaceWebLogicOracleV1NamespacedDomainAsync(name, namespace, body, pretty, callback);
  }

  private final CallFactory<Domain> REPLACE_DOMAIN = (requestParams, usage, cont, callback)
        -> wrap(replaceDomainAsync(usage, requestParams.name, requestParams.namespace, (Domain) requestParams.body, callback));
  
  /**
   * Asynchronous step for replacing domain
   * @param name Name
   * @param namespace Namespace
   * @param body Body
   * @param responseStep Response step for when call completes
   * @return Asynchronous step
   */
  public Step replaceDomainAsync(String name, String namespace, Domain body, ResponseStep<Domain> responseStep) {
    return createRequestAsync(responseStep, new RequestParams("replaceDomain", namespace, name, body), REPLACE_DOMAIN);
  }

  /* Custom Resource Definitions */
  
  /**
   * Read custom resource definition
   * @param name Name
   * @return CustomResourceDefinition
   * @throws ApiException API Exception
   */
  public V1beta1CustomResourceDefinition readCustomResourceDefinition(String name) throws ApiException {
    ApiClient client = helper.take();
    try {
      return CALL_FACTORY.readCustomResourceDefinition(client, name, pretty, exact, export);
    } finally {
      helper.recycle(client);
    }
  }

  /**
   * Create custom resource definition
   * @param body Body
   * @return Created custom resource definition
   * @throws ApiException API Exception
   */
  public V1beta1CustomResourceDefinition createCustomResourceDefinition(V1beta1CustomResourceDefinition body)
      throws ApiException {
    ApiClient client = helper.take();
    try {
      return CALL_FACTORY.createCustomResourceDefinition(client, body, pretty);
    } finally {
      helper.recycle(client);
    }
  }

  /* Config Maps */

  private com.squareup.okhttp.Call readConfigMapAsync(ApiClient client, String name, String namespace, ApiCallback<V1ConfigMap> callback) throws ApiException {
    return new CoreV1Api(client).readNamespacedConfigMapAsync(name, namespace, pretty, exact, export, callback);
  }

  private final CallFactory<V1ConfigMap> READ_CONFIGMAP = (requestParams, usage, cont, callback)
        -> wrap(readConfigMapAsync(usage, requestParams.name, requestParams.namespace, callback));
  
  /**
   * Asynchronous step for reading config map
   * @param name Name
   * @param namespace Namespace
   * @param responseStep Response step for when call completes
   * @return Asynchronous step
   */
  public Step readConfigMapAsync(String name, String namespace, ResponseStep<V1ConfigMap> responseStep) {
    return createRequestAsync(responseStep, new RequestParams("readConfigMap", namespace, name, null), READ_CONFIGMAP);
  }

  private com.squareup.okhttp.Call createConfigMapAsync(ApiClient client, String namespace, V1ConfigMap body, ApiCallback<V1ConfigMap> callback) throws ApiException {
    return new CoreV1Api(client).createNamespacedConfigMapAsync(namespace, body, pretty, callback);
  }

  private final CallFactory<V1ConfigMap> CREATE_CONFIGMAP = (requestParams, usage, cont, callback)
        -> wrap(createConfigMapAsync(usage, requestParams.namespace, (V1ConfigMap) requestParams.body, callback));
  
  /**
   * Asynchronous step for creating config map
   * @param namespace Namespace
   * @param body Body
   * @param responseStep Response step for when call completes
   * @return Asynchronous step
   */
  public Step createConfigMapAsync(String namespace, V1ConfigMap body, ResponseStep<V1ConfigMap> responseStep) {
    return createRequestAsync(responseStep, new RequestParams("createConfigMap", namespace, null, body), CREATE_CONFIGMAP);
  }

  private com.squareup.okhttp.Call replaceConfigMapAsync(ApiClient client, String name, String namespace, V1ConfigMap body, ApiCallback<V1ConfigMap> callback) throws ApiException {
    return new CoreV1Api(client).replaceNamespacedConfigMapAsync(name, namespace, body, pretty, callback);
  }

  private final CallFactory<V1ConfigMap> REPLACE_CONFIGMAP = (requestParams, usage, cont, callback)
        -> wrap(replaceConfigMapAsync(usage, requestParams.name, requestParams.namespace, (V1ConfigMap) requestParams.body, callback));
  
  /**
   * Asynchronous step for replacing config map
   * @param name Name
   * @param namespace Namespace
   * @param body Body
   * @param responseStep Response step for when call completes
   * @return Asynchronous step
   */
  public Step replaceConfigMapAsync(String name, String namespace, V1ConfigMap body, ResponseStep<V1ConfigMap> responseStep) {
    return createRequestAsync(responseStep, new RequestParams("replaceConfigMap", namespace, name, body), REPLACE_CONFIGMAP);
  }

  /* Pods */

  private com.squareup.okhttp.Call listPodAsync(ApiClient client, String namespace, String _continue, ApiCallback<V1PodList> callback) throws ApiException {
    return new CoreV1Api(client).listNamespacedPodAsync(namespace, pretty, _continue,
      fieldSelector, includeUninitialized, labelSelector, limit, resourceVersion, timeoutSeconds, watch, callback);
  }

  private final CallFactory<V1PodList> LIST_POD = (requestParams, usage, cont, callback)
        -> wrap(listPodAsync(usage, requestParams.namespace, cont, callback));
  
  /**
   * Asynchronous step for listing pods
   * @param namespace Namespace
   * @param responseStep Response step for when call completes
   * @return Asynchronous step
   */
  public Step listPodAsync(String namespace, ResponseStep<V1PodList> responseStep) {
    return createRequestAsync(responseStep, new RequestParams("listPod", namespace, null, null), LIST_POD);
  }

  private com.squareup.okhttp.Call readPodAsync(ApiClient client, String name, String namespace, ApiCallback<V1Pod> callback) throws ApiException {
    return new CoreV1Api(client).readNamespacedPodAsync(name, namespace, pretty, exact, export, callback);
  }

  private final CallFactory<V1Pod> READ_POD = (requestParams, usage, cont, callback)
        -> wrap(readPodAsync(usage, requestParams.name, requestParams.namespace, callback));
  
  /**
   * Asynchronous step for reading pod
   * @param name Name
   * @param namespace Namespace
   * @param responseStep Response step for when call completes
   * @return Asynchronous step
   */
  public Step readPodAsync(String name, String namespace, ResponseStep<V1Pod> responseStep) {
    return createRequestAsync(responseStep, new RequestParams("readPod", namespace, name, null), READ_POD);
  }

  private com.squareup.okhttp.Call createPodAsync(ApiClient client, String namespace, V1Pod body, ApiCallback<V1Pod> callback) throws ApiException {
    return new CoreV1Api(client).createNamespacedPodAsync(namespace, body, pretty, callback);
  }

  private final CallFactory<V1Pod> CREATE_POD = (requestParams, usage, cont, callback)
        -> wrap(createPodAsync(usage, requestParams.namespace, (V1Pod) requestParams.body, callback));
  
  /**
   * Asynchronous step for creating pod
   * @param namespace Namespace
   * @param body Body
   * @param responseStep Response step for when call completes
   * @return Asynchronous step
   */
  public Step createPodAsync(String namespace, V1Pod body, ResponseStep<V1Pod> responseStep) {
    return createRequestAsync(responseStep, new RequestParams("createPod", namespace, null, body), CREATE_POD);
  }

  private com.squareup.okhttp.Call deletePodAsync(ApiClient client, String name, String namespace, V1DeleteOptions deleteOptions, ApiCallback<V1Status> callback) throws ApiException {
    return new CoreV1Api(client).deleteNamespacedPodAsync(name, namespace, deleteOptions, pretty, gracePeriodSeconds, orphanDependents, propagationPolicy, callback);
  }

  private final CallFactory<V1Status> DELETE_POD = (requestParams, usage, cont, callback)
        -> wrap(deletePodAsync(usage, requestParams.name, requestParams.namespace, (V1DeleteOptions) requestParams.body, callback));
  
  /**
   * Asynchronous step for deleting pod
   * @param name Name
   * @param namespace Namespace
   * @param deleteOptions Delete options
   * @param responseStep Response step for when call completes
   * @return Asynchronous step
   */
  public Step deletePodAsync(String name, String namespace, V1DeleteOptions deleteOptions, ResponseStep<V1Status> responseStep) {
    return createRequestAsync(responseStep, new RequestParams("deletePod", namespace, name, deleteOptions), DELETE_POD);
  }

  private com.squareup.okhttp.Call deleteCollectionPodAsync(ApiClient client, String namespace, String _continue, ApiCallback<V1Status> callback) throws ApiException {
    return new CoreV1Api(client).deleteCollectionNamespacedPodAsync(namespace, pretty, _continue, fieldSelector,
        includeUninitialized, labelSelector, limit, resourceVersion, timeoutSeconds, watch, callback);
  }

  private final CallFactory<V1Status> DELETECOLLECTION_POD = (requestParams, usage, cont, callback)
        -> wrap(deleteCollectionPodAsync(usage, requestParams.namespace, cont, callback));
  
  /**
   * Asynchronous step for deleting collection of pods
   * @param namespace Namespace
   * @param responseStep Response step for when call completes
   * @return Asynchronous step
   */
  public Step deleteCollectionPodAsync(String namespace, ResponseStep<V1Status> responseStep) {
    return createRequestAsync(responseStep, new RequestParams("deleteCollection", namespace, null, null), DELETECOLLECTION_POD);
  }
  
  /* Jobs */

  private com.squareup.okhttp.Call createJobAsync(ApiClient client, String namespace, V1Job body, ApiCallback<V1Job> callback) throws ApiException {
    return new BatchV1Api(client).createNamespacedJobAsync(namespace, body, pretty, callback);
  }

  private final CallFactory<V1Job> CREATE_JOB = (requestParams, usage, cont, callback)
        -> wrap(createJobAsync(usage, requestParams.namespace, (V1Job) requestParams.body, callback));
  
  /**
   * Asynchronous step for creating job
   * @param namespace Namespace
   * @param body Body
   * @param responseStep Response step for when call completes
   * @return Asynchronous step
   */
  public Step createJobAsync(String namespace, V1Job body, ResponseStep<V1Job> responseStep) {
    return createRequestAsync(responseStep, new RequestParams("createJob", namespace, null, body), CREATE_JOB);
  }

  private com.squareup.okhttp.Call deleteJobAsync(ApiClient client, String name, String namespace,V1DeleteOptions body, ApiCallback<V1Status> callback) throws ApiException {
    return new BatchV1Api(client).deleteNamespacedJobAsync(name, namespace, body, pretty, gracePeriodSeconds, orphanDependents, propagationPolicy, callback);
  }

  private final CallFactory<V1Status> DELETE_JOB = (requestParams, usage, cont, callback)
        -> wrap(deleteJobAsync(usage, requestParams.name, requestParams.namespace, (V1DeleteOptions) requestParams.body, callback));
  
  /**
   * Asynchronous step for deleting job
   * @param name Name
   * @param namespace Namespace
   * @param responseStep Response step for when call completes
   * @return Asynchronous step
   */
  public Step deleteJobAsync(String name, String namespace, ResponseStep<V1Status> responseStep) {
    return createRequestAsync(responseStep, new RequestParams("deleteJob", namespace, name, null), DELETE_JOB);
  }

  /* Services */
  
  /**
   * List services
   * @param namespace Namespace
   * @return List of services
   * @throws ApiException API Exception
   */
  public V1ServiceList listService(String namespace) throws ApiException {
    String _continue = "";
    ApiClient client = helper.take();
    try {
      return new CoreV1Api(client).listNamespacedService(namespace, pretty, _continue, fieldSelector,
        includeUninitialized, labelSelector, limit, resourceVersion, timeoutSeconds, watch);
    } finally {
      helper.recycle(client);
    }
  }

  private com.squareup.okhttp.Call listServiceAsync(ApiClient client, String namespace, String _continue, ApiCallback<V1ServiceList> callback) throws ApiException {
    return new CoreV1Api(client).listNamespacedServiceAsync(namespace, pretty, _continue,
      fieldSelector, includeUninitialized, labelSelector, limit, resourceVersion, timeoutSeconds, watch, callback);
  }

  private final CallFactory<V1ServiceList> LIST_SERVICE = (requestParams, usage, cont, callback)
        -> wrap(listServiceAsync(usage, requestParams.namespace, cont, callback));
  
  /**
   * Asynchronous step for listing services
   * @param namespace Namespace
   * @param responseStep Response step for when call completes
   * @return Asynchronous step
   */
  public Step listServiceAsync(String namespace, ResponseStep<V1ServiceList> responseStep) {
    return createRequestAsync(responseStep, new RequestParams("listService", namespace, null, null), LIST_SERVICE);
  }
  
  /**
   * Read service
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

  private com.squareup.okhttp.Call readServiceAsync(ApiClient client, String name, String namespace, ApiCallback<V1Service> callback) throws ApiException {
    return new CoreV1Api(client).readNamespacedServiceAsync(name, namespace, pretty, exact, export, callback);
  }

  private final CallFactory<V1Service> READ_SERVICE = (requestParams, usage, cont, callback)
        -> wrap(readServiceAsync(usage, requestParams.name, requestParams.namespace, callback));
  
  /**
   * Asynchronous step for reading service
   * @param name Name
   * @param namespace Namespace
   * @param responseStep Response step for when call completes
   * @return Asynchronous step
   */
  public Step readServiceAsync(String name, String namespace, ResponseStep<V1Service> responseStep) {
    return createRequestAsync(responseStep, new RequestParams("readService", namespace, name, null), READ_SERVICE);
  }

  private com.squareup.okhttp.Call createServiceAsync(ApiClient client, String namespace, V1Service body, ApiCallback<V1Service> callback) throws ApiException {
    return new CoreV1Api(client).createNamespacedServiceAsync(namespace, body, pretty, callback);
  }

  private final CallFactory<V1Service> CREATE_SERVICE = (requestParams, usage, cont, callback)
        -> wrap(createServiceAsync(usage, requestParams.namespace, (V1Service) requestParams.body, callback));
  
  /**
   * Asynchronous step for creating service
   * @param namespace Namespace
   * @param body Body
   * @param responseStep Response step for when call completes
   * @return Asynchronous step
   */
  public Step createServiceAsync(String namespace, V1Service body, ResponseStep<V1Service> responseStep) {
    return createRequestAsync(responseStep, new RequestParams("createService", namespace, null, body), CREATE_SERVICE);
  }

  /**
   * Delete service
   * @param name Name
   * @param namespace Namespace
   * @return Status of deletion
   * @throws ApiException API Exception
   */
  public V1Status deleteService(String name, String namespace) throws ApiException {
    ApiClient client = helper.take();
    try {
      return new CoreV1Api(client).deleteNamespacedService(name, namespace, pretty);
    } finally {
      helper.recycle(client);
    }
  }

  private com.squareup.okhttp.Call deleteServiceAsync(ApiClient client, String name, String namespace, ApiCallback<V1Status> callback) throws ApiException {
    return new CoreV1Api(client).deleteNamespacedServiceAsync(name, namespace, pretty, callback);
  }

  private final CallFactory<V1Status> DELETE_SERVICE = (requestParams, usage, cont, callback)
        -> wrap(deleteServiceAsync(usage, requestParams.name, requestParams.namespace, callback));
  
  /**
   * Asynchronous step for deleting service
   * @param name Name
   * @param namespace Namespace
   * @param responseStep Response step for when call completes
   * @return Asynchronous step
   */
  public Step deleteServiceAsync(String name, String namespace, ResponseStep<V1Status> responseStep) {
    return createRequestAsync(responseStep, new RequestParams("deleteService", namespace, name, null), DELETE_SERVICE);
  }
  
  /* Events */

  private com.squareup.okhttp.Call listEventAsync(ApiClient client, String namespace, String _continue, ApiCallback<V1EventList> callback) throws ApiException {
    return new CoreV1Api(client).listNamespacedEventAsync(namespace, pretty, _continue,
      fieldSelector, includeUninitialized, labelSelector, limit, resourceVersion, timeoutSeconds, watch, callback);
  }

  private final CallFactory<V1EventList> LIST_EVENT = (requestParams, usage, cont, callback)
        -> wrap(listEventAsync(usage, requestParams.namespace, cont, callback));
  
  /**
   * Asynchronous step for listing events
   * @param namespace Namespace
   * @param responseStep Response step for when call completes
   * @return Asynchronous step
   */
  public Step listEventAsync(String namespace, ResponseStep<V1EventList> responseStep) {
    return createRequestAsync(responseStep, new RequestParams("listEvent", namespace, null, null), LIST_EVENT);
  }

  /* Persistent Volumes */
  
  /**
   * List persistent volumes
   * @return List of persistent volumes
   * @throws ApiException API Exception
   */
  public V1PersistentVolumeList listPersistentVolume() throws ApiException {
    String _continue = "";
    ApiClient client = helper.take();
    try {
      return CALL_FACTORY.listPersistentVolumes(_continue, client, pretty, fieldSelector, includeUninitialized,
                                                labelSelector, limit, resourceVersion, timeoutSeconds, watch);
    } finally {
      helper.recycle(client);
    }
  }

  /* Persistent Volume Claims */

  private com.squareup.okhttp.Call listPersistentVolumeClaimAsync(ApiClient client, String namespace, String _continue, ApiCallback<V1PersistentVolumeClaimList> callback) throws ApiException {
    return new CoreV1Api(client).listNamespacedPersistentVolumeClaimAsync(namespace, pretty, _continue,
      fieldSelector, includeUninitialized, labelSelector, limit, resourceVersion, timeoutSeconds, watch, callback);
  }

  private final CallFactory<V1PersistentVolumeClaimList> LIST_PERSISTENTVOLUMECLAIM = (requestParams, usage, cont, callback)
        -> wrap(listPersistentVolumeClaimAsync(usage, requestParams.namespace, cont, callback));
  
  /**
   * Asynchronous step for listing persistent volume claims
   * @param namespace Namespace
   * @param responseStep Response step for when call completes
   * @return Asynchronous step
   */
  public Step listPersistentVolumeClaimAsync(String namespace, ResponseStep<V1PersistentVolumeClaimList> responseStep) {
    return createRequestAsync(responseStep, new RequestParams("listPersistentVolumeClaim", namespace, null, null), LIST_PERSISTENTVOLUMECLAIM);
  }
  
  /* Secrets */
  
  /**
   * Read secret
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

  private com.squareup.okhttp.Call readSecretAsync(ApiClient client, String name, String namespace, ApiCallback<V1Secret> callback) throws ApiException {
    return new CoreV1Api(client).readNamespacedSecretAsync(name, namespace, pretty, exact, export, callback);
  }

  private final CallFactory<V1Secret> READ_SECRET = (requestParams, usage, cont, callback)
        -> wrap(readSecretAsync(usage, requestParams.name, requestParams.namespace, callback));
  
  /**
   * Create secret
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
   * Delete secret
   * @param name Name
   * @param namespace Namespace
   * @param deleteOptions Delete options
   * @return Status of deletion
   * @throws ApiException API Exception
   */
  public V1Status deleteSecret(String name, String namespace, V1DeleteOptions deleteOptions) throws ApiException {
    ApiClient client = helper.take();
    try {
      return new CoreV1Api(client).deleteNamespacedSecret(name, namespace, deleteOptions, pretty, gracePeriodSeconds,
        orphanDependents, propagationPolicy);
    } finally {
      helper.recycle(client);
    }
  }

  /**
   * Asynchronous step for reading secret
   * @param name Name
   * @param namespace Namespace
   * @param responseStep Response step for when call completes
   * @return Asynchronous step
   */
  public Step readSecretAsync(String name, String namespace, ResponseStep<V1Secret> responseStep) {
    return createRequestAsync(responseStep, new RequestParams("readSecret", namespace, name, null), READ_SECRET);
  }
  
  /* Subject Access Review */
  
  /**
   * Create subject access review
   * @param body Body
   * @return Created subject access review
   * @throws ApiException API Exception
   */
  public V1SubjectAccessReview createSubjectAccessReview(V1SubjectAccessReview body) throws ApiException {
    ApiClient client = helper.take();
    try {
      return new AuthorizationV1Api(client).createSubjectAccessReview(body, pretty);
    } finally {
      helper.recycle(client);
    }
  }

  private com.squareup.okhttp.Call createSubjectAccessReviewAsync(ApiClient client, V1SubjectAccessReview body, ApiCallback<V1SubjectAccessReview> callback) throws ApiException {
    return new AuthorizationV1Api(client).createSubjectAccessReviewAsync(body, pretty, callback);
  }

  private final CallFactory<V1SubjectAccessReview> CREATE_SUBJECTACCESSREVIEW = (requestParams, usage, cont, callback)
        -> wrap(createSubjectAccessReviewAsync(usage, (V1SubjectAccessReview) requestParams.body, callback));
  
  /**
   * Asynchronous step for creating subject access review
   * @param body Body
   * @param responseStep Response step for when call completes
   * @return Asynchronous step
   */
  public Step createSubjectAccessReviewAsync(V1SubjectAccessReview body, ResponseStep<V1SubjectAccessReview> responseStep) {
    return createRequestAsync(responseStep, new RequestParams("createSubjectAccessReview", null, null, body), CREATE_SUBJECTACCESSREVIEW);
  }
  
  /* Self Subject Access Review */
  
  /**
   * Create self subject access review
   * @param body Body
   * @return Created self subject access review
   * @throws ApiException API Exception
   */
  public V1SelfSubjectAccessReview createSelfSubjectAccessReview(V1SelfSubjectAccessReview body) throws ApiException {
    ApiClient client = helper.take();
    try {
      return new AuthorizationV1Api(client).createSelfSubjectAccessReview(body, pretty);
    } finally {
      helper.recycle(client);
    }
  }

  private com.squareup.okhttp.Call createSelfSubjectAccessReviewAsync(ApiClient client, V1SelfSubjectAccessReview body, ApiCallback<V1SelfSubjectAccessReview> callback) throws ApiException {
    return new AuthorizationV1Api(client).createSelfSubjectAccessReviewAsync(body, pretty, callback);
  }

  private final CallFactory<V1SelfSubjectAccessReview> CREATE_SELFSUBJECTACCESSREVIEW = (requestParams, usage, cont, callback)
        -> wrap(createSelfSubjectAccessReviewAsync(usage, (V1SelfSubjectAccessReview) requestParams.body, callback));
  
  /**
   * Asynchronous step for creating self subject access review
   * @param body Body
   * @param responseStep Response step for when call completes
   * @return Asynchronous step
   */
  public Step createSelfSubjectAccessReviewAsync(V1SelfSubjectAccessReview body, ResponseStep<V1SelfSubjectAccessReview> responseStep) {
    return createRequestAsync(responseStep, new RequestParams("createSelfSubjectAccessReview", null, null, body), CREATE_SELFSUBJECTACCESSREVIEW);
  }
  
  /* Self Subject Rules Review */
  
  /**
   * Create self subject rules review
   * @param body Body
   * @return Created self subject rules review
   * @throws ApiException API Exception
   */
  public V1SelfSubjectRulesReview createSelfSubjectRulesReview(V1SelfSubjectRulesReview body) throws ApiException {
    ApiClient client = helper.take();
    try {
      String pretty = this.pretty;
      return CALL_FACTORY.createSelfSubjectRulesReview(client, body, pretty);
    } finally {
      helper.recycle(client);
    }
  }

  private com.squareup.okhttp.Call createSelfSubjectRulesReviewAsync(ApiClient client, V1SelfSubjectRulesReview body, ApiCallback<V1SelfSubjectRulesReview> callback) throws ApiException {
    return new AuthorizationV1Api(client).createSelfSubjectRulesReviewAsync(body, pretty, callback);
  }

  private final CallFactory<V1SelfSubjectRulesReview> CREATE_SELFSUBJECTRULESREVIEW = (requestParams, usage, cont, callback)
        -> wrap(createSelfSubjectRulesReviewAsync(usage, (V1SelfSubjectRulesReview) requestParams.body, callback));
  
  /**
   * Asynchronous step for creating self subject rules review
   * @param body Body
   * @param responseStep Response step for when call completes
   * @return Asynchronous step
   */
  public Step createSelfSubjectRulesReviewAsync(V1SelfSubjectRulesReview body, ResponseStep<V1SelfSubjectRulesReview> responseStep) {
    return createRequestAsync(responseStep, new RequestParams("createSelfSubjectRulesReview", null, null, body), CREATE_SELFSUBJECTRULESREVIEW);
  }
  
  /* Token Review */
  
  /**
   * Create token review
   * @param body Body
   * @return Created token review
   * @throws ApiException API Exception
   */
  public V1TokenReview createTokenReview(V1TokenReview body) throws ApiException {
    ApiClient client = helper.take();
    try {
      return new AuthenticationV1Api(client).createTokenReview(body, pretty);
    } finally {
      helper.recycle(client);
    }
  }

  /* Ingress */

  private com.squareup.okhttp.Call listIngressAsync(ApiClient client, String namespace, String _continue, ApiCallback<V1beta1IngressList> callback) throws ApiException {
    return new ExtensionsV1beta1Api(client).listNamespacedIngressAsync(namespace, pretty, _continue,
      fieldSelector, includeUninitialized, labelSelector, limit, resourceVersion, timeoutSeconds, watch, callback);
  }

  private final CallFactory<V1beta1IngressList> LIST_INGRESS = (requestParams, usage, cont, callback)
        -> wrap(listIngressAsync(usage, requestParams.namespace, cont, callback));
  
  /**
   * Asynchronous step for listing ingress
   * @param namespace Namespace
   * @param responseStep Response step for when call completes
   * @return Asynchronous step
   */
  public Step listIngressAsync(String namespace, ResponseStep<V1beta1IngressList> responseStep) {
    return createRequestAsync(responseStep, new RequestParams("listIngress", namespace, null, null), LIST_INGRESS);
  }
  
  /**
   * Read ingress
   * @param name Name
   * @param namespace Namespace
   * @return Read ingress
   * @throws ApiException API Exception
   */
  public V1beta1Ingress readIngress(String name, String namespace) throws ApiException {
    ApiClient client = helper.take();
    try {
      return new ExtensionsV1beta1Api(client).readNamespacedIngress(name, namespace, pretty, exact, export);
    } finally {
      helper.recycle(client);
    }
  }

  private com.squareup.okhttp.Call readIngressAsync(ApiClient client, String name, String namespace, ApiCallback<V1beta1Ingress> callback) throws ApiException {
    return new ExtensionsV1beta1Api(client).readNamespacedIngressAsync(name, namespace, pretty, exact, export, callback);
  }

  private final CallFactory<V1beta1Ingress> READ_INGRESS = (requestParams, usage, cont, callback)
        -> wrap(readIngressAsync(usage, requestParams.name, requestParams.namespace, callback));
  
  /**
   * Asynchronous step for reading ingress
   * @param name Name
   * @param namespace Namespace
   * @param responseStep Response step for when call completes
   * @return Asynchronous step
   */
  public Step readIngressAsync(String name, String namespace, ResponseStep<V1beta1Ingress> responseStep) {
    return createRequestAsync(responseStep, new RequestParams("readIngress", namespace, name, null), READ_INGRESS);
  }

  private com.squareup.okhttp.Call createIngressAsync(ApiClient client, String namespace, V1beta1Ingress body, ApiCallback<V1beta1Ingress> callback) throws ApiException {
    return new ExtensionsV1beta1Api(client).createNamespacedIngressAsync(namespace, body, pretty, callback);
  }

  private final CallFactory<V1beta1Ingress> CREATE_INGRESS = (requestParams, usage, cont, callback)
        -> wrap(createIngressAsync(usage, requestParams.namespace, (V1beta1Ingress) requestParams.body, callback));
  
  /**
   * Asynchronous step for creating ingress
   * @param namespace Namespace
   * @param body Body
   * @param responseStep Response step for when call completes
   * @return Asynchronous step
   */
  public Step createIngressAsync(String namespace, V1beta1Ingress body, ResponseStep<V1beta1Ingress> responseStep) {
    return createRequestAsync(responseStep, new RequestParams("createIngress", namespace, null, body), CREATE_INGRESS);
  }

  private com.squareup.okhttp.Call replaceIngressAsync(ApiClient client, String name, String namespace, V1beta1Ingress body, ApiCallback<V1beta1Ingress> callback) throws ApiException {
    return new ExtensionsV1beta1Api(client).replaceNamespacedIngressAsync(name, namespace, body, pretty, callback);
  }

  private final CallFactory<V1beta1Ingress> REPLACE_INGRESS = (requestParams, usage, cont, callback)
        -> wrap(replaceIngressAsync(usage, requestParams.name, requestParams.namespace, (V1beta1Ingress) requestParams.body, callback));
  
  /**
   * Asynchronous step for replacing ingress
   * @param name Name
   * @param namespace Namespace
   * @param body Body
   * @param responseStep Response step for when call completes
   * @return Asynchronous step
   */
  public Step replaceIngressAsync(String name, String namespace, V1beta1Ingress body, ResponseStep<V1beta1Ingress> responseStep) {
    return createRequestAsync(responseStep, new RequestParams("replaceIngress", namespace, name, body), REPLACE_INGRESS);
  }
  
  /**
   * Delete ingress
   * @param name Name
   * @param namespace Namespace
   * @param deleteOptions Delete options
   * @return Status of deletion
   * @throws ApiException API Exception
   */
  public V1Status deleteIngress(String name, String namespace, V1DeleteOptions deleteOptions) throws ApiException {
    ApiClient client = helper.take();
    try {
      return new ExtensionsV1beta1Api(client).deleteNamespacedIngress(name, namespace, deleteOptions, pretty, gracePeriodSeconds,
        orphanDependents, propagationPolicy);
    } finally {
      helper.recycle(client);
    }
  }

  private com.squareup.okhttp.Call deleteIngressAsync(ApiClient client, String name, String namespace, V1DeleteOptions deleteOptions, ApiCallback<V1Status> callback) throws ApiException {
    return new ExtensionsV1beta1Api(client).deleteNamespacedIngressAsync(name, namespace, deleteOptions, pretty, gracePeriodSeconds, orphanDependents, propagationPolicy, callback);
  }

  private final CallFactory<V1Status> DELETE_INGRESS = (requestParams, usage, cont, callback)
        -> wrap(deleteIngressAsync(usage, requestParams.name, requestParams.namespace, (V1DeleteOptions) requestParams.body, callback));
  
  /**
   * Asynchronous step for deleting ingress
   * @param name Name
   * @param namespace Namespace
   * @param deleteOptions Delete options
   * @param responseStep Response step for when call completes
   * @return Asynchronous step
   */
  public Step deleteIngressAsync(String name, String namespace, V1DeleteOptions deleteOptions, ResponseStep<V1Status> responseStep) {
    return createRequestAsync(responseStep, new RequestParams("deleteIngress", namespace, name, deleteOptions), DELETE_INGRESS);
  }


  private static final AsyncRequestStepFactory STEP_FACTORY = AsyncRequestStep::new;

  private <T> Step createRequestAsync(ResponseStep<T> next, RequestParams requestParams, CallFactory<T> factory) {
    return STEP_FACTORY.createRequestAsync(next, requestParams, factory, helper, timeoutSeconds, maxRetryCount, fieldSelector, labelSelector, resourceVersion);
  }


  public static class SynchronousCallFactoryImpl implements SynchronousCallFactory {
    @Override
    public V1beta1CustomResourceDefinition readCustomResourceDefinition(ApiClient client, String name, String pretty, Boolean exact, Boolean export) throws ApiException {
      return new ApiextensionsV1beta1Api(client).readCustomResourceDefinition(name, pretty, exact, export);
    }

    @Override
    public V1beta1CustomResourceDefinition createCustomResourceDefinition(ApiClient client, V1beta1CustomResourceDefinition body, String pretty) throws ApiException {
      return new ApiextensionsV1beta1Api(client).createCustomResourceDefinition(body, pretty);
    }

    @Override
    public V1SelfSubjectRulesReview createSelfSubjectRulesReview(ApiClient client, V1SelfSubjectRulesReview body, String pretty) throws ApiException {
      return new AuthorizationV1Api(client).createSelfSubjectRulesReview(body, pretty);
    }

    @Override
    public V1PersistentVolumeList listPersistentVolumes(String _continue, ApiClient client, String pretty, String fieldSelector, Boolean includeUninitialized, String labelSelector, Integer limit, String resourceVersion, Integer timeoutSeconds, Boolean watch) throws ApiException {
      return new CoreV1Api(client).listPersistentVolume(pretty, _continue, fieldSelector,
            includeUninitialized, labelSelector, limit, resourceVersion, timeoutSeconds, watch);
    }

    @Override
    public VersionInfo getVersionCode(ApiClient client) throws ApiException {
      return new VersionApi(client).getCode();
    }

    @Override
    public DomainList getDomainList(ApiClient client, String namespace, String _continue, String pretty, String fieldSelector, Boolean includeUninitialized, String labelSelector, Integer limit, String resourceVersion, Integer timeoutSeconds, Boolean watch) throws ApiException {
      return new WeblogicApi(client).listWebLogicOracleV1NamespacedDomain(namespace, pretty, _continue,
            fieldSelector, includeUninitialized, labelSelector, limit, resourceVersion, timeoutSeconds, watch);
    }
  }
}
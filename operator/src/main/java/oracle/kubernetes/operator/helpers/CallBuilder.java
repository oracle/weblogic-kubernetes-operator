// Copyright 2017, 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helpers;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import com.squareup.okhttp.Call;

import io.kubernetes.client.ApiCallback;
import io.kubernetes.client.ApiClient;
import io.kubernetes.client.ApiException;
import io.kubernetes.client.apis.ApiextensionsV1beta1Api;
import io.kubernetes.client.apis.AuthenticationV1Api;
import io.kubernetes.client.apis.AuthorizationV1Api;
import io.kubernetes.client.apis.CoreV1Api;
import io.kubernetes.client.apis.ExtensionsV1beta1Api;
import io.kubernetes.client.apis.VersionApi;
import io.kubernetes.client.models.V1ConfigMap;
import io.kubernetes.client.models.V1ConfigMapList;
import io.kubernetes.client.models.V1DeleteOptions;
import io.kubernetes.client.models.V1Event;
import io.kubernetes.client.models.V1EventList;
import io.kubernetes.client.models.V1ListMeta;
import io.kubernetes.client.models.V1Namespace;
import io.kubernetes.client.models.V1NamespaceList;
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
import io.kubernetes.client.models.V1beta1Ingress;
import io.kubernetes.client.models.V1beta1IngressList;
import io.kubernetes.client.models.VersionInfo;
import oracle.kubernetes.weblogic.domain.v1.Domain;
import oracle.kubernetes.weblogic.domain.v1.DomainList;
import oracle.kubernetes.weblogic.domain.v1.api.WeblogicApi;
import oracle.kubernetes.operator.TuningParameters.CallBuilderTuning;
import oracle.kubernetes.operator.logging.LoggingFacade;
import oracle.kubernetes.operator.logging.LoggingFactory;
import oracle.kubernetes.operator.logging.MessageKeys;
import oracle.kubernetes.operator.work.Step;
import oracle.kubernetes.operator.work.Component;
import oracle.kubernetes.operator.work.NextAction;
import oracle.kubernetes.operator.work.Packet;

/**
 * Simplifies synchronous and asynchronous call patterns to the Kubernetes API Server.
 * 
 */
public class CallBuilder {
  private static final LoggingFacade LOGGER = LoggingFactory.getLogger("Operator", "Operator");

  static final String RESPONSE_COMPONENT_NAME = "response";

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
      return new VersionApi(client).getCode();
    } finally {
      helper.recycle(client);
    }
  }
  
  /* Namespaces */
  
  /**
   * List namespaces
   * @return List of namespaces
   * @throws ApiException API Exception
   */
  public V1NamespaceList listNamespace() throws ApiException {
    String _continue = "";
    ApiClient client = helper.take();
    try {
      return new CoreV1Api(client).listNamespace(pretty, _continue, fieldSelector,
        includeUninitialized, labelSelector, limit, resourceVersion, timeoutSeconds, watch);
    } finally {
      helper.recycle(client);
    }
  }

  private com.squareup.okhttp.Call listNamespaceAsync(ApiClient client, String _continue, ApiCallback<V1NamespaceList> callback) throws ApiException {
    return new CoreV1Api(client).listNamespaceAsync(pretty, _continue,
      fieldSelector, includeUninitialized, labelSelector, limit, resourceVersion, timeoutSeconds, watch, callback);
  }

  private final CallFactory<V1NamespaceList> LIST_NAMESPACE = (requestParams, usage, cont, callback) -> {
    return listNamespaceAsync(usage, cont, callback);
  };
  
  /**
   * Asynchronous step for listing namespaces
   * @param responseStep Response step for when call completes
   * @return Asynchronous step
   */
  public Step listNamespaceAsync(ResponseStep<V1NamespaceList> responseStep) {
    return createRequestAsync(responseStep, new RequestParams("listNamespace", null, null, null), LIST_NAMESPACE);
  }
  
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

  private com.squareup.okhttp.Call readNamespaceAsync(ApiClient client, String name, ApiCallback<V1Namespace> callback) throws ApiException {
    return new CoreV1Api(client).readNamespaceAsync(name, pretty, exact, export, callback);
  }

  private final CallFactory<V1Namespace> READ_NAMESPACE = (requestParams, usage, cont, callback) -> {
    return readNamespaceAsync(usage, requestParams.name, callback);
  };
  
  /**
   * Asynchronous step for reading namespace
   * @param name Name
   * @param responseStep Response step for when call completes
   * @return Asynchronous step
   */
  public Step readNamespaceAsync(String name, ResponseStep<V1Namespace> responseStep) {
    return createRequestAsync(responseStep, new RequestParams("readNamespace", null, name, null), READ_NAMESPACE);
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

  private com.squareup.okhttp.Call createNamespaceAsync(ApiClient client, V1Namespace body, ApiCallback<V1Namespace> callback) throws ApiException {
    return new CoreV1Api(client).createNamespaceAsync(body, pretty, callback);
  }

  private final CallFactory<V1Namespace> CREATE_NAMESPACE = (requestParams, usage, cont, callback) -> {
    return createNamespaceAsync(usage, (V1Namespace) requestParams.body, callback);
  };
  
  /**
   * Asynchronous step for creating namespace
   * @param body Body
   * @param responseStep Response step for when call completes
   * @return Asynchronous step
   */
  public Step createNamespaceAsync(V1Namespace body, ResponseStep<V1Namespace> responseStep) {
    return createRequestAsync(responseStep, new RequestParams("createNamespace", null, null, body), CREATE_NAMESPACE);
  }
  
  /**
   * Delete namespace
   * @param name Name
   * @param deleteOptions Delete options
   * @return Status of deletion
   * @throws ApiException API Exception
   */
  public V1Status deleteNamespace(String name, V1DeleteOptions deleteOptions) throws ApiException {
    ApiClient client = helper.take();
    try {
      return new CoreV1Api(client).deleteNamespace(name, deleteOptions, pretty, gracePeriodSeconds,
          orphanDependents, propagationPolicy);
    } finally {
      helper.recycle(client);
    }
  }

  private com.squareup.okhttp.Call deleteNamespaceAsync(ApiClient client, String name, V1DeleteOptions deleteOptions, ApiCallback<V1Status> callback) throws ApiException {
    return new CoreV1Api(client).deleteNamespaceAsync(name, deleteOptions, pretty, gracePeriodSeconds,
        orphanDependents, propagationPolicy, callback);
  }

  private final CallFactory<V1Status> DELETE_NAMESPACE = (requestParams, usage, cont, callback) -> {
    return deleteNamespaceAsync(usage, requestParams.name, (V1DeleteOptions) requestParams.body, callback);
  };
  
  /**
   * Asynchronous step for deleting service
   * @param name Name
   * @param responseStep Response step for when call completes
   * @return Asynchronous step
   */
  public Step deleteNamespaceAsync(String name, ResponseStep<V1Status> responseStep) {
    return createRequestAsync(responseStep, new RequestParams("deleteNamespace", null, name, null), DELETE_NAMESPACE);
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
      return new WeblogicApi(client).listWebLogicOracleV1NamespacedDomain(namespace, pretty, _continue,
        fieldSelector, includeUninitialized, labelSelector, limit, resourceVersion, timeoutSeconds, watch);
    } finally {
      helper.recycle(client);
    }
  }

  private com.squareup.okhttp.Call listDomainAsync(ApiClient client, String namespace, String _continue, ApiCallback<DomainList> callback) throws ApiException {
    return new WeblogicApi(client).listWebLogicOracleV1NamespacedDomainAsync(namespace, pretty, _continue,
      fieldSelector, includeUninitialized, labelSelector, limit, resourceVersion, timeoutSeconds, watch, callback);
  }

  private final CallFactory<DomainList> LIST_DOMAIN = (requestParams, usage, cont, callback) -> {
    return listDomainAsync(usage, requestParams.namespace, cont, callback);
  };
  
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

  private final CallFactory<Domain> REPLACE_DOMAIN = (requestParams, usage, cont, callback) -> {
    return replaceDomainAsync(usage, requestParams.name, requestParams.namespace, (Domain) requestParams.body, callback);
  };
  
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
  
  /**
   * Replace domain status
   * @param name Name
   * @param namespace Namespace
   * @param body Body
   * @return Replaced domain
   * @throws ApiException APIException
   */
  public Domain replaceDomainStatus(String name, String namespace, Domain body) throws ApiException {
    ApiClient client = helper.take();
    try {
      return new WeblogicApi(client).replaceWebLogicOracleV1NamespacedDomainStatus(name, namespace, body, pretty);
    } finally {
      helper.recycle(client);
    }
  }
  
  private com.squareup.okhttp.Call replaceDomainStatusAsync(ApiClient client, String name, String namespace, Domain body, ApiCallback<Domain> callback) throws ApiException {
    return new WeblogicApi(client).replaceWebLogicOracleV1NamespacedDomainStatusAsync(name, namespace, body, pretty, callback);
  }

  private final CallFactory<Domain> REPLACE_STATUS_DOMAIN = (requestParams, usage, cont, callback) -> {
    return replaceDomainStatusAsync(usage, requestParams.name, requestParams.namespace, (Domain) requestParams.body, callback);
  };
  
  /**
   * Asynchronous step for replacing domain status
   * @param name Name
   * @param namespace Namespace
   * @param body Body
   * @param responseStep Response step for when call completes
   * @return Asynchronous step
   */
  public Step replaceDomainStatusAsync(String name, String namespace, Domain body, ResponseStep<Domain> responseStep) {
    return createRequestAsync(responseStep, new RequestParams("replaceDomainStatus", namespace, name, body), REPLACE_STATUS_DOMAIN);
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
      return new ApiextensionsV1beta1Api(client).readCustomResourceDefinition(name, pretty, exact, export);
    } finally {
      helper.recycle(client);
    }
  }

  private com.squareup.okhttp.Call readCustomResourceDefinitionAsync(ApiClient client, String name, ApiCallback<V1beta1CustomResourceDefinition> callback) throws ApiException {
    return new ApiextensionsV1beta1Api(client).readCustomResourceDefinitionAsync(name, pretty, exact, export, callback);
  }

  private final CallFactory<V1beta1CustomResourceDefinition> READ_CUSTOMRESOURCEDEFINITION = (requestParams, usage, cont, callback) -> {
    return readCustomResourceDefinitionAsync(usage, requestParams.name, callback);
  };
  
  /**
   * Asynchronous step for reading custom resource definition
   * @param name Name
   * @param responseStep Response step for when call completes
   * @return Asynchronous step
   */
  public Step readCustomResourceDefinitionAsync(String name, ResponseStep<V1beta1CustomResourceDefinition> responseStep) {
    return createRequestAsync(responseStep, new RequestParams("readCustomResourceDefinition", null, name, null), READ_CUSTOMRESOURCEDEFINITION);
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
      return new ApiextensionsV1beta1Api(client).createCustomResourceDefinition(body, pretty);
    } finally {
      helper.recycle(client);
    }
  }

  private com.squareup.okhttp.Call createCustomResourceDefinitionAsync(ApiClient client, V1beta1CustomResourceDefinition body, ApiCallback<V1beta1CustomResourceDefinition> callback) throws ApiException {
    return new ApiextensionsV1beta1Api(client).createCustomResourceDefinitionAsync(body, pretty, callback);
  }

  private final CallFactory<V1beta1CustomResourceDefinition> CREATE_CUSTOMRESOURCEDEFINITION = (requestParams, usage, cont, callback) -> {
    return createCustomResourceDefinitionAsync(usage, (V1beta1CustomResourceDefinition) requestParams.body, callback);
  };
  
  /**
   * Asynchronous step for creating custom resource definition
   * @param name Name
   * @param body Body
   * @param responseStep Response step for when call completes
   * @return Asynchronous step
   */
  public Step createCustomResourceDefinitionAsync(String name, V1beta1CustomResourceDefinition body, ResponseStep<V1beta1CustomResourceDefinition> responseStep) {
    return createRequestAsync(responseStep, new RequestParams("createCustomResourceDefinition", null, name, body), CREATE_CUSTOMRESOURCEDEFINITION);
  }
  
  /* Config Maps */
  
  /**
   * List config maps
   * @param namespace Namespace
   * @return List of config maps
   * @throws ApiException API Exception
   */
  public V1ConfigMapList listConfigMap(String namespace) throws ApiException {
    String _continue = "";
    ApiClient client = helper.take();
    try {
      return new CoreV1Api(client).listNamespacedConfigMap(namespace, pretty, _continue, fieldSelector,
        includeUninitialized, labelSelector, limit, resourceVersion, timeoutSeconds, watch);
    } finally {
      helper.recycle(client);
    }
  }

  private com.squareup.okhttp.Call listConfigMapAsync(ApiClient client, String namespace, String _continue, ApiCallback<V1ConfigMapList> callback) throws ApiException {
    return new CoreV1Api(client).listNamespacedConfigMapAsync(namespace, pretty, _continue,
      fieldSelector, includeUninitialized, labelSelector, limit, resourceVersion, timeoutSeconds, watch, callback);
  }

  private final CallFactory<V1ConfigMapList> LIST_CONFIGMAP = (requestParams, usage, cont, callback) -> {
    return listConfigMapAsync(usage, requestParams.namespace, cont, callback);
  };
  
  /**
   * Asynchronous step for listing config maps
   * @param namespace Namespace
   * @param responseStep Response step for when call completes
   * @return Asynchronous step
   */
  public Step listConfigMapAsync(String namespace, ResponseStep<V1ConfigMapList> responseStep) {
    return createRequestAsync(responseStep, new RequestParams("listConfigMap", namespace, null, null), LIST_CONFIGMAP);
  }
  
  /**
   * Read config map
   * @param name Name
   * @param namespace Namespace
   * @return Read config map
   * @throws ApiException API Exception
   */
  public V1ConfigMap readConfigMap(String name, String namespace) throws ApiException {
    ApiClient client = helper.take();
    try {
      return new CoreV1Api(client).readNamespacedConfigMap(name, namespace, pretty, exact, export);
    } finally {
      helper.recycle(client);
    }
  }

  private com.squareup.okhttp.Call readConfigMapAsync(ApiClient client, String name, String namespace, ApiCallback<V1ConfigMap> callback) throws ApiException {
    return new CoreV1Api(client).readNamespacedConfigMapAsync(name, namespace, pretty, exact, export, callback);
  }

  private final CallFactory<V1ConfigMap> READ_CONFIGMAP = (requestParams, usage, cont, callback) -> {
    return readConfigMapAsync(usage, requestParams.name, requestParams.namespace, callback);
  };
  
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
  
  /**
   * Create config map
   * @param namespace Namespace
   * @param body Body
   * @return Created config map
   * @throws ApiException API Exception
   */
  public V1ConfigMap createConfigMap(String namespace, V1ConfigMap body) throws ApiException {
    ApiClient client = helper.take();
    try {
      return new CoreV1Api(client).createNamespacedConfigMap(namespace, body, pretty);
    } finally {
      helper.recycle(client);
    }
  }

  private com.squareup.okhttp.Call createConfigMapAsync(ApiClient client, String namespace, V1ConfigMap body, ApiCallback<V1ConfigMap> callback) throws ApiException {
    return new CoreV1Api(client).createNamespacedConfigMapAsync(namespace, body, pretty, callback);
  }

  private final CallFactory<V1ConfigMap> CREATE_CONFIGMAP = (requestParams, usage, cont, callback) -> {
    return createConfigMapAsync(usage, requestParams.namespace, (V1ConfigMap) requestParams.body, callback);
  };
  
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
  
  /**
   * Replace config map
   * @param name Name
   * @param namespace Namespace
   * @param body Body
   * @return Replaced config map
   * @throws ApiException API Exception
   */
  public V1ConfigMap replaceConfigMap(String name, String namespace, V1ConfigMap body) throws ApiException {
    ApiClient client = helper.take();
    try {
      return new CoreV1Api(client).replaceNamespacedConfigMap(name, namespace, body, pretty);
    } finally {
      helper.recycle(client);
    }
  }

  private com.squareup.okhttp.Call replaceConfigMapAsync(ApiClient client, String name, String namespace, V1ConfigMap body, ApiCallback<V1ConfigMap> callback) throws ApiException {
    return new CoreV1Api(client).replaceNamespacedConfigMapAsync(name, namespace, body, pretty, callback);
  }

  private final CallFactory<V1ConfigMap> REPLACE_CONFIGMAP = (requestParams, usage, cont, callback) -> {
    return replaceConfigMapAsync(usage, requestParams.name, requestParams.namespace, (V1ConfigMap) requestParams.body, callback);
  };
  
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
  
  /**
   * Delete config map
   * @param name Name
   * @param namespace Namespace
   * @param deleteOptions Delete options
   * @return Status of deletion
   * @throws ApiException API Exception
   */
  public V1Status deleteConfigMap(String name, String namespace, V1DeleteOptions deleteOptions) throws ApiException {
    ApiClient client = helper.take();
    try {
      return new CoreV1Api(client).deleteNamespacedConfigMap(name, namespace, deleteOptions, pretty, gracePeriodSeconds,
        orphanDependents, propagationPolicy);
    } finally {
      helper.recycle(client);
    }
  }

  private com.squareup.okhttp.Call deleteConfigMapAsync(ApiClient client, String name, String namespace, V1DeleteOptions deleteOptions, ApiCallback<V1Status> callback) throws ApiException {
    return new CoreV1Api(client).deleteNamespacedConfigMapAsync(name, namespace, deleteOptions, pretty, gracePeriodSeconds, orphanDependents, propagationPolicy, callback);
  }

  private final CallFactory<V1Status> DELETE_CONFIGMAP = (requestParams, usage, cont, callback) -> {
    return deleteConfigMapAsync(usage, requestParams.name, requestParams.namespace, (V1DeleteOptions) requestParams.body, callback);
  };
  
  /**
   * Asynchronous step for deleting config map
   * @param name Name
   * @param namespace Namespace
   * @param responseStep Response step for when call completes
   * @return Asynchronous step
   */
  public Step deleteConfigMapAsync(String name, String namespace, ResponseStep<V1Status> responseStep) {
    return createRequestAsync(responseStep, new RequestParams("deleteConfigMap", namespace, name, null), DELETE_CONFIGMAP);
  }
  
  /* Pods */
  
  /**
   * List pods
   * @param namespace Namespace
   * @return Listed pods
   * @throws ApiException API Exception
   */
  public V1PodList listPod(String namespace) throws ApiException {
    String _continue = "";
    ApiClient client = helper.take();
    try {
      return new CoreV1Api(client).listNamespacedPod(namespace, pretty, _continue, fieldSelector,
        includeUninitialized, labelSelector, limit, resourceVersion, timeoutSeconds, watch);
    } finally {
      helper.recycle(client);
    }
  }

  private com.squareup.okhttp.Call listPodAsync(ApiClient client, String namespace, String _continue, ApiCallback<V1PodList> callback) throws ApiException {
    return new CoreV1Api(client).listNamespacedPodAsync(namespace, pretty, _continue,
      fieldSelector, includeUninitialized, labelSelector, limit, resourceVersion, timeoutSeconds, watch, callback);
  }

  private final CallFactory<V1PodList> LIST_POD = (requestParams, usage, cont, callback) -> {
    return listPodAsync(usage, requestParams.namespace, cont, callback);
  };
  
  /**
   * Asynchronous step for listing pods
   * @param namespace Namespace
   * @param responseStep Response step for when call completes
   * @return Asynchronous step
   */
  public Step listPodAsync(String namespace, ResponseStep<V1PodList> responseStep) {
    return createRequestAsync(responseStep, new RequestParams("listPod", namespace, null, null), LIST_POD);
  }
  
  /**
   * Read pod
   * @param name Name
   * @param namespace Namespace
   * @return Read pod
   * @throws ApiException API Exception
   */
  public V1Pod readPod(String name, String namespace) throws ApiException {
    ApiClient client = helper.take();
    try {
      return new CoreV1Api(client).readNamespacedPod(name, namespace, pretty, exact, export);
    } finally {
      helper.recycle(client);
    }
  }

  private com.squareup.okhttp.Call readPodAsync(ApiClient client, String name, String namespace, ApiCallback<V1Pod> callback) throws ApiException {
    return new CoreV1Api(client).readNamespacedPodAsync(name, namespace, pretty, exact, export, callback);
  }

  private final CallFactory<V1Pod> READ_POD = (requestParams, usage, cont, callback) -> {
    return readPodAsync(usage, requestParams.name, requestParams.namespace, callback);
  };
  
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
  
  /**
   * Create pod
   * @param namespace Namespace
   * @param body Body
   * @return Created pod
   * @throws ApiException API Exception
   */
  public V1Pod createPod(String namespace, V1Pod body) throws ApiException {
    ApiClient client = helper.take();
    try {
      return new CoreV1Api(client).createNamespacedPod(namespace, body, pretty);
    } finally {
      helper.recycle(client);
    }
  }

  private com.squareup.okhttp.Call createPodAsync(ApiClient client, String namespace, V1Pod body, ApiCallback<V1Pod> callback) throws ApiException {
    return new CoreV1Api(client).createNamespacedPodAsync(namespace, body, pretty, callback);
  }

  private final CallFactory<V1Pod> CREATE_POD = (requestParams, usage, cont, callback) -> {
    return createPodAsync(usage, requestParams.namespace, (V1Pod) requestParams.body, callback);
  };
  
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
  
  /**
   * Replace pod
   * @param name Name
   * @param namespace Namespace
   * @param body Body
   * @return Replaced pod
   * @throws ApiException API Exception
   */
  public V1Pod replacePod(String name, String namespace, V1Pod body) throws ApiException {
    ApiClient client = helper.take();
    try {
      return new CoreV1Api(client).replaceNamespacedPod(name, namespace, body, pretty);
    } finally {
      helper.recycle(client);
    }
  }

  private com.squareup.okhttp.Call replacePodAsync(ApiClient client, String name, String namespace, V1Pod body, ApiCallback<V1Pod> callback) throws ApiException {
    return new CoreV1Api(client).replaceNamespacedPodAsync(name, namespace, body, pretty, callback);
  }

  private final CallFactory<V1Pod> REPLACE_POD = (requestParams, usage, cont, callback) -> {
    return replacePodAsync(usage, requestParams.name, requestParams.namespace, (V1Pod) requestParams.body, callback);
  };
  
  /**
   * Asynchronous step for replacing pod
   * @param name Name
   * @param namespace Namespace
   * @param body Body
   * @param responseStep Response step for when call completes
   * @return Asynchronous step
   */
  public Step replacePodAsync(String name, String namespace, V1Pod body, ResponseStep<V1Pod> responseStep) {
    return createRequestAsync(responseStep, new RequestParams("replacePod", namespace, name, body), REPLACE_POD);
  }
  
  /**
   * Delete pod
   * @param name Name
   * @param namespace Namespace
   * @param deleteOptions Delete options
   * @return Status of deletion
   * @throws ApiException API Exception
   */
  public V1Status deletePod(String name, String namespace, V1DeleteOptions deleteOptions) throws ApiException {
    ApiClient client = helper.take();
    try {
      return new CoreV1Api(client).deleteNamespacedPod(name, namespace, deleteOptions, pretty, gracePeriodSeconds,
        orphanDependents, propagationPolicy);
    } finally {
      helper.recycle(client);
    }
  }

  private com.squareup.okhttp.Call deletePodAsync(ApiClient client, String name, String namespace, V1DeleteOptions deleteOptions, ApiCallback<V1Status> callback) throws ApiException {
    return new CoreV1Api(client).deleteNamespacedPodAsync(name, namespace, deleteOptions, pretty, gracePeriodSeconds, orphanDependents, propagationPolicy, callback);
  }

  private final CallFactory<V1Status> DELETE_POD = (requestParams, usage, cont, callback) -> {
    return deletePodAsync(usage, requestParams.name, requestParams.namespace, (V1DeleteOptions) requestParams.body, callback);
  };
  
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
  
  /**
   * Delete collection of pods
   * @param namespace Namespace
   * @return Status of deletion
   * @throws ApiException API Exception
   */
  public V1Status deleteCollectionPod(String namespace) throws ApiException {
    String _continue = "";
    ApiClient client = helper.take();
    try {
      return new CoreV1Api(client).deleteCollectionNamespacedPod(namespace, pretty, _continue, fieldSelector,
        includeUninitialized, labelSelector, limit, resourceVersion, timeoutSeconds, watch);
    } finally {
      helper.recycle(client);
    }
  }

  private com.squareup.okhttp.Call deleteCollectionPodAsync(ApiClient client, String namespace, String _continue, ApiCallback<V1Status> callback) throws ApiException {
    return new CoreV1Api(client).deleteCollectionNamespacedPodAsync(namespace, pretty, _continue, fieldSelector,
        includeUninitialized, labelSelector, limit, resourceVersion, timeoutSeconds, watch, callback);
  }

  private final CallFactory<V1Status> DELETECOLLECTION_POD = (requestParams, usage, cont, callback) -> {
    return deleteCollectionPodAsync(usage, requestParams.namespace, cont, callback);
  };
  
  /**
   * Asynchronous step for deleting collection of pods
   * @param namespace Namespace
   * @param responseStep Response step for when call completes
   * @return Asynchronous step
   */
  public Step deleteCollectionPodAsync(String namespace, ResponseStep<V1Status> responseStep) {
    return createRequestAsync(responseStep, new RequestParams("deleteCollection", namespace, null, null), DELETECOLLECTION_POD);
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

  private final CallFactory<V1ServiceList> LIST_SERVICE = (requestParams, usage, cont, callback) -> {
    return listServiceAsync(usage, requestParams.namespace, cont, callback);
  };
  
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

  private final CallFactory<V1Service> READ_SERVICE = (requestParams, usage, cont, callback) -> {
    return readServiceAsync(usage, requestParams.name, requestParams.namespace, callback);
  };
  
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
  
  /**
   * Create service
   * @param namespace Namespace
   * @param body Body
   * @return Created service
   * @throws ApiException API Exception
   */
  public V1Service createService(String namespace, V1Service body) throws ApiException {
    ApiClient client = helper.take();
    try {
      return new CoreV1Api(client).createNamespacedService(namespace, body, pretty);
    } finally {
      helper.recycle(client);
    }
  }

  private com.squareup.okhttp.Call createServiceAsync(ApiClient client, String namespace, V1Service body, ApiCallback<V1Service> callback) throws ApiException {
    return new CoreV1Api(client).createNamespacedServiceAsync(namespace, body, pretty, callback);
  }

  private final CallFactory<V1Service> CREATE_SERVICE = (requestParams, usage, cont, callback) -> {
    return createServiceAsync(usage, requestParams.namespace, (V1Service) requestParams.body, callback);
  };
  
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
   * Replace service
   * @param name Name
   * @param namespace Namespace
   * @param body Body
   * @return Replaced service
   * @throws ApiException API Exception
   */
  public V1Service replaceService(String name, String namespace, V1Service body) throws ApiException {
    ApiClient client = helper.take();
    try {
      return new CoreV1Api(client).replaceNamespacedService(name, namespace, body, pretty);
    } finally {
      helper.recycle(client);
    }
  }

  private com.squareup.okhttp.Call replaceServiceAsync(ApiClient client, String name, String namespace, V1Service body, ApiCallback<V1Service> callback) throws ApiException {
    return new CoreV1Api(client).replaceNamespacedServiceAsync(name, namespace, body, pretty, callback);
  }

  private final CallFactory<V1Service> REPLACE_SERVICE = (requestParams, usage, cont, callback) -> {
    return replaceServiceAsync(usage, requestParams.name, requestParams.namespace, (V1Service) requestParams.body, callback);
  };
  
  /**
   * Asynchronous step for replacing service
   * @param name Name
   * @param namespace Namespace
   * @param body Body
   * @param responseStep Response step for when call completes
   * @return Asynchronous step
   */
  public Step replaceServiceAsync(String name, String namespace, V1Service body, ResponseStep<V1Service> responseStep) {
    return createRequestAsync(responseStep, new RequestParams("replaceService", namespace, name, body), REPLACE_SERVICE);
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

  private final CallFactory<V1Status> DELETE_SERVICE = (requestParams, usage, cont, callback) -> {
    return deleteServiceAsync(usage, requestParams.name, requestParams.namespace, callback);
  };
  
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
  
  /**
   * List events
   * @param namespace Namespace
   * @return List of events
   * @throws ApiException API Exception
   */
  public V1EventList listEent(String namespace) throws ApiException {
    String _continue = "";
    ApiClient client = helper.take();
    try {
      return new CoreV1Api(client).listNamespacedEvent(namespace, pretty, _continue, fieldSelector,
        includeUninitialized, labelSelector, limit, resourceVersion, timeoutSeconds, watch);
    } finally {
      helper.recycle(client);
    }
  }

  private com.squareup.okhttp.Call listEventAsync(ApiClient client, String namespace, String _continue, ApiCallback<V1EventList> callback) throws ApiException {
    return new CoreV1Api(client).listNamespacedEventAsync(namespace, pretty, _continue,
      fieldSelector, includeUninitialized, labelSelector, limit, resourceVersion, timeoutSeconds, watch, callback);
  }

  private final CallFactory<V1EventList> LIST_EVENT = (requestParams, usage, cont, callback) -> {
    return listEventAsync(usage, requestParams.namespace, cont, callback);
  };
  
  /**
   * Asynchronous step for listing events
   * @param namespace Namespace
   * @param responseStep Response step for when call completes
   * @return Asynchronous step
   */
  public Step listEventAsync(String namespace, ResponseStep<V1EventList> responseStep) {
    return createRequestAsync(responseStep, new RequestParams("listEvent", namespace, null, null), LIST_EVENT);
  }
  
  /**
   * Read event
   * @param name Name
   * @param namespace Namespace
   * @return Read event
   * @throws ApiException API Exception
   */
  public V1Event readEvent(String name, String namespace) throws ApiException {
    ApiClient client = helper.take();
    try {
      return new CoreV1Api(client).readNamespacedEvent(name, namespace, pretty, exact, export);
    } finally {
      helper.recycle(client);
    }
  }

  private com.squareup.okhttp.Call readEventAsync(ApiClient client, String name, String namespace, ApiCallback<V1Event> callback) throws ApiException {
    return new CoreV1Api(client).readNamespacedEventAsync(name, namespace, pretty, exact, export, callback);
  }

  private final CallFactory<V1Event> READ_EVENT = (requestParams, usage, cont, callback) -> {
    return readEventAsync(usage, requestParams.name, requestParams.namespace, callback);
  };
  
  /**
   * Asynchronous step for reading event
   * @param name Name
   * @param namespace Namespace
   * @param responseStep Response step for when call completes
   * @return Asynchronous step
   */
  public Step readEventAsync(String name, String namespace, ResponseStep<V1Event> responseStep) {
    return createRequestAsync(responseStep, new RequestParams("readEvent", namespace, name, null), READ_EVENT);
  }
  
  /**
   * Create event
   * @param namespace Namespace
   * @param body Body
   * @return Created service
   * @throws ApiException API Exception
   */
  public V1Event createEvent(String namespace, V1Event body) throws ApiException {
    ApiClient client = helper.take();
    try {
      return new CoreV1Api(client).createNamespacedEvent(namespace, body, pretty);
    } finally {
      helper.recycle(client);
    }
  }

  private com.squareup.okhttp.Call createEventAsync(ApiClient client, String namespace, V1Event body, ApiCallback<V1Event> callback) throws ApiException {
    return new CoreV1Api(client).createNamespacedEventAsync(namespace, body, pretty, callback);
  }

  private final CallFactory<V1Event> CREATE_EVENT = (requestParams, usage, cont, callback) -> {
    return createEventAsync(usage, requestParams.namespace, (V1Event) requestParams.body, callback);
  };
  
  /**
   * Asynchronous step for creating event
   * @param namespace Namespace
   * @param body Body
   * @param responseStep Response step for when call completes
   * @return Asynchronous step
   */
  public Step createEventAsync(String namespace, V1Service body, ResponseStep<V1Event> responseStep) {
    return createRequestAsync(responseStep, new RequestParams("createEvent", namespace, null, body), CREATE_EVENT);
  }
  
  /**
   * Delete event
   * @param name Name
   * @param namespace Namespace
   * @param deleteOptions Deletion options
   * @return Status of deletion
   * @throws ApiException API Exception
   */
  public V1Status deleteEvent(String name, String namespace, V1DeleteOptions deleteOptions) throws ApiException {
    ApiClient client = helper.take();
    try {
      return new CoreV1Api(client).deleteNamespacedEvent(name, namespace, deleteOptions, pretty, gracePeriodSeconds,
        orphanDependents, propagationPolicy);
    } finally {
      helper.recycle(client);
    }
  }

  private com.squareup.okhttp.Call deleteEventAsync(ApiClient client, String name, String namespace, V1DeleteOptions deleteOptions, ApiCallback<V1Status> callback) throws ApiException {
    return new CoreV1Api(client).deleteNamespacedEventAsync(name, namespace, deleteOptions, pretty, gracePeriodSeconds, orphanDependents, propagationPolicy, callback);
  }

  private final CallFactory<V1Status> DELETE_EVENT = (requestParams, usage, cont, callback) -> {
    return deleteEventAsync(usage, requestParams.name, requestParams.namespace, (V1DeleteOptions) requestParams.body, callback);
  };
  
  /**
   * Asynchronous step for deleting event
   * @param name Name
   * @param namespace Namespace
   * @param responseStep Response step for when call completes
   * @return Asynchronous step
   */
  public Step deleteEventAsync(String name, String namespace, ResponseStep<V1Status> responseStep) {
    return createRequestAsync(responseStep, new RequestParams("deleteEvent", namespace, name, null), DELETE_EVENT);
  }
  
  /* Persistent Volume Claims */
  
  /**
   * List persistent volume claims
   * @param namespace Namespace
   * @return List of persistent volume claims
   * @throws ApiException API Exception
   */
  public V1PersistentVolumeClaimList listPersistentVolumeClaim(String namespace) throws ApiException {
    String _continue = "";
    ApiClient client = helper.take();
    try {
      return new CoreV1Api(client).listNamespacedPersistentVolumeClaim(namespace, pretty, _continue, fieldSelector,
        includeUninitialized, labelSelector, limit, resourceVersion, timeoutSeconds, watch);
    } finally {
      helper.recycle(client);
    }
  }

  private com.squareup.okhttp.Call listPersistentVolumeClaimAsync(ApiClient client, String namespace, String _continue, ApiCallback<V1PersistentVolumeClaimList> callback) throws ApiException {
    return new CoreV1Api(client).listNamespacedPersistentVolumeClaimAsync(namespace, pretty, _continue,
      fieldSelector, includeUninitialized, labelSelector, limit, resourceVersion, timeoutSeconds, watch, callback);
  }

  private final CallFactory<V1PersistentVolumeClaimList> LIST_PERSISTENTVOLUMECLAIM = (requestParams, usage, cont, callback) -> {
    return listPersistentVolumeClaimAsync(usage, requestParams.namespace, cont, callback);
  };
  
  /**
   * Asynchronous step for listing persistent volume claims
   * @param namespace Namespace
   * @param responseStep Response step for when call completes
   * @return Asynchronous step
   */
  public Step listPersistentVolumeClaimAsync(String namespace, ResponseStep<V1PersistentVolumeClaimList> responseStep) {
    return createRequestAsync(responseStep, new RequestParams("listPersistentVolumeClaim", namespace, null, null), LIST_PERSISTENTVOLUMECLAIM);
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
      return new CoreV1Api(client).listPersistentVolume(pretty, _continue, fieldSelector, includeUninitialized,
        labelSelector, limit, resourceVersion, timeoutSeconds, watch);
    } finally {
      helper.recycle(client);
    }
  }

  private com.squareup.okhttp.Call listPersistentVolumeAsync(ApiClient client, String _continue, ApiCallback<V1PersistentVolumeList> callback) throws ApiException {
    return new CoreV1Api(client).listPersistentVolumeAsync(pretty, _continue,
      fieldSelector, includeUninitialized, labelSelector, limit, resourceVersion, timeoutSeconds, watch, callback);
  }

  private final CallFactory<V1PersistentVolumeList> LIST_PERSISTENTVOLUME = (requestParams, usage, cont, callback) -> {
    return listPersistentVolumeAsync(usage, cont, callback);
  };
  
  /**
   * Asynchronous step for listing persistent volumes
   * @param responseStep Response step for when call completes
   * @return Asynchronous step
   */
  public Step listPersistentVolumeAsync(ResponseStep<V1PersistentVolumeList> responseStep) {
    return createRequestAsync(responseStep, new RequestParams("listPersistentVolume", null, null, null), LIST_PERSISTENTVOLUME);
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

  private final CallFactory<V1Secret> READ_SECRET = (requestParams, usage, cont, callback) -> {
    return readSecretAsync(usage, requestParams.name, requestParams.namespace, callback);
  };
  
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

  private final CallFactory<V1SubjectAccessReview> CREATE_SUBJECTACCESSREVIEW = (requestParams, usage, cont, callback) -> {
    return createSubjectAccessReviewAsync(usage, (V1SubjectAccessReview) requestParams.body, callback);
  };
  
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

  private final CallFactory<V1SelfSubjectAccessReview> CREATE_SELFSUBJECTACCESSREVIEW = (requestParams, usage, cont, callback) -> {
    return createSelfSubjectAccessReviewAsync(usage, (V1SelfSubjectAccessReview) requestParams.body, callback);
  };
  
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
      return new AuthorizationV1Api(client).createSelfSubjectRulesReview(body, pretty);
    } finally {
      helper.recycle(client);
    }
  }

  private com.squareup.okhttp.Call createSelfSubjectRulesReviewAsync(ApiClient client, V1SelfSubjectRulesReview body, ApiCallback<V1SelfSubjectRulesReview> callback) throws ApiException {
    return new AuthorizationV1Api(client).createSelfSubjectRulesReviewAsync(body, pretty, callback);
  }

  private final CallFactory<V1SelfSubjectRulesReview> CREATE_SELFSUBJECTRULESREVIEW = (requestParams, usage, cont, callback) -> {
    return createSelfSubjectRulesReviewAsync(usage, (V1SelfSubjectRulesReview) requestParams.body, callback);
  };
  
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

  private com.squareup.okhttp.Call createTokenReviewAsync(ApiClient client, V1TokenReview body, ApiCallback<V1TokenReview> callback) throws ApiException {
    return new AuthenticationV1Api(client).createTokenReviewAsync(body, pretty, callback);
  }

  private final CallFactory<V1TokenReview> CREATE_TOKENREVIEW = (requestParams, usage, cont, callback) -> {
    return createTokenReviewAsync(usage, (V1TokenReview) requestParams.body, callback);
  };
  
  /**
   * Asynchronous step for creating token review
   * @param body Body
   * @param responseStep Response step for when call completes
   * @return Asynchronous step
   */
  public Step createTokenReviewAsync(V1TokenReview body, ResponseStep<V1TokenReview> responseStep) {
    return createRequestAsync(responseStep, new RequestParams("createTokenReview", null, null, body), CREATE_TOKENREVIEW);
  }
  
  /* Ingress */
  
  /**
   * List ingress
   * @param namespace Namespace
   * @return Listed ingress
   * @throws ApiException API Exception
   */
  public V1beta1IngressList listIngress(String namespace) throws ApiException {
    String _continue = "";
    ApiClient client = helper.take();
    try {
      return new ExtensionsV1beta1Api(client).listNamespacedIngress(namespace, pretty, _continue, fieldSelector,
        includeUninitialized, labelSelector, limit, resourceVersion, timeoutSeconds, watch);
    } finally {
      helper.recycle(client);
    }
  }

  private com.squareup.okhttp.Call listIngressAsync(ApiClient client, String namespace, String _continue, ApiCallback<V1beta1IngressList> callback) throws ApiException {
    return new ExtensionsV1beta1Api(client).listNamespacedIngressAsync(namespace, pretty, _continue,
      fieldSelector, includeUninitialized, labelSelector, limit, resourceVersion, timeoutSeconds, watch, callback);
  }

  private final CallFactory<V1beta1IngressList> LIST_INGRESS = (requestParams, usage, cont, callback) -> {
    return listIngressAsync(usage, requestParams.namespace, cont, callback);
  };
  
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

  private final CallFactory<V1beta1Ingress> READ_INGRESS = (requestParams, usage, cont, callback) -> {
    return readIngressAsync(usage, requestParams.name, requestParams.namespace, callback);
  };
  
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
  
  /**
   * Create ingress
   * @param namespace Namespace
   * @param body Body
   * @return Created ingress
   * @throws ApiException API Exception
   */
  public V1beta1Ingress createIngress(String namespace, V1beta1Ingress body) throws ApiException {
    ApiClient client = helper.take();
    try {
      return new ExtensionsV1beta1Api(client).createNamespacedIngress(namespace, body, pretty);
    } finally {
      helper.recycle(client);
    }
  }

  private com.squareup.okhttp.Call createIngressAsync(ApiClient client, String namespace, V1beta1Ingress body, ApiCallback<V1beta1Ingress> callback) throws ApiException {
    return new ExtensionsV1beta1Api(client).createNamespacedIngressAsync(namespace, body, pretty, callback);
  }

  private final CallFactory<V1beta1Ingress> CREATE_INGRESS = (requestParams, usage, cont, callback) -> {
    return createIngressAsync(usage, requestParams.namespace, (V1beta1Ingress) requestParams.body, callback);
  };
  
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
  
  /**
   * Replace ingress
   * @param name Name
   * @param namespace Namespace
   * @param body Body
   * @return Replaced ingress
   * @throws ApiException API Exception
   */
  public V1beta1Ingress replaceIngress(String name, String namespace, V1beta1Ingress body) throws ApiException {
    ApiClient client = helper.take();
    try {
      return new ExtensionsV1beta1Api(client).replaceNamespacedIngress(name, namespace, body, pretty);
    } finally {
      helper.recycle(client);
    }
  }

  private com.squareup.okhttp.Call replaceIngressAsync(ApiClient client, String name, String namespace, V1beta1Ingress body, ApiCallback<V1beta1Ingress> callback) throws ApiException {
    return new ExtensionsV1beta1Api(client).replaceNamespacedIngressAsync(name, namespace, body, pretty, callback);
  }

  private final CallFactory<V1beta1Ingress> REPLACE_INGRESS = (requestParams, usage, cont, callback) -> {
    return replaceIngressAsync(usage, requestParams.name, requestParams.namespace, (V1beta1Ingress) requestParams.body, callback);
  };
  
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

  private final CallFactory<V1Status> DELETE_INGRESS = (requestParams, usage, cont, callback) -> {
    return deleteIngressAsync(usage, requestParams.name, requestParams.namespace, (V1DeleteOptions) requestParams.body, callback);
  };
  
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
  
  private static abstract class BaseApiCallback<T> implements ApiCallback<T> {
    @Override
    public void onDownloadProgress(long bytesRead, long contentLength, boolean done) {
      // no-op
    }

    @Override
    public void onUploadProgress(long bytesWritten, long contentLength, boolean done) {
      // no-op
    }
  }

  @FunctionalInterface
  interface CallFactory<T> {
    public Call generate(RequestParams requestParams, ApiClient client, String cont, ApiCallback<T> callback) throws ApiException;
  }
  
  static final class RequestParams {
    public final String call;
    public final String namespace;
    public final String name;
    public final Object body;
    
    public RequestParams(String call, String namespace, String name, Object body) {
      this.call = call;
      this.namespace = namespace;
      this.name = name;
      this.body = body;
    }
  }
  
  static final class CallResponse<T> {
    public final T result;
    public final ApiException e;
    public final int statusCode;
    public final Map<String, List<String>> responseHeaders;
    
    public CallResponse(T result, ApiException e, int statusCode, Map<String, List<String>> responseHeaders) {
      this.result = result;
      this.e = e;
      this.statusCode = statusCode;
      this.responseHeaders = responseHeaders;
    }
  }
  
  /**
   * Failed or timed-out call retry strategy
   * 
   */
  public interface RetryStrategy {
    /**
     * Initialization that provides reference to step that should be invoked on a retry attempt
     * @param retryStep Retry step
     */
    public void setRetryStep(Step retryStep);
    
    /**
     * Called during {@link ResponseStep#onFailure(Packet, ApiException, int, Map)} to decide
     * if another retry attempt will occur.
     * @param conflictStep Conflict step, or null
     * @param packet Packet
     * @param e ApiException thrown by Kubernetes client; will be null for simple timeout
     * @param statusCode HTTP response status code; will be 0 for simple timeout
     * @param responseHeaders HTTP response headers; will be null for simple timeout
     * @return Desired next action which should specify retryStep.  Return null when call will not be retried.
     */
    public NextAction doPotentialRetry(Step conflictStep, Packet packet, ApiException e, int statusCode, Map<String, List<String>> responseHeaders);
    
    /**
     * Called when retry count, or other statistics, should be reset, such as when partial list 
     * was returned and new request for next portion of list (continue) is invoked.
     */
    public void reset();
  }
  
  private static final Random R = new Random();
  private static final int HIGH = 200;
  private static final int LOW = 10;
  private static final int SCALE = 100;
  private static final int MAX = 10000;
  
  private final class DefaultRetryStrategy implements RetryStrategy {
    private long retryCount = 0;
    private Step retryStep = null;
    
    @Override
    public void setRetryStep(Step retryStep) {
      this.retryStep = retryStep;
    }

    @Override
    public NextAction doPotentialRetry(Step conflictStep, Packet packet, ApiException e, int statusCode,
        Map<String, List<String>> responseHeaders) {
      // Check statusCode, many statuses should not be retried
      // https://github.com/kubernetes/community/blob/master/contributors/devel/api-conventions.md#http-status-codes
      if (statusCode == 0   /* simple timeout */ ||
          statusCode == 429 /* StatusTooManyRequests */ ||
          statusCode == 500 /* StatusInternalServerError */ ||
          statusCode == 503 /* StatusServiceUnavailable */ ||
          statusCode == 504 /* StatusServerTimeout */) {
        
        // exponential back-off
        long waitTime = Math.min((2 << ++retryCount) * SCALE, MAX) + (R.nextInt(HIGH - LOW) + LOW);
        
        if (statusCode == 0 || statusCode == 504 /* StatusServerTimeout */) {
          // increase server timeout
          timeoutSeconds *= 2;
        }
        
        NextAction na = new NextAction();
        if (statusCode == 0 && retryCount <= maxRetryCount) {
          na.invoke(retryStep, packet);
        } else {
          LOGGER.info(MessageKeys.ASYNC_RETRY, String.valueOf(waitTime));
          na.delay(retryStep, packet, waitTime, TimeUnit.MILLISECONDS);
        }
        return na;
      } else if (statusCode == 409 /* Conflict */ && conflictStep != null) {
        // Conflict is an optimistic locking failure.  Therefore, we can't
        // simply retry the request.  Instead, application code needs to rebuild
        // the request based on latest contents.  If provided, a conflict step will do that.
        
        // exponential back-off
        long waitTime = Math.min((2 << ++retryCount) * SCALE, MAX) + (R.nextInt(HIGH - LOW) + LOW);
        
        LOGGER.info(MessageKeys.ASYNC_RETRY, String.valueOf(waitTime));
        NextAction na = new NextAction();
        na.delay(conflictStep, packet, waitTime, TimeUnit.MILLISECONDS);
        return na;
      }
      
      // otherwise, we will not retry
      return null;
    }

    @Override
    public void reset() {
      retryCount = 0;
    }
  }

  private class AsyncRequestStep<T> extends Step {
    private final RequestParams requestParams;
    private final CallFactory<T> factory;
    
    public AsyncRequestStep(ResponseStep<T> next, RequestParams requestParams, CallFactory<T> factory) {
      super(next);
      this.requestParams = requestParams;
      this.factory = factory;
      next.setPrevious(this);
    }

    @Override
    public NextAction apply(Packet packet) {
      // clear out earlier results
      String cont = null;
      RetryStrategy retry = null;
      Component oldResponse = packet.getComponents().remove(RESPONSE_COMPONENT_NAME);
      if (oldResponse != null) {
        @SuppressWarnings("unchecked")
        CallResponse<T> old = oldResponse.getSPI(CallResponse.class);
        if (old != null && old.result != null) {
          // called again, access continue value, if available
          cont = accessContinue(old.result);
        }
        
        retry = oldResponse.getSPI(RetryStrategy.class);
      }
      String _continue = (cont != null) ? cont : "";
      if (retry == null) {
        retry = new DefaultRetryStrategy();
        retry.setRetryStep(this);
      }
      RetryStrategy _retry = retry;

      LOGGER.fine(MessageKeys.ASYNC_REQUEST, requestParams.call, requestParams.namespace, requestParams.name, requestParams.body, fieldSelector, labelSelector, resourceVersion);

      AtomicBoolean didResume = new AtomicBoolean(false);
      AtomicBoolean didRecycle = new AtomicBoolean(false);
      ApiClient client = helper.take();
      return doSuspend((fiber) -> {
        ApiCallback<T> callback = new BaseApiCallback<T>() {
          @Override
          public void onFailure(ApiException e, int statusCode, Map<String, List<String>> responseHeaders) {
            if (didResume.compareAndSet(false, true)) {
              if (statusCode != NOT_FOUND) {
                LOGGER.info(MessageKeys.ASYNC_FAILURE, e, statusCode, responseHeaders, requestParams.call, requestParams.namespace, requestParams.name, requestParams.body, fieldSelector, labelSelector, resourceVersion);
              }
              
              if (didRecycle.compareAndSet(false, true)) {
                helper.recycle(client);
              }
              packet.getComponents().put(RESPONSE_COMPONENT_NAME, Component.createFor(RetryStrategy.class, _retry, new CallResponse<Void>(null, e, statusCode, responseHeaders)));
              fiber.resume(packet);
            }
          }

          @Override
          public void onSuccess(T result, int statusCode, Map<String, List<String>> responseHeaders) {
            if (didResume.compareAndSet(false, true)) {
              LOGGER.fine(MessageKeys.ASYNC_SUCCESS, result, statusCode, responseHeaders);

              if (didRecycle.compareAndSet(false, true)) {
                helper.recycle(client);
              }
              packet.getComponents().put(RESPONSE_COMPONENT_NAME, Component.createFor(new CallResponse<T>(result, null, statusCode, responseHeaders)));
              fiber.resume(packet);
            }
          }
        };
        
        try {
          Call c = factory.generate(requestParams, client, _continue, callback);
          
          // timeout handling
          fiber.owner.getExecutor().schedule(() -> {
            if (didRecycle.compareAndSet(false, true)) {
              // don't recycle on timeout because state is unknown
              // usage.recycle();
            }
            if (didResume.compareAndSet(false, true)) {
              try {
                c.cancel();
              } finally {
                LOGGER.info(MessageKeys.ASYNC_TIMEOUT, requestParams.call, requestParams.namespace, requestParams.name, requestParams.body, fieldSelector, labelSelector, resourceVersion);
                packet.getComponents().put(RESPONSE_COMPONENT_NAME, Component.createFor(RetryStrategy.class, _retry));
                fiber.resume(packet);
              }
            }
          }, timeoutSeconds, TimeUnit.SECONDS);
        } catch (Throwable t) {
          LOGGER.warning(MessageKeys.ASYNC_FAILURE, t, 0, null, requestParams, requestParams.namespace, requestParams.name, requestParams.body, fieldSelector, labelSelector, resourceVersion);
          if (didRecycle.compareAndSet(false, true)) {
            // don't recycle on throwable because state is unknown
            // usage.recycle();
          }
          if (didResume.compareAndSet(false, true)) {
            packet.getComponents().put(RESPONSE_COMPONENT_NAME, Component.createFor(RetryStrategy.class, _retry));
            fiber.resume(packet);
          }
        }
      });
    }
  }
  
  private <T> Step createRequestAsync(ResponseStep<T> next, RequestParams requestParams, CallFactory<T> factory) {
    return new AsyncRequestStep<T>(next, requestParams, factory);
  }
  
  private static String accessContinue(Object result) {
    String cont = "";
    if (result != null) {
      try {
        Method m = result.getClass().getMethod("getMetadata");
        Object meta = m.invoke(result);
        if (meta instanceof V1ListMeta) {
          return ((V1ListMeta) meta).getContinue();
        }
      } catch (NoSuchMethodException | SecurityException | IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
        // no-op, no-log
      }
    }
    return cont;
  }
}
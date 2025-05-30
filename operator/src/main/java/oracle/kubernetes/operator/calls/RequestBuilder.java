// Copyright (c) 2024, 2025, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.calls;

import java.util.function.Function;
import java.util.function.UnaryOperator;

import io.kubernetes.client.common.KubernetesListObject;
import io.kubernetes.client.common.KubernetesObject;
import io.kubernetes.client.common.KubernetesType;
import io.kubernetes.client.custom.V1Patch;
import io.kubernetes.client.extended.controller.reconciler.Result;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1Pod;
import io.kubernetes.client.openapi.models.V1PodList;
import io.kubernetes.client.openapi.models.V1Status;
import io.kubernetes.client.openapi.models.VersionInfo;
import io.kubernetes.client.util.Watchable;
import io.kubernetes.client.util.generic.KubernetesApiResponse;
import io.kubernetes.client.util.generic.options.CreateOptions;
import io.kubernetes.client.util.generic.options.DeleteOptions;
import io.kubernetes.client.util.generic.options.GetOptions;
import io.kubernetes.client.util.generic.options.ListOptions;
import io.kubernetes.client.util.generic.options.PatchOptions;
import io.kubernetes.client.util.generic.options.UpdateOptions;
import oracle.kubernetes.operator.ResourceCache;
import oracle.kubernetes.operator.work.Packet;

public class RequestBuilder<A extends KubernetesObject, L extends KubernetesListObject> {
  private static final KubernetesApiFactory DEFAULT_KUBERNETES_API_FACTORY = new KubernetesApiFactory() {
  };

  public static <X extends KubernetesObject, Y extends KubernetesListObject>
      KubernetesApi<X, Y> createKubernetesApi(Class<X> apiTypeClass, Class<Y> apiListTypeClass,
                                              String apiGroup, String apiVersion, String resourcePlural,
                                              UnaryOperator<ApiClient> clientSelector) {
    return kubernetesApiFactory.create(apiTypeClass, apiListTypeClass, apiGroup, apiVersion,
            resourcePlural, clientSelector);
  }

  @SuppressWarnings({"FieldMayBeFinal", "CanBeFinal"})
  private static KubernetesApiFactory kubernetesApiFactory = DEFAULT_KUBERNETES_API_FACTORY;

  private static final WatchApiFactory DEFAULT_WATCH_API_FACTORY = new WatchApiFactory() {
  };

  protected static final UnaryOperator<ApiClient> CLIENT_SELECTOR = client -> client;

  public static <X extends KubernetesObject, Y extends KubernetesListObject>
      WatchApi<X> createWatchApi(Class<X> apiTypeClass, Class<Y> apiListTypeClass,
                                 String apiGroup, String apiVersion, String resourcePlural) {
    return watchApiFactory.create(apiTypeClass, apiListTypeClass, apiGroup, apiVersion, resourcePlural);
  }

  @SuppressWarnings({"FieldMayBeFinal", "CanBeFinal"})
  private static WatchApiFactory watchApiFactory = DEFAULT_WATCH_API_FACTORY;

  protected final ResourceCache resourceCache;
  protected final Class<A> apiTypeClass;
  protected final Class<L> apiListTypeClass;
  protected final String apiGroup;
  protected final String apiVersion;
  protected final String resourcePlural;
  protected final String resourceSingular;

  /**
   * Create a request builder.
   * @param resourceCache Resource cache
   * @param apiTypeClass API type
   * @param apiListTypeClass API list type
   * @param apiGroup Group
   * @param apiVersion Version
   * @param resourcePlural Plural
   * @param resourceSingular Singular
   */
  public RequestBuilder(
      ResourceCache resourceCache,
      Class<A> apiTypeClass,
      Class<L> apiListTypeClass,
      String apiGroup,
      String apiVersion,
      String resourcePlural,
      String resourceSingular) {
    this.resourceCache = resourceCache;
    this.apiGroup = apiGroup;
    this.apiVersion = apiVersion;
    this.resourcePlural = resourcePlural;
    this.resourceSingular = resourceSingular;
    this.apiTypeClass = apiTypeClass;
    this.apiListTypeClass = apiListTypeClass;
  }

  String getApiGroup() {
    return apiGroup;
  }

  String getApiVersion() {
    return apiVersion;
  }

  String getResourcePlural() {
    return resourcePlural;
  }

  String getResourceSingular() {
    return resourceSingular;
  }

  /**
   * Step to create resource.
   * @param object Resource
   * @param responseStep Response step
   * @return Request step
   */
  public RequestStep<A, L, A> create(A object, ResponseStep<A> responseStep) {
    return create(object, new CreateOptions(), responseStep);
  }

  /**
   * Step to create resource.
   * @param object Resource
   * @param createOptions Create options
   * @param responseStep Response step
   * @return Request step
   */
  public RequestStep<A, L, A> create(
      A object, CreateOptions createOptions, ResponseStep<A> responseStep) {
    return create(object, createOptions, responseStep, CLIENT_SELECTOR);
  }

  /**
   * Step to create resource.
   * @param object Resource
   * @param createOptions Create options
   * @param responseStep Response step
   * @param clientSelector Client selector
   * @return Request step
   */
  public RequestStep<A, L, A> create(
          A object, CreateOptions createOptions, ResponseStep<A> responseStep,
          UnaryOperator<ApiClient> clientSelector) {
    return new RequestStep.CreateRequestStep<>(
            resourceCache,
            responseStep, apiTypeClass, apiListTypeClass, apiGroup, apiVersion, resourcePlural, resourceSingular,
            object, createOptions, clientSelector);
  }

  /**
   * Create resource.
   * @param object Resource
   * @return Created resource
   * @throws ApiException On failure
   */
  public A create(A object) throws ApiException {
    return create(object, new CreateOptions());
  }

  /**
   * Create resource.
   * @param object Resource
   * @param createOptions Create options
   * @return Created resource
   * @throws ApiException On failure
   */
  public A create(A object, CreateOptions createOptions) throws ApiException {
    return create(object, createOptions, CLIENT_SELECTOR);
  }

  /**
   * Create resource.
   * @param object Resource
   * @param createOptions Create options
   * @param clientSelector Client selector
   * @return Created resource
   * @throws ApiException On failure
   */
  public A create(A object, CreateOptions createOptions, UnaryOperator<ApiClient> clientSelector) throws ApiException {
    DirectResponseStep<A> response = new DirectResponseStep<>();
    RequestStep<A, L, A> step = create(object, createOptions, response, clientSelector);
    step.apply(new Packet());
    return response.get();
  }

  /**
   * Step to delete resource.
   * @param name Name
   * @param responseStep Response step
   * @return Request step
   */
  public RequestStep<A, L, A> delete(String name, ResponseStep<A> responseStep) {
    return delete(name, new DeleteOptions(), responseStep);
  }

  /**
   * Step to delete resource.
   * @param name Name
   * @param deleteOptions Delete options
   * @param responseStep Response step
   * @return Request step
   */
  public RequestStep<A, L, A> delete(
      String name, DeleteOptions deleteOptions, ResponseStep<A> responseStep) {
    return delete(name, deleteOptions, responseStep, CLIENT_SELECTOR);
  }

  /**
   * Step to delete resource.
   * @param name Name
   * @param deleteOptions Delete options
   * @param responseStep Response step
   * @param clientSelector Client selector
   * @return Request step
   */
  public RequestStep<A, L, A> delete(
          String name, DeleteOptions deleteOptions, ResponseStep<A> responseStep,
          UnaryOperator<ApiClient> clientSelector) {
    return new RequestStep.ClusterDeleteRequestStep<>(
            resourceCache,
            responseStep, apiTypeClass, apiListTypeClass, apiGroup, apiVersion, resourcePlural, resourceSingular,
            name, deleteOptions, clientSelector);
  }

  /**
   * Step to delete resource.
   * @param namespace Namespace
   * @param name Name
   * @param responseStep Response step
   * @return Request step
   */
  public RequestStep<A, L, A> delete(
      String namespace, String name, ResponseStep<A> responseStep) {
    return delete(namespace, name, new DeleteOptions(), responseStep);
  }

  /**
   * Step to delete resource.
   * @param namespace Namespace
   * @param name Name
   * @param deleteOptions Delete options
   * @param responseStep Response step
   * @return Request step
   */
  public RequestStep<A, L, A> delete(
      String namespace, String name, DeleteOptions deleteOptions, ResponseStep<A> responseStep) {
    return delete(namespace, name, deleteOptions, responseStep, CLIENT_SELECTOR);
  }

  /**
   * Step to delete resource.
   * @param namespace Namespace
   * @param name Name
   * @param deleteOptions Delete options
   * @param responseStep Response step
   * @param clientSelector Client selector
   * @return Request step
   */
  public RequestStep<A, L, A> delete(
          String namespace, String name, DeleteOptions deleteOptions, ResponseStep<A> responseStep,
          UnaryOperator<ApiClient> clientSelector) {
    return new RequestStep.DeleteRequestStep<>(
            resourceCache,
            responseStep, apiTypeClass, apiListTypeClass, apiGroup, apiVersion, resourcePlural, resourceSingular,
            namespace, name, deleteOptions, clientSelector);
  }

  /**
   * Delete resource.
   * @param name Name
   * @return Deleted resource
   * @throws ApiException On failure
   */
  public A delete(String name) throws ApiException {
    return delete(name, new DeleteOptions());
  }

  /**
   * Delete resource.
   * @param name Name
   * @param deleteOptions Delete options
   * @return Deleted resource
   * @throws ApiException On failure
   */
  public A delete(String name, DeleteOptions deleteOptions) throws ApiException {
    return delete(name, deleteOptions, CLIENT_SELECTOR);
  }

  /**
   * Delete resource.
   * @param name Name
   * @param deleteOptions Delete options
   * @param clientSelector Client selector
   * @return Deleted resource
   * @throws ApiException On failure
   */
  public A delete(String name, DeleteOptions deleteOptions,
                  UnaryOperator<ApiClient> clientSelector) throws ApiException {
    DirectResponseStep<A> response = new DirectResponseStep<>();
    RequestStep<A, L, A> step = delete(name, deleteOptions, response, clientSelector);
    step.apply(new Packet());
    return response.get();
  }

  /**
   * Delete resource.
   * @param namespace Namespace
   * @param name Name
   * @return Deleted resource
   * @throws ApiException On failure
   */
  public A delete(String namespace, String name) throws ApiException {
    return delete(namespace, name, new DeleteOptions());
  }

  /**
   * Delete resource.
   * @param namespace Namespace
   * @param name Name
   * @param deleteOptions Delete options
   * @return Deleted resource
   * @throws ApiException On failure
   */
  public A delete(String namespace, String name, DeleteOptions deleteOptions) throws ApiException {
    return delete(namespace, name, deleteOptions, CLIENT_SELECTOR);
  }

  /**
   * Delete resource.
   * @param namespace Namespace
   * @param name Name
   * @param deleteOptions Delete options
   * @param clientSelector Client selector
   * @return Deleted resource
   * @throws ApiException On failure
   */
  public A delete(String namespace, String name, DeleteOptions deleteOptions,
                  UnaryOperator<ApiClient> clientSelector) throws ApiException {
    DirectResponseStep<A> response = new DirectResponseStep<>();
    RequestStep<A, L, A> step = delete(namespace, name, deleteOptions, response, clientSelector);
    step.apply(new Packet());
    return response.get();
  }

  /**
   * Get resource.
   * @param name Name
   * @param responseStep Response step
   * @return Request step
   */
  public RequestStep<A, L, A> get(String name, ResponseStep<A> responseStep) {
    return get(name, new GetOptions(), responseStep);
  }

  /**
   * Get resource.
   * @param name Name
   * @param getOptions Get options
   * @param responseStep Response step
   * @return Request step
   */
  public RequestStep<A, L, A> get(
      String name, GetOptions getOptions, ResponseStep<A> responseStep) {
    return get(name, getOptions, responseStep, CLIENT_SELECTOR);
  }

  /**
   * Get resource.
   * @param name Name
   * @param getOptions Get options
   * @param responseStep Response step
   * @param clientSelector Client selector
   * @return Request step
   */
  public RequestStep<A, L, A> get(
          String name, GetOptions getOptions, ResponseStep<A> responseStep, UnaryOperator<ApiClient> clientSelector) {
    return new RequestStep.ClusterGetRequestStep<>(
            resourceCache,
            responseStep, apiTypeClass, apiListTypeClass, apiGroup, apiVersion, resourcePlural, resourceSingular,
            name, getOptions, clientSelector);
  }

  /**
   * Get resource.
   * @param namespace Namespace
   * @param name Name
   * @param responseStep Response step
   * @return Request step
   */
  public RequestStep<A, L, A> get(
      String namespace, String name, ResponseStep<A> responseStep) {
    return get(namespace, name, new GetOptions(), responseStep);
  }

  /**
   * Get resource.
   * @param namespace Namespace
   * @param name Name
   * @param getOptions Get options
   * @param responseStep Response step
   * @return Request step
   */
  public RequestStep<A, L, A> get(
      String namespace, String name, GetOptions getOptions, ResponseStep<A> responseStep) {
    return get(namespace, name, getOptions, responseStep, CLIENT_SELECTOR);
  }

  /**
   * Get resource.
   * @param namespace Namespace
   * @param name Name
   * @param getOptions Get options
   * @param responseStep Response step
   * @param clientSelector Client selector
   * @return Request step
   */
  public RequestStep<A, L, A> get(
          String namespace, String name, GetOptions getOptions, ResponseStep<A> responseStep,
          UnaryOperator<ApiClient> clientSelector) {
    return new RequestStep.GetRequestStep<>(
            resourceCache,
            responseStep, apiTypeClass, apiListTypeClass, apiGroup, apiVersion, resourcePlural, resourceSingular,
            namespace, name, getOptions, clientSelector);
  }

  /**
   * Get resource.
   * @param name Name
   * @return Resource
   * @throws ApiException On failure
   */
  public A get(String name) throws ApiException {
    return get(name, new GetOptions());
  }

  /**
   * Get resource.
   * @param name Name
   * @param getOptions Get options
   * @return Resource
   * @throws ApiException On failure
   */
  public A get(String name, GetOptions getOptions) throws ApiException {
    return get(name, getOptions, CLIENT_SELECTOR);
  }

  /**
   * Get resource.
   * @param name Name
   * @param getOptions Get options
   * @param clientSelector Client selector
   * @return Resource
   * @throws ApiException On failure
   */
  public A get(String name, GetOptions getOptions, UnaryOperator<ApiClient> clientSelector) throws ApiException {
    DirectResponseStep<A> response = new DirectResponseStep<>();
    RequestStep<A, L, A> step = get(name, getOptions, response, clientSelector);
    step.apply(new Packet());
    return response.get();
  }

  /**
   * Get resource.
   * @param namespace Namespace
   * @param name Name
   * @return Resource
   * @throws ApiException On failure
   */
  public A get(String namespace, String name) throws ApiException {
    return get(namespace, name, new GetOptions());
  }

  /**
   * Get resource.
   * @param namespace Namespace
   * @param name Name
   * @param getOptions Get options
   * @return Resource
   * @throws ApiException On failure
   */
  public A get(String namespace, String name, GetOptions getOptions) throws ApiException {
    return get(namespace, name, getOptions, CLIENT_SELECTOR);
  }

  /**
   * Get resource.
   * @param namespace Namespace
   * @param name Name
   * @param getOptions Get options
   * @param clientSelector Client selector
   * @return Resource
   * @throws ApiException On failure
   */
  public A get(String namespace, String name, GetOptions getOptions,
               UnaryOperator<ApiClient> clientSelector) throws ApiException {
    DirectResponseStep<A> response = new DirectResponseStep<>();
    RequestStep<A, L, A> step = get(namespace, name, getOptions, response, clientSelector);
    step.apply(new Packet());
    return response.get();
  }

  /**
   * List resources.
   * @param responseStep Response step
   * @return Request step
   */
  public RequestStep<A, L, L> list(ResponseStep<L> responseStep) {
    return list(new ListOptions(), responseStep);
  }

  /**
   * List resources.
   * @param listOptions List options
   * @param responseStep Response step
   * @return Request step
   */
  public RequestStep<A, L, L> list(
      ListOptions listOptions, ResponseStep<L> responseStep) {
    return list(listOptions, responseStep, CLIENT_SELECTOR);
  }

  /**
   * List resources.
   * @param listOptions List options
   * @param responseStep Response step
   * @param clientSelector Client selector
   * @return Request step
   */
  public RequestStep<A, L, L> list(
          ListOptions listOptions, ResponseStep<L> responseStep, UnaryOperator<ApiClient> clientSelector) {
    return new RequestStep.ClusterListRequestStep<>(
            resourceCache,
            responseStep, apiTypeClass, apiListTypeClass, apiGroup, apiVersion, resourcePlural, resourceSingular,
            listOptions, clientSelector);
  }

  /**
   * List resources.
   * @param namespace Namespace
   * @param responseStep Response step
   * @return Request step
   */
  public RequestStep<A, L, L> list(
      String namespace, ResponseStep<L> responseStep) {
    return list(namespace, new ListOptions(), responseStep);
  }

  /**
   * List resources.
   * @param namespace Namespace
   * @param listOptions List options
   * @param responseStep Response step
   * @return Request step
   */
  public RequestStep<A, L, L> list(
      String namespace, ListOptions listOptions, ResponseStep<L> responseStep) {
    return list(namespace, listOptions, responseStep, CLIENT_SELECTOR);
  }

  /**
   * List resources.
   * @param namespace Namespace
   * @param listOptions List options
   * @param responseStep Response step
   * @param clientSelector Client selector
   * @return Request step
   */
  public RequestStep<A, L, L> list(
          String namespace, ListOptions listOptions, ResponseStep<L> responseStep,
          UnaryOperator<ApiClient> clientSelector) {
    return new RequestStep.ListRequestStep<>(
            resourceCache,
            responseStep, apiTypeClass, apiListTypeClass, apiGroup, apiVersion, resourcePlural, resourceSingular,
            namespace, listOptions, clientSelector);
  }

  /**
   * List resources.
   * @return List of resources
   * @throws ApiException On failure
   */
  public L list() throws ApiException {
    return list(new ListOptions());
  }

  /**
   * List resources.
   * @param listOptions List options
   * @return List of resources
   * @throws ApiException On failure
   */
  public L list(ListOptions listOptions) throws ApiException {
    return list(listOptions, CLIENT_SELECTOR);
  }

  /**
   * List resources.
   * @param listOptions List options
   * @param clientSelector Client selector
   * @return List of resources
   * @throws ApiException On failure
   */
  public L list(ListOptions listOptions, UnaryOperator<ApiClient> clientSelector) throws ApiException {
    DirectResponseStep<L> response = new DirectResponseStep<>();
    RequestStep<A, L, L> step = list(listOptions, response, clientSelector);
    step.apply(new Packet());
    return response.get();
  }

  /**
   * List resources.
   * @param namespace Namespace
   * @return List of resources
   * @throws ApiException On failure
   */
  public L list(String namespace) throws ApiException {
    return list(namespace, new ListOptions());
  }

  /**
   * List resources.
   * @param namespace Namespace
   * @param listOptions List options
   * @return List of resources
   * @throws ApiException On failure
   */
  public L list(String namespace, ListOptions listOptions) throws ApiException {
    return list(namespace, listOptions, CLIENT_SELECTOR);
  }

  /**
   * List resources.
   * @param namespace Namespace
   * @param listOptions List options
   * @param clientSupplier Client supplier
   * @return List of resources
   * @throws ApiException On failure
   */
  public L list(String namespace, ListOptions listOptions,
                UnaryOperator<ApiClient> clientSupplier) throws ApiException {
    DirectResponseStep<L> response = new DirectResponseStep<>();
    RequestStep<A, L, L> step = list(namespace, listOptions, response, clientSupplier);
    step.apply(new Packet());
    return response.get();
  }

  /**
   * Update resource.
   * @param object Resource
   * @param responseStep Response step
   * @return Request step
   */
  public RequestStep<A, L, A> update(A object, ResponseStep<A> responseStep) {
    return update(object, new UpdateOptions(), responseStep);
  }

  /**
   * Update resource.
   * @param object Resource
   * @param updateOptions Update options
   * @param responseStep Response step
   * @return Request step
   */
  public RequestStep<A, L, A> update(
      A object, UpdateOptions updateOptions, ResponseStep<A> responseStep) {
    return update(object, updateOptions, responseStep, CLIENT_SELECTOR);
  }

  /**
   * Update resource.
   * @param object Resource
   * @param updateOptions Update options
   * @param responseStep Response step
   * @param clientSelector Client selector
   * @return Request step
   */
  public RequestStep<A, L, A> update(
          A object, UpdateOptions updateOptions, ResponseStep<A> responseStep,
          UnaryOperator<ApiClient> clientSelector) {
    return new RequestStep.UpdateRequestStep<>(
            resourceCache,
            responseStep, apiTypeClass, apiListTypeClass, apiGroup, apiVersion, resourcePlural, resourceSingular,
            object, updateOptions, clientSelector);
  }

  /**
   * Update resource.
   * @param object Resource
   * @return Resource
   * @throws ApiException On failure
   */
  public A update(A object) throws ApiException {
    return update(object, new UpdateOptions());
  }

  /**
   * Update resource.
   * @param object Resource
   * @param updateOptions Update options
   * @return Resource
   * @throws ApiException On failure
   */
  public A update(A object, UpdateOptions updateOptions) throws ApiException {
    return update(object, updateOptions, CLIENT_SELECTOR);
  }

  /**
   * Update resource.
   * @param object Resource
   * @param updateOptions Update options
   * @param clientSupplier Client supplier
   * @return Resource
   * @throws ApiException On failure
   */
  public A update(A object, UpdateOptions updateOptions, UnaryOperator<ApiClient> clientSupplier) throws ApiException {
    DirectResponseStep<A> response = new DirectResponseStep<>();
    RequestStep<A, L, A> step = update(object, updateOptions, response, clientSupplier);
    step.apply(new Packet());
    return response.get();
  }

  /**
   * Patch resource.
   * @param name Name
   * @param patchType Patch type
   * @param patch Patch
   * @param responseStep Response step
   * @return Request step
   */
  public RequestStep<A, L, A> patch(
      String name, String patchType, V1Patch patch, ResponseStep<A> responseStep) {
    return patch(name, patchType, patch, new PatchOptions(), responseStep);
  }

  /**
   * Patch resource.
   * @param name Name
   * @param patchType Patch type
   * @param patch Patch
   * @param patchOptions Patch options
   * @param responseStep Response step
   * @return Request step
   */
  public RequestStep<A, L, A> patch(
      String name, String patchType, V1Patch patch, PatchOptions patchOptions, ResponseStep<A> responseStep) {
    return patch(name, patchType, patch, patchOptions, responseStep, CLIENT_SELECTOR);
  }

  /**
   * Patch resource.
   * @param name Name
   * @param patchType Patch type
   * @param patch Patch
   * @param patchOptions Patch options
   * @param responseStep Response step
   * @param clientSelector Client selector
   * @return Request step
   */
  public RequestStep<A, L, A> patch(
          String name, String patchType, V1Patch patch, PatchOptions patchOptions, ResponseStep<A> responseStep,
          UnaryOperator<ApiClient> clientSelector) {
    return new RequestStep.ClusterPatchRequestStep<>(
            resourceCache,
            responseStep, apiTypeClass, apiListTypeClass, apiGroup, apiVersion, resourcePlural, resourceSingular,
            name, patchType, patch, patchOptions, clientSelector);
  }

  /**
   * Patch resource.
   * @param namespace Namespace
   * @param name Name
   * @param patchType Patch type
   * @param patch Patch
   * @param responseStep Response step
   * @return Request step
   */
  public RequestStep<A, L, A> patch(
      String namespace, String name, String patchType, V1Patch patch, ResponseStep<A> responseStep) {
    return patch(namespace, name, patchType, patch, new PatchOptions(), responseStep);
  }

  /**
   * Patch resource.
   * @param namespace Namespace
   * @param name Name
   * @param patchType Patch type
   * @param patch Patch
   * @param patchOptions Patch options
   * @param responseStep Response step
   * @return Request step
   */
  public RequestStep<A, L, A> patch(
      String namespace, String name, String patchType, V1Patch patch,
      PatchOptions patchOptions, ResponseStep<A> responseStep) {
    return patch(namespace, name, patchType, patch, patchOptions, responseStep, CLIENT_SELECTOR);
  }

  /**
   * Patch resource.
   * @param namespace Namespace
   * @param name Name
   * @param patchType Patch type
   * @param patch Patch
   * @param patchOptions Patch options
   * @param responseStep Response step
   * @param clientSelector Client selector
   * @return Request step
   */
  public RequestStep<A, L, A> patch(
          String namespace, String name, String patchType, V1Patch patch,
          PatchOptions patchOptions, ResponseStep<A> responseStep, UnaryOperator<ApiClient> clientSelector) {
    return new RequestStep.PatchRequestStep<>(
            resourceCache,
            responseStep, apiTypeClass, apiListTypeClass, apiGroup, apiVersion, resourcePlural, resourceSingular,
            namespace, name, patchType, patch, patchOptions, clientSelector);
  }

  /**
   * Patch resource.
   * @param name Name
   * @param patchType Patch type
   * @param patch Patch
   * @return Resource
   * @throws ApiException On failure
   */
  public A patch(String name, String patchType, V1Patch patch) throws ApiException {
    return patch(name, patchType, patch, new PatchOptions());
  }

  /**
   * Patch resource.
   * @param name Name
   * @param patchType Patch type
   * @param patch Patch
   * @param patchOptions Patch options
   * @return Resource
   * @throws ApiException On failure
   */
  public A patch(String name, String patchType, V1Patch patch, PatchOptions patchOptions) throws ApiException {
    return patch(name, patchType, patch, patchOptions, CLIENT_SELECTOR);
  }

  /**
   * Patch resource.
   * @param name Name
   * @param patchType Patch type
   * @param patch Patch
   * @param patchOptions Patch options
   * @param clientSupplier Client supplier
   * @return Resource
   * @throws ApiException On failure
   */
  public A patch(String name, String patchType, V1Patch patch, PatchOptions patchOptions,
                 UnaryOperator<ApiClient> clientSupplier) throws ApiException {
    DirectResponseStep<A> response = new DirectResponseStep<>();
    RequestStep<A, L, A> step = patch(name, patchType, patch, patchOptions, response, clientSupplier);
    step.apply(new Packet());
    return response.get();
  }

  /**
   * Patch resource.
   * @param namespace Namespace
   * @param name Name
   * @param patchType Patch type
   * @param patch Patch
   * @return Resource
   * @throws ApiException On failure
   */
  public A patch(String namespace, String name, String patchType, V1Patch patch) throws ApiException {
    return patch(namespace, name, patchType, patch, new PatchOptions());
  }

  /**
   * Patch resource.
   * @param namespace Namespace
   * @param name Name
   * @param patchType Patch type
   * @param patch Patch
   * @param patchOptions Patch options
   * @return Resource
   * @throws ApiException On failure
   */
  public A patch(String namespace, String name, String patchType, V1Patch patch,
                 PatchOptions patchOptions) throws ApiException {
    return patch(namespace, name, patchType, patch, patchOptions, CLIENT_SELECTOR);
  }

  /**
   * Patch resource.
   * @param namespace Namespace
   * @param name Name
   * @param patchType Patch type
   * @param patch Patch
   * @param patchOptions Patch options
   * @param clientSupplier Client supplier
   * @return Resource
   * @throws ApiException On failure
   */
  public A patch(String namespace, String name, String patchType, V1Patch patch,
                 PatchOptions patchOptions, UnaryOperator<ApiClient> clientSupplier) throws ApiException {
    DirectResponseStep<A> response = new DirectResponseStep<>();
    RequestStep<A, L, A> step = patch(namespace, name, patchType, patch, patchOptions, response, clientSupplier);
    step.apply(new Packet());
    return response.get();
  }

  /**
   * Update status.
   * @param object Resource object
   * @param status Function to get status from resource
   * @param responseStep Response step
   * @return Request step
   */
  public RequestStep<A, L, A> updateStatus(
      A object, Function<A, Object> status, ResponseStep<A> responseStep) {
    return updateStatus(object, status, new UpdateOptions(), responseStep);
  }

  /**
   * Update status.
   * @param object Resource object
   * @param status Function to get status from resource
   * @param updateOptions Update options
   * @param responseStep Response step
   * @return Request step
   */
  public RequestStep<A, L, A> updateStatus(
      A object, Function<A, Object> status,
      UpdateOptions updateOptions, ResponseStep<A> responseStep) {
    return updateStatus(object, status, updateOptions, responseStep, CLIENT_SELECTOR);
  }

  /**
   * Update status.
   * @param object Resource object
   * @param status Function to get status from resource
   * @param updateOptions Update options
   * @param responseStep Response step
   * @param clientSelector Client selector
   * @return Request step
   */
  public RequestStep<A, L, A> updateStatus(
          A object, Function<A, Object> status,
          UpdateOptions updateOptions, ResponseStep<A> responseStep, UnaryOperator<ApiClient> clientSelector) {
    return new RequestStep.UpdateStatusRequestStep<>(
            resourceCache,
            responseStep, apiTypeClass, apiListTypeClass, apiGroup, apiVersion, resourcePlural, resourceSingular,
            object, status, updateOptions, clientSelector);
  }

  /**
   * Update status.
   * @param object Data object
   * @param status Function to get status from data object
   * @return Data object
   * @throws ApiException On failure
   */
  public A updateStatus(A object, Function<A, Object> status) throws ApiException {
    return updateStatus(object, status, new UpdateOptions());
  }

  /**
   * Update status.
   * @param object Data object
   * @param status Function to get status from data object
   * @param updateOptions Update options
   * @return Data object
   * @throws ApiException On failure
   */
  public A updateStatus(A object, Function<A, Object> status,
                        UpdateOptions updateOptions) throws ApiException {
    return updateStatus(object, status, updateOptions, CLIENT_SELECTOR);
  }

  /**
   * Update status.
   * @param object Data object
   * @param status Function to get status from data object
   * @param updateOptions Update options
   * @param clientSupplier Client supplier
   * @return Data object
   * @throws ApiException On failure
   */
  public A updateStatus(A object, Function<A, Object> status,
                        UpdateOptions updateOptions, UnaryOperator<ApiClient> clientSupplier) throws ApiException {
    DirectResponseStep<A> response = new DirectResponseStep<>();
    RequestStep<A, L, A> step = updateStatus(object, status, updateOptions, response, clientSupplier);
    step.apply(new Packet());
    return response.get();
  }

  /**
   * Create watch.
   * @param listOptions the list options
   * @return the watchable
   * @throws ApiException thrown on failure
   */
  public Watchable<A> watch(final ListOptions listOptions) throws ApiException {
    WatchApi<A> client = createWatchApi(apiTypeClass, apiListTypeClass, apiGroup, apiVersion, resourcePlural);
    return client.watch(listOptions);
  }

  /**
   * Create watch.
   * @param namespace the namespace
   * @param listOptions the list options
   * @return the watchable
   * @throws ApiException thrown on failure
   */
  public Watchable<A> watch(String namespace, final ListOptions listOptions) throws ApiException {
    WatchApi<A> client = createWatchApi(apiTypeClass, apiListTypeClass, apiGroup, apiVersion, resourcePlural);
    return client.watch(namespace, listOptions);
  }

  private static class DirectResponseStep<R extends KubernetesType> extends ResponseStep<R> {
    private KubernetesApiResponse<R> callResponse;

    @Override
    public Result onFailure(Packet packet, KubernetesApiResponse<R> callResponse) {
      this.callResponse = callResponse;
      return doEnd();
    }

    @Override
    public Result onSuccess(Packet packet, KubernetesApiResponse<R> callResponse) {
      this.callResponse = callResponse;
      return doEnd();
    }

    public R get() throws ApiException {
      if (callResponse != null) {
        return callResponse.throwsApiException().getObject();
      }
      return null;
    }
  }

  public record StringObject(String value) implements KubernetesObject {

    @Override
    public V1ObjectMeta getMetadata() {
      return null;
    }

    @Override
    public String getApiVersion() {
      return null;
    }

    @Override
    public String getKind() {
      return null;
    }
  }

  public record VersionInfoObject(VersionInfo value) implements KubernetesObject {

    @Override
    public V1ObjectMeta getMetadata() {
      return null;
    }

    @Override
    public String getApiVersion() {
      return null;
    }

    @Override
    public String getKind() {
      return null;
    }
  }

  public static class V1StatusObject implements KubernetesObject {
    private final V1Status value;

    public V1StatusObject(V1Status value) {
      this.value = value;
    }

    @Override
    public V1ObjectMeta getMetadata() {
      return null;
    }

    @Override
    public String getApiVersion() {
      return value.getApiVersion();
    }

    @Override
    public String getKind() {
      return value.getKind();
    }
  }

  public static class PodRequestBuilder extends RequestBuilder<V1Pod, V1PodList> {

    public PodRequestBuilder(ResourceCache resourceCache) {
      super(resourceCache, V1Pod.class, V1PodList.class, "", "v1", "pods", "pod");
    }

    /**
     * Step to return pod logs.
     * @param namespace Namespace
     * @param name Name
     * @param container Container name
     * @param responseStep Response step
     * @return Request step
     */
    public RequestStep<V1Pod, V1PodList, StringObject> logs(
        String namespace, String name, String container, ResponseStep<StringObject> responseStep) {
      return logs(namespace, name, container, responseStep, CLIENT_SELECTOR);
    }

    /**
     * Step to return pod logs.
     * @param namespace Namespace
     * @param name Name
     * @param container Container name
     * @param responseStep Response step
     * @param clientSelector Client selector
     * @return Request step
     */
    public RequestStep<V1Pod, V1PodList, StringObject> logs(
            String namespace, String name, String container, ResponseStep<StringObject> responseStep,
            UnaryOperator<ApiClient> clientSelector) {
      return new RequestStep.LogsRequestStep(
              resourceCache,
              responseStep, apiTypeClass, apiListTypeClass, apiGroup, apiVersion, resourcePlural, resourceSingular,
              namespace, name, container, clientSelector);
    }

    /**
     * Step to delete collection of pods.
     * @param namespace Namespace
     * @param listOptions List options
     * @param deleteOptions Delete options
     * @param responseStep Response step
     * @return Request step
     */
    public RequestStep<V1Pod, V1PodList, V1StatusObject> deleteCollection(
        String namespace, ListOptions listOptions, DeleteOptions deleteOptions,
        ResponseStep<V1StatusObject> responseStep) {
      return deleteCollection(namespace, listOptions, deleteOptions, responseStep, CLIENT_SELECTOR);
    }

    /**
     * Step to delete collection of pods.
     * @param namespace Namespace
     * @param listOptions List options
     * @param deleteOptions Delete options
     * @param responseStep Response step
     * @param clientSelector Client selector
     * @return Request step
     */
    public RequestStep<V1Pod, V1PodList, V1StatusObject> deleteCollection(
            String namespace, ListOptions listOptions, DeleteOptions deleteOptions,
            ResponseStep<V1StatusObject> responseStep, UnaryOperator<ApiClient> clientSelector) {
      return new RequestStep.DeleteCollectionRequestStep(
              resourceCache,
              responseStep, V1Pod.class, V1PodList.class, apiGroup, apiVersion, resourcePlural, resourceSingular,
              namespace, listOptions, deleteOptions, clientSelector);
    }
  }

  public static class VersionCodeRequestBuilder extends RequestBuilder<KubernetesObject, KubernetesListObject> {

    public VersionCodeRequestBuilder(ResourceCache resourceCache) {
      super(resourceCache, KubernetesObject.class, KubernetesListObject.class, "", "", "", "");
    }

    /**
     * Step to return version info.
     * @param responseStep Response step
     * @return Request step
     */
    public RequestStep<KubernetesObject, KubernetesListObject, VersionInfoObject> versionCode(
        ResponseStep<VersionInfoObject> responseStep) {
      return versionCode(responseStep, CLIENT_SELECTOR);
    }

    /**
     * Step to return version info.
     * @param responseStep Response step
     * @param clientSelector Client selector
     * @return Request step
     */
    public RequestStep<KubernetesObject, KubernetesListObject, VersionInfoObject> versionCode(
            ResponseStep<VersionInfoObject> responseStep, UnaryOperator<ApiClient> clientSelector) {
      return new RequestStep.VersionCodeRequestStep(resourceCache, responseStep, clientSelector);
    }

    /**
     * Get version info.
     * @return Version info
     * @throws ApiException On failure
     */
    public VersionInfoObject versionCode() throws ApiException {
      DirectResponseStep<VersionInfoObject> response = new DirectResponseStep<>();
      RequestStep<KubernetesObject, KubernetesListObject, VersionInfoObject> step = versionCode(response);
      step.apply(new Packet());
      return response.get();
    }

  }
}

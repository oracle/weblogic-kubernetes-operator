// Copyright (c) 2017, 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.weblogic.domain.api;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.reflect.TypeToken;
import io.kubernetes.client.custom.V1Patch;
import io.kubernetes.client.openapi.ApiCallback;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.ApiResponse;
import io.kubernetes.client.openapi.Configuration;
import io.kubernetes.client.openapi.Pair;
import io.kubernetes.client.openapi.models.V1DeleteOptions;
import okhttp3.Call;
import oracle.kubernetes.weblogic.domain.model.Domain;
import oracle.kubernetes.weblogic.domain.model.DomainList;

import static oracle.kubernetes.operator.KubernetesConstants.DOMAIN_PATH;
import static oracle.kubernetes.operator.KubernetesConstants.DOMAIN_SCALE_PATH;
import static oracle.kubernetes.operator.KubernetesConstants.DOMAIN_SPECIFIC_PATH;
import static oracle.kubernetes.operator.KubernetesConstants.DOMAIN_STATUS_PATH;

public class WeblogicApi {
  private ApiClient localVarApiClient;

  public WeblogicApi() {
    this(Configuration.getDefaultApiClient());
  }

  public WeblogicApi(ApiClient apiClient) {
    this.localVarApiClient = apiClient;
  }

  public ApiClient getApiClient() {
    return this.localVarApiClient;
  }

  public void setApiClient(ApiClient apiClient) {
    this.localVarApiClient = apiClient;
  }

  protected Call createNamespacedDomainCall(
      String namespace, Domain body, String pretty, ApiCallback callback) throws ApiException {
    final String localVarPath =
        DOMAIN_PATH.replaceAll("\\{namespace\\}", this.localVarApiClient.escapeString(namespace));
    final List<Pair> localVarQueryParams = new ArrayList<>();
    final List<Pair> localVarCollectionQueryParams = new ArrayList<>();
    if (pretty != null) {
      localVarQueryParams.addAll(this.localVarApiClient.parameterToPair("pretty", pretty));
    }

    final Map<String, String> localVarHeaderParams = new HashMap<>();
    final Map<String, String> localVarCookieParams = new HashMap<>();
    final Map<String, Object> localVarFormParams = new HashMap<>();
    String[] localVarAccepts = new String[] {"application/json"};
    String localVarAccept = this.localVarApiClient.selectHeaderAccept(localVarAccepts);
    if (localVarAccept != null) {
      localVarHeaderParams.put("Accept", localVarAccept);
    }

    String[] localVarContentTypes = new String[0];
    String localVarContentType =
        this.localVarApiClient.selectHeaderContentType(localVarContentTypes);
    localVarHeaderParams.put("Content-Type", localVarContentType);
    String[] localVarAuthNames = new String[] {"BearerToken"};
    return this.localVarApiClient.buildCall(
        localVarPath,
        "POST",
        localVarQueryParams,
        localVarCollectionQueryParams,
        body,
        localVarHeaderParams,
        localVarCookieParams,
        localVarFormParams,
        localVarAuthNames,
        callback);
  }

  private Call createNamespacedDomainValidateBeforeCall(
      String namespace, Domain body, String pretty, ApiCallback callback) throws ApiException {
    if (namespace == null) {
      throw new ApiException(
          "Missing the required parameter 'namespace' when calling createNamespacedDomain(Async)");
    } else if (body == null) {
      throw new ApiException(
          "Missing the required parameter 'body' when calling createNamespacedDomain(Async)");
    } else {
      return this.createNamespacedDomainCall(namespace, body, pretty, callback);
    }
  }

  /**
   * Create domain.
   * @param namespace namespace
   * @param body domain
   * @param pretty pretty flag
   * @return domain
   * @throws ApiException on failure
   */
  public Domain createNamespacedDomain(String namespace, Domain body, String pretty)
      throws ApiException {
    ApiResponse<Domain> localVarResp =
        this.createNamespacedDomainWithHttpInfo(namespace, body, pretty);
    return localVarResp.getData();
  }

  protected ApiResponse<Domain> createNamespacedDomainWithHttpInfo(
      String namespace, Domain body, String pretty) throws ApiException {
    Call localVarCall =
        this.createNamespacedDomainValidateBeforeCall(namespace, body, pretty, null);
    Type localVarReturnType = (new TypeToken<Domain>() {}).getType();
    return this.localVarApiClient.execute(localVarCall, localVarReturnType);
  }

  protected Call createNamespacedDomainAsync(
      String namespace, Domain body, String pretty, ApiCallback<Domain> callback)
      throws ApiException {
    Call localVarCall =
        this.createNamespacedDomainValidateBeforeCall(namespace, body, pretty, callback);
    Type localVarReturnType = (new TypeToken<Domain>() {}).getType();
    this.localVarApiClient.executeAsync(localVarCall, localVarReturnType, callback);
    return localVarCall;
  }

  protected Call deleteNamespacedDomainCall(
      String name,
      String namespace,
      V1DeleteOptions body,
      Integer gracePeriodSeconds,
      Boolean orphanDependents,
      String propagationPolicy,
      ApiCallback callback)
      throws ApiException {
    final String localVarPath =
        DOMAIN_SPECIFIC_PATH
            .replaceAll("\\{namespace\\}", this.localVarApiClient.escapeString(namespace))
            .replaceAll("\\{name\\}", this.localVarApiClient.escapeString(name));
    List<Pair> localVarQueryParams = new ArrayList<>();
    final List<Pair> localVarCollectionQueryParams = new ArrayList<>();
    if (gracePeriodSeconds != null) {
      localVarQueryParams.addAll(
          this.localVarApiClient.parameterToPair("gracePeriodSeconds", gracePeriodSeconds));
    }

    if (orphanDependents != null) {
      localVarQueryParams.addAll(
          this.localVarApiClient.parameterToPair("orphanDependents", orphanDependents));
    }

    if (propagationPolicy != null) {
      localVarQueryParams.addAll(
          this.localVarApiClient.parameterToPair("propagationPolicy", propagationPolicy));
    }

    Map<String, String> localVarHeaderParams = new HashMap<>();
    Map<String, String> localVarCookieParams = new HashMap<>();
    Map<String, Object> localVarFormParams = new HashMap<>();
    String[] localVarAccepts = new String[] {"application/json"};
    String localVarAccept = this.localVarApiClient.selectHeaderAccept(localVarAccepts);
    if (localVarAccept != null) {
      localVarHeaderParams.put("Accept", localVarAccept);
    }

    String[] localVarContentTypes = new String[0];
    String localVarContentType =
        this.localVarApiClient.selectHeaderContentType(localVarContentTypes);
    localVarHeaderParams.put("Content-Type", localVarContentType);
    String[] localVarAuthNames = new String[] {"BearerToken"};
    return this.localVarApiClient.buildCall(
        localVarPath,
        "DELETE",
        localVarQueryParams,
        localVarCollectionQueryParams,
        body,
        localVarHeaderParams,
        localVarCookieParams,
        localVarFormParams,
        localVarAuthNames,
        callback);
  }

  private Call deleteNamespacedDomainValidateBeforeCall(
      String name,
      String namespace,
      V1DeleteOptions body,
      Integer gracePeriodSeconds,
      Boolean orphanDependents,
      String propagationPolicy,
      ApiCallback callback)
      throws ApiException {
    if (namespace == null) {
      throw new ApiException(
          "Missing the required parameter 'namespace' when calling deleteNamespacedDomain(Async)");
    } else if (name == null) {
      throw new ApiException(
          "Missing the required parameter 'name' when calling deleteNamespacedDomain(Async)");
    } else if (body == null) {
      throw new ApiException(
          "Missing the required parameter 'body' when calling deleteNamespacedDomain(Async)");
    } else {
      Call localVarCall =
          this.deleteNamespacedDomainCall(
              name,
              namespace,
              body,
              gracePeriodSeconds,
              orphanDependents,
              propagationPolicy,
              callback);
      return localVarCall;
    }
  }

  /**
   * Delete domain.
   * @param name name
   * @param namespace namespace
   * @param body domain
   * @param gracePeriodSeconds grace period
   * @param orphanDependents if orphan dependents
   * @param propagationPolicy propagation policy
   * @return domain
   * @throws ApiException on failure
   */
  public Domain deleteNamespacedDomain(
      String name,
      String namespace,
      V1DeleteOptions body,
      Integer gracePeriodSeconds,
      Boolean orphanDependents,
      String propagationPolicy)
      throws ApiException {
    ApiResponse<Domain> localVarResp =
        this.deleteNamespacedDomainWithHttpInfo(
            name, namespace, body, gracePeriodSeconds, orphanDependents, propagationPolicy);
    return localVarResp.getData();
  }

  protected ApiResponse<Domain> deleteNamespacedDomainWithHttpInfo(
      String name,
      String namespace,
      V1DeleteOptions body,
      Integer gracePeriodSeconds,
      Boolean orphanDependents,
      String propagationPolicy)
      throws ApiException {
    Call localVarCall =
        this.deleteNamespacedDomainValidateBeforeCall(
            name, namespace, body, gracePeriodSeconds, orphanDependents, propagationPolicy, null);
    Type localVarReturnType = (new TypeToken<Domain>() {}).getType();
    return this.localVarApiClient.execute(localVarCall, localVarReturnType);
  }

  /**
   * Asynchronously delete domain.
   * @param name name
   * @param namespace namespace
   * @param body deletion options
   * @param gracePeriodSeconds grace period
   * @param orphanDependents if orphan dependents
   * @param propagationPolicy propagation policy
   * @param callback callback
   * @return domain
   * @throws ApiException on failure
   */
  public Call deleteNamespacedDomainAsync(
      String name,
      String namespace,
      V1DeleteOptions body,
      Integer gracePeriodSeconds,
      Boolean orphanDependents,
      String propagationPolicy,
      ApiCallback<Domain> callback)
      throws ApiException {
    Call localVarCall =
        this.deleteNamespacedDomainValidateBeforeCall(
            name,
            namespace,
            body,
            gracePeriodSeconds,
            orphanDependents,
            propagationPolicy,
            callback);
    Type localVarReturnType = (new TypeToken<Domain>() {}).getType();
    this.localVarApiClient.executeAsync(localVarCall, localVarReturnType, callback);
    return localVarCall;
  }

  protected Call getNamespacedDomainCall(String name, String namespace, ApiCallback callback)
      throws ApiException {
    Object localVarPostBody = null;
    String localVarPath =
        DOMAIN_SPECIFIC_PATH
            .replaceAll("\\{namespace\\}", this.localVarApiClient.escapeString(namespace))
            .replaceAll("\\{name\\}", this.localVarApiClient.escapeString(name));
    List<Pair> localVarQueryParams = new ArrayList<>();
    List<Pair> localVarCollectionQueryParams = new ArrayList<>();
    Map<String, String> localVarHeaderParams = new HashMap<>();
    Map<String, String> localVarCookieParams = new HashMap<>();
    Map<String, Object> localVarFormParams = new HashMap<>();
    String[] localVarAccepts = new String[] {"application/json"};
    String localVarAccept = this.localVarApiClient.selectHeaderAccept(localVarAccepts);
    if (localVarAccept != null) {
      localVarHeaderParams.put("Accept", localVarAccept);
    }

    String[] localVarContentTypes = new String[0];
    String localVarContentType =
        this.localVarApiClient.selectHeaderContentType(localVarContentTypes);
    localVarHeaderParams.put("Content-Type", localVarContentType);
    String[] localVarAuthNames = new String[] {"BearerToken"};
    return this.localVarApiClient.buildCall(
        localVarPath,
        "GET",
        localVarQueryParams,
        localVarCollectionQueryParams,
        localVarPostBody,
        localVarHeaderParams,
        localVarCookieParams,
        localVarFormParams,
        localVarAuthNames,
        callback);
  }

  private Call getNamespacedDomainValidateBeforeCall(
      String name, String namespace, ApiCallback callback) throws ApiException {
    if (namespace == null) {
      throw new ApiException(
          "Missing the required parameter 'namespace' when calling getNamespacedDomain(Async)");
    } else if (name == null) {
      throw new ApiException(
          "Missing the required parameter 'name' when calling getNamespacedDomain(Async)");
    } else {
      Call localVarCall = this.getNamespacedDomainCall(name, namespace, callback);
      return localVarCall;
    }
  }

  public Domain getNamespacedDomain(String name, String namespace) throws ApiException {
    ApiResponse<Domain> localVarResp = this.getNamespacedDomainWithHttpInfo(name, namespace);
    return localVarResp.getData();
  }

  protected ApiResponse<Domain> getNamespacedDomainWithHttpInfo(String namespace, String name)
      throws ApiException {
    Call localVarCall = this.getNamespacedDomainValidateBeforeCall(name, namespace, null);
    Type localVarReturnType = (new TypeToken<Domain>() {}).getType();
    return this.localVarApiClient.execute(localVarCall, localVarReturnType);
  }

  /**
   * Asynchronously read domain.
   * @param name name
   * @param namespace namespace
   * @param callback callback
   * @return call
   * @throws ApiException on failure
   */
  public Call getNamespacedDomainAsync(String name, String namespace, ApiCallback<Domain> callback)
      throws ApiException {
    Call localVarCall = this.getNamespacedDomainValidateBeforeCall(name, namespace, callback);
    Type localVarReturnType = (new TypeToken<Domain>() {}).getType();
    this.localVarApiClient.executeAsync(localVarCall, localVarReturnType, callback);
    return localVarCall;
  }

  protected Call getNamespacedDomainScaleCall(String name, String namespace, ApiCallback callback)
      throws ApiException {
    Object localVarPostBody = null;
    String localVarPath =
        DOMAIN_SCALE_PATH
            .replaceAll("\\{namespace\\}", this.localVarApiClient.escapeString(namespace))
            .replaceAll("\\{name\\}", this.localVarApiClient.escapeString(name));
    List<Pair> localVarQueryParams = new ArrayList();
    List<Pair> localVarCollectionQueryParams = new ArrayList();
    Map<String, String> localVarHeaderParams = new HashMap();
    Map<String, String> localVarCookieParams = new HashMap();
    Map<String, Object> localVarFormParams = new HashMap();
    String[] localVarAccepts =
        new String[] {
          "application/json", "application/yaml", "application/vnd.kubernetes.protobuf"
        };
    String localVarAccept = this.localVarApiClient.selectHeaderAccept(localVarAccepts);
    if (localVarAccept != null) {
      localVarHeaderParams.put("Accept", localVarAccept);
    }

    String[] localVarContentTypes = new String[0];
    String localVarContentType =
        this.localVarApiClient.selectHeaderContentType(localVarContentTypes);
    localVarHeaderParams.put("Content-Type", localVarContentType);
    String[] localVarAuthNames = new String[] {"BearerToken"};
    return this.localVarApiClient.buildCall(
        localVarPath,
        "GET",
        localVarQueryParams,
        localVarCollectionQueryParams,
        localVarPostBody,
        localVarHeaderParams,
        localVarCookieParams,
        localVarFormParams,
        localVarAuthNames,
        callback);
  }

  private Call getNamespacedDomainScaleValidateBeforeCall(
      String name, String namespace, ApiCallback callback) throws ApiException {
    if (namespace == null) {
      throw new ApiException(
          "Missing the required parameter 'namespace' when calling getNamespacedDomainScale(Async)");
    } else if (name == null) {
      throw new ApiException(
          "Missing the required parameter 'name' when calling getNamespacedDomainScale(Async)");
    } else {
      Call localVarCall = this.getNamespacedDomainScaleCall(name, namespace, callback);
      return localVarCall;
    }
  }

  public Domain getNamespacedDomainScale(String name, String namespace) throws ApiException {
    ApiResponse<Domain> localVarResp = this.getNamespacedDomainScaleWithHttpInfo(name, namespace);
    return localVarResp.getData();
  }

  protected ApiResponse<Domain> getNamespacedDomainScaleWithHttpInfo(String name, String namespace)
      throws ApiException {
    Call localVarCall = this.getNamespacedDomainScaleValidateBeforeCall(name, namespace, null);
    Type localVarReturnType = (new TypeToken<Domain>() {}).getType();
    return this.localVarApiClient.execute(localVarCall, localVarReturnType);
  }

  /**
   * Asynchronously read domain scale.
   * @param name name
   * @param namespace namespace
   * @param callback callback
   * @return call
   * @throws ApiException on failure
   */
  public Call getNamespacedDomainScaleAsync(
      String name, String namespace, ApiCallback<Domain> callback) throws ApiException {
    Call localVarCall = this.getNamespacedDomainScaleValidateBeforeCall(name, namespace, callback);
    Type localVarReturnType = (new TypeToken<Domain>() {}).getType();
    this.localVarApiClient.executeAsync(localVarCall, localVarReturnType, callback);
    return localVarCall;
  }

  protected Call getNamespacedDomainStatusCall(String name, String namespace, ApiCallback callback)
      throws ApiException {
    Object localVarPostBody = null;
    String localVarPath =
        DOMAIN_STATUS_PATH
            .replaceAll("\\{namespace\\}", this.localVarApiClient.escapeString(namespace))
            .replaceAll("\\{name\\}", this.localVarApiClient.escapeString(name));
    List<Pair> localVarQueryParams = new ArrayList<>();
    List<Pair> localVarCollectionQueryParams = new ArrayList<>();
    Map<String, String> localVarHeaderParams = new HashMap<>();
    Map<String, String> localVarCookieParams = new HashMap<>();
    Map<String, Object> localVarFormParams = new HashMap<>();
    String[] localVarAccepts =
        new String[] {
          "application/json", "application/yaml", "application/vnd.kubernetes.protobuf"
        };
    String localVarAccept = this.localVarApiClient.selectHeaderAccept(localVarAccepts);
    if (localVarAccept != null) {
      localVarHeaderParams.put("Accept", localVarAccept);
    }

    String[] localVarContentTypes = new String[0];
    String localVarContentType =
        this.localVarApiClient.selectHeaderContentType(localVarContentTypes);
    localVarHeaderParams.put("Content-Type", localVarContentType);
    String[] localVarAuthNames = new String[] {"BearerToken"};
    return this.localVarApiClient.buildCall(
        localVarPath,
        "GET",
        localVarQueryParams,
        localVarCollectionQueryParams,
        localVarPostBody,
        localVarHeaderParams,
        localVarCookieParams,
        localVarFormParams,
        localVarAuthNames,
        callback);
  }

  private Call getNamespacedDomainStatusValidateBeforeCall(
      String name, String namespace, ApiCallback callback) throws ApiException {
    if (namespace == null) {
      throw new ApiException(
          "Missing the required parameter 'namespace' when calling getNamespacedDomainStatus(Async)");
    } else if (name == null) {
      throw new ApiException(
          "Missing the required parameter 'name' when calling getNamespacedDomainStatus(Async)");
    } else {
      Call localVarCall = this.getNamespacedDomainStatusCall(name, namespace, callback);
      return localVarCall;
    }
  }

  public Domain getNamespacedDomainStatus(String name, String namespace) throws ApiException {
    ApiResponse<Domain> localVarResp = this.getNamespacedDomainStatusWithHttpInfo(name, namespace);
    return localVarResp.getData();
  }

  protected ApiResponse<Domain> getNamespacedDomainStatusWithHttpInfo(String name, String namespace)
      throws ApiException {
    Call localVarCall = this.getNamespacedDomainStatusValidateBeforeCall(name, namespace, null);
    Type localVarReturnType = (new TypeToken<Domain>() {}).getType();
    return this.localVarApiClient.execute(localVarCall, localVarReturnType);
  }

  /**
   * Asynchronously read domain status.
   * @param name name
   * @param namespace namespace
   * @param callback callback
   * @return call
   * @throws ApiException on failure
   */
  public Call getNamespacedDomainStatusAsync(
      String name, String namespace, ApiCallback<Domain> callback) throws ApiException {
    Call localVarCall = this.getNamespacedDomainStatusValidateBeforeCall(name, namespace, callback);
    Type localVarReturnType = (new TypeToken<Domain>() {}).getType();
    this.localVarApiClient.executeAsync(localVarCall, localVarReturnType, callback);
    return localVarCall;
  }

  /**
   * Generate call to list domains.
   * @param namespace namespace
   * @param pretty pretty flag
   * @param cont continuation
   * @param fieldSelector field selector
   * @param labelSelector label selector
   * @param limit limit
   * @param resourceVersion resource version
   * @param timeoutSeconds timeout
   * @param watch if watch
   * @param callback callback
   * @return call
   * @throws ApiException on failure
   */
  public Call listNamespacedDomainCall(
      String namespace,
      String pretty,
      String cont,
      String fieldSelector,
      String labelSelector,
      Integer limit,
      String resourceVersion,
      Integer timeoutSeconds,
      Boolean watch,
      ApiCallback callback)
      throws ApiException {
    final Object localVarPostBody = null;
    final String localVarPath =
        DOMAIN_PATH.replaceAll("\\{namespace\\}", this.localVarApiClient.escapeString(namespace));
    final List<Pair> localVarQueryParams = new ArrayList();
    final List<Pair> localVarCollectionQueryParams = new ArrayList();
    if (pretty != null) {
      localVarQueryParams.addAll(this.localVarApiClient.parameterToPair("pretty", pretty));
    }

    if (cont != null) {
      localVarQueryParams.addAll(this.localVarApiClient.parameterToPair("continue", cont));
    }

    if (fieldSelector != null) {
      localVarQueryParams.addAll(
          this.localVarApiClient.parameterToPair("fieldSelector", fieldSelector));
    }

    if (labelSelector != null) {
      localVarQueryParams.addAll(
          this.localVarApiClient.parameterToPair("labelSelector", labelSelector));
    }

    if (limit != null) {
      localVarQueryParams.addAll(this.localVarApiClient.parameterToPair("limit", limit));
    }

    if (resourceVersion != null) {
      localVarQueryParams.addAll(
          this.localVarApiClient.parameterToPair("resourceVersion", resourceVersion));
    }

    if (timeoutSeconds != null) {
      localVarQueryParams.addAll(
          this.localVarApiClient.parameterToPair("timeoutSeconds", timeoutSeconds));
    }

    if (watch != null) {
      localVarQueryParams.addAll(this.localVarApiClient.parameterToPair("watch", watch));
    }

    Map<String, String> localVarHeaderParams = new HashMap();
    Map<String, String> localVarCookieParams = new HashMap();
    Map<String, Object> localVarFormParams = new HashMap();
    String[] localVarAccepts = new String[] {"application/json", "application/json;stream=watch"};
    String localVarAccept = this.localVarApiClient.selectHeaderAccept(localVarAccepts);
    if (localVarAccept != null) {
      localVarHeaderParams.put("Accept", localVarAccept);
    }

    String[] localVarContentTypes = new String[0];
    String localVarContentType =
        this.localVarApiClient.selectHeaderContentType(localVarContentTypes);
    localVarHeaderParams.put("Content-Type", localVarContentType);
    String[] localVarAuthNames = new String[] {"BearerToken"};
    return this.localVarApiClient.buildCall(
        localVarPath,
        "GET",
        localVarQueryParams,
        localVarCollectionQueryParams,
        localVarPostBody,
        localVarHeaderParams,
        localVarCookieParams,
        localVarFormParams,
        localVarAuthNames,
        callback);
  }

  private Call listNamespacedDomainValidateBeforeCall(
      String namespace,
      String pretty,
      String cont,
      String fieldSelector,
      String labelSelector,
      Integer limit,
      String resourceVersion,
      Integer timeoutSeconds,
      Boolean watch,
      ApiCallback callback)
      throws ApiException {
    if (namespace == null) {
      throw new ApiException(
          "Missing the required parameter 'namespace' when calling listNamespacedDomain(Async)");
    } else {
      Call localVarCall =
          this.listNamespacedDomainCall(
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
      return localVarCall;
    }
  }

  /**
   * List domains.
   * @param namespace namespace
   * @param pretty pretty flag
   * @param cont continuation
   * @param fieldSelector field selector
   * @param labelSelector label selector
   * @param limit limit
   * @param resourceVersion resource version
   * @param timeoutSeconds timeout
   * @param watch if watch
   * @return domain list
   * @throws ApiException on failure
   */
  public DomainList listNamespacedDomain(
      String namespace,
      String pretty,
      String cont,
      String fieldSelector,
      String labelSelector,
      Integer limit,
      String resourceVersion,
      Integer timeoutSeconds,
      Boolean watch)
      throws ApiException {
    ApiResponse<DomainList> localVarResp =
        this.listNamespacedDomainWithHttpInfo(
            namespace,
            pretty,
            cont,
            fieldSelector,
            labelSelector,
            limit,
            resourceVersion,
            timeoutSeconds,
            watch);
    return localVarResp.getData();
  }

  protected ApiResponse<DomainList> listNamespacedDomainWithHttpInfo(
      String namespace,
      String pretty,
      String cont,
      String fieldSelector,
      String labelSelector,
      Integer limit,
      String resourceVersion,
      Integer timeoutSeconds,
      Boolean watch)
      throws ApiException {
    Call localVarCall =
        this.listNamespacedDomainValidateBeforeCall(
            namespace,
            pretty,
            cont,
            fieldSelector,
            labelSelector,
            limit,
            resourceVersion,
            timeoutSeconds,
            watch,
            null);
    Type localVarReturnType = (new TypeToken<DomainList>() {}).getType();
    return this.localVarApiClient.execute(localVarCall, localVarReturnType);
  }

  /**
   * Asynchronously list domains.
   * @param namespace namespace
   * @param pretty pretty flag
   * @param cont continuation
   * @param fieldSelector field selector
   * @param labelSelector label selector
   * @param limit limit
   * @param resourceVersion resource version
   * @param timeoutSeconds timeout
   * @param watch watch
   * @param callback callback
   * @return call
   * @throws ApiException on failure
   */
  public Call listNamespacedDomainAsync(
      String namespace,
      String pretty,
      String cont,
      String fieldSelector,
      String labelSelector,
      Integer limit,
      String resourceVersion,
      Integer timeoutSeconds,
      Boolean watch,
      ApiCallback<DomainList> callback)
      throws ApiException {
    Call localVarCall =
        this.listNamespacedDomainValidateBeforeCall(
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
    Type localVarReturnType = (new TypeToken<DomainList>() {}).getType();
    this.localVarApiClient.executeAsync(localVarCall, localVarReturnType, callback);
    return localVarCall;
  }

  protected Call patchNamespacedDomainCall(
      String name, String namespace, V1Patch body, ApiCallback callback) throws ApiException {
    String localVarPath =
        DOMAIN_SPECIFIC_PATH
            .replaceAll("\\{namespace\\}", this.localVarApiClient.escapeString(namespace))
            .replaceAll("\\{name\\}", this.localVarApiClient.escapeString(name));
    List<Pair> localVarQueryParams = new ArrayList<>();
    List<Pair> localVarCollectionQueryParams = new ArrayList<>();
    Map<String, String> localVarHeaderParams = new HashMap<>();
    Map<String, String> localVarCookieParams = new HashMap<>();
    Map<String, Object> localVarFormParams = new HashMap<>();
    String[] localVarAccepts = new String[] {"application/json"};
    String localVarAccept = this.localVarApiClient.selectHeaderAccept(localVarAccepts);
    if (localVarAccept != null) {
      localVarHeaderParams.put("Accept", localVarAccept);
    }

    String[] localVarContentTypes =
        new String[] {"application/json-patch+json", "application/merge-patch+json"};
    String localVarContentType =
        this.localVarApiClient.selectHeaderContentType(localVarContentTypes);
    localVarHeaderParams.put("Content-Type", localVarContentType);
    String[] localVarAuthNames = new String[] {"BearerToken"};
    return this.localVarApiClient.buildCall(
        localVarPath,
        "PATCH",
        localVarQueryParams,
        localVarCollectionQueryParams,
        body,
        localVarHeaderParams,
        localVarCookieParams,
        localVarFormParams,
        localVarAuthNames,
        callback);
  }

  private Call patchNamespacedDomainValidateBeforeCall(
      String name, String namespace, V1Patch body, ApiCallback callback) throws ApiException {
    if (namespace == null) {
      throw new ApiException(
          "Missing the required parameter 'namespace' when calling patchNamespacedDomain(Async)");
    } else if (name == null) {
      throw new ApiException(
          "Missing the required parameter 'name' when calling patchNamespacedDomain(Async)");
    } else if (body == null) {
      throw new ApiException(
          "Missing the required parameter 'body' when calling patchNamespacedDomain(Async)");
    } else {
      Call localVarCall = this.patchNamespacedDomainCall(name, namespace, body, callback);
      return localVarCall;
    }
  }

  /**
   * Patch domain.
   * @param name name
   * @param namespace namespace
   * @param body patch
   * @return domain
   * @throws ApiException on failure
   */
  public Domain patchNamespacedDomain(String name, String namespace, V1Patch body)
      throws ApiException {
    ApiResponse<Domain> localVarResp =
        this.patchNamespacedDomainWithHttpInfo(name, namespace, body);
    return localVarResp.getData();
  }

  protected ApiResponse<Domain> patchNamespacedDomainWithHttpInfo(
      String name, String namespace, V1Patch body) throws ApiException {
    Call localVarCall = this.patchNamespacedDomainValidateBeforeCall(name, namespace, body, null);
    Type localVarReturnType = (new TypeToken<Domain>() {}).getType();
    return this.localVarApiClient.execute(localVarCall, localVarReturnType);
  }

  /**
   * Asynchronously patch domain.
   * @param name name
   * @param namespace namespace
   * @param body patch
   * @param callback callback
   * @return call
   * @throws ApiException on failure
   */
  public Call patchNamespacedDomainAsync(
      String name, String namespace, V1Patch body, ApiCallback<Domain> callback)
      throws ApiException {
    Call localVarCall =
        this.patchNamespacedDomainValidateBeforeCall(name, namespace, body, callback);
    Type localVarReturnType = (new TypeToken<Domain>() {}).getType();
    this.localVarApiClient.executeAsync(localVarCall, localVarReturnType, callback);
    return localVarCall;
  }

  protected Call patchNamespacedDomainScaleCall(
      String name, String namespace, V1Patch body, ApiCallback callback) throws ApiException {
    String localVarPath =
        DOMAIN_SCALE_PATH
            .replaceAll("\\{namespace\\}", this.localVarApiClient.escapeString(namespace))
            .replaceAll("\\{name\\}", this.localVarApiClient.escapeString(name));
    List<Pair> localVarQueryParams = new ArrayList();
    List<Pair> localVarCollectionQueryParams = new ArrayList();
    Map<String, String> localVarHeaderParams = new HashMap();
    Map<String, String> localVarCookieParams = new HashMap();
    Map<String, Object> localVarFormParams = new HashMap();
    String[] localVarAccepts =
        new String[] {
          "application/json", "application/yaml", "application/vnd.kubernetes.protobuf"
        };
    String localVarAccept = this.localVarApiClient.selectHeaderAccept(localVarAccepts);
    if (localVarAccept != null) {
      localVarHeaderParams.put("Accept", localVarAccept);
    }

    String[] localVarContentTypes =
        new String[] {"application/json-patch+json", "application/merge-patch+json"};
    String localVarContentType =
        this.localVarApiClient.selectHeaderContentType(localVarContentTypes);
    localVarHeaderParams.put("Content-Type", localVarContentType);
    String[] localVarAuthNames = new String[] {"BearerToken"};
    return this.localVarApiClient.buildCall(
        localVarPath,
        "PATCH",
        localVarQueryParams,
        localVarCollectionQueryParams,
        body,
        localVarHeaderParams,
        localVarCookieParams,
        localVarFormParams,
        localVarAuthNames,
        callback);
  }

  private Call patchNamespacedDomainScaleValidateBeforeCall(
      String name, String namespace, V1Patch body, ApiCallback callback) throws ApiException {
    if (namespace == null) {
      throw new ApiException(
          "Missing the required parameter 'namespace' when calling patchNamespacedDomainScale(Async)");
    } else if (name == null) {
      throw new ApiException(
          "Missing the required parameter 'name' when calling patchNamespacedDomainScale(Async)");
    } else if (body == null) {
      throw new ApiException(
          "Missing the required parameter 'body' when calling patchNamespacedDomainScale(Async)");
    } else {
      Call localVarCall = this.patchNamespacedDomainScaleCall(name, namespace, body, callback);
      return localVarCall;
    }
  }

  /**
   * Patch domain scale.
   * @param name name
   * @param namespace namespace
   * @param body patch
   * @return domain
   * @throws ApiException on failure
   */
  public Domain patchNamespacedDomainScale(String name, String namespace, V1Patch body)
      throws ApiException {
    ApiResponse<Domain> localVarResp =
        this.patchNamespacedDomainScaleWithHttpInfo(name, namespace, body);
    return localVarResp.getData();
  }

  protected ApiResponse<Domain> patchNamespacedDomainScaleWithHttpInfo(
      String name, String namespace, V1Patch body) throws ApiException {
    Call localVarCall =
        this.patchNamespacedDomainScaleValidateBeforeCall(name, namespace, body, null);
    Type localVarReturnType = (new TypeToken<Domain>() {}).getType();
    return this.localVarApiClient.execute(localVarCall, localVarReturnType);
  }

  /**
   * Asynchronously patch domain scale.
   * @param name name
   * @param namespace namespace
   * @param body patch
   * @param callback callback
   * @return call
   * @throws ApiException on failure
   */
  public Call patchNamespacedDomainScaleAsync(
      String name, String namespace, V1Patch body, ApiCallback<Domain> callback)
      throws ApiException {
    Call localVarCall =
        this.patchNamespacedDomainScaleValidateBeforeCall(name, namespace, body, callback);
    Type localVarReturnType = (new TypeToken<Domain>() {}).getType();
    this.localVarApiClient.executeAsync(localVarCall, localVarReturnType, callback);
    return localVarCall;
  }

  protected Call patchNamespacedDomainStatusCall(
      String name, String namespace, V1Patch body, ApiCallback callback) throws ApiException {
    String localVarPath =
        DOMAIN_STATUS_PATH
            .replaceAll("\\{namespace\\}", this.localVarApiClient.escapeString(namespace))
            .replaceAll("\\{name\\}", this.localVarApiClient.escapeString(name));
    List<Pair> localVarQueryParams = new ArrayList();
    List<Pair> localVarCollectionQueryParams = new ArrayList();
    Map<String, String> localVarHeaderParams = new HashMap();
    Map<String, String> localVarCookieParams = new HashMap();
    Map<String, Object> localVarFormParams = new HashMap();
    String[] localVarAccepts =
        new String[] {
          "application/json", "application/yaml", "application/vnd.kubernetes.protobuf"
        };
    String localVarAccept = this.localVarApiClient.selectHeaderAccept(localVarAccepts);
    if (localVarAccept != null) {
      localVarHeaderParams.put("Accept", localVarAccept);
    }

    String[] localVarContentTypes =
        new String[] {"application/json-patch+json", "application/merge-patch+json"};
    String localVarContentType =
        this.localVarApiClient.selectHeaderContentType(localVarContentTypes);
    localVarHeaderParams.put("Content-Type", localVarContentType);
    String[] localVarAuthNames = new String[] {"BearerToken"};
    return this.localVarApiClient.buildCall(
        localVarPath,
        "PATCH",
        localVarQueryParams,
        localVarCollectionQueryParams,
        body,
        localVarHeaderParams,
        localVarCookieParams,
        localVarFormParams,
        localVarAuthNames,
        callback);
  }

  private Call patchNamespacedDomainStatusValidateBeforeCall(
      String name, String namespace, V1Patch body, ApiCallback callback) throws ApiException {
    if (namespace == null) {
      throw new ApiException(
          "Missing the required parameter 'namespace' when calling patchNamespacedDomainStatus(Async)");
    } else if (name == null) {
      throw new ApiException(
          "Missing the required parameter 'name' when calling patchNamespacedDomainStatus(Async)");
    } else if (body == null) {
      throw new ApiException(
          "Missing the required parameter 'body' when calling patchNamespacedDomainStatus(Async)");
    } else {
      Call localVarCall = this.patchNamespacedDomainStatusCall(name, namespace, body, callback);
      return localVarCall;
    }
  }

  /**
   * Patch domain status.
   * @param name name
   * @param namespace namespace
   * @param body patch
   * @return domain
   * @throws ApiException on failure
   */
  public Domain patchNamespacedDomainStatus(String name, String namespace, V1Patch body)
      throws ApiException {
    ApiResponse<Domain> localVarResp =
        this.patchNamespacedDomainStatusWithHttpInfo(name, namespace, body);
    return localVarResp.getData();
  }

  protected ApiResponse<Domain> patchNamespacedDomainStatusWithHttpInfo(
      String name, String namespace, V1Patch body) throws ApiException {
    Call localVarCall =
        this.patchNamespacedDomainStatusValidateBeforeCall(name, namespace, body, null);
    Type localVarReturnType = (new TypeToken<Domain>() {}).getType();
    return this.localVarApiClient.execute(localVarCall, localVarReturnType);
  }

  /**
   * Asynchronously patch domain status.
   * @param name name
   * @param namespace namespace
   * @param body patch
   * @param callback callback
   * @return call
   * @throws ApiException on failure
   */
  public Call patchNamespacedDomainStatusAsync(
      String name, String namespace, V1Patch body, ApiCallback<Domain> callback)
      throws ApiException {
    Call localVarCall =
        this.patchNamespacedDomainStatusValidateBeforeCall(name, namespace, body, callback);
    Type localVarReturnType = (new TypeToken<Domain>() {}).getType();
    this.localVarApiClient.executeAsync(localVarCall, localVarReturnType, callback);
    return localVarCall;
  }

  protected Call replaceNamespacedDomainCall(
      String name, String namespace, Domain body, ApiCallback callback) throws ApiException {
    String localVarPath =
        DOMAIN_SPECIFIC_PATH
            .replaceAll("\\{namespace\\}", this.localVarApiClient.escapeString(namespace))
            .replaceAll("\\{name\\}", this.localVarApiClient.escapeString(name));
    List<Pair> localVarQueryParams = new ArrayList();
    List<Pair> localVarCollectionQueryParams = new ArrayList();
    Map<String, String> localVarHeaderParams = new HashMap();
    Map<String, String> localVarCookieParams = new HashMap();
    Map<String, Object> localVarFormParams = new HashMap();
    String[] localVarAccepts = new String[] {"application/json"};
    String localVarAccept = this.localVarApiClient.selectHeaderAccept(localVarAccepts);
    if (localVarAccept != null) {
      localVarHeaderParams.put("Accept", localVarAccept);
    }

    String[] localVarContentTypes = new String[0];
    String localVarContentType =
        this.localVarApiClient.selectHeaderContentType(localVarContentTypes);
    localVarHeaderParams.put("Content-Type", localVarContentType);
    String[] localVarAuthNames = new String[] {"BearerToken"};
    return this.localVarApiClient.buildCall(
        localVarPath,
        "PUT",
        localVarQueryParams,
        localVarCollectionQueryParams,
        body,
        localVarHeaderParams,
        localVarCookieParams,
        localVarFormParams,
        localVarAuthNames,
        callback);
  }

  private Call replaceNamespacedDomainValidateBeforeCall(
      String name, String namespace, Domain body, ApiCallback callback) throws ApiException {
    if (namespace == null) {
      throw new ApiException(
          "Missing the required parameter 'namespace' when calling replaceNamespacedDomain(Async)");
    } else if (name == null) {
      throw new ApiException(
          "Missing the required parameter 'name' when calling replaceNamespacedDomain(Async)");
    } else if (body == null) {
      throw new ApiException(
          "Missing the required parameter 'body' when calling replaceNamespacedDomain(Async)");
    } else {
      Call localVarCall = this.replaceNamespacedDomainCall(name, namespace, body, callback);
      return localVarCall;
    }
  }

  /**
   * Replace domain.
   * @param name name
   * @param namespace namespace
   * @param body domain
   * @return domain
   * @throws ApiException on failure
   */
  public Domain replaceNamespacedDomain(String name, String namespace, Domain body)
      throws ApiException {
    ApiResponse<Domain> localVarResp =
        this.replaceNamespacedDomainWithHttpInfo(name, namespace, body);
    return localVarResp.getData();
  }

  protected ApiResponse<Domain> replaceNamespacedDomainWithHttpInfo(
      String name, String namespace, Domain body) throws ApiException {
    Call localVarCall = this.replaceNamespacedDomainValidateBeforeCall(name, namespace, body, null);
    Type localVarReturnType = (new TypeToken<Domain>() {}).getType();
    return this.localVarApiClient.execute(localVarCall, localVarReturnType);
  }

  /**
   * Asynchronously replace domain.
   * @param name name
   * @param namespace namespace
   * @param body domain
   * @param callback callback
   * @return call
   * @throws ApiException on failure
   */
  public Call replaceNamespacedDomainAsync(
      String name, String namespace, Domain body, ApiCallback<Domain> callback)
      throws ApiException {
    Call localVarCall =
        this.replaceNamespacedDomainValidateBeforeCall(name, namespace, body, callback);
    Type localVarReturnType = (new TypeToken<Domain>() {}).getType();
    this.localVarApiClient.executeAsync(localVarCall, localVarReturnType, callback);
    return localVarCall;
  }

  protected Call replaceNamespacedDomainScaleCall(
      String name, String namespace, Domain body, ApiCallback callback) throws ApiException {
    String localVarPath =
        DOMAIN_SCALE_PATH
            .replaceAll("\\{namespace\\}", this.localVarApiClient.escapeString(namespace))
            .replaceAll("\\{name\\}", this.localVarApiClient.escapeString(name));
    List<Pair> localVarQueryParams = new ArrayList();
    List<Pair> localVarCollectionQueryParams = new ArrayList();
    Map<String, String> localVarHeaderParams = new HashMap();
    Map<String, String> localVarCookieParams = new HashMap();
    Map<String, Object> localVarFormParams = new HashMap();
    String[] localVarAccepts =
        new String[] {
          "application/json", "application/yaml", "application/vnd.kubernetes.protobuf"
        };
    String localVarAccept = this.localVarApiClient.selectHeaderAccept(localVarAccepts);
    if (localVarAccept != null) {
      localVarHeaderParams.put("Accept", localVarAccept);
    }

    String[] localVarContentTypes = new String[0];
    String localVarContentType =
        this.localVarApiClient.selectHeaderContentType(localVarContentTypes);
    localVarHeaderParams.put("Content-Type", localVarContentType);
    String[] localVarAuthNames = new String[] {"BearerToken"};
    return this.localVarApiClient.buildCall(
        localVarPath,
        "PUT",
        localVarQueryParams,
        localVarCollectionQueryParams,
        body,
        localVarHeaderParams,
        localVarCookieParams,
        localVarFormParams,
        localVarAuthNames,
        callback);
  }

  private Call replaceNamespacedDomainScaleValidateBeforeCall(
      String name, String namespace, Domain body, ApiCallback callback) throws ApiException {
    if (namespace == null) {
      throw new ApiException(
          "Missing the required parameter 'namespace' when calling replaceNamespacedDomainScale(Async)");
    } else if (name == null) {
      throw new ApiException(
          "Missing the required parameter 'name' when calling replaceNamespacedDomainScale(Async)");
    } else if (body == null) {
      throw new ApiException(
          "Missing the required parameter 'body' when calling replaceNamespacedDomainScale(Async)");
    } else {
      Call localVarCall = this.replaceNamespacedDomainScaleCall(name, namespace, body, callback);
      return localVarCall;
    }
  }

  /**
   * Replace domain scale.
   * @param name name
   * @param namespace namespace
   * @param body domain
   * @return domain
   * @throws ApiException on failure
   */
  public Domain replaceNamespacedDomainScale(String name, String namespace, Domain body)
      throws ApiException {
    ApiResponse<Domain> localVarResp =
        this.replaceNamespacedDomainScaleWithHttpInfo(name, namespace, body);
    return localVarResp.getData();
  }

  protected ApiResponse<Domain> replaceNamespacedDomainScaleWithHttpInfo(
      String name, String namespace, Domain body) throws ApiException {
    Call localVarCall =
        this.replaceNamespacedDomainScaleValidateBeforeCall(name, namespace, body, null);
    Type localVarReturnType = (new TypeToken<Domain>() {}).getType();
    return this.localVarApiClient.execute(localVarCall, localVarReturnType);
  }

  /**
   * Asynchronously replace domain status.
   * @param name name
   * @param namespace namespace
   * @param body domain
   * @param callback callback
   * @return call
   * @throws ApiException on failure
   */
  public Call replaceNamespacedDomainScaleAsync(
      String name, String namespace, Domain body, ApiCallback<Domain> callback)
      throws ApiException {
    Call localVarCall =
        this.replaceNamespacedDomainScaleValidateBeforeCall(name, namespace, body, callback);
    Type localVarReturnType = (new TypeToken<Domain>() {}).getType();
    this.localVarApiClient.executeAsync(localVarCall, localVarReturnType, callback);
    return localVarCall;
  }

  protected Call replaceNamespacedDomainStatusCall(
      String name, String namespace, Domain body, ApiCallback callback) throws ApiException {
    String localVarPath =
        DOMAIN_STATUS_PATH
            .replaceAll("\\{namespace\\}", this.localVarApiClient.escapeString(namespace))
            .replaceAll("\\{name\\}", this.localVarApiClient.escapeString(name));
    List<Pair> localVarQueryParams = new ArrayList();
    List<Pair> localVarCollectionQueryParams = new ArrayList();
    Map<String, String> localVarHeaderParams = new HashMap();
    Map<String, String> localVarCookieParams = new HashMap();
    Map<String, Object> localVarFormParams = new HashMap();
    String[] localVarAccepts =
        new String[] {
          "application/json", "application/yaml", "application/vnd.kubernetes.protobuf"
        };
    String localVarAccept = this.localVarApiClient.selectHeaderAccept(localVarAccepts);
    if (localVarAccept != null) {
      localVarHeaderParams.put("Accept", localVarAccept);
    }

    String[] localVarContentTypes = new String[0];
    String localVarContentType =
        this.localVarApiClient.selectHeaderContentType(localVarContentTypes);
    localVarHeaderParams.put("Content-Type", localVarContentType);
    String[] localVarAuthNames = new String[] {"BearerToken"};
    return this.localVarApiClient.buildCall(
        localVarPath,
        "PUT",
        localVarQueryParams,
        localVarCollectionQueryParams,
        body,
        localVarHeaderParams,
        localVarCookieParams,
        localVarFormParams,
        localVarAuthNames,
        callback);
  }

  private Call replaceNamespacedDomainStatusValidateBeforeCall(
      String name, String namespace, Domain body, ApiCallback callback) throws ApiException {
    if (namespace == null) {
      throw new ApiException(
          "Missing the required parameter 'namespace' when calling replaceNamespacedDomainStatus(Async)");
    } else if (name == null) {
      throw new ApiException(
          "Missing the required parameter 'name' when calling replaceNamespacedDomainStatus(Async)");
    } else if (body == null) {
      throw new ApiException(
          "Missing the required parameter 'body' when calling replaceNamespacedDomainStatus(Async)");
    } else {
      Call localVarCall = this.replaceNamespacedDomainStatusCall(name, namespace, body, callback);
      return localVarCall;
    }
  }

  /**
   * Replace domain status.
   * @param name name
   * @param namespace namespace
   * @param body domain
   * @return domain
   * @throws ApiException on failure
   */
  public Domain replaceNamespacedDomainStatus(String name, String namespace, Domain body)
      throws ApiException {
    ApiResponse<Domain> localVarResp =
        this.replaceNamespacedDomainStatusWithHttpInfo(name, namespace, body);
    return localVarResp.getData();
  }

  protected ApiResponse<Domain> replaceNamespacedDomainStatusWithHttpInfo(
      String name, String namespace, Domain body) throws ApiException {
    Call localVarCall =
        this.replaceNamespacedDomainStatusValidateBeforeCall(name, namespace, body, null);
    Type localVarReturnType = (new TypeToken<Domain>() {}).getType();
    return this.localVarApiClient.execute(localVarCall, localVarReturnType);
  }

  /**
   * Asynchronously replace domain status.
   * @param name name
   * @param namespace namespace
   * @param body domain
   * @param callback callback
   * @return call
   * @throws ApiException on failure
   */
  public Call replaceNamespacedDomainStatusAsync(
      String name, String namespace, Domain body, ApiCallback<Domain> callback)
      throws ApiException {
    Call localVarCall =
        this.replaceNamespacedDomainStatusValidateBeforeCall(name, namespace, body, callback);
    Type localVarReturnType = (new TypeToken<Domain>() {}).getType();
    this.localVarApiClient.executeAsync(localVarCall, localVarReturnType, callback);
    return localVarCall;
  }
}

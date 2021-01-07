// Copyright (c) 2017, 2021, Oracle and/or its affiliates.
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
import io.kubernetes.client.openapi.Pair;
import okhttp3.Call;
import oracle.kubernetes.weblogic.domain.model.Domain;
import oracle.kubernetes.weblogic.domain.model.DomainList;

import static oracle.kubernetes.operator.KubernetesConstants.DOMAIN_PATH;
import static oracle.kubernetes.operator.KubernetesConstants.DOMAIN_SPECIFIC_PATH;
import static oracle.kubernetes.operator.KubernetesConstants.DOMAIN_STATUS_PATH;

public class WeblogicApi {
  private final ApiClient localVarApiClient;

  public WeblogicApi(ApiClient apiClient) {
    this.localVarApiClient = apiClient;
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
    final List<Pair> localVarQueryParams = new ArrayList<>();
    final List<Pair> localVarCollectionQueryParams = new ArrayList<>();
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

    Map<String, String> localVarHeaderParams = new HashMap<>();
    Map<String, String> localVarCookieParams = new HashMap<>();
    Map<String, Object> localVarFormParams = new HashMap<>();
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

  protected Call replaceNamespacedDomainCall(
      String name, String namespace, Domain body, ApiCallback callback) throws ApiException {
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

  protected Call replaceNamespacedDomainStatusCall(
      String name, String namespace, Domain body, ApiCallback callback) throws ApiException {
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

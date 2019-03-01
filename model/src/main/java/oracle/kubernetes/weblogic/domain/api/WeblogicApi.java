// Copyright 2017, 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.weblogic.domain.api;

import static oracle.kubernetes.operator.KubernetesConstants.DOMAIN_VERSION;

import com.google.gson.reflect.TypeToken;
import io.kubernetes.client.ApiCallback;
import io.kubernetes.client.ApiClient;
import io.kubernetes.client.ApiException;
import io.kubernetes.client.ApiResponse;
import io.kubernetes.client.Configuration;
import io.kubernetes.client.Pair;
import io.kubernetes.client.ProgressRequestBody;
import io.kubernetes.client.ProgressResponseBody;
import io.kubernetes.client.models.V1DeleteOptions;
import io.kubernetes.client.models.V1Scale;
import io.kubernetes.client.models.V1Status;
import io.kubernetes.client.models.V1WatchEvent;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.sound.midi.Patch;
import oracle.kubernetes.weblogic.domain.v3.Domain;
import oracle.kubernetes.weblogic.domain.v3.DomainList;

public class WeblogicApi {
  private ApiClient apiClient;

  public WeblogicApi() {
    this(Configuration.getDefaultApiClient());
  }

  public WeblogicApi(ApiClient apiClient) {
    this.apiClient = apiClient;
  }

  public ApiClient getApiClient() {
    return apiClient;
  }

  public void setApiClient(ApiClient apiClient) {
    this.apiClient = apiClient;
  }

  /**
   * Build call for createWebLogicOracleNamespacedDomain.
   *
   * @param namespace object name and auth scope, such as for teams and projects (required)
   * @param body (required)
   * @param pretty If &#39;true&#39;, then the output is pretty printed. (optional)
   * @param progressListener Progress listener
   * @param progressRequestListener Progress request listener
   * @return Call to execute
   * @throws ApiException If fail to serialize the request body object
   */
  public com.squareup.okhttp.Call createWebLogicOracleNamespacedDomainCall(
      String namespace,
      Domain body,
      String pretty,
      final ProgressResponseBody.ProgressListener progressListener,
      final ProgressRequestBody.ProgressRequestListener progressRequestListener)
      throws ApiException {
    Object localVarPostBody = body;

    // create path and map variables
    String localVarPath =
        "/apis/weblogic.oracle/"
            + DOMAIN_VERSION
            + "/namespaces/{namespace}/domains"
                .replaceAll("\\{" + "namespace" + "\\}", apiClient.escapeString(namespace));

    List<Pair> localVarQueryParams = new ArrayList<Pair>();
    List<Pair> localVarCollectionQueryParams = new ArrayList<Pair>();
    if (pretty != null) localVarQueryParams.addAll(apiClient.parameterToPair("pretty", pretty));

    Map<String, String> localVarHeaderParams = new HashMap<String, String>();

    Map<String, Object> localVarFormParams = new HashMap<String, Object>();

    final String[] localVarAccepts = {
      "application/json", "application/yaml", "application/vnd.kubernetes.protobuf"
    };
    final String localVarAccept = apiClient.selectHeaderAccept(localVarAccepts);
    if (localVarAccept != null) localVarHeaderParams.put("Accept", localVarAccept);

    final String[] localVarContentTypes = {"*/*"};
    final String localVarContentType = apiClient.selectHeaderContentType(localVarContentTypes);
    localVarHeaderParams.put("Content-Type", localVarContentType);

    if (progressListener != null) {
      apiClient
          .getHttpClient()
          .networkInterceptors()
          .add(
              chain -> {
                com.squareup.okhttp.Response originalResponse = chain.proceed(chain.request());
                return originalResponse
                    .newBuilder()
                    .body(new ProgressResponseBody(originalResponse.body(), progressListener))
                    .build();
              });
    }

    String[] localVarAuthNames = new String[] {"BearerToken"};
    return apiClient.buildCall(
        localVarPath,
        "POST",
        localVarQueryParams,
        localVarCollectionQueryParams,
        localVarPostBody,
        localVarHeaderParams,
        localVarFormParams,
        localVarAuthNames,
        progressRequestListener);
  }

  private com.squareup.okhttp.Call createWebLogicOracleNamespacedDomainValidateBeforeCall(
      String namespace,
      Domain body,
      String pretty,
      final ProgressResponseBody.ProgressListener progressListener,
      final ProgressRequestBody.ProgressRequestListener progressRequestListener)
      throws ApiException {

    // verify the required parameter 'namespace' is set
    if (namespace == null) {
      throw new ApiException(
          "Missing the required parameter 'namespace' when calling createWebLogicOracleNamespacedDomain(Async)");
    }

    // verify the required parameter 'body' is set
    if (body == null) {
      throw new ApiException(
          "Missing the required parameter 'body' when calling createWebLogicOracleNamespacedDomain(Async)");
    }

    com.squareup.okhttp.Call call =
        createWebLogicOracleNamespacedDomainCall(
            namespace, body, pretty, progressListener, progressRequestListener);
    return call;
  }

  /**
   * create a Domain.
   *
   * @param namespace object name and auth scope, such as for teams and projects (required)
   * @param body (required)
   * @param pretty If &#39;true&#39;, then the output is pretty printed. (optional)
   * @return Domain
   * @throws ApiException If fail to call the API, e.g. server error or cannot deserialize the
   *     response body
   */
  public Domain createWebLogicOracleNamespacedDomain(String namespace, Domain body, String pretty)
      throws ApiException {
    ApiResponse<Domain> resp =
        createWebLogicOracleNamespacedDomainWithHttpInfo(namespace, body, pretty);
    return resp.getData();
  }

  /**
   * create a Domain.
   *
   * @param namespace object name and auth scope, such as for teams and projects (required)
   * @param body (required)
   * @param pretty If &#39;true&#39;, then the output is pretty printed. (optional)
   * @return ApiResponse&lt;Domain&gt;
   * @throws ApiException If fail to call the API, e.g. server error or cannot deserialize the
   *     response body
   */
  public ApiResponse<Domain> createWebLogicOracleNamespacedDomainWithHttpInfo(
      String namespace, Domain body, String pretty) throws ApiException {
    com.squareup.okhttp.Call call =
        createWebLogicOracleNamespacedDomainValidateBeforeCall(namespace, body, pretty, null, null);
    Type localVarReturnType = new TypeToken<Domain>() {}.getType();
    return apiClient.execute(call, localVarReturnType);
  }

  /**
   * (asynchronously) create a Domain.
   *
   * @param namespace object name and auth scope, such as for teams and projects (required)
   * @param body (required)
   * @param pretty If &#39;true&#39;, then the output is pretty printed. (optional)
   * @param callback The callback to be executed when the API call finishes
   * @return The request call
   * @throws ApiException If fail to process the API call, e.g. serializing the request body object
   */
  public com.squareup.okhttp.Call createWebLogicOracleNamespacedDomainAsync(
      String namespace, Domain body, String pretty, final ApiCallback<Domain> callback)
      throws ApiException {

    ProgressResponseBody.ProgressListener progressListener = null;
    ProgressRequestBody.ProgressRequestListener progressRequestListener = null;

    if (callback != null) {
      progressListener =
          (bytesRead, contentLength, done) ->
              callback.onDownloadProgress(bytesRead, contentLength, done);

      progressRequestListener =
          (bytesWritten, contentLength, done) ->
              callback.onUploadProgress(bytesWritten, contentLength, done);
    }

    com.squareup.okhttp.Call call =
        createWebLogicOracleNamespacedDomainValidateBeforeCall(
            namespace, body, pretty, progressListener, progressRequestListener);
    Type localVarReturnType = new TypeToken<Domain>() {}.getType();
    apiClient.executeAsync(call, localVarReturnType, callback);
    return call;
  }

  /**
   * Build call for deleteWebLogicOracleCollectionNamespacedDomain.
   *
   * @param namespace object name and auth scope, such as for teams and projects (required)
   * @param pretty If &#39;true&#39;, then the output is pretty printed. (optional)
   * @param _continue The continue option should be set when retrieving more results from the
   *     server. Since this value is server defined, clients may only use the continue value from a
   *     previous query result with identical query parameters (except for the value of continue)
   *     and the server may reject a continue value it does not recognize. If the specified continue
   *     value is no longer valid whether due to expiration (generally five to fifteen minutes) or a
   *     configuration change on the server the server will respond with a 410 ResourceExpired error
   *     indicating the client must restart their list without the continue field. This field is not
   *     supported when watch is true. Clients may start a watch from the last resourceVersion value
   *     returned by the server and not miss any modifications. (optional)
   * @param fieldSelector A selector to restrict the list of returned objects by their fields.
   *     Defaults to everything. (optional)
   * @param includeUninitialized If true, partially initialized resources are included in the
   *     response. (optional)
   * @param labelSelector A selector to restrict the list of returned objects by their labels.
   *     Defaults to everything. (optional)
   * @param limit limit is a maximum number of responses to return for a list call. If more items
   *     exist, the server will set the &#x60;continue&#x60; field on the list metadata to a value
   *     that can be used with the same initial query to retrieve the next set of results. Setting a
   *     limit may return fewer than the requested amount of items (up to zero items) in the event
   *     all requested objects are filtered out and clients should only use the presence of the
   *     continue field to determine whether more results are available. Servers may choose not to
   *     support the limit argument and will return all of the available results. If limit is
   *     specified and the continue field is empty, clients may assume that no more results are
   *     available. This field is not supported if watch is true. The server guarantees that the
   *     objects returned when using continue will be identical to issuing a single list call
   *     without a limit - that is, no objects created, modified, or deleted after the first request
   *     is issued will be included in any subsequent continued requests. This is sometimes referred
   *     to as a consistent snapshot, and ensures that a client that is using limit to receive
   *     smaller chunks of a very large result can ensure they see all possible objects. If objects
   *     are updated during a chunked list the version of the object that was present at the time
   *     the first list result was calculated is returned. (optional)
   * @param resourceVersion When specified with a watch call, shows changes that occur after that
   *     particular version of a resource. Defaults to changes from the beginning of history. When
   *     specified for list: - if unset, then the result is returned from remote storage based on
   *     quorum-read flag; - if it&#39;s 0, then we simply return what we currently have in cache,
   *     no guarantee; - if set to non zero, then the result is at least as fresh as given rv.
   *     (optional)
   * @param timeoutSeconds Timeout for the list/watch call. (optional)
   * @param watch Watch for changes to the described resources and return them as a stream of add,
   *     update, and remove notifications. Specify resourceVersion. (optional)
   * @param progressListener Progress listener
   * @param progressRequestListener Progress request listener
   * @return Call to execute
   * @throws ApiException If fail to serialize the request body object
   */
  public com.squareup.okhttp.Call deleteWebLogicOracleCollectionNamespacedDomainCall(
      String namespace,
      String pretty,
      String _continue,
      String fieldSelector,
      Boolean includeUninitialized,
      String labelSelector,
      Integer limit,
      String resourceVersion,
      Integer timeoutSeconds,
      Boolean watch,
      final ProgressResponseBody.ProgressListener progressListener,
      final ProgressRequestBody.ProgressRequestListener progressRequestListener)
      throws ApiException {
    Object localVarPostBody = null;

    // create path and map variables
    String localVarPath =
        "/apis/weblogic.oracle/"
            + DOMAIN_VERSION
            + "/namespaces/{namespace}/domains"
                .replaceAll("\\{" + "namespace" + "\\}", apiClient.escapeString(namespace));

    List<Pair> localVarQueryParams = new ArrayList<Pair>();
    List<Pair> localVarCollectionQueryParams = new ArrayList<Pair>();
    if (pretty != null) {
      localVarQueryParams.addAll(apiClient.parameterToPair("pretty", pretty));
    }
    if (_continue != null) {
      localVarQueryParams.addAll(apiClient.parameterToPair("continue", _continue));
    }
    if (fieldSelector != null) {
      localVarQueryParams.addAll(apiClient.parameterToPair("fieldSelector", fieldSelector));
    }
    if (includeUninitialized != null) {
      localVarQueryParams.addAll(
          apiClient.parameterToPair("includeUninitialized", includeUninitialized));
    }
    if (labelSelector != null) {
      localVarQueryParams.addAll(apiClient.parameterToPair("labelSelector", labelSelector));
    }
    if (limit != null) {
      localVarQueryParams.addAll(apiClient.parameterToPair("limit", limit));
    }
    if (resourceVersion != null) {
      localVarQueryParams.addAll(apiClient.parameterToPair("resourceVersion", resourceVersion));
    }
    if (timeoutSeconds != null) {
      localVarQueryParams.addAll(apiClient.parameterToPair("timeoutSeconds", timeoutSeconds));
    }
    if (watch != null) {
      localVarQueryParams.addAll(apiClient.parameterToPair("watch", watch));
    }

    Map<String, String> localVarHeaderParams = new HashMap<String, String>();

    Map<String, Object> localVarFormParams = new HashMap<String, Object>();

    final String[] localVarAccepts = {
      "application/json", "application/yaml", "application/vnd.kubernetes.protobuf"
    };
    final String localVarAccept = apiClient.selectHeaderAccept(localVarAccepts);
    if (localVarAccept != null) localVarHeaderParams.put("Accept", localVarAccept);

    final String[] localVarContentTypes = {"*/*"};
    final String localVarContentType = apiClient.selectHeaderContentType(localVarContentTypes);
    localVarHeaderParams.put("Content-Type", localVarContentType);

    if (progressListener != null) {
      apiClient
          .getHttpClient()
          .networkInterceptors()
          .add(
              chain -> {
                com.squareup.okhttp.Response originalResponse = chain.proceed(chain.request());
                return originalResponse
                    .newBuilder()
                    .body(new ProgressResponseBody(originalResponse.body(), progressListener))
                    .build();
              });
    }

    String[] localVarAuthNames = new String[] {"BearerToken"};
    return apiClient.buildCall(
        localVarPath,
        "DELETE",
        localVarQueryParams,
        localVarCollectionQueryParams,
        localVarPostBody,
        localVarHeaderParams,
        localVarFormParams,
        localVarAuthNames,
        progressRequestListener);
  }

  private com.squareup.okhttp.Call deleteWebLogicOracleCollectionNamespacedDomainValidateBeforeCall(
      String namespace,
      String pretty,
      String _continue,
      String fieldSelector,
      Boolean includeUninitialized,
      String labelSelector,
      Integer limit,
      String resourceVersion,
      Integer timeoutSeconds,
      Boolean watch,
      final ProgressResponseBody.ProgressListener progressListener,
      final ProgressRequestBody.ProgressRequestListener progressRequestListener)
      throws ApiException {

    // verify the required parameter 'namespace' is set
    if (namespace == null) {
      throw new ApiException(
          "Missing the required parameter 'namespace' when calling deleteWebLogicOracleCollectionNamespacedDomain(Async)");
    }

    com.squareup.okhttp.Call call =
        deleteWebLogicOracleCollectionNamespacedDomainCall(
            namespace,
            pretty,
            _continue,
            fieldSelector,
            includeUninitialized,
            labelSelector,
            limit,
            resourceVersion,
            timeoutSeconds,
            watch,
            progressListener,
            progressRequestListener);
    return call;
  }

  /**
   * delete collection of Domain.
   *
   * @param namespace object name and auth scope, such as for teams and projects (required)
   * @param pretty If &#39;true&#39;, then the output is pretty printed. (optional)
   * @param _continue The continue option should be set when retrieving more results from the
   *     server. Since this value is server defined, clients may only use the continue value from a
   *     previous query result with identical query parameters (except for the value of continue)
   *     and the server may reject a continue value it does not recognize. If the specified continue
   *     value is no longer valid whether due to expiration (generally five to fifteen minutes) or a
   *     configuration change on the server the server will respond with a 410 ResourceExpired error
   *     indicating the client must restart their list without the continue field. This field is not
   *     supported when watch is true. Clients may start a watch from the last resourceVersion value
   *     returned by the server and not miss any modifications. (optional)
   * @param fieldSelector A selector to restrict the list of returned objects by their fields.
   *     Defaults to everything. (optional)
   * @param includeUninitialized If true, partially initialized resources are included in the
   *     response. (optional)
   * @param labelSelector A selector to restrict the list of returned objects by their labels.
   *     Defaults to everything. (optional)
   * @param limit limit is a maximum number of responses to return for a list call. If more items
   *     exist, the server will set the &#x60;continue&#x60; field on the list metadata to a value
   *     that can be used with the same initial query to retrieve the next set of results. Setting a
   *     limit may return fewer than the requested amount of items (up to zero items) in the event
   *     all requested objects are filtered out and clients should only use the presence of the
   *     continue field to determine whether more results are available. Servers may choose not to
   *     support the limit argument and will return all of the available results. If limit is
   *     specified and the continue field is empty, clients may assume that no more results are
   *     available. This field is not supported if watch is true. The server guarantees that the
   *     objects returned when using continue will be identical to issuing a single list call
   *     without a limit - that is, no objects created, modified, or deleted after the first request
   *     is issued will be included in any subsequent continued requests. This is sometimes referred
   *     to as a consistent snapshot, and ensures that a client that is using limit to receive
   *     smaller chunks of a very large result can ensure they see all possible objects. If objects
   *     are updated during a chunked list the version of the object that was present at the time
   *     the first list result was calculated is returned. (optional)
   * @param resourceVersion When specified with a watch call, shows changes that occur after that
   *     particular version of a resource. Defaults to changes from the beginning of history. When
   *     specified for list: - if unset, then the result is returned from remote storage based on
   *     quorum-read flag; - if it&#39;s 0, then we simply return what we currently have in cache,
   *     no guarantee; - if set to non zero, then the result is at least as fresh as given rv.
   *     (optional)
   * @param timeoutSeconds Timeout for the list/watch call. (optional)
   * @param watch Watch for changes to the described resources and return them as a stream of add,
   *     update, and remove notifications. Specify resourceVersion. (optional)
   * @return V1Status
   * @throws ApiException If fail to call the API, e.g. server error or cannot deserialize the
   *     response body
   */
  public V1Status deleteWebLogicOracleCollectionNamespacedDomain(
      String namespace,
      String pretty,
      String _continue,
      String fieldSelector,
      Boolean includeUninitialized,
      String labelSelector,
      Integer limit,
      String resourceVersion,
      Integer timeoutSeconds,
      Boolean watch)
      throws ApiException {
    ApiResponse<V1Status> resp =
        deleteWebLogicOracleCollectionNamespacedDomainWithHttpInfo(
            namespace,
            pretty,
            _continue,
            fieldSelector,
            includeUninitialized,
            labelSelector,
            limit,
            resourceVersion,
            timeoutSeconds,
            watch);
    return resp.getData();
  }

  /**
   * delete collection of Domain.
   *
   * @param namespace object name and auth scope, such as for teams and projects (required)
   * @param pretty If &#39;true&#39;, then the output is pretty printed. (optional)
   * @param _continue The continue option should be set when retrieving more results from the
   *     server. Since this value is server defined, clients may only use the continue value from a
   *     previous query result with identical query parameters (except for the value of continue)
   *     and the server may reject a continue value it does not recognize. If the specified continue
   *     value is no longer valid whether due to expiration (generally five to fifteen minutes) or a
   *     configuration change on the server the server will respond with a 410 ResourceExpired error
   *     indicating the client must restart their list without the continue field. This field is not
   *     supported when watch is true. Clients may start a watch from the last resourceVersion value
   *     returned by the server and not miss any modifications. (optional)
   * @param fieldSelector A selector to restrict the list of returned objects by their fields.
   *     Defaults to everything. (optional)
   * @param includeUninitialized If true, partially initialized resources are included in the
   *     response. (optional)
   * @param labelSelector A selector to restrict the list of returned objects by their labels.
   *     Defaults to everything. (optional)
   * @param limit limit is a maximum number of responses to return for a list call. If more items
   *     exist, the server will set the &#x60;continue&#x60; field on the list metadata to a value
   *     that can be used with the same initial query to retrieve the next set of results. Setting a
   *     limit may return fewer than the requested amount of items (up to zero items) in the event
   *     all requested objects are filtered out and clients should only use the presence of the
   *     continue field to determine whether more results are available. Servers may choose not to
   *     support the limit argument and will return all of the available results. If limit is
   *     specified and the continue field is empty, clients may assume that no more results are
   *     available. This field is not supported if watch is true. The server guarantees that the
   *     objects returned when using continue will be identical to issuing a single list call
   *     without a limit - that is, no objects created, modified, or deleted after the first request
   *     is issued will be included in any subsequent continued requests. This is sometimes referred
   *     to as a consistent snapshot, and ensures that a client that is using limit to receive
   *     smaller chunks of a very large result can ensure they see all possible objects. If objects
   *     are updated during a chunked list the version of the object that was present at the time
   *     the first list result was calculated is returned. (optional)
   * @param resourceVersion When specified with a watch call, shows changes that occur after that
   *     particular version of a resource. Defaults to changes from the beginning of history. When
   *     specified for list: - if unset, then the result is returned from remote storage based on
   *     quorum-read flag; - if it&#39;s 0, then we simply return what we currently have in cache,
   *     no guarantee; - if set to non zero, then the result is at least as fresh as given rv.
   *     (optional)
   * @param timeoutSeconds Timeout for the list/watch call. (optional)
   * @param watch Watch for changes to the described resources and return them as a stream of add,
   *     update, and remove notifications. Specify resourceVersion. (optional)
   * @return ApiResponse&lt;V1Status&gt;
   * @throws ApiException If fail to call the API, e.g. server error or cannot deserialize the
   *     response body
   */
  public ApiResponse<V1Status> deleteWebLogicOracleCollectionNamespacedDomainWithHttpInfo(
      String namespace,
      String pretty,
      String _continue,
      String fieldSelector,
      Boolean includeUninitialized,
      String labelSelector,
      Integer limit,
      String resourceVersion,
      Integer timeoutSeconds,
      Boolean watch)
      throws ApiException {
    com.squareup.okhttp.Call call =
        deleteWebLogicOracleCollectionNamespacedDomainValidateBeforeCall(
            namespace,
            pretty,
            _continue,
            fieldSelector,
            includeUninitialized,
            labelSelector,
            limit,
            resourceVersion,
            timeoutSeconds,
            watch,
            null,
            null);
    Type localVarReturnType = new TypeToken<V1Status>() {}.getType();
    return apiClient.execute(call, localVarReturnType);
  }

  /**
   * (asynchronously) delete collection of Domain.
   *
   * @param namespace object name and auth scope, such as for teams and projects (required)
   * @param pretty If &#39;true&#39;, then the output is pretty printed. (optional)
   * @param _continue The continue option should be set when retrieving more results from the
   *     server. Since this value is server defined, clients may only use the continue value from a
   *     previous query result with identical query parameters (except for the value of continue)
   *     and the server may reject a continue value it does not recognize. If the specified continue
   *     value is no longer valid whether due to expiration (generally five to fifteen minutes) or a
   *     configuration change on the server the server will respond with a 410 ResourceExpired error
   *     indicating the client must restart their list without the continue field. This field is not
   *     supported when watch is true. Clients may start a watch from the last resourceVersion value
   *     returned by the server and not miss any modifications. (optional)
   * @param fieldSelector A selector to restrict the list of returned objects by their fields.
   *     Defaults to everything. (optional)
   * @param includeUninitialized If true, partially initialized resources are included in the
   *     response. (optional)
   * @param labelSelector A selector to restrict the list of returned objects by their labels.
   *     Defaults to everything. (optional)
   * @param limit limit is a maximum number of responses to return for a list call. If more items
   *     exist, the server will set the &#x60;continue&#x60; field on the list metadata to a value
   *     that can be used with the same initial query to retrieve the next set of results. Setting a
   *     limit may return fewer than the requested amount of items (up to zero items) in the event
   *     all requested objects are filtered out and clients should only use the presence of the
   *     continue field to determine whether more results are available. Servers may choose not to
   *     support the limit argument and will return all of the available results. If limit is
   *     specified and the continue field is empty, clients may assume that no more results are
   *     available. This field is not supported if watch is true. The server guarantees that the
   *     objects returned when using continue will be identical to issuing a single list call
   *     without a limit - that is, no objects created, modified, or deleted after the first request
   *     is issued will be included in any subsequent continued requests. This is sometimes referred
   *     to as a consistent snapshot, and ensures that a client that is using limit to receive
   *     smaller chunks of a very large result can ensure they see all possible objects. If objects
   *     are updated during a chunked list the version of the object that was present at the time
   *     the first list result was calculated is returned. (optional)
   * @param resourceVersion When specified with a watch call, shows changes that occur after that
   *     particular version of a resource. Defaults to changes from the beginning of history. When
   *     specified for list: - if unset, then the result is returned from remote storage based on
   *     quorum-read flag; - if it&#39;s 0, then we simply return what we currently have in cache,
   *     no guarantee; - if set to non zero, then the result is at least as fresh as given rv.
   *     (optional)
   * @param timeoutSeconds Timeout for the list/watch call. (optional)
   * @param watch Watch for changes to the described resources and return them as a stream of add,
   *     update, and remove notifications. Specify resourceVersion. (optional)
   * @param callback The callback to be executed when the API call finishes
   * @return The request call
   * @throws ApiException If fail to process the API call, e.g. serializing the request body object
   */
  public com.squareup.okhttp.Call deleteWebLogicOracleCollectionNamespacedDomainAsync(
      String namespace,
      String pretty,
      String _continue,
      String fieldSelector,
      Boolean includeUninitialized,
      String labelSelector,
      Integer limit,
      String resourceVersion,
      Integer timeoutSeconds,
      Boolean watch,
      final ApiCallback<V1Status> callback)
      throws ApiException {

    ProgressResponseBody.ProgressListener progressListener = null;
    ProgressRequestBody.ProgressRequestListener progressRequestListener = null;

    if (callback != null) {
      progressListener =
          (bytesRead, contentLength, done) ->
              callback.onDownloadProgress(bytesRead, contentLength, done);

      progressRequestListener =
          (bytesWritten, contentLength, done) ->
              callback.onUploadProgress(bytesWritten, contentLength, done);
    }

    com.squareup.okhttp.Call call =
        deleteWebLogicOracleCollectionNamespacedDomainValidateBeforeCall(
            namespace,
            pretty,
            _continue,
            fieldSelector,
            includeUninitialized,
            labelSelector,
            limit,
            resourceVersion,
            timeoutSeconds,
            watch,
            progressListener,
            progressRequestListener);
    Type localVarReturnType = new TypeToken<V1Status>() {}.getType();
    apiClient.executeAsync(call, localVarReturnType, callback);
    return call;
  }

  /**
   * Build call for deleteWebLogicOracleNamespacedDomain.
   *
   * @param name name of the Domain (required)
   * @param namespace object name and auth scope, such as for teams and projects (required)
   * @param body (required)
   * @param pretty If &#39;true&#39;, then the output is pretty printed. (optional)
   * @param gracePeriodSeconds The duration in seconds before the object should be deleted. Value
   *     must be non-negative integer. The value zero indicates delete immediately. If this value is
   *     nil, the default grace period for the specified type will be used. Defaults to a per object
   *     value if not specified. zero means delete immediately. (optional)
   * @param orphanDependents Deprecated: please use the PropagationPolicy, this field will be
   *     deprecated in 1.7. Should the dependent objects be orphaned. If true/false, the
   *     \&quot;orphan\&quot; finalizer will be added to/removed from the object&#39;s finalizers
   *     list. Either this field or PropagationPolicy may be set, but not both. (optional)
   * @param propagationPolicy Whether and how garbage collection will be performed. Either this
   *     field or OrphanDependents may be set, but not both. The default policy is decided by the
   *     existing finalizer set in the metadata.finalizers and the resource-specific default policy.
   *     (optional)
   * @param progressListener Progress listener
   * @param progressRequestListener Progress request listener
   * @return Call to execute
   * @throws ApiException If fail to serialize the request body object
   */
  public com.squareup.okhttp.Call deleteWebLogicOracleNamespacedDomainCall(
      String name,
      String namespace,
      V1DeleteOptions body,
      String pretty,
      Integer gracePeriodSeconds,
      Boolean orphanDependents,
      String propagationPolicy,
      final ProgressResponseBody.ProgressListener progressListener,
      final ProgressRequestBody.ProgressRequestListener progressRequestListener)
      throws ApiException {
    Object localVarPostBody = body;

    // create path and map variables
    String localVarPath =
        "/apis/weblogic.oracle/"
            + DOMAIN_VERSION
            + "/namespaces/{namespace}/domains/{name}"
                .replaceAll("\\{" + "name" + "\\}", apiClient.escapeString(name))
                .replaceAll("\\{" + "namespace" + "\\}", apiClient.escapeString(namespace));

    List<Pair> localVarQueryParams = new ArrayList<Pair>();
    List<Pair> localVarCollectionQueryParams = new ArrayList<Pair>();
    if (pretty != null) {
      localVarQueryParams.addAll(apiClient.parameterToPair("pretty", pretty));
    }
    if (gracePeriodSeconds != null) {
      localVarQueryParams.addAll(
          apiClient.parameterToPair("gracePeriodSeconds", gracePeriodSeconds));
    }
    if (orphanDependents != null) {
      localVarQueryParams.addAll(apiClient.parameterToPair("orphanDependents", orphanDependents));
    }
    if (propagationPolicy != null) {
      localVarQueryParams.addAll(apiClient.parameterToPair("propagationPolicy", propagationPolicy));
    }

    Map<String, String> localVarHeaderParams = new HashMap<String, String>();

    Map<String, Object> localVarFormParams = new HashMap<String, Object>();

    final String[] localVarAccepts = {
      "application/json", "application/yaml", "application/vnd.kubernetes.protobuf"
    };
    final String localVarAccept = apiClient.selectHeaderAccept(localVarAccepts);
    if (localVarAccept != null) localVarHeaderParams.put("Accept", localVarAccept);

    final String[] localVarContentTypes = {"*/*"};
    final String localVarContentType = apiClient.selectHeaderContentType(localVarContentTypes);
    localVarHeaderParams.put("Content-Type", localVarContentType);

    if (progressListener != null) {
      apiClient
          .getHttpClient()
          .networkInterceptors()
          .add(
              chain -> {
                com.squareup.okhttp.Response originalResponse = chain.proceed(chain.request());
                return originalResponse
                    .newBuilder()
                    .body(new ProgressResponseBody(originalResponse.body(), progressListener))
                    .build();
              });
    }

    String[] localVarAuthNames = new String[] {"BearerToken"};
    return apiClient.buildCall(
        localVarPath,
        "DELETE",
        localVarQueryParams,
        localVarCollectionQueryParams,
        localVarPostBody,
        localVarHeaderParams,
        localVarFormParams,
        localVarAuthNames,
        progressRequestListener);
  }

  private com.squareup.okhttp.Call deleteWebLogicOracleNamespacedDomainValidateBeforeCall(
      String name,
      String namespace,
      V1DeleteOptions body,
      String pretty,
      Integer gracePeriodSeconds,
      Boolean orphanDependents,
      String propagationPolicy,
      final ProgressResponseBody.ProgressListener progressListener,
      final ProgressRequestBody.ProgressRequestListener progressRequestListener)
      throws ApiException {

    // verify the required parameter 'name' is set
    if (name == null) {
      throw new ApiException(
          "Missing the required parameter 'name' when calling deleteWebLogicOracleNamespacedDomain(Async)");
    }

    // verify the required parameter 'namespace' is set
    if (namespace == null) {
      throw new ApiException(
          "Missing the required parameter 'namespace' when calling deleteWebLogicOracleNamespacedDomain(Async)");
    }

    // verify the required parameter 'body' is set
    if (body == null) {
      throw new ApiException(
          "Missing the required parameter 'body' when calling deleteWebLogicOracleNamespacedDomain(Async)");
    }

    com.squareup.okhttp.Call call =
        deleteWebLogicOracleNamespacedDomainCall(
            name,
            namespace,
            body,
            pretty,
            gracePeriodSeconds,
            orphanDependents,
            propagationPolicy,
            progressListener,
            progressRequestListener);
    return call;
  }

  /**
   * delete a Domain.
   *
   * @param name name of the Domain (required)
   * @param namespace object name and auth scope, such as for teams and projects (required)
   * @param body (required)
   * @param pretty If &#39;true&#39;, then the output is pretty printed. (optional)
   * @param gracePeriodSeconds The duration in seconds before the object should be deleted. Value
   *     must be non-negative integer. The value zero indicates delete immediately. If this value is
   *     nil, the default grace period for the specified type will be used. Defaults to a per object
   *     value if not specified. zero means delete immediately. (optional)
   * @param orphanDependents Deprecated: please use the PropagationPolicy, this field will be
   *     deprecated in 1.7. Should the dependent objects be orphaned. If true/false, the
   *     \&quot;orphan\&quot; finalizer will be added to/removed from the object&#39;s finalizers
   *     list. Either this field or PropagationPolicy may be set, but not both. (optional)
   * @param propagationPolicy Whether and how garbage collection will be performed. Either this
   *     field or OrphanDependents may be set, but not both. The default policy is decided by the
   *     existing finalizer set in the metadata.finalizers and the resource-specific default policy.
   *     (optional)
   * @return V1Status
   * @throws ApiException If fail to call the API, e.g. server error or cannot deserialize the
   *     response body
   */
  public V1Status deleteWebLogicOracleNamespacedDomain(
      String name,
      String namespace,
      V1DeleteOptions body,
      String pretty,
      Integer gracePeriodSeconds,
      Boolean orphanDependents,
      String propagationPolicy)
      throws ApiException {
    ApiResponse<V1Status> resp =
        deleteWebLogicOracleNamespacedDomainWithHttpInfo(
            name, namespace, body, pretty, gracePeriodSeconds, orphanDependents, propagationPolicy);
    return resp.getData();
  }

  /**
   * delete a Domain.
   *
   * @param name name of the Domain (required)
   * @param namespace object name and auth scope, such as for teams and projects (required)
   * @param body (required)
   * @param pretty If &#39;true&#39;, then the output is pretty printed. (optional)
   * @param gracePeriodSeconds The duration in seconds before the object should be deleted. Value
   *     must be non-negative integer. The value zero indicates delete immediately. If this value is
   *     nil, the default grace period for the specified type will be used. Defaults to a per object
   *     value if not specified. zero means delete immediately. (optional)
   * @param orphanDependents Deprecated: please use the PropagationPolicy, this field will be
   *     deprecated in 1.7. Should the dependent objects be orphaned. If true/false, the
   *     \&quot;orphan\&quot; finalizer will be added to/removed from the object&#39;s finalizers
   *     list. Either this field or PropagationPolicy may be set, but not both. (optional)
   * @param propagationPolicy Whether and how garbage collection will be performed. Either this
   *     field or OrphanDependents may be set, but not both. The default policy is decided by the
   *     existing finalizer set in the metadata.finalizers and the resource-specific default policy.
   *     (optional)
   * @return ApiResponse&lt;V1Status&gt;
   * @throws ApiException If fail to call the API, e.g. server error or cannot deserialize the
   *     response body
   */
  public ApiResponse<V1Status> deleteWebLogicOracleNamespacedDomainWithHttpInfo(
      String name,
      String namespace,
      V1DeleteOptions body,
      String pretty,
      Integer gracePeriodSeconds,
      Boolean orphanDependents,
      String propagationPolicy)
      throws ApiException {
    com.squareup.okhttp.Call call =
        deleteWebLogicOracleNamespacedDomainValidateBeforeCall(
            name,
            namespace,
            body,
            pretty,
            gracePeriodSeconds,
            orphanDependents,
            propagationPolicy,
            null,
            null);
    Type localVarReturnType = new TypeToken<V1Status>() {}.getType();
    return apiClient.execute(call, localVarReturnType);
  }

  /**
   * (asynchronously) delete a Domain.
   *
   * @param name name of the Domain (required)
   * @param namespace object name and auth scope, such as for teams and projects (required)
   * @param body (required)
   * @param pretty If &#39;true&#39;, then the output is pretty printed. (optional)
   * @param gracePeriodSeconds The duration in seconds before the object should be deleted. Value
   *     must be non-negative integer. The value zero indicates delete immediately. If this value is
   *     nil, the default grace period for the specified type will be used. Defaults to a per object
   *     value if not specified. zero means delete immediately. (optional)
   * @param orphanDependents Deprecated: please use the PropagationPolicy, this field will be
   *     deprecated in 1.7. Should the dependent objects be orphaned. If true/false, the
   *     \&quot;orphan\&quot; finalizer will be added to/removed from the object&#39;s finalizers
   *     list. Either this field or PropagationPolicy may be set, but not both. (optional)
   * @param propagationPolicy Whether and how garbage collection will be performed. Either this
   *     field or OrphanDependents may be set, but not both. The default policy is decided by the
   *     existing finalizer set in the metadata.finalizers and the resource-specific default policy.
   *     (optional)
   * @param callback The callback to be executed when the API call finishes
   * @return The request call
   * @throws ApiException If fail to process the API call, e.g. serializing the request body object
   */
  public com.squareup.okhttp.Call deleteWebLogicOracleNamespacedDomainAsync(
      String name,
      String namespace,
      V1DeleteOptions body,
      String pretty,
      Integer gracePeriodSeconds,
      Boolean orphanDependents,
      String propagationPolicy,
      final ApiCallback<V1Status> callback)
      throws ApiException {

    ProgressResponseBody.ProgressListener progressListener = null;
    ProgressRequestBody.ProgressRequestListener progressRequestListener = null;

    if (callback != null) {
      progressListener =
          (bytesRead, contentLength, done) ->
              callback.onDownloadProgress(bytesRead, contentLength, done);

      progressRequestListener =
          (bytesWritten, contentLength, done) ->
              callback.onUploadProgress(bytesWritten, contentLength, done);
    }

    com.squareup.okhttp.Call call =
        deleteWebLogicOracleNamespacedDomainValidateBeforeCall(
            name,
            namespace,
            body,
            pretty,
            gracePeriodSeconds,
            orphanDependents,
            propagationPolicy,
            progressListener,
            progressRequestListener);
    Type localVarReturnType = new TypeToken<V1Status>() {}.getType();
    apiClient.executeAsync(call, localVarReturnType, callback);
    return call;
  }

  /**
   * Build call for listWebLogicOracleDomainForAllNamespaces.
   *
   * @param _continue The continue option should be set when retrieving more results from the
   *     server. Since this value is server defined, clients may only use the continue value from a
   *     previous query result with identical query parameters (except for the value of continue)
   *     and the server may reject a continue value it does not recognize. If the specified continue
   *     value is no longer valid whether due to expiration (generally five to fifteen minutes) or a
   *     configuration change on the server the server will respond with a 410 ResourceExpired error
   *     indicating the client must restart their list without the continue field. This field is not
   *     supported when watch is true. Clients may start a watch from the last resourceVersion value
   *     returned by the server and not miss any modifications. (optional)
   * @param fieldSelector A selector to restrict the list of returned objects by their fields.
   *     Defaults to everything. (optional)
   * @param includeUninitialized If true, partially initialized resources are included in the
   *     response. (optional)
   * @param labelSelector A selector to restrict the list of returned objects by their labels.
   *     Defaults to everything. (optional)
   * @param limit limit is a maximum number of responses to return for a list call. If more items
   *     exist, the server will set the &#x60;continue&#x60; field on the list metadata to a value
   *     that can be used with the same initial query to retrieve the next set of results. Setting a
   *     limit may return fewer than the requested amount of items (up to zero items) in the event
   *     all requested objects are filtered out and clients should only use the presence of the
   *     continue field to determine whether more results are available. Servers may choose not to
   *     support the limit argument and will return all of the available results. If limit is
   *     specified and the continue field is empty, clients may assume that no more results are
   *     available. This field is not supported if watch is true. The server guarantees that the
   *     objects returned when using continue will be identical to issuing a single list call
   *     without a limit - that is, no objects created, modified, or deleted after the first request
   *     is issued will be included in any subsequent continued requests. This is sometimes referred
   *     to as a consistent snapshot, and ensures that a client that is using limit to receive
   *     smaller chunks of a very large result can ensure they see all possible objects. If objects
   *     are updated during a chunked list the version of the object that was present at the time
   *     the first list result was calculated is returned. (optional)
   * @param pretty If &#39;true&#39;, then the output is pretty printed. (optional)
   * @param resourceVersion When specified with a watch call, shows changes that occur after that
   *     particular version of a resource. Defaults to changes from the beginning of history. When
   *     specified for list: - if unset, then the result is returned from remote storage based on
   *     quorum-read flag; - if it&#39;s 0, then we simply return what we currently have in cache,
   *     no guarantee; - if set to non zero, then the result is at least as fresh as given rv.
   *     (optional)
   * @param timeoutSeconds Timeout for the list/watch call. (optional)
   * @param watch Watch for changes to the described resources and return them as a stream of add,
   *     update, and remove notifications. Specify resourceVersion. (optional)
   * @param progressListener Progress listener
   * @param progressRequestListener Progress request listener
   * @return Call to execute
   * @throws ApiException If fail to serialize the request body object
   */
  public com.squareup.okhttp.Call listWebLogicOracleDomainForAllNamespacesCall(
      String _continue,
      String fieldSelector,
      Boolean includeUninitialized,
      String labelSelector,
      Integer limit,
      String pretty,
      String resourceVersion,
      Integer timeoutSeconds,
      Boolean watch,
      final ProgressResponseBody.ProgressListener progressListener,
      final ProgressRequestBody.ProgressRequestListener progressRequestListener)
      throws ApiException {
    Object localVarPostBody = null;

    // create path and map variables
    String localVarPath = "/apis/weblogic.oracle/" + DOMAIN_VERSION + "/domains";

    List<Pair> localVarQueryParams = new ArrayList<Pair>();
    List<Pair> localVarCollectionQueryParams = new ArrayList<Pair>();
    if (_continue != null) {
      localVarQueryParams.addAll(apiClient.parameterToPair("continue", _continue));
    }
    if (fieldSelector != null) {
      localVarQueryParams.addAll(apiClient.parameterToPair("fieldSelector", fieldSelector));
    }
    if (includeUninitialized != null) {
      localVarQueryParams.addAll(
          apiClient.parameterToPair("includeUninitialized", includeUninitialized));
    }
    if (labelSelector != null) {
      localVarQueryParams.addAll(apiClient.parameterToPair("labelSelector", labelSelector));
    }
    if (limit != null) {
      localVarQueryParams.addAll(apiClient.parameterToPair("limit", limit));
    }
    if (pretty != null) {
      localVarQueryParams.addAll(apiClient.parameterToPair("pretty", pretty));
    }
    if (resourceVersion != null) {
      localVarQueryParams.addAll(apiClient.parameterToPair("resourceVersion", resourceVersion));
    }
    if (timeoutSeconds != null) {
      localVarQueryParams.addAll(apiClient.parameterToPair("timeoutSeconds", timeoutSeconds));
    }
    if (watch != null) {
      localVarQueryParams.addAll(apiClient.parameterToPair("watch", watch));
    }

    Map<String, String> localVarHeaderParams = new HashMap<String, String>();

    Map<String, Object> localVarFormParams = new HashMap<String, Object>();

    final String[] localVarAccepts = {
      "application/json",
      "application/yaml",
      "application/vnd.kubernetes.protobuf",
      "application/json;stream=watch",
      "application/vnd.kubernetes.protobuf;stream=watch"
    };
    final String localVarAccept = apiClient.selectHeaderAccept(localVarAccepts);
    if (localVarAccept != null) localVarHeaderParams.put("Accept", localVarAccept);

    final String[] localVarContentTypes = {"*/*"};
    final String localVarContentType = apiClient.selectHeaderContentType(localVarContentTypes);
    localVarHeaderParams.put("Content-Type", localVarContentType);

    if (progressListener != null) {
      apiClient
          .getHttpClient()
          .networkInterceptors()
          .add(
              chain -> {
                com.squareup.okhttp.Response originalResponse = chain.proceed(chain.request());
                return originalResponse
                    .newBuilder()
                    .body(new ProgressResponseBody(originalResponse.body(), progressListener))
                    .build();
              });
    }

    String[] localVarAuthNames = new String[] {"BearerToken"};
    return apiClient.buildCall(
        localVarPath,
        "GET",
        localVarQueryParams,
        localVarCollectionQueryParams,
        localVarPostBody,
        localVarHeaderParams,
        localVarFormParams,
        localVarAuthNames,
        progressRequestListener);
  }

  private com.squareup.okhttp.Call listWebLogicOracleDomainForAllNamespacesValidateBeforeCall(
      String _continue,
      String fieldSelector,
      Boolean includeUninitialized,
      String labelSelector,
      Integer limit,
      String pretty,
      String resourceVersion,
      Integer timeoutSeconds,
      Boolean watch,
      final ProgressResponseBody.ProgressListener progressListener,
      final ProgressRequestBody.ProgressRequestListener progressRequestListener)
      throws ApiException {

    com.squareup.okhttp.Call call =
        listWebLogicOracleDomainForAllNamespacesCall(
            _continue,
            fieldSelector,
            includeUninitialized,
            labelSelector,
            limit,
            pretty,
            resourceVersion,
            timeoutSeconds,
            watch,
            progressListener,
            progressRequestListener);
    return call;
  }

  /**
   * list or watch objects of kind Domain.
   *
   * @param _continue The continue option should be set when retrieving more results from the
   *     server. Since this value is server defined, clients may only use the continue value from a
   *     previous query result with identical query parameters (except for the value of continue)
   *     and the server may reject a continue value it does not recognize. If the specified continue
   *     value is no longer valid whether due to expiration (generally five to fifteen minutes) or a
   *     configuration change on the server the server will respond with a 410 ResourceExpired error
   *     indicating the client must restart their list without the continue field. This field is not
   *     supported when watch is true. Clients may start a watch from the last resourceVersion value
   *     returned by the server and not miss any modifications. (optional)
   * @param fieldSelector A selector to restrict the list of returned objects by their fields.
   *     Defaults to everything. (optional)
   * @param includeUninitialized If true, partially initialized resources are included in the
   *     response. (optional)
   * @param labelSelector A selector to restrict the list of returned objects by their labels.
   *     Defaults to everything. (optional)
   * @param limit limit is a maximum number of responses to return for a list call. If more items
   *     exist, the server will set the &#x60;continue&#x60; field on the list metadata to a value
   *     that can be used with the same initial query to retrieve the next set of results. Setting a
   *     limit may return fewer than the requested amount of items (up to zero items) in the event
   *     all requested objects are filtered out and clients should only use the presence of the
   *     continue field to determine whether more results are available. Servers may choose not to
   *     support the limit argument and will return all of the available results. If limit is
   *     specified and the continue field is empty, clients may assume that no more results are
   *     available. This field is not supported if watch is true. The server guarantees that the
   *     objects returned when using continue will be identical to issuing a single list call
   *     without a limit - that is, no objects created, modified, or deleted after the first request
   *     is issued will be included in any subsequent continued requests. This is sometimes referred
   *     to as a consistent snapshot, and ensures that a client that is using limit to receive
   *     smaller chunks of a very large result can ensure they see all possible objects. If objects
   *     are updated during a chunked list the version of the object that was present at the time
   *     the first list result was calculated is returned. (optional)
   * @param pretty If &#39;true&#39;, then the output is pretty printed. (optional)
   * @param resourceVersion When specified with a watch call, shows changes that occur after that
   *     particular version of a resource. Defaults to changes from the beginning of history. When
   *     specified for list: - if unset, then the result is returned from remote storage based on
   *     quorum-read flag; - if it&#39;s 0, then we simply return what we currently have in cache,
   *     no guarantee; - if set to non zero, then the result is at least as fresh as given rv.
   *     (optional)
   * @param timeoutSeconds Timeout for the list/watch call. (optional)
   * @param watch Watch for changes to the described resources and return them as a stream of add,
   *     update, and remove notifications. Specify resourceVersion. (optional)
   * @return DomainList
   * @throws ApiException If fail to call the API, e.g. server error or cannot deserialize the
   *     response body
   */
  public DomainList listWebLogicOracleDomainForAllNamespaces(
      String _continue,
      String fieldSelector,
      Boolean includeUninitialized,
      String labelSelector,
      Integer limit,
      String pretty,
      String resourceVersion,
      Integer timeoutSeconds,
      Boolean watch)
      throws ApiException {
    ApiResponse<DomainList> resp =
        listWebLogicOracleDomainForAllNamespacesWithHttpInfo(
            _continue,
            fieldSelector,
            includeUninitialized,
            labelSelector,
            limit,
            pretty,
            resourceVersion,
            timeoutSeconds,
            watch);
    return resp.getData();
  }

  /**
   * list or watch objects of kind Domain.
   *
   * @param _continue The continue option should be set when retrieving more results from the
   *     server. Since this value is server defined, clients may only use the continue value from a
   *     previous query result with identical query parameters (except for the value of continue)
   *     and the server may reject a continue value it does not recognize. If the specified continue
   *     value is no longer valid whether due to expiration (generally five to fifteen minutes) or a
   *     configuration change on the server the server will respond with a 410 ResourceExpired error
   *     indicating the client must restart their list without the continue field. This field is not
   *     supported when watch is true. Clients may start a watch from the last resourceVersion value
   *     returned by the server and not miss any modifications. (optional)
   * @param fieldSelector A selector to restrict the list of returned objects by their fields.
   *     Defaults to everything. (optional)
   * @param includeUninitialized If true, partially initialized resources are included in the
   *     response. (optional)
   * @param labelSelector A selector to restrict the list of returned objects by their labels.
   *     Defaults to everything. (optional)
   * @param limit limit is a maximum number of responses to return for a list call. If more items
   *     exist, the server will set the &#x60;continue&#x60; field on the list metadata to a value
   *     that can be used with the same initial query to retrieve the next set of results. Setting a
   *     limit may return fewer than the requested amount of items (up to zero items) in the event
   *     all requested objects are filtered out and clients should only use the presence of the
   *     continue field to determine whether more results are available. Servers may choose not to
   *     support the limit argument and will return all of the available results. If limit is
   *     specified and the continue field is empty, clients may assume that no more results are
   *     available. This field is not supported if watch is true. The server guarantees that the
   *     objects returned when using continue will be identical to issuing a single list call
   *     without a limit - that is, no objects created, modified, or deleted after the first request
   *     is issued will be included in any subsequent continued requests. This is sometimes referred
   *     to as a consistent snapshot, and ensures that a client that is using limit to receive
   *     smaller chunks of a very large result can ensure they see all possible objects. If objects
   *     are updated during a chunked list the version of the object that was present at the time
   *     the first list result was calculated is returned. (optional)
   * @param pretty If &#39;true&#39;, then the output is pretty printed. (optional)
   * @param resourceVersion When specified with a watch call, shows changes that occur after that
   *     particular version of a resource. Defaults to changes from the beginning of history. When
   *     specified for list: - if unset, then the result is returned from remote storage based on
   *     quorum-read flag; - if it&#39;s 0, then we simply return what we currently have in cache,
   *     no guarantee; - if set to non zero, then the result is at least as fresh as given rv.
   *     (optional)
   * @param timeoutSeconds Timeout for the list/watch call. (optional)
   * @param watch Watch for changes to the described resources and return them as a stream of add,
   *     update, and remove notifications. Specify resourceVersion. (optional)
   * @return ApiResponse&lt;DomainList&gt;
   * @throws ApiException If fail to call the API, e.g. server error or cannot deserialize the
   *     response body
   */
  public ApiResponse<DomainList> listWebLogicOracleDomainForAllNamespacesWithHttpInfo(
      String _continue,
      String fieldSelector,
      Boolean includeUninitialized,
      String labelSelector,
      Integer limit,
      String pretty,
      String resourceVersion,
      Integer timeoutSeconds,
      Boolean watch)
      throws ApiException {
    com.squareup.okhttp.Call call =
        listWebLogicOracleDomainForAllNamespacesValidateBeforeCall(
            _continue,
            fieldSelector,
            includeUninitialized,
            labelSelector,
            limit,
            pretty,
            resourceVersion,
            timeoutSeconds,
            watch,
            null,
            null);
    Type localVarReturnType = new TypeToken<DomainList>() {}.getType();
    return apiClient.execute(call, localVarReturnType);
  }

  /**
   * (asynchronously) list or watch objects of kind Domain.
   *
   * @param _continue The continue option should be set when retrieving more results from the
   *     server. Since this value is server defined, clients may only use the continue value from a
   *     previous query result with identical query parameters (except for the value of continue)
   *     and the server may reject a continue value it does not recognize. If the specified continue
   *     value is no longer valid whether due to expiration (generally five to fifteen minutes) or a
   *     configuration change on the server the server will respond with a 410 ResourceExpired error
   *     indicating the client must restart their list without the continue field. This field is not
   *     supported when watch is true. Clients may start a watch from the last resourceVersion value
   *     returned by the server and not miss any modifications. (optional)
   * @param fieldSelector A selector to restrict the list of returned objects by their fields.
   *     Defaults to everything. (optional)
   * @param includeUninitialized If true, partially initialized resources are included in the
   *     response. (optional)
   * @param labelSelector A selector to restrict the list of returned objects by their labels.
   *     Defaults to everything. (optional)
   * @param limit limit is a maximum number of responses to return for a list call. If more items
   *     exist, the server will set the &#x60;continue&#x60; field on the list metadata to a value
   *     that can be used with the same initial query to retrieve the next set of results. Setting a
   *     limit may return fewer than the requested amount of items (up to zero items) in the event
   *     all requested objects are filtered out and clients should only use the presence of the
   *     continue field to determine whether more results are available. Servers may choose not to
   *     support the limit argument and will return all of the available results. If limit is
   *     specified and the continue field is empty, clients may assume that no more results are
   *     available. This field is not supported if watch is true. The server guarantees that the
   *     objects returned when using continue will be identical to issuing a single list call
   *     without a limit - that is, no objects created, modified, or deleted after the first request
   *     is issued will be included in any subsequent continued requests. This is sometimes referred
   *     to as a consistent snapshot, and ensures that a client that is using limit to receive
   *     smaller chunks of a very large result can ensure they see all possible objects. If objects
   *     are updated during a chunked list the version of the object that was present at the time
   *     the first list result was calculated is returned. (optional)
   * @param pretty If &#39;true&#39;, then the output is pretty printed. (optional)
   * @param resourceVersion When specified with a watch call, shows changes that occur after that
   *     particular version of a resource. Defaults to changes from the beginning of history. When
   *     specified for list: - if unset, then the result is returned from remote storage based on
   *     quorum-read flag; - if it&#39;s 0, then we simply return what we currently have in cache,
   *     no guarantee; - if set to non zero, then the result is at least as fresh as given rv.
   *     (optional)
   * @param timeoutSeconds Timeout for the list/watch call. (optional)
   * @param watch Watch for changes to the described resources and return them as a stream of add,
   *     update, and remove notifications. Specify resourceVersion. (optional)
   * @param callback The callback to be executed when the API call finishes
   * @return The request call
   * @throws ApiException If fail to process the API call, e.g. serializing the request body object
   */
  public com.squareup.okhttp.Call listWebLogicOracleDomainForAllNamespacesAsync(
      String _continue,
      String fieldSelector,
      Boolean includeUninitialized,
      String labelSelector,
      Integer limit,
      String pretty,
      String resourceVersion,
      Integer timeoutSeconds,
      Boolean watch,
      final ApiCallback<DomainList> callback)
      throws ApiException {

    ProgressResponseBody.ProgressListener progressListener = null;
    ProgressRequestBody.ProgressRequestListener progressRequestListener = null;

    if (callback != null) {
      progressListener =
          (bytesRead, contentLength, done) ->
              callback.onDownloadProgress(bytesRead, contentLength, done);

      progressRequestListener =
          (bytesWritten, contentLength, done) ->
              callback.onUploadProgress(bytesWritten, contentLength, done);
    }

    com.squareup.okhttp.Call call =
        listWebLogicOracleDomainForAllNamespacesValidateBeforeCall(
            _continue,
            fieldSelector,
            includeUninitialized,
            labelSelector,
            limit,
            pretty,
            resourceVersion,
            timeoutSeconds,
            watch,
            progressListener,
            progressRequestListener);
    Type localVarReturnType = new TypeToken<DomainList>() {}.getType();
    apiClient.executeAsync(call, localVarReturnType, callback);
    return call;
  }

  /**
   * Build call for listWebLogicOracleNamespacedDomain.
   *
   * @param namespace object name and auth scope, such as for teams and projects (required)
   * @param pretty If &#39;true&#39;, then the output is pretty printed. (optional)
   * @param _continue The continue option should be set when retrieving more results from the
   *     server. Since this value is server defined, clients may only use the continue value from a
   *     previous query result with identical query parameters (except for the value of continue)
   *     and the server may reject a continue value it does not recognize. If the specified continue
   *     value is no longer valid whether due to expiration (generally five to fifteen minutes) or a
   *     configuration change on the server the server will respond with a 410 ResourceExpired error
   *     indicating the client must restart their list without the continue field. This field is not
   *     supported when watch is true. Clients may start a watch from the last resourceVersion value
   *     returned by the server and not miss any modifications. (optional)
   * @param fieldSelector A selector to restrict the list of returned objects by their fields.
   *     Defaults to everything. (optional)
   * @param includeUninitialized If true, partially initialized resources are included in the
   *     response. (optional)
   * @param labelSelector A selector to restrict the list of returned objects by their labels.
   *     Defaults to everything. (optional)
   * @param limit limit is a maximum number of responses to return for a list call. If more items
   *     exist, the server will set the &#x60;continue&#x60; field on the list metadata to a value
   *     that can be used with the same initial query to retrieve the next set of results. Setting a
   *     limit may return fewer than the requested amount of items (up to zero items) in the event
   *     all requested objects are filtered out and clients should only use the presence of the
   *     continue field to determine whether more results are available. Servers may choose not to
   *     support the limit argument and will return all of the available results. If limit is
   *     specified and the continue field is empty, clients may assume that no more results are
   *     available. This field is not supported if watch is true. The server guarantees that the
   *     objects returned when using continue will be identical to issuing a single list call
   *     without a limit - that is, no objects created, modified, or deleted after the first request
   *     is issued will be included in any subsequent continued requests. This is sometimes referred
   *     to as a consistent snapshot, and ensures that a client that is using limit to receive
   *     smaller chunks of a very large result can ensure they see all possible objects. If objects
   *     are updated during a chunked list the version of the object that was present at the time
   *     the first list result was calculated is returned. (optional)
   * @param resourceVersion When specified with a watch call, shows changes that occur after that
   *     particular version of a resource. Defaults to changes from the beginning of history. When
   *     specified for list: - if unset, then the result is returned from remote storage based on
   *     quorum-read flag; - if it&#39;s 0, then we simply return what we currently have in cache,
   *     no guarantee; - if set to non zero, then the result is at least as fresh as given rv.
   *     (optional)
   * @param timeoutSeconds Timeout for the list/watch call. (optional)
   * @param watch Watch for changes to the described resources and return them as a stream of add,
   *     update, and remove notifications. Specify resourceVersion. (optional)
   * @param progressListener Progress listener
   * @param progressRequestListener Progress request listener
   * @return Call to execute
   * @throws ApiException If fail to serialize the request body object
   */
  public com.squareup.okhttp.Call listWebLogicOracleNamespacedDomainCall(
      String namespace,
      String pretty,
      String _continue,
      String fieldSelector,
      Boolean includeUninitialized,
      String labelSelector,
      Integer limit,
      String resourceVersion,
      Integer timeoutSeconds,
      Boolean watch,
      final ProgressResponseBody.ProgressListener progressListener,
      final ProgressRequestBody.ProgressRequestListener progressRequestListener)
      throws ApiException {
    Object localVarPostBody = null;

    // create path and map variables
    String localVarPath =
        "/apis/weblogic.oracle/"
            + DOMAIN_VERSION
            + "/namespaces/{namespace}/domains"
                .replaceAll("\\{" + "namespace" + "\\}", apiClient.escapeString(namespace));

    List<Pair> localVarQueryParams = new ArrayList<Pair>();
    List<Pair> localVarCollectionQueryParams = new ArrayList<Pair>();
    if (pretty != null) {
      localVarQueryParams.addAll(apiClient.parameterToPair("pretty", pretty));
    }
    if (_continue != null) {
      localVarQueryParams.addAll(apiClient.parameterToPair("continue", _continue));
    }
    if (fieldSelector != null) {
      localVarQueryParams.addAll(apiClient.parameterToPair("fieldSelector", fieldSelector));
    }
    if (includeUninitialized != null) {
      localVarQueryParams.addAll(
          apiClient.parameterToPair("includeUninitialized", includeUninitialized));
    }
    if (labelSelector != null) {
      localVarQueryParams.addAll(apiClient.parameterToPair("labelSelector", labelSelector));
    }
    if (limit != null) {
      localVarQueryParams.addAll(apiClient.parameterToPair("limit", limit));
    }
    if (resourceVersion != null) {
      localVarQueryParams.addAll(apiClient.parameterToPair("resourceVersion", resourceVersion));
    }
    if (timeoutSeconds != null) {
      localVarQueryParams.addAll(apiClient.parameterToPair("timeoutSeconds", timeoutSeconds));
    }
    if (watch != null) {
      localVarQueryParams.addAll(apiClient.parameterToPair("watch", watch));
    }

    Map<String, String> localVarHeaderParams = new HashMap<String, String>();

    Map<String, Object> localVarFormParams = new HashMap<String, Object>();

    final String[] localVarAccepts = {
      "application/json",
      "application/yaml",
      "application/vnd.kubernetes.protobuf",
      "application/json;stream=watch",
      "application/vnd.kubernetes.protobuf;stream=watch"
    };
    final String localVarAccept = apiClient.selectHeaderAccept(localVarAccepts);
    if (localVarAccept != null) localVarHeaderParams.put("Accept", localVarAccept);

    final String[] localVarContentTypes = {"*/*"};
    final String localVarContentType = apiClient.selectHeaderContentType(localVarContentTypes);
    localVarHeaderParams.put("Content-Type", localVarContentType);

    if (progressListener != null) {
      apiClient
          .getHttpClient()
          .networkInterceptors()
          .add(
              chain -> {
                com.squareup.okhttp.Response originalResponse = chain.proceed(chain.request());
                return originalResponse
                    .newBuilder()
                    .body(new ProgressResponseBody(originalResponse.body(), progressListener))
                    .build();
              });
    }

    String[] localVarAuthNames = new String[] {"BearerToken"};
    return apiClient.buildCall(
        localVarPath,
        "GET",
        localVarQueryParams,
        localVarCollectionQueryParams,
        localVarPostBody,
        localVarHeaderParams,
        localVarFormParams,
        localVarAuthNames,
        progressRequestListener);
  }

  private com.squareup.okhttp.Call listWebLogicOracleNamespacedDomainValidateBeforeCall(
      String namespace,
      String pretty,
      String _continue,
      String fieldSelector,
      Boolean includeUninitialized,
      String labelSelector,
      Integer limit,
      String resourceVersion,
      Integer timeoutSeconds,
      Boolean watch,
      final ProgressResponseBody.ProgressListener progressListener,
      final ProgressRequestBody.ProgressRequestListener progressRequestListener)
      throws ApiException {

    // verify the required parameter 'namespace' is set
    if (namespace == null) {
      throw new ApiException(
          "Missing the required parameter 'namespace' when calling listWebLogicOracleNamespacedDomain(Async)");
    }

    com.squareup.okhttp.Call call =
        listWebLogicOracleNamespacedDomainCall(
            namespace,
            pretty,
            _continue,
            fieldSelector,
            includeUninitialized,
            labelSelector,
            limit,
            resourceVersion,
            timeoutSeconds,
            watch,
            progressListener,
            progressRequestListener);
    return call;
  }

  /**
   * list or watch objects of kind Domain.
   *
   * @param namespace object name and auth scope, such as for teams and projects (required)
   * @param pretty If &#39;true&#39;, then the output is pretty printed. (optional)
   * @param _continue The continue option should be set when retrieving more results from the
   *     server. Since this value is server defined, clients may only use the continue value from a
   *     previous query result with identical query parameters (except for the value of continue)
   *     and the server may reject a continue value it does not recognize. If the specified continue
   *     value is no longer valid whether due to expiration (generally five to fifteen minutes) or a
   *     configuration change on the server the server will respond with a 410 ResourceExpired error
   *     indicating the client must restart their list without the continue field. This field is not
   *     supported when watch is true. Clients may start a watch from the last resourceVersion value
   *     returned by the server and not miss any modifications. (optional)
   * @param fieldSelector A selector to restrict the list of returned objects by their fields.
   *     Defaults to everything. (optional)
   * @param includeUninitialized If true, partially initialized resources are included in the
   *     response. (optional)
   * @param labelSelector A selector to restrict the list of returned objects by their labels.
   *     Defaults to everything. (optional)
   * @param limit limit is a maximum number of responses to return for a list call. If more items
   *     exist, the server will set the &#x60;continue&#x60; field on the list metadata to a value
   *     that can be used with the same initial query to retrieve the next set of results. Setting a
   *     limit may return fewer than the requested amount of items (up to zero items) in the event
   *     all requested objects are filtered out and clients should only use the presence of the
   *     continue field to determine whether more results are available. Servers may choose not to
   *     support the limit argument and will return all of the available results. If limit is
   *     specified and the continue field is empty, clients may assume that no more results are
   *     available. This field is not supported if watch is true. The server guarantees that the
   *     objects returned when using continue will be identical to issuing a single list call
   *     without a limit - that is, no objects created, modified, or deleted after the first request
   *     is issued will be included in any subsequent continued requests. This is sometimes referred
   *     to as a consistent snapshot, and ensures that a client that is using limit to receive
   *     smaller chunks of a very large result can ensure they see all possible objects. If objects
   *     are updated during a chunked list the version of the object that was present at the time
   *     the first list result was calculated is returned. (optional)
   * @param resourceVersion When specified with a watch call, shows changes that occur after that
   *     particular version of a resource. Defaults to changes from the beginning of history. When
   *     specified for list: - if unset, then the result is returned from remote storage based on
   *     quorum-read flag; - if it&#39;s 0, then we simply return what we currently have in cache,
   *     no guarantee; - if set to non zero, then the result is at least as fresh as given rv.
   *     (optional)
   * @param timeoutSeconds Timeout for the list/watch call. (optional)
   * @param watch Watch for changes to the described resources and return them as a stream of add,
   *     update, and remove notifications. Specify resourceVersion. (optional)
   * @return DomainList
   * @throws ApiException If fail to call the API, e.g. server error or cannot deserialize the
   *     response body
   */
  public DomainList listWebLogicOracleNamespacedDomain(
      String namespace,
      String pretty,
      String _continue,
      String fieldSelector,
      Boolean includeUninitialized,
      String labelSelector,
      Integer limit,
      String resourceVersion,
      Integer timeoutSeconds,
      Boolean watch)
      throws ApiException {
    ApiResponse<DomainList> resp =
        listWebLogicOracleNamespacedDomainWithHttpInfo(
            namespace,
            pretty,
            _continue,
            fieldSelector,
            includeUninitialized,
            labelSelector,
            limit,
            resourceVersion,
            timeoutSeconds,
            watch);
    return resp.getData();
  }

  /**
   * list or watch objects of kind Domain.
   *
   * @param namespace object name and auth scope, such as for teams and projects (required)
   * @param pretty If &#39;true&#39;, then the output is pretty printed. (optional)
   * @param _continue The continue option should be set when retrieving more results from the
   *     server. Since this value is server defined, clients may only use the continue value from a
   *     previous query result with identical query parameters (except for the value of continue)
   *     and the server may reject a continue value it does not recognize. If the specified continue
   *     value is no longer valid whether due to expiration (generally five to fifteen minutes) or a
   *     configuration change on the server the server will respond with a 410 ResourceExpired error
   *     indicating the client must restart their list without the continue field. This field is not
   *     supported when watch is true. Clients may start a watch from the last resourceVersion value
   *     returned by the server and not miss any modifications. (optional)
   * @param fieldSelector A selector to restrict the list of returned objects by their fields.
   *     Defaults to everything. (optional)
   * @param includeUninitialized If true, partially initialized resources are included in the
   *     response. (optional)
   * @param labelSelector A selector to restrict the list of returned objects by their labels.
   *     Defaults to everything. (optional)
   * @param limit limit is a maximum number of responses to return for a list call. If more items
   *     exist, the server will set the &#x60;continue&#x60; field on the list metadata to a value
   *     that can be used with the same initial query to retrieve the next set of results. Setting a
   *     limit may return fewer than the requested amount of items (up to zero items) in the event
   *     all requested objects are filtered out and clients should only use the presence of the
   *     continue field to determine whether more results are available. Servers may choose not to
   *     support the limit argument and will return all of the available results. If limit is
   *     specified and the continue field is empty, clients may assume that no more results are
   *     available. This field is not supported if watch is true. The server guarantees that the
   *     objects returned when using continue will be identical to issuing a single list call
   *     without a limit - that is, no objects created, modified, or deleted after the first request
   *     is issued will be included in any subsequent continued requests. This is sometimes referred
   *     to as a consistent snapshot, and ensures that a client that is using limit to receive
   *     smaller chunks of a very large result can ensure they see all possible objects. If objects
   *     are updated during a chunked list the version of the object that was present at the time
   *     the first list result was calculated is returned. (optional)
   * @param resourceVersion When specified with a watch call, shows changes that occur after that
   *     particular version of a resource. Defaults to changes from the beginning of history. When
   *     specified for list: - if unset, then the result is returned from remote storage based on
   *     quorum-read flag; - if it&#39;s 0, then we simply return what we currently have in cache,
   *     no guarantee; - if set to non zero, then the result is at least as fresh as given rv.
   *     (optional)
   * @param timeoutSeconds Timeout for the list/watch call. (optional)
   * @param watch Watch for changes to the described resources and return them as a stream of add,
   *     update, and remove notifications. Specify resourceVersion. (optional)
   * @return ApiResponse&lt;DomainList&gt;
   * @throws ApiException If fail to call the API, e.g. server error or cannot deserialize the
   *     response body
   */
  public ApiResponse<DomainList> listWebLogicOracleNamespacedDomainWithHttpInfo(
      String namespace,
      String pretty,
      String _continue,
      String fieldSelector,
      Boolean includeUninitialized,
      String labelSelector,
      Integer limit,
      String resourceVersion,
      Integer timeoutSeconds,
      Boolean watch)
      throws ApiException {
    com.squareup.okhttp.Call call =
        listWebLogicOracleNamespacedDomainValidateBeforeCall(
            namespace,
            pretty,
            _continue,
            fieldSelector,
            includeUninitialized,
            labelSelector,
            limit,
            resourceVersion,
            timeoutSeconds,
            watch,
            null,
            null);
    Type localVarReturnType = new TypeToken<DomainList>() {}.getType();
    return apiClient.execute(call, localVarReturnType);
  }

  /**
   * (asynchronously) list or watch objects of kind Domain.
   *
   * @param namespace object name and auth scope, such as for teams and projects (required)
   * @param pretty If &#39;true&#39;, then the output is pretty printed. (optional)
   * @param _continue The continue option should be set when retrieving more results from the
   *     server. Since this value is server defined, clients may only use the continue value from a
   *     previous query result with identical query parameters (except for the value of continue)
   *     and the server may reject a continue value it does not recognize. If the specified continue
   *     value is no longer valid whether due to expiration (generally five to fifteen minutes) or a
   *     configuration change on the server the server will respond with a 410 ResourceExpired error
   *     indicating the client must restart their list without the continue field. This field is not
   *     supported when watch is true. Clients may start a watch from the last resourceVersion value
   *     returned by the server and not miss any modifications. (optional)
   * @param fieldSelector A selector to restrict the list of returned objects by their fields.
   *     Defaults to everything. (optional)
   * @param includeUninitialized If true, partially initialized resources are included in the
   *     response. (optional)
   * @param labelSelector A selector to restrict the list of returned objects by their labels.
   *     Defaults to everything. (optional)
   * @param limit limit is a maximum number of responses to return for a list call. If more items
   *     exist, the server will set the &#x60;continue&#x60; field on the list metadata to a value
   *     that can be used with the same initial query to retrieve the next set of results. Setting a
   *     limit may return fewer than the requested amount of items (up to zero items) in the event
   *     all requested objects are filtered out and clients should only use the presence of the
   *     continue field to determine whether more results are available. Servers may choose not to
   *     support the limit argument and will return all of the available results. If limit is
   *     specified and the continue field is empty, clients may assume that no more results are
   *     available. This field is not supported if watch is true. The server guarantees that the
   *     objects returned when using continue will be identical to issuing a single list call
   *     without a limit - that is, no objects created, modified, or deleted after the first request
   *     is issued will be included in any subsequent continued requests. This is sometimes referred
   *     to as a consistent snapshot, and ensures that a client that is using limit to receive
   *     smaller chunks of a very large result can ensure they see all possible objects. If objects
   *     are updated during a chunked list the version of the object that was present at the time
   *     the first list result was calculated is returned. (optional)
   * @param resourceVersion When specified with a watch call, shows changes that occur after that
   *     particular version of a resource. Defaults to changes from the beginning of history. When
   *     specified for list: - if unset, then the result is returned from remote storage based on
   *     quorum-read flag; - if it&#39;s 0, then we simply return what we currently have in cache,
   *     no guarantee; - if set to non zero, then the result is at least as fresh as given rv.
   *     (optional)
   * @param timeoutSeconds Timeout for the list/watch call. (optional)
   * @param watch Watch for changes to the described resources and return them as a stream of add,
   *     update, and remove notifications. Specify resourceVersion. (optional)
   * @param callback The callback to be executed when the API call finishes
   * @return The request call
   * @throws ApiException If fail to process the API call, e.g. serializing the request body object
   */
  public com.squareup.okhttp.Call listWebLogicOracleNamespacedDomainAsync(
      String namespace,
      String pretty,
      String _continue,
      String fieldSelector,
      Boolean includeUninitialized,
      String labelSelector,
      Integer limit,
      String resourceVersion,
      Integer timeoutSeconds,
      Boolean watch,
      final ApiCallback<DomainList> callback)
      throws ApiException {

    ProgressResponseBody.ProgressListener progressListener = null;
    ProgressRequestBody.ProgressRequestListener progressRequestListener = null;

    if (callback != null) {
      progressListener =
          (bytesRead, contentLength, done) ->
              callback.onDownloadProgress(bytesRead, contentLength, done);

      progressRequestListener =
          (bytesWritten, contentLength, done) ->
              callback.onUploadProgress(bytesWritten, contentLength, done);
    }

    com.squareup.okhttp.Call call =
        listWebLogicOracleNamespacedDomainValidateBeforeCall(
            namespace,
            pretty,
            _continue,
            fieldSelector,
            includeUninitialized,
            labelSelector,
            limit,
            resourceVersion,
            timeoutSeconds,
            watch,
            progressListener,
            progressRequestListener);
    Type localVarReturnType = new TypeToken<DomainList>() {}.getType();
    apiClient.executeAsync(call, localVarReturnType, callback);
    return call;
  }

  /**
   * Build call for patchWebLogicOracleNamespacedDomain.
   *
   * @param name name of the Domain (required)
   * @param namespace object name and auth scope, such as for teams and projects (required)
   * @param body (required)
   * @param pretty If &#39;true&#39;, then the output is pretty printed. (optional)
   * @param progressListener Progress listener
   * @param progressRequestListener Progress request listener
   * @return Call to execute
   * @throws ApiException If fail to serialize the request body object
   */
  public com.squareup.okhttp.Call patchWebLogicOracleNamespacedDomainCall(
      String name,
      String namespace,
      Patch body,
      String pretty,
      final ProgressResponseBody.ProgressListener progressListener,
      final ProgressRequestBody.ProgressRequestListener progressRequestListener)
      throws ApiException {
    Object localVarPostBody = body;

    // create path and map variables
    String localVarPath =
        "/apis/weblogic.oracle/"
            + DOMAIN_VERSION
            + "/namespaces/{namespace}/domains/{name}"
                .replaceAll("\\{" + "name" + "\\}", apiClient.escapeString(name))
                .replaceAll("\\{" + "namespace" + "\\}", apiClient.escapeString(namespace));

    List<Pair> localVarQueryParams = new ArrayList<Pair>();
    List<Pair> localVarCollectionQueryParams = new ArrayList<Pair>();
    if (pretty != null) localVarQueryParams.addAll(apiClient.parameterToPair("pretty", pretty));

    Map<String, String> localVarHeaderParams = new HashMap<String, String>();

    Map<String, Object> localVarFormParams = new HashMap<String, Object>();

    final String[] localVarAccepts = {
      "application/json", "application/yaml", "application/vnd.kubernetes.protobuf"
    };
    final String localVarAccept = apiClient.selectHeaderAccept(localVarAccepts);
    if (localVarAccept != null) localVarHeaderParams.put("Accept", localVarAccept);

    final String[] localVarContentTypes = {
      "application/json-patch+json",
      "application/merge-patch+json",
      "application/strategic-merge-patch+json"
    };
    final String localVarContentType = apiClient.selectHeaderContentType(localVarContentTypes);
    localVarHeaderParams.put("Content-Type", localVarContentType);

    if (progressListener != null) {
      apiClient
          .getHttpClient()
          .networkInterceptors()
          .add(
              chain -> {
                com.squareup.okhttp.Response originalResponse = chain.proceed(chain.request());
                return originalResponse
                    .newBuilder()
                    .body(new ProgressResponseBody(originalResponse.body(), progressListener))
                    .build();
              });
    }

    String[] localVarAuthNames = new String[] {"BearerToken"};
    return apiClient.buildCall(
        localVarPath,
        "PATCH",
        localVarQueryParams,
        localVarCollectionQueryParams,
        localVarPostBody,
        localVarHeaderParams,
        localVarFormParams,
        localVarAuthNames,
        progressRequestListener);
  }

  private com.squareup.okhttp.Call patchWebLogicOracleNamespacedDomainValidateBeforeCall(
      String name,
      String namespace,
      Patch body,
      String pretty,
      final ProgressResponseBody.ProgressListener progressListener,
      final ProgressRequestBody.ProgressRequestListener progressRequestListener)
      throws ApiException {

    // verify the required parameter 'name' is set
    if (name == null) {
      throw new ApiException(
          "Missing the required parameter 'name' when calling patchWebLogicOracleNamespacedDomain(Async)");
    }

    // verify the required parameter 'namespace' is set
    if (namespace == null) {
      throw new ApiException(
          "Missing the required parameter 'namespace' when calling patchWebLogicOracleNamespacedDomain(Async)");
    }

    // verify the required parameter 'body' is set
    if (body == null) {
      throw new ApiException(
          "Missing the required parameter 'body' when calling patchWebLogicOracleNamespacedDomain(Async)");
    }

    com.squareup.okhttp.Call call =
        patchWebLogicOracleNamespacedDomainCall(
            name, namespace, body, pretty, progressListener, progressRequestListener);
    return call;
  }

  /**
   * partially update the specified Domain.
   *
   * @param name name of the Domain (required)
   * @param namespace object name and auth scope, such as for teams and projects (required)
   * @param body (required)
   * @param pretty If &#39;true&#39;, then the output is pretty printed. (optional)
   * @return Domain
   * @throws ApiException If fail to call the API, e.g. server error or cannot deserialize the
   *     response body
   */
  public Domain patchWebLogicOracleNamespacedDomain(
      String name, String namespace, Patch body, String pretty) throws ApiException {
    ApiResponse<Domain> resp =
        patchWebLogicOracleNamespacedDomainWithHttpInfo(name, namespace, body, pretty);
    return resp.getData();
  }

  /**
   * partially update the specified Domain.
   *
   * @param name name of the Domain (required)
   * @param namespace object name and auth scope, such as for teams and projects (required)
   * @param body (required)
   * @param pretty If &#39;true&#39;, then the output is pretty printed. (optional)
   * @return ApiResponse&lt;Domain&gt;
   * @throws ApiException If fail to call the API, e.g. server error or cannot deserialize the
   *     response body
   */
  public ApiResponse<Domain> patchWebLogicOracleNamespacedDomainWithHttpInfo(
      String name, String namespace, Patch body, String pretty) throws ApiException {
    com.squareup.okhttp.Call call =
        patchWebLogicOracleNamespacedDomainValidateBeforeCall(
            name, namespace, body, pretty, null, null);
    Type localVarReturnType = new TypeToken<Domain>() {}.getType();
    return apiClient.execute(call, localVarReturnType);
  }

  /**
   * (asynchronously) partially update the specified Domain.
   *
   * @param name name of the Domain (required)
   * @param namespace object name and auth scope, such as for teams and projects (required)
   * @param body (required)
   * @param pretty If &#39;true&#39;, then the output is pretty printed. (optional)
   * @param callback The callback to be executed when the API call finishes
   * @return The request call
   * @throws ApiException If fail to process the API call, e.g. serializing the request body object
   */
  public com.squareup.okhttp.Call patchWebLogicOracleNamespacedDomainAsync(
      String name, String namespace, Patch body, String pretty, final ApiCallback<Domain> callback)
      throws ApiException {

    ProgressResponseBody.ProgressListener progressListener = null;
    ProgressRequestBody.ProgressRequestListener progressRequestListener = null;

    if (callback != null) {
      progressListener =
          (bytesRead, contentLength, done) ->
              callback.onDownloadProgress(bytesRead, contentLength, done);

      progressRequestListener =
          (bytesWritten, contentLength, done) ->
              callback.onUploadProgress(bytesWritten, contentLength, done);
    }

    com.squareup.okhttp.Call call =
        patchWebLogicOracleNamespacedDomainValidateBeforeCall(
            name, namespace, body, pretty, progressListener, progressRequestListener);
    Type localVarReturnType = new TypeToken<Domain>() {}.getType();
    apiClient.executeAsync(call, localVarReturnType, callback);
    return call;
  }

  /**
   * Build call for patchWebLogicOracleNamespacedDomainScale.
   *
   * @param name name of the Scale (required)
   * @param namespace object name and auth scope, such as for teams and projects (required)
   * @param body (required)
   * @param pretty If &#39;true&#39;, then the output is pretty printed. (optional)
   * @param progressListener Progress listener
   * @param progressRequestListener Progress request listener
   * @return Call to execute
   * @throws ApiException If fail to serialize the request body object
   */
  public com.squareup.okhttp.Call patchWebLogicOracleNamespacedDomainScaleCall(
      String name,
      String namespace,
      Patch body,
      String pretty,
      final ProgressResponseBody.ProgressListener progressListener,
      final ProgressRequestBody.ProgressRequestListener progressRequestListener)
      throws ApiException {
    Object localVarPostBody = body;

    // create path and map variables
    String localVarPath =
        "/apis/weblogic.oracle/"
            + DOMAIN_VERSION
            + "/namespaces/{namespace}/domains/{name}/scale"
                .replaceAll("\\{" + "name" + "\\}", apiClient.escapeString(name))
                .replaceAll("\\{" + "namespace" + "\\}", apiClient.escapeString(namespace));

    List<Pair> localVarQueryParams = new ArrayList<Pair>();
    List<Pair> localVarCollectionQueryParams = new ArrayList<Pair>();
    if (pretty != null) localVarQueryParams.addAll(apiClient.parameterToPair("pretty", pretty));

    Map<String, String> localVarHeaderParams = new HashMap<String, String>();

    Map<String, Object> localVarFormParams = new HashMap<String, Object>();

    final String[] localVarAccepts = {
      "application/json", "application/yaml", "application/vnd.kubernetes.protobuf"
    };
    final String localVarAccept = apiClient.selectHeaderAccept(localVarAccepts);
    if (localVarAccept != null) localVarHeaderParams.put("Accept", localVarAccept);

    final String[] localVarContentTypes = {
      "application/json-patch+json",
      "application/merge-patch+json",
      "application/strategic-merge-patch+json"
    };
    final String localVarContentType = apiClient.selectHeaderContentType(localVarContentTypes);
    localVarHeaderParams.put("Content-Type", localVarContentType);

    if (progressListener != null) {
      apiClient
          .getHttpClient()
          .networkInterceptors()
          .add(
              chain -> {
                com.squareup.okhttp.Response originalResponse = chain.proceed(chain.request());
                return originalResponse
                    .newBuilder()
                    .body(new ProgressResponseBody(originalResponse.body(), progressListener))
                    .build();
              });
    }

    String[] localVarAuthNames = new String[] {"BearerToken"};
    return apiClient.buildCall(
        localVarPath,
        "PATCH",
        localVarQueryParams,
        localVarCollectionQueryParams,
        localVarPostBody,
        localVarHeaderParams,
        localVarFormParams,
        localVarAuthNames,
        progressRequestListener);
  }

  private com.squareup.okhttp.Call patchWebLogicOracleNamespacedDomainScaleValidateBeforeCall(
      String name,
      String namespace,
      Patch body,
      String pretty,
      final ProgressResponseBody.ProgressListener progressListener,
      final ProgressRequestBody.ProgressRequestListener progressRequestListener)
      throws ApiException {

    // verify the required parameter 'name' is set
    if (name == null) {
      throw new ApiException(
          "Missing the required parameter 'name' when calling patchWebLogicOracleNamespacedDomainScale(Async)");
    }

    // verify the required parameter 'namespace' is set
    if (namespace == null) {
      throw new ApiException(
          "Missing the required parameter 'namespace' when calling patchWebLogicOracleNamespacedDomainScale(Async)");
    }

    // verify the required parameter 'body' is set
    if (body == null) {
      throw new ApiException(
          "Missing the required parameter 'body' when calling patchWebLogicOracleNamespacedDomainScale(Async)");
    }

    com.squareup.okhttp.Call call =
        patchWebLogicOracleNamespacedDomainScaleCall(
            name, namespace, body, pretty, progressListener, progressRequestListener);
    return call;
  }

  /**
   * partially update scale of the specified Domain.
   *
   * @param name name of the Scale (required)
   * @param namespace object name and auth scope, such as for teams and projects (required)
   * @param body (required)
   * @param pretty If &#39;true&#39;, then the output is pretty printed. (optional)
   * @return V1Scale
   * @throws ApiException If fail to call the API, e.g. server error or cannot deserialize the
   *     response body
   */
  public V1Scale patchWebLogicOracleNamespacedDomainScale(
      String name, String namespace, Patch body, String pretty) throws ApiException {
    ApiResponse<V1Scale> resp =
        patchWebLogicOracleNamespacedDomainScaleWithHttpInfo(name, namespace, body, pretty);
    return resp.getData();
  }

  /**
   * partially update scale of the specified Domain.
   *
   * @param name name of the Scale (required)
   * @param namespace object name and auth scope, such as for teams and projects (required)
   * @param body (required)
   * @param pretty If &#39;true&#39;, then the output is pretty printed. (optional)
   * @return ApiResponse&lt;V1Scale&gt;
   * @throws ApiException If fail to call the API, e.g. server error or cannot deserialize the
   *     response body
   */
  public ApiResponse<V1Scale> patchWebLogicOracleNamespacedDomainScaleWithHttpInfo(
      String name, String namespace, Patch body, String pretty) throws ApiException {
    com.squareup.okhttp.Call call =
        patchWebLogicOracleNamespacedDomainScaleValidateBeforeCall(
            name, namespace, body, pretty, null, null);
    Type localVarReturnType = new TypeToken<V1Scale>() {}.getType();
    return apiClient.execute(call, localVarReturnType);
  }

  /**
   * (asynchronously) partially update scale of the specified Domain.
   *
   * @param name name of the Scale (required)
   * @param namespace object name and auth scope, such as for teams and projects (required)
   * @param body (required)
   * @param pretty If &#39;true&#39;, then the output is pretty printed. (optional)
   * @param callback The callback to be executed when the API call finishes
   * @return The request call
   * @throws ApiException If fail to process the API call, e.g. serializing the request body object
   */
  public com.squareup.okhttp.Call patchWebLogicOracleNamespacedDomainScaleAsync(
      String name, String namespace, Patch body, String pretty, final ApiCallback<V1Scale> callback)
      throws ApiException {

    ProgressResponseBody.ProgressListener progressListener = null;
    ProgressRequestBody.ProgressRequestListener progressRequestListener = null;

    if (callback != null) {
      progressListener =
          (bytesRead, contentLength, done) ->
              callback.onDownloadProgress(bytesRead, contentLength, done);

      progressRequestListener =
          (bytesWritten, contentLength, done) ->
              callback.onUploadProgress(bytesWritten, contentLength, done);
    }

    com.squareup.okhttp.Call call =
        patchWebLogicOracleNamespacedDomainScaleValidateBeforeCall(
            name, namespace, body, pretty, progressListener, progressRequestListener);
    Type localVarReturnType = new TypeToken<V1Scale>() {}.getType();
    apiClient.executeAsync(call, localVarReturnType, callback);
    return call;
  }

  /**
   * Build call for patchWebLogicOracleNamespacedDomainStatus.
   *
   * @param name name of the Domain (required)
   * @param namespace object name and auth scope, such as for teams and projects (required)
   * @param body (required)
   * @param pretty If &#39;true&#39;, then the output is pretty printed. (optional)
   * @param progressListener Progress listener
   * @param progressRequestListener Progress request listener
   * @return Call to execute
   * @throws ApiException If fail to serialize the request body object
   */
  public com.squareup.okhttp.Call patchWebLogicOracleNamespacedDomainStatusCall(
      String name,
      String namespace,
      Patch body,
      String pretty,
      final ProgressResponseBody.ProgressListener progressListener,
      final ProgressRequestBody.ProgressRequestListener progressRequestListener)
      throws ApiException {
    Object localVarPostBody = body;

    // create path and map variables
    String localVarPath =
        "/apis/weblogic.oracle/"
            + DOMAIN_VERSION
            + "/namespaces/{namespace}/domains/{name}/status"
                .replaceAll("\\{" + "name" + "\\}", apiClient.escapeString(name))
                .replaceAll("\\{" + "namespace" + "\\}", apiClient.escapeString(namespace));

    List<Pair> localVarQueryParams = new ArrayList<Pair>();
    List<Pair> localVarCollectionQueryParams = new ArrayList<Pair>();
    if (pretty != null) localVarQueryParams.addAll(apiClient.parameterToPair("pretty", pretty));

    Map<String, String> localVarHeaderParams = new HashMap<String, String>();

    Map<String, Object> localVarFormParams = new HashMap<String, Object>();

    final String[] localVarAccepts = {
      "application/json", "application/yaml", "application/vnd.kubernetes.protobuf"
    };
    final String localVarAccept = apiClient.selectHeaderAccept(localVarAccepts);
    if (localVarAccept != null) localVarHeaderParams.put("Accept", localVarAccept);

    final String[] localVarContentTypes = {
      "application/json-patch+json",
      "application/merge-patch+json",
      "application/strategic-merge-patch+json"
    };
    final String localVarContentType = apiClient.selectHeaderContentType(localVarContentTypes);
    localVarHeaderParams.put("Content-Type", localVarContentType);

    if (progressListener != null) {
      apiClient
          .getHttpClient()
          .networkInterceptors()
          .add(
              chain -> {
                com.squareup.okhttp.Response originalResponse = chain.proceed(chain.request());
                return originalResponse
                    .newBuilder()
                    .body(new ProgressResponseBody(originalResponse.body(), progressListener))
                    .build();
              });
    }

    String[] localVarAuthNames = new String[] {"BearerToken"};
    return apiClient.buildCall(
        localVarPath,
        "PATCH",
        localVarQueryParams,
        localVarCollectionQueryParams,
        localVarPostBody,
        localVarHeaderParams,
        localVarFormParams,
        localVarAuthNames,
        progressRequestListener);
  }

  private com.squareup.okhttp.Call patchWebLogicOracleNamespacedDomainStatusValidateBeforeCall(
      String name,
      String namespace,
      Patch body,
      String pretty,
      final ProgressResponseBody.ProgressListener progressListener,
      final ProgressRequestBody.ProgressRequestListener progressRequestListener)
      throws ApiException {

    // verify the required parameter 'name' is set
    if (name == null) {
      throw new ApiException(
          "Missing the required parameter 'name' when calling patchWebLogicOracleNamespacedDomainStatus(Async)");
    }

    // verify the required parameter 'namespace' is set
    if (namespace == null) {
      throw new ApiException(
          "Missing the required parameter 'namespace' when calling patchWebLogicOracleNamespacedDomainStatus(Async)");
    }

    // verify the required parameter 'body' is set
    if (body == null) {
      throw new ApiException(
          "Missing the required parameter 'body' when calling patchWebLogicOracleNamespacedDomainStatus(Async)");
    }

    com.squareup.okhttp.Call call =
        patchWebLogicOracleNamespacedDomainStatusCall(
            name, namespace, body, pretty, progressListener, progressRequestListener);
    return call;
  }

  /**
   * partially update status of the specified Domain.
   *
   * @param name name of the Domain (required)
   * @param namespace object name and auth scope, such as for teams and projects (required)
   * @param body (required)
   * @param pretty If &#39;true&#39;, then the output is pretty printed. (optional)
   * @return Domain
   * @throws ApiException If fail to call the API, e.g. server error or cannot deserialize the
   *     response body
   */
  public Domain patchWebLogicOracleNamespacedDomainStatus(
      String name, String namespace, Patch body, String pretty) throws ApiException {
    ApiResponse<Domain> resp =
        patchWebLogicOracleNamespacedDomainStatusWithHttpInfo(name, namespace, body, pretty);
    return resp.getData();
  }

  /**
   * partially update status of the specified Domain.
   *
   * @param name name of the Domain (required)
   * @param namespace object name and auth scope, such as for teams and projects (required)
   * @param body (required)
   * @param pretty If &#39;true&#39;, then the output is pretty printed. (optional)
   * @return ApiResponse&lt;Domain&gt;
   * @throws ApiException If fail to call the API, e.g. server error or cannot deserialize the
   *     response body
   */
  public ApiResponse<Domain> patchWebLogicOracleNamespacedDomainStatusWithHttpInfo(
      String name, String namespace, Patch body, String pretty) throws ApiException {
    com.squareup.okhttp.Call call =
        patchWebLogicOracleNamespacedDomainStatusValidateBeforeCall(
            name, namespace, body, pretty, null, null);
    Type localVarReturnType = new TypeToken<Domain>() {}.getType();
    return apiClient.execute(call, localVarReturnType);
  }

  /**
   * (asynchronously) partially update status of the specified Domain.
   *
   * @param name name of the Domain (required)
   * @param namespace object name and auth scope, such as for teams and projects (required)
   * @param body (required)
   * @param pretty If &#39;true&#39;, then the output is pretty printed. (optional)
   * @param callback The callback to be executed when the API call finishes
   * @return The request call
   * @throws ApiException If fail to process the API call, e.g. serializing the request body object
   */
  public com.squareup.okhttp.Call patchWebLogicOracleNamespacedDomainStatusAsync(
      String name, String namespace, Patch body, String pretty, final ApiCallback<Domain> callback)
      throws ApiException {

    ProgressResponseBody.ProgressListener progressListener = null;
    ProgressRequestBody.ProgressRequestListener progressRequestListener = null;

    if (callback != null) {
      progressListener =
          (bytesRead, contentLength, done) ->
              callback.onDownloadProgress(bytesRead, contentLength, done);

      progressRequestListener =
          (bytesWritten, contentLength, done) ->
              callback.onUploadProgress(bytesWritten, contentLength, done);
    }

    com.squareup.okhttp.Call call =
        patchWebLogicOracleNamespacedDomainStatusValidateBeforeCall(
            name, namespace, body, pretty, progressListener, progressRequestListener);
    Type localVarReturnType = new TypeToken<Domain>() {}.getType();
    apiClient.executeAsync(call, localVarReturnType, callback);
    return call;
  }

  /**
   * Build call for readWebLogicOracleNamespacedDomain.
   *
   * @param name name of the Domain (required)
   * @param namespace object name and auth scope, such as for teams and projects (required)
   * @param pretty If &#39;true&#39;, then the output is pretty printed. (optional)
   * @param exact Should the export be exact. Exact export maintains cluster-specific fields like
   *     &#39;Namespace&#39;. (optional)
   * @param export Should this value be exported. Export strips fields that a user can not specify.
   *     (optional)
   * @param progressListener Progress listener
   * @param progressRequestListener Progress request listener
   * @return Call to execute
   * @throws ApiException If fail to serialize the request body object
   */
  public com.squareup.okhttp.Call readWebLogicOracleNamespacedDomainCall(
      String name,
      String namespace,
      String pretty,
      Boolean exact,
      Boolean export,
      final ProgressResponseBody.ProgressListener progressListener,
      final ProgressRequestBody.ProgressRequestListener progressRequestListener)
      throws ApiException {
    Object localVarPostBody = null;

    // create path and map variables
    String localVarPath =
        "/apis/weblogic.oracle/"
            + DOMAIN_VERSION
            + "/namespaces/{namespace}/domains/{name}"
                .replaceAll("\\{" + "name" + "\\}", apiClient.escapeString(name))
                .replaceAll("\\{" + "namespace" + "\\}", apiClient.escapeString(namespace));

    List<Pair> localVarQueryParams = new ArrayList<Pair>();
    List<Pair> localVarCollectionQueryParams = new ArrayList<Pair>();
    if (pretty != null) {
      localVarQueryParams.addAll(apiClient.parameterToPair("pretty", pretty));
    }
    if (exact != null) {
      localVarQueryParams.addAll(apiClient.parameterToPair("exact", exact));
    }
    if (export != null) {
      localVarQueryParams.addAll(apiClient.parameterToPair("export", export));
    }

    Map<String, String> localVarHeaderParams = new HashMap<String, String>();

    Map<String, Object> localVarFormParams = new HashMap<String, Object>();

    final String[] localVarAccepts = {
      "application/json", "application/yaml", "application/vnd.kubernetes.protobuf"
    };
    final String localVarAccept = apiClient.selectHeaderAccept(localVarAccepts);
    if (localVarAccept != null) localVarHeaderParams.put("Accept", localVarAccept);

    final String[] localVarContentTypes = {"*/*"};
    final String localVarContentType = apiClient.selectHeaderContentType(localVarContentTypes);
    localVarHeaderParams.put("Content-Type", localVarContentType);

    if (progressListener != null) {
      apiClient
          .getHttpClient()
          .networkInterceptors()
          .add(
              chain -> {
                com.squareup.okhttp.Response originalResponse = chain.proceed(chain.request());
                return originalResponse
                    .newBuilder()
                    .body(new ProgressResponseBody(originalResponse.body(), progressListener))
                    .build();
              });
    }

    String[] localVarAuthNames = new String[] {"BearerToken"};
    return apiClient.buildCall(
        localVarPath,
        "GET",
        localVarQueryParams,
        localVarCollectionQueryParams,
        localVarPostBody,
        localVarHeaderParams,
        localVarFormParams,
        localVarAuthNames,
        progressRequestListener);
  }

  private com.squareup.okhttp.Call readWebLogicOracleNamespacedDomainValidateBeforeCall(
      String name,
      String namespace,
      String pretty,
      Boolean exact,
      Boolean export,
      final ProgressResponseBody.ProgressListener progressListener,
      final ProgressRequestBody.ProgressRequestListener progressRequestListener)
      throws ApiException {

    // verify the required parameter 'name' is set
    if (name == null) {
      throw new ApiException(
          "Missing the required parameter 'name' when calling readWebLogicOracleNamespacedDomain(Async)");
    }

    // verify the required parameter 'namespace' is set
    if (namespace == null) {
      throw new ApiException(
          "Missing the required parameter 'namespace' when calling readWebLogicOracleNamespacedDomain(Async)");
    }

    com.squareup.okhttp.Call call =
        readWebLogicOracleNamespacedDomainCall(
            name, namespace, pretty, exact, export, progressListener, progressRequestListener);
    return call;
  }

  /**
   * read the specified Domain.
   *
   * @param name name of the Domain (required)
   * @param namespace object name and auth scope, such as for teams and projects (required)
   * @param pretty If &#39;true&#39;, then the output is pretty printed. (optional)
   * @param exact Should the export be exact. Exact export maintains cluster-specific fields like
   *     &#39;Namespace&#39;. (optional)
   * @param export Should this value be exported. Export strips fields that a user can not specify.
   *     (optional)
   * @return Domain
   * @throws ApiException If fail to call the API, e.g. server error or cannot deserialize the
   *     response body
   */
  public Domain readWebLogicOracleNamespacedDomain(
      String name, String namespace, String pretty, Boolean exact, Boolean export)
      throws ApiException {
    ApiResponse<Domain> resp =
        readWebLogicOracleNamespacedDomainWithHttpInfo(name, namespace, pretty, exact, export);
    return resp.getData();
  }

  /**
   * read the specified Domain.
   *
   * @param name name of the Domain (required)
   * @param namespace object name and auth scope, such as for teams and projects (required)
   * @param pretty If &#39;true&#39;, then the output is pretty printed. (optional)
   * @param exact Should the export be exact. Exact export maintains cluster-specific fields like
   *     &#39;Namespace&#39;. (optional)
   * @param export Should this value be exported. Export strips fields that a user can not specify.
   *     (optional)
   * @return ApiResponse&lt;Domain&gt;
   * @throws ApiException If fail to call the API, e.g. server error or cannot deserialize the
   *     response body
   */
  public ApiResponse<Domain> readWebLogicOracleNamespacedDomainWithHttpInfo(
      String name, String namespace, String pretty, Boolean exact, Boolean export)
      throws ApiException {
    com.squareup.okhttp.Call call =
        readWebLogicOracleNamespacedDomainValidateBeforeCall(
            name, namespace, pretty, exact, export, null, null);
    Type localVarReturnType = new TypeToken<Domain>() {}.getType();
    return apiClient.execute(call, localVarReturnType);
  }

  /**
   * (asynchronously) read the specified Domain.
   *
   * @param name name of the Domain (required)
   * @param namespace object name and auth scope, such as for teams and projects (required)
   * @param pretty If &#39;true&#39;, then the output is pretty printed. (optional)
   * @param exact Should the export be exact. Exact export maintains cluster-specific fields like
   *     &#39;Namespace&#39;. (optional)
   * @param export Should this value be exported. Export strips fields that a user can not specify.
   *     (optional)
   * @param callback The callback to be executed when the API call finishes
   * @return The request call
   * @throws ApiException If fail to process the API call, e.g. serializing the request body object
   */
  public com.squareup.okhttp.Call readWebLogicOracleNamespacedDomainAsync(
      String name,
      String namespace,
      String pretty,
      Boolean exact,
      Boolean export,
      final ApiCallback<Domain> callback)
      throws ApiException {

    ProgressResponseBody.ProgressListener progressListener = null;
    ProgressRequestBody.ProgressRequestListener progressRequestListener = null;

    if (callback != null) {
      progressListener =
          (bytesRead, contentLength, done) ->
              callback.onDownloadProgress(bytesRead, contentLength, done);

      progressRequestListener =
          (bytesWritten, contentLength, done) ->
              callback.onUploadProgress(bytesWritten, contentLength, done);
    }

    com.squareup.okhttp.Call call =
        readWebLogicOracleNamespacedDomainValidateBeforeCall(
            name, namespace, pretty, exact, export, progressListener, progressRequestListener);
    Type localVarReturnType = new TypeToken<Domain>() {}.getType();
    apiClient.executeAsync(call, localVarReturnType, callback);
    return call;
  }

  /**
   * Build call for readWebLogicOracleNamespacedDomainScale.
   *
   * @param name name of the Scale (required)
   * @param namespace object name and auth scope, such as for teams and projects (required)
   * @param pretty If &#39;true&#39;, then the output is pretty printed. (optional)
   * @param progressListener Progress listener
   * @param progressRequestListener Progress request listener
   * @return Call to execute
   * @throws ApiException If fail to serialize the request body object
   */
  public com.squareup.okhttp.Call readWebLogicOracleNamespacedDomainScaleCall(
      String name,
      String namespace,
      String pretty,
      final ProgressResponseBody.ProgressListener progressListener,
      final ProgressRequestBody.ProgressRequestListener progressRequestListener)
      throws ApiException {
    Object localVarPostBody = null;

    // create path and map variables
    String localVarPath =
        "/apis/weblogic.oracle/"
            + DOMAIN_VERSION
            + "/namespaces/{namespace}/domains/{name}/scale"
                .replaceAll("\\{" + "name" + "\\}", apiClient.escapeString(name))
                .replaceAll("\\{" + "namespace" + "\\}", apiClient.escapeString(namespace));

    List<Pair> localVarQueryParams = new ArrayList<Pair>();
    List<Pair> localVarCollectionQueryParams = new ArrayList<Pair>();
    if (pretty != null) localVarQueryParams.addAll(apiClient.parameterToPair("pretty", pretty));

    Map<String, String> localVarHeaderParams = new HashMap<String, String>();

    Map<String, Object> localVarFormParams = new HashMap<String, Object>();

    final String[] localVarAccepts = {
      "application/json", "application/yaml", "application/vnd.kubernetes.protobuf"
    };
    final String localVarAccept = apiClient.selectHeaderAccept(localVarAccepts);
    if (localVarAccept != null) localVarHeaderParams.put("Accept", localVarAccept);

    final String[] localVarContentTypes = {"*/*"};
    final String localVarContentType = apiClient.selectHeaderContentType(localVarContentTypes);
    localVarHeaderParams.put("Content-Type", localVarContentType);

    if (progressListener != null) {
      apiClient
          .getHttpClient()
          .networkInterceptors()
          .add(
              chain -> {
                com.squareup.okhttp.Response originalResponse = chain.proceed(chain.request());
                return originalResponse
                    .newBuilder()
                    .body(new ProgressResponseBody(originalResponse.body(), progressListener))
                    .build();
              });
    }

    String[] localVarAuthNames = new String[] {"BearerToken"};
    return apiClient.buildCall(
        localVarPath,
        "GET",
        localVarQueryParams,
        localVarCollectionQueryParams,
        localVarPostBody,
        localVarHeaderParams,
        localVarFormParams,
        localVarAuthNames,
        progressRequestListener);
  }

  private com.squareup.okhttp.Call readWebLogicOracleNamespacedDomainScaleValidateBeforeCall(
      String name,
      String namespace,
      String pretty,
      final ProgressResponseBody.ProgressListener progressListener,
      final ProgressRequestBody.ProgressRequestListener progressRequestListener)
      throws ApiException {

    // verify the required parameter 'name' is set
    if (name == null) {
      throw new ApiException(
          "Missing the required parameter 'name' when calling readWebLogicOracleNamespacedDomainScale(Async)");
    }

    // verify the required parameter 'namespace' is set
    if (namespace == null) {
      throw new ApiException(
          "Missing the required parameter 'namespace' when calling readWebLogicOracleNamespacedDomainScale(Async)");
    }

    com.squareup.okhttp.Call call =
        readWebLogicOracleNamespacedDomainScaleCall(
            name, namespace, pretty, progressListener, progressRequestListener);
    return call;
  }

  /**
   * read scale of the specified Domain.
   *
   * @param name name of the Scale (required)
   * @param namespace object name and auth scope, such as for teams and projects (required)
   * @param pretty If &#39;true&#39;, then the output is pretty printed. (optional)
   * @return V1Scale
   * @throws ApiException If fail to call the API, e.g. server error or cannot deserialize the
   *     response body
   */
  public V1Scale readWebLogicOracleNamespacedDomainScale(
      String name, String namespace, String pretty) throws ApiException {
    ApiResponse<V1Scale> resp =
        readWebLogicOracleNamespacedDomainScaleWithHttpInfo(name, namespace, pretty);
    return resp.getData();
  }

  /**
   * read scale of the specified Domain.
   *
   * @param name name of the Scale (required)
   * @param namespace object name and auth scope, such as for teams and projects (required)
   * @param pretty If &#39;true&#39;, then the output is pretty printed. (optional)
   * @return ApiResponse&lt;V1Scale&gt;
   * @throws ApiException If fail to call the API, e.g. server error or cannot deserialize the
   *     response body
   */
  public ApiResponse<V1Scale> readWebLogicOracleNamespacedDomainScaleWithHttpInfo(
      String name, String namespace, String pretty) throws ApiException {
    com.squareup.okhttp.Call call =
        readWebLogicOracleNamespacedDomainScaleValidateBeforeCall(
            name, namespace, pretty, null, null);
    Type localVarReturnType = new TypeToken<V1Scale>() {}.getType();
    return apiClient.execute(call, localVarReturnType);
  }

  /**
   * (asynchronously) read scale of the specified Domain.
   *
   * @param name name of the Scale (required)
   * @param namespace object name and auth scope, such as for teams and projects (required)
   * @param pretty If &#39;true&#39;, then the output is pretty printed. (optional)
   * @param callback The callback to be executed when the API call finishes
   * @return The request call
   * @throws ApiException If fail to process the API call, e.g. serializing the request body object
   */
  public com.squareup.okhttp.Call readWebLogicOracleNamespacedDomainScaleAsync(
      String name, String namespace, String pretty, final ApiCallback<V1Scale> callback)
      throws ApiException {

    ProgressResponseBody.ProgressListener progressListener = null;
    ProgressRequestBody.ProgressRequestListener progressRequestListener = null;

    if (callback != null) {
      progressListener =
          (bytesRead, contentLength, done) ->
              callback.onDownloadProgress(bytesRead, contentLength, done);

      progressRequestListener =
          (bytesWritten, contentLength, done) ->
              callback.onUploadProgress(bytesWritten, contentLength, done);
    }

    com.squareup.okhttp.Call call =
        readWebLogicOracleNamespacedDomainScaleValidateBeforeCall(
            name, namespace, pretty, progressListener, progressRequestListener);
    Type localVarReturnType = new TypeToken<V1Scale>() {}.getType();
    apiClient.executeAsync(call, localVarReturnType, callback);
    return call;
  }

  /**
   * Build call for readWebLogicOracleNamespacedDomainStatus.
   *
   * @param name name of the Domain (required)
   * @param namespace object name and auth scope, such as for teams and projects (required)
   * @param pretty If &#39;true&#39;, then the output is pretty printed. (optional)
   * @param progressListener Progress listener
   * @param progressRequestListener Progress request listener
   * @return Call to execute
   * @throws ApiException If fail to serialize the request body object
   */
  public com.squareup.okhttp.Call readWebLogicOracleNamespacedDomainStatusCall(
      String name,
      String namespace,
      String pretty,
      final ProgressResponseBody.ProgressListener progressListener,
      final ProgressRequestBody.ProgressRequestListener progressRequestListener)
      throws ApiException {
    Object localVarPostBody = null;

    // create path and map variables
    String localVarPath =
        "/apis/weblogic.oracle/"
            + DOMAIN_VERSION
            + "/namespaces/{namespace}/domains/{name}/status"
                .replaceAll("\\{" + "name" + "\\}", apiClient.escapeString(name))
                .replaceAll("\\{" + "namespace" + "\\}", apiClient.escapeString(namespace));

    List<Pair> localVarQueryParams = new ArrayList<Pair>();
    List<Pair> localVarCollectionQueryParams = new ArrayList<Pair>();
    if (pretty != null) localVarQueryParams.addAll(apiClient.parameterToPair("pretty", pretty));

    Map<String, String> localVarHeaderParams = new HashMap<String, String>();

    Map<String, Object> localVarFormParams = new HashMap<String, Object>();

    final String[] localVarAccepts = {
      "application/json", "application/yaml", "application/vnd.kubernetes.protobuf"
    };
    final String localVarAccept = apiClient.selectHeaderAccept(localVarAccepts);
    if (localVarAccept != null) localVarHeaderParams.put("Accept", localVarAccept);

    final String[] localVarContentTypes = {"*/*"};
    final String localVarContentType = apiClient.selectHeaderContentType(localVarContentTypes);
    localVarHeaderParams.put("Content-Type", localVarContentType);

    if (progressListener != null) {
      apiClient
          .getHttpClient()
          .networkInterceptors()
          .add(
              chain -> {
                com.squareup.okhttp.Response originalResponse = chain.proceed(chain.request());
                return originalResponse
                    .newBuilder()
                    .body(new ProgressResponseBody(originalResponse.body(), progressListener))
                    .build();
              });
    }

    String[] localVarAuthNames = new String[] {"BearerToken"};
    return apiClient.buildCall(
        localVarPath,
        "GET",
        localVarQueryParams,
        localVarCollectionQueryParams,
        localVarPostBody,
        localVarHeaderParams,
        localVarFormParams,
        localVarAuthNames,
        progressRequestListener);
  }

  private com.squareup.okhttp.Call readWebLogicOracleNamespacedDomainStatusValidateBeforeCall(
      String name,
      String namespace,
      String pretty,
      final ProgressResponseBody.ProgressListener progressListener,
      final ProgressRequestBody.ProgressRequestListener progressRequestListener)
      throws ApiException {

    // verify the required parameter 'name' is set
    if (name == null) {
      throw new ApiException(
          "Missing the required parameter 'name' when calling readWebLogicOracleNamespacedDomainStatus(Async)");
    }

    // verify the required parameter 'namespace' is set
    if (namespace == null) {
      throw new ApiException(
          "Missing the required parameter 'namespace' when calling readWebLogicOracleNamespacedDomainStatus(Async)");
    }

    com.squareup.okhttp.Call call =
        readWebLogicOracleNamespacedDomainStatusCall(
            name, namespace, pretty, progressListener, progressRequestListener);
    return call;
  }

  /**
   * read status of the specified Domain.
   *
   * @param name name of the Domain (required)
   * @param namespace object name and auth scope, such as for teams and projects (required)
   * @param pretty If &#39;true&#39;, then the output is pretty printed. (optional)
   * @return Domain
   * @throws ApiException If fail to call the API, e.g. server error or cannot deserialize the
   *     response body
   */
  public Domain readWebLogicOracleNamespacedDomainStatus(
      String name, String namespace, String pretty) throws ApiException {
    ApiResponse<Domain> resp =
        readWebLogicOracleNamespacedDomainStatusWithHttpInfo(name, namespace, pretty);
    return resp.getData();
  }

  /**
   * read status of the specified Domain.
   *
   * @param name name of the Domain (required)
   * @param namespace object name and auth scope, such as for teams and projects (required)
   * @param pretty If &#39;true&#39;, then the output is pretty printed. (optional)
   * @return ApiResponse&lt;Domain&gt;
   * @throws ApiException If fail to call the API, e.g. server error or cannot deserialize the
   *     response body
   */
  public ApiResponse<Domain> readWebLogicOracleNamespacedDomainStatusWithHttpInfo(
      String name, String namespace, String pretty) throws ApiException {
    com.squareup.okhttp.Call call =
        readWebLogicOracleNamespacedDomainStatusValidateBeforeCall(
            name, namespace, pretty, null, null);
    Type localVarReturnType = new TypeToken<Domain>() {}.getType();
    return apiClient.execute(call, localVarReturnType);
  }

  /**
   * (asynchronously) read status of the specified Domain.
   *
   * @param name name of the Domain (required)
   * @param namespace object name and auth scope, such as for teams and projects (required)
   * @param pretty If &#39;true&#39;, then the output is pretty printed. (optional)
   * @param callback The callback to be executed when the API call finishes
   * @return The request call
   * @throws ApiException If fail to process the API call, e.g. serializing the request body object
   */
  public com.squareup.okhttp.Call readWebLogicOracleNamespacedDomainStatusAsync(
      String name, String namespace, String pretty, final ApiCallback<Domain> callback)
      throws ApiException {

    ProgressResponseBody.ProgressListener progressListener = null;
    ProgressRequestBody.ProgressRequestListener progressRequestListener = null;

    if (callback != null) {
      progressListener =
          (bytesRead, contentLength, done) ->
              callback.onDownloadProgress(bytesRead, contentLength, done);

      progressRequestListener =
          (bytesWritten, contentLength, done) ->
              callback.onUploadProgress(bytesWritten, contentLength, done);
    }

    com.squareup.okhttp.Call call =
        readWebLogicOracleNamespacedDomainStatusValidateBeforeCall(
            name, namespace, pretty, progressListener, progressRequestListener);
    Type localVarReturnType = new TypeToken<Domain>() {}.getType();
    apiClient.executeAsync(call, localVarReturnType, callback);
    return call;
  }

  /**
   * Build call for replaceWebLogicOracleNamespacedDomain.
   *
   * @param name name of the Domain (required)
   * @param namespace object name and auth scope, such as for teams and projects (required)
   * @param body (required)
   * @param pretty If &#39;true&#39;, then the output is pretty printed. (optional)
   * @param progressListener Progress listener
   * @param progressRequestListener Progress request listener
   * @return Call to execute
   * @throws ApiException If fail to serialize the request body object
   */
  public com.squareup.okhttp.Call replaceWebLogicOracleNamespacedDomainCall(
      String name,
      String namespace,
      Domain body,
      String pretty,
      final ProgressResponseBody.ProgressListener progressListener,
      final ProgressRequestBody.ProgressRequestListener progressRequestListener)
      throws ApiException {
    Object localVarPostBody = body;

    // create path and map variables
    String localVarPath =
        "/apis/weblogic.oracle/"
            + DOMAIN_VERSION
            + "/namespaces/{namespace}/domains/{name}"
                .replaceAll("\\{" + "name" + "\\}", apiClient.escapeString(name))
                .replaceAll("\\{" + "namespace" + "\\}", apiClient.escapeString(namespace));

    List<Pair> localVarQueryParams = new ArrayList<Pair>();
    List<Pair> localVarCollectionQueryParams = new ArrayList<Pair>();
    if (pretty != null) localVarQueryParams.addAll(apiClient.parameterToPair("pretty", pretty));

    Map<String, String> localVarHeaderParams = new HashMap<String, String>();

    Map<String, Object> localVarFormParams = new HashMap<String, Object>();

    final String[] localVarAccepts = {
      "application/json", "application/yaml", "application/vnd.kubernetes.protobuf"
    };
    final String localVarAccept = apiClient.selectHeaderAccept(localVarAccepts);
    if (localVarAccept != null) localVarHeaderParams.put("Accept", localVarAccept);

    final String[] localVarContentTypes = {"*/*"};
    final String localVarContentType = apiClient.selectHeaderContentType(localVarContentTypes);
    localVarHeaderParams.put("Content-Type", localVarContentType);

    if (progressListener != null) {
      apiClient
          .getHttpClient()
          .networkInterceptors()
          .add(
              chain -> {
                com.squareup.okhttp.Response originalResponse = chain.proceed(chain.request());
                return originalResponse
                    .newBuilder()
                    .body(new ProgressResponseBody(originalResponse.body(), progressListener))
                    .build();
              });
    }

    String[] localVarAuthNames = new String[] {"BearerToken"};
    return apiClient.buildCall(
        localVarPath,
        "PUT",
        localVarQueryParams,
        localVarCollectionQueryParams,
        localVarPostBody,
        localVarHeaderParams,
        localVarFormParams,
        localVarAuthNames,
        progressRequestListener);
  }

  private com.squareup.okhttp.Call replaceWebLogicOracleNamespacedDomainValidateBeforeCall(
      String name,
      String namespace,
      Domain body,
      String pretty,
      final ProgressResponseBody.ProgressListener progressListener,
      final ProgressRequestBody.ProgressRequestListener progressRequestListener)
      throws ApiException {

    // verify the required parameter 'name' is set
    if (name == null) {
      throw new ApiException(
          "Missing the required parameter 'name' when calling replaceWebLogicOracleNamespacedDomain(Async)");
    }

    // verify the required parameter 'namespace' is set
    if (namespace == null) {
      throw new ApiException(
          "Missing the required parameter 'namespace' when calling replaceWebLogicOracleNamespacedDomain(Async)");
    }

    // verify the required parameter 'body' is set
    if (body == null) {
      throw new ApiException(
          "Missing the required parameter 'body' when calling replaceWebLogicOracleNamespacedDomain(Async)");
    }

    com.squareup.okhttp.Call call =
        replaceWebLogicOracleNamespacedDomainCall(
            name, namespace, body, pretty, progressListener, progressRequestListener);
    return call;
  }

  /**
   * replace the specified Domain.
   *
   * @param name name of the Domain (required)
   * @param namespace object name and auth scope, such as for teams and projects (required)
   * @param body (required)
   * @param pretty If &#39;true&#39;, then the output is pretty printed. (optional)
   * @return Domain
   * @throws ApiException If fail to call the API, e.g. server error or cannot deserialize the
   *     response body
   */
  public Domain replaceWebLogicOracleNamespacedDomain(
      String name, String namespace, Domain body, String pretty) throws ApiException {
    ApiResponse<Domain> resp =
        replaceWebLogicOracleNamespacedDomainWithHttpInfo(name, namespace, body, pretty);
    return resp.getData();
  }

  /**
   * replace the specified Domain.
   *
   * @param name name of the Domain (required)
   * @param namespace object name and auth scope, such as for teams and projects (required)
   * @param body (required)
   * @param pretty If &#39;true&#39;, then the output is pretty printed. (optional)
   * @return ApiResponse&lt;Domain&gt;
   * @throws ApiException If fail to call the API, e.g. server error or cannot deserialize the
   *     response body
   */
  public ApiResponse<Domain> replaceWebLogicOracleNamespacedDomainWithHttpInfo(
      String name, String namespace, Domain body, String pretty) throws ApiException {
    com.squareup.okhttp.Call call =
        replaceWebLogicOracleNamespacedDomainValidateBeforeCall(
            name, namespace, body, pretty, null, null);
    Type localVarReturnType = new TypeToken<Domain>() {}.getType();
    return apiClient.execute(call, localVarReturnType);
  }

  /**
   * (asynchronously) replace the specified Domain.
   *
   * @param name name of the Domain (required)
   * @param namespace object name and auth scope, such as for teams and projects (required)
   * @param body (required)
   * @param pretty If &#39;true&#39;, then the output is pretty printed. (optional)
   * @param callback The callback to be executed when the API call finishes
   * @return The request call
   * @throws ApiException If fail to process the API call, e.g. serializing the request body object
   */
  public com.squareup.okhttp.Call replaceWebLogicOracleNamespacedDomainAsync(
      String name, String namespace, Domain body, String pretty, final ApiCallback<Domain> callback)
      throws ApiException {

    ProgressResponseBody.ProgressListener progressListener = null;
    ProgressRequestBody.ProgressRequestListener progressRequestListener = null;

    if (callback != null) {
      progressListener =
          (bytesRead, contentLength, done) ->
              callback.onDownloadProgress(bytesRead, contentLength, done);

      progressRequestListener =
          (bytesWritten, contentLength, done) ->
              callback.onUploadProgress(bytesWritten, contentLength, done);
    }

    com.squareup.okhttp.Call call =
        replaceWebLogicOracleNamespacedDomainValidateBeforeCall(
            name, namespace, body, pretty, progressListener, progressRequestListener);
    Type localVarReturnType = new TypeToken<Domain>() {}.getType();
    apiClient.executeAsync(call, localVarReturnType, callback);
    return call;
  }

  /**
   * Build call for replaceWebLogicOracleNamespacedDomainScale.
   *
   * @param name name of the Scale (required)
   * @param namespace object name and auth scope, such as for teams and projects (required)
   * @param body (required)
   * @param pretty If &#39;true&#39;, then the output is pretty printed. (optional)
   * @param progressListener Progress listener
   * @param progressRequestListener Progress request listener
   * @return Call to execute
   * @throws ApiException If fail to serialize the request body object
   */
  public com.squareup.okhttp.Call replaceWebLogicOracleNamespacedDomainScaleCall(
      String name,
      String namespace,
      V1Scale body,
      String pretty,
      final ProgressResponseBody.ProgressListener progressListener,
      final ProgressRequestBody.ProgressRequestListener progressRequestListener)
      throws ApiException {
    Object localVarPostBody = body;

    // create path and map variables
    String localVarPath =
        "/apis/weblogic.oracle/"
            + DOMAIN_VERSION
            + "/namespaces/{namespace}/domains/{name}/scale"
                .replaceAll("\\{" + "name" + "\\}", apiClient.escapeString(name))
                .replaceAll("\\{" + "namespace" + "\\}", apiClient.escapeString(namespace));

    List<Pair> localVarQueryParams = new ArrayList<Pair>();
    List<Pair> localVarCollectionQueryParams = new ArrayList<Pair>();
    if (pretty != null) localVarQueryParams.addAll(apiClient.parameterToPair("pretty", pretty));

    Map<String, String> localVarHeaderParams = new HashMap<String, String>();

    Map<String, Object> localVarFormParams = new HashMap<String, Object>();

    final String[] localVarAccepts = {
      "application/json", "application/yaml", "application/vnd.kubernetes.protobuf"
    };
    final String localVarAccept = apiClient.selectHeaderAccept(localVarAccepts);
    if (localVarAccept != null) localVarHeaderParams.put("Accept", localVarAccept);

    final String[] localVarContentTypes = {"*/*"};
    final String localVarContentType = apiClient.selectHeaderContentType(localVarContentTypes);
    localVarHeaderParams.put("Content-Type", localVarContentType);

    if (progressListener != null) {
      apiClient
          .getHttpClient()
          .networkInterceptors()
          .add(
              chain -> {
                com.squareup.okhttp.Response originalResponse = chain.proceed(chain.request());
                return originalResponse
                    .newBuilder()
                    .body(new ProgressResponseBody(originalResponse.body(), progressListener))
                    .build();
              });
    }

    String[] localVarAuthNames = new String[] {"BearerToken"};
    return apiClient.buildCall(
        localVarPath,
        "PUT",
        localVarQueryParams,
        localVarCollectionQueryParams,
        localVarPostBody,
        localVarHeaderParams,
        localVarFormParams,
        localVarAuthNames,
        progressRequestListener);
  }

  private com.squareup.okhttp.Call replaceWebLogicOracleNamespacedDomainScaleValidateBeforeCall(
      String name,
      String namespace,
      V1Scale body,
      String pretty,
      final ProgressResponseBody.ProgressListener progressListener,
      final ProgressRequestBody.ProgressRequestListener progressRequestListener)
      throws ApiException {

    // verify the required parameter 'name' is set
    if (name == null) {
      throw new ApiException(
          "Missing the required parameter 'name' when calling replaceWebLogicOracleNamespacedDomainScale(Async)");
    }

    // verify the required parameter 'namespace' is set
    if (namespace == null) {
      throw new ApiException(
          "Missing the required parameter 'namespace' when calling replaceWebLogicOracleNamespacedDomainScale(Async)");
    }

    // verify the required parameter 'body' is set
    if (body == null) {
      throw new ApiException(
          "Missing the required parameter 'body' when calling replaceWebLogicOracleNamespacedDomainScale(Async)");
    }

    com.squareup.okhttp.Call call =
        replaceWebLogicOracleNamespacedDomainScaleCall(
            name, namespace, body, pretty, progressListener, progressRequestListener);
    return call;
  }

  /**
   * replace scale of the specified Domain.
   *
   * @param name name of the Scale (required)
   * @param namespace object name and auth scope, such as for teams and projects (required)
   * @param body (required)
   * @param pretty If &#39;true&#39;, then the output is pretty printed. (optional)
   * @return V1Scale
   * @throws ApiException If fail to call the API, e.g. server error or cannot deserialize the
   *     response body
   */
  public V1Scale replaceWebLogicOracleNamespacedDomainScale(
      String name, String namespace, V1Scale body, String pretty) throws ApiException {
    ApiResponse<V1Scale> resp =
        replaceWebLogicOracleNamespacedDomainScaleWithHttpInfo(name, namespace, body, pretty);
    return resp.getData();
  }

  /**
   * replace scale of the specified Domain.
   *
   * @param name name of the Scale (required)
   * @param namespace object name and auth scope, such as for teams and projects (required)
   * @param body (required)
   * @param pretty If &#39;true&#39;, then the output is pretty printed. (optional)
   * @return ApiResponse&lt;V1Scale&gt;
   * @throws ApiException If fail to call the API, e.g. server error or cannot deserialize the
   *     response body
   */
  public ApiResponse<V1Scale> replaceWebLogicOracleNamespacedDomainScaleWithHttpInfo(
      String name, String namespace, V1Scale body, String pretty) throws ApiException {
    com.squareup.okhttp.Call call =
        replaceWebLogicOracleNamespacedDomainScaleValidateBeforeCall(
            name, namespace, body, pretty, null, null);
    Type localVarReturnType = new TypeToken<V1Scale>() {}.getType();
    return apiClient.execute(call, localVarReturnType);
  }

  /**
   * (asynchronously) replace scale of the specified Domain.
   *
   * @param name name of the Scale (required)
   * @param namespace object name and auth scope, such as for teams and projects (required)
   * @param body (required)
   * @param pretty If &#39;true&#39;, then the output is pretty printed. (optional)
   * @param callback The callback to be executed when the API call finishes
   * @return The request call
   * @throws ApiException If fail to process the API call, e.g. serializing the request body object
   */
  public com.squareup.okhttp.Call replaceWebLogicOracleNamespacedDomainScaleAsync(
      String name,
      String namespace,
      V1Scale body,
      String pretty,
      final ApiCallback<V1Scale> callback)
      throws ApiException {

    ProgressResponseBody.ProgressListener progressListener = null;
    ProgressRequestBody.ProgressRequestListener progressRequestListener = null;

    if (callback != null) {
      progressListener =
          (bytesRead, contentLength, done) ->
              callback.onDownloadProgress(bytesRead, contentLength, done);

      progressRequestListener =
          (bytesWritten, contentLength, done) ->
              callback.onUploadProgress(bytesWritten, contentLength, done);
    }

    com.squareup.okhttp.Call call =
        replaceWebLogicOracleNamespacedDomainScaleValidateBeforeCall(
            name, namespace, body, pretty, progressListener, progressRequestListener);
    Type localVarReturnType = new TypeToken<V1Scale>() {}.getType();
    apiClient.executeAsync(call, localVarReturnType, callback);
    return call;
  }

  /**
   * Build call for replaceWebLogicOracleNamespacedDomainStatus.
   *
   * @param name name of the Domain (required)
   * @param namespace object name and auth scope, such as for teams and projects (required)
   * @param body (required)
   * @param pretty If &#39;true&#39;, then the output is pretty printed. (optional)
   * @param progressListener Progress listener
   * @param progressRequestListener Progress request listener
   * @return Call to execute
   * @throws ApiException If fail to serialize the request body object
   */
  public com.squareup.okhttp.Call replaceWebLogicOracleNamespacedDomainStatusCall(
      String name,
      String namespace,
      Domain body,
      String pretty,
      final ProgressResponseBody.ProgressListener progressListener,
      final ProgressRequestBody.ProgressRequestListener progressRequestListener)
      throws ApiException {
    Object localVarPostBody = body;

    // create path and map variables
    String localVarPath =
        "/apis/weblogic.oracle/"
            + DOMAIN_VERSION
            + "/namespaces/{namespace}/domains/{name}/status"
                .replaceAll("\\{" + "name" + "\\}", apiClient.escapeString(name))
                .replaceAll("\\{" + "namespace" + "\\}", apiClient.escapeString(namespace));

    List<Pair> localVarQueryParams = new ArrayList<Pair>();
    List<Pair> localVarCollectionQueryParams = new ArrayList<Pair>();
    if (pretty != null) localVarQueryParams.addAll(apiClient.parameterToPair("pretty", pretty));

    Map<String, String> localVarHeaderParams = new HashMap<String, String>();

    Map<String, Object> localVarFormParams = new HashMap<String, Object>();

    final String[] localVarAccepts = {
      "application/json", "application/yaml", "application/vnd.kubernetes.protobuf"
    };
    final String localVarAccept = apiClient.selectHeaderAccept(localVarAccepts);
    if (localVarAccept != null) localVarHeaderParams.put("Accept", localVarAccept);

    final String[] localVarContentTypes = {"*/*"};
    final String localVarContentType = apiClient.selectHeaderContentType(localVarContentTypes);
    localVarHeaderParams.put("Content-Type", localVarContentType);

    if (progressListener != null) {
      apiClient
          .getHttpClient()
          .networkInterceptors()
          .add(
              chain -> {
                com.squareup.okhttp.Response originalResponse = chain.proceed(chain.request());
                return originalResponse
                    .newBuilder()
                    .body(new ProgressResponseBody(originalResponse.body(), progressListener))
                    .build();
              });
    }

    String[] localVarAuthNames = new String[] {"BearerToken"};
    return apiClient.buildCall(
        localVarPath,
        "PUT",
        localVarQueryParams,
        localVarCollectionQueryParams,
        localVarPostBody,
        localVarHeaderParams,
        localVarFormParams,
        localVarAuthNames,
        progressRequestListener);
  }

  private com.squareup.okhttp.Call replaceWebLogicOracleNamespacedDomainStatusValidateBeforeCall(
      String name,
      String namespace,
      Domain body,
      String pretty,
      final ProgressResponseBody.ProgressListener progressListener,
      final ProgressRequestBody.ProgressRequestListener progressRequestListener)
      throws ApiException {

    // verify the required parameter 'name' is set
    if (name == null) {
      throw new ApiException(
          "Missing the required parameter 'name' when calling replaceWebLogicOracleNamespacedDomainStatus(Async)");
    }

    // verify the required parameter 'namespace' is set
    if (namespace == null) {
      throw new ApiException(
          "Missing the required parameter 'namespace' when calling replaceWebLogicOracleNamespacedDomainStatus(Async)");
    }

    // verify the required parameter 'body' is set
    if (body == null) {
      throw new ApiException(
          "Missing the required parameter 'body' when calling replaceWebLogicOracleNamespacedDomainStatus(Async)");
    }

    com.squareup.okhttp.Call call =
        replaceWebLogicOracleNamespacedDomainStatusCall(
            name, namespace, body, pretty, progressListener, progressRequestListener);
    return call;
  }

  /**
   * replace status of the specified Domain.
   *
   * @param name name of the Domain (required)
   * @param namespace object name and auth scope, such as for teams and projects (required)
   * @param body (required)
   * @param pretty If &#39;true&#39;, then the output is pretty printed. (optional)
   * @return Domain
   * @throws ApiException If fail to call the API, e.g. server error or cannot deserialize the
   *     response body
   */
  public Domain replaceWebLogicOracleNamespacedDomainStatus(
      String name, String namespace, Domain body, String pretty) throws ApiException {
    ApiResponse<Domain> resp =
        replaceWebLogicOracleNamespacedDomainStatusWithHttpInfo(name, namespace, body, pretty);
    return resp.getData();
  }

  /**
   * replace status of the specified Domain.
   *
   * @param name name of the Domain (required)
   * @param namespace object name and auth scope, such as for teams and projects (required)
   * @param body (required)
   * @param pretty If &#39;true&#39;, then the output is pretty printed. (optional)
   * @return ApiResponse&lt;Domain&gt;
   * @throws ApiException If fail to call the API, e.g. server error or cannot deserialize the
   *     response body
   */
  public ApiResponse<Domain> replaceWebLogicOracleNamespacedDomainStatusWithHttpInfo(
      String name, String namespace, Domain body, String pretty) throws ApiException {
    com.squareup.okhttp.Call call =
        replaceWebLogicOracleNamespacedDomainStatusValidateBeforeCall(
            name, namespace, body, pretty, null, null);
    Type localVarReturnType = new TypeToken<Domain>() {}.getType();
    return apiClient.execute(call, localVarReturnType);
  }

  /**
   * (asynchronously) replace status of the specified Domain.
   *
   * @param name name of the Domain (required)
   * @param namespace object name and auth scope, such as for teams and projects (required)
   * @param body (required)
   * @param pretty If &#39;true&#39;, then the output is pretty printed. (optional)
   * @param callback The callback to be executed when the API call finishes
   * @return The request call
   * @throws ApiException If fail to process the API call, e.g. serializing the request body object
   */
  public com.squareup.okhttp.Call replaceWebLogicOracleNamespacedDomainStatusAsync(
      String name, String namespace, Domain body, String pretty, final ApiCallback<Domain> callback)
      throws ApiException {

    ProgressResponseBody.ProgressListener progressListener = null;
    ProgressRequestBody.ProgressRequestListener progressRequestListener = null;

    if (callback != null) {
      progressListener =
          (bytesRead, contentLength, done) ->
              callback.onDownloadProgress(bytesRead, contentLength, done);

      progressRequestListener =
          (bytesWritten, contentLength, done) ->
              callback.onUploadProgress(bytesWritten, contentLength, done);
    }

    com.squareup.okhttp.Call call =
        replaceWebLogicOracleNamespacedDomainStatusValidateBeforeCall(
            name, namespace, body, pretty, progressListener, progressRequestListener);
    Type localVarReturnType = new TypeToken<Domain>() {}.getType();
    apiClient.executeAsync(call, localVarReturnType, callback);
    return call;
  }

  /**
   * Build call for watchWebLogicOracleDomainListForAllNamespaces.
   *
   * @param _continue The continue option should be set when retrieving more results from the
   *     server. Since this value is server defined, clients may only use the continue value from a
   *     previous query result with identical query parameters (except for the value of continue)
   *     and the server may reject a continue value it does not recognize. If the specified continue
   *     value is no longer valid whether due to expiration (generally five to fifteen minutes) or a
   *     configuration change on the server the server will respond with a 410 ResourceExpired error
   *     indicating the client must restart their list without the continue field. This field is not
   *     supported when watch is true. Clients may start a watch from the last resourceVersion value
   *     returned by the server and not miss any modifications. (optional)
   * @param fieldSelector A selector to restrict the list of returned objects by their fields.
   *     Defaults to everything. (optional)
   * @param includeUninitialized If true, partially initialized resources are included in the
   *     response. (optional)
   * @param labelSelector A selector to restrict the list of returned objects by their labels.
   *     Defaults to everything. (optional)
   * @param limit limit is a maximum number of responses to return for a list call. If more items
   *     exist, the server will set the &#x60;continue&#x60; field on the list metadata to a value
   *     that can be used with the same initial query to retrieve the next set of results. Setting a
   *     limit may return fewer than the requested amount of items (up to zero items) in the event
   *     all requested objects are filtered out and clients should only use the presence of the
   *     continue field to determine whether more results are available. Servers may choose not to
   *     support the limit argument and will return all of the available results. If limit is
   *     specified and the continue field is empty, clients may assume that no more results are
   *     available. This field is not supported if watch is true. The server guarantees that the
   *     objects returned when using continue will be identical to issuing a single list call
   *     without a limit - that is, no objects created, modified, or deleted after the first request
   *     is issued will be included in any subsequent continued requests. This is sometimes referred
   *     to as a consistent snapshot, and ensures that a client that is using limit to receive
   *     smaller chunks of a very large result can ensure they see all possible objects. If objects
   *     are updated during a chunked list the version of the object that was present at the time
   *     the first list result was calculated is returned. (optional)
   * @param pretty If &#39;true&#39;, then the output is pretty printed. (optional)
   * @param resourceVersion When specified with a watch call, shows changes that occur after that
   *     particular version of a resource. Defaults to changes from the beginning of history. When
   *     specified for list: - if unset, then the result is returned from remote storage based on
   *     quorum-read flag; - if it&#39;s 0, then we simply return what we currently have in cache,
   *     no guarantee; - if set to non zero, then the result is at least as fresh as given rv.
   *     (optional)
   * @param timeoutSeconds Timeout for the list/watch call. (optional)
   * @param watch Watch for changes to the described resources and return them as a stream of add,
   *     update, and remove notifications. Specify resourceVersion. (optional)
   * @param progressListener Progress listener
   * @param progressRequestListener Progress request listener
   * @return Call to execute
   * @throws ApiException If fail to serialize the request body object
   */
  public com.squareup.okhttp.Call watchWebLogicOracleDomainListForAllNamespacesCall(
      String _continue,
      String fieldSelector,
      Boolean includeUninitialized,
      String labelSelector,
      Integer limit,
      String pretty,
      String resourceVersion,
      Integer timeoutSeconds,
      Boolean watch,
      final ProgressResponseBody.ProgressListener progressListener,
      final ProgressRequestBody.ProgressRequestListener progressRequestListener)
      throws ApiException {
    Object localVarPostBody = null;

    // create path and map variables
    String localVarPath = "/apis/weblogic.oracle/" + DOMAIN_VERSION + "/watch/domains";

    List<Pair> localVarQueryParams = new ArrayList<Pair>();
    List<Pair> localVarCollectionQueryParams = new ArrayList<Pair>();
    if (_continue != null) {
      localVarQueryParams.addAll(apiClient.parameterToPair("continue", _continue));
    }
    if (fieldSelector != null) {
      localVarQueryParams.addAll(apiClient.parameterToPair("fieldSelector", fieldSelector));
    }
    if (includeUninitialized != null) {
      localVarQueryParams.addAll(
          apiClient.parameterToPair("includeUninitialized", includeUninitialized));
    }
    if (labelSelector != null) {
      localVarQueryParams.addAll(apiClient.parameterToPair("labelSelector", labelSelector));
    }
    if (limit != null) {
      localVarQueryParams.addAll(apiClient.parameterToPair("limit", limit));
    }
    if (pretty != null) {
      localVarQueryParams.addAll(apiClient.parameterToPair("pretty", pretty));
    }
    if (resourceVersion != null) {
      localVarQueryParams.addAll(apiClient.parameterToPair("resourceVersion", resourceVersion));
    }
    if (timeoutSeconds != null) {
      localVarQueryParams.addAll(apiClient.parameterToPair("timeoutSeconds", timeoutSeconds));
    }
    if (watch != null) {
      localVarQueryParams.addAll(apiClient.parameterToPair("watch", watch));
    }

    Map<String, String> localVarHeaderParams = new HashMap<String, String>();

    Map<String, Object> localVarFormParams = new HashMap<String, Object>();

    final String[] localVarAccepts = {
      "application/json",
      "application/yaml",
      "application/vnd.kubernetes.protobuf",
      "application/json;stream=watch",
      "application/vnd.kubernetes.protobuf;stream=watch"
    };
    final String localVarAccept = apiClient.selectHeaderAccept(localVarAccepts);
    if (localVarAccept != null) localVarHeaderParams.put("Accept", localVarAccept);

    final String[] localVarContentTypes = {"*/*"};
    final String localVarContentType = apiClient.selectHeaderContentType(localVarContentTypes);
    localVarHeaderParams.put("Content-Type", localVarContentType);

    if (progressListener != null) {
      apiClient
          .getHttpClient()
          .networkInterceptors()
          .add(
              chain -> {
                com.squareup.okhttp.Response originalResponse = chain.proceed(chain.request());
                return originalResponse
                    .newBuilder()
                    .body(new ProgressResponseBody(originalResponse.body(), progressListener))
                    .build();
              });
    }

    String[] localVarAuthNames = new String[] {"BearerToken"};
    return apiClient.buildCall(
        localVarPath,
        "GET",
        localVarQueryParams,
        localVarCollectionQueryParams,
        localVarPostBody,
        localVarHeaderParams,
        localVarFormParams,
        localVarAuthNames,
        progressRequestListener);
  }

  private com.squareup.okhttp.Call watchWebLogicOracleDomainListForAllNamespacesValidateBeforeCall(
      String _continue,
      String fieldSelector,
      Boolean includeUninitialized,
      String labelSelector,
      Integer limit,
      String pretty,
      String resourceVersion,
      Integer timeoutSeconds,
      Boolean watch,
      final ProgressResponseBody.ProgressListener progressListener,
      final ProgressRequestBody.ProgressRequestListener progressRequestListener)
      throws ApiException {

    com.squareup.okhttp.Call call =
        watchWebLogicOracleDomainListForAllNamespacesCall(
            _continue,
            fieldSelector,
            includeUninitialized,
            labelSelector,
            limit,
            pretty,
            resourceVersion,
            timeoutSeconds,
            watch,
            progressListener,
            progressRequestListener);
    return call;
  }

  /**
   * watch individual changes to a list of Domain.
   *
   * @param _continue The continue option should be set when retrieving more results from the
   *     server. Since this value is server defined, clients may only use the continue value from a
   *     previous query result with identical query parameters (except for the value of continue)
   *     and the server may reject a continue value it does not recognize. If the specified continue
   *     value is no longer valid whether due to expiration (generally five to fifteen minutes) or a
   *     configuration change on the server the server will respond with a 410 ResourceExpired error
   *     indicating the client must restart their list without the continue field. This field is not
   *     supported when watch is true. Clients may start a watch from the last resourceVersion value
   *     returned by the server and not miss any modifications. (optional)
   * @param fieldSelector A selector to restrict the list of returned objects by their fields.
   *     Defaults to everything. (optional)
   * @param includeUninitialized If true, partially initialized resources are included in the
   *     response. (optional)
   * @param labelSelector A selector to restrict the list of returned objects by their labels.
   *     Defaults to everything. (optional)
   * @param limit limit is a maximum number of responses to return for a list call. If more items
   *     exist, the server will set the &#x60;continue&#x60; field on the list metadata to a value
   *     that can be used with the same initial query to retrieve the next set of results. Setting a
   *     limit may return fewer than the requested amount of items (up to zero items) in the event
   *     all requested objects are filtered out and clients should only use the presence of the
   *     continue field to determine whether more results are available. Servers may choose not to
   *     support the limit argument and will return all of the available results. If limit is
   *     specified and the continue field is empty, clients may assume that no more results are
   *     available. This field is not supported if watch is true. The server guarantees that the
   *     objects returned when using continue will be identical to issuing a single list call
   *     without a limit - that is, no objects created, modified, or deleted after the first request
   *     is issued will be included in any subsequent continued requests. This is sometimes referred
   *     to as a consistent snapshot, and ensures that a client that is using limit to receive
   *     smaller chunks of a very large result can ensure they see all possible objects. If objects
   *     are updated during a chunked list the version of the object that was present at the time
   *     the first list result was calculated is returned. (optional)
   * @param pretty If &#39;true&#39;, then the output is pretty printed. (optional)
   * @param resourceVersion When specified with a watch call, shows changes that occur after that
   *     particular version of a resource. Defaults to changes from the beginning of history. When
   *     specified for list: - if unset, then the result is returned from remote storage based on
   *     quorum-read flag; - if it&#39;s 0, then we simply return what we currently have in cache,
   *     no guarantee; - if set to non zero, then the result is at least as fresh as given rv.
   *     (optional)
   * @param timeoutSeconds Timeout for the list/watch call. (optional)
   * @param watch Watch for changes to the described resources and return them as a stream of add,
   *     update, and remove notifications. Specify resourceVersion. (optional)
   * @return V1WatchEvent
   * @throws ApiException If fail to call the API, e.g. server error or cannot deserialize the
   *     response body
   */
  public V1WatchEvent watchWebLogicOracleDomainListForAllNamespaces(
      String _continue,
      String fieldSelector,
      Boolean includeUninitialized,
      String labelSelector,
      Integer limit,
      String pretty,
      String resourceVersion,
      Integer timeoutSeconds,
      Boolean watch)
      throws ApiException {
    ApiResponse<V1WatchEvent> resp =
        watchWebLogicOracleDomainListForAllNamespacesWithHttpInfo(
            _continue,
            fieldSelector,
            includeUninitialized,
            labelSelector,
            limit,
            pretty,
            resourceVersion,
            timeoutSeconds,
            watch);
    return resp.getData();
  }

  /**
   * watch individual changes to a list of Domain.
   *
   * @param _continue The continue option should be set when retrieving more results from the
   *     server. Since this value is server defined, clients may only use the continue value from a
   *     previous query result with identical query parameters (except for the value of continue)
   *     and the server may reject a continue value it does not recognize. If the specified continue
   *     value is no longer valid whether due to expiration (generally five to fifteen minutes) or a
   *     configuration change on the server the server will respond with a 410 ResourceExpired error
   *     indicating the client must restart their list without the continue field. This field is not
   *     supported when watch is true. Clients may start a watch from the last resourceVersion value
   *     returned by the server and not miss any modifications. (optional)
   * @param fieldSelector A selector to restrict the list of returned objects by their fields.
   *     Defaults to everything. (optional)
   * @param includeUninitialized If true, partially initialized resources are included in the
   *     response. (optional)
   * @param labelSelector A selector to restrict the list of returned objects by their labels.
   *     Defaults to everything. (optional)
   * @param limit limit is a maximum number of responses to return for a list call. If more items
   *     exist, the server will set the &#x60;continue&#x60; field on the list metadata to a value
   *     that can be used with the same initial query to retrieve the next set of results. Setting a
   *     limit may return fewer than the requested amount of items (up to zero items) in the event
   *     all requested objects are filtered out and clients should only use the presence of the
   *     continue field to determine whether more results are available. Servers may choose not to
   *     support the limit argument and will return all of the available results. If limit is
   *     specified and the continue field is empty, clients may assume that no more results are
   *     available. This field is not supported if watch is true. The server guarantees that the
   *     objects returned when using continue will be identical to issuing a single list call
   *     without a limit - that is, no objects created, modified, or deleted after the first request
   *     is issued will be included in any subsequent continued requests. This is sometimes referred
   *     to as a consistent snapshot, and ensures that a client that is using limit to receive
   *     smaller chunks of a very large result can ensure they see all possible objects. If objects
   *     are updated during a chunked list the version of the object that was present at the time
   *     the first list result was calculated is returned. (optional)
   * @param pretty If &#39;true&#39;, then the output is pretty printed. (optional)
   * @param resourceVersion When specified with a watch call, shows changes that occur after that
   *     particular version of a resource. Defaults to changes from the beginning of history. When
   *     specified for list: - if unset, then the result is returned from remote storage based on
   *     quorum-read flag; - if it&#39;s 0, then we simply return what we currently have in cache,
   *     no guarantee; - if set to non zero, then the result is at least as fresh as given rv.
   *     (optional)
   * @param timeoutSeconds Timeout for the list/watch call. (optional)
   * @param watch Watch for changes to the described resources and return them as a stream of add,
   *     update, and remove notifications. Specify resourceVersion. (optional)
   * @return ApiResponse&lt;V1WatchEvent&gt;
   * @throws ApiException If fail to call the API, e.g. server error or cannot deserialize the
   *     response body
   */
  public ApiResponse<V1WatchEvent> watchWebLogicOracleDomainListForAllNamespacesWithHttpInfo(
      String _continue,
      String fieldSelector,
      Boolean includeUninitialized,
      String labelSelector,
      Integer limit,
      String pretty,
      String resourceVersion,
      Integer timeoutSeconds,
      Boolean watch)
      throws ApiException {
    com.squareup.okhttp.Call call =
        watchWebLogicOracleDomainListForAllNamespacesValidateBeforeCall(
            _continue,
            fieldSelector,
            includeUninitialized,
            labelSelector,
            limit,
            pretty,
            resourceVersion,
            timeoutSeconds,
            watch,
            null,
            null);
    Type localVarReturnType = new TypeToken<V1WatchEvent>() {}.getType();
    return apiClient.execute(call, localVarReturnType);
  }

  /**
   * (asynchronously) watch individual changes to a list of Domain.
   *
   * @param _continue The continue option should be set when retrieving more results from the
   *     server. Since this value is server defined, clients may only use the continue value from a
   *     previous query result with identical query parameters (except for the value of continue)
   *     and the server may reject a continue value it does not recognize. If the specified continue
   *     value is no longer valid whether due to expiration (generally five to fifteen minutes) or a
   *     configuration change on the server the server will respond with a 410 ResourceExpired error
   *     indicating the client must restart their list without the continue field. This field is not
   *     supported when watch is true. Clients may start a watch from the last resourceVersion value
   *     returned by the server and not miss any modifications. (optional)
   * @param fieldSelector A selector to restrict the list of returned objects by their fields.
   *     Defaults to everything. (optional)
   * @param includeUninitialized If true, partially initialized resources are included in the
   *     response. (optional)
   * @param labelSelector A selector to restrict the list of returned objects by their labels.
   *     Defaults to everything. (optional)
   * @param limit limit is a maximum number of responses to return for a list call. If more items
   *     exist, the server will set the &#x60;continue&#x60; field on the list metadata to a value
   *     that can be used with the same initial query to retrieve the next set of results. Setting a
   *     limit may return fewer than the requested amount of items (up to zero items) in the event
   *     all requested objects are filtered out and clients should only use the presence of the
   *     continue field to determine whether more results are available. Servers may choose not to
   *     support the limit argument and will return all of the available results. If limit is
   *     specified and the continue field is empty, clients may assume that no more results are
   *     available. This field is not supported if watch is true. The server guarantees that the
   *     objects returned when using continue will be identical to issuing a single list call
   *     without a limit - that is, no objects created, modified, or deleted after the first request
   *     is issued will be included in any subsequent continued requests. This is sometimes referred
   *     to as a consistent snapshot, and ensures that a client that is using limit to receive
   *     smaller chunks of a very large result can ensure they see all possible objects. If objects
   *     are updated during a chunked list the version of the object that was present at the time
   *     the first list result was calculated is returned. (optional)
   * @param pretty If &#39;true&#39;, then the output is pretty printed. (optional)
   * @param resourceVersion When specified with a watch call, shows changes that occur after that
   *     particular version of a resource. Defaults to changes from the beginning of history. When
   *     specified for list: - if unset, then the result is returned from remote storage based on
   *     quorum-read flag; - if it&#39;s 0, then we simply return what we currently have in cache,
   *     no guarantee; - if set to non zero, then the result is at least as fresh as given rv.
   *     (optional)
   * @param timeoutSeconds Timeout for the list/watch call. (optional)
   * @param watch Watch for changes to the described resources and return them as a stream of add,
   *     update, and remove notifications. Specify resourceVersion. (optional)
   * @param callback The callback to be executed when the API call finishes
   * @return The request call
   * @throws ApiException If fail to process the API call, e.g. serializing the request body object
   */
  public com.squareup.okhttp.Call watchWebLogicOracleDomainListForAllNamespacesAsync(
      String _continue,
      String fieldSelector,
      Boolean includeUninitialized,
      String labelSelector,
      Integer limit,
      String pretty,
      String resourceVersion,
      Integer timeoutSeconds,
      Boolean watch,
      final ApiCallback<V1WatchEvent> callback)
      throws ApiException {

    ProgressResponseBody.ProgressListener progressListener = null;
    ProgressRequestBody.ProgressRequestListener progressRequestListener = null;

    if (callback != null) {
      progressListener =
          (bytesRead, contentLength, done) ->
              callback.onDownloadProgress(bytesRead, contentLength, done);

      progressRequestListener =
          (bytesWritten, contentLength, done) ->
              callback.onUploadProgress(bytesWritten, contentLength, done);
    }

    com.squareup.okhttp.Call call =
        watchWebLogicOracleDomainListForAllNamespacesValidateBeforeCall(
            _continue,
            fieldSelector,
            includeUninitialized,
            labelSelector,
            limit,
            pretty,
            resourceVersion,
            timeoutSeconds,
            watch,
            progressListener,
            progressRequestListener);
    Type localVarReturnType = new TypeToken<V1WatchEvent>() {}.getType();
    apiClient.executeAsync(call, localVarReturnType, callback);
    return call;
  }

  /**
   * Build call for watchWebLogicOracleNamespacedDomain.
   *
   * @param name name of the Pod (required)
   * @param namespace object name and auth scope, such as for teams and projects (required)
   * @param _continue The continue option should be set when retrieving more results from the
   *     server. Since this value is server defined, clients may only use the continue value from a
   *     previous query result with identical query parameters (except for the value of continue)
   *     and the server may reject a continue value it does not recognize. If the specified continue
   *     value is no longer valid whether due to expiration (generally five to fifteen minutes) or a
   *     configuration change on the server the server will respond with a 410 ResourceExpired error
   *     indicating the client must restart their list without the continue field. This field is not
   *     supported when watch is true. Clients may start a watch from the last resourceVersion value
   *     returned by the server and not miss any modifications. (optional)
   * @param fieldSelector A selector to restrict the list of returned objects by their fields.
   *     Defaults to everything. (optional)
   * @param includeUninitialized If true, partially initialized resources are included in the
   *     response. (optional)
   * @param labelSelector A selector to restrict the list of returned objects by their labels.
   *     Defaults to everything. (optional)
   * @param limit limit is a maximum number of responses to return for a list call. If more items
   *     exist, the server will set the &#x60;continue&#x60; field on the list metadata to a value
   *     that can be used with the same initial query to retrieve the next set of results. Setting a
   *     limit may return fewer than the requested amount of items (up to zero items) in the event
   *     all requested objects are filtered out and clients should only use the presence of the
   *     continue field to determine whether more results are available. Servers may choose not to
   *     support the limit argument and will return all of the available results. If limit is
   *     specified and the continue field is empty, clients may assume that no more results are
   *     available. This field is not supported if watch is true. The server guarantees that the
   *     objects returned when using continue will be identical to issuing a single list call
   *     without a limit - that is, no objects created, modified, or deleted after the first request
   *     is issued will be included in any subsequent continued requests. This is sometimes referred
   *     to as a consistent snapshot, and ensures that a client that is using limit to receive
   *     smaller chunks of a very large result can ensure they see all possible objects. If objects
   *     are updated during a chunked list the version of the object that was present at the time
   *     the first list result was calculated is returned. (optional)
   * @param pretty If &#39;true&#39;, then the output is pretty printed. (optional)
   * @param resourceVersion When specified with a watch call, shows changes that occur after that
   *     particular version of a resource. Defaults to changes from the beginning of history. When
   *     specified for list: - if unset, then the result is returned from remote storage based on
   *     quorum-read flag; - if it&#39;s 0, then we simply return what we currently have in cache,
   *     no guarantee; - if set to non zero, then the result is at least as fresh as given rv.
   *     (optional)
   * @param timeoutSeconds Timeout for the list/watch call. (optional)
   * @param watch Watch for changes to the described resources and return them as a stream of add,
   *     update, and remove notifications. Specify resourceVersion. (optional)
   * @param progressListener Progress listener
   * @param progressRequestListener Progress request listener
   * @return Call to execute
   * @throws ApiException If fail to serialize the request body object
   */
  public com.squareup.okhttp.Call watchWebLogicOracleNamespacedDomainCall(
      String name,
      String namespace,
      String _continue,
      String fieldSelector,
      Boolean includeUninitialized,
      String labelSelector,
      Integer limit,
      String pretty,
      String resourceVersion,
      Integer timeoutSeconds,
      Boolean watch,
      final ProgressResponseBody.ProgressListener progressListener,
      final ProgressRequestBody.ProgressRequestListener progressRequestListener)
      throws ApiException {
    Object localVarPostBody = null;

    // create path and map variables
    String localVarPath =
        "/apis/weblogic.oracle/"
            + DOMAIN_VERSION
            + "/watch/namespaces/{namespace}/domains/{name}"
                .replaceAll("\\{" + "name" + "\\}", apiClient.escapeString(name))
                .replaceAll("\\{" + "namespace" + "\\}", apiClient.escapeString(namespace));

    List<Pair> localVarQueryParams = new ArrayList<Pair>();
    List<Pair> localVarCollectionQueryParams = new ArrayList<Pair>();
    if (_continue != null) {
      localVarQueryParams.addAll(apiClient.parameterToPair("continue", _continue));
    }
    if (fieldSelector != null) {
      localVarQueryParams.addAll(apiClient.parameterToPair("fieldSelector", fieldSelector));
    }
    if (includeUninitialized != null) {
      localVarQueryParams.addAll(
          apiClient.parameterToPair("includeUninitialized", includeUninitialized));
    }
    if (labelSelector != null) {
      localVarQueryParams.addAll(apiClient.parameterToPair("labelSelector", labelSelector));
    }
    if (limit != null) {
      localVarQueryParams.addAll(apiClient.parameterToPair("limit", limit));
    }
    if (pretty != null) {
      localVarQueryParams.addAll(apiClient.parameterToPair("pretty", pretty));
    }
    if (resourceVersion != null) {
      localVarQueryParams.addAll(apiClient.parameterToPair("resourceVersion", resourceVersion));
    }
    if (timeoutSeconds != null) {
      localVarQueryParams.addAll(apiClient.parameterToPair("timeoutSeconds", timeoutSeconds));
    }
    if (watch != null) {
      localVarQueryParams.addAll(apiClient.parameterToPair("watch", watch));
    }

    Map<String, String> localVarHeaderParams = new HashMap<String, String>();

    Map<String, Object> localVarFormParams = new HashMap<String, Object>();

    final String[] localVarAccepts = {
      "application/json",
      "application/yaml",
      "application/vnd.kubernetes.protobuf",
      "application/json;stream=watch",
      "application/vnd.kubernetes.protobuf;stream=watch"
    };
    final String localVarAccept = apiClient.selectHeaderAccept(localVarAccepts);
    if (localVarAccept != null) localVarHeaderParams.put("Accept", localVarAccept);

    final String[] localVarContentTypes = {"*/*"};
    final String localVarContentType = apiClient.selectHeaderContentType(localVarContentTypes);
    localVarHeaderParams.put("Content-Type", localVarContentType);

    if (progressListener != null) {
      apiClient
          .getHttpClient()
          .networkInterceptors()
          .add(
              chain -> {
                com.squareup.okhttp.Response originalResponse = chain.proceed(chain.request());
                return originalResponse
                    .newBuilder()
                    .body(new ProgressResponseBody(originalResponse.body(), progressListener))
                    .build();
              });
    }

    String[] localVarAuthNames = new String[] {"BearerToken"};
    return apiClient.buildCall(
        localVarPath,
        "GET",
        localVarQueryParams,
        localVarCollectionQueryParams,
        localVarPostBody,
        localVarHeaderParams,
        localVarFormParams,
        localVarAuthNames,
        progressRequestListener);
  }

  private com.squareup.okhttp.Call watchWebLogicOracleNamespacedDomainValidateBeforeCall(
      String name,
      String namespace,
      String _continue,
      String fieldSelector,
      Boolean includeUninitialized,
      String labelSelector,
      Integer limit,
      String pretty,
      String resourceVersion,
      Integer timeoutSeconds,
      Boolean watch,
      final ProgressResponseBody.ProgressListener progressListener,
      final ProgressRequestBody.ProgressRequestListener progressRequestListener)
      throws ApiException {

    // verify the required parameter 'name' is set
    if (name == null) {
      throw new ApiException(
          "Missing the required parameter 'name' when calling watchWebLogicOracleNamespacedDomain(Async)");
    }

    // verify the required parameter 'namespace' is set
    if (namespace == null) {
      throw new ApiException(
          "Missing the required parameter 'namespace' when calling watchWebLogicOracleNamespacedDomain(Async)");
    }

    com.squareup.okhttp.Call call =
        watchWebLogicOracleNamespacedDomainCall(
            name,
            namespace,
            _continue,
            fieldSelector,
            includeUninitialized,
            labelSelector,
            limit,
            pretty,
            resourceVersion,
            timeoutSeconds,
            watch,
            progressListener,
            progressRequestListener);
    return call;
  }

  /**
   * watch changes to an object of kind Domain.
   *
   * @param name name of the Pod (required)
   * @param namespace object name and auth scope, such as for teams and projects (required)
   * @param _continue The continue option should be set when retrieving more results from the
   *     server. Since this value is server defined, clients may only use the continue value from a
   *     previous query result with identical query parameters (except for the value of continue)
   *     and the server may reject a continue value it does not recognize. If the specified continue
   *     value is no longer valid whether due to expiration (generally five to fifteen minutes) or a
   *     configuration change on the server the server will respond with a 410 ResourceExpired error
   *     indicating the client must restart their list without the continue field. This field is not
   *     supported when watch is true. Clients may start a watch from the last resourceVersion value
   *     returned by the server and not miss any modifications. (optional)
   * @param fieldSelector A selector to restrict the list of returned objects by their fields.
   *     Defaults to everything. (optional)
   * @param includeUninitialized If true, partially initialized resources are included in the
   *     response. (optional)
   * @param labelSelector A selector to restrict the list of returned objects by their labels.
   *     Defaults to everything. (optional)
   * @param limit limit is a maximum number of responses to return for a list call. If more items
   *     exist, the server will set the &#x60;continue&#x60; field on the list metadata to a value
   *     that can be used with the same initial query to retrieve the next set of results. Setting a
   *     limit may return fewer than the requested amount of items (up to zero items) in the event
   *     all requested objects are filtered out and clients should only use the presence of the
   *     continue field to determine whether more results are available. Servers may choose not to
   *     support the limit argument and will return all of the available results. If limit is
   *     specified and the continue field is empty, clients may assume that no more results are
   *     available. This field is not supported if watch is true. The server guarantees that the
   *     objects returned when using continue will be identical to issuing a single list call
   *     without a limit - that is, no objects created, modified, or deleted after the first request
   *     is issued will be included in any subsequent continued requests. This is sometimes referred
   *     to as a consistent snapshot, and ensures that a client that is using limit to receive
   *     smaller chunks of a very large result can ensure they see all possible objects. If objects
   *     are updated during a chunked list the version of the object that was present at the time
   *     the first list result was calculated is returned. (optional)
   * @param pretty If &#39;true&#39;, then the output is pretty printed. (optional)
   * @param resourceVersion When specified with a watch call, shows changes that occur after that
   *     particular version of a resource. Defaults to changes from the beginning of history. When
   *     specified for list: - if unset, then the result is returned from remote storage based on
   *     quorum-read flag; - if it&#39;s 0, then we simply return what we currently have in cache,
   *     no guarantee; - if set to non zero, then the result is at least as fresh as given rv.
   *     (optional)
   * @param timeoutSeconds Timeout for the list/watch call. (optional)
   * @param watch Watch for changes to the described resources and return them as a stream of add,
   *     update, and remove notifications. Specify resourceVersion. (optional)
   * @return V1WatchEvent
   * @throws ApiException If fail to call the API, e.g. server error or cannot deserialize the
   *     response body
   */
  public V1WatchEvent watchWebLogicOracleNamespacedDomain(
      String name,
      String namespace,
      String _continue,
      String fieldSelector,
      Boolean includeUninitialized,
      String labelSelector,
      Integer limit,
      String pretty,
      String resourceVersion,
      Integer timeoutSeconds,
      Boolean watch)
      throws ApiException {
    ApiResponse<V1WatchEvent> resp =
        watchWebLogicOracleNamespacedDomainWithHttpInfo(
            name,
            namespace,
            _continue,
            fieldSelector,
            includeUninitialized,
            labelSelector,
            limit,
            pretty,
            resourceVersion,
            timeoutSeconds,
            watch);
    return resp.getData();
  }

  /**
   * watch changes to an object of kind Domain.
   *
   * @param name name of the Pod (required)
   * @param namespace object name and auth scope, such as for teams and projects (required)
   * @param _continue The continue option should be set when retrieving more results from the
   *     server. Since this value is server defined, clients may only use the continue value from a
   *     previous query result with identical query parameters (except for the value of continue)
   *     and the server may reject a continue value it does not recognize. If the specified continue
   *     value is no longer valid whether due to expiration (generally five to fifteen minutes) or a
   *     configuration change on the server the server will respond with a 410 ResourceExpired error
   *     indicating the client must restart their list without the continue field. This field is not
   *     supported when watch is true. Clients may start a watch from the last resourceVersion value
   *     returned by the server and not miss any modifications. (optional)
   * @param fieldSelector A selector to restrict the list of returned objects by their fields.
   *     Defaults to everything. (optional)
   * @param includeUninitialized If true, partially initialized resources are included in the
   *     response. (optional)
   * @param labelSelector A selector to restrict the list of returned objects by their labels.
   *     Defaults to everything. (optional)
   * @param limit limit is a maximum number of responses to return for a list call. If more items
   *     exist, the server will set the &#x60;continue&#x60; field on the list metadata to a value
   *     that can be used with the same initial query to retrieve the next set of results. Setting a
   *     limit may return fewer than the requested amount of items (up to zero items) in the event
   *     all requested objects are filtered out and clients should only use the presence of the
   *     continue field to determine whether more results are available. Servers may choose not to
   *     support the limit argument and will return all of the available results. If limit is
   *     specified and the continue field is empty, clients may assume that no more results are
   *     available. This field is not supported if watch is true. The server guarantees that the
   *     objects returned when using continue will be identical to issuing a single list call
   *     without a limit - that is, no objects created, modified, or deleted after the first request
   *     is issued will be included in any subsequent continued requests. This is sometimes referred
   *     to as a consistent snapshot, and ensures that a client that is using limit to receive
   *     smaller chunks of a very large result can ensure they see all possible objects. If objects
   *     are updated during a chunked list the version of the object that was present at the time
   *     the first list result was calculated is returned. (optional)
   * @param pretty If &#39;true&#39;, then the output is pretty printed. (optional)
   * @param resourceVersion When specified with a watch call, shows changes that occur after that
   *     particular version of a resource. Defaults to changes from the beginning of history. When
   *     specified for list: - if unset, then the result is returned from remote storage based on
   *     quorum-read flag; - if it&#39;s 0, then we simply return what we currently have in cache,
   *     no guarantee; - if set to non zero, then the result is at least as fresh as given rv.
   *     (optional)
   * @param timeoutSeconds Timeout for the list/watch call. (optional)
   * @param watch Watch for changes to the described resources and return them as a stream of add,
   *     update, and remove notifications. Specify resourceVersion. (optional)
   * @return ApiResponse&lt;V1WatchEvent&gt;
   * @throws ApiException If fail to call the API, e.g. server error or cannot deserialize the
   *     response body
   */
  public ApiResponse<V1WatchEvent> watchWebLogicOracleNamespacedDomainWithHttpInfo(
      String name,
      String namespace,
      String _continue,
      String fieldSelector,
      Boolean includeUninitialized,
      String labelSelector,
      Integer limit,
      String pretty,
      String resourceVersion,
      Integer timeoutSeconds,
      Boolean watch)
      throws ApiException {
    com.squareup.okhttp.Call call =
        watchWebLogicOracleNamespacedDomainValidateBeforeCall(
            name,
            namespace,
            _continue,
            fieldSelector,
            includeUninitialized,
            labelSelector,
            limit,
            pretty,
            resourceVersion,
            timeoutSeconds,
            watch,
            null,
            null);
    Type localVarReturnType = new TypeToken<V1WatchEvent>() {}.getType();
    return apiClient.execute(call, localVarReturnType);
  }

  /**
   * (asynchronously) watch changes to an object of kind Domain.
   *
   * @param name name of the Pod (required)
   * @param namespace object name and auth scope, such as for teams and projects (required)
   * @param _continue The continue option should be set when retrieving more results from the
   *     server. Since this value is server defined, clients may only use the continue value from a
   *     previous query result with identical query parameters (except for the value of continue)
   *     and the server may reject a continue value it does not recognize. If the specified continue
   *     value is no longer valid whether due to expiration (generally five to fifteen minutes) or a
   *     configuration change on the server the server will respond with a 410 ResourceExpired error
   *     indicating the client must restart their list without the continue field. This field is not
   *     supported when watch is true. Clients may start a watch from the last resourceVersion value
   *     returned by the server and not miss any modifications. (optional)
   * @param fieldSelector A selector to restrict the list of returned objects by their fields.
   *     Defaults to everything. (optional)
   * @param includeUninitialized If true, partially initialized resources are included in the
   *     response. (optional)
   * @param labelSelector A selector to restrict the list of returned objects by their labels.
   *     Defaults to everything. (optional)
   * @param limit limit is a maximum number of responses to return for a list call. If more items
   *     exist, the server will set the &#x60;continue&#x60; field on the list metadata to a value
   *     that can be used with the same initial query to retrieve the next set of results. Setting a
   *     limit may return fewer than the requested amount of items (up to zero items) in the event
   *     all requested objects are filtered out and clients should only use the presence of the
   *     continue field to determine whether more results are available. Servers may choose not to
   *     support the limit argument and will return all of the available results. If limit is
   *     specified and the continue field is empty, clients may assume that no more results are
   *     available. This field is not supported if watch is true. The server guarantees that the
   *     objects returned when using continue will be identical to issuing a single list call
   *     without a limit - that is, no objects created, modified, or deleted after the first request
   *     is issued will be included in any subsequent continued requests. This is sometimes referred
   *     to as a consistent snapshot, and ensures that a client that is using limit to receive
   *     smaller chunks of a very large result can ensure they see all possible objects. If objects
   *     are updated during a chunked list the version of the object that was present at the time
   *     the first list result was calculated is returned. (optional)
   * @param pretty If &#39;true&#39;, then the output is pretty printed. (optional)
   * @param resourceVersion When specified with a watch call, shows changes that occur after that
   *     particular version of a resource. Defaults to changes from the beginning of history. When
   *     specified for list: - if unset, then the result is returned from remote storage based on
   *     quorum-read flag; - if it&#39;s 0, then we simply return what we currently have in cache,
   *     no guarantee; - if set to non zero, then the result is at least as fresh as given rv.
   *     (optional)
   * @param timeoutSeconds Timeout for the list/watch call. (optional)
   * @param watch Watch for changes to the described resources and return them as a stream of add,
   *     update, and remove notifications. Specify resourceVersion. (optional)
   * @param callback The callback to be executed when the API call finishes
   * @return The request call
   * @throws ApiException If fail to process the API call, e.g. serializing the request body object
   */
  public com.squareup.okhttp.Call watchWebLogicOracleNamespacedDomainAsync(
      String name,
      String namespace,
      String _continue,
      String fieldSelector,
      Boolean includeUninitialized,
      String labelSelector,
      Integer limit,
      String pretty,
      String resourceVersion,
      Integer timeoutSeconds,
      Boolean watch,
      final ApiCallback<V1WatchEvent> callback)
      throws ApiException {

    ProgressResponseBody.ProgressListener progressListener = null;
    ProgressRequestBody.ProgressRequestListener progressRequestListener = null;

    if (callback != null) {
      progressListener =
          (bytesRead, contentLength, done) ->
              callback.onDownloadProgress(bytesRead, contentLength, done);

      progressRequestListener =
          (bytesWritten, contentLength, done) ->
              callback.onUploadProgress(bytesWritten, contentLength, done);
    }

    com.squareup.okhttp.Call call =
        watchWebLogicOracleNamespacedDomainValidateBeforeCall(
            name,
            namespace,
            _continue,
            fieldSelector,
            includeUninitialized,
            labelSelector,
            limit,
            pretty,
            resourceVersion,
            timeoutSeconds,
            watch,
            progressListener,
            progressRequestListener);
    Type localVarReturnType = new TypeToken<V1WatchEvent>() {}.getType();
    apiClient.executeAsync(call, localVarReturnType, callback);
    return call;
  }

  /**
   * Build call for watchWebLogicOracleNamespacedDomainList.
   *
   * @param namespace object name and auth scope, such as for teams and projects (required)
   * @param _continue The continue option should be set when retrieving more results from the
   *     server. Since this value is server defined, clients may only use the continue value from a
   *     previous query result with identical query parameters (except for the value of continue)
   *     and the server may reject a continue value it does not recognize. If the specified continue
   *     value is no longer valid whether due to expiration (generally five to fifteen minutes) or a
   *     configuration change on the server the server will respond with a 410 ResourceExpired error
   *     indicating the client must restart their list without the continue field. This field is not
   *     supported when watch is true. Clients may start a watch from the last resourceVersion value
   *     returned by the server and not miss any modifications. (optional)
   * @param fieldSelector A selector to restrict the list of returned objects by their fields.
   *     Defaults to everything. (optional)
   * @param includeUninitialized If true, partially initialized resources are included in the
   *     response. (optional)
   * @param labelSelector A selector to restrict the list of returned objects by their labels.
   *     Defaults to everything. (optional)
   * @param limit limit is a maximum number of responses to return for a list call. If more items
   *     exist, the server will set the &#x60;continue&#x60; field on the list metadata to a value
   *     that can be used with the same initial query to retrieve the next set of results. Setting a
   *     limit may return fewer than the requested amount of items (up to zero items) in the event
   *     all requested objects are filtered out and clients should only use the presence of the
   *     continue field to determine whether more results are available. Servers may choose not to
   *     support the limit argument and will return all of the available results. If limit is
   *     specified and the continue field is empty, clients may assume that no more results are
   *     available. This field is not supported if watch is true. The server guarantees that the
   *     objects returned when using continue will be identical to issuing a single list call
   *     without a limit - that is, no objects created, modified, or deleted after the first request
   *     is issued will be included in any subsequent continued requests. This is sometimes referred
   *     to as a consistent snapshot, and ensures that a client that is using limit to receive
   *     smaller chunks of a very large result can ensure they see all possible objects. If objects
   *     are updated during a chunked list the version of the object that was present at the time
   *     the first list result was calculated is returned. (optional)
   * @param pretty If &#39;true&#39;, then the output is pretty printed. (optional)
   * @param resourceVersion When specified with a watch call, shows changes that occur after that
   *     particular version of a resource. Defaults to changes from the beginning of history. When
   *     specified for list: - if unset, then the result is returned from remote storage based on
   *     quorum-read flag; - if it&#39;s 0, then we simply return what we currently have in cache,
   *     no guarantee; - if set to non zero, then the result is at least as fresh as given rv.
   *     (optional)
   * @param timeoutSeconds Timeout for the list/watch call. (optional)
   * @param watch Watch for changes to the described resources and return them as a stream of add,
   *     update, and remove notifications. Specify resourceVersion. (optional)
   * @param progressListener Progress listener
   * @param progressRequestListener Progress request listener
   * @return Call to execute
   * @throws ApiException If fail to serialize the request body object
   */
  public com.squareup.okhttp.Call watchWebLogicOracleNamespacedDomainListCall(
      String namespace,
      String _continue,
      String fieldSelector,
      Boolean includeUninitialized,
      String labelSelector,
      Integer limit,
      String pretty,
      String resourceVersion,
      Integer timeoutSeconds,
      Boolean watch,
      final ProgressResponseBody.ProgressListener progressListener,
      final ProgressRequestBody.ProgressRequestListener progressRequestListener)
      throws ApiException {
    Object localVarPostBody = null;

    // create path and map variables
    String localVarPath =
        "/apis/weblogic.oracle/"
            + DOMAIN_VERSION
            + "/watch/namespaces/{namespace}/domains"
                .replaceAll("\\{" + "namespace" + "\\}", apiClient.escapeString(namespace));

    List<Pair> localVarQueryParams = new ArrayList<Pair>();
    List<Pair> localVarCollectionQueryParams = new ArrayList<Pair>();
    if (_continue != null) {
      localVarQueryParams.addAll(apiClient.parameterToPair("continue", _continue));
    }
    if (fieldSelector != null) {
      localVarQueryParams.addAll(apiClient.parameterToPair("fieldSelector", fieldSelector));
    }
    if (includeUninitialized != null) {
      localVarQueryParams.addAll(
          apiClient.parameterToPair("includeUninitialized", includeUninitialized));
    }
    if (labelSelector != null) {
      localVarQueryParams.addAll(apiClient.parameterToPair("labelSelector", labelSelector));
    }
    if (limit != null) {
      localVarQueryParams.addAll(apiClient.parameterToPair("limit", limit));
    }
    if (pretty != null) {
      localVarQueryParams.addAll(apiClient.parameterToPair("pretty", pretty));
    }
    if (resourceVersion != null) {
      localVarQueryParams.addAll(apiClient.parameterToPair("resourceVersion", resourceVersion));
    }
    if (timeoutSeconds != null) {
      localVarQueryParams.addAll(apiClient.parameterToPair("timeoutSeconds", timeoutSeconds));
    }
    if (watch != null) {
      localVarQueryParams.addAll(apiClient.parameterToPair("watch", watch));
    }

    Map<String, String> localVarHeaderParams = new HashMap<String, String>();

    Map<String, Object> localVarFormParams = new HashMap<String, Object>();

    final String[] localVarAccepts = {
      "application/json",
      "application/yaml",
      "application/vnd.kubernetes.protobuf",
      "application/json;stream=watch",
      "application/vnd.kubernetes.protobuf;stream=watch"
    };
    final String localVarAccept = apiClient.selectHeaderAccept(localVarAccepts);
    if (localVarAccept != null) localVarHeaderParams.put("Accept", localVarAccept);

    final String[] localVarContentTypes = {"*/*"};
    final String localVarContentType = apiClient.selectHeaderContentType(localVarContentTypes);
    localVarHeaderParams.put("Content-Type", localVarContentType);

    if (progressListener != null) {
      apiClient
          .getHttpClient()
          .networkInterceptors()
          .add(
              chain -> {
                com.squareup.okhttp.Response originalResponse = chain.proceed(chain.request());
                return originalResponse
                    .newBuilder()
                    .body(new ProgressResponseBody(originalResponse.body(), progressListener))
                    .build();
              });
    }

    String[] localVarAuthNames = new String[] {"BearerToken"};
    return apiClient.buildCall(
        localVarPath,
        "GET",
        localVarQueryParams,
        localVarCollectionQueryParams,
        localVarPostBody,
        localVarHeaderParams,
        localVarFormParams,
        localVarAuthNames,
        progressRequestListener);
  }

  private com.squareup.okhttp.Call watchWebLogicOracleNamespacedDomainListValidateBeforeCall(
      String namespace,
      String _continue,
      String fieldSelector,
      Boolean includeUninitialized,
      String labelSelector,
      Integer limit,
      String pretty,
      String resourceVersion,
      Integer timeoutSeconds,
      Boolean watch,
      final ProgressResponseBody.ProgressListener progressListener,
      final ProgressRequestBody.ProgressRequestListener progressRequestListener)
      throws ApiException {

    // verify the required parameter 'namespace' is set
    if (namespace == null) {
      throw new ApiException(
          "Missing the required parameter 'namespace' when calling watchWebLogicOracleNamespacedDomainList(Async)");
    }

    com.squareup.okhttp.Call call =
        watchWebLogicOracleNamespacedDomainListCall(
            namespace,
            _continue,
            fieldSelector,
            includeUninitialized,
            labelSelector,
            limit,
            pretty,
            resourceVersion,
            timeoutSeconds,
            watch,
            progressListener,
            progressRequestListener);
    return call;
  }

  /**
   * watch individual changes to a list of Domain.
   *
   * @param namespace object name and auth scope, such as for teams and projects (required)
   * @param _continue The continue option should be set when retrieving more results from the
   *     server. Since this value is server defined, clients may only use the continue value from a
   *     previous query result with identical query parameters (except for the value of continue)
   *     and the server may reject a continue value it does not recognize. If the specified continue
   *     value is no longer valid whether due to expiration (generally five to fifteen minutes) or a
   *     configuration change on the server the server will respond with a 410 ResourceExpired error
   *     indicating the client must restart their list without the continue field. This field is not
   *     supported when watch is true. Clients may start a watch from the last resourceVersion value
   *     returned by the server and not miss any modifications. (optional)
   * @param fieldSelector A selector to restrict the list of returned objects by their fields.
   *     Defaults to everything. (optional)
   * @param includeUninitialized If true, partially initialized resources are included in the
   *     response. (optional)
   * @param labelSelector A selector to restrict the list of returned objects by their labels.
   *     Defaults to everything. (optional)
   * @param limit limit is a maximum number of responses to return for a list call. If more items
   *     exist, the server will set the &#x60;continue&#x60; field on the list metadata to a value
   *     that can be used with the same initial query to retrieve the next set of results. Setting a
   *     limit may return fewer than the requested amount of items (up to zero items) in the event
   *     all requested objects are filtered out and clients should only use the presence of the
   *     continue field to determine whether more results are available. Servers may choose not to
   *     support the limit argument and will return all of the available results. If limit is
   *     specified and the continue field is empty, clients may assume that no more results are
   *     available. This field is not supported if watch is true. The server guarantees that the
   *     objects returned when using continue will be identical to issuing a single list call
   *     without a limit - that is, no objects created, modified, or deleted after the first request
   *     is issued will be included in any subsequent continued requests. This is sometimes referred
   *     to as a consistent snapshot, and ensures that a client that is using limit to receive
   *     smaller chunks of a very large result can ensure they see all possible objects. If objects
   *     are updated during a chunked list the version of the object that was present at the time
   *     the first list result was calculated is returned. (optional)
   * @param pretty If &#39;true&#39;, then the output is pretty printed. (optional)
   * @param resourceVersion When specified with a watch call, shows changes that occur after that
   *     particular version of a resource. Defaults to changes from the beginning of history. When
   *     specified for list: - if unset, then the result is returned from remote storage based on
   *     quorum-read flag; - if it&#39;s 0, then we simply return what we currently have in cache,
   *     no guarantee; - if set to non zero, then the result is at least as fresh as given rv.
   *     (optional)
   * @param timeoutSeconds Timeout for the list/watch call. (optional)
   * @param watch Watch for changes to the described resources and return them as a stream of add,
   *     update, and remove notifications. Specify resourceVersion. (optional)
   * @return V1WatchEvent
   * @throws ApiException If fail to call the API, e.g. server error or cannot deserialize the
   *     response body
   */
  public V1WatchEvent watchWebLogicOracleNamespacedDomainList(
      String namespace,
      String _continue,
      String fieldSelector,
      Boolean includeUninitialized,
      String labelSelector,
      Integer limit,
      String pretty,
      String resourceVersion,
      Integer timeoutSeconds,
      Boolean watch)
      throws ApiException {
    ApiResponse<V1WatchEvent> resp =
        watchWebLogicOracleNamespacedDomainListWithHttpInfo(
            namespace,
            _continue,
            fieldSelector,
            includeUninitialized,
            labelSelector,
            limit,
            pretty,
            resourceVersion,
            timeoutSeconds,
            watch);
    return resp.getData();
  }

  /**
   * watch individual changes to a list of Domain.
   *
   * @param namespace object name and auth scope, such as for teams and projects (required)
   * @param _continue The continue option should be set when retrieving more results from the
   *     server. Since this value is server defined, clients may only use the continue value from a
   *     previous query result with identical query parameters (except for the value of continue)
   *     and the server may reject a continue value it does not recognize. If the specified continue
   *     value is no longer valid whether due to expiration (generally five to fifteen minutes) or a
   *     configuration change on the server the server will respond with a 410 ResourceExpired error
   *     indicating the client must restart their list without the continue field. This field is not
   *     supported when watch is true. Clients may start a watch from the last resourceVersion value
   *     returned by the server and not miss any modifications. (optional)
   * @param fieldSelector A selector to restrict the list of returned objects by their fields.
   *     Defaults to everything. (optional)
   * @param includeUninitialized If true, partially initialized resources are included in the
   *     response. (optional)
   * @param labelSelector A selector to restrict the list of returned objects by their labels.
   *     Defaults to everything. (optional)
   * @param limit limit is a maximum number of responses to return for a list call. If more items
   *     exist, the server will set the &#x60;continue&#x60; field on the list metadata to a value
   *     that can be used with the same initial query to retrieve the next set of results. Setting a
   *     limit may return fewer than the requested amount of items (up to zero items) in the event
   *     all requested objects are filtered out and clients should only use the presence of the
   *     continue field to determine whether more results are available. Servers may choose not to
   *     support the limit argument and will return all of the available results. If limit is
   *     specified and the continue field is empty, clients may assume that no more results are
   *     available. This field is not supported if watch is true. The server guarantees that the
   *     objects returned when using continue will be identical to issuing a single list call
   *     without a limit - that is, no objects created, modified, or deleted after the first request
   *     is issued will be included in any subsequent continued requests. This is sometimes referred
   *     to as a consistent snapshot, and ensures that a client that is using limit to receive
   *     smaller chunks of a very large result can ensure they see all possible objects. If objects
   *     are updated during a chunked list the version of the object that was present at the time
   *     the first list result was calculated is returned. (optional)
   * @param pretty If &#39;true&#39;, then the output is pretty printed. (optional)
   * @param resourceVersion When specified with a watch call, shows changes that occur after that
   *     particular version of a resource. Defaults to changes from the beginning of history. When
   *     specified for list: - if unset, then the result is returned from remote storage based on
   *     quorum-read flag; - if it&#39;s 0, then we simply return what we currently have in cache,
   *     no guarantee; - if set to non zero, then the result is at least as fresh as given rv.
   *     (optional)
   * @param timeoutSeconds Timeout for the list/watch call. (optional)
   * @param watch Watch for changes to the described resources and return them as a stream of add,
   *     update, and remove notifications. Specify resourceVersion. (optional)
   * @return ApiResponse&lt;V1WatchEvent&gt;
   * @throws ApiException If fail to call the API, e.g. server error or cannot deserialize the
   *     response body
   */
  public ApiResponse<V1WatchEvent> watchWebLogicOracleNamespacedDomainListWithHttpInfo(
      String namespace,
      String _continue,
      String fieldSelector,
      Boolean includeUninitialized,
      String labelSelector,
      Integer limit,
      String pretty,
      String resourceVersion,
      Integer timeoutSeconds,
      Boolean watch)
      throws ApiException {
    com.squareup.okhttp.Call call =
        watchWebLogicOracleNamespacedDomainListValidateBeforeCall(
            namespace,
            _continue,
            fieldSelector,
            includeUninitialized,
            labelSelector,
            limit,
            pretty,
            resourceVersion,
            timeoutSeconds,
            watch,
            null,
            null);
    Type localVarReturnType = new TypeToken<V1WatchEvent>() {}.getType();
    return apiClient.execute(call, localVarReturnType);
  }

  /**
   * (asynchronously) watch individual changes to a list of Domain.
   *
   * @param namespace object name and auth scope, such as for teams and projects (required)
   * @param _continue The continue option should be set when retrieving more results from the
   *     server. Since this value is server defined, clients may only use the continue value from a
   *     previous query result with identical query parameters (except for the value of continue)
   *     and the server may reject a continue value it does not recognize. If the specified continue
   *     value is no longer valid whether due to expiration (generally five to fifteen minutes) or a
   *     configuration change on the server the server will respond with a 410 ResourceExpired error
   *     indicating the client must restart their list without the continue field. This field is not
   *     supported when watch is true. Clients may start a watch from the last resourceVersion value
   *     returned by the server and not miss any modifications. (optional)
   * @param fieldSelector A selector to restrict the list of returned objects by their fields.
   *     Defaults to everything. (optional)
   * @param includeUninitialized If true, partially initialized resources are included in the
   *     response. (optional)
   * @param labelSelector A selector to restrict the list of returned objects by their labels.
   *     Defaults to everything. (optional)
   * @param limit limit is a maximum number of responses to return for a list call. If more items
   *     exist, the server will set the &#x60;continue&#x60; field on the list metadata to a value
   *     that can be used with the same initial query to retrieve the next set of results. Setting a
   *     limit may return fewer than the requested amount of items (up to zero items) in the event
   *     all requested objects are filtered out and clients should only use the presence of the
   *     continue field to determine whether more results are available. Servers may choose not to
   *     support the limit argument and will return all of the available results. If limit is
   *     specified and the continue field is empty, clients may assume that no more results are
   *     available. This field is not supported if watch is true. The server guarantees that the
   *     objects returned when using continue will be identical to issuing a single list call
   *     without a limit - that is, no objects created, modified, or deleted after the first request
   *     is issued will be included in any subsequent continued requests. This is sometimes referred
   *     to as a consistent snapshot, and ensures that a client that is using limit to receive
   *     smaller chunks of a very large result can ensure they see all possible objects. If objects
   *     are updated during a chunked list the version of the object that was present at the time
   *     the first list result was calculated is returned. (optional)
   * @param pretty If &#39;true&#39;, then the output is pretty printed. (optional)
   * @param resourceVersion When specified with a watch call, shows changes that occur after that
   *     particular version of a resource. Defaults to changes from the beginning of history. When
   *     specified for list: - if unset, then the result is returned from remote storage based on
   *     quorum-read flag; - if it&#39;s 0, then we simply return what we currently have in cache,
   *     no guarantee; - if set to non zero, then the result is at least as fresh as given rv.
   *     (optional)
   * @param timeoutSeconds Timeout for the list/watch call. (optional)
   * @param watch Watch for changes to the described resources and return them as a stream of add,
   *     update, and remove notifications. Specify resourceVersion. (optional)
   * @param callback The callback to be executed when the API call finishes
   * @return The request call
   * @throws ApiException If fail to process the API call, e.g. serializing the request body object
   */
  public com.squareup.okhttp.Call watchWebLogicOracleNamespacedDomainListAsync(
      String namespace,
      String _continue,
      String fieldSelector,
      Boolean includeUninitialized,
      String labelSelector,
      Integer limit,
      String pretty,
      String resourceVersion,
      Integer timeoutSeconds,
      Boolean watch,
      final ApiCallback<V1WatchEvent> callback)
      throws ApiException {

    ProgressResponseBody.ProgressListener progressListener = null;
    ProgressRequestBody.ProgressRequestListener progressRequestListener = null;

    if (callback != null) {
      progressListener =
          (bytesRead, contentLength, done) ->
              callback.onDownloadProgress(bytesRead, contentLength, done);

      progressRequestListener =
          (bytesWritten, contentLength, done) ->
              callback.onUploadProgress(bytesWritten, contentLength, done);
    }

    com.squareup.okhttp.Call call =
        watchWebLogicOracleNamespacedDomainListValidateBeforeCall(
            namespace,
            _continue,
            fieldSelector,
            includeUninitialized,
            labelSelector,
            limit,
            pretty,
            resourceVersion,
            timeoutSeconds,
            watch,
            progressListener,
            progressRequestListener);
    Type localVarReturnType = new TypeToken<V1WatchEvent>() {}.getType();
    apiClient.executeAsync(call, localVarReturnType, callback);
    return call;
  }
}

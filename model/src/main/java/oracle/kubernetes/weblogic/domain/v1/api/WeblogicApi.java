// Copyright 2017, 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.weblogic.domain.v1.api;

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
import oracle.kubernetes.weblogic.domain.v1.Domain;
import oracle.kubernetes.weblogic.domain.v1.DomainList;

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
   * Build call for createWebLogicOracleV1NamespacedDomain
   *
   * @param namespace object name and auth scope, such as for teams and projects (required)
   * @param body (required)
   * @param pretty If &#39;true&#39;, then the output is pretty printed. (optional)
   * @param progressListener Progress listener
   * @param progressRequestListener Progress request listener
   * @return Call to execute
   * @throws ApiException If fail to serialize the request body object
   */
  public com.squareup.okhttp.Call createWebLogicOracleV1NamespacedDomainCall(
      String namespace,
      Domain body,
      String pretty,
      final ProgressResponseBody.ProgressListener progressListener,
      final ProgressRequestBody.ProgressRequestListener progressRequestListener)
      throws ApiException {
    Object localVarPostBody = body;

    // create path and map variables
    String localVarPath =
        "/apis/weblogic.oracle/v1/namespaces/{namespace}/domains"
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

  private com.squareup.okhttp.Call createWebLogicOracleV1NamespacedDomainValidateBeforeCall(
      String namespace,
      Domain body,
      String pretty,
      final ProgressResponseBody.ProgressListener progressListener,
      final ProgressRequestBody.ProgressRequestListener progressRequestListener)
      throws ApiException {

    // verify the required parameter 'namespace' is set
    if (namespace == null) {
      throw new ApiException(
          "Missing the required parameter 'namespace' when calling createWebLogicOracleV1NamespacedDomain(Async)");
    }

    // verify the required parameter 'body' is set
    if (body == null) {
      throw new ApiException(
          "Missing the required parameter 'body' when calling createWebLogicOracleV1NamespacedDomain(Async)");
    }

    com.squareup.okhttp.Call call =
        createWebLogicOracleV1NamespacedDomainCall(
            namespace, body, pretty, progressListener, progressRequestListener);
    return call;
  }

  /**
   * create a Domain
   *
   * @param namespace object name and auth scope, such as for teams and projects (required)
   * @param body (required)
   * @param pretty If &#39;true&#39;, then the output is pretty printed. (optional)
   * @return Domain
   * @throws ApiException If fail to call the API, e.g. server error or cannot deserialize the
   *     response body
   */
  public Domain createWebLogicOracleV1NamespacedDomain(String namespace, Domain body, String pretty)
      throws ApiException {
    ApiResponse<Domain> resp =
        createWebLogicOracleV1NamespacedDomainWithHttpInfo(namespace, body, pretty);
    return resp.getData();
  }

  /**
   * create a Domain
   *
   * @param namespace object name and auth scope, such as for teams and projects (required)
   * @param body (required)
   * @param pretty If &#39;true&#39;, then the output is pretty printed. (optional)
   * @return ApiResponse&lt;Domain&gt;
   * @throws ApiException If fail to call the API, e.g. server error or cannot deserialize the
   *     response body
   */
  public ApiResponse<Domain> createWebLogicOracleV1NamespacedDomainWithHttpInfo(
      String namespace, Domain body, String pretty) throws ApiException {
    com.squareup.okhttp.Call call =
        createWebLogicOracleV1NamespacedDomainValidateBeforeCall(
            namespace, body, pretty, null, null);
    Type localVarReturnType = new TypeToken<Domain>() {}.getType();
    return apiClient.execute(call, localVarReturnType);
  }

  /**
   * (asynchronously) create a Domain
   *
   * @param namespace object name and auth scope, such as for teams and projects (required)
   * @param body (required)
   * @param pretty If &#39;true&#39;, then the output is pretty printed. (optional)
   * @param callback The callback to be executed when the API call finishes
   * @return The request call
   * @throws ApiException If fail to process the API call, e.g. serializing the request body object
   */
  public com.squareup.okhttp.Call createWebLogicOracleV1NamespacedDomainAsync(
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
        createWebLogicOracleV1NamespacedDomainValidateBeforeCall(
            namespace, body, pretty, progressListener, progressRequestListener);
    Type localVarReturnType = new TypeToken<Domain>() {}.getType();
    apiClient.executeAsync(call, localVarReturnType, callback);
    return call;
  }

  /**
   * Build call for deleteWebLogicOracleV1CollectionNamespacedDomain
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
  public com.squareup.okhttp.Call deleteWebLogicOracleV1CollectionNamespacedDomainCall(
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
        "/apis/weblogic.oracle/v1/namespaces/{namespace}/domains"
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

  private com.squareup.okhttp.Call
      deleteWebLogicOracleV1CollectionNamespacedDomainValidateBeforeCall(
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
          "Missing the required parameter 'namespace' when calling deleteWebLogicOracleV1CollectionNamespacedDomain(Async)");
    }

    com.squareup.okhttp.Call call =
        deleteWebLogicOracleV1CollectionNamespacedDomainCall(
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
   * delete collection of Domain
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
  public V1Status deleteWebLogicOracleV1CollectionNamespacedDomain(
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
        deleteWebLogicOracleV1CollectionNamespacedDomainWithHttpInfo(
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
   * delete collection of Domain
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
  public ApiResponse<V1Status> deleteWebLogicOracleV1CollectionNamespacedDomainWithHttpInfo(
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
        deleteWebLogicOracleV1CollectionNamespacedDomainValidateBeforeCall(
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
   * (asynchronously) delete collection of Domain
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
  public com.squareup.okhttp.Call deleteWebLogicOracleV1CollectionNamespacedDomainAsync(
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
        deleteWebLogicOracleV1CollectionNamespacedDomainValidateBeforeCall(
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
   * Build call for deleteWebLogicOracleV1NamespacedDomain
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
  public com.squareup.okhttp.Call deleteWebLogicOracleV1NamespacedDomainCall(
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
        "/apis/weblogic.oracle/v1/namespaces/{namespace}/domains/{name}"
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

  private com.squareup.okhttp.Call deleteWebLogicOracleV1NamespacedDomainValidateBeforeCall(
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
          "Missing the required parameter 'name' when calling deleteWebLogicOracleV1NamespacedDomain(Async)");
    }

    // verify the required parameter 'namespace' is set
    if (namespace == null) {
      throw new ApiException(
          "Missing the required parameter 'namespace' when calling deleteWebLogicOracleV1NamespacedDomain(Async)");
    }

    // verify the required parameter 'body' is set
    if (body == null) {
      throw new ApiException(
          "Missing the required parameter 'body' when calling deleteWebLogicOracleV1NamespacedDomain(Async)");
    }

    com.squareup.okhttp.Call call =
        deleteWebLogicOracleV1NamespacedDomainCall(
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
   * delete a Domain
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
  public V1Status deleteWebLogicOracleV1NamespacedDomain(
      String name,
      String namespace,
      V1DeleteOptions body,
      String pretty,
      Integer gracePeriodSeconds,
      Boolean orphanDependents,
      String propagationPolicy)
      throws ApiException {
    ApiResponse<V1Status> resp =
        deleteWebLogicOracleV1NamespacedDomainWithHttpInfo(
            name, namespace, body, pretty, gracePeriodSeconds, orphanDependents, propagationPolicy);
    return resp.getData();
  }

  /**
   * delete a Domain
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
  public ApiResponse<V1Status> deleteWebLogicOracleV1NamespacedDomainWithHttpInfo(
      String name,
      String namespace,
      V1DeleteOptions body,
      String pretty,
      Integer gracePeriodSeconds,
      Boolean orphanDependents,
      String propagationPolicy)
      throws ApiException {
    com.squareup.okhttp.Call call =
        deleteWebLogicOracleV1NamespacedDomainValidateBeforeCall(
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
   * (asynchronously) delete a Domain
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
  public com.squareup.okhttp.Call deleteWebLogicOracleV1NamespacedDomainAsync(
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
        deleteWebLogicOracleV1NamespacedDomainValidateBeforeCall(
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
   * Build call for listWebLogicOracleV1DomainForAllNamespaces
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
  public com.squareup.okhttp.Call listWebLogicOracleV1DomainForAllNamespacesCall(
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
    String localVarPath = "/apis/weblogic.oracle/v1/domains";

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

  private com.squareup.okhttp.Call listWebLogicOracleV1DomainForAllNamespacesValidateBeforeCall(
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
        listWebLogicOracleV1DomainForAllNamespacesCall(
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
   * list or watch objects of kind Domain
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
  public DomainList listWebLogicOracleV1DomainForAllNamespaces(
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
        listWebLogicOracleV1DomainForAllNamespacesWithHttpInfo(
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
   * list or watch objects of kind Domain
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
  public ApiResponse<DomainList> listWebLogicOracleV1DomainForAllNamespacesWithHttpInfo(
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
        listWebLogicOracleV1DomainForAllNamespacesValidateBeforeCall(
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
   * (asynchronously) list or watch objects of kind Domain
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
  public com.squareup.okhttp.Call listWebLogicOracleV1DomainForAllNamespacesAsync(
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
        listWebLogicOracleV1DomainForAllNamespacesValidateBeforeCall(
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
   * Build call for listWebLogicOracleV1NamespacedDomain
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
  public com.squareup.okhttp.Call listWebLogicOracleV1NamespacedDomainCall(
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
        "/apis/weblogic.oracle/v1/namespaces/{namespace}/domains"
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

  private com.squareup.okhttp.Call listWebLogicOracleV1NamespacedDomainValidateBeforeCall(
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
          "Missing the required parameter 'namespace' when calling listWebLogicOracleV1NamespacedDomain(Async)");
    }

    com.squareup.okhttp.Call call =
        listWebLogicOracleV1NamespacedDomainCall(
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
   * list or watch objects of kind Domain
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
  public DomainList listWebLogicOracleV1NamespacedDomain(
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
        listWebLogicOracleV1NamespacedDomainWithHttpInfo(
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
   * list or watch objects of kind Domain
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
  public ApiResponse<DomainList> listWebLogicOracleV1NamespacedDomainWithHttpInfo(
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
        listWebLogicOracleV1NamespacedDomainValidateBeforeCall(
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
   * (asynchronously) list or watch objects of kind Domain
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
  public com.squareup.okhttp.Call listWebLogicOracleV1NamespacedDomainAsync(
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
        listWebLogicOracleV1NamespacedDomainValidateBeforeCall(
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
   * Build call for patchWebLogicOracleV1NamespacedDomain
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
  public com.squareup.okhttp.Call patchWebLogicOracleV1NamespacedDomainCall(
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
        "/apis/weblogic.oracle/v1/namespaces/{namespace}/domains/{name}"
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

  private com.squareup.okhttp.Call patchWebLogicOracleV1NamespacedDomainValidateBeforeCall(
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
          "Missing the required parameter 'name' when calling patchWebLogicOracleV1NamespacedDomain(Async)");
    }

    // verify the required parameter 'namespace' is set
    if (namespace == null) {
      throw new ApiException(
          "Missing the required parameter 'namespace' when calling patchWebLogicOracleV1NamespacedDomain(Async)");
    }

    // verify the required parameter 'body' is set
    if (body == null) {
      throw new ApiException(
          "Missing the required parameter 'body' when calling patchWebLogicOracleV1NamespacedDomain(Async)");
    }

    com.squareup.okhttp.Call call =
        patchWebLogicOracleV1NamespacedDomainCall(
            name, namespace, body, pretty, progressListener, progressRequestListener);
    return call;
  }

  /**
   * partially update the specified Domain
   *
   * @param name name of the Domain (required)
   * @param namespace object name and auth scope, such as for teams and projects (required)
   * @param body (required)
   * @param pretty If &#39;true&#39;, then the output is pretty printed. (optional)
   * @return Domain
   * @throws ApiException If fail to call the API, e.g. server error or cannot deserialize the
   *     response body
   */
  public Domain patchWebLogicOracleV1NamespacedDomain(
      String name, String namespace, Patch body, String pretty) throws ApiException {
    ApiResponse<Domain> resp =
        patchWebLogicOracleV1NamespacedDomainWithHttpInfo(name, namespace, body, pretty);
    return resp.getData();
  }

  /**
   * partially update the specified Domain
   *
   * @param name name of the Domain (required)
   * @param namespace object name and auth scope, such as for teams and projects (required)
   * @param body (required)
   * @param pretty If &#39;true&#39;, then the output is pretty printed. (optional)
   * @return ApiResponse&lt;Domain&gt;
   * @throws ApiException If fail to call the API, e.g. server error or cannot deserialize the
   *     response body
   */
  public ApiResponse<Domain> patchWebLogicOracleV1NamespacedDomainWithHttpInfo(
      String name, String namespace, Patch body, String pretty) throws ApiException {
    com.squareup.okhttp.Call call =
        patchWebLogicOracleV1NamespacedDomainValidateBeforeCall(
            name, namespace, body, pretty, null, null);
    Type localVarReturnType = new TypeToken<Domain>() {}.getType();
    return apiClient.execute(call, localVarReturnType);
  }

  /**
   * (asynchronously) partially update the specified Domain
   *
   * @param name name of the Domain (required)
   * @param namespace object name and auth scope, such as for teams and projects (required)
   * @param body (required)
   * @param pretty If &#39;true&#39;, then the output is pretty printed. (optional)
   * @param callback The callback to be executed when the API call finishes
   * @return The request call
   * @throws ApiException If fail to process the API call, e.g. serializing the request body object
   */
  public com.squareup.okhttp.Call patchWebLogicOracleV1NamespacedDomainAsync(
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
        patchWebLogicOracleV1NamespacedDomainValidateBeforeCall(
            name, namespace, body, pretty, progressListener, progressRequestListener);
    Type localVarReturnType = new TypeToken<Domain>() {}.getType();
    apiClient.executeAsync(call, localVarReturnType, callback);
    return call;
  }

  /**
   * Build call for patchWebLogicOracleV1NamespacedDomainScale
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
  public com.squareup.okhttp.Call patchWebLogicOracleV1NamespacedDomainScaleCall(
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
        "/apis/weblogic.oracle/v1/namespaces/{namespace}/domains/{name}/scale"
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

  private com.squareup.okhttp.Call patchWebLogicOracleV1NamespacedDomainScaleValidateBeforeCall(
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
          "Missing the required parameter 'name' when calling patchWebLogicOracleV1NamespacedDomainScale(Async)");
    }

    // verify the required parameter 'namespace' is set
    if (namespace == null) {
      throw new ApiException(
          "Missing the required parameter 'namespace' when calling patchWebLogicOracleV1NamespacedDomainScale(Async)");
    }

    // verify the required parameter 'body' is set
    if (body == null) {
      throw new ApiException(
          "Missing the required parameter 'body' when calling patchWebLogicOracleV1NamespacedDomainScale(Async)");
    }

    com.squareup.okhttp.Call call =
        patchWebLogicOracleV1NamespacedDomainScaleCall(
            name, namespace, body, pretty, progressListener, progressRequestListener);
    return call;
  }

  /**
   * partially update scale of the specified Domain
   *
   * @param name name of the Scale (required)
   * @param namespace object name and auth scope, such as for teams and projects (required)
   * @param body (required)
   * @param pretty If &#39;true&#39;, then the output is pretty printed. (optional)
   * @return V1Scale
   * @throws ApiException If fail to call the API, e.g. server error or cannot deserialize the
   *     response body
   */
  public V1Scale patchWebLogicOracleV1NamespacedDomainScale(
      String name, String namespace, Patch body, String pretty) throws ApiException {
    ApiResponse<V1Scale> resp =
        patchWebLogicOracleV1NamespacedDomainScaleWithHttpInfo(name, namespace, body, pretty);
    return resp.getData();
  }

  /**
   * partially update scale of the specified Domain
   *
   * @param name name of the Scale (required)
   * @param namespace object name and auth scope, such as for teams and projects (required)
   * @param body (required)
   * @param pretty If &#39;true&#39;, then the output is pretty printed. (optional)
   * @return ApiResponse&lt;V1Scale&gt;
   * @throws ApiException If fail to call the API, e.g. server error or cannot deserialize the
   *     response body
   */
  public ApiResponse<V1Scale> patchWebLogicOracleV1NamespacedDomainScaleWithHttpInfo(
      String name, String namespace, Patch body, String pretty) throws ApiException {
    com.squareup.okhttp.Call call =
        patchWebLogicOracleV1NamespacedDomainScaleValidateBeforeCall(
            name, namespace, body, pretty, null, null);
    Type localVarReturnType = new TypeToken<V1Scale>() {}.getType();
    return apiClient.execute(call, localVarReturnType);
  }

  /**
   * (asynchronously) partially update scale of the specified Domain
   *
   * @param name name of the Scale (required)
   * @param namespace object name and auth scope, such as for teams and projects (required)
   * @param body (required)
   * @param pretty If &#39;true&#39;, then the output is pretty printed. (optional)
   * @param callback The callback to be executed when the API call finishes
   * @return The request call
   * @throws ApiException If fail to process the API call, e.g. serializing the request body object
   */
  public com.squareup.okhttp.Call patchWebLogicOracleV1NamespacedDomainScaleAsync(
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
        patchWebLogicOracleV1NamespacedDomainScaleValidateBeforeCall(
            name, namespace, body, pretty, progressListener, progressRequestListener);
    Type localVarReturnType = new TypeToken<V1Scale>() {}.getType();
    apiClient.executeAsync(call, localVarReturnType, callback);
    return call;
  }

  /**
   * Build call for patchWebLogicOracleV1NamespacedDomainStatus
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
  public com.squareup.okhttp.Call patchWebLogicOracleV1NamespacedDomainStatusCall(
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
        "/apis/weblogic.oracle/v1/namespaces/{namespace}/domains/{name}/status"
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

  private com.squareup.okhttp.Call patchWebLogicOracleV1NamespacedDomainStatusValidateBeforeCall(
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
          "Missing the required parameter 'name' when calling patchWebLogicOracleV1NamespacedDomainStatus(Async)");
    }

    // verify the required parameter 'namespace' is set
    if (namespace == null) {
      throw new ApiException(
          "Missing the required parameter 'namespace' when calling patchWebLogicOracleV1NamespacedDomainStatus(Async)");
    }

    // verify the required parameter 'body' is set
    if (body == null) {
      throw new ApiException(
          "Missing the required parameter 'body' when calling patchWebLogicOracleV1NamespacedDomainStatus(Async)");
    }

    com.squareup.okhttp.Call call =
        patchWebLogicOracleV1NamespacedDomainStatusCall(
            name, namespace, body, pretty, progressListener, progressRequestListener);
    return call;
  }

  /**
   * partially update status of the specified Domain
   *
   * @param name name of the Domain (required)
   * @param namespace object name and auth scope, such as for teams and projects (required)
   * @param body (required)
   * @param pretty If &#39;true&#39;, then the output is pretty printed. (optional)
   * @return Domain
   * @throws ApiException If fail to call the API, e.g. server error or cannot deserialize the
   *     response body
   */
  public Domain patchWebLogicOracleV1NamespacedDomainStatus(
      String name, String namespace, Patch body, String pretty) throws ApiException {
    ApiResponse<Domain> resp =
        patchWebLogicOracleV1NamespacedDomainStatusWithHttpInfo(name, namespace, body, pretty);
    return resp.getData();
  }

  /**
   * partially update status of the specified Domain
   *
   * @param name name of the Domain (required)
   * @param namespace object name and auth scope, such as for teams and projects (required)
   * @param body (required)
   * @param pretty If &#39;true&#39;, then the output is pretty printed. (optional)
   * @return ApiResponse&lt;Domain&gt;
   * @throws ApiException If fail to call the API, e.g. server error or cannot deserialize the
   *     response body
   */
  public ApiResponse<Domain> patchWebLogicOracleV1NamespacedDomainStatusWithHttpInfo(
      String name, String namespace, Patch body, String pretty) throws ApiException {
    com.squareup.okhttp.Call call =
        patchWebLogicOracleV1NamespacedDomainStatusValidateBeforeCall(
            name, namespace, body, pretty, null, null);
    Type localVarReturnType = new TypeToken<Domain>() {}.getType();
    return apiClient.execute(call, localVarReturnType);
  }

  /**
   * (asynchronously) partially update status of the specified Domain
   *
   * @param name name of the Domain (required)
   * @param namespace object name and auth scope, such as for teams and projects (required)
   * @param body (required)
   * @param pretty If &#39;true&#39;, then the output is pretty printed. (optional)
   * @param callback The callback to be executed when the API call finishes
   * @return The request call
   * @throws ApiException If fail to process the API call, e.g. serializing the request body object
   */
  public com.squareup.okhttp.Call patchWebLogicOracleV1NamespacedDomainStatusAsync(
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
        patchWebLogicOracleV1NamespacedDomainStatusValidateBeforeCall(
            name, namespace, body, pretty, progressListener, progressRequestListener);
    Type localVarReturnType = new TypeToken<Domain>() {}.getType();
    apiClient.executeAsync(call, localVarReturnType, callback);
    return call;
  }

  /**
   * Build call for readWebLogicOracleV1NamespacedDomain
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
  public com.squareup.okhttp.Call readWebLogicOracleV1NamespacedDomainCall(
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
        "/apis/weblogic.oracle/v1/namespaces/{namespace}/domains/{name}"
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

  private com.squareup.okhttp.Call readWebLogicOracleV1NamespacedDomainValidateBeforeCall(
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
          "Missing the required parameter 'name' when calling readWebLogicOracleV1NamespacedDomain(Async)");
    }

    // verify the required parameter 'namespace' is set
    if (namespace == null) {
      throw new ApiException(
          "Missing the required parameter 'namespace' when calling readWebLogicOracleV1NamespacedDomain(Async)");
    }

    com.squareup.okhttp.Call call =
        readWebLogicOracleV1NamespacedDomainCall(
            name, namespace, pretty, exact, export, progressListener, progressRequestListener);
    return call;
  }

  /**
   * read the specified Domain
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
  public Domain readWebLogicOracleV1NamespacedDomain(
      String name, String namespace, String pretty, Boolean exact, Boolean export)
      throws ApiException {
    ApiResponse<Domain> resp =
        readWebLogicOracleV1NamespacedDomainWithHttpInfo(name, namespace, pretty, exact, export);
    return resp.getData();
  }

  /**
   * read the specified Domain
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
  public ApiResponse<Domain> readWebLogicOracleV1NamespacedDomainWithHttpInfo(
      String name, String namespace, String pretty, Boolean exact, Boolean export)
      throws ApiException {
    com.squareup.okhttp.Call call =
        readWebLogicOracleV1NamespacedDomainValidateBeforeCall(
            name, namespace, pretty, exact, export, null, null);
    Type localVarReturnType = new TypeToken<Domain>() {}.getType();
    return apiClient.execute(call, localVarReturnType);
  }

  /**
   * (asynchronously) read the specified Domain
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
  public com.squareup.okhttp.Call readWebLogicOracleV1NamespacedDomainAsync(
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
        readWebLogicOracleV1NamespacedDomainValidateBeforeCall(
            name, namespace, pretty, exact, export, progressListener, progressRequestListener);
    Type localVarReturnType = new TypeToken<Domain>() {}.getType();
    apiClient.executeAsync(call, localVarReturnType, callback);
    return call;
  }

  /**
   * Build call for readWebLogicOracleV1NamespacedDomainScale
   *
   * @param name name of the Scale (required)
   * @param namespace object name and auth scope, such as for teams and projects (required)
   * @param pretty If &#39;true&#39;, then the output is pretty printed. (optional)
   * @param progressListener Progress listener
   * @param progressRequestListener Progress request listener
   * @return Call to execute
   * @throws ApiException If fail to serialize the request body object
   */
  public com.squareup.okhttp.Call readWebLogicOracleV1NamespacedDomainScaleCall(
      String name,
      String namespace,
      String pretty,
      final ProgressResponseBody.ProgressListener progressListener,
      final ProgressRequestBody.ProgressRequestListener progressRequestListener)
      throws ApiException {
    Object localVarPostBody = null;

    // create path and map variables
    String localVarPath =
        "/apis/weblogic.oracle/v1/namespaces/{namespace}/domains/{name}/scale"
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

  private com.squareup.okhttp.Call readWebLogicOracleV1NamespacedDomainScaleValidateBeforeCall(
      String name,
      String namespace,
      String pretty,
      final ProgressResponseBody.ProgressListener progressListener,
      final ProgressRequestBody.ProgressRequestListener progressRequestListener)
      throws ApiException {

    // verify the required parameter 'name' is set
    if (name == null) {
      throw new ApiException(
          "Missing the required parameter 'name' when calling readWebLogicOracleV1NamespacedDomainScale(Async)");
    }

    // verify the required parameter 'namespace' is set
    if (namespace == null) {
      throw new ApiException(
          "Missing the required parameter 'namespace' when calling readWebLogicOracleV1NamespacedDomainScale(Async)");
    }

    com.squareup.okhttp.Call call =
        readWebLogicOracleV1NamespacedDomainScaleCall(
            name, namespace, pretty, progressListener, progressRequestListener);
    return call;
  }

  /**
   * read scale of the specified Domain
   *
   * @param name name of the Scale (required)
   * @param namespace object name and auth scope, such as for teams and projects (required)
   * @param pretty If &#39;true&#39;, then the output is pretty printed. (optional)
   * @return V1Scale
   * @throws ApiException If fail to call the API, e.g. server error or cannot deserialize the
   *     response body
   */
  public V1Scale readWebLogicOracleV1NamespacedDomainScale(
      String name, String namespace, String pretty) throws ApiException {
    ApiResponse<V1Scale> resp =
        readWebLogicOracleV1NamespacedDomainScaleWithHttpInfo(name, namespace, pretty);
    return resp.getData();
  }

  /**
   * read scale of the specified Domain
   *
   * @param name name of the Scale (required)
   * @param namespace object name and auth scope, such as for teams and projects (required)
   * @param pretty If &#39;true&#39;, then the output is pretty printed. (optional)
   * @return ApiResponse&lt;V1Scale&gt;
   * @throws ApiException If fail to call the API, e.g. server error or cannot deserialize the
   *     response body
   */
  public ApiResponse<V1Scale> readWebLogicOracleV1NamespacedDomainScaleWithHttpInfo(
      String name, String namespace, String pretty) throws ApiException {
    com.squareup.okhttp.Call call =
        readWebLogicOracleV1NamespacedDomainScaleValidateBeforeCall(
            name, namespace, pretty, null, null);
    Type localVarReturnType = new TypeToken<V1Scale>() {}.getType();
    return apiClient.execute(call, localVarReturnType);
  }

  /**
   * (asynchronously) read scale of the specified Domain
   *
   * @param name name of the Scale (required)
   * @param namespace object name and auth scope, such as for teams and projects (required)
   * @param pretty If &#39;true&#39;, then the output is pretty printed. (optional)
   * @param callback The callback to be executed when the API call finishes
   * @return The request call
   * @throws ApiException If fail to process the API call, e.g. serializing the request body object
   */
  public com.squareup.okhttp.Call readWebLogicOracleV1NamespacedDomainScaleAsync(
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
        readWebLogicOracleV1NamespacedDomainScaleValidateBeforeCall(
            name, namespace, pretty, progressListener, progressRequestListener);
    Type localVarReturnType = new TypeToken<V1Scale>() {}.getType();
    apiClient.executeAsync(call, localVarReturnType, callback);
    return call;
  }

  /**
   * Build call for readWebLogicOracleV1NamespacedDomainStatus
   *
   * @param name name of the Domain (required)
   * @param namespace object name and auth scope, such as for teams and projects (required)
   * @param pretty If &#39;true&#39;, then the output is pretty printed. (optional)
   * @param progressListener Progress listener
   * @param progressRequestListener Progress request listener
   * @return Call to execute
   * @throws ApiException If fail to serialize the request body object
   */
  public com.squareup.okhttp.Call readWebLogicOracleV1NamespacedDomainStatusCall(
      String name,
      String namespace,
      String pretty,
      final ProgressResponseBody.ProgressListener progressListener,
      final ProgressRequestBody.ProgressRequestListener progressRequestListener)
      throws ApiException {
    Object localVarPostBody = null;

    // create path and map variables
    String localVarPath =
        "/apis/weblogic.oracle/v1/namespaces/{namespace}/domains/{name}/status"
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

  private com.squareup.okhttp.Call readWebLogicOracleV1NamespacedDomainStatusValidateBeforeCall(
      String name,
      String namespace,
      String pretty,
      final ProgressResponseBody.ProgressListener progressListener,
      final ProgressRequestBody.ProgressRequestListener progressRequestListener)
      throws ApiException {

    // verify the required parameter 'name' is set
    if (name == null) {
      throw new ApiException(
          "Missing the required parameter 'name' when calling readWebLogicOracleV1NamespacedDomainStatus(Async)");
    }

    // verify the required parameter 'namespace' is set
    if (namespace == null) {
      throw new ApiException(
          "Missing the required parameter 'namespace' when calling readWebLogicOracleV1NamespacedDomainStatus(Async)");
    }

    com.squareup.okhttp.Call call =
        readWebLogicOracleV1NamespacedDomainStatusCall(
            name, namespace, pretty, progressListener, progressRequestListener);
    return call;
  }

  /**
   * read status of the specified Domain
   *
   * @param name name of the Domain (required)
   * @param namespace object name and auth scope, such as for teams and projects (required)
   * @param pretty If &#39;true&#39;, then the output is pretty printed. (optional)
   * @return Domain
   * @throws ApiException If fail to call the API, e.g. server error or cannot deserialize the
   *     response body
   */
  public Domain readWebLogicOracleV1NamespacedDomainStatus(
      String name, String namespace, String pretty) throws ApiException {
    ApiResponse<Domain> resp =
        readWebLogicOracleV1NamespacedDomainStatusWithHttpInfo(name, namespace, pretty);
    return resp.getData();
  }

  /**
   * read status of the specified Domain
   *
   * @param name name of the Domain (required)
   * @param namespace object name and auth scope, such as for teams and projects (required)
   * @param pretty If &#39;true&#39;, then the output is pretty printed. (optional)
   * @return ApiResponse&lt;Domain&gt;
   * @throws ApiException If fail to call the API, e.g. server error or cannot deserialize the
   *     response body
   */
  public ApiResponse<Domain> readWebLogicOracleV1NamespacedDomainStatusWithHttpInfo(
      String name, String namespace, String pretty) throws ApiException {
    com.squareup.okhttp.Call call =
        readWebLogicOracleV1NamespacedDomainStatusValidateBeforeCall(
            name, namespace, pretty, null, null);
    Type localVarReturnType = new TypeToken<Domain>() {}.getType();
    return apiClient.execute(call, localVarReturnType);
  }

  /**
   * (asynchronously) read status of the specified Domain
   *
   * @param name name of the Domain (required)
   * @param namespace object name and auth scope, such as for teams and projects (required)
   * @param pretty If &#39;true&#39;, then the output is pretty printed. (optional)
   * @param callback The callback to be executed when the API call finishes
   * @return The request call
   * @throws ApiException If fail to process the API call, e.g. serializing the request body object
   */
  public com.squareup.okhttp.Call readWebLogicOracleV1NamespacedDomainStatusAsync(
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
        readWebLogicOracleV1NamespacedDomainStatusValidateBeforeCall(
            name, namespace, pretty, progressListener, progressRequestListener);
    Type localVarReturnType = new TypeToken<Domain>() {}.getType();
    apiClient.executeAsync(call, localVarReturnType, callback);
    return call;
  }

  /**
   * Build call for replaceWebLogicOracleV1NamespacedDomain
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
  public com.squareup.okhttp.Call replaceWebLogicOracleV1NamespacedDomainCall(
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
        "/apis/weblogic.oracle/v1/namespaces/{namespace}/domains/{name}"
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

  private com.squareup.okhttp.Call replaceWebLogicOracleV1NamespacedDomainValidateBeforeCall(
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
          "Missing the required parameter 'name' when calling replaceWebLogicOracleV1NamespacedDomain(Async)");
    }

    // verify the required parameter 'namespace' is set
    if (namespace == null) {
      throw new ApiException(
          "Missing the required parameter 'namespace' when calling replaceWebLogicOracleV1NamespacedDomain(Async)");
    }

    // verify the required parameter 'body' is set
    if (body == null) {
      throw new ApiException(
          "Missing the required parameter 'body' when calling replaceWebLogicOracleV1NamespacedDomain(Async)");
    }

    com.squareup.okhttp.Call call =
        replaceWebLogicOracleV1NamespacedDomainCall(
            name, namespace, body, pretty, progressListener, progressRequestListener);
    return call;
  }

  /**
   * replace the specified Domain
   *
   * @param name name of the Domain (required)
   * @param namespace object name and auth scope, such as for teams and projects (required)
   * @param body (required)
   * @param pretty If &#39;true&#39;, then the output is pretty printed. (optional)
   * @return Domain
   * @throws ApiException If fail to call the API, e.g. server error or cannot deserialize the
   *     response body
   */
  public Domain replaceWebLogicOracleV1NamespacedDomain(
      String name, String namespace, Domain body, String pretty) throws ApiException {
    ApiResponse<Domain> resp =
        replaceWebLogicOracleV1NamespacedDomainWithHttpInfo(name, namespace, body, pretty);
    return resp.getData();
  }

  /**
   * replace the specified Domain
   *
   * @param name name of the Domain (required)
   * @param namespace object name and auth scope, such as for teams and projects (required)
   * @param body (required)
   * @param pretty If &#39;true&#39;, then the output is pretty printed. (optional)
   * @return ApiResponse&lt;Domain&gt;
   * @throws ApiException If fail to call the API, e.g. server error or cannot deserialize the
   *     response body
   */
  public ApiResponse<Domain> replaceWebLogicOracleV1NamespacedDomainWithHttpInfo(
      String name, String namespace, Domain body, String pretty) throws ApiException {
    com.squareup.okhttp.Call call =
        replaceWebLogicOracleV1NamespacedDomainValidateBeforeCall(
            name, namespace, body, pretty, null, null);
    Type localVarReturnType = new TypeToken<Domain>() {}.getType();
    return apiClient.execute(call, localVarReturnType);
  }

  /**
   * (asynchronously) replace the specified Domain
   *
   * @param name name of the Domain (required)
   * @param namespace object name and auth scope, such as for teams and projects (required)
   * @param body (required)
   * @param pretty If &#39;true&#39;, then the output is pretty printed. (optional)
   * @param callback The callback to be executed when the API call finishes
   * @return The request call
   * @throws ApiException If fail to process the API call, e.g. serializing the request body object
   */
  public com.squareup.okhttp.Call replaceWebLogicOracleV1NamespacedDomainAsync(
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
        replaceWebLogicOracleV1NamespacedDomainValidateBeforeCall(
            name, namespace, body, pretty, progressListener, progressRequestListener);
    Type localVarReturnType = new TypeToken<Domain>() {}.getType();
    apiClient.executeAsync(call, localVarReturnType, callback);
    return call;
  }

  /**
   * Build call for replaceWebLogicOracleV1NamespacedDomainScale
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
  public com.squareup.okhttp.Call replaceWebLogicOracleV1NamespacedDomainScaleCall(
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
        "/apis/weblogic.oracle/v1/namespaces/{namespace}/domains/{name}/scale"
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

  private com.squareup.okhttp.Call replaceWebLogicOracleV1NamespacedDomainScaleValidateBeforeCall(
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
          "Missing the required parameter 'name' when calling replaceWebLogicOracleV1NamespacedDomainScale(Async)");
    }

    // verify the required parameter 'namespace' is set
    if (namespace == null) {
      throw new ApiException(
          "Missing the required parameter 'namespace' when calling replaceWebLogicOracleV1NamespacedDomainScale(Async)");
    }

    // verify the required parameter 'body' is set
    if (body == null) {
      throw new ApiException(
          "Missing the required parameter 'body' when calling replaceWebLogicOracleV1NamespacedDomainScale(Async)");
    }

    com.squareup.okhttp.Call call =
        replaceWebLogicOracleV1NamespacedDomainScaleCall(
            name, namespace, body, pretty, progressListener, progressRequestListener);
    return call;
  }

  /**
   * replace scale of the specified Domain
   *
   * @param name name of the Scale (required)
   * @param namespace object name and auth scope, such as for teams and projects (required)
   * @param body (required)
   * @param pretty If &#39;true&#39;, then the output is pretty printed. (optional)
   * @return V1Scale
   * @throws ApiException If fail to call the API, e.g. server error or cannot deserialize the
   *     response body
   */
  public V1Scale replaceWebLogicOracleV1NamespacedDomainScale(
      String name, String namespace, V1Scale body, String pretty) throws ApiException {
    ApiResponse<V1Scale> resp =
        replaceWebLogicOracleV1NamespacedDomainScaleWithHttpInfo(name, namespace, body, pretty);
    return resp.getData();
  }

  /**
   * replace scale of the specified Domain
   *
   * @param name name of the Scale (required)
   * @param namespace object name and auth scope, such as for teams and projects (required)
   * @param body (required)
   * @param pretty If &#39;true&#39;, then the output is pretty printed. (optional)
   * @return ApiResponse&lt;V1Scale&gt;
   * @throws ApiException If fail to call the API, e.g. server error or cannot deserialize the
   *     response body
   */
  public ApiResponse<V1Scale> replaceWebLogicOracleV1NamespacedDomainScaleWithHttpInfo(
      String name, String namespace, V1Scale body, String pretty) throws ApiException {
    com.squareup.okhttp.Call call =
        replaceWebLogicOracleV1NamespacedDomainScaleValidateBeforeCall(
            name, namespace, body, pretty, null, null);
    Type localVarReturnType = new TypeToken<V1Scale>() {}.getType();
    return apiClient.execute(call, localVarReturnType);
  }

  /**
   * (asynchronously) replace scale of the specified Domain
   *
   * @param name name of the Scale (required)
   * @param namespace object name and auth scope, such as for teams and projects (required)
   * @param body (required)
   * @param pretty If &#39;true&#39;, then the output is pretty printed. (optional)
   * @param callback The callback to be executed when the API call finishes
   * @return The request call
   * @throws ApiException If fail to process the API call, e.g. serializing the request body object
   */
  public com.squareup.okhttp.Call replaceWebLogicOracleV1NamespacedDomainScaleAsync(
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
        replaceWebLogicOracleV1NamespacedDomainScaleValidateBeforeCall(
            name, namespace, body, pretty, progressListener, progressRequestListener);
    Type localVarReturnType = new TypeToken<V1Scale>() {}.getType();
    apiClient.executeAsync(call, localVarReturnType, callback);
    return call;
  }

  /**
   * Build call for replaceWebLogicOracleV1NamespacedDomainStatus
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
  public com.squareup.okhttp.Call replaceWebLogicOracleV1NamespacedDomainStatusCall(
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
        "/apis/weblogic.oracle/v1/namespaces/{namespace}/domains/{name}/status"
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

  private com.squareup.okhttp.Call replaceWebLogicOracleV1NamespacedDomainStatusValidateBeforeCall(
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
          "Missing the required parameter 'name' when calling replaceWebLogicOracleV1NamespacedDomainStatus(Async)");
    }

    // verify the required parameter 'namespace' is set
    if (namespace == null) {
      throw new ApiException(
          "Missing the required parameter 'namespace' when calling replaceWebLogicOracleV1NamespacedDomainStatus(Async)");
    }

    // verify the required parameter 'body' is set
    if (body == null) {
      throw new ApiException(
          "Missing the required parameter 'body' when calling replaceWebLogicOracleV1NamespacedDomainStatus(Async)");
    }

    com.squareup.okhttp.Call call =
        replaceWebLogicOracleV1NamespacedDomainStatusCall(
            name, namespace, body, pretty, progressListener, progressRequestListener);
    return call;
  }

  /**
   * replace status of the specified Domain
   *
   * @param name name of the Domain (required)
   * @param namespace object name and auth scope, such as for teams and projects (required)
   * @param body (required)
   * @param pretty If &#39;true&#39;, then the output is pretty printed. (optional)
   * @return Domain
   * @throws ApiException If fail to call the API, e.g. server error or cannot deserialize the
   *     response body
   */
  public Domain replaceWebLogicOracleV1NamespacedDomainStatus(
      String name, String namespace, Domain body, String pretty) throws ApiException {
    ApiResponse<Domain> resp =
        replaceWebLogicOracleV1NamespacedDomainStatusWithHttpInfo(name, namespace, body, pretty);
    return resp.getData();
  }

  /**
   * replace status of the specified Domain
   *
   * @param name name of the Domain (required)
   * @param namespace object name and auth scope, such as for teams and projects (required)
   * @param body (required)
   * @param pretty If &#39;true&#39;, then the output is pretty printed. (optional)
   * @return ApiResponse&lt;Domain&gt;
   * @throws ApiException If fail to call the API, e.g. server error or cannot deserialize the
   *     response body
   */
  public ApiResponse<Domain> replaceWebLogicOracleV1NamespacedDomainStatusWithHttpInfo(
      String name, String namespace, Domain body, String pretty) throws ApiException {
    com.squareup.okhttp.Call call =
        replaceWebLogicOracleV1NamespacedDomainStatusValidateBeforeCall(
            name, namespace, body, pretty, null, null);
    Type localVarReturnType = new TypeToken<Domain>() {}.getType();
    return apiClient.execute(call, localVarReturnType);
  }

  /**
   * (asynchronously) replace status of the specified Domain
   *
   * @param name name of the Domain (required)
   * @param namespace object name and auth scope, such as for teams and projects (required)
   * @param body (required)
   * @param pretty If &#39;true&#39;, then the output is pretty printed. (optional)
   * @param callback The callback to be executed when the API call finishes
   * @return The request call
   * @throws ApiException If fail to process the API call, e.g. serializing the request body object
   */
  public com.squareup.okhttp.Call replaceWebLogicOracleV1NamespacedDomainStatusAsync(
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
        replaceWebLogicOracleV1NamespacedDomainStatusValidateBeforeCall(
            name, namespace, body, pretty, progressListener, progressRequestListener);
    Type localVarReturnType = new TypeToken<Domain>() {}.getType();
    apiClient.executeAsync(call, localVarReturnType, callback);
    return call;
  }

  /**
   * Build call for watchWebLogicOracleV1DomainListForAllNamespaces
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
  public com.squareup.okhttp.Call watchWebLogicOracleV1DomainListForAllNamespacesCall(
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
    String localVarPath = "/apis/weblogic.oracle/v1/watch/domains";

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

  private com.squareup.okhttp.Call
      watchWebLogicOracleV1DomainListForAllNamespacesValidateBeforeCall(
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
        watchWebLogicOracleV1DomainListForAllNamespacesCall(
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
   * watch individual changes to a list of Domain
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
  public V1WatchEvent watchWebLogicOracleV1DomainListForAllNamespaces(
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
        watchWebLogicOracleV1DomainListForAllNamespacesWithHttpInfo(
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
   * watch individual changes to a list of Domain
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
  public ApiResponse<V1WatchEvent> watchWebLogicOracleV1DomainListForAllNamespacesWithHttpInfo(
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
        watchWebLogicOracleV1DomainListForAllNamespacesValidateBeforeCall(
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
   * (asynchronously) watch individual changes to a list of Domain
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
  public com.squareup.okhttp.Call watchWebLogicOracleV1DomainListForAllNamespacesAsync(
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
        watchWebLogicOracleV1DomainListForAllNamespacesValidateBeforeCall(
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
   * Build call for watchWebLogicOracleV1NamespacedDomain
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
  public com.squareup.okhttp.Call watchWebLogicOracleV1NamespacedDomainCall(
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
        "/apis/weblogic.oracle/v1/watch/namespaces/{namespace}/domains/{name}"
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

  private com.squareup.okhttp.Call watchWebLogicOracleV1NamespacedDomainValidateBeforeCall(
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
          "Missing the required parameter 'name' when calling watchWebLogicOracleV1NamespacedDomain(Async)");
    }

    // verify the required parameter 'namespace' is set
    if (namespace == null) {
      throw new ApiException(
          "Missing the required parameter 'namespace' when calling watchWebLogicOracleV1NamespacedDomain(Async)");
    }

    com.squareup.okhttp.Call call =
        watchWebLogicOracleV1NamespacedDomainCall(
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
   * watch changes to an object of kind Domain
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
  public V1WatchEvent watchWebLogicOracleV1NamespacedDomain(
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
        watchWebLogicOracleV1NamespacedDomainWithHttpInfo(
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
   * watch changes to an object of kind Domain
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
  public ApiResponse<V1WatchEvent> watchWebLogicOracleV1NamespacedDomainWithHttpInfo(
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
        watchWebLogicOracleV1NamespacedDomainValidateBeforeCall(
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
   * (asynchronously) watch changes to an object of kind Domain
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
  public com.squareup.okhttp.Call watchWebLogicOracleV1NamespacedDomainAsync(
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
        watchWebLogicOracleV1NamespacedDomainValidateBeforeCall(
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
   * Build call for watchWebLogicOracleV1NamespacedDomainList
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
  public com.squareup.okhttp.Call watchWebLogicOracleV1NamespacedDomainListCall(
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
        "/apis/weblogic.oracle/v1/watch/namespaces/{namespace}/domains"
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

  private com.squareup.okhttp.Call watchWebLogicOracleV1NamespacedDomainListValidateBeforeCall(
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
          "Missing the required parameter 'namespace' when calling watchWebLogicOracleV1NamespacedDomainList(Async)");
    }

    com.squareup.okhttp.Call call =
        watchWebLogicOracleV1NamespacedDomainListCall(
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
   * watch individual changes to a list of Domain
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
  public V1WatchEvent watchWebLogicOracleV1NamespacedDomainList(
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
        watchWebLogicOracleV1NamespacedDomainListWithHttpInfo(
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
   * watch individual changes to a list of Domain
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
  public ApiResponse<V1WatchEvent> watchWebLogicOracleV1NamespacedDomainListWithHttpInfo(
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
        watchWebLogicOracleV1NamespacedDomainListValidateBeforeCall(
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
   * (asynchronously) watch individual changes to a list of Domain
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
  public com.squareup.okhttp.Call watchWebLogicOracleV1NamespacedDomainListAsync(
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
        watchWebLogicOracleV1NamespacedDomainListValidateBeforeCall(
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

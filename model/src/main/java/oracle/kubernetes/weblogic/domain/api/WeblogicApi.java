// Copyright 2017, 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.weblogic.domain.api;

import static oracle.kubernetes.operator.KubernetesConstants.DOMAIN_GROUP;
import static oracle.kubernetes.operator.KubernetesConstants.DOMAIN_PLURAL;
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
import io.kubernetes.client.models.V1Status;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import oracle.kubernetes.weblogic.domain.model.Domain;
import oracle.kubernetes.weblogic.domain.model.DomainList;

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
   * Build call for createNamespacedDomain
   *
   * @param namespace object name and auth scope, such as for teams and projects (required)
   * @param body (required)
   * @param includeUninitialized If true, partially initialized resources are included in the
   *     response. (optional)
   * @param pretty If &#39;true&#39;, then the output is pretty printed. (optional)
   * @param dryRun When present, indicates that modifications should not be persisted. An invalid or
   *     unrecognized dryRun directive will result in an error response and no further processing of
   *     the request. Valid values are: - All: all dry run stages will be processed (optional)
   * @param progressListener Progress listener
   * @param progressRequestListener Progress request listener
   * @return Call to execute
   * @throws ApiException If fail to serialize the request body object
   */
  public com.squareup.okhttp.Call createNamespacedDomainCall(
      String namespace,
      Domain body,
      Boolean includeUninitialized,
      String pretty,
      String dryRun,
      final ProgressResponseBody.ProgressListener progressListener,
      final ProgressRequestBody.ProgressRequestListener progressRequestListener)
      throws ApiException {
    Object localVarPostBody = body;

    // create path and map variables
    String localVarPath =
        ("/apis/"
                + DOMAIN_GROUP
                + "/"
                + DOMAIN_VERSION
                + "/namespaces/{namespace}/"
                + DOMAIN_PLURAL)
            .replaceAll("\\{" + "namespace" + "\\}", apiClient.escapeString(namespace.toString()));

    List<Pair> localVarQueryParams = new ArrayList<Pair>();
    List<Pair> localVarCollectionQueryParams = new ArrayList<Pair>();
    if (includeUninitialized != null)
      localVarQueryParams.addAll(
          apiClient.parameterToPair("includeUninitialized", includeUninitialized));
    if (pretty != null) localVarQueryParams.addAll(apiClient.parameterToPair("pretty", pretty));
    if (dryRun != null) localVarQueryParams.addAll(apiClient.parameterToPair("dryRun", dryRun));

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
              new com.squareup.okhttp.Interceptor() {
                @Override
                public com.squareup.okhttp.Response intercept(
                    com.squareup.okhttp.Interceptor.Chain chain) throws IOException {
                  com.squareup.okhttp.Response originalResponse = chain.proceed(chain.request());
                  return originalResponse
                      .newBuilder()
                      .body(new ProgressResponseBody(originalResponse.body(), progressListener))
                      .build();
                }
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

  @SuppressWarnings("rawtypes")
  private com.squareup.okhttp.Call createNamespacedDomainValidateBeforeCall(
      String namespace,
      Domain body,
      Boolean includeUninitialized,
      String pretty,
      String dryRun,
      final ProgressResponseBody.ProgressListener progressListener,
      final ProgressRequestBody.ProgressRequestListener progressRequestListener)
      throws ApiException {

    // verify the required parameter 'namespace' is set
    if (namespace == null) {
      throw new ApiException(
          "Missing the required parameter 'namespace' when calling createNamespacedDomain(Async)");
    }

    // verify the required parameter 'body' is set
    if (body == null) {
      throw new ApiException(
          "Missing the required parameter 'body' when calling createNamespacedDomain(Async)");
    }

    com.squareup.okhttp.Call call =
        createNamespacedDomainCall(
            namespace,
            body,
            includeUninitialized,
            pretty,
            dryRun,
            progressListener,
            progressRequestListener);
    return call;
  }

  /**
   * create a Domain
   *
   * @param namespace object name and auth scope, such as for teams and projects (required)
   * @param body (required)
   * @param includeUninitialized If true, partially initialized resources are included in the
   *     response. (optional)
   * @param pretty If &#39;true&#39;, then the output is pretty printed. (optional)
   * @param dryRun When present, indicates that modifications should not be persisted. An invalid or
   *     unrecognized dryRun directive will result in an error response and no further processing of
   *     the request. Valid values are: - All: all dry run stages will be processed (optional)
   * @return Domain
   * @throws ApiException If fail to call the API, e.g. server error or cannot deserialize the
   *     response body
   */
  public Domain createNamespacedDomain(
      String namespace, Domain body, Boolean includeUninitialized, String pretty, String dryRun)
      throws ApiException {
    ApiResponse<Domain> resp =
        createNamespacedDomainWithHttpInfo(namespace, body, includeUninitialized, pretty, dryRun);
    return resp.getData();
  }

  /**
   * create a Domain
   *
   * @param namespace object name and auth scope, such as for teams and projects (required)
   * @param body (required)
   * @param includeUninitialized If true, partially initialized resources are included in the
   *     response. (optional)
   * @param pretty If &#39;true&#39;, then the output is pretty printed. (optional)
   * @param dryRun When present, indicates that modifications should not be persisted. An invalid or
   *     unrecognized dryRun directive will result in an error response and no further processing of
   *     the request. Valid values are: - All: all dry run stages will be processed (optional)
   * @return ApiResponse&lt;Domain&gt;
   * @throws ApiException If fail to call the API, e.g. server error or cannot deserialize the
   *     response body
   */
  public ApiResponse<Domain> createNamespacedDomainWithHttpInfo(
      String namespace, Domain body, Boolean includeUninitialized, String pretty, String dryRun)
      throws ApiException {
    com.squareup.okhttp.Call call =
        createNamespacedDomainValidateBeforeCall(
            namespace, body, includeUninitialized, pretty, dryRun, null, null);
    Type localVarReturnType = new TypeToken<Domain>() {}.getType();
    return apiClient.execute(call, localVarReturnType);
  }

  /**
   * (asynchronously) create a Domain
   *
   * @param namespace object name and auth scope, such as for teams and projects (required)
   * @param body (required)
   * @param includeUninitialized If true, partially initialized resources are included in the
   *     response. (optional)
   * @param pretty If &#39;true&#39;, then the output is pretty printed. (optional)
   * @param dryRun When present, indicates that modifications should not be persisted. An invalid or
   *     unrecognized dryRun directive will result in an error response and no further processing of
   *     the request. Valid values are: - All: all dry run stages will be processed (optional)
   * @param callback The callback to be executed when the API call finishes
   * @return The request call
   * @throws ApiException If fail to process the API call, e.g. serializing the request body object
   */
  public com.squareup.okhttp.Call createNamespacedDomainAsync(
      String namespace,
      Domain body,
      Boolean includeUninitialized,
      String pretty,
      String dryRun,
      final ApiCallback<Domain> callback)
      throws ApiException {

    ProgressResponseBody.ProgressListener progressListener = null;
    ProgressRequestBody.ProgressRequestListener progressRequestListener = null;

    if (callback != null) {
      progressListener =
          new ProgressResponseBody.ProgressListener() {
            @Override
            public void update(long bytesRead, long contentLength, boolean done) {
              callback.onDownloadProgress(bytesRead, contentLength, done);
            }
          };

      progressRequestListener =
          new ProgressRequestBody.ProgressRequestListener() {
            @Override
            public void onRequestProgress(long bytesWritten, long contentLength, boolean done) {
              callback.onUploadProgress(bytesWritten, contentLength, done);
            }
          };
    }

    com.squareup.okhttp.Call call =
        createNamespacedDomainValidateBeforeCall(
            namespace,
            body,
            includeUninitialized,
            pretty,
            dryRun,
            progressListener,
            progressRequestListener);
    Type localVarReturnType = new TypeToken<Domain>() {}.getType();
    apiClient.executeAsync(call, localVarReturnType, callback);
    return call;
  }

  /**
   * Build call for deleteCollectionNamespacedDomain
   *
   * @param namespace object name and auth scope, such as for teams and projects (required)
   * @param includeUninitialized If true, partially initialized resources are included in the
   *     response. (optional)
   * @param pretty If &#39;true&#39;, then the output is pretty printed. (optional)
   * @param _continue The continue option should be set when retrieving more results from the
   *     server. Since this value is server defined, clients may only use the continue value from a
   *     previous query result with identical query parameters (except for the value of continue)
   *     and the server may reject a continue value it does not recognize. If the specified continue
   *     value is no longer valid whether due to expiration (generally five to fifteen minutes) or a
   *     configuration change on the server, the server will respond with a 410 ResourceExpired
   *     error together with a continue token. If the client needs a consistent list, it must
   *     restart their list without the continue field. Otherwise, the client may send another list
   *     request with the token received with the 410 error, the server will respond with a list
   *     starting from the next key, but from the latest snapshot, which is inconsistent from the
   *     previous list results - objects that are created, modified, or deleted after the first list
   *     request will be included in the response, as long as their keys are after the \&quot;next
   *     key\&quot;. This field is not supported when watch is true. Clients may start a watch from
   *     the last resourceVersion value returned by the server and not miss any modifications.
   *     (optional)
   * @param fieldSelector A selector to restrict the list of returned objects by their fields.
   *     Defaults to everything. (optional)
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
   * @param timeoutSeconds Timeout for the list/watch call. This limits the duration of the call,
   *     regardless of any activity or inactivity. (optional)
   * @param watch Watch for changes to the described resources and return them as a stream of add,
   *     update, and remove notifications. Specify resourceVersion. (optional)
   * @param progressListener Progress listener
   * @param progressRequestListener Progress request listener
   * @return Call to execute
   * @throws ApiException If fail to serialize the request body object
   */
  public com.squareup.okhttp.Call deleteCollectionNamespacedDomainCall(
      String namespace,
      Boolean includeUninitialized,
      String pretty,
      String _continue,
      String fieldSelector,
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
        ("/apis/"
                + DOMAIN_GROUP
                + "/"
                + DOMAIN_VERSION
                + "/namespaces/{namespace}/"
                + DOMAIN_PLURAL)
            .replaceAll("\\{" + "namespace" + "\\}", apiClient.escapeString(namespace.toString()));

    List<Pair> localVarQueryParams = new ArrayList<Pair>();
    List<Pair> localVarCollectionQueryParams = new ArrayList<Pair>();
    if (includeUninitialized != null)
      localVarQueryParams.addAll(
          apiClient.parameterToPair("includeUninitialized", includeUninitialized));
    if (pretty != null) localVarQueryParams.addAll(apiClient.parameterToPair("pretty", pretty));
    if (_continue != null)
      localVarQueryParams.addAll(apiClient.parameterToPair("continue", _continue));
    if (fieldSelector != null)
      localVarQueryParams.addAll(apiClient.parameterToPair("fieldSelector", fieldSelector));
    if (labelSelector != null)
      localVarQueryParams.addAll(apiClient.parameterToPair("labelSelector", labelSelector));
    if (limit != null) localVarQueryParams.addAll(apiClient.parameterToPair("limit", limit));
    if (resourceVersion != null)
      localVarQueryParams.addAll(apiClient.parameterToPair("resourceVersion", resourceVersion));
    if (timeoutSeconds != null)
      localVarQueryParams.addAll(apiClient.parameterToPair("timeoutSeconds", timeoutSeconds));
    if (watch != null) localVarQueryParams.addAll(apiClient.parameterToPair("watch", watch));

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
              new com.squareup.okhttp.Interceptor() {
                @Override
                public com.squareup.okhttp.Response intercept(
                    com.squareup.okhttp.Interceptor.Chain chain) throws IOException {
                  com.squareup.okhttp.Response originalResponse = chain.proceed(chain.request());
                  return originalResponse
                      .newBuilder()
                      .body(new ProgressResponseBody(originalResponse.body(), progressListener))
                      .build();
                }
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

  @SuppressWarnings("rawtypes")
  private com.squareup.okhttp.Call deleteCollectionNamespacedDomainValidateBeforeCall(
      String namespace,
      Boolean includeUninitialized,
      String pretty,
      String _continue,
      String fieldSelector,
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
          "Missing the required parameter 'namespace' when calling deleteCollectionNamespacedDomain(Async)");
    }

    com.squareup.okhttp.Call call =
        deleteCollectionNamespacedDomainCall(
            namespace,
            includeUninitialized,
            pretty,
            _continue,
            fieldSelector,
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
   * @param includeUninitialized If true, partially initialized resources are included in the
   *     response. (optional)
   * @param pretty If &#39;true&#39;, then the output is pretty printed. (optional)
   * @param _continue The continue option should be set when retrieving more results from the
   *     server. Since this value is server defined, clients may only use the continue value from a
   *     previous query result with identical query parameters (except for the value of continue)
   *     and the server may reject a continue value it does not recognize. If the specified continue
   *     value is no longer valid whether due to expiration (generally five to fifteen minutes) or a
   *     configuration change on the server, the server will respond with a 410 ResourceExpired
   *     error together with a continue token. If the client needs a consistent list, it must
   *     restart their list without the continue field. Otherwise, the client may send another list
   *     request with the token received with the 410 error, the server will respond with a list
   *     starting from the next key, but from the latest snapshot, which is inconsistent from the
   *     previous list results - objects that are created, modified, or deleted after the first list
   *     request will be included in the response, as long as their keys are after the \&quot;next
   *     key\&quot;. This field is not supported when watch is true. Clients may start a watch from
   *     the last resourceVersion value returned by the server and not miss any modifications.
   *     (optional)
   * @param fieldSelector A selector to restrict the list of returned objects by their fields.
   *     Defaults to everything. (optional)
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
   * @param timeoutSeconds Timeout for the list/watch call. This limits the duration of the call,
   *     regardless of any activity or inactivity. (optional)
   * @param watch Watch for changes to the described resources and return them as a stream of add,
   *     update, and remove notifications. Specify resourceVersion. (optional)
   * @return V1Status
   * @throws ApiException If fail to call the API, e.g. server error or cannot deserialize the
   *     response body
   */
  public V1Status deleteCollectionNamespacedDomain(
      String namespace,
      Boolean includeUninitialized,
      String pretty,
      String _continue,
      String fieldSelector,
      String labelSelector,
      Integer limit,
      String resourceVersion,
      Integer timeoutSeconds,
      Boolean watch)
      throws ApiException {
    ApiResponse<V1Status> resp =
        deleteCollectionNamespacedDomainWithHttpInfo(
            namespace,
            includeUninitialized,
            pretty,
            _continue,
            fieldSelector,
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
   * @param includeUninitialized If true, partially initialized resources are included in the
   *     response. (optional)
   * @param pretty If &#39;true&#39;, then the output is pretty printed. (optional)
   * @param _continue The continue option should be set when retrieving more results from the
   *     server. Since this value is server defined, clients may only use the continue value from a
   *     previous query result with identical query parameters (except for the value of continue)
   *     and the server may reject a continue value it does not recognize. If the specified continue
   *     value is no longer valid whether due to expiration (generally five to fifteen minutes) or a
   *     configuration change on the server, the server will respond with a 410 ResourceExpired
   *     error together with a continue token. If the client needs a consistent list, it must
   *     restart their list without the continue field. Otherwise, the client may send another list
   *     request with the token received with the 410 error, the server will respond with a list
   *     starting from the next key, but from the latest snapshot, which is inconsistent from the
   *     previous list results - objects that are created, modified, or deleted after the first list
   *     request will be included in the response, as long as their keys are after the \&quot;next
   *     key\&quot;. This field is not supported when watch is true. Clients may start a watch from
   *     the last resourceVersion value returned by the server and not miss any modifications.
   *     (optional)
   * @param fieldSelector A selector to restrict the list of returned objects by their fields.
   *     Defaults to everything. (optional)
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
   * @param timeoutSeconds Timeout for the list/watch call. This limits the duration of the call,
   *     regardless of any activity or inactivity. (optional)
   * @param watch Watch for changes to the described resources and return them as a stream of add,
   *     update, and remove notifications. Specify resourceVersion. (optional)
   * @return ApiResponse&lt;V1Status&gt;
   * @throws ApiException If fail to call the API, e.g. server error or cannot deserialize the
   *     response body
   */
  public ApiResponse<V1Status> deleteCollectionNamespacedDomainWithHttpInfo(
      String namespace,
      Boolean includeUninitialized,
      String pretty,
      String _continue,
      String fieldSelector,
      String labelSelector,
      Integer limit,
      String resourceVersion,
      Integer timeoutSeconds,
      Boolean watch)
      throws ApiException {
    com.squareup.okhttp.Call call =
        deleteCollectionNamespacedDomainValidateBeforeCall(
            namespace,
            includeUninitialized,
            pretty,
            _continue,
            fieldSelector,
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
   * @param includeUninitialized If true, partially initialized resources are included in the
   *     response. (optional)
   * @param pretty If &#39;true&#39;, then the output is pretty printed. (optional)
   * @param _continue The continue option should be set when retrieving more results from the
   *     server. Since this value is server defined, clients may only use the continue value from a
   *     previous query result with identical query parameters (except for the value of continue)
   *     and the server may reject a continue value it does not recognize. If the specified continue
   *     value is no longer valid whether due to expiration (generally five to fifteen minutes) or a
   *     configuration change on the server, the server will respond with a 410 ResourceExpired
   *     error together with a continue token. If the client needs a consistent list, it must
   *     restart their list without the continue field. Otherwise, the client may send another list
   *     request with the token received with the 410 error, the server will respond with a list
   *     starting from the next key, but from the latest snapshot, which is inconsistent from the
   *     previous list results - objects that are created, modified, or deleted after the first list
   *     request will be included in the response, as long as their keys are after the \&quot;next
   *     key\&quot;. This field is not supported when watch is true. Clients may start a watch from
   *     the last resourceVersion value returned by the server and not miss any modifications.
   *     (optional)
   * @param fieldSelector A selector to restrict the list of returned objects by their fields.
   *     Defaults to everything. (optional)
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
   * @param timeoutSeconds Timeout for the list/watch call. This limits the duration of the call,
   *     regardless of any activity or inactivity. (optional)
   * @param watch Watch for changes to the described resources and return them as a stream of add,
   *     update, and remove notifications. Specify resourceVersion. (optional)
   * @param callback The callback to be executed when the API call finishes
   * @return The request call
   * @throws ApiException If fail to process the API call, e.g. serializing the request body object
   */
  public com.squareup.okhttp.Call deleteCollectionNamespacedDomainAsync(
      String namespace,
      Boolean includeUninitialized,
      String pretty,
      String _continue,
      String fieldSelector,
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
          new ProgressResponseBody.ProgressListener() {
            @Override
            public void update(long bytesRead, long contentLength, boolean done) {
              callback.onDownloadProgress(bytesRead, contentLength, done);
            }
          };

      progressRequestListener =
          new ProgressRequestBody.ProgressRequestListener() {
            @Override
            public void onRequestProgress(long bytesWritten, long contentLength, boolean done) {
              callback.onUploadProgress(bytesWritten, contentLength, done);
            }
          };
    }

    com.squareup.okhttp.Call call =
        deleteCollectionNamespacedDomainValidateBeforeCall(
            namespace,
            includeUninitialized,
            pretty,
            _continue,
            fieldSelector,
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
   * Build call for deleteNamespacedDomain
   *
   * @param name name of the Domain (required)
   * @param namespace object name and auth scope, such as for teams and projects (required)
   * @param body (required)
   * @param pretty If &#39;true&#39;, then the output is pretty printed. (optional)
   * @param dryRun When present, indicates that modifications should not be persisted. An invalid or
   *     unrecognized dryRun directive will result in an error response and no further processing of
   *     the request. Valid values are: - All: all dry run stages will be processed (optional)
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
   *     Acceptable values are: &#39;Orphan&#39; - orphan the dependents; &#39;Background&#39; -
   *     allow the garbage collector to delete the dependents in the background;
   *     &#39;Foreground&#39; - a cascading policy that deletes all dependents in the foreground.
   *     (optional)
   * @param progressListener Progress listener
   * @param progressRequestListener Progress request listener
   * @return Call to execute
   * @throws ApiException If fail to serialize the request body object
   */
  public com.squareup.okhttp.Call deleteNamespacedDomainCall(
      String name,
      String namespace,
      V1DeleteOptions body,
      String pretty,
      String dryRun,
      Integer gracePeriodSeconds,
      Boolean orphanDependents,
      String propagationPolicy,
      final ProgressResponseBody.ProgressListener progressListener,
      final ProgressRequestBody.ProgressRequestListener progressRequestListener)
      throws ApiException {
    Object localVarPostBody = body;

    // create path and map variables
    String localVarPath =
        ("/apis/"
                + DOMAIN_GROUP
                + "/"
                + DOMAIN_VERSION
                + "/namespaces/{namespace}/"
                + DOMAIN_PLURAL
                + "/{name}")
            .replaceAll("\\{" + "name" + "\\}", apiClient.escapeString(name.toString()))
            .replaceAll("\\{" + "namespace" + "\\}", apiClient.escapeString(namespace.toString()));

    List<Pair> localVarQueryParams = new ArrayList<Pair>();
    List<Pair> localVarCollectionQueryParams = new ArrayList<Pair>();
    if (pretty != null) localVarQueryParams.addAll(apiClient.parameterToPair("pretty", pretty));
    if (dryRun != null) localVarQueryParams.addAll(apiClient.parameterToPair("dryRun", dryRun));
    if (gracePeriodSeconds != null)
      localVarQueryParams.addAll(
          apiClient.parameterToPair("gracePeriodSeconds", gracePeriodSeconds));
    if (orphanDependents != null)
      localVarQueryParams.addAll(apiClient.parameterToPair("orphanDependents", orphanDependents));
    if (propagationPolicy != null)
      localVarQueryParams.addAll(apiClient.parameterToPair("propagationPolicy", propagationPolicy));

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
              new com.squareup.okhttp.Interceptor() {
                @Override
                public com.squareup.okhttp.Response intercept(
                    com.squareup.okhttp.Interceptor.Chain chain) throws IOException {
                  com.squareup.okhttp.Response originalResponse = chain.proceed(chain.request());
                  return originalResponse
                      .newBuilder()
                      .body(new ProgressResponseBody(originalResponse.body(), progressListener))
                      .build();
                }
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

  @SuppressWarnings("rawtypes")
  private com.squareup.okhttp.Call deleteNamespacedDomainValidateBeforeCall(
      String name,
      String namespace,
      V1DeleteOptions body,
      String pretty,
      String dryRun,
      Integer gracePeriodSeconds,
      Boolean orphanDependents,
      String propagationPolicy,
      final ProgressResponseBody.ProgressListener progressListener,
      final ProgressRequestBody.ProgressRequestListener progressRequestListener)
      throws ApiException {

    // verify the required parameter 'name' is set
    if (name == null) {
      throw new ApiException(
          "Missing the required parameter 'name' when calling deleteNamespacedDomain(Async)");
    }

    // verify the required parameter 'namespace' is set
    if (namespace == null) {
      throw new ApiException(
          "Missing the required parameter 'namespace' when calling deleteNamespacedDomain(Async)");
    }

    // verify the required parameter 'body' is set
    if (body == null) {
      throw new ApiException(
          "Missing the required parameter 'body' when calling deleteNamespacedDomain(Async)");
    }

    com.squareup.okhttp.Call call =
        deleteNamespacedDomainCall(
            name,
            namespace,
            body,
            pretty,
            dryRun,
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
   * @param dryRun When present, indicates that modifications should not be persisted. An invalid or
   *     unrecognized dryRun directive will result in an error response and no further processing of
   *     the request. Valid values are: - All: all dry run stages will be processed (optional)
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
   *     Acceptable values are: &#39;Orphan&#39; - orphan the dependents; &#39;Background&#39; -
   *     allow the garbage collector to delete the dependents in the background;
   *     &#39;Foreground&#39; - a cascading policy that deletes all dependents in the foreground.
   *     (optional)
   * @return V1Status
   * @throws ApiException If fail to call the API, e.g. server error or cannot deserialize the
   *     response body
   */
  public V1Status deleteNamespacedDomain(
      String name,
      String namespace,
      V1DeleteOptions body,
      String pretty,
      String dryRun,
      Integer gracePeriodSeconds,
      Boolean orphanDependents,
      String propagationPolicy)
      throws ApiException {
    ApiResponse<V1Status> resp =
        deleteNamespacedDomainWithHttpInfo(
            name,
            namespace,
            body,
            pretty,
            dryRun,
            gracePeriodSeconds,
            orphanDependents,
            propagationPolicy);
    return resp.getData();
  }

  /**
   * delete a Domain
   *
   * @param name name of the Domain (required)
   * @param namespace object name and auth scope, such as for teams and projects (required)
   * @param body (required)
   * @param pretty If &#39;true&#39;, then the output is pretty printed. (optional)
   * @param dryRun When present, indicates that modifications should not be persisted. An invalid or
   *     unrecognized dryRun directive will result in an error response and no further processing of
   *     the request. Valid values are: - All: all dry run stages will be processed (optional)
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
   *     Acceptable values are: &#39;Orphan&#39; - orphan the dependents; &#39;Background&#39; -
   *     allow the garbage collector to delete the dependents in the background;
   *     &#39;Foreground&#39; - a cascading policy that deletes all dependents in the foreground.
   *     (optional)
   * @return ApiResponse&lt;V1Status&gt;
   * @throws ApiException If fail to call the API, e.g. server error or cannot deserialize the
   *     response body
   */
  public ApiResponse<V1Status> deleteNamespacedDomainWithHttpInfo(
      String name,
      String namespace,
      V1DeleteOptions body,
      String pretty,
      String dryRun,
      Integer gracePeriodSeconds,
      Boolean orphanDependents,
      String propagationPolicy)
      throws ApiException {
    com.squareup.okhttp.Call call =
        deleteNamespacedDomainValidateBeforeCall(
            name,
            namespace,
            body,
            pretty,
            dryRun,
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
   * @param dryRun When present, indicates that modifications should not be persisted. An invalid or
   *     unrecognized dryRun directive will result in an error response and no further processing of
   *     the request. Valid values are: - All: all dry run stages will be processed (optional)
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
   *     Acceptable values are: &#39;Orphan&#39; - orphan the dependents; &#39;Background&#39; -
   *     allow the garbage collector to delete the dependents in the background;
   *     &#39;Foreground&#39; - a cascading policy that deletes all dependents in the foreground.
   *     (optional)
   * @param callback The callback to be executed when the API call finishes
   * @return The request call
   * @throws ApiException If fail to process the API call, e.g. serializing the request body object
   */
  public com.squareup.okhttp.Call deleteNamespacedDomainAsync(
      String name,
      String namespace,
      V1DeleteOptions body,
      String pretty,
      String dryRun,
      Integer gracePeriodSeconds,
      Boolean orphanDependents,
      String propagationPolicy,
      final ApiCallback<V1Status> callback)
      throws ApiException {

    ProgressResponseBody.ProgressListener progressListener = null;
    ProgressRequestBody.ProgressRequestListener progressRequestListener = null;

    if (callback != null) {
      progressListener =
          new ProgressResponseBody.ProgressListener() {
            @Override
            public void update(long bytesRead, long contentLength, boolean done) {
              callback.onDownloadProgress(bytesRead, contentLength, done);
            }
          };

      progressRequestListener =
          new ProgressRequestBody.ProgressRequestListener() {
            @Override
            public void onRequestProgress(long bytesWritten, long contentLength, boolean done) {
              callback.onUploadProgress(bytesWritten, contentLength, done);
            }
          };
    }

    com.squareup.okhttp.Call call =
        deleteNamespacedDomainValidateBeforeCall(
            name,
            namespace,
            body,
            pretty,
            dryRun,
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
   * Build call for listNamespacedDomain
   *
   * @param namespace object name and auth scope, such as for teams and projects (required)
   * @param includeUninitialized If true, partially initialized resources are included in the
   *     response. (optional)
   * @param pretty If &#39;true&#39;, then the output is pretty printed. (optional)
   * @param _continue The continue option should be set when retrieving more results from the
   *     server. Since this value is server defined, clients may only use the continue value from a
   *     previous query result with identical query parameters (except for the value of continue)
   *     and the server may reject a continue value it does not recognize. If the specified continue
   *     value is no longer valid whether due to expiration (generally five to fifteen minutes) or a
   *     configuration change on the server, the server will respond with a 410 ResourceExpired
   *     error together with a continue token. If the client needs a consistent list, it must
   *     restart their list without the continue field. Otherwise, the client may send another list
   *     request with the token received with the 410 error, the server will respond with a list
   *     starting from the next key, but from the latest snapshot, which is inconsistent from the
   *     previous list results - objects that are created, modified, or deleted after the first list
   *     request will be included in the response, as long as their keys are after the \&quot;next
   *     key\&quot;. This field is not supported when watch is true. Clients may start a watch from
   *     the last resourceVersion value returned by the server and not miss any modifications.
   *     (optional)
   * @param fieldSelector A selector to restrict the list of returned objects by their fields.
   *     Defaults to everything. (optional)
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
   * @param timeoutSeconds Timeout for the list/watch call. This limits the duration of the call,
   *     regardless of any activity or inactivity. (optional)
   * @param watch Watch for changes to the described resources and return them as a stream of add,
   *     update, and remove notifications. Specify resourceVersion. (optional)
   * @param progressListener Progress listener
   * @param progressRequestListener Progress request listener
   * @return Call to execute
   * @throws ApiException If fail to serialize the request body object
   */
  public com.squareup.okhttp.Call listNamespacedDomainCall(
      String namespace,
      Boolean includeUninitialized,
      String pretty,
      String _continue,
      String fieldSelector,
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
        ("/apis/"
                + DOMAIN_GROUP
                + "/"
                + DOMAIN_VERSION
                + "/namespaces/{namespace}/"
                + DOMAIN_PLURAL)
            .replaceAll("\\{" + "namespace" + "\\}", apiClient.escapeString(namespace.toString()));

    List<Pair> localVarQueryParams = new ArrayList<Pair>();
    List<Pair> localVarCollectionQueryParams = new ArrayList<Pair>();
    if (includeUninitialized != null)
      localVarQueryParams.addAll(
          apiClient.parameterToPair("includeUninitialized", includeUninitialized));
    if (pretty != null) localVarQueryParams.addAll(apiClient.parameterToPair("pretty", pretty));
    if (_continue != null)
      localVarQueryParams.addAll(apiClient.parameterToPair("continue", _continue));
    if (fieldSelector != null)
      localVarQueryParams.addAll(apiClient.parameterToPair("fieldSelector", fieldSelector));
    if (labelSelector != null)
      localVarQueryParams.addAll(apiClient.parameterToPair("labelSelector", labelSelector));
    if (limit != null) localVarQueryParams.addAll(apiClient.parameterToPair("limit", limit));
    if (resourceVersion != null)
      localVarQueryParams.addAll(apiClient.parameterToPair("resourceVersion", resourceVersion));
    if (timeoutSeconds != null)
      localVarQueryParams.addAll(apiClient.parameterToPair("timeoutSeconds", timeoutSeconds));
    if (watch != null) localVarQueryParams.addAll(apiClient.parameterToPair("watch", watch));

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
              new com.squareup.okhttp.Interceptor() {
                @Override
                public com.squareup.okhttp.Response intercept(
                    com.squareup.okhttp.Interceptor.Chain chain) throws IOException {
                  com.squareup.okhttp.Response originalResponse = chain.proceed(chain.request());
                  return originalResponse
                      .newBuilder()
                      .body(new ProgressResponseBody(originalResponse.body(), progressListener))
                      .build();
                }
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

  @SuppressWarnings("rawtypes")
  private com.squareup.okhttp.Call listNamespacedDomainValidateBeforeCall(
      String namespace,
      Boolean includeUninitialized,
      String pretty,
      String _continue,
      String fieldSelector,
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
          "Missing the required parameter 'namespace' when calling listNamespacedDomain(Async)");
    }

    com.squareup.okhttp.Call call =
        listNamespacedDomainCall(
            namespace,
            includeUninitialized,
            pretty,
            _continue,
            fieldSelector,
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
   * @param includeUninitialized If true, partially initialized resources are included in the
   *     response. (optional)
   * @param pretty If &#39;true&#39;, then the output is pretty printed. (optional)
   * @param _continue The continue option should be set when retrieving more results from the
   *     server. Since this value is server defined, clients may only use the continue value from a
   *     previous query result with identical query parameters (except for the value of continue)
   *     and the server may reject a continue value it does not recognize. If the specified continue
   *     value is no longer valid whether due to expiration (generally five to fifteen minutes) or a
   *     configuration change on the server, the server will respond with a 410 ResourceExpired
   *     error together with a continue token. If the client needs a consistent list, it must
   *     restart their list without the continue field. Otherwise, the client may send another list
   *     request with the token received with the 410 error, the server will respond with a list
   *     starting from the next key, but from the latest snapshot, which is inconsistent from the
   *     previous list results - objects that are created, modified, or deleted after the first list
   *     request will be included in the response, as long as their keys are after the \&quot;next
   *     key\&quot;. This field is not supported when watch is true. Clients may start a watch from
   *     the last resourceVersion value returned by the server and not miss any modifications.
   *     (optional)
   * @param fieldSelector A selector to restrict the list of returned objects by their fields.
   *     Defaults to everything. (optional)
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
   * @param timeoutSeconds Timeout for the list/watch call. This limits the duration of the call,
   *     regardless of any activity or inactivity. (optional)
   * @param watch Watch for changes to the described resources and return them as a stream of add,
   *     update, and remove notifications. Specify resourceVersion. (optional)
   * @return DomainList
   * @throws ApiException If fail to call the API, e.g. server error or cannot deserialize the
   *     response body
   */
  public DomainList listNamespacedDomain(
      String namespace,
      Boolean includeUninitialized,
      String pretty,
      String _continue,
      String fieldSelector,
      String labelSelector,
      Integer limit,
      String resourceVersion,
      Integer timeoutSeconds,
      Boolean watch)
      throws ApiException {
    ApiResponse<DomainList> resp =
        listNamespacedDomainWithHttpInfo(
            namespace,
            includeUninitialized,
            pretty,
            _continue,
            fieldSelector,
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
   * @param includeUninitialized If true, partially initialized resources are included in the
   *     response. (optional)
   * @param pretty If &#39;true&#39;, then the output is pretty printed. (optional)
   * @param _continue The continue option should be set when retrieving more results from the
   *     server. Since this value is server defined, clients may only use the continue value from a
   *     previous query result with identical query parameters (except for the value of continue)
   *     and the server may reject a continue value it does not recognize. If the specified continue
   *     value is no longer valid whether due to expiration (generally five to fifteen minutes) or a
   *     configuration change on the server, the server will respond with a 410 ResourceExpired
   *     error together with a continue token. If the client needs a consistent list, it must
   *     restart their list without the continue field. Otherwise, the client may send another list
   *     request with the token received with the 410 error, the server will respond with a list
   *     starting from the next key, but from the latest snapshot, which is inconsistent from the
   *     previous list results - objects that are created, modified, or deleted after the first list
   *     request will be included in the response, as long as their keys are after the \&quot;next
   *     key\&quot;. This field is not supported when watch is true. Clients may start a watch from
   *     the last resourceVersion value returned by the server and not miss any modifications.
   *     (optional)
   * @param fieldSelector A selector to restrict the list of returned objects by their fields.
   *     Defaults to everything. (optional)
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
   * @param timeoutSeconds Timeout for the list/watch call. This limits the duration of the call,
   *     regardless of any activity or inactivity. (optional)
   * @param watch Watch for changes to the described resources and return them as a stream of add,
   *     update, and remove notifications. Specify resourceVersion. (optional)
   * @return ApiResponse&lt;DomainList&gt;
   * @throws ApiException If fail to call the API, e.g. server error or cannot deserialize the
   *     response body
   */
  public ApiResponse<DomainList> listNamespacedDomainWithHttpInfo(
      String namespace,
      Boolean includeUninitialized,
      String pretty,
      String _continue,
      String fieldSelector,
      String labelSelector,
      Integer limit,
      String resourceVersion,
      Integer timeoutSeconds,
      Boolean watch)
      throws ApiException {
    com.squareup.okhttp.Call call =
        listNamespacedDomainValidateBeforeCall(
            namespace,
            includeUninitialized,
            pretty,
            _continue,
            fieldSelector,
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
   * @param includeUninitialized If true, partially initialized resources are included in the
   *     response. (optional)
   * @param pretty If &#39;true&#39;, then the output is pretty printed. (optional)
   * @param _continue The continue option should be set when retrieving more results from the
   *     server. Since this value is server defined, clients may only use the continue value from a
   *     previous query result with identical query parameters (except for the value of continue)
   *     and the server may reject a continue value it does not recognize. If the specified continue
   *     value is no longer valid whether due to expiration (generally five to fifteen minutes) or a
   *     configuration change on the server, the server will respond with a 410 ResourceExpired
   *     error together with a continue token. If the client needs a consistent list, it must
   *     restart their list without the continue field. Otherwise, the client may send another list
   *     request with the token received with the 410 error, the server will respond with a list
   *     starting from the next key, but from the latest snapshot, which is inconsistent from the
   *     previous list results - objects that are created, modified, or deleted after the first list
   *     request will be included in the response, as long as their keys are after the \&quot;next
   *     key\&quot;. This field is not supported when watch is true. Clients may start a watch from
   *     the last resourceVersion value returned by the server and not miss any modifications.
   *     (optional)
   * @param fieldSelector A selector to restrict the list of returned objects by their fields.
   *     Defaults to everything. (optional)
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
   * @param timeoutSeconds Timeout for the list/watch call. This limits the duration of the call,
   *     regardless of any activity or inactivity. (optional)
   * @param watch Watch for changes to the described resources and return them as a stream of add,
   *     update, and remove notifications. Specify resourceVersion. (optional)
   * @param callback The callback to be executed when the API call finishes
   * @return The request call
   * @throws ApiException If fail to process the API call, e.g. serializing the request body object
   */
  public com.squareup.okhttp.Call listNamespacedDomainAsync(
      String namespace,
      Boolean includeUninitialized,
      String pretty,
      String _continue,
      String fieldSelector,
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
          new ProgressResponseBody.ProgressListener() {
            @Override
            public void update(long bytesRead, long contentLength, boolean done) {
              callback.onDownloadProgress(bytesRead, contentLength, done);
            }
          };

      progressRequestListener =
          new ProgressRequestBody.ProgressRequestListener() {
            @Override
            public void onRequestProgress(long bytesWritten, long contentLength, boolean done) {
              callback.onUploadProgress(bytesWritten, contentLength, done);
            }
          };
    }

    com.squareup.okhttp.Call call =
        listNamespacedDomainValidateBeforeCall(
            namespace,
            includeUninitialized,
            pretty,
            _continue,
            fieldSelector,
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
   * Build call for patchNamespacedDomain
   *
   * @param name name of the Domain (required)
   * @param namespace object name and auth scope, such as for teams and projects (required)
   * @param body (required)
   * @param pretty If &#39;true&#39;, then the output is pretty printed. (optional)
   * @param dryRun When present, indicates that modifications should not be persisted. An invalid or
   *     unrecognized dryRun directive will result in an error response and no further processing of
   *     the request. Valid values are: - All: all dry run stages will be processed (optional)
   * @param progressListener Progress listener
   * @param progressRequestListener Progress request listener
   * @return Call to execute
   * @throws ApiException If fail to serialize the request body object
   */
  public com.squareup.okhttp.Call patchNamespacedDomainCall(
      String name,
      String namespace,
      Object body,
      String pretty,
      String dryRun,
      final ProgressResponseBody.ProgressListener progressListener,
      final ProgressRequestBody.ProgressRequestListener progressRequestListener)
      throws ApiException {
    Object localVarPostBody = body;

    // create path and map variables
    String localVarPath =
        ("/apis/"
                + DOMAIN_GROUP
                + "/"
                + DOMAIN_VERSION
                + "/namespaces/{namespace}/"
                + DOMAIN_PLURAL
                + "/{name}")
            .replaceAll("\\{" + "name" + "\\}", apiClient.escapeString(name.toString()))
            .replaceAll("\\{" + "namespace" + "\\}", apiClient.escapeString(namespace.toString()));

    List<Pair> localVarQueryParams = new ArrayList<Pair>();
    List<Pair> localVarCollectionQueryParams = new ArrayList<Pair>();
    if (pretty != null) localVarQueryParams.addAll(apiClient.parameterToPair("pretty", pretty));
    if (dryRun != null) localVarQueryParams.addAll(apiClient.parameterToPair("dryRun", dryRun));

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
              new com.squareup.okhttp.Interceptor() {
                @Override
                public com.squareup.okhttp.Response intercept(
                    com.squareup.okhttp.Interceptor.Chain chain) throws IOException {
                  com.squareup.okhttp.Response originalResponse = chain.proceed(chain.request());
                  return originalResponse
                      .newBuilder()
                      .body(new ProgressResponseBody(originalResponse.body(), progressListener))
                      .build();
                }
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

  @SuppressWarnings("rawtypes")
  private com.squareup.okhttp.Call patchNamespacedDomainValidateBeforeCall(
      String name,
      String namespace,
      Object body,
      String pretty,
      String dryRun,
      final ProgressResponseBody.ProgressListener progressListener,
      final ProgressRequestBody.ProgressRequestListener progressRequestListener)
      throws ApiException {

    // verify the required parameter 'name' is set
    if (name == null) {
      throw new ApiException(
          "Missing the required parameter 'name' when calling patchNamespacedDomain(Async)");
    }

    // verify the required parameter 'namespace' is set
    if (namespace == null) {
      throw new ApiException(
          "Missing the required parameter 'namespace' when calling patchNamespacedDomain(Async)");
    }

    // verify the required parameter 'body' is set
    if (body == null) {
      throw new ApiException(
          "Missing the required parameter 'body' when calling patchNamespacedDomain(Async)");
    }

    com.squareup.okhttp.Call call =
        patchNamespacedDomainCall(
            name, namespace, body, pretty, dryRun, progressListener, progressRequestListener);
    return call;
  }

  /**
   * partially update the specified Domain
   *
   * @param name name of the Domain (required)
   * @param namespace object name and auth scope, such as for teams and projects (required)
   * @param body (required)
   * @param pretty If &#39;true&#39;, then the output is pretty printed. (optional)
   * @param dryRun When present, indicates that modifications should not be persisted. An invalid or
   *     unrecognized dryRun directive will result in an error response and no further processing of
   *     the request. Valid values are: - All: all dry run stages will be processed (optional)
   * @return Domain
   * @throws ApiException If fail to call the API, e.g. server error or cannot deserialize the
   *     response body
   */
  public Domain patchNamespacedDomain(
      String name, String namespace, Object body, String pretty, String dryRun)
      throws ApiException {
    ApiResponse<Domain> resp =
        patchNamespacedDomainWithHttpInfo(name, namespace, body, pretty, dryRun);
    return resp.getData();
  }

  /**
   * partially update the specified Domain
   *
   * @param name name of the Domain (required)
   * @param namespace object name and auth scope, such as for teams and projects (required)
   * @param body (required)
   * @param pretty If &#39;true&#39;, then the output is pretty printed. (optional)
   * @param dryRun When present, indicates that modifications should not be persisted. An invalid or
   *     unrecognized dryRun directive will result in an error response and no further processing of
   *     the request. Valid values are: - All: all dry run stages will be processed (optional)
   * @return ApiResponse&lt;Domain&gt;
   * @throws ApiException If fail to call the API, e.g. server error or cannot deserialize the
   *     response body
   */
  public ApiResponse<Domain> patchNamespacedDomainWithHttpInfo(
      String name, String namespace, Object body, String pretty, String dryRun)
      throws ApiException {
    com.squareup.okhttp.Call call =
        patchNamespacedDomainValidateBeforeCall(name, namespace, body, pretty, dryRun, null, null);
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
   * @param dryRun When present, indicates that modifications should not be persisted. An invalid or
   *     unrecognized dryRun directive will result in an error response and no further processing of
   *     the request. Valid values are: - All: all dry run stages will be processed (optional)
   * @param callback The callback to be executed when the API call finishes
   * @return The request call
   * @throws ApiException If fail to process the API call, e.g. serializing the request body object
   */
  public com.squareup.okhttp.Call patchNamespacedDomainAsync(
      String name,
      String namespace,
      Object body,
      String pretty,
      String dryRun,
      final ApiCallback<Domain> callback)
      throws ApiException {

    ProgressResponseBody.ProgressListener progressListener = null;
    ProgressRequestBody.ProgressRequestListener progressRequestListener = null;

    if (callback != null) {
      progressListener =
          new ProgressResponseBody.ProgressListener() {
            @Override
            public void update(long bytesRead, long contentLength, boolean done) {
              callback.onDownloadProgress(bytesRead, contentLength, done);
            }
          };

      progressRequestListener =
          new ProgressRequestBody.ProgressRequestListener() {
            @Override
            public void onRequestProgress(long bytesWritten, long contentLength, boolean done) {
              callback.onUploadProgress(bytesWritten, contentLength, done);
            }
          };
    }

    com.squareup.okhttp.Call call =
        patchNamespacedDomainValidateBeforeCall(
            name, namespace, body, pretty, dryRun, progressListener, progressRequestListener);
    Type localVarReturnType = new TypeToken<Domain>() {}.getType();
    apiClient.executeAsync(call, localVarReturnType, callback);
    return call;
  }
  /**
   * Build call for patchNamespacedDomainStatus
   *
   * @param name name of the Domain (required)
   * @param namespace object name and auth scope, such as for teams and projects (required)
   * @param body (required)
   * @param pretty If &#39;true&#39;, then the output is pretty printed. (optional)
   * @param dryRun When present, indicates that modifications should not be persisted. An invalid or
   *     unrecognized dryRun directive will result in an error response and no further processing of
   *     the request. Valid values are: - All: all dry run stages will be processed (optional)
   * @param progressListener Progress listener
   * @param progressRequestListener Progress request listener
   * @return Call to execute
   * @throws ApiException If fail to serialize the request body object
   */
  public com.squareup.okhttp.Call patchNamespacedDomainStatusCall(
      String name,
      String namespace,
      Object body,
      String pretty,
      String dryRun,
      final ProgressResponseBody.ProgressListener progressListener,
      final ProgressRequestBody.ProgressRequestListener progressRequestListener)
      throws ApiException {
    Object localVarPostBody = body;

    // create path and map variables
    String localVarPath =
        ("/apis/"
                + DOMAIN_GROUP
                + "/"
                + DOMAIN_VERSION
                + "/namespaces/{namespace}/"
                + DOMAIN_PLURAL
                + "/{name}/status")
            .replaceAll("\\{" + "name" + "\\}", apiClient.escapeString(name.toString()))
            .replaceAll("\\{" + "namespace" + "\\}", apiClient.escapeString(namespace.toString()));

    List<Pair> localVarQueryParams = new ArrayList<Pair>();
    List<Pair> localVarCollectionQueryParams = new ArrayList<Pair>();
    if (pretty != null) localVarQueryParams.addAll(apiClient.parameterToPair("pretty", pretty));
    if (dryRun != null) localVarQueryParams.addAll(apiClient.parameterToPair("dryRun", dryRun));

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
              new com.squareup.okhttp.Interceptor() {
                @Override
                public com.squareup.okhttp.Response intercept(
                    com.squareup.okhttp.Interceptor.Chain chain) throws IOException {
                  com.squareup.okhttp.Response originalResponse = chain.proceed(chain.request());
                  return originalResponse
                      .newBuilder()
                      .body(new ProgressResponseBody(originalResponse.body(), progressListener))
                      .build();
                }
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

  @SuppressWarnings("rawtypes")
  private com.squareup.okhttp.Call patchNamespacedDomainStatusValidateBeforeCall(
      String name,
      String namespace,
      Object body,
      String pretty,
      String dryRun,
      final ProgressResponseBody.ProgressListener progressListener,
      final ProgressRequestBody.ProgressRequestListener progressRequestListener)
      throws ApiException {

    // verify the required parameter 'name' is set
    if (name == null) {
      throw new ApiException(
          "Missing the required parameter 'name' when calling patchNamespacedDomainStatus(Async)");
    }

    // verify the required parameter 'namespace' is set
    if (namespace == null) {
      throw new ApiException(
          "Missing the required parameter 'namespace' when calling patchNamespacedDomainStatus(Async)");
    }

    // verify the required parameter 'body' is set
    if (body == null) {
      throw new ApiException(
          "Missing the required parameter 'body' when calling patchNamespacedDomainStatus(Async)");
    }

    com.squareup.okhttp.Call call =
        patchNamespacedDomainStatusCall(
            name, namespace, body, pretty, dryRun, progressListener, progressRequestListener);
    return call;
  }

  /**
   * partially update status of the specified Domain
   *
   * @param name name of the Domain (required)
   * @param namespace object name and auth scope, such as for teams and projects (required)
   * @param body (required)
   * @param pretty If &#39;true&#39;, then the output is pretty printed. (optional)
   * @param dryRun When present, indicates that modifications should not be persisted. An invalid or
   *     unrecognized dryRun directive will result in an error response and no further processing of
   *     the request. Valid values are: - All: all dry run stages will be processed (optional)
   * @return Domain
   * @throws ApiException If fail to call the API, e.g. server error or cannot deserialize the
   *     response body
   */
  public Domain patchNamespacedDomainStatus(
      String name, String namespace, Object body, String pretty, String dryRun)
      throws ApiException {
    ApiResponse<Domain> resp =
        patchNamespacedDomainStatusWithHttpInfo(name, namespace, body, pretty, dryRun);
    return resp.getData();
  }

  /**
   * partially update status of the specified Domain
   *
   * @param name name of the Domain (required)
   * @param namespace object name and auth scope, such as for teams and projects (required)
   * @param body (required)
   * @param pretty If &#39;true&#39;, then the output is pretty printed. (optional)
   * @param dryRun When present, indicates that modifications should not be persisted. An invalid or
   *     unrecognized dryRun directive will result in an error response and no further processing of
   *     the request. Valid values are: - All: all dry run stages will be processed (optional)
   * @return ApiResponse&lt;Domain&gt;
   * @throws ApiException If fail to call the API, e.g. server error or cannot deserialize the
   *     response body
   */
  public ApiResponse<Domain> patchNamespacedDomainStatusWithHttpInfo(
      String name, String namespace, Object body, String pretty, String dryRun)
      throws ApiException {
    com.squareup.okhttp.Call call =
        patchNamespacedDomainStatusValidateBeforeCall(
            name, namespace, body, pretty, dryRun, null, null);
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
   * @param dryRun When present, indicates that modifications should not be persisted. An invalid or
   *     unrecognized dryRun directive will result in an error response and no further processing of
   *     the request. Valid values are: - All: all dry run stages will be processed (optional)
   * @param callback The callback to be executed when the API call finishes
   * @return The request call
   * @throws ApiException If fail to process the API call, e.g. serializing the request body object
   */
  public com.squareup.okhttp.Call patchNamespacedDomainStatusAsync(
      String name,
      String namespace,
      Object body,
      String pretty,
      String dryRun,
      final ApiCallback<Domain> callback)
      throws ApiException {

    ProgressResponseBody.ProgressListener progressListener = null;
    ProgressRequestBody.ProgressRequestListener progressRequestListener = null;

    if (callback != null) {
      progressListener =
          new ProgressResponseBody.ProgressListener() {
            @Override
            public void update(long bytesRead, long contentLength, boolean done) {
              callback.onDownloadProgress(bytesRead, contentLength, done);
            }
          };

      progressRequestListener =
          new ProgressRequestBody.ProgressRequestListener() {
            @Override
            public void onRequestProgress(long bytesWritten, long contentLength, boolean done) {
              callback.onUploadProgress(bytesWritten, contentLength, done);
            }
          };
    }

    com.squareup.okhttp.Call call =
        patchNamespacedDomainStatusValidateBeforeCall(
            name, namespace, body, pretty, dryRun, progressListener, progressRequestListener);
    Type localVarReturnType = new TypeToken<Domain>() {}.getType();
    apiClient.executeAsync(call, localVarReturnType, callback);
    return call;
  }

  /**
   * Build call for readNamespacedDomain
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
  public com.squareup.okhttp.Call readNamespacedDomainCall(
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
        ("/apis/"
                + DOMAIN_GROUP
                + "/"
                + DOMAIN_VERSION
                + "/namespaces/{namespace}/"
                + DOMAIN_PLURAL
                + "/{name}")
            .replaceAll("\\{" + "name" + "\\}", apiClient.escapeString(name.toString()))
            .replaceAll("\\{" + "namespace" + "\\}", apiClient.escapeString(namespace.toString()));

    List<Pair> localVarQueryParams = new ArrayList<Pair>();
    List<Pair> localVarCollectionQueryParams = new ArrayList<Pair>();
    if (pretty != null) localVarQueryParams.addAll(apiClient.parameterToPair("pretty", pretty));
    if (exact != null) localVarQueryParams.addAll(apiClient.parameterToPair("exact", exact));
    if (export != null) localVarQueryParams.addAll(apiClient.parameterToPair("export", export));

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
              new com.squareup.okhttp.Interceptor() {
                @Override
                public com.squareup.okhttp.Response intercept(
                    com.squareup.okhttp.Interceptor.Chain chain) throws IOException {
                  com.squareup.okhttp.Response originalResponse = chain.proceed(chain.request());
                  return originalResponse
                      .newBuilder()
                      .body(new ProgressResponseBody(originalResponse.body(), progressListener))
                      .build();
                }
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

  @SuppressWarnings("rawtypes")
  private com.squareup.okhttp.Call readNamespacedDomainValidateBeforeCall(
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
          "Missing the required parameter 'name' when calling readNamespacedDomain(Async)");
    }

    // verify the required parameter 'namespace' is set
    if (namespace == null) {
      throw new ApiException(
          "Missing the required parameter 'namespace' when calling readNamespacedDomain(Async)");
    }

    com.squareup.okhttp.Call call =
        readNamespacedDomainCall(
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
  public Domain readNamespacedDomain(
      String name, String namespace, String pretty, Boolean exact, Boolean export)
      throws ApiException {
    ApiResponse<Domain> resp =
        readNamespacedDomainWithHttpInfo(name, namespace, pretty, exact, export);
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
  public ApiResponse<Domain> readNamespacedDomainWithHttpInfo(
      String name, String namespace, String pretty, Boolean exact, Boolean export)
      throws ApiException {
    com.squareup.okhttp.Call call =
        readNamespacedDomainValidateBeforeCall(name, namespace, pretty, exact, export, null, null);
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
  public com.squareup.okhttp.Call readNamespacedDomainAsync(
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
          new ProgressResponseBody.ProgressListener() {
            @Override
            public void update(long bytesRead, long contentLength, boolean done) {
              callback.onDownloadProgress(bytesRead, contentLength, done);
            }
          };

      progressRequestListener =
          new ProgressRequestBody.ProgressRequestListener() {
            @Override
            public void onRequestProgress(long bytesWritten, long contentLength, boolean done) {
              callback.onUploadProgress(bytesWritten, contentLength, done);
            }
          };
    }

    com.squareup.okhttp.Call call =
        readNamespacedDomainValidateBeforeCall(
            name, namespace, pretty, exact, export, progressListener, progressRequestListener);
    Type localVarReturnType = new TypeToken<Domain>() {}.getType();
    apiClient.executeAsync(call, localVarReturnType, callback);
    return call;
  }

  /**
   * Build call for readNamespacedDomainStatus
   *
   * @param name name of the Domain (required)
   * @param namespace object name and auth scope, such as for teams and projects (required)
   * @param pretty If &#39;true&#39;, then the output is pretty printed. (optional)
   * @param progressListener Progress listener
   * @param progressRequestListener Progress request listener
   * @return Call to execute
   * @throws ApiException If fail to serialize the request body object
   */
  public com.squareup.okhttp.Call readNamespacedDomainStatusCall(
      String name,
      String namespace,
      String pretty,
      final ProgressResponseBody.ProgressListener progressListener,
      final ProgressRequestBody.ProgressRequestListener progressRequestListener)
      throws ApiException {
    Object localVarPostBody = null;

    // create path and map variables
    String localVarPath =
        ("/apis/"
                + DOMAIN_GROUP
                + "/"
                + DOMAIN_VERSION
                + "/namespaces/{namespace}/"
                + DOMAIN_PLURAL
                + "/{name}/status")
            .replaceAll("\\{" + "name" + "\\}", apiClient.escapeString(name.toString()))
            .replaceAll("\\{" + "namespace" + "\\}", apiClient.escapeString(namespace.toString()));

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
              new com.squareup.okhttp.Interceptor() {
                @Override
                public com.squareup.okhttp.Response intercept(
                    com.squareup.okhttp.Interceptor.Chain chain) throws IOException {
                  com.squareup.okhttp.Response originalResponse = chain.proceed(chain.request());
                  return originalResponse
                      .newBuilder()
                      .body(new ProgressResponseBody(originalResponse.body(), progressListener))
                      .build();
                }
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

  @SuppressWarnings("rawtypes")
  private com.squareup.okhttp.Call readNamespacedDomainStatusValidateBeforeCall(
      String name,
      String namespace,
      String pretty,
      final ProgressResponseBody.ProgressListener progressListener,
      final ProgressRequestBody.ProgressRequestListener progressRequestListener)
      throws ApiException {

    // verify the required parameter 'name' is set
    if (name == null) {
      throw new ApiException(
          "Missing the required parameter 'name' when calling readNamespacedDomainStatus(Async)");
    }

    // verify the required parameter 'namespace' is set
    if (namespace == null) {
      throw new ApiException(
          "Missing the required parameter 'namespace' when calling readNamespacedDomainStatus(Async)");
    }

    com.squareup.okhttp.Call call =
        readNamespacedDomainStatusCall(
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
  public Domain readNamespacedDomainStatus(String name, String namespace, String pretty)
      throws ApiException {
    ApiResponse<Domain> resp = readNamespacedDomainStatusWithHttpInfo(name, namespace, pretty);
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
  public ApiResponse<Domain> readNamespacedDomainStatusWithHttpInfo(
      String name, String namespace, String pretty) throws ApiException {
    com.squareup.okhttp.Call call =
        readNamespacedDomainStatusValidateBeforeCall(name, namespace, pretty, null, null);
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
  public com.squareup.okhttp.Call readNamespacedDomainStatusAsync(
      String name, String namespace, String pretty, final ApiCallback<Domain> callback)
      throws ApiException {

    ProgressResponseBody.ProgressListener progressListener = null;
    ProgressRequestBody.ProgressRequestListener progressRequestListener = null;

    if (callback != null) {
      progressListener =
          new ProgressResponseBody.ProgressListener() {
            @Override
            public void update(long bytesRead, long contentLength, boolean done) {
              callback.onDownloadProgress(bytesRead, contentLength, done);
            }
          };

      progressRequestListener =
          new ProgressRequestBody.ProgressRequestListener() {
            @Override
            public void onRequestProgress(long bytesWritten, long contentLength, boolean done) {
              callback.onUploadProgress(bytesWritten, contentLength, done);
            }
          };
    }

    com.squareup.okhttp.Call call =
        readNamespacedDomainStatusValidateBeforeCall(
            name, namespace, pretty, progressListener, progressRequestListener);
    Type localVarReturnType = new TypeToken<Domain>() {}.getType();
    apiClient.executeAsync(call, localVarReturnType, callback);
    return call;
  }

  /**
   * Build call for replaceNamespacedDomain
   *
   * @param name name of the Domain (required)
   * @param namespace object name and auth scope, such as for teams and projects (required)
   * @param body (required)
   * @param pretty If &#39;true&#39;, then the output is pretty printed. (optional)
   * @param dryRun When present, indicates that modifications should not be persisted. An invalid or
   *     unrecognized dryRun directive will result in an error response and no further processing of
   *     the request. Valid values are: - All: all dry run stages will be processed (optional)
   * @param progressListener Progress listener
   * @param progressRequestListener Progress request listener
   * @return Call to execute
   * @throws ApiException If fail to serialize the request body object
   */
  public com.squareup.okhttp.Call replaceNamespacedDomainCall(
      String name,
      String namespace,
      Domain body,
      String pretty,
      String dryRun,
      final ProgressResponseBody.ProgressListener progressListener,
      final ProgressRequestBody.ProgressRequestListener progressRequestListener)
      throws ApiException {
    Object localVarPostBody = body;

    // create path and map variables
    String localVarPath =
        ("/apis/"
                + DOMAIN_GROUP
                + "/"
                + DOMAIN_VERSION
                + "/namespaces/{namespace}/"
                + DOMAIN_PLURAL
                + "/{name}")
            .replaceAll("\\{" + "name" + "\\}", apiClient.escapeString(name.toString()))
            .replaceAll("\\{" + "namespace" + "\\}", apiClient.escapeString(namespace.toString()));

    List<Pair> localVarQueryParams = new ArrayList<Pair>();
    List<Pair> localVarCollectionQueryParams = new ArrayList<Pair>();
    if (pretty != null) localVarQueryParams.addAll(apiClient.parameterToPair("pretty", pretty));
    if (dryRun != null) localVarQueryParams.addAll(apiClient.parameterToPair("dryRun", dryRun));

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
              new com.squareup.okhttp.Interceptor() {
                @Override
                public com.squareup.okhttp.Response intercept(
                    com.squareup.okhttp.Interceptor.Chain chain) throws IOException {
                  com.squareup.okhttp.Response originalResponse = chain.proceed(chain.request());
                  return originalResponse
                      .newBuilder()
                      .body(new ProgressResponseBody(originalResponse.body(), progressListener))
                      .build();
                }
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

  @SuppressWarnings("rawtypes")
  private com.squareup.okhttp.Call replaceNamespacedDomainValidateBeforeCall(
      String name,
      String namespace,
      Domain body,
      String pretty,
      String dryRun,
      final ProgressResponseBody.ProgressListener progressListener,
      final ProgressRequestBody.ProgressRequestListener progressRequestListener)
      throws ApiException {

    // verify the required parameter 'name' is set
    if (name == null) {
      throw new ApiException(
          "Missing the required parameter 'name' when calling replaceNamespacedDomain(Async)");
    }

    // verify the required parameter 'namespace' is set
    if (namespace == null) {
      throw new ApiException(
          "Missing the required parameter 'namespace' when calling replaceNamespacedDomain(Async)");
    }

    // verify the required parameter 'body' is set
    if (body == null) {
      throw new ApiException(
          "Missing the required parameter 'body' when calling replaceNamespacedDomain(Async)");
    }

    com.squareup.okhttp.Call call =
        replaceNamespacedDomainCall(
            name, namespace, body, pretty, dryRun, progressListener, progressRequestListener);
    return call;
  }

  /**
   * replace the specified Domain
   *
   * @param name name of the Domain (required)
   * @param namespace object name and auth scope, such as for teams and projects (required)
   * @param body (required)
   * @param pretty If &#39;true&#39;, then the output is pretty printed. (optional)
   * @param dryRun When present, indicates that modifications should not be persisted. An invalid or
   *     unrecognized dryRun directive will result in an error response and no further processing of
   *     the request. Valid values are: - All: all dry run stages will be processed (optional)
   * @return Domain
   * @throws ApiException If fail to call the API, e.g. server error or cannot deserialize the
   *     response body
   */
  public Domain replaceNamespacedDomain(
      String name, String namespace, Domain body, String pretty, String dryRun)
      throws ApiException {
    ApiResponse<Domain> resp =
        replaceNamespacedDomainWithHttpInfo(name, namespace, body, pretty, dryRun);
    return resp.getData();
  }

  /**
   * replace the specified Domain
   *
   * @param name name of the Domain (required)
   * @param namespace object name and auth scope, such as for teams and projects (required)
   * @param body (required)
   * @param pretty If &#39;true&#39;, then the output is pretty printed. (optional)
   * @param dryRun When present, indicates that modifications should not be persisted. An invalid or
   *     unrecognized dryRun directive will result in an error response and no further processing of
   *     the request. Valid values are: - All: all dry run stages will be processed (optional)
   * @return ApiResponse&lt;Domain&gt;
   * @throws ApiException If fail to call the API, e.g. server error or cannot deserialize the
   *     response body
   */
  public ApiResponse<Domain> replaceNamespacedDomainWithHttpInfo(
      String name, String namespace, Domain body, String pretty, String dryRun)
      throws ApiException {
    com.squareup.okhttp.Call call =
        replaceNamespacedDomainValidateBeforeCall(
            name, namespace, body, pretty, dryRun, null, null);
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
   * @param dryRun When present, indicates that modifications should not be persisted. An invalid or
   *     unrecognized dryRun directive will result in an error response and no further processing of
   *     the request. Valid values are: - All: all dry run stages will be processed (optional)
   * @param callback The callback to be executed when the API call finishes
   * @return The request call
   * @throws ApiException If fail to process the API call, e.g. serializing the request body object
   */
  public com.squareup.okhttp.Call replaceNamespacedDomainAsync(
      String name,
      String namespace,
      Domain body,
      String pretty,
      String dryRun,
      final ApiCallback<Domain> callback)
      throws ApiException {

    ProgressResponseBody.ProgressListener progressListener = null;
    ProgressRequestBody.ProgressRequestListener progressRequestListener = null;

    if (callback != null) {
      progressListener =
          new ProgressResponseBody.ProgressListener() {
            @Override
            public void update(long bytesRead, long contentLength, boolean done) {
              callback.onDownloadProgress(bytesRead, contentLength, done);
            }
          };

      progressRequestListener =
          new ProgressRequestBody.ProgressRequestListener() {
            @Override
            public void onRequestProgress(long bytesWritten, long contentLength, boolean done) {
              callback.onUploadProgress(bytesWritten, contentLength, done);
            }
          };
    }

    com.squareup.okhttp.Call call =
        replaceNamespacedDomainValidateBeforeCall(
            name, namespace, body, pretty, dryRun, progressListener, progressRequestListener);
    Type localVarReturnType = new TypeToken<Domain>() {}.getType();
    apiClient.executeAsync(call, localVarReturnType, callback);
    return call;
  }
  /**
   * Build call for replaceNamespacedDomainStatus
   *
   * @param name name of the Domain (required)
   * @param namespace object name and auth scope, such as for teams and projects (required)
   * @param body (required)
   * @param pretty If &#39;true&#39;, then the output is pretty printed. (optional)
   * @param dryRun When present, indicates that modifications should not be persisted. An invalid or
   *     unrecognized dryRun directive will result in an error response and no further processing of
   *     the request. Valid values are: - All: all dry run stages will be processed (optional)
   * @param progressListener Progress listener
   * @param progressRequestListener Progress request listener
   * @return Call to execute
   * @throws ApiException If fail to serialize the request body object
   */
  public com.squareup.okhttp.Call replaceNamespacedDomainStatusCall(
      String name,
      String namespace,
      Domain body,
      String pretty,
      String dryRun,
      final ProgressResponseBody.ProgressListener progressListener,
      final ProgressRequestBody.ProgressRequestListener progressRequestListener)
      throws ApiException {
    Object localVarPostBody = body;

    // create path and map variables
    String localVarPath =
        ("/apis/"
                + DOMAIN_GROUP
                + "/"
                + DOMAIN_VERSION
                + "/namespaces/{namespace}/"
                + DOMAIN_PLURAL
                + "/{name}/status")
            .replaceAll("\\{" + "name" + "\\}", apiClient.escapeString(name.toString()))
            .replaceAll("\\{" + "namespace" + "\\}", apiClient.escapeString(namespace.toString()));

    List<Pair> localVarQueryParams = new ArrayList<Pair>();
    List<Pair> localVarCollectionQueryParams = new ArrayList<Pair>();
    if (pretty != null) localVarQueryParams.addAll(apiClient.parameterToPair("pretty", pretty));
    if (dryRun != null) localVarQueryParams.addAll(apiClient.parameterToPair("dryRun", dryRun));

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
              new com.squareup.okhttp.Interceptor() {
                @Override
                public com.squareup.okhttp.Response intercept(
                    com.squareup.okhttp.Interceptor.Chain chain) throws IOException {
                  com.squareup.okhttp.Response originalResponse = chain.proceed(chain.request());
                  return originalResponse
                      .newBuilder()
                      .body(new ProgressResponseBody(originalResponse.body(), progressListener))
                      .build();
                }
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

  @SuppressWarnings("rawtypes")
  private com.squareup.okhttp.Call replaceNamespacedDomainStatusValidateBeforeCall(
      String name,
      String namespace,
      Domain body,
      String pretty,
      String dryRun,
      final ProgressResponseBody.ProgressListener progressListener,
      final ProgressRequestBody.ProgressRequestListener progressRequestListener)
      throws ApiException {

    // verify the required parameter 'name' is set
    if (name == null) {
      throw new ApiException(
          "Missing the required parameter 'name' when calling replaceNamespacedDomainStatus(Async)");
    }

    // verify the required parameter 'namespace' is set
    if (namespace == null) {
      throw new ApiException(
          "Missing the required parameter 'namespace' when calling replaceNamespacedDomainStatus(Async)");
    }

    // verify the required parameter 'body' is set
    if (body == null) {
      throw new ApiException(
          "Missing the required parameter 'body' when calling replaceNamespacedDomainStatus(Async)");
    }

    com.squareup.okhttp.Call call =
        replaceNamespacedDomainStatusCall(
            name, namespace, body, pretty, dryRun, progressListener, progressRequestListener);
    return call;
  }

  /**
   * replace status of the specified Domain
   *
   * @param name name of the Domain (required)
   * @param namespace object name and auth scope, such as for teams and projects (required)
   * @param body (required)
   * @param pretty If &#39;true&#39;, then the output is pretty printed. (optional)
   * @param dryRun When present, indicates that modifications should not be persisted. An invalid or
   *     unrecognized dryRun directive will result in an error response and no further processing of
   *     the request. Valid values are: - All: all dry run stages will be processed (optional)
   * @return Domain
   * @throws ApiException If fail to call the API, e.g. server error or cannot deserialize the
   *     response body
   */
  public Domain replaceNamespacedDomainStatus(
      String name, String namespace, Domain body, String pretty, String dryRun)
      throws ApiException {
    ApiResponse<Domain> resp =
        replaceNamespacedDomainStatusWithHttpInfo(name, namespace, body, pretty, dryRun);
    return resp.getData();
  }

  /**
   * replace status of the specified Domain
   *
   * @param name name of the Domain (required)
   * @param namespace object name and auth scope, such as for teams and projects (required)
   * @param body (required)
   * @param pretty If &#39;true&#39;, then the output is pretty printed. (optional)
   * @param dryRun When present, indicates that modifications should not be persisted. An invalid or
   *     unrecognized dryRun directive will result in an error response and no further processing of
   *     the request. Valid values are: - All: all dry run stages will be processed (optional)
   * @return ApiResponse&lt;Domain&gt;
   * @throws ApiException If fail to call the API, e.g. server error or cannot deserialize the
   *     response body
   */
  public ApiResponse<Domain> replaceNamespacedDomainStatusWithHttpInfo(
      String name, String namespace, Domain body, String pretty, String dryRun)
      throws ApiException {
    com.squareup.okhttp.Call call =
        replaceNamespacedDomainStatusValidateBeforeCall(
            name, namespace, body, pretty, dryRun, null, null);
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
   * @param dryRun When present, indicates that modifications should not be persisted. An invalid or
   *     unrecognized dryRun directive will result in an error response and no further processing of
   *     the request. Valid values are: - All: all dry run stages will be processed (optional)
   * @param callback The callback to be executed when the API call finishes
   * @return The request call
   * @throws ApiException If fail to process the API call, e.g. serializing the request body object
   */
  public com.squareup.okhttp.Call replaceNamespacedDomainStatusAsync(
      String name,
      String namespace,
      Domain body,
      String pretty,
      String dryRun,
      final ApiCallback<Domain> callback)
      throws ApiException {

    ProgressResponseBody.ProgressListener progressListener = null;
    ProgressRequestBody.ProgressRequestListener progressRequestListener = null;

    if (callback != null) {
      progressListener =
          new ProgressResponseBody.ProgressListener() {
            @Override
            public void update(long bytesRead, long contentLength, boolean done) {
              callback.onDownloadProgress(bytesRead, contentLength, done);
            }
          };

      progressRequestListener =
          new ProgressRequestBody.ProgressRequestListener() {
            @Override
            public void onRequestProgress(long bytesWritten, long contentLength, boolean done) {
              callback.onUploadProgress(bytesWritten, contentLength, done);
            }
          };
    }

    com.squareup.okhttp.Call call =
        replaceNamespacedDomainStatusValidateBeforeCall(
            name, namespace, body, pretty, dryRun, progressListener, progressRequestListener);
    Type localVarReturnType = new TypeToken<Domain>() {}.getType();
    apiClient.executeAsync(call, localVarReturnType, callback);
    return call;
  }
}

// Copyright (c) 2018, 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.builders;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.concurrent.TimeUnit;
import java.util.function.BiFunction;

import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.apis.BatchV1Api;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.models.V1ConfigMap;
import io.kubernetes.client.openapi.models.V1Event;
import io.kubernetes.client.openapi.models.V1Job;
import io.kubernetes.client.openapi.models.V1Namespace;
import io.kubernetes.client.openapi.models.V1Pod;
import io.kubernetes.client.openapi.models.V1Service;
import io.kubernetes.client.util.Watch;
import okhttp3.Call;
import okhttp3.OkHttpClient;
import oracle.kubernetes.operator.helpers.ClientPool;
import oracle.kubernetes.operator.helpers.Pool;
import oracle.kubernetes.weblogic.domain.api.WeblogicApi;
import oracle.kubernetes.weblogic.domain.model.Domain;

public class WatchBuilder {
  /** Always true for watches. */
  private static final boolean WATCH = true;

  /** Ignored for watches. */
  private static final String START_LIST = null;

  private static final Boolean ALLOW_BOOKMARKS = false;

  private static WatchFactory FACTORY = new WatchFactoryImpl();

  private final CallParamsImpl callParams = new CallParamsImpl();

  public WatchBuilder() {
  }

  private static Type getType(Class<?> responseBodyType) {
    return new ParameterizedType() {
      @Override
      public Type[] getActualTypeArguments() {
        return new Type[] {responseBodyType};
      }

      @Override
      public Type getRawType() {
        return Watch.Response.class;
      }

      @Override
      public Type getOwnerType() {
        return Watch.class;
      }
    };
  }

  /**
   * Creates a web hook object to track service calls.
   *
   * @param namespace the namespace
   * @return the active web hook
   * @throws ApiException if there is an error on the call that sets up the web hook.
   */
  public WatchI<V1Service> createServiceWatch(String namespace) throws ApiException {
    return FACTORY.createWatch(
        ClientPool.getInstance(),
        callParams,
        V1Service.class,
        new ListNamespacedServiceCall(namespace));
  }

  /**
   * Creates a web hook object to track pods.
   *
   * @param namespace the namespace
   * @return the active web hook
   * @throws ApiException if there is an error on the call that sets up the web hook.
   */
  public WatchI<V1Pod> createPodWatch(String namespace) throws ApiException {
    return FACTORY.createWatch(
        ClientPool.getInstance(), callParams, V1Pod.class, new ListPodCall(namespace));
  }

  /**
   * Creates a web hook object to track jobs.
   *
   * @param namespace the namespace
   * @return the active web hook
   * @throws ApiException if there is an error on the call that sets up the web hook.
   */
  public WatchI<V1Job> createJobWatch(String namespace) throws ApiException {
    return FACTORY.createWatch(
        ClientPool.getInstance(), callParams, V1Job.class, new ListJobCall(namespace));
  }

  /**
   * Creates a web hook object to track events.
   *
   * @param namespace the namespace
   * @return the active web hook
   * @throws ApiException if there is an error on the call that sets up the web hook.
   */
  public WatchI<V1Event> createEventWatch(String namespace) throws ApiException {
    return FACTORY.createWatch(
        ClientPool.getInstance(), callParams, V1Event.class, new ListEventCall(namespace));
  }

  /**
   * Creates a web hook object to track changes to weblogic domains in one namespaces.
   *
   * @param namespace the namespace in which to track domains
   * @return the active web hook
   * @throws ApiException if there is an error on the call that sets up the web hook.
   */
  public WatchI<Domain> createDomainWatch(String namespace) throws ApiException {
    return FACTORY.createWatch(
        ClientPool.getInstance(), callParams, Domain.class, new ListDomainsCall(namespace));
  }

  /**
   * Creates a web hook object to track config map calls.
   *
   * @param namespace the namespace
   * @return the active web hook
   * @throws ApiException if there is an error on the call that sets up the web hook.
   */
  public WatchI<V1ConfigMap> createConfigMapWatch(String namespace) throws ApiException {
    return FACTORY.createWatch(
        ClientPool.getInstance(),
        callParams,
        V1ConfigMap.class,
        new ListNamespacedConfigMapCall(namespace));
  }

  /**
   * Creates a web hook object to track namespace calls.
   *
   * @return the active web hook
   * @throws ApiException if there is an error on the call that sets up the web hook.
   */
  public WatchI<V1Namespace> createNamespacesWatch() throws ApiException {
    return FACTORY.createWatch(
        ClientPool.getInstance(),
        callParams,
        V1Namespace.class,
        new ListNamespaceCall());
  }

  /**
   * Sets a value for the fieldSelector parameter for the call that will set up this watch. Defaults
   * to null.
   *
   * @param fieldSelector the desired value
   * @return the updated builder
   */
  public WatchBuilder withFieldSelector(String fieldSelector) {
    callParams.setFieldSelector(fieldSelector);
    return this;
  }

  public WatchBuilder withLabelSelector(String labelSelector) {
    callParams.setLabelSelector(labelSelector);
    return this;
  }

  public WatchBuilder withLabelSelectors(String... labelSelectors) {
    callParams.setLabelSelector(String.join(",", labelSelectors));
    return this;
  }

  @SuppressWarnings("SameParameterValue")
  WatchBuilder withLimit(Integer limit) {
    callParams.setLimit(limit);
    return this;
  }

  public WatchBuilder withResourceVersion(String resourceVersion) {
    callParams.setResourceVersion(resourceVersion);
    return this;
  }

  public WatchBuilder withTimeoutSeconds(Integer timeoutSeconds) {
    callParams.setTimeoutSeconds(timeoutSeconds);
    return this;
  }

  public interface WatchFactory {
    <T> WatchI<T> createWatch(
        Pool<ApiClient> pool,
        CallParams callParams,
        Class<?> responseBodyType,
        BiFunction<ApiClient, CallParams, Call> function)
        throws ApiException;
  }

  static class WatchFactoryImpl implements WatchFactory {
    @Override
    public <T> WatchI<T> createWatch(
        Pool<ApiClient> pool,
        CallParams callParams,
        Class<?> responseBodyType,
        BiFunction<ApiClient, CallParams, Call> function)
        throws ApiException {
      ApiClient client = pool.take();
      try {
        return new WatchImpl<>(
            pool,
            client,
            Watch.createWatch(
                client, function.apply(client, callParams), getType(responseBodyType)));
      } catch (UncheckedApiException e) {
        throw e.getCause();
      }
    }
  }

  private class ListNamespacedServiceCall implements BiFunction<ApiClient, CallParams, Call> {
    private final String namespace;

    ListNamespacedServiceCall(String namespace) {
      this.namespace = namespace;
    }

    @Override
    public Call apply(ApiClient client, CallParams callParams) {
      // Ensure that client doesn't time out before call or watch
      // infinite timeout
      OkHttpClient httpClient =
          client.getHttpClient().newBuilder().readTimeout(0, TimeUnit.SECONDS).build();
      client.setHttpClient(httpClient);

      try {
        return new CoreV1Api(client)
            .listNamespacedServiceCall(
                namespace,
                callParams.getPretty(),
                ALLOW_BOOKMARKS,
                START_LIST,
                callParams.getFieldSelector(),
                callParams.getLabelSelector(),
                callParams.getLimit(),
                callParams.getResourceVersion(),
                callParams.getTimeoutSeconds(),
                WATCH,
                null);
      } catch (ApiException e) {
        throw new UncheckedApiException(e);
      }
    }
  }

  private class ListPodCall implements BiFunction<ApiClient, CallParams, Call> {
    private final String namespace;

    ListPodCall(String namespace) {
      this.namespace = namespace;
    }

    @Override
    public Call apply(ApiClient client, CallParams callParams) {
      // Ensure that client doesn't time out before call or watch
      // infinite timeout
      OkHttpClient httpClient =
          client.getHttpClient().newBuilder().readTimeout(0, TimeUnit.SECONDS).build();
      client.setHttpClient(httpClient);

      try {
        return new CoreV1Api(client)
            .listNamespacedPodCall(
                namespace,
                callParams.getPretty(),
                ALLOW_BOOKMARKS,
                START_LIST,
                callParams.getFieldSelector(),
                callParams.getLabelSelector(),
                callParams.getLimit(),
                callParams.getResourceVersion(),
                callParams.getTimeoutSeconds(),
                WATCH,
                null);
      } catch (ApiException e) {
        throw new UncheckedApiException(e);
      }
    }
  }

  private class ListJobCall implements BiFunction<ApiClient, CallParams, Call> {
    private final String namespace;

    ListJobCall(String namespace) {
      this.namespace = namespace;
    }

    @Override
    public Call apply(ApiClient client, CallParams callParams) {
      // Ensure that client doesn't time out before call or watch
      // infinite timeout
      OkHttpClient httpClient =
          client.getHttpClient().newBuilder().readTimeout(0, TimeUnit.SECONDS).build();
      client.setHttpClient(httpClient);

      try {
        return new BatchV1Api(client)
            .listNamespacedJobCall(
                namespace,
                callParams.getPretty(),
                ALLOW_BOOKMARKS,
                START_LIST,
                callParams.getFieldSelector(),
                callParams.getLabelSelector(),
                callParams.getLimit(),
                callParams.getResourceVersion(),
                callParams.getTimeoutSeconds(),
                WATCH,
                null);
      } catch (ApiException e) {
        throw new UncheckedApiException(e);
      }
    }
  }

  private class ListEventCall implements BiFunction<ApiClient, CallParams, Call> {
    private final String namespace;

    ListEventCall(String namespace) {
      this.namespace = namespace;
    }

    @Override
    public Call apply(ApiClient client, CallParams callParams) {
      // Ensure that client doesn't time out before call or watch
      // infinite timeout
      OkHttpClient httpClient =
          client.getHttpClient().newBuilder().readTimeout(0, TimeUnit.SECONDS).build();
      client.setHttpClient(httpClient);

      try {
        return new CoreV1Api(client)
            .listNamespacedEventCall(
                namespace,
                callParams.getPretty(),
                ALLOW_BOOKMARKS,
                START_LIST,
                callParams.getFieldSelector(),
                callParams.getLabelSelector(),
                callParams.getLimit(),
                callParams.getResourceVersion(),
                callParams.getTimeoutSeconds(),
                WATCH,
                null);
      } catch (ApiException e) {
        throw new UncheckedApiException(e);
      }
    }
  }

  private class ListDomainsCall implements BiFunction<ApiClient, CallParams, Call> {
    private final String namespace;

    ListDomainsCall(String namespace) {
      this.namespace = namespace;
    }

    @Override
    public Call apply(ApiClient client, CallParams callParams) {
      // Ensure that client doesn't time out before call or watch
      // infinite timeout
      OkHttpClient httpClient =
          client.getHttpClient().newBuilder().readTimeout(0, TimeUnit.SECONDS).build();
      client.setHttpClient(httpClient);

      try {
        return new WeblogicApi(client)
            .listNamespacedDomainCall(
                namespace,
                callParams.getPretty(),
                START_LIST,
                callParams.getFieldSelector(),
                callParams.getLabelSelector(),
                callParams.getLimit(),
                callParams.getResourceVersion(),
                callParams.getTimeoutSeconds(),
                WATCH,
                null);
      } catch (ApiException e) {
        throw new UncheckedApiException(e);
      }
    }
  }

  private class ListNamespacedConfigMapCall implements BiFunction<ApiClient, CallParams, Call> {
    private final String namespace;

    ListNamespacedConfigMapCall(String namespace) {
      this.namespace = namespace;
    }

    @Override
    public Call apply(ApiClient client, CallParams callParams) {
      // Ensure that client doesn't time out before call or watch
      // infinite timeout
      OkHttpClient httpClient =
          client.getHttpClient().newBuilder().readTimeout(0, TimeUnit.SECONDS).build();
      client.setHttpClient(httpClient);

      try {
        return new CoreV1Api(client)
            .listNamespacedConfigMapCall(
                namespace,
                callParams.getPretty(),
                ALLOW_BOOKMARKS,
                START_LIST,
                callParams.getFieldSelector(),
                callParams.getLabelSelector(),
                callParams.getLimit(),
                callParams.getResourceVersion(),
                callParams.getTimeoutSeconds(),
                WATCH,
                null);
      } catch (ApiException e) {
        throw new UncheckedApiException(e);
      }
    }
  }

  private class ListNamespaceCall implements BiFunction<ApiClient, CallParams, Call> {

    @Override
    public Call apply(ApiClient client, CallParams callParams) {
      // Ensure that client doesn't time out before call or watch
      // infinite timeout
      OkHttpClient httpClient =
          client.getHttpClient().newBuilder().readTimeout(0, TimeUnit.SECONDS).build();
      client.setHttpClient(httpClient);

      try {
        return new CoreV1Api(client)
            .listNamespaceCall(
                callParams.getPretty(),
                ALLOW_BOOKMARKS,
                START_LIST,
                callParams.getFieldSelector(),
                callParams.getLabelSelector(),
                callParams.getLimit(),
                callParams.getResourceVersion(),
                callParams.getTimeoutSeconds(),
                WATCH,
                null);
      } catch (ApiException e) {
        throw new UncheckedApiException(e);
      }
    }
  }

}

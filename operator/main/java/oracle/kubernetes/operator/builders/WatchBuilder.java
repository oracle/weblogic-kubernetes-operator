// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.builders;

import io.kubernetes.client.ApiClient;
import io.kubernetes.client.ApiException;
import io.kubernetes.client.ProgressRequestBody;
import io.kubernetes.client.ProgressResponseBody;
import io.kubernetes.client.apis.CoreV1Api;
import io.kubernetes.client.apis.ExtensionsV1beta1Api;
import io.kubernetes.client.models.V1ConfigMap;
import io.kubernetes.client.models.V1Pod;
import io.kubernetes.client.models.V1Service;
import io.kubernetes.client.models.V1beta1Ingress;
import io.kubernetes.client.util.Watch;
import oracle.kubernetes.operator.TuningParameters;
import oracle.kubernetes.operator.helpers.ClientPool;
import oracle.kubernetes.operator.helpers.Pool;
import oracle.kubernetes.operator.work.ContainerResolver;
import oracle.kubernetes.weblogic.domain.v1.Domain;
import oracle.kubernetes.weblogic.domain.v1.api.WeblogicApi;

import com.squareup.okhttp.Call;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.function.BiFunction;

@SuppressWarnings("WeakerAccess")
public class WatchBuilder {
    /** Always true for watches. */
    private static final boolean WATCH = true;

    /** Ignored for watches. */
    private static final String START_LIST = null;

    private static WatchFactory FACTORY = new WatchFactoryImpl();

    private CallParamsImpl callParams = new CallParamsImpl();

    public interface WatchFactory {
        <T> WatchI<T> createWatch(Pool<ApiClient> pool, CallParams callParams, Class<?> responseBodyType, BiFunction<ApiClient, CallParams, Call> function) throws ApiException;
    }

    public WatchBuilder() {
        TuningParameters tuning = ContainerResolver.getInstance().getContainer().getSPI(TuningParameters.class);
        if (tuning != null) {
          callParams.setTimeoutSeconds(tuning.getWatchTuning().watchLifetime);
        }
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
     * Creates a web hook object to track service calls
     * @param namespace the namespace
     * @return the active web hook
     * @throws ApiException if there is an error on the call that sets up the web hook.
     */
    public WatchI<V1Service> createServiceWatch(String namespace) throws ApiException {
        return FACTORY.createWatch(ClientPool.getInstance(), callParams, V1Service.class, new ListNamespacedServiceCall(namespace));
    }

    private class ListNamespacedServiceCall implements BiFunction<ApiClient, CallParams, Call> {
        private String namespace;

        ListNamespacedServiceCall(String namespace) {
            this.namespace = namespace;
        }

        @Override
        public Call apply(ApiClient client, CallParams callParams) {
            try {
                return new CoreV1Api(client).listNamespacedServiceCall(namespace,
                              callParams.getPretty(), START_LIST,
                              callParams.getFieldSelector(), callParams.getIncludeUninitialized(), callParams.getLabelSelector(),
                              callParams.getLimit(), callParams.getResourceVersion(), callParams.getTimeoutSeconds(), WATCH, null, null);
            } catch (ApiException e) {
                throw new UncheckedApiException(e);
            }
        }
    }

    /**
     * Creates a web hook object to track pods
     * @param namespace the namespace
     * @return the active web hook
     * @throws ApiException if there is an error on the call that sets up the web hook.
     */
    public WatchI<V1Pod> createPodWatch(String namespace) throws ApiException {
        return FACTORY.createWatch(ClientPool.getInstance(), callParams, V1Pod.class, new ListPodCall(namespace));
    }

    private class ListPodCall implements BiFunction<ApiClient, CallParams, Call> {
        private String namespace;

        ListPodCall(String namespace) {
            this.namespace = namespace;
        }

        @Override
        public Call apply(ApiClient client, CallParams callParams) {
            try {
                return new CoreV1Api(client).listNamespacedPodCall(namespace, callParams.getPretty(),
                            START_LIST, callParams.getFieldSelector(), callParams.getIncludeUninitialized(),
                            callParams.getLabelSelector(), callParams.getLimit(), callParams.getResourceVersion(),
                            callParams.getTimeoutSeconds(), WATCH, null, null);
            } catch (ApiException e) {
                throw new UncheckedApiException(e);
            }
        }
    }

    /**
     * Creates a web hook object to track changes to the cluster ingress
     * @param namespace the namespace
     * @return the active web hook
     * @throws ApiException if there is an error on the call that sets up the web hook.
     */
    public WatchI<V1beta1Ingress> createIngressWatch(String namespace) throws ApiException {
        return FACTORY.createWatch(ClientPool.getInstance(), callParams, V1beta1Ingress.class, new ListIngressCall(namespace));
    }

    private class ListIngressCall implements BiFunction<ApiClient, CallParams, Call> {
        private String namespace;

        ListIngressCall(String namespace) {
            this.namespace = namespace;
        }

        @Override
        public Call apply(ApiClient client, CallParams callParams) {
            try {
                return new ExtensionsV1beta1Api(client).listNamespacedIngressCall(namespace,
                            callParams.getPretty(), START_LIST, callParams.getFieldSelector(),
                            callParams.getIncludeUninitialized(), callParams.getLabelSelector(), callParams.getLimit(),
                            callParams.getResourceVersion(), callParams.getTimeoutSeconds(), WATCH, null, null);
            } catch (ApiException e) {
                throw new UncheckedApiException(e);
            }
        }
    }

    /**
     * Creates a web hook object to track changes to weblogic domains in one namespaces
     * @param namespace the namespace in which to track domains
     * @return the active web hook
     * @throws ApiException if there is an error on the call that sets up the web hook.
     */
    public WatchI<Domain> createDomainWatch(String namespace) throws ApiException {
        return FACTORY.createWatch(ClientPool.getInstance(), callParams, Domain.class, new ListDomainsCall(namespace));
    }

    private class ListDomainsCall implements BiFunction<ApiClient, CallParams, Call> {
        private String namespace;

        ListDomainsCall(String namespace) {
            this.namespace = namespace;
        }

        @Override
        public Call apply(ApiClient client, CallParams callParams) {
            try {
                return new WeblogicApi(client).listWebLogicOracleV1NamespacedDomainCall(namespace,
                            callParams.getPretty(), START_LIST, callParams.getFieldSelector(),
                            callParams.getIncludeUninitialized(), callParams.getLabelSelector(), callParams.getLimit(),
                            callParams.getResourceVersion(), callParams.getTimeoutSeconds(), WATCH, null, null);
            } catch (ApiException e) {
                throw new UncheckedApiException(e);
            }
        }
    }

    /**
     * Creates a web hook object to track config map calls
     * @param namespace the namespace
     * @return the active web hook
     * @throws ApiException if there is an error on the call that sets up the web hook.
     */
    public WatchI<V1ConfigMap> createConfigMapWatch(String namespace) throws ApiException {
        return FACTORY.createWatch(ClientPool.getInstance(), callParams, V1ConfigMap.class, new ListNamespacedConfigMapCall(namespace));
    }

    private class ListNamespacedConfigMapCall implements BiFunction<ApiClient, CallParams, Call> {
        private String namespace;

        ListNamespacedConfigMapCall(String namespace) {
            this.namespace = namespace;
        }

        @Override
        public Call apply(ApiClient client, CallParams callParams) {
            try {
                return new CoreV1Api(client).listNamespacedConfigMapCall(namespace,
                              callParams.getPretty(), START_LIST,
                              callParams.getFieldSelector(), callParams.getIncludeUninitialized(), callParams.getLabelSelector(),
                              callParams.getLimit(), callParams.getResourceVersion(), callParams.getTimeoutSeconds(), WATCH, null, null);
            } catch (ApiException e) {
                throw new UncheckedApiException(e);
            }
        }
    }

    /**
     * Sets a value for the fieldSelector parameter for the call that will set up this watch. Defaults to null.
     * @param fieldSelector the desired value
     * @return the updated builder
     */
    public WatchBuilder withFieldSelector(String fieldSelector) {
        callParams.setFieldSelector(fieldSelector);
        return this;
    }

    public WatchBuilder withIncludeUninitialized(Boolean includeUninitialized) {
        callParams.setIncludeUninitialized(includeUninitialized);
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

    private String asList(String... selectors) {
        return String.join(",", selectors);
    }

    public WatchBuilder withLimit(Integer limit) {
        callParams.setLimit(limit);
        return this;
    }

    public WatchBuilder withPrettyPrinting() {
        callParams.setPretty("true");
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

    public WatchBuilder withProgressListener(ProgressResponseBody.ProgressListener progressListener) {
        callParams.setProgressListener(progressListener);
        return this;
    }

    public WatchBuilder withProgressRequestListener(ProgressRequestBody.ProgressRequestListener progressRequestListener) {
        callParams.setProgressRequestListener(progressRequestListener);
        return this;
    }

    static class WatchFactoryImpl implements WatchFactory {
        @Override
        public <T> WatchI<T> createWatch(Pool<ApiClient> pool, CallParams callParams, Class<?> responseBodyType, BiFunction<ApiClient, CallParams, Call> function) throws ApiException {
          ApiClient client = pool.take();
            try {
                return new WatchImpl<T>(pool, client, Watch.createWatch(client, function.apply(client, callParams), getType(responseBodyType)));
            } catch (UncheckedApiException e) {
                throw e.getCause();
            }
        }
    }
}
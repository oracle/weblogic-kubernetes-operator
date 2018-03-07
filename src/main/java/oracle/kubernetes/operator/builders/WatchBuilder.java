// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at http://oss.oracle.com/licenses/upl.
package oracle.kubernetes.operator.builders;

import com.squareup.okhttp.Call;
import io.kubernetes.client.ApiException;
import io.kubernetes.client.ProgressRequestBody;
import io.kubernetes.client.ProgressResponseBody;
import io.kubernetes.client.models.V1Pod;
import io.kubernetes.client.models.V1Service;
import io.kubernetes.client.models.V1beta1Ingress;
import io.kubernetes.client.util.Watch;
import oracle.kubernetes.weblogic.domain.v1.Domain;
import oracle.kubernetes.operator.KubernetesConstants;
import oracle.kubernetes.operator.helpers.ClientHolder;

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

    private ClientHolder clientHolder;
    private CallParamsImpl callParams = new CallParamsImpl();

    public interface WatchFactory {
        <T> WatchI<T> createWatch(ClientHolder clientHolder, CallParams callParams, Class<?> responseBodyType, BiFunction<ClientHolder, CallParams, Call> function) throws ApiException;
    }

    public WatchBuilder(ClientHolder clientHolder) {
        this.clientHolder = clientHolder;
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
        return FACTORY.createWatch(clientHolder, callParams, V1Service.class, new ListNamespacedServiceCall(namespace));
    }

    private class ListNamespacedServiceCall implements BiFunction<ClientHolder, CallParams, Call> {
        private String namespace;

        ListNamespacedServiceCall(String namespace) {
            this.namespace = namespace;
        }

        @Override
        public Call apply(ClientHolder clientHolder, CallParams callParams) {
            try {
                return clientHolder.getCoreApiClient().listNamespacedServiceCall(namespace,
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
        return FACTORY.createWatch(clientHolder, callParams, V1Pod.class, new ListPodCall(namespace));
    }

    private class ListPodCall implements BiFunction<ClientHolder, CallParams, Call> {
        private String namespace;

        ListPodCall(String namespace) {
            this.namespace = namespace;
        }

        @Override
        public Call apply(ClientHolder clientHolder, CallParams callParams) {
            try {
                return clientHolder.getCoreApiClient().listNamespacedPodCall(namespace, callParams.getPretty(),
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
        return FACTORY.createWatch(clientHolder, callParams, V1beta1Ingress.class, new ListIngressCall(namespace));
    }

    private class ListIngressCall implements BiFunction<ClientHolder, CallParams, Call> {
        private String namespace;

        ListIngressCall(String namespace) {
            this.namespace = namespace;
        }

        @Override
        public Call apply(ClientHolder clientHolder, CallParams callParams) {
            try {
                return clientHolder.getExtensionsV1beta1ApiClient().listNamespacedIngressCall(namespace,
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
        return FACTORY.createWatch(clientHolder, callParams, Domain.class, new ListDomainsCall(namespace));
    }

    private class ListDomainsCall implements BiFunction<ClientHolder, CallParams, Call> {
        private String namespace;

        ListDomainsCall(String namespace) {
            this.namespace = namespace;
        }

        @Override
        public Call apply(ClientHolder clientHolder, CallParams callParams) {
            try {
                return clientHolder.getCustomObjectsApiClient().listNamespacedCustomObjectCall(
                            KubernetesConstants.DOMAIN_GROUP, KubernetesConstants.DOMAIN_VERSION, 
                            namespace, KubernetesConstants.DOMAIN_PLURAL,
                            callParams.getPretty(), callParams.getLabelSelector(), callParams.getResourceVersion(), 
                            WATCH, null, null);
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

    @SuppressWarnings("unused")
    public WatchBuilder withProgressListener(ProgressResponseBody.ProgressListener progressListener) {
        callParams.setProgressListener(progressListener);
        return this;
    }

    @SuppressWarnings("unused")
    public WatchBuilder withProgressRequestListener(ProgressRequestBody.ProgressRequestListener progressRequestListener) {
        callParams.setProgressRequestListener(progressRequestListener);
        return this;
    }

    static class WatchFactoryImpl implements WatchFactory {
        @Override
        public <T> WatchI<T> createWatch(ClientHolder clientHolder, CallParams callParams, Class<?> responseBodyType, BiFunction<ClientHolder, CallParams, Call> function) throws ApiException {
            try {
                return new WatchImpl<T>(Watch.createWatch(clientHolder.getApiClient(), function.apply(clientHolder, callParams), getType(responseBodyType)));
            } catch (UncheckedApiException e) {
                throw e.getCause();
            }
        }
    }
}

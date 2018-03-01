// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at http://oss.oracle.com/licenses/upl.
package oracle.kubernetes.operator.builders;

import com.squareup.okhttp.Call;
import io.kubernetes.client.ApiClient;
import io.kubernetes.client.ApiException;
import io.kubernetes.client.ProgressRequestBody;
import io.kubernetes.client.ProgressResponseBody;
import io.kubernetes.client.models.V1Namespace;
import io.kubernetes.client.models.V1Pod;
import io.kubernetes.client.models.V1Service;
import io.kubernetes.client.models.V1beta1Ingress;
import io.kubernetes.client.util.Watch;
import oracle.kubernetes.operator.domain.model.oracle.kubernetes.weblogic.domain.v1.Domain;
import oracle.kubernetes.operator.helpers.ClientHolder;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.function.BiFunction;

@SuppressWarnings("unused")
public class WatchBuilder {

    private static final String START_LIST = "";
    private static final boolean WATCH = true;
    private static WatchFactory FACTORY = new WatchFactoryImpl();
    private ClientHolder clientHolder;

    private CallParams callParams = new CallParams();

    public interface WatchFactory {
        <T> Watch<T> createWatch(ClientHolder clientHolder, CallParams callParams, Class<?> responseBodyType, BiFunction<ClientHolder, CallParams, Call> function) throws ApiException;
    }

    public WatchBuilder(ClientHolder clientHolder) {
        this.clientHolder = clientHolder;
    }

    /**
     * Creates a web hook object to track changes to the namespaces.
     * @return the active web hook
     * @throws ApiException if there is an error on the call that sets up the web hook.
     */
    public Watch<V1Namespace> createNamespaceWatch() throws ApiException {
        return FACTORY.createWatch(clientHolder, callParams, V1Namespace.class, WatchBuilder::listNamespaceCall);
    }

    private ApiClient getApiClient() {
        return clientHolder.getApiClient();
    }

    private static Call listNamespaceCall(ClientHolder clientHolder, CallParams callParams) {
        try {
            return clientHolder.getCoreApiClient().listNamespaceCall(
                        callParams.getPretty(), START_LIST, callParams.getFieldSelector(),
                        callParams.getIncludeUninitialized(), callParams.getLabelSelector(), callParams.getLimit(),
                        callParams.getResourceVersion(), callParams.getTimeoutSeconds(), WATCH,
                        callParams.getProgressListener(), callParams.getProgressRequestListener());
        } catch (ApiException e) {
            throw new UncheckedApiException(e);
        }
    }

    private static <T> Type getType(Class<?> responseBodyType) {
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
     * @return the active web hook
     * @throws ApiException if there is an error on the call that sets up the web hook.
     */
    public Watch<V1Service> createServiceWatch(String namespace) throws ApiException {
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
     * @return the active web hook
     * @throws ApiException if there is an error on the call that sets up the web hook.
     */
    public Watch<V1Pod> createPodWatch(String namespace) throws ApiException {
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
     * @return the active web hook
     * @throws ApiException if there is an error on the call that sets up the web hook.
     */
    public Watch<V1beta1Ingress> createIngressWatch(String namespace) throws ApiException {
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
     * Creates a web hook object to track changes to weblogic domains in all namespaces
     * @return the active web hook
     * @throws ApiException if there is an error on the call that sets up the web hook.
     */
    public Watch<Domain> createDomainsInAllNamespacesWatch() throws ApiException {
        return FACTORY.createWatch(clientHolder, callParams, Domain.class, WatchBuilder::listDomainsForAllNamespacesCall);
    }

    private static Call listDomainsForAllNamespacesCall(ClientHolder clientHolder, CallParams callParams) {
        try {
            return clientHolder.getWeblogicApiClient().listWebLogicOracleV1DomainForAllNamespacesCall(START_LIST,
                        callParams.getFieldSelector(), callParams.getIncludeUninitialized(), callParams.getLabelSelector(),
                        callParams.getLimit(), callParams.getPretty(), callParams.getResourceVersion(),
                        callParams.getTimeoutSeconds(), WATCH, callParams.getProgressListener(), callParams.getProgressRequestListener());
        } catch (ApiException e) {
            throw new UncheckedApiException(e);
        }
    }

    /**
     * Creates a web hook object to track changes to weblogic domains in one namespaces
     * @param namespace the namespace in which to track domains
     * @return the active web hook
     * @throws ApiException if there is an error on the call that sets up the web hook.
     */
    public Watch<Domain> createDomainsInNamespaceWatch(String namespace) throws ApiException {
        return FACTORY.createWatch(clientHolder, callParams, Domain.class, new ListDomainsInNamespaceCall(namespace));
    }

    private class ListDomainsInNamespaceCall implements BiFunction<ClientHolder, CallParams, Call> {
        private String namespace;

        ListDomainsInNamespaceCall(String namespace) {
            this.namespace = namespace;
        }

        @Override
        public Call apply(ClientHolder clientHolder, CallParams callParams) {
            try {
                return clientHolder.getWeblogicApiClient().listWebLogicOracleV1NamespacedDomainCall(namespace,
                            callParams.getPretty(), START_LIST, callParams.getFieldSelector(),
                            callParams.getIncludeUninitialized(), callParams.getLabelSelector(), callParams.getLimit(),
                            callParams.getResourceVersion(), callParams.getTimeoutSeconds(), WATCH, null, null);
            } catch (ApiException e) {
                throw new UncheckedApiException(e);
            }
        }
    }

    /**
     * Creates a web hook object to track changes to custom objects
     * @param namespace the namespace in which to track custom objects.
     * @param responseBodyType the type of objects returned in the events
     * @param group the custom resource group name
     * @param version the custom resource version
     * @param plural the custom resource plural name    @return the active web hook
     * @throws ApiException if there is an error on the call that sets up the web hook.
     */
    public <T> Watch<T> createCustomObjectWatch(String namespace, Class<?> responseBodyType, String group, String version, String plural) throws ApiException {
        return FACTORY.createWatch(clientHolder, callParams, responseBodyType, new ListCustomObjectsInNamespaceCall(namespace, group, version, plural));
    }

    private class ListCustomObjectsInNamespaceCall implements BiFunction<ClientHolder, CallParams, Call> {
        private String namespace;
        private String group;
        private String version;
        private String plural;

        ListCustomObjectsInNamespaceCall(String namespace, String group, String version, String plural) {
            this.namespace = namespace;
            this.group = group;
            this.version = version;
            this.plural = plural;
        }

        @Override
        public Call apply(ClientHolder clientHolder, CallParams callParams) {
            try {
                return clientHolder.getCustomObjectsApiClient().listNamespacedCustomObjectCall(
                            group, version, namespace, plural,
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
        public <T> Watch<T> createWatch(ClientHolder clientHolder, CallParams callParams, Class<?> responseBodyType, BiFunction<ClientHolder, CallParams, Call> function) throws ApiException {
            try {
                return Watch.createWatch(clientHolder.getApiClient(), function.apply(clientHolder, callParams), getType(responseBodyType));
            } catch (UncheckedApiException e) {
                throw e.getCause();
            }
        }
    }
}

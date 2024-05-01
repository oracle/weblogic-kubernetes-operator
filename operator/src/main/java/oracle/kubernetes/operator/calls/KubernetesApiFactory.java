// Copyright (c) 2024, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.calls;

import java.util.function.UnaryOperator;

import io.kubernetes.client.common.KubernetesListObject;
import io.kubernetes.client.common.KubernetesObject;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.apis.VersionApi;
import io.kubernetes.client.util.generic.GenericKubernetesApi;
import io.kubernetes.client.util.generic.KubernetesApiResponse;
import io.kubernetes.client.util.generic.options.DeleteOptions;
import io.kubernetes.client.util.generic.options.ListOptions;

public interface KubernetesApiFactory {
  default <A extends KubernetesObject, L extends KubernetesListObject>
      KubernetesApi<A, L> create(Class<A> apiTypeClass, Class<L> apiListTypeClass,
                                 String apiGroup, String apiVersion, String resourcePlural,
                                 UnaryOperator<ApiClient> clientSelector) {
    return new KubernetesApiImpl<>(apiTypeClass, apiListTypeClass, apiGroup, apiVersion,
            resourcePlural, clientSelector);
  }

  class KubernetesApiImpl<A extends KubernetesObject, L extends KubernetesListObject>
      extends GenericKubernetesApi<A, L> implements KubernetesApi<A, L> {
    public KubernetesApiImpl(Class<A> apiTypeClass, Class<L> apiListTypeClass,
                             String apiGroup, String apiVersion, String resourcePlural,
                             UnaryOperator<ApiClient> clientSelector) {
      super(apiTypeClass, apiListTypeClass, apiGroup, apiVersion, resourcePlural,
              clientSelector.apply(Client.getInstance()));
    }

    @Override
    public KubernetesApiResponse<RequestBuilder.V1StatusObject> deleteCollection(
        String namespace, ListOptions listOptions, DeleteOptions deleteOptions) {
      CoreV1Api c = new CoreV1Api(Client.getInstance());
      try {
        return new KubernetesApiResponse<>(new RequestBuilder.V1StatusObject(
            c.deleteCollectionNamespacedPod(namespace, null, null, null, listOptions.getFieldSelector(), null,
                listOptions.getLabelSelector(), null, null, null, null, null, null, null, deleteOptions)));
      } catch (ApiException e) {
        return RequestStep.responseFromApiException(c.getApiClient(), e);
      }
    }

    @Override
    public KubernetesApiResponse<RequestBuilder.StringObject> logs(String namespace, String name, String container) {
      CoreV1Api c = new CoreV1Api(Client.getInstance());
      try {
        return new KubernetesApiResponse<>(new RequestBuilder.StringObject(
            c.readNamespacedPodLog(name, namespace, container,
                null, null, null, null, null, null, null, null)));
      } catch (ApiException e) {
        return RequestStep.responseFromApiException(c.getApiClient(), e);
      }
    }

    @Override
    public KubernetesApiResponse<RequestBuilder.VersionInfoObject> getVersionCode() {
      VersionApi c = new VersionApi(Client.getInstance());
      try {
        return new KubernetesApiResponse<>(new RequestBuilder.VersionInfoObject(c.getCode()));
      } catch (ApiException e) {
        return RequestStep.responseFromApiException(c.getApiClient(), e);
      }
    }
  }

}

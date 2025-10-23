// Copyright (c) 2024, 2025, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.calls;

import java.util.Arrays;
import java.util.function.Function;
import java.util.function.UnaryOperator;

import io.kubernetes.client.common.KubernetesListObject;
import io.kubernetes.client.common.KubernetesObject;
import io.kubernetes.client.custom.V1Patch;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.apis.CustomObjectsApi;
import io.kubernetes.client.openapi.apis.VersionApi;
import io.kubernetes.client.util.PatchUtils;
import io.kubernetes.client.util.generic.GenericKubernetesApi;
import io.kubernetes.client.util.generic.KubernetesApiResponse;
import io.kubernetes.client.util.generic.options.DeleteOptions;
import io.kubernetes.client.util.generic.options.ListOptions;
import io.kubernetes.client.util.generic.options.UpdateOptions;

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
    private final Class<A> apiTypeClass;
    private final Class<L> apiListTypeClass;
    private final String apiGroup;
    private final String apiVersion;
    private final String resourcePlural;

    /**
     * Create the impl class.
     * @param apiTypeClass API type class
     * @param apiListTypeClass API list type class
     * @param apiGroup group
     * @param apiVersion version
     * @param resourcePlural plural
     * @param clientSelector client selector
     */
    public KubernetesApiImpl(Class<A> apiTypeClass, Class<L> apiListTypeClass,
                             String apiGroup, String apiVersion, String resourcePlural,
                             UnaryOperator<ApiClient> clientSelector) {
      super(apiTypeClass, apiListTypeClass, apiGroup, apiVersion, resourcePlural,
              clientSelector.apply(Client.getInstance()));
      this.apiTypeClass = apiTypeClass;
      this.apiListTypeClass = apiListTypeClass;
      this.apiGroup = apiGroup;
      this.apiVersion = apiVersion;
      this.resourcePlural = resourcePlural;
    }

    @Override
    public KubernetesApiResponse<A> updateStatus(
        A object, Function<A, Object> status, final UpdateOptions updateOptions) {
      CustomObjectsApi c = new CustomObjectsApi(Client.getInstance());
      try {
        return new KubernetesApiResponse<>(PatchUtils.patch(
            apiTypeClass,
            () ->
                c.patchNamespacedCustomObjectStatusCall(
                    apiGroup, apiVersion, object.getMetadata().getNamespace(), resourcePlural,
                        object.getMetadata().getName(),
                        Arrays.asList(new StatusPatch(status.apply(object))),
                        null, null, null, null, null
                    ),
            V1Patch.PATCH_FORMAT_JSON_PATCH,
            c.getApiClient()));
      } catch (ApiException e) {
        return RequestStep.responseFromApiException(c.getApiClient(), e);
      }
    }

    @Override
    public KubernetesApiResponse<RequestBuilder.V1StatusObject> deleteCollection(
        String namespace, ListOptions listOptions, DeleteOptions deleteOptions) {
      CoreV1Api c = new CoreV1Api(Client.getInstance());
      try {
        return new KubernetesApiResponse<>(new RequestBuilder.V1StatusObject(
            c.deleteCollectionNamespacedPod(namespace, null, null, null, listOptions.getFieldSelector(), null, null,
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
                null, null, null, null, null, null, null, null, null)));
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

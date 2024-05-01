// Copyright (c) 2024, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.calls;

import io.kubernetes.client.common.KubernetesListObject;
import io.kubernetes.client.common.KubernetesObject;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.util.generic.GenericKubernetesApi;

import static java.util.concurrent.TimeUnit.SECONDS;

public interface WatchApiFactory {
  default <A extends KubernetesObject, L extends KubernetesListObject>
      WatchApi<A> create(Class<A> apiTypeClass, Class<L> apiListTypeClass,
                                 String apiGroup, String apiVersion, String resourcePlural) {
    return new WatchApiImpl<>(apiTypeClass, apiListTypeClass, apiGroup, apiVersion, resourcePlural);
  }

  class WatchApiImpl<A extends KubernetesObject, L extends KubernetesListObject>
      extends GenericKubernetesApi<A, L> implements WatchApi<A> {
    public WatchApiImpl(Class<A> apiTypeClass, Class<L> apiListTypeClass,
                             String apiGroup, String apiVersion, String resourcePlural) {
      super(apiTypeClass, apiListTypeClass, apiGroup, apiVersion, resourcePlural, getWatchClient(Client.getInstance()));
    }

    private static ApiClient getWatchClient(ApiClient client) {
      return client.setHttpClient(client.getHttpClient().newBuilder().readTimeout(0, SECONDS).build());
    }
  }

}

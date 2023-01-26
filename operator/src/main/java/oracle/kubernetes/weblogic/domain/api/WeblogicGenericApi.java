// Copyright (c) 2017, 2023, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.weblogic.domain.api;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.util.generic.GenericKubernetesApi;
import io.kubernetes.client.util.generic.KubernetesApiResponse;
import oracle.kubernetes.operator.helpers.HeaderModifierInterceptor;
import oracle.kubernetes.weblogic.domain.model.KubernetesListObjectImpl;
import oracle.kubernetes.weblogic.domain.model.PartialObjectMetadata;

public class WeblogicGenericApi extends GenericKubernetesApi<PartialObjectMetadata, KubernetesListObjectImpl> {
  private final ApiClient apiClient;

  /**
   * Constructs a generic api client used to get the partial CRD metadata.
   * @param apiClient api client.
   */
  public WeblogicGenericApi(ApiClient apiClient) {
    super(PartialObjectMetadata.class, KubernetesListObjectImpl.class, "apiextensions.k8s.io", "v1",
        "customresourcedefinitions", apiClient);
    this.apiClient = apiClient;
  }

  /**
   * Get CRD metadata.
   *
   * @return CRD metadata
   */
  public PartialObjectMetadata readCustomResourceDefinitionMetadata(String name) {
    final PartialObjectMetadata partialObjectMetadata;
    HeaderModifierInterceptor.setPartialMetadataHeader(true);
    try {
      partialObjectMetadata = toPartialObjectMetadata(get(name));
    } finally {
      HeaderModifierInterceptor.removePartialMetadataHeader();
    }
    return partialObjectMetadata;
  }

  private PartialObjectMetadata toPartialObjectMetadata(Object o) {
    if (o == null) {
      return null;
    }
    return this.apiClient.getJSON().getGson().fromJson(convertToJson(((KubernetesApiResponse) o).getObject()),
        PartialObjectMetadata.class);
  }

  private JsonElement convertToJson(Object o) {
    Gson gson = apiClient.getJSON().getGson();
    return gson.toJsonTree(o);
  }
}

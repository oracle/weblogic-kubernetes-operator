// Copyright (c) 2017, 2023, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.weblogic.domain.api;

import java.io.IOException;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.util.generic.GenericKubernetesApi;
import io.kubernetes.client.util.generic.KubernetesApiResponse;
import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import oracle.kubernetes.weblogic.domain.model.PartialObjectMetadata;
import oracle.kubernetes.weblogic.domain.model.PartialObjectMetadataList;

public class WeblogicGenericApi extends GenericKubernetesApi<PartialObjectMetadata,PartialObjectMetadataList> {
  public static final String PARTIAL_OBJECT_METADATA_HEADER =
      "application/json;as=PartialObjectMetadata;g=meta.k8s.io;v=v1,application/json";
  private final ApiClient apiClient;

  /**
   * Constructs a generic api client used to get the partial CRD metadata.
   * @param apiClient api client.
   */
  public WeblogicGenericApi(ApiClient apiClient) {
    super(PartialObjectMetadata.class, PartialObjectMetadataList.class, "apiextensions.k8s.io", "v1",
        "customresourcedefinitions", apiClient);
    this.apiClient = apiClient;
  }

  /**
   * Get CRD metadata.
   *
   * @return CRD metadata
   * @throws ApiException on failure
   */
  public PartialObjectMetadata readCustomResourceDefinitionMetadata(String name) {
    OkHttpClient httpClient = this.apiClient.getHttpClient();
    // Add the interceptor to insert the Accept header to get the metatdata-only response
    this.apiClient.setHttpClient(httpClient.newBuilder()
        .addInterceptor(new ReplaceHeaderInterceptor("Accept", PARTIAL_OBJECT_METADATA_HEADER)).build());
    PartialObjectMetadata partialObjectMetadata = toPartialObjectMetadata(get(name));
    // Remove the interceptor
    this.apiClient.setHttpClient(httpClient);
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

  private class ReplaceHeaderInterceptor implements Interceptor {
    private final String headerName;
    private final String headerValue;

    public ReplaceHeaderInterceptor(String headerName, String headerValue) {
      this.headerName = headerName;
      this.headerValue = headerValue;
    }

    public Response intercept(Chain chain) throws IOException {
      Request request = chain.request();
      Request newRequest;

      try {
        newRequest = request.newBuilder()
            .removeHeader(headerName)
            .addHeader(headerName, headerValue)
            .build();
      } catch (Exception e) {
        return chain.proceed(request);
      }

      return chain.proceed(newRequest);
    }
  }
}

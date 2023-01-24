// Copyright (c) 2023, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helpers;

import java.io.IOException;

import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;

public class HeaderModifierInterceptor implements Interceptor {

  private static final String PARTIAL_OBJECT_METADATA_HEADER =
      "application/json;as=PartialObjectMetadata;g=meta.k8s.io;v=v1,application/json";

  private static final ThreadLocal<Boolean> partialMetadataHeader = ThreadLocal.withInitial(() -> false);

  /**
   * Intereceptor method to modify the request headers.
   *
   * @param chain Chain
   * @return Response response
   */
  public Response intercept(Chain chain) throws IOException {
    Request request = chain.request();
    Request newRequest;
    if (Boolean.TRUE.equals(partialMetadataHeader.get())) {
      try {
        newRequest = request.newBuilder()
            //.removeHeader("Accept")
            .header("Accept", PARTIAL_OBJECT_METADATA_HEADER)
            .build();
      } catch (Exception e) {
        return chain.proceed(request);
      }
      return chain.proceed(newRequest);
    } else {
      return chain.proceed(request);
    }
  }

  public static void setPartialMetadataHeader(Boolean value) {
    partialMetadataHeader.set(value);
  }

  public static void removePartialMetadataHeader() {
    partialMetadataHeader.remove();
  }
}
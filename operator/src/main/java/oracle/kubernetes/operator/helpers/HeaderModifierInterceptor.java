// Copyright (c) 2023, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helpers;

import java.io.IOException;
import javax.annotation.Nonnull;

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
  @Nonnull
  public Response intercept(Chain chain) throws IOException {
    final Request request = chain.request();
    final Request newRequest;
    if (Boolean.FALSE.equals(partialMetadataHeader.get())) {
      return chain.proceed(request);
    } else {
      newRequest = request.newBuilder()
          .header("Accept", PARTIAL_OBJECT_METADATA_HEADER)
          .build();
      return chain.proceed(newRequest);
    }
  }

  public static void setPartialMetadataHeader(Boolean value) {
    partialMetadataHeader.set(value);
  }

  public static void removePartialMetadataHeader() {
    partialMetadataHeader.remove();
  }
}
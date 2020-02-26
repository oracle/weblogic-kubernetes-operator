// Copyright (c) 2017, 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.calls;

import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.ApiException;
import oracle.kubernetes.operator.helpers.Pool;

public interface SynchronousCallDispatcher {
  <T> T execute(
      SynchronousCallFactory<T> factory, RequestParams requestParams, Pool<ApiClient> helper)
      throws ApiException;
}

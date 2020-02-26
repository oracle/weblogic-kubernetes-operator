// Copyright (c) 2017, 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.calls;

import io.kubernetes.client.openapi.ApiCallback;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.ApiException;

@FunctionalInterface
public interface CallFactory<T> {
  CancellableCall generate(
      RequestParams requestParams, ApiClient client, String cont, ApiCallback<T> callback)
      throws ApiException;
}

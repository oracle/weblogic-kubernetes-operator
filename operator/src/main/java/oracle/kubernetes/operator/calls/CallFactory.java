// Copyright 2017,2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.calls;

import io.kubernetes.client.ApiCallback;
import io.kubernetes.client.ApiClient;
import io.kubernetes.client.ApiException;

@FunctionalInterface
public interface CallFactory<T> {
  CancellableCall generate(
      RequestParams requestParams, ApiClient client, String cont, ApiCallback<T> callback)
      throws ApiException;
}

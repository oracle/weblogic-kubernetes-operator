// Copyright (c) 2018, 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.calls;

import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.ApiException;

public interface SynchronousCallFactory<R> {
  R execute(ApiClient client, RequestParams requestParams) throws ApiException;
}

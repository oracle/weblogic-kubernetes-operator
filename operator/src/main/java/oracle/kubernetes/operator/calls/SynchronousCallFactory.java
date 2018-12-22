// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.calls;

import io.kubernetes.client.ApiClient;
import io.kubernetes.client.ApiException;

public interface SynchronousCallFactory<R> {
  R execute(ApiClient client, RequestParams requestParams) throws ApiException;
}

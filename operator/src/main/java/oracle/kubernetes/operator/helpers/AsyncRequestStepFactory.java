// Copyright (c) 2018, 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helpers;

import oracle.kubernetes.operator.calls.CallFactory;
import oracle.kubernetes.operator.calls.RequestParams;
import oracle.kubernetes.operator.work.Step;

public interface AsyncRequestStepFactory {
  <T> Step createRequestAsync(
      ResponseStep<T> next,
      RequestParams requestParams,
      CallFactory<T> factory,
      ClientPool helper,
      int timeoutSeconds,
      int maxRetryCount,
      String fieldSelector,
      String labelSelector,
      String resourceVersion);
}

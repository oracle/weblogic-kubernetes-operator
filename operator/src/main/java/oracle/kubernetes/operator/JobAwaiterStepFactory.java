// Copyright (c) 2018, 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator;

import io.kubernetes.client.openapi.models.V1Job;
import oracle.kubernetes.operator.work.Step;

public interface JobAwaiterStepFactory {

  Step waitForReady(V1Job job, Step next);
}

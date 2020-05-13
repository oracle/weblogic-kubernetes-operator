// Copyright (c) 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes.actions.impl;

import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.models.V1Job;
import oracle.weblogic.kubernetes.actions.impl.primitive.Kubernetes;

public class Job {

  public static String createJob(V1Job jodBody) throws ApiException {
    return Kubernetes.createJob(jodBody);
  }

}

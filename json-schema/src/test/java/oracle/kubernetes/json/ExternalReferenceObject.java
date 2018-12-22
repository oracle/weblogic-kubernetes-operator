// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.json;

import io.kubernetes.client.models.V1EnvVar;

class ExternalReferenceObject {
  private SimpleObject simple;
  private V1EnvVar[] env;
}

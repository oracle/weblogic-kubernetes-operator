// Copyright (c) 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.tuning;

/**
 * A collection of tuning parameters to control calls to Kubernetes.
 */
public interface CallBuilderTuning {

  int getCallRequestLimit();

  int getCallMaxRetryCount();

  int getCallTimeoutSeconds();
}

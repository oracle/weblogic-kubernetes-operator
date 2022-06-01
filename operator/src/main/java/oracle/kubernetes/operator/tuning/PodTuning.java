// Copyright (c) 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.tuning;

/**
 * A collection of tuning parameters to control pod creation.
 */
public interface PodTuning {

  int getReadinessProbeInitialDelaySeconds();

  int getReadinessProbeTimeoutSeconds();

  int getReadinessProbePeriodSeconds();

  int getReadinessProbeSuccessThreshold();

  int getReadinessProbeFailureThreshold();

  int getLivenessProbeInitialDelaySeconds();

  int getLivenessProbeTimeoutSeconds();

  int getLivenessProbePeriodSeconds();

  int getLivenessProbeSuccessThreshold();

  int getLivenessProbeFailureThreshold();
}

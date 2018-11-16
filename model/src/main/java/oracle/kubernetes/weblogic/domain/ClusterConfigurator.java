// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.weblogic.domain;

import io.kubernetes.client.models.V1PodSecurityContext;
import io.kubernetes.client.models.V1SecurityContext;

/** An interface for an object to configure a cluster in a test. */
@SuppressWarnings("UnusedReturnValue")
public interface ClusterConfigurator {
  ClusterConfigurator withReplicas(int replicas);

  ClusterConfigurator withDesiredState(String state);

  ClusterConfigurator withEnvironmentVariable(String name, String value);

  ClusterConfigurator withServerStartState(String cluster);

  ClusterConfigurator withServerStartPolicy(String policy);

  ClusterConfigurator withReadinessProbeSettings(
      Integer initialDelay, Integer timeout, Integer period);

  ClusterConfigurator withLivenessProbeSettings(
      Integer initialDelay, Integer timeout, Integer period);

  ClusterConfigurator withNodeSelector(String labelKey, String labelValue);

  ClusterConfigurator withRequestRequirement(String resource, String quantity);

  ClusterConfigurator withLimitRequirement(String resource, String quantity);

  ClusterConfigurator withContainerSecurityContext(V1SecurityContext containerSecurityContext);

  ClusterConfigurator withPodSecurityContext(V1PodSecurityContext podSecurityContext);

  ClusterConfigurator withAdditionalVolume(String name, String path);

  ClusterConfigurator withAdditionalVolumeMount(String name, String path);
}

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

  ClusterConfigurator withMaxUnavailable(int maxUnavailable);

  ClusterConfigurator withDesiredState(String state);

  ClusterConfigurator withEnvironmentVariable(String name, String value);

  ClusterConfigurator withServerStartState(String cluster);

  ClusterConfigurator withServerStartPolicy(String policy);

  ClusterConfigurator withReadinessProbeSettings(
      Integer initialDelay, Integer timeout, Integer period);

  ClusterConfigurator withLivenessProbeSettings(
      Integer initialDelay, Integer timeout, Integer period);

  /**
   * Add a node label to the Cluster's node selector.
   *
   * @param labelKey the pod label key
   * @param labelValue the pod label value
   * @return this object
   */
  ClusterConfigurator withNodeSelector(String labelKey, String labelValue);

  /**
   * Add a resource requirement at cluster level. The requests for memory are measured in bytes. You
   * can express memory as a plain integer or as a fixed-point integer using one of these suffixes:
   * E, P, T, G, M, K. You can also use the power-of-two equivalents: Ei, Pi, Ti, Gi, Mi, Ki. The
   * requests for cpu are measured in cpu units and can be expressed in millicores i.e. 100m is the
   * same as 0.1
   *
   * @param resource the resource to be added as requirement cpu or memory
   * @param quantity the quantity required for the resource
   * @return this object
   */
  ClusterConfigurator withRequestRequirement(String resource, String quantity);

  /**
   * Add a resource limit at cluster level, the requests for memory are measured in bytes. You can
   * express memory as a plain integer or as a fixed-point integer using one of these suffixes: E,
   * P, T, G, M, K. You can also use the power-of-two equivalents: Ei, Pi, Ti, Gi, Mi, Ki. The
   * requests for cpu are measured in cpu units and can be expressed in millicores i.e. 100m is the
   * same as 0.1
   *
   * @param resource the resource to be added as requirement cpu or memory
   * @param quantity the quantity required for the resource
   * @return this object
   */
  ClusterConfigurator withLimitRequirement(String resource, String quantity);

  /**
   * Add security constraints at container level, if the same constraint is also defined at pod
   * level then container constraint take precedence.
   *
   * @param containerSecurityContext the security context object
   * @return this object
   */
  ClusterConfigurator withContainerSecurityContext(V1SecurityContext containerSecurityContext);

  /**
   * Add security constraints at container level, if the same constraint is also defined at pod
   * level then container constraint take precedence.
   *
   * @param podSecurityContext pod-level security attributes to be added to this ClusterConfigurator
   * @return this object
   */
  ClusterConfigurator withPodSecurityContext(V1PodSecurityContext podSecurityContext);

  ClusterConfigurator withAdditionalVolume(String name, String path);

  ClusterConfigurator withAdditionalVolumeMount(String name, String path);

  ClusterConfigurator withPodLabel(String name, String value);

  ClusterConfigurator withPodAnnotation(String name, String value);

  ClusterConfigurator withServiceLabel(String name, String value);

  ClusterConfigurator withServiceAnnotation(String name, String value);

  /**
   * Tells the operator whether the customer wants to restart the server pods. The value can be any
   * String and it can be defined on domain, cluster or server to restart the different pods. After
   * the value is added, the corresponding pods will be terminated and created again. If customer
   * modifies the value again after the pods were recreated, then the pods will again be terminated
   * and recreated.
   *
   * @since 2.0
   * @param restartVersion If present, every time this value is updated the operator will restart
   *     the required servers
   * @return this object
   */
  ClusterConfigurator withRestartVersion(String restartVersion);
}

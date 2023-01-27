// Copyright (c) 2018, 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.weblogic.domain;

import io.kubernetes.client.openapi.models.V1Affinity;
import io.kubernetes.client.openapi.models.V1Container;
import io.kubernetes.client.openapi.models.V1EnvVar;
import io.kubernetes.client.openapi.models.V1PodSecurityContext;
import io.kubernetes.client.openapi.models.V1SecurityContext;

/** An interface for an object to configure a server pod in a test. */
@SuppressWarnings("UnusedReturnValue")
public interface ServerPodConfigurator {
  ServerPodConfigurator withEnvironmentVariable(String name, String value);

  ServerPodConfigurator withEnvironmentVariable(V1EnvVar envVar);

  ServerPodConfigurator withLivenessProbeSettings(Integer initialDelay, Integer timeout, Integer period);

  ServerPodConfigurator withLivenessProbeThresholds(Integer successThreshold, Integer failureThreshold);

  ServerPodConfigurator withReadinessProbeSettings(Integer initialDelay, Integer timeout, Integer period);

  ServerPodConfigurator withReadinessProbeThresholds(Integer successThreshold, Integer failureThreshold);

  /**
   * Add a node label to the Servers's node selector.
   *
   * @param labelKey the pod label key
   * @param labelValue the pod label value
   * @return this object
   */
  ServerPodConfigurator withNodeSelector(String labelKey, String labelValue);

  /**
   * Add a resource requirement at server level. The requests for memory are measured in bytes. You
   * can express memory as a plain integer or as a fixed-point integer using one of these suffixes:
   * E, P, T, G, M, K. You can also use the power-of-two equivalents: Ei, Pi, Ti, Gi, Mi, Ki. The
   * requests for cpu are measured in cpu units and can be expressed in millicores i.e. 100m is the
   * same as 0.1
   *
   * @param resource the resource to be added as requirement cpu or memory
   * @param quantity the quantity required for the resource
   * @return this object
   */
  ServerPodConfigurator withRequestRequirement(String resource, String quantity);

  /**
   * Add a resource limit at server level, the requests for memory are measured in bytes. You can
   * express memory as a plain integer or as a fixed-point integer using one of these suffixes: E,
   * P, T, G, M, K. You can also use the power-of-two equivalents: Ei, Pi, Ti, Gi, Mi, Ki. The
   * requests for cpu are measured in cpu units and can be expressed in millicores i.e. 100m is the
   * same as 0.1
   *
   * @param resource the resource to be added as requirement cpu or memory
   * @param quantity the quantity required for the resource
   * @return this object
   */
  ServerPodConfigurator withLimitRequirement(String resource, String quantity);

  /**
   * Add security constraints at container level, if the same constraint is also defined at pod
   * level then container constraint take precedence.
   *
   * @param containerSecurityContext the security context object
   * @return this object
   */
  ServerPodConfigurator withContainerSecurityContext(V1SecurityContext containerSecurityContext);

  /**
   * Add security constraints at container level, if the same constraint is also defined at pod
   * level then container constraint take precedence.
   *
   * @param podSecurityContext pod-level security attributes to be added to this ServerConfigurator
   * @return this object
   */
  ServerPodConfigurator withPodSecurityContext(V1PodSecurityContext podSecurityContext);

  ServerPodConfigurator withAdditionalVolume(String name, String path);

  ServerPodConfigurator withAdditionalVolumeMount(String name, String path);

  ServerPodConfigurator withInitContainer(V1Container initContainer);

  ServerPodConfigurator withContainer(V1Container container);

  ServerPodConfigurator withPodLabel(String name, String value);

  ServerPodConfigurator withPodAnnotation(String name, String value);

  ServerPodConfigurator withAffinity(V1Affinity affinity);

  ServerPodConfigurator withNodeName(String nodeName);

  ServerPodConfigurator withMaximumReadyWaitTimeSeconds(long waitTime);

  ServerPodConfigurator withMaximumPendingWaitTimeSeconds(long waitTime);

  ServerPodConfigurator withSchedulerName(String schedulerName);

  ServerPodConfigurator withRuntimeClassName(String runtimeClassName);

  ServerPodConfigurator withPriorityClassName(String priorityClassName);

}

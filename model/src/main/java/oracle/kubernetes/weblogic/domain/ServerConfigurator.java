// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.weblogic.domain;

import io.kubernetes.client.models.V1PodSecurityContext;
import io.kubernetes.client.models.V1SecurityContext;

/** An interface for an object to configure a server in a test. */
@SuppressWarnings("UnusedReturnValue")
public interface ServerConfigurator {
  ServerConfigurator withDesiredState(String desiredState);

  ServerConfigurator withEnvironmentVariable(String name, String value);

  ServerConfigurator withServerStartState(String state);

  ServerConfigurator withServerStartPolicy(String startNever);

  ServerConfigurator withLivenessProbeSettings(
      Integer initialDelay, Integer timeout, Integer period);

  ServerConfigurator withReadinessProbeSettings(
      Integer initialDelay, Integer timeout, Integer period);

  /**
   * Add a node label to the Servers's node selector.
   *
   * @param labelKey the pod label key
   * @param labelValue the pod label value
   * @return this object
   */
  ServerConfigurator withNodeSelector(String labelKey, String labelValue);

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
  ServerConfigurator withRequestRequirement(String resource, String quantity);

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
  ServerConfigurator withLimitRequirement(String resource, String quantity);

  /**
   * Add security constraints at container level, if the same constraint is also defined at pod
   * level then container constraint take precedence.
   *
   * @param containerSecurityContext the security context object
   * @return this object
   */
  ServerConfigurator withContainerSecurityContext(V1SecurityContext containerSecurityContext);

  /**
   * Add security constraints at container level, if the same constraint is also defined at pod
   * level then container constraint take precedence.
   *
   * @param podSecurityContext pod-level security attributes to be added to this ServerConfigurator
   * @return this object
   */
  ServerConfigurator withPodSecurityContext(V1PodSecurityContext podSecurityContext);

  ServerConfigurator withAdditionalVolume(String name, String path);

  ServerConfigurator withAdditionalVolumeMount(String name, String path);

  ServerConfigurator withPodLabel(String name, String value);

  ServerConfigurator withPodAnnotation(String name, String value);

  ServerConfigurator withServiceLabel(String name, String value);

  ServerConfigurator withServiceAnnotation(String name, String value);

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
  ServerConfigurator withRestartVersion(String restartVersion);
}

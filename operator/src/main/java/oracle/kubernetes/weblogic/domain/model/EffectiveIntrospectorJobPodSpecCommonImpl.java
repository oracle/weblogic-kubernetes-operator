// Copyright (c) 2023, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.weblogic.domain.model;

import java.util.List;
import java.util.Map;

import io.kubernetes.client.openapi.models.V1Container;
import io.kubernetes.client.openapi.models.V1EnvFromSource;
import io.kubernetes.client.openapi.models.V1EnvVar;
import io.kubernetes.client.openapi.models.V1PodSecurityContext;
import io.kubernetes.client.openapi.models.V1ResourceRequirements;
import oracle.kubernetes.operator.processing.EffectiveIntrospectorJobPodSpec;

/** The effective configuration for the introspector pod. */
public class EffectiveIntrospectorJobPodSpecCommonImpl implements EffectiveIntrospectorJobPodSpec {
  private final Introspector introspector;

  /**
   * Constructs an object to return the effective configuration.
   *
   * @param spec Domain spec
   * @param introspector the server whose configuration is to be returned
   */
  EffectiveIntrospectorJobPodSpecCommonImpl(DomainSpec spec, Introspector introspector) {
    this.introspector = getIntrospectorBaseConfiguration(introspector);
  }

  private Introspector getIntrospectorBaseConfiguration(Introspector introspector) {
    return introspector != null ? introspector.getConfiguration() : new Introspector();
  }

  @Override
  public List<V1EnvVar> getEnv() {
    return introspector.getEnv();
  }

  @Override
  public List<V1EnvFromSource> getEnvFrom() {
    return introspector.getEnvFrom();
  }

  @Override
  public V1ResourceRequirements getResources() {
    return introspector.getResources();
  }

  @Override
  public V1PodSecurityContext getPodSecurityContext() {
    return introspector.getPodSecurityContext();
  }

  @Override
  public List<V1Container> getInitContainers() {
    return introspector.getInitContainers();
  }

  @Override
  public Map<String, String> getLabels() {
    return introspector.getLabels();
  }

  @Override
  public Map<String, String> getAnnotations() {
    return introspector.getAnnotations();
  }

}

// Copyright (c) 2023, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.weblogic.domain.model;

import java.util.List;

import io.kubernetes.client.openapi.models.V1EnvVar;
import io.kubernetes.client.openapi.models.V1ResourceRequirements;
import oracle.kubernetes.operator.processing.EffectiveIntrospectorJobPodSpec;

/** The effective configuration for the introspector pod. */
public class EffectiveIntrospectorJobSpecCommonImpl implements EffectiveIntrospectorJobPodSpec {
  private final IntrospectorJob introspector;

  /**
   * Constructs an object to return the effective configuration.
   *
   * @param spec Domain spec
   * @param introspector the server whose configuration is to be returned
   */
  EffectiveIntrospectorJobSpecCommonImpl(DomainSpec spec, IntrospectorJob introspector) {
    this.introspector = getIntrospectorBaseConfiguration(introspector);
  }

  private IntrospectorJob getIntrospectorBaseConfiguration(IntrospectorJob introspector) {
    return introspector != null ? introspector.getConfiguration() : new IntrospectorJob();
  }

  @Override
  public List<V1EnvVar> getEnv() {
    return introspector.getEnv();
  }

  @Override
  public V1ResourceRequirements getResources() {
    return introspector.getResources();
  }
}

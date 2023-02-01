// Copyright (c) 2023, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.weblogic.domain.model;

import java.util.List;

import io.kubernetes.client.openapi.models.V1EnvVar;
import io.kubernetes.client.openapi.models.V1ResourceRequirements;
import oracle.kubernetes.operator.processing.EffectiveBaseServerPodSpec;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;

/** The effective configuration for the introspector pod. */
public class EffectiveIntrospectorSpecCommonImpl implements EffectiveBaseServerPodSpec {
  private final Introspector introspector;

  /**
   * Constructs an object to return the effective configuration.
   *
   * @param spec Domain spec
   * @param introspector the server whose configuration is to be returned
   */
  EffectiveIntrospectorSpecCommonImpl(DomainSpec spec, Introspector introspector) {
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
  public V1ResourceRequirements getResources() {
    return introspector.getResources();
  }

  @Override
  public String toString() {
    return new ToStringBuilder(this)
        .appendSuper(super.toString())
        .append("server", introspector)
        .toString();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }

    if (!(o instanceof EffectiveIntrospectorSpecCommonImpl)) {
      return false;
    }

    EffectiveIntrospectorSpecCommonImpl that = (EffectiveIntrospectorSpecCommonImpl) o;

    return new EqualsBuilder()
        .appendSuper(super.equals(o))
        .append(introspector, that.introspector)
        .isEquals();
  }

  @Override
  public int hashCode() {
    return new HashCodeBuilder(17, 37)
        .appendSuper(super.hashCode())
        .append(introspector)
        .toHashCode();
  }
}

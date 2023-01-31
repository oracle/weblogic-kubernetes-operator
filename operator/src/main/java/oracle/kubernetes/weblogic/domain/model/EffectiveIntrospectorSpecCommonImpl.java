// Copyright (c) 2018, 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.weblogic.domain.model;

import java.util.List;

import io.kubernetes.client.openapi.models.V1EnvVar;
import io.kubernetes.client.openapi.models.V1ResourceRequirements;
import oracle.kubernetes.operator.processing.EffectiveBasicServerPodSpecBase;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;

/** The effective configuration for a server configured by the version 2 domain model. */
public class EffectiveIntrospectorSpecCommonImpl extends EffectiveBasicServerPodSpecBase {
  private final Introspector introspector;

  /**
   * Constructs an object to return the effective configuration.
   *
   * @param spec Domain spec
   * @param introspector the server whose configuration is to be returned
   */
  EffectiveIntrospectorSpecCommonImpl(DomainSpec spec, Introspector introspector) {
    super(spec);
    this.introspector = getIntrospectorBaseConfiguration(introspector);
    //this.introspector.fillInFrom(spec.getServerPod().baseIntrospectorServerPodConfiguration);
  }

  private Introspector getIntrospectorBaseConfiguration(Introspector introspector) {
    return introspector != null ? introspector.getConfiguration() : new Introspector();
  }

  @Override
  public List<V1EnvVar> getEnvironmentVariables() {
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

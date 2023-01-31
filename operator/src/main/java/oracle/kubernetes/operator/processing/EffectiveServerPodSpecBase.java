// Copyright (c) 2023, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.processing;

import oracle.kubernetes.weblogic.domain.model.DomainSpec;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;

/** Represents the effective configuration for a server, as seen by the operator runtime. */
@SuppressWarnings("WeakerAccess")
public abstract class EffectiveServerPodSpecBase implements EffectiveServerPodSpec {

  protected final DomainSpec domainSpec;

  protected EffectiveServerPodSpecBase(DomainSpec domainSpec) {
    this.domainSpec = domainSpec;
  }

  @Override
  public String toString() {
    return new ToStringBuilder(this).append("domainSpec", domainSpec).toString();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }

    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    if (!(o instanceof EffectiveServerPodSpecBase)) {
      return false;
    }

    EffectiveServerPodSpecBase that = (EffectiveServerPodSpecBase) o;

    return new EqualsBuilder().append(domainSpec, that.domainSpec).isEquals();
  }

  @Override
  public int hashCode() {
    return new HashCodeBuilder(17, 37).append(domainSpec).toHashCode();
  }
}

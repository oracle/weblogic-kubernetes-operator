// Copyright (c) 2021, 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.weblogic.domain.model;

import java.util.Optional;

import oracle.kubernetes.json.Default;
import oracle.kubernetes.json.Description;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;

public class ClusterService extends KubernetesResource {

  @Description(
      "Advanced setting to enable client IP based session affinity. "
          + "Must be ClientIP or None. Defaults to None. More info: "
          + "https://oracle.github.io/weblogic-kubernetes-operator/managing-domains/domain-resource/#cluster-spec-elements")
  @Default(strDefault = "None")
  private String sessionAffinity;

  void fillInFrom(ClusterService clusterService1) {
    super.fillInFrom(clusterService1);
    this.sessionAffinity =
        Optional.ofNullable(sessionAffinity).orElse(clusterService1.sessionAffinity);
  }

  public String getSessionAffinity() {
    return sessionAffinity;
  }

  public void setSessionAffinity(String sessionAffinity) {
    this.sessionAffinity = sessionAffinity;
  }

  public ClusterService withSessionAffinity(String sessionAffinity) {
    this.sessionAffinity = sessionAffinity;
    return this;
  }

  @Override
  public String toString() {
    return new ToStringBuilder(this)
        .appendSuper(super.toString())
        .append("sessionAffinity", sessionAffinity)
        .toString();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }

    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    ClusterService that = (ClusterService) o;

    return new EqualsBuilder()
        .appendSuper(super.equals(o))
        .append(sessionAffinity, that.sessionAffinity)
        .isEquals();
  }

  @Override
  public int hashCode() {
    return new HashCodeBuilder(17, 37)
        .appendSuper(super.hashCode())
        .append(sessionAffinity)
        .toHashCode();
  }
}

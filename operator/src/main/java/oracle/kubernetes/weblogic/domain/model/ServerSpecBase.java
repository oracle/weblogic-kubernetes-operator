// Copyright (c) 2018, 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.weblogic.domain.model;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import javax.annotation.Nonnull;

import io.kubernetes.client.openapi.models.V1Affinity;
import io.kubernetes.client.openapi.models.V1LocalObjectReference;
import io.kubernetes.client.openapi.models.V1PodReadinessGate;
import io.kubernetes.client.openapi.models.V1PodSecurityContext;
import io.kubernetes.client.openapi.models.V1ResourceRequirements;
import io.kubernetes.client.openapi.models.V1SecurityContext;
import io.kubernetes.client.openapi.models.V1Toleration;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;

/** Represents the effective configuration for a server, as seen by the operator runtime. */
@SuppressWarnings("WeakerAccess")
public abstract class ServerSpecBase implements ServerSpec {

  protected final DomainSpec domainSpec;

  public ServerSpecBase(DomainSpec domainSpec) {
    this.domainSpec = domainSpec;
  }

  @Override
  public String getImage() {
    return domainSpec.getImage();
  }

  @Override
  public String getImagePullPolicy() {
    return domainSpec.getImagePullPolicy();
  }

  @Override
  public List<V1LocalObjectReference> getImagePullSecrets() {
    return domainSpec.getImagePullSecrets();
  }

  @Override
  @Nonnull
  public ProbeTuning getLivenessProbe() {
    return new ProbeTuning();
  }

  @Override
  @Nonnull
  public ProbeTuning getReadinessProbe() {
    return new ProbeTuning();
  }

  @Override
  @Nonnull
  public Shutdown getShutdown() {
    return new Shutdown();
  }

  @Override
  @Nonnull
  public Map<String, String> getNodeSelectors() {
    return Collections.emptyMap();
  }

  @Override
  public V1Affinity getAffinity() {
    return null;
  }

  @Override
  public String getPriorityClassName() {
    return null;
  }

  @Override
  public List<V1PodReadinessGate> getReadinessGates() {
    return null;
  }

  @Override
  public String getRestartPolicy() {
    return null;
  }

  @Override
  public String getRuntimeClassName() {
    return null;
  }

  @Override
  public String getNodeName() {
    return null;
  }

  @Override
  public String getSchedulerName() {
    return null;
  }

  @Override
  public List<V1Toleration> getTolerations() {
    return null;
  }

  @Override
  public V1ResourceRequirements getResources() {
    return null;
  }

  @Override
  public V1PodSecurityContext getPodSecurityContext() {
    return null;
  }

  @Override
  public V1SecurityContext getContainerSecurityContext() {
    return null;
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

    if (!(o instanceof ServerSpecBase)) {
      return false;
    }

    ServerSpecBase that = (ServerSpecBase) o;

    return new EqualsBuilder().append(domainSpec, that.domainSpec).isEquals();
  }

  @Override
  public int hashCode() {
    return new HashCodeBuilder(17, 37).append(domainSpec).toHashCode();
  }
}

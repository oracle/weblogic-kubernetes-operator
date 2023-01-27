// Copyright (c) 2018, 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.weblogic.domain.model;

import java.util.List;
import java.util.Map;
import javax.annotation.Nonnull;

import io.kubernetes.client.openapi.models.V1Affinity;
import io.kubernetes.client.openapi.models.V1Container;
import io.kubernetes.client.openapi.models.V1EnvVar;
import io.kubernetes.client.openapi.models.V1HostAlias;
import io.kubernetes.client.openapi.models.V1PodReadinessGate;
import io.kubernetes.client.openapi.models.V1PodSecurityContext;
import io.kubernetes.client.openapi.models.V1ResourceRequirements;
import io.kubernetes.client.openapi.models.V1SecurityContext;
import io.kubernetes.client.openapi.models.V1Toleration;
import io.kubernetes.client.openapi.models.V1Volume;
import io.kubernetes.client.openapi.models.V1VolumeMount;
import oracle.kubernetes.operator.processing.EffectiveServerPodSpecBase;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;

/** The effective configuration for a server configured by the version 2 domain model. */
public class EffectiveIntrospectorSpecCommonImpl extends EffectiveServerPodSpecBase {
  private final Introspector introspector;

  /**
   * Constructs an object to return the effective configuration.
   *
   * @param spec Domain spec
   * @param introspector the server whose configuration is to be returned
   */
  EffectiveIntrospectorSpecCommonImpl(DomainSpec spec, Introspector introspector) {
    super(spec);
    this.introspector = getBaseConfiguration(introspector);
    this.introspector.fillInFrom(spec.baseServerPodConfiguration);
  }

  private Introspector getBaseConfiguration(Introspector introspector) {
    return introspector != null ? introspector.getConfiguration() : new Introspector();
  }

  @Override
  public List<V1EnvVar> getEnvironmentVariables() {
    return introspector.getEnv();
  }

  @Override
  public List<V1Volume> getAdditionalVolumes() {
    return introspector.getAdditionalVolumes();
  }

  @Override
  public List<V1VolumeMount> getAdditionalVolumeMounts() {
    return introspector.getAdditionalVolumeMounts();
  }

  @Override
  @Nonnull
  public Map<String, String> getPodLabels() {
    return introspector.getPodLabels();
  }

  @Override
  @Nonnull
  public Map<String, String> getPodAnnotations() {
    return introspector.getPodAnnotations();
  }

  @Override
  @Nonnull
  public List<V1Container> getContainers() {
    return introspector.getContainers();
  }

  @Override
  @Nonnull
  public List<V1Container> getInitContainers() {
    return introspector.getInitContainers();
  }

  @Nonnull
  @Override
  public ProbeTuning getLivenessProbe() {
    return introspector.getLivenessProbe();
  }

  @Nonnull
  @Override
  public ProbeTuning getReadinessProbe() {
    return introspector.getReadinessProbe();
  }

  @Nonnull
  @Override
  public Map<String, String> getNodeSelectors() {
    return introspector.getNodeSelector();
  }

  @Override
  public V1Affinity getAffinity() {
    return introspector.getAffinity();
  }

  @Override
  public String getPriorityClassName() {
    return introspector.getPriorityClassName();
  }

  @Override
  public List<V1PodReadinessGate> getReadinessGates() {
    return introspector.getReadinessGates();
  }

  @Override
  public String getRestartPolicy() {
    return introspector.getRestartPolicy();
  }

  @Override
  public String getRuntimeClassName() {
    return introspector.getRuntimeClassName();
  }

  @Override
  public String getNodeName() {
    return introspector.getNodeName();
  }

  @Override
  public String getServiceAccountName() {
    return introspector.getServiceAccountName();
  }

  @Override
  public String getSchedulerName() {
    return introspector.getSchedulerName();
  }

  @Override
  public List<V1Toleration> getTolerations() {
    return introspector.getTolerations();
  }

  @Override
  public List<V1HostAlias> getHostAliases() {
    return introspector.getHostAliases();
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
  public V1SecurityContext getContainerSecurityContext() {
    return introspector.getContainerSecurityContext();
  }

  @Override
  public Long getMaximumReadyWaitTimeSeconds() {
    return introspector.getMaximumReadyWaitTimeSeconds();
  }

  @Override
  public Long getMaximumPendingWaitTimeSeconds() {
    return introspector.getMaximumPendingWaitTimeSeconds();
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

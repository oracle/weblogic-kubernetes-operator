// Copyright 2018, 2019, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.weblogic.domain.model;

import static oracle.kubernetes.operator.KubernetesConstants.ALWAYS_IMAGEPULLPOLICY;
import static oracle.kubernetes.operator.KubernetesConstants.DEFAULT_IMAGE;
import static oracle.kubernetes.operator.KubernetesConstants.IFNOTPRESENT_IMAGEPULLPOLICY;

import io.kubernetes.client.models.V1EnvVar;
import io.kubernetes.client.models.V1LocalObjectReference;
import io.kubernetes.client.models.V1PodSecurityContext;
import io.kubernetes.client.models.V1ResourceRequirements;
import io.kubernetes.client.models.V1SecurityContext;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.annotation.Nonnull;
import oracle.kubernetes.operator.KubernetesConstants;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;

/** Represents the effective configuration for a server, as seen by the operator runtime. */
@SuppressWarnings("WeakerAccess")
public abstract class ServerSpecBase implements ServerSpec {

  private static final String ADMIN_MODE_FLAG = "-Dweblogic.management.startupMode=ADMIN";
  protected DomainSpec domainSpec;

  public ServerSpecBase(DomainSpec domainSpec) {
    this.domainSpec = domainSpec;
  }

  @Override
  public String getImage() {
    return Optional.ofNullable(domainSpec.getImage()).orElse(DEFAULT_IMAGE);
  }

  @Override
  public String getImagePullPolicy() {
    return Optional.ofNullable(getConfiguredImagePullPolicy()).orElse(getInferredPullPolicy());
  }

  protected String getConfiguredImagePullPolicy() {
    return domainSpec.getImagePullPolicy();
  }

  private String getInferredPullPolicy() {
    return useLatestImage() ? ALWAYS_IMAGEPULLPOLICY : IFNOTPRESENT_IMAGEPULLPOLICY;
  }

  private boolean useLatestImage() {
    return getImage().endsWith(KubernetesConstants.LATEST_IMAGE_SUFFIX);
  }

  @Override
  public List<V1LocalObjectReference> getImagePullSecrets() {
    return domainSpec.getImagePullSecrets();
  }

  protected List<V1EnvVar> withStateAdjustments(List<V1EnvVar> env) {
    if (!getDesiredState().equals("ADMIN")) {
      return env;
    } else {
      List<V1EnvVar> adjustedEnv = new ArrayList<>(env);
      V1EnvVar var = getOrCreateVar(adjustedEnv, "JAVA_OPTIONS");
      var.setValue(prepend(var.getValue(), ADMIN_MODE_FLAG));
      return adjustedEnv;
    }
  }

  @Override
  public String getConfigOverrides() {
    return domainSpec.getConfigOverrides();
  }

  @Override
  public List<String> getConfigOverrideSecrets() {
    return domainSpec.getConfigOverrideSecrets();
  }

  @SuppressWarnings("SameParameterValue")
  private V1EnvVar getOrCreateVar(List<V1EnvVar> env, String name) {
    for (V1EnvVar var : env) {
      if (var.getName().equals(name)) {
        return var;
      }
    }
    V1EnvVar var = new V1EnvVar().name(name);
    env.add(var);
    return var;
  }

  @SuppressWarnings("SameParameterValue")
  private String prepend(String value, String prefix) {
    return value == null ? prefix : value.contains(prefix) ? value : prefix + ' ' + value;
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
  public Map<String, String> getNodeSelectors() {
    return Collections.emptyMap();
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

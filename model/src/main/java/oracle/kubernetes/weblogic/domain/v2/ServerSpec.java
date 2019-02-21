// Copyright 2018, 2019, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.weblogic.domain.v2;

import static oracle.kubernetes.operator.KubernetesConstants.ALWAYS_IMAGEPULLPOLICY;
import static oracle.kubernetes.operator.KubernetesConstants.DEFAULT_IMAGE;
import static oracle.kubernetes.operator.KubernetesConstants.IFNOTPRESENT_IMAGEPULLPOLICY;

import io.kubernetes.client.models.V1EnvVar;
import io.kubernetes.client.models.V1LocalObjectReference;
import io.kubernetes.client.models.V1PodSecurityContext;
import io.kubernetes.client.models.V1ResourceRequirements;
import io.kubernetes.client.models.V1SecurityContext;
import io.kubernetes.client.models.V1Volume;
import io.kubernetes.client.models.V1VolumeMount;
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
public abstract class ServerSpec {

  private static final String ADMIN_MODE_FLAG = "-Dweblogic.management.startupMode=ADMIN";
  protected DomainSpec domainSpec;

  public ServerSpec(DomainSpec domainSpec) {
    this.domainSpec = domainSpec;
  }

  public String getImage() {
    return Optional.ofNullable(domainSpec.getImage()).orElse(DEFAULT_IMAGE);
  }

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

  /**
   * The secrets used to authenticate to a docker repository when pulling an image.
   *
   * @return a list of objects containing the name of secrets. May be empty.
   */
  public List<V1LocalObjectReference> getImagePullSecrets() {
    return domainSpec.getImagePullSecrets();
  }

  /**
   * Returns the environment variables to be defined for this server.
   *
   * @return a list of environment variables
   */
  public abstract List<V1EnvVar> getEnvironmentVariables();

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

  /**
   * The Kubernetes config map name used in WebLogic configuration overrides.
   *
   * @return configMapName. May be empty.
   */
  public String getConfigOverrides() {
    return domainSpec.getConfigOverrides();
  }

  /**
   * The secret names used in WebLogic configuration overrides.
   *
   * @return a list of secret names. May be empty.
   */
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

  /**
   * Desired startup state. Legal values are RUNNING or ADMIN.
   *
   * @return desired state
   */
  public abstract String getDesiredState();

  /**
   * Returns true if the specified server should be started, based on the current domain spec.
   *
   * @param currentReplicas the number of replicas already selected for the cluster.
   * @return whether to start the server
   */
  public abstract boolean shouldStart(int currentReplicas);

  /**
   * Returns the volume mounts to be defined for this server.
   *
   * @return a list of environment volume mounts
   */
  public abstract List<V1VolumeMount> getAdditionalVolumeMounts();

  /**
   * Returns the volumes to be defined for this server.
   *
   * @return a list of volumes
   */
  public abstract List<V1Volume> getAdditionalVolumes();

  @Nonnull
  public ProbeTuning getLivenessProbe() {
    return new ProbeTuning();
  }

  @Nonnull
  public ProbeTuning getReadinessProbe() {
    return new ProbeTuning();
  }

  /**
   * Returns the labels applied to the pod.
   *
   * @return a map of labels
   */
  @Nonnull
  public abstract Map<String, String> getPodLabels();

  /**
   * Returns the annotations applied to the pod.
   *
   * @return a map of annotations
   */
  @Nonnull
  public abstract Map<String, String> getPodAnnotations();

  /**
   * Returns the labels applied to the service.
   *
   * @return a map of labels
   */
  @Nonnull
  public abstract Map<String, String> getServiceLabels();

  /**
   * Returns the annotations applied to the service.
   *
   * @return a map of annotations
   */
  @Nonnull
  public abstract Map<String, String> getServiceAnnotations();

  @Nonnull
  public Map<String, String> getListenAddressServiceLabels() {
    return Collections.emptyMap();
  }

  @Nonnull
  public Map<String, String> getListenAddressServiceAnnotations() {
    return Collections.emptyMap();
  }

  @Nonnull
  public Map<String, String> getNodeSelectors() {
    return Collections.emptyMap();
  }

  public V1ResourceRequirements getResources() {
    return null;
  }

  public V1PodSecurityContext getPodSecurityContext() {
    return null;
  }

  public V1SecurityContext getContainerSecurityContext() {
    return null;
  }

  public abstract String getDomainRestartVersion();

  public abstract String getClusterRestartVersion();

  public abstract String getServerRestartVersion();

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

    if (!(o instanceof ServerSpec)) {
      return false;
    }

    ServerSpec that = (ServerSpec) o;

    return new EqualsBuilder().append(domainSpec, that.domainSpec).isEquals();
  }

  @Override
  public int hashCode() {
    return new HashCodeBuilder(17, 37).append(domainSpec).toHashCode();
  }
}

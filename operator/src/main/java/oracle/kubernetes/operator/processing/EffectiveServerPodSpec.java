// Copyright (c) 2023, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.processing;

import java.util.List;
import java.util.Map;
import javax.annotation.Nonnull;

import io.kubernetes.client.openapi.models.V1PodSecurityContext;
import io.kubernetes.client.openapi.models.V1SecurityContext;
import io.kubernetes.client.openapi.models.V1Volume;
import io.kubernetes.client.openapi.models.V1VolumeMount;
import oracle.kubernetes.weblogic.domain.model.ProbeTuning;

public interface EffectiveServerPodSpec extends EffectiveIntrospectorPodSpec {
  /**
   * Returns the volume mounts to be defined for this server.
   *
   * @return a list of environment volume mounts
   */
  List<V1VolumeMount> getAdditionalVolumeMounts();

  /**
   * Returns the volumes to be defined for this server.
   *
   * @return a list of volumes
   */
  List<V1Volume> getAdditionalVolumes();

  @Nonnull
  ProbeTuning getLivenessProbe();

  @Nonnull
  ProbeTuning getReadinessProbe();

  /**
   * Returns the labels applied to the pod.
   *
   * @return a map of labels
   */
  @Nonnull
  Map<String, String> getPodLabels();

  /**
   * Returns the annotations applied to the pod.
   *
   * @return a map of annotations
   */
  @Nonnull
  Map<String, String> getPodAnnotations();

  V1PodSecurityContext getPodSecurityContext();

  V1SecurityContext getContainerSecurityContext();

  Long getMaximumReadyWaitTimeSeconds();

  Long getMaximumPendingWaitTimeSeconds();
}

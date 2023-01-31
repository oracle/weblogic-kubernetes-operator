// Copyright (c) 2018, 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.processing;

import java.util.List;
import java.util.Map;
import javax.annotation.Nonnull;

import io.kubernetes.client.openapi.models.V1Affinity;
import io.kubernetes.client.openapi.models.V1Container;
import io.kubernetes.client.openapi.models.V1HostAlias;
import io.kubernetes.client.openapi.models.V1PodReadinessGate;
import io.kubernetes.client.openapi.models.V1PodSecurityContext;
import io.kubernetes.client.openapi.models.V1SecurityContext;
import io.kubernetes.client.openapi.models.V1Toleration;
import io.kubernetes.client.openapi.models.V1Volume;
import io.kubernetes.client.openapi.models.V1VolumeMount;
import oracle.kubernetes.weblogic.domain.model.ProbeTuning;

public interface EffectiveServerPodSpec extends EffectiveBasicServerPodSpec {
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

  @Nonnull
  List<V1Container> getInitContainers();

  @Nonnull
  List<V1Container> getContainers();

  Map<String, String> getNodeSelectors();

  V1Affinity getAffinity();

  String getPriorityClassName();

  List<V1PodReadinessGate> getReadinessGates();

  String getRestartPolicy();

  String getRuntimeClassName();

  String getNodeName();

  String getServiceAccountName();

  String getSchedulerName();

  List<V1Toleration> getTolerations();

  List<V1HostAlias> getHostAliases();

  V1PodSecurityContext getPodSecurityContext();

  V1SecurityContext getContainerSecurityContext();

  Long getMaximumReadyWaitTimeSeconds();

  Long getMaximumPendingWaitTimeSeconds();
}

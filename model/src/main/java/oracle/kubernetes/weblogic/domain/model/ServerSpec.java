// Copyright 2018, 2019, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.weblogic.domain.model;

import io.kubernetes.client.models.V1Container;
import io.kubernetes.client.models.V1EnvVar;
import io.kubernetes.client.models.V1LocalObjectReference;
import io.kubernetes.client.models.V1PodSecurityContext;
import io.kubernetes.client.models.V1ResourceRequirements;
import io.kubernetes.client.models.V1SecurityContext;
import io.kubernetes.client.models.V1Volume;
import io.kubernetes.client.models.V1VolumeMount;
import java.util.List;
import java.util.Map;
import javax.annotation.Nonnull;

public interface ServerSpec {
  String getImage();

  String getImagePullPolicy();

  /**
   * The secrets used to authenticate to a docker repository when pulling an image.
   *
   * @return a list of objects containing the name of secrets. May be empty.
   */
  List<V1LocalObjectReference> getImagePullSecrets();

  /**
   * Returns the environment variables to be defined for this server.
   *
   * @return a list of environment variables
   */
  List<V1EnvVar> getEnvironmentVariables();

  /**
   * The Kubernetes config map name used in WebLogic configuration overrides.
   *
   * @return configMapName. May be empty.
   */
  String getConfigOverrides();

  /**
   * The secret names used in WebLogic configuration overrides.
   *
   * @return a list of secret names. May be empty.
   */
  List<String> getConfigOverrideSecrets();

  /**
   * Desired startup state. Legal values are RUNNING or ADMIN.
   *
   * @return desired state
   */
  String getDesiredState();

  /**
   * Returns true if the specified server should be started, based on the current domain spec.
   *
   * @param currentReplicas the number of replicas already selected for the cluster.
   * @return whether to start the server
   */
  boolean shouldStart(int currentReplicas);

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

  /**
   * Returns the labels applied to the service.
   *
   * @return a map of labels
   */
  @Nonnull
  Map<String, String> getServiceLabels();

  /**
   * Returns the annotations applied to the service.
   *
   * @return a map of annotations
   */
  @Nonnull
  Map<String, String> getServiceAnnotations();

  @Nonnull
  List<V1Container> getInitContainers();

  @Nonnull
  List<V1Container> getContainers();

  Map<String, String> getNodeSelectors();

  V1ResourceRequirements getResources();

  V1PodSecurityContext getPodSecurityContext();

  V1SecurityContext getContainerSecurityContext();

  String getDomainRestartVersion();

  String getClusterRestartVersion();

  String getServerRestartVersion();
}

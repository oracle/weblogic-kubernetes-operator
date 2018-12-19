// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helpers;

import static oracle.kubernetes.operator.KubernetesConstants.DOMAIN_CONFIG_MAP_NAME;
import static oracle.kubernetes.operator.KubernetesConstants.DOMAIN_DEBUG_CONFIG_MAP_SUFFIX;
import static oracle.kubernetes.operator.KubernetesConstants.INTROSPECTOR_CONFIG_MAP_NAME_SUFFIX;
import static oracle.kubernetes.operator.helpers.StepContextConstants.ALL_READ_AND_EXECUTE;
import static oracle.kubernetes.operator.helpers.StepContextConstants.DEBUG_CM_MOUNTS_PATH;
import static oracle.kubernetes.operator.helpers.StepContextConstants.DEBUG_CM_VOLUME;
import static oracle.kubernetes.operator.helpers.StepContextConstants.SCRIPTS_MOUNTS_PATH;
import static oracle.kubernetes.operator.helpers.StepContextConstants.SCRIPTS_VOLUME;
import static oracle.kubernetes.operator.helpers.StepContextConstants.SIT_CONFIG_MAP_VOLUME_SUFFIX;

import io.kubernetes.client.models.V1ConfigMapVolumeSource;
import io.kubernetes.client.models.V1Volume;
import io.kubernetes.client.models.V1VolumeMount;
import java.util.ArrayList;
import java.util.List;

class PodDefaults {
  static final String K8S_SERVICE_ACCOUNT_MOUNT_PATH =
      "/var/run/secrets/kubernetes.io/serviceaccount";

  static List<V1Volume> getStandardVolumes(String domainUID) {
    List<V1Volume> volumes = new ArrayList<>();
    volumes.add(createScriptsVolume());
    volumes.add(createDebugCMVolume(domainUID));
    volumes.add(createSitConfigVolume(domainUID));
    return volumes;
  }

  private static V1Volume createScriptsVolume() {
    return createVolume(SCRIPTS_VOLUME, DOMAIN_CONFIG_MAP_NAME);
  }

  private static V1Volume createVolume(String volumeName, String configMapName) {
    return new V1Volume()
        .name(volumeName)
        .configMap(
            new V1ConfigMapVolumeSource().name(configMapName).defaultMode(ALL_READ_AND_EXECUTE));
  }

  private static V1Volume createDebugCMVolume(String domainUID) {
    V1Volume volume = createVolume(DEBUG_CM_VOLUME, domainUID + DOMAIN_DEBUG_CONFIG_MAP_SUFFIX);
    volume.getConfigMap().setOptional(true);
    return volume;
  }

  private static V1Volume createSitConfigVolume(String domainUID) {
    return createVolume(getSitConfigMapVolumeName(domainUID), getConfigMapName(domainUID));
  }

  private static String getSitConfigMapVolumeName(String domainUID) {
    return domainUID + SIT_CONFIG_MAP_VOLUME_SUFFIX;
  }

  private static String getConfigMapName(String domainUID) {
    return domainUID + INTROSPECTOR_CONFIG_MAP_NAME_SUFFIX;
  }

  static List<V1VolumeMount> getStandardVolumeMounts(String domainUID) {
    List<V1VolumeMount> mounts = new ArrayList<>();
    mounts.add(createScriptsVolumeMount());
    mounts.add(createDebugCMVolumeMount());
    mounts.add(createSitConfigVolumeMount(domainUID));
    return mounts;
  }

  private static V1VolumeMount createScriptsVolumeMount() {
    return readOnlyVolumeMount(SCRIPTS_VOLUME, SCRIPTS_MOUNTS_PATH);
  }

  private static V1VolumeMount createDebugCMVolumeMount() {
    return readOnlyVolumeMount(DEBUG_CM_VOLUME, DEBUG_CM_MOUNTS_PATH);
  }

  private static V1VolumeMount createSitConfigVolumeMount(String domainUID) {
    return volumeMount(getSitConfigMapVolumeName(domainUID), "/weblogic-operator/introspector");
  }

  private static V1VolumeMount readOnlyVolumeMount(String volumeName, String mountPath) {
    return volumeMount(volumeName, mountPath).readOnly(true);
  }

  private static V1VolumeMount volumeMount(String volumeName, String mountPath) {
    return new V1VolumeMount().name(volumeName).mountPath(mountPath);
  }
}

// Copyright (c) 2018, 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helpers;

import java.util.ArrayList;
import java.util.List;

import io.kubernetes.client.openapi.models.V1ConfigMapVolumeSource;
import io.kubernetes.client.openapi.models.V1Volume;
import io.kubernetes.client.openapi.models.V1VolumeMount;

import static oracle.kubernetes.operator.KubernetesConstants.DOMAIN_DEBUG_CONFIG_MAP_SUFFIX;
import static oracle.kubernetes.operator.KubernetesConstants.SCRIPT_CONFIG_MAP_NAME;
import static oracle.kubernetes.operator.helpers.StepContextConstants.ALL_READ_AND_EXECUTE;
import static oracle.kubernetes.operator.helpers.StepContextConstants.DEBUG_CM_MOUNTS_PATH;
import static oracle.kubernetes.operator.helpers.StepContextConstants.DEBUG_CM_VOLUME;
import static oracle.kubernetes.operator.helpers.StepContextConstants.SCRIPTS_MOUNTS_PATH;
import static oracle.kubernetes.operator.helpers.StepContextConstants.SCRIPTS_VOLUME;
import static oracle.kubernetes.operator.helpers.StepContextConstants.SIT_CONFIG_MAP_VOLUME;
import static oracle.kubernetes.operator.helpers.StepContextConstants.WDT_CONFIG_MAP_VOLUME;

class PodDefaults {
  static final String K8S_SERVICE_ACCOUNT_MOUNT_PATH =
      "/var/run/secrets/kubernetes.io/serviceaccount";

  static List<V1Volume> getStandardVolumes(String domainUid) {
    List<V1Volume> volumes = new ArrayList<>();
    volumes.add(createScriptsVolume());
    volumes.add(createDebugCmVolume(domainUid));
    volumes.add(createSitConfigVolume(domainUid));
    return volumes;
  }

  private static V1Volume createScriptsVolume() {
    return createVolume(SCRIPTS_VOLUME, SCRIPT_CONFIG_MAP_NAME);
  }

  private static V1Volume createVolume(String volumeName, String configMapName) {
    return new V1Volume()
        .name(volumeName)
        .configMap(
            new V1ConfigMapVolumeSource().name(configMapName).defaultMode(ALL_READ_AND_EXECUTE));
  }

  private static V1Volume createDebugCmVolume(String domainUid) {
    V1Volume volume = createVolume(DEBUG_CM_VOLUME, domainUid + DOMAIN_DEBUG_CONFIG_MAP_SUFFIX);
    volume.getConfigMap().setOptional(true);
    return volume;
  }

  private static V1Volume createSitConfigVolume(String domainUid) {
    return createVolume(getSitConfigMapVolumeName(domainUid), ConfigMapHelper.getIntrospectorConfigMapName(domainUid));
  }

  private static String getSitConfigMapVolumeName(String domainUid) {
    return SIT_CONFIG_MAP_VOLUME;
  }

  private static V1Volume createWdtConfigMapVolume(String domainUid) {
    return createVolume(getWdtConfigMapVolumeName(domainUid), ConfigMapHelper.getIntrospectorConfigMapName(domainUid));
  }

  private static String getWdtConfigMapVolumeName(String domainUid) {
    return WDT_CONFIG_MAP_VOLUME;
  }

  static List<V1VolumeMount> getStandardVolumeMounts(String domainUid) {
    List<V1VolumeMount> mounts = new ArrayList<>();
    mounts.add(createScriptsVolumeMount());
    mounts.add(createDebugCmVolumeMount());
    mounts.add(createSitConfigVolumeMount(domainUid));
    return mounts;
  }

  private static V1VolumeMount createScriptsVolumeMount() {
    return readOnlyVolumeMount(SCRIPTS_VOLUME, SCRIPTS_MOUNTS_PATH);
  }

  private static V1VolumeMount createDebugCmVolumeMount() {
    return readOnlyVolumeMount(DEBUG_CM_VOLUME, DEBUG_CM_MOUNTS_PATH);
  }

  private static V1VolumeMount createSitConfigVolumeMount(String domainUid) {
    return volumeMount(getSitConfigMapVolumeName(domainUid), "/weblogic-operator/introspector");
  }

  private static V1VolumeMount createWdtConfigVolumeMount(String domainUid) {
    return volumeMount(getWdtConfigMapVolumeName(domainUid), "/weblogic-operator/introspector");
  }

  private static V1VolumeMount readOnlyVolumeMount(String volumeName, String mountPath) {
    return volumeMount(volumeName, mountPath).readOnly(true);
  }

  private static V1VolumeMount volumeMount(String volumeName, String mountPath) {
    return new V1VolumeMount().name(volumeName).mountPath(mountPath);
  }
}

// Copyright (c) 2023, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.weblogic.domain.model;

import java.util.List;
import java.util.Map;

import io.kubernetes.client.custom.Quantity;
import io.kubernetes.client.openapi.models.V1HostPathVolumeSource;
import oracle.kubernetes.json.Description;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;

public class PersistentVolumeSpec {

  @Description("AccessModes contains all ways the volume can be mounted. "
      + "More info: https://kubernetes.io/docs/concepts/storage/persistent-volumes#access-modes")
  private List<String> accessModes;

  @Description("Capacity is the description of the persistent volume's resources and capacity. "
      + "More info: https://kubernetes.io/docs/concepts/storage/persistent-volumes#capacity")
  private Map<String, Quantity> capacity;

  @Description("HostPath represents a directory on the host. Provisioned by a developer or tester."
      + " This is useful for single-node development and testing only! On-host storage is not supported in any way"
      + " and WILL NOT WORK in a multi-node cluster. More info:\n"
      + " https://kubernetes.io/docs/concepts/storage/volumes#hostpath\n"
      + "Represents a host path mapped into a pod. Host path volumes do not support ownership management"
      + " or SELinux relabeling.")
  private V1HostPathVolumeSource hostPath;

  @Description("PersistentVolumeReclaimPolicy defines what happens to a persistent volume when released from"
      + " its claim. Valid options are Retain (default for manually created PersistentVolumes),"
      + " Delete (default for dynamically provisioned PersistentVolumes), and Recycle (deprecated)."
      + " Recycle must be supported by the volume plugin underlying this PersistentVolume."
      + " More info: https://kubernetes.io/docs/concepts/storage/persistent-volumes#reclaiming  ")
  private String persistentVolumeReclaimPolicy;

  @Description("StorageClassName is the name of StorageClass to which this persistent volume belongs."
      + " Empty value means that this volume does not belong to any StorageClass.")
  private String storageClassName;

  @Description("VolumeMode defines if a volume is intended to be used with a formatted filesystem "
      + "or to remain in raw block state. Value of Filesystem is implied when not included in spec.")
  private String volumeMode;

  public List<String> getAccessModes() {
    return accessModes;
  }

  public PersistentVolumeSpec accessModes(List<String> accessModes) {
    this.accessModes = accessModes;
    return this;
  }

  public Map<String, Quantity> getCapacity() {
    return capacity;
  }

  public PersistentVolumeSpec capacity(Map<String, Quantity> capacity) {
    this.capacity = capacity;
    return this;
  }

  public V1HostPathVolumeSource getHostPath() {
    return hostPath;
  }

  public PersistentVolumeSpec hostPath(V1HostPathVolumeSource hostPath) {
    this.hostPath = hostPath;
    return this;
  }

  public String getPersistentVolumeReclaimPolicy() {
    return persistentVolumeReclaimPolicy;
  }

  public void setPersistentVolumeReclaimPolicy(String persistentVolumeReclaimPolicy) {
    this.persistentVolumeReclaimPolicy = persistentVolumeReclaimPolicy;
  }

  public String getStorageClassName() {
    return storageClassName;
  }

  public PersistentVolumeSpec storageClassName(String storageClassName) {
    this.storageClassName = storageClassName;
    return this;
  }

  public String getVolumeMode() {
    return volumeMode;
  }

  public void setVolumeMode(String volumeMode) {
    this.volumeMode = volumeMode;
  }

  @Override
  public String toString() {
    ToStringBuilder builder =
        new ToStringBuilder(this)
            .append("accessModes", accessModes)
            .append("capacity", capacity)
            .append("hostPath", hostPath)
            .append("persistentVolumeReclaimPolicy", persistentVolumeReclaimPolicy)
            .append("storageClassName", storageClassName)
            .append("volumeMode", volumeMode);

    return builder.toString();
  }

  @Override
  public int hashCode() {
    HashCodeBuilder builder = new HashCodeBuilder()
        .append(accessModes)
        .append(capacity)
        .append(hostPath)
        .append(persistentVolumeReclaimPolicy)
        .append(storageClassName)
        .append(volumeMode);

    return builder.toHashCode();
  }

  @Override
  public boolean equals(Object other) {
    if (other == this) {
      return true;
    } else if (!(other instanceof PersistentVolumeSpec)) {
      return false;
    }

    PersistentVolumeSpec rhs = ((PersistentVolumeSpec) other);
    EqualsBuilder builder =
        new EqualsBuilder()
            .append(accessModes, rhs.accessModes)
            .append(capacity, rhs.capacity)
            .append(hostPath, rhs.hostPath)
            .append(persistentVolumeReclaimPolicy, rhs.persistentVolumeReclaimPolicy)
            .append(storageClassName, rhs.storageClassName)
            .append(volumeMode, rhs.volumeMode);

    return builder.isEquals();
  }
}
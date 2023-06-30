// Copyright (c) 2023, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.weblogic.domain.model;

import io.kubernetes.client.openapi.models.V1ResourceRequirements;
import oracle.kubernetes.json.Description;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;

public class PersistentVolumeClaimSpec {

  @Description("Resources represents the minimum resources the volume should have."
      + " More info https://kubernetes.io/docs/concepts/storage/persistent-volumes#resources."
      + " ResourceRequirements describes the compute resource requirements.")
  private V1ResourceRequirements resources;

  @Description("StorageClassName is the name of StorageClass to which this persistent volume belongs."
      + " Empty value means that this volume does not belong to any StorageClass.")
  private String storageClassName;

  @Description("VolumeName is the binding reference to the PersistentVolume backing this claim.")
  private String volumeName;

  public V1ResourceRequirements getResources() {
    return resources;
  }

  public PersistentVolumeClaimSpec resources(V1ResourceRequirements resources) {
    this.resources = resources;
    return this;
  }

  public String getStorageClassName() {
    return storageClassName;
  }

  public PersistentVolumeClaimSpec storageClassName(String storageClassName) {
    this.storageClassName = storageClassName;
    return this;
  }

  public String getVolumeName() {
    return volumeName;
  }

  public PersistentVolumeClaimSpec volumeName(String volumeName) {
    this.volumeName = volumeName;
    return this;
  }

  @Override
  public String toString() {
    ToStringBuilder builder =
        new ToStringBuilder(this)
            .append("resources", resources)
            .append("storageClassName", storageClassName)
            .append("volumeName", volumeName);

    return builder.toString();
  }

  @Override
  public int hashCode() {
    HashCodeBuilder builder = new HashCodeBuilder()
        .append(resources)
        .append(storageClassName)
        .append(volumeName);

    return builder.toHashCode();
  }

  @Override
  public boolean equals(Object other) {
    if (other == this) {
      return true;
    } else if (!(other instanceof PersistentVolumeClaimSpec)) {
      return false;
    }

    PersistentVolumeClaimSpec rhs = ((PersistentVolumeClaimSpec) other);
    EqualsBuilder builder =
        new EqualsBuilder()
            .append(resources, rhs.resources)
            .append(storageClassName, rhs.storageClassName)
            .append(volumeName, rhs.volumeName);

    return builder.isEquals();
  }
}

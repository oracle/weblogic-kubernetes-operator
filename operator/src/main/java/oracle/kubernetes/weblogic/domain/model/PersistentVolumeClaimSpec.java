// Copyright (c) 2023, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.weblogic.domain.model;

import java.util.List;

import io.kubernetes.client.openapi.models.V1ResourceRequirements;
import io.swagger.annotations.ApiModelProperty;
import oracle.kubernetes.json.Description;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;

public class PersistentVolumeClaimSpec {

  @ApiModelProperty("accessModes contains all ways the volume can be mounted. "
      + "More info: https://kubernetes.io/docs/concepts/storage/persistent-volumes#access-modes")
  private List<String> accessModes;

  @Description("Resources represents the minimum resources the volume should have. If"
      + " RecoverVolumeExpansionFailure feature is enabled users are allowed to\n"
      + " specify resource requirements that are lower than previous value but must\n"
      + " still be higher than capacity recorded in the status field of the claim.\n"
      + " More info:\n"
      + " https://kubernetes.io/docs/concepts/storage/persistent-volumes#resources\n"
      + " ResourceRequirements describes the compute resource requirements.")
  private V1ResourceRequirements resources;

  @ApiModelProperty("storageClassName is the name of StorageClass to which this persistent volume belongs."
      + " Empty value means that this volume does not belong to any StorageClass.")
  private String storageClassName;

  @ApiModelProperty("volumeMode defines if a volume is intended to be used with a formatted filesystem "
      + "or to remain in raw block state. Value of Filesystem is implied when not included in spec.")
  private String volumeMode;

  @ApiModelProperty("volumeName is the binding reference to the PersistentVolume backing this claim.")
  private String volumeName;

  @Override
  public String toString() {
    ToStringBuilder builder =
        new ToStringBuilder(this)
            .append("accessModes", accessModes)
            .append("resources", resources)
            .append("storageClassName", storageClassName)
            .append("volumeMode", volumeMode)
            .append("volumeName", volumeName);

    return builder.toString();
  }

  @Override
  public int hashCode() {
    HashCodeBuilder builder = new HashCodeBuilder()
        .append(accessModes)
        .append(resources)
        .append(storageClassName)
        .append(volumeMode)
        .append(volumeName);

    return builder.toHashCode();
  }

  @Override
  public boolean equals(Object other) {
    if (other == this) {
      return true;
    } else if (!(other instanceof InitDomain)) {
      return false;
    }

    PersistentVolumeClaimSpec rhs = ((PersistentVolumeClaimSpec) other);
    EqualsBuilder builder =
        new EqualsBuilder()
            .append(accessModes, rhs.accessModes)
            .append(resources, rhs.resources)
            .append(storageClassName, rhs.storageClassName)
            .append(volumeMode, rhs.volumeMode)
            .append(volumeName, rhs.volumeName);

    return builder.isEquals();
  }
}

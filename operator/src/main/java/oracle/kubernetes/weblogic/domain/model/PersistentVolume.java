// Copyright (c) 2023, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.weblogic.domain.model;

import io.kubernetes.client.openapi.models.V1ObjectMeta;
import oracle.kubernetes.json.Description;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;

public class PersistentVolume {

  @Description("Standard object's metadata. "
      + "More info: https://git.k8s.io/community/contributors/devel/sig-architecture/api-conventions.md#metadata")
  private V1ObjectMeta metadata;

  @Description("Spec defines a specification of a persistent volume owned by the cluster. More info:"
      + "https://kubernetes.io/docs/concepts/storage/persistent-volumes#persistent-volumes . "
      + "Note: Only a subset of fields in standard Kubernetes PersistentVolume are supported.")
  private PersistentVolumeSpec spec;

  public V1ObjectMeta getMetadata() {
    return metadata;
  }

  public PersistentVolume metadata(V1ObjectMeta metadata) {
    this.metadata = metadata;
    return this;
  }

  public PersistentVolumeSpec getSpec() {
    return spec;
  }

  public PersistentVolume spec(PersistentVolumeSpec spec) {
    this.spec = spec;
    return this;
  }

  @Override
  public String toString() {
    ToStringBuilder builder =
        new ToStringBuilder(this)
            .append("metadata", metadata)
            .append("spec", spec);

    return builder.toString();
  }

  @Override
  public int hashCode() {
    HashCodeBuilder builder = new HashCodeBuilder()
        .append(metadata)
        .append(spec);

    return builder.toHashCode();
  }

  @Override
  public boolean equals(Object other) {
    if (other == this) {
      return true;
    } else if (!(other instanceof PersistentVolume)) {
      return false;
    }

    PersistentVolume rhs = ((PersistentVolume) other);
    EqualsBuilder builder =
        new EqualsBuilder()
            .append(metadata, rhs.metadata)
            .append(spec, rhs.spec);

    return builder.isEquals();
  }
}

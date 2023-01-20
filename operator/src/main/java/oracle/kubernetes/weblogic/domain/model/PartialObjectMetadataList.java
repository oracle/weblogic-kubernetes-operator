// Copyright (c) 2017, 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.weblogic.domain.model;

import java.util.List;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;

public class PartialObjectMetadataList extends KubernetesListObjectImpl {

  private List<PartialObjectMetadata> items;

  public List<PartialObjectMetadata> getItems() {
    return items;
  }

  public void setItems(List<PartialObjectMetadata> items) {
    this.items = items;
  }

  public void addItem(PartialObjectMetadata item) {
    this.items.add(item);
  }

  @Override
  public String toString() {
    return new ToStringBuilder(this)
        .append("apiVersion", apiVersion)
        .append("items", items)
        .append("kind", kind)
        .append("metadata", metadata)
        .toString();
  }

  @Override
  public int hashCode() {
    return new HashCodeBuilder()
        .append(metadata)
        .append(apiVersion)
        .append(items)
        .append(kind)
        .toHashCode();
  }

  @Override
  public boolean equals(Object other) {
    if (other == this) {
      return true;
    }
    if (!(other instanceof PartialObjectMetadataList)) {
      return false;
    }
    PartialObjectMetadataList rhs = ((PartialObjectMetadataList) other);
    return new EqualsBuilder()
        .append(metadata, rhs.metadata)
        .append(apiVersion, rhs.apiVersion)
        .append(items, rhs.items)
        .append(kind, rhs.kind)
        .isEquals();
  }
}


// Copyright (c) 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.weblogic.domain.model;

import java.util.ArrayList;
import java.util.List;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;
import io.kubernetes.client.openapi.models.V1ListMeta;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;

@Description("DomainList is a list of Domains.")
public class DomainList {

  @Description("The API version for the Domain.")
  private String apiVersion;

  @Description(
      "List of domains. More info: https://git.k8s.io/community/contributors/devel/api-conventions.md. Required.")
  private List<Domain> items = new ArrayList<Domain>();

  @Description("The type of resource. Must be 'DomainList'.")
  private String kind;

  @Description(
      "Standard list metadata. "
          + "More info: https://git.k8s.io/community/contributors/devel/api-conventions.md#types-kinds.")
  private V1ListMeta metadata;

  public DomainList apiVersion(String apiVersion) {
    this.apiVersion = apiVersion;
    return this;
  }

  public String getApiVersion() {
    return apiVersion;
  }

  public void setApiVersion(String apiVersion) {
    this.apiVersion = apiVersion;
  }

  public DomainList items(List<Domain> items) {
    this.items = items;
    return this;
  }

  public List<Domain> getItems() {
    return items;
  }

  public void setItems(List<Domain> items) {
    this.items = items;
  }

  public DomainList kind(String kind) {
    this.kind = kind;
    return this;
  }

  public String getKind() {
    return kind;
  }

  public void setKind(String kind) {
    this.kind = kind;
  }

  public DomainList metadata(V1ListMeta metadata) {
    this.metadata = metadata;
    return this;
  }

  public V1ListMeta getMetadata() {
    return metadata;
  }

  public void setMetadata(V1ListMeta metadata) {
    this.metadata = metadata;
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
    if (!(other instanceof DomainList)) {
      return false;
    }
    DomainList rhs = ((DomainList) other);
    return new EqualsBuilder()
        .append(metadata, rhs.metadata)
        .append(apiVersion, rhs.apiVersion)
        .append(items, rhs.items)
        .append(kind, rhs.kind)
        .isEquals();
  }

}

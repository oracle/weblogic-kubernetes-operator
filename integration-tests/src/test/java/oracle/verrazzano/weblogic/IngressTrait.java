// Copyright (c) 2023, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.verrazzano.weblogic;

import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;

@ApiModel(
    description
    = "IngressTrait represents a Verrazzano IngressTrait and how it will be realized in the Kubernetes cluster.")
public class IngressTrait {

  @ApiModelProperty("The API version for the ApplicationConfiguration.")
  public String apiVersion;

  @ApiModelProperty("The type of resource. Must be 'IngressTrait'.")
  public String kind;

  @ApiModelProperty("The IngressTrait meta-data. Must include the name and namespace.")
  public V1ObjectMeta metadata = new V1ObjectMeta();

  @ApiModelProperty("The specification of the IngressTrait. Required.")
  public IngressTraitSpec spec = new IngressTraitSpec();

  public IngressTrait apiVersion(String apiVersion) {
    this.apiVersion = apiVersion;
    return this;
  }

  public String apiVersion() {
    return apiVersion;
  }

  public String getApiVersion() {
    return apiVersion;
  }

  public void setApiVersion(String apiVersion) {
    this.apiVersion = apiVersion;
  }

  public IngressTrait kind(String kind) {
    this.kind = kind;
    return this;
  }

  public String kind() {
    return kind;
  }

  public String getKind() {
    return kind;
  }

  public void setKind(String kind) {
    this.kind = kind;
  }

  public IngressTrait metadata(V1ObjectMeta metadata) {
    this.metadata = metadata;
    return this;
  }

  public V1ObjectMeta metadata() {
    return metadata;
  }

  public V1ObjectMeta getMetadata() {
    return metadata;
  }

  public void setMetadata(V1ObjectMeta metadata) {
    this.metadata = metadata;
  }

  public IngressTrait spec(IngressTraitSpec spec) {
    this.spec = spec;
    return this;
  }

  public IngressTraitSpec spec() {
    return spec;
  }

  public IngressTraitSpec getSpec() {
    return spec;
  }

  public void setSpec(IngressTraitSpec spec) {
    this.spec = spec;
  }

  @Override
  public String toString() {
    return new ToStringBuilder(this)
        .append("apiVersion", apiVersion)
        .append("kind", kind)
        .append("metadata", metadata)
        .append("spec", spec)
        .toString();
  }

  @Override
  public int hashCode() {
    return new HashCodeBuilder()
        .append(metadata)
        .append(apiVersion)
        .append(kind)
        .append(spec)
        .toHashCode();
  }

  @Override
  public boolean equals(Object other) {
    if (this == other) {
      return true;
    }

    if (other == null || getClass() != other.getClass()) {
      return false;
    }
    IngressTrait rhs = (IngressTrait) other;
    return new EqualsBuilder()
        .append(metadata, rhs.metadata)
        .append(apiVersion, rhs.apiVersion)
        .append(kind, rhs.kind)
        .append(spec, rhs.spec)
        .isEquals();
  }
}

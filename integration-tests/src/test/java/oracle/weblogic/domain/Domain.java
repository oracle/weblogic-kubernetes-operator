// Copyright (c) 2020, 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.domain;

import io.kubernetes.client.common.KubernetesObject;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;

@ApiModel(
    description =
        "Domain represents a WebLogic domain and how it will be realized in the Kubernetes cluster.")
public class Domain implements KubernetesObject {

  @ApiModelProperty("The API version for the Domain.")
  private String apiVersion;

  @ApiModelProperty("The type of resource. Must be 'Domain'.")
  private String kind;

  @ApiModelProperty("The domain meta-data. Must include the name and namespace.")
  private V1ObjectMeta metadata = new V1ObjectMeta();

  @ApiModelProperty("The specification of the domain. Required.")
  private DomainSpec spec = new DomainSpec();

  @ApiModelProperty("The current status of the domain. Updated by the operator.")
  private DomainStatus status;

  public Domain apiVersion(String apiVersion) {
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

  public Domain kind(String kind) {
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

  public Domain metadata(V1ObjectMeta metadata) {
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

  public Domain spec(DomainSpec spec) {
    this.spec = spec;
    return this;
  }

  public DomainSpec spec() {
    return spec;
  }

  public DomainSpec getSpec() {
    return spec;
  }

  public void setSpec(DomainSpec spec) {
    this.spec = spec;
  }

  public Domain status(DomainStatus status) {
    this.status = status;
    return this;
  }

  public DomainStatus status() {
    return status;
  }

  public DomainStatus getStatus() {
    return status;
  }

  public void setStatus(DomainStatus status) {
    this.status = status;
  }

  @Override
  public String toString() {
    return new ToStringBuilder(this)
        .append("apiVersion", apiVersion)
        .append("kind", kind)
        .append("metadata", metadata)
        .append("spec", spec)
        .append("status", status)
        .toString();
  }

  @Override
  public int hashCode() {
    return new HashCodeBuilder()
        .append(metadata)
        .append(apiVersion)
        .append(kind)
        .append(spec)
        .append(status)
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
    Domain rhs = (Domain) other;
    return new EqualsBuilder()
        .append(metadata, rhs.metadata)
        .append(apiVersion, rhs.apiVersion)
        .append(kind, rhs.kind)
        .append(spec, rhs.spec)
        .append(status, rhs.status)
        .isEquals();
  }
}

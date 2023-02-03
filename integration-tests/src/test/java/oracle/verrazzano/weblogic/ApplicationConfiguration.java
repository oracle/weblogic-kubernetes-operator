// Copyright (c) 2023, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.verrazzano.weblogic;

import io.kubernetes.client.common.KubernetesObject;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;

@ApiModel(
    description
    = "ApplicationConfiguration represents a Verrazzano application.")
public class ApplicationConfiguration implements KubernetesObject {

  @ApiModelProperty("The API version for the ApplicationConfiguration.")
  private String apiVersion;

  @ApiModelProperty("The type of resource. Must be 'ApplicationConfiguration'.")
  private String kind;

  @ApiModelProperty("The ApplicationConfiguration meta-data. Must include the name and namespace.")
  private V1ObjectMeta metadata = new V1ObjectMeta();

  @ApiModelProperty("The specification of the ApplicationConfiguration. Required.")
  private ApplicationConfigurationSpec spec = new ApplicationConfigurationSpec();

  public ApplicationConfiguration apiVersion(String apiVersion) {
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

  public ApplicationConfiguration kind(String kind) {
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

  public ApplicationConfiguration metadata(V1ObjectMeta metadata) {
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
  
  public ApplicationConfiguration spec(ApplicationConfigurationSpec spec) {
    this.spec = spec;
    return this;
  }

  public ApplicationConfigurationSpec spec() {
    return spec;
  }

  public ApplicationConfigurationSpec getSpec() {
    return spec;
  }

  public void setSpec(ApplicationConfigurationSpec spec) {
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
    ApplicationConfiguration rhs = (ApplicationConfiguration) other;
    return new EqualsBuilder()
        .append(metadata, rhs.metadata)
        .append(apiVersion, rhs.apiVersion)
        .append(kind, rhs.kind)
        .append(spec, rhs.spec)
        .isEquals();
  }
}

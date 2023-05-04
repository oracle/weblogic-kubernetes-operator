// Copyright (c) 2023, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.verrazzano.weblogic;

import java.util.HashMap;
import java.util.Map;

import io.kubernetes.client.common.KubernetesObject;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;

@ApiModel(
    description
    = "Workload represents a Verrazzano Workload and how it will be realized in the Kubernetes cluster.")
public class Workload implements KubernetesObject {

  @ApiModelProperty("The API version for the Workload.")
  private String apiVersion;

  @ApiModelProperty("The type of resource. Must be 'Workload'.")
  private String kind;

  @ApiModelProperty("The Workload meta-data. Must include the name and namespace.")
  private V1ObjectMeta metadata = new V1ObjectMeta();

  @ApiModelProperty("Configmap data of the Workload.")
  Map<String, String> data = new HashMap<>();
  
  @ApiModelProperty("The specification of the Workload. Required.")
  private WorkloadSpec spec = new WorkloadSpec();
  
  public Workload apiVersion(String apiVersion) {
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

  public Workload kind(String kind) {
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

  public Workload metadata(V1ObjectMeta metadata) {
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

  public Workload spec(WorkloadSpec spec) {
    this.spec = spec;
    return this;
  }

  public WorkloadSpec spec() {
    return spec;
  }

  public WorkloadSpec getSpec() {
    return spec;
  }

  public void setSpec(WorkloadSpec spec) {
    this.spec = spec;
  }
  
  public Workload data(Map<String, String> data) {
    this.data = data;
    return this;
  }

  public Map<String, String> data() {
    return data;
  }

  public Map<String, String> getData() {
    return data;
  }

  public void setData(Map<String, String> data) {
    this.data = data;
  }    
  
  @Override
  public String toString() {
    return new ToStringBuilder(this)
        .append("apiVersion", apiVersion)
        .append("kind", kind)
        .append("metadata", metadata)
        .append("spec", spec)
        .append("data", data)
        .toString();
  }

  @Override
  public int hashCode() {
    return new HashCodeBuilder()
        .append(metadata)
        .append(apiVersion)
        .append(kind)
        .append(spec)
        .append(data)
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
    Workload rhs = (Workload) other;
    return new EqualsBuilder()
        .append(metadata, rhs.metadata)
        .append(apiVersion, rhs.apiVersion)
        .append(kind, rhs.kind)
        .append(spec, rhs.spec)
        .append(data, rhs.data)
        .isEquals();
  }

}
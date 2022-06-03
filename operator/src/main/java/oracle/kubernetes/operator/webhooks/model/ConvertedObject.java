// Copyright (c) 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.http.rest.model;

import java.util.Objects;

import com.google.gson.annotations.Expose;
import io.kubernetes.client.openapi.models.V1ObjectMeta;

public class ConvertedObject {

  @Expose
  private String kind;
  @Expose
  private String apiVersion;
  @Expose
  private V1ObjectMeta metadata;

  public String getKind() {
    return kind;
  }

  public void setKind(String kind) {
    this.kind = kind;
  }

  public String getApiVersion() {
    return apiVersion;
  }

  public void setApiVersion(String apiVersion) {
    this.apiVersion = apiVersion;
  }

  public V1ObjectMeta getMetadata() {
    return metadata;
  }

  public void setMetadata(V1ObjectMeta metadata) {
    this.metadata = metadata;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    ConvertedObject that = (ConvertedObject) o;
    return Objects.equals(kind, that.kind)
            && Objects.equals(apiVersion, that.apiVersion)
            && Objects.equals(metadata, that.metadata);
  }

  @Override
  public int hashCode() {
    return Objects.hash(kind, apiVersion, metadata);
  }

  @Override
  public String toString() {
    return "ConvertedObject{"
            + "kind='" + kind + '\''
            + ", apiVersion='" + apiVersion + '\''
            + ", metadata=" + metadata
            + '}';
  }
}

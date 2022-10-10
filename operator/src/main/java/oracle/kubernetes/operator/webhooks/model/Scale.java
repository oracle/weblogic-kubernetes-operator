// Copyright (c) 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.webhooks.model;

import java.util.Map;
import java.util.Objects;

import com.google.gson.annotations.Expose;
import io.kubernetes.client.openapi.models.V1ObjectMeta;

public class Scale {

  @Expose
  private V1ObjectMeta metadata;

  @Expose
  private Map<String, String> status;

  @Expose
  private Map<String, String> spec;

  public V1ObjectMeta getMetadata() {
    return metadata;
  }

  public Scale metadata(V1ObjectMeta metadata) {
    this.metadata = metadata;
    return this;
  }

  public Map<String, String> getStatus() {
    return status;
  }

  public Scale status(Map<String, String> status) {
    this.status = status;
    return this;
  }

  public Map<String, String> getSpec() {
    return spec;
  }

  public Scale spec(Map<String, String> spec) {
    this.spec = spec;
    return this;
  }

  @Override
  public String toString() {
    return "Scale{"
        + "metadata=" + metadata + '\''
        + ", spec='" + spec + '\''
        + ", status='" + status + '\''
        + '}';
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    Scale result = (Scale) o;
    return Objects.equals(metadata, result.metadata)
        && Objects.equals(spec, result.spec)
            && Objects.equals(status, result.status);
  }

  @Override
  public int hashCode() {
    return Objects.hash(metadata, spec.hashCode(), status.hashCode());
  }
}
// Copyright (c) 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.webhooks.model;

import java.util.Objects;

import com.google.gson.annotations.Expose;
import io.kubernetes.client.openapi.models.V1ObjectMeta;

public class Result {

  @Expose
  private V1ObjectMeta metadata;

  @Expose
  private String status;

  @Expose
  private String message;

  public V1ObjectMeta getMetadata() {
    return metadata;
  }

  public Result metadata(V1ObjectMeta metadata) {
    this.metadata = metadata;
    return this;
  }

  public String getStatus() {
    return status;
  }

  public Result status(String status) {
    this.status = status;
    return this;
  }

  public String getMessage() {
    return message;
  }

  public Result message(String message) {
    this.message = message;
    return this;
  }

  @Override
  public String toString() {
    return "Result{"
            + "metadata=" + metadata
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
    Result result = (Result) o;
    return Objects.equals(metadata, result.metadata)
            && Objects.equals(status, result.status);
  }

  @Override
  public int hashCode() {
    return Objects.hash(metadata, status);
  }
}
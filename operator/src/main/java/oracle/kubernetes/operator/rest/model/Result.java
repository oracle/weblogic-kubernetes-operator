// Copyright (c) 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.rest.model;

import com.google.gson.annotations.Expose;
import io.kubernetes.client.openapi.models.V1ObjectMeta;

public class Result {

  @Expose
  private V1ObjectMeta metadata;

  @Expose
  private String status;

  /**
   * Get the metadata.
   * @return
   *     The metadata
   */
  public V1ObjectMeta getMetadata() {
    return metadata;
  }

  public void setMetadata(V1ObjectMeta metadata) {
    this.metadata = metadata;
  }

  public Result metadata(V1ObjectMeta metadata) {
    this.metadata = metadata;
    return this;
  }

  /**
   * Get the status.
   * @return
   *     The status
   */
  public String getStatus() {
    return status;
  }

  /**
   * Set the status.
   * @param status
   *     The status
   */
  public void setStatus(String status) {
    this.status = status;
  }

  public Result status(String status) {
    this.status = status;
    return this;
  }

}

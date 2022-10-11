// Copyright (c) 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.webhooks.model;

import java.util.Map;

import com.google.gson.annotations.Expose;
import io.kubernetes.client.openapi.models.V1ObjectMeta;

public class Scale {

  @Expose
  private V1ObjectMeta metadata;

  @Expose
  private Map<String, String> spec;

  public V1ObjectMeta getMetadata() {
    return metadata;
  }

  public Scale metadata(V1ObjectMeta metadata) {
    this.metadata = metadata;
    return this;
  }

  public Map<String, String> getSpec() {
    return spec;
  }

  public Scale spec(Map<String, String> spec) {
    this.spec = spec;
    return this;
  }

}
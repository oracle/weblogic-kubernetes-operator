// Copyright 2017, 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.custom;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.kubernetes.client.models.V1ObjectMeta;
import java.util.Map;

/** Example Custom Resource class */
public class TestDomain {

  @JsonProperty("apiVersion")
  private String apiVersion;

  @JsonProperty("kind")
  private String kind;

  @JsonProperty("metadata")
  private V1ObjectMeta metadata;

  @JsonProperty("spec")
  private Map<String, String> spec;

  // This is necessary to be implemented for the resourceVersion tracker
  // in the watch wrapper. When not present, watch requests will return
  // everything from the beginning of the resource history.
  public String getResourceVersion() {
    return (String) metadata.getResourceVersion();
  }

  public V1ObjectMeta getMetadata() {
    return metadata;
  }

  public void setMetadata(V1ObjectMeta metadata) {
    this.metadata = metadata;
  }

  public String getApiVersion() {
    return apiVersion;
  }

  public void setApiVersion(String apiVersion) {
    this.apiVersion = apiVersion;
  }

  public String getKind() {
    return kind;
  }

  public void setKind(String kind) {
    this.kind = kind;
  }

  public Map<String, String> getSpec() {
    return spec;
  }

  public void setSpec(Map<String, String> spec) {
    this.spec = spec;
  }

  @Override
  public String toString() {
    return kind
        + "."
        + apiVersion
        + "."
        + metadata.getName()
        + " resourceVersion="
        + metadata.getResourceVersion()
        + ", spec: "
        + spec.toString();
  }
}

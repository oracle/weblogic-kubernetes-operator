// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.weblogic.domain.v1;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class ExportedNetworkAccessPoint {

  @SerializedName("labels")
  @Expose
  private Map<String, String> labels = new HashMap<>();

  @SerializedName("annotations")
  @Expose
  private Map<String, String> annotations = new HashMap<>();

  public ExportedNetworkAccessPoint addLabel(String name, String value) {
    labels.put(name, value);
    return this;
  }

  public Map<String, String> getLabels() {
    return Collections.unmodifiableMap(labels);
  }

  public ExportedNetworkAccessPoint addAnnotation(String name, String value) {
    annotations.put(name, value);
    return this;
  }

  public Map<String, String> getAnnotations() {
    return Collections.unmodifiableMap(annotations);
  }
}

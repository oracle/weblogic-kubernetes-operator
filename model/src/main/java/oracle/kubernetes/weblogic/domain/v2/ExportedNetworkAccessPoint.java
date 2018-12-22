// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.weblogic.domain.v2;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;

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

  @Override
  public String toString() {
    return new ToStringBuilder(this)
        .append("labels", labels)
        .append("annotations", annotations)
        .toString();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;

    if (o == null || getClass() != o.getClass()) return false;

    ExportedNetworkAccessPoint that = (ExportedNetworkAccessPoint) o;

    return new EqualsBuilder()
        .append(labels, that.labels)
        .append(annotations, that.annotations)
        .isEquals();
  }

  @Override
  public int hashCode() {
    return new HashCodeBuilder(17, 37).append(labels).append(annotations).toHashCode();
  }
}

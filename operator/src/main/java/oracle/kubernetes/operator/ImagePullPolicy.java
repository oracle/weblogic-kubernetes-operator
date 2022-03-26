// Copyright (c) 2018, 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator;

import com.google.gson.annotations.SerializedName;

public enum ImagePullPolicy implements Labeled {
  @SerializedName("Always")
  ALWAYS("Always"),
  @SerializedName("Never")
  NEVER("Never"),
  @SerializedName("IfNotPresent")
  IF_NOT_PRESENT("IfNotPresent");

  private final String label;

  ImagePullPolicy(String label) {
    this.label = label;
  }

  @Override
  public String label() {
    return label;
  }

  @Override
  public String toString() {
    return label();
  }
}

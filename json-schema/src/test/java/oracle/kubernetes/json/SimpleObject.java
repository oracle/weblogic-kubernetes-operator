// Copyright (c) 2018, 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.json;

import java.util.Map;
import javax.annotation.Nonnull;

import com.google.gson.annotations.SerializedName;

@SuppressWarnings("unused")
class SimpleObject {
  private static final int staticInt = 2;

  @Description("A flag")
  @Nonnull
  private final Boolean aaBoolean = true;

  @Description("A string")
  private String aaString;

  @SerializedName("depth")
  private float aaFloat;

  private Map<String,Integer> keys;
}

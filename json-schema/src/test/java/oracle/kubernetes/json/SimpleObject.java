// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.json;

import com.google.gson.annotations.SerializedName;
import javax.annotation.Nonnull;

@SuppressWarnings("unused")
class SimpleObject {
  @Description("A flag")
  @Nonnull
  private Boolean aBoolean = true;

  @Description("A string")
  private String aString;

  @SerializedName("depth")
  private float aFloat;

  private static int staticInt = 2;
}

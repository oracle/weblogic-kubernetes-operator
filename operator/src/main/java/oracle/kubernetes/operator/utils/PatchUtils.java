// Copyright 2019 Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.utils;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.List;
import java.util.stream.Collectors;
import javax.json.JsonPatch;
import javax.json.JsonValue;

public class PatchUtils {
  public static List<JsonObject> toKubernetesPatch(JsonPatch jsonPatch) {
    return jsonPatch
        .toJsonArray()
        .stream()
        .map(PatchUtils::toJsonObject)
        .collect(Collectors.toList());
  }

  private static JsonObject toJsonObject(JsonValue value) {
    return new Gson().fromJson(value.toString(), JsonElement.class).getAsJsonObject();
  }
}

// Copyright (c) 2019, 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.utils;

import java.util.List;
import java.util.stream.Collectors;
import javax.json.JsonPatch;
import javax.json.JsonValue;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

public class PatchUtils {
  /**
   * Convert to a Kubernetes patch.
   * @param jsonPatch the patch in JSON
   * @return the Kubernetes patch object
   */
  public static List<JsonObject> toKubernetesPatch(JsonPatch jsonPatch) {
    return jsonPatch.toJsonArray().stream()
        .map(PatchUtils::toJsonObject)
        .collect(Collectors.toList());
  }

  private static JsonObject toJsonObject(JsonValue value) {
    return new Gson().fromJson(value.toString(), JsonElement.class).getAsJsonObject();
  }
}

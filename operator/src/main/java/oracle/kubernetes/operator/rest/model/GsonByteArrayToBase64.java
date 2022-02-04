// Copyright (c) 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.rest.model;

import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;

public class GsonByteArrayToBase64 implements JsonSerializer<byte[]>, JsonDeserializer<byte[]> {
  /**
   * Deserialize the byte array object.
   */
  public byte[] deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context)
          throws JsonParseException {
    return Base64.getDecoder().decode(json.getAsString());
  }

  /**
  * Serialize the byte array object.
  */
  public JsonElement serialize(byte[] src, Type typeOfSrc, JsonSerializationContext context) {
    try {
      return new JsonPrimitive(new String(Base64.getEncoder().encode(src), StandardCharsets.UTF_8));
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }
}

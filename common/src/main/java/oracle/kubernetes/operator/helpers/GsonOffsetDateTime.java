// Copyright (c) 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helpers;

import java.lang.reflect.Type;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;

public class GsonOffsetDateTime implements JsonSerializer<OffsetDateTime>, JsonDeserializer<OffsetDateTime> {

  @Override
  public OffsetDateTime deserialize(JsonElement jsonElement, Type type,
                                    JsonDeserializationContext jsonDeserializationContext) throws JsonParseException {
    String ldtString = jsonElement.getAsString();
    return OffsetDateTime.parse(ldtString,DateTimeFormatter.ISO_OFFSET_DATE_TIME);
  }

  @Override
  public JsonElement serialize(OffsetDateTime localDateTime, Type type,
                               JsonSerializationContext jsonSerializationContext) {
    return new JsonPrimitive(localDateTime.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
  }
}
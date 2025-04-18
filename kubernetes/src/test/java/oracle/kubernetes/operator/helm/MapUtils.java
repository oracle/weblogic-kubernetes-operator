// Copyright (c) 2018, 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helm;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;

class MapUtils {
  static <T> void loadFromMap(Map<String, Object> map, Consumer<String> setter, String key) {
    if (map.containsKey(key)) {
      setter.accept((String) map.get(key));
    }
  }

  static void loadBooleanFromMap(Map<String, Object> map, Consumer<Boolean> setter, String key) {
    if (map.containsKey(key)) {
      setter.accept((Boolean) map.get(key));
    }
  }

  static void loadIntegerFromMap(Map<String, Object> map, Consumer<String> setter, String key) {
    Integer value = (Integer) map.get(key);
    if (value != null) {
      setter.accept(value.toString());
    }
  }

  static Boolean valueOf(String stringValue) {
    switch (stringValue) {
      case "false":
        return false;
      case "true":
        return true;
      default:
        return null;
    }
  }

  static Integer integerValue(String integerString) {
    if (integerString.isEmpty()) {
      return null;
    } else {
      return Integer.parseInt(integerString);
    }
  }

  static void addStringMapEntry(HashMap<String, Object> map, Supplier<String> getter, String key) {
    if (!getter.get().isEmpty()) {
      map.put(key, getter.get());
    }
  }

  static void addMapEntry(HashMap<String, Object> map, Supplier<Object> getter, String key) {
    if (getter.get() != null) {
      map.put(key, getter.get());
    }
  }
}

// Copyright (c) 2019, 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helpers;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

class CheckFactory {
  static <T> CompatibilityCheck create(String description, List<T> expected, List<T> actual) {
    if (canBeMap(expected) && canBeMap(actual)) {
      return new CompatibleMaps<>(description, asMap(expected), asMap(actual));
    } else {
      return new CompatibleSets<>(description, expected, actual);
    }
  }

  private static <T> boolean canBeMap(List<T> list) {
    return asMap(list) != null;
  }

  private static <T> Map<String, T> asMap(List<T> values) {
    if (values == null) {
      return Collections.emptyMap();
    }
    Map<String, T> result = new HashMap<>();
    for (T value : values) {
      String key = getKey(value);
      if (key == null) {
        return null;
      }
      result.put(key, value);
    }

    return result;
  }

  private static <T> String getKey(T value) {
    try {
      Method getKey = value.getClass().getDeclaredMethod("getName");
      return (String) getKey.invoke(value);
    } catch (NoSuchMethodException | InvocationTargetException | IllegalAccessException e) {
      return null;
    }
  }
}

// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helm;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.Map;
import oracle.kubernetes.operator.utils.OperatorValues;

class HelmOperatorValues extends OperatorValues {
  HelmOperatorValues() {}

  HelmOperatorValues(Map<String, Object> mappedValues) {
    for (Map.Entry<String, Object> entry : mappedValues.entrySet()) {
      assignToField(entry.getKey(), entry.getValue());
    }
  }

  private void assignToField(String key, Object value) {
    try {
      Field field = getClass().getSuperclass().getDeclaredField(key);
      setFieldValue(field, value);
    } catch (NoSuchFieldException | IllegalAccessException ignored) {
    }
  }

  private void setFieldValue(Field field, Object value) throws IllegalAccessException {
    boolean wasAccessible = field.isAccessible();
    try {
      field.setAccessible(true);
      field.set(this, value.toString());
    } finally {
      field.setAccessible(wasAccessible);
    }
  }

  Map<String, Object> createMap() {
    HashMap<String, Object> map = new HashMap<>();
    map.put("internalOperatorCert", "test-cert");
    map.put("internalOperatorKey", "test-key");

    for (Field field : getClass().getSuperclass().getDeclaredFields()) {
      addToMapIfNeeded(map, field);
    }
    return map;
  }

  private void addToMapIfNeeded(HashMap<String, Object> map, Field field) {
    try {
      Object value = getValue(field);
      if (includeInMap(field, value)) {
        map.put(field.getName(), value);
      }
    } catch (IllegalAccessException ignored) {
    }
  }

  private boolean includeInMap(Field field, Object value) {
    return !Modifier.isStatic(field.getModifiers()) && !isEmptyString(value);
  }

  private boolean isEmptyString(Object value) {
    return value instanceof String && ((String) value).length() == 0;
  }

  private Object getValue(Field field) throws IllegalAccessException {
    boolean wasAccessible = field.isAccessible();
    try {
      field.setAccessible(true);
      return field.get(this);
    } finally {
      field.setAccessible(wasAccessible);
    }
  }
}

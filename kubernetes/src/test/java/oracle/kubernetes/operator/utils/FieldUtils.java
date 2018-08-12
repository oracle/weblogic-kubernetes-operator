// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.utils;

import java.lang.reflect.Field;

public class FieldUtils {
  @SuppressWarnings("unchecked")
  public static <T> T getValue(Object object, String fieldName) throws IllegalAccessException {
    return (T) getValue(object, getField(object.getClass(), fieldName));
  }

  private static Object getValue(Object object, Field field) throws IllegalAccessException {
    boolean wasAccessible = field.isAccessible();
    try {
      field.setAccessible(true);
      return field.get(object);
    } finally {
      field.setAccessible(wasAccessible);
    }
  }

  private static Field getField(Class<?> aClass, String fieldName) {
    assert aClass != null : "No such field '" + fieldName + "'";

    try {
      return aClass.getDeclaredField(fieldName);
    } catch (NoSuchFieldException e) {
      return getField(aClass.getSuperclass(), fieldName);
    }
  }
}

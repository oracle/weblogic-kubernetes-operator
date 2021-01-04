// Copyright (c) 2018, 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.json;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.FIELD;

/** Specifies an enum class whose values match the permitted values for the field. */
@Retention(RetentionPolicy.RUNTIME)
@Target(FIELD)
public @interface EnumClass {

  /**
   * Enum class.
   *
   * @return enum class
   */
  Class<? extends java.lang.Enum> value();

  /**
   * Enum qualifier.
   *
   * @return enum qualifier
   */
  String qualifier() default "";
}

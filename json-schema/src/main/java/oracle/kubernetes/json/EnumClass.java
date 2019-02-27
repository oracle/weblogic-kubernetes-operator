// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.json;

import static java.lang.annotation.ElementType.FIELD;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

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

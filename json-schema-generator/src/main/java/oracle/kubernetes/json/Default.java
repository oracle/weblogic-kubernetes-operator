// Copyright (c) 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.json;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.FIELD;

/** Specifies default value for the field. */
@Retention(RetentionPolicy.RUNTIME)
@Target(FIELD)
public @interface Default {

  /**
   * Default value.
   *
   * @return default value
   */
  String strDefault() default "";

  /**
   * Default numeric value.
   *
   * @return default value
   */
  int intDefault() default Integer.MAX_VALUE;

  /**
   * Default boolean value.
   *
   * @return default value
   */
  boolean boolDefault() default false;
}

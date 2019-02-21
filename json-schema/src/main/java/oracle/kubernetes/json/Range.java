// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.json;

import static java.lang.annotation.ElementType.FIELD;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Specifies minimum and/or maximum permitted values for the field. */
@Retention(RetentionPolicy.RUNTIME)
@Target(FIELD)
public @interface Range {

  /**
   * Minimum value.
   *
   * @return minimum value
   */
  int minimum() default Integer.MIN_VALUE;

  /**
   * Maximum value.
   *
   * @return maximum value
   */
  int maximum() default Integer.MAX_VALUE;
}

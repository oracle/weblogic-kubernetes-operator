// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.json;

import static java.lang.annotation.ElementType.FIELD;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Supplies an ECMA 262 regular expression that the field must match. */
@Retention(RetentionPolicy.RUNTIME)
@Target(FIELD)
public @interface Pattern {

  /**
   * Pattern value.
   *
   * @return pattern value
   */
  String value();
}

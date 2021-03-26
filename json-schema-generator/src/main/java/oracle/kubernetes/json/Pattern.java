// Copyright (c) 2018, 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.json;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.FIELD;

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

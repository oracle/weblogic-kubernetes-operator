// Copyright (c) 2018, 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.json;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.TYPE;

/** Supplies a description for a field to be inserted into the generated JSON schema. */
@Retention(RetentionPolicy.RUNTIME)
@Target({TYPE, FIELD})
public @interface Description {

  /**
   * Description value.
   *
   * @return description value
   */
  String value();
}

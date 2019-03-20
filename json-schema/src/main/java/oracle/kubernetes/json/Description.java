// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.json;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.TYPE;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

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

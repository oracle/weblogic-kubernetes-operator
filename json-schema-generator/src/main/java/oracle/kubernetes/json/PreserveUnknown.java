// Copyright (c) 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.json;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.TYPE;

/** States that all values in the map field in question should be preserved, even though they are not in its schema. */
@Retention(RetentionPolicy.RUNTIME)
@Target({TYPE, FIELD})
public @interface PreserveUnknown {
}

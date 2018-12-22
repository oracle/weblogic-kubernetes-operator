// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helpers;

/** An interface to permit inexact comparisons of body objects sent with async calls. */
public interface BodyMatcher {

  /** Returns true if the actual body sent meets expectations. */
  boolean matches(Object actualBody);
}

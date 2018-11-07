// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.json;

@SuppressWarnings("unused")
class ReferencingObject {
  private SimpleObject simple;
  private DerivedObject derived;

  @Deprecated private int deprecatedField;
}

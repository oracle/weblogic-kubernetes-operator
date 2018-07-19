// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.utils;

public abstract class OperatorYamlFactory {
  public OperatorValues newOperatorValues() throws Exception {
    return createDefaultValues().withTestDefaults();
  }

  public abstract OperatorValues createDefaultValues() throws Exception;

  public abstract GeneratedOperatorObjects generate(OperatorValues inputValues) throws Exception;
}

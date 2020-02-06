// Copyright (c) 2018, 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.utils;

public abstract class OperatorYamlFactory {
  public OperatorValues newOperatorValues() throws Exception {
    return createDefaultValues().withTestDefaults();
  }

  public abstract OperatorValues createDefaultValues() throws Exception;

  public abstract GeneratedOperatorObjects generate(OperatorValues inputValues) throws Exception;
}

// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.utils;

public abstract class DomainYamlFactory {
  public DomainValues newDomainValues() throws Exception {
    return createDefaultValues().withTestDefaults();
  }

  public abstract CreateDomainInputs createDefaultValues() throws Exception;

  public abstract GeneratedDomainYamlFiles generate(DomainValues values) throws Exception;
}

// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.create;

import oracle.kubernetes.operator.utils.CreateDomainInputs;
import oracle.kubernetes.operator.utils.DomainValues;
import oracle.kubernetes.operator.utils.DomainYamlFactory;
import oracle.kubernetes.operator.utils.GeneratedDomainYamlFiles;

public class ScriptedDomainYamlFactory extends DomainYamlFactory {

  @Override
  public CreateDomainInputs createDefaultValues() throws Exception {
    return CreateDomainInputs.readDefaultInputsFile();
  }

  @Override
  public GeneratedDomainYamlFiles generate(DomainValues values) throws Exception {
    return GeneratedDomainYamlFiles.generateDomainYamlFiles(values);
  }
}

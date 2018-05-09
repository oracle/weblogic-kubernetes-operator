// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.create;

import java.nio.file.Path;
import oracle.kubernetes.weblogic.domain.v1.Domain;

/** Parses a generated domain-custom-resource.yaml file into a set of typed k8s java objects */
public class ParsedDomainCustomResourceYaml extends ParsedKubernetesYaml {

  private CreateDomainInputs inputs;

  public ParsedDomainCustomResourceYaml(Path yamlPath, CreateDomainInputs inputs) throws Exception {
    super(yamlPath);
    this.inputs = inputs;
  }

  public Domain getDomain() {
    return getDomains().find(inputs.getDomainUID());
  }

  public int getExpectedObjectCount() {
    return 1;
  }
}

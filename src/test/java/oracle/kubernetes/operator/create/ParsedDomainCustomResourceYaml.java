// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
package oracle.kubernetes.operator.create;

import java.nio.file.Path;

import static oracle.kubernetes.operator.create.KubernetesArtifactUtils.*;
import oracle.kubernetes.weblogic.domain.v1.Domain;

/**
 * Parses a generated domain-custom-resource.yaml file into a set of typed k8s java objects
 */
public class ParsedDomainCustomResourceYaml {

  private CreateDomainInputs inputs;
  private ParsedKubernetesYaml parsedYaml;

  public ParsedDomainCustomResourceYaml(Path yamlPath, CreateDomainInputs inputs) throws Exception {
    this.inputs = inputs;
    parsedYaml = new ParsedKubernetesYaml(yamlPath);
  }

  public Domain getDomain() {
    return parsedYaml.getDomains().find(inputs.getDomainUid());
  }
}

// Copyright (c) 2018, 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.utils;

import java.nio.file.Path;

import oracle.kubernetes.weblogic.domain.model.Domain;

/** Parses a generated domain-custom-resource.yaml file into a set of typed k8s java objects */
public class ParsedDomainCustomResourceYaml extends ParsedKubernetesYaml {

  private DomainValues inputs;

  public ParsedDomainCustomResourceYaml(Path yamlPath, DomainValues inputs) throws Exception {
    super(new ScriptGeneratedYamlReader(yamlPath));
    this.inputs = inputs;
  }

  public ParsedDomainCustomResourceYaml(YamlReader factory, DomainValues inputs) throws Exception {
    super(factory);
    this.inputs = inputs;
  }

  public Domain getDomain() {
    return getDomains().find(inputs.getDomainUid());
  }

  public int getExpectedObjectCount() {
    return 1;
  }
}

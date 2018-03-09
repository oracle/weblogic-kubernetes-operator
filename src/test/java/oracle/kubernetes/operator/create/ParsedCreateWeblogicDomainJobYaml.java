// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
package oracle.kubernetes.operator.create;

import java.nio.file.Path;

import static oracle.kubernetes.operator.create.KubernetesArtifactUtils.*;

/**
 * Parses a generated create-weblogic-domain-job.yaml file into a set of typed k8s java objects
 */
public class ParsedCreateWeblogicDomainJobYaml {

  private CreateDomainInputs inputs;
  private ParsedKubernetesYaml parsedYaml;

  public ParsedCreateWeblogicDomainJobYaml(Path yamlPath, CreateDomainInputs inputs) throws Exception {
    this.inputs = inputs;
    parsedYaml = new ParsedKubernetesYaml(yamlPath);
  }
}

/*
ConfigMap - domain-domainuid-scripts
kind: Job - domain-domainuid-job
*/

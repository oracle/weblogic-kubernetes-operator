// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
package oracle.kubernetes.operator.create;

import java.nio.file.Path;

import io.kubernetes.client.models.V1PersistentVolumeClaim;

/**
 * Parses a generated weblogic-domain-persistent-volume-claim.yaml file into a set of typed k8s java objects
 */
public class ParsedWeblogicDomainPersistentVolumeClaimYaml {

  private CreateDomainInputs inputs;
  private ParsedKubernetesYaml parsedYaml;

  public ParsedWeblogicDomainPersistentVolumeClaimYaml(Path yamlPath, CreateDomainInputs inputs) throws Exception {
    this.inputs = inputs;
    parsedYaml = new ParsedKubernetesYaml(yamlPath);
  }

  public V1PersistentVolumeClaim getWeblogicDomainPersistentVolumeClaim() {
    return parsedYaml.getPersistentVolumeClaims().find(inputs.getDomainUid() + "-" + inputs.getPersistenceVolumeClaimName());
  }
}

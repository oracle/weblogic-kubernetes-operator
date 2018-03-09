// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
package oracle.kubernetes.operator.create;

import java.nio.file.Path;

import io.kubernetes.client.models.V1PersistentVolumeClaim;

import static oracle.kubernetes.operator.create.KubernetesArtifactUtils.*;
import oracle.kubernetes.weblogic.domain.v1.Domain;

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

  public V1PersistentVolumeClaim getPersistentVolumeClaim() {
    return parsedYaml.getPersistentVolumeClaims().find(inputs.getPersistenceVolumeClaimName());
  }
}

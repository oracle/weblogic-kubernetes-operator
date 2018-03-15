// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
package oracle.kubernetes.operator.create;

import java.nio.file.Path;

import io.kubernetes.client.models.V1PersistentVolumeClaim;

/**
 * Parses a generated weblogic-domain-persistent-volume-claim.yaml file into a set of typed k8s java objects
 */
public class ParsedWeblogicDomainPersistentVolumeClaimYaml extends ParsedKubernetesYaml {

  private CreateDomainInputs inputs;

  public ParsedWeblogicDomainPersistentVolumeClaimYaml(Path yamlPath, CreateDomainInputs inputs) throws Exception {
    super(yamlPath);
    this.inputs = inputs;
  }

  public V1PersistentVolumeClaim getWeblogicDomainPersistentVolumeClaim() {
    return getPersistentVolumeClaims().find(inputs.getDomainUID() + "-" + inputs.getPersistenceVolumeClaimName());
  }

  public int getExpectedObjectCount() {
    return 1;
  }
}

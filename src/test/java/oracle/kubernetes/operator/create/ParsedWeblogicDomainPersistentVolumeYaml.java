// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
package oracle.kubernetes.operator.create;

import java.nio.file.Path;

import io.kubernetes.client.models.V1PersistentVolume;

/**
 * Parses a generated weblogic-domain-persistent-volume.yaml file into a set of typed k8s java objects
 */
public class ParsedWeblogicDomainPersistentVolumeYaml extends ParsedKubernetesYaml {

  private CreateDomainInputs inputs;

  public ParsedWeblogicDomainPersistentVolumeYaml(Path yamlPath, CreateDomainInputs inputs) throws Exception {
    super(yamlPath);
    this.inputs = inputs;
  }

  public V1PersistentVolume getWeblogicDomainPersistentVolume() {
    return getPersistentVolumes().find(inputs.getDomainUid() + "-" + inputs.getPersistenceVolumeName());
  }

  public int getExpectedObjectCount() {
    return 1;
  }
}

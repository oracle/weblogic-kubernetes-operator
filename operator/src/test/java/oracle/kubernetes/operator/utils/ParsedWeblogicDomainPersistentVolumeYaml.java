// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.utils;

import java.nio.file.Path;

import io.kubernetes.client.models.V1PersistentVolume;

/**
 * Parses a generated weblogic-domain-pv.yaml file into a set of typed k8s java objects
 */
public class ParsedWeblogicDomainPersistentVolumeYaml extends ParsedKubernetesYaml {

  private CreateDomainInputs inputs;

  public ParsedWeblogicDomainPersistentVolumeYaml(Path yamlPath, CreateDomainInputs inputs) throws Exception {
    super(yamlPath);
    this.inputs = inputs;
  }

  public V1PersistentVolume getWeblogicDomainPersistentVolume() {
    return getPersistentVolumes().find(inputs.getWeblogicDomainPersistentVolumeName());
  }

  public int getExpectedObjectCount() {
    return 1;
  }
}

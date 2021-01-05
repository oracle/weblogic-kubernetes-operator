// Copyright (c) 2018, 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.utils;

import java.nio.file.Path;

import io.kubernetes.client.openapi.models.V1PersistentVolume;

/** Parses a generated weblogic-domain-pv.yaml file into a set of typed k8s java objects */
public class ParsedWeblogicDomainPersistentVolumeYaml extends ParsedKubernetesYaml {

  private DomainValues inputs;

  public ParsedWeblogicDomainPersistentVolumeYaml(Path yamlPath, DomainValues inputs)
      throws Exception {
    super(new ScriptGeneratedYamlReader(yamlPath));
    this.inputs = inputs;
  }

  public ParsedWeblogicDomainPersistentVolumeYaml(YamlReader factory, DomainValues inputs)
      throws Exception {
    super(factory);
    this.inputs = inputs;
  }

  public V1PersistentVolume getWeblogicDomainPersistentVolume() {
    return getPersistentVolumes().find(inputs.getWeblogicDomainPersistentVolumeName());
  }

  public V1PersistentVolume getWeblogicDomainJobPersistentVolume() {
    return getPersistentVolumes().find(inputs.getWeblogicDomainJobPersistentVolumeName());
  }

  public int getExpectedObjectCount() {
    return 1;
  }
}

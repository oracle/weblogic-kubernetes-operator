// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.utils;

import io.kubernetes.client.models.V1PersistentVolume;
import java.nio.file.Path;

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

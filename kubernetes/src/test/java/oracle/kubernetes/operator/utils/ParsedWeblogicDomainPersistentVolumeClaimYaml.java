// Copyright (c) 2018, 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.utils;

import java.nio.file.Path;

import io.kubernetes.client.openapi.models.V1PersistentVolumeClaim;

/** Parses a generated weblogic-domain-pvc.yaml file into a set of typed k8s java objects */
public class ParsedWeblogicDomainPersistentVolumeClaimYaml extends ParsedKubernetesYaml {

  private DomainValues inputs;

  public ParsedWeblogicDomainPersistentVolumeClaimYaml(Path yamlPath, DomainValues inputs)
      throws Exception {
    super(new ScriptGeneratedYamlReader(yamlPath));
    this.inputs = inputs;
  }

  public ParsedWeblogicDomainPersistentVolumeClaimYaml(YamlReader factory, DomainValues inputs)
      throws Exception {
    super(factory);
    this.inputs = inputs;
  }

  public V1PersistentVolumeClaim getWeblogicDomainPersistentVolumeClaim() {
    return getPersistentVolumeClaims().find(inputs.getWeblogicDomainPersistentVolumeClaimName());
  }

  public V1PersistentVolumeClaim getWeblogicDomainJobPersistentVolumeClaim() {
    return getPersistentVolumeClaims().find(inputs.getWeblogicDomainJobPersistentVolumeClaimName());
  }

  public int getExpectedObjectCount() {
    return 1;
  }
}

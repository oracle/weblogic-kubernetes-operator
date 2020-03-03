// Copyright (c) 2018, 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.utils;

import java.nio.file.Path;

import io.kubernetes.client.openapi.models.V1ConfigMap;
import io.kubernetes.client.openapi.models.V1Job;

/** Parses a generated delete-weblogic-domain-job.yaml file into a set of typed k8s java objects */
public class ParsedDeleteWeblogicDomainJobYaml extends ParsedKubernetesYaml {

  private DomainValues inputs;

  public ParsedDeleteWeblogicDomainJobYaml(Path yamlPath, DomainValues inputs) throws Exception {
    super(new ScriptGeneratedYamlReader(yamlPath));
    this.inputs = inputs;
  }

  public ParsedDeleteWeblogicDomainJobYaml(YamlReader factory, DomainValues inputs)
      throws Exception {
    super(factory);
    this.inputs = inputs;
  }

  public V1ConfigMap getDeleteWeblogicDomainConfigMap() {
    return getConfigMaps().find(inputs.getDomainUid() + "-delete-weblogic-domain-job-cm");
  }

  public V1Job getDeleteWeblogicDomainJob() {
    return getJobs().find(inputs.getDomainUid() + "-delete-weblogic-domain-job");
  }

  public int getExpectedObjectCount() {
    return 2;
  }
}

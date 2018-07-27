// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.utils;

import io.kubernetes.client.models.V1ConfigMap;
import io.kubernetes.client.models.V1Job;
import java.nio.file.Path;

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
    return getConfigMaps().find(inputs.getDomainUID() + "-delete-weblogic-domain-job-cm");
  }

  public V1Job getDeleteWeblogicDomainJob() {
    return getJobs().find(inputs.getDomainUID() + "-delete-weblogic-domain-job");
  }

  public int getExpectedObjectCount() {
    return 2;
  }
}
